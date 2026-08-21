# AL-003 Staging Preflight Private-Leaf Repair Evidence

> Status: `AL-003_STAGING_PREFLIGHT_REPAIR_WAITING_FOR_OWNER_REVIEW`
>
> Verification date: 2026-08-07, America/Toronto
>
> Base: `origin/main` at `1482cddf4f10478ed571e4d7422100dc40006f6b`
>
> Branch: `codex/al-003-staging-preflight-private-leaf-repair`
>
> Review gate: Draft PR #59, base `main`

## Scope and retained runtime boundary

PR #58 merged the immutable failed-attempt evidence before this branch was
created. That evidence remains the authority for the point-in-time runtime
state: Staging recovered to `4397f995...` / Flyway V8, Production remained
`4667f3c...`, and V9/V10 were not applied. This repair did not reconnect to or
mutate either runtime.

The package changes only Staging path validation, its fake-filesystem
regression coverage, Staging runbooks, and governance. It changes no business
code, migration, Compose topology, image identity, printing behavior, API,
authorization, Store Profile, or Production deployment path.

## Root cause

The formal preflight used `cd -P` to canonicalize every directory, including
`/srv/restaurant-pos/staging/state/postgres`. After initialization,
`postgres:16-alpine` correctly owns that leaf as UID 70 with mode `0700`.
The `ubuntu` deployment user can inspect its directory entry from the
traversable `state` parent but cannot enter the leaf. The old guard converted
that expected `Permission denied` into a false path/symlink `NO_GO`.

The deployment wrapper had the same canonicalization pattern in its input
gate. Leaving that second call site unchanged would allow formal preflight to
pass and then reproduce the false rejection before the serial build.

## Corrected behavior

Both Staging gates now:

1. canonicalize the traversable Staging root and `state` parent;
2. require the exact child name and topology `state/postgres`;
3. require a real directory entry and reject a symlink in the leaf or any
   parent component;
4. require owner to be the deployment user before initialization or fixed
   `postgres:16-alpine` UID 70 after initialization;
5. require mode `0700`;
6. never `cd` into the protected leaf.

The change preserves the original fail-closed checks. Missing paths, parent or
leaf symlink replacement, unexpected owner/mode, Production path overlap,
unsafe printing, wrong exact SHA, and invalid evidence remain rejected.

## Regression evidence

The regression fixture simulates a directory that the invoking user cannot
enter while its sanitized metadata reports UID 70 and mode `0700`. It proves
that both formal preflight and the deployment wrapper's validation pass that
legitimate state. Separate cases prove `NO_GO` for:

- PostgreSQL leaf replaced by a symlink;
- Staging `state` parent replaced by a symlink;
- missing PostgreSQL leaf;
- owner outside deployment user / UID 70;
- mode other than `0700`.

Local checks completed:

| Check | Result |
|---|---|
| `bash -n` for every `deployment/cloud/**/*.sh` | `PASS` |
| `deployment/cloud/tests/test_staging_guard.sh` | `PASS` |
| `deployment/cloud/tests/test_staging_server_preflight.sh` | `PASS` |
| `deployment/cloud/tests/test_staging_server_control.sh` | `PASS` |
| `deployment/cloud/tests/test_staging_deploy_cli_state.sh` | `PASS` |
| `deployment/cloud/tests/test_staging_local_rehearsal.sh` | `PASS` on clean candidate commit `2909c053cf710f7279e8671c1ce16b7684fb6222` |
| Java/backend/frontend suites | `NOT_RUN_BY_SCOPE`; no application source or contract changed |
| Runtime command | `NOT_RUN_BY_POLICY`; no SSH, Docker lifecycle, Flyway, bootstrap, or clone |

The local-rehearsal harness intentionally rejects a dirty repository. Its first
pre-commit invocation therefore stopped before fixture execution. The required
rerun against the clean candidate commit completed successfully.

## Independent review

Agent 6 first requested explicit deploy-wrapper server-mode coverage and two
governance state corrections. The candidate added the opaque positive case and
all five direct wrapper rejection cases, changed pre-PR status to
`IMPLEMENTED_IN_WORKTREE`, and made the Runtime Mutation Gate explicitly cover
Staging as well as Production. The final re-review reported no findings and
returned `APPROVE`.

## Staging Owner login prerequisite

This repair does not execute STG-005A or create an account. Retained evidence
proves that the current Staging runtime remains Flyway V8 and the reviewed
bootstrap has never run there. It does not prove the absence of every unrelated
synthetic row because no database query was authorized or executed.

Repository inspection confirms that STG-005A creates only the Organization,
source Store, Owner credential, active Organization membership, and active
source-Store membership. It does not create a target Store or Owner
target-Store membership. No retained runtime evidence proves a safe credential,
successful Owner login, target access, or authenticated AL-003 API call.

The acceptance prerequisite therefore remains
`AL-003_STAGING_OWNER_LOGIN_PREREQUISITE_PENDING`. It may be closed only by a
later Owner-approved, synthetic-only runtime preparation using reviewed
bootstrap/onboarding/authentication paths. Production credentials, manual SQL,
authorization bypasses, developer login switching, and copied business data
are forbidden.

## Non-modification statement and next gate

```text
Staging NOT REDEPLOYED
V9/V10 NOT EXECUTED
Bootstrap NOT EXECUTED
Owner login NOT EXECUTED
Validate NOT EXECUTED
Clone NOT EXECUTED
Production NOT MODIFIED
```

After Owner review and merge, obtain the new full `origin/main` SHA and return
to the Staging Release Gate with fresh release, environment digest, preflight
evidence, and exact-SHA approval. The historical
`8f909525781804f61d1da388882f530da358c3c4` approval and failed evidence cannot
authorize a changed release.
