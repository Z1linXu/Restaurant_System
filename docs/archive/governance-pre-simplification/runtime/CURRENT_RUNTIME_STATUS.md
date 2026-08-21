# Current Runtime Status

## Status

- Phase status: `PHASE_3_COMPLETE`
- Documentation gate: `PHASE_4_READY`
- Environment label: `restaurant-prod`
- Snapshot date: 2026-07-27, America/Toronto
- Evidence source: Phase 3E operator confirmation, summarized in
  [POST_DEPLOY_RUNTIME_EVIDENCE.md](POST_DEPLOY_RUNTIME_EVIDENCE.md)

This page is the current operational status summary. It is not a deployment
manifest, secret store, formal release approval, or substitute for the
historical evidence snapshots listed below.

## Reported production state

| Item | Current reported state | Evidence classification |
|---|---|---|
| Server branch and commit | `main` at `4667f3c` | OPERATOR_CONFIRMED |
| Deployment mode | HTTP | OPERATOR_CONFIRMED |
| Compose services | `db`, `backend`, `nginx` | OPERATOR_CONFIRMED |
| Schema state | Flyway version 7; `V7__add_print_job_attention_acknowledgement.sql` | OPERATOR_CONFIRMED |
| Health endpoint | `/api/v1/system/health` returned HTTP 200 | OPERATOR_CONFIRMED |
| Backup artifact | Reported non-empty file at `deployment/cloud/backups/restaurant_pos_20260725_033648.dump`, approximately 812K | OPERATOR_CONFIRMED |
| Print mode and field flow | PAD_DIRECT field printing passed for GRAB, FRONTDESK_RECEIPT, and HOT_KITCHEN | OPERATOR_CONFIRMED |
| APK compatibility | New and old APK flows were manually exercised successfully | OPERATOR_CONFIRMED |

## Pilot health

- New APK login, menu loading, order creation, and order submission: passed in
  the Phase 3E field test.
- Old APK continued to connect and submit orders against the reported current
  production environment.
- PAD_DIRECT Worker completed a long-run field observation without a recurrence
  of the stopped-and-unrecoverable symptom.
- No formal production approval record or database restore rehearsal is
  established by this status page.

## Open incident and evidence limits

- `INCIDENT_RECOVERED_ROOT_CAUSE_PENDING`: the Android Orders page previously
  loaded a blank screen after a request for an old JavaScript chunk. Clearing
  Android WebView/App cache recovered the incident. A permanent code-level fix
  remains pending.
- The backup is only reported as present and non-empty. Recoverability remains
  unverified until a separately approved restore rehearsal is recorded.
- The Phase 3E results are `OPERATOR_CONFIRMED`; this page makes no
  `MACHINE_VERIFIED` claim.

## Historical evidence snapshots

The following reports remain immutable historical snapshots and must not be
overwritten to match this newer field result:

- [CLOUD_RUNTIME_EVIDENCE.md](CLOUD_RUNTIME_EVIDENCE.md)
- [VERIFIED_RUNTIME_BASELINE.md](VERIFIED_RUNTIME_BASELINE.md)
- [PRE_DOCUMENTATION_GAP_CLOSURE.md](PRE_DOCUMENTATION_GAP_CLOSURE.md)
- [ANDROID_RUNTIME_EVIDENCE.md](ANDROID_RUNTIME_EVIDENCE.md)

## Documentation authority

The operational mapping for customer-facing menu names and GRAB kitchen names is
maintained separately in
[FRONTDESK_GRAB_ITEM_NAME_RULES.md](../../../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md).
That document is an `OPERATIONAL_DISPLAY_RULE_SOURCE`; this status page does
not duplicate its menu-item table.
