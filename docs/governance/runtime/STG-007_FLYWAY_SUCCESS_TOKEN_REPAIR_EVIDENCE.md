# STG-007 Flyway Success Token Repair Evidence

> Evidence classification: `STAGING_RUNTIME_OBSERVATION_PLUS_REPOSITORY_REPAIR`
>
> Base: `origin/main@39fa284b7bccd64d650c396f2c7532b0a0858b4b`
>
> Runtime boundary: the Owner-authorized V10-to-V10 continuation deployed only
> exact Staging candidate `39fa284b...`; runtime collection then stopped before
> PASS evidence and before any same-image restart, synthetic write, credential,
> login, Store read, clone, or Production mutation

## Exact continuation ground truth

PR #80 is `IN_MAIN` at `39fa284b7bccd64d650c396f2c7532b0a0858b4b`.
Fresh pre-action evidence confirmed retained Staging `868e229f...`, exact
Flyway V1-V10 with current schema V10 and no pending migration, health
`200/200/200`, printing `DISABLED` / `false`, loopback-only exposure, isolated
state/network/mounts, and unchanged Production continuity at
`4667f3c35f85c9f8538f82789d9df1531d4fbc9e`.

The new continuation authorization explicitly accepted V10 as the entry
schema. Exact candidate import, detached release creation and four-field
private-environment rotation passed. The resulting environment digest is
`19da0f482afc6b37cbe6387de4b27cd9086288a2bee6ca5bb9a709762c3bcb85`.
The V10 continuation entry evidence digest is
`56010d3821f9d4b28facdf3a6d80ecededb3f1d0c7dd358f4b63e30ecf1948b9`;
the fresh formal preflight digest is
`3ece60651d3582477b4318d2e390a18c34b29b5f5aee53ffab5c9d2574689259`.

The reviewed deploy helper built backend then frontend serially and started
only `restaurant-pos-staging`. The deployed release/build source SHA is exact
`39fa284b7bccd64d650c396f2c7532b0a0858b4b`. Flyway remained V10 with the same
ten successful version/script/checksum rows and no V11 or pending migration.
Frontend, backend health and SockJS returned `200/200/200`; printing and all
isolation boundaries remained unchanged. Minimum Production container/image/
start/restart/health metadata remained unchanged.

PR #80's repaired readiness collector then passed. Its private evidence digest
is `7d60f9ee35b9a8dee1e6ef5c364576a2d8c3a42c48442ba829b8d078ffb36ffe`.
This proves the repaired optional-health classification worked against the
real three-service topology.

## Runtime collection stop

The separately approved `collect-evidence` action stopped before emitting a
PASS record:

```text
AL003S_ACCEPTANCE|NO_GO|Flyway history contains an invalid or failed row
```

The one-use approval digest
`0274bb7df8f460ac2b26c32de016544ec38fb87facfb10fe91691d01e60786e2`
was consumed. The redirected evidence file is empty, with the standard empty
SHA-256 `e3b0c442...`; it is failure context and must never be promoted to PASS.
The read-only collection action changed no container or database state, wrote
no restart blocked marker, and no same-image restart was attempted.

## Deterministic root cause and repair

The collector query serializes Flyway's boolean with PostgreSQL
`success::text`, whose exact successful token is `true`. The validator and its
mock fixture expected the abbreviated `psql` display token `t`. Consequently a
correct, checksum-matched V1-V10 history failed before sanitized evidence
emission.

The bounded repair aligns validation and fixtures with the collector's own
query by requiring exact token `true`. `false`, abbreviated `t`, blank, failed,
missing, duplicate, reordered, renamed or checksum-mismatched rows remain
fail-closed. No SQL query, migration manifest, action lock, approval binding,
image/container identity, restart sequence, deployment behavior, business API
or product contract changes.

## Verification and independent review

- focused runtime collector/restart regression: `PASS`;
- exact `true` success path plus `false` and abbreviated-`t` rejection:
  `PASS`;
- readiness, runtime-guard, Owner-client, release-rotation and exact-bootstrap
  regressions plus shell syntax: `PASS`;
- `git diff --check`, Markdown links, secret scan, exact scope scan and
  governance drift scan: `PASS`;
- Agent 6 independent review: `ACCEPT`, with no blocking or non-blocking
  finding. The reviewer independently confirmed PostgreSQL canonical boolean
  semantics, exact fail-closed test coverage, unchanged runtime/security
  boundaries, consistent Ground Truth and `git diff --cached --check` PASS.

## Stop and next gate

The unique stop state is
`STG-007_RUNTIME_COLLECTION_BLOCKED_BY_FLYWAY_SUCCESS_TOKEN_REPAIR`.
Staging remains healthy at exact `39fa284b...` / Flyway V10 with printing
disabled; Production continuity remains read-only and unchanged. When this
repair enters main it creates a new exact candidate, so the `39fa284b...`
release/preflight/readiness/approval chain expires. The Owner-authorized
Dependency Repair Auto-Loop may restart the full V10-aware continuation only
from that new exact main. It may not reuse this failed collection, skip fresh
identity/preflight/readiness, or infer authorization for STG-008.
