#!/usr/bin/env python3
"""First import on the explicitly identified new Allo host; never touches the old host."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tarfile
import time

ROOT = Path('/srv/allo-nouilles')
INCOMING = ROOT / 'incoming'
SOURCE_SHA = '2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9'
SOURCE_IMAGES = {
    'backend': 'sha256:9f476edd07150b2239c6ab55115426251f0a74136cba3e304140ac472bb32d76',
    'frontend': 'sha256:0415d9bf930a07d79dcb179c6d06563707e9b45654944e844b4abd7627141fcc',
    'postgres': 'sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28',
}


def run(args, **kwargs):
    return subprocess.run([str(x) for x in args], check=True, capture_output=True, **kwargs)


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    return h.hexdigest()


def main():
    assert os.geteuid() == 0, 'Run as root on the new host only'
    assert socket.gethostname() == 'VM-0-6-ubuntu', 'Wrong host; Production is forbidden'
    assert b'10.16.0.6/' in run(['ip', '-4', 'address', 'show']).stdout
    marker = ROOT / 'state/import-complete.json'
    assert not marker.exists(), 'Initial import already finished; use the documented update/recovery procedure'
    for raw in run(['docker', 'ps', '-aq']).stdout.decode().split():
        row = json.loads(run(['docker', 'inspect', raw]).stdout)[0]
        assert row['Config']['Labels'].get('com.docker.compose.project') == 'allo-nouilles', 'Unexpected workload'

    tenant = json.loads((INCOMING / 'tenant-migration-manifest.json').read_text())
    assert digest(INCOMING / 'allo-only-migration.dump') == tenant['tenant_dump_sha256']
    assert digest(INCOMING / 'source-images.tar.gz') == 'fefea3da85aa72b4fae4d4aceb33c97b2e0455e4787a593d225268f360b7887d'
    print('New host identity and source archive checksums verified', flush=True)

    with tarfile.open(INCOMING / 'release-payload.tgz') as archive:
        staging = INCOMING / 'unpacked'
        staging.mkdir(exist_ok=True)
        for member in archive.getmembers():
            assert member.name.startswith('release/') or member.name == 'release'
            assert '..' not in Path(member.name).parts and not member.issym() and not member.islnk()
        archive.extractall(staging)
    release = staging / 'release'
    checks = json.loads((release / 'payload-sha256.json').read_text())
    for name, expected in checks.items():
        assert digest(release / name) == expected, name
    compatibility = json.loads((release / 'compatibility-manifest.json').read_text())
    assert compatibility['source_sha'] == SOURCE_SHA
    assert digest(release / 'compatibility.patch') == compatibility['patch_sha256']
    version = compatibility['release_version']
    destination = ROOT / 'releases' / version
    if destination.exists():
        for name, expected in checks.items():
            assert digest(destination / name) == expected, 'Existing release differs'
    else:
        shutil.move(str(release), destination)
    release = destination

    run(['docker', 'image', 'load', '-i', INCOMING / 'source-images.tar.gz'])
    for service, identity in SOURCE_IMAGES.items():
        row = json.loads(run(['docker', 'image', 'inspect', identity]).stdout)[0]
        assert row['Id'] == identity
        if service != 'postgres':
            assert row['Config']['Labels']['org.opencontainers.image.revision'] == SOURCE_SHA
        run(['docker', 'tag', identity, 'allo-nouilles-source-' + service + ':source'])
    run(['docker', 'tag', SOURCE_IMAGES['postgres'], 'allo-nouilles-postgres:source'])
    print('Exact source images loaded and checked', flush=True)
    for service in ['backend', 'frontend']:
        run(['docker', 'build', '--pull=false', '--network=none', '-f', release / ('Dockerfile.' + service),
             '--build-arg', 'BASE_IMAGE=allo-nouilles-source-' + service + ':source',
             '--build-arg', 'RELEASE_VERSION=' + version, '--build-arg', 'SOURCE_SHA=' + SOURCE_SHA,
             '--build-arg', 'PATCH_SHA256=' + compatibility['patch_sha256'],
             '-t', 'allo-nouilles-' + service + ':' + version, release])
        print('Derived ' + service + ' image built', flush=True)

    private = ROOT / 'private'
    private.mkdir(exist_ok=True)
    private.chmod(0o700)
    runtime = private / 'runtime.env'
    incoming_runtime = INCOMING / 'target-runtime.env'
    if runtime.exists():
        assert runtime.read_bytes() == incoming_runtime.read_bytes(), 'Do not overwrite existing secrets'
    else:
        shutil.copyfile(incoming_runtime, runtime)
    runtime.chmod(0o600)
    menu = private / 'menu.source.json'
    shutil.copyfile(release / 'menu.source.json', menu)
    os.chown(menu, 0, 10001)
    menu.chmod(0o640)
    tls = private / 'tls'
    tls.mkdir(exist_ok=True)
    os.chown(tls, 0, 101)
    tls.chmod(0o750)
    shutil.copyfile(release / 'compose.yml', ROOT / 'compose.yml')
    shutil.copyfile(release / 'nginx.http.conf', ROOT / 'config/nginx.conf')
    (ROOT / 'config/nginx.conf').chmod(0o644)
    shutil.copyfile(release / 'backup-db.sh', ROOT / 'bin/backup-db.sh')
    (ROOT / 'bin/backup-db.sh').chmod(0o750)
    compose = ['docker', 'compose', '--project-name', 'allo-nouilles', '--env-file', runtime, '-f', ROOT / 'compose.yml']
    run(compose + ['config', '--quiet'])
    run(compose + ['up', '-d', 'db'])
    db_id = run(compose + ['ps', '-q', 'db']).stdout.decode().strip()
    assert db_id
    for _ in range(60):
        state = json.loads(run(['docker', 'inspect', db_id]).stdout)[0]['State']
        if state.get('Health', {}).get('Status') == 'healthy':
            break
        time.sleep(2)
    else:
        raise RuntimeError('New PostgreSQL health check failed')

    def query(sql):
        return run(['docker', 'exec', '-i', db_id, 'psql', '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1',
                    '-U', 'allo_test', '-d', 'restaurant_allo_nouilles_test'], input=sql.encode()).stdout.decode().strip()

    assert query('select current_database();') == 'restaurant_allo_nouilles_test'
    assert int(query("select count(*) from pg_tables where schemaname='public';")) == 0, 'Target is not empty; refusing restore'
    with (INCOMING / 'allo-only-migration.dump').open('rb') as stream:
        run(['docker', 'exec', '-i', db_id, 'pg_restore', '--no-owner', '--no-acl', '--exit-on-error',
             '-U', 'allo_test', '-d', 'restaurant_allo_nouilles_test'], stdin=stream)

    def table_manifest():
        result = {}
        for table in tenant['tables']:
            sql = ('SELECT json_build_object(\'count\',count(*),\'row_hash\',md5(coalesce('
                   'string_agg(row_to_json(x)::text,E\'\\n\' ORDER BY row_to_json(x)::text),\'\'))) FROM "' + table + '" x;')
            result[table] = json.loads(query(sql))
        return result

    actual = table_manifest()
    differences = [table for table in actual if actual[table] != tenant['tables'][table]]
    assert not differences, 'Restored data differences: ' + ', '.join(differences)
    assert query("select count(*) from stores where id=1 and organization_id=1 and printing_mode='MOCK';") == '1'
    assert query('select count(*) from stores;') == '1'
    assert query('select count(*) from organizations;') == '1'
    assert query('select count(*) from store_devices;') == '0'
    print('All 85 restored table hashes match the tenant migration package', flush=True)
    (ROOT / 'backups/import-source-match.json').write_text(json.dumps(actual, indent=2))

    hashes = json.loads((INCOMING / 'new-password-hashes.json').read_text())
    assert set(hashes) == {'allo_owner', 'allo_manager'}
    for username, password_hash in hashes.items():
        assert password_hash.startswith('$2') and len(password_hash) == 60
        escaped = password_hash.replace("'", "''")
        assert query("select count(*) from user_credentials where login_identifier='" + username + "';") == '1'
        query("update user_credentials set password_hash='" + escaped + "', password_updated_at=current_timestamp, "
              "updated_at=current_timestamp where login_identifier='" + username + "';")
    rotated = table_manifest()
    assert [t for t in rotated if actual[t] != rotated[t]] == ['user_credentials']
    (ROOT / 'backups/import-after-credential-rotation.json').write_text(json.dumps(rotated, indent=2))
    backup = ROOT / 'backups/initial-import.dump'
    with backup.open('wb') as stream:
        subprocess.run(['docker', 'exec', db_id, 'pg_dump', '-U', 'allo_test', '-d',
                        'restaurant_allo_nouilles_test', '-Fc', '--no-owner', '--no-acl'], stdout=stream, check=True)
    backup.chmod(0o600)
    (ROOT / 'backups/initial-import.dump.sha256').write_text(digest(backup) + '  initial-import.dump\n')
    print('Two account passwords rotated; initial independent backup created', flush=True)
    run(compose + ['up', '-d'])
    status = {'at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'version': version,
              'source_sha': SOURCE_SHA, 'tenant_dump_sha256': tenant['tenant_dump_sha256'],
              'verified_tables': 85, 'all_business_hashes_match_before_password_rotation': True,
              'passwords_rotated': ['allo_owner', 'allo_manager'], 'initial_backup_sha256': digest(backup),
              'tls_state': 'ACME bootstrap only; HTTPS still to be configured'}
    marker.write_text(json.dumps(status, indent=2))
    print(json.dumps(status), flush=True)


if __name__ == '__main__':
    main()
