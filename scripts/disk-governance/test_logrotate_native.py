"""Native logrotate rehearsal on synthetic files in one private temp directory.

Only paths and OS account names are substituted; policy directives are unchanged.
Never pass a host log directory. No daemon or scheduled job is started.
"""
import gzip
import grp
import os
from pathlib import Path
import pwd
import shutil
import subprocess
import tempfile
import unittest

BASE = Path(__file__).resolve().parent
BINARY = os.environ.get('LOGROTATE_BIN') or shutil.which('logrotate')


@unittest.skipUnless(BINARY, 'native logrotate binary unavailable; semantic tests remain separate')
class NativeRotationTests(unittest.TestCase):
    def test_debug_then_two_rotations_preserve_and_compress_synthetic_logs(self):
        source = (BASE / 'pending/maintenance.logrotate').read_text()
        user = pwd.getpwuid(os.getuid()).pw_name
        group = grp.getgrgid(os.getgid()).gr_name
        with tempfile.TemporaryDirectory(prefix='restaurant-logrotate-fixture-') as tmp:
            root = Path(tmp).resolve()
            (root / 'user').mkdir()
            logs = [root / 'journal.log', root / 'user/cache.log']
            for log in logs:
                log.write_text('synthetic-first:' + log.name + '\n')
            config = source.replace('/var/log/restaurant-disk-retention', str(root))
            config = config.replace('create 0640 root adm', 'create 0640 ' + user + ' ' + group)
            config = config.replace('create 0640 ubuntu adm', 'create 0640 ' + user + ' ' + group)
            config = config.replace('su ubuntu adm', 'su ' + user + ' ' + group)
            self.assertNotIn('/var/log/', config)
            config_path = root / 'fixture.logrotate'
            config_path.write_text(config)
            state = root / 'fixture.state'
            environment = os.environ.copy()
            if environment.get('LOGROTATE_LIBRARY_DIR'):
                environment['DYLD_LIBRARY_PATH'] = environment['LOGROTATE_LIBRARY_DIR']
            result = subprocess.run([BINARY, '--debug', '--state', str(state), str(config_path)],
                                    capture_output=True, text=True, env=environment)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertFalse(state.exists())
            self.assertFalse(any(root.glob('*.1')))
            for _ in range(2):
                result = subprocess.run([BINARY, '--force', '--state', str(state), str(config_path)],
                                        capture_output=True, text=True, env=environment)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                if _ == 0:
                    for log in logs: log.write_text('synthetic-second:' + log.name + '\n')
            for log in logs:
                self.assertEqual(log.read_text(), '')
                self.assertEqual(Path(str(log) + '.1').read_text(), 'synthetic-second:' + log.name + '\n')
                with gzip.open(str(log) + '.2.gz', 'rt') as archive:
                    self.assertEqual(archive.read(), 'synthetic-first:' + log.name + '\n')
                self.assertEqual(log.stat().st_mode & 0o777, 0o640)


if __name__ == '__main__':
    unittest.main()
