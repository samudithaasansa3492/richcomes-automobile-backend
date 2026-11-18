#!/usr/bin/env bash
# Read only 14 non-sensitive migration-history metadata rows. No database writes.
set -euo pipefail
set +x

die() { printf 'STOP: %s\n' "$*" >&2; exit 2; }
PROJECT_REF="iehuxwbrwfysgujaqagj"
[[ "${RICHCOMES_CONFIRM_PROJECT:-}" == "$PROJECT_REF" ]] ||
  die "Confirm the exact project: export RICHCOMES_CONFIRM_PROJECT=$PROJECT_REF"
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
[[ -n "${PGPASSFILE:-}" && -f "$PGPASSFILE" ]] ||
  die "Provide a protected PGPASSFILE rather than a password in CLI arguments"
[[ "$(stat -c '%a' "$PGPASSFILE")" == 600 ]] ||
  die "PGPASSFILE must have chmod 600"
script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
echo "Validating exact immutable V1-V14 Flyway history against current Git source (no writes)..."

# COPY TO STDOUT outputs metadata only; the transaction is READ ONLY.
PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=10000 -c lock_timeout=3000" \
  psql -X -qAt -v ON_ERROR_STOP=1 \
  -c "COPY (SELECT installed_rank, version, script, checksum, success
          FROM public.flyway_schema_history
          ORDER BY installed_rank) TO STDOUT WITH (FORMAT CSV)" |
  python3 "$script_root/verify-applied-flyway-history.py"
