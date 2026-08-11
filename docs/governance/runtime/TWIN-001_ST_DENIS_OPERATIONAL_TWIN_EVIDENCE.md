# TWIN-001 St-Denis Operational Twin Evidence

> Status: `PASS_WAITING_FOR_OWNER_FIELD_TEST`
>
> Evidence date: 2026-08-10, America/Toronto
>
> Unique stop state:
> `TWIN-001_ST_DENIS_OPERATIONAL_TWIN_READY_WAITING_FOR_OWNER_FIELD_TEST`

## Result

Owner approval `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL` completed its bounded
scope. The retained isolated Staging Organization and Store were reconciled in
place from `CURRENT_SYNTHETIC_BASELINE` to a Production-like St-Denis
Operational Twin. The configuration projector, staff/API reconciliation,
V10 parity validator, application smoke and final health checks passed.

Production remained read-only and was not queried during reconstruction.
Staging remained on Flyway V10. No migration, Flyway history edit, downgrade,
reset, raw database copy, physical printer binding or device pairing occurred.
The Production V7 to Staging V10 difference is therefore
`CURRENT_PRODUCTION_VERSION_DIFFERENCE`; observed V10 operational
incompatibilities and `BLOCKING_BEHAVIOR_DIFFERENCE` are both zero.

## Bound identities

| Item | Retained identity |
|---|---|
| Latest `main` used by runtime package | `53209823fa320cc56c31d04ee5c7719a83a78acc` |
| Deployed Staging release | exact clean checkout `53209823fa320cc56c31d04ee5c7719a83a78acc` |
| Production runtime | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`, Flyway V7 |
| Production scope | Organization `1 / LANZHOU_NOODLES`; Store `1 / 4483_R_SAINT_DENIS` |
| Staging scope | Organization `1 / STG005_ORG_20260809_R01`; Store `1 / STG005_SRC_20260809_R01` |
| Staging schema | ten successful Flyway rows, V10, zero failed rows |
| Manifest v2 | `ST_DENIS_TWIN_PARITY_MANIFEST_V2.json` |
| Manifest fingerprint | `1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68` |

The synthetic Staging Organization/Store codes and names were deliberately
retained as environment identities. They do not change Store-scoped business
configuration parity.

## Deterministic reconstruction and parity

The manifest-v2-bound writer projected the reviewed safe configuration in one
bounded transaction with exact database, user, Organization, Store and V10
guards, short statement/lock timeouts, fixed-order locks, exact pre-write
state checks and full pre-commit parity validation. It performed no delete or
truncate. The final read-only validator returned:

```text
TWIN001_PARITY|status=PASS|manifest=1c82440ca4677f9d1585369dc719a2f9b55d47e34344f5824f256775ec875e68|flyway=V10|blocking_behavior_difference=0
```

| Domain | Result | Evidence |
|---|---|---|
| `STORE` | `EXPECTED_ENVIRONMENT_DIFFERENCE / ACCEPTABLE` | Synthetic Staging identity retained; operational Store settings mapped exactly. |
| `MENU` | `MATCH` | 6 categories, 5 stations, 39 items (35 active), 380 options (330 active), 11 parent-option edges; complete bidirectional value and relationship validation passed. |
| `TABLES` | `MATCH` | 13 active rows. Manifest-observed Production shape contains 12 unique `table_code` values and 13 unique display names because two distinct rows use `T12`; Staging preserves exactly that reviewed shape and adds no extra duplicate/crossover. |
| `STAFF` | `MATCH_WITH_EXPECTED_CREDENTIAL_DIFFERENCE` | `owner/OWNER`, `manager/MANAGER`, `staffA/FRONTDESK`, `staffB/FRONTDESK`; username parity `YES`, role parity `YES`, credential parity `NO`. |
| `ACCESS` | `MATCH` | Exact Organization/Store membership and Owner permission topology validated; every test principal authenticated in Organization 1 / Store 1. |
| `FEATURES` | `MATCH` | Store printing remains `DISABLED`; bar-kitchen task setting remains false; the Planbook-accepted KDS feature-disabled boundary is retained. |
| `PRINTING` | `MATCH / TEST_HARDWARE_DIFFERENCE` | 4 logical printer configurations and 3 assignments match; all endpoints remain null and runtime Printing remains disabled. |
| `DEVICES` | `MATCH / TEST_HARDWARE_DIFFERENCE` | 7 safe topology rows match; configured device-token count is zero and no pairing occurred. |
| `SCHEMA` | `CURRENT_PRODUCTION_VERSION_DIFFERENCE` | Production V7 maps through reviewed V8/V9/V10 to current Staging V10; validator and application smoke passed with zero blocking behavior difference. |
| `OPERATIONAL_WORKFLOWS` | `MATCH_WITH_REVIEWED_RUNTIME_BOUNDARIES` | Safe automated Owner/Staff, workspace, menu, table, Frontdesk beverage-order and realtime flows passed; KDS positive use and physical hardware remain separately gated. |

There is no missing menu relationship, cross-Store relationship, unintended
extra configuration row, printer endpoint or device credential. Manifest v2
and the V7-to-V10 mapping remain the only Production-derived reconstruction
inputs.

## Automated Staging smoke

Four independent Staging principals completed real login and Store-scope
verification. The smoke then passed menu catalog/revision, table board, Owner
overview, menu-management context, staff list and Frontdesk board reads. A
synthetic, non-PII pickup order exercised:

`draft create -> beverage item add -> quantity update -> submit -> preparing ->
ready -> served -> order complete -> history verification`

The WebSocket info endpoint returned HTTP 200. KDS and Printing endpoints
returned the expected feature-disabled response; no hardware write was
attempted. The private mode-0600 smoke evidence is:

`/srv/restaurant-pos/staging/evidence/twin001-operational-smoke-53209823fa320cc56c31d04ee5c7719a83a78acc.txt`

Its SHA-256 is
`0236f9fa63654102d77511abdbd7d1bdb28028dc91e0bf99ddbebe01d35dcb4b`.

## Runtime health, credentials and continuity

Final Staging frontend, backend health and WebSocket endpoints each returned
HTTP 200. The release checkout is exact and clean; Staging backend, database
and nginx are running with restart count zero. The runtime evidence remains a
mode-0600 file at:

`/srv/restaurant-pos/staging/evidence/twin001-runtime-53209823fa320cc56c31d04ee5c7719a83a78acc.txt`

The independent Staging credential retrieval path is:

`/srv/restaurant-pos/staging/state/twin001-staff-credentials-v1.json`

It is mode `0600`. No credential value is recorded here, in Git, stdout or the
manifest.

Production frontend and backend health remained HTTP 200. Before/after
continuity retained the same running container identities, image identities,
start times and restart count zero:

| Production component | Container prefix | Image prefix | Started UTC |
|---|---|---|---|
| backend | `e5027dc08709` | `36daa6697ff7` | `2026-07-24T19:44:29Z` |
| database | `c2ab37fec6ac` | `57c72fd2a128` | `2026-07-11T12:09:37Z` |
| nginx | `a5c37d6f289c` | `781cb93ee4e8` | `2026-07-24T19:44:29Z` |

## Safety and delivery evidence

- Production reconstruction-round query count: `0`; Production write count:
  `0`.
- No customer/PII, historical Production order/item, payment, credential,
  password/hash, token/cookie/session secret, raw environment, database
  secret, printer secret, device credential or unrelated Store/Organization
  data was read or copied.
- Staging credentials were generated/reconciled independently through the
  existing API and were never copied from Production.
- Dependency repair PR #111 merged as
  `53209823fa320cc56c31d04ee5c7719a83a78acc`; its fresh Agent 6 review was
  `ACCEPT` and all 11 focused tests plus the 390-test backend regression passed.
- Fresh Agent 6 closure review is `ACCEPT`. This documentation/evidence-only
  closure enters `main` through the repository Auto-Merge policy and does not
  authorize a Staging rebind.

## Gates and stop

`TWIN-001 = PASS` for reconstruction, deterministic V10 parity and safe
automated smoke. It is ready for Owner field testing; this evidence does not
perform or claim that manual acceptance.

The next Owner Gate is `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP`. Real/home printer
binding, positive KDS enablement and physical Pad pairing remain
`SEPARATE_OWNER_RUNTIME_GATE_PENDING` and are not blocking core Twin readiness
under the reviewed feature-disabled/test-hardware boundaries.

Unique stop:
`TWIN-001_ST_DENIS_OPERATIONAL_TWIN_READY_WAITING_FOR_OWNER_FIELD_TEST`.
