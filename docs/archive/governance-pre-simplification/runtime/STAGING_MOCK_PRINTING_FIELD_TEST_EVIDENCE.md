# Staging MOCK Printing Field-Test Evidence

> Status: `PASS`
>
> Package: `STAGING_MOCK_PRINTING_FIELD_TEST_ENABLEMENT`
>
> Date: 2026-08-11, America/Toronto

## Authority and retained boundaries

The Owner authorized a bounded Staging-only runtime package inside
`OWNER_FIELD_TEST_AND_BUG_FIX_LOOP`. Production remained read-only and was
never configured, restarted, queried for business data or contacted for
printing. `REAL`, `PAD_DIRECT`, printer endpoints, home-printer binding, public
printer ports and Pad pairing remained prohibited.

Repository repair PR #114 entered `main` at
`1dd036737f6cf41c0558f14b7f8343144f718b5a`. The repair adds an
environment-neutral application mode allowlist and endpoint-configuration
policy. Shared defaults preserve existing behavior; isolated Staging is fixed
to exactly `DISABLED,MOCK`, with endpoint configuration disabled. Agent 6
returned `ACCEPT` on exact final repair head
`58d5c54a21844636e95c5480d6218ccf92070d2c` after its earlier mode-read race
finding was corrected.

## Exact-SHA Staging enablement

The dedicated Staging repository imported exact `main`, preserved unrelated
refs and `FETCH_HEAD`, created a clean detached release, and rotated only the
four standard release identity fields through the reviewed one-use approval
path. The private environment was then atomically restricted to:

- `STAGING_PRINT_MODE=MOCK`;
- `STAGING_PRINTING_FEATURE_ENABLED=true`;
- `STAGING_ALLOWED_PRINTING_MODES=DISABLED,MOCK`;
- `STAGING_PRINTER_ENDPOINT_CONFIGURATION_ENABLED=false`.

Every other private environment field was preserved by a filtered-content
digest check. Formal same-host preflight passed before the serial build/start.
Staging deployed exact
`1dd036737f6cf41c0558f14b7f8343144f718b5a`; Flyway validated ten successful
migrations, retained V10, and ran no migration. Backend and nginx were
recreated at the exact SHA, while the existing Staging database container and
data remained in place. All three Staging containers have restart count zero.

The Store-scoped Owner API then changed only Store 1 printing mode from
`DISABLED` to `MOCK`. Final runtime state is Printing feature enabled, Store
mode `MOCK`, four enabled endpoint-free logical printers, and three enabled
assignments for `FRONTDESK_RECEIPT`, `GRAB`, and `HOT_KITCHEN`.

## Automated MOCK pipeline smoke

A single non-PII synthetic pickup order exercised the normal application
pipeline. The submit response persisted the order in `preparing` while zero
Print Jobs existed immediately after the response. The durable outbox then
processed all three automatic routes:

`order commit -> six persisted outbox events -> routing -> PrintJob -> renderer
-> assignment -> MOCK dispatch -> rendered snapshot -> PRINTED -> outbox completed`

Results:

| Flow | GRAB | FRONTDESK_RECEIPT | HOT_KITCHEN |
|---|---:|---:|---:|
| initial submit | `PASS` | `PASS` | `PASS` |
| submitted-order update | `PASS` | `PASS` | `PASS` |

A separate GRAB manual reprint also passed. Seven Print Jobs were created in
total; all seven are `PRINTED`, all seven contain non-blank rendered snapshots,
failed jobs are zero and aggregate job retry count is zero. Six outbox events
are `COMPLETED`, with zero pending and zero failure attempts. The synthetic
order was cancelled after verification.

MOCK logged seven observable ticket dispatches and never entered physical
printer transport. The private, mode-`0600` evidence contains the complete
sanitized rendered text for every ticket plus per-ticket SHA-256 fingerprints:

`/srv/restaurant-pos/staging/evidence/staging-mock-printing-smoke-1dd036737f6cf41c0558f14b7f8343144f718b5a.txt`

Evidence SHA-256:
`e225823bbf61e7b88c2485ebef889e606db652c71e51d2744e5e8b930aafdc7f`.

The persisted-outbox failure regression remains the supported retry contract:
an unexpected dispatcher failure leaves the event `PENDING`, increments the
attempt count and schedules backoff. A failure was not injected into the live
Store topology because that would require unnecessary configuration mutation.
Once a Print Job exists, operator recovery is manual reprint rather than an
automatic physical retry. This runtime returned the committed order before
outbox dispatch and the repository failure regression passed, so a downstream
printing failure cannot roll back order submission.

## Owner-visible verification

An authenticated browser check reached
`/stores/1/admin/settings/printing` through the existing loopback tunnel. The
page did not contain `Feature Disabled: PRINTING`; it showed MOCK as the active
mode, all three routed module types, recent `PRINTED` jobs and ticket preview /
reprint controls. The browser session was logged out and closed. No credential
value entered evidence or Git.

## Health, continuity and prohibited data

- Staging frontend, backend system health and WebSocket info returned
  `200/200/200`.
- Production frontend, backend system health and WebSocket info returned
  `200/200/200` before final closure.
- Production retained the exact backend/database/nginx container and image
  identities, original start times and restart count zero.
- Production runtime remained
  `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` / Flyway V7; no Production
  database read or write occurred.
- No customer/PII, historical order/item, payment, credential value,
  password/hash, token/cookie/session secret, raw environment, database secret,
  printer secret or device credential was copied into Git or evidence.
- Staging credentials remain independent. The approved server-private
  retrieval path remains
  `/srv/restaurant-pos/staging/state/twin001-staff-credentials-v1.json`, mode
  `0600`.

## Result and stop

`STAGING_MOCK_PRINTING_FIELD_TEST_ENABLEMENT = PASS`.

The next field-test item is Owner manual use of representative St-Denis POS
orders and inspection of GRAB, frontdesk and hot-kitchen ticket previews in
the Printing Settings page. Physical printer binding remains a separate Owner
runtime gate and does not block MOCK field testing.

Unique stop:
`STAGING_MOCK_PRINTING_VERIFIED_OWNER_FIELD_TEST_CONTINUES`.
