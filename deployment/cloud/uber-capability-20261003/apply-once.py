#!/usr/bin/env python3
"""Bounded Owner-confirmed option 370 and existing TEST Store capability; Staging only."""
import datetime,fcntl,importlib.util,json,os,re,subprocess,sys,urllib.request
from pathlib import Path
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
os.umask(0o077)
spec=importlib.util.spec_from_file_location('menu_batch',HERE.parent/'final-channels-20261003/apply-menu-once.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*a,**kw):return None
http=urllib.request.build_opener(NoRedirect)
def api(path,token=None,body=None,method=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    with http.open(urllib.request.Request(m.BASE+path,headers=headers,method=method,
            data=None if body is None else json.dumps(body).encode()),timeout=30) as response:
        assert response.status==200
        return json.load(response)['data']
def main():
    sha=sys.argv[1];assert re.fullmatch('[0-9a-f]{40}',sha)
    assert HERE.parents[2]==Path('/srv/restaurant-pos/staging/releases')/sha
    assert subprocess.check_output(['git','-C',str(HERE),'rev-parse','HEAD'],text=True).strip()==sha
    assert not subprocess.check_output(['git','-C',str(HERE),'status','--porcelain'])
    backend=json.loads(subprocess.check_output(['docker','inspect','restaurant-pos-staging-backend-1']))[0]
    assert backend['Config']['Labels']['org.opencontainers.image.revision']==sha
    env=dict(v.split('=',1) for v in backend['Config']['Env'])
    assert env['APP_ENVIRONMENT']=='staging' and env['UBER_EATS_ENVIRONMENT']=='sandbox'
    assert env['UBER_EATS_CLIENT_ID']=='t86ofdunSsVjL-0AvK6MA6LCTyF2eYLf'
    backup=Path('/srv/restaurant-pos/staging/uber-capability-20261003/staging-before.dump')
    assert backup.is_file() and backup.stat().st_size>0
    with backup.open('rb') as f:
        subprocess.run(['docker','exec','-i',m.DB,'pg_restore','--list'],stdin=f,stdout=subprocess.DEVNULL,check=True)
    assert m.db("select version from flyway_schema_history order by installed_rank desc limit 1")=='37'
    assert m.db("select count(*) from uber_eats_store_mappings where store_id=1 and organization_id=1 and environment='sandbox' and uber_store_id='bd993244-5589-4b19-8f0d-dc2ba73d4273' and enabled and processing_mode='KITCHEN_MIRROR'")=='1'
    creds=json.loads(Path('/srv/restaurant-pos/staging/state/twin001-staff-credentials-v1.json').read_text())
    token=api('/api/v1/auth/login',body={'login_identifier':creds['owner_login_identifier'],'password':creds['owner_login_password']})['access_token']
    before=api('/api/v1/admin/menu/addons?store_id=1',token)
    assert len(before['conflicts'])==1 and before['conflicts'][0]['option_ids']==[370]
    sql="BEGIN ISOLATION LEVEL SERIALIZABLE; SELECT id FROM stores WHERE id=1 FOR UPDATE;\n"
    sql+='LOCK TABLE '+','.join(m.HISTORY)+' IN SHARE MODE;\n'
    sql+='LOCK TABLE menu_item_options,store_addons IN SHARE ROW EXCLUSIVE MODE;\n'
    sql+='CREATE TEMP TABLE history_before ON COMMIT DROP AS '+m.history_query()+';\n'
    sql+=(HERE/'reconcile-option370.sql').read_text()
    sql+='CREATE TEMP TABLE history_after ON COMMIT DROP AS '+m.history_query()+''';
DO $$ BEGIN
 IF EXISTS(SELECT * FROM history_before EXCEPT SELECT * FROM history_after)
 THEN RAISE EXCEPTION 'HISTORICAL_BUSINESS_DATA_CHANGED'; END IF;
END $$;
SELECT json_build_object('history_unchanged',true,'history_hashes',json_agg(history_after)) FROM history_after;
COMMIT;'''
    proof=m.parse_history_evidence(m.db(sql,write=True))
    after=api('/api/v1/admin/menu/addons?store_id=1',token);assert not after['conflicts']
    modules=api('/api/v1/admin/stores/1/modules',token,{'store_id':1,'modules':[{'module_key':'UBER_EATS','enabled':True}]},'PUT')
    assert any(x['module_key']=='UBER_EATS' and x['enabled'] and x['persisted'] for x in modules['modules'])
    assert m.db("select count(*) from store_modules where module_key='UBER_EATS' and enabled and store_id<>1")=='0'
    assert m.db("select count(*) from uber_eats_store_mappings b join store_modules s on s.store_id=b.store_id and s.module_key='UBER_EATS' where b.enabled and not s.enabled")=='0'
    print(json.dumps({'status':'PASS','at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sha':sha,
        'option_id':370,'code':'tea_egg','name_zh':'加卤蛋','name_en':'Extra Tea Egg','price':1.99,
        'addon_conflicts_after':0,'enabled_test_store':1,'all_other_stores_disabled':True,'history':proof},ensure_ascii=False))
if __name__=='__main__':
    locks=[]
    for name in ['/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock','/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock']:
        path=Path(name);assert path.is_file() and not path.is_symlink()
        h=path.open('r+');fcntl.flock(h,fcntl.LOCK_EX|fcntl.LOCK_NB);locks.append(h)
    try:main()
    except Exception as ex:
        print('STAGING_CONFIG_FAILED:'+type(ex).__name__,file=sys.stderr);sys.exit(1)
