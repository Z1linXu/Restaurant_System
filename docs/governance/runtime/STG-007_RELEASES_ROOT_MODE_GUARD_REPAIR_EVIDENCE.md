# STG-007 Releases-Root Mode Guard Repair Evidence

> Evidence classification: `LOCAL_REPOSITORY_VERIFICATION_WITH_BOUNDED_RUNTIME_TRIGGER`
>
> Runtime mutation: dedicated bare-repository candidate object/ref import only;
> no release, environment rotation, preflight, Docker lifecycle, Flyway,
> application, credential, business-data, clone, restart, or Production mutation
>
> Base: `origin/main@5c6d8bb70d74756cc7fe3f76b2d43cb07c6e6f33`

## Trigger and preserved runtime

PR #77 merged the state-parent guard reconciliation and invalidated candidate
`e6fac236...`. STG-007 restarted Batch A from exact candidate
`5c6d8bb70d74756cc7fe3f76b2d43cb07c6e6f33`. Fresh checks again passed for
retained Staging `4397f995...` / Flyway V8, disabled printing, loopback health,
resource, state/mount/network isolation, exact V1-V10 repository migrations,
and minimum Production continuity at `4667f3c...`. The new candidate was
double-pinned, object-only fetched and compare-and-swap bound in the dedicated
mode-`0700` repository.

The corrected bootstrap passed its control-root guard and delegated to the
reviewed release helper. That helper stopped in `validate_inputs`, before
approval consumption or release/env mutation, because it required the fixed
`releases` parent to use mode `0700`. The real directory is canonical,
non-symlink, operator-owned mode `0750`, contains three retained historical
release directories, and is not group/world writable. The task control root
was removed by bootstrap cleanup. The unconsumed mode-`0600` approval was
identity-checked and removed. Candidate release remained absent and the
private env remained bound to `4397f995...`.

## Deterministic repair boundary

The fixed release parent is a source containment directory. Exact mode `0750`
permits group traversal/read but no group/world write, so it cannot replace or
create release entries. The helper now accepts only exact `0700` or established
`0750`, while requiring the fixed canonical non-symlink path and operator
owner. It records the exact initial mode and device/inode and revalidates them
immediately before and after `git worktree add`. Mode `0775`, any other mode,
path/owner/mode/inode drift, and symlink replacement remain fail-closed. The
new candidate release itself remains mode `0700` and must be a clean detached
worktree at the exact approved SHA.

Focused tests cover `0750` and `0700`, rejection of `0775`, exact-mode drift,
and the existing release/env approval, recovery, private-file and identity
matrix. This changes no product, migration, Compose/runtime configuration,
secret, database, Docker, API or Production behavior.

Agent 6 independently returned `ACCEPT` with no blocking or non-blocking
finding. The review confirmed the fixed/canonical/non-symlink owner boundary,
non-group-writable `0750`, exact pre/post mode and device/inode checks, private
exact release, focused coverage, accurate runtime Ground Truth and single-layer
scope. The bounded signal-fixture wait adjustment was confirmed as test-only.

## Next gate

This repair changes the candidate. Candidate
`5c6d8bb70d74756cc7fe3f76b2d43cb07c6e6f33` and its old authorization binding
expire after merge; the unconsumed approval artifact was already removed.
STG-007 Batch A must restart from the next exact merged main. Batch B remains
ineligible until every mandatory Batch A gate passes for that new candidate.
