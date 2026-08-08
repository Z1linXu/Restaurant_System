# Alive Runtime Planbook

> Status: `ACTIVE_GOVERNANCE_RECORD`
>
> Last updated: 2026-08-08, America/Toronto
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
| Last merged `DOCUMENTATION_COMMIT` | `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d` | `MACHINE_VERIFIED` from `origin/main` | PR #60 is `IN_MAIN`. This does not make that commit a Staging or Production runtime. |
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
| Current Agile Loop | `REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE` |
| Loop type | `DEPENDENCY_BOUND_PREPARATION` |
| Loop status | `REL-001_RC_PLAN_PREPARED_WAITING_FOR_STAGING_ACCEPTANCE_AND_OWNER_APPROVAL` |
| AL-001 state | `PLAN_COMPLETE` |
| AL-002 state | PR #27 merged the backend foundation into `main`; Production remains on the older runtime and no production onboarding is established by that merge. |
| STG-002 state | Deployment package merged to `main` by PR #31; this does not establish a server Staging runtime. |
| STG-003 state | PR #35 merged the completed real local Docker rehearsal into `main`; final runtime Head `74dd6a628002f96e4f2b4fbe3cf479fb23ed8e01` is `FINAL_HEAD_REHEARSAL_PASS`. |
| STG-004 state | PR #38 merged the STG-004 runtime evidence. Exact SHA `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` passed PLAN, fresh PREFLIGHT, serial build/start, runtime verification, and isolated stop/start recovery. Server Staging remains running; Production remained unchanged. |
| STG-005 state | PLAN complete. The Owner approved CP-0 as a separate minimal Staging-only bootstrap implementation and accepted CP-4 as a feature-disabled KDS/Assembling boundary. Positive Kitchen/Assembling workflow remains `EVIDENCE_PENDING`. |
| STG-005A state | PR #40 merged the profile-gated synthetic bootstrap and append-only `V9__add_staging_synthetic_bootstrap_requests.sql` into `main`. This record does not prove V9 was applied or that bootstrap ran against server Staging. |
| AL-003 state | PR #58 preserves the failed-attempt evidence and PR #59's bounded PostgreSQL UID-70/mode-0700 private-leaf repair is `IN_MAIN` at `c3956592da8a33092ab745c7cc6aac05e9babfa7`. Neither record proves a new Staging deployment. |
| Staging Owner login prerequisite | `AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`; code audit proves an Organization Owner naturally accesses every same-Organization Store, so no explicit target Owner Store membership is required. Runtime bootstrap, credential, login, workspace, target onboarding, and API evidence remain pending. |
| Current permitted work | Review the stacked Draft PR queue #61-#65 and #67-#70 in dependency order. Independently review printer Store-isolation repair PR #66. Exact-SHA release planning and evidence templates may continue; all runtime inspection/mutation remains prohibited without new Owner approval. |
| Explicitly not permitted | Reusing old SHA approval/evidence; SSH/runtime mutation; deployment; Flyway; bootstrap; credential creation; login; source-menu writes; validate/execute; real clone; Production Store 1 access/mutation; or implementation outside the selected architecture/STG-005B package and explicitly bounded downstream preparation. |

The authoritative work records are [FEATURE_BACKLOG.md](../FEATURE_BACKLOG.md),
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md), and
[AL-001 technical plan](../agile/AL-001_OWNER_STORE_ONBOARDING_CHINATOWN_TECHNICAL_PLAN.md).

The current fully qualified modular Loop identifiers and their legacy-label
mapping are authoritative in
[STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](../agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md);
historical short `AL-004`/`AL-005` labels must not be interpreted as the new
packages without that mapping.

### Current AL-003 Git ground truth

| Package | Git state | Current capability boundary |
|---|---|---|
| PR-A, PR-B, PR-B2, PR-B3, PR-B4 | `IN_MAIN` | Contract, V10/idempotency foundation, revision/lock consistency, generic profile registry, and exact profile identity. |
| PR-C / PR #47 | `IN_MAIN` at `ba169ed8b689ddef8dffe94deee82fea191cdcfb` | Generic Category/Station/Item base transaction only. |
| PR-D / PR #52 | `IN_MAIN` via merge `13f26f1` | Generic source-option cloning and parent mapping are current-main capability. |
| PR-E / PR #54 | `IN_MAIN` via `82b8059f6af1c7dff4eeb1648ca47bec039b5e52` | Concrete versioned Chinatown Profile, target override composer, and bounded Small label compatibility are current-main capability. |
| PR-F0 / PR #55 | `IN_MAIN` via merge `6773fd0b78d7b3b33ee0d2a8b1d593a7b8c6af2` | Internal read-only option-plan composition/validation, shared execute parity validation, and bounded structured diagnostics. |
| PR-F / PR #56 | `IN_MAIN` via merge `8f909525781804f61d1da388882f530da358c3c4` | Protected Owner validate/execute API facade reusing the internal planner, V10 coordinator, and lock-owning transaction without a second clone engine. |
| Attempt evidence / PR #58 | `IN_MAIN` via merge `1482cddf4f10478ed571e4d7422100dc40006f6b` | Immutable record of the failed exact-SHA preflight and safe V8 runtime recovery. |
| Private-leaf repair / PR #59 | `IN_MAIN` via merge `c3956592da8a33092ab745c7cc6aac05e9babfa7` | Staging-only path-validation correction and governance rules; no runtime action or business/API change. |
| STG-005B Synthetic St-Denis baseline / PR #62 | `STACKED_DRAFT_WAITING_FOR_OWNER_REVIEW` | [Local evidence](STG-005B_SYNTHETIC_ST_DENIS_BASELINE_EVIDENCE.md) covers the guarded non-web planner/applier, 4/3/13/38 source graph, 4/3/17/74 target-plan compatibility, rollback, concurrency, and full backend regression. No runtime execution. |
| Owner decisions governance sync / PR #60 | `IN_MAIN` via merge `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d` | Documentation-only alignment of product direction, access semantics, FT-001 gaps, and runtime gates. |
| Modular Store provisioning architecture / PR #61 | `DRAFT_PR_WAITING_FOR_OWNER_REVIEW` | Defines Generic Engine + Versioned Profiles + Reusable Modules; no API/runtime behavior. |
| AL-003S Staging acceptance preparation / PR #63 | `STACKED_DRAFT_WAITING_FOR_OWNER_REVIEW_AND_RUNTIME_APPROVAL` | Adds a default-validation guarded STG-005A/STG-005B launcher, fresh passive resource/Production-continuity evidence collector, action/identity-bound approval gate, immutable image pin, exact command plan, evidence template, and rollback boundary. It performs no SSH, Docker runtime action, Flyway, bootstrap, login, API call, or clone. |
| AL-004 generic Store Profile contract / PR #64 | `STACKED_ONLY_WAITING_FOR_OWNER_REVIEW` | Adds exact Store-profile identity/version/composition, module policies, activation requirements, canonical fingerprinting, and safe summaries. It registers no concrete profile and has no endpoint, migration, provisioning engine, UI, or runtime action. |
| AL-005A Staff/Table module preparation / PR #65 | `AL-005A_PREPARED_WAITING_FOR_AL-004` (`STACKED_ONLY` Git classification) | Records the existing staff/access and dining-table authorities, reusable module contracts, security gaps, test gates, and Owner/schema decisions. It adds no writer, endpoint, migration, credential, table, or runtime action. |
| AL-005 Printing provisioning preparation / PR #67 | `AL-005_PRINTING_PREPARED_WAITING_FOR_DEPENDENCIES` (`STACKED_ONLY` Git classification) | Records existing printing authorities, profile/runtime boundaries, fixed Chinatown policy, prerequisite defects, staged contracts, and test gates. It adds no writer, endpoint, migration, printer, assignment, device, mode change, test print, or runtime action. |
| AL-005B Device/Pad provisioning preparation / PR #68 | `AL-005B_DEVICE_PREPARED_WAITING_FOR_DEPENDENCIES` (`STACKED_ONLY` Git classification) | Records pairing/auth/heartbeat/Store-wide queue authorities, profile/runtime boundaries, four-Pad Chinatown policy, prerequisite gaps, and readiness gates. It adds no writer, endpoint, migration, device, token, pairing, Worker change, or runtime action. |
| AL-006 Store activation preparation / PR #69 | `AL-006_ACTIVATION_PREPARED_WAITING_FOR_DEPENDENCIES` (`STACKED_ONLY` Git classification) | Defines the conceptual lifecycle, fail-closed evidence aggregation, Profile/module responsibility split, future exclusive activation writer, legacy direct-active compatibility gate, staged tests, and Owner decisions. It adds no endpoint, migration, status transition, or runtime action. |
| REL-001 Chinatown Production RC preparation / PR #70 | `REL-001_RC_PLAN_PREPARED_WAITING_FOR_STAGING_ACCEPTANCE_AND_OWNER_APPROVAL` (`STACKED_ONLY` Git classification) | Defines exact-SHA identity, Staging acceptance dependency, Production read-only gap scope, V8-V10 migration/compatibility matrix, backup/recovery gates, NO-GO conditions, rollback boundaries, and sanitized evidence. It names no candidate and performs no runtime action. |
| Printer Store-isolation repair / PR #66 | independent `DRAFT_PR_WAITING_FOR_OWNER_REVIEW` | Main-based prerequisite; not included in this stack. It must merge before an executable printing writer is promoted. |

PR-D promotion evidence is now historical main evidence: semantic source
`5a0dc09944b4b0945fe95027d7f12647212ea559`, reviewed promotion head
`5f6438ad1ffe1379eb3740a3db64180ce2433bfa`, and merge `13f26f1`.
PR-E entered `main` through PR #54 at
`82b8059f6af1c7dff4eeb1648ca47bec039b5e52`.
PR-F0 is rebuilt from that main commit, rather than promoted from its historical
stacked branch. Its evidence is
[AL-003 PR-F0 Read-only Planning Boundary](../agile/AL-003_PR_F0_READ_ONLY_PLANNING_BOUNDARY.md).

Current `main` contains the generic clone transaction, source-option layer,
complete Chinatown Profile, read-only planner, and protected Owner HTTP API.
This is repository capability only. No real clone has run. The latest retained
runtime evidence snapshots record Staging at `4397f995...` / Flyway V8 and
Production at `4667f3c` / Flyway V7; this package performed no fresh runtime
inspection and does not assert that those environments remain unchanged.

The architecture package remains in Draft PR #61. Dependency-bound STG-005B
Draft PR #62 is stacked above it and is not `IN_MAIN`. The separate
runtime acceptance prerequisite remains
`AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`.

The next stacked preparation is documented in
[AL-003S Staging Acceptance Preparation](../agile/AL-003S_STAGING_ACCEPTANCE_PREPARATION.md).
Its launcher closes the bounded non-web command-entry gap but does not authorize
runtime use. The package now provides a passive Production-continuity/resource
collector, but no runtime evidence has been collected. Independent review still
requires secret-safe release/environment rotation, same-image restart/Flyway
evidence, and a secret-safe Owner/API client before acceptance can be ready.
Local checks and explicit pending gates are retained in
[AL-003S Preparation Evidence](AL-003S_STAGING_ACCEPTANCE_PREPARATION_EVIDENCE.md).

The next dependency-bound code slice is documented in
[AL-004 Generic Store Profile Contract](../agile/AL-004_GENERIC_STORE_PROFILE_CONTRACT.md).
It is declarative only. No concrete Chinatown or St-Denis Store Profile is
registered, and no Store Profile can yet be selected or executed through an
Owner API.

The bounded downstream Staff/Table audit is documented in
[AL-005A Staff and Table Provisioning Module Plan](../agile/AL-005A_STAFF_TABLE_PROVISIONING_MODULE_PLAN.md).
It confirms that AL-002's internal onboarding staff service is the credential
and membership authority, while the current Platform Admin dining-table writer
is not safe as a provisioning upsert. AL-005A therefore remains a contract and
read-only-planner preparation until AL-004 is merged. Chinatown's existing
blank-table/manual-setup decision is retained; table-code, replay, and schema
gates apply only before a future predefined-table writer.

The downstream Printing audit is documented in
[AL-005 Printing Provisioning Module Plan](../agile/AL-005_PRINTING_PROVISIONING_MODULE_PLAN.md).
It keeps Store Profiles endpoint-free, preserves `DISABLED` until operational
acceptance, and records PR #66 plus strict-mode, a generic pre-job enabled-module
gate, role/assignment integrity, and idempotency as inactive-writer gates.
AL-005B device readiness is a later runtime-binding/activation gate, not a gate
for creating inactive logical configuration.

The dependent Device/Pad audit is documented in
[AL-005B Device and Pad Provisioning Module Plan](../agile/AL-005B_DEVICE_PAD_PROVISIONING_MODULE_PLAN.md).
It preserves the current Store-wide PAD_DIRECT queue with no per-device module
assignment and keeps identities, tokens, pairing, auto-print, and Worker health
outside versioned profiles. Executable work remains blocked by AL-005 and the
documented credential, idempotency, integrity, and runtime-evidence gates.

The bounded Activation audit is documented in
[AL-006 Store Activation Workflow Plan](../agile/AL-006_STORE_ACTIVATION_WORKFLOW_PLAN.md).
It records that no unified activation orchestrator exists, that conceptual
readiness stages are not current `stores.status` values, and that legacy direct
`active` writes, evidence persistence, verifier contracts, and runtime gates
must be resolved before a future exclusive activation transition is built.

The Production Release Candidate boundary is documented in
[REL-001 Chinatown Production Release Candidate Plan](../agile/REL-001_CHINATOWN_PRODUCTION_RELEASE_CANDIDATE_PLAN.md).
It requires one exact merged SHA to pass the same-SHA Staging acceptance plus a
fresh, separately approved Production gap/backup/compatibility review. No RC
SHA is selected, and no runtime evidence was collected by that package.
The current Production Compose uses a release-relative PostgreSQL bind path and
the current deploy helper combines backend/frontend builds without the retained
1 GiB memory gate. Production deployment is `NO-GO` until a bounded repair
preserves a fixed external state root and enforces guarded serial builds.

Git ground truth must always distinguish `MERGED_ON_GITHUB`, `IN_MAIN`,
`DEPLOYED_TO_STAGING`, and `DEPLOYED_TO_PRODUCTION`. A stacked PR merged into a
non-`main` base remains stacked-only until an independently reviewed promotion
enters `main`. Production runtime, current main, stacked-only development, and
unimplemented work are separate states.

Every code iteration must complete the mandatory governance sync in
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md) before its
review gate. The Planbook, Feature Backlog, System documentation, API contract,
and applicable technical plan must describe the same code and deployment
boundary as the commit under review.

At the start and end of each iteration, read this Planbook and verify Git and
any separately authorized runtime ground truth. Ground truth overrides stale
navigation, and governance drift must be corrected in the same iteration. The
Dependency Repair Auto-Loop and continuous-next-action rules are authoritative
in [AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md); they are
not duplicated here.

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
- PR #41 merged this contract into `main`; PR-B is the only authorized
  implementation package after that merge.

### AL-003 PR-B idempotency and transaction foundation record

- Branch: `codex/al-003-pr-b-idempotency-foundation`, based on `origin/main`
  `11be5c94f9b73e3beb8ec1f84b4a5a3c586c9d34` after PR #41.
- V9 remains owned by STG-005A. PR-B adds only the append-only
  `V10__add_owner_store_menu_clone_requests.sql` for durable request/evidence
  state, a four-column scope/key unique constraint, and a target-Store index.
- The coordinator implements insert-if-absent, pessimistic row locking,
  completed replay, fingerprint conflict, in-progress rejection, and bounded
  sanitized completion/failure evidence. It never stores a full menu request,
  credential, token, printer endpoint, or raw failure text.
- Owner-approved contract correction: replay exposes only durable scope,
  revision, count, result-code, and safe-warning summary; it exposes no menu ID
  maps. `FAILED` is terminal for its key, and any revalidated retry uses a new
  idempotency key.
- DTO, profile, exception, and transaction interfaces are compile-time
  foundations only. No Controller is registered and no menu graph clone,
  Store 1 read, Chinatown override, revision mutation, or runtime action is
  included.
- An isolated PostgreSQL 16.14 run applied V1-V10, verified V10's exact
  table/constraint/index, passed cloud-profile JPA validation and health, then
  restarted against the same database with schema 10 and no migration needed.
  Focused, concurrency/replay, full backend, compile, diff, and secret checks
  are the PR exit evidence.
- Evidence:
  [AL-003 PR-B PostgreSQL/Flyway V10 verification](AL-003_PR_B_POSTGRES_FLYWAY_V10_VERIFICATION.md).
- No SSH, Staging/Production access, Store 1 query, real menu clone, merge, or
  deployment occurred.
- PR #42 subsequently merged this foundation into `main`; its former review
  state is historical and is not the current AL-003 stop state.

Architecture anchor: shared provisioning infrastructure remains generic while
Chinatown differences stay in the first versioned Store Profile; future profile,
printing, activation, staff/table, and device modules are direction only.

Dependency repair and Store Profile governance are authoritative in
[AGILE_LOOP_OPERATING_MODEL.md](../AGILE_LOOP_OPERATING_MODEL.md).

### AL-003 exact-SHA Staging release gate

- Exact candidate: `8f909525781804f61d1da388882f530da358c3c4`, the
  merged PR #56 `origin/main` commit.
- A 2026-08-08 read-only preflight observed Staging still running the exact
  historical SHA `4397f995...` with Flyway V8, printing disabled, only
  `127.0.0.1:18080`, healthy frontend/backend/SockJS endpoints, and isolated
  project/network/database state.
- Production remained `main` at `4667f3c35f85c9f8538f82789d9df1531d4fbc9e`
  with its same container IDs, start times, running state, and zero restarts.
  No Production write or lifecycle command occurred.
- Host evidence reported two CPUs, about 1.7 GiB available memory, and about
  42 GiB free disk. These are point-in-time readings; the 1 GiB stop threshold
  and serial backend-then-nginx build remain mandatory.
- Staging is missing V9 and V10. Production is missing V8 through V10. Only
  Staging V9/V10 is eligible for a later exact-SHA deployment approval;
  Production remains out of scope.
- Existing preflight evidence and images belong to older SHAs and cannot be
  reused. Runtime compatibility of the retained V8-era images with a V10
  database is unproven, so application rollback to them after migration is
  `NO-GO` without a separate compatibility gate.
- Gate result: `GO` to request Owner exact-SHA deployment approval; `NO-GO`
  for immediate deployment. No candidate release, fresh formal evidence,
  build, start, migration, bootstrap, validate, execute, or clone occurred.
- Evidence:
  [AL-003 Staging Release Read-only Preflight Evidence](AL-003_STAGING_RELEASE_PREFLIGHT_EVIDENCE.md)
  and
  [AL-003 Exact-SHA Staging Release and Acceptance Plan](../agile/AL-003_STAGING_RELEASE_ACCEPTANCE_PLAN.md).
- Historical next state before the approved attempt:
  `AL-003_STAGING_RELEASE_PLAN_WAITING_FOR_OWNER_APPROVAL`.

### AL-003 exact-SHA deployment attempt

- PR #57 entered `main` at
  `f73fce9aa1c9abff1796715f3258dc4f6bb22207`. The Owner separately approved
  runtime release SHA `8f909525781804f61d1da388882f530da358c3c4`.
- The fresh pre-write baseline passed: old Staging was healthy at
  `4397f995...` / Flyway V8, printing was disabled, `18080` was loopback-only,
  resources exceeded thresholds, and Production continuity matched retained
  evidence.
- The independent Staging repository created a clean detached candidate
  release. The private identity was updated and only the old Staging project
  was stopped to free the formal-preflight port.
- Formal preflight returned `NO-GO` before build because its directory
  canonicalizer attempted to `cd` into the PostgreSQL-owned UID-70 mode-0700
  persistent data leaf. This is a real upgrade-path guard defect.
- The pre-migration recovery guard restored the old private identity and old
  Staging runtime. Flyway remains V8; frontend, backend health, and SockJS info
  returned 200; Production container IDs, start times, states, and zero
  restarts remained unchanged.
- No candidate image, V9/V10 migration, bootstrap, validate, execute, Store 1
  read, clone, or Production mutation occurred.
- The failed private evidence has SHA-256
  `c0c926e77bafeacb2ad972c2580417791814b323e4a3ab9fc05462c475f384b5`.
- Dependency Repair Gate: fix only the opaque PostgreSQL-leaf validation and
  its regression test. Do not weaken directory permissions, bypass evidence,
  or edit the approved release. A merged repair requires a new full-SHA Owner
  approval and fresh evidence.
- Evidence:
  [AL-003 Staging Release Attempt Evidence](AL-003_STAGING_RELEASE_ATTEMPT_EVIDENCE.md).
- Next state:
  `AL-003_STAGING_RELEASE_NO_GO_WAITING_FOR_OWNER_REPAIR_APPROVAL`.

### AL-003 PostgreSQL private-leaf dependency repair

- PR #58 merged the attempt evidence into `origin/main` at
  `1482cddf4f10478ed571e4d7422100dc40006f6b`.
- The repair keeps `/srv/restaurant-pos/staging/state/postgres` owner-only. It
  canonicalizes the traversable `state` parent, then validates the exact
  `postgres` directory entry, non-symlink topology, owner (deploy user or
  `postgres:16-alpine` UID 70), and mode `0700` without entering the leaf.
- The same protected-leaf semantics cover the formal server preflight and the
  `staging-deploy.sh` input gate so a successful formal preflight is not
  followed by the same false rejection before build.
- Regression fixtures cover a non-traversable UID-70/mode-0700 leaf, leaf and
  parent symlink replacement, missing leaf, unexpected owner/mode, and the
  existing exact-SHA/evidence/printing/isolation guards.
- This package performed no SSH, Docker lifecycle operation, Flyway execution,
  bootstrap, validate, execute, clone, or Production/Staging mutation.
- Evidence:
  [AL-003 Staging Preflight Private-Leaf Repair Evidence](AL-003_STAGING_PREFLIGHT_REPAIR_EVIDENCE.md).
- Git state: `IN_MAIN` through PR #59 at
  `c3956592da8a33092ab745c7cc6aac05e9babfa7`; runtime deployment remains
  separately unverified and unauthorized.

### AL-003 Staging Owner login prerequisite

- Status: `AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`.
- Retained evidence says Staging remains Flyway V8 and STG-005A has never run;
  the repair did not query runtime tables. Exact user/membership row presence
  is therefore `EVIDENCE_PENDING`, not inferred absent.
- Repository inspection confirms STG-005A can create a synthetic Organization,
  source Store, Owner credential, active Organization membership, and active
  source-Store membership. It does not create a target Store. The formal
  onboarding API creates the inactive target and target-scoped staff.
- `StoreAccessService` grants an authenticated `OWNER` access to every Store
  whose Organization has the Owner's active Organization membership. No
  redundant target-Store membership is required for that Owner; Manager,
  Frontdesk, and other Store-scoped staff still require explicit memberships.
- No retained evidence establishes a known safe Staging credential, successful
  Owner login, target access, or authenticated clone API call.
- A future exact-SHA deployment cannot be labeled Staging acceptance-ready
  until an Owner-approved synthetic-only preparation proves bootstrap
  idempotency, target creation/access, Owner login, and authorization for
  validate/execute. Production credentials, raw SQL, authorization bypasses,
  and copied business data remain forbidden.

### Owner decisions and FT-001 direction (2026-08-08)

- Chinatown is the second planned real Production Store. The FT-001 endpoint
  is a Production-ready Store with access/staff, menu, tables, printing,
  devices, login, and actual order/print acceptance, not merely a Store row or
  clone response.
- The reviewed Chinatown Profile is frozen as the initial Production menu
  contract. Production Store 1 remains the live clone source and requires a
  separately approved read-only drift capture before Production clone.
- First initialization must use the generic clone engine and versioned profile;
  later ordinary changes may use Menu Management.
- Future Owner provisioning must offer Chinatown and St-Denis menu templates
  through the same generic engine. `ST_DENIS_MENU` remains a planned profile,
  not a current API/profile capability.
- Staging is a long-lived Production-like environment with synthetic-only data.
  No Production credential, database copy, customer/order/payment data, real
  printer, or device secret may enter it.
- Production release follows exact-SHA Staging acceptance, gap audit, formal
  Release Candidate, migration and backup/rollback review, Owner approval,
  exact-SHA deployment, and post-deploy verification.
- The complete gap matrix and bounded loop order are maintained in
  [FEATURE_BACKLOG.md](../FEATURE_BACKLOG.md). Recording those loops does not
  authorize their implementation or any runtime mutation.
- The modular target and classification gate are maintained in
  [STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md](../agile/STORE_PROVISIONING_MODULAR_ARCHITECTURE_PLAN.md).

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
