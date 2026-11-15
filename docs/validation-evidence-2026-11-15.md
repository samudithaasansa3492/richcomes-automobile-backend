# Member 1 backend — isolated validation evidence (2026-11-15)

## Scope and evidence basis

Reviewed source commit: `7e26ebc57dad29fdfc11a291551319fffa2c7d8a` on `feature/developer-identity-recovery`.

These are results from interactive GitHub Codespaces terminal outputs supplied by the developer and reviewed on 2026-11-15. The additional PostgreSQL/HTTP/SMTP harnesses were temporary, isolated test programs under `/tmp`; they are **not checked-in repeatable CI tests**. The checked-in selected-test runner is `scripts/validate-developer-recovery.py`. Evidence is accurate to this reviewed commit; subsequent source changes require rerunning the relevant checks.

Test environment: Java 21, Spring Boot 4.1.2, Hibernate 7.4.13.Final, disposable PostgreSQL 17.12 on a Docker container with `--network none`, tmpfs data and no published ports. Application tests ran in containers sharing only that isolated database network namespace. No shared Supabase migration, application startup or data write was authorized or performed as part of these checks.

## Observed successful checks

| Area | Observed result |
| --- | --- |
| Source regression | Java 21 compile succeeded; **153 selected tests passed**, 0 failures, 0 errors and 0 skips using the checked-in validation runner |
| SQL/schema restrictions | V1–V15 smoke validation, identity constraints, role seeds and browser-role/RLS grants checked against disposable PostgreSQL |
| Flyway fresh install | Flyway 13.12.2 applied V1–V15 to fresh database; `flyway validate` passed (15/15) |
| Flyway upgrade | V1–V13 legacy fixture upgraded through V14 and V15; legacy owner identifiers/password hashes preserved and ineligible technician access disabled |
| Technical operator provisioning | Real interactive `DeveloperAdminCommand` created enabled, Employee-independent, sole-ADMIN account plus audit; duplicate username refused with unchanged account/audit counts |
| Login and access | Real HTTP login issued JWT; `/api/auth/me` returned technical identity and only `technical:recovery`; technical ADMIN was forbidden from GET `/api/employees` (403) |
| JWT revocation | Original JWT accepted (200); changing `credential_version` on cloned database rejected old JWT (401) while newly issued JWT worked (200) |
| Recovery JPA transaction/locking | Real PostgreSQL verified-email enrollment, delivery-failure rollback, challenge issuance, exactly-one concurrent reset, audit atomicity and replay rejection |
| Recovery disabled HTTP boundaries | Public known/unknown requests both 202; invalid tokens 400; anonymous protected endpoints 401; technical ADMIN permitted; disabled enrollment rejected; DB state unchanged |
| First OWNER concurrency | Two competing provisioning requests visibly waited on OWNER role lock; exactly one created OWNER and one refused `OWNER_ALREADY_EXISTS` |
| Enabled recovery HTTP end-to-end | Verified-email enrollment/confirmation, cooldown, request/reset, token replay prevention, former JWT invalidated and new-password JWT accepted; expected account state and audit preserved |
| SMTP STARTTLS transport | Local capture SMTP successfully received email with trusted TLS; untrusted certificate and no-STARTTLS listener were refused; negative cases captured no message |
| Last OWNER concurrency | Real PostgreSQL simultaneous account disable vs Employee deactivation waited on role lock; exactly one operation succeeded, other rejected `LAST_ACTIVE_OWNER`, one active OWNER remained |

For SMTP tests, a self-signed certificate was trusted **only** by the disposable Java test process. No production mail provider, actual customer mailbox or Internet mail delivery was tested. For enabled recovery HTTP tests, a capture-only `JavaMailSender` received links; real SMTP STARTTLS was tested separately.

## Boundaries and remaining rollout work

1. **No production approval:** Shared Supabase remains unchanged. Before rollout, approve migration/change window for V12–V15, review backups and actual legacy data, validate intended service-role grants, and ensure application nodes are updated in a controlled order.
2. **External configuration:** Supply real deployment SMTP credentials, sender identity and TLS configuration; verify actual provider delivery and DNS/operational requirements. Set up protected RSA signing keys, issuer/audience, connection secrets and environment management without committing secrets.
3. **Client-side flow:** Implement and review the client-owned HTTPS recovery page and support nullable `employeeId`; protect recovery link fragments from analytics, logs, link previews and unsafe redirects.
4. **Perimeter and retention:** Configure IP/gateway abuse rate limits, monitoring and retention cleanup for expired recovery challenges and security audit events. Plan operational access control and incident recovery.
5. **Broader quality gates:** Obtain peer review, CI/entire-project regression coverage and deployment-specific smoke checks. These local results do not verify every other module, real end-to-end provider TLS, or production resilience.
6. **Operational cleanup:** Remove only the specifically identified, disposable local PostgreSQL/Docker test resources after evidence review. A local resource cleanup is not a production migration or rollback.

## Security outcomes and caveats

- The isolated tests demonstrate that independent ADMIN authentication does **not** imply business data access or OWNER rights.
- Public recovery requests deliberately return generic 202 responses so user existence is not disclosed; invalid link handling remains generic.
- Account-role isolation, durable per-account cooldown, token hashing and single-use redemption were exercised through the intended service or HTTP boundaries.
- Test harnesses ran with synthetic identities/passwords and `.invalid` addresses; interactive account passwords were entered into terminals, not captured in this evidence file.
- This is **review evidence**, not a statement that the application has already been deployed or fully certified production-ready.
