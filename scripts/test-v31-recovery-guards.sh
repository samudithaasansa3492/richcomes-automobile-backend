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
export RICHCOMES_RECOVERY_PROJECT=abcdefghijklmnopqrst
export RICHCOMES_CONFIRM_PROJECT="$RICHCOMES_RECOVERY_PROJECT"
export RICHCOMES_RECOVERY_APPROVAL=YES
export RICHCOMES_APPROVED_COMMIT="$(git -C "$repo" rev-parse HEAD)"
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
    PROJECT_REF=abcdefghijklmnopqrst \
    FLYWAY_HISTORY=31:31 \
    "SOURCE_COMMIT=$RICHCOMES_APPROVED_COMMIT" \
    "$@" \
    RESTORE_TEST=isolated_postgres17_pass \
    > "$tmp/snapshot/manifest.txt"
}
reject() {
  local reason="$1" expected="$2" output
  if output="$(bash "$repo/scripts/railway-offsite-v31-backup.sh" upload "$tmp/snapshot" 2>&1)"; then
    echo "FAIL: $reason was accepted" >&2
    exit 1
  fi
  [[ ! -e "$MOCK_MARKER" ]] || {
    echo "FAIL: $reason attempted to call age/S3 before validation" >&2
    exit 1
  }
  [[ "$output" == *"$expected"* ]] || { echo "FAIL: $reason wrong rejection: $output"; exit 1; }
  echo "PASS: $reason rejected before external service access"
}
counts="$(printf '0:%.0s' {1..33})0"
write_manifest "APP_ROW_COUNTS=$counts"
reject 'missing format' 'Backup snapshot lacks'
write_manifest BACKUP_FORMAT_VERSION=4 "APP_ROW_COUNTS=$counts"
reject 'obsolete format' 'Backup snapshot lacks'
write_manifest BACKUP_FORMAT_VERSION=5 "APP_ROW_COUNTS=${counts%:0}"
reject '33 instead of 34 counts' 'row-count manifest invalid'
write_manifest BACKUP_FORMAT_VERSION=5 "APP_ROW_COUNTS=${counts%0}invalid"
reject 'malformed counts' 'row-count manifest invalid'
write_manifest BACKUP_FORMAT_VERSION=5 "APP_ROW_COUNTS=$counts"
RICHCOMES_RECOVERY_PROJECT=iehuxwbrwfysgujaqagj RICHCOMES_CONFIRM_PROJECT=iehuxwbrwfysgujaqagj reject 'quarantined incident target' 'Specify a NEW'
RICHCOMES_RECOVERY_APPROVAL=NO reject 'missing approval' 'Record team/DB owner approval'
RICHCOMES_APPROVED_COMMIT=0000000000000000000000000000000000000000 reject 'wrong source commit' 'Use the exact approved merged commit'
printf 'tampered dump\n' > "$tmp/snapshot/garage.dump"
reject 'tampered V31 archive' 'Source archive checksum mismatch'
(cd "$tmp/snapshot" && sha256sum garage.dump flyway-history.dump > SHA256SUMS)
printf '%064d  /tmp/forbidden\n' 0 >> "$tmp/snapshot/SHA256SUMS"
reject 'unexpected archive member' 'Unexpected archive hash manifest'
# A valid synthetic manifest must reach the fake age binary, not an earlier rejection.
(cd "$tmp/snapshot" && sha256sum garage.dump flyway-history.dump > SHA256SUMS)
if bash "$repo/scripts/railway-offsite-v31-backup.sh" upload "$tmp/snapshot" > "$tmp/positive" 2>&1; then
  echo "FAIL: stubbed encryption unexpectedly succeeded"; exit 1
fi
[[ -s "$MOCK_MARKER" ]] && grep -q 'Client-side encryption failed' "$tmp/positive" ||
  { echo 'FAIL: valid manifest did not reach fake encryption'; cat "$tmp/positive"; exit 1; }
echo 'PASS: V31 offline guard tests and positive mock path'
