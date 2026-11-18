# Team members: run backend now, without waiting for shared Supabase

**Use only for local development or a private Codespace.** This setup never contacts the shared Supabase project, does not require Railway, and needs no real customer records. The target is an independent throwaway PostgreSQL 17 + Java 21 backend on localhost, migrated V1–V23 automatically. **Do not use for production or publish port 18080 publicly**.

## Start (one command)

In a clean checkout of the up-to-date `main` branch with Docker Engine, Docker Compose v2 and OpenSSL installed:

```bash
git pull --ff-only origin main
bash scripts/team-dev.sh up
bash scripts/team-dev.sh verify
```

Expected output: readiness UP, 23 successful migrations and private database port. The service can be reached **from that same machine / Codespace** at:

- API health: `http://127.0.0.1:18080/actuator/health/readiness`
- Swagger: `http://127.0.0.1:18080/swagger-ui/index.html`
- Base API: `http://127.0.0.1:18080/api`

If using Codespaces, use its **private** port forwarding for 18080. Do **not** mark the forwarded port Public; keep it available only to authorized team members. An individual team member may use their own private Codespace, avoiding database collisions and needing no shared user credentials.

## How this is isolated

- The Docker Compose file **hardcodes** `jdbc:postgresql://database:5432/richcomes_team?sslmode=disable` on an isolated Docker network. No Supabase JDBC URL, auth credentials or API keys are accepted from the host. The database has **no published port**.
- The database lives on a Docker **tmpfs**. It and all data are destroyed when stopped/removed; never store real data in it.
- Only the local backend HTTP port `127.0.0.1:18080` is published. Its business routes still enforce JWT authorization. A health endpoint returning UP is not an authenticated session.
- On first use, `scripts/team-dev.sh` creates a random *synthetic* PostgreSQL password and throwaway 2048-bit RSA JWT signing keypair under `$HOME/.local/state/richcomes-team-dev`, accessible only to the local operator. Docker mounts the test keys as read-only secrets. The files are **never committed** to source control or included in the Docker image.
- `FLYWAY_ENABLED=true` is permitted **only here** because the database is disposable and isolated. `CUSTOMER_ONBOARDING_ENABLED=false` and recovery SMTP OFF remain unchanged. Normal hosted/shared environments still default to Flyway OFF.
- No customer login is auto-provisioned. To test authenticated flows, use the explicit owner provisioning procedures from [docs/first-admin-provisioning.md](first-admin-provisioning.md) in your own disposable environment, or the existing isolated JWT integration tests; do not create a universal test login in source control.

## Stop and reset

```bash
bash scripts/team-dev.sh status
bash scripts/team-dev.sh down
```

`down` destroys all disposable database content and the synthetic key files. The next `up` starts an empty DB and re-applies V1–V23. To select another local HTTP port, set `RICHCOMES_TEAM_PORT` (4–5 digits), e.g.:

```bash
RICHCOMES_TEAM_PORT=18081 bash scripts/team-dev.sh up
RICHCOMES_TEAM_PORT=18081 bash scripts/team-dev.sh verify
```

If your checkout contains a real `.env`, the script **refuses** to start. Run the dev stack from a separate clean checkout instead of deleting or changing your production connection settings. No one should push `.env`, secrets, private PEMs or any DB backups to GitHub.

## Failure diagnosis

If readiness is not UP, first run `bash scripts/team-dev.sh status`. Confirm Docker/Compose and OpenSSL are present, port 18080 isn't already used, and that `main` includes PR #44. In an authorized private Codespace, use `docker compose -p "richcomes-team-$(id -u)" -f compose.team-dev.yml logs --tail=100 backend` **only after confirming logs don't expose secrets**. Keep logs private. Reset only your own disposable environment using `bash scripts/team-dev.sh down`.

## What is still not complete

This unblocker is **not production hosting**. The connected shared Supabase Free-tier project remains V14, and the release still needs a real independently restorable encrypted backup, V15–V23 controlled migration, an operational HTTPS backend, a private SMTP provider/domain and real customer-verification sign-off. See [release gates](merge-release-gates.md). Team development does **not** have to wait for those operations.
