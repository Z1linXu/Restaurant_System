# Restaurant POS Cloud Deployment

This folder contains the Docker deployment package for a single Ubuntu 22.04
cloud server:

- PostgreSQL runs as the `db` Docker Compose service on the same server.
- Backend is built from `backend/Dockerfile`.
- Frontend is built from `frontend/Dockerfile`.
- Nginx serves the React build and reverse-proxies `/api` and `/ws`.
- HTTP-only mode uses `nginx.http.conf`, `server_name _`, and does not require a
  domain.
- HTTPS mode uses `nginx.conf`; Certbot issues and renews Let's Encrypt
  certificates through the webroot challenge path.

For the complete fresh-server runbook, use:

```text
../../README_SERVER_DEPLOY.md
```

## Files

- `.env.example`: production environment template.
- `docker-compose.yml`: `db`, `backend`, `nginx`, and `certbot` services.
- `nginx.http.conf`: HTTP-only config for public-IP deploy and temporary HTTPS
  certificate issuance.
- `nginx.conf`: HTTPS production config.
- `deploy.sh`: first-server deploy script. Supports `--http-only` and
  `--https`.
- `bootstrap-admin.sh`: one-time interactive first owner/admin bootstrap command.
- `bootstrap-admin.env`: optional server-local first owner/admin input file.
  Keep it chmod `600`; it is ignored by Git and must not contain a password.
- `export-store-config.sh`: Mac-side helper for exporting only old single-store
  configuration table data.
- `import-store-config.sh`: cloud-side guarded importer for whitelisted store
  configuration dumps.
- `update.sh`: future one-command update script.
- `backup-db.sh`: PostgreSQL custom-format backup through the `db` container.
- `restore-db.sh`: explicit-confirm restore through the `db` container.
- `health-check.sh`: frontend and backend reverse-proxy checks.
- `README_PRODUCTION_BOOTSTRAP.md`: first real owner/store data runbook.
- `FINAL_SMOKE_TEST_CHECKLIST.md`: manual smoke test checklist.
- `README_ROLLBACK.md`: rollback checklist.

## Quick Start

```bash
cd deployment/cloud
cp .env.example .env
nano .env
./deploy.sh --http-only
./health-check.sh
nano bootstrap-admin.env
chmod 600 bootstrap-admin.env
./bootstrap-admin.sh --dry-run --env-file bootstrap-admin.env
./bootstrap-admin.sh --env-file bootstrap-admin.env
```

For HTTPS, set `ENABLE_HTTPS=true`, fill `DOMAIN` and `LETSENCRYPT_EMAIL`, then
run:

```bash
./deploy.sh --https
```

In HTTP-only mode, `DOMAIN` and `LETSENCRYPT_EMAIL` may stay blank. `DB_PASSWORD`
and `JWT_SECRET` are always required.

Do not commit `.env` or anything under `deployment/cloud/data`.
