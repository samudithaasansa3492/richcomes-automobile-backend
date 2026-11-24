#!/usr/bin/env python3
"""Offline unit tests for read-only open-PR collision detection; no GH_TOKEN used."""
import importlib.util
from pathlib import Path
import unittest

module_path = Path(__file__).with_name("check-pr-flyway-collisions.py")
spec = importlib.util.spec_from_file_location("flyway_pr_claims", module_path)
module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(module)


def f(path: str, status: str = "added") -> dict:
    return {"filename": "src/main/resources/db/migration/" + path, "status": status}


class PendingMigrationClaimsTests(unittest.TestCase):
    def test_detect_same_version_different_filenames(self):
        us = module.new_version_claims([f("V30__customer.sql")])
        them = module.new_version_claims([f("V30__inventory.sql")])
        self.assertEqual(module.collisions(us, them), [30])

    def test_distinct_versions_are_allowed(self):
        us = module.new_version_claims([f("V29__appointments.sql")])
        them = module.new_version_claims([f("V30__billing.sql")])
        self.assertEqual(module.collisions(us, them), [])

    def test_existing_sql_edits_are_not_new_claims(self):
        self.assertEqual(module.new_version_claims([f("V28__inventory.sql", "modified")]), {})

    def test_renamed_new_version_is_claimed(self):
        self.assertEqual(set(module.new_version_claims([f("V30__rename.sql", "renamed")])), {30})

    def test_unrelated_and_malformed_files_not_claimed(self):
        result = module.new_version_claims([
            f("V0__invalid.sql"), f("V31__bad-name.sql"),
            {"filename": "src/main/java/Version.java", "status": "added"}
        ])
        self.assertEqual(result, {})

    def test_two_claims_in_one_pr_are_visible(self):
        claims = module.new_version_claims([
            f("V29__a.sql"), f("V30__b.sql")
        ])
        self.assertEqual(set(claims), {29, 30})


if __name__ == "__main__":
    unittest.main()
