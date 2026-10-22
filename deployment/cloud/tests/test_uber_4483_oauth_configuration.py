import contextlib,copy,importlib.util,io,json,subprocess,tempfile,types,unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('prepare',Path(__file__).resolve().parents[1]/'configure-uber-4483-oauth-20261022.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
MENU=b'{"menus":[],"categories":[],"items":[],"modifier_groups":[]}'

class FakeHttp:
 def __init__(self,scope='eats.store eats.store.orders.read',stores_status=200,stores=None,menu_status=200,menu=MENU,oauth_status=200,oauth_error=None):
  self.calls=[];self.scope=scope;self.stores_status=stores_status;self.stores=[] if stores is None else stores;self.menu_status=menu_status;self.menu=menu;self.oauth_status=oauth_status;self.oauth_error=oauth_error
 def __call__(self,url,*,raw=None,token=None):
  self.calls.append((url,raw,token))
  if url==m.OAUTH:
   assert raw is not None and token is None
   if self.oauth_status!=200:return self.oauth_status,self.oauth_error
   return 200,json.dumps({'access_token':'fixture-token-never-output','scope':self.scope}).encode()
  assert raw is None and token=='fixture-token-never-output'
  if url==m.STORES:return self.stores_status,json.dumps({'stores':self.stores}).encode() if self.stores_status==200 else None
  assert url==m.MENU
  return self.menu_status,self.menu if self.menu_status==200 else None

class PromotionError(RuntimeError):pass

class FakeOps:
 PromotionError=PromotionError
 def __init__(self,base,env):
  self.BASE=base;self.original=copy.deepcopy(env);self.current=copy.deepcopy(env);self.up=[]
 @contextlib.contextmanager
 def operation_locks(self):yield
 def require(self,condition,code):
  if not condition:raise PromotionError(code)
 def inspect(self,name):
  if name==m.IMAGE:return {'Config':{'Labels':{'org.opencontainers.image.revision':m.SHA}}}
  if name=='cloud-backend-1':return {'Id':'backend','Image':m.IMAGE,'env':self.current}
  if name=='restaurant-pos-staging-backend-1':return {'Id':'stage','env':{'UBER_EATS_CLIENT_SECRET':'fixture-sandbox-secret'}}
  return {'Id':name}
 def env_of(self,container):return container['env']
 def private_file(self,path):return Path(path)
 def models(self,original,image,env):
  target=copy.deepcopy(original);target['services']['backend']['image']=image;target['services']['backend']['environment']=dict(env)
  bad=copy.deepcopy(original);bad['services']['backend']['environment']['UBER_EATS_ENABLED']='false'
  return target,bad
 def validate_delta(self,before,after,image,env):
  assert after['services']['backend']['image']==image
  assert after['services']['backend']['environment']==env
 def require_production_secret_isolation(self,*args):pass
 def write_private(self,path,value):
  path=Path(path);path.write_text(json.dumps(value,indent=2));path.chmod(0o600)
 def inventory(self,db):
  return {'flyway':'38',**{k:0 for k in ('pending_print_jobs','pending_outbox','pending_uber_events','due_uber_orders','uber_orders','local_uber_orders','uber_print_jobs')},'uber_bindings':1}
 def wait_healthy(self,*args):pass
 def run(self,args,**kwargs):
  if args[:3]==['docker','compose','-p']:
   p=Path(args[args.index('-f')+1]);model=json.loads(p.read_text())
   if 'config' in args:return json.dumps(model).encode()
   assert 'up' in args
   assert args[-1]=='backend' and '--no-deps' in args and '--no-build' in args
   self.current=model['services']['backend']['environment'];self.up.append(p.name);return b''
  if args==['docker','ps','--format','{{.Names}}']:return b'cloud-backend-1\ncloud-db-1\ncloud-nginx-1\n'
  if args==['docker','exec','-i',m.DB,'pg_restore','--list']:return b'fixture restore listing'
  raise AssertionError(args)

class Tests(unittest.TestCase):
 def base(self):
  return {'APP_ENVIRONMENT':'production','UBER_EATS_ENABLED':'true','UBER_EATS_WORKER_ENABLED':'false','UBER_EATS_ENVIRONMENT':'production','UBER_EATS_PROCESSING_MODE':'KITCHEN_MIRROR','UBER_EATS_SCOPES':m.SCOPES,'UBER_EATS_CLIENT_ID':m.CLIENT,'UBER_EATS_STORE_CUTOVERS':'{}','UBER_EATS_PROCESSING_NOT_BEFORE':'','UBER_EATS_WEBHOOK_SIGNING_KEY':'fixture-original-primary-key','UNRELATED':'fixture-preserved'}
 def test_probe_exact_readonly_requests_and_original_menu_hash(self):
  h=FakeHttp(stores=[{'id':m.STORE}]);result,proof,raw=m.probe('fixture-input-secret',h)
  self.assertEqual([c[0] for c in h.calls],[m.OAUTH,m.STORES,m.MENU])
  self.assertEqual(m.urllib.parse.parse_qs(h.calls[0][1].decode())['scope'],[m.SCOPES])
  self.assertEqual(raw,MENU);self.assertEqual(result['store_count'],1)
  self.assertEqual(proof['raw_menu_sha256'],m.hashlib.sha256(MENU).hexdigest())
  self.assertTrue(proof['authorized_store_access']);self.assertNotIn('fixture-token',json.dumps((result,proof)))
 def test_missing_read_grant_fails_before_get(self):
  h=FakeHttp(scope='eats.store')
  with self.assertRaisesRegex(m.PreparationError,'OAUTH_READ_SCOPE_NOT_GRANTED') as caught:m.probe('fixture-secret',h)
  self.assertEqual(len(h.calls),1)
  self.assertEqual(caught.exception.diagnostic['oauth_http_status'],200)
  self.assertEqual(caught.exception.diagnostic['oauth_granted_scopes'],['eats.store'])
  self.assertEqual(caught.exception.diagnostic['other_granted_scope_count'],0)
 def test_missing_read_grant_only_counts_untrusted_scope_names(self):
  h=FakeHttp(scope='eats.store fixture-private-untrusted-scope')
  with self.assertRaises(m.OAuthProbeError) as caught:m.probe('fixture-secret',h)
  self.assertEqual(caught.exception.diagnostic['oauth_granted_scopes'],['eats.store'])
  self.assertEqual(caught.exception.diagnostic['other_granted_scope_count'],1)
  self.assertNotIn('fixture-private-untrusted-scope',json.dumps(caught.exception.diagnostic))
  self.assertEqual(len(h.calls),1)
 def test_store_access_403_remains_external_not_oauth_configuration_failure(self):
  result,proof,raw=m.probe('fixture-secret',FakeHttp(stores_status=403,menu_status=403))
  self.assertEqual(result['oauth_http_status'],200);self.assertEqual(result['stores_http_status'],403)
  self.assertEqual(result['store_access'],'EXTERNALLY_BLOCKED');self.assertFalse(proof['authorized_store_access']);self.assertIsNone(raw)
 def test_zero_stores_and_no_menu_are_external(self):
  result,proof,raw=m.probe('fixture-secret',FakeHttp(menu_status=403))
  self.assertEqual(result['store_count'],0);self.assertEqual(result['menu_http_status'],403);self.assertIsNone(raw)
 def test_menu_error_or_extra_top_fields_never_saved(self):
  result,proof,raw=m.probe('fixture-secret',FakeHttp(menu=b'{"menus":[],"categories":[],"items":[],"modifier_groups":[],"extra":"fixture-private"}'))
  self.assertFalse(proof['authorized_store_access']);self.assertIsNone(raw);self.assertNotIn('fixture-private',json.dumps((result,proof)))
 def test_only_secret_delta_and_original_receiver_key_preserved(self):
  original=self.base();updated=m.updated_env(original,'fixture-new-secret')
  self.assertEqual({k for k in updated if updated[k]!=original.get(k)},{'UBER_EATS_CLIENT_SECRET'})
  self.assertEqual(updated['UBER_EATS_WEBHOOK_SIGNING_KEY'],original['UBER_EATS_WEBHOOK_SIGNING_KEY'])
  self.assertEqual(updated['UBER_EATS_WORKER_ENABLED'],'false');self.assertEqual(updated['UBER_EATS_STORE_CUTOVERS'],'{}')
 def test_different_existing_secret_preserved(self):
  env=self.base();env['UBER_EATS_CLIENT_SECRET']='fixture-original-secret'
  with self.assertRaisesRegex(m.PreparationError,'EXISTING_CLIENT_SECRET_DIFFERS'):m.updated_env(env,'fixture-other-secret')
  self.assertEqual(m.updated_env(env,'fixture-original-secret'),env)
 def test_non_tty_refuses_before_secret_prompt_or_network(self):
  with patch.object(m,'load'),patch.object(m,'configured_secret',return_value=('', 'fixture-signing-key')),patch.object(m.sys,'stdin',types.SimpleNamespace(isatty=lambda:False)),patch.object(m.getpass,'getpass') as hidden,patch.object(m,'probe') as probe,contextlib.redirect_stdout(io.StringIO()) as output:
   self.assertEqual(m.main(),1);hidden.assert_not_called();probe.assert_not_called()
   self.assertIn('REAL_TTY_REQUIRED',output.getvalue())
 def test_getpass_warning_cannot_fall_back_to_echoed_input(self):
  def fallback(prompt):
   m.warnings.warn('cannot disable echo',m.getpass.GetPassWarning)
   self.fail('fallback read must not run')
  with patch.object(m,'load'),patch.object(m,'configured_secret',return_value=('', 'fixture-signing-key')),patch.object(m.sys,'stdin',types.SimpleNamespace(isatty=lambda:True)),patch.object(m.getpass,'getpass',fallback),patch.object(m,'probe') as probe,contextlib.redirect_stdout(io.StringIO()) as output:
   self.assertEqual(m.main(),1);probe.assert_not_called()
   self.assertIn('GetPassWarning',output.getvalue())
 def test_oauth_http_failure_never_exposes_body_or_secret(self):
  def failed(url,**kwargs):return 401,b'fixture-private-secret fixture-access-token'
  with self.assertRaises(m.PreparationError) as failure:m.probe('fixture-private-secret',failed)
  self.assertEqual(str(failure.exception),'PRODUCTION_OAUTH_NOT_200')
  self.assertNotIn('fixture-',str(failure.exception))
  self.assertEqual(failure.exception.diagnostic['oauth_http_status'],401)
  self.assertIsNone(failure.exception.diagnostic['oauth_error_description'])
 def test_oauth_error_allowlist_description_redacts_secret_tokens_and_unknown_fields(self):
  secret='fixture-input-private-secret';token='eyJhbGciOiJIUzI1NiJ9.fixtureAccessToken12345678901234567890.fixtureSignature1234567890'
  raw=json.dumps({'error':'invalid_client','error_description':'Client authentication failed '+secret+' access_token='+token+' Bearer short-private-value',
                  'private_body':'must-never-appear','access_token':token,'headers':{'private':'must-never-appear'}}).encode()
  with self.assertRaises(m.OAuthProbeError) as caught:m.probe(secret,FakeHttp(oauth_status=401,oauth_error=raw))
  diagnostic=caught.exception.diagnostic;serialized=json.dumps(diagnostic)
  self.assertEqual(diagnostic['oauth_error'],'invalid_client');self.assertEqual(diagnostic['oauth_requested_scopes'],m.SCOPES.split())
  self.assertIn('Client authentication failed',serialized)
  for private in (secret,token,'short-private-value','must-never-appear','fixtureAccessToken'):self.assertNotIn(private,serialized)
 def test_oauth_error_description_blocks_html_and_control_characters(self):
  for description in ('<html>private hidden content</html>','&lt;script&gt;private&lt;/script&gt;','private\x00hidden'):
   with self.subTest(description=repr(description)):
    diagnostic=m.oauth_diagnostic(403,json.dumps({'error':'invalid_scope','error_description':description}).encode(),'fixture-secret')
    self.assertEqual(diagnostic['oauth_error'],'invalid_scope')
    self.assertEqual(diagnostic['oauth_error_description'],'REDACTED_UNTRUSTED_ERROR_DESCRIPTION')
 def test_short_jwt_quoted_token_fields_encoded_secret_and_long_opaque_redacted(self):
  secret='fixture private/+ secret'
  description='Client authentication failed {"access_token":"shorttoken"} refresh_token=short-refresh eyJhbGci.eyJpc3Mi.c2lnbmF0dXJl '+m.urllib.parse.quote_plus(secret)+' AbCdEfGhIjKlMnOpQrStUvWx'
  diagnostic=m.oauth_diagnostic(401,json.dumps({'error':'invalid_client','error_description':description}).encode(),secret)
  value=diagnostic['oauth_error_description']
  self.assertIn('Client authentication failed',value)
  for private in ('shorttoken','short-refresh','eyJhbGci','c2lnbmF0dXJl',m.urllib.parse.quote_plus(secret),'AbCdEfGhIjKlMnOpQrStUvWx'):self.assertNotIn(private,value)
 def test_network_oauth_failure_produces_safe_diagnostic_without_arbitrary_exception(self):
  def failed(url,**kwargs):raise m.PreparationError('private-network-error-never-output')
  with self.assertRaises(m.OAuthProbeError) as caught:m.probe('fixture-secret',failed)
  self.assertEqual(str(caught.exception),'UBER_HTTP_REQUEST_FAILED');self.assertIsNone(caught.exception.diagnostic['oauth_http_status'])
  self.assertTrue(caught.exception.diagnostic['oauth_request_attempted']);self.assertNotIn('private-network-error',json.dumps(caught.exception.diagnostic))
 def test_http_error_retains_only_bounded_json_in_memory_and_closes_response(self):
  raw=b'{"error":"invalid_scope","error_description":"Requested scope is unavailable"}'
  body=io.BytesIO(raw)
  error=m.urllib.error.HTTPError(m.OAUTH,401,'private-error-reason',{'private-header':'never-output'},body)
  def rejected(*args,**kwargs):raise error
  with patch.object(m.urllib.request,'build_opener',return_value=types.SimpleNamespace(open=rejected)):
   status,result=m.request(m.OAUTH,raw=b'fixture-form-data')
  self.assertEqual(status,401);self.assertEqual(result,raw);self.assertTrue(body.closed)
  for body_raw in (b'<html>private</html>',b'[]',b'{"error":"'+b'x'*m.ERROR_JSON_LIMIT+b'"}'):
   self.assertIsNone(m.error_json(io.BytesIO(body_raw)))
 def test_v37_guard_does_not_silently_skip(self):
  value=FakeOps(Path('/tmp'),self.base()).inventory(m.DB);value['flyway']='37'
  with self.assertRaisesRegex(PromotionError,'FLYWAY_V38_REQUIRED'):m.queue_guard(FakeOps(Path('/tmp'),self.base()),value)
 def setup_configure(self,directory):
  base=Path(directory)/'base';(base/'production').mkdir(parents=True)
  env=self.base();model={'services':{'backend':{'image':m.IMAGE,'environment':env},'nginx':{'image':'fixture-nginx'}}}
  source=base/'production/current.compose.json';source.write_text(json.dumps(model));source.chmod(0o600)
  ops=FakeOps(base,env)
  safety={'bindings':[{'id':1,'store':1,'org':1,'env':'production','uber':m.STORE,'mode':'KITCHEN_MIRROR','enabled':True}],'modules':'fixed','printing':'fixed','assignments':'fixed','printers':'fixed','mappings':'fixed'}
  verify=types.SimpleNamespace(psql=lambda db,sql:copy.deepcopy(safety))
  def backup(args,stdout,**kwargs):stdout.write(b'fixture-custom-dump');return types.SimpleNamespace(returncode=0)
  return ops,verify,source,backup
 def set_existing_secret(self,ops,source,secret):
  ops.current['UBER_EATS_CLIENT_SECRET']=secret
  model=json.loads(source.read_text());model['services']['backend']['environment']=copy.deepcopy(ops.current)
  source.write_text(json.dumps(model))
 def test_existing_consistent_secret_reused_without_tty_or_prompt(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td);secret='fixture-existing-private-secret';self.set_existing_secret(ops,source,secret)
   with patch.object(m,'load',return_value=ops),patch.object(m.sys,'stdin',types.SimpleNamespace(isatty=lambda:False)),patch.object(m.getpass,'getpass') as hidden,patch.object(m,'probe',return_value=({}, {}, None)) as probe,patch.object(m,'configure',return_value={'oauth_http_status':200}) as configure,contextlib.redirect_stdout(io.StringIO()) as output:
    self.assertEqual(m.main(),0)
   hidden.assert_not_called();probe.assert_called_once_with(secret);self.assertEqual(configure.call_args.args[0],secret)
   self.assertNotIn(secret,output.getvalue());self.assertEqual(ops.up,[])
 def test_missing_consistent_secret_prompts_hidden_exactly_once(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td)
   with patch.object(m,'load',return_value=ops),patch.object(m.sys,'stdin',types.SimpleNamespace(isatty=lambda:True)),patch.object(m.getpass,'getpass',return_value='fixture-hidden-private-secret') as hidden,patch.object(m,'probe',return_value=({}, {}, None)) as probe,patch.object(m,'configure',return_value={'oauth_http_status':200}),contextlib.redirect_stdout(io.StringIO()) as output:
    self.assertEqual(m.main(),0)
   hidden.assert_called_once();probe.assert_called_once_with('fixture-hidden-private-secret')
   self.assertNotIn('fixture-hidden-private-secret',output.getvalue())
 def test_runtime_canonical_secret_conflict_fails_before_prompt_or_oauth(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td);self.set_existing_secret(ops,source,'fixture-runtime-private-secret')
   model=json.loads(source.read_text());model['services']['backend']['environment']['UBER_EATS_CLIENT_SECRET']='fixture-stale-private-secret';source.write_text(json.dumps(model));original=source.read_bytes()
   with patch.object(m,'load',return_value=ops),patch.object(m.getpass,'getpass') as hidden,patch.object(m,'probe') as probe,patch.object(m,'configure') as configure,contextlib.redirect_stdout(io.StringIO()) as output:
    self.assertEqual(m.main(),1)
   hidden.assert_not_called();probe.assert_not_called();configure.assert_not_called()
   self.assertIn('CANONICAL_MODEL_RUNTIME_DRIFT',output.getvalue());self.assertNotIn('fixture-',output.getvalue())
   self.assertEqual(source.read_bytes(),original);self.assertEqual(ops.up,[])
 def test_oauth_failure_persists_only_safe_proof_without_configuration_or_deploy(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td);secret='fixture-existing-private-secret';self.set_existing_secret(ops,source,secret)
   original=source.read_bytes();runtime=copy.deepcopy(ops.current);root=Path(td)/'operation';original_probe=m.probe
   http=FakeHttp(oauth_status=401,oauth_error=json.dumps({'error':'invalid_client','error_description':'Client authentication failed '+secret+' opaqueToken12345678901234567890','debug':'raw-body-private'}).encode())
   with patch.object(m,'ROOT',root),patch.object(m,'load',return_value=ops),patch.object(m.getpass,'getpass') as hidden,patch.object(m,'probe',side_effect=lambda value:original_probe(value,http)),patch.object(m,'configure') as configure,contextlib.redirect_stdout(io.StringIO()) as output:
    self.assertEqual(m.main(),1)
   proof_path=root.with_name(root.name+'-oauth-failure.json');proof=json.loads(proof_path.read_text());serialized=proof_path.read_text()+output.getvalue()
   self.assertEqual(proof['oauth_http_status'],401);self.assertEqual(proof['oauth_error'],'invalid_client')
   self.assertEqual(proof['oauth_requested_scopes'],m.SCOPES.split());self.assertTrue(proof['oauth_request_attempted'])
   self.assertFalse(proof['client_secret_written']);self.assertFalse(proof['deployment_attempted'])
   self.assertEqual(proof_path.stat().st_mode & 0o777,0o600);self.assertFalse(root.exists())
   for private in (secret,'opaqueToken12345678901234567890','raw-body-private'):self.assertNotIn(private,serialized)
   hidden.assert_not_called();configure.assert_not_called();self.assertEqual([c[0] for c in http.calls],[m.OAUTH])
   self.assertEqual(source.read_bytes(),original);self.assertEqual(ops.current,runtime);self.assertEqual(ops.up,[])
 def test_signing_key_input_is_rejected_before_oauth_with_safe_failure_proof(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td);root=Path(td)/'operation';key=ops.current['UBER_EATS_WEBHOOK_SIGNING_KEY'];original=source.read_bytes()
   with patch.object(m,'ROOT',root),patch.object(m,'load',return_value=ops),patch.object(m.sys,'stdin',types.SimpleNamespace(isatty=lambda:True)),patch.object(m.getpass,'getpass',return_value=key) as hidden,patch.object(m,'probe') as probe,patch.object(m,'configure') as configure,contextlib.redirect_stdout(io.StringIO()) as output:
    self.assertEqual(m.main(),1)
   proof_path=root.with_name(root.name+'-oauth-failure.json');proof=json.loads(proof_path.read_text())
   self.assertEqual(proof['error_code'],'OAUTH_INPUT_IS_WEBHOOK_SIGNING_KEY');self.assertFalse(proof['oauth_request_attempted']);self.assertIsNone(proof['oauth_http_status'])
   hidden.assert_called_once();probe.assert_not_called();configure.assert_not_called()
   self.assertNotIn(key,proof_path.read_text()+output.getvalue());self.assertEqual(source.read_bytes(),original);self.assertEqual(ops.up,[])
 def test_same_image_configuration_with_pending_store_access(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td);root=Path(td)/'operation'
   with patch.object(m,'ROOT',root),patch.object(m.subprocess,'run',backup):
    result=m.configure('fixture-new-secret',{'oauth_http_status':200,'menu_http_status':403},{'http_status':403},None,ops,verify,types.SimpleNamespace(accept=lambda env:{'valid_signature':200}))
   self.assertTrue(result['client_secret_configured']);self.assertEqual(ops.current['UBER_EATS_CLIENT_SECRET'],'fixture-new-secret')
   self.assertEqual(ops.current['UBER_EATS_WEBHOOK_SIGNING_KEY'],'fixture-original-primary-key');self.assertEqual(ops.up,['target.private.json'])
   self.assertEqual(json.loads(source.read_text())['services']['backend']['image'],m.IMAGE)
 def test_source_cas_drift_stops_before_backend_restart(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td)
   def drift(args,stdout,**kwargs):
    result=backup(args,stdout,**kwargs);source.write_text('foreign canonical content');return result
   with patch.object(m,'ROOT',Path(td)/'operation'),patch.object(m.subprocess,'run',drift):
    with self.assertRaisesRegex(PromotionError,'CANONICAL_SOURCE_CHANGED'):
     m.configure('fixture-secret',{}, {'http_status':403},None,ops,verify,types.SimpleNamespace(accept=lambda env:{}))
   self.assertEqual(ops.up,[]);self.assertEqual(ops.current,ops.original)
   self.assertEqual(source.read_text(),'foreign canonical content')
 def test_post_recreate_external_canonical_drift_stops_without_second_recreate(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td)
   def drift(env):
    source.write_text('foreign same-image canonical change');return {'valid_signature':200}
   with patch.object(m,'ROOT',Path(td)/'operation'),patch.object(m.subprocess,'run',backup):
    with self.assertRaisesRegex(PromotionError,'CANONICAL_DRIFT_REQUIRES_OPERATOR_NO_RECREATE'):
     m.configure('fixture-new-secret',{}, {'http_status':403},None,ops,verify,types.SimpleNamespace(accept=drift))
   self.assertEqual(ops.up,['target.private.json'])
   self.assertEqual(source.read_text(),'foreign same-image canonical change')
   self.assertEqual(ops.current['UBER_EATS_CLIENT_SECRET'],'fixture-new-secret')
 def test_system_exit_rolls_back_exact_original_receiver_env(self):
  with tempfile.TemporaryDirectory() as td:
   ops,verify,source,backup=self.setup_configure(td);original=source.read_bytes()
   def fail(env):raise SystemExit('do not emit fixture-secret')
   with patch.object(m,'ROOT',Path(td)/'operation'),patch.object(m.subprocess,'run',backup):
    with self.assertRaisesRegex(PromotionError,'ORIGINAL_RECEIVE_ONLY_RESTORED'):
     m.configure('fixture-new-secret',{}, {'http_status':403},None,ops,verify,types.SimpleNamespace(accept=fail))
   self.assertEqual(ops.up,['target.private.json','rollback.private.json']);self.assertEqual(ops.current,ops.original);self.assertEqual(source.read_bytes(),original)
   self.assertEqual(ops.current['UBER_EATS_ENABLED'],'true');self.assertEqual(ops.current['UBER_EATS_WORKER_ENABLED'],'false')

if __name__=='__main__':unittest.main(verbosity=2)
