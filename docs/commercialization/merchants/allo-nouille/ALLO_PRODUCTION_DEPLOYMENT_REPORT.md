# Allô controlled production release — 2026-11-12

**NOT DEPLOYED — PENDING_BROWSER_GATE — RELEASE NO-GO.**

Owner deployment authorization already covers this release to
<https://pos.allonouilles.com/>. No additional deployment approval is being
requested. The browser interaction gate remains incomplete because the ordinary
synthetic Finish confirmation blocks the supported browser controls; details
and completed cases are in [browser acceptance](HUMAN_LIKE_E2E_REPORT.md).
This report must not be used as a successful deployment certificate.

## Target and unchanged live version

Only `43.166.242.250` / `VM-0-6-ubuntu`, Compose project `allo-nouilles`, root
`/srv/allo-nouilles`. Existing Lanzhou Production and the unrelated dirty main
checkout were not deployment targets.

| Property | Actual live value before candidate activation |
| --- | --- |
| Backend/frontend source | `2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9` + authenticated WSS compatibility patch `915e4ff4d8a6a0698c54ae70d2567e0919c50a49a59e48bdd03fbf902321c6e0` |
| Backend image | `sha256:613327357035df7e16cbf50206891d7853dca02afd79b4bfbcbf8b13bb81e1d7` |
| Frontend image | `sha256:e3ae049a8fcaf77311dca3173b5f5761168334672a8571df2e8cb7520cb24cc0` |
| PostgreSQL image | `sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28` |
| Migration | V1–V41 success; candidate V42 has not run on Production |
| Database / user | `restaurant_allo_nouilles_test` / `allo_test`, existing independent physical production database |
| Tenant | Organization1 / Store1 / `ALLO_NOUILLES_TEST` |
| Printing | Enabled, Store `MOCK`, 0 paired devices |
| Menu | revision96; 32 numbered products +2 zero-price refills; approved screenshot prices/categories match |
| Business state | 10 completed orders, 0 active orders; 30 Jobs:25 PRINTED/4 CANCELLED/1 historical FAILED |
| Outbox | 23 MOCK_RENDERED/2 SKIPPED/4 POLICY_BLOCKED/1 historical FAILED; no PENDING/PROCESSING |
| Canonical Add-ons | 0 catalog rows; controlled reconciliation has not been applied |
| Combo | 0 configured fixed offers/components; no new commercial offer was invented |

The container identities and business/menu hashes remained unchanged after
candidate image staging. Current production health is checked separately in
the restricted `production-live-unchanged.json` /
`production-health-before-release.json`; it certifies the **old running
version**, not a post-deployment smoke of the candidate.

At09:10:11 UTC a final guarded read-only snapshot again matched all old
container IDs, business hashes, Store configuration and34 prices. A separate
configuration-only extraction included all188 option leaves and31 groups;
static review found0 reconciliation plan conflicts for13 paid codes/94 leaves,
including complete zh/en/fr/FK/parent/binding checks. This improves the earlier
aggregate preflight but is **not** a new-backend checked API dry-run/apply.
No production rows were imported into the synthetic QA database.

## Traceable candidate artifacts actually prepared

App source commit:
`e7dde0a6e27adf3be00baa9972fee06cdcc5482c` on
`codex/allo-production-improvements-20261112`.
Evidence/runbook-only follow-up commits do not change these sealed app artifacts.

- Backend JAR SHA256:
  `9430ee811525bd22bf525d479c864ac86c22d26c45bb93facee8e3f14c02d380`.
- Production frontend build explicitly sets this app SHA and namespace
  `allo-nouilles`; it does not contain the QA namespace.
- Existing merchant logo bytes were retained, SHA256
  `8a91f839aa9e614876fee5d312b06b48e55f00ee7ef36be62490fba52dd0cf94`,
  equal to the actual old frontend asset. This is an audited existing private
  asset input, not an untracked source-code override.
- Release archive SHA256:
  `1395236f03579715d62ee2a4c7f1433f684a36426e60c172f20d314e38baa3b5`.
  Manifest verifies59 payload files, committed Dockerfiles/Compose, JAR and dist.
- Candidate directory on the new host:
  `/srv/allo-nouilles/releases/e7dde0a6e27adf3be00baa9972fee06cdcc5482c`.
- Backend candidate image:
  `sha256:06438f7ebaf408a3b921b9cd00028b8b4854a35471bff9231be42a5a4ac3c591`.
- Frontend candidate image:
  `sha256:390cb0d0a6b565dbb58bbd9c98d30f3e9ae85e07dbdc2007a4592ba04132c81c`.

The final e7dde0a images were staged at2026-11-12 09:01:41 UTC. The earlier
c242548 staged-only candidate is superseded and must not be activated: the
human-browser update case exposed a one-cent receipt reconciliation issue,
fixed and re-reviewed in e7dde0a. Old Job18 retains its actual6.88 text; new
updates use the correct frozen order delta.

Both images were built on the target using the verified original exact base
images, `--pull=false --network=none`, and validated source/revision labels.
Old container IDs/images were checked before staging and retained afterward.
No `compose up`, active Compose/env replacement, migration, capability enable,
canonical reconciliation, order, reprint or price adjustment was performed.

## Completed release evidence

| Gate | Result and actual scope |
| --- | --- |
| Backend | PASS:191 Surefire XML files,1519 total =1402 passed +117 explicitly skipped,0 failures/errors |
| Frontend | PASS:84 files/481 tests; TypeScript and Vite builds |
| Database | PASS:V42 on fresh synthetic PostgreSQL; H2 transaction/locking/idempotency/audit rollback cases in backend suite |
| Five renderer groups | PASS:6 synthetic orders/12 saved snapshots; 2/3 are approved-item equivalents, not fixed Combo certification |
| Update receipt money | PASS:actual saved new Job19–28, positive/negative cent allocation, consecutive free refill, no duplicate update Jobs, all prior data unchanged; [evidence](UPDATE_RECEIPT_AMOUNT_REGRESSION.md) |
| Independent code review | PASS for e7dde0a after four initial P1 repairs and the update-amount regression; hardware remains NOT TESTED |
| Security | ACCEPT WITH LIMITATIONS; production npm audit0,7 moderate dev-only findings; no exhaustive backend SCA claim |
| Human-like browser | PARTIAL; new main workflows operated, remaining Finish/Print Center/other modules NOT RUN after browser control blocker |
| Hosted CI | NOT RUN / no repository `.github` workflow configured; actual local test/build results above are not relabelled remote CI |
| Production candidate smoke | NOT RUN, because candidate is not activated |

## Recovery backup actually restored

Fresh source snapshot at **2026-11-12 08:31:59 UTC** /03:31:59 America/Toronto:

`/srv/allo-nouilles/backups/release-20261112/PRODUCTION_RECOVERY_ONLY_20261112T083159Z.dump`

SHA256:
`55dd0ddf0e8a4e4f6a7f1ed22cd298d6e9501fc7d850de485f784da9f5ca0008`.

The custom dump is360648 bytes. The manifest's counts/hashes and dump share a
single exported repeatable-read snapshot. A restricted local copy was restored
at08:33:27 UTC into a separate **Unix-socket-only recovery cluster**, not QA.
All85 tables matched exact row counts/content hashes; indexes and constraints
were valid. That temporary recovery cluster was stopped and removed. Original
dump and verification JSON remain private. All85 source hashes also matched the
earlier07:16 backup; staging did not mutate source data.

Before a resumed activation, redo a current runtime/active-order/queue check and
fresh backup validation if intervening time or real business activity makes this
snapshot stale. The earlier verified file is a recovery reference, not permission
to overwrite new business records.

## Application rollback prepared, not executed

At09:03:07 UTC the exact old tuple was archived root-only at:

`/srv/allo-nouilles/releases/e7dde0a6e27adf3be00baa9972fee06cdcc5482c/rollback-baseline/`

Contains old `compose.yml`, `runtime.env`, `nginx.conf` and `baseline.json` with
file hashes and old image IDs. Resolved old Compose was parsed in memory and
checked without printing secrets. Old Compose SHA256
`2de5ecc5ce6c07c83674610bb63bedf731816001e61e6f5854e3015a3de6679f`
also equals the recorded Git baseline.

Rollback must restore **old Compose + old environment + old backend/frontend
images together**. New policy values are hardcoded Compose environment entries;
restoring only env or only the old JAR is not a valid rollback. Keep the live
database and additive V42/ledgers. Never restore this pre-release dump over
orders accepted after deployment. Existing WSS authentication repair must remain.

V42 adds a default-false flag and new preview/ledger tables; it does not rewrite
old schema/business rows. Old code already supports the V28 canonical FK shape.
After reconciliation it can read original prices/names, but its older Add-on
projection can omit French on **future** order snapshots, and old template/admin
edits can diverge. A temporary old-version rollback therefore requires pausing
Add-on/template editing and acknowledging French fallback. Exact old binary
startup against V42 and an actual live rollback were **not executed**; do not
claim a full rollback drill.

Before any old-binary rollback, pause new order writes and let the new binary
process all PENDING/PROCESSING outbox events, including new `update_*` monetary
intents; confirm no active Jobs. The old renderer ignores the new fields and
would calculate their tax under the old rule. Do not change/requeue intents or
clear history to satisfy this guard. Otherwise keep the application safely
stopped or forward-fix with the new version.

Only consider application rollback while Store remains MOCK and current queues
are controlled. If an Owner later selects PAD_DIRECT or there are claimed/
PRINTING Jobs, reassess ownership/provenance and use a forward fix where needed;
never silently replay or transform physical work.

## Remaining activation sequence

1. Recover browser input and complete the documented remaining synthetic UI
   cases; fix/retest any critical product fault and refresh independent review
   if code changes. Do not force a GO from the current partial result.
2. Reverify exact target/old runtime, no unsafe active orders/queues, artifact
   hashes, current source backup and rollback tuple.
3. Use committed candidate Compose with V42 and explicit Allô production opt-in,
   preserving DB/JWT/TLS/source metadata. Recreate only backend/frontend, wait
   for health; retain DB container/data and old images.
4. Keep Store mode MOCK. Explicitly enable only Store1/Org1's bulk-price
   capability under its exact identity; record this configuration change.
   Do not apply a price batch.
5. Owner-authorized checked Add-on dry-run must show expected13 groups/94 paid
   leaves, no conflicts, current revision and fingerprint. Apply those exact
   guards without `confirmed_prices`. Confirm all raw names/FR/prices/IDs and
   other94 physical leaves unchanged, catalog13, checked replay zero writes.
   Any conflict stops this step for investigation, not a guessed price choice.
6. Compare all34 base prices and historical order/item/option/Job/outbox hashes.
   Read-only website/login/admin/menu/category/Add-on/pricing/printer/policy/
   Frontdesk/history/health/WSS/log smoke. No test sale/reprint/price operation.
7. Record activation time, actual image/commit/Flyway and smoke results, then
   update this report and final status. None of these post-activation results is
   claimed by the present staged-only evidence.

Android Worker and physical output remain NOT TESTED. Dessert kitchen routing
default remains included until Owner chooses otherwise. Automatic off-host
backup synchronization and the remaining dev dependency maintenance are
separate non-blocking follow-ups.
