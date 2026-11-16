#!/usr/bin/env python3
"""Selected Java/H2 checks only; retains a sanitized summary after terminal reloads."""
import datetime
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CLASSES = {
    'IdentityModelTest', 'IdentityPersistenceTest', 'UserAccountServiceTest', 'JwtTokenTest',
    'AuthenticationServiceTest', 'SecurityFlowTest', 'AdminAccessGuardTest', 'AdminAccessConcurrencyTest',
    'EmployeeAdminProtectionTest', 'FirstAdminProvisionerTest', 'FirstAdminCommandTest', 'ProvisioningTargetTest',
    'DeveloperAdminCommandTest', 'DeveloperAdminProvisionerTest', 'RecoveryPersistenceTest', 'RecoveryDeliveryTest',
}

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()

def main():
    if len(sys.argv) != 2 or not re.fullmatch(r'[0-9a-f]{40}', sys.argv[1]):
        raise SystemExit('Usage: python3 scripts/validate-developer-recovery.py <full-reviewed-sha>')
    expected = sys.argv[1]
    if git('rev-parse', 'HEAD') != expected or git('status', '--porcelain'):
        raise SystemExit('STOP: expected clean HEAD required; no build or tests started.')
    home = os.environ.get('JAVA_HOME', '')
    if not home:
        raise SystemExit('Select an existing Java 21 JAVA_HOME; no JDK will be installed.')
    java = subprocess.run([str(Path(home) / 'bin/java'), '-version'], capture_output=True, text=True, check=True)
    javac = subprocess.run([str(Path(home) / 'bin/javac'), '-version'], capture_output=True, text=True, check=True)
    if not re.search(r'version "21\.', java.stderr + java.stdout) or not (javac.stdout + javac.stderr).startswith('javac 21.'):
        raise SystemExit('STOP: Java 21 and javac 21 required.')
    os.umask(0o077)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    directory = ROOT.parent / 'richcomes-validation-reports' / ('developer-recovery-' + stamp)
    directory.mkdir(parents=True, exist_ok=False)
    report = directory / 'report.txt'
    lines = ['Selected developer identity/recovery validation', 'Start UTC: ' + stamp,
             'Branch: ' + git('branch', '--show-current'), 'Expected HEAD: ' + expected,
             'Initial clean tree: PASS', 'Java: ' + (java.stderr + java.stdout).splitlines()[0],
             'Compiler: ' + (javac.stdout + javac.stderr).strip()]
    result = 1
    environment = os.environ.copy()
    environment['PATH'] = str(Path(home) / 'bin') + os.pathsep + environment.get('PATH', '')
    try:
        commands = [('compile', ['./mvnw', '-B', 'clean', 'compile']),
                    ('tests', ['./mvnw', '-B', '-Dtest=' + ','.join(sorted(CLASSES)), 'test'])]
        for phase, command in commands:
            log = directory / (phase + '.log')
            with log.open('w') as stream:
                code = subprocess.run(command, cwd=ROOT, env=environment, stdout=stream,
                                      stderr=subprocess.STDOUT, timeout=600).returncode
            lines.append('Command: ' + ' '.join(command))
            lines.append(phase + ' exit code: ' + str(code))
            lines.append('Private diagnostic log: ' + str(log))
            if code:
                raise RuntimeError(phase + ' failed; later phases were not run')
        reports = sorted((ROOT / 'target/surefire-reports').glob('TEST-*.xml'))
        seen = set()
        totals = [0, 0, 0, 0]
        for path in reports:
            suite = ET.parse(path).getroot()
            name = suite.attrib['name'].rsplit('.', 1)[-1]
            if name not in CLASSES or name in seen:
                raise RuntimeError('Unexpected or duplicate test suite')
            seen.add(name)
            counts = [int(suite.attrib[key]) for key in ('tests', 'failures', 'errors', 'skipped')]
            lines.append(f'{name}: tests={counts[0]}, failures={counts[1]}, errors={counts[2]}, skipped={counts[3]}')
            if counts[0] == 0 or any(counts[1:]):
                raise RuntimeError('Selected suite incomplete or unsuccessful: ' + name)
            totals = [a + b for a, b in zip(totals, counts)]
        if seen != CLASSES:
            raise RuntimeError('Missing selected reports: ' + ', '.join(sorted(CLASSES - seen)))
        lines.append('Totals: tests=%d, failures=%d, errors=%d, skipped=%d' % tuple(totals))
        result = 0
    except RuntimeError as error:
        lines.append('STOP: ' + str(error))
    except Exception as error:
        # Do not serialize arbitrary subprocess/XML exceptions or environment values.
        lines.append('STOP: ' + type(error).__name__ + '; inspect private diagnostics')
    finally:
        same = git('rev-parse', 'HEAD') == expected and not git('status', '--porcelain')
        lines.append('Final HEAD unchanged and tree clean: ' + str(same))
        if not same:
            result = 1
        lines.append('Result: ' + ('PASS (selected Java/H2 scope)' if result == 0 else 'NOT VALIDATED / FAILED'))
        lines.append('No full suite, real SMTP, application startup, provisioning command invocation, Flyway, Docker or shared database validation.')
        lines.append('Only suite counts are extracted; XML system properties and test failure payloads are excluded.')
        lines.append('End UTC: ' + datetime.datetime.now(datetime.timezone.utc).isoformat())
        report.write_text('\n'.join(lines) + '\n')
        print(report.read_text(), end='')
        print('Saved report: ' + str(report))
    return result

if __name__ == '__main__':
    sys.exit(main())
