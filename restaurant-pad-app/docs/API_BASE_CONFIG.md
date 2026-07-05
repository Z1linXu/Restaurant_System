# Runtime Web App / API Base Configuration

The Android Pad App now supports two local/debug loading modes.

## Mode A: Local Preview Web App URL

Recommended for local LAN testing before cloud deployment:

```text
Web App URL: http://{developer-lan-ip}:5173
API Base URL: leave empty
```

In this mode the WebView directly loads the frontend production preview server.
The frontend keeps using relative paths such as:

```text
/api/v1/auth/login
/api/v1/me/workspaces
/ws
```

Those requests go to the preview server first, then Vite preview proxies them to
the backend on the development computer:

```text
Android WebView
  -> http://{developer-lan-ip}:5173
  -> /api and /ws
  -> backend localhost:8080 through preview proxy
```

This avoids the HTTPS asset page -> HTTP API mixed-content problem during local
testing.

## Mode B: Bundled Assets + API Base URL

Bundled assets mode loads:

```text
https://restaurant-pad.local/index.html
```

The bundled frontend may call relative paths such as:

```text
/api/v1/auth/login
/api/v1/me/workspaces
/ws
```

In this mode the Pad shell must provide a runtime API base URL.

Example:

```text
http://{developer-lan-ip}:8080
```

Do not hardcode a real developer IP in source code. The shell stores both Web App URL and API Base URL in Android `SharedPreferences`.

Long press inside the Pad app to open the Local Control Panel. The panel keeps
the runtime config fields and also provides Web shortcut buttons for common
Restaurant POS pages.

## Local Control Panel Shortcuts

The Local Control Panel is a thin Android shell helper. It does not reimplement
owner/admin pages, does not read WebView tokens, and does not call backend
business APIs directly.

The panel shows:

- current WebView URL
- configured Web App URL
- current loading mode: Local Preview Mode or Bundled Assets Mode
- local troubleshooting notes

Shortcut buttons:

- Refresh Current Page
- Open Frontdesk
- Open Order Center
- Open Print Center
- Open Menu Management
- Open Dining Tables
- Test Web App URL
- Test Printer Connection
- Test Print

When the current WebView URL contains `/stores/{storeId}/`, shortcuts use the
store-scoped routes:

```text
/stores/{storeId}/frontdesk
/stores/{storeId}/frontdesk/order
/stores/{storeId}/admin/settings/printing
/stores/{storeId}/admin/menu/items
/stores/{storeId}/admin/settings/tables
```

If no store id can be parsed, shortcuts use legacy routes such as `/frontdesk`
or `/admin/settings/printing`. The Web frontend is responsible for redirecting
those routes to the correct store workspace after normal auth/store checks.

`Test Web App URL` only checks whether the configured Web App URL is reachable.
It does not test backend APIs and does not use the logged-in Web session.

## Local Printer Test

The Local Control Panel also includes a small local printer test section:

```text
Printer IP
Port, default 9100
Timeout ms, default 3000
```

Buttons:

- `Test Printer Connection`: opens a TCP socket to the printer and closes it.
- `Test Print`: sends a fixed ESC/POS test ticket from the Android device.

This is a local LAN hardware test only. It does not enable `PAD_DIRECT`, does
not claim backend `print_jobs`, does not call complete/fail/release, and does
not print real restaurant orders.

The printer IP, port, and timeout are stored in Android `SharedPreferences`
using local test keys only. The shell does not store device tokens, backend
secrets, or WebView bearer tokens for this feature.

## Production Placeholder

Example:

```text
https://api.example.com
```

Production API base must be configured at runtime in a future setup/pairing flow. It must not be compiled into the APK.

## Injection Strategy

Bundled assets mode serves files from:

```text
https://restaurant-pad.local/index.html
```

When serving `index.html`, the shell injects:

```text
window.__RESTAURANT_API_BASE_URL__
window.__RESTAURANT_WS_BASE_URL__
```

It also patches `fetch` and `WebSocket` calls that begin with `/api/` or `/ws` so the current frontend build can run without Vite proxy.

This is a POC bridge. A later hardening pass may add explicit frontend support for runtime API base.

## Cleartext HTTP

The main Android manifest keeps cleartext disabled. A debug manifest overlay enables cleartext only for local debug builds.

Production should use HTTPS.

## Common Local Errors

- `localhost` or `127.0.0.1`: inside Android this means the Android device, not the development computer.
- `ERR_CLEARTEXT_NOT_PERMITTED`: the build is not allowing local HTTP. Use a debug build for local preview.
- `ERR_CONNECTION_REFUSED`: backend or preview server is not running, the port is wrong, or a firewall blocked the connection.
- `ERR_NAME_NOT_RESOLVED`: the hostname cannot be resolved. Use the computer LAN IP for local preview.
- WebSocket `/ws` failure: confirm `npm run preview:lan` is running and Vite preview is proxying `/ws`.
- Old frontend behavior: rebuild frontend and copy dist into Android assets only when using bundled assets mode.
