#!/usr/bin/env python3
"""Staging-only Finish All batch: reuse reviewed backup/build/app-pair/rollback lifecycle."""
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
    assert before.splitlines()[-1] == '39|V39__add_dining_table_split_label_mode.sql|1431202837|t'
    lines = after.splitlines()
    assert lines[:-1] == before.splitlines(), 'Existing migration ledger changed'
    assert re.fullmatch(r'40\|V40__order_finish_batches.sql\|-?\d+\|t', lines[-1]), 'Only additive V40 is authorized'


def main(sha):
    assert re.fullmatch('[0-9a-f]{40}', sha), 'Exact SHA required'
    assert release.i.ledger(STAGE_DB).splitlines()[-1].startswith('39|'), 'Expected existing V39 Staging'
    release.OLD_IMAGE = 'sha256:bf09f83635d532523b82c39dae30afbdfa12840858dd195d46423423cf49f696'
    release.OLD_FRONTEND = 'sha256:af9a528ae56ceb905597794823b2ad417ef3c580fac6e3689c311ec837dcc9d9'
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
        assert version in ('39', '40')
        return original_queue(db, version)
    release.read_sql = read
    release.queue_guard = queue
    release.main('staging', sha)  # No Production argument or promotion path is exposed.


if __name__ == '__main__':
    assert len(sys.argv) == 2, 'Usage: deploy-staging.py <exact SHA>'
    with release.ops.operation_locks():
        main(sys.argv[1])
