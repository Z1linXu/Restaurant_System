import copy
import datetime as dt
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

BASE = Path(__file__).resolve().parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, BASE / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


PLAN = load('plan')
COLLECT = load('collect_metadata')
LOGGING = load('validate_logging')
NOW = dt.datetime(2026, 11, 4, tzinfo=dt.timezone.utc)


def cache(cid='cache-old', days=31, **changes):
    record = {'ID': cid, 'Size': 100, 'InUse': False, 'Shared': False,
              'Type': 'regular', 'LastUsedAt': (NOW - dt.timedelta(days=days)).isoformat()}
    record.update(changes)
    return record


def snapshot():
    return {'schema_version': 1, 'observed_utc': NOW.isoformat(),
            'docker_socket': 'unix:///var/run/docker.sock', 'containers_complete': True,
            'operation_gate': {'complete': True, 'active': []},
            'containers': [{'id': 'container', 'name': '/prod', 'image_id': 'image-current', 'running': True,
                            'mount_sources': ['/srv/release/current/config']},
                           {'id': 'stopped', 'name': '/paused-test', 'image_id': 'image-paused', 'running': False}],
            'images': [{'Id': item, 'Size': 1000, 'SharedSize': 500}
                       for item in ['image-current', 'image-paused', 'image-rollback', 'image-unknown']],
            'cache': [cache()],
            'volumes': [{'Name': 'paused-db', 'UsageData': {'Size': 1024, 'RefCount': 0}}],
            'releases': [{'path': '/srv/release/current', 'sha': 'a' * 40, 'identity_verified': True,
                          'references_complete': False, 'contains_windows_package': True,
                          'data_paths': [{'path': '/srv/release/current/package/database/a.dump'}]}]}


class PlanTests(unittest.TestCase):
    def test_running_paused_rollback_and_unknown_images_all_retained(self):
        result = PLAN.review(snapshot(), {'rollback_image_ids': ['image-rollback']})
        rows = {row['id']: row for row in result['images']}
        self.assertEqual(rows['image-current']['reasons'], ['RUNNING_CONTAINER:/prod'])
        self.assertEqual(rows['image-paused']['reasons'], ['STOPPED_CONTAINER_RECOVERY:/paused-test'])
        self.assertEqual(rows['image-rollback']['reasons'], ['EXPLICIT_ROLLBACK_IMAGE'])
        self.assertEqual(rows['image-unknown']['status'], 'RETAINED')
        self.assertEqual(result['image_candidate_ids'], [])
        self.assertEqual(result['volumes'][0]['status'], 'RETAINED')
        self.assertEqual(result['containers'][1]['id'], 'stopped')

    def test_cache_age_references_types_and_future_metadata(self):
        value = snapshot()
        value['cache'] = [cache('old'), cache('boundary', 30), cache('shared', Shared=True),
                          cache('used', InUse=True), cache('internal', Type='internal'),
                          cache('frontend', Type='frontend'), cache('missing', LastUsedAt=None),
                          cache('future', -1), cache('string-bool', InUse='false'),
                          cache('no-type', Type=None)]
        result = PLAN.review(value, {})
        self.assertEqual(result['cache_candidate_ids'], ['old'])
        self.assertEqual(result['cache_candidate_logical_bytes_upper_bound'], 100)
        self.assertFalse(result['execute_supported'])
        self.assertFalse(result['automatic_cleanup_enabled'])

    def test_nanos_and_timestamp_without_zone(self):
        value = snapshot()
        value['cache'] = [cache('nanos', LastUsedAt='2026-09-01T00:00:00.123456789Z'),
                          cache('no-zone', LastUsedAt='2026-09-01T00:00:00')]
        self.assertEqual(PLAN.review(value, {})['cache_candidate_ids'], ['nanos'])

    def test_active_or_unknown_operation_gate_blocks_cache_candidates(self):
        for gate in ({}, {'complete': False, 'active': []}, {'complete': True, 'active': ['DEPLOY']}):
            value = snapshot()
            value['operation_gate'] = gate
            self.assertEqual(PLAN.review(value, {})['cache_candidate_ids'], [])

    def test_incomplete_or_inconsistent_inventory_fails_closed(self):
        for change in ['containers', 'rollback', 'duplicate-cache', 'cache-size', 'remote']:
            value, policy = snapshot(), {}
            if change == 'containers': value['containers_complete'] = False
            if change == 'rollback': policy['rollback_image_ids'] = ['not-present']
            if change == 'duplicate-cache': value['cache'].append(cache())
            if change == 'cache-size': value['cache'][0]['Size'] = '100'
            if change == 'remote': value['docker_socket'] = 'tcp://remote:2375'
            with self.assertRaises(ValueError): PLAN.review(value, policy)

    def test_data_opaque_and_container_release_references_preserved(self):
        result = PLAN.review(snapshot(), {})
        row = result['releases'][0]
        self.assertEqual(result['release_candidate_paths'], [])
        self.assertIn('CONTAINER_CONFIG_MOUNT_OR_WORKDIR_REFERENCE', row['reasons'])
        self.assertIn('DATA_BEARING_OR_WINDOWS_DISTRIBUTION', row['reasons'])
        self.assertIn('OPAQUE_OR_INCOMPLETE_RECOVERY_REFERENCES', row['reasons'])
        self.assertNotIn('total_reclaim_bytes', result)

    def test_execute_flag_cannot_reach_any_mutation(self):
        result = subprocess.run([sys.executable, str(BASE / 'plan.py'), '--execute'], capture_output=True)
        self.assertNotEqual(result.returncode, 0)

    def test_planner_does_not_change_input_or_read_data_paths(self):
        value = snapshot()
        before = copy.deepcopy(value)
        value['releases'][0]['data_paths'][0]['path'] = '/a/business/file/that/does/not/exist.dump'
        before = copy.deepcopy(value)
        PLAN.review(value, {})
        self.assertEqual(value, before)

    def test_container_internal_root_is_not_a_host_release_reference(self):
        value = snapshot()
        value['containers'][0]['working_dir'] = '/'
        value['containers'][0]['mount_sources'] = []
        row = PLAN.review(value, {})['releases'][0]
        self.assertNotIn('CONTAINER_CONFIG_MOUNT_OR_WORKDIR_REFERENCE', row['reasons'])
        self.assertEqual(row['status'], 'RETAINED')

    def test_captured_current_metadata_has_conservative_exact_inventory(self):
        path = BASE.parents[1] / 'docs/evidence/production-resource-optimization-20261103/disk-metadata.json'
        if not path.exists(): self.skipTest('optional read-only acceptance snapshot absent')
        value = json.loads(path.read_text())
        policy = json.loads((BASE / 'policy.example.json').read_text())
        result = PLAN.review(value, policy)
        self.assertEqual([len(result[k]) for k in ['containers', 'images', 'cache', 'releases', 'volumes']], [12, 141, 207, 87, 3])
        self.assertEqual(len(result['cache_candidate_ids']), 11)
        self.assertEqual(result['cache_candidate_logical_bytes_upper_bound'], 770115120)
        self.assertEqual(result['image_candidate_ids'], [])
        self.assertEqual(result['release_candidate_paths'], [])


class CollectorTests(unittest.TestCase):
    def test_symlink_component_is_never_followed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp).resolve()
            (root / 'real').mkdir()
            (root / 'link').symlink_to(root / 'real', target_is_directory=True)
            with self.assertRaises(RuntimeError): COLLECT.identity(root / 'link')

    def test_collector_does_not_export_environment_or_build_descriptions(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = str(Path(tmp).resolve())
            cid = 'a' * 64
            logdir = Path(tmp) / 'containers' / cid
            logdir.mkdir(parents=True)
            (logdir / (cid + '-json.log')).write_text('BUSINESS-CONTENT-NOT-READ')
            def api(path):
                if path == '/version': return {'ApiVersion': '1.55'}
                if path.endswith('/system/df'):
                    return {'Images': [{'Id': 'image', 'Labels': {'SECRET': 'HIDDEN-LABEL'}}],
                            'BuildCache': [cache(Description='HIDDEN-BUILD-DESCRIPTION')]}
                if path.endswith('/info'): return {'DockerRootDir': tmp}
                if '/containers/json' in path: return [{'Id': cid}]
                return {'Name': '/prod', 'Image': 'image', 'State': {'Running': True, 'StartedAt': 'stamp'},
                        'Config': {'Env': ['SECRET=HIDDEN-ENV'], 'Labels': {'SECRET': 'HIDDEN-LABEL'}},
                        'HostConfig': {'LogConfig': {'Type': 'json-file', 'Config': {'max-size': '10m', 'secret': 'HIDDEN-OPTION'}}}}
            with patch.object(COLLECT, 'get', side_effect=api), patch.object(COLLECT, 'ROOTS', ()), \
                 patch.object(COLLECT, 'POLICY_FILES', ()), \
                 patch.object(COLLECT.subprocess, 'check_output', return_value=''):
                result = COLLECT.collect()
            encoded = json.dumps(result)
            for forbidden in ['HIDDEN-ENV', 'HIDDEN-LABEL', 'HIDDEN-BUILD', 'HIDDEN-OPTION', 'BUSINESS-CONTENT']:
                self.assertNotIn(forbidden, encoded)
            self.assertEqual(result['logs'][0]['files'][0]['logical_bytes'], len('BUSINESS-CONTENT-NOT-READ'))


class LoggingTests(unittest.TestCase):
    def test_pending_config_has_only_bounded_logging_settings(self):
        self.assertEqual(LOGGING.validate(BASE / 'pending')['logrotate_semantics'], 'PASS')

    def test_more_aggressive_journal_expiry_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for path in (BASE / 'pending').iterdir():
                (root / path.name).write_bytes(path.read_bytes())
            with (root / 'journald.conf').open('a') as stream: stream.write('SystemMaxUse=1M\n')
            with self.assertRaises(ValueError): LOGGING.validate(root)


if __name__ == '__main__':
    unittest.main()
