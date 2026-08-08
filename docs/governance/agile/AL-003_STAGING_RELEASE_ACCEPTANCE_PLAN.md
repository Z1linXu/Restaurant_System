# AL-003 Exact-SHA Staging Release and Acceptance Plan

> Status: `AL-003_STAGING_PREFLIGHT_REPAIR_WAITING_FOR_OWNER_REVIEW`
>
> Historical failed candidate: `8f909525781804f61d1da388882f530da358c3c4`
>
> Next candidate SHA: `EVIDENCE_PENDING` after repair merge and fresh Owner approval

## Authorization boundary

This template does not approve SSH, deployment, Flyway execution, Store 1
access, synthetic bootstrap execution, or a real clone. A later Owner approval
must bind one full 40-character merged-main SHA and each runtime command batch.

PR #56 is merged into `main`; the fixed release candidate is now
`8f909525781804f61d1da388882f530da358c3c4`. The read-only preflight is recorded
in
[AL-003 Staging Release Read-only Preflight Evidence](../runtime/AL-003_STAGING_RELEASE_PREFLIGHT_EVIDENCE.md).
It returned `GO` for requesting exact-SHA Owner approval and `NO-GO` for
immediate deployment. The detached candidate release and fresh formal
preflight evidence do not yet exist, and rollback from a V10 database to the
retained V8-era Staging images has no runtime compatibility evidence.

The Owner subsequently approved this SHA. A clean detached release was created,
but formal preflight returned `NO-GO` before build: the path guard requires the
deployment user to enter the initialized PostgreSQL UID-70 mode-0700 data leaf.
Automatic pre-migration recovery restored the old Staging runtime and identity;
Flyway remains V8 and Production remained unchanged. See
[AL-003 Staging Release Attempt Evidence](../runtime/AL-003_STAGING_RELEASE_ATTEMPT_EVIDENCE.md).
The old approval and failed evidence cannot be reused after the required
preflight repair is merged.

The bounded repair validates the initialized PostgreSQL directory as a
protected leaf from its canonical parent and metadata. It does not enter the
leaf, change its UID-70 ownership or `0700` mode, redeploy Staging, or apply a
migration. The repair must merge before a new full-SHA release candidate can be
selected.

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

### Staging Owner login prerequisite

The acceptance prerequisite state is
`AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`. Application deployment alone
must never be reported as `AL-003_STAGING_ACCEPTANCE_READY`.

Retained runtime evidence proves that Staging is still on Flyway V8 and that
the STG-005A bootstrap has never been executed there. Therefore no V9 bootstrap
request or idempotency evidence from that mechanism exists in the evidenced
runtime. This repair performed no database query, so the precise row-level
reason that Owner login is unavailable remains `EVIDENCE_PENDING`: the reports
do not prove whether unrelated synthetic user or membership rows exist.

Repository code and the reviewed runbook establish these exact capability
boundaries:

- STG-005A can create one synthetic Organization, one synthetic source Store,
  one synthetic Owner credential, one active Organization membership, and one
  active source-Store membership.
- It does not create a synthetic target Store or an Owner target-Store
  membership.
- The formal Owner onboarding API can create an inactive target Store and its
  requested `MANAGER`/`FRONTDESK` staff, but that fact is not evidence that the
  bootstrap Owner has an explicit target-Store membership.
- No safe Staging login credential or successful Owner login is present in the
  retained evidence. Credentials must be supplied only at an approved runtime
  checkpoint and never copied from Production or recorded in evidence.

Before AL-003 acceptance, an Owner-approved runtime preparation must prove this
synthetic-only topology without raw SQL or authorization bypass:

`Synthetic Organization -> Source Store -> Target Store -> Synthetic Owner -> active Organization membership -> valid source/target Store access -> Owner login -> validate -> execute`

The runtime gate must record sanitized evidence for the bootstrap run/replay,
the target onboarding result, memberships/access, login success, and Owner API
authorization. If satisfying explicit target-Store membership requires new
bootstrap behavior or a new product/auth contract, stop at an Owner Gate; do
not improvise a database insert. If the existing approved mechanisms are
sufficient, the sequence may proceed later as bounded Staging acceptance
preparation only after explicit Owner runtime-mutation approval.

Forbidden shortcuts include Production accounts or password hashes, manual
membership inserts, developer login switching, hard-coded tokens, disabled
authorization, and copied customer or Store data.

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
- The currently retained `4397f995...` Staging images are not a verified V10
  rollback target. They remain `NO-GO` after migration unless a separately
  approved compatibility gate passes.
- Stop on source ID/profile mismatch, incomplete source evidence, nonempty
  target, printing not disabled, source mutation, target revision other than
  +1, partial graph, duplicate clone, print job creation, or Production change.
- Synthetic Staging success does not prove Production Store 1 data compatibility
  or production deployment. Production still requires approved Store-1-only
  menu evidence, backup evidence, release approval, and a separate deployment
  gate.
- An initialized PostgreSQL data leaf may be non-traversable to the deployment
  user while still being healthy. The repair under review validates that leaf
  without requiring `cd`; it does not authorize a new release until merged and
  bound to a new exact SHA. Do not weaken the data-directory permissions or
  bypass the evidence gate.
- Acceptance remains `NO-GO` while
  `AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`; exact-SHA deployment and
  Flyway V10 alone do not prove the Owner login or clone API topology.
