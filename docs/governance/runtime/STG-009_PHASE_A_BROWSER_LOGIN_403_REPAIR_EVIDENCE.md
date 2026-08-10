# STG-009 Phase-A browser-login 403 repair evidence

> Classification: `REPOSITORY_DEPENDENCY_REPAIR_EVIDENCE`
>
> Runtime state: `REQUIRES_FRESH_EXACT_SHA_STAGING_DEPLOY_AND_BROWSER_VALIDATION`

## Observation

Owner manual Chrome acceptance used the loopback SSH-tunnel URL and synthetic
Owner identifier. The first failing request was `POST /api/v1/auth/login` with
HTTP 403. No authenticated principal existed and no role, Organization, Store,
workspace, or dashboard authorization branch ran. The subsequent UI text
`没有权限访问 / Access denied` was the frontend's generic 403 presentation, not
proof of a Store-membership denial.

Bounded nginx evidence recorded the browser method/path/status and a 31-byte
compressed response. A secret-free, non-login probe using the same Origin,
Referer, content type and compression reproduced the same 403 transfer shape;
its sanitized decoded body was `Invalid CORS request`. The earlier secret-safe
client did not send a browser Origin and therefore could not cover this defect.

## Root cause and bounded repair

The HTTP and HTTPS nginx proxy templates forwarded `$host`, which removes an
explicit external port. For an SSH-tunnel browser at `127.0.0.1:18080`, Spring
therefore compared the browser Origin with an upstream Host that no longer had
the same port and applied the configured cross-origin allowlist. This rejected
the request before `AuthController.login`.

Both API and WebSocket proxy blocks now forward `$http_host`, preserving the
browser-visible host and explicit port. This is generic proxy identity
preservation: it adds no user, Organization, Store, membership, or environment
hardcode and does not broaden the cross-origin allowlist.

The same package extends the reviewed OPS-001 client with the separately
approval-bound `rotate-owner-credential` action required by the Owner after the
manual credential exposure. It accepts old/new values only through the private
secret FD, changes only the authenticated synthetic Owner credential through
the existing staff-admin contract, and proves the new credential with a second
login/context/logout sequence. No value is printed or retained in evidence.

## Verification and remaining runtime gate

`test_nginx_browser_origin_contract.sh` requires both proxy templates to
preserve Host plus port and rejects the old port-stripping header. The normal
cloud/staging shell regressions, Markdown links, secret scan, governance drift,
and independent review remain publication gates.

Repository acceptance is not runtime acceptance. After merge, the existing
continuous authorization requires a fresh exact-SHA detached Staging release,
formal preflight, V10-to-V10 deploy, readiness and isolation checks. The
compromised synthetic credential must then be rotated through the private
runtime-only channel before browser-equivalent and Owner manual acceptance.
No STG-005A/B action may be repeated, and Phase B/Chinatown/Production remains
outside this repair.
