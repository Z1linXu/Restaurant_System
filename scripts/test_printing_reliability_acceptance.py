"""Non-network proof of the bounded acceptance contract; no runtime credentials."""
import hashlib
import hmac
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('printing_acceptance', Path(__file__).with_name('printing-reliability-acceptance.py'))
a = importlib.util.module_from_spec(spec)
spec.loader.exec_module(a)


class Response:
    code = 200
    def __enter__(self): return self
    def __exit__(self, *args): pass
    def read(self, maximum): return b'{"success":true,"data":{"id":1}}'


class AcceptanceSafetyTest(unittest.TestCase):
    def test_menu_comparison_ignores_only_response_clock(self):
        original = {'generated_at': 'old', 'store_id': 26, 'menu_revision': 1,
                    'content_hash': 'hash', 'categories': [{'price': 2, 'name': 'unchanged'}]}
        self.assertEqual(a.menu_business_snapshot(original), a.menu_business_snapshot({**original, 'generated_at': 'new'}))
        for key, value in [('store_id', 27), ('menu_revision', 2), ('content_hash', 'changed'),
                           ('categories', [{'price': 3, 'name': 'unchanged'}])]:
            self.assertNotEqual(a.menu_business_snapshot(original), a.menu_business_snapshot({**original, key: value}))

    def test_request_proof_is_bound_to_exact_body_path_and_bearer_without_device_token_header(self):
        api = a.ProofApi()
        api.device = {'device_id': 10, 'device_token': 'synthetic-token'}
        api.token = 'synthetic'
        with patch.object(api.opener, 'open', return_value=Response()) as opened, patch.object(a.time, 'time', return_value=1800000000):
            result = api.call('/orders/9/reprint', 'POST', {})
        self.assertEqual(result, {'id': 1})
        request = opened.call_args.args[0]
        self.assertEqual(request.full_url, 'http://127.0.0.1:18080/api/v1/orders/9/reprint')
        self.assertEqual(request.data, b'{}')
        headers = dict(request.header_items())
        self.assertEqual(headers['X-print-signature'], '21898df597d27ff1f9f7d192567b5dce2e2aabb3c030c1c788531d0817be98e7')
        self.assertNotIn('synthetic-token', json.dumps(headers))
        self.assertNotIn('X-device-token', headers)

    def test_runtime_targets_are_fixed_and_synthetic(self):
        self.assertEqual(a.STORE, 26)
        self.assertEqual(a.CONTROL, 27)
        self.assertEqual(a.m.API_BASE, 'http://127.0.0.1:18080/api/v1')

    def test_lost_registration_response_cleanup_uses_new_exact_run_devices_only(self):
        name = 'SYNTHETIC_PRINT_test01_A'
        rows = [{'id': 1, 'store_id': 26, 'device_name': name},
                {'id': 2, 'store_id': 26, 'device_name': name},
                {'id': 3, 'store_id': 27, 'device_name': name},
                {'id': 4, 'store_id': 26, 'device_name': 'real-owner-pad'}]
        self.assertEqual([2], [d['id'] for d in a.cleanup_candidates(rows, {1}, {name})])

    def test_two_pad_fixture_and_cross_pad_reprint_are_explicit(self):
        source = Path(a.__file__).read_text()
        self.assertIn("'PRINT-' + args.run_id + '-' + str(index)", source)
        self.assertIn("order_id not in report['order_ids']", source)
        self.assertIn("jobs_by_device[devices[0]['device_id']]", source)
        self.assertIn('api.device = devices[1]', source)


if __name__ == '__main__': unittest.main()
