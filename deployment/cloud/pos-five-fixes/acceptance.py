#!/usr/bin/env python3
"""One bounded public-HTTPS Staging acceptance. No Production target or real transport.

Run from the reviewed exact release. All writes are limited to CHINATOWN fixture
Store 18, newly created run-coded orders/items/Remove, its MOCK mode and one
NO_SPICY rule. Existing orders/jobs are never updated. Transport ambiguity stops
without mutation retry. Credentials are read only from the two existing private
server files. The new private report retains IDs for inspection; no cleanup runs.
"""
from __future__ import annotations

import argparse
import copy
import datetime
from decimal import Decimal, ROUND_HALF_UP
import fcntl
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import sys
import time

sys.dont_write_bytecode = True
REPO = Path(__file__).resolve().parents[3]
_spec = importlib.util.spec_from_file_location('pos_five_menu_acceptance', REPO / 'scripts/menu-simplification-acceptance.py')
h = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(h)  # Existing helpers only; main/old runtime validator are not invoked.
API_BASE = 'https://staging-pos.lanzhounoodlesmtl.com/api/v1'
h.API_BASE = API_BASE
ROOT = Path('/srv/restaurant-pos/staging')
CREDENTIALS = ROOT / 'state/twin001-staff-credentials-v1.json'
FALLBACK = ROOT / 'state/stg005-owner-credential-20260809-r01'
TARGET = 18
NoGo, require = h.NoGo, h.require
CENT = Decimal('.01')


def money(value):
    return Decimal(str(value)).quantize(CENT, rounding=ROUND_HALF_UP)


def line_amount(line):
    return money((Decimal(str(line['unit_price_snapshot'])) + sum(
        (Decimal(str(option.get('option_price_snapshot', 0))) * option['quantity'] for option in line['options']), Decimal(0)
    )) * line['quantity'])


def tax(subtotal):
    return money(subtotal * Decimal('.14975'))


def component_id(group, code):
    key = group.strip().upper() + ':' + code.strip().lower()
    legacy = {'COMBO_EGG:combo_tea_egg': -20101, 'COMBO_EGG:combo_fried_egg': -20102,
              'COMBO_SIDE:combo_edamame': -20201, 'COMBO_SIDE:combo_shredded_potato': -20202,
              'COMBO_SIDE:combo_cucumber_salad': -20203}
    if key in legacy:
        return legacy[key]
    value = 0x811c9dc5
    for char in key:
        value = ((value ^ ord(char)) * 0x01000193) & 0xffffffff
    return -300000 - value % 600000


def login(api):
    credentials = json.loads(h.private_bytes(CREDENTIALS))
    try:
        return api.login(credentials)
    except NoGo as error:
        # Only an explicit credential rejection permits one alternate existing
        # password. Network errors/403/unknown failures never cause a retry.
        if str(error) != 'http_status_401_expected_200':
            raise
    alternate = dict(credentials)
    alternate['owner_login_password'] = h.private_bytes(FALLBACK).decode('utf-8').strip()
    return api.login(alternate)


class StagingApi(h.Api):
    def __init__(self, run_id):
        super().__init__()
        self.prefix = 'FIVE_' + run_id
        self.orders, self.jobs, self.items, self.removes = set(), set(), set(), set()

    def check_write(self, path, method, body):
        require(not re.search(r'(?:devices|integrations|uber|/payload|/claim|start-print|connection-test|modules/test)', path, re.I), 'devices_external_and_transport_api_forbidden')
        if method == 'GET':
            return
        require(isinstance(body, dict), 'mutation_body_required')
        if path in ('/auth/login', '/auth/logout'):
            return
        if path == '/admin/printing/status':
            require(body == {'store_id': TARGET, 'printing_mode': 'MOCK'}, 'mock_target_only')
            return
        if path in ('/admin/printing/display-rules/draft', '/admin/printing/display-rules/publish'):
            require(body.get('store_id') == TARGET, 'printing_rule_target_only')
            return
        if path == f'/stores/{TARGET}/orders/idempotent-submit':
            require(body.get('store_id') == TARGET and body.get('pickup_no', '').startswith(self.prefix)
                    and body.get('idempotency_key', '').startswith(self.prefix)
                    and body.get('client_order_id') == body.get('idempotency_key'), 'new_order_identity_required')
            return
        match = re.fullmatch(r'/orders/(\d+)/(updates|reprint)', path)
        if match:
            require(int(match[1]) in self.orders and body.get('idempotency_key', '').startswith(self.prefix)
                    and 'printer_id' not in body, 'new_order_mutation_only')
            return
        match = re.fullmatch(r'/admin/printing/jobs/(\d+)/reprint', path)
        if match:
            require(int(match[1]) in self.jobs and body.get('idempotency_key', '').startswith(self.prefix), 'new_job_reprint_only')
            return
        if path == '/admin/platform/menu/items':
            require(body.get('store_id') == TARGET and body.get('sku', '').startswith(self.prefix)
                    and money(body.get('base_price')) == 0, 'new_zero_price_fixture_only')
            return
        if path in ('/admin/menu/removes', '/admin/menu/removes/reconcile'):
            require(body.get('store_id') == TARGET, 'remove_target_only')
            if path.endswith('/removes'):
                require(body.get('code', '').startswith(self.prefix.lower()), 'new_run_remove_only')
            return
        match = re.fullmatch(r'/admin/menu/removes/(\d+)(/applicability)?\?store_id=18', path)
        if match:
            require(int(match[1]) in self.removes and body.get('store_id') == TARGET, 'new_remove_mutation_only')
            if match[2]:
                require(body.get('category_ids') == [] and all(row.get('item_id') in self.items for row in body.get('items', [])), 'new_fixture_applicability_only')
            return
        raise NoGo('unapproved_mutation_path')

    def call(self, path, method='GET', body=None, **kwargs):
        self.check_write(path, method, body)
        return super().call(path, method, body, **kwargs)


def runtime(sha):
    require(re.fullmatch('[0-9a-f]{40}', sha), 'exact_sha_required')
    release = ROOT / 'releases' / sha
    require(Path(__file__).resolve() == release / 'deployment/cloud/pos-five-fixes/acceptance.py', 'exact_release_script_required')
    require(h.command(['git', '-C', str(release), 'rev-parse', 'HEAD']).decode().strip() == sha, 'release_sha_mismatch')
    require(not h.command(['git', '-C', str(release), 'status', '--porcelain']).strip(), 'release_must_be_clean')
    deployment = json.loads(h.private_bytes(ROOT / ('pos-five-release-' + sha) / 'result.json'))
    require(deployment.get('source_sha') == sha and deployment.get('result') == 'PASS'
            and deployment.get('flyway') == '38' and deployment.get('production_unchanged') is True,
            'reviewed_staging_deployment_evidence_required')
    rows = json.loads(h.command(['docker', 'inspect', 'restaurant-pos-staging-backend-1', 'restaurant-pos-staging-nginx-1']))
    require(len(rows) == 2, 'staging_runtime_missing')
    for row in rows:
        require(row['State']['Running'] and row['Config']['Labels'].get('com.docker.compose.project') == 'restaurant-pos-staging'
                and row['Config']['Labels'].get('org.opencontainers.image.revision') == sha, 'staging_runtime_sha_mismatch')
    backend = next(row for row in rows if row['Name'] == '/restaurant-pos-staging-backend-1')
    env = dict(value.split('=', 1) for value in backend['Config']['Env'])
    require(env.get('APP_ENVIRONMENT') == 'staging' and env.get('UBER_EATS_ENVIRONMENT') == 'sandbox'
            and env.get('DB_NAME') == 'restaurant_pos_staging'
            and 'MOCK' in env.get('APP_PRINTING_ALLOWED_MODES', '').split(','), 'staging_runtime_policy_mismatch')
    return {'source_sha': sha, 'images': [row['Image'] for row in rows], 'public_api': API_BASE}


class Acceptance:
    def __init__(self, api, run_id, report, checkpoint):
        self.api, self.run_id, self.report, self.checkpoint = api, run_id, report, checkpoint
        self.prefix = api.prefix
        self.store, self.catalog, self.other_before = None, None, None

    def record(self, label, **evidence):
        self.report['checks'].append({'check': label, 'status': 'PASS', **evidence})
        self.checkpoint()

    def menu(self, store_id=TARGET):
        return self.api.call(f'/menu/catalog?store_id={store_id}')

    def control_snapshots(self):
        result = {}
        for row in self.workspaces['stores']:
            if row['id'] == TARGET:
                continue
            sid = row['id']
            result[str(sid)] = {name: h.digest(self.api.call(path, expected=(200, 403))) for name, path in {
                'printing': f'/admin/printing?store_id={sid}',
                'rules': f'/admin/printing/display-rules?store_id={sid}',
                'removes': f'/admin/menu/removes?store_id={sid}',
            }.items()}
        return result

    def prepare(self):
        self.workspaces = self.api.call('/me/workspaces')
        overview = self.api.call('/owner/overview')
        workspace = h.one(self.workspaces['stores'], lambda row: row.get('code') == 'CHINATOWN', 'chinatown_workspace_ambiguous')
        owners = [store for org in overview['organizations'] for store in org.get('stores', [])]
        self.store = h.one(owners, lambda row: row.get('code') == 'CHINATOWN', 'chinatown_overview_ambiguous')
        require(self.store['id'] == workspace['id'] == TARGET and self.store.get('store_kind') == 'VALIDATION_FIXTURE'
                and self.store.get('lifecycle_status') == 'ACTIVE'
                and self.store.get('provisioning_source') == 'PHASE_B_OWNER_PROVISIONING', 'exact_chinatown_fixture_required')
        self.organization_id = workspace['organization_id']
        self.other_before = self.control_snapshots()
        printing = self.api.call(f'/admin/printing?store_id={TARGET}')
        require(all(not printer.get('ip_address') for printer in printing.get('printers', [])), 'target_fixture_has_hardware_endpoint')
        require(all(any(row.get('module_code') == module and row.get('enabled') is True for row in printing.get('assignments', []))
                    for module in ('GRAB', 'FRONTDESK_RECEIPT', 'HOT_KITCHEN')), 'enabled_mock_assignments_required')
        if printing.get('printing_mode') != 'MOCK':
            self.api.call('/admin/printing/status', 'PUT', {'store_id': TARGET, 'printing_mode': 'MOCK'})
        self.assert_mock()
        self.record('target_fixture_and_mock_boundary', store_id=TARGET, code='CHINATOWN', previous_mode=printing.get('printing_mode'), other_store_digests=self.other_before)
        self.publish_no_spicy()
        self.catalog = self.menu()
        require(self.catalog['store_id'] == TARGET and self.catalog['organization_id'] == self.organization_id, 'catalog_scope_mismatch')
        self.report['pricing_policy'] = self.catalog.get('pricing_policy')

    def assert_mock(self):
        current = self.api.call(f'/admin/printing?store_id={TARGET}')
        require(current['printing_mode'] == 'MOCK' and all(not row.get('ip_address') for row in current.get('printers', [])), 'mock_boundary_changed')

    def publish_no_spicy(self):
        settings = self.api.call(f'/admin/printing/display-rules?store_id={TARGET}')
        content = copy.deepcopy(settings['active_revision']['content'])
        entries = content['dictionaries']['SPICINESS']
        entry = h.one(entries, lambda row: isinstance(row, dict) and row.get('semantic_code') == 'NO_SPICY', 'no_spicy_semantic_missing')
        desired = {'GRAB': '（走s）', 'HOT_KITCHEN': '（走s）', 'FRONTDESK_RECEIPT': '不辣'}
        if all(entry.get('outputs', {}).get(key) == value for key, value in desired.items()):
            self.record('no_spicy_already_published', revision_id=settings['active_revision']['id'])
            return
        require(settings.get('draft_revision') is None, 'existing_rule_draft_protected')
        entry.setdefault('outputs', {}).update(desired)
        draft = self.api.call('/admin/printing/display-rules/draft', 'POST', {'store_id': TARGET, 'content': content, 'summary': self.prefix + ' NO_SPICY acceptance'})
        if draft.get('lifecycle_result') != 'ALREADY_ACTIVE':
            self.api.call('/admin/printing/display-rules/publish', 'POST', {'store_id': TARGET, 'revision_id': draft['id']})
        saved = self.api.call(f'/admin/printing/display-rules?store_id={TARGET}')
        actual = h.one(saved['active_revision']['content']['dictionaries']['SPICINESS'], lambda row: isinstance(row, dict) and row.get('semantic_code') == 'NO_SPICY', 'no_spicy_saved_missing')
        require(all(actual['outputs'].get(key) == value for key, value in desired.items()), 'no_spicy_save_readback_mismatch')
        self.record('no_spicy_publish_readback', revision_id=saved['active_revision']['id'])

    def item(self, sku):
        return h.one(h.flatten_menu(self.catalog), lambda row: row.get('sku') == sku and row.get('is_active'), 'required_menu_item_missing_' + sku)

    def line(self, item, size=None, combo=False, egg=False, soup=None):
        options = [row for row in item['options'] if row.get('is_active')]
        chosen = []
        for option_type in ('size', 'soup_base', 'noodle_type', 'spicy_level'):
            candidates = sorted([row for row in options if row.get('option_type') == option_type], key=lambda row: (row.get('sort_order') or 0, row['id']))
            code = size if option_type == 'size' else soup if option_type == 'soup_base' else 'spicy_none' if option_type == 'spicy_level' else None
            if candidates:
                selected = h.one(candidates, lambda row: row.get('option_code') == code, 'required_option_missing_' + str(code)) if code else candidates[0]
                chosen.append(h.option_snapshot(selected))
        if combo:
            chosen.append(h.option_snapshot(h.one(options, lambda row: row.get('option_group') == 'COMBO', 'combo_upgrade_missing')))
            config = self.catalog.get('combo_configuration') or {}
            require(config.get('store_id') == TARGET, 'combo_config_store_mismatch')
            for group in config.get('groups', []):
                if group.get('enabled') is False:
                    continue
                components = [row for row in group['components'] if row.get('enabled')]
                require(components or not group.get('required', True), 'required_combo_group_empty')
                if not components:
                    continue
                default = item.get('default_combo_egg_component_code') if group.get('group_code', group.get('component_group')) == 'COMBO_EGG' else None
                default = default or group.get('default_component_code')
                selected = next((row for row in components if row['component_code'] == default), sorted(components, key=lambda row: (row['display_order'], row['component_code']))[0])
                group_code = group.get('group_code') or group['component_group']
                chosen.append(h.option_snapshot({'option_group': group_code, 'option_code': selected['component_code'],
                    'name_zh': selected['name_zh'], 'name_en': selected['name_en'], 'price_delta': 0}, component_id(group_code, selected['component_code'])))
        if egg:
            egg_code = 'tea_egg' if egg is True else egg
            paid_egg = h.one(options, lambda row: row.get('option_group') == 'ADD_ON' and row.get('option_code') == egg_code, 'paid_egg_missing_' + egg_code)
            require(money(paid_egg['price_delta']) > 0, 'paid_egg_price_required')
            chosen.append(h.option_snapshot(paid_egg))
        category = h.one(self.catalog['categories'], lambda row: row['id'] == item['category_id'], 'item_category_missing')
        return {'menu_item_id': item['id'], 'item_name_snapshot_zh': item['name_zh'], 'item_name_snapshot_en': item['name_en'],
            'unit_price_snapshot': item['base_price'], 'category_code_snapshot': category['code'], 'station_id_snapshot': item['station_id'],
            'item_sku_snapshot': item['sku'], 'item_type_snapshot': item['item_type'], 'quantity': 2, 'combo_role': 'standalone',
            'combo_group_no': None, 'notes': None, 'options': chosen}

    def hot_line(self, beef):
        # Match the existing HotKitchenPrintEligibilityService contract using
        # only active menu rows. The fixture may legitimately have no wok SKU.
        wok_skus = {'beef_chow_mein', 'chicken_chow_mein', 'tomato_chow_mein', 'vegetable_chow_mein'}
        def has_no_spicy(item):
            return any(row.get('is_active') and row.get('option_type') == 'spicy_level'
                       and row.get('option_code') == 'spicy_none' for row in item['options'])
        woks = sorted([row for row in h.flatten_menu(self.catalog) if row.get('is_active')
                       and row.get('sku') in wok_skus and has_no_spicy(row)], key=lambda row: row['id'])
        if woks:
            selected = woks[0]
            return self.line(selected), {'eligibility': 'existing_active_wok_sku', 'sku': selected['sku'], 'menu_item_id': selected['id']}
        require(beef.get('is_active') and has_no_spicy(beef), 'hot_no_spicy_source_unavailable')
        line = self.line(beef, egg='fried_egg')
        return line, {'eligibility': 'existing_paid_fried_egg_option', 'sku': beef['sku'], 'menu_item_id': beef['id'], 'option_code': 'fried_egg'}

    def jobs(self, order_id, module, batch=None):
        for _ in range(40):
            rows = self.api.call(f'/orders/{order_id}/print-jobs')
            matches = [row for row in rows if row['module_code'] == module and row.get('order_update_batch_id') == batch]
            if matches and all(row['status'] == 'PRINTED' for row in matches):
                for row in matches:
                    require(row['store_id'] == TARGET and row['order_id'] == order_id and row.get('printer_id') is None
                            and row.get('printed_by_device_id') is None and row.get('claimed_by_device_id') is None
                            and not row.get('printer_endpoint') and row.get('execution_mode') in (None, 'MOCK'), 'mock_job_boundary')
                    self.api.jobs.add(row['id'])
                return matches
            require(not any(row['status'] in ('FAILED', 'CANCELLED', 'PRINTING') for row in matches), 'mock_job_failed_or_wrong_transport')
            time.sleep(.5)
        raise NoGo('mock_job_not_confirmed_no_mutation_retry')

    def verify_order(self, order_id, lines):
        detail = self.api.call(f'/orders/{order_id}')
        expected = sum(map(line_amount, lines), Decimal(0))
        require(money(detail['subtotal_amount']) == expected and money(detail['total_amount']) == expected + tax(expected), 'persisted_order_amount_mismatch')
        require(len(detail['items']) == len(lines), 'persisted_line_count_mismatch')
        for actual, submitted in zip(detail['items'], lines):
            require(actual['menu_item_id'] == submitted['menu_item_id'] and actual['quantity'] == submitted['quantity']
                    and money(actual['unit_price']) == money(submitted['unit_price_snapshot'])
                    and money(actual['line_amount']) == line_amount(submitted), 'persisted_line_amount_mismatch')
        receipt = self.jobs(order_id, 'FRONTDESK_RECEIPT')[0]
        text = re.sub(r'\[\[/?[^]]+]]', '', receipt['rendered_text_snapshot'])
        displayed = sum((money(row[1:]) for row in text.splitlines() if re.fullmatch(r'\$-?\d+\.\d{2}', row)), Decimal(0))
        require(displayed == expected and f'Subtotal: ${expected:.2f}' in text
                and f'Tax (14.975%): ${tax(expected):.2f}' in text and f'Total: ${expected + tax(expected):.2f}' in text, 'receipt_amount_mismatch')
        self.record('order_database_receipt_amounts', order_id=order_id, job_id=receipt['id'], subtotal=str(expected), tax=str(tax(expected)), total=str(expected + tax(expected)), receipt=text)
        return detail, receipt

    def submit(self, label, lines):
        self.assert_mock()
        identity = self.prefix + '_' + label
        payload = {'client_order_id': identity, 'idempotency_key': identity, 'local_draft_id': identity,
            'store_id': TARGET, 'organization_id': self.organization_id, 'server_order_id': None,
            'order_type': 'pickup', 'pickup_no': identity, 'table_no': None, 'menu_revision': self.catalog['menu_revision'],
            'expected_subtotal_amount': str(sum(map(line_amount, lines), Decimal(0))), 'items': lines}
        result = self.api.call(f'/stores/{TARGET}/orders/idempotent-submit', 'POST', payload)
        order_id = result['order_id']
        self.api.orders.add(order_id)
        self.report['order_ids'].append(order_id)
        self.checkpoint()
        return self.verify_order(order_id, lines)

    def orders_and_printing(self):
        beef = self.item('traditional_beef_noodle')
        large = self.line(beef, size='size_large')
        small = self.line(beef, size='size_small')
        combo = self.line(beef, size='size_large', combo=True)
        paid = self.line(beef, size='size_large', combo=True, egg=True)
        hot_line, hot_source = self.hot_line(beef)
        self.report['current_price_snapshots'] = {'large': large, 'small': small, 'combo': combo, 'paid_combo': paid}
        self.report['hot_source'] = hot_source
        original, original_job = self.submit('large2', [large])
        self.submit('small2', [small])
        self.submit('combo2', [combo])
        self.submit('comboegg2', [paid])
        grab = self.jobs(original['id'], 'GRAB')[0]
        require('（走s）' in grab['rendered_text_snapshot'], 'actual_grab_no_spicy_missing')
        self.record('actual_new_grab_no_spicy', order_id=original['id'], job_id=grab['id'], text=grab['rendered_text_snapshot'])
        vegetable = self.item('vegetable_noodle')
        for code, label in [('soup_vegan', '素汤'), ('soup_beef', '肉汤')]:
            _, receipt = self.submit(code, [self.line(vegetable, size='size_large', soup=code)])
            require('汤底: ' + label in receipt['rendered_text_snapshot'] or '汤底：' + label in receipt['rendered_text_snapshot'], 'vegetable_soup_receipt_missing_' + code)
        hot_order, _ = self.submit('hot_no_spicy', [hot_line])
        hot = self.jobs(hot_order['id'], 'HOT_KITCHEN')[0]
        require('（走s）' in hot['rendered_text_snapshot'], 'actual_hot_no_spicy_missing')
        self.record('actual_new_hot_no_spicy', order_id=hot_order['id'], job_id=hot['id'], source=hot_source, text=hot['rendered_text_snapshot'])
        before = h.frozen_order(original)
        self.assert_mock()
        update = self.api.call(f"/orders/{original['id']}/updates", 'POST', {'idempotency_key': self.prefix + '_update', 'items': [combo]})
        after = self.api.call(f"/orders/{original['id']}")
        old_ids = {row['id'] for row in original['items']}
        old_projection = dict(after, items=[row for row in after['items'] if row['id'] in old_ids])
        require(h.frozen_order(old_projection)['items'] == before['items'], 'update_changed_original_frozen_lines')
        require(money(after['subtotal_amount']) == line_amount(large) + line_amount(combo), 'updated_subtotal_mismatch')
        update_job = self.jobs(original['id'], 'FRONTDESK_RECEIPT', update['update_batch_id'])[0]
        require(f'Subtotal: ${line_amount(combo):.2f}' in update_job['rendered_text_snapshot'], 'update_batch_receipt_amount_mismatch')
        self.assert_mock()
        full = self.api.call(f"/orders/{original['id']}/reprint", 'POST', {'receipt_type': 'FRONTDESK_RECEIPT', 'idempotency_key': self.prefix + '_full'})
        require(full['status'] == 'PRINTED' and f"Subtotal: ${money(after['subtotal_amount']):.2f}" in full['rendered_text_snapshot'], 'full_reprint_amount_mismatch')
        self.api.jobs.add(full['id'])
        frozen = self.api.call(f"/admin/printing/jobs/{original_job['id']}/reprint", 'POST', {'idempotency_key': self.prefix + '_frozen'})
        require(frozen['status'] == 'PRINTED' and frozen['rendered_text_snapshot'] == original_job['rendered_text_snapshot'], 'job_reprint_did_not_preserve_original_text')
        unchanged = h.one(self.api.call(f"/orders/{original['id']}/print-jobs"), lambda row: row['id'] == original_job['id'], 'original_job_missing')
        require(unchanged['rendered_text_snapshot'] == original_job['rendered_text_snapshot'], 'original_job_snapshot_changed')
        self.record('update_full_and_frozen_reprint', order_id=original['id'], update_batch_id=update['update_batch_id'], update_job_id=update_job['id'], full_job_id=full['id'], frozen_job_id=frozen['id'], full_receipt=full['rendered_text_snapshot'], frozen_receipt=frozen['rendered_text_snapshot'])

    def removes(self):
        before = self.api.call(f'/admin/menu/removes?store_id={TARGET}')
        dry = self.api.call('/admin/menu/removes/reconcile', 'POST', {'store_id': TARGET, 'dry_run': True})
        require(self.api.call(f'/admin/menu/removes?store_id={TARGET}') == before, 'remove_dry_run_mutated')
        self.api.call('/admin/menu/removes/reconcile', 'POST', {'store_id': TARGET, 'dry_run': False})
        reconciled = self.api.call(f'/admin/menu/removes?store_id={TARGET}')
        require(dry['conflicts'] == reconciled['conflicts'], 'remove_reconcile_guessed_conflicts')
        self.record('remove_bounded_reconciliation', linked_groups=dry['linked_groups'], conflicts=dry['conflicts'])
        self.catalog = self.menu()
        source = self.item('traditional_beef_noodle')
        created = []
        for suffix in ('a', 'b'):
            row = self.api.call('/admin/platform/menu/items', 'POST', {'store_id': TARGET, 'category_id': source['category_id'], 'station_id': source['station_id'],
                'sku': self.prefix + '_' + suffix, 'name_zh': self.prefix + ' Test ' + suffix, 'name_en': self.prefix + ' Test ' + suffix,
                'item_type': 'main', 'base_price': 0, 'cost_per_item': 0, 'is_active': True, 'is_sold_out': False})
            self.api.items.add(row['id']); created.append(row['id'])
            self.report['item_ids'].append(row['id']); self.checkpoint()
        code = self.prefix.lower() + '_remove'
        shared = self.api.call('/admin/menu/removes', 'POST', {'store_id': TARGET, 'code': code, 'name_zh': '测试减料', 'name_en': 'Test Remove', 'active': True, 'sort_order': 9990})
        rid = shared['id']; self.api.removes.add(rid); self.report['remove_id'] = rid; self.checkpoint()
        self.api.call(f'/admin/menu/removes/{rid}/applicability?store_id=18', 'PUT', {'store_id': TARGET, 'category_ids': [], 'items': [{'item_id': item, 'mode': 'ALLOW'} for item in created]})
        self.api.call(f'/admin/menu/removes/{rid}?store_id=18', 'PUT', {'store_id': TARGET, 'name_zh': '共享测试减料', 'name_en': 'Shared Test Remove', 'active': True, 'sort_order': 9990})
        current = self.menu()
        for item_id in created:
            item = h.one(h.flatten_menu(current), lambda row: row['id'] == item_id, 'test_item_not_in_catalog')
            option = h.one(item['options'], lambda row: row.get('option_code') == code and row.get('option_group') == 'REMOVE', 'shared_remove_not_materialized')
            require(option['name_zh'] == '共享测试减料' and money(option['price_delta']) == 0 and option['is_active'], 'shared_free_remove_not_synchronized')
        control = next(row['id'] for row in self.workspaces['stores'] if row['id'] != TARGET)
        rejection = self.api.call(f'/admin/menu/removes/{rid}/applicability?store_id={control}', expected=(400,))
        require(rejection.get('message') == 'REMOVE_STORE_MISMATCH' or rejection.get('error_code') == 'REMOVE_STORE_MISMATCH', 'negative_store_scope_contract_mismatch')
        require(self.control_snapshots() == self.other_before, 'other_store_private_configuration_changed')
        self.record('shared_remove_two_items_and_store_isolation', remove_id=rid, item_ids=created, code=code, other_store_id=control)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sha', required=True)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--report', required=True)
    args = parser.parse_args(argv)
    require(re.fullmatch('[0-9a-f]{40}', args.sha), 'exact_sha_required')
    require(re.fullmatch('[a-z0-9]{8,24}', args.run_id), 'bounded_run_id_required')
    return args


def main(argv=None):
    os.umask(0o077)
    args = parse_args(argv)
    report = {'schema': 'POS_FIVE_FIXES_ACCEPTANCE_V1', 'environment': 'staging', 'source_sha': args.sha,
              'run_id': args.run_id, 'result': 'FAIL', 'order_ids': [], 'item_ids': [], 'checks': [], 'hardware_printing': False}
    path = Path(args.report)
    require(path.is_absolute() and path.parent == ROOT / 'evidence' and path.parent.resolve() == path.parent, 'new_private_staging_evidence_path_required')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    api, lock = StagingApi(args.run_id), None
    def checkpoint():
        payload = json.dumps(report, ensure_ascii=False, indent=2).encode() + b'\n'
        os.lseek(fd, 0, os.SEEK_SET); os.ftruncate(fd, 0)
        while payload:
            payload = payload[os.write(fd, payload):]
        os.fsync(fd)
    checkpoint()
    try:
        lock_path = ROOT / 'state/restaurant-pos-staging-hygiene.lock'
        h.private_bytes(lock_path)
        lock = os.open(lock_path, os.O_RDWR | os.O_NOFOLLOW)
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        report['runtime'] = runtime(args.sha)
        login(api)
        acceptance = Acceptance(api, args.run_id, report, checkpoint)
        acceptance.prepare()
        acceptance.orders_and_printing()
        acceptance.removes()
        require(runtime(args.sha) == report['runtime'], 'runtime_changed_during_acceptance')
        report['result'] = 'PASS'
    except NoGo as error:
        report['failure'] = str(error)
    except Exception:
        report['failure'] = 'unexpected_contract_or_local_io_error_no_sensitive_details'
    finally:
        report['last_request'] = api.last_request
        try:
            api.logout()
        except Exception:
            report['result'] = 'FAIL'; report['logout'] = 'FAIL'
        report['finished_at'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
        checkpoint(); os.close(fd)
        if lock is not None:
            os.close(lock)
    print('STAGING_FIVE_FIXES_ACCEPTANCE=' + report['result'])
    return 0 if report['result'] == 'PASS' else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        print('STAGING_FIVE_FIXES_ACCEPTANCE=FAIL_LOCAL_GUARD', file=sys.stderr)
        sys.exit(1)
