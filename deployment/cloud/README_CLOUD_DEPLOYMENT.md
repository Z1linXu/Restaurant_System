# Restaurant POS Cloud Deployment Template

This folder is a cloud deployment architecture package. It is a template only:
it does not deploy to any server, does not connect to infrastructure, and does
not contain real secrets.

## Architecture

Recommended cloud shape:

- Backend: Spring Boot jar packaged into a backend container image.
- Frontend: React static build served by Nginx.
- Reverse proxy: Nginx proxies `/api` and `/ws` to the backend service.
- Database: managed PostgreSQL preferred. The compose file includes an optional
  local PostgreSQL profile for staging or pilot rehearsals only.
- TLS: terminate HTTPS with Nginx and certificates from a real certificate
  provider.
- Backups: use `backup-db.sh` with `pg_dump -Fc`.
- Restores: use `restore-db.sh` only after rehearsal against staging.

## Files

- `.env.example`: blank placeholders for deployment configuration.
- `docker-compose.yml`: backend, frontend/Nginx, and optional local Postgres.
- `nginx.conf.example`: static frontend, REST proxy, and WebSocket proxy.
- `application-cloud.yml.example`: environment mapping reference for cloud.
- `deploy.sh`: compose validation and start template.
- `backup-db.sh`: custom-format PostgreSQL backup.
- `restore-db.sh`: explicit-confirm restore helper.
- `health-check.sh`: frontend and backend reachability checks.
- `README_ROLLBACK.md`: rollback checklist.

## Environment Setup

Copy `.env.example` to `.env` on the server and fill values there. Do not commit
the filled `.env` file.

Required values:

- `DOMAIN`
- `BACKEND_IMAGE`
- `FRONTEND_IMAGE`
- `DB_HOST`
- `DB_PORT`
- `DB_NAME`
- `DB_USER`
- `DB_PASSWORD`
- `JWT_SECRET`

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

Use managed PostgreSQL when possible. Create an empty database and user before
starting the backend.

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
owner/bootstrap runbook is intentionally left for a later PR. Until that exists,
prepare a controlled one-time bootstrap procedure before live use.

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

Typical local packaging before copying artifacts:

```bash
cd backend && mvn -q -DskipTests package
cd ../frontend && npm run build
```

On the cloud host:

```bash
cd deployment/cloud
cp .env.example .env
# Fill .env with real deployment values.
./deploy.sh
./health-check.sh
```

## Smoke Test Checklist

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
