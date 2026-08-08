# AL-003S Guarded Staging Acceptance Commands

This runbook covers the repository-supplied launcher for STG-005A and STG-005B
one-shot commands. It does not authorize runtime execution.

The authoritative dependency order, commands, evidence fields, API follow-up,
rollback boundary, and unresolved gates are in:

- `docs/governance/agile/AL-003S_STAGING_ACCEPTANCE_PREPARATION.md`
- `docs/governance/runtime/AL-003S_STAGING_ACCEPTANCE_EVIDENCE_TEMPLATE.md`
- `docs/governance/agile/AL-003_STAGING_RELEASE_ACCEPTANCE_PLAN.md`

## Safe defaults

`staging-synthetic-acceptance.sh` defaults to validation and requires:

- exact clean release SHA;
- fixed `/srv/restaurant-pos/staging` root;
- fixed `restaurant-pos-staging` Compose project;
- mode-`0600` private environment file;
- formal preflight PASS evidence and matching SHA-256/env digest;
- fresh mode-`0600` readiness evidence (maximum age 15 minutes) that binds host
  memory, CPU count, free disk, normalized load, and exact Staging and
  Production container fingerprints;
- an action-specific mode-`0600` Owner approval artifact and matching SHA-256;
- a SHA-tagged backend image whose running image ID is pinned through a private
  immutable Compose override for the one-shot container;
- `Printing=DISABLED`;
- running exact-image backend, `127.0.0.1:18080`, and health HTTP 200.

Validation calls the existing official `staging-deploy.sh --validate` path. It
does not create a one-shot container.

## Runtime gates

Every plan or write command requires `--execute-runtime` because even a
read-only Java plan creates a temporary one-shot container. Writes additionally
require `bootstrap-execute` or `source-menu-execute`.

Before requesting action approval, run
`staging-acceptance-readiness.sh` with the reviewed exact SHA, env/preflight
bindings, Production project `cloud`, and approved minimum memory/CPU values.
Redirect its sanitized stdout to a new file under the fixed Staging evidence
directory, set mode `0600`, and retain its SHA-256. The collector performs only
filtered Docker metadata reads, host resource reads, and loopback health; it
does not create a container or change either Compose project.

Each one-shot action must then reference a separately reviewed approval file:

```text
APPROVAL|STATUS|OWNER_APPROVED
APPROVAL|EXPIRES_AT_EPOCH|<ten-digit-epoch-no-more-than-24-hours-ahead>
APPROVAL|APPROVED_SHA|<full-sha>
APPROVAL|ACTION|<exact-action>
APPROVAL|REQUEST_FINGERPRINT|<sha256>
APPROVAL|PREFLIGHT_SHA256|<sha256>
APPROVAL|READINESS_SHA256|<sha256>
APPROVAL|REFERENCE|<sanitized-owner-review-reference>
```

The request fingerprint is the SHA-256 of the launcher's documented canonical
newline-delimited fields: action, approved SHA, run ID, Organization name/code,
source Store name/code, Owner login/name, and source Store ID. Blank fields
remain present. Approval and readiness files must be regular, non-symlink,
invoking-user-owned mode-`0600` files under
`/srv/restaurant-pos/staging/evidence`. They are re-hashed immediately before
execution. Neither file may contain a password, token, raw request body, or
customer data.

This artifact is a procedural, digest-bound review record, not a cryptographic
signature or an independent authorization system. Repository tooling can prove
that the exact reviewed artifact was supplied and unchanged; it cannot prove
who authored the file. Runtime access remains restricted to the separately
approved operator/session, and the sanitized approval reference must point to
the Owner's external review record.

The launcher uses:

```text
docker --context default compose
--project-name restaurant-pos-staging
--rm --no-deps -T
--entrypoint java
```

It never starts dependencies, never targets another Compose project, and never
uses `down`, `down -v`, restore, Flyway clean/repair, or image pull/build.
`--pull never` is combined with a private Compose override that names the
already-running backend's immutable `sha256:` image ID. A moved SHA tag, changed
container fingerprint, stale readiness record, expired/mismatched approval,
non-loopback port, or non-200 health result fails closed before the command.
Actions are serialized by an owner-only lock under the fixed Staging state
directory and bounded to 600 seconds. Docker metadata, cleanup, and post-check
calls are independently bounded to 20 seconds. A deterministic action-fingerprint
container name lets failure/`INT`/`TERM` cleanup target only that one-shot
container. The same Staging/Production fingerprints, resource thresholds,
loopback health, release, and evidence bindings are checked again after the
action; a post-check failure is `NO_GO` and requires review of the already
committed transactional result rather than an automatic retry.

An action failure/timeout, failed post-check, unverified cleanup, `INT`, or
`TERM` attempts to record blocked state in both the held lock file and the
owner-only marker
`/srv/restaurant-pos/staging/state/al003s-acceptance.blocked`. At least one
record must succeed; either record independently blocks every later action after
it obtains the lock. Absence of the companion marker does not mean the action is
unblocked because the lock record remains authoritative. Blocked state may be
cleared only in a
separately approved Owner recovery after confirming the scoped one-shot
container is absent and repeating continuity checks; recovery must inspect and
clear every blocked record that actually exists. This launcher never clears a
record automatically.

`bootstrap-execute` accepts its password only from non-interactive stdin. The
launcher has no password/token argument and does not write an evidence file
containing command output. Retain only the sanitized `STG005_BOOTSTRAP|...` or
`STG005_SOURCE_MENU|...` result lines in the reviewed evidence template.

## Docker CLI isolation

Each launcher or readiness invocation creates a private mode-`0700` temporary
root containing HOME and DOCKER_CONFIG. The tools explicitly use Docker context
`default`, do not read the user's `~/.docker`, verify the private directories
before each Docker call, and remove the root and any immutable-image override
on success, failure, `INT`, or `TERM`.

## Stop conditions

Stop without retry or repair if any binding, evidence digest, runtime image,
port, health, source Store ID, bootstrap provenance, manifest fingerprint,
revision, or graph check differs. Do not weaken a guard, edit data manually, or
substitute Production credentials/data.

The API login/onboarding/clone sequence remains a separate Owner-approved
checkpoint. This launcher does not handle bearer tokens or idempotency keys.
