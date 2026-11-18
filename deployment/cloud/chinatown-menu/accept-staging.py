#!/usr/bin/env python3
"""Apply the Chinatown menu through the normal APIs and verify synthetic orders."""
import importlib.util
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.request
from decimal import Decimal

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


deployment = load('stage_deploy', HERE / 'deploy-staging.py')
existing = load('stage_acceptance', HERE.parent / 'pos-five-fixes/acceptance.py')
h, money, tax = existing.h, existing.money, existing.tax
h.API_BASE = deployment.STAGING_URL + '/api/v1'
ROOT, DB, release = deployment.ROOT, deployment.DB, deployment.release
i, p, ops = deployment.i, deployment.p, deployment.ops
NOODLES = {
    'traditional_beef_noodle', 'braised_beef_tendon_noodle', 'braised_beef_noodle',
    'vegetable_noodle', 'pickled_vegetable_beef_noodle', 'dan_dan_noodle',
    'zha_jiang_noodle', 'cold_noodle_shredded_chicken', 'beef_chow_mein',
    'chicken_chow_mein', 'tomato_chow_mein', 'vegetable_chow_mein',
}
PRODUCTS = [
    ('beef_broth', '牛肉汤', 'Beef Broth', '3.00'),
    ('zha_jiang', '加炸酱', 'Extra Fried Bean Sauce', '3.00'),
    ('dan_dan_sauce', '加担担酱', 'Extra Dan Dan Sauce', '3.00'),
    ('bok_choy', '加上海青', 'Extra Shanghai Bok Choy', '3.00'),
    ('radish', '加萝卜', 'Extra Radish', '3.00'),
    ('corn', '加玉米', 'Extra Corn', '3.00'),
    ('free_soup', '免费汤', 'Free Soup', '0.00'),
    ('takeout_chili', '外卖辣椒', 'Takeout Chili', None),
    ('green_onion', '加葱', 'Extra Green Onion', '0.00'),
    ('cilantro', '加香', 'Extra Cilantro', '0.00'),
    ('greens', '加青', 'Extra Greens', None),
]


def db(statement):
    return release.read_sql(DB, statement)


def one(rows, match, label):
    return h.one(rows, match, label)


def fingerprint_other_menus(store_id):
    hashes = {}
    for table in ('menu_items', 'menu_categories', 'store_addons', 'store_removes',
                  'store_combo_groups', 'store_combo_components', 'store_pricing_policies'):
        hashes[table] = db(f"select md5(coalesce(jsonb_agg(to_jsonb(t) order by id),'[]'::jsonb)::text) from {table} t where store_id<>{store_id}")
    hashes['options'] = db(f"select md5(coalesce(jsonb_agg(to_jsonb(o) order by o.id),'[]'::jsonb)::text) from menu_item_options o join menu_items m on m.id=o.menu_item_id where m.store_id<>{store_id}")
    hashes['revision'] = db(f"select md5(jsonb_agg(jsonb_build_array(id,menu_revision) order by id)::text) from stores where id<>{store_id}")
    return hashes


def deactivate_unresolved_egg(api, item_id, option_id):
    # The generic helper intentionally excludes DELETE. This single explicit
    # legacy item-option endpoint is a soft disable, scoped by both IDs.
    url = h.API_BASE + f'/admin/menu/items/{item_id}/options/{option_id}?legacy_addon=true'
    request = urllib.request.Request(url, method='DELETE', headers={'Authorization': 'Bearer ' + api.token})
    with api.opener.open(request, timeout=15) as response:
        result = json.load(response)
        assert response.status == 200 and result['success'] and result['data']['is_active'] is False


def configure(api, store):
    sid = store['id']
    params = f'?store_id={sid}'
    ctx = api.call('/admin/menu/management-context' + params)
    items = ctx['menu_items']
    station = one(ctx['stations'], lambda x: x['code'] == 'NOODLE' and x['is_active'], 'noodle_station')
    assert station['store_id'] == sid
    noodles = {row['sku']: row for row in items if row['sku'] in NOODLES}
    assert set(noodles) == NOODLES and all(row['store_id'] == sid for row in noodles.values())

    def options(item):
        return api.call(f"/admin/menu/items/{item['id']}/options")

    spice = [x for x in options(noodles['traditional_beef_noodle'])
             if x.get('option_group') == 'SPICY_LEVEL' and x['is_active']]
    assert len(spice) >= 2 and all(money(x['price_delta']) == 0 and x['option_code'] for x in spice)
    removes = api.call('/admin/menu/removes' + params)['removes']
    definitions = [
        ('egg_on_side', '蛋单放', 'Egg on the Side', sorted(NOODLES)),
        ('meat_on_side', '肉单放', 'Meat on the Side', ['traditional_beef_noodle', 'braised_beef_tendon_noodle']),
        ('tendon_on_side', '牛筋单放', 'Tendon on the Side', ['braised_beef_tendon_noodle']),
        ('bok_choy_on_side', '上海青单放', 'Bok Choy on the Side', ['vegetable_noodle', 'braised_beef_tendon_noodle']),
    ]
    existing_category = [c for c in ctx['menu_categories'] if c['code'] == 'CATEGORY_ADD_ON' or c['name_en'] == 'Add On']
    assert len(existing_category) <= 1
    category = existing_category[0] if existing_category else api.call('/admin/menu/categories' + params, 'POST',
        {'store_id': sid, 'name_zh': '加选', 'name_en': 'Add On', 'enabled': True})
    assert category['store_id'] == sid and category['code'] == 'CATEGORY_ADD_ON' and category['is_active']
    products = {}
    for key, zh, en, price in PRODUCTS:
        sku = 'add_on_' + key
        matches = [row for row in items if row['sku'] == sku or row['name_zh'] == zh]
        assert len(matches) <= 1, 'Existing ambiguous item: ' + sku
        if matches:
            row = matches[0]
            assert row['store_id'] == sid and row['category_id'] == category['id'] and row['sku'] == sku
            assert (row['base_price'] is None if price is None else money(row['base_price']) == money(price))
        else:
            row = api.call('/admin/platform/menu/items', 'POST', {'store_id': sid, 'category_id': category['id'],
                'station_id': station['id'], 'sku': sku, 'name_zh': zh, 'name_en': en, 'item_type': 'menu_item',
                'base_price': price, 'cost_per_item': None, 'is_active': price is not None and key != 'beef_broth',
                'is_sold_out': False})
        assert row['store_id'] == sid and row['category_id'] == category['id']
        if price is None:
            assert row['base_price'] is None and not row['is_active']
        products[key] = row

    broth = products['beef_broth']
    item_path = f"/admin/menu/items/{broth['id']}"
    portions = {'size_large': '3.00', 'size_small': '2.00'}
    current = options(broth)
    actual = {x['option_code']: str(money(x['portion_price'])) for x in current
              if x.get('portion_price') is not None and x['is_active']}
    if actual != portions:
        api.call(item_path + '/size-configuration', 'PUT', {'enabled_size_codes': list(portions),
            'default_size_code': 'size_large', 'portion_prices': portions})
    current = options(broth)
    for source in spice:
        matches = [x for x in current if x['option_code'] == source['option_code']]
        assert len(matches) <= 1
        payload = {k: source[k] for k in ('option_group', 'option_code', 'name_zh', 'name_en', 'sort_order')}
        if not matches:
            api.call(item_path + '/options', 'POST', {**payload, 'option_type': 'spicy_level',
                                                     'price_delta': 0, 'is_active': True})
        else:
            assert matches[0]['is_active'] and all(matches[0][k] == payload[k] for k in payload)
    definitions += [('chili_on_side', '辣单放', 'Chili on the Side', ['add_on_beef_broth'])]
    targets = {**noodles, 'add_on_beef_broth': broth}
    for code, zh, en, skus in definitions:
        matches = [r for r in removes if r['code'] == code or r['name_zh'] == zh]
        assert len(matches) <= 1
        row = matches[0] if matches else api.call('/admin/menu/removes', 'POST',
            {'store_id': sid, 'code': code, 'name_zh': zh, 'name_en': en, 'active': True})
        assert row['code'] == code and row['name_zh'] == zh and row['active'] and row['store_id'] == sid
        desired = {targets[sku]['id'] for sku in skus}
        assert set(row['item_ids']) <= desired, 'Unexpected existing Remove scope'
        if set(row['item_ids']) != desired:
            api.call(f"/admin/menu/removes/{row['id']}/applicability" + params, 'PUT',
                     {'store_id': sid, 'category_ids': [], 'items': [
                         {'item_id': item_id, 'mode': 'ALLOW'} for item_id in sorted(desired)]})

    addons = api.call('/admin/menu/addons' + params)['addons']
    if not any(x['code'] == 'corn' for x in addons):
        # Preserve the older Staging corn price and existing item eligibility.
        payload = {'store_id': sid, 'option_codes': ['corn'], 'dry_run': True}
        plan = api.call('/admin/menu/addons/reconcile', 'POST', payload)
        assert plan['linked_groups'] == 1 and not plan['conflicts']
        result = api.call('/admin/menu/addons/reconcile', 'POST', {**payload, 'dry_run': False})
        assert result['linked_options'] == plan['linked_options']
        addons = api.call('/admin/menu/addons' + params)['addons']
    corn = one(addons, lambda x: x['code'] == 'corn' and x['active'], 'active_corn')
    for sku in ('traditional_beef_noodle', 'dan_dan_noodle'):
        path = f"/admin/menu/items/{noodles[sku]['id']}/addons"
        assignment = one(api.call(path), lambda x: x['id'] == corn['id'], 'corn_assignment')
        if not assignment['enabled']:
            api.call(f"{path}/{corn['id']}", 'PUT', {'enabled': True})
    dan = noodles['dan_dan_noodle']
    fried_eggs = [x for x in options(dan) if x.get('option_group') == 'ADD_ON' and x['option_code'] == 'fried_egg']
    assert len(fried_eggs) == 1
    egg = fried_eggs[0]
    if egg['is_active']:
        if egg.get('store_addon_id'):
            api.call(f"/admin/menu/items/{dan['id']}/addons/{egg['store_addon_id']}", 'PUT', {'enabled': False})
        else:
            deactivate_unresolved_egg(api, dan['id'], egg['id'])
    assert not one(options(dan), lambda x: x['id'] == egg['id'], 'egg_retained')['is_active']
    if not broth['is_active']:
        broth = api.call(f"/admin/platform/menu/items/{broth['id']}", 'PUT', {**broth, 'is_active': True})
        products['beef_broth'] = broth
    return {'category_code': category['code'], 'items': [
        {'sku': x['sku'], 'name': x['name_zh'], 'base_price': x['base_price'], 'active': x['is_active']}
        for x in products.values()], 'removes': {code: skus for code, _, _, skus in definitions},
        'corn_existing_modifier_price': str(money(corn['price'])), 'dan_dan_paid_fried_egg_option_disabled': egg['id'],
        'broth_portion_prices': portions, 'broth_default_spice': spice[0]['option_code']}


def verify(api, store, catalog, report, root):
    sid = store['id']
    by_sku = {item['sku']: {**item, 'category_code': cat['code']}
              for cat in catalog['categories'] for item in cat['items']}
    addon = one(catalog['categories'], lambda x: x['code'] == 'CATEGORY_ADD_ON', 'catalog_add_on')
    assert {x['sku'] for x in addon['items']} == {'add_on_' + x[0] for x in PRODUCTS if x[3] is not None}
    configured_removes = report['menu']['removes']
    for sku, item in by_sku.items():
        codes = {o['option_code'] for o in item['options'] if o.get('option_group') == 'REMOVE'}
        assert (codes & configured_removes.keys()) == {code for code, skus in configured_removes.items() if sku in skus}
    assert all('corn' in {o['option_code'] for o in by_sku[sku]['options']}
               for sku in ('traditional_beef_noodle', 'dan_dan_noodle'))
    broth = by_sku['add_on_beef_broth']
    sizes = [o for o in broth['options'] if o.get('option_group') == 'SIZE']
    assert [x['option_code'] for x in sizes] == ['size_large', 'size_small']
    assert [money(x['portion_price']) for x in sizes] == [Decimal('3.00'), Decimal('2.00')]
    refreshed = api.call(f'/menu/catalog?store_id={sid}')
    assert refreshed['content_hash'] == catalog['content_hash']
    assert api.call(f'/menu/catalog/revision?store_id={sid}')['menu_revision'] == catalog['menu_revision']

    def line(sku, qty=1, choices=(), size=None, spice=None):
        item = by_sku[sku]
        codes = set(choices)
        selected = [h.option_snapshot(o) for o in item['options'] if o['option_code'] in codes]
        assert len(selected) == len(codes)
        for group, code in [('SIZE', size), ('SOUP_BASE', None), ('SPICY_LEVEL', spice)]:
            pool = [o for o in item['options'] if o.get('option_group') == group]
            if pool:
                if code:
                    selected.append(h.option_snapshot(one(pool, lambda x: x['option_code'] == code, group)))
                else:
                    selected.append(h.option_snapshot(pool[0]))
        return {'menu_item_id': item['id'], 'item_name_snapshot_zh': item['name_zh'],
                'item_name_snapshot_en': item['name_en'], 'unit_price_snapshot': item['base_price'],
                'category_code_snapshot': item['category_code'], 'station_id_snapshot': item['station_id'],
                'item_sku_snapshot': sku, 'item_type_snapshot': item['item_type'], 'quantity': qty,
                'combo_role': 'standalone', 'options': selected}

    def submit(key, lines, table=None):
        identity = 'CHINATOWN_MENU_' + report['sha'][:10] + '_' + key
        payload = {'client_order_id': identity, 'idempotency_key': identity, 'local_draft_id': identity,
                   'store_id': sid, 'organization_id': store['organization_id'], 'server_order_id': None,
                   'order_type': 'dine_in' if table else 'pickup', 'table_no': table, 'pickup_no': None if table else identity,
                   'menu_revision': catalog['menu_revision'],
                   'expected_subtotal_amount': str(sum(map(existing.line_amount, lines))), 'items': lines}
        result = api.call(f'/stores/{sid}/orders/idempotent-submit', 'POST', payload)
        order = api.call(f"/orders/{result['order_id']}")
        assert order['store_id'] == sid
        report['orders'][key] = order['id']
        ops.write_private(root / 'acceptance-progress.json', report)
        return order

    def amounts(order, subtotal):
        expected = money(subtotal)
        assert money(order['subtotal_amount']) == expected
        assert money(order['total_amount']) == expected + tax(expected)

    large = line('add_on_beef_broth', choices=['chili_on_side'], spice='spicy_regular')
    small = line('add_on_beef_broth', size='size_small', spice='spicy_mild', qty=2)
    paid = [line('add_on_' + x[0], qty=3 if x[0] == 'corn' else 1)
            for x in PRODUCTS if x[3] is not None and x[0] != 'beef_broth']
    only_addons = submit('standalone', [large, small, *paid])
    expected_addon_total = Decimal('3.00') + 2 * Decimal('2.00') + sum(
        Decimal(price) * (3 if key == 'corn' else 1) for key, _, _, price in PRODUCTS
        if price is not None and key != 'beef_broth'
    )
    amounts(only_addons, expected_addon_total)
    assert [money(x['line_amount']) for x in only_addons['items'][:2]] == [Decimal('3.00'), Decimal('4.00')]

    tendon = line('braised_beef_tendon_noodle', choices=['egg_on_side','meat_on_side','tendon_on_side','bok_choy_on_side',
                                                       'fried_egg'])
    beef = line('traditional_beef_noodle', choices=['egg_on_side', 'meat_on_side', 'corn'])
    vegetable = line('vegetable_noodle', choices=['egg_on_side','bok_choy_on_side'])
    original_lines = [tendon, beef, vegetable, large]
    for row in original_lines[:3]:
        stripped = {**row, 'options': [o for o in row['options'] if not o['option_code_snapshot'].endswith('_on_side')]}
        assert existing.line_amount(row) == existing.line_amount(stripped)
    tables = json.loads(db(f"""select jsonb_agg(t.table_name order by t.id) from dining_tables t where t.store_id={sid}
        and t.is_active and not exists(select 1 from orders o where o.store_id=t.store_id and o.order_type='dine_in'
        and o.status not in ('completed','cancelled') and (o.table_no=t.table_name or starts_with(o.table_no,t.table_name||'-')))"""))
    assert tables, 'No unoccupied synthetic table'
    order = submit('dine_in', original_lines, tables[0])
    original_total = sum(map(existing.line_amount, original_lines))
    amounts(order, original_total)
    update_tendon = line('braised_beef_tendon_noodle', choices=['egg_on_side', 'tendon_on_side', 'fried_egg'])
    updated = api.call(f"/orders/{order['id']}/updates", 'POST', {
        'idempotency_key': 'CHINATOWN_MENU_' + report['sha'][:10] + '_update',
        'items': [small, update_tendon]})
    amounts(updated['order'], original_total + existing.line_amount(small) + existing.line_amount(update_tendon))
    assert len(updated['order']['items']) == 6
    required = {'GRAB': ('蛋单放','肉单放','牛筋单放','上海青单放','牛肉汤 大份','正常辣','辣单放'),
                'FRONTDESK_RECEIPT': ('蛋单放','肉单放','牛筋单放','上海青单放','牛肉汤 大份','正常辣','辣单放'),
                'HOT_KITCHEN': ('蛋单放','牛筋单放'),
                'GRAB_UPDATE': ('蛋单放','牛筋单放','牛肉汤 小份','少辣'),
                'FRONTDESK_RECEIPT_UPDATE': ('蛋单放','牛筋单放','牛肉汤 小份','少辣'),
                'HOT_KITCHEN_UPDATE': ('蛋单放','牛筋单放')}
    for _ in range(30):
        rows = api.call(f"/orders/{order['id']}/print-jobs")
        if set(required) <= {row['receipt_type'] for row in rows}:
            break
        time.sleep(.5)
    headers = report.setdefault('print_previews', {})
    for receipt, tokens in required.items():
        row = one(rows, lambda x: x['receipt_type'] == receipt, 'missing_' + receipt)
        assert row['status'] == 'PRINTED' and row['store_id'] == sid
        assert not row.get('printer_id') and not row.get('printed_by_device_id') and not row.get('claimed_by_device_id')
        text = row['rendered_text_snapshot']
        assert text.startswith('[[TOP_FEED_16MM]]\n[[LARGE]]' + tables[0] + '[[/LARGE]]')
        assert text.count('[[TOP_FEED_16MM]]') == 1
        assert all(token in text for token in tokens), (receipt, tokens)
        assert not any('走' + token in text for token in tokens if token.endswith('单放'))
        if receipt.endswith('_UPDATE'):
            assert ' (UPDATE)' in text.splitlines()[1] and '辣单放' not in text and '肉单放' not in text
        headers[receipt] = {'job_id': row['id'], 'text': text}
    report['checks'] = ['standalone_add_on_quantity_and_tax', 'scope_all_visible_noodles', 'snapshot_and_update',
                        'six_mock_previews', 'no_physical_print', 'archived_orders_and_other_stores_unchanged']


def main(sha, mode):
    deployment.require_clean_release(sha)
    assert mode in ('apply', 'verify')
    root = ROOT / ('chinatown-menu-' + sha)
    deployed = json.loads((root / 'result.json').read_text())
    assert deployed['result'] == 'PASS' and deployed['sha'] == sha
    for service in ('backend', 'nginx'):
        row = i.inspect(f'restaurant-pos-staging-{service}-1')
        assert row['State']['Running'] and row['Image'] == deployed['images'][service]
    env = ops.env_of(i.inspect('restaurant-pos-staging-backend-1'))
    assert env['APP_ENVIRONMENT'] == 'staging' and env['DB_NAME'] == 'restaurant_pos_staging'
    assert env['APP_PRINTING_ALLOWED_MODES'] == 'DISABLED,MOCK' and env['UBER_EATS_ENABLED'] == 'false'
    store = json.loads(db("select jsonb_agg(to_jsonb(s)) from (select id,code,organization_id,printing_mode,menu_revision from stores where code='CHINATOWN') s"))
    assert len(store) == 1 and store[0]['printing_mode'] == 'MOCK'
    store = store[0]
    baseline_path = root / 'menu-baseline.json'
    if not baseline_path.exists():
        assert mode == 'apply'
        ops.write_private(baseline_path, {'history': p.history_fingerprints(),
            'controls': fingerprint_other_menus(store['id']), 'revision': store['menu_revision']})
    baseline = json.loads(baseline_path.read_text())
    prod = {name: i.fingerprint(name) for name in i.PROD}
    api = h.Api()
    existing.login(api)
    try:
        report = {'sha': sha, 'store_id': store['id'], 'before_revision': baseline['revision'], 'orders': {}}
        if mode == 'apply':
            assert not (root / 'acceptance-progress.json').exists()
            report['menu'] = configure(api, store)
            ops.write_private(root / 'menu-configured.json', report)
        else:
            report = json.loads((root / 'menu-configured.json').read_text())
            assert not (root / 'acceptance-progress.json').exists(), 'Inspect existing test orders before replay'
        catalog = api.call(f"/menu/catalog?store_id={store['id']}")
        report['menu_revision'] = catalog['menu_revision']
        assert catalog['menu_revision'] > baseline['revision']
        verify(api, store, catalog, report, root)
        assert p.history_fingerprints(baseline['history']['cutoffs']) == baseline['history']
        assert fingerprint_other_menus(store['id']) == baseline['controls']
        assert {name: i.fingerprint(name) for name in i.PROD} == prod
        report['result'] = 'PASS'
        ops.write_private(root / 'acceptance.json', report)
        print(json.dumps(report, ensure_ascii=False), flush=True)
    finally:
        api.logout()


if __name__ == '__main__':
    assert len(sys.argv) == 3
    with ops.operation_locks():
        lock = h.action_lock()
        try:
            main(*sys.argv[1:])
        finally:
            os.close(lock)
