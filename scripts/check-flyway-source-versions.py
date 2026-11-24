#!/usr/bin/env python3
"""Fail-closed source Flyway audit; outputs the latest version with --latest.

This DOES NOT connect to any database, migrate, prove a backup, or authorize deployment.
Previously applied V1-V28 SQL paths and Flyway CRC32 values are pinned; newer
versions are dynamically discovered, but MUST be consecutive and uniquely named.
"""
from __future__ import annotations

import argparse
import csv
from pathlib import Path
import re
import sys
import zlib

ROOT = Path(__file__).resolve().parents[1]
SQL_DIR = ROOT / "src/main/resources/db/migration"
V26_FIXTURE = ROOT / "scripts/fixtures/v26-applied-flyway-history.csv"
SQL_NAME = re.compile(r"V([1-9][0-9]*)__([A-Za-z0-9_]+)\.sql\Z")
# These migrations were confirmed successfully applied in shared Supabase;
# never rewrite, rename, or reorder them. Change only with explicit DBA review
# and a separately approved recovery plan; automated CI must not weaken this.
APPLIED_V27_V28 = {
    27: ("V27__add_customer_consent_verification.sql", 655573779),
    28: ("V28__add_stock_transaction_store_location.sql", -1018756729),
}


def sql_crc32(path: Path) -> int:
    text = path.read_bytes().decode("utf-8-sig")
    result = zlib.crc32("".join(text.splitlines()).encode("utf-8"))
    return result if result < 2**31 else result - 2**32


def applied_baseline() -> dict[int, tuple[str, int]]:
    baseline = {}
    with V26_FIXTURE.open(newline="", encoding="utf-8") as file:
        for row in csv.reader(file):
            if len(row) != 5:
                raise ValueError("Invalid V26 applied migration fixture")
            rank, version, script, checksum, success = row
            n = int(version)
            if int(rank) != n or n != len(baseline) + 1 or success.lower() not in ("t", "true"):
                raise ValueError("Nonconsecutive or failed V26 fixture")
            if SQL_NAME.fullmatch(script) is None:
                raise ValueError("Unsafe SQL migration in historical fixture")
            baseline[n] = (script, int(checksum))
    if sorted(baseline) != list(range(1, 27)):
        raise ValueError("Expected exactly 26 historical applied migrations")
    baseline.update(APPLIED_V27_V28)
    return baseline


def audit(folder: Path, immutable: dict[int, tuple[str, int]]) -> tuple[int, list[str]]:
    """Return latest version and errors; does not touch a database."""
    candidates = sorted(folder.glob("V*"))
    found: dict[int, list[Path]] = {}
    errors: list[str] = []
    for file in candidates:
        if file.is_symlink() or not file.is_file():
            errors.append(f"Unexpected migration path type: {file.name}")
            continue
        match = SQL_NAME.fullmatch(file.name)
        if not match:
            errors.append(f"Invalid migration filename: {file.name}")
            continue
        version = int(match.group(1))
        found.setdefault(version, []).append(file)
        try:
            if not file.read_bytes().strip():
                errors.append(f"Empty migration SQL: {file.name}")
        except OSError:
            errors.append(f"Unreadable migration SQL: {file.name}")
    for version, files in sorted(found.items()):
        if len(files) != 1:
            errors.append(f"Duplicate Flyway V{version}: {', '.join(f.name for f in files)}")
    latest = max(found, default=0)
    if latest > 10000:
        errors.append("Unreasonably large migration version; require explicit review")
    elif sorted(found) != list(range(1, latest + 1)):
        missing = sorted(set(range(1, latest + 1)) - set(found))
        errors.append(f"Missing migration versions (no gaps allowed): {missing}")
    if latest < max(immutable, default=0):
        errors.append("Missing previously applied migrations from immutable source baseline")
    for n, (name, checksum) in sorted(immutable.items()):
        files = found.get(n, [])
        if len(files) != 1 or files[0].name != name:
            errors.append(f"V{n}: applied migration path missing/renamed; expected {name}")
            continue
        try:
            actual = sql_crc32(files[0])
            if actual != checksum:
                errors.append(f"V{n}: applied SQL checksum changed (expected {checksum}, got {actual})")
        except (OSError, UnicodeDecodeError):
            errors.append(f"V{n}: applied SQL could not be decoded")
    return latest, errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--latest", action="store_true",
                        help="Output ONLY the latest validated integer version for CI scripts")
    args = parser.parse_args()
    try:
        latest, errors = audit(SQL_DIR, applied_baseline())
    except (OSError, UnicodeDecodeError, ValueError) as exc:
        print(f"FAIL: immutable baseline unavailable or invalid: {type(exc).__name__}", file=sys.stderr)
        return 1
    if errors:
        print("FAIL: source migration preflight", file=sys.stderr)
        for error in errors:
            print(" - " + error, file=sys.stderr)
        return 1
    if args.latest:
        print(latest)
    else:
        print(f"PASS: V1–V{latest} are unique, complete and contiguous")
        print("PASS: immutable applied V1–V28 filenames and Flyway CRC32 match source")
        print("NOTE: source-only validation, not live Flyway history, backup or release approval")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
