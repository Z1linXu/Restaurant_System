#!/usr/bin/env python3
"""Explicit synthetic identity bootstrap, API-only menu setup and HTTP acceptance."""
import argparse, copy, decimal, hashlib, importlib.util, json, pathlib, subprocess, sys, urllib.error, urllib.request, uuid
import allo_runtime as life
D=decimal.Decimal

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl): return None

class Client:
    def __init__(self,run,username=None,password_file='owner-password.txt'):
        self.run,self.state=life.state_for(run); self.base=f"http://localhost:{self.state['ports']['frontend']}"
        self.opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
        self.token=None; self.evidence=[]; self.username=username or self.state['username']; self.password_file=password_file
    def call(self,method,path,payload=None,expected=(200,),headers=None,auth=True):
        if not path.startswith('/api/') or any(c in path for c in ('\r','\n','#','\\')): raise ValueError('relative /api/ path required')
        if auth and self.token is None: self.login()
        h={'Content-Type':'application/json','Origin':self.base,**(headers or {})}
        if auth:h['Authorization']='Bearer '+self.token
        body=None if payload is None else json.dumps(payload,ensure_ascii=False).encode()
        req=urllib.request.Request(self.base+path,data=body,headers=h,method=method)
        try:
            with self.opener.open(req,timeout=30) as r: status=r.status; raw=r.read()
        except urllib.error.HTTPError as e: status=e.code; raw=e.read()
        obj=json.loads(raw) if raw else None
        okay=status in expected and not(status==200 and isinstance(obj,dict) and obj.get('success') is False)
        self.evidence.append({'method':method,'path':path,'status':status,'pass':okay})
        if not okay: raise RuntimeError(json.dumps({'method':method,'path':path,'status':status,'response':obj},ensure_ascii=False))
        return obj.get('data') if isinstance(obj,dict) and 'data' in obj else obj
    def login(self):
        data=self.call('POST','/api/v1/auth/login',{'login_identifier':self.username,'password':(self.run/self.password_file).read_text().strip()},auth=False)
        self.token=data['access_token']
    def save(self,name):life.put(self.run/name,self.evidence)

def bootstrap(run):
    run,state=life.state_for(run)
    # Only this generated empty database is eligible; no existing accounts are overwritten.
    if life.sql(run,'select count(*) from users;',state['database'],False).strip()!='0': raise RuntimeError('Identity bootstrap requires empty users table; refusing overwrite')
    java='import java.io.*; import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; class HashOwner { public static void main(String[] a)throws Exception { System.out.print(new BCryptPasswordEncoder(12).encode(new BufferedReader(new InputStreamReader(System.in)).readLine())); }}'
    (run/'HashOwner.java').write_text(java)
    jars=[]
    for lib in ('spring-security-crypto','spring-jcl'):
        candidates=list((pathlib.Path.home()/'.m2/repository/org/springframework').rglob(lib+'-*.jar'));candidates=[p for p in candidates if not any(x in p.name for x in ('sources','javadoc'))]
        jars.append(str(sorted(candidates)[-1]))
    def hashed(filename):
        return subprocess.check_output([str(life.JAVA),'-cp',':'.join(jars),str(run/'HashOwner.java')],input=(run/filename).read_text(),text=True,env=life.ENV).strip()
    import secrets
    (run/'foreign-password.txt').write_text(secrets.token_urlsafe(20)+'\n'); (run/'foreign-password.txt').chmod(0o600)
    # All interpolated credentials below are locally generated BCrypt/alphanumeric; never printed or logged.
    h1=hashed('owner-password.txt');h2=hashed('foreign-password.txt')
    statement=f"""BEGIN;
INSERT INTO roles(code,name) SELECT 'OWNER','Local Demo Owner' WHERE NOT EXISTS (SELECT 1 FROM roles WHERE code='OWNER');
INSERT INTO organizations(code,name,status,created_at,updated_at) VALUES ('ALLO_LOCAL','Allô Nouille — LOCAL DEMO','active',now(),now()),('NEUTRAL_LOCAL','Neutral — LOCAL DEMO','active',now(),now());
INSERT INTO users(username,full_name,role_id,status,created_at,updated_at) SELECT 'allo.owner','Allô Local Owner',id,'active',now(),now() FROM roles WHERE code='OWNER';
INSERT INTO users(username,full_name,role_id,status,created_at,updated_at) SELECT 'neutral.owner','Neutral Local Owner',id,'active',now(),now() FROM roles WHERE code='OWNER';
INSERT INTO user_credentials(user_id,login_identifier,password_algorithm,password_hash,is_active,created_at,updated_at,password_updated_at) SELECT id,username,'BCRYPT','{h1}',true,now(),now(),now() FROM users WHERE username='allo.owner';
INSERT INTO user_credentials(user_id,login_identifier,password_algorithm,password_hash,is_active,created_at,updated_at,password_updated_at) SELECT id,username,'BCRYPT','{h2}',true,now(),now(),now() FROM users WHERE username='neutral.owner';
INSERT INTO organization_memberships(organization_id,user_id,role_id,role_code,is_active,created_at,updated_at) SELECT o.id,u.id,u.role_id,'OWNER',true,now(),now() FROM organizations o JOIN users u ON u.username='allo.owner' WHERE o.code IN ('ALLO_LOCAL','NEUTRAL_LOCAL');
INSERT INTO organization_memberships(organization_id,user_id,role_id,role_code,is_active,created_at,updated_at) SELECT o.id,u.id,u.role_id,'OWNER',true,now(),now() FROM organizations o JOIN users u ON u.username='neutral.owner' WHERE o.code='NEUTRAL_LOCAL';
COMMIT;"""
    life.sql(run,statement,state['database'],False)
    ids=life.sql(run,"select code||':'||id from organizations order by id;",state['database'],False).strip().splitlines()
    state['organizations']={line.split(':')[0]:int(line.split(':')[1]) for line in ids};life.put(run/'runtime.json',state)
    print('SYNTHETIC_IDENTITY_BOOTSTRAP_PASS')

def configure(run):
    c=Client(run); run,state=c.run,c.state
    if state.get('configured'): raise RuntimeError('Already configured; refusing duplicate setup')
    source=json.loads((life.ROOT/'scripts/local-demo/allo_menu.json').read_text())
    state.setdefault('stores',{}); state['store_codes']={'allo':'ALLO_DEMO_V1','neutral':'NEUTRAL_DEMO_V1'}
    resources=state.setdefault('resources',{}); resources.setdefault('items',{}); resources.setdefault('categories',{});resources.setdefault('addons',{})
    def checkpoint(): life.put(run/'runtime.json',state)
    for key,org,display in [('allo','ALLO_LOCAL','Allô Nouille'),('neutral','NEUTRAL_LOCAL','Demo Neutral Restaurant')]:
        if key not in state['stores']:
            data=c.call('POST',f"/api/v1/owner/organizations/{state['organizations'][org]}/stores",{'store_name':display,'store_code':state['store_codes'][key]},headers={'Idempotency-Key':'allo-local-'+key+'-v1'})
            state['stores'][key]=data['store_id']; checkpoint()
    store=state['stores']['allo']
    current=c.call('GET',f'/api/v1/stores/{store}/merchant-configuration')['safe_summary']
    c.call('PUT',f'/api/v1/stores/{store}/merchant-configuration',{'schema_version':1,'expected_config_revision':current['config_revision'],'brand_name':'Allô Nouille','locale':'zh-CN'})
    if 'station' not in resources:
        resources['station']=c.call('POST','/api/v1/admin/platform/stations',{'store_id':store,'code':'BACK_KITCHEN','name':'后厨','name_zh':'后厨','name_en':'Back Kitchen','station_type':'KITCHEN','sort_order':10,'is_active':True})['id'];checkpoint()
    for i,category in enumerate(source['categories']):
        code=category['code']
        if code not in resources['categories']:
            resources['categories'][code]=c.call('POST','/api/v1/admin/platform/menu/categories',{**category,'store_id':store,'sort_order':(i+1)*10,'is_active':True})['id'];checkpoint()
    all_items=source['items']+source['component_only_items']+source['optional_standalone_items']
    for data in all_items:
        code=data['code']
        if code in resources['items']:continue
        body={k:data[k] for k in ['sku','name_zh','name_en','name_fr','base_price','item_type','sellable']}
        body.update(store_id=store,category_id=resources['categories'][data['category_code']],station_id=resources['station'] if data['logical_station_code'] else None,is_active=True,is_sold_out=False)
        resources['items'][code]=c.call('POST','/api/v1/admin/platform/menu/items',body)['id'];checkpoint()
    for data in source['addons']:
        code=data['code']
        if code not in resources['addons']:
            body={k:data[k] for k in ('code','name_zh','name_en','name_fr','price','active')};body['store_id']=store
            resources['addons'][code]=c.call('POST','/api/v1/admin/menu/addons',body)['id'];checkpoint()
        for item in source['items']:
            if item['category_code']==data['eligible_category_code']:
                c.call('PUT',f"/api/v1/admin/menu/items/{resources['items'][item['code']]}/addons/{resources['addons'][code]}",{'enabled':True})
    done=resources.setdefault('options_completed',[])
    for item in source['items']:
        if item['code'] in done:continue
        choices=source['noodle_options'] if item['category_code']=='NOODLE' else source['protein_options'] if item['code']=='C5' else []
        existing=c.call('GET',f"/api/v1/admin/menu/items/{resources['items'][item['code']]}/options")
        codes={o['option_code'] for o in existing}
        for option in choices:
            if option['option_code'] not in codes:
                c.call('POST',f"/api/v1/admin/menu/items/{resources['items'][item['code']]}/options",{**option,'is_active':True})
        done.append(item['code']);checkpoint()
    groups=[]
    for index,g in enumerate(source['combo_groups']):
        body={k:g[k] for k in ('group_code','name_zh','name_en','name_fr','selection_rule','default_component_code')}
        body.update(enabled=True,required=True,display_order=(index+1)*10,components=[])
        for i,comp in enumerate(g['components']):
            row={k:comp[k] for k in ('component_code','name_zh','name_en','name_fr','business_behavior')}
            row.update(linked_menu_item_id=resources['items'][comp['linked_item_code']],enabled=True,display_order=(i+1)*10)
            body['components'].append(row)
        groups.append(body)
    c.call('PUT','/api/v1/admin/menu/combo-configuration',{'store_id':store,'groups':groups})
    c.call('PUT','/api/v1/admin/menu/pricing-policy',{'store_id':store,'size_small_delta':'0.00','size_regular_delta':'0.00','size_large_delta':'2.00','combo_delta':'0.00'})
    for item in source['items']:
        config=copy.deepcopy(source['customization_overrides'].get(item['code'],source['customization_defaults'].get(item['category_code'],{'schema_version':1,'option_groups':[],'addon_max_quantities':{}})))
        selected=next((offer for offer in source['combos'] if item['code'] in offer['eligible_item_codes']),None)
        c.call('PUT',f"/api/v1/admin/menu/items/{resources['items'][item['code']]}/combo-policy",{'combo_allowed':selected is not None})
        if selected:config['combo']={k:selected[k] for k in ('fixed_price','group_codes')}
        c.call('PUT',f"/api/v1/admin/menu/items/{resources['items'][item['code']]}/customization",config)
    for item in source['optional_standalone_items']:
        c.call('PUT',f"/api/v1/admin/menu/items/{resources['items'][item['code']]}/customization",{'schema_version':1,'option_groups':[],'addon_max_quantities':{}})
    if not resources.get('tables_created'):
        for number in ('T1','T2'):
            c.call('POST','/api/v1/admin/platform/dining-tables',{'store_id':store,'table_code':number,'table_name':number,'capacity':4,'supports_split':False,'is_active':True})
        resources['tables_created']=True;checkpoint()
    content={'schema_version':'PRINTING_DISPLAY_RULES_V1','outputs':['GRAB','FRONTDESK_RECEIPT','HOT_KITCHEN'],'item_aliases':[],'dictionaries':{},'conditional_overrides':[],
        'formatting':{'grab_station_codes':'BACK_KITCHEN','grab_title':'后厨','receipt_language':'zh'}}
    draft=c.call('POST','/api/v1/admin/printing/display-rules/draft',{'store_id':store,'content':content,'summary':'Local synthetic single kitchen; Chinese; no private abbreviations'})
    c.call('POST','/api/v1/admin/printing/display-rules/publish',{'store_id':store,'revision_id':draft['id']})
    c.call('PUT','/api/v1/admin/printing/status',{'store_id':store,'printing_mode':'MOCK'})
    for module,enabled in [('GRAB',True),('FRONTDESK_RECEIPT',True),('HOT_KITCHEN',False)]:
        c.call('PUT',f'/api/v1/admin/printing/assignments/{module}',{'store_id':store,'printer_id':None,'enabled':enabled,'font_size':'NORMAL','takeout_receipt_copies':1})
    state['configured']=True;checkpoint();c.save('setup-http-evidence.json')
    print('API_MENU_SETUP_PASS',{'store':store,'numbered_items':32,'addons':15,'component_only':1,'optional_standalone':2})

def smoke(run):
    import time
    c=Client(run);run,state=c.run,c.state;store=state['stores']['allo'];neutral=state['stores']['neutral']
    catalog=c.call('GET',f'/api/v1/menu/catalog?store_id={store}')
    rows={item['id']:{**item,'category_code':cat['code']} for cat in catalog['categories'] for item in cat['items']}
    codes=state['resources']['items']; groups={g['group_code']:g for g in catalog['combo_configuration']['groups']}
    cases=[];orders=[]
    def check(name,condition,details=None):
        cases.append({'case':name,'pass':bool(condition),'details':details})
        life.put(run/'acceptance-cases-progress.json',cases)
        if not condition: raise AssertionError(name+': '+str(details))
    neutralcat=c.call('GET',f'/api/v1/menu/catalog?store_id={neutral}')
    check('neutral_empty',sum(len(x['items']) for x in neutralcat['categories'])==0)
    check('all_numbered_products',len([code for code in codes if len(code)==2 and code[0] in 'ABCEF'])==32)
    check('component_hidden',codes['SPRING_ROLL_1PC_COMPONENT'] not in rows)
    check('all_prices_three_languages',all(rows[codes[code]].get('name_fr') and rows[codes[code]].get('name_zh') and rows[codes[code]].get('name_en') for code in codes if len(code)==2))
    source=json.loads((life.ROOT/'scripts/local-demo/allo_menu.json').read_text())
    for expected in source['items']:
        current=rows[codes[expected['code']]]
        check(expected['code']+'_source_match',D(str(current['base_price']))==D(expected['base_price']) and all(current[k]==expected[k] for k in ('name_zh','name_en','name_fr')))
    addon_catalog=c.call('GET',f'/api/v1/admin/menu/addons?store_id={store}')
    actual_addons={a['code']:a for a in addon_catalog['addons']}
    for expected in source['addons']:
        actual=actual_addons[expected['code']]
        check(expected['code']+'_source_price',D(str(actual['price']))==D(expected['price']))
    for item in source['items']:
        current=rows[codes[item['code']]]
        if item['category_code']=='NOODLE':
            check(item['code']+'_no_size',not any(o.get('option_group')=='SIZE' for o in current['options']))

    def line(code,quantity=1,combo=None,addons=None,proteins=(),noodle='an_noodle_thin'):
        item=rows[codes[code]];selected=[]
        def option(o):
            return {'option_id':o['id'],'option_type_snapshot':o['option_type'],'option_code_snapshot':o['option_code'],'option_group_snapshot':o.get('option_group'),'option_name_snapshot_zh':o['name_zh'],'option_name_snapshot_en':o['name_en'],'option_name_snapshot_fr':o.get('name_fr'),'option_price_snapshot':str(o['price_delta']),'quantity':quantity}
        for o in item['options']:
            if (o['option_code']==noodle and code.startswith('B')) or o['option_code'] in proteins or o['option_code'] in (addons or {}):
                value=option(o);value['quantity']*=int((addons or {}).get(o['option_code'],1));selected.append(value)
        if combo is not None:
            marker=next(o for o in item['options'] if o.get('option_group')=='COMBO');selected.append(option(marker))
            for group_code in item['customization_config']['combo']['group_codes']:
                group=groups[group_code]
                component_code=combo.get(group_code,group['default_component_code'])
                component=next(o for o in group['components'] if o['component_code']==component_code)
                selected.append({'option_id':component['synthetic_option_id'],'option_type_snapshot':'addon','option_code_snapshot':component_code,'option_group_snapshot':group_code,'option_name_snapshot_zh':component['name_zh'],'option_name_snapshot_en':component['name_en'],'option_name_snapshot_fr':component.get('name_fr'),'option_price_snapshot':'0.00','quantity':quantity})
        return {'menu_item_id':item['id'],'item_name_snapshot_zh':item['name_zh'],'item_name_snapshot_en':item['name_en'],'item_name_snapshot_fr':item.get('name_fr'),'unit_price_snapshot':str(item['base_price']),'category_code_snapshot':item['category_code'],'station_id_snapshot':item['station_id'],'item_sku_snapshot':item['sku'],'quantity':quantity,'options':selected}
    def submit(name,lines,expected_subtotal,invalid=False,mutate=None):
        key='allo-'+uuid.uuid4().hex
        payload={'client_order_id':key,'idempotency_key':key,'organization_id':state['organizations']['ALLO_LOCAL'],'store_id':store,'order_type':'pickup','pickup_no':key[-8:],'menu_revision':catalog['menu_revision'],'expected_subtotal_amount':expected_subtotal,'items':lines}
        if mutate: mutate(payload)
        data=c.call('POST',f'/api/v1/stores/{store}/orders/idempotent-submit',payload,expected=(400,409) if invalid else (200,))
        if invalid:check(name,True);return None
        order=data['order'];orders.append(order['id']);check(name,D(str(order['subtotal_amount']))==D(expected_subtotal),order['subtotal_amount'])
        expected_total=(D(expected_subtotal)+(D(expected_subtotal)*D('0.14975')).quantize(D('0.01'),rounding=decimal.ROUND_HALF_UP))
        check(name+'_tax_total',D(str(order['total_amount']))==expected_total)
        replay=c.call('POST',f'/api/v1/stores/{store}/orders/idempotent-submit',payload)
        check(name+'_stable_retry',replay['replayed'] and replay['order_id']==order['id'])
        return order
    ordinary=submit('B1_plain',[line('B1')],'15.99')
    submit('B1_thick',[line('B1',noodle='an_noodle_thick')],'15.99')
    submit('B1_paid_and_free',[line('B1',addons={'n_extra_beef':1,'n_extra_noodles':1})],'23.98')
    noodle=submit('B1_A1_combo',[line('B1',combo={})],'19.99')
    rolls=submit('B1_A2_combo_q3',[line('B1',3,combo={'COMBO_NOODLE_APPETIZER':'combo_n_a2'})],'59.97')
    rice=submit('C1_rice_combo_q3',[line('C1',3,combo={})],'59.97')
    submit('C6_fixed_price',[line('C6',combo={})],'19.99')
    c5=submit('C5_two_proteins',[line('C5',proteins=('beef','shrimp'))],'19.99')
    drinks=submit('E4_no_kitchen',[line('E4')],'2.99')
    dessert=submit('F1_kitchen',[line('F1')],'3.99')
    submit('C5_one_rejected',[line('C5',proteins=('beef',))],'19.99',invalid=True)
    bad=line('C5',proteins=('beef','chicken'));bad['options'][1]['option_id']=bad['options'][0]['option_id']
    submit('C5_duplicate_rejected',[bad],'19.99',invalid=True)
    submit('free_more_than_one_rejected',[line('B1',addons={'n_extra_noodles':2})],'15.99',invalid=True)
    bad=line('B1');bad['unit_price_snapshot']='0.00';submit('forged_base_rejected',[bad],'0',invalid=True)
    bad=line('B1',addons={'n_extra_beef':1});bad['options'][-1]['option_price_snapshot']='0';submit('forged_addon_rejected',[bad],'15.99',invalid=True)
    bad=line('B4');bad['options'].append({'option_id':-123456789,'option_group_snapshot':'COMBO','option_type_snapshot':'addon','option_code_snapshot':'combo','option_name_snapshot_zh':'套餐','option_name_snapshot_en':'Combo','option_price_snapshot':'0','quantity':1})
    submit('B4_combo_rejected',[bad],'20.99',invalid=True)
    bad=line('C5',proteins=('beef','chicken'));bad['options'].append({'option_id':-123456789,'option_group_snapshot':'COMBO','option_type_snapshot':'addon','option_code_snapshot':'combo','option_name_snapshot_zh':'套餐','option_name_snapshot_en':'Combo','option_price_snapshot':'0','quantity':1})
    submit('C5_combo_rejected',[bad],'19.99',invalid=True)
    # Real DB reads prove the submitted task counts and immutable snapshots; no direct business writes.
    def query(statement):return life.sql(run,statement,state['database'],False).strip()
    def tasks(order):return json.loads(query("select coalesce(json_agg(row_to_json(t)), '[]') from kitchen_tasks t where order_id="+str(order['id'])+';'))
    check('drinks_no_tasks',tasks(drinks)==[])
    check('dessert_back_kitchen',len(tasks(dessert))==1 and tasks(dessert)[0]['station_code']=='BACK_KITCHEN')
    rt=tasks(rice);nt=tasks(rolls)
    check('rice_three_tasks_exact',len(rt)==3 and all(t['quantity']==3 and t['station_code']=='BACK_KITCHEN' for t in rt),[(t['item_name_snapshot_zh'],t['quantity']) for t in rt])
    check('rice_single_roll',sum('（1条）' in t['item_name_snapshot_zh'] for t in rt)==1 and not any('（2条）' in t['item_name_snapshot_zh'] for t in rt))
    check('noodle_two_roll_portion',len(nt)==2 and any('（2条）' in t['item_name_snapshot_zh'] and t['quantity']==3 for t in nt))
    protein_names={o['option_code']:o['name_zh'] for o in json.loads((life.ROOT/'scripts/local-demo/allo_menu.json').read_text())['protein_options']}
    check('C5_proteins_on_task',all(protein_names[code] in tasks(c5)[0]['special_instructions_snapshot'] for code in ('beef','shrimp')))
    def jobs(order):
        for attempt in range(20):
            data=c.call('GET',f"/api/v1/admin/printing/jobs?store_id={store}&orderId={order['id']}")
            if data and all(j['status'] in ('PRINTED','FAILED') for j in data):return data
            time.sleep(.1)
        return data
    check('store_is_mock',c.call('GET',f'/api/v1/admin/printing?store_id={store}')['printing_mode']=='MOCK')
    for name,order in [('rice',rice),('noodle',rolls),('plain',ordinary),('dessert',dessert)]:
        data=jobs(order);k=[j for j in data if j['module_code']=='GRAB'];front=[j for j in data if j['module_code']=='FRONTDESK_RECEIPT']
        check(name+'_one_kitchen_ticket',len(k)==1 and k[0]['status']=='PRINTED' and k[0]['printer_id'] is None and k[0]['printer_endpoint'] is None,[(j['module_code'],j['status']) for j in data])
        check(name+'_one_frontdesk_ticket',len(front)==1 and front[0]['status']=='PRINTED' and front[0]['printer_id'] is None and front[0]['printer_endpoint'] is None)
        check(name+'_receipt_amount',('$'+str(D(str(order['total_amount'])).quantize(D('.01')))) in front[0]['rendered_text_snapshot'])
        for label,job in [('kitchen',k[0]),('frontdesk',front[0])]:
            (run/(name+'-'+label+'-preview.txt')).write_text(job['rendered_text_snapshot'])
    saved=c.call('GET',f"/api/v1/orders/{rolls['id']}")
    check('history_target_is_B1',any(i['menu_item_id']==codes['B1'] for i in saved['items']))
    oldjobs=jobs(rolls)
    config=c.call('GET',f"/api/v1/admin/menu/items/{codes['B1']}/customization")
    edited=copy.deepcopy(config);edited['option_groups'][0]['default_option_codes']=['an_noodle_thick']
    try:
        c.call('PUT',f"/api/v1/admin/menu/items/{codes['B1']}/customization",edited)
        check('history_order_unchanged',c.call('GET',f"/api/v1/orders/{rolls['id']}")==saved)
        check('history_job_unchanged',[(x['id'],x['rendered_text_snapshot']) for x in jobs(rolls)]==[(x['id'],x['rendered_text_snapshot']) for x in oldjobs])
    finally:
        c.call('PUT',f"/api/v1/admin/menu/items/{codes['B1']}/customization",config)
    foreign=Client(run,'neutral.owner','foreign-password.txt')
    foreign.call('GET',f'/api/v1/menu/catalog?store_id={store}',expected=(403,))
    foreign.call('GET',f"/api/v1/orders/{rice['id']}",expected=(403,))
    foreign.call('PUT',f"/api/v1/admin/menu/items/{codes['B1']}/customization",config,expected=(403,))
    foreign.call('GET','/api/v1/store-profiles/ALLO_NOUILLE_PROFILE/versions/v1',expected=(403,))
    check('real_foreign_scope_denied',True)
    c.call('PUT','/api/v1/admin/printing/status',{'store_id':store,'printing_mode':'REAL'},expected=(400,))
    c.call('PUT','/api/v1/admin/printing/status',{'store_id':store,'printing_mode':'PAD_DIRECT'},expected=(400,))
    check('no_real_printers_devices_uber',query('select (select count(*) from printer_configs)+(select count(*) from store_devices)+(select count(*) from uber_eats_store_mappings);')=='0')
    life.put(run/'acceptance-cases.json',cases);c.save('acceptance-http.json');foreign.save('acceptance-foreign-http.json')
    state['smoke_orders']=orders;state['preview_order_ids']={'rice':rice['id'],'noodle':rolls['id']};state['http_acceptance']='PASS';life.put(run/'runtime.json',state)
    print('ALLO_HTTP_PG_MOCK_ACCEPTANCE_PASS',len(cases),'cases',len(c.evidence)+len(foreign.evidence),'HTTP checks')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('command',choices=('bootstrap','configure','smoke'));p.add_argument('--runtime',required=True);a=p.parse_args()
    if a.command=='bootstrap':bootstrap(a.runtime)
    elif a.command=='configure':configure(a.runtime)
    else:smoke(a.runtime)
