#!/usr/bin/env python3
"""Replace the reviewed Chinatown Staging frontend. Retain its V41 backend."""

import json
import subprocess
import sys
import urllib.request

sys.dont_write_bytecode = True
from pathlib import Path
import importlib.util

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('chinatown_deploy', HERE / 'deploy-staging.py')
stage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(stage)
i, p, ops = stage.i, stage.p, stage.ops
RUNTIME = 'a7f408eea3add042026af4e1da8395d166cd5a61'
NGINX = stage.PROJECT + '-nginx-1'
BACKEND = stage.PROJECT + '-backend-1'


def main(sha):
    checkout = stage.require_clean_release(sha)
    git = ['git', '-C', str(checkout)]
    i.run(git + ['merge-base', '--is-ancestor', RUNTIME, sha])
    assert not i.run(git + ['diff', '--name-only', RUNTIME, sha, '--', 'backend']).strip()
    root = stage.ROOT / ('chinatown-quick-add-' + sha)
    previous = json.loads(ops.private_file(stage.ROOT / ('chinatown-menu-' + RUNTIME) / 'result.json').read_text())
    assert previous['result'] == 'PASS' and previous['sha'] == RUNTIME
    live_nginx = i.inspect(NGINX)
    backend = i.inspect(BACKEND)
    assert live_nginx['Image'] == previous['images']['nginx'] and backend['Image'] == previous['images']['backend']
    env = ops.env_of(backend)
    assert env['APP_ENVIRONMENT'] == 'staging' and env['APP_PRINTING_ALLOWED_MODES'] == 'DISABLED,MOCK'
    assert env['UBER_EATS_WORKER_ENABLED'] == env['UBER_EATS_ENABLED'] == 'false'
    p.require_staging_database(env, backend, i.inspect(stage.DB), i.inspect('cloud-db-1'))
    protected = {name: i.fingerprint(name) for name in i.PROD + [BACKEND, stage.DB, stage.RELAY]}
    ledgers = {name: i.ledger(name) for name in (stage.DB, 'cloud-db-1')}
    assert ledgers[stage.DB].splitlines()[-1] == '41|V41__menu_item_portion_price.sql|1328816755|t'
    stage.release.prior.wait_health(stage.STAGING_URL)
    root.mkdir(mode=0o700)
    disk_before = i.run(['df', '-h', '/']).decode()
    cache_before = i.run(['docker', 'system', 'df']).decode()
    old_model = p.modeled('nginx', live_nginx)
    network = {'restaurant-pos': {'external': True, 'name': stage.NETWORK}}

    def model(path, service):
        ops.write_private(path, i.escape_compose({'services': {'nginx': service}, 'networks': network}))

    rollback = root / 'rollback.private.json'
    model(rollback, old_model)
    container_before = i.fingerprint(NGINX)
    p.build_images(checkout, sha, root, (('nginx', 'frontend'),))
    image = i.inspect('restaurant-pos-frontend:staging-' + sha)
    assert image['Config']['Labels']['org.opencontainers.image.revision'] == sha
    target = root / 'target.private.json'
    model(target, {**old_model, 'image': image['Id'],
                   'labels': {**old_model['labels'], 'org.opencontainers.image.revision': sha}})
    assert i.fingerprint(NGINX) == container_before
    assert all(i.fingerprint(name) == view for name, view in protected.items())
    try:
        i.compose(target, 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'nginx')
        stage.release.prior.wait_health(stage.STAGING_URL)
        live = i.inspect(NGINX)
        assert live['Image'] == image['Id'] and ops.env_of(live) == ops.env_of(live_nginx)
        assert live['Config']['Labels']['org.opencontainers.image.revision'] == sha
        assert all(i.fingerprint(name) == view for name, view in protected.items())
        assert all(i.ledger(name) == ledger for name, ledger in ledgers.items())
        with urllib.request.urlopen(stage.STAGING_URL + '/login', timeout=10) as response:
            public_index = response.read()
        assert public_index == i.run(['docker', 'exec', NGINX, 'cat', '/usr/share/nginx/html/index.html'])
    except Exception as error:
        i.compose(rollback, 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'nginx')
        stage.release.prior.wait_health(stage.STAGING_URL)
        assert i.inspect(NGINX)['Image'] == live_nginx['Image']
        ops.write_private(root / 'failure.json', {'result': 'ROLLED_BACK', 'error_type': type(error).__name__})
        raise RuntimeError('Staging frontend rollback applied') from None

    result = {'result': 'PASS', 'frontend_sha': sha, 'frontend_image': image['Id'],
              'backend_sha': RUNTIME, 'backend_image': backend['Image'], 'flyway': '41',
              'production_unchanged': True, 'stage_backend_and_db_unchanged': True,
              'disk_before': disk_before, 'cache_before': cache_before,
              'disk_after': i.run(['df', '-h', '/']).decode(), 'cache_after': i.run(['docker', 'system', 'df']).decode()}
    ops.write_private(root / 'result.json', result)
    print(json.dumps(result), flush=True)


if __name__ == '__main__':
    assert len(sys.argv) == 2
    with ops.operation_locks():
        main(sys.argv[1])
