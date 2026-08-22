# Phase B Part 2 Product-Flow Staging Acceptance Evidence

This additive evidence record supersedes the Owner-facing workflow described
in `PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE_EVIDENCE.md`. The earlier record
remains historical evidence for the internal Part 2 provisioning/readiness/
activation implementation. This record proves the final product contract:

```text
Owner -> Add Store -> required details -> Create -> LIVE
```

It records isolated Staging facts only. It does not authorize or record a real
Store activation, real credential, physical Printer/Pad, Production mutation,
or Phase C.

## Result

```text
PHASE_B_PART2_REPOSITORY_IMPLEMENTATION = COMPLETE
PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE = PASS
PHASE_B_PART2_OWNER_MANUAL_ACCEPTANCE = PENDING
STOP = PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE_PASS_WAITING_FOR_OWNER_MANUAL_ACCEPTANCE
```

- Exact deployed application SHA:
  `d0b1bde3a0e3382032a214a315703c5c18d4d058`
- Runtime implementation PR: #195
- Acceptance-tooling repair PR: #196
- Staging Flyway: `V26` (no migration was added by the product-flow correction)
- Staging environment digest:
  `c62d94af699a9902fc553682962af0406962a3c3c618a49298a73615592e1562`
- Fresh synthetic validation Store: ID `21`, code/name
  `PHASE_B_VALIDATION_STORE_PRODUCT_FLOW_D0B1BDE_R03`
- Owner-created Staging Chinatown regression Store: ID `18`
- Organization: isolated Staging Organization `1`

## Exact-SHA runtime evidence

The following mode-0600 sanitized evidence files are under
`/srv/restaurant-pos/staging/evidence/`.

| Evidence | Result | SHA-256 |
| --- | --- | --- |
| Preflight before build, `phase-b-product-flow-preflight-d0b1...-r02.txt` | PASS | `b95361634945ebc9226b92c0ecf73b29a32677f9ecd95c22ccf599222c7aa674` |
| Preflight after deploy, `phase-b-product-flow-preflight-d0b1...-post-deploy-r01.txt` | PASS | `f5cc86a9db4dffef3be21bf7767e6de43678076263686842db51ddb16b7cd793` |
| Host/runtime readiness, `phase-b-product-flow-readiness-d0b1...-r01.txt` | PASS | `3ff199efbbd5b477c41c397c3c1d5e2136610ad566d6f596109a8a76ab1765ce` |
| Runtime/Flyway collection, `phase-b-product-flow-runtime-collect-d0b1...-r01.txt` | PASS; Flyway count/max V26 | `b3562fe0ee4c51f5b8895ed3516fad0f7b0f331e8625c76b53c504afde371a64` |
| Same-image restart, `phase-b-product-flow-restart-d0b1...-r01.txt` | PASS; container/image/Flyway/project identity retained | `cdb4f0450bf381c4bd638663f33711f06885ba5557c61121fcc10ca40eb35c3b` |
| One-action product acceptance, `phase-b-product-flow-acceptance-d0b1...-r03.txt` | PASS; target Store 21 | `e80fa226f7eeffb44deeb7560536153f9015da39cc37b79fe3be45dcf03274b3` |
| Failed-fixture reconciliation replay, `phase-b-product-flow-fixture-reconciliation-d0b1...-r01.txt` | PASS; Stores 19/20 removed, evidence preserved | `eeef1aa4aa37124a930e09a98dd0e93fb97978bc02f7ce502251221e3dc3d41e` |

The readiness evidence retained the Production project fingerprint
`df8fe7cc36fbfb0909fcfe6528f855bab69190271f586adc31d4794a1d8bbc91`.
No Production command, deployment, migration, restart, credential or database
mutation was executed.

## Product-flow acceptance

The reviewed helper
[`staging-phase-b-part1-acceptance.sh`](../../deployment/cloud/staging-phase-b-part1-acceptance.sh)
used the same one-action Store creation API as the Owner UI. A token acquired
before Store creation immediately accessed the new Store after the create
response, so no refresh or re-login was required. The acceptance passed:

- Organization/Owner authorization and target-code availability;
- transactional Store creation, duplicate-code failure rollback and stable
  idempotent replay;
- Profile/Master materialization with Store-local IDs and parent remapping;
- canonical `status=active` plus `lifecycle_status=ACTIVE`, exposed as
  `operational_state=LIVE` and `is_live=true` by Store Context, workspace and
  Owner overview APIs;
- two default dining tables, Store-local stations and Owner Store membership;
- endpoint-free logical printing roles, Printing Management access and zero
  normal-create device enrollment;
- no normal-create synthetic staff credentials or Store-local users;
- immediate Frontdesk table access and Printing Management access while
  physical Printer bindings remain absent;
- Store-local item/category deactivation, Store-only item creation, pricing,
  combo and Printing Display Rule independence;
- source Store, Chain Master Menu and Store Profile immutability;
- secret-free output and logout.

The first two product-flow attempts created Stores 19 and 20 but stopped on a
Staging-host jq-compatibility false negative. Direct API checks proved their
runtime tables and Printing Management were correct. PR #196 added only the
two exact fail-closed list-cardinality filters used by the reviewed acceptance
helper. Agent 6 returned `ACCEPT`. After exact-SHA redeploy, Store 21 passed the
full matrix.

## Reported Chinatown blocker regression

The current Store 18 database and API chain was rechecked after deploy:

- Store row: `status=active`, `lifecycle_status=ACTIVE`;
- readiness: `READY` with a current fingerprint;
- activation ledger: `COMPLETED`, target `LIVE`;
- Store Context: HTTP 200, `operational_state=LIVE`, `is_live=true`;
- Frontdesk tables: HTTP 200, three rows;
- Printing module: enabled/configured;
- Printing Management: HTTP 200;
- logical printer roles: two, physical bindings: zero;
- device management: HTTP 200; the existing Store 18 device is synthetic
  Staging data from the earlier Part 2 manual workflow.

This proves the lifecycle label and Printing Management blockers share the
same corrected canonical Store/module contracts without requiring a physical
printer. The UI bundle for the exact SHA was rebuilt and served successfully;
Owner visual/manual confirmation remains the active Owner gate.

## Fixture reconciliation and retained Stores

The reviewed Staging fixture cleanup path first passed dependency preflight,
then removed failed automated Stores 19 and 20. It deleted only Store-local
fixture rows and preserved/detached provisioning, readiness and activation
evidence. Its idempotent replay passed.

The final Staging Store inventory is:

- Store 1 `STG005_SRC_20260809_R01` — protected source/reference Store;
- Store 18 `CHINATOWN` — Owner-created Staging manual-acceptance Store;
- Store 21 `PHASE_B_VALIDATION_STORE_PRODUCT_FLOW_D0B1BDE_R03` — final
  automated product-flow acceptance Store.

Post-reconciliation health passed. Chain Master Menu retained one version,
Store Profile retained two `READY` versions, Store 1 remained `active/ACTIVE`,
and Store 21 retained 426 Store-local Master mappings.

## Owner manual acceptance entry

Create the loopback tunnel:

```bash
ssh -L 127.0.0.1:28080:127.0.0.1:18080 restaurant-prod
```

Open `http://127.0.0.1:28080/` and sign in as the existing synthetic Staging
Owner `owner`. The password remains only in the private Staging credential
artifact and is intentionally absent from repository/evidence.

Manual acceptance should now verify:

1. Owner Home shows Chinatown and Store 21 as `LIVE`.
2. Opening Chinatown or Store 21 Admin Dashboard shows `Live`, never a
   simultaneous `Not Live` label.
3. Frontdesk opens and shows the Store-local tables/menu.
4. Printing Management is visible and opens even with logical roles unbound.
5. Creating another explicitly synthetic Staging test Store requires only
   details plus `Create`; success returns a complete `LIVE` Store without
   provisioning/readiness/activation buttons.
6. No real Printer/Pad/device, real staff credential or Production action is
   performed.

## Remaining gate and boundary

- `PHASE_B_PART2_OWNER_MANUAL_ACCEPTANCE = PENDING`.
- Real Chinatown/Sainte-Catherine activation, real staff credentials, physical
  Printer/Pad binding, Production Flyway/deploy/restart/credential mutation and
  Phase C remain unauthorized.
- Physical hardware behavior remains outside this synthetic Staging proof.
