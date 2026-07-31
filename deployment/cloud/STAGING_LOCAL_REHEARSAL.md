# STG-003 Local Isolated Rehearsal

## Purpose

This procedure is a local-only rehearsal for the standalone STG-002 staging
Compose package. It is not a server deployment path and must not be used on a
production host.

It pins one clean local Git SHA, uses either the default temporary root or a
single `restaurant-pos-stg003-*` namespace directly under the canonical local
temporary directory, binds only `127.0.0.1:18080`, generates synthetic
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

If a root contains more than one rehearsal release, cleanup requires the exact
release SHA: append `--commit <full-sha>`. This prevents an ambiguous cleanup
from targeting the wrong local worktree.

## Verified Local Execution

An Owner-authorized local Docker Desktop rehearsal completed on 2026-07-30 for
exact commit `b17ffa9a397bef62d474a58b649f1e55467a974f`.

- Docker Engine 29.6.2 and Compose v5.3.1 built and started `db`, `backend`,
  and `nginx` under project `restaurant-pos-staging`.
- Only `127.0.0.1:18080` was published.
- PostgreSQL 16.14 applied Flyway V1-V8 on the first startup and retained the
  same eight successful history rows on the second startup.
- Backend health, frontend root, `/api`, and SockJS `/ws/info` checks passed.
- Printing stayed `DISABLED`, and no business records were created.
- Cleanup removed only the local containers and network; the isolated
  PostgreSQL state directory was preserved.

Docker Compose v5 normalizes CPU and memory values in resolved configuration.
The guards validate normalized values per service. The local runner also uses a
generated credential-free Docker CLI configuration that exposes only the
verified Compose plugin directory; it does not depend on or copy a developer's
registry credentials.

The complete machine evidence, including bounded failed attempts and test
results, is maintained in
`docs/governance/runtime/STG-003_LOCAL_REHEARSAL_EVIDENCE.md`. This local result
does not authorize or prove a server Staging deployment.

PR #35 subsequently bound the same runtime trees to final tested runtime Head
`74dd6a628002f96e4f2b4fbe3cf479fb23ed8e01` with status
`FINAL_HEAD_REHEARSAL_PASS`, then merged the completed STG-003 work into
`main`. The final binding does not change the server-deployment boundary.
