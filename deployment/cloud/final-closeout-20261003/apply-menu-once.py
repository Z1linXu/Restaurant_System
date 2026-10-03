#!/usr/bin/env python3
"""Exact Owner-approved Staging data batch. No remap/release of historical orders."""
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
BACKUP = Path('/srv/restaurant-pos/staging/state/final-closeout-menu-20261003.dump')
TEST = 'bd993244-5589-4b19-8f0d-dc2ba73d4273'
os.umask(0o077)

def db(sql, write=False):
    opts = '-c statement_timeout=15000 -c lock_timeout=3000'
    if not write: opts += ' -c default_transaction_read_only=on'
    p = subprocess.run(['docker','exec','-i',DB,'sh','-c',
        'PGOPTIONS="'+opts+'" psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=sql,text=True,capture_output=True)
    if p.returncode: raise RuntimeError('STAGING_SQL_FAILED')
    return p.stdout.strip()

def api(path, token=None, body=None):
    headers = {'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    with urllib.request.urlopen(urllib.request.Request(BASE+path,headers=headers,
        data=None if body is None else json.dumps(body).encode()),timeout=30) as response:
        return json.load(response)['data']

def q(value): return "'"+str(value).replace("'","''")+"'"

def main():
    assert sys.argv[1:] in ([],['--apply'])
    rt=json.loads(subprocess.check_output(['docker','inspect','restaurant-pos-staging-backend-1']))[0]
    env=dict(x.split('=',1) for x in rt['Config']['Env'])
    assert env['APP_ENVIRONMENT']=='staging' and env['UBER_EATS_ENVIRONMENT']=='sandbox'
    assert rt['Config']['Labels']['org.opencontainers.image.revision']=='500aacd991fddbc8f224174fc65db833e60d7848'
    creds=json.loads(Path('/srv/restaurant-pos/staging/state/twin001-staff-credentials-v1.json').read_text())
    token=api('/api/v1/auth/login',body={'login_identifier':creds['owner_login_identifier'],'password':creds['owner_login_password']})['access_token']
    prefix='/api/v1/stores/1/integrations/uber-eats'
    conn=api(prefix+'/connection',token)
    binding=conn['store']
    assert (binding['id'],binding['storeId'],binding['organizationId'],binding['uberStoreId'],binding['environment'],binding['processingMode'],binding['enabled'])==(1,1,1,TEST,'sandbox','KITCHEN_MIRROR',True)
    plan=json.loads((HERE/'approved-modifiers.json').read_text())
    assert len(plan)==25 and len({(p['uberIdentifier'],p['uberItemId']) for p in plan})==25
    roots=json.loads(db("select json_agg(t) from (select m.uber_identifier,i.id,i.sku from uber_eats_menu_mappings m join menu_items i on i.id=m.local_menu_item_id where m.store_mapping_id=1 and m.kind='ITEM' and m.mapping_action='MAP' and i.store_id=1) t"))
    by_root={r['uber_identifier']:r for r in roots}
    assert len(by_root)==41
    choices={}
    for p in plan:
        assert p['humanConfirmed'] and by_root[p['uberItemId']]['sku']==p['localSku']
        item=by_root[p['uberItemId']]['id']
        if item not in choices: choices[item]=api(prefix+'/mapping-options/'+str(item),token)
        match=[c for c in choices[item] if c['code']==p['localOptionCode'] and c['group']==p['localOptionGroup'] and c['parentId'] is None]
        assert len(match)==1
        p['localMenuItemId']=item
    addon_before=api('/api/v1/admin/menu/addons?store_id=1',token)
    assert {c['code'] for c in addon_before['conflicts']}=={None,'extra_meat','tea_egg'}
    values=',\n'.join('('+','.join([q(p['uberIdentifier']),q(p['uberItemId']),str(p['localMenuItemId']),q(p['localOptionCode']),q(p['localOptionGroup']),q(p['localSku'])])+')' for p in plan)
    sql="""BEGIN ISOLATION LEVEL SERIALIZABLE;
SELECT id FROM stores WHERE id=1 FOR UPDATE;
SELECT id FROM uber_eats_store_mappings WHERE id=1 FOR UPDATE;
LOCK TABLE uber_eats_menu_mappings IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE expected_modifier(uber_id text,parent_id text,item_id bigint,code text,grp text,sku text,PRIMARY KEY(uber_id,parent_id)) ON COMMIT DROP;
INSERT INTO expected_modifier VALUES """+values+""";
CREATE TEMP TABLE mapping_before ON COMMIT DROP AS SELECT * FROM uber_eats_menu_mappings;
DO $$ BEGIN
 IF current_database()<>'restaurant_pos_staging' THEN RAISE EXCEPTION 'WRONG_DATABASE'; END IF;
 IF (SELECT count(*) FROM uber_eats_store_mappings b JOIN stores s ON s.id=b.store_id
 WHERE b.id=1 AND b.store_id=1 AND b.organization_id=1 AND b.environment='sandbox'
 AND b.uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273' AND b.processing_mode='KITCHEN_MIRROR'
 AND b.enabled AND s.organization_id=1 AND s.code='STG005_SRC_20260809_R01')<>1 THEN RAISE EXCEPTION 'BINDING_DRIFT'; END IF;
 IF (SELECT count(*) FROM uber_eats_menu_mappings WHERE store_mapping_id=1)<>262 THEN RAISE EXCEPTION 'MAPPING_BASELINE_DRIFT'; END IF;
 IF (SELECT count(*) FROM expected_modifier e JOIN uber_eats_menu_mappings root ON root.store_mapping_id=1
 AND root.kind='ITEM' AND root.mapping_action='MAP' AND root.identifier_type='ID' AND root.uber_item_id=''
 AND root.uber_identifier=e.parent_id AND root.local_menu_item_id=e.item_id)<>25
 THEN RAISE EXCEPTION 'ROOT_IDENTITY_DRIFT'; END IF;
 IF EXISTS(SELECT 1 FROM expected_modifier e JOIN uber_eats_menu_mappings m ON m.store_mapping_id=1 AND m.kind='MODIFIER'
 AND m.uber_identifier=e.uber_id AND m.uber_item_id=e.parent_id) THEN RAISE EXCEPTION 'MODIFIER_ALREADY_EXISTS'; END IF;
 IF (SELECT count(*) FROM expected_modifier e JOIN menu_item_options o ON o.menu_item_id=e.item_id
 JOIN menu_items i ON i.id=o.menu_item_id WHERE i.store_id=1 AND i.sku=e.sku AND o.option_code=e.code
 AND o.option_group=e.grp AND o.is_active AND o.parent_option_id IS NULL)<>25 THEN RAISE EXCEPTION 'CHOICE_DRIFT'; END IF;
END $$;
"""+(HERE/'reconcile-extra-meat.sql').read_text()+"""
INSERT INTO uber_eats_menu_mappings(store_mapping_id,kind,identifier_type,uber_identifier,uber_item_id,
 local_menu_item_id,local_option_code,local_option_group,mapping_action,item_mapping_mode,action_reason,updated_at)
 SELECT 1,'MODIFIER','ID',uber_id,parent_id,item_id,code,grp,'MAP','STANDARD','HUMAN_CONFIRMED_IDENTITY',localtimestamp FROM expected_modifier;
DO $$ BEGIN
 IF (SELECT count(*) FROM expected_modifier e JOIN uber_eats_menu_mappings m ON m.store_mapping_id=1 AND m.kind='MODIFIER'
 AND m.uber_identifier=e.uber_id AND m.uber_item_id=e.parent_id AND m.local_menu_item_id=e.item_id
 AND m.local_option_code=e.code AND m.local_option_group=e.grp AND m.mapping_action='MAP' AND m.identifier_type='ID'
 AND m.parent_option_code IS NULL)<>25 THEN RAISE EXCEPTION 'MODIFIER_READBACK_FAILED'; END IF;
 IF EXISTS(SELECT * FROM mapping_before EXCEPT SELECT * FROM uber_eats_menu_mappings) THEN RAISE EXCEPTION 'EXISTING_MAPPING_CHANGED'; END IF;
END $$;
INSERT INTO audit_logs(store_id,actor_name_snapshot,actor_role_snapshot,action,entity_type,entity_id,summary,metadata_json,created_at)
 VALUES(1,'Owner-authorized final closeout','SYSTEM','UBER_MENU_MAPPED','UBER_MAPPING',1,
 '25 confirmed modifier contexts; reconcile same-price extra_meat; no order replay',
 '{"human_confirmed":true,"modifier_contexts":25,"extra_meat_options":11,"scope":"sandbox_store_1","replay_orders":false}',localtimestamp);
COMMIT;
"""
    print(json.dumps({'preflight':'PASS','modifiers':25,'sql_sha256':hashlib.sha256(sql.encode()).hexdigest()}))
    if '--apply' not in sys.argv:return
    assert not BACKUP.exists(), 'PRIOR_RUN_REQUIRES_REVIEW'
    with BACKUP.open('xb') as f:subprocess.run(['docker','exec',DB,'sh','-c','pg_dump -Fc -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],stdout=f,check=True)
    with BACKUP.open('rb') as f:subprocess.run(['docker','exec','-i',DB,'pg_restore','--list'],stdin=f,stdout=subprocess.DEVNULL,check=True)
    db(sql,True)
    actual=json.loads(db("select json_agg(t) from (select local_option_code,count(*) contexts from uber_eats_menu_mappings where store_mapping_id=1 and kind='MODIFIER' and local_option_code in ('extra_meat','bok_choy','extra_noodle','soup_beef','soup_vegan') group by local_option_code) t"))
    assert {r['local_option_code']:r['contexts'] for r in actual}=={'extra_meat':5,'bok_choy':9,'extra_noodle':9,'soup_beef':1,'soup_vegan':1}
    addon_after=api('/api/v1/admin/menu/addons?store_id=1',token)
    assert {c['code'] for c in addon_after['conflicts']}=={None,'tea_egg'}
    print(json.dumps({'status':'PASS','at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'backup_bytes':BACKUP.stat().st_size,'mapping_readback':actual,'addon_conflicts_before':3,'addon_conflicts_after':2,'remaining_addon_conflicts':addon_after['conflicts']},ensure_ascii=False))

if __name__=='__main__':
    try: main()
    except Exception as e:
        print(json.dumps({'status':'FAIL','error_type':type(e).__name__}));raise SystemExit(1)
