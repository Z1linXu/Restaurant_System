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

Exact-SHA Staging deployment/read-back follows this reviewed merge. Production remains unauthorized. No new Uber orders or physical prints are part of this acceptance.
