#!/usr/bin/env bash
# CI-only V29 migration preflight. The caller creates a disposable localhost PostgreSQL.
# No shared Supabase or Railway connections. Guard remains enabled in the application.
set -euo pipefail
set +x

[[ "$#" == 4 ]] || { echo "STOP: disposable container, port, synthetic password and workdir required" >&2; exit 2; }
name="$1"
port="$2"
password="$3"
workdir="$4"

[[ "$name" =~ ^richcomes-context-[A-Za-z0-9]+$ ]] ||
  { echo "STOP: unexpected CI container name" >&2; exit 2; }
[[ "$port" =~ ^[0-9]{4,5}$ ]] ||
  { echo "STOP: invalid localhost port" >&2; exit 2; }
[[ "$workdir" == /tmp/richcomes-context-validation.* && -d "$workdir" && ! -L "$workdir" ]] ||
  { echo "STOP: invalid CI-only working directory" >&2; exit 2; }
[[ -n "$password" && "$password" == synthetic-* ]] ||
  { echo "STOP: not a synthetic CI password" >&2; exit 2; }
test ! -e .env || { echo "STOP: .env is not allowed in isolated CI"; exit 2; }
command -v docker >/dev/null
[[ "$(docker inspect -f '{{.State.Running}}' "$name")" == true ]] ||
  { echo "STOP: disposable database container is not running" >&2; exit 2; }

# Refuse an externally published DB; only the already-checking caller may bind to loopback.
binding="$(docker port "$name" 5432/tcp)"
[[ "$binding" == "127.0.0.1:$port" ]] ||
  { echo "STOP: CI Postgres port is not localhost-only" >&2; exit 2; }

url="jdbc:postgresql://127.0.0.1:${port}/richcomes_context?sslmode=disable"
mkdir -p "$workdir/libs"
echo "=== Compile and prepare isolated PostgreSQL Flyway classpath ==="
if ! env -i HOME="$HOME" USER="${USER:-runner}" PATH="$PATH" JAVA_HOME="$JAVA_HOME" \
  ./mvnw -B -DskipTests compile dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory="$workdir/libs" > "$workdir/flyway-compile.log" 2>&1; then
  grep -E 'ERROR|BUILD FAILURE' "$workdir/flyway-compile.log" | tail -n 12 || true
  echo "FAIL: CI-only Flyway runtime classpath build failed"
  exit 1
fi

cat > "$workdir/CiFlywayMigrate.java" <<'JAVA'
import org.flywaydb.core.Flyway;
class CiFlywayMigrate {
    public static void main(String[] args) {
        if (args.length != 2 ||
            !args[0].matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/richcomes_context\\?sslmode=disable")) {
            throw new IllegalArgumentException("Only disposable CI localhost is allowed");
        }
        var flyway = Flyway.configure()
                .dataSource(args[0], "postgres", args[1])
                .locations("classpath:db/migration")
                .defaultSchema("public")
                .cleanDisabled(true)
                .load();
        flyway.migrate();
        flyway.validate();
        System.out.println("PASS: Flyway current-source migration and checksum validation");
    }
}
JAVA

cp="target/classes:$workdir/libs/*:$workdir"
"$JAVA_HOME/bin/javac" -cp "$cp" -d "$workdir" "$workdir/CiFlywayMigrate.java"
if ! env -i HOME="$HOME" USER="${USER:-runner}" PATH="$PATH" JAVA_HOME="$JAVA_HOME" \
  "$JAVA_HOME/bin/java" -cp "$cp" CiFlywayMigrate "$url" "$password" \
  > "$workdir/flyway-migrate.log" 2>&1; then
  grep -E 'FlywayException|ERROR|Caused by:|Migration ' "$workdir/flyway-migrate.log" | tail -n 15 || true
  echo "FAIL: disposable migrations failed (source database untouched)"
  exit 1
fi

history="$(docker exec "$name" psql -X -qAt -v ON_ERROR_STOP=1 \
  -U postgres -d richcomes_context -c \
  "SELECT COUNT(*)::text || ':' || MAX(version::int)::text
   FROM public.flyway_schema_history WHERE success AND version ~ '^[0-9]+$'")"
[[ "$history" == 29:29 ]] ||
  { echo "FAIL: expected exactly V1-V29 migrations in disposable CI, found $history"; exit 1; }

# Exact schema metadata needed by the *unchanged* SchemaCompatibilityStartupGuard.
# Include V29 appointment cancellation constraint to detect partial migrations.
guard_ready="$(docker exec "$name" psql -X -qAt -v ON_ERROR_STOP=1 \
  -U postgres -d richcomes_context -c "
    SELECT
       (SELECT COUNT(DISTINCT attname) FROM pg_attribute
         WHERE attrelid=to_regclass('garage.user_accounts')
           AND attnum > 0 AND NOT attisdropped
           AND attname IN ('customer_verified_at','customer_verified_by',
                           'customer_verification_ref','customer_verified_email',
                           'customer_password_set_at')) = 5
       AND EXISTS (SELECT 1 FROM pg_constraint
         WHERE conrelid=to_regclass('garage.recovery_challenges')
           AND conname='recovery_challenges_purpose_check'
           AND pg_get_constraintdef(oid) LIKE '%INITIAL_PASSWORD%')
       AND EXISTS (SELECT 1 FROM pg_constraint
         WHERE conrelid=to_regclass('garage.appointments')
           AND conname='chk_appointment_cancellation'
           AND pg_get_constraintdef(oid) LIKE '%CANCELLED%')
    ")"
[[ "$guard_ready" == t ]] ||
  { echo "FAIL: V23 security guard metadata or V29 appointment constraints absent"; exit 1; }
echo "PASS: CI-only V1-V29 Flyway history, V23 guard and V29 cancellation schema verified"
