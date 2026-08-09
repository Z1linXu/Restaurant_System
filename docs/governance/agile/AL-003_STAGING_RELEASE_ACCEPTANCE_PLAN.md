# AL-003 Exact-SHA Staging Release and Acceptance Plan

> Capability state: `STG-008_CREDENTIAL_CONTRACT_ALIGNMENT_WAITING_FOR_OWNER_DECISION`
>
> Historical failed candidate: `8f909525781804f61d1da388882f530da358c3c4`
>
> Current exact deployed Staging SHA: `2837ae88e55142c99c6975f8b6575febffc913a1`
>
> Governance packages: PR #72 and PRs #60-#71 are `IN_MAIN`; repository merge is not runtime evidence
>
> Runtime checkpoint: `STG-007=PASS`; V10-to-V10 deploy, repaired readiness, runtime collection, same-image restart and post-restart verification all `PASS`

## Authorization boundary

This template does not approve Store 1 access, synthetic bootstrap execution,
credentials, login or a real clone. The Owner separately approved a bounded
V10-aware STG-007 continuation through exact redeploy, runtime collection and
same-image restart. After PR #82 merged the readiness/fail-closed repair, a
fully fresh chain deployed exact `2837ae88...` and passed every STG-007 gate.
That authority is complete and consumed; it does not extend to STG-008,
acceptance data writes, Owner/API actions or Production mutation.

The Owner later authorized the bounded STG-008 synthetic topology/source
batch. Its read-only entry checks retained exact runtime `2837ae88...`, Flyway
V10, health `200/200/200`, printing/isolation, and unchanged Production
continuity. Staging contained zero Organization, Store, user, credential,
membership, or bootstrap-request rows; `stores_id_seq last_value=1,
is_called=false` safely proves the next Store can be ID `1`. The batch stopped
before `bootstrap-plan` because the requested account convention does not
satisfy the reviewed `STG005_` identity and 12-through-256 password contract.
No one-shot, credential, synthetic write, source graph, login, or API action
occurred. See
[STG-008 entry evidence](../runtime/STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md).

Historically, PR #56 entered `main` and the fixed release candidate was
`8f909525781804f61d1da388882f530da358c3c4`. The read-only preflight is recorded
in
[AL-003 Staging Release Read-only Preflight Evidence](../runtime/AL-003_STAGING_RELEASE_PREFLIGHT_EVIDENCE.md).
It returned `GO` for requesting exact-SHA Owner approval and `NO-GO` for
immediate deployment. This paragraph describes that historical candidate;
rollback from a V10 database to the retained V8-era Staging images still has no
runtime compatibility evidence.

The Owner subsequently approved this SHA. A clean detached release was created,
but formal preflight returned `NO-GO` before build: the path guard requires the
deployment user to enter the initialized PostgreSQL UID-70 mode-0700 data leaf.
Automatic pre-migration recovery restored the old Staging runtime and identity;
Flyway remains V8 and Production remained unchanged. See
[AL-003 Staging Release Attempt Evidence](../runtime/AL-003_STAGING_RELEASE_ATTEMPT_EVIDENCE.md).
The old approval and failed evidence cannot be reused. PR #59's later merge
changes the candidate code and requires a new exact-SHA approval/evidence set.

STG-006 subsequently selected current
`origin/main@33c6e3c52aa40793f6bb861101c16ccdd1b85b5b` and completed only the
Owner-authorized passive observation. Fresh evidence confirmed the retained
runtime remains `4397f995...` / Flyway V8, isolated, healthy, and printing
disabled, with unchanged Production continuity. The candidate release is
absent and no environment rotation, image build, container lifecycle, Flyway,
login, or data action occurred. See
[STG-006 Exact-Main Passive Preflight Evidence](../runtime/STG-006_EXACT_MAIN_PREFLIGHT_EVIDENCE.md).

OPS-001 supplies the reviewed repository helpers for STG-007: exact detached
release/four-field private-env rotation, same-container restart plus sanitized
Flyway evidence, and a secret-FD Owner/API acceptance client. They preserve the
existing exact-SHA, approval, lock, redaction and runtime boundaries. No helper
may infer its own runtime authority; every action requires a distinct exact-SHA/
environment/action-bound Owner approval. Runtime use through PR #80's exact
`39fa284b...` redeploy and repaired readiness passed; PR #81 then repaired the
canonical `true` token and entered main at `63600b13...`. That historical
continuation passed Flyway/runtime collection but ended with restart `NO_GO`.
PR #82 entered main at exact `2837ae88...` with the bounded readiness and
nonzero-exit repair. A fresh continuation deployed that exact SHA, kept Flyway
V10/no-pending, and passed readiness, runtime collection, same-image restart
and post-restart verification. No synthetic acceptance action followed.

PR #59 merged the earlier private-leaf bounded repair at
`c3956592da8a33092ab745c7cc6aac05e9babfa7`. It validates the initialized
PostgreSQL directory as a protected leaf from its canonical parent and
metadata without entering the leaf or changing its UID-70 ownership / `0700`
mode. This is repository evidence only: Staging remains on the retained older
runtime until a new exact-SHA deployment is separately approved.

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
5. JPA validation must pass. After any restart, a fixed bounded window must
   converge to HTTP 200 for `/api/v1/system/health`, `/`, and `/ws/info` before
   exact container/image/Flyway/project invariance and PASS evidence can pass.
6. Public binding, non-disabled printing, Production container change, resource
   breach, digest mismatch, or migration/schema error is an immediate NO-GO.

## Synthetic data prerequisites

- Use the approved STG-005A mechanism for synthetic Organization/Owner/source
  topology and the formal onboarding API for an inactive, empty target Store.
- The synthetic source Store must be created as ID `1` because the reviewed
  Chinatown Profile binds source Store ID `1`; stop if runtime allocation does
  not match rather than changing IDs manually.
- Its complete synthetic St-Denis menu contract must be built from the reviewed
  STG-005B synthetic-only manifest through the guarded non-web application
  service. Repository seed rows, ad-hoc row-by-row API calls, raw SQL, and
  Production database copies are not acceptable substitutes.
- STG-005A creates identity/access topology only. STG-005B creates the source
  menu in one application transaction after its independent write checkpoint.
- Keep target printing disabled and verify no printer/device/table/order data.

### Staging Owner login prerequisite

The acceptance prerequisite state is
`AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`. Application deployment alone
must never be reported as `AL-003_STAGING_ACCEPTANCE_READY`.

Current runtime evidence proves that Staging is on exact Flyway V10 and that
the STG-005A bootstrap has never been executed there. The STG-008 entry used
synthetic-scoped and aggregate read-only queries and proved zero Organization,
Store, user, credential, Organization-membership, Store-membership, and V9
bootstrap-request rows. Synthetic Owner is therefore `NOT_CREATED`; no
password/hash/token or unrelated identifier was read or retained.

Repository code and the reviewed runbook establish these exact capability
boundaries:

- STG-005A can create one synthetic Organization, one synthetic source Store,
  one synthetic Owner credential, one active Organization membership, and one
  active source-Store membership.
- It does not create a synthetic target Store. The formal Owner onboarding API
  can create the inactive target Store and its requested
  `MANAGER`/`FRONTDESK` staff.
- `StoreAccessService` already grants an active Organization `OWNER` access to
  every Store in that Organization. The bootstrap Owner therefore does not
  need a redundant target-Store membership after onboarding; runtime evidence
  must prove inherited access through workspace/context and Owner APIs.
- No safe Staging login credential or successful Owner login is present in the
  retained evidence. Credentials must be supplied only at an approved runtime
  checkpoint and never copied from Production or recorded in evidence.

Before AL-003 acceptance, an Owner-approved runtime preparation must prove this
synthetic-only topology without raw SQL or authorization bypass:

`Synthetic Organization -> Source Store -> Target Store -> Synthetic Owner -> active Organization membership -> valid source/target Store access -> Owner login -> validate -> execute`

The runtime gate must record sanitized evidence for the bootstrap run/replay,
the target onboarding result, Organization membership and inherited target
access, login success, and Owner API authorization. Existing approved
mechanisms are sufficient for the identity/access topology; no raw membership
insert or new authorization behavior is planned. The sequence may proceed only
after explicit Owner runtime-mutation approval.

Forbidden shortcuts include Production accounts or password hashes, manual
membership inserts, developer login switching, hard-coded tokens, disabled
authorization, and copied customer or Store data.

## End-to-end runtime sequence and current gate split

Steps 1-2 and the infrastructure restart portion of step 9 were completed by
STG-007 at exact deployed `2837ae88...`; they must not be repeated from this
plan without new authority. STG-008 received runtime authority, but its
read-only entry stopped before step 3 at the credential-contract Owner Gate.
After explicit credential alignment, a resumed batch may cover only the
synthetic topology/source work in steps 3 and 5, with fresh readiness and
distinct plan/create/replay approvals. Steps 4 and 6-9's
login/onboarding/clone acceptance remain later gates. The retained end-to-end
sequence is:

1. Create a fresh detached Staging release and fresh env/preflight evidence
   bound to the exact SHA; confirm `restaurant-pos-staging`, only
   `127.0.0.1:18080`, independent state, resources, Production continuity, and
   global printing `DISABLED`.
2. Deploy only Staging; apply V9/V10; verify Flyway V1-V10, JPA validation,
   health, second startup with no new migration, and Production continuity.
3. Execute STG-005A once with a new `STG005_` run ID and runtime-only synthetic
   credential, then replay the same request to prove idempotency. Record only
   sanitized IDs/status and confirm source Store ID is exactly `1`.
4. Log in as the synthetic Owner and verify Organization/source workspace and
   Owner capabilities. Never retain the token or credential in evidence.
5. Run the reviewed STG-005B non-web command in default planning mode, then at
   its separate write checkpoint apply the immutable synthetic source graph.
   Verify 4 categories, 3 stations, 13 source items, 38 source options, one
   revision increment, an exact replay with no revision change, and printing
   disabled. Do not improvise row-by-row HTTP writes or raw SQL.
6. Call the formal onboarding API with a fresh key to create the inactive
   synthetic target and target-only Manager/Frontdesk accounts. Replay the same
   request, verify no duplicate Store/staff, and confirm the Owner sees/opens
   the target through Organization membership without an explicit target Owner
   membership.
7. Run read-only `/menu-clone/validate`; review structured diagnostics and stop
   on any source/profile drift or nonempty target.
8. At the separately recorded execute checkpoint, call `/menu-clone` with a
   fresh unrecorded key; verify result, replay, source invariance, target
   revision, counts, absence of excluded side effects, and printing disabled.
9. Stop/start only the Staging Compose project; bounded readiness must prove
   backend health, frontend root and `/ws/info` HTTP 200 before exact identity/
   Flyway/project checks and PASS evidence. Any non-PASS after stop begins must
   persist blocked state before cleanup. Then verify persisted topology/menu,
   login/access and replay. Production remains untouched.

The reusable Synthetic St-Denis manifest/application path entered `main` via
PR #62. It remains repository capability only: it is not available to runtime
until a fresh exact SHA is selected and the Owner separately approves the
mutation sequence.

The `IN_MAIN` AL-003S package adds the missing guarded one-shot launcher and
publishes the exact command, evidence, and rollback plan in
[AL-003S Staging Acceptance Preparation](AL-003S_STAGING_ACCEPTANCE_PREPARATION.md).
It defaults to validation and requires explicit runtime and write gates. This
does not satisfy the dependency merge or Owner runtime-approval conditions.

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
sanitized HTTP outcomes, canonical V9/V10 request evidence, supplementary
audit action when available, resources, and Production continuity.
Never record secrets, raw idempotency keys, credentials, tokens, complete menu
payloads, or customer data. An empty or partial redirected action file remains
failure context and can never be promoted to PASS merely because health later
recovers.

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
  user while still being healthy. PR #59's merged repair validates that leaf
  without requiring `cd`; the merge itself does not authorize a release. Bind
  a fresh exact SHA and evidence after this governance package merges. Do not
  weaken the data-directory permissions or bypass the evidence gate.
- Acceptance remains `NO-GO` while
  `AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`; exact-SHA deployment and
  Flyway V10 alone do not prove the Owner login or clone API topology.
- A same-image restart is `NO-GO` unless all three loopback endpoints converge
  inside the reviewed bound, exact container/image/Flyway/project identities
  remain unchanged, complete PASS evidence is emitted, and every post-mutation
  non-PASS path has durable blocked-state semantics.

## Capability dependency state

`STG-008_CREDENTIAL_CONTRACT_ALIGNMENT_WAITING_FOR_OWNER_DECISION`
