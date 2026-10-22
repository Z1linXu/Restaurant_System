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
    parser.add_argument('--record-staging-acceptance', type=Path,
                        help='Validate operator HTTP proof plus current held receipt; do not deploy')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if args.record_staging_acceptance:
            require(args.environment == 'staging' and not args.uber_env_file, 'ACCEPTANCE_ARGUMENTS_INVALID')
            result = record_staging_acceptance(args.image, args.sha, args.record_staging_acceptance)
        else:
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
