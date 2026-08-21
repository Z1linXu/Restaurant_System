# Phase A11 Printing Rule Transparency and Configuration Design Audit

## Status

```text
PHASE_A11_PRINTING_RULE_TRANSPARENCY_AND_CONFIGURATION_DESIGN = AUDIT_COMPLETE
A11_OWNER_5_ANSWERS = CLOSED
A11_IMPLEMENTATION = ACCEPTED_BY_OWNER
PHASE_A11_OWNER_ACCEPTANCE = PASS
PHASE_B_IMPLEMENTATION = WAITING_FOR_EXPLICIT_OWNER_APPROVAL
```

Current stop after Owner acceptance and A11.5 design:

```text
PHASE_A11_5_CHAIN_MASTER_MENU_DESIGN_COMPLETE_WAITING_FOR_PHASE_B_OWNER_APPROVAL
```

Latest Owner acceptance supersedes the original A11 blocking state. Do not
rerun A11 manual acceptance in the A11.5 loop.

## Authority and boundaries

- Owner gate:
  `PHASE_A11_PRINTING_RULE_TRANSPARENCY_AND_CONFIGURATION_DESIGN`.
- Fresh repository authority:
  `origin/main@78ac87b633ba6d4e113d52ed65eddb8fcc06eacd`.
- Current deployed Staging identity from current governance:
  application SHA `ad4572759e01b5546ec59af24aa36b09e5c2dd00`,
  Flyway `V16`.
- Local execution context could not read the Docker socket or mounted
  `/srv/restaurant-pos/staging` evidence path; no runtime mutation was attempted.
- Production: `NO MUTATION`.
- Historical design-audit Staging boundary: `NO MUTATION`, no deploy, no
  restart, no runtime config change.
- Historical design-audit backend/frontend boundary: `NO CHANGE`.
- Historical design-audit Flyway/schema boundary: `NO CHANGE`.
- Phase B/C, Chinatown, Sainte-Catherine and Production promotion:
  `NOT_STARTED`.

Implementation overlay:

- Owner selected the hybrid UI: item aliases in Menu Management, Store-global
  dictionaries/rules/history/preview in Printing Settings.
- Owner selected independent output aliases with menu-name fallback for
  `GRAB`, `FRONTDESK_RECEIPT` and `HOT_KITCHEN`.
- Owner selected Store-global dictionaries plus item overrides.
- Owner selected frozen historical rule revision/rendered evidence.
- Owner selected structured fields plus constrained conditions only; no
  scripts, regex or raw executable templates.
- Repository candidate adds additive V17, Store-scoped versioned rule
  persistence, backend resolver/API, renderer integration, PrintJob revision
  capture, and frontend Owner UI. Staging deployment and Owner retest remain
  separate runtime gates.

## Deliverables

- [PRINTING_RULE_RECONCILIATION_MATRIX](PRINTING_RULE_RECONCILIATION_MATRIX.md)
- [PRODUCT_PRINT_RULE_INVENTORY](PRODUCT_PRINT_RULE_INVENTORY.md)
- [PRINTING_RULE_CONFIGURATION_ARCHITECTURE_OPTIONS](PRINTING_RULE_CONFIGURATION_ARCHITECTURE_OPTIONS.md)
- [A11_IMPLEMENTATION_PLAN_DRAFT](A11_IMPLEMENTATION_PLAN_DRAFT.md)
- [PHASE_A11_PRINTING_RULE_CONFIGURATION_IMPLEMENTATION_EVIDENCE](PHASE_A11_PRINTING_RULE_CONFIGURATION_IMPLEMENTATION_EVIDENCE.md)

## Files inspected

Required governance and system authority:

- `docs/governance/runtime/ALIVE_RUNTIME_PLANBOOK.md`
- `docs/governance/runtime/CURRENT_HANDOFF.md`
- `docs/governance/AGILE_LOOP_OPERATING_MODEL.md`
- `docs/governance/agile/FINAL_PRODUCTIZATION_PLANBOOK.md`
- `docs/governance/FEATURE_BACKLOG.md`
- `docs/governance/KNOWN_ISSUES_BACKLOG.md`
- `SYSTEM_DOCUMENTATION.md`
- `doc/API.md`

Printing and profile/hardware evidence:

- `docs/operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md`
- `docs/archive/architecture/phase-a-staging-2026-08-14/05_PRINTING_SEQUENCE.md`
- `docs/governance/runtime/STAGING_MOCK_PRINTING_FIELD_TEST_EVIDENCE.md`
- `docs/governance/runtime/STAGING_MOCK_PRINTING_RUNTIME_POLICY_REPAIR_EVIDENCE.md`
- `docs/governance/runtime/OWNER_FIELD_TEST_PRINTING_FIXES_EVIDENCE.md`
- `docs/governance/agile/PHASE_A0_DYNAMIC_ITEM_SIZE_CONFIGURATION_EVIDENCE.md`
- `docs/governance/agile/PHASE_A0_1_STANDARD_SIZE_PRICING_POLICY_IMPLEMENTATION_EVIDENCE.md`
- `docs/governance/agile/PHASE_A0_2_STORE_COMBO_CONFIGURATION_IMPLEMENTATION_EVIDENCE.md`
- `docs/governance/agile/PHASE_A4_STORE_PROFILE_CONTRACT.md`
- `docs/governance/agile/PHASE_A5_ST_DENIS_CANONICAL_PROFILE.md`
- `docs/governance/agile/PHASE_A5_5_MENU_MANAGEMENT_CONFIGURABILITY_EVIDENCE.md`
- `docs/governance/agile/PHASE_A8_HARDWARE_CAPABILITY_CONTRACT_EVIDENCE.md`
- `docs/governance/agile/PHASE_A9_LEGACY_COMPATIBILITY_LEDGER.md`
- `docs/governance/agile/PHASE_A9_LEGACY_COUPLING_REMOVAL_EVIDENCE.md`
- `docs/governance/agile/PHASE_A10_FINAL_MODULAR_PRODUCTIZATION_ACCEPTANCE_EVIDENCE.md`
- `docs/governance/../../governance/drafts/AL-004_GENERIC_STORE_PROFILE_CONTRACT.md`
- `docs/governance/../../governance/drafts/AL-005_PRINTING_PROVISIONING_MODULE_PLAN.md`
- `docs/governance/../../governance/drafts/AL-005B_DEVICE_PAD_PROVISIONING_MODULE_PLAN.md`

Renderer and supporting code:

- `backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java`
- `backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java`
- `backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java`
- `backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java`
- `backend/src/main/java/com/restaurant/system/printing/renderer/PrintTableDisplayFormatter.java`
- `backend/src/main/java/com/restaurant/system/printing/renderer/PrintMarkup.java`
- `backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java`
- `backend/src/main/java/com/restaurant/system/printing/semantic/OptionSemanticResolver.java`
- `backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/service/impl/PrintJobServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/service/impl/OrderDispatchOutboxProcessor.java`
- `backend/src/main/java/com/restaurant/system/menu/entity/MenuItem.java`
- `backend/src/main/java/com/restaurant/system/menu/entity/MenuItemOption.java`
- `backend/src/main/java/com/restaurant/system/order/entity/OrderItem.java`
- `backend/src/main/java/com/restaurant/system/order/entity/OrderItemOption.java`
- `backend/src/main/java/com/restaurant/system/kitchen/entity/KitchenTask.java`
- `backend/src/main/java/com/restaurant/system/printing/entity/PrintJob.java`

Tests and frontend surfaces:

- `backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java`
- `backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java`
- `backend/src/test/java/com/restaurant/system/printing/semantic/OptionSemanticResolverTest.java`
- `backend/src/test/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityServiceTest.java`
- `backend/src/test/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImplTest.java`
- `frontend/src/features/owner-admin/PrintingSettingsPage.tsx`
- `frontend/src/features/owner-admin/MenuManagementPage.tsx`
- `frontend/src/features/owner-admin/MenuOptionsPanel.tsx`
- `frontend/src/features/owner-admin/ComboConfigurationPanel.tsx`
- `frontend/src/services/printingAdminService.ts`
- `frontend/src/services/ownerMenuOptionService.ts`

## Actual printing implementation summary

Current printing is a layered pipeline:

```text
Order submit/update
→ OrderItem / OrderItemOption snapshots
→ KitchenTask snapshots
→ durable print dispatch event
→ PrintJob
→ renderer
→ rendered_text_snapshot
→ MOCK / PAD_DIRECT / REAL dispatch
→ reprint path
```

The audit found that the current print text is not controlled by a single
configuration source. It is composed from:

- Store/menu data:
  - item Chinese/English names;
  - option Chinese/English names;
  - option type/group/code;
  - category/station metadata;
  - Store Combo Configuration;
  - Store printer assignments and runtime mode.
- frozen order/kitchen snapshots:
  - `order_items.item_name_snapshot_*`;
  - `order_item_options.option_*_snapshot`;
  - `kitchen_tasks.item_name_snapshot_*`;
  - `kitchen_tasks.special_instructions_snapshot`;
  - `print_jobs.rendered_text_snapshot`.
- hardcoded code rules:
  - SKU aliases;
  - Size/noodle/spicy abbreviations;
  - add/remove modifier tokens;
  - green compression;
  - combo label cleaning;
  - Frontdesk receipt wording;
  - HOT_KITCHEN eligibility fallbacks;
  - quantity formatting;
  - split-table A/B wording.
- legacy fallbacks:
  - Chinese/English label string matching for old options and fried egg
    semantics.

## Findings

### 1. The current Owner cannot inspect or configure many business display rules

Examples:

- product aliases such as `鸡凉`, `牛炒`, `番炒`, `酸`, `红`;
- Frontdesk `传统牛肉面 -> 牛肉面`;
- `二细 -> 二`, `韭叶 -> 韭`;
- `少辣 -> （少s）`;
- `加葱 + 加香菜 -> 加青`;
- `套餐毛豆 -> 毛豆` / `小菜: 毛豆`;
- quantity formatting differences across GRAB, Frontdesk and Hot Kitchen.

These rules are currently buried in renderers or order snapshot construction.

### 2. Existing Store/menu configuration is necessary but not sufficient

Menu and option rows already supply names, codes, groups, prices and Store
Combo Configuration. However, there are no Store-owned output-specific aliases
or display dictionaries. Two Stores with the same codebase but different print
wording cannot currently diverge without code changes.

### 3. Documentation and code are not perfectly aligned

Top inconsistencies:

- `docs/operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md` still documents GRAB
  fried quantity as `2*炸虾`; current code/tests use `2×炸虾`.
- `SYSTEM_DOCUMENTATION.md` still says `FRONTDESK_RECEIPT` intentionally does
  not print item notes; current `FrontdeskReceiptRenderer` does print
  `备注：{note}`.
- Some rules are now resolved in code/tests and high-level Known Issues, but
  the operational rule document still carries historical wording or line-number
  references.

### 4. HOT_KITCHEN mixes display and eligibility concerns

`HotKitchenPrintEligibilityService` correctly treats routing/eligibility as
system logic. It still contains product-specific fallback SKUs and fried-egg
label fallback. A11 must not expose eligibility as Owner display wording.
Display aliases may be configurable, but hot-kitchen routing must remain
protected.

### 5. Reprint semantics need an explicit product decision

Current behavior:

- single PrintJob reprint uses `print_jobs.rendered_text_snapshot` when present;
- order-level reprint creates a new job and re-renders the complete order
  through the current renderer.

If Owner changes printing display rules later, order-level historical reprint
could change text unless A11 records and resolves a historical rule revision.

## Owner UX location audit

Option A: `Menu Management → Menu Item → Printing Rules`

- Best for product-specific aliases.
- Weak for Store-global dictionaries such as Size, noodle type, spiciness and
  table labels.

Option B: `Printing Settings → Display / Naming Rules`

- Best for Store-global print-display dictionaries and output previews.
- Weak when Owner is already editing one menu item and wants to set aliases.

Hybrid:

- Menu Management owns item-specific aliases and overrides.
- Printing Settings owns Store-global dictionaries, output labels, preview and
  validation.

Audit recommendation: hybrid, pending Owner answer.

## A11 product design boundary

Owner-configurable:

- product print alias per output;
- Size wording per output;
- noodle type wording per output;
- spiciness wording per output;
- modifier/add/remove wording per output;
- combo egg/side wording per output;
- quantity wording from structured choices;
- Store-global and item-specific display overrides.

Not Owner-configurable:

- printer routing authorization;
- PrintJob dispatch target;
- queue, lease, retry and duplicate prevention;
- payment/order amount/tax/report logic;
- Store/Organization authorization;
- physical printer endpoints and Pad/device credentials.

## A11 modular architecture result

A11 must add a Phase A closure before Phase B:

```text
Phase A automated acceptance
→ A11 Printing Rule Transparency & Configuration Design
→ Owner Design Decision
→ A11 Implementation
→ Staging Owner Acceptance
→ Phase B
```

Phase B remains blocked until A11 passes Staging Owner acceptance.

Future Phase B Store creation must consume:

```text
Printing Rule Configuration
+
Logical Printing Topology
+
Hardware Capability Readiness
```

`ST_DENIS_CANONICAL_PROFILE` should be able to include a
`PRINTING_DISPLAY_RULES` snapshot/default artifact in a future profile version.
Materialization must create target Store-owned independent rule configuration.
