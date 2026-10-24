#!/usr/bin/env python3
"""2026-10-24 table soft delete; reuse reviewed app-pair promotion primitives."""
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
OLD_IMAGE = 'sha256:ced502b10fc6d3a61eb57b55733ee24d45665ce16350c15a7caf1de2339f0b2e'
OLD_FRONTEND = 'sha256:e2fee1acee5a4b9dfb137090b0be5aa8e4a361cfc6e51b6527dcd91c85593bf0'
SERVICES = ('backend', 'nginx')
RELEASE_PREFIX = 'dining-table-soft-delete-'
os.umask(0o077)


def read_sql(db, sql):
    return i.run(['docker', 'exec', '-i', '-e', 'PGOPTIONS=-c default_transaction_read_only=on -c statement_timeout=5000',
        db, 'sh', '-c', 'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], input=sql.encode()).decode().strip()


def configuration(db):
    state = ops.current_4483_configuration(db)
    # This release has no migration; the entire ledger is checked below.
    state.pop('flyway_schema_history')
    state['dining_tables'] = read_sql(db, "SELECT md5(coalesce(jsonb_agg(to_jsonb(t) ORDER BY id),'[]'::jsonb)::text) FROM dining_tables t")
    return state


def validate_ledger(before, after):
    assert before.splitlines()[-1] == '39|V39__add_dining_table_split_label_mode.sql|1431202837|t'
    assert after == before, 'No migration or ledger change allowed'


def table_modes(db):
    result = json.loads(read_sql(db, """SELECT json_build_object(
        'rows',count(*),'left_right',count(*) FILTER(WHERE split_label_mode='LEFT_RIGHT'),
        'inside_outside',count(*) FILTER(WHERE split_label_mode='INSIDE_OUTSIDE'),
        'stores',(SELECT json_agg(t) FROM (SELECT s.id,s.code,count(d.id) AS tables,
          count(d.id) FILTER(WHERE d.split_label_mode='LEFT_RIGHT') AS default_mode_tables,
          count(d.id) FILTER(WHERE d.split_label_mode='INSIDE_OUTSIDE') AS inside_outside_tables
          FROM stores s LEFT JOIN dining_tables d ON d.store_id=s.id GROUP BY s.id,s.code ORDER BY s.id) t))
        FROM dining_tables"""))
    assert result['rows'] == result['left_right'] + result['inside_outside'], 'Invalid table mode'
    return result


def order_contexts(db, cutoff):
    assert isinstance(cutoff, int) and cutoff >= 0
    return read_sql(db, f"SELECT md5(coalesce(jsonb_agg(jsonb_build_array(id,store_id,order_type,table_no) ORDER BY id),'[]'::jsonb)::text) FROM orders WHERE id<={cutoff}")


def queue_guard(db, flyway='39'):
    value = ops.inventory(db)
    assert value['flyway'] == flyway
    assert all(value[k] == 0 for k in ('pending_print_jobs', 'pending_outbox', 'pending_uber_events', 'due_uber_orders'))
    state = configuration(db)
    assert state['fresh_printing'] == 0, 'Wait for a quiet printing window'
    return state


def validate_delta(before, after, images):
    for service in SERVICES:
        assert after['services'][service]['image'] == images[service]
    left, right = copy.deepcopy(before), copy.deepcopy(after)
    for model in (left, right):
        for service in SERVICES:
            model['services'][service].pop('image')
            model['services'][service].pop('labels', None)
    assert left == right, 'Only application images/OCI labels may change'


def normalized_binds(value):
    # Docker's bind array order is not stable. Preserve every source/target/option.
    binds = value or []
    assert isinstance(binds, list) and all(isinstance(v, str) for v in binds)
    destinations = [v.split(':')[1] for v in binds if len(v.split(':')) in (2, 3)]
    assert len(destinations) == len(binds) and len(set(destinations)) == len(binds), 'Ambiguous bind destinations'
    return sorted(binds)


def evidence_root(environment, sha):
    assert environment in ('staging', 'production') and re.fullmatch('[0-9a-f]{40}', sha)
    return BASE / environment / (RELEASE_PREFIX + sha)


def main(environment, sha):
    assert environment in ('staging', 'production') and re.fullmatch('[0-9a-f]{40}', sha)
    release = BASE / 'staging/releases' / sha
    script_release = HERE.parents[2]
    script_sha = i.run(['git', '-C', str(script_release), 'rev-parse', 'HEAD']).decode().strip()
    assert HERE == BASE / 'staging/releases' / script_sha / 'deployment/cloud/dine-in-headers'
    assert script_sha == sha
    assert not i.run(['git', '-C', str(script_release), 'status', '--porcelain']).strip()
    assert not i.run(['git', '-C', str(script_release), 'diff', sha, script_sha, '--', 'backend', 'frontend']).strip()
    assert i.run(['git', '-C', str(release), 'rev-parse', 'HEAD']).decode().strip() == sha
    assert not i.run(['git', '-C', str(release), 'status', '--porcelain']).strip()
    prod = environment == 'production'
    project = 'cloud' if prod else 'restaurant-pos-staging'
    name, db = project + '-backend-1', project + '-db-1'
    url = 'https://' + ('pos' if prod else 'staging-pos') + '.lanzhounoodlesmtl.com'
    before = {service: i.inspect(project + '-' + service + '-1') for service in SERVICES}
    assert before['backend']['Image'] == OLD_IMAGE and before['nginx']['Image'] == OLD_FRONTEND
    env = ops.env_of(before['backend'])
    assert env['APP_ENVIRONMENT'] == environment
    assert env['UBER_EATS_ENABLED'] == 'true' and env['UBER_EATS_WORKER_ENABLED'] == ('true' if prod else 'false')
    stage_db, production_db = i.inspect('restaurant-pos-staging-db-1'), i.inspect('cloud-db-1')
    assert stage_db['Mounts'][0]['Source'] != production_db['Mounts'][0]['Source']
    assert set(stage_db['NetworkSettings']['Networks']).isdisjoint(production_db['NetworkSettings']['Networks'])
    if not prod:
        p.require_staging_database(env, before['backend'], stage_db, production_db)
    else:
        address = production_db['NetworkSettings']['Networks']['cloud_restaurant-pos']['IPAddress']
        assert env['DB_HOST'] in ('db', 'cloud-db-1', address) and env['DB_NAME'] == 'restaurant_pos'
        assert env['DB_PORT'] == '5432' and env['UBER_EATS_ENVIRONMENT'] == 'production'
        assert not any(env.get(k) for k in ('SPRING_DATASOURCE_URL', 'SPRING_APPLICATION_JSON',
            'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'SPRING_CONFIG_IMPORT'))
        launch = ' '.join(env.get(k, '') for k in ('JAVA_OPTS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'))
        launch += json.dumps([before['backend']['Config'].get('Entrypoint'), before['backend']['Config'].get('Cmd')])
        assert 'spring.datasource' not in launch.lower() and 'spring.config' not in launch.lower()
    root = evidence_root(environment, sha)
    assert not root.exists(), 'Do not overwrite release evidence'
    root.mkdir(mode=0o700)
    source = BASE / 'production/current.compose.json'
    original_bytes = ops.private_file(source).read_bytes() if prod else None
    rollback = json.loads(original_bytes) if prod else i.escape_compose({
        'services': {service: p.modeled(service, before[service]) for service in SERVICES},
        'networks': {'restaurant-pos': {'external': True, 'name': 'restaurant-pos-staging_restaurant-pos'}}})
    ops.write_private(root / 'rollback.private.json', rollback)
    def compose(path, *args):
        return i.run(['docker', 'compose', '-p', project, '-f', str(path), *args], stderr=subprocess.STDOUT)
    resolved_before = json.loads(compose(root / 'rollback.private.json', 'config', '--format', 'json'))
    for service in SERVICES:
        assert resolved_before['services'][service]['image'] == before[service]['Image']
        assert resolved_before['services'][service]['environment'] == ops.env_of(before[service])
    updated_names = {project + '-' + service + '-1' for service in SERVICES}
    protected = {n: i.fingerprint(n) for n in i.PROD + i.STAGE if n not in updated_names}
    ledger = i.ledger(db)
    configuration = queue_guard(db)
    p.DB = db
    history = p.history_fingerprints()
    contexts = order_contexts(db, history['cutoffs'][0])
    baseline = {'protected': protected, 'ledger': ledger,
        'configuration': configuration, 'history': history, 'order_contexts': contexts}
    ops.write_private(root / 'baseline.private.json', baseline)
    with (root / 'database-before.dump').open('xb') as output:
        subprocess.run(['docker', 'exec', db, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'],
                       stdout=output, check=True, timeout=180)
    with (root / 'database-before.dump').open('rb') as backup:
        toc = i.run(['docker', 'exec', '-i', db, 'pg_restore', '--list'], stdin=backup)
        assert b'public orders' in toc and b'public print_jobs' in toc
    if not prod:
        p.build_images(release, sha, root)
    image_rows = {service: i.inspect('restaurant-pos-' + ('backend' if service == 'backend' else 'frontend') + ':staging-' + sha) for service in SERVICES}
    assert all(row['Config']['Labels']['org.opencontainers.image.revision'] == sha for row in image_rows.values())
    images = {service: row['Id'] for service, row in image_rows.items()}
    if prod:
        accepted = json.loads(ops.private_file(BASE / 'staging' / (RELEASE_PREFIX + sha) / 'acceptance.json').read_text())
        assert accepted['accepted'] is True and accepted['source_sha'] == sha and accepted['images'] == images
        assert accepted['header_cases'] == 24 and accepted['health'] == 'PASS'
        assert accepted['additional_top_feed_mm'] == 16 and accepted['top_feed_once'] is True
        assert accepted['split_label_cases'] == 48 and accepted['flyway'] == '39' and accepted['mock_split_labels'] == 'PASS'
        assert accepted['table_soft_delete_ui'] == 'PASS' and accepted['migration'] == 'NONE_V39_UNCHANGED'
        for service in SERVICES: assert i.inspect('restaurant-pos-staging-' + service + '-1')['Image'] == images[service]
        i.health('https://staging-pos.lanzhounoodlesmtl.com')
    target = copy.deepcopy(rollback)
    for service in SERVICES:
        target['services'][service]['image'] = images[service]
        target['services'][service].setdefault('labels', {}).update({k: v for k, v in image_rows[service]['Config']['Labels'].items() if not k.startswith('com.docker.compose.')})
    ops.write_private(root / 'target.private.json', target)
    validate_delta(resolved_before, json.loads(compose(root / 'target.private.json', 'config', '--format', 'json')), images)
    # Build may take minutes: recheck live state immediately before the only restart.
    for service in SERVICES: assert i.fingerprint(project + '-' + service + '-1')['id'] == before[service]['Id']
    assert queue_guard(db) == configuration and p.history_fingerprints(history['cutoffs']) == history
    assert i.ledger(db) == ledger and order_contexts(db, history['cutoffs'][0]) == contexts
    if prod: assert source.read_bytes() == original_bytes
    phase = 'restart'
    try:
        for service in SERVICES:
            phase = service + '.restart'
            compose(root / 'target.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', service)
            phase = service + '.health'
            prior.wait_health(url)
            after = i.inspect(project + '-' + service + '-1')
            phase = service + '.image_environment'
            assert after['Image'] == images[service] and ops.env_of(after) == ops.env_of(before[service])
            for key in ('Entrypoint', 'Cmd', 'User', 'WorkingDir'):
                phase = service + '.Config.' + key
                assert after['Config'].get(key) == before[service]['Config'].get(key)
            for key in ('Memory', 'MemorySwap', 'NanoCpus', 'PortBindings', 'Binds', 'RestartPolicy', 'LogConfig'):
                phase = service + '.HostConfig.' + key
                left, right = after['HostConfig'].get(key), before[service]['HostConfig'].get(key)
                assert (normalized_binds(left) == normalized_binds(right)) if key == 'Binds' else left == right
            phase = service + '.network'
            assert {k: v['IPAddress'] for k, v in after['NetworkSettings']['Networks'].items()} == {k: v['IPAddress'] for k, v in before[service]['NetworkSettings']['Networks'].items()}
        phase = 'protected_containers'
        assert all(i.fingerprint(n) == value for n, value in protected.items())
        phase = 'ledger'
        validate_ledger(ledger, i.ledger(db))
        phase = 'configuration_and_table_modes'
        assert queue_guard(db, '39') == configuration
        modes = table_modes(db)
        phase = 'order_contexts_and_history'
        assert order_contexts(db, history['cutoffs'][0]) == contexts
        assert p.history_fingerprints(history['cutoffs']) == history
        phase = 'public_health'
        for endpoint in ('https://pos.lanzhounoodlesmtl.com', 'https://staging-pos.lanzhounoodlesmtl.com'): i.health(endpoint)
        if prod:
            phase = 'canonical_model'
            assert source.read_bytes() == original_bytes
            ops.write_private(source, target)
    except BaseException as error:
        # No exception text: subprocess/env/config values can contain secrets.
        diagnostic = {'failed_phase': phase, 'error_type': type(error).__name__, 'script_sha': script_sha}
        ops.write_private(root / 'failure.json', diagnostic)
        print(json.dumps(diagnostic), file=sys.stderr, flush=True)
        compose(root / 'rollback.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', *SERVICES)
        prior.wait_health(url)
        if prod: ops.write_private(source, rollback)
        raise RuntimeError('Application rollback applied; database retained') from None
    result = {'environment': environment, 'source_sha': sha, 'script_sha': script_sha, 'migration': 'NONE_V39_UNCHANGED', 'image': images['backend'], 'images': images, 'health': 'PASS',
        'flyway': '39', 'table_modes': modes, 'order_contexts_unchanged': True, 'accepted': False, 'configuration_unchanged': True, 'history_unchanged': True,
        'other_containers_unchanged': True, 'uber_worker_enabled': prod,
        'deployed_at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'backup_sha256': hashlib.sha256((root / 'database-before.dump').read_bytes()).hexdigest()}
    ops.write_private(root / 'result.json', result)
    if prod: ops.write_private(source.parent / 'release.json', {'source_sha': sha, 'release_dir': str(root), 'compose': source.name})
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    with ops.operation_locks():
        main(*sys.argv[1:])
