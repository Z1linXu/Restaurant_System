# Staging Restore Rehearsal

Status: `REHEARSAL_NOT_EXECUTED_WAITING_FOR_OWNER_APPROVAL`

No restore operation is implemented or authorized by STG-006 preparation. A
backup file being present does not prove that it is complete, valid, or
recoverable.

## Future rehearsal boundary

Any future restore rehearsal must target a new, disposable Staging-only
environment with all of the following distinct from canonical Staging and
production:

- Compose project name;
- root and data directory;
- PostgreSQL database, user, and credentials;
- loopback port;
- synthetic-only data set; and
- owner-approved exact image SHA.

It must never target the canonical Staging database or a production path. It
must not use production data, `Flyway clean`, manual schema-history edits,
volume deletion, or a destructive Compose operation.

## Required future evidence

1. Owner approval for the disposable target and maintenance window.
2. Metadata-only source backup inventory before content verification.
3. Exact source and target image/SHA record.
4. Explicit schema compatibility decision before any application start.
5. Post-rehearsal record that proves only the approved disposable target was
   involved.

Until a separately approved procedure is executed, the only valid conclusion is
`REHEARSAL_NOT_EXECUTED_WAITING_FOR_OWNER_APPROVAL`.
