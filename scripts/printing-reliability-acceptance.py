#!/usr/bin/env python3
"""Bounded endpoint-free printing acceptance. Reuses reviewed Staging guards/API client.

Only audited synthetic Store 26 (MENU_ALIAS1601_A); Store 27 is read-only control.
No SQL, production target, real endpoint, PAD_DIRECT mode or physical transport.
Synthetic device tokens stay in memory, devices are revoked on exit. No new Store.
"""
import argparse
import hashlib
import hmac
import importlib.util
import json
import os
from pathlib import Path
import re
import time
import urllib.request
import urllib.error

spec = importlib.util.spec_from_file_location('menu_acceptance', Path(__file__).with_name('menu-simplification-acceptance.py'))
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
require = m.require
STORE = 26
CONTROL = 27


def menu_business_snapshot(catalog):
    # MenuCatalogResponse.generated_at is a response clock, not menu mutation.
    # Keep every other field, including revision/hash/prices/eligibility/scope.
    return {key: value for key, value in catalog.items() if key != 'generated_at'}


class ProofApi(m.Api):
    device = None
    def call(self, path, method='GET', body=None, expected=(200,), **kwargs):
        if self.device is None or method != 'POST' or not re.fullmatch(r'/orders/\d+/(submit|updates|reprint)|/admin/printing/jobs/\d+/reprint', path):
            return super().call(path, method, body, expected, **kwargs)
        raw = b'' if body is None else json.dumps(body).encode()
        auth = 'Bearer ' + self.token
        stamp = str(int(time.time()))
        device = str(self.device['device_id'])
        digest = lambda b: hashlib.sha256(b).hexdigest()
        canonical = f'PRINT_ORIGIN_V1\n{device}\n{stamp}\nPOST\n/api/v1{path}\n{digest(raw)}\n{digest(auth.encode())}'
        signature = hmac.new(hashlib.sha256(self.device['device_token'].encode()).digest(), canonical.encode(), hashlib.sha256).hexdigest()
        request = urllib.request.Request(m.API_BASE + path, data=raw, method=method, headers={
            'Content-Type': 'application/json', 'Authorization': auth,
            'X-Print-Device-Id': device, 'X-Print-Timestamp': stamp, 'X-Print-Signature': signature})
        try:
            response = self.opener.open(request, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        except (OSError, urllib.error.URLError):
            raise m.NoGo('transport_unknown_no_automatic_retry') from None
        with response:
            require(response.code in expected, 'proof_http_' + str(response.code))
            data = json.loads(response.read(4 * 1024 * 1024))
            require(data.get('success') is (response.code < 400), 'proof_envelope')
            return data['data'] if response.code < 400 else data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--approved-sha', required=True)
    parser.add_argument('--preflight-evidence', required=True)
    parser.add_argument('--preflight-evidence-sha256', required=True)
    parser.add_argument('--secrets-file', required=True)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--report', required=True)
    args = parser.parse_args()
    require(args.execute and re.fullmatch('[a-z0-9]{4,16}', args.run_id), 'explicit_bounded_action_required')
    require(Path(__file__).resolve() == m.ROOT / 'releases' / args.approved_sha / 'scripts/printing-reliability-acceptance.py', 'exact_release_required')
    report_path = Path(args.report)
    require(report_path.parent == m.ROOT / 'evidence' and not report_path.exists(), 'new_private_report_required')
    report = {'sha': args.approved_sha, 'result': 'FAIL', 'store_id': STORE, 'production_mutation': 'NONE', 'physical_printing': 'NONE', 'checks': [], 'order_ids': [], 'device_ids': []}
    def record(check, **facts): report['checks'].append({'check': check, 'status': 'PASS', **facts})
    api = ProofApi(); devices = []; lock = None
    device_baseline = None
    attempted_device_names = set()
    try:
        lock = m.action_lock()
        runtime = m.validate_runtime(args)
        require(runtime['printing_enabled'], 'printing_feature_required')
        api.login(json.loads(m.private_bytes(args.secrets_file)), 1)
        overview = api.call('/owner/overview')
        stores = [s for org in overview['organizations'] for s in org['stores']]
        for store_id, code in ((STORE, 'STG005_MENU_ALIAS1601_A'), (CONTROL, 'STG005_MENU_ALIAS1601_B')):
            s = m.one(stores, lambda x: x['id'] == store_id, 'audited_fixture_missing')
            require(s['code'] == code and s['provisioning_source'] == 'PHASE_B_OWNER_PROVISIONING' and s['is_live'], 'audited_fixture_provenance_mismatch')
        before_control = api.call(f'/menu/catalog?store_id={CONTROL}')
        before_menu = api.call(f'/menu/catalog?store_id={STORE}')
        before_templates = m.template_snapshot(api, 1)
        old_jobs = api.call(f'/admin/printing/jobs?store_id={STORE}')
        printing = api.call(f'/admin/printing?store_id={STORE}')
        require(printing['printing_mode'] == 'MOCK' and not printing['printers'], 'endpoint_free_mock_required')
        # Logical assignment only; explicit runtime policy blocks any endpoint/hardware binding.
        for module in ('GRAB', 'HOT_KITCHEN'):
            if any(a['module_code'] == module and a['enabled'] for a in printing['assignments']):
                continue
            api.call('/admin/printing/assignments/' + module, 'PUT', {'store_id': STORE, 'printer_id': None,
                     'enabled': True, 'font_size': 'NORMAL', 'takeout_receipt_copies': 1})
        device_baseline = {d['id'] for d in api.call(f'/admin/printing/devices?store_id={STORE}')}
        for name in ('A', 'B'):
            device_name = 'SYNTHETIC_PRINT_' + args.run_id + '_' + name
            attempted_device_names.add(device_name)  # Record intent before a response can be lost.
            d = api.call('/devices/register', 'POST', {'store_id': STORE, 'device_name': device_name,
                         'device_type': 'PAD', 'platform': 'ANDROID', 'app_version': '0.3.0-synthetic-proof'})
            devices.append(d); report['device_ids'].append(d['device_id'])
        item = m.one(m.flatten_menu(before_menu), lambda x: x['sku'] == 'beef_chow_mein', 'stable_fixture_item_missing')
        onion = m.one(item['options'], lambda x: x['option_code'] == 'remove_onion', 'onion_identity_missing')
        jobs_by_device = {}
        for index, d in enumerate(devices):
            api.device = d
            order = api.call('/orders', 'POST', {'store_id': STORE, 'order_type': 'pickup', 'pickup_no': 'PRINT-' + args.run_id + '-' + str(index),
                'items': [{'menu_item_id': item['id'], 'quantity': 1, 'combo_role': 'standalone', 'options': [m.option_snapshot(onion)]}]})
            order_id = order['id']
            require(order_id not in report['order_ids'], 'second_pad_order_must_be_distinct')
            report['order_ids'].append(order_id)
            api.call(f'/orders/{order_id}/submit', 'POST')
            jobs = []
            for _ in range(20):
                jobs = api.call(f'/orders/{order_id}/print-jobs')
                if all(any(j['module_code'] == module and j['status'] == 'PRINTED' for j in jobs) for module in ('GRAB', 'HOT_KITCHEN')): break
                time.sleep(1)
            for module in ('GRAB', 'HOT_KITCHEN'):
                j = m.one(jobs, lambda x: x['module_code'] == module, 'module_job_missing')
                require(j['status'] == 'PRINTED' and j.get('printer_id') is None and j.get('printed_by_device_id') is None, 'mock_not_printed_safely')
                require('走洋葱' in j['rendered_text_snapshot'] and '走葱' not in j['rendered_text_snapshot'], 'onion_semantic_wrong')
                require(j['preferred_device_id'] == d['device_id'], 'outbox_origin_lost')
            record('mock_onion_grab_hot_and_durable_origin', device_id=d['device_id'], order_id=order_id, job_ids=[j['id'] for j in jobs])
            jobs_by_device[d['device_id']] = jobs
        source = next(j for j in jobs_by_device[devices[0]['device_id']] if j['module_code'] == 'GRAB')
        order_id = report['order_ids'][0]
        frozen_order = api.call(f'/orders/{order_id}')
        api.device = devices[1]
        body = {'idempotency_key': 'print-' + args.run_id + '-job'}
        fresh = api.call(f'/admin/printing/jobs/{source["id"]}/reprint', 'POST', body)
        replay = api.call(f'/admin/printing/jobs/{source["id"]}/reprint', 'POST', body)
        require(fresh['id'] != source['id'] and replay['id'] == fresh['id'] and fresh['rendered_text_snapshot'] == source['rendered_text_snapshot'], 'new_job_snapshot_or_idempotency')
        require(fresh['preferred_device_id'] == devices[1]['device_id'] and fresh['reprint_source_job_id'] == source['id'], 'reprint_origin_or_parent')
        record('job_reprint_new_frozen_replay', source_job_id=source['id'], new_job_id=fresh['id'])
        body = {'receipt_type': 'GRAB', 'idempotency_key': 'print-' + args.run_id + '-order'}
        fresh = api.call(f'/orders/{order_id}/reprint', 'POST', body)
        replay = api.call(f'/orders/{order_id}/reprint', 'POST', body)
        require(fresh['id'] == replay['id'] and fresh['preferred_device_id'] == devices[1]['device_id'], 'order_reprint_replay_origin')
        record('order_reprint_current_pad_replay', new_job_id=fresh['id'])
        denied = api.call(f'/orders/{order_id}/reprint', 'POST', {**body, 'receipt_type': 'HOT_KITCHEN'}, expected=(400,))
        require('conflict' in denied['message'].lower(), 'changed_intent_not_rejected')
        api.device = None
        api.call(f'/admin/printing/devices/{devices[1]["device_id"]}/disable?store_id={STORE}', 'POST')
        api.device = devices[1]
        api.call(f'/orders/{order_id}/reprint', 'POST', {'receipt_type': 'GRAB', 'idempotency_key': 'print-' + args.run_id + '-disabled'}, expected=(403,))
        record('disabled_attestation_rejected')
        api.device = None
        current = {j['id']: j for j in api.call(f'/admin/printing/jobs?store_id={STORE}')}
        require(all(current.get(j['id']) == j for j in old_jobs), 'historical_print_job_mutated')
        require(current[source['id']] == source and api.call(f'/orders/{order_id}') == frozen_order, 'source_or_order_snapshot_changed')
        require(menu_business_snapshot(api.call(f'/menu/catalog?store_id={STORE}')) == menu_business_snapshot(before_menu)
                and menu_business_snapshot(api.call(f'/menu/catalog?store_id={CONTROL}')) == menu_business_snapshot(before_control), 'menu_cross_store_mutation')
        require(m.template_snapshot(api, 1) == before_templates, 'shared_authority_mutated')
        record('old_jobs_orders_menu_control_store_and_shared_authority_unchanged')
        report['not_executed'] = ['Staging PAD_DIRECT claim/affinity expiry/start-print/active-job confirmation (MOCK-only policy)',
                                  'real Android lifecycle/renderer/physical TCP/three-Pad testing (Owner Gate)']
        report['result'] = 'PASS_NON_PHYSICAL_SLICE'
    except m.NoGo as error:
        report['failure'] = str(error)
    except Exception:
        report['failure'] = 'unexpected_error_no_secrets_emitted'
    finally:
        api.device = None
        if device_baseline is not None:
            try:
                # Discover even a registered device whose credential response was lost.
                current_devices = api.call(f'/admin/printing/devices?store_id={STORE}')
                cleanup = cleanup_candidates(current_devices, device_baseline, attempted_device_names)
                report['device_ids'] = sorted(set(report['device_ids']) | {d['id'] for d in cleanup})
                for d in cleanup:
                    api.call(f'/admin/printing/devices/{d["id"]}/revoke?store_id={STORE}', 'POST')
                report['revoked_device_ids'] = [d['id'] for d in cleanup]
            except Exception:
                report['result'] = 'FAIL'; report['cleanup_failure'] = 'synthetic_device_discovery_or_revoke_failed'
        try: api.logout()
        except Exception: report['logout'] = 'FAILED'
        if lock is not None: os.close(lock)
        fd = os.open(report_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(fd, 'w') as handle: json.dump(report, handle, ensure_ascii=False, indent=2)
    print(json.dumps(report, ensure_ascii=False))
    return 0 if report['result'] == 'PASS_NON_PHYSICAL_SLICE' else 1


def cleanup_candidates(rows, baseline, attempted_names):
    return [d for d in rows if d['id'] not in baseline and d['store_id'] == STORE
            and d['device_name'] in attempted_names]


if __name__ == '__main__':
    raise SystemExit(main())
