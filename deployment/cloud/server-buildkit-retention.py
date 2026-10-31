#!/usr/bin/env python3
"""Shared-host cache policy: private unused BuildKit records older than 30 days."""
import argparse
import datetime
import fcntl
import http.client
import json
import os
from pathlib import Path
import re
import socket
import stat
import subprocess
import urllib.request

AGE = 30 * 86400
ROOT = Path('/srv/restaurant-pos/staging/state')
LOCKS = ('server-disk-maintenance.lock', 'restaurant-pos-staging-hygiene.lock',
         'al003s-acceptance.lock', 'phase-b-part1-acceptance.lock', 'phase-b-part2-acceptance.lock')
COMMAND = ['docker', '--context', 'default', 'buildx', 'prune', '--builder', 'default',
           '--filter', 'until=720h', '--force', '--verbose']


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def output(args):
    return subprocess.check_output(args, text=True).strip()


class Docker(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(90)
        self.sock.connect('/var/run/docker.sock')


def get(path):
    connection = Docker('localhost')
    try:
        connection.request('GET', path)
        response = connection.getresponse()
        body = response.read()
        require(response.status == 200, 'Docker read API unavailable')
        return json.loads(body)
    finally:
        connection.close()


def candidates(records, now):
    eligible, retained = [], []
    for record in records:
        require(type(record['InUse']) is bool and type(record['Shared']) is bool
                and type(record['Size']) is int and record['Size'] >= 0, 'invalid cache metadata')
        age = None
        value = record.get('LastUsedAt')
        if value:
            stamp = datetime.datetime.fromisoformat(
                re.sub(r'(\.\d{6})\d+', r'\1', value).replace('Z', '+00:00'))
            require(stamp.tzinfo is not None, 'cache timestamp lacks timezone')
            age = (now - stamp).total_seconds()
            require(age >= 0, 'future cache timestamp')
        unused = not record['InUse'] and not record['Shared']
        require(not unused or age is not None, 'unused cache lacks last-use timestamp')
        selected = unused and record['Type'] not in ('internal', 'frontend') and age > AGE
        (eligible if selected else retained).append(record)
    return eligible, retained


def idle():
    endpoint = output(['docker', '--context', 'default', 'context', 'inspect', 'default',
                       '--format', '{{.Endpoints.docker.Host}}'])
    require(endpoint == 'unix:///var/run/docker.sock', 'unexpected Docker endpoint')
    builder = output(['docker', '--context', 'default', 'buildx', 'inspect', 'default'])
    require(re.search(r'^Driver:\s+docker$', builder, re.M)
            and re.search(r'^Status:\s+running$', builder, re.M), 'unexpected default builder')
    history = [json.loads(line) for line in output([
        'docker', '--context', 'default', 'buildx', 'history', 'ls', '--builder', 'default',
        '--format', 'json']).splitlines() if line.strip()]
    require(all(item['status'].lower() in ('completed', 'failed', 'canceled', 'cancelled', 'error')
                and item.get('completed_at') for item in history), 'build active or unknown')
    for process in Path('/proc').iterdir():
        if not process.name.isdigit():
            continue
        try:
            comm = (process / 'comm').read_text().strip()
            args = (process / 'cmdline').read_bytes().replace(b'\0', b' ').decode(errors='replace')
        except FileNotFoundError:
            continue
        require(comm not in ('docker', 'docker-buildx', 'docker-compose', 'buildctl'), 'another build/Docker client is active')
        if comm in ('bash', 'sh', 'python3', 'python3.10', 'python3.11', 'python3.12'):
            require(not re.search(r'(?:/| )[^ /]*(?:deploy|release|rotation|promot)[^ /]*\.(?:sh|py)', args),
                    'deployment process active')
    check_health()


def check_health():
    for port in (80, 18080):
        with urllib.request.urlopen('http://127.0.0.1:%d/api/v1/system/health' % port, timeout=10) as response:
            require(response.status == 200, 'service unhealthy')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true')
    args = parser.parse_args()
    require(ROOT.resolve() == ROOT and ROOT.is_dir(), 'unexpected state directory')
    descriptors = []
    try:
        for name in LOCKS:
            descriptor = os.open(ROOT / name, os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
            descriptors.append(descriptor)
            value = os.fstat(descriptor)
            require(stat.S_ISREG(value.st_mode) and value.st_uid == os.getuid()
                    and value.st_nlink == 1 and stat.S_IMODE(value.st_mode) == 0o600, 'unsafe lock metadata')
            fcntl.flock(descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
        idle()
        prefix = '/v' + get('/version')['ApiVersion']
        before = get(prefix + '/system/df')
        now = datetime.datetime.now(datetime.timezone.utc)
        eligible, retained = candidates(before['BuildCache'], now)
        print(json.dumps({'observed_utc': now.isoformat(), 'candidate_ids': [r['ID'] for r in eligible],
                          'candidate_bytes': sum(r['Size'] for r in eligible),
                          'retained_ids': [r['ID'] for r in retained], 'command': COMMAND}), flush=True)
        if args.execute and eligible:
            idle()  # Recheck other deployment paths immediately before prune.
            subprocess.run(COMMAND, check=True)
            check_health()
            print(json.dumps({'post_cleanup_health': 'production/staging HTTP 200'}), flush=True)
    finally:
        for descriptor in reversed(descriptors):
            os.close(descriptor)


if __name__ == '__main__':
    main()
