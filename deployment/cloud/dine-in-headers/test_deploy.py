import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('headers', Path(__file__).with_name('deploy.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class HeaderReleaseTest(unittest.TestCase):
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
            release.ops, 'current_4483_configuration', return_value={'fresh_printing': 0}):
            self.assertEqual({'fresh_printing': 0}, release.queue_guard('cloud-db-1'))
            for key in ('pending_print_jobs', 'pending_outbox', 'pending_uber_events', 'due_uber_orders'):
                queues[key] = 1
                with self.assertRaises(AssertionError): release.queue_guard('cloud-db-1')
                queues[key] = 0


if __name__ == '__main__': unittest.main()
