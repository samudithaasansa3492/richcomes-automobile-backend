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
    "SELECT COUNT(*) FROM public.flyway_schema_history WHERE success AND version ~ '^[0-9]+$' AND version::int BETWEEN 1 AND 29")"
  test "$count" = 29 || { echo "FAIL: ${db} migration count=${count}"; exit 1; }
  echo "PASS: ${db} contains 29 successful Flyway migrations"
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

verify_browser_denial() {
  local db="$1" count
  count="$(docker exec "$CONTAINER" psql -U postgres -d "richcomes_${db}" -Atc "
    SELECT count(*) FROM pg_class c
    JOIN pg_namespace n ON n.oid=c.relnamespace
    CROSS JOIN (VALUES ('anon'),('authenticated')) AS browser(role_name)
    WHERE n.nspname='garage' AND c.relkind IN ('r','p','v','m')
      AND (has_schema_privilege(browser.role_name, n.oid, 'USAGE')
           OR has_table_privilege(browser.role_name, c.oid, 'SELECT')
           OR has_table_privilege(browser.role_name, c.oid, 'INSERT')
           OR has_table_privilege(browser.role_name, c.oid, 'UPDATE')
           OR has_table_privilege(browser.role_name, c.oid, 'DELETE'))"
  )"
  test "$count" = 0 || {
    echo "FAIL: ${db} grants direct browser-role access to garage objects"; exit 1;
  }
  echo "PASS: ${db} garage schema is inaccessible to anon/authenticated roles"
}

verify_isolated_backup_roundtrip() {
  echo '=== Exercise custom-format garage and history backup/restore on disposable V14 ==='
  docker exec "$CONTAINER" pg_dump -U postgres -d richcomes_upgrade \
    --format=custom --no-owner --no-acl --schema=garage \
    -f /tmp/garage-v14-ci.dump
  docker exec "$CONTAINER" pg_dump -U postgres -d richcomes_upgrade \
    --format=custom --no-owner --no-acl --table=public.flyway_schema_history \
    -f /tmp/flyway-history-v14-ci.dump
  docker exec "$CONTAINER" pg_restore --list /tmp/garage-v14-ci.dump >/dev/null
  docker exec "$CONTAINER" pg_restore --list /tmp/flyway-history-v14-ci.dump >/dev/null
  docker exec "$CONTAINER" createdb -U postgres richcomes_restore
  docker exec "$CONTAINER" pg_restore --exit-on-error --no-owner --no-acl \
    -U postgres -d richcomes_restore /tmp/garage-v14-ci.dump
  docker exec "$CONTAINER" pg_restore --exit-on-error --no-owner --no-acl \
    -U postgres -d richcomes_restore /tmp/flyway-history-v14-ci.dump
  local query original restored
  query="SELECT (SELECT count(*) FROM public.flyway_schema_history WHERE success)::text || ':' ||
                (SELECT count(*) FROM garage.stock_categories)::text || ':' ||
                (SELECT count(*) FROM garage.suppliers)::text || ':' ||
                (SELECT count(*) FROM garage.employees)::text"
  original="$(docker exec "$CONTAINER" psql -U postgres -d richcomes_upgrade -Atc "$query")"
  restored="$(docker exec "$CONTAINER" psql -U postgres -d richcomes_restore -Atc "$query")"
  test "$original" = "$restored" || {
    echo 'FAIL: isolated garage V14 backup/restore row counts differ'; exit 1;
  }
  # Confirm all 21 canonical V14 tables survive the backup/restore, not
  # merely the 3 legacy spot-check tables.
  docker cp scripts/garage-v14-exact-counts.sql "$CONTAINER:/tmp/garage-v14-exact-counts.sql"
  local expected_counts actual_counts
  expected_counts="$(docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 \
    -U postgres -d richcomes_upgrade -f /tmp/garage-v14-exact-counts.sql)"
  actual_counts="$(docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 \
    -U postgres -d richcomes_restore -f /tmp/garage-v14-exact-counts.sql)"
  [[ "$expected_counts" =~ ^[0-9]+(:[0-9]+){20}$ ]] || {
    echo 'FAIL: V14 source table-count fixture did not return 21 values'; exit 1;
  }
  test "$expected_counts" = "$actual_counts" || {
    echo 'FAIL: recovered V14 data differs across the 21 application tables'; exit 1;
  }
  echo 'PASS: custom-format V14 app schema/history successfully restored on throwaway PostgreSQL'
}

verify_inventory_v26() {
  local db="$1" ok
  ok="$(docker exec "$CONTAINER" psql -U postgres -d "richcomes_${db}" -qAt -v ON_ERROR_STOP=1 -c "
    SELECT
      to_regclass('garage.store_locations') IS NOT NULL
      AND to_regclass('garage.store_stock_balances') IS NOT NULL
      AND to_regclass('garage.grn_item_allocations') IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='garage' AND table_name='service_job_parts'
          AND column_name='store_location_id'
      )
      AND (SELECT COUNT(*) FROM garage.permissions
           WHERE code IN ('inventory:grn:confirm','inventory:grn:cancel','inventory:stock:adjust'))=3
      AND (SELECT COUNT(*) FROM garage.role_permissions rp
           JOIN garage.roles r ON r.id=rp.role_id
           JOIN garage.permissions p ON p.id=rp.permission_id
           WHERE r.code='OWNER' AND
             p.code IN ('inventory:grn:confirm','inventory:grn:cancel','inventory:stock:adjust'))=3
  ")"
  test "$ok" = t || { echo "FAIL: ${db} V24-V26 inventory tables/permissions missing"; exit 1; }
  echo "PASS: ${db} V24-V26 multi-store tables, service-part location and OWNER permissions"
}

verify_v27_to_v29_schema() {
  local db="$1" ok
  ok="$(docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d "richcomes_${db}" -c "
    SELECT (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema='garage' AND table_name='customers'
              AND column_name IN ('sms_consent','mobile_verified','email_consent','email_verified'))=4
      AND EXISTS(SELECT 1 FROM information_schema.columns
                 WHERE table_schema='garage' AND table_name='stock_transactions'
                   AND column_name='store_location_id')
      AND (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema='garage' AND table_name='appointments'
             AND column_name IN ('cancellation_reason','cancelled_at',
                                 'cancelled_by_account_id','cancelled_by_type'))=4
      AND EXISTS(SELECT 1 FROM pg_constraint
                 WHERE conrelid='garage.appointments'::regclass
                   AND conname='chk_appointment_cancellation'
                   AND pg_get_constraintdef(oid) LIKE '%CANCELLED%')
  ")"
  [[ "$ok" == t ]] || { echo "FAIL: ${db} V27-V29 schema missing"; exit 1; }
  echo "PASS: ${db} V27-V29 customer consent, store location and appointment cancellation"
}

verify_latest_backup_roundtrip() {
  echo '=== Exercise complete V29 backup restore in disposable PostgreSQL ==='
  docker exec "$CONTAINER" pg_dump -U postgres -d richcomes_fresh --format=custom \
    --no-owner --no-acl --schema=garage -f /tmp/garage-v29-ci.dump
  docker exec "$CONTAINER" pg_dump -U postgres -d richcomes_fresh --format=custom \
    --no-owner --no-acl --table=public.flyway_schema_history -f /tmp/history-v29-ci.dump
  docker exec "$CONTAINER" createdb -U postgres richcomes_v29_restore
  docker exec "$CONTAINER" pg_restore -U postgres -d richcomes_v29_restore \
    --exit-on-error --no-owner --no-acl /tmp/garage-v29-ci.dump
  docker exec "$CONTAINER" pg_restore -U postgres -d richcomes_v29_restore \
    --exit-on-error --no-owner --no-acl /tmp/history-v29-ci.dump
  docker cp scripts/garage-v26-exact-counts.sql "$CONTAINER:/tmp/garage-v26-exact-counts.sql"
  local before after versions
  before="$(docker exec "$CONTAINER" psql -U postgres -d richcomes_fresh -qAt -v ON_ERROR_STOP=1 \
    -f /tmp/garage-v26-exact-counts.sql)"
  after="$(docker exec "$CONTAINER" psql -U postgres -d richcomes_v29_restore -qAt -v ON_ERROR_STOP=1 \
    -f /tmp/garage-v26-exact-counts.sql)"
  [[ "$before" =~ ^[0-9]+(:[0-9]+){32}$ && "$before" == "$after" ]] || {
    echo 'FAIL: V29 source and isolated recovered table counts differ'; exit 1;
  }
  versions="$(docker exec "$CONTAINER" psql -U postgres -d richcomes_v29_restore -qAt \
    -c "SELECT COUNT(*)::text || ':' || max(version::int)::text FROM public.flyway_schema_history
        WHERE success AND version ~ '^[0-9]+$'")"
  test "$versions" = 29:29 || {
    echo 'FAIL: recovered V29 Flyway history incomplete'; exit 1;
  }
  echo 'PASS: V29 garage/history custom-format archive restored, 33 table counts and 29 history match'
}

echo '=== Fresh install V1-V29 ==='
flyway fresh latest
verify_count fresh
verify_customer_security fresh
verify_business_delegation fresh
verify_browser_denial fresh
verify_inventory_v26 fresh
verify_v27_to_v29_schema fresh
verify_latest_backup_roundtrip

echo '=== Simulate upgrade V1-V14 to V29 ==='
flyway upgrade 14
verify_isolated_backup_roundtrip
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
verify_browser_denial upgrade
verify_inventory_v26 upgrade
verify_v27_to_v29_schema upgrade
owner_count="$(docker exec "$CONTAINER" psql -U postgres -d richcomes_upgrade -Atc \
  "SELECT count(*) FROM garage.user_accounts a JOIN garage.user_account_roles ar ON ar.user_account_id=a.id JOIN garage.roles r ON r.id=ar.role_id JOIN garage.employees e ON e.id=a.employee_id WHERE a.username='validation.owner' AND a.enabled AND a.identity_kind='BUSINESS' AND r.code='OWNER' AND e.position='OWNER'")"
test "$owner_count" = 1 || { echo 'FAIL: synthetic legacy OWNER was not preserved'; exit 1; }
echo 'PASS: synthetic legacy OWNER account, grants and identity preserved'
echo 'PASS: disposable Flyway fresh/upgrade verification completed. No shared database touched.'
