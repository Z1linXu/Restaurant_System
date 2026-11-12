# Allô controlled Production release — 2026-11-12

**DEPLOYED — PRODUCTION SMOKE PASS — EXPLICIT NON-BLOCKING TOOL COVERAGE LIMITS.**

Owner explicitly authorized completing browser acceptance and then deploying
Allô without repeated per-step confirmation. No deployment approval is missing.
The ordinary synthetic Finish native confirmation blocks supported browser
control; no new completed/table-release state is claimed. Independent review
classified the remaining Finish/Frontdesk-UI/library-full-Save gaps as
NOT_RUN_TOOL_LIMIT / NON_BLOCKING_COVERAGE_LIMIT under Owner§8.6/§11.1.
Finish/auth paths are unchanged and actual8FinishAPI proofs pass; library has
focused actual JPA/scoped-payload proof. This supersedes the earlier over-strict
interpretation that every unsupported browser action must block release.
See [actual browser results](HUMAN_LIKE_E2E_REPORT.md).

## Exact fixed batch, prepared and independently reviewed

| Property | Verified value |
| --- | --- |
| Source | `23f3e804713ad633f1d95f10d1b0cfebfcf31edf` |
| Branch | `codex/allo-production-improvements-20261112` |
| PR | [#310](https://github.com/Z1linXu/Restaurant_System/pull/310), ready for review, base `codex/allo-nouilles-delivery-20261103` at `1f9f1122efe7763015b49f1a6aa4ecbb33030dd8`; not merged into main |
| Fresh origin/main | `80e888fcdad74e98326fcbeaa5df14bc0d9baed6`; reference only, not Allô deployed source |
| New archive SHA-256 | `f12e133270db93bb913071c7e60bab4c3c04ad58014a5e37d961c4f93ff04fb1` |
| JAR SHA-256 | `d4b9473fce4d7bfaf35c6a5f1ace4e83bc5dc0ab24fe9c04a616c9187154f522` |
| Backend image | `sha256:edd2f53a0c2c64e9218c682015763f958dc87db8fa246447db6987cbcd5f5f9f` |
| Frontend image | `sha256:1e5094013596f889b498f9438a17d3ba3dd103fb10815240051f5e8afedbeaf2` |
| Compose SHA-256 | `6672b3277ab87b39da37d6313d8694b238599de29fb41f3c6635754a7bc94177` |
| Resolved candidate env SHA-256 | `e0f7e07543b16f2d3ba97eadea7f789ded8a628e7edd44fca4b37823e0604a2a`; private values never printed/committed |
| Frontend namespace | `allo-nouilles`; QA namespace absent from Production build |
| Migration | Reviewed additive V42, Store capability defaultsfalse; no price/order/history rewrite |
| Reviewed targeted capability SQL SHA-256 | `e9b5b41b301bc71991a4942ce2e168f4eec792392b69bedb0ac8f4e8f6f279c8` |
| Remote candidate | `/srv/allo-nouilles/releases/23f3e804713ad633f1d95f10d1b0cfebfcf31edf` |

Archive59file hashes, source labels, verified existing base images, brand asset
and JAR were checked locally and on43.166.242.250. Docker builds used
`--network=none --pull=false`. Production namespace is retained. Source is
pushed and reviewable; a PR on the separate Allô delivery line is not
claimed as a merge into main. Unrelated dirty main-checkout work was untouched.
The oldc242/e7candidate artifacts are superseded and must not be activated.
Documentation-only commits do not change/rebuild this exact application batch.

## Actual activation and post-deployment result

Activation began `2026-11-12T16:07:52Z` and completed
`2026-11-12T16:09:12.653166Z` (Toronto11:09). Only backend/frontend were
recreated. Exact image IDs match the sealed table above; running JAR SHA matches
`d4b9473f…4f522`. Backendcontainer`c10fe14222d5e0224e58777776315d39bda5c109f168e6794477122382e60b81`,
frontendcontainer`28d00cc47a186900c8230c1b41b2e59b7612cc12767543d86047359829350f77`.
DBcontainer and its `2026-11-08T07:51:56.8264236Z` start time are unchanged.
FlywayV42 successful; exact Store1 capability enabled without a price batch.

Actual checked canonicalAPI dry-run returned13groups/94leaves/0conflicts at
menu_revision96/fingerprint`f3279421df19dddd0ea7548d86bf77146db43f18475b5088b83d6033fcfd9f32`.
Apply used those fresh guards and returnedrevision97/fingerprint
`2ffb06597d681dbb7a4f267593cc89d67814a57e49896ef3dfc29ebdad8f125b`.
Replay used the apply-returned guards and produced0groups/0leaves, unchanged
revision/fingerprint/configuration/audit. Apply produced exactly one reconcile
audit.13canonicalAdd-ons now serve94linked leaves. All94core fields including
French and price retained; the other94options remain full-row identical.
All34menu_items, groups, printers/assignments and historical business hashes
remain identical to pre-deploy snapshots.10completedorders/30Jobs/30outboxrows
retain their existing statuses, with0active/pending work. No historicalFAILED
record was cleared or automatically replayed.

At16:11:46UTC authenticated readonly smoke passed Owner and Manager login,
Storecontext,34itemcatalog,13Add-ons,canonical categoryGET,capabilityflag,
printingMOCK/0devices. Runtimepolicy reportsproduction/build23f3 and
DISABLED,MOCK,PAD_DIRECT; REAL absent. AnonymousAPI401/crossStore403; WSS
anonymous/invalid/crossStore/wildcard/client-publish denied, ownStore
CONNECTED/SUBSCRIBED/DISCONNECTED. No test order/reprint/pricebatch/pairing.

At16:12:35UTC publicHTTPS/TLS/healthUP passed. RootHTML,frontdeskfallbackHTML
and6observedstaticassets exactly match the sealed bundle; brandlogo unchanged.
No post-deploy browser visual pass is claimed because the IAB confirmation
still blocks supported control. The final readonly smoke required one helper
retry: initial nonexistent categoryGET returned500; the source-confirmed actual
`GET /api/v1/admin/platform/menu/categories` returned200. No application code,
artifact,restart or business repair was needed for this smoke-path typo.

## Verified pre-deployment baseline

Target only `43.166.242.250` / `VM-0-6-ubuntu`, Composeproject`allo-nouilles`,
root`/srv/allo-nouilles`, canonical`https://pos.allonouilles.com`.
Lanzhou Production and old shared Test/Staging are outside scope.

At15:47:43UTC guarded read-only audit still matched prior container identities,
start times and full business hashes:

| Property | Actual live value |
| --- | --- |
| Backend/frontend | `2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9` + authenticated WSS patch `915e4ff4d8a6a0698c54ae70d2567e0919c50a49a59e48bdd03fbf902321c6e0` |
| Backend image | `sha256:613327357035df7e16cbf50206891d7853dca02afd79b4bfbcbf8b13bb81e1d7` |
| Frontend image | `sha256:e3ae049a8fcaf77311dca3173b5f5761168334672a8571df2e8cb7520cb24cc0` |
| DB image/container | `sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28` / `cbd0c6cf504d3b746f880879db3f0beb9da3c3ce212e35c8d2b0a21061820d37` |
| DB identity | `restaurant_allo_nouilles_test` / user`allo_test`; physical independent Production DB despite historical name |
| Tenant | Store1/Org1/code`ALLO_NOUILLES_TEST` |
| Flyway | V1–V41 success; V42 has not run in Production |
| Print | StoreMOCK/enabled,0devices,0printer endpoints |
| Menu | revision96;34baseprices unchanged,32numbered products +2free refills |
| Orders | 10completed/0active |
| Jobs | 30:25PRINTED/4CANCELLED/1historicalFAILED;0pending/claimed/printing |
| Outbox | 23MOCK_RENDERED/2SKIPPED/4POLICY_BLOCKED/1historicalFAILED;0pending/processing |
| Canonical Add-ons | 0; no Production reconcile/capability-enable/price-batch operation |

The prepared script's default **prepare** mode passed after resolving one
read-only table-name typo. It validates all artifact hashes, old runtime tuple,
DBmount/image, exact tenant/MOCK, queues/devices/endpoints, backup freshness,
resolved old/new Compose differences and secret preservation. Only the exact
backend/frontend images and reviewed policy/Flyway variables may differ.
DB service configuration compares equal. Candidate private files and images
were staged; active rootCompose/runtime.env were not replaced or restarted.

## Fresh recovery proof

- Dump: `/srv/allo-nouilles/backups/release-20261112/PRODUCTION_RECOVERY_ONLY_20261112T154832Z.dump`.
- SHA-256: `ad7dee0694447c566ecef239c19e453dad605d2b3d1918d0ce6e31a6bcd53eff`.
- 360648bytes,85tables; dump shares a consistent exported PostgreSQL snapshot
  with all table counts/full-row hashes.
-15:49:01UTC independent socket-only recovery restored all85tables, exact full
  row hashes/counts and valid constraints/indexes. Recovery cluster stopped and
  removed. Production recovery data was never used as synthetic QA data.
- Previous recovery artifacts remain protected. No live DB restore was run.

## Acceptance and review

- Backend1520total=1403passed+117skipped,0fail/error,192XML; focused44pass.
- Frontend unchanged runtime source481tests/84files; TypeScript/Vite final
  Production buildPASS. Production npm audit0;7moderate devlint findings deferred.
- Five renderer groups/6orders/12original snapshotsPASS; no fixed offers or
  single-roll portion invented. New monetarydelta renderer/HTTP proofsPASS.
- Actual two rounds34concurrent optionGET/healthPASS with originalpool4,
  full responses unchanged;20tables378rows unchanged. [Proof](MENU_OPTION_READ_CONCURRENCY.md).
- Actual browserPrintCenter, updatepreview, originalMOCKreprint, failedJob,
  assignmentpersist/restore, C6cardinality/takeout/history, longpreviewscroll,
  canonicalavailability/station/displaydraft and Combo loadPASS. Finish and
  remaining role/library confirmation cases remain unobserved.
- [Agent6](INDEPENDENT_REVIEW.md) passed source23f3 and the final exact batch /
  activation script. Its P2 capabilitySQL identity guard was fixed: one
  transaction locks exactStore1/Org1/code/MOCK, rejects missing target and
  asserts one policy row changed. Final pre-restart guard rechecks MOCK,
 0devices/endpoints and no active/pending work. No automatic oldbinaryrollback.
- Android Worker/actual Pad/codepage/paper/physical printing **NOT TESTED**.

## Executed release procedure

1. Repeat instant exact target/runtime/queue/backup guards. If backup is stale
   or current data/identity differs, refresh appropriate evidence before change.
2. Replace only reviewed rootCompose and runtime.env from the fixed batch,
   preserving DB/JWT/TLS/private metadata. Recreate only backend then frontend
   with `--no-deps`; health-check exact images. DBcontainer/starttime must remain.
3. Flyway runs only additiveV42. Execute the reviewed capabilitySQL for exact
   Store1/Org1/code/MOCK inside one lock transaction with affected-row assertion.
   No category preview/apply price adjustment is permitted in Production smoke.
4. AuthenticatedOwner API `POST /api/v1/admin/menu/addons/reconcile` dry-run
   `{store_id:1,dry_run:true}`: require0conflicts,13groups/94leaves and fresh
   menu_revision/fingerprint. Apply those exact fresh guards, no confirmed_prices.
   Replay with the apply-returned guards; require0groups/0leaves and no new
   revision/audit writes. Static09:10full-config check is not actual API proof.
5. Verify34baseprices; existing orders/items/options/jobs/outbox unchanged.
   For94linked leaves preserve ID/item/code/zh/en/fr/price/default/sort/parent/
   templatebinding; allowed linkage/group/eligibility/timestamp updates only.
   Other94physical leaves retain full rows. Do not assert all188 full rows equal.
6. Read-only HTTPS/login/menu/categories/addons/capabilities/printing/history/
   frontdesk/WSS/health/logsmoke; no Production test sale/reprint/pairing. Keep
   actualStoreMOCK until separately authorized real hardware is available.

## Matching rollback tuple, prepared not executed

`/srv/allo-nouilles/releases/23f3e804713ad633f1d95f10d1b0cfebfcf31edf/rollback-baseline` stores root-only oldCompose/runtime.env/nginx and image IDs.
OldComposeSHA`2de5ecc5ce6c07c83674610bb63bedf731816001e61e6f5854e3015a3de6679f`. Restore **oldCompose +oldenv +both
oldimages together**, retaining additiveV42 and current data; do not run an old
DBdump over new business. No oldbinary+V42 live rollback rehearsal is claimed.

Before any oldbinary rollback pause writes and use the new binary to drain
new `update_subtotal/update_total` monetary outbox intents and active jobs.
Unmaterialized failed monetary intents must not be requeued through an old
renderer. If this cannot be proven, keep a safe stop/forwardfix boundary.
Preserve source-key idempotency and queues; never clear/replay them blindly.
During temporary oldbinary use pause Add-on/templateediting because future
French Add-on snapshots can fall back to English in old code.

## Docker / Disk Hygiene

| Metric | Before | After |
| --- | --- | --- |
| Disk | 14%,7.9GBused,49GBavailable | 15%,8.1GBused,49GBavailable |
| BuildCache | 1.046GB,10.18MBreclaimable | 1.162GB,12.72MBreclaimable |
| Images | 11total/3active | 13total/3active |

CleanupPerformed:none; ReclaimedSpace:0. Healthy limits did not require cleanup.
ProtectedArtifacts: current23f3app pair, previous2b44+WSS rollbackpair, recovery
backups, DBmount/privateconfig and evidence. All3Allôcontainers healthy;
DBcontainer unchanged. StagingHealth:not touched/recertified by this task.
ProductionMutation:only reviewed Allôapp activation,V42,Store1capability and
13/94canonicalmetadata; no other environment/runtime mutation.

## Product repair / tooling and governance separation

PRODUCT REPAIR: six features in c242, monetarydelta repair ine7, one readOnly
transaction boundary in23f3 with meaningful behavior regression. All serve the
original Allô outcome. No framework/globalOSIV/pool expansion was introduced.
TOOLING / GOVERNANCE: reused existing Compose, backup/recovery and HTTP/WSS
paths; required fixed-batch guards/one-time orchestration and concise evidence.
Two helper-only corrections were bounded (printertable name during prepare;
categoryGET path during readonlysmoke); no application rebuild/redeploy followed.
Current execution authority is only CURRENT_STATE.yml. Deployment is complete;
no main merge, real hardware pairing/printing or nextPhase is implied.

## Later QA confirmation evidence

At18:25UTC Owner accepted the pending synthetic QA native Finish confirmation.
Actual browser readback verifiedTEST1completed,4idle/0occupiedtables and unchanged
CAD36.17history. This closes Finish's browser gap asPASS_OWNER_ASSISTED; only
Frontdesk-roleUIlogin and LibraryfullSave retain their documented limits.
No new code/artifact, Production access/redeploy or migration was performed.
[Supplemental proof](evidence/qa-finish-owner-assisted.json).
