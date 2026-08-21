# AL-003 Owner Return Review Package

> Status: `PREPARATION_COMPLETE_WAITING_FOR_OWNER`
>
> Ground-truth main: `e6b41dd644c50b847d27947b5b0d27e1d4449c09`

## Current ground truth

- PR-C and PR #51 are in `origin/main`.
- Draft PR #52 is the only current promotion candidate. Its head is
  `5f6438ad1ffe1379eb3740a3db64180ce2433bfa`; it is not in `main`, Staging, or
  Production.
- PR-E and PR-F0 remain historical stacked-only implementations.
- PR-F remains unimplemented. No public menu-clone endpoint exists.
- Reported Production remains `4667f3c`; the prior Staging runtime remains
  `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`. Neither runtime was accessed.

## Draft PR queue

| PR | Package | Base | Head | Verification | Review | Owner action |
|---|---|---|---|---|---|---|
| #52 | PR-D generic source options | `e6b41dd644c50b847d27947b5b0d27e1d4449c09` | `5f6438ad1ffe1379eb3740a3db64180ce2433bfa` | Focused, transaction, full backend 298 tests, compile, diff/secret/scope scans PASS | Agent 6 PASS; GitHub mergeable | Review and merge or reject |

No final PR-E, PR-F0, or PR-F Draft PR was created because its direct
dependency is not in `origin/main`.

## Completed preparation

- PR-E single-layer diff, compatibility, migration, scope, and test plan.
- PR-F0 single-layer design audit and validate/execute parity gap.
- PR-F proposed API, DTO, authorization, replay/FAILED, error, and test scope.
- Exact-SHA Staging release/acceptance, evidence, rollback, and NO-GO template.
- Independent Agent 6 review: two P1 wording/scope findings were corrected;
  final verdict `PASS`.

## Active risk and decision

Historical PR-F0 allows some malformed option plans to pass validate and fail
execute because full field validation is persistence-only. It also leaves the
technical plan's approved structured diagnostic collections empty. PR-F0 must
repair validate/execute parity and implement those frozen diagnostics after
PR-E is merged. Until that repair enters `main`, the Dependency Repair Gate
blocks PR-F implementation.

Other residual gaps are frontend Small default/price/snapshot coverage, a true
two-transaction PostgreSQL revision-drift test, Store 1 live menu evidence, and
the Production/main deployment gap.

## Strict Owner review order

1. Review Draft PR #52.
2. Merge PR #52 if approved.
3. Coordinator fetches the new main and rebuilds PR-E from it.
4. Review and merge PR-E if approved.
5. Coordinator rebuilds PR-F0 from post-PR-E main, fixes validation parity,
   and implements the already-approved structured diagnostics contract.
6. Review and merge PR-F0 if approved.
7. Coordinator re-audits and implements PR-F from post-PR-F0 main.
8. Review and merge PR-F if approved.
9. Separately approve an exact-SHA Staging release and command batches.

No automatic merge, deployment, runtime access, Flyway execution, Store 1
query, or real clone is authorized by this package.
