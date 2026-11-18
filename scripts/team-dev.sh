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
      chmod 600 "$RICHCOMES_TEAM_STATE/"*
    fi
    export RICHCOMES_TEAM_PASSWORD="$(cat "$RICHCOMES_TEAM_STATE/password")"
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
    rm -f "$RICHCOMES_TEAM_STATE/private.pem" "$RICHCOMES_TEAM_STATE/public.pem" "$RICHCOMES_TEAM_STATE/password"
    echo "Disposable team database stopped and temporary secrets destroyed."
    ;;
  status)
    [[ -r "$RICHCOMES_TEAM_STATE/password" ]] || { echo "STOP: no local fixture" >&2; exit 2; }
    export RICHCOMES_TEAM_PASSWORD="$(cat "$RICHCOMES_TEAM_STATE/password")"
    docker compose --project-name "$PROJECT" -f "$COMPOSE" ps
    ;;
  *)
    echo "Usage: bash scripts/team-dev.sh [up|status|down]" >&2
    exit 2
    ;;
esac
