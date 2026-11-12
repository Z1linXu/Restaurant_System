#!/usr/bin/env python3
"""Real localhost HTTP protocol checks. No Android worker or socket print operation.

The only TCP listener beyond PostgreSQL/backend is a loopback sentinel that counts
connections and immediately closes them. It never reads, sends or forwards bytes.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import socket
import threading
import time

import five_orders
import runtime

SENTINEL_PORT = 29512
NO_PRINT = 'SYNTHETIC_HTTP_PROTOCOL_SIMULATION_ONLY_NO_HARDWARE_NO_PRINT_ATTEMPT'


class Sentinel:
    def __enter__(self):
        self.connections = 0
        self.stopped = threading.Event()
        self.listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.listener.bind(('127.0.0.1', SENTINEL_PORT))
        self.listener.listen(8)
        self.listener.settimeout(0.2)
        self.thread = threading.Thread(target=self.listen, daemon=True)
        self.thread.start()
        return self

    def listen(self):
        while not self.stopped.is_set():
            try:
                connection, _ = self.listener.accept()
                self.connections += 1
                connection.close()  # No recv/send/forward, even if the backend misbehaves.
            except socket.timeout:
                pass
            except OSError:
                break

    def __exit__(self, *_):
        self.stopped.set()
        self.listener.close()
        self.thread.join(timeout=1)


def pad(api, device, path, method='GET', body=None, expected=(200,), bad_token=False):
    return api.call(path, method, body, expected=expected, authenticated=False,
        extra_headers={'X-Device-Id': str(device['device_id']),
                       'X-Device-Token': 'SYNTHETIC_INVALID_TOKEN' if bad_token else device['device_token']})


def mode(api, store_id, value):
    api.call('/admin/printing/status', 'PUT', {'store_id': store_id, 'printing_mode': value})
    readback = api.call(f'/admin/printing?store_id={store_id}')
    runtime.require(readback['printing_mode'] == value, 'printing mode readback differs')


def assignment_request(row):
    return {key: row.get(key) for key in ('store_id','printer_id','enabled','font_size','takeout_receipt_copies')}


def wait_queued(api, order_id):
    for _ in range(80):
        jobs = api.call(f'/orders/{order_id}/print-jobs')
        if len(jobs) >= 2 and all(job['execution_mode'] == 'PAD_DIRECT' for job in jobs):
            runtime.require(len(jobs) == 2 and {row['module_code'] for row in jobs} == {'GRAB','FRONTDESK_RECEIPT'},
                            'one GRAB and one FRONTDESK expected')
            runtime.require(all(job['status'] == 'PENDING' for job in jobs), 'Pad jobs must queue without backend printing')
            return jobs
        if any(job['status'] == 'FAILED' for job in jobs):
            raise RuntimeError('PAD artifact/queue failure: ' + str([(job['id'],job.get('error_code'),job.get('error_message')) for job in jobs]))
        time.sleep(0.25)
    raise RuntimeError('Pad queue did not settle')


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute', action='store_true', required=True)
    args = parser.parse_args()
    c = runtime.config()
    runtime.require(c.get('frontdesk_username') == 'allo_frontdesk_qa' and c.get('frontdesk_password'),
                    'new synthetic Frontdesk fixture required')
    artifact = json.loads((runtime.PRIVATE / 'qa-backend-process.json').read_text())
    runtime.require(artifact['phase'] == 'runtime'
        and hashlib.sha256(Path(artifact['jar']).read_bytes()).hexdigest() == artifact['sha256'],
        'exact new QA runtime artifact required')
    stores = json.loads(runtime.sql(c, "select json_agg(json_build_object('id',id,'code',code,'name',name,'printing_mode',printing_mode) order by id) from stores"))
    runtime.require(len(stores) == 2 and stores[0]['id'] == 1 and stores[0]['code'] == 'ALLO_NOUILLES_TEST'
        and stores[0]['name'] == 'SYNTHETIC Allo QA' and stores[0]['printing_mode'] == 'MOCK'
        and stores[1]['id'] == 2 and stores[1]['code'] == 'ALLO_SCOPE_NEGATIVE', 'exact fresh synthetic QA stores required')
    runtime.require(runtime.sql(c, "select count(*) from printer_configs where printer_type <> 'MOCK' or nullif(ip_address,'') is not null") == '0',
                    'initial printer configuration must be endpoint-free MOCK')
    store_id, other_store = 1, 2
    run_id = str(time.time_ns())
    evidence = runtime.PRIVATE / f'qa-api-safety-{run_id}.json'
    api = five_orders.Api()
    devices, orders, printers, assignments = [], [], [], []
    report = {'result': 'FAIL', 'started_at_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
        'artifact': {key: artifact[key] for key in ('sha256','build_sha','phase')},
        'target': five_orders.BASE, 'database_host': '127.0.0.1', 'database_port': runtime.DB_PORT,
        'stores': stores, 'checks': [], 'physical_printing': 'NOT TESTED',
        'worker': 'SYNTHETIC_HTTP_CLIENT_ONLY_NO_SOCKET_PRINT_NO_ANDROID',
        'completion_semantics': NO_PRINT}
    def record(name, **facts):
        report['checks'].append({'check': name, 'result': 'PASS', **facts})
        print(json.dumps({'check': name, 'result': 'PASS'}))
    sentinel = None
    mutated = False
    try:
        owner = api.call('/auth/login', 'POST', {'login_identifier': 'allo_owner', 'password': c['owner_password']})
        api.token = owner['access_token']
        safety = api.call(f'/stores/{store_id}/template/runtime-safety')
        runtime.require(safety['environment'] == 'production'
            and set(safety['allowed_printing_modes']) == {'DISABLED','MOCK','PAD_DIRECT'}
            and safety['endpoint_configuration_enabled'] is True, 'production printing permission chain required')
        report['runtime_safety'] = safety
        frontdesk = five_orders.Api()
        logged = frontdesk.call('/auth/login', 'POST', {'login_identifier': c['frontdesk_username'],
                                                      'password': c['frontdesk_password']})
        runtime.require(logged['user']['role_code'] == 'FRONTDESK' and logged['user']['store_id'] == store_id,
                        'real synthetic Frontdesk role/store required')
        frontdesk.token = logged['access_token']
        frontdesk.call(f'/menu/catalog?store_id={store_id}')
        frontdesk.call(f'/stores/{store_id}/receipt-presentation', expected=(403,))
        frontdesk.call(f'/admin/menu/pricing-capabilities?store_id={store_id}', expected=(403,))
        frontdesk.call('/admin/menu/category-pricing/preview', 'POST', {'store_id': store_id}, expected=(403,))
        frontdesk.call('/admin/menu/category-pricing/apply', 'POST', {'store_id': store_id}, expected=(403,))
        # Existing Add-on capability is preserved; do not fabricate a new role restriction.
        frontdesk.call(f'/admin/menu/addons?store_id={store_id}')
        record('frontdesk_pos_200_receipt_and_new_bulk_403_existing_addon_access_preserved',
               user_id=logged['user']['id'], role='FRONTDESK', calls=frontdesk.calls)
        api.call(f'/menu/catalog?store_id={other_store}', expected=(403,))
        record('owner_cross_store_scope_denied_403')
        baseline_catalog = api.call(f'/menu/catalog?store_id={store_id}')
        printers = api.call(f'/admin/printing/printers?store_id={store_id}')
        assignments = api.call(f'/admin/printing/assignments?store_id={store_id}')
        runtime.require(len(printers) == 2 and len(assignments) == 2, 'known synthetic bootstrap printer roles required')
        for label in ('A','B'):
            device = api.call('/devices/register', 'POST', {'store_id': store_id,
                'device_name': 'SYNTHETIC_HTTP_' + run_id + '_' + label, 'device_type': 'PAD',
                'platform': 'ANDROID', 'app_version': 'http-protocol-simulation-no-worker'})
            devices.append(device)
        a, b = devices
        report['device_ids'] = [row['device_id'] for row in devices]
        pad(api, a, f'/stores/{store_id}/printing/jobs/pending', expected=(401,), bad_token=True)
        pad(api, a, f'/stores/{other_store}/printing/jobs/pending', expected=(403,))
        record('device_bad_token_401_cross_store_403')
        with Sentinel() as sentinel:
            mutated = True
            for printer in printers:
                body = dict(printer)
                body.update({'ip_address': '127.0.0.1', 'port': SENTINEL_PORT,
                             'printer_type': 'ESC_POS_TCP', 'text_encoding': 'UTF-8', 'font_size': 'MEDIUM'})
                api.call(f'/admin/printing/printers/{printer["id"]}', 'PUT', body)
            for assignment in assignments:
                body = assignment_request(assignment)
                body['font_size'] = 'MEDIUM'
                api.call('/admin/printing/assignments/' + assignment['module_code'], 'PUT', body)
            mode(api, store_id, 'PAD_DIRECT')
            item = next(item for category in baseline_catalog['categories'] for item in category['items'] if item['sku'] == 'AN_B1')
            leaf = five_orders.option(item, 'choice_thin')
            draft = api.call('/orders', 'POST', {'store_id': store_id, 'order_type': 'dine_in',
                'table_no': 'TEST-2', 'items': [{'menu_item_id': item['id'], 'quantity': 1,
                    'notes': NO_PRINT, 'combo_role': 'standalone', 'options': [{'option_id': leaf['id'], 'quantity': 1}]}]})
            orders.append(draft['id'])
            api.call(f'/orders/{draft["id"]}/submit', 'POST', expected=(403,))
            runtime.require(api.call(f'/orders/{draft["id"]}')['status'] == 'draft', 'unsigned Pad submission changed order')
            api.device = a
            api.call(f'/orders/{draft["id"]}/submit', 'POST')
            jobs = wait_queued(api, draft['id'])
            report['queued_jobs'] = jobs
            runtime.require(all(row['preferred_device_id'] == a['device_id'] for row in jobs), 'signed origin lost before queue')
            pending = pad(api, a, f'/stores/{store_id}/printing/jobs/pending')
            runtime.require({job['id'] for job in jobs}.issubset({job['id'] for job in pending}), 'origin device cannot see queued jobs')
            record('pad_direct_signed_submit_queued_two_frozen_jobs_no_backend_print', order_id=draft['id'],
                   job_ids=[row['id'] for row in jobs], sentinel_connections=sentinel.connections)
            by_module = {row['module_code']: row for row in jobs}
            kitchen, front = by_module['GRAB']['id'], by_module['FRONTDESK_RECEIPT']['id']
            token_a, token_b = secrets.token_hex(20), secrets.token_hex(20)
            claim = pad(api, a, f'/printing/jobs/{kitchen}/claim', 'POST', {'client_attempt_token': token_a, 'lease_seconds': 15})
            runtime.require(claim['status'] == 'CLAIMED' and claim['claimed_by_device_id'] == a['device_id'], 'claim ownership mismatch')
            duplicate = pad(api, a, f'/printing/jobs/{kitchen}/claim', 'POST', {'client_attempt_token': token_a, 'lease_seconds': 15})
            runtime.require(duplicate['id'] == kitchen and duplicate['claim_expires_at'] == claim['claim_expires_at'], 'same claim replay changed lease')
            pad(api, b, f'/printing/jobs/{kitchen}/claim', 'POST', {'client_attempt_token': token_b}, expected=(409,))
            pad(api, b, f'/printing/jobs/{kitchen}/payload', expected=(409,))
            pad(api, a, f'/printing/jobs/{kitchen}/start-print', 'POST', {'client_attempt_token': 'wrong-attempt'}, expected=(409,))
            record('claim_idempotent_wrong_device_and_attempt_denied')
            # Exercise an actual expired lease through HTTP, without editing DB clocks/state.
            time.sleep(16)
            pad(api, a, f'/printing/jobs/{kitchen}/start-print', 'POST', {'client_attempt_token': token_a}, expected=(409,))
            reclaimed = pad(api, b, f'/printing/jobs/{kitchen}/claim', 'POST', {'client_attempt_token': token_b, 'lease_seconds': 15})
            runtime.require(reclaimed['claimed_by_device_id'] == b['device_id'], 'expired CLAIMED job was not safely reclaimable')
            pad(api, a, f'/printing/jobs/{kitchen}/start-print', 'POST', {'client_attempt_token': token_a}, expected=(409,))
            started = pad(api, b, f'/printing/jobs/{kitchen}/start-print', 'POST', {'client_attempt_token': token_b, 'lease_seconds': 60})
            runtime.require(started['status'] == 'PRINTING', 'start-print did not transition')
            for device, token in ((a, token_a), (b, token_b), (b, 'second-attempt')):
                pad(api, device, f'/printing/jobs/{kitchen}/claim', 'POST', {'client_attempt_token': token}, expected=(409,))
            payload = pad(api, b, f'/printing/jobs/{kitchen}/payload')
            runtime.require(payload['printer_host'] == '127.0.0.1' and payload['printer_port'] == SENTINEL_PORT
                and payload['rendered_text_snapshot'] == by_module['GRAB']['rendered_text_snapshot'], 'payload differs from frozen queue')
            data = base64.b64decode(payload['escpos_payload_base64'], validate=True)
            runtime.require(data, 'real rendered ESC/POS payload missing')
            frozen = json.loads(runtime.sql(c, f'select payload_snapshot from print_jobs where id={kitchen} and store_id=1'))
            runtime.require(frozen['receipt_artifact']['transport'] == 'PAD_DIRECT_ESCPOS_V1'
                and frozen['receipt_artifact']['bytes_sha256'] == hashlib.sha256(data).hexdigest(), 'physical-intent artifact/hash contract mismatch')
            report['payload_proof'] = {'job_id': kitchen, 'bytes': len(data),
                'sha256': hashlib.sha256(data).hexdigest(), 'artifact': frozen['receipt_artifact'],
                'rendered_text_snapshot': payload['rendered_text_snapshot'], 'physical_output': 'NOT TESTED'}
            pad(api, b, f'/printing/jobs/{kitchen}/complete', 'POST', {'client_attempt_token': 'wrong-attempt'}, expected=(409,))
            pad(api, a, f'/printing/jobs/{kitchen}/complete', 'POST', {'client_attempt_token': token_a}, expected=(409,))
            record('expired_claim_recovery_printing_not_reclaimable_payload_and_wrong_completion')
            mode(api, store_id, 'DISABLED')
            pad(api, a, f'/stores/{store_id}/printing/jobs/pending', expected=(409,))
            pad(api, a, f'/printing/jobs/{front}/claim', 'POST', {'client_attempt_token': token_a}, expected=(409,))
            pad(api, b, f'/printing/jobs/{kitchen}/start-print', 'POST', {'client_attempt_token': token_b}, expected=(409,))
            pad(api, b, f'/printing/jobs/{kitchen}/payload', expected=(409,))
            completed = pad(api, b, f'/printing/jobs/{kitchen}/complete', 'POST', {'client_attempt_token': token_b, 'raw_result': NO_PRINT})
            runtime.require(completed['status'] == 'PRINTED' and completed['printed_by_device_id'] == b['device_id'], 'in-flight result reporting failed')
            replay = pad(api, b, f'/printing/jobs/{kitchen}/complete', 'POST', {'client_attempt_token': token_b, 'raw_result': NO_PRINT})
            runtime.require(replay['id'] == kitchen and replay['printed_at'] == completed['printed_at'], 'completion replay mutated result')
            record('disabled_blocks_pending_claim_start_payload_but_records_inflight_simulated_completion')
            mode(api, store_id, 'PAD_DIRECT')
            pad(api, a, f'/printing/jobs/{front}/claim', 'POST', {'client_attempt_token': token_a, 'lease_seconds': 15})
            pad(api, a, f'/printing/jobs/{front}/start-print', 'POST', {'client_attempt_token': token_a, 'lease_seconds': 60})
            mode(api, store_id, 'DISABLED')
            failed = pad(api, a, f'/printing/jobs/{front}/fail', 'POST', {'client_attempt_token': token_a,
                'error_code': 'SYNTHETIC_HTTP_NO_PRINT_ATTEMPT', 'error_message': NO_PRINT, 'raw_result': NO_PRINT})
            runtime.require(failed['status'] == 'FAILED' and failed['error_code'] == 'SYNTHETIC_HTTP_NO_PRINT_ATTEMPT', 'simulated failure was not saved')
            mode(api, store_id, 'PAD_DIRECT')
            pad(api, a, f'/printing/jobs/{front}/claim', 'POST', {'client_attempt_token': 'no-blind-retry'}, expected=(409,))
            record('disabled_accepts_inflight_simulated_failure_no_automatic_retry')
            mode(api, store_id, 'MOCK')
            finished = api.call(f'/orders/{draft["id"]}/complete', 'POST')
            runtime.require(finished['status'] == 'completed', 'protocol synthetic order not finished')
            report['final_jobs'] = api.call(f'/orders/{draft["id"]}/print-jobs')
            runtime.require(sentinel.connections == 0, 'backend attempted TCP printer connection')
            record('loopback_sentinel_zero_connections_no_worker_no_socket_print', sentinel_connections=0,
                   sentinel_endpoint=f'127.0.0.1:{SENTINEL_PORT}', hardware='NOT TESTED')
        runtime.require(sentinel.connections == 0, 'sentinel observed a late backend TCP connection')
        current_catalog = api.call(f'/menu/catalog?store_id={store_id}')
        runtime.require(current_catalog['content_hash'] == baseline_catalog['content_hash'], 'protocol test changed menu/prices')
        report['result'] = 'PASS'
    except Exception as error:
        report['error'] = str(error)
        raise
    finally:
        cleanup_errors = []
        if mutated:
            try:
                mode(api, store_id, 'MOCK')
                for printer in printers:
                    api.call(f'/admin/printing/printers/{printer["id"]}', 'PUT', printer)
                for assignment in assignments:
                    api.call('/admin/printing/assignments/' + assignment['module_code'], 'PUT', assignment_request(assignment))
                restored = api.call(f'/admin/printing?store_id={store_id}')
                runtime.require(restored['printing_mode'] == 'MOCK' and all(
                    row['printer_type'] == 'MOCK' and not row.get('ip_address') for row in restored['printers']),
                    'QA must return to endpoint-free MOCK before any browser/five-orders work')
                report['restored_endpoint_free_mock'] = True
            except Exception as error:
                cleanup_errors.append(str(error))
        for device in devices:
            try:
                api.call(f'/admin/printing/devices/{device["device_id"]}/revoke?store_id={store_id}', 'POST')
            except Exception as error:
                cleanup_errors.append(str(error))
        report['owned_order_ids'] = orders
        report['devices_revoked'] = not cleanup_errors
        report['sentinel_connections'] = sentinel.connections if sentinel is not None else None
        report['api_calls'] = api.calls
        if cleanup_errors:
            report['result'] = 'FAIL'
            report['cleanup_errors'] = cleanup_errors
        runtime.write_private(evidence, five_orders.json_safe(report))
    runtime.require(report['result'] == 'PASS', 'QA protocol cleanup failed; inspect private evidence')
    print(json.dumps({'result': 'PASS', 'evidence': str(evidence), 'physical_printing': 'NOT TESTED',
                      'sentinel_connections': report['sentinel_connections'], 'mode': 'MOCK'}))


if __name__ == '__main__':
    main()
