#!/usr/bin/env python3
"""Bounded Staging-only application update; reuses the existing ingress Compose model.

No Production lifecycle/config writes, database restore, queue cleanup, endpoint
changes or business API calls. Private snapshots and build logs remain server-side.
"""
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

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
STAGING = Path('/srv/restaurant-pos/staging')
DB = 'restaurant-pos-staging-db-1'
OLD = {
    'backend': 'sha256:573dc7d9979455d52c46de650e8ec8a4a7d0b4feb4c21823e7acf55a918625b2',
    'nginx': 'sha256:735bf29313c5096a3e1961ed7cff9a48c59e1adc406a92ff415ee2e195589fcc',
}
spec = importlib.util.spec_from_file_location('staging_ingress_model', HERE.parent / 'staging-ingress/apply-once.py')
i = importlib.util.module_from_spec(spec)
spec.loader.exec_module(i)
os.umask(0o077)


def modeled(service, row):
    i.SIMG[service] = row['Image']
    return i.model(service, row)


def require_staging_database(env, backend, database, production_database):
    network = 'restaurant-pos-staging_restaurant-pos'
    assert list(backend['NetworkSettings']['Networks']) == [network]
    assert list(database['NetworkSettings']['Networks']) == [network]
    assert network not in production_database['NetworkSettings']['Networks']
    # Check application-cloud.yml inputs and any higher-priority explicit URL
    # before ANY image starts/Flyway runs. Never print credentials.
    address = database['NetworkSettings']['Networks'][network]['IPAddress']
    hosts = {'db', DB, address}
    database_env = dict(value.split('=', 1) for value in database.get('Config', {}).get('Env', []))
    assert env.get('DB_HOST') in hosts and env.get('DB_PORT') == '5432', 'Staging database host mismatch'
    assert env.get('DB_NAME') == database_env.get('POSTGRES_DB') == 'restaurant_pos_staging', 'Staging database name mismatch'
    if env.get('SPRING_DATASOURCE_URL'):
        match = re.fullmatch(r'jdbc:postgresql://([^/:?]+):5432/restaurant_pos_staging(?:\?[^#]*)?', env['SPRING_DATASOURCE_URL'])
        assert match and match.group(1) in hosts, 'Staging datasource override mismatch'
    assert not env.get('SPRING_APPLICATION_JSON'), 'Unreviewed Spring JSON override'
    for key in ['SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT']:
        assert not env.get(key), 'Unreviewed external Spring configuration'
    for key in ['JAVA_OPTS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS']:
        value = env.get(key, '').lower()
        assert 'spring.datasource' not in value and 'spring.config' not in value, 'Unreviewed Java datasource override'
    launch = json.dumps([backend['Config'].get('Entrypoint'), backend['Config'].get('Cmd')]).lower()
    assert 'spring.datasource' not in launch and 'spring.config' not in launch, 'Unreviewed launch override'


def run_logged(argv, path, timeout=1200):
    with path.open('wb') as output:
        subprocess.run(argv, stdout=output, stderr=subprocess.STDOUT, check=True, timeout=timeout)


def history_fingerprints(cutoffs=None):
    command = ['docker', 'exec', '-i', '-e', 'PGOPTIONS=-c default_transaction_read_only=on -c statement_timeout=5000',
               DB, 'sh', '-c', 'psql -X -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"']
    if cutoffs is None:
        values = i.run(command, input=b'SELECT coalesce(max(id),0) FROM orders; SELECT coalesce(max(id),0) FROM print_jobs;').decode().splitlines()
        cutoffs = [int(value) for value in values]
    order, job = cutoffs
    sql = f"""
SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM
 (SELECT id,subtotal_amount,total_amount,discount_amount FROM orders WHERE id<={order}) t;
SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM
 (SELECT id,order_id,menu_item_id,unit_price,line_amount,quantity,notes,item_name_snapshot_zh,item_name_snapshot_en
 FROM order_items WHERE order_id<={order}) t;
SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM
 (SELECT p.* FROM order_item_options p JOIN order_items o ON o.id=p.order_item_id WHERE o.order_id<={order}) t;
SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM
 (SELECT id,item_name_snapshot_zh,item_name_snapshot_en,special_instructions_snapshot FROM kitchen_tasks WHERE order_id<={order}) t;
SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM
 (SELECT id,rendered_text_snapshot,payload_snapshot,escpos_payload_base64,printing_rule_revision_id,printing_rule_fingerprint FROM print_jobs WHERE id<={job}) t;
"""
    hashes = i.run(command, input=sql.encode()).decode().splitlines()
    assert len(hashes) == 5 and all(re.fullmatch('[0-9a-f]{32}', value) for value in hashes)
    return {'cutoffs': cutoffs, 'hashes': hashes}


def build_images(release, sha, evidence):
    # A disposable builder bounds this shared host's build memory and CPU. Removing
    # only this newly created builder does not prune shared images/cache/volumes.
    builder = 'pos-five-' + sha[:12]
    created = False
    try:
        run_logged(['docker', 'buildx', 'create', '--name', builder, '--driver', 'docker-container',
                    '--driver-opt', 'memory=1g,memory-swap=1g,cpu-period=100000,cpu-quota=100000',
                    '--bootstrap'], evidence / 'builder.private.log')
        created = True
        for service, folder in [('backend', 'backend'), ('nginx', 'frontend')]:
            tag = 'restaurant-pos-' + folder + ':staging-' + sha
            args = ['docker', 'buildx', 'build', '--builder', builder, '--load', '--provenance=false',
                    '--label', 'org.opencontainers.image.revision=' + sha, '-t', tag]
            if service == 'nginx': args += ['--build-arg', 'VITE_APP_BUILD_VERSION=' + sha]
            run_logged(args + [str(release / folder)], evidence / (service + '-build.private.log'))
            print('STAGING_' + service.upper() + '_IMAGE_BUILT', flush=True)
    finally:
        if created:
            run_logged(['docker', 'buildx', 'rm', builder], evidence / 'builder-remove.private.log', 120)


def main(sha):
    assert re.fullmatch('[0-9a-f]{40}', sha), 'Exact SHA required'
    release = STAGING / 'releases' / sha
    assert HERE == release / 'deployment/cloud/pos-five-fixes', 'Run exact Staging release only'
    assert i.run(['git', '-C', str(release), 'rev-parse', 'HEAD']).decode().strip() == sha
    assert not i.run(['git', '-C', str(release), 'status', '--porcelain']).strip(), 'Release must be clean'
    before = {s: i.inspect('restaurant-pos-staging-' + s + '-1') for s in OLD}
    assert all(before[s]['Image'] == OLD[s] for s in OLD), 'Pre-deploy application drift'
    env = dict(value.split('=', 1) for value in before['backend']['Config']['Env'])
    assert env.get('APP_ENVIRONMENT') == 'staging' and env.get('UBER_EATS_ENVIRONMENT') == 'sandbox'
    assert env.get('APP_AUTH_X_USER_ID_FALLBACK_ENABLED') == 'false'
    database, production_database = i.inspect(DB), i.inspect('cloud-db-1')
    require_staging_database(env, before['backend'], database, production_database)
    assert database['Mounts'][0]['Source'] == '/srv/restaurant-pos/staging/state/postgres'
    assert production_database['Mounts'][0]['Source'] != database['Mounts'][0]['Source']
    queue = i.run(['docker', 'exec', DB, 'sh', '-c',
        'PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=5000" psql -X -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT count(*) FROM print_jobs WHERE status IN (\'CLAIMED\',\'PRINTING\') AND id NOT IN (200,205,207)"'])
    assert queue.strip() == b'0', 'New active printing requires inspection'
    evidence = STAGING / ('pos-five-release-' + sha)
    assert not evidence.exists(), 'Do not overwrite release evidence'
    evidence.mkdir(mode=0o700)
    baseline = {'fingerprints': {n: i.fingerprint(n) for n in i.PROD + [DB]},
                'staging_flyway': i.ledger(DB), 'production_flyway': i.ledger('cloud-db-1')}
    baseline['history'] = history_fingerprints()
    assert baseline['staging_flyway'].strip().splitlines()[-1].startswith('38|V38__add_store_remove_catalog.sql|')
    i.write_json(evidence / 'baseline.private.json', baseline)
    rollback = {'services': {s: modeled(s, before[s]) for s in OLD},
                'networks': {'restaurant-pos': {'external': True, 'name': 'restaurant-pos-staging_restaurant-pos'}}}
    i.write_compose(evidence / 'rollback.private.json', rollback)
    with (evidence / 'staging-before.dump').open('wb') as output:
        subprocess.run(['docker', 'exec', DB, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], stdout=output, check=True, timeout=120)
    with (evidence / 'staging-before.dump').open('rb') as source:
        toc = i.run(['docker', 'exec', '-i', DB, 'pg_restore', '--list'], stdin=source)
        assert b'public orders' in toc and b'public print_jobs' in toc
    build_images(release, sha, evidence)
    target = json.loads(json.dumps(rollback))
    for service, wanted in target['services'].items():
        tag = 'restaurant-pos-' + ('frontend' if service == 'nginx' else 'backend') + ':staging-' + sha
        image = json.loads(i.run(['docker', 'image', 'inspect', tag]))[0]
        assert image['Config']['Labels']['org.opencontainers.image.revision'] == sha
        wanted['image'] = image['Id']
        wanted['labels'].update({key: value for key, value in (image['Config'].get('Labels') or {}).items()
                                 if not key.startswith('com.docker.compose.')})
    i.write_compose(evidence / 'target.private.json', target)
    actual = json.loads(i.compose(evidence / 'target.private.json', 'config', '--format', 'json'))
    assert set(actual['services']) == set(OLD)
    for service, wanted in target['services'].items():
        got = actual['services'][service]
        for key in ['image', 'environment', 'entrypoint', 'command', 'logging', 'restart', 'labels', 'networks']:
            assert got.get(key) == i.escape_compose(wanted[key]), 'Unexpected Compose change: ' + service + ' ' + key
        for key in ['mem_limit', 'memswap_limit']: assert int(got[key]) == wanted[key]
        assert float(got['cpus']) == wanted['cpus']
        if service == 'backend': assert not got.get('ports') and not got.get('volumes')
        else:
            assert len(got['ports']) == 1 and got['ports'][0]['host_ip'] == '127.0.0.1' and str(got['ports'][0]['published']) == '18080'
            assert got['volumes'][0]['source'] == '/srv/restaurant-pos/staging/ingress-20260930/staging.conf.template'

    def continuity():
        for name in i.PROD + [DB]: assert i.fingerprint(name) == baseline['fingerprints'][name], 'Unrelated container changed'
        assert i.ledger('cloud-db-1') == baseline['production_flyway']
        assert history_fingerprints(baseline['history']['cutoffs']) == baseline['history'], 'Historical snapshots changed'
        i.health('http://127.0.0.1')

    continuity()
    try:
        for service in ['backend', 'nginx']:
            i.compose(evidence / 'target.private.json', 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', service)
            i.wait_health()
        for service, wanted in target['services'].items():
            assert modeled(service, i.inspect('restaurant-pos-staging-' + service + '-1')) == wanted, 'Runtime model differs'
        ledger = i.ledger(DB)
        assert ledger == baseline['staging_flyway'], 'V38 repair must not alter the migration ledger'
        continuity()
    except BaseException:
        i.compose(evidence / 'rollback.private.json', 'up', '-d', '--no-deps', '--no-build', '--pull', 'never', 'backend', 'nginx')
        i.wait_health()
        continuity()
        raise RuntimeError('Staging application restored; additive schema retained') from None
    i.write_json(evidence / 'result.json', {'result': 'PASS', 'environment': 'staging', 'source_sha': sha,
        'frontend_image': target['services']['nginx']['image'], 'backend_image': target['services']['backend']['image'],
        'flyway': '38', 'deployed_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'backup_sha256': hashlib.sha256((evidence / 'staging-before.dump').read_bytes()).hexdigest(),
        'production_unchanged': True, 'database_container_unchanged': True})
    print('STAGING_DEPLOYMENT=PASS; FLYWAY_V38=PASS; PRODUCTION_UNCHANGED=PASS', flush=True)


if __name__ == '__main__':
    locks = []
    for filename in ['/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock',
                     '/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock']:
        path = Path(filename)
        assert path.is_file() and not path.is_symlink()
        handle = path.open('r+')
        fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        locks.append(handle)
    try:
        main(sys.argv[1])
    except Exception as error:
        print('STAGING_UPDATE_FAILED:' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
