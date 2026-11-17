# Business API permissions — controller-to-security review

**2026-11-17 — source review on PR #31, not an authorization sign-off.**
This is a map of implemented controller methods observed on the stacked feature branch and the `SecurityConfig` allowlist. It does not claim that every business workflow has been implemented or validated end-to-end. Unrecognized routes/methods intentionally fall through to `.anyRequest().denyAll()`.

## Reviewed methods

| Controller base URL | Implemented controller operations | SecurityConfig permission |
| --- | --- | --- |
| `/api/v1/customers` | POST | `customer:register` |
| `/api/v1/customers` | GET list, ID, NIC, search | `customer:read` |
| `/api/v1/customers` | PUT /{id}, PATCH suspend/activate | `customer:manage` |
| `/api/customer-accounts` | POST disabled enrollment | Delegatable `customer:register` |
| `/api/customer-accounts/{id}/activate` | PATCH activation | **OWNER role + `customer:register`**, identity proofing evidence still pending |
| `/api/accounts/{id}/permissions/customer-registration` | PUT | OWNER-only `customer:delegate_register` |
| `/api/accounts/{id}/permissions` | GET, PUT | OWNER-only `permission:delegate` |
| `/api/v1/vehicles` | GET list/ID/number/customer; POST | `vehicle:read`, `vehicle:create` |
| `/api/v1/vehicles` | PUT ID, PATCH activate/deactivate | `vehicle:update` |
| `/api/v1/appointments` | GET list/ID/status/date/mobile/vehicle; POST | `appointment:read`, `appointment:create` |
| `/api/v1/appointments` | PATCH approve/reject/complete/not-completed | `appointment:manage` |
| `/api/inventory/suppliers` | GET, POST, PUT ID, PATCH activate/deactivate | `inventory:read/create/update` respectively |
| `/api/inventory/categories` | GET, POST, PUT ID, PATCH activate/deactivate | `inventory:read/create/update` respectively |
| `/api/inventory/items` | GET by ID/category/low-stock, POST, PUT ID, PATCH activate/deactivate | `inventory:read/create/update` respectively |
| `/api/inventory/grns` | GET list/ID; POST draft | `inventory:read`, `inventory:create` |
| `/api/inventory/stock-transactions` | GET all/by-item | `inventory:read` |
| `/api/service-categories` | GET list/ID; POST; PUT ID; DELETE ID | `service:read/create/update/delete` respectively |
| `/api/service-items` | GET list/ID/category; POST; PUT ID; DELETE ID | `service:read/create/update/delete` respectively |
| `/api/service-jobs` | GET list/ID/job-no; POST; PUT ID, PATCH status; DELETE ID | `service:read/create/update/delete` respectively |
| `/api/service-job-items`, `/api/service-job-parts` | GET by job; POST; DELETE ID | `service:read/create/delete` respectively |
| `/api/v1/billing/invoices` | GET /{id} only | `billing:read` |
| `/api/v1/billing` | POST /calculate only | `billing:calculate` |
| `/api/v1/reports` | GET dashboard, operational, stock, financial, performance, comprehensive | `report:read` |
| `/api/v1/reports` | GET /export/pdf and report-specific /pdf | `report:export` |
| `/api/v1/history` | GET list/ID/vehicle/job/technician (also GET /test); POST; PATCH ID; DELETE ID | `history:read/create/update/delete` respectively |
| `/api/v1/reminders` | GET list/due/ID/vehicle/customer; POST / and /generate-from-history; PATCH ID/send/cancel; DELETE ID | `reminder:read/create/manage/delete` respectively |
| `/api/v1/notifications` | GET list/ID/customer/vehicle (also GET /test); POST send/retry; DELETE ID | `notification:read/send/delete` respectively |
| `/api/v1/audit-logs` | GET list/ID/entity/date-range/actor/action | OWNER-only `audit:read` (not delegatable) |
| `/api/employees`, `/api/accounts` | Staff employee and identity administration | OWNER role permissions only; shared CASHIER grants ignored |
| `/api/customer/me` | GET own profile | CUSTOMER-only `customer:self` |

## Remaining business/security decisions for module owners

1. **Sensitive data**: Owner must explicitly approve delegating `customer:read`, `customer:manage`, financial `report:read`, `report:export`, `billing:read`, and notification data access to CASHIER. Current model permits OWNER to delegate those exact allowlisted privileges; it is not a data-field-level access policy.
2. **Destructive actions**: `service:delete`, `history:delete`, `reminder:delete` and `notification:delete` are delegatable. Confirm which operations should instead be OWNER-only or require an extra audit/approval workflow.
3. **Incomplete workflows**: GRN approve/confirm/cancel, inventory movement writes, invoice creation/issuance/payment/refund and purchase-order actions do not appear in the reviewed controllers. Do not grant or expose guessed endpoints for them.
4. **Customer enrollment**: CASHIER can enroll disabled customer accounts only after explicit delegation. **Only OWNER** (with `customer:register`) can activate, checked by both the HTTP filter and service. However, the backend still has no independent verified proof-of-identity record for activation. Define how evidence is recorded and how customer-controlled initial credentials are securely delivered before releasing customer logins.
5. **Support/test paths**: `/api/v1/history/test` and `/api/v1/notifications/test` are currently covered by their modules' authenticated GET-read rules. Verify whether these development-only endpoints should be removed before release.
6. **Dual delegation APIs**: `customer:register` may be granted via legacy customer-registration delegation and the newer general-permission API. Both are OWNER-gated and serialize using the OWNER role lock, but currently record audit actions through different audit tables. Review a single canonical admin UI and reporting strategy.
7. **Authorization testing**: Existing JWT tests cover role isolation and a real grant/revoke `vehicle:read` flow. Add module-owner-approved API smoke tests for each sensitive route before production. Database RLS/revoke for identity and audit tables does not replace backend per-request authorization.
8. **Deployment**: PR #27 migrations must be approved and merged before PR #29 (V21), and then PR #31 (V22). Tests ran only against isolated PostgreSQL; never infer shared Supabase migration readiness from unit/context passes.

## Default-deny rule

Any URL/method absent from the exact Spring Security allowlist, including unimplemented writes or future API additions, must remain denied until its corresponding module owner and OWNER/CASHIER permission policy are reviewed. Never mark an endpoint public simply to make a controller test pass.


## Conservative high-impact CASHIER boundary — pre-approval decision (2026-11-17)

Because the module owners have **not** approved CASHIER access to financial/PII and destructive operations, the runtime `CashierDelegatablePermissions.ALLOWED` list now *excludes* `customer:read`, `customer:manage`, `billing:read`, `report:read`, `report:export`, `service:delete`, `history:delete`, `reminder:delete`, and `notification:delete`. These remain **OWNER-accessible** through their existing role permissions and `SecurityConfig` route mappings. No SQL migration or backend module-controller routes were removed.

The **effective permission filter** also ignores historical CASHIER direct-grant rows for these nine operations, even when already persisted, and does not trust role-wide grants or JWT claims. The OWNER's delegation API refuses new grants, but accepts *revocation only* of those known legacy codes, with an audit event on actual change and idempotent no-op when already absent. The endpoint response omits ineffective legacy permissions; an OWNER should plan an audit/cleanup of old rows before wider rollout.

This is a safe **default-deny policy while waiting for module-owner signoff**, not an assertion that the final permission matrix has been approved. Before expanding the allowlist, explicitly document business need, least privilege, audit expectations, and field-level data restrictions. Existing alert/reminder sending actions remain individually delegatable, so outbound communication abuse controls still require module-owner review. The new code and negative-path tests require a fresh Java 21 / disposable PostgreSQL validation on the latest head.
