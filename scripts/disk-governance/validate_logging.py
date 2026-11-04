#!/usr/bin/env python3
"""Offline semantic checks; optionally run installed logrotate in debug mode."""
import argparse
import configparser
import json
from pathlib import Path
import re
import shutil
import subprocess


def validate(root):
    expected = {'driver': 'local', 'options': {'max-size': '10m', 'max-file': '3'}}
    daemon = json.loads((root / 'docker-logging.json').read_text())
    if daemon != {'log-driver': 'local', 'log-opts': expected['options']}:
        raise ValueError('unexpected Docker defaults')
    compose = json.loads((root / 'compose-logging.json').read_text())
    if set(compose.get('services', {})) != {'db', 'backend', 'nginx'}:
        raise ValueError('unexpected Compose service scope')
    if any(service != {'logging': expected} for service in compose['services'].values()):
        raise ValueError('logging fragment changes other runtime settings')
    journal = configparser.ConfigParser()
    journal.read(root / 'journald.conf')
    if journal.sections() != ['Journal'] or dict(journal['Journal']) != {'maxfilesec': '1day'}:
        raise ValueError('journald fragment widens expiry or scope')
    rotation = (root / 'maintenance.logrotate').read_text()
    blocks = re.findall(r'([^{}]+)\{([^{}]+)\}', rotation)
    if len(blocks) != 2:
        raise ValueError('unexpected logrotate scope')
    for (path, body), user in zip(blocks, ['root', 'ubuntu']):
        expected_path = '/var/log/restaurant-disk-retention/' + ('journal.log' if user == 'root' else 'user/*.log')
        directives = {line.strip() for line in body.splitlines() if line.strip()}
        required = {'daily', 'rotate 30', 'maxage 30', 'missingok', 'notifempty', 'compress',
                    'delaycompress', 'create 0640 ' + user + ' adm'}
        if user == 'ubuntu':
            required.add('su ubuntu adm')
        if path.strip() != expected_path or directives != required:
            raise ValueError('unexpected logrotate directives')
    return {'docker_and_compose': 'PASS', 'journald': 'PASS', 'logrotate_semantics': 'PASS'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pending', type=Path, default=Path(__file__).parent / 'pending')
    parser.add_argument('--native-logrotate-debug', action='store_true')
    args = parser.parse_args()
    result = validate(args.pending)
    if args.native_logrotate_debug:
        binary = shutil.which('logrotate')
        if not binary:
            raise RuntimeError('logrotate unavailable; native validation not performed')
        # -d performs no rotation and does not update the state file.
        subprocess.run([binary, '--debug', '--state', '/dev/null', str(args.pending / 'maintenance.logrotate')], check=True)
        result['native_logrotate_debug'] = 'PASS'
    else:
        result['native_logrotate_debug'] = 'NOT_RUN'
    result['installed_or_activated'] = False
    print(json.dumps(result, sort_keys=True))


if __name__ == '__main__':
    main()
