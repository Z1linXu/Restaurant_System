#!/usr/bin/env python3
"""Owner-approved exact 41-root Staging configuration batch; never replay orders."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.request

BASE = 'https://staging-pos.lanzhounoodlesmtl.com'
DB = 'restaurant-pos-staging-db-1'
SHA = '500aacd991fddbc8f224174fc65db833e60d7848'
TEST = 'bd993244-5589-4b19-8f0d-dc2ba73d4273'
PLAN_FILE = Path(__file__).with_name('approved-roots.json')
BACKUP = Path('/srv/restaurant-pos/staging/state/uber-all-roots-20261002.dump')
os.umask(0o077)

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

def api(path, token=None, body=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(BASE + path, headers=headers,
        data=None if body is None else json.dumps(body).encode())
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)['data']

def quote(value):
    return "'" + str(value).replace("'", "''") + "'"

def main():
    assert sys.argv[1:] in ([], ['--apply'])
    plan = json.loads(PLAN_FILE.read_text())
    assert len(plan) == 41 and len({r['uberIdentifier'] for r in plan}) == 41
    assert all(r['humanConfirmed'] for r in plan)
    runtime = json.loads(subprocess.check_output(['docker', 'inspect', 'restaurant-pos-staging-backend-1']))[0]
    env = dict(x.split('=', 1) for x in runtime['Config']['Env'])
    assert env['APP_ENVIRONMENT'] == 'staging' and env['UBER_EATS_ENVIRONMENT'] == 'sandbox'
    assert runtime['Config']['Labels']['org.opencontainers.image.revision'] == SHA
    creds = json.loads(Path('/srv/restaurant-pos/staging/state/twin001-staff-credentials-v1.json').read_text())
    token = api('/api/v1/auth/login', body={'login_identifier': creds['owner_login_identifier'], 'password': creds['owner_login_password']})['access_token']
    prefix = '/api/v1/stores/1/integrations/uber-eats'
    connection = api(prefix + '/connection', token)
    binding = connection['store']
    assert (binding['id'], binding['storeId'], binding['organizationId'], binding['uberStoreId'], binding['environment'], binding['processingMode'], binding['enabled']) == (1, 1, 1, TEST, 'sandbox', 'KITCHEN_MIRROR', True)
    catalog = api(prefix + '/mapping-catalog', token)
    assert catalog['store_id'] == 1 and catalog['organization_id'] == 1
    items = {}
    def walk(node):
        if isinstance(node, dict):
            if node.get('sku') and node.get('id'):
                items.setdefault(node['sku'], set()).add(node['id'])
            for value in node.values(): walk(value)
        elif isinstance(node, list):
            for value in node: walk(value)
    walk(catalog)
    for row in plan:
        assert len(items.get(row['localSku'], set())) == 1
        row['localMenuItemId'] = next(iter(items[row['localSku']]))
        assert row['itemMappingMode'] == ('COMBO_ROOT' if 'Combo' in row['name'] else 'STANDARD')
        if row['itemMappingMode'] == 'COMBO_ROOT':
            choices = api(prefix + '/mapping-options/' + str(row['localMenuItemId']), token)
            assert len([c for c in choices if c['group'] == 'COMBO' and c['code'] == 'combo']) == 1
            assert {'COMBO_EGG', 'COMBO_SIDE'} <= {c['group'] for c in choices}
    values = ',\n'.join('(' + ','.join([quote(r['uberIdentifier']), quote(r['localSku']), str(r['localMenuItemId']), quote(r['itemMappingMode'])]) + ')' for r in plan)
    sql = """BEGIN ISOLATION LEVEL SERIALIZABLE;
LOCK TABLE uber_eats_menu_mappings IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE expected_root(uber_id text PRIMARY KEY,sku text,item_id bigint,mode text) ON COMMIT DROP;
INSERT INTO expected_root VALUES """ + values + """;
CREATE TEMP TABLE protected_before ON COMMIT DROP AS
 SELECT * FROM uber_eats_menu_mappings WHERE store_mapping_id<>1 OR kind<>'ITEM';
DO $$ BEGIN
 IF (SELECT count(*) FROM uber_eats_store_mappings b JOIN stores s ON s.id=b.store_id
 WHERE b.id=1 AND b.store_id=1 AND b.organization_id=1 AND b.environment='sandbox'
 AND b.uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273' AND b.processing_mode='KITCHEN_MIRROR'
 AND b.enabled AND s.organization_id=1 AND s.code='STG005_SRC_20260809_R01')<>1
 THEN RAISE EXCEPTION 'BINDING_CHANGED'; END IF;
 IF (SELECT count(*) FROM expected_root e JOIN menu_items i ON i.id=e.item_id AND i.sku=e.sku AND i.store_id=1)<>41
 THEN RAISE EXCEPTION 'LOCAL_IDENTITY_CHANGED'; END IF;
 IF (SELECT count(*) FROM uber_eats_menu_mappings WHERE store_mapping_id=1 AND kind='ITEM')<>5
 THEN RAISE EXCEPTION 'ROOT_BASELINE_CHANGED'; END IF;
 IF EXISTS(SELECT 1 FROM uber_eats_menu_mappings m LEFT JOIN expected_root e ON e.uber_id=m.uber_identifier
 WHERE m.store_mapping_id=1 AND m.kind='ITEM' AND (e.uber_id IS NULL OR m.local_menu_item_id<>e.item_id
 OR m.item_mapping_mode<>e.mode OR m.identifier_type<>'ID' OR m.uber_item_id<>'' OR m.mapping_action<>'MAP'))
 THEN RAISE EXCEPTION 'EXISTING_ROOT_DIFFERS'; END IF;
END $$;
INSERT INTO uber_eats_menu_mappings(store_mapping_id,kind,identifier_type,uber_identifier,uber_item_id,
 local_menu_item_id,mapping_action,item_mapping_mode,updated_at)
 SELECT 1,'ITEM','ID',e.uber_id,'',e.item_id,'MAP',e.mode,localtimestamp FROM expected_root e
 WHERE NOT EXISTS(SELECT 1 FROM uber_eats_menu_mappings m WHERE m.store_mapping_id=1 AND m.kind='ITEM' AND m.uber_identifier=e.uber_id);
DO $$ BEGIN
 IF (SELECT count(*) FROM uber_eats_menu_mappings m JOIN expected_root e ON e.uber_id=m.uber_identifier
 AND e.item_id=m.local_menu_item_id AND e.mode=m.item_mapping_mode WHERE m.store_mapping_id=1
 AND m.kind='ITEM' AND m.identifier_type='ID' AND m.uber_item_id='' AND m.mapping_action='MAP')<>41
 THEN RAISE EXCEPTION 'ROOT_READBACK_FAILED'; END IF;
 IF EXISTS((SELECT * FROM protected_before EXCEPT SELECT * FROM uber_eats_menu_mappings)
 UNION ALL (SELECT * FROM uber_eats_menu_mappings WHERE store_mapping_id<>1 OR kind<>'ITEM' EXCEPT SELECT * FROM protected_before))
 THEN RAISE EXCEPTION 'PROTECTED_MAPPING_CHANGED'; END IF;
END $$;
INSERT INTO audit_logs(store_id,actor_name_snapshot,actor_role_snapshot,action,entity_type,entity_id,summary,metadata_json,created_at)
 VALUES(1,'Owner-authorized configuration batch','SYSTEM','UBER_MENU_MAPPED','UBER_MAPPING',1,
 'Persist all 41 explicitly human-confirmed TEST roots; no order replay',
 '{"human_confirmed":true,"roots_before":5,"roots_after":41,"new_roots":36,"scope":"sandbox_store_1","replay_orders":false}',localtimestamp);
COMMIT;
"""
    print(json.dumps({'preflight': 'PASS', 'roots': 41, 'new_roots': 36, 'combo_roots': 8, 'sql_sha256': hashlib.sha256(sql.encode()).hexdigest()}))
    if '--apply' not in sys.argv:
        return
    assert not BACKUP.exists(), 'BACKUP_ALREADY_EXISTS_REVIEW_PRIOR_RUN'
    with BACKUP.open('xb') as out:
        subprocess.run(['docker', 'exec', DB, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], stdout=out, check=True)
    with BACKUP.open('rb') as source:
        subprocess.run(['docker', 'exec', '-i', DB, 'pg_restore', '--list'], stdin=source, stdout=subprocess.DEVNULL, check=True)
    db(sql, write=True)
    actual = json.loads(db("SELECT json_agg(t) FROM (SELECT m.uber_identifier,m.local_menu_item_id,i.sku,m.item_mapping_mode FROM uber_eats_menu_mappings m JOIN menu_items i ON i.id=m.local_menu_item_id WHERE m.store_mapping_id=1 AND m.kind='ITEM' AND m.mapping_action='MAP' ORDER BY m.uber_identifier) t;"))
    assert {(m['uber_identifier'],m['sku'],m['item_mapping_mode']) for m in actual} == {(r['uberIdentifier'],r['localSku'],r['itemMappingMode']) for r in plan}
    print(json.dumps({'status': 'PASS', 'at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'backup_bytes': BACKUP.stat().st_size, 'root_readback': actual}, ensure_ascii=False))

if __name__ == '__main__':
    try:
        main()
    except Exception as ex:
        print(json.dumps({'status': 'FAIL', 'error_type': type(ex).__name__}))
        raise SystemExit(1)
