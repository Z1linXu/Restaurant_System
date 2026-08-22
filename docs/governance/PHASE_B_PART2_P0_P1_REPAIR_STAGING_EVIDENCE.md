# Phase B Part 2 P0 + P1 Repair — Staging Evidence

Date: 2026-08-22 (America/Toronto)

Implementation authority: `7dfe1e9d691941c6c36ee61aaaa69b4e17fca9ed`

Final exact Staging artifact: `ae446874e6a6bc7d2c19cdbc1ca92603ed53d6de`
(PR #209 governance/evidence sync; application code is the reviewed
implementation above)

Environment: isolated Staging (`restaurant-pos-staging`)

Flyway: V26 (26 migrations validated; no migration added)

Owner gate: `PHASE_B_PART2_OWNER_MANUAL_ACCEPTANCE = PENDING`

This is additive repair evidence. The complete Part 2 matrix was not rerun;
the authoritative full acceptance remains
[Phase B Part 2 product-flow Staging acceptance](PHASE_B_PART2_PRODUCT_FLOW_STAGING_ACCEPTANCE_EVIDENCE.md).
The current gate is supported by that existing full PASS plus the exact-SHA
repair regression and runtime evidence below.

## Boundary

- Production containers, data, credentials, Flyway, deployment and restart were not mutated.
- No real printer endpoint, real Printer binding, Pad/device binding or hardware action was used.
- KDS/Pickup optimization and Phase C were not entered.
- Runtime verification used Store 1 (`STG005_SRC_20260809_R01`) and endpoint-free MOCK printing.

## P0 result

### Frontdesk correctness

- The production-mode Frontdesk bundle verified on Staging no longer initializes with demo `T1`–`T8` tables.
- Loading, legal zero-table, failure/retry and generation-guarded Store switching are explicit.
- Store changes immediately clear tables, occupancy and orders; delayed old-Store responses cannot write into the new Store.
- Browser acceptance rendered Store 1's actual tables (`1里`, `1外`, `2里`, `2外`, `3`…`11`) and retained left/right/whole-table controls.
- A Store 1 manager was rejected from Store 18 context with HTTP 403, confirming Store isolation.

### Printing role and MOCK runtime

- `store_logical_printer_roles.enabled` is the Store-local canonical requirement input.
- Enabled roles are REQUIRED; disabled/excluded roles are NOT_REQUIRED and do not fail the Printing aggregate.
- MOCK reconciliation is transactional, idempotent, Store-scoped and endpoint-free. Assignment writes do not silently rewrite role requirements.
- Print dispatch and readiness share the same enabled-role rule; no third Owner-maintained station declaration was introduced.
- Outbox results distinguish dispatched/printed, MOCK-rendered, skipped/policy-blocked/capability-unavailable and failed outcomes using the existing schema.
- Browser acceptance for order 53 made one `print-options` request and one `reprint` request, both HTTP 200. PrintJob 147 was Store/Organization scoped, `FRONTDESK_RECEIPT`, `PRINTED`, retry count 0, rendered snapshot length 659, and had no IP/port/printer endpoint.
- HOT_KITCHEN correctly appeared disabled for an order without applicable hot items rather than creating an inapplicable ticket.

### Print request coordinator

| Metric | Before | After |
| --- | ---: | ---: |
| Requests per order interaction | 8 | 2 |
| Failed requests | 8 | 0 |
| Retry duration | about 16 s | about 1 s in coordinator tests; immediate HTTP 200 on Staging |

The shared coordinator cancels on Store/order change and unmount, rejects stale responses and stops retrying classified policy/capability outcomes.

## P1 result

### Admin and Store-scoped reads

- Initial Admin dashboard fetches decreased from 2 identical calls to 1; Staging returned one 3,550-byte dashboard response.
- Single-Store context was 8,303 bytes, replacing the previously observed approximately 140–145 KB platform-overview dependency for Store pages.
- Backend repositories use Store-scoped selectors for Store pages while retaining explicit Platform Admin global views.
- Offline reconciliation deduplicates order IDs, removes terminal local records, limits concurrency to 3 and cancels on Store change/unmount.

### Frontdesk realtime and polling

- Root cause of the production-built browser bundle's blank Staging bootstrap was a SockJS transitive Node-style `global` reference. Vite now maps it to standard browser `globalThis`; a Vite configuration regression test guards the mapping, and production build inspection confirmed no unresolved bare `global` in the generated entry bundle.
- A real SockJS/STOMP session completed `/ws/info` and HTTP 101 WebSocket upgrade. The observed connection remained open for 115.631 seconds until navigation closed it.
- Connection lifecycle diagnostics are sanitized; event-driven refresh is primary, with 120-second connected reconciliation and 30-second disconnected fallback.
- A 303-second idle browser window recorded 19 HTTP requests, 0 failures: 10 health checks, 5 menu revision checks, 2 table refreshes and 2 order refreshes. No application order event occurred during the intentionally idle window.

| Metric | Before | After |
| --- | ---: | ---: |
| Frontdesk HTTP requests / 5 min | about 35 | 19 |
| Failed requests / 5 min | 0 | 0 |
| WebSocket handshake | 0 | 1 real HTTP 101 session |
| Application data events in idle window | 0 | 0 (expected: no order mutation) |
| Tables refresh / 5 min | polling baseline included in 35 | 2 |
| Orders refresh / 5 min | polling baseline included in 35 | 2 |

### Infrastructure hygiene and observability

- Reviewed release retention protects current, previous verified, rollback/recovery and Production-related artifacts and uses `git worktree remove`, never raw recursive deletion.
- 31 reviewed old release worktrees were removed. Release directory bytes fell from 6,322,648,813 to 3,487,286,021 immediately after cleanup: 2,835,362,792 bytes (2.64 GiB) reclaimed. Subsequent exact-SHA repair deployments raised the final release total to 32 directories / 3,853,725,474 bytes.
- Two legacy releases remain fail-closed: `35033645…` has unsafe mode/provenance and `b83afa98…` fails clean-worktree validation. They were not deleted.
- Final disk is 81% used with 11,251,268 KiB available, versus 82% / 10,925,174,784 bytes available at baseline. Net host usage fell by 596,123,648 bytes while repeated image builds occurred.
- BuildKit cleanup dry-run remains NO_GO because old reclaimable records are not all clearly immutable, unshared and zero-use. BuildKit reclaimed 0 bytes; no broader prune, image prune, volume prune or database action was attempted.
- Staging containers use the Docker `local` log driver with `max-size=10m` and `max-file=3`. Nginx exposes sanitized method, normalized URI, request time and upstream timings. Shared-host journald and Production logs were not mutated.
- Disk checks report warning at 80% / under 10 GB available and critical at 90% / under 5 GB available.

## Verification

- Frontend full test suite: 31 files / 140 tests PASS.
- Frontend production build: PASS; generated entry bundle contains no unresolved bare `global` reference.
- Backend full Maven suite: PASS.
- Focused Printing/backend suites: PASS, including enabled-role matrix, disabled NOT_REQUIRED, MOCK reconciliation, PrintJob snapshots/reprint/update, outcome semantics, isolation and STG005 regression.
- Infrastructure hygiene, deployment CLI/runtime guards and Staging safety tests: PASS.
- Agent 6 reviewed the implementation and each bounded deployment-tooling repair; final result APPROVED.
- Staging loopback frontend/backend health: PASS; `/api/v1/system/health` returned `UP`.
- Staging and Production container restart counts: 0.
- Production container image IDs remained `061ac73df1ee`, `2de71105c8fa` and `postgres:16-alpine`.

## Merged PR chain

- Core repair: PR #198 (`57d89faacb56403d84029a5613ce3494fd4c3b7d`)
- Initial documentation: PR #199 (`30da94adfd1dad37276326832efdadac6d2edc53`)
- Reviewed Staging hygiene compatibility repairs: PRs #200–#207
- Browser `globalThis` repair: PR #208, final implementation SHA `7dfe1e9d691941c6c36ee61aaaa69b4e17fca9ed`
- Governance/evidence sync: PR #209, exact Staging artifact `ae446874e6a6bc7d2c19cdbc1ca92603ed53d6de`

## Remaining risks

- BuildKit cache remains the largest unresolved disk source. The reviewed tool correctly refuses ambiguous records; changing eligibility policy requires a separately reviewed safety decision.
- The two unsafe legacy release directories remain retained until their provenance/cleanliness can be established.
- The runtime idle proof intentionally produced no order mutation, so application event callback behavior is covered by automated tests while the real Staging proof establishes the live 101 transport and reconciliation behavior.
- Real printer connectivity and Pad execution remain separate Owner gates.

The Phase B Part 2 automated acceptance result is unchanged. This repair returns to Owner manual retest and does not authorize Phase C.
