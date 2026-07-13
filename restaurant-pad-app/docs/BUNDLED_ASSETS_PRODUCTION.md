# Bundled Assets Production Mode

PR7 packages the React static shell inside the Android APK while keeping the
API, WebSocket, menu, authentication, and store data runtime-configured. This
mode lets an already-authorized Pad open the ordering shell and its local data
when the hosted frontend or network is unavailable.

## Supported Architecture

```text
Android WebView
  -> https://restaurant-pad.local/
  -> APK assets/web/index.html + hashed JS/CSS
  -> runtime API/WebSocket base injected by Android
  -> cloud backend when reachable
  -> IndexedDB menu/drafts/outbox/workspace snapshots when offline
```

The optional `Local Preview Web App URL` remains available in the Local Control
Panel as a diagnostics and rollback path. When it is non-empty, the Pad loads
that remote URL instead of bundled assets.

## Reproducible Build Flow

Install frontend dependencies before the first build, then run from repository
root:

```bash
restaurant-pad-app/scripts/build-bundled-apk.sh debug
```

For a release artifact after release signing is configured:

```bash
restaurant-pad-app/scripts/build-bundled-apk.sh release
```

The pipeline performs these steps in order:

1. Generates one `BUNDLED_BUILD_VERSION` and passes it to Vite.
2. Runs `frontend` production build.
3. deletes the previous `android/app/src/main/assets/web` directory.
4. copies the complete new `frontend/dist` tree.
5. verifies `index.html` references existing main JavaScript and CSS files.
6. generates `asset-manifest.json` with every asset size and SHA-256.
7. generates `build-info.json` with build/schema/manifest identity.
8. recomputes every hash before Gradle packages the APK.

Gradle `preBuild` depends on `verifyBundledFrontend`, so Android Studio or a
direct `./gradlew :app:assembleDebug` also refuses stale, missing, extra, or
modified bundled files. `copy-frontend-dist.sh` is safe for an already-built
`frontend/dist`, but the full build script is the supported release path.

## Runtime Configuration

The WebView serves bundled routes through SPA fallback at:

```text
https://restaurant-pad.local/
```

Before the frontend entry script runs, Android injects:

```text
window.__RESTAURANT_API_BASE_URL__
window.__RESTAURANT_WS_BASE_URL__
window.__RESTAURANT_APP_CONFIG__
```

The app config contains loading mode, Android version, bundled build version,
asset-manifest SHA-256, IndexedDB schema version, and paired store id. It does
not include Web tokens, device tokens, printer credentials, or a hardcoded
server/store address. The bundled login form also ships with empty account and
password fields.

## Offline Cold Start Boundary

Offline access is deliberately narrower than normal online authorization:

- Existing access/refresh tokens must still be present on the device.
- The account must have a successful auth snapshot from the previous 24 hours.
- Only the last online-validated store is exposed by the cached workspace.
- Only frontdesk ordering routes are allowed while using the snapshot.
- Cached menu, local drafts, and queued outbox records remain isolated by
  account, organization, store, and order context.
- Login/account switching, cross-store switching, owner/platform/admin tools,
  KDS/Pickup, and payment operations are unavailable offline.
- HTTP `401` or `403` never falls back to a cached snapshot.

When connectivity returns, the frontend calls `/auth/me` again and reloads the
store context. Revoked sessions, changed roles, or removed store memberships
therefore replace or invalidate the local snapshot before online work resumes.

## IndexedDB Upgrade Safety

PR7 upgrades `restaurant-pos-offline` from schema 3 to schema 4 by adding only
the `workspaceSnapshots` object store. It does not delete or recreate:

- `menuHeads`
- `menuSnapshots`
- `localDrafts`
- `orderOutbox`

An APK upgrade therefore preserves `QUEUED`, `SUBMITTING`, retryable, and
conflict records. Build metadata and runtime code compare the packaged schema
version with the frontend schema and log a visible diagnostic key on mismatch;
they never clear IndexedDB to repair a mismatch.

No Service Worker is installed. Authentication, order submission, admin writes,
and other mutating APIs are never cached as HTTP responses.

## Verification Checklist

Automated checks:

```bash
cd frontend
npm run test
npm run build

cd ../restaurant-pad-app
scripts/verify-frontend-assets.sh

cd android
./gradlew :app:assembleDebug
```

Required device checks before pilot use:

1. Online login, open one store, load menu, and create a local draft.
2. Queue a test order in a non-production test store and confirm its outbox key.
3. Disable network, kill the app, and cold start bundled mode.
4. Confirm the bundled shell opens, the one cached store/menu loads, and the
   draft plus queued order remain visible without claiming server success.
5. Confirm direct admin, another store, and `/login` are blocked offline.
6. Restore network and confirm session plus store membership are revalidated.
7. Upgrade the APK without clearing app data and verify drafts/outbox, Pad
   pairing, printer test settings, and PAD_DIRECT settings remain present.
8. Set a Local Preview Web App URL and confirm the remote rollback path still
   loads; clear it to return to bundled mode.

The exact packet-loss, backend-restart, multi-Pad, and long-run fault-injection
matrix is deferred to PR8.
