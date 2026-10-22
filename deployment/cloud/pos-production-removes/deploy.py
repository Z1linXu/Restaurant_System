#!/usr/bin/env python3
"""Owner-authorized 2026-10-22 POS/Remove batch, using existing runtime models.

Build once on Staging; promote those exact images after acceptance. Only app
images/OCI labels change. Existing Compose configuration and DB containers stay.
No business API calls, hardware calls, queue replay or database restore.
"""
import copy
import datetime
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
BASE = Path('/srv/restaurant-pos')
spec = importlib.util.spec_from_file_location('pos_five_deployment', HERE.parent / 'pos-five-fixes/deploy-once.py')
p = importlib.util.module_from_spec(spec)
spec.loader.exec_module(p)
i = p.i
spec = importlib.util.spec_from_file_location('existing_promotion', HERE.parent / 'promote-uber-receive-only-20261022.py')
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)
os.umask(0o077)
SERVICES = ('backend', 'nginx')
BASELINES = {
    'staging': ('fbcdb4f8152183b62b6ecc3859e2d5a689d01d49', 'fbcdb4f8152183b62b6ecc3859e2d5a689d01d49'),
    'production': ('7d573be03d66a9505e0c68f92fa17829247f56e3', '8c3a9e356f59cd025beff25f7fb1d5a20594e028'),
}


def sql(db, query):
    return i.run(['docker', 'exec', '-i', '-e',
        'PGOPTIONS=-c default_transaction_read_only=on -c statement_timeout=10000',
        db, 'sh', '-c', 'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], input=query.encode())


def queues(db):
    result = json.loads(sql(db, """SELECT json_build_object(
      'printing',(select coalesce(json_agg(id order by id),'[]') from print_jobs where status in ('CLAIMED','PRINTING')),
      'pending_print',(select count(*) from print_jobs where status='PENDING'),
      'outbox',(select count(*) from order_dispatch_outbox where status in ('PENDING','PROCESSING')),
      'uber_pending',(select count(*) from uber_eats_events where status='PENDING'))"""))
    assert result['pending_print'] == result['outbox'] == result['uber_pending'] == 0, 'Pending queue requires inspection'
    assert result['printing'] == ([200, 205, 207] if db.startswith('restaurant-pos-staging') else [1176, 3169, 9226, 20391]), 'Active printing drift'
    return result


def revision(row):
    return i.inspect(row['Image'])['Config'].get('Labels', {}).get('org.opencontainers.image.revision')


def wait_health(url):
    for _ in range(45):
        try:
            i.health(url)
            return
        except Exception:
            time.sleep(2)
    raise RuntimeError('Application health timeout')


def main(environment, sha):
    assert environment in BASELINES and re.fullmatch('[0-9a-f]{40}', sha)
    release = BASE / 'staging/releases' / sha
    assert HERE == release / 'deployment/cloud/pos-production-removes'
    assert i.run(['git', '-C', str(release), 'rev-parse', 'HEAD']).decode().strip() == sha
    assert not i.run(['git', '-C', str(release), 'status', '--porcelain']).strip()
    prod = environment == 'production'
    project = 'cloud' if prod else 'restaurant-pos-staging'
    db = project + '-db-1'
    url = 'https://' + ('pos' if prod else 'staging-pos') + '.lanzhounoodlesmtl.com'
    before = {s: i.inspect(project + '-' + s + '-1') for s in SERVICES}
    assert tuple(revision(before[s]) for s in SERVICES) == BASELINES[environment], 'Application baseline drift'
    env = dict(v.split('=', 1) for v in before['backend']['Config']['Env'])
    assert env['APP_ENVIRONMENT'] == environment
    assert env.get('UBER_EATS_WORKER_ENABLED') == 'false', 'Worker policy drift'
    stage_db, production_db = i.inspect('restaurant-pos-staging-db-1'), i.inspect('cloud-db-1')
    assert stage_db['Id'] != production_db['Id']
    assert stage_db['Mounts'][0]['Source'] != production_db['Mounts'][0]['Source']
    assert set(stage_db['NetworkSettings']['Networks']).isdisjoint(production_db['NetworkSettings']['Networks'])
    if prod:
        hosts = {'db', 'cloud-db-1', production_db['NetworkSettings']['Networks']['cloud_restaurant-pos']['IPAddress']}
        assert list(before['backend']['NetworkSettings']['Networks']) == ['cloud_restaurant-pos']
        db_env = dict(v.split('=', 1) for v in production_db['Config']['Env'])
        assert env['DB_NAME'] == db_env['POSTGRES_DB'] == 'restaurant_pos' and env['DB_HOST'] in hosts and env['DB_PORT'] == '5432'
        if env.get('SPRING_DATASOURCE_URL'):
            match = re.fullmatch(r'jdbc:postgresql://([^/:?]+):5432/restaurant_pos(?:\?[^#]*)?', env['SPRING_DATASOURCE_URL'])
            assert match and match.group(1) in hosts, 'Production datasource override mismatch'
        assert not env.get('SPRING_APPLICATION_JSON'), 'Unreviewed Spring JSON override'
        for key in ('SPRING_CONFIG_LOCATION','SPRING_CONFIG_ADDITIONAL_LOCATION','SPRING_CONFIG_IMPORT'):
            assert not env.get(key), 'Unreviewed external Spring config'
        overrides = ' '.join(env.get(key, '') for key in ('JAVA_OPTS','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS'))
        overrides += json.dumps([before['backend']['Config'].get('Entrypoint'), before['backend']['Config'].get('Cmd')])
        assert 'spring.datasource' not in overrides.lower() and 'spring.config' not in overrides.lower(), 'Unreviewed launch config override'
        assert env.get('UBER_EATS_ENVIRONMENT') == 'production'
    else:
        p.require_staging_database(env, before['backend'], stage_db, production_db)
    queues(db)
    root = BASE / environment / ('pos-removes-' + sha)
    assert not root.exists(), 'Release evidence already exists'
    root.mkdir(mode=0o700)
    names = i.PROD + i.STAGE
    baseline = {'fingerprints': {n: i.fingerprint(n) for n in names}, 'ledger': i.ledger(db)}
    assert baseline['ledger'].splitlines()[-1].startswith(('37|' if prod else '38|'))
    baseline['queues'] = queues(db)
    baseline['protected_jobs'] = sql(db, "SELECT coalesce(json_agg(t order by id),'[]') from (select * from print_jobs where status in ('CLAIMED','PRINTING')) t").decode()
    p.DB = db
    baseline['history'] = p.history_fingerprints()
    i.write_json(root / 'baseline.private.json', baseline)
    source = BASE / 'production/current.compose.json'
    if prod:
        assert source.is_file() and not source.is_symlink() and source.stat().st_mode & 0o777 == 0o600
        original_bytes = source.read_bytes()
        rollback = json.loads(original_bytes)  # Already Compose-escaped private model.
    else:
        rollback = {'services': {s: p.modeled(s, before[s]) for s in SERVICES},
                    'networks': {'restaurant-pos': {'external': True, 'name': 'restaurant-pos-staging_restaurant-pos'}}}
        rollback = i.escape_compose(rollback)
    assert set(rollback['services']) == set(SERVICES)
    i.write_json(root / 'rollback.private.json', rollback)

    def compose(path, *args):
        return i.run(['docker', 'compose', '-p', project, '-f', str(path), *args], stderr=subprocess.STDOUT)

    old_resolved = json.loads(compose(root / 'rollback.private.json', 'config', '--format', 'json'))
    for s in SERVICES:
        assert old_resolved['services'][s]['image'] == before[s]['Image']
        assert old_resolved['services'][s]['environment'] == dict(v.split('=', 1) for v in before[s]['Config']['Env'])
    with (root / 'database-before.dump').open('xb') as out:
        subprocess.run(['docker', 'exec', db, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'], stdout=out, check=True, timeout=180)
    with (root / 'database-before.dump').open('rb') as data:
        toc = i.run(['docker', 'exec', '-i', db, 'pg_restore', '--list'], stdin=data)
        assert b'public orders' in toc and b'public print_jobs' in toc and b'public menu_item_options' in toc
    # Scoped current menu/rules backup is a read-only extraction, kept private.
    scope_ids = '1,2,3' if prod else '18'
    scope = sql(db, f"""SELECT json_build_object(
      'stores',(select json_agg(t) from (select * from stores where id in ({scope_ids})) t),
      'options',(select json_agg(t) from (select o.* from menu_item_options o join menu_items m on m.id=o.menu_item_id where m.store_id in ({scope_ids})) t),
      'rules',(select json_agg(t) from (select r.* from printing_display_rule_revisions r join printing_display_rule_sets s on s.id=r.rule_set_id where s.store_id in ({scope_ids})) t))""")
    (root / 'scoped-menu-rules-before.private.json').write_bytes(scope)
    if not prod:
        p.build_images(release, sha, root)
    else:
        accepted = json.loads((BASE / 'staging' / ('pos-removes-' + sha) / 'acceptance.json').read_text())
        assert accepted['accepted'] is True and accepted['source_sha'] == sha and accepted['health'] == 'PASS'
    target = copy.deepcopy(rollback)
    images = {}
    for s in SERVICES:
        tag = 'restaurant-pos-' + ('backend' if s == 'backend' else 'frontend') + ':staging-' + sha
        image = i.inspect(tag)
        assert image['Config']['Labels']['org.opencontainers.image.revision'] == sha
        images[s] = image['Id']
        if prod:
            assert i.inspect('restaurant-pos-staging-' + s + '-1')['Image'] == image['Id'], 'Accepted artifact drift'
            assert accepted['images'][s] == image['Id']
        target['services'][s]['image'] = image['Id']
        target['services'][s].setdefault('labels', {}).update({k: v for k, v in image['Config']['Labels'].items() if not k.startswith('com.docker.compose.')})
    i.write_json(root / 'target.private.json', target)
    resolved = json.loads(compose(root / 'target.private.json', 'config', '--format', 'json'))
    left, right = copy.deepcopy(old_resolved), copy.deepcopy(resolved)
    for model in (left, right):
        for s in SERVICES:
            model['services'][s].pop('image')
            model['services'][s].pop('labels', None)
    assert left == right, 'Unapproved Compose configuration delta'
    if prod:
        assert source.read_bytes() == original_bytes
    queues(db)
    def continuity():
        for n in names:
            if n not in [project + '-' + s + '-1' for s in SERVICES]:
                assert i.fingerprint(n) == baseline['fingerprints'][n], 'Unrelated container changed'
        assert p.history_fingerprints(baseline['history']['cutoffs']) == baseline['history'], 'Historical snapshot changed'
        assert queues(db) == baseline['queues'], 'Queue drift'
        assert sql(db, "SELECT coalesce(json_agg(t order by id),'[]') from (select * from print_jobs where status in ('CLAIMED','PRINTING')) t").decode() == baseline['protected_jobs'], 'Protected print job changed'
        ledger = i.ledger(db)
        assert all(line in ledger.splitlines() for line in baseline['ledger'].splitlines()), 'Existing Flyway ledger changed'
        assert ledger.splitlines()[-1].startswith('38|'), 'Expected V38 migration'
    try:
        for s in SERVICES:
            compose(root / 'target.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', s)
            wait_health(url)
        for s in SERVICES:
            actual = i.inspect(project + '-' + s + '-1')
            assert actual['Image'] == images[s]
            assert dict(v.split('=', 1) for v in actual['Config']['Env']) == dict(v.split('=', 1) for v in before[s]['Config']['Env']), 'Runtime environment drift'
            for k in ('Entrypoint', 'Cmd', 'User', 'WorkingDir'):
                assert actual['Config'].get(k) == before[s]['Config'].get(k), 'Runtime config drift'
            for k in ('Memory','MemorySwap','NanoCpus','PortBindings','Binds','RestartPolicy','LogConfig'):
                assert actual['HostConfig'].get(k) == before[s]['HostConfig'].get(k), 'Host config drift'
            assert {n: v['IPAddress'] for n,v in actual['NetworkSettings']['Networks'].items()} == {n: v['IPAddress'] for n,v in before[s]['NetworkSettings']['Networks'].items()}
        continuity()
        i.health('https://pos.lanzhounoodlesmtl.com')
        i.health('https://staging-pos.lanzhounoodlesmtl.com')
        if prod:
            assert source.read_bytes() == original_bytes
            ops.write_private(source, target)
    except BaseException:
        compose(root / 'rollback.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', *SERVICES)
        wait_health(url)
        if prod:
            ops.write_private(source, rollback)
        raise RuntimeError('Application rollback applied; additive schema retained; database NOT restored') from None
    result = {'environment': environment, 'source_sha': sha, 'images': images, 'health': 'PASS', 'flyway': '38',
              'deployed_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'accepted': False,
              'backup_sha256': hashlib.sha256((root / 'database-before.dump').read_bytes()).hexdigest(),
              'history_unchanged': True, 'unrelated_containers_unchanged': True}
    i.write_json(root / 'result.json', result)
    if prod:
        ops.write_private(source.parent / 'release.json', {'source_sha': sha, 'release_dir': str(root), 'compose': source.name})
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    locks = []
    for filename in ('/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock',
                     '/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock'):
        handle = open(filename, 'r+')
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        locks.append(handle)
    try:
        main(*sys.argv[1:])
    except Exception as error:
        print('POS_REMOVE_DEPLOY_FAILED:' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
