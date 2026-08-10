# TWIN-001 St-Denis Production-like Staging Twin Plan

> Status: `MANIFEST_V2_RECONSTRUCTION_READY_READ_ONLY`
>
> Current stop state: `TWIN-001_MANIFEST_V2_RECONSTRUCTION_READY_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`
>
> Owner decision date: 2026-08-10, America/Toronto

## 1. Owner decision and scope

Owner designates Staging's long-term role as a Production-like St-Denis
Operational Twin and mandatory pre-Production validation environment. The
Twin is planned, not yet established; the immediate FT-001 route is therefore:

`Production St-Denis -> parity model -> Staging St-Denis Twin -> development/
bug-fix loop -> automated validation -> Owner manual validation -> Production
promotion`

Manifest v2 now supplies deterministic, schema-valid safe configuration input
and an explicit V7-to-V10 mapping. It does not authorize a Staging writer.

This plan is governance and reconstruction planning only. The Owner-approved
bounded Production configuration read has completed; it performed no Staging
mutation, deployment, migration, printer binding, module implementation,
Chinatown onboarding, or Production promotion.

The existing synthetic St-Denis Organization/Owner/source Store/menu remains a
`CURRENT_SYNTHETIC_BASELINE`. It is useful Twin foundation evidence, but it is
not automatically a Production-parity Twin. Do not delete, reset, or rebuild
it destructively. A later TWIN-001 runtime package must explicitly decide what
can be reused, what differs, and what must be reconciled.

The resulting [sanitized parity manifest](../runtime/ST_DENIS_TWIN_PARITY_MANIFEST.md)
and [inventory evidence](../runtime/TWIN-001_PRODUCTION_INVENTORY_EVIDENCE.md)
are the only current Production-derived inputs. They identify Production
Store `1 / 4483_R_SAINT_DENIS / 4483 R. Saint-Denis` in Organization
`1 / LANZHOU_NOODLES / Lanzhou Noodles`. They contain no secrets, PII,
customer/order/payment data, endpoint credentials, token hashes, or raw dump.

## 2. Operational parity contract

The Twin should use the same shared application code, generic Store logic,
migration chain, authorization contracts and operational modules as
Production. It must not introduce `if staging`, `if storeId == 1`,
`if StDenis`, a duplicate St-Denis engine, or copy/paste business logic.

Parity domains are:

| Domain | Required classification |
|---|---|
| `APP` | application behavior |
| `SCHEMA` | migration chain and schema contract |
| `STORE` | Store configuration |
| `MENU` | categories, items, prices, options and relationships |
| `TABLES` | station/table configuration |
| `STAFF` | Production-equivalent usernames and roles |
| `ACCESS` | Owner/membership/permission topology |
| `FEATURES` | feature flags and relevant operational settings |
| `PRINTING` | configuration model, roles, assignments and routing |
| `DEVICES` | safe device/module configuration structure |
| `OPERATIONAL_WORKFLOWS` | Owner, Frontdesk, KDS, ordering and Pad behavior |

Every domain must emit exactly one of `MATCH`, `EXPECTED_DIFFERENCE`,
`BLOCKING_DIFFERENCE`, or `NOT_YET_VERIFIED`. “Looks similar” is not evidence.

## 3. Data and identity safety

The Twin reconstructs sanitized configuration, not a raw Production database.
The future approved read may include only explicitly selected configuration
columns for Store config, menu graph, stations, tables, staff usernames/roles,
role mappings, feature flags, printing topology, and safe device/module
structure. A table containing secret fields must be queried by an explicit
column list; `SELECT *` is prohibited.

Never read, export, copy or stage customer PII, historical orders, payments,
payment credentials, Production passwords or unsafe password hashes, tokens,
cookies, SSH keys, Production secrets, printer credentials/endpoints where
sensitive, device credentials, or unrelated Organization/Store data. Synthetic
or anonymized fixtures are required for historical order-shape tests.

Staging staff usernames and roles should be Production-equivalent where safe:

`username parity = YES; role parity = YES; credential parity = NO`.

All Staging credentials remain independent synthetic/test credentials. A
Production password or hash must never be reused.

## 4. Completed read gate and historical reconstruction gate

The completed read gate was independently named:

`PRODUCTION_ST_DENIS_CONFIGURATION_READ_APPROVAL`

Its approval package bound:

- exact Production runtime identity and exact Store 1 identity;
- exact allowed configuration read domains and columns;
- exact prohibited domains, including all business/history/secret data;
- bounded query strategy, timeout/resource/locking safety and no-write proof;
- sanitized parity-manifest format and evidence retention location;
- Staging reconstruction target and expected differences;
- rollback/no-mutation guarantee and abort conditions.

The read gate is complete. Its next runtime gate was independently named
`TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`; the Owner later granted it. The
pre-write source check failed before runtime entry, so that approval produced
no Staging mutation and cannot authorize the new corrected Production read.

## 5. Reconstruction strategy

The long-lived model is:

`Production read-only configuration inventory -> sanitized parity manifest ->
generic Twin reconstruction/provisioning -> parity validator`

A one-time bounded extraction may establish the initial Twin. The default
process must never become `rsync Production DB -> Staging`, raw Production
`pg_dump` into Staging, or complete Production database copying. The manifest
must be auditable, repeatable, versioned, secret-free and reusable by a future
Store Profile.

Safe environment differences must be explicit: synthetic credentials, Owner
home test-printer endpoints, loopback exposure, test devices, sanitized
historical fixtures and environment secrets. Business code paths remain the
same.

## 6. Printing and hardware direction

Staging must eventually support controlled real printing against Owner home
test printers while never reaching a Production printer endpoint or secret.
The model preserves Production roles and routing (`PAD_DIRECT`, `GRAB`,
`FRONTDESK_RECEIPT`, `HOT_KITCHEN` and other current semantics) but records a
home endpoint as `TEST_HARDWARE_DIFFERENCE`, not a parity failure.

Future sequence:

`Printing DISABLED -> explicit Owner test-print gate -> HOME_TEST_PRINTER_BINDING
-> controlled real print -> order/print workflow acceptance`

`HOME_TEST_PRINTER_BINDING` is a separate runtime gate. This plan does not
implement VPN, port forwarding, network exposure, printer binding or a test
print.

## 7. Owner manual field-test checklist

The checklist is intentionally evolvable and is not a stress test:

- `LOGIN/AUTH`: Owner and Staff login, logout, refresh, session persistence.
- `OWNER/ADMIN`: dashboard, Store access, menu management, staff/role
  visibility and Store configuration.
- `FRONTDESK`: table board, open table, dine-in, takeout, submit/edit/update
  order and order-state refresh.
- `MENU`: category/item/price/option parity and notes/modifiers.
- `KITCHEN/STATIONS`: routing, KDS visibility, item completion, order
  completion and ready flow.
- `PRINTING`: configuration parity, test-printer binding, test print, GRAB,
  FRONTDESK_RECEIPT, kitchen routing and failure/recovery.
- `PAD`: login/binding, menu, submit, Worker state and reconnect/recovery.
- `PERSISTENCE`: refresh, logout/login, browser/app restart and same-image
  runtime restart where separately required.

Owner may extend this checklist during `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP`.

## 8. Bug-fix loop and promotion invariant

Every Owner finding is classified as `UI_BUG`, `AUTH_BUG`, `DATA_PARITY_BUG`,
`BUSINESS_LOGIC_BUG`, `PRINTING_BUG`, `PAD_BUG`, `KDS_BUG`,
`STAGING_ONLY_BUG`, or `PRODUCTION_PARITY_GAP`.

A bounded repository/tooling bug follows:

`reproduce -> repair -> tests -> Agent 6 -> PR -> auto merge -> exact-SHA
Staging deploy -> automated regression -> Owner retest`.

Any `BLOCKING_DIFFERENCE` makes Production promotion `NO_GO`. A candidate may
enter `REL-001` only after code and schema are validated in the Twin, the
parity manifest has no blocking difference, daily workflows pass, relevant
printing/device flows pass, and required Owner manual validation is complete.

## 9. Release identity, recurring drift, and recovery handoff

TWIN-001 hands release policy to the canonical
[Agile Loop release/promotion policy](../AGILE_LOOP_OPERATING_MODEL.md#83-canonical-release-promotion-drift-and-recovery-policy)
and does not create a second RC authority. After Twin acceptance, the release
package must freeze an immutable `RC_ID` bound to exact source SHA, backend and
frontend artifact digests, relevant Android artifact identity, migration set,
parity-manifest identity, automated result, Owner result, and build metadata.
Later `main` movement never changes that RC. Production may promote only the
same artifact digests accepted in Staging; if tooling cannot carry those
digests, the promotion remains a documented implementation gap and `NO_GO`.

Recurring Production-to-Twin checks are read-only and emit sanitized manifests.
They classify each result as `MATCH`, `EXPECTED_ENVIRONMENT_DIFFERENCE`,
`SANITIZED_DATA_DIFFERENCE`, `TEST_HARDWARE_DIFFERENCE`,
`BLOCKING_BEHAVIOR_DIFFERENCE`, or `NOT_YET_VERIFIED`. Detection only reports
and classifies. It never automatically mutates or overwrites Staging; an
explicit Owner-approved, Owner-triggered Twin sync request is required for
bounded Twin sync. This plan grants no Production-read or Staging-mutation
authority. Any `BLOCKING_BEHAVIOR_DIFFERENCE` is a Production `NO_GO`.

Production incident handling uses
`APPLICATION_ROLLBACK_COMPATIBILITY_GATE`: rollback is preferred only when
the previous application artifact is proven compatible with the current schema
and migration state; otherwise use a bounded roll-forward repair. Database
restore, Flyway history changes, volume deletion, and other destructive
rollback actions remain separately Owner-gated. Backup existence is not
recoverability; integrity evidence, isolated restore rehearsal status, and
recovery-point/time boundaries must be recorded before release maturity is
claimed.

## 10. Definition of Done and deferred routes

TWIN-001 requires APP, SCHEMA, STORE, MENU, TABLES, STAFF, ACCESS, FEATURES,
PRINTING, DEVICES and OPERATIONAL_WORKFLOWS classifications; no raw sensitive
data leakage; an automated parity manifest/check; Owner daily-flow acceptance;
and zero `BLOCKING_DIFFERENCE` results. Completion does not automatically
start modularization.

The next loop after a completed Twin is
`OWNER_FIELD_TEST_AND_BUG_FIX_LOOP`. Its only completion signal is the Owner
decision “可以进行模块化了”. Until then, modular provisioning remains
`ARCHITECTURE_DIRECTION_APPROVED / IMPLEMENTATION_DEFERRED /
WAITING_FOR_OWNER_FIELD_TEST_COMPLETION`.

Chinatown remains a future second Production Store. It is
`DEFERRED_BY_OWNER_ST_DENIS_TWIN_AND_FIELD_TEST_PRIORITY`; its existing
AL-003/REL-001 plans, code and historical evidence are preserved. After a
separate modularization DoD, Planbook must issue `CHINATOWN_RESUME_GATE` before
resuming STG-009 Phase B or AL-003 validate/execute/replay. No second clone
engine may be created.

## 11. Design-only TWIN-001_STAGING_RECONSTRUCTION_PLAN

The reconstruction plan remains declarative and has not run; the granted
execution package stopped before its first runtime action:

1. Bind a fresh exact release and compatible schema decision; do not copy
   Production V7 history into Staging V10 or run Flyway as part of this plan.
2. Reuse the generic Store/Profile and provisioning modules already in `main`.
   Project Store, Organization, menu graph, stations, tables, staff/access,
   feature flags, printing topology and devices through idempotent, Store-scoped
   contracts. Never copy Production IDs, credentials, printer endpoints, or
   device token hashes.
3. Preserve the synthetic Owner and source Store as an environment boundary.
   Replace credentials with a private synthetic value and keep Printing
   `DISABLED` until the separate home-printer gate.
4. Reconcile the manifest classes before any write: V7/V10 schema is a
   blocking behavior difference; menu/table/Store values are sanitized data
   differences; staff/access/features are expected environment differences;
   printing/devices are test-hardware differences; workflows remain
   not-yet-verified.
5. Execute only after the Owner reconstruction gate with a dry-run diff,
   one-use approval, rollback/abort contract, no-cross-Store assertion,
   duplicate detection, and before/after sanitized evidence. Ambiguous
   identity fails closed.
6. Validate every domain and retain a versioned manifest fingerprint. No
   automatic sync, raw dump, broad clone, destructive cleanup, or Production
   write is permitted.

## 12. Current stop

`TWIN-001_MANIFEST_V2_RECONSTRUCTION_READY_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`

The earlier reconstruction approval stopped before runtime entry because v1
was incomplete. The corrected read now supplies
[manifest v2 completion evidence](../runtime/TWIN-001_MANIFEST_V2_COMPLETION_EVIDENCE.md)
and [V7-to-V10 mapping](../runtime/V7_PRODUCTION_TO_V10_TWIN_CONFIGURATION_MAPPING.md).

Local PostgreSQL 16 evidence proves the reviewed forward path
`V7 -> V8 -> V9 -> V10`, current-candidate startup, data-shape preservation,
and second-start idempotency. The raw version delta is therefore
`CURRENT_PRODUCTION_VERSION_DIFFERENCE`, not authority to downgrade Staging.
The aggregate `SCHEMA` result remains `BLOCKING_BEHAVIOR_DIFFERENCE` until a
reconstructed St-Denis Twin actually operates and passes parity validation on
V10.

The next TRUE OWNER GATE is `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`.
No Staging reconstruction, Twin Sync, Production read, schema action, or other
runtime action is authorized by this plan until that distinct approval.
