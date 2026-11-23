#!/usr/bin/env python3
"""One approved financial repair. Reuse reviewed Compose/backup/health primitives.
No Staging activation, database restart, print action, webhook or order write.
"""
import copy
import datetime as dt
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

sys.dont_write_bytecode = True
os.umask(0o077)
HERE = Path(__file__).resolve().parent

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

d = load('reviewed_release', HERE.parent / 'dine-in-headers/deploy.py')
i, ops = d.i, d.ops
DB = 'cloud-db-1'
BASE = Path('/srv/restaurant-pos/production')
OLD = {'backend':'sha256:bf09f83635d532523b82c39dae30afbdfa12840858dd195d46423423cf49f696',
       'nginx':'sha256:af9a528ae56ceb905597794823b2ad417ef3c580fac6e3689c311ec837dcc9d9'}
SERVICES = tuple(OLD)
CASE = 'UBER_ANALYTICS_20261103_ST_DENIS'
PAUSED = (
 'f263920c00250b8fffc2c4f9a9a66c6be5906bccceafd419f9d39559b3df4ad0',
 'e16a3f0fa724db629c7bb06b805da6fbf2d41b147aa327a17651f53be80b484d',
 'b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5',
 '13c56a063ad3faaa04ab7cebc59a235defce5f12b13684f7a2bf1323785d3aee',
 '3b8fc711a94c5e0653fe9b093e3de7d6eff4d2db272b54dac79ebb95c2a2bd94',
 '3ac7d5158ca7248d93341c6480c46ef07160e47da5935b7c283a97aafb17a2fc',
 '0a6e8125c9c841ac9b88c11a80b10d5f4a2db30a40a62bf120fdf1b65c8c77ef',
 'd57e094259cd1ee7f579393b69890fa64903b84b0fd9a6dfa7fd24626f1bcf36')


def run(args, **kwargs):
    return i.run(args, **kwargs)

def sql(value):
    return d.read_sql(DB, value)

def root_for(sha):
    assert re.fullmatch('[a-f0-9]{40}', sha)
    assert run(['git','-C',str(HERE.parents[2]),'rev-parse','HEAD']).decode().strip() == sha
    assert not run(['git','-C',str(HERE.parents[2]),'status','--porcelain']).strip()
    return BASE / ('uber-analytics-pending-'+sha)

def compose(path, *args):
    return run(['docker','compose','-p','cloud','-f',str(path),*args], stderr=subprocess.STDOUT)

def save(path, value):
    ops.write_private(path, value)

def read(path):
    return json.loads(ops.private_file(path).read_text())

def resources():
    available = int(Path('/proc/meminfo').read_text().split('MemAvailable:')[1].split()[0])*1024
    disk = os.statvfs('/'); free = disk.f_bavail*disk.f_frsize
    used = 1-disk.f_bfree/disk.f_blocks
    assert available >= 1024**3 and free >= 5*1024**3 and used < .90, 'Resource safety gate'
    assert os.getloadavg()[0] < 2, 'Load gate'
    return {'mem_available_bytes':available,'disk_free_bytes':free,'disk_used_percent':round(used*100,2),
            'load':os.getloadavg(),'docker_system_df':run(['docker','system','df']).decode()}

def guard():
    q=ops.inventory(DB)
    assert all(q[k]==0 for k in ('pending_print_jobs','pending_outbox','pending_uber_events','due_uber_orders'))
    state=json.loads(sql("""SELECT json_build_object(
      'other_printing',(SELECT count(*) FROM print_jobs WHERE status='PRINTING' AND id NOT IN (1176,3169,9226,20391)),
      'fresh_printing',(SELECT count(*) FROM print_jobs WHERE id IN (1176,3169,9226,20391) AND status='PRINTING' AND
         (claim_expires_at>timezone('America/Toronto',now()) OR updated_at>timezone('America/Toronto',now())-interval '2 minutes')),
      'processing_events',(SELECT count(*) FROM uber_eats_events WHERE status='PROCESSING'),
      'fresh_events',(SELECT count(*) FROM uber_eats_events WHERE created_at>now()-interval '2 minutes'),
      'recent_pos',(SELECT count(*) FROM orders WHERE external_source IS DISTINCT FROM 'UBER_EATS' AND submitted_at>timezone('America/Toronto',now())-interval '10 minutes'))"""))
    assert all(v==0 for v in state.values()), 'New live work; historical jobs only are exempt'
    # Avoid the existing 23:30 end-of-day worker while replacing the single Backend.
    local=json.loads(sql("SELECT json_build_object('hour',extract(hour from timezone('America/Toronto',now())),'minute',extract(minute from timezone('America/Toronto',now())))"))
    assert not (local['hour']==23 and 20<=local['minute']<=40), 'End-of-day worker window'
    assert i.inspect(DB)['State']['Health']['Status']=='healthy'
    return q

def business():
    tables=('orders','order_items','print_jobs','order_dispatch_outbox','kitchen_tasks','uber_eats_events','uber_eats_orders')
    result={table:json.loads(sql(f"SELECT json_build_object('count',count(*),'max',coalesce(max(id),0)) FROM {table}")) for table in tables}
    result['original_financials']=sql("""SELECT md5(coalesce(string_agg(id::text||coalesce(item_financial_snapshot_json,'')||coalesce(raw_financial_snapshot_json,'')||status,'' ORDER BY id),'')) FROM uber_eats_orders WHERE environment='production' AND store_id IN (1,2,3) AND placed_at>='2026-11-03 05:00:00' AND placed_at<'2026-11-04 05:00:00'""")
    result['protected_printing']=sql("SELECT md5(coalesce(string_agg(row_to_json(p)::text,'' ORDER BY id),'')) FROM print_jobs p WHERE id IN (1176,3169,9226,20391)")
    return result

def analytics():
    smoke=load('numeric_read_smoke',HERE.parent/'production-v26-smoke.py')
    http=load('bounded_http',HERE.parent/'verify-uber-receive-only-20261022.py')
    smoke.run_psql=lambda db,query:d.read_sql(db,query)
    user,role,code,_,org=smoke.numeric_identity(DB)
    assert org==1
    env=ops.env_of(i.inspect('cloud-backend-1'))
    token=smoke.mint_token(env['JWT_SECRET'],user,role,1,org,code)
    result={}
    for store in (1,2,3):
        status,body=http.request('https://pos.lanzhounoodlesmtl.com/api/v1/admin/analytics/summaries?store_id='+str(store)+'&range=custom&start_date=2026-11-03&end_date=2026-11-03',headers={'Authorization':'Bearer '+token})
        assert status==200
        envelope=json.loads(body); assert envelope.get('success') is True
        result[str(store)]=envelope['data']['channel_sales']
    return result

def stable(value):
    if isinstance(value,dict):return {k:stable(v) for k,v in value.items() if k not in ('unknown_order_count','unknown_line_count','pending_reasons')}
    if isinstance(value,list):return [stable(v) for v in value]
    return value

def verify_totals(data, corrected):
    t=data['1']['totals']; assert [t[c]['order_count'] for c in ('total','in_store','uber_eats')]==[106,60,46]
    assert t['in_store']['revenue']==2639.25
    assert t['uber_eats']['known_revenue']==(1403.06 if corrected else 1103.13)
    assert t['total']['known_revenue']==(4042.31 if corrected else 3742.38)
    assert t['total']['revenue'] is None and t['total']['average_order_value'] is None
    assert t['uber_eats']['unknown_amount_count']==(6 if corrected else 14)
    if corrected:assert t['uber_eats']['unknown_order_count']==6

def cli(root, mode, preview_sha=None):
    image=read(root/'images.json')['backend']
    env=ops.env_of(i.inspect('cloud-backend-1'))
    # Only DB credentials enter the private CLI file; never stdout or an argv value.
    env_path=root/'cli.private.env'
    env_path.write_text(''.join(k+'='+env[k]+'\n' for k in ('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD')))
    env_path.chmod(0o600)
    args=['docker','run','--rm','--network','cloud_restaurant-pos','--read-only','--memory','192m','--cpus','.5',
          '--pids-limit','64','--cap-drop','ALL','--security-opt','no-new-privileges','--env-file',str(env_path),
          '--entrypoint','java',image,'-Xmx128m','-Dloader.main=com.restaurant.system.integration.ubereats.service.UberFinancialCorrectionCli',
          '-cp','/app/app.jar','org.springframework.boot.loader.launch.PropertiesLauncher','--mode',mode]
    if preview_sha:args+=['--preview-sha',preview_sha]
    return json.loads(run(args))

def prepare(sha):
    root=root_for(sha); assert not root.exists(); root.mkdir(mode=0o700)
    before={s:i.inspect('cloud-'+s+'-1') for s in SERVICES}
    assert all(before[s]['Image']==OLD[s] for s in SERVICES)
    assert all(d.prior.revision(before[s])=='028d4971740889a0ac0d784a43497bc3e5b54b3d' for s in SERVICES)
    model=read(BASE/'current.compose.json')
    save(root/'rollback.private.json',model)
    resolved=json.loads(compose(root/'rollback.private.json','config','--format','json'))
    for s in SERVICES:
        assert resolved['services'][s]['image']==before[s]['Image']
        assert resolved['services'][s]['environment']==ops.env_of(before[s])
    names=run(['docker','ps','-aq','--no-trunc']).decode().split()
    protected={n:i.fingerprint(n) for n in names if n not in {c['Id'] for c in before.values()}}
    assert set(PAUSED).issubset(names)
    assert all(not i.inspect(n)['State']['Running'] for n in PAUSED)
    assert i.ledger(DB).splitlines()[-1].startswith('39|')
    baseline={'before':before,'protected':protected,'business':business(),'queues':guard(),'resources':resources(),
              'ledger':i.ledger(DB),'analytics':analytics(),'prepared_at':dt.datetime.now(dt.timezone.utc).isoformat()}
    verify_totals(baseline['analytics'],False)
    save(root/'baseline.private.json',baseline)
    with (root/'database-before.dump').open('xb') as output:
        subprocess.run(['docker','exec',DB,'sh','-c','pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'],stdout=output,check=True,timeout=180)
    with (root/'database-before.dump').open('rb') as backup:
        toc=run(['docker','exec','-i',DB,'pg_restore','--list'],stdin=backup)
    assert b'public orders' in toc and b'public print_jobs' in toc and b'public uber_eats_orders' in toc
    save(root/'backup.json',{'sha256':hashlib.sha256((root/'database-before.dump').read_bytes()).hexdigest(),'bytes':(root/'database-before.dump').stat().st_size,'toc':'PASS'})
    print(json.dumps({'prepare':'PASS','root':str(root),'backup':read(root/'backup.json')}))

def preview(sha):
    root=root_for(sha); result=cli(root,'preview'); save(root/'preview.json',result)
    assert len(result['orders'])==14 and sum(x['allowed'] for x in result['orders'])==8
    assert sum(x['candidateMinor'] for x in result['orders'] if x['allowed'])==29993
    print(json.dumps(result))

def deploy(sha):
    root=root_for(sha); baseline=read(root/'baseline.private.json'); images=read(root/'images.json')
    proof=read(root/'restore-verified.json'); assert proof['backup_sha256']==read(root/'backup.json')['sha256'] and proof['restore']=='PASS'
    assert proof['preview_sha256']==read(root/'preview.json')['previewSha256']
    assert read(root/'validation.json')['source_sha']==sha and read(root/'validation.json')['status']=='PASS'
    rollback=read(root/'rollback.private.json'); assert read(BASE/'current.compose.json')==rollback
    for s in SERVICES:
        assert i.inspect('cloud-'+s+'-1')['Id']==baseline['before'][s]['Id']
        assert i.inspect(images[s])['Config']['Labels']['org.opencontainers.image.revision']==sha
    assert business()==baseline['business']; guard(); resources()
    assert all(i.fingerprint(n)==v for n,v in baseline['protected'].items())
    assert all(not i.inspect(n)['State']['Running'] for n in PAUSED)
    assert cli(root,'preview')==read(root/'preview.json')
    target=copy.deepcopy(rollback)
    for s in SERVICES:
        target['services'][s]['image']=images[s]
        target['services'][s].setdefault('labels',{})['org.opencontainers.image.revision']=sha
    save(root/'target.private.json',target)
    d.validate_delta(json.loads(compose(root/'rollback.private.json','config','--format','json')),json.loads(compose(root/'target.private.json','config','--format','json')),images)
    applied=False; start=time.time()
    try:
        for s in SERVICES:
            compose(root/'target.private.json','up','-d','--no-build','--pull','never','--no-deps',s)
            d.prior.wait_health('https://pos.lanzhounoodlesmtl.com')
            after=i.inspect('cloud-'+s+'-1'); before=baseline['before'][s]
            assert after['Image']==images[s] and ops.env_of(after)==ops.env_of(before)
            for key in ('Entrypoint','Cmd','User','WorkingDir'):assert after['Config'].get(key)==before['Config'].get(key)
            for key in ('Memory','MemorySwap','NanoCpus','PortBindings','RestartPolicy','LogConfig'):assert after['HostConfig'].get(key)==before['HostConfig'].get(key)
            assert d.normalized_binds(after['HostConfig'].get('Binds'))==d.normalized_binds(before['HostConfig'].get('Binds'))
            assert {k:v['IPAddress'] for k,v in after['NetworkSettings']['Networks'].items()}=={k:v['IPAddress'] for k,v in before['NetworkSettings']['Networks'].items()}
        assert i.ledger(DB).splitlines()[:-1]==baseline['ledger'].splitlines() and i.ledger(DB).splitlines()[-1].startswith('39.1|')
        assert business()==baseline['business']; guard()
        before_correction=analytics();verify_totals(before_correction,False)
        assert all(stable(before_correction[s])==stable(baseline['analytics'][s]) for s in ('1','2','3'))
        preview=read(root/'preview.json'); applied=True
        correction=cli(root,'apply',preview['previewSha256']);save(root/'apply.json',correction);assert correction['inserted']==8
        after=analytics();verify_totals(after,True)
        assert all(stable(after[s])==stable(baseline['analytics'][s]) for s in ('2','3'))
        assert business()==baseline['business'];guard()
        assert all(i.fingerprint(n)==v for n,v in baseline['protected'].items())
        assert i.inspect(DB)['State']['Health']['Status']=='healthy'
        assert all(not i.inspect(n)['State']['Running'] for n in PAUSED)
        save(BASE/'current.compose.json',target)
        save(BASE/'release.json',{'source_sha':sha,'release_dir':str(root),'compose':'current.compose.json'})
        save(root/'acceptance.json',{'source_sha':sha,'status':'PASS','analytics':after,'resources':resources(),
             'elapsed_release_seconds':round(time.time()-start,2),'business_unchanged':True,'protected_containers_unchanged':True,
             'corrections':8,'remaining_pending':6,'single_instance_replacement':True})
        print(json.dumps({'deploy':'PASS','source_sha':sha,'images':images,'corrections':8,'pending':6,'elapsed_seconds':round(time.time()-start,2)}))
    except BaseException as error:
        try:save(root/'failure.json',{'type':type(error).__name__,'after_apply_attempt':applied})
        finally:rollback_failed_release(root,applied,rollback)
        raise RuntimeError('Release failed; app rollback applied; additive schema retained; inspect private evidence') from None

def rollback_failed_release(root, applied, rollback):
    # Even DB outage, missing preview or compensation diagnostics must not skip app rollback.
    try:
        if applied:
            try:
                batch=read(root/'preview.json')['previewSha256']
                assert re.fullmatch('[a-f0-9]{64}',batch)
                count=int(sql("SELECT count(*) FROM uber_eats_financial_corrections WHERE action='APPLY' AND batch_preview_sha256='"+batch+"'"))
                if count:save(root/'compensation.json',cli(root,'revoke',batch))
            except BaseException as error:
                save(root/'compensation-failure.json',{'type':type(error).__name__,'requires_owner_attention':True})
    finally:
        compose(root/'rollback.private.json','up','-d','--no-build','--pull','never','--no-deps',*SERVICES)
        d.prior.wait_health('https://pos.lanzhounoodlesmtl.com')
        save(BASE/'current.compose.json',rollback)

if __name__=='__main__':
    lock=open('/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock','r+')
    fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    try:
        {'prepare':prepare,'preview':preview,'deploy':deploy}[sys.argv[1]](sys.argv[2])
    except BaseException as error:
        print('PENDING_RELEASE_FAILED:'+type(error).__name__,file=sys.stderr)
        sys.exit(2)
