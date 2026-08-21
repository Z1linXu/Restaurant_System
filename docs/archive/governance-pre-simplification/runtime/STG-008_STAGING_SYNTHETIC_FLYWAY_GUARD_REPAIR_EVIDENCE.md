# STG-008 Guarded One-Shot Flyway Safety Dependency Repair Evidence

> Evidence classification: `STAGING_PLAN_NO_GO_DEPENDENCY_REPAIR`
>
> Repository base: `origin/main@828af4e84581dcb051248beee694c307a65210c5`
>
> Deployed Staging runtime: `2837ae88e55142c99c6975f8b6575febffc913a1`
>
> Runtime result: `STG-005A PLAN = NO_GO`; no application data write
>
> Repair state: `IN_MAIN` through PR #85 at
> `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6`; runtime use remains separately gated

## Scope and Ground Truth

PR #84 entered `main` at
`828af4e84581dcb051248beee694c307a65210c5` with the earlier sanitized
STG-008 entry evidence/governance only. Its diff from deployed Staging
`2837ae88...`, together with PR #83, contains no backend, frontend, Android,
migration, Compose, or runtime-script change. The resumed Owner-authorized
batch therefore correctly retained the clean exact `2837ae88...` release and
its private environment rather than misreporting documentation `main` as the
deployed SHA.

Fresh read-only observation before the action reconfirmed:

- Staging exact release and environment SHA `2837ae88...` / private env digest
  `124eb472bf95bc7311b4977beed9f1700a99ad6e371d6a7d390386c9bdd7e1cc`;
- Flyway 10 successful rows through exact V10 with no failed row;
- backend, frontend, and `/ws/info` HTTP `200/200/200`;
- printing `DISABLED/false`, loopback-only `127.0.0.1:18080`, and the retained
  isolated project/network/state/mount identities;
- no prior AL-003S blocked state or scoped one-shot container;
- zero Organization, Store, user, credential, membership, and STG-005A request
  rows, no approved-login collision, exactly one `OWNER` role, and
  `stores_id_seq last_value=1, is_called=false`;
- unchanged permitted Production container/image/start/restart metadata and
  health `200`, without a Production database or business-data read.

The reviewed launcher validation also returned `VALIDATED` for the exact
release, preflight digest
`7174a295d6f4696e766100001e5209f03dc055db6db213a3a5fd0a3365158236`,
project, and disabled-printing identity.

## Approved identity and plan invocation

The Owner resolved the earlier credential decision without lowering any guard:

```text
run_id: STG005_20260808_R01
organization: STG005_ORG_20260808_R01
source_store: STG005_SRC_20260808_R01
owner_login/display: STG005_OWNER_20260808_R01
```

The synthetic login had no collision. The password remains an ungenerated,
unrecorded 12-through-256-character runtime-only input. It was not requested,
read, supplied, logged, or retained because the failed action was the
password-free `bootstrap-plan` step.

A fresh action-specific readiness artifact passed with the retained
1-GiB-memory, two-CPU, 10-GiB-free-disk, and normalized-load thresholds. Its
SHA-256 was
`da6ea6f840379f15abb913a808ec2eaf921a1c827ee332c51d4b75ff229f9620`.
A distinct mode-0600 approval artifact bound the exact action, SHA, preflight,
readiness, and non-secret identity fingerprint; its SHA-256 was
`850a71c603a8f30b39f45d0836d35b5b5d89a6f8a71bca2958b9ea5063f5fa64`.

## Deterministic failure and fail-closed result

The launcher created the bounded immutable-image one-shot for
`bootstrap-plan`. Spring stopped during bean-factory safety validation before
the STG-005A command, transaction service, credential reader, or business data
path ran:

```text
Production safety check failed:
spring.flyway.enabled must be true for cloud/prod profiles.
```

The conflict is deterministic. The reviewed synthetic command must use exact
profiles `cloud,staging-synthetic-bootstrap`, non-web mode, and
`spring.flyway.enabled=false` so a one-shot can never apply a migration. The
older global cloud safety rule did not recognize that narrower guarded shape.

The launcher removed the scoped one-shot, retained health `200/200/200`, and
correctly persisted both the shared blocked marker and one lock record. A
post-failure read-only transaction proved all seven topology/request counts
remain zero and Flyway remains V10 with no failed row. Staging and Production
container continuity remained unchanged.

No retry, blocked-state cleanup, runtime patch, credential input, bootstrap
write, source-menu action, deploy, Flyway action, or Production mutation
followed.

## Bounded repository repair

The repair changes only `ProductionSafetyConfig` plus focused tests. It keeps
all ordinary `cloud`, `prod`, and `production` runtimes fail-closed with
Flyway required. The Flyway-disabled path is accepted only when every one-shot
predicate is simultaneously true:

- active profiles are exactly `cloud` and `staging-synthetic-bootstrap`;
- Flyway is exactly disabled and JPA DDL is exactly `validate`;
- web mode is `none`;
- printing and runtime seeding are disabled;
- exactly one of the guarded STG-005A or STG-005B commands is enabled;
- datasource URL and user identify the isolated Staging synthetic database.

The same profile is rejected if Flyway is enabled, because silently migrating
from a data command would violate the existing contract. Missing, broadened,
or contradictory predicates fail startup. JWT, developer fallback, role
switcher, seed, DDL, credential, Store-isolation, and application-command
guards are unchanged.

The package adds no endpoint, migration, Store-specific writer, credential,
runtime configuration value, deployment action, or Production behavior.

## Verification and review gate

Repository verification on the final uncommitted repair content passed:

- 54 focused tests covering `ProductionSafetyConfig` and retained STG-005A/
  STG-005B command, guard, and safety-shape behavior: 54 passed, zero failed,
  zero errors, zero skipped;
- full backend regression: 388 tests, zero failed, zero errors, three skipped;
- backend compile;
- the AL-003S launcher shell regression;
- `git diff --check`, Markdown links across all 15 changed governance/readme
  files, high-confidence secret scan with zero findings, scope scan, and
  governance-drift scan.

Independent Agent 6 initially blocked the repair because the first datasource
comparison normalized case and whitespace. The repair now compares both URL
and user against the raw resolved property exactly and rejects case/whitespace
aliases; focused and full regressions were rerun. Agent 6's final re-review is
`ACCEPT` with no remaining finding.

The GitHub gate then passed against exact base `828af4e8...`: PR #85 contained
one expected commit (`8582805b...`) and 17 expected files, was conflict-free,
`MERGEABLE/CLEAN`, had no failed or pending check, and retained Agent 6
`ACCEPT`. It was marked ready and merged under the permanent Auto-Merge Policy
as `c95c3840fa972f84b3e5dbd345fef3e4c12aa8c6`. Both the reviewed head and
merge commit were verified ancestors of `origin/main`.

## Runtime boundary and next Owner Gate

PR #85 created a new exact `main` SHA with a backend change. It cannot repair
the already-built/deployed `2837ae88...` image, and neither
the failed readiness/approval nor the retained STG-007 deployment evidence may
be rebound to that changed candidate. The shared blocked state also requires
a separately approved recovery that first confirms one-shot absence, zero
transaction state, V10, health, and continuity before removing both retained
blocked records.

The next runtime batch therefore requires explicit Owner approval for:

1. the freshly fetched latest exact `main` containing PR #85 (a later
   governance-only merge may advance it without changing this repair);
2. a fresh exact Staging release/preflight and Staging-only deployment of that
   SHA with no new migration expected;
3. bounded blocked-state recovery after read-only safety confirmation; and
4. a complete restart of STG-008 from fresh readiness and new per-action
   approvals.

Until that gate is granted, do not request the password or start another
one-shot. The unique stop state after repository publication is:

`STG-008_DEPENDENCY_REPAIR_IN_MAIN_WAITING_FOR_EXACT_SHA_STAGING_REBIND_AND_BLOCKED_STATE_RECOVERY_OWNER_RUNTIME_APPROVAL`
