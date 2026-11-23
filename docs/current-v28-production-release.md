# Live shared Supabase V28: safe application backup and independent restore

> **Updated 2026-11-24:** shared project `iehuxwbrwfysgujaqagj` has **28 successful Flyway migrations** and **33 `garage` tables**. V27 adds customer consent/verification columns; V28 adds a store reference to stock transactions. Both applied Git checksums matched `main` on 2026-11-24. Database state may change again. **Re-check before doing anything.** This is an operator procedure; no real source snapshot, S3 upload, independent restore or production deployment is certified here.
>
> The earlier [V26 procedure](current-v26-production-release.md) and `*-v26-*` scripts are historically version-locked. They **correctly refuse** to operate on V28. Do not weaken their version guard, alter Flyway history, rerun V27/V28, or call this an authentication failure.

## 1. Read-only migration preflight — safe without a write freeze

Requires a trusted private operator Codespace with PostgreSQL **17** client, `python3` and the latest GitHub `main`. Use the connection details from **Supabase Dashboard → Connect → Session pooler, port 5432**. Do not paste a password into GitHub, Jira, chat or a shell command. The operator already created an encrypted-transport `PGPASSFILE` **outside the repository** under their private home; validate its owner and mode (`0600`) before use.

```bash
git switch main && git pull --ff-only origin main
export PGHOST='<session-pooler-host-from-dashboard>'
export PGUSER='postgres.iehuxwbrwfysgujaqagj'
export PGPORT=5432 PGDATABASE=postgres PGSSLMODE=require
export PGPASSFILE="$HOME/.local/state/richcomes-v26/db.pgpass"
export RICHCOMES_CONFIRM_PROJECT=iehuxwbrwfysgujaqagj

bash scripts/check-shared-flyway-v28-readonly.sh
```

This script runs **read-only** and fails closed unless **exactly 28 successful history rows** match the Git source filenames and CRC32 checksums. It is **not** a backup. If it prints V29 or any mismatch, **STOP**: reconcile with the team and update scripts under review. Do not run a V26 script to bypass the error.

## 2. Authorized real V28 source backup and networkless restore rehearsal

First, **coordinate an actual maintenance/write freeze** with the project/team DB owner; a shell flag alone does not freeze writes. Record approval, ensure source writers/DDL are paused, and determine whether other Supabase-managed schemas/roles/Auth/Storage/external files require their **own** backups. The application-specific script captures only `garage` and `public.flyway_schema_history`. Ensure the Codespace has enough private disk space and working Docker. Do not store private customer archives in the Git checkout.

After a real freeze is confirmed, with the PostgreSQL environment above still present:

```bash
umask 077
export RICHCOMES_BACKUP_DIR="$HOME/richcomes-private-backups"
mkdir -p "$RICHCOMES_BACKUP_DIR"
chmod 700 "$RICHCOMES_BACKUP_DIR"
export RICHCOMES_WRITES_PAUSED=YES  # ONLY after real team-coordinated write freeze
bash scripts/backup-garage-v28-with-restore-rehearsal.sh
```

Success requires the literal `LOCAL BACKUP + ISOLATED RESTORE VERIFIED`. The script refuses any source other than 28:28; verifies applied source CRC; exports two restricted PostgreSQL custom archives; checks **all 33 application table row counts**, SHA-256 hashes, and an isolated network-disabled PostgreSQL 17 restore. Its `BACKUP_FORMAT_VERSION=4` means the **row-count manifest format**, not the Flyway schema version. No migrations or source database writes occur.

The write freeze may be lifted only after the snapshot has completed and ownership has approved proceeding. Private Codespace storage is **not** durable off-site recovery.

## 3. Private encrypted off-site upload and independent restore

The live Railway private S3 bucket exists. **Display name**: `richcomes-offsite-backups-live`; **API bucket name**: `functional-locker-mh5j-coszp`; **endpoint**: `https://t3.storageapi.dev`; **region**: `auto`. Never use the duplicate *staged-only* bucket. A separately authorized operator must obtain restricted S3 access keys directly in Railway dashboard. The connected assistant cannot access their secret values. Use `age` encryption keys with a **separately safeguarded private identity** (not in code, chat or Railway service variables), plus AWS CLI in the operator environment.

With `AWS_PROFILE` configured privately, configure:

```bash
export RICHCOMES_BACKUP_BUCKET=functional-locker-mh5j-coszp
export RICHCOMES_S3_ENDPOINT=https://t3.storageapi.dev
export AWS_REGION=auto
export AWS_PROFILE='<approved-private-upload-profile>'
export RICHCOMES_AGE_RECIPIENT='<age1-public-recipient>'
bash scripts/railway-offsite-v28-backup.sh upload '<verified-V28-snapshot-folder>'
```

Record returned `BACKUP_OBJECT_KEY` and `BACKUP_CIPHERTEXT_SHA256` metadata. The script encrypts before upload and verifies a downloaded ciphertext's SHA-256; that alone **does not** establish a successful independent restore.

On a **separate trusted recovery machine**, with read-only S3 credentials, `age` private identity (`0600`) and Docker, run:

```bash
export RICHCOMES_AGE_IDENTITY_FILE='<protected-private-age-identity-file>'
export AWS_PROFILE='<approved-private-read-profile>'
bash scripts/railway-offsite-v28-backup.sh recover \
  '<BACKUP_OBJECT_KEY>' '<BACKUP_CIPHERTEXT_SHA256>'
```

Recovery verifies ciphertext, decrypts locally, validates archive members and internal hashes, restores to a network-disabled PostgreSQL 17 Docker container, verifies **28:28 Flyway history and 33-table row-count equality**. Document actual independent PASS, recovery operator, timestamp, object key/hash, wider Supabase resource coverage and privacy sign-off in [release Issue #34](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/34).

## 4. Production is a separate gate

**Do not** accept the Railway staged patch until private DB/JWT runtime secrets exist, the redundant staged-only backup bucket has been removed, a real V28 backup and independent recovery are proven, and the business owners approve the release. Leave `FLYWAY_ENABLED=false`, `APP_RECOVERY_ENABLED=false` and `CUSTOMER_ONBOARDING_ENABLED=false`. Verify HTTPS readiness, identity APIs, least-privilege authorization and first OWNER provisioning only through approved operator processes. The live database is shared among six members; recheck Flyway immediately before any release decision.
