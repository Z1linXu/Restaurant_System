#!/usr/bin/env python3
"""Configure the existing Production receiver only; secret arrives over stdin.

Reuses the reviewed private Compose/lock/backup path, preserves the current
image and OAuth credentials, and never enables the import worker. No secret or
signature is written to public evidence, CLI arguments or subprocess output.
"""
import argparse
import copy
import datetime
import hashlib
import hmac
import json
import os
from pathlib import Path
import subprocess
import uuid

import importlib.util


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


ops = load('receiver_ops', 'promote-uber-receive-only-20261022.py')
verify = load('receiver_verify', 'verify-uber-receive-only-20261022.py')
BASE = 'https://pos.lanzhounoodlesmtl.com'
STORE = '5a7dca5c-b7ec-57c8-a684-87d23e66e8d9'


def receiver_env(original, key):
    ops.require(isinstance(key, str) and bool(key.strip()) and key == key.strip()
                and len(key) <= 4096 and '\n' not in key and '\r' not in key,
                'SIGNING_KEY_INPUT_INVALID')
    required = {'APP_ENVIRONMENT': 'production', 'UBER_EATS_ENVIRONMENT': 'production',
        'UBER_EATS_PROCESSING_MODE': 'KITCHEN_MIRROR', 'UBER_EATS_WORKER_ENABLED': 'false',
        'UBER_EATS_CLIENT_ID': ops.PROD_CLIENT_ID, 'UBER_EATS_SCOPES': ops.SCOPES,
        'UBER_EATS_STORE_CUTOVERS': '{}'}
    ops.require(all(original.get(k) == v for k, v in required.items()), 'RECEIVE_ONLY_GUARD')
    ops.require(not original.get('UBER_EATS_PROCESSING_NOT_BEFORE'), 'CUTOVER_MUST_BE_EMPTY')
    result = dict(original, UBER_EATS_ENABLED='true', UBER_EATS_WEBHOOK_SIGNING_KEY=key)
    ops.require({k for k in set(original) | set(result) if original.get(k) != result.get(k)}
                <= {'UBER_EATS_ENABLED', 'UBER_EATS_WEBHOOK_SIGNING_KEY'}, 'UNAPPROVED_ENV_DELTA')
    return result


def business_counts():
    return verify.psql('cloud-db-1', """SELECT json_build_object(
      'integration_orders',(SELECT count(*) FROM uber_eats_orders),
      'local_orders',(SELECT count(*) FROM orders WHERE external_source='UBER_EATS'),
      'kitchen_tasks',(SELECT count(*) FROM kitchen_tasks k JOIN orders o ON o.id=k.order_id WHERE o.external_source='UBER_EATS'),
      'production_tasks',(SELECT count(*) FROM production_tasks k JOIN orders o ON o.id=k.order_id WHERE o.external_source='UBER_EATS'),
      'print_jobs',(SELECT count(*) FROM print_jobs k JOIN orders o ON o.id=k.order_id WHERE o.external_source='UBER_EATS'),
      'bindings',(SELECT count(*) FROM uber_eats_store_mappings));""")


def accept(expected):
    before = business_counts()
    event, order = str(uuid.uuid4()), str(uuid.uuid4())
    raw = json.dumps({'event_id': event, 'event_type': 'orders.notification',
        'meta': {'user_id': STORE, 'resource_id': order}}, separators=(',', ':')).encode()
    signature = hmac.new(expected['UBER_EATS_WEBHOOK_SIGNING_KEY'].encode(), raw, hashlib.sha256).hexdigest()
    headers = {'Content-Type': 'application/json', 'X-Environment': 'production', 'X-Uber-Signature': signature}
    proof = {}
    for name, override, code in (
        ('invalid_signature', {'X-Uber-Signature': '0'*64}, 401),
        ('foreign_environment', {'X-Environment': 'sandbox'}, 400),
        ('valid_signature', {}, 200), ('duplicate', {}, 200)):
        status, body = verify.request(BASE+'/api/v1/integrations/uber-eats/webhook', raw=raw, headers={**headers, **override})
        ops.require(status == code and (code != 200 or body == b''), 'HMAC_ACCEPTANCE_FAILED')
        proof[name] = status
    held = verify.psql('cloud-db-1', "SELECT json_build_object('count',count(*),'held',count(*) FILTER(WHERE status='HELD_RECEIVE_ONLY')) FROM uber_eats_events WHERE environment='production' AND event_id='"+event+"';")
    ops.require(held == {'count': 1, 'held': 1}, 'DURABLE_RECEIPT_NOT_PROVEN')
    ops.require(business_counts() == before, 'UBER_BUSINESS_SIDE_EFFECT')
    # Existing reviewed POS smoke: GET-only; not a password login or order submission.
    identity = verify.smoke.numeric_identity('cloud-db-1')
    user, role, role_code, store, organization = identity
    token = verify.smoke.mint_token(expected['JWT_SECRET'], user, role, store, organization, role_code)
    paths = ['/api/v1/auth/me', '/api/v1/me/workspaces', f'/api/v1/menu/catalog?store_id={store}',
        f'/api/v1/frontdesk/dining-tables?store_id={store}', f'/api/v1/frontdesk/orders/history?store_id={store}&limit=1',
        f'/api/v1/admin/printing?store_id={store}', f'/api/v1/admin/printing/devices?store_id={store}']
    for path in paths:
        status, body = verify.request(BASE+path, headers={'Authorization': 'Bearer '+token})
        ops.require(status == 200 and json.loads(body).get('success') is True, 'POS_READ_SMOKE_FAILED')
    status, _ = verify.request(BASE+'/api/v1/stores/9223372036854775807/context', headers={'Authorization': 'Bearer '+token})
    ops.require(status == 403, 'STORE_ISOLATION_FAILED')
    proof.update({'synthetic_event_id': event, 'durable_rows': 1, 'event_status': 'HELD_RECEIVE_ONLY',
        'business_counts_unchanged': True, 'pos_get_checks': 7, 'store_isolation': 'PASS',
        'official_uber_delivery': 'NOT_VERIFIED'})
    return proof


def configure(image, sha, key):
    with ops.operation_locks():
        current = ops.inspect('cloud-backend-1')
        ops.require(current['Image'] == image and ops.inspect(image)['Config']['Labels']['org.opencontainers.image.revision'] == sha,
                    'EXACT_ARTIFACT_DRIFT')
        original_env = ops.env_of(current)
        expected = receiver_env(original_env, key)
        ops.require_production_secret_isolation(expected, ops.env_of(ops.inspect('restaurant-pos-staging-backend-1')))
        source = ops.private_file(ops.BASE/'production/current.compose.json')
        source_bytes = source.read_bytes()
        original = json.loads(source_bytes)
        target, rollback = ops.models(original, image, expected)
        root = source.parent/('uber-receiver-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
        root.mkdir(mode=0o700)
        baseline = {name: ops.inspect(name)['Id'] for name in ops.run(['docker','ps','--format','{{.Names}}']).decode().splitlines()}
        for name, value in [('original.private.json', original), ('target.private.json', target), ('rollback.private.json', rollback)]:
            ops.write_private(root/name, value)

        def compose(path, *args):
            return ops.run(['docker','compose','-p','cloud','-f',str(path),*args])

        before_model = json.loads(compose(source, 'config','--format','json'))
        ops.require(before_model['services']['backend']['environment'] == original_env, 'PRIVATE_MODEL_DRIFT')
        ops.require(before_model['services']['backend']['image'] == image, 'PRIVATE_IMAGE_DRIFT')
        ops.validate_delta(before_model, json.loads(compose(root/'target.private.json','config','--format','json')), image, expected)
        rollback_env = dict(original_env, UBER_EATS_ENABLED='false', UBER_EATS_WORKER_ENABLED='false')
        ops.validate_delta(before_model, json.loads(compose(root/'rollback.private.json','config','--format','json')), image, rollback_env)
        inventory = ops.inventory('cloud-db-1')
        ops.queue_guard(inventory, True)
        ops.write_private(root/'queue-before.json', inventory)
        backup = root/'database-before.dump'
        with backup.open('xb') as out:
            os.fchmod(out.fileno(),0o600)
            result = subprocess.run(['docker','exec','cloud-db-1','sh','-c','pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"'],stdout=out,stderr=subprocess.PIPE,timeout=180)
        ops.require(result.returncode == 0 and backup.stat().st_size > 0,'BACKUP_FAILED')
        with backup.open('rb') as data:
            ops.require(bool(ops.run(['docker','exec','-i','cloud-db-1','pg_restore','--list'],stdin=data)),'BACKUP_INVALID')
        ops.require(source.read_bytes() == source_bytes, 'PRIVATE_MODEL_CHANGED')
        ops.queue_guard(ops.inventory('cloud-db-1'), True)
        try:
            compose(root/'target.private.json','up','-d','--no-build','--pull','never','--no-deps','backend')
            ops.wait_healthy(BASE)
            after = ops.inspect('cloud-backend-1')
            ops.require(after['Image'] == image and ops.env_of(after) == expected, 'RUNTIME_ENVIRONMENT_DRIFT')
            for name, identity in baseline.items():
                if name != 'cloud-backend-1':
                    ops.require(ops.inspect(name)['Id'] == identity,'UNRELATED_CONTAINER_CHANGED')
            proof = accept(expected)
            ops.health('https://staging-pos.lanzhounoodlesmtl.com')
            ops.require(source.read_bytes() == source_bytes,'PRIVATE_MODEL_CHANGED')
            ops.write_private(source, target)
        except (Exception, SystemExit):
            compose(root/'rollback.private.json','up','-d','--no-build','--pull','never','--no-deps','backend')
            ops.wait_healthy(BASE)
            restored = ops.env_of(ops.inspect('cloud-backend-1'))
            ops.require(restored == rollback_env and ops.inspect('cloud-backend-1')['Image'] == image, 'SAFE_ROLLBACK_FAILED')
            ops.write_private(source, rollback)
            raise ops.PromotionError('RECEIVER_FAILED_SAFE_ROLLBACK_APPLIED') from None
        proof.update({'source_sha':sha, 'image':image, 'receiver_enabled':True, 'worker_enabled':False,
            'signing_key_runtime_match':True, 'secondary_key_configured':False, 'health':'PASS',
            'backup_sha256':hashlib.sha256(backup.read_bytes()).hexdigest(), 'evidence_root':str(root)})
        ops.write_private(root/'result.json',proof)
        return proof


def main():
    import sys
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('image')
    parser.add_argument('source_sha')
    args = parser.parse_args()
    os.umask(0o077)
    try:
        payload = json.loads(sys.stdin.readline())
        ops.require(isinstance(payload,dict) and set(payload) == {'primary_signing_key'},'SECRET_INPUT_INVALID')
        result = configure(args.image,args.source_sha,payload['primary_signing_key'])
    except (Exception, SystemExit) as error:
        print('RECEIVER_CONFIGURATION_FAILED:'+(str(error) if isinstance(error,ops.PromotionError) else type(error).__name__))
        return 1
    print(json.dumps(result))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
