"""Pure fake checks: never invoke HTTP, Docker, login or a server filesystem."""
import importlib.util
import copy
import json
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

    def test_helper_release_sha_is_separate_from_deployed_app_sha(self):
        app_sha, script_sha = 'a' * 40, 'b' * 40
        args = ['--sha', app_sha, '--run-id', 'run20261021', '--report', '/unused']
        self.assertEqual(a.parse_args(args).script_sha, app_sha)
        self.assertEqual(a.parse_args(args + ['--script-sha', script_sha]).script_sha, script_sha)
        release = a.ROOT / 'releases' / script_sha
        labels = {'com.docker.compose.project': 'restaurant-pos-staging', 'org.opencontainers.image.revision': app_sha}
        env = ['APP_ENVIRONMENT=staging', 'UBER_EATS_ENVIRONMENT=sandbox', 'DB_NAME=restaurant_pos_staging', 'APP_PRINTING_ALLOWED_MODES=MOCK,PAD_DIRECT']
        rows = [{'Name': '/restaurant-pos-staging-' + role + '-1', 'State': {'Running': True}, 'Image': role,
                 'Config': {'Labels': labels, 'Env': env}} for role in ('backend', 'nginx')]
        evidence = {'source_sha': app_sha, 'result': 'PASS', 'flyway': '38', 'production_unchanged': True}
        with patch.object(a.Path, 'resolve', return_value=release / 'deployment/cloud/pos-five-fixes/acceptance.py'), \
                patch.object(a.h, 'command', side_effect=[script_sha.encode(), b'', json.dumps(rows).encode()]) as command, \
                patch.object(a.h, 'private_bytes', return_value=json.dumps(evidence).encode()) as private:
            result = a.runtime(app_sha, script_sha)
        self.assertEqual(result['app_sha'], app_sha)
        self.assertEqual(result['script_sha'], script_sha)
        self.assertEqual(command.call_args_list[0].args[0], ['git', '-C', str(release), 'rev-parse', 'HEAD'])
        self.assertEqual(private.call_args.args[0], a.ROOT / ('pos-five-release-' + app_sha) / 'result.json')

    def test_existing_pad_endpoints_are_preserved_and_only_mode_is_changed(self):
        api = a.StagingApi('run20261021')
        store = {'id': 18, 'code': 'CHINATOWN', 'organization_id': 5, 'store_kind': 'VALIDATION_FIXTURE',
                 'lifecycle_status': 'ACTIVE', 'provisioning_source': 'PHASE_B_OWNER_PROVISIONING'}
        printing = {'printing_mode': 'PAD_DIRECT', 'printers': [{'id': 9, 'ip_address': '192.0.2.9', 'port': 9100}],
                    'assignments': [{'module_code': module, 'printer_id': 9, 'enabled': True}
                                    for module in ('GRAB', 'FRONTDESK_RECEIPT', 'HOT_KITCHEN')]}
        original = copy.deepcopy(printing)
        def call(path, method='GET', body=None, **kwargs):
            if path == '/me/workspaces': return {'stores': [store]}
            if path == '/owner/overview': return {'organizations': [{'stores': [store]}]}
            if path == '/admin/printing?store_id=18': return copy.deepcopy(printing)
            if path == '/menu/catalog?store_id=18': return {'store_id': 18, 'organization_id': 5}
            if path == '/admin/printing/status' and method == 'PUT':
                api.check_write(path, method, body)
                printing['printing_mode'] = body['printing_mode']
                return copy.deepcopy(printing)
            self.fail('Unexpected fake API path')
        api.call = Mock(side_effect=call)
        runner = a.Acceptance(api, 'run20261021', {'checks': []}, lambda: None)
        runner.control_snapshots = Mock(return_value={})
        runner.publish_no_spicy = Mock()
        runner.prepare()
        runner.assert_mock()
        self.assertEqual(printing, dict(original, printing_mode='MOCK'))
        writes = [entry for entry in api.call.call_args_list if len(entry.args) > 1 and entry.args[1] != 'GET']
        self.assertEqual(len(writes), 1)
        self.assertEqual(writes[0].args, ('/admin/printing/status', 'PUT', {'store_id': 18, 'printing_mode': 'MOCK'}))
        printing['printing_mode'] = 'PAD_DIRECT'
        with self.assertRaisesRegex(a.NoGo, 'mock_boundary_changed'):
            runner.assert_mock()

    def test_mock_jobs_still_reject_physical_execution_identity(self):
        api = a.StagingApi('run20261021')
        runner = a.Acceptance(api, 'run20261021', {'checks': []}, lambda: None)
        job = {'id': 50, 'store_id': 18, 'order_id': 45, 'execution_mode': None}
        runner.assert_mock_job(job, 45)
        runner.assert_mock_job(dict(job, execution_mode='MOCK'), 45)
        self.assertIsNone(runner.report['mock_jobs']['50']['printer_id'])
        self.assertEqual(runner.report['mock_jobs']['50']['effective_execution_mode'], 'MOCK')
        for field, value in [('printer_id', 9), ('printed_by_device_id', 9), ('claimed_by_device_id', 9),
                             ('printer_endpoint', '192.0.2.9:9100'), ('execution_mode', 'PAD_DIRECT')]:
            with self.subTest(field=field), self.assertRaisesRegex(a.NoGo, 'mock_job_boundary'):
                runner.assert_mock_job(dict(job, **{field: value}), 45)

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
        api.call = Mock(side_effect=lambda path, **kw: detail if path == '/orders/45' else
                        {'printing_mode': 'MOCK', 'printers': [{'ip_address': '192.0.2.9'}]} if path == '/admin/printing?store_id=18' else [job])
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
