# Dining Tables safe soft delete

PRIMARY_REPAIR_GOAL: replace Active/Inactive UI with safe table deletion in all
Production Stores, preserving history, A/B identities, split labels and printing.
EXPECTED_REPAIR_CLASS: HIGH

Owner authorized implementation, tests, required review, merge, Staging and
immediate Production deploy without another confirmation on 2026-10-24.
Start: 2026-10-24T05:21:50Z. Fresh main:
`c7b65ecc62633d6c1a60341995d199b440331d58`.
Clean attached worktree reused on `codex/dining-table-soft-delete`; original
Owner dirty workspace and unrelated worktrees protected.
Phase B bounded Package DINING_TABLE_SOFT_DELETE, no next Phase opened.

## Plan and affected paths

- backend station table lookup/locking, platform table endpoints/service,
  order table-entry gates and directly related tests.
- frontend DiningTablesManagementPage, API client, Frontdesk board/cache,
  dining route/submit entry guards and relevant tests.
- SYSTEM_DOCUMENTATION.md and doc/API.md for the current contract.
- Reuse deployment/cloud/dine-in-headers deploy/verify/tests and existing JAR
  print harness; no new release lifecycle, schema, migration or printing code.

Use is_active=false as an irreversible-through-normal-save tombstone. Both
Admin/Frontdesk table lists filter false on the backend. Add always active;
old PUT cannot resurrect. DELETE uses Store-scoped authorization and row lock,
rejects any unfinished full/A/B/legacy-code order including empty drafts.
Table locking serializes deletion with new orders, submission and context edits.
History display lookups continue to include tombstones; no order/print/sales
row is deleted or rewritten.

Deleted identities cannot be reused by Add/rename: raw table_no has no stable
row ID, so reusing the name/code would let an old cached context refer to a new
table. This is the smallest safe choice without changing order storage.
Existing live duplicate codes remain compatible. Truly unknown legacy raw table
identities retain existing backend compatibility; known tombstones do not.

Frontdesk requires a fresh server-active context before opening/restoring a
dining menu or manually submitting. Cached configurations are not authorization;
offline/unconfirmed contexts cannot enter ordering. Draft/outbox data is retained,
not cleared. Automatic idempotency replay keeps its existing backend semantics.

## Fresh read-only preflight

05:24:30Z: both app pairs still f838, V39. Production has 35 active and 4 inactive
rows among 39 tables (Owner changes after the prior release); all 3 Store
TABLE_MANAGEMENT capabilities enabled/CONFIGURED, including Chinatown. Release
must preserve the entire current dining_tables hash, not reset any is_active or
split mode. Current queues quiet, full environment/continuous Uber preserved.
Modes:35 LEFT_RIGHT/4 INSIDE_OUTSIDE; all current values preserved.
Disk77% /14GiB free; cache16.82GB. Strict aged review291 records,279 reclaimable,
all279 excluded for nonzero UsageCount; eligible0, no cleanup.

## Product simplicity and scope

Only the requested Delete button/confirmation replaces an Active toggle; Add
has fewer choices. No duplicate configuration, lifecycle or validation UI.
Necessary fresh network confirmation is a deletion safety boundary, not a new
user workflow; retained offline work is not silently discarded.

BLOCKING tooling adaptation only: pin current f838 rollback pair, new evidence
prefix, V39 complete ledger unchanged, full table-row hash unchanged, active
Delete UI public asset and unchanged 24+48+6 receipt proof. Preserve sorted full
bind strings and sanitized failure diagnostics from the reviewed helper. Old
one-shot retry logic is not needed in this new namespace; historical scripts
and evidence remain immutable at their exact SHAs. No cache/image cleanup or
new deployment framework enters this Package.


## Implementation and focused validation

Initial pre-review backend regression: 584 total,574 PASS,0failure/error,10 existing optional
skips (printing2, dailyclose8). Actual isolated PG24 executed: new soft-delete17,
previous split flow/concurrency6 and V39 migration1. Table/slot/code statuses
include empty draft, submitted, preparing, ready, picked_up, custom and null;
completed/cancelled are deletable. Two-direction delete/order concurrency proves
row locking; completed idempotent replay after delete returns the same order.
Controller Store authorization/capability denial and cross-Store service scopes
covered. Tombstone legacy-null compatibility, old PUT true, identity reservation,
history Order/frozenjob snapshots and duplicate live code compatibility covered.
A narrow wording correction explicitly says Complete or cancel / 完成或取消.

Initial frontend65files/371tests PASS, final build PASS. Changed18files lint has0errors;
one existing useDraftOrder catalogItems dependency warning reproduced at exact
c7b65ec baseline, no suppressions. New concurrency test fixture initially omitted
required orderType; fixed and all3 related cases PASS, final typecheck/build PASS.
Fresh Gate has no effect on takeout; retained offline data is not cleared.

Same-store dining_table.changed reuses existing afterCommit publisher and
Frontdesk topic, null order_id supported. Other Pads force refresh after100ms;
no new topic, polling loop, KDS or print event. TableConfig alone maps supports_split;
redundant Supports split checkbox removed per Add's requested fields.

Existing seed paths with force=false skip existing inactive rows; live Store
baseline is new-Store-only. Old explicit Part2 reconfiguration is restricted to
non-ACTIVE validation fixtures and stays out of scope, not an automatic revival.
Lock review found no reachable order Table/Order -> Store-write reverse cycle.

Tool guard tests8 PASS. Actual local class24+48+6 receipts PASS, original font,
leftmost headers, inline UPDATE, one extra16mm and full PAD/TCP bytes identical.
No renderer/transport/print state machine code was changed. Remote acceptance
will additionally verify actual authenticated GET Admin+Frontdesk table membership
against current active database rows for all3 Production Stores, including the4
existing inactive Chinatown rows. It reuses the reviewed short-lived numeric
identity and allows GET only; no password login or operational data writes.


## Consolidated safety repair before release

Delete confirmation now uses persisted table name/code, even after unsaved
editor changes; the final component6 cases and build/lint PASS. Fresh network
checks plus a second inflight check after local flush keep concurrent submit
clicks to one operation without altering the stable idempotency identity.

The final review cluster closes old API input variants: table lock classification
uses trim+ignoreCase for dine_in, active-order EXISTS lower(trim(order_type)) and
trim(table_no), and table identities include A/B plus legacy a/b suffixes. The
locking context alone recognizes lowercase seats; raw stored values and display/
printing are untouched, literal exact table names still take precedence.
Configuration names/codes with outer whitespace are rejected, not normalized;
read-only SQL proves both existing shared databases have0 such values. Empty-name
legacy code fallback remains valid. Known tombstones cannot fall into the unknown
legacy path through these variants. Actual local PG24 deletion cases now cover
this cluster; previous SplitLabel PG6 and migration1 are separate retained proof.


Final regression results: backend597 total,587 PASS,10 existing conditional skips,
zero failures/errors (aggregate final XML; unchanged printing404 and migration1
proof retained, affected order/platform/station re-executed). Actual PG35:
soft-delete28, prior split6, migration1. Frontend final root full run65files/372
PASS (2.18s), final build PASS. Agent6 independently ran8 tool/33 frontend tests.
Native SQL uses a constant chars1..32 CTE and btrim to exactly match Java.trim;
TAB/CRLF/SOH rows cannot escape occupancy guards. Raw values stay unchanged.
Names/codes must have at least one nonblank identity; empty-name/code fallback
is preserved. Both shared environments have0 pre-existing invalid identities.
No further executable changes are pending outside the consolidated review.

Agent6 consolidated review ACCEPT, no remaining P0/P1/P2; independent tool8/frontend33 PASS. Local isolated PostgreSQL stopped after final validation.


## Immutable release batch

PR #302 reviewed commit `ec2f98c18f30aa3080d9604d3f589f3fb5c7af43`, application
merge SHA `028d4971740889a0ac0d784a43497bc3e5b54b3d`. Fresh fetch/ancestry and
unchanged reviewed backend/frontend/deployment trees verified. Authority refreshed;
exact clean detached server release created. Stage build started05:42:17 UTC.
No new migration; entire V39 ledger and all table rows are immutable through
promotion. Current f838 backend/frontend pair is the exact application rollback.
Stage and Production each require fresh custom pg_dump and readable TOC. Only
backend/nginx are replaced; DB containers, all other runtime fingerprints, full
private environments and continuous Uber worker remain protected.


Stage deployed05:47:06 UTC (4m49 from build start), accepted05:47:42 UTC. Backend
`sha256:bf09f83635d532523b82c39dae30afbdfa12840858dd195d46423423cf49f696`;
frontend `sha256:af9a528ae56ceb905597794823b2ad417ef3c580fac6e3689c311ec837dcc9d9`.
Actual JAR SHA256 `1983c90d201aad1cd85b26cb27764ddaf605535d083cb18d643f96df2bded618`.
V39/all table rows/config/history/context/protected runtime remained unchanged;
Store18 actual authenticated Admin+Frontdesk GET both match3 active tables.
Public Delete/SplitLabel/absence of checkbox and same-byte preview assets PASS;
actual deployed JAR24+48+6 print cases, single16mm/fullPAD-TCP parity PASS.
No Stage application retry or rebuild. Read-only identity is not password login.
Production same-artifact promotion started05:47:49 UTC, fresh backup and f838
exact app-pair rollback retained; no database schema or business configuration writes.


## Production acceptance and final audit

Production deployed05:48:37 UTC, accepted05:48:58 UTC. Same Stage images/JAR;
no application retry/rollback/rebuild, no migration. Fresh readable backup SHA256
`2412b382c22d587efce7abfbf2232a9e2c45d486a60bed6922cd368810ea0115`.
Actual authenticated GET Admin+Frontdesk lists both exactly match DB active rows:
Store1 13, Chinatown20 excluding4 inactive, Store3 2. All39 table rows remain
unchanged,35 LEFT_RIGHT/4 INSIDE_OUTSIDE. Both public apps healthUP; deployed
Delete/SplitLabel/no-checkbox/preview assets match container bytes. Actual JAR
24+48+6 print cases preserve the original layout and single extra16mm feed.

05:49:46 UTC independent fresh read-only audit PASS: canonical target/release
marker/backup, Stage same pair/JAR/OCI, entire V39 ledger, complete configuration
and table hash, initial preflight independent row projection, history/context,
complete environment/continuous Uber, four protected containers, quiet queues
and four old PRINTING jobs unchanged. No real table/Delete/order/print API write
or device action was performed during live acceptance; behavioral CRUD/Finish/
isolation is actual local PG and frontend test proof, not a claimed live write.
No password login, logged-in UI click, physical Pad print or paper measurement.

## Docker / Disk Hygiene

Disk before Stage77% /14GiB free; after Stage/before Production78% /13GiB free;
after Production78%,13,720,465,408 bytes available. Build Cache before/after
16.82GB, reclaimable10.45GB,291 records. Strict second review of age>168h has266
reclaimable records, all excluded for UsageCount>0; eligible0/unparseable0.
WARNING / NON_BLOCKING_EXISTING_DEBT; healthy disk<70/cache<=8GB targets remain
unmet, no new acceptance gate introduced. Cleanup performed:no; reclaimed0.
Current Stage/Production images, exact f838 rollback pair, database containers/
volumes, private configuration, fresh backups and authority evidence protected.
Both sites UP. Production mutation is only the expressly authorized app-pair
release; no Flyway, operational data or Uber configuration mutation. Own bounded
builder/render containers removed, local isolated PG/Java stopped.

## Product / tooling and governance separation

PRODUCT REPAIR: backend platform table CRUD, station context locking, order
repository/entry guards; frontend table editor/board/ordering/cache and relevant
tests. Implementation about8 minutes; tests/input-safety repair about9 minutes
(Estimated, parallel). Includes persisted confirmation and ASCII case/whitespace
edge closure; no adjacent menu/price/Combo/modifier/device/printing changes.

BLOCKING TOOLING: reuse existing dine-in-headers deploy/verify/test paths, pin
current rollback pair, preserve complete V39/table rows, verify actual read-only
lists and public UI; about3 minutes (Estimated, parallel with product work).
GOVERNANCE: SYSTEM/API/current-state and this evidence; docs-only final sync uses
the risk-based review exception after successful runtime audit. No extra app
build or deployment. Unrelated lint warning, pre-active validation-fixture
provisioning and disk cleanup remain outside this bounded release.

Stop: DINING_TABLE_SOFT_DELETE_PRODUCTION_COMPLETE; global Production default
mutation_authorized=false and authorization.production=NONE remain. No next
Package or Phase is opened by completion.
