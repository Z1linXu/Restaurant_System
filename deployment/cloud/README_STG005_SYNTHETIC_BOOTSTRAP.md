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

This PR intentionally does not add a server launcher. A future execution
approval must publish the exact one-shot container or JAR command, bind it to
the approved runtime/tool SHAs and V9 evidence, and review that command before
it runs. Do not improvise with the production checkout, production `.env`,
raw SQL, `RuntimeDataSeeder`, or developer role switching.

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
6. Obtain a separate Owner approval for write mode and the exact launcher.
7. Run write mode once, then exact replay once.
8. Verify only the synthetic IDs and Store/Organization membership boundaries.
9. Stop for Owner review before any menu, table, order, restart, or other
   STG-005 acceptance step.

No SSH, migration, bootstrap execution, or server write was performed while
creating this implementation.
