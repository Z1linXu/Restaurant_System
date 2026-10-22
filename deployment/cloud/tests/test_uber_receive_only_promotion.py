import copy
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('uber_promotion', Path(__file__).resolve().parents[1] / 'promote-uber-receive-only-20261022.py')
promotion = importlib.util.module_from_spec(spec)
spec.loader.exec_module(promotion)

IMAGE = 'sha256:' + 'a' * 64
SHA = 'b' * 40


class UberReceiveOnlyPromotionTest(unittest.TestCase):
    def environment(self, runtime='staging'):
        return {'APP_ENVIRONMENT': runtime, 'JWT_SECRET': 'unchanged$private',
                'UBER_EATS_ENABLED': 'true', 'UBER_EATS_ENVIRONMENT': 'sandbox',
                'UBER_EATS_CLIENT_ID': 'test-client', 'UBER_EATS_CLIENT_SECRET': 'test-oauth',
                'UBER_EATS_WEBHOOK_SIGNING_KEY': 'test-signing'}

    def model(self, environment):
        return {'services': {'backend': {'image': 'sha256:' + 'c' * 64,
            'environment': environment, 'entrypoint': ['sh', '-c', 'java $$JAVA_OPTS -jar /app/app.jar'],
            'mem_limit': '768m', 'networks': {'retained': {'ipv4_address': '172.1.0.2'}}}},
            'networks': {'retained': {'external': True}}}

    def test_staging_preserves_credentials_and_pauses_worker(self):
        original = self.environment()
        wanted = promotion.wanted_env('staging', original, {})
        self.assertEqual(wanted['UBER_EATS_WORKER_ENABLED'], 'false')
        self.assertEqual(wanted['UBER_EATS_PROCESSING_MODE'], 'KITCHEN_MIRROR')
        self.assertEqual(wanted['UBER_EATS_SCOPES'], 'eats.store eats.store.orders.read')
        self.assertEqual(wanted['UBER_EATS_STORE_CUTOVERS'], '{}')
        for key in promotion.CREDENTIAL_KEYS:
            self.assertEqual(original[key], wanted[key])
        self.assertNotIn('UBER_EATS_WORKER_ENABLED', original)

    def test_production_without_key_stays_disabled_and_gets_no_fake_secret(self):
        original = {'APP_ENVIRONMENT': 'production', 'JWT_SECRET': 'private', 'UBER_EATS_ENABLED': 'false'}
        wanted = promotion.wanted_env('production', original, {})
        self.assertEqual(wanted['UBER_EATS_ENABLED'], 'false')
        self.assertEqual(wanted['UBER_EATS_WORKER_ENABLED'], 'false')
        self.assertEqual(wanted['UBER_EATS_CLIENT_ID'], promotion.PROD_CLIENT_ID)
        self.assertNotIn('UBER_EATS_CLIENT_SECRET', wanted)
        self.assertNotIn('UBER_EATS_WEBHOOK_SIGNING_KEY', wanted)

    def test_production_independent_key_enables_only_receipt_and_removes_test_exception(self):
        original = {'APP_ENVIRONMENT': 'production', 'UBER_EATS_TEST_WEBHOOK_STORE_ID': 'test-store'}
        wanted = promotion.wanted_env('production', original, {'UBER_EATS_WEBHOOK_SIGNING_KEY': 'new$key'})
        self.assertEqual(wanted['UBER_EATS_ENABLED'], 'true')
        self.assertEqual(wanted['UBER_EATS_WORKER_ENABLED'], 'false')
        self.assertEqual(wanted['UBER_EATS_ENVIRONMENT'], 'production')
        self.assertNotIn('UBER_EATS_TEST_WEBHOOK_STORE_ID', wanted)

    def test_private_overlay_cannot_change_pos_or_worker_flags(self):
        for key in ('JWT_SECRET', 'APP_PRINTING_ALLOWED_MODES', 'UBER_EATS_WORKER_ENABLED'):
            with self.subTest(key=key), self.assertRaisesRegex(promotion.PromotionError, 'CREDENTIAL_INPUT_INVALID'):
                promotion.wanted_env('production', {'APP_ENVIRONMENT': 'production'}, {key: 'true'})
        with self.assertRaisesRegex(promotion.PromotionError, 'STAGING_CREDENTIAL_OVERRIDE_FORBIDDEN'):
            promotion.wanted_env('staging', self.environment(), {'UBER_EATS_CLIENT_SECRET': 'replacement'})

    def test_production_oauth_secret_cannot_reuse_test_webhook_key(self):
        with self.assertRaisesRegex(promotion.PromotionError, '^PRODUCTION_TEST_SECRET_REUSE$'):
            promotion.require_production_secret_isolation(
                {'UBER_EATS_CLIENT_SECRET': 'test-signing'}, self.environment())

    def test_production_webhook_key_cannot_reuse_test_oauth_secret(self):
        with self.assertRaisesRegex(promotion.PromotionError, '^PRODUCTION_TEST_SECRET_REUSE$'):
            promotion.require_production_secret_isolation(
                {'UBER_EATS_WEBHOOK_SIGNING_KEY': 'test-oauth'}, self.environment())

    def test_independent_production_secrets_and_absent_credentials_are_allowed(self):
        promotion.require_production_secret_isolation({}, self.environment())
        promotion.require_production_secret_isolation(
            {'UBER_EATS_CLIENT_SECRET': 'production-oauth',
             'UBER_EATS_WEBHOOK_SIGNING_KEY': 'production-signing'}, self.environment())

    def test_models_escape_dollars_and_safe_rollback_never_restores_stage_worker(self):
        original = self.model(self.environment())
        wanted = promotion.wanted_env('staging', self.environment(), {})
        wanted['UBER_EATS_WEBHOOK_SIGNING_KEY'] = 'literal${not-an-env}$'
        target, rollback = promotion.models(original, IMAGE, wanted)
        self.assertEqual(target['services']['backend']['environment']['UBER_EATS_WEBHOOK_SIGNING_KEY'], 'literal$${not-an-env}$$')
        self.assertEqual(rollback['services']['backend']['environment']['UBER_EATS_ENABLED'], 'false')
        self.assertEqual(rollback['services']['backend']['environment']['UBER_EATS_WORKER_ENABLED'], 'false')
        self.assertEqual(rollback['services']['backend']['image'], original['services']['backend']['image'])
        self.assertEqual(original['services']['backend']['environment']['UBER_EATS_ENABLED'], 'true')

    def test_unapproved_service_env_network_and_frontend_deltas_are_rejected(self):
        original = self.model(self.environment())
        wanted = promotion.wanted_env('staging', self.environment(), {})
        target, _ = promotion.models(original, IMAGE, wanted)
        promotion.validate_delta(original, target, IMAGE, wanted)
        for mutation in ('env', 'network', 'resource', 'frontend'):
            changed = copy.deepcopy(target)
            if mutation == 'env':
                changed['services']['backend']['environment']['JWT_SECRET'] = 'different'
            elif mutation == 'network':
                changed['networks']['retained']['external'] = False
            elif mutation == 'resource':
                changed['services']['backend']['mem_limit'] = '4096m'
            else:
                changed['services']['nginx'] = {'image': IMAGE}
            with self.subTest(mutation=mutation), self.assertRaises(promotion.PromotionError):
                promotion.validate_delta(original, changed, IMAGE, wanted)

    def test_queue_guard_preserves_printing_failed_but_refuses_consumable_work(self):
        value = {'flyway': '37', 'pending_print_jobs': 0, 'pending_outbox': 0,
                 'pending_uber_events': 0, 'due_uber_orders': 0, 'uber_bindings': 0,
                 'uber_orders': 0, 'local_uber_orders': 0, 'uber_print_jobs': 0,
                 'retained_print_states': [{'id': 200, 'status': 'PRINTING'}, {'id': 209, 'status': 'FAILED'}]}
        promotion.queue_guard(value, True)
        for key in ('pending_print_jobs', 'pending_outbox', 'pending_uber_events', 'due_uber_orders'):
            changed = dict(value, **{key: 1})
            with self.subTest(key=key), self.assertRaisesRegex(promotion.PromotionError, 'PENDING_QUEUE'):
                promotion.queue_guard(changed)
        with self.assertRaisesRegex(promotion.PromotionError, 'PRODUCTION_UBER_BASELINE_DRIFT'):
            promotion.queue_guard(dict(value, uber_bindings=1), True)

    def test_production_requires_exact_accepted_staging_artifact_and_no_side_effects(self):
        accepted = {'environment': 'staging', 'source_sha': SHA, 'image': IMAGE,
                    'accepted': True, 'worker_enabled': False, 'health': 'PASS',
                    'business_order_delta': 0, 'uber_print_job_delta': 0}
        promotion.validate_staging_acceptance(accepted, IMAGE, SHA)
        for key, value in (('source_sha', '0'*40), ('image', 'sha256:'+'0'*64),
                           ('worker_enabled', True), ('accepted', False), ('business_order_delta', 1), ('uber_print_job_delta', 1)):
            with self.subTest(key=key), self.assertRaisesRegex(promotion.PromotionError, 'STAGING_ACCEPTANCE_REQUIRED'):
                promotion.validate_staging_acceptance(dict(accepted, **{key: value}), IMAGE, SHA)

    def test_acceptance_requires_http_proof_and_valid_nonbusiness_event_identity(self):
        value = {'environment': 'staging', 'source_sha': SHA, 'image': IMAGE,
                 'accepted': True, 'worker_enabled': False, 'health': 'PASS',
                 'business_order_delta': 0, 'uber_print_job_delta': 0,
                 'valid_http_status': 200, 'duplicate_http_status': 200,
                 'invalid_http_status': 401, 'environment_mismatch_http_status': 400,
                 'event_id': '9e3644c8-f461-4bd8-a5a3-41a4b9baf758'}
        promotion.validate_acceptance_proof(value, IMAGE, SHA)
        for key, changed in (('valid_http_status', 500), ('event_id', "' OR true;--"), ('invalid_http_status', 200)):
            with self.subTest(key=key), self.assertRaises(promotion.PromotionError):
                promotion.validate_acceptance_proof(dict(value, **{key: changed}), IMAGE, SHA)

    def test_private_atomic_write_permissions_and_symlink_refusal(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory).resolve() / 'private.json'
            promotion.write_private(path, {'private': 'value'})
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(promotion.private_file(path), path)
            symlink = Path(directory).resolve() / 'link.json'
            symlink.symlink_to(path)
            with self.assertRaisesRegex(promotion.PromotionError, 'PRIVATE_PATH_SYMLINK'):
                promotion.write_private(symlink, {})
            with self.assertRaisesRegex(promotion.PromotionError, 'PRIVATE_PATH_SYMLINK'):
                promotion.private_file(symlink)

    def test_subprocess_error_does_not_expose_args_stdout_or_stderr(self):
        failed = subprocess.CompletedProcess(['sensitive-command', 'secret'], 1, b'secret stdout', b'secret stderr')
        with patch.object(promotion.subprocess, 'run', return_value=failed):
            with self.assertRaises(promotion.PromotionError) as caught:
                promotion.run(['sensitive-command', 'secret'])
        self.assertEqual(str(caught.exception), 'COMMAND_FAILED')
        self.assertNotIn('secret', str(caught.exception))

    def exercise_deployment(self, fail_after_start=False):
        """Fake Docker/DB boundaries exercise mutation order, not a real deployment."""
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory).resolve()
            stage = base / 'staging'
            source = stage / 'production-release-20261005/target.private.json'
            source.parent.mkdir(parents=True)
            env = self.environment()
            env['JWT_SECRET'] = 'unchanged'
            original = self.model(env)
            promotion.write_private(source, original)
            locks = (base / 'production.lock', base / 'staging.lock')
            for lock in locks:
                promotion.write_private(lock, {})
            initial = {
                'Id': 'previous-backend-id', 'Image': original['services']['backend']['image'],
                'State': {'Running': True},
                'Config': {'Env': [k+'='+v for k, v in env.items()], 'Entrypoint': ['java']},
                'HostConfig': {}, 'NetworkSettings': {'Networks': {'retained': {'IPAddress': '172.1.0.2'}}},
            }
            state = {'backend': copy.deepcopy(initial), 'up': [], 'after_started': False}
            queues = {'flyway': '37', 'pending_print_jobs': 0, 'pending_outbox': 0,
                      'pending_uber_events': 0, 'due_uber_orders': 0, 'uber_bindings': 1,
                      'uber_orders': 13, 'local_uber_orders': 11, 'uber_print_jobs': 28,
                      'retained_print_states': [{'id': 200, 'status': 'PRINTING'}]}

            def fake_inspect(name):
                if name == IMAGE:
                    return {'Config': {'Labels': {'org.opencontainers.image.revision': SHA}}}
                if name == 'restaurant-pos-staging-backend-1':
                    value = copy.deepcopy(state['backend'])
                    if fail_after_start and state['after_started'] and len(state['up']) == 1:
                        value['Image'] = 'unexpected-image'
                    return value
                return {'Id': name+'-retained-id', 'State': {'Running': True}}

            def fake_run(args, **kwargs):
                if args[:2] == ['docker', 'ps']:
                    return b'restaurant-pos-staging-backend-1\nrestaurant-pos-staging-db-1\ncloud-backend-1\n'
                if args[:2] == ['docker', 'compose']:
                    model_path = Path(args[5])
                    model = json.loads(model_path.read_text())
                    if args[6] == 'config':
                        return json.dumps(model).encode()
                    self.assertEqual(args[6:], ['up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend'])
                    state['up'].append(model_path.name)
                    state['after_started'] = True
                    state['backend'] = copy.deepcopy(initial)
                    state['backend']['Image'] = model['services']['backend']['image']
                    state['backend']['Config']['Env'] = [k+'='+v for k, v in model['services']['backend']['environment'].items()]
                    return b''
                if args[:3] == ['docker', 'exec', '-i'] and args[-2:] == ['pg_restore', '--list']:
                    self.assertGreater((stage / ('uber-receive-only-'+SHA) / 'database-before.dump').stat().st_size, 0)
                    return b'validated fixture archive'
                if args[:3] == ['docker', 'exec', '-i']:
                    self.assertTrue(kwargs['input'].startswith(b'BEGIN READ ONLY;'))
                    return json.dumps(queues).encode()
                self.fail('Unexpected fake command')

            def backup(args, **kwargs):
                self.assertIn('pg_dump -Fc', args[-1])
                kwargs['stdout'].write(b'PGDMP fixture')
                return subprocess.CompletedProcess(args, 0)

            with patch.object(promotion, 'BASE', base), patch.object(promotion, 'LOCKS', locks), \
                    patch.object(promotion, 'inspect', side_effect=fake_inspect), \
                    patch.object(promotion, 'run', side_effect=fake_run), \
                    patch.object(promotion.subprocess, 'run', side_effect=backup), \
                    patch.object(promotion, 'health'):
                if fail_after_start:
                    with self.assertRaisesRegex(promotion.PromotionError, 'PROMOTION_FAILED_SAFE_ROLLBACK_APPLIED'):
                        promotion.promotion('staging', IMAGE, SHA)
                else:
                    result = promotion.promotion('staging', IMAGE, SHA)
                    self.assertFalse(result['worker_enabled'])
                    self.assertFalse(result['accepted'])
            current = json.loads(source.read_text())['services']['backend']
            self.assertEqual(current['environment']['UBER_EATS_WORKER_ENABLED'], 'false')
            if fail_after_start:
                self.assertEqual(state['up'], ['target.private.json', 'rollback.private.json'])
                self.assertEqual(current['image'], initial['Image'])
                self.assertEqual(current['environment']['UBER_EATS_ENABLED'], 'false')
            else:
                self.assertEqual(state['up'], ['target.private.json'])
                self.assertEqual(current['image'], IMAGE)

    def test_deployment_uses_only_backend_and_commits_paused_model_after_checks(self):
        self.exercise_deployment()

    def test_failed_deployment_rolls_back_image_with_uber_disabled_and_worker_false(self):
        self.exercise_deployment(fail_after_start=True)


if __name__ == '__main__':
    unittest.main()
