# Phase B Part 2 Staging Automated Acceptance Evidence

This is the current non-historical evidence record for the final Phase B Part
2 automated acceptance. It records observed Staging facts only; it does not
authorize a real Store, physical hardware, Production, or Phase C.

## Result

```text
PHASE_B_PART2_REPOSITORY_IMPLEMENTATION = COMPLETE
PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE = PASS
PHASE_B_PART2_OWNER_MANUAL_ACCEPTANCE = PENDING
STOP = PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE_PASS_WAITING_FOR_OWNER_MANUAL_ACCEPTANCE
```

- Application deployed SHA: `72fecac3a17e0ac40d6207f4c501eb0308210123`
- Staging Flyway: `V25`
- Staging environment digest: `619e0bb7747ef411ef860ad22886a7b63006c18a3c5275cacdfba336e1dd879e`
- Synthetic validation Store: ID `17`, code/name
  `PHASE_B_VALIDATION_STORE_FINAL_20260822_R04`
- Organization: synthetic Staging Organization `1`
- Final synthetic Store remains `ACTIVE` with `MOCK` printing for Owner manual
  review. It is not a real restaurant Store.

## Exact-SHA operational evidence

All paths below are on the isolated Staging host under
`/srv/restaurant-pos/staging/evidence/`. The listed digests are SHA-256 of the
sanitized mode-0600 evidence files.

| Evidence | Result | SHA-256 |
| --- | --- | --- |
| Candidate import | exact object/CAS import, no Production mutation | `9369793a16e3488ed15dbc3a865a2a47102024b899e814692b355637b30cfb75` |
| Release/env binding | exact release and private env identity | `7c9267dc69e542290a3da27c9a75e1ba310a673ef4cf2033f434187da0b726c5` |
| Preflight before build | PASS | `bb774b1828ebcf2e1fc75a450c3337df265ecfc783b4ab08b94ae1b45214b342` |
| Preflight after deploy | PASS | `e9251e60abf33b87cddf5fc9431613eef9909b6d36bfb97b0d7257f5563d5cbd` |
| SHA-specific deployment | backend/frontend rebuilt for SHA; DB kept scoped | `215f5f35da944c27210b8de832407d9771c0e1a49a12741df35a497b880ab60f` |
| Loopback health | PASS | `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14` |
| Host/runtime readiness | PASS | `a74dc22bca2c0d5396bb82aaec7d4b6e7ed4e103a482b0ee320b02a623cf1a32` |
| Same-image restart | PASS; container/image/Flyway/project identity retained | `be594d6d49ba617fc7a29b8a62ffe2d062386f5f404ee3739366cb82e01e54ca` |
| Post-restart runtime validation | PASS | `a61ec5fc61a293bef9810efa12e56359904a4de84336ac3c1678cb8023f6bd3e` |

The final Staging release checkout resolved to the approved application SHA and
the running images were SHA-tagged backend/frontend images. No Production
Compose project, Production database, or Production environment was used for
the acceptance.

## Phase B acceptance evidence

Part 1 was re-run for the fresh synthetic Store before Part 2:

- Part 1 bootstrap evidence: `9f37188dd240ca1ac9670ffc61eef550ea14cd8e76822e274b1e6cc1f389d0a0`
- It passed Store creation, replay, lifecycle, local materialization,
  isolation, Master/Profile immutability and logout checks.

The final Part 2 acceptance evidence is:

- Evidence: `phase-b-part2-acceptance-72fecac3a17e0ac40d6207f4c501eb0308210123-store17-r01.txt`
- SHA-256: `7b27c6d69857bf01abcede2253b59a66af45d2e557cef9f0f3fa40c06e839702`

The reviewed matrix passed:

- Flyway V25, initial `NOT_READY`, missing-prerequisite behavior and
  failed-provisioning rollback with no partial rows;
- tables/stations, Store-local staff/access, organization and Store isolation,
  idempotent provisioning replay and changed-request conflict;
- logical printer topology, Store-scoped assignment, `MOCK`/disabled safety,
  no endpoint or physical printer binding, and excluded-module safety;
- synthetic device registration/authentication, heartbeat, readiness proof,
  TTL expiry/freshness, missing prerequisite `NOT_READY`, and restoration to
  `READY`;
- owner/organization authorization, fingerprint revalidation, activation
  idempotency, concurrent activation ledger, changed-request conflict and
  transactional `READY -> ACTIVE/LIVE` activation;
- Master/Profile immutability, Menu Store-local isolation, drift/restart
  revalidation, sanitized evidence, and Production/real-hardware untouched.

The final automated acceptance used the reviewed helper
[`staging-phase-b-part2-acceptance.sh`](../../deployment/cloud/staging-phase-b-part2-acceptance.sh)
with synthetic data and private Staging Owner credentials delivered through a
secret file descriptor. No password, token, staff credential, device token,
printer endpoint, or raw request body is recorded here.

## Owner manual acceptance entry

Use an SSH tunnel to the loopback-only Staging UI:

```bash
ssh -L 127.0.0.1:28080:127.0.0.1:18080 restaurant-prod
```

Open `http://127.0.0.1:28080/`, sign in with the existing synthetic Staging
Owner login `owner`, and use the Owner dashboard. The password remains in the
private Staging credential artifact and is intentionally not recorded in this
document.

Manual checks should cover Store 17 and, when the full lifecycle needs to be
replayed, a new synthetic Staging fixture:

1. Review Store 17's current `LIVE` state, readiness counts/checks,
   tables/stations, Store-local staff/access, device freshness and activation
   result.
2. Review logical printing readiness; it must remain `MOCK`/unbound with no
   real endpoint or hardware action.
3. For a fresh lifecycle test, use the Owner UI's Create New Store action with
   a `PHASE_B_VALIDATION_STORE_...` synthetic name. Before pressing
   `Provision synthetic defaults`, refresh readiness and confirm the new Store
   is `NOT LIVE`/`NOT_READY` with missing prerequisite checks.
4. Restore the prerequisites by pressing `Provision synthetic defaults` on that
   new synthetic fixture and confirm the panel becomes `READY`; the one-time
   synthetic staff/device credentials may be viewed only in the UI and must
   not be copied into evidence.
5. Click `Activate Owner-approved Store` only for a synthetic fixture and
   confirm the result is `ACTIVE`/`LIVE` with `MOCK` printing. Do not use Store
   17 for a reset or destructive prerequisite experiment.

Do not use this entry to create or activate Chinatown or Sainte-Catherine, add
real staff credentials, bind a real Printer or Pad, or touch Production.

## Package and execution record

The implementation was delivered as four logical packages:

1. P2-1 Contract + Readiness Planner;
2. P2-2 Safe Provisioning Writers;
3. P2-3 Device Readiness + Staging Acceptance;
4. P2-4 Activation Coordinator.

The repository merge sequence was PRs #183–#190: one initial Part 2 merge plus
seven focused compatibility, response-state, acceptance-tooling, heartbeat,
TTL and device-proof repairs. Agent 6 reviewed the material implementation and
each material repair. The final runtime acceptance used the merged application
SHA above.

Observed final execution windows were kept separate from implementation
elapsed time: exact-SHA deploy/readiness/restart/validation completed before
the synthetic acceptance; the fresh Part 1 Store bootstrap completed in one
runtime run; and the fresh Part 2 matrix completed in one runtime run. A single
aggregate implementation wall-clock duration is not reconstructed from the
individual Agile Loop sessions.

## Remaining gates and risks

- Owner manual acceptance of the Staging UI and synthetic Store is pending.
- Real Store activation, real staff credentials, real Printer binding, real
  Pad binding, Production credential mutation, Production Flyway/deploy/restart
  and Phase C remain unauthorized.
- Physical printer/device behavior and network-specific hardware failure modes
  still require a separately authorized real-environment package.
- Store 17 is a synthetic manual-acceptance fixture; its successful activation
  is not evidence that any real Store is activated.
