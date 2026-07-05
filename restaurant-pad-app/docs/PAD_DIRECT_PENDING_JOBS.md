# Pad Direct Pending Jobs Viewer

This document covers the Android Pad shell read-only pending jobs viewer.

The viewer is not a worker. It does not claim jobs, fetch payloads, print,
complete/fail/release jobs, retry jobs, poll in the background, or use
WebSocket subscriptions.

## Prerequisites

1. Android Pad App can load the Web POS.
2. Android Pad is paired through Web Print Center.
3. The store is configured with printing mode `PAD_DIRECT`.
4. Backend and frontend preview are running for local testing.
5. Android Pad and development computer are on the same LAN.

## API

The Android shell calls:

```text
GET /api/v1/stores/{storeId}/printing/jobs/pending?limit=25
X-Device-Id: {saved device id}
X-Device-Token: {saved device token}
```

The request uses device authentication only. It does not use the Web bearer
token and does not read WebView `localStorage`.

## API Base Selection

Local Preview mode:

```text
Web App URL: http://{developer-lan-ip}:5173
Pending jobs API: http://{developer-lan-ip}:5173/api/v1/stores/{storeId}/printing/jobs/pending?limit=25
```

The Vite preview server proxies `/api` to backend `localhost:8080`.

Bundled Assets mode:

```text
API Base URL: http://{developer-lan-ip}:8080
Pending jobs API: http://{developer-lan-ip}:8080/api/v1/stores/{storeId}/printing/jobs/pending?limit=25
```

## Manual Test Flow

1. Open the Android Pad App.
2. Log in to the Web POS.
3. Open Web Print Center.
4. Pair this Pad if it is not already paired.
5. Set printing mode to `PAD_DIRECT`.
6. Submit an order.
7. Confirm Print Center shows `PENDING` jobs.
8. Long press inside the Android app to open Local Control Panel.
9. Tap `Refresh Pending Print Jobs / 刷新待打印任务`.
10. Confirm the panel lists job id, order id, module code, status, created time,
    printer endpoint, and any operator/error message.

No paper should print from this viewer.

## Expected Messages

- `请先配对本机 Pad`: no saved device credentials.
- `暂无待打印任务`: no pending jobs for this store.
- `设备认证失败，请重新配对`: saved device id/token is invalid, inactive, or belongs
  to another setup.
- `无法连接后端`: Web App URL, Wi-Fi, preview server, backend, or firewall is not
  reachable.

## Boundaries

This PR intentionally does not implement:

- claim
- payload fetch
- native order printing
- complete/fail/release
- retry/backoff
- lease renewal
- background worker
- printer selection
- production encrypted token storage

The next recommended PR is a manual claim + print happy path, still without a
long-running worker.
