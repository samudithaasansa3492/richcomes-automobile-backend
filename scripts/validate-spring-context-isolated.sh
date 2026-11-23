#!/usr/bin/env bash
# Full Spring Boot tests against ephemeral local PostgreSQL, never shared Supabase.
set -euo pipefail
set +x
cd "$(dirname "$0")/.."

test ! -e .env || { echo "STOP: checkout contains .env"; exit 2; }
test -n "${JAVA_HOME:-}" && test -x "$JAVA_HOME/bin/java" || {
  echo "STOP: configure JAVA_HOME for Java 21"; exit 2;
}
"$JAVA_HOME/bin/javac" -version 2>&1 | grep -q '^javac 21\.' || {
  echo "STOP: Java 21 required"; exit 2;
}
command -v docker >/dev/null && docker info >/dev/null 2>&1 || {
  echo "STOP: local Docker daemon required"; exit 2;
}
command -v openssl >/dev/null || { echo "STOP: openssl required"; exit 2; }

# Fail closed: do not inherit external database and JWT/environment overrides.
unset DB_URL DB_USERNAME DB_PASSWORD PGHOST PGPORT PGUSER PGPASSWORD PGDATABASE
unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD
unset SPRING_APPLICATION_JSON SPRING_CONFIG_IMPORT
unset JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS MAVEN_OPTS

ROOT="$(mktemp -d /tmp/richcomes-context-validation.XXXXXX)"
NAME="richcomes-context-$(basename "$ROOT" | tr -cd '[:alnum:]')"
DB_PASSWORD="synthetic-$(date +%s)-${RANDOM}-${RANDOM}"

cleanup() {
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  rm -rf -- "$ROOT"
}
trap cleanup EXIT

echo "=== Generating disposable RSA test key pair ==="
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$ROOT/private.pem" >/dev/null 2>&1
openssl pkey -in "$ROOT/private.pem" -pubout -out "$ROOT/public.pem" >/dev/null 2>&1
chmod 600 "$ROOT/private.pem" "$ROOT/public.pem"

echo "=== Starting ephemeral PostgreSQL (localhost only) ==="
docker run -d --rm --name "$NAME" \
  --tmpfs /var/lib/postgresql/data:rw,nosuid,size=512m \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -e POSTGRES_DB=richcomes_context \
  -p 127.0.0.1::5432 postgres:17.12 >/dev/null

ready=0
for _ in $(seq 1 120); do
  if docker exec -e "PGPASSWORD=$DB_PASSWORD" "$NAME" \
      psql -X -qAt -h 127.0.0.1 -U postgres -d postgres \
      -c 'SELECT 1' 2>/dev/null | grep -qx 1; then
    ready=1; break
  fi
  sleep 0.5
done
test "$ready" -eq 1 || { echo "FAIL: disposable PostgreSQL not ready"; exit 1; }
binding="$(docker port "$NAME" 5432/tcp)"
[[ "$binding" =~ ^127\.0\.0\.1:([0-9]+)$ ]] || {
  echo "STOP: database port is not bound to localhost only"; exit 2;
}
port="${BASH_REMATCH[1]}"

docker exec -i -e "PGPASSWORD=$DB_PASSWORD" "$NAME" \
  psql -X -q -v ON_ERROR_STOP=1 -h 127.0.0.1 -U postgres -d postgres <<'SQL'
CREATE ROLE anon NOLOGIN;
CREATE ROLE authenticated NOLOGIN;
SQL
echo "PASS: disposable PostgreSQL and Supabase NOLOGIN role fixtures"

# Explicitly pass a new database URL and throwaway JWT signing keys.
# A clean environment prevents other host settings from redirecting the test.
URL="jdbc:postgresql://127.0.0.1:${port}/richcomes_context?sslmode=disable"
echo "=== Full-context security tests plus opt-in PostgreSQL permission/audit transaction IT ==="

if env -i \
  HOME="$HOME" USER="${USER:-codespace}" PATH="$PATH" JAVA_HOME="$JAVA_HOME" \
  DB_USERNAME=postgres DB_PASSWORD="$DB_PASSWORD" \
  JWT_ISSUER="https://isolated-junit.invalid" JWT_AUDIENCE=richcomes-test \
  JWT_PUBLIC_KEY_LOCATION="file:$ROOT/public.pem" \
  JWT_PRIVATE_KEY_LOCATION="file:$ROOT/private.pem" \
  RICHCOMES_LOCAL_PG_TESTS=YES \
  ./mvnw -B \
    -Dtest=GarageApplicationTests,ServiceCategoryControllerTest,CashierPermissionPostgresIT \
    -Dspring.flyway.enabled=true \
    -Dspring.datasource.url="$URL" \
    -Dspring.datasource.username=postgres \
    -Dspring.datasource.password="$DB_PASSWORD" \
    -Dspring.datasource.driver-class-name=org.postgresql.Driver \
    -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect \
    test > "$ROOT/maven.log" 2>&1
then
  grep -E 'Tests run:|BUILD SUCCESS' "$ROOT/maven.log" | tail -n 12
  echo "PASS: full-context Spring Boot tests completed in disposable DB"
else
  echo "=== FULL CONTEXT TEST FAILURE (no shared DB touched) ==="
  grep -m 12 -E 'Caused by:|Tests run:|expected:|but was:|ERROR:' "$ROOT/maven.log" || true
  tail -n 65 "$ROOT/maven.log"
  exit 1
fi

echo "PASS: no shared Supabase connection was configured"
