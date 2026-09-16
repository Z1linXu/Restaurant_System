# Add-on identity and print alias ownership

2026-09-16 implementation evidence. Runtime acceptance is recorded separately
below when executed; this file does not authorize Production.

Base: `52a7fe6844850d00c1d7f346d5a35fb07cba03f7`.
PRIMARY_REPAIR_GOAL: Menu owns Add-on identity; Printing consumes optional aliases;
apply explicit Owner price decisions only on Staging, preserving history.
EXPECTED_REPAIR_CLASS: HIGH (money propagation, printing revision/history).

## Repository validation

- Backend: 175 passing tests in 16 classes, including Menu catalog, controller,
  pricing rollback, Store/Organization JDBC isolation, printing semantic/GRAB/HOT
  rendering, Combo defaults, provisioning and order regression.
- Frontend: 43 passing tests across five matching files; alias-only controls,
  null reset, unchanged legacy values, catalog editing and cache/draft regression.
- TypeScript/Vite production build: PASS.
- Existing acceptance-helper tests: 21 PASS.
- Governance validation and whitespace: PASS.
- Schema unchanged; no Flyway migration introduced.

Agent 6: independent review `01a0aafa-cf2a-7950-8ada-d7dc722ab6f3` found P1 stale
JPA active pointer under interleaved publication and P2 legacy draft-only code
compatibility. Both were repaired before merge. Re-review ACCEPT; reviewer
verified 13 lifecycle tests and whitespace, no remaining P0/P1/P2. Added real
two-transaction interleaving and actual JDBC legacy-vocabulary tests.

Product changes use existing Store catalog, option materialization, printing
revision repositories and menu revision mechanism. The only tooling change is
the bounded alias/confirmed-price slice in the existing Staging acceptance
script; no deployment, credential or migration framework was introduced.

## Pre-deployment observation

Staging observed at `8fc7ba8623c3180b35c6e3605a46d0a7d6744ce7`; database healthy.
Production images/start times match the existing release; no mutations.
Disk 67%, about 20 GB available. Build Cache 19.19 GB / 15.7 GB reclaimable.
Reviewed cache dry-run returned NO_GO because records are not clearly eligible.
No cleanup performed; all current/rollback artifacts remain protected.

Owner's Chrome session is available. Combo UI acceptance was already confirmed
by Owner; new alias UI checks still require the newly deployed application.

## Runtime result

Not yet executed. No Staging acceptance PASS or Production authorization claimed.
