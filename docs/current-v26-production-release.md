> **ARCHIVED/DO NOT USE ON CURRENT SHARED DB:** As of 2026-11-24 the shared DB is **V28**. The commands in this historical V26 guide deliberately reject V27/V28. Use [Current V28 production release and backup guide](current-v28-production-release.md). Do not run V26-only backup or recovery scripts on V28.

# Current V26 shared Supabase recovery and production release gate

**Verified 2026-11-21:** shared project `iehuxwbrwfysgujaqagj` has **26 successful Flyway migrations**, source CRC32 matches V1–V26 Git SQL files, and **33 application tables** under `garage`. The multi-store inventory API/migrations are merged into backend `main` as PR #53. The Railway `richcomes-backend` deployment is still STAGED, not public or production-ready.

**No real live database backup/upload/independent restore is evidenced as of this document.** A code/CI-tested synthetic restore is not a live disaster-recovery test.

## Scope and exclusions

The V26-specific scripts export/read-only:
- a custom PostgreSQL archive of the **entire `garage` schema** (tables, structures and rows)
- a separate archive of `public.flyway_schema_history` (all 26 successful migrations)
- a protected manifest with exact counts for **all 33 V26 application tables**, validated against the source and isolated recovered database
- SHA-256 checksums for both source dump archives.

These scripts do **not** back up Supabase Auth/Storage objects, other managed schemas, external files, role definitions, project settings, DNS, mail delivery or Railway credentials. Before authorizing real production traffic, the appointed database owner must assess the completeness of full Supabase platform recovery and take any additional backups needed. **Do not run the historical V14-only backup scripts on V26.**

## A. Obtain authorized private connection and create a current local backup

On an operator-owned workstation or private Codespace with Docker, PostgreSQL 17 client, Python 3 and disk capacity:

1. Agree to a write-freeze/change window with the team. Disable service writes/migrations during the snapshot. The tooling refuses to run without the explicit freeze acknowledgment.
2. In Supabase Dashboard → Connect, obtain the **exact project's** direct or *session-pooler* PostgreSQL hostname and username. Transaction-pooler mode is not appropriate for this `pg_dump` process.
3. Store the database password in an operator-local `PGPASSFILE` file (mode 0600) — not the Git repository, CI logs, Railway variables for migration, chat or Jira. Keep the backup destination outside the Git checkout.
4. Set **non-secret metadata** privately in the shell, using the actual dashboard host/username:
   ```bash
   export PGHOST='<approved-direct-host-or-session-pooler-host>'
   export PGPORT=5432 PGDATABASE=postgres
   export PGUSER='<approved-Postgres-user>'
   export PGSSLMODE=require
   export PGPASSFILE="$HOME/.pgpass"
   export RICHCOMES_CONFIRM_PROJECT=iehuxwbrwfysgujaqagj
   export RICHCOMES_BACKUP_DIR='/absolute/private/directory-outside-repo'
   # Set only after real maintenance coordination and writes paused:
   export RICHCOMES_WRITES_PAUSED=YES

   bash scripts/check-shared-flyway-v26-readonly.sh
   bash scripts/backup-garage-v26-with-restore-rehearsal.sh
   ```
5. Require **`LOCAL BACKUP + ISOLATED RESTORE VERIFIED`** before releasing the write freeze. The script refuses any source version other than exact `26:26`, changed applied migration CRCs, an unexpected number of V26 tables, damaged dump, mismatched 33-table counts or incomplete PostgreSQL restore. The final output directory contains `garage.dump`, `flyway-history.dump`, `SHA256SUMS` and `manifest.txt` with `BACKUP_FORMAT_VERSION=4`.

## B. Encrypt in the workstation and upload to Railway private S3

The private Railway storage bucket is already provisioned. Its provider display name is `richcomes-offsite-backups-live`, but the **S3 API bucket name** is `functional-locker-mh5j-coszp`, the HTTPS endpoint is `https://t3.storageapi.dev` and S3 client region is `auto`. Obtain restricted access credentials from Railway Bucket settings privately. **The connected assistant cannot read OAuth-redacted keys.**

Generate and separately custody an **age** encryption identity; never store its private half in Git, Railway service variables or chat. On the upload machine use only its *public* `age1...` recipient.

```bash
export RICHCOMES_BACKUP_BUCKET=functional-locker-mh5j-coszp
export RICHCOMES_S3_ENDPOINT=https://t3.storageapi.dev
export AWS_REGION=auto
export AWS_PROFILE='<private-upload-credentials-profile>'
export RICHCOMES_AGE_RECIPIENT='<age1-public-key-only>'

bash scripts/railway-offsite-v26-backup.sh upload '/absolute/private/V26-snapshot-directory'
```

The tool encrypts the archives *before* upload, retrieves the ciphertext and validates the same SHA-256. The `BACKUP_OBJECT_KEY` and `BACKUP_CIPHERTEXT_SHA256` are operational metadata, not credentials. A successful upload/byte-level retrieval **is not yet independent restore evidence**.

## C. Independently retrieve, decrypt and restore

On a **separate trusted recovery machine**, use restricted bucket read access and separately custodied private `age` identity (mode 0600). Run:

```bash
export RICHCOMES_BACKUP_BUCKET=functional-locker-mh5j-coszp
export RICHCOMES_S3_ENDPOINT=https://t3.storageapi.dev
export AWS_REGION=auto
export AWS_PROFILE='<private-read-credentials-profile>'
export RICHCOMES_AGE_IDENTITY_FILE='/private/location/age-identity.txt'

bash scripts/railway-offsite-v26-backup.sh recover \
  '<recorded-BACKUP_OBJECT_KEY>' '<recorded-BACKUP_CIPHERTEXT_SHA256>'
```

Recovery decrypts locally, rejects unexpected archive members and malformed v4 metadata, re-verifies archive checksums, and restores in a **network-disabled, tmpfs-backed** PostgreSQL 17 container. It must report all **33 table counts matched** plus Flyway history `26:26`. Never paste a real password, private identity, encrypted archive or customer record into chat or an issue.

## D. Release authorization — after proven recovery only

- Record a real backup operation ID, archive creation time, encrypted object key/digest, independent restore PASS, scope/managed-resource caveats and reviewer approval in [GitHub Issue #34](https://github.com/samudithaasansa3492/richcomes-automobile-backend/issues/34). Keep storage/object access restricted and ensure retention and key custody cover foreseeable incidents.
- Since Supabase is **already V26**, do not rerun or reapply V15–V26. Inspect the latest Flyway history/schema, dedicated JDBC user and business role grants.
- Review the Railway pending patch carefully. It contains a **redundant staged-only bucket**, separate from the already-live backup bucket. Remove that redundant pending resource via Railway Dashboard before approving the app deployment. The service still needs protected TLS database credentials and an RS256 private/public key pair in protected Railway runtime configuration.
- Only after the database and runtime settings exist should the backend be deployed and its HTTPS `/actuator/health/readiness` endpoint actually tested. Keep `FLYWAY_ENABLED=false`, `CUSTOMER_ONBOARDING_ENABLED=false` and `APP_RECOVERY_ENABLED=false` by default.
- Activating customer emails still requires approved SMTP sender/domain, first-party HTTPS account setup, physical identity verification SOP and evidence-custody signoff. Those operational requirements cannot be satisfied by CI.

**This guide prepares the operator workflow; it is not a claim of production deployment, off-site backup completion, or actual business approval.**
