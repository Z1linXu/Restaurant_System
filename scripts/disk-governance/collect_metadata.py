#!/usr/bin/env python3
"""Bounded, read-only metadata for the existing shared-host retention policy.

No environment values, business file contents, cleanup, locks, or installations.
Run locally on the target host only after the Owner-authorized read-only gate.
"""
import datetime as dt
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import socket
import stat
import subprocess

ROOTS = ('/srv/restaurant-pos/staging/releases', '/srv/restaurant-pos/production-tools/releases')
ENTRY_BUDGET = 10000
POLICY_FILES = (
    '/etc/cron.d/restaurant-disk-retention',
    '/etc/logrotate.d/restaurant-disk-retention',
    '/usr/local/lib/restaurant-disk-retention/server-buildkit-retention.py',
    '/usr/local/lib/restaurant-disk-retention/server-journal-retention.py',
    '/usr/local/lib/restaurant-disk-retention/staging-retention-run.sh',
    '/etc/docker/daemon.json',
    '/etc/systemd/journald.conf',
)


class Docker(http.client.HTTPConnection):
    def connect(self):
        self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self.sock.settimeout(40)
        self.sock.connect('/var/run/docker.sock')


def get(path):
    connection = Docker('localhost')
    try:
        connection.request('GET', path)
        response = connection.getresponse()
        body = response.read(16 * 1024 * 1024 + 1)
        if response.status != 200 or len(body) > 16 * 1024 * 1024:
            raise RuntimeError('Docker metadata unavailable or exceeds budget')
        return json.loads(body)
    finally:
        connection.close()


def identity(path):
    """Do not follow a symlink in any component of a fixed metadata path."""
    path = Path(path)
    for part in [*reversed(path.parents), path]:
        value = part.lstat()
        if stat.S_ISLNK(value.st_mode):
            raise RuntimeError('symlink metadata path retained')
    value = path.lstat()
    return {'device': value.st_dev, 'inode': value.st_ino, 'uid': value.st_uid,
            'mode': oct(stat.S_IMODE(value.st_mode)), 'mtime_ns': value.st_mtime_ns,
            'logical_bytes': value.st_size, 'allocated_bytes': value.st_blocks * 512,
            'regular': stat.S_ISREG(value.st_mode), 'directory': stat.S_ISDIR(value.st_mode)}


def collect():
    observed = dt.datetime.now(dt.timezone.utc).isoformat()
    errors = []
    version = get('/version')
    prefix = '/v' + version['ApiVersion']
    disk = get(prefix + '/system/df')
    info = get(prefix + '/info')
    container_records = get(prefix + '/containers/json?all=true')
    if len(container_records) > 1000:
        raise RuntimeError('container inventory exceeds budget')
    containers, logs, releases = [], [], []
    entry_count = 0
    for record in container_records:
        cid = record['Id']
        if not re.fullmatch('[a-f0-9]{64}', cid):
            raise RuntimeError('invalid container identity')
        value = get(prefix + '/containers/' + cid + '/json')
        labels = value['Config'].get('Labels') or {}
        row = {'id': cid, 'name': value['Name'], 'image_id': value['Image'],
               'running': value['State']['Running'], 'started_at': value['State']['StartedAt'],
               'revision': labels.get('org.opencontainers.image.revision'),
               'project': labels.get('com.docker.compose.project'),
               'compose_working_dir': labels.get('com.docker.compose.project.working_dir'),
               'compose_files': labels.get('com.docker.compose.project.config_files'),
               'working_dir': value['Config'].get('WorkingDir'),
               'mount_sources': [m['Source'] for m in value.get('Mounts', [])],
               'logging': {'Type': value['HostConfig']['LogConfig']['Type'],
                           'Config': {key: val for key, val in value['HostConfig']['LogConfig']['Config'].items()
                                      if key in ('max-size', 'max-file', 'compress', 'mode', 'max-buffer-size')}}}
        containers.append(row)
        # Only Docker-owned direct log files; no log contents and no recursive du.
        root = Path(info['DockerRootDir']) / 'containers' / cid
        driver = row['logging']['Type']
        logdir = root / 'local-logs' if driver == 'local' else root
        logfiles = []
        try:
            if not identity(logdir)['directory']:
                raise RuntimeError('log directory unavailable')
            for item in os.scandir(logdir):
                entry_count += 1
                if entry_count > ENTRY_BUDGET:
                    raise RuntimeError('metadata entry budget exceeded')
                if driver == 'json-file' and '-json.log' not in item.name:
                    continue
                if driver not in ('local', 'json-file'):
                    continue
                meta = identity(item.path)
                if meta['regular']:
                    logfiles.append({'path': item.path, **meta})
            logs.append({'container_id': cid, 'name': row['name'], 'driver': driver,
                         'files': logfiles, 'complete': driver in ('local', 'json-file')})
        except (OSError, RuntimeError) as error:
            errors.append({'scope': 'logs', 'object': cid, 'error': type(error).__name__})

    for root in ROOTS:
        try:
            if not identity(root)['directory']:
                raise RuntimeError('release root unavailable')
            entries = list(os.scandir(root))
            entry_count += len(entries)
            if entry_count > ENTRY_BUDGET:
                raise RuntimeError('metadata entry budget exceeded')
            for entry in sorted(entries, key=lambda e: e.name):
                # Unknown names are included and retained, never normalized.
                row = {'path': entry.path, 'sha': entry.name if re.fullmatch('[a-f0-9]{40}', entry.name) else None,
                       'references_complete': False, 'contains_windows_package': False,
                       'data_paths': [], 'identity_verified': False}
                try:
                    row['identity'] = identity(entry.path)
                    row['identity_verified'] = row['identity']['directory']
                    for relative in ('deployment/windows-pilot/package',
                                     'deployment/windows-pilot/restaurant-pos-windows-pilot.zip'):
                        path = Path(entry.path) / relative
                        try:
                            metadata = identity(path)
                        except FileNotFoundError:
                            continue
                        row['contains_windows_package'] = True
                        row['data_paths'].append({'path': str(path), **metadata})
                    database = Path(entry.path) / 'deployment/windows-pilot/package/database'
                    try:
                        if identity(database)['directory']:
                            for item in os.scandir(database):
                                entry_count += 1
                                if entry_count > ENTRY_BUDGET:
                                    raise RuntimeError('metadata entry budget exceeded')
                                meta = identity(item.path)
                                if meta['regular']:
                                    row['data_paths'].append({'path': item.path, **meta})
                    except FileNotFoundError:
                        pass
                except (OSError, RuntimeError) as error:
                    row['metadata_error'] = type(error).__name__
                releases.append(row)
        except (OSError, RuntimeError) as error:
            errors.append({'scope': 'releases', 'object': root, 'error': type(error).__name__})

    # This does not acquire/create any lock. Unknown reads retain every cache.
    active, operations_complete = [], True
    try:
        locks = subprocess.check_output(['lslocks', '--noheadings', '--output', 'PID,COMMAND,PATH'], text=True, timeout=5)
        if any('.production-ops.lock' in line or 'server-disk-maintenance.lock' in line for line in locks.splitlines()):
            active.append('DEPLOYMENT_OR_MAINTENANCE_LOCK')
        processes = list(Path('/proc').iterdir())
        if len(processes) > ENTRY_BUDGET:
            raise RuntimeError('process budget exceeded')
        for process in processes:
            if not process.name.isdigit():
                continue
            try:
                comm = (process / 'comm').read_text().strip()
                if comm in ('docker', 'docker-buildx', 'docker-compose', 'buildctl'):
                    active.append('DOCKER_CLIENT')
                elif comm in ('sh', 'bash', 'python', 'python3', 'python3.10', 'python3.11', 'python3.12'):
                    args = (process / 'cmdline').read_bytes().replace(b'\0', b' ').decode(errors='replace')
                    if re.search(r'(?:/| )[^ /]*(?:deploy|promot|retention-run)[^ /]*\.(?:sh|py)', args):
                        active.append('DEPLOYMENT_OR_MAINTENANCE_PROCESS')
            except FileNotFoundError:
                continue
    except (OSError, RuntimeError, subprocess.SubprocessError):
        operations_complete = False
    journal = {'complete': False}
    try:
        # This command reads existing journal metadata only.
        journal = {'complete': True, 'display': subprocess.check_output(
            ['journalctl', '--disk-usage'], text=True, timeout=5).strip()}
    except (OSError, subprocess.SubprocessError):
        pass
    policy_files = []
    for filename in POLICY_FILES:
        row = {'path': filename}
        try:
            meta = identity(filename)
            row['identity'] = meta
            if not meta['regular'] or meta['logical_bytes'] > 1024 * 1024:
                raise RuntimeError('policy file type or size unverified')
            # Only fixed operational policy/code files, never .env or business data.
            raw = Path(filename).read_bytes()
            row['sha256'] = hashlib.sha256(raw).hexdigest()
            if filename.endswith('/restaurant-disk-retention') and '/cron.d/' in filename:
                lines = [line for line in raw.decode().splitlines() if line.strip() and not line.lstrip().startswith('#')]
                row['enabled_domains'] = {domain: any(marker in line for line in lines)
                    for domain, marker in [('cache', 'server-buildkit-retention'),
                                           ('journal', 'run-journal-retention'),
                                           ('releases', 'staging-retention-run')]}
            row['verified_metadata'] = True
        except (OSError, RuntimeError, UnicodeDecodeError) as error:
            row['verified_metadata'] = False
            row['error'] = type(error).__name__
        policy_files.append(row)
    return {'schema_version': 1, 'observed_utc': observed,
            'source': 'local-unix-docker-api-and-fixed-path-stat',
            'docker_socket': 'unix:///var/run/docker.sock', 'docker_api_version': version['ApiVersion'],
            'docker_data_root': info['DockerRootDir'],
            'containers_complete': True, 'containers': containers,
            'images': [{key: value for key, value in image.items()
                        if key in ('Id', 'RepoTags', 'RepoDigests', 'Size', 'SharedSize', 'Containers', 'Created', 'ParentId')}
                       for image in disk.get('Images', [])],
            'cache': [{key: value for key, value in record.items()
                       if key in ('ID', 'InUse', 'Shared', 'Size', 'Type', 'LastUsedAt', 'CreatedAt', 'UsageCount', 'Parents')}
                      for record in disk.get('BuildCache', [])],
            'volumes': [{key: value for key, value in volume.items()
                         if key in ('Name', 'Driver', 'Mountpoint', 'UsageData')}
                        for volume in disk.get('Volumes', [])],
            'releases': releases, 'release_scan_complete': not any(e['scope'] == 'releases' for e in errors),
            'logs': logs, 'journal': journal, 'metadata_entry_count': entry_count,
            'policy_files': policy_files,
            'operation_gate': {'complete': operations_complete, 'active': sorted(set(active))},
            'errors': errors,
            'accounting_note': 'Docker logical/shared accounting and allocated file bytes overlap. Do not add them.'}


if __name__ == '__main__':
    print(json.dumps(collect(), sort_keys=True))
