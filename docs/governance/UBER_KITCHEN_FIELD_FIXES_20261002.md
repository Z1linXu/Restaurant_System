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

Bounded `apply-field-fixes-once.py` is required because the prior one-shot deploy forbids any existing orders and assumes MOCK. The new exact-SHA helper preserves all runtime environment/endpoints, permits only observed in-flight205/200, backs up Staging, applies V33 and checks unchanged Production/DB container fingerprints. No generic deployment refactor. Contracts updated for the Owner's explicit fallback rule.

## Validation (local, not physical proof)

- Full backend verify: 857 tests, 0 failures/errors, 7 environment-gated skips; final affected tests add2 =859 aggregate, no failures/errors. Real local PostgreSQL V1→V33 migration/validation and integration tests enabled.
- Frontend238 tests, build, targeted lint PASS. Android worker policy unit tests PASS. Credential scan1558 files /4 runtime-secret fingerprints PASS (values never output).
- Added integration coverage: ordinary/mirror HOT pending→claim→start→payload→complete; partial/mixed/raw/missing required option; no guessed HOT/no drop; immutable reprint after mapping mutation; malformed nested quantity and public ordinary fallback injection rejection; both footer markers/no Uber frontdesk. Existing DINE_IN/TAKEOUT renderer tests pass.

Runtime deployment/physical acceptance remains pending until recorded below. No physical PASS inferred from MockMvc, MOCK or device API simulation.
