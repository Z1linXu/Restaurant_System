# STG-007 Exact-SHA V10 Continuation Evidence

> Evidence classification: `STAGING_INFRASTRUCTURE_ACCEPTANCE`
>
> Runtime candidate: `2837ae88e55142c99c6975f8b6575febffc913a1`
>
> Result: `STG-007 = PASS`
>
> Runtime boundary: Owner-authorized Staging-only V10-to-V10 continuation.
> Production was limited to container identity/start/restart metadata and the
> health endpoint. No synthetic write, credential, login, Store read, clone,
> printer/Pad action, Production mutation, or STG-008 action occurred.

## Ground Truth and dependency repair

PR #82 merged the bounded OPS-001 restart-readiness/fail-closed repair into
`main` at `2837ae88e55142c99c6975f8b6575febffc913a1`. The reviewed repair waits
for backend health, frontend root, and `/ws/info` HTTP 200 after the ordered
same-container start sequence; retries only bounded startup-transient states;
and persists blocked state on every nonzero post-mutation exit before cleanup.
Its focused/regression checks and independent Agent 6 review passed.

Because that merge changed `main`, every `63600b13...` release, environment,
preflight, readiness, collection, and restart authorization was treated as
expired. The final continuation began with a fresh fetch and independently
verified that both the local detached source and `origin/main` were exactly
`2837ae88e55142c99c6975f8b6575febffc913a1`. The dedicated Staging bare
repository imported only that pinned object and advanced its remote-main ref by
compare-and-swap; no clone, floating fetch, or Production checkout was used.

## Fresh entry baseline

Before release rotation, the retained Staging runtime was exact
`63600b13b10a5549d9095a03c94e69a9f880af9f` with environment digest
`0fbcd4038b203cc9ca68f78777eb7dc6ac08be6a67fd26393d5ac4aba8947a94`.
Fresh observation proved:

- repository migrations and installed Flyway history were exactly V1-V10;
- Flyway had 10 successful rows, maximum version 10, no failed or pending row,
  and digest
  `b07616f0316934f32f83b4d1e242bccb3d97a3a4e3258c1e02f780ba04d9ec11`;
- Staging returned `200/200/200` for backend health, frontend root, and
  `/ws/info`;
- printing remained `DISABLED` with runtime feature flag `false`;
- project, loopback `127.0.0.1:18080`, private state, network, and mounts were
  isolated from Production;
- no shared blocked marker was present; and
- Production retained runtime `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`
  had unchanged container/image identities, original start times, restart
  counts `0`, and health 200.

This V10 baseline was accepted as the continuation entry condition. No attempt
was made to return Staging to V8 or manufacture V8-to-V9-to-V10 evidence.

## Exact release, preflight, and deployment

The reviewed bootstrap created a clean detached mode-0700 release from exact
Git source and atomically rotated only the four release identity fields in the
private environment. All secret, database, isolation, resource, profile, and
printing fields remained byte-identical.

| Artifact | SHA-256 / identity | Result |
|---|---|---|
| exact release, build source, and deployed SHA | `2837ae88e55142c99c6975f8b6575febffc913a1` | `PASS` |
| current private environment | `124eb472bf95bc7311b4977beed9f1700a99ad6e371d6a7d390386c9bdd7e1cc` | `PASS` |
| release/env approval | `438a53d433a00c754e93c82a98c304347ac7209196415146af2ba0884347cacc` | consumed once |
| environment recovery record | `91417d6e42639487ff0fe12d3c70ef47a90b6598aa2151ff0e7a24418a32bfd4` | `COMMITTED` |
| V10 continuation entry | `8d744fa86482af5dc045aa6020c80a99e725a4d010ce66519ca0a7695ad4eee8` | `PASS` |
| formal preflight | `7174a295d6f4696e766100001e5209f03dc055db6db213a3a5fd0a3365158236` | `PASS` |
| post-deploy readiness | `19a8fec2bb0e8fe8540ef2ff62f84489c6bb59cf019dfddb2e3004a8341b58f5` | `PASS` |
| runtime collection approval | `b106bc1011af80ffb5e7609c4952c5373453e8cd920727371de483cb1269d6ec` | consumed once |
| runtime collection | `03337e71cb1e1476411db9ac5020e34cc44376888c1310777d1876b0703c2d14` | `PASS` |
| restart readiness | `6392783fb2944fb473727b754e4ceb293c4cbd1221315eedf678436511106257` | `PASS` |
| restart approval | `ea82c72237927defcb5a9c9414a8827a839e6813c8b5ad6cb23ddf0737914059` | consumed once |
| same-image restart | `2208d8cafea8ec2c3c78546ab23f4fb9c0c43435a334d3dd0ab37e3445745653` | `PASS` |

Formal preflight explicitly bound the continuation entry digest and recorded
`repository migrations = V1-V10`, `current schema = V10`, and
`pending migrations = NONE`. Path, release cleanliness, retained-listener
ownership, resource, private environment, project, printing, Production-root
isolation, and serial-build gates passed. No V11+, dirty history, failed row,
or checksum mismatch was present.

The deploy built backend and frontend serially from the clean detached release,
then started only project `restaurant-pos-staging`. It did not pull images or
operate on Production. Backend startup validated the already-current V10 schema
without applying a new migration. Repaired readiness distinguished
`NO_HEALTHCHECK` from configured-invalid/unhealthy state and required all three
loopback endpoints to return 200.

## Runtime and same-image identity

Runtime collection and post-restart evidence retained these exact identities:

| Service | Container ID | Immutable image ID |
|---|---|---|
| `db` | `b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5` | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` |
| `backend` | `eee89acde5a74d4a4774277ec294fe385d6a6e6b6fb25e8d35e8deb731b30bff` | `sha256:8953658ad82579b1f1c6930aab23f0746bc3c0f0ac5156d2f0b345f3f178553f` |
| `nginx` | `ffa9d26d1af0610e7043df0418f0014a0450e2f4681c6758e88829355f4a74fa` | `sha256:e20c5dde0c886b08a4ced546d5ef5f90e6c05b58c466c35882e0074c7b341cd2` |

The Staging project fingerprint before and after restart was
`811f566cee58a67e47d2707376b0d452e0592fe6934512cef595aac8474a84e8`.
The reviewed helper used only:

```text
stop nginx backend db
start db
start backend
start nginx
```

It performed no build, pull, recreate, environment rotation, migration, or
Production action. The repaired bounded readiness window reached
`200/200/200`; complete PASS evidence was then emitted before mutation state
was cleared. Container IDs, image IDs, release SHA, environment digest, Flyway
history, and project fingerprint remained identical. Restart counts remained
zero and no blocked marker was created.

Independent post-restart observation reconfirmed `200/200/200`, Flyway
`count=10/max=10/failed=0`, printing `DISABLED/false`, loopback-only exposure,
network `restaurant-pos-staging_restaurant-pos`, PostgreSQL state
`/srv/restaurant-pos/staging/state/postgres`, and release-scoped nginx mount.
Two initial ad hoc observer assertions used an incorrect assumed network name
and compared Docker's 12-character `ps -q` Production ID with the retained
64-character ID. Both stopped read-only scripts without runtime mutation; the
corrected exact metadata checks then passed. They do not weaken or replace the
reviewed runtime evidence.

## Production continuity and exclusions

Production fingerprint
`35765c0287d14c753cc468ce4566e71393fa44c6994ce6dd30a1d2bafbd615c5`
and the following permitted continuity fields remained unchanged:

| Service | Container ID | Immutable image ID | Restart | Original start |
|---|---|---|---:|---|
| `db` | `c2ab37fec6ac966e77a1d8ab7aa41d1da294b9ac57b1a21ce18904d04cfae91e` | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` | 0 | `2026-07-11T12:09:37.936437539Z` |
| `backend` | `e5027dc087097a009286ed1d5e8d8e5be297781d228ffeb73643b7354160286a` | `sha256:36daa6697ff7204d88e831315e356241721a956c8513551cf919937cce260792` | 0 | `2026-07-24T19:44:29.149398323Z` |
| `nginx` | `a5c37d6f289c09e9b7a06a7275513b287f80ccbc07fc5f2538ba54b105196849` | `sha256:781cb93ee4e821a827890f57de58a9f4286371bfc43aef9b4ad8a9507536eca7` | 0 | `2026-07-24T19:44:29.346691651Z` |

Production health remained 200. No Production build, pull, restart, deploy,
Flyway, database/business-data read, Store 1/menu/order/customer/payment read,
or mutation occurred.

No STG-005A bootstrap, STG-005B source creation, synthetic credential, Owner
login, target onboarding, validate/execute/replay/clone, printer configuration,
Pad pairing, ACT-001, or Chinatown activation occurred.

## Result and next Owner Runtime Gate

Every STG-007 PASS condition is satisfied:

- latest selected exact main was deployed;
- formal V10-to-V10 preflight, repaired readiness, runtime collection,
  same-image restart, and post-restart verification passed;
- Flyway remained exact V10 with no pending or failed migration;
- exact image/release identity remained unchanged across restart;
- printing and Staging isolation remained intact; and
- Production continuity remained unchanged.

Therefore `STG-007 = PASS`. This is not AL-003 Staging clone acceptance and is
not Production acceptance or deployment.

The unique stop state is
`STG-008_SYNTHETIC_TOPOLOGY_AND_SOURCE_WAITING_FOR_OWNER_RUNTIME_APPROVAL`.
The next bounded batch requires a new explicit Owner runtime authorization
because it will create synthetic Staging state: one `STG005_` Organization,
source Store ID 1, synthetic Owner credential, Organization/source
memberships, and the reviewed Synthetic St-Denis source graph of 4 categories,
3 stations, 13 items, and 38 options, with guarded replay evidence. The
password remains runtime-only. That future authorization does not include
target onboarding, Owner login, clone, Production access, or any real Store
data.

Independent Agent 6 evidence/governance review returned `ACCEPT` with no
findings. Repository publication still requires the unchanged final-head gates.
