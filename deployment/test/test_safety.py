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
import tarfile
import stat
from deployment_targets import DEFAULT_TARGET, ALLO_TARGET, target_config, rendered_files, validate_inventory
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
             patch.object(remote, 'jsonrun', side_effect=inspect) as calls, \
             patch.object(remote, 'container_documents', side_effect=lambda ids: [container('backend' if ids[0] == 'test_internal-member' else ingress_member)]):
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


class EdgeValidationFileTest(unittest.TestCase):
    def test_candidate_uses_stdin_payload_and_private_regular_file_command(self):
        candidate = b'server { listen 80; server_name synthetic.example; }'
        with patch.object(remote, 'run', return_value=b'') as command:
            remote.validate_edge(candidate)
        args, payload = command.call_args.args
        self.assertEqual(['docker', 'exec', '-i', remote.EDGE, 'sh', '-c'], args[:6])
        self.assertEqual(7, len(args))
        script = args[-1]
        self.assertIn('umask 077', script)
        self.assertIn('mktemp -d /tmp/restaurant-template-nginx.XXXXXX', script)
        self.assertIn('rm -f -- "$directory/candidate.conf"; rmdir -- "$directory"', script)
        self.assertNotIn('rm -r', script)
        self.assertIn('nginx -t -c "$directory/candidate.conf" >/dev/null 2>&1', script)
        self.assertNotIn('/dev/stdin', script)
        self.assertNotIn(candidate.decode(), script)
        self.assertTrue(payload.startswith(b'worker_processes 1; error_log stderr; events {} http {'))
        self.assertIn(candidate, payload)
        self.assertEqual(0, subprocess.run(['sh', '-n', '-c', script], capture_output=True).returncode)

    def validation_lifecycle(self, exit_status):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            record = root / 'observed.json'
            sentinel = root / 'unrelated.conf'; sentinel.write_text('leave unchanged')
            executable = root / 'nginx'
            executable.write_text('''#!/usr/bin/env python3
import hashlib,json,os,pathlib,stat,sys
assert sys.argv[1:3] == ['-t','-c'] and len(sys.argv) == 4
path=pathlib.Path(sys.argv[3])
assert path.is_absolute() and path.is_file() and not path.is_symlink()
record={'path':str(path),'directory_mode':stat.S_IMODE(path.parent.stat().st_mode),
        'file_mode':stat.S_IMODE(path.stat().st_mode),'content_sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
pathlib.Path(os.environ['TEST_NGINX_RECORD']).write_text(json.dumps(record))
print('SYNTHETIC_DIAGNOSTIC_MUST_NOT_ESCAPE')
print('SYNTHETIC_DIAGNOSTIC_MUST_NOT_ESCAPE',file=sys.stderr)
sys.exit(int(os.environ['TEST_NGINX_EXIT']))
''')
            executable.chmod(0o700)
            observed = {}
            def execute(args, data=None):
                observed['payload'] = data
                process = subprocess.run(['sh', '-c', args[-1]], input=data, capture_output=True,
                    env={'PATH': str(root) + os.pathsep + os.environ['PATH'],
                         'TEST_NGINX_RECORD': str(record), 'TEST_NGINX_EXIT': str(exit_status)})
                self.assertEqual(exit_status, process.returncode)
                self.assertEqual(b'', process.stdout)
                self.assertEqual(b'', process.stderr)
                if process.returncode: raise RuntimeError('Candidate validation failed')
                return process.stdout
            with patch.object(remote, 'run', side_effect=execute):
                if exit_status:
                    with self.assertRaisesRegex(RuntimeError, 'validation failed'):
                        remote.validate_edge(b'server { listen 80; server_name synthetic.example; }')
                else:
                    remote.validate_edge(b'server { listen 80; server_name synthetic.example; }')
            evidence = json.loads(record.read_text())
            candidate = Path(evidence['path'])
            self.assertTrue(str(candidate.parent).startswith('/tmp/restaurant-template-nginx.'))
            self.assertEqual(0o700, evidence['directory_mode'])
            self.assertEqual(0o600, evidence['file_mode'])
            self.assertEqual(remote.sha256(observed['payload']), evidence['content_sha256'])
            self.assertFalse(candidate.exists())
            self.assertFalse(candidate.parent.exists())
            self.assertEqual('leave unchanged', sentinel.read_text())

    def test_success_cleans_only_private_validation_file_and_directory(self):
        self.validation_lifecycle(0)

    def test_nginx_failure_preserves_failure_status_and_cleans_private_directory(self):
        self.validation_lifecycle(42)


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


class FixedTargetTest(unittest.TestCase):
    def test_template_defaults_and_rendered_topology_unchanged(self):
        self.assertEqual('/srv/restaurant-pos/test', str(target_config()['root']))
        files = rendered_files(HERE)
        for name in ('compose.yml', 'relay.conf', 'nginx.conf', 'Dockerfile.backend', 'Dockerfile.frontend'):
            self.assertEqual((HERE/name).read_bytes(), files[name])

    def test_unknown_target_rejected(self):
        for name in ('production', '../../test', 'restaurant-pos-staging', 'arbitrary'):
            with self.subTest(name=name), self.assertRaises(ValueError): target_config(name)

    def test_allo_protects_production_and_staging_without_retired_template(self):
        with remote.target_context(ALLO_TARGET):
            self.assertEqual(remote.BASE_PROTECTED, remote.PROTECTED)
            self.assertEqual(8, len(remote.PROTECTED))
            self.assertFalse(any(name.startswith(DEFAULT_TARGET + '-') for name in remote.PROTECTED))
        self.assertEqual(DEFAULT_TARGET, remote.ACTIVE_TARGET)

    def test_structural_inspect_never_reads_env_or_logs(self):
        with patch.object(remote, 'run', return_value=b'{}\n') as command:
            remote.container_documents(['example'])
        fmt = command.call_args.args[0][3]
        self.assertIn('--format', command.call_args.args[0])
        self.assertNotIn('.Config.Env', fmt); self.assertNotIn('Health.Log', fmt)
        self.assertNotIn('Cmd', fmt); self.assertIn('.Config.Labels', fmt)

    def test_private_runtime_secrets_are_independent_and_reentry_stable(self):
        images=json.loads((HERE/'images.json').read_text())
        with tempfile.TemporaryDirectory() as tmp:
            folder=Path(tmp).resolve(); values=[]
            for target in (DEFAULT_TARGET,ALLO_TARGET):
                location=folder/target;location.mkdir()
                with remote.target_context(target),patch.object(remote,'ROOT',location):
                    first=remote.runtime_env(SHA,images);before=first.read_bytes()
                    remote.runtime_env(SHA,images)
                    self.assertEqual(before,first.read_bytes());self.assertEqual(0o600,stat.S_IMODE(first.stat().st_mode))
                    values.append(dict(line.split('=',1) for line in before.decode().splitlines()))
            self.assertNotEqual(values[0]['TEST_JWT_SECRET'],values[1]['TEST_JWT_SECRET'])
            self.assertNotEqual(values[0]['TEST_DB_PASSWORD'],values[1]['TEST_DB_PASSWORD'])
            self.assertNotIn('TEMPLATE_TEST_OWNER_PASSWORD',values[1]);self.assertIn('ALLO_TEST_OWNER_PASSWORD',values[1])

    def test_allo_inventory_refuses_unreviewed_env_file(self):
        names=set(rendered_files(HERE,ALLO_TARGET))|{'app.jar','code-sha.txt','checks.json','merchant/menu.source.json','dist/index.html'}
        validate_inventory(names,ALLO_TARGET)
        with self.assertRaises(ValueError):validate_inventory(names|{'runtime.env'},ALLO_TARGET)
        with self.assertRaises(ValueError):validate_inventory(names-{'merchant/menu.source.json'},ALLO_TARGET)

    def test_allo_cannot_consume_template_manifest_identity(self):
        with remote.target_context(ALLO_TARGET):
            with self.assertRaises(ValueError): remote.require_target_identity({'deployment_target':DEFAULT_TARGET})
            with self.assertRaises(ValueError): remote.require_target_identity({})
            remote.require_target_identity({'deployment_target':ALLO_TARGET})


class AlloComposeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory(); cls.folder = Path(cls.temp.name).resolve()
        for name, raw in rendered_files(HERE, ALLO_TARGET).items(): (cls.folder/name).write_bytes(raw)
        (cls.folder/'merchant').mkdir(); (cls.folder/'merchant/menu.source.json').write_text('{}')
        images = json.loads((HERE/'images.json').read_text())
        env = {k:os.environ[k] for k in ('PATH','HOME') if k in os.environ}
        env.update(TEST_IMAGE_SHA=SHA, TEST_DB_IMAGE=images['postgres'], TEST_NGINX_IMAGE=images['nginx'])
        for key in ('TEST_DB_PASSWORD','TEST_JWT_SECRET','ALLO_TEST_OWNER_PASSWORD','ALLO_TEST_MANAGER_PASSWORD'): env[key]='0'*64
        result = subprocess.run(['docker','compose','--project-name',ALLO_TARGET,'--env-file','/dev/null','--file',str(cls.folder/'compose.yml'),'config','--format','json'],env=env,capture_output=True,text=True)
        if result.returncode: raise RuntimeError('Allo local Compose parse failed; output withheld')
        cls.model=json.loads(result.stdout)

    @classmethod
    def tearDownClass(cls): cls.temp.cleanup()

    def validate(self, model=None):
        with remote.target_context(ALLO_TARGET): remote.validate_compose(model or self.model,SHA,self.folder)

    def reject(self, mutate):
        model=copy.deepcopy(self.model);mutate(model)
        with self.assertRaises(ValueError): self.validate(model)

    def test_actual_allo_compose_and_backend_menu_readonly(self):
        self.validate()
        backend=self.model['services']['backend']
        self.assertEqual('allo-test',backend['environment']['SPRING_PROFILES_ACTIVE'])
        self.assertEqual('/run/merchant/menu.source.json',backend['volumes'][0]['target'])
        self.assertTrue(backend['volumes'][0]['read_only'])
        self.assertNotIn('TEMPLATE_TEST_OWNER_PASSWORD',backend['environment'])
        self.assertIn('ALLO_TEST_OWNER_PASSWORD',backend['environment'])
        self.assertEqual('28080',str(self.model['services']['frontend']['ports'][0]['published']))
        self.assertIn('172.18.0.1:28480', (self.folder/'relay.conf').read_text())
        self.assertIn('127.0.0.1:28080', (self.folder/'relay.conf').read_text())
        self.assertEqual((HERE/'relay.conf').read_bytes(), (self.folder/'relay.conf').read_bytes())

    def test_template_database_rejected(self): self.reject(lambda m:m['services']['db']['environment'].update(POSTGRES_DB='restaurant_template_test'))
    def test_template_user_rejected(self): self.reject(lambda m:m['services']['backend']['environment'].update(SPRING_DATASOURCE_USERNAME='template_test'))
    def test_template_profile_rejected(self): self.reject(lambda m:m['services']['backend']['environment'].update(SPRING_PROFILES_ACTIVE='template-test'))
    def test_template_volume_rejected(self): self.reject(lambda m:m['volumes']['test_postgres'].update(name=DEFAULT_TARGET+'_postgres'))
    def test_foreign_network_rejected(self): self.reject(lambda m:m['networks']['test_internal'].update(name=DEFAULT_TARGET+'_internal'))
    def test_parallel_test_frontend_port_rejected(self): self.reject(lambda m:m['services']['frontend']['ports'][0].update(published='28180'))
    def test_menu_writable_rejected(self): self.reject(lambda m:m['services']['backend']['volumes'][0].update(read_only=False))
    def test_menu_from_other_release_rejected(self): self.reject(lambda m:m['services']['backend']['volumes'][0].update(source='/srv/restaurant-pos/test/private/menu.json'))
    def test_additional_backend_mount_rejected(self): self.reject(lambda m:m['services']['backend']['volumes'].append(copy.deepcopy(m['services']['backend']['volumes'][0])))
    def test_backend_egress_rejected(self): self.reject(lambda m:m['services']['backend']['networks'].update(test_ingress={}))
    def test_old_template_container_cannot_be_adopted(self):
        original=container('db')
        with remote.target_context(ALLO_TARGET), self.assertRaises(ValueError): remote.require_project_container(original,'db')
    def test_template_network_cannot_be_adopted(self):
        doc={'Name':DEFAULT_TARGET+'_internal','Driver':'bridge','Scope':'local','Internal':True,'Labels':{'com.docker.compose.project':DEFAULT_TARGET,'com.docker.compose.network':'test_internal'}}
        with remote.target_context(ALLO_TARGET), self.assertRaises(ValueError): remote.validate_network_document(doc)


class MenuArtifactTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.folder=Path(self.temp.name).resolve()
        self.menu=self.folder/'menu.json';self.menu.write_text('{"synthetic_test":true}');self.menu.chmod(0o600)
    def tearDown(self): self.temp.cleanup()
    def test_private_json_only(self): self.assertEqual(self.menu.read_bytes(),release.read_merchant_menu(self.menu,ALLO_TARGET))
    def test_template_target_cannot_import_customer_menu(self):
        with self.assertRaises(ValueError): release.read_merchant_menu(self.menu,DEFAULT_TARGET)
    def test_world_readable_source_refused(self):
        self.menu.chmod(0o644)
        with self.assertRaises(ValueError): release.read_merchant_menu(self.menu,ALLO_TARGET)
    def test_source_symlink_refused(self):
        alias=self.folder/'alias.json';alias.symlink_to(self.menu)
        with self.assertRaises(ValueError): release.read_merchant_menu(alias,ALLO_TARGET)
    def test_invalid_json_refused(self):
        self.menu.write_text('invalid')
        with self.assertRaises(ValueError): release.read_merchant_menu(self.menu,ALLO_TARGET)
    def test_allo_receipt_binds_menu_and_namespace(self):
        good={'code_sha':SHA,'checks':{k:'PASS' for k in release.CHECKS},'independent_review':'ACCEPT','commands':['synthetic unit proof'],'jar_sha256':'jar','dist_sha256':'dist','environment':'ISOLATED_SYNTHETIC_MOCK','deployment_target':ALLO_TARGET,'merchant_menu_sha256':'menu','client_namespace':ALLO_TARGET}
        release.receipt_ok(good,SHA,'jar','dist',ALLO_TARGET,'menu')
        for key,value in [('deployment_target',DEFAULT_TARGET),('merchant_menu_sha256','other'),('client_namespace',SHA)]:
            bad=copy.deepcopy(good);bad[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError): release.receipt_ok(bad,SHA,'jar','dist',ALLO_TARGET,'menu')

    def pack(self):
        repo=self.folder/'repo';(repo/'backend/target').mkdir(parents=True);(repo/'frontend/dist').mkdir(parents=True);(repo/'backend/src/main/resources/db/migration').mkdir(parents=True)
        jar=b'synthetic tested jar';(repo/'backend/target/app.jar').write_bytes(jar)
        (repo/'frontend/dist/index.html').write_text(SHA+' '+ALLO_TARGET)
        (repo/'backend/src/main/resources/db/migration/V1__synthetic.sql').write_text('-- fixture')
        files=release.dist_files(repo/'frontend/dist')
        receipt={'code_sha':SHA,'checks':{k:'PASS' for k in release.CHECKS},'independent_review':'ACCEPT','commands':['synthetic packaging test only'],'jar_sha256':release.digest(jar),'dist_sha256':release.tree_digest(files),'environment':'ISOLATED_SYNTHETIC_MOCK','deployment_target':ALLO_TARGET,'merchant_menu_sha256':release.digest(self.menu.read_bytes()),'client_namespace':ALLO_TARGET}
        proof=self.folder/'receipt.json';proof.write_text(json.dumps(receipt))
        bundle=self.folder/'bundle.tar.gz'
        with patch.object(release,'REPO',repo),patch.object(release.subprocess,'check_output',side_effect=lambda args,**kw:SHA if args[1]=='rev-parse' else b''),contextlib.redirect_stdout(io.StringIO()):
            release.pack(proof,bundle,ALLO_TARGET,self.menu)
        return bundle

    def test_pack_carries_exact_menu_and_fixed_target(self):
        bundle=self.pack()
        self.assertEqual(0o600,stat.S_IMODE(bundle.stat().st_mode))
        with tarfile.open(bundle,'r:gz') as archive:
            manifest=json.load(archive.extractfile('manifest.json'))
            self.assertEqual(ALLO_TARGET,manifest['deployment_target'])
            self.assertEqual(self.menu.read_bytes(),archive.extractfile('merchant/menu.source.json').read())
            self.assertIn(b'allo-test',archive.extractfile('compose.yml').read())
            self.assertNotIn('runtime.env',archive.getnames())
            self.assertIn('deployment_targets.py',archive.getnames())
        with patch.object(release.subprocess,'run') as ssh:
            with self.assertRaises(ValueError): release.upload(bundle,SHA,DEFAULT_TARGET)
            ssh.assert_not_called()

    def test_remote_verifier_rejects_changed_menu(self):
        bundle=self.pack();root=self.folder/'remote';destination=root/'releases'/SHA;destination.mkdir(parents=True)
        with tarfile.open(bundle,'r:gz') as archive:
            for member in archive.getmembers():
                path=destination/member.name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(archive.extractfile(member).read())
        with remote.target_context(ALLO_TARGET),patch.object(remote,'ROOT',root):
            remote.verify_release(destination,SHA)
            (destination/'merchant/menu.source.json').write_text('{}')
            with self.assertRaises(ValueError):remote.verify_release(destination,SHA)


class AlloReplacementBoundaryTest(unittest.TestCase):
    def test_every_edge_and_certificate_cli_action_refused_before_release_or_runtime_reads(self):
        for action in ('edge-http','edge-https','edge-rollback','edge-switch','edge-restore','issue-cert'):
            with self.subTest(action=action), remote.target_context(ALLO_TARGET), \
                 patch('sys.argv',['remote.py',action,'--target',ALLO_TARGET,'--sha',SHA]), \
                 patch.object(remote,'verify_release') as verify, patch.object(remote,'run') as command:
                with self.assertRaisesRegex(ValueError,'shared edge mutation is forbidden'):remote.main()
                verify.assert_not_called();command.assert_not_called()

    def test_direct_edge_and_certificate_calls_cannot_bypass_cli_guard(self):
        actions=(lambda:remote.apply_edge(True,'f'*64),lambda:remote.managed_slot_candidate(b'original'),
                 lambda:remote.managed_slot_candidate(b'original',restore=True),lambda:remote.issue_cert())
        with remote.target_context(ALLO_TARGET), patch.object(remote,'run') as command, \
             patch.object(remote,'private_write') as write, patch.object(remote,'locked_edge_source') as locked:
            for action in actions:
                with self.assertRaises(ValueError):action()
            command.assert_not_called();write.assert_not_called();locked.assert_not_called()

    def test_template_retirement_guard_refuses_even_stopped_named_container(self):
        for service in ('db','backend','frontend','relay'):
            raw=(DEFAULT_TARGET+'-'+service+'-1\n').encode()
            with self.subTest(service=service), remote.target_context(ALLO_TARGET), patch.object(remote,'run',return_value=raw) as command:
                with self.assertRaisesRegex(ValueError,'retired before Allo'):remote.verify_template_retired()
                self.assertEqual(['docker','ps','-a','--format','{{.Names}}'],command.call_args.args[0])

    def test_retired_template_allows_existing_allo_or_other_environment_names(self):
        raw=('cloud-backend-1\nrestaurant-pos-staging-db-1\n'+ALLO_TARGET+'-frontend-1\n').encode()
        with remote.target_context(ALLO_TARGET), patch.object(remote,'run',return_value=raw):remote.verify_template_retired()
        with remote.target_context(DEFAULT_TARGET), patch.object(remote,'run') as command:
            remote.verify_template_retired();command.assert_not_called()

    def test_deploy_checks_retirement_before_any_resource_or_secret_action(self):
        with remote.target_context(ALLO_TARGET), patch.object(remote,'run',return_value=(DEFAULT_TARGET+'-db-1\n').encode()), \
             patch.object(remote,'protected_snapshot') as snapshot, patch.object(remote,'runtime_env') as secrets:
            with self.assertRaisesRegex(ValueError,'retired before Allo'):remote.deploy(Path('/unused'),SHA,{})
            snapshot.assert_not_called();secrets.assert_not_called()

    def test_old_template_listener_on_reused_port_is_not_adopted(self):
        old=container('relay')
        row={'port':28480,'ip':'172.18.0.1','pids':[7]}
        with remote.target_context(ALLO_TARGET):
            with self.assertRaisesRegex(ValueError,'unknown listener'):
                remote.validate_port_owners([row],{}, {})
            with self.assertRaisesRegex(ValueError,'non-Test container'):
                remote.validate_port_owners([row],{'relay':old},{7:{'cgroup':'/docker/test-container-id','args':[]}})

    def test_protected_snapshot_retains_stopped_staging_and_detects_state_changes(self):
        rows=[]
        for name in remote.BASE_PROTECTED:
            rows.append(['/'+name,'id-'+name,'image-'+name,'original-start',0,not name.startswith('restaurant-pos-staging-'),False,'healthy' if name=='cloud-db-1' else None])
        def encoded(values):return ('\n'.join('|'.join(json.dumps(v) for v in row) for row in values)+'\n').encode()
        with remote.target_context(ALLO_TARGET), patch.object(remote,'run',return_value=encoded(rows)) as command:
            before=remote.protected_snapshot()
            fmt=command.call_args.args[0][3]
            self.assertIn('.State.Running',fmt);self.assertIn('.State.Restarting',fmt);self.assertIn('.State.Health.Status',fmt)
            self.assertNotIn('.Config.Env',fmt);self.assertNotIn('Health.Log',fmt)
        self.assertFalse(before['/restaurant-pos-staging-backend-1']['running'])
        for field,value in ((5,False),(6,True),(7,'unhealthy')):
            changed=copy.deepcopy(rows);changed[0][field]=value
            with self.subTest(field=field),remote.target_context(ALLO_TARGET),patch.object(remote,'run',return_value=encoded(changed)):
                self.assertNotEqual(before,remote.protected_snapshot())

    def test_protected_stop_without_recreation_fails_deployment(self):
        before={'/cloud-backend-1':{'id':'same','image':'same','started':'same','restarts':0,'running':True,'restarting':False,'health':'healthy'}}
        after=copy.deepcopy(before);after['/cloud-backend-1']['running']=False
        with tempfile.TemporaryDirectory() as folder,remote.target_context(ALLO_TARGET),contextlib.ExitStack() as stack:
            release_folder=Path(folder).resolve();(release_folder/'relay.conf').write_text('synthetic');(release_folder/'merchant').mkdir();(release_folder/'merchant/menu.source.json').write_text('{}')
            for name in ('verify_template_retired','verify_existing_resources','verify_port_owners','validate_compose'):
                stack.enter_context(patch.object(remote,name))
            stack.enter_context(patch.object(remote,'protected_snapshot',side_effect=[before,after]))
            stack.enter_context(patch.object(remote,'admission',return_value={}))
            stack.enter_context(patch.object(remote,'runtime_env',return_value=release_folder/'private.env'))
            stack.enter_context(patch.object(remote,'compose',return_value=b'{}'))
            stack.enter_context(patch.object(remote,'run',return_value=b''))
            response=stack.enter_context(patch.object(remote.urllib.request,'urlopen')).return_value.__enter__.return_value
            response.status=200;response.read.return_value=b'{"data":{"status":"UP"}}'
            verified=stack.enter_context(patch.object(remote,'verify_runtime'))
            evidence=stack.enter_context(patch.object(remote,'evidence'))
            with self.assertRaisesRegex(RuntimeError,'Protected environment identity changed'):
                remote.deploy(release_folder,SHA,{'jre':'synthetic-jre','nginx':'synthetic-nginx','postgres':'synthetic-postgres'})
            verified.assert_not_called();evidence.assert_not_called()

    def test_first_start_ram_admission_is_not_lowered_for_replacement(self):
        with remote.target_context(ALLO_TARGET),patch.object(remote.Path,'read_text',return_value='MemAvailable: '+str(1099*1024)+' kB\n'), \
             patch.object(remote.shutil,'disk_usage',return_value=type('Disk',(),{'free':7*1024**3})()),patch.object(remote,'container_documents') as inspected:
            with self.assertRaisesRegex(ValueError,'1100MiB'):remote.admission()
            inspected.assert_not_called()


if __name__ == '__main__': unittest.main()
