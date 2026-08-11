# TWIN-001 Staging Reconstruction Tooling Evidence

> Status: `REPOSITORY_VERIFIED_AWAITING_IN_MAIN_RUNTIME_EXECUTION`
>
> Date: `2026-08-10` (America/Toronto)

## Result

The Owner granted `TWIN-001_STAGING_RECONSTRUCTION_APPROVAL`. Fresh read-only
Staging evidence proved that 37 of the retained 38 synthetic options map by
stable item/option code. The remaining synthetic-only
`cucumber_salad/remove_garlic` row has one explicit manifest target,
`remove_peanut`; the projector replaces that row in place. It preserves every
baseline ID and adds only the missing `2/2/26/342` menu rows plus the reviewed
table, KDS, logical printing and device topology. It performs no reset or
deletion.

The repository package adds:

- a manifest-fingerprint-bound, exact-V10, exact-Store projector with read-only
  plan/full validation and a single transactional apply with fixed-order table
  locks, exact in-lock baseline recheck and complete pre-commit parity checks;
- a secret-FD staff reconciler that uses the existing Staff API and BCrypt
  service for independent Staging credentials, binds fresh exact-SHA/V10
  runtime evidence and API workspace identity, and verifies every staff login;
- regression tests and the reconstruction runbook.

The configuration writer never contacts Production, reads an environment
file, reads credentials/tokens/order/payment/customer data, sets a printer
endpoint/device token, changes Flyway, or creates a migration. It reads only
the aggregate count of non-null Staging device-token hashes and requires zero;
it never selects a token/hash value. Printing remains
`DISABLED` and hardware binding remains separately gated.

## Verification

- manifest/artifact/projector/staff tests: `11 PASS`;
- full backend regression: `390 PASS / 0 failure / 0 error / 3 skipped`;
- Python compile: `PASS`;
- fresh remote projector plan: `PLAN_READY`, exact retained baseline,
  `delete=0`;
- task-owned loopback PostgreSQL V1-through-V10 apply and complete post-write
  value/relationship validation: `TWIN_PARITY`; the first strict mapping test
  safely rolled back and exposed the explicit `remove_garlic -> remove_peanut`
  baseline replacement before the successful retry; a deliberate pre-commit
  category-value tamper was also rejected by full parity validation and the
  transaction returned to `CURRENT_SYNTHETIC_BASELINE`; a deliberate duplicate
  category injected after lock acquisition was rejected by exact in-lock count
  validation and likewise rolled back;
- manifest fingerprint and complete 39-item/380-option/11-parent graph:
  `PASS`;
- prohibited query/secret surface tests: `PASS`.
- fresh independent Agent 6 review after race, duplicate, runtime-binding and
  governance repairs: `ACCEPT`.

No reconstruction write, credential action, deployment, migration, restart,
physical printer/device action, Production read, or Production mutation is
part of this repository evidence. Runtime execution begins only after this
package enters `main`, fresh authority recovery, and exact package binding.
