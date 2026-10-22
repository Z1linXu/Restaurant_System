#!/usr/bin/env python3
"""Bounded backend-only promotion. Never enables an Uber processing worker.

Usage: script staging|production sha256:<image> <full-source-sha>
              [--uber-env-file /private/operator-owned/credentials.json]
The optional JSON may supply PROD credentials only. It is never printed.
After external Staging acceptance, the operator records acceptance.json in the
Staging release directory; see validate_staging_acceptance for its safe fields.
"""
import argparse
import copy
import contextlib
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import tempfile
import time
import urllib.request
import uuid

BASE = Path('/srv/restaurant-pos')
LOCKS = (
    Path('/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock'),
    BASE / 'staging/state/restaurant-pos-staging-hygiene.lock',
)
PROD_CLIENT_ID = '6GaiQpZTcfcuTa150wr4RyztC33PV7LS'
SCOPES = 'eats.store eats.store.orders.read'
EDITABLE_ENV = frozenset({
    'UBER_EATS_ENABLED', 'UBER_EATS_WORKER_ENABLED', 'UBER_EATS_ENVIRONMENT',
    'UBER_EATS_PROCESSING_MODE', 'UBER_EATS_SCOPES', 'UBER_EATS_PROCESSING_NOT_BEFORE',
    'UBER_EATS_STORE_CUTOVERS',
    'UBER_EATS_CLIENT_ID', 'UBER_EATS_CLIENT_SECRET', 'UBER_EATS_WEBHOOK_SIGNING_KEY',
    'UBER_EATS_TEST_WEBHOOK_CLIENT_ID', 'UBER_EATS_TEST_WEBHOOK_STORE_ID',
})
CREDENTIAL_KEYS = frozenset({
    'UBER_EATS_CLIENT_ID', 'UBER_EATS_CLIENT_SECRET', 'UBER_EATS_WEBHOOK_SIGNING_KEY',
})
CURRENT_4483_BASELINE_SHA = '4b3abd1349e66a146c36ac310b6a8b48bb2ad572'
CURRENT_4483_BASELINE_IMAGE = 'sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5'
CURRENT_4483_STORES = {'staging': 'bd993244-5589-4b19-8f0d-dc2ba73d4273',
                     'production': '5a7dca5c-b7ec-57c8-a684-87d23e66e8d9'}
CURRENT_4483_ROOT = 'e72231fb-9029-4539-91d6-b6efbeeb3767'
CURRENT_4483_DEFAULTS_KEY = 'UBER_EATS_MIRROR_ITEM_DEFAULTS'
QUEUE_SQL = """BEGIN READ ONLY;
SELECT json_build_object(
 'flyway',(select version from flyway_schema_history where success order by installed_rank desc limit 1),
 'pending_print_jobs',(select count(*) from print_jobs where status in ('PENDING','CLAIMED')),
 'pending_outbox',(select count(*) from order_dispatch_outbox where status in ('PENDING','PROCESSING')),
 'pending_uber_events',(select count(*) from uber_eats_events where status='PENDING'),
 'due_uber_orders',(select count(*) from uber_eats_orders where next_attempt_at<=now() and status in
 ('ACCEPTING','DENYING','UBER_ACCEPTED','LOCAL_FAILED','WAITING_FOR_ACCEPTANCE','WAITING_FOR_RELEASE','MIRROR_READY','MIRROR_LOCAL_FAILED','RELEASED_MAPPING_REQUIRED')),
 'uber_bindings',(select count(*) from uber_eats_store_mappings),
 'uber_orders',(select count(*) from uber_eats_orders),
 'local_uber_orders',(select count(*) from orders where external_source='UBER_EATS'),
 'uber_print_jobs',(select count(*) from print_jobs p join orders o on o.id=p.order_id where o.external_source='UBER_EATS'),
 'retained_print_states',(select coalesce(json_agg(x),'[]'::json) from
 (select id,module_code,status from print_jobs where status in ('PRINTING','FAILED') order by id) x));
ROLLBACK;
"""


class PromotionError(RuntimeError):
    """Only fixed, non-sensitive error codes may cross the CLI boundary."""


def require(condition, code):
    if not condition:
        raise PromotionError(code)


def private_file(path):
    path = Path(path)
    require(path.resolve() == path.absolute(), 'PRIVATE_PATH_SYMLINK')
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
            and stat.S_IMODE(info.st_mode) == 0o600, 'PRIVATE_FILE_PERMISSIONS')
    return path


@contextlib.contextmanager
def operation_locks():
    with contextlib.ExitStack() as stack:
        for path in LOCKS:
            handle = stack.enter_context(private_file(path).open('r+'))
            fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def write_private(path, value):
    """Atomic writes retain 0600 and cannot follow an existing destination symlink."""
    path = Path(path)
    require(not path.is_symlink(), 'PRIVATE_PATH_SYMLINK')
    temporary = None
    try:
        with tempfile.NamedTemporaryFile('w', dir=path.parent, prefix='.uber-', delete=False) as out:
            temporary = Path(out.name)
            os.fchmod(out.fileno(), 0o600)
            json.dump(value, out, indent=2)
            out.flush()
            os.fsync(out.fileno())
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def run(args, **kwargs):
    try:
        result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=300, check=False, **kwargs)
    except (OSError, subprocess.SubprocessError):
        raise PromotionError('COMMAND_FAILED') from None
    require(result.returncode == 0, 'COMMAND_FAILED')
    return result.stdout


def inspect(name):
    return json.loads(run(['docker', 'inspect', name]))[0]


def env_of(container):
    return dict(value.split('=', 1) for value in container['Config']['Env'])


def wanted_env(environment, original, credentials):
    require(environment in ('staging', 'production'), 'ENVIRONMENT_INVALID')
    require(original.get('APP_ENVIRONMENT') == environment, 'RUNTIME_ENVIRONMENT_DRIFT')
    require(set(credentials) <= CREDENTIAL_KEYS
            and all(isinstance(v, str) for v in credentials.values()), 'CREDENTIAL_INPUT_INVALID')
    require(environment == 'production' or not credentials, 'STAGING_CREDENTIAL_OVERRIDE_FORBIDDEN')
    result = dict(original)
    result.update({
        'UBER_EATS_WORKER_ENABLED': 'false',
        'UBER_EATS_ENVIRONMENT': 'production' if environment == 'production' else 'sandbox',
        'UBER_EATS_PROCESSING_MODE': 'KITCHEN_MIRROR',
        'UBER_EATS_SCOPES': SCOPES,
        'UBER_EATS_PROCESSING_NOT_BEFORE': '',
        'UBER_EATS_STORE_CUTOVERS': '{}',
    })
    if environment == 'production':
        result.update(credentials)
        result['UBER_EATS_CLIENT_ID'] = result.get('UBER_EATS_CLIENT_ID') or PROD_CLIENT_ID
        require(result['UBER_EATS_CLIENT_ID'] == PROD_CLIENT_ID, 'PRODUCTION_CLIENT_ID_MISMATCH')
        result.pop('UBER_EATS_TEST_WEBHOOK_CLIENT_ID', None)
        result.pop('UBER_EATS_TEST_WEBHOOK_STORE_ID', None)
        result['UBER_EATS_ENABLED'] = 'true' if result.get('UBER_EATS_WEBHOOK_SIGNING_KEY', '').strip() else 'false'
    else:
        require(bool(original.get('UBER_EATS_WEBHOOK_SIGNING_KEY')), 'STAGING_SIGNING_KEY_MISSING')
        require(original.get('UBER_EATS_ENVIRONMENT') == 'sandbox', 'STAGING_ENVIRONMENT_DRIFT')
        result['UBER_EATS_ENABLED'] = 'true'
    return result


def models(original, image, expected_env):
    target = copy.deepcopy(original)
    target['services']['backend']['image'] = image
    encoded = target['services']['backend']['environment']
    for key in EDITABLE_ENV:
        if key in expected_env:
            # Private Compose models contain literal escapes; never interpolate a credential.
            encoded[key] = expected_env[key].replace('$', '$$')
        else:
            encoded.pop(key, None)
    rollback = copy.deepcopy(original)
    rollback['services']['backend']['environment'].update({
        'UBER_EATS_ENABLED': 'false', 'UBER_EATS_WORKER_ENABLED': 'false',
    })
    return target, rollback


def validate_delta(before, after, image, expected_env):
    require(after['services']['backend']['image'] == image, 'IMAGE_DRIFT')
    require(after['services']['backend']['environment'] == expected_env, 'ENVIRONMENT_DRIFT')
    left, right = copy.deepcopy(before), copy.deepcopy(after)
    for value in (left, right):
        backend = value['services']['backend']
        backend.pop('image', None)
        backend['environment'] = {k: v for k, v in backend['environment'].items() if k not in EDITABLE_ENV}
    require(left == right, 'UNAPPROVED_SERVICE_DELTA')


def queue_guard(value, production=False):
    require(value['flyway'] == '37', 'FLYWAY_DRIFT')
    for key in ('pending_print_jobs', 'pending_outbox', 'pending_uber_events', 'due_uber_orders'):
        require(value[key] == 0, 'PENDING_QUEUE_REQUIRES_REVIEW')
    if production:
        for key in ('uber_bindings', 'uber_orders', 'local_uber_orders', 'uber_print_jobs'):
            require(value[key] == 0, 'PRODUCTION_UBER_BASELINE_DRIFT')


def validate_staging_acceptance(value, image, sha):
    expected = {
        'environment': 'staging', 'source_sha': sha, 'image': image,
        'accepted': True, 'worker_enabled': False, 'health': 'PASS',
        'business_order_delta': 0, 'uber_print_job_delta': 0,
    }
    require(all(value.get(k) == v for k, v in expected.items()), 'STAGING_ACCEPTANCE_REQUIRED')


def require_production_secret_isolation(production, staging):
    keys = ('UBER_EATS_CLIENT_SECRET', 'UBER_EATS_WEBHOOK_SIGNING_KEY')
    test_secrets = {staging[key] for key in keys if staging.get(key)}
    require(all(not production.get(key) or production[key] not in test_secrets for key in keys),
            'PRODUCTION_TEST_SECRET_REUSE')


def inventory(db):
    return json.loads(run(['docker', 'exec', '-i', db, 'sh', '-c',
        'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], input=QUEUE_SQL.encode()))


def validate_acceptance_proof(value, image, sha):
    validate_staging_acceptance(value, image, sha)
    expected = {'valid_http_status': 200, 'duplicate_http_status': 200,
                'invalid_http_status': 401, 'environment_mismatch_http_status': 400}
    require(all(value.get(k) == v for k, v in expected.items()), 'STAGING_HTTP_ACCEPTANCE_REQUIRED')
    try:
        require(str(uuid.UUID(value['event_id'])) == value['event_id'], 'ACCEPTANCE_EVENT_ID_INVALID')
    except (KeyError, ValueError, AttributeError, TypeError):
        raise PromotionError('ACCEPTANCE_EVENT_ID_INVALID') from None


def verify_staging_observation(root, image, sha, evidence):
    stage = inspect('restaurant-pos-staging-backend-1')
    require(stage['Image'] == image and env_of(stage).get('UBER_EATS_WORKER_ENABLED') == 'false'
            and env_of(stage).get('UBER_EATS_ENVIRONMENT') == 'sandbox', 'STAGING_RUNTIME_DRIFT')
    require(inspect(image)['Config'].get('Labels', {}).get('org.opencontainers.image.revision') == sha, 'SOURCE_SHA_DRIFT')
    health('https://staging-pos.lanzhounoodlesmtl.com')
    current = inventory('restaurant-pos-staging-db-1')
    queue_guard(current)
    before = json.loads(private_file(root / 'queue-before.json').read_text())
    for key in ('uber_orders', 'local_uber_orders', 'uber_print_jobs', 'uber_bindings'):
        require(current[key] == before[key], 'STAGING_ACCEPTANCE_SIDE_EFFECT')
    # UUID validation above makes this literal safe; no credentials/customer data is selected.
    sql = ("BEGIN READ ONLY; SELECT json_build_object('event_rows',count(*),"
           "'held_rows',count(*) filter(where status='HELD_RECEIVE_ONLY'),"
           "'integration_rows',(select count(*) from uber_eats_orders o where o.environment='sandbox'"
           " and o.uber_order_id in (select uber_order_id from uber_eats_events where"
           " environment='sandbox' and event_id='" + evidence['event_id'] + "')))"
           " from uber_eats_events where environment='sandbox' and event_id='" + evidence['event_id'] + "'; ROLLBACK;")
    observed = json.loads(run(['docker', 'exec', '-i', 'restaurant-pos-staging-db-1', 'sh', '-c',
        'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], input=sql.encode()))
    require(observed == {'event_rows': 1, 'held_rows': 1, 'integration_rows': 0}, 'STAGING_DURABLE_RECEIPT_REQUIRED')


def record_staging_acceptance(image, sha, evidence_path):
    require(bool(re.fullmatch(r'sha256:[0-9a-f]{64}', image))
            and bool(re.fullmatch(r'[0-9a-f]{40}', sha)), 'ARTIFACT_INVALID')
    with operation_locks():
        root = BASE / 'staging' / ('uber-receive-only-' + sha)
        evidence = json.loads(private_file(evidence_path).read_text())
        validate_acceptance_proof(evidence, image, sha)
        result = json.loads(private_file(root / 'result.json').read_text())
        require(result.get('source_sha') == sha and result.get('image') == image
                and result.get('worker_enabled') is False and result.get('health') == 'PASS', 'STAGING_DEPLOYMENT_REQUIRED')
        verify_staging_observation(root, image, sha, evidence)
        proof_path = root / 'acceptance-evidence.json'
        write_private(proof_path, evidence)
        accepted = {key: evidence[key] for key in ('environment', 'source_sha', 'image', 'accepted',
                    'worker_enabled', 'health', 'business_order_delta', 'uber_print_job_delta', 'event_id')}
        accepted['evidence_sha256'] = hashlib.sha256(proof_path.read_bytes()).hexdigest()
        accepted['observed_at'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        write_private(root / 'acceptance.json', accepted)
        return accepted


def health(url):
    try:
        with urllib.request.urlopen(url + '/api/v1/system/health', timeout=6) as response:
            require(response.status == 200 and response.geturl() == url + '/api/v1/system/health'
                    and json.load(response)['data']['status'] == 'UP', 'HEALTH_FAILED')
    except Exception:
        raise PromotionError('HEALTH_FAILED') from None


def wait_healthy(url):
    for attempt in range(40):
        try:
            health(url)
            return
        except PromotionError:
            time.sleep(2)
    raise PromotionError('BACKEND_HEALTH_TIMEOUT')


def current_4483_defaults(environment):
    require(environment in CURRENT_4483_STORES, 'ENVIRONMENT_INVALID')
    return [{'environment': 'production' if environment == 'production' else 'sandbox',
             'localStoreId': 1, 'organizationId': 1,
             'uberStoreId': CURRENT_4483_STORES[environment], 'uberItemId': CURRENT_4483_ROOT,
             'localSku': 'vegetable_noodle', 'optionGroup': 'SOUP_BASE', 'optionCode': 'soup_vegan'}]


def current_4483_env(environment, original):
    expected = {'APP_ENVIRONMENT': environment, 'UBER_EATS_ENABLED': 'true',
                'UBER_EATS_WORKER_ENABLED': 'false', 'UBER_EATS_PROCESSING_MODE': 'KITCHEN_MIRROR',
                'UBER_EATS_ENVIRONMENT': 'production' if environment == 'production' else 'sandbox',
                'UBER_EATS_SCOPES': SCOPES, 'UBER_EATS_STORE_CUTOVERS': '{}'}
    require(all(original.get(k) == v for k, v in expected.items())
            and not original.get('UBER_EATS_PROCESSING_NOT_BEFORE')
            and all(original.get(k) for k in CREDENTIAL_KEYS), 'CURRENT_4483_RECEIVE_ONLY_REQUIRED')
    if environment == 'production':
        require(original['UBER_EATS_CLIENT_ID'] == PROD_CLIENT_ID, 'PRODUCTION_CLIENT_ID_MISMATCH')
    defaults = current_4483_defaults(environment)
    try:
        prior = json.loads(original.get(CURRENT_4483_DEFAULTS_KEY) or '[]')
    except Exception:
        raise PromotionError('CURRENT_4483_DEFAULTS_DRIFT') from None
    require(prior == [] or prior == defaults, 'CURRENT_4483_DEFAULTS_DRIFT')
    return dict(original, **{CURRENT_4483_DEFAULTS_KEY: json.dumps(defaults, separators=(',', ':'))})


def current_4483_configuration(db):
    # Read-only configuration hashes exclude device heartbeats and printer telemetry.
    tables = ('stations', 'menu_items', 'menu_item_options', 'menu_categories', 'store_addons',
              'organization_addon_definitions', 'store_removes', 'store_remove_categories',
              'store_remove_item_rules', 'store_combo_groups', 'store_combo_components',
              'store_modules', 'uber_eats_menu_mappings', 'printer_assignments', 'flyway_schema_history')
    fields = []
    for table in tables:
        fields += ["'" + table + "'", "(SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text) FROM " + table + " t)"]
    fields += ["'bindings'", "(SELECT coalesce(jsonb_agg(jsonb_build_object('id',id,'store',store_id,'org',organization_id,'env',environment,'uber',uber_store_id,'enabled',enabled,'mode',processing_mode) ORDER BY id),'[]'::jsonb) FROM uber_eats_store_mappings)",
               "'local_store'", "(SELECT jsonb_build_object('id',id,'org',organization_id,'code',code) FROM stores WHERE id=1)",
               "'mapping_count'", "(SELECT count(*) FROM uber_eats_menu_mappings)",
               "'printing'", "(SELECT md5(coalesce(jsonb_agg(jsonb_build_array(id,printing_enabled,printing_mode) ORDER BY id),'[]'::jsonb)::text) FROM stores)",
               "'devices'", "(SELECT md5(coalesce(jsonb_agg(to_jsonb(d)-ARRAY['last_seen_at','last_heartbeat_at','updated_at','last_ip','last_seen_ip'] ORDER BY id),'[]'::jsonb)::text) FROM store_devices d)",
               "'printers'", "(SELECT md5(coalesce(jsonb_agg(to_jsonb(p)-ARRAY['last_successful_print_at','last_failed_print_at','last_error_message','last_connection_success_at','last_connection_failed_at','last_connection_error','updated_at'] ORDER BY id),'[]'::jsonb)::text) FROM printer_configs p)",
               "'fresh_printing'", "(SELECT count(*) FROM print_jobs WHERE status='PRINTING' AND (claim_expires_at>timezone('America/Toronto',now()) OR updated_at>timezone('America/Toronto',now())-interval '2 minutes'))",
               "'retained_printing'", "(SELECT md5(coalesce(jsonb_agg(to_jsonb(p) ORDER BY id),'[]'::jsonb)::text) FROM print_jobs p WHERE status IN ('PRINTING','FAILED'))"]
    sql = 'BEGIN READ ONLY; SELECT json_build_object(' + ','.join(fields) + '); ROLLBACK;'
    return json.loads(run(['docker', 'exec', '-i', db, 'sh', '-c',
                          'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], input=sql.encode()))


def current_4483_guard(environment, queues, configuration):
    require(queues['flyway'] == '38', 'CURRENT_4483_FLYWAY_V38_REQUIRED')
    for key in ('pending_print_jobs', 'pending_outbox', 'pending_uber_events', 'due_uber_orders'):
        require(queues[key] == 0, 'PENDING_QUEUE_REQUIRES_REVIEW')
    expected = {'id': 1, 'store': 1, 'org': 1,
                'env': 'production' if environment == 'production' else 'sandbox',
                'uber': CURRENT_4483_STORES[environment], 'enabled': True, 'mode': 'KITCHEN_MIRROR'}
    require(queues['uber_bindings'] == 1 and configuration['bindings'] == [expected]
            and configuration['mapping_count'] == 299, 'CURRENT_4483_BINDING_MAPPING_DRIFT')
    require(configuration['local_store'] == {'id': 1, 'org': 1,
            'code': '4483_R_SAINT_DENIS' if environment == 'production' else 'STG005_SRC_20260809_R01'},
            'CURRENT_4483_LOCAL_STORE_DRIFT')
    require(configuration['fresh_printing'] == 0, 'CURRENT_PRINTING_REQUIRES_QUIET_WINDOW')
    if environment == 'production':
        require(all(queues[k] == 0 for k in ('uber_orders', 'local_uber_orders', 'uber_print_jobs')),
                'CURRENT_4483_UBER_BUSINESS_DRIFT')


def validate_current_4483_acceptance(value, image, sha):
    expected = {'environment': 'staging', 'source_sha': sha, 'image': image,
                'accepted': True, 'worker_enabled': False, 'health': 'PASS',
                'preview_count': 300, 'preview_errors': 0, 'preview_warnings': 0,
                'raw_fallback_count': 0}
    require(isinstance(value, dict) and all(value.get(k) == v and type(value.get(k)) is type(v)
            for k, v in expected.items()), 'CURRENT_4483_STAGING_ACCEPTANCE_REQUIRED')


def patch_current_4483(environment, image, sha, acceptance_path=None):
    """One V38/backend-only item-default patch; never activates a worker or changes a credential."""
    require(environment in CURRENT_4483_STORES and bool(re.fullmatch(r'sha256:[0-9a-f]{64}', image))
            and bool(re.fullmatch(r'[0-9a-f]{40}', sha)), 'ARTIFACT_INVALID')
    require(environment == 'production' or acceptance_path is None, 'CURRENT_4483_ARGUMENTS_INVALID')
    with operation_locks():
        project = 'cloud' if environment == 'production' else 'restaurant-pos-staging'
        container, db = project + '-backend-1', project + '-db-1'
        baseline = {name: inspect(name) for name in run(['docker', 'ps', '--format', '{{.Names}}']).decode().splitlines()}
        before = baseline[container]
        require(before['State']['Running'] and baseline[db]['State']['Running'], 'RUNTIME_NOT_RUNNING')
        require(before['Image'] == CURRENT_4483_BASELINE_IMAGE
                and inspect(before['Image'])['Config'].get('Labels', {}).get('org.opencontainers.image.revision')
                == CURRENT_4483_BASELINE_SHA, 'CURRENT_4483_APPLICATION_BASELINE_DRIFT')
        require(inspect(image)['Config'].get('Labels', {}).get('org.opencontainers.image.revision') == sha,
                'SOURCE_SHA_DRIFT')
        original_env = env_of(before)
        expected_env = current_4483_env(environment, original_env)
        if environment == 'production':
            require(acceptance_path is not None, 'CURRENT_4483_STAGING_ACCEPTANCE_REQUIRED')
            validate_current_4483_acceptance(json.loads(private_file(acceptance_path).read_bytes()), image, sha)
            stage = baseline['restaurant-pos-staging-backend-1']
            stage_env = env_of(stage)
            require(stage['Image'] == image and stage_env == current_4483_env('staging', stage_env),
                    'CURRENT_4483_STAGING_RUNTIME_DRIFT')
            require_production_secret_isolation(expected_env, stage_env)
            source = BASE / 'production/current.compose.json'
        else:
            labels = before['Config'].get('Labels', {})
            require(labels.get('com.docker.compose.project') == project, 'CURRENT_4483_STAGE_MODEL_UNPROVEN')
            source = Path(labels.get('com.docker.compose.project.config_files', ''))
            require(source.is_absolute() and source.is_relative_to(BASE / 'staging')
                    and ',' not in str(source), 'CURRENT_4483_STAGE_MODEL_UNPROVEN')
        source = private_file(source)
        original_bytes = source.read_bytes()
        original = json.loads(original_bytes)
        require(set(original.get('services', {})) in ({'backend'}, {'backend', 'nginx'}), 'MODEL_SERVICES_DRIFT')
        target = copy.deepcopy(original)
        target['services']['backend']['image'] = image
        target['services']['backend']['environment'][CURRENT_4483_DEFAULTS_KEY] = expected_env[CURRENT_4483_DEFAULTS_KEY]
        root = BASE / environment / ('uber-4483-backend-' + sha)
        require(not root.exists(), 'RELEASE_ALREADY_EXISTS')
        require(not root.parent.is_symlink() and root.parent.stat().st_uid == os.getuid()
                and not stat.S_IMODE(root.parent.stat().st_mode) & 0o022, 'RELEASE_PARENT_UNSAFE')
        root.mkdir(mode=0o700)
        for name, model in (('original.private.json', original), ('target.private.json', target), ('rollback.private.json', original)):
            write_private(root / name, model)

        def compose(path, *args):
            return run(['docker', 'compose', '-p', project, '-f', str(path), *args])

        resolved_before = json.loads(compose(source, 'config', '--format', 'json'))
        resolved_target = json.loads(compose(root / 'target.private.json', 'config', '--format', 'json'))
        for service in original['services']:
            runtime = baseline[project + '-' + service + '-1']
            require(resolved_before['services'][service]['image'] == runtime['Image']
                    and resolved_before['services'][service]['environment'] == env_of(runtime), 'CANONICAL_MODEL_RUNTIME_DRIFT')
        left, right = copy.deepcopy(resolved_before), copy.deepcopy(resolved_target)
        require(right['services']['backend']['image'] == image
                and right['services']['backend']['environment'] == expected_env, 'CURRENT_4483_TARGET_DRIFT')
        left['services']['backend']['image'] = right['services']['backend']['image']
        left['services']['backend']['environment'][CURRENT_4483_DEFAULTS_KEY] = expected_env[CURRENT_4483_DEFAULTS_KEY]
        require(left == right, 'UNAPPROVED_SERVICE_DELTA')
        queues, configuration = inventory(db), current_4483_configuration(db)
        current_4483_guard(environment, queues, configuration)
        write_private(root / 'queue-before.json', queues)
        backup = root / 'database-before.dump'
        with backup.open('xb') as out:
            os.fchmod(out.fileno(), 0o600)
            completed = subprocess.run(['docker', 'exec', db, 'sh', '-c',
                'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'], stdout=out,
                stderr=subprocess.PIPE, timeout=180, check=False)
        require(completed.returncode == 0 and backup.stat().st_size > 0, 'DATABASE_BACKUP_FAILED')
        with backup.open('rb') as data:
            require(bool(run(['docker', 'exec', '-i', db, 'pg_restore', '--list'], stdin=data)), 'DATABASE_BACKUP_INVALID')
        current_4483_guard(environment, inventory(db), current_4483_configuration(db))
        require(current_4483_configuration(db) == configuration and source.read_bytes() == original_bytes,
                'CURRENT_4483_PREFLIGHT_DRIFT')
        base = 'https://' + ('pos' if environment == 'production' else 'staging-pos') + '.lanzhounoodlesmtl.com'
        attempted = False
        canonical_written = False
        try:
            attempted = True
            compose(root / 'target.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend')
            wait_healthy(base)
            after = inspect(container)
            require(after['Image'] == image and env_of(after) == expected_env, 'RUNTIME_CONFIGURATION_DRIFT')
            for key in ('Entrypoint', 'Cmd', 'User', 'WorkingDir', 'ExposedPorts', 'Volumes'):
                require(after['Config'].get(key) == before['Config'].get(key), 'CONTAINER_CONFIGURATION_DRIFT')
            for key in ('NanoCpus', 'CpuQuota', 'CpuPeriod', 'Memory', 'MemorySwap', 'PidsLimit',
                        'RestartPolicy', 'LogConfig', 'PortBindings', 'Binds', 'Mounts', 'ReadonlyRootfs',
                        'SecurityOpt', 'CapAdd', 'CapDrop', 'NetworkMode'):
                require(after['HostConfig'].get(key) == before['HostConfig'].get(key), 'HOST_CONFIGURATION_DRIFT')
            old_networks, new_networks = before['NetworkSettings']['Networks'], after['NetworkSettings']['Networks']
            require(set(old_networks) == set(new_networks) and all(new_networks[n]['IPAddress'] == v['IPAddress']
                    for n, v in old_networks.items()), 'NETWORK_DRIFT')
            for name, row in baseline.items():
                if name != container:
                    require(inspect(name)['Id'] == row['Id'], 'UNRELATED_CONTAINER_CHANGED')
            after_queues = inventory(db)
            current_4483_guard(environment, after_queues, current_4483_configuration(db))
            require(all(after_queues[k] == queues[k] for k in
                    ('uber_orders', 'local_uber_orders', 'uber_print_jobs', 'uber_bindings')), 'UBER_BUSINESS_SIDE_EFFECT')
            require(current_4483_configuration(db) == configuration, 'CURRENT_4483_CONFIGURATION_CHANGED')
            health('https://pos.lanzhounoodlesmtl.com')
            health('https://staging-pos.lanzhounoodlesmtl.com')
            require(source.read_bytes() == original_bytes, 'CANONICAL_SOURCE_CHANGED')
            if environment == 'production':
                write_private(source, target)
                canonical_written = True
        except BaseException:
            if attempted:
                owned = source.read_bytes() == original_bytes
                if not owned and canonical_written:
                    try: owned = json.loads(source.read_bytes()) == target
                    except Exception: owned = False
                require(owned, 'CANONICAL_DRIFT_REQUIRES_OPERATOR_NO_RECREATE')
                require(inspect(container)['Image'] in (before['Image'], image), 'ROLLBACK_APPLICATION_DRIFT_STOP')
                compose(root / 'rollback.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend')
                wait_healthy(base)
                restored = inspect(container)
                require(restored['Image'] == before['Image'] and env_of(restored) == original_env,
                        'ORIGINAL_RECEIVE_ONLY_ROLLBACK_FAILED')
                if canonical_written:
                    require(json.loads(source.read_bytes()) == target, 'CANONICAL_DRIFT_REQUIRES_OPERATOR')
                    write_private(source, original)
            raise PromotionError('CURRENT_4483_PATCH_FAILED_ORIGINAL_RESTORED') from None
        result = {'environment': environment, 'source_sha': sha, 'image': image, 'accepted': False,
                  'worker_enabled': False, 'health': 'PASS', 'defaults_entries': 1,
                  'other_containers_and_configuration_preserved': True,
                  'backup_sha256': hashlib.sha256(backup.read_bytes()).hexdigest()}
        write_private(root / 'result.json', result)
        if environment == 'production':
            write_private(source.parent / 'release.json', {'source_sha': sha, 'release_dir': str(root), 'compose': source.name})
        return result


def promotion(environment, image, sha, credential_path=None):
    with operation_locks():
        return promote_locked(environment, image, sha, credential_path)


def promote_locked(environment, image, sha, credential_path=None):
    require(bool(re.fullmatch(r'sha256:[0-9a-f]{64}', image)), 'IMAGE_INVALID')
    require(bool(re.fullmatch(r'[0-9a-f]{40}', sha)), 'SOURCE_SHA_INVALID')
    project = 'cloud' if environment == 'production' else 'restaurant-pos-staging'
    container, db = project + '-backend-1', project + '-db-1'
    source = BASE / ('production/current.compose.json' if environment == 'production' else
                     'staging/production-release-20261005/target.private.json')
    root = BASE / environment / ('uber-receive-only-' + sha)
    require(not root.exists(), 'RELEASE_ALREADY_EXISTS')
    private_file(source)
    require(not root.parent.is_symlink() and root.parent.stat().st_uid == os.getuid()
            and not stat.S_IMODE(root.parent.stat().st_mode) & 0o022, 'RELEASE_PARENT_UNSAFE')
    credentials = json.loads(private_file(credential_path).read_text()) if credential_path else {}
    baseline = {name: inspect(name) for name in run(['docker', 'ps', '--format', '{{.Names}}']).decode().splitlines()}
    require(baseline[container]['State']['Running'] and baseline[db]['State']['Running'], 'RUNTIME_NOT_RUNNING')
    require(inspect(image)['Config'].get('Labels', {}).get('org.opencontainers.image.revision') == sha, 'SOURCE_SHA_DRIFT')
    if environment == 'production':
        acceptance_root = BASE / 'staging' / ('uber-receive-only-' + sha)
        acceptance = json.loads(private_file(acceptance_root / 'acceptance.json').read_text())
        validate_staging_acceptance(acceptance, image, sha)
        proof_path = private_file(acceptance_root / 'acceptance-evidence.json')
        require(hashlib.sha256(proof_path.read_bytes()).hexdigest() == acceptance.get('evidence_sha256'), 'STAGING_EVIDENCE_HASH_MISMATCH')
        proof = json.loads(proof_path.read_text())
        validate_acceptance_proof(proof, image, sha)
        verify_staging_observation(acceptance_root, image, sha, proof)
        stage = baseline['restaurant-pos-staging-backend-1']
        require(stage['Image'] == image and env_of(stage).get('UBER_EATS_WORKER_ENABLED') == 'false', 'STAGING_RUNTIME_DRIFT')
    original_bytes = source.read_bytes()
    original = json.loads(original_bytes)
    require(set(original['services']) == ({'backend', 'nginx'} if environment == 'production' else {'backend'}), 'MODEL_SERVICES_DRIFT')
    require(original['services']['backend']['image'] == baseline[container]['Image'], 'MODEL_IMAGE_DRIFT')
    expected_env = wanted_env(environment, env_of(baseline[container]), credentials)
    if environment == 'production':
        require_production_secret_isolation(expected_env, env_of(baseline['restaurant-pos-staging-backend-1']))
    target, rollback = models(original, image, expected_env)
    root.mkdir(mode=0o700)
    for name, value in (('before.private.json', baseline), ('original.private.json', original),
                        ('target.private.json', target), ('rollback.private.json', rollback)):
        write_private(root / name, value)

    def compose(path, *args):
        return run(['docker', 'compose', '-p', project, '-f', str(path), *args])

    before_model = json.loads(compose(source, 'config', '--format', 'json'))
    require(before_model['services']['backend']['environment'] == env_of(baseline[container]), 'MODEL_ENVIRONMENT_DRIFT')
    validate_delta(before_model, json.loads(compose(root / 'target.private.json', 'config', '--format', 'json')), image, expected_env)
    rollback_env = dict(env_of(baseline[container]), UBER_EATS_ENABLED='false', UBER_EATS_WORKER_ENABLED='false')
    validate_delta(before_model, json.loads(compose(root / 'rollback.private.json', 'config', '--format', 'json')), baseline[container]['Image'], rollback_env)

    before = inventory(db)
    queue_guard(before, environment == 'production')
    write_private(root / 'queue-before.json', before)
    backup = root / 'database-before.dump'
    try:
        with backup.open('xb') as out:
            os.fchmod(out.fileno(), 0o600)
            completed = subprocess.run(['docker', 'exec', db, 'sh', '-c',
                'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'], stdout=out,
                stderr=subprocess.PIPE, timeout=180, check=False)
        require(completed.returncode == 0 and backup.stat().st_size > 0, 'DATABASE_BACKUP_FAILED')
        with backup.open('rb') as data:
            require(bool(run(['docker', 'exec', '-i', db, 'pg_restore', '--list'], stdin=data)), 'DATABASE_BACKUP_INVALID')
    except (OSError, subprocess.SubprocessError):
        raise PromotionError('DATABASE_BACKUP_FAILED') from None
    queue_guard(inventory(db), environment == 'production')
    require(source.read_bytes() == original_bytes, 'SOURCE_MODEL_CHANGED')
    base = 'https://' + ('pos' if environment == 'production' else 'staging-pos') + '.lanzhounoodlesmtl.com'
    try:
        compose(root / 'target.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend')
        wait_healthy(base)
        after = inspect(container)
        require(after['Image'] == image and env_of(after) == expected_env, 'RUNTIME_CONFIGURATION_DRIFT')
        for key in ('Entrypoint', 'Cmd', 'User', 'WorkingDir', 'ExposedPorts', 'Volumes'):
            require(after['Config'].get(key) == baseline[container]['Config'].get(key), 'CONTAINER_CONFIGURATION_DRIFT')
        for key in ('NanoCpus', 'CpuQuota', 'CpuPeriod', 'Memory', 'MemorySwap', 'PidsLimit',
                    'RestartPolicy', 'LogConfig', 'PortBindings', 'Binds', 'Mounts', 'ReadonlyRootfs',
                    'SecurityOpt', 'CapAdd', 'CapDrop', 'NetworkMode'):
            require(after['HostConfig'].get(key) == baseline[container]['HostConfig'].get(key), 'HOST_CONFIGURATION_DRIFT')
        old_networks = baseline[container]['NetworkSettings']['Networks']
        new_networks = after['NetworkSettings']['Networks']
        require(set(old_networks) == set(new_networks) and all(
            new_networks[name]['IPAddress'] == value['IPAddress'] for name, value in old_networks.items()), 'NETWORK_DRIFT')
        for name, value in baseline.items():
            if name != container:
                require(inspect(name)['Id'] == value['Id'], 'UNRELATED_CONTAINER_CHANGED')
        after_queue = inventory(db)
        queue_guard(after_queue, environment == 'production')
        for key in ('uber_orders', 'local_uber_orders', 'uber_print_jobs', 'uber_bindings'):
            require(after_queue[key] == before[key], 'UBER_BUSINESS_SIDE_EFFECT')
        health('https://pos.lanzhounoodlesmtl.com')
        health('https://staging-pos.lanzhounoodlesmtl.com')
        write_private(root / 'queue-after.json', after_queue)
        require(source.read_bytes() == original_bytes, 'SOURCE_MODEL_CHANGED')
        write_private(source, target)
    except Exception:
        try:
            compose(root / 'rollback.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend')
            restored = inspect(container)
            require(restored['Image'] == baseline[container]['Image']
                    and env_of(restored).get('UBER_EATS_ENABLED') == 'false'
                    and env_of(restored).get('UBER_EATS_WORKER_ENABLED') == 'false', 'SAFE_ROLLBACK_FAILED')
            wait_healthy(base)
            write_private(source, rollback)
        except Exception:
            raise PromotionError('SAFE_ROLLBACK_FAILED_OPERATOR_REQUIRED') from None
        raise PromotionError('PROMOTION_FAILED_SAFE_ROLLBACK_APPLIED') from None
    result = {'environment': environment, 'source_sha': sha, 'image': image,
              'worker_enabled': False, 'health': 'PASS', 'accepted': False,
              'uber_enabled': expected_env['UBER_EATS_ENABLED'] == 'true',
              'backup_sha256': hashlib.sha256(backup.read_bytes()).hexdigest(),
              'other_containers_preserved': True,
              'timestamp': datetime.datetime.now(datetime.timezone.utc).isoformat()}
    write_private(root / 'result.json', result)
    if environment == 'production':
        write_private(source.parent / 'release.json', {'source_sha': sha, 'release_dir': str(root), 'compose': source.name})
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('environment', choices=('staging', 'production'))
    parser.add_argument('image')
    parser.add_argument('sha')
    parser.add_argument('--uber-env-file', type=Path)
    parser.add_argument('--current-4483', action='store_true', help='Current V38 backend/default patch; preserve all credentials and worker=false')
    parser.add_argument('--current-4483-acceptance', type=Path, help='Reviewed exact-image Staging 300-preview acceptance for the current patch')
    parser.add_argument('--record-staging-acceptance', type=Path,
                        help='Validate operator HTTP proof plus current held receipt; do not deploy')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        require(args.current_4483 or not args.current_4483_acceptance, 'CURRENT_4483_ARGUMENTS_INVALID')
        if args.current_4483:
            require(not args.uber_env_file and not args.record_staging_acceptance, 'CURRENT_4483_ARGUMENTS_INVALID')
            result = patch_current_4483(args.environment, args.image, args.sha, args.current_4483_acceptance)
        elif args.record_staging_acceptance:
            require(args.environment == 'staging' and not args.uber_env_file, 'ACCEPTANCE_ARGUMENTS_INVALID')
            result = record_staging_acceptance(args.image, args.sha, args.record_staging_acceptance)
        else:
            require(not args.current_4483_acceptance, 'CURRENT_4483_ARGUMENTS_INVALID')
            result = promotion(args.environment, args.image, args.sha, args.uber_env_file)
    except Exception as error:
        # Never emit subprocess args/output, JSON values, private paths or exception text.
        print('UBER_RECEIVE_ONLY_PROMOTION_FAILED:' +
              (str(error) if isinstance(error, PromotionError) else type(error).__name__))
        return 1
    print(json.dumps(result))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
