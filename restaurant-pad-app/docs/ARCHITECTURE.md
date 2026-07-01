# Pad App Skeleton Architecture

This document tracks the independent Android Pad App structure. PR2 reserved the skeleton. PR3 adds a WebView shell POC that can load a copied frontend `dist` artifact. PR4 adds a native raw TCP printer test bridge for hardware validation only.

## Target Architecture

```text
restaurant-pad-app
  -> Android WebView / Capacitor shell
  -> bundled Restaurant_System frontend build
  -> native PrinterPlugin
  -> runtime API base config
  -> device/store binding
  -> local LAN print execution
```

`Restaurant_System` remains the backend and web source of truth:

```text
Restaurant_System backend
  -> orders
  -> menu
  -> auth
  -> store access
  -> owner dashboard
  -> print_jobs
  -> receipt renderers
```

## Boundaries

The Pad App should not duplicate:

- order pricing
- tax calculation
- receipt business layout
- combo rules
- store access rules
- role/permission logic

Those responsibilities stay in the backend and current frontend.

The Pad App native layer should only provide local device capability:

- open TCP socket to LAN printer
- send rendered ESC/POS bytes
- return success/failure to backend

## Future Runtime Flow

```text
Pad frontend submits order
  -> backend saves order
  -> backend creates and renders print_jobs
  -> Pad fetches pending job
  -> Pad claims job
  -> native PrinterPlugin prints locally
  -> Pad completes or fails job
```

## Current Contents

- `android/`: Android WebView shell POC.
- `app/`: reserved for future shell app source.
- `web/`: reserved for future frontend build artifact.
- `plugins/`: reserved for future native plugins and plugin docs.
- `docs/`: Pad app documentation.

## PR3 Runtime Notes

- Bundled assets are served from `android/app/src/main/assets/web`.
- Deep paths fall back to `index.html`.
- Runtime API base is stored in Android `SharedPreferences`.
- `index.html` is injected with a small bridge for `/api` and `/ws` runtime base handling.
- Debug builds can use cleartext HTTP through the debug manifest overlay.

## PR4 Printer POC Notes

- `RestaurantPrinter.testConnection` opens a TCP socket with timeout.
- `RestaurantPrinter.printRawTcp` sends raw base64-decoded bytes.
- The printer test page is reachable at `/printer-test.html`.
- Android sends bytes only and does not build restaurant receipts.
- Physical printer validation is manual and must not be assumed from repository checks.

## Non-Goals Through PR4

- No Capacitor install.
- No backend changes.
- No frontend source changes.
- No print job integration.
- No `PAD_DIRECT`.

## PR5/PR6 Backend Contract

The existing Restaurant_System backend now recognizes `PAD_DIRECT` mode and exposes the Pad Direct device/print-job contract.

- `PAD_DIRECT` creates rendered print jobs without opening backend TCP sockets.
- Store devices register through the backend and receive a one-time token.
- Runtime Pad requests use `X-Device-Id` and `X-Device-Token`.
- Pending jobs are claimed with a lease so one physical Pad owns one job at a time.
- The payload endpoint returns both receipt preview text and `escpos_payload_base64`.

The Android shell in this directory still does not run a production polling worker. PR7/next Android work should wire this contract into a local worker that calls the native printer bridge.
