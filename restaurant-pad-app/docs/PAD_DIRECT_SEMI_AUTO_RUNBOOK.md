# Pad Direct Safe Semi-Auto Printing Runbook

This runbook covers PR11D-5/6/7 pilot behavior. The Android Pad can process
`PAD_DIRECT` jobs while the app is open and semi-auto processing is enabled.

## What This Is

- A foreground-only Android pilot loop.
- Operator-controlled: `Start Auto Print / 开启自动处理打印任务` enables
  semi-auto processing, and `Stop Auto Print / 停止自动处理` disables it.
- A paired Pad may auto-start the same foreground worker when the app opens,
  returns to the foreground, or the Control Panel opens, but only if the saved
  user preference is enabled.
- One job at a time.
- Device-auth only: `X-Device-Id` and `X-Device-Token`.
- Safe flow per job:

```text
pending -> claim -> start-print -> payload -> assigned printer TCP print -> complete/fail
```

`start-print` sets the backend job status to `PRINTING` before Android opens the
local printer socket. This reduces duplicate-print risk because ordinary claim
calls do not reclaim active `PRINTING` jobs.

## What This Is Not

- Not an Android background service.
- Not a boot receiver or system daemon.
- Not a WebSocket worker.
- Not unattended production printing.
- Not encrypted device credential storage.
- Not force-release or mark-failed tooling.
- Not module/printer affinity.

## Setup

1. Pair the Android Pad from Web Print Center.
2. Set the store printing mode to `PAD_DIRECT`.
3. Configure local printer IP, port, and timeout in Android Local Control Panel.
4. Confirm `Test Printer Connection` succeeds.
5. Submit a test order and confirm Print Center shows `PENDING` jobs.
6. Open Local Control Panel.
7. Tap `Start Auto Print / 开启自动处理打印任务`.

## Expected Behavior

- The loop waits when no jobs are pending.
- It claims one pending job at a time.
- It marks the job `PRINTING`.
- It fetches the ESC/POS payload.
- It prints through Android native TCP to the payload `printer_host:printer_port`.
- It calls complete on success.
- It retries safe connect-phase failures briefly when no bytes were written.
- It calls fail when payload/native TCP fails after the safe retry window.
- It enters `RECOVERING` for temporary pending-poll/backend/API network errors
  before local TCP printing begins. Auto processing remains enabled and retries
  use 2s / 5s / 10s / 30s backoff.
- It still stops on device auth, TCP write/flush, complete-reporting, fail-
  reporting, and other high-risk errors where continuing could hide or duplicate
  a print.
- It stops when the app leaves the foreground and resumes when the app returns,
  as long as the user preference is still enabled and the last stop was
  lifecycle-related.
- It keeps the screen awake while the app is foregrounded and the worker is
  running, then releases that flag when auto processing is stopped or the app
  backgrounds.

## Worker Status And Watchdog

PR11D-13 adds explicit worker status to the Android Local Control Panel so the
operator can tell whether the Pad is really consuming the queue.

The panel shows:

- Auto processing enabled/disabled.
- Worker state: stopped, starting, waiting, polling, recovering, processing
  job, stopping, or error-stopped.
- Device id and store id.
- Last poll time and last poll result count.
- Last poll duration.
- Oldest pending job age and last queue delay.
- Last job processing duration.
- Consecutive error count.
- Recovery backoff delay and recovery attempt count when state is recovering.
- Whether the next poll is scheduled.
- Watchdog status.
- Current job, module, and printer endpoint while processing.
- Last start reason, stop reason, and error.

If the panel says auto processing is disabled, the Pad will not claim
`PENDING` jobs. If the panel says the worker is running but there is no poll for
more than about 10 seconds and no job is in progress, the watchdog schedules a
fresh poll and logs `Worker Watchdog Rescheduled`.

Manual Stop persists auto processing as disabled. App start or foreground
resume will not secretly restart printing until the operator taps Start again.
Pairing or manual Start enables auto processing.

Temporary network/API failures do not disable the user preference. If the panel
shows `RECOVERING`, leave the app open; the worker will retry automatically.
Use the frontdesk `检查打印 / 唤醒打印` button to trigger an immediate recovery
poll if the network has already returned.

## Multi-Pad Pilot Rules

- Multiple paired Pads can run semi-auto at the same time.
- Backend atomic claim allows only one Pad to claim each `PENDING` job.
- `PRINTING` jobs are not normally reclaimable.
- Any paired Pad can process GRAB, FRONTDESK_RECEIPT, or HOT_KITCHEN jobs.
- The Pad must be able to reach the assigned printer endpoint for the claimed
  job. If it cannot, the job fails and the worker stops.
- If a `PRINTING` job becomes stale, confirm whether paper already printed
  before reprinting from Print Center.

## Operator Checklist

- Keep the Android app open while semi-auto mode is active.
- Do not close the app or lock the screen during a print batch.
- If Print Center shows stale `PRINTING`, check the physical printer output
  before reprinting.
- If the printer is off, out of paper, or disconnected, semi-auto stops and
  Print Center should show a failed job or stale job requiring attention.
- If Print Center shows `ANDROID_PRINTER_CONNECT_TIMEOUT`,
  `ANDROID_PRINTER_CONNECTION_REFUSED`, or
  `ANDROID_PRINTER_NETWORK_UNREACHABLE`, test the displayed assigned printer
  endpoint from the same Android Pad before reprinting.
- If Print Center shows `ANDROID_PRINTER_WRITE_FAILED` or
  `ANDROID_PRINTER_FLUSH_FAILED`, inspect physical paper output first. A partial
  ticket may already exist.
- Use `Stop Auto Print / 停止自动处理` before changing printer IP or switching
  stores.
- If Print Center shows `PENDING / Waiting Pad / Attempt 0`, open the Android
  Local Control Panel and check auto processing, worker state, last poll time,
  last poll result count, next poll scheduled, and last stop/error reason. If
  auto processing is disabled, tap Start. If the worker is error-stopped, fix
  the displayed reason and restart manually.
- If the frontdesk print health banner says the worker is recovering, first
  verify Wi-Fi/backend availability and tap `检查打印 / 唤醒打印`; do not reprint
  failed or stale jobs until you confirm whether paper already came out.

## Logcat Markers

Filter Android logs by:

```text
RestaurantPadWorker
```

Important markers:

- `Worker Started reason=...`
- `worker_started reason=...`
- `Worker Stopped reason=...`
- `worker_stopped reason=...`
- `Worker Poll Scheduled delayMs=...`
- `Worker Poll Started deviceId=... storeId=...`
- `poll_start deviceId=... storeId=...`
- `Worker Poll Result count=...`
- `poll_end durationMs=... resultCount=... oldestJobAgeMs=...`
- `Worker Picked jobId=... module=... printerEndpoint=...`
- `job_picked jobId=... module=... queueDelayMs=... printerEndpoint=...`
- `claim_duration_ms jobId=... durationMs=...`
- `start_print_duration_ms jobId=... durationMs=...`
- `payload_duration_ms jobId=... durationMs=...`
- `tcp_print_duration_ms jobId=... durationMs=...`
- `complete_duration_ms jobId=... durationMs=...`
- `fail_duration_ms jobId=... durationMs=...`
- `job_finished totalDurationMs=... jobId=... module=...`
- `Worker Job Processing jobId=...`
- `Worker Job Finished jobId=... status=PRINTED`
- `Worker Job Failed jobId=... errorCode=...`
- `Worker Exception ...`
- `Worker Watchdog Rescheduled`

## Known Pilot Limitations

- Device token is still in Android `SharedPreferences`; production should move
  to encrypted storage.
- No long-running Android foreground service.
- No lease renewal loop during very long physical prints.
- No force release / mark failed operator action yet.
- No per-device module/printer assignment yet; routing comes from Print Center
  module-to-printer assignment per job.
- Complete failure after successful physical print is still a manual
  reconciliation case. Avoid blind reprint.
- `retry_count` is display/accounting only. It does not automatically requeue or
  reprint failed jobs.
