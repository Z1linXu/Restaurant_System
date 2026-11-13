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

In the configurable `lanzhou` flavor, the optional `Local Preview Web App URL` remains available in the Local Control
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
3. deletes the previous assets directory for the selected flavor only.
4. copies the complete new frontend dist tree for that flavor.
5. verifies `index.html` references existing main JavaScript and CSS files.
6. generates `asset-manifest.json` with every asset size and SHA-256.
7. generates `build-info.json` with build/schema/manifest identity.
8. recomputes every hash before Gradle packages the APK.

Gradle `preBuild` depends on `verifyBundledFrontend`, so Android Studio or a
direct `./gradlew :app:assembleLanzhouDebug` (or `:app:assembleAlloDebug`) also refuses stale, missing, extra, or
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
store address. The `lanzhou` flavor retains its configurable server; the `allo`
flavor fixes the server to `https://pos.allonouilles.com`. The bundled login form also ships with empty account and
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
./gradlew :app:assembleLanzhouDebug
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
8. For the configurable Lanzhou flavor, set a Local Preview Web App URL and confirm the remote rollback path still
   loads; clear it to return to bundled mode.

The exact packet-loss, backend-restart, multi-Pad, and long-run fault-injection
matrix is deferred to PR8.

## Independent Allô Nouilles flavor

`lanzhou` preserves applicationId `com.restaurant.pad`, label `Restaurant Pad`,
version `0.4.0-kitchen-field-fixes` (code 4), existing signing configuration and
configurable server behavior. The default script command still selects it.

`allo` uses applicationId `com.allonouilles.pos`, label `Allô Nouilles POS`,
version `1.0.0` (code 1), the existing merchant logo and a separate Android UID,
data directory, WebView profile, preferences and IndexedDB. It cannot read the
legacy app's token, pairing or drafts. First install is unpaired. Android
backup is disabled by the existing shared manifest. No signing key is added.

Build an installable Debug APK (Release requires separately configured signing):

```bash
BUNDLED_BUILD_VERSION="$(git rev-parse HEAD)" \
  restaurant-pad-app/scripts/build-bundled-apk.sh debug allo
```

Output: `restaurant-pad-app/android/app/build/outputs/apk/allo/debug/app-allo-debug.apk`.
The Allô frontend is built in `restaurant-pad-app/web/allo-dist`, copied only to
`android/app/src/allo/assets/web`, and verified separately. The legacy assets
under `src/main/assets/web` are neither rebuilt nor merged into this APK.

Allô always starts bundled assets at `https://restaurant-pad.local`; cloud
HTTP calls use `https://pos.allonouilles.com` and STOMP uses the corresponding
`wss://` origin. Its server fields are read-only. A variant-specific network
security policy denies cleartext even for Debug, trusts only system CAs, and
retains normal certificate validation. WebView navigation/resource checks and
CSP restrict it to the bundled origin and the Allô cloud; native device HTTP
requests use the fixed API origin and do not follow redirects. Native printer
TCP remains the existing PAD_DIRECT path, governed by cloud assignments and
device proofs; these restrictions do not create a new print lifecycle.

The Allô backend profile must permit the exact bundled Origin
`https://restaurant-pad.local`. Auth, Organization/Store scope and authenticated
STOMP remain required. No server printing-mode change or real device pairing is
part of APK construction. Unpaired status is handled by the existing worker
and Print Center. Hardware printing requires separate on-site acceptance.

Focused native verification, using a dedicated emulator:

```bash
cd restaurant-pad-app/android
./gradlew :app:testAlloDebugUnitTest :app:connectedAlloDebugAndroidTest
```

Instrumentation tests verify the real WebView login shell, loaded logo, empty
credentials, runtime API/WSS, independent app sandbox, and no cleartext or
preinstalled device token. They do not pair a device or print an order.
Minimum supported Android is 8.0 (API 26); the delivery report distinguishes
the tested emulator OS from the declared minimum.

SockJS endpoint resolution uses the injected HTTPS API origin for its initial
`/ws/info` request as well as WSS, rather than the local asset origin. Frontdesk,
KDS and Pickup read the current bearer token for each STOMP reconnect. Normal
browser requests still resolve against their own origin. Optional emulator
read-only acceptance can consume `files/qa-login.json` injected into the Allô
sandbox at test time; credentials are never included in source or either APK.
The test reads workspace, Frontdesk, menu and Print Center without submitting,
pairing or printing, then removes its runtime input.
