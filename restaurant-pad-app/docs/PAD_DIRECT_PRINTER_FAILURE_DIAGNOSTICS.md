# PAD_DIRECT Printer Failure Diagnostics

PR11D-12 adds structured diagnostics and safe connect retry for intermittent
assigned-printer failures.

## What Changed

Android native TCP printing now reports structured failure metadata:

```text
native_error_code
phase
bytes_written
exception_class
exception_message
endpoint
```

The main phases are:

- `CONNECT`: Android could not open the TCP socket.
- `WRITE`: Android opened the socket but failed while writing ESC/POS bytes.
- `FLUSH`: Android failed while flushing bytes to the socket.
- `CLOSE`: socket close phase.

No ESC/POS payload base64, bearer token, or device token is logged or returned in
diagnostics.

## Safe Retry Rule

Android only retries when all of these are true:

- `phase = CONNECT`
- `bytes_written = 0`
- `native_error_code` is `TIMEOUT`, `CONNECTION_REFUSED`, or `UNREACHABLE`
- same job
- same paired device
- same assigned printer endpoint

Retry timing:

```text
attempt 1: immediate
attempt 2: after 500 ms
attempt 3: after 1500 ms
```

If all attempts fail, Android reports the job as `FAILED` and stops the
semi-auto worker.

## No Retry After Bytes May Have Been Sent

`WRITE` and `FLUSH` failures are not retried automatically. The printer may have
already received part or all of the ticket. Retrying blindly could duplicate or
partially duplicate a kitchen ticket.

When this happens:

1. Android reports the job as `FAILED`.
2. Print Center shows the error code, assigned printer endpoint, device id, phase,
   and raw diagnostic details.
3. Staff should check physical paper output before using Reprint.

## Error Codes

- `ANDROID_PRINTER_CONNECT_TIMEOUT`
- `ANDROID_PRINTER_CONNECTION_REFUSED`
- `ANDROID_PRINTER_NETWORK_UNREACHABLE`
- `ANDROID_PRINTER_WRITE_FAILED`
- `ANDROID_PRINTER_FLUSH_FAILED`
- `ANDROID_ASSIGNED_PRINTER_UNREACHABLE` legacy compatibility
- `ANDROID_NATIVE_PRINT_FAILED`

## retry_count Semantics

`retry_count` is still only a failure counter/display value. It does not drive
automatic retry. `FAILED` jobs are not returned to the pending queue and are not
automatically reprinted.

## Field Checklist

- Use the same Android Pad that failed to test the assigned printer host/port.
- Confirm printer static IP or DHCP reservation.
- Confirm Pad and printer are on the same Wi-Fi/VLAN.
- Check AP isolation / guest Wi-Fi isolation.
- Check printer power, sleep mode, paper, and network signal.
- Confirm the Print Center assignment points to the expected printer endpoint.
