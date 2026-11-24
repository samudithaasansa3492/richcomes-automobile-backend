# Member 1 — final backend handover (code/test scope, 24 Nov 2026)

**Current reference:** `main` after [PR #69](https://github.com/samudithaasansa3492/richcomes-automobile-backend/pull/69), verified merge `06028d25bdc0e283320db5268df9f152f39b9f67`. **Engineering status:** authentication, employee management, role and account backend code delivered and independently tested in disposable Java 21/PostgreSQL. **Production deployment is NOT complete or certified.** Do not conflate a verified development backend with live Railway hosting.

## What this team member can hand over now

- Employee CRUD: list, get by UUID, create, update and deactivate; secured by `employee:*` permissions and self/last-owner protections for sensitive operations.
- UserAccount CRUD/lifecycle: create disabled accounts, list, replace OWNER/CASHIER role assignments, enable/disable, protect last enabled OWNER; credentials stored as BCrypt, never readable from API.
- Custom Spring Security **RS256 JWT** (not Supabase Auth): username/password login, 15-minute bearer token, `/api/auth/me`; authentication refreshes current account state and permissions from the database. It denies disabled staff, ineligible technicians, unverified customers and improperly linked roles.
- Role/permission seeds: `OWNER`, `CASHIER`, `ADMIN` (technical only), `CUSTOMER`; 45 permission definitions observed on 21 Nov at shared V26 (historical count; not re-counted at V28). CASHIER defaults to no blanket business role grants and receives only explicitly owner-delegated scoped permissions.
- Secure first-OWNER operator procedure; technical ADMIN kept separate from business OWNER. Customer recovery/initial-password support is code-integrated, but live mail/identity-verification operations remain OFF.
- **Team unblocker:** private throwaway Compose PostgreSQL using the latest validated contiguous Flyway source + automatic local RSA PEM pair and one-command synthetic `test.owner`/`test.cashier` creation. No real Supabase account or permanent default password is created.

## Fast IntelliJ IDEA / Postman demo (no shared database)

Prerequisites: local Git clone on current `main`; Docker Engine + Compose v2; OpenSSL, curl, Python 3; Git Bash or WSL/Linux terminal. Execute from the project directory in a **clean checkout without a real `.env`**:

```bash
git switch main
git pull --ff-only origin main
bash scripts/team-dev.sh up
bash scripts/team-dev.sh verify
bash scripts/seed-team-dev-accounts.sh create
```

The script prints PASS only after two real local JWT logins succeed, OWNER's `GET /api/employees` returns **200**, and CASHIER's same request returns **403**. Run `bash scripts/seed-team-dev-accounts.sh verify` to rerun. Username pair: `test.owner` and `test.cashier`. Each user's **random** password is generated and kept privately under `$HOME/.local/state/richcomes-team-dev/test-user-logins.txt` with mode 0600; to see only on the owning workstation run `cat "$HOME/.local/state/richcomes-team-dev/test-user-logins.txt"`. Do not send this file or tokens in chat.

- Health: `GET http://127.0.0.1:18080/actuator/health/readiness`
- Login: `POST http://127.0.0.1:18080/api/auth/login`, JSON `{"username":"test.owner","password":"<your-local-password>"}`
- Current identity: `GET http://127.0.0.1:18080/api/auth/me` with `Authorization: Bearer <accessToken>`
- Employees: `GET http://127.0.0.1:18080/api/employees` with same bearer header
- Account metadata: `GET http://127.0.0.1:18080/api/accounts` with OWNER bearer header
- Access-denial demo: login as `test.cashier` and repeat employee GET — expected 403 (not a backend crash).

For employee creation/update request field constraints and account request/response examples, use [account-management.md](account-management.md), [EmployeeController](../src/main/java/com/richcomes/garage/employee/controller/EmployeeController.java) and Swagger/OpenAPI where enabled. The HTTP app runs in **Docker**, not under IntelliJ's JVM debugger. For stepping through local Java code in the IDE, configure a separately approved **local-only** PostgreSQL database and private RSA pair; never point an IDE `FLYWAY_ENABLED=true` configuration at shared Supabase.

At the end: `bash scripts/team-dev.sh down` destroys local tmpfs database, fixture users, random credentials and throwaway RSA PEM keys.

## First production/business OWNER is a different procedure

Do **not** create a first business OWNER by running the test fixture against shared Supabase or copying `test.owner`. Use the [explicit first-OWNER operator CLI](first-admin-provisioning.md) with an approved active non-technician Employee, owner-approved username, protected password and the correct actual database after full backup/recovery readiness. This command needs a trusted operator, interactive terminal, one-time setup and review; it never runs on normal application startup.

## API inventory and common security outcomes

| Route | Authentication or authority | Expected behavior |
| --- | --- | --- |
| `POST /api/auth/login` | Public | 200 and short-lived RS256 bearer token for valid enabled identity; invalid credentials 401 |
| `GET /api/auth/me` | Authenticated | 200 account identity, current roles and effective permissions; no token/hash |
| `GET /api/employees`, `GET /api/employees/{id}` | `employee:read` | 200 for OWNER / approved explicit grantee, 403 for unprivileged CASHIER |
| `POST /api/employees` | `employee:create` | 201 for accepted employee request |
| `PUT /api/employees/{id}` | `employee:update` | 200 for accepted edit |
| `PATCH /api/employees/{id}/deactivate` | `employee:deactivate` | Guarded against deactivating account owner/self and last active OWNER |
| `GET /api/accounts`, `GET /api/accounts/{id}` | `account:read` | Owner-view account metadata only |
| `POST /api/accounts` | `account:create` | Creates **disabled** account, not an automatic enabled login |
| `PUT /api/accounts/{id}/roles` | `account:manage` | Only supported business role assignments; cannot promote technical ADMIN |
| `PATCH /api/accounts/{id}/enable`, `.../disable` | `account:manage` | Owner-guarded account lifecycle |

General error semantics: 400 invalid request, 401 missing/invalid/expired JWT or bad login credentials, 403 valid token without required permission, 404 unknown resource, 409 conflicting state or last active OWNER protection, 503 missing essential DB/role or deliberately disabled enrollment. Failed login does not reveal whether username exists. Do not disable auth for frontend test compatibility.

## Audited test evidence

- **Latest verified main, 24 Nov:** [CI run 45326977358](https://github.com/samudithaasansa3492/richcomes-automobile-backend/actions/runs/45326977358) on merge `06028d25bdc0e283320db5268df9f152f39b9f67` passed **558 selected Java regression tests and 6 Spring Boot/isolated PostgreSQL tests** (0 failures/errors/skips), including `CashierPermissionPostgresIT`. Verified V1–V28 fresh and V14→V28 upgrade, immutable V1–V28 source filenames/CRC32, disposable restoration of all 33 application table counts, Java 21 Docker build, private Compose readiness and synthetic OWNER/CASHIER JWT allow/deny checks. No shared DB connection configured in CI.
- [PR #69](https://github.com/samudithaasansa3492/richcomes-automobile-backend/pull/69) dynamically derives source-latest migration version for disposable CI and local development; rejects gaps, duplicates, changes to applied V1–V28 SQL and duplicate version claims across open PRs. [Registry #68](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/68) governs new migration reservations. After an approved migration actually reaches shared DB, independently verify its applied filename/CRC32 and extend the immutable baseline in a reviewed change.
- [Recorded 24 Nov shared DB evidence](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/34#issuecomment-7111678897): shared Supabase V28, 33 application tables; V27/V28 applied SQL matches source. This is historical read-only evidence, not a current backup. Member 02 [PR #63](https://github.com/samudithaasansa3492/richcomes-automobile-backend/pull/63) adds V29 and its [isolated CI](https://github.com/samudithaasansa3492/richcomes-automobile-backend/actions/runs/45327123346) passed; PR remains open awaiting review and V29 live application is not confirmed.
- [15 Nov isolated PostgreSQL/HTTP evidence](validation-evidence-2026-11-15.md): overlapping first-OWNER provisioning exactly one success; account disable vs employee deactivation exactly one success with one active OWNER remaining. These temporary harnesses are historical evidence, not a repeatable current CI matrix for every account endpoint and concurrent role-removal path (SCRUM-10). Current selected regression includes `AdminAccessConcurrencyTest` and first-owner command/provisioner tests.

- [Merged PR #55 / exact-head CI run 44878947192](https://github.com/samudithaasansa3492/richcomes-automobile-backend/actions/runs/44878947192): **317 Java regression tests passed (0 failures)**; **6 Spring Boot / isolated PostgreSQL tests passed**; isolated Flyway **V1–V26** and V14→V26 upgrade; Docker Java 21 build; live Compose backend readiness UP and **real synthetic OWNER/CASHIER JWT login + 200/403 permission smoke**.
- [Merged PR #53 inventory V26 integration](https://github.com/samudithaasansa3492/richcomes-automobile-backend/pull/53) and [PR #54 V26 backup tooling](https://github.com/samudithaasansa3492/richcomes-automobile-backend/pull/54) passed independently; their synthetic restoration is **not** a real shared Supabase backup.
- Read-only Supabase check (21 Nov 2026): **26 successful Flyway migrations**, `garage.roles` contains **4 roles**, `garage.permissions` **45 definitions**, and `garage.user_accounts` **0 records**. The shared database does not contain the disposable test users, by design.
- Do not claim every possible business flow or whole-team acceptance was reviewed solely on this evidence.

## Exact handover acceptance (code versus release)

**Code/demo readiness:** All Member 1 API routes and local testing procedure are provided in the repo; isolated Java, PostgreSQL, JWT and access-guard CI are green. Teammates can immediately test modules using a private source-latest backend and locally seeded test identities.

**Still needing business or release decisions:**
1. Actual university assignment rubric review, including whether **refresh tokens, extra role-management endpoints or other specified deliverables** are required. No such requirement has been confirmed from the project context, so this document does not claim rubric acceptance.
2. [Module-owner permission sign-off (#33)](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/33): exact CASHIER workflows/PII/destructive actions/provider messaging. Code intentionally keeps unapproved operations deny-by-default.
3. [Customer proofing & SMTP/HTTPS operational approval (#32)](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/32): live identity evidence custody and customer-owned email setup; keep customer onboarding OFF.
4. [Actual current-version off-site backup and Railway release (#34)](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/34): privately held DB/S3 credentials, independently retrieved/decrypted backup and verified restore; dedicated runtime DB role/TLS, JWT private signing key and production deployment/health checks. Last recorded Railway backend was staged, without a public API. V26 backup scripts are historical and must not be used against V28. [PR #67](https://github.com/samudithaasansa3492/richcomes-automobile-backend/pull/67) contains proposed V28 version-locked backup tooling, still unmerged at this checkpoint; reconcile with current main, review and validate it before an authorized operator runs a real source backup. No real off-site recovery or deployment is certified here.

**Decision requested of the team:** accept the **Member 1 code/API/testing handover** based on the above evidence, or identify a precise missing rubric acceptance criterion; separately own and approve the production rollout. Do not keep all teammates blocked solely because the production host has not yet received secrets and recovery approval.
