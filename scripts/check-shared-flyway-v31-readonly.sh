#!/usr/bin/env bash
# Read only 31 non-sensitive migration-history metadata rows. No database writes.
set -euo pipefail
set +x

die() { printf 'STOP: %s\n' "$*" >&2; exit 2; }
PROJECT_REF="${RICHCOMES_RECOVERY_PROJECT:-}"
[[ "$PROJECT_REF" =~ ^[a-z0-9]{20}$ && "$PROJECT_REF" != iehuxwbrwfysgujaqagj ]] ||
  die "Specify a NEW separately approved Supabase project reference"
[[ "${RICHCOMES_CONFIRM_PROJECT:-}" == "$PROJECT_REF" ]] ||
  die "Explicit confirmation must match the new project"
[[ "${RICHCOMES_RECOVERY_APPROVAL:-}" == YES ]] ||
  die "Record team/DB owner approval before use"
[[ "${PGDATABASE:-}" == "postgres" && "${PGPORT:-}" == "5432" ]] ||
  die "PGDATABASE=postgres, PGPORT=5432 required"
[[ "${PGSSLMODE:-}" =~ ^(require|verify-ca|verify-full)$ ]] ||
  die "PGSSLMODE must require TLS"
[[ -n "${PGHOST:-}" && -n "${PGUSER:-}" ]] ||
  die "Specify PGHOST and PGUSER using a private password file"
if [[ "$PGHOST" == "db.$PROJECT_REF.supabase.co" ]]; then
  [[ "$PGUSER" == postgres ]] || die "Direct connection requires postgres database user"
else
  [[ "$PGHOST" == *.pooler.supabase.com && "$PGUSER" == "postgres.$PROJECT_REF" ]] ||
    die "Use direct hostname or exact-project session pooler (not transaction mode)"
fi
command -v psql >/dev/null || die "psql not found"
command -v python3 >/dev/null || die "python3 not found"
[[ -z "${PGHOSTADDR:-}${PGSERVICE:-}${PGSERVICEFILE:-}${PGPASSWORD:-}${PGOPTIONS:-}" ]] ||
  die "Unset libpq overrides and plaintext passwords"
[[ -n "${PGPASSFILE:-}" && -f "$PGPASSFILE" && ! -L "$PGPASSFILE" && -O "$PGPASSFILE" ]] ||
  die "Use operator-owned regular PGPASSFILE"
[[ "$(stat -c '%a' "$PGPASSFILE")" == 600 ]] || die "PGPASSFILE must have mode 600"
repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
[[ "${RICHCOMES_APPROVED_COMMIT:-}" =~ ^[0-9a-f]{40}$ &&
   "$(git -C "$repo" rev-parse HEAD)" == "$RICHCOMES_APPROVED_COMMIT" ]] ||
  die "Use the exact approved merged commit"
[[ -z "$(git -C "$repo" status --porcelain --untracked-files=no)" ]] ||
  die "Tracked checkout changes are prohibited"
[[ "$(python3 "$repo/scripts/check-flyway-source-versions.py" --latest)" == 31 ]] ||
  die "Exact V1-V31 source required"
script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
echo "Validating exact immutable V1-V31 Flyway history against current Git source (no writes)..."

# COPY TO STDOUT outputs metadata only; the transaction is READ ONLY.
PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=10000 -c lock_timeout=3000" \
  psql -X -qAt -v ON_ERROR_STOP=1 \
  -c "COPY (SELECT installed_rank, version, script, checksum, success
          FROM public.flyway_schema_history
          ORDER BY installed_rank) TO STDOUT WITH (FORMAT CSV)" |
  python3 "$script_root/verify-applied-flyway-history.py" --expected-version 31

# Exact history alone does not prove the application tables exist.
PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=10000 -c lock_timeout=3000" \
  psql -X -qAt -v ON_ERROR_STOP=1 -f "$script_root/garage-v31-exact-counts.sql" |
  grep -Ex '[0-9]+(:[0-9]+){33}' >/dev/null || die "Expected all 34 tables"
total="$(PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=10000" psql -X -qAt -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='garage' AND table_type='BASE TABLE'")"
[[ "$total" == 34 ]] || die "Expected exactly 34 garage tables"
echo "PASS: V31 history and 34 application tables exist"
