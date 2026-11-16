# Customer login, staff registration and per-cashier delegation

## Identity boundaries

- `BUSINESS` staff: `OWNER`, `CASHIER`, and existing technical-deny behavior. Requires an active non-technician Employee.
- `CUSTOMER`: exactly the `CUSTOMER` role, linked to a single active garage Customer record; **never** an Employee. A customer cannot see other customers or act as staff.
- `TECHNICAL` independent developer `ADMIN`: no Customer or Employee linked and only `technical:recovery`.
- All identities use the existing Spring Boot JWT login endpoint `POST /api/auth/login` and short-lived bearer JWTs. Role claims are not trusted; current DB account state and permissions are loaded for each request. `GET /api/auth/me` now includes nullable `customerId` alongside nullable `employeeId`.

## Privileges and lifecycle

| Method and endpoint | Required effective authority | Description |
| --- | --- | --- |
| POST /api/v1/customers | `customer:register` | Existing Member 2 customer-record creation; staff only |
| POST /api/customer-accounts | `customer:register` | Enroll existing active customer, disabled by default |
| PATCH /api/customer-accounts/{id}/activate | **OWNER role + `customer:register`** | Only OWNER can activate after independent identity verification; delegated CASHIER cannot activate |
| PUT /api/accounts/{id}/permissions/customer-registration | `customer:delegate_register` | OWNER grants or revokes a specific CASHIER's registration permission |
| GET /api/customer/me | `customer:self` | Customer's own limited profile, ID derived from authenticated principal |
| GET /api/v1/customers/** | `customer:read` | Staff-only customer record searches/details |
| PUT or PATCH /api/v1/customers/** | `customer:manage` | Staff-only customer updates/status |

- V21 creates `CUSTOMER` role and permissions. It gives `OWNER` register/read/manage/delegate rights, `CUSTOMER` only self-profile rights and **no grants to the CASHIER role**.
- OWNER must explicitly grant/revoke `customer:register` per-CASHIER account. A revoked permission is denied on subsequent requests even if the cashier still holds an old JWT.
- STAFF registration is two-phase: create a disabled login account, verify customer's identity out of band at the garage, then activate with `PATCH`. **No public customer signup or unverified self-activation endpoint.**
- Customer enrollment only links a pre-existing active Customer; customer lookup/create and vehicle assignment are Member 2-owned workflows. Current code does **not** verify vehicle ownership or expose vehicle/job history to customers.
- Customer `SUSPENDED` status prevents all customer login and invalidates existing access on next request.
- Customer passwords are 15–72 Java characters and max 72 UTF-8 bytes for BCrypt. Passwords are never returned in responses or included in DTO `toString()`.
- Identity creation, activation, and delegation changes produce fixed-code security audit records with the authenticated staff actor. Do not log request bodies, passwords or tokens.

## Example flow

1. OWNER signs in; Member 2 staff endpoint creates the Customer business record if necessary.
2. OWNER optionally grants a particular CASHIER via `PUT /api/accounts/42/permissions/customer-registration` body `{"enabled":true}`.
3. Authorized staff registers existing Customer using `POST /api/customer-accounts` with `{"customerId":7,"username":"customer.one","password":"<secure-initial-password>"}`. Response is disabled, no password.
4. Identity is verified by an approved independent process **outside the API** and evidence recorded under the garage's policy. **OWNER (not CASHIER)** activates using `PATCH /api/customer-accounts/{accountId}/activate`. The initial secret must be delivered via an approved private channel, not email/chat plaintext or application logs.
5. CUSTOMER signs in via existing `POST /api/auth/login`, uses `GET /api/customer/me`; requests for staff-only / other-customer routes are rejected.

## Observed isolated validation (2026-11-17)

A clean detached Codespaces worktree at source commit `f02106a7372a5856bc2706d545da4c99497b458e` reported:

- **Java 21 Maven BUILD SUCCESS; 233 selected automated tests, 0 failures, errors or skips.** New test classes covered customer access policy, disabled-by-default account creation and activation, and per-account CASHIER permission delegation.
- **PostgreSQL 17.12 disposable fresh V1–V21:** 21 successful Flyway migrations and validation PASS.
- **PostgreSQL 17.12 disposable upgrade V1–V14→V21:** 14 + 4 + 3 migrations, validation PASS.
- Role grants for CUSTOMER/OWNER/CASHIER, customer identity constraint and account_permissions RLS checks PASS on both databases. Synthetic legacy OWNER survived upgrade.
- Worktree was clean; **shared Supabase was not modified**.

**Limitations:** The selected tests excluded full Spring Boot context tests; no live SMTP/provider onboarding or full HTTP/JPA application run was claimed. Any new changes after `f02106a` require revalidation.

## Review and release gates

**This is a draft feature, not a completed or deployed capability.** Requires PR #27's reviewed migration ordering and V21. Do not apply migrations to shared Supabase or run the application against it without specific release approval.

- Run Java 21 selected regression tests, new customer model/service/delegation tests and all previously passing tests.
- Validate V1–V21 from empty disposable PostgreSQL and V1–V14 → V21 upgrade; verify role grants, unique account customer constraint, RLS and revocation.
- Add HTTP/JWT integration tests for positive staff permissions, owner-to-cashier delegation, negative customer access, suspended customer and transactional concurrency; run full-context app tests only on disposable PostgreSQL.
- **Identity proofing** is a manual staff obligation in this draft: decide audited evidence/proof standard or approved verified-email/OTP onboarding before production signup. An unchecked UI checkbox alone is insufficient identity verification.
- Review secure initial password delivery and first-login password change/reset process. The existing verified-email recovery facility remains off until configured.
- Finalize Member 2 vehicle ownership verification, per-module permission matrix, and frontend routing. `.anyRequest().denyAll()` remains the default for nonreviewed business modules.

## Migration dependencies

V21 follows V17 identity tables, V18 seeds, V19 OWNER split and V20 recovery schema. `scripts/check-flyway-source-versions.py` and `scripts/validate-flyway-isolated.sh` are extended to include V21 in this branch only. They are **not** evidence that V21 passed until actually run.

## Full-context finding and test separation (2026-11-17)

Codespaces executed the regression suite at `c33e4de5eb56de3f53b32a3a193f169c034cd9cc`: **236 tests PASSED**, zero failures/errors/skips. In `scripts/validate-spring-context-isolated.sh` the full application context loaded with local PostgreSQL 17.12 and **all 21 Flyway migrations validated**. The context-load test passed; the legacy `ServiceCategoryControllerTest` expected HTTP 200 on an **anonymous** `GET /api/service-categories`, but the JWT-protected server correctly returned **401**. Thus overall full-context run was 1 pass/1 failure: a test-versus-security expectation mismatch, not a Flyway or context-init problem.

To preserve meaningful coverage without weakening production security, the feature branch now:
- Asserts that anonymous and invalid-bearer service-category requests return HTTP 401 in the full-context test.
- Keeps a separate, filter-free MVC slice test for controller mapping and HTTP 200 with mocked category service.
- Asserts that even authenticated CASHIER cannot use the unmapped service-category route (HTTP 403) until the module's permission matrix is reviewed.

**Still blocked:** OWNER and authorized CASHIER cannot use service-category endpoints in the actual secured application until those endpoints receive explicit, reviewed permissions and audited delegation. Do not declare full business-module authorization complete or make these routes `permitAll` just to pass tests. The three new/updated tests are awaiting a clean rerun.


## Delegated registration account-state hardening (2026-11-17)

The legacy `PUT /api/accounts/{id}/permissions/customer-registration` delegation now **refuses new grants to disabled or inactive CASHIER identities**. OWNER can still revoke a registration permission from an inactive/disabled CASHIER, including an idempotent revoke when no grant exists; permission catalogue lookups are only required for new grants. Three unit regression tests were added. These commits have **not yet been rerun** on this parent PR branch.

**Activation design review remains required**: role-level authorization is now split — `customer:register` can be delegated for disabled-account enrollment, but activation requires **OWNER role** plus `customer:register` in both the HTTP security filter and service. This still does **not** provide identity proofing evidence or secure initial credential delivery; these remain mandatory production prerequisites.

## OWNER-only activation hardening (2026-11-17 — pending test rerun)

Both the HTTP authorization filter and the account service now require **OWNER role + current database-backed `customer:register`** for `PATCH /api/customer-accounts/{id}/activate`. A delegated CASHIER may enroll a disabled Customer but must never activate a Customer login. Unit and JWT-filter tests cover delegated CASHIER rejection, OWNER without the permission rejection and authorized OWNER activation. Tests have been committed but **not yet executed** on these new branch heads. This is an authorization safeguard, **not proof of identity verification**; secure independent evidence and customer-controlled initial credentials remain release blockers.
