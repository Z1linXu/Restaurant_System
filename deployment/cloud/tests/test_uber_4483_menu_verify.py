"""Offline graph/provenance tests; no Uber, network, database or printer use."""
import copy
import datetime as dt
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('uber_4483_menu_verify', Path(__file__).resolve().parents[1] / 'uber-4483-menu-verify.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
NOW = dt.datetime(2026, 10, 22, 16, tzinfo=dt.timezone.utc)


class MenuVerificationTest(unittest.TestCase):
    def setUp(self):
        self.menu = {
            'menus': [{'id': 'menu', 'category_ids': ['category'], 'service_availability': []}],
            'categories': [{'id': 'category', 'entities': [{'id': 'root', 'type': 'ITEM'}]}],
            'items': [
                {'id': 'root', 'modifier_group_ids': {'ids': ['group'], 'overrides': []}, 'quantity_info': {'quantity': {}}, 'suspension_info': {}},
                {'id': 'modifier', 'quantity_info': {'quantity': {'max_permitted': 1}}}],
            'modifier_groups': [{'id': 'group', 'quantity_info': {'quantity': {'min_permitted': 0, 'max_permitted': 1}}, 'modifier_options': [{'id': 'modifier', 'type': 'ITEM'}]}]}
        self.baseline = json.dumps(self.menu).encode()
        self.approved = {'rawSourceSha256': hashlib.sha256(self.baseline).hexdigest(), 'approvedRuleContexts': [
            {'kind': 'ITEM', 'identifierType': 'ID', 'uberIdentifier': 'root', 'mappingAction': 'MAP', 'itemMappingMode': 'STANDARD', 'localSku': 'noodle'},
            {'kind': 'MODIFIER', 'identifierType': 'ID', 'uberIdentifier': 'modifier', 'rootID': 'root', 'immediateParentItemID': 'root', 'modifierGroupID': 'group',
             'mappingAction': 'MAP', 'itemMappingMode': 'STANDARD', 'localSku': 'noodle', 'localOptionCode': 'fried_egg', 'localOptionGroup': 'ADD_ON', 'parentOptionCode': None}]}
        self.candidates = {'candidates': [
            {'targetStoreId': 1, 'sourceKind': r['kind'], 'sourceIdentifierType': 'ID', 'sourceUberIdentifier': r['uberIdentifier'],
             'sourceUberParentRoot': r.get('rootID', ''), 'localSku': 'noodle', 'resolvedProductionMenuItemId': 11,
             'localOptionCode': r.get('localOptionCode'), 'localOptionGroup': r.get('localOptionGroup'), 'parentOptionCode': None,
             'mappingAction': 'MAP', 'itemMappingMode': 'STANDARD', 'actionReason': 'HUMAN_CONFIRMED_IDENTITY',
             'resolvedProductionStoreAddonId': 31 if r['kind'] == 'MODIFIER' else None,
             'resolvedProductionStoreRemoveId': None, 'localIdentityStatus': 'VERIFIED', 'localErrors': []}
            for r in self.approved['approvedRuleContexts']]}

    def proof(self, raw):
        return {'schema_version': 1, 'application_client_id': module.CLIENT_ID, 'environment': 'production',
                'uber_store_id': module.STORE_UUID, 'method': 'GET', 'url': module.MENU_URL, 'http_status': 200,
                'authorized_store_access': True, 'oauth_granted_scopes': ['eats.store', 'eats.store.orders.read'],
                'raw_menu_sha256': hashlib.sha256(raw).hexdigest(), 'fetched_at_utc': '2026-10-22T16:00:00Z'}

    def run_menu(self, menu=None, proof_change=None):
        raw = json.dumps(self.menu if menu is None else menu, indent=4).encode()
        proof = self.proof(raw)
        if proof_change: proof.update(proof_change)
        return module.verify(self.baseline, self.approved, self.candidates, raw, proof, NOW)

    def assert_blocked(self, result, code=None):
        self.assertFalse(result['ready_for_persistence'])
        self.assertTrue(result['candidate_only'])
        self.assertEqual(result['verified_rule_count'], 0)
        self.assertEqual(result['persisted_by_this_tool'], 0)
        self.assertTrue(all(not r['persistence_allowed'] for r in result['rules']))
        if code: self.assertIn(code, [d['code'] for d in result['differences']])

    def test_identical_graph_different_json_format_uses_formal_production_ids(self):
        result = self.run_menu()
        self.assertTrue(result['ready_for_persistence'])
        self.assertEqual(result['verified_rule_count'], 2)
        self.assertEqual(result['root_count'], 1)
        self.assertEqual(result['modifier_context_count'], 1)
        self.assertTrue(all(r['human_confirmed'] and r['persistence_allowed'] for r in result['rules']))
        self.assertTrue(all(r['api_payload']['localMenuItemId'] == 11 for r in result['rules']))
        self.assertTrue(all(r['api_path'] == '/api/v1/stores/1/integrations/uber-eats/mappings' for r in result['rules']))
        self.assertFalse(result['approved_rules_require_human_reconfirmation'])

    def test_menu_category_id_changes_and_missing_entity_type_are_layout_only(self):
        changed = copy.deepcopy(self.menu)
        changed['menus'][0]['id'] = 'different-menu'
        changed['menus'][0]['category_ids'] = ['different-category']
        changed['categories'][0]['id'] = 'different-category'
        del changed['categories'][0]['entities'][0]['type']
        result = self.run_menu(changed)
        self.assertTrue(result['ready_for_persistence'])
        self.assertTrue(all(d['severity'] == 'INFO' for d in result['differences']))

    def test_null_group_ids_on_nonselectable_item_mean_no_groups(self):
        changed = copy.deepcopy(self.menu)
        changed['items'][1]['modifier_group_ids'] = {'ids': None, 'overrides': []}
        self.assertTrue(self.run_menu(changed)['ready_for_persistence'])

    def test_root_availability_swap_cannot_hide_in_global_menu_multiset(self):
        first = self.menu['menus'][0]
        first['service_availability'] = [{'day_of_week': 'monday'}]
        self.menu['menus'].append({'id': 'second-menu', 'category_ids': ['second-category'],
                                   'service_availability': [{'day_of_week': 'tuesday'}], 'invisible': True})
        self.menu['categories'].append({'id': 'second-category', 'entities': [{'id': 'second-root'}]})
        other = copy.deepcopy(self.menu['items'][0]); other['id'] = 'second-root'
        self.menu['items'].append(other)
        new_approvals = copy.deepcopy(self.approved['approvedRuleContexts'])
        new_approvals[0]['uberIdentifier'] = 'second-root'
        new_approvals[1]['rootID'] = 'second-root'
        new_approvals[1]['immediateParentItemID'] = 'second-root'
        self.approved['approvedRuleContexts'].extend(new_approvals)
        new_candidates = copy.deepcopy(self.candidates['candidates'])
        new_candidates[0]['sourceUberIdentifier'] = 'second-root'
        new_candidates[1]['sourceUberParentRoot'] = 'second-root'
        self.candidates['candidates'].extend(new_candidates)
        self.baseline = json.dumps(self.menu).encode()
        self.approved['rawSourceSha256'] = hashlib.sha256(self.baseline).hexdigest()
        self.assertTrue(self.run_menu()['ready_for_persistence'])
        for swap in ('root-membership', 'availability'):
            with self.subTest(swap=swap):
                changed = copy.deepcopy(self.menu)
                if swap == 'root-membership':
                    changed['menus'][0]['category_ids'], changed['menus'][1]['category_ids'] = \
                        changed['menus'][1]['category_ids'], changed['menus'][0]['category_ids']
                else:
                    for field in ('service_availability', 'invisible'):
                        left = changed['menus'][0].get(field, False)
                        changed['menus'][0][field] = changed['menus'][1][field]
                        changed['menus'][1][field] = left
                self.assert_blocked(self.run_menu(changed), 'ROOT_MENU_AVAILABILITY_CHANGED')

    def test_stable_id_with_changed_item_modifier_or_group_identity_requires_review(self):
        for collection, index in (('items', 0), ('items', 1), ('modifier_groups', 0)):
            for field in ('title', 'external_data'):
                with self.subTest(collection=collection, index=index, field=field):
                    changed = copy.deepcopy(self.menu)
                    changed[collection][index][field] = {'translations': {'en': 'Different business identity'}} if field == 'title' else 'different-sku'
                    self.assert_blocked(self.run_menu(changed), 'IDENTITY_DRIFT_REVIEW_REQUIRED')

    def test_price_difference_is_reported_separately_without_guessing_new_identity(self):
        changed = copy.deepcopy(self.menu)
        changed['items'][0]['price_info'] = {'price': 2399}
        result = self.run_menu(changed)
        self.assertTrue(result['ready_for_persistence'])
        self.assertEqual([difference['code'] for difference in result['differences']], ['PRICE_CHANGED'])
        self.assertTrue(all(difference['severity'] == 'INFO' for difference in result['differences']))

    def test_root_id_changed_or_new_root_cannot_be_fuzzy_matched(self):
        for operation in ('change', 'add', 'remove'):
            with self.subTest(operation=operation):
                changed = copy.deepcopy(self.menu)
                if operation == 'change':
                    changed['items'][0]['id'] = 'similar-root'
                    changed['categories'][0]['entities'][0]['id'] = 'similar-root'
                elif operation == 'add':
                    changed['items'].append({'id': 'new-root'})
                    changed['categories'][0]['entities'].append({'id': 'new-root'})
                else: changed['categories'][0]['entities'] = []
                self.assert_blocked(self.run_menu(changed))

    def test_modifier_or_group_stable_id_change_blocks_parent_context(self):
        for changed_kind in ('modifier', 'group'):
            with self.subTest(changed_kind=changed_kind):
                changed = copy.deepcopy(self.menu)
                if changed_kind == 'modifier':
                    changed['items'][1]['id'] = 'new-modifier'
                    changed['modifier_groups'][0]['modifier_options'][0]['id'] = 'new-modifier'
                else:
                    changed['modifier_groups'][0]['id'] = 'new-group'
                    changed['items'][0]['modifier_group_ids']['ids'] = ['new-group']
                self.assert_blocked(self.run_menu(changed), 'PARENT_CONTEXTS_REMOVED')

    def test_quantity_group_limits_and_availability_have_explicit_blocking_diffs(self):
        for changed_kind in ('item-quantity', 'group-min', 'group-max', 'availability', 'menu-hidden'):
            with self.subTest(changed_kind=changed_kind):
                changed = copy.deepcopy(self.menu)
                if changed_kind == 'item-quantity': changed['items'][1]['quantity_info']['quantity']['max_permitted'] = 2
                elif changed_kind == 'group-min': changed['modifier_groups'][0]['quantity_info']['quantity']['min_permitted'] = 1
                elif changed_kind == 'group-max': changed['modifier_groups'][0]['quantity_info']['quantity']['max_permitted'] = 3
                elif changed_kind == 'availability': changed['items'][0]['suspension_info'] = {'suspension': {'suspend_until': 999999}}
                else: changed['menus'][0]['invisible'] = True
                result = self.run_menu(changed)
                self.assert_blocked(result)
                self.assertTrue(any(d['severity'] == 'BLOCK' and d['baseline'] != d['production'] for d in result['differences']))

    def test_missing_refs_duplicate_ids_and_duplicate_edges_are_rejected(self):
        for changed_kind in ('missing-item', 'missing-group', 'missing-category', 'duplicate-item', 'duplicate-group', 'duplicate-edge'):
            with self.subTest(changed_kind=changed_kind):
                changed = copy.deepcopy(self.menu)
                if changed_kind == 'missing-item': changed['modifier_groups'][0]['modifier_options'][0]['id'] = 'missing'
                elif changed_kind == 'missing-group': changed['items'][0]['modifier_group_ids']['ids'] = ['missing']
                elif changed_kind == 'missing-category': changed['menus'][0]['category_ids'] = ['missing']
                elif changed_kind == 'duplicate-item': changed['items'].append(copy.deepcopy(changed['items'][0]))
                elif changed_kind == 'duplicate-group': changed['modifier_groups'].append(copy.deepcopy(changed['modifier_groups'][0]))
                else: changed['modifier_groups'][0]['modifier_options'].append({'id': 'modifier', 'type': 'ITEM'})
                self.assert_blocked(self.run_menu(changed))

    def test_new_nested_selectable_group_is_explicitly_unsupported(self):
        changed = copy.deepcopy(self.menu)
        changed['items'][1]['modifier_group_ids'] = {'ids': ['child-group']}
        changed['items'].append({'id': 'child'})
        changed['modifier_groups'].append({'id': 'child-group', 'modifier_options': [{'id': 'child'}]})
        self.assert_blocked(self.run_menu(changed), 'UNSUPPORTED_NESTED_SELECTABLE_GROUP')

    def test_other_store_candidates_are_never_included_or_used(self):
        poison = copy.deepcopy(self.candidates['candidates'][0])
        poison.update(targetStoreId=3, resolvedProductionMenuItemId=999, localIdentityStatus='UNRESOLVED')
        self.candidates['candidates'].append(poison)
        result = self.run_menu()
        self.assertTrue(result['ready_for_persistence'])
        self.assertEqual(result['candidate_rule_count'], 2)
        self.assertTrue(all(r['store_id'] == 1 and r['api_payload']['localMenuItemId'] != 999 for r in result['rules']))

    def test_local_approval_drift_missing_canonical_id_and_duplicate_candidate_block(self):
        for mutation in ('sku', 'local-id', 'canonical-id', 'duplicate', 'missing'):
            with self.subTest(mutation=mutation):
                previous = copy.deepcopy(self.candidates)
                target = self.candidates['candidates'][1]
                if mutation == 'sku': target['localSku'] = 'guessed-noodle'
                elif mutation == 'local-id': target['resolvedProductionMenuItemId'] = None
                elif mutation == 'canonical-id': target['resolvedProductionStoreAddonId'] = None
                elif mutation == 'duplicate': self.candidates['candidates'].append(copy.deepcopy(target))
                else: self.candidates['candidates'].pop()
                self.assert_blocked(self.run_menu())
                self.candidates = previous

    def test_wrong_production_identity_role_scope_or_http_never_verifies(self):
        for change in ({'application_client_id': 'test-app'}, {'environment': 'sandbox'}, {'uber_store_id': 'other'},
                       {'method': 'POST'}, {'url': 'https://test-api.uber.com/menus'}, {'http_status': 403},
                       {'http_status': True}, {'authorized_store_access': False}, {'oauth_granted_scopes': ['eats.store']},
                       {'oauth_granted_scopes': 'eats.store.orders.read'}, {'schema_version': 2}):
            with self.subTest(change=change): self.assert_blocked(self.run_menu(proof_change=change))

    def test_only_order_read_scope_is_required_and_store_discovery_scope_is_observed(self):
        result = self.run_menu(proof_change={'oauth_granted_scopes': ['eats.store.orders.read']})
        self.assertTrue(result['ready_for_persistence'])
        self.assertEqual(result['observed_read_scopes'], ['eats.store.orders.read'])

    def test_menu_byte_hash_is_not_normalized_or_accepted_from_untrusted_proof(self):
        self.assert_blocked(self.run_menu(proof_change={'raw_menu_sha256': hashlib.sha256(self.baseline).hexdigest()}), 'PRODUCTION_MENU_HASH_NOT_PROVEN')
        for value in ('2026-10-20T16:00:00Z', '2026-10-23T16:00:00Z', '2026-10-22T16:00:00-04:00', ''):
            self.assert_blocked(self.run_menu(proof_change={'fetched_at_utc': value}), 'PRODUCTION_READ_PROOF_STALE_OR_UNDATED')

    def test_no_proof_or_no_production_menu_still_exports_candidates_with_zero_verified(self):
        result = module.verify(self.baseline, self.approved, self.candidates, now=NOW)
        self.assert_blocked(result, 'PRODUCTION_READ_PROOF_REQUIRED')
        self.assertEqual(result['candidate_rule_count'], 2)
        raw = json.dumps(self.menu).encode()
        result = module.verify(self.baseline, self.approved, self.candidates, raw, None, NOW)
        self.assert_blocked(result)

    def test_approved_baseline_hash_and_complete_context_coverage_are_required(self):
        self.approved['rawSourceSha256'] = '0' * 64
        self.assert_blocked(self.run_menu(), 'APPROVED_BASELINE_HASH_MISMATCH')
        self.approved['rawSourceSha256'] = hashlib.sha256(self.baseline).hexdigest()
        self.approved['approvedRuleContexts'].pop()
        self.assert_blocked(self.run_menu(), 'APPROVED_CONTEXT_COVERAGE_MISMATCH')

    def test_wrong_menu_schema_is_not_verified(self):
        for changed in ({'items': []}, dict(self.menu, extra=[]), dict(self.menu, items='invalid')):
            self.assert_blocked(self.run_menu(changed), 'MENU_SCHEMA_INVALID')

    def test_duplicate_json_keys_are_refused_without_echoing_values(self):
        with self.assertRaisesRegex(module.VerificationError, '^JSON_DUPLICATE_KEY$'):
            module.load_json(b'{"items":[],"items":["private-value"]}')

    def test_output_is_private_and_symlinks_are_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'manifest.json'
            module.private_write(output, self.run_menu())
            self.assertEqual(stat.S_IMODE(output.stat().st_mode), 0o600)
            link = Path(directory) / 'link.json'; link.symlink_to(output)
            with self.assertRaisesRegex(module.VerificationError, '^OUTPUT_SYMLINK_REFUSED$'):
                module.private_write(link, {})


if __name__ == '__main__':
    unittest.main()
