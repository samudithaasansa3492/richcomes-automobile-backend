#!/usr/bin/env bash
# Isolated Docker team-dev DB ONLY. Creates two random local test identities.
# Never connects to Supabase or exposes fixed credentials in Git/CI.
set -euo pipefail
set +x
umask 077
die() { echo "STOP: $*" >&2; exit 2; }
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT"
[[ ! -e .env ]] || die "Refusing checkout with .env. Use a clean local clone."
for cmd in docker openssl curl python3; do
  command -v "$cmd" >/dev/null || die "$cmd required"
done
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 required"
STATE="$HOME/.local/state/richcomes-team-dev"
[[ -d "$STATE" && -r "$STATE/password" ]] || die "First run: bash scripts/team-dev.sh up"
[[ "$(stat -c '%a' "$STATE")" == 700 ]] || die "Local state directory must be mode 0700"
export RICHCOMES_TEAM_STATE="$STATE"
export RICHCOMES_TEAM_PASSWORD="$(cat "$STATE/password")"
COMPOSE="$ROOT/compose.team-dev.yml"
PROJECT="richcomes-team-$(id -u)"
db_container="$(docker compose -p "$PROJECT" -f "$COMPOSE" ps -q database)"
[[ -n "$db_container" ]] || die "Private Docker database is not running"
[[ "$(docker inspect -f '{{.State.Running}}' "$db_container")" == true ]] ||
  die "Private Docker database is not running"
[[ -z "$(docker port "$db_container" 5432/tcp 2>/dev/null)" ]] ||
  die "Refusing DB with a published TCP port"
psql_local() {
  docker compose -p "$PROJECT" -f "$COMPOSE" exec -T database \
    psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d richcomes_team "$@"
}
history="$(psql_local -c "SELECT COUNT(*)::text || ':' || MAX(version::int)::text
  FROM public.flyway_schema_history WHERE success AND version ~ '^[0-9]+$'")"
latest="$(python3 "$ROOT/scripts/check-flyway-source-versions.py" --latest)" ||
  die "Invalid migration source; refusing to seed accounts"
[[ "$history" == "$latest:$latest" ]] ||
  die "Expected local disposable V1-V$latest, got $history"
CREDS="$STATE/test-user-logins.txt"
case "${1:-create}" in
  create)
    [[ ! -e "$CREDS" ]] ||
      die "Already seeded. Run: bash scripts/seed-team-dev-accounts.sh verify"
    owner_password="$(openssl rand -hex 24)"
    cashier_password="$(openssl rand -hex 24)"
    [[ "$owner_password" =~ ^[0-9a-f]{48}$ &&
       "$cashier_password" =~ ^[0-9a-f]{48}$ ]] || die "Random generation failed"
    # Passwords only go into the ephemeral container's SQL stdin and private
    # credential file. Never shell argv, Git, output, shared databases or logs.
    psql_local >/dev/null <<SQL
BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
DO \$guard\$
BEGIN
  IF EXISTS (SELECT 1 FROM garage.user_accounts)
     OR EXISTS (SELECT 1 FROM garage.employees WHERE employee_code
                   IN ('TEST-OWNER-LOCAL','TEST-CASHIER-LOCAL')) THEN
     RAISE EXCEPTION 'Refusing to overwrite existing local accounts';
  END IF;
  IF (SELECT count(*) FROM garage.roles WHERE code IN ('OWNER','CASHIER')) <> 2 THEN
     RAISE EXCEPTION 'Required existing role seeds absent';
  END IF;
END
\$guard\$;
INSERT INTO garage.employees(employee_code,nic,first_name,last_name,phone,position,active)
VALUES ('TEST-OWNER-LOCAL','LOCAL-OWNER','Test','Owner','0770000000','OWNER',TRUE),
       ('TEST-CASHIER-LOCAL','LOCAL-CASHIER','Test','Cashier','0770000001','CASHIER',TRUE);
INSERT INTO garage.user_accounts(employee_id,username,password_hash,enabled,identity_kind)
SELECT e.id,
       CASE e.employee_code WHEN 'TEST-OWNER-LOCAL' THEN 'test.owner' ELSE 'test.cashier' END,
       CASE e.employee_code WHEN 'TEST-OWNER-LOCAL'
            THEN '{bcrypt}' || crypt('$owner_password', gen_salt('bf',12))
            ELSE '{bcrypt}' || crypt('$cashier_password',gen_salt('bf',12)) END,
       TRUE,'BUSINESS'
FROM garage.employees e
WHERE e.employee_code IN ('TEST-OWNER-LOCAL','TEST-CASHIER-LOCAL');
INSERT INTO garage.user_account_roles(user_account_id,role_id)
SELECT a.id,r.id FROM garage.user_accounts a
JOIN garage.roles r ON r.code=CASE a.username
  WHEN 'test.owner' THEN 'OWNER' ELSE 'CASHIER' END
WHERE a.username IN ('test.owner','test.cashier');
DO \$verify_seed\$
BEGIN
 IF (SELECT count(*) FROM garage.user_accounts
     WHERE enabled AND username IN ('test.owner','test.cashier')) <> 2
    OR (SELECT count(*) FROM garage.user_account_roles ar
        JOIN garage.user_accounts a ON a.id=ar.user_account_id
        WHERE a.username IN ('test.owner','test.cashier')) <> 2
 THEN RAISE EXCEPTION 'Local user/role verification failed'; END IF;
END
\$verify_seed\$;
COMMIT;
SQL
    {
      printf 'OWNER_USERNAME=test.owner\nOWNER_PASSWORD=%s\n' "$owner_password"
      printf 'CASHIER_USERNAME=test.cashier\nCASHIER_PASSWORD=%s\n' "$cashier_password"
    } > "$CREDS"
    chmod 600 "$CREDS"
    unset owner_password cashier_password
    ;;
  verify)
    [[ -f "$CREDS" && ! -L "$CREDS" && "$(stat -c '%a' "$CREDS")" == 600 ]] ||
      die "Missing private credentials. Run with create first."
    ;;
  *) die "Usage: bash scripts/seed-team-dev-accounts.sh [create|verify]" ;;
esac

owner_password=""
cashier_password=""
while IFS='=' read -r key value; do
  case "$key" in
    OWNER_PASSWORD) owner_password="$value" ;;
    CASHIER_PASSWORD) cashier_password="$value" ;;
  esac
done < "$CREDS"
[[ "$owner_password" =~ ^[0-9a-f]{48}$ &&
   "$cashier_password" =~ ^[0-9a-f]{48}$ ]] || die "Invalid private credential file"

port="${RICHCOMES_TEAM_PORT:-18080}"
[[ "$port" =~ ^[0-9]{4,5}$ ]] || die "Invalid localhost port"
tmp="$(mktemp -d "${TMPDIR:-/tmp}/richcomes-local-login.XXXXXXXX")"
trap 'rm -rf -- "$tmp"' EXIT
login() {
  local username="$1" password="$2" dest="$3" http
  http="$(printf '{"username":"%s","password":"%s"}' "$username" "$password" |
    curl --silent --show-error --max-time 15 -o "$dest" -w '%{http_code}' \
    -H 'Content-Type: application/json' --data-binary @- \
    "http://127.0.0.1:$port/api/auth/login")" ||
    die "Local backend login endpoint unreachable"
  [[ "$http" == 200 ]] || die "$username login returned HTTP $http"
  python3 - "$dest" <<'PY'
import json,sys
data=json.load(open(sys.argv[1],encoding="utf-8"))
assert data.get("tokenType") == "Bearer" and len(data.get("accessToken","")) > 50
PY
}
login test.owner "$owner_password" "$tmp/owner.json"
login test.cashier "$cashier_password" "$tmp/cashier.json"
check_employees() {
  local file="$1" expected="$2" token response
  token="$(python3 - "$file" <<'PY'
import json,sys
print(json.load(open(sys.argv[1],encoding="utf-8"))["accessToken"])
PY
)"
  # Avoid placing test JWT in curl argv or a filesystem file.
  response="$(printf 'header = "Authorization: Bearer %s"\n' "$token" |
    curl --config - --silent --max-time 12 -o /dev/null -w '%{http_code}' \
      "http://127.0.0.1:$port/api/employees")"
  [[ "$response" == "$expected" ]] || die "JWT authorization: expected $expected, got $response"
}
check_employees "$tmp/owner.json" 200
check_employees "$tmp/cashier.json" 403
unset owner_password cashier_password
echo "PASS: two locally generated BCrypt users, JWT login, OWNER allowed and CASHIER denied"
echo "Private passwords file: $CREDS"
echo "On this machine ONLY: cat '$CREDS'"
echo "NEVER copy these passwords or tokens to Git or a group chat."
