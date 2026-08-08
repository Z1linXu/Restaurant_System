# REL-001 Chinatown Production Release Candidate Plan

> Status: `REL-001_RC_PLAN_PREPARED_WAITING_FOR_STAGING_ACCEPTANCE_AND_OWNER_APPROVAL`
>
> Git classification: `STACKED_ONLY`
>
> Candidate SHA: `EVIDENCE_PENDING`
>
> Runtime access: not performed

## 1. Purpose

REL-001 defines the exact evidence and approval package required before the
Chinatown capability can be deployed to Production. It does not choose a
candidate from an unmerged Draft stack, deploy an image, run Flyway, read Store
1, create Chinatown data, activate a Store, or modify either runtime.

The release rule is:

```text
exact candidate merged to main
        +
same exact SHA accepted on isolated Staging
        +
Production read-only gap evidence
        +
backup / rollback / compatibility evidence
        +
explicit Owner Production approval
        -> eligible for a separately approved Production deployment batch
```

`git pull latest` is never a release identity or approval mechanism.

## 2. Current evidence boundary

No remote inspection was performed for this package. The following are retained
historical evidence, not fresh runtime assertions:

| Environment | Retained runtime SHA | Retained Flyway level | Evidence boundary |
|---|---|---|---|
| Production | `4667f3c` | V7 | Owner/operator-confirmed historical baseline; formal release approval and current freshness are unproven. |
| Staging | `4397f995bdc56f35b4d65a6ee9b99ab966dc4e9c` | V8 | Historical STG-004 evidence; later AL-003 candidate attempts did not establish a completed replacement runtime. |
| Repository `main` at stack origin | `2058d7fcac6b4d2ee05f49f6e6e431d9ea96170d` | V1-V10 files present | Repository capability only; not deployed by this plan. |

The Draft stack above that `main` commit is not a Production candidate. A
candidate can be named only after all intended code packages are merged to
`main`, the tree is clean, and the full 40-character SHA is immutable for the
release review.

### Current P0 release-path conflict

The checked-in Production Compose mounts PostgreSQL from
`./data/postgres`, relative to `deployment/cloud/docker-compose.yml`. Running
that Compose file from a new detached release would resolve to that release's
own data path and could start against an empty database. Running the release
from the existing Production checkout would instead abandon the immutable
release boundary and collide with retained evidence that the checkout contains
untracked operational files.

Production deployment is therefore `NO-GO` until a bounded prerequisite repair
provides and tests one of these equivalent guarantees:

- a fixed external Production state root supplied explicitly at runtime; or
- a stable, separately guarded Compose control root whose mounts are proven to
  preserve the existing Production database and Nginx/certificate state while
  images are built from the exact detached SHA.

The repair must fail closed on missing, relative, symlink-replaced,
wrong-owner, wrong-project, or Staging-overlapping paths. It must never move,
copy, initialize, restore, or delete the current database as part of the guard.

## 3. RC identity contract

An RC record must bind all of these values:

- full 40-character Git SHA and verified `main` ancestry;
- backend and frontend image tags and immutable image IDs/digests;
- frontend build metadata tied to that SHA;
- migration file names and repository checksums for V1 through the candidate
  target version;
- exact Staging release/evidence identity for the same SHA;
- Production preflight evidence identity and digest;
- private environment/config digest without secret values;
- Compose project, checkout/release path, service names, network, mounts, and
  public bindings;
- current and target Flyway versions;
- printing and feature-flag intent;
- backup artifact metadata and separately reviewed recovery boundary;
- approval identity, time, scope, maintenance window, stop conditions, and
  rollback decision.

Changing code, image, environment digest, evidence digest, migration chain, or
deployment scope invalidates the approval. A fresh RC must be generated.

## 4. Dependency gates

REL-001 is not eligible for Production approval until:

1. the intended architecture/provisioning/repair packages are merged to
   `main`, not merely stacked or Draft;
2. the exact candidate passes full repository tests and build checks;
3. AL-003S completes exact-SHA synthetic Staging onboarding, Owner login,
   validate, execute, replay, restart, and source-invariance acceptance;
4. the candidate's Store Profile and required module configuration are fixed;
5. the relevant activation path is either implemented and accepted or the
   release is explicitly limited to deploy-only with no Store activation;
6. Production read-only evidence is freshly collected under a separate Owner
   approval;
7. Production backup, rollback, and old-application compatibility gates pass;
8. the Production deployment path preserves the existing external state root,
   enforces serial image builds, and enforces the approved resource threshold;
9. an explicit Owner approves that exact SHA and exact execution batch.

An AL-003S pass authorizes neither Production deployment nor Production Store
1 access. Those remain separate gates.

## 5. Production read-only gap audit

The future approved preflight may read only the minimum sanitized evidence
needed for a release decision:

### 5.1 Runtime identity and continuity

- current Production branch and full commit SHA;
- clean/dirty checkout summary without reading untracked file contents;
- Compose project and expected `db`, `backend`, `nginx` services;
- container/image IDs, creation times, restart counts, health and public ports;
- resource snapshot and disk headroom;
- current Spring profile and non-secret feature/printing mode values;
- current health endpoints and existing public frontend reachability.

### 5.2 Database and migrations

- `flyway_schema_history` version/script/checksum/success/timestamp only;
- PostgreSQL version;
- presence and shape of V8-V10 target tables/constraints/indexes only where
  needed after migration;
- Store-code normalization duplicate check if still relevant to onboarding;
- no customer, order, payment, credential, token, print payload, or staff
  personal data.

### 5.3 Store 1 menu source

Production Store 1 read is a separate, explicit Owner gate. If approved, the
scope is only:

- Store/menu revision identity;
- Category;
- Station;
- Menu Item;
- Menu Item Option;
- stable codes/SKUs, active state, prices, ordering, and option relationships.

It must not read orders, customers, staff, payment, inventory, printer, device,
credential, token, analytics, or raw operational payload data. The capture is
used to compare drift against the reviewed Chinatown Profile; it never silently
changes the Profile or writes Source Store data.

## 6. Migration matrix

The current repository chain contains append-only migrations through V10:

| Migration | Purpose | Retained Production gap | Release concern |
|---|---|---|---|
| V8 `add_owner_store_onboarding_requests` | Owner onboarding idempotency/evidence | historical Production V7 -> V8 pending | old backend compatibility after table addition; no automatic Store creation |
| V9 `add_staging_synthetic_bootstrap_requests` | guarded synthetic Staging bootstrap request evidence | historical Production V7 -> V9 pending as part of linear chain | table may exist in Production, but Staging-only guard must prevent command execution there |
| V10 `add_owner_store_menu_clone_requests` | Owner menu-clone idempotency/evidence | historical Production V7 -> V10 pending | no clone occurs without authorized API execution; old backend/schema compatibility must be proven |

The exact candidate's migration filenames and checksums must be compared with
both Staging acceptance evidence and the fresh Production Flyway history. This
plan does not claim that V8-V10 are applied anywhere beyond retained evidence.

### Migration execution gate

- migrations run only through the approved candidate backend startup;
- `Flyway clean`, `repair`, history edits, manual DDL, and migration mutation are
  forbidden;
- first startup must show only expected pending versions and JPA validation;
- second startup must show the target schema with no migration necessary;
- any checksum mismatch, failed row, unexpected version, unexpected schema, or
  destructive statement is `NO-GO`;
- Production migration happens in the deployment batch only after a fresh
  backup and explicit Owner approval.

## 7. Compatibility matrix

Before Production deployment, retain evidence for:

| Direction | Required proof | Why |
|---|---|---|
| candidate migration path from V7 -> V10 | candidate startup against a disposable isolated synthetic V7 PostgreSQL database, followed by a second no-migration startup | prove the Production-shaped migration/start path without touching live Production |
| candidate app -> V10 schema | exact-SHA Staging first/second startup and full acceptance | primary forward path |
| current Production app -> V10 schema | separately rehearsed startup and critical read/write smoke against isolated Staging | determines whether application rollback is possible after migration |
| currently supported Android/Web clients -> candidate backend | login, workspace, ordering/offline submit and API compatibility on Staging | avoid forcing an unplanned fleet update |
| candidate frontend -> candidate backend | exact-build smoke and stale-asset/cache checks | prevent chunk/API drift |

If the old Production backend cannot safely run on V10, application rollback
after migration is `NO-GO`; the release needs a roll-forward plan or an
Owner-approved, rehearsed database recovery strategy.

## 8. Backup and recovery gate

The retained Production backup record proves only that a non-empty file was
reported in July 2026. It does not prove freshness, integrity, or restorability.

Before deployment:

1. confirm the approved Production backup directory without exposing secrets;
2. complete an Owner-approved isolated restore rehearsal using the same backup
   and restore toolchain before declaring recovery viable;
3. create a fresh pre-deploy backup only in a separately approved write batch;
4. record sanitized filename hash, size, timestamp, format/tool version, and
   command result;
5. perform an approved format/integrity check on the final artifact without
   exposing contents;
6. ensure backup storage has enough free space and is outside destructive
   Compose volume operations;
7. verify a reviewed recovery procedure exists;
8. record recovery point and recovery time expectations.

The checked-in `restore-db.sh` uses `pg_restore --clean --if-exists` and is
destructive. It must never be run as an automatic rollback step or against
Production without separate Owner approval, a rehearsed target, traffic/write
control, and exact database identity confirmation.

`docker compose down -v`, database-directory deletion, `Flyway clean`, and
manual schema-history edits are never rollback procedures.

## 9. Deployment sequence template

The checked-in Production `deploy.sh` currently runs one combined
`docker compose build backend nginx`; it does not prove serial execution and
does not enforce the retained 1 GiB available-memory stop threshold. The
relative database mount conflict above is also unresolved. The executable
command batch is therefore blocked on a separately reviewed deployment-tooling
repair and may be prepared only after the exact SHA is approved.

Its conceptual order is:

```text
fresh passive Production preflight
  -> verify exact candidate and evidence/config digests
  -> verify resources and current Production continuity
  -> serial backend image build
  -> serial frontend image build
  -> inspect immutable image IDs
  -> enter approved write-quiescence / maintenance boundary
  -> fresh final database backup and integrity check
  -> start candidate with current Compose project only
  -> first-start Flyway/JPA/health checks
  -> second-start no-migration check if approved
  -> frontend/API/WebSocket smoke
  -> client compatibility smoke
  -> monitor logs/resources/restarts
  -> release evidence and Owner review
```

Production Store onboarding, Store 1 menu capture, Chinatown clone, printing
configuration, Pad pairing, activation, and business smoke are not silently
included in a deploy-only batch. ACT-001 owns those separately approved
runtime actions.

## 10. GO / NO-GO criteria

### GO to request Production deployment approval

- exact candidate SHA is merged to `main` and immutable;
- same SHA passed complete Staging acceptance;
- full backend/frontend/Android compatibility scope is documented and passed;
- Production preflight is fresh and sanitized;
- V7-to-target migration matrix and checksums match expectation;
- backup, isolated restore rehearsal, final integrity, and recovery gates are
  accepted;
- old-application compatibility or an explicit roll-forward-only strategy is
  approved;
- resources and maintenance window are adequate, including at least 1 GiB
  available memory immediately before each serial build/start phase;
- Production Compose/project/path identity is exact;
- the deployment wrapper preserves an explicit fixed Production state root and
  enforces backend-then-frontend serial builds;
- printing and unrelated domain behavior are unchanged by deployment;
- no open P0/P1 or unresolved migration/security blocker applies;
- Owner approves the exact SHA, evidence digests, actions, and stop conditions.

### Immediate NO-GO

- candidate differs from Staging-accepted SHA;
- any dependency is Draft/stacked-only rather than in `main`;
- Production SHA, schema, Compose project, service, mount, port, profile, or
  resource state differs materially from the reviewed preflight;
- the candidate Compose can resolve database/certificate/Nginx state relative
  to a detached release rather than the approved fixed Production state root;
- the deployment path still uses a combined backend/frontend build or lacks the
  1 GiB available-memory stop gate;
- backup is absent, stale beyond Owner policy, failed, or unreviewed;
- the final backup is not protected by the approved write-quiescence boundary,
  fails integrity checks, or the restore toolchain has no isolated rehearsal;
- Flyway history/checksum differs, migration failed, or JPA validation fails;
- old app/schema rollback boundary is unknown and no approved roll-forward plan
  exists;
- runtime secrets/config would be copied from Staging or exposed;
- print mode or routing changes unexpectedly;
- Production Store 1 would be modified during source capture;
- an execution step requires `down -v`, restore, clean/repair, manual SQL, or
  bypassed authorization;
- health, login, ordering, supported-client compatibility, or Production
  continuity fails.

## 11. Rollback boundaries

### Before migration commit

Stop and leave current Production containers/data unchanged. Remove no volumes
and do not alter the Production checkout to recover from a failed preflight or
build.

### After additive migration, before business provisioning

Prefer a previously proven old-app-on-new-schema rollback only if its exact
image is retained and compatibility passed. Otherwise stop, stabilize, and use
the approved roll-forward plan. Do not restore merely because application
startup failed.

### After Chinatown provisioning or activation

This belongs to ACT-001, not REL-001. Application rollback alone may not undo
created Store/menu/access/printing/device state. ACT-001 must define reversible
module actions, terminal evidence, and manual containment without deleting
orders, payments, print jobs, or shared source data.

### Database restore

Last resort only, separately approved and rehearsed. A restore affects all
Production data since the recovery point and is not an automatic module
compensation.

## 12. Sanitized evidence template

```text
REL-001 evidence id:
observed_at / timezone:
operator / approval reference:

candidate_sha:
candidate_main_ancestry: PASS | NO_GO
staging_accepted_sha:
staging_acceptance_evidence_digest:

production_sha_before:
production_compose_project:
production_services:
production_container_ids_before (short):
production_image_ids_before (short):
production_health_before:
production_state_root_identity (sanitized):
production_mount_binding_verified:
state_root_owner_mode_symlink_guard:
deployment_wrapper_gate:
memory_before_backend_build:
memory_before_frontend_build:
memory_before_start:
observed_build_order:

flyway_before:
expected_pending_migrations:
migration_file_checksums:
flyway_after:
second_start_no_migration:
jpa_validation:

backup_filename_hash:
backup_size:
backup_time:
backup_result:
write_quiescence_started_at:
write_quiescence_boundary_result:
backup_integrity_result:
restore_rehearsal_reference:
isolated_restore_rehearsal_result:

candidate_backend_image:
candidate_frontend_image:
container_ids_after (short):
health_after:
frontend_after:
ws_info_after:
client_compatibility:
printing_continuity:
production_data_scope_check:

rollback_mode:
stop_condition_triggered:
final_result: PASS | NO_GO | EVIDENCE_PENDING
sanitized_warnings:
```

Never record secrets, full environment output, passwords/hashes, JWTs, tokens,
authorization headers, customer/order details, printer endpoints, raw print
payloads, or database backup contents.

## 13. Owner checkpoints

| Checkpoint | Required decision | No implied authorization |
|---|---|---|
| `REL-CP0` | approve exact RC scope and intended merged packages | no runtime read |
| `REL-CP1` | approve bounded Production read-only preflight and Store 1 source scope separately | no write/deploy |
| `REL-CP2` | approve fresh backup action and maintenance window | no deploy |
| `REL-CP3` | approve exact SHA, evidence/config digests, migration, serial build/start batch, and stop conditions | no Chinatown provisioning |
| `REL-CP4` | review post-deploy evidence and decide whether ACT-001 may begin | no automatic activation |

## 14. Acceptance criteria for this planning package

- RC identity cannot drift from merged `main` or Staging acceptance.
- Historical runtime evidence is not represented as fresh verification.
- V8-V10 migration purpose, compatibility, and rollback limits are explicit.
- Store 1 read, Production deployment, and ACT-001 remain separate approvals.
- Backup existence is not confused with recoverability.
- No secret or business data enters the plan/evidence template.
- No SSH, Docker runtime, Flyway, database query/write, deployment, clone, or
  activation occurred.

## 15. Review status

Independent review completed in two passes. The initial review identified the
release-relative Production state path, non-serial build path, unsafe live-V7
migration rehearsal assumption, backup timing/recovery gaps, and governance
drift. Those findings are incorporated as fail-closed gates above. The final
review identified missing evidence-template fields; the template now records
the state-root guard, deployment wrapper, serial build order, phase-specific
memory, write-quiescence, backup integrity, and isolated restore rehearsal.

No finding was waived. Runtime verification and Owner approval remain pending.
