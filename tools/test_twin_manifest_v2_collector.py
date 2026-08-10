import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from twin_manifest_v2_collector import FORBIDDEN, REQUIRED_COLUMNS, SQL, STAGING_SQL, normalize

class CollectorContractTest(unittest.TestCase):
    def test_query_has_no_prohibited_surface(self):
        self.assertFalse(any(term in SQL.lower() for term in FORBIDDEN))
        self.assertFalse(any(term in STAGING_SQL.lower() for term in FORBIDDEN))

    def test_normalize_replaces_production_ids_with_refs(self):
        raw = {"store":{"organization_id":1,"code":"4483_R_SAINT_DENIS","name":"4483 R. Saint-Denis","status":"active","enable_bar_kitchen_tasks":False,"printing_enabled":True,"printing_mode":"PAD_DIRECT","menu_revision":1,"menu_updated_at":"x"},"organization":{"code":"LANZHOU_NOODLES","name":"Lanzhou Noodles","status":"active"},"flyway":[],"categories":[{"id":10,"code":"A","name_zh":"甲","name_en":"A","sort_order":1,"is_active":True}],"stations":[{"id":20,"code":"S","name":"S","sort_order":1,"is_active":True}],"items":[{"id":30,"category_id":10,"station_id":20,"sku":"x","item_type":"menu_item","name_zh":"甲","name_en":"A","base_price":1,"cost_per_item":None,"is_active":True,"is_sold_out":False,"sort_order":1}],"options":[{"id":40,"menu_item_id":30,"option_type":"addon","option_code":"x","option_group":"g","parent_option_id":None,"sort_order":1,"name_zh":"甲","name_en":"A","price_delta":0,"is_active":True}],"tables":[],"staff":[],"organization_memberships":[],"store_memberships":[],"user_stations":[],"role_permissions":[],"kds_display_configs":[],"printers":[],"printer_assignments":[],"receipt_templates":[],"devices":[],"staging_comparison":{"store":{},"counts":{},"flyway":[]}}
        result = normalize(raw)
        encoded = json.dumps(result)
        self.assertNotIn('"id": 30', encoded)
        self.assertEqual("CAT-001", result["items"][0]["category_ref"])
        self.assertEqual("ITEM-001", result["options"][0]["item_ref"])

if __name__ == "__main__":
    unittest.main()
