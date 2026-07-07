# Pad Direct Multi-Printer Routing

PR11D-8 changes Android PAD_DIRECT order printing from a single local default
printer to backend-assigned printer routing.

## Routing Rule

Print Center remains the source of truth:

```text
GRAB              -> assigned printer A
FRONTDESK_RECEIPT -> assigned printer B
HOT_KITCHEN       -> assigned printer C
```

When the backend creates a PAD_DIRECT job, it stores the assigned
`print_jobs.printer_id`. When Android fetches payload, the response includes
the assigned printer endpoint:

```text
printer_id
printer_name
printer_host
printer_port
printer_endpoint
paper_width_mm
text_encoding
escpos_code_page
timeout_ms
module_code
```

Android must print to `printer_host:printer_port` from the payload. It must not
use the Local Control Panel printer test IP for real PAD_DIRECT jobs.

## Android Behavior

- Manual `领取并打印` uses:
  `claim -> start-print -> payload -> assigned printer TCP print -> complete/fail`.
- Semi-auto mode uses the same endpoint selection for every job.
- If payload is missing `printer_host` or `printer_port`, Android marks the job
  failed with `ANDROID_ASSIGNED_PRINTER_MISSING`.
- If the assigned printer cannot be reached from this Pad, Android records
  structured native diagnostics (`native_error_code`, `phase`, `bytes_written`,
  exception, endpoint). Safe connect failures are retried briefly; persistent
  failures mark the job `FAILED` and stop the worker.
- `WRITE` and `FLUSH` failures are not retried because the printer may have
  already received part of the ticket.

## What Local Printer Test Means Now

The Local Control Panel printer IP/port fields are only for:

- `Test Printer Connection`
- `Test Print`
- local network troubleshooting

They are not the default route for PAD_DIRECT restaurant order jobs.

## Multi-Pad Strategy

- Any paired Pad can process any module job for its store.
- Backend atomic claim and `PRINTING` state prevent duplicate claim/print.
- There is no device-printer affinity in this PR.
- A Pad must be on a network that can reach every assigned printer it may claim.

## Pilot Limitations

- Printer endpoints are read from current `printer_configs` through
  `print_jobs.printer_id`; there is no endpoint snapshot column.
- If a printer IP is changed after jobs are already pending, payload uses the
  latest printer config for that `printer_id`.
- Device credential storage is still local pilot storage.
- No background daemon, force release tooling, or module affinity is included.
- No automatic requeue/reprint is included. `retry_count` is a failure counter
  only; `FAILED` jobs still require operator review and manual reprint.
