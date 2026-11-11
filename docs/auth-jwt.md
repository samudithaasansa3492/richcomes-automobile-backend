# Login and JWT phase

## Scope and startup boundary

This phase adds login and bearer-token authentication. It does not apply migrations,
seed roles/permissions/accounts, provision an administrator, or add account-management
HTTP endpoints. V12 remains subject to shared database approval. Do not start the
application with shared database credentials before that approval: Flyway is enabled.

Required external configuration (no default keys or automatic key generation):

| Environment variable | Value |
| --- | --- |
| JWT_ISSUER | Stable issuer identifier, typically an application URL |
| JWT_AUDIENCE | Stable API identifier |
| JWT_PUBLIC_KEY_LOCATION | Spring resource URL for an external X.509 public PEM, e.g. file:/secure/richcomes-public.pem |
| JWT_PRIVATE_KEY_LOCATION | Spring resource URL for an external PKCS#8 private PEM, e.g. file:/secure/richcomes-private.pem |

Use a matching RSA pair of at least 2048 bits. Keep the private key outside the
repository, restrict file permissions, and supply it through deployment secrets.
Never commit PEM files or paste private keys into logs/chat. Missing configuration
or unusable keys prevents normal startup. Compile and the selected tests below do
not require these variables or keys. No production key files are generated here.

## API contract

POST /api/auth/login accepts username and password. Successful login returns:

```json
{"accessToken":"<signed-token>","tokenType":"Bearer","expiresIn":900}
```

Username is normalized using the existing account rule; password is never trimmed.
Invalid credentials, disabled accounts and inactive employees return the same 401
message. Unknown users perform a dummy password comparison. Invalid JSON/DTO input
returns a generic 400 without echoing rejected values. Login responses use no-store.
This is not a complete anti-enumeration or brute-force defense; deployment rate
limiting must be added before public exposure.

GET /api/auth/me requires Authorization: Bearer <token> and returns account ID,
employee ID, username, current roles and permissions. No password hash or token is
included in that identity response. Token responses and login DTOs redact secrets
from toString output; do not enable HTTP request/response body logging for auth.

## Token validation and current authorization

- RS256 only. The JWT contains sub (account ID), iss, aud, iat, nbf, exp and jti.
- Lifetime is exactly 15 minutes; validation uses zero clock skew. Keep server clocks
  synchronized. Missing required claims, invalid/overflowing subjects, wrong
  issuer/audience/signature, future or expired tokens are rejected.
- Spring Security's resource-server filter performs bearer extraction/validation.
  Tokens in query parameters are not accepted. Basic/form login and server sessions
  are disabled. CSRF is disabled only because this API accepts authentication via
  explicit Bearer headers, not cookies. Revisit CSRF if cookie auth is introduced.
- On each authenticated request, the account, employee, roles and permissions are
  loaded from the DB. Disabled/deleted accounts and inactive employees are rejected.
  Token claims cannot grant roles/permissions. DB outages must not grant access.
- No permission cache is added. This trades database work for current access checks;
  measure connection-pool usage before rollout. A concurrent change after the request
  has passed authentication cannot retroactively cancel that in-flight request.

## Route policy

| Method | Route | Required access |
| --- | --- | --- |
| POST | /api/auth/login | Public |
| GET | /api/auth/me | Valid authenticated account |
| GET | /api/employees and /api/employees/{id} | employee:read |
| POST | /api/employees | employee:create |
| PUT | /api/employees/{id} | employee:update |
| PATCH | /api/employees/{id}/deactivate | employee:deactivate |
| Any | All other routes/methods | Denied |

OWNER_ADMIN has no implicit bypass; its database role must have explicit permissions.
Other team modules, health endpoints and Swagger are denied until explicitly mapped.
Review those mappings with the team before deployment. No cross-origin frontend
allowlist is configured yet. Permission checks here are action-level; requirements
for record-level access restrictions need separate implementation.

## Logout and revocation limitations

There is no server logout endpoint, refresh token, token denylist or session table.
Client logout only discards its token. A copied token can remain valid until expiry.
Account/employee disable blocks subsequent requests while disabled; re-enabling
before token expiry can make an old token usable again. A password change does not
revoke tokens. Do not describe this phase as immediate per-session revocation.
Replacing the configured key invalidates old tokens after rollout, but coordinated
key rotation, refresh rotation and session revocation remain future work.

## Isolated validation

```bash
./mvnw clean compile
./mvnw -Dtest=IdentityModelTest,IdentityPersistenceTest,UserAccountServiceTest,JwtTokenTest,AuthenticationServiceTest,SecurityFlowTest test
```

Expected test count: 66 (30 prior tests plus 36 new tests). Always use actual results.
The new tests generate RSA keys in memory, use a fixed clock and mocked repositories,
and exercise the real JWT encoder/decoder, security filter chain and auth controller.
SecurityFlowTest uses employee route probes to check permissions; it does not test
real Employee CRUD integration. IdentityPersistenceTest continues to use private H2.
None of these contexts load application.properties, .env, Flyway or Supabase.

This does not validate full application startup, shared PostgreSQL integration,
existing modules' full test suite, key deployment or production readiness. Check
remaining role provisioning, administrator lockout protection, rate limiting,
frontend CORS and revocation requirements before production use.
