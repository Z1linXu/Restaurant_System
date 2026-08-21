# STG-007 Restart Readiness and Fail-Closed Repair Evidence

> Evidence classification: `STAGING_RUNTIME_OBSERVATION_PLUS_REPOSITORY_REPAIR`
>
> Base: `origin/main@63600b13b10a5549d9095a03c94e69a9f880af9f`
>
> Runtime boundary: the Owner-authorized V10-to-V10 continuation deployed only
> exact Staging candidate `63600b13...`; read-only runtime collection passed,
> but the same-image restart returned `NO_GO` before PASS evidence. No second
> restart, synthetic write, credential, login, Store read, clone or Production
> mutation followed.

## Exact continuation ground truth

PR #81 placed the Flyway success-token repair in `main` at
`63600b13b10a5549d9095a03c94e69a9f880af9f`. The continuation restarted rather
than reusing the prior `39fa284b...` chain. Fresh evidence reconfirmed the
previous Staging SHA `39fa284b...`, Flyway V10 with no pending migration,
health `200/200/200`, printing `DISABLED` / `false`, loopback exposure,
isolated state/network/mounts, and Production continuity at
`4667f3c35f85c9f8538f82789d9df1531d4fbc9e`.

The exact candidate was imported into the dedicated bare repository and bound
to a clean detached release and private environment. The authoritative
bindings are:

| Artifact | SHA-256 / identity | Result |
|---|---|---|
| exact release/build/deployed SHA | `63600b13b10a5549d9095a03c94e69a9f880af9f` | `PASS` |
| private environment | `0fbcd4038b203cc9ca68f78777eb7dc6ac08be6a67fd26393d5ac4aba8947a94` | `PASS` |
| V10 continuation entry | `3264d42b645573d1be680ff46ec862f51a183ed9a2147fe6ef8272f1531520d9` | `PASS` |
| formal preflight | `c7f505a7e475575e3730ba166b4b6ab5acf2d10a890ce99a94df39a7ae422f18` | `PASS` |
| post-deploy readiness | `7c02f27e5c7e89e637efd738732cb7bccfff1addd92d8e1222b4346cab5e6215` | `PASS` |
| runtime collection | `3c1a6f27544a46fb609996ce95cc9c4550cb26ec5706dc9b8c3c77f33fcf955b` | `PASS` |
| restart readiness | `8d5a1aa4ea5f0ebf9a6551dea731cb24993544c111106873c3c8334242bb0df4` | `PASS` |

The repaired collector verified exact V1-V10 version/script/checksum rows,
`count=10`, `max_version=10`, and Flyway history digest
`b07616f0316934f32f83b4d1e242bccb3d97a3a4e3258c1e02f780ba04d9ec11`.
The deployed identities were:

| Service | Container ID | Immutable image ID |
|---|---|---|
| `db` | `b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5` | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` |
| `backend` | `3d0ad5b3922709d3aa455fe8feabc1d4de9aecfb510700a52dbb4248468ce609` | `sha256:c81c32ef0623f232868b2682cc4a1b558d72afa3ba3f2c0f6f1c61fe948fb82f` |
| `nginx` | `3bc89f1cccd9efb9850459a3184565b081460b25b5709e80a56691754977b044` | `sha256:d5af48bec53ac7ac14b3057db1500b24151b5b383d4c1784dd47d5f8a67640ab` |

The Staging project fingerprint was `8efed1b7...`; the Production fingerprint
remained `35765c02...` before the restart action.

## Same-image restart `NO_GO`

The fresh one-use restart approval digest was
`100c3c1de7f18170de2e98c5d901a2b9a41aa4da5adc4006c80fea1d52ca8e7a`.
The reviewed helper stopped and started only the existing Staging containers
in the fixed order. All three exact container and immutable image IDs above
were retained and restart counts remained zero. It then performed one
immediate backend health request. Because backend and nginx have no Docker
healthcheck, their `running` state arrived before the Spring application was
ready; nginx returned 502 and the helper emitted:

```text
AL003S_ACCEPTANCE|NO_GO|loopback health must return HTTP 200
```

The redirected restart evidence is empty and has SHA-256
`e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`.
It is failure context and must never be promoted to PASS. The approval is
consumed and cannot be replayed.

About 36 seconds after backend start, bounded read-only diagnosis observed the
Spring application complete startup. Frontend, backend health and `/ws/info`
then returned `200/200/200`; Flyway remained `10/10/0 failed`; the same exact
containers/images remained running; printing and Staging isolation remained
unchanged. Production `cloud` container IDs/images/start times/restart counts
were unchanged and its port-80 health endpoint returned 200. Runtime recovery
does not retroactively turn the failed restart action into PASS evidence.

## Second deterministic defect

No `state/al003s-acceptance.blocked` marker was present after the failure.
`die()` exits explicitly, while the executable relied on an `ERR` trap to call
its restart-failure handler. Bash does not run `ERR` for explicit `exit`, so
the helper released cleanup without persisting the documented blocked marker.
The consumed approval still prevented replay, but it does not replace the
required shared fail-closed state.

## Bounded repository repair

The repair changes only the OPS-001 runtime helper, focused mocks, runbook and
governance:

1. after ordered same-container starts, require bounded HTTP-200 convergence
   for backend health, frontend root and `/ws/info`;
2. retry only transport failure and 502/503/504 during the fixed startup
   window; fail all other HTTP states immediately;
3. replace ERR-only failure handling with one nonzero `EXIT` handler so
   explicit exits, command failures, signals and evidence failures after stop
   begins persist blocked state before cleanup;
4. clear the mutation flag only after complete `AFTER_RESTART` PASS evidence.

The fix adds no healthcheck, container, image, build, pull, deployment,
migration, Flyway action, runtime configuration, application/API behavior,
business-data path or Production operation.

## Verification

- focused runtime collector/restart test: `PASS`, including transient 502 and
  transport recovery, permanent 502 bounded failure, immediate 404 rejection,
  all three loopback endpoints, unchanged stop/start order, exact container/
  image/Flyway/project checks, and a real mode-0600 blocked marker plus lock
  record before cleanup on explicit post-mutation exit;
- readiness, Staging guard, deploy CLI-state, server preflight/control,
  synthetic acceptance/runtime guards, Owner client, release rotation,
  release bootstrap and exact candidate-import regressions: `PASS`;
- syntax validation for every `deployment/cloud/*.sh`: `PASS`;
- `git diff --check`, Markdown links, bounded added-line secret scan, exact
  scope and governance drift checks: `PASS`;
- `test_staging_local_rehearsal.sh` correctly refused its required clean
  worktree before any Docker action. It is a real local deployment rehearsal,
  not a mock regression required by this two-function helper repair; no local
  Docker lifecycle action ran.

Independent Agent 6 final-head review: `ACCEPT`. Its earlier blocking finding
about the test-created action-lock mode is closed by creating the lock under
umask `077`, enforcing mode `0600`, and asserting that mode alongside the real
blocked marker before cleanup.

## Stop and next gate

The unique stop state is
`STG-007_RUNTIME_RECOVERED_RESTART_EVIDENCE_BLOCKED_BY_READINESS_FAIL_CLOSED_REPAIR_WAITING_FOR_OWNER_REVIEW`.
Staging is healthy at exact `63600b13...` / Flyway V10, but STG-007 is not
PASS. This repair's merge creates another exact main SHA. The bounded V10-aware
continuation must restart from that new SHA with fresh release/env, formal
preflight, readiness and action approvals; no artifact from this failed
restart chain may be reused. STG-008 remains unauthorized.
