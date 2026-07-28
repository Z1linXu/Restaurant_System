# STG-003 Local Isolated Rehearsal

## Purpose

This procedure is a local-only rehearsal for the standalone STG-002 staging
Compose package. It is not a server deployment path and must not be used on a
production host.

It pins one clean local Git SHA, uses a separate temporary root ending in
`/restaurant-pos/staging`, binds only `127.0.0.1:18080`, generates synthetic
credentials locally, and keeps printing disabled. The runner rejects `/srv`,
production-like paths, repository paths, remote Docker configuration, real
printer configuration, and dirty checkouts.

## Safe Planning

This command creates no worktree, configuration, container, image, database,
or data directory:

```bash
deployment/cloud/staging-local-rehearsal.sh --plan \
  --root "${TMPDIR:-/tmp}/restaurant-pos/staging"
```

## Owner-approved Local Run

Only run this after Docker Desktop/local Docker is available and the owner
approves local container startup. Do not substitute `/srv`, a network path, a
production checkout, or a real endpoint.

```bash
deployment/cloud/staging-local-rehearsal.sh --run \
  --confirm-local-container-start \
  --root "${TMPDIR:-/tmp}/restaurant-pos/staging"
```

The runner creates a detached worktree at `releases/<full-sha>`, generates
`.env.staging` with random synthetic values at `config/.env.staging` (mode
`0600`), verifies the `db`, `backend`, and `nginx` Compose services, performs a
first and a second startup, and records non-secret evidence under the local
root. The second startup uses `stop` then `up -d`; it must preserve the local
PostgreSQL data directory. It does not submit an order, create customer data,
or exercise printing.

Expected runtime evidence when Docker is available:

- PostgreSQL 16 container starts from an empty isolated data directory.
- Flyway history is present after first startup and unchanged after the second.
- The backend health endpoint and Nginx root return HTTP 200 on loopback.
- `/ws` is recorded only as an HTTP entry response; this is not a STOMP test.
- Compose status and resource limits are collected without credentials.

## Cleanup

Cleanup is opt-in and only removes containers/network for the exact local
project. It deliberately does not remove volumes or `state/postgres`.

```bash
deployment/cloud/staging-local-rehearsal.sh --cleanup \
  --confirm-local-container-start \
  --root "${TMPDIR:-/tmp}/restaurant-pos/staging"
```

The runner never uses `docker compose down -v`, `Flyway clean`, database
restore, or a remote Docker context. Delete local rehearsal data only through a
separate owner-approved local maintenance action after preserving needed test
evidence.

## Current Machine Limitation

At the time of this STG-003 implementation, the development machine has no
Docker CLI/runtime. `--plan` and the fake-Docker safety test are available, but
`--run` intentionally fails before creating local state. Runtime claims remain
evidence pending until an owner-approved local Docker execution is captured.
