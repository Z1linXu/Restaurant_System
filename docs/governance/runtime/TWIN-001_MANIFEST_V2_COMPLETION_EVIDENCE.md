# TWIN-001 Manifest V2 Completion Evidence

> Status: `MANIFEST_V2_RECONSTRUCTION_READY_READ_ONLY`
>
> Date: `2026-08-10` (America/Toronto)

## Result

The Owner-approved bounded completion read produced
[manifest v2](ST_DENIS_TWIN_PARITY_MANIFEST_V2.json) with fingerprint
`1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68`.
It contains deterministic manifest-local references for every item and option,
including all parent-option edges. It does not retain Production IDs,
credentials, hashes, endpoint values, ports, PII, customer/order/payment data
or raw environment values.

| Safe domain | Rows |
|---|---:|
| categories / stations / items / options | 6 / 5 / 39 / 380 |
| tables / KDS configurations | 13 / 6 |
| staff / organization memberships / Store memberships / user-stations | 4 / 1 / 1 / 0 |
| OWNER role permissions | 0 |
| logical printers / assignments / receipt templates / devices | 4 / 3 / 0 / 7 |

The fresh read-only Staging baseline remained V10 with `4/3/13/38/0/0/0/0`
for categories/stations/items/options/tables/printers/assignments/devices and
Printing `DISABLED`. No Staging write occurred.

## Query and continuity safety

Production remote `psql` invocations: seven — one connection-identity probe,
one SQL compilation failure that returned no rows, and five completed bounded
aggregate projections while repairing the collector contract. Staging remote
`psql` invocations: two read-only baseline/comparison projections. The final
artifact is from the last paired projection. Each completed projection used the
v2 allowlist's read-only transaction and timeouts. No privilege change, lock
escalation, migration, restart, deployment, credential action or write occurred.

Before and after the final read, `cloud-db-1`, `cloud-backend-1`, and
`cloud-nginx-1` were running with the same retained start times and restart
count `0`. Staging was queried read-only twice and not changed.

## Validation

- Complete option graph: `PASS` — 380 `OPT-*` rows, each resolves to an
  `ITEM-*`; every non-null parent resolves to an `OPT-*`.
- Required category/station and printer-assignment relationships: `PASS`.
- V7 source / V10 canonical mapping: `PASS`; see
  [mapping](V7_PRODUCTION_TO_V10_TWIN_CONFIGURATION_MAPPING.md).
- V10 manifest schema contract and deterministic fingerprint: `PASS`.
- Prohibited-column, PII and secret scan: `PASS`.
- Staging mutation: `NOT_PERFORMED`.

## Domain classification after v2

| Domain | Classification |
|---|---|
| APP | `EXPECTED_ENVIRONMENT_DIFFERENCE` |
| SCHEMA version position | `CURRENT_PRODUCTION_VERSION_DIFFERENCE` |
| SCHEMA aggregate | `BLOCKING_BEHAVIOR_DIFFERENCE` — no Twin has yet operated on V10 |
| STORE, MENU, TABLES | `SANITIZED_DATA_DIFFERENCE` — deterministic input is ready; no reconstruction was run |
| STAFF, ACCESS, FEATURES | `EXPECTED_ENVIRONMENT_DIFFERENCE` |
| PRINTING, DEVICES | `TEST_HARDWARE_DIFFERENCE` |
| OPERATIONAL_WORKFLOWS | `NOT_YET_VERIFIED` |

## Stop

`TWIN-001_MANIFEST_V2_RECONSTRUCTION_READY_WAITING_FOR_STAGING_RECONSTRUCTION_APPROVAL`

The next TRUE OWNER GATE is `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`.
It must separately define an idempotent Staging-only writer, dry-run/abort
contract, before/after snapshots, and validation. This evidence authorizes no
Staging reconstruction, Field Test, modularization, Chinatown or Production
release.
