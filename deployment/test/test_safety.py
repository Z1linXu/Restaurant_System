"""Offline guard tests; compose config renders only synthetic dummy values."""
import copy
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, HERE / (name + '.py'))
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


remote = load('remote')
release = load('release')
SHA = 'a' * 40


class ComposeBoundaryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        images = json.loads((HERE / 'images.json').read_text())
        env = {k: os.environ[k] for k in ('PATH', 'HOME') if k in os.environ}
        env.update(TEST_IMAGE_SHA=SHA, TEST_DB_IMAGE=images['postgres'], TEST_NGINX_IMAGE=images['nginx'])
        for k in ('TEST_DB_PASSWORD', 'TEST_JWT_SECRET', 'TEMPLATE_TEST_OWNER_PASSWORD', 'TEMPLATE_TEST_MANAGER_PASSWORD'):
            env[k] = '0' * 64  # synthetic values only, never used to start anything
        p = subprocess.run(['docker', 'compose', '--project-name', remote.PROJECT, '--env-file', '/dev/null',
                            '--file', str(HERE / 'compose.yml'), 'config', '--format', 'json'],
                           env=env, capture_output=True, text=True)
        if p.returncode: raise RuntimeError('local compose config failed: ' + p.stderr)
        cls.model = json.loads(p.stdout)

    def test_rendered_configuration_passes_guards(self):
        remote.validate_compose(self.model, SHA, HERE)

    def test_real_compose_parser_keeps_each_tmpfs_mount_and_options_together(self):
        for name, size in [('backend', '64m'), ('frontend', '8m'), ('relay', '8m')]:
            with self.subTest(service=name):
                mounts = self.model['services'][name]['tmpfs']
                self.assertEqual(1, len(mounts), 'A YAML comma must not create an extra mount')
                target, options = mounts[0].split(':', 1)
                self.assertEqual('/tmp', target)
                self.assertEqual({'size': size, 'mode': '1777'}, dict(value.split('=', 1) for value in options.split(',')))

    def rejected(self, mutate):
        model = copy.deepcopy(self.model); mutate(model)
        with self.assertRaises(ValueError): remote.validate_compose(model, SHA)

    def test_split_yaml_tmpfs_mode_is_not_a_mount_path(self):
        self.rejected(lambda m: m['services']['backend'].update(tmpfs=['/tmp:size=64m', 'mode=1777']))

    def test_relative_tmpfs_mount_rejected(self):
        self.rejected(lambda m: m['services']['frontend'].update(tmpfs=['tmp:size=8m,mode=1777']))

    def test_unexpected_tmpfs_target_rejected(self):
        self.rejected(lambda m: m['services']['relay'].update(tmpfs=['/var/cache:size=8m,mode=1777']))

    def test_unexpected_tmpfs_size_rejected(self):
        self.rejected(lambda m: m['services']['backend'].update(tmpfs=['/tmp:size=8m,mode=1777']))

    def test_missing_tmpfs_mode_rejected(self):
        self.rejected(lambda m: m['services']['frontend'].update(tmpfs=['/tmp:size=8m']))

    def test_public_frontend_bind_rejected(self):
        self.rejected(lambda m: m['services']['frontend']['ports'][0].update(host_ip='0.0.0.0'))

    def test_backend_host_port_rejected(self):
        self.rejected(lambda m: m['services']['backend'].update(ports=[{'target': 8080, 'published': '8080'}]))

    def test_database_host_port_rejected(self):
        self.rejected(lambda m: m['services']['db'].update(ports=[{'target': 5432, 'published': '5432'}]))

    def test_database_url_boundary(self):
        self.rejected(lambda m: m['services']['backend']['environment'].update(SPRING_DATASOURCE_URL='jdbc:postgresql://other/db'))

    def test_network_egress_rejected(self):
        self.rejected(lambda m: m['networks']['test_internal'].update(internal=False))

    def test_real_compose_has_frontend_only_ingress_and_internal_backend(self):
        self.assertEqual({'test_internal', 'test_ingress'}, set(self.model['networks']))
        self.assertIs(False, self.model['networks']['test_ingress'].get('internal', False))
        self.assertEqual('bridge', self.model['networks']['test_ingress']['driver'])
        self.assertEqual(remote.PROJECT + '_ingress', self.model['networks']['test_ingress']['name'])
        self.assertEqual({'test_internal', 'test_ingress'}, set(self.model['services']['frontend']['networks']))
        self.assertEqual(1, self.model['services']['frontend']['networks']['test_ingress']['gw_priority'])
        self.assertFalse(self.model['services']['frontend'].get('environment'))
        for name in ('db', 'backend'):
            self.assertEqual({'test_internal'}, set(self.model['services'][name]['networks']))
            self.assertFalse(self.model['services'][name].get('ports'))

    def test_frontend_missing_ingress_rejected(self):
        self.rejected(lambda m: m['services']['frontend']['networks'].pop('test_ingress'))

    def test_frontend_foreign_network_join_rejected(self):
        self.rejected(lambda m: m['services']['frontend']['networks'].update(cloud_restaurant_pos={}))

    def test_backend_or_db_cannot_join_ingress(self):
        for name in ('backend', 'db'):
            with self.subTest(service=name):
                self.rejected(lambda m: m['services'][name]['networks'].update(test_ingress={}))

    def test_ingress_must_be_own_noninternal_bridge(self):
        for patch_value in [{'internal': True}, {'name': 'cloud_restaurant-pos'}, {'driver': 'host'},
                            {'external': True}, {'driver_opts': {'com.docker.network.bridge.name': 'docker0'}}]:
            with self.subTest(patch=patch_value):
                self.rejected(lambda m: m['networks']['test_ingress'].update(patch_value))

    def test_ingress_gateway_priority_required(self):
        self.rejected(lambda m: m['services']['frontend']['networks']['test_ingress'].update(gw_priority=0))

    def test_production_network_join_rejected(self):
        self.rejected(lambda m: m['services']['backend']['networks'].update(cloud_restaurant_pos={}))

    def test_shared_volume_rejected(self):
        self.rejected(lambda m: m['volumes']['test_postgres'].update(name='cloud_postgres'))

    def test_database_host_mount_rejected(self):
        self.rejected(lambda m: m['services']['db']['volumes'][0].update(type='bind', source='/production/data'))

    def test_backend_host_mount_rejected(self):
        self.rejected(lambda m: m['services']['backend'].update(volumes=[{'type': 'bind', 'source': '/production'}]))

    def test_host_privileges_rejected(self):
        self.rejected(lambda m: m['services']['backend'].update(privileged=True))

    def test_missing_read_only_rejected(self):
        self.rejected(lambda m: m['services']['frontend'].update(read_only=False))

    def test_unlimited_memory_rejected(self):
        self.rejected(lambda m: m['services']['backend'].update(mem_limit=0))

    def test_excess_memory_rejected(self):
        self.rejected(lambda m: m['services']['backend'].update(mem_limit=1024 ** 3))

    def test_non_test_profile_rejected(self):
        self.rejected(lambda m: m['services']['backend']['environment'].update(SPRING_PROFILES_ACTIVE='cloud'))

    def test_existing_application_image_rejected(self):
        self.rejected(lambda m: m['services']['backend'].update(image='restaurant-pos-backend:production'))

    def test_unexpected_service_rejected(self):
        self.rejected(lambda m: m['services'].update(production={}))


class ArtifactAndIngressTest(unittest.TestCase):
    def test_receipt_requires_exact_executed_checks_and_review(self):
        good = {'code_sha': SHA, 'checks': {k: 'PASS' for k in release.CHECKS}, 'independent_review': 'ACCEPT',
                'commands': ['executed test command'], 'jar_sha256': 'jar', 'dist_sha256': 'dist',
                'environment': 'ISOLATED_SYNTHETIC_MOCK'}
        release.receipt_ok(good, SHA, 'jar', 'dist')
        for key, value in [('code_sha', 'b' * 40), ('independent_review', 'PENDING'), ('commands', []),
                           ('jar_sha256', 'other'), ('environment', 'PRODUCTION')]:
            changed = copy.deepcopy(good); changed[key] = value
            with self.assertRaises(ValueError): release.receipt_ok(changed, SHA, 'jar', 'dist')
        changed = copy.deepcopy(good); changed['checks']['security'] = 'NOT_RUN'
        with self.assertRaises(ValueError): release.receipt_ok(changed, SHA, 'jar', 'dist')

    def test_candidate_is_test_only_and_blocks_broker(self):
        block = remote.edge_block(True).decode()
        self.assertEqual(2, block.count('server_name test.lanzhounoodlesmtl.com;'))
        self.assertIn('location ^~ /ws { return 403; }', block)
        self.assertIn('http://172.18.0.1:28480', block)
        self.assertNotIn('server_name pos.', block)
        self.assertNotIn('server_name staging-pos.', block)
        self.assertNotIn('backend:8080', block)

    def test_http_preparation_does_not_expose_default_app(self):
        self.assertIn('return 503', remote.edge_block(False).decode())
        self.assertNotIn('proxy_pass', remote.edge_block(False).decode())


def container(service, identity='test-container-id'):
    return {'Id': identity, 'Name': '/' + remote.PROJECT + '-' + service + '-1',
            'Config': {'Labels': {'com.docker.compose.project': remote.PROJECT,
                                 'com.docker.compose.service': service,
                                 'org.opencontainers.image.revision': SHA}},
            'State': {'Running': True, 'Restarting': False, 'Health': {'Status': 'healthy'}},
            'NetworkSettings': {'Networks': {'host': {}} if service == 'relay' else
                                {remote.PROJECT + '_internal': {'IPAddress': '172.30.0.4'}, remote.PROJECT + '_ingress': {'IPAddress': '172.31.0.4'}} if service == 'frontend' else
                                {remote.PROJECT + '_internal': {'IPAddress': '172.30.0.4'}},
                                'Ports': {'8080/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '28080'}]} if service == 'frontend' else {}}}


class ExistingResourceTest(unittest.TestCase):
    def test_network_requires_ownership_and_internal_bridge(self):
        good = {'Name': remote.PROJECT + '_internal', 'Driver': 'bridge', 'Scope': 'local', 'Internal': True,
                'Labels': {'com.docker.compose.project': remote.PROJECT, 'com.docker.compose.network': 'test_internal'}}
        remote.validate_network_document(good)
        for field, value in [('Internal', False), ('Driver', 'host'), ('Labels', {}), ('Name', 'cloud_restaurant-pos')]:
            changed = copy.deepcopy(good); changed[field] = value
            with self.assertRaises(ValueError): remote.validate_network_document(changed)

    def test_ingress_requires_exact_owner_noninternal_bridge(self):
        good = {'Name': remote.PROJECT + '_ingress', 'Driver': 'bridge', 'Scope': 'local', 'Internal': False,
                'Labels': {'com.docker.compose.project': remote.PROJECT, 'com.docker.compose.network': 'test_ingress'}}
        remote.validate_network_document(good, 'test_ingress')
        for field, value in [('Internal', True), ('Driver', 'host'), ('Labels', {}), ('Name', 'cloud_restaurant-pos'),
                             ('Options', {'com.docker.network.bridge.name': 'docker0'})]:
            changed = copy.deepcopy(good); changed[field] = value
            with self.assertRaises(ValueError): remote.validate_network_document(changed, 'test_ingress')

    def test_ingress_allows_only_exact_test_frontend_member(self):
        remote.validate_network_member(container('frontend'), 'test_ingress')
        for name in ('db', 'backend', 'relay'):
            with self.subTest(service=name), self.assertRaises(ValueError):
                remote.validate_network_member(container(name), 'test_ingress')
        foreign = container('frontend'); foreign['Config']['Labels']['com.docker.compose.project'] = 'cloud'
        with self.assertRaises(ValueError): remote.validate_network_member(foreign, 'test_ingress')
        replica = container('frontend'); replica['Name'] = '/' + remote.PROJECT + '-frontend-2'
        with self.assertRaises(ValueError): remote.validate_network_member(replica, 'test_ingress')

    def existing_networks(self, ingress_member):
        docs = {}
        for key, suffix, internal in [('test_internal', '_internal', True), ('test_ingress', '_ingress', False)]:
            docs[remote.PROJECT + suffix] = {'Name': remote.PROJECT + suffix, 'Driver': 'bridge', 'Scope': 'local',
                'Internal': internal, 'Labels': {'com.docker.compose.project': remote.PROJECT, 'com.docker.compose.network': key},
                'Containers': {key + '-member': {}}}
        def inspect(args):
            if args[:3] == ['docker', 'network', 'inspect']: return [docs[args[3]]]
            return [container('backend' if args[2] == 'test_internal-member' else ingress_member)]
        with patch.object(remote, 'run', side_effect=lambda args: ('\n'.join(docs).encode() if args[:3] == ['docker', 'network', 'ls'] else b'')), \
             patch.object(remote, 'jsonrun', side_effect=inspect) as calls:
            remote.verify_existing_resources()
            self.assertEqual(2, sum(call.args[0][:3] == ['docker', 'network', 'inspect'] for call in calls.call_args_list))

    def test_existing_resource_reentry_checks_both_test_networks(self):
        self.existing_networks('frontend')

    def test_existing_ingress_with_backend_member_rejected(self):
        with self.assertRaises(ValueError): self.existing_networks('backend')

    def test_volume_refuses_foreign_or_host_redirected_storage(self):
        good = {'Name': remote.PROJECT + '_postgres', 'Driver': 'local', 'Options': None,
                'Labels': {'com.docker.compose.project': remote.PROJECT, 'com.docker.compose.volume': 'test_postgres'}}
        remote.validate_volume_document(good)
        for field, value in [('Options', {'device': '/production/db'}), ('Driver', 'nfs'), ('Labels', {})]:
            changed = copy.deepcopy(good); changed[field] = value
            with self.assertRaises(ValueError): remote.validate_volume_document(changed)

    def test_foreign_network_member_rejected(self):
        with self.assertRaises(ValueError): remote.require_project_container({'Config': {'Labels': {'com.docker.compose.project': 'cloud'}}}, 'backend')

    def test_same_test_project_reentry_is_accepted(self):
        remote.require_project_container(container('backend'), 'backend')


class RelayHealthTest(unittest.TestCase):
    def test_unknown_listener_rejected(self):
        with self.assertRaises(ValueError):
            remote.validate_port_owners([{'port': 28480, 'ip': '172.18.0.1', 'pids': [7]}], {}, {})

    def test_relay_listener_cgroup_must_match_exact_container(self):
        row = {'port': 28480, 'ip': '172.18.0.1', 'pids': [7]}
        remote.validate_port_owners([row], {'relay': container('relay')}, {7: {'cgroup': '/docker/test-container-id', 'args': []}})
        with self.assertRaises(ValueError):
            remote.validate_port_owners([row], {'relay': container('relay')}, {7: {'cgroup': '/docker/another-container', 'args': []}})

    def test_frontend_listener_requires_exact_docker_mapping(self):
        row = {'port': 28080, 'ip': '127.0.0.1', 'pids': [8]}
        args = ['/usr/bin/docker-proxy', '-host-ip', '127.0.0.1', '-host-port', '28080', '-container-ip', '172.31.0.4', '-container-port', '8080']
        remote.validate_port_owners([row], {'frontend': container('frontend')}, {8: {'cgroup': '/', 'args': args}})
        changed = list(args); changed[-1] = '8081'
        with self.assertRaises(ValueError):
            remote.validate_port_owners([row], {'frontend': container('frontend')}, {8: {'cgroup': '/', 'args': changed}})
        wrong_network = list(args); wrong_network[wrong_network.index('-container-ip') + 1] = '172.30.0.4'
        with self.assertRaises(ValueError):
            remote.validate_port_owners([row], {'frontend': container('frontend')}, {8: {'cgroup': '/', 'args': wrong_network}})

    def test_public_binding_rejected_even_without_userland_proxy(self):
        frontend = container('frontend')
        frontend['NetworkSettings']['Ports']['8080/tcp'][0]['HostIp'] = '0.0.0.0'
        with patch.object(remote, 'listener_records', return_value=[]):
            with self.assertRaisesRegex(ValueError, 'localhost-only'): remote.verify_port_owners({'frontend': frontend})

    def test_actual_database_publication_rejected(self):
        database = container('db')
        database['NetworkSettings']['Ports'] = {'5432/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '5432'}]}
        with patch.object(remote, 'listener_records', return_value=[]):
            with self.assertRaisesRegex(ValueError, 'DB/backend'): remote.verify_port_owners({'db': database})

    def runtime(self, documents, marker=SHA):
        def request(args, data=None):
            self.assertEqual(args[:7], ['docker', 'exec', remote.EDGE, '/usr/bin/env', '-i', '/usr/bin/wget', '-q'])
            self.assertIn('Host: ' + remote.DOMAIN, args)
            if args[-1].endswith('/test-health'): return b'test-frontend-up\n'
            if args[-1].endswith('/test-code-sha'): return marker.encode()
            return b'{"data":{"status":"UP"}}'
        with patch.object(remote, 'test_containers', return_value=documents), \
             patch.object(remote, 'verify_port_owners', return_value=[{'port': 28480}]), \
             patch.object(remote, 'verify_existing_resources'), patch.object(remote, 'run', side_effect=request):
            return remote.verify_runtime(SHA)

    def test_relay_exited_blocks_success(self):
        documents = {s: container(s) for s in ('db', 'backend', 'frontend', 'relay')}
        documents['relay']['State']['Running'] = False
        with self.assertRaises(ValueError): self.runtime(documents)

    def test_wrong_test_marker_blocks_success(self):
        documents = {s: container(s) for s in ('db', 'backend', 'frontend', 'relay')}
        with self.assertRaises(ValueError): self.runtime(documents, marker='another-release')

    def test_full_allowlisted_relay_chain(self):
        documents = {s: container(s) for s in ('db', 'backend', 'frontend', 'relay')}
        self.assertEqual(4, len(self.runtime(documents)))

    def test_unhealthy_database_blocks_success(self):
        documents = {s: container(s) for s in ('db', 'backend', 'frontend', 'relay')}
        documents['db']['State']['Health']['Status'] = 'unhealthy'
        with self.assertRaises(ValueError): self.runtime(documents)

    def test_runtime_rejects_missing_extra_or_backend_ingress_network(self):
        for service, add in [('frontend', False), ('frontend', True), ('backend', True), ('db', True)]:
            documents = {s: container(s) for s in ('db', 'backend', 'frontend', 'relay')}
            if not add: documents['frontend']['NetworkSettings']['Networks'].pop(remote.PROJECT + '_ingress')
            else: documents[service]['NetworkSettings']['Networks']['cloud_restaurant-pos' if service == 'frontend' else remote.PROJECT + '_ingress'] = {}
            with self.subTest(service=service, add=add), self.assertRaisesRegex(ValueError, 'network boundary'):
                self.runtime(documents)


class EdgeCASTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name).resolve()
        self.source = self.root / 'source.conf'
        self.old = b'server { listen 80; server_name original.example; }\n'
        self.source.write_bytes(self.old)
        observed = self.source.stat(); self.identity = (observed.st_dev, observed.st_ino)
        self.effective = {'identity': (1, 2), 'data': self.old}
        locks = [self.root / 'production.lock', self.root / 'staging.lock']
        for p in locks: p.touch()
        self.stack = contextlib.ExitStack()
        for name, value in [('ROOT', self.root), ('EDGE_TEMPLATE', self.source), ('SHARED_LOCKS', locks)]:
            self.stack.enter_context(patch.object(remote, name, value))
        self.stack.enter_context(patch.object(remote, 'protected_snapshot', return_value={}))
        self.stack.enter_context(patch.object(remote, 'head_status', return_value=200))
        self.stack.enter_context(patch.object(remote, 'container_file_identity', side_effect=lambda p: self.identity if 'templates/' in p else (1, 2)))
        self.stack.enter_context(patch.object(remote, 'effective_state', side_effect=lambda: copy.deepcopy(self.effective)))
        self.stack.enter_context(patch.object(remote, 'validate_edge'))
        self.stack.enter_context(patch.object(remote, 'run', return_value=b''))
        self.stack.enter_context(patch.object(remote, 'write_effective', side_effect=self.write_effective))
        self.stack.enter_context(contextlib.redirect_stdout(io.StringIO()))

    def tearDown(self):
        self.stack.close(); self.temp.cleanup()

    def write_effective(self, data, expected):
        if expected != self.effective: raise ValueError('effective CAS failed')
        self.effective['data'] = data

    def apply(self):
        remote.apply_edge(False, remote.sha256(self.old))

    def test_source_drift_after_prevalidation_is_not_overwritten(self):
        with patch.object(remote, 'validate_edge', side_effect=lambda _: self.source.write_bytes(b'other publication')):
            with self.assertRaisesRegex(ValueError, 'after validation'): self.apply()
        self.assertEqual(b'other publication', self.source.read_bytes())
        self.assertEqual(self.old, self.effective['data'])

    def test_source_inode_replacement_after_prevalidation_is_not_overwritten(self):
        def replace(_):
            other = self.root / 'other.conf'; other.write_bytes(b'new inode'); os.replace(other, self.source)
        with patch.object(remote, 'validate_edge', side_effect=replace):
            with self.assertRaisesRegex(ValueError, 'inode changed'): self.apply()
        self.assertEqual(b'new inode', self.source.read_bytes())

    def test_effective_drift_after_prevalidation_is_not_overwritten(self):
        def change(_): self.effective['data'] = b'another effective publication'
        with patch.object(remote, 'validate_edge', side_effect=change):
            with self.assertRaisesRegex(ValueError, 'after validation'): self.apply()
        self.assertEqual(self.old, self.source.read_bytes())
        self.assertEqual(b'another effective publication', self.effective['data'])

    def test_shared_operations_lock_blocks_another_writer(self):
        with remote.SHARED_LOCKS[0].open('r+') as handle:
            remote.fcntl.flock(handle, remote.fcntl.LOCK_EX | remote.fcntl.LOCK_NB)
            with self.assertRaises(BlockingIOError): self.apply()
        self.assertEqual(self.old, self.source.read_bytes())

    def test_effective_failure_after_source_write_safely_restores_old(self):
        with patch.object(remote, 'write_effective', side_effect=RuntimeError('write failed before mutation')):
            with self.assertRaisesRegex(RuntimeError, 'write failed'): self.apply()
        self.assertEqual(self.old, self.source.read_bytes())
        self.assertEqual(self.old, self.effective['data'])

    def test_reload_failure_restores_both_only_while_owned(self):
        reloads = []
        def command(args, data=None):
            if args[-1] == 'reload':
                reloads.append(1)
                if len(reloads) == 1: raise RuntimeError('reload failed')
            return b''
        with patch.object(remote, 'run', side_effect=command):
            with self.assertRaisesRegex(RuntimeError, 'reload failed'): self.apply()
        self.assertEqual(2, len(reloads))
        self.assertEqual(self.old, self.source.read_bytes())
        self.assertEqual(self.old, self.effective['data'])

    def test_third_party_change_after_write_is_never_rolled_back(self):
        def command(args, data=None):
            if args[-1] == 'reload':
                self.source.write_bytes(b'third-party source')
                self.effective['data'] = b'third-party effective'
                raise RuntimeError('reload failed')
            return b''
        with patch.object(remote, 'run', side_effect=command):
            with self.assertRaisesRegex(RuntimeError, 'rollback refused'): self.apply()
        self.assertEqual(b'third-party source', self.source.read_bytes())
        self.assertEqual(b'third-party effective', self.effective['data'])
        self.assertEqual(self.old, (self.root / 'ingress/original.conf').read_bytes())

    def test_counted_partial_source_write_can_be_restored(self):
        real_write = remote.write_source; calls = []
        def partial(fd, data, progress):
            calls.append(1)
            if len(calls) == 1:
                part = data[:len(self.old) + 10]; os.lseek(fd, 0, os.SEEK_SET); os.write(fd, part)
                progress['written'] = len(part)
                raise OSError('controlled partial write')
            real_write(fd, data, progress)
        with patch.object(remote, 'write_source', side_effect=partial):
            with self.assertRaisesRegex(OSError, 'partial write'): self.apply()
        self.assertEqual(self.old, self.source.read_bytes())


class CertbotHookTest(unittest.TestCase):
    def test_certbot_cannot_inherit_config_or_hooks(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(remote, 'ROOT', Path(tmp).resolve()), \
             patch.object(remote, 'run', return_value=b'') as run, contextlib.redirect_stdout(io.StringIO()):
            remote.issue_cert()
        args = run.call_args.args[0]
        self.assertEqual('/dev/null', args[args.index('--config') + 1])
        self.assertIn('--no-directory-hooks', args)
        for flag in ('--pre-hook', '--post-hook', '--deploy-hook'): self.assertEqual('', args[args.index(flag) + 1])
        self.assertEqual(remote.DOMAIN, args[args.index('--cert-name') + 1])
        self.assertNotIn('--nginx', args); self.assertNotIn('--standalone', args)


if __name__ == '__main__': unittest.main()
