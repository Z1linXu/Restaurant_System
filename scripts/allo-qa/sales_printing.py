#!/usr/bin/env python3
"""Bounded synthetic-only Allô financial / Takeout HTTP acceptance. Never accepts a remote URL."""
import concurrent.futures,copy,json,os,secrets,time
from decimal import Decimal,ROUND_HALF_UP
import runtime,five_orders
os.umask(0o077)
c=runtime.config();a=five_orders.Api();report={'checks':[],'physical_print':'HARDWARE_NOT_TESTED'}
assert runtime.sql(c,"select count(*) from stores where id=1 and name='SYNTHETIC Allo QA' and printing_mode='MOCK'")=='1'
assert runtime.sql(c,"select max(version::int) from flyway_schema_history where success")=='44'
a.token=a.call('/auth/login','POST',{'login_identifier':'allo_owner','password':c['owner_password']})['access_token']
f=five_orders.Api();f.token=f.call('/auth/login','POST',{'login_identifier':c['frontdesk_username'],'password':c['frontdesk_password']})['access_token']
cat=a.call('/menu/catalog?store_id=1');menu={i['sku']:{**i,'category_code':g['code']} for g in cat['categories'] for i in g['items']}
s='/allo/stores/1/sales';run=secrets.token_hex(6);made=[]
def req(code,qty=1,notes=None):
 i=menu[code];return {'menu_item_id':i['id'],'quantity':qty,'combo_role':'standalone','notes':notes,'options':[],
 'item_name_snapshot_zh':i['name_zh'],'item_name_snapshot_en':i['name_en'],'unit_price_snapshot':str(i['base_price']),
 'category_code_snapshot':i['category_code'],'station_id_snapshot':i['station_id'],'item_sku_snapshot':i['sku']}
def create(lines,kind='pickup'):
 d=a.call('/orders','POST',{'store_id':1,'order_type':kind,'pickup_no':'SYN-'+run+'-'+str(len(made)) if kind=='pickup' else None,'table_no':'TEST-1' if kind=='dine_in' else None,'items':lines})
 made.append(d['id']);return a.call('/orders/'+str(d['id'])+'/submit','POST')
def detail(i):return f.call(s+f'/orders/{i}')
def change(i,action,**kw):
 d=detail(i);r={'request_key':secrets.token_hex(16),'expected_revision':d['order']['current_revision'] or 0,'action':action,**kw}
 preview=f.call(s+f'/orders/{i}/preview','POST',r);out=f.call(s+f'/orders/{i}/adjustments','POST',r)
 assert out['amounts']==preview['after'];return out,r
try:
 d=a.call('/devices/register','POST',{'store_id':1,'device_name':'SYNTHETIC_SALES_'+run,'device_type':'PAD','platform':'ANDROID','app_version':'synthetic-protocol-no-native-print'})
 a.device=d
 order=create([req('AN_A5'),req('AN_A5')]);i=order['id'];assert order['status']=='completed' and order['completed_at'];assert len(order['items'])==2
 jobs=five_orders.wait_jobs(a,i);assert len(jobs)==2
 texts={j['module_code']:j['rendered_text_snapshot'] for j in jobs}
 assert 'x2' in texts['GRAB'] and '2 x ' in texts['FRONTDESK_RECEIPT']
 frozen=runtime.sql(c,f"select md5(string_agg(row_to_json(j)::text,'' order by id)) from print_jobs j where order_id={i}")
 before=detail(i);gross=Decimal(str(before['amounts']['gross']))
 out,r=change(i,'DISCOUNT',discount_type='PERCENT',discount_value=50,discount_name='50% Off')
 assert Decimal(str(out['amounts']['discount']))==(gross/2).quantize(Decimal('.01'),ROUND_HALF_UP)
 assert Decimal(str(out['amounts']['tax']))==(Decimal(str(out['amounts']['net']))*Decimal('.14975')).quantize(Decimal('.01'),ROUND_HALF_UP)
 count=len(out['adjustments']);assert len(f.call(s+f'/orders/{i}/adjustments','POST',r)['adjustments'])==count
 f.call(s+f'/orders/{i}/adjustments','POST',{**r,'discount_value':25},expected=(409,))
 # Manual latest-order reprint uses current financial totals, never rewrites originals.
 j=a.call(f'/orders/{i}/reprint','POST',{'receipt_type':'FRONTDESK_RECEIPT','idempotency_key':secrets.token_hex(16)})
 assert 'Discount:' in j['rendered_text_snapshot'] and '$'+five_orders.money(out['amounts']['total']) in j['rendered_text_snapshot']
 line=out['items'][0];out,_=change(i,'VOID_ITEMS',items=[{'item_id':line['id'],'quantity':1}])
 assert Decimal(str(out['amounts']['gross']))==gross/2
 out,_=change(i,'REMOVE_DISCOUNT');assert out['amounts']['discount']==0 and len(out['items'])==2 and out['items'][0]['voided_quantity']==1
 # Confirm server order projection retains only current effective portions.
 current=a.call(f'/orders/{i}');assert len(current['items'])==1
 out,_=change(i,'VOID_ORDER');assert out['order']['status']=='cancelled' and out['amounts']['total']==0
 assert runtime.sql(c,f"select md5(string_agg(row_to_json(j)::text,'' order by id)) from print_jobs j where order_id={i} and id in ({','.join(str(x['id']) for x in jobs)})")==frozen
 report['checks'].append('MERGE_TAKEOUT_DISCOUNT_VOID_RETRY_IMMUTABLE_ORIGINALS_PASS')
 # 100% discount remains a valid zero sale; concurrent adjustments are never lost.
 z=create([req('AN_D1',3)]);zid=z['id'];out,_=change(zid,'DISCOUNT',discount_type='PERCENT',discount_value=100,discount_name='100% Off');assert out['order']['status']=='completed' and out['amounts']['total']==0
 line=out['items'][0];out,_=change(zid,'VOID_ITEMS',items=[{'item_id':line['id'],'quantity':1}]);assert a.call(f'/orders/{zid}')['items'][0]['quantity']==2
 rev=out['order']['current_revision'];rs=[{'request_key':secrets.token_hex(16),'expected_revision':rev,'action':'DISCOUNT','discount_type':'AMOUNT','discount_value':v,'discount_name':str(v)+' Off'} for v in (1,2)]
 def parallel(r):
  b=five_orders.Api();b.token=f.token;b.call(s+f'/orders/{zid}/adjustments','POST',r,expected=(200,409));return b.calls[-1]['status']
 with concurrent.futures.ThreadPoolExecutor(2) as pool:
  results=sorted(pool.map(parallel,rs));assert results==[200,409],str(results)
 report['checks'].append('ZERO_VALID_PARTIAL_QUANTITY_CONCURRENCY_PASS')
 # Server presets are shared; edits never change the applied snapshot.
 p=f.call(s+'/presets','POST',{'name':'SYNTHETIC '+run,'discount_type':'PERCENT','value':25,'enabled':True})[-1]
 out,_=change(zid,'DISCOUNT',discount_type=p['discount_type'],discount_value=str(p['value']),discount_name=p['name']);saved=out['amounts']
 a.call(s+'/presets','POST',{**{k:p[k] for k in ('id','name','discount_type','enabled','revision')},'value':50,'enabled':False})
 assert detail(zid)['amounts']==saved and next(x for x in f.call(s+'/presets') if x['id']==p['id'])['enabled'] is False
 # Original submission date is authoritative, not today's updated_at.
 hist=create([req('AN_D1')]);hid=hist['id'];runtime.sql(c,f"update orders set submitted_at=(current_timestamp at time zone 'America/Toronto')::date-interval '1 day'+interval '23 hours 59 minutes',updated_at=localtimestamp where id={hid} and store_id=1")
 old=detail(hid);assert not old['editable'];f.call(s+f'/orders/{hid}/adjustments','POST',{'request_key':secrets.token_hex(16),'expected_revision':old['order']['current_revision'] or 0,'action':'VOID_ORDER'},expected=(409,))
 f.call('/allo/stores/2/sales/overview',expected=(403,));five_orders.Api().call(s+'/overview',expected=(401,),authenticated=False)
 report['checks'].append('PRESET_SNAPSHOT_STORE_AUTH_ORIGINAL_DAY_PASS')
 # Dine-in still needs Finish.
 dine=create([req('AN_D1')],'dine_in');assert dine['status']!='completed';a.call(f'/orders/{dine["id"]}/complete','POST');assert a.call(f'/orders/{dine["id"]}')['status']=='completed'
 # Idempotent Outbox endpoint returns same completed Takeout on a lost-response replay.
 payload={'client_order_id':'SYN-'+secrets.token_hex(16),'organization_id':1,'store_id':1,'order_type':'pickup','pickup_no':'SYN-OUTBOX-'+run,'menu_revision':cat['menu_revision'],'items':[req('AN_D1')]}
 x=a.call('/stores/1/orders/idempotent-submit','POST',payload);y=a.call('/stores/1/orders/idempotent-submit','POST',payload);made.append(x['order_id']);assert x['order_id']==y['order_id'] and y['replayed'] and x['order']['status']=='completed'
 report['checks'].append('DINE_IN_FINISH_AND_OLD_OUTBOX_DIRECT_COMPLETED_PASS')
 # Raw determined order totals, live report, page snapshot and print snapshot agree.
 v=f.call(s+'/overview');snap=f.call(s+'/reports','POST',{});assert all(v[k]==snap[k] for k in ('gross','discount','net','tax','total','collected','order_count'))
 expected=json.loads(runtime.sql(c,"select json_build_object('gross',coalesce(sum(subtotal_amount),0),'discount',coalesce(sum(discount_amount),0),'net',coalesce(sum(subtotal_amount-discount_amount),0),'tax',coalesce(sum(coalesce(tax_amount,total_amount-(subtotal_amount-discount_amount))),0),'total',coalesce(sum(total_amount),0),'count',count(*)) from orders where store_id=1 and status='completed' and financial_mode='IN_STORE' and external_source is null and submitted_at::date=(current_timestamp at time zone 'America/Toronto')::date"),parse_float=Decimal)
 assert all(Decimal(str(v[k]))==Decimal(str(expected[k])) for k in ('gross','discount','net','tax','total'));assert v['order_count']==expected['count'];assert v['total']==v['collected']
 key=secrets.token_hex(16);pr=f.call(s+'/reports/print','POST',{'report_id':snap['report_id'],'request_key':key});pr2=f.call(s+'/reports/print','POST',{'report_id':snap['report_id'],'request_key':key});assert pr['id']==pr2['id'] and pr['order_id'] is None
 assert f.call(s+f'/reports/jobs/{pr["id"]}')['id']==pr['id']
 frozen_report=json.loads(runtime.sql(c,f"select payload_snapshot::jsonb->>'report_snapshot' from print_jobs where id={int(pr['id'])} and store_id=1"),parse_float=Decimal)
 assert all(frozen_report[k]==snap[k] for k in ('gross','discount','net','tax','total','collected','order_count','report_id'))
 assert '$'+five_orders.money(v['total']) in pr['rendered_text_snapshot']
 report['checks'].append('FINANCIAL_RECONCILIATION_REPORT_SNAPSHOT_NO_FAKE_ORDER_PASS')
 report['snapshot']=five_orders.json_safe(snap);report['created_order_ids']=made;report['result']='PASS'
finally:
 if 'd' in locals():a.call(f'/admin/printing/devices/{d["device_id"]}/revoke?store_id=1','POST')
 runtime.write_private(runtime.PRIVATE/('qa-sales-printing-'+str(time.time_ns())+'.json'),five_orders.json_safe(report))
print(json.dumps({'result':report.get('result','FAIL'),'checks':report['checks']}))
