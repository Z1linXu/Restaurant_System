# Android Native Printer Plugin POC

Current status: PR4 code-level POC.

This POC does not print restaurant orders and does not use backend `print_jobs`.

It is a hardware connectivity test for the Android native TCP bridge only. It does not claim jobs, call complete/fail/release, or use backend business receipt rendering.

## Plugin Interface

The Android JavaScript bridge exposes:

```text
RestaurantPrinter.testConnection(json)
RestaurantPrinter.printRawTcp(json)
```

Expected request for `testConnection`:

```json
{
  "ip": "192.168.2.200",
  "port": 9100,
  "timeoutMs": 3000
}
```

Expected request for `printRawTcp`:

```json
{
  "ip": "192.168.2.200",
  "port": 9100,
  "timeoutMs": 3000,
  "payloadBase64": "..."
}
```

The plugin sends raw bytes only. It does not build business receipts, calculate totals, choose combo options, or read orders.

`192.168.2.200` is an example local test printer. Do not hardcode this IP in source code. Real printer addresses must come from a setting, runtime config, backend printer config, or operator input.

## Test Page

The Pad shell includes:

```text
https://restaurant-pad.local/printer-test.html
```

This page allows manual input of:

- printer IP
- port
- timeout
- base64 payload

It includes buttons for:

- Test Connection
- Print Test Receipt

## Error Codes

The bridge returns structured JSON with:

- `TIMEOUT`
- `CONNECTION_REFUSED`
- `UNREACHABLE`
- `WRITE_FAILED`
- `UNKNOWN`

## Network Requirements

- Pad and printer must be on the same LAN.
- Backend computer is not required for PR4 raw TCP printer testing, but if the WebView also needs API calls it must be reachable at `http://{developer-lan-ip}:8080`.
- Printer should have a fixed IP or DHCP reservation.
- Typical ESC/POS TCP port is `9100`.
- Router AP isolation / client isolation must be disabled.
- Firewalls must allow Pad-to-printer TCP traffic.

For the simplest local test, keep all devices in the same subnet:

```text
Android Pad: 192.168.2.x
Backend computer: 192.168.2.x
Printer: 192.168.2.200
```

`192.168.1.x` and `192.168.2.x` are usually separate `/24` networks. They usually cannot talk directly unless cross-subnet routing is configured or the subnet mask allows it.

If connection fails, check:

- Pad IP
- backend computer IP, if API access is needed
- printer IP
- subnet mask
- gateway
- AP isolation / client isolation
- TCP port `9100`
- Android local-network socket behavior
- printer fixed IP / DHCP reservation

## Encoding Notes

The default test payload contains:

- ESC/POS init
- code page command
- English text
- Chinese text encoded as GBK bytes
- large text command
- bold command
- line feeds
- cut command

Real printer firmware may require different code page settings. Manual testing is required.

## Manual Hardware QA Required

Automated repository checks cannot prove physical printing.

Required manual QA:

1. Install/run the Pad app on Android hardware.
2. Connect Pad and printer to the same LAN.
3. Open the printer test page.
4. Enter printer IP `192.168.2.200`, port `9100`, and timeout `3000` or `5000`.
5. Tap Test Connection.
6. Tap Print Test Receipt.
7. Confirm paper output.
8. Confirm Chinese, font size, bold, and cut behavior.
9. Test wrong IP/port and verify clear errors.
