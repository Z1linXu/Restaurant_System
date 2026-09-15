"""Safety regression for bounded API acceptance. No Docker, SSH or shared runtime."""
import contextlib
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('acceptance', Path(__file__).with_name('menu-simplification-acceptance.py'))
acceptance = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(acceptance)


class SafetyTests(unittest.TestCase):
    def test_mock_job_uses_existing_store_mode_contract_without_hardware(self):
        job = {'store_id': 24, 'order_id': 64, 'module_code': 'GRAB', 'status': 'PRINTED',
               'execution_mode': None, 'printer_id': None, 'printer_endpoint': None,
               'printed_by_device_id': None, 'rendered_text_snapshot': '+TESTADD +TESTCOMBO'}
        printing = {'printing_mode': 'MOCK', 'printers': []}
        for mode in (None, 'MOCK'):
            acceptance.verify_mock_job({**job, 'execution_mode': mode}, printing, 24, 64)
        for changed in ({'execution_mode': 'REAL'}, {'execution_mode': 'PAD_DIRECT'},
                        {'store_id': 25}, {'order_id': 65}, {'printer_id': 1},
                        {'printer_endpoint': '192.0.2.1'}, {'printed_by_device_id': 1},
                        {'status': 'FAILED'}, {'module_code': 'FRONTDESK_RECEIPT'},
                        {'rendered_text_snapshot': '+TESTADD'}, {'rendered_text_snapshot': '+TESTCOMBO'}):
            with self.subTest(changed=changed), self.assertRaises(acceptance.NoGo):
                acceptance.verify_mock_job({**job, **changed}, printing, 24, 64)
        for changed in ({'printing_mode': 'REAL'}, {'printing_mode': 'DISABLED'},
                        {'printers': [{'ip_address': '192.0.2.1'}]}):
            with self.subTest(changed=changed), self.assertRaises(acceptance.NoGo):
                acceptance.verify_mock_job(job, {**printing, **changed}, 24, 64)

    def arguments(self):
        return ['--execute', '--approved-sha', 'a' * 40, '--preflight-evidence', '/safe/preflight',
                '--preflight-evidence-sha256', 'b' * 64, '--organization-id', '1',
                '--store-a-id', '21', '--store-a-code', 'STG005_MENU_TEST_A',
                '--store-b-id', '22', '--store-b-code', 'STG005_MENU_TEST_B',
                '--run-id', 'test01', '--secrets-fd', '3', '--report', '/safe/report']

    def test_execute_gate_precedes_any_io_or_api(self):
        with patch.object(acceptance, 'validate_runtime') as runtime, patch.object(acceptance.Api, 'call') as api:
            with contextlib.redirect_stdout(io.StringIO()) as output:
                result = acceptance.main(self.arguments()[1:])
            self.assertEqual(result, 1)
            runtime.assert_not_called()
            api.assert_not_called()
            self.assertIn('explicit_execute_required', output.getvalue())

    def test_real_or_duplicate_store_rejected(self):
        for key, value in (('--store-a-code', 'CHINATOWN'), ('--store-b-id', '21'),
                           ('--store-b-code', 'STG005_MENU_TEST_A'), ('--run-id', '../evil')):
            args = self.arguments()
            args[args.index(key) + 1] = value
            with self.subTest(key=key), self.assertRaises(acceptance.NoGo):
                acceptance.parse_args(args)

    def test_no_configurable_remote_endpoint(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
            acceptance.parse_args(self.arguments() + ['--base-url', 'http://production'])
        self.assertEqual(acceptance.API_BASE, 'http://127.0.0.1:18080/api/v1')

    def test_runtime_rejects_unsafe_actual_policy_after_helper_passes(self):
        args = acceptance.parse_args(self.arguments())
        safe_policy = {'APP_PHASE_B_RUNTIME': 'staging', 'APP_PRINTING_ALLOWED_MODES': 'DISABLED,MOCK',
                       'APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED': 'false', 'APP_FEATURES_PRINTING': 'true'}
        for changed in ({'APP_PRINTING_ALLOWED_MODES': 'DISABLED,MOCK,REAL'},
                        {'APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED': 'true'},
                        {'APP_PHASE_B_RUNTIME': 'production'}, {}):
            policy = {**safe_policy, **changed}
            row = {'Config': {'Labels': {'com.docker.compose.project': 'restaurant-pos-staging'},
                              'Env': [key + '=' + value for key, value in policy.items()]},
                   'State': {'Running': True}, 'Image': 'sha256:' + 'c' * 64, 'Id': 'd' * 64}
            source = str(acceptance.ROOT / 'releases' / args.approved_sha / 'scripts/menu-simplification-acceptance.py')
            with self.subTest(changed=changed), patch.object(acceptance, '__file__', source), \
                    patch.object(acceptance, 'private_bytes', return_value=b'PRIVATE_ENV_SECRET'), \
                    patch.object(acceptance, 'command', side_effect=[b'PASS', json.dumps([row]).encode()]):
                if changed:
                    with self.assertRaises(acceptance.NoGo):
                        acceptance.validate_runtime(args)
                else:
                    result = acceptance.validate_runtime(args)
                    self.assertTrue(result['printing_enabled'])
                    self.assertNotIn('PRIVATE_ENV_SECRET', json.dumps(result))

    def test_private_file_permissions_and_symlink(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root).resolve() / 'private.json'
            path.write_text('{"login_password":"DO_NOT_PRINT_SECRET"}')
            path.chmod(0o600)
            self.assertIn(b'DO_NOT_PRINT_SECRET', acceptance.private_bytes(path))
            path.chmod(0o640)
            with self.assertRaises(acceptance.NoGo):
                acceptance.private_bytes(path)
            path.chmod(0o600)
            link = path.with_name('link')
            link.symlink_to(path)
            with self.assertRaises(acceptance.NoGo):
                acceptance.private_bytes(link)

    def test_inherited_private_fd(self):
        with tempfile.TemporaryDirectory() as root:
            path = Path(root) / 'private.json'
            path.write_text('{"owner_login_identifier":"owner","owner_login_password":"never-print-this"}')
            path.chmod(0o600)
            with path.open('rb') as file:
                args = acceptance.parse_args(self.arguments())
                args.secrets_fd = file.fileno()
                result = acceptance.secret_input(args)
                self.assertEqual(result['owner_login_identifier'], 'owner')
            args.secrets_fd = 0
            with self.assertRaises(acceptance.NoGo):
                acceptance.secret_input(args)

    def test_mutation_transport_failure_has_no_body_or_secret(self):
        api = acceptance.Api()
        api.token = 'TOP_SECRET_TOKEN'
        with patch.object(api.opener, 'open', side_effect=OSError('TOP_SECRET_TOKEN password')):
            with self.assertRaises(acceptance.NoGo) as error:
                api.call('/admin/menu/addons', 'POST', {'name_zh': 'PRIVATE_PAYLOAD'})
        self.assertNotIn('TOP_SECRET', str(error.exception))
        self.assertNotIn('PRIVATE_PAYLOAD', str(error.exception))
        self.assertIn('no_automatic_mutation_retry', str(error.exception))

    def test_unsafe_api_path_rejected_before_transport(self):
        api = acceptance.Api()
        with patch.object(api.opener, 'open') as send:
            for path in ('//evil.test/auth', '/../../api', '/auth#x', 'https://other/auth'):
                with self.subTest(path=path), self.assertRaises(acceptance.NoGo):
                    api.call(path)
            send.assert_not_called()

    def test_live_kitchen_changes_do_not_hide_snapshot_mutation(self):
        before = {'subtotal_amount': 10, 'discount_amount': 0, 'total_amount': 10,
                  'items': [{'id': 1, 'task_status': 'pending', 'item_name_snapshot_zh': 'original',
                             'options': [{'option_code_snapshot': 'test', 'price_delta': 1}]}]}
        after = copy.deepcopy(before)
        after['items'][0]['task_status'] = 'ready'
        self.assertEqual(acceptance.frozen_order(before), acceptance.frozen_order(after))
        after['items'][0]['options'][0]['price_delta'] = 9
        self.assertNotEqual(acceptance.frozen_order(before), acceptance.frozen_order(after))

    def test_creation_evidence_requires_current_sha_and_approved_store(self):
        args = acceptance.parse_args(self.arguments())
        args.creation_evidence = '/private/evidence'
        evidence = {'schema': 'V26_BUSINESS_STORE_CREATE_ACCEPTANCE_V1', 'source_sha': 'a' * 40,
                    'environment_sha256': 'env', 'backend_image_id': 'image', 'store_id': 21, 'organization_id': 1,
                    'fresh_create': 'PASS', 'replay': 'PASS', 'final_result': 'PASS'}
        for changed in ({'source_sha': 'c' * 40}, {'store_id': 999}, {'organization_id': 99}):
            with self.subTest(changed=changed), patch.object(acceptance, 'private_bytes', return_value=json.dumps({**evidence, **changed}).encode()):
                with self.assertRaises(acceptance.NoGo):
                    acceptance.creation_evidence(args, {'checks': []}, {'environment_sha256': 'env', 'backend_image_id': 'image'})

    def test_missing_creation_evidence_is_not_pass(self):
        args = acceptance.parse_args(self.arguments())
        report = {'checks': []}
        acceptance.creation_evidence(args, report, {})
        self.assertEqual(report['checks'][0]['status'], 'NOT_RUN')


class FixtureTests(unittest.TestCase):
    arguments = SafetyTests.arguments

    def create_arguments(self):
        arguments = self.arguments()
        for key in ('--store-a-id', '--store-a-code', '--store-b-id', '--store-b-code'):
            offset = arguments.index(key)
            del arguments[offset:offset + 2]
        return arguments + ['--create-fixtures']

    def test_create_mode_cannot_override_store_identities(self):
        args = acceptance.parse_args(self.create_arguments())
        self.assertTrue(args.create_fixtures)
        self.assertIsNone(args.store_a_id)
        for extra in (['--store-a-id', '999'], ['--store-a-code', 'REAL_STORE'],
                      ['--creation-evidence', '/private/old-proof']):
            with self.subTest(extra=extra), self.assertRaises(acceptance.NoGo):
                acceptance.parse_args(self.create_arguments() + extra)

    def test_runtime_failure_precedes_login_and_fixture_creation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            (root / 'evidence').mkdir()
            args = self.create_arguments()
            args[args.index('--report') + 1] = str(root / 'evidence/result.json')
            lock_fd = os.open(root / 'lock', os.O_CREAT | os.O_RDWR, 0o600)
            with patch.object(acceptance, 'ROOT', root), patch.object(acceptance, 'action_lock', return_value=lock_fd), \
                    patch.object(acceptance, 'validate_runtime', side_effect=acceptance.NoGo('unsafe_runtime')), \
                    patch.object(acceptance.Api, 'login') as login, \
                    patch.object(acceptance, 'create_store_fixtures') as create, \
                    contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(acceptance.main(args), 1)
                login.assert_not_called()
                create.assert_not_called()

    def provision_api(self, existing=None):
        from unittest.mock import Mock
        calls, stores = [], {}
        templates = {'catalog': {'enabled': True, 'profile_code': 'PROFILE', 'profile_version': 'v2',
                      'master_menu_key': 'MASTER', 'master_menu_version': 'v1',
                      'master_menu_fingerprint_sha256': 'b' * 64},
                     'profile': {'profile_code': 'PROFILE', 'profile_version': 'v2', 'valid': True,
                      'fingerprint_sha256': 'c' * 64, 'content': {}, 'artifacts': []}}
        conflicts = [{'code': 'legacy_egg', 'names_zh': ['PRIVATE_NAME'], 'reason': 'CONFLICT'}]
        counts = {key: 1 for key in ('station_count', 'category_count', 'item_count', 'option_count',
                                    'pricing_policy_count', 'combo_component_count', 'printing_rule_count')}

        def call(path, method='GET', body=None, **kwargs):
            calls.append((path, method, copy.deepcopy(body), kwargs))
            if path == '/owner/overview':
                return {'organizations': [{'id': 1, 'role_code': 'OWNER', 'can_create_store': True,
                                          'status': 'active', 'stores': existing or []}]}
            if path.endswith('/create-catalog'):
                return copy.deepcopy(templates['catalog'])
            if path.startswith('/store-profiles/'):
                return copy.deepcopy(templates['profile'])
            if method == 'POST' and path == '/owner/organizations/1/stores':
                code = body['store_code']
                replayed = code in stores
                if not replayed:
                    stores[code] = {'id': 21 + len(stores), 'body': copy.deepcopy(body)}
                return {'store_id': stores[code]['id'], 'request_id': stores[code]['id'] + 100,
                        'store_code': code, 'store_name': body['store_name'], 'replayed': replayed,
                        'store_kind': 'BUSINESS', 'store_status': 'active', 'lifecycle_status': 'ACTIVE',
                        'operational_state': 'LIVE', 'is_live': True, 'validation_status': 'PASS',
                        'counts': counts, 'addon_conflicts': conflicts}
            if path.startswith('/stores/'):
                return {'id': int(path.split('/')[2]), 'is_live': True, 'operational_state': 'LIVE'}
            if path.startswith('/admin/menu/management-context'):
                store_id = int(path.split('=')[1])
                entry = next(x for x in stores.values() if x['id'] == store_id)
                return {'stores': [{'id': store_id, 'code': entry['body']['store_code'],
                                    'name': entry['body']['store_name'], 'organization_id': 1,
                                    'provisioned_profile_fingerprint_sha256': 'c' * 64,
                                    'provisioned_master_menu_fingerprint_sha256': 'b' * 64}]}
            if path.startswith('/admin/menu/addons'):
                return {'addons': [{'id': 30}], 'conflicts': conflicts}
            raise AssertionError(path)

        return Mock(call=call), templates, calls

    def test_two_fixed_stores_use_identical_body_and_key_for_replay(self):
        args = acceptance.parse_args(self.create_arguments())
        api, templates, calls = self.provision_api()
        report = {'checks': []}
        acceptance.create_store_fixtures(api, args, report, templates)
        writes = [x for x in calls if x[1] == 'POST']
        self.assertEqual(len(writes), 4)
        for offset, side in ((0, 'A'), (2, 'B')):
            self.assertEqual(writes[offset], writes[offset + 1])
            self.assertEqual(writes[offset][2]['store_code'], 'STG005_MENU_TEST01_' + side)
            self.assertEqual(writes[offset][2]['store_name'], 'STG005 Menu Simplification TEST01 ' + side)
            self.assertEqual(writes[offset][2]['profile_fingerprint_sha256'], 'c' * 64)
        self.assertNotEqual(writes[0][3]['key'], writes[2][3]['key'])
        self.assertEqual(report['store_ids'], [21, 22])
        self.assertTrue(all(x['status'] == 'PASS' for x in report['checks']))
        self.assertNotIn('PRIVATE_NAME', json.dumps(report))

    def test_existing_store_name_drift_fails_before_any_post(self):
        args = acceptance.parse_args(self.create_arguments())
        api, templates, calls = self.provision_api([{'id': 22, 'code': 'STG005_MENU_TEST01_B', 'name': 'Changed'}])
        with self.assertRaises(acceptance.NoGo):
            acceptance.create_store_fixtures(api, args, {'checks': []}, templates)
        self.assertFalse(any(x[1] == 'POST' for x in calls))

    def test_profile_drift_not_accepted_as_immutable(self):
        from unittest.mock import Mock
        args = acceptance.parse_args(self.create_arguments())
        api = Mock()
        before = {'profile': 'before', 'catalog': 'same'}
        with patch.object(acceptance, 'template_snapshot', return_value={'profile': 'after', 'catalog': 'same'}):
            with self.assertRaises(acceptance.NoGo):
                acceptance.assert_templates_unchanged(api, args, before, {'checks': []}, 'after_create')

    def test_existing_store_identity_checked_before_new_staff(self):
        from unittest.mock import Mock
        args = acceptance.parse_args(self.arguments() + ['--create-isolation-fixtures'])
        runner = acceptance.Acceptance(args, Mock(), {'checks': []})
        with patch.object(runner, 'fixture', side_effect=[{}, acceptance.NoGo('store_code_drift')]), \
                patch.object(acceptance, 'create_isolation_fixture') as create:
            with self.assertRaises(acceptance.NoGo):
                runner.run({}, {})
            create.assert_not_called()

    def test_isolation_secret_private_and_collision_never_resets_password(self):
        from unittest.mock import Mock
        args = acceptance.parse_args(self.arguments() + ['--create-isolation-fixtures'])
        api = Mock()
        api.call.side_effect = [[{'code': 'OWNER'}], [],
                                {'id': 81, 'store_id': 22, 'username': 'STG005_MENU_TEST01_OWNER',
                                 'role_code': 'OWNER', 'status': 'active'}]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary).resolve()
            (root / 'state').mkdir(mode=0o700)
            report, credentials = {'checks': []}, {}
            with patch.object(acceptance, 'ROOT', root), patch.object(acceptance.secrets, 'token_urlsafe', return_value='NEW_PRIVATE_PASSWORD'):
                acceptance.create_isolation_fixture(api, args, credentials, report)
            path = Path(report['isolation_credentials_file'])
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertIn('NEW_PRIVATE_PASSWORD', path.read_text())
            self.assertNotIn('NEW_PRIVATE_PASSWORD', json.dumps(report))
            self.assertEqual(credentials['wrong_store_owner']['control_store_id'], 22)
        writes = [call for call in api.call.call_args_list if len(call.args) > 1 and call.args[1] != 'GET']
        self.assertEqual(len(writes), 1)
        self.assertEqual(writes[0].args[:2], ('/admin/staff', 'POST'))
        api.reset_mock()
        api.call.side_effect = [[{'code': 'OWNER'}], [{'username': 'STG005_MENU_TEST01_OWNER'}]]
        with self.assertRaises(acceptance.NoGo):
            acceptance.create_isolation_fixture(api, args, {}, {'checks': []})
        self.assertEqual(api.call.call_count, 2)


class HttpBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.received = []
        received = self.received

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                received.append(self.path)
                if self.path.endswith('/redirect'):
                    self.send_response(302)
                    self.send_header('Location', '/secret-target')
                    self.end_headers()
                else:
                    self.send_response(400)
                    self.end_headers()
                    self.wfile.write(json.dumps({'success': False, 'message': 'SECRET_ERROR_BODY'}).encode())

            def log_message(self, *_):
                pass

        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f'http://127.0.0.1:{self.server.server_port}/api/v1'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def test_redirect_not_followed_even_with_proxy_environment(self):
        with patch.object(acceptance, 'API_BASE', self.base), patch.dict(os.environ, {'http_proxy': 'http://127.0.0.1:1', 'no_proxy': ''}):
            with self.assertRaises(acceptance.NoGo) as error:
                acceptance.Api().call('/redirect')
        self.assertEqual(self.received, ['/api/v1/redirect'])
        self.assertIn('http_status_302', str(error.exception))

    def test_expected_error_must_match_business_contract(self):
        with patch.object(acceptance, 'API_BASE', self.base):
            with self.assertRaises(acceptance.NoGo) as error:
                acceptance.Api().call('/negative', expected=(400,), error_code='ADDON_CODE_IMMUTABLE')
        self.assertEqual(str(error.exception), 'negative_error_contract_mismatch')
        self.assertNotIn('SECRET_ERROR_BODY', str(error.exception))


if __name__ == '__main__':
    unittest.main()
