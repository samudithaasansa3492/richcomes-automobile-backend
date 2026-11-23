
#!/usr/bin/env bash
# Full Spring Boot tests against ephemeral local PostgreSQL.
# Never connects to shared Supabase.

set -euo pipefail
set +x

cd "$(dirname "$0")/.."

# --------------------------------------------------
# 1. Safety and environment validation
# --------------------------------------------------

test ! -e .env || {
    echo "STOP: checkout contains .env"
    exit 2
}

test -n "${JAVA_HOME:-}" && test -x "$JAVA_HOME/bin/java" || {
    echo "STOP: configure JAVA_HOME for Java 21"
    exit 2
}

"$JAVA_HOME/bin/javac" -version 2>&1 |
    grep -q '^javac 21\.' || {
        echo "STOP: Java 21 required"
        exit 2
    }

command -v docker >/dev/null &&
    docker info >/dev/null 2>&1 || {
        echo "STOP: local Docker daemon required"
        exit 2
    }

command -v openssl >/dev/null || {
    echo "STOP: openssl required"
    exit 2
}

# Never inherit external database or runtime overrides.
unset DB_URL DB_USERNAME DB_PASSWORD
unset PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
unset SPRING_DATASOURCE_URL
unset SPRING_DATASOURCE_USERNAME
unset SPRING_DATASOURCE_PASSWORD
unset SPRING_APPLICATION_JSON SPRING_CONFIG_IMPORT
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_OPTS

# --------------------------------------------------
# 2. Create disposable workspace
# --------------------------------------------------

ROOT="$(mktemp -d /tmp/richcomes-context-validation.XXXXXX)"

NAME="richcomes-context-$(basename "$ROOT" | tr -cd '[:alnum:]')"

DB_PASSWORD="synthetic-$(date +%s)-${RANDOM}-${RANDOM}"

cleanup() {
    docker rm -f "$NAME" >/dev/null 2>&1 || true
    rm -rf -- "$ROOT"
}

trap cleanup EXIT

# --------------------------------------------------
# 3. Windows Git Bash compatibility
# --------------------------------------------------

KEY_PUBLIC_LOCATION="file:$ROOT/public.pem"
KEY_PRIVATE_LOCATION="file:$ROOT/private.pem"

TEST_TMP="$ROOT"
TEST_TMP_NATIVE="$ROOT"

if [[ "${OSTYPE:-}" == msys* ||
      "${OSTYPE:-}" == cygwin* ]]; then

    command -v cygpath >/dev/null || {
        echo "STOP: cygpath is required on Windows"
        exit 2
    }

    WIN_ROOT="$(cygpath -m "$ROOT")"

    KEY_PUBLIC_LOCATION="file:///${WIN_ROOT}/public.pem"
    KEY_PRIVATE_LOCATION="file:///${WIN_ROOT}/private.pem"

    TEST_TMP_NATIVE="$WIN_ROOT"
fi

# --------------------------------------------------
# 4. Generate disposable RSA keys
# --------------------------------------------------

echo "=== Generating disposable RSA test key pair ==="

openssl genpkey \
    -algorithm RSA \
    -pkeyopt rsa_keygen_bits:2048 \
    -out "$ROOT/private.pem" \
    >/dev/null 2>&1

openssl pkey \
    -in "$ROOT/private.pem" \
    -pubout \
    -out "$ROOT/public.pem" \
    >/dev/null 2>&1

chmod 600 "$ROOT/private.pem" "$ROOT/public.pem"

test -s "$ROOT/private.pem" || {
    echo "FAIL: private key was not generated"
    exit 1
}

test -s "$ROOT/public.pem" || {
    echo "FAIL: public key was not generated"
    exit 1
}

# --------------------------------------------------
# 5. Start isolated PostgreSQL
# --------------------------------------------------

echo "=== Starting ephemeral PostgreSQL (localhost only) ==="

docker run -d --rm \
    --name "$NAME" \
    --tmpfs /var/lib/postgresql/data:rw,nosuid,size=512m \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -e POSTGRES_DB=richcomes_context \
    -p 127.0.0.1::5432 \
    postgres:17.12 >/dev/null

ready=0

for _ in $(seq 1 120); do

    if docker exec \
        -e "PGPASSWORD=$DB_PASSWORD" \
        "$NAME" \
        psql -X -qAt \
        -h 127.0.0.1 \
        -U postgres \
        -d postgres \
        -c 'SELECT 1' 2>/dev/null |
        grep -qx 1; then

        ready=1
        break
    fi

    sleep 0.5
done

test "$ready" -eq 1 || {
    echo "FAIL: disposable PostgreSQL not ready"
    exit 1
}

# --------------------------------------------------
# 6. Validate localhost-only database port
# --------------------------------------------------

binding="$(docker port "$NAME" 5432/tcp)"

[[ "$binding" =~ ^127\.0\.0\.1:([0-9]+)$ ]] || {
    echo "STOP: database port is not bound to localhost only"
    exit 2
}

port="${BASH_REMATCH[1]}"

# --------------------------------------------------
# 7. Create synthetic Supabase role fixtures
# --------------------------------------------------

docker exec -i \
    -e "PGPASSWORD=$DB_PASSWORD" \
    "$NAME" \
    psql -X -q \
    -v ON_ERROR_STOP=1 \
    -h 127.0.0.1 \
    -U postgres \
    -d postgres <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
SQL

echo "PASS: disposable PostgreSQL and Supabase NOLOGIN role fixtures"

# --------------------------------------------------
# 8. Configure isolated database connection
# --------------------------------------------------

URL="jdbc:postgresql://127.0.0.1:${port}/richcomes_context?sslmode=disable"

# --------------------------------------------------
# 9. Diagnostic function (read-only)
# --------------------------------------------------

diagnose_schema() {
    echo "=== Schema compatibility diagnostics (isolated DB only) ==="

    echo "--- Flyway migration history ---"

    docker exec \
        -e "PGPASSWORD=$DB_PASSWORD" \
        "$NAME" \
        psql -X -U postgres -d richcomes_context \
        -c "
        SELECT version, description, success
        FROM public.flyway_schema_history
        ORDER BY installed_rank DESC
        LIMIT 6;
        " || true

    echo "--- Customer verification columns ---"

    docker exec \
        -e "PGPASSWORD=$DB_PASSWORD" \
        "$NAME" \
        psql -X -U postgres -d richcomes_context \
        -c "
        SELECT COUNT(DISTINCT attname) AS verification_column_count
        FROM pg_attribute
        WHERE attrelid = to_regclass('garage.user_accounts')
          AND attnum > 0
          AND NOT attisdropped
          AND attname IN (
              'customer_verified_at',
              'customer_verified_by',
              'customer_verification_ref',
              'customer_verified_email',
              'customer_password_set_at'
          );
        " || true

    echo "--- Recovery challenge constraint ---"

    docker exec \
        -e "PGPASSWORD=$DB_PASSWORD" \
        "$NAME" \
        psql -X -U postgres -d richcomes_context \
        -c "
        SELECT
            conname,
            pg_get_constraintdef(oid) AS definition,
            (
                pg_get_constraintdef(oid)
                LIKE '%INITIAL_PASSWORD%'
            ) AS allows_initial_password
        FROM pg_constraint
        WHERE conrelid = to_regclass('garage.recovery_challenges')
          AND conname = 'recovery_challenges_purpose_check';
        " || true

    echo "--- Matching recovery constraints ---"

    docker exec \
        -e "PGPASSWORD=$DB_PASSWORD" \
        "$NAME" \
        psql -X -U postgres -d richcomes_context \
        -c "
        SELECT conname, pg_get_constraintdef(oid) AS definition
        FROM pg_constraint
        WHERE conrelid = to_regclass('garage.recovery_challenges')
          AND contype = 'c';
        " || true
}

# --------------------------------------------------
# 10. Run full-context integration tests
# --------------------------------------------------

echo "=== Full-context security tests plus PostgreSQL integration tests ==="

if env -i \
    HOME="$HOME" \
    USER="${USER:-codespace}" \
    PATH="$PATH" \
    JAVA_HOME="$JAVA_HOME" \
    TMPDIR="$TEST_TMP" \
    TEMP="$TEST_TMP_NATIVE" \
    TMP="$TEST_TMP_NATIVE" \
    DB_USERNAME=postgres \
    DB_PASSWORD="$DB_PASSWORD" \
    JWT_ISSUER="https://isolated-junit.invalid" \
    JWT_AUDIENCE=richcomes-test \
    JWT_PUBLIC_KEY_LOCATION="$KEY_PUBLIC_LOCATION" \
    JWT_PRIVATE_KEY_LOCATION="$KEY_PRIVATE_LOCATION" \
    RICHCOMES_LOCAL_PG_TESTS=YES \
    ./mvnw -B \
    -Djava.io.tmpdir="$TEST_TMP_NATIVE" \
    -Dtest=GarageApplicationTests,ServiceCategoryControllerTest,CashierPermissionPostgresIT \
    -Dspring.flyway.enabled=true \
    -Dspring.datasource.url="$URL" \
    -Dspring.datasource.username=postgres \
    -Dspring.datasource.password="$DB_PASSWORD" \
    -Dspring.datasource.driver-class-name=org.postgresql.Driver \
    -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect \
    test > "$ROOT/maven.log" 2>&1

then

    grep -E 'Tests run:|BUILD SUCCESS' \
        "$ROOT/maven.log" | tail -n 12

    echo "PASS: full-context Spring Boot tests completed in disposable DB"

else

    echo "=== FULL CONTEXT TEST FAILURE (no shared DB touched) ==="

    # Print the actual failure reason.
    grep -m 12 -E \
        'Caused by:|Tests run:|expected:|but was:|ERROR:' \
        "$ROOT/maven.log" || true

    # Read-only checks against this script's throwaway database.
    diagnose_schema

    tail -n 65 "$ROOT/maven.log"

    exit 1
fi

echo "PASS: no shared Supabase connection was configured"
