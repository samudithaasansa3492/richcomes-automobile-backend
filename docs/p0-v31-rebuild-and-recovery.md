# P0 V31 clean database recovery (staged, 2026-11-25)

**Do not run against the quarantined incident project.** #75: old project has zero garage tables and 28 history rows. Lost records were reported synthetic. Preserve the old project and history. Do not reset, delete, repair, manually rewrite Flyway history or use old V28 backup scripts. Isolated #78 passed 641 regression tests, 8 PostgreSQL tests and a 34-table V31 restoration; this is not proof of hosted recovery.

## Approval gates
1. Ishini confirms #77 ready or coordinates a V30-only PR; merge only reviewed green changes. Then sync and merge #76 after both CI jobs pass. This validation-only tooling branch is not a release; review/sync separately against updated main.
2. Team/DB owner approve exact merged commit and a **new** private Supabase project, including plan/cost. Verify new project has no application objects or Flyway history; preserve managed schemas. Never share migration-owner credentials with tests. On approved target only, operator applies V1–V31 through Flyway using protected migration credentials, not Hibernate or generic application startup. Stop on any unexpected version or error.
3. Separate least-privilege non-owner runtime credentials from migration owner: no DDL, superuser, bypass RLS or ownership/membership. Review schema exposure, actual privileges (tables/sequences/functions), RLS and advisors. Verify DML rights and DDL denial. Runtime FLYWAY_ENABLED=false and ddl-auto=none. Recovery/onboarding remain disabled until operational approval. Verify actual schema and CRCs before seeding synthetic first OWNER using docs/first-admin-provisioning.md. Each module owner recreates test records. No old rows are claimed recovered.
4. Run verified backup and independent restore before cutover. Use private PostgreSQL 17 client, Docker, Python, exact approved clean Git checkout; TLS, private chmod 600 PGPASSFILE, dashboard-confirmed PGHOST and PGUSER, PGPORT=5432 PGDATABASE=postgres PGSSLMODE=verify-full (or require with documented acceptance). Unset PGHOSTADDR, PGSERVICE, PGSERVICEFILE, PGPASSWORD, PGOPTIONS.

## V31 operator commands — only after gates 1–3
```bash
export RICHCOMES_RECOVERY_PROJECT='<new 20-character project reference>'
export RICHCOMES_CONFIRM_PROJECT="$RICHCOMES_RECOVERY_PROJECT"
export RICHCOMES_APPROVED_COMMIT='<exact approved merged commit SHA>'
export RICHCOMES_RECOVERY_APPROVAL=YES # only after actual documented approval
bash scripts/check-shared-flyway-v31-readonly.sh
```
Requires all 31 history CRCs and 34 expected application tables. Coordinate actual write freeze; set RICHCOMES_WRITES_PAUSED=YES only once writers are stopped; configure private absolute RICHCOMES_BACKUP_DIR outside Git. Run `bash scripts/backup-garage-v31-with-restore-rehearsal.sh`. Verify local restore success. Format 5 binds project/commit/V31 history and 34 counts. Archives omit global roles, ACLs, ownership, managed Supabase schemas/settings/Auth/Storage/external files. These need separate review and restoration; do not claim whole-project backup.

Use separately approved private Railway S3 profile/credentials, HTTPS endpoint, bucket/region and age recipient. Run `bash scripts/railway-offsite-v31-backup.sh upload '<verified-folder>'`; record ciphertext hash/object key. Independent recovery machine with read-only S3 credentials and privately held chmod 600 age identity runs `bash scripts/railway-offsite-v31-backup.sh recover '<object-key>' '<hash>'` using the same approved source commit. Require real successful retrieval/decryption/CRC/34-table restore. No secrets in Git, Jira, chat or command arguments. No actual S3 or Supabase writes in this draft.

## Cutover
Record results in #75 and #34. Confirm new restricted runtime credentials, JWT, HTTPS and approved deployment. Validate signed JWT OWNER/CASHIER/ADMIN/CUSTOMER boundaries and module-owner tests; confirm independent backup and rollback plan. Do not route traffic to the damaged database. Only then lift freeze.
