#!/usr/bin/env python3
"""Offline unit tests for source/historical Flyway checksum comparison."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

scripts = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location(
    "verify_applied_history", scripts / "verify-applied-flyway-history.py")
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
fixture = (scripts / "fixtures" / "v14-applied-flyway-history.csv").read_text()


class LiveHistoryPreflightTests(unittest.TestCase):
    def test_source_scripts_match_verified_v14_history(self):
        self.assertEqual(module.verify(fixture), [])

    def test_changed_checksum_is_denied(self):
        corrupted = fixture.replace("1050085485", "1050085486", 1)
        self.assertTrue(any("checksum" in item for item in module.verify(corrupted)))

    def test_malformed_and_missing_history_are_denied(self):
        self.assertTrue(module.verify(""))
        self.assertTrue(module.verify("unexpected"))
        self.assertTrue(module.verify(fixture.splitlines()[0] + "\n"))

    def test_duplicate_version_is_denied(self):
        lines = fixture.splitlines()
        lines[-1] = lines[0]
        self.assertTrue(any("Duplicate applied" in item for item in module.verify("\n".join(lines))))

    def test_failed_sql_migration_is_denied(self):
        rows = fixture.replace("-2135815917,t", "-2135815917,f")
        self.assertTrue(any("not marked successful" in x for x in module.verify(rows)))

    def test_name_mismatch_is_denied(self):
        rows = fixture.replace("V14__create_invoice_tables.sql", "V14__renamed.sql")
        self.assertTrue(any("missing, renamed" in x for x in module.verify(rows)))

    def test_future_version_is_denied_until_release_procedure_updated(self):
        rows = fixture + "15,15,V15__inventory_decimal_quantities.sql,1,t\n"
        self.assertTrue(any("Unexpected applied version" in x for x in module.verify(rows)))

    def test_crc32_ignores_line_ending_style(self):
        with tempfile.TemporaryDirectory() as tmp:
            left = Path(tmp) / "lf.sql"
            right = Path(tmp) / "crlf.sql"
            left.write_bytes(b"-- example\nSELECT 1;\n")
            right.write_bytes(b"-- example\r\nSELECT 1;\r\n")
            self.assertEqual(module.flyway_sql_checksum(left), module.flyway_sql_checksum(right))


if __name__ == "__main__":
    unittest.main()
