# Git Deploy Workflow

The current production deployment uses the repository checkout on the server and
Docker Compose under `deployment/cloud`.

## Normal Update

```bash
cd /path/to/Restaurant_System
git fetch origin --prune
git checkout main
git pull --ff-only origin main

cd deployment/cloud
./deploy.sh --validate
./deploy.sh --http
./health-check.sh
```

Use `./deploy.sh --https` instead of `--http` when the server has valid
certificates under `deployment/cloud/data/letsencrypt`.

## Why Not `docker compose pull`

The default production images are local build tags:

```text
restaurant-pos-backend:local
restaurant-pos-frontend:local
```

They are built from the checked-out repository. Do not run `docker compose pull`
for those local tags. If a future remote registry workflow is added, use an
explicit flag or documented profile rather than changing the default local build
path.

## Expected Compose Services

```bash
cd deployment/cloud
docker compose --env-file .env -f docker-compose.yml config --services
```

Expected output:

```text
db
backend
nginx
```

The service names intentionally match the existing production containers:

```text
cloud-db-1
cloud-backend-1
cloud-nginx-1
```
