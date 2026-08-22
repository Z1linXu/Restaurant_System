# Staging Store Cleanup Audit

This is the current evidence record for the Owner-authorized Staging Store
cleanup audit on 2026-08-22. It records the exact isolated Staging database,
Owner Dashboard inventory, dependency classification and the fail-closed
result. It does not authorize Production, real Store, credential, Printer,
Pad or Phase C mutation.

## Scope and runtime

- Environment: isolated Staging project `restaurant-pos-staging` only.
- Application SHA: `72fecac3a17e0ac40d6207f4c501eb0308210123`.
- Flyway: `V25`.
- Organization: synthetic Staging Organization `1`.
- Production: no mutation and no Production database command.
- Phase B Part 2 Owner manual acceptance gate: unchanged and still pending.
- Phase B automated acceptance: not rerun.

The inventory was checked through the current Owner Dashboard API and a
read-only query against the Staging PostgreSQL container. Both showed the same
17 Stores below. Store-local dependency counts and source/reference rows were
also inspected without changing data.

## Classification before cleanup

| Store ID | Code | Classification | Evidence-based reason |
| ---: | --- | --- | --- |
| 1 | `STG005_SRC_20260809_R01` | `KEEP_STG005` and `KEEP_REQUIRED_REFERENCE` | Active synthetic STG005 baseline; `staging_synthetic_bootstrap_requests` records `source_store_id=1`. |
| 2 | `A10_VALIDATION_STORE_A10_20260815_015431` | `DELETE_AUTOMATED_TEST` | `status=A10_VALIDATION_INACTIVE`, legacy validation date, no Part 1/Part 2 provisioning records and no source/reference dependency. |
| 3 | `A10_VALIDATION_STORE_20260815_020632_E907FB` | `DELETE_AUTOMATED_TEST` | Same A10 validation status and legacy-only provenance; no source/reference dependency. |
| 4 | `A10_VALIDATION_STORE_20260815_020932_E680AF` | `DELETE_AUTOMATED_TEST` | Same A10 validation status and legacy-only provenance; old Store-local test rows, but no source/reference dependency. |
| 5 | `A10_VALIDATION_STORE_20260815_021325_54D213` | `DELETE_AUTOMATED_TEST` | Same A10 validation status and legacy-only provenance; old Store-local test rows, but no source/reference dependency. |
| 6 | `PHASE_B_VALIDATION_STORE_489E157_R1` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; automated onboarding and Part 2 provisioning records. |
| 7 | `PHASE_B_VALIDATION_STORE_A9BE0C8_R1` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; automated onboarding and Part 2 provisioning records. |
| 8 | `PHASE_B_VALIDATION_STORE_D08238A_R1` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; automated onboarding and Part 2 provisioning records. |
| 9 | `CHINATOWN` | `DELETE_OWNER_MANUAL_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`, onboarding record, outside the current automated `PHASE_B_VALIDATION_STORE_` target namespace, and no source/reference dependency. Exact human-vs-older-tool creator identity is not persisted. |
| 10 | `PHASE_B_VALIDATION_STORE_42B5A15_R1` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; automated onboarding and Part 2 provisioning records. |
| 11 | `PHASE_B_VALIDATION_STORE_96C81CF_R1` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; automated onboarding and Part 2 provisioning records. |
| 12 | `TESTAAA` | `DELETE_OWNER_MANUAL_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`, onboarding record, outside the current automated target namespace, and no source/reference dependency. Exact human-vs-older-tool creator identity is not persisted. |
| 13 | `PHASE_B_VALIDATION_STORE_SIZEOPT_9BEBAC8_R1` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; automated onboarding and Part 2 provisioning records. |
| 14 | `PHASE_B_VALIDATION_STORE_FINAL_20260822_R01` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; automated onboarding and Part 2 provisioning records. |
| 15 | `PHASE_B_VALIDATION_STORE_FINAL_20260822_R02` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; failed/repair acceptance fixture with Part 2 ledger rows. |
| 16 | `PHASE_B_VALIDATION_STORE_FINAL_20260822_R03` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; failed/repair acceptance fixture with Part 2 ledger rows. |
| 17 | `PHASE_B_VALIDATION_STORE_FINAL_20260822_R04` | `DELETE_AUTOMATED_TEST` | `VALIDATION_FIXTURE` + `PHASE_B_OWNER_PROVISIONING`; final synthetic Part 2 acceptance fixture, currently `ACTIVE`/`MOCK`. |

No Store was classified `REVIEW_UNSAFE_OR_UNKNOWN` because the available
records were sufficient to establish that Stores 2-17 are disposable test
fixtures and that Store 1 is the retained source/reference. This classification
does not itself authorize an unsafe destructive operation.

## Dependency and safety findings

- Store 1 is the only `source_store_id` found in the current
  `staging_synthetic_bootstrap_requests` records; no current
  `restaurant_templates` row or menu-clone request references another Store
  as a source.
- The Chain Master Menu remains the shared Organization-owned
  `LANZHOU_CHAIN_MASTER_MENU/v1`; its persisted published graph contains 6
  categories, 39 products and 380 options.
- The Store Profile authority remains `ST_DENIS_CANONICAL_PROFILE`, with its
  two profile versions and 25 profile artifacts intact.
- Stores 6-17 have Store-local master mappings and Store-local materialized
  menu rows; those rows are dependencies a future reviewed decommission path
  must handle transactionally without touching shared Master/Profile records.
- All inspected Stores belong to Organization `1`; no cross-Organization Store
  mapping was found.
- No physical Printer binding or real Pad binding was performed by this audit.

## Reviewed cleanup path result

The repository was searched for the current Store deletion/decommission path.
`PlatformAdminController` exposes Store GET/POST/PUT operations only; the
Owner Store controllers expose onboarding, menu clone and Part 2 provisioning/
activation, but no Store delete/decommission operation. No Store deletion
service/repository path or reviewed Staging Store fixture cleanup tool exists.
The existing acceptance cleanup code only removes a scoped one-shot container;
the Part 2 runbook explicitly describes database Store cleanup as a separate
future reviewed reconciliation.

Therefore the cleanup action failed closed:

- Reviewed cleanup path used: none exists; no substitute path was executed.
- Ad-hoc SQL DELETE: not executed.
- FK/constraint bypass: not executed.
- Store API mutation: not executed.
- Historical evidence deletion: not executed.
- Stores actually deleted: none (`0`).
- Disposable test Stores remaining: Stores `2-17` (`16`).

This means the requested clean Dashboard state could not be reached safely in
this run. The 16 disposable Stores require a future Owner-authorized reviewed
decommission/fixture cleanup capability before deletion can proceed.

## Post-audit verification

- `STG005_SRC_20260809_R01` / Store `1`: still present and accessible.
- Owner Dashboard inventory: still 17 Stores, matching the database inventory;
  no deleted Store was falsely reported as removed.
- Chain Master Menu: unchanged and complete.
- Store Profile: unchanged and complete.
- Master mappings: unchanged; Store-local mappings remain Store-scoped.
- Staging loopback health: `PASS`.
- Passive Staging host/runtime readiness: `PASS`, project
  `restaurant-pos-staging`, application SHA
  `72fecac3a17e0ac40d6207f4c501eb0308210123`.
- Production mutation: `NONE`.
- Phase B Part 2 Owner manual acceptance gate: unchanged,
  `PHASE_B_PART2_OWNER_MANUAL_ACCEPTANCE = PENDING`.
