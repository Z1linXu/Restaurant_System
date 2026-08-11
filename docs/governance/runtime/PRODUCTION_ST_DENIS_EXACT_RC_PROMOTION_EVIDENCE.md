# Production St-Denis Exact-RC Promotion Evidence

Date: 2026-08-11
Environment: existing Production St-Denis only
Result: `PRODUCTION_PROMOTION_PASS`

## Frozen release identity

| Field | Evidence |
|---|---|
| RC | `RC-ST-DENIS-20260811-2661EB76` |
| Frozen manifest SHA-256 | `b11ff37ef312fbff9ae3a2f9d8dad2ca02ea7bec442902d66e503ef55d1e6e46` |
| Staging-accepted/application SHA | `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` |
| Backend image ID | `sha256:2db920f0929b775aae30271794e903c217f9ba99eb5e889f37ef0c2a4df309a9` |
| Frontend image ID | `sha256:233cc07da7d41143bdc435a8850fb910af0c45490832e0edee57e95a27f4fa8f` |
| Previous Production source | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` |
| Previous Production backend image ID | `sha256:36daa6697ff7204d88e831315e356241721a956c8513551cf919937cce260792` |
| Previous Production frontend image ID | `sha256:781cb93ee4e821a827890f57de58a9f4286371bfc43aef9b4ad8a9507536eca7` |
| Promotion tooling | `1c24b76c7bf0db161a7081b1999912619e3c41b1` |
| Flyway manifest SHA-256 | `503e25377e82f98757af817cefa188a6702ec09b0b19a8ee81d8483b6d28a466` |

The Production working checkout remains at the previous source identity. The
deployed application identity is the immutable backend/frontend image pair
above; no Production build or pull occurred. Both exact previous image IDs remain on
the host as `PREVIOUS_GOOD_RC` artifacts.

## Final preflight and execution

- Compose project remained `cloud`, with exactly `db`, `backend`, and `nginx`.
- PostgreSQL image remained
  `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777`.
- PostgreSQL container remained `c2ab37fec6ac`; the fixed bind state root
  remained `/home/ubuntu/Restaurant_System/deployment/cloud/data/postgres`.
- Available memory was 1,846,496 KiB immediately before execution and remained
  above the reviewed 1 GiB floor. Available disk was 34,072,796 KiB, above the
  reviewed 5 GiB floor.
- The final preflight observed Flyway V1 through V7, all successful, and no
  failed history row.
- The reviewed helper promoted only the frozen backend and frontend image IDs.
  Production Flyway advanced through the reviewed V8, V9, and V10 migrations.
- The separate `--second-start` action passed and proved the Flyway ledger was
  unchanged with no pending migration. The database container identity did not
  change.

The first helper invocation returned fail-closed immediately after recreating
nginx because its public-route verifier made a single request during nginx
startup and received a connection reset. Read-only checks immediately after
that exit proved both exact images running, frontend/API/WebSocket healthy,
Flyway V10 exact, and the database unchanged. The separately required
second-start/no-pending helper then returned `PASS`. This was a tooling
readiness false-negative, not an application incident. The evidence package
adds a bounded 30-second public-route retry for future promotions; the frozen
RC, installed helper used for this deployment, and Production runtime were not
modified by that follow-up repair.

## Post-deploy smoke and observation

Three bounded observations returned:

- frontend HTTP `200`;
- `/api/v1/system/health` = `UP`;
- `/ws/info` HTTP `200`;
- unauthenticated `/api/v1/auth/me` = `401`, preserving the auth boundary;
- restart counts `db=0`, `backend=0`, `nginx=0`;
- DB connectivity `PASS`;
- backend/nginx error counts `0/0` in the stabilized final window.

No Production credential was read, so no authenticated business request was
fabricated. Startup/JPA/health, protected-route enforcement, safe Store-scoped
configuration counts, and the pre-deploy isolated compatibility smoke cover
the non-destructive validation boundary. No real order was created.

## Configuration and continuity

Production retained `printing_enabled=true`, `printing_mode=PAD_DIRECT`, four
enabled logical printer configs, and three enabled assignments. No printer
endpoint or device credential was read, copied, or changed, and no Staging
MOCK value crossed into Production.

Store-scoped safe row counts remained the manifest-v2 values: 6 menu
categories, 39 menu items, 380 menu options, 13 dining tables, 4 staff users,
5 stations, and 7 device-topology rows. The fixed database state root and
container were retained; only reviewed forward migrations ran. No Store,
Organization, menu, table, staff, access, feature, printing, device, order,
customer, payment, or credential mutation was performed by the promotion.
Staging remained on exact accepted SHA `2661eb76...`, Flyway V10, and MOCK.

## Decision

Both independent Agent 6 pre-deploy reviews returned `GO` for the exact frozen
RC. Backup/restore and rollback gates are recorded separately. No P0/P1 was
observed during the bounded Production window.

Unique stop state:
`PRODUCTION_EXACT_RC_PROMOTED_POST_DEPLOY_OBSERVATION_PASS`.
