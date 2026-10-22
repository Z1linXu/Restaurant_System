#!/usr/bin/env python3
"""Prepared-only bounded Client Secret configuration and read-only Uber API proof.

An operator runs this on the verified server from a real TTY. Existing Production
secret is entered with getpass, never as argv, stdin JSON, shell text or a log.
Only the OAuth token endpoint uses POST; every Eats request is GET. No order or
POS-data API is called. Production import worker remains disabled throughout.
"""
import copy
import datetime
import getpass
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import warnings

HELPERS=Path('/srv/restaurant-pos/artifacts/uber-receiver-5d2c5635faf232e5f986299e7d73167d4a7c4877')
ROOT=Path('/srv/restaurant-pos/production/uber-4483-final-20261022')
IMAGE='sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5'
SHA='4b3abd1349e66a146c36ac310b6a8b48bb2ad572'
CLIENT='6GaiQpZTcfcuTa150wr4RyztC33PV7LS'
STORE='5a7dca5c-b7ec-57c8-a684-87d23e66e8d9'
SCOPES='eats.store eats.store.orders.read'
OAUTH='https://auth.uber.com/oauth/v2/token'
STORES='https://api.uber.com/v1/eats/stores'
MENU='https://api.uber.com/v2/eats/stores/'+STORE+'/menus'
DB='cloud-db-1'
MENU_KEYS=frozenset({'menus','categories','items','modifier_groups'})


class PreparationError(RuntimeError):
    pass


def require(condition,code):
    if not condition: raise PreparationError(code)


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args): return None


def request(url,*,raw=None,token=None):
    headers={'Accept':'application/json'}
    if raw is not None: headers['Content-Type']='application/x-www-form-urlencoded'
    if token is not None: headers['Authorization']='Bearer '+token
    req=urllib.request.Request(url,data=raw,method='POST' if raw is not None else 'GET',headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect()).open(req,timeout=20) as response:
            if response.status!=200: return response.status,None
            body=response.read(8*1024*1024+1)
            require(len(body)<=8*1024*1024,'UBER_RESPONSE_TOO_LARGE')
            return response.status,body
    except urllib.error.HTTPError as error:
        status=error.code
        error.close() # Do not log or retain an error body.
        return status,None
    except PreparationError: raise
    except Exception: raise PreparationError('UBER_HTTP_REQUEST_FAILED') from None


def probe(secret,http=request):
    require(isinstance(secret,str) and bool(secret) and secret==secret.strip()
            and len(secret)<=4096 and '\n' not in secret and '\r' not in secret,'CLIENT_SECRET_INPUT_INVALID')
    status,raw=http(OAUTH,raw=urllib.parse.urlencode({'client_id':CLIENT,'client_secret':secret,
        'grant_type':'client_credentials','scope':SCOPES}).encode())
    require(status==200,'PRODUCTION_OAUTH_NOT_200')
    try: oauth=json.loads(raw)
    except Exception: raise PreparationError('OAUTH_RESPONSE_INVALID') from None
    require(isinstance(oauth,dict) and isinstance(oauth.get('access_token'),str)
            and bool(oauth['access_token'].strip()) and isinstance(oauth.get('scope'),str),
            'OAUTH_TOKEN_OR_GRANTED_SCOPE_MISSING')
    actual=set(oauth['scope'].split())
    require('eats.store.orders.read' in actual,'OAUTH_READ_SCOPE_NOT_GRANTED')
    # Expose permission identifiers only; never return or write access_token/response body.
    granted=sorted(actual & {'eats.store','eats.store.orders.read','eats.order'})
    result={'oauth_http_status':status,'oauth_granted_scopes':granted,
            'other_granted_scope_count':len(actual-set(granted))}
    token=oauth['access_token']
    store_status,store_raw=http(STORES,token=token)
    result.update({'stores_http_status':store_status,'store_count':None,'target_store_in_list':False})
    if store_status==200:
        try: listing=json.loads(store_raw)
        except Exception: raise PreparationError('STORE_LIST_RESPONSE_INVALID') from None
        require(isinstance(listing,dict) and isinstance(listing.get('stores'),list),
                'STORE_LIST_RESPONSE_INVALID')
        result['store_count']=len(listing['stores'])
        result['target_store_in_list']=any(isinstance(s,dict) and s.get('id')==STORE for s in listing['stores'])
    menu_status,menu_raw=http(MENU,token=token)
    proof={'schema_version':1,'application_client_id':CLIENT,'environment':'production',
        'uber_store_id':STORE,'method':'GET','url':MENU,'http_status':menu_status,
        'oauth_granted_scopes':granted,'authorized_store_access':False,
        'raw_menu_sha256':None,'fetched_at_utc':now()}
    valid_menu=False
    if menu_status==200:
        try: document=json.loads(menu_raw)
        except Exception: document=None
        valid_menu=isinstance(document,dict) and set(document)==MENU_KEYS and all(isinstance(document[k],list) for k in MENU_KEYS)
        if valid_menu:
            proof['authorized_store_access']=True
            proof['raw_menu_sha256']=hashlib.sha256(menu_raw).hexdigest()
            result['menu_counts']={k:len(document[k]) for k in sorted(MENU_KEYS)}
    result.update({'menu_http_status':menu_status,'store_access':'PASS' if valid_menu else 'EXTERNALLY_BLOCKED',
                   'menu_response_shape':'PASS' if valid_menu else 'NOT_VERIFIED'})
    return result,proof,menu_raw if valid_menu else None


def updated_env(original,secret):
    expected={'APP_ENVIRONMENT':'production','UBER_EATS_ENABLED':'true','UBER_EATS_WORKER_ENABLED':'false',
        'UBER_EATS_ENVIRONMENT':'production','UBER_EATS_PROCESSING_MODE':'KITCHEN_MIRROR',
        'UBER_EATS_SCOPES':SCOPES,'UBER_EATS_CLIENT_ID':CLIENT,'UBER_EATS_STORE_CUTOVERS':'{}'}
    require(all(original.get(k)==v for k,v in expected.items())
            and not original.get('UBER_EATS_PROCESSING_NOT_BEFORE')
            and bool(original.get('UBER_EATS_WEBHOOK_SIGNING_KEY')),'RECEIVE_ONLY_CONFIGURATION_REQUIRED')
    prior=original.get('UBER_EATS_CLIENT_SECRET','')
    require(not prior or prior==secret,'EXISTING_CLIENT_SECRET_DIFFERS_PRESERVE_AND_STOP')
    result=dict(original,UBER_EATS_CLIENT_SECRET=secret)
    require({k for k in set(original)|set(result) if original.get(k)!=result.get(k)}
            <= {'UBER_EATS_CLIENT_SECRET'},'UNAPPROVED_ENVIRONMENT_DELTA')
    return result


def load(name,filename):
    spec=importlib.util.spec_from_file_location(name,HELPERS/filename)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module


SAFETY_SQL="""SELECT json_build_object(
 'bindings',(SELECT coalesce(jsonb_agg(jsonb_build_object('id',id,'store',store_id,'org',organization_id,
 'env',environment,'uber',uber_store_id,'mode',processing_mode,'enabled',enabled) ORDER BY id),'[]'::jsonb)
 FROM uber_eats_store_mappings),
 'modules',(SELECT md5(coalesce(jsonb_agg(to_jsonb(m) ORDER BY id),'[]'::jsonb)::text) FROM store_modules m),
 'printing',(SELECT md5(coalesce(jsonb_agg(jsonb_build_array(id,printing_enabled,printing_mode) ORDER BY id),'[]'::jsonb)::text) FROM stores),
 'assignments',(SELECT md5(coalesce(jsonb_agg(to_jsonb(a) ORDER BY id),'[]'::jsonb)::text) FROM printer_assignments a),
 'printers',(SELECT md5(coalesce(jsonb_agg(to_jsonb(p)-'last_successful_print_at'-'last_failed_print_at'-'last_error_message'-'last_connection_success_at'-'last_connection_failed_at'-'last_connection_error'-'updated_at' ORDER BY id),'[]'::jsonb)::text) FROM printer_configs p),
 'mappings',(SELECT md5(coalesce(jsonb_agg(to_jsonb(m) ORDER BY id),'[]'::jsonb)::text) FROM uber_eats_menu_mappings m));"""


def queue_guard(ops,inventory):
    ops.require(inventory['flyway']=='38','FLYWAY_V38_REQUIRED')
    for key in ('pending_print_jobs','pending_outbox','pending_uber_events','due_uber_orders',
                'uber_orders','local_uber_orders','uber_print_jobs'):
        ops.require(inventory[key]==0,'QUEUE_OR_UBER_BUSINESS_BASELINE_DRIFT')
    ops.require(inventory['uber_bindings']==1,'EXACT_SINGLE_BINDING_REQUIRED')


def write_raw_private(path,raw):
    require(not path.exists() and not path.is_symlink(),'PRIVATE_MENU_DESTINATION_EXISTS')
    with path.open('xb') as out:
        os.fchmod(out.fileno(),0o600);out.write(raw);out.flush();os.fsync(out.fileno())


def configure(secret,oauth_result,menu_proof,menu_raw,ops,verify,receiver):
    with ops.operation_locks():
        current=ops.inspect('cloud-backend-1')
        ops.require(current['Image']==IMAGE
            and ops.inspect(IMAGE)['Config']['Labels']['org.opencontainers.image.revision']==SHA,
            'EXACT_CURRENT_APPLICATION_DRIFT_STOP')
        original_env=ops.env_of(current)
        expected=updated_env(original_env,secret)
        ops.require_production_secret_isolation(expected,ops.env_of(ops.inspect('restaurant-pos-staging-backend-1')))
        source=ops.private_file(ops.BASE/'production/current.compose.json')
        source_bytes=source.read_bytes();original=json.loads(source_bytes)
        # The generic helper's rollback disables receipt. This operation preserves original receipt/key/env instead.
        target,_=ops.models(original,IMAGE,expected)
        rollback=copy.deepcopy(original)
        ops.require(not ROOT.exists() and not ROOT.is_symlink(),'OPERATION_ROOT_EXISTS_REVIEW_REQUIRED')
        ROOT.mkdir(mode=0o700)
        for name,value in [('original.private.json',original),('target.private.json',target),('rollback.private.json',rollback)]:
            ops.write_private(ROOT/name,value)
        def compose(path,*args):return ops.run(['docker','compose','-p','cloud','-f',str(path),*args])
        model=json.loads(compose(source,'config','--format','json'))
        ops.require(model['services']['backend']['environment']==original_env
            and model['services']['backend']['image']==IMAGE,'CANONICAL_MODEL_RUNTIME_DRIFT')
        ops.validate_delta(model,json.loads(compose(ROOT/'target.private.json','config','--format','json')),IMAGE,expected)
        ops.validate_delta(model,json.loads(compose(ROOT/'rollback.private.json','config','--format','json')),IMAGE,original_env)
        before=ops.inventory(DB);queue_guard(ops,before)
        safety_before=verify.psql(DB,SAFETY_SQL)
        binding=safety_before['bindings']
        ops.require(len(binding)==1 and all(binding[0].get(k)==v for k,v in
            {'id':1,'store':1,'org':1,'env':'production','uber':STORE,'mode':'KITCHEN_MIRROR','enabled':True}.items()),
            'EXACT_ST_DENIS_BINDING_REQUIRED')
        baseline={name:ops.inspect(name)['Id'] for name in ops.run(['docker','ps','--format','{{.Names}}']).decode().splitlines()}
        backup=ROOT/'database-before.dump'
        with backup.open('xb') as out:
            os.fchmod(out.fileno(),0o600)
            completed=subprocess.run(['docker','exec',DB,'sh','-c','pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'],
                stdout=out,stderr=subprocess.PIPE,timeout=180)
        ops.require(completed.returncode==0 and backup.stat().st_size>0,'DATABASE_BACKUP_FAILED')
        with backup.open('rb') as data:
            ops.require(bool(ops.run(['docker','exec','-i',DB,'pg_restore','--list'],stdin=data)),'DATABASE_BACKUP_INVALID')
        ops.require(source.read_bytes()==source_bytes,'CANONICAL_SOURCE_CHANGED')
        queue_guard(ops,ops.inventory(DB))
        ops.write_private(ROOT/'production-menu-read-proof.json',menu_proof)
        if menu_raw is not None:write_raw_private(ROOT/'production-menu.json',menu_raw)
        changed=expected!=original_env
        apply_attempted=False
        canonical_written=False
        try:
            if changed:
                apply_attempted=True
                compose(ROOT/'target.private.json','up','-d','--no-build','--pull','never','--no-deps','backend')
            ops.wait_healthy('https://pos.lanzhounoodlesmtl.com')
            after=ops.inspect('cloud-backend-1')
            ops.require(after['Image']==IMAGE and ops.env_of(after)==expected,'RUNTIME_OAUTH_CONFIGURATION_DRIFT')
            for name,identity in baseline.items():
                if name!='cloud-backend-1':ops.require(ops.inspect(name)['Id']==identity,'UNRELATED_CONTAINER_CHANGED')
            hmac_proof=receiver.accept(expected)
            ops.require(verify.psql(DB,SAFETY_SQL)==safety_before,'STORE_MENU_PRINT_CONFIGURATION_CHANGED')
            after_counts=ops.inventory(DB);queue_guard(ops,after_counts)
            ops.require(all(after_counts[k]==before[k] for k in ('uber_bindings','uber_orders','local_uber_orders','uber_print_jobs')),
                        'UBER_BUSINESS_SIDE_EFFECT')
            ops.require(source.read_bytes()==source_bytes,'CANONICAL_SOURCE_CHANGED')
            if changed:
                ops.write_private(source,target);canonical_written=True
            result={**oauth_result,'environment':'production','source_sha':SHA,'image':IMAGE,
                'client_secret_configured':True,'receiver_enabled':True,'worker_enabled':False,
                'configuration_changed':changed,'backend_restarted':changed,'hmac_and_pos_read_smoke':hmac_proof,
                'store_configuration_unchanged':True,'production_order_delta':0,'uber_print_job_delta':0,
                'menu_file_saved':menu_raw is not None,'evidence_root':str(ROOT),'observed_at_utc':now()}
            ops.write_private(ROOT/'result.json',result)
            return result
        except BaseException:
            if apply_attempted:
                # Do not recreate a backend using stale configuration if any outside writer
                # changed the canonical model, even when the application image is unchanged.
                current_source=source.read_bytes()
                if current_source!=source_bytes:
                    try: owned_target=canonical_written and json.loads(current_source)==target
                    except Exception: owned_target=False
                    ops.require(owned_target,'CANONICAL_DRIFT_REQUIRES_OPERATOR_NO_RECREATE')
                ops.require(ops.inspect('cloud-backend-1')['Image']==IMAGE,'ROLLBACK_APPLICATION_DRIFT_STOP_NO_OLD_APP')
                compose(ROOT/'rollback.private.json','up','-d','--no-build','--pull','never','--no-deps','backend')
                ops.wait_healthy('https://pos.lanzhounoodlesmtl.com')
                restored=ops.inspect('cloud-backend-1')
                ops.require(restored['Image']==IMAGE and ops.env_of(restored)==original_env,'ORIGINAL_RECEIVE_ONLY_ROLLBACK_FAILED')
                if source.read_bytes()!=source_bytes:
                    ops.require(canonical_written and json.loads(source.read_bytes())==target,'CANONICAL_DRIFT_REQUIRES_OPERATOR')
                    ops.write_private(source,rollback)
            raise ops.PromotionError('OAUTH_CONFIGURATION_FAILED_ORIGINAL_RECEIVE_ONLY_RESTORED' if apply_attempted
                                     else 'OAUTH_ACCEPTANCE_FAILED_NO_RUNTIME_CHANGE') from None


def main():
    os.umask(0o077)
    try:
        require(sys.stdin.isatty(),'REAL_TTY_REQUIRED_FOR_HIDDEN_SECRET')
        with warnings.catch_warnings():
            warnings.simplefilter('error',getpass.GetPassWarning)
            secret=getpass.getpass('Existing Production App Client Secret (hidden): ')
        oauth_result,menu_proof,menu_raw=probe(secret)
        ops=load('oauth_prepare_ops','promote-uber-receive-only-20261022.py')
        verify=load('oauth_prepare_verify','verify-uber-receive-only-20261022.py')
        receiver=load('oauth_prepare_receiver','configure-uber-production-receiver.py')
        result=configure(secret,oauth_result,menu_proof,menu_raw,ops,verify,receiver)
        print(json.dumps(result))
        return 0
    except BaseException as error:
        # Only fixed codes/type names escape. Never stringify arbitrary network/parser errors.
        allowed=isinstance(error,PreparationError) or type(error).__name__=='PromotionError'
        print('UBER_4483_OAUTH_PREPARATION_FAILED:'+(str(error) if allowed else type(error).__name__))
        return 1


if __name__=='__main__':raise SystemExit(main())
