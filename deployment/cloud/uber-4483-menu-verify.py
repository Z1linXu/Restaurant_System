#!/usr/bin/env python3
"""Offline Store 1 menu identity audit. Never calls an API or writes a database.

A candidate file is always useful, but only an authenticated GET read-proof and
an unchanged complete stable-ID graph can make its API payloads verified.
Menu GET access does not prove order_webhook role, order access or activation.
The proof is provenance from the separately reviewed reader, not an Uber-signed
attestation. Protect that reader's original response/proof as one evidence pair.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import tempfile

STORE_ID = 1
STORE_UUID = '5a7dca5c-b7ec-57c8-a684-87d23e66e8d9'
CLIENT_ID = '6GaiQpZTcfcuTa150wr4RyztC33PV7LS'
MENU_URL = 'https://api.uber.com/v2/eats/stores/' + STORE_UUID + '/menus'
COLLECTIONS = ('menus', 'categories', 'items', 'modifier_groups')
READ_SCOPE = 'eats.store.orders.read'
OBSERVED_READ_SCOPES = {'eats.store', READ_SCOPE}


class VerificationError(ValueError):
    pass


def load_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise VerificationError('JSON_DUPLICATE_KEY')
            result[key] = value
        return result
    try:
        return json.loads(raw, object_pairs_hook=unique)
    except VerificationError:
        raise
    except Exception:
        raise VerificationError('JSON_INVALID') from None


def issue(output, code, path='', before=None, after=None, severity='BLOCK'):
    output.append({'code': code, 'severity': severity, 'path': path,
                   'baseline': before, 'production': after})


def canonical(value):
    # Object key order is presentation. Array order remains meaningful except ID sets.
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False)


def graph(menu, errors, source):
    if not isinstance(menu, dict) or set(menu) != set(COLLECTIONS) or any(not isinstance(menu[k], list) for k in COLLECTIONS):
        issue(errors, 'MENU_SCHEMA_INVALID', source)
        return None
    indexes = {}
    for collection in COLLECTIONS:
        index = {}
        for n, row in enumerate(menu[collection]):
            path = f'{source}/{collection}/{n}'
            if not isinstance(row, dict) or not isinstance(row.get('id'), str) or not row['id'].strip():
                issue(errors, 'ENTITY_ID_INVALID', path)
                continue
            if row['id'] in index:
                issue(errors, 'DUPLICATE_ID', path, row['id'])
            index[row['id']] = row
        indexes[collection] = index
    roots, placements, contexts = set(), set(), set()
    root_groups = {}
    root_availability = {}
    for mid, menu_row in indexes['menus'].items():
        references = menu_row.get('category_ids')
        if not isinstance(references, list) or any(not isinstance(v, str) for v in references):
            issue(errors, 'CATEGORY_REFERENCES_INVALID', f'{source}/menus/{mid}')
            continue
        if len(references) != len(set(references)):
            issue(errors, 'DUPLICATE_REFERENCE', f'{source}/menus/{mid}/category_ids')
        for cid in references:
            if cid not in indexes['categories']:
                issue(errors, 'MISSING_CATEGORY_REFERENCE', f'{source}/menus/{mid}', after=cid)
                continue
            entities = indexes['categories'][cid].get('entities')
            if not isinstance(entities, list):
                issue(errors, 'CATEGORY_ENTITIES_INVALID', f'{source}/categories/{cid}')
                continue
            seen = set()
            for entity in entities:
                rid = entity.get('id') if isinstance(entity, dict) else None
                if not isinstance(rid, str) or entity.get('type', 'ITEM') != 'ITEM' or rid not in indexes['items']:
                    issue(errors, 'MISSING_OR_UNSUPPORTED_ITEM_REFERENCE', f'{source}/categories/{cid}', after=rid)
                    continue
                if rid in seen:
                    issue(errors, 'DUPLICATE_REFERENCE', f'{source}/categories/{cid}', after=rid)
                seen.add(rid); roots.add(rid); placements.add((mid, cid, rid))
                root_availability.setdefault(rid, set()).add(canonical({
                    'service_availability': menu_row.get('service_availability', []),
                    'invisible': menu_row.get('invisible', False)}))
    item_rules = {}
    for iid, item in indexes['items'].items():
        group_data = item.get('modifier_group_ids') or {}
        gids = group_data.get('ids', []) if isinstance(group_data, dict) else False
        if gids is None: gids = []  # Uber returns ids:null for no selectable groups.
        if not isinstance(gids, list) or any(not isinstance(g, str) for g in gids):
            issue(errors, 'GROUP_REFERENCES_INVALID', f'{source}/items/{iid}')
            gids = []
        if len(gids) != len(set(gids)):
            issue(errors, 'DUPLICATE_REFERENCE', f'{source}/items/{iid}/modifier_group_ids')
        for gid in gids:
            if gid not in indexes['modifier_groups']:
                issue(errors, 'MISSING_GROUP_REFERENCE', f'{source}/items/{iid}', after=gid)
        item_rules[iid] = {
            'group_ids': sorted(gids), 'group_overrides': group_data.get('overrides', []) if isinstance(group_data, dict) else [],
            'quantity': item.get('quantity_info') or {},
            'availability': {key: item.get(key) for key in ('suspension_info', 'selling_info', 'availability_info') if item.get(key) is not None},
        }
        if iid in roots:
            root_groups[iid] = sorted(gids)
    group_rules = {}
    for gid, group in indexes['modifier_groups'].items():
        options = group.get('modifier_options')
        if not isinstance(options, list):
            issue(errors, 'MODIFIER_OPTIONS_INVALID', f'{source}/modifier_groups/{gid}')
            options = []
        ids = []
        for option in options:
            oid = option.get('id') if isinstance(option, dict) else None
            if not isinstance(oid, str) or option.get('type', 'ITEM') != 'ITEM' or oid not in indexes['items']:
                issue(errors, 'MISSING_OR_UNSUPPORTED_MODIFIER_REFERENCE', f'{source}/modifier_groups/{gid}', after=oid)
                continue
            ids.append(oid)
        if len(ids) != len(set(ids)):
            issue(errors, 'DUPLICATE_REFERENCE', f'{source}/modifier_groups/{gid}/modifier_options')
        group_rules[gid] = {'modifier_ids': sorted(ids), 'quantity': group.get('quantity_info') or {}}
    for rid, gids in root_groups.items():
        for gid in gids:
            if gid not in group_rules:
                continue
            for oid in group_rules[gid]['modifier_ids']:
                contexts.add((rid, rid, gid, oid))
                if item_rules[oid]['group_ids']:
                    issue(errors, 'UNSUPPORTED_NESTED_SELECTABLE_GROUP', f'{source}/root/{rid}/group/{gid}/modifier/{oid}', after=item_rules[oid]['group_ids'])
    return {'root_ids': sorted(roots), 'root_placements': sorted(placements),
            'contexts': sorted(contexts), 'item_rules': item_rules, 'group_rules': group_rules,
            'root_availability': {rid: sorted(values) for rid, values in root_availability.items()},
            'presentation': {k: {i: {field: row.get(field) for field in ('title', 'price_info', 'external_data')}
                                 for i, row in indexes[k].items()} for k in ('items', 'modifier_groups')}}


def compare_graph(before, after, differences):
    for key, code in (('root_ids', 'ROOT_IDS'), ('contexts', 'PARENT_CONTEXTS')):
        old, new = set(map(tuple, before[key])) if key == 'contexts' else set(before[key]), set(map(tuple, after[key])) if key == 'contexts' else set(after[key])
        for value in sorted(old - new): issue(differences, code + '_REMOVED', key, value)
        for value in sorted(new - old): issue(differences, code + '_ADDED', key, after=value)
    for section in ('item_rules', 'group_rules'):
        old, new = before[section], after[section]
        for identifier in sorted(set(old) | set(new)):
            if identifier not in old: issue(differences, section.upper() + '_ADDED', identifier, after=new[identifier])
            elif identifier not in new: issue(differences, section.upper() + '_REMOVED', identifier, old[identifier])
            elif canonical(old[identifier]) != canonical(new[identifier]):
                for field in sorted(set(old[identifier]) | set(new[identifier])):
                    if canonical(old[identifier].get(field)) != canonical(new[identifier].get(field)):
                        issue(differences, section.upper() + '_CHANGED', identifier + '/' + field, old[identifier].get(field), new[identifier].get(field))
    for rid in sorted(set(before['root_availability']) & set(after['root_availability'])):
        if before['root_availability'][rid] != after['root_availability'][rid]:
            issue(differences, 'ROOT_MENU_AVAILABILITY_CHANGED', rid,
                  before['root_availability'][rid], after['root_availability'][rid])
    if before['root_placements'] != after['root_placements']:
        issue(differences, 'PRESENTATION_OR_LAYOUT_CHANGED', 'root_placements',
              before['root_placements'], after['root_placements'], severity='INFO')
    for collection in ('items', 'modifier_groups'):
        old, new = before['presentation'][collection], after['presentation'][collection]
        for identifier in sorted(set(old) & set(new)):
            for field in ('title', 'external_data'):
                if canonical(old[identifier][field]) != canonical(new[identifier][field]):
                    issue(differences, 'IDENTITY_DRIFT_REVIEW_REQUIRED', collection + '/' + identifier + '/' + field,
                          old[identifier][field], new[identifier][field])
            if canonical(old[identifier]['price_info']) != canonical(new[identifier]['price_info']):
                issue(differences, 'PRICE_CHANGED', collection + '/' + identifier + '/price_info',
                      old[identifier]['price_info'], new[identifier]['price_info'], severity='INFO')


def proof_valid(proof, menu_raw, differences, now):
    expected = {'schema_version': 1, 'application_client_id': CLIENT_ID, 'environment': 'production',
                'uber_store_id': STORE_UUID, 'method': 'GET', 'url': MENU_URL,
                'http_status': 200, 'authorized_store_access': True}
    if not isinstance(proof, dict):
        issue(differences, 'PRODUCTION_READ_PROOF_REQUIRED'); return False
    valid = True
    for key, value in expected.items():
        if type(proof.get(key)) is not type(value) or proof.get(key) != value:
            issue(differences, 'PRODUCTION_READ_PROOF_INVALID', key); valid = False
    scopes = proof.get('oauth_granted_scopes')
    if not isinstance(scopes, list) or any(not isinstance(s, str) for s in scopes) or READ_SCOPE not in scopes:
        issue(differences, 'READ_SCOPES_NOT_PROVEN'); valid = False
    if menu_raw is None or proof.get('raw_menu_sha256') != hashlib.sha256(menu_raw).hexdigest():
        issue(differences, 'PRODUCTION_MENU_HASH_NOT_PROVEN'); valid = False
    try:
        fetched = proof['fetched_at_utc']
        if not isinstance(fetched, str) or not fetched.endswith('Z'): raise ValueError()
        age = (now - dt.datetime.fromisoformat(fetched.replace('Z', '+00:00'))).total_seconds()
        if not 0 <= age <= 86400: raise ValueError()
    except Exception:
        issue(differences, 'PRODUCTION_READ_PROOF_STALE_OR_UNDATED'); valid = False
    return valid


def rule_key(rule, candidate=False):
    if candidate:
        return (rule.get('sourceKind'), rule.get('sourceIdentifierType'), rule.get('sourceUberIdentifier'), rule.get('sourceUberParentRoot') or '')
    return (rule.get('kind'), rule.get('identifierType'), rule.get('uberIdentifier'), rule.get('rootID') or '')


def verify(baseline_raw, approved, candidates, production_raw=None, proof=None, now=None):
    now = now or dt.datetime.now(dt.timezone.utc)
    differences = []
    baseline = graph(load_json(baseline_raw), differences, 'baseline')
    if not isinstance(approved, dict) or approved.get('rawSourceSha256') != hashlib.sha256(baseline_raw).hexdigest():
        issue(differences, 'APPROVED_BASELINE_HASH_MISMATCH')
    approved_rules = approved.get('approvedRuleContexts', []) if isinstance(approved, dict) else []
    if not isinstance(approved_rules, list): approved_rules = []; issue(differences, 'APPROVED_RULES_INVALID')
    approvals = {}
    approved_contexts = set()
    approved_roots = set()
    for rule in approved_rules:
        if not isinstance(rule, dict): issue(differences, 'APPROVED_RULE_INVALID'); continue
        key = rule_key(rule)
        if key in approvals: issue(differences, 'DUPLICATE_APPROVED_RULE', after=key)
        approvals[key] = rule
        if rule.get('kind') == 'ITEM': approved_roots.add(rule.get('uberIdentifier'))
        elif rule.get('kind') in ('MODIFIER', 'REMOVED_MODIFIER'):
            approved_contexts.add((rule.get('rootID'), rule.get('immediateParentItemID'), rule.get('modifierGroupID'), rule.get('uberIdentifier')))
        else: issue(differences, 'APPROVED_RULE_KIND_INVALID')
    if baseline and (approved_roots != set(baseline['root_ids']) or approved_contexts != set(baseline['contexts'])):
        issue(differences, 'APPROVED_CONTEXT_COVERAGE_MISMATCH')
    selected = {}
    source_candidates = candidates.get('candidates', []) if isinstance(candidates, dict) else []
    if not isinstance(source_candidates, list): source_candidates = []; issue(differences, 'CANDIDATES_INVALID')
    for candidate in source_candidates:
        if not isinstance(candidate, dict) or type(candidate.get('targetStoreId')) is not int or candidate['targetStoreId'] != STORE_ID:
            continue
        key = rule_key(candidate, True)
        if key in selected: issue(differences, 'DUPLICATE_STORE_1_CANDIDATE', after=key)
        selected[key] = candidate
    if set(selected) != set(approvals): issue(differences, 'STORE_1_CANDIDATE_COVERAGE_MISMATCH')
    output_rules = []
    for key, candidate in sorted(selected.items(), key=lambda entry: str(entry[0])):
        approval = approvals.get(key)
        valid = (approval is not None and candidate.get('localIdentityStatus') == 'VERIFIED'
                 and candidate.get('localErrors') == [] and type(candidate.get('resolvedProductionMenuItemId')) is int
                 and candidate['resolvedProductionMenuItemId'] > 0)
        if approval:
            for field in ('localSku', 'localOptionCode', 'localOptionGroup', 'parentOptionCode', 'mappingAction', 'itemMappingMode'):
                if candidate.get(field) != approval.get(field, 'STANDARD' if field == 'itemMappingMode' else 'MAP' if field == 'mappingAction' else None): valid = False
            if candidate.get('mappingAction') == 'NO_OP' and candidate.get('actionReason') != 'INGREDIENT_NOT_USED': valid = False
        if candidate.get('mappingAction') == 'MAP' and candidate.get('localOptionGroup') in ('ADD_ON', 'REMOVE'):
            canonical_field = 'resolvedProductionStoreAddonId' if candidate['localOptionGroup'] == 'ADD_ON' else 'resolvedProductionStoreRemoveId'
            if type(candidate.get(canonical_field)) is not int or candidate[canonical_field] <= 0: valid = False
        if not valid: issue(differences, 'LOCAL_PRODUCTION_IDENTITY_OR_APPROVAL_MISMATCH', after=key)
        payload = {'kind': candidate.get('sourceKind'), 'identifierType': candidate.get('sourceIdentifierType'),
                   'uberIdentifier': candidate.get('sourceUberIdentifier'), 'uberItemId': candidate.get('sourceUberParentRoot') or '',
                   'localMenuItemId': candidate.get('resolvedProductionMenuItemId'), 'localOptionCode': candidate.get('localOptionCode'),
                   'localOptionGroup': candidate.get('localOptionGroup'), 'parentOptionCode': candidate.get('parentOptionCode'),
                   'mappingAction': candidate.get('mappingAction'), 'itemMappingMode': candidate.get('itemMappingMode'),
                   'actionReason': candidate.get('actionReason')}
        output_rules.append({'store_id': STORE_ID, 'human_confirmed': True, 'local_identity_verified': valid,
                             'context': {field: approval.get(field) for field in ('rootID', 'immediateParentItemID', 'modifierGroupID')} if approval else {},
                             'api_method': 'PUT', 'api_path': '/api/v1/stores/1/integrations/uber-eats/mappings', 'api_payload': payload,
                             'production_external_identity_verified': False, 'persistence_allowed': False})
    trusted = proof_valid(proof, production_raw, differences, now)
    if production_raw is not None:
        production = graph(load_json(production_raw), differences, 'production')
        if baseline and production: compare_graph(baseline, production, differences)
    else: issue(differences, 'PRODUCTION_MENU_REQUIRED')
    ready = trusted and baseline is not None and bool(output_rules) and not any(d['severity'] == 'BLOCK' for d in differences)
    for rule in output_rules:
        rule['production_external_identity_verified'] = ready
        rule['persistence_allowed'] = ready
    return {'schema_version': 1, 'target_store_id': STORE_ID, 'production_uber_store_id': STORE_UUID,
            'production_client_id': CLIENT_ID, 'menu_url': MENU_URL, 'candidate_only': not ready,
            'production_identity_verified': ready, 'ready_for_persistence': ready,
            'verification_scope': 'MENU_IDENTITY_AND_MAPPING_CANDIDATES_ONLY',
            'order_webhook_role_verified': False, 'production_activation_ready': False,
            'candidate_rule_count': len(output_rules), 'verified_rule_count': len(output_rules) if ready else 0,
            'root_count': len(baseline['root_ids']) if baseline else 0,
            'modifier_context_count': len(baseline['contexts']) if baseline else 0,
            'persisted_by_this_tool': 0, 'approved_rules_require_human_reconfirmation': False,
            'observed_read_scopes': sorted(OBSERVED_READ_SCOPES.intersection(proof.get('oauth_granted_scopes', []))) if isinstance(proof, dict) and isinstance(proof.get('oauth_granted_scopes'), list) and all(isinstance(s, str) for s in proof['oauth_granted_scopes']) else [],
            'differences': differences, 'rules': output_rules}


def private_write(path, value):
    path = Path(path)
    if path.is_symlink(): raise VerificationError('OUTPUT_SYMLINK_REFUSED')
    with tempfile.NamedTemporaryFile('w', encoding='utf-8', dir=path.parent, prefix='.menu-verify-', delete=False) as file:
        temp = Path(file.name)
        try:
            os.chmod(temp, 0o600)
            json.dump(value, file, ensure_ascii=False, indent=2); file.write('\n'); file.flush(); os.fsync(file.fileno())
            os.replace(temp, path)
        finally:
            if temp.exists(): temp.unlink()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', required=True)
    parser.add_argument('--approved-contexts', required=True)
    parser.add_argument('--candidates', required=True)
    parser.add_argument('--production-menu')
    parser.add_argument('--read-proof')
    parser.add_argument('--output', required=True)
    parser.add_argument('--diff-output', required=True)
    args = parser.parse_args()
    try:
        result = verify(Path(args.baseline).read_bytes(), load_json(Path(args.approved_contexts).read_bytes()),
                        load_json(Path(args.candidates).read_bytes()),
                        Path(args.production_menu).read_bytes() if args.production_menu else None,
                        load_json(Path(args.read_proof).read_bytes()) if args.read_proof else None)
        private_write(args.output, result)
        private_write(args.diff_output, {key: value for key, value in result.items() if key != 'rules'})
        print(json.dumps({key: result[key] for key in ('target_store_id', 'candidate_only', 'ready_for_persistence', 'candidate_rule_count', 'verified_rule_count', 'persisted_by_this_tool')}))
        return 0 if result['ready_for_persistence'] else 2
    except Exception as error:
        # Do not echo raw input/parser data, filesystem paths or credential-like values.
        print('UBER_MENU_VERIFICATION_FAILED:' + (str(error) if isinstance(error, VerificationError) else type(error).__name__))
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
