#!/usr/bin/env python3
"""Restore the retired synthetic Staging and run the reviewed exact app pair."""
import datetime
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
ROOT = Path('/srv/restaurant-pos/staging')
ARCHIVE = Path('/srv/restaurant-pos/retired/20261108/staging')
RESTORE_SHA256 = 'b38b6a8583dac194629996d3cc6ba7aa8da86d46fda7bb6366099743410f4028'
PROJECT = 'restaurant-pos-staging'
NETWORK = PROJECT + '_restaurant-pos'
DB = PROJECT + '-db-1'
RELAY = PROJECT + '-ingress'
STAGING_URL = 'https://staging-pos.lanzhounoodlesmtl.com'
BASELINE = 'a14cc4ce87ecaea17b31d86aa55aae76f1a79294'
BACKEND_ENTRYPOINT = ['sh', '-c', 'java $JAVA_OPTS -jar /app/app.jar']
os.umask(0o077)
if not __debug__:
    raise RuntimeError('Staging safety assertions must be enabled')

spec = importlib.util.spec_from_file_location('reviewed_release', HERE.parent / 'dine-in-headers/deploy.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
i, p, ops = release.i, release.p, release.ops


def archived(name):
    result = json.loads(i.run(['sudo', '-n', 'cat', str(ARCHIVE / (name + '.final.private.json'))]))
    row = result[0] if isinstance(result, list) else result
    assert row['Name'] == '/' + name
    assert row['Config']['Labels']['com.docker.compose.project'] == PROJECT
    return row


def require_clean_release(sha):
    assert re.fullmatch(r'[0-9a-f]{40}', sha)
    checkout = ROOT / 'releases' / sha
    assert HERE == checkout / 'deployment/cloud/chinatown-menu'
    assert i.run(['git', '-C', str(checkout), 'rev-parse', 'HEAD']).decode().strip() == sha
    assert not i.run(['git', '-C', str(checkout), 'status', '--porcelain']).strip()
    i.run(['git', '-C', str(checkout), 'merge-base', '--is-ancestor', BASELINE, sha])
    return checkout


def stage_environment(model, db_env):
    env = model['environment']
    assert env['APP_ENVIRONMENT'] == env['APP_PHASE_B_RUNTIME'] == 'staging'
    assert env['DB_NAME'] == db_env['POSTGRES_DB'] == 'restaurant_pos_staging'
    assert env['DB_USER'] == db_env['POSTGRES_USER'] and env['DB_PASSWORD'] == db_env['POSTGRES_PASSWORD']
    assert env['DB_HOST'] == 'db' and env['DB_PORT'] == '5432'
    assert env['SPRING_PROFILES_ACTIVE'] == 'cloud'
    assert model['entrypoint'] == BACKEND_ENTRYPOINT and not model['command'] and not model.get('volumes')
    for flag in ('APP_AUTH_X_USER_ID_FALLBACK_ENABLED', 'APP_SEED_DEFAULT_USERS_ENABLED',
                 'APP_SEED_DEMO_DATA_ENABLED', 'APP_SEED_MEMBERSHIP_SUPPLEMENT_ENABLED',
                 'APP_SEED_PRODUCTION_BOOTSTRAP_ENABLED'):
        assert env[flag] == 'false'
    assert {key for key in env if key.startswith('SPRING_')} == {'SPRING_PROFILES_ACTIVE'}
    assert {key for key in env if key.startswith('FLYWAY_')} == {'FLYWAY_TARGET'}
    assert env['FLYWAY_TARGET'] == 'latest'
    assert env['JAVA_OPTS'].split() == ['-Xms128m', '-Xmx512m']
    assert not any(env.get(key) for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH'))
    for k in list(env):
        if k.startswith('UBER_EATS_'):
            del env[k]
    env.update(UBER_EATS_ENABLED='false', UBER_EATS_WORKER_ENABLED='false', UBER_EATS_ENVIRONMENT='sandbox',
               APP_PRINTING_ALLOWED_MODES='DISABLED,MOCK', APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED='false',
               APP_SEED_RUNTIME_ENABLED='false')
    env['JAVA_OPTS'] = (env.get('JAVA_OPTS', '') + ' -Dapp.seed.runtime-enabled=false').strip()


def main(sha):
    checkout = require_clean_release(sha)
    root = ROOT / ('chinatown-menu-' + sha)
    root.mkdir(mode=0o700)
    prod = {name: i.fingerprint(name) for name in i.PROD}
    prod_ledger = i.ledger('cloud-db-1')
    disk_before = i.run(['df', '-h', '/']).decode()
    cache_before = i.run(['docker', 'system', 'df']).decode()
    assert not any(name.startswith(PROJECT) for name in i.run(['docker', 'ps', '-a', '--format', '{{.Names}}']).decode().splitlines())
    assert NETWORK not in i.run(['docker', 'network', 'ls', '--format', '{{.Name}}']).decode().splitlines()
    assert 'restaurant-pos-staging_chinatown_postgres' not in i.run(['docker', 'volume', 'ls', '--format', '{{.Name}}']).decode().splitlines()
    old = {name: archived(PROJECT + '-' + name + '-1') for name in ('backend', 'nginx', 'db')}
    relay = archived(RELAY)
    assert all(old[s]['Config']['Labels']['org.opencontainers.image.revision'] == BASELINE for s in ('backend', 'nginx'))
    db_env = ops.env_of(old['db'])
    db_env = {k: db_env[k] for k in ('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'TZ')}
    dump = i.run(['sudo', '-n', 'cat', str(ARCHIVE / 'staging-final.dump')])
    assert hashlib.sha256(dump).hexdigest() == RESTORE_SHA256
    model = {s: p.modeled(s, old[s]) for s in ('backend', 'nginx')}
    # Docker clears observed addresses on stop. Recover only the archived
    # configured static addresses, not arbitrary network settings.
    for s in ('backend', 'nginx'):
        address = old[s]['NetworkSettings']['Networks'][NETWORK]['IPAMConfig']['IPv4Address']
        assert address == ('172.19.0.3' if s == 'backend' else '172.19.0.4')
        model[s]['networks']['restaurant-pos']['ipv4_address'] = address
    stage_environment(model['backend'], db_env)
    rollback = dict(services=model, networks={'restaurant-pos': {'external': True, 'name': NETWORK}})
    ops.write_private(root / 'rollback.private.json', i.escape_compose(rollback))
    assert relay['HostConfig']['NetworkMode'] == 'host' and relay['HostConfig']['Memory'] == 67108864
    relay_file = Path(relay['Mounts'][0]['Source'])
    assert relay_file == ROOT / 'ingress-20260930/host-relay.conf' and not relay['Mounts'][0]['RW']
    relay_conf = relay_file.read_text()
    assert 'listen 172.18.0.1:18480;' in relay_conf and 'proxy_pass http://127.0.0.1:18080;' in relay_conf
    db_model = {'image': old['db']['Image'], 'environment': db_env, 'restart': 'unless-stopped',
                'volumes': ['chinatown_postgres:/var/lib/postgresql/data'], 'networks': ['restaurant-pos'],
                'mem_limit': '512m', 'memswap_limit': '512m', 'cpus': .75,
                'logging': old['db']['HostConfig']['LogConfig'] and
                    {'driver': old['db']['HostConfig']['LogConfig']['Type'],
                     'options': old['db']['HostConfig']['LogConfig']['Config']}}
    build = json.loads(json.dumps(model))
    p.build_images(checkout, sha, root)
    for service, folder in (('backend', 'backend'), ('nginx', 'frontend')):
        image = i.inspect('restaurant-pos-' + folder + ':staging-' + sha)
        assert image['Config']['Labels']['org.opencontainers.image.revision'] == sha
        if service == 'backend':
            assert image['Config']['Entrypoint'] == BACKEND_ENTRYPOINT and not image['Config'].get('Cmd')
        build[service]['image'] = image['Id']
        build[service]['labels']['org.opencontainers.image.revision'] = sha

    build['db'] = db_model
    build['ingress'] = {'image': relay['Image'], 'container_name': RELAY, 'network_mode': 'host',
                        'entrypoint': ['/usr/sbin/nginx'], 'command': ['-g', 'daemon off;'],
                        'volumes': [str(relay_file) + ':/etc/nginx/nginx.conf:ro'],
                        'mem_limit': '64m', 'memswap_limit': '64m', 'cpus': .2,
                        'restart': 'unless-stopped', 'logging': {'driver': 'local', 'options': {'max-size': '10m', 'max-file': '3'}}}
    target = {'services': build, 'volumes': {'chinatown_postgres': {}},
              'networks': {'restaurant-pos': {'driver': 'bridge', 'ipam': {'config': [{'subnet': '172.19.0.0/16'}]}}}}
    path = root / 'target.private.json'
    ops.write_private(path, i.escape_compose(target))
    assert {n: i.fingerprint(n) for n in i.PROD} == prod
    try:
        i.compose(path, 'up', '-d', '--no-build', '--pull', 'never', 'db')
        for _ in range(90):
            if subprocess.run(['docker', 'exec', DB, 'sh', '-c',
                               'pg_isready -h 127.0.0.1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('Staging database did not become ready')
        row = i.inspect(DB)
        assert row['Config']['Labels']['com.docker.compose.project'] == PROJECT
        assert list(row['NetworkSettings']['Networks']) == [NETWORK] and not row['HostConfig']['PortBindings']
        prod_db = i.inspect('cloud-db-1')
        assert NETWORK not in prod_db['NetworkSettings']['Networks']
        assert row['Mounts'][0]['Name'] == PROJECT + '_chinatown_postgres' and row['Mounts'][0]['Source'] != prod_db['Mounts'][0]['Source']
        assert not release.read_sql(DB, "select to_regclass('public.orders')")
        i.run(['docker', 'exec', '-i', DB, 'sh', '-c',
               'pg_restore --exit-on-error --single-transaction --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
              input=dump, stderr=subprocess.STDOUT)
        ledger = i.ledger(DB)
        assert ledger.splitlines()[-1].startswith('40|')
        assert release.read_sql(DB, 'select count(*) from orders') == '118'
        assert release.read_sql(DB, 'select count(*) from stores') == '9'
        assert release.read_sql(DB, "select count(*) from stores where printing_mode in ('REAL','PAD_DIRECT')") == '0'
        history = p.history_fingerprints()
        i.compose(path, 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend', 'nginx', 'ingress')
        release.prior.wait_health(STAGING_URL)
        final_ledger = i.ledger(DB)
        assert final_ledger.startswith(ledger) and re.fullmatch(
            r'41\|V41__menu_item_portion_price.sql\|-?\d+\|t\n', final_ledger[len(ledger):])
        assert p.history_fingerprints(history['cutoffs']) == history
        for service in ('backend', 'nginx'):
            live = i.inspect(PROJECT + '-' + service + '-1')
            assert live['Image'] == build[service]['image']
            assert live['Config']['Labels']['org.opencontainers.image.revision'] == sha
            assert list(live['NetworkSettings']['Networks']) == [NETWORK]
        p.require_staging_database(ops.env_of(i.inspect(PROJECT + '-backend-1')),
                                   i.inspect(PROJECT + '-backend-1'), i.inspect(DB), i.inspect('cloud-db-1'))
        assert {n: i.fingerprint(n) for n in i.PROD} == prod
        assert i.ledger('cloud-db-1') == prod_ledger
        i.health('https://pos.lanzhounoodlesmtl.com')
        report = {'result': 'PASS', 'sha': sha, 'restored_sha256': RESTORE_SHA256,
                  'flyway': '40 -> 41', 'ledger_tail': final_ledger.splitlines()[-1],
                  'images': {s: build[s]['image'] for s in ('backend', 'nginx')},
                  'history_unchanged': True, 'production_unchanged': True, 'disk_before': disk_before,
                  'cache_before': cache_before, 'disk_after': i.run(['df', '-h', '/']).decode(),
                  'cache_after': i.run(['docker', 'system', 'df']).decode(),
                  'deployed_at': datetime.datetime.now(datetime.timezone.utc).isoformat()}
        ops.write_private(root / 'result.json', report)
        print(json.dumps(report), flush=True)
    except Exception as error:
        ops.write_private(root / 'failure.json', {'error_type': type(error).__name__})
        # Return the formerly retired environment to a stopped state. Keep any
        # restored database and evidence available for diagnosis.
        i.compose(path, 'stop', '-t', '15', 'ingress', 'nginx', 'backend', 'db')
        raise


if __name__ == '__main__':
    assert len(sys.argv) == 2
    with ops.operation_locks():
        main(sys.argv[1])
