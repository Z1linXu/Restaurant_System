# AL-003 PR-F0 Read-only Planning Boundary

> Status: `AL-003_PR_F0_READ_ONLY_PLANNING_WAITING_FOR_OWNER_REVIEW`
>
> Scope: prerequisite repair under the Dependency Repair Gate
>
> Migration: none
>
> Public endpoint: none

## Why this prerequisite exists

PR-F requires a validation endpoint that produces the same logical menu graph
as execution without writing an idempotency request, menu row, audit row, or
menu revision. Before this repair, graph composers persisted options directly,
so calling the execute transaction and rolling it back would have been the only
way to calculate the complete result. Rollback-based validation is not the
approved contract.

PR-F0 separates logical planning from persistence:

1. Resolve the exact, case-sensitive profile code through the shared registry.
2. Load and validate the source and target Stores without write locks.
3. Resolve the source snapshot and the profile-selected base graph.
4. Assign transaction-local virtual target IDs for read-only planning.
5. Run the same ordered graph composers used by execution.
6. Recheck target emptiness and query both current menu revisions.
7. Return expected counts and revisions without writing state.

Execution repeats the same resolution and composition after locking the source
and target Stores. It then persists the base graph and the transaction-local
option plan, verifies the stored graph and source revision, increments the
target revision once, and completes the existing idempotency request.

## Shared contract

- `StoreMenuCloneCompositionContext` owns an in-memory option plan keyed by
  target item and stable option code.
- `StoreMenuCloneGraphComposer` implementations add or replace logical plan
  entries; they do not write repositories.
- Composer created counts must equal the change in planned option count.
- The shared coordinator rejects missing, self-referencing, or cyclic planned
  option-parent graphs before persistence.
- `StoreMenuCloneTransactionService.validate` is read-only and does not call
  Store locking or revision mutation methods.
- `StoreMenuCloneTransactionService.execute` remains the only persistence
path and keeps the existing Store lock, revision, rollback, and evidence
boundaries.
- Execution validates every persisted option field and parent link against the
  logical plan before advancing the target revision.
- The shared planning and persistence code contains no Store ID, Store name,
  or Chinatown branch. Store-specific rules remain in the concrete profile and
  its profile-specific composer.

The transient source-to-target and option plans are not API response fields and
are not persisted as clone evidence.

## Validation result

The internal validation result contains:

- exact profile code;
- source and target menu revisions;
- expected Station, Category, Item, and Option counts;
- safe missing-code, duplicate-code, and warning collections.

PR-F remains responsible for Owner authorization, public DTO/error mapping,
and the `/validate` and `/execute` endpoints. PR-F0 does not expose an API.

## Required evidence

- Source option and parent plans are produced without repository writes.
- Chinatown overrides operate on the same in-memory plan used by execution.
- Validation produces the reviewed `4 / 3 / 17 / 74` Chinatown counts while
  leaving source and target menu rows and revisions unchanged.
- Validation rejects a source or target revision change during composition.
- Execution persists fresh option IDs, resolves parent IDs after insertion,
  proves the persisted fields and parent links match the plan, advances the
  target revision once, and preserves rollback behavior.
- Focused and full backend tests pass.
- Compile, diff, scope, and secret checks pass.

## Dependency state

PR-F is blocked until the Owner merges this prerequisite. After merge, PR-F
must be rebuilt from the new `main`; it must not reuse a rollback-based
validation implementation from an older preparation branch.
