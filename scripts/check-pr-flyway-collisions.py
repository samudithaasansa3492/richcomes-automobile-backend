#!/usr/bin/env python3
"""Read-only GitHub PR migration-version collision check.

Only claims made by OPEN pull requests are visible. Unpushed/unopened branches
cannot be detected: the team must coordinate reservations before creating SQL.
"""
from __future__ import annotations

import json
import os
import re
import sys
from urllib.request import Request, urlopen

MIGRATION = re.compile(r"\Asrc/main/resources/db/migration/V([1-9][0-9]*)__[A-Za-z0-9_]+\.sql\Z")
REPO = re.compile(r"\A[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")


def new_version_claims(files: list[dict]) -> dict[int, set[str]]:
    claims: dict[int, set[str]] = {}
    for file in files:
        # A renamed-to-new-version file is also a claim on the new number.
        if file.get("status") not in {"added", "renamed"}:
            continue
        name = file.get("filename", "")
        match = MIGRATION.fullmatch(name)
        if match:
            claims.setdefault(int(match.group(1)), set()).add(name)
    return claims


def collisions(current_claims: dict[int, set[str]],
               other_claims: dict[int, set[str]]) -> list[int]:
    return sorted(set(current_claims).intersection(other_claims))


def fetch_pages(repo: str, subpath: str, token: str, limit: int = 5) -> list:
    data = []
    for page in range(1, limit + 1):
        path = f"https://api.github.com/repos/{repo}/{subpath}"
        separator = "&" if "?" in path else "?"
        req = Request(f"{path}{separator}per_page=100&page={page}", headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "richcomes-flyway-pr-coordination",
        })
        with urlopen(req, timeout=15) as response:
            batch = json.load(response)
        if not isinstance(batch, list):
            raise ValueError("Unexpected GitHub API response")
        data.extend(batch)
        if len(batch) < 100:
            return data
    # Fail closed rather than silently ignoring PRs/files on large repositories.
    raise ValueError("GitHub pagination exceeded safe inspection limit")


def main() -> int:
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    token = os.environ.get("GH_TOKEN", "")
    pr_text = os.environ.get("CURRENT_PR", "")
    if not REPO.fullmatch(repo) or not pr_text.isdecimal() or not token:
        print("STOP: PR-only read token and repository/PR metadata required", file=sys.stderr)
        return 2
    current = int(pr_text)
    try:
        current_files = fetch_pages(repo, f"pulls/{current}/files", token)
        requested = new_version_claims(current_files)
        if not requested:
            print("PASS: PR introduces no new Flyway version claims")
            return 0
        open_prs = fetch_pages(repo, "pulls?state=open", token)
        conflicts = []
        for other in open_prs:
            number = other.get("number")
            if not isinstance(number, int) or number == current:
                continue
            their_files = fetch_pages(repo, f"pulls/{number}/files", token)
            overlapping = collisions(requested, new_version_claims(their_files))
            if overlapping:
                conflicts.append((number, overlapping))
    except (OSError, TimeoutError, ValueError, json.JSONDecodeError) as exc:
        print(f"FAIL: unable to inspect all open PR version claims ({type(exc).__name__})",
              file=sys.stderr)
        return 1
    if conflicts:
        print("FAIL: migration version already claimed by another open PR",
              file=sys.stderr)
        for number, versions in conflicts:
            print(f" - PR #{number} also adds: " +
                  ", ".join(f"V{v}" for v in versions), file=sys.stderr)
        print("Coordinate/rebase and rename only UNAPPLIED migrations; never edit applied SQL.",
              file=sys.stderr)
        return 1
    print("PASS: new migration version(s) " +
          ", ".join(f"V{v}" for v in sorted(requested)) +
          " do not collide with other open PRs")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
