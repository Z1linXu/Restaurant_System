# Staging Synthetic/Test Fixture Cleanup Execution

This evidence records the Owner-authorized, bounded Staging fixture cleanup
executed on 2026-08-22. It supersedes no historical evidence and does not
authorize Production, real Store, real credential, Printer, Pad, device or
Phase C mutation.

## Runtime and scope

- Environment: isolated Staging project `restaurant-pos-staging` only.
- Exact deployed application SHA: `7bebe49f81ba45d151a4b8934a1d143c8925311d`.
- Flyway: `V26` (26 migrations validated and applied).
- Organization: Staging Organization `1`.
- Cleanup endpoint: `POST /api/v1/owner/organizations/1/staging/fixture-cleanup`.
- Production mutation: `NONE`; Production remains at the governed V10 boundary.
- Phase B Part 2 automated acceptance was not rerun or changed; Owner manual
  acceptance remains pending.

## Audited classification before cleanup

| Store ID | Code | Classification | Result |
| ---: | --- | --- | --- |
| 1 | `STG005_SRC_20260809_R01` | `KEEP_STG005`, `KEEP_REQUIRED_REFERENCE` | Retained and hard-protected |
| 2 | `A10_VALIDATION_STORE_A10_20260815_015431` | `DELETE_AUTOMATED_TEST` | Deleted |
| 3 | `A10_VALIDATION_STORE_20260815_020632_E907FB` | `DELETE_AUTOMATED_TEST` | Deleted |
| 4 | `A10_VALIDATION_STORE_20260815_020932_E680AF` | `DELETE_AUTOMATED_TEST` | Deleted |
| 5 | `A10_VALIDATION_STORE_20260815_021325_54D213` | `DELETE_AUTOMATED_TEST` | Deleted |
| 6 | `PHASE_B_VALIDATION_STORE_489E157_R1` | `DELETE_AUTOMATED_TEST` | Deleted |
| 7 | `PHASE_B_VALIDATION_STORE_A9BE0C8_R1` | `DELETE_AUTOMATED_TEST` | Deleted |
| 8 | `PHASE_B_VALIDATION_STORE_D08238A_R1` | `DELETE_AUTOMATED_TEST` | Deleted |
| 9 | `CHINATOWN` | `DELETE_OWNER_MANUAL_TEST` | Deleted; Owner-confirmed Staging test |
| 10 | `PHASE_B_VALIDATION_STORE_42B5A15_R1` | `DELETE_AUTOMATED_TEST` | Deleted |
| 11 | `PHASE_B_VALIDATION_STORE_96C81CF_R1` | `DELETE_AUTOMATED_TEST` | Deleted |
| 12 | `TESTAAA` | `DELETE_OWNER_MANUAL_TEST` | Deleted; Owner-confirmed Staging test |
| 13 | `PHASE_B_VALIDATION_STORE_SIZEOPT_9BEBAC8_R1` | `DELETE_AUTOMATED_TEST` | Deleted |
| 14 | `PHASE_B_VALIDATION_STORE_FINAL_20260822_R01` | `DELETE_AUTOMATED_TEST` | Deleted |
| 15 | `PHASE_B_VALIDATION_STORE_FINAL_20260822_R02` | `DELETE_AUTOMATED_TEST` | Deleted |
| 16 | `PHASE_B_VALIDATION_STORE_FINAL_20260822_R03` | `DELETE_AUTOMATED_TEST` | Deleted |
| 17 | `PHASE_B_VALIDATION_STORE_FINAL_20260822_R04` | `DELETE_AUTOMATED_TEST` | Deleted |

The decision was based on Store ID, Organization, lifecycle/provenance,
validation records and dependency checks. Store name alone was not used.
There were no `REVIEW_UNSAFE_OR_UNKNOWN` Stores in the approved target set.

## Reviewed cleanup path and dependency graph

The restricted service locks the target Store rows, validates the complete
direct `store_id` table inventory and direct foreign-key graph, rejects source
or target clone dependencies, rejects foreign Organization/Owner staff
dependencies and rejects shared inventory/prep dependencies. It then deletes
Store-local order, printing, combo/menu/BOM/inventory, table/station,
device/readiness, module/pricing/mapping, membership/credential and Store-root
rows in dependency order. It never deletes Chain Master Menu, Store Profile,
Master identity, shared authority or historical evidence. Historical
`owner_store_provisioning_requests` rows are preserved with their Store FK
detached to `NULL` before root deletion.

The V26 ledger, per-key advisory lock, ordered Store locks and request
fingerprint provide idempotent replay and changed-request conflict behavior.
The endpoint is separate from general Store CRUD and is Staging-gated;
Production and non-Staging runtime markers fail closed.

## Dry-run, execution and safety probes

The first dry-run request contained exactly Store IDs `2-17` and approved
manual IDs `9,12`. It returned `DRY_RUN_PASS`, all 16 rows were `READY`, Store
1 was absent, no delete counts were present, and all source/FK/staff/inventory
checks passed.

Evidence files are private Staging evidence files under mode `0600`:

| Evidence | SHA-256 |
| --- | --- |
| `phase-b-fixture-cleanup-dry-run-7bebe49f81ba45d151a4b8934a1d143c8925311d.json` | `49ae675d5800496e8de49efdd81b77ea49acb0f08c0d0894d1565960ef26a0a0` |
| `phase-b-fixture-cleanup-execute-7bebe49f81ba45d151a4b8934a1d143c8925311d.json` | `2e2f0311685f73f8f31f293ee00637ab3cd7512918196c12de97dcafa5fe08f2` |
| `phase-b-fixture-cleanup-replay-7bebe49f81ba45d151a4b8934a1d143c8925311d.json` | `eabb324bb4e6ad12b4eacac345bfcdf1303c9c89a83b9025522bf1a60c6d4bf7` |
| `phase-b-fixture-cleanup-store1-probe-7bebe49f81ba45d151a4b8934a1d143c8925311d.json` | `6647a9aff607a65ea9a6c0d020c98388a9814579b9cf0657b3a4ba4174d99699` |
| `phase-b-fixture-cleanup-changed-request-7bebe49f81ba45d151a4b8934a1d143c8925311d.json` | `f289ab72d51de4d26561c8345e380d4b8c64814f526ce31201b549104c6172a8` |

The execute response was `EXECUTED`; the same idempotency key returned
`REPLAYED` without additional side effects. A request containing protected
Store 1 returned HTTP 409 `STAGING_FIXTURE_CLEANUP_TARGET_REJECTED`. Reusing
the execute key with a changed target set returned HTTP 409
`STAGING_FIXTURE_CLEANUP_IDEMPOTENCY_CONFLICT`.

## Deleted Store-local dependent fixture counts

The transactional execute response reported:

| Table | Deleted rows | Table | Deleted rows |
| --- | ---: | --- | ---: |
| `analytics_alerts` | 2 | `order_item_options` | 7 |
| `kitchen_tasks` | 3 | `production_tasks` | 3 |
| `order_dispatch_outbox` | 8 | `order_submission_requests` | 1 |
| `print_job_attempts` | 10 | `print_jobs` | 11 |
| `order_items` | 3 | `orders` | 3 |
| `store_logical_printer_roles` | 22 | `printer_assignments` | 6 |
| `printer_configs` | 6 | `printing_display_rule_revisions` | 22 |
| `printing_display_rule_sets` | 16 | `store_combo_components` | 63 |
| `store_combo_groups` | 27 | `menu_item_sales_summary` | 1 |
| `menu_item_options` | 4571 | `menu_items` | 480 |
| `menu_categories` | 76 | `store_menu_master_mappings` | 5109 |
| `store_modules` | 165 | `store_pricing_policies` | 15 |
| `store_device_readiness` | 11 | `store_devices` | 11 |
| `sales_daily_summary` | 60 | `sales_hourly_summary` | 1440 |
| `store_performance_summary` | 60 | `dining_tables` | 25 |
| `store_memberships` | 22 | `refresh_tokens` | 2 |
| `user_credentials` | 24 | `organization_memberships` | 22 |
| `users` | 24 | `stations` | 62 |
| `store_root` | 16 | | |

Preserved sanitized evidence counts were: `owner_store_provisioning_requests`
12 (detached), `store_readiness_evidence` 12,
`store_provisioning_part2_requests` 20, `store_provisioning_resources` 33,
`store_readiness_evidence_history` 58, `store_activation_requests` 5 and
`audit_logs` 63. No historical evidence row was deleted.

## Post-cleanup verification

- Final Store inventory: exactly Store 1,
  `STG005_SRC_20260809_R01`, `active` / `ACTIVE` / `BUSINESS` /
  `LEGACY_EXISTING_STORE` in Organization 1.
- Owner Dashboard `/api/v1/owner/overview`: HTTP 200; one Organization and
  exactly one Store, Store 1.
- All inspected direct Store-local residual counts for Store IDs 2-17 are
  zero except preserved audit/provisioning/readiness/activation evidence.
- Store 1 integrity: 13 dining tables, 6 categories, 39 menu items, 11
  modules, 1 pricing policy, 5 stations, 7 devices, 4 printer configs, 3
  printer assignments and 1 Store membership remain.
- Shared Chain Master counts remain: 1 master menu, 1 version, 6 categories,
  39 products and 380 options.
- Shared Store Profile counts remain: 1 profile, 2 versions and 25 artifacts.
- Organization isolation: zero Stores outside Organization 1 and zero
  cross-Organization Store menu mappings.
- Staging loopback health: `PASS`.
- Post-cleanup readiness: `PASS`; project `restaurant-pos-staging`.
- Runtime evidence: exact container/image identity, Flyway 26/26 and runtime
  validation `PASS`.
- Production fingerprint was unchanged by the pre/post passive checks; no
  Production deployment, restart, migration, credential, Store, Printer, Pad
  or device mutation was performed.

## Repository and validation

- Cleanup implementation PR: `#193`, merged at
  `7bebe49f81ba45d151a4b8934a1d143c8925311d`.
- Agent 6 final static review: `PASS` on the merged implementation.
- Focused cleanup tests: `PASS`.
- Full backend Maven test suite: `PASS`.
- Production/staging tooling guard tests and shell syntax validation: `PASS`.
- Historical audit `PHASE_B_STAGING_STORE_CLEANUP_EVIDENCE.md` remains
  unchanged; this execution record is additive.
