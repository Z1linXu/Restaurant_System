# Owner Field-Test Printing Fixes Evidence

> Status: `AGENT6_ACCEPT_WAITING_FOR_PR_MERGE_STAGING_DEPLOY`
>
> Package: `OWNER_FIELD_TEST_AND_BUG_FIX_LOOP`
>
> Date: 2026-08-11, America/Toronto

## Authority and scope

The Owner approved bounded Staging/repository repair for field-test printing
bugs after the Operational Twin reached READY. Production remains unchanged and
may receive only lightweight continuity checks. This package does not authorize
Production mutation, real printer binding, public printer access, Pad pairing,
Chinatown, modularization, REL-001 or Production promotion.

## Issue 1 - GRAB remove bok choy abbreviation

Root cause: `OrderServiceImpl.mapRemoveToken()` mapped canonical
`remove_bok_choy` / `bok_choy` to `走青` while addon bok choy already kept the
full `加上海青` label. GRAB printed the kitchen snapshot it received.

Fix: map removed bok choy to `走上海青`. Existing GRAB green simplification
remains limited to exact onion/cilantro tokens.

Regression: `removeBokChoyKeepsFullKitchenInstructionName` and
`keepsRemoveBokChoyFullName`.

## Issue 2 - Pad screen-off / inactive worker blocking

Root cause classification: Android lifecycle behavior was the primary defect,
with backend lease semantics as supporting safety context. The Android worker
stopped on `onPause`/`onStop` by invalidating the current worker generation.
If a job was already in flight, a later callback could be ignored on resume
boundaries, leaving the operator-visible state dependent on returning Pads to
the app. Backend correctly allowed expired `CLAIMED` jobs to be reclaimed, but
intentionally did not let another Pad blindly reclaim expired `PRINTING` jobs
because that can duplicate output after TCP write ambiguity.

Fix: lifecycle stop is deferred while an automatic or manual PAD_DIRECT job is
in flight. The active job keeps its generation long enough to complete,
fail/report, or enter the existing high-risk operator review path. A queue poll
alone is not treated as an in-flight print job; lifecycle stop still invalidates
poll-only workers. The automatic worker also rechecks foreground, running state
and generation after selecting a pending job and before claim/execute, so a
backgrounded stale poll cannot claim or print a newly selected job.

Regression: Android `PadDirectWorkerPolicyTest` proves lifecycle pause defers
stop for in-flight jobs, poll-only lifecycle stop does not defer, and a stopped
or stale generation cannot start a polled job. Backend
`PadPrintJobServiceImplTest` proves an expired `CLAIMED` job can be reclaimed
by another active Pad, while the existing `expiredPrintingIsNotReclaimable`
regression preserves the no-duplicate `PRINTING` boundary.

Agent 6 initial review returned `BLOCK` because the first lifecycle fix
conflated poll-only worker activity with a concrete in-flight print job. The
bounded follow-up repair above addresses that finding without changing backend
lease semantics or enabling blind `PRINTING` reclaim.

Agent 6 follow-up review returned a second `BLOCK` for a residual race between
the pre-claim lifecycle check and active-job context creation. The final repair
uses one shared lifecycle lock for both normal lifecycle stop/defer decisions
and the automatic worker's final polled-job begin transition. If lifecycle stop
wins the lock, the generation is invalidated and the worker cannot claim. If
job begin wins the lock, the active context is established before network
claim, and lifecycle stop defers. Network claim remains outside the lock.
`lifecycleStopAndPolledJobBeginAreAtomicAlternatives` covers the two safe
atomic outcomes.

Agent 6 final-final review: `ACCEPT`.

## Issue 3 - Chicken cold noodle GRAB noodle-type rule

Root cause: `cold_noodle_shredded_chicken` incorrectly treated `韭叶` as the
default hidden noodle type, so `细` printed as `鸡凉细` and `韭叶` printed as
plain `鸡凉`.

Fix: chicken cold noodle treats `细` / `细面` as the default hidden type, and
`韭叶` maps to the existing `韭` suffix.

Regression: `coldChickenNoodleHidesThinAndShowsLeekLeafInKitchenInstruction`.

## Issue 4 - Frontdesk receipt no aggregation

Root cause: Frontdesk did not merge separate `OrderItem` rows, but it did print
one `OrderItem.quantity=2` as a single quantity line such as `2 x` or
`2* combo`. That is a display-level aggregation even though the order data
model remains valid.

Fix: `FrontdeskReceiptRenderer` expands each receipt item by quantity in the
renderer only. Order data, totals, pricing, GRAB aggregation and Kitchen
aggregation remain unchanged.

Regression: `frontdeskReceiptExpandsIdenticalNoodleQuantityIntoSeparateLines`
and `frontdeskReceiptExpandsIdenticalComboQuantityIntoSeparateLines`.

## Issue 5 - GRAB fried quantity symbol

Root cause: `GrabReceiptRenderer.resolveFriedPrimaryKitchenLine()` used ASCII
`*` for grouped fried quantities.

Fix: GRAB fried item display now uses U+00D7 multiplication sign: `×`.

Regression: `grabFriedQuantityUsesMultiplicationSign`; existing grouped fried
tests were updated from `*` to `×`.

## Issue 6 - Queue latency audit only

No behavior changed for scheduling/queue architecture.

Actual path:

`order submit -> enqueue order_dispatch_outbox -> scheduler poll -> dispatchPersistedEvent -> PrintJob -> renderer -> assignment -> mode-specific dispatch -> PRINTED/PENDING/FAILED`

Design intent:

- Order submission commits business data and returns independently of printing.
- Automatic submit creates GRAB and FRONTDESK_RECEIPT outbox events, plus
  HOT_KITCHEN only when renderable hot-kitchen content exists.
- Order updates follow the same three-module maximum for update tickets.
- Outbox is durable and idempotent by `source_key`.
- The outbox processor polls every `app.printing.dispatch-outbox-poll-ms`,
  default `1000ms`, after an initial default `2000ms`.
- One scheduler invocation takes up to 10 due events ordered by outbox `id`
  under pessimistic write locking.
- REAL and MOCK dispatch render and complete each event synchronously inside
  that processor loop.
- PAD_DIRECT renders and persists a `PENDING` job, then waits for an Android
  Pad to poll, claim, start-print, fetch payload, print locally, and ack/fail.
- Multiple jobs to different printers are not explicitly parallelized in the
  automatic outbox path despite the presence of `printTaskExecutor`.
- PAD_DIRECT prevents duplicate printing through atomic claim and lease. A
  blocked `PRINTING` job is not automatically stolen because local output may
  already have begun.

Delay sources:

- up to the scheduler poll interval before an event is observed;
- sequential per-event processing in the single scheduler loop;
- render/database work per event;
- REAL socket connect/write/flush timeout when physical transport is used;
- PAD_DIRECT worker poll cadence and quick-kick timing;
- PAD_DIRECT claim/start/payload/complete round trips;
- retry backoff for failed outbox processing: `2s`, `4s`, `8s`, `16s`, `32s`,
  then capped at `60s`;
- PAD_DIRECT claim lease defaults: `90s` claim, `300s` printing.

Classification:

- `DESIGN_INTENT`: durable outbox, idempotent source keys, claim/lease
  anti-duplicate protection, order-submit independence, and no blind
  `PRINTING` reclaim.
- `ACCIDENTAL_LATENCY`: automatic dispatch path is effectively single-loop
  synchronous and does not use the configured `printTaskExecutor`.
- `FOLLOW_UP_REPAIR_CANDIDATE`: yes, if the Owner later approves a separate
  printing scheduling/concurrency loop.

## Verification

- `mvn test -Dtest=GrabReceiptRendererTest,OrderServiceImplTest,PadPrintJobServiceImplTest`:
  `73 tests, 0 failures, 0 errors, 0 skipped`.
- `./gradlew testDebugUnitTest --tests com.restaurant.pad.PadDirectWorkerPolicyTest`:
  `BUILD SUCCESSFUL`.
- `mvn test`: `402 tests, 0 failures, 0 errors, 3 skipped`.
- `./gradlew testDebugUnitTest`: `BUILD SUCCESSFUL`.
- Deployment tooling regressions:
  `test_staging_server_preflight.sh`,
  `test_staging_synthetic_acceptance.sh`, and
  `test_staging_synthetic_acceptance_runtime_guards.sh` all `PASS`.
- `git diff --check`: `PASS`.
- Changed Markdown local links: `PASS`.
- Bounded added-line secret scan: `PASS`; only dummy test attempt-token names
  and safety-boundary prose matched, with no runtime secret value.

## Pending

PR auto-merge, exact-SHA Staging deploy/rebind, automated MOCK smoke and
Production continuity remain pending for this repair package.
