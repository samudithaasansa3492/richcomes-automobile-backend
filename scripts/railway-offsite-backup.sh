#!/usr/bin/env bash
# Encrypt an operator-verified V14 backup and store it in private Railway S3.
# Recover retrieves, decrypts and restores in a networkless PostgreSQL container.
# NEVER connects to, writes, or migrates the Supabase source.
set -euo pipefail
set +x
umask 077
die() { echo "STOP: $*" >&2; exit 2; }
action="${1:-}"
[[ "$action" == upload || "$action" == recover ]] ||
  die "Usage: $0 upload <verified-backup-directory> | recover <object-key> <expected-ciphertext-sha256>"
for tool in aws age tar sha256sum mktemp; do
  command -v "$tool" >/dev/null || die "Missing tool: $tool"
done
bucket="${RICHCOMES_BACKUP_BUCKET:-}"
endpoint="${RICHCOMES_S3_ENDPOINT:-}"
[[ "$bucket" =~ ^[a-z0-9][a-z0-9.-]{2,61}[a-z0-9]$ ]] ||
  die "Configure the private Railway S3 bucket name"
[[ "$endpoint" == https://* ]] || die "S3 endpoint must use HTTPS"
[[ -n "${AWS_REGION:-${AWS_DEFAULT_REGION:-}}" ]] ||
  die "Configure AWS_REGION with the actual private bucket region"
[[ -n "${AWS_PROFILE:-}" || ( -n "${AWS_ACCESS_KEY_ID:-}" && -n "${AWS_SECRET_ACCESS_KEY:-}" ) ]] ||
  die "Configure restricted AWS credentials or profile privately"
tmp="$(mktemp -d "${TMPDIR:-/tmp}/richcomes-offsite.XXXXXXXX")"
chmod 700 "$tmp"
container=""
cleanup() {
  if [[ -n "$container" ]]; then docker rm -f "$container" >/dev/null 2>&1 || true; fi
  rm -rf -- "$tmp"
}
trap cleanup EXIT
download() {
  aws --endpoint-url "$endpoint" s3 cp --only-show-errors "s3://$bucket/$1" "$2" >/dev/null ||
    die "Off-site encrypted object retrieval failed"
}
check_sha() {
  [[ "$2" =~ ^[0-9a-f]{64}$ ]] || die "Invalid expected SHA-256"
  [[ "$(sha256sum "$1" | cut -d ' ' -f 1)" == "$2" ]] ||
    die "Ciphertext checksum mismatch"
}
if [[ "$action" == upload ]]; then
  [[ "$#" == 2 ]] || die "Supply exactly one verified local backup directory"
  src="$(realpath "$2")"
  [[ -d "$src" && ! -L "$2" ]] || die "Source backup directory invalid"
  repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
  case "$src" in "$repo"|"$repo"/*) die "Do not store backup inside Git checkout" ;; esac
  for name in garage.dump flyway-history.dump SHA256SUMS manifest.txt; do
    [[ -f "$src/$name" && ! -L "$src/$name" ]] ||
      die "Missing backup object: $name"
  done
  grep -qx 'PROJECT_REF=iehuxwbrwfysgujaqagj' "$src/manifest.txt" ||
    die "Source project is not the approved Supabase project"
  grep -qx 'FLYWAY_HISTORY=14:14' "$src/manifest.txt" ||
    die "Backup snapshot is not pre-V15 V14"
  grep -qx 'BACKUP_FORMAT_VERSION=2' "$src/manifest.txt" ||
    die "Backup snapshot lacks the source row-count manifest; create a fresh export"
  source_counts="$(sed -n 's/^APP_ROW_COUNTS=//p' "$src/manifest.txt")"
  [[ "$source_counts" =~ ^[0-9]+(:[0-9]+){6}$ ]] ||
    die "Backup snapshot row-count manifest invalid or missing"
  grep -qx 'RESTORE_TEST=isolated_postgres17_pass' "$src/manifest.txt" ||
    die "Local restore rehearsal not confirmed"
  (cd "$src" && sha256sum --check --status SHA256SUMS) ||
    die "Source archive checksum mismatch"
  recipient="${RICHCOMES_AGE_RECIPIENT:-}"
  [[ "$recipient" == age1* ]] || die "Provide offline-custodied age public recipient"
  tar -C "$src" -cf - garage.dump flyway-history.dump SHA256SUMS manifest.txt |
    age -r "$recipient" -o "$tmp/archive.tar.age" ||
    die "Client-side encryption failed"
  [[ -s "$tmp/archive.tar.age" ]] || die "Encrypted archive empty"
  hash="$(sha256sum "$tmp/archive.tar.age" | cut -d ' ' -f 1)"
  key="richcomes-pre-v15/$(date -u +%Y%m%dT%H%M%SZ)-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n').tar.age"
  if aws --endpoint-url "$endpoint" s3api head-object --bucket "$bucket" --key "$key" >/dev/null 2>&1; then
    die "Object already exists; refusing overwrite"
  fi
  aws --endpoint-url "$endpoint" s3 cp --only-show-errors "$tmp/archive.tar.age" \
    "s3://$bucket/$key" >/dev/null || die "Encrypted off-site upload failed"
  download "$key" "$tmp/downloaded.tar.age"
  check_sha "$tmp/downloaded.tar.age" "$hash"
  echo "PASS: encrypted upload + off-site download checksum match"
  echo "BACKUP_OBJECT_KEY=$key"
  echo "BACKUP_CIPHERTEXT_SHA256=$hash"
  echo "INDEPENDENT_RESTORE=NOT_YET_VERIFIED"
  exit 0
fi
[[ "$#" == 3 ]] || die "Recover needs object key and independently recorded SHA-256"
key="$2"
[[ "$key" =~ ^richcomes-pre-v15/[A-Za-z0-9._-]+\.tar\.age$ ]] ||
  die "Unexpected object path"
identity="${RICHCOMES_AGE_IDENTITY_FILE:-}"
[[ -f "$identity" && ! -L "$identity" && "$(stat -c '%a' "$identity")" == 600 ]] ||
  die "Private age identity must be a private, regular chmod 600 file"
for tool in docker pg_restore; do command -v "$tool" >/dev/null || die "Missing tool: $tool"; done
docker info >/dev/null 2>&1 || die "Docker daemon unavailable"
download "$key" "$tmp/downloaded.tar.age"
check_sha "$tmp/downloaded.tar.age" "$3"
age -d -i "$identity" -o "$tmp/archive.tar" "$tmp/downloaded.tar.age" ||
  die "Decryption failed"
files="$(tar -tf "$tmp/archive.tar" | LC_ALL=C sort)"
expected="$(printf '%s\n' SHA256SUMS flyway-history.dump garage.dump manifest.txt | LC_ALL=C sort)"
[[ "$files" == "$expected" ]] || die "Unexpected archive member list"
mkdir "$tmp/recovered"
tar -C "$tmp/recovered" --no-same-owner --no-same-permissions -xf "$tmp/archive.tar" ||
  die "Archive extraction failed"
for name in garage.dump flyway-history.dump SHA256SUMS manifest.txt; do
  [[ -f "$tmp/recovered/$name" && ! -L "$tmp/recovered/$name" ]] ||
    die "Unsafe recovered file"
done
(cd "$tmp/recovered" && sha256sum --check --status SHA256SUMS) ||
  die "Recovered archive SHA256 failed"
grep -qx 'PROJECT_REF=iehuxwbrwfysgujaqagj' "$tmp/recovered/manifest.txt" ||
  die "Recovered project does not match"
grep -qx 'FLYWAY_HISTORY=14:14' "$tmp/recovered/manifest.txt" ||
  die "Recovered history version does not match"
pg_restore --list "$tmp/recovered/garage.dump" >/dev/null ||
  die "Garage database archive corrupted"
pg_restore --list "$tmp/recovered/flyway-history.dump" >/dev/null ||
  die "History database archive corrupted"
container="richcomes-recovery-$(od -An -N8 -tx1 /dev/urandom | tr -d ' \n')"
docker run -d --rm --name "$container" --network none \
  --tmpfs /var/lib/postgresql/data:rw,nosuid,size=1536m \
  -e POSTGRES_USER=postgres -e POSTGRES_DB=postgres \
  -e "POSTGRES_PASSWORD=synthetic-$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')" \
  postgres:17.12 >/dev/null || die "Isolated PostgreSQL failed"
ready=0
for _ in $(seq 1 120); do
  if docker exec "$container" psql -X -qAt -U postgres -d postgres \
      -c 'SELECT 1' 2>/dev/null | grep -qx 1; then ready=1; break; fi
  sleep 0.5
done
[[ "$ready" == 1 ]] || die "Isolated PostgreSQL not ready"
docker exec "$container" psql -X -q -v ON_ERROR_STOP=1 -U postgres -d postgres \
  -c 'CREATE ROLE anon NOLOGIN; CREATE ROLE authenticated NOLOGIN;' >/dev/null ||
  die "Synthetic roles could not be created"
docker cp "$tmp/recovered/garage.dump" "$container:/tmp/garage.dump"
docker cp "$tmp/recovered/flyway-history.dump" "$container:/tmp/history.dump"
docker exec "$container" pg_restore -U postgres -d postgres --exit-on-error \
  --no-owner --no-acl /tmp/garage.dump >/dev/null ||
  die "Recovered garage database failed to restore"
docker exec "$container" pg_restore -U postgres -d postgres --exit-on-error \
  --no-owner --no-acl /tmp/history.dump >/dev/null ||
  die "Recovered Flyway metadata failed to restore"
history="$(docker exec "$container" psql -X -qAt -v ON_ERROR_STOP=1 \
  -U postgres -d postgres -c "SELECT count(*)::text || ':' || max(version::int)::text
    FROM public.flyway_schema_history WHERE success AND version ~ '^[0-9]+$'")"
[[ "$history" == 14:14 ]] || die "Restored Flyway history incomplete"
echo "PASS: independent retrieval, decryption, hashes and networkless Postgres restoration"
echo "RESTORED_FLYWAY_HISTORY=$history"
echo "SOURCE_DATABASE_WRITES=NONE"
echo "SCOPE=garage_schema_plus_public_flyway_history_only"
