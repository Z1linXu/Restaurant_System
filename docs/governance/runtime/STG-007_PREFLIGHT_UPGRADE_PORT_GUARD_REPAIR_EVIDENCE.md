# STG-007 Preflight Upgrade Port Guard Repair Evidence

> Evidence classification: `LOCAL_REPOSITORY_VERIFICATION`
>
> Runtime mutation: `NO`
>
> Base: `origin/main@362c954a8753204476ddf1415ea86050589760dd`

## Trigger

The Owner authorized phased STG-007 runtime work from exact main. A minimum
read-only identity check confirmed the retained Staging runtime still owns
`127.0.0.1:18080`. Static inspection then proved that the formal preflight
rejected every occupied port, including that exact retained runtime. Batch A
requires a formal preflight PASS but does not authorize stopping the retained
runtime first, so the old rule was a deterministic upgrade-path false positive.

No environment rotation, release creation, container lifecycle action, image
build, Flyway operation, bootstrap, credential, login, clone, Production
mutation, or business-data read occurred before entering this repair loop.

## Repair boundary

The formal preflight now accepts exactly two port states:

1. no listener on port `18080`, for a first deployment; or
2. a listener whose local address is exactly `127.0.0.1:18080`, backed by
   exactly one running Compose container labeled project
   `restaurant-pos-staging` and service `nginx`, with exactly one
   `80/tcp -> 127.0.0.1:18080` mapping.

Every public, unexpected, unowned, multiply owned, stopped, wrong-project,
wrong-service, missing-metadata, or differently mapped listener remains
fail-closed as `NO_GO`. The check uses only sanitized `ss`, Compose `ps`, and
formatted Docker inspection. It performs no Docker lifecycle action and does
not print container environment or resolved Compose configuration.

## Verification

The focused fixture covers the free-port path, an unowned busy port, the exact
retained-Staging upgrade path, duplicate listeners, multiple nginx container
IDs, an extra published mapping, wrong project/service, stopped ownership,
missing metadata, and a public listener. Existing path isolation, protected
PostgreSQL leaf, printing-disabled, exact-SHA, evidence binding, and
no-lifecycle assertions remain active.

Local verification passed for:

- the formal preflight fixture and shell syntax;
- the Staging guard and deploy CLI-state regressions;
- OPS-001 release rotation and runtime evidence regressions;
- AL-003S acceptance readiness regression;
- Markdown links, secret scan, governance drift, scope scan, and
  `git diff --check`.

Agent 6 initially blocked duplicate-listener and extra-published-mapping gaps.
After both guards and their negative fixtures were added, its independent
second review returned `ACCEPT` with no blocking or non-blocking findings.
Publication still requires latest-main base, clean GitHub mergeability, and an
unchanged final scope/head.

## Ground Truth and next gate

OPS-001 is `REPOSITORY_COMPLETE` in main through PR #74. This bounded repair
changes the candidate commit, so the Owner's prior exact SHA
`362c954a8753204476ddf1415ea86050589760dd` expires after the repair enters
main. STG-007 remains runtime-incomplete. Batch A must restart from the new
merged `origin/main` and re-establish every release, environment, isolation,
resource, formal-preflight, and minimum Production-continuity gate before
Batch B can become eligible.

The repair PR's auto-merge authority does not authorize any Staging or
Production runtime action.
