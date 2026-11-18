# Flyway safe startup — explicit migration authorization

> **Live dev checkpoint (2026-11-18):** `richcomes-automobile-dev` remains at 14 successful Flyway versions; V15–V23 are not deployed. PRs #35–#37 are merged to `main`. `public.flyway_schema_history` has already been hardened transactionally as the owner: RLS ON, `anon`/`authenticated` SELECT denied, history still V14. Do not repeat this as an unverified future step. The organization's Supabase Free tier does not include scheduled automatic daily backups; there is no confirmed off-site restorable backup yet. Hold all remaining DDL until a backup is exported, independently restored/tested and change-window approval is recorded. Keep `FLYWAY_ENABLED=false` and `CUSTOMER_ONBOARDING_ENABLED=false` until full release readiness.
>

## Default
The Spring Boot API uses `spring.flyway.enabled=${FLYWAY_ENABLED:false}`.

**Starting the application must NOT automatically migrate a shared PostgreSQL/Supabase database.** V15–V23 contain schema, identity, audit, and customer-login changes; applying them is a separate privileged database operation, not an incidental side effect of `java -jar` or `spring-boot:run`.

- `FLYWAY_ENABLED` unset or `false`: Flyway is disabled, so application startup itself does not execute migrations. A schema behind the merged code (such as dev database V14) is **not application-ready** and may fail startup until approved schema work is complete; never bypass that failure by silently enabling Flyway.
- `FLYWAY_ENABLED=true`: enables Flyway for the explicitly authorized migration operator only, after signed-off target/schema-history preflight and verified backup/restore capability. Use controlled environment variables; do not commit real JDBC URLs, usernames, passwords, or key material.
- Existing isolated CI jobs explicitly pass `-Dspring.flyway.enabled=true` for local PostgreSQL Spring-context tests, and the standalone Flyway fresh/upgrade runner builds its own localhost-only Flyway configuration. **This keeps test coverage without granting CI any shared-database credentials.**

## Required migration operator preflight
1. Confirm the exact **target project and environment**, owner-approved maintenance window, verified current backup and restore test, and fallback/forward-fix plan. Code rollback alone does not undo schema migrations.
2. Query `public.flyway_schema_history` **read-only**, verify successful V1–V14 and compare the already applied V12/V13/V14 files to the immutable migration history. Confirm no duplicate version/checksum issues.
3. Review *every* pending V15–V23 migration and its RLS/privilege effects. The shared `richcomes-automobile-dev` database last read-only verified at V14; do **not** assume subsequent source merges applied SQL.
4. Run approved migrations only with scoped database-owner credentials in the approved deployment environment, using a controlled and logged migration job. Never enable Flyway against shared Supabase simply to make a developer's app start.
5. After successful approved migration, verify Flyway success, OWNER/CASHIER/CUSTOMER separation, denial of direct/browser-role access to sensitive tables, and application health. Disable migration opt-in for normal API services again.
6. Separately review `ops/security/lock-down-public-flyway-history.sql` for tightening `public.flyway_schema_history` Data API permissions. This manual database-owner step must not run concurrently with Flyway or before backup authorization.

## Release status
This config change prevents unintended schema mutation on startup. **It does not perform or authorize the actual V15–V23 shared database rollout.** Track proofing/privacy/initial-credential dependencies in Issue #32, module-owner permission signoff in Issue #33, and database approval/backups and migration execution in Issue #34.
