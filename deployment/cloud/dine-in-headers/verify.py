#!/usr/bin/env python3
"""Table-delete UI and unchanged split-label/render acceptance of this exact deployed JAR; never dispatches a job."""
import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import sys
import zipfile
import urllib.request

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('header_release', HERE / 'deploy.py')
d = importlib.util.module_from_spec(spec)
spec.loader.exec_module(d)


def validate_table_response(rows, expected, store):
    fields = ('id', 'store_id', 'table_code', 'table_name', 'table_config', 'split_label_mode', 'is_active')
    assert isinstance(rows, list) and len(rows) == len(expected)
    assert all(row['store_id'] == store and row.get('is_active') is not False for row in rows)
    projected = [{field: row.get(field) for field in fields} for row in rows]
    assert sorted(projected, key=lambda row: row['id']) == expected, 'Table API must exactly match active database rows'


def verify_table_lists(environment, db, env, base):
    # Reuse the reviewed short-lived existing numeric identity. GET only, no login
    # password, credential write, table mutation, order or print operation.
    def load(name, path):
        spec = importlib.util.spec_from_file_location(name, path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    smoke = load('table_smoke', HERE.parent / 'production-v26-smoke.py')
    http = load('table_http', HERE.parent / 'verify-uber-receive-only-20261022.py')
    smoke.run_psql = d.read_sql
    user, role, role_code, _, org = smoke.numeric_identity(db)
    targets = [1, 2, 3] if environment == 'production' else [18]
    if environment == 'production': assert org == 1
    token = smoke.mint_token(env['JWT_SECRET'], user, role, targets[0], org, role_code)
    def get(path):
        assert path == '/auth/me' or any(path == f'{prefix}?store_id={store}' for store in targets
            for prefix in ('/frontdesk/dining-tables', '/admin/platform/dining-tables'))
        status, raw = http.request(base + '/api/v1' + path, headers={'Authorization': 'Bearer ' + token})
        assert status == 200, 'Table read-only acceptance HTTP status ' + str(status)
        response = json.loads(raw)
        assert response.get('success') is True
        return response['data']
    assert get('/auth/me') is not None
    report = []
    for store in targets:
        expected = json.loads(d.read_sql(db, f"SELECT coalesce(json_agg(t ORDER BY id),'[]'::json) FROM (SELECT id,store_id,table_code,table_name,table_config,split_label_mode,is_active FROM dining_tables WHERE store_id={store} AND is_active IS DISTINCT FROM false) t"))
        inactive = int(d.read_sql(db, f'SELECT count(*) FROM dining_tables WHERE store_id={store} AND is_active=false'))
        for prefix in ('/frontdesk/dining-tables', '/admin/platform/dining-tables'):
            validate_table_response(get(f'{prefix}?store_id={store}'), expected, store)
        report.append({'store_id': store, 'active_tables': len(expected), 'excluded_inactive_tables': inactive, 'both_lists': 'PASS'})
    return report


def main(environment, sha, class_sha256):
    assert environment in ('staging', 'production') and re.fullmatch('[0-9a-f]{40}', sha)
    assert re.fullmatch('[0-9a-f]{64}', class_sha256)
    script_release = HERE.parents[2]
    script_sha = d.i.run(['git', '-C', str(script_release), 'rev-parse', 'HEAD']).decode().strip()
    assert HERE == d.BASE / 'staging/releases' / script_sha / 'deployment/cloud/dine-in-headers'
    assert not d.i.run(['git', '-C', str(script_release), 'status', '--porcelain']).strip()
    assert not d.i.run(['git', '-C', str(script_release), 'diff', sha, script_sha, '--', 'backend', 'frontend']).strip()
    root = d.evidence_root(environment, sha)
    result = json.loads(d.ops.private_file(root / 'result.json').read_text())
    assert result['source_sha'] == sha and result['health'] == 'PASS'
    name = ('cloud' if environment == 'production' else 'restaurant-pos-staging') + '-backend-1'
    runtime = d.i.inspect(name)
    image = runtime['Image']
    assert image == result['image'] and d.prior.revision(runtime) == sha
    frontend_name = name.replace('-backend-1', '-nginx-1')
    frontend = d.i.inspect(frontend_name)
    assert frontend['Image'] == result['images']['nginx'] and d.prior.revision(frontend) == sha
    # Verify the deployed preview module is the same public static asset, without logging in or making jobs.
    asset_paths = d.i.run(['docker', 'exec', frontend_name, 'sh', '-c',
        'grep -l "data-receipt-top-feed" /usr/share/nginx/html/assets/*.js']).decode().splitlines()
    assert asset_paths
    asset = Path(asset_paths[0])
    assert asset.parent == Path('/usr/share/nginx/html/assets') and re.fullmatch(r'[A-Za-z0-9_.-]+\.js', asset.name)
    asset_bytes = d.i.run(['docker', 'exec', frontend_name, 'cat', str(asset)])
    assert b'[[TOP_FEED_16MM]]' in asset_bytes and b'16mm' in asset_bytes
    base = 'https://' + ('pos' if environment == 'production' else 'staging-pos') + '.lanzhounoodlesmtl.com'
    with urllib.request.urlopen(base + '/assets/' + asset.name, timeout=8) as response:
        assert response.status == 200 and response.read() == asset_bytes
    label_asset_paths = d.i.run(['docker', 'exec', frontend_name, 'sh', '-c',
        'grep -l "Split Label" /usr/share/nginx/html/assets/AdminDiningTables-*.js']).decode().splitlines()
    assert len(label_asset_paths) == 1
    label_asset = Path(label_asset_paths[0])
    assert label_asset.parent == Path('/usr/share/nginx/html/assets') and re.fullmatch(r'[A-Za-z0-9_.-]+\.js', label_asset.name)
    label_bytes = d.i.run(['docker', 'exec', frontend_name, 'cat', str(label_asset)])
    assert b'INSIDE_OUTSIDE' in label_bytes and b'LEFT_RIGHT' in label_bytes
    assert b'Delete Table' in label_bytes and b'checkbox' not in label_bytes
    with urllib.request.urlopen(base + '/assets/' + label_asset.name, timeout=8) as response:
        assert response.status == 200 and response.read() == label_bytes
    harness = d.ops.private_file(root / 'RenderHeaderAcceptance.class')
    assert hashlib.sha256(harness.read_bytes()).hexdigest() == class_sha256
    proof = root / 'render-proof'
    assert not proof.is_symlink()
    proof.mkdir(mode=0o700, exist_ok=True)
    assert proof.stat().st_uid == os.getuid() and stat.S_IMODE(proof.stat().st_mode) == 0o700
    (proof / harness.name).write_bytes(harness.read_bytes())
    d.i.run(['docker', 'cp', name + ':/app/app.jar', str(proof / 'app.jar')])
    with zipfile.ZipFile(proof / 'app.jar') as jar:
        for entry in jar.infolist():
            if entry.is_dir(): continue
            if entry.filename.startswith(('BOOT-INF/classes/', 'BOOT-INF/lib/')):
                relative = Path(entry.filename.removeprefix('BOOT-INF/'))
                assert not relative.is_absolute() and '..' not in relative.parts
                target = proof / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(jar.read(entry))
    observed = json.loads(d.i.run(['docker', 'run', '--rm', '--network', 'none', '--read-only',
        '--memory', '256m', '--cpus', '.5', '--pids-limit', '64', '--cap-drop', 'ALL',
        '--user', str(os.getuid()) + ':' + str(os.getgid()),
        '--security-opt', 'no-new-privileges', '--entrypoint', 'java',
        '-v', str(proof) + ':/proof:ro', image,
        '-cp', '/proof/classes:/proof/lib/*:/proof', 'RenderHeaderAcceptance']))
    assert observed['status'] == 'PASS' and observed['header_cases'] == 24 and observed['fits_42_columns'] is True
    assert observed['additional_top_feed_mm'] == 16 and observed['top_feed_once'] is True
    assert observed['pad_tcp_payload_identical'] is True and observed['takeout_cases'] == 6
    assert observed['split_label_cases'] == 48
    assert observed['database_written'] is False and observed['print_dispatched'] is False
    assert d.i.inspect(name)['Id'] == runtime['Id']
    assert d.i.inspect(frontend_name)['Id'] == frontend['Id']
    db = name.replace('-backend-1', '-db-1')
    baseline = json.loads(d.ops.private_file(root / 'baseline.private.json').read_text())
    d.p.DB = db
    table_lists = verify_table_lists(environment, db, d.ops.env_of(runtime), base)
    d.validate_ledger(baseline['ledger'], d.i.ledger(db))
    assert d.queue_guard(db, '39') == baseline['configuration']
    assert d.order_contexts(db, baseline['history']['cutoffs'][0]) == baseline['order_contexts']
    modes = d.table_modes(db)
    assert d.p.history_fingerprints(baseline['history']['cutoffs']) == baseline['history']
    for url in ('https://pos.lanzhounoodlesmtl.com', 'https://staging-pos.lanzhounoodlesmtl.com'): d.i.health(url)
    accepted = dict(observed, environment=environment, source_sha=sha, image=image, health='PASS',
        images=result['images'], flyway='39', table_modes=modes, mock_split_labels='PASS',
        split_label_public_asset_sha256=hashlib.sha256(label_bytes).hexdigest(), preview_asset_sha256=hashlib.sha256(asset_bytes).hexdigest(), preview_public_asset='PASS',
        accepted=True, table_soft_delete_ui='PASS', table_lists=table_lists,
        password_login='NOT_TESTED_EXISTING_NUMERIC_IDENTITY_GET_ONLY',
        migration='NONE_V39_UNCHANGED', script_sha=script_sha, app_jar_sha256=hashlib.sha256((proof / 'app.jar').read_bytes()).hexdigest(),
        proof_class_sha256=class_sha256, observed_at=datetime.datetime.now(datetime.timezone.utc).isoformat())
    d.ops.write_private(root / 'render-acceptance.json', accepted)
    d.ops.write_private(root / 'acceptance.json', accepted)
    print(json.dumps(accepted))


if __name__ == '__main__':
    with d.ops.operation_locks(): main(*sys.argv[1:])
