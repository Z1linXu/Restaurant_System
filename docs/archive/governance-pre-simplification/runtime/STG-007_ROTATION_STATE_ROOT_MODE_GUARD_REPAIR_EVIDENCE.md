# STG-007 Rotation State-Root Mode Guard Repair Evidence

> Evidence classification: `LOCAL_REPOSITORY_VERIFICATION_WITH_BOUNDED_RUNTIME_TRIGGER`
>
> Runtime mutation: dedicated bare-repository import, exact detached release
> creation, and one-use approval consumption; no env rotation, preflight,
> Docker lifecycle, Flyway, application/business-data, credential, clone,
> restart, or Production mutation
>
> Base: `origin/main@35ccf5cb823bb22b449d8b82baa2f22db2e242df`

## Trigger and preserved runtime

PR #78 merged the releases-parent reconciliation and invalidated candidate
`5c6d8bb7...`. STG-007 restarted Batch A from exact candidate
`35ccf5cb823bb22b449d8b82baa2f22db2e242df`. Fresh retained Staging
`4397f995...` / V8, disabled printing, health/resource/isolation, exact V1-V10
migrations and minimum Production continuity passed. The new candidate import
passed. Bootstrap and release validation then created a clean detached
mode-`0700` release at the exact candidate and consumed the action-specific
approval.

Environment rotation stopped before creating a recovery snapshot, next env or
record because `rotate_environment` separately hardcoded the fixed state
parent to mode `0700`. The established canonical/non-symlink/operator-owned
state parent is mode `0750`; its PostgreSQL leaf remains UID 70/mode `0700`.
Bootstrap cleanup left zero control roots. No rotation blocked marker exists,
and the private env remains byte-identical at digest
`926a075e482215b1e8c0917a96db483f342dfed895adfe122f1c9cccb63fa94c`,
bound to `4397f995...`. The inert exact release and consumed approval record
are preserved as required; they are not replayed or deleted.

## Complete state-root reconciliation

A static audit confirmed that the shared action lock already accepts any
owner-owned state root that is not group/other writable, and approval
consumption does not require parent mode `0700`; the remaining hardcode was
isolated to environment rotation.

The helper now validates the fixed canonical non-symlink state parent during
`validate_inputs`, before acquiring/consuming an approval or creating a
release. Only exact `0700` or established non-group-writable `0750` is
accepted. The exact starting mode and device/inode are revalidated after lock
acquisition, before recovery work and immediately before atomic env replace.
Mode `0775`, every other mode, path/owner/mode/inode drift or symlink
replacement remains fail-closed. Recovery and approval directories remain
private `0700`; env, snapshots, records, locks and approval artifacts remain
`0600`.

Focused tests cover `0750`, `0700`, `0775`, exact-mode drift and the full
release/env approval, recovery and identity matrix. No product, migration,
Compose/runtime configuration, database, Docker, API or Production behavior
changes.

## Independent review

Agent 6 returned `ACCEPT` with no blocking or non-blocking finding. The review
independently confirmed the base, exact `0700`/`0750` contract, fail-closed
ordering before approval consumption and release creation, post-lock and
pre-recovery/pre-replace identity revalidation, TOCTOU and rollback boundaries,
test coverage, partial-runtime Ground Truth, and repository-only scope. Agent 6
also reran the focused release-rotation suite and staged diff check; both
passed.

## Next gate

This repair changes the candidate. Candidate `35ccf5cb...` and its consumed
authorization cannot be reused after merge. Batch A must restart from the next
exact merged main with a new approval; the inert old release is retained only
as historical server state. Batch B remains ineligible.
