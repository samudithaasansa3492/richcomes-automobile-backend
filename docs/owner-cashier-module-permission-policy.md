# OWNER → CASHIER business permission delegation (V22)

## Goal
Each individual CASHIER account starts with no new business-module permissions. OWNER may grant or revoke **specific actions for specific cashier accounts**. CASHIER cannot delegate, modify its own access, read audit logs, manage employee/identity accounts, or acquire any developer ADMIN privileges.

This is an additive feature stacked on PR #29 and the previous migration alignment PR #27. **Not deployed or validated yet.**

## New API

- `GET /api/accounts/{cashierAccountId}/permissions` — OWNER-only; lists explicit CASHIER business grants.
- `PUT /api/accounts/{cashierAccountId}/permissions` — OWNER-only; body `{"permissionCode":"vehicle:read","enabled":true}` grants a single permission. `enabled:false` revokes it. The path account ID is taken from the URL; actor ID is taken from the validated JWT, never the request body.
- Both require `permission:delegate` and role `OWNER` at service layer. The OWNER must also hold the permission being delegated. A target must have exactly the `CASHIER` business role; granting requires it be active and enabled.
- Changes acquire the shared OWNER role lock, update `account_permissions` and append a `garage.account_permission_audit` row (actor, target, exact code, grant/revoke, timestamp) in one transaction. Idempotent re-submission does not create extra audit events.
- JWT role/permission claims are ignored; each authenticated request reloads the account permissions from the DB. Revocation is enforced on the next request.

## Implemented route permission mapping

| Module | Action → permission |
| --- | --- |
| Customer | existing `customer:register`, `customer:read`, `customer:manage` |
| Vehicle | GET `vehicle:read`; POST `vehicle:create`; PUT/PATCH `vehicle:update` |
| Appointments | GET `appointment:read`; POST `appointment:create`; PATCH status actions `appointment:manage` |
| Inventory suppliers/categories/items/GRN | GET `inventory:read`; POST draft/create `inventory:create`; existing PUT/PATCH `inventory:update` |
| Service categories/items/jobs/job-items/job-parts | GET `service:read`; POST `service:create`; PUT/PATCH `service:update`; DELETE `service:delete` |
| Billing | GET invoice `billing:read`; POST calculation `billing:calculate` |
| Reports | JSON dashboards/analytics `report:read`; listed PDF exports `report:export` |
| Service history | GET `history:read`; POST `history:create`; PATCH `history:update`; DELETE `history:delete` |
| Reminders | GET `reminder:read`; POST `reminder:create`; PATCH `reminder:manage`; DELETE `reminder:delete` |
| Notifications | GET `notification:read`; POST send/retry `notification:send`; DELETE `notification:delete` |
| Audit trails | GET `audit:read` — **OWNER only and never CASHIER-delegatable** |

`employee:*`, `account:*`, `permission:delegate`, `audit:read`, `technical:recovery` and `customer:self` are not cashier-delegatable. BUSINESS CUSTOMER may only call `GET /api/customer/me` using its own identity. Developer ADMIN remains limited to technical recovery.

Only current controller methods are mapped for mutation routes. Unimplemented payment/PO/GRN confirm/GRN cancel/stock movement writes and any unrelated methods **remain default-denied**; new endpoints require their own review. This is not a promise that every workflow across all six modules is fully developed.

## Validation and safety

- V22 seeds OWNER authorities only, no CASHIER/CUSTOMER/technical ADMIN business grants. Adds `account_permission_audit` with RLS on and PUBLIC/`anon`/`authenticated` access revoked.
- V22 never modifies existing applied SQL. Verify fresh and V1–V14 upgrade on disposable PostgreSQL via `bash scripts/validate-flyway-isolated.sh`.
- Validate Java 21 selected regression, per-cashier service, JWT authorization and full-context tests with `bash scripts/validate-spring-context-isolated.sh`, never with shared Supabase configuration.
- Do not merge while PR #27 and #29 are unreviewed; after dependency merges, rebase carefully, review security policies and coordinate API names with all feature owners.
- Confirm OWNER-only high-impact actions, report finance visibility and destructive service/history DELETE privileges with stakeholders before production.

## Role change and account disable safeguards

A staff account's previously delegated permissions must never reactivate accidentally after it is reassigned between `CASHIER` and `OWNER`, or disabled and subsequently re-enabled. The business account service now revokes all direct permissions when the role set **actually changes** or an account is disabled, and writes a distinct `account_permission_audit` revocation record (actor, target, permission and time) for every removed grant in the same database transaction. Assigning an unchanged role set does not remove valid grants. Re-enabling a cashier requires the OWNER to grant necessary business permissions explicitly again. These changes were made **after** the 249/249 regression pass and require fresh verification.

## OWNER-only administration hardening and database-level validation

Security policy now **completely ignores shared role permissions for non-OWNER business accounts**. A CASHIER receives effective permissions only from the explicit per-account allowlist and is therefore denied even if someone accidentally gives the CASHIER role `employee:read`, `employee:create`, `account:manage`, `permission:delegate` or `audit:read`. The OWNER still receives the explicitly seeded role permissions; CUSTOMER and technical ADMIN retain their distinct deny-by-default identity boundaries. New JWT filter and pure policy tests guard this.

A new **opt-in `CashierPermissionPostgresIT`** is selected only by `scripts/validate-spring-context-isolated.sh`, with `RICHCOMES_LOCAL_PG_TESTS=YES` and a fresh localhost-bound, throwaway `richcomes_context` PostgreSQL database. Before writes, the test also checks the actual JDBC URL is the named local database. It validates real JPA join rows and audit events for grant, idempotent replay, rollback, revoke, and disable/re-enable revocation. Its own environment guard prevents inadvertent execution in routine Maven runs. **This integration test and the OWNER-only legacy grant fix have been committed but are not yet validated.**

Earlier baseline **252/252 selected regression tests, 3/3 full-context tests, V1–V22 fresh and V1–V14→V22 upgrade PASS** on source commit `6a801616c1354db2d5120f1518189327bb5f4578`. Never attribute that result to newer source commits without running the validation again.

## Live JWT → JPA authorization smoke path (newer than the last PASS)

The opt-in `CashierPermissionPostgresIT` now also uses a signed bearer token, the **real Spring Security filter chain**, and the real `GET /api/v1/vehicles` Controller backed by the disposable PostgreSQL database. It verifies an existing token is **403 before grant**, **200 after committed OWNER grant**, **200 after a rolled-back revoke**, **403 after committed revoke**, **401 while the account is disabled**, and **403 after re-enable without a renewed grant**. No permission values are trusted from JWT claims. These additional assertions require rerunning the isolated Spring context runner; the previously observed PASS belongs to the older integration test.
