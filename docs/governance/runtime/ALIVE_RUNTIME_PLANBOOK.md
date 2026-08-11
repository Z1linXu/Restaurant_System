# Alive Runtime Planbook

## Current exact-RC Production promotion preparation (2026-08-11)

The Owner freshly attested that exact Staging `2661eb76c36dd9aa58db94ceacd278242ef4c9ab`
passed the required field acceptance and conditionally authorized its existing
St-Denis application-only Production promotion after every mandatory gate
passes. This closes the stale Owner-retest blocker for that exact SHA only;
Chinatown, Store provisioning, printer/Pad changes and Production data changes
remain excluded. `RC-ST-DENIS-20260811-2661EB76` is `RC_PREPARED`, not frozen.

A bounded tooling repair now supplies fixed-state, same-host exact-image-ID,
no-build/no-pull, serialized backend-before-frontend promotion plus private
atomic backup and network-isolated restore rehearsal. Runtime use is still
`NO_GO` pending merge, fresh validation, V7-to-V10 and old-app-on-V10 rehearsal,
backup/restore evidence, final frozen RC and Agent 6 acceptance. Production
remains `4667f3c...` / V7 and was not restarted or migrated by this package.
Current unique stop: `RC_PREPARED_WAITING_FOR_MANDATORY_PROMOTION_GATES`.

## Historical superseded Owner field-test Printing bug repair (2026-08-11)

The Owner continued `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` after the Operational
Twin reached READY and approved bounded Staging/repository repair for six
printing field-test issues. Repository repair entered `main` through PR #117
at `2661eb76c36dd9aa58db94ceacd278242ef4c9ab`, was deployed to exact Staging
at that SHA, and passed automated MOCK smoke after resolving Agent 6
lifecycle-safety blocks.

Scope includes GRAB/Frontdesk renderer fixes, Android PAD_DIRECT lifecycle
reliability repair, and an audit-only queue latency record. Production remains
unchanged and may receive only lightweight continuity checks. Physical printer
binding, Pad pairing, `REAL`, public printer access, Chinatown, modularization,
REL-001 and Production promotion remain prohibited.

See [Owner field-test printing fixes evidence](OWNER_FIELD_TEST_PRINTING_FIXES_EVIDENCE.md).
Historical stop, superseded by the fresh exact-RC Owner acceptance:
`HISTORICAL_OWNER_FIELD_TEST_PRINTING_FIXES_DEPLOYED_WAITING_FOR_OWNER_RETEST`.

## Current Staging MOCK Printing field-test override (2026-08-11)

The Owner started `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` and approved the bounded
Staging-only package `STAGING_MOCK_PRINTING_FIELD_TEST_ENABLEMENT`. That
earlier package entered `main` through PR #114 and was superseded in current
runtime by PR #117. Exact Staging now runs
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab` at V10 with Store Printing
`MOCK/true`, four endpoint-free logical printers and three enabled assignments.
Production remains unchanged and read-only at
`4667f3c35f85c9f8538f82789d9df1531d4fbc9e` / V7.

Ground Truth found the reviewed bounded dependency: server tooling permits
only `DISABLED`, while enabling the existing Print Center without a runtime
ceiling would also expose `REAL` and `PAD_DIRECT`. The current package adds a
generic application runtime allowlist and endpoint-configuration policy, with
Staging fixed to `DISABLED,MOCK` and endpoint configuration disabled. See the
[runtime-policy repair evidence](STAGING_MOCK_PRINTING_RUNTIME_POLICY_REPAIR_EVIDENCE.md).

Repository tests, Agent 6, PR/auto-merge, exact-SHA rebind, private environment
change, Store-scoped MOCK transition, browser verification and automated
synthetic smoke passed. See
[field-test evidence](STAGING_MOCK_PRINTING_FIELD_TEST_EVIDENCE.md). Current
historical stop: `HISTORICAL_OWNER_FIELD_TEST_PRINTING_FIXES_DEPLOYED_WAITING_FOR_OWNER_RETEST`. Production
mutation, real/home
printer binding, public printer access, Pad pairing, Chinatown,
modularization, REL-001 and Production promotion remain prohibited.

## Current operational Twin readiness override (2026-08-10)

`TWIN-001_STAGING_RECONSTRUCTION_APPROVAL` completed successfully on exact
historical Staging `53209823fa320cc56c31d04ee5c7719a83a78acc` / Flyway V10
with manifest-v2 parity, `BLOCKING_BEHAVIOR_DIFFERENCE=0`, automated
Owner/Staff/POS beverage workflow smoke, frontend/backend/WebSocket health and
Production continuity. The current field-test packages subsequently deployed
exact `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` while retaining Flyway V10 and
that parity. Production remained read-only at
`4667f3c35f85c9f8538f82789d9df1531d4fbc9e`/V7. See
[operational Twin evidence](TWIN-001_ST_DENIS_OPERATIONAL_TWIN_EVIDENCE.md).

Historical reconstruction stop:
`TWIN-001_ST_DENIS_OPERATIONAL_TWIN_READY_WAITING_FOR_OWNER_FIELD_TEST`.
The Owner has opened that loop; the current field-test override above governs.
Physical printing,
positive KDS enablement and Pad pairing remain
`SEPARATE_OWNER_RUNTIME_GATE_PENDING`; Chinatown, modularization, REL-001 and
Production promotion remain prohibited.

## Historical reconstruction execution override (2026-08-10)

The Owner granted `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`. Fresh Git Ground
Truth is `origin/main=5f89bdeea6f9a6810c0a38d6d94a59b2156bd6ba`; retained Production
remains read-only at V7 and isolated Staging remains exact
`1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c` at V10. A fresh bounded Staging
snapshot accepted the exact `CURRENT_SYNTHETIC_BASELINE` and the repository
projector plan is `PLAN_READY` with no delete/reset/downgrade.

The current dependency package is
[TWIN-001 Staging Reconstruction Tooling Evidence](TWIN-001_STAGING_RECONSTRUCTION_TOOLING_EVIDENCE.md).
It must enter `main`, pass Agent 6, then fresh authority recovery may continue
the already-approved Staging-only apply, staff reconciliation, parity and
automated smoke loop without another ordinary repair gate. Production reads or
writes, schema/migration changes, destructive reset and physical hardware
binding remain prohibited or separately gated.

> Status: `ACTIVE_GOVERNANCE_RECORD`
>
> Last updated: 2026-08-10, America/Toronto
>
> Scope: current operating baseline, active work, deployment entry conditions,
> and approval boundaries. This is a living index, not a replacement for the
> immutable Phase 3 evidence reports.

## Historical manifest v2 readiness checkpoint (2026-08-10)

The corrected Owner-approved read completed in read-only mode. Manifest v2
contains the deterministic safe St-Denis configuration graph, all 380 option
relationships, and an explicit V7-to-V10 mapping; neither runtime changed.
See [manifest v2 completion evidence](TWIN-001_MANIFEST_V2_COMPLETION_EVIDENCE.md).
Historical stop: `TWIN-001_MANIFEST_V2_RECONSTRUCTION_READY_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
That gate is now granted and superseded by the current execution override.

## Historical reconstruction NO-GO override (2026-08-10)

The Owner granted `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`. Pre-write source
validation stopped before any Staging or Production runtime action because the
reviewed parity manifest is not a deterministic, schema-consistent writer
input. It omits required row-level option/relationship and logical
KDS/printing/device values, and its documented query columns conflict with the
checksum-identical repository V7 schema. Historical inventory evidence is
preserved; repository seeds cannot fill the gaps.

An isolated PostgreSQL 16.14 verification proved the intended forward path
`Production V7 -> reviewed V8 -> reviewed V9 -> reviewed V10`: only the three
append-only request-table migrations ran, current-candidate JPA validation and
health passed, representative St-Denis configuration shape was unchanged, and
the second startup required no migration. The observed V7/V10 delta is
`CURRENT_PRODUCTION_VERSION_DIFFERENCE`, not authority for backward parity.
Aggregate `SCHEMA = BLOCKING_BEHAVIOR_DIFFERENCE` remains because no
reconstructed St-Denis Twin has operated on V10.

No Staging write, release/rebind/restart/Flyway action, credential action,
Production read/mutation, printer, Pad, Chinatown, modularization, or REL-001
action occurred. The immutable evidence is
[TWIN-001 reconstruction schema/input NO-GO](TWIN-001_STAGING_RECONSTRUCTION_SCHEMA_NO_GO_EVIDENCE.md).
The unique stop is
`TWIN-001_RECONSTRUCTION_NO_GO_WAITING_FOR_MANIFEST_COMPLETION_READ_APPROVAL`.
The next TRUE OWNER GATE is
`TWIN-001_RECONSTRUCTION_MANIFEST_COMPLETION_READ_APPROVAL`.

## Historical verified inventory checkpoint (2026-08-10)

The Owner-approved `PRODUCTION_ST_DENIS_CONFIGURATION_READ_APPROVAL` was
completed as a bounded, read-only inventory. Fresh Git Ground Truth is
`origin/main=34ef8c577dd5e8464ef885bf235b0bece0018503`. Production retained
runtime is `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` at Flyway V7; isolated
Staging is `1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c` at Flyway V10 with no
pending or failed history rows observed. The exact Production identity is
Store `1 / 4483_R_SAINT_DENIS / 4483 R. Saint-Denis` in Organization
`1 / LANZHOU_NOODLES / Lanzhou Noodles`.

The sanitized parity manifest and evidence are now in
[ST_DENIS_TWIN_PARITY_MANIFEST](ST_DENIS_TWIN_PARITY_MANIFEST.md) and
[TWIN-001 Production Inventory Evidence](TWIN-001_PRODUCTION_INVENTORY_EVIDENCE.md).
All observations were read-only, Store-scoped, explicitly columned and
bounded; no secrets, PII, customer/order/payment data, migration, restart,
deployment, Staging write, or Twin sync occurred. The checkpoint stop was
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
Its next Owner Gate was `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`, which the
Owner later granted before the current pre-write NO-GO.

## Historical Owner strategic route override (2026-08-10)

The Owner has reprioritized the product route. The former sequence
`STG-009 Phase A -> Chinatown Phase B -> REL-001 Production RC` is preserved as
history but deferred. The current highest priority is planning
`TWIN-001_ST_DENIS_STAGING_TWIN`, whose designated long-term role is a
Production-like St-Denis Operational Twin and mandatory pre-Production
validation environment.

The Twin is planned, not yet established. Existing synthetic St-Denis data is
`CURRENT_SYNTHETIC_BASELINE`, not a claim of Production parity. The completed
read-only inventory is evidence only; no Staging/Production mutation occurred.
The historical next runtime gate was
`TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`; it was later granted, but the
package stopped at the current manifest-input NO-GO before runtime entry.

The recent Twin governance closure merges are verified governance-only in
`main`; a fresh `git fetch origin --prune` is required before the next action to
establish the current exact `origin/main`. These documentation-only merges do
not change the deployed Staging SHA.

The former read-approval stop and inventory checkpoint are historical; that
checkpoint stop was
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.
See [TWIN-001 St-Denis Twin Plan](../agile/TWIN-001_ST_DENIS_STAGING_TWIN_PLAN.md).

## 1. Evidence vocabulary

| Label | Meaning | Do not infer |
|---|---|---|
| `RUNTIME_COMMIT` | The commit reported as running in production. | That it is formally approved or matches the documentation branch. |
| `DOCUMENTATION_COMMIT` | The Git commit that contains this governance baseline. | That it is deployed. |
| `OPERATOR_CONFIRMED` | A responsible operator reported completing a field action. | Machine logs, exact timing, or fleet-wide behavior. |
| `LOG_OBSERVED` | A retained, sanitized machine-log observation. | A business acceptance result without matching operator evidence. |
| `MACHINE_VERIFIED` | Reproducible automated or machine-produced evidence with a known scope. | Production-wide behavior beyond that scope. |
| `EVIDENCE_PENDING` | No adequate evidence has been retained. | Failure, success, or production absence. |

## 2. Current production baseline

| Item | Current value | Evidence | Boundary |
|---|---|---|---|
| Environment | `restaurant-prod` | `OPERATOR_CONFIRMED` | Environment label only; no host or secret is recorded. |
| `RUNTIME_COMMIT` | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` | retained identity plus `MACHINE_VERIFIED_READ_ONLY` continuity during STG-007 | Current Production runtime identity only, not a formal release approval. |
| Production branch | `main` | `MACHINE_VERIFIED_READ_ONLY` during STG-006 continuity | Branch relationship is not a deployment approval record. |
| Runtime-sensitive deployed candidate | `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` | `MACHINE_VERIFIED` Git/runtime Ground Truth | PR #117's Owner field-test printing fixes are in the exact detached release, build source and deployed Staging images. Flyway remains V10; MOCK submit/update/reprint smoke passes. |
| STG-007 exact runtime candidate / documentation base before evidence publication | `2837ae88e55142c99c6975f8b6575febffc913a1` | `MACHINE_VERIFIED` from `origin/main`, detached release, build source and deployed Staging identity | PR #82 merge and deployed Staging SHA; it is not the Production runtime. A later evidence-only merge must remain distinct from the deployed SHA. |
| Deployment mode | HTTP | `OPERATOR_CONFIRMED` | HTTPS/certificate posture is outside this record. |
| Compose services | `db`, `backend`, `nginx` under project `cloud`; unchanged across the final STG-007 continuation, original start times, restart count 0, health 200 | `MACHINE_VERIFIED_READ_ONLY` | Minimum continuity only; no environment, Flyway, Store, or business-data read. |
| Database schema | Flyway V7, including `V7__add_print_job_attention_acknowledgement.sql` | `OPERATOR_CONFIRMED` | Not a restore or schema-integrity rehearsal. |
| Current backup artifact | `deployment/cloud/backups/restaurant_pos_20260725_033648.dump` | `OPERATOR_CONFIRMED` | Reported non-empty, approximately 812K; recoverability is unproven. |
| Print mode | PAD_DIRECT field flow | `OPERATOR_CONFIRMED` | Does not replace device-by-device health evidence. |

Historical detail remains in [POST_DEPLOY_RUNTIME_EVIDENCE.md](POST_DEPLOY_RUNTIME_EVIDENCE.md),
[CURRENT_RUNTIME_STATUS.md](CURRENT_RUNTIME_STATUS.md), and the immutable Phase 3
snapshots. Do not copy those reports into this planbook.

## 3. Confirmed field baseline

| Field result | Classification | Scope and limit |
|---|---|---|
| New APK can log in, load menu, create and submit orders. | `OPERATOR_CONFIRMED` | A field flow, not an exhaustive offline fault-injection test. |
| Older APK can connect to the reported current backend and submit orders. | `OPERATOR_CONFIRMED` | Does not establish compatibility for every historical APK. |
| GRAB, FRONTDESK_RECEIPT, and HOT_KITCHEN physically printed. | `OPERATOR_CONFIRMED` | No job IDs, raw payloads, or exact timestamps are retained here. |
| PAD_DIRECT Worker completed the reported long-run observation without the prior stopped-and-unrecoverable symptom. | `OPERATOR_CONFIRMED` | Not fleet-wide proof and not a substitute for future monitoring. |
| Phase 3A-3C repository/container/Pad observations. | `LOG_OBSERVED` and `MACHINE_VERIFIED` only where the source report says so | Read the cited historical report for each exact assertion. |

## 4. Current incidents and backlog

| Area | Current state | Authority |
|---|---|---|
| P0/P1 production incident | No active P0 or P1 item recorded in the current backlog. | [KNOWN_ISSUES_BACKLOG.md](../KNOWN_ISSUES_BACKLOG.md) |
| Historical Orders stale-chunk/WebView blank page | `KI-001` is closed as `CLOSED_OPERATOR_CONFIRMED`; the historical cache-clear recovery remains documented. | [KNOWN_ISSUES_BACKLOG.md](../KNOWN_ISSUES_BACKLOG.md) |
| Active operational issues | `KI-002` through `KI-007` remain open or evidence/process pending. | [KNOWN_ISSUES_BACKLOG.md](../KNOWN_ISSUES_BACKLOG.md) |
| Production approval record | Not established. | `KI-006`; `EVIDENCE_PENDING` |
| Database restore rehearsal | Not executed or evidenced. | `KI-005`; `EVIDENCE_PENDING` |

## 5. Current feature and Agile Loop

| Item | Current state |
|---|---|
| Current feature | `FT-001 Owner Store Onboarding - Chinatown` (deferred by Owner) |
| Current Agile Loop | `TWIN-001_ST_DENIS_STAGING_TWIN` |
| Loop type | `OWNER_GATED_PRODUCTION_LIKE_STAGING_PARITY` |
| Loop status | `STG-008=PASS; STG-009_PHASE_A_OWNER_LOGIN=DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY; TWIN-001=PASS_OWNER_FIELD_TEST_ACTIVE` |
| Current package | Exact `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` is deployed to isolated Staging at Flyway V10 and passes manifest-v2 parity plus MOCK printing acceptance and field-test printing fixes smoke. |
| STG-008 state | Historical `PASS` synthetic foundation; TWIN-001 reconciled it in place to the current Operational Twin without reset or cross-Store crossover. |
| STG-009 Phase-A state | `DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY`: prior API/browser-equivalent evidence is retained; Owner manual UI acceptance is no longer the immediate loop. |
| AL-001 state | `PLAN_COMPLETE` |
| AL-002 state | PR #27 merged the backend foundation into `main`; Production remains on the older runtime and no production onboarding is established by that merge. |
| STG-002 state | Deployment package merged to `main` by PR #31; this does not establish a server Staging runtime. |
| STG-003 state | PR #35 merged the completed real local Docker rehearsal into `main`; final runtime Head `74dd6a628002f96e4f2b4fbe3cf479fb23ed8e01` is `FINAL_HEAD_REHEARSAL_PASS`. |
| STG-004 state | PR #38 merged the STG-004 runtime evidence. Exact SHA `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` passed PLAN, fresh PREFLIGHT, serial build/start, runtime verification, and isolated stop/start recovery. Server Staging remains running; Production remained unchanged. |
| STG-005 state | PLAN complete. The Owner approved CP-0 as a separate minimal Staging-only bootstrap implementation and accepted CP-4 as a feature-disabled KDS/Assembling boundary. Positive Kitchen/Assembling workflow remains `EVIDENCE_PENDING`. |
| STG-005A state | `DEPLOYED_TO_STAGING` execution evidence: PLAN `VALIDATED`, EXECUTE `CREATED`, REPLAY `REPLAYED`; exactly one synthetic Organization, Owner, source Store, credential, memberships and request exist. This is not Staging acceptance. |
| STG-006 state | `PASS` for candidate `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b`. Fresh read-only evidence confirmed retained Staging `4397f995...` / V8, isolated project/network/state, loopback bind, printing disabled, healthy endpoints, resource headroom, and unchanged Production continuity. No candidate release, deploy, Flyway, restart, login, or data mutation occurred. |
| OPS-001 state | `REPOSITORY_COMPLETE` through PR #87. The final exact `2837ae88...` runtime use passed release/env binding, V10-to-V10 deploy, repaired readiness, sanitized Flyway/runtime collection and same-image restart without weakening any guard. PR #87 later supplied the blocked-state-safe release/env preparation path, which was used by exact `6753855497...` for the bounded rebind/deploy/recovery continuation. Runtime use remains action-specific and Owner-gated. |
| STG-007 state | `PASS` at exact deployed Staging SHA `2837ae88e55142c99c6975f8b6575febffc913a1`. Environment digest `124eb472...`, continuation entry `8d744fa8...`, formal preflight `7174a295...`, readiness `19a8fec2...`, runtime collection `03337e71...`, restart readiness `6392783f...`, and same-image restart `2208d8ca...` all passed. Flyway remained exact V10/no-pending; health returned 200/200/200; exact container/image/release identity, printing, isolation and Production continuity were unchanged. |
| AL-003 state | PRs #61-#101 are `IN_MAIN`; Chinatown Phase B, validation/clone and REL-001 are `DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY`. Existing code, plans and evidence remain preserved; no Production acceptance result exists. |
| Staging Owner login prerequisite | Historical synthetic Owner/browser-equivalent foundation is complete; manual Phase-A closure is deferred by the Owner Twin priority. Retrieval remains private; no secret is stored in Git/evidence. |
| Current permitted work | Continue Owner manual field testing against the retained Operational Twin and verified Staging MOCK pipeline; bounded bugs return through repair, tests, Agent 6 and exact-SHA Staging retry under the active loop. |
| Explicitly not permitted | Production read/write/deploy/Flyway action; Staging downgrade/Flyway edit/destructive reset; Chinatown; modularization; REL-001; Production promotion; physical printer binding; Pad pairing; `REAL`; or public printer access. `IN_MAIN`, `DEPLOYED_TO_STAGING`, and `STAGING_ACCEPTED` remain distinct. |

The canonical [release/promotion policy](../AGILE_LOOP_OPERATING_MODEL.md#83-canonical-release-promotion-drift-and-recovery-policy)
now requires immutable RC freeze, exact same-artifact promotion, recurring
read-only drift detection with explicit Owner-triggered Twin sync, the
`APPLICATION_ROLLBACK_COMPATIBILITY_GATE`, and backup integrity plus isolated
restore-rehearsal readiness. These are future governance gates only; they do
not authorize a Production read, deployment, restore, or Staging mutation in
the current TWIN-001 loop.

Agent and worker execution is ephemeral. After a bounded task, the result and
evidence must be returned and persisted, the active session/process terminated,
and only known safe task-owned temporary resources cleaned. Unknown, shared,
unmerged, active, runtime, database, backup, and evidence resources remain and
are reported. Each loop reports its Agent and worktree accounting before
stopping.

The authoritative work records are [FEATURE_BACKLOG.md](../FEATURE_BACKLOG.md),
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md), and
[AL-001 technical plan](../agile/AL-001_OWNER_STORE_ONBOARDING_CHINATOWN_TECHNICAL_PLAN.md).
The concise [Current Project Handoff](CURRENT_HANDOFF.md) is navigation only;
these authorities and Git/runtime evidence win if it drifts.

The current fully qualified modular Loop identifiers and their legacy-label
mapping are authoritative in
[STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](../agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md);
historical short `AL-004`/`AL-005` labels must not be interpreted as the new
packages without that mapping.

### Current AL-003 Git ground truth

| Package | Git state | Current capability boundary |
|---|---|---|
| PR-A, PR-B, PR-B2, PR-B3, PR-B4 | `IN_MAIN` | Contract, V10/idempotency foundation, revision/lock consistency, generic profile registry, and exact profile identity. |
| PR-C / PR #47 | `IN_MAIN` at `ba169ed8b689ddef8dffe94deee82fea191cdcfb` | Generic Category/Station/Item base transaction only. |
| PR-D / PR #52 | `IN_MAIN` via merge `13f26f1` | Generic source-option cloning and parent mapping are current-main capability. |
| PR-E / PR #54 | `IN_MAIN` via `82b8059f6af1c7dff4eeb1648ca47bec039b5e52` | Concrete versioned Chinatown Profile, target override composer, and bounded Small label compatibility are current-main capability. |
| PR-F0 / PR #55 | `IN_MAIN` via merge `6773fd0b78d7b3b33ee0d2a8b1d593a7b8c6af2` | Internal read-only option-plan composition/validation, shared execute parity validation, and bounded structured diagnostics. |
| PR-F / PR #56 | `IN_MAIN` via merge `8f909525781804f61d1da388882f530da358c3c4` | Protected Owner validate/execute API facade reusing the internal planner, V10 coordinator, and lock-owning transaction without a second clone engine. |
| Attempt evidence / PR #58 | `IN_MAIN` via merge `1482cddf4f10478ed571e4d7422100dc40006f6b` | Immutable record of the failed exact-SHA preflight and safe V8 runtime recovery. |
| Private-leaf repair / PR #59 | `IN_MAIN` via merge `c3956592da8a33092ab745c7cc6aac05e9babfa7` | Staging-only path-validation correction and governance rules; no runtime action or business/API change. |
| Owner decisions governance sync / PR #60 | `IN_MAIN` at `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d` | Documentation-only alignment of product direction, access semantics, FT-001 gaps, and runtime gates. |
| Current project handoff / PR #71 | `IN_MAIN` at `5baada03935e004d80af1e7a36fb7db39bd6abbb` | Navigation snapshot only; no runtime action or capability change. |
| Modular architecture / PR #61 | `IN_MAIN` at `bbb1af9520c188b6ef6362e783284ba4001a7e63` | Defines the Generic Store Provisioning Engine, Versioned Store Profiles, Reusable Provisioning Modules, and anti-hardcode boundary; no API/runtime behavior. |
| STG-005B Synthetic St-Denis baseline / PR #62 | `IN_MAIN` at `467ab5f8758fdafc3d6d0d3e2ede4145a9fb3b4b` | Guarded source baseline entered repository main; it is not runtime execution evidence. |
| AL-003S Staging acceptance preparation / PR #63 | `IN_MAIN` at `732d77c89ff067982702426ff918d5e097e1d0fb` | Guarded launcher, passive evidence, approval/identity binding, immutable image pin, command plan, acceptance template, and rollback boundary only; no runtime action. |
| AL-004 Generic Store Profile contract / PR #64 | `IN_MAIN` at `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6` | Exact identity/version/composition, module policies, activation requirements, canonical fingerprinting, and safe summaries; no concrete profile, endpoint, migration, provisioning engine, UI, or runtime action. |
| AL-005A Staff/Table plan / PR #65 | `IN_MAIN` at `8f58bcbfca253c1598b967f4d17c04c0be1cce5b` | Planning only; no writer, endpoint, migration, credential, table, or runtime action. |
| Printer Store-isolation repair / PR #66 | `IN_MAIN` at `f483a4640503c20f6eec1e2e9ae1d198bf23d1f3` | Rejects cross-Store printer config updates, cross-Store automatic dispatch, and PAD_DIRECT printer-health updates; no migration, endpoint shape, transport, Android, or runtime action. |
| AL-005 Printing plan / PR #67 | `IN_MAIN` at `65e3d3ced2b5b05eb36d56ce67e475768ad19dff` | Reusable Store-scoped Printing Provisioning plan only; no writer, endpoint, migration, printer, assignment, mode change, test print, or runtime mutation. |
| AL-005B Device/Pad plan / PR #68 | `IN_MAIN` at `9e93573be97cfd01a9ad3efe64d55827854c497a` | Reusable Store-scoped Device/Pad Provisioning plan only; no endpoint, migration, device, token, pairing, Worker change, or runtime mutation. |
| AL-006 Activation plan / PR #69 | `IN_MAIN` at `dc682203b2b24bbdb453a5520b297b9051139f13` | Fail-closed workflow plan only; lifecycle and validator are conceptual; no Store status transition or activation writer. |
| REL-001 Production RC plan / PR #70 | `IN_MAIN` at `645d4909625f70fc241d5468382d66a30a030fb1` | Exact-SHA release gates only; no selected candidate, Staging pass, Production deploy, or activation action. |
| Post-stack Ground Truth audit / PR #72 | `IN_MAIN` at `33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` | Capability/gap governance only; no runtime action. |
| STG-006 evidence/governance / PR #73 | `IN_MAIN` at `85d97b7327b2e15aa561ed28a5788b92cedf6f5b` | Passive evidence only; `STG-006=PASS`, no deployment or runtime mutation. |
| Readiness health fingerprint repair / PR #80 | `IN_MAIN` at `39fa284b7bccd64d650c396f2c7532b0a0858b4b` | Correct optional-health classification; later runtime use separately proved repaired readiness PASS. |
| Flyway success-token repair / PR #81 | `IN_MAIN` at `63600b13b10a5549d9095a03c94e69a9f880af9f` | Exact `success::text=true` collector validation; later runtime use separately proved runtime collection PASS. |
| Restart readiness/fail-closed repair / PR #82 | `IN_MAIN` at `2837ae88e55142c99c6975f8b6575febffc913a1` | Bounded three-endpoint post-start readiness plus nonzero-exit blocked-state persistence; its exact merged SHA later passed the complete STG-007 continuation. |
| STG-007 final evidence / PR #83 | `IN_MAIN` at `2ed56b06f37c9257a655ec334f81e31ca4a518a6` | Evidence/governance only; no backend/frontend/Android, migration, deployment tooling, runtime configuration, or runtime mutation. |
| STG-008 entry evidence / PR #84 | `IN_MAIN` at `828af4e84581dcb051248beee694c307a65210c5` | Sanitized credential-gate evidence/governance only; no application, migration, runtime configuration, credential, or business-data mutation. |
| STG-008 guarded one-shot Flyway safety repair / PR #85 | `IN_MAIN` at `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6` | Exact-profile, no-migration startup safety reconciliation plus tests/governance; its original publication was not runtime evidence, and later exact `6753855497...` superseded the old deployed identity. |
| STG-008 dependency-repair Ground Truth / PR #86 | `IN_MAIN` at `4759a23b1a00d3254936e6c8eeb0ec33012b5145` | Documentation-only closure for PR #85 and its exact-SHA recovery gate; no runtime action. |
| STG-008 release-rebind serialization repair / PR #87 | `IN_MAIN` at `4b954e09a365fec909ed6da3ddf8fa9f13639cdc` | Dedicated blocked-state-safe release/env preparation plus tests and evidence. It preserved the reviewed records and every ordinary action block, then supported the later exact `6753855497...` Staging rebind/deploy/recovery continuation. |

PR-D promotion evidence is now historical main evidence: semantic source
`5a0dc09944b4b0945fe95027d7f12647212ea559`, reviewed promotion head
`5f6438ad1ffe1379eb3740a3db64180ce2433bfa`, and merge `13f26f1`.
PR-E entered `main` through PR #54 at
`82b8059f6af1c7dff4eeb1648ca47bec039b5e52`.
PR-F0 is rebuilt from that main commit, rather than promoted from its historical
stacked branch. Its evidence is
[AL-003 PR-F0 Read-only Planning Boundary](../agile/AL-003_PR_F0_READ_ONLY_PLANNING_BOUNDARY.md).

Current `main` contains the generic clone transaction, source-option layer,
complete Chinatown Profile, read-only planner, and protected Owner HTTP API.
This is repository capability only. No real clone has run. STG-006 freshly
confirmed Staging at `4397f995...` / Flyway V8 and minimum Production
continuity at full SHA `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`.
Production Flyway was not queried and remains retained V7 evidence only.

The exact `468b8705...` continuation exposed the historical browser CORS
failure. PR #99 repaired it, and exact `1a3f2e...` subsequently passed formal
preflight, V10-to-V10 deploy, readiness, private credential rotation, API and
real-Chrome browser-equivalent acceptance. The former Phase-A stop and the
read-approval stop are historical and deferred by the Owner Twin route. The
inventory checkpoint stop was
`TWIN-001_PRODUCTION_INVENTORY_COMPLETE_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`.

OPS-001 adds repository-only guarded helpers for a detached release plus
four-field atomic private-env rotation, sanitized Flyway/runtime collection
plus same-container restart, and secret-FD Owner onboarding/clone acceptance.
Each runtime action is exact-SHA/environment/action bound and consumes one
private Owner approval digest. The package changes no application, migration,
Compose/runtime configuration, or business API. Historical PR #81 runtime use
ended at restart `NO_GO`; PR #82 then entered `main` at exact `2837ae88...`.
A fully fresh Owner-authorized continuation deployed that exact SHA
V10-to-V10 and passed release/env binding, formal preflight, repaired
readiness, sanitized Flyway/runtime collection, one same-image restart, and
post-restart verification. No API, synthetic write, credential or Production
mutation followed.
See [OPS-001 local evidence](OPS-001_STAGING_SECRET_SAFE_TOOLING_EVIDENCE.md)
and the
[OPS-001 runbook](../../../deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md).
[Final STG-007 continuation evidence](STG-007_EXACT_SHA_CONTINUATION_EVIDENCE.md)
records the exact artifacts and runtime boundaries.
[STG-008 entry evidence](STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md)
records the later read-only runtime/topology check, zero-row synthetic state,
safe Store-ID-1 proof, credential-contract conflict, and `NO_GO` decision.
The Owner later aligned that credential decision. Fresh plan readiness passed,
but the first password-free one-shot exposed the shared cloud/Flyway safety
conflict before the command path and retained blocked state with zero writes.
[STG-008 Flyway guard repair evidence](STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md)
records the failure, continuity, bounded repair and next exact-SHA recovery
Owner Gate.
[STG-008 release-rebind serialization repair evidence](STG-008_RELEASE_REBIND_SERIALIZATION_REPAIR_EVIDENCE.md)
records the historical fresh baseline, deterministic sequencing deadlock,
bounded PR #87 repair and final Agent 6 acceptance. The later exact
`6753855497...` continuation completed Batch A and cleared only the reviewed
old blocked pair; its following plan failure and replacement repair are in
[STG-008 Non-Web Request-Context Repair Evidence](STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md).
PR #72 and PRs #61 through #71 are `IN_MAIN`. STG-006 is historical passive
evidence. The former runtime acceptance prerequisite
`AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING` is historical and deferred
by the current TWIN-001 route.

The post-stack capability matrix remains a historical capability snapshot in
[Post-Stack Ground Truth Audit](POST_STACK_GROUND_TRUTH_AUDIT.md); its old loop
order and runtime authorization are superseded by TWIN-001 and are not current
authority. The completed passive observation is recorded in
[STG-006 Exact-Main Passive Preflight Evidence](STG-006_EXACT_MAIN_PREFLIGHT_EVIDENCE.md).
`STG-006 = PASS`. OPS-001 is `REPOSITORY_COMPLETE` in PR #74. STG-007 then
stopped before mutation because the formal preflight could not accept an exact
retained Staging listener without first stopping it. The bounded repair and
evidence are in
[STG-007 Preflight Upgrade Port Guard Repair Evidence](STG-007_PREFLIGHT_UPGRADE_PORT_GUARD_REPAIR_EVIDENCE.md).
That repair entered main through PR #75 at `b93d8efdbd699333d73d9ffcc29e8f8443e51764`.
Batch A restarted from that SHA and passed its fresh read-only baseline, then
stopped before mutation at the retained-release/helper first-use gap. The
second bounded repair is recorded in
[STG-007 Release Tool Bootstrap Repair Evidence](STG-007_RELEASE_TOOL_BOOTSTRAP_REPAIR_EVIDENCE.md).
At that stop, the pending repair would expire `b93d8ef...` and require every
Batch A gate to restart from the next exact main; Batch B was ineligible.

PR #76 then merged that bootstrap at
`e6fac236c7620cd2f579d2a180367f4f753a6d42`. Batch A restarted and passed the
fresh baseline plus exact candidate import, but stopped before release/env
mutation when the bootstrap rejected the established owner-owned mode `0750`
state parent. The deterministic guard repair is recorded in
[STG-007 State-Root Mode Guard Repair Evidence](STG-007_STATE_ROOT_MODE_GUARD_REPAIR_EVIDENCE.md).
At that stop, the pending repair would expire `e6fac236...` and require Batch A
to restart from the next exact main; Batch B was ineligible.

PR #77 merged that state-parent correction at
`5c6d8bb70d74756cc7fe3f76b2d43cb07c6e6f33`. The next Batch A restart passed
fresh gates and exact candidate import; bootstrap then delegated, but the
release helper stopped before approval consumption/mutation at the established
mode-`0750` releases-parent boundary. The fourth deterministic repair is in
[STG-007 Releases-Root Mode Guard Repair Evidence](STG-007_RELEASES_ROOT_MODE_GUARD_REPAIR_EVIDENCE.md).
At that stop, the pending repair would expire `5c6d8bb7...` and require Batch A
to restart from the next exact main; Batch B was ineligible.

PR #78 merged that releases-parent correction at
`35ccf5cb823bb22b449d8b82baa2f22db2e242df`. Batch A restarted, created the
exact release and consumed approval, then stopped before recovery/env write at
environment rotation's remaining state-parent mode hardcode. The fifth repair
and full state-root audit are in
[STG-007 Rotation State-Root Mode Guard Repair Evidence](STG-007_ROTATION_STATE_ROOT_MODE_GUARD_REPAIR_EVIDENCE.md).
At that stop, the pending repair would expire `35ccf5cb...`; its consumed
authorization could not be reused, Batch A had to restart with a new exact
main and approval, and Batch B was ineligible.

PR #79 merged the rotation reconciliation at
`868e229f1b5afd28163e5031ad8fabffaad651f6`. The full Batch A restart then
passed, including release/env rotation and formal preflight. Conditional Batch
B deployed that exact SHA, applied V9 and V10, and returned healthy frontend,
backend and SockJS endpoints with printing disabled, isolated mounts/networks,
and unchanged Production continuity. The passive readiness collector then
failed before emitting PASS evidence because Docker omits `State.Health` from
services without a healthcheck and shared fingerprinting used unsafe direct
field access. No collect-evidence approval or same-image restart followed. The
bounded repair is recorded in
[STG-007 Readiness Health Fingerprint Repair Evidence](STG-007_READINESS_HEALTH_FINGERPRINT_REPAIR_EVIDENCE.md).

PR #80 merged that repair at
`39fa284b7bccd64d650c396f2c7532b0a0858b4b`. A newly authorized V10-aware
continuation reconfirmed the `868e229f...` / V10 baseline, then bound,
preflighted and deployed exact `39fa284b...` without a new migration. Repaired
readiness passed. The following read-only collector consumed its separate
approval but stopped before PASS evidence because PostgreSQL
`success::text=true` did not match the validator's mock-only `t` token. No
restart or blocked marker followed. The bounded repair and exact runtime
boundary are recorded in
[STG-007 Flyway Success Token Repair Evidence](STG-007_FLYWAY_SUCCESS_TOKEN_REPAIR_EVIDENCE.md).

PR #81 merged the Flyway token repair at
`63600b13b10a5549d9095a03c94e69a9f880af9f`. A fresh exact-main continuation
passed V10 entry, release/env binding, formal preflight, V10-to-V10 deploy,
repaired readiness and sanitized runtime collection. The following same-image
restart retained all exact containers/images and V10, but its single immediate
backend health request raced the roughly 36-second application startup and
returned 502. Runtime subsequently recovered to `200/200/200`; Production
port-80 health and container continuity remained unchanged. The action remains
`NO_GO`: its approval is consumed, its evidence is empty/non-PASS, and explicit
`die/exit` bypassed the ERR-only blocked-marker handler. The bounded repair is
recorded in
[STG-007 Restart Readiness and Fail-Closed Repair Evidence](STG-007_RESTART_READINESS_FAIL_CLOSED_REPAIR_EVIDENCE.md).

PR #82 merged that bounded repair at
`2837ae88e55142c99c6975f8b6575febffc913a1`. The continuation restarted from
fresh Git, release, private-environment, V10-entry, formal-preflight,
readiness, collection and restart approvals. Exact `2837ae88...` was deployed
to Staging V10-to-V10 for STG-007. Repaired readiness and sanitized runtime collection
passed; the reviewed same-image helper then stopped/started only the exact
existing Staging containers, waited for backend/frontend/SockJS HTTP 200, and
emitted PASS after container, image, release, environment, Flyway and project
identity remained unchanged. Printing stayed `DISABLED/false`, isolation was
intact, and permitted Production continuity metadata plus health remained
unchanged. See
[STG-007 Exact-SHA V10 Continuation Evidence](STG-007_EXACT_SHA_CONTINUATION_EVIDENCE.md).
`STG-007 = PASS`; no STG-008 action is implied.

The `IN_MAIN` acceptance preparation is documented in
[AL-003S Staging Acceptance Preparation](../agile/AL-003S_STAGING_ACCEPTANCE_PREPARATION.md).
Its launcher closes the bounded non-web command-entry gap but does not authorize
runtime use. The package provides a passive Production-continuity/resource
collector; STG-006 used a single bounded read-only Coordinator timeline for its
own preflight evidence, not an acceptance action. STG-007 has now evidenced
release/environment rotation, exact V10-to-V10 deployment, Flyway V10,
repaired readiness, sanitized runtime collection and same-image restart PASS.
The separately gated synthetic topology/source and secret-safe Owner/API
acceptance sequences remain pending.
Local checks and explicit pending gates are retained in
[AL-003S Preparation Evidence](AL-003S_STAGING_ACCEPTANCE_PREPARATION_EVIDENCE.md).

The Smart Multi-Agent policy is authoritative in
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md): parallelize
only independent work, keep serial work with the Coordinator, use one runtime
executor, and finish normal rounds with zero active Agents.

The independent printer Store-isolation repair is documented in
[AL-005 Printer Store Isolation Dependency Repair](../agile/AL-005_DEPENDENCY_REPAIR_PRINTER_STORE_ISOLATION.md).
It is `IN_MAIN` at PR #66's merge and remains limited to Store-bound config
update, automatic dispatch, and PAD_DIRECT printer-health guards; it does not
authorize printing provisioning, printer configuration, a test print, or any
runtime action.

The downstream Printing audit is documented in
[AL-005 Printing Provisioning Module Plan](../agile/AL-005_PRINTING_PROVISIONING_MODULE_PLAN.md).
It keeps Store Profiles endpoint-free, preserves `DISABLED` until operational
acceptance, and records PR #66 plus strict-mode, a generic pre-job enabled-module
gate, role/assignment integrity, and idempotency as inactive-writer gates.
AL-005B device readiness is a later runtime-binding/activation gate, not a gate
for creating inactive logical configuration.

The `IN_MAIN` Device/Pad audit is documented in
[AL-005B Device and Pad Provisioning Module Plan](../agile/AL-005B_DEVICE_PAD_PROVISIONING_MODULE_PLAN.md).
It preserves the current Store-wide PAD_DIRECT queue with no per-device module
assignment and keeps identities, tokens, pairing, auto-print, and Worker health
outside versioned profiles. It adds no endpoint, migration, pairing, credential,
Worker, or runtime behavior. Executable work remains blocked by the documented
credential, idempotency, integrity, and runtime-evidence gates.

The bounded Activation audit is documented in
[AL-006 Store Activation Workflow Plan](../agile/AL-006_STORE_ACTIVATION_WORKFLOW_PLAN.md).
It records that no unified activation orchestrator exists, that conceptual
readiness stages are not current `stores.status` values, and that legacy direct
`active` writes, evidence persistence, verifier contracts, and runtime gates
must be resolved before a future exclusive activation transition is built.

The Production Release Candidate boundary is documented in
[REL-001 Chinatown Production Release Candidate Plan](../agile/REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE_PLAN.md).
It requires one exact merged SHA to pass the same-SHA Staging acceptance plus a
fresh, separately approved Production gap/backup/compatibility review. No RC
SHA is selected, and no runtime evidence was collected by that package.
The current Production Compose uses a release-relative PostgreSQL bind path and
the current deploy helper combines backend/frontend builds without the retained
1 GiB memory gate. Production deployment is `NO-GO` until a bounded repair
preserves a fixed external state root and enforces guarded serial builds.

Git ground truth must always distinguish `MERGED_ON_GITHUB`, `IN_MAIN`,
`DEPLOYED_TO_STAGING`, and `DEPLOYED_TO_PRODUCTION`. A stacked PR merged into a
non-`main` base remains stacked-only until an independently reviewed promotion
enters `main`. Production runtime, current main, stacked-only development, and
unimplemented work are separate states.

Every code iteration must complete the mandatory governance sync in
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md) before its
review gate. The Planbook, Feature Backlog, System documentation, API contract,
and applicable technical plan must describe the same code and deployment
boundary as the commit under review.

At the start and end of each iteration, read this Planbook and verify Git and
any separately authorized runtime ground truth. Ground truth overrides stale
navigation, and governance drift must be corrected in the same iteration. The
Dependency Repair Auto-Loop and continuous-next-action rules are authoritative
in [AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md); they are
not duplicated here.

### STG-001 planning record

- Planning branch: `codex/stg-001-staging-environment-plan`.
- Planning baseline: `origin/main` commit
  `eadf100295c351a5f14a80fb2fb6eea351c2931b`.
- The recommended architecture uses an exact-SHA detached Staging worktree,
  explicit Compose project name, SHA-specific images, loopback-only ports, and
  a dedicated PostgreSQL state root.
- Initial Staging data is empty or synthetic only. Printing defaults to
  `DISABLED` and may use `MOCK` for bounded acceptance; it must not connect to
  a real printer or production Pad.
- See
  [STG-001 Isolated Staging Environment Plan](../agile/STG-001_STAGING_ENVIRONMENT_PLAN.md).
- Historical planning status: `PLAN_COMPLETE_WAITING_FOR_OWNER_APPROVAL`.
  STG-002 is now merged as a package; server access, migration execution, and
  deployment remain independently owner-gated.

### STG-002 and STG-003 verification record

- PR #31 merged the isolated STG-002 deployment package into `main`.
- Historical PR #32 was merged into the STG-002 branch before PR #31 reached
  `main`; it remains an immutable record and cannot receive the final local
  Docker evidence.
- The continuation branch `codex/stg-003-docker-rehearsal` was safely rebased
  onto `origin/main` commit
  `4ac1d10cde169bf7ebc807aac3624b0323e3c440`.
- A real local Docker Desktop rehearsal passed at exact commit
  `b17ffa9a397bef62d474a58b649f1e55467a974f`.
- The rehearsal built and started exactly `db`, `backend`, and `nginx` under
  project `restaurant-pos-staging`, exposed only `127.0.0.1:18080`, used an
  isolated PostgreSQL path, and kept printing `DISABLED`.
- PostgreSQL 16.14 applied Flyway V1-V8 on the first startup. The second
  startup validated schema version 8 with no migration necessary, and V8's
  table, Organization/idempotency unique constraint, and Store lookup index
  were verified.
- Backend health, frontend root, `/api`, and SockJS `/ws/info` returned HTTP
  200. An ordinary `/ws` GET returned 400 because it was not a WebSocket
  Upgrade; no STOMP session was attempted.
- Cleanup removed only local Staging containers and network. The isolated
  PostgreSQL state was retained; no volume deletion occurred.
- Full evidence is in
  [STG-003 Local Isolated Rehearsal Evidence](STG-003_LOCAL_REHEARSAL_EVIDENCE.md).
- PR #35 repeated the concise real Docker regression against final runtime Head
  `74dd6a628002f96e4f2b4fbe3cf479fb23ed8e01`, recorded
  `FINAL_HEAD_REHEARSAL_PASS`, and merged the completed STG-003 work into
  `main`.
- Status: `STG-003_LOCAL_REHEARSAL_COMPLETE`. Server Staging and deployment
  remain separately Owner-gated.

### STG-004 preflight review record

- Review branch: `codex/stg-004-first-deploy-preflight`.
- PR #33 is based on `main` after the PR #35 merge.
- Scope is limited to a read-only same-host preflight, validation-only default
  deploy wrapper, explicit Owner start gate, and plan-only stop/rollback
  controls.
- The Owner later approved one STG-004 server run for exact SHA
  `3c1b117e137cc90b984bb392cb3f9e4b7a7f149f`, isolated under
  `/srv/restaurant-pos/staging`, with project `restaurant-pos-staging`,
  loopback bind `127.0.0.1:18080`, and printing `DISABLED`.
- PLAN command categories were local governance reads and local Git
  baseline/source inspection only. The current Git branch, local HEAD, and
  `origin/main` were all `main` /
  `3c1b117e137cc90b984bb392cb3f9e4b7a7f149f`; the local worktree was clean.
- Prior read-only server evidence recorded 2 CPUs, 2.1 GiB available memory,
  44 GiB free disk, an unused port `18080`, and continuously running production
  project `cloud`. Those observations are pre-PLAN context, not fresh deploy
  evidence.
- PLAN result: `NO_GO`. The approved
  `deployment/cloud/staging-deploy.sh` start path invokes one
  `docker compose build backend nginx` command and does not enforce
  `--parallel 1` or an equivalent sequential-build control. That conflicts
  with the Owner's explicit requirement that backend and frontend builds run
  sequentially. The exact approved release cannot be modified in place or
  bypassed.
- No new SSH command, server file write, Docker build/start/stop, Flyway
  execution, environment creation, or deployment occurred after this gate was
  identified.
- Evidence paths:
  `deployment/cloud/staging-deploy.sh`,
  `deployment/cloud/staging-server-preflight.sh`,
  `deployment/cloud/docker-compose.staging.yml`, and this checkpoint.
- Unresolved risk: a combined Compose build may run services concurrently and
  exceed the shared-host resource envelope. The separate no-Swap observation
  increases the consequence of that uncertainty.
- Owner decision: use a minimal review branch to replace the combined build
  with `build backend` followed by `build nginx`, preserving every existing
  exact-SHA, preflight-evidence, environment-digest, project/root, and
  `--execute-start` gate.
- The regression harness requires backend success before nginx starts, proves
  backend failure prevents nginx and `up`, and rejects any combined
  `build backend nginx` command.
- VERIFY command categories were local shell syntax checks and isolated
  fake-Docker guard/preflight/control tests only. All Staging scripts passed
  `bash -n`; `test_staging_guard.sh`,
  `test_staging_server_preflight.sh`, and
  `test_staging_server_control.sh` passed. No SSH, real Docker lifecycle
  command, Flyway execution, or server operation was part of this verification.
- Verification evidence paths:
  `deployment/cloud/tests/test_staging_guard.sh`,
  `deployment/cloud/tests/test_staging_server_preflight.sh`, and
  `deployment/cloud/tests/test_staging_server_control.sh`.
- Current Git baseline for the candidate is parent SHA
  `3c1b117e137cc90b984bb392cb3f9e4b7a7f149f` on branch
  `codex/stg-004-serial-build-fix`; the resulting review commit and PR are
  implementation commit
  `67f183ba998b88810e03db4b77b7c433ac5c3cf1` and draft PR #36 with base
  `main`.
- PR #36 merged as
  `35033645b5414f0804cc0aba92a8b8bb832bb074`. Its next Owner-approved
  isolated server run passed formal PREFLIGHT but stopped before backend image
  creation with `mkdir /nonexistent: permission denied`.
- Root cause: the wrapper removed ambient Docker configuration but set both
  `HOME` and `DOCKER_CONFIG` to non-writable `/nonexistent`. Read-only Compose
  validation did not need persistent CLI state, while BuildKit/buildx did.
- The bounded correction creates a `mktemp` state root with mode `0700`, uses
  child `home` and `docker-config` directories, rejects symlink replacement,
  checks `docker --context default compose version`, and removes the state on
  `EXIT`, `ERR`, `INT`, and `TERM`.
- Existing exact-SHA, preflight-evidence, environment-digest, project/root,
  printing-disabled, and sequential backend-then-nginx-then-up gates remain
  unchanged.
- Review branch: `codex/stg-004-docker-cli-state-fix`. Verification is local
  and uses fake Docker fixtures only; it does not authorize SSH or deployment.
- PR #37 merged the Docker CLI-state correction. The Owner then approved exact
  SHA `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`; the former SHA
  `35033645b5414f0804cc0aba92a8b8bb832bb074` and all earlier environment and
  evidence digests are invalid for this run.
- PLAN command categories: local governance/Git inspection and server
  read-only resource, Production-continuity, port, and Staging-state checks.
  Result: `PASS`. The host reported 2 CPUs, about 2.2 GiB available memory,
  44 GiB free disk, no listener on port `18080`, no existing Staging
  container/network, and no leftover isolated Docker CLI-state directory.
  Production `cloud` services `db`, `backend`, and `nginx` were running with
  unchanged baseline IDs, start times, and zero restarts.
- PREFLIGHT command categories: fetch into the independent Staging bare
  repository, create an exact detached release, update only the private
  Staging identity fields, and run the formal read-only preflight. The release
  is clean at the approved SHA, the environment remains owner `ubuntu` mode
  `0600`, and printing is `DISABLED`.
- Fresh environment SHA-256:
  `926a075e482215b1e8c0917a96db483f342dfed895adfe122f1c9cccb63fa94c`.
  Fresh evidence:
  `/srv/restaurant-pos/staging/evidence/stg-004-preflight-4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c.txt`
  with SHA-256
  `01fca943915a922a389c3d00d6e38bb5dcbcae3dc5bed5e1718daf1d875f1707`.
  Every formal gate passed; both exact-SHA images were
  `PENDING_PREBUILD`, the expected first-build state.
- EXECUTE command category: the approved release's guarded
  `staging-deploy.sh --execute-start` path. It revalidated the exact release,
  environment and evidence digests, then built backend first, built nginx only
  after backend success, and started only project `restaurant-pos-staging`.
  Both exact-SHA images built successfully; the isolated Docker CLI state
  count was zero before and after the command.
- VERIFY command categories: project-scoped formatted Docker inspection,
  loopback HTTP/SockJS checks, read-only PostgreSQL/Flyway queries, filtered
  backend logs, resource observation, and an Owner-approved stop/start of only
  `restaurant-pos-staging`. PostgreSQL 16.14 retained exactly eight successful
  migrations through schema version 8; second startup ran no migration, and
  JPA/application startup succeeded.
- Final Staging services are running with only `127.0.0.1:18080`; printing is
  `DISABLED`. Production `cloud` container IDs, start times, running states,
  and zero restart counts remained unchanged. All deploy/verify/restart Docker
  CLI temporary state roots were removed.
- Evidence:
  [STG-004 Same-Host Server Staging Evidence](STG-004_SERVER_STAGING_EVIDENCE.md).
- Unresolved risks: frontend dependency audit findings; no synthetic
  login/onboarding, real STOMP, Android, restore, load, or soak validation.
- Next state:
  `STG-004_SERVER_STAGING_RUNNING_WAITING_FOR_OWNER_VALIDATION`.

### STG-005 synthetic acceptance planning record

- Planning branch: `codex/stg-005-synthetic-acceptance-plan`.
- Planning baseline: `origin/main` commit
  `2e6be1ac44f59cd6e005e68e61f8c567ea80022e`.
- Planned runtime remains exact STG-004 SHA
  `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`, Compose project
  `restaurant-pos-staging`, SSH-tunnel-only access, and printing `DISABLED`.
- Command categories executed in PLAN: local governance/source reads and local
  Git baseline inspection only. No SSH, API, database, Docker, migration,
  restart, account, Store, menu, table, or order write occurred.
- Evidence:
  [STG-005 Synthetic Business Acceptance Plan](STG-005_SYNTHETIC_ACCEPTANCE_PLAN.md).
- Gate result: the empty Staging runtime has no confirmed formal first
  Owner/Organization/source Store bootstrap path. AL-002 requires all three,
  and the current staging configuration disables default/demo/bootstrap users,
  developer switching, and Platform Admin entry.
- Additional boundary: current backend and frontend feature configuration
  disables KDS, so positive Kitchen/Assembling workflow acceptance requires a
  separate Owner decision and exact-SHA authorization.
- Current resource state: no new observation was performed in PLAN; use the
  immutable STG-004 evidence and do not infer current runtime health from this
  documentation commit.
- Unresolved risks: synthetic bootstrap provenance, KDS/Assembling acceptance
  scope, and final Organization/Store cleanup through supported APIs.
- Next state:
  `STG-005_PLAN_COMPLETE_WAITING_FOR_OWNER_REVIEW`.

### STG-005A synthetic bootstrap implementation record

- Owner decisions: CP-0 authorized a minimal isolated-Staging bootstrap;
  CP-4 accepts the current KDS/Assembling feature-disabled boundary. No KDS
  enablement is included.
- Implementation branch:
  `codex/stg-005a-staging-synthetic-bootstrap`, based on historical
  `origin/main` `22ddc96728057056c194a453825d1c36884f7a92`; PR #40 is now merged into
  `main` at the AL-003 PR-A base
  `2613344d403365d61283ae440de16edffaaad788`.
- The one-shot Spring command exists only under the exact
  `cloud,staging-synthetic-bootstrap` profiles and a separate explicit enable
  property. Default mode validates only; write mode requires both `--execute`
  and `--password-stdin`.
- Guards bind the request to project `restaurant-pos-staging`, root
  `/srv/restaurant-pos/staging`, exact runtime/tool SHAs, the isolated Staging
  database identity, non-web mode, and printing `DISABLED`.
- The transaction creates only the synthetic Organization, source Store,
  BCrypt Owner identity, active Owner Organization membership, and active
  source-Store membership. It does not create menu, table, order, printer, Pad,
  or customer data.
- Flyway V9 adds only an idempotency/audit request table. It contains no seed
  data and has not been applied to the server by this loop. The running
  Staging evidence remains Flyway V8 until a separately approved migration.
- Exact replay returns the same IDs; changed content/password conflicts; a
  forced membership failure rolls back the request and all topology records.
  Evidence output contains only synthetic IDs, status, and SHAs.
- Verification is local only: focused guard, command, idempotency, credential,
  and rollback tests plus the full backend suite and compile are required
  before the Draft PR is published.
- Runbook:
  [STG-005A Synthetic Bootstrap](../../../deployment/cloud/README_STG005_SYNTHETIC_BOOTSTRAP.md).
- No SSH, Docker, Flyway, server command, bootstrap execution, synthetic
  runtime write, Production change, or KDS change occurred.
- Merge state: PR #40 merged the implementation and reserved migration V9 for
  `V9__add_staging_synthetic_bootstrap_requests.sql`. Runtime execution remains
  separately gated and unproven by the merge.

### AL-003 PR-A menu-clone contract record

- Branch: `codex/al-003-pr-a-plan-contract`, based on `origin/main`
  `2613344d403365d61283ae440de16edffaaad788` after PR #40.
- Product authority:
  [AL-003A Final Menu Comparison](../agile/AL-003A_FINAL_MENU_COMPARISON.md).
- Technical authority:
  [AL-003 Store Menu Clone Technical Plan](../agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md).
- The unique source is the current live menu of St-Denis, Store ID `1`.
  Repository seed data is historical reference only and cannot populate the
  clone.
- V9 is already occupied by STG-005A. AL-003 plans the append-only
  `V10__add_owner_store_menu_clone_requests.sql`; PR-A creates no migration or
  business implementation.
- No Store 1 query, SSH, Docker, Flyway execution, database access, runtime
  clone, Staging/Production write, merge, or deployment occurred.
- PR #41 merged this contract into `main`; PR-B is the only authorized
  implementation package after that merge.

### AL-003 PR-B idempotency and transaction foundation record

- Branch: `codex/al-003-pr-b-idempotency-foundation`, based on `origin/main`
  `11be5c94f9b73e3beb8ec1f84b4a5a3c586c9d34` after PR #41.
- V9 remains owned by STG-005A. PR-B adds only the append-only
  `V10__add_owner_store_menu_clone_requests.sql` for durable request/evidence
  state, a four-column scope/key unique constraint, and a target-Store index.
- The coordinator implements insert-if-absent, pessimistic row locking,
  completed replay, fingerprint conflict, in-progress rejection, and bounded
  sanitized completion/failure evidence. It never stores a full menu request,
  credential, token, printer endpoint, or raw failure text.
- Owner-approved contract correction: replay exposes only durable scope,
  revision, count, result-code, and safe-warning summary; it exposes no menu ID
  maps. `FAILED` is terminal for its key, and any revalidated retry uses a new
  idempotency key.
- DTO, profile, exception, and transaction interfaces are compile-time
  foundations only. No Controller is registered and no menu graph clone,
  Store 1 read, Chinatown override, revision mutation, or runtime action is
  included.
- An isolated PostgreSQL 16.14 run applied V1-V10, verified V10's exact
  table/constraint/index, passed cloud-profile JPA validation and health, then
  restarted against the same database with schema 10 and no migration needed.
  Focused, concurrency/replay, full backend, compile, diff, and secret checks
  are the PR exit evidence.
- Evidence:
  [AL-003 PR-B PostgreSQL/Flyway V10 verification](AL-003_PR_B_POSTGRES_FLYWAY_V10_VERIFICATION.md).
- No SSH, Staging/Production access, Store 1 query, real menu clone, merge, or
  deployment occurred.
- PR #42 subsequently merged this foundation into `main`; its former review
  state is historical and is not the current AL-003 stop state.

Architecture anchor: shared provisioning infrastructure remains generic while
Chinatown differences stay in the first versioned Store Profile; future profile,
printing, activation, staff/table, and device modules are direction only.

Dependency repair and Store Profile governance are authoritative in
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md).

### AL-003 exact-SHA Staging release gate

- Exact candidate: `8f909525781804f61d1da388882f530da358c3c4`, the
  merged PR #56 `origin/main` commit.
- A 2026-08-08 read-only preflight observed Staging still running the exact
  historical SHA `4397f995...` with Flyway V8, printing disabled, only
  `127.0.0.1:18080`, healthy frontend/backend/SockJS endpoints, and isolated
  project/network/database state.
- Production remained `main` at `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`
  with its same container IDs, start times, running state, and zero restarts.
  No Production write or lifecycle command occurred.
- Host evidence reported two CPUs, about 1.7 GiB available memory, and about
  42 GiB free disk. These are point-in-time readings; the 1 GiB stop threshold
  and serial backend-then-nginx build remain mandatory.
- Staging is missing V9 and V10. Production is missing V8 through V10. Only
  Staging V9/V10 is eligible for a later exact-SHA deployment approval;
  Production remains out of scope.
- Existing preflight evidence and images belong to older SHAs and cannot be
  reused. Runtime compatibility of the retained V8-era images with a V10
  database is unproven, so application rollback to them after migration is
  `NO-GO` without a separate compatibility gate.
- Gate result: `GO` to request Owner exact-SHA deployment approval; `NO-GO`
  for immediate deployment. No candidate release, fresh formal evidence,
  build, start, migration, bootstrap, validate, execute, or clone occurred.
- Evidence:
  [AL-003 Staging Release Read-only Preflight Evidence](AL-003_STAGING_RELEASE_PREFLIGHT_EVIDENCE.md)
  and
  [AL-003 Exact-SHA Staging Release and Acceptance Plan](../agile/AL-003_STAGING_RELEASE_ACCEPTANCE_PLAN.md).
- Historical next state before the approved attempt:
  `AL-003_STAGING_RELEASE_PLAN_WAITING_FOR_OWNER_APPROVAL`.

### AL-003 exact-SHA deployment attempt

- PR #57 entered `main` at
  `f73fce9aa1c9abff1796715f3258dc4f6bb22207`. The Owner separately approved
  runtime release SHA `8f909525781804f61d1da388882f530da358c3c4`.
- The fresh pre-write baseline passed: old Staging was healthy at
  `4397f995...` / Flyway V8, printing was disabled, `18080` was loopback-only,
  resources exceeded thresholds, and Production continuity matched retained
  evidence.
- The independent Staging repository created a clean detached candidate
  release. The private identity was updated and only the old Staging project
  was stopped to free the formal-preflight port.
- Formal preflight returned `NO-GO` before build because its directory
  canonicalizer attempted to `cd` into the PostgreSQL-owned UID-70 mode-0700
  persistent data leaf. This is a real upgrade-path guard defect.
- The pre-migration recovery guard restored the old private identity and old
  Staging runtime. Flyway remains V8; frontend, backend health, and SockJS info
  returned 200; Production container IDs, start times, states, and zero
  restarts remained unchanged.
- No candidate image, V9/V10 migration, bootstrap, validate, execute, Store 1
  read, clone, or Production mutation occurred.
- The failed private evidence has SHA-256
  `c0c926e77bafeacb2ad972c2580417791814b323e4a3ab9fc05462c475f384b5`.
- Dependency Repair Gate: fix only the opaque PostgreSQL-leaf validation and
  its regression test. Do not weaken directory permissions, bypass evidence,
  or edit the approved release. A merged repair requires a new full-SHA Owner
  approval and fresh evidence.
- Evidence:
  [AL-003 Staging Release Attempt Evidence](AL-003_STAGING_RELEASE_ATTEMPT_EVIDENCE.md).
- Next state:
  `AL-003_STAGING_RELEASE_NO_GO_WAITING_FOR_OWNER_REPAIR_APPROVAL`.

### AL-003 PostgreSQL private-leaf dependency repair

- PR #58 merged the attempt evidence into `origin/main` at
  `1482cddf4f10478ed571e4d7422100dc40006f6b`.
- The repair keeps `/srv/restaurant-pos/staging/state/postgres` owner-only. It
  canonicalizes the traversable `state` parent, then validates the exact
  `postgres` directory entry, non-symlink topology, owner (deploy user or
  `postgres:16-alpine` UID 70), and mode `0700` without entering the leaf.
- The same protected-leaf semantics cover the formal server preflight and the
  `staging-deploy.sh` input gate so a successful formal preflight is not
  followed by the same false rejection before build.
- Regression fixtures cover a non-traversable UID-70/mode-0700 leaf, leaf and
  parent symlink replacement, missing leaf, unexpected owner/mode, and the
  existing exact-SHA/evidence/printing/isolation guards.
- This package performed no SSH, Docker lifecycle operation, Flyway execution,
  bootstrap, validate, execute, clone, or Production/Staging mutation.
- Evidence:
  [AL-003 Staging Preflight Private-Leaf Repair Evidence](AL-003_STAGING_PREFLIGHT_REPAIR_EVIDENCE.md).
- Git state: `IN_MAIN` through PR #59 at
  `c3956592da8a33092ab745c7cc6aac05e9babfa7`; runtime deployment remains
  separately unverified and unauthorized.

### Historical AL-003 Staging Owner login prerequisite

- Status: historical `AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`; current
  route is deferred by the Owner Twin priority.
- Retained evidence says Staging remains Flyway V8 and STG-005A has never run;
  the repair did not query runtime tables. Exact user/membership row presence
  is therefore `EVIDENCE_PENDING`, not inferred absent.
- Repository inspection confirms STG-005A can create a synthetic Organization,
  source Store, Owner credential, active Organization membership, and active
  source-Store membership. It does not create a target Store. The formal
  onboarding API creates the inactive target and target-scoped staff.
- `StoreAccessService` grants an authenticated `OWNER` access to every Store
  whose Organization has the Owner's active Organization membership. No
  redundant target-Store membership is required for that Owner; Manager,
  Frontdesk, and other Store-scoped staff still require explicit memberships.
- No retained evidence establishes a known safe Staging credential, successful
  Owner login, target access, or authenticated clone API call.
- A future exact-SHA deployment cannot be labeled Staging acceptance-ready
  until an Owner-approved synthetic-only preparation proves bootstrap
  idempotency, target creation/access, Owner login, and authorization for
  validate/execute. Production credentials, raw SQL, authorization bypasses,
  and copied business data remain forbidden.

### Owner decisions and FT-001 direction (2026-08-08)

- Chinatown is the second planned real Production Store. The FT-001 endpoint
  is a Production-ready Store with access/staff, menu, tables, printing,
  devices, login, and actual order/print acceptance, not merely a Store row or
  clone response.
- The reviewed Chinatown Profile is frozen as the initial Production menu
  contract. Production Store 1 remains the live clone source and requires a
  separately approved read-only drift capture before Production clone.
- First initialization must use the generic clone engine and versioned profile;
  later ordinary changes may use Menu Management.
- Future Owner provisioning must offer Chinatown and St-Denis menu templates
  through the same generic engine. `ST_DENIS_MENU` remains a planned profile,
  not a current API/profile capability.
- Staging is a long-lived Production-like environment with synthetic-only data.
  No Production credential, database copy, customer/order/payment data, real
  printer, or device secret may enter it.
- Production release follows exact-SHA Staging acceptance, gap audit, formal
  Release Candidate, migration and backup/rollback review, Owner approval,
  exact-SHA deployment, and post-deploy verification.
- The complete gap matrix and bounded loop order are maintained in
  [FEATURE_BACKLOG.md](../FEATURE_BACKLOG.md). Recording those loops does not
  authorize their implementation or any runtime mutation.
- The modular target and classification gate are maintained in
  [STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](../agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md).

### AL-002 implementation record

- Review branch: `codex/al-002-owner-store-onboarding-backend`.
- Scope completed locally: exact-Organization Owner authorization, durable
  Organization-scoped idempotency record, BCrypt-backed target-Store-only staff
  membership provisioning, and inactive/printing-disabled Store defaults.
- Local verification completed: focused onboarding/security tests and the full
  backend Maven suite. This is code verification only, not owner approval,
  merge, migration execution, deployment, or production provisioning.
- Local PostgreSQL/Flyway verification completed against an isolated PostgreSQL
  16.14 database using the cloud profile: V1-V8 applied successfully, V8's
  table/unique constraint/index were verified, and a second startup validated
  the schema without reapplying V8. See
  [AL-002 PostgreSQL and Flyway V8 Local Verification](AL-002_POSTGRES_FLYWAY_V8_VERIFICATION.md).
- The populated staging/production Store Code duplicate risk, deployment, and
  production migration remain `EVIDENCE_PENDING` and owner-gated.

## 6. Next deployment entry conditions

No deployment is authorized by this planbook. A future implementation PR may
enter `DEPLOY` only when all applicable conditions are recorded:

1. The applicable Agile Loop has passed `VERIFY` and has explicit owner
   approval for `MERGE` and production deployment.
2. Backend, frontend, Android, migration, and deployment impacts are stated in
   the PR and the required automated tests pass.
3. Any schema migration is reviewed for forward compatibility and has an
   owner-approved backup/rollback plan. No `down -v`, restore, or destructive
   database action is implicit.
4. Production initialization inputs, including account passwords and printer
   endpoints, are supplied at runtime by an authorized owner and never placed
   in Git, migrations, seeders, logs, or documentation.
5. Store isolation, printer routing, and field acceptance criteria are checked
   on site before the new Store is operationally handed over.
6. Post-deployment observations are appended as new evidence; historical
   reports are not rewritten.

## 7. Rollback reference

The latest reported production runtime point is `4667f3c` on `main`
(`OPERATOR_CONFIRMED`). It is a **rollback reference**, not an automatic
rollback instruction. Any rollback requires owner approval, confirmation of
schema compatibility, and the deployment runbook. Never delete a database
volume, restore a backup, or run an unreviewed downgrade as part of rollback.

## 8. Owner approval boundaries

Codex may prepare branches, code, tests, commits, push a review branch, open a
PR, and merge a qualifying repository PR under the Operating Model's permanent
auto-merge gate. The following runtime actions require explicit Owner approval
for each occurrence:

- production deployment, SSH/runtime commands, or environment changes;
- production Store, user, membership, credential, device, printer, or table creation;
- production migrations, backup restore/rehearsal, data repair, or deletion;
- use of passwords, secrets, certificates, printer IPs, or pairing credentials;
- any print, reprint, job claim, payload retrieval, or job-state transition.

## 9. Operating maintenance

- Update this planbook after each approved deployment, field validation, or
  backlog/loop state change.
- Preserve evidence classifications exactly; do not promote
  `OPERATOR_CONFIRMED` to `MACHINE_VERIFIED` without new machine evidence.
- Keep full display-name rules in
  [FRONTDESK_GRAB_ITEM_NAME_RULES.md](../../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md),
  not in this planbook.
