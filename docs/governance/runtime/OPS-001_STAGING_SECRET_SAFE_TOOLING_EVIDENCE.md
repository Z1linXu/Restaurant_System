# OPS-001 Staging Secret-Safe Tooling Evidence

> Evidence classification: `LOCAL_REPOSITORY_VERIFICATION`
>
> Runtime access or mutation: `NO`
>
> Base: `origin/main@85d97b7327b2e15aa561ed28a5788b92cedf6f5b`

## Scope

OPS-001 supplies three fail-closed repository helpers and mock tests:

1. clean detached release plus atomic private-environment identity rotation;
2. sanitized Flyway/runtime collection plus same-container, same-image restart;
3. secret-FD Owner login/onboarding/validate/execute/replay client.

The shared approval entry binds exact full SHA, environment digest, exact
action, canonical scope and external Owner reference. Approval digests are
one-use. No backend, frontend, Android, migration, Compose configuration,
business API, clone engine or Production deployment behavior changed.

## Verification contract

Focused tests cover wrong SHA/action/environment, absent/expired/replayed
approval, unsafe private-file permissions, non-identity environment drift,
release cleanliness, official validator recovery, container/image drift,
failed/missing/duplicate/script/checksum-mismatched Flyway rows, restart order/health,
recovery-directory symlink redirection and shared-lock contention,
secret descriptor validation,
token/header containment, response redaction, Owner topology, onboarding and
clone replay, API failure, reviewed profile/counts and source identity. The API
client disables ambient curl configuration, binds each batch to exact preflight
and running-image validation, and leaves clone execute behind a later approval
than onboarding plus validation.

The established Staging guard, preflight, deployment, readiness and AL-003S
regressions remain mandatory. Markdown links, secret scan, governance drift,
`git diff --check`, and independent Agent 6 review are exit gates.

## Runtime boundary and next gate

This evidence proves repository behavior only. It does not prove that the
dedicated server repository, candidate release, private environment, images,
Flyway V9/V10, synthetic credentials/topology, Owner session, clone result or
restart evidence exists.

After this package is `IN_MAIN`, STG-007 may enter only its Owner Runtime Gate:
select the new exact merged-main SHA and separately approve release/env,
deploy/Flyway and later mutation batches. No STG-007 runtime action is part of
OPS-001.
