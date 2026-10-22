"""Pure fake checks: never invoke HTTP, Docker, login or a server filesystem."""
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

SPEC = importlib.util.spec_from_file_location('five_acceptance', Path(__file__).with_name('acceptance.py'))
a = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(a)


class SafetyTests(unittest.TestCase):
    def test_fixed_staging_origin_and_source_guard(self):
        self.assertEqual(a.API_BASE, 'https://staging-pos.lanzhounoodlesmtl.com/api/v1')
        self.assertEqual(a.h.API_BASE, a.API_BASE)
        with self.assertRaisesRegex(a.NoGo, 'exact_release_script_required'):
            a.runtime('a' * 40)

    def test_mutations_cannot_reach_other_store_old_order_or_devices(self):
        api = a.StagingApi('run20261021')
        for path, method, body in [
            ('/admin/printing/status', 'PUT', {'store_id': 1, 'printing_mode': 'MOCK'}),
            ('/admin/printing/status', 'PUT', {'store_id': 18, 'printing_mode': 'REAL'}),
            ('/orders/10402/updates', 'POST', {'idempotency_key': api.prefix + '_old'}),
            ('/admin/menu/removes/8?store_id=18', 'PUT', {'store_id': 18}),
            ('/admin/platform/menu/items', 'POST', {'store_id': 18, 'sku': api.prefix + '_x', 'base_price': 1}),
            ('/admin/printing/assignments/GRAB', 'PUT', {'store_id': 18, 'enabled': True}),
            ('/devices/1/heartbeat', 'POST', {}),
            ('/admin/integrations/uber-eats/orders', 'GET', None),
            ('/api/printing/jobs/1/payload', 'GET', None),
        ]:
            with self.subTest(path=path), self.assertRaises(a.NoGo):
                api.check_write(path, method, body)
        api.check_write('/admin/printing/status', 'PUT', {'store_id': 18, 'printing_mode': 'MOCK'})
        api.orders.add(44)
        api.check_write('/orders/44/updates', 'POST', {'idempotency_key': api.prefix + '_update', 'items': []})

    def test_applicability_only_new_test_items_and_no_category_broadcast(self):
        api = a.StagingApi('run20261021'); api.removes.add(8); api.items.update({40, 41})
        path = '/admin/menu/removes/8/applicability?store_id=18'
        body = {'store_id': 18, 'category_ids': [], 'items': [{'item_id': 40, 'mode': 'ALLOW'}]}
        api.check_write(path, 'PUT', body)
        for changed in [dict(body, category_ids=[1]), dict(body, items=[{'item_id': 400, 'mode': 'ALLOW'}])]:
            with self.assertRaises(a.NoGo): api.check_write(path, 'PUT', changed)

    def test_existing_credentials_fallback_only_after_explicit_401(self):
        original = b'{"owner_login_identifier":"synthetic_owner","owner_login_password":"old_private_secret"}'
        api = Mock(); api.login.side_effect = [a.NoGo('http_status_401_expected_200'), {'id': 7}]
        with patch.object(a.h, 'private_bytes', side_effect=[original, b'current_private_secret\n']) as read:
            self.assertEqual(a.login(api), {'id': 7})
            self.assertEqual([call.args[0] for call in read.call_args_list], [a.CREDENTIALS, a.FALLBACK])
            self.assertEqual(api.login.call_args_list[1].args[0]['owner_login_password'], 'current_private_secret')
        for failure in ['http_status_403_expected_200', 'api_transport_failed_no_automatic_mutation_retry']:
            api = Mock(); api.login.side_effect = a.NoGo(failure)
            with patch.object(a.h, 'private_bytes', return_value=original) as read, self.assertRaises(a.NoGo):
                a.login(api)
            self.assertEqual(read.call_count, 1)
            self.assertEqual(api.login.call_count, 1)


class PricingTests(unittest.TestCase):
    @staticmethod
    def line(deltas):
        return {'unit_price_snapshot': '16.99', 'quantity': 2,
                'options': [{'option_price_snapshot': delta, 'quantity': count} for delta, count in deltas]}

    def test_per_unit_size_combo_egg_and_negative_small(self):
        for deltas, expected in [([('2', 1)], '37.98'), ([('-2', 1)], '29.98'),
                                  ([('2', 1), ('5', 1)], '47.98'),
                                  ([('2', 1), ('5', 1), ('1.99', 1), ('0', 1)], '51.96')]:
            with self.subTest(expected=expected):
                self.assertEqual(str(a.line_amount(self.line(deltas))), expected)
        self.assertEqual(str(a.tax(a.money('47.98'))), '7.19')
        self.assertEqual(a.component_id('COMBO_SIDE', 'combo_cucumber_salad'), -20203)
        self.assertLess(a.component_id('COMBO_SIDE', 'combo_daily_side'), -300000)

    def test_database_receipt_conservation_rejects_old_base_only_display(self):
        api = a.StagingApi('run20261021')
        line = dict(self.line([('2', 1)]), menu_item_id=40)
        detail = {'id': 45, 'subtotal_amount': '37.98', 'total_amount': '43.67', 'items': [
            {'menu_item_id': 40, 'quantity': 2, 'unit_price': '16.99', 'line_amount': '37.98'}]}
        receipt = '$18.99\n$18.99\nSubtotal: $37.98\nTax (14.975%): $5.69\nTotal: $43.67'
        job = {'id': 50, 'store_id': 18, 'order_id': 45, 'module_code': 'FRONTDESK_RECEIPT',
               'status': 'PRINTED', 'rendered_text_snapshot': receipt}
        api.call = Mock(side_effect=lambda path, **kw: detail if path == '/orders/45' else [job])
        report = {'checks': []}
        runner = a.Acceptance(api, 'run20261021', report, lambda: None)
        runner.verify_order(45, [line])
        self.assertEqual(report['checks'][0]['subtotal'], '37.98')
        job['rendered_text_snapshot'] = receipt.replace('$18.99', '$16.99')
        with self.assertRaisesRegex(a.NoGo, 'receipt_amount_mismatch'):
            runner.verify_order(45, [line])
        detail['subtotal_amount'] = '35.98'
        with self.assertRaisesRegex(a.NoGo, 'persisted_order_amount_mismatch'):
            runner.verify_order(45, [line])

    def test_job_polling_does_not_retry_a_mutation_or_touch_physical_printing(self):
        api = a.StagingApi('run20261021')
        api.call = Mock(return_value=[{'module_code': 'GRAB', 'status': 'PRINTING'}])
        runner = a.Acceptance(api, 'run20261021', {'checks': []}, lambda: None)
        with self.assertRaisesRegex(a.NoGo, 'mock_job_failed_or_wrong_transport'):
            runner.jobs(45, 'GRAB')
        self.assertEqual(api.call.call_args.args, ('/orders/45/print-jobs',))
        self.assertEqual(api.call.call_count, 1)

    def test_hot_uses_actual_active_wok_then_existing_paid_fried_egg(self):
        api = a.StagingApi('run20261021')
        runner = a.Acceptance(api, 'run20261021', {'checks': []}, lambda: None)
        no_spicy = {'id': 1, 'is_active': True, 'option_type': 'spicy_level', 'option_group': 'SPICINESS',
                    'option_code': 'spicy_none', 'name_zh': '不辣', 'name_en': 'Not Spicy', 'price_delta': 0}
        egg = {'id': 2, 'is_active': True, 'option_type': 'addon', 'option_group': 'ADD_ON',
               'option_code': 'fried_egg', 'name_zh': '煎蛋', 'name_en': 'Fried Egg', 'price_delta': '1.99'}
        beef = {'id': 40, 'is_active': True, 'sku': 'traditional_beef_noodle', 'options': [no_spicy, egg],
                'category_id': 3, 'station_id': 8, 'name_zh': '牛肉面', 'name_en': 'Beef Noodle', 'base_price': '16.99', 'item_type': 'main'}
        wok = dict(beef, id=41, sku='beef_chow_mein', options=[no_spicy])
        runner.catalog = {'categories': [{'id': 3, 'code': 'NOODLE', 'items': [beef, wok]}]}
        line, source = runner.hot_line(beef)
        self.assertEqual(line['menu_item_id'], 41)
        self.assertEqual(source['eligibility'], 'existing_active_wok_sku')
        wok['is_active'] = False
        line, source = runner.hot_line(beef)
        self.assertEqual(line['menu_item_id'], 40)
        self.assertEqual(source['eligibility'], 'existing_paid_fried_egg_option')
        self.assertEqual(line['options'][-1]['option_code_snapshot'], 'fried_egg')
        self.assertEqual(str(a.line_amount(line)), '37.96')
        egg['is_active'] = False
        with self.assertRaisesRegex(a.NoGo, 'paid_egg_missing_fried_egg'):
            runner.hot_line(beef)


if __name__ == '__main__':
    unittest.main()
