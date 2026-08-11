import importlib.util
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE_PATH = ROOT / "tools" / "twin_staging_reconstruction.py"
SPEC = importlib.util.spec_from_file_location("twin_staging_reconstruction", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class TwinStagingReconstructionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.manifest = MODULE.load_manifest(MODULE.DEFAULT_MANIFEST)

    def test_manifest_and_sql_are_bounded(self):
        sql = MODULE.apply_sql(self.manifest)
        self.assertIn("restaurant_pos_staging", sql)
        self.assertIn("TWIN001_BASELINE_CATEGORY_CHANGED", sql)
        self.assertIn("LOCK TABLE flyway_schema_history, organizations, stores, menu_categories", sql)
        self.assertIn("TWIN001_BASELINE_COUNT_CHANGED", sql)
        self.assertIn("TWIN001_POST_WRITE_COUNT_REJECTED", sql)
        self.assertIn("ARRAY['1','2','3','4','5','6','7','8','9','10']", sql)
        self.assertIn("TWIN001_POST_OPTION_PARITY_REJECTED", sql)
        self.assertIn("TWIN001_POST_DEVICE_PARITY_REJECTED", sql)
        self.assertIn("delete=0", Path(MODULE_PATH).read_text(encoding="utf-8"))
        self.assertNotIn("DELETE FROM", sql.upper())
        self.assertNotIn("TRUNCATE", sql.upper())
        self.assertNotIn("ALTER TABLE", sql.upper())
        self.assertNotIn("flyway_schema_history SET", sql)

    def test_complete_relationship_contract(self):
        self.assertEqual(39, len(self.manifest["items"]))
        self.assertEqual(380, len(self.manifest["options"]))
        self.assertEqual(11, sum(row["parent_option_ref"] is not None for row in self.manifest["options"]))
        self.assertEqual(2, sum(row["sku"] == "fried_egg" for row in self.manifest["items"]))

    def test_snapshot_is_read_only_and_secret_free(self):
        sql = MODULE.SNAPSHOT_SQL.lower()
        self.assertIn("begin read only", sql)
        self.assertNotIn("select *", sql)
        for prohibited in ("password_hash", "refresh_tokens", "customers", "payments"):
            self.assertNotIn(prohibited, sql)
        self.assertEqual(1, sql.count("device_token_hash"))
        self.assertIn("device_token_hash is not null", sql)

    def test_baseline_contract_is_complete(self):
        baseline = MODULE.baseline_contract()
        self.assertEqual((4, 3, 13, 38), tuple(len(baseline[key]) for key in ("categories", "stations", "items", "options")))
        self.assertEqual(38, len({(row["item_sku"], row["option_code"]) for row in baseline["options"]}))


if __name__ == "__main__":
    unittest.main()
