# AL-005 Printing Provisioning Module Plan

> Status: `AL-005_PRINTING_PREPARED_WAITING_FOR_DEPENDENCIES` in Draft PR #67
>
> Package: `AL-005_PRINTING_PROVISIONING_TEMPLATE`
>
> Git classification: `STACKED_ONLY`
>
> Base: AL-005A Draft PR #65 head
>
> Draft PR: #67 (`STACKED_ONLY`)
>
> Runtime access: not performed

## 1. Purpose

AL-005 will wrap the existing Store-scoped printing capabilities in a reusable
provisioning module. It is not a new print engine and does not copy printer
rows from another Store. The module eventually translates a reviewed Store
Profile's logical printing requirements into a safe, inactive configuration
plan, while physical endpoints and activation remain explicit on-site work.

This package is an authority audit and implementation plan only. It adds no
writer, public endpoint, migration, printer, assignment, device, print job,
mode change, test print, or runtime evidence.

The architectural classification is:

| Concern | Classification | Boundary |
|---|---|---|
| validation, fingerprinting, planning, idempotent coordination | generic shared capability | Store-neutral code only |
| required logical printer roles and module routing | versioned Store Profile rule | reviewed non-secret desired state |
| printer endpoint, name, timeout, physical printer ID | runtime-only configuration | supplied and retained on site |
| applying configs/assignments | reusable provisioning module | existing printing services behind a reviewed transaction contract |
| connection/test-print/Pad health proof | one-time operational evidence | sanitized acceptance record |

## 2. Existing executable authorities

| Capability | Current authority | Reuse decision |
|---|---|---|
| physical printer configuration | `PrinterConfig`, `PrinterConfigRepository`, `PrinterConfigServiceImpl` | reuse; do not clone source rows |
| module-to-printer assignment | `PrinterAssignment`, `PrinterAssignmentRepository`, `PrinterAssignmentServiceImpl` | reuse only after uniqueness/concurrency contract is resolved |
| Owner/Manager Print Center API | `OwnerPrintingController` plus `AuthorizationService.requireForStore()` | operational configuration authority, not a provisioning API |
| Store printing enable/mode | `Store.printing_enabled`, `Store.printing_mode`, `PrintingMode` | Store remains `DISABLED` during provisioning |
| automatic dispatch and renderer routing | `PrintDispatcherServiceImpl` and registered `ReceiptRenderer` beans | reuse the existing engine, adding only a separately reviewed pre-job policy gate; no Print Engine V2 |
| PAD_DIRECT claim/payload lifecycle | `PadPrintJobServiceImpl`, Store device auth, Android worker | execution authority; readiness belongs to AL-005B |
| cloud private-endpoint safety | `CloudPrintingGuard` and transport checks | preserve; never bypass for provisioning |
| module vocabulary | `PrintModuleCode` | profile validation must accept only modules with an approved renderer/policy |

Authorization is enforced by the controlled Controller boundary; the current
printing services are not self-authorizing public APIs. A future provisioning
adapter must be invoked only by the parent Organization/Store-authorized
workflow and must not create a second capability path.

## 3. Current mode behavior

| Mode | Current behavior | Provisioning rule |
|---|---|---|
| `DISABLED` | automatic jobs are cancelled as printing-disabled; configuration remains accessible | mandatory initial and post-plan state |
| `PAD_DIRECT` | Backend queues a payload; a paired Pad claims and prints it | runtime activation only after AL-005B and field acceptance |
| `MOCK` | transport is skipped and rendered output is logged/marked printed | not a Profile-selected Production mode |
| `REAL` | Backend uses the ESC/POS TCP transport, subject to cloud guard | not selected by a Store Profile |

`PrintingMode.normalize()` treats blank and unknown input as `REAL`.
`PrinterConfigServiceImpl.getStorePrintingMode()` has a separate legacy path:
a blank stored mode resolves to `DISABLED` only when `printing_enabled=false`,
otherwise to `REAL`. Unknown non-blank values also normalize to `REAL`. These
paths are not uniformly fail-closed and block an executable generic writer.
Changing legacy blank-value compatibility may affect existing Stores, so this
plan records the issue rather than silently changing runtime semantics in a
documentation package.

## 4. Profile-safe desired state

A future versioned profile section may declare only non-secret logical intent:

```text
PrintingProvisioningConfiguration
- contractVersion
- policy: MANUAL_AFTER_CREATION
- enabledModuleCodes[]
- logicalPrinterRoles[]
  - roleCode
  - paperWidthMm
  - textEncoding
  - escposCodePage
  - fontSize
- moduleAssignments[]
  - moduleCode
  - logicalPrinterRoleCode
  - takeoutReceiptCopies
- activationRequirements[]
```

Required validator rules:

- exact, versioned, deterministic input and fingerprint;
- unique logical role codes;
- unique enabled module codes;
- unique module codes within the desired plan;
- every assignment module is enabled and every enabled module has exactly one
  assignment;
- every assignment references a declared role;
- only explicitly supported renderer/module combinations are accepted;
- paper width and encoding use new reviewed allow-lists/bounds because current
  printer writes accept them directly; font mode and copy count retain their
  existing normalization and receive explicit contract bounds;
- no profile can request `REAL`, `MOCK`, or `PAD_DIRECT` activation;
- initial policy is exactly `MANUAL_AFTER_CREATION`;
- no endpoint, database ID, device identity, secret, or mutable health state is
  accepted or fingerprinted.

The read-only planner should return sanitized diagnostics such as missing
logical role binding, duplicate assignment, unsupported renderer, physical
endpoint not configured, assignment not accepted, device readiness pending,
and Store mode not disabled. It must not return endpoint values or raw payloads.

## 5. Runtime-only configuration

The following never belongs in Git, a Store Profile, request fingerprint,
replay response, or durable provisioning-request evidence:

- printer IP, port, endpoint, DNS name, connection timeout, or site label;
- database `printer_id` or assignment row IDs;
- device ID/token/hash, pairing token, or Pad identity;
- selected active printing mode and assignment enabled state;
- last connection/print timestamps or runtime error text;
- Print Job IDs, attempt tokens, rendered receipt content, or ESC/POS payload;
- customer/order content and MOCK receipt output.

Physical configuration is entered on site after Store creation. A separately
approved operational acceptance record, distinct from durable provisioning
request/replay evidence, may record only logical role, module,
endpoint-present boolean, active mode, health/result code, timestamp, and an
approved test identifier when policy permits.

## 6. Fixed Chinatown profile input

The existing AL-001 Owner decision remains authoritative:

- target execution mode after field acceptance is `PAD_DIRECT`;
- exactly `GRAB` and `FRONTDESK_RECEIPT` are enabled for Chinatown;
- two physical printers are configured on site;
- `HOT_KITCHEN` is excluded, including fried/combo-egg paths;
- no endpoint, device token, credential, physical printer ID, or Pad pairing is
  part of the versioned profile;
- Store remains printing-disabled until Print Center configuration, AL-005B
  device readiness, and explicit acceptance complete.
- the enabled-module policy is evaluated before Print Job creation so excluded
  `HOT_KITCHEN` work does not become a failed/attention job.

The profile will express two logical destinations and the fixed module mapping.
Final role-code identifiers belong to the versioned profile contract and must
not be inferred from physical printer names or endpoints.

## 7. Dependency Repair Gate

Independent Draft PR #66 repairs three pre-existing Store-isolation defects:

1. an existing printer config cannot be moved across Stores through a changed
   request `store_id`;
2. automatic dispatch revalidates that an assigned printer belongs to the
   dispatch Store before renderer/transport execution;
3. PAD_DIRECT complete/fail scopes printer-health updates to the durable job
   Store instead of looking up a bare printer ID.

PR #66 targets `main` independently of this stacked package. It must enter
`main` before any executable AL-005 writer is promoted. This plan does not
copy that implementation into the stacked branch and does not treat a Draft PR
as merged capability.

## 8. Remaining implementation gates

| Gate | Current evidence | Required before writer |
|---|---|---|
| printer Store isolation | independent Draft PR #66 | merge and latest-main promotion review |
| strict mode handling | unknown non-blank values normalize to `REAL`; blank stored mode follows legacy `printing_enabled` fallback to `DISABLED` or `REAL` | compatibility decision and focused fail-closed tests |
| logical printer role | no current persisted role code | reviewed contract; migration only if persistence is required |
| assignment uniqueness | no `(store_id, module_code)` database uniqueness | read-only duplicate evidence and schema decision |
| assignment concurrency | current find-then-save upsert has no lock/unique guarantee | bounded repair and concurrency tests |
| pre-job module policy | current dispatch creates a job before assignment lookup and has no generic Store enabled-module gate | Store-scoped policy contract plus dispatcher gate before payload/job creation |
| printer/assignment FK integrity | schema does not prove same-Store relationship | migration/data-cleanliness decision or equivalent guarded writer |
| supported module set | assignment vocabulary exceeds current renderer set | exact validator allow-list/policy |
| whole-module transaction/idempotency | current operations are separate transactions | parent coordinator contract and terminal failure/replay semantics |
| printer CRUD audit | mode/assignment are audited; printer CRUD is not | audit contract before executable Owner workflow |
| PAD readiness | belongs to device/pairing/worker domain | AL-005B evidence |

No append-only migration is authorized by this plan. A future migration cannot
add unique constraints until Store-scoped duplicate evidence is collected in
an approved environment and normalization/remediation policy is reviewed.

## 9. Bounded delivery sequence

| Package | Scope | Gate |
|---|---|---|
| `AL-005P1` | immutable configuration contract, enabled-module policy, strict validator, canonical fingerprint, sanitized read-only planner | AL-004 contract in `main`; no writer or migration |
| `AL-005P2` | minimum prerequisite repairs for strict mode, pre-job module gate, role/assignment integrity, audit | separate Owner-reviewed compatibility/data/schema decisions |
| `AL-005P3` | parent-coordinated idempotent writer that leaves Store and assignments inactive | P1/P2 merged; full transaction/concurrency tests |
| `AL-005P4` | runtime binding and physical acceptance runbook/evidence | exact-SHA runtime approval; AL-005B device dependency |

The initial executable slice must not activate printing. It may at most create
or reconcile inactive logical configuration under a parent idempotency scope.
That inactive writer does not depend on AL-005B. Runtime binding in AL-005P4
and activation in AL-006 do depend on AL-005B readiness evidence.

## 10. Test contract

The future implementation must prove:

- deterministic fingerprint and immutable profile snapshot;
- rejection of endpoints, secrets, IDs, active mode, and unknown modules;
- duplicate role/assignment rejection;
- renderer/policy compatibility;
- excluded modules are rejected before payload/Print Job creation;
- Organization and Store authorization/isolation;
- same request replay, changed request conflict, terminal failed key;
- concurrent execution creates no duplicate assignments;
- transaction failure leaves no partial printer/assignment configuration;
- Store remains `DISABLED` and assignments remain inactive;
- no test print, print job, renderer, transport, Pad claim, or Worker action;
- no source Store printer, assignment, job, or device mutation;
- full printing regression, backend suite, compile, diff, and secret scan.

## 11. Acceptance and rollback boundary

Repository acceptance for a planning/contract package is not physical printing
acceptance. Runtime readiness later requires exact-SHA evidence for:

- two on-site logical-role bindings without exposing endpoints;
- exactly the approved Chinatown module policy;
- PAD_DIRECT selected only by an approved operator;
- paired-device Store scope and healthy Worker evidence from AL-005B;
- approved GRAB and FRONTDESK_RECEIPT test jobs;
- no HOT_KITCHEN job creation;
- Production/source Store continuity and no unrelated print/job changes.

Rollback of declarative planning is a Git revert. A future writer must define
record-level compensation and must never recommend `Flyway clean`, database
restore, automatic FAILED requeue, or destructive bulk deletion.

## 12. Explicit non-goals

- no Print Engine V2, renderer, routing, job-state, retry, or reprint rewrite;
- no Android Worker, pairing, device-token, or headless daemon change;
- no printer endpoint or site secret in Git;
- no public provisioning API or Owner UI in this package;
- no migration, Store/printer/assignment/device creation, or test print;
- no SSH, Docker, Flyway runtime, Staging/Production access, or deployment;
- no Order, Payment, KDS, Inventory, or `completeOrder` change.

## 13. Stop state

`AL-005_PRINTING_PREPARED_WAITING_FOR_DEPENDENCIES`
