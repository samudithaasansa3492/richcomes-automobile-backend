#!/usr/bin/env python3
"""Offline fail-closed Flyway source policy tests. No external DB or credentials."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    "migration_policy", ROOT / "scripts/check-flyway-source-versions.py")
module = importlib.util.module_from_spec(spec)
assert spec and spec.loader
spec.loader.exec_module(module)


class SourceVersionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        self.original = self.folder / "V1__initial.sql"
        self.original.write_text("SELECT 1;\n", encoding="utf-8")
        self.baseline = {1: (self.original.name, module.sql_crc32(self.original))}

    def check(self):
        return module.audit(self.folder, self.baseline)

    def test_current_real_source_has_immutable_applied_migrations(self):
        n, errors = module.audit(module.SQL_DIR, module.applied_baseline())
        self.assertGreaterEqual(n, 28)
        self.assertEqual(errors, [])

    def test_new_version_is_automatically_accepted(self):
        (self.folder / "V2__second.sql").write_text("SELECT 2;\n", encoding="utf-8")
        (self.folder / "V3__third.sql").write_text("SELECT 3;\n", encoding="utf-8")
        self.assertEqual(self.check(), (3, []))

    def test_gap_is_rejected(self):
        (self.folder / "V3__out_of_order.sql").write_text("SELECT 3;\n")
        self.assertTrue(any("Missing migration versions" in e for e in self.check()[1]))

    def test_duplicate_version_is_rejected(self):
        (self.folder / "V2__first.sql").write_text("SELECT 2;\n")
        (self.folder / "V2__another.sql").write_text("SELECT 2;\n")
        self.assertTrue(any("Duplicate Flyway V2" in e for e in self.check()[1]))

    def test_changed_applied_sql_is_rejected(self):
        self.original.write_text("SELECT 4;\n")
        self.assertTrue(any("checksum changed" in e for e in self.check()[1]))

    def test_renamed_applied_sql_is_rejected(self):
        self.original.rename(self.folder / "V1__renamed.sql")
        self.assertTrue(any("applied migration path" in e for e in self.check()[1]))

    def test_invalid_file_is_rejected(self):
        (self.folder / "V2__bad-name.sql").write_text("SELECT 2;\n")
        self.assertTrue(any("Invalid migration filename" in e for e in self.check()[1]))

    def test_empty_migration_is_rejected(self):
        (self.folder / "V2__empty.sql").write_text(" \n")
        self.assertTrue(any("Empty migration" in e for e in self.check()[1]))

    def test_remove_applied_migration_is_rejected(self):
        self.original.unlink()
        self.assertTrue(any("missing" in e.lower() for e in self.check()[1]))


if __name__ == "__main__":
    unittest.main()
