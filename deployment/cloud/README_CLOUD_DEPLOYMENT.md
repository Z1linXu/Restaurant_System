# Restaurant POS Cloud Deployment Template

This folder is a cloud deployment architecture package. It is a template only:
it does not deploy to any server, does not connect to infrastructure, and does
not contain real secrets.

## Architecture

Recommended cloud shape:

- Backend: Spring Boot jar packaged into a backend container image.
- Frontend: React static build served by Nginx.
- Reverse proxy: Nginx proxies `/api` and `/ws` to the backend service.
- Database: PostgreSQL 16 container for the current single-server production
  deployment. Keep the existing `deployment/cloud/data/postgres` directory; do
  not remove it during application updates.
- TLS: terminate HTTPS with Nginx and certificates from a real certificate
  provider.
- Backups: use `backup-db.sh` with `pg_dump -Fc`.
- Restores: use `restore-db.sh` only after rehearsal against staging.

## Files

- `.env.example`: blank placeholders for deployment configuration.
- `docker-compose.yml`: `db`, `backend`, and `nginx` services.
- `nginx.http.conf.template`: HTTP static frontend, REST proxy, and WebSocket proxy.
- `nginx.https.conf.template`: HTTPS/Let's Encrypt production template.
- `nginx.conf.example`: reference-only Nginx example.
- `application-cloud.yml.example`: environment mapping reference for cloud.
- `deploy.sh`: compose validation and start template.
- `backup-db.sh`: custom-format PostgreSQL backup.
- `restore-db.sh`: explicit-confirm restore helper.
- `health-check.sh`: frontend and backend reachability checks.
- `README_PRODUCTION_BOOTSTRAP.md`: safe first organization/store/owner bootstrap runbook.
- `FINAL_SMOKE_TEST_CHECKLIST.md`: final pre-deploy and post-deploy pilot checklist.
- `bootstrap-template.sql.example`: reviewed skeleton only, not a production-ready script.
- `README_ROLLBACK.md`: rollback checklist.

## Environment Setup

Copy `.env.example` to `.env` on the server and fill values there. Do not commit
the filled `.env` file.

Required values:

- `DOMAIN`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`

Optional image names default to local build tags:

- `BACKEND_IMAGE=restaurant-pos-backend:local`
- `FRONTEND_IMAGE=restaurant-pos-frontend:local`

The JWT secret must be generated as a strong production secret. Do not reuse any
development value.

## Server Preparation

Install:

- Docker Engine and Docker Compose plugin.
- PostgreSQL client tools for backup and restore scripts.
- Nginx and Certbot if terminating TLS on the host instead of in a container.
- Firewall rules allowing only the intended public HTTP/HTTPS ports.

## DNS and HTTPS

Point DNS to the cloud host before issuing certificates. The active Nginx
example is HTTP-only so it can be validated early. Enable the HTTPS sketch after
certificates are installed and paths are configured.

## Database Initialization

The current single-server compose deployment runs PostgreSQL as service `db`
and persists data under `deployment/cloud/data/postgres`.

Do not run:

```bash
docker compose down -v
rm -rf deployment/cloud/data/postgres
```

Those commands can remove production data.

Startup expectations:

- `spring.profiles.active=cloud`.
- Flyway is enabled.
- JPA schema generation is validation-only.
- RuntimeDataSeeder does not create default demo users or demo restaurant data.

Run migration validation as part of the backend startup or CI deployment check.

## Production Safety Guard

The backend cloud profile is protected by startup guards:

- JWT secret must be present and production-safe.
- X-User-Id fallback must be disabled.
- Dev role switcher must be disabled.
- Flyway must remain enabled.
- Unsafe JPA DDL modes must not be used.
- Demo/default seeding must remain disabled.

If any guard fails, fix configuration rather than bypassing the guard.

## First Owner Bootstrap

Cloud deployment must not rely on development default accounts. The production
owner/bootstrap runbook lives in `README_PRODUCTION_BOOTSTRAP.md`. It is still a
manual operations runbook, not a runtime CLI/API implementation.

Bootstrap rules:

- Generate owner credentials securely.
- Store only BCrypt hashes.
- Rehearse against staging or a temporary database.
- Do not re-enable demo seed flags.
- Do not commit plaintext passwords or filled bootstrap scripts.

## Printing Boundary

Cloud servers must not directly connect to private LAN receipt printers. In cloud
deployments, use one of:

- `MOCK` for dry-run validation.
- `DISABLED` when printing should be off.
- `PAD_DIRECT` or a local print bridge for real store printers.

The cloud printing guard blocks unsafe direct private-printer socket attempts.
`HOT_KITCHEN` remains a configurable printing module, but physical transport
must follow the same cloud boundary.

## Build and Start

The production compose file is designed to build the backend and frontend images
on the server from the checked-out repository. It does not rely on a host
`frontend/dist` bind mount.

Validate only:

```bash
cd deployment/cloud
./deploy.sh --validate
```

HTTP mode deployment:

```bash
cd deployment/cloud
cp .env.example .env
# Fill .env with real deployment values.
./deploy.sh --http
./health-check.sh
```

HTTPS mode deployment after certificates exist under `data/letsencrypt`:

```bash
cd deployment/cloud
./deploy.sh --https
./health-check.sh
```

`deploy.sh --help` prints help only. It does not pull, build, or start services.
By default, `deploy.sh` runs:

```bash
docker compose --env-file .env -f docker-compose.yml config
docker compose --env-file .env -f docker-compose.yml build backend nginx
docker compose --env-file .env -f docker-compose.yml up -d
```

Use `--pull-images` only for an explicit remote-image workflow. Do not use it
with the default `restaurant-pos-*:local` images.

Expected services:

```bash
docker compose --env-file .env -f docker-compose.yml config --services
# db
# backend
# nginx
```

## Smoke Test Checklist

Use `FINAL_SMOKE_TEST_CHECKLIST.md` as the copy-paste operational checklist.
At minimum, validate:

- Frontend loads over the configured domain.
- `/api/v1/auth/me` returns either authenticated user data or HTTP 401.
- Login works with the production bootstrap account.
- `/owner/dashboard` or the store workspace loads for the authenticated role.
- Frontdesk can create an order.
- Print Center mode is set intentionally.
- Print jobs are visible and failures are human-readable.
- Cloud private-printer guard is not bypassed.
- Backup script creates a timestamped dump.

## Windows Pilot Separation

The Windows pilot package remains separate under `deployment/windows-pilot`.
Do not mix Windows local-server scripts with this cloud deployment package.

## 943ed07 Deployment Regression Note

Commit `943ed07` promoted the foundation snapshot to `main` and accidentally
replaced the production cloud compose package with a template that used
`frontend` instead of `nginx`, removed build definitions, made Postgres profile
only, and bind-mounted `frontend/dist`. This broke the server's existing update
flow:

```bash
docker compose build backend nginx
docker compose up -d
```

The current package restores the production service names and local build flow:

- `db`: PostgreSQL 16 with healthcheck and persistent `./data/postgres`.
- `backend`: built from `../../backend/Dockerfile`.
- `nginx`: built from `../../frontend/Dockerfile` and configured through
  `./data/nginx/default.conf.template`.

The next `docker compose up -d --build` from project `cloud` will manage the
existing `cloud-db-1`, `cloud-backend-1`, and `cloud-nginx-1` containers instead
of creating a new `frontend` orphan.
