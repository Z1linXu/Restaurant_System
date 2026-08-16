# Known Issues Backlog

## Phase B Part 1 repository-resolved gaps pending runtime validation

Owner has granted implementation authority for Phase B Part 1. The previously
identified Part 1 gaps are repository-resolved by the current implementation
and now require PR/merge, fresh Staging preflight, exact-SHA Staging deploy and
automated acceptance before Owner manual retest:

- Chain Master Menu persistence/versioning: `V18` + `V19`.
- `PRINTING_DISPLAY_RULES` Store Profile artifact support: `V18`.
- Store lifecycle/provisioning provenance and validation fixture visibility:
  `V18` plus workspace/overview/store context/UI filtering.
- Canonical idempotent Store materialization: Owner provisioning service and
  `owner_store_provisioning_requests`.
- Owner Create New Store UI: Owner Dashboard Part 1 panel.

Evidence:

- [PHASE_B_PART1_IMPLEMENTATION_AUDIT](agile/PHASE_B_PART1_IMPLEMENTATION_AUDIT.md)
- [PHASE_B_PART1_PACKAGE_PLAN](agile/PHASE_B_PART1_PACKAGE_PLAN.md)
- [PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](agile/PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md)

## KI-A11-5-001 - Store Profile artifact whitelist lacks A11 printing rules

Status: `REPOSITORY_RESOLVED_PENDING_STAGING_FLYWAY_V18`.

A11 governance requires post-A11 Store Profile versions to include a
`PRINTING_DISPLAY_RULES` artifact, and A11 V17 adds Store-owned printing
display rule tables. Fresh schema audit found V14
`store_profile_artifacts.artifact_type` still whitelists the original A4
artifact types and does not include `PRINTING_DISPLAY_RULES`; V17 does not
alter that whitelist.

This is not a runtime incident and does not rewrite historical
`ST_DENIS_CANONICAL_PROFILE/v1`. Phase B Part 1 migration V18 extends the
artifact whitelist additively; V19 creates `ST_DENIS_CANONICAL_PROFILE/v2`
with a `PRINTING_DISPLAY_RULES/v1` artifact. Runtime closure still depends on
exact-SHA Staging applying V18-V20 successfully.

## Phase A11/A11.5 resolved gating risk

Owner has declared `PHASE_A11_OWNER_ACCEPTANCE = PASS`. Phase B is no longer
blocked by A11 Owner acceptance or by A11.5 design. It remains intentionally
stopped until explicit Owner approval for Phase B implementation.

## KI-A10-001 — KDS disabled module gate returns generic 500

Status: `OPEN_NON_BLOCKING`.

A10 runtime validation confirmed KDS-disabled access fails closed, but the HTTP
shape is currently a generic `500 Internal server error` instead of a cleaner
module-disabled or unavailable-capability response. No unauthorized mutation
occurred, so this is not a Phase A acceptance blocker. Clean up response shape
before module-gate UX polish.

## Phase A10 resolved architecture risk

`PHASE_A_IMPLEMENTATION_COMPLETE = YES` and
`PHASE_A_AUTOMATED_ACCEPTANCE = PASS`. Owner final Staging acceptance remains
pending. Phase B, Phase C, Chinatown, Sainte-Catherine and Production
promotion remain unauthorized.

## Phase A9 resolved architecture risk

The legacy coupling risk is addressed by
`PHASE_A9_LEGACY_COUPLING_REMOVAL` at repository-implemented / pending-final-
validation status. Current shared business runtime code has no active
Store-ID/name hardcode for Chinatown, St-Denis or Sainte-Catherine. Remaining
historical profile/source references are classified as bounded profile
identity, staging-tool guard or documentation/evidence, not runtime Store
branching.

Legacy direct active Store creation is disabled until Phase B provisioning,
Owner onboarding/menu-clone HTTP facades are `PLATFORM` capability gated, and
blank/unknown persisted printing mode now fails closed to `DISABLED`. No
Production incident or active P0/P1 is introduced by A9; no Flyway migration is
expected.

## Phase A8 resolved architecture risk

The prior risk that Printing, Pad devices, runtime print mode, Store logical
printer topology and physical binding were mixed behind `printing_enabled`,
`printing_mode` and legacy feature flags is resolved at the contract layer by
`PHASE_A8_HARDWARE_CAPABILITY_CONTRACT`. Remaining physical printer/Pad
binding work is still a separate runtime gate, not an A8 blocker.

Current final productization update (2026-08-13): the Owner closed the 30-answer
gate and authorized [FINAL_PRODUCTIZATION_PLANBOOK](agile/FINAL_PRODUCTIZATION_PLANBOOK.md)
plus Phase A. The field-test/bug-fix loop remains side-car unless a confirmed
P0/P1, security/data-integrity, or architecture blocker exists. No active P0/P1
issue in this backlog blocks starting `PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION`.

Current A0.1 product-rule refinement (2026-08-13): Owner review accepted the A0
`menu_item_options` Size engine but rejected free-form Size editing. The new
contract requires system-controlled Small/Regular/Large and Store-level
Size/Combo pricing policy. The Owner approved the additive Store pricing policy
schema direction:
`PHASE_A0_1_PRICING_POLICY_SCHEMA_CHANGE_APPROVAL`. This remains a
productization package, not a Production incident. Production stays
no-mutation; A0.1 Staging V10->V11 validation passed and the Owner accepted the
manual Pricing UX retest.

Current A0.2 productization package (2026-08-13): Owner approved
`PHASE_A0_2_STORE_COMBO_CONFIGURATION` to implement Store-level Combo Contents
configuration. This is not an incident and not a Production action. It must
preserve A0.1 Pricing Rules as the Combo price source, keep item
`COMBO_ALLOWED` in `menu_item_options`, and may add only bounded additive
schema through `store_combo_components` for Store-scoped `COMBO_EGG` /
`COMBO_SIDE` content configuration. It merged through PR #134, deployed to
exact-SHA Staging `90ac0cb0496161b12c47cff00573b56b4abc961c` at Flyway V12,
passed automated A0.2 validation, and the Owner accepted manual Staging retest
with `OWNER_A0_2_MANUAL_STAGING_RETEST = PASS`. Historical A0.2 closure:
`PHASE_A0_2_STORE_COMBO_CONFIGURATION_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST`.
Current Phase A authority continues through A1 -> A2 -> A3 and stops before A4.

Current A1 Module Catalog package records the Reporting-vs-Analytics drift as a
compatibility boundary: `REPORTING_CORE` is required for normal Stores, while
`ANALYTICS_ADVANCED` is optional. The current `ANALYTICS` feature flag still
covers both and is classified as an environment capability until A3/A6/A7 split
Store module state and runtime gates.

A1 is accepted and merged (`PHASE_A1_MODULE_CATALOG = PASS`, PR #137,
`34169152c6d48ecf503b441fe7428416c399d0a9`). A2 is accepted and merged
(`PHASE_A2_MODULE_DEPENDENCY_GRAPH = PASS`, PR #138,
`1780c8934a502709844713d91c493b076e714983`). A2 records fail-closed dependency
validation as a bounded repository/backend package. Unknown modules, invalid
graph entries, missing required dependencies, missing environment or hardware
capabilities and conflicts are validator outcomes, not runtime issues or Owner
gates by themselves.

Current A3 Store-level Module Configuration adds additive `store_modules`
persistence and Store module read/config contracts. It intentionally retains
legacy runtime gating until A6/A7, so the Reporting-vs-Analytics split and
full backend/frontend gating migration remain productization backlog
boundaries, not active incidents. A3 is deployed and validated on exact-SHA
Staging `c1b5e7681f24a11fbf99293567b3da08076fa3b6` at Flyway V13 after the
bounded runtime DI repair PR #140. A3 acceptance and core regression smoke
passed; Production remained no-mutation. There is no active Known Issue blocking
the A3 stop state.

Current A4 Store Profile Contract is productization work, not a Production
incident, and is in `main` through PR #142 at
`be14923c96098d80b1b841e2ba0edbe3ca2563a5`. A5 St-Denis Canonical Profile is
also productization work, not an incident. It adds V15 safe profile seed data
and a dry-run validator for `ST_DENIS_CANONICAL_PROFILE/v1`; it does not create
Stores, provision auth material, copy Production/St-Denis runtime secrets or
DB IDs, bind printers/devices, start Phase B/C or mutate Production. PR #143
entered `main`, but the first exact-SHA Staging deploy failed closed before
V15 history because V15 profile seed JSON literals started with a newline that
violated the A4 `content_json` check. PR #145 repaired that seed literal shape
and exact-SHA Staging then applied V15 successfully, but backend startup failed
closed on Hibernate schema validation because the A4 `fingerprint_sha256
char(64)` columns were still mapped by JPA as default `varchar(255)`. This is
tracked as bounded A5 dependency repair, not a Production incident. PR #146
added explicit `char(64)` DDL metadata; the current follow-up repair adds
explicit JDBC `Types#CHAR` metadata and regression coverage only. PR #147
entered `main` at `3440fddad7571409c66189e44976658921e5de1f`; exact-SHA
Staging deploy passed Flyway V15 and Profile runtime validation. Production
remains no-mutation.

Current A5.5 Menu Management Configurability Closure (2026-08-14): this is
Owner-requested Phase A productization work, not a Production incident. It
addresses the known configurability gap that A0.2 still had hardcoded
`COMBO_EGG`/`COMBO_SIDE` first-catalog assumptions, and adds Owner controls for
Store-scoped Combo Groups/Components, Categories, and Stations. The former
A5.5 UML baseline is deferred to A5.6. No active P0/P1 issue blocks this A5.5
package. Production remains no-mutation; exact-SHA Staging deployment and
automated validation are required after merge before Owner manual retest.

Current final productization route (2026-08-12): open field-test follow-up work
continues through the Owner field-test and bug-fix loop, but the loop is now a
side-car to product development unless a confirmed P0/P1 blocker exists. The
three-phase productization roadmap is tracked in
[FINAL_PRODUCTIZATION_THREE_PHASE_ROADMAP_AUDIT](agile/FINAL_PRODUCTIZATION_THREE_PHASE_ROADMAP_AUDIT.md)
and currently waits for the Owner's 30 Phase A/B/C answers. No new runtime issue
or runtime mutation is introduced by that planning audit.

Current Production deployment update (2026-08-12): KI-014's three repository
repairs are `DEPLOYED_TO_PRODUCTION` through exact RC
`RC-THREE-RELIABILITY-20260812-3EC4D88` / application SHA `3ec4d88...`.
Production health, Flyway V10/no pending, configuration preservation,
application-only rollback compatibility, fresh backup, isolated restore and
bounded observation passed. The issue is not yet `OWNER_FIELD_VERIFIED`;
Owner must still retest physical Pad sleep behavior, menu revision field
behavior and real printing latency in Production.

Current Production-promotion tooling dependency (2026-08-12): the
historical same-round three-reliability Production promotion loop found a repository/tooling-only
blocker before deployment. Exact-artifact promotion must distinguish previous
running application SHA from retained Production control checkout SHA, and V10
backup rehearsal must validate a V10 Flyway ledger. The bounded repair is not a
runtime action and does not mark the three reliability repairs
`OWNER_FIELD_VERIFIED`.

Current Owner field-test repair batch (2026-08-11): the Owner-authorized
`STAGING_THREE_RELIABILITY_REPAIR_BATCH` completed through PR #122 and exact
Staging deploy at `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`. Repository and
Staging smoke passed for screen-off Pad pre-output claim blocking, long-lived
menu snapshot revision invalidation plus visible click-lock UX, and accidental
global printing outbox serialization. Production remained out of scope.

Production-promotion closure (2026-08-11): exact frozen RC
`RC-ST-DENIS-20260811-2661EB76` is deployed to existing Production St-Denis at
Flyway V10. Migration, backup/integrity/isolated restore, rollback compatibility,
second-start/no-pending, health and continuity gates passed. No P0/P1 remains
open from the bounded observation. A one-shot post-nginx readiness check caused
a fail-closed false-negative during startup; the runtime was healthy on the
immediate bounded retry, and a minimal repository repair adds bounded polling
for future promotions. It did not change the frozen RC or deployed runtime.
Current unique stop:
`PRODUCTION_EXACT_RC_PROMOTED_POST_DEPLOY_OBSERVATION_PASS`.

Historical Production-promotion preparation update (2026-08-11): the Owner's fresh exact-candidate
acceptance attestation closes KI-013 as a release blocker for
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab`. The former relative Production
state-root, mutable rebuild, combined start and unproven backup-tooling gaps are
addressed by the later bounded dependency repair, Agent 6 review, merge and
promotion-gate validation. The exact RC was subsequently promoted through the
current Production-promotion evidence; this paragraph is retained only as a
superseded checkpoint.
Historical stop (superseded):
`RC_PREPARED_WAITING_FOR_MANDATORY_PROMOTION_GATES`.

Historical superseded field-test bug-repair override (2026-08-11): a bounded
`OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` repair is in progress for Owner-confirmed
printing display issues, PAD_DIRECT lifecycle reliability, and audit-only queue
latency documentation. Repository repair is locally verified after resolving an
Agent 6 lifecycle-safety block. Agent 6 returned `ACCEPT`; PR #117 entered
`main`, exact Staging
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab` passed automated MOCK smoke, and
the package then waited for Owner retest; the fresh exact-RC Owner acceptance
supersedes that wait.

Current field-test override (2026-08-11): bounded issue `KI-012` is resolved.
The generic allowlist/endpoint-policy repair entered `main` through PR #114,
and exact Staging `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` / V10 now runs
server-side MOCK with the complete pipeline and zero physical transport.

Current TWIN-001 closure override (2026-08-10): KI-011's reconstruction and
automated-validation portion is resolved. Exact Staging
`2661eb76c36dd9aa58db94ceacd278242ef4c9ab` / V10 retains complete manifest-v2
parity, independent Staff credentials, safe automated workflow smoke and zero
blocking behavior difference. The Owner field-test loop is active at
`HISTORICAL_OWNER_FIELD_TEST_PRINTING_FIXES_DEPLOYED_WAITING_FOR_OWNER_RETEST`, now superseded.

Historical KI-011 dependency-repair override (2026-08-10): the manifest-v2
projector and secret-FD staff reconciler are repository verified. Fresh
read-only planning accepts the exact synthetic baseline with zero deletes.
Runtime parity is still pending merge and approved Staging execution; this is
not yet a Twin PASS.

> Status: `ACTIVE_GOVERNANCE_BACKLOG`
>
> Last updated: 2026-08-11, America/Toronto
>
> This is the authority for current issue triage. Historical evidence remains
> in the Phase 3 reports and is not rewritten here.

Historical TWIN-001 stop:
`TWIN-001_STAGING_RECONSTRUCTION_APPROVED_DEPENDENCY_REPAIR_IN_PROGRESS`.
The reconstruction approval is active; the next TRUE OWNER GATE after
operational readiness is the Owner field-test loop. Manifest v2 is
deterministic and schema-valid. The V7-to-V10 forward migration path passed
local PostgreSQL 16.14 verification; the raw delta is
`CURRENT_PRODUCTION_VERSION_DIFFERENCE`, while aggregate
`SCHEMA = BLOCKING_BEHAVIOR_DIFFERENCE` remains pending an actual reconstructed
V10 Twin.

## Priority definitions

| Priority | Meaning |
|---|---|
| P0 | Production is fully unavailable, data is being destroyed/corrupted, or there is a security incident. |
| P1 | A core on-site function is interrupted and cannot be safely recovered during service. |
| P2 | A business-rule defect or local functional exception that needs a workaround. |
| P3 | A UX, process, or governance improvement. |

## Active issues

### KI-015 - Printing display rules are hidden in code rather than Store configuration

| Field | Value |
|---|---|
| issue_id | `KI-015` |
| priority | `P2` productization blocker for Phase B; no Production incident asserted |
| title | Printing display rules are not transparent or configurable by Store/Profile |
| observed_behavior | GRAB, FRONTDESK_RECEIPT and HOT_KITCHEN display wording depends on many renderer/order-service hardcodes: product aliases, Size/noodle/spicy abbreviations, add/remove modifier wording, combo wording, quantity formatting, label cleanup and legacy string matching. Owner cannot inspect or edit these rules in Menu Management or Printing Settings. |
| expected_behavior | Canonical Printing Rule Configuration is Store-scoped, profile-compatible, versionable/snapshot-safe, reusable by Phase B provisioning and independent after Store materialization. It separates display rules from routing, PrintJob state, printer assignment, payment and authorization. |
| operational_impact | Phase B Create New Store remains blocked until the A11 implementation candidate passes PR, exact-SHA Staging validation and Owner acceptance. |
| current_workaround | Repository candidate adds Store-owned display rule configuration with menu/order snapshot fallback; runtime remains unchanged until Staging deploy. |
| evidence | [PHASE_A11_PRINTING_RULE_AUDIT](agile/PHASE_A11_PRINTING_RULE_AUDIT.md), [PRINTING_RULE_RECONCILIATION_MATRIX](agile/PRINTING_RULE_RECONCILIATION_MATRIX.md), [PRODUCT_PRINT_RULE_INVENTORY](agile/PRODUCT_PRINT_RULE_INVENTORY.md), [PHASE_A11_PRINTING_RULE_CONFIGURATION_IMPLEMENTATION_EVIDENCE](agile/PHASE_A11_PRINTING_RULE_CONFIGURATION_IMPLEMENTATION_EVIDENCE.md). |
| status | `A11_IMPLEMENTATION_REPOSITORY_CANDIDATE_READY_WAITING_FOR_PR_AND_STAGING_VALIDATION` |
| target_loop | `PHASE_A11_PRINTING_RULE_TRANSPARENCY_AND_CONFIGURATION` |
| safety_boundary | Additive V17 repository candidate only until deployment. No Production mutation, Production deploy, physical printer binding, Pad pairing, Phase B/C, Chinatown or Sainte-Catherine. |
| last_updated | 2026-08-15 |

### KI-014 - Owner field-test three reliability follow-up repairs

| Field | Value |
|---|---|
| issue_id | `KI-014` |
| priority | `P1` for Pad sleep print blocking; `P2` for menu revision/click-lock and bounded printing latency |
| title | Pad sleep print blocking, menu revision/click-lock, and accidental global printing latency |
| observed_behavior | Owner field testing and investigation identified a background/screen-off Pad holding a pre-output print claim long enough to block service, long-lived Pad menu snapshots that can remain on revision N without a visible refresh trigger, click handlers that silently return while a draft is locked, and automatic outbox dispatch that serializes unrelated printers in one scheduler loop. |
| expected_behavior | An inactive Pad must not indefinitely hold a not-yet-started print job; ambiguous physical `PRINTING` state must not be blindly reprinted; Pad menu cache must remain offline-first but revision-aware; locked drafts must show clear disabled state; unrelated printers should dispatch concurrently while same-printer FIFO remains preserved. |
| operational_impact | Staging Owner retest should cover physical Pad sleep behavior after repository deploy. Repository tests proved lifecycle policy, leases, cache atomicity, click-lock UX and bounded keyed scheduling; real device screen-off remains `OWNER_PHYSICAL_PAD_RETEST_REQUIRED`. |
| current_workaround | Wake the sleeping Pad to release queued output; refresh/reopen Pad browser after Menu Management changes; avoid submitting new print-heavy orders while one printer path is slow. |
| evidence | [three-reliability repair batch evidence](runtime/STAGING_THREE_RELIABILITY_REPAIR_BATCH_EVIDENCE.md). |
| status | `DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST` |
| target_loop | `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` |
| safety_boundary | Staging/repository-only; no Production mutation, deploy, restart, Flyway, configuration change, printer contact, credential copy, schema change, physical binding, Pad pairing, Chinatown or modularization. |
| last_updated | 2026-08-11 |

### KI-013 - Owner field-test printing display and PAD_DIRECT lifecycle bugs

| Field | Value |
|---|---|
| issue_id | `KI-013` |
| priority | `P1` for PAD_DIRECT reliability, `P2` for receipt display; Staging/field-test scope |
| title | Owner field-test printing display and Pad lifecycle defects |
| observed_behavior | Historical field defects covered GRAB/Frontdesk display and inactive/screen-off Pad lifecycle behavior. The bounded repair deployed to exact Staging `2661eb76...`, passed MOCK smoke, and the Owner's exact-candidate acceptance closed the retest gate. |
| expected_behavior | GRAB keeps `走上海青`, chicken cold noodle hides thin and appends `韭` for leek leaf, Frontdesk prints each noodle/combo serving separately, GRAB fried quantity uses `×`, in-flight Pad jobs complete/fail without lifecycle generation loss, and queue latency is documented without behavior change. |
| operational_impact | Resolved for the exact accepted candidate. Physical printer binding and Pad pairing remain separate runtime gates, not unresolved KI-013 behavior. |
| current_workaround | None for the repaired exact candidate. |
| evidence | [Owner field-test printing fixes evidence](runtime/OWNER_FIELD_TEST_PRINTING_FIXES_EVIDENCE.md). |
| status | `RESOLVED_OWNER_ACCEPTED_FOR_EXACT_RC` |
| target_loop | Historical `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP`; completed for exact `2661eb76...`. |
| safety_boundary | The repair did not authorize real printer binding, Pad pairing, secrets, destructive reset, Chinatown or modularization. Its accepted artifact was later promoted only through the separately frozen Production RC. |
| last_updated | 2026-08-11 |

### KI-012 - Staging server MOCK lacks a fail-closed runtime mode ceiling

| Field | Value |
|---|---|
| issue_id | `KI-012` |
| priority | `P2` field-test enablement; no Production incident |
| title | Staging server MOCK lacks a fail-closed runtime mode ceiling |
| observed_behavior | Deployment tooling permits server Staging only as `DISABLED/false`; if the Printing feature alone is enabled, the existing Store API accepts `REAL` and `PAD_DIRECT` as well as `MOCK`. |
| expected_behavior | An environment-neutral application policy constrains permitted modes; Staging allows exactly `DISABLED,MOCK`, rejects printer endpoint writes, and MOCK still executes the normal renderer/job/dispatch pipeline without transport. |
| operational_impact | Owner cannot safely inspect real Print Center behavior or synthetic tickets in the Operational Twin. |
| current_workaround | None for MOCK field testing. Physical binding remains separately gated. |
| evidence | [runtime-policy repair evidence](runtime/STAGING_MOCK_PRINTING_RUNTIME_POLICY_REPAIR_EVIDENCE.md) and [field-test evidence](runtime/STAGING_MOCK_PRINTING_FIELD_TEST_EVIDENCE.md). |
| status | `RESOLVED_STAGING_MOCK_VERIFIED` |
| target_loop | `STAGING_MOCK_PRINTING_FIELD_TEST_ENABLEMENT` |
| safety_boundary | Staging only; no Production mutation, real endpoint, socket contact, Pad pairing, migration or destructive reset. |
| last_updated | 2026-08-11 |

### KI-011 - Production-like St-Denis Twin parity and automated smoke

| Field | Value |
|---|---|
| issue_id | `KI-011` |
| priority | `P2` governance/product readiness |
| title | Staging must become a long-lived Production-like St-Denis Operational Twin |
| observed_behavior | Exact Staging `2661eb76...` / V10 retains manifest-v2 parity and independent Staff credentials, passed safe workflow/MOCK/field-test smoke with zero blocking behavior difference, received exact-candidate Owner acceptance, and became the frozen Production RC. Physical hardware remains separately gated. |
| expected_behavior | Staging reconstructs safe St-Denis operational configuration through shared application code and generic Store logic, with every parity domain classified `MATCH`, `EXPECTED_DIFFERENCE`, `BLOCKING_DIFFERENCE`, or `NOT_YET_VERIFIED`. |
| operational_impact | Resolved for St-Denis reconstruction, automated/Owner acceptance and the exact-RC Production promotion. Chinatown and hardware routes remain independent future decisions. |
| current_workaround | Preserve exact Staging V10 Twin state; no workaround or active field-test loop remains. |
| evidence | [manifest v2](runtime/ST_DENIS_TWIN_PARITY_MANIFEST_V2.json), [mapping](runtime/V7_PRODUCTION_TO_V10_TWIN_CONFIGURATION_MAPPING.md), [completion evidence](runtime/TWIN-001_MANIFEST_V2_COMPLETION_EVIDENCE.md), [operational Twin evidence](runtime/TWIN-001_ST_DENIS_OPERATIONAL_TWIN_EVIDENCE.md), and [MOCK field-test evidence](runtime/STAGING_MOCK_PRINTING_FIELD_TEST_EVIDENCE.md). |
| status | `RESOLVED_TWIN_OWNER_ACCEPTED_EXACT_RC_PROMOTED` |
| next_gate | None for KI-011; physical printer binding remains a separate runtime gate and Chinatown remains separately deferred. |
| safety_boundary | No raw customer/order/payment data, credentials, secrets, production printer/device endpoints, `SELECT *`, or complete database dump. |
| last_updated | 2026-08-11 |

### KI-010 - Browser login rejected by proxy same-origin contract

| Field | Value |
|---|---|
| issue_id | `KI-010` |
| priority | `P1` for Staging acceptance; no Production incident observed |
| title | SSH-tunneled browser login returns CORS HTTP 403 before authentication |
| observed_behavior | Manual Chrome acceptance against `http://127.0.0.1:18080` reached `POST /api/v1/auth/login` and received `403 Invalid CORS request`; no principal, role, Organization, Store, or dashboard request was reached. The API-only acceptance client had no browser Origin and therefore passed. |
| expected_behavior | nginx preserves the browser-visible Host and explicit tunnel port so Spring recognizes the request as same-origin; login then proceeds through the existing generic authentication and Organization/Store authorization contracts. |
| operational_impact | Automated Phase-A API and browser-equivalent acceptance pass; the former manual closure is preserved as evidence and deferred by the Owner's TWIN-001 priority. |
| current_workaround | None required. Do not weaken CORS or add a Store/user-specific allowlist. |
| evidence | [STG-009 browser-login 403 repair evidence](runtime/STG-009_PHASE_A_BROWSER_LOGIN_403_REPAIR_EVIDENCE.md) and [browser-equivalent acceptance evidence](runtime/STG-009_PHASE_A_BROWSER_EQUIVALENT_ACCEPTANCE_EVIDENCE.md). |
| status | `REPAIR_DEPLOYED_BROWSER_EQUIVALENT_PASS_DEFERRED_BY_OWNER_TWIN_PRIORITY` |
| target_loop | `TWIN-001_ST_DENIS_STAGING_TWIN`; the browser-equivalent repair is retained as historical foundation and no new runtime repair is authorized by this issue entry. |
| acceptance_criteria | Proxy regression and repository checks pass; independent review accepts; fresh exact-SHA Staging deploy proves browser-equivalent login/session/redirect/Owner shell/Organization/source-Store/dashboard/refresh/logout without 401/403; the exposed synthetic credential is privately rotated; Owner manual browser evidence passes. |
| deployment_required | Historical Staging-only exact redeploy completed; no new deployment is authorized by this issue entry. |
| last_updated | 2026-08-10 |

### KI-009 - Non-web STG-005 one-shot retains WebSocket broker lifecycle

| Field | Value |
|---|---|
| issue_id | `KI-009` |
| priority | `P2` |
| title | Non-web STG-005 one-shot does not exit after validated command completion |
| observed_behavior | Exact Staging `2a6c30a...` reached `STG005_BOOTSTRAP|status=VALIDATED` before credential or data access, but the non-web profile also started `SimpleBrokerMessageHandler`; the JVM remained alive until the reviewed 600-second timeout. Compose `--rm` cleanup and the launcher finalizer then overlapped, preserving fail-closed blocked state. |
| expected_behavior | The dedicated non-web one-shot excludes long-lived WebSocket infrastructure while preserving the normal web runtime contract. A successful password-free plan exits inside its bounded window; unexpected cleanup failure remains fail-closed. |
| operational_impact | Historical STG-008 progress stopped before synthetic business writes; the reviewed pair was later recovered and the current synthetic baseline is complete. No replay is authorized by this issue entry. |
| current_workaround | Historical recovery and lifecycle repairs are retained as evidence. Do not replay the old one-shot or infer current runtime authority from its former exact SHA; the current route is the Owner-prioritized TWIN-001 governance plan. |
| evidence | [STG-008 one-shot lifecycle repair evidence](runtime/STG-008_ONE_SHOT_LIFECYCLE_REPAIR_EVIDENCE.md). |
| authoritative_rule | [AL-003S Staging acceptance runbook](../../deployment/cloud/README_AL003S_STAGING_ACCEPTANCE.md). |
| status | `HISTORICAL_REPAIR_RESOLVED_DEFERRED_BY_OWNER_ST_DENIS_TWIN_PRIORITY` |
| target_loop | `TWIN-001_ST_DENIS_STAGING_TWIN`; no STG-008 replay is authorized by this backlog entry. |
| acceptance_criteria | Focused non-web lifecycle and shell safety regressions plus independent review passed; PR #91 entered `main` and later exact runtime evidence is retained. No new rebind, recovery or PLAN is authorized by this issue entry. |
| deployment_required | Historical Staging-only exact redeploy completed; no new deployment is authorized by this backlog entry. |
| last_updated | 2026-08-09 |

### KI-008 - Non-web STG-005 plan cannot construct servlet request context

| Field | Value |
|---|---|
| issue_id | `KI-008` |
| priority | `P2` |
| title | Non-web STG-005 plan cannot construct servlet request context |
| observed_behavior | The approved password-free `bootstrap-plan` exits during Spring context initialization because `RequestUserContextService` requires `HttpServletRequest` while the guarded one-shot is non-web. |
| expected_behavior | The non-web guarded command constructs without inventing a request; any request-authentication attempt remains fail-closed. |
| operational_impact | Historical blocker resolved: exact `2a6c30a...` constructed and reached `VALIDATED` before credential/data access. The separate lifecycle blocker is tracked as `KI-009`. |
| current_workaround | None; do not reuse old evidence. The former STG-008 continuation is historical and deferred behind the Owner-prioritized TWIN-001 route. |
| evidence | [STG-008 non-web request-context repair evidence](runtime/STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md). |
| authoritative_rule | [AL-003S Staging acceptance runbook](../../deployment/cloud/README_AL003S_STAGING_ACCEPTANCE.md). |
| status | `RESOLVED_BY_EXACT_RUNTIME_VALIDATION` |
| target_loop | `TWIN-001_ST_DENIS_STAGING_TWIN`; no STG-008 replay is authorized by this backlog entry. |
| acceptance_criteria | Focused authorization/non-web safety regressions and independent review passed; PR #89 entered `main`; exact `2a6c30a...` passed rebind/deploy/readiness/recovery and emitted password-free `VALIDATED`. |
| deployment_required | Completed by the exact `2a6c30a...` Staging-only deploy; no Production deployment. |
| last_updated | 2026-08-09 |

### KI-002 - 走上海青被错误压缩为走青

| Field | Value |
|---|---|
| issue_id | `KI-002` |
| priority | `P2` |
| title | 走上海青被错误压缩为走青 |
| observed_behavior | A GRAB/display shorthand compresses the specific removal `走上海青` into the broader phrase `走青`. |
| expected_behavior | Print/display `走上海青`; do not apply the `走葱` + `走香菜` to `走青` compression to this distinct vegetable removal. |
| operational_impact | Kitchen staff can interpret the ticket as removing all greens rather than only Shanghai bok choy. |
| current_workaround | Staff manually clarify the ticket before production when it is observed. |
| evidence | Operator-reported field rule; code-level reproduction and regression test are pending. |
| authoritative_rule | [FRONTDESK_GRAB_ITEM_NAME_RULES.md](../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md), with this issue as the required correction. |
| status | `OPEN` |
| target_loop | Future display-rule bug loop, unassigned. |
| acceptance_criteria | The exact option renders as `走上海青`; only the jointly selected green-onion and cilantro removals compress to `走青`; automated renderer coverage is added. |
| deployment_required | Yes, backend receipt-renderer deployment. |
| last_updated | 2026-07-27 |

### KI-003 - 鸡丝凉面默认细面错误显示为鸡凉细

| Field | Value |
|---|---|
| issue_id | `KI-003` |
| priority | `P2` |
| title | 鸡丝凉面默认细面错误显示为鸡凉细 |
| observed_behavior | The default thin noodle selection is shown in the kitchen shorthand for chicken cold noodles. |
| expected_behavior | Default thin noodles render as `鸡凉`, without the extra `细` suffix. |
| operational_impact | Kitchen tickets contain redundant shorthand and weaken the agreed production naming convention. |
| current_workaround | Kitchen staff treat `鸡凉细` as the default chicken cold noodle. |
| evidence | Operator-reported field rule. Migration V5 establishes thin-noodle ordering, but it does not prove the desired renderer omission. |
| authoritative_rule | [FRONTDESK_GRAB_ITEM_NAME_RULES.md](../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md), with this issue as the required correction. |
| status | `OPEN` |
| target_loop | Future display-rule bug loop, unassigned. |
| acceptance_criteria | Default thin chicken cold noodle renders `鸡凉`; non-default noodle types retain their explicit suffixes; renderer tests cover both cases. |
| deployment_required | Yes, backend receipt-renderer deployment. |
| last_updated | 2026-07-27 |

### KI-004 - 鸡丝凉面韭页面型显示错误

| Field | Value |
|---|---|
| issue_id | `KI-004` |
| priority | `P2` |
| title | 鸡丝凉面韭页面型显示错误 |
| observed_behavior | Chicken cold noodles with leek-leaf noodles do not render using the agreed shorthand. |
| expected_behavior | Render `鸡凉韭`. |
| operational_impact | Kitchen staff must infer a non-default noodle type from an incorrect or unclear label. |
| current_workaround | Staff clarify the noodle type verbally when needed. |
| evidence | Operator-reported field rule; no code-level regression test has yet been recorded. |
| authoritative_rule | [FRONTDESK_GRAB_ITEM_NAME_RULES.md](../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md), with this issue as the required correction. |
| status | `OPEN` |
| target_loop | Future display-rule bug loop, unassigned. |
| acceptance_criteria | A chicken cold noodle with the stable leek-leaf option renders `鸡凉韭`; default thin still renders `鸡凉`; unrelated noodle SKU rules do not regress. |
| deployment_required | Yes, backend receipt-renderer deployment. |
| last_updated | 2026-07-27 |

### KI-005 - 数据库恢复演练（已完成隔离验证）

| Field | Value |
|---|---|
| issue_id | `KI-005` |
| priority | `P2` |
| title | 数据库恢复演练与可恢复性证据 |
| observed_behavior | A fresh V7 pre-deploy backup passed custom-format integrity and restored successfully only into a network-none, tmpfs, resource-limited disposable PostgreSQL container with an exact V1-V7 ledger. |
| expected_behavior | A separately approved, non-production restore rehearsal has a documented result, timing, recovery-point/time expectations, and follow-up actions; backup integrity is independently verified. |
| operational_impact | Recoverability was demonstrated for the fresh pre-deploy archive. Production restore remains a destructive Owner gate and no RTO claim is inferred. |
| current_workaround | None; preserve the verified archive and the separate restore authority boundary. |
| evidence | [Production backup/restore rehearsal evidence](runtime/PRODUCTION_ST_DENIS_BACKUP_RESTORE_REHEARSAL_EVIDENCE.md). |
| authoritative_rule | [RUNTIME_VERIFICATION_CHECKLIST.md](RUNTIME_VERIFICATION_CHECKLIST.md). |
| status | `RESOLVED_ISOLATED_RESTORE_PASS` |
| target_loop | Completed exact-RC recovery gate; a real Production restore is still separately Owner-gated. |
| acceptance_criteria | An owner-approved isolated restore rehearsal succeeds without Production data mutation; backup integrity, recovery boundaries and scope/limitations are recorded without secrets. |
| deployment_required | No; the approved isolated exercise completed without Production mutation. |
| last_updated | 2026-08-11 |

### KI-006 - 正式生产批准和发布记录（已建立）

| Field | Value |
|---|---|
| issue_id | `KI-006` |
| priority | `P3` |
| title | 正式生产批准和发布记录 |
| observed_behavior | Frozen `RC-ST-DENIS-20260811-2661EB76` binds exact source/images/migrations/acceptance/backup/rollback evidence and the Owner-authorized Production result. |
| expected_behavior | Each production deployment has an immutable RC identity, Owner approval, release/PR reference, exact source/artifact digests, deployed commit, migration statement, parity/acceptance results, and rollback reference. |
| operational_impact | Resolved for the exact St-Denis RC; future releases require new immutable records. |
| current_workaround | None. |
| evidence | [frozen RC](runtime/RC_ST_DENIS_20260811_2661EB76.json) and [Production promotion evidence](runtime/PRODUCTION_ST_DENIS_EXACT_RC_PROMOTION_EVIDENCE.md). |
| authoritative_rule | [AGILE_LOOP_OPERATING_MODEL.md](AGILE_LOOP_OPERATING_MODEL.md). |
| status | `RESOLVED_EXACT_RC_RECORD_ESTABLISHED` |
| target_loop | Completed for `RC-ST-DENIS-20260811-2661EB76`. |
| acceptance_criteria | A lightweight owner-approved RC record freezes exact source/artifact identities after Twin/automated/Owner acceptance, proves same-artifact promotion, records compatibility-gated rollback/roll-forward and backup readiness, and contains no secrets or customer data. |
| deployment_required | Completed as part of the separately authorized exact-RC batch. |
| last_updated | 2026-08-11 |

### KI-007 - 单门店员工的 Android Device Pairing 流程过于复杂

| Field | Value |
|---|---|
| issue_id | `KI-007` |
| priority | `P3` |
| title | 单门店员工的 Android Device Pairing 流程过于复杂 |
| observed_behavior | A user logs in with a single-store account and must still understand and complete a separate Pad Direct pairing action. |
| expected_behavior | After explicit authorization, an employee with exactly one accessible Store may use a guided automatic registration flow bound only to that Store. |
| operational_impact | Pairing errors delay on-site printing setup and make store binding harder to understand. |
| current_workaround | Use the explicit Print Center/Android Control Panel pairing workflow and verify Store ID before use. |
| evidence | Current pairing architecture in `StoreDeviceController`, `StoreDeviceServiceImpl`, Web Print Center, and Android `RestaurantPadDevice` bridge. No approved auto-pair requirement exists yet. |
| authoritative_rule | [AL-001 technical plan](agile/AL-001_OWNER_STORE_ONBOARDING_CHINATOWN_TECHNICAL_PLAN.md). |
| status | `REQUIREMENTS_PENDING` |
| target_loop | Future single-store auto-pairing loop; **not** the first AL-001 implementation batch. |
| acceptance_criteria | A separately approved design proves explicit consent, single-store binding, no token exposure, and no cross-store registration path. |
| deployment_required | Yes, when implemented. |
| last_updated | 2026-07-27 |

## Closed / historical issues

### KI-001 - Orders stale JavaScript chunk / Android WebView blank page

| Field | Value |
|---|---|
| issue_id | `KI-001` |
| priority | Historical P1 |
| title | Orders stale JavaScript chunk / Android WebView blank page |
| observed_behavior | The Orders page previously requested an older JavaScript chunk, rendered a blank page, and could not be exited normally. Clearing Android WebView/App cache recovered the field incident. |
| expected_behavior | Orders loads after deployment without a stale-chunk blank page. |
| operational_impact | The page was unavailable on the affected Pad until cache recovery. |
| current_workaround | Historical recovery was clearing Android WebView/App cache. It is not an ongoing operational requirement. |
| evidence | Responsible owner confirms a code-level repair and field recovery. Historical records retain `INCIDENT_RECOVERED_ROOT_CAUSE_PENDING`; no automatic test/deployment proof is added by this backlog entry. |
| authoritative_rule | Historical Phase 3 reports and future regression coverage for the repaired path. |
| status | `CLOSED_OPERATOR_CONFIRMED` |
| target_loop | Not a current Agile Loop target. |
| acceptance_criteria | Closed by responsible-owner confirmation of code repair and field recovery; future evidence may add, but does not retroactively imply, machine verification. |
| deployment_required | Completed historically according to owner confirmation. |
| last_updated | 2026-07-27 |

`KI-001` is not marked `MACHINE_VERIFIED`. Do not reopen it merely because the
historical cache-clear recovery remains documented; reopen only with a new
reproduced or field-observed incident.
