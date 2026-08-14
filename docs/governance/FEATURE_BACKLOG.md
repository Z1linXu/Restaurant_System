# Feature Backlog

## Current final productization planbook (2026-08-13)

The Owner closed the 30-answer productization gate. The active plan is
[FINAL_PRODUCTIZATION_PLANBOOK](agile/FINAL_PRODUCTIZATION_PLANBOOK.md).

Current authorized roadmap:

- `PHASE_A_MODULAR_PRODUCTIZATION`: authorized now. First loop:
  `PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION`.
- `PHASE_B_OWNER_STORE_PROVISIONING`: not authorized for implementation yet.
- `PHASE_C_REAL_MULTI_STORE_PROOF_CHINATOWN_AND_SAINTE_CATHERINE`: not
  authorized for implementation yet.

Core normal-Store capabilities selected by the Owner are Ordering/POS, Menu,
Menu Management, Table Management, Printing, GRAB Printing, Frontdesk Receipt,
Order History and Reporting. KDS is optional/default off. Reports are core;
advanced Analytics may be classified separately only after architecture audit.

Current A0.1 refinement approval:

```text
PHASE_A0_1_PRICING_POLICY_SCHEMA_CHANGE_APPROVAL
```

Owner UI review rejected free-form Size editing as final product UX. A0.1 keeps
the `OPTION + MODIFIER_GROUP + PRICE_DELTA` engine for per-item Size identity,
but requires system-controlled Small/Regular/Large and Store-level Size/Combo
pricing policies as the canonical price source. The approved implementation
uses additive `store_pricing_policies`, Pricing Rules, system-controlled Size
Configuration, and a rollback compatibility mirror for Size/Combo
`menu_item_options.price_delta` only. Evidence:
[PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_SCHEMA_GATE](agile/PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_SCHEMA_GATE.md).
Implementation evidence:
[PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_IMPLEMENTATION_EVIDENCE](agile/PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_IMPLEMENTATION_EVIDENCE.md).
The Owner accepted A0.1 manual Staging UX retest:
`OWNER_A0_1_PRICING_UX_RETEST = PASS`.

Current A0.2 implementation approval:

```text
PHASE_A0_2_STORE_COMBO_CONFIGURATION
```

A0.2 implements Store-level Combo Contents configuration while preserving A0.1
Pricing Rules as the Combo price source. The repository package may add
`store_combo_components` as the Store-scoped canonical content table for
reviewed `COMBO_EGG` and `COMBO_SIDE` components; item `COMBO_ALLOWED` remains
in `menu_item_options`. Evidence:
[PHASE_A0_2_STORE_COMBO_CONFIGURATION_IMPLEMENTATION_EVIDENCE](agile/PHASE_A0_2_STORE_COMBO_CONFIGURATION_IMPLEMENTATION_EVIDENCE.md).
Runtime Staging evidence:
[PHASE_A0_2_STORE_COMBO_CONFIGURATION_STAGING_EVIDENCE](agile/PHASE_A0_2_STORE_COMBO_CONFIGURATION_STAGING_EVIDENCE.md).

A0.2 merged through PR #134 and is deployed to exact-SHA Staging at
`90ac0cb0496161b12c47cff00573b56b4abc961c` with Flyway V12 and automated
validation PASS. Owner manual Staging retest is accepted:

```text
OWNER_A0_2_MANUAL_STAGING_RETEST = PASS
```

Historical A0.2 closure:

```text
PHASE_A0_2_STORE_COMBO_CONFIGURATION_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

Current authorized execution advances continuously through A1 Module Catalog,
A2 Module Dependency Graph and A3 Store-level Module Configuration, then stops
before A4.

Current A1 Module Catalog package:

- Technical evidence:
  [PHASE_A1_MODULE_CATALOG](agile/PHASE_A1_MODULE_CATALOG.md)
- Canonical machine-readable catalog:
  `backend/src/main/resources/module/module-catalog.v1.json`
- Static validation:
  `backend/src/test/java/com/restaurant/system/modules/ModuleCatalogContractTest.java`

A1 is complete:

```text
PHASE_A1_MODULE_CATALOG = PASS
PR = #137
MERGE_SHA = 34169152c6d48ecf503b441fe7428416c399d0a9
```

Current A2 Module Dependency Graph package:

- Technical evidence:
  [PHASE_A2_MODULE_DEPENDENCY_GRAPH](agile/PHASE_A2_MODULE_DEPENDENCY_GRAPH.md)
- Machine-readable graph:
  `backend/src/main/resources/module/module-dependency-graph.v1.json`
- Reusable validator:
  `backend/src/main/java/com/restaurant/system/modules/ModuleDependencyValidator.java`
- Focused validation:
  `backend/src/test/java/com/restaurant/system/modules/ModuleDependencyValidatorTest.java`

A2 is complete:

```text
PHASE_A2_MODULE_DEPENDENCY_GRAPH = PASS
PR = #138
MERGE_SHA = 1780c8934a502709844713d91c493b076e714983
```

Current A3 Store-level Module Configuration package:

- Technical evidence:
  [PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION](agile/PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION.md)
- Additive persistence:
  `backend/src/main/resources/db/migration/V13__add_store_modules.sql`
- Canonical Store module read/config contract:
  `GET /api/v1/stores/{storeId}/context`,
  `GET /api/v1/stores/{storeId}/modules`, and
  `PUT /api/v1/admin/stores/{storeId}/modules`
- Backend foundation:
  `backend/src/main/java/com/restaurant/system/modules/StoreModuleServiceImpl.java`

A3 remains bounded to Store module persistence/read/config. A4 profiles, A6/A7
runtime gating, Phase B/C, Chinatown, Sainte-Catherine and Production work are
not part of this package.

A3 is complete:

```text
PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION = PASS
IMPLEMENTATION_PR = #139
IMPLEMENTATION_MERGE_SHA = 1643ca071199c49b5d4404feac6ba367a3143a81
RUNTIME_DI_REPAIR_PR = #140
RUNTIME_DI_REPAIR_MERGE_SHA = c1b5e7681f24a11fbf99293567b3da08076fa3b6
DEPLOYED_STAGING_SHA = c1b5e7681f24a11fbf99293567b3da08076fa3b6
STAGING_FLYWAY = V13
A3_ACCEPTANCE = PASS
CORE_REGRESSION_SMOKE = PASS
PRODUCTION_MUTATION = NONE
```

Current stop:

```text
PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION_COMPLETE_WAITING_FOR_PHASE_A4_OWNER_CONTINUATION
```

Current A4 Store Profile Contract package:

- Technical evidence:
  [PHASE_A4_STORE_PROFILE_CONTRACT](agile/PHASE_A4_STORE_PROFILE_CONTRACT.md)
- Additive persistence:
  `backend/src/main/resources/db/migration/V14__add_store_profiles.sql`
- Canonical profile read contract:
  `GET /api/v1/store-profiles` and
  `GET /api/v1/store-profiles/{profileCode}/versions/{profileVersion}`
- Validator:
  `backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java`

A4 is bounded to database-backed versioned Profile contract/read/validation and
entered `main` through PR #142 at
`be14923c96098d80b1b841e2ba0edbe3ca2563a5`. Focused backend tests, full
backend tests, `git diff --check` and Agent 6 are PASS for the A4 package.

A5 adds the first database-backed canonical profile:
[PHASE_A5_ST_DENIS_CANONICAL_PROFILE](agile/PHASE_A5_ST_DENIS_CANONICAL_PROFILE.md).
`ST_DENIS_CANONICAL_PROFILE/v1` is safe, versioned, fingerprinted and
profile-local. It carries the complete reviewed St-Denis configuration graph
without Production DB IDs, auth material, physical printer endpoints or device
pairing material. It does not create or materialize Stores, start Owner
provisioning, start A6 gating, create Chinatown/Sainte-Catherine or mutate
Production. Exact-SHA Staging deploy/Flyway validation is required after A5 PR
merge because V15 seeds database-backed profile data. PR #143 entered `main`
at `b83afa98d304223834793d03bfc367b4cf4238f1`; the first exact-SHA Staging
attempt applied V14 then failed closed before V15 history because V15 JSON
literals began with a newline under the A4 `content_json` check. PR #145
entered `main` at `494497dfbf874bcf12da7eb3821a276f663959c5` and repaired only
the V15 seed literal layout and OPS-001 Flyway checksum evidence. Exact-SHA
Staging deploy of that merge applied V15 successfully, then backend startup
failed closed because the A4 `fingerprint_sha256 char(64)` columns were mapped
by JPA as default `varchar(255)`. PR #146 added explicit `char(64)` DDL
metadata, but Staging proved Hibernate still expected JDBC `Types#VARCHAR`.
PR #147 added explicit `@JdbcTypeCode(SqlTypes.CHAR)` and regression coverage
only. Exact-SHA Staging deploy of
`3440fddad7571409c66189e44976658921e5de1f` passed health, WebSocket readiness,
Flyway V15, Profile fingerprint/artifact validation and graph-count validation.
No Store materialization, new migration after V15, Flyway history edit or
Production action occurred.

Phase A0 deployed evidence is tracked in
[PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION_EVIDENCE](agile/PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION_EVIDENCE.md).
Current deployed conclusion: existing menu option/modifier data safely models
dynamic Size variants, so no Flyway migration was required for A0. Code,
Staging deploy and automated validation are complete; Owner A0.1 Pricing UX
acceptance is now PASS.

## Current final productization roadmap (2026-08-12)

The final productization route is now planned as a strict three-phase program:

- `PHASE_A_MODULAR_PRODUCTIZATION`: module catalog, dependency graph, Store
  Profile contract, canonical St-Denis profile, module configuration schema,
  validator and gating contract.
- `PHASE_B_OWNER_STORE_PROVISIONING`: Owner-safe Draft -> profile/template ->
  module configuration -> validation -> readiness -> activation workflow.
- `PHASE_C_REAL_MULTI_STORE_PROOF_CHINATOWN_AND_SAINTE_CATHERINE`: use Phase
  A+B to provision real Stores without manual shortcuts or per-Store code.

Backlog rule: do not start Owner Create New Store before Phase A is accepted,
and do not create Chinatown/Sainte-Catherine before Phase B is accepted. The
field-test bug-fix loop remains a side loop unless a confirmed P0/P1 blocker
is open. See
[FINAL_PRODUCTIZATION_THREE_PHASE_ROADMAP_AUDIT](agile/FINAL_PRODUCTIZATION_THREE_PHASE_ROADMAP_AUDIT.md).

Current stop:
`FINAL_PRODUCTIZATION_AUDIT_COMPLETE_WAITING_FOR_OWNER_30_ANSWERS`.

## Current Production deployment of three-reliability repairs (2026-08-12)

`PAD_SLEEP_PRINT_BLOCKING_REPAIR`,
`PAD_MENU_REVISION_AND_CLICK_LOCK_REPAIR`, and
`PRINTING_BOUNDED_SCHEDULING_LATENCY_REPAIR` are now
`DEPLOYED_TO_PRODUCTION` through exact RC
`RC-THREE-RELIABILITY-20260812-3EC4D88` / application SHA
`3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`. Production remained Flyway V10,
kept `PAD_DIRECT` printing, four printers and three assignments, and passed
health/WebSocket/second-start/bounded observation. Owner field retest remains
required before marking any of these repairs `OWNER_FIELD_VERIFIED`.

Current unique stop:
`THREE_RELIABILITY_REPAIRS_PRODUCTION_PROMOTED_WAITING_FOR_OWNER_FIELD_RETEST`.

## Current three-reliability Production promotion tooling dependency repair (2026-08-12)

Historical same-round dependency repair: `THREE_RELIABILITY_REPAIRS_PRODUCTION_RC_PROMOTION` found a pre-deploy
tooling-only blocker: the next immutable RC must record previous application
artifact identity separately from retained Production control checkout
identity, and fresh V10 backup restore rehearsal needs an explicit V10 Flyway
ledger target. A bounded repository repair updates only promotion/backup
helpers and tests. It does not authorize or perform Production deployment,
restart, migration, backup, restore, configuration change, Chinatown,
modularization, physical printer binding or Pad pairing.

## Completed Owner field-test three-reliability repair batch (2026-08-11)

- `PAD_SLEEP_PRINT_BLOCKING_REPAIR`: `DEPLOYED_TO_STAGING`; repository change
  shortens Android pre-output `CLAIMED` lease while preserving conservative
  `PRINTING` handling for ambiguous physical-output state. Owner physical Pad
  retest remains required for real screen-off confirmation.
- `PAD_MENU_REVISION_AND_CLICK_LOCK_REPAIR`: `DEPLOYED_TO_STAGING`; repository
  change keeps IndexedDB offline snapshots and adds visibility/focus/online/
  periodic revision checks plus visible disabled UX when a draft is locked.
- `PRINTING_BOUNDED_SCHEDULING_LATENCY_REPAIR`: `DEPLOYED_TO_STAGING`;
  repository change keeps durable outbox and same-printer FIFO while enabling
  bounded Store+printer keyed concurrency for unrelated printers.

PR #122 merged and exact Staging
`3ec4d88a47f68e05b92d9246bfd63af2d1f297f9` passed health and automated MOCK
smoke. Scope remained Staging/repository-only. Production promotion, Chinatown,
modularization, schema/Flyway changes, physical printer binding, Pad pairing
and Production configuration remain excluded.

## Production exact-artifact promotion result (2026-08-11)

- `REL-DEPLOY-001`: exact-image, fixed-state promotion — `PASS` for frozen
  `RC-ST-DENIS-20260811-2661EB76`; later nginx-readiness retry repair is
  repository-only and does not change the completed RC.
- `REL-RECOVERY-001`: fresh atomic backup, integrity and isolated restore —
  `PASS`; restore remains a new Owner gate.
- Production St-Denis: exact accepted application SHA `2661eb76...`, Flyway
  V10, second-start/no-pending and bounded observation — `PASS`.
- Previous application images are retained and old-app-on-V10 compatibility is
  `YES`; application-only rollback is available for a future severe incident.

Current unique stop:
`PRODUCTION_EXACT_RC_PROMOTED_POST_DEPLOY_OBSERVATION_PASS`.

## Historical Production exact-artifact promotion dependency repair (2026-08-11)

- `REL-DEPLOY-001`: fixed-state, exact-image-ID, no-build/no-pull Production
  promotion helper and override — `DEPENDENCY_REPAIR_UNDER_REVIEW`.
- `REL-RECOVERY-001`: private atomic Production backup plus isolated
  transactional restore rehearsal — `DEPENDENCY_REPAIR_UNDER_REVIEW`.
- Exact `2661eb76...` Owner field acceptance — `OPERATOR_CONFIRMED_PASS` for
  this RC only; governance synchronization supersedes the stale retest wait.
- Production deployment remains `NO_GO` until the prepared RC is frozen with
  migration, Android/client, rollback, backup/restore and Agent 6 PASS results.
Historical stop (superseded):
`RC_PREPARED_WAITING_FOR_MANDATORY_PROMOTION_GATES`.

## Historical superseded Owner field-test Printing bug repair (2026-08-11)

`OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` continues after the earlier
`STAGING_MOCK_PRINTING_VERIFIED_OWNER_FIELD_TEST_CONTINUES` checkpoint. The current
bounded repair covers Owner-confirmed printing display defects, PAD_DIRECT
screen-off reliability, and an audit-only queue latency explanation. Repository
repair entered `main` through PR #117, exact Staging
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab` passed automated MOCK smoke, and
the package then waited for Owner retest at
`HISTORICAL_OWNER_FIELD_TEST_PRINTING_FIXES_DEPLOYED_WAITING_FOR_OWNER_RETEST`, now superseded.

## Historical Owner field-test Printing slice (2026-08-11)

`OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` was active for the bounded
`STAGING_MOCK_PRINTING_FIELD_TEST_ENABLEMENT` slice. PR #114 supplied the
generic fail-closed runtime policy. Exact current Staging
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab` / V10 now runs
`MOCK/true`. Submit, three-route dispatch, update tickets, reprint, rendered
snapshots and browser visibility passed against the retained
4-printer/3-assignment topology. Physical printing, Pad pairing and Production
were excluded from that package. Historical stop:
`HISTORICAL_OWNER_FIELD_TEST_PRINTING_FIXES_DEPLOYED_WAITING_FOR_OWNER_RETEST`, now superseded.

## Historical TWIN-001 operational Twin readiness override (2026-08-10)

TWIN-001 reconstruction remains `PASS`; Owner field testing was active at this
historical checkpoint.
Exact Staging `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` / Flyway V10 passes deterministic
manifest-v2 parity, independent Staff credential verification, safe automated
workflow smoke and health with zero blocking behavior difference. Historical stop:
`HISTORICAL_OWNER_FIELD_TEST_PRINTING_FIXES_DEPLOYED_WAITING_FOR_OWNER_RETEST`, now superseded.
That loop permitted bounded Owner field testing and bug repair. It did not
start a hardware gate, modularization, Chinatown, REL-001 or Production work
automatically.

## Historical TWIN-001 reconstruction execution override (2026-08-10)

`TWIN-001_STAGING_RECONSTRUCTION_APPROVAL` is active. The bounded zero-delete
projector/staff dependency package is repository verified and awaiting
Agent 6/PR/merge before Staging apply, parity and automated smoke continue.
Production, Flyway, physical printing/Pad pairing, Chinatown, modularization
and REL-001 remain outside this loop.

## Historical manifest v2 readiness checkpoint (2026-08-10)

The corrected bounded read completed with a deterministic V7-to-V10 safe
manifest v2. No Staging writer ran at that checkpoint. Historical stop:
`TWIN-001_MANIFEST_V2_RECONSTRUCTION_READY_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
That gate is now granted and superseded by the current execution override.

## Historical reconstruction NO-GO override (2026-08-10)

`TWIN-001_STAGING_RECONSTRUCTION_APPROVAL` was received, but pre-write source
validation stopped before runtime entry. The reviewed manifest is incomplete
as a deterministic reconstruction payload and its recorded query columns
conflict with the checksum-identical V7 schema. No Staging/Production action
occurred.

An isolated PostgreSQL 16.14 verification passed the intended append-only
`V7 -> V8 -> V9 -> V10` forward path, current-candidate JPA/health, safe
configuration preservation, and second-start no-migration check. The observed
version delta is `CURRENT_PRODUCTION_VERSION_DIFFERENCE`, not a downgrade
target. Aggregate `SCHEMA = BLOCKING_BEHAVIOR_DIFFERENCE` and
`TWIN-001 = NO_GO` remain because no complete reconstructed Twin has operated
on V10.

Historical stop:
`TWIN-001_RECONSTRUCTION_NO_GO_WAITING_FOR_MANIFEST_COMPLETION_READ_APPROVAL`.
Next TRUE OWNER GATE:
`TWIN-001_RECONSTRUCTION_MANIFEST_COMPLETION_READ_APPROVAL`. See
[the immutable NO-GO evidence](runtime/TWIN-001_STAGING_RECONSTRUCTION_SCHEMA_NO_GO_EVIDENCE.md).

## Historical verified Production inventory checkpoint (2026-08-10)

The Owner-approved St-Denis read gate completed as a bounded, explicit-column,
read-only inventory. Fresh `origin/main=34ef8c577dd5e8464ef885bf235b0bece0018503`;
Production retained `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`/V7 and isolated
Staging retained `1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`/V10. Exact target
identity is Production Store `1 / 4483_R_SAINT_DENIS / 4483 R. Saint-Denis` in
Organization `1 / LANZHOU_NOODLES / Lanzhou Noodles`.

The [sanitized parity manifest](runtime/ST_DENIS_TWIN_PARITY_MANIFEST.md) and
[inventory evidence](runtime/TWIN-001_PRODUCTION_INVENTORY_EVIDENCE.md) record
the domain classes and concrete safe values. No Staging reconstruction, Twin
Sync, deployment, migration, restart, business-data read, or mutation occurred.
The checkpoint stop was
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`;
its next gate was `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`, later granted
before the current pre-write NO-GO.

## Historical Owner strategic route override (2026-08-10)

The immediate route is now `TWIN-001_ST_DENIS_STAGING_TWIN`: a long-lived
Production-like St-Denis Operational Twin and mandatory pre-Production
validation environment. The former STG-009 manual Phase-A gate, Chinatown
Phase B, and REL-001 Production RC remain preserved but are
`DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY`.

The Twin is not yet established. Existing synthetic St-Denis is
`CURRENT_SYNTHETIC_BASELINE`, not Production-parity evidence. The read-only
inventory is evidence only; the corrected manifest-completion read and any
later reconstruction retry remain independently Owner-gated.

The former read-approval stop and inventory checkpoint are historical. The
checkpoint stop was:
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.

Release maturity follows the canonical Agile Loop policy: an accepted Twin
freezes an immutable RC, Production promotes the same artifact digests rather
than rebuilding the same SHA, recurring drift checks are read-only and never
auto-sync Staging, rollback requires `APPLICATION_ROLLBACK_COMPATIBILITY_GATE`,
and backup existence is not recoverability. These remain future gates; the
current loop is repository-only TWIN-001 inventory review and design-only
reconstruction planning.

## Historical verified STG-008 runtime state (2026-08-10)

Staging is exact `1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c` at Flyway V10. STG-005A and STG-005B
PLAN/EXECUTE/REPLAY are `VALIDATED/CREATED/REPLAYED`; the source menu is
`4 categories / 3 stations / 13 items / 38 options` and replay is `2 -> 2`.
Synthetic Organization, Owner, source Store and credential are ready. No
one-shot is active, the blocked marker is absent, and the lock is empty. This
is not `STAGING_ACCEPTED`; PR #97 is `IN_MAIN` and its Phase-A tooling is
runtime-verified on the exact Staging release. The API-only login/me/workspaces/
overview/logout checks passed. PR #99's repair is deployed, the credential was
privately rotated, and real-Chrome browser-equivalent acceptance passed without
401/403. The pre-repair manual failure remains historical; fresh Owner manual
UI evidence remains pending. No Chinatown or Production action is authorized.

> Status: `ACTIVE_GOVERNANCE_BACKLOG`
>
> Last updated: 2026-08-10, America/Toronto
>
> Features are not incidents. A feature may be requirements-confirmed without
> being authorized for implementation or production provisioning.

## FT-001 - Owner Store Onboarding - Chinatown

| Field | Value |
|---|---|
| feature_id | `FT-001` |
| title | Owner Store Onboarding - Chinatown |
| priority | `HIGH` |
| status | `DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY` |
| target_loop | Chinatown remains deferred. The separate St-Denis Twin, Owner acceptance and exact-RC Production promotion completed for `2661eb76...`; they do not activate this feature. |
| implementation status | `STG-008=PASS`; PR #99's generic repair is deployed at exact `1a3f2e...`. API and browser-equivalent evidence remain valid historical foundation. Owner has deferred manual Phase-A closure behind Twin planning. |
| authority | [AL-003A final menu comparison](agile/AL-003A_FINAL_MENU_COMPARISON.md), [AL-003 technical plan](agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md), [STG-008 entry evidence](runtime/STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md), [STG-008 Flyway guard repair evidence](runtime/STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md), [STG-008 release-rebind repair evidence](runtime/STG-008_RELEASE_REBIND_SERIALIZATION_REPAIR_EVIDENCE.md), [STG-008 non-web request-context repair evidence](runtime/STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md), and [STG-008 one-shot lifecycle repair evidence](runtime/STG-008_ONE_SHOT_LIFECYCLE_REPAIR_EVIDENCE.md). |
| next action | Await a new explicit Owner decision. Do not begin Chinatown, modularize, enter hardware gates, restore/rollback Production, or infer authority from the completed St-Denis RC. |

### Current strategic roadmap

| Phase | State | Boundary |
|---|---|---|
| `PHASE 1 TWIN-001_ST_DENIS_STAGING_TWIN` | `PASS` | Exact V10 Twin parity and safe automated smoke passed |
| `PHASE 2 OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` | `OWNER_ACCEPTED_FOR_EXACT_RC` | Exact `2661eb76...` acceptance supplied the St-Denis release gate |
| `PHASE 2B ST_DENIS_EXACT_RC_PRODUCTION_PROMOTION` | `PASS` | Frozen exact artifacts deployed at Production V10; bounded observation passed |
| `PHASE 3 MODULAR_PROVISIONING_IMPLEMENTATION` | `ARCHITECTURE_DIRECTION_APPROVED_IMPLEMENTATION_DEFERRED` | Opens only after Owner says “可以进行模块化了” |
| `PHASE 4 CHINATOWN_STAGING_ACCEPTANCE` | `DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY` | Existing AL-003/REL-001 preserved; requires future `CHINATOWN_RESUME_GATE` |

### Current AL-003 delivery state

| Package | State |
|---|---|
| PR-A | `IN_MAIN` |
| PR-B | `IN_MAIN` |
| PR-B2 | `IN_MAIN` |
| PR-B3 | `IN_MAIN` |
| PR-B4 | `IN_MAIN` |
| PR-C | `IN_MAIN` |
| PR-D | `IN_MAIN` via PR #52 |
| PR-E | `IN_MAIN` via PR #54 |
| PR-F0 | `IN_MAIN` via PR #55 |
| PR-F | `IN_MAIN` via PR #56 |
| PR #58 attempt evidence | `IN_MAIN` |
| Private-leaf preflight repair / PR #59 | `IN_MAIN` via merge `c3956592da8a33092ab745c7cc6aac05e9babfa7` |
| Owner decisions governance sync / PR #60 | `IN_MAIN` at `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`; documentation only, no runtime action |
| Current project handoff / PR #71 | `IN_MAIN` at `5baada03935e004d80af1e7a36fb7db39bd6abbb`; navigation only, no runtime action |
| Modular architecture / PR #61 | `IN_MAIN` at `bbb1af9520c188b6ef6362e783284ba4001a7e63`; Generic Store Provisioning Engine + Versioned Store Profiles + Reusable Provisioning Modules, architecture only |
| STG-005B Synthetic St-Denis baseline / PR #62 | `IN_MAIN` at `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b`; guarded, versioned, transactional source baseline; no runtime execution implied |
| AL-003S Staging acceptance preparation / PR #63 | `IN_MAIN` at `732d77c89ff067982702426ff918d5e097e1d0fb`; guarded launcher, passive evidence, approval/identity binding, immutable image pin, command plan, acceptance template, and rollback boundary only; no runtime action |
| AL-004 Generic Store Profile contract / PR #64 | `IN_MAIN` at `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6`; declarative version/profile identity, module-reference, canonical fingerprint, and safe-summary contract only; no concrete profile, API, migration, UI, or provisioning execution |
| AL-005A Staff/Table plan / PR #65 | `IN_MAIN` at `8f58bcbfca253c1598b967f4d17c04c0be1cce5b`; planning only; no writer, endpoint, migration, credential, table, or runtime execution |
| Printer Store-isolation repair / PR #66 | `IN_MAIN` at `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3`; rejects cross-Store printer config updates, cross-Store automatic dispatch, and PAD_DIRECT printer-health updates; no migration, endpoint shape, transport, Android, or runtime action |
| AL-005 Printing plan / PR #67 | `IN_MAIN` at `65e3d3ced2b5b05eb36d56ce67e475768ad19dff` | Reusable Store-scoped Printing Provisioning planning only; no writer, endpoint, migration, printer, assignment, mode change, test print, or runtime mutation |
| AL-005B Device/Pad plan / PR #68 | `IN_MAIN` at `9e93573be97cfd01a9ad3efe64d55827854c497a` | Single-layer reusable Device/Pad Provisioning plan; no pairing, token, Worker, endpoint, migration, or runtime mutation |
| AL-006 Activation plan / PR #69 | `IN_MAIN` at `dc682203b2b24bbdb453a5520b297b9051139f13` | Fail-closed workflow plan only; lifecycle and validator are conceptual; no status transition or activation writer |
| REL-001 Production RC plan / PR #70 | `IN_MAIN` at `645d4909625f70fc241d5468382d66a30a030fb1` | Exact-SHA release gates only; no selected candidate, Staging pass, Production deploy, or activation action |
| Post-stack Ground Truth audit / PR #72 | `IN_MAIN` at `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | Capability/runtime gap audit only; no deployment or acceptance |
| STG-006 evidence/governance / PR #73 | `IN_MAIN` at `85d97b7327b2e15aa561ed28a5788b92cedf6f5b` | Passive evidence only; STG-006 PASS, no deployment or mutation |
| OPS-001 secret-safe tooling | `REPOSITORY_COMPLETE` through PR #74 plus control-path repairs #75-#78 | Detached release/env rotation, runtime/Flyway restart evidence, and secret-FD Owner/API helpers; runtime actions remain separately gated |
| Readiness health fingerprint repair / PR #80 | `IN_MAIN` at `39fa284b7bccd64d650c396f2c7532b0a0858b4b` | Missing optional health is classified as `NO_HEALTHCHECK`; present-invalid and unhealthy remain fail-closed |
| Flyway success-token repair / PR #81 | `IN_MAIN` at `63600b13b10a5549d9095a03c94e69a9f880af9f` | PostgreSQL `success::text=true` is accepted exactly; false/abbreviated/invalid history remains fail-closed |
| Restart readiness/fail-closed repair / PR #82 | `IN_MAIN` at `2837ae88e55142c99c6975f8b6575febffc913a1` | Bounded three-endpoint readiness and nonzero-exit blocked-state persistence; exact merged SHA later passed STG-007 |
| STG-007 final evidence/governance / PR #83 | `IN_MAIN` at `2ed56b06f37c9257a655ec334f81e31ca4a518a6` | Documentation/evidence only; no runtime-capability or runtime-state change |
| STG-008 entry evidence/governance / PR #84 | `IN_MAIN` at `828af4e84581dcb051248beee694c307a65210c5` | Sanitized credential-gate entry evidence only; no application, migration, runtime configuration, credential, or data mutation |
| STG-008 guarded one-shot Flyway safety repair / PR #85 | `IN_MAIN` at `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6` | Exact no-migration synthetic one-shot startup reconciliation plus tests/governance; its initial publication made no runtime mutation and it was later included in deployed exact `6753855497...` |
| STG-008 dependency-repair Ground Truth / PR #86 | `IN_MAIN` at `4759a23b1a00d3254936e6c8eeb0ec33012b5145` | Documentation-only closure; no runtime action |
| STG-008 release-rebind serialization repair / PR #87 | `IN_MAIN` at `4b954e09a365fec909ed6da3ddf8fa9f13639cdc` | Dedicated recovery release/env preparation preserves both blocked records and every ordinary action block; it later supported the exact `6753855497...` Staging rebind/deploy/recovery continuation |

The Owner-login acceptance prerequisite is not satisfied by repository code or
deployment alone. Read-only code audit confirms that an active Organization
`OWNER` membership already grants access to every Store in that Organization;
the onboarding flow therefore does not need to create a redundant target-Store
membership for the Owner. STG-005A and onboarding can establish the required
identity/access topology, but they have not run on the evidenced Staging
runtime. STG-008 entry evidence now proves the account/topology rows are
absent and Store ID `1` remains safely allocatable; it also records the
historical credential-contract `NO_GO`. The Owner has now aligned that contract,
but the fresh password-free plan exposed the bounded cloud/Flyway safety-rule
conflict before the command path. Separate Owner-approved exact-SHA recovery
and runtime evidence must still prove the synthetic credential, login,
workspace access, target onboarding, and
authenticated validate/execute calls. No Production credential, raw SQL,
authorization bypass, or real business data may supply that evidence.

The AL-003S preparation closes the reviewed non-web launcher gap for
STG-005A/STG-005B. STG-006 freshly collected passive resource, isolation, and
Staging/Production-continuity evidence. Secret-safe release/env rotation,
sanitized Flyway collection and valid same-image restart now have runtime
evidence. Synthetic topology/source and secret-safe Owner/API calls remain
explicit, separately approved prerequisites rather than inferred capability.

`MERGED_ON_GITHUB` is not sufficient evidence for `IN_MAIN` when a PR's base
is another feature branch. Each stacked layer requires a latest-`main`
promotion, fresh verification, and Owner review in dependency order.

### Goal

Provide a reusable, owner-scoped Store onboarding capability. Chinatown is the
first approved onboarding request, not a hard-coded special case. The existing
Owner must be able to view St-Denis, Chinatown, and organization-wide `All
Stores` data while never creating a Store in an organization they do not own.

### Confirmed business definition

#### Organization and Store

- Chinatown and St-Denis belong to the same Organization.
- Create Store `Chinatown`, suggested code `CHINATOWN`, status `ACTIVE` after
  provisioning acceptance.
- Timezone, tax, language, and base receipt defaults inherit from the selected
  source Store. The present Store model does not yet prove a single per-store
  representation for all four values, so the implementation must identify and
  persist the authoritative existing configuration before activation.
- Start with zero sales. Do not copy orders, sales, analytics summaries, print
  jobs, or inventory balances.

#### Accounts and membership isolation

Create these exact runtime login identifiers only after owner approval:

| Login identifier | Role | Store scope | Initial destination |
|---|---|---|---|
| `staffCT1` | `MANAGER` | Chinatown only | Chinatown administration/dashboard |
| `staffCT2` | `FRONTDESK` | Chinatown only | Chinatown frontdesk |
| `staffCT3` | `FRONTDESK` | Chinatown only | Chinatown frontdesk |
| `staffCT4` | `FRONTDESK` | Chinatown only | Chinatown frontdesk |

- No email is required.
- The initial password is an owner-approved, one-time runtime input. It must be
  BCrypt-hashed and must never be written to Git, migrations, seeders,
  documentation, logs, API responses, or audit metadata.
- Each account receives only an active Chinatown `store_membership`; no
  St-Denis membership may be created. The legacy `users.store_id` must be
  Chinatown or null and cannot grant another Store through fallback behavior.
- Direct St-Denis URLs and APIs must return 403 for these users.

#### Live menu clone

At an independently approved execution phase, clone from the current live menu
of St-Denis, Store ID `1`. `RuntimeDataSeeder`, `menuImportSeed.ts`, and other
repository seed data are historical reference only and must never supply clone
rows or fill missing live data.

The target profile is `CHINATOWN_MENU_2026_02_02`. It creates only
`SOUP_NOODLE`, `DRY_NOODLE`, `SIDE_DISHES`, and `DRINK`, in that order, and
uses the Chinatown PDF prices instead of the superseded Small-13.99 backlog
rule. Dry noodles are ordered Dan Dan then Zha Jiang. Side dishes are ordered
Braised Beef Shank, Spicy Cucumber, Edamame, Seaweed Potato, Sichuan Pepper
Chicken, then Tea Egg.

The new target SKUs are `sichuan_pepper_chicken`, `tea_egg`, `seven_up`, and
`ginger_ale`. Combo 1-4 apply only to their mapped main dishes; Combo 3 includes
a side and tea egg. All five target noodles receive all seven noodle types, and
all active Store 1 add/remove options for reused items are preserved. Tea egg
exists as both a standalone target item and an add-on option. No automatic
schedule or French localization is added.

St-Denis remains unchanged. AL-003 does not clone printers, printer
assignments, devices, staff, tables, orders, payments, credentials, inventory,
analytics, KDS configuration, or production data.

#### Tables

Do not clone tables. Chinatown begins with blank table setup. Its owner/manager
uses the existing table UI to create, edit, split, combine, and change status
within Chinatown only.

#### Printing and Pads

- AL-003 neither clones nor configures printing or Pads. The target remains
  printing-disabled until a separate Owner-approved provisioning loop.
- Any future Chinatown printer endpoint remains on-site runtime configuration
  and must never enter Git or the clone request/evidence record.
- Future Pad pairing, printer module-to-printer assignments, and physical print
  acceptance are separate from the menu-clone transaction and cannot be
  inferred from PR-A. There is no per-device module assignment.

#### Acceptance boundaries

AL-003 is accepted only after the exact Store 1 live-source validation, target
mapping, PDF price/size/Combo rules, transaction rollback, idempotency,
Organization isolation, source invariance, and explicit side-effect exclusions
pass. The broader FT-001 feature still requires separately approved table,
printing, Pad, UI, and field acceptance; it is never accepted by creating a
seed/demo Store.

### Owner decisions effective 2026-08-08

- Chinatown is the second planned real Production Store. FT-001 closes only at
  `Production-ready Chinatown Store`, not at Store creation, API completion, or
  a Staging demonstration.
- The reviewed `CHINATOWN_MENU_2026_02_02` Categories, Stations, 17 items,
  bilingual names, prices, sizes, noodle types, Combo rules, tea egg, extra
  meat, and ordering are frozen as the initial Production target contract.
  Normal post-activation changes use Menu Management and do not expand AL-003.
- Production Store 1 / St-Denis live menu is the only Production clone source.
  Repository seeds and synthetic Staging data are not Production evidence.
- Chinatown's first menu initialization must use the reviewed clone engine:
  create inactive Store, validate, review, execute, verify, then activate.
  Manual Menu Management is not the initial Production provisioning path.
- Organization Owners inherit access to all Stores in their active
  Organization membership. Manager/frontdesk and other Store-scoped staff keep
  explicit target-Store memberships.
- Future Owner UI must offer reviewed versioned menu templates including
  `CHINATOWN_MENU` and a future `ST_DENIS_MENU` profile, both backed by the same
  generic clone/provisioning engine.
- Staging is a persistent Production-like, synthetic-only environment. A
  Synthetic St-Denis baseline must be reproducible without Production
  credentials, database copies, customers, orders, payments, real printers, or
  device secrets.
- Production release strategy is a formal exact-SHA Release Candidate after
  Staging acceptance, Production gap audit, migration review, and
  backup/rollback review. `git pull latest` is not a release process.

### FT-001 completion gap matrix

| Capability | State | Evidence / next boundary |
|---|---|---|
| Generic Owner Organization authorization | `DONE_IN_MAIN` | Active Organization `OWNER` membership grants same-Organization Store access; cross-Organization access remains forbidden. |
| Idempotent inactive Store onboarding | `DONE_IN_MAIN` | AL-002 creates an inactive target and requested Manager/Frontdesk accounts with BCrypt credentials and Store memberships. |
| Generic menu clone transaction, options, replay, locks, API | `DONE_IN_MAIN` | PR-A through PR-F and V10 are repository capability only. |
| Frozen Chinatown Store Profile | `DONE_IN_MAIN` | `CHINATOWN_MENU_2026_02_02` is the approved initial Production target contract. |
| PostgreSQL private-leaf Staging guard | `DONE_IN_MAIN` | PR #59 merged at `c3956592da8a33092ab745c7cc6aac05e9babfa7`; no redeploy is implied. |
| Exact-SHA Staging deployment and Flyway V9/V10 | `STG-007_PASS` / `DEPLOYED_TO_STAGING` | Historical exact `2837ae88...` passed fresh V10 continuation entry, formal preflight, V10-to-V10 deploy, repaired readiness, runtime collection, same-image restart and post-restart verification. Flyway remains exact V10/no-pending; this is not AL-003 Staging acceptance. |
| Synthetic Organization/source/Owner bootstrap | `STG-008_PASS` / `DEPLOYED_TO_STAGING` | Exact `1a3f2e...` retains ready synthetic Organization, Owner, source Store, credential and memberships; A/B execution/replay evidence is complete with no blocked marker or active one-shot. |
| Synthetic target onboarding and Owner target access | `STAGING_PENDING` | Existing onboarding plus Organization Owner access is sufficient; runtime evidence is missing. |
| Synthetic Owner login/workspace/Owner API authorization | `BROWSER_EQUIVALENT_PASS_OWNER_MANUAL_DEFERRED_BY_OWNER_TWIN_PRIORITY` | Exact `1a3f2e...` deployed PR #99, rotated the credential privately and passed API plus real-Chrome browser-equivalent login/session/Organization/Store/dashboard/refresh/logout acceptance without a 401/403. Fresh Owner manual UI evidence remains deferred behind TWIN-001. |
| Reproducible Synthetic St-Denis source-menu baseline | `DEPLOYED_TO_STAGING` via PR #62 capability | Guarded source graph is ready at `4/3/13/38`, replay revision `2 -> 2`; this is not Production or Chinatown acceptance. |
| AL-003 validate/execute/replay/restart acceptance | `DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY` | Chinatown Phase B remains prohibited. Future `CHINATOWN_RESUME_GATE` follows Twin, field-test and later modularization DoD. |
| STG-006 exact-main passive preflight | `PASS` | At STG-006 capture, candidate was `33c6e3c...` and retained Staging was `4397f995...` / V8; this historical PASS does not override the later STG-007 deployment. |
| OPS-001 secret-safe tooling | `REPOSITORY_COMPLETE` through PR #87; STG-007 runtime evidence `PASS` | Release/env rotation, exact deploy, repaired readiness, sanitized runtime collection and same-image restart passed at `2837ae88...`. PR #87's later recovery-only release/env path supported the exact `6753855497...` rebind/deploy/recovery continuation. No credentials or API action occurred. |
| Production Store 1 read-only source capture/drift review | `PRODUCTION_PENDING` | Separate Owner Runtime Gate; only menu-related evidence may be read. |
| Production Chinatown Store/staff/menu provisioning | `PRODUCTION_PENDING` | Exact-SHA Release Candidate and production approval required. |
| Generic Store Profile identity/composition contract | `IN_MAIN` via PR #64 | Exact versioned identity, module references with reviewed expected fingerprints, activation requirements, canonical fingerprint, and safe summaries are repository capability only; no concrete Store Profile or callable workflow is implied. |
| Owner Create Store / Choose Menu Template UI | `NOT_IMPLEMENTED` | Existing Platform Admin template UI is not the approved Owner workflow. |
| Generic Store Profile contract | `IN_MAIN` via PR #64 | Declarative identity/composition contract only; no Owner UI or provisioning execution. |
| Versioned `ST_DENIS_MENU` profile | `NEEDS_NEW_LOOP` | The strict identity for a complete Store Profile is not finalized; it must reuse the generic profile registry/clone engine with no Store ID 3 branch. |
| Staff/Table provisioning module | `IN_MAIN` via PR #65 | Reusable Staff/Access and Table module planning only; no writer. Chinatown remains blank-table/manual setup; future predefined-table writing requires schema, normalization, ownership, and replay decisions. |
| Printing provisioning module | `IN_MAIN` via PR #67 | Single-layer reusable Store-scoped Printing Provisioning plan; no executable writer or runtime mutation. |
| Device/Pad provisioning module | `IN_MAIN` via PR #68 at `9e93573be97cfd01a9ad3efe64d55827854c497a` | Reusable Store-scoped pairing, binding, readiness, and health planning only; no pairing, token, Worker, endpoint, or runtime mutation. |
| Store activation validation/workflow | `IN_MAIN` via PR #69 | Fail-closed plan only; lifecycle and validator are conceptual; no status transition or activation writer. |
| Chinatown Production Release Candidate | `PLAN_ONLY` via PR #70 `IN_MAIN` | Exact-SHA/migration/backup/rollback/resource/deployment gates only; no selected candidate, Staging pass, Production deploy, or Chinatown activation. |
| Chinatown end-to-end field acceptance | `PRODUCTION_PENDING` | Owner/staff login, dine-in order, update, expected tickets, and operational completion remain required. |

### Proposed bounded loop order

The #61-#70 preparation packages below are complete in main. Their historical
dependency order is retained for traceability; it is not the current execution
queue. The current capability matrix and next executable/implementation loop
order are in [Post-Stack Ground Truth Audit](runtime/POST_STACK_GROUND_TRUTH_AUDIT.md).

1. `STG-005B_SYNTHETIC_ST_DENIS_BASELINE`: define a reviewed, idempotent,
   synthetic-only St-Denis menu/configuration baseline using existing generic
   modules and supported APIs.
2. `AL-003S_STAGING_CLONE_ACCEPTANCE`: exact-SHA deployment, V9/V10,
   STG-005A bootstrap, target onboarding, Owner login/access, source baseline,
   validate, execute, replay, restart, and Production-continuity evidence.
3. `AL-004_GENERIC_STORE_PROFILE_FRAMEWORK`: Owner Create Store UI, versioned
   template selection, and the future `ST_DENIS_MENU` profile without a second
   clone engine.
4. `AL-005A_STAFF_TABLE_PROVISIONING_MODULES`: reusable staff/access and table
   provisioning inputs around the existing onboarding authority and a new
   Store-safe table planner/provisioner boundary. The preparation contract is
   [AL-005A Staff and Table Provisioning Module Plan](agile/AL-005A_STAFF_TABLE_PROVISIONING_MODULE_PLAN.md).
5. `AL-005_PRINTING_PROVISIONING_TEMPLATE`: Store-scoped printer/module
   assignment inputs and safe physical-print acceptance gates. The bounded
   preparation is [AL-005 Printing Provisioning Module Plan](agile/AL-005_PRINTING_PROVISIONING_MODULE_PLAN.md).
6. `AL-005B_DEVICE_PAD_PROVISIONING_MODULE`: reusable Store device binding and
   Pad pairing/worker readiness gates without embedding device secrets. The
   bounded preparation is [AL-005B Device and Pad Provisioning Module Plan](agile/AL-005B_DEVICE_PAD_PROVISIONING_MODULE_PLAN.md).
7. `AL-006_STORE_ACTIVATION_WORKFLOW`: aggregate validation and explicit Store
   activation after all provisioning modules pass. The bounded preparation is
   [AL-006 Store Activation Workflow Plan](agile/AL-006_STORE_ACTIVATION_WORKFLOW_PLAN.md).
8. `REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE`: Store 1 read-only source
   capture, Production gap/migration/backup/rollback review, and exact-SHA
   approval. The bounded preparation is
   [REL-001 Chinatown Production Release Candidate Plan](agile/REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE_PLAN.md).
9. `ACT-001_CHINATOWN_PRODUCTION_ACTIVATION`: execute approved provisioning and
   complete Owner/staff/order/printing/Pad field acceptance.

These names record dependency order only. They do not authorize implementation,
runtime mutation, Production access, or deployment.

Historical short labels are preserved rather than silently reused: the AL-001
plan's `AL-004` UI/configuration scope is split across the current AL-004,
AL-005A, and AL-005 packages, while its historical `AL-005` Production scope
maps to REL-001 and ACT-001. The canonical mapping is maintained in
[STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md).

The shared architecture and anti-hardcode boundary for these loops is
[STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md).

### Explicit non-goals

- No automatic failed-job reprint or background daemon.
- No credential, token, printer endpoint, or production data in source control.
- No automatic production deployment, initialization, restore, `docker compose
  down -v`, or data deletion.
- Future single-store first-login auto pairing is not in the first FT-001
  implementation batch.
