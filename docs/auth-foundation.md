# Identity and access foundation

This change adds persistence only. It does not implement login, JWT issuance,
password encoding, account creation endpoints, authorization enforcement or seeds.
Spring Boot owns application identities; this is not a Supabase Auth integration.

## Model

- An employee may have zero or one account. Every account references an employee.
- Account IDs are BIGINT, compatible with the current audit actor column; employee
  IDs stay UUID. No audit foreign key or existing audit schema is changed here.
- Accounts have multiple roles, and roles have multiple permissions. Job positions
  do not implicitly grant roles. Codes are stable, unique identifiers.
- Usernames are normalized to lowercase and trimmed before storage and lookup.
  They contain 3–80 ASCII letters/digits/dots/underscores/hyphens, beginning with a
  letter or digit. No email-login convention is implied.
- New accounts are disabled until explicitly enabled. The future authentication
  service must check both account.enabled and employee.active.
- The future service must encode passwords before constructing accounts or calling
  changePasswordHash. The entity validates presence/length, not cryptographic format.
  Entities must never be returned as controller responses. Password hashes are
  additionally ignored by Jackson and excluded from generated logging methods.
- Shared roles, permissions and employees have no JPA cascading delete from an
  account. Join rows are unique. Collections are lazy except explicit lookup graphs.
- JPA callbacks maintain account timestamps for entity updates. Native SQL changes
  must set updated_at themselves; role membership changes do not timestamp accounts.

## Migration approval boundary

V12 is an unapplied Flyway migration. V1–V11 remain unchanged. Do not start the app
against the shared database until the migration has been reviewed and approved:
Flyway is enabled in application.properties and startup will apply pending migrations.
Check other team branches and the live Flyway history for a version collision first.

V12 creates five tables in garage and enables RLS without browser policies. It
revokes PUBLIC/anon/authenticated access to the new tables and identity sequences.
Verify the backend DB role owns the tables or has an explicitly approved server-side
access model before applying. Do not grant browser roles access to password hashes.
No existing table data, roles or passwords are seeded or modified.

## Safe validation in Codespaces

From the project root, without starting the application:

```bash
./mvnw clean compile
./mvnw -Dtest=IdentityModelTest,IdentityPersistenceTest test
```

The selected persistence test creates its own in-memory H2 datasource and scans only
identity and employee entities. It does not load application.properties, .env,
GarageApplication, or Flyway, and cannot use DB_URL. It checks normalized username
uniqueness, employee/account uniqueness, role/permission uniqueness, relationship
fetches, lifecycle timestamps, and non-cascading deletion.

These tests do not validate PostgreSQL-specific CHECK constraints, RLS, grants or
Flyway migration execution. Validate V12 on an isolated PostgreSQL instance before
shared application. Do not run the unfiltered test suite against shared credentials:
the existing GarageApplicationTests loads the full application context.

## Remaining work

1. Decide role/permission assignments and controlled first-admin provisioning.
2. Add account service/DTOs, password encoder, validation and consistent API errors.
3. Add login/JWT configuration and enforce employee/account status and permissions.
4. Define refresh/logout/revocation behavior and test unauthorized/forbidden paths.
5. Resolve UUID employee audit identifiers separately (audit entity_id is BIGINT).

## Account service phase

`UserAccountService` now provides internal create/get/replaceRoles/enable/disable
operations. Request validation also runs inside the service, so it does not depend
on a future controller's @Valid annotation. Domain error codes are carried by
AccountOperationException; future HTTP handlers must map these to safe responses.

Creation normalizes usernames, resolves existing roles, and encodes passwords with
an explicitly configured bcrypt-only DelegatingPasswordEncoder (cost 12). Tune the
cost against deployment hardware before production. Passwords must be 15–72 Java
characters and at most 72 UTF-8 bytes. Passwords are never trimmed or truncated.
DTO logging redacts the raw password; request serialization treats it as write-only.
Responses do not contain password/hash fields. Do not log request bodies or raw
validation exceptions in the future HTTP layer.

New accounts remain disabled. Creating a disabled account for an inactive employee
is allowed, but enabling it is rejected. Enabling also requires at least one role.
Role replacement requires a nonempty set and validates every role before mutation.
Role/permission seeds and initial administrator provisioning are not included.

These methods are NOT exposed as HTTP endpoints and do not yet authenticate callers.
Before exposing mutations, implement administrator permission checks, concurrency
control for administrative updates, and protection against removal/disable of the
last administrator. Login/JWT must recheck employee.active and account.enabled;
this service alone does not enforce access on requests or revoke existing tokens.

Compile and run only the isolated identity tests (no shared DB connection):

```bash
./mvnw clean compile
./mvnw -Dtest=IdentityModelTest,IdentityPersistenceTest,UserAccountServiceTest test
```

UserAccountServiceTest uses mocked repositories and a real Bean Validator, with a
real encoder test for salted hashing and password matching. It does not start Spring
Boot or Flyway. Transactional service integration and HTTP security are later checks.
