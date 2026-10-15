# Phase 2 Store boundary repair evidence

PRIMARY_REPAIR_GOAL: close enabled Profile/Device/BOM cross-tenant and concurrency
paths inside isolated Commercial development. EXPECTED_REPAIR_CLASS: HIGH.
P2-PROFILE-04 / PRINT-05 / INV-07 / FIXTURE-08 share this bounded root review.
Production and Staging were not mutated. All databases, accounts and orders below
are locally generated synthetic fixtures; no real printing or remote dispatch.

| Boundary / misuse | Old call chain | Repair and negative evidence |
| --- | --- | --- |
| Private Profile list/read | Owner -> global root list/version/artifacts | StoreProfileAccessService filters root first; exact active Organization OWNER; foreign/unowned/version guesses denied before child lookup (StoreProfileIsolationTest) |
| Private create catalog / provision | static initial catalog + direct global profile lookup | scoped catalog omits inaccessible identifiers; provisioning checks root before version/artifact (catalog/Service tests) |
| Private compatibility clone / replay | descriptor registry + same-Org Store checks but no private Profile grant | descriptor requiredStoreProfileCode; public orchestration checks before reservation/replay, locked transaction repeats check (OwnerStoreMenuCloneServiceImplTest, existing clone graph suites) |
| Legacy owner inference | V19 same master copied to all Organizations | V38 uses only exact Store Profile/version/fingerprint and one distinct Org; ambiguous/unreferenced roots stay PRIVATE-unowned (StoreProfileIsolationPostgresTest) |
| Device token with moved/malformed Store | active token -> touchLastSeen without current Org relationship | constant-time hash and current active Store/Org checks before touch; revoked/inactive/moved Store denied (StoreDeviceServiceImplTest) |
| Foreign printer/job | printer path/body and lifecycle scope | existing assignment/payload checks retained; foreign assignment and all seven Pad operations denied, zero write/payload (PrinterAssignmentIsolationTest, PadPrintJobServiceImplTest) |
| BOM foreign stock / overlap | unscoped findById -> read-modify-save per BOM | aggregate demand; same-Store pessimistic locks by sorted ID; validate all before writes; transaction mandatory (OrderServiceImplTest, InventoryConsumptionPostgresTest) |
| Concurrent/repeated submission | draft status read without row lock | existing locked refreshing Order reader reused; second submission cannot consume; add-only batch idempotency unchanged |
| Unsafe local defaults | implicit local profile enables seed/header and schema update | explicit commercial profile validates loopback synthetic target, Flyway, no seed/header/physical I/O; scanner rejects real defaults (CommercialSafetyConfigTest, Python scanner tests) |

## Actual validation

- Clean source baseline is separately recorded in PHASE2_BASELINE_MANIFEST.md.
- Root full backend regression: `mvn -o test`, Java 17.0.14, all six PostgreSQL
  opt-in suite variables explicitly pointed to the dedicated PostgreSQL 16.14
  loopback cluster at port 55439. Result: **968 tests, 0 failures/errors, 0 skipped**.
  Log: `/private/tmp/restaurant-commercial-baseline/root-full-postgres-retry.log`.
- Subsequent small uniform-denial and negative-test additions: 16 focused tests,
  0 failures/errors/skips, `root-focused-final-retry.log`. One previous assertion
  expected missing Profile 400; changed to the intentional uniform 403 contract.
- PG evidence includes two concurrent stock consumers (one succeeds at stock 5 /
  demand 3 each, final 2 and ledger -3), foreign second ingredient zero partial
  writes, later Order failure rollback, transaction requirement, V37 upgrade and
  fresh migration replay; Profile JSON/fingerprints unchanged.
- Existing optional V30 regression had an unbounded latest target with an
  assertion for exactly one migration. It now explicitly targets V30; new V38
  migration tests separately cover latest clean/upgrade and replay.
- `verify-commercial-baseline.py`, `scan-commercial-defaults.py`, three Python
  scanner tests, governance validator and diff whitespace checks PASS.
- Independent Agent 6 and integrated Auth/Offline regression: pending in the
  phase acceptance report; this module evidence alone does not close G2.

## Limits and rollback

V1–V37 remain byte-for-byte unchanged. V38 only adds root visibility/owner columns,
a check/index and conservative metadata inference. It does not rewrite business
orders, kitchen snapshots, rendered receipts, Profile JSON or fingerprints. No
publish API exists; no test pretends otherwise.

Before any real release, inspect ambiguous/unowned Profiles and current merchant
roles with separately authorized evidence. Never guess a private owner to unlock
a deployment. Reverting the application must retain private-access deny rules;
keep additive columns and use a reviewed forward fix. Local disposable fixture DBs
may be rebuilt by the guarded test helper. Do not run old global-catalog code on
the new schema as a purported safe rollback. Inventory rollback must not replay
consumption or reconstruct real ledger rows. Full restock/prep remains unimplemented
and documented as such, not silently enabled.

The scanner's scope is new Commercial defaults and fixtures, not historical
migrations, dated evidence or reviewed one-off deployment targets. Its failures
never print a matched credential. The added GitHub workflow runs these read-only
checks; no deployment or real database access is part of CI. Hardware and installed
APK claims remain HARDWARE_QA_PENDING / UNKNOWN.
