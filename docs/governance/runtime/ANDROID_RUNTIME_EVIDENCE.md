# Phase 3C Android Runtime Evidence

## Scope and provenance

- Environment label: one representative Android Pad
- Device label: Serial prefix adb-R5GL
- Verification timestamp: 2026-07-24 14:53 EDT
- Verification mode: owner-approved, read-only Android runtime inspection
- Package: com.restaurant.pad
- Installed versionCode: 2
- Installed versionName: 0.2.0-offline-pr7
- First install time: 2026-07-03 21:47:56
- Last update time: 2026-07-24 14:39:30
- Local repository version checked in Phase 3C: versionCode 2,
  versionName 0.2.0-offline-pr7

The package identity and version match the local Android project metadata
observed during the first Phase 3C batch.

This report covers one device only. No second or third Pad was inspected.

## Classification vocabulary

- VERIFIED_MATCH: The observed device fact matches the checked local or cloud
  evidence within the stated scope.
- VERIFIED_DIFFERENCE: The observed device fact differs from the checked local
  or cloud evidence.
- RUNTIME_EVIDENCE_PENDING: The allowed read-only evidence did not establish
  the fact.
- NOT_APPLICABLE: The fact is not applicable to this observation.
- NOT_RUN_BY_POLICY: The check was intentionally not run by policy.

## APK identity

| Check | Evidence | Classification |
|---|---|---|
| Package name | com.restaurant.pad | VERIFIED_MATCH |
| versionCode | 2, matching local Android project metadata | VERIFIED_MATCH |
| versionName | 0.2.0-offline-pr7, matching local Android project metadata | VERIFIED_MATCH |
| Installed | Package Manager reported installed=true | VERIFIED_MATCH |
| Stopped | Package Manager reported stopped=false | VERIFIED_MATCH |
| Enabled | Package Manager reported enabled=0, the default state; no explicit disabled state was observed | RUNTIME_EVIDENCE_PENDING |
| Signing certificate | Not collected in this phase | NOT_RUN_BY_POLICY |

The package manager fields were filtered before output. No complete
Package Manager dump was recorded.

## Current application and WebView observation

| Check | Evidence | Classification |
|---|---|---|
| App process | com.restaurant.pad had a live process during the check | VERIFIED_MATCH |
| Current Activity | com.restaurant.pad/.MainActivity was top resumed | VERIFIED_MATCH |
| Current window focus | The filtered window query did not return the package as focused | RUNTIME_EVIDENCE_PENDING |
| Current screen | The screen showed the frontdesk table workspace | VERIFIED_MATCH |
| Store workspace | The visible UI was the table board with available/occupied tables | VERIFIED_MATCH |
| WebView mode | Local Control Panel was not visible; Bundled Assets versus Local Preview was not established | RUNTIME_EVIDENCE_PENDING |
| Current WebView URL | Not read by policy | NOT_RUN_BY_POLICY |

The current screen was inspected passively. No tap, navigation, refresh,
configuration change, or Control Panel action was performed.

## Pairing and Local Control Panel state

The current screen was the frontdesk table page rather than the Local Control
Panel. The following fields were therefore not visible in the approved
read-only UI observation:

| Field | Result | Classification |
|---|---|---|
| Paired / Unpaired | Not visible | RUNTIME_EVIDENCE_PENDING |
| Device ID | Not recorded; worker log identifiers were redacted | NOT_RUN_BY_POLICY |
| Store ID | The worker request path contained store 1, but the pairing panel was not visible | RUNTIME_EVIDENCE_PENDING |
| Device name | Not visible and not recorded | NOT_RUN_BY_POLICY |
| Registered at | Not visible | RUNTIME_EVIDENCE_PENDING |
| App version in pairing panel | Not visible; installed package version is recorded above | RUNTIME_EVIDENCE_PENDING |
| Platform | Not visible in the panel | RUNTIME_EVIDENCE_PENDING |
| Auto Print Enabled preference | Not read from application storage; panel was not visible | NOT_RUN_BY_POLICY |
| Token last four | Not recorded | NOT_RUN_BY_POLICY |

The worker was able to issue a device-authenticated pending-job request during
the observed interval. This is evidence of an active runtime request, but it
does not expose or prove the stored credential value and does not replace the
pairing-panel verification.

## Worker runtime evidence

The filtered RestaurantPadWorker log contained the following safe observations:

| Worker check | Evidence | Classification |
|---|---|---|
| Worker polling | Poll Started and poll_start were observed | VERIFIED_MATCH |
| Poll result | Result count=0 was observed twice | VERIFIED_MATCH |
| Poll duration | 42 ms and 37 ms were observed | VERIFIED_MATCH |
| Next poll | delayMs=4000 was scheduled after both polls | VERIFIED_MATCH |
| Recovery | The scheduled entries reported recovery=false | VERIFIED_MATCH |
| Worker stopped | No stop or error-stop marker was observed in the captured interval | RUNTIME_EVIDENCE_PENDING |
| Worker recovering | No recovering marker was observed in the captured interval | RUNTIME_EVIDENCE_PENDING |
| Last start reason | Not present in the captured log interval | RUNTIME_EVIDENCE_PENDING |
| Last stop reason | Not present in the captured log interval | RUNTIME_EVIDENCE_PENDING |
| Last error | No error marker was observed in the captured interval | RUNTIME_EVIDENCE_PENDING |
| Watchdog state | Not present in the captured log interval | RUNTIME_EVIDENCE_PENDING |
| Current job | No job was picked during the captured interval | NOT_APPLICABLE |
| Queue result | The pending queue returned zero jobs during both observed polls | VERIFIED_MATCH |

The observed sequence was:

1. Poll started at 14:53:27.
2. Pending queue returned zero jobs.
3. Poll ended after 42 ms.
4. A 4000 ms next poll was scheduled with recovery=false.
5. Poll started at 14:53:31.
6. Pending queue returned zero jobs.
7. Poll ended after 37 ms.
8. A 4000 ms next poll was scheduled with recovery=false.

This proves active polling during the captured interval. It does not prove
long-run worker health outside that interval.

No raw device identifiers, customer data, order details, printer endpoint,
device token, payload, or authorization header was retained in the report.

## Bundled assets and build metadata

| Check | Result | Classification |
|---|---|---|
| Device buildVersion | Not visible in the current screen and not read from app storage | RUNTIME_EVIDENCE_PENDING |
| Device generatedAt | Not visible and not read | RUNTIME_EVIDENCE_PENDING |
| Device offlineDatabaseSchemaVersion | Not visible and not read | RUNTIME_EVIDENCE_PENDING |
| Device asset-manifest hash | Not read from APK/application storage | NOT_RUN_BY_POLICY |
| Comparison with Phase 3A metadata | Cannot be established from this batch | RUNTIME_EVIDENCE_PENDING |

The installed package version matches the local Android project metadata, but
this does not prove that the bundled frontend assets match the Phase 3A
metadata.

## Relationship to cloud evidence

The Phase 3B report recorded Store 1 as active with PAD_DIRECT printing enabled
and recorded seven active Android device rows. This Pad made a pending-job
request containing store 1 in the request path during the captured interval.

The Device ID was redacted and the pairing panel was not visible, so a specific
match between this Pad and one Phase 3B store_devices row is not established.
The following fields remain runtime-pending:

- exact server device row for this Pad
- server last_seen_at corresponding to this observation
- server app_version for this Pad
- current server-side device status
- current server-side worker or claim activity

## Unexecuted or unavailable checks

- No second or third Android Pad was inspected.
- No Local Control Panel was opened or navigated to.
- No Start Auto Print or Stop Auto Print action was performed.
- No Clear Pairing or re-pair action was performed.
- No SharedPreferences, WebView LocalStorage, Cookie, JWT, Refresh Token,
  Device Token, or Token Hash was read.
- No complete logcat was exported.
- No printer test or connection test was performed.
- No order, reprint, Print Job, claim, start-print, payload, complete, fail, or
  release operation was performed.
- No server, database, or application API was called by the verification
  procedure.

## Risks and remaining uncertainty

- The worker was polling successfully for the captured interval, but the
  sample does not establish long-run lifecycle recovery.
- Pairing and Auto Print preference are not visible because the current screen
  was the frontdesk table page.
- WebView mode and device bundled asset metadata remain unverified.
- The absence of a recent log error is not proof that no earlier error occurred.
- A zero pending result is only a queue observation, not evidence that future
  jobs will be consumed.
- Server device-row correlation remains unverified.

## Non-modification statement

This Phase 3C batch did not install, update, uninstall, clear, or modify the
Android application. It did not change Android settings, pairing, printer
configuration, Worker state, application data, server files, database rows, or
Print Jobs.

No existing governance report was modified. No application code, Android source,
configuration, migration, deployment file, or generated asset was modified.
No SSH command or server-side remote command was executed in this phase.
Only locally initiated, read-only ADB inspection commands were used.
No Phase 3C work for another device was started.

## Local completion checks

The local completion checks for this report were:

- git diff --check: passed with no output.
- git diff --stat: no output because this report is an untracked file and the
  default git diff does not include untracked files.
- git status --short: exactly
  ?? docs/governance/runtime/ANDROID_RUNTIME_EVIDENCE.md

Phase 3C verification for one representative Pad is formally complete. The
second and third Pads were not inspected. No print was triggered, no Android
device was modified, and no documentation unification or Alive Runtime
Planbook was started.
