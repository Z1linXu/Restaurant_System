# STG-005A Synthetic Bootstrap Runbook

> Status: `IMPLEMENTED_NOT_EXECUTED`
>
> Scope: one-shot creation of the minimum synthetic Organization, source Store,
> Owner identity, and Owner memberships required by STG-005.

This command is not a normal application endpoint and is not enabled in the
running backend. It is a profile-gated, non-web `ApplicationRunner` intended
only for a separately approved operation against the isolated Staging database.
Merging this implementation does not authorize a migration, server command, or
synthetic data write.

## Fixed safety identity

Execution is rejected unless every condition below is true:

- active profiles are exactly `cloud,staging-synthetic-bootstrap`;
- Compose project is `restaurant-pos-staging`;
- Staging root is `/srv/restaurant-pos/staging`;
- expected and observed runtime SHAs are identical full 40-character SHAs;
- the bootstrap tool is identified by its own full 40-character SHA;
- database name and user are both `restaurant_pos_staging`;
- web application type is `none`;
- application printing is disabled and requested printing mode is `DISABLED`;
- the command enable property is explicitly true;
- every run, Organization, Store, login, and display name uses `STG005_`.

The runtime SHA and tool SHA are separate. The runtime SHA identifies the
already approved Staging application being accepted. The tool SHA identifies
the reviewed bootstrap implementation. Neither value may be shortened.

## Data created

One successful first execution creates exactly:

- one active synthetic Organization;
- one active synthetic source Store under that Organization;
- one active synthetic Owner user;
- one BCrypt credential;
- one active Owner Store membership;
- one active Owner Organization membership;
- one sanitized bootstrap request record.

It does not create a target Store or an Owner membership for a target Store.
The Owner Organization membership and source-Store membership are sufficient
only for the bounded source bootstrap contract. They do not by themselves
prove that AL-003 Staging acceptance can log in, create or access a target
Store, or call the clone APIs. Those prerequisites require separate runtime
evidence and the approved application flow; they must not be supplied with raw
SQL, copied Production credentials, authorization bypasses, or developer login
switching.

The Store is created with `printing_enabled=false`,
`printing_mode=DISABLED`, and bar/KDS task generation disabled. The command
does not create menu data, tables, orders, printers, Pad devices, customer
data, or production identities.

Flyway V9,
`V9__add_staging_synthetic_bootstrap_requests.sql`, adds only the request
record table, its unique `run_id`, and Organization/Store lookup indexes. It
contains no seed data. Applying V9 is a separate Owner-approved Staging
migration action. The bootstrap profile disables Flyway so the one-shot
command cannot silently migrate a database.

## Default mode and execution gate

The command is disabled unless:

```text
STG005_BOOTSTRAP_COMMAND_ENABLED=true
```

When enabled without `--execute` and `--password-stdin`, it validates all
environment and request guards, prints one sanitized `VALIDATED` line, and
does not call the transactional bootstrap service.

Write mode requires both flags:

```text
--execute --password-stdin
```

Supplying either flag alone is rejected. The password is one line read from
standard input. It must never be supplied as a command-line argument,
environment variable, file, shell trace, evidence field, or PR comment.

## Argument contract

Every invocation supplies exactly one value for each argument:

```text
--run-id=STG005_<RUN>
--organization-name=STG005_ORG_<RUN>
--organization-code=STG005_ORG_<RUN>
--source-store-name=STG005_SRC_<RUN>
--source-store-code=STG005_SRC_<RUN>
--owner-login=STG005_OWNER_<RUN>
--owner-name=STG005_OWNER_<RUN>
--expected-runtime-sha=<approved-runtime-full-sha>
--observed-runtime-sha=<observed-runtime-full-sha>
--tool-sha=<reviewed-bootstrap-tool-full-sha>
--compose-project=restaurant-pos-staging
--staging-root=/srv/restaurant-pos/staging
--printing-mode=DISABLED
```

Unknown, missing, duplicated, positional, or valued flag arguments are
rejected. Use a synthetic run suffix with no customer, employee, phone, email,
or restaurant information.

## Password handling

An approved operator must disable shell tracing before reading the password.
The launcher must pipe the password directly to standard input without
printing it. The command converts it only for the existing BCrypt provisioning
service and clears its mutable input buffer after use.

The dependency-bound AL-003S preparation adds the reviewed guarded launcher at
`staging-synthetic-acceptance.sh`. It independently binds the exact release,
private environment digest, formal preflight evidence, Compose project,
printing mode, fresh Staging/Production readiness fingerprints, action-specific
Owner approval, immutable running backend image ID, loopback binding, and
health before a one-shot command. Its presence is not runtime approval. Do not improvise with
the production checkout, production `.env`, raw SQL, `RuntimeDataSeeder`, or
developer role switching.

The non-web profile deliberately sets Flyway disabled. The shared production
safety guard accepts that state only for the exact
`cloud,staging-synthetic-bootstrap` profile pair when web mode is `none`, JPA
DDL is `validate`, printing and runtime seeding are disabled, exactly one
STG-005 command is enabled, and the datasource URL/user identify the isolated
Staging database. The same profile rejects Flyway enabled. This is a bounded
no-migration one-shot contract, not a general cloud Flyway bypass.

## Idempotency and transaction behavior

- The first successful run stores a SHA-256 request fingerprint and result IDs.
- Exact replay with the same run ID, payload, runtime/tool SHAs, topology, and
  password returns the original IDs with status `REPLAYED`.
- Reusing the run ID with changed content or password returns
  `STG005_BOOTSTRAP_IDEMPOTENCY_CONFLICT`.
- A topology or membership that no longer matches the completed record returns
  `STG005_BOOTSTRAP_RESULT_UNAVAILABLE` and requires Owner review.
- Organization, Store, identity, credential, memberships, and request record
  are in one transaction. A failure leaves no partial bootstrap topology.

The request record does not contain the password, password hash, token,
printer endpoint, or complete request payload.

## Sanitized evidence

Allowed output is limited to one of these shapes:

```text
STG005_BOOTSTRAP|status=VALIDATED|run_id=<synthetic>|runtime_sha=<sha>|tool_sha=<sha>
STG005_BOOTSTRAP|status=CREATED|run_id=<synthetic>|organization_id=<id>|source_store_id=<id>|owner_user_id=<id>|runtime_sha=<sha>|tool_sha=<sha>
STG005_BOOTSTRAP|status=REPLAYED|run_id=<synthetic>|organization_id=<id>|source_store_id=<id>|owner_user_id=<id>|runtime_sha=<sha>|tool_sha=<sha>
```

Do not retain stdin, shell history containing credentials, raw application
arguments with secrets, database connection secrets, or full environment
output. IDs and SHAs are evidence only; they are not authorization to continue
STG-005.

## Required approval sequence

1. Merge the reviewed implementation.
2. Select and approve exact runtime SHA, tool SHA, run ID, and synthetic names.
3. Review and separately approve V9 application to the isolated Staging
   database.
4. Verify V9 and JPA validation without changing Production.
5. Run validate mode and retain only sanitized output.
6. Collect fresh bounded readiness evidence and obtain a digest-bound Owner
   approval for the exact action and synthetic identity fingerprint.
7. Obtain a separate Owner approval for write mode and the exact launcher.
8. Run write mode once, then exact replay once.
9. Verify only the synthetic IDs and Store/Organization membership boundaries.
10. Stop for Owner review before any menu, table, order, restart, or other
   STG-005 acceptance step.

No SSH, migration, bootstrap execution, or server write was performed while
creating this implementation.

## STG-008 runtime checkpoints (2026-08-08)

The Owner-authorized STG-008 entry performed a read-only check against exact
deployed Staging SHA `2837ae88e55142c99c6975f8b6575febffc913a1` at Flyway
V10. It found zero Organization, Store, user, credential, membership, and
bootstrap-request rows. `stores_id_seq last_value=1, is_called=false` proves
the first guarded bootstrap can still receive source Store ID `1` without a
write probe or sequence reset. Synthetic Owner is `NOT_CREATED`.

Execution stopped before `bootstrap-plan`: the requested account convention
does not satisfy this runbook's mandatory `STG005_` identity prefix and
12-through-256 runtime password contract. Do not lower either guard, transform
or invent the Owner's secret, or run a one-shot merely to reproduce the known
failure. Resume only after an explicit Owner decision supplies a compatible
safe login/display identifier and runtime-only password. The sanitized record
is [STG-008 entry evidence](../../docs/archive/governance-pre-simplification/runtime/STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md).

The Owner later approved `STG005_OWNER_20260808_R01` and retained the password
contract. Fresh Ground Truth/readiness passed, but the password-free
`bootstrap-plan` one-shot stopped before the command or data path because the
older production safety guard did not recognize this profile's required
Flyway-disabled shape. Cleanup succeeded, topology remained empty, and the
launcher persisted blocked state. The bounded repository correction retains
all ordinary cloud guards and adds the exact bidirectional one-shot contract
above. That repair entered main through PR #85. A new exact release/deploy and
separately approved blocked-state recovery are still required; the old image
cannot be patched or retried. See
[STG-008 Flyway guard repair evidence](../../docs/archive/governance-pre-simplification/runtime/STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md).
