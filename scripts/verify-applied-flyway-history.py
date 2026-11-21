#!/usr/bin/env python3
"""Verify immutable applied V1-V14 Flyway names/checksums against Git-tracked SQL.

Consumes only non-sensitive CSV metadata from the database's flyway_schema_history.
No network, no DB credentials, no Flyway startup and no schema mutations.
"""
from __future__ import annotations

import argparse
import csv
import io
import re
import sys
import zlib
from pathlib import Path

EXPECTED_VERSIONS = tuple(range(1, 15))
ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/db/migration"
SAFE_SCRIPT = re.compile(r"V([1-9][0-9]*)__([A-Za-z0-9_]+)\.sql\Z")


def flyway_sql_checksum(path: Path) -> int:
    # Flyway calculates CRC32 across SQL lines, omitting line separators.
    # In particular, CRC32 over raw file bytes (including LF) is incorrect.
    decoded = path.read_bytes().decode("utf-8-sig")
    crc = zlib.crc32("".join(decoded.splitlines()).encode("utf-8"))
    return crc if crc < (1 << 31) else crc - (1 << 32)


def verify(history_csv: str, root: Path = ROOT, expected_latest: int = 14) -> list[str]:
    expected_versions = tuple(range(1, expected_latest + 1))
    rows = list(csv.reader(io.StringIO(history_csv)))
    errors: list[str] = []
    if len(rows) != len(expected_versions):
        errors.append(f"Expected exactly {expected_latest} applied migration rows, found {len(rows)}")
    seen: set[int] = set()
    for row in rows:
        if len(row) != 5:
            errors.append("Malformed history CSV: expected five columns per row")
            continue
        rank_text, version_text, script, checksum_text, success = row
        try:
            rank = int(rank_text)
            version = int(version_text)
            checksum = int(checksum_text)
        except ValueError:
            errors.append("Malformed history CSV: rank, version and checksum must be integers")
            continue
        if version not in expected_versions:
            errors.append(f"Unexpected applied version V{version} (expected only V1-V{expected_latest})")
            continue
        if rank != version:
            errors.append(f"V{version}: noncontiguous installed rank {rank}")
        if version in seen:
            errors.append(f"Duplicate applied version V{version}")
            continue
        seen.add(version)
        if success.lower() not in {"t", "true"}:
            errors.append(f"V{version}: history row is not marked successful")
        match = SAFE_SCRIPT.fullmatch(script)
        if match is None or int(match.group(1)) != version:
            errors.append(f"V{version}: invalid or mismatched history script name")
            continue
        matches = sorted(root.glob(f"V{version}__*.sql"))
        if len(matches) != 1 or matches[0].name != script:
            errors.append(f"V{version}: source script missing, renamed or duplicated")
            continue
        try:
            actual_checksum = flyway_sql_checksum(matches[0])
        except (OSError, UnicodeDecodeError) as exc:
            errors.append(f"V{version}: cannot read UTF-8 SQL ({type(exc).__name__})")
            continue
        if actual_checksum != checksum:
            errors.append(
                f"V{version}: applied checksum {checksum} != source checksum {actual_checksum}"
            )
    missing = set(expected_versions) - seen
    if missing:
        errors.append("Missing successful history versions: " + ",".join(map(str, sorted(missing))))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--history-csv", type=str, default="-",
        help="CSV file from read-only catalog COPY query, or '-' to read stdin",
    )
    parser.add_argument('--expected-version', type=int, choices=(14, 26), default=14,
                        help='Exact frozen source migration upper bound; default V14 for historical archives')
    args = parser.parse_args()
    try:
        source = sys.stdin.read() if args.history_csv == "-" else Path(args.history_csv).read_text()
    except OSError as exc:
        print(f"STOP: Unable to load history CSV: {type(exc).__name__}", file=sys.stderr)
        return 2
    errors = verify(source, expected_latest=args.expected_version)
    if errors:
        print("FAIL: applied SQL migration preflight", file=sys.stderr)
        for message in errors:
            print(" - " + message, file=sys.stderr)
        return 1
    print(f"PASS: shared V1-V{args.expected_version} Flyway history matches {args.expected_version} immutable Git source names and CRC32 checksums")
    print("NOTE: this is read-only history matching, NOT a backup, migration or deploy authorization")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
