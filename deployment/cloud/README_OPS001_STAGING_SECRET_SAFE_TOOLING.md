# OPS-001 Staging Secret-Safe Tooling

Status: repository tooling complete; runtime use remains Owner-gated.

This package closes the three repository tooling gaps recorded by STG-006. It
does not authorize or perform SSH, deployment, Flyway, bootstrap, credential
creation, login, API acceptance, restart, or Production access.

## Tools

| Tool | Safe default | Explicit runtime actions |
|---|---|---|
| `staging-release-rotation.sh` | validates exact SHA, private env and dedicated bare repository | `prepare-release-env`: detached worktree plus atomic four-field identity rotation |
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
and Owner/API batches therefore cannot overlap. Lock contention or an existing
blocked marker fails closed before exact-runtime validation or mutation.

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

`prepare-release-env` requires the exact commit to already exist in the
dedicated bare repository at `/srv/restaurant-pos/staging/repository.git`. It
does not fetch or clone. It creates only
`releases/<approved-sha>` as a clean detached worktree, never from the
Production checkout.

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
It never emits database credentials or raw environment values.

`same-image-restart` serializes with AL-003S, captures the existing container,
image, project and Flyway identities, and invokes only:

```text
stop nginx backend db
start db
start backend
start nginx
```

It requires the same container IDs, image IDs, Flyway digest, loopback health
and project fingerprint afterward. Failure after stop begins writes the shared
blocked state and requires Owner-reviewed recovery. There is no `up`, `down`,
`rm`, image pull/build, Flyway command, volume action or Production command.

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
deployment/cloud/tests/test_staging_release_rotation.sh
deployment/cloud/tests/test_staging_runtime_evidence.sh
deployment/cloud/tests/test_staging_owner_acceptance_client.sh
```

The future runtime order remains separate Owner gates:

`release/env -> deploy/Flyway -> bootstrap/source -> login/onboarding -> clone -> restart`

No successful repository test promotes any of these actions to runtime
authorization.
