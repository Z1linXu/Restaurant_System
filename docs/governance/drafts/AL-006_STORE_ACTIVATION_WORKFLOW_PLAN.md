# AL-006 Store Activation Workflow Plan

> **DRAFT / SUPPORTING / NOT AUTHORIZED.** See `docs/governance/CURRENT_STATE.yml`.

> Final route update (2026-08-13): preserved as historical activation-planning
> evidence and `SUPERSEDED_BY_FINAL_PRODUCTIZATION_PLAN` for active sequencing.
> Owner activation now follows Phase A module/profile validation and Phase B
> provisioning; activation implementation is not authorized until Phase B. See
> [FINAL_PRODUCTIZATION_PLANBOOK](../../archive/governance-pre-simplification/agile/FINAL_PRODUCTIZATION_PLANBOOK.md).

> Status: `IN_MAIN` via PR #69
>
> Package: `AL-006A_ACTIVATION_CONTRACT_AND_READ_ONLY_VALIDATOR_PLAN`
>
> Git classification: `IN_MAIN`
>
> Source PR: #69; historical base `main@9e93573be97cfd01a9ad3efe64d55827854c497a`; merged at `dc682203b2b24bbdb453a5520b297b9051139f13`
>
> Runtime effect: none

## 1. Purpose

AL-006 defines the single fail-closed workflow that will eventually decide
whether an inactive Store is ready to become active. It composes evidence from
the generic Store Profile and reusable provisioning modules; it does not
replace their domain logic.

The contract is:

```text
Profile declares required capabilities
        -> provisioning modules produce sanitized evidence
        -> activation validator verifies the complete evidence set
        -> activation workflow owns the only approved inactive -> active write
```

This package is planning only. It creates no endpoint, migration, entity,
request record, Store, credential, printer, device, order, or runtime evidence,
and it does not change `stores.status`.

## 2. Classification

| Concern | Classification | Boundary |
|---|---|---|
| Workflow, validation, evidence aggregation, locking | Generic shared capability | Must not branch on Store ID, Store name, city, or Store ordinal. |
| Required modules and policy thresholds | Versioned Store Profile data | Chinatown requirements belong only to a reviewed Profile. |
| Store/access/menu/table/printing/device checks | Reusable provisioning module verification | Existing domain authorities remain authoritative. |
| Credentials, printer endpoints, device tokens, local Worker state | Runtime-only configuration/state | Never stored in a Profile or returned in activation evidence. |
| Login, physical ticket, Worker and order-flow proof | Time-bounded operational evidence | Sanitized, Store-bound, exact-build evidence only; validity remains subject to the reviewed freshness policy. |

## 3. Current repository truth

There is no unified activation coordinator, activation repository, activation
API, or persisted activation state machine in the current stack.

- `Store.status` is a free-text `String` in `Store`; it is not an activation
  enum and carries no module evidence or transition history.
- `OwnerStoreOnboardingServiceImpl.createInactiveStore` creates the target as
  `inactive`, with printing disabled and mode `DISABLED`.
- `StoreMenuCloneTransactionServiceImpl` requires the target to remain inactive
  and printing-disabled before clone execution.
- `PlatformAdminServiceImpl.saveStore` and `createStoreFromTemplate` can write
  `active` directly and default null status to `active`. These legacy paths are
  a compatibility and enforcement gate for a future activation writer.
- `RuntimeDataSeeder.seedStores` and the guarded Staging-only
  `StagingSyntheticBootstrapServiceImpl.createSourceStore` also create active
  Stores. They are scoped bootstrap/seed exceptions, not activation evidence,
  and must be included in the future writer inventory.
- `StoreProfileComposition.activationRequirements` currently declares which
  applicable provisioning modules are required. `StoreProfileRegistry`
  validates those references, but no runtime service evaluates their evidence.
- Existing module services and repositories can provide pieces of readiness;
  no current service aggregates them or owns final activation.

Repository code does not prove Staging or Production readiness. Retained
runtime evidence remains historical and must not be promoted into a fresh
activation result.

## 4. Lifecycle contract

The conceptual lifecycle is:

```text
DRAFT
  -> STORE_CREATED
  -> ACCESS_READY
  -> MENU_READY
  -> TABLES_READY
  -> PRINTING_READY
  -> DEVICES_READY
  -> ACCEPTANCE_READY
  -> ACTIVE
```

These labels are workflow stages, not approved `stores.status` values. A future
implementation must not write them into the current free-text column without a
separately reviewed persistence and compatibility decision.

Rules:

1. Progress is monotonic only while all prior evidence remains valid.
2. A later-stage check cannot compensate for missing earlier evidence.
3. `manual_after_creation` means explicit completion evidence or an approved
   waiver, never automatic readiness.
4. Any missing, stale, conflicting, wrong-Store, wrong-Organization,
   wrong-profile, wrong-version, or unverifiable critical evidence fails closed.
5. Planning and validation are read-only. Only the future activation workflow
   may perform the final status transition.
6. Validation success is not activation, and repository capability is not
   runtime acceptance.

## 5. Responsibility split

### 5.1 Store Profile

The exact Profile declares WHAT is required through versioned module references
and `activationRequirements`. It may define non-secret thresholds and policies,
but never runtime identities, database IDs, endpoints, credentials, tokens, or
raw evidence.

### 5.2 Provisioning modules

Each reusable module owns HOW its capability is provisioned and verified. A
module returns a bounded result inside an immutable evidence envelope:

```text
ModuleReadinessEvidence
- evidence_id
- organization_id
- store_id
- profile_code
- profile_version
- profile_fingerprint
- module_code
- module_contract_version
- status
- result_code
- checked_at
- valid_until or explicit non-expiring policy
- source_commit / build_provenance where applicable
- expected_configuration_fingerprint
- observed_configuration_fingerprint where safe
- warnings[]
- evidence_references[]
```

The shape is conceptual. It is not an implemented DTO or schema.

### 5.3 Activation validator

The validator resolves the exact Profile, enumerates required modules, verifies
their evidence, evaluates mandatory global gates and cross-module constraints,
and returns a read-only decision. It must not provision, repair, pair, print,
log in, submit orders, or change Store state while validating.

The current module enum does not contain `LOGIN` or `OPERATIONAL_SMOKE`, and
`activationRequirements` may be empty. Therefore the first AL-006 contract must
not pretend those checks are module requirements. Store/Organization validity,
Profile/evidence integrity, Owner access, required login, and operational smoke
evidence are explicit global gates in addition to all Profile-required module
gates. A Profile with no reviewed activation requirements is not activation-
eligible unless a future contract explicitly defines that policy.

### 5.4 Activation workflow

The future workflow coordinates authorization, idempotency, Store locking,
fresh validation, final transition, sanitized audit evidence, and replay. It is
the only new path allowed to move an onboarding-created Store from inactive to
active after legacy compatibility is resolved.

## 6. Evidence authority matrix

| Gate | Current repository authority | Required evidence | Fail-closed examples |
|---|---|---|---|
| Store and Organization | `StoreRepository`, `OrganizationRepository`, `StoreAccessService` | exact Store/Organization/Profile binding; active Organization; inactive Store precondition | missing Store, wrong or inactive Organization, already-active ambiguity |
| Owner access | `OwnerOrganizationAuthorizationService`, `OrganizationMembershipRepository` | active Owner Organization membership and same-Organization Store access | inactive membership, cross-Organization Store |
| Staff | `OnboardingStaffProvisioningServiceImpl`, credential and Store membership repositories | required roles, active credentials, exact target membership | missing role, inactive credential, membership at another Store |
| Menu | profile registry, AL-003 planner/transaction, V10 request evidence, menu repositories/revision service | exact Profile result, expected counts/fingerprint, target revision and source invariance | incomplete clone, revision drift, wrong Profile |
| Tables | `DiningTableRepository` and future AL-005A module | exact template evidence or explicit reviewed manual policy evidence | missing required table, duplicate code, unacknowledged manual policy |
| Printing | printer config/assignment services and future AL-005 module | Store-scoped logical config, disabled-before-binding invariant, approved runtime readiness | missing required role/module, wrong Store printer, enabled too early |
| Devices | `StoreDeviceServiceImpl`, pairing/auth state and future AL-005B module | required active pairings plus trusted installed-build and Worker evidence | token/pairing ambiguity, stale heartbeat-only proof, Worker not polling |
| Login | `AuthServiceImpl`, `StoreAccessService` and approved smoke evidence | successful Owner/staff login and target workspace access | credentials unavailable, wrong workspace, authorization bypass |
| Operational smoke | approved acceptance workflow | exact-build order flow and expected bounded outputs | no retained evidence, stale build, unexpected print/module side effect |

No gate may expose passwords, hashes, JWTs, device tokens, authorization
headers, printer endpoints, raw print payloads, customer data, or order notes.

## 7. Cross-module constraints

The validator must evaluate relationships that no single module can prove:

- every evidence record belongs to the same Organization, Store, Profile
  identity/version, and expected module contract;
- Profile and module fingerprints match the reviewed desired state;
- menu, table, printing, and device evidence was produced after the relevant
  provisioning result and has not been invalidated by later drift;
- printing remains `DISABLED` until logical configuration and device readiness
  are complete and an approved operational gate allows the reviewed mode;
- required printer assignments reference Store-owned printers and enabled
  module policy excludes unsupported modules before Print Job creation;
- device count does not substitute for paired identity, trusted APK provenance,
  local auto-print/Worker health, and Store-wide queue readiness;
- a successful login cannot substitute for required module readiness;
- a smoke test cannot retroactively authorize an unreviewed configuration.

## 8. Chinatown activation profile input

Chinatown-specific values must remain in a future concrete versioned Store
Profile and runtime evidence, never in shared activation code. The current
repository has a complete Chinatown menu-clone Profile but does not register a
complete Chinatown `StoreProfileDescriptor`. The initial reviewed activation
requirements are:

- Store created inactive and linked to the approved Organization;
- Owner Organization access and required Store-scoped Manager/Frontdesk access;
- completed Chinatown menu clone with reviewed counts, target revision change,
  source invariance, exact Profile code, and no unintended domain side effects;
- table policy evidence. The current blank/manual decision is not silently
  equivalent to `TABLES_READY`; it needs explicit reviewed completion evidence;
- printing initially disabled, with exactly `GRAB` and
  `FRONTDESK_RECEIPT` logical assignments and no `HOT_KITCHEN` assignment;
- runtime binding to two physical printer roles without storing endpoints in
  the Profile;
- four independently paired Pads sharing the Store-wide PAD_DIRECT queue, with
  trusted APK/build, auto-print, polling, and recovery evidence;
- Owner and staff login/workspace checks;
- approved dine-in submit/update and expected-ticket smoke evidence;
- payment, KDS configuration, inventory, and unrelated business expansion are
  not added by activation.

## 9. Read-only decision contract

A future first slice should expose an internal read-only plan/validate contract
before any writer:

```text
ActivationValidationResult
- organization_id
- store_id
- profile_code
- profile_version
- profile_fingerprint
- overall_status: READY | NOT_READY
- current_store_status
- stages[]
- module_results[]
- blocking_diagnostics[]
- warnings[]
- checked_at
```

Diagnostics must use stable codes and bounded metadata. They must not include
raw exceptions, request payloads, credentials, tokens, endpoints, customer
data, or full database object maps.

Validation must return `NOT_READY`, not partially activate, when any critical
gate is unmet.

## 10. Future write/idempotency contract

No migration is authorized by this plan. Before an activation writer exists,
the implementation package must decide whether durable request/evidence tables
are required and, if so, propose a new append-only migration after the current
chain. It must not overload V8, V9, V10, or menu-clone evidence.

The expected style follows current onboarding and clone contracts:

- scope includes Organization, Store, exact Profile identity, and an explicit
  idempotency key;
- same key and same fingerprint replays a completed result;
- changed payload conflicts;
- `FAILED` is terminal for that key; retry requires fresh validation and a new
  key;
- a Store row is locked before final validation and transition;
- concurrent activation cannot produce duplicate transition/audit records;
- failure before commit leaves the Store inactive;
- evidence contains only sanitized codes, timestamps, fingerprints, counts,
  and references.

## 11. Legacy write compatibility gate

`PlatformAdminServiceImpl.saveStore` and `createStoreFromTemplate` currently
allow direct active status. `RuntimeDataSeeder.seedStores` and the Staging-only
synthetic bootstrap also create active Stores within their bounded seed/bootstrap
contexts. A future writer cannot claim exclusive activation until all
Store-status write paths are inventoried and a backward-compatible policy is
approved.

Required prerequisite decisions:

1. whether Platform Admin may retain an emergency/manual activation path;
2. which capability and confirmation protect that path;
3. whether legacy active Stores are grandfathered without synthetic activation
   evidence;
4. how unknown/case-variant Store statuses in existing data are normalized;
5. whether activation/deactivation requires a new state/evidence schema;
6. whether runtime access is denied for inactive Stores today or activation is
   initially governance-only.

This package does not change those paths.

## 12. Delivery sequence

| Package | Scope | Entry gate | Stop state |
|---|---|---|---|
| `AL-006A` | evidence interfaces, read-only validator/plan, diagnostics | AL-004 contract in main; module verifier contracts stable | review gate; no status write |
| `AL-006B` | smallest prerequisite repairs: legacy status writes, Store-status compatibility, durable evidence/idempotency only if approved | Owner decisions above; migration review if needed | dependency repair review |
| `AL-006C` | protected activation coordinator/API and exclusive final transition | required modules merged; B complete; authorization contract approved | no runtime execution |
| `AL-006D` | exact-SHA Staging activation acceptance and drift/restart checks | explicit Owner runtime approval | runtime evidence review |

AL-006 does not block continued AL-003 Chinatown acceptance planning. It does
block describing a new Store as fully activation-ready or active based only on
provisioning code.

## 13. Verification plan

Future implementation tests must prove:

- every required gate independently blocks activation when absent;
- stale, wrong-Store, wrong-Organization, wrong-profile/version/fingerprint,
  duplicate, and conflicting evidence blocks;
- profile requirements and module evidence are evaluated generically;
- `manual_after_creation` remains blocking without explicit evidence/waiver;
- validation performs no writes;
- only the approved coordinator can perform the final transition;
- replay/conflict/in-progress/terminal-failure contracts hold;
- concurrent activation cannot double-transition or deadlock;
- transaction failure leaves Store inactive and no false completion evidence;
- legacy active Store compatibility is explicit and tested;
- logs, responses, audit, and persistence contain no secret/runtime endpoint or
  raw operational payload;
- no shared implementation contains Chinatown, St-Denis, Store ID 1/2, or
  ordinal-Store branching;
- onboarding, menu, staff, tables, printing, device, authentication, order,
  and Store-access regressions pass;
- governance and API docs describe only code actually in the reviewed branch.

## 14. Owner decisions and dependencies

Implementation remains blocked by these decisions or unmerged dependencies:

- promotion of the generic architecture/Profile/module stack into `main` is
  complete through #68; remaining work is verifier/evidence contract review;
- completion of required staff/table/printing/device verifier contracts;
- activation persistence and next migration decision;
- legacy Platform Admin active-write policy and existing Store-status
  compatibility evidence;
- activation authority/capability and public API ownership;
- evidence freshness/TTL, trusted minimum APK/build, and smoke-evidence validity;
- deactivation, reactivation, periodic revalidation, and configuration-drift
  behavior;
- whether and how an approved emergency/manual activation path exists.

No SSH, Staging/Production access, Flyway execution, bootstrap, pairing, print,
login, order submission, real clone, or Store activation is authorized.

## 15. Acceptance criteria for this planning package

- Current repository behavior and future concepts are explicitly separated.
- The lifecycle is defined without pretending it is a current schema enum.
- Profile, module, validator, workflow, and operational evidence authorities do
  not overlap.
- Activation fails closed and has one future status-write owner.
- Chinatown rules remain in a versioned Profile/evidence boundary.
- Legacy direct-active paths and persistence decisions are visible gates.
- No code, API, migration, runtime configuration, or runtime state changes.
