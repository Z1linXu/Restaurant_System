#!/usr/bin/env python3
"""Submit/Finish five Allô scenarios (six orders) and retain real persisted render snapshots.

This executable has no remote URL option. Prices come from the approved public menu
fixture, never from server totals. No production data, printer endpoint or Pad worker.
"""
import argparse
import base64
from decimal import Decimal, ROUND_HALF_UP
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import time
import urllib.error
import urllib.request

import runtime

BASE = 'http://127.0.0.1:18512/api/v1'
TAX = Decimal('0.14975')
CENT = Decimal('0.01')
REPORT = runtime.ROOT / 'docs/commercialization/merchants/allo-nouille/PRINTING_ACCEPTANCE_5_ORDERS.md'
COMBO_LIMIT = '未配置固定Combo/单条portion，非固定套餐证明；仅验证批准菜单的单品等价组合，A2始终为2条整份。'


def money(value):
    return format(Decimal(str(value)).quantize(CENT, rounding=ROUND_HALF_UP), '.2f')


def save_json(path, value):
    runtime.write_private(path, value)


class Api:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPHandler(), runtime.NoRedirect())
        self.token = None
        self.device = None
        self.calls = []

    def call(self, path, method='GET', body=None, expected=(200,), extra_headers=None, authenticated=True):
        runtime.require(path.startswith('/') and not path.startswith('//') and '..' not in path,
                        'invalid bounded local API path')
        raw = None if body is None else json.dumps(body, ensure_ascii=False, separators=(',', ':')).encode()
        headers = {'Content-Type': 'application/json'}
        if self.token and authenticated:
            headers['Authorization'] = 'Bearer ' + self.token
        if extra_headers:
            headers.update(extra_headers)
        if self.device and method == 'POST' and re.fullmatch(r'/orders/\d+/(submit|updates|reprint)', path):
            device = str(self.device['device_id'])
            timestamp = str(int(time.time()))
            digest = lambda value: hashlib.sha256(value).hexdigest()
            canonical = f'PRINT_ORIGIN_V1\n{device}\n{timestamp}\nPOST\n/api/v1{path}\n{digest(raw or b"")}\n{digest(headers["Authorization"].encode())}'
            signature = hmac.new(hashlib.sha256(self.device['device_token'].encode()).digest(),
                                 canonical.encode(), hashlib.sha256).hexdigest()
            headers.update({'X-Print-Device-Id': device, 'X-Print-Timestamp': timestamp,
                            'X-Print-Signature': signature})
        request = urllib.request.Request(BASE + path, data=raw, method=method, headers=headers)
        try:
            response = self.opener.open(request, timeout=25)
        except urllib.error.HTTPError as error:
            response = error
        except OSError as error:
            raise RuntimeError('transport result unknown; no mutation was automatically retried') from error
        with response:
            data = json.loads(response.read(6 * 1024 * 1024), parse_float=Decimal)
            self.calls.append({'method': method, 'path': path, 'status': response.status})
            runtime.require(response.status in expected,
                            f'{method} {path}: HTTP {response.status}: {data.get("message", "")}')
            runtime.require(data.get('success') is (response.status < 400), 'API envelope mismatch')
            return data.get('data') if response.status < 400 else data


def json_safe(value):
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, dict):
        return {key: json_safe(item) for key, item in value.items()}
    if isinstance(value, list):
        return [json_safe(item) for item in value]
    return value


def option(item, code):
    # Both API views refer to the same menu_item_options identity; never invent IDs.
    candidates = {row['id']: row for row in item.get('options', [])
                  if row.get('option_code') == code and row.get('is_active', True)}
    for group in item.get('option_groups', []):
        for row in group.get('options', []):
            if row.get('option_code') == code and row.get('is_active', True):
                candidates[row['id']] = row
    runtime.require(len(candidates) == 1, f'one current option required for {item["sku"]}/{code}')
    return next(iter(candidates.values()))


def expectations(fixture, specs):
    items = {item['code']: item for item in fixture['items']}
    choices = {'choice_' + row['code'].lower(): row for row in fixture['noodle_options']}
    for group in fixture['option_groups']:
        if group['code'] != 'NOODLE_TYPE':
            choices.update({'choice_' + row['code'].lower(): row for row in group['options']})
    addons = {row['code']: row for row in fixture['addons']}
    rows = []
    subtotal = Decimal('0')
    for code, selected in specs:
        item = items[code]
        row = {'code': code, 'quantity': 1, 'name_zh': item['name_zh'], 'name_en': item['name_en'],
               'base_price': money(item['base_price']), 'category': item['category_code'], 'options': []}
        line = Decimal(item['base_price'])
        for selected_code in selected:
            source = addons.get(selected_code) or choices[selected_code]
            price = Decimal(source.get('price', source.get('price_delta')))
            line += price
            row['options'].append({'code': selected_code, 'quantity': 1, 'name_zh': source['name_zh'],
                                   'name_en': source['name_en'], 'price': money(price)})
        row['line_amount'] = money(line)
        rows.append(row)
        subtotal += line
    tax = (subtotal * TAX).quantize(CENT, rounding=ROUND_HALF_UP)
    return {'lines': rows, 'subtotal': money(subtotal), 'tax_rate': '14.975%',
            'tax': money(tax), 'total': money(subtotal + tax),
            'calculation': 'sum((approved base price + selected approved option prices) * quantity); tax=round_half_up(subtotal*0.14975,2)'}


def assert_amounts(order, expected):
    runtime.require(money(order['subtotal_amount']) == expected['subtotal'], 'subtotal differs from independent fixture calculation')
    runtime.require(money(order['total_amount']) == expected['total'], 'total differs from independent fixture calculation')
    actual_tax = Decimal(str(order['total_amount'])) - Decimal(str(order['subtotal_amount']))
    runtime.require(money(actual_tax) == expected['tax'], 'tax differs from per-order half-up calculation')
    by_sku = {item['item_sku_snapshot']: item for item in order['items']}
    runtime.require(len(by_sku) == len(expected['lines']), 'unexpected extra or duplicate order item')
    for row in expected['lines']:
        saved = by_sku['AN_' + row['code']]
        runtime.require(saved['quantity'] == 1 and money(saved['unit_price']) == row['base_price']
                        and money(saved['line_amount']) == row['line_amount'], 'saved line price/quantity mismatch')
        actual_options = {leaf['option_code_snapshot']: leaf for leaf in saved.get('options', [])}
        runtime.require(set(actual_options) == {leaf['code'] for leaf in row['options']}, 'saved options differ from request')
        for leaf in row['options']:
            actual = actual_options[leaf['code']]
            runtime.require(actual['quantity'] == 1 and money(actual['price_delta']) == leaf['price'], 'saved option price/quantity mismatch')
        runtime.require(saved.get('combo_role') in (None, 'standalone') and saved.get('combo_group_no') is None,
                        'unconfigured synthetic Combo must not be fabricated')


def wait_jobs(api, order_id):
    for _ in range(60):
        jobs = api.call(f'/orders/{order_id}/print-jobs')
        if len(jobs) >= 2 and all(job['status'] in ('PRINTED', 'FAILED', 'CANCELLED') for job in jobs):
            return jobs
        time.sleep(0.25)
    raise RuntimeError('real rendered PrintJobs did not settle')


def assert_snapshots(jobs, expected, dessert_enabled, device_id):
    runtime.require(len(jobs) == 2 and {job['module_code'] for job in jobs} == {'GRAB', 'FRONTDESK_RECEIPT'},
                    'exactly one GRAB and one FRONTDESK_RECEIPT required; no duplicate dispatch')
    by_module = {job['module_code']: job for job in jobs}
    for job in jobs:
        # Legacy MOCK jobs leave execution_mode NULL. The persisted receipt artifact
        # and frozen context below establish MOCK transport without inventing a mode.
        runtime.require(job['status'] == 'PRINTED' and job.get('execution_mode') in (None, 'MOCK'),
                        f'MOCK renderer did not succeed: job {job["id"]} {job.get("error_code")}')
        runtime.require(not job.get('printer_endpoint') and job.get('printed_by_device_id') is None,
                        'no physical endpoint/device transport permitted')
        runtime.require(job.get('preferred_device_id') == device_id, 'signed synthetic device provenance lost')
        runtime.require(isinstance(job.get('rendered_text_snapshot'), str) and job['rendered_text_snapshot'].strip(),
                        'actual saved renderer snapshot missing')
        runtime.require('SYNTHETIC Allo QA' in job['rendered_text_snapshot'], 'snapshot must visibly identify synthetic QA')
    kitchen = by_module['GRAB']['rendered_text_snapshot']
    frontdesk = by_module['FRONTDESK_RECEIPT']['rendered_text_snapshot']
    for row in expected['lines']:
        kitchen_name = row['code'] + ' ' + row['name_zh']
        frontdesk_name = row['code'] + ' ' + row['name_en']
        in_kitchen = row['category'] != 'DRINK' and (row['category'] != 'DESSERT' or dessert_enabled)
        runtime.require(kitchen.count(kitchen_name) == int(in_kitchen), f'kitchen route/duplicate mismatch for {row["code"]}')
        runtime.require(frontdesk.count(frontdesk_name) == 1, f'frontdesk must retain each paid item exactly once: {row["code"]}')
        for leaf in row['options']:
            runtime.require(kitchen.count(leaf['name_zh'] + ' x1') == 1, 'kitchen selected option missing/duplicated')
            runtime.require(frontdesk.count(leaf['name_en'] + ' x1') == 1, 'frontdesk selected option missing/duplicated')
    for label, amount in [('Subtotal', expected['subtotal']), ('Tax (14.975%)', expected['tax']), ('Total', expected['total'])]:
        runtime.require(label + ': $' + amount in frontdesk, f'receipt {label} does not match independently expected amount')
    runtime.require('Combo' not in frontdesk and 'Combo' not in kitchen, 'unconfigured Combo appeared in receipt')


def receipt_settings(api, store_id, dessert_enabled):
    current = api.call(f'/stores/{store_id}/receipt-presentation')
    request = {key: current[key] for key in ('enabled', 'default_language', 'kitchen_language',
               'frontdesk_language', 'kitchen_station_codes', 'kitchen_category_enabled') if key in current}
    request.update({'enabled': True, 'default_language': 'zh-CN', 'kitchen_language': 'zh-CN',
        'frontdesk_language': 'en', 'kitchen_station_codes': ['BACK_KITCHEN'],
        'kitchen_category_enabled': {'APPETIZER': True, 'NOODLE': True, 'RICE': True,
                                     'DRINK': False, 'DESSERT': dessert_enabled},
        'expected_revision': current['revision']})
    result = api.call(f'/stores/{store_id}/receipt-presentation', 'PUT', request)
    runtime.require(result['kitchen_category_enabled']['DESSERT'] is dessert_enabled, 'dessert setting readback differs')
    return result


def markdown(report):
    lines = ['# Allô 五组实际 Renderer 验收', '',
        f'结果：**{report["result"]}**。时间：`{report["started_at_utc"]}`。', '',
        '全部订单、凭据和设备均为本轮新建本机合成数据。目标仅 `127.0.0.1:18512` / '
        '`127.0.0.1:26512/restaurant_allo_nouilles_test`；Store `ALLO_NOUILLES_TEST`，'
        '显示名 `SYNTHETIC Allo QA`，`printing_mode=MOCK`。无远程/生产数据库连接、真实订单、LAN 连接、'
        'Android Worker 或物理打印。这六单不领取 Pad 队列；另行 PAD_DIRECT HTTP 模拟证据单独保存。'
        '此证明不代表纸张或打印机硬件验收。', '',
        f'Artifact SHA-256：`{report["artifact"]["sha256"]}`；build 标识：`{report["artifact"]["build_sha"]}`。', '',
        f'本机 Flyway：`V{report["flyway"]["latest"]}`，成功 `{report["flyway"]["successful"]}`、失败 `{report["flyway"]["failed"]}`。', '',
        f'批准菜单 SHA-256：`{report["menu_sha256"]}`。预期金额从该菜单单价与实际选项独立用 Decimal 计算：'
        '`tax = round_half_up(subtotal × 0.14975, 2)`，`total = subtotal + tax`。', '',
        '五组产生六个实际订单，第五组为 Dessert 开/关对照。全部通过实际 Create → signed Submit → '
        '持久化 PrintJob → Finish API。下文小票均直接取保存的 `rendered_text_snapshot`，保留原始 markup；'
        '未手写或替换 Renderer 输出。', '',
        '旧兼容 MOCK Job 的 `execution_mode` 字段可为 `null`；本轮同时逐张验证冻结 '
        '`receipt_context.execution_mode=MOCK` 与 `receipt_artifact.transport=MOCK_ONLY_NOT_PHYSICAL_PROOF`。', '',
        f'固定套餐限制：{COMBO_LIMIT}', '',
        '| 场景 | Order ID | GRAB Job | FRONTDESK Job | 预期/实际小计 | 预期/实际税 | 预期/实际总计 | Finish |',
        '| --- | --- | --- | --- | --- | --- | --- | --- |']
    for case in report['cases']:
        expected, actual = case['expected'], case['actual']
        by_module = {job['module_code']: job for job in case['jobs']}
        lines.append(f'| {case["scenario"]} | {actual["id"]} | {by_module["GRAB"]["id"]} | '
            f'{by_module["FRONTDESK_RECEIPT"]["id"]} | {expected["subtotal"]}/{money(actual["subtotal_amount"])} | '
            f'{expected["tax"]}/{money(Decimal(actual["total_amount"])-Decimal(actual["subtotal_amount"]))} | '
            f'{expected["total"]}/{money(actual["total_amount"])} | {case["finished_status"]} |')
    for case in report['cases']:
        lines += ['', '## ' + case['scenario'], '', case['description'], '',
                  '独立预期与 API 实际读回：', '', '```json',
                  json.dumps({'expected': case['expected'], 'actual': case['actual']}, ensure_ascii=False, indent=2), '```']
        if case.get('combo_limitation'):
            lines += ['', case['combo_limitation']]
        for job in case['jobs']:
            lines += ['', f'### {job["module_code"]} — Job {job["id"]}', '',
                      f'`status={job["status"]}`，`execution_mode={json.dumps(job["execution_mode"])}`，'
                      f'`preferred_device_id={job["preferred_device_id"]}`。保存文本 SHA-256：'
                      f'`{hashlib.sha256(job["rendered_text_snapshot"].encode()).hexdigest()}`。', '',
                      '```text', job['rendered_text_snapshot'], '```']
    lines += ['', 'Dessert 对照：关闭时 GRAB 仅包含前菜与主菜；开启时 Dessert 增加一次。'
              '饮品两单均保留在 FRONTDESK、两单均不进 GRAB；金额一致；每单恰好一张 GRAB，无重复派单。', '',
              '数据库读回已逐张核对保存文本与 API 快照完全相同；所有订单最终状态 `completed`。'
              '合成设备验收后已撤销。固定 Combo 定价、单条春卷 portion、真实 Pad/打印机输出未在本报告中宣称通过。', '']
    return '\n'.join(lines)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true', required=True)
    parser.add_argument('--write-report', action='store_true')
    args = parser.parse_args()
    c = runtime.config()
    artifact = json.loads((runtime.PRIVATE / 'qa-backend-process.json').read_text())
    runtime.require(artifact['phase'] == 'runtime', 'production-policy QA runtime required')
    runtime.require(hashlib.sha256(runtime.MENU.read_bytes()).hexdigest() == c['menu_sha256'], 'approved menu changed')
    fixture = json.loads(runtime.MENU.read_text())
    runtime.require(not fixture['combo_offers'], 'this bounded acceptance assumes no fixed Combo fixture')
    runtime.require(runtime.sql(c, "select count(*) from stores where code='ALLO_NOUILLES_TEST' and name='SYNTHETIC Allo QA' and printing_mode='MOCK'") == '1', 'synthetic MOCK store required')
    runtime.require(runtime.sql(c, "select count(*) from printer_configs where printer_type <> 'MOCK' or nullif(ip_address,'') is not null") == '0', 'physical endpoints forbidden')
    run_id = str(time.time_ns())
    evidence = runtime.PRIVATE / f'qa-five-orders-{run_id}.json'
    api = Api()
    report = {'result': 'FAIL', 'started_at_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'artifact': {key: artifact[key] for key in ('sha256', 'build_sha', 'phase')},
        'menu_sha256': c['menu_sha256'], 'target': BASE, 'synthetic_only': True,
        'physical_printing': 'NONE', 'combo_limitation': COMBO_LIMIT, 'cases': []}
    report['flyway'] = json.loads(runtime.sql(c, "select json_build_object('successful',count(*) filter (where success),'failed',count(*) filter (where not success),'latest',max(version::int)) from flyway_schema_history where version is not null"))
    runtime.require(report['flyway']['failed'] == 0 and report['flyway']['latest'] == 42, 'new synthetic QA must have V42 migrated successfully')
    device_id = None
    original_settings = None
    try:
        login = api.call('/auth/login', 'POST', {'login_identifier': 'allo_owner', 'password': c['owner_password']})
        api.token = login['access_token']
        store_id = int(runtime.sql(c, "select id from stores where code='ALLO_NOUILLES_TEST'"))
        safety = api.call(f'/stores/{store_id}/template/runtime-safety')
        runtime.require(safety['environment'] == 'production' and set(safety['allowed_printing_modes']) == {'MOCK','DISABLED','PAD_DIRECT'}
                        and safety['endpoint_configuration_enabled'] is True, 'production Pad policy chain absent')
        report['runtime_safety'] = safety
        overview = api.call(f'/admin/printing?store_id={store_id}')
        runtime.require(overview['printing_mode'] == 'MOCK', 'store must remain MOCK')
        catalog = api.call(f'/menu/catalog?store_id={store_id}')
        runtime.require(not catalog['combo_configuration']['groups'], 'unconfigured production Combo assumption no longer holds')
        report['menu_revision'] = catalog['menu_revision']
        menu = {item['sku'][3:]: item for category in catalog['categories'] for item in category['items'] if item['sku'].startswith('AN_')}
        original_settings = api.call(f'/stores/{store_id}/receipt-presentation')
        device = api.call('/devices/register', 'POST', {'store_id': store_id,
            'device_name': 'SYNTHETIC_ALLO_QA_' + run_id, 'device_type': 'PAD',
            'platform': 'ANDROID', 'app_version': 'synthetic-render-proof-no-worker'})
        api.device, device_id = device, device['device_id']
        report['synthetic_device_id'] = device_id
        cases = [
            ('1. B1 面型、无 Combo', [('B1', ['choice_thin'])], False,
             'B1 经典牛肉面，细面；没有 Combo 选项或额外子菜。'),
            ('2. B1 + A2 整份 + paid Add-on', [('B1', ['choice_thin','n_extra_beef']), ('A2', [])], False,
             'B1 细面加一份牛肉（$7.99），A2 炸素春卷始终为2条整份（$4.99）。'),
            ('3. C1 + A1 汤 + A2 整份', [('C1', []), ('A1', []), ('A2', [])], False,
             'C1 炸鸡排饭、A1 虾云吞汤3颗、A2 炸素春卷2条；所有金额采用单品价。'),
            ('4. C6 双拼两蛋白', [('C6', ['choice_beef','choice_chicken'])], False,
             'C6 四选二选择牛肉与鸡肉，两项均为$0.00选择；保留一份饭，不生成两份全价饭。'),
            ('5a. 前菜 + 主菜 + 饮品 + Dessert（关闭）', [('A2', []), ('B1', ['choice_thin']), ('D1', []), ('E1', [])], False,
             'Dessert 厨房开关关闭；GRAB 只打印前菜和主菜；饮品与甜品仍完整记账并留在收据。'),
            ('5b. 前菜 + 主菜 + 饮品 + Dessert（开启）', [('A2', []), ('B1', ['choice_thin']), ('D1', []), ('E1', [])], True,
             '与5a同样的单品及金额；Dessert 厨房开关开启，甜品仅增加一次，饮品仍不进厨房。')]
        for index, (name, specs, dessert_enabled, description) in enumerate(cases, 1):
            expected = expectations(fixture, specs)
            settings = receipt_settings(api, store_id, dessert_enabled)
            requests = []
            for code, selected in specs:
                item = menu[code]
                runtime.require(money(item['base_price']) == next(row for row in expected['lines'] if row['code'] == code)['base_price'], 'live fixture base price differs from approved menu')
                requests.append({'menu_item_id': item['id'], 'quantity': 1, 'combo_role': 'standalone',
                    'options': [{'option_id': option(item, selected_code)['id'], 'quantity': 1} for selected_code in selected]})
            case = {'scenario': name, 'description': description, 'dessert_enabled': dessert_enabled,
                    'settings_revision': settings['revision'], 'expected': expected}
            if index in (2, 3):
                case['combo_limitation'] = COMBO_LIMIT
            report['cases'].append(case)
            draft = api.call('/orders', 'POST', {'store_id': store_id, 'order_type': 'dine_in', 'table_no': 'TEST-1', 'items': requests})
            case['order_id'] = draft['id']
            assert_amounts(draft, expected)
            submitted = api.call(f'/orders/{draft["id"]}/submit', 'POST')
            assert_amounts(submitted, expected)
            jobs = wait_jobs(api, draft['id'])
            case['jobs'] = [{key: job.get(key) for key in ('id','order_id','module_code','receipt_type','status',
                'execution_mode','preferred_device_id','printed_by_device_id','printer_endpoint','rendered_text_snapshot')} for job in jobs]
            assert_snapshots(jobs, expected, dessert_enabled, device_id)
            for job in jobs:
                saved = runtime.sql(c, f"select encode(convert_to(rendered_text_snapshot,'UTF8'),'base64') from print_jobs where id={int(job['id'])} and store_id={store_id} and order_id={int(draft['id'])}")
                runtime.require(base64.b64decode(saved).decode() == job['rendered_text_snapshot'], 'database/API rendered snapshot differs')
                frozen = json.loads(runtime.sql(c, f"select payload_snapshot from print_jobs where id={int(job['id'])} and store_id={store_id}"))
                runtime.require(frozen['receipt_context']['execution_mode'] == 'MOCK'
                    and frozen['receipt_artifact']['transport'] == 'MOCK_ONLY_NOT_PHYSICAL_PROOF', 'persisted MOCK artifact provenance required')
                case.setdefault('persisted_artifacts', []).append({'job_id': job['id'],
                    'frozen_execution_mode': frozen['receipt_context']['execution_mode'],
                    'artifact': frozen['receipt_artifact']})
                snapshot_path = runtime.PRIVATE / f'qa-five-orders-{run_id}-{index}-{job["module_code"]}.txt'
                with snapshot_path.open('x') as f:
                    os.chmod(snapshot_path, 0o600)
                    f.write(job['rendered_text_snapshot'])
            finished = api.call(f'/orders/{draft["id"]}/complete', 'POST')
            runtime.require(finished['status'] == 'completed', 'Finish did not complete actual order')
            assert_amounts(finished, expected)
            case['finished_status'] = finished['status']
            case['actual'] = {key: json_safe(finished[key]) for key in ('id','order_no','status','subtotal_amount','total_amount','items')}
            case['result'] = 'PASS'
            print(json.dumps({'case': index, 'result': 'PASS', 'order_id': draft['id'],
                'job_ids': [job['id'] for job in jobs], 'total': expected['total']}))
        runtime.require(report['cases'][-1]['expected']['total'] == report['cases'][-2]['expected']['total'], 'dessert display switch altered price')
        report['result'] = 'PASS'
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        try:
            if original_settings is not None:
                current = api.call(f'/stores/{store_id}/receipt-presentation')
                restore = {key: value for key, value in original_settings.items() if key not in ('revision','expected_revision')}
                restore['expected_revision'] = current['revision']
                api.call(f'/stores/{store_id}/receipt-presentation', 'PUT', restore)
                report['receipt_settings_restored'] = True
        except Exception as error:
            report['result'] = 'FAIL'
            report.setdefault('cleanup_errors', []).append(str(error))
        try:
            if device_id is not None:
                api.call(f'/admin/printing/devices/{device_id}/revoke?store_id={store_id}', 'POST')
                report['synthetic_device_revoked'] = True
        except Exception as error:
            report['result'] = 'FAIL'
            report.setdefault('cleanup_errors', []).append(str(error))
        report['api_calls'] = api.calls
        save_json(evidence, json_safe(report))
    if args.write_report:
        runtime.require(report['result'] == 'PASS' and len(report['cases']) == 6, 'only real complete acceptance can produce the report')
        runtime.require(not REPORT.exists(), 'existing acceptance report is protected')
        REPORT.parent.mkdir(parents=True, exist_ok=True)
        with REPORT.open('x') as f:
            f.write(markdown(report))
    print(json.dumps({'result': report['result'], 'actual_orders': len(report['cases']),
                      'evidence': str(evidence), 'report': str(REPORT) if args.write_report else None}))


if __name__ == '__main__':
    main()
