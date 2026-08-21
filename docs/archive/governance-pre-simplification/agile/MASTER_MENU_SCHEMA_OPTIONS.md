# Master Menu Schema Options

## Decision

Recommended option:

```text
OPTION_C_HYBRID_NORMALIZED_IDENTITY_PLUS_CANONICAL_ARTIFACT = SELECTED
```

This decision was made for future Phase B implementation during A11.5, which
added no migration. The Owner has since granted Phase B Part 1 implementation
authority, and the repository implementation of Option C is recorded in
[PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md).

## Option A - dedicated normalized Master Menu tables

Description: create normalized tables for master menus, versions, categories,
products, option groups, options and relationships.

Strengths:

- strongest relational integrity;
- easiest future joins for reporting and diff;
- clear uniqueness and Organization scope.

Weaknesses:

- large schema surface before the product is fully stable;
- higher migration and mapping complexity;
- harder to store full Profile provenance and future menu nuances without
  adding many tables early.

Disposition:

```text
VALID_BUT_TOO_HEAVY_FOR_PHASE_B_V1
```

## Option B - versioned artifact/JSON only

Description: store the complete Master Menu as a canonical JSON artifact with
fingerprint and lifecycle, with no normalized identity projection.

Strengths:

- fast to implement;
- flexible for menu graph changes;
- similar to Store Profile artifacts.

Weaknesses:

- weak relational joins for Store mappings and reports;
- harder to enforce uniqueness and immutable key rules;
- future diff/apply and cross-Store reporting become more ad hoc.

Disposition:

```text
VALID_FOR_PROTOTYPE_ONLY
```

## Option C - hybrid

Description: store Master Menu lifecycle/version/provenance and stable identity
projection in normalized rows, while preserving the full canonical graph in a
version artifact.

Strengths:

- keeps Organization scope and stable identity enforceable;
- supports future materialization mapping, reporting and diff;
- preserves full canonical graph/fingerprint provenance;
- avoids over-normalizing every menu nuance before Phase B v1.

Weaknesses:

- requires careful canonical artifact and projection consistency checks;
- still needs an additive migration and validator in Phase B.

Disposition:

```text
SELECTED_FOR_PHASE_B_DESIGN
```

## Future schema sketch

Names are illustrative and not implementation approval:

- `chain_master_menus`
- `chain_master_menu_versions`
- `chain_master_menu_artifacts`
- `chain_master_menu_categories`
- `chain_master_menu_products`
- `chain_master_menu_options`
- `store_menu_master_mappings` or equivalent columns/mapping tables
- `store_menu_local_overrides` or equivalent override state projection

The future migration must also address the A11 profile artifact gap by allowing
`PRINTING_DISPLAY_RULES` as a valid `store_profile_artifacts.artifact_type`
before post-A11 profile versions persist that artifact.

## Non-negotiables

- Additive migration only.
- Published Master versions immutable.
- No source Store DB IDs in Master or Profile artifacts.
- No Production data read/copy.
- No physical printer endpoints, Pad/device credentials or secrets.
- Existing Store runtime behavior preserved.
