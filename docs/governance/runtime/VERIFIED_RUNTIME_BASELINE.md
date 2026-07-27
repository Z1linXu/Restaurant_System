# Phase 3 Verified Runtime Baseline

## Scope and verification date

- Verification date: 2026-07-24, America/Toronto (EDT)
- Scope: read-only synthesis of the existing Phase 3A local baseline,
  Phase 3B cloud evidence, and Phase 3C evidence for one representative
  Android Pad
- Evidence sources:
  - LOCAL_REPOSITORY_BASELINE.md
  - CLOUD_RUNTIME_EVIDENCE.md
  - ANDROID_RUNTIME_EVIDENCE.md
  - AUTHORITY_MATRIX.md

No new server, database, HTTP, Docker, SSH, or ADB command was used to create
this synthesis.

Classification values in this report are limited to:

- VERIFIED_MATCH
- VERIFIED_DIFFERENCE
- RUNTIME_EVIDENCE_PENDING
- NOT_RUN_BY_POLICY
- NOT_APPLICABLE

## Repository and deployment commits

| Item | Observed value | Classification |
|---|---|---|
| Local governance baseline commit | ed5f173273208429d301233da128a7626e69cd15 | VERIFIED_MATCH |
| Server deployment commit | 3d7ad88e1ec4c6d11a05aa2fbada7abaa889e611 | VERIFIED_MATCH for the observed server checkout |
| Server branch | main | VERIFIED_MATCH |
| Local governance commit equals server deployment commit | They are different commits | VERIFIED_DIFFERENCE |
| Intended approved release provenance | Not established by Phase 3 evidence | RUNTIME_EVIDENCE_PENDING |

The local governance baseline is not proof of the application version deployed to
the server.

## Server containers

| Service | Container | Status | Health | Classification |
|---|---|---|---|---|
| db | cloud-db-1 | running | healthy | VERIFIED_MATCH |
| backend | cloud-backend-1 | running | NO_HEALTHCHECK | VERIFIED_MATCH for running state |
| nginx | cloud-nginx-1 | running | NO_HEALTHCHECK | VERIFIED_MATCH for running state |

The expected Compose service structure was db, backend, and nginx. Backend and
nginx did not report healthchecks, so application health beyond the observed
HTTP endpoint remains RUNTIME_EVIDENCE_PENDING.

## Backend profile and database schema

| Item | Observed value | Classification |
|---|---|---|
| Explicit Spring profile | cloud | VERIFIED_MATCH |
| Flyway history in the observed database | V1 through V6 successful | VERIFIED_MATCH |
| Migration file set in the server checkout | V1 through V6 | VERIFIED_MATCH |
| V7 in the server checkout | absent | VERIFIED_DIFFERENCE relative to the local governance baseline |
| V7 in the observed database history | absent | VERIFIED_DIFFERENCE relative to the local governance baseline |
| Whether V7 is required for the intended release | Not established | RUNTIME_EVIDENCE_PENDING |
| Final effective Flyway/JPA settings | Not established from unset environment overrides | RUNTIME_EVIDENCE_PENDING |

The database V1-V6 history matches the migration file set in server commit
3d7ad88 within the checked-in migration scope. This does not prove that the
database is complete for any future release, and no migration was executed.

## Printing baseline

The observed runtime store was Store 1:

| Item | Observed value | Classification |
|---|---|---|
| Store status | active | VERIFIED_MATCH |
| Printing enabled | true | VERIFIED_MATCH |
| Printing mode | PAD_DIRECT | VERIFIED_MATCH |

Module assignment:

| Module | Assigned printer | Assignment enabled | Module font size | Copies | Classification |
|---|---|---|---|---:|---|
| FRONTDESK_RECEIPT | Printer ID 2, Main Printer | true | SMALL | 1 | VERIFIED_MATCH |
| GRAB | Printer ID 3, Grab Printer | true | MEDIUM | 1 | VERIFIED_MATCH |
| HOT_KITCHEN | Printer ID 4, Fired Printer | true | LARGE | 1 | VERIFIED_MATCH |

Four enabled ESC_POS_TCP printer configurations were observed. All used 80mm,
port 9100, and GBK. Printer endpoint values were redacted.

The following remain RUNTIME_EVIDENCE_PENDING:

- reachability of each assigned printer from each Pad
- physical print output
- whether physical output applies the configured module font size

## Android Pad baseline

The cloud evidence recorded seven active Store 1 Android device rows. The Phase
3C inspection covered only one representative Pad.

| Item | Observed value | Classification |
|---|---|---|
| Representative device label | adb-R5GL serial prefix only | VERIFIED_MATCH |
| Package | com.restaurant.pad | VERIFIED_MATCH |
| versionCode | 2 | VERIFIED_MATCH against local project metadata |
| versionName | 0.2.0-offline-pr7 | VERIFIED_MATCH against local project metadata |
| firstInstallTime | 2026-07-03 21:47:56 | VERIFIED_MATCH for observed package |
| lastUpdateTime | 2026-07-24 14:39:30 | VERIFIED_MATCH for observed package |
| Server registered Pad count | 7 active Android rows observed in Store 1 | VERIFIED_MATCH |
| Installed APK version for all registered Pads | Not established | RUNTIME_EVIDENCE_PENDING |
| Exact server device row for representative Pad | Not established because Device ID was redacted | RUNTIME_EVIDENCE_PENDING |

Only one representative Pad was verified. The second and third Pads were not
checked.

## Representative Pad Worker short observation

The Pad process was running and MainActivity was top resumed. The screen showed
the frontdesk table workspace, not the Local Control Panel.

Observed Worker sequence:

- Poll started at 14:53:27.
- Pending result count was 0.
- Poll duration was 42 ms.
- Next poll was scheduled after 4000 ms with recovery=false.
- Poll started at 14:53:31.
- Pending result count was 0.
- Poll duration was 37 ms.
- Next poll was scheduled after 4000 ms with recovery=false.

| Worker item | Classification |
|---|---|
| Short-interval active polling | VERIFIED_MATCH |
| Short-interval queue result count 0 | VERIFIED_MATCH |
| Short-interval next poll scheduling | VERIFIED_MATCH |
| Short-interval recovery=false | VERIFIED_MATCH |
| Auto Print preference | NOT_RUN_BY_POLICY |
| Pairing panel status | RUNTIME_EVIDENCE_PENDING |
| WebView mode | RUNTIME_EVIDENCE_PENDING |
| Long-run Worker health | RUNTIME_EVIDENCE_PENDING |
| Watchdog state | RUNTIME_EVIDENCE_PENDING |
| Last start/stop reason | RUNTIME_EVIDENCE_PENDING |
| Last error outside captured interval | RUNTIME_EVIDENCE_PENDING |

The short observation proves that the representative Pad was polling during the
captured interval. It does not prove continuous foreground recovery, long-run
polling, or successful consumption of future jobs.

## Verified facts

- The observed server checkout is on main at commit
  3d7ad88e1ec4c6d11a05aa2fbada7abaa889e611.
- The expected db/backend/nginx containers were running; db was healthy.
- The backend container explicitly used the cloud Spring profile.
- The observed database contained successful Flyway V1-V6 history.
- The server checkout contained the same V1-V6 migration file set.
- Store 1 was active with PAD_DIRECT printing enabled.
- GRAB, FRONTDESK_RECEIPT, and HOT_KITCHEN had enabled module assignments.
- Seven active Android device rows existed for Store 1 at the cloud evidence
  collection time.
- The representative Pad package and version matched the local Android project
  metadata.
- The representative Pad was actively polling the pending queue during the
  captured short interval.

## Confirmed differences

- The local governance baseline commit differs from the server deployment
  commit.
- V7 exists in the local governance baseline but was absent from the server
  checkout and observed database history.
- Backend and nginx containers had no reported healthchecks.
- Server-side registered Pad rows had unknown app_version values.
- Server build metadata files were not available at their expected Nginx paths;
  this was not classified as a frontend failure.
- The default remote deployment/cloud/backups directory was not present.

## Pending runtime evidence

- Intended release approval and deployment provenance.
- Final effective Spring, feature flag, auth, CORS, and WebSocket configuration.
- HTTPS and complete WebSocket/STOMP behavior.
- Device pairing status and exact server device correlation for the representative
  Pad.
- Auto Print preference as displayed in the Local Control Panel.
- Bundled buildVersion, generatedAt, offlineDatabaseSchemaVersion, and asset
  manifest hash on the installed Pad.
- Long-run Worker recovery, lifecycle behavior, watchdog status, and error
  history.
- Printer endpoint reachability and physical output.
- Backup location outside the missing default directory and restore readiness.

## NOT_RUN_BY_POLICY

No approved existing PAD_DIRECT test Job ID was supplied. Therefore the
following were not performed:

- broad Print Job or print_job_attempts queries
- order submission
- reprint creation
- new Print Job creation
- Claim
- Start Print
- Payload Fetch
- Complete
- Fail
- Release
- printer connection test
- test print

No database migration, repair, clean, or write operation was performed.

## Main runtime risks

1. The server and local governance commits differ, and intended release
   provenance is still pending.
2. Only one of seven registered Pads was inspected.
3. The representative Pad Worker was observed for only a short interval.
4. Pairing and Auto Print preference were not visible because the Control Panel
   was not open.
5. Printer reachability and physical printing were not tested.
6. PAD_DIRECT job state and attempt behavior were not inspected by policy.
7. The default backup directory was absent and restore readiness is unknown.
8. Backend and nginx lack container healthchecks.

## Readiness for documentation unification

The Phase 3 evidence is sufficient to create this factual runtime baseline, but
it is not sufficient to treat all runtime claims as verified. The following
items remain RUNTIME_EVIDENCE_PENDING or NOT_RUN_BY_POLICY:

- representative Pad pairing and preference state
- installed asset provenance
- long-run Worker behavior
- physical printing
- PAD_DIRECT job processing
- backup and restore readiness
- release provenance

Therefore this baseline is **not sufficient to begin automatic documentation
unification or correction**. Any future documentation work must use this report
as a bounded evidence input and preserve the pending classifications.

## Non-modification statement

This report is a synthesis of previously collected evidence only. No server,
database, Android device, application code, configuration, migration, deployment
file, or existing governance report was modified. No remote, Docker, HTTP,
database, or ADB command was executed for this report.
