#!/usr/bin/env python3
"""Staging-only V40 acceptance repair; reuse reviewed backup/build/app-pair/rollback lifecycle."""
import importlib.util
from pathlib import Path
import re
import sys

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('finish_staging_release', Path(__file__).resolve().parents[1] / 'dine-in-headers/deploy.py')
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
STAGE_DB = 'restaurant-pos-staging-db-1'


def validate_ledger(before, after):
    assert re.fullmatch(r'40\|V40__order_finish_batches.sql\|-?\d+\|t', before.splitlines()[-1])
    assert after == before, 'Permission/fixture repair has no migration'


def main(sha):
    assert re.fullmatch('[0-9a-f]{40}', sha), 'Exact SHA required'
    assert release.i.ledger(STAGE_DB).splitlines()[-1].startswith('40|'), 'Expected accepted V40 Staging'
    release.OLD_IMAGE = 'sha256:18dd937f827ccd30759b5e03ff762f100faccf111bead4e71b59917d469b5f2d'
    release.OLD_FRONTEND = 'sha256:7a999a9278ce78cc3086653c5d55e5d0672a17b193451ea959e0d832158612de'
    release.RELEASE_PREFIX = 'finish-all-tables-'
    release.validate_ledger = validate_ledger
    original_read = release.read_sql
    original_queue = release.queue_guard
    def read(db, sql):
        assert db == STAGE_DB, 'Finish All release may only query Staging DB'
        return original_read(db, sql)
    def queue(db, flyway='39'):
        assert db == STAGE_DB
        version = release.i.ledger(db).splitlines()[-1].split('|')[0]
        assert version == '40'
        return original_queue(db, version)
    release.read_sql = read
    release.queue_guard = queue
    release.main('staging', sha)  # No Production argument or promotion path is exposed.


if __name__ == '__main__':
    assert len(sys.argv) == 2, 'Usage: deploy-staging.py <exact SHA>'
    with release.ops.operation_locks():
        main(sys.argv[1])
