# Phase A5 St-Denis Canonical Profile

Status: `PHASE_A5_REPOSITORY_IMPLEMENTATION_AGENT_6_ACCEPTED_FOR_PR_MERGE`

Date: 2026-08-13

Fresh repository authority:

```text
origin/main@be14923c96098d80b1b841e2ba0edbe3ca2563a5
```

A4 Store Profile Contract is in `main` through PR #142:

```text
PHASE_A4_STORE_PROFILE_CONTRACT = PASS
PR = https://github.com/Z1linXu/Restaurant_System/pull/142
merge = be14923c96098d80b1b841e2ba0edbe3ca2563a5
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging before A5 deploy: `c1b5e7681f24a11fbf99293567b3da08076fa3b6`,
  Flyway V13
- A5 repository package adds Flyway V15 profile seed data; exact-SHA Staging
  deploy/Flyway validation is required after PR merge before A5 runtime PASS
- No Store materialization, Store activation, Owner Create New Store,
  Chinatown, Sainte-Catherine, A6, Phase B/C or Production action is included

## Profile identity

```text
profile_code = ST_DENIS_CANONICAL_PROFILE
profile_version = v1
schema_version = STORE_PROFILE_CONTRACT_V1
profile_fingerprint_sha256 = af1a8f34cd156c1987b74ec1a9a22ddfd004859c617937b7d53f05e16e762602
source_manifest = ST_DENIS_TWIN_PARITY_MANIFEST_V2
source_manifest_fingerprint_sha256 = 1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68
```

Implementation:

```text
backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql
backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileMaterializationDryRunValidator.java
backend/src/test/java/com/restaurant/system/owner/profile/StDenisCanonicalProfileContractTest.java
```

## Versioned artifacts

V15 seeds a database-backed A4 Store Profile, Version and Artifact set:

```text
store_profiles
store_profile_versions
store_profile_artifacts
```

The Version is inserted as `DRAFT`, all artifacts are inserted while mutable,
then the Version is updated to `READY`. A4 immutable triggers therefore protect
the final reviewed profile content and artifacts.

Artifacts:

- `MODULE_DEFAULTS`
- `MENU_TEMPLATE`
- `PRICING_POLICY`
- `COMBO_CONFIGURATION`
- `TABLE_TEMPLATE`
- `STATION_TEMPLATE`
- `LOGICAL_PRINTING_TOPOLOGY` / artifact code `PRINTING_TOPOLOGY`
- `ROLE_ACCESS_DEFAULTS`
- `HARDWARE_REQUIREMENTS`
- `DEVICE_CAPABILITY_REQUIREMENTS`
- `OPERATIONAL_SETTINGS`
- `FEATURE_DEFAULTS`

## Deterministic graph counts

```text
categories = 6
items = 39
options = 380
parent_option_relationships = 11
tables = 13
stations = 5
logical_printers = 4
printer_assignments = 3
combo_components = 5
staff_templates = 4
device_slots = 7
```

## Domain parity classification

| Domain | A5 classification |
|---|---|
| MODULES | `MATCH` |
| MENU | `MATCH` |
| PRICING | `MATCH_WITH_DERIVED_REPOSITORY_DEFAULT_FOR_DISABLED_SMALL_SIZE` |
| COMBO | `MATCH` |
| TABLES | `MATCH` |
| STATIONS | `MATCH` |
| PRINTING TOPOLOGY | `MATCH_LOGICAL_TOPOLOGY_ONLY` |
| ROLE DEFAULTS | `MATCH_SAFE_USERNAME_ROLE_ONLY` |
| HARDWARE REQUIREMENTS | `MATCH_CAPABILITY_CONTRACT_ONLY` |
| OPERATIONAL SETTINGS | `MATCH_SAFE_SETTINGS` |

Expected differences:

- new target Store surrogate IDs
- new category/item/option/station/table/printer/device surrogate IDs
- independent login material
- independent physical printer binding
- independent device pairing
- runtime print mode may differ by environment

Blocking differences:

```text
BLOCKING_DIFFERENCE = 0
```

## Mapping and materialization contract

The profile uses profile-local refs such as `CAT-001`, `ITEM-001`, `OPT-001`,
`STA-001`, generated `TABLE-001`, `PRINTER-001`, and generated staff/device
refs. These are not source database surrogate IDs.

Materialization contract:

```text
uses_profile_local_refs = true
new_surrogate_ids_required = true
source_store_db_ids_allowed = false
profile_store_independence = true
materialized_store_updates_profile = false
profile_update_changes_existing_stores = false
physical_printer_binding_included = false
device_pairing_included = false
auth_material_included = false
```

The dry-run validator proves:

- category -> item refs resolve
- item -> station refs resolve
- item -> option refs resolve
- parent option refs resolve within the same item
- table/station/printer refs are unique profile-local identities
- printer assignments resolve to logical printers
- combo allowed item refs resolve to menu items
- no source Store database IDs are required
- profile and materialized Store are independent

## Pricing and combo rules

`PRICING_POLICY` materializes to `store_pricing_policies` and preserves A0.1:

```text
size_small_delta = -2.00
size_regular_delta = 0.00
size_large_delta = 2.00
combo_delta = 5.00
```

`size_regular_delta`, `size_large_delta`, and `combo_delta` are observed from
the safe St-Denis menu graph. `size_small_delta` is derived from the reviewed
A0.1 `StandardSize.SMALL` default because no current St-Denis item enables
`size_small`; it does not change current St-Denis ordering behavior.

`COMBO_CONFIGURATION` materializes to `store_combo_components` and preserves
A0.2. It includes 5 canonical enabled components:

- `COMBO_EGG / combo_tea_egg`
- `COMBO_EGG / combo_fried_egg`
- `COMBO_SIDE / combo_edamame`
- `COMBO_SIDE / combo_shredded_potato`
- `COMBO_SIDE / combo_cucumber_salad`

`COMBO_SIDE_REMOVE` options remain in the menu graph as ordinary option
relationships; they are not Store-level combo components.

## Prohibited-data boundary

A5 profile seed contains no:

- orders, historical order items, customers, payments or receipts
- password, hash, token, cookie, session or secret material
- physical printer endpoint, IP, port or hardware credential
- device credential or pairing secret
- Production environment, SSH or database credential
- Production database surrogate IDs

Physical printer binding and Pad/device pairing remain separate Owner runtime
gates.

## Validation

Focused tests:

```text
mvn -q -Dtest='StDenisCanonicalProfileContractTest,StoreProfileContractValidatorTest,StoreProfileMigrationTest,StoreProfileControllerTest' test
```

Result:

```text
PASS
```

Full backend regression:

```text
mvn -q test
```

Result:

```text
PASS
```

Agent 6:

```text
A5_ACCEPT
```

Accepted evidence:

- V15 seeds only A4 `store_profiles` / versions / artifacts.
- Version insert order is `DRAFT` -> artifacts -> `READY`.
- No Store/materialization/Production runtime write exists.
- Deterministic aggregate and artifact fingerprints are verified.
- Graph counts match `6/39/380/11`, tables `13`, stations `5`,
  printers/assignments `4/3`, combo `5`, staff `4`, devices `7`.
- Prohibited runtime/secret key scan found `0`.
- No source DB IDs, physical endpoints or auth material are present.
- Dry-run validator is generic and has no St-Denis shared-logic conditional.

Local Docker/PostgreSQL rehearsal was unavailable in this execution context
because the Docker daemon was not reachable. V15 SQL is parser/contract-tested
locally and must be proven by exact-SHA Staging Flyway after PR merge.

## Boundaries retained

A5 does not:

- create or materialize a Store
- bind an existing Store as a live Profile instance
- copy or reference Production database IDs
- copy runtime secrets or auth material
- copy physical printer endpoints or device pairing material
- start A6/A7/A8/A9/A10
- start Phase B/C
- create Chinatown or Sainte-Catherine
- deploy or mutate Production

Repository stop before PR/merge:

```text
PHASE_A5_REPOSITORY_IMPLEMENTATION_AGENT_6_ACCEPTED_FOR_PR_MERGE
```
