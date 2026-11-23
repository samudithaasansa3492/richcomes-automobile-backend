#!/usr/bin/env python3
"""Offline V28 historical checksum tests: no database connection or credentials."""
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
observed = (folder / "fixtures/v28-applied-flyway-history.csv").read_text()


class V28HistoryTests(unittest.TestCase):
    def test_exact_applied_v28_matches_source_migrations(self):
        self.assertEqual(module.verify(observed, expected_latest=28), [])

    def test_missing_applied_version_is_rejected(self):
        rows = observed.splitlines()
        self.assertTrue(module.verify("\n".join(rows[:-1]), expected_latest=28))

    def test_changed_v28_checksum_is_rejected(self):
        rows = observed.splitlines()
        rows[-1] = rows[-1].replace("-1018756729", "-1709613710")
        self.assertTrue(any("checksum" in x
                            for x in module.verify("\n".join(rows), expected_latest=28)))

    def test_duplicate_version_is_rejected(self):
        rows = observed.splitlines()
        rows[-1] = rows[-2]
        self.assertTrue(module.verify("\n".join(rows), expected_latest=28))

    def test_v26_history_remains_supported(self):
        archived = (folder / "fixtures/v26-applied-flyway-history.csv").read_text()
        self.assertEqual(module.verify(archived, expected_latest=26), [])

    def test_v29_requires_separate_review(self):
        rows = observed.splitlines()
        rows.append("29,29,V29__unreviewed.sql,123,t")
        self.assertTrue(module.verify("\\n".join(rows), expected_latest=28))

    def test_v14_default_remains_unchanged(self):
        archived = (folder / "fixtures/v14-applied-flyway-history.csv").read_text()
        self.assertEqual(module.verify(archived), [])


if __name__ == "__main__":
    unittest.main()
