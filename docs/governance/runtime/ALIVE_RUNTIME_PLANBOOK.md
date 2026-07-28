# Alive Runtime Planbook

> Status: `ACTIVE_GOVERNANCE_RECORD`
>
> Last updated: 2026-07-28, America/Toronto
>
> Scope: current operating baseline, active work, deployment entry conditions,
> and approval boundaries. This is a living index, not a replacement for the
> immutable Phase 3 evidence reports.

## 1. Evidence vocabulary

| Label | Meaning | Do not infer |
|---|---|---|
| `RUNTIME_COMMIT` | The commit reported as running in production. | That it is formally approved or matches the documentation branch. |
| `DOCUMENTATION_COMMIT` | The Git commit that contains this governance baseline. | That it is deployed. |
| `OPERATOR_CONFIRMED` | A responsible operator reported completing a field action. | Machine logs, exact timing, or fleet-wide behavior. |
| `LOG_OBSERVED` | A retained, sanitized machine-log observation. | A business acceptance result without matching operator evidence. |
| `MACHINE_VERIFIED` | Reproducible automated or machine-produced evidence with a known scope. | Production-wide behavior beyond that scope. |
| `EVIDENCE_PENDING` | No adequate evidence has been retained. | Failure, success, or production absence. |

## 2. Current production baseline

| Item | Current value | Evidence | Boundary |
|---|---|---|---|
| Environment | `restaurant-prod` | `OPERATOR_CONFIRMED` | Environment label only; no host or secret is recorded. |
| `RUNTIME_COMMIT` | `4667f3c` | `OPERATOR_CONFIRMED` | Reported deployed commit, not a formal release approval. |
| Production branch | `main` | `OPERATOR_CONFIRMED` | Branch relationship is not a deployment approval record. |
| `DOCUMENTATION_COMMIT` | `4c01d81` | `MACHINE_VERIFIED` locally | This is the committed governance-document baseline. It is not the runtime commit and is not deployed by this record. |
| Deployment mode | HTTP | `OPERATOR_CONFIRMED` | HTTPS/certificate posture is outside this record. |
| Compose services | `db`, `backend`, `nginx` | `OPERATOR_CONFIRMED` | No new container inspection was run for this planbook. |
| Database schema | Flyway V7, including `V7__add_print_job_attention_acknowledgement.sql` | `OPERATOR_CONFIRMED` | Not a restore or schema-integrity rehearsal. |
| Current backup artifact | `deployment/cloud/backups/restaurant_pos_20260725_033648.dump` | `OPERATOR_CONFIRMED` | Reported non-empty, approximately 812K; recoverability is unproven. |
| Print mode | PAD_DIRECT field flow | `OPERATOR_CONFIRMED` | Does not replace device-by-device health evidence. |

Historical detail remains in [POST_DEPLOY_RUNTIME_EVIDENCE.md](POST_DEPLOY_RUNTIME_EVIDENCE.md),
[CURRENT_RUNTIME_STATUS.md](CURRENT_RUNTIME_STATUS.md), and the immutable Phase 3
snapshots. Do not copy those reports into this planbook.

## 3. Confirmed field baseline

| Field result | Classification | Scope and limit |
|---|---|---|
| New APK can log in, load menu, create and submit orders. | `OPERATOR_CONFIRMED` | A field flow, not an exhaustive offline fault-injection test. |
| Older APK can connect to the reported current backend and submit orders. | `OPERATOR_CONFIRMED` | Does not establish compatibility for every historical APK. |
| GRAB, FRONTDESK_RECEIPT, and HOT_KITCHEN physically printed. | `OPERATOR_CONFIRMED` | No job IDs, raw payloads, or exact timestamps are retained here. |
| PAD_DIRECT Worker completed the reported long-run observation without the prior stopped-and-unrecoverable symptom. | `OPERATOR_CONFIRMED` | Not fleet-wide proof and not a substitute for future monitoring. |
| Phase 3A-3C repository/container/Pad observations. | `LOG_OBSERVED` and `MACHINE_VERIFIED` only where the source report says so | Read the cited historical report for each exact assertion. |

## 4. Current incidents and backlog

| Area | Current state | Authority |
|---|---|---|
| P0/P1 production incident | No active P0 or P1 item recorded in the current backlog. | [KNOWN_ISSUES_BACKLOG.md](../KNOWN_ISSUES_BACKLOG.md) |
| Historical Orders stale-chunk/WebView blank page | `KI-001` is closed as `CLOSED_OPERATOR_CONFIRMED`; the historical cache-clear recovery remains documented. | [KNOWN_ISSUES_BACKLOG.md](../KNOWN_ISSUES_BACKLOG.md) |
| Active operational issues | `KI-002` through `KI-007` remain open or evidence/process pending. | [KNOWN_ISSUES_BACKLOG.md](../KNOWN_ISSUES_BACKLOG.md) |
| Production approval record | Not established. | `KI-006`; `EVIDENCE_PENDING` |
| Database restore rehearsal | Not executed or evidenced. | `KI-005`; `EVIDENCE_PENDING` |

## 5. Current feature and Agile Loop

| Item | Current state |
|---|---|
| Current feature | `FT-001 Owner Store Onboarding - Chinatown` |
| Current Agile Loop | `STG-002 through STG-006 Isolated Staging Delivery Preparation` |
| Loop type | `DELIVERY_GOVERNANCE_IMPLEMENTATION_AND_LOCAL_VERIFICATION` |
| Loop status | `PARTIAL_COMPLETE_BLOCKED_WAITING_FOR_OWNER` |
| AL-001 state | `PLAN_COMPLETE` |
| AL-002 state | `AL-002_WAITING_FOR_OWNER_APPROVAL`; the Staging plan does not approve, merge, deploy, or supersede it. |
| Current permitted work | Owner review of the stacked Draft PRs and restoration of a safe local Docker runtime for the remaining STG-003 gate. |
| Explicitly not permitted | Merge, SSH, server Docker/Flyway execution, server/env/firewall/Nginx changes, production database access/copy, real accounts/devices/printers, restore, AL-003 implementation, or deployment. |

The authoritative work records are [FEATURE_BACKLOG.md](../FEATURE_BACKLOG.md),
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md), and
[AL-001 technical plan](../agile/AL-001_OWNER_STORE_ONBOARDING_CHINATOWN_TECHNICAL_PLAN.md).

### STG-001 through STG-006 execution record

- STG-001 entered `origin/main` at merge commit `c1f0108` through PR #30 and
  remains the architecture and approval-boundary plan.
- STG-002 implementation head `e7015fe` is on
  `codex/stg-002-local-staging-package`, Draft PR #31:
  `STG-002_IMPLEMENT_COMPLETE_WAITING_FOR_OWNER_REVIEW`.
- STG-003 implementation head `afae460` is on
  `codex/stg-003-local-isolated-rehearsal`, Draft PR #32. Its script guards,
  fake-Docker lifecycle, frontend build, backend tests, and isolated
  PostgreSQL 16.14/Flyway V1-V8 startup checks passed. A real local Docker
  Compose rehearsal was not possible because no Docker-compatible runtime was
  installed:
  `STG-003_IMPLEMENTATION_READY_RUNTIME_REHEARSAL_BLOCKED_LOCAL_DOCKER`.
- STG-004 implementation head `cff9f73` is on
  `codex/stg-004-first-deploy-preflight`, Draft PR #33. It prepares
  fail-closed preflight evidence and owner-action plans but has not run on a
  server: `STG-004_PREFLIGHT_READY_SERVER_DEPLOYMENT_BLOCKED`.
- STG-005 has no branch, commit, fixture, or PR. Its required Docker-backed
  STG-003 environment did not succeed, so the stage was not started:
  `NOT_STARTED_BLOCKED_BY_STG-003_RUNTIME_GATE`.
- STG-006 operations preparation is on
  `codex/stg-006-operational-hardening`, Draft PR #34. Its read-only inventory,
  disk, image-compatibility, evidence, and backup metadata planning guards
  passed an independent security review. Runtime hardening and synthetic
  rebuild remain blocked on STG-003 and STG-005:
  `STG-006_PREPARATION_ONLY_BLOCKED_ON_STG-003_STG-005`.

The shared architecture remains exact-SHA, explicit project
`restaurant-pos-staging`, SHA-specific images, loopback-only access, dedicated
PostgreSQL state, empty/synthetic data only, and printing `DISABLED` or bounded
`MOCK`. No real Docker deployment, server access, production data, real
printer, Pad pairing, restore, merge, or migration was executed by this chain.
See [STG-001 plan](../agile/STG-001_STAGING_ENVIRONMENT_PLAN.md) and the
[Staging package runbook](../../../deployment/cloud/README_STAGING.md).

### AL-002 implementation record

- Review branch: `codex/al-002-owner-store-onboarding-backend`.
- Scope completed locally: exact-Organization Owner authorization, durable
  Organization-scoped idempotency record, BCrypt-backed target-Store-only staff
  membership provisioning, and inactive/printing-disabled Store defaults.
- Local verification completed: focused onboarding/security tests and the full
  backend Maven suite. This is code verification only, not owner approval,
  merge, migration execution, deployment, or production provisioning.
- Local PostgreSQL/Flyway verification completed against an isolated PostgreSQL
  16.14 database using the cloud profile: V1-V8 applied successfully, V8's
  table/unique constraint/index were verified, and a second startup validated
  the schema without reapplying V8. See
  [AL-002 PostgreSQL and Flyway V8 Local Verification](AL-002_POSTGRES_FLYWAY_V8_VERIFICATION.md).
- The populated staging/production Store Code duplicate risk, deployment, and
  production migration remain `EVIDENCE_PENDING` and owner-gated.

## 6. Next deployment entry conditions

No deployment is authorized by this planbook. A future implementation PR may
enter `DEPLOY` only when all applicable conditions are recorded:

1. The applicable Agile Loop has passed `VERIFY` and has explicit owner
   approval for `MERGE` and production deployment.
2. Backend, frontend, Android, migration, and deployment impacts are stated in
   the PR and the required automated tests pass.
3. Any schema migration is reviewed for forward compatibility and has an
   owner-approved backup/rollback plan. No `down -v`, restore, or destructive
   database action is implicit.
4. Production initialization inputs, including account passwords and printer
   endpoints, are supplied at runtime by an authorized owner and never placed
   in Git, migrations, seeders, logs, or documentation.
5. Store isolation, printer routing, and field acceptance criteria are checked
   on site before the new Store is operationally handed over.
6. Post-deployment observations are appended as new evidence; historical
   reports are not rewritten.

## 7. Rollback reference

The latest reported production runtime point is `4667f3c` on `main`
(`OPERATOR_CONFIRMED`). It is a **rollback reference**, not an automatic
rollback instruction. Any rollback requires owner approval, confirmation of
schema compatibility, and the deployment runbook. Never delete a database
volume, restore a backup, or run an unreviewed downgrade as part of rollback.

## 8. Owner approval boundaries

Codex may prepare branches, code, tests, commits, push a review branch, and
open a PR when the applicable loop permits it. The following require explicit
owner approval for each occurrence:

- PR merge, production deployment, SSH/runtime commands, or environment changes;
- production Store, user, membership, credential, device, printer, or table creation;
- production migrations, backup restore/rehearsal, data repair, or deletion;
- use of passwords, secrets, certificates, printer IPs, or pairing credentials;
- any print, reprint, job claim, payload retrieval, or job-state transition.

## 9. Operating maintenance

- Update this planbook after each approved deployment, field validation, or
  backlog/loop state change.
- Preserve evidence classifications exactly; do not promote
  `OPERATOR_CONFIRMED` to `MACHINE_VERIFIED` without new machine evidence.
- Keep full display-name rules in
  [FRONTDESK_GRAB_ITEM_NAME_RULES.md](../../operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md),
  not in this planbook.
