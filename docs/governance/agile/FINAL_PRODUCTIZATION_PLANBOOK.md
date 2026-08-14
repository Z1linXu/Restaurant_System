# Final Productization Planbook

> Status: `FINAL_PRODUCTIZATION_PLANBOOK_READY_PHASE_A_STARTED`
>
> Prepared: 2026-08-13, America/Toronto
>
> Fresh repository authority at package start:
> `origin/main@4f98ad1db74da752cbd9db71db2e983f45a06dba`
>
> Supersedes:
> [FINAL_PRODUCTIZATION_THREE_PHASE_ROADMAP_AUDIT](FINAL_PRODUCTIZATION_THREE_PHASE_ROADMAP_AUDIT.md)
> stop `FINAL_PRODUCTIZATION_AUDIT_COMPLETE_WAITING_FOR_OWNER_30_ANSWERS`.
>
> Runtime effect of this planbook package: repository governance only. It does
> not deploy, restart, migrate, create a Store, mutate Production/Staging data,
> bind printers, pair Pads, or change credentials.

## 1. Authority closure

The Owner closed the 30-answer productization gate on 2026-08-13 and authorized:

```text
FINAL_PRODUCTIZATION_PLANBOOK
+
BEGIN_PHASE_A_MODULAR_PRODUCTIZATION
```

The accepted product rule is:

```text
BUILD ONCE, CONFIGURE MANY
```

Future Stores must not be supported by copied projects, copied databases,
per-Store branches, manual database clones, or shared-code business logic such
as `if store == Chinatown`, `if store == StDenis`, or
`if store == SainteCatherine`.

The active roadmap is exactly:

```text
PHASE A — MODULAR PRODUCTIZATION
↓
PHASE B — OWNER NEW STORE PROVISIONING
↓
PHASE C — REAL MULTI-STORE PROOF
```

The Owner field-test bug-fix loop remains continuous side work. Ordinary bugs
do not block this roadmap unless they are confirmed P0/P1, security/data-
integrity blockers, or architecture blockers.

## 2. Current runtime authority

Current retained Production and Staging application artifact:

```text
3ec4d88a47f68e05b92d9246bfd63af2d1f297f9
```

The three reliability repairs are `DEPLOYED_TO_PRODUCTION` through
`RC-THREE-RELIABILITY-20260812-3EC4D88`, but remain pending Owner field retest.
That field retest is side-loop evidence and is not a blocker for Phase A while
no P0/P1 production blocker is active.

Phase A has this runtime boundary:

```text
Production = NO MUTATION
Staging = allowed only when the selected Phase A loop explicitly requires
          exact-SHA deployment/validation
```

No Production deploy, restart, Flyway action, configuration change, menu change,
printer/device action, Store creation, credential action, business-data
mutation, rollback, restore, Chinatown creation, or Sainte-Catherine creation is
authorized by this Planbook.

## 3. Owner final decisions

### Phase A decisions

| Gate | Owner decision | Technical implementation | Migration / compatibility notes |
|---|---|---|---|
| A-Q1 Minimum modules | Normal Stores require `ORDERING/POS`, `MENU`, `MENU_MANAGEMENT`, `TABLE_MANAGEMENT`, `PRINTING`, `GRAB_PRINTING`, `FRONTDESK_RECEIPT`, `ORDER_HISTORY`, and `REPORTING`. | Phase A module naming may normalize these into canonical modules/capabilities, but every listed business capability remains required for a normal Lanzhou Store. | Existing code has feature pages and domains rather than a full module catalog; Phase A must map current routes/APIs without changing the product requirement. |
| A-Q2 KDS | `KDS = OPTIONAL / DISABLED BY DEFAULT`. | KDS-off must be a valid Store configuration and must not block activation. | Existing KDS code is preserved for future enablement. |
| A-Q3 Reports | `REPORTING = CORE_REQUIRED`. | Split basic business Reports from advanced Analytics if current architecture supports that distinction. | Basic reports must be core; advanced analytics may be optional only after audit. |
| A-Q4 St-Denis profile | Define `ST_DENIS_CANONICAL_PROFILE` as a safe versioned Store Profile/template, not a Production clone. | It expresses modules, menu template reference, tables, stations, logical printing, settings, role defaults, device capability requirements and feature/module config. | It must exclude orders, customers, payments, credentials, password hashes, tokens, printer endpoints, device credentials and Production runtime identity. |
| A-Q5 Profile storage | `DATABASE-BACKED VERSIONED PROFILE`. | Application code owns schema/contract/validation/compatibility; DB owns profile instances, versions and reviewed data. | Flyway governance applies. Additive profile schema may proceed only inside an approved Phase A loop; destructive/irreversible schema remains a TRUE OWNER GATE. |
| A-Q6 Unknown values | Unknown module and invalid module value fail closed. | Optional modules may use only explicit defaults declared by their profile/module contract. | Legacy fallbacks, including ambiguous printing mode defaults, must be fenced or migrated without breaking current Production behavior. |
| A-Q7 Platform Admin | Remove/retire direct legacy Platform Admin Active Store creation entry. | Audit UI route, backend API, authorization, callers and tests; preserve unrelated safe Platform Admin features. | If compatibility deprecation is needed, document it; do not route future Owner provisioning through the legacy direct writer. |
| A-Q8 Feature flags | Store-level module flags are required. | Split environment capability from Store module enablement; frontend gates by authenticated Store contract and backend enforces. | Global flags become environment/platform capability inputs, not the only source of module truth. |
| A-Q9 Hardware | Phase A defines hardware capability contract only. | Define logical printer roles, PAD_DIRECT, devices, module/hardware dependencies and readiness validation. | Physical printer endpoints, credentials, Pad pairing and test prints remain Phase B/C runtime readiness gates. |
| A-Q10 Bug loop | Continuous side loop. | P0/P1/security/data-integrity/architecture blockers may interrupt; ordinary bugs proceed through bounded Agile repair without stopping Phase A. | Do not mark side-loop field retest as blocking unless priority rules require it. |

### Phase B decisions

| Gate | Owner decision | Technical implementation | Migration / compatibility notes |
|---|---|---|---|
| B-Q1 Creator | Organization Owner only. | Backend must enforce Organization Owner authority for Store creation. | Manager/Staff cannot create Stores. |
| B-Q2 Lifecycle | Create a stable Draft Store row early. | Draft Store identity supports module configuration before validation/activation. | Draft is not Active and must not enter normal Staff operations. |
| B-Q3 Templates | First templates: `ST_DENIS_CANONICAL_PROFILE` and `CHINATOWN_PROFILE`. | Chinatown must be re-reviewed under the new module architecture before Phase C. | Do not wrap old hardcode as a Profile without review. |
| B-Q4 Staff credentials | New Store defaults: 1 Manager + 4 Staff identities. | Production uses secure temporary/bootstrap credential delivery. | A fixed shared numeric password may be non-Production test configuration only; never a hardcoded universal Production password. |
| B-Q5 Menu source | Owner prefers existing Store clone, implemented as reviewed/versioned safe snapshot materialization. | Capture source Store safe menu snapshot, then instantiate into target Store with new IDs. | No live-link, no order/credential/runtime copy, no automatic later source sync. |
| B-Q6 Printers | Logical topology default available when profile includes Printing. | Provision logical printer topology/assignments as profile data; bind physical endpoint separately. | Never copy source printer IP, endpoint, credential or physical device identity. |
| B-Q7 Pads | Auto Store assignment plus auto device enrollment. | Login/membership can identify Store; physical device identity remains separate and safely enrolled. | Staff membership is not a substitute for Pad/device identity. |
| B-Q8 Activation | Owner activates only after required validation passes. | `Owner authority + REQUIRED VALIDATION PASS = ACTIVATE`. | Owner cannot force structurally invalid Store active. |
| B-Q9 Replay | Same operation safely replays under same idempotency identity; changed config creates a new operation version. | No duplicate Store/menu/staff/membership/printer assignment on replay. | Changed normalized request must not be silently stuffed into old idempotency request. |
| B-Q10 Organizations | Multi-Organization architecture is required. | Support Organization -> many Stores now and future multiple Organizations. | Current Lanzhou three Stores share `LANZHOU NOODLES`; do not retain global single-Organization assumptions. |

### Phase C decisions

| Gate | Owner decision | Technical implementation | Migration / compatibility notes |
|---|---|---|---|
| C-Q1 Store order | Chinatown first, Sainte-Catherine second. | Phase C begins with Chinatown after Phase B acceptance. | No manual shortcut to either Store before Phase B. |
| C-Q2 Chinatown history | Full re-review under module architecture. | Classify old requirements as valid, superseded, module configuration, profile configuration or obsolete. | Do not restore historical hardcode/clone behavior mechanically. |
| C-Q3 Sainte-Catherine menu | Initial menu equals reviewed St-Denis menu template. | Materialize a copy into new Store identity; later menu becomes independent. | No automatic future sync from St-Denis without explicit Owner action. |
| C-Q4 Organization | `LANZHOU NOODLES` owns St-Denis, Chinatown and Sainte-Catherine. | Organization Owner sees/switches/aggregates all; Staff/Manager remain membership-scoped. | Architecture must still preserve future multi-Organization support. |
| C-Q5 Printers | Phase C acceptance includes physical printer binding/test for new real Stores. | Profile copies logical roles/routing only; runtime binds endpoint/device per Store. | Physical binding remains separate runtime readiness evidence. |
| C-Q6 Pads | Minimum 3 Pads per new Store. | Validate Store/device/access behavior for at least three Pads per new Store. | Credentials and device identities must not be hardcoded in Git. |
| C-Q7 Reports | Multi-store reporting required. | Owner can see single-Store reports, switch Stores, aggregate Organization reports, compare Stores and view authorized summary. | Backend must enforce Store/Organization authorization; no frontend-only isolation. |
| C-Q8 Isolation proof | Medium proof. | Automated positive, negative cross-Store and negative cross-Organization tests for critical APIs/mutations. | No heavyweight security framework is required solely for this medium proof. |
| C-Q9 Staff roles | Different staff/manager memberships across Stores are required. | At minimum: Organization Owner all Stores; Manager A one Store; Manager B two Stores if supported; Staff A/B/C one Store each. | Test credentials are created only through safe provisioning/test contract. |
| C-Q10 Final acceptance | No cross-Store data/permission conflict, all required flows operate, no known P0/P1 bug. | Prove St-Denis, Chinatown and Sainte-Catherine ACTIVE on one shared application with module/profile/provisioning/isolation/reporting/device/printing PASS. | Productization is complete only when another similar Store can be created without shared business-code changes. |

## 4. Final roadmap

### Phase A — Modular Productization

Goal:

```text
current working St-Denis implementation
→ shared modular platform for configurable N Stores
```

Execution order:

```text
A0  Dynamic Item Size Configuration
A1  Module Catalog
A2  Module Dependency Graph
A3  Store-Level Module Configuration
A4  Store Profile Contract
A5  St-Denis Canonical Profile
A5.5 Menu Management Configurability Closure
A5.6 Current Staging Architecture UML Baseline
A6  Backend Module Gating
A7  Frontend Module Gating
A8  Hardware Capability Contract
A9  Legacy Coupling Removal
A10 Module Validation / Regression
```

Current completed Phase A checkpoints:

```text
PHASE_A1_MODULE_CATALOG = PASS
PHASE_A2_MODULE_DEPENDENCY_GRAPH = PASS
PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION = PASS
DEPLOYED_STAGING_SHA = c1b5e7681f24a11fbf99293567b3da08076fa3b6
STAGING_FLYWAY = V13
CURRENT_STOP = PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION_COMPLETE_WAITING_FOR_PHASE_A4_OWNER_CONTINUATION
```

Current Owner-authorized continuous execution:

```text
PHASE_A4_STORE_PROFILE_CONTRACT
→ PHASE_A5_ST_DENIS_CANONICAL_PROFILE
```

A4 defines the database-backed versioned Store Profile contract with additive
Flyway V14 `store_profiles`, `store_profile_versions`, and
`store_profile_artifacts`, deterministic fingerprints, published version
immutability including profile binding protection, immutable artifact
insert/delete/update/move protection, A1/A2 module validation, prohibited-data
scanning and read-only Owner Profile APIs. A4 focused backend tests, full
backend tests, `git diff --check` and Agent 6 are PASS. A4 is generic contract
work only; A5 owns the first `ST_DENIS_CANONICAL_PROFILE` proof. A6 and Phase
B/C remain unauthorized until the A5 stop.

A4 entered `main` through PR #142 at
`be14923c96098d80b1b841e2ba0edbe3ca2563a5`. A5 repository implementation
entered `main` through PR #143 at
`b83afa98d304223834793d03bfc367b4cf4238f1` and adds Flyway V15 seed data for the safe, versioned
`ST_DENIS_CANONICAL_PROFILE/v1` and validates it with the A4 contract plus a
dry-run materialization validator. A5 proves profile-local identity remapping,
complete St-Denis menu graph relationships, A0.1 pricing policy continuity,
A0.2 combo configuration continuity, logical printing topology only,
role/access defaults without auth material, and profile/store independence.
The first exact-SHA Staging attempt applied V14 then failed closed before a V15
history row because V15 JSON seed literals started with newline characters and
the A4 `content_json` check uses `left(btrim(content_json), 1)`. PR #145
entered `main` at `494497dfbf874bcf12da7eb3821a276f663959c5` and repaired the
V15 seed literal layout plus OPS-001 Flyway checksum evidence. Exact-SHA
Staging deploy of that merge reached successful Flyway V15, then backend
startup failed closed because V14 `fingerprint_sha256 char(64)` columns were
mapped by JPA as default `varchar(255)`. PR #146 added explicit `char(64)` DDL
metadata, but Staging proved Hibernate still expected JDBC `Types#VARCHAR`.
PR #147 added explicit JDBC `Types#CHAR` metadata and regression coverage only.
Exact-SHA Staging deploy of `3440fddad7571409c66189e44976658921e5de1f` passed
health, WebSocket readiness, Flyway V15, Profile fingerprint/artifact
validation and deterministic graph-count validation. A5 is complete and waits
for Phase A6 Owner continuation.

The Owner then revised the pre-A6 sequence: the previously planned A5.5 UML
baseline is deferred as
`PHASE_A5_6_CURRENT_STAGING_ARCHITECTURE_UML_BASELINE`, while A5.5 is now:

```text
PHASE_A5_5_MENU_MANAGEMENT_CONFIGURABILITY_CLOSURE
```

A5.5 closes Owner-requested configurability gaps in Menu Management. It adds a
generic Store-scoped Combo Group model on top of the existing
`store_combo_components` canonical content table, Category management through
the current `menu_categories` model, and Station management through the current
`stations` model. It preserves Store-level Combo price in
`store_pricing_policies.combo_delta`, item Combo allowed in item-scoped
`menu_item_options`, historical order/printing/report snapshots, and
Store-scoped menu revision/cache invalidation. Evidence:
[PHASE_A5_5_MENU_MANAGEMENT_CONFIGURABILITY_EVIDENCE](PHASE_A5_5_MENU_MANAGEMENT_CONFIGURABILITY_EVIDENCE.md).

After A5.5 merge, exact-SHA Staging deploy and automated validation must pass
before Owner manual retest. The first Owner manual Staging acceptance failed on
menu cache hash parity, dynamic Combo rendering/default refresh, and
display-order UX. Historical automated PASS evidence remains retained; the
current bounded repair must deploy only to Staging, keep Production
no-mutation, and must stop at:

```text
PHASE_A5_5_OWNER_ACCEPTANCE_REPAIR_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

Owner manual acceptance of A5.5 is now PASS. A5.6 is complete as the formal
current-Staging anti-drift architecture baseline:
[Current Staging Architecture UML Baseline](../../architecture/current-staging/README.md).
The formal baseline is fresh main/deployed Staging
`923346f15757ca85fdafb509a803e87f04ae55bd` at Flyway V16.

Current continuous execution is:

```text
PHASE_A7_FRONTEND_MODULE_GATING
```

A6 backend module gating evidence:
[PHASE_A6_BACKEND_MODULE_GATING_EVIDENCE](PHASE_A6_BACKEND_MODULE_GATING_EVIDENCE.md).
A7 frontend module gating evidence:
[PHASE_A7_FRONTEND_MODULE_GATING_EVIDENCE](PHASE_A7_FRONTEND_MODULE_GATING_EVIDENCE.md).
After A7 exact-SHA Staging validation, stop before A8:

```text
PHASE_A7_FRONTEND_MODULE_GATING_COMPLETE_WAITING_FOR_PHASE_A8_OWNER_CONTINUATION
```

Owner final manual retest found one remaining UI-only blocker in Combo
Configuration sort-control layout. The bounded
`PHASE_A5_5_COMBO_CONFIGURATION_SORT_CONTROL_UI_REPAIR` may change only
frontend layout/responsive spacing for group/component rows and must stop at:

```text
PHASE_A5_5_FINAL_UI_REPAIR_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

This order must not change unless fresh audit proves a real technical
dependency. Any change records the original order, actual order and reason.

Phase A final technical stop:

```text
PHASE_A_MODULAR_PRODUCTIZATION_IMPLEMENTATION_COMPLETE_WAITING_FOR_OWNER_ACCEPTANCE
```

After Owner acceptance:

```text
PHASE_A_MODULAR_PRODUCTIZATION_ACCEPTED_WAITING_FOR_PHASE_B_APPROVAL
```

### Phase B — Owner New Store Provisioning

Goal:

```text
Owner → Create New Store → Profile → Modules → Configure → Validate
→ READY → Activate
```

Minimum subloops:

```text
B1 DRAFT STORE
B2 PROFILE SELECTION
B3 MODULE SELECTION
B4 MENU PROVISIONING
B5 TABLE/STATION CONFIGURATION
B6 STAFF/ACCESS PROVISIONING
B7 PRINTING TOPOLOGY
B8 AUTO DEVICE ENROLLMENT
B9 VALIDATION
B10 READY
B11 OWNER ACTIVATION
B12 REPLAY/RECOVERY
```

Phase B is not authorized for implementation by this Planbook.

### Phase C — Real Multi-Store Proof

Goal:

```text
Chinatown first
→ Sainte-Catherine second
→ St-Denis + Chinatown + Sainte-Catherine multi-Store acceptance
```

Phase C must use the accepted Phase B provisioning workflow. It must not use
manual SQL shortcuts, old Store-ID hardcodes, database clones, code forks, or
special Chinatown/Sainte-Catherine branches.

Phase C is not authorized for implementation by this Planbook.

## 5. Phase A0 — Dynamic Item Size Configuration

### A0.1 refinement gate

Owner Staging UI review after A0 accepted the underlying
`OPTION + MODIFIER_GROUP + PRICE_DELTA` engine but rejected free-form Size
creation/editing as final product UX. A0.1 requires system-controlled
Small/Regular/Large Size definitions and Store-level Size/Combo pricing policy
as the canonical price source.

Fresh schema audit found no existing Store-level pricing policy/settings table.
The Owner approved the additive schema direction:

```text
PHASE_A0_1_PRICING_POLICY_SCHEMA_CHANGE_APPROVAL
```

Design/evidence:
[PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_SCHEMA_GATE](PHASE_A0_1_STANDARD_SIZE_AND_STORE_PRICING_POLICY_SCHEMA_GATE.md).
Implementation evidence:
[PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_IMPLEMENTATION_EVIDENCE](PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_IMPLEMENTATION_EVIDENCE.md).
Staging evidence:
[PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_STAGING_EVIDENCE](PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_STAGING_EVIDENCE.md).

A0.1 is deployed to exact-SHA Staging at
`ed3e4cdbf38c4d8812620baf64cd42ce3a229431`; Staging Flyway advanced from V10
to V11 and automated validation passed. Owner manual retest accepted:

```text
OWNER_A0_1_PRICING_UX_RETEST = PASS
```

### A0.2 Store Combo Configuration

Owner approved the next bounded A0 implementation:

```text
PHASE_A0_2_STORE_COMBO_CONFIGURATION
```

A0.2 implements Store-level Combo Contents configuration while preserving the
reviewed A0.1 pricing and item contracts:

- `store_pricing_policies.combo_delta` remains the canonical Store-level Combo
  price source.
- Item `COMBO_ALLOWED` remains expressed by the item-scoped
  `menu_item_options` `COMBO` row.
- `menu_item_options` continues to own Size enablement/identity, item
  `COMBO_ALLOWED`, ordinary options and rollback-compatible legacy rows. It is
  not the new application canonical source for `COMBO_EGG` / `COMBO_SIDE`
  contents.
- Store-level Combo content availability is modeled with additive
  `store_combo_components`.
- New ordering may use stable synthetic transport IDs in order snapshots for
  Store-level Combo components, but the canonical enabled/name/code state remains
  `store_combo_components`.
- No shared-code Store ID/name conditionals, second combo engine, Production
  mutation, Phase B/C work, Chinatown work or Production deploy is authorized.

Required system-controlled first catalog:

```text
COMBO_EGG:
  combo_tea_egg
  combo_fried_egg

COMBO_SIDE:
  combo_edamame
  combo_shredded_potato
  combo_cucumber_salad
```

Content mutations must be Store-scoped and must update
`stores.menu_revision` / `stores.menu_updated_at` in the same transaction.
New catalog/hash/cache behavior must include `combo_configuration`. New order
submission must reject disabled or unsupported Store-configured
`COMBO_EGG`/`COMBO_SIDE` selections. Existing drafts, submitted/completed
orders, receipts, printing snapshots and reports must not be repriced or
reselected.

Implementation evidence:
[PHASE_A0_2_STORE_COMBO_CONFIGURATION_IMPLEMENTATION_EVIDENCE](PHASE_A0_2_STORE_COMBO_CONFIGURATION_IMPLEMENTATION_EVIDENCE.md).
Runtime Staging evidence:
[PHASE_A0_2_STORE_COMBO_CONFIGURATION_STAGING_EVIDENCE](PHASE_A0_2_STORE_COMBO_CONFIGURATION_STAGING_EVIDENCE.md).

A0.2 is merged through PR #134 and deployed to exact-SHA Staging at
`90ac0cb0496161b12c47cff00573b56b4abc961c`; Staging is Flyway V12 and
automated validation passed. Owner manual Staging retest is accepted:

```text
OWNER_A0_2_MANUAL_STAGING_RETEST = PASS
```

Historical A0.2 closure:

```text
PHASE_A0_2_STORE_COMBO_CONFIGURATION_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

Current implementation must be audited before changes. A0 must determine the
actual size model:

```text
CURRENT_SIZE_MODEL =
OPTION | MODIFIER_GROUP | ITEM_VARIANT | PRICE_DELTA | HARDCODED_UI | OTHER
```

The target product model is:

```text
MenuItem
  └── SizeVariant[1..N]
```

The implementation should use the existing canonical Menu Engine if it already
represents this safely through option/modifier data. Do not create a second size
engine unless the current schema cannot satisfy the contract.

Required fields/semantics:

```text
id
item_id
code/key
display_name
display_name_zh
display_order
enabled
default_selected
pricing rule
```

The canonical pricing model must be based on current code audit. If current
ordering already uses:

```text
base item price + size price delta
```

preserve that model. Do not maintain conflicting absolute-price and delta
truths. Absolute price variants can be recorded as a future extension if needed.

Required tested cases:

```text
Small / Regular / Large
Regular / Large
Single Size
Small enabled + Regular enabled + Large disabled
valid single default for multi-size Items
```

Menu Management must support, where the contract permits:

```text
enable/disable size
add size
edit bilingual label
set price delta/current pricing field
set display order
set default
```

Validation must enforce:

```text
at least one enabled Size when size config applies
unique code/key per Item
valid pricing
valid display order
valid default
```

Single enabled Size behavior:

```text
Ordering auto-selects the only valid size.
Order item snapshot, price, printing, history and edit flow still retain a
deterministic size identity.
```

Draft consistency contract:

```text
existing draft lines retain their selected item/size snapshot;
new menu revision applies to future selections;
backend submit performs final legality/price/availability validation;
never silently transform an existing selected size into another size.
```

Revision/cache contract:

```text
Pad has menu revision N
→ Menu Management changes Size config
→ backend revision becomes N+1
→ Pad detects revision
→ complete snapshot download/validation
→ atomic IndexedDB head switch
→ Ordering modal uses new Size config
```

Weak network fallback keeps revision N fully usable until the complete N+1
snapshot succeeds. Mixed revision item/size data is forbidden.

Printing/receipt contract:

```text
GRAB
FRONTDESK_RECEIPT
HOT_KITCHEN where applicable
```

must keep current reviewed renderer business rules. Dynamic Size must not
rewrite abbreviations or create new print semantics except where required to
show the selected configured Size.

### A0 migration gate

If A0 requires a new Flyway migration or irreversible schema change, stop at:

```text
PHASE_A0_SCHEMA_CHANGE_WAITING_FOR_OWNER_APPROVAL
```
unless the exact Phase A loop has already documented and passed the applicable
schema governance. Additive schema work is not a Production action, but it
still needs the reviewed Flyway path, tests and Staging-first validation.

### A0 Staging validation

A0 may deploy exact candidate to Staging after tests, Agent 6, governance sync,
PR and merge. It must not deploy to Production.

Staging test data must be synthetic/Operational Twin-safe. If a Staging menu
item is modified for Owner UX testing, record it as:

```text
STAGING FIELD TEST CONFIG
```

and do not sync it to Production.

Owner retest checklist:

```text
1. Open Menu Management.
2. Choose one safe test noodle item.
3. Configure Small / Regular / Large.
4. Save.
5. Without clearing Pad cache, observe menu update.
6. Open the item and confirm all three Sizes.
7. Change to Regular / Large.
8. Observe Pad again.
9. Change to Single Size.
10. Confirm ordering needs no extra Size tap and price is correct.
```

If code, Staging deployment and automated validation pass but Owner UI retest
is still pending, report exactly:

```text
A0_CODE = COMPLETE
A0_STAGING_DEPLOYED = YES
A0_AUTOMATED_VALIDATION = PASS
A0_OWNER_UI_ACCEPTANCE = PENDING
```

Do not report Owner acceptance as PASS before the Owner confirms it.
For the current A0.1 Pricing UX, the Owner has now confirmed PASS; this
conditional template remains for future loops that deploy before Owner retest.

## 6. Phase A1-A10 acceptance summary

| Loop | Required output |
|---|---|
| A1 Module Catalog | Canonical module catalog with module key, display name, category, core/optional, default state, dependencies, conflicts, environment requirements, hardware requirements, activation behavior, frontend routes and backend capabilities. |
| A2 Dependency Graph | Fail-closed validator for requires/conflicts/environment/hardware constraints. |
| A3 Store Module State | Clear Store module state model, generally `DISABLED` / `ENABLED`, with `CONFIGURING` only if lifecycle truly requires it. |
| A4 Store Profile Contract | Database-backed versioned Profile/ProfileVersion/ProfileModule/ProfileConfigurationSnapshot design and implementation. |
| A5 St-Denis Canonical Profile | Reviewed safe profile expressing St-Denis-like Store generation without Production clone or Store-specific code path. |
| A5.5 Menu Management Configurability | Owner can configure Store-specific Combo groups/components, Categories, and Stations without developer code changes, while preserving historical snapshots and menu revision/cache consistency. |
| A5.6 Current Staging UML Baseline | Documentation-only architecture baseline for current Staging after A5/A5.5, before A6 backend gating. |
| A6 Backend Gating | `authority + Store module enabled + environment capability available` enforced server-side. |
| A7 Frontend Gating | Routes/navigation/settings/screens derive from authenticated Store Context `module_configuration`, fail closed, keep KDS disabled UX Store-scoped, and keep Reports Core independent from Analytics Advanced. |
| A8 Hardware Capability | Logical hardware capability contract for printing, Pad Direct, devices and readiness requirements without endpoints/secrets. |
| A9 Legacy Cleanup | Chinatown Store-ID constant, legacy user fallback, direct active Store writer, global flags and blank printing mode either removed or bounded without current Production regression. |
| A10 Regression | Prove multiple legal Store module configurations on one shared application with no cross-Store effects. |

Phase A final validation requires:

```text
MODULE_CATALOG = READY
MODULE_DEPENDENCY_GRAPH = READY
STORE_MODULE_CONFIGURATION = READY
STORE_PROFILE_CONTRACT = READY
ST_DENIS_CANONICAL_PROFILE = READY
BACKEND_GATING = PASS
FRONTEND_GATING = PASS
HARDWARE_CAPABILITY_CONTRACT = READY
LEGACY_BLOCKERS = REMOVED/BOUNDED
N_STORE_MODULE_REGRESSION = PASS
A0 OWNER SIZE UX = PASS/PENDING
A0.2 STORE COMBO CONFIGURATION = OWNER_ACCEPTED
```

## 7. True Owner gates

Stop for Owner decision if the next step requires:

```text
new product decision not covered here
Production mutation
Production deployment
new Store creation
Chinatown runtime creation
Sainte-Catherine runtime creation
destructive data migration
irreversible schema change
security boundary weakening
payment/regulatory change
credential policy change
ambiguous Store isolation risk
Phase B implementation
Phase C implementation
```

普通 bounded implementation bugs inside approved Phase A follow the Agile repair
loop: root cause, minimal repair, tests, Agent 6, governance sync, PR,
auto-merge, fresh fetch and retry the failed step.

## 8. Current loop after this Planbook

After this Planbook enters `main`, the next implementation loop is:

```text
PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION
```

Current fresh authority has advanced inside A0 through A0.1 and A0.2. A0.2 has
reached exact-SHA Staging automated validation PASS and Owner manual retest
PASS. The Owner now authorizes continuous A1 -> A2 -> A3 execution. Do not
restart completed A0 inventory/design work. Do not start A4, Phase B/C,
Chinatown, Sainte-Catherine or Production work without a fresh Owner decision.

Current A1 Module Catalog evidence:

```text
docs/governance/agile/PHASE_A1_MODULE_CATALOG.md
backend/src/main/resources/module/module-catalog.v1.json
backend/src/test/java/com/restaurant/system/modules/ModuleCatalogContractTest.java
```

A1 completion:

```text
PHASE_A1_MODULE_CATALOG = PASS
AGENT_6 = A1_ACCEPT
PR = #137
MERGE_SHA = 34169152c6d48ecf503b441fe7428416c399d0a9
```

Current A2 Module Dependency Graph evidence:

```text
docs/governance/agile/PHASE_A2_MODULE_DEPENDENCY_GRAPH.md
backend/src/main/resources/module/module-dependency-graph.v1.json
backend/src/main/java/com/restaurant/system/modules/ModuleDependencyValidator.java
backend/src/test/java/com/restaurant/system/modules/ModuleDependencyValidatorTest.java
```

A2 completion:

```text
PHASE_A2_MODULE_DEPENDENCY_GRAPH = PASS
AGENT_6 = A2_ACCEPT
PR = #138
MERGE_SHA = 1780c8934a502709844713d91c493b076e714983
```

Current A3 Store-level Module Configuration evidence:

```text
docs/governance/agile/PHASE_A3_STORE_LEVEL_MODULE_CONFIGURATION.md
backend/src/main/resources/db/migration/V13__add_store_modules.sql
backend/src/main/java/com/restaurant/system/modules/StoreModuleServiceImpl.java
backend/src/main/java/com/restaurant/system/modules/StoreModuleController.java
backend/src/test/java/com/restaurant/system/modules/StoreModuleServiceImplTest.java
backend/src/test/java/com/restaurant/system/modules/StoreModuleControllerTest.java
```

Expected successful A0 stop:

```text
FINAL_PRODUCTIZATION_PLANBOOK_MERGED_PHASE_A0_DEPLOYED_WAITING_FOR_OWNER_SIZE_RETEST
```

If A0 hits the schema gate:

```text
PHASE_A0_SCHEMA_CHANGE_WAITING_FOR_OWNER_APPROVAL
```
