# STG-008 one-shot lifecycle repair evidence

> Classification: `REPOSITORY_DEPENDENCY_REPAIR_EVIDENCE`
>
> Scope: exact-runtime observation plus the bounded repository repair. This is
> not a Staging acceptance record and authorizes no retry, credential read,
> synthetic write, login, source-menu action, or Production operation.

## Ground Truth and observation

- Fresh `origin/main` was `2a6c30a5948882ff8bf1d3808e2970fe5b4a6ae3`.
- The approved SHA, detached release, build source, and deployed isolated
  Staging image identity were that same exact SHA. Staging remained Flyway V10
  with V1--V10 chain and no pending migration.
- Formal preflight and fresh readiness passed before the new password-free
  `bootstrap-plan`. Printing remained disabled; loopback/project/network/state
  isolation held; Production continuity observation was unchanged.
- The reviewed previous blocked marker/lock pair was the only recovery target
  and was cleared under the already approved bounded recovery sequence.

## Failure classification

The repaired non-web request-context path was actually exercised: the one-shot
started and emitted `STG005_BOOTSTRAP|status=VALIDATED` before credential
reading, command execution, or a transaction. It then remained alive until the
launcher’s 600-second timeout.

The sanitized bounded result showed the active `SimpleBrokerMessageHandler`.
`WebSocketConfig` was still active under `staging-synthetic-bootstrap`, even
though the one-shot uses `spring.main.web-application-type=none`. When the
timeout stopped Compose, Compose `--rm` had begun removing the deterministic
scoped container while the launcher finalizer attempted its separate bounded
`docker rm -f`. The finalizer observed cleanup already in progress and, by
design, retained the exact fail-closed marker/lock pair.

Classification: `REPOSITORY_NON_WEB_ONE_SHOT_LIFECYCLE_DEFECT`.

This is neither a Flyway/schema failure nor a request-context failure. It is
not a Production incident: the allowed lightweight continuity observation kept
the Production container image identities, start times, restart counts, and
HTTP 200 health unchanged. Staging returned to 200 for backend health,
frontend root, and `/ws/info`; synthetic Organization, Store, user, credential,
membership, and request state remained zero.

## Bounded repair

`WebSocketConfig` is excluded only for the dedicated
`staging-synthetic-bootstrap` profile. The normal web runtime retains its
WebSocket configuration. This removes the long-lived broker from the non-web
one-shot so a completed guarded command can exit before the timeout; it does
not weaken approvals, identity binding, secret handling, Flyway guards,
printing disablement, scoped cleanup checks, blocked-state behavior, or Store
isolation.

Focused safety-shape coverage asserts that the synthetic profile excludes the
WebSocket configuration alongside the existing scheduler and printing-worker
exclusions. The existing shell runtime-guard regression remains required.

## Required next gate

This repair is runtime-sensitive. After it enters `main`, a new exact-main
Owner Runtime Gate must approve a fresh release/private-env binding, formal
preflight, Staging-only V10-to-V10 deployment, fresh readiness, and recovery
only of the exact retained blocked pair. A new digest-bound STG-005A PLAN is
then required. Do not reuse this attempt’s action approval, readiness, plan,
or runtime-only credential channel.
