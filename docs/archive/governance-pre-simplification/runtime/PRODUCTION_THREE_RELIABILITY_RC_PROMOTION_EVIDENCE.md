# Production Three-Reliability RC Promotion Evidence

Date: 2026-08-12, America/Toronto
Result: `PRODUCTION_PROMOTION_PASS`

## Frozen release identity

| Field | Evidence |
|---|---|
| RC | `RC-THREE-RELIABILITY-20260812-3EC4D88` |
| Frozen manifest SHA-256 | `43b0e30e160a7254a1ad70ec01d0fe21986b6eaf7401fc5e9a842af380fb725f` |
| exact candidate / Staging accepted SHA | `3ec4d88a47f68e05b92d9246bfd63af2d1f297f9` |
| latest main during promotion | `47584d40e9a4f65cd719d8ea898d723bd8dba64f` |
| previous Production application SHA | `2661eb76c36dd9aa58db94ceacd278242ef4c9ab` |
| retained Production control checkout | `4667f3c35f85c9f8538f82789d9df1531d4fbc9e` |
| backend image ID | `sha256:2de71105c8fa262c59833c71f7fddfb3f18ec3fb869ba4765adb3b04e1b4ef14` |
| frontend image ID | `sha256:061ac73df1ee8516f8a0fcd94bda70ccdae2a90d7c4f7833e1b63650fe503be0` |
| PostgreSQL image ID | `sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777` |
| tooling PR | `#124`, merge `47584d40e9a4f65cd719d8ea898d723bd8dba64f` |

The Production candidate is the exact Staging-deployed and tested
runtime-sensitive SHA `3ec4d88...`, not later docs/tooling main. The Staging
candidate artifacts were already present on the host and were promoted by image
ID with no build and no pull.

## Gates

- Candidate source SHA = Staging deployed SHA = Staging tested
  runtime-sensitive SHA: PASS.
- Candidate ancestry in latest main: PASS.
- Three reliability repairs present in candidate: PASS by PR #122 / exact
  artifact identity.
- Agent 6:
  - three-reliability repair batch: `ACCEPT`;
  - promotion tooling repair: first `BLOCK` for an awk regex bug, then
    `ACCEPT` after fix;
  - final Production RC pre-deploy: `GO`.
- Fresh backup: PASS; see
  [backup/restore evidence](PRODUCTION_THREE_RELIABILITY_BACKUP_RESTORE_EVIDENCE.md).
- Isolated restore: PASS, exact V10 ledger.
- Rollback compatibility: YES; see
  [rollback evidence](PRODUCTION_THREE_RELIABILITY_ROLLBACK_EVIDENCE.md).
- Production preflight: frontend/API/WebSocket `200`, Flyway V10 last row
  success, failed rows `0`, restart counts `0`, resource gate PASS.
- Production configuration preservation: PASS.

## Deployment and post-deploy verification

The exact-artifact promotion helper returned:

- `PROMOTION|action=execute|rc_id=RC-THREE-RELIABILITY-20260812-3EC4D88|db_unchanged=true|result=PASS`
- `PROMOTION|action=second-start|rc_id=RC-THREE-RELIABILITY-20260812-3EC4D88|db_unchanged=true|result=PASS`

Post-deploy:

- backend image:
  `sha256:2de71105c8fa262c59833c71f7fddfb3f18ec3fb869ba4765adb3b04e1b4ef14`
- frontend image:
  `sha256:061ac73df1ee8516f8a0fcd94bda70ccdae2a90d7c4f7833e1b63650fe503be0`
- frontend HTTP `200`;
- `/api/v1/system/health` HTTP `200` / `UP`;
- `/ws/info` HTTP `200`;
- unauthenticated `/api/v1/auth/me` HTTP `401`;
- Flyway last row `10|10|V10__add_owner_store_menu_clone_requests.sql|true`;
- Flyway count `10`, failed rows `0`;
- second start proved no pending migration;
- database container identity was retained;
- restart counts remained `0`;
- 30-second stabilized observation window: backend error count `0`, nginx error
  count `0`.

## Production configuration preservation

Production retained Store `1 / 4483_R_SAINT_DENIS`, Printing
`printing_enabled=true`, `printing_mode=PAD_DIRECT`, four logical printers,
three assignments and seven device-topology rows. No Staging `MOCK` mode,
synthetic Store/Organization identity, Staging credential, printer endpoint,
device credential, runtime environment value or business data was copied into
Production.

No customer/PII, historical order/item history, payment, password hash, token,
cookie/session secret, raw environment, DB secret, printer secret or device
credential value appears in this evidence.

## Repair presence and Owner retest

- `PAD_SLEEP_PRINT_BLOCKING_REPAIR`: present by artifact identity; 30-second
  pre-output `CLAIMED` behavior and conservative `PRINTING` ambiguity behavior
  are included. `OWNER_PRODUCTION_PHYSICAL_PAD_RETEST_REQUIRED`.
- `PAD_MENU_REVISION_AND_CLICK_LOCK_REPAIR`: present by artifact identity;
  visibility/focus/online/periodic menu refresh, atomic IndexedDB snapshot
  behavior and visible click-lock UX are included.
  `OWNER_PRODUCTION_MENU_REVISION_RETEST_REQUIRED`.
- `PRINTING_BOUNDED_SCHEDULING_LATENCY_REPAIR`: present by artifact identity;
  Store+printer FIFO, bounded independent-printer concurrency,
  slow-printer isolation and retry/duplicate safety are included.
  `OWNER_PRODUCTION_PRINTING_LATENCY_RETEST_REQUIRED`.

The three repairs are `DEPLOYED_TO_PRODUCTION`, not `OWNER_FIELD_VERIFIED`.

Unique stop:
`THREE_RELIABILITY_REPAIRS_PRODUCTION_PROMOTED_WAITING_FOR_OWNER_FIELD_RETEST`.
