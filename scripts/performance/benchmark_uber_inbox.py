#!/usr/bin/env python3
"""Synthetic-only Inbox SQL benchmark. Starts its own disposable loopback PostgreSQL.
No existing database URL, credentials, Production files, or Uber connection is accepted.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import resource
import re
import textwrap
import socket
import statistics
import subprocess
import tempfile
import time

try:
    import psutil
except ImportError:
    psutil = None


class Session:
    def __init__(self, binary, port):
        self.process = subprocess.Popen([str(binary / 'psql'), '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
            '-h', '127.0.0.1', '-p', str(port), '-d', 'inbox_bench_synthetic'],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
        self.serial = 0
        self.backend_pid = self.query('SELECT pg_backend_pid()')

    def query(self, sql):
        self.serial += 1
        marker = 'INBOX_BENCH_END_' + str(self.serial)
        self.process.stdin.write(sql.rstrip(';') + ';\n\\echo ' + marker + '\n')
        self.process.stdin.flush()
        result = []
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError('Synthetic psql session failed')
            if line.strip() == marker:
                break
            result.append(line)
        value = ''.join(result).strip()
        return json.loads(value) if value else None

    def close(self):
        if self.process.poll() is None:
            try:
                self.process.stdin.write('\\q\n')
                self.process.stdin.flush()
            except BrokenPipeError:
                pass
        self.process.wait(timeout=10)


def percentile(values, p):
    ordered = sorted(values)
    return ordered[min(len(ordered)-1, max(0, int(len(ordered)*p + .999999)-1))]


def plan(session, query):
    result = session.query('EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON) ' + query)[0]
    root = result['Plan']
    return {'planning_ms': result['Planning Time'], 'execution_ms': result['Execution Time'],
        'shared_hit_blocks': root.get('Shared Hit Blocks', 0),
        'shared_read_blocks': root.get('Shared Read Blocks', 0),
        'plan': result}


def cpu_seconds(pid):
    if psutil is None:
        return None
    value = psutil.Process(pid).cpu_times()
    return value.user + value.system


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pg-bin', default='/opt/homebrew/opt/postgresql@16/bin')
    parser.add_argument('--port', type=int, default=55486)
    parser.add_argument('--rows', type=int, default=40000)
    parser.add_argument('--orders', type=int, default=200)
    parser.add_argument('--repetitions', type=int, default=30)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.rows < 40000 or not 1 <= args.orders <= 200 or args.repetitions < 20:
        parser.error('Require >=40000 synthetic jobs, 1..200 inbox rows, >=20 repetitions')
    binaries = Path(args.pg_bin)
    for name in ('initdb', 'pg_ctl', 'createdb', 'psql'):
        if not (binaries / name).is_file():
            parser.error('Missing local PostgreSQL executable: ' + name)
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', args.port))
    results = {'kind': 'SYNTHETIC_ONLY_NOT_PRODUCTION', 'rows': args.rows, 'inbox_orders': args.orders,
        'repetitions': args.repetitions, 'warmup_repetitions': 3, 'measurements': []}
    with tempfile.TemporaryDirectory(prefix='inbox-bench-synthetic-') as directory:
        data = Path(directory) / 'data'
        subprocess.run([str(binaries/'initdb'), '-D', str(data), '--auth=trust', '--no-locale', '-E', 'UTF8'],
            check=True, stdout=subprocess.DEVNULL)
        subprocess.run([str(binaries/'pg_ctl'), '-D', str(data), '-o', f'-h 127.0.0.1 -p {args.port} -k {directory}',
            '-l', str(Path(directory)/'server.log'), '-w', 'start'], check=True, stdout=subprocess.DEVNULL)
        session = None
        try:
            subprocess.run([str(binaries/'createdb'), '-h', '127.0.0.1', '-p', str(args.port), 'inbox_bench_synthetic'], check=True)
            session = Session(binaries, args.port)
            # Large ticket columns remain external; inline metadata makes row scanning realistic.
            session.query('''CREATE TABLE print_jobs(id bigint primary key,store_id bigint,order_id bigint,
                module_code text,status text,created_at timestamp,padding text,payload_snapshot text,
                rendered_text_snapshot text,escpos_payload_base64 text)''')
            for field in ('payload_snapshot', 'rendered_text_snapshot', 'escpos_payload_base64'):
                session.query(f'ALTER TABLE print_jobs ALTER COLUMN {field} SET STORAGE EXTERNAL')
            session.query('ALTER TABLE print_jobs ALTER COLUMN padding SET STORAGE PLAIN')
            session.query(f'''INSERT INTO print_jobs SELECT g,1,(g-1)/4+1,
                CASE WHEN ((g-1)/4+1)%7=0 AND g%4<2 THEN 'FRONTDESK_RECEIPT'
                     WHEN ((g-1)/4+1)%5=0 AND g%4>=2 THEN 'FRONTDESK_RECEIPT'
                     WHEN g%4<2 THEN 'GRAB' ELSE 'HOT_KITCHEN' END,
                CASE WHEN g%9=0 THEN 'FAILED' ELSE 'PRINTED' END,
                timestamp '2026-11-03 12:00:00'+g*interval '1 second',
                repeat(md5(g::text),24),repeat(md5(g::text),128),repeat(md5((g+1)::text),128),repeat(md5((g+2)::text),128)
                FROM generate_series(1,{args.rows}) g''')
            session.query('''CREATE INDEX idx_print_jobs_active_order_module ON print_jobs(store_id,order_id,module_code,id)
                WHERE status IN ('PENDING','CLAIMED','PRINTING')''')
            session.query('CREATE TABLE order_dispatch_outbox(store_id bigint,source_key text unique,status text)')
            session.query(f'''INSERT INTO order_dispatch_outbox SELECT 1,'submit:'||g||':'||m,'PENDING'
                FROM generate_series(1,{args.rows//4}) g CROSS JOIN (VALUES('GRAB'),('HOT_KITCHEN')) modules(m)''')
            session.query('ANALYZE print_jobs')
            session.query('ANALYZE order_dispatch_outbox')
            results['server_version'] = session.query('SELECT to_json(version())')
            results['print_jobs_physical_bytes'] = session.query("SELECT pg_total_relation_size('print_jobs')")
            ids = ','.join(str(n) for n in range(1,args.orders+1))
            batch = f'''SELECT order_id,module_code,status FROM (
                SELECT order_id,module_code,status,row_number() OVER(PARTITION BY order_id,module_code ORDER BY created_at DESC,id DESC) AS position
                FROM print_jobs WHERE store_id=1 AND order_id IN ({ids}) AND module_code IN ('GRAB','HOT_KITCHEN')
                ) latest WHERE position=1'''
            original_queries = []
            def original():
                result = {}; count = 0
                for order in range(1,args.orders+1):
                    query = f'SELECT * FROM print_jobs WHERE store_id=1 AND order_id={order} ORDER BY created_at DESC,id DESC'
                    if len(original_queries)<args.orders:
                        original_queries.append(query)
                    rows = session.query('SELECT coalesce(json_agg(t),\'[]\'::json) FROM ('+query+') t')
                    count += 1
                    for module in ('GRAB','HOT_KITCHEN'):
                        latest = next((r for r in rows if r['module_code']==module), None)
                        if latest:
                            status = latest['status']
                        else:
                            events = session.query(f"SELECT coalesce(json_agg(t),'[]'::json) FROM (SELECT status FROM order_dispatch_outbox WHERE source_key='submit:{order}:{module}') t")
                            count += 1
                            status = events[0]['status'] if events else ('NOT_REQUIRED' if module=='HOT_KITCHEN' else 'WAITING')
                        result[f'{order}:{module}'] = status
                return result,count
            def batched():
                rows=session.query("SELECT coalesce(json_agg(t),'[]'::json) FROM ("+batch+") t")
                result={f"{r['order_id']}:{r['module_code']}":r['status'] for r in rows}
                missing=[f'submit:{order}:{module}' for order in range(1,args.orders+1) for module in ('GRAB','HOT_KITCHEN') if f'{order}:{module}' not in result]
                count=1
                if missing:
                    keys=','.join("'"+key+"'" for key in missing)
                    events=session.query("SELECT coalesce(json_agg(t),'[]'::json) FROM (SELECT source_key,status FROM order_dispatch_outbox WHERE store_id=1 AND source_key IN ("+keys+")) t")
                    count+=1
                    values={r['source_key']:r['status'] for r in events}
                    for key in missing:
                        result[key.removeprefix('submit:')]=values.get(key,'NOT_REQUIRED' if key.endswith(':HOT_KITCHEN') else 'WAITING')
                return result,count
            expected,old_count=original()
            assert batched()[0]==expected
            for label,operation in [('original_n_plus_one',original),('batch_no_index',batched),('batch_with_index',batched)]:
                if label=='batch_with_index':
                    session.query('CREATE INDEX idx_print_jobs_inbox_order_created ON print_jobs(store_id,order_id,created_at DESC,id DESC)')
                    session.query('ANALYZE print_jobs')
                for _ in range(3):
                    assert operation()[0]==expected
                before_cpu=cpu_seconds(session.backend_pid)
                before_client=resource.getrusage(resource.RUSAGE_SELF)
                wall=[]
                for _ in range(args.repetitions):
                    start=time.perf_counter(); actual,count=operation(); wall.append((time.perf_counter()-start)*1000)
                    assert actual==expected
                after_cpu=cpu_seconds(session.backend_pid)
                after_client=resource.getrusage(resource.RUSAGE_SELF)
                row={'variant':label,'sql_per_projection':count,'p50_ms':statistics.median(wall),'p95_ms':percentile(wall,.95),
                    'server_backend_cpu_seconds':None if before_cpu is None else after_cpu-before_cpu,
                    'client_python_cpu_seconds':(after_client.ru_utime+after_client.ru_stime)-(before_client.ru_utime+before_client.ru_stime),
                    'cpu_method':'psutil backend process user+system delta; Python getrusage separately' if psutil else 'Backend CPU UNKNOWN; Python getrusage only',
                    'result_sha256':hashlib.sha256(json.dumps(actual,sort_keys=True).encode()).hexdigest()}
                queries=original_queries if label=='original_n_plus_one' else [batch]
                plans=[plan(session,q) for q in queries]
                row['print_query_buffers_per_projection']={key:sum(p[key] for p in plans) for key in ['shared_hit_blocks','shared_read_blocks']}
                row['representative_print_plan']=plans[0]['plan']
                row['sum_print_execution_ms']=sum(p['execution_ms'] for p in plans)
                results['measurements'].append(row)
                print(json.dumps({k:v for k,v in row.items() if 'plan' not in k}),flush=True)
            # Measure the complete new grouped read path too, including its attention scan.
            # SQL text blocks come from the real read repository, keeping the benchmark in sync.
            source=Path('backend/src/main/java/com/restaurant/system/integration/ubereats/repository/UberEatsInboxReadRepository.java').read_text()
            fragments={name:textwrap.dedent(value).strip() for name,value in re.findall(r'private static final String (\w+) = """(.*?)""";',source,re.S)}
            results['read_sql_fragments_sha256']=hashlib.sha256(json.dumps(fragments,sort_keys=True).encode()).hexdigest()
            session.query('CREATE TABLE orders(id bigint primary key,store_id bigint,status text)')
            session.query(f"INSERT INTO orders SELECT g,1,CASE WHEN g%17=0 THEN 'preparing' ELSE 'completed' END FROM generate_series(1,{args.rows//4}) g")
            session.query("""CREATE TABLE uber_eats_orders(id bigint primary key,environment text,store_id bigint,status text,
                display_id text,uber_order_id text,mapping_status text,mapping_error text,last_error text,local_order_id bigint,
                placed_at timestamp,created_at timestamp,accepted_at timestamp,cancelled_at timestamp,raw_order_snapshot_json text,
                processing_mode text,customer_display_name text,released_at timestamp,accepted_observed_at timestamp,
                local_request_json text,kitchen_dispatched_at timestamp,cancelled boolean,edit_required boolean)""")
            session.query(f"""INSERT INTO uber_eats_orders(id,environment,store_id,status,local_order_id,placed_at,created_at,
                processing_mode,cancelled,edit_required)
                SELECT g,'sandbox',1,CASE WHEN g%97=0 THEN 'EDIT_REVIEW_REQUIRED' ELSE 'RELEASED_TO_KITCHEN' END,g,
                CASE WHEN g<={args.orders} THEN timestamp '2026-11-03 15:00:00' ELSE timestamp '2026-11-01 15:00:00' END,
                timestamp '2026-11-03 12:00:00','KITCHEN_MIRROR',false,false FROM generate_series(1,{args.rows//4}) g""")
            session.query('CREATE INDEX idx_uber_inbox ON uber_eats_orders(environment,store_id,id)')
            session.query('ANALYZE uber_eats_orders'); session.query('ANALYZE orders')
            parameters={'env':"'sandbox'",'store':'1','start':"timestamp '2026-11-03 05:00:00'",
                'end':"timestamp '2026-11-04 05:00:00'",'createdStart':"timestamp '2026-11-03 05:00:00'",'createdEnd':"timestamp '2026-11-04 05:00:00'"}
            def bind(sql):
                return re.sub(r':(env|store|start|end|createdStart|createdEnd)\b',lambda match:parameters[match.group(1)],sql)
            where=" FROM uber_eats_orders o WHERE o.environment=:env AND o.store_id=:store AND "
            today_query=bind('SELECT '+fragments['COLUMNS']+where+fragments['ON_DAY']+' ORDER BY o.id DESC LIMIT 101')
            attention_predicate='('+fragments['WARNINGS']+' OR '+fragments['PRINT_EXCEPTION']+' OR (('+fragments['ON_DAY']+') IS NOT TRUE AND '+fragments['UNFINISHED']+'))'
            attention_from=" FROM uber_eats_orders o "+fragments['ATTENTION_JOINS']+" WHERE o.environment=:env AND o.store_id=:store AND "
            attention_query=bind(fragments['LATEST_JOBS']+' SELECT '+fragments['COLUMNS']+attention_from+attention_predicate+' ORDER BY o.id DESC LIMIT 101')
            grouped_queries=[]
            def grouped():
                grouped_queries.clear()
                grouped_queries.extend([today_query,attention_query])
                pages=[session.query("SELECT coalesce(json_agg(t),'[]'::json) FROM ("+query+") t") for query in grouped_queries[:2]]
                selected=[row for page in pages for row in page[:100]]
                order_ids=sorted({row['local_order_id'] for row in selected if row['local_order_id'] is not None})
                state={}
                if order_ids:
                    selected_batch=batch.replace('order_id IN ('+ids+')','order_id IN ('+','.join(map(str,order_ids))+')')
                    grouped_queries.append(selected_batch)
                    jobs=session.query("SELECT coalesce(json_agg(t),'[]'::json) FROM ("+selected_batch+") t")
                    state={f"{row['order_id']}:{row['module_code']}":row['status'] for row in jobs}
                    missing=[f'submit:{order}:{module}' for order in order_ids for module in ('GRAB','HOT_KITCHEN') if f'{order}:{module}' not in state]
                    if missing:
                        missing_keys=','.join("'"+key+"'" for key in missing)
                        fallback="SELECT source_key,status FROM order_dispatch_outbox WHERE store_id=1 AND source_key IN ("+missing_keys+")"
                        grouped_queries.append(fallback)
                        events=session.query("SELECT coalesce(json_agg(t),'[]'::json) FROM ("+fallback+") t")
                        found={row['source_key']:row['status'] for row in events}
                        for key in missing:state[key.removeprefix('submit:')]=found.get(key,'NOT_REQUIRED' if key.endswith(':HOT_KITCHEN') else 'WAITING')
                return {'today_ids':[row['id'] for row in pages[0][:100]],'attention_ids':[row['id'] for row in pages[1][:100]],
                    'today_has_more':len(pages[0])>100,'attention_has_more':len(pages[1])>100,'states':state},len(grouped_queries)
            grouped_expected=grouped()[0]
            for label in ['grouped_inbox_no_index','grouped_inbox_with_index']:
                if label.endswith('no_index'):
                    session.query('DROP INDEX idx_print_jobs_inbox_order_created')
                else:
                    session.query('CREATE INDEX idx_print_jobs_inbox_order_created ON print_jobs(store_id,order_id,created_at DESC,id DESC)')
                for _ in range(3):assert grouped()[0]==grouped_expected
                before_cpu=cpu_seconds(session.backend_pid); before_client=resource.getrusage(resource.RUSAGE_SELF); wall=[]
                for _ in range(args.repetitions):
                    start=time.perf_counter(); actual,count=grouped(); wall.append((time.perf_counter()-start)*1000);assert actual==grouped_expected
                after_cpu=cpu_seconds(session.backend_pid);after_client=resource.getrusage(resource.RUSAGE_SELF)
                plans=[plan(session,query) for query in grouped_queries]
                measurement={'variant':label,'sql_per_grouped_inbox_excluding_timezone_and_authorization':count,
                    'fixed_timezone_lookup_extra_sql':1,'p50_ms':statistics.median(wall),'p95_ms':percentile(wall,.95),
                    'server_backend_cpu_seconds':None if before_cpu is None else after_cpu-before_cpu,
                    'client_python_cpu_seconds':(after_client.ru_utime+after_client.ru_stime)-(before_client.ru_utime+before_client.ru_stime),
                    'all_read_query_buffers_per_request':{key:sum(item[key] for item in plans) for key in ['shared_hit_blocks','shared_read_blocks']},
                    'query_plans':[item['plan'] for item in plans],
                    'result_sha256':hashlib.sha256(json.dumps(actual,sort_keys=True).encode()).hexdigest()}
                results['measurements'].append(measurement)
                print(json.dumps({key:value for key,value in measurement.items() if 'plans' not in key}),flush=True)
            results['grouped_result_consistency']='PASS: source SQL fragments, both index variants, every repetition; includes attention latest-job scan'
            results['result_consistency']='PASS: every repetition, all three variants, exact module status equality'
            results['limitations']=['Warm loopback synthetic database, not a Production capacity prediction.',
                'P50/P95 include persistent-connection SQL round trips, JSON serialization, ticket transfer and client projection.',
                'Buffer counts above are print queries only; Outbox fallback query counts are included in SQL totals.',
                'Only one backend process CPU is measured; Python client CPU is separate.',
                'Dataset contains compressible synthetic ticket bytes stored externally, not real receipt content.']
            args.output.parent.mkdir(parents=True,exist_ok=True)
            args.output.write_text(json.dumps(results,indent=2)+'\n')
        finally:
            try:
                if session is not None:
                    session.close()
            finally:
                subprocess.run([str(binaries/'pg_ctl'),'-D',str(data),'-m','fast','-w','stop'],check=True,stdout=subprocess.DEVNULL)

if __name__=='__main__':
    main()
