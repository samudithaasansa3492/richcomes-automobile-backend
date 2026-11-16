# Account management

All endpoints require a Bearer token. Authorities are loaded from the database on each request.

| Method | Route | Authority | Success |
| --- | --- | --- | --- |
| POST | /api/accounts | account:create | 201 + Location |
| GET | /api/accounts?page=0&size=20 | account:read | 200 |
| GET | /api/accounts/{id} | account:read | 200 |
| PUT | /api/accounts/{id}/roles | account:manage | 200 |
| PATCH | /api/accounts/{id}/enable | account:manage | 200 |
| PATCH | /api/accounts/{id}/disable | account:manage | 200 |

Create accepts employeeId, username, password and roleCodes. Accounts start disabled.
Roles accepts a nonempty roleCodes array and replaces the complete assignment.
Enable/disable require no body. Actor identity comes from the authenticated principal.
Responses contain account metadata and role codes, never passwords or hashes; Cache-Control is no-store.
Listing is sorted by ID, with zero-based page and size 1–100. Its envelope contains
content, page, size, totalElements and totalPages. Collection joins are not used for pagination.

## Access protection

An active owner means an enabled account attached to an active Employee with the
OWNER role. Employee.position is not an authorization role.
Self-disable, self role replacement and deactivating your own linked Employee are forbidden (403).
Removing the last active owner through account disable, role replacement or Employee
deactivation returns LAST_ACTIVE_OWNER (409). Retaining OWNER is allowed for another account.

Enable, disable, role replacement and Employee deactivation acquire a pessimistic write lock
on the same OWNER role row before loading the target. The lock is held until transaction
completion, with READ_COMMITTED isolation so waiting operations see committed predecessors.
All future access-removal paths must follow this same protocol. Direct SQL, role deletion and
external writers do not participate in this protection. Account creation is disabled by default
and cannot remove an existing owner.

The role row must exist before guarded operations; otherwise OWNER_ROLE_NOT_CONFIGURED (503)
is returned. Provisioning the role, permissions and first enabled owner is a separate,
explicitly approved operation. These endpoints do not bootstrap an owner or seed roles.
Only trusted owners should receive account:create/account:manage, because role assignment
can grant privileged access. Role permission definitions must preserve the intended owner
permissions. No shared Supabase migration or data change is included here.

Other errors: invalid requests/unknown roles 400, missing account/employee 404, account conflicts
409, database availability failures 503 with a generic response. No SQL details are returned.

## Validation

Run compilation first, then the selected auth tests plus AdminAccessGuardTest,
AdminAccessConcurrencyTest and EmployeeAdminProtectionTest. The concurrency test uses a private
H2 database and real transactional row locking. It does not establish PostgreSQL concurrency
behavior. Before rollout, repeat simultaneous disable/role-removal/Employee-deactivation checks
against an isolated PostgreSQL database using synthetic accounts and the actual HTTP endpoints.
Shared Supabase and production deployment require separate approval.

## Developer separation and technician eligibility

Only OWNER/CASHIER can be assigned by these APIs. ADMIN targets cannot be enabled,
disabled or reassigned here. ADMIN is not a business superuser and receives no
effective business authorities even if a business grant/OWNER role is attached.
Technician employees cannot receive or enable accounts, replace roles, authenticate,
or retain bearer access. Employee position changes to TECHNICIAN are rejected when
an account exists. Account creation and employee updates also acquire the OWNER lock.
Technical recovery endpoints are not implemented in this change; see role-model.md.
