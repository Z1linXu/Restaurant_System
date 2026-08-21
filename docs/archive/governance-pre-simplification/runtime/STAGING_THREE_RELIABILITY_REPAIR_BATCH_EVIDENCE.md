# Staging Three Reliability Repair Batch Evidence

> Status: `DEPLOYED_TO_STAGING_AUTOMATED_SMOKE_PASS_WAITING_FOR_OWNER_RETEST`
>
> Date: 2026-08-11, America/Toronto
>
> Scope: `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP` /
> `STAGING_THREE_RELIABILITY_REPAIR_BATCH`.

## Authority and boundaries

Owner authorized three bounded Staging/repository repair packages after the
exact-RC Production promotion. Production is not in scope for mutation:
no Production deploy, restart, Flyway, environment/configuration change,
printer contact, Pad mutation, business-data mutation, schema change, raw clone
or credential/secret action is authorized.

Final stop after merge, Staging deploy and runtime regression:
`OWNER_FIELD_TEST_THREE_RELIABILITY_REPAIRS_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST`.
This package did not start Production promotion, Chinatown, modularization,
physical printer binding or Pad pairing.

## Runtime ground truth used

- Fresh `origin/main`: `73345aeb564bd3a780e8592eb579a0b211856b60`.
- Production result from current Planbook/Handoff: exact promoted
  `RC-ST-DENIS-20260811-2661EB76` at application SHA
  `2661eb76c36dd9aa58db94ceacd278242ef4c9ab`, Flyway V10.
- Staging before this repository package: exact
  `2661eb76c36dd9aa58db94ceacd278242ef4c9ab`, Flyway V10, Printing
  `MOCK/true`, four endpoint-free logical printers and three assignments.
- PR #122 merged at
  `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`.
- Final deployed Staging SHA:
  `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`.
- Final Staging Flyway: V10, ten successful rows, failed rows `0`.
- Final Staging Printing: `printing_enabled=true`, `printing_mode=MOCK`,
  four enabled logical printers, three enabled assignments.

## Package 1: PAD_SLEEP_PRINT_BLOCKING_REPAIR

Root cause confirmation: the earlier repair kept the worker generation alive
for an in-flight print, but Android still requested the same long lease before
physical output had started. A Pad that went background/screen-off after
claiming but before `start-print` could therefore keep a safe-to-reclaim
`CLAIMED` job unavailable for too long. Active Pads could not take over until
the claim expired or the sleeping Pad resumed.

Implementation:

- Android `PAD_DIRECT_PRE_PRINT_CLAIM_LEASE_SECONDS = 30`.
- Android `PAD_DIRECT_PRINTING_LEASE_SECONDS = 300`.
- Existing backend semantics remain: expired `CLAIMED` can be reclaimed;
  expired `PRINTING` is not blindly reclaimed because physical output may have
  started and acknowledgement may be ambiguous.

Safety:

- No Store-isolation change.
- No printer-assignment change.
- No new duplicate-prone `PRINTING` timeout/reprint behavior.
- `AMBIGUOUS_PRINT_STATE` remains operator/field-test visible rather than
  automatically duplicated.
- Deterministic local tests cover lifecycle/worker policy and backend
  claim/start/fail/complete/reclaim rules; real screen-off behavior still
  requires Owner physical Pad retest.

## Package 2: PAD_MENU_REVISION_AND_CLICK_LOCK_REPAIR

Root cause confirmation: Pad/WebView menu storage already used revisioned
IndexedDB snapshots and an atomic head pointer, but long-lived visible sessions
had no continuous refresh trigger. A Pad could remain on revision N after Menu
Management created N+1 until a manual reload or another fetch path occurred.
Separately, `draftSubmissionLocked` could stop click handlers without a clear
disabled/loading reason.

Implementation:

- `useMenuCatalog` triggers refresh on document visibility, window focus,
  window online and a bounded 60-second periodic check while mounted and online.
- Refresh still downloads a complete catalog, validates the snapshot, writes the
  revisioned snapshot and only then switches the IndexedDB head.
- Failed refresh keeps the last complete validated snapshot; it does not clear
  cache or expose a mixed revision.
- Ordering page shows a visible lock message when a draft is submitting,
  queued, retryable failed, validation-conflicted or otherwise syncing.
- Menu cards and customization modal receive disabled state/reason and block
  add/decrement/submit affordances visibly.

Safety:

- No API shape change.
- No offline-cache removal.
- No half-new/half-old snapshot exposure.
- Lock release restores normal interaction through the existing draft state.

## Package 3: PRINTING_BOUNDED_SCHEDULING_LATENCY_REPAIR

Before:

- Order submit committed independently, then durable outbox rows became due.
- Scheduler polled every `app.printing.dispatch-outbox-poll-ms` default
  `1000ms`.
- One scheduler pass read up to 10 due rows by outbox id.
- Dispatch was effectively synchronous in one loop, so unrelated printer
  events could wait behind one slow printer even though same-printer ordering
  is the real ordering requirement.
- Retry backoff remained `2s`, `4s`, `8s`, `16s`, `32s`, `60s`.

After:

- Due rows are fetched read-only by id order.
- Each event is claimed with an atomic repository update that moves due
  `PENDING` or stale `PROCESSING` work to `PROCESSING` and sets a bounded
  120-second processing lease in `nextAttemptAt`.
- Dispatch is scheduled through the existing `printTaskExecutor`.
- Serialization key is `Store + assigned printer` when an enabled module
  assignment resolves to a printer id; disabled/missing assignments fall back
  to `Store + module`, matching dispatch eligibility.
- Same key remains FIFO/serial.
- Different Store/printer keys can run concurrently within the bounded
  executor.
- Success marks the outbox row `COMPLETED`; failure returns it to `PENDING`
  with bounded retry/backoff and safe error text.

Safety:

- No unbounded thread creation.
- No same-printer parallel dispatch.
- No schema/Flyway change.
- No order-submit rollback behavior change.
- A crashed or interrupted `PROCESSING` event becomes due after the processing
  lease rather than remaining stuck forever.
- Duplicate protection continues to rely on durable source keys and existing
  print-job idempotency.

Latency model:

| Component | Before | After |
|---|---:|---:|
| scheduler wait | up to configured poll interval, default ~1000ms | unchanged |
| due event claim | synchronous loop | atomic per-event claim |
| unrelated slow printer | can delay later rows in same scheduler loop | isolated by different Store+printer key |
| same printer | serial by implementation side effect | explicitly FIFO/serial |
| retry backoff | 2/4/8/16/32/60s | unchanged |
| processing crash recovery | could remain ambiguous by status timing | stale `PROCESSING` due after 120s lease |

## Verification

Repository verification completed in a clean temporary worktree:

- Backend focused regression:
  `mvn test -Dtest=OrderDispatchOutboxProcessorTest,PadPrintJobServiceImplTest,StoreDeviceServiceImplTest`
  -> `34 tests`, `0 failures`, `0 errors`.
- Android focused regression:
  `./gradlew testDebugUnitTest --tests com.restaurant.pad.PadDirectWorkerPolicyTest`
  -> `BUILD SUCCESSFUL`.
- Frontend focused regression:
  `npm test -- --run src/hooks/useMenuCatalog.test.ts src/hooks/useDraftOrder.menuSnapshot.test.ts src/offline/menuCache.test.ts`
  -> `3 files`, `13 tests`, all passed.

Broader build/regression completed:

- Backend full regression: `mvn test` -> `407 tests`, `0 failures`,
  `0 errors`, `3 skipped`.
- Frontend production build: `npm run build` -> PASS.
- Android debug unit regression: `./gradlew testDebugUnitTest` -> PASS.
- Android debug package build: `./gradlew assembleDebug` -> PASS.
- Diff hygiene: `git diff --check` -> PASS.
- Migration drift check: no `backend/src/main/resources/db/migration` diff.
- Prohibited-data scan: diff scan found only governance boundary words such as
  `credential` and `printer contact`; no secret, credential value, printer
  endpoint, token, customer/PII, payment, raw environment or Production data
  value is present.

Agent 6 final repository review returned `ACCEPT`. Agent 6 confirmed scope,
safety, test evidence, no schema/Flyway change, no Production mutation path, no
prohibited data, conservative `PRINTING` handling, same-printer FIFO, bounded
unrelated-printer concurrency, atomic menu snapshots and visible click-lock UX.

PR/merge, exact-SHA Staging deployment and post-deploy automated regression
completed.

## PR, deploy and post-deploy evidence

- PR: #122
  `https://github.com/Z1linXu/Restaurant_System/pull/122`.
- Merge SHA:
  `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`.
- Dedicated Staging repository import:
  `STAGING_REPO_IMPORT|PASS|3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`.
- Release/env rotation:
  `OPS001_RELEASE_ENV|PASS`; recovery record SHA-256
  `b4b4b09ca1cb47355faa3d43551205ccf150e109c71e61f7a9f96c55afa82d65`.
- Release/env approval SHA-256:
  `28a7be4b8b03f1175eea34920eec5ed06269b42fd4de3c946437e7d0d99567cb`.
- Preflight evidence:
  `/srv/restaurant-pos/staging/evidence/three-reliability-preflight-3ec4d88a47f68e05b92d9246bfd63af2d1f297f9.txt`.
- Preflight SHA-256:
  `2594029f590a22ea215fa92ba27e9529d1d6818a8fff7d342f1c5d5d7c04ebac`.
- Staging deploy:
  `STAGING_DEPLOY|PASS|approved_sha=3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`.
- Health: initial post-start health returned transient HTTP `502`; bounded
  retry reached HTTP `200` on attempt 5, then official
  `staging-health-check.sh` passed.
- Final HTTP checks:
  Staging frontend `200`, Staging system health `200`, Staging SockJS
  `/ws/info` `200`, Production system health `200`, Production menu health
  `200`.

Automated Staging smoke:

- Evidence:
  `/srv/restaurant-pos/staging/evidence/three-reliability-mock-smoke-3ec4d88a47f68e05b92d9246bfd63af2d1f297f9.txt`.
- Evidence SHA-256:
  `51f1f18800dde6429d0d971c22062effe3eb4ed2c04b87938f621aa43e2869eb`.
- Login: PASS using existing Staging credential file without printing secrets.
- Catalog: PASS at menu revision `16`; required option snapshot included.
- Order submit: PASS via idempotent order API.
- MOCK pipeline: PASS; outbox
  `FRONTDESK_RECEIPT:COMPLETED,GRAB:COMPLETED`; PrintJobs
  `FRONTDESK_RECEIPT:PRINTED,GRAB:PRINTED`; counts
  `jobs=2|printed=2|failed=0`.
- The selected synthetic item routed to GRAB and Frontdesk receipt in current
  Staging configuration. HOT/cross-printer scheduling is covered by repository
  regression because this smoke item did not create a HOT_KITCHEN route.

## Prohibited data and runtime confirmation

- Production was not written, restarted, deployed, migrated or reconfigured by
  this package. Production health remained HTTP `200` before/after Staging
  deploy checks.
- Staging runtime was changed only through the reviewed exact-SHA
  release/env/deploy helpers for
  `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`.
- No credentials, password hashes, tokens, cookies, printer endpoints, device
  secrets, raw environment values, customer/PII, historical orders, order item
  history or payments were read or copied.
- No migration or Flyway history file was added or modified.

## Final classification

- `PAD_SLEEP_PRINT_BLOCKING_REPAIR`: `DEPLOYED_REPOSITORY_REGRESSION_PASS`;
  `OWNER_PHYSICAL_PAD_RETEST_REQUIRED` for true screen-off hardware behavior.
- `PAD_MENU_REVISION_AND_CLICK_LOCK_REPAIR`:
  `DEPLOYED_REPOSITORY_REGRESSION_PASS`.
- `PRINTING_BOUNDED_SCHEDULING_LATENCY_REPAIR`:
  `DEPLOYED_REPOSITORY_AND_STAGING_MOCK_SMOKE_PASS`.
- `STAGING`: exact
  `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9`, Flyway V10, health PASS.
- `PRODUCTION`: continuity PASS; unchanged by this package.
- Unique stop:
  `OWNER_FIELD_TEST_THREE_RELIABILITY_REPAIRS_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST`.
