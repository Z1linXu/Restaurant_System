import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('headers', Path(__file__).with_name('deploy.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class HeaderReleaseTest(unittest.TestCase):
    def test_bind_order_is_nonsemantic_but_content_and_destination_are_protected(self):
        binds = ['/source/a:/target/a:ro', '/source/b:/target/b:ro']
        self.assertEqual(release.normalized_binds(binds), release.normalized_binds(binds[::-1]))
        for changed in [['/other:/target/a:ro', binds[1]],
                        ['/source/a:/target/a:rw', binds[1]], [binds[0]],
                        [binds[0], '/source/b:/other:ro']]:
            self.assertNotEqual(release.normalized_binds(binds), release.normalized_binds(changed))
        for ambiguous in [[binds[0], binds[0]], [binds[0], '/other:/target/a:ro'], ['invalid']]:
            with self.assertRaises(AssertionError): release.normalized_binds(ambiguous)

    def test_retry_root_is_fixed_production_only(self):
        sha = 'a' * 40
        original = release.evidence_root('production', sha)
        self.assertEqual(original / 'retry-1', release.evidence_root('production', sha, 'retry-1'))
        for environment, attempt in [('staging', 'retry-1'), ('production', '../retry-1'),
                                     ('production', 'retry-2'), ('production', '/tmp/retry-1')]:
            with self.assertRaises(AssertionError): release.evidence_root(environment, sha, attempt)

    def test_retry_retains_exact_v39_and_original_data_guards(self):
        import copy
        old = '38|V38__add_store_remove_catalog.sql|99|t'
        new = old + '\n39|V39__add_dining_table_split_label_mode.sql|1431202837|t'
        original = dict(ledger=old, protected={'db': 'same'}, configuration={'env': 'digest'},
                        history={'cutoffs': [1, 2]}, order_contexts='same')
        current = dict(original, ledger=new)
        release.validate_retry_baseline(original, current)
        release.validate_release_ledger(new, new, old)
        for key in ('protected', 'configuration', 'history', 'order_contexts'):
            changed = copy.deepcopy(current)
            changed[key] = 'changed'
            with self.assertRaises(AssertionError): release.validate_retry_baseline(original, changed)
        for ledger in [old, new.replace('1431202837', '42'), new + '\n40|other.sql|1|t']:
            with self.assertRaises(AssertionError): release.validate_release_ledger(new, ledger, old)

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
        queues = dict(flyway='38', pending_print_jobs=0, pending_outbox=0,
                      pending_uber_events=0, due_uber_orders=0, uber_orders=39, local_uber_orders=39)
        with patch.object(release.ops, 'inventory', return_value=queues), patch.object(
            release.ops, 'current_4483_configuration', return_value={'fresh_printing': 0, 'flyway_schema_history': 'ledger'}):
            with patch.object(release, 'read_sql', return_value='old-table-digest'):
                self.assertEqual({'fresh_printing': 0, 'dining_tables_legacy_columns': 'old-table-digest'}, release.queue_guard('cloud-db-1'))
            for key in ('pending_print_jobs', 'pending_outbox', 'pending_uber_events', 'due_uber_orders'):
                queues[key] = 1
                with self.assertRaises(AssertionError): release.queue_guard('cloud-db-1')
                queues[key] = 0

    def test_only_exact_v39_append_preserves_all_old_migrations(self):
        import zlib
        checksum = 0
        migration = release.HERE.parents[2] / 'backend/src/main/resources/db/migration/V39__add_dining_table_split_label_mode.sql'
        for line in migration.read_text().splitlines(): checksum = zlib.crc32(line.encode(), checksum)
        if checksum >= 2**31: checksum -= 2**32
        old = '1|V1__baseline_current_schema.sql|42|t\n38|V38__add_store_remove_catalog.sql|99|t\n'
        added = f'39|V39__add_dining_table_split_label_mode.sql|{checksum}|t\n'
        release.validate_migration(old, old + added)
        for changed in [old, old.replace('|42|', '|43|') + added,
                        old + added.replace('|t', '|f'), old + added + '40|other.sql|1|t\n',
                        old + added.replace(str(checksum), str(checksum + 1))]:
            with self.assertRaises(AssertionError): release.validate_migration(old, changed)

    def test_existing_tables_must_default_left_right_including_every_store(self):
        import json
        state = {'rows': 39, 'left_right': 39, 'stores': [{'id': 2, 'code': 'CHINATOWN', 'tables': 24, 'default_mode_tables': 24}]}
        with patch.object(release, 'read_sql', return_value=json.dumps(state)):
            self.assertEqual(state, release.table_modes('cloud-db-1'))
        state['left_right'] = 38
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
