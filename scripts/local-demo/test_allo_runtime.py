"""Fail-closed lifecycle guards; never start or stop a real process."""
import pathlib
import types
import unittest
from unittest.mock import patch

import allo_runtime as runtime


class RuntimeIdentityTest(unittest.TestCase):
    def test_invalid_pid_never_signals(self):
        with patch.object(runtime.os, 'kill') as kill:
            for value in (None, 0, -1, True, '123'):
                self.assertFalse(runtime.alive(value))
            kill.assert_not_called()

    def test_missing_process_can_be_started(self):
        with patch.object(runtime, 'alive', return_value=False), patch.object(runtime.subprocess, 'check_output') as ps:
            self.assertFalse(runtime.owned_alive(123, '/owned/backend.jar'))
            ps.assert_not_called()

    def test_owned_running_process_is_reused(self):
        with patch.object(runtime, 'alive', return_value=True), patch.object(runtime.subprocess, 'check_output', return_value='java -jar /owned/backend.jar'):
            self.assertTrue(runtime.owned_alive(123, '/owned/backend.jar'))

    def test_reused_pid_is_rejected(self):
        with patch.object(runtime, 'alive', return_value=True), patch.object(runtime.subprocess, 'check_output', return_value='another application'):
            with self.assertRaises(SystemExit):
                runtime.owned_alive(123, '/owned/backend.jar')

    def test_stale_pg_pid_file_does_not_skip_start(self):
        run=pathlib.Path('/owned/runtime')
        state={'ports': {'postgres': 55441}, 'socket_directory': '/private/tmp/allo-owned'}
        with patch.object(runtime, 'state_for', return_value=(run, state)), patch.object(runtime.subprocess, 'run', side_effect=[types.SimpleNamespace(returncode=3), types.SimpleNamespace(returncode=0)]) as execute:
            runtime.start_pg(run)
            self.assertEqual(execute.call_count, 2)
            self.assertEqual(execute.call_args_list[1].args[0][-1], 'start')

    def test_unknown_pg_status_fails_closed(self):
        with patch.object(runtime, 'state_for', return_value=(pathlib.Path('/owned/runtime'), {})), patch.object(runtime.subprocess, 'run', return_value=types.SimpleNamespace(returncode=1)) as execute:
            with self.assertRaises(SystemExit):
                runtime.start_pg('/owned/runtime')
            self.assertEqual(execute.call_count, 1)

    def test_foreign_application_pid_blocks_before_any_start(self):
        with patch.object(runtime, 'state_for', return_value=(pathlib.Path('/owned/runtime'), {'pids': {'backend': 123}})), patch.object(runtime, 'owned_alive', side_effect=SystemExit('foreign')), patch.object(runtime, 'start_pg') as pg:
            with self.assertRaises(SystemExit):
                runtime.start('/owned/runtime')
            pg.assert_not_called()


if __name__=='__main__':
    unittest.main()
