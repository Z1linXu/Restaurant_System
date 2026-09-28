# Printing Reliability — implementation and validation evidence

This file records evidence, not release authority. Current Gate is owned by
`CURRENT_STATE.yml`. Owner authorized implementation, independent review, merge,
exact-SHA Staging/non-paper acceptance and bundled APK build only. Production,
real Pad installation and physical printing remain outside this execution.

## Scope and repository proof

- Baseline: `3a5a92dbd7fbed1cf46c5caf2ea4e4604c45e519`; isolated worktree
  `codex/printing-reliability`. Unknown staged work in the Owner checkout was
  not touched.
- PRIMARY_REPAIR_GOAL: correct onion semantics and durable, duplicate-safe
  manual/automatic Pad printing across retry, affinity and lifecycle failures.
- EXPECTED_REPAIR_CLASS: HIGH (device proof, transaction/physical-side-effect
  boundaries, concurrency, Android native transport and additive schema).
- V30 adds nullable origin/preference/reprint-parent/hash/printing-start fields
  and active-job index. Existing order/job text is not rewritten.
- Product simplicity: no new configuration wizard, worker platform or printer
  capability matrix. One lightweight duplicate-print confirmation, only when
  an active job exists; normal successful reprint remains one action.

## Tests

- Backend full Maven suite: 815 tests, 0 failures/errors, 26 conditional skips.
  Includes real local PostgreSQL V29→V30 migration/replay/snapshot preservation,
  atomic claim/start ownership, outbox-origin scope, manual guard/replay and
  actual H2 transaction proof for REAL post-commit transport reservation.
- Frontend: 234 tests / 44 files; build PASS. Includes shared three-entry status
  contract, returned-ID polling, double click, confirmation, timeout→401 and
  renderer/module recreation preserving the same persistent intent key.
- Android: 15 JVM policy/transport/proof tests; compilation PASS. Fake sockets
  cover stalled connect/write, flush failure, possible bytes despite zero
  confirmed bytes, unconfirmed termination retaining the process lane, old/new
  owner overlap and standardized safe-connect errors. No hardware equivalence
  is claimed.
- Acceptance-script local checks: 4 PASS; proof vector matches native/backend,
  lost registration response cleanup excludes pre-existing/cross-Store devices.
- Governance validation, diff whitespace and reviewed Flyway-manifest collector
  tests PASS. Manifest adds already-current V29 plus V30; no deployment helper
  behavior was redesigned.

## Agent 6

Initial independent review: REQUEST CHANGES (3 P1, 1 P2).

| Finding | Repair and proof |
| --- | --- |
| Unknown result lost key after later 401 | Never discard unresolved identity on HTTP errors; regression added. |
| WebView recreation lost sessionStorage | Persist non-secret intent in localStorage partitioned by API environment/account/Organization/exact order-job path; recreated module replay test. |
| REAL output before idempotency commit | Reserve job/key/PRINTING before after-commit transport; new completion transaction; rollback/no-resend tests. |
| Native connection errors lost safe retry codes | Restore TIMEOUT/CONNECTION_REFUSED/UNREACHABLE only for confirmed zero-write failures; ambiguity never retries. |

First re-review closed the product findings but identified three P2 acceptance
fixture defects: shared pickup identity, lost-register-response cleanup and
same-Pad rather than cross-Pad reprint proof. These now use distinct pickup/order
IDs, exact-run device discovery minus the registration baseline, and A-origin
job reprinted by B. Final independent re-review: **ACCEPT**; all P1/P2 closed.
Reviewer independently ran the 4 script tests, governance/diff checks and all
30 migration checksum comparisons. Merge/deploy evidence is recorded below
only after actual execution.

## Runtime pre-observation

- Staging still `101dbdc75c25904c340251134ee1a0a03f5ce4b7`, V29 baseline.
- Staging health `/api/v1/system/health` UP; PostgreSQL healthy.
- DB container: `b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5`.
- Production containers/images unchanged; no mutation.
- Disk 70%, about 18 GB available; Build Cache 21.13 GB, reclaimable17.41 GB.
  Reviewed cache dry-run NO_GO: reclaimable records not clearly eligible.
  No cleanup or threshold bypass; current and rollback images protected.
- Planned API slice reuses audited synthetic Stores 26/27 from Menu acceptance,
  not STG005 source or Chinatown. It creates two synthetic credentials only in
  memory and revokes those devices on exit; no real binding/endpoint.
- Staging policy remains DISABLED/MOCK. Online PAD_DIRECT active-confirmation,
  claim/affinity/start/expiry and real Android lifecycle/renderer/hardware tests
  cannot be represented as Staging PASS under that policy. Local tests cover
  the protocol; actual hardware remains the Owner Gate.

## Owner three-Pad acceptance checklist (after a separately approved hardware setup)

1. Confirm isolated test environment and approved printer endpoints; do not
   point this unapproved-for-Production package at Production. Install matching
   signed APK in place on A/B/C, without uninstall/clear-data. Confirm paired
   identities survived and all three report the new build.
2. Enable only the approved test Store's PAD_DIRECT configuration. Verify each
   module keeps its assigned printer; clear any genuine uncertain old task by
   inspecting paper first, not by automatic retry.
3. A submits a 走洋葱 item. Inspect GRAB/HOT output, job preferred=A, attempts
   and one physical print per required module. Repeat from B and C.
4. While A has a PENDING preference window, B must still process B's own job.
   Keep A unavailable before claim: after 10 seconds B/C may claim the still-
   PENDING job. Do not simulate failover after PRINTING by resending it.
5. From B, reprint A's order using Dine-in, Order Center and Print Center in
   turn. Each explicit operation creates one new job preferred=B; old job and
   attempts stay intact. Double click/retry must return the same new ID.
6. While an active same-module job exists, verify cancel creates no job and
   explicit confirmation creates one new job. PRINTING warning must tell the
   operator to check paper for duplication risk.
7. Observe PENDING/CLAIMED/PRINTING progress and PRINTED completion. Interrupt
   response delivery: show unknown, not failure/success; login or renderer
   recovery then retry must retain the same intent/job.
8. Test zero-write connection failure, write stall/flush uncertainty and lost
   complete response. Uncertain output must stop for review, never auto-resend.
9. Background/foreground, repeated kick, Activity recreate and renderer crash:
   UI should recover, diagnostics should show phase/job/error, and no old/new
   worker may send the same job twice. Check backend attempts and actual paper.
10. Preserve sanitized job/attempt IDs, timing and device/build identities;
    do not include tokens, payload bytes or customer content. Stop on ambiguous
    output and report it; Production remains a separate release decision.
