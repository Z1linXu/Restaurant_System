# STG-009 Phase-A browser-login 403 repair evidence

> Classification: `REPOSITORY_DEPENDENCY_REPAIR_EVIDENCE`
>
> Runtime state: `REPAIR_DEPLOYED_BROWSER_EQUIVALENT_PASS_DEFERRED_BY_OWNER_TWIN_PRIORITY`

> Repository publication: PR #99 is `IN_MAIN`; reviewed head
> `b983b884c2b5eaa3a2b26ce81f1c098d083f4a79` entered `main` through merge
> `1c0289b797207fad50d4327df64a8234e02fe594`. This publication is not runtime
> deployment or browser acceptance evidence.

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

## Verification and remaining Owner gate

`test_nginx_browser_origin_contract.sh` requires both proxy templates to
preserve Host plus port and rejects the old port-stripping header. The normal
cloud/staging shell regressions, Markdown links, secret scan, governance drift,
and independent review remain publication gates.

Repository acceptance is not runtime acceptance. Exact
`1a3f2e761aded38a246460ffa6bc1c6a28a7ca5c` was subsequently bound, preflighted,
built and deployed to isolated Staging. V10-to-V10 continuity, readiness,
isolation, private credential rotation, API acceptance and real-Chrome
browser-equivalent acceptance passed without a 401/403. See
[the browser-equivalent evidence](STG-009_PHASE_A_BROWSER_EQUIVALENT_ACCEPTANCE_EVIDENCE.md).
No STG-005A/B action was repeated. Fresh Owner post-repair manual UI evidence
is still required, and Phase B/Chinatown/Production remains outside this repair.
