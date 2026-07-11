# Pad Direct Manual Print Happy Path

This runbook covers PR11D-4 and PR11D-5 local pilot testing. It is a manual
one-job flow, not an Android background worker.

## Flow

```text
Refresh Pending Jobs
-> tap one job: 领取并打印
-> claim
-> start-print
-> fetch ESC/POS payload
-> native TCP print to the assigned printer endpoint
-> complete on success
-> fail on payload/native print error
```

The Android shell authenticates with saved device credentials:

```text
X-Device-Id
X-Device-Token
```

It does not read the WebView bearer token or WebView `localStorage`.

## Setup

1. Pair the Android Pad from Web Print Center.
2. Configure store printing mode as `PAD_DIRECT`.
3. Configure local printer IP, port, and timeout in the Android Local Control
   Panel.
4. Submit an order from the Web POS.
5. Confirm Print Center shows `PENDING` jobs.
6. Long press in the Android app and refresh pending jobs.
7. Tap `领取并打印` on one job.

## Expected Results

- Success: the printer outputs the ticket, Android shows `打印完成`, and Print
  Center marks the job `PRINTED`.
- Printing: after `start-print`, Print Center shows the job as `PRINTING` with
  the claiming Pad device and lease expiry.
- Routing: Android prints to the payload `printer_host:printer_port`, not the
  Local Control Panel test printer field.
- Printer failure: Android shows a local print failure, calls the backend `fail`
  API, and Print Center marks the job `FAILED`.
- Two Pads claim the same job: only one Pad succeeds; the other shows
  `任务已被其他 Pad 领取`.
- If the printer physically printed but Android cannot call `complete`, treat
  the Print Center warning as a duplicate-print risk. Confirm the paper before
  reprinting.

## Boundaries

PR11D-4 does not implement:

- background worker
- automatic polling
- batch print
- lease renewal
- release
- retry/backoff
- production encrypted token storage

PR11D-5 adds the explicit `PRINTING` state and prevents ordinary claim from
stealing active `PRINTING` jobs. It does not add force release, encrypted token
storage, or a background worker.
