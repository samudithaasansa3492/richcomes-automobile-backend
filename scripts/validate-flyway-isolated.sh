#!/usr/bin/env bash
# Disposable PostgreSQL validation; no access to shared Supabase.
set -euo pipefail
set +x
cd "$(dirname "$0")/.."
test ! -e .env || { echo 'STOP: use a clean checkout without .env'; exit 2; }
command -v docker >/dev/null && docker info >/dev/null 2>&1 || { echo 'STOP: Docker unavailable'; exit 2; }
test -n "${JAVA_HOME:-}" && test -x "$JAVA_HOME/bin/javac" || { echo 'STOP: JAVA_HOME must be Java 21'; exit 2; }
"$JAVA_HOME/bin/javac" -version 2>&1 | grep -q '^javac 21\.' || { echo 'STOP: Java 21 required'; exit 2; }

# Reject inheritance of shared database and signing configuration.
unset DB_URL DB_USERNAME DB_PASSWORD PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
unset SPRING_APPLICATION_JSON SPRING_CONFIG_IMPORT JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS

python3 scripts/check-flyway-source-versions.py

TEMP="$(mktemp -d /tmp/richcomes-flyway-validation.XXXXXX)"
CONTAINER="richcomes-local-$(basename "$TEMP" | tr -c '[:alnum:]_-' '-')"
cleanup() {
  docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
  rm -rf -- "$TEMP"
}
trap cleanup EXIT
PASSWORD="testonly-$(date +%s)-${RANDOM}-${RANDOM}"

echo '=== Compile Java and prepare Flyway classpath (no Spring Boot startup) ==='
./mvnw -B -DskipTests compile dependency:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory="$TEMP/libs"

cat > "$TEMP/LocalFlywayValidation.java" <<'JAVA'
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

class LocalFlywayValidation {
    public static void main(String[] args) {
        if (args.length != 3 || !args[0].matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/richcomes_(fresh|upgrade)\\?sslmode=disable")) {
            throw new IllegalArgumentException("Only disposable localhost databases are permitted");
        }
        var config = Flyway.configure().dataSource(args[0], "postgres", args[1])
                .locations("classpath:db/migration").defaultSchema("public").cleanDisabled(true);
        if (!"latest".equals(args[2])) {
            config.target(MigrationVersion.fromVersion(args[2]));
        }
        var flyway = config.load();
        var result = flyway.migrate();
        System.out.println("Flyway migrations executed: " + result.migrationsExecuted);
        if ("latest".equals(args[2])) {
            flyway.validate();
            System.out.println("Flyway validation passed");
        }
    }
}
JAVA

CP="target/classes:$TEMP/libs/*:$TEMP"
"$JAVA_HOME/bin/javac" -cp "$CP" -d "$TEMP" "$TEMP/LocalFlywayValidation.java"

echo '=== Start synthetic PostgreSQL 17 (127.0.0.1 only) ==='
docker run --rm -d --name "$CONTAINER" \
  --tmpfs /var/lib/postgresql/data:rw,nosuid,size=512m \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$PASSWORD" \
  -e POSTGRES_DB=postgres -p 127.0.0.1::5432 postgres:17.12 >/dev/null

# Official postgres container briefly starts a socket-only bootstrap server.
# A socket-based pg_isready can succeed before that server shuts down, so
# require an authenticated TCP query against the final PostgreSQL server.
ready=0
for _ in $(seq 1 120); do
  if docker exec -e "PGPASSWORD=$PASSWORD" "$CONTAINER" \
      psql -X -qAt -h 127.0.0.1 -p 5432 -U postgres -d postgres \
      -c 'SELECT 1' 2>/dev/null | grep -qx '1'; then
    ready=1
    break
  fi
  sleep 0.5
done
test "$ready" -eq 1 || { echo 'FAIL: final isolated PostgreSQL TCP server not ready'; exit 1; }
echo 'PASS: final PostgreSQL server accepts authenticated TCP connections'

HOST_BIND="$(docker port "$CONTAINER" 5432/tcp)"
if [[ ! "$HOST_BIND" =~ ^127\.0\.0\.1:([0-9]+)$ ]]; then
  echo 'STOP: localhost-only port mapping was not confirmed'
  exit 2
fi
PORT="${BASH_REMATCH[1]}"

# Supabase provides anon/authenticated cluster roles. Plain PostgreSQL does not.
# Simulate ONLY these role names in this disposable test container; do not
# weaken application RLS/REVOKE statements or create login-capable roles.
docker exec -i -e "PGPASSWORD=$PASSWORD" "$CONTAINER" \
  psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres -d postgres <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
SQL
role_count="$(docker exec -e "PGPASSWORD=$PASSWORD" "$CONTAINER" \
  psql -X -qAt -h 127.0.0.1 -U postgres -d postgres \
  -c "SELECT count(*) FROM pg_roles WHERE rolname IN ('anon','authenticated') AND NOT rolcanlogin")"
test "$role_count" = 2 || { echo 'FAIL: synthetic NOLOGIN roles not available'; exit 1; }
echo 'PASS: isolated Supabase-compatible NOLOGIN role fixtures'

docker exec -e "PGPASSWORD=$PASSWORD" "$CONTAINER" \
  createdb -h 127.0.0.1 -U postgres richcomes_fresh
docker exec -e "PGPASSWORD=$PASSWORD" "$CONTAINER" \
  createdb -h 127.0.0.1 -U postgres richcomes_upgrade

flyway() {
  local database="$1" version="$2"
  "$JAVA_HOME/bin/java" -cp "$CP" LocalFlywayValidation \
    "jdbc:postgresql://127.0.0.1:${PORT}/richcomes_${database}?sslmode=disable" \
    "$PASSWORD" "$version"
}
verify_count() {
  local db="$1" count
  count="$(docker exec "$CONTAINER" psql -U postgres -d "richcomes_${db}" -Atc \
    "SELECT COUNT(*) FROM public.flyway_schema_history WHERE success AND version ~ '^[0-9]+$' AND version::int BETWEEN 1 AND 23")"
  test "$count" = 23 || { echo "FAIL: ${db} migration count=${count}"; exit 1; }
  echo "PASS: ${db} contains 23 successful Flyway migrations"
}

verify_customer_security() {
  local db="$1" outcome
  outcome="$(docker exec "$CONTAINER" psql -U postgres -d "richcomes_${db}" -Atc "
    SELECT CASE WHEN
      (SELECT COUNT(*) FROM garage.roles WHERE code='CUSTOMER') = 1
      AND (SELECT COUNT(*) FROM garage.role_permissions rp
           JOIN garage.roles r ON r.id=rp.role_id
           JOIN garage.permissions p ON p.id=rp.permission_id
           WHERE r.code='OWNER' AND p.code IN ('customer:register','customer:delegate_register')) = 2
      AND (SELECT COUNT(*) FROM garage.role_permissions rp
           JOIN garage.roles r ON r.id=rp.role_id
           JOIN garage.permissions p ON p.id=rp.permission_id
           WHERE r.code='CASHIER' AND p.code='customer:register') = 0
      AND (SELECT COUNT(*) FROM garage.role_permissions rp
           JOIN garage.roles r ON r.id=rp.role_id
           JOIN garage.permissions p ON p.id=rp.permission_id
           WHERE r.code='CUSTOMER' AND p.code='customer:self') = 1
      AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='garage'
                  AND table_name='user_accounts' AND column_name='customer_id')
      AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='garage'
                  AND table_name='user_accounts' AND column_name='customer_password_set_at')
      AND EXISTS (SELECT 1 FROM information_schema.check_constraints
                  WHERE constraint_name='recovery_challenges_purpose_check'
                    AND check_clause LIKE '%INITIAL_PASSWORD%')
      AND EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                  WHERE n.nspname='garage' AND c.relname='account_permissions' AND c.relrowsecurity)
      AND EXISTS (SELECT 1 FROM pg_constraint WHERE conname='ck_account_identity_kind'
                  AND conrelid='garage.user_accounts'::regclass)
    THEN 'PASS' ELSE 'FAIL' END
  ")" 
  test "$outcome" = PASS || { echo "FAIL: ${db} customer role/RLS/constraints"; exit 1; }
  echo "PASS: ${db} CUSTOMER/OWNER/CASHIER grants, identity constraint and RLS"
}

verify_business_delegation() {
  local db="$1" verdict
  verdict="$(docker exec "$CONTAINER" psql -U postgres -d "richcomes_${db}" -Atc "
    SELECT CASE WHEN
      (SELECT COUNT(*) FROM garage.permissions
       WHERE code IN ('permission:delegate','vehicle:read','vehicle:update',
                      'appointment:manage','inventory:read','service:read',
                      'billing:read','report:read','history:read','reminder:read',
                      'notification:read','audit:read')) = 12
      AND (SELECT COUNT(*) FROM garage.role_permissions rp
           JOIN garage.roles r ON r.id=rp.role_id
           JOIN garage.permissions p ON p.id=rp.permission_id
           WHERE r.code='OWNER' AND p.code IN
           ('permission:delegate','vehicle:read','vehicle:update','appointment:manage',
            'inventory:read','service:read','billing:read','report:read',
            'history:read','reminder:read','notification:read','audit:read')) = 12
      AND (SELECT COUNT(*) FROM garage.role_permissions rp
           JOIN garage.roles r ON r.id=rp.role_id
           JOIN garage.permissions p ON p.id=rp.permission_id
           WHERE r.code IN ('CASHIER','CUSTOMER','ADMIN')
           AND p.code IN ('permission:delegate','audit:read','vehicle:read',
                          'inventory:read','billing:read')) = 0
      AND EXISTS (SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
           WHERE n.nspname='garage' AND c.relname='account_permission_audit'
           AND c.relrowsecurity)
    THEN 'PASS' ELSE 'FAIL' END
  ")" 
  test "$verdict" = PASS || { echo "FAIL: ${db} business delegation schema or role grants"; exit 1; }
  echo "PASS: ${db} explicit OWNER business permissions, no CASHIER wildcard grants, audit RLS"
}

echo '=== Fresh install V1-V23 ==='
flyway fresh latest
verify_count fresh
verify_customer_security fresh
verify_business_delegation fresh

echo '=== Simulate upgrade V1-V14 to V22 ==='
flyway upgrade 14
flyway upgrade 18

# Synthetic legacy OWNER_ADMIN account exists only in the throwaway upgrade DB.
docker exec -i "$CONTAINER" psql -v ON_ERROR_STOP=1 -U postgres -d richcomes_upgrade <<'SQL'
WITH employee AS (
  INSERT INTO garage.employees(employee_code,nic,first_name,last_name,phone,position)
  VALUES ('VALIDATION-OWNER','VALIDATION-NIC','Sample','Owner','0770000000','OWNER_ADMIN')
  RETURNING id
), account AS (
  INSERT INTO garage.user_accounts(employee_id,username,password_hash,enabled)
  SELECT id, 'validation.owner', 'synthetic-not-a-login-secret', TRUE FROM employee
  RETURNING id
)
INSERT INTO garage.user_account_roles(user_account_id,role_id)
SELECT account.id, role.id FROM account CROSS JOIN garage.roles role
WHERE role.code = 'OWNER_ADMIN';
SQL

flyway upgrade latest
verify_count upgrade
verify_customer_security upgrade
verify_business_delegation upgrade
owner_count="$(docker exec "$CONTAINER" psql -U postgres -d richcomes_upgrade -Atc \
  "SELECT count(*) FROM garage.user_accounts a JOIN garage.user_account_roles ar ON ar.user_account_id=a.id JOIN garage.roles r ON r.id=ar.role_id JOIN garage.employees e ON e.id=a.employee_id WHERE a.username='validation.owner' AND a.enabled AND a.identity_kind='BUSINESS' AND r.code='OWNER' AND e.position='OWNER'")"
test "$owner_count" = 1 || { echo 'FAIL: synthetic legacy OWNER was not preserved'; exit 1; }
echo 'PASS: synthetic legacy OWNER account, grants and identity preserved'
echo 'PASS: disposable Flyway fresh/upgrade verification completed. No shared database touched.'
