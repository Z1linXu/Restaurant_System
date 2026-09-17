# Active Backlog

Only active or intentionally deferred work belongs here. Historical/completed
narrative is preserved in [`docs/archive`](../archive/README.md). Priority uses
`P0` critical through `P3` improvement.

| ID | Type | Status | Priority | Phase / Package | Blocking | Description | Authority / reference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| UBER-SANDBOX | FEATURE | TEST_STORE_PROVISIONING_BLOCKED | P1 | Phase B / Uber Eats v1 | Yes for Production | Supply required Sandbox request address and submit official Test Store request; verify test Store/order manager, map real menu IDs, and run real Sandbox + field printing acceptance. Temporary HTTPS callback requires availability recheck before continuing. No Production activation. | [integration](../UBER_EATS_INTEGRATION.md), [staging evidence](UBER_EATS_STAGING_ACCEPTANCE.md) |
| UBER-STORE-ENABLE | FEATURE | DEFERRED | P2 | Phase B / Uber Store control | No for Sandbox | Small follow-up: store-scoped UBER_EATS enable control; planned St-Denis and St-Catherine enabled only after pilot authorization, third Store disabled. Actual UUIDs pending. | [integration](../UBER_EATS_INTEGRATION.md) |
| MENU-SIMPLIFY | FEATURE | STAGING_ACCEPTANCE_PARTIAL | P1 | Phase B / Menu Management | Yes | Implementation merged and deployed V28; API slices passed, Owner browser and foreign-Organization runtime proof pending. Production business-value reconciliation remains Owner-gated. | [contract](contracts/MENU_ADDON_CATALOG_CONTRACT.md), [state](CURRENT_STATE.yml), [evidence](MENU_SIMPLIFICATION_STAGING_EVIDENCE.md) |
| PB1-ACCEPT | FEATURE | OWNER_ACCEPTED_CLOSED | P1 | Phase B Part 1 | No | Repository implementation, exact-SHA Staging automated acceptance and Owner manual acceptance are complete; Part 1 remains non-live and does not authorize activation. | [`CURRENT_STATE.yml`](CURRENT_STATE.yml), [`ROADMAP.md`](ROADMAP.md) |
| KI-A10-001 | BUG | OPEN_NON_BLOCKING | P3 | Phase B / UX debt | No | Replace generic KDS-disabled HTTP 500 with a clearer fail-closed capability response when separately authorized. | [historical issue detail](../archive/governance-pre-simplification/KNOWN_ISSUES_BACKLOG.md) |
| PRINT-HARDWARE | FEATURE | OWNER_GATED | P2 | Phase B Part 2 | No for Part 1 | Real Printer/Pad endpoints, pairing and physical binding require a separately authorized package and exact environment scope. | [`ROADMAP.md`](ROADMAP.md), [`drafts/`](drafts/) |
| PB2-PROVISION | FEATURE | OWNER_MANUAL_ACCEPTANCE_PENDING | P2 | Phase B Part 2 | No | Repository implementation and exact-SHA Staging automated acceptance are complete for tables/stations, staff/access, printing topology, device enrollment, validation, READY and activation; real Store activation and physical bindings remain Owner gates. | [`CURRENT_STATE.yml`](CURRENT_STATE.yml), [`ROADMAP.md`](ROADMAP.md), [`PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE_EVIDENCE.md`](PHASE_B_PART2_STAGING_AUTOMATED_ACCEPTANCE_EVIDENCE.md) |
| PC-MULTISTORE | FUTURE | NOT_AUTHORIZED | P2 | Phase C | No | Prove Chinatown then Sainte-Catherine through accepted Phase B provisioning without special branches or SQL shortcuts. | [`ROADMAP.md`](ROADMAP.md) |
| DOC-TECH-SLIM | TECH_DEBT | DEFERRED | P3 | Governance | No | Reduce remaining technical-document drift only when maintenance pressure justifies a separate scoped package. | [`AUTHORITY.md`](AUTHORITY.md) |
| FRONTEND-FAVICON-MODE | BUG | OPEN_NON_BLOCKING | P3 | Frontend packaging | No | The accepted V26 frontend image contains `favicon.svg` as mode 0600, producing HTTP 403 on Staging and Production while `index.html`, all referenced JS/CSS assets, API and WebSocket remain healthy. Normalize public-asset readability in a future reviewed image build and verify through the normal exact-SHA Staging path. | [`PRODUCTION_V10_V26_RELEASE_EVIDENCE.md`](PRODUCTION_V10_V26_RELEASE_EVIDENCE.md) |
| OPTION-CODE-STABILITY | TECH_DEBT | ADD_ON_IN_SCOPE_OTHER_GROUPS_DEFERRED | P2 | Phase B / Menu identity | Yes for ADD_ON | ADD_ON identity is covered by MENU-SIMPLIFY; other option groups remain deferred. Historical snapshots must never be rewritten. | [contract](contracts/MENU_ADDON_CATALOG_CONTRACT.md), [`doc/API.md`](../../doc/API.md) |

## Backlog rules

- One row must describe one actionable or intentionally deferred item.
- Closed implementation timelines do not remain here.
- Runtime status belongs in `CURRENT_STATE.yml`, not in backlog prose.
- A backlog row never authorizes implementation, deployment or mutation.
- Add detail in a task-specific plan only when the work is Owner-authorized and
  the row is too small to execute safely.
