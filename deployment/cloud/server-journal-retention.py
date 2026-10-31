#!/usr/bin/env python3
"""Remove only archived journal files whose final entry is older than 30 days."""
import argparse
import glob
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import time

ARCHIVE = re.compile(r"(?:system|user-\d+)@[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{16}\.journal")
ROOT = Path('/var/log/journal')
HOLD_FILE = Path('/etc/restaurant-system/log-retention-holds.json')


def require(value, message):
    if not value:
        raise RuntimeError(message)


def header(path):
    text = subprocess.check_output(['journalctl', '--file=' + str(path), '--header', '--no-pager'],
                                   text=True, env={'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'LC_ALL': 'C'})
    state = re.search(r'^State: (\w+)$', text, re.M)
    tail = re.search(r'^Tail realtime timestamp:.*\(([0-9a-f]+)\)$', text, re.M)
    return state.group(1) if state else None, int(tail.group(1), 16) / 1e6 if tail else None


def identity(value):
    return [value.st_dev, value.st_ino, value.st_size, value.st_mtime_ns]


def open_files():
    opened = set()
    for descriptor in glob.glob('/proc/[0-9]*/fd/*'):
        try:
            opened.add(os.readlink(descriptor))
        except FileNotFoundError:
            continue
    return opened


def holds():
    if not HOLD_FILE.exists():
        return set(), None
    require(not HOLD_FILE.is_symlink() and HOLD_FILE.resolve() == HOLD_FILE, 'unsafe log hold file')
    value = HOLD_FILE.stat()
    require(value.st_uid == 0 and not value.st_mode & 0o022, 'unsafe log hold metadata')
    body = HOLD_FILE.read_bytes()
    paths = json.loads(body)['paths']
    require(isinstance(paths, list) and all(isinstance(value, str) for value in paths), 'invalid log holds')
    return set(paths), hashlib.sha256(body).hexdigest()


def plan(now):
    require(os.geteuid() == 0, 'root required to check all process file references')
    require(ROOT.is_dir() and ROOT.resolve() == ROOT, 'unexpected journal directory')
    require(ROOT.stat().st_uid == 0 and not ROOT.stat().st_mode & 0o022, 'unsafe journal directory owner/mode')
    held, hold_digest = holds()
    opened = open_files()
    candidates, preserved = [], []
    for path in sorted(ROOT.glob('*/*')):
        if not path.is_file():
            continue
        value = path.lstat()
        row = {'path': str(path), 'allocated_bytes': value.st_blocks * 512, 'identity': identity(value)}
        reason = 'active_or_unknown_filename'
        if ARCHIVE.fullmatch(path.name) and path.resolve() == path:
            state, tail = header(path)
            row.update(state=state, tail_timestamp=tail)
            if (state == 'ARCHIVED' and tail is not None and 0 < tail < now - 30 * 86400
                    and value.st_mtime < now - 30 * 86400 and stat.S_ISREG(value.st_mode)
                    and value.st_nlink == 1 and value.st_uid == 0
                    and path.parent.stat().st_uid == 0 and not path.parent.stat().st_mode & 0o022):
                reason = 'explicit_hold' if str(path) in held else 'open_file' if str(path) in opened else None
            else:
                reason = 'recent_or_unverified_archive'
        if reason is None:
            candidates.append(row)
        else:
            row['reason'] = reason
            preserved.append(row)
    return {'observed_unix': now, 'holds_sha256': hold_digest, 'candidates': candidates, 'preserved': preserved,
            'candidate_bytes': sum(row['allocated_bytes'] for row in candidates)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute-plan', type=Path)
    parser.add_argument('--sha256')
    args = parser.parse_args()
    current = plan(time.time())
    if not args.execute_plan:
        print(json.dumps(current, indent=2))
        return
    require(args.sha256 and not args.execute_plan.is_symlink(), 'reviewed plan and digest required')
    body = args.execute_plan.read_bytes()
    require(hashlib.sha256(body).hexdigest() == args.sha256, 'journal plan digest mismatch')
    approved = json.loads(body)
    require(approved['holds_sha256'] == current['holds_sha256'], 'journal holds changed since plan')
    eligible = {row['path']: row for row in current['candidates']}
    removed = []
    for row in approved['candidates']:
        name = row['path']
        require(name in eligible and row['identity'] == eligible[name]['identity'], 'journal candidate drift')
        path = Path(name)
        require(path.resolve() == path and identity(path.lstat()) == row['identity'], 'journal path drift')
        state, tail = header(path)
        require(state == 'ARCHIVED' and tail is not None and tail < time.time() - 30 * 86400,
                'archive no longer eligible')
        require(name not in open_files(), 'journal opened during cleanup')
        require(holds()[1] == current['holds_sha256'], 'journal holds changed during cleanup')
        require(identity(path.lstat()) == row['identity'], 'journal changed during verification')
        path.unlink()
        removed.append(name)
    print(json.dumps({'removed': removed, 'removed_allocated_bytes': sum(eligible[name]['allocated_bytes'] for name in removed)}))


if __name__ == '__main__':
    main()
