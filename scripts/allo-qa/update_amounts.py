#!/usr/bin/env python3
"""API-only Allô update-money regression against the explicitly approved new QA JAR.

No runtime launch, price/configuration mutation, remote target or physical printing.
Only orders created by this invocation can be submitted, appended to or Finished.
"""
import argparse
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import zipfile

import five_orders
import runtime

REPORT = runtime.ROOT / 'docs/commercialization/merchants/allo-nouille/UPDATE_RECEIPT_AMOUNT_REGRESSION.md'
LEGACY = runtime.PRIVATE / 'qa-addon-cleanup-1794472752532852000.json'
CENT = Decimal('0.01')
RATE = Decimal('0.14975')


def cents(value):
    return Decimal(str(value)).quantize(CENT, rounding=ROUND_HALF_UP)


def total(subtotal):
    return cents(subtotal) + (cents(subtotal) * RATE).quantize(CENT, rounding=ROUND_HALF_UP)


def money(value):
    return format(cents(value), '.2f')


def digest(value):
    raw = json.dumps(five_orders.json_safe(value), ensure_ascii=False, sort_keys=True, separators=(',', ':'))
    return hashlib.sha256(raw.encode()).hexdigest()


class ReadOnlyDatabase:
    def __init__(self, config):
        self.env = runtime.pg_environment(config)
        self.env['PGOPTIONS'] = '-c default_transaction_read_only=on'

    def read(self, query):
        runtime.require(query.lstrip().lower().startswith('select '), 'only read-only QA SELECT allowed')
        response = subprocess.run([str(runtime.PGBIN / 'psql'), '-X', '-q', '-A', '-t',
            '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-p', '26512', '-U', 'allo_test',
            '-d', 'restaurant_allo_nouilles_test'], input=query, env=self.env,
            check=True, capture_output=True, text=True)
        return json.loads(response.stdout, parse_float=Decimal)

    def rows(self, table, condition='true'):
        return self.read(f"select coalesce(json_agg(to_jsonb(x) order by id),'[]'::json) from {table} x where {condition}")

    def historical(self):
        # Includes protocol Order 1 too, so this also matches the existing full-row
        # cleanup baseline, which was captured while browser Order 8 was preparing.
        return {'orders': self.rows('orders', 'id<=8'),
            'order_items': self.rows('order_items', 'order_id<=8'),
            'order_item_options': self.rows('order_item_options',
                'order_item_id in(select id from order_items where order_id<=8)'),
            'print_jobs': self.rows('print_jobs', 'id<=18'),
            'outbox': self.rows('order_dispatch_outbox', 'order_id<=8')}

    def menus(self):
        return {table: self.rows(table) for table in ('menu_items', 'menu_item_options',
                                                     'store_addons', 'store_pricing_policies')}


def assert_order(order, subtotal):
    runtime.require(cents(order['subtotal_amount']) == cents(subtotal)
        and cents(order['total_amount']) == total(subtotal), 'order amount differs from independent approved-price calculation')


def rendered_amounts(text, language):
    labels = {'zh-CN': ('小计', '税', '合计'), 'en': ('Subtotal', 'Tax', 'Total'),
              'fr-CA': ('Sous-total', 'Taxes', 'Total')}
    runtime.require(language in labels, 'supported captured receipt locale required')
    plain = re.sub(r'\[\[[^\]]+\]\]', '', text)
    results = []
    for label in labels[language]:
        # Preserve the actual new order-rounding tax label in evidence. Amount
        # assertions remain exact, independent of its translated explanatory text.
        matches = [line for line in plain.splitlines()
                   if re.fullmatch(re.escape(label) + r'(?:[ (:（].*)?: \$\d+\.\d{2}', line)]
        runtime.require(len(matches) == 1, f'one actual receipt amount line required for {label}')
        results.append({'line': matches[0], 'amount': matches[0].rsplit('$', 1)[1]})
    return {'subtotal': results[0], 'tax': results[1], 'total': results[2]}


def settled_jobs(api, order_id, source_keys):
    for _ in range(80):
        jobs = api.call(f'/orders/{order_id}/print-jobs')
        if len(jobs) > len(source_keys) or any(j['status'] in ('FAILED', 'CANCELLED') for j in jobs):
            raise RuntimeError('unexpected duplicate/failed job: ' + str([(j['id'], j['status'], j.get('error_code')) for j in jobs]))
        if len(jobs) == len(source_keys) and all(j['status'] == 'PRINTED' for j in jobs):
            return jobs
        time.sleep(0.25)
    raise RuntimeError('actual MOCK print jobs did not settle')


def capture_jobs(db, order_id, expected_keys):
    jobs = db.rows('print_jobs', f'order_id={int(order_id)}')
    runtime.require(len(jobs) == len(expected_keys) and
        {j['dispatch_source_key'] for j in jobs} == set(expected_keys), 'persisted job source-key uniqueness mismatch')
    records = []
    for job in jobs:
        payload = json.loads(job['payload_snapshot'], parse_float=Decimal)
        context, artifact = payload['receipt_context'], payload['receipt_artifact']
        text_hash = hashlib.sha256(job['rendered_text_snapshot'].encode()).hexdigest()
        runtime.require(job['status'] == 'PRINTED' and job['execution_mode'] in (None, 'MOCK')
            and context['execution_mode'] == 'MOCK'
            and artifact['transport'] == 'MOCK_ONLY_NOT_PHYSICAL_PROOF'
            and artifact['text_sha256'] == text_hash, 'actual saved MOCK artifact required')
        records.append({'job_id': job['id'], 'module': job['module_code'], 'receipt_type': job['receipt_type'],
            'batch_id': job['order_update_batch_id'], 'source_key': job['dispatch_source_key'],
            'text_sha256': text_hash, 'row_sha256': digest(job), 'receipt_context': context,
            'artifact': artifact, 'rendered_text_snapshot': job['rendered_text_snapshot']})
    return records


def assert_receipt(job, expected_subtotal, expected_total, update):
    context = job['receipt_context']
    actual = rendered_amounts(job['rendered_text_snapshot'], context['language'])
    expected_tax = cents(expected_total) - cents(expected_subtotal)
    runtime.require(actual['subtotal']['amount'] == money(expected_subtotal)
        and actual['tax']['amount'] == money(expected_tax)
        and actual['total']['amount'] == money(expected_total), 'saved receipt amount differs from expected rounded-order delta')
    if update:
        runtime.require(context.get('update_subtotal') is not None and context.get('update_total') is not None
            and cents(context['update_subtotal']) == cents(expected_subtotal)
            and cents(context['update_total']) == cents(expected_total), 'server-frozen update amounts absent or wrong')
    job['verified_amount_lines'] = actual


def markdown(report):
    lines = ['# Allô 加单收据金额回归', '', '**PASS — 新建合成本机订单的实际 HTTP / Renderer 验收。**', '',
        '本报告为 API 验收，不代表浏览器 Finish、Print Center UI 或实体打印通过。'
        '没有重写原 Order 8 或旧 Job 18；旧加单收据 `$6.88` 原样保留。', '',
        f'时间：`{report["started_at_utc"]}`；build：`{report["build_sha"]}`；JAR SHA-256：`{report["jar_sha256"]}`。', '',
        '目标仅 `127.0.0.1:18512` / `127.0.0.1:26512/restaurant_allo_nouilles_test`，'
        'Store `ALLO_NOUILLES_TEST / SYNTHETIC Allo QA`，MOCK、无 endpoint。'
        '使用正常 QA Owner Bearer 登录；无生产、菜单改价/改名、路由写入、Pad Worker 或物理打印。', '',
        '预期金额从批准菜单独立用 Decimal 计算。每次加单应收额为'
        '`round_order_total(new_subtotal) - round_order_total(previous_subtotal)`；'
        '该批税额为应收额减新增小计。新 context 的 `update_subtotal/update_total` 与实际收据逐项核对。', '',
        '| 场景 | 新订单 | 初单总计 | 新增加小计 / 税 / 应收 | 当前整单总计 | 旧独立计税应收 | Finish |',
        '| --- | --- | --- | --- | --- | --- | --- |']
    for case in report['cases']:
        update = case['updates'][0]
        lines.append(f'| {case["name"]} | {case["order_id"]} | {case["initial_total"]} | '
            f'{update["subtotal"]} / {update["tax"]} / {update["total"]} | {case["final_total"]} | '
            f'{update["old_independent_total"]} | {case["finished_status"]} |')
    lines += ['', '所有 update 使用稳定 idempotency_key，重复同一请求返回原 batch/revision、'
              '`already_processed=true`，未新增订单行、batch、Outbox 或重复 Job。'
              '每批实际保留一张 GRAB 与一张 FRONTDESK_RECEIPT。', '']
    for case in report['cases']:
        lines += ['## ' + case['name'], '', '```json',
                  json.dumps(five_orders.json_safe({k: v for k, v in case.items() if k != 'jobs'}), ensure_ascii=False, indent=2), '```']
        for job in case['jobs']:
            lines += ['', f'### Job {job["job_id"]} / {job["receipt_type"]}', '',
                f'`source_key={job["source_key"]}`，SHA-256 `{job["text_sha256"]}`。', '',
                '冻结 context（实际保存值）：', '', '```json',
                json.dumps(five_orders.json_safe(job['receipt_context']), ensure_ascii=False, indent=2), '```', '',
                '实际 `rendered_text_snapshot` 原文：', '', '```text', job['rendered_text_snapshot'], '```']
    lines += ['', '## 原历史和菜单保护', '',
        '启动前将旧数据全行 hash 与既有 `qa-addon-cleanup-1794472752532852000.json` '
        '比较；测试后再次比较。涵盖 Orders 1–8（包含要求的 2–8）、其订单行/选项、'
        'PrintJobs 1–18 和对应 Outbox。全部保持相同；原 Job 18 仍保存 `$6.88`，'
        '其文本、payload、bytes 与状态都没有被新实现重写。', '', '```json',
        json.dumps(report['historical_protection'], ensure_ascii=False, indent=2), '```', '',
        '全部 menu_items、menu_item_options、store_addons、store_pricing_policies 前后全行 hash 相同。'
        '只对本脚本新建的订单执行 Finish；Order 8 保持原状态。硬件打印 **NOT TESTED**。', '']
    if not report['with_refill']:
        lines += ['零价合格续面未启用：NOT RUN。', '']
    return '\n'.join(lines)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true', required=True)
    parser.add_argument('--build-sha', required=True)
    parser.add_argument('--jar-sha256', required=True)
    parser.add_argument('--with-refill', action='store_true')
    parser.add_argument('--write-report', action='store_true')
    args = parser.parse_args()
    runtime.require(re.fullmatch('[0-9a-f]{64}', args.jar_sha256), 'exact approved new artifact digest required')
    c = runtime.config()
    process = json.loads((runtime.PRIVATE / 'qa-backend-process.json').read_text())
    jar = Path(process['jar'])
    runtime.require(process['phase'] == 'runtime' and process['build_sha'] == args.build_sha
        and process['sha256'] == args.jar_sha256 and hashlib.sha256(jar.read_bytes()).hexdigest() == args.jar_sha256,
        'approved new backend must already be running; this script never restarts it')
    with zipfile.ZipFile(jar) as archive:
        presentation = archive.read('BOOT-INF/classes/com/restaurant/system/printing/dto/ReceiptPresentation.class')
        runtime.require(b'update_subtotal' in presentation and b'update_total' in presentation,
                        'old backend artifact cannot run this acceptance')
    runtime.require(not args.write_report or not REPORT.exists(), 'existing regression report is protected')
    db = ReadOnlyDatabase(c)
    identity = db.read("select json_build_object('database',current_database(),'host',inet_server_addr(),'port',inet_server_port(),'store',(select row_to_json(s) from(select id,code,name,printing_mode from stores where id=1)s))")
    runtime.require(identity == {'database': runtime.DATABASE, 'host': '127.0.0.1', 'port': 26512,
        'store': {'id': 1, 'code': 'ALLO_NOUILLES_TEST', 'name': 'SYNTHETIC Allo QA', 'printing_mode': 'MOCK'}}, 'exact synthetic MOCK QA identity required')
    runtime.require(db.read("select count(*) from printer_configs where printer_type<>'MOCK' or nullif(ip_address,'') is not null") == 0,
                    'endpoint-free MOCK printers required')
    old = json.loads(LEGACY.read_text())
    before = db.historical()
    before_menus = db.menus()
    for key, value in before.items():
        runtime.require(digest(value) == old['unchanged'][key]['after_sha256'], 'historical baseline changed before test: ' + key)
    legacy_job18 = next(j for j in before['print_jobs'] if j['id'] == 18)
    runtime.require('合计: $6.88' in legacy_job18['rendered_text_snapshot'], 'original Job 18 must remain its saved old evidence')
    run_id = str(time.time_ns())
    evidence = runtime.PRIVATE / f'qa-update-amounts-{run_id}.json'
    api = five_orders.Api()
    report = {'result': 'FAIL', 'started_at_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'build_sha': args.build_sha, 'jar_sha256': args.jar_sha256, 'target': five_orders.BASE,
        'with_refill': args.with_refill, 'physical_printing': 'NOT TESTED', 'cases': [], 'owned_order_ids': []}
    try:
        login = api.call('/auth/login', 'POST', {'login_identifier': 'allo_owner', 'password': c['owner_password']})
        api.token = login['access_token']
        runtime.require(login['user']['role_code'] == 'OWNER' and login['user']['store_id'] == 1, 'normal same-Store Owner authentication required')
        safety = api.call('/stores/1/template/runtime-safety')
        runtime.require(safety['build_sha'] == args.build_sha and safety['environment'] == 'production', 'live QA build identity mismatch')
        settings = api.call('/stores/1/receipt-presentation')
        runtime.require(settings['enabled'] and settings['kitchen_station_codes'] == ['BACK_KITCHEN']
            and not any((settings.get('kitchen_category_enabled') or {}).get(code) is False for code in ('APPETIZER','NOODLE','DESSERT')),
            'existing kitchen route must allow the acceptance items; script will not change settings')
        catalog = api.call('/menu/catalog?store_id=1')
        menu = {item['sku']: item for category in catalog['categories'] for item in category['items']}
        for sku, expected in [('AN_A1','4.99'),('AN_E1','3.99'),('AN_B1','16.99')]:
            runtime.require(cents(menu[sku]['base_price']) == Decimal(expected), 'approved fixture price changed: ' + sku)
        thin = five_orders.option(menu['AN_B1'], 'choice_thin')
        thick = five_orders.option(menu['AN_B1'], 'choice_thick')
        runtime.require(cents(thin['price_delta']) == 0 and cents(thick['price_delta']) == 0, 'approved noodle selection must be zero price')
        table = db.read("select row_to_json(x) from(select table_code,table_name from dining_tables where store_id=1 and table_code='TEST-3' and is_active)x")
        runtime.require(table and table['table_name'] != 'TEST 1', 'existing separate synthetic table required')
        runtime.require(db.read("select count(*) from orders where store_id=1 and table_no in('TEST-3','TEST 3') and coalesce(status,'') not in('completed','cancelled')") == 0,
                        'separate QA table is already occupied; never reuse another order')
        if args.with_refill:
            refill = menu['AN_REFILL_NOODLES']
            runtime.require(refill['refill_category_code'] == 'NOODLE' and cents(refill['base_price']) == 0,
                            'existing approved zero-price noodle refill required')
        plans = [('A1 takeout → E1', 'takeout', 'AN_A1', None, [('AN_E1', None, Decimal('3.99'))], Decimal('4.99')),
                 ('B1 dine-in → B1' + (' → 合格零价续面' if args.with_refill else ''), 'dine_in', 'AN_B1', thin,
                  [('AN_B1', thick, Decimal('16.99'))] + ([('AN_REFILL_NOODLES', None, Decimal('0.00'))] if args.with_refill else []), Decimal('16.99'))]
        for index, (name, kind, initial_sku, initial_option, additions, subtotal) in enumerate(plans, 1):
            def request_item(sku, leaf):
                return {'menu_item_id': menu[sku]['id'], 'quantity': 1, 'combo_role': 'standalone',
                    'notes': 'SYNTHETIC_UPDATE_AMOUNTS_' + run_id,
                    'options': [] if leaf is None else [{'option_id': leaf['id'], 'quantity': 1}]}
            maximum_id = db.read('select coalesce(max(id),0) from orders')
            body = {'store_id': 1, 'order_type': kind, 'items': [request_item(initial_sku, initial_option)]}
            if kind == 'takeout':
                body['pickup_no'] = 'AMT-' + run_id[-12:]
            else:
                body['table_no'] = table['table_name']
            draft = api.call('/orders', 'POST', body)
            runtime.require(draft['id'] > maximum_id and draft['store_id'] == 1 and draft['status'] == 'draft', 'create returned an existing order; do not touch it')
            order_id = draft['id']
            report['owned_order_ids'].append(order_id)
            api.call(f'/orders/{order_id}/submit', 'POST')
            assert_order(api.call(f'/orders/{order_id}'), subtotal)
            expected_keys = [f'submit:{order_id}:GRAB', f'submit:{order_id}:FRONTDESK_RECEIPT']
            settled_jobs(api, order_id, expected_keys)
            initial_jobs = capture_jobs(db, order_id, expected_keys)
            initial_front = next(j for j in initial_jobs if j['module'] == 'FRONTDESK_RECEIPT')
            assert_receipt(initial_front, subtotal, total(subtotal), False)
            initial_total = total(subtotal)
            case = {'name': name, 'order_id': order_id, 'initial_subtotal': money(subtotal),
                    'initial_total': money(initial_total), 'updates': []}
            report['cases'].append(case)
            receipt_sum = initial_total
            for serial, (sku, leaf, added) in enumerate(additions, 1):
                previous_total = total(subtotal)
                next_subtotal = subtotal + added
                delta = total(next_subtotal) - previous_total
                key = 'amount-' + run_id + '-' + str(index) + '-' + str(serial)
                update_body = {'idempotency_key': key, 'items': [request_item(sku, leaf)]}
                response = api.call(f'/orders/{order_id}/updates', 'POST', update_body)
                runtime.require(response['already_processed'] is False, 'fresh update was unexpectedly replayed')
                replay = api.call(f'/orders/{order_id}/updates', 'POST', update_body)
                runtime.require(replay['already_processed'] is True and replay['update_batch_id'] == response['update_batch_id']
                    and replay['revision'] == response['revision'], 'same-key update was not idempotent')
                batch_id = response['update_batch_id']
                assert_order(replay['order'], next_subtotal)
                expected_keys += [f'update:{order_id}:{batch_id}:GRAB', f'update:{order_id}:{batch_id}:FRONTDESK_RECEIPT']
                settled_jobs(api, order_id, expected_keys)
                jobs = capture_jobs(db, order_id, expected_keys)
                front = next(j for j in jobs if j['batch_id'] == batch_id and j['module'] == 'FRONTDESK_RECEIPT')
                assert_receipt(front, added, delta, True)
                runtime.require(db.read(f"select count(*) from order_update_batches where order_id={order_id} and idempotency_key='{key}'") == 1,
                                'same-key update created duplicate batches')
                receipt_sum += delta
                runtime.require(receipt_sum == total(next_subtotal), 'cumulative initial/update receipts do not equal current order total')
                case['updates'].append({'batch_id': batch_id, 'idempotency_key': key,
                    'replay_already_processed': True, 'sku': sku, 'subtotal': money(added),
                    'tax': money(delta - added), 'total': money(delta), 'old_independent_total': money(total(added)),
                    'whole_subtotal': money(next_subtotal), 'whole_total': money(total(next_subtotal)),
                    'cumulative_receipts': money(receipt_sum), 'frontdesk_job_id': front['job_id']})
                subtotal = next_subtotal
            case['jobs'] = capture_jobs(db, order_id, expected_keys)
            for initial in initial_jobs:
                saved = next(j for j in case['jobs'] if j['job_id'] == initial['job_id'])
                runtime.require(saved['row_sha256'] == initial['row_sha256'], 'later update rewrote an initial receipt artifact')
            finished = api.call(f'/orders/{order_id}/complete', 'POST')
            runtime.require(finished['status'] == 'completed', 'only the new owned order should Finish')
            assert_order(finished, subtotal)
            case['finished_status'], case['final_total'] = finished['status'], money(finished['total_amount'])
            print(json.dumps({'result': 'PASS', 'case': name, 'order_id': order_id,
                              'new_job_ids': [j['job_id'] for j in case['jobs']], 'final_total': case['final_total']}))
        after = db.historical()
        report['historical_protection'] = {key: {'rows': len(value), 'before_sha256': digest(value),
            'after_sha256': digest(after[key]), 'unchanged': value == after[key]} for key, value in before.items()}
        runtime.require(all(x['unchanged'] for x in report['historical_protection'].values()), 'original Orders 2–8 / Jobs 1–18 changed')
        after_menus = db.menus()
        report['menu_protection'] = {key: digest(value) == digest(after_menus[key]) for key, value in before_menus.items()}
        runtime.require(all(report['menu_protection'].values()), 'unexpected menu/price mutation')
        report['result'] = 'PASS'
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        report['api_calls'] = api.calls
        runtime.write_private(evidence, five_orders.json_safe(report))
    if args.write_report:
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        with REPORT.open('x') as f:
            f.write(markdown(report))
    print(json.dumps({'result': 'PASS', 'evidence': str(evidence), 'report': str(REPORT) if args.write_report else None,
                      'owned_order_ids': report['owned_order_ids'], 'old_order8_untouched': True}))


if __name__ == '__main__':
    main()
