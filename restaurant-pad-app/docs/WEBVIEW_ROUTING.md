# WebView Routing

The current Restaurant frontend uses path-based routes such as:

```text
/stores/1/frontdesk
/stores/1/frontdesk/menu
/stores/1/admin/settings/printing
```

Bundled Android assets do not have physical files for every route. The Pad shell must fall back to `index.html` for app routes.

## PR3 Strategy

The WebView loads:

```text
https://restaurant-pad.local/index.html
```

`FrontendAssetPathHandler` serves files from:

```text
android/app/src/main/assets/web/
```

If a requested path is not a real asset, the handler serves:

```text
web/index.html
```

This lets the React app handle route parsing on the client side.

## Asset Copy POC

After building the current frontend:

```bash
cd frontend
npm run build
```

copy the build into the Pad Android assets:

```bash
bash restaurant-pad-app/scripts/copy-frontend-dist.sh
```

The script copies:

```text
frontend/dist -> restaurant-pad-app/android/app/src/main/assets/web
```

This manual copy is for POC only. A future PR should replace it with a pinned frontend build artifact.
