# PR #27 → #29 → #31: merge and release gates

**Status: draft planning, not a deployment authorization.** This checklist is meant to stop repeated history-only merges and keep code integration distinct from applying Flyway migrations to the shared Supabase project.

## Automated evidence (exact revisions)

- PR #27: Flyway V12–V14 applied history alignment, V15–V20 staged; standalone migration validation was previously reported using disposable PostgreSQL. Review applied V12–V14 bytes and target history with the database owner before staging.
- PR #29 at `177b553c0400b985ef688f56b045985347bcb963`: **246/246 selected regressions and 3/3 full-context PASS** with throwaway local PostgreSQL.
- PR #31 at `f49db746d6631cdff473ffe83176499f9f90a22f`: **264/264 selected regressions and 4/4 full-context + real JWT/PostgreSQL integration PASS**; V1–V22 fresh install and synthetic V1–V14→V22 upgrade PASS were observed on unchanged SQL.
- Later PR #31 changes add conservative reminder/notification policy, regression assertions and the `.github/workflows/security-isolated-ci.yml` CI workflow. **Do not attribute prior PASS to these new commits.** Confirm the new GitHub Actions results or rerun the same tests on the exact final head.

## Required approvals and security decisions before production use

- [ ] Database owner independently confirms target `flyway_schema_history`, applied V12–V14 checksum/history alignment, backups/restore plan and V15–V22 migration window.
- [ ] Backend reviewers approve the authentication/role changes (especially technical ADMIN vs OWNER, CUSTOMER vs staff boundaries, JWT key and config management).
- [ ] Customer onboarding owner approves **recorded independent identity proof**, method of customer-controlled first-use credential setup/recovery, communications consent, handling of lost credentials and how an OWNER verifies evidence before activation. OWNER-only activation **does not** prove verification; staff-supplied initial passwords are not a production-ready credential-delivery process by themselves.
- [ ] Module owners approve the exact per-CASHIER allowlist and any field-level restrictions. Financial reporting, customer data browse/manage, destructive actions and all reminder/notification actions are currently **OWNER-only pending approval**. Do not widen permissions to make an old test return HTTP 200.
- [ ] Review abuse controls for external notifications: recipient restrictions, throughput/rate limits, message templates, audit records and provider delivery safeguards.
- [ ] Each PR has a non-author review, up-to-date required CI results and an acceptable protected-branch merge plan. GitHub 'mergeable' checks ancestry/conflicts, **not** the correctness of authorization or migration policy.
- [ ] Define rollback/restoration steps for application code and database; acknowledge that a code rollback does not automatically undo schema migrations.

## Merge order (code only)

1. Review and merge **PR #27** (migration history alignment) into `main`. Verify intended `main` SHA and reviews/status first.
2. Once #27 has been merged, retarget **PR #29** to `main`, inspect resulting diff and CI again, then merge after approval.
3. Once #29 has been merged, retarget **PR #31** to `main`, inspect the changed-files set, review + CI, then merge. Preserve ancestry or explicitly inspect the cumulative diff if using squash.
4. Code merging alone **does not run or authorize shared Supabase Flyway**, enable production customer onboarding, or grant new permissions to CASHIER by default.

## Local-only CI and verification

`security-isolated-ci.yml` uses a GitHub-hosted Java 21 runner with no database secrets and least-privilege `contents: read`; it invokes three phases: selected Maven regression tests without database configuration, `scripts/validate-spring-context-isolated.sh`, and `scripts/validate-flyway-isolated.sh`. The scripts create temporary localhost-only PostgreSQL 17 and synthetic `anon`/`authenticated` NOLOGIN fixtures, and the integration test rejects a non-local JDBC URL.

If GitHub Actions is disabled/not installed or the new workflow has not run, **report this as missing CI evidence**; Codespaces execution is useful but is not a replacement for an enforced branch check. Never supply real Supabase credentials, copy a project `.env`, or run Flyway against a shared database for CI.

## After approval: separate deployment change

Create a change record including target project/environment, actual database backup confirmation, migration plan and rollback runbook. During an approved maintenance period, migration operator verifies the live Flyway history **read-only**, validates backups, and then separately authorizes applying only the pending migrations. App startup/database migrations should not be executed incidentally against the shared target through a developer Codespace.
