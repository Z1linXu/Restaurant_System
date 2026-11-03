#!/usr/bin/env python3
"""Exact-SHA Staging Store18 only; normal login/API writes, readonly DB proof, no transport."""
import datetime
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('finish_release', HERE / 'deploy-staging.py')
d = importlib.util.module_from_spec(spec); spec.loader.exec_module(d)
spec = importlib.util.spec_from_file_location('existing_stage_acceptance', HERE.parent / 'pos-five-fixes/acceptance.py')
a = importlib.util.module_from_spec(spec); spec.loader.exec_module(a)
a.h.API_BASE = 'https://staging-pos.lanzhounoodlesmtl.com/api/v1'
TARGET = 18
os.umask(0o077)


def db(sql):
    return d.release.read_sql('restaurant-pos-staging-db-1', sql)


class Api(a.h.Api):
    def __init__(self, cutoff):
        super().__init__(); self.owned = set(); self.cutoff = cutoff
    def call(self, path, method='GET', body=None, **kwargs):
        assert method == 'GET' or (method == 'POST' and (
            path in ('/auth/login', '/auth/logout')
            or path == f'/stores/{TARGET}/orders/finish-all-tables'
            or path == '/orders' and body.get('store_id') == TARGET
            or re.fullmatch(r'/orders/(\d+)/(submit|cancel)', path) and int(path.split('/')[2]) in self.owned
        )), 'Unapproved acceptance mutation'
        result = super().call(path, method, body, **kwargs)
        if path == '/orders' and method == 'POST':
            assert result['id'] > self.cutoff and result['store_id'] == TARGET, 'Existing orders are protected'
            self.owned.add(result['id'])
        return result


def main(sha):
    assert re.fullmatch('[0-9a-f]{40}', sha)
    assert HERE == Path('/srv/restaurant-pos/staging/releases') / sha / 'deployment/cloud/finish-all-tables'
    assert d.release.i.run(['git', '-C', str(HERE.parents[2]), 'rev-parse', 'HEAD']).decode().strip() == sha
    runtime = d.release.i.inspect('restaurant-pos-staging-backend-1')
    env = d.release.ops.env_of(runtime)
    assert runtime['Config']['Labels']['org.opencontainers.image.revision'] == sha
    assert env['APP_ENVIRONMENT'] == 'staging' and env['DB_NAME'] == 'restaurant_pos_staging'
    assert env['UBER_EATS_ENVIRONMENT'] == 'sandbox' and env['UBER_EATS_WORKER_ENABLED'] == 'false'
    assert db('select printing_mode from stores where id=18') == 'MOCK', 'Existing synthetic MOCK fixture required'
    assert db("select count(*) from orders where store_id=18 and order_type='dine_in' and status not in ('completed','cancelled')") == '0', 'Do not finish pre-existing active orders'
    before_prod = {n:d.release.i.fingerprint(n) for n in d.release.i.PROD}
    controls = db("select md5(coalesce(jsonb_agg(to_jsonb(o) order by id),'[]'::jsonb)::text) from orders o where store_id<>18")
    tables = db("select md5(jsonb_agg(to_jsonb(t) order by id)::text) from dining_tables t")
    cutoff = int(db('select coalesce(max(id),0) from orders'))
    old_target = db(f"select md5(coalesce(jsonb_agg(to_jsonb(o) order by id),'[]'::jsonb)::text) from orders o where store_id=18 and id<={cutoff}")
    api = Api(cutoff); user = a.login(api)
    prefix = 'FINISH_ALL_' + uuid.uuid4().hex[:10]
    raw = db("""select row_to_json(t) from(select i.id,i.sku,i.item_type,i.name_zh,i.name_en,i.base_price,i.station_id,c.code category_code
        from menu_items i join menu_categories c on c.id=i.category_id
        where i.store_id=18 and i.is_active and not coalesce(i.is_sold_out,false) and i.item_type='SINGLE'
        and not exists(select 1 from menu_item_bom b where b.menu_item_id=i.id)
        order by case when i.sku='coke' then 0 else 1 end,i.id limit 1)t""")
    item = json.loads(raw)
    line = {'menu_item_id':item['id'],'quantity':1,'unit_price_snapshot':item['base_price'],
        'item_name_snapshot_zh':item['name_zh'],'item_name_snapshot_en':item['name_en'],
        'item_sku_snapshot':item['sku'],'item_type_snapshot':item['item_type'],
        'station_id_snapshot':item['station_id'],'category_code_snapshot':item['category_code'],
        'notes':prefix,'options':[]}
    def create(table=None, pickup=None, submit=True):
        row = api.call('/orders', 'POST', {'store_id':18,'order_type':'pickup' if pickup else 'dine_in',
            'table_no':table,'pickup_no':pickup,'items':[line] if submit else []})
        if submit: row = api.call(f"/orders/{row['id']}/submit", 'POST', {})
        return row['id']
    def states(ids):
        return {str(i):api.call(f'/orders/{i}')['status'] for i in ids}
    def counts():
        return int(db('select count(*) from print_jobs'))
    def quiet_owned_outbox():
        ids=','.join(str(i) for i in sorted(api.owned))
        for _ in range(80):
            if db(f"select count(*) from order_dispatch_outbox where order_id in ({ids}) and status in ('PENDING','PROCESSING')") == '0': return
            time.sleep(.25)
        raise RuntimeError('Owned MOCK dispatch did not settle; no Finish attempt')
    def financial_identity(ids):
        rows=[api.call(f'/orders/{i}') for i in ids]
        return a.h.digest([[row['id'],row.get('subtotal_amount'),row.get('total_amount'),
            [[item.get(k) for k in ['id','menu_item_id','quantity','unit_price','line_amount','options']] for item in row['items']]] for row in rows])
    first = [create('1里-A'),create('1里-B'),create('2')]
    draft = create('1外-A', submit=False)
    takeout = create(pickup=prefix)
    preview = api.call('/stores/18/orders/finish-all-tables/preview')
    assert {o['order_id'] for o in preview['orders']} == set(first)
    assert preview['table_slot_count'] == 3 and preview['blocked_tables']
    # Cancel is GET-only: no POST and no state/print change.
    after_preview = states(first + [draft,takeout]); assert all(after_preview[str(i)] == 'preparing' for i in first)
    assert db(f"select count(*) from order_finish_batches where id='{preview['batch_id']}'") == '0'
    fresh = create('1外-B')
    quiet_owned_outbox()
    money_before = financial_identity(first)
    jobs_before = counts()
    result = api.call('/stores/18/orders/finish-all-tables', 'POST', {'preview_token':preview['preview_token']})
    assert result['completed'] == 3 and result['skipped'] == result['failed'] == 0
    assert result['released_slot_count'] == 3
    actual = states(first + [draft,takeout,fresh])
    assert all(actual[str(i)] == 'completed' for i in first)
    assert actual[str(draft)] == 'draft' and actual[str(takeout)] == actual[str(fresh)] == 'preparing'
    assert counts() == jobs_before, 'Finish must not dispatch another print'
    assert money_before == financial_identity(first), 'Order/item money and history changed'
    replay = api.call('/stores/18/orders/finish-all-tables', 'POST', {'preview_token':preview['preview_token']})
    assert replay['results'] == result['results'] and replay['completed'] == 3 and counts() == jobs_before
    # Finish only the newly previewed test order; protected draft is explicitly cancelled.
    next_preview = api.call('/stores/18/orders/finish-all-tables/preview')
    assert {o['order_id'] for o in next_preview['orders']} == {fresh}
    second = api.call('/stores/18/orders/finish-all-tables','POST',{'preview_token':next_preview['preview_token']})
    assert second['completed'] == 1
    api.call(f'/orders/{draft}/cancel','POST',{})
    assert api.call('/stores/18/orders/finish-all-tables/preview')['order_count'] == 0
    assert controls == db("select md5(coalesce(jsonb_agg(to_jsonb(o) order by id),'[]'::jsonb)::text) from orders o where store_id<>18")
    assert old_target == db(f"select md5(coalesce(jsonb_agg(to_jsonb(o) order by id),'[]'::jsonb)::text) from orders o where store_id=18 and id<={cutoff}")
    assert tables == db("select md5(jsonb_agg(to_jsonb(t) order by id)::text) from dining_tables t")
    assert all(d.release.i.fingerprint(n) == v for n,v in before_prod.items())
    # Leave an owned, documented set for real browser Cancel/Confirm and viewport checks.
    ui_orders = [create('1里-A'),create('1里-B'),create('2')]
    quiet_owned_outbox()
    root = Path('/srv/restaurant-pos/staging') / ('finish-all-tables-' + sha)
    report = {'source_sha':sha,'status':'PASS','store_id':18,'batch_id':result['batch_id'],
        'completed_order_ids':first+[fresh],'draft_cancelled_id':draft,'takeout_control_id':takeout,
        'batch_result':result,'backend_states':actual,'preview_cancel_no_write':True,
        'new_order_after_preview_excluded':True,'retry_same_batch_identical':True,'history_preserved':True,
        'no_extra_finish_print_jobs':True,'other_stores_unchanged':True,'table_configuration_unchanged':True,
        'production_containers_unchanged':True,'ui_order_ids':ui_orders,'ui_print_jobs_before':counts(),
        'observed_at':datetime.datetime.now(datetime.timezone.utc).isoformat()}
    d.release.ops.write_private(root/'acceptance-api.json',report)
    d.release.ops.write_private(root/'browser-session.private.json',{'access_token':api.token,'refresh_token':api.refresh,'user':user})
    print(json.dumps(report),flush=True)


if __name__ == '__main__':
    assert len(sys.argv) == 2
    lock = a.h.action_lock()
    try: main(sys.argv[1])
    finally: os.close(lock)
