# Same-Host Staging Preflight (STG-004)

Status: `STG-004_PREFLIGHT_READY_WAITING_FOR_OWNER_REVIEW`. This document
defines a future Owner-approved same-host check. It does not authorize a server
connection, deployment, or container action.

## Completed prerequisite

STG-003 completed its real local Docker rehearsal and was merged to `main`
through [PR #35](https://github.com/Z1linXu/Restaurant_System/pull/35).
The final runtime Head
`74dd6a628002f96e4f2b4fbe3cf479fb23ed8e01` is recorded as
`FINAL_HEAD_REHEARSAL_PASS` in
[`STG-003_LOCAL_REHEARSAL_EVIDENCE.md`](../../docs/governance/runtime/STG-003_LOCAL_REHEARSAL_EVIDENCE.md).
That evidence satisfies the local rehearsal prerequisite only. It does not
prove server isolation, authorize SSH, or approve a Staging deployment.

## Boundary

The only permitted Staging identity is:

- root: `/srv/restaurant-pos/staging`;
- Compose project: `restaurant-pos-staging`;
- HTTP bind: `127.0.0.1:18080`;
- release: `releases/<full-40-character-approved-sha>`;
- printing: application-level `DISABLED` with no printer endpoint.

The preflight rejects a root that overlaps the explicitly supplied production
root, a public or unowned bind, a non-clean release, unsafe config metadata,
unsafe printing input, or missing resource headroom. A free port passes for a
first deployment. For an upgrade, an occupied port passes only when `ss` and
Docker metadata agree that exactly one running
`restaurant-pos-staging/nginx` container owns the sole
`80/tcp -> 127.0.0.1:18080` mapping. A missing, public, multiple, stopped,
wrong-project, wrong-service, or differently mapped owner remains `NO_GO`.
The retained container is not stopped or changed by this check. It reads only
the Staging-scoped Compose service list, formatted image/container metadata, host
resource metadata, and the external environment file through the existing
secret-safe STG-002 validation wrapper. It never prints environment values or
resolved Compose content.

An initialized PostgreSQL bind source is a protected leaf. With
`postgres:16-alpine`, it may be owned by UID `70` with mode `0700`, so the
deployment user is not expected to enter it. The preflight canonicalizes the
traversable Staging root and `state` parent, then validates the exact
`state/postgres` directory entry, non-symlink topology, owner, and mode using
metadata from the parent. It never weakens permissions or treats the inability
to `cd` into that private leaf as a database failure. A missing leaf, symlinked
leaf or parent, unexpected owner, or mode other than `0700` remains `NO_GO`.

## Owner-approved future preflight

The following is a future, state-preserving command. It is not run by this
repository script automatically:

```bash
/srv/restaurant-pos/staging/releases/<approved-full-sha>/deployment/cloud/staging-server-preflight.sh \
  --validate \
  --env-file /srv/restaurant-pos/staging/config/.env.staging \
  --approved-sha <approved-full-sha> \
  --production-project cloud \
  --production-root /home/ubuntu/Restaurant_System \
  --min-free-bytes <owner-approved-minimum> \
  --max-used-percent <owner-approved-maximum> \
  --min-available-memory-kb <owner-approved-minimum> \
  --min-cpu-count <owner-approved-minimum>
```

Output is stable and sanitized:

```text
CHECK|RELEASE_SHA|PASS|approved SHA matches release HEAD
CHECK|PORT_18080|PASS|retained expected Staging nginx owns the exact loopback port binding
CHECK|IMAGE_restaurant-pos-backend|PENDING_PREBUILD|SHA-specific image is not built yet
SUMMARY|PASS|same-host Staging preflight passed without state changes
```

Exit status `0` means all blocking checks passed. Exit `2` is `NO_GO`; exit
`3` means runtime evidence is still required for a required check. Unexpected
tooling failures preserve their non-zero shell status and must not be treated
as PASS. `PENDING_PREBUILD` and no existing Staging containers are expected
before a first approved build and are reported without claiming that images or
runtime health have been verified.

## Start gate

`staging-deploy.sh` validates by default. It cannot build or start without all
of the following:

1. `--execute-start`;
2. `--approved-sha` that exactly matches the release and `.env.staging` SHA;
3. an existing non-symlink preflight evidence file under the Staging evidence
   directory, owned by the invoking user with mode `0600`;
4. `--preflight-evidence-sha256` matching the exact Owner-reviewed evidence
   file; and
5. evidence lines binding the result to the approved SHA, Staging root,
   Compose project, and current `.env.staging` SHA-256.

After those gates pass, the wrapper builds the backend image first and starts
the nginx/frontend image build only after the backend build succeeds. A failed
backend build stops the sequence before nginx or `up -d`; the wrapper never
uses a combined `docker compose build backend nginx` command.

Docker build execution remains isolated from the invoking user's Docker
credentials. The wrapper creates an owner-only temporary `HOME` and
`DOCKER_CONFIG`, verifies the system Compose plugin under context `default`,
and cleans that state on every exit path.

Create the private evidence directory with mode `0700`, capture the preflight
with a restrictive umask, and review its SHA-256 before approving a start. The
evidence capture itself is an Owner action. Do not put secrets, resolved Compose
output, full image IDs, or production paths in a shared report.

## Owner checkpoints

1. Approve the exact SHA, same-host design, production project/root, resource
   thresholds, and a non-peak build window.
2. Run and review the preflight evidence.
3. Approve `--execute-start` as a separate action.
4. Review loopback health, project-scoped container status, image IDs, and
   Flyway state after start.
5. Approve any stop or application rollback separately.

Static image/migration comparison cannot prove that an older application image
is compatible with the current schema. Without reviewed runtime compatibility
evidence, an application rollback is `NO_GO`.

## Plan-only controls

`staging-server-control.sh` is intentionally not an execution tool. It can
only validate arguments or print an `OWNER_ACTION_REQUIRED` plan:

```bash
./staging-server-control.sh --plan-stop \
  --env-file /srv/restaurant-pos/staging/config/.env.staging \
  --approved-sha <approved-full-sha>
```

The plan names only `restaurant-pos-staging` and sequences a future stop as
`nginx`, `backend`, then `db`. It never runs Docker and never proposes `down`,
`down -v`, `rm`, `prune`, image pull, Flyway clean, restore, or volume deletion.

## Private access

Do not publicly expose port `18080`. If an Owner separately approves private
access, the operator may run this tunnel manually from their workstation:

```bash
ssh -o ExitOnForwardFailure=yes -N \
  -L 127.0.0.1:28080:127.0.0.1:18080 \
  <OWNER_APPROVED_HOST_ALIAS>
```

Then access `http://127.0.0.1:28080`. The scripts never initiate SSH tunnels,
open firewall ports, or change Nginx.
