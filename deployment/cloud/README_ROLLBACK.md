# Restaurant POS Cloud Rollback Runbook

This runbook is a template. Review it before live use and adapt it to the actual
hosting provider, image registry, and database process.

## Immediate Stabilization

If order taking is working but printing is failing:

1. Switch Print Center mode to `MOCK`, `DISABLED`, or `PAD_DIRECT` as appropriate.
2. Do not stop order taking solely because a printer transport is failing.
3. Use Print Center and Order Center to inspect failed jobs and reprint when safe.

## Application Rollback

Preferred rollback order:

1. Identify the last known-good backend and frontend image tags.
2. Update `.env` image values to those tags.
3. Run:

```bash
docker compose --env-file .env -f docker-compose.yml pull
docker compose --env-file .env -f docker-compose.yml up -d
./health-check.sh
```

4. Confirm login, order creation, and Print Center visibility.

## Frontend-Only Rollback

If only the web UI is broken:

1. Roll back the frontend image tag.
2. Keep backend unchanged.
3. Restart the frontend service.
4. Smoke test login, Frontdesk, Order Center, and Print Center.

## Backend-Only Rollback

If API behavior regressed:

1. Roll back the backend image tag.
2. Confirm database migrations are compatible before restarting.
3. Restart backend.
4. Run `health-check.sh`.
5. Smoke test login, order creation, and printing visibility.

## Database Restore

Database restore is a high-risk operation. Prefer application rollback first.

Before restoring:

- Rehearse the restore against staging or a temporary database.
- Confirm the backup timestamp and target database.
- Confirm that Flyway migration state is compatible.
- Pause write traffic if a real restore is required.

Restore command:

```bash
./restore-db.sh backups/example.dump
```

The script requires typing `RESTORE` before it proceeds.

## Flyway Rollback Notes

Flyway migrations are forward-only by default. Do not manually edit migration
history in production. If a migration caused damage, use a tested repair plan:

- Roll forward with a corrective migration when possible.
- Restore from backup only after approval and rehearsal.
- Keep application image and database schema compatibility aligned.

## After Rollback

- Run `./health-check.sh`.
- Verify login.
- Verify owner/store workspace routing.
- Submit a small test order if operationally safe.
- Verify Print Center shows current jobs and failures clearly.
- Record the incident, image tags, backup file, and follow-up action.
