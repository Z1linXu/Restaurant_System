#!/usr/bin/env python3
"""Bounded Store 18 Staging / three verified Production Store reconciliation.

Uses existing short-lived numeric Owner identity verification. Production has
no available private password: never claims a password-login test. No orders,
Print Jobs, hardware, device, integration or queue mutation API is permitted.
"""
import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

p = load('remove_deploy', HERE / 'deploy.py')
smoke = load('pos_smoke', HERE.parent / 'production-v26-smoke.py')
http = load('existing_http', HERE.parent / 'verify-uber-receive-only-20261022.py')
BASELINE = ('cilantro','green_onion','beef','radish','noodle','less_noodle','bok_choy','broccoli','corn','mushroom','seaweed','carrot','cucumber','edamame','peanut','cabbage','meat','green_pepper')
os.umask(0o077)


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':')).encode()).hexdigest()


def safety(db):
    # Hashes only: no token, customer, endpoint or device secret crosses output.
    query = """SELECT json_build_object(
      'menu', (select md5(coalesce(string_agg((to_jsonb(t)-'store_remove_id'-'store_remove_store_id'-'updated_at')::text,'' order by id),'')) from menu_item_options t),
      'items', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from menu_items t),
      'pricing', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from store_pricing_policies t),
      'combo_groups', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from store_combo_groups t),
      'combo_components', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from store_combo_components t),
      'printers', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from printer_configs t),
      'assignments', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from printer_assignments t),
      'device_pairing', (select md5(coalesce(string_agg((to_jsonb(t)-'last_seen_at'-'last_heartbeat_at'-'updated_at'-'app_version')::text,'' order by id),'')) from store_devices t),
      'modes', (select md5(coalesce(string_agg(json_build_array(id,printing_mode,printing_enabled)::text,'' order by id),'')) from stores),
      'uber_mappings', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from uber_eats_store_mappings t),
      'uber_menu', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from uber_eats_menu_mappings t),
      'uber_events', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from uber_eats_events t),
      'uber_orders', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from uber_eats_orders t),
      'published_rules', (select md5(coalesce(string_agg(to_jsonb(t)::text,'' order by id),'')) from printing_display_rule_revisions t where status='PUBLISHED'))"""
    return json.loads(p.sql(db, query))


def main(environment, sha, apply=False):
    assert environment in ('staging', 'production')
    root = p.BASE / environment / ('pos-removes-' + sha)
    assert HERE == p.BASE / 'staging/releases' / sha / 'deployment/cloud/pos-production-removes'
    deployment = json.loads((root / 'result.json').read_text())
    assert deployment['source_sha'] == sha and deployment['health'] == 'PASS'
    project = 'cloud' if environment == 'production' else 'restaurant-pos-staging'
    db = project + '-db-1'
    base = 'https://' + ('pos' if environment == 'production' else 'staging-pos') + '.lanzhounoodlesmtl.com'
    targets = [1, 2, 3] if environment == 'production' else [18]
    backend = p.i.inspect(project + '-backend-1')
    assert p.revision(backend) == sha
    env = dict(v.split('=', 1) for v in backend['Config']['Env'])
    assert env['UBER_EATS_WORKER_ENABLED'] == 'false'
    user, role, role_code, _, org = smoke.numeric_identity(db)
    assert environment != 'production' or org == 1
    token = smoke.mint_token(env['JWT_SECRET'], user, role, targets[0], org, role_code)
    headers = {'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'}
    def call(path, body=None):
        if body is not None:
            assert path in ('/admin/menu/removes/baseline-reconcile', '/admin/printing/display-rules/validate', '/admin/printing/display-rules/preview'), 'Unapproved write endpoint'
            assert body['store_id'] in targets
        status, raw = http.request(base + '/api/v1' + path, headers=headers,
                                   raw=None if body is None else json.dumps(body).encode())
        assert status == 200, 'Acceptance HTTP status ' + str(status)
        envelope = json.loads(raw)
        assert envelope.get('success') is True, 'Acceptance API failure'
        return envelope['data']
    identity = call('/auth/me')
    assert identity is not None
    report = {'environment': environment, 'source_sha': sha, 'stores': [], 'password_login': 'NOT_TESTED_NO_PRODUCTION_PRIVATE_PASSWORD', 'apply': apply}
    before = safety(db)
    p.p.DB = db
    baseline = json.loads((root / 'baseline.private.json').read_text())
    for store in targets:
        context = call(f'/stores/{store}/context')
        assert context['id'] == store
        if environment == 'production':
            assert context['code'] == {1:'4483_R_SAINT_DENIS',2:'CHINATOWN',3:'ST_CATHERINE'}[store]
            assert context['organization_id'] == 1 and context['store_kind'] == 'BUSINESS'
        else:
            assert context['code'] == 'CHINATOWN' and context['store_kind'] == 'VALIDATION_FIXTURE'
        menu_before = call(f'/menu/catalog?store_id={store}')
        rules_before = call(f'/admin/printing/display-rules?store_id={store}')
        dry = call('/admin/menu/removes/baseline-reconcile', {'store_id': store, 'dry_run': True})
        assert dry['menu_item_eligibility_changes'] == 0
        p.i.write_json(root / f'store-{store}-dry-run.private.json', dry)
        result = dry
        if apply:
            result = call('/admin/menu/removes/baseline-reconcile', {'store_id': store, 'dry_run': False, 'expected_before_fingerprint': dry['before_fingerprint']})
            p.i.write_json(root / f'store-{store}-apply.private.json', result)
            assert result['menu_item_eligibility_changes'] == 0
            def unchanged_options(rows):
                return [{k:v for k,v in row.items() if k not in ('store_remove_id','store_remove_store_id')} for row in rows]
            assert unchanged_options(result['before']) == unchanged_options(result['after']), 'Original Remove selection changed'
            replay = call('/admin/menu/removes/baseline-reconcile', {'store_id': store, 'dry_run': True})
            assert replay['created_master_count'] == replay['linked_option_count'] == 0
            replay = call('/admin/menu/removes/baseline-reconcile', {'store_id': store, 'dry_run': False, 'expected_before_fingerprint': replay['before_fingerprint']})
            assert replay['created_master_count'] == replay['linked_option_count'] == replay['menu_item_eligibility_changes'] == 0
            p.i.write_json(root / f'store-{store}-replay.private.json', replay)
        catalog = call(f'/admin/menu/removes?store_id={store}')
        rules = call(f'/admin/printing/display-rules?store_id={store}')
        menu_after = call(f'/menu/catalog?store_id={store}')
        assert menu_before['categories'] == menu_after['categories'], 'Effective menu changed'
        assert menu_before['pricing_policy'] == menu_after['pricing_policy']
        assert menu_before['combo_configuration'] == menu_after['combo_configuration']
        assert rules_before['active_revision'] == rules['active_revision'], 'Published rules changed'
        if apply:
            semantics = [r['semantic_code'] for r in catalog['removes']]
            assert len(semantics) == len(set(semantics)), 'Duplicate shared semantic'
            assert all(semantics.count(code) == 1 for code in BASELINE), 'Baseline semantic missing'
            rows = rules['remove_aliases']
            assert len({r['code'] for r in rows}) == len(rows)
            assert all(sum(r['code'] == code for r in rows) == 1 for code in BASELINE)
        active = rules['active_revision']
        assert active is not None
        assert rules['remove_alias_conflicts'] == [], 'Published alias conflict requires review'
        assert rules['proposed_remove_aliases'] == active['content']['dictionaries']['MODIFIER_REMOVE'], 'Alias normalization requires reviewed draft/publish'
        validation = call('/admin/printing/display-rules/validate', {'store_id': store, 'content': active['content']})
        assert validation['valid'] is True
        preview = call('/admin/printing/display-rules/preview', {'store_id': store, 'content': active['content'],
            'item_sku': 'traditional_beef_noodle', 'item_name_zh': '传统牛肉面', 'modifier_remove_codes': ['remove_cilantro','remove_green_onion','remove_beef','remove_meat'], 'spiciness_zh': '不辣'})
        assert all(word in preview['grab_preview'] for word in ('走香','走葱','走牛','走肉')), 'Multiple Remove preview mismatch'
        call(f'/admin/printing?store_id={store}')
        report['stores'].append({'store_id':store, 'store_name':context['name'], 'store_code':context['code'], 'organization_id':context['organization_id'], 'shared_before':dry['existing_shared_count'],
            'shared_after':len(catalog['removes']), 'linked':result['linked_option_count'], 'conflicts':len(result['conflicts']),
            'legacy_unlinked':len(catalog.get('legacy_options', [])), 'eligibility_changes':0,
            'semantics':[{'code':r['code'],'semantic_code':r['semantic_code'],'name_zh':r['name_zh'],'item_count':len(r['item_ids'])} for r in catalog['removes']],
            'printing_rows':len(rules.get('remove_aliases', [])), 'active_rule_revision_id':active['id'], 'active_rule_revision_number':active['revision_number'],
            'rule_unchanged':True,'validate':'PASS','preview_multiple_prefix_remove':'PASS','menu_categories_sha256':digest(menu_after['categories'])})
        assert safety(db) == before, 'Menu/price/device/integration/published snapshot changed'
    assert p.p.history_fingerprints(baseline['history']['cutoffs']) == baseline['history'], 'Historical snapshot changed'
    p.queues(db)
    p.i.health(base)
    status, _ = http.request(base + '/')
    assert status == 200
    report.update({'accepted':True, 'health':'PASS', 'images':deployment['images'], 'safety_before':before, 'safety_after':safety(db), 'history_unchanged':True, 'real_orders_created':0,'physical_prints_triggered':0})
    p.i.write_json(root / ('acceptance.json' if apply else 'dry-run.json'), report)
    print(json.dumps(report, ensure_ascii=False), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('environment', choices=('staging','production'))
    parser.add_argument('sha')
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try:
        with p.ops.operation_locks():
            main(args.environment, args.sha, args.apply)
    except Exception as error:
        print('POS_REMOVE_ACCEPTANCE_FAILED:' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
