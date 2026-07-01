# Restaurant Pad App

This directory is the independent Android Pad App workspace for the Restaurant POS Pad transition.

Current status: PR2-PR7 code-level Pad App architecture files are present. Android build and real-device printer QA still require a local Android SDK / Android Studio environment and physical LAN printer access.

This project does not currently:

- run a production background print worker
- claim or complete live `print_jobs` from the Android app
- print real restaurant orders from the native bridge

The Android WebView shell can load a bundled frontend artifact after `frontend/dist` is copied into Android assets. This is still a POC; the long-term release process should use a pinned frontend build artifact.

The native printer bridge exposes a test-only raw TCP interface for hardware validation. It is not connected to restaurant order printing.

## Purpose

The Pad App will eventually run the existing Restaurant_System React frontend inside an Android WebView / Capacitor-style shell. Android native code will provide local device capabilities that browsers cannot provide, especially direct TCP printing to LAN ESC/POS printers.

The current `Restaurant_System` Web + Backend remains the source of truth for:

- auth
- store workspace
- menu catalog
- orders
- print jobs
- receipt rendering
- owner multi-store views

The Pad App native layer will eventually execute local LAN printing for a store printer such as `192.168.x.x:9100`.

There is only one backend in the current architecture:

```text
Restaurant_System/backend
```

`restaurant-pad-app` is not a second backend. It is an Android client shell that talks to the same Restaurant_System backend over the LAN or future deployment network.

## Why This Is Separate

Keeping this as an independent workspace protects the current stable Web + Backend project from Android SDK, Gradle, Capacitor, native permissions, APK build output, and printer-plugin experiments.

Future PRs should keep Pad work isolated unless a small, reviewed backend/frontend contract change is explicitly required.

## Planned PR Sequence

- PR5: add backend `PAD_DIRECT` mode.
- PR6: add device registration and print job claim APIs.
- PR7: show Pad device and claim status in Print Center.

PR8 cloud pairing/config is intentionally not part of the current execution sequence.

## Current Local Run Impact

None. This skeleton does not change how the existing app is run:

```bash
cd backend && mvn spring-boot:run
cd frontend && npm run dev
```

or production-preview flow:

```bash
cd frontend && npm run build
cd frontend && npm run preview:lan
```

## Directory Layout

```text
restaurant-pad-app/
  android/   # Android WebView shell project
  app/       # Reserved for future app-level source/shared files
  web/       # Future copied frontend dist artifact
  plugins/   # Future native plugins, including PrinterPlugin
  docs/      # Pad app specific documentation
```

## Loading The Current Frontend Build

Build the current web frontend:

```bash
cd frontend
npm run build
```

Copy it into Android assets:

```bash
bash restaurant-pad-app/scripts/copy-frontend-dist.sh
```

The Android shell serves bundled assets from:

```text
android/app/src/main/assets/web
```

Long press in the app to set the runtime API base URL. Example local value:

```text
http://{developer-lan-ip}:8080
```

Do not use Android's own `localhost` when the backend is running on the development computer. On the Android device, `localhost` means the Android device itself, not the Mac/Windows backend host.

Do not hardcode developer IPs or printer IPs in source code.

## Native Printer Test POC

The Android shell includes a test page:

```text
https://restaurant-pad.local/printer-test.html
```

It calls the native bridge:

```text
RestaurantPrinter.testConnection(...)
RestaurantPrinter.printRawTcp(...)
```

This page is for manual hardware validation only. It does not print orders and does not touch backend `print_jobs`.

Local hardware test example:

```text
Printer IP: 192.168.2.200
Port: 9100
Timeout: 3000 or 5000 ms
```

Recommended flow:

1. Make sure the Android Pad, development computer running `Restaurant_System/backend`, and printer are on the same LAN/subnet.
2. Open `https://restaurant-pad.local/printer-test.html` inside the Pad app.
3. Enter `192.168.2.200`, port `9100`, and timeout `3000` or `5000`.
4. Tap `Test Connection`.
5. If the connection succeeds, tap `Print Test Receipt`.

`192.168.2.200` is only a local testing example. Do not hardcode it into source code; production values should come from a setup screen, runtime config, backend printer config, or operator input.

Network note: `192.168.1.x` and `192.168.2.x` are usually different `/24` subnets and may not talk to each other unless the router/subnet mask explicitly allows routing between them. For first-pass testing, prefer:

```text
Android Pad: 192.168.2.x
Backend computer: 192.168.2.x
Printer: 192.168.2.200
```

## Pad Direct Backend Contract

Backend PR6 adds the Pad Direct queue contract used by a future Android worker:

- Register a store device with `POST /api/v1/devices/register` using a normal authorized admin session.
- Store the returned `device_id` and one-time `device_token` in the Pad app.
- Send runtime requests with `X-Device-Id` and `X-Device-Token`.
- Poll `GET /api/v1/stores/{storeId}/printing/jobs/pending`.
- Claim one job at a time with `POST /api/v1/printing/jobs/{jobId}/claim`.
- Fetch the printable payload through `GET /api/v1/printing/jobs/{jobId}/payload`.
- Use `escpos_payload_base64` with the native TCP printer bridge.
- Report success/failure through `complete`, `fail`, or `release`.

The backend stores only the device token hash. `PAD_DIRECT` never opens TCP printer sockets from the backend.

## Print Center Visibility

Restaurant_System Print Center now displays Pad Direct readiness:

- registered Pad devices for the selected store
- last seen time, app version, platform, and active status
- print job execution mode
- claim owner device id and lease expiration
- printed-by device id after completion
- whether a preview has an ESC/POS base64 payload
