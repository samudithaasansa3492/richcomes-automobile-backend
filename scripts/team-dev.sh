#!/usr/bin/env bash
# One-command isolated backend with disposable PostgreSQL and synthetic RSA keypair.
# This script MUST NOT connect to or migrate real Supabase.
set -euo pipefail
set +x
umask 077
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
COMPOSE="$ROOT/compose.team-dev.yml"
ACTION="${1:-up}"

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "STOP: $1 is required" >&2; exit 2; }
}
require docker
docker info >/dev/null 2>&1 || { echo "STOP: Docker daemon unavailable" >&2; exit 2; }
docker compose version >/dev/null 2>&1 || { echo "STOP: Docker Compose v2 required" >&2; exit 2; }

# A fixed local-only Compose project name; no host Supabase URL or credentials
# are read from .env, the checked-out repository, or GitHub Actions secrets.
[[ ! -e "$ROOT/.env" ]] || { echo "STOP: remove local .env from this checkout" >&2; exit 2; }
export RICHCOMES_TEAM_STATE="$HOME/.local/state/richcomes-team-dev"
PROJECT="richcomes-team-$(id -u)"
export RICHCOMES_TEAM_PORT="${RICHCOMES_TEAM_PORT:-18080}"
[[ "$RICHCOMES_TEAM_PORT" =~ ^[0-9]{4,5}$ ]] || { echo "STOP: use a local 4-5 digit port" >&2; exit 2; }
case "$RICHCOMES_TEAM_PORT" in 0|80|443|5432) echo "STOP: reserved port" >&2; exit 2 ;; esac

case "$ACTION" in
  up)
    require openssl
    mkdir -p "$RICHCOMES_TEAM_STATE"
    chmod 700 "$RICHCOMES_TEAM_STATE"
    if [[ ! -s "$RICHCOMES_TEAM_STATE/private.pem" ||
          ! -s "$RICHCOMES_TEAM_STATE/public.pem" ||
          ! -s "$RICHCOMES_TEAM_STATE/password" ]]; then
      # Cleanup a partially generated test-only fixture, then atomically rebuild.
      rm -f "$RICHCOMES_TEAM_STATE/private.pem" "$RICHCOMES_TEAM_STATE/public.pem" "$RICHCOMES_TEAM_STATE/password"
      openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
        -out "$RICHCOMES_TEAM_STATE/private.pem" >/dev/null 2>&1
      openssl pkey -in "$RICHCOMES_TEAM_STATE/private.pem" -pubout \
        -out "$RICHCOMES_TEAM_STATE/public.pem" >/dev/null 2>&1
      openssl rand -hex 24 > "$RICHCOMES_TEAM_STATE/password"
      chmod 600 "$RICHCOMES_TEAM_STATE/password"
      # These keys are throwaway LOCAL fixtures only; the parent host directory
      # is mode 0700. Bind-mounted Compose file secrets must be readable by
      # the unprivileged runtime UID 10001 (mode remapping is not guaranteed).
      chmod 644 "$RICHCOMES_TEAM_STATE/private.pem" "$RICHCOMES_TEAM_STATE/public.pem"
    fi
    export RICHCOMES_TEAM_PASSWORD="$(cat "$RICHCOMES_TEAM_STATE/password")"
    chmod 600 "$RICHCOMES_TEAM_STATE/password"
    chmod 644 "$RICHCOMES_TEAM_STATE/private.pem" "$RICHCOMES_TEAM_STATE/public.pem"
    # No real Supabase or external JDBC endpoint is ever configured by Compose.
    docker compose --project-name "$PROJECT" -f "$COMPOSE" up -d --build
    echo "Team backend started at http://127.0.0.1:$RICHCOMES_TEAM_PORT"
    echo "Health: http://127.0.0.1:$RICHCOMES_TEAM_PORT/actuator/health/readiness"
    echo "Swagger UI: http://127.0.0.1:$RICHCOMES_TEAM_PORT/swagger-ui/index.html (authenticated endpoints require a test user)"
    echo "Database is a disposable Docker tmpfs; ALL test data disappear after down."
    ;;
  down)
    if [[ ! -r "$RICHCOMES_TEAM_STATE/password" ]]; then
      echo "STOP: local test state not found; inspect Docker manually" >&2
      exit 2
    fi
    export RICHCOMES_TEAM_PASSWORD="$(cat "$RICHCOMES_TEAM_STATE/password")"
    docker compose --project-name "$PROJECT" -f "$COMPOSE" down --remove-orphans -v
    # Remove keys so next dev start gets a new synthetic identity.
    rm -f "$RICHCOMES_TEAM_STATE/private.pem" "$RICHCOMES_TEAM_STATE/public.pem" "$RICHCOMES_TEAM_STATE/password" "$RICHCOMES_TEAM_STATE/test-user-logins.txt"
    echo "Disposable team database stopped and temporary secrets destroyed."
    ;;
  verify)
    require curl
    [[ -r "$RICHCOMES_TEAM_STATE/password" ]] || { echo "STOP: no local fixture" >&2; exit 2; }
    export RICHCOMES_TEAM_PASSWORD="$(cat "$RICHCOMES_TEAM_STATE/password")"
    response="$(curl -fsS --max-time 8 "http://127.0.0.1:$RICHCOMES_TEAM_PORT/actuator/health/readiness" 2>/dev/null)" ||
      { echo "FAIL: backend readiness not UP" >&2; exit 1; }
    [[ "$response" == *'"status":"UP"'* ]] || { echo "FAIL: unexpected readiness response" >&2; exit 1; }
    count="$(docker compose --project-name "$PROJECT" -f "$COMPOSE" exec -T database \
      psql -U postgres -d richcomes_team -Atc \
      "SELECT COUNT(*) FROM public.flyway_schema_history WHERE success" 2>/dev/null)"
    [[ "$count" == 26 ]] || { echo "FAIL: expected 26 disposable Flyway migrations, got $count" >&2; exit 1; }
    # Neither database ports nor customer data are exposed to the host.
    [[ -z "$(docker port "$(docker compose --project-name "$PROJECT" -f "$COMPOSE" ps -q database)" 5432/tcp 2>/dev/null)" ]] ||
      { echo "FAIL: local PostgreSQL unexpectedly published" >&2; exit 1; }
    echo "PASS: disposable team backend readiness UP; migrations V1-V26; DB port private"
    ;;
  logs)
    container_id="$(docker ps -aq --filter "name=^/${PROJECT}-backend-1$" | head -n 1)"
    [[ -n "$container_id" ]] || { echo "STOP: team backend container not found" >&2; exit 2; }
    docker logs --tail 100 "$container_id" 2>&1 |
      sed -E 's/[A-Za-z0-9_-]{43}/[redacted-token]/g' |
      grep -E 'Exception|ERROR|WARN|started|Started|Flyway|permission|denied|certificate|Could not' |
      tail -n 45 || true
    ;;
  status)
    [[ -r "$RICHCOMES_TEAM_STATE/password" ]] || { echo "STOP: no local fixture" >&2; exit 2; }
    export RICHCOMES_TEAM_PASSWORD="$(cat "$RICHCOMES_TEAM_STATE/password")"
    docker compose --project-name "$PROJECT" -f "$COMPOSE" ps
    ;;
  *)
    echo "Usage: bash scripts/team-dev.sh [up|verify|status|logs|down]" >&2
    exit 2
    ;;
esac
