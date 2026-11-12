# Member 1 roles and first administrator

## Approval and scope

This change adds V13 seed SQL and an explicit operator command. It does not apply V12/V13
to shared Supabase or provision an account there. Shared migration/data changes require
separate approval. **Normal Spring Boot startup runs pending Flyway migrations**, including
V13: do not start the application against the shared database before rollout approval.

V13 creates missing role/permission definitions and adds seven OWNER_ADMIN grants:

| Role | Member 1 permissions seeded |
| --- | --- |
| OWNER_ADMIN | employee:read, employee:create, employee:update, employee:deactivate, account:read, account:create, account:manage |
| CASHIER | None |
| TECHNICIAN | None |

Cashier/Technician business-module permissions need separate team agreement. Login and
`/api/auth/me` remain available to otherwise eligible enabled accounts. Employee.position
is not an authorization role.

The migration uses natural-key conflicts to avoid duplicate definitions/mappings. Existing
IDs, descriptions, extra grants and assignments are retained. It grants these seven
permissions to every existing OWNER_ADMIN role holder, so review existing assignments
before an approved rollout. It does not seed Employees, accounts, passwords or any public
RLS policies. V12 stays unchanged. Flyway applies V13 once; it is not a startup grant reset.

## Preconditions

- Java 21 and dependencies built from the approved branch.
- Original migrations through V13 applied to the specifically approved target database.
- An existing active Employee with no UserAccount, and an unused username.
- No enabled OWNER_ADMIN account linked to an active Employee.
- A trusted operator with database privileges to read/lock the necessary rows and insert
  accounts and account-role mappings. Anon/authenticated Data API roles are not appropriate.
- A real interactive terminal; redirected/piped password input is refused.

If the database has no Employee, arrange a separately approved Employee provisioning step.
This command does not insert an Employee, alter roles or grant missing permissions.

## Build (no application startup)

From the repository root:

```sh
./mvnw -DskipTests package dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/provisioning-lib
```

This builds the command and copies runtime dependencies; `-DskipTests` is not test validation.
The Spring Boot plugin explicitly keeps `GarageApplication` as its normal entry point.
The command lives outside its component scan and has no Spring runner or controller.

## Explicit target configuration

Create an operator-owned file outside the repository, with only these properties. Example
for an isolated disposable PostgreSQL server (replace host/port as appropriate):

```properties
jdbc.url=jdbc:postgresql://127.0.0.1:5432/validation?sslmode=disable
jdbc.username=disposable
```

For an approved non-local target, use the appropriate TLS mode, normally `verify-full`
with the required trust configuration. The driver defaults to `sslmode=require` when no
mode is specified by this command. `sslmode=disable` is for isolated test environments.
Only a single PostgreSQL host/database and an optional sslmode query are accepted.
No password property, embedded URL credentials or arbitrary driver options are allowed.

Do not put passwords in arguments, source, shell history or environment variables.
The command loads only this explicit file, not application.properties or .env. It starts
neither Spring Boot nor Flyway and does not require JWT signing keys.

## Run explicitly in a terminal (Linux / Codespaces)

```sh
java -cp 'target/classes:target/provisioning-lib/*' com.richcomes.provisioning.FirstAdminCommand --config /absolute/path/to/target.properties
```

The command displays the database host/port/name and requires `CREATE FIRST ADMIN`.
It asks for Employee UUID, username, then hidden database password, hidden new account
password and hidden confirmation. The account password must be 15–72 Java characters
and at most 72 UTF-8 bytes, consistent with the existing Account API and BCrypt limit.
Passwords are not trimmed or silently truncated. Password arrays are cleared after use;
JDBC/encoder internals can still create temporary immutable strings.

Success prints only the newly created account ID. The account is enabled and receives
OWNER_ADMIN. Use normal authenticated Account APIs for subsequent management.

Exit codes: 0 success; 2 usage/cancellation/password-confirmation failure or no terminal;
3 domain refusal; 4 configuration/database/unexpected failure. Arbitrary exception text,
SQL parameters, passwords, hashes and stack traces are not printed. For connection or commit
failures, inspect actual target state through an approved diagnostic procedure before retrying;
a lost connection during commit can leave the outcome uncertain. There is no automatic retry.

## Atomicity and repeat execution

A dedicated JDBC transaction uses READ_COMMITTED and locks the same OWNER_ADMIN role row
as AdminAccessGuard before checking active-owner count. The Employee row is also locked.
The command verifies required owner permissions, active Employee and account uniqueness,
then inserts the encoded account and role mapping and commits together. Failure rolls back.
Statements have a 15-second query timeout; driver connect/socket timeouts are 10/30 seconds.

A second concurrent command waits for the role lock and refuses if the first created an
active owner. Existing enabled/disabled accounts are never overwritten or promoted by this
tool. If only inactive/disabled owners exist, a new account on a different eligible Employee
can be provisioned explicitly; this is not a password-reset mechanism. Direct SQL and future
access-changing code must respect the locking protocol to preserve these guarantees.

## Validation required before shared rollout

- Selected regression tests plus FirstAdminProvisionerTest, FirstAdminCommandTest and
  ProvisioningTargetTest. H2 tests exercise the command service, transaction rollback and
  concurrent creation, but do not validate PostgreSQL seed SQL or its locking behavior.
- On isolated PostgreSQL 17.11, original Flyway V1–V13, seed contents and unchanged privileges.
- Re-execute the seed SQL in an isolated test transaction and confirm no duplicate rows,
  no description/extra-grant loss and stable existing IDs; roll back that test transaction.
- Run the actual console command with synthetic hidden inputs in a PTY, validate enabled
  owner creation, matching PasswordEncoder hash and normal API login. Capture no secrets.
- Verify refusals for existing active owner, duplicate links/usernames, inactive Employee,
  missing role/permissions and invalid passwords; verify no changes after rejection.
- Run two provisioning processes against distinct Employee targets with real overlapping
  PostgreSQL lock waits; exactly one succeeds and one refuses with ADMIN_ALREADY_EXISTS.
- Confirm normal application startup never invokes provisioning and cleanup removes only
  test-owned resources. Shared Supabase is outside isolated validation authorization.
