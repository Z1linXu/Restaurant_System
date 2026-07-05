# Android Local Printer Testing

This document covers the Android Pad shell local printer test panel.

The test panel is a hardware connectivity POC only. It is not `PAD_DIRECT`, it
does not claim backend print jobs, and it does not print real restaurant orders.

## Open The Test Panel

1. Install a debug build of the Android Pad app.
2. Long press anywhere inside the app WebView.
3. In `Restaurant Pad Local Control Panel`, find:
   - `Printer IP`
   - `Port`
   - `Timeout ms`
   - `Test Printer Connection`
   - `Test Print`

## Printer Settings

Use the printer's LAN IP address.

Typical ESC/POS TCP settings:

```text
Port: 9100
Timeout ms: 3000
```

The Android Pad and printer must be on the same Wi-Fi/LAN. If the printer uses
DHCP, reserve its IP in the router so the address does not change during pilot.

## Test Sequence

1. Enter printer IP.
2. Keep port at `9100` unless the printer is configured differently.
3. Keep timeout at `3000`; use `5000` for slower networks.
4. Tap `Test Printer Connection`.
5. If the connection succeeds, tap `Test Print`.
6. Confirm paper output, Chinese text, and cut behavior.

The fixed test ticket contains:

```text
RESTAURANT PAD TEST
打印机测试
IP: {ip}:{port}
Time: yyyy-MM-dd HH:mm:ss
----------------
```

## Common Failures

- `TIMEOUT`: printer is offline, IP is wrong, printer is on another subnet, or
  router/firewall/VLAN isolation blocks Pad-to-printer traffic.
- `CONNECTION_REFUSED`: port is wrong, printer does not expose raw TCP printing,
  or the printer service is disabled.
- `UNREACHABLE`: Android cannot route to that host. Check Wi-Fi, subnet, and AP
  isolation.
- `WRITE_FAILED`: socket opened but payload write failed. Check printer state,
  paper, and network stability.
- Chinese garbled output: printer firmware/code page does not match the GBK test
  payload. This should be handled in a later encoding hardening pass.
- Paper not cut: printer may not support this cut command, cutter may be
  disabled, or paper/cutter hardware needs service.

## Boundaries

The local printer test area does not:

- poll pending jobs
- claim jobs
- fetch backend ESC/POS payloads
- call complete/fail/release
- implement background printing
- read WebView `localStorage`
- upload printer IP to the backend

Device pairing is handled separately through Web Print Center plus the Android
`RestaurantPadDevice` bridge. Pairing stores device credentials for future
Pad Direct work, but it still does not make this printer test area a production
order-printing worker.

Those belong to later `PAD_DIRECT` worker PRs.
