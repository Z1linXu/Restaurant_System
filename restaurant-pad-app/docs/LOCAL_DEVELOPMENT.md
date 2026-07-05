# Pad App Local Development

Current status: Pad App POC workspace with Android WebView shell files and native TCP printer test bridge. Android build still requires a local Android SDK / Android Studio environment.

## Architecture Reminder

There is only one backend in the current system:

```text
Restaurant_System/backend
```

`restaurant-pad-app` is not a backend. It is an Android client shell that loads the Restaurant_System frontend and, in later work, will execute local printer operations from the Android device.

During local development:

```text
Recommended:
Android Pad App
  -> Web App URL: http://{developer-lan-ip}:5173
  -> frontend production preview
  -> /api and /ws proxy to backend localhost:8080

Alternative bundled-assets mode:
Android Pad App
  -> bundled frontend artifact: android/app/src/main/assets/web
  -> API Base URL: http://{developer-lan-ip}:8080
```

The development computer, Android Pad, and printers must be on the same LAN when testing native printer behavior.

## Current Restaurant_System Run Commands

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend dev server:

```bash
cd frontend
npm run dev
```

Frontend LAN production preview:

```bash
cd frontend
npm run build
npm run preview:lan
```

Confirm preview listens on the LAN:

```bash
ipconfig getifaddr en0
curl -i http://{developer-lan-ip}:5173/
```

Build the frontend and copy it into Android assets:

```bash
cd frontend
npm run build
cd ..
bash restaurant-pad-app/scripts/copy-frontend-dist.sh
```

## Local Preview Web App URL

For local testing, prefer setting the Android Pad App Web App URL to the development computer LAN preview address:

```text
http://{developer-lan-ip}:5173
```

Examples:

```text
http://192.168.2.140:5173
http://192.168.2.33:5173
```

Long press inside the Pad app to open the Local Control Panel. Fill `Local Preview Web App URL` and leave `Bundled Assets API Base URL` empty for this mode.

Before using the shell, open the same URL in Android Chrome. If Android Chrome cannot open it, the shell cannot open it either.

## Local Control Panel

The long-press panel is intentionally lightweight. It is a Web shortcut and local
debug panel, not a native replacement for the Restaurant POS admin pages.

It provides:

- current WebView URL
- configured Web App URL
- current mode: Local Preview Mode or Bundled Assets Mode
- troubleshooting reminders for LAN preview
- Refresh Current Page
- Open Frontdesk
- Open Order Center
- Open Print Center
- Open Menu Management
- Open Dining Tables
- Test Web App URL
- Local Printer Test fields
- Test Printer Connection
- Test Print

Shortcut behavior:

- If the current page URL includes `/stores/{storeId}/`, shortcuts open the
  matching store-scoped Web route.
- If no store id is available, shortcuts open legacy Web routes such as
  `/frontdesk` or `/admin/settings/printing`; the Web app then redirects through
  its normal store workspace logic.
- The Android shell does not read WebView `localStorage`, reuse auth tokens, or
  call backend printing/menu APIs directly.

`Test Web App URL` performs a simple reachability check against the configured
Web App URL only. It does not test backend auth or business APIs.

`Local Printer Test` performs direct Android-to-printer TCP checks. It does not
use WebView login state, does not call backend APIs, and does not claim
`PAD_DIRECT` jobs.

## Bundled Assets API Base Configuration

If you leave Web App URL empty, the shell loads Android bundled assets from:

```text
https://restaurant-pad.local/index.html
```

Then configure API Base URL to the development computer LAN backend address:

```text
http://{developer-lan-ip}:8080
```

Bundled assets mode is useful when testing the copied frontend artifact, but local preview mode is simpler for day-to-day LAN testing because `/api` and `/ws` go through Vite preview proxy.

Do not use Android's own `localhost` or `127.0.0.1` to mean the development computer. Inside Android, `localhost` points back to the Android device itself.

Do not hardcode the development computer IP in source code. It should be entered through runtime settings/native preferences during local testing.

## Native Printer Bridge Local Test

The native printer test area inside the Local Control Panel is a hardware
connectivity POC only.

It does not:

- print real restaurant orders
- call backend `print_jobs`
- claim print jobs
- call complete/fail/release
- use backend receipt business logic

Use the local test printer example:

```text
Printer IP: your printer LAN IP
Port: 9100
Timeout: 3000 or 5000 ms
```

Recommended test sequence:

1. Put the Android Pad on the same Wi-Fi/LAN as the printer.
2. Confirm the backend computer is also reachable on the same LAN if the WebView needs API access.
3. Long press inside the Pad app to open the Local Control Panel.
4. Enter the printer IP, port `9100`, and timeout `3000` or `5000`.
5. Tap `Test Connection`.
6. If the connection succeeds, tap `Test Print`.

Printer IP values are operator-entered local test settings. Real deployments
must read the printer IP from runtime config, backend printer config, or an
approved setup flow.

The fixed test ticket contains:

```text
RESTAURANT PAD TEST
打印机测试
IP: {ip}:{port}
Time: yyyy-MM-dd HH:mm:ss
----------------
```

The Android shell builds a raw ESC/POS payload locally with init, GBK-encoded
text, line feeds, and cut. If Chinese output is garbled, the printer firmware
may require a different code page or encoding setting in a later hardening PR.

## LAN / Subnet Checklist

For first-pass local testing, keep all three devices in the same subnet:

```text
Android Pad: 192.168.2.x
Backend computer: 192.168.2.x
Printer: 192.168.2.200
```

`192.168.1.x` and `192.168.2.x` are usually different `/24` networks. They normally cannot directly reach each other unless the router is configured for cross-subnet routing or the subnet mask allows it.

If connection fails, check:

- Android Pad IP address
- backend computer IP address
- whether Android Chrome can open `http://{developer-lan-ip}:5173`
- whether backend is reachable at `http://{developer-lan-ip}:8080/api/v1/auth/me`
- printer IP address
- subnet mask
- gateway
- router AP isolation / client isolation
- computer firewall allowing port `5173`
- computer firewall allowing port `8080` if using bundled assets API base mode
- Vite preview `/api` and `/ws` proxy when using local preview mode
- printer port `9100`
- Android local-network socket permission / cleartext debug config
- whether the printer has a fixed IP or DHCP reservation
- Chinese garbled output, which usually means firmware/code page mismatch
- paper not cut, which usually means the printer uses a different cut command or cutter is disabled

Common WebView / Android errors:

- `ERR_CLEARTEXT_NOT_PERMITTED`: local HTTP is blocked. Use a debug build or HTTPS.
- `ERR_CONNECTION_REFUSED`: server is not listening, wrong IP/port, or firewall blocked it.
- `ERR_NAME_NOT_RESOLVED`: hostname cannot resolve. Use the LAN IP.
- `Failed to fetch`: inspect whether the request went to `/api` through preview or directly to backend API base.
- WebSocket errors: confirm `/ws` proxy exists in Vite preview and backend is running.
- Old frontend dist: rebuild frontend and rerun `copy-frontend-dist.sh` if using bundled assets mode.

## Configuration Rules

- Do not hardcode developer IP addresses in source code.
- Do not hardcode printer IP addresses in source code.
- Local Preview Web App URL should be configurable in Pad native preferences or a setup screen.
- Runtime API base must remain configurable for bundled assets mode.
- Printer IP must come from user input, runtime config, or backend printer configuration.
- Android debug builds may allow cleartext HTTP only for local development.
- Production should use HTTPS for backend API traffic.

## Not Yet Available

- production background print worker inside Android
- production device pairing / cloud config
- real order print job polling from Android
- automated physical printer QA
- native Print Center or Menu Management screens
