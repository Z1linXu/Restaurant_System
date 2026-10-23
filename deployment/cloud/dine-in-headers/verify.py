#!/usr/bin/env python3
"""Render-only acceptance of this exact deployed JAR; never dispatches a job."""
import datetime
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import zipfile

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('header_release', HERE / 'deploy.py')
d = importlib.util.module_from_spec(spec)
spec.loader.exec_module(d)


def main(environment, sha, class_sha256):
    assert environment in ('staging', 'production') and re.fullmatch('[0-9a-f]{40}', sha)
    assert re.fullmatch('[0-9a-f]{64}', class_sha256)
    assert HERE == d.BASE / 'staging/releases' / sha / 'deployment/cloud/dine-in-headers'
    root = d.BASE / environment / ('dine-in-headers-' + sha)
    result = json.loads(d.ops.private_file(root / 'result.json').read_text())
    assert result['source_sha'] == sha and result['health'] == 'PASS'
    name = ('cloud' if environment == 'production' else 'restaurant-pos-staging') + '-backend-1'
    runtime = d.i.inspect(name)
    image = runtime['Image']
    assert image == result['image'] and d.prior.revision(runtime) == sha
    harness = d.ops.private_file(root / 'RenderHeaderAcceptance.class')
    assert hashlib.sha256(harness.read_bytes()).hexdigest() == class_sha256
    proof = root / 'render-proof'
    proof.mkdir(mode=0o700)
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
        '--security-opt', 'no-new-privileges', '--entrypoint', 'java',
        '-v', str(proof) + ':/proof:ro', image,
        '-cp', '/proof/classes:/proof/lib/*:/proof', 'RenderHeaderAcceptance']))
    assert observed['status'] == 'PASS' and observed['header_cases'] == 24 and observed['fits_42_columns'] is True
    assert observed['database_written'] is False and observed['print_dispatched'] is False
    assert d.i.inspect(name)['Id'] == runtime['Id']
    db = name.replace('-backend-1', '-db-1')
    baseline = json.loads(d.ops.private_file(root / 'baseline.private.json').read_text())
    d.p.DB = db
    assert d.i.ledger(db) == baseline['ledger']
    assert d.queue_guard(db) == baseline['configuration']
    assert d.p.history_fingerprints(baseline['history']['cutoffs']) == baseline['history']
    for url in ('https://pos.lanzhounoodlesmtl.com', 'https://staging-pos.lanzhounoodlesmtl.com'): d.i.health(url)
    accepted = dict(observed, environment=environment, source_sha=sha, image=image, health='PASS',
        accepted=True, app_jar_sha256=hashlib.sha256((proof / 'app.jar').read_bytes()).hexdigest(),
        proof_class_sha256=class_sha256, observed_at=datetime.datetime.now(datetime.timezone.utc).isoformat())
    d.ops.write_private(root / 'render-acceptance.json', accepted)
    d.ops.write_private(root / 'acceptance.json', accepted)
    print(json.dumps(accepted))


if __name__ == '__main__':
    with d.ops.operation_locks(): main(*sys.argv[1:])
