#!/usr/bin/env bash
# Run from a reviewed checkout; never launches the app, Flyway, Docker or provisioning.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

if [[ $# != 1 ]]; then
  echo 'Usage: bash scripts/validate-role-separation.sh <expected-full-commit-sha>' >&2
  exit 2
fi
expected_commit=$1
if [[ ! "$expected_commit" =~ ^[0-9a-f]{40}$ ]]; then
  echo 'Expected a full 40-character commit SHA.' >&2
  exit 2
fi
if [[ "$(git rev-parse HEAD)" != "$expected_commit" ]]; then
  echo 'HEAD does not match the reviewed commit. No build or tests started.' >&2
  exit 2
fi
if [[ -n "$(git status --porcelain)" ]]; then
  echo 'Working tree must be clean. No build or tests started.' >&2
  exit 2
fi
if [[ -z "${JAVA_HOME:-}" || ! -x "$JAVA_HOME/bin/java" || ! -x "$JAVA_HOME/bin/javac" ]]; then
  echo 'Select an existing Java 21 JDK in JAVA_HOME first; no JDK will be installed.' >&2
  exit 2
fi
export PATH="$JAVA_HOME/bin:$PATH"
if ! java -version 2>&1 | head -n 1 | grep -Eq 'version "21[.]'; then
  echo 'Java 21 is required. No build or tests started.' >&2
  exit 2
fi
if ! javac -version 2>&1 | grep -Eq '^javac 21[.]'; then
  echo 'Java 21 javac is required. No build or tests started.' >&2
  exit 2
fi
printf 'Branch: %s\nHEAD: %s\n' "$(git branch --show-current)" "$expected_commit"
# set -e deliberately stops before tests if compilation fails.
./mvnw clean compile
./mvnw -Dtest=IdentityModelTest,IdentityPersistenceTest,UserAccountServiceTest,JwtTokenTest,AuthenticationServiceTest,SecurityFlowTest,AdminAccessGuardTest,AdminAccessConcurrencyTest,EmployeeAdminProtectionTest,FirstAdminProvisionerTest,FirstAdminCommandTest,ProvisioningTargetTest test
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
expected = {
 'IdentityModelTest', 'IdentityPersistenceTest', 'UserAccountServiceTest', 'JwtTokenTest',
 'AuthenticationServiceTest', 'SecurityFlowTest', 'AdminAccessGuardTest', 'AdminAccessConcurrencyTest',
 'EmployeeAdminProtectionTest', 'FirstAdminProvisionerTest', 'FirstAdminCommandTest', 'ProvisioningTargetTest'
}
reports = list(Path('target/surefire-reports').glob('TEST-*.xml'))
seen = set()
totals = [0, 0, 0, 0]
for report in sorted(reports):
    suite = ET.parse(report).getroot()
    name = suite.attrib['name'].rsplit('.', 1)[-1]
    if name not in expected or name in seen:
        raise SystemExit(f'Unexpected or duplicate test report: {name}')
    seen.add(name)
    counts = [int(suite.attrib.get(key, 0)) for key in ('tests', 'failures', 'errors', 'skipped')]
    print(f'{name}: tests={counts[0]}, failures={counts[1]}, errors={counts[2]}, skipped={counts[3]}')
    if counts[0] == 0 or any(counts[1:]):
        raise SystemExit(f'Incomplete or unsuccessful suite: {name}')
    totals = [a+b for a,b in zip(totals, counts)]
if seen != expected:
    raise SystemExit(f'Missing reports: {sorted(expected-seen)}')
print(f'Total: tests={totals[0]}, failures={totals[1]}, errors={totals[2]}, skipped={totals[3]}')
print('Selected Java/H2 scope only. PostgreSQL V14 and HTTP/concurrency validation remain separate.')
PY
if [[ "$(git rev-parse HEAD)" != "$expected_commit" || -n "$(git status --porcelain)" ]]; then
  echo 'Repository changed during validation; inspect before accepting results.' >&2
  exit 1
fi
echo 'Selected validation complete; repository remains clean.'
