#!/usr/bin/env python3
"""Financial-column-only Sandbox backfill. Runtime/API credentials and raw responses stay in memory."""
import argparse
import collections
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
DB = 'restaurant-pos-staging-db-1'
TEST = 'bd993244-5589-4b19-8f0d-dc2ba73d4273'
CLIENT = 't86ofdunSsVjL-0AvK6MA6LCTyF2eYLf'
TOOLS = Path('/srv/restaurant-pos/staging/state/final-channels-financial-tools-20261003')
BACKUP = Path('/srv/restaurant-pos/staging/state/final-channels-financial-20261003.dump')
HISTORY = ('orders', 'order_items', 'order_item_options', 'order_update_batches', 'kitchen_tasks',
           'frontdesk_beverage_items', 'production_tasks', 'inventory_transactions',
           'order_dispatch_outbox', 'print_jobs', 'uber_eats_events')
os.umask(0o077)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def db(sql, write=False):
    options = '-c statement_timeout=15000 -c lock_timeout=3000'
    if not write:
        options += ' -c default_transaction_read_only=on'
    result = subprocess.run(['docker', 'exec', '-i', DB, 'sh', '-c',
        'PGOPTIONS="' + options + '" psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=sql, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('STAGING_SQL_FAILED')
    return result.stdout.strip()


def runtime(expected_sha):
    row = json.loads(subprocess.check_output(['docker', 'inspect', 'restaurant-pos-staging-backend-1']))[0]
    env = dict(entry.split('=', 1) for entry in row['Config']['Env'])
    assert env.get('APP_ENVIRONMENT') == 'staging' and env.get('UBER_EATS_ENVIRONMENT') == 'sandbox'
    assert env.get('UBER_EATS_CLIENT_ID') == CLIENT and env.get('UBER_EATS_CLIENT_SECRET')
    assert row['Config']['Labels']['org.opencontainers.image.revision'] == expected_sha
    assert row['State']['Running'] and row['Image'].startswith('sha256:')
    return row['Image'], env


def production_fingerprints():
    result = []
    for name in ('cloud-backend-1', 'cloud-nginx-1', 'cloud-db-1'):
        row = json.loads(subprocess.check_output(['docker', 'inspect', name]))[0]
        result.append((row['Id'], row['Image'], row['State']['StartedAt'], row['State']['Running']))
    return result


def tool_files_ready(artifact_jar, expected_sha):
    # Before V35 deploy, dry-run may check Testing availability without a converter.
    if artifact_jar is None or not TOOLS.exists():
        return False
    assert TOOLS.resolve() == TOOLS and TOOLS.is_dir() and TOOLS.stat().st_mode & 0o077 == 0
    for path in TOOLS.rglob('*'):
        assert not path.is_symlink(), 'TOOL_SYMLINK_FORBIDDEN'
    jar = Path(artifact_jar)
    assert jar.is_file() and not jar.is_symlink(), 'ARTIFACT_JAR_REQUIRED'
    jar_hash = hashlib.sha256(jar.read_bytes()).hexdigest()
    deployed_hash = subprocess.check_output(['docker', 'exec', 'restaurant-pos-staging-backend-1',
        'sha256sum', '/app/app.jar'], text=True).split()[0]
    assert re.fullmatch('[0-9a-f]{64}', deployed_hash) and deployed_hash == jar_hash, 'RUNTIME_JAR_MISMATCH'
    expected_files = {}
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            if re.fullmatch(r'BOOT-INF/classes/com/restaurant/system/integration/ubereats/dto/UberFinancialSnapshot(?:\$[A-Za-z0-9]+)?\.class', entry):
                relative = 'classes/' + entry.removeprefix('BOOT-INF/classes/')
            elif re.fullmatch(r'BOOT-INF/lib/jackson-(annotations|core|databind)-[0-9.]+\.jar', entry):
                relative = 'lib/' + entry.removeprefix('BOOT-INF/lib/')
            else:
                continue
            expected_files[relative] = hashlib.sha256(archive.read(entry)).hexdigest()
    assert 'classes/com/restaurant/system/integration/ubereats/dto/UberFinancialSnapshot.class' in expected_files
    assert all(sum(name.startswith('lib/jackson-' + part + '-') for name in expected_files) == 1
        for part in ('annotations', 'core', 'databind'))
    manifest_name = 'snapshot-export-manifest.json'
    manifest = json.loads((TOOLS / manifest_name).read_text())
    assert manifest['runtime_sha'] == expected_sha
    assert manifest['source_sha256'] == hashlib.sha256((HERE / 'ops/SnapshotExport.java').read_bytes()).hexdigest(), 'CLI_SOURCE_DRIFT'
    bridges = manifest['classes']
    assert isinstance(bridges, dict) and 'classes/SnapshotExport.class' in bridges
    assert all(re.fullmatch(r'classes/SnapshotExport(?:\$[A-Za-z0-9]+)?\.class', path)
        and re.fullmatch('[0-9a-f]{64}', digest) for path, digest in bridges.items())
    expected_files.update(bridges)
    actual_names = {str(path.relative_to(TOOLS)) for path in TOOLS.rglob('*') if path.is_file()}
    assert actual_names == set(expected_files) | {manifest_name}, 'UNEXPECTED_TOOL_FILE'
    for path, digest in expected_files.items():
        assert hashlib.sha256((TOOLS / path).read_bytes()).hexdigest() == digest, 'TOOL_ARTIFACT_DRIFT'
    return True


def convert(records, image):
    result = subprocess.run(['docker', 'run', '--rm', '-i', '--pull', 'never', '--network', 'none', '--read-only',
        '--memory', '128m', '--cpus', '0.5', '--pids-limit', '64', '--cap-drop', 'ALL',
        '--security-opt', 'no-new-privileges', '--user', str(os.getuid()) + ':' + str(os.getgid()),
        '-v', str(TOOLS) + ':/work:ro', '--entrypoint', 'java', image,
        '-XX:-UsePerfData', '-Xmx64m', '-cp', '/work/classes:/work/lib/*', 'SnapshotExport'],
        input=json.dumps(records), text=True, capture_output=True, timeout=120)
    if result.returncode:
        raise RuntimeError('FINANCIAL_CONVERTER_FAILED')
    converted = json.loads(result.stdout)
    assert isinstance(converted, list) and len(converted) == len(records)
    assert {row['row_id'] for row in converted} == {row['row_id'] for row in records}
    return converted


def q(value):
    return "'" + str(value).replace("'", "''") + "'"


def history_query():
    return ' UNION ALL '.join("SELECT " + q(table)
        + " AS name,count(*) AS rows,md5(coalesce(string_agg(md5(to_jsonb(t)::text),'' ORDER BY t.id),'')) AS fingerprint FROM "
        + table + ' t' for table in HISTORY)


def apply_sql(candidates):
    values = ',\n'.join('(' + str(row['id']) + ',' + q(row['uber_order_id']) + ','
        + q(row['fingerprint']) + ',' + q(json.dumps(row['snapshot'], separators=(',', ':'))) + ')' for row in candidates)
    return """BEGIN ISOLATION LEVEL SERIALIZABLE;
SELECT id FROM stores WHERE id=1 FOR UPDATE;
SELECT id FROM uber_eats_store_mappings WHERE id=1 FOR UPDATE;
LOCK TABLE uber_eats_orders IN SHARE ROW EXCLUSIVE MODE;
""" + 'LOCK TABLE ' + ','.join(HISTORY) + ' IN SHARE MODE;\n' + """
CREATE TEMP TABLE expected_financial(id bigint PRIMARY KEY,uber_id text,old_hash text,snapshot text) ON COMMIT DROP;
INSERT INTO expected_financial VALUES """ + values + """;
CREATE TEMP TABLE uber_before ON COMMIT DROP AS SELECT id,to_jsonb(o) AS row FROM uber_eats_orders o;
CREATE TEMP TABLE history_before ON COMMIT DROP AS """ + history_query() + """;
DO $$ BEGIN
 IF current_database()<>'restaurant_pos_staging' THEN RAISE EXCEPTION 'WRONG_DATABASE'; END IF;
 IF NOT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='35' AND success)
 THEN RAISE EXCEPTION 'V35_REQUIRED'; END IF;
 IF (SELECT count(*) FROM uber_eats_store_mappings b JOIN stores s ON s.id=b.store_id WHERE b.id=1
 AND b.store_id=1 AND b.organization_id=1 AND b.environment='sandbox' AND b.processing_mode='KITCHEN_MIRROR'
 AND b.uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273' AND b.enabled
 AND s.code='STG005_SRC_20260809_R01' AND s.organization_id=1)<>1 THEN RAISE EXCEPTION 'BINDING_DRIFT'; END IF;
 IF (SELECT count(*) FROM expected_financial e JOIN uber_eats_orders o ON o.id=e.id
 WHERE o.store_id=1 AND o.store_mapping_id=1 AND o.environment='sandbox'
 AND o.uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273' AND o.uber_order_id=e.uber_id
 AND (o.accepted_observed_at IS NOT NULL OR o.accepted_at IS NOT NULL OR o.local_order_id IS NOT NULL)
 AND o.item_financial_snapshot_json IS NULL AND md5((to_jsonb(o)-'item_financial_snapshot_json')::text)=e.old_hash
 AND e.snapshot::jsonb->>'order_id'=o.uber_order_id AND e.snapshot::jsonb->>'store_id'=o.uber_store_id
 AND (e.snapshot::jsonb->>'version')::int=1)<>(SELECT count(*) FROM expected_financial)
 THEN RAISE EXCEPTION 'FROZEN_ORDER_OR_FINANCIAL_DRIFT'; END IF;
END $$;
-- Intentionally no updated_at, state, scalar money, raw snapshot or replay field write.
UPDATE uber_eats_orders o SET item_financial_snapshot_json=e.snapshot FROM expected_financial e
 WHERE o.id=e.id AND o.item_financial_snapshot_json IS NULL;
CREATE TEMP TABLE history_after ON COMMIT DROP AS """ + history_query() + """;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM expected_financial e JOIN uber_eats_orders o ON o.id=e.id
 WHERE o.item_financial_snapshot_json IS DISTINCT FROM e.snapshot)
 THEN RAISE EXCEPTION 'FINANCIAL_READBACK_FAILED'; END IF;
 IF EXISTS(SELECT 1 FROM uber_before b JOIN uber_eats_orders o ON o.id=b.id
 WHERE b.row-'item_financial_snapshot_json' IS DISTINCT FROM to_jsonb(o)-'item_financial_snapshot_json')
 OR (SELECT count(*) FROM uber_before)<>(SELECT count(*) FROM uber_eats_orders)
 THEN RAISE EXCEPTION 'OLD_UBER_FIELDS_CHANGED'; END IF;
 IF EXISTS(SELECT 1 FROM uber_before b JOIN uber_eats_orders o ON o.id=b.id
 WHERE b.id NOT IN (SELECT id FROM expected_financial) AND b.row IS DISTINCT FROM to_jsonb(o))
 THEN RAISE EXCEPTION 'UNAPPROVED_UBER_ROW_CHANGED'; END IF;
 IF EXISTS(SELECT * FROM history_before EXCEPT SELECT * FROM history_after)
 THEN RAISE EXCEPTION 'ORDER_OR_PRINT_HISTORY_CHANGED'; END IF;
END $$;
INSERT INTO audit_logs(store_id,actor_name_snapshot,actor_role_snapshot,action,entity_type,entity_id,summary,metadata_json,created_at)
 VALUES(1,'Owner-authorized financial backfill','SYSTEM','UBER_FINANCIAL_SNAPSHOT_BACKFILLED','UBER_MAPPING',1,
 'Populate only new null item financial column from identity-matched Sandbox responses; no replay',
 json_build_object('rows',(SELECT count(*) FROM expected_financial),'old_fields_unchanged',true,'replay',false)::text,localtimestamp);
COMMIT;
"""


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--dry-run', action='store_true')
    mode.add_argument('--apply', action='store_true')
    parser.add_argument('--expected-runtime-sha', required=True)
    parser.add_argument('--artifact-jar', help='Exact tested backend.jar; must hash-match running /app/app.jar')
    args = parser.parse_args()
    assert re.fullmatch('[0-9a-f]{40}', args.expected_runtime_sha)
    image, env = runtime(args.expected_runtime_sha)
    before_production = production_fingerprints()
    ready = tool_files_ready(args.artifact_jar, args.expected_runtime_sha)
    if args.apply:
        assert ready, 'FINANCIAL_TOOL_REQUIRED'
        assert db("select count(*) from flyway_schema_history where version='35' and success") == '1'
    assert db("select current_database()") == 'restaurant_pos_staging'
    assert db("""select count(*) from uber_eats_store_mappings b join stores s on s.id=b.store_id
        where b.id=1 and b.store_id=1 and b.organization_id=1 and b.environment='sandbox'
        and b.uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273' and b.processing_mode='KITCHEN_MIRROR'
        and b.enabled and s.organization_id=1 and s.code='STG005_SRC_20260809_R01'""") == '1'
    rows = json.loads(db("""select coalesce(json_agg(t),'[]'::json) from (
        select o.id,o.uber_order_id,o.raw_order_snapshot_json,o.raw_financial_snapshot_json,
        md5((to_jsonb(o)-'item_financial_snapshot_json')::text) AS fingerprint
        from uber_eats_orders o join uber_eats_store_mappings b on b.id=o.store_mapping_id
        join stores s on s.id=b.store_id where o.store_mapping_id=1 AND o.store_id=1 AND o.environment='sandbox'
        AND o.uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273'
        AND b.store_id=1 AND b.organization_id=1 AND b.environment='sandbox' AND b.enabled
        AND b.uber_store_id=o.uber_store_id AND b.processing_mode='KITCHEN_MIRROR'
        AND s.organization_id=1 AND s.code='STG005_SRC_20260809_R01'
        AND (o.accepted_observed_at IS NOT NULL OR o.accepted_at IS NOT NULL OR o.local_order_id IS NOT NULL)
        AND to_jsonb(o)->>'item_financial_snapshot_json' IS NULL order by o.id) t"""))
    assert len(rows) <= 1000
    http = urllib.request.build_opener(NoRedirect)
    body = urllib.parse.urlencode({'client_id': CLIENT, 'client_secret': env['UBER_EATS_CLIENT_SECRET'],
        'grant_type': 'client_credentials', 'scope': 'eats.order eats.store.orders.read'}).encode()
    with http.open(urllib.request.Request('https://sandbox-login.uber.com/oauth/v2/token', data=body,
            headers={'Content-Type': 'application/x-www-form-urlencoded'}), timeout=30) as response:
        token = json.load(response)['access_token']
    blocked = collections.Counter()
    records = []
    by_id = {row['id']: row for row in rows}
    for row in rows:
        try:
            assert re.fullmatch('[0-9a-f-]{36}', row['uber_order_id'])
            frozen = json.loads(row['raw_order_snapshot_json'])
            frozen_charges = json.loads(row['raw_financial_snapshot_json'])
            with http.open(urllib.request.Request('https://test-api.uber.com/v2/eats/order/' + row['uber_order_id'],
                    headers={'Authorization': 'Bearer ' + token}), timeout=30) as response:
                raw = json.load(response)
            assert raw.get('id') == row['uber_order_id'] and raw.get('store', {}).get('id') == TEST
            records.append({'row_id': row['id'], 'order_id': row['uber_order_id'], 'store_id': TEST,
                'frozen': frozen, 'frozen_charges': frozen_charges, 'response': raw})
        except urllib.error.HTTPError as error:
            blocked['TESTING_HTTP_' + str(error.code)] += 1
        except Exception:
            blocked['RESPONSE_OR_FROZEN_SNAPSHOT_UNAVAILABLE'] += 1
    candidates = []
    complete = 0
    if ready:
        for result in convert(records, image):
            if result['status'] != 'MATCHED':
                blocked[result['reason']] += 1
                continue
            snapshot = result['snapshot']
            row = by_id[result['row_id']]
            assert snapshot['version'] == 1 and snapshot['order_id'] == row['uber_order_id'] and snapshot['store_id'] == TEST
            row['snapshot'] = snapshot
            candidates.append(row)
            if snapshot['revenue_minor'] is not None and snapshot['blocked_reason'] is None:
                complete += 1
            else:
                assert snapshot['revenue_minor'] is None and snapshot['blocked_reason']
                blocked[snapshot['blocked_reason']] += 1
    else:
        blocked['FINANCIAL_CONVERTER_NOT_READY'] += len(records)
    summary = {'mode': 'APPLY' if args.apply else 'DRY_RUN', 'empty_column_rows': len(rows),
        'testing_responses': len(records), 'identity_matched_snapshots': len(candidates),
        'complete_revenue_snapshots': complete, 'blocked_reasons': dict(blocked)}
    if args.apply and candidates:
        assert not BACKUP.exists(), 'PRIOR_BACKFILL_REQUIRES_REVIEW'
        with BACKUP.open('xb') as output:
            subprocess.run(['docker', 'exec', DB, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], stdout=output, check=True)
        assert BACKUP.stat().st_size > 0
        with BACKUP.open('rb') as source:
            subprocess.run(['docker', 'exec', '-i', DB, 'pg_restore', '--list'], stdin=source, stdout=subprocess.DEVNULL, check=True)
        runtime(args.expected_runtime_sha)
        db(apply_sql(candidates), True)
        ids = ','.join(str(row['id']) for row in candidates)
        assert db('select count(*) from uber_eats_orders where id in (' + ids + ') and item_financial_snapshot_json is not null') == str(len(candidates))
        summary['written_rows'] = len(candidates)
        summary['old_fields_order_print_hashes_unchanged'] = True
    else:
        summary['written_rows'] = 0
    assert production_fingerprints() == before_production, 'PRODUCTION_RUNTIME_CHANGED'
    summary['production_changed'] = False
    summary['at'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'status': 'FAIL', 'error_type': type(error).__name__, 'private_values_emitted': False}))
        raise SystemExit(1)
