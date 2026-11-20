#!/usr/bin/env bash
# Negative tests: invalid local backup metadata must NEVER reach S3 or age.
set -euo pipefail
set +x

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
tmp="$(mktemp -d /tmp/richcomes-backup-guard-test.XXXXXXXX)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/snapshot"
for command in aws age; do
  cat > "$tmp/bin/$command" <<'STUB'
#!/usr/bin/env bash
printf 'unexpected external invocation\n' >> "$MOCK_MARKER"
exit 98
STUB
  chmod +x "$tmp/bin/$command"
done
export MOCK_MARKER="$tmp/unexpected-calls"
export RICHCOMES_BACKUP_BUCKET=local-test-private-bucket
export RICHCOMES_S3_ENDPOINT=https://example.invalid
export AWS_REGION=auto AWS_ACCESS_KEY_ID=dummy AWS_SECRET_ACCESS_KEY=dummy
export RICHCOMES_AGE_RECIPIENT=age1synthetictestrejectonly
export PATH="$tmp/bin:$PATH"
for f in garage.dump flyway-history.dump; do printf 'synthetic %s\n' "$f" > "$tmp/snapshot/$f"; done
(cd "$tmp/snapshot" && sha256sum garage.dump flyway-history.dump > SHA256SUMS)
write_manifest() {
  printf '%s\n' \
    PROJECT_REF=iehuxwbrwfysgujaqagj \
    FLYWAY_HISTORY=14:14 \
    "$@" \
    RESTORE_TEST=isolated_postgres17_pass \
    > "$tmp/snapshot/manifest.txt"
}
reject() {
  local reason="$1" output
  if output="$(bash "$repo/scripts/railway-offsite-backup.sh" upload "$tmp/snapshot" 2>&1)"; then
    echo "FAIL: $reason was accepted" >&2
    exit 1
  fi
  [[ ! -e "$MOCK_MARKER" ]] || {
    echo "FAIL: $reason attempted to call age/S3 before validation" >&2
    exit 1
  }
  echo "PASS: $reason rejected before external service access"
}
write_manifest APP_ROW_COUNTS=0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0
reject 'missing backup format version'
write_manifest BACKUP_FORMAT_VERSION=3
reject 'missing table-count baseline'
write_manifest BACKUP_FORMAT_VERSION=2 APP_ROW_COUNTS=0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0
reject 'obsolete seven-table backup format'
write_manifest BACKUP_FORMAT_VERSION=3 APP_ROW_COUNTS=0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0
reject 'too few application counts'
write_manifest BACKUP_FORMAT_VERSION=3 APP_ROW_COUNTS=0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:garbled
reject 'nonnumeric application count'
write_manifest BACKUP_FORMAT_VERSION=3 APP_ROW_COUNTS=0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0
# Invalid source checksums must likewise fail prior to attempting encryption.
printf 'modified original\n' > "$tmp/snapshot/garage.dump"
reject 'modified source archive SHA-256'
echo 'PASS: all off-site guard negative tests'
