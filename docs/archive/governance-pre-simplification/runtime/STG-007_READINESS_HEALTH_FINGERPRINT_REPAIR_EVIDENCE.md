# STG-007 Readiness Health Fingerprint Repair Evidence

> Evidence classification: `STAGING_RUNTIME_OBSERVATION_PLUS_REPOSITORY_REPAIR`
>
> Base: `origin/main@868e229f1b5afd28163e5031ad8fabffaad651f6`
>
> Runtime boundary: exact Staging deploy and Flyway V8 to V10 completed under
> the Owner's conditional Batch B authorization; no readiness PASS, runtime
> evidence collection, same-image restart, bootstrap, credential, login, clone,
> business-data or Production mutation followed

## Exact execution ground truth

After PR #79 entered main, STG-007 restarted from exact candidate
`868e229f1b5afd28163e5031ad8fabffaad651f6`. Fresh Batch A verified retained
Staging `4397f995...` / V8, disabled printing, loopback exposure, state/network/
mount isolation, host resources and unchanged Production continuity. Exact
candidate import, detached release creation and four-field private-env rotation
passed. The rotated env digest is
`8d304153bd13d18f625a04c5450a3b5977fdbf9bf9025dc488a42e492c7cbe35`.

Formal preflight passed at private evidence digest
`bc2bf98e0dc451b76b184a7f54a81f443491a4eb416ccca931c45665c2a7fa08`.
The candidate migration set was exactly V1-V10 and the runtime was exactly V8,
so conditional Batch B became eligible. The reviewed deploy helper built
backend then frontend serially and started only project
`restaurant-pos-staging`. Flyway applied V9 and V10 successfully.

Post-deploy machine evidence established:

- db container `b64d3c676dbb...`, image `57c72fd2a128...`, restart `0`;
- backend container `2c09a020bb18...`, image `6a344d61a188...`, restart `0`;
- nginx container `dcbeed4da285...`, image `88f0a193bc0c...`, restart `0`;
- exact backend/frontend image tags bind `868e229f...` to those image IDs;
- Flyway count/max/success is `10 / 10 / true`, ordered versions V1-V10;
- frontend, backend health and `/ws/info` returned `200 / 200 / 200`;
- runtime printing flag is `false`, bind is `127.0.0.1:18080`, and Staging has
  zero network or mount crossover with Production;
- Production fingerprint remained
  `1bf8909b76ece11333c16df9dd9530103d224484b0a13da829f231ab43914d20`
  and Production health remained 200.

An ad-hoc post-deploy assertion initially returned nonzero because it compared
Docker's short `ps -q` ID with a full ID. A read-only field-by-field diagnosis
proved every runtime gate above; this was an operator evidence-script width
error, not repository or runtime drift.

## Readiness stop

The reviewed passive readiness collector then returned `NO_GO` before emitting
PASS evidence:

```text
template parsing error: executing at <.State.Health>: map has no entry for key "Health"
AL003S_ACCEPTANCE|NO_GO|cannot inspect restaurant-pos-staging container metadata
```

Docker omits the optional `State.Health` map entry for a service without a
healthcheck. Shared `project_fingerprint` accessed that key directly even
though its semantic contract already accepts `NO_HEALTHCHECK`. No action lock
blocked record was created, no runtime approval was created or consumed, and no
collect-evidence or same-image restart action started.

## Bounded repair

The project fingerprint now enumerates Docker state-map keys first. Only a
genuinely absent optional `Health` key becomes `NO_HEALTHCHECK`; when the key
exists, `Status` must be present, syntactically valid and exactly `healthy`.
The exact three-service set and running-state checks are unchanged, so
present-empty/invalid health metadata, missing, extra, stopped or unhealthy
services remain fail-closed.

The focused readiness test exercises absent, healthy, unhealthy and
present-invalid health branches, verifies a stable SHA-256 fingerprint for the
valid mixed project and rejects regression to direct `.State.Health` access.
No application, migration, deployment configuration, runtime action, database
or Production behavior is changed by this repair.

## Independent review

Agent 6 returned `ACCEPT` with no blocking or non-blocking finding after the
initial overly broad `with index` approach was rejected and replaced. The final
review confirmed exact missing-key classification, fail-closed present-empty,
invalid and unhealthy handling, unchanged service/running/fingerprint identity,
four-branch focused coverage, `868e229f...` / Flyway V10 Ground Truth, and the
explicit prohibition on generalizing the original V8-only Batch B authority.
Agent 6 reran readiness, runtime-guard and runtime-evidence tests plus staged
diff check; all passed.

## Next gate

Publication follows the Dependency Repair Auto-Loop. After merge,
`868e229f...` remains the known deployed Staging runtime at Flyway V10 but is no
longer the current-main candidate. Ground Truth evaluation must restart from the
new exact main. The original authorization required a V8 Batch B entry state;
it must not be silently generalized to redeploy a new candidate over V10.
