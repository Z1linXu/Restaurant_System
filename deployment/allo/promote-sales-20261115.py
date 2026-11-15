#!/usr/bin/env python3
"""Bounded adaptation of reviewed Allô stage/activate helpers, for this release only.
Run as root on VM-0-6-ubuntu: SCRIPT FULL_SHA stage|activate.
Uses existing Compose and images; never operates on another project or prunes Docker.
"""
import copy,datetime,hashlib,json,os,pathlib,re,socket,subprocess,sys,time
assert os.geteuid()==0 and socket.gethostname()=='VM-0-6-ubuntu'
os.umask(0o077)
SHA,ACTION=sys.argv[1:];assert re.fullmatch('[0-9a-f]{40}',SHA) and ACTION in ('stage','activate')
ROOT=pathlib.Path('/srv/allo-nouilles');R=ROOT/'releases'/SHA
run=lambda a,**kw:subprocess.run(a,check=True,capture_output=True,**kw)
def text(a):return run(a).stdout.decode().strip()
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(p,v):p.write_text(json.dumps(v,indent=2,ensure_ascii=False,default=str)+'\n');p.chmod(0o600)
def sql(q,write=False):
 a=['docker','exec','-i']
 if not write:a+=['-e','PGOPTIONS=-c default_transaction_read_only=on']
 a+=['allo-nouilles-db-1','psql','-XqAt','-v','ON_ERROR_STOP=1','-U','allo_test','-d','restaurant_allo_nouilles_test']
 return run(a,input=q.encode()).stdout.decode().strip()
def containers():
 cs=json.loads(run(['docker','inspect','allo-nouilles-backend-1','allo-nouilles-frontend-1','allo-nouilles-db-1']).stdout)
 assert all(c['Config']['Labels']['com.docker.compose.project']=='allo-nouilles' for c in cs)
 assert cs[2]['Image']=='sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28'
 assert any(m['Source']=='/srv/allo-nouilles/state/postgres' and m['Destination']=='/var/lib/postgresql/data' for m in cs[2]['Mounts'])
 return cs
def compose(env,compose_file):return json.loads(run(['docker','compose','--project-name','allo-nouilles','--env-file',str(env),'-f',str(compose_file),'config','--format','json']).stdout)
def disk():return {'df':text(['df','-h','/']),'docker_system_df':text(['docker','system','df'])}
def identity(version):
 assert json.loads(sql("select json_build_object('n',count(*),'valid',bool_and(id=1 and organization_id=1 and code='ALLO_NOUILLES_TEST' and timezone='America/Toronto' and printing_mode='PAD_DIRECT')) from stores"))=={'n':1,'valid':True}
 assert sql('select max(version::int) from flyway_schema_history where success and version is not null')==str(version)
 assert sql('select count(*) from flyway_schema_history where not success')=='0'
def idle():return sql("select count(*) from print_jobs where status in ('CLAIMED','PRINTING')")=='0'
def stop_backend():
 for _ in range(15):
  if idle():break
  time.sleep(1)
 assert idle(),'Wait for the existing physical attempt; never reset or reprint it'
 run(['docker','stop','--time','30','allo-nouilles-backend-1'])
 if not idle():
  run(['docker','start','allo-nouilles-backend-1']);raise RuntimeError('Claim raced with stop; original container restarted, no repair applied')
def health():
 for _ in range(60):
  cs=containers()
  if all(c['State'].get('Health',{}).get('Status')=='healthy' for c in cs):return cs
  if not cs[0]['State']['Running']:break
  time.sleep(1)
 raise RuntimeError('Health not ready; preserve new data/schema and use the documented forward recovery')
M=json.loads((R/'manifest.json').read_text());assert M['source_git']==SHA and M['flyway_target']==44
for name,h in M['files'].items():
 assert not pathlib.PurePosixPath(name).is_absolute() and '..' not in pathlib.PurePosixPath(name).parts
 assert digest(R/name)==h,name
cs=containers()
if ACTION=='stage':
 identity(43);assert all(c['State'].get('Health',{}).get('Status')=='healthy' for c in cs)
 assert cs[0]['Image']=='sha256:7141585f9b545a69afe865a38a7577fca166bda5ab3cf7a9f3a7a9925c884804'
 assert cs[1]['Image']=='sha256:1e8c3dbe54e03b3567c76c5fcccc055cb52328fed6800d77d360dfad4a5b78a2'
 B=R/'rollback-baseline';B.mkdir(mode=0o700)
 for p,name in [('compose.yml','compose.yml'),('private/runtime.env','runtime.env'),('config/nginx.conf','nginx.conf')]:
  dst=B/name;assert not dst.exists();dst.write_bytes((ROOT/p).read_bytes());dst.chmod(0o600)
 base={'containers':cs,'sha256':{p:digest(ROOT/p) for p in ('compose.yml','private/runtime.env','config/nginx.conf')},'disk_before':disk()}
 save(B/'baseline.json',base)
 old=compose(ROOT/'private/runtime.env',ROOT/'compose.yml');new_values={'BACKEND_IMAGE':'allo-nouilles-backend:'+SHA,'FRONTEND_IMAGE':'allo-nouilles-frontend:'+SHA,'APP_BUILD_SHA':SHA}
 lines=(ROOT/'private/runtime.env').read_text().splitlines();seen={k:0 for k in new_values};out=[]
 for line in lines:
  key=line.split('=',1)[0]
  if key in new_values:seen[key]+=1;line=key+'='+new_values[key]
  out.append(line)
 assert all(v==1 for v in seen.values());(R/'candidate-runtime.env').write_text('\n'.join(out)+'\n')
 source=(ROOT/'compose.yml').read_text();assert source.count("SPRING_FLYWAY_TARGET: '43'")==1
 (R/'candidate-compose.yml').write_text(source.replace("SPRING_FLYWAY_TARGET: '43'","SPRING_FLYWAY_TARGET: '44'"))
 new=compose(R/'candidate-runtime.env',R/'candidate-compose.yml')
 assert old['services']['db']==new['services']['db']
 for service in ('backend','frontend'):
  a=copy.deepcopy(old['services'][service]);b=copy.deepcopy(new['services'][service]);a['image']=b['image']
  if service=='backend':a['environment']['APP_BUILD_SHA']=SHA;a['environment']['SPRING_FLYWAY_TARGET']='44'
  assert a==b,(service,'unexpected configuration change')
 images={}
 for service in ('backend','frontend'):
  image=new_values[service.upper()+'_IMAGE'];base_image=M['base_'+service+'_image']
  run(['docker','image','inspect',base_image])
  p=run(['docker','build','--network=none','--pull=false','--build-arg','BASE_IMAGE='+base_image,'--build-arg','RELEASE_VERSION='+SHA,'--build-arg','SOURCE_SHA='+SHA,'--build-arg','PATCH_SHA256='+M['files']['compatibility.patch'],'-f','Dockerfile.'+service,'-t',image,'.'],cwd=R)
  (R/('build-'+service+'.log')).write_bytes(p.stdout+p.stderr)
  v=json.loads(run(['docker','image','inspect',image]).stdout)[0];assert v['Config']['Labels']['org.opencontainers.image.revision']==SHA
  images[service]={'reference':image,'id':v['Id']}
 save(R/'staged-images.json',{'images':images,'old_ids':[c['Id'] for c in cs],'candidate_env':digest(R/'candidate-runtime.env'),'candidate_compose':digest(R/'candidate-compose.yml'),'disk':disk()})
 print(json.dumps({'staged':SHA,'runtime_mutated':False}));sys.exit()
S=json.loads((R/'staged-images.json').read_text());B=json.loads((R/'rollback-baseline/baseline.json').read_text())
assert [c['Id'] for c in cs]==S['old_ids']
for p,h in B['sha256'].items():assert digest(ROOT/p)==h,p
assert digest(R/'candidate-runtime.env')==S['candidate_env'] and digest(R/'candidate-compose.yml')==S['candidate_compose']
for service,v in S['images'].items():assert json.loads(run(['docker','image','inspect',v['reference']]).stdout)[0]['Id']==v['id']
backup=json.loads((ROOT/'backups/sales-printing-20261115/latest.json').read_text());proof=json.loads((R/'recovery-verification.json').read_text())
assert backup['dump_sha256']==proof['production_dump_sha256'] and digest(pathlib.Path(backup['file']))==backup['dump_sha256']
assert proof['tables_restored_and_hash_matched']==len(backup['tables'])==87 and proof['constraints_indexes_valid'] and proof['socket_only'] and proof['recovery_only_cluster_removed']
assert (datetime.datetime.now(datetime.timezone.utc)-datetime.datetime.fromisoformat(backup['at_utc'])).total_seconds()<3600
identity(43);stop_backend()
# Apply the exact image/config through the existing Compose path; database service is never recreated.
(ROOT/'private/runtime.env').write_bytes((R/'candidate-runtime.env').read_bytes());(ROOT/'compose.yml').write_bytes((R/'candidate-compose.yml').read_bytes())
cmd=['docker','compose','--project-name','allo-nouilles','--env-file',str(ROOT/'private/runtime.env'),'-f',str(ROOT/'compose.yml')]
run(cmd+['up','-d','--no-deps','--force-recreate','backend']);health();identity(44)
# A second bounded stop is necessary for the Owner-authorized data repair after Flyway.
# The compatible old web stays in place until the repair has committed and backend is healthy.
stop_backend()
try:
 snapshot={}
 for name,q in {
  'takeout':"select coalesce(json_agg(o order by id),'[]') from orders o where store_id=1 and order_type in ('pickup','takeout') and status in ('submitted','preparing','ready','picked_up') and financial_mode='IN_STORE' and external_source is null and submitted_at is not null",
  'spice_groups':"select coalesce(json_agg(g order by id),'[]') from template_item_option_groups g where store_id=1 and kind='SPICY_LEVEL'",
  'spice_options':"select coalesce(json_agg(o order by id),'[]') from menu_item_options o where option_group='SPICY_LEVEL' and menu_item_id in(select id from menu_items where store_id=1)",
  'store':"select row_to_json(s) from stores s where id=1"}.items():snapshot[name]=json.loads(sql(q))
 save(R/'pre-repair-targets.json',snapshot)
 result=sql((R/'repair-sales-20261115.sql').read_text(),write=True)
 (R/'data-repair-result.txt').write_text(result+'\n')
 # Idempotency replay is part of acceptance; it must report zero additional rows/config changes.
 replay=sql((R/'repair-sales-20261115.sql').read_text(),write=True)
 (R/'data-repair-replay.txt').write_text(replay+'\n')
 records=[];remaining=replay.strip();decoder=json.JSONDecoder()
 while remaining:
  record,end=decoder.raw_decode(remaining);records.append(record);remaining=remaining[end:].strip()
 assert len(records)==2 and records[0]['takeout_repaired']==0 and records[1]['spice_groups_retired']==0
finally:
 run(['docker','start','allo-nouilles-backend-1'])
health();run(cmd+['up','-d','--no-deps','--force-recreate','frontend']);after=health();identity(44)
assert after[2]['Id']==B['containers'][2]['Id'] and after[2]['State']['StartedAt']==B['containers'][2]['State']['StartedAt']
assert digest(ROOT/'config/nginx.conf')==B['sha256']['config/nginx.conf']
for c,service in zip(after[:2],('backend','frontend')):assert c['Image']==S['images'][service]['id']
save(R/'ACTIVATED.json',{'sha':SHA,'at_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'containers':[{'name':c['Name'],'id':c['Id'],'image':c['Image'],'started_at':c['State']['StartedAt']} for c in after],'flyway':44,'repair_targets':len(snapshot['takeout']),'disk_after':disk(),'db_container_unchanged':True,'nginx_config_unchanged':True})
print(json.dumps({'activated':SHA,'flyway':44,'repair_targets':len(snapshot['takeout']),'db_unchanged':True}))
