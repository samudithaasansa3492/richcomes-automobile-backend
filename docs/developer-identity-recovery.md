# Developer identity and verified-email recovery

## Scope and boundaries

V15 adds independent `TECHNICAL` accounts with no garage Employee. Existing accounts
remain `BUSINESS`; nothing silently converts a client account into a developer.
Only an enabled technical identity with exactly the ADMIN role authenticates as a
technical user. It receives the explicit `technical:recovery` permission, regardless
of accidental business grants. Adding OWNER/CASHIER to a technical account makes it
unusable. Business accounts cannot acquire that technical permission through grants.

Normal business account creation still requires an Employee and accepts only
OWNER/CASHIER. Business account list/detail do not expose independent technical
identities; business mutations cannot change them. Technicians still cannot log in.
`/api/auth/me` returns a null employeeId for technical identities. Frontend consumers
must support that field being null. No maintenance shell/SQL, backups, business data,
impersonation, password-setting or role-escalation capability is exposed to ADMIN.

## Operator provisioning

Apply approved V1–V15 migrations first. Use the existing standalone provisioning
classpath arrangement in `first-admin-provisioning.md`, replacing its entry point
with `com.richcomes.provisioning.DeveloperAdminCommand`:

```
java -cp 'target/classes:target/dependency/*' com.richcomes.provisioning.DeveloperAdminCommand --config /absolute/path/to/target.properties
```

This requires a real terminal, the explicit `CREATE DEVELOPER ADMIN` confirmation,
a new username and hidden database/account passwords. Do not pass passwords on the
command line or in config files. The target parser and TLS/timeout rules are the
same as the owner operator command. No Employee UUID is requested. A transaction
creates the account, its sole ADMIN assignment and a fixed audit event. Any failure
rolls them all back. It never overwrites an existing username. Secret input arrays
are cleared on all exits. No Spring startup, Flyway invocation or initial email
verification happens in the command. The developer enrolls their own verified email
after login. Keep infrastructure operator access and its human audit trail separate
from application permissions; this audit event records an external operator, not a
claimed application actor.

## Email enrollment and recovery

| Method and endpoint | Access | Input / result |
| --- | --- | --- |
| POST /api/auth/recovery/email | Authenticated account | Current password and new email; sends verification link |
| POST /api/auth/recovery/email/confirm | Link holder | Token; verifies the recipient-controlled address |
| POST /api/auth/recovery/request | Public | Username; always empty 202 for valid requests |
| POST /api/technical/recovery/request | Independent ADMIN only | Username; same generic response, actor audited |
| POST /api/auth/recovery/reset | Link holder | Token and new password; consumes link, no auto-login |

All successful responses are empty 202 with Cache-Control no-store. Invalid or
expired reset/verification links produce a generic 400 without input values.
Enrollment requires the account's current password; ADMIN cannot provide or change
another account's recovery address. Existing unverified Employee emails are never
trusted or copied automatically. A reset request sends only to the address already
confirmed by its recipient. Neither an API response nor ADMIN receives the token.
Unknown, unverified, disabled and throttled accounts receive the same request
response. SMTP delivery failures also have the same public request response.

Challenges contain 256 bits of randomness; only SHA-256 digests are persisted.
They expire at 15 minutes, are purpose-bound and single-use. The latest issued link
invalidates all earlier challenges for that account. A durable per-account 60-second
cooldown limits email delivery. Apply gateway/IP rate limits before public rollout;
this backend limit is not a distributed perimeter abuse-control system.

Redemption locks and refreshes the account under READ COMMITTED before checking the
challenge. Concurrent uses can commit only once. Reset requires a still-usable
account, matching credential version and unchanged verified destination. It cannot
enable accounts, reactivate Employees or change roles. Reset increments credential
version, so prior JWTs are rejected on their next request. Legacy JWTs without `cv`
work only at version zero until expiry or the first revocation. A JPA row version
prevents concurrent ordinary updates overwriting recovered credentials. All app
nodes must use V15-capable code before creating independent technical accounts.

Audit rows contain only fixed event codes, actor/target IDs and timestamps, never
addresses, credentials or tokens. Audit writes are in the same transaction as the
change. Tables have RLS enabled with no browser policies or PUBLIC/anon/authenticated
grants. Expired unused challenge rows should be removed by an approved operational
retention task; audit retention needs a separate policy. SMTP failure rolls back
issuance; an email delivered before a later DB rollback contains an unusable link.

## Delivery configuration and frontend contract

Recovery is off by default. Set `app.recovery.enabled=true` only after configuring:

- Spring Mail SMTP host, port, username and secret through deployment secret settings.
- `app.recovery.from`: authorized sender address.
- `app.recovery.page`: a fixed client-owned HTTPS URL, without user info, query or fragment.

The transport requires TLS (implicit SSL or required STARTTLS), hostname checking,
and bounded connection/read/write timeouts. Mail debug logging is disabled.
Configuration must not enter git. Never configure a request-controlled redirect URL.
No real email is sent by the included tests.

The client page receives `#purpose=VERIFY_EMAIL&token=...` or
`#purpose=RESET_PASSWORD&token=...`. It must remove the fragment from browser history,
avoid analytics/third-party scripts, and POST the token in the JSON request body to
the matching endpoint. Do not consume a token via GET or automatically submit reset
on page load (email scanners must not spend tokens). Password confirmation belongs
in the client UI; the backend enforces 15–72 characters and at most 72 UTF-8 bytes.
Request/body logging must not record these sensitive endpoints. After reset, direct
the recipient to normal login. The frontend and production SMTP setup are separate
deployment work, not performed by this backend change.

## Validation status

**Validated against source commit `7e26ebc57dad29fdfc11a291551319fffa2c7d8a` on 2026-11-15.**
See [isolated validation evidence](validation-evidence-2026-11-15.md) for observed checks, precise boundaries and rollout limitations. These results supersede the earlier pre-fix validation notes for commit `2b39df1`.

- Java 21 compilation and 153 selected tests: **passed**, zero failures/errors/skips, using `scripts/validate-developer-recovery.py`.
- Disposable PostgreSQL 17.12: V1–V15 Flyway fresh and legacy upgrade validation; identity/RLS constraints and seed checks **passed**.
- Real interactive technical ADMIN provisioning and duplicate refusal; real HTTP login, technical/business separation, JWT revocation **passed**.
- Recovery transactions on PostgreSQL: single-use token concurrency, delivery rollback, verified-email logic **passed**.
- HTTP recovery disabled and enabled flows, including email confirmation, cooldown, reset and JWT revocation **passed** with capture-only mail delivery.
- Local STARTTLS transport: trusted TLS delivered; untrusted certificate and missing STARTTLS were rejected. Real provider-specific email delivery **not yet validated**.
- PostgreSQL OWNER provisioning race and last-active-OWNER access-removal race **passed** with observed blocking locks.

These were developer-supplied Codespaces outputs, including temporary one-off integration harnesses, not a statement that all checks are checked into CI or that shared Supabase has been changed. **Production release still requires explicit schema approval, migration/data review, deployment configuration, frontend integration, abuse controls, external SMTP verification, CI and operational review.**

Run `python3 scripts/validate-developer-recovery.py <reviewed-full-commit-sha>` in a clean Java 21 checkout to reproduce the selected regression suite. Before production, repeat applicable integration tests against an approved nonproduction environment. Never use the shared Supabase database to replay disposable-test setup.
