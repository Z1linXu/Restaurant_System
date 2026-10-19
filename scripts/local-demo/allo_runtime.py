#!/usr/bin/env python3
"""Opt-in isolated Mac demo lifecycle. Never reads cloud/local workspace environments."""
import argparse, datetime, json, os, pathlib, secrets, shutil, signal, socket, subprocess, sys, tempfile, time, urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
PG = pathlib.Path('/opt/homebrew/opt/postgresql@16/bin')
JAVA = pathlib.Path('/Users/xuzilin/Library/Java/JavaVirtualMachines/ms-17.0.14/Contents/Home/bin/java')
NODE = pathlib.Path('/opt/homebrew/opt/node@20/bin/node')
ENV = {k: os.environ[k] for k in ('HOME', 'PATH', 'LANG') if k in os.environ}
ENV['PATH'] = '/opt/homebrew/bin:/usr/bin:/bin'
ENV['LANG'] = 'en_US.UTF-8'

def put(path, obj):
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2)+'\n'); path.chmod(0o600)

def port(preferred):
    with socket.socket() as s:
        try: s.bind(('127.0.0.1', preferred)); return preferred
        except OSError: s.bind(('127.0.0.1', 0)); return s.getsockname()[1]

def sql(run, statement, database='postgres', admin=True):
    state=json.loads((run/'runtime.json').read_text()); sec=json.loads((run/'secrets.json').read_text())
    user='allo_admin' if admin else 'allo_app'
    env={**ENV, 'PGPASSWORD':sec['pg_admin_password' if admin else 'db_password']}
    return subprocess.run([str(PG/'psql'),'-X','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p',str(state['ports']['postgres']),'-U',user,'-d',database,'-At'],input=statement,text=True,capture_output=True,env=env,check=True).stdout

def init():
    parent=pathlib.Path.home()/'Library/Application Support/RestaurantSystemCommercialDemo'
    parent.mkdir(parents=True,exist_ok=True)
    run=pathlib.Path(tempfile.mkdtemp(prefix='allo-20261018-',dir=parent)); run.chmod(0o700)
    ports={'postgres':port(55441),'backend':port(8081),'frontend':port(51026)}
    sec={k:secrets.token_urlsafe(40) for k in ('pg_admin_password','db_password','jwt_secret')}
    put(run/'secrets.json',sec)
    (run/'owner-password.txt').write_text(secrets.token_urlsafe(20)+'\n'); (run/'owner-password.txt').chmod(0o600)
    (run/'pg-admin-password.txt').write_text(sec['pg_admin_password']); (run/'pg-admin-password.txt').chmod(0o600)
    socket_dir=pathlib.Path(tempfile.mkdtemp(prefix='allo_pg_',dir='/private/tmp')); socket_dir.chmod(0o700)
    state={'socket_directory':str(socket_dir),'kind':'ALLO_LOCAL_SYNTHETIC_ONLY','root':str(ROOT),'runtime':str(run),'ports':ports,'database':'commercial_allo_demo','username':'allo.owner','printing':'MOCK','tax_policy':'TEST_ONLY_EXISTING_14_975_PERCENT_TAX_EXCLUSIVE','pids':{}}
    put(run/'runtime.json',state)
    with (run/'pg-init.log').open('w') as log:
        subprocess.run([str(PG/'initdb'),'-D',str(run/'pgdata'),'-U','allo_admin','--pwfile='+str(run/'pg-admin-password.txt'),'--auth-host=scram-sha-256','--auth-local=trust','--encoding=UTF8','--locale=C'],env=ENV,stdout=log,stderr=log,check=True)
    start_pg(run)
    sql(run,"CREATE ROLE allo_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '"+sec['db_password']+"';\nCREATE DATABASE commercial_allo_demo OWNER allo_app;\nCREATE DATABASE commercial_allo_regression OWNER allo_admin;")
    print(json.dumps({'runtime':str(run),'ports':ports,'database':state['database']},ensure_ascii=False))

def state_for(run):
    run=pathlib.Path(run).expanduser().resolve()
    state=json.loads((run/'runtime.json').read_text())
    if state.get('kind')!='ALLO_LOCAL_SYNTHETIC_ONLY' or state.get('runtime')!=str(run) or state.get('root')!=str(ROOT):
        raise SystemExit('Runtime identity mismatch; refusing operation')
    allowed_root=pathlib.Path.home()/'Library/Application Support/RestaurantSystemCommercialDemo'
    if run.parent!=allowed_root or not run.name.startswith('allo-') or state.get('database')!='commercial_allo_demo':
        raise SystemExit('Not an owned Allo demo runtime; refusing operation')
    ports=state.get('ports',{})
    if set(ports)!={'postgres','backend','frontend'} or any(type(p) is not int or p<1024 or p>65535 for p in ports.values()) or len(set(ports.values()))!=3:
        raise SystemExit('Invalid local port identity; refusing operation')
    return run,state

def start_pg(run):
    run,state=state_for(run)
    status=subprocess.run([str(PG/'pg_ctl'),'-D',str(run/'pgdata'),'status'],env=ENV,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    if status.returncode==0:
        identity=(run/'pgdata/postmaster.pid').read_text().splitlines()
        if len(identity)<4 or identity[1]!=str(run/'pgdata') or identity[3]!=str(state['ports']['postgres']):
            raise SystemExit('PostgreSQL identity mismatch; refusing operation')
        if not owned_alive(int(identity[0]),str(run/'pgdata')):
            raise SystemExit('PostgreSQL status changed; retry after checking its log')
        return
    if status.returncode!=3:
        raise SystemExit('Cannot establish PostgreSQL status; refusing operation')
    # pg_ctl handles a stale postmaster.pid itself; never delete the file or reset data.
    subprocess.run([str(PG/'pg_ctl'),'-D',str(run/'pgdata'),'-l',str(run/'postgres.log'),'-o',f"-h 127.0.0.1 -p {state['ports']['postgres']} -k {state['socket_directory']}",'-w','start'],env=ENV,stdout=subprocess.DEVNULL,check=True)

def alive(pid):
    if type(pid) is not int or pid<=0: return False
    try: os.kill(pid,0); return True
    except (ProcessLookupError,TypeError): return False

def owned_alive(pid,marker):
    if not alive(pid): return False
    command=subprocess.check_output(['/bin/ps','-p',str(pid),'-o','command='],text=True)
    if marker not in command: raise SystemExit('PID identity mismatch; refusing operation')
    return True

def start(run):
    run,state=state_for(run)
    backend_running=owned_alive(state['pids'].get('backend'),str(run/'backend.jar'))
    frontend_running=owned_alive(state['pids'].get('frontend'),str(run/'vite.config.mjs'))
    start_pg(run)
    sec=json.loads((run/'secrets.json').read_text()); bp=state['ports']['backend']; fp=state['ports']['frontend']
    if not backend_running:
        if not (run/'backend.jar').exists():
            shutil.copy2(ROOT/'backend/target/restaurant-system-backend-0.0.1-SNAPSHOT.jar',run/'backend.jar')
        env={**ENV,'COMMERCIAL_DB_URL':f"jdbc:postgresql://127.0.0.1:{state['ports']['postgres']}/{state['database']}",'COMMERCIAL_DB_USER':'allo_app','COMMERCIAL_DB_PASSWORD':sec['db_password'],'COMMERCIAL_JWT_SECRET':sec['jwt_secret'],'UBER_EATS_ENABLED':'false'}
        log=(run/'backend.log').open('a')
        p=subprocess.Popen([str(JAVA),'-jar',str(run/'backend.jar'),'--spring.profiles.active=commercial','--spring.config.location=classpath:/application.yml,classpath:/application-commercial.yml','--server.address=127.0.0.1',f'--server.port={bp}','--app.daily-close.enabled=false',f'--app.realtime.allowed-origins=http://localhost:{fp},http://127.0.0.1:{fp}'],env=env,cwd=run,stdin=subprocess.DEVNULL,stdout=log,stderr=log,start_new_session=True)
        state['pids']['backend']=p.pid
        put(run/'runtime.json',state)
    if not frontend_running:
        frontend_dir=pathlib.Path(state.get('frontend_directory',str(run/'frontend-dist')))
        if frontend_dir.resolve().parent!=run: raise SystemExit('Frontend artifact must be inside this runtime')
        if not frontend_dir.exists(): shutil.copytree(ROOT/'frontend/dist',frontend_dir)
        cfg={'root':str(ROOT/'frontend'),'cacheDir':str(run/'vite-cache'),'build':{'outDir':str(frontend_dir)},'preview':{'host':'127.0.0.1','port':fp,'strictPort':True,'proxy':{'/api':{'target':f'http://127.0.0.1:{bp}','changeOrigin':False},'/ws':{'target':f'http://127.0.0.1:{bp}','changeOrigin':True,'ws':True}}}}
        (run/'vite.config.mjs').write_text('export default '+json.dumps(cfg)+';\n')
        log=(run/'frontend.log').open('a')
        p=subprocess.Popen([str(NODE),str(ROOT/'frontend/node_modules/vite/bin/vite.js'),'preview','--config',str(run/'vite.config.mjs')],env=ENV,cwd=ROOT/'frontend',stdin=subprocess.DEVNULL,stdout=log,stderr=log,start_new_session=True)
        state['pids']['frontend']=p.pid
        put(run/'runtime.json',state)
    put(run/'runtime.json',state)
    status(run)

def status(run):
    run,state=state_for(run)
    health=False
    try:
        op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with op.open(f"http://127.0.0.1:{state['ports']['backend']}/api/v1/system/health",timeout=3) as r: health=r.status==200
    except Exception: pass
    print(json.dumps({'runtime':str(run),'frontend':f"http://localhost:{state['ports']['frontend']}/login",'backend':f"http://localhost:{state['ports']['backend']}",'pids':state['pids'],'backend_health_http_200':health},ensure_ascii=False))

def stop(run):
    run,state=state_for(run)
    for name in ('frontend','backend'):
        pid=state['pids'].get(name)
        if not alive(pid): continue
        command=subprocess.check_output(['/bin/ps','-p',str(pid),'-o','command='],text=True)
        if str(run) not in command: raise SystemExit('PID identity mismatch; refusing to stop')
        os.kill(pid,signal.SIGTERM)
        for _ in range(100):
            if not alive(pid): break
            time.sleep(.1)
        if alive(pid): raise SystemExit('Owned process is still stopping; no new process started')
    # Only this tool-created cluster is stopped; no database is deleted.
    subprocess.run([str(PG/'pg_ctl'),'-D',str(run/'pgdata'),'-m','fast','-w','stop'],env=ENV,check=True)
    state['pids']={}; put(run/'runtime.json',state)

def refresh_frontend(run):
    run,state=state_for(run)
    artifact=run/('frontend-dist-'+secrets.token_hex(4))
    shutil.copytree(ROOT/'frontend/dist',artifact)
    pid=state['pids'].get('frontend')
    if alive(pid):
        command=subprocess.check_output(['/bin/ps','-p',str(pid),'-o','command='],text=True)
        if str(run/'vite.config.mjs') not in command: raise SystemExit('Frontend PID identity mismatch')
        os.kill(pid,signal.SIGTERM)
        for _ in range(50):
            if not alive(pid): break
            time.sleep(.1)
        if alive(pid): raise SystemExit('Owned frontend is still stopping')
    state['pids'].pop('frontend',None);state['frontend_directory']=str(artifact)
    put(run/'runtime.json',state);start(run)

if __name__=='__main__':
    p=argparse.ArgumentParser(); p.add_argument('command',choices=('init','start','status','stop','refresh-frontend')); p.add_argument('--runtime')
    a=p.parse_args()
    if a.command=='init': init()
    elif not a.runtime: p.error('--runtime is required')
    elif a.command=='refresh-frontend': refresh_frontend(a.runtime)
    else: globals()[a.command](a.runtime)
