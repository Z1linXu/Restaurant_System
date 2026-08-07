# AL-003 PR-F0 Promotion Preparation

> Status: `AL-003_PR_F0_PROMOTION_WAITING_FOR_OWNER_REVIEW`
>
> Promotion base: `82b8059f6af1c7dff4eeb1648ca47bec039b5e52`
>
> Historical single-layer source: `e74f285965c4f3ec1f969e7d62112ec1adc9b6dc`
>
> Upstream status: PR-D and PR-E are in `main`; PR-E entered through PR #54

## Boundary

This document records the historical audit and its resolved promotion outcome.
Historical PR-F0 was stacked on historical PR-E and is not current authority.
The current PR-F0 candidate is rebuilt from the PR #54 main merge.

## Valid design to migrate

- Logical option plans use stable option codes and do not expose or persist
  internal source-to-target ID maps.
- The composition context owns an ordered, target-item-scoped option plan.
- Graph composers contribute to the logical plan instead of writing option
  repositories directly.
- Validation uses transaction-local virtual IDs; execution uses persisted IDs.
- Execution retains ordered Store locks, base graph creation, two-pass option
  parent persistence, persisted-plan verification, revision increment, and
  request completion in one transaction.
- Source/target revision rechecks, target-empty guards, strict profile identity,
  and missing/self/cyclic parent rejection remain required.
- Shared implementation contains no Chinatown, St-Denis, or Store ID branch.

## Dependency Repair Gate

Historical PR-F0 did not guarantee validate/execute parity. Logical
composition validates option ownership, duplicate stable codes, and the parent
graph, while exact `optionType`, `optionCode`, `optionGroup`, and positive sort
order checks occur only on the execute persistence path. An invalid custom
composer could therefore pass validate and fail execute.

The promoted PR-F0 extracts one shared option-plan validator and runs it in
both validate and execute before `persistOptionPlan()`. Execution may already
have written the base graph inside the same transaction; any option-plan
failure must roll that transaction back. Requiring all composition before any
base-graph persistence would be a larger redesign and is not part of this
minimal repair.

The approved technical plan freezes structured validation diagnostics:
`missingCodes`, `duplicateCodes`, and safe `warnings`. Historical PR-F0 builds
only a successful result with empty lists while failures use safe exceptions.
The promoted PR-F0 implements the frozen structured diagnostics contract. A
proposal to remove those fields or replace them with typed-errors-only behavior
would be a new Owner decision and a separate contract repair.

## Test corrections

- Remove disconnected repository mocks whose `never()` assertions do not
  observe the real composer.
- Prove zero writes at the transaction/repository or SQL-observation boundary
  for menu, V10 request, audit, and revision tables.
- Prove validate does not call Store write-lock methods.
- Prove the same invalid option plan fails validate and execute with the same
  safe code.
- Add a real two-transaction PostgreSQL revision-drift test if it can remain a
  bounded test-only addition; otherwise record it as a Staging evidence gap.
- Re-run persisted-plan field/parent equality, fresh-ID, rollback, revision +1,
  focused/full backend, compile, diff, secret, and scope checks.

## Explicit exclusions

No Controller, endpoint, Owner authorization, migration, menu business-rule
change, real clone, Store 1 query, SSH, Staging/Production action, Printing,
Order, Payment, or KDS change.

## Promotion order

PR-D and PR-E entered `main`; PR-F0 is rebuilt with the parity repair and
approved diagnostics contract and awaits Owner review. PR-F cannot begin
implementation before PR-F0 enters `origin/main`.
