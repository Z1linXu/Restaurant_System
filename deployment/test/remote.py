#!/usr/bin/env python3
"""Bounded Test-only deployment. Never called automatically by packaging.

Only deploy after the integration owner releases the exact tested candidate.
No prune/down/restore/production deployment commands exist in this helper.
"""
import argparse
import contextlib
import datetime
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import stat
import subprocess
import tarfile
import time
import urllib.request

ROOT = Path('/srv/restaurant-pos/test')
PROJECT = 'restaurant-template-test'
DOMAIN = 'test.lanzhounoodlesmtl.com'
EDGE = 'cloud-nginx-1'
EDGE_TEMPLATE = Path('/home/ubuntu/Restaurant_System/deployment/cloud/data/nginx/default.conf.template')
CERTROOT = Path('/home/ubuntu/Restaurant_System/deployment/cloud/data/letsencrypt')
WEBROOT = Path('/home/ubuntu/Restaurant_System/deployment/cloud/data/certbot-www')
BEGIN = b'\n# BEGIN RESTAURANT_TEMPLATE_TEST\n'
END = b'# END RESTAURANT_TEMPLATE_TEST\n'
SHA = re.compile(r'[0-9a-f]{40}')
HEX64 = re.compile(r'[0-9a-f]{64}')
PROTECTED = ('cloud-nginx-1', 'cloud-backend-1', 'cloud-db-1',
             'restaurant-pos-staging-nginx-1', 'restaurant-pos-staging-backend-1',
             'restaurant-pos-staging-db-1', 'restaurant-pos-staging-ingress',
             'restaurant-pos-uber-sandbox-webhook')
SHARED_LOCKS = (Path('/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock'),
                Path('/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock'))


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def run(args, data=None):
    # No ambient COMPOSE_FILE/DOCKER_HOST or application credentials inherited.
    env = {'PATH': os.environ.get('PATH', '/usr/bin:/bin'), 'HOME': os.environ.get('HOME', '/home/ubuntu'),
           'LANG': 'C.UTF-8', 'DOCKER_HOST': 'unix:///var/run/docker.sock'}
    p = subprocess.run(args, input=data, capture_output=True, env=env)
    if p.returncode:
        # Never echo resolved compose, passwords, container logs or raw stderr.
        raise RuntimeError('Command failed: ' + ' '.join(args[:3]) + ' (exit ' + str(p.returncode) + ')')
    return p.stdout


def jsonrun(args):
    return json.loads(run(args))


def safe_path(path, parent):
    path, parent = Path(path), Path(parent)
    if path.is_symlink() or parent.is_symlink() or parent.resolve() not in path.resolve().parents:
        raise ValueError('Test path/symlink boundary violation')
    # Refuse an existing symlink in any component below /srv/restaurant-pos.
    for part in (path, *path.parents):
        if part == Path('/srv/restaurant-pos'): break
        if part.is_symlink(): raise ValueError('symlink refused')
    return path


def private_write(path, data):
    safe_path(path, ROOT)
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    with path.open('wb') as f:
        os.fchmod(f.fileno(), 0o600)
        f.write(data)
        f.flush(); os.fsync(f.fileno())


def verify_release(release, sha):
    if not SHA.fullmatch(sha) or release != ROOT / 'releases' / sha:
        raise ValueError('exact Test release path/SHA required')
    safe_path(release, ROOT)
    m = json.loads((release / 'manifest.json').read_text())
    if m.get('code_sha') != sha or m.get('environment') != 'ISOLATED_SYNTHETIC_MOCK':
        raise ValueError('manifest identity mismatch')
    for name, expected in m['files'].items():
        p = safe_path(release / name, release)
        if not p.is_file() or sha256(p.read_bytes()) != expected:
            raise ValueError('release file hash mismatch: ' + name)
    checks = json.loads((release / 'checks.json').read_text())
    if checks.get('code_sha') != sha or checks.get('independent_review') != 'ACCEPT':
        raise ValueError('review/SHA gate not satisfied')
    if any(checks.get('checks', {}).get(k) != 'PASS' for k in ('backend', 'frontend', 'integration', 'security', 'legacy')):
        raise ValueError('executed test gate not satisfied')
    images = json.loads((release / 'images.json').read_text())
    for key, repo in [('jre', 'eclipse-temurin'), ('nginx', 'nginx'), ('postgres', 'postgres')]:
        if not re.fullmatch(repo + r'@sha256:[0-9a-f]{64}', images[key]):
            raise ValueError('only fixed official base image digests accepted')
    return m, images


def protected_snapshot():
    docs = jsonrun(['docker', 'inspect', *PROTECTED])
    return {d['Name']: {'id': d['Id'], 'image': d['Image'], 'started': d['State']['StartedAt'],
                        'restarts': d['RestartCount']} for d in docs}


def admission(require_capacity=True):
    fields = dict(re.findall(r'^(\w+):\s+(\d+) kB$', Path('/proc/meminfo').read_text(), re.M))
    available = int(fields['MemAvailable']) // 1024
    free_disk = shutil.disk_usage('/srv/restaurant-pos').free
    minimum_ram = 1100 if require_capacity else 128
    minimum_disk = (6 * 1024 ** 3) if require_capacity else (512 * 1024 ** 2)
    if available < minimum_ram or free_disk < minimum_disk:
        raise ValueError('Test resource admission failed (need ' + str(minimum_ram) + 'MiB available RAM / ' + str(minimum_disk) + ' disk bytes); no cleanup allowed')
    edge = jsonrun(['docker', 'inspect', EDGE])[0]
    n = edge['NetworkSettings']['Networks']
    if set(n) != {'cloud_restaurant-pos'} or n['cloud_restaurant-pos']['IPAddress'] != '172.18.0.4' or n['cloud_restaurant-pos']['Gateway'] != '172.18.0.1':
        raise ValueError('reviewed edge/relay addresses changed')
    return {'available_ram_mib': available, 'disk_available_bytes': free_disk}


def require_project_container(document, service):
    labels = document.get('Config', {}).get('Labels', {})
    if labels.get('com.docker.compose.project') != PROJECT or labels.get('com.docker.compose.service') != service:
        raise ValueError('refusing non-Test container ownership')


def validate_network_document(document, key='test_internal'):
    if key not in ('test_internal', 'test_ingress'):
        raise ValueError('unexpected Test network')
    suffix, internal = ('_internal', True) if key == 'test_internal' else ('_ingress', False)
    labels = document.get('Labels') or {}
    if document.get('Name') != PROJECT + suffix or document.get('Driver') != 'bridge' or document.get('Scope') != 'local' or document.get('Internal') is not internal or document.get('Options'):
        raise ValueError('existing Test network has unexpected bridge isolation or options')
    if labels.get('com.docker.compose.project') != PROJECT or labels.get('com.docker.compose.network') != key:
        raise ValueError('refusing unowned existing Test network')


def validate_network_member(document, key):
    allowed = ('db', 'backend', 'frontend') if key == 'test_internal' else ('frontend',) if key == 'test_ingress' else ()
    service = document.get('Config', {}).get('Labels', {}).get('com.docker.compose.service')
    if service not in allowed:
        raise ValueError('unexpected Test network member')
    require_project_container(document, service)
    if document.get('Name') != '/' + PROJECT + '-' + service + '-1':
        raise ValueError('unexpected Test network container identity')


def validate_volume_document(document):
    labels = document.get('Labels') or {}
    if document.get('Name') != PROJECT + '_postgres' or document.get('Driver') != 'local' or document.get('Options'):
        raise ValueError('existing Test volume has unexpected storage options')
    if labels.get('com.docker.compose.project') != PROJECT or labels.get('com.docker.compose.volume') != 'test_postgres':
        raise ValueError('refusing unowned existing Test volume')


def verify_existing_resources():
    networks = run(['docker', 'network', 'ls', '--format', '{{.Name}}']).decode().splitlines()
    for key, suffix in [('test_internal', '_internal'), ('test_ingress', '_ingress')]:
        network = PROJECT + suffix
        if network not in networks:
            continue
        document = jsonrun(['docker', 'network', 'inspect', network])[0]
        validate_network_document(document, key)
        ids = list((document.get('Containers') or {}).keys())
        for container in jsonrun(['docker', 'inspect', *ids]) if ids else []:
            validate_network_member(container, key)
    volume = PROJECT + '_postgres'
    if volume in run(['docker', 'volume', 'ls', '--format', '{{.Name}}']).decode().splitlines():
        validate_volume_document(jsonrun(['docker', 'volume', 'inspect', volume])[0])
        ids = run(['docker', 'ps', '-a', '--filter', 'volume=' + volume, '--format', '{{.ID}}']).decode().splitlines()
        for container in jsonrun(['docker', 'inspect', *ids]) if ids else []:
            require_project_container(container, 'db')


def test_containers():
    names = run(['docker', 'ps', '-a', '--format', '{{.Names}}']).decode().splitlines()
    result = {}
    for service in ('db', 'backend', 'frontend', 'relay'):
        name = PROJECT + '-' + service + '-1'
        if name in names:
            document = jsonrun(['docker', 'inspect', name])[0]
            require_project_container(document, service)
            result[service] = document
    return result


def listener_records():
    text = run(['sudo', '-n', 'ss', '-H', '-ltnp']).decode()
    records = []
    for line in text.splitlines():
        fields = line.split()
        if len(fields) < 4: continue
        address = fields[3]
        if ':' not in address: continue
        ip, port = address.rsplit(':', 1)
        if port not in ('28080', '28480'): continue
        records.append({'ip': ip, 'port': int(port), 'pids': [int(v) for v in re.findall(r'pid=(\d+)', line)]})
    return records


def validate_port_owners(records, containers, process_info):
    for record in records:
        service = 'frontend' if record['port'] == 28080 else 'relay'
        document = containers.get(service)
        if document is None or not document.get('State', {}).get('Running'):
            raise ValueError('unknown listener on reserved Test port; do not adopt or kill')
        require_project_container(document, service)
        if not record['pids']: raise ValueError('listener ownership cannot be established')
        if service == 'relay':
            if record['ip'] != '172.18.0.1': raise ValueError('relay listener escaped reviewed bind')
            if any(document['Id'] not in process_info[pid]['cgroup'] for pid in record['pids']):
                raise ValueError('reserved relay port belongs to another process/container')
        else:
            if record['ip'] != '127.0.0.1': raise ValueError('frontend listener escaped localhost')
            bindings = document.get('NetworkSettings', {}).get('Ports', {}).get('8080/tcp') or []
            if bindings != [{'HostIp': '127.0.0.1', 'HostPort': '28080'}]: raise ValueError('unexpected frontend port binding')
            ip = document.get('NetworkSettings', {}).get('Networks', {}).get(PROJECT + '_ingress', {}).get('IPAddress')
            for pid in record['pids']:
                args = process_info[pid]['args']
                expected = {'-host-ip': '127.0.0.1', '-host-port': '28080', '-container-ip': ip, '-container-port': '8080'}
                if not args or Path(args[0]).name != 'docker-proxy' or any(flag not in args or args.index(flag) + 1 >= len(args) or args[args.index(flag) + 1] != value for flag, value in expected.items()):
                    raise ValueError('frontend listener is not its exact Docker forwarding process')


def verify_port_owners(containers=None):
    containers = test_containers() if containers is None else containers
    if 'frontend' in containers and containers['frontend'].get('State', {}).get('Running'):
        bindings = containers['frontend'].get('NetworkSettings', {}).get('Ports', {}).get('8080/tcp') or []
        if bindings != [{'HostIp': '127.0.0.1', 'HostPort': '28080'}]: raise ValueError('actual frontend binding is not localhost-only')
    for service in ('db', 'backend'):
        if service in containers and any(containers[service].get('NetworkSettings', {}).get('Ports', {}).values()):
            raise ValueError('actual DB/backend host publication forbidden')
    records = listener_records()
    info = {}
    for record in records:
        service = 'frontend' if record['port'] == 28080 else 'relay'
        if service not in containers or not containers[service].get('State', {}).get('Running'):
            raise ValueError('unknown listener on reserved Test port; do not adopt or kill')
        for pid in record['pids']:
            proc = Path('/proc/' + str(pid))
            # Never inspect arbitrary process arguments or any process environ.
            args = []
            if service == 'frontend':
                if proc.joinpath('comm').read_text().strip() != 'docker-proxy': raise ValueError('unknown frontend listener process')
                args = [b.decode() for b in proc.joinpath('cmdline').read_bytes().split(b'\0') if b]
            info[pid] = {'cgroup': proc.joinpath('cgroup').read_text(), 'args': args}
    validate_port_owners(records, containers, info)
    return records


def verify_runtime(sha):
    containers = test_containers()
    if set(containers) != {'db', 'backend', 'frontend', 'relay'}: raise ValueError('all four Test containers required')
    for service, document in containers.items():
        if not document.get('State', {}).get('Running') or document['State'].get('Restarting'):
            raise ValueError('Test container not steadily running: ' + service)
        if service in ('backend', 'frontend') and document['Config'].get('Labels', {}).get('org.opencontainers.image.revision') != sha:
            raise ValueError('running Test application SHA mismatch')
        expected = {'host'} if service == 'relay' else {PROJECT + '_internal', PROJECT + '_ingress'} if service == 'frontend' else {PROJECT + '_internal'}
        if set(document['NetworkSettings']['Networks']) != expected: raise ValueError('running Test network boundary mismatch')
    if containers['db']['State'].get('Health', {}).get('Status') != 'healthy': raise ValueError('Test database not healthy')
    records = verify_port_owners(containers)
    if not any(r['port'] == 28480 for r in records): raise ValueError('Test relay is not listening')
    # The already authorized edge is the actual allowlisted source; this does not reload it.
    def probe(path):
        return run(['docker', 'exec', EDGE, '/usr/bin/env', '-i', '/usr/bin/wget', '-q', '-T', '5', '-O', '-',
                    '--header', 'Host: ' + DOMAIN, 'http://172.18.0.1:28480' + path])
    if probe('/test-health').strip() != b'test-frontend-up' or probe('/test-code-sha').strip() != sha.encode():
        raise ValueError('edge-to-relay reached the wrong Test frontend')
    health = json.loads(probe('/api/v1/system/health'))
    if health.get('data', {}).get('status') != 'UP': raise ValueError('edge-to-relay-to-backend health failed')
    verify_existing_resources()
    return list(containers.values())


def runtime_env(sha, images):
    path = ROOT / 'private/runtime.env'
    safe_path(path, ROOT)
    values = {}
    if path.exists():
        if path.stat().st_mode & 0o077: raise ValueError('runtime.env must be 0600')
        values = dict(l.split('=', 1) for l in path.read_text().splitlines() if l)
    secret_keys = ['TEST_DB_PASSWORD', 'TEST_JWT_SECRET', 'TEMPLATE_TEST_OWNER_PASSWORD', 'TEMPLATE_TEST_MANAGER_PASSWORD']
    for key in secret_keys:
        if key not in values: values[key] = secrets.token_hex(32)
        if not HEX64.fullmatch(values[key]): raise ValueError('unexpected private Test secret format')
    expected_keys = set(secret_keys) | {'TEST_IMAGE_SHA', 'TEST_DB_IMAGE', 'TEST_NGINX_IMAGE'}
    if set(values) - expected_keys: raise ValueError('unexpected key in Test env; do not source another environment')
    values.update(TEST_IMAGE_SHA=sha, TEST_DB_IMAGE=images['postgres'], TEST_NGINX_IMAGE=images['nginx'])
    private_write(path, ''.join(k + '=' + values[k] + '\n' for k in sorted(values)).encode())
    return path


def compose(release, env, *args):
    return run(['docker', 'compose', '--project-name', PROJECT, '--env-file', str(env),
                '--file', str(release / 'compose.yml'), *args])


def validate_compose(model, sha, release_dir=None):
    services = model.get('services', {})
    if set(services) != {'db', 'backend', 'frontend', 'relay'}:
        raise ValueError('unexpected service')
    expected_tmpfs = {'db': [], 'backend': ['/tmp:size=64m,mode=1777'],
                      'frontend': ['/tmp:size=8m,mode=1777'], 'relay': ['/tmp:size=8m,mode=1777']}
    for name, expected in expected_tmpfs.items():
        mounts = services[name].get('tmpfs', [])
        if not isinstance(mounts, list) or any(not isinstance(mount, str) or not mount.split(':', 1)[0].startswith('/') for mount in mounts):
            raise ValueError('tmpfs mount paths must be absolute')
        if mounts != expected:
            raise ValueError('unexpected tmpfs target, size or mode for ' + name)
    if set(model.get('networks', {})) != {'test_internal', 'test_ingress'}:
        raise ValueError('exactly the two private Test-owned networks are required')
    for key, suffix, internal in [('test_internal', '_internal', True), ('test_ingress', '_ingress', False)]:
        network = model['networks'][key]
        if network.get('name') != PROJECT + suffix or network.get('driver') != 'bridge' or network.get('internal', False) is not internal or network.get('external') or network.get('driver_opts'):
            raise ValueError('unexpected Test network name, driver or isolation')
    if set(model.get('volumes', {})) != {'test_postgres'} or model['volumes']['test_postgres'].get('name') != PROJECT + '_postgres':
        raise ValueError('unexpected persistent volume')
    db_mounts = services['db'].get('volumes', [])
    if len(db_mounts) != 1 or db_mounts[0].get('type') != 'volume' or db_mounts[0].get('source') != 'test_postgres' or db_mounts[0].get('target') != '/var/lib/postgresql/data':
        raise ValueError('database must use only its own named volume')
    if services['backend'].get('volumes') or services['frontend'].get('volumes'):
        raise ValueError('application host mounts forbidden')
    relay_mounts = services['relay'].get('volumes', [])
    if len(relay_mounts) != 1 or relay_mounts[0].get('type') != 'bind' or not relay_mounts[0].get('read_only') or relay_mounts[0].get('target') != '/etc/nginx/nginx.conf':
        raise ValueError('relay may mount only its read-only configuration')
    if release_dir is not None and relay_mounts[0].get('source') != str(Path(release_dir) / 'relay.conf'):
        raise ValueError('relay mount outside verified Test release')
    for name in ('db', 'backend', 'frontend'):
        expected = {'test_internal', 'test_ingress'} if name == 'frontend' else {'test_internal'}
        if set(services[name].get('networks', {})) != expected or services[name].get('network_mode'):
            raise ValueError('application connected to another network')
    if services['frontend']['networks']['test_ingress'].get('gw_priority') != 1:
        raise ValueError('frontend publication must use its dedicated Test ingress network')
    if services['db'].get('ports') or services['backend'].get('ports'):
        raise ValueError('DB/backend host ports forbidden')
    ports = services['frontend'].get('ports', [])
    if len(ports) != 1 or ports[0].get('host_ip') != '127.0.0.1' or str(ports[0].get('published')) != '28080' or ports[0].get('target') != 8080:
        raise ValueError('frontend must bind only 127.0.0.1:28080')
    if services['relay'].get('network_mode') != 'host' or services['relay'].get('ports'):
        raise ValueError('unexpected relay topology')
    if services['backend']['image'] != 'restaurant-template-test-backend:' + sha or services['frontend']['image'] != 'restaurant-template-test-frontend:' + sha:
        raise ValueError('candidate image identity mismatch')
    env = services['backend']['environment']
    if env.get('SPRING_PROFILES_ACTIVE') != 'template-test' or env.get('SPRING_DATASOURCE_URL') != 'jdbc:postgresql://db:5432/restaurant_template_test':
        raise ValueError('Test profile/database boundary violation')
    for name, cap in [('backend', 512), ('db', 128), ('frontend', 32), ('relay', 16)]:
        limit = int(services[name].get('mem_limit', 0))
        if limit > cap * 1024 ** 2 or limit <= 0:
            raise ValueError('missing/excessive memory limit')
        if services[name].get('privileged') or services[name].get('pid') == 'host' or services[name].get('devices'):
            raise ValueError('host privileges forbidden')
    for name in ('backend', 'frontend', 'relay'):
        if not services[name].get('read_only') or services[name].get('cap_drop') != ['ALL'] or 'no-new-privileges:true' not in services[name].get('security_opt', []):
            raise ValueError('application privilege restrictions missing')


def deploy(release, sha, images):
    before = protected_snapshot()
    resources = admission()
    verify_existing_resources()
    verify_port_owners()
    env = runtime_env(sha, images)
    model = json.loads(compose(release, env, 'config', '--format', 'json'))
    validate_compose(model, sha, release)
    # Base images are official fixed digests, never production application images.
    for image in (images['jre'], images['nginx'], images['postgres']):
        run(['docker', 'pull', '--platform', 'linux/amd64', image])
    for kind, base_arg, base in [('backend', 'JRE_BASE', images['jre']), ('frontend', 'NGINX_BASE', images['nginx'])]:
        run(['docker', 'build', '--network', 'none', '--pull=false', '--platform', 'linux/amd64',
             '--build-arg', base_arg + '=' + base, '--build-arg', 'CODE_SHA=' + sha,
             '--file', str(release / ('Dockerfile.' + kind)), '--tag', PROJECT + '-' + kind + ':' + sha, str(release)])
    # The relay's bind file is public configuration; secrets stay in private/runtime.env.
    (release / 'relay.conf').chmod(0o644)
    verify_existing_resources()
    verify_port_owners()
    compose(release, env, 'up', '-d', '--no-build', '--pull', 'never')
    ready = False
    for _ in range(90):
        try:
            with urllib.request.urlopen('http://127.0.0.1:28080/api/v1/system/health', timeout=3) as response:
                ready = response.status == 200 and json.load(response).get('data', {}).get('status') == 'UP'
            if ready: break
        except (OSError, ValueError): pass
        time.sleep(2)
    if protected_snapshot() != before: raise RuntimeError('Protected environment identity changed; stop and investigate')
    if not ready: raise RuntimeError('Test health did not become UP; Test resources preserved, no shared entry changed')
    verify_runtime(sha)
    evidence(release, sha, resources)


def effective_config():
    raw = run(['docker', 'cp', EDGE + ':/etc/nginx/conf.d/default.conf', '-'])
    with tarfile.open(fileobj=io.BytesIO(raw), mode='r:*') as t:
        members = [m for m in t.getmembers() if m.isfile()]
        if len(members) != 1: raise ValueError('unexpected edge archive')
        return t.extractfile(members[0]).read()


def container_file_identity(path):
    allowed = ('/etc/nginx/conf.d/default.conf', '/etc/nginx/templates/default.conf.template')
    if path not in allowed: raise ValueError('unexpected edge file')
    script = 'test ! -L "$1" && test -f "$1" && stat -c "%d:%i" "$1"'
    text = run(['docker', 'exec', EDGE, 'sh', '-c', script, 'sh', path]).decode().strip()
    if not re.fullmatch(r'\d+:\d+', text): raise ValueError('edge identity unavailable')
    return tuple(map(int, text.split(':')))


def effective_state():
    identity = container_file_identity('/etc/nginx/conf.d/default.conf')
    data = effective_config()
    if identity != container_file_identity('/etc/nginx/conf.d/default.conf'):
        raise ValueError('effective edge file changed during observation')
    return {'identity': identity, 'data': data}


def write_effective(data, expected):
    # Keep a controlled FD inside the existing edge; CAS inode+bytes at the write,
    # preserve that inode, and never trust a pathname reopened after validation.
    identity = ':'.join(str(v) for v in expected['identity'])
    script = '''set -eu
p=/etc/nginx/conf.d/default.conf
test ! -L "$p"
test -f "$p"
exec 3<> "$p"
test "$(stat -Lc '%d:%i' /proc/self/fd/3)" = "$1"
test "$(stat -c '%d:%i' "$p")" = "$1"
test "$(sha256sum /proc/self/fd/3 | cut -d ' ' -f 1)" = "$2"
cat >&3
truncate -s "$3" /proc/self/fd/3
'''
    run(['docker', 'exec', '-i', EDGE, 'sh', '-c', script, 'sh', identity, sha256(expected['data']), str(len(data))], data)
    after = effective_state()
    if after['identity'] != expected['identity'] or after['data'] != data:
        raise RuntimeError('effective write did not retain the controlled inode/content')


@contextlib.contextmanager
def locked_edge_source():
    handles = []
    try:
        for path in (*SHARED_LOCKS, EDGE_TEMPLATE):
            descriptor = os.open(path, os.O_RDWR | os.O_NOFOLLOW)
            handles.append(descriptor)
            observed = os.fstat(descriptor)
            named = path.lstat()
            if not stat.S_ISREG(observed.st_mode) or (observed.st_dev, observed.st_ino) != (named.st_dev, named.st_ino):
                raise ValueError('shared lock/source is not a stable regular file')
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        source = handles[-1]
        observed = os.fstat(source)
        yield source, (observed.st_dev, observed.st_ino)
    finally:
        for descriptor in reversed(handles): os.close(descriptor)


def source_bytes(descriptor, identity):
    named, held = EDGE_TEMPLATE.lstat(), os.fstat(descriptor)
    if not stat.S_ISREG(named.st_mode) or (named.st_dev, named.st_ino) != identity or (held.st_dev, held.st_ino) != identity:
        raise ValueError('shared edge source inode changed; no write/rollback')
    if container_file_identity('/etc/nginx/templates/default.conf.template') != identity:
        raise ValueError('source path no longer identifies the edge bind mount')
    if held.st_size > 1024 * 1024: raise ValueError('unexpectedly large shared edge configuration')
    return os.pread(descriptor, held.st_size + 1, 0)


def write_source(descriptor, data, progress):
    os.lseek(descriptor, 0, os.SEEK_SET)
    progress['written'] = 0
    while progress['written'] < len(data):
        count = os.write(descriptor, data[progress['written']:])
        if count <= 0: raise OSError('short edge write')
        progress['written'] += count
    os.ftruncate(descriptor, len(data)); os.fsync(descriptor)


def edge_block(tls):
    http = '''server {
    listen 80;
    server_name test.lanzhounoodlesmtl.com;
    location /.well-known/acme-challenge/ { root /var/www/certbot; try_files $uri =404; }
    location / { HTTP_ACTION }
}
'''.replace('HTTP_ACTION', 'return 308 https://test.lanzhounoodlesmtl.com$request_uri;' if tls else "return 503 'Test preparing\\n';")
    https = '''server {
    listen 443 ssl;
    server_name test.lanzhounoodlesmtl.com;
    client_max_body_size 4m;
    ssl_certificate /etc/letsencrypt/live/test.lanzhounoodlesmtl.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/test.lanzhounoodlesmtl.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    if ($ssl_server_name != test.lanzhounoodlesmtl.com) { return 421; }
    if ($host != test.lanzhounoodlesmtl.com) { return 421; }
    location ^~ /ws { return 403; }
    location / {
        proxy_pass http://172.18.0.1:28480;
        proxy_http_version 1.1;
        proxy_set_header Host test.lanzhounoodlesmtl.com;
        proxy_set_header X-Forwarded-Host test.lanzhounoodlesmtl.com;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header Forwarded '';
        proxy_connect_timeout 5s;
        proxy_read_timeout 60s;
    }
}
''' if tls else ''
    return BEGIN + (http + https).encode() + END


def validate_edge(data):
    wrapper = b'worker_processes 1; error_log stderr; events {} http { include /etc/nginx/mime.types;\n' + data + b'\n}'
    run(['docker', 'exec', '-i', EDGE, 'nginx', '-t', '-c', '/dev/stdin'], wrapper)


def head_status(url):
    with urllib.request.urlopen(urllib.request.Request(url, method='HEAD'), timeout=10) as r:
        return r.status


def apply_edge(tls, expected_hash, rollback=False):
    if not HEX64.fullmatch(expected_hash or ''): raise ValueError('explicit observed edge hash required')
    with locked_edge_source() as (descriptor, identity):
        before = protected_snapshot()
        old = source_bytes(descriptor, identity)
        effective = effective_state()
        if sha256(old) != expected_hash or effective['data'] != old:
            raise ValueError('shared edge drift; do not overwrite')
        saved = ROOT / 'ingress/original.conf'
        if not saved.exists():
            if BEGIN in old or DOMAIN.encode() in old: raise ValueError('unrecognized pre-existing Test entry')
            private_write(saved, old)
        original = saved.read_bytes()
        if old != original and not (old.startswith(original + BEGIN) and old.endswith(END) and old.count(BEGIN) == 1):
            raise ValueError('non-Test changes since backup; manual review required')
        candidate = original if rollback else original + edge_block(tls)
        validate_edge(candidate)
        continuity = {u: head_status(u) for u in ['https://pos.lanzhounoodlesmtl.com/', 'https://staging-pos.lanzhounoodlesmtl.com/']}
        private_write(ROOT / ('ingress/before-' + expected_hash + '.conf'), old)
        # Recheck both identities and bytes AFTER all slow validation/HEAD work.
        if source_bytes(descriptor, identity) != old or effective_state() != effective:
            raise ValueError('shared edge changed after validation; no mutation')
        progress = {'written': 0}
        try:
            write_source(descriptor, candidate, progress)
            if source_bytes(descriptor, identity) != candidate: raise ValueError('source changed during write')
            write_effective(candidate, effective)
            if source_bytes(descriptor, identity) != candidate or effective_state() != {'identity': effective['identity'], 'data': candidate}:
                raise ValueError('shared edge changed before reload')
            run(['docker', 'exec', EDGE, 'nginx', '-t'])
            run(['docker', 'exec', EDGE, 'nginx', '-s', 'reload'])
            if protected_snapshot() != before: raise RuntimeError('protected container identity changed')
            if any(head_status(u) != status for u, status in continuity.items()): raise RuntimeError('existing entrance continuity failed')
            if source_bytes(descriptor, identity) != candidate or effective_state() != {'identity': effective['identity'], 'data': candidate}:
                raise ValueError('shared edge changed after reload')
        except Exception as error:
            current_source = source_bytes(descriptor, identity)
            current_effective = effective_state()
            known_partial = candidate[:progress['written']] + old[progress['written']:]
            if current_source not in (old, candidate, known_partial) or current_effective['identity'] != effective['identity'] or current_effective['data'] not in (old, candidate):
                raise RuntimeError('shared edge changed/has unproven partial content; automatic rollback refused, private backup preserved') from error
            # No third-party bytes/inode are overwritten; each write also CASes its held identity.
            if source_bytes(descriptor, identity) != current_source: raise RuntimeError('source changed before rollback; refused') from error
            if current_source != old: write_source(descriptor, old, {})
            if current_effective['data'] != old: write_effective(old, current_effective)
            run(['docker', 'exec', EDGE, 'nginx', '-t'])
            run(['docker', 'exec', EDGE, 'nginx', '-s', 'reload'])
            raise
    print(json.dumps({'test_edge_sha256': sha256(candidate), 'old_vhost_prefix_preserved': True,
                      'existing_container_identity_unchanged': True, 'existing_head_status': continuity}))


def issue_cert():
    # Explicit Test cert name/domain, no standalone/nginx plugin/other cert renewal.
    work, logs = ROOT / 'ingress/acme-work', ROOT / 'ingress/acme-logs'
    work.mkdir(mode=0o700, parents=True, exist_ok=True); logs.mkdir(mode=0o700, exist_ok=True)
    run(['sudo', '-n', 'certbot', '--config', '/dev/null', 'certonly', '--no-directory-hooks',
         '--pre-hook', '', '--post-hook', '', '--deploy-hook', '', '--webroot', '--webroot-path', str(WEBROOT),
         '--config-dir', str(CERTROOT), '--work-dir', str(work), '--logs-dir', str(logs),
         '--cert-name', DOMAIN, '-d', DOMAIN, '--non-interactive', '--agree-tos',
         '--register-unsafely-without-email', '--no-eff-email'])
    print('Test-only certificate issued; no proxy reload performed')


def evidence(release, sha, resources=None):
    docs = verify_runtime(sha)
    items = [{'name': d['Name'], 'image_id': d['Image'], 'started': d['State']['StartedAt'],
              'running': d['State']['Running'], 'networks': sorted(d['NetworkSettings']['Networks'])} for d in docs]
    for d in docs:
        if d['Name'] in ('/' + PROJECT + '-backend-1', '/' + PROJECT + '-frontend-1'):
            if d['Config'].get('Labels', {}).get('org.opencontainers.image.revision') != sha:
                raise ValueError('running application image revision mismatch')
    ledger = run(['docker', 'exec', PROJECT + '-db-1', 'psql', '-U', 'template_test', '-d',
                  'restaurant_template_test', '-At', '-c',
                  'SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank']).decode().strip()
    rows = [line.split('|') for line in ledger.splitlines()]
    expected = set(json.loads((release / 'manifest.json').read_text())['expected_migrations'])
    if not rows or any(len(row) != 3 or row[2] != 't' for row in rows) or {row[1] for row in rows} != expected:
        raise ValueError('Test Flyway ledger differs from exact source migration inventory')
    data = {'code_sha': sha, 'observed_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'containers': items, 'flyway_version_success_rows': ledger.splitlines(),
            'config_source': str(release / 'compose.yml'),
            'compose_sha256': sha256((release / 'compose.yml').read_bytes()),
            'private_env_path': str(ROOT / 'private/runtime.env'), 'resource_admission': resources,
            'websocket_public_policy': 'DENY /ws; F31 NOT CLOSED', 'transport': 'MOCK_ONLY_INTERNAL_NETWORK',
            'physical_print_tested': False, 'production_release_authorized': False}
    private_write(ROOT / ('evidence/' + sha + '.json'), json.dumps(data, indent=2).encode())
    print(json.dumps(data))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=['deploy', 'evidence', 'edge-http', 'issue-cert', 'edge-https', 'edge-rollback'])
    p.add_argument('--sha', required=True)
    p.add_argument('--apply-tested-candidate', help='exact SHA released by Integration Owner')
    p.add_argument('--shared-entry-reviewed', action='store_true')
    p.add_argument('--expected-edge-sha256')
    a = p.parse_args()
    release = ROOT / 'releases' / a.sha
    _, images = verify_release(release, a.sha)
    if a.action != 'evidence' and a.apply_tested_candidate != a.sha:
        raise ValueError('exact candidate release acknowledgment required')
    if a.action.startswith('edge-') or a.action == 'issue-cert':
        if not a.shared_entry_reviewed: raise ValueError('shared Test vhost plan must be reviewed first')
        admission(require_capacity=False)
        if a.action != 'edge-rollback': verify_runtime(a.sha)
        if a.action == 'issue-cert': issue_cert()
        else: apply_edge(a.action == 'edge-https', a.expected_edge_sha256, a.action == 'edge-rollback')
    elif a.action == 'deploy': deploy(release, a.sha, images)
    else: evidence(release, a.sha)


if __name__ == '__main__':
    try: main()
    except (ValueError, OSError, RuntimeError, subprocess.SubprocessError) as error:
        raise SystemExit(str(error))
