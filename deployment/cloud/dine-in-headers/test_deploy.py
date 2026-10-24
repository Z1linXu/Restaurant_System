import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('headers', Path(__file__).with_name('deploy.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
verify_spec = importlib.util.spec_from_file_location('table_verify', Path(__file__).with_name('verify.py'))
verify = importlib.util.module_from_spec(verify_spec)
verify_spec.loader.exec_module(verify)


class HeaderReleaseTest(unittest.TestCase):
    def test_actual_table_lists_must_match_active_rows_and_store(self):
        import copy
        expected = [dict(id=1, store_id=2, table_code='T1', table_name='1', table_config='split_supported', split_label_mode='INSIDE_OUTSIDE', is_active=True)]
        verify.validate_table_response(expected, expected, 2)
        for rows in [[], expected + expected, [dict(expected[0], is_active=False)],
                     [dict(expected[0], store_id=3)], [dict(expected[0], split_label_mode='LEFT_RIGHT')]]:
            with self.assertRaises(AssertionError): verify.validate_table_response(rows, expected, 2)
        legacy_null = [dict(expected[0], is_active=None)]
        verify.validate_table_response(legacy_null, legacy_null, 2)

    def test_bind_order_is_nonsemantic_but_content_and_destination_are_protected(self):
        binds = ['/source/a:/target/a:ro', '/source/b:/target/b:ro']
        self.assertEqual(release.normalized_binds(binds), release.normalized_binds(binds[::-1]))
        for changed in [['/other:/target/a:ro', binds[1]],
                        ['/source/a:/target/a:rw', binds[1]], [binds[0]],
                        [binds[0], '/source/b:/other:ro']]:
            self.assertNotEqual(release.normalized_binds(binds), release.normalized_binds(changed))
        for ambiguous in [[binds[0], binds[0]], [binds[0], '/other:/target/a:ro'], ['invalid']]:
            with self.assertRaises(AssertionError): release.normalized_binds(ambiguous)

    def test_evidence_paths_are_environment_and_sha_scoped(self):
        sha = 'a' * 40
        self.assertEqual(release.BASE / 'production' / (release.RELEASE_PREFIX + sha), release.evidence_root('production', sha))
        for environment, value in [('invalid', sha), ('production', '../bad'), ('staging', 'a' * 39)]:
            with self.assertRaises(AssertionError): release.evidence_root(environment, value)

    def test_only_application_images_and_labels_may_change(self):
        old = {'services': {'backend': {'image': 'old', 'environment': {'UBER_EATS_WORKER_ENABLED': 'true'}},
                            'nginx': {'image': 'frontend'}}}
        import copy
        target = copy.deepcopy(old)
        target['services']['backend'].update(image='new', labels={'org.opencontainers.image.revision': 'sha'})
        target['services']['nginx']['image'] = 'new-frontend'
        images = {'backend': 'new', 'nginx': 'new-frontend'}
        release.validate_delta(old, target, images)
        for mutate in (
            lambda x: x['services']['backend']['environment'].update(UBER_EATS_WORKER_ENABLED='false'),
            lambda x: x['services']['nginx'].update(image='changed'),
            lambda x: x['services']['backend'].update(networks={'other': {}}),
        ):
            changed = copy.deepcopy(target)
            mutate(changed)
            with self.assertRaises(AssertionError): release.validate_delta(old, changed, images)

    def test_quiet_window_allows_existing_uber_business_but_rejects_active_work(self):
        queues = dict(flyway='39', pending_print_jobs=0, pending_outbox=0,
                      pending_uber_events=0, due_uber_orders=0, uber_orders=39, local_uber_orders=39)
        with patch.object(release.ops, 'inventory', return_value=queues), patch.object(
            release.ops, 'current_4483_configuration', return_value={'fresh_printing': 0, 'flyway_schema_history': 'ledger'}):
            with patch.object(release, 'read_sql', return_value='old-table-digest'):
                self.assertEqual({'fresh_printing': 0, 'dining_tables': 'old-table-digest'}, release.queue_guard('cloud-db-1'))
            for key in ('pending_print_jobs', 'pending_outbox', 'pending_uber_events', 'due_uber_orders'):
                queues[key] = 1
                with self.assertRaises(AssertionError): release.queue_guard('cloud-db-1')
                queues[key] = 0

    def test_entire_v39_ledger_must_remain_unchanged(self):
        old = '1|V1__baseline_current_schema.sql|42|t\n39|V39__add_dining_table_split_label_mode.sql|1431202837|t'
        release.validate_ledger(old, old)
        for changed in [old.replace('|42|', '|43|'), old.replace('1431202837', '42'),
                        old + '\n40|other.sql|1|t', old.rsplit('\n', 1)[0]]:
            with self.assertRaises(AssertionError): release.validate_ledger(old, changed)
        with self.assertRaises(AssertionError): release.validate_ledger(old.replace('1431202837', '42'), old)

    def test_both_persisted_split_modes_are_accepted_without_mutation(self):
        import json
        state = {'rows': 39, 'left_right': 38, 'inside_outside': 1, 'stores': [{'id': 2, 'code': 'CHINATOWN', 'tables': 24, 'default_mode_tables': 24}]}
        with patch.object(release, 'read_sql', return_value=json.dumps(state)):
            self.assertEqual(state, release.table_modes('cloud-db-1'))
        state['left_right'] = 37
        with patch.object(release, 'read_sql', return_value=json.dumps(state)):
            with self.assertRaises(AssertionError): release.table_modes('cloud-db-1')

    def test_post_migration_guard_requires_v39(self):
        queues = dict(flyway='39', pending_print_jobs=0, pending_outbox=0,
                      pending_uber_events=0, due_uber_orders=0)
        with patch.object(release.ops, 'inventory', return_value=queues), patch.object(
            release, 'configuration', return_value={'fresh_printing': 0}):
            self.assertEqual({'fresh_printing': 0}, release.queue_guard('cloud-db-1', '39'))
            with self.assertRaises(AssertionError): release.queue_guard('cloud-db-1', '38')


if __name__ == '__main__': unittest.main()
