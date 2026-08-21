# TWIN-001 Staging Reconstruction Schema and Input NO-GO Evidence

> Status: `RECONSTRUCTION_NO_GO_MANIFEST_INPUT_NOT_EXECUTABLE`
>
> Date: `2026-08-10` (America/Toronto)
>
> Owner authority received: `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`

## 1. Decision

`TWIN-001 = NO_GO` and the aggregate parity result remains
`SCHEMA = BLOCKING_BEHAVIOR_DIFFERENCE`.

The observed Production V7 versus Staging V10 number difference is not a
request for backward parity. Local machine evidence proves that the reviewed
forward chain `V7 -> V8 -> V9 -> V10` is valid for the current candidate and
preserves a representative St-Denis configuration shape. That observation is
classified separately as `CURRENT_PRODUCTION_VERSION_DIFFERENCE`.

The aggregate schema domain cannot close yet because no reconstructed
St-Denis Twin has operated on V10. Reconstruction did not start: the retained
parity manifest is not a deterministic or schema-consistent writer input.
Downgrading Staging, removing V8/V9/V10, editing Flyway history, reversing a
migration, rebuilding Staging from Production V7, or migrating Production was
not attempted and remains prohibited.

## 2. Repository and runtime boundary

| Item | Result |
|---|---|
| fresh `origin/main` | `295ed4b1278750dfc5492c3109e0ac767e158ffd` |
| reviewed migration chain | V1 through V10; repository filenames and retained checksums match the recorded runtime chain |
| retained Production | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`, Flyway V7 |
| retained Staging | `1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c`, Flyway V10 |
| Staging reconstruction writes | none |
| Staging release/rebind/restart/Flyway | none |
| Production reads or lifecycle actions in this round | none |
| Production mutation | none |

The prior inventory evidence proves `Production inventory = COMPLETE`, a
bounded accepted read scope, unchanged before/after continuity, and prohibited
data exclusion. This round stopped before runtime entry because the reviewed
reconstruction source failed the pre-write executability check. Therefore no
fresh Staging pre-write snapshot or new Production continuity snapshot was
needed or collected; the last retained continuity remains the inventory
evidence and is not promoted to a fresh observation.

## 3. V7 to V10 forward-path verification

The repository migrations are append-only:

| Version | Operation | Existing operational tables changed |
|---|---|---|
| V8 | adds `owner_store_onboarding_requests` and its Store index | none |
| V9 | adds `staging_synthetic_bootstrap_requests` and two indexes | none |
| V10 | adds `owner_store_menu_clone_requests`, its constraint, and index | none |

A task-owned, loopback-only PostgreSQL 16.14 database was created with no
Production/Staging connection and no real credential, order, customer,
payment, printer endpoint, device token, or secret. The current candidate
first applied V1--V7 exactly. A safe representative Store configuration was
then created with the manifest's domain shape: 6 categories, 39 items, 380
options with parent relationships, 5 stations, 13 tables, 1 Owner/access
topology, 6 KDS screen codes, 4 endpoint-free logical printers, 3 assignments,
and 7 token-free devices.

The candidate then started normally against V7:

- Flyway applied only V8, V9, and V10 and reported schema V10;
- all ten history rows were successful with the reviewed checksums;
- Hibernate `ddl-auto=validate` passed;
- authoritative health and SockJS `/ws/info` returned HTTP 200;
- the pre/post safe configuration fingerprint remained
  `781da2ae8bf84f79710d1e60e90c1e8a`;
- the safe domain counts remained `6/39/380/5/13/4/3/7`;
- the three new request tables contained zero rows, proving no implicit
  onboarding, synthetic bootstrap, or menu clone;
- a second normal startup reported V10 and `No migration necessary`.

This is local migration/application compatibility evidence. It proves the
intended future Production migration direction and rejects any backward-parity
interpretation. It is not Production migration evidence, Staging
reconstruction evidence, old-application-on-V10 rollback evidence, or Owner
field acceptance.

## 4. Reconstruction-source failure

The reviewed manifest cannot be executed deterministically from its retained
content. It records counts/fingerprints but omits values required by its own
reconstruction contract, including:

- the complete 380 option rows with every item association, bilingual name,
  price, active flag, sort order, and parent relationship;
- complete item bilingual names, category/station relationships, and ordering;
- complete station names/order and unambiguous dining-table code/order values;
- complete KDS display configuration values;
- all four logical printer configuration values and three assignment values;
- stable safe device topology identities needed to distinguish seven rows.

The manifest also describes its executed schema columns using names that do
not exist in the reviewed V1--V7 schema whose V1 checksum is identical to the
Production checksum:

| Manifest claim | Repository V7 authority |
|---|---|
| `stores.is_active` | no such Store column |
| `roles.status` | no such Role column |
| `dining_tables.area` | column is `area_name` |
| `store_kds_configs` with `layout_mode/display_density/is_active` | table is `store_kds_display_configs`; those columns do not exist |
| `printer_configs.connection_timeout_ms/encoding` | columns are `timeout_ms/text_encoding` |
| `printer_assignments.assignment_type/font_size_mode/copies` | columns are `module_code/font_size/takeout_receipt_copies` |

Historical inventory evidence is preserved and not silently rewritten. These
contradictions mean the report cannot prove that the documented query shapes
executed as stated, and the omitted row values cannot be reconstructed from a
fingerprint. Repository seeds are explicitly non-authoritative and cannot be
used to fill the gaps. A writer assembled from inference would violate the
reviewed manifest-only source rule and could create wrong menu, routing,
printing, or device behavior.

## 5. Domain result

| Domain | Result | Reason |
|---|---|---|
| APP | `EXPECTED_ENVIRONMENT_DIFFERENCE` | retained Production runs the old application; the Twin continues to target the latest approved shared candidate |
| SCHEMA observed version delta | `CURRENT_PRODUCTION_VERSION_DIFFERENCE` | Production V7 and Staging V10 are intentionally different points on the reviewed forward chain |
| SCHEMA aggregate | `BLOCKING_BEHAVIOR_DIFFERENCE` | the forward path passes locally, but reconstructed St-Denis-on-V10 behavior does not yet exist to verify |
| STORE | `SANITIZED_DATA_DIFFERENCE` | no reconstruction attempted; complete deterministic target payload is absent |
| MENU | `SANITIZED_DATA_DIFFERENCE` | 380-row graph payload is incomplete; no safe writer input |
| TABLES | `SANITIZED_DATA_DIFFERENCE` | target values/order are incomplete or ambiguous; no write attempted |
| STAFF | `EXPECTED_ENVIRONMENT_DIFFERENCE` | Production-equivalent username/role remains intended; Production credential parity remains forbidden |
| ACCESS | `EXPECTED_ENVIRONMENT_DIFFERENCE` | environment-local IDs and synthetic credentials remain required; capability mapping remains unverified |
| FEATURES | `EXPECTED_ENVIRONMENT_DIFFERENCE` | Printing remains disabled pending its independent hardware gate |
| PRINTING | `TEST_HARDWARE_DIFFERENCE` | logical payload is incomplete and no home-printer binding is authorized |
| DEVICES | `TEST_HARDWARE_DIFFERENCE` | no Production credential/token may be copied and no pairing is authorized |
| OPERATIONAL_WORKFLOWS | `NOT_YET_VERIFIED` | reconstruction and automated smoke did not run |

The forward schema test does not reduce the Twin blocking-difference count to
zero. It isolates the blocker: current Production version difference is
acceptable in principle; missing trustworthy reconstruction input and absent
V10 Twin behavior evidence are not.

## 6. Required bounded repair and Owner gate

The next action is a new TRUE OWNER GATE:

`TWIN-001_RECONSTRUCTION_MANIFEST_COMPLETION_READ_APPROVAL`

If approved, it must authorize only a corrected, bounded, read-only,
explicit-column capture of the previously approved safe configuration domains.
The capture must:

1. bind the exact current Production runtime, Store, repository migration
   checksum, and corrected V7 table/column names before any business query;
2. retain a complete sanitized reconstruction payload, not only counts or a
   hash, for menu/options/relationships, stations/tables, KDS settings,
   logical printing topology, and safe device topology;
3. keep credentials, hashes, tokens, cookies, sessions, endpoint secrets,
   customer/order/payment/history data, and unrelated Stores excluded;
4. use read-only transactions, explicit bounds, timeouts, Store predicates,
   prohibited-data scans, and before/after continuity;
5. receive independent review before any Staging writer is planned or run.

Any new migration, schema operation, Flyway history edit, Production migration,
destructive operation, broader Production data read, or Production write
remains a separate TRUE OWNER GATE. The already-granted reconstruction
approval does not authorize filling an incomplete manifest by inference or
performing this new Production read.

## 7. Stop state

`TWIN-001_RECONSTRUCTION_NO_GO_WAITING_FOR_MANIFEST_COMPLETION_READ_APPROVAL`

No credential was created, reused, rotated, or disclosed. No automated Twin
smoke or parity validator was run because no reconstructed Twin exists. No
module, Chinatown, REL-001, Production promotion, printer, or Pad action began.
