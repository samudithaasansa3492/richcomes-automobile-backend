# Staff access model

Confirmed clarification, 2026-11-14: ADMIN means the development/support team;
OWNER means the client. The proposal's combined Owner/Admin heading is superseded
by this clarification. Technicians are employees assigned to jobs, not system users.

| Identity | Intended access |
| --- | --- |
| OWNER | Business administration; approved business permissions |
| CASHIER | Only operational permissions approved by the owner |
| ADMIN | Technical support, recovery, maintenance and security, without business data |
| TECHNICIAN | Employee record only; no role, account or login |

## Implemented boundary in this change

V14 preserves earlier migration checksums, renames OWNER_ADMIN to OWNER (including
employee position), creates ADMIN with no grants, disables legacy technician
accounts and removes the technician system role. It aborts for ambiguous existing
OWNER/ADMIN roles or legacy owners linked to technician employees. Review such data
explicitly; never repair Flyway history or delete client data to bypass the abort.

The normal account API can assign only OWNER/CASHIER. It cannot mutate technical
ADMIN accounts; employee mutation endpoints also reject linked technical identities.
Technician account creation, enablement, role reassignment and
login are rejected. Changing an employee with an account to TECHNICIAN is rejected;
account creation and employee position updates serialize on the OWNER role lock.
Existing technician bearer tokens are rejected on the next request.

ADMIN effective business permissions are empty, even when a business grant or an
OWNER role is accidentally attached. There is no role-name superuser bypass.
Last-owner protection and the explicit bootstrap command now refer to OWNER, not
ADMIN. The legacy Java class names FirstAdminCommand/FirstAdminProvisioner remain
for invocation compatibility; confirmation now requires CREATE FIRST OWNER.

## Not yet implemented; do not claim full developer administration

- Independent developer identity provisioning (current UserAccount requires an
  Employee; do not create synthetic garage employees for developers).
- Separate technical account metadata endpoints, audited recovery, session/token
  revocation, technical lock/unlock and recovery authorization controls.
- Redacted health/security-log interfaces with a reviewed technical permission
  allowlist. ADMIN currently has no technical operation grants or endpoints.
- Owner-managed cashier permission configuration and other-module permission mapping.

Technical support must not let an administrator impersonate the owner or grant
itself business roles. Recovery design needs to preserve this boundary: an admin
must not set a client password it can subsequently use to access business data.

GitHub, deployment, database migrations and backup/restore are infrastructure
permissions, not JWT application permissions. Do not expose arbitrary SQL, shell,
logs, backups, environment variables or secrets in a technical admin API.

## Rollout and verification

This change does not apply migrations to shared Supabase. Test V1-V14 on disposable
PostgreSQL, including upgrade from V13 with synthetic legacy role assignments,
ambiguous mapping rollback and simultaneous owner-removal operations. Run the
selected authentication/account/provisioning tests before rollout. Previous V13
validation does not validate V14 or the new access model.

## Reproducible selected validation

On a clean checkout of the reviewed commit, select an existing Java 21 JDK in
`JAVA_HOME`, then run:

```sh
bash scripts/validate-role-separation.sh <full-reviewed-commit-sha>
```

The script checks the exact HEAD and clean tree, compiles first, and stops before
tests on any compile/dependency failure. It runs exactly twelve named test classes,
checks their Surefire XML counts, rejects missing/extra/failed/skipped reports, and
checks that HEAD and the working tree remain unchanged. It does not launch Spring
Boot, Flyway, provisioning or Docker. The selected tests use mock repositories or
private H2 databases, not application.properties or a shared database.

Local preparation: `git diff --check` and shell syntax checks passed. Compilation
could not start because Maven Central DNS resolution failed while resolving the
Spring Boot parent POM. No Java test execution or PostgreSQL V14 validation is
claimed for this change. Codespaces validation is required before integration.

API compatibility changes: EmployeePosition OWNER_ADMIN becomes OWNER; account
errors LAST_ACTIVE_ADMIN and ADMIN_ROLE_NOT_CONFIGURED become LAST_ACTIVE_OWNER
and OWNER_ROLE_NOT_CONFIGURED. Provisioning refusal ADMIN_ALREADY_EXISTS becomes
OWNER_ALREADY_EXISTS. Coordinate frontend inputs and error handling before rollout.
