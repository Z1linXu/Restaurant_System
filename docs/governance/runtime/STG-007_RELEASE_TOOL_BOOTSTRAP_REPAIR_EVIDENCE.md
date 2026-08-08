# STG-007 Release Tool Bootstrap Repair Evidence

> Evidence classification: `LOCAL_REPOSITORY_VERIFICATION`
>
> Runtime mutation: `NO`
>
> Base: `origin/main@b93d8efdbd699333d73d9ffcc29e8f8443e51764`

## Trigger

After PR #75 repaired and merged the formal-preflight upgrade-port guard,
STG-007 restarted Batch A from the new exact main. A fresh bounded read-only
baseline confirmed retained Staging `4397f995...` / Flyway V8, printing
disabled, loopback health, project/mount isolation, resource headroom, and
unchanged minimum Production continuity.

The dedicated Staging bare repository was still mode `0775`, lacked the new
candidate object and `refs/remotes/origin/main`, and the retained pre-OPS-001
release did not contain `staging-release-rotation.sh`. The reviewed rotation
helper deliberately does not fetch and is itself responsible for creating the
candidate release. Therefore no reviewed control path could invoke it without
first manually creating a release or improvising a code-transfer path.

Execution stopped before repository permission/ref changes, approval-file
creation, release creation, environment rotation, formal preflight, Docker
lifecycle, image build, Flyway, bootstrap, credential, login, clone, or
Production mutation.

## Repair boundary

`staging-release-control-bootstrap.sh` is a control-path adapter, not a second
release engine. It must itself be materialized from the approved commit in the
fixed dedicated bare repository into an owner-only task-specific control root.
It verifies:

- fixed environment file and full candidate SHA arguments;
- fixed bare-repository path, owner, mode `0700`, commit object, and exact
  `refs/remotes/origin/main` binding;
- owner-only, non-symlink control root and executable;
- its own SHA-256 against the approved Git blob;
- a symlink-free `deployment/cloud` archive from that exact commit; and
- exact extracted digests for the rotation helper and both sourced libraries.

Only then does it delegate the unchanged arguments to the existing reviewed
`staging-release-rotation.sh`. Its exact temporary control root is removed on
success, error, interrupt, or termination. It performs no fetch, clone,
release creation, environment rotation, Docker, Flyway, API, business-data, or
Production action itself.

The runbook separately defines the bounded prerequisite that proves the
existing repository's canonical path, owner, inode, bare identity, sole exact
origin, and pinned remote `main` before mutation. It then fetches only the
approved commit object with `--no-write-fetch-head`, repeats the pinned remote
and trust-root checks, and compare-and-swap updates only
`refs/remotes/origin/main`. A remote mismatch cannot rewrite the old ref or
`FETCH_HEAD`; the exact candidate object/ref must be proved before the
bootstrap may run.

## Verification and next gate

The focused fixtures cover pinned candidate import, pre- and post-fetch remote
mismatches with no ref/FETCH_HEAD overwrite, compare-and-swap contention,
exact delegation, success/help/invalid/delegate-failure/signal cleanup, strict
control-root suffix/source/initial-content ownership, cleanup mode/inode drift
and removal failure, tampered bootstrap source, unsafe repository/control
modes, wrong origin/ref binding, and an archived symlink. Existing OPS-001
release/env rotation, preflight, Staging guard, deploy, runtime evidence, link,
secret, drift, diff, and independent review gates remain mandatory.

Agent 6's independent review iterations blocked publication because the
initial runbook used a ref-writing fetch before pinning remote `main`,
trust-root validation was incomplete, cleanup traps were installed after
argument parsing, the task-root predicate was broad, cleanup failure could
retain a success status, and race tests did not cover the second pin/CAS. The
repair now uses two pinned `ls-remote` checks, an object-only fetch, a CAS ref
update, repeated repository identity checks, an exact initially single-source
control root, fail-closed cleanup, and all named race fixtures. Final
independent review then returned `ACCEPT` with no blocking or non-blocking
finding. Agent 6 confirmed the trust checks, double remote pin, object-only
fetch, CAS, strict task root, fail-closed cleanup, race coverage, one-layer
scope, Ground Truth, stop state, and runtime boundaries.

This repair changes the candidate again. The prior candidate
`b93d8efdbd699333d73d9ffcc29e8f8443e51764` expires after the repair enters
main. STG-007 Batch A must restart from the next exact merged main. Batch B
remains ineligible, and this repository repair authorizes no runtime action.
