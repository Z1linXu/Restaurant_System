# A11 Implementation Plan Draft

Status:

```text
A11_IMPLEMENTATION_PLAN = ACCEPTED_BASELINE
A11_OWNER_5_ANSWERS = CLOSED
A11_IMPLEMENTATION = ACCEPTED_BY_OWNER
PHASE_A11_OWNER_ACCEPTANCE = PASS
```

This plan was the implementation baseline after the Owner answered the A11
product questions and approved the implementation gate. Repository evidence is
recorded in
[PHASE_A11_PRINTING_RULE_CONFIGURATION_IMPLEMENTATION_EVIDENCE](PHASE_A11_PRINTING_RULE_CONFIGURATION_IMPLEMENTATION_EVIDENCE.md).
The latest Owner verdict supersedes the historical Staging Owner-acceptance
pending step; do not rerun A11 manual acceptance.

## Proposed sequence after Owner approval

```text
Owner 5 answers = CLOSED
→ final A11 product decision record
→ additive V17 persistence
→ backend rule contract
→ renderer integration
→ profile artifact contract
→ frontend Owner UI
→ deterministic preview/validation
→ tests/regression
→ Agent 6
→ PR/merge
→ exact-SHA Staging deploy
→ Staging Owner acceptance
→ unblock Phase B
```

Implemented candidate summary:

- `printing_display_rule_sets` and `printing_display_rule_revisions` are the
  Store-scoped canonical A11 rule source.
- Menu Management owns item-specific aliases for `GRAB`,
  `FRONTDESK_RECEIPT`, and `HOT_KITCHEN`.
- Printing Settings owns Store-global dictionaries, constrained conditions,
  preview, validation, publish, fingerprint and revision history.
- `print_jobs` records the rendering rule revision/fingerprint while preserving
  the existing `rendered_text_snapshot` as the primary job-reprint artifact.
- Post-A11 profile versions must include a `PRINTING_DISPLAY_RULES` artifact;
  historical v1 profile contracts remain valid.

## A11.1 Final design closure

Inputs:

- `PHASE_A11_PRINTING_RULE_AUDIT.md`
- `PRINTING_RULE_RECONCILIATION_MATRIX.md`
- `PRODUCT_PRINT_RULE_INVENTORY.md`
- `PRINTING_RULE_CONFIGURATION_ARCHITECTURE_OPTIONS.md`
- Owner answers to the five A11 questions

Outputs:

- final accepted A11 configuration contract;
- exact UI placement;
- historical reprint policy;
- profile artifact shape;
- validation rules.

No runtime action is included in this step.

## A11.2 Additive schema gate, if accepted

Likely additive persistence candidates:

```text
printing_display_rule_sets
printing_display_rule_revisions
```

or equivalent Store-scoped versioned structures.

Required schema properties:

- Store-scoped;
- Organization-safe through Store relationship;
- versioned revisions with deterministic fingerprint;
- no physical printer endpoints;
- no device IDs, tokens, credentials or pairing state;
- no order/customer/payment data;
- no source Store database IDs in profile artifacts;
- immutable or protected published profile/default revisions.

Fail-closed migration/backfill:

- materialize rule revision 1 from current code-equivalent defaults;
- reject ambiguous duplicate semantic aliases;
- preserve existing historical orders/print jobs;
- do not reinterpret old `rendered_text_snapshot`.

## A11.3 Backend rule resolver

Add a structured resolver that receives:

```text
store_id
output_type
order/item/option/task snapshots
active printing display rule revision
```

It returns display labels only.

The resolver must not:

- choose printer assignment;
- change routing or eligibility;
- change price, tax, total or report values;
- change PrintJob state;
- contact printers;
- inspect unrelated Stores.

Recommended resolver boundaries:

- item alias lookup;
- dictionary lookup;
- item override lookup;
- safe fallback to frozen order/menu snapshots;
- strict validation for required aliases.

## A11.4 Renderer integration

Renderer changes should be minimal and focused:

- GRAB renderer consumes resolved aliases/dictionaries for display wording;
- Frontdesk renderer consumes output-specific aliases and labels;
- Hot Kitchen renderer consumes display aliases but keeps eligibility protected
  in `HotKitchenPrintEligibilityService`;
- `KitchenNoodlePrintFormatter` receives resolved display tokens instead of
  hardcoding all modifier/quantity wording.

System logic retained in code:

- grouping keys;
- duplicate prevention;
- route eligibility;
- job state;
- ESC/POS markup conversion;
- Store authorization.

## A11.5 Snapshot and reprint integration

Recommended default:

```text
FROZEN_RULE_REPRINT
```

Implementation outline:

- new order submit/update records active printing display rule revision for
  each print render;
- `print_jobs.rendered_text_snapshot` remains the primary reprint artifact;
- job reprint continues using frozen rendered snapshot;
- order-level reprint either:
  - uses the order/job captured rule revision, or
  - if the Owner explicitly chooses `LATEST_RULE_REPRINT`, clearly labels that
    behavior in Print Center.

Historical orders without a captured A11 revision must use current frozen
snapshots and existing renderer-compatible fallback, not guessed rules.

## A11.6 Profile contract

Future `ST_DENIS_CANONICAL_PROFILE` should be able to reference:

```text
PRINTING_DISPLAY_RULES
```

Profile artifact constraints:

- profile-local item/option/station refs only;
- no source Store DB IDs;
- no physical printer endpoint;
- no device credential;
- no runtime PrintJob data;
- deterministic fingerprint;
- materialization creates target Store-owned independent rule revision 1.

Phase B Create New Store must consume the accepted A11 contract together with
menu/profile materialization, logical printing topology and hardware readiness.

## A11.7 Owner UI and preview

Likely hybrid UI, pending Owner answer:

- Menu Management:
  - item-specific `Printing Rules`;
  - aliases for GRAB, Frontdesk Receipt and Hot Kitchen;
  - item default-option display behavior.
- Printing Settings:
  - Store-global Display/Naming Rules;
  - Size/noodle/spicy/modifier/combo dictionaries;
  - output preview;
  - validation/fingerprint/revision history.

Preview must show at least:

- GRAB ticket;
- FRONTDESK_RECEIPT;
- HOT_KITCHEN where applicable;
- combo case;
- add/remove modifier case;
- size/noodle/spicy case;
- historical snapshot/reprint behavior if configured.

## A11.8 Validation requirements

Backend tests:

- rule schema validation;
- Store isolation;
- profile artifact validation;
- materialization independence;
- alias/dictionary precedence;
- invalid/blank/duplicate alias rejection;
- renderer golden outputs for GRAB/FRONTDESK_RECEIPT/HOT_KITCHEN;
- historical reprint/frozen snapshot behavior;
- no routing/price/state mutation from display rules.

Frontend tests:

- Menu Management item alias editor;
- Printing Settings dictionary editor;
- preview rendering state;
- validation errors;
- revision/hash/cache refresh;
- iPad-friendly layout.

Runtime Staging acceptance:

- St-Denis current behavior preserved under code-equivalent default rules;
- Owner can safely view and edit display rules;
- new orders use new rule revision;
- historical print job reprint remains safe;
- physical printers are not contacted unless a separate runtime gate is open.

## A11.9 True Owner Gates

Stop and ask before implementation if any of these are required:

- Production mutation;
- Production data read beyond already-approved safe evidence;
- schema/migration change without explicit A11 schema approval;
- physical printer binding or real printer contact;
- Pad pairing;
- security boundary weakening;
- exposing routing/state/payment logic to Owner-editable display rules;
- ambiguous product rule that cannot be represented safely.

## Non-goals

A11 does not implement:

- Phase B Store creation;
- Chinatown or Sainte-Catherine provisioning;
- physical printer endpoint setup;
- Pad pairing;
- Production promotion;
- arbitrary rule engine scripting;
- payment/tax/report recalculation.
