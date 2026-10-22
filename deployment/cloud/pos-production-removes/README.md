# POS + canonical Remove release batch

This bounded 2026-10-22 batch integrates PR #282 with Production's Uber baseline.
It uses the existing private Compose model, default operation locks, disposable
bounded builder, database backup tools and immutable image promotion. No new
release lifecycle or hosting system is introduced.

1. Fetch/merge/review/test the final source and stage a clean exact-SHA checkout
   under `/srv/restaurant-pos/staging/releases/<sha>` using the existing bare repo.
2. `python3 deployment/cloud/pos-production-removes/deploy.py staging <sha>`
   builds both application images once, verifies provenance and deploys them using
   the actual runtime model. Staging remains sandbox and Store 18 remains MOCK.
3. `python3 deployment/cloud/pos-production-removes/acceptance.py staging <sha> --apply`
   reconciles only Store 18, checks exact selections and replay, validates/previews
   published rules and retains `acceptance.json` with exact accepted image IDs.
4. `python3 deployment/cloud/pos-production-removes/deploy.py production <sha>`
   requires that acceptance and promotes the same two images. A fresh readable
   custom-format backup and scoped three-Store backup precede app mutation/Flyway.
   Production V37 advances additively to V38; no V39 is needed.
5. `python3 deployment/cloud/pos-production-removes/acceptance.py production <sha> --apply`
   verifies real Store IDs/codes/Organization/kind, performs fresh read-only dry-run,
   then reconciles each of Stores 1/2/3 in a separate fingerprint-guarded transaction.
   Full per-option before/after, links and replay reports remain private per release.
   Existing published aliases already contain all 18 canonical pairs: verify their
   proposal is identical, do not save/publish an unnecessary revision.

Only shared Remove masters, same-Store assignment FKs, original ALLOW/DENY rules,
menu revision and atomic audit entries may change. Never call generic reconciliation
for this data batch: it could proactively create unrequested nonbaseline masters.
No order/Print Job/hardware/device/Uber write endpoint exists in acceptance.
Read identity uses the reviewed short-lived numeric Owner approach; no available
Production private password means password-login acceptance is not claimed.

The four old Production PRINTING jobs (1176/3169/9226/20391) and three Staging
jobs (200/205/207) are preserved byte-for-byte. New active/pending queues or
unexpected configuration/provenance/data drift fail closed.

Application failure restores only the previous exact app model and verifies
health; the additive schema remains. Retain the previous image pair, private
`rollback.private.json`, both preflight and release backups, TOC, source model,
reports and scoped configuration. A Store reconciliation invariant/audit failure
rolls back that Store transaction before any next Store. Never automatically
restore a full database over later business orders or delete migration records.


For a reviewed acceptance-only correction, `acceptance.py` accepts
`--script-sha <full-clean-source-sha>` while its positional SHA remains the
accepted application SHA. It proves no Backend/Frontend source difference before
using existing images. Compare Combo business fields independently of the shared
`menu_revision` metadata: normal baseline linking advances that revision without
changing Combo configuration. Retain failed-attempt and replay reports; do not
rebuild an unchanged application for a verification-script correction.
