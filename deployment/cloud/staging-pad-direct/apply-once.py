#!/usr/bin/env python3
"""Owner-authorized, config-only Staging endpoint policy batch, 2026-09-30."""
import fcntl
import importlib.util
import json
import os
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ingress', HERE.parent / 'staging-ingress/apply-once.py')
i = importlib.util.module_from_spec(spec)
spec.loader.exec_module(i)
ROOT = pathlib.Path('/srv/restaurant-pos/staging/pad-direct-endpoints-20260930')
BACKEND = 'restaurant-pos-staging-backend-1'
UNCHANGED = i.PROD + ['restaurant-pos-staging-nginx-1', 'restaurant-pos-staging-db-1']
POLICY = json.loads((HERE / 'backend-environment.json').read_text())
assert POLICY == {'APP_PRINTING_ALLOWED_MODES': 'DISABLED,MOCK,PAD_DIRECT',
                  'APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED': 'true'}


def guard(d):
    env = dict(v.split('=', 1) for v in d['Config']['Env'])
    assert env['APP_ENVIRONMENT'] == 'staging'
    assert env['SPRING_PROFILES_ACTIVE'] == 'cloud'
    assert env['APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED'] in ['false', 'true']
    assert env['APP_FEATURES_PRINTING'] == 'true'
    assert env['APP_PRINTING_ALLOWED_MODES'] == POLICY['APP_PRINTING_ALLOWED_MODES']
    assert d['Image'] == i.SIMG['backend']
    return env


def verify(original=False):
    i.wait_health()
    baseline = json.loads((ROOT / 'baseline.private.json').read_text())
    for name in UNCHANGED:
        assert i.fingerprint(name) == baseline['fingerprints'][name], name + ' changed'
    for name, expected in baseline['flyway'].items():
        assert i.ledger(name) == expected, name + ' Flyway changed'
    previous = json.loads((ROOT / 'backend.private.json').read_text())
    current = i.inspect(BACKEND)
    expected = guard(previous)
    if not original:
        expected.update(POLICY)
    assert guard(current) == expected
    # Model compares image, entrypoint, command, resources, networks, labels and logging.
    old_model, new_model = i.model('backend', previous), i.model('backend', current)
    old_model['environment'] = expected
    assert new_model == old_model, 'Unexpected backend configuration change'
    i.health('http://127.0.0.1')
    print('STAGING_HEALTH=UP; UNCHANGED_CONTAINERS=PASS; FLYWAY=UNCHANGED; EXACT_ENV_DELTA=PASS', flush=True)


def queue_preflight():
    sql = """SELECT
      (SELECT count(*) FROM stores WHERE UPPER(TRIM(printing_mode))='PAD_DIRECT' AND id<>18),
      (SELECT count(*) FROM print_jobs WHERE status IN ('PENDING','CLAIMED','PRINTING')),
      (SELECT count(*) FROM order_dispatch_outbox WHERE status NOT IN ('COMPLETED','MOCK_RENDERED','SKIPPED')),
      (SELECT count(*) FROM stores WHERE id=18 AND code='CHINATOWN' AND organization_id=1 AND printing_mode='PAD_DIRECT')"""
    result = i.run(['docker', 'exec', '-i', 'restaurant-pos-staging-db-1', 'sh', '-c',
        'PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=5000" '
        'psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1'], input=sql.encode()).decode().strip()
    assert result == '0|0|0|1', 'Unexpected PAD stores, target or pending work; stop before endpoint enablement'
    print('CHINATOWN_PAD_DIRECT_ONLY; PENDING_WORK=NONE')


def prepare():
    assert not ROOT.exists(), 'Existing backup must not be overwritten'
    queue_preflight()
    previous = i.inspect(BACKEND)
    assert guard(previous)['APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED'] == 'false'
    baseline = {'fingerprints': {n: i.fingerprint(n) for n in UNCHANGED},
                'flyway': {n: i.ledger(n) for n in ['cloud-db-1', 'restaurant-pos-staging-db-1']}}
    assert baseline['flyway']['cloud-db-1'].splitlines()[-1].startswith('28|')
    assert baseline['flyway']['restaurant-pos-staging-db-1'].splitlines()[-1].startswith('30|')
    ROOT.mkdir(mode=0o700)
    i.write_json(ROOT / 'baseline.private.json', baseline)
    i.write_json(ROOT / 'backend.private.json', previous)
    base = {'services': {'backend': i.model('backend', previous)},
            'networks': {'restaurant-pos': {'external': True, 'name': 'restaurant-pos-staging_restaurant-pos'}}}
    i.write_compose(ROOT / 'rollback.private.json', base)
    base['services']['backend']['environment'].update(POLICY)
    i.write_compose(ROOT / 'target.private.json', base)
    for filename in ['rollback.private.json', 'target.private.json']:
        resolved = json.loads(i.compose(ROOT / filename, 'config', '--format', 'json'))
        expected = json.loads((ROOT / filename).read_text())['services']['backend']
        assert set(resolved['services']) == {'backend'}
        actual = resolved['services']['backend']
        for key in ['image', 'environment', 'entrypoint', 'command', 'logging', 'restart', 'labels', 'networks']:
            assert actual.get(key) == expected[key], 'Resolved delta: ' + key
        for key in ['mem_limit', 'memswap_limit']:
            assert int(actual[key]) == expected[key]
        assert float(actual['cpus']) == expected['cpus']
        assert not actual.get('ports') and not actual.get('volumes')
    verify(original=True)
    print('PREPARED_BACKEND_ONLY; NO_BUILD; NO_DATABASE_WRITES')


def apply(original=False):
    # Fail before recreating anything if other services or backend drifted.
    if not original:
        verify(original=True)
        queue_preflight()
    filename = 'rollback.private.json' if original else 'target.private.json'
    try:
        print(i.compose(ROOT / filename, 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'backend').decode())
        verify(original=original)
    except BaseException:
        if not original:
            print(i.compose(ROOT / 'rollback.private.json', 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'backend').decode())
            verify(original=True)
        raise


if __name__ == '__main__':
    os.umask(0o077)
    locks = []
    for name in ['/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock',
                 '/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock']:
        path = pathlib.Path(name)
        assert path.is_file() and not path.is_symlink()
        handle = path.open('r+')
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        locks.append(handle)
    action = sys.argv[1]
    if action == 'prepare':
        prepare()
    elif action == 'apply':
        apply()
    elif action == 'verify':
        verify()
    elif action == 'rollback':
        apply(original=True)
    else:
        raise SystemExit('Unknown action')
