# STG-007 State-Root Mode Guard Repair Evidence

> Evidence classification: `LOCAL_REPOSITORY_VERIFICATION_WITH_BOUNDED_RUNTIME_TRIGGER`
>
> Runtime mutation: dedicated bare-repository permission/object/ref import only;
> no release, environment rotation, preflight, Docker lifecycle, Flyway,
> application, business-data, credential, login, clone, or Production mutation
>
> Base: `origin/main@e6fac236c7620cd2f579d2a180367f4f753a6d42`

## Trigger and preserved runtime

PR #76 entered `main` with the reviewed exact-Git release bootstrap. STG-007
therefore expired candidate `b93d8ef...`, selected exact candidate
`e6fac236c7620cd2f579d2a180367f4f753a6d42`, and restarted Batch A from the
beginning.

The fresh bounded baseline passed for retained Staging `4397f995...` / Flyway
V8, disabled printing, loopback health, mount/network/state isolation,
resource headroom, exact repository migrations V1-V10, and minimum Production
continuity at `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`. The approved candidate
object was imported into the dedicated bare repository by the reviewed
double-pin, object-only-fetch and compare-and-swap procedure; the repository
was secured from mode `0775` to `0700`.

The bootstrap then stopped before delegation because it required the fixed
Staging `state` parent to use mode `0700`. The observed and previously accepted
Staging topology uses an operator-owned, canonical, non-symlink `state` parent
at mode `0750`; its private PostgreSQL leaf remains UID 70/mode `0700`. No
release was created and the private environment remained bound to
`4397f995...`. The exact unexecuted control root and unconsumed approval file
created for this attempt were identity-checked and removed. A follow-up
sanitized query reconfirmed Staging `4397f995...` / Flyway V8.

## Deterministic repair boundary

The state parent is a containment directory, not a secret file. Mode `0750`
does not permit group or world writes, so it cannot replace the owner-only
task root. The bootstrap now accepts only exact mode `0700` or the established
`0750`, while retaining every existing canonical-path, owner, non-symlink and
inode check. It records the exact initial state-parent mode and requires that
same mode during cleanup. Mode `0775`, any group/world-writable mode, owner or
inode drift, and cleanup failure remain fail-closed.

Focused fixtures exercise success and cleanup beneath `0750`, rejection of
`0775`, strict task-root/source ownership, mode/inode drift, removal failure,
signals, repository identity, exact Git source, and delegation. This repair
does not weaken the private control root (`0700`), bootstrap source (`0700`),
environment (`0600`), approval (`0600`), dedicated repository (`0700`), or
PostgreSQL leaf (`0700`) contracts.

Agent 6 independently returned `ACCEPT` with no blocking or non-blocking
finding. The review confirmed that `0750` is non-group-writable, every
owner/canonical/symlink/inode/private-root constraint remains intact, exact
starting-mode drift fails closed, the focused matrix passes, governance is
accurate, and the staged diff contains no application, migration or runtime
configuration expansion.

## Next gate

This repair changes the candidate. Candidate
`e6fac236c7620cd2f579d2a180367f4f753a6d42` and its prior authorization binding
expire after the repair enters main; the unconsumed approval artifact itself
was already removed. STG-007 Batch A must restart again from the next exact
merged main. Batch B remains ineligible until every mandatory Batch A gate
passes for that new candidate.
