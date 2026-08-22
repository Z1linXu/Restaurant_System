# Phase B Part 1 Size Option Repair Staging Evidence

## Scope

This evidence records the bounded Phase B Part 1 repair for Store-local Size
option identity and Owner option-edit round trips. It does not authorize Phase
B Part 2, Store activation, real Printer/Pad binding, Production deployment or
Production mutation.

## Reviewed release

- implementation PR: `#177`
- implementation commit: `8acb9bed5ce2295ede94fb6293d91b26a26c7d70`
- merged/deployed SHA: `9bebac8a427690ca2a842ab8fad9dc09e2c75208`
- Staging environment: `restaurant-pos-staging`
- Flyway: `count=21`, `max_version=21`
- migrations/backfill: none
- Agent 6: `AGENT_6_ACCEPT`, no P0/P1/P2 findings

## Repository validation

- focused backend tests: 19 passed
- complete backend suite: 581 tests, 0 failures, 0 errors, 3 skipped
- frontend suite: 23 files / 114 tests passed
- frontend production build: passed
- governance validator and `git diff --check`: passed

The repository repair establishes one Store-local canonical row for each
standard Size semantic, using `size_small`, `size_regular` and `size_large`.
Duplicate/inactive legacy Master candidates retain independent inactive
noncanonical rows with stable derived codes and Master mappings. Generic
option validation no longer treats type-only legacy Size rows as canonical,
while a genuine canonical `SIZE` row with a missing code remains invalid.

## Exact-SHA Staging release evidence

All listed files are private evidence under
`/srv/restaurant-pos/staging/evidence`.

| Check | Evidence file | SHA-256 / result |
| --- | --- | --- |
| candidate import | `phase-b-size-option-candidate-import-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `6722a044ec59b6cd46e1a78ba9e14ab1c09026ada59c6ef32bb9831f1b427c50` |
| release environment | `phase-b-size-option-release-env-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `563c90b52e2989134d84d7b586ba2d99310edfd27271b1542c2fb182738f4447`; resulting env digest `720cf292b1d2d505161287878f6433480872d7243ea60767b72b041813643ad9` |
| preflight | `phase-b-size-option-preflight-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `c5fe8999eace0f75310348a0b566f284a7148df72dbfa20b18d2acffcac2f2fb`; PASS |
| deploy | `phase-b-size-option-deploy-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `41ca9d297d0d5b6cd40482ba2cc594584e835842349236b2068c3be39ecd79fc`; PASS |
| health | `phase-b-size-option-health-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14`; PASS |
| readiness | `phase-b-size-option-readiness-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `d5a08a3b0f3425f8e766ec380f732b3072df3caf79f8ba5fa9ad77c0321bcbe8`; PASS |
| runtime | `phase-b-size-option-runtime-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `4d5e5a89b5683db89fb9fc49fea338d9914de9de7a5a82f327606371f8daca39`; exact SHA, Flyway V21, restart counts 0, PASS |
| affected slice | `phase-b-size-option-affected-slice-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `001a3d2af460d347314fdaf4ad3a8aec356f18f00d7a2b4e1719df9a85e0eab0`; PASS |
| post-acceptance health | `phase-b-size-option-post-acceptance-health-9bebac8a427690ca2a842ab8fad9dc09e2c75208.txt` | `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14`; PASS |

The detached release HEAD was re-read after acceptance and remained exactly
`9bebac8a427690ca2a842ab8fad9dc09e2c75208`.

## Affected Phase B Part 1 acceptance slice

The prior full-package automated acceptance remains recorded in
[`PHASE_B_PART1_STAGING_AUTOMATED_ACCEPTANCE_EVIDENCE.md`](PHASE_B_PART1_STAGING_AUTOMATED_ACCEPTANCE_EVIDENCE.md)
for exact SHA `96c81cf4fab8e771187ceeddeed28e5fc3e87f4a`. PR #177 is a bounded
Size/option identity repair: it does not change authentication, Organization
scope, Store lifecycle, module materialization, category/item isolation,
pricing, Combo, Printing Rule or fixture-hygiene behavior proven there. The
complete 581-test backend regression and 114-test frontend regression passed
for the repair, and the exact-SHA Staging slice below re-proved every changed
runtime path plus source/Master/Profile immutability. Those three layers—the
prior full acceptance baseline, full repository regression on the bounded
delta, and exact-SHA affected-slice acceptance—are the traceable basis for
retaining `PHASE_B_PART1_AUTOMATED_ACCEPTANCE_PASS` at deployed SHA
`9bebac8a427690ca2a842ab8fad9dc09e2c75208`.

The release provisioned synthetic inactive validation Store 13,
`PHASE_B_VALIDATION_STORE_SIZEOPT_9BEBAC8_R1`, from the approved Profile and
Master. The first generic acceptance runner evidence ended after Store
provisioning and was not counted as a full acceptance PASS. The bounded affected
slice then used the actual Owner APIs and verified:

1. a newly materialized noodle item returned active canonical
   `size_regular`/`size_large` identities;
2. duplicate legacy Master Size candidates became stable inactive
   noncanonical Store-local rows;
3. opening and saving without changes preserved canonical IDs/codes;
4. enabling `size_small` preserved the existing regular/large identities;
5. ADD_ON create/update preserved its code and persisted after reload;
6. REMOVE update preserved its code and persisted;
7. combined SIZE + ADD_ON state remained correct after reload;
8. an existing legacy Store with type-only null-code Size rows could create and
   reload an ADD_ON without the old Size validation blocker;
9. an illegal canonical SIZE create request was rejected with HTTP 400, while
   the focused service test proves the missing-code validation remains strict;
10. source Store 1 and published Master/Profile signatures stayed unchanged.

## Gate and environment boundary

The repository and Staging automated repair acceptance are PASS. The current
gate remains `WAITING_FOR_OWNER_MANUAL_ACCEPTANCE`; Phase B Part 2 remains
unauthorized. Staging validation data changed only within synthetic validation
Stores. Production SHA/Flyway/data/runtime were not mutated.
