# STG-005B Synthetic St-Denis Source Menu Runbook

> Capability state: `IN_MAIN_IMPLEMENTED_NOT_EXECUTED` via PR #62
>
> Scope: guarded, non-web creation of one reviewed synthetic source-menu graph
> for AL-003 Staging acceptance.

This tool is not a Production menu importer, a public HTTP endpoint, or live
St-Denis evidence. It is a profile-gated `ApplicationRunner` that can operate
only against the isolated Staging identity already used by STG-005A. Merging
the implementation does not authorize SSH, deployment, Flyway, bootstrap,
credential creation, or menu writes.

## Reviewed manifest

The immutable manifest identity is:

```text
manifest: STG005_ST_DENIS_SOURCE_MENU
version: STG005_SOURCE_MENU_V1
source graph: 4 categories / 3 stations / 13 items / 38 options
```

The graph is a synthetic technical fixture derived from the reviewed AL-003
contract and its integration fixture. It is not copied from Production and
does not assert the current contents or prices of live Store 1.

Organization, Store, user, display names, and evidence identities use the
`STG005_` marker. Menu category/station codes, SKUs, option groups, and option
codes keep their reviewed AL-003 semantic values, such as
`traditional_beef_noodle`, `NOODLE_TYPE`, and `tea_egg`. Prefixing those
technical identifiers would make the source incompatible with the clone
profile.

The source graph has 38 options. The existing Chinatown profile expands that
input into a validated target plan of 4 categories, 3 stations, 17 items, and
74 options. These are different evidence counts and must not be interchanged.

## Safety gates

The command is disabled unless:

```text
stg005.source-menu.command-enabled=true
```

Every invocation is rejected unless all of the following are true:

- active profiles are exactly `cloud,staging-synthetic-bootstrap`;
- Compose project is `restaurant-pos-staging`;
- Staging root is `/srv/restaurant-pos/staging`;
- expected, observed, and tool identities are full lowercase 40-character Git
  SHAs with exact bindings;
- database name and user are both `restaurant_pos_staging`;
- web application type is `none`;
- application printing and requested printing mode are both disabled;
- source Store ID is exactly `1`, matching the reviewed AL-003 profile source;
- source Store code is exact and begins with `STG005_`;
- exactly one completed STG-005A bootstrap record binds that Store and its
  Organization to the same runtime/tool SHAs;
- the Store is active, belongs to an Organization, and remains
  `printing_enabled=false`, `printing_mode=DISABLED`.

The profile disables Flyway. The command cannot silently migrate the database.
No password, token, endpoint, customer, order, payment, printer, or device
input is accepted.

The shared production safety guard permits Flyway disabled only for the exact
guarded non-web Staging synthetic shape: exact profiles, `ddl-auto=validate`,
printing/runtime seeding disabled, exactly one STG-005 command enabled, and
the fixed isolated Staging datasource URL/user. It rejects Flyway enabled for
this one-shot and continues to require Flyway enabled for ordinary cloud/prod
runtime. This is safety narrowing, not a general exception.

## Modes

Default mode is read-only planning. It loads the Store and returns one
sanitized `VALIDATED` evidence line only when the Store menu is empty or
already exactly equal to the manifest.

Write mode requires the explicit flag:

```text
--execute
```

The dependency-bound AL-003S preparation adds the guarded launcher at
`staging-synthetic-acceptance.sh`. A later Owner-approved runtime package must
still bind its exact release/env/preflight inputs and approve the selected
plan/write action before either mode runs. The launcher also requires fresh
resource and Staging/Production fingerprint evidence, an action/identity-bound
Owner approval artifact, and the immutable image ID of the running backend.

The command-line project/root/SHA/mode values remain guarded request inputs, not
independent observations by themselves. The AL-003S launcher derives and
checks release HEAD, private env/preflight digests, project, root, printing,
running image, loopback binding, and health before forwarding those values.
Matching caller-supplied literals alone remain insufficient evidence.

Every invocation supplies exactly one value for:

```text
--source-store-id=1
--source-store-code=STG005_SRC_<RUN>
--expected-runtime-sha=<approved-runtime-full-sha>
--observed-runtime-sha=<observed-runtime-full-sha>
--tool-sha=<reviewed-tool-full-sha>
--compose-project=restaurant-pos-staging
--staging-root=/srv/restaurant-pos/staging
--printing-mode=DISABLED
```

Unknown, missing, duplicate, positional, or valued `--execute` arguments are
rejected.

## Transaction and replay contract

The write service locks source Store 1 before inspecting or changing its menu.

- `EMPTY`: create the entire fixed graph in one transaction and increment
  `menu_revision` exactly once.
- `EXACT`: return `REPLAYED`; create nothing and do not change the revision.
- partial, extra, inactive, renamed, repriced, recosted, or otherwise different graph:
  return `STG005_SOURCE_MENU_GRAPH_CONFLICT`; do not repair, delete, or write.
- a late failure rolls back categories, stations, items, options, parent links,
  and the revision increment.
- concurrent execution serializes on the Store lock, producing at most one
  create and one or more exact replays.

The graph itself is the replay authority, so this bounded package needs no new
migration or request table. Durable operational invocation history would be a
separate Owner decision and migration gate.

## Sanitized evidence

Allowed evidence contains only status, completed bootstrap request ID, source
Store ID, runtime/tool SHA, manifest code/version, manifest fingerprint,
revision before/after, aggregate counts, and result code.
It never includes the manifest payload, ID maps, credentials, tokens,
connection strings, endpoints, customers, orders, payments, or raw exceptions.

## Runtime approval sequence

1. Merge the architecture package and this dependency-bound implementation in
   order.
2. Select a fresh exact merged-main release SHA and tool SHA.
3. Collect fresh AL-003S readiness evidence and obtain explicit, action-bound
   Owner approval for the Staging mutation sequence.
4. Deploy and verify the isolated Staging release and V9/V10 separately.
5. Execute STG-005A and prove the synthetic source Store is ID `1`.
6. Review the exact STG-005B launcher and run default planning mode.
7. Obtain the write checkpoint, run `--execute`, then one exact replay.
8. Verify revision/counts and run the existing read-only AL-003 validate API.
9. Stop for Owner review before target clone execution.

No runtime command was executed while creating this package.

## STG-008 runtime entry checkpoint (2026-08-08)

STG-008 did not reach source-menu planning. Read-only Staging evidence found no
completed STG-005A request and no source Store, so the required source Store ID
`1` / bootstrap provenance does not exist. STG-005A itself stopped before plan
or write at the credential-contract Owner Gate. Consequently no category,
station, item, option, relationship, fingerprint, or revision mutation
occurred, and the 4/3/13/38 graph remains repository authority only.

Do not bypass the parent bootstrap, create the graph with raw SQL, or treat
this entry `NO_GO` as source-menu failure. See
[STG-008 entry evidence](../../docs/archive/governance-pre-simplification/runtime/STG-008_SYNTHETIC_TOPOLOGY_SOURCE_NO_GO_EVIDENCE.md).

After the Owner aligned the credential contract, fresh `bootstrap-plan`
reached the guarded one-shot but failed before the STG-005A command because
the older shared production safety rule required Flyway enabled for every
cloud profile. The bounded repair models the exact Flyway-disabled one-shot
contract without weakening ordinary cloud/prod startup. No topology or
source-menu row was written. Source-menu plan/create/replay remain blocked
until the PR #85 repair is deployed in a new exact backend image and the
retained AL-003S blocked state receives separate Owner-approved recovery. See
[STG-008 Flyway guard repair evidence](../../docs/archive/governance-pre-simplification/runtime/STG-008_STAGING_SYNTHETIC_FLYWAY_GUARD_REPAIR_EVIDENCE.md).
