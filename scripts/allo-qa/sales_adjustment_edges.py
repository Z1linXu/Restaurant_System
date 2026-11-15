#!/usr/bin/env python3
"""Consolidated Agent 6 findings: actual synthetic API, legacy view, add-only and audit rollback."""
import json,secrets,time
from decimal import Decimal,ROUND_HALF_UP
import runtime,five_orders
c=runtime.config();a=five_orders.Api();a.token=a.call('/auth/login','POST',{'login_identifier':'allo_owner','password':c['owner_password']})['access_token'];cat=a.call('/menu/catalog?store_id=1');menu={i['sku']:i for g in cat['categories'] for i in g['items']};run=secrets.token_hex(6);s='/allo/stores/1/sales'
assert runtime.sql(c,"select count(*) from stores where id=1 and name='SYNTHETIC Allo QA' and printing_mode='MOCK'")=='1'
def create(lines,kind='dine_in'):
 d=a.call('/orders','POST',{'store_id':1,'order_type':kind,'table_no':'TEST-2' if kind=='dine_in' else None,'pickup_no':'SYN-EDGE-'+secrets.token_hex(6) if kind=='pickup' else None,'items':lines});return a.call(f'/orders/{d["id"]}/submit','POST')
def line(code,n=1):return {'menu_item_id':menu[code]['id'],'quantity':n,'options':[]}
def detail(i):return a.call(s+f'/orders/{i}')
def adjust(i,action,**kw):
 r={'request_key':secrets.token_hex(16),'expected_revision':detail(i)['order']['current_revision'] or 0,'action':action,**kw};return a.call(s+f'/orders/{i}/adjustments','POST',r)
d=None;result={'result':'FAIL'}
try:
 d=a.call('/devices/register','POST',{'store_id':1,'device_name':'SYNTHETIC_EDGE_'+run,'device_type':'PAD','platform':'ANDROID','app_version':'synthetic-no-worker'});a.device=d
 o=create([line('AN_D1')]);i=o['id'];legacy={'idempotency_key':secrets.token_hex(16),'items':[line('AN_D1')]};first=a.call(f'/orders/{i}/updates','POST',legacy)
 out=adjust(i,'DISCOUNT',discount_type='PERCENT',discount_value=50,discount_name='50% Off')
 old=a.call(f'/orders/{i}');new=a.call(f'/orders/{i}?financial_view=2')
 assert Decimal(str(old['subtotal_amount']))==Decimal(str(new['net_amount'])) and sum(Decimal(str(l['line_amount'])) for l in old['items'])==Decimal(str(new['net_amount']))
 assert Decimal(str(old['total_amount']))==Decimal(str(new['total_amount'])) and Decimal(str(old['total_amount']))==Decimal(str(old['subtotal_amount']))+(Decimal(str(old['subtotal_amount']))*Decimal('.14975')).quantize(Decimal('.01'),ROUND_HALF_UP)
 assert a.call(f'/orders/{i}/updates','POST',legacy)['already_processed']
 blocked={'idempotency_key':secrets.token_hex(16),'items':[line('AN_D1')]};a.call(f'/orders/{i}/updates','POST',blocked,expected=(409,))
 update={**blocked,'financial_version':2,'expected_revision':out['order']['current_revision'],'order_notes':'SYNTHETIC revised amount'}
 updated=a.call(f'/orders/{i}/updates','POST',update);order=updated['order'];assert Decimal(str(order['subtotal_amount']))==Decimal('5.97') and Decimal(str(order['discount_amount']))==Decimal('2.99') and Decimal(str(order['total_amount']))==Decimal('3.43')
 for _ in range(60):
  js=a.call(f'/orders/{i}/print-jobs');batch=[j for j in js if j.get('order_update_batch_id')==updated['update_batch_id'] and j['module_code']=='FRONTDESK_RECEIPT']
  if batch and batch[0]['status']=='PRINTED':break
  time.sleep(.1)
 else:raise AssertionError('Discounted update receipt not printed in MOCK')
 assert '$1.14' in batch[0]['rendered_text_snapshot'] and '$1.00' in batch[0]['rendered_text_snapshot']
 # An audit insert failure rolls back amount, revision, item void count and all ledger writes.
 name='qa_ledger_'+run;magic='SYNTHETIC_LEDGER_FAILURE_'+run
 before=runtime.sql(c,f"select row_to_json(o)::text from orders o where id={i}");ledger=runtime.sql(c,f'select count(*) from allo_order_adjustments where order_id={i}')
 runtime.sql(c,f"alter table allo_order_adjustments add constraint {name} check(reason is distinct from '{magic}') not valid")
 try:a.call(s+f'/orders/{i}/adjustments','POST',{'request_key':secrets.token_hex(16),'expected_revision':detail(i)['order']['current_revision'],'action':'VOID_ORDER','reason':magic},expected=(500,))
 finally:runtime.sql(c,f'alter table allo_order_adjustments drop constraint {name}')
 assert before==runtime.sql(c,f"select row_to_json(o)::text from orders o where id={i}") and ledger==runtime.sql(c,f'select count(*) from allo_order_adjustments where order_id={i}')
 adjust(i,'REMOVE_DISCOUNT');last=detail(i)['adjustments'][-1];pre=json.loads(last['before_snapshot']);post=json.loads(last['after_snapshot']);assert pre['discount_rule']['type']=='PERCENT' and pre['discount_rule']['value']==50 and post['discount_rule']['type'] is None and post['print_policy'] and isinstance(post['print_jobs'],list)
 a.call(f'/orders/{i}/complete','POST')
 # Synthetic existing combo identity model (no claim about a configured menu offer).
 combo=create([line('AN_C1'),line('AN_A2')],'pickup');ci=combo['id'];ids=[x['id'] for x in combo['items']]
 runtime.sql(c,f"update order_items set combo_group_no=41,combo_role=case when id={ids[0]} then 'main' else 'combo_side' end where order_id={ci}")
 out=adjust(ci,'VOID_ORDER');assert out['order']['status']=='cancelled' and out['amounts']['total']==0 and all(x['voided_quantity']==x['quantity'] for x in out['items'])
 result={'result':'PASS','legacy_net_display':'PASS','legacy_adjusted_order_new_update_blocked':'PASS','legacy_committed_update_replay':'PASS','new_adjusted_update_and_receipt':'PASS','mandatory_ledger_failure_rolls_back':'PASS','discount_rule_and_print_policy_history':'PASS','combo_order_void':'PASS','physical_print':'NOT_TRIGGERED','orders':[i,ci]}
finally:
 if d:a.call(f'/admin/printing/devices/{d["device_id"]}/revoke?store_id=1','POST')
runtime.write_private(runtime.PRIVATE/('qa-sales-edges-'+str(time.time_ns())+'.json'),result);print(json.dumps(result))
