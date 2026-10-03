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
No pruning. Exact Staging deployment and rendering evidence are recorded below.


## Merged release and Staging acceptance

PR [265](https://github.com/Z1linXu/Restaurant_System/pull/265) merged at
2026-10-03T21:07:52Z, exact reviewed head
`cf33306121f2a23fd30fc5fd84f56322cd48874f`, main/runtime merge
`7ccef30919b268732a7f7c369cdd8370a8782ffe`. No required GitHub checks were present;
normal merge used exact-head matching after local gates and independent review.
Merged source tree equals the tested artifact tree.

Staging backend/frontend both run that merge SHA, Flyway V36 success (checksum
1806435613). Backend image
`sha256:8a014fa1ef503662805b06652386352368220cdf74c9451f23c4a0d249c23635`;
frontend image
`sha256:e5abdce0636d94a4929eb0ef4a095626cd7a910f82e8b7362a0096ca3c65cdf2`.
Runtime JAR SHA256
`79262cab93e1109d0687d7e3c4b14f50336a649491c8af07fae4fc6467692449`;
frontend USTAR SHA256
`1b471fe33fa022c1dfd215822600bf316b52f83e2d9cbdc4abad03591bcb01cb`.
Full pre-deploy Staging dump was validated by pg_restore --list; private dump,
baseline and rollback Compose remain in `/srv/restaurant-pos/staging/uber-note-20261003`.
Only backend/nginx were replaced. Staging DB container, ingress, TEST credentials,
Store/Pad/printer config and Production containers/start times/environment digest/
V28 ledger remain unchanged. No data backfill, mapping write, replay or print ran.

At21:10:28Z runtime acceptance PASS: public HTTPS health UP, authenticated login and
workspace,299 existing mappings,12/12 current modifier previews, canonical Add-ons,
Dashboard/Reports today/week/month category totals and amount/quantity parity,
frontend routes and printing state. Today5 Uber orders/45 quantity/CAD646.92;
Week/Month POS19.53 + Uber885.92 = CAD905.45, no missing amounts. Existing option370
is the only ambiguous Add-on conflict (2→1 from the prior batch); extra_meat and
tea_egg are resolved. Current known mapping gaps remain0. No repeat data writes.

At21:10:47Z actual HTTPS mapping-preview plus exact runtime renderer proof PASS:
[synthetic rendered snapshots](runtime/UBER_NOTE_RENDER_20261003.json).
Nine mapped chow-mein roots plus one unknown raw root; two distinct mapped item
notes and a raw item note remain intact. Seven mapped notes exactly equal the
normalized cart note and are suppressed only there. Each GRAB/HOT ticket has one
order note after its items and before外卖. RAW does not enter guessed HOT routing.
The helper uses explicit synthetic WOK tasks and is a preview/render proof only,
not a new durable Staging order, physical print or real Uber E2E. Actual PostgreSQL
integration tests separately prove frozen create/outbox/reprint behavior.

The temporary runner was independently reviewed ACCEPT, P0/P1/P2=0; SHA256
`04659223fcd90d590846192255d7a9c27f59a068f4c9ef52a8c1ff59dc1bce6f`.
It binds Staging TEST identity, verifies running JAR/artifact and compiled helper
hashes, extracts only runtime classes/libraries, and runs Java without network,
credentials, Spring, database or dispatch (read-only, dropped capabilities,
128MB/0.5CPU). Synthetic notes only are included in the committed evidence.

B8E83 before/after hashes match for Uber record, order (excluding new nullable
column), order items and all print jobs. Existing tickets intentionally retain
their original historical content; reprint remains exact, without Uber GET.
Browser Today acceptance shows one whole-order note for B8E83, its distinct item
instruction only beneath its item, and no note fan-out in raw summaries. No
reprint button or order action was used. Customer text is omitted from evidence.

Post-deploy21:11:01Z: disk66%/20GB free; cache15.14GB (9.956GB reclaimable).
Second cache review: stable below70% disk with adequate headroom, retain current
and previous rollback artifacts; no prune/cleanup,0GB reclaimed. All Staging and
Production containers remain running, restart counts0. Production mutation NO.

Work started20:56:13Z. Investigation, implementation, tests and review overlapped.
One fixture-compatibility test repair and one fresh-database full-suite retry are
included; no Staging deploy retry was required. Final elapsed interval is in the
Owner report. Product repair is the V36/note-scope change; operations used the
bounded reviewed helper, with no new endpoint or general deployment framework.
