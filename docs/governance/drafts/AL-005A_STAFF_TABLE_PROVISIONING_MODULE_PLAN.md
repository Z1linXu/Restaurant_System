# AL-005A Staff and Table Provisioning Module Plan

> **DRAFT / SUPPORTING / NOT AUTHORIZED.** See `docs/governance/CURRENT_STATE.yml`.

> Final route update (2026-08-13): preserved as historical module-planning
> evidence and `SUPERSEDED_BY_FINAL_PRODUCTIZATION_PLAN` for active sequencing.
> Staff/access and table requirements now flow through Phase A module/profile
> contracts before Phase B Owner provisioning. See
> [FINAL_PRODUCTIZATION_PLANBOOK](../../archive/governance-pre-simplification/agile/FINAL_PRODUCTIZATION_PLANBOOK.md).

> Status: `IN_MAIN` via PR #65 merge
> `8f58bcbfca253c1598b967f4d17c04c0be1cce5b`
>
> Package: `AL-005A1_STAFF_TABLE_MODULE_CONTRACTS`
>
> Dependency: AL-004 Store Profile contract `IN_MAIN` at
> `54b784e3a5c5e257c4fc4df4c1ce21f14160e9a6`
>
> PR: #65 (`IN_MAIN`)
>
> Runtime effect: none

## 1. Purpose

AL-005A will turn existing staff/access and dining-table capabilities into
reusable Store provisioning modules without creating another onboarding
engine. This preparation package records the executable boundaries that are
safe to implement after the AL-004 profile contract enters `main`; it is now
entered `main` and remains planning only.

The architecture remains:

```text
Versioned Store Profile
        |
        +-- ACCESS_STAFF module configuration
        +-- TABLES module configuration
        |
Generic Store Provisioning Engine
        |
        +-- existing AL-002 staff/credential authority
        +-- future Store-scoped table provisioner
```

This document is not an API, migration, runtime approval, or concrete Store
Profile. It does not create staff, credentials, memberships, or tables.

## 2. Classification

| Concern | Classification | Rule |
|---|---|---|
| Staff and table orchestration | Generic shared capability | Store-neutral interfaces, validation, plans, and sanitized results only. |
| Required roles or table topology | Versioned Store Profile data | Concrete Chinatown/St-Denis values must not appear in shared services. |
| Login identifier and password | Runtime-only configuration | Never stored in a Profile, fingerprint, log, evidence, or Git. |
| Existing user, membership, and table rows | Runtime state | Must be inspected through Store-scoped repositories inside an approved transaction. |
| Provisioning result | One-time operational evidence | IDs/counts/status only; no credentials or customer/order data. |

## 3. Current executable authorities

### 3.1 Staff and access

- `OnboardingStaffProvisioningServiceImpl` is the current safe write authority
  for an onboarding-created Store staff identity. It validates Organization and
  Store ownership, hashes the runtime password through `PasswordService`, and
  creates `User`, `UserCredential`, and one target `StoreMembership` in a
  transaction.
- `OwnerStoreOnboardingServiceImpl` owns the current parent idempotency and
  transaction boundary. `OnboardingStaffProvisioningService` is not independently
  replay-safe and must not be invoked as a standalone retry mechanism.
- `OwnerOrganizationAuthorizationService` is the Owner authorization boundary.
- `StoreAccessService` grants an Organization Owner inherited access to Stores
  in that Organization. New AL-005A provisioning must create explicit
  memberships for Store-scoped staff. The existing service still supports a
  legacy `users.store_id` fallback for users with no active memberships; the
  planner must report rather than silently normalize those historical rows.
- `OnboardingStaffProvisioningService` creates a Store membership for every
  supplied role, including `OWNER`. The future `ACCESS_STAFF` adapter must
  exclude Organization Owner creation or delegate Owner topology to a separate
  controlled bootstrap/onboarding path so it does not create a redundant Owner
  target-Store membership.
- `StaffAdminServiceImpl.createStaff()` is not a provisioning authority because
  it does not establish the required Store membership in the same operation.

### 3.2 Dining tables

- `DiningTable`, `DiningTableRepository`, and the Platform Admin service are the
  current data/write path.
- Frontdesk configuration only reads tables.
- `RestaurantTemplate.default_dining_tables_json` is mutable JSON. It has no
  immutable Profile version or reviewed fingerprint and is not a Store Profile
  authority.
- The current schema has no unique constraint for
  `(store_id, table_code)`. A replaying table writer could create duplicates.
- The current Platform Admin table update path authorizes using request Store
  context before loading an arbitrary table ID. It must not be reused as a
  provisioning upsert until Store ownership is enforced against the loaded row.

## 4. AL-005A1 contract package

After AL-004 is merged, the smallest safe implementation package may add:

1. Versioned, non-secret `ACCESS_STAFF` and `TABLES` module configuration
   contracts.
2. Canonical module fingerprints compatible with the AL-004 parent Profile
   fingerprint binding.
3. Validators that reject secret-bearing fields and incomplete configuration.
4. Sanitized read-only plans and verification results.
5. A staff adapter that delegates to `OnboardingStaffProvisioningService` only
   while a parent coordinator owns authorization, idempotency, and transaction.
6. A table read-only planner/verifier that compares desired table definitions
   with Store-scoped rows without writing or changing runtime table state.

The first package must not add a public endpoint, migration, independent retry
coordinator, table writer, or concrete Chinatown/St-Denis configuration.

## 5. Proposed module contracts

### 5.1 ACCESS_STAFF

The versioned configuration may describe required non-Owner Store-scoped role slots, but
must not contain usernames, passwords, password hashes, tokens, personal names,
or emails. Runtime credential input is a separate redacted command.

Fixed access rules:

- Organization Owner access comes from active Organization membership and is
  outside the Store-scoped staff adapter.
- No redundant target-Store Owner membership is created by this module.
- Store-scoped staff require active target Store membership.
- Passwords use the existing BCrypt credential flow.
- Staff execution is subordinate to a parent idempotency and transaction
  coordinator; `FAILED` is not silently replayed.

Safe result fields are limited to Store ID, created/reused counts, role codes,
sanitized result code, and warnings. Raw or hashed credentials are forbidden.

### 5.2 TABLES

The configuration contract supports the following policy vocabulary:

- `MANUAL_AFTER_CREATION`: the module records that table setup is an activation
  prerequisite completed by an authorized operator later.
- `PREDEFINED_TEMPLATE`: reserved for a separately reviewed immutable,
  fingerprinted table definition.

The existing Chinatown decision is authoritative: do not clone tables;
Chinatown starts with blank table setup and therefore uses
`MANUAL_AFTER_CREATION`. `PREDEFINED_TEMPLATE` is a future reusable capability,
not a reopened Chinatown product question.

The first implementation promotes only validation and read-only planning. A
writer remains blocked until table-code normalization, uniqueness, update,
deactivation, and replay semantics are approved and backed by schema evidence.

The module must never copy:

- active orders or order-table bindings;
- occupancy/session state;
- payment or customer data;
- runtime-generated table state;
- mutable `RestaurantTemplate` JSON without conversion to a reviewed immutable
  module configuration.

## 6. Transaction and idempotency boundary

```text
Owner authorization
        -> parent provisioning reservation
        -> lock Organization/Store scope
        -> validate module fingerprints
        -> plan ACCESS_STAFF and TABLES
        -> execute approved writers in one parent transaction
        -> persist sanitized module evidence
```

- Module code must not create a second idempotency engine.
- Parent replay returns the original sanitized result and must not create
  another credential, membership, or table.
- Same key with a different request/profile fingerprint is a conflict.
- `FAILED` remains terminal for that key; retry requires a newly validated key.
- A failure before commit must leave no partial identity, credential,
  membership, or table rows.

## 7. Required prerequisite decisions and evidence

The following are genuine gates for later executable Staff or predefined-table
packages, not blockers for the AL-005A1 contract/read-only-planner slice:

1. Whether a future Staff Profile defines only role slots/counts or also non-secret
   login-name conventions.
2. Exact `table_code` normalization: case, Unicode/ASCII whitespace, and allowed
   character grammar.
3. Existing-row policy for a future predefined writer: exact replay, payload
   conflict, update, deactivate, or
   reject.
4. Whether module idempotency is represented only by the future parent
   provisioning request or also requires module-level evidence rows.
5. A read-only duplicate audit before any unique constraint is proposed.
6. A read-only legacy-access audit that distinguishes explicit Store
   memberships from `users.store_id` fallback users before any normalization.

No destructive migration is permitted. Any future uniqueness migration must be
append-only and may proceed only after duplicate evidence and a separate Owner
review.

## 8. Required tests

### Contract and profile binding

- deterministic configuration fingerprint;
- field names/counts prevent ambiguous canonicalization;
- exact module contract version;
- secret-bearing fields cannot enter configuration, summary, or evidence;
- concrete Store rules remain outside shared implementation.

### Staff adapter

- Organization/Store mismatch rejected;
- BCrypt credential created through the existing authority;
- Store-scoped staff receive exactly the target membership;
- Organization Owner receives no redundant target membership;
- parent replay creates no duplicate identity or membership;
- transaction failure leaves no partial user, credential, or membership;
- command, exception, logs, and evidence redact credentials.

### Table planner and future writer gate

- Store-scoped reads only;
- duplicate desired codes rejected under the approved normalization;
- existing exact state plans `REUSE`, differing state plans `CONFLICT` until an
  explicit update policy is approved;
- cross-Store table IDs cannot be updated or adopted;
- order/occupancy state is never part of a provisioning write;
- writer tests remain disabled/nonexistent until schema and product gates are
  closed.

## 9. Delivery sequence

| Package | Scope | Entry gate | Stop state |
|---|---|---|---|
| `AL-005A1` | Configuration contracts, fingerprints, validators, staff adapter boundary, table read-only planner | AL-004 merged and rebuilt from latest `main` | `WAITING_FOR_OWNER_REVIEW` |
| `AL-005A2` | Table uniqueness/ownership prerequisite repair, if approved | Duplicate evidence plus normalization/update decisions | `WAITING_FOR_OWNER_REVIEW` |
| `AL-005A3` | Parent-coordinated staff/table execution | AL-005A1/2 merged and parent engine contract stable | `WAITING_FOR_OWNER_REVIEW` |

Every package requires focused tests, full backend regression, compile,
`git diff --check`, secret/scope scan, independent review, and mandatory
governance sync.

## 10. Current stop state

`AL-005A_IN_MAIN_WAITING_FOR_NEXT_DEPENDENCY_REVIEW`

No Staff/Table provisioning runtime capability is established by this plan.
