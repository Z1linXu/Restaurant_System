import hashlib
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs/governance/runtime/ST_DENIS_TWIN_PARITY_MANIFEST_V2.json"
MAPPING = ROOT / "docs/governance/runtime/V7_PRODUCTION_TO_V10_TWIN_CONFIGURATION_MAPPING.md"

class ManifestArtifactTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.value = json.loads(MANIFEST.read_text(encoding="utf-8"))

    def test_fingerprint_and_complete_graph(self):
        value = json.loads(MANIFEST.read_text(encoding="utf-8"))
        reported = value["manifest"].pop("fingerprint_sha256")
        body = json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        self.assertEqual(reported, hashlib.sha256(body.encode()).hexdigest())
        self.assertEqual(380, len(self.value["options"]))
        items = {row["source_ref"] for row in self.value["items"]}
        options = {row["source_ref"] for row in self.value["options"]}
        categories = {row["source_ref"] for row in self.value["categories"]}
        stations = {row["source_ref"] for row in self.value["stations"]}
        printers = {row["source_ref"] for row in self.value["printers"]}
        self.assertTrue(all(row["item_ref"] in items for row in self.value["options"]))
        self.assertTrue(all(row["parent_option_ref"] in options for row in self.value["options"] if row["parent_option_ref"] is not None))
        self.assertTrue(all(row["category_ref"] in categories and row["station_ref"] in stations for row in self.value["items"]))
        self.assertTrue(all(row["printer_ref"] in printers for row in self.value["printer_assignments"]))

    def test_contract_provenance_and_prohibited_surface(self):
        contract = self.value["source_schema_contract"]["required_columns"]
        for table in ("organizations", "users", "roles", "organization_memberships", "store_memberships", "user_stations", "role_permissions", "receipt_templates"):
            self.assertIn(table, contract)
        self.assertEqual("READ_ONLY_COMPARISON_PERFORMED", self.value["collection_safety"]["staging_access"])
        encoded = json.dumps(self.value).lower()
        for prohibited in ('"password', 'token_hash', 'ip_address', '"phone"', '"full_name"', '"port"'):
            self.assertNotIn(prohibited, encoded)
        mapping = MAPPING.read_text(encoding="utf-8")
        self.assertIn("V8--V10 add only request/audit tables", mapping)
        self.assertIn("`MAPPED`", mapping)

if __name__ == "__main__":
    unittest.main()
