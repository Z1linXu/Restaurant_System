#!/usr/bin/env python3
"""Bounded TEST-only backend image/key update; reuse reviewed ingress model/rollback."""
import fcntl
import importlib.util
import json
import os
import pathlib
import re
import subprocess
import sys

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ingress', HERE.parent / 'staging-ingress/apply-once.py')
i = importlib.util.module_from_spec(spec)
spec.loader.exec_module(i)
B = 'restaurant-pos-staging-backend-1'
D = 'restaurant-pos-staging-db-1'
UNCHANGED = i.PROD + ['restaurant-pos-staging-nginx-1', D]
ROOT = pathlib.Path('/srv/restaurant-pos/staging/uber-signing-key-20261001')
os.umask(0o077)


def queue_guard():
    sql = """BEGIN READ ONLY;
SELECT (SELECT count(*) FROM uber_eats_orders),
 (SELECT count(*) FROM uber_eats_events WHERE status <> 'IGNORED'),
 (SELECT count(*) FROM print_jobs WHERE status IN ('PENDING','CLAIMED','PRINTING')),
 (SELECT count(*) FROM order_dispatch_outbox WHERE status NOT IN ('DISPATCHED','MOCK_RENDERED','SKIPPED')),
 (SELECT count(*) FROM stores WHERE id=1 AND code='STG005_SRC_20260809_R01' AND organization_id=1 AND printing_mode='MOCK');
ROLLBACK;"""
    out = i.run(['docker', 'exec', '-i', D, 'sh', '-c',
        'psql -X -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], input=sql.encode()).decode()
    assert out.strip().splitlines() == ['BEGIN', '0|0|0|0|1', 'ROLLBACK'], 'Pending work or target drift'


def modeled(d):
    old = i.SIMG['backend']
    try:
        i.SIMG['backend'] = d['Image']
        return i.model('backend', d)
    finally:
        i.SIMG['backend'] = old


def main():
    sha = sys.argv[1]
    assert re.fullmatch('[0-9a-f]{40}', sha)
    assert i.run(['git', '-C', str(HERE), 'rev-parse', 'HEAD']).decode().strip() == sha
    assert not i.run(['git', '-C', str(HERE), 'status', '--porcelain', '--untracked-files=normal']).strip()
    previous = i.inspect(B)
    env = dict(x.split('=', 1) for x in previous['Config']['Env'])
    assert env['APP_ENVIRONMENT'] == 'staging' and env['UBER_EATS_ENVIRONMENT'] == 'sandbox'
    assert env['UBER_EATS_CLIENT_ID'] == 't86ofdunSsVjL-0AvK6MA6LCTyF2eYLf'
    assert previous['Image'] == i.SIMG['backend'], 'Unexpected source image'
    private = dict(line.split('=', 1) for line in pathlib.Path('/srv/restaurant-pos/staging/config/.env.staging').read_text().splitlines() if line and not line.startswith('#') and '=' in line)
    key = private['UBER_EATS_WEBHOOK_SIGNING_KEY']
    assert key and all(32 < ord(c) < 127 for c in key)
    target_image = 'restaurant-pos-backend:staging-' + sha
    image = json.loads(i.run(['docker', 'image', 'inspect', target_image]))[0]
    assert image['Config']['Labels']['org.opencontainers.image.revision'] == sha
    queue_guard()
    assert not ROOT.exists(), 'Existing evidence must not be overwritten'
    ROOT.mkdir(mode=0o700)
    baseline = {'fingerprints': {n: i.fingerprint(n) for n in UNCHANGED}, 'flyway': i.ledger(D)}
    i.write_json(ROOT / 'baseline.private.json', baseline)
    i.write_json(ROOT / 'backend.private.json', previous)
    with (ROOT / 'staging-before.dump').open('wb') as f:
        subprocess.run(['docker', 'exec', D, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], stdout=f, check=True)
    assert (ROOT / 'staging-before.dump').stat().st_size > 0
    base = {'services': {'backend': modeled(previous)}, 'networks': {'restaurant-pos': {'external': True, 'name': 'restaurant-pos-staging_restaurant-pos'}}}
    i.write_compose(ROOT / 'rollback.private.json', base)
    wanted = base['services']['backend']
    wanted['image'] = image['Id']
    wanted['labels'].update(image['Config'].get('Labels') or {})
    wanted['environment']['UBER_EATS_WEBHOOK_SIGNING_KEY'] = key
    i.write_compose(ROOT / 'target.private.json', base)
    resolved = json.loads(i.compose(ROOT / 'target.private.json', 'config', '--format', 'json'))
    assert set(resolved['services']) == {'backend'}
    actual = resolved['services']['backend']
    for k in ['image', 'environment', 'entrypoint', 'command', 'logging', 'restart', 'labels', 'networks']:
        assert actual[k] == i.escape_compose(wanted[k]), 'Compose delta: ' + k
    for k in ['mem_limit', 'memswap_limit']:
        assert int(actual[k]) == wanted[k]
    assert float(actual['cpus']) == wanted['cpus']
    assert not actual.get('ports') and not actual.get('volumes')
    def verify(expected):
        i.wait_health()
        assert modeled(i.inspect(B)) == expected, 'Unexpected backend model'
        assert i.ledger(D) == baseline['flyway'], 'Flyway changed'
        for n in UNCHANGED:
            assert i.fingerprint(n) == baseline['fingerprints'][n], 'Unrelated container changed: ' + n
        i.health('http://127.0.0.1')
    queue_guard()
    try:
        i.compose(ROOT / 'target.private.json', 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'backend')
        verify(wanted)
    except BaseException:
        i.compose(ROOT / 'rollback.private.json', 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'backend')
        verify(modeled(previous))
        raise RuntimeError('Staging update failed; previous backend restored') from None
    print('STAGING_BACKEND_UPDATE=PASS; KEY=PRESENT; DB_FRONTEND_PRODUCTION_UNCHANGED=PASS; FLYWAY_UNCHANGED=PASS')


if __name__ == '__main__':
    locks = []
    for name in ['/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock', '/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock']:
        path = pathlib.Path(name)
        assert path.is_file() and not path.is_symlink()
        h = path.open('r+')
        fcntl.flock(h, fcntl.LOCK_EX | fcntl.LOCK_NB)
        locks.append(h)
    try:
        main()
    except Exception as ex:
        print('STAGING_UPDATE_FAILED:' + type(ex).__name__, file=sys.stderr)
        sys.exit(1)
