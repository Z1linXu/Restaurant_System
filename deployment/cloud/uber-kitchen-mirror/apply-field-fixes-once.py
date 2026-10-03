#!/usr/bin/env python3
"""Owner-authorized Store 1 field repair: preserve TEST credentials, PAD endpoints and in-flight identities."""
import fcntl
import importlib.util
import json
import os
import pathlib
import re
import subprocess
import sys

sys.dont_write_bytecode = True
HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('ingress', HERE.parent / 'staging-ingress/apply-once.py')
i = importlib.util.module_from_spec(spec)
spec.loader.exec_module(i)
D = 'restaurant-pos-staging-db-1'
OLD = {
    'backend': 'sha256:1324bd845bac5c702f1641c859a394c54ae3c7f3e07c192f2eae01e4cb370531',
    'nginx': 'sha256:1f27422fe342d2a2e8d05fb5703006ddddf43ba2cb434037f01a816b4ab0440b',
}
ROOT = pathlib.Path('/srv/restaurant-pos/staging/uber-kitchen-field-fixes-20261002')
os.umask(0o077)


def queue_guard():
    # Existing pending jobs are incident evidence, not a reason to delete/requeue them.
    # Only the observed legacy PRINTING jobs may be active at the deploy boundary.
    sql = """BEGIN READ ONLY;
SELECT (SELECT count(*) FROM stores WHERE id=1 AND code='STG005_SRC_20260809_R01' AND organization_id=1 AND printing_mode='PAD_DIRECT'),
 (SELECT count(*) FROM uber_eats_store_mappings WHERE store_id=1 AND organization_id=1 AND uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273' AND processing_mode='KITCHEN_MIRROR' AND enabled=true),
 (SELECT count(*) FROM print_jobs WHERE status IN ('CLAIMED','PRINTING') AND id NOT IN (200,205,207)),
 (SELECT count(*) FROM printer_configs WHERE id=13 AND store_id=1 AND ip_address='192.168.12.19' AND port=9100);
ROLLBACK;"""
    out = i.run(['docker', 'exec', '-i', D, 'sh', '-c',
        'psql -X -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], input=sql.encode()).decode()
    assert out.strip().splitlines() == ['BEGIN', '1|1|0|1', 'ROLLBACK'], 'Target/device queue drift requires inspection'



def modeled(service, inspected):
    previous = i.SIMG[service]
    try:
        i.SIMG[service] = inspected['Image']
        return i.model(service, inspected)
    finally:
        i.SIMG[service] = previous


def main():
    sha = sys.argv[1]
    assert re.fullmatch('[0-9a-f]{40}', sha)
    assert pathlib.Path(i.run(['git', '-C', str(HERE), 'rev-parse', '--show-toplevel']).decode().strip()).resolve() == pathlib.Path('/srv/restaurant-pos/staging/releases') / sha
    assert i.run(['git', '-C', str(HERE), 'rev-parse', 'HEAD']).decode().strip() == sha
    assert not i.run(['git', '-C', str(HERE), 'status', '--porcelain', '--untracked-files=normal']).strip()
    before = {s: i.inspect('restaurant-pos-staging-' + s + '-1') for s in OLD}
    for s in OLD:
        assert before[s]['Image'] == OLD[s], 'Unexpected old image'
    env = dict(x.split('=', 1) for x in before['backend']['Config']['Env'])
    assert env['APP_ENVIRONMENT'] == 'staging' and env['UBER_EATS_ENVIRONMENT'] == 'sandbox'
    assert env['UBER_EATS_CLIENT_ID'] == 't86ofdunSsVjL-0AvK6MA6LCTyF2eYLf'
    assert env.get('UBER_EATS_WEBHOOK_SIGNING_KEY'), 'Explicit TEST signing key missing'
    queue_guard()
    assert not ROOT.exists(), 'Existing evidence must not be overwritten'
    ROOT.mkdir(mode=0o700)
    baseline = {'fingerprints': {n: i.fingerprint(n) for n in i.PROD + [D]},
                'staging_flyway': i.ledger(D), 'production_flyway': i.ledger('cloud-db-1')}
    assert baseline['staging_flyway'].strip().splitlines()[-1].startswith('32|')
    i.write_json(ROOT / 'baseline.private.json', baseline)
    with (ROOT / 'staging-before.dump').open('wb') as f:
        subprocess.run(['docker', 'exec', D, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], stdout=f, check=True)
    assert (ROOT / 'staging-before.dump').stat().st_size > 0
    # Validate archive readability without restoring or touching another database.
    with (ROOT / 'staging-before.dump').open('rb') as f:
        i.run(['docker', 'exec', '-i', D, 'pg_restore', '--list'], stdin=f)
    target = {'services': {s: modeled(s, before[s]) for s in OLD},
              'networks': {'restaurant-pos': {'external': True, 'name': 'restaurant-pos-staging_restaurant-pos'}}}
    i.write_compose(ROOT / 'rollback.private.json', target)
    for service, wanted in target['services'].items():
        name = 'restaurant-pos-' + ('frontend' if service == 'nginx' else service) + ':staging-' + sha
        image = json.loads(i.run(['docker', 'image', 'inspect', name]))[0]
        assert image['Config']['Labels']['org.opencontainers.image.revision'] == sha
        wanted['image'] = image['Id']
        wanted['labels'].update({k: v for k, v in (image['Config'].get('Labels') or {}).items()
                                if not k.startswith('com.docker.compose.')})
    i.write_compose(ROOT / 'target.private.json', target)
    actual = json.loads(i.compose(ROOT / 'target.private.json', 'config', '--format', 'json'))
    assert set(actual['services']) == set(OLD)
    for service, wanted in target['services'].items():
        got = actual['services'][service]
        for key in ['image', 'environment', 'entrypoint', 'command', 'logging', 'restart', 'labels', 'networks']:
            assert got[key] == i.escape_compose(wanted[key]), 'Compose delta: ' + service + ' ' + key
        for key in ['mem_limit', 'memswap_limit']:
            assert int(got[key]) == wanted[key]
        assert float(got['cpus']) == wanted['cpus']
        if service == 'backend':
            assert not got.get('ports') and not got.get('volumes')
        else:
            assert len(got['ports']) == 1 and got['ports'][0]['host_ip'] == '127.0.0.1' and str(got['ports'][0]['published']) == '18080' and got['ports'][0]['target'] == 80
            assert len(got['volumes']) == 1 and all(got['volumes'][0][k] == wanted['volumes'][0][k] for k in ['type', 'source', 'target', 'read_only'])

    def continuity():
        for n in i.PROD + [D]:
            assert i.fingerprint(n) == baseline['fingerprints'][n], 'Unrelated container changed: ' + n
        assert i.ledger('cloud-db-1') == baseline['production_flyway']
        i.health('http://127.0.0.1')

    queue_guard()
    try:
        for service in ['backend', 'nginx']:
            i.compose(ROOT / 'target.private.json', 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', service)
            i.wait_health()
        for service, wanted in target['services'].items():
            assert modeled(service, i.inspect('restaurant-pos-staging-' + service + '-1')) == wanted
        ledger = i.ledger(D)
        assert ledger.startswith(baseline['staging_flyway'])
        delta = ledger[len(baseline['staging_flyway']):].strip().splitlines()
        assert len(delta) == 1 and delta[0].startswith('33|') and 'V33__external_kitchen_fallback_snapshot.sql' in delta[0], 'Unexpected migration delta'
        continuity()
    except BaseException:
        # Additive V33 can remain; never restore/drop database data automatically.
        queue_guard()
        i.compose(ROOT / 'rollback.private.json', 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'backend', 'nginx')
        i.wait_health()
        for service in OLD:
            assert modeled(service, i.inspect('restaurant-pos-staging-' + service + '-1')) == modeled(service, before[service])
        continuity()
        raise RuntimeError('Staging update failed; previous application restored; additive schema retained') from None
    print('STAGING_APPLICATION_UPDATE=PASS; FLYWAY_V33=PASS; DB_CONTAINER_PRODUCTION_UNCHANGED=PASS')


if __name__ == '__main__':
    locks = []
    for name in ['/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock', '/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock']:
        path = pathlib.Path(name)
        assert path.is_file() and not path.is_symlink()
        handle = path.open('r+')
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        locks.append(handle)
    try:
        main()
    except Exception as ex:
        print('STAGING_UPDATE_FAILED:' + type(ex).__name__, file=sys.stderr)
        sys.exit(1)
