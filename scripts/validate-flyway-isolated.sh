
#!/usr/bin/env bash
# Disposable PostgreSQL and Flyway validation.
# Never connects to shared Supabase.

set -euo pipefail
set +x

cd "$(dirname "$0")/.."

# --------------------------------------------------
# 1. Safety checks
# --------------------------------------------------

test ! -e .env || {
    echo 'STOP: use a clean checkout without .env'
    exit 2
}

command -v docker >/dev/null &&
    docker info >/dev/null 2>&1 || {
        echo 'STOP: Docker unavailable'
        exit 2
    }

test -n "${JAVA_HOME:-}" &&
    test -x "$JAVA_HOME/bin/javac" || {
        echo 'STOP: JAVA_HOME must be Java 21'
        exit 2
    }

"$JAVA_HOME/bin/javac" -version 2>&1 |
    grep -q '^javac 21\.' || {
        echo 'STOP: Java 21 required'
        exit 2
    }

# Reject inherited shared database configuration.
unset DB_URL DB_USERNAME DB_PASSWORD
unset PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
unset SPRING_DATASOURCE_URL
unset SPRING_DATASOURCE_USERNAME
unset SPRING_DATASOURCE_PASSWORD
unset SPRING_APPLICATION_JSON SPRING_CONFIG_IMPORT
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_OPTS

# --------------------------------------------------
# 2. Validate migration source files
# --------------------------------------------------

if command -v python3 >/dev/null 2>&1 &&
    python3 -c 'import sys' >/dev/null 2>&1; then
    python3 scripts/check-flyway-source-versions.py
LATEST="$(python3 scripts/check-flyway-source-versions.py --latest)"
[[ "$LATEST" =~ ^[0-9]+$ && "$LATEST" -ge 28 ]] ||
  { echo "FAIL: dynamic validated latest version is invalid" >&2; exit 1; }
echo "=== Testing disposable Flyway V1-V$LATEST ==="
elif command -v py >/dev/null 2>&1; then
    py -3 scripts/check-flyway-source-versions.py
else
    echo 'STOP: Python 3 is required'
    exit 2
fi

# --------------------------------------------------
# 3. Disposable workspace
# --------------------------------------------------

TEMP="$(mktemp -d /tmp/richcomes-flyway-validation.XXXXXX)"

CONTAINER="richcomes-local-$(basename "$TEMP" | tr -c '[:alnum:]_-' '-')"

cleanup() {
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    rm -rf -- "$TEMP"
}

trap cleanup EXIT

PASSWORD="testonly-$(date +%s)-${RANDOM}-${RANDOM}"

# --------------------------------------------------
# 4. Compile Java and prepare Flyway classpath
# --------------------------------------------------

echo '=== Compile Java and prepare Flyway classpath ==='

./mvnw -B -DskipTests compile dependency:copy-dependencies \
    -DincludeScope=runtime \
    -DoutputDirectory="$TEMP/libs"

cat > "$TEMP/LocalFlywayValidation.java" <<'JAVA'
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;

class LocalFlywayValidation {

    public static void main(String[] args) {

        if (args.length != 3 || !args[0].matches(
                "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/richcomes_(fresh|upgrade)\\?sslmode=disable")) {

            throw new IllegalArgumentException(
                    "Only disposable localhost databases are permitted");
        }

        var config = Flyway.configure()
                .dataSource(args[0], "postgres", args[1])
                .locations("classpath:db/migration")
                .defaultSchema("public")
                .cleanDisabled(true);

        if (!"latest".equals(args[2])) {
            config.target(MigrationVersion.fromVersion(args[2]));
        }

        var flyway = config.load();
        var result = flyway.migrate();

        System.out.println(
                "Flyway migrations executed: " + result.migrationsExecuted);

        if ("latest".equals(args[2])) {
            flyway.validate();
            System.out.println("Flyway validation passed");
        }
    }
}
JAVA

# Support native Windows Java when called from Git Bash.
CP="target/classes:$TEMP/libs/*:$TEMP"
JAVA_SOURCE="$TEMP/LocalFlywayValidation.java"
JAVA_OUTPUT="$TEMP"

if [[ "${OSTYPE:-}" == msys* ||
      "${OSTYPE:-}" == cygwin* ]]; then

    command -v cygpath >/dev/null || {
        echo 'STOP: cygpath required on Windows'
        exit 2
    }

    WIN_TEMP="$(cygpath -m "$TEMP")"
    WIN_CLASSES="$(cygpath -m "$PWD/target/classes")"

    CP="${WIN_CLASSES};${WIN_TEMP}/libs/*;${WIN_TEMP}"
    JAVA_SOURCE="${WIN_TEMP}/LocalFlywayValidation.java"
    JAVA_OUTPUT="$WIN_TEMP"
fi

"$JAVA_HOME/bin/javac" \
    -cp "$CP" \
    -d "$JAVA_OUTPUT" \
    "$JAVA_SOURCE"

# --------------------------------------------------
# 5. Start isolated PostgreSQL
# --------------------------------------------------

echo '=== Start synthetic PostgreSQL 17 (127.0.0.1 only) ==='

docker run --rm -d \
    --name "$CONTAINER" \
    --tmpfs /var/lib/postgresql/data:rw,nosuid,size=512m \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD="$PASSWORD" \
    -e POSTGRES_DB=postgres \
    -p 127.0.0.1::5432 \
    postgres:17.12 >/dev/null

ready=0

for _ in $(seq 1 120); do

    if docker exec \
        -e "PGPASSWORD=$PASSWORD" \
        "$CONTAINER" \
        psql -X -qAt \
        -h 127.0.0.1 \
        -p 5432 \
        -U postgres \
        -d postgres \
        -c 'SELECT 1' 2>/dev/null |
        grep -qx '1'; then

        ready=1
        break
    fi

    sleep 0.5
done

test "$ready" -eq 1 || {
    echo 'FAIL: isolated PostgreSQL TCP server not ready'
    exit 1
}

echo 'PASS: PostgreSQL accepts authenticated TCP connections'

# --------------------------------------------------
# 6. Verify localhost-only port
# --------------------------------------------------

HOST_BIND="$(docker port "$CONTAINER" 5432/tcp)"

if [[ ! "$HOST_BIND" =~ ^127\.0\.0\.1:([0-9]+)$ ]]; then
    echo 'STOP: localhost-only port mapping not confirmed'
    exit 2
fi

PORT="${BASH_REMATCH[1]}"

# --------------------------------------------------
# 7. Synthetic Supabase roles
# --------------------------------------------------

docker exec -i \
    -e "PGPASSWORD=$PASSWORD" \
    "$CONTAINER" \
    psql -X -q -v ON_ERROR_STOP=1 \
    -h 127.0.0.1 \
    -U postgres \
    -d postgres <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
SQL

role_count="$(docker exec \
    -e "PGPASSWORD=$PASSWORD" \
    "$CONTAINER" \
    psql -X -qAt \
    -h 127.0.0.1 \
    -U postgres \
    -d postgres \
    -c "SELECT COUNT(*) FROM pg_roles
        WHERE rolname IN ('anon','authenticated')
        AND NOT rolcanlogin")"

test "$role_count" = 2 || {
    echo 'FAIL: synthetic NOLOGIN roles unavailable'
    exit 1
}

echo 'PASS: isolated Supabase-compatible NOLOGIN roles'

# --------------------------------------------------
# 8. Create two isolated databases
# --------------------------------------------------

docker exec \
    -e "PGPASSWORD=$PASSWORD" \
    "$CONTAINER" \
    createdb -h 127.0.0.1 -U postgres richcomes_fresh

docker exec \
    -e "PGPASSWORD=$PASSWORD" \
    "$CONTAINER" \
    createdb -h 127.0.0.1 -U postgres richcomes_upgrade

flyway() {
    local database="$1"
    local version="$2"

    "$JAVA_HOME/bin/java" \
        -cp "$CP" \
        LocalFlywayValidation \
        "jdbc:postgresql://127.0.0.1:${PORT}/richcomes_${database}?sslmode=disable" \
        "$PASSWORD" \
        "$version"
}

# --------------------------------------------------
# 9. Verify complete V1-V28 history
# --------------------------------------------------

verify_count() {
    local db="$1"
    local result

    result="$(docker exec "$CONTAINER" \
        psql -X -U postgres -d "richcomes_${db}" -qAt \
        -v ON_ERROR_STOP=1 \
        -c "
        SELECT COUNT(*)::text || ':' ||
               MIN(version::int)::text || ':' ||
               MAX(version::int)::text
        FROM public.flyway_schema_history
        WHERE success
          AND version ~ '^[0-9]+$';
        ")"

    test "$result" = "${LATEST}:1:${LATEST}" || {
        echo "FAIL: ${db} Flyway history expected ${LATEST}:1:${LATEST}, got $result"
        exit 1
    }

    echo "PASS: ${db} contains V1-V${LATEST} successful migrations"
}

# --------------------------------------------------
# 10. Verify customer permissions and constraints
# --------------------------------------------------

verify_customer_security() {
    local db="$1"
    local outcome

    outcome="$(docker exec "$CONTAINER" \
        psql -U postgres -d "richcomes_${db}" -Atc "
        SELECT CASE WHEN
          (SELECT COUNT(*) FROM garage.roles
           WHERE code='CUSTOMER') = 1

          AND (SELECT COUNT(*)
               FROM garage.role_permissions rp
               JOIN garage.roles r ON r.id=rp.role_id
               JOIN garage.permissions p ON p.id=rp.permission_id
               WHERE r.code='OWNER'
                 AND p.code IN (
                     'customer:register',
                     'customer:delegate_register'
                 )) = 2

          AND (SELECT COUNT(*)
               FROM garage.role_permissions rp
               JOIN garage.roles r ON r.id=rp.role_id
               JOIN garage.permissions p ON p.id=rp.permission_id
               WHERE r.code='CASHIER'
                 AND p.code='customer:register') = 0

          AND (SELECT COUNT(*)
               FROM garage.role_permissions rp
               JOIN garage.roles r ON r.id=rp.role_id
               JOIN garage.permissions p ON p.id=rp.permission_id
               WHERE r.code='CUSTOMER'
                 AND p.code='customer:self') = 1

          AND EXISTS (
              SELECT 1 FROM information_schema.columns
              WHERE table_schema='garage'
                AND table_name='user_accounts'
                AND column_name='customer_id'
          )

          AND EXISTS (
              SELECT 1 FROM information_schema.columns
              WHERE table_schema='garage'
                AND table_name='user_accounts'
                AND column_name='customer_password_set_at'
          )

          AND EXISTS (
              SELECT 1 FROM information_schema.check_constraints
              WHERE constraint_name='recovery_challenges_purpose_check'
                AND check_clause LIKE '%INITIAL_PASSWORD%'
          )

          AND EXISTS (
              SELECT 1
              FROM pg_class c
              JOIN pg_namespace n ON n.oid=c.relnamespace
              WHERE n.nspname='garage'
                AND c.relname='account_permissions'
                AND c.relrowsecurity
          )

          AND EXISTS (
              SELECT 1 FROM pg_constraint
              WHERE conname='ck_account_identity_kind'
                AND conrelid='garage.user_accounts'::regclass
          )

        THEN 'PASS' ELSE 'FAIL' END
        ")"

    test "$outcome" = PASS || {
        echo "FAIL: ${db} customer security checks"
        exit 1
    }

    echo "PASS: ${db} customer roles, permissions, constraints and RLS"
}

# --------------------------------------------------
# 11. Verify OWNER business delegation
# --------------------------------------------------

verify_business_delegation() {
    local db="$1"
    local verdict

    verdict="$(docker exec "$CONTAINER" \
        psql -U postgres -d "richcomes_${db}" -Atc "
        SELECT CASE WHEN

          (SELECT COUNT(*)
           FROM garage.permissions
           WHERE code IN (
               'permission:delegate',
               'vehicle:read',
               'vehicle:update',
               'appointment:manage',
               'inventory:read',
               'service:read',
               'billing:read',
               'report:read',
               'history:read',
               'reminder:read',
               'notification:read',
               'audit:read'
           )) = 12

          AND (SELECT COUNT(*)
               FROM garage.role_permissions rp
               JOIN garage.roles r ON r.id=rp.role_id
               JOIN garage.permissions p ON p.id=rp.permission_id
               WHERE r.code='OWNER'
                 AND p.code IN (
                     'permission:delegate',
                     'vehicle:read',
                     'vehicle:update',
                     'appointment:manage',
                     'inventory:read',
                     'service:read',
                     'billing:read',
                     'report:read',
                     'history:read',
                     'reminder:read',
                     'notification:read',
                     'audit:read'
                 )) = 12

          AND (SELECT COUNT(*)
               FROM garage.role_permissions rp
               JOIN garage.roles r ON r.id=rp.role_id
               JOIN garage.permissions p ON p.id=rp.permission_id
               WHERE r.code IN ('CASHIER','CUSTOMER','ADMIN')
                 AND p.code IN (
                     'permission:delegate',
                     'audit:read',
                     'vehicle:read',
                     'inventory:read',
                     'billing:read'
                 )) = 0

          AND EXISTS (
              SELECT 1
              FROM pg_class c
              JOIN pg_namespace n ON n.oid=c.relnamespace
              WHERE n.nspname='garage'
                AND c.relname='account_permission_audit'
                AND c.relrowsecurity
          )

        THEN 'PASS' ELSE 'FAIL' END
        ")"

    test "$verdict" = PASS || {
        echo "FAIL: ${db} OWNER delegation check"
        exit 1
    }

    echo "PASS: ${db} OWNER delegation and audit RLS"
}

# --------------------------------------------------
# 12. Verify browser-role access is denied
# --------------------------------------------------

verify_browser_denial() {
    local db="$1"
    local count

    count="$(docker exec "$CONTAINER" \
        psql -U postgres -d "richcomes_${db}" -Atc "
        SELECT COUNT(*)
        FROM pg_class c
        JOIN pg_namespace n ON n.oid=c.relnamespace
        CROSS JOIN (
            VALUES ('anon'),('authenticated')
        ) AS browser(role_name)
        WHERE n.nspname='garage'
          AND c.relkind IN ('r','p','v','m')
          AND (
              has_schema_privilege(
                  browser.role_name, n.oid, 'USAGE'
              )
              OR has_table_privilege(
                  browser.role_name, c.oid, 'SELECT'
              )
              OR has_table_privilege(
                  browser.role_name, c.oid, 'INSERT'
              )
              OR has_table_privilege(
                  browser.role_name, c.oid, 'UPDATE'
              )
              OR has_table_privilege(
                  browser.role_name, c.oid, 'DELETE'
              )
          )
        ")"

    test "$count" = 0 || {
        echo "FAIL: ${db} grants browser-role access to garage"
        exit 1
    }

    echo "PASS: ${db} denies direct browser-role access"
}

# --------------------------------------------------
# 13. Verify existing V14 backup and restore test
# --------------------------------------------------

verify_isolated_backup_roundtrip() {

    echo '=== Exercise V14 backup/restore on disposable PostgreSQL ==='

    docker exec "$CONTAINER" \
        pg_dump -U postgres -d richcomes_upgrade \
        --format=custom --no-owner --no-acl \
        --schema=garage \
        -f /tmp/garage-v14-ci.dump

    docker exec "$CONTAINER" \
        pg_dump -U postgres -d richcomes_upgrade \
        --format=custom --no-owner --no-acl \
        --table=public.flyway_schema_history \
        -f /tmp/flyway-history-v14-ci.dump

    docker exec "$CONTAINER" \
        pg_restore --list /tmp/garage-v14-ci.dump >/dev/null

    docker exec "$CONTAINER" \
        pg_restore --list /tmp/flyway-history-v14-ci.dump >/dev/null

    docker exec "$CONTAINER" \
        createdb -U postgres richcomes_restore

    docker exec "$CONTAINER" \
        pg_restore --exit-on-error --no-owner --no-acl \
        -U postgres -d richcomes_restore \
        /tmp/garage-v14-ci.dump

    docker exec "$CONTAINER" \
        pg_restore --exit-on-error --no-owner --no-acl \
        -U postgres -d richcomes_restore \
        /tmp/flyway-history-v14-ci.dump

    local query original restored

    query="SELECT
        (SELECT COUNT(*)
         FROM public.flyway_schema_history
         WHERE success)::text || ':' ||
        (SELECT COUNT(*)
         FROM garage.stock_categories)::text || ':' ||
        (SELECT COUNT(*)
         FROM garage.suppliers)::text || ':' ||
        (SELECT COUNT(*)
         FROM garage.employees)::text"

    original="$(docker exec "$CONTAINER" \
        psql -U postgres -d richcomes_upgrade -Atc "$query")"

    restored="$(docker exec "$CONTAINER" \
        psql -U postgres -d richcomes_restore -Atc "$query")"

    test "$original" = "$restored" || {
        echo 'FAIL: V14 backup/restore counts differ'
        exit 1
    }

    docker cp scripts/garage-v14-exact-counts.sql \
        "$CONTAINER:/tmp/garage-v14-exact-counts.sql"

    local expected_counts actual_counts

    expected_counts="$(docker exec "$CONTAINER" \
        psql -X -qAt -v ON_ERROR_STOP=1 \
        -U postgres -d richcomes_upgrade \
        -f /tmp/garage-v14-exact-counts.sql)"

    actual_counts="$(docker exec "$CONTAINER" \
        psql -X -qAt -v ON_ERROR_STOP=1 \
        -U postgres -d richcomes_restore \
        -f /tmp/garage-v14-exact-counts.sql)"

    [[ "$expected_counts" =~ ^[0-9]+(:[0-9]+){20}$ ]] || {
        echo 'FAIL: V14 count fixture did not return 21 values'
        exit 1
    }

    test "$expected_counts" = "$actual_counts" || {
        echo 'FAIL: recovered V14 table counts differ'
        exit 1
    }

    echo 'PASS: V14 backup and restore verified'
}

# --------------------------------------------------
# 14. Preserve V24-V26 inventory checks
# --------------------------------------------------

verify_inventory_v26() {
    local db="$1"
    local ok

    ok="$(docker exec "$CONTAINER" \
        psql -U postgres -d "richcomes_${db}" \
        -qAt -v ON_ERROR_STOP=1 -c "
        SELECT
          to_regclass('garage.store_locations') IS NOT NULL

          AND to_regclass('garage.store_stock_balances') IS NOT NULL

          AND to_regclass('garage.grn_item_allocations') IS NOT NULL

          AND EXISTS (
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema='garage'
                AND table_name='service_job_parts'
                AND column_name='store_location_id'
          )

          AND (
              SELECT COUNT(*)
              FROM garage.permissions
              WHERE code IN (
                  'inventory:grn:confirm',
                  'inventory:grn:cancel',
                  'inventory:stock:adjust'
              )
          ) = 3

          AND (
              SELECT COUNT(*)
              FROM garage.role_permissions rp
              JOIN garage.roles r ON r.id=rp.role_id
              JOIN garage.permissions p ON p.id=rp.permission_id
              WHERE r.code='OWNER'
                AND p.code IN (
                    'inventory:grn:confirm',
                    'inventory:grn:cancel',
                    'inventory:stock:adjust'
                )
          ) = 3
        ")"

    test "$ok" = t || {
        echo "FAIL: ${db} V24-V26 inventory validation"
        exit 1
    }

    echo "PASS: ${db} V24-V26 inventory features"
}

# --------------------------------------------------
# 15. Verify V28 store FK
# --------------------------------------------------

verify_inventory_v28() {
    local db="$1"
    local ok

    ok="$(docker exec "$CONTAINER" \
        psql -U postgres -d "richcomes_${db}" \
        -qAt -v ON_ERROR_STOP=1 -c "
        SELECT
          EXISTS (
              SELECT 1
              FROM information_schema.columns
              WHERE table_schema='garage'
                AND table_name='stock_transactions'
                AND column_name='store_location_id'
                AND data_type='bigint'
          )

          AND EXISTS (
              SELECT 1
              FROM pg_constraint
              WHERE conname='fk_stock_transactions_store_location'
                AND conrelid='garage.stock_transactions'::regclass
                AND confrelid='garage.store_locations'::regclass
                AND contype='f'
          )

          AND EXISTS (
              SELECT 1
              FROM pg_indexes
              WHERE schemaname='garage'
                AND tablename='stock_transactions'
                AND indexname='idx_stock_transactions_store_created'
          )
        ")"

    test "$ok" = t || {
        echo "FAIL: ${db} V28 store FK/index validation"
        exit 1
    }

    echo "PASS: ${db} V28 stock transaction store FK and index"
}

# --------------------------------------------------
verify_appointment_v29_if_present() {
    local db="$1" ok
    if [[ "$LATEST" -lt 29 ]]; then
        return 0
    fi
    ok="$(docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 \
        -U postgres -d "richcomes_${db}" -c "
        SELECT
          (SELECT count(*) FROM information_schema.columns
           WHERE table_schema='garage' AND table_name='appointments'
             AND column_name IN ('cancellation_reason','cancelled_at',
                                 'cancelled_by_account_id','cancelled_by_type')) = 4
          AND EXISTS (
             SELECT 1 FROM pg_constraint
             WHERE conrelid='garage.appointments'::regclass
               AND conname='chk_appointment_cancellation'
               AND pg_get_constraintdef(oid) LIKE '%CANCELLED%'
          )
          AND EXISTS (
             SELECT 1 FROM pg_constraint
             WHERE conrelid='garage.appointments'::regclass
               AND conname='fk_appointments_cancelled_by_account'
               AND confrelid='garage.user_accounts'::regclass
               AND contype='f'
          )
          AND (SELECT count(*) FROM information_schema.columns
               WHERE table_schema='garage' AND table_name='customers'
                 AND column_name IN ('sms_consent','mobile_verified',
                                     'email_consent','email_verified')) = 4
        ")"
    [[ "$ok" == t ]] ||
      { echo "FAIL: ${db} V27 customer consent or V29 cancellation constraints missing"; exit 1; }
    echo "PASS: ${db} Member 02 V29 appointment cancellation schema and V27 consent"
}

# 16. V28 complete backup/restore verification
# --------------------------------------------------

verify_latest_backup_roundtrip() {

    echo '=== Exercise complete latest backup/restore in disposable PostgreSQL ==='

    docker exec "$CONTAINER" \
        pg_dump -U postgres -d richcomes_fresh \
        --format=custom --no-owner --no-acl \
        --schema=garage \
        -f /tmp/garage-latest-ci.dump

    docker exec "$CONTAINER" \
        pg_dump -U postgres -d richcomes_fresh \
        --format=custom --no-owner --no-acl \
        --table=public.flyway_schema_history \
        -f /tmp/history-latest-ci.dump

    docker exec "$CONTAINER" \
        pg_restore --list /tmp/garage-latest-ci.dump >/dev/null

    docker exec "$CONTAINER" \
        pg_restore --list /tmp/history-latest-ci.dump >/dev/null

    docker exec "$CONTAINER" \
        createdb -U postgres richcomes_latest_restore

    docker exec "$CONTAINER" \
        pg_restore -U postgres -d richcomes_latest_restore \
        --exit-on-error --no-owner --no-acl \
        /tmp/garage-latest-ci.dump

    docker exec "$CONTAINER" \
        pg_restore -U postgres -d richcomes_latest_restore \
        --exit-on-error --no-owner --no-acl \
        /tmp/history-latest-ci.dump

    # Keep existing 33-table V26 fixture as regression coverage.
    docker cp scripts/garage-v26-exact-counts.sql \
        "$CONTAINER:/tmp/garage-v26-exact-counts.sql"

    local before after versions

    before="$(docker exec "$CONTAINER" \
        psql -U postgres -d richcomes_fresh \
        -qAt -v ON_ERROR_STOP=1 \
        -f /tmp/garage-v26-exact-counts.sql)"

    after="$(docker exec "$CONTAINER" \
        psql -U postgres -d richcomes_latest_restore \
        -qAt -v ON_ERROR_STOP=1 \
        -f /tmp/garage-v26-exact-counts.sql)"

    [[ "$before" =~ ^[0-9]+(:[0-9]+){32}$ ]] || {
        echo 'FAIL: V26 table-count fixture did not return 33 values'
        exit 1
    }

    test "$before" = "$after" || {
        echo 'FAIL: Latest recovered table counts differ'
        exit 1
    }

    # The frozen V26 fixture checks the original 33 tables. Also compare ALL
    # present garage tables so future source migrations adding new tables cannot
    # silently fall outside the disposable restore verification.
    local source_tables recovered_tables table source_rows recovered_rows checked
    source_tables="$(docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 \
        -U postgres -d richcomes_fresh -c \
        "SELECT tablename FROM pg_tables WHERE schemaname='garage' ORDER BY tablename")"
    recovered_tables="$(docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 \
        -U postgres -d richcomes_latest_restore -c \
        "SELECT tablename FROM pg_tables WHERE schemaname='garage' ORDER BY tablename")"
    [[ -n "$source_tables" && "$source_tables" == "$recovered_tables" ]] || {
        echo 'FAIL: source and recovered garage table sets differ'
        exit 1
    }
    checked=0
    while IFS= read -r table; do
        [[ "$table" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || {
            echo 'FAIL: unexpected garage table identifier in disposable restore'
            exit 1
        }
        source_rows="$(docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 \
            -U postgres -d richcomes_fresh -c "SELECT COUNT(*) FROM garage.$table")"
        recovered_rows="$(docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 \
            -U postgres -d richcomes_latest_restore -c "SELECT COUNT(*) FROM garage.$table")"
        [[ "$source_rows" =~ ^[0-9]+$ && "$source_rows" == "$recovered_rows" ]] || {
            echo "FAIL: recovered garage.$table has a different row count"
            exit 1
        }
        checked=$((checked + 1))
    done <<< "$source_tables"
    [[ "$checked" -ge 33 ]] || {
        echo "FAIL: expected at least 33 garage tables, got $checked"
        exit 1
    }
    echo "PASS: all $checked current garage tables and row counts match isolated restore"
    if [[ "$LATEST" == 31 ]]; then
        test "$checked" = 34
        local counts31 restored31
        counts31="$(docker exec -i "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d richcomes_fresh < scripts/garage-v31-exact-counts.sql)"
        restored31="$(docker exec -i "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d richcomes_latest_restore < scripts/garage-v31-exact-counts.sql)"
        [[ "$counts31" =~ ^[0-9]+(:[0-9]+){33}$ && "$counts31" == "$restored31" ]]
        docker exec "$CONTAINER" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d richcomes_latest_restore -c "COPY (SELECT installed_rank, version, script, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank) TO STDOUT WITH (FORMAT CSV)" |
          python3 scripts/verify-applied-flyway-history.py --expected-version 31
        echo "PASS: exact V31 counts and restored migration CRCs"
    fi


    versions="$(docker exec "$CONTAINER" \
        psql -U postgres -d richcomes_latest_restore \
        -qAt -v ON_ERROR_STOP=1 -c "
        SELECT COUNT(*)::text || ':' ||
               MIN(version::int)::text || ':' ||
               MAX(version::int)::text
        FROM public.flyway_schema_history
        WHERE success
          AND version ~ '^[0-9]+$'
        ")"

    test "$versions" = "${LATEST}:1:${LATEST}" || {
        echo "FAIL: recovered latest history expected ${LATEST}:1:${LATEST}, got $versions"
        exit 1
    }

    echo "PASS: latest V$LATEST backup/restore, 33 tracked table counts and $LATEST migrations"
}

# --------------------------------------------------
# 17. Fresh migration test
# --------------------------------------------------

echo "=== Fresh install V1-V$LATEST ==="

flyway fresh latest

verify_count fresh
verify_customer_security fresh
verify_business_delegation fresh
verify_browser_denial fresh
verify_inventory_v26 fresh
verify_inventory_v28 fresh
verify_appointment_v29_if_present fresh
verify_latest_backup_roundtrip

# --------------------------------------------------
# 18. Upgrade V1-V14 to V28
# --------------------------------------------------

echo "=== Simulate upgrade V1-V14 to V$LATEST ==="

flyway upgrade 14

verify_isolated_backup_roundtrip

flyway upgrade 18

# Synthetic legacy OWNER_ADMIN account.
# Exists only in the throwaway upgrade database.

docker exec -i "$CONTAINER" \
    psql -v ON_ERROR_STOP=1 \
    -U postgres -d richcomes_upgrade <<'SQL'
WITH employee AS (
    INSERT INTO garage.employees(
        employee_code,
        nic,
        first_name,
        last_name,
        phone,
        position
    )
    VALUES (
        'VALIDATION-OWNER',
        'VALIDATION-NIC',
        'Sample',
        'Owner',
        '0770000000',
        'OWNER_ADMIN'
    )
    RETURNING id
),
account AS (
    INSERT INTO garage.user_accounts(
        employee_id,
        username,
        password_hash,
        enabled
    )
    SELECT
        id,
        'validation.owner',
        'synthetic-not-a-login-secret',
        TRUE
    FROM employee
    RETURNING id
)
INSERT INTO garage.user_account_roles(
    user_account_id,
    role_id
)
SELECT
    account.id,
    role.id
FROM account
CROSS JOIN garage.roles role
WHERE role.code = 'OWNER_ADMIN';
SQL

flyway upgrade latest

verify_count upgrade
verify_customer_security upgrade
verify_business_delegation upgrade
verify_browser_denial upgrade
verify_inventory_v26 upgrade
verify_inventory_v28 upgrade
verify_appointment_v29_if_present upgrade

# --------------------------------------------------
# 19. Confirm legacy OWNER preservation
# --------------------------------------------------

owner_count="$(docker exec "$CONTAINER" \
    psql -U postgres -d richcomes_upgrade -Atc "
    SELECT COUNT(*)
    FROM garage.user_accounts a
    JOIN garage.user_account_roles ar
      ON ar.user_account_id=a.id
    JOIN garage.roles r
      ON r.id=ar.role_id
    JOIN garage.employees e
      ON e.id=a.employee_id
    WHERE a.username='validation.owner'
      AND a.enabled
      AND a.identity_kind='BUSINESS'
      AND r.code='OWNER'
      AND e.position='OWNER'
    ")"

test "$owner_count" = 1 || {
    echo 'FAIL: synthetic legacy OWNER was not preserved'
    exit 1
}

if [[ "$LATEST" == 31 ]]; then
    docker exec -i "$CONTAINER" psql -X -v ON_ERROR_STOP=1 -U postgres -d richcomes_fresh < ops/security/lock-down-public-flyway-history.sql
    docker exec -i "$CONTAINER" psql -X -v ON_ERROR_STOP=1 -U postgres -d richcomes_fresh < ops/security/configure-v31-runtime-role.sql
    docker exec -i "$CONTAINER" psql -X -v ON_ERROR_STOP=1 -U postgres -d richcomes_fresh < scripts/test-v31-runtime-role.sql
    echo 'PASS: restricted runtime DML, OWNER row lock, audit/static seed protection and DDL denial'
fi

echo 'PASS: synthetic legacy OWNER preserved'
echo "PASS: disposable Flyway V1-V$LATEST fresh and upgrade verification completed"
echo 'PASS: no shared Supabase database touched'
