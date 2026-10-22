"""No-network tests for the receive-only acceptance boundary and safe evidence."""
import contextlib
import copy
import hashlib
import hmac
import importlib.util
import io
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import Mock, patch
import urllib.parse


spec = importlib.util.spec_from_file_location(
    'uber_receive_only_verification',
    Path(__file__).resolve().parents[1] / 'verify-uber-receive-only-20261022.py')
verification = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verification)

IMAGE = 'sha256:' + 'a' * 64
SHA = 'b' * 40
STORE_UUID = '7084e71d-ab9b-4a2c-821c-c009754ac24d'
ORDER_UUID = 'a6e485e0-3567-4fe3-99f6-6c1b1191980c'
POS_TOKEN = 'fixture-private-pos-token'
TEST_TOKEN = 'fixture-private-testing-token'
JWT_SECRET = 'fixture-private-jwt-key'
SIGNING_KEY = 'fixture-private-signing-key'
OAUTH_SECRET = 'fixture-private-oauth-secret'
PRIVATE_VALUES = (POS_TOKEN, TEST_TOKEN, JWT_SECRET, SIGNING_KEY, OAUTH_SECRET)


class FixtureError(RuntimeError):
    pass


class AcceptanceFixture:
    """Fake all privileged/network dependencies; retain requests only in memory."""

    def __init__(self, environment):
        self.environment = environment
        self.base = 'https://' + ('pos' if environment == 'production' else 'staging-pos') + '.lanzhounoodlesmtl.com'
        self.env = {
            'UBER_EATS_ENABLED': 'false' if environment == 'production' else 'true',
            'UBER_EATS_WORKER_ENABLED': 'false',
            'UBER_EATS_PROCESSING_MODE': 'KITCHEN_MIRROR',
            'UBER_EATS_ENVIRONMENT': 'production' if environment == 'production' else 'sandbox',
            'JWT_SECRET': JWT_SECRET,
        }
        if environment == 'staging':
            self.env.update({
                'UBER_EATS_CLIENT_ID': 'fixture-testing-client',
                'UBER_EATS_CLIENT_SECRET': OAUTH_SECRET,
                'UBER_EATS_WEBHOOK_SIGNING_KEY': SIGNING_KEY,
                'UBER_EATS_SCOPES': 'eats.store eats.store.orders.read',
            })
        self.backend = {'Image': IMAGE, 'Env': self.env}
        self.image = {'Config': {'Labels': {'org.opencontainers.image.revision': SHA}}}
        self.counts = {'uber_orders': 0, 'local_uber_orders': 0,
                       'uber_print_jobs': 0, 'uber_bindings': 0}
        self.ops = SimpleNamespace(
            BASE=Path('/fixture/private/releases'), PromotionError=FixtureError,
            operation_locks=Mock(side_effect=contextlib.nullcontext),
            inspect=Mock(side_effect=self.inspect), env_of=lambda backend: backend['Env'],
            require=self.require, health=Mock(), queue_guard=Mock(),
            inventory=Mock(side_effect=lambda _db: copy.deepcopy(self.counts)),
            write_private=Mock(), run=Mock(side_effect=AssertionError('NO_DOCKER_ALLOWED')))
        self.smoke = SimpleNamespace(
            numeric_identity=Mock(return_value=(7, 3, 'OWNER', 11, 5)),
            mint_token=Mock(return_value=POS_TOKEN))
        self.requests = []
        self.request = Mock(side_effect=self.respond)
        self.psql = Mock(side_effect=self.read_fixture_sql)

    @staticmethod
    def require(condition, code):
        if not condition:
            raise FixtureError(code)

    def inspect(self, name):
        if name == IMAGE:
            return self.image
        expected = 'cloud-backend-1' if self.environment == 'production' else 'restaurant-pos-staging-backend-1'
        if name != expected:
            raise AssertionError('Unexpected Docker inspection target')
        return self.backend

    @staticmethod
    def read_fixture_sql(db, sql):
        if not sql.startswith('SELECT '):
            raise AssertionError('Acceptance SQL must be read-only')
        if 'uber_eats_store_mappings' in sql:
            return {'id': STORE_UUID}
        if 'uber_eats_orders' in sql:
            return {'id': ORDER_UUID, 'store': 11}
        raise AssertionError('Unexpected fixture SQL')

    def respond(self, url, *, raw=None, headers=None):
        headers = headers or {}
        self.requests.append((url, raw, headers))
        parsed = urllib.parse.urlparse(url)
        if parsed.netloc == 'sandbox-login.uber.com':
            assert self.environment == 'staging'
            assert parsed.path == '/oauth/v2/token' and raw is not None
            form = urllib.parse.parse_qs(raw.decode())
            assert form['client_secret'] == [OAUTH_SECRET]
            assert form['grant_type'] == ['client_credentials']
            return 200, json.dumps({'access_token': TEST_TOKEN, 'scope': 'eats.store eats.store.orders.read'}).encode()
        if parsed.netloc == 'test-api.uber.com':
            assert self.environment == 'staging'
            assert parsed.path == '/v2/eats/order/' + ORDER_UUID and raw is None
            assert headers == {'Authorization': 'Bearer ' + TEST_TOKEN}
            return 200, json.dumps({'id': ORDER_UUID, 'store': {'id': STORE_UUID}}).encode()
        if not url.startswith(self.base + '/'):
            raise AssertionError('Unexpected acceptance host')
        if parsed.path == '/api/v1/integrations/uber-eats/webhook':
            if raw is None:
                return 405, b''
            if self.environment == 'production':
                assert raw == b'{}'
                return 503, b''
            expected = hmac.new(SIGNING_KEY.encode(), raw, hashlib.sha256).hexdigest()
            if not hmac.compare_digest(headers.get('X-Uber-Signature', ''), expected):
                return 401, b'{"message":"invalid signature"}'
            if headers.get('X-Environment') != 'sandbox':
                return 400, b'{"message":"environment mismatch"}'
            return 200, b''
        if parsed.path == '/api/v1/stores/9223372036854775807/context':
            assert raw is None
            assert headers == {'Authorization': 'Bearer ' + POS_TOKEN}
            return 403, b'{"success":false}'
        if parsed.path == '/api/v1/stores/11/integrations/uber-eats/mapping-preview':
            assert raw is not None
            assert json.loads(raw)['id'] == ORDER_UUID
            assert headers['Authorization'] == 'Bearer ' + POS_TOKEN
            return 200, b'{"data":{"errors":[],"warnings":[]}}'
        if parsed.path.startswith('/api/'):
            assert raw is None, 'No business or printing mutation is permitted'
            assert headers == {'Authorization': 'Bearer ' + POS_TOKEN}
            return 200, b'{"success":true,"data":[]}'
        assert raw is None
        return 200, b'<!doctype html><html><body>Fixture</body></html>'

    @contextlib.contextmanager
    def patched(self):
        with patch.object(verification, 'ops', self.ops), \
                patch.object(verification, 'smoke', self.smoke), \
                patch.object(verification, 'request', self.request), \
                patch.object(verification, 'psql', self.psql):
            yield self


class UberReceiveOnlyVerificationTest(unittest.TestCase):
    def assert_no_private_values(self, value):
        encoded = json.dumps(value, default=str)
        for private in PRIVATE_VALUES:
            self.assertNotIn(private, encoded)

    def test_disabled_production_uses_only_pos_reads_and_nonbusiness_503_post(self):
        fixture = AcceptanceFixture('production')
        with fixture.patched():
            result = verification.verify('production', IMAGE, SHA)
        self.assertTrue(result['accepted'])
        self.assertFalse(result['worker_enabled'])
        self.assertEqual(result['get_http_status'], 405)
        self.assertEqual(result['disabled_post_http_status'], 503)
        self.assertEqual(result['production_hmac'], 'EXTERNALLY_BLOCKED_SIGNING_KEY_NOT_CONFIGURED')
        posts = [(url, raw) for url, raw, _headers in fixture.requests if raw is not None]
        self.assertEqual(posts, [(fixture.base + '/api/v1/integrations/uber-eats/webhook', b'{}')])
        self.assertTrue(all(urllib.parse.urlparse(url).netloc == 'pos.lanzhounoodlesmtl.com'
                            for url, _raw, _headers in fixture.requests))
        fixture.psql.assert_not_called()
        fixture.ops.run.assert_not_called()
        fixture.ops.queue_guard.assert_called_once_with(fixture.counts, True)
        self.assertEqual(result['business_order_delta'], 0)
        self.assertEqual(result['uber_print_job_delta'], 0)
        self.assert_no_private_values(result)

    def test_staging_signs_same_raw_receipt_checks_hmac_environment_ack_and_duplicate(self):
        fixture = AcceptanceFixture('staging')
        with fixture.patched():
            result = verification.verify('staging', IMAGE, SHA)
        self.assertEqual([result[key] for key in ('invalid_http_status', 'environment_mismatch_http_status',
                                                  'valid_http_status', 'duplicate_http_status')], [401, 400, 200, 200])
        posts = [(raw, headers) for url, raw, headers in fixture.requests
                 if url == fixture.base + '/api/v1/integrations/uber-eats/webhook' and raw is not None]
        self.assertEqual(len(posts), 4)
        self.assertTrue(all(raw == posts[0][0] for raw, _headers in posts))
        body = json.loads(posts[0][0])
        self.assertEqual(body['event_id'], result['event_id'])
        self.assertEqual(body['event_type'], 'orders.notification')
        self.assertEqual(body['meta']['user_id'], STORE_UUID)
        self.assertNotEqual(body['meta']['resource_id'], ORDER_UUID)
        signature = hmac.new(SIGNING_KEY.encode(), posts[0][0], hashlib.sha256).hexdigest()
        self.assertEqual(posts[0][1]['X-Uber-Signature'], '0' * 64)
        self.assertEqual(posts[1][1]['X-Environment'], 'foreign')
        self.assertTrue(all(headers['X-Uber-Signature'] == signature for _raw, headers in posts[1:]))
        self.assertEqual(result['testing_oauth'], 'PASS')
        self.assertEqual(result['testing_get_order'], 'PASS')
        self.assertEqual(result['mapping_preview'], 'PASS')
        self.assertEqual(result['mapping_preview_errors'], 0)
        fixture.ops.queue_guard.assert_called_once_with(fixture.counts, False)
        fixture.ops.run.assert_not_called()
        for url, raw, _headers in fixture.requests:
            path = urllib.parse.urlparse(url).path
            if raw is not None:
                self.assertIn(path, ('/api/v1/integrations/uber-eats/webhook', '/oauth/v2/token',
                                     '/api/v1/stores/11/integrations/uber-eats/mapping-preview'))
        proof_path, proof = fixture.ops.write_private.call_args.args
        self.assertEqual(proof_path, fixture.ops.BASE / 'staging' / ('uber-receive-only-' + SHA) / 'http-proof.json')
        self.assertEqual(proof, result)
        self.assertEqual(proof['business_order_delta'], 0)
        self.assertEqual(proof['uber_print_job_delta'], 0)
        self.assert_no_private_values(proof)

    def test_runtime_image_worker_mode_environment_and_source_drift_fail_closed(self):
        cases = (
            ('image', 'RUNTIME_DRIFT'), ('worker', 'RUNTIME_DRIFT'),
            ('mode', 'RUNTIME_DRIFT'), ('environment', 'ENVIRONMENT_DRIFT'),
            ('source', 'SOURCE_SHA_DRIFT'))
        for changed, code in cases:
            with self.subTest(changed=changed):
                fixture = AcceptanceFixture('production')
                if changed == 'image':
                    fixture.backend['Image'] = 'sha256:' + 'c' * 64
                elif changed == 'worker':
                    fixture.env['UBER_EATS_WORKER_ENABLED'] = 'true'
                elif changed == 'mode':
                    fixture.env['UBER_EATS_PROCESSING_MODE'] = 'ORDER_MANAGER'
                elif changed == 'environment':
                    fixture.env['UBER_EATS_ENVIRONMENT'] = 'sandbox'
                else:
                    fixture.image['Config']['Labels']['org.opencontainers.image.revision'] = 'c' * 40
                with fixture.patched(), self.assertRaisesRegex(FixtureError, '^' + code + '$'):
                    verification.verify('production', IMAGE, SHA)
                fixture.request.assert_not_called()
                fixture.ops.write_private.assert_not_called()

    def test_each_business_count_change_refuses_accepted_proof(self):
        for key in ('uber_orders', 'local_uber_orders', 'uber_print_jobs', 'uber_bindings'):
            with self.subTest(key=key):
                fixture = AcceptanceFixture('production')
                after = dict(fixture.counts, **{key: 1})
                fixture.ops.inventory.side_effect = [fixture.counts, after]
                with fixture.patched(), self.assertRaisesRegex(FixtureError, '^BUSINESS_SIDE_EFFECT$'):
                    verification.verify('production', IMAGE, SHA)
                fixture.ops.write_private.assert_not_called()

    def test_wrong_http_result_or_nonempty_success_ack_refuses_proof(self):
        for failure in ('invalid-signature-accepted', 'valid-ack-error', 'valid-ack-body'):
            with self.subTest(failure=failure):
                fixture = AcceptanceFixture('staging')
                def response(url, *, raw=None, headers=None):
                    status, body = fixture.respond(url, raw=raw, headers=headers)
                    if raw is not None and url.endswith('/webhook'):
                        if failure == 'invalid-signature-accepted' and status == 401:
                            return 200, b''
                        if failure == 'valid-ack-error' and status == 200:
                            return 503, b'private diagnostic not logged'
                        if failure == 'valid-ack-body' and status == 200:
                            return 200, b'private diagnostic not logged'
                    return status, body
                fixture.request.side_effect = response
                code = 'WEBHOOK_ACK_BODY_NOT_EMPTY' if failure == 'valid-ack-body' else 'WEBHOOK_HTTP_ACCEPTANCE_FAILED'
                with fixture.patched(), self.assertRaisesRegex(FixtureError, '^' + code + '$'):
                    verification.verify('staging', IMAGE, SHA)
                fixture.ops.write_private.assert_not_called()

    def test_enabled_production_is_not_falsely_accepted_as_disabled(self):
        fixture = AcceptanceFixture('production')
        fixture.env['UBER_EATS_ENABLED'] = 'true'
        with fixture.patched(), self.assertRaisesRegex(FixtureError, '^PRODUCTION_EXPECTED_DISABLED$'):
            verification.verify('production', IMAGE, SHA)
        self.assertTrue(all(raw is None for _url, raw, _headers in fixture.requests))
        fixture.ops.write_private.assert_not_called()

    def test_request_network_error_is_fixed_code_without_secret_diagnostics(self):
        opener = Mock()
        opener.open.side_effect = RuntimeError(' '.join(PRIVATE_VALUES))
        fixture = AcceptanceFixture('production')
        with patch.object(verification, 'ops', fixture.ops), \
                patch.object(verification.urllib.request, 'build_opener', return_value=opener), \
                self.assertRaisesRegex(FixtureError, '^HTTP_REQUEST_FAILED$') as caught:
            verification.request('https://fixture.invalid', headers={'Authorization': 'Bearer ' + POS_TOKEN})
        self.assert_no_private_values(str(caught.exception))

    def test_cli_success_and_generic_error_never_print_credentials(self):
        for fail in (False, True):
            with self.subTest(fail=fail):
                fixture = AcceptanceFixture('production')
                if fail:
                    fixture.smoke.mint_token.side_effect = RuntimeError(' '.join(PRIVATE_VALUES))
                output = io.StringIO()
                with fixture.patched(), patch('sys.argv', ['verify', 'production', IMAGE, SHA]), \
                        patch.object(verification.os, 'umask') as umask, contextlib.redirect_stdout(output):
                    status = verification.main()
                self.assertEqual(status, 1 if fail else 0)
                umask.assert_called_once_with(0o077)
                text = output.getvalue()
                self.assert_no_private_values(text)
                if fail:
                    self.assertEqual(text.strip(), 'UBER_RECEIVE_ONLY_VERIFICATION_FAILED:RuntimeError')
                else:
                    self.assertTrue(json.loads(text)['accepted'])

    def test_malformed_artifact_is_rejected_before_runtime_or_http_reads(self):
        fixture = AcceptanceFixture('production')
        with fixture.patched(), self.assertRaisesRegex(FixtureError, '^ARTIFACT_INVALID$'):
            verification.verify('production', 'bad-image', SHA)
        fixture.ops.inspect.assert_not_called()
        fixture.request.assert_not_called()
        fixture.ops.write_private.assert_not_called()


if __name__ == '__main__':
    unittest.main()
