#!/usr/bin/env python3
"""Apply the release repair SQL to owned synthetic records, then prove replay and stale Outbox."""
import copy,json,secrets,time
from pathlib import Path
from decimal import Decimal
import runtime,five_orders
c=runtime.config();a=five_orders.Api();a.token=a.call('/auth/login','POST',{'login_identifier':'allo_owner','password':c['owner_password']})['access_token']
assert runtime.sql(c,"select count(*) from stores where id=1 and name='SYNTHETIC Allo QA' and printing_mode='MOCK'")=='1'
# Public menu-shaped synthetic fixture only, matching the verified real group codes.
for number in range(1,9):
 runtime.sql(c,f"""insert into template_item_option_groups(store_id,menu_item_id,group_code,name,kind,selection_mode,required,is_active,sort_order,min_select,max_select)
 select 1,i.id,'allo_spicy_an_b{number}_v1','SYNTHETIC SPICY','SPICY_LEVEL','SINGLE',true,true,2,1,1 from menu_items i where i.store_id=1 and i.sku='AN_B{number}'
 and not exists(select 1 from template_item_option_groups g where g.menu_item_id=i.id and g.group_code='allo_spicy_an_b{number}_v1');
 insert into menu_item_options(menu_item_id,option_type,option_code,option_group,name_zh,name_en,price_delta,is_active,template_group_code,template_default,sort_order,created_at,updated_at)
 select i.id,'spicy_level','SYN_SPICY_NONE','SPICY_LEVEL','不辣','Non spicy',0,true,'allo_spicy_an_b{number}_v1',true,0,localtimestamp,localtimestamp from menu_items i where i.store_id=1 and i.sku='AN_B{number}'
 and not exists(select 1 from menu_item_options o where o.menu_item_id=i.id and o.option_code='SYN_SPICY_NONE');
 update template_item_option_groups set is_active=true,required=true,min_select=1 where store_id=1 and group_code='allo_spicy_an_b{number}_v1';
 update menu_item_options set is_active=true,template_default=true where template_group_code='allo_spicy_an_b{number}_v1' and menu_item_id in(select id from menu_items where store_id=1);""")
cat=a.call('/menu/catalog?store_id=1');menu={i['sku']:{**i,'category_code':g['code']} for g in cat['categories'] for i in g['items']}
b=menu['AN_B1'];opts={o['option_code']:o for g in b['option_groups'] for o in g['options']}
spices=[o for o in b['options'] if o['option_group']=='SPICY_LEVEL']
if not spices:spices=[o for g in b['option_groups'] if g['kind']=='SPICY_LEVEL' for o in g['options']]
# The API uses the exact Store-bound identities; no name matching.
spicy=spices[0];thin=opts['choice_thin']
def row(o):return {'option_id':o['id'],'quantity':1,'option_code_snapshot':o['option_code'],'option_group_snapshot':o['option_group'],'option_name_snapshot_zh':o['name_zh'],'option_name_snapshot_en':o['name_en'],'option_price_snapshot':str(o['price_delta'])}
item={'menu_item_id':b['id'],'quantity':1,'combo_role':'standalone','item_name_snapshot_zh':b['name_zh'],'item_name_snapshot_en':b['name_en'],'unit_price_snapshot':str(b['base_price']),'category_code_snapshot':b['category_code'],'station_id_snapshot':b['station_id'],'item_sku_snapshot':b['sku'],'options':[row(thin),row(spicy)]}
old={'client_order_id':'SYN-OLD-SPICE-'+secrets.token_hex(16),'organization_id':1,'store_id':1,'order_type':'pickup','pickup_no':'SYN-OLD-SPICE','menu_revision':cat['menu_revision'],'items':[item]}
# Create a historical synthetic Takeout with the exact old symptom without invoking Finish or printing again.
d=a.call('/orders','POST',{'store_id':1,'order_type':'pickup','pickup_no':'SYN-REPAIR-'+secrets.token_hex(8),'items':[{'menu_item_id':menu['AN_D1']['id'],'quantity':1,'options':[]}]});o=a.call(f'/orders/{d["id"]}/submit','POST');oid=o['id']
for _ in range(50):
 jobs=a.call(f'/orders/{oid}/print-jobs')
 if jobs and all(j['status']=='PRINTED' for j in jobs):break
 time.sleep(.1)
else:raise AssertionError('Synthetic receipt did not finish')
runtime.sql(c,f"update orders set status='preparing',completed_at=null,submitted_at=(current_timestamp at time zone 'America/Toronto')::date-interval '2 days'+interval '23 hours 59 minutes' where id={oid} and store_id=1")
def protected():return runtime.sql(c,"select md5(string_agg(v,'' order by n)) from (select 'jobs' n,coalesce(string_agg(row_to_json(j)::text,'' order by id),'') v from print_jobs j union all select 'tasks',coalesce(string_agg(row_to_json(t)::text,'' order by id),'') from kitchen_tasks t union all select 'options',coalesce(string_agg(row_to_json(o)::text,'' order by id),'') from order_item_options o)x")
before=protected();sql=Path('deployment/allo/repair-sales-20261115.sql').read_text()
# Only the local fixture intentionally has an inaccessible second Store. Production guard is unchanged.
sql=sql.replace('(SELECT count(*) FROM stores)<>1','(SELECT count(*) FROM stores)<>2')
first=runtime.sql(c,sql);assert protected()==before
x=a.call(f'/orders/{oid}');assert x['status']=='completed' and x['submitted_at'][:10]==x['completed_at'][:10]
rev=int(runtime.sql(c,'select menu_revision from stores where id=1'));assert rev==cat['menu_revision']+1
second=runtime.sql(c,sql);assert protected()==before;assert int(runtime.sql(c,'select menu_revision from stores where id=1'))==rev
assert runtime.sql(c,f"select count(*) from allo_order_adjustments where order_id={oid} and action='TAKEOUT_REPAIR'")=='1'
fresh=a.call('/menu/catalog?store_id=1');fb=next(i for g in fresh['categories'] for i in g['items'] if i['sku']=='AN_B1')
assert not any(g['is_active'] and g['kind']=='SPICY_LEVEL' for g in fb['option_groups'])
x=a.call('/stores/1/orders/idempotent-submit','POST',old);y=a.call('/stores/1/orders/idempotent-submit','POST',old)
assert x['order_id']==y['order_id'] and y['replayed'] and x['order']['status']=='completed'
assert any(o['option_code_snapshot']==spicy['option_code'] for o in x['order']['items'][0]['options'])
# New menu selection has no hidden default spice requirement.
new=copy.deepcopy(old);new['client_order_id']='SYN-NEW-SPICE-'+secrets.token_hex(16);new['pickup_no']='SYN-NEW-SPICE';new['menu_revision']=rev;new['items'][0]['options']=[row(thin)]
n=a.call('/stores/1/orders/idempotent-submit','POST',new);assert n['order']['status']=='completed';assert not any(o['option_group_snapshot']=='SPICY_LEVEL' for o in n['order']['items'][0]['options'])
result={'result':'PASS','sql_replay':'ZERO_ADDITIONAL_WRITES','historical_takeout_order_id':oid,'first':first,'second':second,'historical_day_retained':True,'print_tasks_options_unchanged':True,'old_outbox_spice_preserved':True,'new_selection_no_spice':True,'physical_print':'NOT_TRIGGERED'}
runtime.write_private(runtime.PRIVATE/('qa-sales-repair-'+str(time.time_ns())+'.json'),result);print(json.dumps(result))
