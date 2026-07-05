# Pad Direct Pending Jobs And Manual Print

This document covers the Android Pad shell pending jobs list and the manual
single-job print happy path.

The panel is not a worker. It does not retry jobs, poll in the background, use
WebSocket subscriptions, renew claim leases, release jobs, or automatically
print new jobs. A user must tap one job at a time.

## Prerequisites

1. Android Pad App can load the Web POS.
2. Android Pad is paired through Web Print Center.
3. The store is configured with printing mode `PAD_DIRECT`.
4. Backend and frontend preview are running for local testing.
5. Android Pad and development computer are on the same LAN.
6. Local printer IP, port, and timeout are configured in the Local Control Panel.

## APIs

The Android shell calls this API to list pending jobs:

```text
GET /api/v1/stores/{storeId}/printing/jobs/pending?limit=25
X-Device-Id: {saved device id}
X-Device-Token: {saved device token}
```

When the user taps `领取并打印`, the Android shell calls:

```text
POST /api/v1/printing/jobs/{jobId}/claim
POST /api/v1/printing/jobs/{jobId}/start-print
GET  /api/v1/printing/jobs/{jobId}/payload
POST /api/v1/printing/jobs/{jobId}/complete
POST /api/v1/printing/jobs/{jobId}/fail
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
Manual job APIs:  http://{developer-lan-ip}:5173/api/v1/printing/jobs/{jobId}/...
```

The Vite preview server proxies `/api` to backend `localhost:8080`.

Bundled Assets mode:

```text
API Base URL: http://{developer-lan-ip}:8080
Pending jobs API: http://{developer-lan-ip}:8080/api/v1/stores/{storeId}/printing/jobs/pending?limit=25
Manual job APIs:  http://{developer-lan-ip}:8080/api/v1/printing/jobs/{jobId}/...
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
9. Confirm the local printer IP, port, and timeout are correct.
10. Tap `Refresh Pending Print Jobs / 刷新待打印任务`.
11. Confirm the panel lists job id, order id, module code, status, created time,
    printer endpoint, and any operator/error message.
12. Tap `领取并打印` on one job.
13. Confirm the printer outputs paper.
14. Confirm Print Center marks the job `PRINTED`.

The manual print flow is:

```text
claim -> start-print -> payload -> assigned printer Android native TCP print -> complete
```

If payload fetch or native TCP print fails after claim, the Android shell calls
the backend `fail` API with an Android error code so Print Center can show the
job as `FAILED`.

`start-print` marks the job `PRINTING`, extends the claim lease, and records
the active Pad attempt before the local TCP socket is opened. Ordinary pending
claim calls cannot steal active `PRINTING` jobs. If `PRINTING` expires, Print
Center warns the operator to confirm whether paper already printed before
reprinting.

Payload also includes the assigned printer endpoint from Print Center. Android
uses `printer_host` and `printer_port` from payload for real PAD_DIRECT jobs.
The Local Control Panel printer test IP is not used as the route for restaurant
order jobs.

## Expected Messages

- `请先配对本机 Pad`: no saved device credentials.
- `暂无待打印任务`: no pending jobs for this store.
- `设备认证失败，请重新配对`: saved device id/token is invalid, inactive, or belongs
  to another setup.
- `任务已被其他 Pad 领取`: another Pad claimed the job first.
- `无法连接后端`: Web App URL, Wi-Fi, preview server, backend, or firewall is not
  reachable.
- `请先配置本机打印机 IP`: the local printer test IP field is empty.
- `打印 payload 缺失`: the backend did not return ESC/POS payload data.
- `打印任务缺少 assigned printer`: payload did not include a valid assigned
  printer endpoint.
- `本机打印失败`: Android could not send the ESC/POS payload to the LAN printer.

## Boundaries

This PR intentionally does not implement:

- retry/backoff
- lease renewal
- background worker
- printer selection
- automatic polling
- batch claim
- release
- production encrypted token storage
- force release / stale reset tooling

The next recommended PR is deeper worker hardening: encrypted credential
storage, force-release/mark-failed operator tooling, module/printer affinity,
and long-running lifecycle testing.
