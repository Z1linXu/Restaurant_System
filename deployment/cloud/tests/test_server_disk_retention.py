import datetime as dt
import importlib.util
import hashlib
import io
import json
from pathlib import Path
import stat
import subprocess
import tempfile
import types
import unittest
from unittest.mock import patch

BASE=Path(__file__).resolve().parents[1]
def load(name):
    spec=importlib.util.spec_from_file_location(name,BASE/(name+'.py'))
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module
CACHE=load('server-buildkit-retention')
RELEASE=load('staging-release-runtime-protection')
JOURNAL=load('server-journal-retention')
NOW=dt.datetime(2026,10,31,tzinfo=dt.timezone.utc)

def record(days,**changes):
    value={'ID':'record','Size':123,'InUse':False,'Shared':False,'Type':'regular',
           'LastUsedAt':(NOW-dt.timedelta(days=days)).isoformat()}
    value.update(changes);return value

class CacheTests(unittest.TestCase):
    def test_exact_age_and_reference_protection(self):
        old=record(31,ID='old'); boundary=record(30,ID='boundary')
        shared=record(50,ID='shared',Shared=True);active=record(50,ID='active',InUse=True)
        internal=record(50,ID='internal',Type='internal')
        a,b=CACHE.candidates([old,boundary,shared,active,internal],NOW)
        self.assertEqual([r['ID'] for r in a],['old']);self.assertEqual(len(b),4)
    def test_timestamp_and_boolean_fail_closed(self):
        for value in [record(40,InUse='false'),record(40,LastUsedAt=None),record(-1),record(40,LastUsedAt='2020-01-01')]:
            with self.assertRaises((RuntimeError,ValueError,TypeError)):
                CACHE.candidates([value],NOW)
    def test_nanoseconds_and_prune_boundary(self):
        self.assertEqual(len(CACHE.candidates([record(31,LastUsedAt='2026-09-01T00:00:00.123456789Z')],NOW)[0]),1)
        self.assertIn('until=720h',CACHE.COMMAND)
        self.assertNotIn('--all',CACHE.COMMAND)
        self.assertNotIn('--max-used-space',CACHE.COMMAND)

class ReleaseTests(unittest.TestCase):
    def test_latest_successes_not_directory_dates_or_failed_builds(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for n in range(7):
                p=root/str(n);p.mkdir()
                (p/'result.json').write_text(json.dumps({'source_sha':str(n)*40,'environment':'staging',
                    'health':'PASS','deployed_at':'2026-10-%02dT00:00:00+00:00'%(n+1)}))
            p=root/'failed';p.mkdir();(p/'result.json').write_text(json.dumps({'source_sha':'f'*40,'health':'FAIL','environment':'staging'}))
            self.assertEqual(RELEASE.successful_releases(root)[:5],[str(n)*40 for n in range(6,1,-1)])
    def test_unknown_binary_retains_all_and_backup_is_not_parsed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);(root/'opaque').write_bytes(b'opaque\x00binary')
            (root/'backup.dump').write_bytes(b'PGDMP\x00protected backup')
            (root/'rollback.json').write_text(json.dumps({'sha':'a'*40}))
            rows=list(RELEASE.reference_files(root,root))
            self.assertIn(('*','opaque_reference_retains_all','opaque'),rows)
            self.assertIn(('a'*40,'evidence_or_recovery_reference','rollback.json'),rows)
            self.assertFalse(any(r[2]=='backup.dump' for r in rows))
    def test_sparse_checkout_keeps_identity_and_other_windows_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)
            subprocess.run(['git','init','-q',tmp],check=True)
            for name in ['backend/app.java','deployment/windows-pilot/package/backend/app.jar',
                         'deployment/windows-pilot/restaurant-pos-windows-pilot.zip','deployment/windows-pilot/README.md']:
                path=p/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('fixture')
            subprocess.run(['git','-C',tmp,'add','.'],check=True)
            subprocess.run(['git','-C',tmp,'-c','user.name=test','-c','user.email=test@example.invalid','commit','-qm','fixture'],check=True)
            sha=subprocess.check_output(['git','-C',tmp,'rev-parse','HEAD'])
            subprocess.run(['git','-C',tmp,'sparse-checkout','set','--no-cone','/*',
                '!/deployment/windows-pilot/package/','!/deployment/windows-pilot/restaurant-pos-windows-pilot.zip'],check=True)
            self.assertFalse((p/'deployment/windows-pilot/package').exists())
            self.assertFalse((p/'deployment/windows-pilot/restaurant-pos-windows-pilot.zip').exists())
            self.assertTrue((p/'backend/app.java').exists());self.assertTrue((p/'deployment/windows-pilot/README.md').exists())
            self.assertEqual(subprocess.check_output(['git','-C',tmp,'rev-parse','HEAD']),sha)
            self.assertEqual(subprocess.check_output(['git','-C',tmp,'status','--porcelain']),b'')

class JournalTests(unittest.TestCase):
    def test_header_uses_last_entry_and_archived_state(self):
        text='State: ARCHIVED\nHead realtime timestamp: old (100)\nTail realtime timestamp: recent (200)\n'
        with patch.object(JOURNAL.subprocess,'check_output',return_value=text):
            self.assertEqual(JOURNAL.header(Path('/fixture')),('ARCHIVED',0x200/1e6))
    def test_missing_tail_is_unknown(self):
        with patch.object(JOURNAL.subprocess,'check_output',return_value='State: ARCHIVED\n'):
            self.assertEqual(JOURNAL.header(Path('/fixture')),('ARCHIVED',None))
    def test_active_filename_is_never_archive(self):
        self.assertIsNone(JOURNAL.ARCHIVE.fullmatch('system.journal'))
        self.assertIsNone(JOURNAL.ARCHIVE.fullmatch('user-1000.journal'))
        self.assertIsNone(JOURNAL.ARCHIVE.fullmatch('corrupt.journal~'))
    def test_explicit_holds_require_private_owner(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'holds.json';p.write_text('{"paths": []}');p.chmod(0o666)
            with patch.object(JOURNAL,'HOLD_FILE',p):
                with self.assertRaises(RuntimeError):JOURNAL.holds()

    def execute_fixture(self, change=None):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp).resolve()
            archive=root/'archive.journal';archive.write_bytes(b'archived fixture')
            row={'path':str(archive),'identity':JOURNAL.identity(archive.stat()),
                 'allocated_bytes':archive.stat().st_blocks*512}
            current={'candidates':[dict(row)],'holds_sha256':None}
            approved={'candidates':[dict(row)],'holds_sha256':None,'candidate_bytes':99999999}
            opened=set();hold_digest=None;tail=NOW.timestamp()-40*86400
            if change=='identity':approved['candidates'][0]['identity']=[0,0,0,0]
            if change=='open':opened={str(archive)}
            if change=='hold':hold_digest='new-hold'
            if change=='recent-tail':tail=NOW.timestamp()-5*86400
            plan=root/'plan.json';body=json.dumps(approved).encode();plan.write_bytes(body)
            stream=io.StringIO()
            with patch.object(JOURNAL,'plan',return_value=current), \
                 patch.object(JOURNAL,'header',return_value=('ARCHIVED',tail)), \
                 patch.object(JOURNAL,'open_files',return_value=opened), \
                 patch.object(JOURNAL,'holds',return_value=(set(),hold_digest)), \
                 patch.object(JOURNAL.time,'time',return_value=NOW.timestamp()), \
                 patch('sys.argv',['journal','--execute-plan',str(plan),'--sha256',hashlib.sha256(body).hexdigest()]), \
                 patch('sys.stdout',stream):
                if change:
                    with self.assertRaises(RuntimeError):JOURNAL.main()
                    self.assertTrue(archive.exists())
                else:
                    JOURNAL.main();self.assertFalse(archive.exists())
                    self.assertEqual(json.loads(stream.getvalue())['removed_allocated_bytes'],row['allocated_bytes'])
    def test_verified_unlink_and_measured_bytes(self):self.execute_fixture()
    def test_identity_drift_preserves_archive(self):self.execute_fixture('identity')
    def test_open_archive_is_preserved(self):self.execute_fixture('open')
    def test_new_hold_prevents_unlink(self):self.execute_fixture('hold')
    def test_recent_tail_prevents_unlink_despite_old_plan(self):self.execute_fixture('recent-tail')

if __name__=='__main__':unittest.main()
