# AL-003 PR-B PostgreSQL and Flyway V10 Verification

> Status: `MACHINE_VERIFIED` (local isolated environment only)
>
> Verified: 2026-07-31, America/Toronto
>
> Scope: AL-003 PR-B idempotency and transaction foundation only

## 1. Repository baseline and boundary

| Item | Evidence |
|---|---|
| Base commit | `11be5c94f9b73e3beb8ec1f84b4a5a3c586c9d34` (`origin/main` after PR #41) |
| Branch | `codex/al-003-pr-b-idempotency-foundation` |
| PostgreSQL image/version | `postgres:16-alpine`, server `16.14` |
| Isolated databases | `al003_prb_verify` and `al003_prb_app_verify` |
| Spring profile | `cloud` |
| Runtime seed | disabled |
| Printing feature | disabled |

The databases and credentials were synthetic and local. No SSH, external
database, Staging/Production environment, Store 1 menu, real account, printer,
Pad, order, or customer data was accessed. No menu clone endpoint was exposed
and no menu clone was executed.

## 2. Flyway history

The empty application-verification database applied the following history:

| Version | Script | Flyway checksum | Success |
|---|---|---:|---|
| 1 | `V1__baseline_current_schema.sql` | `431188510` | true |
| 2 | `V2__add_versioned_menu_revision.sql` | `-1546045661` | true |
| 3 | `V3__add_idempotent_order_submission_and_dispatch_outbox.sql` | `-1713808660` | true |
| 4 | `V4__add_menu_item_sort_order.sql` | `1636049775` | true |
| 5 | `V5__set_cold_chicken_noodle_default_type.sql` | `-1638580130` | true |
| 6 | `V6__add_order_item_routing_snapshots.sql` | `-1681894826` | true |
| 7 | `V7__add_print_job_attention_acknowledgement.sql` | `625683957` | true |
| 8 | `V8__add_owner_store_onboarding_requests.sql` | `1654406856` | true |
| 9 | `V9__add_staging_synthetic_bootstrap_requests.sql` | `1828097009` | true |
| 10 | `V10__add_owner_store_menu_clone_requests.sql` | `482267873` | true |

First startup reported ten migrations applied and schema version 10. The second
startup reported ten migrations validated, current schema version 10, and
`No migration necessary`. Both startups completed JPA `ddl-auto=validate` and
returned HTTP 200 from `/api/v1/system/health` on a loopback-only local port.

## 3. V10 object verification

`owner_store_menu_clone_requests` exists with the reviewed scope, fingerprint,
profile, status, revision, created-count, safe result/error-code, actor, and
timestamp columns.

Machine inspection confirmed:

- primary key `owner_store_menu_clone_requests_pkey` on `id`;
- unique constraint `uq_owner_store_menu_clone_scope_key` on
  `(organization_id, source_store_id, target_store_id, idempotency_key)`;
- index `idx_owner_store_menu_clone_target_store` on `(target_store_id)`;
- check constraint `chk_owner_store_menu_clone_status` allowing only
  `PROCESSING`, `COMPLETED`, and `FAILED`.

V10 is append-only. It does not alter V1-V9 and contains no menu, account,
credential, printer, device, or production seed data.

## 4. Idempotency, concurrency, and evidence checks

Automated tests verified:

- first reservation creates one `PROCESSING` request;
- same key and fingerprint after completion returns the original request as a
  replay;
- same scope/key with a different persisted fingerprint returns
  `IDEMPOTENCY_CONFLICT`;
- an existing processing request returns `MENU_CLONE_IN_PROGRESS`;
- two concurrent reservations create one row and one in-progress result;
- row completion is locked and stores only revisions, nonnegative counts, and
  a normalized result code;
- failure stores only a normalized error code and bounded revision context;
- raw idempotency keys are not returned by the reservation contract, and raw
  failure messages, passwords, tokens, endpoints, or menu payloads are not
  retained as evidence;
- source Store must be `1`, source and target must differ, and the reviewed
  profile code is required before a reservation write.

PR-B intentionally does not implement target emptiness checks, target Store
locking, menu-row writes, or failed-request retry revalidation. Those are later
reviewed packages and cannot be inferred from this foundation evidence.

## 5. Verification commands and results

| Command/check | Result |
|---|---|
| Focused entity/coordinator tests | PASS |
| Explicit PostgreSQL integration tests | PASS |
| Empty database cloud-profile first startup | PASS |
| Same database cloud-profile second startup | PASS |
| `mvn -q test` | PASS: 256 tests, 0 failures, 0 errors, 3 conditionally skipped |
| `mvn -q -DskipTests compile` | PASS |
| `git diff --check` | PASS |
| bounded secret scan | PASS: no credential, token, endpoint, or private-key value added |

Two local harness corrections preceded the retained pass: the initial Flyway
count query compared version strings and was corrected to integer comparison;
the second-start wrapper initially used the wrong synthetic database username
and was corrected to use the isolated container's declared user. These failures
occurred before application business work and did not change migration files or
database schema.

## 6. Remaining gates

- Staging and Production migration status remain `RUNTIME_EVIDENCE_PENDING`.
- Store 1 live menu evidence was not read.
- Owner authorization and the protected HTTP Controller remain PR-F work.
- Category, station, item, option, Chinatown override, Combo, and final menu
  revision transaction behavior remain PR-C through PR-E work.
- A real clone, Staging validation, merge, deployment, and Production migration
  each require separate Owner approval.

## 7. Rollback boundary

Before any future environment applies V10, application rollback is a Git/image
rollback only. After V10 is applied, older application code can ignore the new
table; V10 must not be removed with `Flyway clean`, manual history edits, table
drops, or destructive rollback. No environment applied V10 as part of this
local verification record.
