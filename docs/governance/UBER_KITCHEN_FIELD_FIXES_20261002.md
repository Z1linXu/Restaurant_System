# Uber Kitchen Mirror Staging field repair — 2026-10-02

Scope: TEST Store `bd993244-5589-4b19-8f0d-dc2ba73d4273` → org1/Store1 `STG005_SRC_20260809_R01`. Owner authorized existing paired Android Pad + printer `192.168.12.19:9100`, raw fallback, footer, reviewed merge and Staging. No Production changes.

## Actual incident evidence

Read-only observation 2026-10-03T02:55Z–03:07Z. Database printer13 name is **Haha** (Owner calls it Hana), endpoint exactly `192.168.12.19:9100`; no rename performed. Device32 is ACTIVE Store1 Android, continuously reporting.

`NORMAL_HOT_JOB=NOT_FOUND`: no matching ordinary HOT/PAD_DIRECT record existed at observation. The observed successful ordinary jobs are GRAB201/203 and FRONTDESK202/204, printer13/device32. This proves the shared endpoint/device worked but does not fabricate the requested exact HOT A/B. `UBER_HOT_JOB=206,208`, both PENDING with zero claim/attempts and populated snapshots.

### FIELD_DIFF (all nulls retained)

The comparison below explicitly uses ordinary **GRAB203**, not an invented normal HOT record.

| Field | Normal GRAB 203 | Uber HOT 208 |
| --- | --- | --- |
| id | 203 | 208 |
| organization_id | 1 | 1 |
| store_id | 1 | 1 |
| order_id | 80 | 77 |
| order_update_batch_id | null | null |
| printer_id | 13 | 13 |
| module_code | GRAB | HOT_KITCHEN |
| receipt_type | GRAB | HOT_KITCHEN |
| execution_mode | PAD_DIRECT | PAD_DIRECT |
| status | PRINTED | PENDING |
| preferred_device_id | 31 | 32 |
| preferred_device_until | 2026-10-02T22:27:46.94684 | 2026-10-02T22:30:23.449447 |
| claimed_by_device_id | 32 | null |
| claimed_at | 2026-10-02T22:28:46.599456 | null |
| claim_expires_at | 2026-10-02T22:33:46.652904 | null |
| printed_by_device_id | 32 | null |
| client_attempt_token | PRESENT (redacted) | null |
| requested_by_user_id | null | 3 |
| dispatch_source_key | submit:80:GRAB | manual:1:45d967dc-cc98-41ae-81dd-c3c426a3771e |
| manual_request_hash | null | ad386b358d665b3ad8fa35a003e3032ee6c728c07162cdf9e682d1e6786d45bb |
| escpos_payload_base64_present | True | True |
| rendered_text_snapshot_present | True | True |
| rendered_text_snapshot_length | 194 | 243 |
| retry_count | 0 | 0 |
| max_retry_count | 3 | 3 |
| error_code | null | null |
| error_message | null | null |
| created_at | 2026-10-02T22:27:36.942052 | 2026-10-02T22:30:13.447722 |
| updated_at | 2026-10-02T22:28:46.768778 | 2026-10-02T22:30:13.44963 |
| last_attempt_at | 2026-10-02T22:28:46.768778 | null |
| ESC/POS decoded bytes | 152 | 187 |

### Real paired-device pending API

At 2026-10-03T03:05:27Z: DEVICE_ID=32, STORE_ID=1; authenticated GET `/api/v1/stores/1/printing/jobs/pending?limit=25` → HTTP200. PENDING_RESPONSE_JOB_IDS=`206,207,208,209,210,211`. UBER_HOT_JOB_IN_PENDING_RESPONSE=YES. Device credential remained in-memory and was not persisted/output.

### Worker-side boundary

Backend/access logs show device32 completed ordinary201–204, then claim/start/payload for205 at 22:28:49 Toronto. Its `/printing/jobs/205/payload` returns500 repeatedly at ~30s cadence; no later real worker poll/claim for206/208. Job205 is GRAB/printer2 with PRINTING/device32. HOT206/208 remain unclaimed; their 10s preference deadlines expired, so preference is not the exclusion.

Code proves payload validation throws409 for missing assigned host, but the shared global exception handler converted it to500; the native worker treats500 as recoverable and retains the same active job before TCP. **PRINT_PENDING_ROOT_CAUSE=payload protocol conflict converted to retryable500, leaving the shared worker on205 and starving later HOT jobs.** The known unbound GRAB configuration itself is not categorized as a defect.

Pad was not USB-accessible; Owner confirmed it cannot be connected now. Native worker state/generation/log lines and TCP output were **NOT OBSERVED**. Backend request evidence is not mislabeled native logs. UBER_JOB_RETURNED_TO_PAD=YES for the controlled authenticated read; actual worker did not poll after205. UBER_JOB_CLAIM_ATTEMPTED=NO for206/208, START_PRINT/PAYLOAD/COMPLETE=NOT_ATTEMPTED in backend evidence; TCP_CONNECT/TCP_WRITE=NOT_OBSERVED.

## Product repair

- Shared PAD controller preserves409; new invalid-endpoint jobs retain FAILED snapshots. Android advances after acknowledged definite pre-TCP configuration/payload failure only; no blind reprint or fake completion. APK version4 / 0.4.0-kitchen-field-fixes.
- V33 stores immutable raw identities/content in order-item snapshots. Known options stay semantic, unknown modifiers survive; unknown roots get GRAB-only holding tasks without invented item/BOM/HOT routing. Partial status and Today raw content; shared snapshot reprint.
- Existing GRAB/HOT footer treats kitchen mirrors as TAKEOUT. Financial isolation and no FRONTDESK receipt preserved.
- [Full 41-root inventory](UBER_ROOT_MAPPING_STATUS_20261002.md): 5 MAP /36 UNMAPPED, 226 rules unchanged. Chow root remains unconfirmed; no candidate persistence.

## Tooling / governance repair

Bounded `apply-field-fixes-once.py` is required because the prior one-shot deploy forbids any existing orders and assumes MOCK. The new exact-SHA helper preserves all runtime environment/endpoints, permits only observed in-flight200/205/207, backs up Staging, applies V33 and checks unchanged Production/DB container fingerprints. No generic deployment refactor. Contracts updated for the Owner's explicit fallback rule.

## Validation (local, not physical proof)

- Fresh merged application full backend verify: 860 tests, 0 failures/errors, 7 environment-gated skips; Uber PostgreSQL integration:48 tests,0 skips. Real local PostgreSQL V1→V33 migration/validation and integration tests enabled.
- Frontend238 tests, build, targeted lint PASS. Android worker policy unit tests PASS. Final credential scan1559 files /4 runtime-secret fingerprints PASS (values never output).
- Added integration coverage: ordinary/mirror HOT pending→claim→start→payload→complete; partial/mixed/raw/missing required option; no guessed HOT/no drop; immutable reprint after mapping mutation; nested raw parent context and no duplicate known add-on; malformed nested quantity and public ordinary fallback injection rejection; both footer markers/no Uber frontdesk. Existing DINE_IN/TAKEOUT renderer tests pass.

## Reviewed merge and exact-SHA Staging

- PR [#255](https://github.com/Z1linXu/Restaurant_System/pull/255), merge `75c82482e7c72de063973128ebb9ac0a98f83b5d`: Agent6 ACCEPT, P0/P1/P2=0 after closing duplicate known-addon fallback and nested parent-context findings.
- First deployment preflight stopped before mutation: device32 had completed206 and started207 during investigation. PR [#256](https://github.com/Z1linXu/Restaurant_System/pull/256), merge `500aacd991fddbc8f224174fc65db833e60d7848`, only adds the observed207 to the retained legacy in-flight allowlist. Separate Agent6 ACCEPT; no jobs reset/reclaimed.
- Backend/frontend running SHA `500aacd991fddbc8f224174fc65db833e60d7848`; source blobs identical to tested75c8248, reused application artifacts. No configured GitHub status checks; required local tests/reviews passed.
- Backend image `sha256:76b68f936c08a7997682fe05ae15a14761eeeffbbe15e649b6140362e620d654`; frontend image `sha256:5cbf1ad709ff42b5a3bafbef59f9bb1f8b7fbd2c44c2282d6a404b3e20a4ec5a`.
- Private backup `/srv/restaurant-pos/staging/uber-kitchen-field-fixes-20261002/staging-before.dump` (1,133,657 bytes; archive listing PASS). V32→V33 at03:21:58Z, backend started03:22:25Z. No destructive SQL.
- 03:28:52Z runtime acceptance PASS: public HTTPS health, login, Store1 context, frontdesk/printing/Today routes, Today API, V33, PAD_DIRECT,226 mapping rules/5 mapped roots. Production and both DB container continuity/fingerprints passed; Production remains SHA11996ef… / V28.

## Real Sandbox mixed order and physical proof

Official Test Store consumer checkout used **Uber Test** payment. New order `87673b75-8257-4d2c-93e1-091fb1f26bed` / **26BED** was placed03:26:59Z, observed CREATED/WAITING_FOR_ACCEPTANCE with no local order at03:27:15Z, then accepted through official Uber TEST Orders UI. No integration Accept API or Production order used.

At03:27:37Z acceptance was observed. One local order **82** (`UBER_EATS`, `EXTERNAL_PLATFORM`, amounts0); two root lines:

| Source | Local identity | Task route | Frozen kitchen content |
| --- | --- | --- | --- |
| Traditional Lanzhou Hand-pull Beef Noodle | traditional_beef_noodle / menu1 | NOODLE; known egg reaches existing HOT renderer | 不辣 / 大碗 / 三细 / 加煎蛋 / 走香菜 |
| Lanzhou Beef Chow Mein (Beef) | RAW_UBER_FALLBACK; menu/station null | RAW holding task; GRAB only | Original root, Mild Spicy ×1, Add Fried Egg ×1; stable IDs frozen |

Kitchen tasks147/148 and production tasks148/149 exist. Dispatch outbox199/200 DISPATCHED; exactly one automatic GRAB213 and HOT214. No FRONTDESK receipt. Today UI verified `KITCHEN_SENT_WITH_MAPPING_WARNINGS`, `PARTIALLY_MAPPED`, mapped Chinese content, full raw block and `UNMAPPED_ROUTE_REVIEW`; both reprint buttons available.

- GRAB213 includes the full raw root/options, known local semantics and **外卖**; SHA256 `e22b855eef2d7f3311d583a099cf6e937c460aaf2a107669bad8ee6a3f43c910`.
- HOT214 includes known local content and **外卖 / TAKEOUT**; excludes the unconfirmed chow root/options; SHA256 `a97890bc21cfd440219cd739ba557cdfdfbf8bea3651a4902827429b03aa6fcc`.
- Current resolved printer is13, endpoint192.168.12.19:9100, for both jobs. Legacy printer2 remains unbound; no printer/binding mutation by this repair.
- Device33 completed both jobs by03:29Z. Owner explicitly confirmed **real paper for both, both footer markers, and installed0.4.0 APK**. Therefore Uber physical test PASS; not inferred from API-only evidence.
- Concurrent human GRAB reprint215 has the exact same frozen snapshot hash as213 and PRINTED; it is a manual source, not a duplicate automatic dispatch. Mapping count unchanged226/5. Later mapping-change immutability separately covered by integration tests, not a live mapping mutation.
- APK versionCode4 / `0.4.0-kitchen-field-fixes`; SHA256 `43897ddfcffc0d8fdf0e5648a381bf6e6045321a49562271b803ec765dba1dfd`. Owner confirmed installation, while server device `app_version` remains `unknown`.

Sanitized GRAB raw section:

```text
Lanzhou Beef Chow Mein (Beef) x1
Mild Spicy x1
Add Fried Egg x1
【未映射 Uber 菜】
外卖
```

### Ordinary POS control

Prepared Store1 ordinary TAKEOUT draft83, pickup `FIELD-A-20261002`, same menu1/five local options as the known line of82. Server-origin submit correctly returned403 `PRINT_ORIGIN_INVALID` (valid paired Pad proof required); no bypass or copied device credential was used to submit. Owner Pad submission/physical confirmation pending. Existing ordinary successful jobs201–204 and local automated ordinary HOT claim/lifecycle tests are separate evidence, not substituted for this exact field HOT control.

### Retained legacy state and limits

- Predeployment accepted TEST orders40D26 and63A1E were parked under the old strict mapping rule. They remain `RELEASED_MAPPING_REQUIRED`; no automatic historical replay/backfill or fake mapping write was performed. The new postdeployment raw/mixed order26BED passed. Any deliberate legacy replay must first re-fetch current Uber state through a bounded recovery path; this repair does not claim those old rows recovered.
- Historical jobs200/205/207 already PRINTING remain untouched; possible prior paper is not guessed. Device33 later completed HOT208 and marked unbound209/212 FAILED/ANDROID_ASSIGNED_PRINTER_MISSING, then completed new213/214. No blind retry of ambiguous printing jobs.
- Native worker state/generation/TCP logs remain unavailable without USB. Actual backend job ownership/completion plus Owner paper confirmation prove this field result; exact native TCP call lines are NOT OBSERVED.
- Partial known-root/unknown-modifier and nested cases passed integration tests. The real order proved mixed known/raw roots; no fabricated Uber modifier was injected to inflate real Sandbox coverage.

## Docker / Disk Hygiene

Disk63%/22GB free →64%/21GB free. Build Cache14.64GB→14.75GB; reclaimable10.01GB unchanged. Above12GB cache-hygiene review performed; no cleanup because disk remains below70% and reviewed protected-cache eligibility was not broadened. Reclaimed0GB. Active Staging/Production images, DB volumes/containers, current and rollback releases, backup and private configuration retained. Both DBs healthy, applications Up; Production mutation/restart NO.

## Evidence-only closure

Final evidence/governance sync changes documentation only. Risk-based Agent6 exception: no executable/runtime/schema/config changes; prior required code/deployment reviews retained. No rebuild/redeploy for this documentation commit.
