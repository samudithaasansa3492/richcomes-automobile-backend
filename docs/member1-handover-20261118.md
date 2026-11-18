# Member 1 authentication and employee backend — handover

**Checkpoint:** 2026-11-18, merged `main` after PR #40; [GitHub repository](https://github.com/samudithaasansa3492/richcomes-automobile-backend). **Status:** implementation integrated and isolated CI passing; shared dev/production deployment **not** complete. This evidence covers Member 1 backend, not a certification of every team module.

## Delivered components

- Employee operations, user account lifecycle, active-owner protection and technical/OWNER/CASHIER separation.
- RS256 Bearer JWT login and `/api/auth/me`; current DB account status and effective permissions reloaded per authenticated request (no trust of stale permission claims).
- Roles/permission seed and per-CASHIER business authorization with conservative OWNER-only defaults for financial/customer/sensitive messaging operations.
- Customer login identity restricted to own profile, disabled enrollment, recorded verification attestation, 15-minute single-use customer-chosen password, separate OWNER activation, and fail-closed suspension/email-change checks.
- First-party same-origin `/account-setup.html` + JS/CSS supporting initial password, password reset and recovery email confirmation; HTTPS and SMTP deployment still required.
- Isolated PostgreSQL 17 Flyway V1–V23 fresh and V14→V23 upgrade checks; shared database RLS hardening of public migration history; V14 application backup/restore rehearsal tooling.

## Validation evidence (exact source and scope)

- [PR #40 Java 21 / PostgreSQL / Flyway CI](https://github.com/samudithaasansa3492/richcomes-automobile-backend/actions/runs/44246030363): 277 selected tests with 0 failures, plus 5 full-context Spring Boot/PostgreSQL tests with 0 failures; JavaScript first-party recovery form tests PASS.
- Disposable PostgreSQL: V1–V23 fresh, V14→V23 upgrade and role/security constraints PASS. A separate V14 custom-format `garage` schema + public history snapshot was successfully restored into an isolated throwaway PostgreSQL DB with expected row counts.
- Both fresh and upgrade CI require `anon`/`authenticated` to have no direct `garage` schema/table privileges. This test does not establish a real Supabase deployment.
- Shared Supabase `richcomes-automobile-dev` **still at V14**: public Flyway history RLS ON, browser SELECT denied, 14 successful migrations; `garage.user_accounts` absent. No V15–V23 migration or customer onboarding enablement was performed there.
- `FLYWAY_ENABLED=false` and `CUSTOMER_ONBOARDING_ENABLED=false` are default fail-closed settings. Organization Free tier lacks verified scheduled backups, and there is **no independently retrieved off-site backup/restore evidence yet**.

## API/role demo checklist (isolated environment with synthetic accounts only)

1. Build with Java 21: `./mvnw clean test` in an isolated test configuration only; use `scripts/validate-spring-context-isolated.sh` and `scripts/validate-flyway-isolated.sh` with Docker where applicable, **not real shared DB credentials**.
2. Seed OWNER/CASHIER/CUSTOMER in a disposable database. The first OWNER provisioning command must be separately operated and its password entered privately; read [first-admin-provisioning.md](first-admin-provisioning.md).
3. Login: `POST /api/auth/login` with synthetic username/password; inspect expiration, signature and generic failure errors, then `GET /api/auth/me` with `Authorization: Bearer <token>`.
4. Employee endpoints: OWNER with `employee:read/create/update/deactivate` may use `GET /api/employees`, `POST /api/employees`, `PUT /api/employees/{id}`, and `PATCH /api/employees/{id}/deactivate`; demonstrate denied actions without authority, self-change and last-owner protection.
5. Account operations: `POST /api/accounts`, `GET /api/accounts`, `PUT /api/accounts/{id}/roles`, `PATCH /api/accounts/{id}/enable|disable` are governed by named permissions and active-owner safeguards. Never expose password hashes.
6. Per-CASHIER delegation: OWNER grants exact allowed direct permission; show CASHIER denied for unapproved access and immediately denied again following revoke even with the same previously issued JWT.
7. Customer (only when explicitly enabled in **isolated** setup): staff registers a disabled existing active Customer; OWNER records a valid synthetic verification case and sends a synthetic invitation through a fake SMTP sink; customer completes `/account-setup.html` first password; OWNER activates; CUSTOMER can only read own `/api/customer/me`. Suspended/no-proof/email-changed accounts must fail.
8. Recovery: simulate reset/verify-email with single-use purpose-bound tokens, expired/replayed tokens and generic failure responses. Never send real customer messages from isolated CI.
9. Record the specific commit, tests, time, environment, reviewer and demonstration result in the team's acceptance notes.

## Typical failures

- `400 INVALID_REQUEST`: malformed input (including username/password constraints).
- `401`: missing, expired, invalid or revoked JWT (authentication).
- `403`: valid identity lacks permission, attempts protected self-change, or tries disallowed role/status operation.
- `404`: requested employee/account/customer record absent.
- `409`: conflicting account, last-owner guard or customer not eligible for lifecycle transition.
- `503`: backend/database/role unavailable or customer onboarding deliberately switched OFF. Recovery token failures intentionally use a generic response; do not treat error bodies as authentication details.

## Outstanding release work (not claimed as completed)

- [Issue #34](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/34): verified real Free-tier backup/restore, V12–V14 checksum comparison, migration V15–V23 release window, source/target validation and post-deployment security checks.
- [Issue #32](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/32): actually approved ID proofing SOP/evidence custody, configured HTTPS domain and TLS SMTP provider, functional live first-login and support processes.
- [Issue #33](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/33): module owners validate cashier allowlist, scoped PII, destructive actions, notification consent/provider safeguards.
- University assessment rubric, if supplied, must be checked separately for extra required APIs (e.g. refresh token), rather than expanding scope without confirmation.

See [account-management.md](account-management.md), [verified customer onboarding](verified-customer-onboarding.md), [physical identity SOP](customer-identity-proofing-operations.md), and [backup runbook](shared-supabase-backup-restore.md).
