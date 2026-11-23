> **CURRENT SAFETY UPDATE — 2026-11-24:** Shared Supabase has **28** successful Flyway migrations (V27 customer verification and V28 stock transaction store location). The V26-locked release/backup steps below are historical; the current procedure is [V28 Production Release](current-v28-production-release.md). Never run V26-only backup scripts or old migrations on current V28. Production Railway remains gated on current-version independent recovery and runtime secrets.

# Richcomes Spring Boot release — protected container deployment

> **2026-11-21 live state:** connected shared Supabase has **26 successful Flyway migrations** through V26, and the `garage.user_accounts` table exists. V24–V26 scripts are merged to `main`, and the applied source Flyway history was verified against current Git. Railway backend remains staged and not deployed. A verified full recoverable backup of the **current V26 database** is still missing; do not claim older V14 snapshots protect V26 data. This integration does not provision secrets or launch customer traffic.


**Status as of 21 Nov 2026:** container and readiness code are integrated and tested; **Railway backend is staged only and not deployed**. The shared Supabase database is **V26, with 26 successful applied migrations and 33 `garage` tables**. The user-account table exists but there are **zero live user accounts**. An actual off-site independently restored **current V26** backup, hosted HTTPS URL, dedicated app-role JDBC credentials, protected RSA signing key pair, and approved external SMTP have **not** been verified. The exact current operator procedure is [V26 Production Release](current-v26-production-release.md).

## Target architecture

- Deploy this repository as a **Java 21 Spring Boot** service with the root [Dockerfile](../Dockerfile). The build uses a JDK image, installs no user secrets, creates a repackaged executable JAR and runs on a non-root JRE user.
- Expose the container HTTP port supplied by the host's `PORT` variable (default 8080). Let a trusted HTTPS reverse proxy / application host terminate TLS. Never publicly expose PostgreSQL or JDBC.
- Supply **all secrets at runtime via protected host configuration or mounted secret files**, never source, public build logs, copy/pasted commands, or image build arguments.
- Configure hosting health-check path to `GET /actuator/health/readiness`. It requires a usable database and returns only UP/DOWN, no component details. `GET /actuator/health/liveness` is an independent minimal process-status check. All other Actuator endpoints remain inaccessible without explicit authorization and are not web-exposed.
- A startup guard refuses the service if required customer-proof identity columns or the single-use INITIAL_PASSWORD challenge constraint are missing, even when a database connection works. Verify the deployed application against the already-applied **V26 schema**.
- For a hosting provider with an existing Railway account, Railway automatically detects the root Dockerfile and uses its own PORT, and can check `/actuator/health/readiness`. See the official Railway [Dockerfile guide](https://docs.railway.com/builds/dockerfiles) and [health-check guide](https://docs.railway.com/deployments/healthchecks). A private Railway `richcomes-automobile` project and `richcomes-backend` service are **staged** and still have no running deployment or public domain. A separate private Railway backup bucket is provisioned, but it contains no verified Supabase archive. See the [current V26 off-site backup operator guide](current-v26-production-release.md), not the historical V14 guide.

> **Railway staged patch inspection (2026-11-19):** the already-live backup bucket is `richcomes-offsite-backups-live` (S3 API name `functional-locker-mh5j-coszp`). Railway currently also retains an **unneeded staged-create bucket** from the earlier draft in its pending environment patch. Do **not** commit the entire pending environment patch until the duplicate staged-only bucket has been discarded through Railway Dashboard and all required DB/JWT secrets are configured. The connected resource tools could not cancel this staged-only bucket; a failed attempt did not provision it. Commit only an explicitly reviewed application patch, never duplicate buckets or an unready service.

## Mandatory prerequisite before real application traffic

1. **DB recoverability:** select exact target/project, freeze writes, export the current **V26** schema/data using the [current V26 source backup and recovery guide](current-v26-production-release.md), encrypt and store off-site, retrieve and restore independently, review Supabase managed roles/Auth/Storage and any other resources outside the application-only archive, and record operator evidence and rollback plan. **Do not run the historical V14-only backup script on V26.** CI synthetic restores do not establish real recoverability.
2. **DB migration:** the connected source **already reports V1–V26 successfully applied**. Validate all 26 applied script names/checksums against current `main` and confirm the relevant V26 tables, grants, permissions and startup guards. **Do not run V15–V26 again and do not enable Flyway on the hosted API**; `FLYWAY_ENABLED=false` is mandatory for normal services. Any future V27+ migration requires its own separately approved, version-unique change and backup/recovery plan.
3. **Keys:** generate a unique RS256 RSA key pair (2048 bits minimum) using an approved key process. **Railway recommended:** store base64-encoded complete PKCS#8 private PEM as protected `JWT_PRIVATE_KEY_PEM_BASE64` and matching X.509 public PEM as `JWT_PUBLIC_KEY_PEM_BASE64`. The application decodes the protected values **in memory only**, validates signature/key pairing and refuses empty/partial/invalid configuration. Both variables are required for this mode; enter them directly in protected Railway Dashboard settings or an independently approved secret manager, never in chat, code, tickets or build arguments. Existing secret file mounts `JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt-private.pem` and `JWT_PUBLIC_KEY_LOCATION=file:/run/secrets/jwt-public.pem` remain supported as an alternative. Do not mix the sources. Set stable `JWT_ISSUER` and `JWT_AUDIENCE`; JWT signing keys are **not generated by ChatGPT or committed**.
4. **Database connection:** configure `DB_URL` (TLS with correct certificate/hostname), `DB_USERNAME` and `DB_PASSWORD` as host secrets for a dedicated application DB role. Prefer least-privilege role rather than the operator/superuser account. Ensure privilege to the `garage` schema, tables and required sequences, and *no* browser/public roles on sensitive tables. Test read/write under the exact dedicated app role on a disposable database before granting it access to real customer data. Keep the migration operator separate.
5. **Service:** the private Railway `richcomes-automobile` project and `richcomes-backend` service are **already staged** against GitHub `main`. Confirm the current commit and review all staged changes. **An extra staged-only bucket** remains alongside the separately live private backup bucket; remove the unwanted pending bucket in Railway Dashboard before accepting the patch. Verify runtime credentials and independent real V26 restore evidence *before* deployment. Keep `FLYWAY_ENABLED=false`, `CUSTOMER_ONBOARDING_ENABLED=false` and `APP_RECOVERY_ENABLED=false`, deploy to an approved HTTPS domain, and actually check readiness, authentication, authorization and the absence of secret leaks.
6. **Mail + customer ownership:** verify approved sender domain and TLS SMTP credentials; configure `APP_RECOVERY_FROM`, `APP_RECOVERY_PAGE=https://<host>/account-setup.html`, `APP_RECOVERY_ENABLED=true`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, SMTP STARTTLS required. Test a *synthetic* token delivery to an authorized test mailbox first. Never send production credentials by plaintext email.
7. **Identity proofing:** a real garage OWNER and privacy custodian must independently accept the in-person ID-verification SOP, case evidence custody/retention, email ownership process, incident response, legitimate test cases and operator training. A pasted case reference alone is not proof. Only then can a named operator explicitly enable `CUSTOMER_ONBOARDING_ENABLED=true` during controlled rollout and test a single authorized customer identity end-to-end.

## Pre-deployment secrets/config checklist

See [environment example](../.env.example) for **names only**. In particular:

| Config | Requirement |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | V26 source; dedicated restricted app role; JDBC TLS; configured after a **real V26** independently verified backup |
| `JWT_ISSUER`, `JWT_AUDIENCE` | issuer/audience matching signed JWT claims; no hardcoded development values |
| `JWT_PUBLIC_KEY_PEM_BASE64`, `JWT_PRIVATE_KEY_PEM_BASE64` | Railway protected variable values containing complete, matching base64-encoded PEM keys; never reveal secrets. Alternatively use mounted key file paths. |
| `FLYWAY_ENABLED` | `false` for all hosted application services; V1–V26 is already applied, never replay it |
| `CUSTOMER_ONBOARDING_ENABLED` | `false` until real proofing/email/live validation approved |
| `APP_RECOVERY_ENABLED` | `false` until TLS SMTP and approved same-origin HTTPS tested |
| `PORT` | provider-defined; default 8080 |

## Release verification

- [ ] CI exact-commit selected regression suite + Spring signed-JWT PostgreSQL + Flyway fresh/upgrade tests pass.
- [ ] Docker image builds with Java 21 and contains no `.env`, private RSA key or backup dump files.
- [ ] Current live **V26** applied Flyway names and checksums match Git `main`; no duplicate or auto-applied migrations.
- [ ] On disposable **V26** PostgreSQL, the backend boots, readiness reports UP, and health reports unavailable when the DB connection fails.
- [ ] API anonymous probes expose no headers with credentials, no environment variables or detailed component health; `/actuator/env` and `/actuator/health` are denied.
- [ ] Exact deployed host/container image commit, health checks, DNS/TLS certificate, gateway rate limits, reverse proxy headers and security logs reviewed.
- [ ] No customer onboarding until verified mailbox + physical ID procedure + guardian/operator audit approval.
- [ ] Record a **real V26** encrypted backup object key/digest, independent restore PASS, complete managed-resource recovery assessment, reviewer, first-OWNER provisioning approval, and deployment/rollback evidence in [Issue #34](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/34).

**Note:** This is a deployment *package and runbook*, not proof that any host, domain, SMTP provider or off-site backup has been connected. If a provider is not connected to ChatGPT, connecting it requires the user's action. No paid infrastructure is provisioned without a provider-specific confirmed cost.
