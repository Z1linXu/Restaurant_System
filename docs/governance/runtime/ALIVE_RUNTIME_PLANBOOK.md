# Alive Runtime Planbook

> Status: `ACTIVE_GOVERNANCE_RECORD`
>
> Last updated: 2026-07-31, America/Toronto
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
| Current Agile Loop | `AL-003 Store 1 -> Chinatown Live Menu Clone` |
| Loop type | `PLAN_CONTRACT` |
| Loop status | `AL-003_PR_A_WAITING_FOR_OWNER_REVIEW` |
| AL-001 state | `PLAN_COMPLETE` |
| AL-002 state | `AL-002_WAITING_FOR_OWNER_APPROVAL`; the Staging plan does not approve, merge, deploy, or supersede it. |
| STG-002 state | Deployment package merged to `main` by PR #31; this does not establish a server Staging runtime. |
| STG-003 state | PR #35 merged the completed real local Docker rehearsal into `main`; final runtime Head `74dd6a628002f96e4f2b4fbe3cf479fb23ed8e01` is `FINAL_HEAD_REHEARSAL_PASS`. |
| STG-004 state | PR #38 merged the STG-004 runtime evidence. Exact SHA `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` passed PLAN, fresh PREFLIGHT, serial build/start, runtime verification, and isolated stop/start recovery. Server Staging remains running; Production remained unchanged. |
| STG-005 state | PLAN complete. The Owner approved CP-0 as a separate minimal Staging-only bootstrap implementation and accepted CP-4 as a feature-disabled KDS/Assembling boundary. Positive Kitchen/Assembling workflow remains `EVIDENCE_PENDING`. |
| STG-005A state | PR #40 merged the profile-gated synthetic bootstrap and append-only `V9__add_staging_synthetic_bootstrap_requests.sql` into `main`. This record does not prove V9 was applied or that bootstrap ran against server Staging. |
| AL-003 state | PR-A freezes the comparison and technical contract only. It plans `V10__add_owner_store_menu_clone_requests.sql`; no V10 file or clone implementation exists in this package. |
| Current permitted work | Owner review of the AL-003 PR-A documentation contract and Draft PR only. |
| Explicitly not permitted | Implementation agents, V10 creation, Store 1 runtime read, SSH, Docker, Flyway execution, database query/write, Staging or Production mutation, real clone, merge, deployment, STG-006, or unrelated backlog work. |

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
- Historical planning status: `PLAN_COMPLETE_WAITING_FOR_OWNER_APPROVAL`.
  STG-002 is now merged as a package; server access, migration execution, and
  deployment remain independently owner-gated.

### STG-002 and STG-003 verification record

- PR #31 merged the isolated STG-002 deployment package into `main`.
- Historical PR #32 was merged into the STG-002 branch before PR #31 reached
  `main`; it remains an immutable record and cannot receive the final local
  Docker evidence.
- The continuation branch `codex/stg-003-docker-rehearsal` was safely rebased
  onto `origin/main` commit
  `4ac1d10cde169bf7ebc807aac3624b0323e3c440`.
- A real local Docker Desktop rehearsal passed at exact commit
  `b17ffa9a397bef62d474a58b649f1e55467a974f`.
- The rehearsal built and started exactly `db`, `backend`, and `nginx` under
  project `restaurant-pos-staging`, exposed only `127.0.0.1:18080`, used an
  isolated PostgreSQL path, and kept printing `DISABLED`.
- PostgreSQL 16.14 applied Flyway V1-V8 on the first startup. The second
  startup validated schema version 8 with no migration necessary, and V8's
  table, Organization/idempotency unique constraint, and Store lookup index
  were verified.
- Backend health, frontend root, `/api`, and SockJS `/ws/info` returned HTTP
  200. An ordinary `/ws` GET returned 400 because it was not a WebSocket
  Upgrade; no STOMP session was attempted.
- Cleanup removed only local Staging containers and network. The isolated
  PostgreSQL state was retained; no volume deletion occurred.
- Full evidence is in
  [STG-003 Local Isolated Rehearsal Evidence](STG-003_LOCAL_REHEARSAL_EVIDENCE.md).
- PR #35 repeated the concise real Docker regression against final runtime Head
  `74dd6a628002f96e4f2b4fbe3cf479fb23ed8e01`, recorded
  `FINAL_HEAD_REHEARSAL_PASS`, and merged the completed STG-003 work into
  `main`.
- Status: `STG-003_LOCAL_REHEARSAL_COMPLETE`. Server Staging and deployment
  remain separately Owner-gated.

### STG-004 preflight review record

- Review branch: `codex/stg-004-first-deploy-preflight`.
- PR #33 is based on `main` after the PR #35 merge.
- Scope is limited to a read-only same-host preflight, validation-only default
  deploy wrapper, explicit Owner start gate, and plan-only stop/rollback
  controls.
- The Owner later approved one STG-004 server run for exact SHA
  `3c1b117e137cc90b984bb392cb3f9e4b7a7f149f`, isolated under
  `/srv/restaurant-pos/staging`, with project `restaurant-pos-staging`,
  loopback bind `127.0.0.1:18080`, and printing `DISABLED`.
- PLAN command categories were local governance reads and local Git
  baseline/source inspection only. The current Git branch, local HEAD, and
  `origin/main` were all `main` /
  `3c1b117e137cc90b984bb392cb3f9e4b7a7f149f`; the local worktree was clean.
- Prior read-only server evidence recorded 2 CPUs, 2.1 GiB available memory,
  44 GiB free disk, an unused port `18080`, and continuously running production
  project `cloud`. Those observations are pre-PLAN context, not fresh deploy
  evidence.
- PLAN result: `NO_GO`. The approved
  `deployment/cloud/staging-deploy.sh` start path invokes one
  `docker compose build backend nginx` command and does not enforce
  `--parallel 1` or an equivalent sequential-build control. That conflicts
  with the Owner's explicit requirement that backend and frontend builds run
  sequentially. The exact approved release cannot be modified in place or
  bypassed.
- No new SSH command, server file write, Docker build/start/stop, Flyway
  execution, environment creation, or deployment occurred after this gate was
  identified.
- Evidence paths:
  `deployment/cloud/staging-deploy.sh`,
  `deployment/cloud/staging-server-preflight.sh`,
  `deployment/cloud/docker-compose.staging.yml`, and this checkpoint.
- Unresolved risk: a combined Compose build may run services concurrently and
  exceed the shared-host resource envelope. The separate no-Swap observation
  increases the consequence of that uncertainty.
- Owner decision: use a minimal review branch to replace the combined build
  with `build backend` followed by `build nginx`, preserving every existing
  exact-SHA, preflight-evidence, environment-digest, project/root, and
  `--execute-start` gate.
- The regression harness requires backend success before nginx starts, proves
  backend failure prevents nginx and `up`, and rejects any combined
  `build backend nginx` command.
- VERIFY command categories were local shell syntax checks and isolated
  fake-Docker guard/preflight/control tests only. All Staging scripts passed
  `bash -n`; `test_staging_guard.sh`,
  `test_staging_server_preflight.sh`, and
  `test_staging_server_control.sh` passed. No SSH, real Docker lifecycle
  command, Flyway execution, or server operation was part of this verification.
- Verification evidence paths:
  `deployment/cloud/tests/test_staging_guard.sh`,
  `deployment/cloud/tests/test_staging_server_preflight.sh`, and
  `deployment/cloud/tests/test_staging_server_control.sh`.
- Current Git baseline for the candidate is parent SHA
  `3c1b117e137cc90b984bb392cb3f9e4b7a7f149f` on branch
  `codex/stg-004-serial-build-fix`; the resulting review commit and PR are
  implementation commit
  `67f183ba998b88810e03db4b77b7c433ac5c3cf1` and draft PR #36 with base
  `main`.
- PR #36 merged as
  `35033645b5414f0804cc0aba92a8b8bb832bb074`. Its next Owner-approved
  isolated server run passed formal PREFLIGHT but stopped before backend image
  creation with `mkdir /nonexistent: permission denied`.
- Root cause: the wrapper removed ambient Docker configuration but set both
  `HOME` and `DOCKER_CONFIG` to non-writable `/nonexistent`. Read-only Compose
  validation did not need persistent CLI state, while BuildKit/buildx did.
- The bounded correction creates a `mktemp` state root with mode `0700`, uses
  child `home` and `docker-config` directories, rejects symlink replacement,
  checks `docker --context default compose version`, and removes the state on
  `EXIT`, `ERR`, `INT`, and `TERM`.
- Existing exact-SHA, preflight-evidence, environment-digest, project/root,
  printing-disabled, and sequential backend-then-nginx-then-up gates remain
  unchanged.
- Review branch: `codex/stg-004-docker-cli-state-fix`. Verification is local
  and uses fake Docker fixtures only; it does not authorize SSH or deployment.
- PR #37 merged the Docker CLI-state correction. The Owner then approved exact
  SHA `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`; the former SHA
  `35033645b5414f0804cc0aba92a8b8bb832bb074` and all earlier environment and
  evidence digests are invalid for this run.
- PLAN command categories: local governance/Git inspection and server
  read-only resource, Production-continuity, port, and Staging-state checks.
  Result: `PASS`. The host reported 2 CPUs, about 2.2 GiB available memory,
  44 GiB free disk, no listener on port `18080`, no existing Staging
  container/network, and no leftover isolated Docker CLI-state directory.
  Production `cloud` services `db`, `backend`, and `nginx` were running with
  unchanged baseline IDs, start times, and zero restarts.
- PREFLIGHT command categories: fetch into the independent Staging bare
  repository, create an exact detached release, update only the private
  Staging identity fields, and run the formal read-only preflight. The release
  is clean at the approved SHA, the environment remains owner `ubuntu` mode
  `0600`, and printing is `DISABLED`.
- Fresh environment SHA-256:
  `926a075e482215b1e8c0917a96db483f342dfed895adfe122f1c9cccb63fa94c`.
  Fresh evidence:
  `/srv/restaurant-pos/staging/evidence/stg-004-preflight-4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c.txt`
  with SHA-256
  `01fca943915a922a389c3d00d6e38bb5dcbcae3dc5bed5e1718daf1d875f1707`.
  Every formal gate passed; both exact-SHA images were
  `PENDING_PREBUILD`, the expected first-build state.
- EXECUTE command category: the approved release's guarded
  `staging-deploy.sh --execute-start` path. It revalidated the exact release,
  environment and evidence digests, then built backend first, built nginx only
  after backend success, and started only project `restaurant-pos-staging`.
  Both exact-SHA images built successfully; the isolated Docker CLI state
  count was zero before and after the command.
- VERIFY command categories: project-scoped formatted Docker inspection,
  loopback HTTP/SockJS checks, read-only PostgreSQL/Flyway queries, filtered
  backend logs, resource observation, and an Owner-approved stop/start of only
  `restaurant-pos-staging`. PostgreSQL 16.14 retained exactly eight successful
  migrations through schema version 8; second startup ran no migration, and
  JPA/application startup succeeded.
- Final Staging services are running with only `127.0.0.1:18080`; printing is
  `DISABLED`. Production `cloud` container IDs, start times, running states,
  and zero restart counts remained unchanged. All deploy/verify/restart Docker
  CLI temporary state roots were removed.
- Evidence:
  [STG-004 Same-Host Server Staging Evidence](STG-004_SERVER_STAGING_EVIDENCE.md).
- Unresolved risks: frontend dependency audit findings; no synthetic
  login/onboarding, real STOMP, Android, restore, load, or soak validation.
- Next state:
  `STG-004_SERVER_STAGING_RUNNING_WAITING_FOR_OWNER_VALIDATION`.

### STG-005 synthetic acceptance planning record

- Planning branch: `codex/stg-005-synthetic-acceptance-plan`.
- Planning baseline: `origin/main` commit
  `2e6be1ac44f59cd6e005e68e61f8c567ea80022e`.
- Planned runtime remains exact STG-004 SHA
  `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c`, Compose project
  `restaurant-pos-staging`, SSH-tunnel-only access, and printing `DISABLED`.
- Command categories executed in PLAN: local governance/source reads and local
  Git baseline inspection only. No SSH, API, database, Docker, migration,
  restart, account, Store, menu, table, or order write occurred.
- Evidence:
  [STG-005 Synthetic Business Acceptance Plan](STG-005_SYNTHETIC_ACCEPTANCE_PLAN.md).
- Gate result: the empty Staging runtime has no confirmed formal first
  Owner/Organization/source Store bootstrap path. AL-002 requires all three,
  and the current staging configuration disables default/demo/bootstrap users,
  developer switching, and Platform Admin entry.
- Additional boundary: current backend and frontend feature configuration
  disables KDS, so positive Kitchen/Assembling workflow acceptance requires a
  separate Owner decision and exact-SHA authorization.
- Current resource state: no new observation was performed in PLAN; use the
  immutable STG-004 evidence and do not infer current runtime health from this
  documentation commit.
- Unresolved risks: synthetic bootstrap provenance, KDS/Assembling acceptance
  scope, and final Organization/Store cleanup through supported APIs.
- Next state:
  `STG-005_PLAN_COMPLETE_WAITING_FOR_OWNER_REVIEW`.

### STG-005A synthetic bootstrap implementation record

- Owner decisions: CP-0 authorized a minimal isolated-Staging bootstrap;
  CP-4 accepts the current KDS/Assembling feature-disabled boundary. No KDS
  enablement is included.
- Implementation branch:
  `codex/stg-005a-staging-synthetic-bootstrap`, based on historical
  `origin/main` `22ddc96728057056c194a453825d1c36884f7a92`; PR #40 is now merged into
  `main` at the AL-003 PR-A base
  `2613344d403365d61283ae440de16edffaaad788`.
- The one-shot Spring command exists only under the exact
  `cloud,staging-synthetic-bootstrap` profiles and a separate explicit enable
  property. Default mode validates only; write mode requires both `--execute`
  and `--password-stdin`.
- Guards bind the request to project `restaurant-pos-staging`, root
  `/srv/restaurant-pos/staging`, exact runtime/tool SHAs, the isolated Staging
  database identity, non-web mode, and printing `DISABLED`.
- The transaction creates only the synthetic Organization, source Store,
  BCrypt Owner identity, active Owner Organization membership, and active
  source-Store membership. It does not create menu, table, order, printer, Pad,
  or customer data.
- Flyway V9 adds only an idempotency/audit request table. It contains no seed
  data and has not been applied to the server by this loop. The running
  Staging evidence remains Flyway V8 until a separately approved migration.
- Exact replay returns the same IDs; changed content/password conflicts; a
  forced membership failure rolls back the request and all topology records.
  Evidence output contains only synthetic IDs, status, and SHAs.
- Verification is local only: focused guard, command, idempotency, credential,
  and rollback tests plus the full backend suite and compile are required
  before the Draft PR is published.
- Runbook:
  [STG-005A Synthetic Bootstrap](../../../deployment/cloud/README_STG005_SYNTHETIC_BOOTSTRAP.md).
- No SSH, Docker, Flyway, server command, bootstrap execution, synthetic
  runtime write, Production change, or KDS change occurred.
- Merge state: PR #40 merged the implementation and reserved migration V9 for
  `V9__add_staging_synthetic_bootstrap_requests.sql`. Runtime execution remains
  separately gated and unproven by the merge.

### AL-003 PR-A menu-clone contract record

- Branch: `codex/al-003-pr-a-plan-contract`, based on `origin/main`
  `2613344d403365d61283ae440de16edffaaad788` after PR #40.
- Product authority:
  [AL-003A Final Menu Comparison](../agile/AL-003A_FINAL_MENU_COMPARISON.md).
- Technical authority:
  [AL-003 Store Menu Clone Technical Plan](../agile/AL-003_STORE_MENU_CLONE_TECHNICAL_PLAN.md).
- The unique source is the current live menu of St-Denis, Store ID `1`.
  Repository seed data is historical reference only and cannot populate the
  clone.
- V9 is already occupied by STG-005A. AL-003 plans the append-only
  `V10__add_owner_store_menu_clone_requests.sql`; PR-A creates no migration or
  business implementation.
- No Store 1 query, SSH, Docker, Flyway execution, database access, runtime
  clone, Staging/Production write, merge, or deployment occurred.
- Next state: `AL-003_PR_A_WAITING_FOR_OWNER_REVIEW`.

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
