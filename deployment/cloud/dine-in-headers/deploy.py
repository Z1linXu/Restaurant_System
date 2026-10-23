#!/usr/bin/env python3
"""2026-10-23 header-only release; reuse existing models, locks and backup primitives."""
import copy
import datetime
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
spec = importlib.util.spec_from_file_location('prior_release', HERE.parent / 'pos-production-removes/deploy.py')
prior = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prior)
p, i, ops = prior.p, prior.i, prior.ops
BASE = Path('/srv/restaurant-pos')
OLD_IMAGE = 'sha256:264babe4f4bed353b310f5989a8b93623dff69ebeee9cd2e0dda81b829238d97'
OLD_FRONTEND = 'sha256:2b0749d39545a4eae784b29315be78aac0b5866dc069fb6c60372ad82ee4387b'
os.umask(0o077)


def queue_guard(db):
    value = ops.inventory(db)
    assert value['flyway'] == '38'
    assert all(value[k] == 0 for k in ('pending_print_jobs', 'pending_outbox', 'pending_uber_events', 'due_uber_orders'))
    state = ops.current_4483_configuration(db)
    assert state['fresh_printing'] == 0, 'Wait for a quiet printing window'
    return state


def validate_delta(before, after, image):
    assert after['services']['backend']['image'] == image
    left, right = copy.deepcopy(before), copy.deepcopy(after)
    for model in (left, right):
        model['services']['backend'].pop('image')
        model['services']['backend'].pop('labels', None)
    assert left == right, 'Only backend image/OCI labels may change'


def main(environment, sha):
    assert environment in ('staging', 'production') and re.fullmatch('[0-9a-f]{40}', sha)
    release = BASE / 'staging/releases' / sha
    assert HERE == release / 'deployment/cloud/dine-in-headers'
    assert i.run(['git', '-C', str(release), 'rev-parse', 'HEAD']).decode().strip() == sha
    assert not i.run(['git', '-C', str(release), 'status', '--porcelain']).strip()
    prod = environment == 'production'
    project = 'cloud' if prod else 'restaurant-pos-staging'
    name, db = project + '-backend-1', project + '-db-1'
    url = 'https://' + ('pos' if prod else 'staging-pos') + '.lanzhounoodlesmtl.com'
    before = i.inspect(name)
    assert before['Image'] == OLD_IMAGE and i.inspect(project + '-nginx-1')['Image'] == OLD_FRONTEND
    env = ops.env_of(before)
    assert env['APP_ENVIRONMENT'] == environment
    assert env['UBER_EATS_ENABLED'] == 'true' and env['UBER_EATS_WORKER_ENABLED'] == ('true' if prod else 'false')
    stage_db, production_db = i.inspect('restaurant-pos-staging-db-1'), i.inspect('cloud-db-1')
    assert stage_db['Mounts'][0]['Source'] != production_db['Mounts'][0]['Source']
    assert set(stage_db['NetworkSettings']['Networks']).isdisjoint(production_db['NetworkSettings']['Networks'])
    if not prod:
        p.require_staging_database(env, before, stage_db, production_db)
    else:
        address = production_db['NetworkSettings']['Networks']['cloud_restaurant-pos']['IPAddress']
        assert env['DB_HOST'] in ('db', 'cloud-db-1', address) and env['DB_NAME'] == 'restaurant_pos'
        assert env['DB_PORT'] == '5432' and env['UBER_EATS_ENVIRONMENT'] == 'production'
        assert not any(env.get(k) for k in ('SPRING_DATASOURCE_URL', 'SPRING_APPLICATION_JSON',
            'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT'))
        launch = ' '.join(env.get(k, '') for k in ('JAVA_OPTS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'))
        launch += json.dumps([before['Config'].get('Entrypoint'), before['Config'].get('Cmd')])
        assert 'spring.datasource' not in launch.lower() and 'spring.config' not in launch.lower()
    root = BASE / environment / ('dine-in-headers-' + sha)
    assert not root.exists(), 'Do not overwrite release evidence'
    root.mkdir(mode=0o700)
    source = BASE / 'production/current.compose.json'
    original_bytes = ops.private_file(source).read_bytes() if prod else None
    rollback = json.loads(original_bytes) if prod else i.escape_compose({
        'services': {'backend': p.modeled('backend', before)},
        'networks': {'restaurant-pos': {'external': True, 'name': 'restaurant-pos-staging_restaurant-pos'}}})
    ops.write_private(root / 'rollback.private.json', rollback)
    def compose(path, *args):
        return i.run(['docker', 'compose', '-p', project, '-f', str(path), *args], stderr=subprocess.STDOUT)
    resolved_before = json.loads(compose(root / 'rollback.private.json', 'config', '--format', 'json'))
    assert resolved_before['services']['backend']['image'] == before['Image']
    assert resolved_before['services']['backend']['environment'] == env
    protected = {n: i.fingerprint(n) for n in i.PROD + i.STAGE if n != name}
    ledger = i.ledger(db)
    configuration = queue_guard(db)
    p.DB = db
    history = p.history_fingerprints()
    ops.write_private(root / 'baseline.private.json', {'protected': protected, 'ledger': ledger,
        'configuration': configuration, 'history': history})
    with (root / 'database-before.dump').open('xb') as output:
        subprocess.run(['docker', 'exec', db, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'],
                       stdout=output, check=True, timeout=180)
    with (root / 'database-before.dump').open('rb') as backup:
        toc = i.run(['docker', 'exec', '-i', db, 'pg_restore', '--list'], stdin=backup)
        assert b'public orders' in toc and b'public print_jobs' in toc
    tag = 'restaurant-pos-backend:staging-' + sha
    if not prod:
        builder = 'dine-header-' + sha[:12]
        created = False
        try:
            p.run_logged(['docker', 'buildx', 'create', '--name', builder, '--driver', 'docker-container',
                '--driver-opt', 'memory=1g,memory-swap=1g,cpu-period=100000,cpu-quota=100000', '--bootstrap'], root / 'builder.private.log')
            created = True
            p.run_logged(['docker', 'buildx', 'build', '--builder', builder, '--load', '--provenance=false',
                '--label', 'org.opencontainers.image.revision=' + sha, '-t', tag, str(release / 'backend')], root / 'build.private.log')
        finally:
            if created: p.run_logged(['docker', 'buildx', 'rm', builder], root / 'builder-remove.private.log', 120)
    image = i.inspect(tag)
    assert image['Config']['Labels']['org.opencontainers.image.revision'] == sha
    if prod:
        accepted = json.loads(ops.private_file(BASE / 'staging' / ('dine-in-headers-' + sha) / 'acceptance.json').read_text())
        assert accepted['accepted'] is True and accepted['source_sha'] == sha and accepted['image'] == image['Id']
        assert accepted['header_cases'] == 24 and accepted['health'] == 'PASS'
        assert i.inspect('restaurant-pos-staging-backend-1')['Image'] == image['Id']
        i.health('https://staging-pos.lanzhounoodlesmtl.com')
    target = copy.deepcopy(rollback)
    target['services']['backend']['image'] = image['Id']
    target['services']['backend'].setdefault('labels', {}).update({k: v for k, v in image['Config']['Labels'].items() if not k.startswith('com.docker.compose.')})
    ops.write_private(root / 'target.private.json', target)
    validate_delta(resolved_before, json.loads(compose(root / 'target.private.json', 'config', '--format', 'json')), image['Id'])
    # Build may take minutes: recheck live state immediately before the only restart.
    assert i.fingerprint(name)['id'] == before['Id']
    assert queue_guard(db) == configuration and p.history_fingerprints(history['cutoffs']) == history
    if prod: assert source.read_bytes() == original_bytes
    try:
        compose(root / 'target.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend')
        prior.wait_health(url)
        after = i.inspect(name)
        assert after['Image'] == image['Id'] and ops.env_of(after) == env
        for key in ('Entrypoint', 'Cmd', 'User', 'WorkingDir'):
            assert after['Config'].get(key) == before['Config'].get(key)
        for key in ('Memory', 'MemorySwap', 'NanoCpus', 'PortBindings', 'Binds', 'RestartPolicy', 'LogConfig'):
            assert after['HostConfig'].get(key) == before['HostConfig'].get(key)
        assert {k: v['IPAddress'] for k, v in after['NetworkSettings']['Networks'].items()} == {k: v['IPAddress'] for k, v in before['NetworkSettings']['Networks'].items()}
        assert all(i.fingerprint(n) == value for n, value in protected.items())
        assert i.ledger(db) == ledger and queue_guard(db) == configuration
        assert p.history_fingerprints(history['cutoffs']) == history
        for endpoint in ('https://pos.lanzhounoodlesmtl.com', 'https://staging-pos.lanzhounoodlesmtl.com'): i.health(endpoint)
        if prod:
            assert source.read_bytes() == original_bytes
            ops.write_private(source, target)
    except BaseException:
        compose(root / 'rollback.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend')
        prior.wait_health(url)
        if prod: ops.write_private(source, rollback)
        raise RuntimeError('Application rollback applied; database retained') from None
    result = {'environment': environment, 'source_sha': sha, 'image': image['Id'], 'health': 'PASS',
        'flyway': '38', 'accepted': False, 'configuration_unchanged': True, 'history_unchanged': True,
        'other_containers_unchanged': True, 'uber_worker_enabled': prod,
        'deployed_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'backup_sha256': hashlib.sha256((root / 'database-before.dump').read_bytes()).hexdigest()}
    ops.write_private(root / 'result.json', result)
    if prod: ops.write_private(source.parent / 'release.json', {'source_sha': sha, 'release_dir': str(root), 'compose': source.name})
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    with ops.operation_locks():
        main(*sys.argv[1:])
