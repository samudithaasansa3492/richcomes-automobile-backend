# Supabase Free-tier recovery — private Railway off-site archive

**Release gate remains OPEN until an independently recovered real snapshot is proven.**
This guide is an operator workflow, not a claim that a real customer database backup was performed. A Railway project named `richcomes-automobile` exists. Its private bucket `richcomes-release-backups` has been **staged, not yet provisioned**. The app service is also staged and has not been launched. Do not accept the whole Railway environment patch merely to create the bucket: the app is intentionally blocked until DB/JWT configuration exists.

## Scope and limitations

The existing `scripts/backup-garage-with-restore-rehearsal.sh` exports **only** application `garage` objects/rows and `public.flyway_schema_history` into local custom-format PostgreSQL archives, checks SHA-256 and restores to a network-isolated disposable Postgres 17. It refuses if shared Supabase is no longer at V14. See [shared DB backup runbook](shared-supabase-backup-restore.md).

The new `scripts/railway-offsite-backup.sh` does **not connect to Supabase**. It encrypts those already-verified archives client-side with `age`, uploads ciphertext to a private S3-compatible bucket, retrieves it, checks the encrypted SHA-256, then allows an independent operator to download/decrypt/restore to a network-isolated disposable PostgreSQL instance.

**These are NOT full Supabase-platform backups.** Supabase Auth, Storage objects, reserved roles, server config, other managed schemas and project settings are outside these two application archives. Before changing shared Supabase schema, independently decide whether those managed resources are in use. Capture additional full-platform, Supabase-compatible roles/schema/data dumps and external Storage objects as required; see [Supabase Database Backups](https://supabase.com/docs/guides/platform/backups) and [Backup & Restore](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore). Use only a maintenance window in which live writers are paused.

## One-time setup (secrets only on operator machine)

1. Provision a **restricted private** Railway bucket in `sin` (Singapore) and check your plan's usage limit, retention, retrieval costs and access. Do not use an unverified bucket as the sole disaster recovery copy. Railway bucket pricing/usage limits: [official documentation](https://docs.railway.com/storage-buckets/billing).
2. In Railway Dashboard, open bucket credentials and privately configure the AWS CLI `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` and `RICHCOMES_S3_ENDPOINT`. Set `RICHCOMES_BACKUP_BUCKET=richcomes-release-backups`. Do **not** paste keys in ChatGPT, Jira, GitHub, CI logs or a committed `.env`. Prefer `AWS_PROFILE` stored in a personal credentials file with `chmod 600`.
3. Generate an `age` X25519 identity on an **offline/restricted backup custodian device** using the installed `age-keygen` command. Save the private key in a secure credential vault with independent recovery custodians and only release the *public recipient* (`age1...`) to the upload operator. Do not commit, email or paste the identity key.
4. Install trusted recent versions of `aws` CLI, `age`, `postgresql-client-17` and `docker` on the operator workstation. Confirm `aws --version`, `age --version`, `pg_restore --version` and `docker info`.

## Step 1 — create real V14 snapshot

Use your private database connection and a formal pause of writes, following the existing [V14 backup & isolated rehearsal runbook](shared-supabase-backup-restore.md). It produces a directory outside Git containing `garage.dump`, `flyway-history.dump`, `SHA256SUMS` and `manifest.txt`. Ensure that its manifest contains `RESTORE_TEST=isolated_postgres17_pass`. Its source is hard-gated to exact project `iehuxwbrwfysgujaqagj` and to V14. It does not capture all managed-platform state.

## Step 2 — client-side encrypt and upload

In the private workstation shell (values intentionally omitted):

```bash
export RICHCOMES_BACKUP_BUCKET=richcomes-release-backups
export RICHCOMES_S3_ENDPOINT='https://<bucket-endpoint-from-Railway>'
export AWS_REGION='<bucket-region-from-Railway>'
export AWS_PROFILE='<private-credentials-profile>'
export RICHCOMES_AGE_RECIPIENT='age1...public-recipient...'

bash scripts/railway-offsite-backup.sh upload '/absolute/private/verified-snapshot-directory'
```

The command refuses any missing files, mismatched source project/history, missing local restore evidence, corrupt checksum, unencrypted bucket endpoint, or Git-based snapshot location. It creates the encrypted archive in a temporary private folder, **never stores a combined plaintext tar archive on disk**, uploads to an object key with a random suffix, retrieves the encrypted object and compares bytes. The command prints an `BACKUP_OBJECT_KEY` and `BACKUP_CIPHERTEXT_SHA256`. Store these two *nonsensitive* values in the restricted release evidence record. It explicitly reports that independent restore is **NOT YET VERIFIED**.

## Step 3 — independent restore from off-site (separate trusted operator/device)

On a **different** secure recovery machine, configure the same bucket read-only access and the **private** age identity file (chmod 600) from the approved custodian. Only give the command the previously recorded **object key and SHA-256**; do not provide database credentials.

```bash
export RICHCOMES_BACKUP_BUCKET=richcomes-release-backups
export RICHCOMES_S3_ENDPOINT='https://<bucket-endpoint-from-Railway>'
export AWS_REGION='<bucket-region-from-Railway>'
export AWS_PROFILE='<restricted-read-profile>'
export RICHCOMES_AGE_IDENTITY_FILE='/secure/private/age-identity.txt'

bash scripts/railway-offsite-backup.sh recover \
  '<recorded-BACKUP_OBJECT_KEY>' '<recorded-BACKUP_CIPHERTEXT_SHA256>'
```

The script retrieves the ciphertext, validates SHA-256, decrypts using the offline-custodied identity, validates the exact member list and plaintext checksums, then restores both dumps into a disposable Postgres **17** instance with `--network none`, no published database ports and `tmpfs` storage. It verifies the restored Flyway history is `14:14` and destroys all temporary data on exit. Record the actual successful output, time, signed operator identity and scope; **do not close the release gate** until the business owner/DBA has approved the backup completeness and an additional full-platform recovery plan.

## Protecting data and credentials

- Keep ciphertext private, enable restricted bucket IAM, independent key custody, retention and secure deletion controls. `age` encryption is client-side; the raw `garage` data must never be uploaded without it.
- Never deploy app JWT signing keys or customer data in GitHub. Railway environment variables are a separate concern from backup encryption credentials.
- Railway buckets on a Free plan have storage/credit usage limits; exceeding the hard limit can suspend access. Don't assume indefinite retention or availability. For production, use an approved funded recovery storage plan and preferably a second independent copy.
- Never use `FLYWAY_ENABLED=true` on the shared API service to bypass the pre-V23 startup guard. V15–V23 require a separate controlled migration operator after the above evidence is approved.
