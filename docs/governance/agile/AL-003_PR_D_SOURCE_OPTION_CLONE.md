# AL-003 PR-D Generic Source Option Clone

> Status: `AL-003_PR_D_PROMOTION_WAITING_FOR_OWNER_REVIEW`
>
> Promotion base: `e6b41dd644c50b847d27947b5b0d27e1d4449c09`
>
> Semantic source commit: `5a0dc09944b4b0945fe95027d7f12647212ea559`
>
> Runtime/deployment: `NOT_EXECUTED`

## Scope

PR-D adds the generic `SOURCE_OPTIONS` graph composer used inside the locked
menu-clone transaction. A reviewed Store Profile must classify every active
source option group used by an application as either `COPY` or
`PROFILE_OVERRIDE`. The shared composer contains no Store ID, Store name,
Chinatown SKU, seven-noodle-type definition, Combo rule, price override, or
public API behavior.

PR-D does not add a Migration, Controller, endpoint, runtime menu clone, or
concrete Chinatown option selection. PR-E owns the concrete
`ChinatownMenuCloneProfile` applications and all profile-created or overridden
options.

## Profile capability

Profiles that want source-option copying implement
`StoreMenuCloneSourceOptionsProfile`. Each application declares:

- one selected source item SKU;
- one existing target item SKU from the PR-C base graph;
- one or more reviewed `(option_type, option_group, disposition)` rules.

`COPY` preserves the active source option graph. `PROFILE_OVERRIDE` reserves
the classified group for PR-E and deliberately excludes its source rows from
this composer. Any active source option without exactly one reviewed rule fails
closed instead of being silently omitted. Every reused item that has active
source options must have an application; omission is not an implicit opt-out. A
target item cannot merge option graphs from different source items. Profile
codes remain exact and case-sensitive through the existing registry.
For `CLONE_IF_ACTIVE_OR_CREATE`, the same reviewed application is conditional:
it copies/classifies options when the source item exists and cleanly contributes
zero rows when PR-C creates the target without a source item.

The capability is generic. A new Store Profile can provide a different set of
applications without changing the composer.

## Clone algorithm

1. Verify source snapshot Store ownership. Exact repeated source-item snapshots
   are deduplicated for one-source-to-many-target mappings; conflicting repeats
   and duplicate source option IDs fail closed.
2. Resolve every profile application by stable source and target SKU.
3. Require application coverage for every reused item with active source
   options, and reject null active-state evidence.
4. Classify every active source option through the reviewed rules. Copy the
   `COPY` groups, reserve `PROFILE_OVERRIDE` groups for the later composer, and
   reject every unclassified active option.
5. Require copied `option_code` values to be exact, nonblank, and unique per
   target item after normalization.
6. Validate every copied parent before any write. Missing, inactive, outside the
   copied graph, self, cross-item, cross-Store, or cyclic parents fail closed.
7. Expand applications into target-local logical keys
   `(target_item_id, source_option_id)`.
8. Sort applications by stable source/target SKU and options by source
   `sort_order` with source option ID as the deterministic tiebreaker.
9. Insert fresh target options with temporary null parent IDs and flush.
10. Resolve parent IDs from fresh target IDs, update the same target rows, and
   flush again.
11. Return only the created option count. Source-to-target option ID maps remain
   transaction-local and are neither persisted nor exposed.

The copied fields are `option_type`, `option_code`, `option_group`,
`sort_order`, Chinese/English names, `price_delta`, and active state. Source IDs,
timestamps, and parent IDs are never reused.

## Transaction and failure behavior

The composer is invoked by PR-C while source and target Store locks are held.
It participates in the enclosing transaction and does not catch persistence or
later-composer failures. A failure in either persistence pass, final graph
validation, or a later `PROFILE_OVERRIDES` composer rolls back the complete
target graph and revision update.

Source graph problems use sanitized `SOURCE_OPTION_AMBIGUOUS` conflicts.
Invalid profile or generated target evidence uses the existing sanitized
`TARGET_MENU_VALIDATION_FAILED` contract. Messages contain no option payload,
credentials, token, endpoint, or runtime secret.

## Verification coverage

- reviewed active `COPY` options copy to fresh target items;
- reviewed `PROFILE_OVERRIDE` options are reserved for PR-E and not copied;
- unclassified active option groups fail closed before persistence;
- missing reused-item applications, null active states, blank/duplicate copied
  option codes, and mismatched source-target applications fail closed;
- `CLONE_IF_ACTIVE_OR_CREATE` applications cover both source-present and
  source-absent paths without weakening classification;
- copied values and deterministic order are preserved;
- inactive options are ignored;
- same-target parent IDs are mapped in two persistence passes;
- missing, inactive, outside-graph, self, cross-item, cross-Store, cyclic, and
  duplicate parent evidence fails before writes;
- exact Profile code support is retained;
- a later transaction failure rolls back both option persistence passes;
- existing PR-C composer ordering, target count, ownership, lock, revision, and
  rollback tests remain part of the required suite.

## PR-E handoff

PR-E may make the versioned Chinatown Profile implement the capability and
declare reviewed source/target applications. It must keep seven noodle types,
Combo definitions, target-only options, names, prices, and ordering in the
Profile/`PROFILE_OVERRIDES` package. Adding those rules requires updating the
Profile fingerprint. Shared PR-D code must not be changed to recognize a Store
name, Store ID, or Chinatown-specific code.

## Independent review

Agent 6 completed an independent read-only review of the promoted production
files, focused tests, transaction integration boundary, and governance diff.
Result: `PASS`, with no blocking correctness or scope finding. The review also
confirmed that the two promoted production files are byte-identical to the
reviewed PR-D source commit.

Residual evidence remains intentionally deferred: PR-E must exercise the real
composer through the complete transaction service when it supplies the first
concrete option-capable profile; PostgreSQL identity/flush behavior remains a
future environment check; and any blank or duplicate legacy Store 1
`option_code` will fail closed until separately approved source-menu evidence
proves the graph safe. None of these conditions authorizes runtime access or a
real clone in PR-D.
