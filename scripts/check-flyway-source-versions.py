#!/usr/bin/env python3
"""Read-only source audit; does not import application config, contact DB, or run Flyway."""
from collections import defaultdict
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1] / "src/main/resources/db/migration"
files = sorted(root.glob("V*__*.sql"))
versions = defaultdict(list)
errors = []

for path in files:
    match = re.fullmatch(r"V([1-9][0-9]*)__([a-zA-Z0-9_]+)\.sql", path.name)
    if not match:
        errors.append(f"Invalid Flyway migration filename: {path.name}")
        continue
    versions[int(match.group(1))].append(path.name)

for version, names in sorted(versions.items()):
    if len(names) != 1:
        errors.append(f"Duplicate V{version}: {', '.join(names)}")

expected = {
    12: "V12__create_service_reminders.sql",
    13: "V13__create_job_items_and_parts.sql",
    14: "V14__create_invoice_tables.sql",
    15: "V15__inventory_decimal_quantities.sql",
    16: "V16__add_stock_item_unit.sql",
    17: "V17__create_identity_and_access_tables.sql",
    18: "V18__seed_member_one_roles_and_permissions.sql",
    19: "V19__separate_owner_and_technical_admin.sql",
    20: "V20__technical_identities_and_recovery.sql",
    21: "V21__customer_accounts_and_delegated_registration.sql",
    22: "V22__seed_owner_delegatable_business_permissions.sql",
    23: "V23__verified_customer_onboarding.sql",
    24: "V24__create_multi_store_inventory.sql",
    25: "V25__add_store_location_to_service_job_parts.sql",
    26: "V26__add_inventory_permissions.sql",
    27: "V27__add_customer_consent_verification.sql",
    28: "V28__add_stock_transaction_store_location.sql",
    29: "V29__add_appointment_cancellation.sql",
}
for version, name in expected.items():
    if versions.get(version) != [name]:
        errors.append(f"Incorrect V{version} path: expected {name}")

if set(versions) != set(range(1, 30)):
    errors.append(f"Expected exactly versions V1–V29; found {sorted(versions)}")

print(f"Source-only Flyway filename audit: {len(files)} files")
for version in sorted(versions):
    print(f"  V{version}: {', '.join(versions[version])}")

if errors:
    print("FAIL:")
    for err in errors:
        print(f"  - {err}")
    sys.exit(1)

print("PASS: unique migration versions V1–V29; source names V24–V29 preserved")
print("NOTE: this is NOT PostgreSQL migration, checksum, SQL-content, or Flyway validation.")
