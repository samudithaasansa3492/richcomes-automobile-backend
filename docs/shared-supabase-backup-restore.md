# Shared V14 database backup & restore release gate

**Status (2026-11-18): NOT executed on the shared database.** The connected Supabase organization is on Free; scheduled daily backup and PITR were not verified. Flyway V15–V23 remain **unapplied** to `richcomes-automobile-dev`, and onboarding must remain OFF. Do not interpret GitHub CI or a GitHub repo commit as a verified backup.

## Read-only V14 checksum preflight (verified 2026-11-19)

The connected shared dev Supabase project's actual `public.flyway_schema_history` was read via SELECT: **all V1–V14 scripts, success flags and Flyway CRC32 checksums matched the unmodified `main` source files**. This comparison is a **read-only** code-vs-history integrity check. It does **not** mean the V15–V23 changes are applied, that a backup has been performed, or that a migration window is safe.

An operator can repeat the check without making any database changes:

```bash
# With private PGPASSFILE (chmod 600), exact-project PGHOST/PGUSER,
# PGPORT=5432, PGDATABASE=postgres and PGSSLMODE=require already set:
export RICHCOMES_CONFIRM_PROJECT=iehuxwbrwfysgujaqagj
bash scripts/check-shared-flyway-history-readonly.sh
```

The wrapper uses a READ ONLY connection and streams *only migration-history metadata* to an offline Python validator. It refuses any unexpected history version, failed entry, missing/renamed migration, duplicate version, installed-rank mismatch or checksum difference. The V14 backup rehearsal script now runs this preflight before dumping data. CI includes the non-sensitive V14 history baseline and regression tests with deliberate checksum/name/version corruption. The baseline must **not** be treated as authoritative for later V15–V23 rollout or silently edited to suppress a drift alarm.

## Scope and operator authorization

Only run `scripts/backup-garage-with-restore-rehearsal.sh` **on an authorized workstation/Codespace** with PostgreSQL 17 `pg_dump`, `pg_restore`, `psql`, Docker and a private, encrypted off-site destination. Require a maintenance window with writes paused, the exact intended Supabase project, and a private database password source. This operator tool performs **only SQL reads against Supabase**; the restore runs in a disposable, **network-disabled** PostgreSQL 17 container. No source data are sent to CI or committed to GitHub.

The script exports: (1) full **garage** application schema with rows and structures, and (2) `public.flyway_schema_history`, separately. The script verifies readable archive files, a complete local restore in Docker, Flyway count/version, representative application-table row counts, and SHA-256 hashes. Output remains privately on the operator's disk. The source database must still be at **V14**.

**This is an application-scoped recovery rehearsal, not a full Supabase-platform backup.** Supabase Auth/Storage, roles, external storage objects, functions, project settings and other managed resources are excluded. For full project recovery, follow the current official Supabase CLI [backup and restore](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore) / [self-hosted restore](https://supabase.com/docs/guides/self-hosting/restore-from-platform) instructions with independently validated roles, schema and data dumps, plus external resource export as applicable. The official CLI filters Supabase-managed schemas; raw whole-database `pg_dump` is not a substitute for a platform-compatible dump.

## Operator procedure (not automated by ChatGPT)

1. Confirm this is the exact `richcomes-automobile-dev` project `iehuxwbrwfysgujaqagj`; schedule a brief write freeze. Ensure no Flyway deployment, writes or concurrent migration job is running. Confirm a local, separate storage directory with sufficient capacity, a safe encryption tool, and off-site storage access. Do not paste any database password into the chat, GitHub issues or command-line arguments.
2. On the operator's machine, read `pg_dump --version`, `psql --version`, `docker info`, and the database connection details from Supabase **Connect**. Use a *session pooler* or direct connection; do **not** use transaction-pooling mode for `pg_dump`. Supply credentials through a private `~/.pgpass` or `PGPASSFILE` (`chmod 600`) rather than embedding them in a shell command.
3. In the operator's shell, set connection metadata (replace placeholders using the private Supabase dashboard, not copied into source control):
    ```bash
    export PGHOST='<direct-host-or-session-pooler-host>'
    export PGPORT=5432
    export PGDATABASE=postgres
    export PGUSER='<postgres-or-postgres.project-ref>'
    export PGSSLMODE=require
    export PGPASSFILE="$HOME/.pgpass"
    export RICHCOMES_CONFIRM_PROJECT=iehuxwbrwfysgujaqagj
    export RICHCOMES_BACKUP_DIR='<existing-absolute-path-outside-repository>'
    # Set this ONLY after actual maintenance coordination and pausing writes.
    export RICHCOMES_WRITES_PAUSED=YES
    bash scripts/backup-garage-with-restore-rehearsal.sh
    ```
    The operator script refuses incorrect project metadata, wrong migration version, non-TLS connections, destination under the repository, absent tools and a missing freeze acknowledgment. It creates no database migration or write.
4. Confirm the output contains `garage.dump`, `flyway-history.dump`, `SHA256SUMS` and `manifest.txt`, and that **isolated restore PASS** is reported. Check `sha256sum -c SHA256SUMS` inside its directory.
5. **Encrypt both backup files**, copy off-site into a restricted private location, retrieve a separate copy, verify hashes, and document a second successful restore to a controlled test environment. Keep encryption keys outside the archive. Do not upload unencrypted customer data to public GitHub, chat, logs or shared cloud artifacts. The automated local test alone does not verify off-site recoverability.
6. Store the backup date, dataset scope, SHA256 values, archive/retrieval evidence (not secrets), recovery operator and change window in [Issue #34](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/34). Separately obtain an approved plan for full Supabase recovery and restore before V15–V23.

## After backup authorization

- Independently compare actual `public.flyway_schema_history` against all versioned source migration files (particularly existing V12–V14 names/checksums).
- Review pending V15–V23 changes and permissions. A schema upgrade is **not a user sign-in enablement**.
- In a controlled, audited change window only, enable `FLYWAY_ENABLED=true` for the dedicated migration operator; the app remains default OFF.
- Re-verify history, new tables, least-privilege grants, role seed, JWT access, and retained RLS on `public.flyway_schema_history`.
- Keep `CUSTOMER_ONBOARDING_ENABLED=false` until the real-world identity-proofing SOP, HTTPS first-password page and approved TLS SMTP delivery are functional and independently reviewed.

## Known browser-role RLS advisory

Supabase's table-listing tool warned about 18 `garage` tables with RLS OFF. **An actual read-only catalog query** showed `anon` and `authenticated` currently have **no USAGE on garage** and no SELECT/INSERT/UPDATE privileges on those tables. Therefore the warning does not, by itself, establish active browser access. Do not auto-enable RLS without reviewing Spring Boot database-user privileges, Supabase exposed schemas, functional requirements and migration ordering; continue verifying that no new grants accidentally expose garage. The already hardened `public.flyway_schema_history` is RLS ON and browser SELECT denied.
