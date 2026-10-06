"""Bounded, Owner-authorized Add-on backend promotion using the existing private runtime models.
No proxy/env/device/DB-container changes. Stage first; Production uses that same immutable image.
"""
import datetime
import fcntl
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.request

os.umask(0o077)
environment, image, sha = sys.argv[1:]
assert environment in ('staging', 'production')
assert image.startswith('sha256:') and len(image) == 71
assert len(sha) == 40 and all(c in '0123456789abcdef' for c in sha)
prod = environment == 'production'
project = 'cloud' if prod else 'restaurant-pos-staging'
container = project + '-backend-1'
db = project + '-db-1'
base = 'https://' + ('pos' if prod else 'staging-pos') + '.lanzhounoodlesmtl.com'
source = Path('/srv/restaurant-pos/production/current.compose.json' if prod else
              '/srv/restaurant-pos/staging/production-release-20261005/target.private.json')
root = Path('/srv/restaurant-pos') / environment / ('addon-identity-' + sha)
root.mkdir(mode=0o700, parents=True, exist_ok=True)
locks = []
for name in ['/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock',
             '/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock']:
    handle = open(name, 'r+')
    fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
    locks.append(handle)

def run(args, **kwargs):
    return subprocess.check_output(args, timeout=300, **kwargs)

def inspect(name):
    return json.loads(run(['docker', 'inspect', name]))[0]

def compose(path, *args):
    return run(['docker', 'compose', '-p', project, '-f', str(path), *args], stderr=subprocess.STDOUT)

def health(url):
    with urllib.request.urlopen(url + '/api/v1/system/health', timeout=6) as response:
        assert json.load(response)['data']['status'] == 'UP'

baseline = {n: inspect(n) for n in run(['docker', 'ps', '--format', '{{.Names}}']).decode().splitlines()}
assert baseline[container]['State']['Running'] and baseline[db]['State']['Running']
assert inspect(image)['Config']['Labels']['org.opencontainers.image.revision'] == sha
if prod:
    assert baseline['restaurant-pos-staging-backend-1']['Image'] == image
    assert json.loads((Path('/srv/restaurant-pos/staging') / ('addon-identity-' + sha) / 'result.json').read_text())['accepted']
    health('https://staging-pos.lanzhounoodlesmtl.com')
assert not (root / 'result.json').exists(), 'Already promoted: inspect result, do not repeat'
old = json.loads(source.read_text())
assert set(old['services']) == ({'backend', 'nginx'} if prod else {'backend'})
assert old['services']['backend']['image'] == baseline[container]['Image']
(root / 'rollback.private.json').write_text(json.dumps(old))
target = json.loads(json.dumps(old))
target['services']['backend']['image'] = image
(root / 'target.private.json').write_text(json.dumps(target))
resolved = json.loads(compose(root / 'target.private.json', 'config', '--format', 'json'))
assert resolved['services']['backend']['environment'] == dict(v.split('=', 1) for v in baseline[container]['Config']['Env'])
if prod:
    assert resolved['services']['backend']['environment']['UBER_EATS_ENABLED'] == 'false'
# Fresh custom-format backup, validated before any restart. Private and never printed.
with (root / 'database-before.dump').open('wb') as backup:
    subprocess.run(['docker', 'exec', db, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'],
                   stdout=backup, check=True, timeout=180)
with (root / 'database-before.dump').open('rb') as backup:
    assert run(['docker', 'exec', '-i', db, 'pg_restore', '--list'], stdin=backup)
(root / 'before.private.json').write_text(json.dumps(baseline))
try:
    compose(root / 'target.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend')
    for attempt in range(40):
        try:
            health(base)
            break
        except Exception:
            time.sleep(2)
    else:
        raise RuntimeError('Backend health timeout')
    after = inspect(container)
    assert after['Image'] == image
    assert dict(v.split('=', 1) for v in after['Config']['Env']) == dict(
        v.split('=', 1) for v in baseline[container]['Config']['Env'])
    for key in ['Entrypoint', 'Cmd', 'User']:
        assert after['Config'][key] == baseline[container]['Config'][key], key
    for name, before in baseline.items():
        if name != container:
            assert inspect(name)['Id'] == before['Id'], name + ' unexpectedly recreated'
    assert after['NetworkSettings']['Networks'] == baseline[container]['NetworkSettings']['Networks'] or all(
        after['NetworkSettings']['Networks'][name]['IPAddress'] == network['IPAddress']
        for name, network in baseline[container]['NetworkSettings']['Networks'].items())
    health('https://pos.lanzhounoodlesmtl.com')
    health('https://staging-pos.lanzhounoodlesmtl.com')
except Exception:
    compose(root / 'rollback.private.json', 'up', '-d', '--no-build', '--pull', 'never', '--no-deps', 'backend')
    raise
source.write_bytes((root / 'target.private.json').read_bytes())
result = {'environment': environment, 'source_sha': sha, 'image': image,
          'health': 'PASS', 'other_containers_preserved': True,
          'accepted': False, 'timestamp': datetime.datetime.now(datetime.timezone.utc).isoformat()}
(root / 'result.json').write_text(json.dumps(result, indent=2))
if prod:
    (source.parent / 'release.json').write_text(json.dumps({'source_sha': sha, 'release_dir': str(root), 'compose': source.name}))
print(json.dumps(result))
