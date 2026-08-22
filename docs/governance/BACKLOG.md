# Active Backlog

Only active or intentionally deferred work belongs here. Historical/completed
narrative is preserved in [`docs/archive`](../archive/README.md). Priority uses
`P0` critical through `P3` improvement.

| ID | Type | Status | Priority | Phase / Package | Blocking | Description | Authority / reference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PB1-ACCEPT | FEATURE | OWNER_MANUAL_ACCEPTANCE_PENDING | P1 | Phase B Part 1 | Yes | Repository implementation and exact-SHA Staging automated acceptance are complete; stop for Owner manual review of the non-live validation Store. | [`CURRENT_STATE.yml`](CURRENT_STATE.yml), [`ROADMAP.md`](ROADMAP.md) |
| KI-A10-001 | BUG | OPEN_NON_BLOCKING | P3 | Phase B / UX debt | No | Replace generic KDS-disabled HTTP 500 with a clearer fail-closed capability response when separately authorized. | [historical issue detail](../archive/governance-pre-simplification/KNOWN_ISSUES_BACKLOG.md) |
| PRINT-HARDWARE | FEATURE | OWNER_GATED | P2 | Phase B Part 2 | No for Part 1 | Real Printer/Pad endpoints, pairing and physical binding require a separately authorized package and exact environment scope. | [`ROADMAP.md`](ROADMAP.md), [`drafts/`](drafts/) |
| PB2-PROVISION | FUTURE | NOT_AUTHORIZED | P2 | Phase B Part 2 | No for Part 1 | Complete tables/stations, staff/access, printing topology, device enrollment, validation, READY and activation workflow. | [`ROADMAP.md`](ROADMAP.md), [`drafts/`](drafts/) |
| PC-MULTISTORE | FUTURE | NOT_AUTHORIZED | P2 | Phase C | No | Prove Chinatown then Sainte-Catherine through accepted Phase B provisioning without special branches or SQL shortcuts. | [`ROADMAP.md`](ROADMAP.md) |
| DOC-TECH-SLIM | TECH_DEBT | DEFERRED | P3 | Governance | No | Reduce remaining technical-document drift only when maintenance pressure justifies a separate scoped package. | [`AUTHORITY.md`](AUTHORITY.md) |

## Backlog rules

- One row must describe one actionable or intentionally deferred item.
- Closed implementation timelines do not remain here.
- Runtime status belongs in `CURRENT_STATE.yml`, not in backlog prose.
- A backlog row never authorizes implementation, deployment or mutation.
- Add detail in a task-specific plan only when the work is Owner-authorized and
  the row is too small to execute safely.
