# AL-003 Exact-SHA Staging Release and Acceptance Plan

> Status: `PLAN_ONLY_NOT_AUTHORIZED`
>
> Candidate SHA: `<PR_F_MERGED_MAIN_FULL_40_SHA>`
>
> Runtime access performed: `NO`

## Authorization boundary

This template does not approve SSH, deployment, Flyway execution, Store 1
access, synthetic bootstrap execution, or a real clone. A later Owner approval
must bind one full 40-character merged-main SHA and each runtime command batch.

## Fixed Staging identity

- Release: `/srv/restaurant-pos/staging/releases/<SHA>`.
- Compose project: `restaurant-pos-staging`.
- Host bind: only `127.0.0.1:18080`.
- Backend/frontend images: `staging-<SHA>`.
- Independent Staging PostgreSQL state; never Production data or mounts.
- Global and target-Store printing: `DISABLED`.
- Only `STG005_` synthetic identities; no real credentials, printers, Pads,
  customers, or orders.

## Preflight and release gates

1. Owner approves the exact merged-main SHA and read/write command batches.
2. Existing Staging guards verify release path, Compose project, private bind,
   environment/evidence digests, independent database path, resource threshold,
   and Production continuity.
3. Build backend then frontend serially; start only `db`, `backend`, and `nginx`.
4. Flyway V1-V10 must be successful. Second startup must not rerun V10.
5. JPA validation, `/`, `/api/v1/system/health`, and `/ws/info` must pass.
6. Public binding, non-disabled printing, Production container change, resource
   breach, digest mismatch, or migration/schema error is an immediate NO-GO.

## Synthetic data prerequisites

- Use the approved STG-005A mechanism for synthetic Organization/Owner/source
  topology and the formal onboarding API for an inactive, empty target Store.
- Source Store ID `1` and its complete menu contract must be proven by approved
  Staging evidence. Do not substitute repository seed data.
- Build source categories, stations, items, and options only through supported
  application APIs. The bootstrap does not create a source menu.
- Keep target printing disabled and verify no printer/device/table/order data.

## Acceptance sequence

1. Capture sanitized before-counts and source/target revisions.
2. Validate: confirm the reviewed profile summary and zero changes to menu,
   V10 request, audit, and revision state; response contains no internal ID map.
3. At a separate Owner checkpoint, execute once with a new key whose raw value
   is never recorded in evidence.
4. Verify four categories, three stations, seventeen items, the promoted option
   count, exact ordering/prices/profile rules, target revision +1, and complete
   source invariance.
5. Replay the exact request/key and confirm the same durable summary,
   `replayed=true`, no additional rows, and no revision change.
6. Confirm sanitized V10 evidence and no printer/device/table/order/payment/KDS/
   inventory side effect.
7. Stop/start only the Staging project; confirm health, Flyway history,
   persisted graph, and replay. Never use `down -v`.

FAILED fault injection and same-key/different-profile scenarios should remain
automated-test evidence unless the Owner separately approves a bounded Staging
fault-injection plan.

## Evidence contract

Record exact SHA, image IDs, container names, network/private bind, PostgreSQL
version, Flyway V1-V10, JPA/health results, before/after counts and revisions,
sanitized HTTP outcomes, audit action, resources, and Production continuity.
Never record secrets, raw idempotency keys, credentials, tokens, complete menu
payloads, or customer data.

## Rollback and NO-GO

- Application rollback may use only a previously verified image that tolerates
  V10. V10 remains append-only; do not use Flyway clean/repair or schema drop.
- Stop on source ID/profile mismatch, incomplete source evidence, nonempty
  target, printing not disabled, source mutation, target revision other than
  +1, partial graph, duplicate clone, print job creation, or Production change.
- Synthetic Staging success does not prove Production Store 1 data compatibility
  or production deployment. Production still requires approved Store-1-only
  menu evidence, backup evidence, release approval, and a separate deployment
  gate.
