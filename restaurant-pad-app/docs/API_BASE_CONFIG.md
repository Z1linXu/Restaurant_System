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

Long press inside the Pad app to reopen the runtime config dialog.

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
