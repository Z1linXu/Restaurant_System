# Phase 3 printing and kitchen compatibility boundary

Supporting implementation record; CURRENT_STATE.yml remains the only current Phase authority.

## Source and scope

`MerchantConfigurationService.isLanzhouCompatible(storeId)` selects presentation behavior. The V39 snapshot of verified ST_DENIS_CANONICAL_PROFILE@v2 Stores and explicitly marked legacy installations preserves their behavior as PROVISIONAL_COMPATIBILITY. Missing, unsupported or invalid configuration selects Neutral behavior. A tenant cannot enable compatibility through printing JSON or Merchant Configuration PUT. Existing Profile code/version/fingerprint and the legacy printing JSON/fingerprint remain unchanged; LANZHOU_NOODLE is a business/fixture alias only.

Runtime PrintingDisplayRuleService chooses an empty Neutral dictionary for missing rules, initial drafts and preview defaults. Its settings projection supports an unconfigured Store without creating database rows, printers, assignments or a hardware readiness loop. Owner-created aliases still refer to that Store's current menu vocabulary. Profile default JSON cannot override authorization, dispatch targets, endpoints, claims, leases or amounts.

Order submission freezes complete option labels for Neutral tasks and does not infer abbreviated names from colliding legacy SKUs. Neutral GRAB/HOT rendering reads complete frozen item/task labels, without noodle grouping, special egg inference or legacy modifier abbreviations. Neutral frontdesk receipts keep all item/option labels and do not hide zero-price combo children. Shared quantity, money, dispatch, lease and persisted payload behavior stays in the existing paths. Existing rendered PrintJob payloads are never recomputed by configuration updates.

## Deliberately bounded compatibility

Existing station/output roles remain the current printing API contract: GRAB station selection and WOK/DEEPFRIED HOT station selection are provisional. Neutral HOT does not infer eligibility from cuisine SKU/category/egg text. Arbitrary station-to-output roles and cuisine-specific route/renderer extraction belong to P5-KITCHEN-01/P5-PRINT-02. Generic Modifier/Combo and pricing validation belongs to Phase4. No claim of universal cuisine, arbitrary printer support or physical printing certification follows from these adapters.

Canonical Add-on identity, Store price and per-item eligibility remain the existing three-level source; the new configuration layer does not copy definitions or introduce per-printer menu identity. StoreComboConfigurationService already reads Store-owned persisted groups/components and has no implicit five-item fallback; Neutral initialization must leave those rows empty. The old enum/synthetic-option compatibility remains explicitly deferred to P4-COMBO-03 with current server validation intact.

## Verification contract

Focused tests cover an empty Neutral dictionary and settings/preview, colliding legacy SKU and ingredient codes with customer-defined full labels, neutral frontdesk zero-price children, no cuisine-derived HOT eligibility, explicit local aliases, and an actual submitted Neutral order task. Existing Lanzhou renderer/golden, order, Add-on and rule lifecycle suites remain required regression. Integrated counts, migration/history/scope proof and independent Agent6 review are recorded in the final Phase3 acceptance report; this design file alone is not PASS evidence.

## Recovery

Do not roll back into a global restaurant dictionary for unknown Stores. Keep configuration columns and use an additive forward repair. Disable optional printing while repairing an unsupported configuration. Preserve old rule revisions, Order/Kitchen snapshots, PrintJob payloads, DB5 queues and idempotency/claim identities; do not replay physical ambiguous jobs. No runtime migration, printer binding or physical print was performed by this local implementation.
