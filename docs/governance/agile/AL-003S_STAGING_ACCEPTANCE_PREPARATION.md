# AL-003S Staging Acceptance Preparation

> Package state: `IN_MAIN`; `STG-006_PASS`; OPS-001 repository tooling complete after reviewed merge
>
> Prepared against: STG-005B checkpoint `0aba8377a3b7acec047c6ffd025f774d8a4d5e87`
>
> Runtime checkpoint: `STG-008_BOOTSTRAP_PLAN_NO_GO_BEFORE_COMMAND_WITH_BLOCKED_STATE`

> Current runtime checkpoint: separate Owner authorization deployed exact
> `2837ae88e55142c99c6975f8b6575febffc913a1` V10-to-V10. Fresh formal
> preflight, repaired readiness, sanitized runtime/Flyway collection, one
> same-image restart and post-restart verification all passed with exact
> identities unchanged. `STG-007=PASS`; no synthetic bootstrap, source-menu,
> credential, login, onboarding or clone action followed.
>
> A later Owner-authorized STG-008 entry reconfirmed that runtime read-only,
> found no existing synthetic Owner/topology, proved the next Store ID is `1`,
> and stopped before `bootstrap-plan` because the requested credential
> convention conflicts with the launcher/application `STG005_` identity and
> 12-through-256 password contract. No one-shot or write occurred.
>
> The Owner then approved `STG005_OWNER_20260808_R01` without lowering the
> password guard. Fresh exact readiness passed and the password-free plan
> started one bounded one-shot. Spring stopped before the STG-005A command
> because the older shared cloud safety rule rejected this profile's required
> Flyway-disabled mode. Cleanup succeeded, topology stayed empty, and both
> blocked records were retained. PR #85 merged the bounded startup-safety
> repair and PR #86 closed its Ground Truth. A later recovery continuation
> reconfirmed the zero-write V10 baseline but stopped before Batch A mutation
> when the ordinary release path could not legally cross those retained
> records. PR #87 merged the dedicated blocked-state-safe release/env repair at
> `4b954e09...`; a later continuation used it to deploy exact `6753855497...`,
> pass fresh readiness and recover only the old reviewed pair. The following
> password-free plan failed before command/data access at the non-web
> request-context dependency. Its repair requires a new exact-main Staging
> release/preflight/deploy Owner approval, and recovery remains a bounded
> post-Batch-A action.

## 1. Purpose and classification

This package turns the reviewed AL-003 Staging sequence into an exact,
dependency-ordered command and evidence plan. It also supplies the missing
guarded launcher for the non-web STG-005A bootstrap and STG-005B synthetic
source-menu commands.

The changes are classified as:

- reusable operational tooling for isolated Staging;
- one-time acceptance evidence templates;
- no Store Profile rule;
- no shared clone-engine behavior;
- no runtime configuration value or credential.

It does not deploy, run Flyway, create credentials, start a one-shot container,
call an API, or execute a clone.

## 2. Dependency gates

The runtime sequence is `NO_GO` until all of the following are true:

1. Modular architecture PR #61 and STG-005B PR #62 are `IN_MAIN`.
2. Guarded AL-003S preparation PR #63 is `IN_MAIN` at
   `732d77c89ff067982702426ff918d5e097e1d0fb`.
3. A new full 40-character merged-main candidate SHA is selected.
4. The Owner approves that exact SHA and each bounded runtime-mutation batch.
5. A fresh release, private Staging environment digest, formal preflight PASS
   evidence, and reviewed evidence digest are created for that SHA.
6. Fresh read-only evidence proves Staging isolation, resource thresholds,
   `Printing=DISABLED`, and Production continuity.

Historical SHA approvals and evidence cannot be reused.

STG-007 satisfied the release/deployment prerequisites above for exact deployed
`2837ae88...`. The credential contract is now aligned. The first fresh plan
one-shot exposed a pre-command cloud/Flyway safety conflict and left the
launcher blocked with zero application writes. The bounded repair changes the
backend startup guard, so the deployed old image cannot consume it. PR #85 put
that repair in main. PR #87 subsequently put the recovery-only release/env
sequencing repair in main after the continuation stopped before mutation. A
resumed batch requires a newly approved latest-exact-main descendant of
`4b954e09...` for recovery-specific release/env binding and Staging deploy,
separately bounded post-Batch-A blocked-state recovery, fresh
readiness, and a distinct digest-bound approval artifact for every bootstrap/
source plan, create, and replay invocation; the password remains stdin-only
and must not be requested before that gate.

## 3. Guarded launcher contract

The launcher is
`deployment/cloud/staging-synthetic-acceptance.sh`.

It provides five actions:

| Action | Container | Database behavior | Required gates |
|---|---:|---|---|
| `validate` | none | none | exact release, env, preflight, project, print mode |
| `bootstrap-plan` | one-shot | guard validation only | `--execute-runtime`, fresh readiness, action-specific Owner approval |
| `bootstrap-execute` | one-shot | transactional STG-005A write/replay | `--execute-runtime`, fresh readiness, action-specific Owner approval, explicit action, password on non-interactive stdin |
| `source-menu-plan` | one-shot | read-only graph plan | `--execute-runtime`, fresh readiness, action-specific Owner approval |
| `source-menu-execute` | one-shot | transactional STG-005B create/replay | `--execute-runtime`, fresh readiness, action-specific Owner approval, explicit action |

Every one-shot uses the already-built exact-SHA backend image, the fixed
`restaurant-pos-staging` project, fixed `/srv/restaurant-pos/staging` root,
`cloud,staging-synthetic-bootstrap`, no dependencies, no web server, Flyway
disabled, and printing disabled. The launcher verifies the running backend
image identity, `127.0.0.1:18080`, and health before creating a one-shot
container. The backend tag must still resolve to the running image ID; the
one-shot is then pinned to that immutable `sha256:` ID through a private
Compose override.

Docker CLI state is created under a private mode-`0700` `mktemp` root. The
launcher does not read the operator's `~/.docker`, and cleanup runs for success,
failure, `INT`, and `TERM`.

`deployment/cloud/staging-acceptance-readiness.sh` is the bounded passive
collector for the final pre-command window. It records only sanitized resource
thresholds and exact Staging/Production container fingerprints. The launcher
accepts readiness evidence for at most 15 minutes, recalculates both project
fingerprints and resource thresholds, and requires an action-specific Owner
approval file bound to the exact action, SHA, synthetic identities, preflight
digest, and readiness digest. Evidence and approval files are re-hashed before
execution. The approval file is a procedural binding to an external Owner
review reference, not a cryptographic signature or substitute for restricted
runtime access.

One-shot actions are serialized with an owner-only lock in the fixed Staging
state directory, have a 600-second timeout, use 20-second bounded Docker
metadata/cleanup checks, and use a deterministic scoped
container name so interrupted cleanup cannot target another project. After the
one-shot exits, the launcher repeats release/evidence integrity, running image,
loopback health, memory, CPU, free-disk, normalized-load, and exact
Staging/Production fingerprint checks.

Action failure/timeout, failed post-check, cleanup that cannot be verified,
`INT`, or `TERM` attempts an authoritative blocked record while the state lock
is held plus a companion owner-only marker in fixed Staging state. At least one
must persist, and either independently blocks future actions; a missing marker
does not override an existing lock record.
Future actions fail closed until a separately approved Owner recovery confirms
container absence, transaction/idempotency state, and continuity, then removes
every blocked record that actually exists. The launcher rechecks blocked state
after locking and never auto-clears it.

OPS-001 same-image restart uses the same fail-closed model. Container
`running|NO_HEALTHCHECK` is not application readiness: after ordered starts it
requires bounded HTTP-200 convergence for backend health, frontend root and
`/ws/info`, followed by exact container/image/Flyway/project invariance. Every
nonzero exit after stop begins must persist blocked state before cleanup,
including explicit `die`, timeout, signal and evidence-emission failure.

The launcher does not accept a password or token argument. `bootstrap-execute`
inherits only a non-interactive stdin stream so the existing Java secret reader
can consume and clear the password.

## 4. Command plan

The following is an executable template, not an authorization. Replace
placeholders only inside an Owner-approved runtime session. Never place the
password, token, or idempotency key in shell history or evidence.

### 4.1 Launcher validation

```bash
RELEASE=/srv/restaurant-pos/staging/releases/<CANDIDATE_SHA>
ENV_FILE=/srv/restaurant-pos/staging/config/.env.staging
PREFLIGHT=/srv/restaurant-pos/staging/evidence/<PREFLIGHT_PASS_FILE>
READINESS=/srv/restaurant-pos/staging/evidence/<READINESS_PASS_FILE>
APPROVAL=/srv/restaurant-pos/staging/evidence/<ACTION_APPROVAL_FILE>

"$RELEASE/deployment/cloud/staging-synthetic-acceptance.sh" \
  --validate \
  --approved-sha <CANDIDATE_SHA> \
  --preflight-evidence "$PREFLIGHT" \
  --preflight-evidence-sha256 <REVIEWED_PREFLIGHT_SHA256> \
  --env-file "$ENV_FILE"
```

This action does not start a one-shot container or write application data.

### 4.2 Fresh readiness and action approval

Immediately before each separately approved one-shot batch, collect new
readiness evidence:

```bash
umask 077
"$RELEASE/deployment/cloud/staging-acceptance-readiness.sh" \
  --approved-sha <CANDIDATE_SHA> \
  --preflight-evidence "$PREFLIGHT" \
  --preflight-evidence-sha256 <REVIEWED_PREFLIGHT_SHA256> \
  --env-file "$ENV_FILE" \
  --production-project cloud \
  --min-available-memory-kb <OWNER_APPROVED_MINIMUM> \
  --min-cpu-count <OWNER_APPROVED_MINIMUM> \
  --min-free-disk-kb <OWNER_APPROVED_MINIMUM> \
  --max-load-per-cpu-milli <OWNER_APPROVED_MAXIMUM> >"$READINESS"
chmod 600 "$READINESS"
```

Hash the file, calculate the exact canonical request fingerprint described in
the guarded launcher runbook, and obtain a mode-`0600` Owner approval artifact
bound to the exact action, request fingerprint, preflight digest, and readiness
digest. Readiness expires after 15 minutes. Approval expiry cannot exceed 24
hours and does not extend readiness validity. This preparation does not create
or approve either runtime artifact.

### 4.3 STG-005A plan

```bash
"$RELEASE/deployment/cloud/staging-synthetic-acceptance.sh" \
  --execute-runtime \
  --action bootstrap-plan \
  --approved-sha <CANDIDATE_SHA> \
  --preflight-evidence "$PREFLIGHT" \
  --preflight-evidence-sha256 <REVIEWED_PREFLIGHT_SHA256> \
  --readiness-evidence "$READINESS" \
  --readiness-evidence-sha256 <READINESS_SHA256> \
  --action-approval "$APPROVAL" \
  --action-approval-sha256 <ACTION_APPROVAL_SHA256> \
  --env-file "$ENV_FILE" \
  --run-id STG005_<RUN> \
  --organization-name STG005_ORG_<RUN> \
  --organization-code STG005_ORG_<RUN> \
  --source-store-name STG005_SRC_<RUN> \
  --source-store-code STG005_SRC_<RUN> \
  --owner-login STG005_OWNER_<RUN> \
  --owner-name STG005_OWNER_<RUN>
```

Although the Java action is validation-only, this command creates a temporary
one-shot container. It therefore still requires runtime authorization.

### 4.4 STG-005A execute and exact replay

Use a secret-safe operator mechanism to pipe the same synthetic password to
stdin twice. Shell tracing must remain disabled. The command is identical to
4.3 except `--action bootstrap-execute`. Generate fresh readiness and approval
artifacts for that exact action. Do not put the password after an
option, in an environment file, or in evidence.

Required sanitized result sequence:

```text
STG005_BOOTSTRAP|status=CREATED|...|source_store_id=1|...
STG005_BOOTSTRAP|status=REPLAYED|...|source_store_id=1|...
```

Stop if source Store ID is not exactly `1`.

### 4.5 STG-005B plan, execute, and replay

```bash
"$RELEASE/deployment/cloud/staging-synthetic-acceptance.sh" \
  --execute-runtime \
  --action source-menu-plan \
  --approved-sha <CANDIDATE_SHA> \
  --preflight-evidence "$PREFLIGHT" \
  --preflight-evidence-sha256 <REVIEWED_PREFLIGHT_SHA256> \
  --readiness-evidence "$READINESS" \
  --readiness-evidence-sha256 <READINESS_SHA256> \
  --action-approval "$APPROVAL" \
  --action-approval-sha256 <ACTION_APPROVAL_SHA256> \
  --env-file "$ENV_FILE" \
  --source-store-id 1 \
  --source-store-code STG005_SRC_<RUN>
```

After a separate write checkpoint, replace `source-menu-plan` with
`source-menu-execute`; obtain action-matching readiness/approval evidence and
repeat the exact execute command once to prove replay.
Expected source results are 4 categories, 3 stations, 13 items, 38 options,
one revision increment on create, and no revision change on replay.

## 5. Remaining API sequence

The launcher intentionally does not automate authentication, target onboarding,
or menu-clone HTTP calls. Those calls require a separate reviewed client that
keeps passwords, bearer tokens, refresh tokens, and raw idempotency keys out of
argv, stdout, evidence, and shell history.

The authorized sequence, after the source graph passes, is:

1. synthetic Owner login;
2. `/auth/me` and Owner overview/workspace access evidence;
3. target Store onboarding and exact replay;
4. read-only menu-clone validation;
5. separate Owner checkpoint;
6. menu-clone execute and exact replay;
7. persistence/restart verification.

Until a secret-safe API client or a separately approved manual procedure is
reviewed, these steps remain `EVIDENCE_PENDING` and must not be improvised with
tokens in shell commands.

## 6. Evidence requirements

Use
[AL-003S Staging Acceptance Evidence Template](../runtime/AL-003S_STAGING_ACCEPTANCE_EVIDENCE_TEMPLATE.md).
Evidence may retain:

- exact SHA, image IDs, container names, project, network, loopback binding;
- sanitized Organization/Store/User IDs;
- sanitized result/status/error codes and counts;
- menu revisions and Flyway versions;
- HTTP status codes, resource summaries, and Production continuity metadata.

Evidence must not retain credentials, tokens, raw idempotency keys, request
bodies containing passwords, raw menu payloads, customer data, printer
endpoints, or device secrets.

The V9/V10 request rows are canonical durable evidence. Audit-log evidence is
supplementary because the shared audit service is best-effort.

## 7. Rollback boundary

- Before V9/V10 migration, stop and recover only through a separately reviewed
  exact-image/environment procedure.
- After V9/V10 migration, the retained V8-era `4397f995...` image is not an
  approved rollback target. Do not start it against the V10 database without a
  separate compatibility result.
- Failed STG-005A, STG-005B, onboarding, and clone transactions roll back their
  own partial writes.
- Successful synthetic commits are retained for evidence. Do not delete them
  with SQL, truncate, restore, Flyway clean/repair, or `down -v`.
- A successful application rollback does not roll back an append-only
  migration.

## 8. Independent review findings

The initial read-only audit accepted the dependency order and identified
bounded gaps. Final Agent 6 review additionally required serialized and bounded
actions, explicit procedural-approval semantics, post-action continuity, and
fresh disk/load checks. This package now supplies those controls alongside
fresh resource/Production-continuity fingerprinting, action-specific approval
binding, and immutable image-ID pinning. OPS-001 closes the three repository
tooling gaps while preserving these remaining runtime gates:

1. OPS-001 now publishes the secret-safe detached-release/four-field
   environment rotation helper;
2. OPS-001 now publishes the approval-bound same-container restart/Flyway
   evidence collector;
3. OPS-001 now publishes the secret-FD Owner/API acceptance client;
4. runtime use has separately proved release/env rotation, V10 redeploy,
   readiness, sanitized collection and valid same-image restart PASS evidence
   at exact `2837ae88...`; every future action still requires a distinct exact
   approval;
5. PostgreSQL 16 concurrency remains runtime evidence pending; local source
   graph concurrency uses H2;
6. STG-006 freshly observed the retained runtime SHAs/Flyway boundary, but did
   not create the candidate release or execute any acceptance action.

Repository completion does not permit claiming
`AL-003_STAGING_ACCEPTANCE_READY`. STG-007 and each later acceptance batch need
a fresh exact-SHA Owner runtime approval and retained evidence.

## 9. Acceptance and stop state

This preparation package passes when its shell tests, existing Staging guard
tests, backend regression, compile, scope scan, and governance checks pass.
Passing the package does not authorize or prove runtime acceptance.

The historical read-only entry result is recorded in
[STG-008 Synthetic Topology and Source Entry Evidence](../runtime/STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md).
The later plan failure, zero-write continuity and dependency repair are in
[STG-008 Flyway Guard Repair Evidence](../runtime/STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md).
The subsequent fresh baseline, release-rebind sequencing deadlock and PR #87
repair are in
[STG-008 Release-Rebind Serialization Repair Evidence](../runtime/STG-008_RELEASE_REBIND_SERIALIZATION_REPAIR_EVIDENCE.md).
The later exact `6753855497...` rebind/deploy/readiness/recovery continuation
passed, but its following password-free plan failed before command/data access
at the non-web request-context dependency. The new repair and current boundary
are recorded in
[STG-008 Non-Web Request-Context Repair Evidence](../runtime/STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_EVIDENCE.md).

Stop state:

`STG-008_NON_WEB_REQUEST_CONTEXT_REPAIR_REQUIRES_NEW_EXACT_SHA_STAGING_REBIND_AND_BLOCKED_STATE_RECOVERY_OWNER_RUNTIME_APPROVAL`
