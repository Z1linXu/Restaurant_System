# Uber Store capability and option 370 closeout

- PRIMARY_REPAIR_GOAL: reconcile Owner-confirmed option 370 and enforce formal Store-scoped Uber capability in the shared frontend/backend, then deploy only Staging.
- EXPECTED_REPAIR_CLASS: MEDIUM.
- Owner authority: tea_egg identity confirmed; ID370/price1.99/history preserved. Future Production target St-Denis/St-Catherine enabled, Chinatown disabled. No Production mutation.
- Fresh base: `04cc828d7bfb0c6dbc8baafc6499c32e0958dd47`; isolated `codex/uber-store-capability` worktree. Original dirty Owner workspace untouched.

## Product repair

V37 adds optional `UBER_EATS`, default off for existing/new Stores. Existing module configuration/Store context remains authoritative. Frontdesk badge and both routes/admin navigation are gated; Store switch discards stale capabilities immediately. Backend retains Store/org/role checks and gates scoped APIs, reprint and internal order processing. Worker pagination excludes disabled Stores; capability dependency drift is deferred 90 seconds so one broken Store cannot starve others. Re-enabling preserves queued work. No second ordering/printing pipeline.

Owner-authorized Staging reconciliation changes option370 identity/link only, under Store/history locks and exact baseline checks; every unrelated option and every historical business table is hash-compared. Canonical existing Store addon60 is tea_egg, price1.99. No historical snapshot changes or deletes.

## Tooling / governance repair

BLOCKING: reused previous reviewed note deployment helper with only exact baseline images, V37 and evidence-directory adjustments; it backs up Staging and validates the archive before changing application containers. Existing ingress/rollback/Production continuity helpers are reused. Reconciliation uses the previous reviewed history comparison helper and formal Store module API. No new deployment/acceptance framework. Read-only acceptance probes reuse existing HTTPS login, DB and container inspection patterns.

SCOPE_EXPANSION_CHECK: product changes span module/controller/import/reprint/frontend plus required migration/tests. The bounded deployment helper is copied with minimal baseline changes, not a new lifecycle; reconciliation SQL is the necessary requested data operation. No cleanup architecture, credential changes, Android rebuild or real orders/physical printing.

## Validation and runtime evidence

- Backend `mvn verify`: 946 tests, 0 failures/errors, 7 existing environment skips; 56 real local PostgreSQL Uber tests included.
- Frontend: 266 tests, targeted ESLint and production build PASS.
- V36→V37 upgrade/idempotency and actual reconciliation SQL: PASS, ID/price/history unchanged.
- Agent 6: one P1 cross-Store queue starvation found and closed with pagination filters + bounded configuration-drift retry; no remaining P0/P1/P2 after re-review. Runtime probe uses nonexistent negative order ID, avoiding business operations even on a gate regression.
- Credential-pattern scan, diff check, Python compilation: PASS. No credentials copied to source/docs.
- Preflight: option370 exact expected baseline; existing addon60 canonical tea_egg; Chinatown Store18 has zero active Uber bindings. Staging backend/frontend at 7ccef309, FlywayV36; Production containers unchanged at V28.
- Disk preflight 66%/20GB free; Build Cache15.14GB (9.956GB reclaimable). Reviewed cache age/size: older reclaimable entries exist, but healthy disk and bounded artifact-only build leave adequate headroom. No cleanup required for this repair; preserve runtime/rollback/evidence/volumes.

## Completed Staging acceptance

- PR #267 merged backend/initial frontend: `8eea8d47c9fe7906563d051196c703da7c14e198`.
- Browser acceptance exposed the existing separate desktop sidebar with no Uber entry. Bounded follow-up PR #268 adds the same module-filtered link: `8c3a9e356f59cd025beff25f7fb1d5a20594e028` (frontend only). Frontend regression now 267 tests; ESLint/build PASS, second Agent6 ACCEPT/P0=P1=P2=0. Backend artifact reused without restart for this follow-up.
- Staging URL: https://staging-pos.lanzhounoodlesmtl.com
- Backend exact SHA `8eea8d47c9fe7906563d051196c703da7c14e198`, image `sha256:f1261cc77211f03fd702414317941fe89483d24ace68972f4eb8b327907f7422`.
- Frontend exact SHA `8c3a9e356f59cd025beff25f7fb1d5a20594e028`, image `sha256:4bf301d4d547cc017be91c1e6d7c0d859cca345d4aed4db1696e32000e7048fc`.
- V37 upgrade PASS; health/login/Store context PASS. Pre-migration custom-format backup validated by pg_restore --list, private rollback configs retained in `/srv/restaurant-pos/staging/uber-capability-20261003/`.
- At 22:47:46 UTC option370 reconciliation/read-back PASS: parent25/braised_beef_noodle, code tea_egg, group ADD_ON, type addon, 加卤蛋 / Extra Tea Egg, price1.99, active true, Store addon60 linked. ID, creation time, history and unrelated options unchanged. All 12 historical business-table row counts/hashes identical inside the locked transaction. No historical order/receipt rewrite or delete.
- Add-on conflicts 1→0; actual Menu Management Add-ons page shows 加卤蛋 / Extra Tea Egg / $1.99 and no Values awaiting Owner decision section.
- Formal module PUT enables only Staging Store1. All other 8 Stores disabled; Chinatown Store18 has zero active bindings. Existing299 mappings intact.
- Enabled Store connection/Today/catalog APIs succeed; 11 disabled Store scoped GET/POST/PUT routes return403 MODULE_DISABLED. Mutation probes use nonexistent ID-1 after read-only existence checks; no business action executed. Cross-Store and reprint negatives covered by PostgreSQL/unit tests.
- Browser: enabled Store Today and Owner integration route accessible; Chinatown manual frontdesk/admin Uber URLs both show 功能未启用 / MODULE_DISABLED. Real Store selector1→18 removes Uber,18→1 restores it. Desktop sidebar and TopNav both observed. No new Uber order, Accept/Deny, reprint or physical print performed.
- Production backend/nginx/database image IDs and start times match initial preflight; Production ledger remains V28. Staging DB container retained. Final continuity observed22:51:47 UTC.
- Docker / Disk Hygiene: disk66%/20GB free→67%/19GB free; Build Cache15.14GB/9.956GB reclaimable→15.25GB/9.958GB reclaimable. Cleanup NO, reclaimed0. Cache reviewed and retained given healthy disk. Current/previous runtime images, rollback configs, DB backup/volumes and evidence protected; both environments healthy.
- Final target Production behavior supported by code, not configured this batch: St-Denis VISIBLE, St-Catherine VISIBLE, Chinatown HIDDEN. Production remains unauthorized.

Tooling follow-up necessity: existing combined deploy helper expects V36→37 and both components; rerunning it would be incorrect. A reviewed temporary frontend-only script reuses its ingress/model/locks/queue/rollback helpers, changes only nginx, and proves backend/DB/Production fingerprints plus ledgers unchanged. Script SHA256 `5361db044f206f7eb6d460b6d6d8c68e2045f84eaeb5edabd5fbe92096d00642`, retained with server artifact under `/srv/restaurant-pos/staging/artifacts/uber-sidebar-8c3a9e356f59cd025beff25f7fb1d5a20594e028/`. No reusable deployment framework added. Full private logs/probe results remain outside Git under `/tmp/rs-uber-capability-20261003/`; local test PostgreSQL stopped.

## Execution time

Start2026-10-03T22:30:30Z; product/runtime acceptance complete22:52:09Z. Investigation3m, implementation5m, tests4m, Agent6/PR/merge3m, deployment3m, acceptance3m, evidence/governance about2m (stage allocations estimated and overlapping). Final elapsed approximately23m. Retries: constructor/test-contract correction, SQL audit-column correction, isolated test-DB retry and queue regression repair; frontend-only follow-up found in actual browser. No scope expansion or Production operation.
