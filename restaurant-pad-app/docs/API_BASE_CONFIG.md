# Runtime API Base Configuration

The Android Pad App cannot rely on Vite proxy. The bundled frontend may call relative paths such as:

```text
/api/v1/auth/login
/api/v1/me/workspaces
/ws
```

The Pad shell must provide a runtime API base URL.

## Local Debug

Example:

```text
http://{developer-lan-ip}:8080
```

Do not hardcode a real developer IP in source code. The PR3 shell stores the value in Android `SharedPreferences`.

Long press inside the Pad app to reopen the API base dialog.

## Production Placeholder

Example:

```text
https://api.example.com
```

Production API base must be configured at runtime in a future setup/pairing flow. It must not be compiled into the APK.

## Injection Strategy

The PR3 WebView serves bundled assets from:

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
