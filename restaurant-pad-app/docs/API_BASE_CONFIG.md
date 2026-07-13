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
https://restaurant-pad.local/
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

## Pad Direct Device Pairing

Pad Direct pairing is started from the Web Print Center, not from a native
replacement settings page.

Recommended local flow:

1. Open the Android Pad App.
2. Use the Local Control Panel shortcut `Open Print Center`.
3. Log in to the Web app normally.
4. In Print Center, use `配对本机 Pad`.
5. The Web page calls the backend device registration API with the normal Web
   bearer token and store-scoped permissions.
6. The backend returns the raw `device_token` once.
7. The Web page immediately calls `window.RestaurantPadDevice.saveDeviceCredentials(...)`.
8. Android stores `device_id`, `device_token`, `store_id`, device name,
   registration metadata, app version, platform, and auto-print preference
   locally.

The Web page does not persist the raw device token. The Android native layer
does not read WebView `localStorage` or reuse the Web bearer token.

The Local Control Panel shows pairing status:

- 未配对 / 已配对
- Device ID
- Store ID
- Device name
- Registered at
- App version
- Platform
- Auto print enabled/disabled
- Token last 4 characters only

Use `Clear Pairing / 清除配对` only when replacing or resetting the Pad. Clearing
pairing removes the local device credentials and requires pairing again before a
future Pad Direct worker can claim print jobs.

Web logout does not clear Pad pairing. Pairing persists across Android app
restart until explicit Clear Pairing, backend disable/revoke, app data clear, or
uninstall.

Current storage is Android `SharedPreferences` for local pilot testing. Before
production Pad Direct worker rollout, move device token storage to
`EncryptedSharedPreferences` or Android Keystore-backed storage.

Pairing by itself does not enable automatic printing. The Android shell can
save the device credentials for later PAD_DIRECT actions, but background
workers, automatic polling, lease renewal, and unattended printing remain later
PRs.

## Pad Direct Pending Jobs And Manual Print

After pairing, the Local Control Panel can manually refresh `PAD_DIRECT` pending
print jobs. Each listed job has a manual `领取并打印` action that runs one happy
path only:

```text
claim -> start-print -> fetch payload -> assigned printer native TCP print -> complete
```

If payload fetch or native TCP printing fails after claim, the Android shell
reports the job as failed through the backend `fail` API so Print Center can show
the failure.

`start-print` moves the job into `PRINTING` before Android opens the local TCP
printer socket. Print Center uses this to show which Pad is actively printing
and to warn operators when a `PRINTING` lease becomes stale.

The payload response includes the backend assigned printer endpoint. Android
prints real PAD_DIRECT jobs to `printer_host:printer_port` from the payload.
The Local Control Panel printer IP/port fields are only for local connection
testing and fixed test tickets.

Pending jobs API route used by the Android shell:

```text
GET /api/v1/stores/{storeId}/printing/jobs/pending?limit=25
X-Device-Id: {saved device id}
X-Device-Token: {saved device token}
```

Local Preview mode uses the configured Web App URL origin:

```text
http://{developer-lan-ip}:5173/api/v1/stores/{storeId}/printing/jobs/pending
```

Vite preview proxies `/api` to the backend on the development computer. This
keeps Android native code from needing to know the backend `8080` port during
local preview testing.

Bundled Assets mode falls back to the configured API Base URL origin.

Manual print job APIs used after tapping `领取并打印`:

```text
POST /api/v1/printing/jobs/{jobId}/claim
POST /api/v1/printing/jobs/{jobId}/start-print
GET  /api/v1/printing/jobs/{jobId}/payload
POST /api/v1/printing/jobs/{jobId}/complete
POST /api/v1/printing/jobs/{jobId}/fail
X-Device-Id: {saved device id}
X-Device-Token: {saved device token}
```

The control panel can also run a foreground-only semi-auto loop when explicitly
enabled by the operator. It refreshes pending jobs, processes one job at a time
with the same `claim -> start-print -> payload -> assigned printer TCP print -> complete`
flow, and stops on device auth, backend, or printer errors. It is not an Android
background service and stops when the app is paused or closed.

Expected setup for manual testing:

1. Pair this Android Pad from Web Print Center.
2. Set store printing mode to `PAD_DIRECT`.
3. Submit an order from the Web POS.
4. Confirm Print Center shows `PENDING` jobs.
5. Open Local Control Panel and tap `Refresh Pending Print Jobs / 刷新待打印任务`.
6. Configure local printer IP/port/timeout.
7. Tap `领取并打印` on one job.

Common viewer errors:

- `设备认证失败，请重新配对`: the saved device id/token is missing, inactive, or invalid.
- `任务已被其他 Pad 领取`: another Pad claimed this job first.
- `无法连接后端`: Web App URL, Wi-Fi, `preview:lan`, backend, or firewall is not reachable.
- `暂无待打印任务`: no `PAD_DIRECT` pending jobs are available for this store/device.
- `打印 payload 缺失`: the backend did not return ESC/POS payload data.
- `打印任务缺少 assigned printer`: Print Center assignment/printer config is missing or disabled.
- `本机打印失败`: Android could not send the payload to the configured LAN printer.

## Production Placeholder

Example:

```text
https://api.example.com
```

Production API base must be configured at runtime in a future setup/pairing flow. It must not be compiled into the APK.

## Injection Strategy

Bundled assets mode serves files from:

```text
https://restaurant-pad.local/
```

When serving `index.html`, the shell injects:

```text
window.__RESTAURANT_API_BASE_URL__
window.__RESTAURANT_WS_BASE_URL__
window.__RESTAURANT_APP_CONFIG__
```

`__RESTAURANT_APP_CONFIG__` contains the bundled build version, asset-manifest
SHA-256, Android app version, IndexedDB schema version, loading mode, and the
paired store id when available. It never contains bearer/device tokens or
printer secrets.

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
