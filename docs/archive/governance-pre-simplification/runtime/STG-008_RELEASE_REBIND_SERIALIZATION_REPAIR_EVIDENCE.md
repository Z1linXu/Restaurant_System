# STG-008 Release-Rebind Serialization Dependency Repair Evidence

> Evidence classification: `STAGING_RELEASE_REBIND_TOOLING_DEPENDENCY_REPAIR`
>
> Repository repair base:
> `origin/main@4759a23b1a00d3254936e6c8eeb0ec33012b5145`
>
> Deployed Staging runtime:
> `2837ae88e55142c99c6975f8b6575febffc913a1`
>
> Runtime result: `NO_GO_BEFORE_BATCH_A_MUTATION`
>
> Repair publication state: `IN_MAIN`
>
> Repair PR: #87, reviewed head
> `84457266d1f752cb755b1933b01d849ae62407ed`, merge
> `4b954e09a365fec909ed6da3ddf8fa9f13639cdc`

## Fresh Ground Truth and bounded observation

The Owner-authorized STG-008 recovery continuation began with a fresh
`git fetch origin --prune`. Git Ground Truth exactly matched the conditional
candidate `4759a23b1a00d3254936e6c8eeb0ec33012b5145`; the dirty, behind Owner
workspace remained untouched and an isolated exact-main worktree was used.

Before any release, environment, Docker lifecycle, credential or application
action, read-only machine observation reconfirmed:

- exact clean Staging release/environment SHA `2837ae88...` and private env
  digest `124eb472bf95bc7311b4977beed9f1700a99ad6e371d6a7d390386c9bdd7e1cc`;
- repository migrations and Staging Flyway history exactly V1 through V10,
  ten successful rows, no failed row and no V11+;
- Organization, Store, user, credential, Organization-membership,
  Store-membership and bootstrap-request counts all zero;
- category, station, item, option, table, order and clone-request counts all
  zero; no approved synthetic-login collision; one `OWNER` role; Store
  sequence `last_value=1,is_called=false`;
- exact Staging `db/backend/nginx` identities running with restart count zero,
  health `200/200/200`, Printing `DISABLED/false`, loopback
  `127.0.0.1:18080`, and unchanged state/network/release-mount isolation;
- no scoped AL-003S one-shot container;
- the marker and lock file were each owner `1000`, mode `0600`, contained the
  same single `AL003S_BLOCKED|action_failed_requires_owner_review` record, and
  had the same SHA-256
  `ee810d341b74e258ef85a23d519f54219cc4978fef9d9846ce0b34756514ef1b`;
- permitted Production container/image/start/restart/health fields were
  unchanged and health returned 200, without a Production database or
  business-data read.

No runtime state changed during these observations.

## Deterministic sequencing deadlock

The approved order requires the latest repaired exact release, private-env
binding, formal preflight and Staging-only deploy to pass before blocked-state
recovery. Existing `staging-release-rotation.sh`, however, called the ordinary
`acquire_action_lock` before approval consumption, release creation or env
rotation. That gate correctly rejects either retained marker or any
`AL003S_BLOCKED|...` lock line.

Formal preflight/deploy require the new exact release and env, while the Owner
sequence forbids clearing either reviewed record early. The repository
contains no second release path. Therefore current main cannot complete Batch
A through a reviewed entry; manually creating a release, editing the env, or
deleting/truncating the records would be an unauthorized bypass.

The Coordinator stopped before candidate import, approval creation or
consumption, release creation, env rotation, build, deploy, Flyway, recovery,
one-shot, password request or data mutation.

## Minimal fail-closed repair

The repair separates the existing lock into:

1. `acquire_staging_serialization_lock`, which preserves the fixed state path,
   symlink, owner/mode, exact lock filename and nonblocking flock checks; and
2. `acquire_action_lock`, which still calls that mutex and then rejects either
   blocked record for every ordinary action.

Only release rotation gains `prepare-recovery-release-env`. It:

- takes the same shared mutex, so it cannot overlap another action;
- requires both fixed blocked files, regular/non-symlink, invoking-user owned,
  mode `0600`, with one identical valid record;
- includes both SHA-256 digests in the one-use Owner approval fingerprint;
- revalidates the records before release creation, before env rotation and
  after rotation;
- leaves both files byte-for-byte unchanged;
- retains the existing exact bare-repository/ref, private release, approval
  consumption, four-field-only env rotation and rollback guards.

No generic `ignore-blocked` option exists. Ordinary `prepare-release-env`, all
STG-005A/STG-005B actions, runtime collection/restart and Owner/API actions
remain blocked. The repair adds no migration, backend/frontend/Android product
behavior, runtime secret, credential, Docker lifecycle action, Flyway action,
business-data writer, Production behavior or blocked-state clear operation.

## Verification and runtime boundary

Focused shell verification covered ordinary-action rejection, unsafe/malformed/
symlink blocked records, digest-bound recovery release, byte-for-byte record
retention, exact bundle delegation, four-field env-only rotation, approval
replay and existing runtime guards. All 13 deployment shell regression files,
Bash syntax, `git diff --check`, changed-file Markdown links, the
high-confidence secret scan, scope scan and governance drift scan passed.
`shellcheck` was unavailable, so Bash syntax was the recorded static-shell
substitute. Agent 6 first blocked unsafe existing-lock mode repair, acceptance
of a trailing unterminated record and a post-commit drift window. All three
findings were corrected and regression-tested; the final independent result
was `ACCEPT` with no blocking finding.

The repair was published through PR #87 after GitHub reconfirmed `base=main`,
the unchanged reviewed head, one expected commit, clean mergeability, no
conflict and no failed check. Its merge is an `IN_MAIN` repository fact only:
no release, private-env rotation, preflight, build, deploy, Flyway, blocked-
state recovery, one-shot, credential request or business-data action occurred.

This repair is runtime-sensitive tooling. Its merge advanced exact main, so
the Owner's conditional authorization for exact
`4759a23b1a00d3254936e6c8eeb0ec33012b5145` cannot be silently rebound to the
new merge. After qualifying publication, fresh Git Ground Truth and a new
exact-SHA Owner Runtime approval are required before candidate import or any
Batch A action. The runtime-sensitive main floor is now
`4b954e09a365fec909ed6da3ddf8fa9f13639cdc`; a later governance-only main may
be selected only after a fresh fetch proves it remains this commit's
descendant. STG-008 remains `NO_GO`; no password may be requested. The unique
stop state is
`STG-008_RELEASE_REBIND_REPAIR_IN_MAIN_WAITING_FOR_NEW_EXACT_SHA_STAGING_REBIND_AND_BLOCKED_STATE_RECOVERY_OWNER_RUNTIME_APPROVAL`.
