# Store Provisioning Modular Architecture Plan

> Status: `ARCHITECTURE_PLAN_DRAFT_WAITING_FOR_OWNER_REVIEW`
>
> Prepared: 2026-08-08, America/Toronto
>
> Repository base: `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d`
>
> Review: Draft PR #61, base `main`
>
> Runtime access: not performed

## 1. Purpose and authority

This plan defines the bounded architecture direction for turning the current
Owner onboarding and menu-clone capabilities into a reusable Store
provisioning system. It does not add a callable API, database table, runtime
profile, Store, credential, printer, device, or deployment authorization.

The fixed design rule is:

`Profile defines WHAT the Store needs. Provisioning Module defines HOW the capability is provisioned.`

Chinatown is the first reviewed Production Store Profile target, not a special
case in shared code. A future St-Denis Profile must use the same engine and
module contracts. Existing stable order, menu, printing, table, device, and
authentication engines remain the execution authorities for their own domains;
the provisioning layer composes them rather than replacing them.

## 2. Architectural classification gate

Every Store-related change must be classified before implementation:

| Classification | Meaning | Examples | Storage boundary |
|---|---|---|---|
| Generic shared capability | Store-neutral orchestration, validation, idempotency, locking, or evidence | workflow coordinator, module result, activation validator | shared code/schema only when reviewed |
| Store Profile data/rule | Versioned declaration of Store-specific desired state | menu profile, table template choice, required module list | reviewed profile implementation/data |
| Reusable provisioning module | Store-neutral adapter that applies one capability | menu, staff, table, printing, device provisioner | shared module code |
| Runtime-only configuration | Site secret or physical endpoint that cannot live in Git | passwords, printer IP/port, Pad token, certificates | owner-supplied runtime config only |
| One-time operational evidence | Sanitized proof of an approved runtime action | exact SHA, counts, revision, health, result codes | immutable evidence report |

If a proposed change cannot be placed in exactly one primary classification,
implementation stops at the architecture/contract gate. Shared services must
not branch on Store ID, Store name, city, or ordinal Store number.

## 3. Target topology

```text
                    Owner / Admin
                         |
                         v
              Store Provisioning Workflow
                         |
              Store Profile Registry
                         |
          +--------------+--------------+
          |                             |
 ST_DENIS_PROFILE               CHINATOWN_PROFILE
          |                             |
          +--------------+--------------+
                         |
               Generic Provisioning Engine
                         |
     +-------------------+-------------------+
     |                   |                   |
     v                   v                   v
 Store Core          Access/Staff          Menu
 Provisioner         Provisioner           Provisioner

     |                   |                   |
     v                   v                   v
 Tables             Printing              Devices/Pad
 Provisioner        Provisioner           Provisioner

                         |
                         v
                Activation Validator
                         |
                         v
                      ACTIVE
```

The target is incremental. No current package is authorized to introduce all
modules or replace the current Store lifecycle at once.

## 4. Versioned Store Profile contract

The future conceptual profile shape is:

```text
StoreProfile
- profileCode
- version
- baseConfiguration
- menuProfile
- staffProfile
- tableProfile
- printingProfile
- deviceProfile
- activationRequirements
```

These names are architecture concepts, not current DTO or endpoint fields.
Each profile component is optional only when the profile explicitly chooses a
reviewed policy such as `manual_after_creation` or `not_applicable`.

### Identity and compatibility

- `profileCode` is stable, strict, case-sensitive, and contains no leading or
  trailing whitespace.
- `version` is immutable once used in accepted evidence. A material desired
  state change creates a new version rather than mutating historical meaning.
- Profile fingerprinting covers only reviewed non-secret desired state and
  module references. It never includes passwords, tokens, printer endpoints,
  device secrets, raw request bodies, or customer data.
- The registry rejects duplicate identities and unsupported module versions.
- Compatibility is checked before any write. A profile can require a minimum
  engine/module contract version without assuming deployment state.

### Current and planned profiles

| Profile | State | Boundary |
|---|---|---|
| `CHINATOWN_MENU_2026_02_02` | `IN_MAIN` menu-clone profile | Frozen initial Chinatown menu target; not a complete Store Profile yet. |
| `ST_DENIS_PROFILE_V1` | `PLANNED` | Production-like Store template; no current registry entry or public API. |
| Synthetic St-Denis baseline | `STG-005B_PLANNED` | Synthetic-only acceptance fixture/manifest; not Production source evidence. |

Production Store 1 is the Owner-approved live source for the Chinatown
Production clone. That source identity/evidence requirement is a profile input
boundary, not permission to hard-code Store 1 inside the generic engine.

## 5. Generic provisioning engine

The future engine coordinates module contracts but does not implement domain
business rules itself. Its responsibilities are limited to:

1. resolve an exact reviewed profile identity;
2. authorize Organization/Store scope;
3. validate requested modules and compatibility;
4. reserve an idempotent provisioning request;
5. determine dependency order;
6. invoke modules through stable contracts;
7. record sanitized module outcomes;
8. run the activation validator;
9. return a bounded summary and replay state.

The engine must reuse the current AL-002 and AL-003 idempotency principles:
same scope/key/fingerprint replays a completed result; changed content
conflicts; `FAILED` is terminal for that key; retry requires revalidation and a
new key. This plan does not authorize a new migration or general request table.
Persistence is decided only when an implementation loop proves it necessary.

Cross-module writes are not assumed to fit one database transaction. Each
module must declare whether it is transactional, compensatable, manually
reversible, or runtime-only. Activation remains false until all required
module evidence is valid.

## 6. Provisioning module contracts

All conceptual modules share a bounded result shape:

```text
ProvisioningModule
- moduleCode
- contractVersion
- validate(context, profileSection)
- provision(context, profileSection, idempotencyScope)
- verify(context, expectedState)
- rollbackBoundary()

ModuleResult
- status
- resultCode
- warnings[]
- createdCount / updatedCount where safe
- evidenceReferences[]
```

No module result exposes credentials, raw payloads, printer endpoints, device
tokens, or full database ID maps.

### 6.1 Store Core Provisioner

Reuse AL-002 Store creation defaults and Organization authorization. Planned
profile inputs include safe Store code/name defaults, timezone/config references,
and initial inactive state. Activation is not part of creation.

### 6.2 Access and Staff Provisioner

Reuse `OwnerStoreOnboardingService`, BCrypt credential provisioning,
`OrganizationMembership`, `StoreMembership`, and `StoreAccessService`.
Organization Owners inherit same-Organization Store access; the module must
not manufacture redundant Owner Store memberships. Store-scoped staff require
explicit target memberships. Initial passwords remain runtime-only.

### 6.3 Menu Provisioner

Reuse `StoreMenuCloneProfileRegistry`, the read-only planner, shared option-plan
validator, lock-owning clone transaction, V10 request coordinator, and current
menu management services. No second clone engine is permitted. Chinatown
differences remain in its versioned profile. A future St-Denis profile is a new
registry input/adaptor, not copied transaction logic.

### 6.4 Table Provisioner

Reuse current Store-scoped dining-table services. A profile may reference a
reviewed table template or declare `manual_after_creation`. The module must not
copy table runtime state, active orders, occupancy, or historical table data.

### 6.5 Printing Provisioner

Reuse current printer configuration, assignment, module routing, test-readiness,
and PAD_DIRECT behavior. Profiles may declare required printer roles/modules
and paper/encoding semantics, but never an IP, port secret, credential, or
physical endpoint. Site endpoints remain Owner-supplied runtime values.
Provisioning does not rewrite the print engine or bypass PRINTING safeguards.

### 6.6 Device / Pad Provisioner

Reuse current pairing, Store binding, module assignment, heartbeat/health, and
Android Worker visibility. Device identity/token creation is runtime-only and
separately approved. The module verifies desired binding/readiness; it does not
embed a Pad or token in a profile and does not rewrite the Android Worker.

## 7. Activation workflow

The planned lifecycle is:

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

This is a future AL-006 contract, not a current database state machine. The
validator must fail closed when a profile-required module lacks evidence. A
module declared `manual_after_creation` still needs explicit completion or an
Owner-approved waiver before activation.

Minimum Chinatown activation evidence includes Store linkage/configuration,
Owner and staff login/access, cloned menu and revision, tables, printer
assignments and physical ticket checks, Pad Store binding and Worker health,
dine-in submit/update, expected GRAB/FRONTDESK_RECEIPT/HOT_KITCHEN tickets, and
operational completion. Payment is not silently added.

## 8. Existing implementation map

| Capability | Current authority | Reuse decision |
|---|---|---|
| Organization Owner access | `StoreAccessService`, `OwnerOrganizationAuthorizationService` | Reuse; no redundant Owner Store membership. |
| Store/staff onboarding | `OwnerStoreOnboardingServiceImpl`, `OnboardingStaffProvisioningServiceImpl`, V8 | Reuse as Store Core + Access/Staff foundation. |
| Synthetic identity bootstrap | `StagingSyntheticBootstrapCommand/Service/Guard`, V9 | Reuse for Staging only; never general Production provisioning. |
| Menu profile/registry | `StoreMenuCloneProfileDescriptor`, `StoreMenuCloneProfileRegistry` | Extend incrementally; preserve strict profile identity. |
| Menu clone execution | `StoreMenuCloneTransactionServiceImpl`, V10 coordinator | Reuse; no second engine. |
| Menu mutation | current Category/Station/Item/Option services and `MenuRevisionService` | Reuse through supported APIs/services. |
| Tables | current dining-table controller/service/repository | Wrap later; do not clone runtime state. |
| Printing | current printer config/assignment/routing services | Wrap later; endpoints remain runtime-only. |
| Devices | `StoreDeviceServiceImpl`, pairing/controller, Android bridge | Wrap later; secrets remain runtime-only. |
| Activation | no single reusable orchestrator in current main | Bounded future AL-006 implementation. |

## 9. STG-005B immediate dependency

STG-005B must establish a reproducible Synthetic St-Denis source baseline
without Production data. It must first audit whether current supported
menu-management APIs and AL-003 test fixtures can express the complete source
SKU/option contract. It may add a reviewed, versioned synthetic manifest and a
Staging-only idempotent application mechanism only if current APIs cannot
provide a safe reproducible path without manual SQL.

The baseline must use the `STG005_` namespace, keep printing disabled, contain
no real credentials/endpoints/devices/customers/orders/payments, and expose
only sanitized plan/evidence. It is not `ST_DENIS_PROFILE_V1`, does not prove
Production Store 1 contents, and cannot replace the Production read-only source
gate.

## 10. Incremental loop boundaries

| Order | Loop | Bounded output | Explicit non-goal |
|---|---|---|---|
| 1 | `STG-005B_SYNTHETIC_ST_DENIS_BASELINE` | Reviewed synthetic source manifest/application path and tests | No SSH/runtime mutation or Production source read |
| 2 | `AL-003S_STAGING_CLONE_ACCEPTANCE` | Exact-SHA Staging plan/evidence and, only after approval, acceptance | No Production action |
| 3 | `AL-004_GENERIC_STORE_PROFILE_FRAMEWORK` | Store-level profile identity/composition/module references and Owner template contract | No all-module rewrite |
| 4 | `AL-005A_STAFF_TABLE_PROVISIONING_MODULES` | Reusable staff/access and table adapters | No order/table runtime-engine rewrite |
| 5 | `AL-005_PRINTING_PROVISIONING_TEMPLATE` | Runtime-safe printer-role/assignment provisioning contract | No endpoints in Git; no print engine V2 |
| 6 | `AL-005B_DEVICE_PAD_PROVISIONING_MODULE` | Pair/bind/assign/health provisioning contract | No Android Worker rewrite |
| 7 | `AL-006_STORE_ACTIVATION_WORKFLOW` | Orchestration and fail-closed activation validator | No implicit payment expansion |
| 8 | `REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE` | Production gap/migration/backup/rollback/compatibility package | No deployment without approval |
| 9 | `ACT-001_CHINATOWN_PRODUCTION_ACTIVATION` | Approved runtime provisioning and field acceptance | No unauthorized Production mutation |

Historical loop IDs remain unchanged. These identifiers map the Owner's
modular intent onto the dependency order already recorded in the Feature
Backlog; naming them does not start or authorize runtime work.

## 11. Testing strategy

Each implementation loop must include:

- profile identity/version/fingerprint tests;
- Organization and Store isolation tests;
- exact replay/conflict/terminal-failure tests where writes are idempotent;
- module validation and fail-closed activation tests;
- transaction rollback or explicit partial-failure boundary tests;
- password/token/endpoint/log redaction tests;
- regressions for existing onboarding, menu clone, tables, printing, devices,
  order lifecycle, and Store access as applicable;
- PostgreSQL/Flyway first/second startup only when a reviewed migration exists;
- mandatory governance and scope scans.

Synthetic Staging acceptance and physical Production checks remain separate
runtime evidence and cannot be replaced by unit tests.

## 12. Security and runtime boundaries

- No Production database copy enters Staging.
- No Production password/hash, JWT, token, customer/order/payment data,
  printer endpoint, or device secret enters Git, a profile, request evidence,
  logs, or synthetic fixtures.
- No raw SQL is the routine Store provisioning mechanism.
- `DISABLED` is the default printing state until a separately approved module
  and physical acceptance gate completes.
- Exact-SHA deployment, Flyway, bootstrap, credential creation, validate,
  execute, Store 1 read, printer setup, Pad pairing, and activation are
  independently Owner-gated runtime actions.

## 13. Acceptance criteria for this architecture package

- Current capabilities and planned concepts are clearly distinguished.
- Profile, engine, module, runtime-only configuration, and evidence boundaries
  are explicit.
- Chinatown contains no new shared-code Store ID/name special case.
- The future St-Denis Profile reuses the same engine.
- STG-005B and downstream loops have bounded responsibilities and dependency
  order.
- No business code, API, migration, deployment script, or runtime state is
  changed by this package.

## 14. Current review boundary

This architecture package may be reviewed and merged independently. While it
is unmerged, STG-005B implementation may be prepared only as explicitly
`STACKED_ONLY` work and must be rebuilt or promoted from latest `main` after
Owner merge. No runtime operation is authorized.
