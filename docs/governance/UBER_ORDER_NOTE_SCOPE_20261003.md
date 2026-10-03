# Uber order note scope repair — 2026-10-03

PRIMARY_REPAIR_GOAL: stop cart-level note fan-out while retaining each customer's
item instructions and immutable reprints. EXPECTED_REPAIR_CLASS: MEDIUM.
Baseline main cff28d4a2927feefb04e601ef983780aba6f0aae; protected original Owner
workspace remains untouched. This is the Owner-authorized final closeout package,
Staging only; no Production operation or new business workflow.

Root cause: UberEatsOrderNormalizer correctly separates cart.special_instructions
(snapshot.notes) and item.special_instructions (item.notes), but mapping joined
both for every mapped item, including the selection-recovery branch, and rawLine
joined the order note into each ExternalKitchenSnapshot.notes.

Repair: an internal request field freezes normalized cart text separately, then
additive V36 stores orders.external_order_note_snapshot. Existing shared OrderService,
outbox, GRAB and HOT renderers carry it; one footer before外卖 per non-empty ticket.
Mapped/raw item notes compare only against normalized cart text (case-sensitive
exact equality after whitespace normalization). Modifier notes retain their target;
NO_OP note review checks remain unchanged. No text-content boilerplate filter.
Public POS create/replace rejects the external-only field, preserving local notes.
Existing historical rendered snapshots are not rewritten; reprints copy them.

Today API adds order_note_snapshot from the frozen request; raw_items is a note-free
summary and item notes are shown once in the authoritative item tree. Missing old
fields retain compatible source-note display. No additional Owner steps/settings.

Local validation:941 backend tests,0 failures/errors,7 optional skips;53 actual
PostgreSQL Uber tests with Flyway V36;119 focused order/render/reprint tests.
Frontend47 files/258 tests PASS; targeted ESLint and TypeScript/Vite build PASS.
Agent6 independent review ACCEPT, P0/P1/P2=0. Governance and diff checks PASS;
1362 source/docs/tool files credential-pattern scan has no high-confidence hits.
One full-suite retry used a fresh isolated database: reused prior-run synthetic
pending rows interfered with a global recovery test; the fresh run passed.

Operational tooling reuses the reviewed final-channel deploy helper; only current
image guards, V35→V36 expectation and private evidence directory differ. The small
read-only render bridge is necessary because the existing mapping-preview API
returns semantics rather than a whole ticket; it executes the exact runtime
classes with synthetic tasks and explicitly does not claim durable dispatch or
real Uber E2E. Durable creation/reprint proof comes from PostgreSQL integration
tests. No maintenance endpoint, physical order or printer action was added.

Pre-deploy Staging backend9c832cae/frontend22a71b05, V35, healthy. Production V28,
container IDs/images/start times remain the prior known runtime. Disk66% used,
20GB free; Build Cache15.03GB/9.955GB reclaimable. Cache hygiene review finds no
pressure; retain current/rollback images, database volumes and private backups.
No pruning. Exact Staging deployment and rendering evidence follow execution.
