# STG-008 Synthetic Topology and Source Entry Evidence

> Evidence classification: `STAGING_SYNTHETIC_ENTRY_NO_GO`
>
> Documentation Ground Truth: `origin/main@2ed56b06f37c9257a655ec334f81e31ca4a518a6`
>
> Deployed Staging runtime: `2837ae88e55142c99c6975f8b6575febffc913a1`
>
> Result: `STG-008 = NO_GO`
>
> Unique stop state:
> `STG-008_CREDENTIAL_CONTRACT_ALIGNMENT_WAITING_FOR_OWNER_DECISION`

## Scope and safety boundary

The Owner authorized a bounded Staging-only STG-008 batch. Ground Truth was
re-established before any possible credential or data write. The Coordinator
performed only read-only Git, Staging runtime, Staging database metadata, and
minimum Production continuity observations. Static authority review ran in
parallel; only the Coordinator accessed runtime.

The entry checks found a credential-contract conflict before the guarded
STG-005A plan. Under the Owner's fail-closed instructions, execution stopped
there. No one-shot container was created, no approval artifact was consumed,
and no Staging or Production data was changed.

## Git and exact runtime identity

Fresh `git fetch origin --prune` selected documentation Ground Truth
`2ed56b06f37c9257a655ec334f81e31ca4a518a6`, the merge of PR #83. The diff
from deployed Staging SHA `2837ae88...` to that commit contains only ten
governance/evidence files. It contains no backend, frontend, Android,
migration, deployment-tooling, or runtime-configuration change. Therefore the
already accepted exact Staging runtime remained the correct STG-008 runtime
candidate; a documentation-only main advance did not authorize or require a
redeploy.

The clean detached release, private environment, and running images remained
bound to exact `2837ae88...`. The private environment digest remained
`124eb472bf95bc7311b4977beed9f1700a99ad6e371d6a7d390386c9bdd7e1cc`.
No shared action-blocked marker or blocked lock record was present, and no
scoped AL-003S one-shot container existed.

## Read-only Staging entry observation

| Gate | Observation | Result |
|---|---|---|
| services | exact `db`, `backend`, `nginx` running; restart counts `0`; db healthy; backend/nginx correctly classified `no-healthcheck` | `PASS` |
| HTTP readiness | backend health `200`; frontend `200`; `/ws/info` `200` | `PASS` |
| Flyway | 10 successful rows; highest installed rank/version `10`; no failed row | `PASS`, exact V10 |
| printing | `STAGING_PRINT_MODE=DISABLED`; runtime `APP_FEATURES_PRINTING=false` | `PASS` |
| exposure | only `127.0.0.1:18080` | `PASS` |
| isolation | project/network `restaurant-pos-staging`; PostgreSQL bind under the private Staging state tree; nginx mount bound to the exact release | `PASS` |

The retained Staging container and immutable image identities were unchanged
from STG-007:

| Service | Container ID | Immutable image ID |
|---|---|---|
| `db` | `b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5` | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` |
| `backend` | `eee89acde5a74d4a4774277ec294fe385d6a6e6b6fb25e8d35e8deb731b30bff` | `sha256:8953658ad82579b1f1c6930aab23f0746bc3c0f0ac5156d2f0b345f3f178553f` |
| `nginx` | `ffa9d26d1af0610e7043df0418f0014a0450e2f4681c6758e88829355f4a74fa` | `sha256:e20c5dde0c886b08a4ced546d5ef5f90e6c05b58c466c35882e0074c7b341cd2` |

## Existing synthetic Owner and topology check

Before credential creation, the Coordinator queried only aggregate and
synthetic-scoped Staging metadata. No password, password hash, token, cookie,
Authorization header, raw credential, or unrelated identifier was selected or
retained.

| Observation | Result |
|---|---|
| Synthetic Owner | `NOT_CREATED` |
| organizations / stores / users / credentials | `0 / 0 / 0 / 0` |
| Organization memberships / Store memberships | `0 / 0` |
| STG-005A bootstrap requests, total / completed | `0 / 0` |
| non-synthetic organization/store/user/credential rows | `0 / 0 / 0 / 0` |
| configured literal account identifier collision | none |
| OWNER role contract | exactly one `OWNER` role exists |
| Store identity entry | `stores_id_seq last_value=1, is_called=false`; next generated Store ID is therefore `1` |

This resolves the Store-identity ambiguity without using execution as a probe.
There is no completed bootstrap provenance or existing credential that can be
safely reused.

## Credential contract conflict

The current reviewed bootstrap is intentionally stricter than the general
login lookup:

- every run, Organization, Store, Owner login, and Owner display identity must
  use the `STG005_` synthetic prefix;
- the runtime-only password must contain 12 through 256 characters;
- the password may enter only through non-interactive standard input, is
  encoded through the current BCrypt service, and is never a SQL/bootstrap
  literal, argument, environment field, log, or evidence value;
- an exact replay requires the same completed run topology and a BCrypt match
  for the same runtime-only password.

The Owner-proposed login identifier does not satisfy the synthetic-prefix
guard, and the Owner-provided password convention does not satisfy the
existing minimum length. No email-shaped identifier is required by the API:
the canonical login field is `login_identifier`, and the bootstrap writes the
same safe value to `users.username` and
`user_credentials.login_identifier`.

Changing the requested account identity without Owner confirmation would
change the approved credential contract. Lowering the password minimum or
bypassing the `STG005_` boundary would weaken a reviewed security guard. The
Dependency Repair Auto-Loop therefore does not apply to this Owner-choice
conflict, and no repair was attempted.

## STG-005A and STG-005B results

| Action | Result | Reason |
|---|---|---|
| STG-005A plan | `NOT_STARTED` | known credential contract conflict; do not create a one-shot container merely to reproduce a deterministic guard failure |
| STG-005A execute | `NOT_STARTED` | plan/gate not satisfied; no credential or topology write |
| STG-005A replay | `NOT_STARTED` | no completed create exists |
| STG-005B plan | `NOT_STARTED` | its completed STG-005A/source Store prerequisite does not exist |
| STG-005B execute | `NOT_STARTED` | source prerequisite absent; no menu write |
| STG-005B replay | `NOT_STARTED` | no source graph exists |

Consequently no Synthetic Organization, Owner, source Store, membership,
category, station, item, option, or revision change exists. The reviewed
STG-005B expected graph remains repository authority only: 4 categories,
3 stations, 13 source items, 38 source options, with one revision increment on
create and no revision change on exact replay.

## Production continuity

Production retained runtime `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`
and STG-007 fingerprint
`35765c0287d14c753cc468ce4566e71393fa44c6994ce6dd30a1d2bafbd615c5`.
The permitted container/image/start/restart fields remained byte-for-byte
consistent with STG-007, all restart counts remained `0`, the database
container remained healthy, and Production health returned `200`.

No Production database, Flyway history, Store, menu, order, customer, payment,
environment, or business data was read. No Production build, pull, deploy,
restart, migration, or mutation occurred.

## Repository verification and independent review

| Gate | Result |
|---|---|
| STG-005A/STG-005B focused Maven suite | `PASS`; 13 reports, 53 tests, 0 failures/errors/skips |
| AL-003S launcher and runtime-guard shell suites | `PASS` |
| `git diff --check` | `PASS` |
| changed Markdown local links | `PASS`; 15 files |
| bounded added-line secret scan | `PASS` |
| governance drift and exact-SHA distinction | `PASS` |
| scope scan | `PASS`; documentation/runbooks only |

Independent Agent 6 initially found that this evidence table stated only the
completed bootstrap-request count while downstream authorities correctly used
the observed total count. The table was corrected to retain both values as
`total / completed = 0 / 0`; all final checks were rerun. Agent 6 then returned
`ACCEPT` with no remaining semantic, safety, secret, link, scope, API-contract,
or governance finding.

## Decision and next Owner Gate

`STG-008 = NO_GO`. This is an entry-gate result, not a failed deployment,
migration, bootstrap transaction, or source-menu transaction. Runtime remains
healthy and unchanged.

To resume the same bounded loop, the Owner must explicitly approve:

1. one exact safe login/display identifier satisfying the reviewed `STG005_`
   synthetic naming contract; and
2. a Staging-only runtime password satisfying the existing 12-through-256
   character contract, supplied again only through non-interactive standard
   input and never repeated in repository, evidence, logs, arguments, or
   assistant output.

The resumed batch must recollect fresh readiness and use a distinct digest-
bound approval artifact for every plan/create/replay invocation. The current
authorization does not extend to Owner login, target onboarding, AL-003
validate/execute/replay/clone, restart, Production access, printer/Pad action,
ACT-001, or Chinatown activation.

The unique current stop state is
`STG-008_CREDENTIAL_CONTRACT_ALIGNMENT_WAITING_FOR_OWNER_DECISION`.
