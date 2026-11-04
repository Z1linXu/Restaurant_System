#!/usr/bin/env python3
"""Owner-approved 74dfc1f app/6-record batch; reuse reviewed promotion primitives.
Operator script is separate from the immutable application SHA/artifacts.
"""
import copy, datetime, fcntl, hashlib, importlib.util, json, os
from pathlib import Path
import subprocess, sys, tarfile, time
sys.dont_write_bytecode=True
os.umask(0o077)
SHA='74dfc1fd235af31fc7345c43f770d9991960da70'
BEFORE_SHA='27612b6f72dabc48e0167cc7d214a3ece565dd5e'
SOURCE=Path('/srv/restaurant-pos/staging/releases')/SHA
ROOT=Path('/srv/restaurant-pos/production')/('uber-order-total-'+SHA)
BASE=ROOT.parent
OLD={'backend':'sha256:1e06e79b8655fdc56bd647390d12099f4df304da6314d7ed617a436031f999f5',
     'nginx':'sha256:18dd0fa21a80f77a357f2a2d32069094182cdb26c8f7cd135f4b04fd76d598e7'}
SERVICES=tuple(OLD)
spec=importlib.util.spec_from_file_location('approved_prior',Path(__file__).with_name('deploy.py'))
# A private uploaded operator uses the exact candidate's existing helper.
if not Path(spec.origin).exists():spec=importlib.util.spec_from_file_location('approved_prior',SOURCE/'deployment/cloud/uber-analytics-pending/deploy.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
run,read,save,sql,compose=m.run,m.read,m.save,m.sql,m.compose

def exact_source():
    assert run(['git','-C',str(SOURCE),'rev-parse','HEAD']).decode().strip()==SHA
    assert not run(['git','-C',str(SOURCE),'status','--porcelain']).strip()
    manifest=read(ROOT/'manifest.json');assert manifest['candidate_sha']==SHA and manifest['production_sha']==BEFORE_SHA
    return manifest

def v2():
    return sql("SELECT md5(coalesce(string_agg(row_to_json(t)::text,'' ORDER BY id),'')) FROM uber_eats_financial_corrections t WHERE snapshot_version=2")

def six():
    return json.loads(sql("SELECT json_build_object('rows',count(*),'v3',count(*) FILTER(WHERE snapshot_version=3),'apply',count(*) FILTER(WHERE action='APPLY')) FROM uber_eats_financial_corrections WHERE uber_eats_order_id IN(693,698,705,711,717,721)"))

def totals(a,corrected):
    t=a['1']['totals'];assert [t[c]['order_count'] for c in ('total','in_store','uber_eats')]==[107,61,46]
    assert t['in_store']['revenue']==2707.04
    assert t['uber_eats']['known_revenue']==(1718.47 if corrected else 1403.06)
    assert t['total']['known_revenue']==(4425.51 if corrected else 4110.10)
    assert t['uber_eats']['unknown_order_count']==(0 if corrected else 6)
    assert t['total']['unknown_line_count']==18
    if corrected:
        assert t['total']['revenue']==4425.51 and t['uber_eats']['revenue']==1718.47
        assert t['total']['average_order_value']==41.36
        assert t['total']['item_pending_order_count']==6
        assert all(row['total']['percentage'] is None for row in a['1']['categories'])
    else:assert t['total']['revenue'] is None

def stable(value):
    if isinstance(value,dict):return {k:stable(v) for k,v in value.items() if k not in ('item_pending_order_count','item_pending_reasons')}
    if isinstance(value,list):return [stable(v) for v in value]
    return value

def history_baseline():
    # Existing read-only API helper, narrowed to prior day for two unaffected Stores.
    smoke=m.load('numeric_old_day',SOURCE/'deployment/cloud/production-v26-smoke.py')
    http=m.load('old_day_http',SOURCE/'deployment/cloud/verify-uber-receive-only-20261022.py')
    smoke.run_psql=lambda db,q:m.d.read_sql(db,q)
    u,r,code,_,org=smoke.numeric_identity(m.DB);assert org==1
    env=m.ops.env_of(m.i.inspect('cloud-backend-1'))
    token=smoke.mint_token(env['JWT_SECRET'],u,r,1,org,code);result={}
    for store in (2,3):
        status,raw=http.request('https://pos.lanzhounoodlesmtl.com/api/v1/admin/analytics/summaries?store_id='+str(store)+'&range=custom&start_date=2026-11-02&end_date=2026-11-02',headers={'Authorization':'Bearer '+token})
        assert status==200;result[str(store)]=json.loads(raw)['data']['channel_sales']
    return result

def continuity(b):
    assert m.business()==b['business'] and v2()==b['v2']
    assert all(m.i.fingerprint(n)==v for n,v in b['protected'].items())
    assert all(not m.i.inspect(n)['State']['Running'] for n in m.PAUSED)
    m.guard();m.resources()

def prepare():
    exact_source();assert not (ROOT/'baseline.private.json').exists()
    before={s:m.i.inspect('cloud-'+s+'-1') for s in SERVICES}
    assert all(before[s]['Image']==OLD[s] and m.d.prior.revision(before[s])==BEFORE_SHA for s in SERVICES)
    assert six()['rows']==0
    ledger=m.i.ledger(m.DB);assert ledger.splitlines()[-1]=='39.1|V39_1__add_uber_financial_corrections.sql|1612790231|t'
    model=read(BASE/'current.compose.json');save(ROOT/'rollback.private.json',model)
    save(ROOT/'rollback-release.private.json',read(BASE/'release.json'))
    resolved=json.loads(compose(ROOT/'rollback.private.json','config','--format','json'))
    for s in SERVICES:
        assert resolved['services'][s]['image']==OLD[s]
        assert resolved['services'][s]['environment']==m.ops.env_of(before[s])
    names=run(['docker','ps','-aq','--no-trunc']).decode().split();assert set(m.PAUSED).issubset(names)
    protected={n:m.i.fingerprint(n) for n in names if n not in {v['Id'] for v in before.values()}}
    m.guard();resources=m.resources();a=m.analytics();totals(a,False)
    b={'before':before,'protected':protected,'ledger':ledger,'business':m.business(),'v2':v2(),'analytics':a,
       'history':history_baseline(),'resources':resources,'prepared_at':datetime.datetime.now(datetime.timezone.utc).isoformat()}
    save(ROOT/'baseline.private.json',b);continuity(b)
    with (ROOT/'database-before.dump').open('xb') as output:
        subprocess.run(['docker','exec',m.DB,'sh','-c','pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'],stdout=output,check=True,timeout=180)
    with (ROOT/'database-before.dump').open('rb') as f:toc=run(['docker','exec','-i',m.DB,'pg_restore','--list'],stdin=f)
    assert all(x in toc for x in (b'public orders',b'public print_jobs',b'public uber_eats_financial_corrections'))
    backup={'sha256':hashlib.sha256((ROOT/'database-before.dump').read_bytes()).hexdigest(),'bytes':(ROOT/'database-before.dump').stat().st_size,'toc':'PASS'}
    save(ROOT/'backup.json',backup);print(json.dumps({'prepare':'PASS','backup':backup}),flush=True)

def build():
    mf=exact_source();continuity(read(ROOT/'baseline.private.json'))
    jar=ROOT/'app.jar';archive=ROOT/'frontend.tgz'
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==mf['jar_sha256']
    assert hashlib.sha256(archive.read_bytes()).hexdigest()==mf['frontend_archive_sha256']
    images={}
    for service in SERVICES:
        base=('restaurant-pos-backend' if service=='backend' else 'restaurant-pos-frontend')+':uber-analytics-'+BEFORE_SHA
        assert m.i.inspect(base)['Id']==OLD[service]
        context=ROOT/('build-'+service);context.mkdir(mode=0o700)
        if service=='backend':(context/'app.jar').write_bytes(jar.read_bytes());copy_line='COPY app.jar /app/app.jar\n'
        else:
            (context/'dist').mkdir()
            with tarfile.open(archive) as t:
                assert all(not Path(x.name).is_absolute() and '..' not in Path(x.name).parts and not x.issym() and not x.islnk() for x in t.getmembers())
                t.extractall(context/'dist')
            copy_line='COPY dist/ /usr/share/nginx/html/\n'
        (context/'Dockerfile').write_text('FROM '+base+'\nLABEL org.opencontainers.image.revision="'+SHA+'"\n'+copy_line)
        tag=('restaurant-pos-backend' if service=='backend' else 'restaurant-pos-frontend')+':uber-order-total-'+SHA
        with (ROOT/('build-'+service+'.log')).open('xb') as log:
            subprocess.run(['docker','build','--pull=false','--network=none','-t',tag,str(context)],stdout=log,stderr=subprocess.STDOUT,check=True)
        image=m.i.inspect(tag);assert image['Config']['Labels']['org.opencontainers.image.revision']==SHA;images[service]=image['Id']
    actual=run(['docker','run','--rm','--network','none','--read-only','--memory','64m','--cpus','.25','--entrypoint','sha256sum',images['backend'],'/app/app.jar']).decode().split()[0]
    assert actual==mf['jar_sha256'];save(ROOT/'images.json',images);print(json.dumps({'build':'PASS','images':images}),flush=True)

def cli(mode):
    mf=read(ROOT/'manifest.json');env=m.ops.env_of(m.i.inspect('cloud-backend-1'))
    env_file=ROOT/'cli.private.env';env_file.write_text(''.join(k+'='+env[k]+'\n' for k in ('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD')));env_file.chmod(0o600)
    args=['docker','run','--rm','--network','cloud_restaurant-pos','--read-only','--memory','192m','--cpus','.5','--pids-limit','64','--cap-drop','ALL','--security-opt','no-new-privileges','--env-file',str(env_file),'--entrypoint','java',read(ROOT/'images.json')['backend'],'-Xmx128m','-Dloader.main=com.restaurant.system.integration.ubereats.service.UberFinancialCorrectionCli','-cp','/app/app.jar','org.springframework.boot.loader.launch.PropertiesLauncher','--mode',mode,'--case','order-total-v3']
    if mode!='preview':args+=['--preview-sha',mf['preview_sha256']]
    return json.loads(run(args))

def preview():
    result=cli('preview');assert result==read(ROOT/'expected-preview.json')
    assert len(result['orders'])==6 and all(x['allowed'] for x in result['orders'])
    assert sum(x['candidateMinor'] for x in result['orders'])==31541
    save(ROOT/'live-preview.json',result);print(json.dumps({'preview':'PASS','sha256':result['previewSha256']}),flush=True)

def restore_application(b):
    try:
        batch=read(ROOT/'manifest.json')['preview_sha256'];assert len(batch)==64 and all(c in '0123456789abcdef' for c in batch)
        count=int(sql("SELECT count(*) FROM uber_eats_financial_corrections WHERE action='APPLY' AND batch_preview_sha256='"+batch+"'"))
        if count:save(ROOT/'compensation.json',cli('revoke'))
    except BaseException as error:
        try:save(ROOT/'compensation-failure.json',{'type':type(error).__name__,'requires_attention':True})
        except BaseException:pass
    finally:
        compose(ROOT/'rollback.private.json','up','-d','--no-build','--pull','never','--no-deps',*SERVICES)
        try:save(BASE/'current.compose.json',read(ROOT/'rollback.private.json'))
        finally:save(BASE/'release.json',read(ROOT/'rollback-release.private.json'))
        m.d.prior.wait_health('https://pos.lanzhounoodlesmtl.com')

def rollback():
    b=read(ROOT/'baseline.private.json');images=read(ROOT/'images.json')
    for service in SERVICES:
        actual=m.i.inspect('cloud-'+service+'-1')
        assert actual['Image'] in (OLD[service],images[service]), 'Another release is active'
        assert m.ops.env_of(actual)==m.ops.env_of(b['before'][service]), 'Runtime configuration changed'
    restore_application(b)

def deploy():
    mf=exact_source();b=read(ROOT/'baseline.private.json');images=read(ROOT/'images.json')
    proof=read(ROOT/'restore-verified.json');assert proof['restore']=='PASS' and proof['backup_sha256']==read(ROOT/'backup.json')['sha256'] and proof['preview_sha256']==mf['preview_sha256']
    rollback=read(ROOT/'rollback.private.json');assert read(BASE/'current.compose.json')==rollback
    for s in SERVICES:
        assert m.i.inspect('cloud-'+s+'-1')['Id']==b['before'][s]['Id']
        assert m.i.inspect(images[s])['Config']['Labels']['org.opencontainers.image.revision']==SHA
    continuity(b);assert m.i.ledger(m.DB)==b['ledger'];assert six()['rows']==0;totals(m.analytics(),False);preview()
    target=copy.deepcopy(rollback)
    for s in SERVICES:
        target['services'][s]['image']=images[s];target['services'][s].setdefault('labels',{})['org.opencontainers.image.revision']=SHA
    save(ROOT/'target.private.json',target)
    m.d.validate_delta(json.loads(compose(ROOT/'rollback.private.json','config','--format','json')),json.loads(compose(ROOT/'target.private.json','config','--format','json')),images)
    started=time.time();phase='replace';mutated=False
    try:
        for s in SERVICES:
            mutated=True;phase=s+'.replace';compose(ROOT/'target.private.json','up','-d','--no-build','--pull','never','--no-deps',s)
            phase=s+'.health';m.d.prior.wait_health('https://pos.lanzhounoodlesmtl.com')
            after=m.i.inspect('cloud-'+s+'-1');before=b['before'][s]
            assert after['Image']==images[s] and m.ops.env_of(after)==m.ops.env_of(before) and after['RestartCount']==0
            for k in ('Entrypoint','Cmd','User','WorkingDir'):assert after['Config'].get(k)==before['Config'].get(k)
            for k in ('Memory','MemorySwap','NanoCpus','PortBindings','RestartPolicy','LogConfig'):assert after['HostConfig'].get(k)==before['HostConfig'].get(k)
            assert m.d.normalized_binds(after['HostConfig'].get('Binds'))==m.d.normalized_binds(before['HostConfig'].get('Binds'))
            assert {k:v['IPAddress'] for k,v in after['NetworkSettings']['Networks'].items()}=={k:v['IPAddress'] for k,v in before['NetworkSettings']['Networks'].items()}
        phase='migration';ledger=m.i.ledger(m.DB).splitlines();assert ledger[:-1]==b['ledger'].splitlines()
        parts=ledger[-1].split('|');assert parts[0]=='39.2' and parts[1]=='V39_2__allow_uber_order_total_correction_v3.sql' and parts[-1]=='t'
        phase='before_apply';continuity(b);a=m.analytics();totals(a,False)
        assert all(stable(a[k])==stable(b['analytics'][k]) for k in ('1','2','3'));preview()
        phase='apply';result=cli('apply');save(ROOT/'apply.json',result);assert result['inserted']==6 and result['previewSha256']==mf['preview_sha256']
        phase='accept';a=m.analytics();totals(a,True);continuity(b)
        assert six()=={'rows':6,'v3':6,'apply':6}
        assert all(stable(a[k])==stable(b['analytics'][k]) for k in ('2','3'))
        assert stable(history_baseline())==stable(b['history'])
        save(BASE/'current.compose.json',target);save(BASE/'release.json',{'source_sha':SHA,'release_dir':str(ROOT),'compose':'current.compose.json'})
        save(ROOT/'acceptance.json',{'status':'PASS','source_sha':SHA,'images':images,'analytics':a,'resources':m.resources(),'ledger':m.i.ledger(m.DB),'elapsed_seconds':round(time.time()-started,2),'business_unchanged':True,'v2_unchanged':True,'other_containers_unchanged':True})
        print(json.dumps({'deploy':'PASS','source_sha':SHA,'six_minor':31541,'total':4425.51,'uber':1718.47,'aov':41.36,'elapsed_seconds':round(time.time()-started,2)}),flush=True)
    except BaseException as error:
        try:save(ROOT/'failure.json',{'phase':phase,'type':type(error).__name__})
        finally:
            if mutated:restore_application(b)
        raise

if __name__=='__main__':
    lock=open('/home/ubuntu/Restaurant_System/deployment/cloud/.production-ops.lock','r+');fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    try:
        action=sys.argv[1]
        if action=='rollback':rollback()
        else:{'prepare':prepare,'build':build,'preview':preview,'deploy':deploy}[action]()
    except BaseException as error:
        print('ORDER_TOTAL_RELEASE_FAILED:'+type(error).__name__,file=sys.stderr,flush=True);sys.exit(2)
