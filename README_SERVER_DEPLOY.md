# Server Deployment Quick Reference

Use this when the production server has already pulled the latest Git code.

## Safe Update

```bash
cd /path/to/Restaurant_System
git status
git log -1 --oneline

cd deployment/cloud
./deploy.sh --validate
./deploy.sh --http
./health-check.sh
```

If the server is already using HTTPS certificates mounted under
`deployment/cloud/data/letsencrypt`, use:

```bash
cd deployment/cloud
./deploy.sh --https
./health-check.sh
```

## What The Deploy Script Does

Default deployment validates compose, builds local images, and starts services:

```bash
docker compose --env-file .env -f docker-compose.yml config
docker compose --env-file .env -f docker-compose.yml build backend nginx
docker compose --env-file .env -f docker-compose.yml up -d
```

The expected services are:

```text
db
backend
nginx
```

## Data Safety

Do not use these commands during normal application updates:

```bash
docker compose down -v
rm -rf deployment/cloud/data/postgres
```

They can remove production database data.

The PostgreSQL data directory is:

```text
deployment/cloud/data/postgres
```

## Rollback

For application rollback, prefer reverting Git to a known-good commit and then
rerunning:

```bash
cd deployment/cloud
./deploy.sh --http
```

or, for HTTPS:

```bash
cd deployment/cloud
./deploy.sh --https
```

Database restore is a separate high-risk operation. Use
`deployment/cloud/README_ROLLBACK.md` before restoring any backup.
