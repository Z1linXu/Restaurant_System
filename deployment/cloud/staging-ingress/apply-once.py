#!/usr/bin/env python3
"""One-off server execution for the reviewed 2026-09-30 ingress batch."""
import datetime,fcntl,hashlib,json,os,pathlib,subprocess,sys,time,urllib.request
ROOT=pathlib.Path('/srv/restaurant-pos/staging/ingress-20260930')
BACKUP=ROOT/'backup'
PROD=['cloud-nginx-1','cloud-backend-1','cloud-db-1']
STAGE=['restaurant-pos-staging-nginx-1','restaurant-pos-staging-backend-1','restaurant-pos-staging-db-1']
PIM=['sha256:d82650726a7faec62f1270a98bc7e878f0060392d5b6d62fa75ee03bf6e0728f','sha256:603f79e0272a3fe83fdd7ac9bb58b72dc2b1facb3ceef720bc636bf5b9c5469d','sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777']
SIMG={'backend':'sha256:23f332075f0142f2aeb941da62d58212cd595b1e393a69adb609b4869f18d870','nginx':'sha256:a146f2b927e29afd46d6a1355712052f58db3c98ab3346f9df72a59fab81d3cf'}
TEMPLATE=pathlib.Path('/home/ubuntu/Restaurant_System/deployment/cloud/data/nginx/default.conf.template')
def run(args,**kw): return subprocess.check_output(args,**kw)
def inspect(name): return json.loads(run(['docker','inspect',name]))[0]
def fingerprint(name):
 d=inspect(name)
 return dict(id=d['Id'],started=d['State']['StartedAt'],restarts=d['RestartCount'],image=d['Image'],networks=d['NetworkSettings']['Networks'],ports=d['HostConfig']['PortBindings'],env_sha256=hashlib.sha256(json.dumps(d['Config']['Env']).encode()).hexdigest())
def ledger(name):
 return run(['docker','exec',name,'sh','-c','PGOPTIONS="-c default_transaction_read_only=on -c statement_timeout=5000" psql -X -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "SELECT version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank"']).decode()
def health(base):
 with urllib.request.urlopen(base+'/',timeout=8) as r: assert r.status==200
 with urllib.request.urlopen(base+'/api/v1/system/health',timeout=8) as r: assert r.status==200 and json.load(r)['data']['status']=='UP'
def continuity():
 b=json.loads((BACKUP/'baseline.json').read_text())
 for n in PROD: assert fingerprint(n)==b['fingerprints'][n], 'Production fingerprint changed: '+n
 assert ledger('cloud-db-1')==b['production_flyway'], 'Production Flyway changed'
 health('http://127.0.0.1')
 print('PRODUCTION_CONTINUITY=PASS',flush=True)
def write_json(path,value):
 path.write_text(json.dumps(value,indent=2)+'\n');path.chmod(0o600)
def escape_compose(value):
 if isinstance(value,str):return value.replace('$','$$')
 if isinstance(value,list):return [escape_compose(v) for v in value]
 if isinstance(value,dict):return {k:escape_compose(v) for k,v in value.items()}
 return value
def write_compose(path,value):write_json(path,escape_compose(value))
def wait_health():
 for attempt in range(45):
  try:health('http://127.0.0.1:18080');return
  except Exception:time.sleep(2)
 raise RuntimeError('Staging health timeout')
def verify_staging(original=False):
 wait_health()
 b=json.loads((BACKUP/'baseline.json').read_text())
 assert ledger('restaurant-pos-staging-db-1')==b['staging_flyway']
 assert fingerprint('restaurant-pos-staging-db-1')==b['fingerprints']['restaurant-pos-staging-db-1']
 for service in ['backend','nginx']:
  d=inspect('restaurant-pos-staging-'+service+'-1');assert d['Image']==SIMG[service]
  assert d['NetworkSettings']['Networks']['restaurant-pos-staging_restaurant-pos']['IPAddress']==('172.19.0.3' if service=='backend' else '172.19.0.4')
 continuity()
def compose(path,*args): return run(['docker','compose','-p','restaurant-pos-staging','-f',str(path),*args],stderr=subprocess.STDOUT)
def model(service,d):
 c,h=d['Config'],d['HostConfig']; assert d['Image']==SIMG[service]
 assert list(d['NetworkSettings']['Networks'])==['restaurant-pos-staging_restaurant-pos']
 assert not h['Privileged'] and not h['CapAdd'] and not h['Devices'] and not h['NetworkMode']=='host'
 assert not d['Mounts'] if service=='backend' else len(d['Mounts'])==1
 s={'image':d['Image'],'environment':dict(x.split('=',1) for x in c['Env']),
    'entrypoint':c['Entrypoint'],'command':c['Cmd'],'restart':h['RestartPolicy']['Name'],
    'networks':{'restaurant-pos':{'ipv4_address':d['NetworkSettings']['Networks']['restaurant-pos-staging_restaurant-pos']['IPAddress']}},
    'mem_limit':h['Memory'],'memswap_limit':h['MemorySwap'],'cpus':h['NanoCpus']/1e9,
    'logging':{'driver':h['LogConfig']['Type'],'options':h['LogConfig']['Config']}}
 if c['WorkingDir']:s['working_dir']=c['WorkingDir']
 if c['User']:s['user']=c['User']
 if h['SecurityOpt']:s['security_opt']=h['SecurityOpt']
 if h['CapDrop']:s['cap_drop']=h['CapDrop']
 assert not c.get('Healthcheck')
 assert not c.get('StopTimeout')
 if c.get('StopSignal'):s['stop_signal']=c['StopSignal']
 s['labels']={k:v for k,v in c.get('Labels',{}).items() if not k.startswith('com.docker.compose.')}
 for k in ['ReadonlyRootfs','Init','PidsLimit','Ulimits','CpuQuota','CpuPeriod','CpuShares','CpusetCpus','Dns','DnsSearch','DnsOptions','ExtraHosts','Sysctls','Mounts']:
  assert not h.get(k),'Unexpected nondefault '+k
 assert h['ShmSize']==67108864 and h['RestartPolicy']['MaximumRetryCount']==0
 for mount in d['Mounts']:assert mount['Propagation']=='rprivate'
 if service=='nginx':
  assert h['PortBindings']=={'80/tcp':[{'HostIp':'127.0.0.1','HostPort':'18080'}]}
  s['ports']=['127.0.0.1:18080:80']
  s['volumes']=[{'type':'bind','source':x['Source'],'target':x['Destination'],'read_only':not x['RW']} for x in d['Mounts']]
 else:assert not h['PortBindings']
 return s

def prepare():
 assert not BACKUP.exists(),'Backup already exists; do not overwrite'
 BACKUP.mkdir(mode=0o700)
 ds={n:inspect(n) for n in PROD+STAGE}
 for n,i in zip(PROD,PIM):assert ds[n]['Image']==i
 assert ds[PROD[0]]['NetworkSettings']['Networks']['cloud_restaurant-pos']['IPAddress']=='172.18.0.4'
 assert ds[STAGE[0]]['NetworkSettings']['Networks']['restaurant-pos-staging_restaurant-pos']['IPAddress']=='172.19.0.4'
 assert ds[STAGE[1]]['NetworkSettings']['Networks']['restaurant-pos-staging_restaurant-pos']['IPAddress']=='172.19.0.3'
 p,s=ledger('cloud-db-1'),ledger('restaurant-pos-staging-db-1')
 assert p.splitlines()[-1].startswith('28|') and s.splitlines()[-1].startswith('30|')
 b={'time':datetime.datetime.now(datetime.timezone.utc).isoformat(),'fingerprints':{n:fingerprint(n) for n in PROD+STAGE},'production_flyway':p,'staging_flyway':s}
 write_json(BACKUP/'baseline.json',b)
 write_json(BACKUP/'inspect.private.json',ds)
 (BACKUP/'edge.template').write_bytes(TEMPLATE.read_bytes())
 for n in [PROD[0],STAGE[0]]:
  (BACKUP/(n+'.effective.conf')).write_bytes(run(['docker','exec',n,'cat','/etc/nginx/conf.d/default.conf']))
 base={'services':{s:model(s,ds['restaurant-pos-staging-'+s+'-1']) for s in ['backend','nginx']},'networks':{'restaurant-pos':{'external':True,'name':'restaurant-pos-staging_restaurant-pos'}}}
 write_compose(BACKUP/'rollback.private.json',base)
 original_mount=pathlib.Path(ds[STAGE[0]]['Mounts'][0]['Source'])
 (BACKUP/'staging.template').write_bytes(original_mount.read_bytes())
 target=json.loads(json.dumps(base));target['services']['backend']['environment'].update(json.loads((ROOT/'backend-environment.json').read_text()))
 target['services']['nginx']['volumes'][0]['source']=str(ROOT/'staging.conf.template')
 write_compose(ROOT/'target.private.json',target)
 for path,expected in [(ROOT/'target.private.json',target),(BACKUP/'rollback.private.json',base)]:
  resolved=json.loads(compose(path,'config','--format','json'))
  assert set(resolved['services'])=={'backend','nginx'}
  for service,wanted in expected['services'].items():
   actual=resolved['services'][service]
   for key in ['image','environment','entrypoint','command','logging','mem_limit','memswap_limit','restart','labels','networks']:
    assert actual[key]==wanted[key],'Resolved '+service+' '+key+' differs'
   assert float(actual['cpus'])==wanted['cpus']
   if service=='backend':assert not actual.get('ports') and not actual.get('volumes')
   else:
    assert len(actual['ports'])==1 and actual['ports'][0]['host_ip']=='127.0.0.1' and str(actual['ports'][0]['published'])=='18080' and actual['ports'][0]['target']==80
    assert len(actual['volumes'])==1 and all(actual['volumes'][0][k]==wanted['volumes'][0][k] for k in ['type','source','target','read_only'])
 print('RESOLVED_COMPOSE_MATCH=PASS; delta=6 backend forwarding variables + nginx template source only')
 (BACKUP/'SHA256SUMS').write_text(''.join(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+f.name+'\n' for f in sorted(BACKUP.iterdir()) if f.is_file()))
 continuity()
 print('PREPARED backup='+str(BACKUP))

def stage():
 continuity()
 try:
  print(compose(ROOT/'target.private.json','up','-d','--no-build','--pull','never','--no-deps','backend').decode())
  wait_health()
  print(compose(ROOT/'target.private.json','up','-d','--no-build','--pull','never','--no-deps','nginx').decode())
  wait_health()
  assert inspect(STAGE[0])['NetworkSettings']['Networks']['restaurant-pos-staging_restaurant-pos']['IPAddress']=='172.19.0.4'
  assert inspect(STAGE[1])['NetworkSettings']['Networks']['restaurant-pos-staging_restaurant-pos']['IPAddress']=='172.19.0.3'
  b=json.loads((BACKUP/'baseline.json').read_text());assert fingerprint('restaurant-pos-staging-db-1')==b['fingerprints']['restaurant-pos-staging-db-1']
  assert ledger('restaurant-pos-staging-db-1')==b['staging_flyway']
  continuity()
 except BaseException:
  print(compose(BACKUP/'rollback.private.json','up','-d','--no-build','--pull','never','--no-deps','backend','nginx').decode())
  verify_staging(original=True)
  raise

def edge(level):
 continuity()
 original=(BACKUP/'cloud-nginx-1.effective.conf').read_bytes();template=(BACKUP/'edge.template').read_bytes()
 extra=(ROOT/'edge-http.conf').read_bytes()
 if level=='tls':extra+=b'\n'+(ROOT/'edge-https.conf').read_bytes()
 candidate=original+b'\n'+extra
 # Validate isolated candidate using existing container's main configuration.
 main=run(['docker','exec',PROD[0],'cat','/etc/nginx/nginx.conf']).replace(b'/etc/nginx/conf.d/*.conf',b'/tmp/staging-edge-candidate.conf')
 run(['docker','exec','-i',PROD[0],'sh','-c','cat > /tmp/staging-edge-main.conf'],input=main)
 run(['docker','exec','-i',PROD[0],'sh','-c','cat > /tmp/staging-edge-candidate.conf'],input=candidate)
 run(['docker','exec',PROD[0],'nginx','-t','-c','/tmp/staging-edge-main.conf'],stderr=subprocess.STDOUT)
 prev_template=TEMPLATE.read_bytes();prev_effective=run(['docker','exec',PROD[0],'cat','/etc/nginx/conf.d/default.conf'])
 checkpoint=BACKUP/('before-edge-'+level);checkpoint.mkdir(mode=0o700)
 (checkpoint/'template').write_bytes(prev_template);(checkpoint/'effective').write_bytes(prev_effective)
 (checkpoint/'SHA256SUMS').write_text(''.join(hashlib.sha256(v).hexdigest()+'  '+k+'\n' for k,v in [('template',prev_template),('effective',prev_effective)]))
 try:
  TEMPLATE.write_bytes(template+b'\n'+extra) # retain bind-mounted inode
  run(['docker','exec','-i',PROD[0],'sh','-c','cat > /etc/nginx/conf.d/default.conf'],input=candidate)
  run(['docker','exec',PROD[0],'nginx','-t'],stderr=subprocess.STDOUT)
  run(['docker','exec',PROD[0],'nginx','-s','reload'],stderr=subprocess.STDOUT)
  health('http://127.0.0.1');continuity()
 except BaseException:
  TEMPLATE.write_bytes(prev_template)
  run(['docker','exec','-i',PROD[0],'sh','-c','cat > /etc/nginx/conf.d/default.conf'],input=prev_effective)
  run(['docker','exec',PROD[0],'nginx','-t'],stderr=subprocess.STDOUT)
  run(['docker','exec',PROD[0],'nginx','-s','reload'],stderr=subprocess.STDOUT)
  continuity()
  raise
 print('EDGE_'+level.upper()+'=APPLIED')

def rollback():
 # Ingress and Staging only; never stop/recreate any Production service.
 TEMPLATE.write_bytes((BACKUP/'edge.template').read_bytes())
 run(['docker','exec','-i',PROD[0],'sh','-c','cat > /etc/nginx/conf.d/default.conf'],input=(BACKUP/'cloud-nginx-1.effective.conf').read_bytes())
 run(['docker','exec',PROD[0],'nginx','-t'],stderr=subprocess.STDOUT)
 run(['docker','exec',PROD[0],'nginx','-s','reload'],stderr=subprocess.STDOUT)
 print(compose(BACKUP/'rollback.private.json','up','-d','--no-build','--pull','never','--no-deps','backend','nginx').decode())
 verify_staging(original=True)

os.umask(0o077)
action=sys.argv[1] if __name__=='__main__' else None
if action is not None:
 lock_handles=[]
 for name in ['/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock','/srv/restaurant-pos/staging/state/restaurant-pos-staging-hygiene.lock']:
  path=pathlib.Path(name);assert path.is_file() and not path.is_symlink()
  handle=path.open('r+');fcntl.flock(handle,fcntl.LOCK_EX|fcntl.LOCK_NB);lock_handles.append(handle)
if action=='prepare':prepare()
elif action=='stage':stage()
elif action in ['http','tls']:edge(action)
elif action=='check':continuity()
elif action=='rollback':rollback()
elif action is not None:raise SystemExit('Unknown action')
