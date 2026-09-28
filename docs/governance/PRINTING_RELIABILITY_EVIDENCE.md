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
- Acceptance-script local checks: final 5 PASS; proof vector matches native/backend,
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

## Merged artifact and APK

- Implementation PR: #237; merged application SHA
  `31b54c6644ea6bc32a34c171ff85ee76fbe249db` (57 changed files).
- Bounded deployment-helper repair PR #238; final merged executable/artifact
  SHA at that step `86ccedd019b4e833a3612b8f3ad7bf8e3a0760f2`. No product runtime diff from
  #237. Existing retention scanned an unrelated retained cloudflared binary
  as text. Streaming opaque-input detection now conservatively protects ALL
  releases. Full hygiene regression and independent Agent 6 re-review ACCEPT.
  No release/cache cleanup, binary removal, or guard bypass was performed.
- Live acceptance on that SHA failed with `proof_http_403`: registration stores
  Base64(SHA-256(token)), but proof verification decoded hex. PR #239 corrects
  that decoder with a strict 32-byte check and adds a real-registration /
  independent-native-signature interoperability test. Focused tests and Agent 6
  re-review ACCEPT. Merged SHA at that repair:
  `d2ac3e65c887b8903f02d455f177e59eaf75fc95`.
- On that SHA all printing checks passed, but the final menu invariant compared
  the per-response `generated_at` clock. PR #240 excludes ONLY this top-level
  response clock. Nested business data, revision/hash, names and prices remain
  compared. Five script tests and independent Agent 6 ACCEPT. Final accepted
  executable SHA: `7b70a4fac4e350444bcd37687ca88a8636ff8f2a`.
- The Base64 repair was followed by focused `PrintOriginFilterTest`,
  `StoreDeviceServiceImplTest`, `ManualReprintServiceTest` and
  `ManualRealTransportTransactionTest` PASS. The earlier 815-test full-suite
  result is not represented as a second full-suite run after this repair.
- First acceptance failure is preserved in `printing-86ccedd-acceptance.json`;
  draft order 68 remains as failed-run evidence, devices 21/22 were revoked.
  No physical output or historical mutation occurred.
- Canonical build: `restaurant-pad-app/scripts/build-bundled-apk.sh debug`, in
  a separate detached worktree at that exact merged SHA; no APK installation.
- APK: `/Users/xuzilin/Downloads/RestaurantPad-printing-reliability-7b70a4f-debug.apk`.
- SHA-256: `f87bd8312bba3b111211c62f9b33517151b1637bf005e14b28ef875218c60004`.
- Package `com.restaurant.pad`; versionCode `3`, versionName
  `0.3.0-printing-reliability`.
- Build identity: `printing-reliability-7b70a4fac4e350444bcd37687ca88a8636ff8f2a`.
- Build-info generation: `2026-09-28T17:54:11.899Z`; offline schema remains 4.
- Asset manifest SHA-256:
  `66e41d53431fd0e9e3ea1722e6535c4bb9758667e5c5313f106534a6cc854648`.
  All 57 embedded assets verified against manifest; index references
  `assets/index-DcP5xghl.js`; bundled reprint/proof code and native deadline /
  proof / renderer recovery classes verified inside APK.
- Certificate SHA-256:
  `eefc3470dfeb1d118a69c2d0ba011c2f8a158e79bc23395caf0ea3ef8d35e106`;
  matches the existing local versionCode 2 debug APK. In-place upgrade is
  compatible with that signing lineage; actual installed-device certificate
  was not inspected. Never uninstall/clear data to work around a mismatch.
- Staging preflight PASS (8 GiB disk / 75% maximum / 1 GiB available memory /
  two CPUs); evidence SHA-256
  `8254f291e11fadb857db0f4d21aa28c8583b01ac710e465d29d9f5c5739443d5`.
- First deploy attempt safely stopped before build: the prior exact release
  contained one audit-generated Python `.pyc`. Preserved it under the private
  task cache in Staging `state`; prior release is clean again. No historical
  evidence/source deletion and no deployment guard change. Runtime Python
  checks use `PYTHONDONTWRITEBYTECODE=1`.

### TOOLING_REASSESSMENT

The legacy all-project readiness collector returned NO_GO because an existing
Uber webhook container carries the same Staging project / nginx service labels;
the collector assumes exactly three containers. It existed before this task and
remains untouched. This is non-blocking existing collector debt for the bounded
MOCK API slice, whose reviewed `staging-runtime-evidence.sh --validate` guard
independently validates the exact release/env/running backend and whose client
checks actual DISABLED/MOCK + endpoint-disabled process policy. Reuse that path
and read-only health/container/Flyway projection; do not change the collector,
stop/relabel Uber, forge readiness evidence, or claim its NO_GO as PASS.
OPS001 combined collector and same-container restart acceptance remain NOT
EXECUTED. Exact application startup/migration and core health are separate facts.

## Final Staging evidence

- Final runtime guard: `staging-runtime-evidence.sh --validate` PASS against
  exact release, immutable preflight digest and running backend identity.
- `staging-health-check.sh` PASS: backend UP, frontend HTTP 200; DB healthy.
- V29 → V30; all 30 successful Flyway version/script/checksum rows match the
  exact release manifest. V30 checksum `119071053`; full row SHA-256
  `6b41ef8f83e7fb821b6e55d185faa70c92724123faff0d389faf8b10b8753a39`.
- Staging DB container unchanged:
  `b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5`.
- Backend container:
  `a3eb3ead95ef7750c35c25fcf99abf8d0ffcd497ba251ddb537776c3398b8fcc`;
  image `sha256:23f332075f0142f2aeb941da62d58212cd595b1e393a69adb609b4869f18d870`.
- Frontend container:
  `92f3872694ac7cadd7e7800c8ecc672560585254f18d5110386d705a4382fe5a`;
  image `sha256:a146f2b927e29afd46d6a1355712052f58db3c98ab3346f9df72a59fab81d3cf`.
  Both exact-SHA image tags, running, restart count zero.
- Private sanitized acceptance report:
  `/srv/restaurant-pos/staging/evidence/printing-7b70a4f-acceptance.json`;
  SHA-256 `8699ea8b66b4919fd331e57c07caf46cbb2f375e23d1b88c33cd72bca2449e07`.
  Result **PASS_NON_PHYSICAL_SLICE**. Synthetic Store 26 only; Store 27 control
  remained unchanged. No new Store was created.

| Executed final online slice | Evidence |
| --- | --- |
| A and B signed submit, durable origin, GRAB/HOT onion | Orders 71/72; devices 25/26; jobs 177–182; all MOCK. |
| Print Center new frozen job and same-key replay | Source 177 → new 183, preferred B. |
| Full-order reprint, same-key replay and changed-request rejection | New 184, preferred B; changed module with same key rejected. |
| Disabled origin proof denied | PASS; new devices 25/26 revoked on exit. |
| Old jobs/orders, Store menu/control menu, Master/Profile | Unchanged; only top-level response clock excluded. |
| Migration and API boundary | Exact V30; no printer endpoints, no real device tokens or hardware. |

Failed-run evidence is retained. The first run left draft order 68 and revoked
devices 21/22. The second run created orders 69/70 and jobs 168–175, passed its
printing assertions, then failed the clock-only invariant; devices 23/24 were
revoked. No historical order/job snapshot was rewritten to make a test pass.

### Browser and non-executed matrix

- Authenticated synthetic Store Frontdesk / Order Center and Print Center are
  accessible; final-page reload displays orders 71/72, 走洋葱 and MOCK PRINTED
  records. No real Store, credential or printer endpoint was changed.
- Order Center on the preceding `d2ac3e6` artifact (same frontend source as
  final `7b70a4f`, except generated build identity) successfully created new
  MOCK job 176 for order 70 and visibly showed `#176 打印完成`.
- Final-artifact browser read smoke PASS, but further button interactions did
  not yield observable changes through the automation surface. The earlier
  local empty-takeout-draft tab could not be closed: browser command
  `Emulation.setFocusEmulationEnabled` timed out. No server order was submitted
  from that empty draft. This is an automation limitation, not proof that the
  product button succeeded or failed. No unrelated browser/tooling repair was
  used to manufacture a PASS.
- Final browser reprint interaction for Print Center and Dine-in, final-SHA
  repeat of Order Center click, browser PENDING/CLAIMED/PRINTING/FAILED/unknown
  rendering and active-confirmation dialogs: **NOT EXECUTED / NOT VERIFIED**.
  Shared service/component tests cover those contracts locally; do not treat
  those tests as completed browser or physical acceptance.
- Online PAD_DIRECT window/expiry/CLAIMED/start-print/PRINTING-no-failover,
  active confirmation and actual backend restart/outbox replay:
  **NOT EXECUTED** under the retained DISABLED/MOCK-only Staging policy. Local
  migration/outbox/conditional-transition tests are separate proof.
- Real Android lifecycle/renderer crash/native stalls/three-Pad physical
  concurrency and paper: **NOT EXECUTED**, Owner Gate. JVM tests do not prove
  actual device scheduling or physical duplicate avoidance under every fault.
- No new multi-threaded PostgreSQL hardware-concurrency stress claim is made;
  conditional-transition, ownership and transaction tests have narrower scope.

## Protection and Docker / Disk Hygiene

- Production backend `72f78e86d907...`, frontend `a08622790b2c...` and DB
  `c2ab37fec6ac...` identities/images unchanged, running, restart count zero;
  PostgreSQL healthy. Production application remains `11996ef...`, V28.
  No Production mutation, deployment, credential change or physical printing.
- Disk: 70% / approximately 18 GB free → 75% / approximately 15 GB free.
  Build Cache: 21.13 GB (17.41 GB reclaimable) → 23.71 GB
  (19.51 GB reclaimable). Four canonical APK builds were local; three completed
  Staging image builds/replacements followed reviewed repairs.
- Final second reviewed cache dry-run returned
  `HYGIENE|NO_GO|reclaimable BuildKit records are not clearly eligible`.
  Cleanup NONE, reclaimed 0 GB. Active/rollback images, all releases referenced
  by opaque evidence, volumes and evidence remain protected. 75% is a warning,
  not critical; further builds should first revisit eligible-cache evidence.
- Unrelated existing Uber webhook container and its labels were untouched.
  No additional application rebuild is required for this docs-only evidence
  sync; the accepted/deployed application identity remains `7b70a4f`.

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
