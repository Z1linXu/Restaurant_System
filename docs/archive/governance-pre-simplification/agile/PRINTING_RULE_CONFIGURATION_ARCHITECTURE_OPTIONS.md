# Printing Rule Configuration Architecture Options

Status:

```text
PHASE_A11_PRINTING_RULE_CONFIGURATION_ARCHITECTURE_OPTIONS = OPTION_A_HYBRID_ACCEPTED
A11_OWNER_5_ANSWERS = CLOSED
IMPLEMENTATION = ACCEPTED_BY_OWNER
PHASE_A11_OWNER_ACCEPTANCE = PASS
```

Latest Owner acceptance supersedes the original repository-candidate status.
Do not rerun A11 manual acceptance in the A11.5 loop.

Owner decision closure:

- Use a hybrid UI: item-specific aliases in Menu Management and Store-global
  dictionaries/rules/history/preview in Printing Settings.
- Use independent output aliases with menu-name fallback for `GRAB`,
  `FRONTDESK_RECEIPT` and `HOT_KITCHEN`.
- Use Store-global dictionaries plus item override.
- Freeze historical rule revision/rendered evidence for reprint safety.
- Use structured fields and constrained conditions only; reject scripts,
  arbitrary regex and executable/raw templates.

The implementation candidate follows Option A with a profile artifact shape
borrowed from Option B, without introducing a general rule engine.

## Required A11 contract

Canonical Printing Rule Configuration must become part of the Phase A modular
architecture, not a St-Denis-specific customization.

Required properties:

- Store-scoped;
- profile-compatible;
- versionable and snapshot-safe;
- reusable by Phase B provisioning;
- independent after Store materialization;
- safe to include as `PRINTING_DISPLAY_RULES` defaults/snapshot in a future
  `ST_DENIS_CANONICAL_PROFILE` version;
- separated from physical printer endpoints, Pad pairing, device secrets,
  PrintJob state, routing authorization and printer assignment.

Disallowed properties:

- no live link to a source Store;
- no copied source Store database IDs;
- no `if St-Denis`, `if Chinatown`, `if Sainte-Catherine` shared-code branch;
- no Owner-authored JavaScript, regex or executable template code;
- no rule that can change order amount, payment, route authorization or PrintJob
  state machine.

## Option A — Structured Printing Dictionary + Item Alias

Store owns a versioned structured dictionary with typed rule families:

- item aliases per output:
  - `GRAB`
  - `FRONTDESK_RECEIPT`
  - `HOT_KITCHEN`
- Store-global display dictionaries:
  - Size wording;
  - noodle type wording;
  - spiciness wording;
  - add/remove modifier wording;
  - table-side wording;
  - combo wording;
  - quantity symbol/copy wording from a small controlled enum.
- item-specific overrides:
  - default option omission;
  - alias override;
  - output-specific special wording.

Precedence:

```text
1. order/print job frozen rendered snapshot, when reprinting a historical job
2. order item / option / kitchen task snapshot values
3. item-specific output alias
4. item-specific dictionary override
5. Store-global output dictionary
6. canonical system fallback from menu/option snapshot
7. fail-closed validation for missing required display fields
```

Profile materialization:

```text
Profile PRINTING_DISPLAY_RULES artifact
→ materialize target Store rule set revision 1
→ target Store owns independent rows/config
→ profile updates do not mutate existing Stores
```

Tradeoffs:

| Dimension | Assessment |
| --- | --- |
| complexity | Medium. Requires new schema/API/UI, but no general rule engine. |
| Owner usability | High. Owner edits recognizable fields and dictionaries. |
| Store scalability | High. Store-owned rule set is independent after materialization. |
| Profile compatibility | High. Can be serialized as profile artifact defaults. |
| migration risk | Medium. Must backfill defaults from current hardcoded behavior and fail closed on conflicts. |
| historical safety | High if rule revision is captured at new order/print-job render time. |
| testing complexity | Medium. Golden renderer fixtures per output + revision snapshot tests. |
| Phase B suitability | High. Phase B can materialize rules together with menu, topology and hardware readiness. |

## Option B — Versioned Printing Rule Profile / Rule Set

Store owns a full versioned rule-set document. The application resolves all
display rules from the active document version using a strict schema.

Rule set examples:

- output-specific item aliases;
- output-specific dictionaries;
- component display rules;
- quantity and formatting styles;
- controlled condition keys such as `output_type`, `item_ref`,
  `option_code`, `option_group`, `canonical_size`, `station_type`.

This option still forbids executable code and arbitrary regex. It is closer to
a rule engine, but with a constrained JSON schema.

Precedence:

```text
1. frozen rendered print job
2. frozen submitted order/rule revision
3. exact item/output rule
4. option/output rule
5. dictionary/output rule
6. default output rule
7. fail closed
```

Tradeoffs:

| Dimension | Assessment |
| --- | --- |
| complexity | High. Needs a rules resolver, validator, preview engine and authoring UI. |
| Owner usability | Medium. Powerful, but easier to make confusing. |
| Store scalability | High if schema remains stable. |
| Profile compatibility | High. A profile can include the whole versioned rule document. |
| migration risk | Medium-high. More moving parts in backfill and regression. |
| historical safety | High if each order or print job records rule-set revision. |
| testing complexity | High. Needs matrix tests for precedence and invalid combinations. |
| Phase B suitability | High, but risks delaying Store provisioning because A11 becomes a mini-rule-engine project. |

## Option C — Add print alias fields directly to menu/options

Extend menu item and option configuration with display fields such as:

- `grab_alias`;
- `frontdesk_alias`;
- `hot_kitchen_alias`;
- maybe `short_code`.

Tradeoffs:

| Dimension | Assessment |
| --- | --- |
| complexity | Low-medium. Easy for product aliases. |
| Owner usability | Medium. Works for item names but scatters global dictionaries. |
| Store scalability | Medium. Store-scoped because menu/options are Store-scoped, but harder to share as profile defaults cleanly. |
| Profile compatibility | Medium. Can be exported, but display dictionaries become embedded across many rows. |
| migration risk | Low for item aliases, high for Size/noodle/modifier/quantity rules because they do not belong naturally on one item row. |
| historical safety | Medium. Must still capture rule revision or snapshot. |
| testing complexity | Medium. Fewer schema objects, but more coupling to menu editing. |
| Phase B suitability | Medium-low. Phase B would need to clone/distribute alias fields and still lacks global display-rule defaults. |

## Recommendation

Recommended architecture:

```text
Option A as the A11 implementation baseline,
with a profile artifact shape borrowed from Option B,
and without Option B's advanced conditional rule engine.
```

Rationale:

- It solves the actual audit finding: most problematic rules are display
  dictionaries and product aliases, not arbitrary business logic.
- It keeps Owner UI understandable.
- It supports `ST_DENIS_CANONICAL_PROFILE` carrying
  `PRINTING_DISPLAY_RULES` defaults without live-linking to Store 1.
- It lets Phase B validate:

```text
Printing Rule Configuration
+
Logical Printing Topology
+
Hardware Capability Readiness
```

- It preserves safety: routing, queueing, PrintJob state, hardware binding and
  payment/tax/order logic remain code-owned.

## Implemented canonical target model

Additive Flyway V17 implements the following Store-scoped model:

```text
printing_display_rule_sets
  store_id
  active_revision_id
  status
  created_at / updated_at

printing_display_rule_revisions
  rule_set_id
  revision
  fingerprint_sha256
  created_by / published_at
  created_at
  summary
  content_json
```

`content_json` is validated against a strict structured schema:

```text
{
  "schema_version": "PRINTING_DISPLAY_RULES_V1",
  "outputs": ["GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN"],
  "item_aliases": [],
  "dictionaries": {
    "sizes": [],
    "noodle_types": [],
    "spiciness": [],
    "modifiers": [],
    "combo": [],
    "table_labels": []
  },
  "formatting": {
    "quantity": [],
    "labels": []
  }
}
```

Validation must reject:

- blank required aliases;
- duplicate ambiguous aliases within the same output and semantic key;
- unknown output type;
- unknown item/profile reference;
- unknown canonical option code where a stable code is required;
- executable code, regex, HTML, JavaScript or raw ESC/POS commands;
- attempts to set printer IDs, endpoints, device IDs, credentials, order
  amounts, route targets or PrintJob statuses.

## Historical and snapshot safety

Current state:

- submitted orders freeze item/option/kitchen-task snapshots;
- PrintJob rows freeze `rendered_text_snapshot`;
- single PrintJob reprint uses the frozen rendered snapshot when present;
- order-level reprint re-renders the full current order from order snapshots
  through the current renderer.

Implemented A11 target:

```text
new order submit/update
→ resolve active Store printing rule revision
→ render using that revision
→ store print_jobs.rendered_text_snapshot
→ store printing_rule_revision_id / printing_rule_fingerprint on the print job
```

Historical behavior uses `FROZEN_RULE_REPRINT`: single-job reprint still prefers
the frozen rendered snapshot; order-level reprint resolves the historical
captured rule revision where available and falls back to the active compatible
context only for pre-A11 jobs without captured A11 metadata.

## UI location comparison

| Location | Strength | Weakness |
| --- | --- | --- |
| Menu Management → Menu Item → Printing Rules | Natural for product aliases and item-specific defaults. | Poor for Store-global dictionaries such as Size/noodle/spicy/table labels. |
| Printing Settings → Display / Naming Rules | Natural for Store-wide print output behavior and preview. | Less convenient when editing a specific item. |
| Hybrid | Lets item aliases live with menu items and dictionaries live with Print Settings. | Requires cross-linking and careful navigation. |

Owner-accepted UI:

```text
Hybrid:
  Menu Management owns item-specific aliases/overrides.
  Printing Settings owns Store-global Display/Naming dictionaries and previews.
```

## Phase B impact

Phase B Create New Store must not start implementation while A11 is unresolved.
When A11 is accepted, Phase B provisioning must materialize:

```text
Store Profile
→ Store-owned menu/categories/options/combo/pricing/tables/staff defaults
→ Store-owned PRINTING_DISPLAY_RULES revision 1
→ Store-owned logical printers/assignments
→ hardware readiness checks
```

The provisioned target Store must own independent Printing Display Rule
Configuration. Later edits in the source profile or source Store must not alter
the target Store unless a separately reviewed profile-update workflow is
approved.
