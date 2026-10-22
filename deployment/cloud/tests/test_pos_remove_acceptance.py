"""A menu revision is metadata; all Combo business fields remain guarded."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('pos_remove_acceptance',
    Path(__file__).resolve().parents[1] / 'pos-production-removes/acceptance.py')
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)

class ComboAcceptanceTest(unittest.TestCase):
    def test_revision_only_change_does_not_change_policy(self):
        before = {'store_id': 1, 'menu_revision': 25, 'groups': [{'component_code': 'combo_edamame', 'enabled': True}], 'item_overrides': []}
        after = dict(before, menu_revision=26)
        self.assertEqual(acceptance.combo_business_values(before), acceptance.combo_business_values(after))
        self.assertEqual(before['menu_revision'], 25)

    def test_component_or_store_change_still_fails_equality(self):
        before = {'store_id': 1, 'menu_revision': 25, 'groups': [{'component_code': 'combo_edamame', 'enabled': True}], 'item_overrides': []}
        for after in (dict(before, store_id=2), dict(before, groups=[]), dict(before, item_overrides=[{'item_id': 1}])):
            self.assertNotEqual(acceptance.combo_business_values(before), acceptance.combo_business_values(after))

if __name__ == '__main__': unittest.main()
