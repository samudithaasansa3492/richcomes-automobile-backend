# Flyway migration contributions: coordination and fail-closed CI

**As of 2026-11-24:** GitHub `main` contains V1–V28; these have already been applied to shared Supabase. Member 02's open PR #63 contains **V29 appointment cancellation**, which has passed isolated CI but is not yet merged. Coordinate additional migrations at **[Issue #68](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/68)**.

## Every team member must do this before writing a new migration

1. Check the most recent **shared Supabase Flyway history** (read-only), `main` migration folder, open pull requests and the centrally maintained version registry. A teammate's private unpushed branch is invisible to automated GitHub checks: **ask them**.
2. Reserve one unique migration number in Issue #68. V29 is currently claimed by Member 02 / PR #63. A different module must not also create `V29__...sql`.
3. Work on a feature branch. **Never edit/rename/reorder V1–V28 (already applied)** or a newer migration after it has been applied. If a pending not-yet-applied migration conflicts, coordinate renumbering **before** any shared database rollout. Do not edit applied history or execute `flyway repair` to hide mismatches.
4. Open a PR immediately so the automated **open-PR version collision check** can see the claim. Check that its base is updated from `main`. Do not force a V30-only PR based on V28 through CI when V29 still lives only in a separate PR: merge/order the dependency first, or coordinate a reviewed integration branch.
5. Include additive migration SQL and tests checking the actual new table/constraints/permissions/behavior. Passing counts alone does not prove a correct migration.

## How the checks work

- `python3 scripts/check-flyway-source-versions.py`: runs without credentials or DB access, requires exactly one nonempty and correctly named SQL file for every consecutive version V1–Vlatest; validates the historically applied V1–V28 **file names and Flyway CRC32 checksums**. Unexpected gaps, duplicate numbers, old-SQL changes, deletions and invalid filenames fail.
- `python3 scripts/check-flyway-source-versions.py --latest`: returns the validated latest integer for **disposable CI and team-dev checks only**. Scripts do not require manual V28→V29→V30 count edits.
- `scripts/check-pr-flyway-collisions.py`: CI read-only GitHub API check, comparing added/renamed version files in the current PR against **other open PRs**. It fails if another open PR claims the same number. Unopened or unpushed branches are undetectable and **still need Issue #68 reservations**.
- `scripts/validate-spring-context-isolated.sh`: Linux CI creates a disposable local PostgreSQL, runs explicit Flyway and validates the actual V23 compatibility proof and version before booting Spring. `SchemaCompatibilityStartupGuard` remains **enabled**, not bypassed.
- `scripts/validate-flyway-isolated.sh`: tests all source versions in a disposable PostgreSQL on fresh install and a V1–V14 upgrade, preserves V23 customer security and V28 inventory checks, verifies a synthetic custom-archive recovery including **all** current `garage` table row counts.
- `scripts/team-dev.sh verify` and `scripts/seed-team-dev-accounts.sh`: compare their **private temporary database** history against the validated source latest. They do not connect to Supabase.

The pinning of V1–V28 against on-disk historical checksums helps prevent accidental edits but is **not** a replacement for authoritative read-only live database `flyway_schema_history` checksum comparison immediately before migration or backup. This new automated count is deliberately **NOT** used in historical V26/V28 production backup scripts; real release scripts remain version-locked and fail if the live DB advances.

## Shared database and deployment safety

**Do not apply migrations to shared Supabase** as part of a feature PR or normal app startup. The designated database operator must first complete a real encrypted recoverable backup, independent restore, team-approved write-freeze, read-only source-vs-applied checksum check and module review; then apply approved migrations **in order** and record the result in Issue #68 and the release gate. Do not use ordinary CI credentials to access the shared DB. Keep runtime `FLYWAY_ENABLED=false`.

Production deployments and offsite recovery are separate release gates; a green PR is only a prerequisite, not authorization to deploy.
