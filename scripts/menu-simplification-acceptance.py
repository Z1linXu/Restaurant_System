#!/usr/bin/env python3
"""Bounded Menu Simplification API acceptance; run only in the exact Staging release.

No credentials in argv, environment, reports or exceptions. Reuses the reviewed
runtime validator and acceptance mutex. --create-fixtures creates exactly two
run-coded synthetic Stores through normal Owner provisioning and same-key replay.
Otherwise existing fixture IDs AND synthetic codes must be explicitly approved.
The script creates run-named menu fixtures, submits one synthetic order and
updates their menu/endpoint-free MOCK printing configuration. No SQL, hardware
binding, deployment, data cleanup or Production target exists.

Private JSON input: existing twin001 owner_login_identifier/owner_login_password,
or existing OPS001 login_identifier/login_password. Optional wrong_store_owner
and wrong_org_owner objects have login_identifier/login_password; their genuine
principals must have OWNER and an accessible control Store (control_store_id).
--create-isolation-fixtures optionally creates one new synthetic OWNER through
normal Staff Admin, saves its new password in a new mode-0600 Staging state file,
and checks real login/Store denials. It never resets an existing credential.
Wrong-Organization checks still require an existing suitable private principal;
there is no supported public API granting a new Organization membership.
No token minting or forged claims. Missing negative fixtures are NOT_RUN.
"""
from __future__ import annotations

import argparse
import copy
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path('/srv/restaurant-pos/staging')
API_BASE = 'http://127.0.0.1:18080/api/v1'
SAFE_ENV = {'PATH': '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin'}


class NoGo(Exception):
    """Messages are fixed local labels, never remote bodies or credentials."""


def require(condition, label):
    if not condition:
        raise NoGo(label)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'),
                                     ensure_ascii=False).encode()).hexdigest()


def private_bytes(path):
    path = Path(path)
    require(path.is_absolute() and path.resolve() == path, 'private_path_not_canonical')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        meta = os.fstat(fd)
        require(stat.S_ISREG(meta.st_mode) and meta.st_uid == os.getuid()
                and stat.S_IMODE(meta.st_mode) == 0o600, 'private_file_permissions')
        with os.fdopen(os.dup(fd), 'rb') as handle:
            value = handle.read(1024 * 1024 + 1)
        require(len(value) <= 1024 * 1024, 'private_file_too_large')
        return value
    finally:
        os.close(fd)


def secret_input(args):
    if args.secrets_file:
        data = private_bytes(args.secrets_file)
    else:
        require(args.secrets_fd is not None and args.secrets_fd >= 3, 'private_fd_required')
        meta = os.fstat(args.secrets_fd)
        require(not os.isatty(args.secrets_fd), 'secret_fd_must_be_noninteractive')
        require((stat.S_ISREG(meta.st_mode) and meta.st_uid == os.getuid()
                 and stat.S_IMODE(meta.st_mode) == 0o600) or stat.S_ISFIFO(meta.st_mode),
                'secret_fd_permissions')
        with os.fdopen(os.dup(args.secrets_fd), 'rb') as handle:
            data = handle.read(1024 * 1024 + 1)
        require(len(data) <= 1024 * 1024, 'secret_input_too_large')
    result = json.loads(data)
    require(isinstance(result, dict), 'secret_json_object_required')
    return result


def command(argv):
    try:
        result = subprocess.run(argv, env=SAFE_ENV, capture_output=True, timeout=120)
    except (OSError, subprocess.TimeoutExpired):
        raise NoGo('runtime_validator_unavailable_or_timeout') from None
    require(result.returncode == 0, 'runtime_validation_failed')
    return result.stdout


def validate_runtime(args):
    require(re.fullmatch('[0-9a-f]{40}', args.approved_sha), 'exact_sha_required')
    require(re.fullmatch('[0-9a-f]{64}', args.preflight_evidence_sha256), 'preflight_digest_required')
    release = ROOT / 'releases' / args.approved_sha
    require(Path(__file__).resolve() == release / 'scripts/menu-simplification-acceptance.py',
            'must_run_reviewed_script_from_exact_staging_release')
    env_path = ROOT / 'config/.env.staging'
    env_digest = hashlib.sha256(private_bytes(env_path)).hexdigest()
    command([str(release / 'deployment/cloud/staging-runtime-evidence.sh'), '--validate',
             '--approved-sha', args.approved_sha, '--env-file', str(env_path),
             '--preflight-evidence', args.preflight_evidence,
             '--preflight-evidence-sha256', args.preflight_evidence_sha256])
    # Existing helper proves env/release/images/network/loopback. Inspect actual
    # backend policy too: a safe dotenv alone cannot prove a running process.
    rows = json.loads(command(['docker', '--context', 'default', 'inspect',
                              'restaurant-pos-staging-backend-1']))
    require(len(rows) == 1, 'backend_identity_ambiguous')
    row = rows[0]
    policy = dict(entry.split('=', 1) for entry in row['Config']['Env'])
    require(row['Config']['Labels']['com.docker.compose.project'] == 'restaurant-pos-staging'
            and row['State']['Running'] is True, 'backend_project_or_state')
    require(policy.get('APP_PHASE_B_RUNTIME') == 'staging'
            and policy.get('APP_PRINTING_ALLOWED_MODES') == 'DISABLED,MOCK'
            and policy.get('APP_PRINTING_ENDPOINT_CONFIGURATION_ENABLED') == 'false',
            'actual_runtime_printing_policy_unsafe')
    require(policy.get('APP_FEATURES_PRINTING') in ('true', 'false'), 'printing_feature_unknown')
    require(hashlib.sha256(private_bytes(env_path)).hexdigest() == env_digest, 'environment_drift')
    return {'environment_sha256': env_digest, 'backend_image_id': row['Image'],
            'backend_container_id': row['Id'], 'printing_enabled': policy['APP_FEATURES_PRINTING'] == 'true'}


def action_lock():
    path = ROOT / 'state/al003s-acceptance.lock'
    require(path.resolve() == path, 'acceptance_lock_path')
    fd = os.open(path, os.O_RDWR | os.O_NOFOLLOW)  # Existing reviewed mutex only.
    meta = os.fstat(fd)
    try:
        require(stat.S_ISREG(meta.st_mode) and meta.st_uid == os.getuid()
                and stat.S_IMODE(meta.st_mode) == 0o600, 'acceptance_lock_permissions')
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        lock_content = os.read(fd, 8193)
        require(len(lock_content) <= 8192, 'acceptance_lock_content_too_large')
        require(b'AL003S_BLOCKED|' not in lock_content
                and not (ROOT / 'state/al003s-acceptance.blocked').exists(), 'acceptance_blocked')
    except Exception:
        os.close(fd)
        raise
    return fd


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Api:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        self.token = None
        self.refresh = None
        self.last_request = None

    def call(self, path, method='GET', body=None, expected=(200,), key=None, error_code=None):
        require(re.fullmatch(r'/[A-Za-z0-9_/?=&.\-]+', path) and '..' not in path and '//' not in path,
                'unsafe_api_path')
        require(method in ('GET', 'POST', 'PUT'), 'unsafe_http_method')
        self.last_request = {'method': method, 'path': path}
        headers = {'Accept': 'application/json', 'Content-Type': 'application/json'}
        if self.token:
            headers['Authorization'] = 'Bearer ' + self.token
        if key:
            headers['Idempotency-Key'] = key
        request = urllib.request.Request(API_BASE + path, method=method, headers=headers,
                                        data=None if body is None else json.dumps(body).encode())
        try:
            response = self.opener.open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        except (OSError, urllib.error.URLError):
            raise NoGo('api_transport_failed_no_automatic_mutation_retry') from None
        with response:
            status = response.code
            raw = response.read(4 * 1024 * 1024 + 1)
        require(status in expected, f'http_status_{status}_expected_' + '_'.join(map(str, expected)))
        require(len(raw) <= 4 * 1024 * 1024, 'api_response_too_large')
        try:
            parsed = json.loads(raw)
        except (ValueError, UnicodeError):
            raise NoGo('invalid_json_response') from None
        require(isinstance(parsed, dict), 'api_envelope_required')
        require(parsed.get('success') is (status < 400), 'unexpected_api_envelope')
        if error_code is not None:
            require(status >= 400 and (parsed.get('error_code') == error_code
                    or parsed.get('message') == error_code), 'negative_error_contract_mismatch')
        return parsed.get('data') if status < 400 else parsed

    def login(self, credentials, organization_id=None):
        login = credentials.get('login_identifier', credentials.get('owner_login_identifier'))
        password = credentials.get('login_password', credentials.get('owner_login_password'))
        require(isinstance(login, str) and login and isinstance(password, str) and len(password) >= 12,
                'private_owner_credentials_missing')
        response = self.call('/auth/login', 'POST', {'login_identifier': login, 'password': password})
        self.token, self.refresh = response.get('access_token'), response.get('refresh_token')
        require(isinstance(self.token, str) and len(self.token) > 20, 'owner_access_token_missing')
        user = response.get('user') or {}
        require(user.get('role_code') == 'OWNER' and user.get('username') == login, 'owner_identity_mismatch')
        if organization_id is not None:
            require(user.get('organization_id') == organization_id, 'owner_organization_mismatch')
        return user

    def logout(self):
        if self.refresh:
            self.call('/auth/logout', 'POST', {'refresh_token': self.refresh})
        self.token = self.refresh = None


def flatten_menu(catalog):
    return [item for category in catalog['categories'] for item in category['items']]


def one(rows, predicate, label):
    matches = [row for row in rows if predicate(row)]
    require(len(matches) == 1, label)
    return matches[0]


def option_snapshot(row, option_id=None):
    return {'option_id': row['id'] if option_id is None else option_id, 'quantity': 1,
            'option_type_snapshot': row.get('option_type', 'addon'),
            'option_group_snapshot': row.get('option_group'),
            'option_code_snapshot': row.get('option_code'),
            'option_name_snapshot_zh': row.get('name_zh'),
            'option_name_snapshot_en': row.get('name_en'),
            'option_price_snapshot': row.get('price_delta', 0),
            'parent_option_id_snapshot': row.get('parent_option_id')}


def frozen_order(order):
    """Only immutable money/name/semantic snapshots, not live kitchen timestamps."""
    keys = ('id', 'menu_item_id', 'category_code_snapshot', 'station_id_snapshot',
            'item_sku_snapshot', 'item_name_snapshot_zh', 'item_name_snapshot_en',
            'quantity', 'unit_price', 'line_amount', 'combo_group_no', 'combo_role', 'options')
    return {'items': [{key: item.get(key) for key in keys} for item in order['items']],
            **{key: order[key] for key in ('subtotal_amount', 'discount_amount', 'total_amount')}}


def verify_mock_job(job, printing, store_id, order_id):
    # Existing dispatcher treats null execution_mode as Store-mode execution;
    # the field is not a mandatory MOCK marker. Never infer safety from null alone.
    require(printing['printing_mode'] == 'MOCK'
            and all(not x.get('ip_address') for x in printing['printers'])
            and job['store_id'] == store_id and job['order_id'] == order_id
            and job['module_code'] == 'GRAB' and job['status'] == 'PRINTED'
            and job.get('execution_mode') in (None, 'MOCK')
            and job.get('printer_id') is None and not job.get('printer_endpoint')
            and job.get('printed_by_device_id') is None,
            'mock_job_execution_boundary_mismatch')
    require('+TESTADD' in job['rendered_text_snapshot']
            and '+TESTCOMBO' in job['rendered_text_snapshot'], 'mock_job_missing_distinct_tokens')


class Acceptance:
    def __init__(self, args, api, report):
        self.args, self.api, self.report = args, api, report
        self.a, self.b = args.store_a_id, args.store_b_id
        self.prefix = 'stg_menu_' + args.run_id
        self.code = self.prefix + '_egg'

    def record(self, check, status='PASS', **facts):
        self.report['checks'].append({'check': check, 'status': status, **facts})

    def catalog(self, store):
        result = self.api.call(f'/admin/menu/addons?store_id={store}')
        require(isinstance(result.get('addons'), list) and isinstance(result.get('conflicts'), list),
                'addon_catalog_shape')
        return result

    def menu(self, store):
        return self.api.call(f'/menu/catalog?store_id={store}')

    def linked(self, item):
        return self.api.call(f'/admin/menu/items/{item}/addons')

    def enable(self, item, addon, enabled=True):
        return self.api.call(f'/admin/menu/items/{item}/addons/{addon}', 'PUT', {'enabled': enabled})

    def fixture(self, store, code):
        context = self.api.call(f'/admin/menu/management-context?store_id={store}')
        fixture = one(context['stores'], lambda x: x['id'] == store, 'fixture_store_missing')
        require(fixture['code'] == code and fixture['organization_id'] == self.args.organization_id,
                'approved_synthetic_store_identity_mismatch')
        printing = self.api.call(f'/admin/printing?store_id={store}')
        require(printing['printing_mode'] in ('MOCK', 'DISABLED')
                and all(not x.get('ip_address') for x in printing['printers']), 'store_printing_unsafe')
        require(str(fixture['status']).lower() == 'active', 'synthetic_store_not_active')
        return context

    def materialization(self):
        for store in (self.a, self.b):
            before = self.catalog(store)
            require(before['addons'] or before['conflicts'], 'new_store_materialization_empty')
            self.record('existing_catalog_materialization_report', store_id=store, addon_count=len(before['addons']),
                        unresolved_conflict_count=len(before['conflicts']), catalog_sha256=digest(before))
            dry = self.api.call('/admin/menu/addons/reconcile', 'POST', {'store_id': store, 'dry_run': True})
            dry_again = self.api.call('/admin/menu/addons/reconcile', 'POST', {'store_id': store, 'dry_run': True})
            require(dry == dry_again and before == self.catalog(store), 'reconcile_dry_run_not_deterministic_or_mutated')
            self.api.call('/admin/menu/addons/reconcile', 'POST', {'store_id': store, 'dry_run': False})
            after = self.catalog(store)
            self.api.call('/admin/menu/addons/reconcile', 'POST', {'store_id': store, 'dry_run': False})
            require(after == self.catalog(store), 'reconcile_replay_changed_catalog')
            require(before['conflicts'] == after['conflicts'], 'reconciliation_guessed_conflict_values')
            self.record('reconciliation_deterministic_replay', store_id=store,
                        unresolved_conflicts_sha256=digest(after['conflicts']))
            self.record('unresolved_conflicts_preserved', 'PASS' if after['conflicts'] else 'NOT_RUN',
                        store_id=store, count=len(after['conflicts']))

    def create_item(self, context, suffix):
        store = context['stores'][0]['id']
        categories = [x for x in context['menu_categories'] if x.get('is_active') is not False]
        stations = [x for x in context['stations'] if x.get('is_active') is not False]
        require(categories and stations, 'synthetic_store_menu_structure_missing')
        # Pick existing topology by stable numeric identity, never by translated name.
        category, station = min(categories, key=lambda x: x['id']), min(stations, key=lambda x: x['id'])
        return self.api.call('/admin/platform/menu/items', 'POST', {
            'store_id': store, 'category_id': category['id'], 'station_id': station['id'],
            'sku': self.prefix + '_' + suffix, 'name_zh': 'Synthetic ' + suffix,
            'name_en': 'Synthetic ' + suffix, 'item_type': 'main', 'base_price': 10,
            'cost_per_item': 0, 'is_active': True, 'is_sold_out': False})

    def create_addon(self, store):
        return self.api.call('/admin/menu/addons', 'POST', {'store_id': store, 'code': self.code,
            'name_zh': 'Synthetic egg', 'name_en': 'Synthetic egg', 'price': 1.25, 'active': True})

    def addon_row(self, store):
        return one(self.catalog(store)['addons'], lambda x: x['code'] == self.code, 'addon_missing_or_duplicated')

    def combo_defaults(self, items):
        config = self.api.call(f'/admin/menu/combo-configuration?store_id={self.a}')
        egg = one(config['groups'], lambda x: x['group_code'] == 'COMBO_EGG', 'combo_egg_group_missing')
        for code in ('combo_tea_egg', 'combo_fried_egg'):
            one(egg['components'], lambda x: x['component_code'] == code and x['enabled'], 'required_egg_missing')
        egg['default_component_code'] = 'combo_tea_egg'
        self.api.call('/admin/menu/combo-configuration', 'PUT', {'store_id': self.a, 'groups': config['groups']})
        for item in items[:2]:
            self.api.call(f"/admin/menu/items/{item['id']}/combo-policy", 'PUT', {'combo_allowed': True})
        target = items[1]['id']
        self.api.call(f'/admin/menu/items/{target}/combo-egg-default', 'PUT',
                      {'default_combo_egg_component_code': 'combo_fried_egg'})
        menu = self.menu(self.a)
        tea = one(flatten_menu(menu), lambda x: x['id'] == items[0]['id'], 'tea_item_missing')
        fried = one(flatten_menu(menu), lambda x: x['id'] == target, 'fried_item_missing')
        require(tea.get('default_combo_egg_component_code') is None
                and fried.get('default_combo_egg_component_code') == 'combo_fried_egg', 'combo_default_catalog_wrong')
        new_config = self.api.call(f'/admin/menu/combo-configuration?store_id={self.a}')
        require(any(x['item_id'] == target and x['default_combo_egg_component_code'] == 'combo_fried_egg'
                    for x in new_config['item_overrides']), 'combo_override_summary_missing')
        require(one(new_config['groups'], lambda x: x['group_code'] == 'COMBO_EGG', 'egg_group')['default_component_code']
                == 'combo_tea_egg', 'store_tea_default_not_saved')
        self.record('combo_tea_default_and_fried_override_api')
        self.record('browser_default_selection_and_manual_precedence', 'NOT_RUN', reason='frontend_browser_worker')
        return menu

    def printing(self, item, enabled):
        context = self.api.call(f'/admin/printing/display-rules?store_id={self.a}')
        require(context.get('draft_revision') is None, 'existing_printing_draft_protected')
        content = copy.deepcopy(context['active_revision']['content'])
        require(self.addon_row(self.a)['printing_configured'] is False, 'missing_printing_rule_not_visible')
        self.record('missing_printing_coverage_fail_visible')
        tokens = [[self.code, '+TESTADD'], ['combo_fried_egg', '+TESTCOMBO']]
        content['dictionaries']['MODIFIER_ADD'] = [x for x in content['dictionaries']['MODIFIER_ADD']
                                                   if x[0] not in (self.code, 'combo_fried_egg')] + tokens
        preview = self.api.call('/admin/printing/display-rules/preview', 'POST', {
            'store_id': self.a, 'content': content, 'item_sku': item['sku'],
            'item_name_zh': item['name_zh'], 'item_name_en': item['name_en'],
            'modifier_add_codes': [self.code, 'combo_fried_egg'], 'modifier_remove_codes': [], 'combo': True})
        require(all(token in preview['grab_preview'] for _, token in tokens), 'printing_preview_code_tokens_missing')
        self.record('printing_preview_distinct_addon_combo_codes')
        draft = self.api.call('/admin/printing/display-rules/draft', 'POST',
                              {'store_id': self.a, 'content': content, 'summary': 'Synthetic menu acceptance'})
        self.api.call('/admin/printing/display-rules/publish', 'POST',
                      {'store_id': self.a, 'revision_id': draft['id']})
        require(self.addon_row(self.a)['printing_configured'] is True, 'configured_printing_coverage_missing')
        if enabled:
            self.api.call('/admin/printing/status', 'PUT', {'store_id': self.a, 'printing_mode': 'MOCK'})
            self.api.call('/admin/printing/assignments/GRAB', 'PUT', {'store_id': self.a, 'printer_id': None,
                'enabled': True, 'font_size': 'NORMAL', 'takeout_receipt_copies': 1})
        return enabled

    def submit(self, items, menu):
        by_id = {x['id']: x for x in flatten_menu(menu)}
        lines = []
        for item, selected_egg in ((items[0], 'combo_fried_egg'), (items[1], 'combo_tea_egg')):
            current = by_id[item['id']]
            addon = one(current['options'], lambda x: x.get('option_code') == self.code, 'order_addon_missing')
            combo = one(current['options'], lambda x: x.get('option_group') == 'COMBO', 'order_combo_missing')
            options = [option_snapshot(addon), option_snapshot(combo)]
            for group in menu['combo_configuration']['groups']:
                if not group['enabled']:
                    continue
                code = selected_egg if group['group_code'] == 'COMBO_EGG' else group['default_component_code']
                if not code and not group['required']:
                    continue
                component = one(group['components'], lambda x: x['component_code'] == code and x['enabled'],
                                'combo_explicit_component_missing')
                legacy_ids = {'combo_tea_egg': -20101, 'combo_fried_egg': -20102,
                              'combo_edamame': -20201, 'combo_shredded_potato': -20202, 'combo_cucumber_salad': -20203}
                require(code in legacy_ids, 'combo_component_id_not_supported_no_guess')
                options.append(option_snapshot({'option_group': group['group_code'], 'option_code': code,
                    'name_zh': component['name_zh'], 'name_en': component['name_en']}, legacy_ids[code]))
            lines.append({'menu_item_id': item['id'], 'quantity': 1, 'combo_role': 'standalone', 'options': options})
        created = self.api.call('/orders', 'POST', {'store_id': self.a, 'order_type': 'pickup',
            'pickup_no': 'MENU-' + self.args.run_id, 'items': lines})
        order_id = created['id']
        self.report['order_id'] = order_id  # Preserve repair identity before submit; never blind retry.
        submitted = self.api.call(f'/orders/{order_id}/submit', 'POST')
        require(submitted['status'] in ('submitted', 'preparing', 'ready'), 'order_submit_failed')
        historical = self.api.call(f'/orders/{order_id}')
        for item, expected_egg in ((items[0], 'combo_fried_egg'), (items[1], 'combo_tea_egg')):
            line = one(historical['items'], lambda x: x['menu_item_id'] == item['id'], 'submitted_item_missing')
            codes = [x['option_code_snapshot'] for x in line['options']]
            require(expected_egg in codes and self.code in codes, 'manual_selection_not_preserved')
        self.record('explicit_manual_egg_selection_submit_preserved')
        return historical

    def auth_negatives(self, credentials):
        for name in ('wrong_store_owner', 'wrong_org_owner'):
            fixture = credentials.get(name)
            if not fixture:
                self.record(name + '_rejected', 'NOT_RUN', reason='existing_negative_owner_fixture_not_supplied')
                continue
            api = Api()
            try:
                principal = api.login(fixture)
                control = fixture.get('control_store_id')
                require(isinstance(control, int) and control != self.a, 'negative_control_store_invalid')
                if 'expected_user_id' in fixture:
                    require(principal['id'] == fixture['expected_user_id'] and principal['store_id'] == control,
                            'created_isolation_login_identity_mismatch')
                api.call(f'/admin/menu/addons?store_id={control}')
                require((principal['organization_id'] == self.args.organization_id) == (name == 'wrong_store_owner'),
                        'negative_owner_organization_precondition')
                api.call(f'/admin/menu/addons?store_id={self.a}', expected=(403,))
                api.call(f'/admin/menu/items/{self.report["item_ids"][0]}/addons', expected=(403,))
                before = self.addon_row(self.a)
                api.call(f'/admin/menu/addons/{before["id"]}', 'PUT',
                         {key: before[key] for key in ('name_zh', 'name_en', 'price', 'active')}, expected=(403,))
                api.call(f'/admin/menu/items/{self.report["item_ids"][0]}/addons/{before["id"]}',
                         'PUT', {'enabled': True}, expected=(403,))
                require(self.addon_row(self.a) == before, 'denied_authority_mutated_catalog')
                self.record(name + '_rejected', user_id=principal['id'], control_store_id=control,
                            read_and_write_denied=True)
            finally:
                api.logout()

    def run(self, credentials, runtime):
        context_a = self.fixture(self.a, self.args.store_a_code)
        self.fixture(self.b, self.args.store_b_code)
        self.record('approved_synthetic_stores_and_endpoint_free_printing')
        require(not any(x['code'] == self.code for x in self.catalog(self.a)['addons'] + self.catalog(self.b)['addons']),
                'run_id_already_used_inspect_prior_evidence_before_retry')
        require(not any(str(x['sku']).startswith(self.prefix + '_') for x in flatten_menu(self.menu(self.a))),
                'run_item_skus_already_exist_no_blind_retry')
        if self.args.create_isolation_fixtures:
            create_isolation_fixture(self.api, self.args, credentials, self.report)
        self.materialization()
        items = []
        self.report['item_ids'] = []
        for suffix in ('tea', 'fried', 'unrelated'):
            item = self.create_item(context_a, suffix)
            items.append(item)
            self.report['item_ids'].append(item['id'])
        addon_a, addon_b = self.create_addon(self.a), self.create_addon(self.b)
        self.report['addon_ids'] = [addon_a['id'], addon_b['id']]
        for item in items[:2]:
            self.enable(item['id'], addon_a['id'])
        unrelated_before = self.api.call(f"/admin/menu/items/{items[2]['id']}/options")
        b_before = self.catalog(self.b)
        b_revision = self.api.call(f'/menu/catalog/revision?store_id={self.b}')
        self.api.call(f"/admin/menu/items/{items[0]['id']}/addons/{addon_b['id']}", 'PUT',
                      {'enabled': True}, expected=(400,), error_code='ADDON_STORE_MISMATCH')
        self.record('cross_store_addon_link_rejected')
        self.combo_defaults(items)
        print_jobs_enabled = self.printing(items[0], runtime['printing_enabled'])
        historical = self.submit(items, self.menu(self.a))
        revision_before_edit = self.menu(self.a)['menu_revision']
        updated = {'name_zh': 'Synthetic egg renamed', 'name_en': 'Synthetic egg renamed', 'price': 2.75, 'active': True}
        self.api.call(f"/admin/menu/addons/{addon_a['id']}", 'PUT', updated)
        changed = self.addon_row(self.a)
        require(all(changed[x] == v for x, v in updated.items()), 'catalog_edit_not_reflected')
        menu = self.menu(self.a)
        require(menu['menu_revision'] > revision_before_edit, 'catalog_revision_not_incremented')
        for item in items[:2]:
            current = one(flatten_menu(menu), lambda x: x['id'] == item['id'], 'linked_item_missing')
            option = one(current['options'], lambda x: x.get('option_code') == self.code, 'linked_option_missing')
            require(option['name_zh'] == updated['name_zh'] and option['name_en'] == updated['name_en']
                    and option['price_delta'] == updated['price'], 'linked_option_not_materialized')
        require(self.api.call(f"/admin/menu/items/{items[2]['id']}/options") == unrelated_before, 'unrelated_item_eligibility_changed')
        require(self.catalog(self.b) == b_before
                and self.api.call(f'/menu/catalog/revision?store_id={self.b}') == b_revision,
                'store_b_catalog_or_revision_changed')
        self.record('rename_reprice_all_linked_options_and_store_isolation')
        order_after = self.api.call(f"/orders/{historical['id']}")
        require(frozen_order(historical) == frozen_order(order_after),
                'historical_order_snapshot_changed')
        self.record('historical_order_snapshots_unchanged', snapshot_sha256=digest(frozen_order(historical)))
        self.api.call(f"/admin/menu/addons/{addon_a['id']}", 'PUT', {**updated, 'code': self.code + '_changed'},
                      expected=(400,), error_code='ADDON_CODE_IMMUTABLE')
        require(self.addon_row(self.a)['code'] == self.code, 'immutable_code_changed')
        self.record('immutable_semantic_code_rejected')
        self.enable(items[1]['id'], addon_a['id'], False)
        self.api.call(f"/admin/menu/addons/{addon_a['id']}", 'PUT', {**updated, 'active': False})
        for item, expected in ((items[0], True), (items[1], False)):
            linked = one(self.linked(item['id']), lambda x: x['id'] == addon_a['id'], 'eligibility_row_missing')
            require(linked['enabled'] is expected, 'inactive_addon_lost_item_eligibility')
        inactive_menu = self.menu(self.a)
        for item in items[:2]:
            current = one(flatten_menu(inactive_menu), lambda x: x['id'] == item['id'], 'inactive_item_missing')
            require(not any(x.get('option_code') == self.code and x.get('is_active') is not False
                            for x in current['options']), 'inactive_addon_still_orderable')
        self.api.call(f"/admin/menu/addons/{addon_a['id']}", 'PUT', updated)
        current = {x['id']: x for x in flatten_menu(self.menu(self.a))}
        require(any(x.get('option_code') == self.code for x in current[items[0]['id']]['options'])
                and not any(x.get('option_code') == self.code for x in current[items[1]['id']]['options']),
                'eligibility_not_restored_independently')
        self.fixture(self.a, self.args.store_a_code)
        self.record('addon_active_independent_of_item_eligibility_and_store_active')
        self.api.call(f"/admin/menu/items/{items[1]['id']}/combo-egg-default", 'PUT',
                      {'default_combo_egg_component_code': None})
        config = self.api.call(f'/admin/menu/combo-configuration?store_id={self.a}')
        require(not any(x['item_id'] == items[1]['id'] for x in config['item_overrides']), 'null_override_not_removed')
        cleared_item = one(flatten_menu(self.menu(self.a)), lambda x: x['id'] == items[1]['id'], 'cleared_item_missing')
        require(cleared_item.get('default_combo_egg_component_code') is None, 'catalog_null_override_not_removed')
        self.record('combo_null_removal')
        if print_jobs_enabled:
            jobs = []
            for _ in range(20):
                jobs = self.api.call(f"/orders/{historical['id']}/print-jobs")
                if any(x['status'] == 'PRINTED' and x['module_code'] == 'GRAB' for x in jobs):
                    break
                time.sleep(1)
            job = one(jobs, lambda x: x['module_code'] == 'GRAB' and x['status'] == 'PRINTED', 'mock_grab_job_missing')
            verify_mock_job(job, self.api.call(f'/admin/printing?store_id={self.a}'), self.a, historical['id'])
            self.record('mock_print_job_distinct_code_tokens', job_id=job['id'],
                        rendered_sha256=digest(job['rendered_text_snapshot']))
        else:
            self.record('mock_print_job_distinct_code_tokens', 'NOT_RUN', reason='runtime_printing_disabled')
        self.auth_negatives(credentials)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true', help='Explicit synthetic Store mutation gate')
    parser.add_argument('--create-fixtures', action='store_true', help='Create fixed STG005_MENU_<RUN>_A/B via normal Owner API')
    parser.add_argument('--create-isolation-fixtures', action='store_true',
                        help='Create one private synthetic OWNER on B using normal Staff API')
    parser.add_argument('--approved-sha', required=True)
    parser.add_argument('--preflight-evidence', required=True)
    parser.add_argument('--preflight-evidence-sha256', required=True)
    parser.add_argument('--organization-id', type=int, required=True)
    for side in ('a', 'b'):
        parser.add_argument(f'--store-{side}-id', type=int)
        parser.add_argument(f'--store-{side}-code')
    parser.add_argument('--run-id', required=True, help='New lowercase alphanumeric run marker, 4-16 chars')
    secrets = parser.add_mutually_exclusive_group(required=True)
    secrets.add_argument('--secrets-file')
    secrets.add_argument('--secrets-fd', type=int)
    parser.add_argument('--creation-evidence', help='Existing reviewed normal Owner business-create evidence')
    parser.add_argument('--report', required=True, help='New private sanitized JSON report; never overwrite')
    args = parser.parse_args(argv)
    require(args.execute, 'explicit_execute_required_no_api_requests_sent')
    require(args.organization_id > 0, 'approved_organization_required')
    require(re.fullmatch('[a-z0-9]{4,16}', args.run_id), 'invalid_run_id')
    if args.create_fixtures:
        require(all(value is None for value in (args.store_a_id, args.store_b_id,
                args.store_a_code, args.store_b_code, args.creation_evidence)),
                'create_fixtures_rejects_existing_store_inputs')
    else:
        require(args.store_a_id is not None and args.store_b_id is not None
                and args.store_a_id > 0 and args.store_b_id > 0 and args.store_a_id != args.store_b_id,
                'two_distinct_approved_stores_required')
        for code in (args.store_a_code, args.store_b_code):
            require(isinstance(code, str) and re.fullmatch(r'(STG005_|PHASE_B_VALIDATION_)[A-Z0-9_]{4,90}', code),
                    'synthetic_store_code_required')
        require(args.store_a_code != args.store_b_code, 'distinct_synthetic_store_codes_required')
    return args


def template_snapshot(api, organization_id):
    """Read the supported catalog and real Profile version; never infer a Master read API."""
    catalog = api.call(f'/owner/organizations/{organization_id}/stores/create-catalog')
    require(catalog['enabled'] is True, 'normal_store_creation_not_enabled')
    for key in ('profile_code', 'profile_version', 'master_menu_key', 'master_menu_version'):
        require(isinstance(catalog[key], str) and re.fullmatch('[A-Za-z0-9_]+', catalog[key]),
                'template_identity_invalid')
    require(re.fullmatch('[0-9a-f]{64}', catalog['master_menu_fingerprint_sha256']),
            'master_catalog_fingerprint_invalid')
    profile = api.call(f"/store-profiles/{catalog['profile_code']}/versions/{catalog['profile_version']}")
    require(profile['valid'] is True and profile['profile_code'] == catalog['profile_code']
            and profile['profile_version'] == catalog['profile_version']
            and re.fullmatch('[0-9a-f]{64}', profile['fingerprint_sha256']), 'profile_version_invalid')
    return {'catalog': catalog, 'profile': profile}


def assert_templates_unchanged(api, args, before, report, phase):
    after = template_snapshot(api, args.organization_id)
    require(before == after, 'master_catalog_or_profile_changed_' + phase)
    report['checks'].append({'check': 'template_fingerprints_unchanged_' + phase, 'status': 'PASS',
        'master_catalog_fingerprint_sha256': after['catalog']['master_menu_fingerprint_sha256'],
        'profile_fingerprint_sha256': after['profile']['fingerprint_sha256'],
        'profile_and_artifacts_sha256': digest(after['profile'])})


def create_store_fixtures(api, args, report, templates):
    """Exactly A/B via the normal Owner business action, with stable same-body replay."""
    catalog = templates['catalog']
    overview = api.call('/owner/overview')
    organization = one(overview['organizations'], lambda x: x['id'] == args.organization_id,
                       'approved_organization_not_visible')
    require(organization['role_code'] == 'OWNER' and organization['can_create_store'] is True
            and str(organization['status']).lower() == 'active', 'organization_cannot_create_store')
    # Identity labels are selected by this script, not arbitrary caller-provided names.
    request_template = {key: catalog[key] for key in ('profile_code', 'profile_version',
                        'master_menu_key', 'master_menu_version', 'master_menu_fingerprint_sha256')}
    request_template['profile_fingerprint_sha256'] = templates['profile']['fingerprint_sha256']
    report['store_ids'] = []
    identities = []
    for side in ('A', 'B'):
        code = f'STG005_MENU_{args.run_id.upper()}_{side}'
        name = f'STG005 Menu Simplification {args.run_id.upper()} {side}'
        found = [x for x in organization['stores'] if x['code'] == code or x['name'] == name]
        require(len(found) <= 1 and all(x['code'] == code and x['name'] == name for x in found),
                'existing_synthetic_store_name_or_code_drift')
        identities.append((side, code, name, found))
    for side, code, name, found in identities:
        body = {**request_template, 'store_code': code, 'store_name': name}
        key = f'menu-simplification-{args.organization_id}-{args.run_id}-{side.lower()}'
        path = f'/owner/organizations/{args.organization_id}/stores'
        created = api.call(path, 'POST', body, key=key)
        store_id = created.get('store_id')
        require(isinstance(store_id, int) and store_id > 0, 'created_store_id_invalid')
        # Capture identity immediately, including an interrupted/failed replay batch.
        report['store_ids'].append(store_id)
        setattr(args, f'store_{side.lower()}_id', store_id)
        setattr(args, f'store_{side.lower()}_code', code)
        require(created['store_code'] == code and created['store_name'] == name,
                'created_store_identity_drift')
        require(not found or found[0]['id'] == store_id, 'store_replay_identity_drift')
        require(created['store_kind'] == 'BUSINESS' and created['store_status'] == 'active'
                and created['lifecycle_status'] == 'ACTIVE' and created['operational_state'] == 'LIVE'
                and created['is_live'] is True and created['validation_status'] == 'PASS',
                'normal_store_create_not_live')
        require(created['replayed'] is (bool(found)), 'unexpected_initial_create_replay')
        counts = {key: created['counts'].get(key) for key in ('station_count', 'category_count',
                  'item_count', 'option_count', 'pricing_policy_count', 'combo_component_count', 'printing_rule_count')}
        require(all(isinstance(value, int) and value > 0 for value in counts.values())
                and isinstance(created['request_id'], int) and created['request_id'] > 0,
                'normal_store_materialization_counts_incomplete')
        replay = api.call(path, 'POST', body, key=key)
        require(replay == {**created, 'replayed': True}, 'normal_store_same_key_replay_drift')
        context = api.call(f'/stores/{store_id}/context')
        require(context['id'] == store_id and context['is_live'] is True
                and context['operational_state'] == 'LIVE', 'created_store_context_not_live')
        menu_context = api.call(f'/admin/menu/management-context?store_id={store_id}')
        store = one(menu_context['stores'], lambda x: x['id'] == store_id, 'created_store_context_missing')
        require(store['code'] == code and store['name'] == name and store['organization_id'] == args.organization_id
                and store['provisioned_profile_fingerprint_sha256'] == request_template['profile_fingerprint_sha256']
                and store['provisioned_master_menu_fingerprint_sha256'] == request_template['master_menu_fingerprint_sha256'],
                'created_store_provenance_mismatch')
        addon_catalog = api.call(f'/admin/menu/addons?store_id={store_id}')
        require(isinstance(addon_catalog['addons'], list) and isinstance(addon_catalog['conflicts'], list)
                and bool(addon_catalog['addons'] or addon_catalog['conflicts'])
                and created['addon_conflicts'] == addon_catalog['conflicts'], 'created_addon_materialization_mismatch')
        report['checks'].append({'check': 'normal_owner_store_create_replay_live_materialization',
            'status': 'PASS' if created['replayed'] is False else 'NOT_RUN', 'store_id': store_id,
            'store_code': code, 'fresh_create': not created['replayed'], 'same_key_replay': 'PASS',
            'request_id': created['request_id'], 'counts': counts,
            'addon_count': len(addon_catalog['addons']), 'conflict_count': len(addon_catalog['conflicts']),
            'conflicts_sha256': digest(addon_catalog['conflicts']), 'request_body_sha256': digest(body)})
    require(args.store_a_id != args.store_b_id, 'created_stores_not_distinct')
    assert_templates_unchanged(api, args, templates, report, 'after_create')


def create_isolation_fixture(api, args, credentials, report):
    """One new OWNER on approved B. Normal Staff API preserves legacy Store scope."""
    require('wrong_store_owner' not in credentials, 'existing_negative_principal_must_not_be_replaced')
    roles = api.call('/admin/platform/roles')  # Positive capability proof before new credentials.
    one(roles, lambda x: x['code'] == 'OWNER', 'owner_staff_role_unavailable')
    staff = api.call(f'/admin/staff?store_id={args.store_b_id}')
    username = f'STG005_MENU_{args.run_id.upper()}_OWNER'
    require(not any(x['username'].lower() == username.lower() for x in staff),
            'isolation_username_exists_no_credential_mutation')
    password = secrets.token_urlsafe(15)
    fixture = {'login_identifier': username, 'login_password': password,
               'control_store_id': args.store_b_id}
    state = ROOT / 'state'
    require(state.resolve() == state and state.is_dir(), 'isolation_private_state_path')
    meta = state.stat()
    require(meta.st_uid == os.getuid() and stat.S_IMODE(meta.st_mode) in (0o700, 0o750),
            'isolation_private_state_permissions')
    path = state / f'menu-simplification-{args.run_id}-isolation-credentials.json'
    require(not path.exists() and not path.is_symlink(), 'isolation_private_input_exists_no_blind_retry')
    # Persist the new secret BEFORE POST so a lost response never requires reset.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'w') as handle:
        json.dump({'schema': 'MENU_SIMPLIFICATION_ISOLATION_INPUT_V1', 'approved_sha': args.approved_sha,
                   'run_id': args.run_id, 'organization_id': args.organization_id,
                   'wrong_store_owner': fixture}, handle)
        handle.write('\n')
        handle.flush()
        os.fsync(handle.fileno())
    report['isolation_credentials_file'] = str(path)  # Path only; no login body in report.
    created = api.call('/admin/staff', 'POST', {'store_id': args.store_b_id, 'username': username,
                                            'role_code': 'OWNER', 'password': password})
    require(created['username'] == username and created['role_code'] == 'OWNER'
            and created['store_id'] == args.store_b_id and created['status'] == 'active'
            and isinstance(created['id'], int), 'new_isolation_owner_identity_mismatch')
    credentials['wrong_store_owner'] = {**fixture, 'expected_user_id': created['id']}
    report['checks'].append({'check': 'normal_staff_isolation_owner_created', 'status': 'PASS',
        'user_id': created['id'], 'control_store_id': args.store_b_id,
        'authorization_shape': 'normal_staff_owner_without_memberships_legacy_store_fallback'})


def creation_evidence(args, report, runtime):
    if not args.creation_evidence:
        report['checks'].append({'check': 'fresh_normal_owner_store_creation', 'status': 'NOT_RUN',
                                 'reason': 'existing_fixtures_no_creation_evidence_supplied'})
        return
    evidence = json.loads(private_bytes(args.creation_evidence))
    require(evidence['schema'] == 'V26_BUSINESS_STORE_CREATE_ACCEPTANCE_V1'
            and evidence['source_sha'] == args.approved_sha
            and evidence['environment_sha256'] == runtime['environment_sha256']
            and evidence['backend_image_id'] == runtime['backend_image_id']
            and evidence['store_id'] in (args.store_a_id, args.store_b_id)
            and evidence['organization_id'] == args.organization_id
            and evidence['fresh_create'] == evidence['replay'] == evidence['final_result'] == 'PASS',
            'creation_evidence_binding_failed')
    report['checks'].append({'check': 'fresh_normal_owner_store_creation', 'status': 'PASS',
                             'store_id': evidence['store_id'], 'evidence_sha256': digest(evidence)})


def main(argv=None):
    report = {'schema': 'MENU_SIMPLIFICATION_ACCEPTANCE_V1', 'environment': 'restaurant-pos-staging',
              'production_mutated': False, 'hardware_printing': False, 'checks': [], 'result': 'FAIL'}
    api, fd, output = Api(), None, None
    started = time.monotonic()
    try:
        args = parse_args(argv)
        report.update(approved_sha=args.approved_sha, run_id=args.run_id,
                      store_ids=[args.store_a_id, args.store_b_id], preflight_sha256=args.preflight_evidence_sha256)
        output_path = Path(args.report)
        require(output_path.parent == ROOT / 'evidence' and output_path.parent.resolve() == output_path.parent,
                'report_must_be_in_staging_evidence')
        output = os.open(output_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        fd = action_lock()
        runtime = validate_runtime(args)
        report['runtime'] = runtime
        credentials = secret_input(args)
        api.login(credentials, args.organization_id)
        workspaces = api.call('/me/workspaces')
        require(any(x['id'] == args.organization_id and x['role_code'] == 'OWNER'
                    for x in workspaces['organizations']), 'organization_owner_membership_missing')
        templates = template_snapshot(api, args.organization_id)
        if args.create_fixtures:
            create_store_fixtures(api, args, report, templates)
            workspaces = api.call('/me/workspaces')
        else:
            creation_evidence(args, report, runtime)
        require(all(any(x['id'] == store and x['organization_id'] == args.organization_id
                        for x in workspaces['stores']) for store in (args.store_a_id, args.store_b_id)),
                'approved_store_membership_missing')
        acceptance = Acceptance(args, api, report)
        acceptance.run(credentials, runtime)
        assert_templates_unchanged(api, args, templates, report, 'after_menu_acceptance')
        report['checks'].append({'check': 'master_artifact_content_immutability', 'status': 'NOT_RUN',
            'reason': 'no_master_artifact_read_api_catalog_fingerprint_is_advertised_selection_only'})
        require(validate_runtime(args) == runtime, 'runtime_drift_during_acceptance')
        report['result'] = 'PARTIAL' if any(x['status'] == 'NOT_RUN' for x in report['checks']) else 'PASS'
    except NoGo as error:
        report['failure'] = str(error)
    except Exception:
        report['failure'] = 'unexpected_contract_or_local_io_error_no_sensitive_details'
    finally:
        failed_request = api.last_request if report['result'] == 'FAIL' else None
        try:
            api.logout()
        except Exception:
            report['logout'] = 'FAIL'
            report['result'] = 'FAIL'
        if report['result'] == 'FAIL':
            report['last_api_request'] = failed_request or api.last_request
        report['elapsed_seconds'] = round(time.monotonic() - started, 2)
        if output is not None:
            with os.fdopen(output, 'w') as handle:
                json.dump(report, handle, ensure_ascii=False, indent=2)
                handle.write('\n')
        if fd is not None:
            os.close(fd)
    print(json.dumps(report, ensure_ascii=False, sort_keys=True))
    return 0 if report['result'] == 'PASS' else 2 if report['result'] == 'PARTIAL' else 1


if __name__ == '__main__':
    sys.exit(main())
