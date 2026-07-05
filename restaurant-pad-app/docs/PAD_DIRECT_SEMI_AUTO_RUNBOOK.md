# Pad Direct Safe Semi-Auto Printing Runbook

This runbook covers PR11D-5/6/7 pilot behavior. The Android Pad can process
`PAD_DIRECT` jobs while the app is open and the Local Control Panel semi-auto
loop is explicitly enabled.

## What This Is

- A foreground-only Android pilot loop.
- Operator-controlled: starts only after tapping `Start Auto Print / 开启自动处理打印任务`.
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
- It calls fail before paper is sent when payload/native TCP fails.
- It stops on device auth, backend, payload, or printer errors.
- It stops when the app is paused or closed.

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
- Use `Stop Auto Print / 停止自动处理` before changing printer IP or switching
  stores.

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
