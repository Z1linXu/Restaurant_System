#!/usr/bin/env python3
"""Existing Pad HTTP protocol against a report job. Socket sentinel receives zero connections."""
import base64,hashlib,json,secrets,time
import runtime,five_orders
from api_safety import Sentinel,pad,mode,assignment_request,SENTINEL_PORT
c=runtime.config();a=five_orders.Api();a.token=a.call('/auth/login','POST',{'login_identifier':'allo_owner','password':c['owner_password']})['access_token']
assert runtime.sql(c,"select count(*) from stores where id=1 and name='SYNTHETIC Allo QA' and printing_mode='MOCK'")=='1'
printers=a.call('/admin/printing/printers?store_id=1');assign=a.call('/admin/printing/assignments?store_id=1');d=None
before=runtime.sql(c,"select md5(coalesce(string_agg(row_to_json(o)::text,'' order by id),'')) from orders o")
result={'result':'FAIL','physical_print':'HARDWARE_NOT_TESTED'}
try:
 d=a.call('/devices/register','POST',{'store_id':1,'device_name':'SYNTHETIC_REPORT_PROTOCOL_'+secrets.token_hex(6),'device_type':'PAD','platform':'ANDROID','app_version':'1.0.3-protocol-simulation'})
 with Sentinel() as sentinel:
  for p in printers:a.call(f'/admin/printing/printers/{p["id"]}','PUT',{**p,'printer_type':'ESC_POS_TCP','ip_address':'127.0.0.1','port':SENTINEL_PORT,'text_encoding':'GBK','paper_width_mm':80,'font_size':'XS'})
  for x in assign:a.call('/admin/printing/assignments/'+x['module_code'],'PUT',{**assignment_request(x),'font_size':'XS','takeout_receipt_copies':2})
  mode(a,1,'PAD_DIRECT')
  snap=a.call('/allo/stores/1/sales/reports','POST',{});key=secrets.token_hex(16)
  job=a.call('/allo/stores/1/sales/reports/print','POST',{'report_id':snap['report_id'],'request_key':key});jid=job['id']
  assert job['status']=='PENDING' and job['order_id'] is None and job['receipt_type']=='SALES_OVERVIEW'
  token=secrets.token_hex(16)
  pad(a,d,f'/printing/jobs/{jid}/claim','POST',{'client_attempt_token':token,'lease_seconds':15})
  pad(a,d,f'/printing/jobs/{jid}/start-print','POST',{'client_attempt_token':token,'lease_seconds':60})
  payload=pad(a,d,f'/printing/jobs/{jid}/payload');b=base64.b64decode(payload['escpos_payload_base64'],validate=True)
  intent=json.loads(runtime.sql(c,f'select payload_snapshot from print_jobs where id={jid}'))
  assert intent['report_intent']['execution_mode']=='PAD_DIRECT' and intent['receipt_artifact']['font']=='SMALL'
  assert hashlib.sha256(b).hexdigest()==intent['receipt_artifact']['bytes_sha256'] and b'\x1d\x21\x11' in b
  assert b.count(b'\x1b\x40')==1 and b.count(b'\x1d\x56\x41\x10')==1
  assert len([j for j in pad(a,d,'/stores/1/printing/jobs/pending') if j['id']==jid])==0
  pad(a,d,f'/printing/jobs/{jid}/fail','POST',{'client_attempt_token':token,'error_code':'SYNTHETIC_NO_PHYSICAL_PRINT','error_message':'Protocol accepted; zero printer bytes sent','raw_result':'{"bytes_written":0,"uncertain":false}'})
  again=a.call('/allo/stores/1/sales/reports/print','POST',{'report_id':snap['report_id'],'request_key':key});assert again['id']==jid and again['status']=='FAILED'
  assert sentinel.connections==0
  result={'result':'PASS','report_id':snap['report_id'],'job_id':jid,'payload_bytes':len(b),'payload_sha256':hashlib.sha256(b).hexdigest(),'xs_bytes':'1D 21 11','copies':1,'claim_start_payload_fail':'PASS','same_request_no_requeue':True,'sentinel_connections':0,'physical_print':'HARDWARE_NOT_TESTED'}
finally:
 mode(a,1,'MOCK')
 for p in printers:a.call(f'/admin/printing/printers/{p["id"]}','PUT',p)
 for x in assign:a.call('/admin/printing/assignments/'+x['module_code'],'PUT',assignment_request(x))
 if d:a.call(f'/admin/printing/devices/{d["device_id"]}/revoke?store_id=1','POST')
assert runtime.sql(c,"select md5(coalesce(string_agg(row_to_json(o)::text,'' order by id),'')) from orders o")==before
runtime.write_private(runtime.PRIVATE/('qa-sales-pad-report-'+str(time.time_ns())+'.json'),result);print(json.dumps(result))
