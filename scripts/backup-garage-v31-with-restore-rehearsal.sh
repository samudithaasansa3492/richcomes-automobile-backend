#!/usr/bin/env bash
# Read-only shared-DB export + restore rehearsal into a disposable, isolated PostgreSQL.
# Never run migrations or touch data on the source. Requires a write freeze.
set -euo pipefail
set +x
umask 077

die() { printf 'STOP: %s\n' "$*" >&2; exit 2; }
for tool in psql pg_dump pg_restore docker sha256sum mktemp realpath python3; do
  command -v "$tool" >/dev/null || die "Required tool missing: $tool"
done
docker info >/dev/null 2>&1 || die "Docker daemon must be available"

PROJECT_REF="${RICHCOMES_RECOVERY_PROJECT:-}"
[[ "$PROJECT_REF" =~ ^[a-z0-9]{20}$ && "$PROJECT_REF" != iehuxwbrwfysgujaqagj ]] ||
  die "Specify a NEW separately approved Supabase project reference"
[[ "${RICHCOMES_CONFIRM_PROJECT:-}" == "$PROJECT_REF" ]] ||
  die "Explicit confirmation must match the new project"
[[ "${RICHCOMES_RECOVERY_APPROVAL:-}" == YES ]] ||
  die "Record team/DB owner approval before use"
[[ "${RICHCOMES_WRITES_PAUSED:-}" == "YES" ]] ||
  die "Arrange a maintenance window / freeze source writes, then set RICHCOMES_WRITES_PAUSED=YES"
[[ "${PGDATABASE:-}" == "postgres" && "${PGPORT:-}" == "5432" ]] ||
  die "PGDATABASE=postgres and PGPORT=5432 are required"
[[ "${PGSSLMODE:-}" =~ ^(require|verify-ca|verify-full)$ ]] ||
  die "Use TLS (PGSSLMODE=require, verify-ca or verify-full)"
[[ -n "${PGHOST:-}" && -n "${PGUSER:-}" ]] ||
  die "Specify the source through PGHOST and PGUSER; use a private password file"
if [[ "$PGHOST" == "db.$PROJECT_REF.supabase.co" ]]; then
  [[ "$PGUSER" == postgres ]] || die "Direct target requires postgres user"
else
  [[ "$PGHOST" == *.pooler.supabase.com && "$PGUSER" == "postgres.$PROJECT_REF" ]] ||
    die "Connection must be direct or a session pooler for the exact confirmed Supabase project"
fi
[[ -n "${RICHCOMES_BACKUP_DIR:-}" && "$RICHCOMES_BACKUP_DIR" == /* ]] ||
  die "RICHCOMES_BACKUP_DIR must be an existing, absolute private directory outside Git"
[[ -d "$RICHCOMES_BACKUP_DIR" && ! -L "$RICHCOMES_BACKUP_DIR" ]] ||
  die "Backup directory must be a real directory, not a symlink"
dest="$(realpath "$RICHCOMES_BACKUP_DIR")"
repo="$(realpath "$(dirname "${BASH_SOURCE[0]}")/..")"
case "$dest" in "$repo"|"$repo"/*) die "Do not save customer backups inside the repository" ;; esac
[[ -w "$dest" ]] || die "Backup destination is not writable"

bash "$repo/scripts/check-shared-flyway-v31-readonly.sh"
export PGOPTIONS="-c default_transaction_read_only=on -c lock_timeout=5000"
# Verify project schema/history, using only reads. Never output credentials/data.
preflight_sql="SELECT
  (SELECT count(*) FROM public.flyway_schema_history WHERE success)::text || ':' ||
  COALESCE((SELECT max(version::int) FROM public.flyway_schema_history
            WHERE success AND version ~ '^[0-9]+$'),0)::text"
before="$(psql -X -qAt -v ON_ERROR_STOP=1 -c "$preflight_sql")" ||
  die "Cannot verify source Flyway history"
[[ "$before" =~ ^[0-9]+:[0-9]+$ ]] || die "Unexpected Flyway history response"
[[ "$before" == "31:31" ]] ||
  die "This V31 snapshot procedure requires exact source history V31, found $before"
printf 'Verified source project and Flyway state: %s\n' "$before"
# A matching version number alone is not enough: refuse silently rewritten
# historical SQL before exporting or attempting a release.
# A dedicated private folder only becomes the final deliverable after the restore test.
scratch="$(mktemp -d "$dest/.richcomes-unverified.XXXXXXXX")"
container="richcomes-rehearsal-$(basename "$scratch" | tr -cd 'a-zA-Z0-9')"
cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  if [[ -n "${scratch:-}" && -d "$scratch" ]]; then rm -rf -- "$scratch"; fi
}
trap cleanup EXIT

# Application-only backup: all garage schema objects and rows, plus separate
# public Flyway history. Does NOT claim to capture Supabase Auth/Storage/internal
# schemas, storage objects, project settings or global role definitions.
pg_dump --format=custom --no-owner --no-acl \
  --lock-wait-timeout=5000 --schema=garage --file="$scratch/garage.dump"
pg_dump --format=custom --no-owner --no-acl \
  --lock-wait-timeout=5000 --table=public.flyway_schema_history \
  --file="$scratch/flyway-history.dump"
pg_restore --list "$scratch/garage.dump" >/dev/null
pg_restore --list "$scratch/flyway-history.dump" >/dev/null
[[ -s "$scratch/garage.dump" && -s "$scratch/flyway-history.dump" ]] ||
  die "Backup files missing or empty"

# A plain PostgreSQL 17 container is deliberately disconnected from all networks.
# It has only synthetic credentials and NO source database configuration.
test_password="synthetic-$(date +%s)-$RANDOM-$RANDOM"
docker run --rm -d --network none --name "$container" \
  --tmpfs /var/lib/postgresql/data:rw,nosuid,size=1024m \
  -e "POSTGRES_PASSWORD=$test_password" -e POSTGRES_USER=postgres \
  -e POSTGRES_DB=postgres postgres:17.12 >/dev/null ||
  die "Cannot start isolated PostgreSQL 17 restore container"

ready=0
for _ in $(seq 1 100); do
  # The initdb entrypoint's temporary server accepts Unix sockets but not TCP.
  # Wait for the final server using authenticated container-local TCP only.
  if docker exec "$container" sh -c \
      'PGPASSWORD="$POSTGRES_PASSWORD" exec psql -X -qAt -w -h 127.0.0.1 -p 5432 -U postgres -d postgres -c "SELECT 1"' \
      2>/dev/null | grep -qx '1'; then ready=1; break; fi
  sleep 0.5
done
[[ "$ready" == 1 ]] || die "Isolated restore server did not become ready"
docker exec "$container" psql -X -q -v ON_ERROR_STOP=1 -U postgres -d postgres \
  -c 'CREATE ROLE anon NOLOGIN; CREATE ROLE authenticated NOLOGIN; CREATE ROLE richcomes_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;' >/dev/null
docker cp "$scratch/garage.dump" "$container:/tmp/garage.dump"
docker cp "$scratch/flyway-history.dump" "$container:/tmp/flyway-history.dump"
docker exec "$container" pg_restore -U postgres -d postgres \
  --no-owner --no-acl --exit-on-error /tmp/garage.dump >/dev/null
docker exec "$container" pg_restore -U postgres -d postgres \
  --no-owner --no-acl --exit-on-error /tmp/flyway-history.dump >/dev/null

after="$(docker exec "$container" psql -X -qAt -v ON_ERROR_STOP=1 \
  -U postgres -d postgres -c "$preflight_sql")"
[[ "$before" == "$after" ]] || die "Restored Flyway history differs from source"
docker exec "$container" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d postgres \
  -c "COPY (SELECT installed_rank, version, script, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank) TO STDOUT WITH (FORMAT CSV)" |
  python3 "$repo/scripts/verify-applied-flyway-history.py" --expected-version 31
# All 34 V31 garage tables, including suppliers, GRN items and stock categories,
# are counted exactly. A newly added table or missing table stops the backup. This is an application-only snapshot, not managed Supabase system backup.
count_file="$repo/scripts/garage-v31-exact-counts.sql"
source_table_total="$(psql -X -qAt -v ON_ERROR_STOP=1 \
  -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='garage' AND table_type='BASE TABLE'")"
[[ "$source_table_total" == "34" ]] || die "Unexpected source garage table count: $source_table_total"
original_counts="$(psql -X -qAt -v ON_ERROR_STOP=1 -f "$count_file")"
[[ "$original_counts" =~ ^[0-9]+(:[0-9]+){33}$ ]] || die "Source table counts unavailable"
docker cp "$count_file" "$container:/tmp/garage-v31-exact-counts.sql"
restored_counts="$(docker exec "$container" psql -X -qAt -v ON_ERROR_STOP=1 \
  -U postgres -d postgres -f /tmp/garage-v31-exact-counts.sql)"
[[ "$original_counts" == "$restored_counts" ]] ||
  die "Restored key table counts differ; check active source writers"
[[ "$(psql -X -qAt -v ON_ERROR_STOP=1 -c "$preflight_sql")" == "$before" ]] ||
  die "Source Flyway history changed during backup"

(
  cd "$scratch"
  sha256sum garage.dump flyway-history.dump > SHA256SUMS
  sha256sum --check SHA256SUMS >/dev/null
)
printf 'Source schema/Flyway restoration PASS on isolated PostgreSQL 17.\n'
printf '%s\n' \
  "PROJECT_REF=$PROJECT_REF" \
  "FLYWAY_HISTORY=$before" \
  "SOURCE_COMMIT=$RICHCOMES_APPROVED_COMMIT" \
  "BACKUP_FORMAT_VERSION=5" \
  "APP_ROW_COUNTS=$original_counts" \
  "SCOPE=garage schema + public.flyway_schema_history only" \
  "RESTORE_TEST=isolated_postgres17_pass" \
  "OFFSITE_COPY=NOT_VERIFIED" \
  "DATE_UTC=$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$scratch/manifest.txt"

final="$dest/richcomes-v31-app-backup-$(date -u +%Y%m%dT%H%M%SZ)-$RANDOM"
[[ ! -e "$final" ]] || die "Refusing to overwrite an existing backup directory"
mv -- "$scratch" "$final"
scratch=""
chmod 700 "$final"
chmod 600 "$final/"*
printf 'LOCAL BACKUP + ISOLATED RESTORE VERIFIED: %s\n' "$final"
printf 'RELEASE STILL BLOCKED: encrypt and copy off-site, verify retrieval, review other Supabase-managed resources, and record DBA sign-off.\n'
