# STG-009 Phase A Owner login client repair evidence

> Classification: `REPOSITORY_DEPENDENCY_REPAIR_EVIDENCE`
>
> Scope: a bounded OPS-001 client action only. This is not Staging login
> evidence and authorizes no release binding, deploy, credential creation,
> onboarding, clone, migration, restart, or Production operation.

## Observation and classification

After the synthetic STG-008 topology/source commands completed, the existing
secret-safe Owner client offered only `prepare-target` and `clone-acceptance`.
Both actions are Phase B behavior because they can create a target Store or
invoke menu-clone validation/execution. They cannot safely stand in for the
approved Phase A Owner-login acceptance.

Classification: `REPOSITORY_TOOLING_SCOPE_GAP`.

## Bounded repair

`owner-login-acceptance` uses the existing loopback-only authenticated routes:
`/auth/login`, `/auth/me`, `/me/workspaces`, `/owner/overview`, and
`/auth/logout`. It requires the existing exact-runtime/preflight binding,
action lock, one-use approval, inherited secret FD, and private mode controls.
It verifies the approved Organization `OWNER` role and that both workspace and
overview expose exactly the approved synthetic source Store. It performs no
onboarding, clone validation, clone execution, or business-data write.

The local mock regression exercises successful login/context/logout and proves
that neither tokens nor passwords are printed. It also asserts that the Phase A
path emits no onboarding or clone result.

## Required runtime boundary

This client change is runtime-sensitive. Once it is in `main`, the continuous
authorization requires a fresh exact-SHA Staging rebind/deploy/readiness before
the new action may run. The result must remain sanitized, use the private
credential channel, and preserve the STG-008/Phase-B and Production boundaries.
