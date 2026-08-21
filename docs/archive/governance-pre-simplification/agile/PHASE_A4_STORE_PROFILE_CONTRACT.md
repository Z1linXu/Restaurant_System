# Phase A4 Store Profile Contract

Status: `PHASE_A4_STORE_PROFILE_CONTRACT_PASS_AGENT_6_ACCEPTED_FOR_PR_MERGE`

Date: 2026-08-13

Fresh repository authority at A4 start:

```text
origin/main@fcd427b882e14ffb550ff9af4c63c51d10e407db
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging: no A4 deploy required before A5; A4 introduces additive schema and
  read-only profile APIs that will be applied by the next exact-SHA Staging
  validation when A5 requires runtime proof
- Schema: additive Flyway V14 only
- Runtime effect: Store Profile contract/persistence/read foundation; no Store
  creation, Store activation, provisioning workflow, Chinatown,
  Sainte-Catherine, Production deploy or Profile materialization write

## Product contract

A4 defines:

```text
STORE_PROFILE_CONTRACT
```

A Store Profile is a versioned, safe, reusable Store configuration template. It
is not a database clone, Store clone, Production export, runtime snapshot, code
branch or live binding to an existing Store.

The contract keeps the product rule:

```text
BUILD ONCE, CONFIGURE MANY
```

Profile-specific behavior must be expressed through configuration, module
defaults, versioned template artifacts and future materialization mappings, not
through shared-code Store name/profile conditionals.

## Persistence model

A4 adds database-backed versioned profile persistence:

```text
store_profiles
store_profile_versions
store_profile_artifacts
```

`store_profiles` owns profile identity and top-level lifecycle metadata:

- `profile_code`
- `display_name`
- `description`
- `status`
- `provenance`
- timestamps

`store_profile_versions` owns immutable version rows:

- `profile_version`
- `status`
- `schema_version`
- `content_json`
- `fingerprint_sha256`
- `source_reference`
- timestamps and `published_at`

`store_profile_artifacts` owns versioned configuration artifacts referenced by
a Profile Version:

- `MODULE_DEFAULTS`
- `MENU_TEMPLATE`
- `PRICING_POLICY`
- `COMBO_CONFIGURATION`
- `TABLE_TEMPLATE`
- `STATION_TEMPLATE`
- `LOGICAL_PRINTING_TOPOLOGY`
- `ROLE_ACCESS_DEFAULTS`
- `HARDWARE_REQUIREMENTS`
- `DEVICE_CAPABILITY_REQUIREMENTS`
- `OPERATIONAL_SETTINGS`
- `FEATURE_DEFAULTS`

Migration:

```text
backend/src/main/resources/db/migration/V14__add_store_profiles.sql
```

The migration does not update orders, order items, print jobs, users,
credentials, printer endpoints, devices, menu rows, pricing rows, combo rows,
tables, stations or current Store runtime fields.

## Version immutability

A4 uses database triggers to prevent published/reviewed/ready/retired Profile
Version business-content and profile-binding rewrites:

```text
prevent_published_store_profile_version_rewrite
prevent_published_store_profile_artifact_rewrite
reject_store_profile_artifact_insert_for_immutable_version
reject_store_profile_artifact_delete_for_immutable_version
```

Draft versions remain editable. Published/reviewed/ready/retired versions
require a new version for material business changes. Published artifacts cannot
be inserted, deleted, updated or moved under immutable parent versions.

## Deterministic fingerprint

Application code canonicalizes JSON by sorting object keys recursively and
hashing canonical JSON. The Profile Version fingerprint is an aggregate of:

- profile code
- profile version
- schema version
- canonical `content_json`
- sorted artifact identities
- artifact canonical fingerprints

The same normalized content produces the same fingerprint; material content
changes produce a different fingerprint.

Implementation:

```text
backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileCanonicalJson.java
backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java
```

## Validator

`StoreProfileContractValidator` validates:

- exact profile identity/version/schema
- deterministic profile fingerprint
- artifact fingerprint and uniqueness
- A1/A2 module defaults through `ModuleDependencyValidator`
- core modules enabled
- versioned template references exist for menu, pricing, combo, tables,
  stations, logical printing, role/access defaults and hardware requirements
- materialization contract uses profile-local references and new target
  surrogate IDs
- source Store database IDs are not reusable template identity
- prohibited data keys are absent

Prohibited data includes credentials, passwords, tokens, cookies, secrets,
customer/phone/email/payment fields, physical printer endpoints/IPs, device
identities, DB credentials, SSH information and source/runtime database IDs.

## Read contract

A4 adds Owner-only read APIs for Profile validation and future A5/Staging
proof:

```text
GET /api/v1/store-profiles
GET /api/v1/store-profiles/{profileCode}/versions/{profileVersion}
```

These are read-only. A4 does not add any Store provisioning writer or Owner
Create New Store workflow.

## Focused validation

Focused tests:

```text
mvn -q -Dtest='StoreProfileMigrationTest,StoreProfileContractValidatorTest,StoreProfileControllerTest,StoreProfileRegistryTest,ModuleCatalogContractTest,ModuleDependencyValidatorTest,StoreModuleMigrationTest,StoreModuleServiceImplTest,StoreModuleControllerTest' test
```

Result:

```text
focused backend tests = PASS
full backend tests = PASS
git diff --check = PASS
Agent 6 = A4_ACCEPT
```

Coverage:

- V14 is additive and secret-free
- database-backed Profile/Version/Artifact model exists
- immutable version trigger blocks downgrade, content/fingerprint/source/schema,
  profile version and profile binding rewrites
- immutable artifact triggers block insert/delete/update/move when old or new
  parent version is immutable
- deterministic fingerprinting is stable across JSON object order
- business-content changes alter fingerprints
- A2 module validation rejects unknown modules and disabled core modules
- prohibited data and source Store DB IDs fail closed
- materialization without explicit ID remapping fails closed
- artifact fingerprint mismatch and duplicate artifacts fail closed
- Owner-only read APIs require Owner authorization

## Boundaries retained

A4 does not implement:

- A5 St-Denis Canonical Profile data
- Store materialization writer
- Owner Create New Store
- A6 backend module gating
- A7 frontend module gating
- A8 hardware capability runtime binding
- A9 legacy cleanup
- A10 regression
- Phase B/C
- Chinatown or Sainte-Catherine creation
- Production deploy, restart, migration or configuration change

Expected A4 completion state after Agent 6, PR and merge:

```text
PHASE_A4_STORE_PROFILE_CONTRACT = PASS
```
