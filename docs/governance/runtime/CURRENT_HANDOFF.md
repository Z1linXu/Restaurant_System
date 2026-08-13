# Current Project Handoff

## Current final productization planbook and Phase A authorization (2026-08-13)

The Owner has closed the PR #126 30-answer gate. The active authority is now
[FINAL_PRODUCTIZATION_PLANBOOK](../agile/FINAL_PRODUCTIZATION_PLANBOOK.md).
It preserves the three-phase route:

```text
PHASE A — MODULAR PRODUCTIZATION
↓
PHASE B — OWNER NEW STORE PROVISIONING
↓
PHASE C — REAL MULTI-STORE PROOF
```

The next executable loop after fresh fetch/reread is:

```text
PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION
```

Owner follow-up A0.1 decision:

```text
PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_REFINEMENT
```

The Owner accepted the A0 data-model direction but rejected free-form Owner Size
editing as final product UX. A0.1 must support only system-controlled
Small/Regular/Large Size definitions and Store-level Size/Combo pricing
policies. The Owner approved the additive pricing policy schema direction:

```text
PHASE_A0_1_PRICING_POLICY_SCHEMA_CHANGE_APPROVAL
```

Evidence/design:
[PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_SCHEMA_GATE](../agile/PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_SCHEMA_GATE.md).
Implementation evidence:
[PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_IMPLEMENTATION_EVIDENCE](../agile/PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_IMPLEMENTATION_EVIDENCE.md).
Staging evidence:
[PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_STAGING_EVIDENCE](../agile/PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_STAGING_EVIDENCE.md).
The implementation package adds additive Flyway V11, Store-level Pricing Rules,
system-controlled Size Configuration, item Combo allowed policy, catalog/hash/
IndexedDB pricing policy semantics, and a Size/Combo `price_delta`
compatibility mirror only for rollback safety. Production remains no-mutation.
The Owner accepted the manual A0.1 Staging UX retest:

```text
OWNER_A0_1_PRICING_UX_RETEST = PASS
```

Phase A is authorized; Phase B/C are not. Production remains no-mutation. A0
used repository changes, tests, Agent 6, PR/auto-merge, exact-SHA Staging
deployment and automated validation.

A0.1 is deployed to exact-SHA Staging at
`ed3e4cdbf38c4d8812620baf64cd42ce3a229431`; Staging advanced from Flyway V10
to V11 and automated validation passed.

Current authorized bounded implementation loop:

```text
PHASE_A0_2_STORE_COMBO_CONFIGURATION
```

A0.2 must implement Store-level Combo Contents configuration, not a second
combo engine and not a pricing source. `store_pricing_policies.combo_delta`
remains the canonical Combo price; per-item `menu_item_options` `COMBO` rows
remain item `COMBO_ALLOWED`; `store_combo_components` is the Store-scoped
canonical content table for reviewed `COMBO_EGG` and `COMBO_SIDE` components.
New ordering may use stable negative transport IDs in frozen snapshots, but the
enabled/name/code state remains Store Combo Configuration. Evidence:
[PHASE_A0_2_STORE_COMBO_CONFIGURATION_IMPLEMENTATION_EVIDENCE](../agile/PHASE_A0_2_STORE_COMBO_CONFIGURATION_IMPLEMENTATION_EVIDENCE.md).
Runtime evidence:
[PHASE_A0_2_STORE_COMBO_CONFIGURATION_STAGING_EVIDENCE](../agile/PHASE_A0_2_STORE_COMBO_CONFIGURATION_STAGING_EVIDENCE.md).

A0.2 merged through PR #134 and is deployed to exact-SHA Staging at
`90ac0cb0496161b12c47cff00573b56b4abc961c`. Staging advanced to Flyway V12;
automated A0.2 validation passed, including Combo API, Combo Egg/Side enablement,
menu revision/hash, IndexedDB cache contract, draft/submitted snapshot
preservation, disabled-component rejection, MOCK printing/kitchen routing,
Store isolation and authorization. The Owner completed manual Staging retest:

```text
OWNER_A0_2_MANUAL_STAGING_RETEST = PASS
```

Historical A0.2 closure:

```text
PHASE_A0_2_STORE_COMBO_CONFIGURATION_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

Current A0 deployed evidence:
[PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION_EVIDENCE](../agile/PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION_EVIDENCE.md).
The audited current size model is `OPTION + MODIFIER_GROUP + PRICE_DELTA`, and
the deployed implementation uses the existing `menu_item_options` engine as the canonical
`MenuItem -> SizeVariant[1..N]` representation. No schema migration or
Production runtime action occurred.

Current A0 result:

```text
A0_CODE = COMPLETE
A0_STAGING_DEPLOYED = YES
A0_AUTOMATED_VALIDATION = PASS
A0_OWNER_UI_ACCEPTANCE = PASS_FOR_A0_1_PRICING_UX
```

Staging now runs exact
`90ac0cb0496161b12c47cff00573b56b4abc961c`, Flyway V12, Printing
`MOCK/true`, four enabled logical printers and three enabled assignments.
Production continuity remained HTTP 200/200 for system/menu health. Production
was not deployed, restarted, migrated or reconfigured during A0.2.

Current authorized continuous execution:

```text
PHASE_A1_MODULE_CATALOG
→ PHASE_A2_MODULE_DEPENDENCY_GRAPH
→ PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION
```

Do not stop between A1/A2/A3 for ordinary PASS/merge/review/deploy evidence.
Stop only for a TRUE OWNER GATE, or after A3 at
`PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION_COMPLETE_WAITING_FOR_PHASE_A4_OWNER_CONTINUATION`.
Do not start A4, Phase B/C, Chinatown, Sainte-Catherine or Production work.

A1 implementation navigation:

- Technical evidence: [PHASE_A1_MODULE_CATALOG](../agile/PHASE_A1_MODULE_CATALOG.md)
- Machine-readable catalog:
  `backend/src/main/resources/module/module-catalog.v1.json`
- Static validation:
  `backend/src/test/java/com/restaurant/system/modules/ModuleCatalogContractTest.java`

A1 defines canonical module keys, capability classification, current feature
flag classification, route/API mapping and authorization mapping. It does not
create Store module persistence, deploy Staging, touch Production or start A2
validation before A1 enters `main`.

A1 is now accepted and merged:

```text
PHASE_A1_MODULE_CATALOG = PASS
AGENT_6 = A1_ACCEPT
PR = #137
MERGE_SHA = 34169152c6d48ecf503b441fe7428416c399d0a9
```

A2 implementation navigation:

- Technical evidence:
  [PHASE_A2_MODULE_DEPENDENCY_GRAPH](../agile/PHASE_A2_MODULE_DEPENDENCY_GRAPH.md)
- Machine-readable dependency graph:
  `backend/src/main/resources/module/module-dependency-graph.v1.json`
- Reusable validator:
  `backend/src/main/java/com/restaurant/system/modules/ModuleDependencyValidator.java`
- Focused validation:
  `backend/src/test/java/com/restaurant/system/modules/ModuleDependencyValidatorTest.java`

A2 validates `REQUIRES`, `CONFLICTS_WITH`,
`REQUIRES_ENVIRONMENT_CAPABILITY`, and `REQUIRES_HARDWARE_CAPABILITY`
relationships. Unknown modules and invalid graph entries fail closed. A2 does
not create Store module persistence, deploy Staging, touch Production, or start
A3 before A2 enters `main`.

A2 is now accepted and merged:

```text
PHASE_A2_MODULE_DEPENDENCY_GRAPH = PASS
AGENT_6 = A2_ACCEPT
PR = #138
MERGE_SHA = 1780c8934a502709844713d91c493b076e714983
```

A3 implementation navigation:

- Technical evidence:
  [PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION](../agile/PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION.md)
- Additive migration:
  `backend/src/main/resources/db/migration/V13__add_store_modules.sql`
- Store module service/API:
  `backend/src/main/java/com/restaurant/system/modules/StoreModuleServiceImpl.java`
  and `backend/src/main/java/com/restaurant/system/modules/StoreModuleController.java`
- Store Context contract extension:
  `backend/src/main/java/com/restaurant/system/common/auth/dto/StoreContextResponse.java`
- Focused validation:
  `backend/src/test/java/com/restaurant/system/modules/StoreModuleServiceImplTest.java`
  and `backend/src/test/java/com/restaurant/system/modules/StoreModuleControllerTest.java`

A3 adds canonical Store-scoped `store_modules` state and the Store module
read/config contract. It does not implement A4 profiles, A6 backend gating, A7
frontend gating, A8 hardware management, Chinatown, Sainte-Catherine or
Production deployment. Because A3 includes Flyway V13/runtime changes, it must
be exact-SHA deployed to Staging and validated after PR merge before final
Phase A1-A3 report.

## Current final productization roadmap audit (2026-08-12)

Planning-only audit is complete for the new final productization route:

```text
currently can operate one Store
    -> build once, configure many
    -> reliably create and operate N Stores
```

Fresh repository authority was
`origin/main@06581f6034539369af544a8fc29ed8ca55800ce8`. Current Production and
Staging both retained exact application artifact
`3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`; read-only image identity and
`/api/v1/system/health` checks passed. Runtime remained unchanged.

The current route has exactly three main phases:

1. `PHASE_A_MODULAR_PRODUCTIZATION`
2. `PHASE_B_OWNER_STORE_PROVISIONING`
3. `PHASE_C_REAL_MULTI_STORE_PROOF_CHINATOWN_AND_SAINTE_CATHERINE`

Do not merge Phase B into Phase A. Do not create Chinatown or
Sainte-Catherine manually before Phase B is accepted. The Owner field-test
bug-fix loop remains a side loop; only confirmed P0/P1 blockers stop normal
product development. See
[FINAL_PRODUCTIZATION_THREE_PHASE_ROADMAP_AUDIT](../agile/FINAL_PRODUCTIZATION_THREE_PHASE_ROADMAP_AUDIT.md).

Current unique stop:
`FINAL_PRODUCTIZATION_AUDIT_COMPLETE_WAITING_FOR_OWNER_30_ANSWERS`.

## Current Production three-reliability promotion result (2026-08-12)

`THREE_RELIABILITY_REPAIRS_PRODUCTION_RC_PROMOTION` is complete. Exact
Staging-tested runtime-sensitive SHA
`3ec4d88a47f68e05b92d9246bfd63af2d1f297f9` was frozen as
`RC-THREE-RELIABILITY-20260812-3EC4D88` and promoted to existing Production
St-Denis by exact backend/frontend image ID. Latest main during promotion was
`47584d40e9a4f65cd719d8ea898d723bd8dba64f`, but the Production application
candidate remained exact `3ec4d88...`.

Production retained Flyway V10, database container/state root, Store
`4483_R_SAINT_DENIS`, `PAD_DIRECT` Printing, four logical printers, three
assignments and existing configuration. Fresh backup and V10 isolated restore
passed. Rollback is application-only to the previous `2661eb76...` image pair
with DB remaining V10. No Production restore, downgrade, Flyway history edit,
Staging MOCK copy, physical printer binding, Pad pairing, Chinatown or
modularization occurred.

Evidence:
[PRODUCTION_THREE_RELIABILITY_RC_PROMOTION_EVIDENCE](PRODUCTION_THREE_RELIABILITY_RC_PROMOTION_EVIDENCE.md).
Current unique stop:
`THREE_RELIABILITY_REPAIRS_PRODUCTION_PROMOTED_WAITING_FOR_OWNER_FIELD_RETEST`.

## Current Production promotion tooling dependency repair (2026-08-12)

Historical same-round dependency repair: the Owner approved `THREE_RELIABILITY_REPAIRS_PRODUCTION_RC_PROMOTION`, but
fresh preflight found a tooling-only blocker before Production deployment:
the previous application artifact and retained Production control checkout are
different identities, and V10 backup rehearsal needs an explicit V10 ledger
target. The bounded repository repair updates the promotion/backup helpers and
tests only. It does not deploy, restart, migrate, back up, restore or
reconfigure Production by itself.

Evidence file:
[THREE_RELIABILITY_PRODUCTION_PROMOTION_TOOLING_REPAIR_EVIDENCE](THREE_RELIABILITY_PRODUCTION_PROMOTION_TOOLING_REPAIR_EVIDENCE.md).

## Current Owner field-test three-reliability repair batch (2026-08-11)

After exact-RC Production promotion, the Owner resumed
`OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` and authorized the Staging/repository-only
`STAGING_THREE_RELIABILITY_REPAIR_BATCH`. Production is out of scope except for
lightweight continuity observation; no Production deploy, restart, Flyway,
configuration, printer, Pad, business-data or credential action is authorized.

Repository repair entered `main` through PR #122 at
`3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`, exact Staging was deployed at
that SHA, Flyway remained V10, Printing remained `MOCK/true`, and automated
Staging MOCK smoke passed. The package repaired three bounded field-test
findings before Owner retest:

1. PAD sleep/background must not indefinitely block another active print worker.
2. long-lived Pad menu snapshots must be revision-aware while retaining
   offline-first IndexedDB fallback, and locked drafts must show a visible
   click-lock state.
3. automatic printing outbox dispatch must remove accidental global
   serialization by using bounded Store+printer keyed execution while
   preserving same-printer FIFO, retry, Store isolation and duplicate safety.

Evidence file:
[STAGING_THREE_RELIABILITY_REPAIR_BATCH_EVIDENCE](STAGING_THREE_RELIABILITY_REPAIR_BATCH_EVIDENCE.md).
Current unique stop:
`OWNER_FIELD_TEST_THREE_RELIABILITY_REPAIRS_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST`.

## Current Production promotion result (2026-08-11)

Existing Production St-Denis now runs the exact frozen backend/frontend image
pair for accepted application SHA `2661eb76c36dd9aa58db94ceacd278242ef4c9ab`
at Flyway V10. Exact RC, backup/integrity/isolated restore, V7-to-V10 rehearsal,
old-app-on-V10 rollback compatibility, Agent 6, second-start/no-pending and
bounded observation all passed. The previous application images remain
available; application-only rollback on V10 is compatible if a future severe
incident requires it. No DB restore is authorized. No Chinatown, Store/menu,
printer, Pad, credential or business-data action is implied.

Current unique stop:
`PRODUCTION_EXACT_RC_PROMOTED_POST_DEPLOY_OBSERVATION_PASS`. See
[exact-RC Production evidence](PRODUCTION_ST_DENIS_EXACT_RC_PROMOTION_EVIDENCE.md).

## Historical Production promotion preparation (2026-08-11)

Fresh Owner authority promotes only the exact Staging-accepted candidate
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab`, never later main. Fresh passive
runtime evidence retains Production `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`
at V7 and binds accepted backend/frontend image IDs. The prepared RC record is
`docs/governance/runtime/RC_ST_DENIS_20260811_2661EB76.json`.

The current repository package repairs the unsafe relative-state/rebuild path
without changing application code or migrations. It is not deployment evidence:
rollback compatibility, migration rehearsal, restore rehearsal, fresh backup,
final RC freeze and Agent 6 remain mandatory before any Production lifecycle
action. Chinatown and every provisioning/activation action remain blocked.
Historical stop (superseded):
`RC_PREPARED_WAITING_FOR_MANDATORY_PROMOTION_GATES`.

## Historical superseded Owner field-test Printing bug repair (2026-08-11)

This historical package was the next `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP`
slice after Staging MOCK printing was verified. The Owner reported GRAB
abbreviation defects, Frontdesk receipt quantity aggregation, GRAB fried
quantity symbol mismatch, and a PAD_DIRECT screen-off reliability issue. Issue
6 is audit-only and does not authorize queue/concurrency behavior changes.

Repository repair entered `main` through PR #117 at
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab`, was deployed to exact Staging at
that SHA, and passed automated MOCK smoke after resolving Agent 6
lifecycle-safety blocks. Production remains unchanged except for allowed
lightweight continuity checks. See
[Owner field-test printing fixes evidence](OWNER_FIELD_TEST_PRINTING_FIXES_EVIDENCE.md).

## Historical Staging MOCK Printing field-test override (2026-08-11)

Owner field testing was active. The bounded package
`STAGING_MOCK_PRINTING_FIELD_TEST_ENABLEMENT` completed through PR #114 and was
superseded in current runtime by the field-test repair package above. Exact
Staging now runs `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` / V10 with
Printing `MOCK/true`, 4 endpoint-free logical printers and 3 enabled
assignments.
Production remained read-only and unchanged at `4667f3c...` / V7 during that
historical Staging-only package.

The bounded dependency repair adds a generic runtime printing-mode allowlist
and endpoint-configuration policy so Staging can permit only
`DISABLED,MOCK`, never `REAL` or `PAD_DIRECT`, while retaining the shared MOCK
renderer/job/dispatch path. Automated submit/update/reprint smoke and browser
Printing Settings verification passed, and PR #117's field-test printing fixes
smoke now also passes. Historical stop, superseded by exact-RC Owner acceptance:
`HISTORICAL_OWNER_FIELD_TEST_PRINTING_FIXES_DEPLOYED_WAITING_FOR_OWNER_RETEST`. See
[field-test evidence](STAGING_MOCK_PRINTING_FIELD_TEST_EVIDENCE.md).
That package authorized no physical printer/Pad action or Production mutation.

## Historical operational Twin readiness override (2026-08-10)

The approved reconstruction loop completed on exact Staging
`53209823fa320cc56c31d04ee5c7719a83a78acc` / Flyway V10. Manifest v2 parity,
independent Staff credentials, safe automated order/beverage smoke and final
health passed; `BLOCKING_BEHAVIOR_DIFFERENCE=0`. Production remained unchanged
and read-only at `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` / V7. See
[operational Twin evidence](TWIN-001_ST_DENIS_OPERATIONAL_TWIN_EVIDENCE.md).

Historical reconstruction stop:
`TWIN-001_ST_DENIS_OPERATIONAL_TWIN_READY_WAITING_FOR_OWNER_FIELD_TEST`.
The Owner later opened and completed that field-test route for the exact
candidate. This historical Twin evidence did not authorize hardware gates,
Chinatown, modularization, REL-001 or Production promotion by itself.

## Historical reconstruction execution override (2026-08-10)

Owner approval `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL` is active. Fresh
`origin/main=5f89bdeea6f9a6810c0a38d6d94a59b2156bd6ba`; Production remains V7
read-only and Staging remains exact `1a3f2e...`/V10 at the retained synthetic
baseline. The manifest-v2-bound, zero-delete projector and secret-FD Staff API
reconciler are repository verified and awaiting Agent 6/PR/merge before the
approved runtime apply continues. See
[tooling evidence](TWIN-001_STAGING_RECONSTRUCTION_TOOLING_EVIDENCE.md).

## Historical manifest v2 readiness checkpoint (2026-08-10)

The manifest-v2 collection baseline was
`origin/main=53c217f893aa60e365f3ebb1b3de989862857eae`.
The corrected Production/Staging read-only loop produced a deterministic,
schema-valid [manifest v2](ST_DENIS_TWIN_PARITY_MANIFEST_V2.json); Staging was
not reconstructed at that checkpoint. Historical stop:
`TWIN-001_MANIFEST_V2_RECONSTRUCTION_READY_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
That gate is now granted and superseded by the current execution override.

## Historical reconstruction NO-GO override (2026-08-10)

Fresh Git Ground Truth is
`origin/main=295ed4b1278750dfc5492c3109e0ac767e158ffd`. The Owner granted
`TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`, but pre-write source validation
stopped before runtime entry. The retained manifest lacks deterministic
row-level values required by its own writer contract and records multiple
table/column names that cannot exist in the checksum-identical V7 repository
schema. No Staging or Production action occurred.

Local isolated PostgreSQL 16.14 evidence proves the intended, append-only
`V7 -> V8 -> V9 -> V10` path, preserves a representative St-Denis safe
configuration fingerprint and counts, passes current-candidate JPA/health, and
reports no migration on second startup. The version delta is explicitly
`CURRENT_PRODUCTION_VERSION_DIFFERENCE`; it does not authorize downgrading
Staging. Aggregate `SCHEMA = BLOCKING_BEHAVIOR_DIFFERENCE` and
`TWIN-001 = NO_GO` remain until the complete reconstructed Twin runs on V10.

See
[reconstruction schema/input NO-GO evidence](TWIN-001_STAGING_RECONSTRUCTION_SCHEMA_NO_GO_EVIDENCE.md).
Unique stop:
`TWIN-001_RECONSTRUCTION_NO_GO_WAITING_FOR_MANIFEST_COMPLETION_READ_APPROVAL`.
Next TRUE OWNER GATE:
`TWIN-001_RECONSTRUCTION_MANIFEST_COMPLETION_READ_APPROVAL`.

## Historical verified Production inventory checkpoint (2026-08-10)

The Owner-approved read gate has completed. Fresh repository Ground Truth is
`origin/main=34ef8c577dd5e8464ef885bf235b0bece0018503`; retained Production is
`4667f3c35f85c9f8538f82789d9df1531d4fbc9e`/Flyway V7 and isolated Staging is
`1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`/Flyway V10. Production Store
`1 / 4483_R_SAINT_DENIS / 4483 R. Saint-Denis` belongs to Organization
`1 / LANZHOU_NOODLES / Lanzhou Noodles`. The read was bounded, explicit-column,
Store-scoped and read-only; no Production business data, secrets, Staging
write, Twin Sync, deployment, migration or restart occurred.
`IN_MAIN`, `DEPLOYED_TO_STAGING`, `STAGING_ACCEPTED`, and
`DEPLOYED_TO_PRODUCTION` remain distinct classifications.

See [sanitized parity manifest](ST_DENIS_TWIN_PARITY_MANIFEST.md) and
[inventory evidence](TWIN-001_PRODUCTION_INVENTORY_EVIDENCE.md). Checkpoint
stop: `TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
Its next Owner Gate was `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`, later
granted before the current pre-write NO-GO.

## Historical Owner strategic route override (2026-08-10)

The former `STG-009 Phase A -> Chinatown Phase B -> REL-001` route is preserved
as historical planning but is deferred. The current route is planning
`TWIN-001_ST_DENIS_STAGING_TWIN`, whose designated long-term role is a
Production-like St-Denis Operational Twin and mandatory pre-Production
validation environment.
The former route is explicitly
`DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY`; it remains
preserved rather than cancelled.

The Twin is not yet established. Existing synthetic St-Denis data is
`CURRENT_SYNTHETIC_BASELINE`, not Production-parity evidence. The completed
read-only inventory is evidence, not a reconstruction or synchronization.

The former read-approval stop and inventory checkpoint are historical. The
checkpoint stop was:
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
See [TWIN-001 St-Denis Twin Plan](../agile/TWIN-001_ST_DENIS_STAGING_TWIN_PLAN.md).

## Historical verified continuation override (2026-08-10)

The recent Twin governance closure merges are governance-only in `main`; the
exact `origin/main` SHA must be freshly fetched before the next action. Staging remains exact
`1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`, Flyway is
V1--V10 with no pending or failed migration, and Printing is disabled.
STG-005A PLAN/EXECUTE/REPLAY are `VALIDATED/CREATED/REPLAYED`; STG-005B is
`VALIDATED/CREATED/REPLAYED` with `4/3/13/38` and replay revision `2 -> 2`.
Synthetic Organization, Owner, source Store and credential are ready. No
one-shot is active, the blocked marker is absent, and the lock is empty.
Formal preflight, V10-to-V10 deploy, readiness, private credential rotation,
secret-safe API acceptance and real-Chrome browser-equivalent login/
Organization/Store/dashboard/refresh/logout checks passed without a 401/403.
Fresh Owner post-repair manual UI evidence remains pending and is deferred
behind TWIN-001; the retained browser-equivalent result is historical Phase-A
foundation. The former
stop `STG-009_PHASE_A_BROWSER_EQUIVALENT_PASS_WAITING_FOR_OWNER_MANUAL_UI_ACCEPTANCE`
is historical. The current stop is
`TWIN-001_STAGING_RECONSTRUCTION_APPROVED_DEPENDENCY_REPAIR_IN_PROGRESS`.
The next TRUE OWNER GATE after operational readiness is the Owner field-test
loop; Chinatown onboarding/clone remains deferred and prohibited.

See [STG-008 synthetic runtime progress evidence](STG-008_SYNTHETIC_RUNTIME_PROGRESS_EVIDENCE.md)
and [STG-009 browser-equivalent acceptance evidence](STG-009_PHASE_A_BROWSER_EQUIVALENT_ACCEPTANCE_EVIDENCE.md).

> This Handoff is a navigation snapshot only.
> Git ground truth, `ALIVE_RUNTIME_PLANBOOK.md`, `FEATURE_BACKLOG.md`,
> `AGILE_LOOP_OPERATING_MODEL.md` and applicable Technical Plans remain
> authoritative. If this file conflicts with those sources, the authoritative
> sources win.
>
> Snapshot date: 2026-08-10, America/Toronto
>
> Runtime freshness: PR #82 entered `main` at `2837ae88...` with the bounded
> restart-readiness/fail-closed repair. A fully fresh Owner-authorized
> V10-to-V10 continuation then bound, preflighted, built and deployed exact
> `2837ae88...` to isolated Staging. Repaired readiness, sanitized runtime
> collection, one same-image restart, and post-restart verification all passed.
> Flyway remained exact V10, health returned `200/200/200`, printing and
> isolation remained unchanged, and Production continuity remained unchanged.
> `STG-007 = PASS`. PR #83 then merged only that evidence/governance into
> `main@2ed56b06...`; runtime remained exact `2837ae88...`. The first STG-008
> entry stopped read-only at the credential decision, and PR #84 placed that
> sanitized evidence in `main@828af4e8...`. The Owner then approved
> `STG005_OWNER_20260808_R01` while retaining the password guard. Fresh exact
> readiness passed, but the password-free STG-005A plan one-shot stopped before
> its command/data path because the older cloud safety rule rejected required
> Flyway-disabled mode. Cleanup and zero-write continuity passed, and the
> launcher retained blocked state. PR #85 merged the bounded startup-safety
> repair and PR #86 closed its Ground Truth. The next authorized continuation
> freshly reconfirmed exact Staging `2837ae88...`, V10, zero synthetic state,
> the reviewed blocked pair, Printing/isolation and Production continuity. It
> stopped before Batch A mutation because the ordinary release path could not
> legally cross the retained block. PR #87 merged the dedicated recovery
> release-rebind repair at `4b954e09...`; its initial publication state is
> historical. That runtime-sensitive merge superseded the authorization bound
> to `4759a23b...`. A later Owner-authorized continuation bound and deployed
> exact `6753855497b8c47be99a8d88ae9d9961653addb0` to Staging at Flyway V10,
> passed formal preflight/readiness and recovered only the reviewed old blocked
> records. The next password-free plan failed before its command/data path
> because non-web startup required a servlet request context; cleanup and
> zero-write continuity passed, and a new blocked pair was retained. The
> bounded repository repair entered `main` as PR #89 at `434c9cc...`. PR #90
> then entered `main@2a6c30a...`. The Owner-authorized continuation deployed
> that exact candidate to isolated Staging at V10, passed preflight/readiness,
> and recovered only the reviewed prior blocked pair. The fresh password-free
> plan reached `VALIDATED`, proving the request-context repair in the runtime,
> but a non-web WebSocket broker kept the JVM alive to its 600-second timeout.
> Its scoped finalizer then encountered Compose `--rm` already in progress and
> correctly retained a new blocked pair. There was no credential read or
> synthetic business write; Staging returned to 200/200/200 with Printing
> disabled and Production continuity remained unchanged. A narrow repository
> lifecycle repair entered `main` through PR #91 at `9a776d3...`. Later
> authorized continuations deployed and verified its successors; this is now
> historical context and grants no current rebind authority. Production
> remained unchanged.

## 1. Project mission

Restaurant System is moving from one operational Store to reusable multi-Store
provisioning without destabilizing current restaurant operations.

- St-Denis is the current Production Store.
- St-Denis Twin planning and Owner field-test/bug-fix acceptance completed for
  exact `2661eb76...`; its exact-RC Production promotion is the current closed
  result.
- Chinatown is still the planned second real Production Store, but is deferred
  until the Twin, field-test loop and later modularization gates complete.
- A future third Store matching St-Denis should reuse a reviewed St-Denis
  profile, not copied code or data scripts.
- The direction remains multi-Store SaaS-style provisioning through shared
  modules and Profiles. No next implementation or runtime route starts from the
  completed St-Denis promotion without a new Owner decision.

## 2. Current Git ground truth

| Item | Verified value | Classification |
|---|---|---|
| runtime-sensitive deployed candidate | `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` | `IN_MAIN` through PR #117, `STAGING_ACCEPTED`, and now `DEPLOYED_TO_PRODUCTION` through frozen RC `RC-ST-DENIS-20260811-2661EB76`. |
| exact deployed Staging runtime | `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` | `DEPLOYED_TO_STAGING`; Flyway V10, reconstructed Operational Twin, MOCK/true, health passed, restart count zero. |
| exact deployed Production application | backend `sha256:2db920f0...`, frontend `sha256:233cc07d...` | Exact accepted SHA `2661eb76...`; Flyway V10; fixed DB state/container retained; post-deploy observation PASS. |
| Owner workspace | `main@ba169ed8b689ddef8dffe94deee82fea191cdcfb`, dirty with Owner work | Local checkout is behind `origin/main`; it was not modified by this handoff |
| Runtime-sensitive delivery package | Owner field-test printing fixes / PR #117 | `STAGING_ACCEPTED` through exact `2661eb76...`; fresh Owner acceptance attested |
| Browser-equivalent evidence / PR #101 | merge `aec59af93a9bf42ce3d167a579a19be80eadc9b0` | `IN_MAIN`; evidence/governance only, not deployed runtime |
| Operational Twin closure PR | [PR #112](https://github.com/Z1linXu/Restaurant_System/pull/112) | `IN_MAIN` at `9715d2447d5781e2437917aafc1fb1b6b4a5250f`; documentation/evidence only, not deployed |

`IN_MAIN`, `DRAFT_PR`, `STACKED_ONLY`, `PREPARATION_ONLY`,
`DEPLOYED_TO_STAGING`, and `DEPLOYED_TO_PRODUCTION` are distinct states. A
GitHub Merged badge into a non-main base is not evidence that work entered
`main`.

### Relevant registered worktrees at this audit

| Worktree | Branch / purpose | State |
|---|---|---|
| `/Users/xuzilin/projects/Restaurant_System` | Owner `main` workspace | Dirty with Owner work; behind `origin/main` and untouched |
| `/private/tmp/restaurant-stg006-preflight` | `codex/stg-006-exact-main-preflight` | Retained historical PR #73 worktree |
| `/private/tmp/restaurant-ops001-tooling` | `codex/ops-001-staging-secret-safe-tooling` | Retained historical OPS-001 repository worktree |
| `/private/tmp/restaurant-post-stack-audit` | `codex/post-stack-ground-truth-audit` | Retained historical PR #72 worktree |
| `/private/tmp/restaurant-current-handoff` | `codex/current-project-handoff` | Retained historical PR #71 worktree |
| `/private/tmp/restaurant-stg007-execution` | `codex/stg007-v10-continuation-final-evidence` | Retained historical STG-007 evidence/governance worktree; Owner workspace untouched |
| `/private/tmp/restaurant-stg008-execution` | `codex/stg008-synthetic-topology-source` | Current isolated STG-008 `NO_GO` evidence/governance worktree; Owner workspace untouched |
| `/private/tmp/restaurant-stg008-resume` | retained PR #85/#86 historical repair and closure worktree | Historical isolated repository worktree; Owner workspace untouched |
| `/private/tmp/restaurant-stg008-recovery` | current continuous Staging/evidence worktree | Owner workspace untouched; reviewed Staging actions and evidence publication completed, with no Production mutation |
| `/private/tmp/restaurant-pr61-rebuild` through `/private/tmp/restaurant-pr65-rebuild` | merged #61-#65 branch worktrees | Retained historical worktrees; not current delivery inputs |

No registered #69/#70 rebuild worktree remains. Historical worktrees were not
removed by this audit because their branches/evidence are retained and cleanup
was not required for the governance correction.

## 3. Completed PR dependency map

GitHub and main ancestry were verified on 2026-08-08. Every PR below is
closed, merged, and `IN_MAIN`.

| PR | Package | Base | Head | State | Depends on | In main? | Owner action |
|---|---|---|---|---|---|---|---|
| #61 | Modular architecture foundation | `main` | merge `bbb1af9520c188b6ef6362e783284ba4001a7e63` | `IN_MAIN` | PR #71/main | Yes | Main capability; no runtime behavior |
| #62 | STG-005B Synthetic St-Denis baseline | `main` | merge `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b` | `IN_MAIN` | #61/main | Yes | Main capability; no runtime execution |
| #63 | AL-003S Staging acceptance preparation | `main` | merge `732d77c89ff067982702426ff918d5e097e1d0fb` | `IN_MAIN` | #62/main | Yes | Repository-only acceptance preparation; runtime use remains separately gated |
| #64 | AL-004 Generic Store Profile contract | `main` | merge `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6` | `IN_MAIN` | #63/main | Yes | Declarative repository capability only |
| #65 | AL-005A Staff/Table plan | `main` | merge `8f58bcbfca253c1598b967f4d17c04c0be1cce5b` | `IN_MAIN` | #64/main | Yes | Repository planning only |
| #66 | Printer Store-isolation repair | `main` | merge `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3` | `IN_MAIN` | #65/main | Yes | Security foundation; no runtime behavior |
| #67 | AL-005 Printing provisioning plan | `main` | merge `65e3d3ced2b5b05eb36d56ce67e475768ad19dff` | `IN_MAIN` | #65/main; #66 IN_MAIN | Yes | Repository planning only |
| #68 | AL-005B Device/Pad plan | `main` | `80839d454e8f88391b16e8ba502d3e4bcccd4fb6` | `IN_MAIN` | #67/main | Yes | Main capability; no runtime behavior |
| #69 | AL-006 Activation workflow plan | `main` | `b38d3188edc0555bea7e54dafc4868a7c4726005` | `IN_MAIN` | #68/main | Yes | Main capability; no runtime behavior |
| #70 | REL-001 Production RC plan | `main` | merge `645d4909625f70fc241d5468382d66a30a030fb1` | `IN_MAIN` | #69/main | Yes | Planning authority only; no RC, ACT-001, or runtime action |
| #72 | Post-stack Ground Truth audit | `main` | merge `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | `IN_MAIN` | completed #61-#71 main stack | Yes | Governance/capability audit only; no runtime action |
| #73 | STG-006 evidence/governance | `main` | merge `85d97b7327b2e15aa561ed28a5788b92cedf6f5b` | `IN_MAIN` | #72/main | Yes | Passive evidence only; no runtime mutation |
| #74 | OPS-001 secret-safe tooling | `main` | merge `362c954a8753204476ddf1415ea86050589760dd` | `IN_MAIN` | #73/main | Yes | Repository tooling only; runtime actions remain separately gated |
| #75 | STG-007 retained-listener preflight repair | `main` | merge `b93d8efdbd699333d73d9ffcc29e8f8443e51764` | `IN_MAIN` | #74/main | Yes | Guard repair only; no runtime mutation |
| #76 | STG-007 exact-Git release bootstrap repair | `main` | merge `e6fac236c7620cd2f579d2a180367f4f753a6d42` | `IN_MAIN` | #75/main | Yes | Control-path repair only; no release, deploy or Flyway action |
| #77 | STG-007 state-parent mode guard repair | `main` | merge `5c6d8bb70d74756cc7fe3f76b2d43cb07c6e6f33` | `IN_MAIN` | #76/main | Yes | Trust-root guard repair only; no release/env, deploy or Flyway action |
| #78 | STG-007 releases-parent mode guard repair | `main` | merge `35ccf5cb823bb22b449d8b82baa2f22db2e242df` | `IN_MAIN` | #77/main | Yes | Trust-root guard repair only; no release/env, deploy or Flyway action |
| #79 | STG-007 rotation state-root mode guard repair | `main` | merge `868e229f1b5afd28163e5031ad8fabffaad651f6` | `IN_MAIN` | #78/main | Yes | Guard/test/governance repair; later runtime use was separately authorized |
| #80 | STG-007 readiness health fingerprint repair | `main` | merge `39fa284b7bccd64d650c396f2c7532b0a0858b4b` | `IN_MAIN` | #79/main | Yes | Runtime fingerprint repair; later V10 continuation and readiness were separately authorized |
| #81 | STG-007 Flyway success-token repair | `main` | merge `63600b13b10a5549d9095a03c94e69a9f880af9f` | `IN_MAIN` | #80/main | Yes | Exact PostgreSQL boolean-token validation repair; later runtime use was separately authorized |
| #82 | STG-007 restart readiness/fail-closed repair | `main` | merge `2837ae88e55142c99c6975f8b6575febffc913a1` | `IN_MAIN` | #81/main | Yes | Bounded three-endpoint readiness and nonzero-exit blocked-state persistence; exact merged SHA later passed the authorized V10 continuation |
| #83 | STG-007 final evidence/governance | `main` | merge `2ed56b06f37c9257a655ec334f81e31ca4a518a6` | `IN_MAIN` | #82/main | Yes | Documentation/evidence only; no runtime-capability or runtime-state change |
| #84 | STG-008 entry evidence/governance | `main` | merge `828af4e84581dcb051248beee694c307a65210c5` | `IN_MAIN` | #83/main | Yes | Sanitized credential-gate evidence only; no application, migration, runtime configuration, credential, or business-data mutation |
| #85 | STG-008 guarded one-shot Flyway safety repair | `main` | merge `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6` | `IN_MAIN` | #84/main | Yes | Exact-profile no-migration startup-safety reconciliation plus tests/governance; its original publication did not deploy or mutate credentials/data |
| #86 | STG-008 dependency-repair Ground Truth closure | `main` | merge `4759a23b1a00d3254936e6c8eeb0ec33012b5145` | `IN_MAIN` | #85/main | Yes | Documentation-only closure; no runtime action |
| #87 | STG-008 release-rebind serialization repair | `main` | merge `4b954e09a365fec909ed6da3ddf8fa9f13639cdc` | `IN_MAIN` | #86/main | Yes | Recovery-only release/env preparation preserves both reviewed blocked records and every ordinary action block; it later supported exact `6753855497...` Staging rebind/deploy/recovery |

Main stack review order:

`#61 -> #62 -> #63 -> #64 -> #65 -> #67 -> #68 -> #69 -> #70`

PR #66 is independent and is now `IN_MAIN`. PR #67 is also `IN_MAIN` as the
Printing Provisioning planning foundation for #68; neither authorizes runtime
printing or device operations.

## 4. Runtime ground truth

STG-006 established the historical passive baseline and STG-007 established
the historical infrastructure identity. The later STG-008 continuation and
the exact St-Denis RC promotion establish the current identities below.

| Environment | Retained evidence | Classification and boundary |
|---|---|---|
| Production | accepted app SHA `2661eb76c36dd9aa58db94ceacd278242ef4c9ab`; backend `sha256:2db920f0...`; frontend `sha256:233cc07d...`; Compose `cloud`; retained DB `c2ab37fec6ac`; health `UP`; restart `0/0/0`; Flyway V10 | `DEPLOYED_TO_PRODUCTION`; frozen exact RC, second-start/no-pending and bounded observation PASS; filesystem checkout `4667f3c...` remains only the retained previous source identity |
| Staging | `2661eb76c36dd9aa58db94ceacd278242ef4c9ab`; Flyway V10 with ten successful and zero failed rows; exact `db/backend/nginx` identities running; health passed | `STAGING_ACCEPTED`; manifest-v2 parity, MOCK printing and fresh Owner acceptance pass with `BLOCKING_BEHAVIOR_DIFFERENCE=0`. |
| Staging isolation | project `restaurant-pos-staging`; only `127.0.0.1:18080`; separate state/network/mounts; private leaf UID 70/mode 0700 | `MACHINE_VERIFIED_READ_ONLY` |
| Staging printing | `STAGING_PRINT_MODE=MOCK`; feature flag `true`; allowed modes `DISABLED,MOCK`; endpoint configuration disabled | `MACHINE_VERIFIED`; 7/7 MOCK jobs printed with zero physical transport |

Repository migrations are exactly V1-V10. Machine evidence proves both Staging
and Production are V10; Production applied only reviewed V8/V9/V10 and the
separate second start proved no pending migration.

The historical credential-entry decision is
[STG-008 Synthetic Topology and Source Entry Evidence](STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md).
The resumed plan failure and bounded repair are recorded in
[STG-008 Flyway Guard Repair Evidence](STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md).
This was a failed password-free plan one-shot before the STG-005A command, not
a failed transaction, deployment, migration, or credential operation. Its
historical fail-closed pair was later recovered through the reviewed path; the
current runtime has no marker or lock and no recovery is pending.

The later fresh baseline, release-rebind sequencing deadlock and bounded PR
#87 correction are recorded in
[STG-008 Release-Rebind Serialization Repair Evidence](STG-008_RELEASE_REBIND_SERIALIZATION_REPAIR_EVIDENCE.md).
That historical continuation stopped before candidate import or Batch A
mutation. Its PR #87 repair was later used by exact `6753855497...` for
Staging rebind/preflight/deploy/readiness and old-pair recovery. Subsequent
non-web repairs and exact continuations resolved that historical gate; no
current rebind or recovery instruction is inherited from it.

## 5. Current feature and loop

| Field | Current value |
|---|---|
| Current Feature | Existing St-Denis exact-RC Production promotion complete; `FT-001` Chinatown remains deferred. |
| Current Agile Loop | `PRODUCTION_ST_DENIS_EXACT_RC_PROMOTION` completed |
| Current package | Frozen `RC-ST-DENIS-20260811-2661EB76`; exact `2661eb76...` deployed to Production at V10; Staging remains exact `2661eb76...` / V10 / MOCK. |
| Feature stop state | `PRODUCTION_EXACT_RC_PROMOTED_POST_DEPLOY_OBSERVATION_PASS` |
| Handoff navigation status | `PROJECT_HANDOFF_IN_MAIN` |
| Current Owner gate | No automatic next runtime action. Any Chinatown, printer/Pad, rollback/restore or new Production batch requires its own authority. |

Release/promotion navigation follows the canonical [Agile Loop policy](../AGILE_LOOP_OPERATING_MODEL.md#83-canonical-release-promotion-drift-and-recovery-policy):
freeze an immutable RC after Twin/automated/Owner acceptance, promote the same
artifact digests, use `APPLICATION_ROLLBACK_COMPATIBILITY_GATE`, and treat
backup existence as distinct from recoverability. Those gates passed for this
exact RC; every future release still requires a new bound record and authority.

### Permitted work

- Fetch and verify Git/GitHub ground truth.
- Perform read-only verification of the completed exact-RC evidence and retained
  Staging/Production continuity.
- Complete this tooling/evidence/governance PR under Auto-Merge policy. Its
  documentation-only merge does not require or authorize another Staging
  rebind.

### Prohibited or separately gated work

- Reuse of consumed/failed STG-007 or STG-008 approval/readiness evidence, or
  continuation on an old image.
- Any further Production runtime/configuration action; schema/migration change;
  application rollback absent a severe incident; database restore; Staging
  downgrade/Flyway edit/destructive reset; physical printer binding, Pad
  pairing, Chinatown, modularization, AL-003/REL-001 or another promotion.
- Production Store 1 read or mutation.
- Real printer/endpoint configuration, physical test print, Pad pairing, or
  device/Worker mutation. Owner manual MOCK use remains permitted.
- Credential exposure/copy from Production or security-boundary weakening.
  Further Staging credential creation/reuse/rotation is no longer authorized
  by the completed reconstruction approval.
- Projector/staff replay, another Staging deploy/rebind, or repetition of the
  completed automated smoke without new bounded authority.
- Repository merge that fails Operating Model section 16's permanent
  auto-merge gate, Production activation, restore, or destructive database/Git
  commands.

## 6. What is already done

- AL-001 planning is complete.
- AL-002 Owner onboarding foundation and V8 are in main.
- STG-001 through STG-004 established the isolated Staging plan, deployment
  package, local Docker rehearsal, and historical server Staging evidence.
- STG-005A/B are in main and fully evidenced in isolated Staging as
  `VALIDATED/CREATED/REPLAYED`, with one synthetic topology and `4/3/13/38`,
  replay `2 -> 2`, no duplicate/crossover and no active block/lock.
- AL-003 PR-A through PR-F are in main.
- PR #58 retained the failed exact-SHA Staging attempt evidence.
- PR #59 fixed the bounded PostgreSQL UID-70/mode-0700 preflight defect in main.
- PR #60 merged Owner product decisions and governance alignment into main at
  `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`.
- PR #71 merged this navigation handoff into main at
  `5baada03935e004d80af1e7a36fb7db39bd6abbb`.
- PR #61 merged the modular architecture foundation into main at
  `bbb1af9520c188b6ef6362e783284ba4001a7e63`.
- PR #62 merged the guarded Synthetic St-Denis baseline into main at
  `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b`.
- PR #63 entered `main` at `732d77c89ff067982702426ff918d5e097e1d0fb`.
- PR #64 entered `main` at `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6`.
- PR #65 entered `main` at `8f58bcbfca253c1598b967f4d17c04c0be1cce5b`.
- PR #66 entered `main` at `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3`.
- PR #67 entered `main` at `65e3d3ced2b5b05eb36d56ce67e475768ad19dff`.
- PR #68 entered `main` at `9e93573be97cfd01a9ad3efe64d55827854c497a`.
- PR #69 entered `main` at `dc682203b2b24bbdb453a5520b297b9051139f13`.
- PR #70 entered `main` at `645d4909625f70fc241d5468382d66a30a030fb1`.
  This completes the #61-#70 preparation stack; no runtime state is implied.
- PR #72 entered `main` at `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b`.
  STG-006 then freshly verified the retained isolated Staging runtime and
  minimum Production continuity without mutation.
- PR #73 entered `main` at `85d97b7327b2e15aa561ed28a5788b92cedf6f5b`.
  It records `STG-006=PASS` and `OPS-001=REQUIRED`; it performed no runtime
  mutation.
- PR #74 entered `main` at `362c954a8753204476ddf1415ea86050589760dd`
  with OPS-001 repository tooling; runtime use remained separately gated.
- PR #75 entered `main` at `b93d8efdbd699333d73d9ffcc29e8f8443e51764`
  with the retained-listener preflight repair; no runtime mutation occurred.
- PR #76 entered `main` at `e6fac236c7620cd2f579d2a180367f4f753a6d42`
  with the exact-Git release bootstrap. The next Batch A attempt imported that
  candidate, then stopped before release/env mutation at the state-parent mode
  guard now under bounded repair.
- PR #77 entered `main` at `5c6d8bb70d74756cc7fe3f76b2d43cb07c6e6f33`
  with the state-parent correction. The next Batch A attempt imported that
  candidate and delegated bootstrap, then stopped before approval consumption
  or mutation at the releases-parent mode guard now under bounded repair.
- PR #78 entered `main` at `35ccf5cb823bb22b449d8b82baa2f22db2e242df`
  with the releases-parent correction. The next Batch A attempt created its
  exact release and consumed approval, then stopped before env mutation at the
  remaining rotation state-parent mode guard now under bounded repair.
- PR #79 entered `main` at `868e229f1b5afd28163e5031ad8fabffaad651f6`
  with the rotation state-parent correction. The next authorized run passed
  Batch A, deployed that exact SHA and applied V9/V10, then stopped before
  readiness PASS or restart at the optional-health fingerprint bug now under
  bounded repair.
- PR #80 entered `main` at `39fa284b7bccd64d650c396f2c7532b0a0858b4b`.
  The new V10-aware continuation deployed that exact SHA, retained V10 and
  passed repaired readiness. Runtime collection then exposed the canonical
  `true` versus mock-`t` validator mismatch before PASS evidence; no restart
  followed.
- PR #81 entered `main` at `63600b13b10a5549d9095a03c94e69a9f880af9f`.
  A fresh V10-aware continuation deployed that exact SHA, passed formal
  preflight, readiness and sanitized runtime/Flyway collection. Its same-image
  restart retained the exact containers/images but the single immediate health
  probe raced application startup and returned 502. Runtime recovered to
  200/200/200 at the same identities; the action remains `NO_GO` because no
  PASS evidence or blocked marker was produced.
- PR #82 entered `main` at `2837ae88e55142c99c6975f8b6575febffc913a1`
  with bounded three-endpoint restart readiness and nonzero-exit fail-closed
  persistence. A new exact-main continuation then deployed that SHA
  V10-to-V10 and passed formal preflight, readiness, runtime collection,
  same-image restart, and post-restart verification. `STG-007 = PASS`.
- PR #83 entered `main` at `2ed56b06f37c9257a655ec334f81e31ca4a518a6`
  with only the final STG-007 evidence/governance. STG-008 later performed a
  read-only entry check: zero synthetic topology/credential rows, safe next
  Store ID `1`, unchanged Staging/Production continuity, and a credential-
  contract `NO_GO` before any plan or write.
- PR #84 entered `main` at `828af4e84581dcb051248beee694c307a65210c5`
  with only that sanitized STG-008 entry evidence/governance. The Owner then
  aligned the credential contract. Fresh readiness passed, but the first
  password-free plan one-shot stopped before its command because the older
  cloud safety guard rejected Flyway-disabled mode. Cleanup and zero-write
  continuity passed; at that historical checkpoint fail-closed state remained
  until the later separately approved recovery.
- PRs #85-#94 repaired the guarded startup, exact release/recovery, non-web
  context, lifecycle and blocked-pair paths. Their later exact runtime use
  completed STG-005A/B without duplicate/crossover and cleared all active
  one-shot/marker/lock state.
- PRs #95-#98 added and evidenced secret-safe API Phase-A acceptance. Owner
  manual Chrome then exposed the distinct same-origin 403.
- PR #99 repaired generic proxy Host/port preservation and added reviewed
  private credential rotation; PR #100 recorded its pre-deploy Ground Truth.
- Exact `1a3f2e...` deployed that repair and passed V10-to-V10 preflight,
  readiness, credential rotation, API and real-Chrome browser-equivalent
  acceptance. PR #101 published the sanitized evidence into
  `main@aec59af93a9bf42ce3d167a579a19be80eadc9b0` without changing runtime.

## 7. AL-003 repository capability

Current main includes:

- generic Category/Station/Item clone transaction;
- source option cloning with parent mapping;
- versioned Chinatown Profile and target overrides;
- shared read-only option-plan validation and structured diagnostics;
- V10 idempotency reservation, replay, terminal `FAILED`, and sanitized result;
- Organization Owner authorization;
- public Owner `validate` and `execute` API facade reusing the same planner and
  lock-owning transaction.

Repository capability is not Staging acceptance and is not Production
deployment. No real Chinatown clone has been evidenced.

Synthetic St-Denis topology/source and automated Owner login evidence now
exist. Still missing are fresh Owner post-repair manual UI evidence, Chinatown
target onboarding, validate/execute/clone/replay evidence, and the separate
Production source, RC, deployment, provisioning and field-acceptance gates.

## 8. Owner product decisions that are settled

1. Chinatown is the second real Production Store.
2. Production Store 1 / St-Denis is the Chinatown menu source of truth.
3. Chinatown's initial menu must be created through the reviewed Clone Engine.
4. After clone/activation, Menu Management may modify the target normally.
5. An Organization Owner naturally manages same-Organization Stores under the
   current authorization contract; do not manufacture redundant target Owner
   memberships.
6. Staging uses synthetic-only credentials and data.
7. Staging remains a Production-like synthetic St-Denis test environment.
8. Production uses a formal exact-SHA Release Candidate process.
9. Chinatown DoD is Store + Owner/Staff + Menu + Tables + Printing + Pads +
   Login + actual order test.
10. A third Store matching St-Denis must reuse a St-Denis Profile and shared
    modules, never copy/paste Store-specific code.

Do not reopen these decisions unless the Owner changes them or executable code
proves the underlying contract has changed.

## 9. Modular architecture direction

Owner-approved direction, currently planned/prepared rather than implemented as
one complete engine:

`Generic Store Provisioning Engine + Versioned Store Profiles + Reusable Provisioning Modules`

`Profile defines WHAT. Module defines HOW.`

Planned reusable modules are Store Core, Access/Staff, Menu, Tables, Printing,
Device/Pad, and Activation. Shared code must not branch on a Store ID/name,
duplicate the Menu Clone Engine, or create copy/paste Store engines. Chinatown
is the first Store Profile sample, not a shared-service special case.

## 10. Current roadmap

| ID | Purpose | Current state | Dependency / Owner gate |
|---|---|---|---|
| STG-005B / #62 | Reproducible synthetic St-Denis menu baseline | `IN_MAIN` and `DEPLOYED_TO_STAGING` evidence | PLAN/EXECUTE/REPLAY passed at `4/3/13/38`, revision `2 -> 2`; no duplicate/crossover |
| AL-003S / #63 | Exact-SHA Staging acceptance preparation | `IN_MAIN`; reviewed tooling used under separate runtime approvals | Exact `2661eb76...` passed Operational Twin, MOCK validation and Owner acceptance, then became the frozen Production RC. |
| AL-004 / #64 | Generic Store Profile contract | `IN_MAIN` | Repository capability only; no provisioning/runtime execution |
| AL-005A / #65 | Staff/Table module plan | `IN_MAIN` | Repository planning only; no writer or runtime execution |
| AL-005 / #67 | Printing provisioning plan | `IN_MAIN` | repository planning only; no writer/runtime action |
| AL-005B / #68 | Device/Pad provisioning plan | `IN_MAIN` at `9e93573be97cfd01a9ad3efe64d55827854c497a` | no pairing/credential/Worker/runtime action |
| AL-006 / #69 | Fail-closed activation workflow plan | `IN_MAIN` at `dc682203b2b24bbdb453a5520b297b9051139f13` | no status transition or activation writer |
| REL-001 / #70 | Formal Chinatown Production RC plan | `IN_MAIN` at `645d4909625f70fc241d5468382d66a30a030fb1` | planning only; no candidate, deployment, or activation |
| ACT-001 | Production provisioning and field acceptance | `NOT_STARTED_OWNER_GATED` | Accepted RC and explicit Production activation approval |
| STG-006 | Exact-main passive preflight | `PASS` for candidate `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | Evidence only; no release/deploy/migration approval |
| OPS-001 | Secret-safe Staging tooling | `REPOSITORY_COMPLETE`; bounded repairs #75-#82 and #87 are `IN_MAIN` | PR #87's recovery-only release/env path supported the later exact `6753855497...` rebind/deploy/recovery; runtime actions remain independently exact-SHA/action/Owner-gated |
| STG-007 | Exact-SHA V10-aware continuation | `PASS` at deployed Staging SHA `2837ae88...` / Flyway V10 | Runtime batch complete; no approval or evidence is reusable |
| STG-008 | Synthetic topology and source | `PASS` | Synthetic Organization/Owner/source Store and credential ready; A/B PLAN/EXECUTE/REPLAY complete at `4/3/13/38`, replay `2 -> 2`, no duplicate/crossover, Printing disabled, isolation and Production continuity unchanged. |

The post-stack capability matrix and Staging decision remain historical in
[Post-Stack Ground Truth Audit](POST_STACK_GROUND_TRUTH_AUDIT.md). Its former
loop order and STG-008 runtime authorization are superseded by TWIN-001 and
are not current authority.

## 11. Stack rebuild rule

After each dependency enters main:

`fetch latest main -> verify dependency IN_MAIN -> rebuild/rebase/retarget next package -> review diff -> rerun checks -> governance sync -> Owner review`

Do not merge into an intermediate feature branch and report it as main. If a PR
was merged only to a non-main base, keep it `STACKED_ONLY` and promote it again
from current main.

## 12. Known blockers and risks

- STG-008 is `PASS`; its former runtime/tooling blockers and blocked records
  are resolved historical evidence and must not be replayed.
- API and real-Chrome browser-equivalent Owner login acceptance pass on exact
  `1a3f2e...`; the former manual Phase-A gate is deferred by the Owner Twin
  route and is not the current loop.
- Chinatown target onboarding and AL-003 validate/execute/clone/replay remain
  unexecuted and outside the current authorization.
- The exact-RC Production gap, fixed state root, serialized exact-image tooling,
  1 GiB resource gate, fresh preflight, backup integrity, isolated restore and
  old-app-on-V10 rollback compatibility are resolved for
  `RC-ST-DENIS-20260811-2661EB76` only.
- Printing and Device/Pad field provisioning remain unimplemented/runtime-gated.
- Chinatown REL-001/ACT-001 remain separately pending and were not activated by
  the St-Denis promotion.

### Recently resolved

- AL-003 generic clone/profile/planner/API packages entered main.
- PostgreSQL private-leaf mode-0700 preflight handling entered main via PR #59.
- Owner decisions and access semantics entered main via PR #60.
- Draft architecture and downstream preparation packages are already written;
  do not regenerate them.
- OPS-001 restart readiness/fail-closed repair entered main through PR #82 and
  the fresh exact-SHA continuation produced valid same-image restart evidence.
- The STG-008 guarded one-shot Flyway safety repair entered main through PR
  #85 at `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6`; its publication itself did
  not perform a runtime action.
- PR #87 merged the blocked-state-safe release-rebind serialization repair at
  `4b954e09a365fec909ed6da3ddf8fa9f13639cdc` after full shell regressions and
  Agent 6 `ACCEPT`; it later supported the exact `6753855497...` Staging
  rebind/deploy/readiness and old-pair recovery continuation.

## 13. START HERE for the next Agent

1. Run `git fetch origin --prune` without altering unrelated local work.
2. Read this file, then read `ALIVE_RUNTIME_PLANBOOK.md`,
   `AGILE_LOOP_OPERATING_MODEL.md`, `FEATURE_BACKLOG.md`, and the applicable
   technical plan.
3. Verify current `origin/main`; do not trust the Owner workspace branch tip.
4. Verify historical GitHub PR #61 through #120, independent PR #66, and the
   current exact-RC evidence PR semantics.
5. Distinguish main, stacked Draft, Staging, and Production state.
6. Report the completed exact-RC Production promotion and retain stop state
   `PRODUCTION_EXACT_RC_PROMOTED_POST_DEPLOY_OBSERVATION_PASS`.
7. Do not recreate or redesign packages #61-#70.
8. Do not infer implementation from the planning packages.
9. Read the TWIN-001 plan, STG-006, OPS-001, the STG-007 repair/final evidence, all STG-008
   evidence records, [STG-009 Phase-A API evidence](STG-009_PHASE_A_OWNER_LOGIN_EVIDENCE.md)
   and [browser-equivalent evidence](STG-009_PHASE_A_BROWSER_EQUIVALENT_ACCEPTANCE_EVIDENCE.md).
   Treat `STG-008=PASS` and automated Phase-A API/browser-equivalent acceptance
   on exact historical Staging `1a3f2e...` as foundation evidence. Exact
   Staging `2661eb76...` remains the validated Operational Twin with MOCK
   printing; exact Production `2661eb76...` now runs V10 with PAD_DIRECT.
10. Do not continue `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP`, rebind/redeploy
    Staging, read or mutate Production, enter physical hardware gates, start
    Chinatown onboarding/clone, restore/rollback, or implement modules without
    a new explicit Owner decision.
11. The completed promotion creates no automatic next runtime action. Stop at
    the unique state above and await a new scoped objective/Owner gate.

## 14. Auto-loop behavior

For a future Owner-approved bounded task, follow Dependency Repair Auto-Loop,
Continuous Agile Loop, Mandatory Governance Sync, and Planbook Ground Truth
Rule. A clear bounded defect within that future authority should be repaired,
tested, reviewed and documented without broadening runtime scope. This section
does not keep the completed Production promotion loop active.

Stop only for a real product/architecture ambiguity, security boundary,
runtime mutation, Production action, irreversible operation, or dependency
requiring Owner merge.

## 15. Do not repeat

- Do not redesign the AL-003 clone engine or create a second one.
- Do not re-decide whether Chinatown is a real Store.
- Do not re-decide the formal Production RC strategy.
- Do not add a target Owner membership unless the authorization contract changes.
- Do not re-fix the PostgreSQL mode-0700 preflight bug.
- Do not revive historical stacked promotion branches.
- Do not regenerate #61-#70 or implement duplicate equivalents.
- Do not infer that a Draft, merge badge, repository migration, or local test is
  deployed runtime evidence.

## 16. Safety and evidence references

Never place passwords, tokens, cookies, printer endpoints, customer data, raw
menu payloads, raw idempotency keys, database credentials, SSH keys, or secrets
in this handoff or future governance records.

Primary authorities:

- [Alive Runtime Planbook](ALIVE_RUNTIME_PLANBOOK.md)
- [Feature Backlog](../FEATURE_BACKLOG.md)
- [Agile Loop Operating Model](../AGILE_LOOP_OPERATING_MODEL.md)
- [Known Issues Backlog](../KNOWN_ISSUES_BACKLOG.md)
- [AL-003 technical plan](../agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md)
- [AL-003 Staging acceptance plan](../agile/AL-003_STAGING_RELEASE_ACCEPTANCE_PLAN.md)
- [STG-006 exact-main preflight evidence](STG-006_EXACT_MAIN_PREFLIGHT_EVIDENCE.md)
- [OPS-001 local tooling evidence](OPS-001_STAGING_SECRET_SAFE_TOOLING_EVIDENCE.md)
- [STG-007 Flyway success-token repair evidence](STG-007_FLYWAY_SUCCESS_TOKEN_REPAIR_EVIDENCE.md)
- [STG-007 restart readiness/fail-closed repair evidence](STG-007_RESTART_READINESS_FAIL_CLOSED_REPAIR_EVIDENCE.md)
- [STG-007 exact-SHA V10 continuation evidence](STG-007_EXACT_SHA_CONTINUATION_EVIDENCE.md)
- [STG-008 synthetic topology/source entry evidence](STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md)
- [STG-008 guarded one-shot Flyway repair evidence](STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md)
- [STG-008 non-web request-context repair evidence](STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md)
- [STG-008 one-shot lifecycle repair evidence](STG-008_ONE_SHOT_LIFECYCLE_REPAIR_EVIDENCE.md)
- [STG-009 Phase-A Owner login evidence](STG-009_PHASE_A_OWNER_LOGIN_EVIDENCE.md)
- [STG-009 browser-equivalent acceptance evidence](STG-009_PHASE_A_BROWSER_EQUIVALENT_ACCEPTANCE_EVIDENCE.md)
- [OPS-001 secret-safe tooling runbook](../../../deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md)
- [System Documentation](../../../SYSTEM_DOCUMENTATION.md)
- [API contract](../../../doc/API.md)
