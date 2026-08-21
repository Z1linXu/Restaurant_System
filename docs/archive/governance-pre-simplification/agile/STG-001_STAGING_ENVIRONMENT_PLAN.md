# STG-001 Isolated Staging Environment Plan

> Loop ID: `STG-001`
>
> Phase: `DISCOVER -> PLAN`
>
> Status: `PLAN_COMPLETE_WAITING_FOR_OWNER_APPROVAL`
>
> Planned against repository commit:
> `eadf100295c351a5f14a80fb2fb6eea351c2931b`
>
> Planning branch: `codex/stg-001-staging-environment-plan`
>
> Last updated: 2026-07-28, America/Toronto

## 1. Purpose and boundary

This plan defines a repeatable Staging environment for Restaurant System. It
is intended to validate an exact reviewed commit before production deployment
without sharing production containers, database storage, credentials, customer
data, printer endpoints, or Android device identities.

This document is a plan only. STG-001 did not:

- connect to a server, database, domain, or Android device;
- run Docker, Flyway, a deployment script, or a smoke test;
- create or modify an environment file;
- copy production data;
- modify production Nginx, firewall rules, or open ports;
- create an account, Store, Organization, print job, or printer assignment;
- start AL-003 or change application behavior.

The initial Staging environment may share the production Ubuntu host only if
every isolation guard and resource gate in this plan is satisfied. Staging is
not a load-test environment.

## 2. Discovery baseline

### 2.1 Git baseline

| Item | Observed repository state |
|---|---|
| Starting branch | `codex/al-002-postgres-flyway-verification` |
| Starting HEAD | `b2459050449a9a381b008f4d4a9f98bc05aea3cf` |
| Local `main` before STG-001 | `69d02c343c45958a7548e0df556731c22f0199fd` |
| Latest `origin/main` after approved fetch | `eadf100295c351a5f14a80fb2fb6eea351c2931b` |
| Relationship | Local `main` was 37 commits behind and 0 ahead of `origin/main` |
| Planning branch base | Exact `origin/main` commit `eadf100295c351a5f14a80fb2fb6eea351c2931b` |
| Starting worktree | Clean |

The local `main` branch was not moved. This planning branch was created
directly from the fetched `origin/main`.

### 2.2 Executable deployment evidence reviewed

| Evidence | Current behavior relevant to Staging |
|---|---|
| `deployment/cloud/docker-compose.yml` | Defines `db`, `backend`, and `nginx`; no explicit Compose project name or container names. |
| `deployment/cloud/docker-compose.yml` | Uses `./data/postgres`, default image tags `restaurant-pos-backend:local` and `restaurant-pos-frontend:local`, and host ports 80/443. |
| `deployment/cloud/deploy.sh` | Uses `.env` and `docker-compose.yml`, creates local data directories, validates, builds `backend` and `nginx`, and starts the project. It does not pass an explicit project name. |
| `deployment/cloud/health-check.sh` | Checks frontend, `/api/v1/auth/me`, and an HTTP probe of `/ws`; it supports `ENV_FILE`. |
| `deployment/cloud/backup-db.sh` | Uses host `pg_dump` and requires `DB_HOST`, although `.env.example` does not define `DB_HOST` and the Compose database does not publish a host port. |
| `deployment/cloud/restore-db.sh` | Uses destructive `pg_restore --clean --if-exists` after confirmation. It is not suitable for an automatic Staging path. |
| `deployment/cloud/.env.example` | Defines production-oriented default ports and shared `:local` image tags. It has no Staging identity or path guard. |
| `backend/src/main/resources/application-cloud.yml` | Enables Flyway with validation and JPA schema validation; disables unsafe default/demo/bootstrap seeds and auth fallback. |
| `ProductionSafetyConfig` | Provides cloud/profile startup safety checks, but does not prove Docker, path, image, port, or data isolation. |
| Migration directory | Contains additive Flyway migrations V1 through V8 at the planning baseline. |
| Cloud Nginx templates | Proxy `/api` and `/ws` to the backend. |
| Cloud runbooks and smoke checklist | Define production bootstrap, smoke, backup, and rollback boundaries but do not provide a Staging-specific command wrapper. |
| CI configuration | No checked-in GitHub Actions workflow was found for Staging package validation. |

The discovery reviewed these repository sources:

- `AGENTS.md`;
- `SYSTEM_DOCUMENTATION.md`;
- `docs/governance/AGILE_LOOP_OPERATING_MODEL.md`;
- `docs/governance/runtime/ALIVE_RUNTIME_PLANBOOK.md`;
- `deployment/cloud/docker-compose.yml`;
- `deployment/cloud/deploy.sh`;
- `deployment/cloud/health-check.sh`;
- `deployment/cloud/backup-db.sh`;
- `deployment/cloud/restore-db.sh`;
- `deployment/cloud/.env.example`;
- `deployment/cloud/application-cloud.yml.example`;
- `deployment/cloud/README_CLOUD_DEPLOYMENT.md`;
- `deployment/cloud/README_PRODUCTION_BOOTSTRAP.md`;
- `deployment/cloud/README_ROLLBACK.md`;
- `deployment/cloud/FINAL_SMOKE_TEST_CHECKLIST.md`;
- `deployment/cloud/nginx.http.conf.template`;
- `deployment/cloud/nginx.https.conf.template`;
- `README_SERVER_DEPLOY.md`;
- `README_GIT_DEPLOY_WORKFLOW.md`;
- `doc/RESTAURANT_POS_CLOUD_READY_MASTER_PLAN_AND_CODEX_SKILL.md`;
- `backend/src/main/resources/application-cloud.yml`;
- `backend/src/main/java/com/restaurant/system/common/config/ProductionSafetyConfig.java`;
- `backend/Dockerfile`;
- `frontend/Dockerfile`;
- all checked-in files under `backend/src/main/resources/db/migration/`.

### 2.3 Current architecture

The checked-in cloud package is a single Compose application:

```text
host ports 80/443
        |
      nginx
      /   \
   /api   /ws
      \   /
     backend
        |
       db
  ./data/postgres
```

This is a valid production-shaped package, but a second checkout using the
defaults is not isolated:

- both checkouts default to Compose project name `cloud`;
- both builds write the same host-wide `:local` image tags;
- both attempt to bind host ports 80 and 443;
- persistent paths are relative to the selected checkout;
- the deployment helper does not reject production-like values.

Therefore a copied `.env` or a second checkout alone is not a safe Staging
environment.

## 3. Non-negotiable isolation rules

Staging must fail closed unless all of these conditions are true:

1. Its exact full Git commit SHA is recorded before building.
2. Its worktree is outside the production checkout.
3. Its Compose project name is explicit and not the production project name.
4. Its image tags contain `staging` and the tested commit SHA.
5. Its PostgreSQL data path is outside every production data path.
6. Its database name, user, password, and JWT secret are Staging-only.
7. Its HTTP/HTTPS bindings do not use production ports.
8. Initial access is loopback-only or otherwise owner-approved and protected.
9. Store printing is `DISABLED` by default and may only be changed to `MOCK`
   for bounded acceptance tests.
10. No real printer endpoint, production device credential, customer data, or
    production account is present.
11. Unsafe seed flags and auth fallback remain disabled.
12. No command can infer the target project from the current directory alone.

If any guard cannot be proven from the resolved Compose configuration, the
action is `NO-GO`.

## 4. Recommended architecture

### 4.1 Directory and Git isolation

Recommended server layout:

```text
/home/ubuntu/Restaurant_System/                  production checkout (existing)

/srv/restaurant-pos/staging/
  repository.git/                               staging-only Git mirror
  releases/
    <full-commit-sha>/                           detached worktree
  config/
    .env.staging                                 mode 0600, never committed
  state/
    postgres/                                    staging PostgreSQL only
    nginx/
    certbot-www/
    letsencrypt/
    backups/
  evidence/
    <full-commit-sha>/
```

The exact root is an Owner decision. The important rule is that the Staging
release and state roots are not children of the production checkout and never
resolve to the production data directory.

Recommended release procedure:

1. Owner approves the full PR head or merged candidate SHA.
2. Fetch only into the Staging Git mirror.
3. Create a detached worktree named by the full SHA.
4. Verify `git rev-parse HEAD` equals the approved SHA and the worktree is
   clean.
5. Build images tagged with that SHA.
6. Record the SHA, image IDs, resolved Compose project, and smoke evidence.

Branch names are navigation aids only. Staging evidence is bound to the full
commit SHA. A force-pushed branch must not silently change the tested artifact.

### 4.2 Docker and Compose isolation

Use the same service contract (`db`, `backend`, `nginx`) but a Staging-specific
Compose layer and wrapper.

Required identities:

| Resource | Required Staging value |
|---|---|
| Compose project | `restaurant-pos-staging` |
| Backend image | `restaurant-pos-backend:staging-<short-sha>` |
| Frontend image | `restaurant-pos-frontend:staging-<short-sha>` |
| Network | Compose-scoped `restaurant-pos-staging_restaurant-pos` |
| PostgreSQL storage | Dedicated Staging state path or dedicated Staging named volume |
| Nginx state | Dedicated Staging state path |
| Container names | Do not set `container_name`; retain project-scoped names |

The future wrapper must pass `--project-name restaurant-pos-staging` on every
Compose call. `COMPOSE_PROJECT_NAME` in `.env.staging` is defense in depth, not
a substitute for the explicit CLI argument.

Recommended implementation shape:

- Parameterize the base Compose file's host bind address and persistent data
  roots while preserving current production defaults.
- Add `docker-compose.staging.yml` for Staging resource limits, log rotation,
  and Staging-specific hardening.
- Add a dedicated `staging-deploy.sh` that resolves and validates both files,
  refuses unsafe values, and then uses the exact same project/file arguments
  for `config`, `build`, `up`, `ps`, logs, and health checks.

A full standalone Staging Compose file is the safer fallback if list-merge
semantics would retain production ports or mounts. A generic invocation such
as `docker compose up -d` from the Staging checkout must not be documented as a
supported Staging operation.

Before any future `build` or `up`, the wrapper must inspect the fully resolved
configuration and reject:

- project name `cloud`, blank, or equal to the recorded production project;
- image tags without `staging-<sha>`;
- host ports 80 or 443;
- non-loopback initial port bindings;
- a PostgreSQL source path equal to or nested under the production path;
- an unset or repository-relative Staging state root;
- missing Staging-only database/JWT inputs;
- `SPRING_PROFILES_ACTIVE` other than the approved secure profile;
- unsafe seed, auth fallback, role-switcher, or developer-tool values.

### 4.3 Database isolation

Staging uses its own PostgreSQL 16 container and:

- unique database name;
- unique database user;
- unique random password;
- dedicated data path or named volume;
- no published PostgreSQL host port;
- no link, mount, or credential shared with production.

The recommended data progression is:

| Option | Use | Decision |
|---|---|---|
| Empty database | Flyway V1-current, startup, checksum, and second-start validation | Required first |
| Synthetic fixture database | Login, Store isolation, ordering, offline, finish, and AL-002 acceptance | Required after empty-DB verification |
| Sanitized production-shaped copy | Rare compatibility investigation after a reviewed anonymization process | Deferred and not part of initial Staging |
| Raw production copy | None | Prohibited |

The empty and synthetic databases may be separate temporary databases inside
the Staging PostgreSQL project or separate disposable Staging projects. They
must never reuse the production volume.

The existing `backup-db.sh` host-connectivity assumption must be resolved
before it is called a Staging backup procedure. A future Staging backup helper
should be project-guarded, target the Staging database container only, and
write under the Staging state root. Restore remains an explicit,
Owner-approved rehearsal against a disposable Staging target.

### 4.4 Network isolation

Initial recommendation:

```text
127.0.0.1:18080 -> staging nginx:80
127.0.0.1:18443 -> staging nginx:443 (only if Staging TLS is explicitly added)
```

Access should initially use an Owner-approved SSH tunnel or private VPN. No
public firewall port is needed.

A Staging subdomain is useful later, but the current production Nginx already
owns host ports 80/443. Adding `staging.<domain>` safely requires a separately
reviewed ingress design, TLS certificate handling, access control, rate
limits, and production Nginx change. It is not part of the first Staging
implementation and was not performed in STG-001.

If an external Staging port is ever approved:

- restrict source IPs at the firewall or authenticated ingress;
- do not expose PostgreSQL;
- use TLS before entering credentials;
- never reuse production cookies, secrets, or accounts;
- record the port, firewall rule, expiration, and Owner approval.

## 5. Configuration isolation

The future `.env.staging` remains outside Git and should contain at least:

| Field | Rule |
|---|---|
| `COMPOSE_PROJECT_NAME` | Exactly `restaurant-pos-staging` |
| `STAGING_COMMIT_SHA` | Exact full SHA approved for the run |
| `STAGING_STATE_DIR` | Absolute Staging-only path |
| `HTTP_BIND_ADDRESS` | Initially `127.0.0.1` |
| `HTTP_PORT` | Recommended 18080 |
| `HTTPS_PORT` | Recommended 18443 if used |
| `DOMAIN` | Blank for initial loopback-only access |
| `NGINX_SERVER_NAME` | `_` until a reviewed Staging ingress exists |
| `FRONTEND_URL` | Explicit loopback URL for the Staging health helper |
| `POSTGRES_IMAGE_TAG` | Pinned PostgreSQL 16 tag or digest |
| `DB_NAME` | Staging-only name |
| `DB_USER` | Staging-only user |
| `DB_PASSWORD` | Unique Staging secret |
| `JWT_SECRET` | Unique Staging secret, never production JWT secret |
| `SPRING_PROFILES_ACTIVE` | `cloud`, unless a future reviewed Staging profile is added |
| `BACKEND_IMAGE` | `restaurant-pos-backend:staging-<sha>` |
| `FRONTEND_IMAGE` | `restaurant-pos-frontend:staging-<sha>` |
| `VITE_APP_BUILD_VERSION` | `staging-<full-or-short-sha>` |
| `JAVA_OPTS` | Bounded heap compatible with the host resource budget |
| `TZ` | Explicit test timezone |

Current cloud safety settings must remain explicit or resolve safely:

- Flyway enabled with validate-on-migrate;
- JPA `ddl-auto=validate`;
- `x-user-id` fallback disabled;
- role switcher and developer tools disabled;
- force-overwrite, default-user, demo-data, membership-supplement, and
  production-bootstrap seeds disabled;
- platform feature disabled unless separately approved.

`app.features.printing=true` only makes printing APIs available. It does not
select a Store's print transport. Every synthetic Staging Store must begin
with printing `DISABLED`. A bounded print-job test may set that synthetic Store
to `MOCK`; `REAL` is prohibited. `PAD_DIRECT` must not be paired to a real Pad
or real printer during initial Staging acceptance.

## 6. Flyway and schema verification

Staging evaluates the migration chain contained in the exact candidate SHA.
At the STG-001 baseline this is V1 through V8.

### 6.1 Before startup

1. Confirm exact candidate SHA and clean detached worktree.
2. Record migration filenames and local checksums.
3. Confirm the Staging database and storage target are not production.
4. Confirm a persistent Staging database has a current Staging-only backup
   before applying a new migration.
5. Review migrations for destructive SQL, long table locks, data backfill, and
   compatibility with the current production application version.
6. Confirm no `Flyway clean`, repair, history editing, or manual DDL is in the
   runbook.

### 6.2 Empty-database pass

1. Start the candidate backend against an empty PostgreSQL 16 Staging database.
2. Require application startup and Flyway success.
3. Read `flyway_schema_history` and require every repository migration through
   the candidate's latest version to be present with `success=true`.
4. Verify expected tables, indexes, and constraints for the candidate
   migration.
5. Stop and start the same candidate again.
6. Require no migration reapplication, checksum mismatch, or JPA validation
   error.

### 6.3 Synthetic-data upgrade pass

1. Build the previously approved schema/data fixture.
2. Start the candidate and apply only the pending forward migrations.
3. Repeat history and second-start checks.
4. Run the acceptance suite against synthetic records.

Migration validation is a Staging result only. It does not authorize a
production migration.

## 7. Synthetic test data

All identities are clearly synthetic, environment-scoped, and disposable.
Secrets are generated at execution time, kept outside Git and reports, and
stored only as BCrypt hashes where application persistence requires them.

Minimum fixture:

- one fake Organization A with one owner;
- one source Store A and one target Store created through AL-002;
- one fake Organization B with a different owner for 403 checks;
- fake manager and frontdesk accounts;
- synthetic menu categories, items, options, tables, and prices;
- no customer names, phone numbers, payment data, production identifiers,
  printer IPs, or device tokens.

### 7.1 AL-002 acceptance in Staging

Use the owner onboarding API only against the synthetic Staging data:

1. Organization A owner can create a Store in Organization A.
2. The same Organization-scoped idempotency key and same request returns the
   original result and creates only one Store.
3. The same key with a different request returns the specified conflict.
4. Concurrent identical requests create only one Store.
5. Organization B owner cannot create in Organization A.
6. Created staff credentials are BCrypt-backed and responses/logs contain no
   password.
7. Created memberships expose only the target Store.
8. Store begins inactive with printing disabled.
9. No menu clone, WOK exclusion, printer policy, device pairing, or Owner UI is
   inferred as completed; those remain outside AL-002.

### 7.2 Store Code duplicate test

The synthetic suite should:

- submit two normalized-equivalent Store Codes within one Organization;
- test concurrent submissions of that duplicate;
- confirm current API behavior and durable idempotency behavior;
- query for normalized duplicates after the test;
- record the result without adding an unreviewed uniqueness migration.

The populated production duplicate risk remains unknown until a separately
approved production-safe check occurs.

## 8. Staging smoke-test checklist

Every line records candidate SHA, timestamp, tester, classification, and
sanitized evidence. A failure does not trigger automatic retries that could
duplicate orders or prints.

### 8.1 Platform and startup

- [ ] Resolved Compose project is `restaurant-pos-staging`.
- [ ] Only Staging containers, network, images, and mounts are referenced.
- [ ] PostgreSQL is healthy and has no host-published port.
- [ ] Backend starts with the approved profile and safety guard.
- [ ] Nginx serves the candidate frontend and proxies `/api` and `/ws`.
- [ ] Build metadata identifies the candidate SHA.
- [ ] Flyway empty-DB first and second startup checks pass.
- [ ] Synthetic upgrade first and second startup checks pass.

### 8.2 Authentication and Store isolation

- [ ] Synthetic owner and staff can log in.
- [ ] Workspace lists only memberships allowed for that account.
- [ ] Direct cross-Organization and cross-Store API/URL attempts return 403.
- [ ] No fallback identity header or development role switcher is enabled.

### 8.3 Ordering and recovery

- [ ] A synthetic table order can be started and submitted.
- [ ] Refresh/crash-style reload restores an unsubmitted local draft.
- [ ] A bounded network interruption queues one order with one stable
      idempotency key.
- [ ] Recovery submits once and does not duplicate order, KDS task, or print
      dispatch records.
- [ ] UI distinguishes local-only, queued, submitting, submitted, and conflict.
- [ ] Finish releases the table and a new order receives new local/client IDs.
- [ ] A second browser/Pad receives the table/order refresh without a polling
      storm.

### 8.4 Printing boundary

- [ ] Initial Store printing mode is `DISABLED`.
- [ ] If approved for this run, switching the synthetic Store to `MOCK`
      produces only mock print records.
- [ ] GRAB, FRONTDESK_RECEIPT, and HOT_KITCHEN routing is inspected without a
      real printer endpoint.
- [ ] No `REAL` server socket call occurs.
- [ ] No real PAD_DIRECT device is paired and no physical print is triggered.
- [ ] Failed/reprint behavior is not auto-retried.

### 8.5 Android boundary

- [ ] If Android Staging acceptance is approved, use a dedicated test Pad or
      emulator with no production pairing.
- [ ] Confirm its frontend/backend origin is Staging.
- [ ] Confirm no production Device ID/token or printer endpoint is copied.
- [ ] Validate menu cache/draft behavior only with synthetic Staging orders.
- [ ] APK installation and device modification require a separate Owner
      approval; they are not implied by this plan.

### 8.6 Restart and operations

- [ ] Restarting only the Staging project preserves Staging data.
- [ ] Production containers and health are unaffected.
- [ ] Worker/scheduler behavior does not create duplicate jobs.
- [ ] Container restart counts, health, CPU, memory, disk, and logs remain
      within the approved budget.
- [ ] Staging-only backup metadata is captured.
- [ ] A restore rehearsal, if separately approved, targets a disposable
      Staging database and never production.

## 9. Resource protection on a shared host

Before same-host implementation, record total production headroom during
business and off-hours. Suggested initial caps are planning values, not proven
safe limits:

| Service | Suggested initial ceiling |
|---|---|
| Staging PostgreSQL | 1 CPU, 1 GiB memory |
| Staging backend | 1 CPU, 768 MiB memory; bounded JVM heap |
| Staging Nginx | 0.25 CPU, 128 MiB memory |
| Total Staging | At most 2 CPU and 2 GiB memory after headroom review |

Implementation must also provide:

- Docker log rotation, initially 10 MiB per file and three files;
- disk-space preflight and stop threshold for images, build cache, state, and
  backups;
- bounded synthetic data and retention;
- no continuous high-frequency poll or log verbosity;
- no builds, migration rehearsal, or disruptive tests during restaurant
  service unless the Owner approves a maintenance window;
- no load, soak, concurrency-volume, or pressure testing on the shared host.

Prefer building in CI or on a separate build machine and transferring a
content-addressed artifact. If server-side builds are retained initially,
schedule them off-hours and verify production resource health before and after.

## 10. Release gates

### 10.1 Gate to merge an application PR

All applicable items are required:

- exact PR head SHA recorded;
- automated backend/frontend/Android checks pass as applicable;
- migration review and empty PostgreSQL verification pass;
- second startup has no Flyway/JPA error;
- Store/auth/idempotency/security tests pass;
- resolved Staging configuration proves isolation;
- synthetic Staging smoke checks pass;
- no secret, real endpoint, customer data, or generated environment file is in
  the diff;
- review records the limitations and Owner approval.

If the merge commit SHA differs from the tested PR head, the exact merge
candidate must be rebuilt and at least migration/startup and critical smoke
checks repeated.

### 10.2 Gate to deploy production

Merge alone is insufficient. Production deployment additionally requires:

- exact production candidate SHA and image identity;
- Owner approval for deployment and any migration;
- reviewed backup and rollback evidence;
- forward and backward schema compatibility assessment;
- successful Staging acceptance for the exact candidate;
- production environment preflight and maintenance timing;
- explicit post-deploy health/smoke plan;
- no open `NO-GO` finding.

### 10.3 NO-GO conditions

Do not build, start, merge, or deploy as applicable if:

- project, image, network, mount, port, credential, or secret isolation is
  missing;
- a Staging command resolves to production project `cloud`;
- PostgreSQL storage resolves to production data;
- host ports 80/443 or an unprotected public port would be bound;
- a production secret, account, customer record, device identity, or printer
  endpoint is present;
- Store printing is `REAL` or a real PAD_DIRECT executor could consume work;
- an unsafe seed/auth/developer flag is enabled;
- Flyway validation/checksum or second startup fails;
- a migration is destructive or requires `clean`, repair, or history editing;
- the candidate SHA differs from the evidence;
- same-host CPU, memory, disk, or log guard cannot be established;
- an order/idempotency/Store-isolation test fails;
- required backup or schema-compatibility evidence is absent.

## 11. Rollback model

### 11.1 Application rollback

Retain the prior known Staging image tags and exact SHAs. Roll back the
Staging project only, using the explicit Staging project name and files.
Production containers are never part of a Staging rollback command.

An older application image may be selected only after confirming it can start
against the current Staging schema. Flyway normally validates the full applied
migration chain; an image that does not know a newer applied migration may
refuse startup.

### 11.2 Database rollback

Flyway migrations are forward-only:

- do not use `Flyway clean`;
- do not edit `flyway_schema_history`;
- do not run manual down migrations;
- prefer an additive corrective migration;
- use restore only for a separately approved disposable Staging rehearsal.

Never recommend or execute `docker compose down -v`. Stopping or replacing an
application container does not require deleting database storage.

## 12. Alternatives considered

| Architecture | Benefits | Why it is not the initial recommendation |
|---|---|---|
| Separate Staging server | Strongest host/resource/failure isolation; supports heavier tests | Additional cost and operations; preferred later if Staging usage grows |
| Same host with isolated project, state, loopback ports | Lowest initial cost; adequate for bounded smoke testing when guards are enforced | Still shares CPU, memory, disk, Docker daemon, and host failure domain |
| Kubernetes or managed platform | Strong deployment primitives and policy options | Excessive complexity for the current solo-developer pilot |
| Public high-numbered port | Easy access | Exposes login surface and is easy to leave open; loopback tunnel/VPN is safer |
| Staging subdomain through production Nginx | Familiar URL and TLS | Couples first Staging rollout to production ingress; requires a separately reviewed change |
| Raw production database clone | Realistic data shape | Prohibited because it copies customer/credential/business data and creates privacy risk |
| Reuse production database with another schema | Low storage | Fails isolation: migrations, users, queries, and cleanup can affect production |
| Reuse production containers with profiles | Fewer containers | A wrong profile/command can alter the live project and provides weak evidence isolation |

## 13. Precise file lists

### 13.1 STG-001 planning deliverables

STG-001 changes documentation only:

- `docs/governance/agile/STG-001_STAGING_ENVIRONMENT_PLAN.md`;
- `docs/governance/runtime/ALIVE_RUNTIME_PLANBOOK.md`;
- `docs/governance/AGILE_LOOP_OPERATING_MODEL.md`;
- `SYSTEM_DOCUMENTATION.md`.

`docs/governance/FEATURE_BACKLOG.md` remains unchanged because STG-001 is a
delivery-governance capability, not a new FT-001 business feature or an
authorization to advance AL-002.

### 13.2 Future implementation files

No file in this list is implemented by STG-001. A reviewed implementation loop
is expected to consider:

| File | Planned purpose |
|---|---|
| `deployment/cloud/docker-compose.yml` | Parameterize host bind and state paths with production-compatible defaults |
| `deployment/cloud/docker-compose.staging.yml` | Staging resource, logging, and isolation overrides |
| `deployment/cloud/.env.staging.example` | Placeholder-only Staging contract; no secrets |
| `deployment/cloud/staging-deploy.sh` | Explicit project/SHA/path/image/port guards and exact Compose invocation |
| `deployment/cloud/staging-health-check.sh` | Staging-only bounded health evidence |
| `deployment/cloud/staging-backup-db.sh` | Optional project-guarded Staging-only backup path |
| `deployment/cloud/README_STAGING.md` | Owner runbook and approval boundaries |
| `deployment/cloud/STAGING_SMOKE_TEST_CHECKLIST.md` | Executable acceptance checklist |
| `deployment/cloud/README_CLOUD_DEPLOYMENT.md` | Link and distinguish production from Staging |
| `deployment/cloud/README_ROLLBACK.md` | Add Staging rollback and schema compatibility notes |
| `.github/workflows/staging-config-validation.yml` | Optional non-deploying config/script validation |
| `SYSTEM_DOCUMENTATION.md` | Concise architecture summary and canonical link |

An implementation may choose a standalone Staging Compose file instead of an
override if resolved-list behavior cannot be made fail-safe. That decision
must be documented before server use.

## 14. Phased implementation plan

| Phase | Scope | Required verification | Rollback |
|---|---|---|---|
| STG-001 | Discovery and this plan | Document review, path verification, `git diff --check` | Revert documentation commit |
| STG-002 | Local-only Staging package | Compose config with synthetic env, shell syntax, guard failure tests; no server | Revert package PR |
| STG-003 | Local/CI isolated rehearsal | PostgreSQL 16 V1-current twice, synthetic smoke, no production access | Remove only disposable local Staging project/state |
| STG-004 | Owner-approved first same-host Staging deployment | Exact SHA, resolved isolation evidence, resource preflight, loopback health | Stop only Staging project; retain its volume; restore prior Staging image if compatible |
| STG-005 | Synthetic acceptance including AL-002 | Auth/Store/idempotency/concurrency/order/weak-network/MOCK checks | Reset only approved synthetic fixture through a reviewed Staging-only procedure |
| STG-006 | Operational hardening | Retention, backup metadata, approved disposable restore rehearsal, evidence template | Revert hardening config without touching production |

Each phase requires a separate branch, reviewable PR, and explicit transition.
STG-001 does not authorize STG-002, AL-003, production deployment, or
production migration.

## 15. Acceptance criteria for the Staging capability

The capability is acceptable only when:

1. An Owner can select an exact commit and reproduce the same isolated project.
2. Guard tests demonstrate that production project names, images, ports, paths,
   and secrets are rejected.
3. Empty and synthetic PostgreSQL 16 migration/startup checks pass twice.
4. Synthetic login, Store isolation, ordering, weak-network idempotency,
   finish, and AL-002 checks pass.
5. Printing begins disabled and approved tests remain MOCK-only.
6. No real Pad, printer, customer data, or production account is required.
7. Staging survives its own restart without affecting production.
8. CPU, memory, disk, and logs stay under the approved shared-host budget.
9. Evidence identifies exact Git SHA and image IDs.
10. Application and schema rollback limitations are understood before
    production deployment.

## 16. Risk register

| Risk | Severity | Control |
|---|---|---|
| Compose project collision with production `cloud` | Critical | Explicit `--project-name`, guard, resolved-config assertion |
| Shared `:local` image overwritten by Staging build | Critical | SHA-specific Staging image tags |
| Staging database mounts production data | Critical | Absolute Staging state root, canonical path comparison, NO-GO |
| Port collision or unintended public exposure | High | Loopback 18080/18443; firewall/ingress approval |
| Staging secret/account reused from production | Critical | Independently generated secrets and synthetic identities |
| Real print triggered | Critical | Store mode DISABLED/MOCK; no endpoint/device pairing |
| Migration makes older app unusable | High | Exact-SHA empty/upgrade/second-start and backward compatibility checks |
| Shared-host resource starvation | High | Limits, log rotation, off-hours build, no load test |
| Compose override silently appends unsafe list values | High | Inspect resolved config or use standalone Staging compose |
| Backup script targets wrong database | High | Project-guarded Staging helper; no generic host target |
| Test data mistaken for production | Medium | Explicit synthetic naming and environment banner/build version |
| Stale worktree tests wrong commit | High | Detached full-SHA worktree and clean-tree assertion |

## 17. Owner decisions required

The Owner must decide before STG-002 or first server use:

1. Same production host versus a separate Staging host.
2. Approved Staging root and production path to protect.
3. Loopback SSH tunnel/VPN access versus a separately designed Staging domain.
4. HTTP-only loopback initially versus Staging TLS.
5. Exact CPU, memory, disk, and log budget based on host headroom.
6. Whether server builds are allowed and the maintenance window.
7. Empty plus synthetic data only, or whether a future anonymization project
   is justified.
8. Staging persistence and data-retention period.
9. Whether a dedicated test Android Pad/emulator is in the first acceptance
   scope.
10. Who can approve Staging start/stop, migration, restore rehearsal, and
    promotion evidence.
11. Whether to implement an optional Staging-only backup helper in STG-002 or
    defer it to STG-006.

Until these decisions and STG-002 are approved, the state remains
`PLAN_COMPLETE_WAITING_FOR_OWNER_APPROVAL`.
