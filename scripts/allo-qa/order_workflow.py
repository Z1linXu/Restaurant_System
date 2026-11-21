#!/usr/bin/env python3
"""Allô workflow acceptance against owned localhost synthetic data and MOCK printers."""
import argparse
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
import json
from pathlib import Path
import re
import secrets
import time

import five_orders
import runtime
from api_safety import assignment_request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--legacy-adjustment', type=Path, required=True)
args = parser.parse_args()
config = runtime.config()
assert runtime.sql(config, "select count(*) from stores where id=1 and name='SYNTHETIC Allo QA' and printing_mode='MOCK'") == '1'
assert runtime.sql(config, "select max(version::int) from flyway_schema_history where success") == '45'
assert runtime.sql(config, "select count(*) from printer_configs where printer_type<>'MOCK' or ip_address is not null") == '0'

owner = five_orders.Api()
owner.token = owner.call('/auth/login', 'POST', {'login_identifier':'allo_owner', 'password':config['owner_password']})['access_token']
staff = five_orders.Api()
staff.token = staff.call('/auth/login', 'POST', {'login_identifier':config['frontdesk_username'], 'password':config['frontdesk_password']})['access_token']
catalog = owner.call('/menu/catalog?store_id=1')
menu = {i['sku']: i for group in catalog['categories'] for i in group['items']}
sales = '/allo/stores/1/sales'
run = secrets.token_hex(5)
checks = []

def dish(sku, quantity=1, options=None, notes=None):
    item = menu[sku]
    return {'menu_item_id':item['id'], 'quantity':quantity, 'options':options or [], 'notes':notes}

def submit(*lines, kind='pickup', notes=None):
    where = {'pickup_no':f'SYN-WORKFLOW-{run}-{secrets.token_hex(3)}'} if kind == 'pickup' else {'table_no':'TEST-1'}
    order = owner.call('/orders', 'POST', {'store_id':1, 'order_type':kind, **where, 'items':lines, 'order_notes':notes})
    return owner.call(f'/orders/{order["id"]}/submit', 'POST')

def detail(order_id):
    return staff.call(f'{sales}/orders/{order_id}')

def adjustment(order_id, action, **extra):
    return {'request_key':secrets.token_hex(16), 'expected_revision':detail(order_id)['order']['current_revision'],
            'action':action, **extra}

def apply(order_id, request):
    endpoint = f'{sales}/orders/{order_id}'
    before = detail(order_id)
    preview = staff.call(endpoint+'/preview', 'POST', request)
    assert before == detail(order_id), 'Preview mutated the order'
    changed = staff.call(endpoint+'/adjustments', 'POST', request)
    assert changed['amounts'] == preview['after']
    return changed

def batch_jobs(order_id, batch=None, modules=('GRAB','HOT_KITCHEN','FRONTDESK_RECEIPT')):
    for _ in range(80):
        job_list = owner.call(f'/orders/{order_id}/print-jobs')
        jobs = [j for j in job_list if j.get('order_update_batch_id') == batch and j.get('reprint_source_job_id') is None]
        assert not any(j['status'] == 'FAILED' for j in jobs), str([(j['module_code'],j.get('error_code')) for j in jobs])
        if len(jobs) == len(modules) and all(j['status'] == 'PRINTED' for j in jobs):
            by_module = {j['module_code']:j for j in jobs}
            assert set(by_module) == set(modules)
            return by_module
        time.sleep(.2)
    raise AssertionError('Expected distinct MOCK jobs did not render')

def same_kitchen(jobs):
    assert jobs['GRAB']['id'] != jobs['HOT_KITCHEN']['id']
    assert jobs['GRAB']['rendered_text_snapshot'] == jobs['HOT_KITCHEN']['rendered_text_snapshot']
    return jobs['GRAB']['rendered_text_snapshot']

def cents(x):
    return x.quantize(Decimal('.01'), ROUND_HALF_UP)

# A genuine applied V44 request, captured before starting V45.
legacy = json.loads(args.legacy_adjustment.read_text())
assert re.fullmatch(r'/allo/stores/1/sales/orders/[0-9]+/adjustments', legacy['endpoint'])
replay = owner.call(legacy['endpoint'], 'POST', legacy['body'])
assert replay['order']['current_revision'] == legacy['revision'] and len(replay['adjustments']) == legacy['entries']
checks.append('V44_ADJUSTMENT_KEY_REPLAYS_WITHOUT_WRITE')

assignments = owner.call('/admin/printing/assignments?store_id=1')
hot = next((row for row in assignments if row['module_code'] == 'HOT_KITCHEN'), None)
grab = next(row for row in assignments if row['module_code'] == 'GRAB')
if hot is None:
    printer = owner.call('/admin/printing/printers','POST', {'store_id':1,'name':'SYNTHETIC HOT','printer_type':'MOCK','enabled':True,'paper_width_mm':80})
    hot = owner.call('/admin/printing/assignments/HOT_KITCHEN','PUT', {**assignment_request(grab),'printer_id':printer['id'],'enabled':False})
device = owner.call('/devices/register','POST',{'store_id':1,'device_name':f'SYNTHETIC_WORKFLOW_{run}','device_type':'PAD','platform':'ANDROID','app_version':'MOCK acceptance'})
owner.device = device

try:
    owner.call('/admin/printing/assignments/HOT_KITCHEN','PUT',{**assignment_request(hot),'enabled':True})
    shrimp = five_orders.option(menu['AN_C1'], 'r_extra_grilled_shrimp')
    item_option = {'option_id':shrimp['id'], 'quantity':1, 'option_price_snapshot':str(shrimp['price_delta'])}
    meal = submit(dish('AN_C1', 2, [item_option], 'SYNTHETIC no sauce'), dish('AN_A3'), notes='SYNTHETIC whole order note')
    order_id = meal['id']
    assert meal['status'] == 'completed'
    initial_jobs = batch_jobs(order_id)
    text = same_kitchen(initial_jobs)
    assert 'C1 ' in text and 'A3 ' in text and 'SYNTHETIC whole order note' in text
    jobs_hash = runtime.sql(config, f"select md5(string_agg(row_to_json(j)::text,'' order by id)) from print_jobs j where order_id={order_id}")
    outbox_hash = runtime.sql(config, f"select md5(string_agg(row_to_json(o)::text,'' order by id)) from order_dispatch_outbox o where order_id={order_id}")
    assert runtime.sql(config, f"select count(distinct routing_context) from order_dispatch_outbox where order_id={order_id} and module_code in ('GRAB','HOT_KITCHEN')") == '1'
    checks.append('INITIAL_HOT_AND_GRAB_TEXT_AND_CONTEXT_IDENTICAL')

    old = detail(order_id)
    chicken = next(i for i in old['items'] if i['item_sku_snapshot'] == 'AN_C1')
    addon = next(o for o in chicken['options'] if o['is_addon'])
    option_id, item_id = addon['id'], chicken['id']
    gross = old['amounts']['gross']
    price = Decimal(shrimp['price_delta'])
    addon_void = adjustment(order_id, 'VOID_ITEMS', addons=[{'option_id':option_id,'quantity':1}])
    too_many = {**addon_void, 'addons':[{'option_id':option_id,'quantity':3}]}
    staff.call(f'{sales}/orders/{order_id}/preview','POST',too_many,expected=(409,))
    changed = apply(order_id, addon_void)
    assert changed['amounts']['gross'] == gross - price
    assert changed['amounts']['tax'] == cents((gross-price)*Decimal('.14975'))
    line = next(i for i in changed['items'] if i['id'] == item_id)
    assert line['quantity']==2 and line['voided_quantity']==0 and line['line_amount']==chicken['line_amount']
    assert line['options'][0]['quantity']==2 and line['options'][0]['voided_quantity']==1 and line['options'][0]['id']==option_id
    assert changed == staff.call(f'{sales}/orders/{order_id}/adjustments','POST',addon_void)
    staff.call(f'{sales}/orders/{order_id}/adjustments','POST',{**addon_void,'addons':[{'option_id':option_id,'quantity':2}]},expected=(409,))
    view = owner.call(f'/orders/{order_id}?financial_view=2')
    assert view['financial_adjusted'] is True
    current = next(line for line in view['items'] if line['id'] == item_id)
    assert current['options'][0]['effective_total_quantity'] == 1
    assert next(i for i in owner.call(f'/orders/{order_id}')['items'] if i['id']==item_id)['line_amount'] == current['line_amount']
    assert runtime.sql(config, f"select md5(string_agg(row_to_json(j)::text,'' order by id)) from print_jobs j where order_id={order_id}") == jobs_hash
    assert runtime.sql(config, f"select md5(string_agg(row_to_json(o)::text,'' order by id)) from order_dispatch_outbox o where order_id={order_id}") == outbox_hash
    for module in ('GRAB','HOT_KITCHEN','FRONTDESK_RECEIPT'):
        job = owner.call(f'/orders/{order_id}/reprint','POST',{'receipt_type':module,'idempotency_key':secrets.token_hex(16)})
        rendered = job['rendered_text_snapshot']
        assert job['status'] == 'PRINTED' and ('虾 x1' in rendered or 'shrimp x1' in rendered), (module, rendered)
    checks.append('ADDON_PORTION_RECONCILES_AND_PRESERVES_ORIGINAL_ARTIFACTS')

    coupon_name = 'SYNTHETIC Coupon ' + run
    created = staff.call(sales+'/coupons','POST',{'name':coupon_name,'discount_type':'AMOUNT','value':5,'enabled':True})
    coupon = next(row for row in created if row['name']==coupon_name)
    assert all(p['id']!=coupon['id'] for p in owner.call(sales+'/presets'))
    discounted = apply(order_id, adjustment(order_id, 'DISCOUNT',discount_type='PERCENT',discount_value=20,discount_name='20% Off'))
    assert discounted['amounts']['discount'] == cents((gross-price)*Decimal('.2'))
    choice = {'id':coupon['id'],'revision':coupon['revision']}
    applied = apply(order_id, adjustment(order_id, 'APPLY_COUPON', coupon=choice))
    assert applied['order']['discount_coupon_id'] == coupon['id'] and applied['amounts']['discount'] == 5
    retired = staff.call(sales+'/coupons','POST',{**coupon,'enabled':False})
    assert not next(row for row in retired if row['id']==coupon['id'])['enabled']
    assert detail(order_id)['amounts'] == applied['amounts']
    stale = adjustment(order_id,'APPLY_COUPON',coupon=choice)
    staff.call(f'{sales}/orders/{order_id}/preview','POST',stale,expected=(409,))
    no_coupon = apply(order_id, adjustment(order_id, 'REMOVE_COUPON'))
    assert no_coupon['amounts']['discount'] == 0 and no_coupon['order']['discount_coupon_id'] is None
    checks.append('STAFF_COUPON_CREATE_APPLY_REPLACE_RETIRE_REMOVE_AND_20_PERCENT')

    partial = apply(order_id, adjustment(order_id, 'VOID_ITEMS',items=[{'item_id':item_id,'quantity':1}]))
    line = next(i for i in partial['items'] if i['id']==item_id)
    assert line['voided_quantity']==1 and line['options'][0]['voided_quantity']==2
    assert line['effective_line_amount']==Decimal(menu['AN_C1']['base_price'])
    report = staff.call(sales+'/reports','POST',{})
    rendered = report['rendered_text']
    assert 'Generated:' not in rendered and 'Report:' not in rendered and 'America/Toronto' not in rendered
    assert not re.search(r'ORD-\d|\d{2}:\d{2}:\d{2}\.\d+', rendered)
    assert 'Amount Collected' in rendered and report['report_id'] not in rendered
    key = secrets.token_hex(16)
    job = staff.call(sales+'/reports/print','POST',{'report_id':report['report_id'],'request_key':key})
    assert job['status']=='PRINTED' and job['order_id'] is None
    assert staff.call(sales+'/reports/print','POST',{'report_id':report['report_id'],'request_key':key})['id'] == job['id']
    checks.append('REPORT_PREVIEW_AND_PRINT_CLEAN_AND_IDEMPOTENT')

    fully_voided = apply(order_id, adjustment(order_id, 'VOID_ORDER'))
    assert fully_voided['amounts']['total']==0 and fully_voided['order']['status']=='cancelled'
    assert all(i['voided_quantity']==i['quantity'] and all(o['voided_quantity']==o['quantity'] for o in i['options']) for i in fully_voided['items'])
    range_result = staff.call(sales+'/orders')
    today = date.fromisoformat(range_result['latest_date'])
    earliest = date.fromisoformat(range_result['earliest_date'])
    assert today-earliest == timedelta(days=29)
    assert any(row['id']==order_id and row['status']=='cancelled' and row['total_amount']==0 for row in range_result['orders'])
    staff.call(sales+f'/orders?date={earliest}', expected=(200,))
    staff.call(sales+f'/orders?date={earliest-timedelta(days=1)}', expected=(400,))
    staff.call(sales+f'/orders?date={today+timedelta(days=1)}', expected=(400,))
    staff.call('/allo/stores/2/sales/orders',expected=(403,))
    checks.append('WHOLE_VOID_HISTORY_KEEPS_ITEMS_AND_30_DAY_SCOPE')

    dine = submit(dish('AN_A3'), kind='dine_in')
    dine_id = dine['id']
    same_kitchen(batch_jobs(dine_id))
    update = owner.call(f'/orders/{dine_id}/updates','POST', {
        'idempotency_key':secrets.token_hex(16), 'expected_revision':dine['current_revision'],
        'financial_version':2,'items':[dish('AN_A2')],'order_notes':'SYNTHETIC update note'})
    text = same_kitchen(batch_jobs(dine_id, update['update_batch_id']))
    assert 'A2 ' in text and 'A3 ' not in text and 'SYNTHETIC update note' in text
    note = owner.call(f'/orders/{dine_id}/updates','POST', {
        'idempotency_key':secrets.token_hex(16), 'expected_revision':update['order']['current_revision'],
        'financial_version':2,'items':[],'order_notes':'SYNTHETIC note only'})
    text = same_kitchen(batch_jobs(dine_id, note['update_batch_id'], ('GRAB','HOT_KITCHEN')))
    assert 'SYNTHETIC note only' in text
    owner.call(f'/orders/{dine_id}/complete','POST')
    checks.append('UPDATE_AND_NOTES_ONLY_MATCH_ON_BOTH_KITCHEN_OUTPUTS')
finally:
    owner.call('/admin/printing/assignments/HOT_KITCHEN','PUT',assignment_request(hot))
    owner.call(f'/admin/printing/devices/{device["device_id"]}/revoke?store_id=1','POST')

print(json.dumps({'status':'PASS','checks':checks,'MOCK_only':True,'workflow_order_id':order_id,
                  'applied_coupon_history_preserved':True}, ensure_ascii=False))
