# OPS-001 Staging Secret-Safe Tooling

Status: repository tooling complete; runtime use remains Owner-gated.

This package closes the three repository tooling gaps recorded by STG-006. It
does not authorize or perform SSH, deployment, Flyway, bootstrap, credential
creation, login, API acceptance, restart, or Production access.

## Tools

| Tool | Safe default | Explicit runtime actions |
|---|---|---|
| `staging-release-control-bootstrap.sh` | verifies an exact Git-materialized control source and private temporary bundle | delegates unchanged arguments to `staging-release-rotation.sh`; it has no release/runtime action of its own |
| `staging-release-rotation.sh` | validates exact SHA, private env and dedicated bare repository | `prepare-release-env`: ordinary detached worktree plus atomic four-field identity rotation; `prepare-recovery-release-env`: the same release/env operation while retaining one exact reviewed AL-003S blocked pair |
| `staging-runtime-evidence.sh` | validates the exact release/env/preflight binding | `collect-evidence`; `same-image-restart` using existing containers only |
| `staging-owner-acceptance-client.sh` | validates exact release, private env and approved preflight identity | `prepare-target`; `clone-acceptance` against loopback APIs |

The tools share `staging-ops-common.sh` for fixed environment identity,
mode-0600 files, digests, approval fingerprints and one-use approval records.
They reuse the official Staging deploy validation, AL-003S release/preflight/
readiness validation, isolated Docker CLI state, project fingerprinting, lock
and blocked-marker behavior. They do not create a second deployment or clone
engine.

Every runtime-capable helper serializes through the existing fixed
`state/al003s-acceptance.lock`. Release/env rotation, runtime evidence/restart,
and Owner/API batches therefore cannot overlap. Ordinary actions still reject
either retained blocked record before exact-runtime validation or mutation.
The sole exception is `prepare-recovery-release-env`, which exists because a
reviewed backend/tool repair must be deployed before those records may be
recovered. It obtains the same mutex, requires both exact owner-only records,
binds their digests into its one-use approval, and proves they remain
byte-for-byte unchanged. It does not clear blocked state or authorize deploy,
recovery, one-shot, credential, or business-data work.

## Approval artifact

Every runtime action requires a new private file directly under
`/srv/restaurant-pos/staging/evidence`, owned by the operator and mode `0600`:

```text
OPS001_APPROVAL|STATUS|OWNER_APPROVED
OPS001_APPROVAL|ENVIRONMENT|restaurant-pos-staging
OPS001_APPROVAL|EXPIRES_AT_EPOCH|<within-24-hours>
OPS001_APPROVAL|ACTION|<exact-action>
OPS001_APPROVAL|APPROVED_SHA|<full-40-character-sha>
OPS001_APPROVAL|ENV_SHA256|<current-private-env-digest>
OPS001_APPROVAL|REQUEST_FINGERPRINT|<tool-specific-canonical-fingerprint>
OPS001_APPROVAL|REFERENCE|<external-owner-review-reference>
```

The exact approval digest is supplied separately. A validated digest is
atomically recorded under `state/ops001-approvals`; replay is rejected even if
the first action fails. This procedural artifact binds an external Owner
decision. It is not a cryptographic signature and does not replace restricted
server access.

## Release and environment boundary

The retained pre-OPS-001 release does not contain the release helper, while
the helper itself is responsible for creating the next release. Use the
reviewed control bootstrap to close that first-use boundary; never create the
candidate release manually.

First, import only the exact approved `main` into the already-existing
dedicated bare repository. The import is deliberately split into an immutable
trust-root check, a pinned remote check, an object-only fetch, and a
compare-and-swap ref update. Do not replace it with a normal fetch refspec:

```bash
set -euo pipefail
repository=/srv/restaurant-pos/staging/repository.git
approved_sha=<full-approved-main-sha>
expected_origin=https://github.com/Z1linXu/Restaurant_System.git
zero_oid=0000000000000000000000000000000000000000

test "$repository" = /srv/restaurant-pos/staging/repository.git
test -d "$repository"
test ! -L "$repository"
test "$(cd -P "$(dirname "$repository")" && pwd)/$(basename "$repository")" = "$repository"
current="$repository"
while test "$current" != /; do
  test ! -L "$current"
  current="$(dirname "$current")"
done
test "$(stat -c '%u' "$repository")" = "$(id -u)"
test "$(git --git-dir="$repository" rev-parse --is-bare-repository)" = true
test "$(git --git-dir="$repository" remote | wc -l | tr -d ' ')" = 1
test "$(git --git-dir="$repository" remote)" = origin
test "$(git --git-dir="$repository" remote get-url --all origin | wc -l | tr -d ' ')" = 1
test "$(git --git-dir="$repository" remote get-url origin)" = "$expected_origin"
repository_identity="$(stat -c '%d:%i' "$repository")"

prior_main="$(git --git-dir="$repository" show-ref --verify --hash \
  refs/remotes/origin/main 2>/dev/null || true)"
test -z "$prior_main" || test "${#prior_main}" = 40
expected_old="${prior_main:-$zero_oid}"
other_refs_before="$(git --git-dir="$repository" for-each-ref \
  --format='%(refname) %(objectname)' | \
  awk '$1 != "refs/remotes/origin/main"' | sha256sum | awk '{print $1}')"
if test -e "$repository/FETCH_HEAD"; then
  fetch_head_before="present:$(sha256sum "$repository/FETCH_HEAD" | awk '{print $1}')"
else
  fetch_head_before=absent
fi

remote_line="$(git --git-dir="$repository" ls-remote --refs origin refs/heads/main)"
test "$remote_line" = "$(printf '%s\t%s' "$approved_sha" refs/heads/main)"

chmod 700 "$repository"
test "$(stat -c '%d:%i' "$repository")" = "$repository_identity"
test "$(stat -c '%u' "$repository")" = "$(id -u)"
test "$(stat -c '%a' "$repository")" = 700
test ! -L "$repository"
test "$(git --git-dir="$repository" rev-parse --is-bare-repository)" = true
test "$(git --git-dir="$repository" remote)" = origin
test "$(git --git-dir="$repository" remote get-url --all origin | wc -l | tr -d ' ')" = 1
test "$(git --git-dir="$repository" remote get-url origin)" = "$expected_origin"

git --git-dir="$repository" fetch --no-tags --no-write-fetch-head origin \
  "$approved_sha"
remote_line="$(git --git-dir="$repository" ls-remote --refs origin refs/heads/main)"
test "$remote_line" = "$(printf '%s\t%s' "$approved_sha" refs/heads/main)"
git --git-dir="$repository" cat-file -e "$approved_sha^{commit}"
test "$(stat -c '%d:%i' "$repository")" = "$repository_identity"
test "$(stat -c '%u' "$repository")" = "$(id -u)"
test "$(stat -c '%a' "$repository")" = 700
test ! -L "$repository"
test "$(git --git-dir="$repository" rev-parse --is-bare-repository)" = true
test "$(git --git-dir="$repository" remote)" = origin
test "$(git --git-dir="$repository" remote get-url --all origin | wc -l | tr -d ' ')" = 1
test "$(git --git-dir="$repository" remote get-url origin)" = "$expected_origin"

git --git-dir="$repository" update-ref refs/remotes/origin/main \
  "$approved_sha" "$expected_old"
test "$(git --git-dir="$repository" show-ref --verify --hash \
  refs/remotes/origin/main)" = "$approved_sha"
test "$(git --git-dir="$repository" for-each-ref \
  --format='%(refname) %(objectname)' | \
  awk '$1 != "refs/remotes/origin/main"' | sha256sum | awk '{print $1}')" = \
  "$other_refs_before"
if test "$fetch_head_before" = absent; then
  test ! -e "$repository/FETCH_HEAD"
else
  test "$fetch_head_before" = \
    "present:$(sha256sum "$repository/FETCH_HEAD" | awk '{print $1}')"
fi
```

This is neither a clone nor a Production checkout operation. Stop if the
canonical path, owner, repository inode, bare/origin identity, either pinned
remote result, object, ref CAS, or exact SHA differs. A pre-fetch remote
mismatch performs no mutation. A post-fetch mismatch may leave only
unreferenced Git objects; it must leave `refs/remotes/origin/main`, all other
refs, and `FETCH_HEAD` unchanged.

Next, materialize only the approved bootstrap blob into a fresh owner-only
control root and invoke it with the release helper's unchanged arguments:

```bash
staging_root=/srv/restaurant-pos/staging
control_root="$(mktemp -d "$staging_root/state/ops001-release-control.XXXXXX")"
chmod 700 "$control_root"
control="$control_root/staging-release-control-bootstrap.sh"
git --git-dir="$repository" show \
  "$approved_sha:deployment/cloud/staging-release-control-bootstrap.sh" >"$control"
chmod 700 "$control"

"$control" --execute-runtime --action <prepare-release-env-or-prepare-recovery-release-env> \
  --approved-sha "$approved_sha" \
  --env-file "$staging_root/config/.env.staging" \
  --approval <private-approval-file> \
  --approval-sha256 <approval-digest>
```

The bootstrap verifies its own digest against the exact candidate Git blob,
the fixed bare-repository/ref identity, the private control root, and the
symlink-free extracted `deployment/cloud` bundle. It then delegates to the
reviewed rotation helper and deletes only its exact task-owned temporary
control root on every normal/error/signal path. The root must use the exact
six-alphanumeric-character `mktemp` suffix and initially contain only the
fixed-name bootstrap source. Cleanup revalidates parent/root owner, mode and
inode. The fixed Staging state parent may be only owner-owned mode `0700` or
the established non-group-writable mode `0750`; its exact starting mode must
remain unchanged through cleanup. Group/world-writable modes remain `NO_GO`.
An identity drift or removal failure returns `NO_GO` without deleting an
untrusted target. A caller should remove an unexecuted control root if
materialization itself fails. It never fetches, clones, builds, starts,
migrates, reads Production, or creates a release by a second path.

`prepare-release-env` requires the exact commit to already exist in the
dedicated bare repository at `/srv/restaurant-pos/staging/repository.git`. It
does not fetch or clone. It creates only
`releases/<approved-sha>` as a clean detached worktree, never from the
Production checkout. The fixed releases parent must be canonical,
non-symlink, operator-owned and exact mode `0700` or the established
non-group-writable `0750`. Its starting mode and inode are revalidated before
and after worktree creation; `0775`, owner/mode/inode drift, or path
replacement is `NO_GO`. The new exact release itself is always mode `0700`.

Ordinary `prepare-release-env` continues to reject a blocked marker or a
blocked line in the shared lock file. The recovery prerequisite action is
accepted only when both fixed files are regular, non-symlink, invoking-user
owned, mode `0600`, contain the same single syntactically valid
`AL003S_BLOCKED|...` record, and remain unchanged before release creation,
before environment rotation, and after rotation. Their SHA-256 digests are
part of the action approval fingerprint. Missing, extra, mismatched, malformed,
unsafe, or drifting records are `NO_GO`; no generic ignore-blocked option
exists.

The environment rotation copies the prior private environment into the
owner-only recovery directory, prepares a new mode-0600 file, proves that only
these keys changed, and atomically replaces `.env.staging`:

- `STAGING_COMMIT_SHA`;
- `BACKEND_IMAGE`;
- `FRONTEND_IMAGE`;
- `VITE_APP_BUILD_VERSION`.

All database/JWT values, isolation fields, resource limits, profiles and
printing guards remain byte-identical. The official `staging-deploy.sh
--validate` must pass after rotation; otherwise the prior environment is
restored. The sanitized recovery record contains digests only.

The rotation state parent uses the same fixed/canonical/non-symlink,
operator-owned exact `0700` or established non-group-writable `0750` contract.
Its starting mode and device/inode are validated before approval consumption
or release creation and revalidated before recovery preparation and before the
atomic environment replacement. Recovery, approval-consumption, lock and
record children remain private `0700` directories or `0600` files. Mode
`0775`, any other mode, or state-root identity drift remains `NO_GO`.

## Restart and Flyway evidence boundary

Both runtime evidence actions require fresh AL-003S preflight/readiness
evidence and an action-specific OPS-001 approval. The collector emits only the
exact SHA/env digest, container and immutable image IDs, restart counts,
project fingerprint, Flyway row count/max version/digest and PASS status. Raw
Flyway rows must form one successful, ordered and non-duplicated history whose
version/script/checksum tuples exactly match the approved release's reviewed
checksum manifest. Focused tests recompute that manifest from the repository
SQL with Flyway 10.10.0's line-normalized CRC32 algorithm; missing, extra,
failed, renamed or checksum-mismatched scripts fail closed.
The collector compares the exact PostgreSQL `success::text` token `true`;
`false`, the abbreviated display token `t`, blank or any other spelling is
rejected. This keeps the validator aligned with its own query instead of a
mock-only `psql` display assumption.
It never emits database credentials or raw environment values.

Project fingerprints explicitly distinguish `healthy` from
`NO_HEALTHCHECK`. Docker omits `State.Health` for services without a configured
healthcheck, so the shared collector first enumerates the state-map keys. Only
a genuinely absent `Health` key becomes `NO_HEALTHCHECK`. If the key exists,
its `Status` must be present, syntactically valid and exactly `healthy`;
present-empty, invalid or unhealthy state, missing services and non-running
services remain `NO_GO`.

`same-image-restart` serializes with AL-003S, captures the existing container,
image, project and Flyway identities, and invokes only:

```text
stop nginx backend db
start db
start backend
start nginx
```

Container `running` without a configured Docker healthcheck is only a lifecycle
signal; it is not application readiness. After the ordered starts, the helper
uses a fixed bounded loopback window and requires HTTP 200 from backend health,
frontend root and `/ws/info`. Transport failure plus 502/503/504 may retry
inside that window; redirects, authorization errors, missing routes and all
other statuses fail immediately. It then requires the same container IDs,
image IDs, Flyway digest and project fingerprint. The post-mutation flag is
cleared only after complete PASS evidence is emitted.

Every nonzero process exit after stop begins, including an explicit fail-closed
`die`, health timeout, identity/Flyway/project drift, signal or evidence-write
failure, writes the shared blocked state before cleanup releases the action
lock. There is no `up`, `down`, `rm`, image pull/build, Flyway command, volume
action or Production command.

## Owner/API secret boundary

The client is fixed to `http://127.0.0.1:18080/api/v1`, disables proxy use and
ambient curl configuration, does not follow redirects, and reuses only the
existing login, me, workspace, Owner overview, onboarding, menu-clone
validate/execute and logout contracts. Before either API action it reuses the
runtime evidence validator to bind the private environment and supplied
preflight evidence to the exact running release/image. It does not use
`X-User-Id` or invent an authorization path.

Passwords, access/refresh tokens, staff initial passwords and raw idempotency
keys enter through one inherited non-interactive file descriptor. They live
only in a private mode-0700 temporary root with mode-0600 request, response and
curl-config files, are never arguments or evidence, and are removed on exit.
Non-login responses containing secret-shaped fields are rejected.

`prepare-target` proves Owner identity/workspace/overview, performs onboarding
and exact replay, proves inherited target access, and stops after the existing
clone validator confirms the reviewed `4/3/17/74` counts. `clone-acceptance`
requires a later, separately approved batch for source Store `1` and profile
`CHINATOWN_MENU_2026_02_02`; it revalidates before execute, proves revision
`+1`, and proves exact replay. Each action has a separate Owner approval and
fresh secret input.

## Local verification

Only mock/local tests are permitted in OPS-001:

```bash
deployment/cloud/tests/test_staging_release_control_bootstrap.sh
deployment/cloud/tests/test_staging_repository_candidate_import.sh
deployment/cloud/tests/test_staging_release_rotation.sh
deployment/cloud/tests/test_staging_runtime_evidence.sh
deployment/cloud/tests/test_staging_owner_acceptance_client.sh
```

The future runtime order remains separate Owner gates:

`release/env -> deploy/Flyway -> bootstrap/source -> login/onboarding -> clone -> restart`

No successful repository test promotes any of these actions to runtime
authorization.
