#!/usr/bin/env python3
"""Locked, nonbusiness receive-only acceptance; no Uber/order/printer mutation.

Staging signs one synthetic receipt with its existing private key and retains it.
Production without its actual key is BLOCKED, not a fabricated HMAC pass.
Existing reviewed smoke helpers mint short-lived in-memory POS read identities;
this does not claim to test a human password login. No response payload is logged.
"""
import argparse
import hashlib
import hmac
import importlib.util
import json
import os
from pathlib import Path
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


ops = load('uber_promotion', 'promote-uber-receive-only-20261022.py')
smoke = load('existing_pos_smoke', 'production-v26-smoke.py')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args):
        return None


def request(url, *, raw=None, headers=None):
    opener = urllib.request.build_opener(NoRedirect())
    try:
        with opener.open(urllib.request.Request(url, data=raw, headers=headers or {}), timeout=20) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()
    except Exception:
        raise ops.PromotionError('HTTP_REQUEST_FAILED') from None


def psql(db, sql):
    raw = ops.run(['docker', 'exec', '-i', db, 'sh', '-c',
        'psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],
        input=('BEGIN READ ONLY;\n' + sql + '\nROLLBACK;\n').encode())
    return json.loads(raw)


def verify(environment, image, sha):
    ops.require(bool(re.fullmatch(r'sha256:[0-9a-f]{64}', image))
                and bool(re.fullmatch(r'[0-9a-f]{40}', sha)), 'ARTIFACT_INVALID')
    project = 'cloud' if environment == 'production' else 'restaurant-pos-staging'
    db = project + '-db-1'
    root = ops.BASE / environment / ('uber-receive-only-' + sha)
    base = 'https://' + ('pos' if environment == 'production' else 'staging-pos') + '.lanzhounoodlesmtl.com'
    with ops.operation_locks():
        backend = ops.inspect(project + '-backend-1')
        env = ops.env_of(backend)
        ops.require(backend['Image'] == image and env.get('UBER_EATS_WORKER_ENABLED') == 'false'
                    and env.get('UBER_EATS_PROCESSING_MODE') == 'KITCHEN_MIRROR', 'RUNTIME_DRIFT')
        ops.require(env.get('UBER_EATS_ENVIRONMENT') == ('production' if environment == 'production' else 'sandbox'), 'ENVIRONMENT_DRIFT')
        ops.require(ops.inspect(image)['Config']['Labels']['org.opencontainers.image.revision'] == sha, 'SOURCE_SHA_DRIFT')
        ops.health(base)
        before = ops.inventory(db)
        ops.queue_guard(before, environment == 'production')
        result = {'environment': environment, 'source_sha': sha, 'image': image,
                  'worker_enabled': False, 'health': 'PASS'}
        endpoint = base + '/api/v1/integrations/uber-eats/webhook'
        status, _ = request(endpoint)
        ops.require(status == 405, 'WEBHOOK_GET_NOT_405')
        result['get_http_status'] = status

        # These GETs reuse the repository's reviewed short-lived identity scheme.
        identity = smoke.numeric_identity(db)
        user, role, role_code, store, organization = identity
        token = smoke.mint_token(env['JWT_SECRET'], user, role, store, organization, role_code)
        headers = {'Authorization': 'Bearer ' + token}
        paths = {'auth': '/api/v1/auth/me', 'workspaces': '/api/v1/me/workspaces',
                 'menu': f'/api/v1/menu/catalog?store_id={store}',
                 'frontdesk': f'/api/v1/frontdesk/dining-tables?store_id={store}',
                 'existing_orders': f'/api/v1/frontdesk/orders/history?store_id={store}&limit=1',
                 'print_center': f'/api/v1/admin/printing?store_id={store}',
                 'devices': f'/api/v1/admin/printing/devices?store_id={store}'}
        for name, path in paths.items():
            status, body = request(base + path, headers=headers)
            ops.require(status == 200 and json.loads(body).get('success') is True, 'POS_READ_SMOKE_FAILED')
        status, _ = request(base + '/api/v1/stores/9223372036854775807/context', headers=headers)
        ops.require(status == 403, 'STORE_ISOLATION_FAILED')
        result['authenticated_pos_get_checks'] = len(paths)
        result['store_isolation'] = 'PASS'
        result['password_login'] = 'NOT_TESTED'
        result['live_draft_submission'] = 'NOT_CREATED'
        for route in ('/login', f'/stores/{store}/frontdesk', f'/stores/{store}/admin/printing'):
            status, body = request(base + route)
            ops.require(status == 200 and b'<html' in body.lower(), 'FRONTEND_HTTP_FAILED')
        result['frontend_http'] = 'PASS'

        if environment == 'staging':
            event_id, order_id = str(uuid.uuid4()), str(uuid.uuid4())
            binding = psql(db, "SELECT json_build_object('id',uber_store_id) FROM uber_eats_store_mappings WHERE environment='sandbox' AND enabled AND processing_mode='KITCHEN_MIRROR' ORDER BY id LIMIT 1;")
            raw = json.dumps({'event_id': event_id, 'event_type': 'orders.notification',
                'meta': {'user_id': binding['id'], 'resource_id': order_id}}, separators=(',', ':')).encode()
            signature = hmac.new(env['UBER_EATS_WEBHOOK_SIGNING_KEY'].encode(), raw, hashlib.sha256).hexdigest()
            signed = {'Content-Type': 'application/json', 'X-Environment': 'sandbox', 'X-Uber-Signature': signature}
            for label, override, expected in (
                    ('invalid_http_status', {'X-Uber-Signature': '0'*64}, 401),
                    ('environment_mismatch_http_status', {'X-Environment': 'foreign'}, 400),
                    ('valid_http_status', {}, 200), ('duplicate_http_status', {}, 200)):
                status, body = request(endpoint, raw=raw, headers={**signed, **override})
                ops.require(status == expected, 'WEBHOOK_HTTP_ACCEPTANCE_FAILED')
                if expected == 200:
                    ops.require(body == b'', 'WEBHOOK_ACK_BODY_NOT_EMPTY')
                result[label] = status
            result['event_id'] = event_id
            result['production_hmac'] = 'NOT_APPLICABLE'
            # Read-only TEST API evidence only. Never send these credentials to Production.
            form = urllib.parse.urlencode({'client_id': env['UBER_EATS_CLIENT_ID'],
                'client_secret': env['UBER_EATS_CLIENT_SECRET'], 'grant_type': 'client_credentials',
                'scope': env['UBER_EATS_SCOPES']}).encode()
            oauth_status, oauth_raw = request('https://sandbox-login.uber.com/oauth/v2/token',
                raw=form, headers={'Content-Type': 'application/x-www-form-urlencoded'})
            result['testing_oauth_http_status'] = oauth_status
            result['testing_oauth'] = 'BLOCKED'
            result['testing_get_order'] = 'NOT_VERIFIED'
            if oauth_status == 200:
                oauth = json.loads(oauth_raw)
                granted = oauth.get('scope')
                ops.require(granted is None or isinstance(granted, str)
                            and 'eats.store.orders.read' in granted.split(), 'TESTING_GRANTED_SCOPE_INVALID')
                ops.require(isinstance(oauth.get('access_token'), str)
                            and bool(oauth['access_token'].strip()), 'TESTING_TOKEN_INVALID')
                result['testing_oauth'] = 'PASS'
                result['testing_granted_scopes'] = granted.split() if granted else 'NOT_RETURNED'
                known = psql(db, "SELECT json_build_object('id',uber_order_id,'store',store_id) FROM uber_eats_orders WHERE environment='sandbox' ORDER BY id DESC LIMIT 1;")
                known_id = str(uuid.UUID(known['id']))
                status, source = request('https://test-api.uber.com/v2/eats/order/' + known_id,
                    headers={'Authorization': 'Bearer ' + oauth['access_token']})
                result['testing_get_order_http_status'] = status
                if status == 200:
                    data = json.loads(source)
                    ops.require(data.get('id') == known_id and data.get('store', {}).get('id') == binding['id'], 'TEST_ORDER_IDENTITY_MISMATCH')
                    result['testing_get_order'] = 'PASS'
                    status, preview = request(base + '/api/v1/stores/' + str(known['store'])
                        + '/integrations/uber-eats/mapping-preview', raw=source,
                        headers={**headers, 'Content-Type': 'application/json'})
                    ops.require(status == 200, 'TEST_MAPPING_PREVIEW_HTTP_FAILED')
                    mapped = json.loads(preview)['data']
                    result['mapping_preview_errors'] = len(mapped.get('errors', []))
                    result['mapping_preview_warnings'] = len(mapped.get('warnings', []))
                    result['mapping_preview'] = 'PASS' if not mapped.get('errors') and not mapped.get('warnings') else 'MAPPING_REQUIRED'
        else:
            # No unsigned bypass or valid-key claim; actual PROD key requires separate verification.
            ops.require(env.get('UBER_EATS_ENABLED') == 'false', 'PRODUCTION_EXPECTED_DISABLED')
            status, _ = request(endpoint, raw=b'{}', headers={'Content-Type': 'application/json'})
            ops.require(status == 503, 'DISABLED_WEBHOOK_NOT_503')
            result['disabled_post_http_status'] = status
            result['production_hmac'] = 'EXTERNALLY_BLOCKED_SIGNING_KEY_NOT_CONFIGURED'

        after = ops.inventory(db)
        for key in ('uber_orders', 'local_uber_orders', 'uber_print_jobs', 'uber_bindings'):
            ops.require(after[key] == before[key], 'BUSINESS_SIDE_EFFECT')
        result.update({'business_order_delta': 0, 'uber_print_job_delta': 0, 'accepted': True})
        ops.write_private(root / 'http-proof.json', result)
        return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('environment', choices=('staging', 'production'))
    parser.add_argument('image')
    parser.add_argument('sha')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        result = verify(args.environment, args.image, args.sha)
    except BaseException as error:
        print('UBER_RECEIVE_ONLY_VERIFICATION_FAILED:' +
              (str(error) if isinstance(error, ops.PromotionError) else type(error).__name__))
        return 1
    print(json.dumps(result))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
