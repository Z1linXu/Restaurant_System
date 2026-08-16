# Current Chain Menu Readiness Audit

## Status

```text
PHASE_A11_5_CHAIN_MENU_READINESS_AUDIT = PASS_WITH_IMPLEMENTATION_GAPS
REPOSITORY_AUTHORITY = origin/main@0de03c773ef04594e7d737c6bccdf6f607692eca
RUNTIME_ACTION = NOT_PERFORMED
PRODUCTION = NO_MUTATION
```

This audit is read-only and design-only. It uses repository authority and
checked-in runtime evidence. It does not inspect or mutate live Production,
deploy Staging, restart services, run Flyway or begin Phase B.

Current note: this file is the pre-implementation A11.5 readiness audit. The
Owner has since granted Phase B Part 1 implementation authority, and the
repository implementation that closes the listed gaps is recorded in
[PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md).

## Recovered authority

| Item | Current authority |
| --- | --- |
| Latest `origin/main` | `0de03c773ef04594e7d737c6bccdf6f607692eca` |
| Latest main commit | `0de03c7 Fix A11 print job fingerprint schema mapping (#160)` |
| Working branch for A11.5 docs | `codex/phase-a11-5-chain-master-menu-design` |
| Stable Staging identity in repository evidence | `ad4572759e01b5546ec59af24aa36b09e5c2dd00`, Flyway `V16` |
| A11 runtime evidence caveat | PR #159 SHA `9c5bc05912e565c0c4e8cb1b82eae88d15d0fa0a` applied V17 but failed startup; `0de03c7` repairs the mapping. Repository evidence does not contain a fresh machine-generated redeploy proof for `0de03c7`. |
| A11 Owner verdict | `PHASE_A11_OWNER_ACCEPTANCE = PASS`, Owner-declared on 2026-08-15. Do not rerun A11 manual acceptance. |
| Production identity tracked by authority docs | `RC-THREE-RELIABILITY-20260812-3EC4D88`, app SHA `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`, Flyway `V10`, `PAD_DIRECT` retained. |

## Current implemented assets

| Area | Current implemented state | A11.5 implication |
| --- | --- | --- |
| Organization and Store | `stores.organization_id` links Stores to Organizations. Store code/name/status/runtime flags remain Store-local. | Chain Master Menu must be Organization-scoped, not Store-scoped. |
| Store Profile contract | V14 provides versioned `store_profiles`, `store_profile_versions`, `store_profile_artifacts`, fingerprints and immutable reviewed/ready artifacts. | Reusable foundation for templates and provenance. It is not a Store materializer. |
| St-Denis canonical profile | V15 seeds `ST_DENIS_CANONICAL_PROFILE/v1` with menu, pricing, combo, tables, stations, logical printing topology, role defaults and hardware requirements. | This is the reviewed source artifact to canonicalize into `LANZHOU_CHAIN_MASTER_MENU/v1`; it must not create a live link to St-Denis. |
| Menu configuration | Store-local categories/items/options/stations have codes, SKUs, sort order, active flags and Store-local DB IDs. | Useful for Store materialization, but DB IDs are not master identity. |
| Pricing | `store_pricing_policies` is the Store source of truth for Size/Combo deltas; `menu_items.base_price` remains Store-local. | Master price is reference/default only; Store actual price remains Store-owned. |
| Combo | `store_combo_groups` and `store_combo_components` are Store-scoped. | Profile/Master may seed defaults; each Store owns combo state after materialization. |
| Printing display rules | V17 adds Store-owned versioned display rule sets and captures rule revision/fingerprint on `print_jobs`. Rules use `item_sku` aliases and semantic dictionaries. | Strong reusable boundary for Master product identity and Store-owned post-materialization rules. |
| Reporting | Current summaries use Store-local `menu_item_id` plus names and Organization/Store IDs. | Cross-Store product reporting needs future master identity mapping. |
| Materialization | Current `StoreProfileMaterializationDryRunValidator` validates graph shape only. | Phase B still needs a real writer/provisioning transaction. |

## Reusable foundations

- A4 Profile versioning, artifact fingerprinting and immutability.
- A5 St-Denis reviewed canonical configuration graph.
- A5.5 menu configurability for categories, stations and combo groups.
- A0.1 Store pricing policy source of truth.
- A8 hardware capability/profile boundary.
- A9 legacy compatibility ledger and disabled direct active Store creation.
- A11 Store-owned printing display rule revisions and `item_sku` rule aliases.
- Store menu revision/hash behavior for Store-local cache invalidation.

## Implementation gaps before Phase B code

- No `chain_master_menu` persisted model exists.
- No Organization-scoped immutable Master Menu version exists.
- No master category/product/option identity mapping exists on live menu rows or
  a separate mapping table.
- No real Store materialization writer exists for Profile plus Master Menu.
- No Store-local override state model exists for master-derived rows.
- Current catalog hash includes Store-local DB IDs, so it is not a Master Menu
  version fingerprint.
- Current reports cannot aggregate by master product identity.
- V14 `store_profile_artifacts.artifact_type` whitelist does not include
  `PRINTING_DISPLAY_RULES`; V17 adds A11 rule tables but does not update that
  profile artifact whitelist. A future additive migration must close this
  before post-A11 profiles can persist that artifact type in DB.

## Drift and inconsistency findings

1. Governance before this update may still record
   `PHASE_A11_OWNER_ACCEPTANCE = PENDING`. Owner has now declared PASS. The
   repository is being synchronized to that verdict without rerunning A11.
2. Checked-in runtime evidence still names stable Staging `ad457...` / V16 as
   the last fully evidenced stable runtime, while A11 implementation evidence
   records a V17 migration attempt and startup repair. Treat `0de03c7` as latest
   code authority, not as freshly redeployed runtime evidence unless a later
   runtime artifact is added.
3. A11 docs require future profiles to include `PRINTING_DISPLAY_RULES`, but
   the V14 artifact type check constraint has not yet been expanded. This is a
   schema follow-up, not a reason to rewrite historical
   `ST_DENIS_CANONICAL_PROFILE/v1`.

## Readiness conclusion

```text
CURRENT_CHAIN_MENU_READINESS_AUDIT = COMPLETE
CHAIN_MASTER_MENU_DESIGN_CAN_PROCEED = YES
PHASE_B_IMPLEMENTATION_CAN_START = NO
```

Historical conclusion note: `PHASE_B_IMPLEMENTATION_CAN_START = NO` was true
for the A11.5 design/audit loop before Owner implementation authorization. The
current Part 1 repository implementation state is tracked separately in
[PHASE_B_PART1_IMPLEMENTATION_EVIDENCE](PHASE_B_PART1_IMPLEMENTATION_EVIDENCE.md).
