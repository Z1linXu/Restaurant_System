#!/usr/bin/env python3
"""Exact Owner-approved Sandbox menu batch. Does not invoke remap or replay orders."""
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.request

HERE = Path(__file__).resolve().parent
DB = 'restaurant-pos-staging-db-1'
BASE = 'https://staging-pos.lanzhounoodlesmtl.com'
BACKUP = Path('/srv/restaurant-pos/staging/state/final-channels-menu-20261003.dump')
RUNTIME_SHA = 'ad3de48e973298604362cf21f119434d0b3f85f6'
TEST = 'bd993244-5589-4b19-8f0d-dc2ba73d4273'
TEA_IDS = [36, 50, 76, 102, 128, 155, 184, 213, 248, 309, 347]
HISTORY = ('orders', 'order_items', 'order_item_options', 'order_update_batches',
           'kitchen_tasks', 'frontdesk_beverage_items', 'production_tasks',
           'inventory_transactions', 'order_dispatch_outbox', 'print_jobs',
           'uber_eats_orders', 'uber_eats_events')
os.umask(0o077)


def db(sql, write=False):
    opts = '-c statement_timeout=15000 -c lock_timeout=3000'
    if not write:
        opts += ' -c default_transaction_read_only=on'
    result = subprocess.run(['docker', 'exec', '-i', DB, 'sh', '-c',
        'PGOPTIONS="' + opts + '" psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=sql, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('STAGING_SQL_FAILED')
    return result.stdout.strip()


def api(path, token=None, body=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    with urllib.request.urlopen(urllib.request.Request(BASE + path, headers=headers,
            data=None if body is None else json.dumps(body).encode()), timeout=30) as response:
        return json.load(response)['data']


def q(value):
    return 'NULL' if value is None else "'" + str(value).replace("'", "''") + "'"


def validate_plan(plan):
    parents = {
        'd2a77967-74f3-4dfb-9e8e-a236369bb4f6': (15, 'chicken_chow_mein'),
        '9c8260ac-717a-4d20-80b5-aeef17613186': (14, 'beef_chow_mein'),
        'b11777c1-9d50-44fe-8397-040a5056152b': (16, 'tomato_chow_mein'),
        '842d5987-6945-42ef-a1d9-ed15cad18a0b': (17, 'vegetable_chow_mein')}
    eggs = {'43731428-8827-403a-b401-246ff779bef0': 'fried_egg',
            '316bb70f-f2a4-4b44-9b7a-7ad73c5243d9': 'tea_egg'}
    vegetables = {'3b8b2764-d492-47e0-9977-107ed4e67d1f', 'e72231fb-9029-4539-91d6-b6efbeeb3767'}
    greens = {'54c53703-db68-4044-85a3-5f4962715566', 'd1f4a19b-fd7c-4cdf-863a-581391172f23'}
    expected = {(parent, modifier) for parent in parents for modifier in eggs}
    expected |= {(parent, modifier) for parent in vegetables for modifier in greens}
    assert len(plan) == 12 and {(p['uberItemId'], p['uberIdentifier']) for p in plan} == expected
    for p in plan:
        assert p['humanConfirmed'] is True
        if p['uberItemId'] in parents:
            assert (p['localMenuItemId'], p['localSku']) == parents[p['uberItemId']]
            assert (p['mappingAction'], p['localOptionCode'], p['localOptionGroup'], p['actionReason']) == (
                'MAP', eggs[p['uberIdentifier']], 'ADD_ON', 'HUMAN_CONFIRMED_IDENTITY')
        else:
            assert (p['localMenuItemId'], p['localSku'], p['mappingAction'], p['localOptionCode'], p['localOptionGroup'], p['actionReason']) == (
                3, 'vegetable_noodle', 'NO_OP', None, None, 'INGREDIENT_NOT_USED')


def history_query():
    # Hash inside PostgreSQL; no raw order, financial, customer or print snapshots leave the DB.
    return ' UNION ALL '.join(
        "SELECT " + q(table) + " AS name,count(*) AS rows,md5(coalesce(string_agg(md5(to_jsonb(t)::text),'' ORDER BY t.id),'')) AS fingerprint FROM " + table + ' t'
        for table in HISTORY)


def build_sql(plan):
    values = ',\n'.join('(' + ','.join([q(p['uberIdentifier']), q(p['uberItemId']),
        str(p['localMenuItemId']), q(p['localOptionCode']), q(p['localOptionGroup']),
        q(p['localSku']), q(p['mappingAction']), q(p['actionReason'])]) + ')' for p in plan)
    sql = """BEGIN ISOLATION LEVEL SERIALIZABLE;
SELECT id FROM stores WHERE id=1 FOR UPDATE;
SELECT id FROM uber_eats_store_mappings WHERE id=1 FOR UPDATE;
LOCK TABLE uber_eats_menu_mappings IN SHARE ROW EXCLUSIVE MODE;
""" + 'LOCK TABLE ' + ','.join(HISTORY) + ' IN SHARE MODE;\n' + """
CREATE TEMP TABLE expected_modifier(uber_id text,parent_id text,item_id bigint,code text,grp text,sku text,action text,reason text,PRIMARY KEY(uber_id,parent_id)) ON COMMIT DROP;
INSERT INTO expected_modifier VALUES """ + values + """;
CREATE TEMP TABLE mapping_before ON COMMIT DROP AS SELECT * FROM uber_eats_menu_mappings;
CREATE TEMP TABLE options_before ON COMMIT DROP AS SELECT * FROM menu_item_options;
CREATE TEMP TABLE addons_before ON COMMIT DROP AS SELECT * FROM store_addons;
CREATE TEMP TABLE history_before ON COMMIT DROP AS """ + history_query() + """;
DO $$ BEGIN
 IF current_database()<>'restaurant_pos_staging' THEN RAISE EXCEPTION 'WRONG_DATABASE'; END IF;
 IF (SELECT count(*) FROM uber_eats_store_mappings b JOIN stores s ON s.id=b.store_id
 WHERE b.id=1 AND b.store_id=1 AND b.organization_id=1 AND b.environment='sandbox'
 AND b.uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273' AND b.processing_mode='KITCHEN_MIRROR'
 AND b.enabled AND s.organization_id=1 AND s.code='STG005_SRC_20260809_R01')<>1 THEN RAISE EXCEPTION 'BINDING_DRIFT'; END IF;
 IF (SELECT count(*) FROM uber_eats_menu_mappings WHERE store_mapping_id=1)<>287 THEN RAISE EXCEPTION 'MAPPING_BASELINE_DRIFT'; END IF;
 IF (SELECT count(*) FROM expected_modifier e JOIN uber_eats_menu_mappings root ON root.store_mapping_id=1
 AND root.kind='ITEM' AND root.mapping_action='MAP' AND root.identifier_type='ID' AND root.uber_item_id=''
 AND root.uber_identifier=e.parent_id AND root.local_menu_item_id=e.item_id
 JOIN menu_items i ON i.id=e.item_id AND i.store_id=1 AND i.sku=e.sku)<>12
 THEN RAISE EXCEPTION 'ROOT_IDENTITY_DRIFT'; END IF;
 IF EXISTS(SELECT 1 FROM expected_modifier e JOIN uber_eats_menu_mappings m ON m.store_mapping_id=1
 AND m.kind IN ('MODIFIER','REMOVED_MODIFIER') AND m.uber_identifier=e.uber_id AND m.uber_item_id=e.parent_id)
 THEN RAISE EXCEPTION 'MODIFIER_ALREADY_EXISTS'; END IF;
 IF (SELECT count(*) FROM expected_modifier e JOIN menu_item_options o ON o.menu_item_id=e.item_id
 WHERE e.action='MAP' AND o.option_code=e.code AND o.option_group=e.grp AND o.is_active AND o.parent_option_id IS NULL)<>8
 THEN RAISE EXCEPTION 'CHOICE_DRIFT'; END IF;
END $$;
""" + (HERE / 'reconcile-tea-egg.sql').read_text() + """
INSERT INTO uber_eats_menu_mappings(store_mapping_id,kind,identifier_type,uber_identifier,uber_item_id,
 local_menu_item_id,local_option_code,local_option_group,mapping_action,item_mapping_mode,action_reason,updated_at)
 SELECT 1,'MODIFIER','ID',uber_id,parent_id,item_id,code,grp,action,'STANDARD',reason,localtimestamp FROM expected_modifier;
CREATE TEMP TABLE history_after ON COMMIT DROP AS """ + history_query() + """;
DO $$ BEGIN
 IF (SELECT count(*) FROM expected_modifier e JOIN uber_eats_menu_mappings m ON m.store_mapping_id=1 AND m.kind='MODIFIER'
 AND m.uber_identifier=e.uber_id AND m.uber_item_id=e.parent_id AND m.local_menu_item_id=e.item_id
 AND m.local_option_code IS NOT DISTINCT FROM e.code AND m.local_option_group IS NOT DISTINCT FROM e.grp
 AND m.mapping_action=e.action AND m.action_reason=e.reason AND m.identifier_type='ID'
 AND m.item_mapping_mode='STANDARD' AND m.parent_option_code IS NULL)<>12 THEN RAISE EXCEPTION 'MODIFIER_READBACK_FAILED'; END IF;
 IF (SELECT count(*) FROM uber_eats_menu_mappings WHERE store_mapping_id=1)<>299
 OR (SELECT count(*) FROM uber_eats_menu_mappings WHERE store_mapping_id=1 AND kind='ITEM' AND mapping_action='MAP')<>41
 OR (SELECT count(*) FROM uber_eats_menu_mappings WHERE store_mapping_id=1 AND kind='MODIFIER' AND mapping_action='MAP')<>238
 OR (SELECT count(*) FROM uber_eats_menu_mappings WHERE store_mapping_id=1 AND mapping_action='NO_OP')<>20
 THEN RAISE EXCEPTION 'MAPPING_TOTAL_READBACK_FAILED'; END IF;
 IF EXISTS(SELECT * FROM mapping_before EXCEPT SELECT * FROM uber_eats_menu_mappings)
 THEN RAISE EXCEPTION 'EXISTING_MAPPING_CHANGED'; END IF;
 IF EXISTS(SELECT * FROM options_before WHERE id NOT IN (SELECT id FROM approved_tea_egg)
 EXCEPT SELECT * FROM menu_item_options WHERE id NOT IN (SELECT id FROM approved_tea_egg))
 OR (SELECT count(*) FROM options_before)<>(SELECT count(*) FROM menu_item_options)
 THEN RAISE EXCEPTION 'UNAPPROVED_OPTION_CHANGED'; END IF;
 IF EXISTS(SELECT 1 FROM options_before b JOIN menu_item_options a ON a.id=b.id JOIN approved_tea_egg p ON p.id=b.id
 WHERE to_jsonb(b)-ARRAY['name_zh','store_addon_id','store_addon_store_id','addon_eligible','updated_at']
 IS DISTINCT FROM to_jsonb(a)-ARRAY['name_zh','store_addon_id','store_addon_store_id','addon_eligible','updated_at'])
 THEN RAISE EXCEPTION 'TEA_EGG_UNAPPROVED_FIELD_CHANGED'; END IF;
 IF EXISTS(SELECT * FROM addons_before EXCEPT SELECT * FROM store_addons)
 THEN RAISE EXCEPTION 'EXISTING_ADDON_CHANGED'; END IF;
 IF EXISTS(SELECT * FROM history_before EXCEPT SELECT * FROM history_after)
 THEN RAISE EXCEPTION 'ORDER_HISTORY_CHANGED'; END IF;
END $$;
INSERT INTO audit_logs(store_id,actor_name_snapshot,actor_role_snapshot,action,entity_type,entity_id,summary,metadata_json,created_at)
 VALUES(1,'Owner-authorized final channel closeout','SYSTEM','UBER_MENU_MAPPED','UBER_MAPPING',1,
 '8 confirmed egg modifier contexts; 4 vegetable greens NO_OP; 11 canonical tea_egg links; no order replay',
 '{"human_confirmed":true,"modifier_maps":8,"modifier_noops":4,"tea_egg_options":11,"scope":"sandbox_store_1","replay_orders":false}',localtimestamp);
SELECT json_build_object('history_unchanged',true,'history_hashes',json_agg(history_after)) FROM history_after;
COMMIT;
"""
    return sql



def parse_history_evidence(output):
    # PostgreSQL can pretty-print composite rows inside json_agg across lines.
    # Decode one whole object, allowing only psql transaction tags/lock IDs around it.
    start = output.find('{')
    assert start >= 0, 'HISTORY_EVIDENCE_MISSING'
    evidence, end = json.JSONDecoder().raw_decode(output[start:])
    surrounding = output[:start] + '\n' + output[start + end:]
    assert all(not line.strip() or line.strip() in {'BEGIN', 'COMMIT'} or line.strip().isdecimal()
               for line in surrounding.splitlines()), 'UNEXPECTED_SQL_OUTPUT_OR_EXTRA_JSON'
    assert isinstance(evidence, dict) and evidence.get('history_unchanged') is True, 'HISTORY_EVIDENCE_INVALID'
    return evidence


def runtime():
    inspected = json.loads(subprocess.check_output(['docker', 'inspect', 'restaurant-pos-staging-backend-1']))[0]
    env = dict(value.split('=', 1) for value in inspected['Config']['Env'])
    assert env['APP_ENVIRONMENT'] == 'staging' and env['UBER_EATS_ENVIRONMENT'] == 'sandbox'
    assert env['UBER_EATS_CLIENT_ID'] == 't86ofdunSsVjL-0AvK6MA6LCTyF2eYLf'
    assert inspected['Config']['Labels']['org.opencontainers.image.revision'] == RUNTIME_SHA


def production_fingerprints():
    result = {}
    for name in ('cloud-backend-1', 'cloud-nginx-1', 'cloud-db-1'):
        item = json.loads(subprocess.check_output(['docker', 'inspect', name]))[0]
        result[name] = (item['Id'], item['Image'], item['State']['StartedAt'], item['State']['Running'])
    return result


def main():
    assert sys.argv[1:] in ([], ['--apply'], ['--check-plan'])
    plan = json.loads((HERE / 'approved-modifiers.json').read_text())
    validate_plan(plan)
    sql = build_sql(plan)
    if sys.argv[1:] == ['--check-plan']:
        print(json.dumps({'static_plan': 'PASS', 'maps': 8, 'noops': 4, 'tea_egg_ids': TEA_IDS,
                          'sql_sha256': hashlib.sha256(sql.encode()).hexdigest()}))
        return
    runtime()
    production_before = production_fingerprints()
    creds = json.loads(Path('/srv/restaurant-pos/staging/state/twin001-staff-credentials-v1.json').read_text())
    token = api('/api/v1/auth/login', body={'login_identifier': creds['owner_login_identifier'],
        'password': creds['owner_login_password']})['access_token']
    prefix = '/api/v1/stores/1/integrations/uber-eats'
    binding = api(prefix + '/connection', token)['store']
    assert (binding['id'], binding['storeId'], binding['organizationId'], binding['uberStoreId'],
        binding['environment'], binding['processingMode'], binding['enabled']) == (1, 1, 1, TEST, 'sandbox', 'KITCHEN_MIRROR', True)
    assert db('select count(*) from uber_eats_menu_mappings where store_mapping_id=1') == '287'
    roots = json.loads(db("select json_agg(t) from (select m.uber_identifier,i.id,i.sku from uber_eats_menu_mappings m join menu_items i on i.id=m.local_menu_item_id where m.store_mapping_id=1 and m.kind='ITEM' and m.mapping_action='MAP' and i.store_id=1) t"))
    by_root = {r['uber_identifier']: r for r in roots}
    assert len(by_root) == 41
    choices = {}
    for p in plan:
        assert (by_root[p['uberItemId']]['id'], by_root[p['uberItemId']]['sku']) == (p['localMenuItemId'], p['localSku'])
        if p['mappingAction'] == 'MAP':
            item = p['localMenuItemId']
            if item not in choices:
                choices[item] = api(prefix + '/mapping-options/' + str(item), token)
            assert len([c for c in choices[item] if c['code'] == p['localOptionCode']
                        and c['group'] == p['localOptionGroup'] and c['parentId'] is None]) == 1
    addon_before = api('/api/v1/admin/menu/addons?store_id=1', token)
    assert {c['code'] for c in addon_before['conflicts']} == {None, 'tea_egg'}
    print(json.dumps({'preflight': 'PASS', 'maps': 8, 'noops': 4, 'sql_sha256': hashlib.sha256(sql.encode()).hexdigest()}))
    if '--apply' not in sys.argv:
        return
    assert not BACKUP.exists(), 'PRIOR_RUN_REQUIRES_REVIEW'
    with BACKUP.open('xb') as target:
        subprocess.run(['docker', 'exec', DB, 'sh', '-c', 'pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'], stdout=target, check=True)
    assert BACKUP.stat().st_size > 0
    with BACKUP.open('rb') as source:
        subprocess.run(['docker', 'exec', '-i', DB, 'pg_restore', '--list'], stdin=source, stdout=subprocess.DEVNULL, check=True)
    runtime()  # Recheck exact Staging runtime immediately before the transaction.
    write_result = db(sql, True)
    history_evidence = [parse_history_evidence(write_result)]
    assert len(history_evidence) == 1 and history_evidence[0]['history_unchanged'] is True
    counts = json.loads(db("select json_build_object('total',count(*),'roots',count(*) filter(where kind='ITEM' and mapping_action='MAP'),'modifier_maps',count(*) filter(where kind='MODIFIER' and mapping_action='MAP'),'noops',count(*) filter(where mapping_action='NO_OP')) from uber_eats_menu_mappings where store_mapping_id=1"))
    assert counts == {'total': 299, 'roots': 41, 'modifier_maps': 238, 'noops': 20}
    addon_after = api('/api/v1/admin/menu/addons?store_id=1', token)
    assert {c['code'] for c in addon_after['conflicts']} == {None}
    tea = [a for a in addon_after['addons'] if a['code'] == 'tea_egg']
    assert len(tea) == 1 and (tea[0]['name_zh'], tea[0]['name_en'], str(tea[0]['price']), tea[0]['active']) == ('加卤蛋', 'Extra Tea Egg', '1.99', True)
    assert production_fingerprints() == production_before, 'PRODUCTION_RUNTIME_CHANGED'
    print(json.dumps({'status': 'PASS', 'at': datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'backup_bytes': BACKUP.stat().st_size, 'mapping_readback': counts,
        'tea_egg_linked_ids': TEA_IDS, 'addon_conflicts_before': len(addon_before['conflicts']),
        'addon_conflicts_after': len(addon_after['conflicts']),
        'remaining_conflicts': addon_after['conflicts'], 'production_changed': False,
        'history_evidence': history_evidence[0]}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print(json.dumps({'status': 'FAIL', 'error_type': type(error).__name__}))
        raise SystemExit(1)
