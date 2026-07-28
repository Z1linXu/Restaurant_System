# Isolated Staging Package

This package is for an Owner-approved, non-production Staging release only. It
does not replace the production package in `deployment/cloud/docker-compose.yml`.
The production helper remains `deploy.sh`; do not use it for Staging.

## Hard boundary

Staging is designed for a future same-host deployment with these fixed defaults:

- root: `/srv/restaurant-pos/staging`;
- Compose project: `restaurant-pos-staging`;
- HTTP: `127.0.0.1:18080` only;
- database: an independent PostgreSQL 16 data directory;
- images: SHA-specific `staging-<full-sha>` tags;
- initial printing: application-level `DISABLED` only;
- data: empty or synthetic only.

Do not use production credentials, production database data, a production
PostgreSQL data path, real accounts, device credentials, printer endpoints, or
`REAL`/`PAD_DIRECT` printing in Staging.

## Release layout

The Staging wrapper validates that its checkout is exactly:

```text
/srv/restaurant-pos/staging/releases/<full-commit-sha>
```

It also validates the external environment file at:

```text
/srv/restaurant-pos/staging/config/.env.staging
```

Prepare the persistent directories outside the release checkout before any
validation. They must be real directories, not symlinks:

```bash
mkdir -p /srv/restaurant-pos/staging/config
mkdir -p /srv/restaurant-pos/staging/state/postgres
chmod 700 /srv/restaurant-pos/staging/config
chmod 700 /srv/restaurant-pos/staging/state/postgres
```

The operator must create a detached worktree at the approved full SHA. The
wrapper checks both the physical release path and `git rev-parse HEAD`, so a
branch movement cannot silently change the tested artifact.

## Configuration

Copy [`.env.staging.example`](.env.staging.example) to the external config path
and replace every placeholder with unique Staging-only values. Never store the
filled file inside a release worktree or commit it.

Required properties include:

- `COMPOSE_PROJECT_NAME=restaurant-pos-staging`;
- `STAGING_COMMIT_SHA` as a lowercase full 40-character SHA;
- `STAGING_POSTGRES_DATA_DIR=/srv/restaurant-pos/staging/state/postgres`;
- `HTTP_BIND_ADDRESS=127.0.0.1` and `HTTP_PORT=18080`;
- database name and user containing `staging`;
- unique database password and JWT secret, at least 16 and 32 characters;
- SHA-specific backend/frontend image tags and frontend build version;
- `STAGING_PRINT_MODE=DISABLED` and `STAGING_PRINTING_FEATURE_ENABLED=false`.

The guard rejects blank SHA values, `:local` tags, ports 80/443, public binds,
relative or symlinked PostgreSQL paths, production-like database defaults,
placeholder secrets, `REAL`, `PAD_DIRECT`, and any configured printer endpoint.

For `STAGING_PRINT_MODE=DISABLED`, the package sets the actual backend
property `APP_FEATURES_PRINTING=false`. The resolved Compose configuration is
checked for that exact value before any build or start. This disables printing
at the application Feature Flag layer, including when a synthetic Store was
mistakenly created with an enabled Store mode.

`STAGING_PRINT_MODE` does not create or modify Store database rows. Any
synthetic Store created for Staging must have its actual `printing_mode` set to
`DISABLED` before normal smoke tests. Server/default Staging does not permit
`MOCK`, because STG-002 has no application-level allowlist proving that a Store
or API cannot be changed to `REAL` or `PAD_DIRECT`. `MOCK` is accepted only by
the no-deploy `--local-validate` rehearsal path, which validates package input
and does not start a backend. Actual mock Print Job execution is deferred to a
future reviewed allowlist implementation. The guard rejects `REAL`,
`PAD_DIRECT`, and any `STAGING_PRINTER_ENDPOINT` declaration.

The dotenv parser accepts only unambiguous `KEY=value` or fully quoted values.
It rejects duplicate keys, inline comments, whitespace in unquoted values, and
dotenv interpolation. The deployment process rejects ambient Docker/Compose and
Compose interpolation variables, then invokes Compose through `env -i` with
`docker --context default`. The config file must be owned by the deploying user
with mode `0600`, and the config directory must be mode `0700` in server mode.
The wrapper caps Staging at 2.00 CPUs and 1408m total container memory
(PostgreSQL 0.75/512m, backend 1.00/768m, Nginx 0.25/128m), backend JVM heap at
512m, and each local log at 10m with at most three files. These ceilings leave
headroom for the same-host production workload; Staging is not for load tests.

## Commands

Run the wrapper from any directory. It resolves its own location and always
passes `--project-name restaurant-pos-staging` to Compose. On a server it
accepts only the exact root `/srv/restaurant-pos/staging`; a similarly named
arbitrary path is rejected.

Validate without creating directories, building images, or starting containers:

```bash
/srv/restaurant-pos/staging/releases/<full-sha>/deployment/cloud/staging-deploy.sh \
  --env-file /srv/restaurant-pos/staging/config/.env.staging \
  --validate
```

`--dry-run` is an alias for `--validate`. Both call `docker compose config` but
never run `build`, `up`, `pull`, restore, Flyway clean, or a destructive command.
The resolved Compose output is inspected privately for guards and is never
printed because it may contain secrets.

Before a deployment, the wrapper copies the validated mode-`0600` environment
file to a private mode-`0600` snapshot under the Staging state root. Compose
uses that snapshot, not inherited caller variables. The wrapper rechecks the
release Git cleanliness, exact SHA, and source/snapshot digest before `build`
and `up`.

For STG-003 local package rehearsal only, an operator may use
`--local-validate` with a temporary physical root ending in
`/restaurant-pos/staging`. This forces validation mode and cannot build or
start containers. It may validate a local `MOCK` input shape only; it cannot
exercise actual mock printing. It exists only to test the package before server
use and does not weaken the default/server root guard.

After Owner approval of the exact SHA and successful validation, start Staging:

```bash
/srv/restaurant-pos/staging/releases/<full-sha>/deployment/cloud/staging-deploy.sh \
  --env-file /srv/restaurant-pos/staging/config/.env.staging
```

Run the loopback health check after services are ready:

```bash
/srv/restaurant-pos/staging/releases/<full-sha>/deployment/cloud/staging-health-check.sh \
  --env-file /srv/restaurant-pos/staging/config/.env.staging
```

To validate only the health-check configuration without sending HTTP requests,
add `--validate`. Its server mode has the same exact-root and symlink guards as
the deploy helper. `--local-validate` is available only for a no-request local
rehearsal and never starts a service.

## Compose contract

`docker-compose.staging.yml` is standalone. It does not merge with the
production Compose file, which prevents list-merge accidents involving ports or
volumes. It defines only `db`, `backend`, and `nginx`, with:

- project-scoped containers and network, because no `container_name` is set;
- PostgreSQL 16 with no host database port;
- loopback-only Nginx HTTP port 18080;
- build contexts from the exact Staging release checkout;
- bounded CPU/memory settings and Docker local log rotation;
- the existing HTTP Nginx template mounted read-only.

The first environment has no TLS or public domain. Access through an
Owner-approved SSH tunnel or private VPN only. A public subdomain, TLS, ingress
or firewall change needs a separate reviewed loop and is outside STG-002.

## Data, migration, and rollback

Start with an empty PostgreSQL database to validate Flyway V1-current and the
second startup. Add only synthetic Organizations, Stores, Owners and Staff for
functional acceptance. Raw production database copies are prohibited; a
sanitized copy requires a separately approved anonymization process.

Flyway is forward-only. Never run `Flyway clean`, manually edit schema history,
or use `docker compose down -v`. Before rolling an application image back,
verify that the older application can read the currently applied schema. Use a
corrective migration rather than schema deletion.

STG-002 intentionally has no backup or restore helper. Any future backup or
restore rehearsal must be project-guarded, target only a disposable Staging
database, and require a separate Owner-approved procedure.

## Resource protection

The package provides configurable CPU and memory limits plus Docker log
rotation. On a shared production host, build and synthetic smoke windows must
be Owner-approved and outside restaurant peak operations. Do not use this
environment for load, soak, or stress testing. Monitor host CPU, memory, disk,
and log retention before any same-host deployment.

## Operator stop conditions

Do not proceed if validation reports any unsafe guard, if the commit SHA differs
from the approved candidate, if the resolved project/port/path/image differs
from this document, if Flyway validation fails, if any Store is configured for
real printing, or if resource headroom has not been approved.
