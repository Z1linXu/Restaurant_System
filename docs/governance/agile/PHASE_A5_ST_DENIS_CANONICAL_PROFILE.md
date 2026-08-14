# Phase A5 St-Denis Canonical Profile

Status: `PHASE_A5_RUNTIME_JDBC_CHAR_TYPE_REPAIR_READY_FOR_PR`

Date: 2026-08-13

Fresh repository authority:

```text
origin/main@494497dfbf874bcf12da7eb3821a276f663959c5
```

A4 Store Profile Contract is in `main` through PR #142:

```text
PHASE_A4_STORE_PROFILE_CONTRACT = PASS
PR = https://github.com/Z1linXu/Restaurant_System/pull/142
merge = be14923c96098d80b1b841e2ba0edbe3ca2563a5
```

A5 repository implementation entered `main` through PR #143:

```text
PHASE_A5_ST_DENIS_CANONICAL_PROFILE_REPOSITORY = PASS
PR = https://github.com/Z1linXu/Restaurant_System/pull/143
merge = b83afa98d304223834793d03bfc367b4cf4238f1
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging before A5 deploy: `c1b5e7681f24a11fbf99293567b3da08076fa3b6`,
  Flyway V13
- First exact-SHA Staging deploy attempt for PR #143 merge
  `b83afa98d304223834793d03bfc367b4cf4238f1` built the Staging images,
  preserved Staging-only `MOCK/true` printing configuration, applied Flyway V14,
  then failed closed before any V15 history row.
- V15 failed because the seed's dollar-quoted `content_json` literals began
  with a newline while the A4 PostgreSQL check constraint requires the stored
  text's first non-space character, as evaluated by `btrim`, to be `{`.
- The bounded seed-literal repair entered `main` through PR #145 at
  `494497dfbf874bcf12da7eb3821a276f663959c5`; it changes only V15 seed literal
  layout and the OPS-001 Flyway checksum manifest/test.
- Exact-SHA Staging deploy of `494497dfbf874bcf12da7eb3821a276f663959c5`
  applied Flyway V15 successfully, then backend startup failed closed during
  Hibernate schema validation because the A4 `fingerprint_sha256 char(64)`
  columns were mapped by JPA as default `varchar(255)`.
- PR #146 changed the A4 Profile entity DDL metadata to explicit `char(64)`,
  but exact-SHA Staging proved Hibernate still treated the field as
  `Types#VARCHAR`. Backend startup therefore remained fail-closed.
- The current bounded JDBC type repair adds explicit Hibernate
  `@JdbcTypeCode(SqlTypes.CHAR)` metadata and regression coverage. It does not
  add a migration, edit Flyway history, reset Staging, materialize a Store,
  touch Production, downgrade, or read runtime secrets.
- Exact-SHA Staging deploy/Flyway validation must be retried after the
  JDBC type repair PR enters `main` before A5 runtime PASS can be claimed.
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

## Runtime seed-literal repair

First Staging runtime attempt:

```text
APPROVED_SHA = b83afa98d304223834793d03bfc367b4cf4238f1
PREVIOUS_STAGING_SHA = c1b5e7681f24a11fbf99293567b3da08076fa3b6
PREVIOUS_STAGING_FLYWAY = V13
STAGING_ENV_ROTATION = PASS
PREFLIGHT = PASS
BUILD_START = PASS
FLYWAY_AFTER_FAILURE = V14_SUCCESSFUL_WITH_NO_V15_SUCCESS_ROW
PRODUCTION_MUTATION = NONE
```

Fail-closed root cause:

```text
V15 content_json dollar-quoted values used $tag$ + newline + JSON.
A4 check constraint uses left(btrim(content_json), 1).
PostgreSQL btrim(text) removes spaces, not newlines.
Therefore the first stored character remained newline and V15 was rejected.
```

Repair:

- V15 profile and artifact JSON literals now use `$tag$ {`: the stored text
  begins with a regular space so the A4 `btrim` check sees `{`, while Flyway
  does not see the placeholder sequence `${`.
- A5 contract test now rejects `$profile_content$` / `$artifact_*$` followed
  by a newline before the JSON root or immediately by `{` / `[`.
- The OPS-001 Flyway checksum manifest now covers V1-V15, including the
  repaired V15 checksum, so official runtime evidence tooling can validate the
  current migration set.

Repair validation:

```text
mvn -q -Dtest='StDenisCanonicalProfileContractTest,StoreProfileMigrationTest,StoreProfileContractValidatorTest,StoreProfileControllerTest' test
PASS

mvn -q test
PASS

deployment/cloud/tests/test_staging_runtime_evidence.sh
PASS
```

Seed-literal repair PR:

```text
PR = https://github.com/Z1linXu/Restaurant_System/pull/145
merge = 494497dfbf874bcf12da7eb3821a276f663959c5
```

## Runtime entity-type repair

Second Staging runtime attempt:

```text
APPROVED_SHA = 494497dfbf874bcf12da7eb3821a276f663959c5
PREFLIGHT = PASS
BUILD_START = PASS
FLYWAY_AFTER_START = V15_SUCCESSFUL
BACKEND_HEALTH = FAIL_CLOSED
PRODUCTION_MUTATION = NONE
```

Fail-closed root cause:

```text
store_profile_versions.fingerprint_sha256 = char(64)
store_profile_artifacts.fingerprint_sha256 = char(64)
StoreProfileVersionEntity.fingerprint_sha256 = default varchar
StoreProfileArtifactEntity.fingerprint_sha256 = default varchar
Hibernate schema validation expected varchar(255) and rejected PostgreSQL bpchar.
```

Repair classification:

```text
BOUNDED_APPLICATION_SCHEMA_MAPPING_REPAIR
NO_NEW_MIGRATION
NO_FLYWAY_HISTORY_EDIT
NO_STAGING_RESET
NO_PRODUCTION_MUTATION
```

Repair:

- declare both Profile fingerprint entity fields as
  `@Column(name = "fingerprint_sha256", columnDefinition = "char(64)", length = 64)`;
- add reflection regression coverage so entity metadata remains aligned with
  the V14 PostgreSQL `character(64)` contract.

Repair validation:

```text
mvn -q -Dtest='StoreProfileMigrationTest,StDenisCanonicalProfileContractTest,StoreProfileContractValidatorTest,StoreProfileControllerTest' test
PASS

mvn -q test
PASS
```

Entity metadata repair PR:

```text
PR = https://github.com/Z1linXu/Restaurant_System/pull/146
merge = 3c99cf1559bbaad2e4c367422bb5eb76877fb086
```

## Runtime JDBC CHAR type repair

Third Staging runtime attempt:

```text
APPROVED_SHA = 3c99cf1559bbaad2e4c367422bb5eb76877fb086
PREFLIGHT = PASS
BUILD_START = PASS
FLYWAY_AFTER_START = V15_SUCCESSFUL
BACKEND_HEALTH = FAIL_CLOSED
PRODUCTION_MUTATION = NONE
```

Fail-closed root cause:

```text
@Column(columnDefinition = "char(64)", length = 64) changed generated DDL text,
but Hibernate schema validation still carried expected JDBC type VARCHAR.
PostgreSQL reports the live V14 columns as bpchar / Types#CHAR.
```

Repair:

- add `@JdbcTypeCode(SqlTypes.CHAR)` to both Profile fingerprint entity fields;
- extend `StoreProfileMigrationTest` to assert both the DDL metadata and the
  Hibernate JDBC type metadata.

Repair validation:

```text
mvn -q -Dtest=StoreProfileMigrationTest,StDenisCanonicalProfileContractTest,StoreProfileControllerTest test
PASS

mvn -q test
PASS
```

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

Current repair stop before repair PR/merge:

```text
PHASE_A5_RUNTIME_JDBC_CHAR_TYPE_REPAIR_READY_FOR_PR
```
