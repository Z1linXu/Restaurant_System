#!/usr/bin/env python3
"""Owner-authorized, exact old Template Test retirement. Default action only plans.

Never prunes, follows symlinks, reads environment secrets, removes images,
recursively removes directories, or invokes Compose. Shared ingress is hash-only.
"""
import argparse
import contextlib
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import urllib.request
from remote import run, protected_snapshot, SHARED_LOCKS, EDGE, EDGE_TEMPLATE, BASE_PROTECTED

PROJECT = 'restaurant-template-test'
SERVICES = ('relay', 'frontend', 'backend', 'db')
NAMES = {PROJECT + '-' + service + '-1': service for service in SERVICES}
VOLUME = PROJECT + '_postgres'
NETWORKS = (PROJECT + '_internal', PROJECT + '_ingress')
ENV_FILE = Path('/srv/restaurant-pos/test/private/runtime.env')
FULL_ID = re.compile('[0-9a-f]{64}')


def digest(document):
    return hashlib.sha256(json.dumps(document, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def require_id(value):
    if not isinstance(value, str) or not FULL_ID.fullmatch(value):
        raise ValueError('full immutable Docker ID required')


def paths_overlap(left, right):
    """Canonical mount aliases/parents are shared; ambiguity is retained, never deleted."""
    if not left or not right:
        return True
    try:
        a, b = Path(left).resolve(strict=True), Path(right).resolve(strict=True)
    except (OSError, RuntimeError):
        return True
    return a == b or a in b.parents or b in a.parents


def plan_resources(containers, volumes, networks):
    """Pure ownership check, also exercised with synthetic fixtures."""
    selected = []
    keep = []
    for row in containers:
        name = row['name'].lstrip('/')
        if name not in NAMES:
            continue
        require_id(row['id'])
        if row['project'] != PROJECT or row['service'] != NAMES[name]:
            raise ValueError('old Test container identity/Compose labels disagree')
        if row['running'] or row['restarting']:
            raise ValueError('old Test must already be stopped; retirement never stops an unknown service')
        selected.append(row)
    selected.sort(key=lambda row: SERVICES.index(row['service']))
    ids = {row['id'] for row in selected}
    others = [row for row in containers if row['id'] not in ids]
    storage = []
    for volume in volumes:
        if volume['Name'] != VOLUME:
            continue
        labels = volume.get('Labels') or {}
        referenced = any((m.get('Type') == 'volume' and m.get('Name') == VOLUME)
                         or paths_overlap(m.get('Source'), volume.get('Mountpoint'))
                         for c in others for m in c['mounts'])
        if labels.get('com.docker.compose.project') != PROJECT or labels.get('com.docker.compose.volume') != 'test_postgres' or volume['Driver'] != 'local' or volume.get('Options') or referenced:
            keep.append({'kind': 'volume', 'name': VOLUME, 'reason': 'ownership or exclusive use not proven'})
        else:
            storage.append({'name': VOLUME, 'driver': volume['Driver'], 'labels': labels, 'created_at': volume.get('CreatedAt'), 'mountpoint': volume.get('Mountpoint')})
    owned_networks = []
    for network in networks:
        if network['Name'] not in NETWORKS:
            continue
        require_id(network['Id'])
        labels = network.get('Labels') or {}
        referenced = set((network.get('Containers') or {}).keys()) - ids
        referenced.update(c['id'] for c in others if network['Name'] in c['networks'])
        if labels.get('com.docker.compose.project') != PROJECT or network['Driver'] != 'bridge' or referenced:
            keep.append({'kind': 'network', 'name': network['Name'], 'reason': 'ownership or exclusive use not proven'})
        else:
            owned_networks.append({'id': network['Id'], 'name': network['Name'], 'labels': labels})
    # A bind-backed database is deliberately retained unless separately proven.
    for c in selected:
        for m in c['mounts']:
            if c['service'] == 'db' and m.get('Type') == 'bind':
                keep.append({'kind': 'bind', 'path': m.get('Source'), 'reason': 'no reviewed bind-directory deletion in this operator'})
    return {'containers': selected, 'volumes': storage, 'networks': owned_networks, 'retained': keep,
            'missing_containers': sorted(set(NAMES) - {r['name'].lstrip('/') for r in selected}),
            'images': 'RETAINED: current and rollback dependency proof is outside this bounded retirement'}


def documents():
    names = run(['docker', 'ps', '-a', '--format', '{{.ID}}']).decode().splitlines()
    fmt = '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Image}},"running":{{json .State.Running}},"restarting":{{json .State.Restarting}},"project":{{json (index .Config.Labels "com.docker.compose.project")}},"service":{{json (index .Config.Labels "com.docker.compose.service")}},"mounts":{{json .Mounts}},"networks":{{json .NetworkSettings.Networks}}}'
    rows = [json.loads(line) for line in run(['docker', 'inspect', '--format', fmt, *names]).decode().splitlines()] if names else []
    volumes = []
    volume_names = run(['docker', 'volume', 'ls', '--format', '{{.Name}}']).decode().splitlines()
    if VOLUME in volume_names:
        volumes = json.loads(run(['docker', 'volume', 'inspect', VOLUME]))
    networks = []
    network_names = run(['docker', 'network', 'ls', '--format', '{{.Name}}']).decode().splitlines()
    for name in NETWORKS:
        if name in network_names:
            networks.extend(json.loads(run(['docker', 'network', 'inspect', name])))
    return rows, volumes, networks


def ingress_proof():
    template = hashlib.sha256(EDGE_TEMPLATE.read_bytes()).hexdigest()
    effective = run(['docker', 'exec', EDGE, 'sha256sum', '/etc/nginx/conf.d/default.conf']).decode().split()[0]
    if not re.fullmatch('[0-9a-f]{64}', effective):
        raise ValueError('shared ingress digest missing')
    return {'template_sha256': template, 'effective_sha256': effective}


def production_health():
    with urllib.request.urlopen('https://pos.lanzhounoodlesmtl.com/api/v1/system/health', timeout=10) as response:
        payload = json.load(response)
        if response.status != 200 or not payload.get('success') or payload.get('data', {}).get('status') != 'UP':
            raise ValueError('Production health must be UP before/after old Test retirement')
    return 'HTTP200_UP'


@contextlib.contextmanager
def operations_lock():
    files = []
    try:
        for path in SHARED_LOCKS:
            descriptor = os.open(path, os.O_RDWR | os.O_NOFOLLOW | os.O_CLOEXEC)
            file = os.fdopen(descriptor, 'r+')
            files.append(file)
            if not stat.S_ISREG(os.fstat(file.fileno()).st_mode):
                raise ValueError('existing shared operations lock is not a regular file')
            fcntl.flock(file, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield
    finally:
        for file in reversed(files):
            file.close()


def env_identity(containers):
    if not ENV_FILE.exists():
        return None
    if ENV_FILE.is_symlink() or any(parent.is_symlink() for parent in ENV_FILE.parents):
        return None
    observed = ENV_FILE.stat()
    if not stat.S_ISREG(observed.st_mode) or observed.st_nlink != 1:
        return None
    if any(paths_overlap(m.get('Source'), str(ENV_FILE)) for c in containers if c['name'].lstrip('/') not in NAMES for m in c['mounts']):
        return None
    return {'path': str(ENV_FILE), 'device': observed.st_dev, 'inode': observed.st_ino, 'size': observed.st_size}


def build_plan():
    containers, volumes, networks = documents()
    resources = plan_resources(containers, volumes, networks)
    protected = protected_snapshot()
    if set(protected) != {'/' + n for n in BASE_PROTECTED}:
        raise ValueError('complete Production/Staging protected inventory required')
    if not all(not row['restarting'] for row in protected.values()):
        raise ValueError('protected runtime is unstable')
    return {'version': 1, 'project': PROJECT, 'resources': resources, 'protected': protected,
            'ingress': ingress_proof(), 'production_health': production_health(),
            'private_env': env_identity(containers), 'database_backup': 'OWNER_EXPLICITLY_WAIVED_FOR_OLD_TEMPLATE_TEST_ONLY'}


def apply_plan(plan, expected):
    if digest(plan) != expected or plan != build_plan():
        raise ValueError('exact retirement plan changed; no deletion allowed')
    completed = []
    try:
        for row in plan['resources']['containers']:
            run(['docker', 'rm', row['id']])  # no --force; stopped IDs only
            completed.append({'kind': 'container', 'id': row['id'], 'name': row['name']})
        for row in plan['resources']['volumes']:
            # Docker refuses in-use volumes, including references from stopped containers.
            run(['docker', 'volume', 'rm', row['name']])
            completed.append({'kind': 'volume', 'name': row['name']})
        for row in plan['resources']['networks']:
            run(['docker', 'network', 'rm', row['id']])
            completed.append({'kind': 'network', **row})
        identity = plan.get('private_env')
        if identity is not None:
            current = env_identity(documents()[0])
            if identity != current:
                raise ValueError('private old Test config identity changed; retained')
            ENV_FILE.unlink()  # one exact proven file, never a directory or source tree
            completed.append({'kind': 'private_config', 'path': str(ENV_FILE)})
        if protected_snapshot() != plan['protected'] or ingress_proof() != plan['ingress']:
            raise ValueError('protected runtime or shared ingress changed')
        return {'status': 'PASS', 'deleted': completed, 'retained': plan['resources']['retained'],
                'images': plan['resources']['images'], 'production_health': production_health(),
                'protected_unchanged': True, 'shared_ingress_unchanged': True}
    except Exception as error:
        return {'status': 'FAIL_PARTIAL', 'deleted': completed, 'error': str(error),
                'automatic_old_test_restore': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['plan', 'apply'])
    parser.add_argument('--plan')
    parser.add_argument('--apply-plan-sha256')
    args = parser.parse_args()
    with operations_lock():
        if args.action == 'plan':
            plan = build_plan()
            print(json.dumps({'plan': plan, 'plan_sha256': digest(plan)}, sort_keys=True))
        else:
            if not args.plan or not args.apply_plan_sha256:
                raise ValueError('reviewed exact plan and digest are required')
            record = json.loads(Path(args.plan).read_text())
            result = apply_plan(record['plan'], args.apply_plan_sha256)
            print(json.dumps(result, sort_keys=True))
            if result['status'] != 'PASS':
                raise SystemExit(1)


if __name__ == '__main__':
    main()
