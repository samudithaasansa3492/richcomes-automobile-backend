# IntelliJ IDEA teammates: test JWT login immediately (NO shared Supabase)

**Fastest safe testing path:** run the already-verified private Docker Compose backend from the IntelliJ IDEA terminal, seed disposable accounts with a separate command, and use Postman or IntelliJ's HTTP Client to call it. Your actual Supabase project is **not** involved. Docker, Docker Compose v2, OpenSSL, curl and Python 3 are required. Run these commands in a Linux / WSL / Git Bash environment with Docker available. From the repository checkout:

```bash
git switch main
git pull --ff-only origin main
bash scripts/team-dev.sh up
bash scripts/team-dev.sh verify
bash scripts/seed-team-dev-accounts.sh create
```

The seed script generates two strong random passwords locally, creates one **enabled synthetic OWNER** and one **enabled synthetic CASHIER** with BCrypt hashes, then **actually calls** `POST /api/auth/login` for both and checks the OWNER can read `GET /api/employees` while CASHIER gets HTTP 403. It **refuses to seed** any database other than the local `richcomes-team-<UID>` Docker Compose database with no published PostgreSQL port and exact V26 Flyway history. It also refuses if *any* user already exists, to avoid replacing existing credentials. It never uses the shared Supabase project, existing team employee or live credentials.

The passwords are **not fixed or stored in Git**. Only on **your own private machine**, read:

```bash
cat "$HOME/.local/state/richcomes-team-dev/test-user-logins.txt"
```

This mode starts a containerized Spring Boot application accessible at `http://127.0.0.1:18080`. **Do not forward port 18080 publicly.** On the same machine, use IntelliJ HTTP Client (create a local untracked `.http` scratch file) or Postman:

```http
### Login as synthetic owner; replace the password with yours from the private file
POST http://127.0.0.1:18080/api/auth/login
Content-Type: application/json

{"username":"test.owner","password":"<YOUR-LOCALLY-GENERATED-OWNER-PASSWORD>"}

### Owner permission test (set token from successful login first)
GET http://127.0.0.1:18080/api/employees
Authorization: Bearer <ACCESS_TOKEN_FROM_LOGIN>

### Cashier login also works; /api/employees is intentionally forbidden
```

**Roles:** OWNER has the approved business permissions; CASHIER does not inherit OWNER privileges and has **no blanket role permissions**. Granting a specific cashier permission is a separate owner-authorized task. ADMIN is for technical recovery only, and CUSTOMER enrollment requires real proofing — no shortcuts or admin/customer dummy accounts are created by this script. This covers the common question “do we have test roles and accounts?”: role definitions were already seeded by V26 Flyway; the two local user accounts are created only when you run the opt-in seed script.

**If you want to debug Java source code using IntelliJ's green Run button**, the above setup runs the backend in Docker, not the IDE JVM. It is ideal for API integration tests, but your uncommitted code edits are not automatically executed until you rebuild the container. For actual IDE step-debugging use a separate approved **local-only PostgreSQL instance** bound to `127.0.0.1`, configure IDE runtime `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_ISSUER`, `JWT_AUDIENCE` and privately generated RSA PEM key locations, and enable Flyway only for that disposable empty database. **Never point an IDE run configuration with `FLYWAY_ENABLED=true` at shared Supabase.** The [team local backend guide](team-disposable-backend.md) provides the supported baseline.

**View local verification again:**

```bash
bash scripts/seed-team-dev-accounts.sh verify
```

**Destroy all local database data, local passwords and throwaway PEM keys:**

```bash
bash scripts/team-dev.sh down
```

Each teammate runs their own separate instance and generates unique passwords. Never send the generated passwords, JWTs or private PEM files to a WhatsApp group, Git repository, tickets or assistant chat. Never run this local test script against a real customer database.
