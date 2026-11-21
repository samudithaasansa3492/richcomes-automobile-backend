#!/usr/bin/env python3
"""Offline V26 historical checksum tests: no database connection or credentials."""
import importlib.util
import unittest
from pathlib import Path

folder = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "historical", folder / "verify-applied-flyway-history.py"
)
module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(module)
observed = (folder / "fixtures/v26-applied-flyway-history.csv").read_text()


class V26HistoryTests(unittest.TestCase):
    def test_exact_applied_v26_matches_source_migrations(self):
        self.assertEqual(module.verify(observed, expected_latest=26), [])

    def test_missing_applied_version_is_rejected(self):
        rows = observed.splitlines()
        self.assertTrue(module.verify("\n".join(rows[:-1]), expected_latest=26))

    def test_changed_v26_checksum_is_rejected(self):
        rows = observed.splitlines()
        rows[-1] = rows[-1].replace("-1709613711", "-1709613710")
        self.assertTrue(any("checksum" in x
                            for x in module.verify("\n".join(rows), expected_latest=26)))

    def test_duplicate_version_is_rejected(self):
        rows = observed.splitlines()
        rows[-1] = rows[-2]
        self.assertTrue(module.verify("\n".join(rows), expected_latest=26))

    def test_v14_default_remains_unchanged(self):
        archived = (folder / "fixtures/v14-applied-flyway-history.csv").read_text()
        self.assertEqual(module.verify(archived), [])


if __name__ == "__main__":
    unittest.main()
