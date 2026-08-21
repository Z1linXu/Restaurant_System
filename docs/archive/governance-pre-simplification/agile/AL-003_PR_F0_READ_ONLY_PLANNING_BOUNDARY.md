# AL-003 PR-F0 Read-only Planning Boundary

> Status: `AL-003_PR_F0_PROMOTION_WAITING_FOR_OWNER_REVIEW`
>
> Base: `82b8059f6af1c7dff4eeb1648ca47bec039b5e52` (`main`, after PR #54)
>
> Runtime access, real clone, deployment, and merge: not performed

## Scope

PR-F0 promotes only the internal, read-only planning layer for the existing
generic Store menu clone transaction. It does not register an HTTP Controller,
create a migration, reserve an idempotency request, or execute a real clone.

The layer builds an in-memory option plan from the existing base graph and
profile composers. It uses virtual target item IDs during validation, so the
target Store has no Category, Station, Item, Option, request, audit, or revision
write.

## Shared option-plan contract

`StoreMenuCloneOptionPlanValidator` is the single complete validator for both
the read-only `validate` path and the execute path before option persistence. It
checks target-item scope, exact required option fields, positive sort order,
duplicate target-item/code pairs, and missing, self-referential, or cyclic
parent references.

Invalid option plans produce the same safe result code,
`TARGET_MENU_VALIDATION_FAILED`, for validation diagnostics and execute failure.
No automatic retry or partial target persistence is introduced.

## Structured diagnostics

The internal validation result exposes only bounded and deterministic stable
codes:

- `missingCodes`
- `duplicateCodes`
- `warnings`

Codes are deduplicated, sorted, and capped at 100 values per list. The contract
does not expose source menu rows, target IDs, names, prices, credentials, tokens,
printer data, raw exception messages, or payloads.

## Boundaries preserved

- Shared coordinator, repository, and transaction code remain profile-agnostic.
- Chinatown and Store 1 rules remain in the concrete profile/composer layer.
- Existing execute behavior retains ordered Store locks, target-empty guards,
  parent two-pass persistence, rollback, and target revision advancement.
- Validation uses normal read queries and takes no Store write lock.
- PR-F, public validate/execute endpoints, Owner authorization mapping, and
  runtime clone execution remain unimplemented.

## Verification

Focused tests cover a valid Chinatown plan with 3 Stations, 4 Categories, 17
Items, and 74 Options; no-write validation; deterministic malformed-plan
diagnostics; execute rejection using the same safe code; source-option planning;
profile overrides; persisted parent behavior; and rollback. Full backend tests,
compile, diff, and secret/scope checks remain required before Owner review.
Independent Agent 6 review found no blocking implementation issue; its
read-only-boundary test recommendations are included in this promotion.

## Next gate

Owner review of this Draft PR is required. Only after it enters `main` may PR-F
begin its separately reviewed public API and authorization integration.
