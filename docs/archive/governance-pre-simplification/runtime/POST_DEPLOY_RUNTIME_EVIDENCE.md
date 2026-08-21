# Phase 3E Post-Deploy Runtime Evidence

## Scope and evidence boundary

- Status: `PHASE_3_COMPLETE`
- Next documentation gate: `PHASE_4_READY`
- Evidence completion date: 2026-07-27, America/Toronto
- Environment: `restaurant-prod`
- Evidence source for this report: restaurant operator's manual field-test
  confirmation supplied for Phase 3E.

This report closes the production field-test portion of Phase 3. It does not
replace the historical snapshots in
`CLOUD_RUNTIME_EVIDENCE.md`, `VERIFIED_RUNTIME_BASELINE.md`, or
`PRE_DOCUMENTATION_GAP_CLOSURE.md`.

All manual field-test results below are classified **OPERATOR_CONFIRMED**. No
Phase 3E screenshot, raw server log, order ID, print-job ID, or precise elapsed
time was supplied to this documentation task, so no result below is classified
as `MACHINE_VERIFIED`. No additional server, database, Android, print-job, or
network command was run while preparing this report.

## Operator-confirmed deployment state

| Runtime fact | Reported value | Evidence classification | Boundary |
|---|---|---|---|
| Server branch | `main` | OPERATOR_CONFIRMED | This confirms the reported current checkout, not formal release approval. |
| Server commit | `4667f3c` | OPERATOR_CONFIRMED | Short commit as supplied by the operator. |
| Deployment mode | HTTP | OPERATOR_CONFIRMED | HTTPS and certificate behavior are not evaluated here. |
| Compose services | `db`, `backend`, `nginx` | OPERATOR_CONFIRMED | This is not a new container inspection. |
| Flyway schema version | 7 | OPERATOR_CONFIRMED | No schema-history query was repeated in Phase 3E. |
| Applied migration | `V7__add_print_job_attention_acknowledgement.sql` | OPERATOR_CONFIRMED | No migration action was performed by this task. |
| Health endpoint | `/api/v1/system/health` returned HTTP 200 | OPERATOR_CONFIRMED | Endpoint result is supplied operational evidence, not a new probe. |
| Backup artifact | `deployment/cloud/backups/restaurant_pos_20260725_033648.dump`, approximately 812K, `BACKUP_OK` non-empty check | OPERATOR_CONFIRMED | Existence/non-empty status is not a restore rehearsal or integrity proof. |

No new Phase 3E server-log packet was supplied for this report. Therefore this
report has no separate `LOG_OBSERVED` test result beyond the incident note
recorded below.

## Manual compatibility and ordering tests

| Test | Result | Evidence classification | Notes |
|---|---|---|---|
| New APK login and workspace entry | Passed | OPERATOR_CONFIRMED | No account or device identifiers recorded. |
| New APK menu loading | Passed | OPERATOR_CONFIRMED | Does not assert a full menu-cache fault-injection test. |
| New APK creates and submits an order | Passed | OPERATOR_CONFIRMED | No order identifier is recorded. |
| Old APK connects to the current production server | Passed | OPERATOR_CONFIRMED | Version compatibility was exercised manually. |
| Old APK creates and submits an order | Passed | OPERATOR_CONFIRMED | No order identifier is recorded. |
| New and old APK complete actual order flows | Passed | OPERATOR_CONFIRMED | This is a field workflow result, not an automated contract matrix. |
| New and old APK compatibility with current backend, frontend, and Flyway V7 production state | Passed | OPERATOR_CONFIRMED | Compatibility is bounded to the exercised field flows. |

## Manual PAD_DIRECT printing and worker tests

| Test | Result | Evidence classification | Notes |
|---|---|---|---|
| GRAB physical print | Passed | OPERATOR_CONFIRMED | No print-job ID, payload, endpoint, or ticket image is recorded. |
| FRONTDESK_RECEIPT physical print | Passed | OPERATOR_CONFIRMED | Same evidence boundary. |
| HOT_KITCHEN physical print | Passed | OPERATOR_CONFIRMED | Same evidence boundary. |
| PAD_DIRECT Worker long-run observation | Completed without the previously observed unrecoverable stopped state | OPERATOR_CONFIRMED | Exact duration, polling interval, and job count were not supplied. |
| Worker polling during the observation | Continued polling; no repeated stopped-and-unrecoverable condition reported | OPERATOR_CONFIRMED | This does not replace future device telemetry monitoring. |

The tests confirm production behavior in the manually exercised scope. They do
not establish that every Pad, every printer endpoint, every error branch, or
every future job will behave identically.

## Incident retained for follow-up

### `INCIDENT_RECOVERED_ROOT_CAUSE_PENDING`: Orders page blank screen

Known facts retained from the field report:

- A new APK previously opened a blank Orders page that could not be exited
  normally.
- Server-side logs were reported to show a request for an old Orders JavaScript
  chunk. This is recorded as **LOG_OBSERVED** based on the supplied incident
  description; no raw log, timestamp, or URL is reproduced here.
- Clearing Android WebView/App cache recovered the screen.
- A permanent code-level cache/chunk invalidation fix has **not** been completed.

Classification: `INCIDENT_RECOVERED_ROOT_CAUSE_PENDING`.

This incident must not be described as permanently fixed, and cache clearing is
an operational recovery step rather than a root-cause correction.

## Explicitly not established by Phase 3E

- A complete database restore rehearsal.
- A formal production approval or release-signoff record.
- A permanent code-level repair for the Orders cache/chunk incident.
- Machine-verified long-run Worker metrics, raw print-job lifecycle evidence,
  or physical printer network diagnostics.

## Phase conclusion

Phase 3 is complete for the approved, manually tested restaurant-pilot scope.
The reported production state, cross-version ordering flows, three PAD_DIRECT
ticket modules, and long-run Worker observation are all
`OPERATOR_CONFIRMED`. Phase 4 may begin as controlled documentation governance
work, provided it preserves the pending and incident boundaries above.
