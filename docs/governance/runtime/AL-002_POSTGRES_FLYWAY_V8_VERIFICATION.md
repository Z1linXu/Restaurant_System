# AL-002 PostgreSQL and Flyway V8 Local Verification

> Status: `MACHINE_VERIFIED` for the bounded local environment described below.
>
> Verification date: 2026-07-28, America/Toronto
>
> Scope: `AL-002 Owner-Scoped Store Onboarding Backend` verification only.

## 1. Boundary

This report records a local, isolated PostgreSQL verification of the repository
state at `f8762f537004482ccc65351950ece7d27b33bc70` (`origin/main` when the
verification branch was created). It does not prove that a production,
staging, cloud, or pilot database has V8 applied.

- Verification branch: `codex/al-002-postgres-flyway-verification`.
- PostgreSQL: `16.14 (Homebrew)`.
- Database: `al002_flyway_verify`.
- Listener: local loopback only on a non-default test port.
- Profile: `cloud`, with a generated non-production JWT value that was never
  written to output or source control.
- Runtime seed: disabled with `APP_SEED_RUNTIME_ENABLED=false`.
- Printing feature: disabled for this startup with
  `APP_FEATURES_PRINTING=false`.
- Isolated data counts after migration: `stores=0`, `users=0`,
  `print_jobs=0`.
- The temporary local PostgreSQL server was stopped after verification; its
  isolated test data directory was not deleted by this loop.

No production database, SSH target, Android device, printer, order, staff
credential, Store, or print job was used. No `Flyway clean`, database delete,
backup restore, or destructive command was run.

## 2. Migration Result

The first cloud-profile backend startup validated and applied V1 through V8 to
an empty PostgreSQL 16 database. The backend reached a successful Spring Boot
startup before being gracefully stopped as part of the local test.

| Version | Script | Checksum | Success |
|---|---|---:|---|
| V1 | `V1__baseline_current_schema.sql` | `431188510` | `true` |
| V2 | `V2__add_versioned_menu_revision.sql` | `-1546045661` | `true` |
| V3 | `V3__add_idempotent_order_submission_and_dispatch_outbox.sql` | `-1713808660` | `true` |
| V4 | `V4__add_menu_item_sort_order.sql` | `1636049775` | `true` |
| V5 | `V5__set_cold_chicken_noodle_default_type.sql` | `-1638580130` | `true` |
| V6 | `V6__add_order_item_routing_snapshots.sql` | `-1681894826` | `true` |
| V7 | `V7__add_print_job_attention_acknowledgement.sql` | `625683957` | `true` |
| V8 | `V8__add_owner_store_onboarding_requests.sql` | `1654406856` | `true` |

`flyway_schema_history` contained exactly one successful V8 row after the
second application startup. The second startup logged successful validation of
eight migrations, schema version `8`, and `No migration necessary`; no checksum
or schema-validation error occurred.

## 3. V8 Schema Evidence

The following PostgreSQL 16 metadata checks passed:

| Requirement | Result | Classification |
|---|---|---|
| `owner_store_onboarding_requests` exists | Present in schema `public`. | `MACHINE_VERIFIED` |
| Organization/idempotency uniqueness | `uq_owner_store_onboarding_organization_key`: `UNIQUE (organization_id, idempotency_key)`. | `MACHINE_VERIFIED` |
| Store lookup index | `idx_owner_store_onboarding_request_store` exists on `(store_id)`. | `MACHINE_VERIFIED` |
| Repeated startup safety | V8 was not re-applied and Flyway validation passed. | `MACHINE_VERIFIED` |
| Backend startup on PostgreSQL | Cloud-profile backend started successfully against PostgreSQL 16.14. | `MACHINE_VERIFIED` |

## 4. Store Code Risk Check

The read-only duplicate check used the existing Store fields and normalized each
candidate as `lower(btrim(code))`, grouped by `organization_id`.

| Scope | Result | Classification |
|---|---|---|
| Isolated empty verification database | No duplicate normalized Store Codes; there were no Store rows. | `MACHINE_VERIFIED` |
| Populated staging or production Store data | Not queried in this loop. The real duplicate-risk result remains unknown. | `EVIDENCE_PENDING` |

No Store Code unique constraint or migration was added by this verification.

## 5. Automated Checks

| Check | Result |
|---|---|
| `mvn -q -DskipTests package` | PASS |
| `mvn -q test` | PASS |
| `mvn -q -DskipTests compile` | PASS |
| Local PostgreSQL 16 first cloud-profile startup | PASS |
| Local PostgreSQL 16 second cloud-profile startup | PASS |
| `git diff --check` | PASS after the verification-document changes; rerun as the final pre-commit check. |

The Maven suite includes mocked printing-oriented tests. They do not represent a
physical print action, and this verification did not submit orders or invoke a
printer transport.

## 6. Staging Acceptance Checklist

The following items require a separately approved staging or owner-controlled
environment. They were not run here:

1. Take and verify an owner-approved staging backup before the normal Flyway
   startup path.
2. Start the approved backend artifact with the intended staging profile and
   confirm V1-V8 in that database's `flyway_schema_history`.
3. Confirm V8's table, unique constraint, and `store_id` index through
   read-only database metadata.
4. Query normalized Store Code duplicates against the populated staging Store
   dataset before any future Store Code uniqueness policy is proposed.
5. Exercise the AL-002 endpoint only with approved synthetic data and a
   non-production credential supplied outside source control.
6. Confirm no menu clone, printer configuration, pairing, print action, or
   production-style Store provisioning is included in the AL-002 acceptance
   scope.

## 7. Rollback Notes

V8 is additive. It creates a durable onboarding-request table and a lookup
index; it does not alter existing order, payment, KDS, or printing tables.

- Do not run `Flyway clean`, a manual table drop, or an unreviewed down
  migration as a rollback.
- A pre-V8 application/Flyway binary was not started against a V8 database in
  this loop. Its ability to validate a newer applied migration is
  `EVIDENCE_PENDING`; verify that compatibility before any owner-approved
  application rollback.
- Any production migration or rollback remains gated by owner approval, a
  verified backup, schema compatibility review, and the deployment runbook.

## 8. Remaining Evidence Gaps

- No populated staging or production Store Code duplicate audit was performed.
- No production Flyway history, backup restore rehearsal, or deployment was
  performed.
- No real owner, Store, staff credential, menu, printer, device, order, or
  printing action was created.
- Owner approval to merge, deploy, or execute V8 outside this isolated local
  database remains required.

## 9. Loop State

The bounded local verification is complete. `AL-002` is
`WAITING_FOR_OWNER_APPROVAL`; this status is not merge, deployment, migration
execution, or production provisioning approval.
