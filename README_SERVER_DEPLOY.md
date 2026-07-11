# Restaurant_System Ubuntu 22.04 Server Deploy

This guide deploys Restaurant_System on one fresh Ubuntu 22.04 Tencent Cloud
Lightweight server with Docker, PostgreSQL on the same server, Nginx reverse
proxy, and Let's Encrypt HTTPS.

The deployment files live in `deployment/cloud`.

## 0. Before You Start

Prepare:

- A domain name, for example `pos.example.com`.
- DNS `A` record pointing that domain to the Tencent Cloud server public IP.
- Tencent Cloud firewall/security group allowing inbound `22`, `80`, and `443`.
- SSH access to the server.

The first deploy needs ports `80` and `443` free on the server. Do not run a
host-level Nginx on the same ports.

## 1. SSH Into The Server

```bash
ssh ubuntu@YOUR_SERVER_PUBLIC_IP
```

Update base packages:

```bash
sudo apt-get update
sudo apt-get install -y git curl ca-certificates openssl
```

## 2. Clone The Project

Choose one directory and keep future deploys there:

```bash
sudo mkdir -p /opt/restaurant-system
sudo chown "$USER":"$USER" /opt/restaurant-system
git clone YOUR_GIT_REPO_URL /opt/restaurant-system
cd /opt/restaurant-system/deployment/cloud
```

If you copy the source by `scp` instead of Git, make sure the final server path
still contains the full project root with `backend`, `frontend`, and
`deployment/cloud`.

## 3. Create Production Environment File

```bash
cp .env.example .env
nano .env
```

Required values:

```bash
DOMAIN=pos.example.com
LETSENCRYPT_EMAIL=owner@example.com
ENABLE_HTTPS=true
DB_NAME=restaurant_pos
DB_USER=restaurant_pos
DB_PASSWORD=replace-with-a-strong-database-password
JWT_SECRET=replace-with-a-strong-jwt-secret
```

Generate secrets on the server:

```bash
openssl rand -base64 32
openssl rand -base64 48
```

Use one generated value for `DB_PASSWORD` and another for `JWT_SECRET`.

## 4. First Deploy

Run:

```bash
cd /opt/restaurant-system/deployment/cloud
./deploy.sh
```

`deploy.sh` performs:

- Docker Engine and Docker Compose plugin installation.
- Runtime directory creation under `deployment/cloud/data`.
- PostgreSQL startup.
- Database readiness check.
- Backend image build.
- Frontend image build.
- Backend startup, which runs Flyway schema initialization.
- Temporary HTTP Nginx startup for Let's Encrypt HTTP-01 validation.
- Let's Encrypt certificate issuance.
- Final HTTPS Nginx startup.
- `docker compose up -d` for the application stack.

## 5. Verify

```bash
./health-check.sh
docker compose --env-file .env -f docker-compose.yml ps
```

Open:

```text
https://YOUR_DOMAIN
```

Backend health through Nginx:

```bash
curl -k https://YOUR_DOMAIN/api/v1/menu/health
```

Expected result is HTTP `200` with a JSON success response.

## 6. First Production Data Bootstrap

The cloud profile does not create demo users or demo restaurant data.

After schema initialization, follow:

```bash
deployment/cloud/README_PRODUCTION_BOOTSTRAP.md
```

Do not enable demo seed flags in production.

## 7. Future Code Update

After new code is available on the server:

```bash
cd /opt/restaurant-system
git pull --ff-only
cd deployment/cloud
./update.sh
./health-check.sh
```

Alternative: set this in `.env`:

```bash
UPDATE_GIT_PULL=true
```

Then future updates can be:

```bash
cd /opt/restaurant-system/deployment/cloud
./update.sh
./health-check.sh
```

## 8. Backup Database

```bash
cd /opt/restaurant-system/deployment/cloud
./backup-db.sh
```

Backups are written to:

```text
deployment/cloud/data/backups/
```

Copy backup files off the server regularly.

## 9. Restore Database

Stop live traffic if this is production. Then run:

```bash
cd /opt/restaurant-system/deployment/cloud
./restore-db.sh ./data/backups/restaurant_pos-YYYYMMDD-HHMMSS.dump
```

The script asks you to type `RESTORE`, stops the backend during restore, restores
the dump into PostgreSQL, and restarts the backend.

## 10. Useful Operations

View logs:

```bash
docker compose --env-file .env -f docker-compose.yml logs -f backend
docker compose --env-file .env -f docker-compose.yml logs -f nginx
docker compose --env-file .env -f docker-compose.yml logs -f db
```

Restart services:

```bash
docker compose --env-file .env -f docker-compose.yml restart
```

Stop services:

```bash
docker compose --env-file .env -f docker-compose.yml down
```

Do not remove `deployment/cloud/data/postgres` unless you intentionally want to
delete the database.

## 11. HTTPS Renewal

`update.sh` runs `certbot renew`. You can also renew manually:

```bash
cd /opt/restaurant-system/deployment/cloud
docker compose --env-file .env -f docker-compose.yml --profile tools run --rm certbot renew --webroot -w /var/www/certbot
docker compose --env-file .env -f docker-compose.yml up -d --force-recreate nginx
```

For production, add a root cron job or Tencent Cloud scheduled command to run
the renewal command at least weekly.

## 12. Rollback

If a new code deploy fails:

```bash
cd /opt/restaurant-system
git log --oneline -5
git checkout PREVIOUS_GOOD_COMMIT
cd deployment/cloud
./update.sh
./health-check.sh
```

If data must be rolled back, restore a known-good database backup with
`restore-db.sh`.
