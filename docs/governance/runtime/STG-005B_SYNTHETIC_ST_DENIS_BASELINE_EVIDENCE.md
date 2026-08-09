# STG-005B Synthetic St-Denis Baseline Evidence

> Historical package state at local verification: `DRAFT_PR_62_WAITING_FOR_OWNER_REVIEW`
>
> Current Git state: `IN_MAIN` via PR #62 merge
> `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b`; this remains repository-only
> capability, not runtime evidence.
>
> Verification time: `2026-08-08T00:54:21-0400`
>
> Runtime access: `NOT_PERFORMED`

## 1. Git and dependency boundary

| Evidence | Value |
|---|---|
| Repository main observed before this package | `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d` |
| Architecture dependency branch | `codex/store-provisioning-modular-architecture-plan` |
| Architecture dependency head used as package base | `61b8feabecac3444b345a9261af4f2bed76b9ccc` |
| Implementation branch | `codex/stg-005b-synthetic-st-denis-baseline` |
| Draft PR | [#62](https://github.com/Z1linXu/Restaurant_System/pull/62) |
| Historical package relationship | `STACKED_ONLY` above the then-unmerged architecture Draft PR |

PR #61 subsequently entered `main` at
`bbb1af9520c188b6ef6362e783284ba4001a7e63`. This package was rebuilt from
that mainline commit for renewed review; the historical rows above retain the
original package-base evidence. PR #62 later entered `main` at
`467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b`.

At the time of this local evidence neither package was `IN_MAIN`. Subsequent
promotion placed PR #62 in `main`; this historical record does not imply a
Staging or Production runtime action.

## 2. Implemented boundary

The package adds one disabled-by-default, profile-gated, non-web command for a
reviewed synthetic AL-003 source menu. It does not add a public HTTP endpoint,
Flyway migration, Production importer, Store Profile registry entry, or server
launcher.

The immutable source manifest contains:

```text
4 categories
3 stations
13 items
38 options
```

Synthetic display names use the `STG005_` marker. Category/station codes, item
SKUs, option groups, and option codes retain the reviewed AL-003 semantic
identifiers needed by the existing clone profile. The graph includes base
price and `cost_per_item` in its canonical SHA-256 fingerprint.

The transaction contract is:

- empty graph: create the complete graph and increment `menu_revision` once;
- exact graph: return replay evidence without writes or a revision change;
- partial, extra, inactive, renamed, repriced, recosted, or dangling-parent
  graph: fail closed without repair;
- late failure: roll back the entire graph and revision;
- concurrent apply: serialize on the source Store lock.

## 3. Local verification evidence

After the rebuild from `origin/main`
`bbb1af9520c188b6ef6362e783284ba4001a7e63`, the focused suite, complete
backend regression, and compile command below were rerun successfully. This is
local repository evidence only.

| Check | Result | Evidence |
|---|---|---|
| Focused STG-005B and retained STG-005A guard/command suite | `PASS` | `mvn -q -Dtest='StagingSyntheticSourceMenu*,StagingSyntheticBootstrapGuardTest,StagingSyntheticBootstrapCommandTest,StagingSyntheticBootstrapSafetyShapeTest' test` |
| STG-005B focused test count | `PASS` | 33 test cases |
| Full backend suite | `PASS` | `mvn -q test`: 358 tests, 0 failures, 0 errors, 3 skipped |
| Backend compile | `PASS` | `mvn -q -DskipTests compile` |
| First apply / exact replay | `PASS` | Integration test verifies one graph, revision `1 -> 2`, then unchanged replay |
| Partial graph rejection | `PASS` | Integration test verifies no repair and unchanged revision |
| Clone-relevant cost drift rejection | `PASS` | Integration test verifies `cost_per_item` drift is not accepted as replay |
| Parent integrity | `PASS` | Manifest validation and persisted dangling-parent regression coverage |
| Late failure rollback | `PASS` | Integration test verifies zero retained menu rows and unchanged revision |
| Concurrent application | `PASS` | Integration test verifies one create plus one replay and one revision increment |
| Existing AL-003 planner compatibility | `PASS` | Applied source graph validates to 4 categories, 3 stations, 17 target items, and 74 target options |
| Migration scope | `PASS` | No migration file is added or modified |
| Public API scope | `PASS` | No Controller or HTTP route is added |
| Sensitive-input scan | `PASS` | No password, token, authorization header, private key, or client attempt token pattern in the new package/runbook |

The transaction integration tests use the repository's isolated JPA test
database. They are repository evidence, not PostgreSQL Staging execution or
runtime acceptance evidence.

## 4. Safety and non-execution evidence

- Command activation requires the exact `staging-synthetic-bootstrap` profile
  and an explicit disabled-by-default property.
- Default invocation is read-only planning; writes require `--execute`.
- Existing STG-005A project/root/SHA/database/non-web/printing guards are reused.
- A unique completed STG-005A request must bind the source Store, Organization,
  Owner topology, runtime SHA, and tool SHA before planning or applying.
- Source identity is limited to the reviewed AL-003 synthetic acceptance
  boundary, and the Store itself must be active, Organization-owned, and have
  printing disabled.
- Evidence output contains only bounded bootstrap/source identity, runtime/tool
  SHA, fingerprint, revision, counts, status, and result code fields.
- CLI identity values are not independent runtime observations. The separately
  reviewed AL-003S launcher must derive and bind the release/project/root/state
  evidence; absent that launcher, execution remains `NO_GO`.
- No SSH, Docker, Flyway, bootstrap command, database mutation, credential
  creation, login, Owner API call, clone, Staging action, or Production access
  occurred while producing this package.

## 5. Remaining gates

1. PR #61 is `IN_MAIN`; this package has been rebuilt from the resulting main
   and still requires Owner review and merge.
2. After #62 enters main, rebuild #63 from latest main and repeat its review,
   tests, and governance sync.
3. A fresh exact merged-main SHA, runtime plan, evidence digest, and explicit
   Owner approval before any Staging command.
4. Runtime proof for V9/V10, STG-005A topology, source Store ID `1`, synthetic
   Owner login, source-menu plan/apply/replay, AL-003 validate/execute/replay,
   restart persistence, and Production continuity.

Until those gates are completed, this package remains repository-only and must
not be described as a deployed Synthetic St-Denis baseline.

## 6. STG-008 runtime entry update

PR #62 is now historical `IN_MAIN` capability. On 2026-08-08 the authorized
STG-008 read-only entry found zero Staging topology/bootstrap rows and safely
proved the next generated Store ID is `1`, but STG-005A stopped before plan or
write at the credential-contract Owner Gate. STG-005B therefore did not enter
plan, execute, or replay. Its runtime state remains `NOT_CREATED`; no source
revision or 4/3/13/38 graph row changed.

The current sanitized runtime decision is
[STG-008 Synthetic Topology and Source Entry Evidence](STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md).

The Owner later aligned the credential contract, but the fresh password-free
STG-005A plan one-shot failed before its command/data path at the older shared
cloud/Flyway safety rule. Cleanup succeeded, topology remained empty, and
blocked state was retained. STG-005B still has no plan/create/replay runtime
result. Its next prerequisite is the bounded repair in main, a newly approved
exact Staging deployment, and separately approved blocked-state recovery. See
[STG-008 Flyway Guard Repair Evidence](STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md).
