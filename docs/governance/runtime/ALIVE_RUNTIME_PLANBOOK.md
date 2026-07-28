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
| Current Agile Loop | `STG-001 Isolated Staging Environment` |
| Loop type | `DELIVERY_GOVERNANCE_PLAN` |
| Loop status | `PLAN_COMPLETE_WAITING_FOR_OWNER_APPROVAL` |
| AL-001 state | `PLAN_COMPLETE` |
| AL-002 state | `AL-002_WAITING_FOR_OWNER_APPROVAL`; the Staging plan does not approve, merge, deploy, or supersede it. |
| Current permitted work | Owner review of the STG-001 plan and explicit decisions on host, access, state root, and resource budget. No Staging implementation or runtime action is authorized. |
| Explicitly not permitted in STG-001 | SSH, Docker/Flyway execution, server/env/firewall/Nginx changes, production database access/copy, real accounts/devices/printers, AL-003 implementation, merge, or deployment. |

The authoritative work records are [FEATURE_BACKLOG.md](../FEATURE_BACKLOG.md),
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md), and
[AL-001 technical plan](../agile/AL-001_OWNER_STORE_ONBOARDING_CHINATOWN_TECHNICAL_PLAN.md).

### STG-001 planning record

- Planning branch: `codex/stg-001-staging-environment-plan`.
- Planning baseline: `origin/main` commit
  `eadf100295c351a5f14a80fb2fb6eea351c2931b`.
- The recommended architecture uses an exact-SHA detached Staging worktree,
  explicit Compose project name, SHA-specific images, loopback-only ports, and
  a dedicated PostgreSQL state root.
- Initial Staging data is empty or synthetic only. Printing defaults to
  `DISABLED` and may use `MOCK` for bounded acceptance; it must not connect to
  a real printer or production Pad.
- See
  [STG-001 Isolated Staging Environment Plan](../agile/STG-001_STAGING_ENVIRONMENT_PLAN.md).
- Status: `PLAN_COMPLETE_WAITING_FOR_OWNER_APPROVAL`. STG-002 implementation,
  server access, migration execution, merge, and deployment remain
  independently owner-gated.

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
