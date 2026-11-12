#!/usr/bin/env python3
"""One isolated localhost-only Allô synthetic acceptance runtime; no remote targets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PRIVATE = Path('/Users/xuzilin/.codex/private/allo-release-20261112')
CONFIG = PRIVATE / 'qa-config.json'
PGDATA = PRIVATE / 'qa-postgres'
PGBIN = Path('/opt/homebrew/opt/postgresql@16/bin')
MENU = Path('/Users/xuzilin/.codex/private/allo-migration-20261108/release-payload/menu.source.json')
DATABASE = 'restaurant_allo_nouilles_test'
USER = 'allo_test'
DB_PORT, BACKEND_PORT, FRONTEND_PORT = 26512, 18512, 28182


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *_args, **_kwargs):
        return None


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def write_private(path, value):
    with path.open('x', encoding='utf-8') as f:
        os.chmod(path, 0o600)
        json.dump(value, f, ensure_ascii=False, indent=2)
        f.write('\n')


def config():
    require(CONFIG.is_file() and CONFIG.stat().st_mode & 0o077 == 0, 'private qa-config required')
    value = json.loads(CONFIG.read_text())
    require(value['pgdata'] == str(PGDATA) and value['database'] == DATABASE
            and value['database_user'] == USER and value['database_port'] == DB_PORT
            and value['backend_port'] == BACKEND_PORT and value['frontend_port'] == FRONTEND_PORT,
            'unexpected QA identity')
    require((PGDATA / 'qa-owner-marker').read_text() == value['owner_marker'], 'QA ownership marker mismatch')
    return value


def free_port(port):
    with socket.socket() as sock:
        # A just-stopped owned server can leave TIME_WAIT sockets; a live listener
        # still prevents this bind. No SO_REUSEPORT and no other process is signalled.
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(('127.0.0.1', port))


def pg_environment(c):
    return {**os.environ, 'PGPASSWORD': c['database_password'], 'PGHOST': '127.0.0.1',
            'PGPORT': str(DB_PORT), 'PGUSER': USER, 'PGDATABASE': DATABASE,
            'PGCONNECT_TIMEOUT': '5'}


def sql(c, statement):
    # Every statement is bound to this fresh, independently owned local database.
    result = subprocess.run([str(PGBIN / 'psql'), '-X', '-v', 'ON_ERROR_STOP=1', '-At',
        '-h', '127.0.0.1', '-p', str(DB_PORT), '-U', USER, '-d', DATABASE],
        input=statement, env=pg_environment(c), check=True, capture_output=True, text=True)
    return result.stdout.strip()


def prepare():
    require(PRIVATE.is_dir() and PRIVATE.stat().st_mode & 0o077 == 0, 'existing private release directory required')
    require(not CONFIG.exists() and not PGDATA.exists(), 'new QA config/data directory required; never reuse another task')
    require(MENU.is_file(), 'approved public menu config missing')
    for port in (DB_PORT, BACKEND_PORT, FRONTEND_PORT):
        free_port(port)
    c = {'owner_marker': secrets.token_hex(24), 'pgdata': str(PGDATA),
         'database': DATABASE, 'database_user': USER, 'database_port': DB_PORT,
         'backend_port': BACKEND_PORT, 'frontend_port': FRONTEND_PORT,
         'database_password': secrets.token_urlsafe(36), 'jwt_secret': secrets.token_urlsafe(64),
         'owner_password': secrets.token_urlsafe(36), 'manager_password': secrets.token_urlsafe(36),
         'menu_path': str(MENU), 'menu_sha256': hashlib.sha256(MENU.read_bytes()).hexdigest(),
         'created_at_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}
    password_file = PRIVATE / 'qa-init-password'
    with password_file.open('x') as f:
        os.chmod(password_file, 0o600)
        f.write(c['database_password'])
    try:
        subprocess.run([str(PGBIN / 'initdb'), '-D', str(PGDATA), '-U', USER,
            '--pwfile', str(password_file), '--auth-local=scram-sha-256',
            '--auth-host=scram-sha-256', '--encoding=UTF8', '--locale=C'],
            check=True, capture_output=True, text=True)
    finally:
        password_file.unlink()
    (PGDATA / 'qa-owner-marker').write_text(c['owner_marker'])
    os.chmod(PGDATA / 'qa-owner-marker', 0o600)
    with (PGDATA / 'postgresql.conf').open('a') as f:
        f.write("\n# Isolated Allo QA: never bind LAN or share task sockets.\n"
                "listen_addresses = '127.0.0.1'\nport = 26512\nunix_socket_directories = ''\n"
                "max_connections = 20\nshared_buffers = '32MB'\n")
    write_private(CONFIG, c)
    log = PRIVATE / 'qa-postgres.log'
    log.touch(mode=0o600, exist_ok=False)
    subprocess.run([str(PGBIN / 'pg_ctl'), '-D', str(PGDATA), '-l', str(log), '-w', 'start'],
                   check=True, capture_output=True, text=True)
    subprocess.run([str(PGBIN / 'createdb'), '-h', '127.0.0.1', '-p', str(DB_PORT),
        '-U', USER, DATABASE], env=pg_environment(c), check=True, capture_output=True, text=True)
    require(sql(c, 'select current_database()') == DATABASE, 'database identity mismatch')
    require(sql(c, "select count(*) from information_schema.tables where table_schema='public'") == '0',
            'QA database must be fresh and empty')
    print(json.dumps({'result': 'PASS', 'database': DATABASE, 'host': '127.0.0.1',
        'port': DB_PORT, 'public_tables': 0, 'credentials_file': str(CONFIG)}))


def backend_environment(c, phase, build_sha):
    # Do not inherit Spring/external-platform/deployment configuration from the shell.
    env = {k: v for k, v in os.environ.items()
           if k in ('HOME', 'PATH', 'TMPDIR', 'LANG', 'LC_ALL', 'JAVA_HOME')}
    production_policy = phase == 'runtime'
    env.update({'SPRING_PROFILES_ACTIVE': 'allo-test,allo-qa', 'SERVER_ADDRESS': '127.0.0.1',
        'SERVER_PORT': str(BACKEND_PORT),
        'SPRING_DATASOURCE_URL': f'jdbc:postgresql://127.0.0.1:{DB_PORT}/{DATABASE}',
        'SPRING_DATASOURCE_USERNAME': USER, 'SPRING_DATASOURCE_PASSWORD': c['database_password'],
        'APP_AUTH_JWT_SECRET': c['jwt_secret'], 'APP_AUTH_X_USER_ID_FALLBACK_ENABLED': 'false',
        'APP_REALTIME_AUTHENTICATED_STOMP_ENABLED': 'true',
        'APP_ENVIRONMENT': 'production' if production_policy else 'test',
        'APP_BUILD_SHA': build_sha, 'APP_TEMPLATE_ENABLED': 'true', 'APP_TEMPLATE_MOCK_ONLY': 'true',
        'APP_TEMPLATE_TEST_BOOTSTRAP_ENABLED': 'false', 'APP_DAILY_CLOSE_ENABLED': 'false',
        'APP_DELIVERY_ALLO_BOOTSTRAP_ENABLED': 'false' if production_policy else 'true',
        'APP_DELIVERY_ALLO_PRODUCTION_PRINTING_ENABLED': 'true' if production_policy else 'false',
        'APP_PRINTING_ALLOWED_MODES': 'DISABLED,MOCK,PAD_DIRECT' if production_policy else 'MOCK,DISABLED',
        'APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED': 'true' if production_policy else 'false',
        'UBER_EATS_ENABLED': 'false', 'UBER_EATS_WORKER_ENABLED': 'false',
        'ALLO_MENU_FILE': str(MENU), 'ALLO_TEST_OWNER_PASSWORD': c['owner_password'],
        'ALLO_TEST_MANAGER_PASSWORD': c['manager_password']})
    for flag in ('RUNTIME', 'FORCE_OVERWRITE', 'SAFE_METADATA', 'DEFAULT_USERS',
                 'DEMO_DATA', 'MEMBERSHIP_SUPPLEMENT', 'PRODUCTION_BOOTSTRAP'):
        env['APP_SEED_' + flag + ('_ENABLED' if flag != 'FORCE_OVERWRITE' else '')] = 'false'
    return env


def start_backend(args):
    c = config()
    require(hashlib.sha256(MENU.read_bytes()).hexdigest() == c['menu_sha256'], 'approved public menu config changed')
    free_port(BACKEND_PORT)
    require(not (PRIVATE / 'qa-backend-process.json').exists(), 'stop the owned previous backend first')
    jar = Path(args.jar).resolve()
    require(jar.is_file() and hashlib.sha256(jar.read_bytes()).hexdigest() == args.sha256,
            'provided artifact digest mismatch')
    with zipfile.ZipFile(jar) as archive:
        safety = archive.read('BOOT-INF/classes/com/restaurant/system/template/bootstrap/TemplateTestRuntimeSafety.class')
        require(b'26512' in safety and b'allo-qa' in safety, 'old artifact: explicit local QA guard absent')
    if args.phase == 'bootstrap':
        require(sql(c, "select count(*) from information_schema.tables where table_schema='public'") == '0',
                'bootstrap is only allowed on empty synthetic DB')
    else:
        require(sql(c, "select count(*) from stores where code='ALLO_NOUILLES_TEST' and name='SYNTHETIC Allo QA' and printing_mode='MOCK'") == '1',
                'configured synthetic MOCK store required')
        require(sql(c, "select count(*) from printer_configs where printer_type <> 'MOCK' or nullif(ip_address,'') is not null") == '0',
                'QA must remain endpoint-free MOCK')
    log_path = PRIVATE / f'qa-backend-{args.phase}-{time.time_ns()}.log'
    with log_path.open('x') as log:
        os.chmod(log_path, 0o600)
        process = subprocess.Popen(['java', '-Xms64m', '-Xmx384m', '-jar', str(jar)],
            cwd=ROOT, env=backend_environment(c, args.phase, args.build_sha),
            stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    identity = subprocess.check_output(['ps', '-p', str(process.pid), '-o', 'lstart='], text=True).strip()
    write_private(PRIVATE / 'qa-backend-process.json', {'pid': process.pid, 'started': identity,
        'jar': str(jar), 'sha256': args.sha256, 'build_sha': args.build_sha, 'phase': args.phase,
        'log': str(log_path), 'backend_port': BACKEND_PORT})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    for _ in range(120):
        require(process.poll() is None, 'QA backend exited; inspect its private log')
        try:
            with opener.open(f'http://127.0.0.1:{BACKEND_PORT}/api/v1/system/health', timeout=2) as response:
                if response.status == 200:
                    print(json.dumps({'result': 'PASS', 'phase': args.phase, 'pid': process.pid,
                                      'sha256': args.sha256, 'log': str(log_path)}))
                    return
        except OSError:
            pass
        time.sleep(0.5)
    raise RuntimeError('QA backend health timeout; process retained for private diagnosis')


def stop_backend():
    config()
    path = PRIVATE / 'qa-backend-process.json'
    owned = json.loads(path.read_text())
    pid = owned['pid']
    result = subprocess.run(['ps', '-p', str(pid), '-o', 'lstart=', '-o', 'command='], capture_output=True, text=True)
    if result.returncode == 0:
        require(owned['started'] in result.stdout and owned['jar'] in result.stdout,
                'PID ownership changed; will not signal this process')
        os.kill(pid, signal.SIGTERM)
        for _ in range(60):
            if subprocess.run(['ps', '-p', str(pid)], capture_output=True).returncode:
                break
            time.sleep(0.25)
        else:
            raise RuntimeError('owned QA backend did not stop; no forced kill')
    path.rename(PRIVATE / f'qa-backend-stopped-{time.time_ns()}.json')
    print(json.dumps({'result': 'PASS', 'stopped_owned_pid': pid}))


def configure_fixture():
    c = config()
    require('frontdesk_password' not in c, 'synthetic Frontdesk fixture already prepared; never reset credentials')
    c['frontdesk_username'] = 'allo_frontdesk_qa'
    c['frontdesk_password'] = secrets.token_urlsafe(36)
    # System htpasswd supports bcrypt via stdin; neither plaintext nor hash is printed.
    hashed = subprocess.run(['/usr/sbin/htpasswd', '-niBC', '12', c['frontdesk_username']],
        input=c['frontdesk_password'] + '\n', check=True, capture_output=True, text=True)
    c['frontdesk_password_hash'] = hashed.stdout.strip().split(':', 1)[1]
    require(c['frontdesk_password_hash'].startswith(('$2a$', '$2b$', '$2y$')), 'BCrypt hash required')
    # This SQL can only touch the explicitly owned, fresh synthetic QA database.
    result = sql(c, """begin;
      do $$ begin
        if (select count(*) from stores) <> 2
          or (select count(*) from orders) <> 0
          or (select count(*) from stores where code='ALLO_NOUILLES_TEST' and printing_mode='MOCK') <> 1
        then raise exception 'not a fresh synthetic Allo QA fixture'; end if;
      end $$;
      update stores set name='SYNTHETIC Allo QA',
        presentation_json=jsonb_set(presentation_json::jsonb,'{display_name}','"SYNTHETIC Allo QA"'::jsonb)::text
        where code='ALLO_NOUILLES_TEST';
      update store_pricing_policies set category_bulk_pricing_enabled=true
        where store_id=(select id from stores where code='ALLO_NOUILLES_TEST');
      insert into roles(code,name,created_at,updated_at)
        values('FRONTDESK','SYNTHETIC QA Frontdesk',current_timestamp,current_timestamp);
      insert into users(store_id,role_id,username,full_name,status,created_at,updated_at)
        select s.id,r.id,'allo_frontdesk_qa','SYNTHETIC QA Frontdesk','active',current_timestamp,current_timestamp
        from stores s,roles r where s.code='ALLO_NOUILLES_TEST' and r.code='FRONTDESK';
      insert into user_credentials(user_id,login_identifier,password_hash,password_algorithm,is_active,
        password_updated_at,created_at,updated_at)
        select id,'allo_frontdesk_qa','""" + c['frontdesk_password_hash'] + """','BCRYPT',true,
          current_timestamp,current_timestamp,current_timestamp from users where username='allo_frontdesk_qa';
      insert into store_memberships(organization_id,store_id,user_id,role_id,role_code,is_active,created_at,updated_at)
        select s.organization_id,s.id,u.id,u.role_id,'FRONTDESK',true,current_timestamp,current_timestamp
        from users u join stores s on s.id=u.store_id where u.username='allo_frontdesk_qa';
      commit;
      select json_build_object('store_id',s.id,'store_code',s.code,'display_name',s.name,
        'printing_mode',s.printing_mode,'category_bulk_pricing_enabled',p.category_bulk_pricing_enabled)
        from stores s join store_pricing_policies p on p.store_id=s.id where s.code='ALLO_NOUILLES_TEST';""")
    pending_config = PRIVATE / f'qa-config-frontdesk-{time.time_ns()}.json'
    write_private(pending_config, c)
    os.replace(pending_config, CONFIG)
    write_private(PRIVATE / 'qa-bootstrap-fixture.json', {'result': 'PASS', 'sql_output': result,
        'only_synthetic_data': True, 'menu_sha256': c['menu_sha256']})
    print(result.splitlines()[-1])


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    sub.add_parser('prepare')
    sub.add_parser('configure-fixture')
    sub.add_parser('stop-backend')
    launch = sub.add_parser('start-backend')
    launch.add_argument('--jar', required=True)
    launch.add_argument('--sha256', required=True)
    launch.add_argument('--build-sha', required=True)
    launch.add_argument('--phase', choices=('bootstrap', 'runtime'), required=True)
    args = parser.parse_args()
    {'prepare': prepare, 'configure-fixture': configure_fixture, 'stop-backend': stop_backend,
     'start-backend': lambda: start_backend(args)}[args.action]()


if __name__ == '__main__':
    main()
