# Five POS fixes: implementation and Staging evidence

Owner request: implement all five changes, test, update technical contracts and
deploy the existing Staging POS. Dedicated branch
`codex/pos-five-fixes-staging`; latest merged baseline
`7d573be03d66a9505e0c68f92fa17829247f56e3` (includes PR #280/#281). The protected dirty
Owner checkout was not changed. No `main` merge, Production mutation, real
printer dispatch or external order action is authorized by this release.

## Implementation checklist

- [x] Size/Combo/Add-on per-unit deltas multiply by item quantity; DRAFT submit
  uses frozen prices; submitted old lines stay frozen on Update. Frontdesk
  per-copy prices sum to the persisted line amount, including old uneven cents.
- [x] Stable SOUP_BASE order snapshots render vegetable soup on Frontdesk;
  missing historical soup remains unknown. Rule-first NO_SPICY resolver is shared
  by GRAB/HOT/Preview and distinguishes empty output from missing configuration.
- [x] Independent Store Remove catalog and V38, category/item eligibility,
  immutable code, free linked assignments, explicit conflict-preserving legacy
  reconciliation, Combo Side identity/parent checks and ingredient conflicts.
- [x] Shared Removes UI, Store-safe refresh, linked-option ownership and
  Add/Remove last-selection behavior. Clone/provisioning creates target Store
  associations without copying source master IDs.
- [x] Existing PrintingDisplayRulesPanel moved to Menu Management after Pricing
  Rules; URL/refresh/Store/permission/module checks preserved. Printing Settings
  retains its other functions and points to the new editor.
- [x] Independent Agent 6 product/deployment review accepted after repairing
  dynamic Side bindings, category delete FKs, paid Remove bypass and deployment
  datasource overrides. Acceptance-script review and its 13 local fake tests passed.
- [x] Dedicated branch pushed; draft PR [#282](https://github.com/Z1linXu/Restaurant_System/pull/282) attached.
- [ ] Staging application images replaced; V38 and public HTTPS verified.
- [ ] Actual Staging orders/receipts/Update/reprints/Remove/UI acceptance recorded.

## Automated validation

Final full Maven suite after PR #281 integration: **1,046 tests**, zero
failures/errors, 83 conditional skips (**963 executed**). The seven Store Remove
PostgreSQL tests ran against a dedicated temporary PostgreSQL 16 cluster,
including clean/V37 upgrade, repeated migration, Store isolation, immutable/free
constraints, applicability, reconciliation/rollback, real Side bindings and
category/item delete cascades. No shared developer database was used.

The additional `PrintingDisplayRuleLifecyclePostgresIntegrationTest` passed on
its own dedicated database. An initial missing nonempty test-password variable
was corrected before rerunning; application code was unchanged.

Frontend: 318 tests in 55 files passed, TypeScript/Vite production build passed,
and targeted ESLint passed. Coverage includes frozen/offline Outbox submissions,
pricing matrix, actual editor Save/Publish/Preview, selected Store transitions,
shared Remove maintenance and dynamically linked Combo Side Remove options.
Backend production package also built successfully. Governance validation on
the tracked release snapshot passed; the 13 deploy/acceptance guard tests passed.
Backend pricing integration round-trips DTO → service → flushed database →
renderer with base 16.99, size ±2, Combo 5, paid egg 1.99 and quantity 2.

Fixed-price fixture examples: large ×2 = 37.98 + 5.69 tax = 43.67;
large Combo ×2 = 47.98 + 7.19 tax = 55.17; large Combo plus paid egg ×2 = 51.96.
These are test fixtures, not changes to the maintained Staging menu prices.

## Staging preflight and release boundary

Public target: <https://staging-pos.lanzhounoodlesmtl.com>.
Observed backend `ce35278b3569a4eacfa29efc01cadbf0c409bec6`, frontend
`8c3a9e356f59cd025beff25f7fb1d5a20594e028`, Flyway V37 before this release.
The existing Staging project, network, database `restaurant_pos_staging` and
data path `/srv/restaurant-pos/staging/state/postgres` are separate from
Production. Existing HTTPS ingress and sandbox integration configuration are
preserved, and no CI workflows exist for the dedicated branch push.

The actual Chinatown fixture is Store **18**, code `CHINATOWN`, kind
`VALIDATION_FIXTURE`, source `PHASE_B_OWNER_PROVISIONING`. It initially uses
PAD_DIRECT; acceptance changes only this explicitly authorized fixture to MOCK
before new orders. Existing test orders and three old PRINTING jobs 200/205/207
are retained. No queue cleanup or replay is performed.

Initial private server backup: `pos-five-fixes-20261022/staging-before.dump`,
1,214,173 bytes, SHA-256
`43f4ee0b4752c5aa6a80edd7eb7f96fc54052aa367fa422fcf865b70c58842dd`;
archive contents checked. The deployment helper makes another fresh backup
immediately before the release and records its digest privately. Runtime
models, dumps, credentials and build logs remain server-side.

Deployment/rollback procedure:
[`deployment/cloud/pos-five-fixes/README.md`](../deployment/cloud/pos-five-fixes/README.md).
Only the Staging backend/nginx applications are replaced. Application rollback
uses their saved actual models; additive V38 is retained. Production container
IDs/start/restart counts and Flyway ledger plus old Staging financial, option,
task and print snapshots are checked for continuity.

## Deployment and acceptance results

The first attempt at `19ba04bf0e42ae70b887b224c86a235715de46e1` stopped at
the initial image identity guard, before backup/build/migration/container writes.
A concurrent independent Staging operation had restored its backend to the
known V37 ancestor `8eea8d47c9fe7906563d051196c703da7c14e198`, image
`sha256:6512955fe8b066855fa1e4b3675549cf4b10c975340b4f6d1db000aaff653f8b`.
Production containers/start/restart counts and migration ledger were unchanged.
The helper's exact backend image pin was updated to this verified baseline;
its actual model becomes the application rollback target. No broader image
allowlist or bypass was added. The current branch includes that ancestor and
the merged #280/#281 changes.

Not yet claimed. This section will be replaced with exact deployed SHA, UTC
time, V38 outcome, backup digest, actual orders/jobs/rendered text, browser
checks and unchanged Production/other-Store evidence after execution.

## Technical references and remaining verification

Updated [system reference](../SYSTEM_DOCUMENTATION.md), [API](../doc/API.md),
[database design](../doc/DatabaseDesign.md),
[pilot scope](CURRENT_PILOT_SCOPE.md),
[Frontdesk/GRAB rules](operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md),
[Remove catalog](operations/REMOVE_CATALOG.md),
[Add-on contract](governance/contracts/MENU_ADDON_CATALOG_CONTRACT.md),
[Store override/clone contract](governance/contracts/STORE_MENU_LOCAL_OVERRIDE_CONTRACT.md),
[Staging](../deployment/cloud/README_STAGING.md) and
[rollback](../deployment/cloud/README_ROLLBACK.md) runbooks.
Shared renderers are tested in MOCK; physical paper, transport and Android Pad
printer hardware still need a separate authorized field test. This package
does not send physical test tickets.
