# Menu Simplification — Implementation Validation

## Scope and baseline

Owner-authorized Combo item defaults and central Add-on catalog; Production
mutations and Phase C are not authorized. Execution state is exclusively in
[`CURRENT_STATE.yml`](CURRENT_STATE.yml). Contract:
[`MENU_ADDON_CATALOG_CONTRACT.md`](contracts/MENU_ADDON_CATALOG_CONTRACT.md).

Fresh implementation baseline: `278851f0c4d932a149d598964abca765bb2b5083`.
Isolated branch `codex/menu-product-simplification`; the original dirty Owner
checkout was not modified or used as release input.

2026-09-15 read-only runtime baseline: both Staging and Production containers
use release `b4d0350c15cf777ff0ee53e9acb0d4e4127d9b99`, newer than the prior
CURRENT_STATE runtime record. Immutable backend image:
`sha256:41d8e2a2743b816fb94af8560f5e0954e1b06c3eb7968e5fa68d7f74d259e754`;
frontend image:
`sha256:7392658e65d9117bfa1ddebba9ccdbff4a5acf5549a821a34989a3c90860a9d1`.
Both database containers were healthy; Staging system health UP and frontend
HTTP 200. No application/database/credential mutation was performed for this
baseline observation.

Production database container identity:
`c2ab37fec6ac966e77a1d8ab7aa41d1da294b9ac57b1a21ce18904d04cfae91e`.
Staging database container identity:
`b64d3c676dbb4003368279453e5c6b390ac6327c3cf28001ead671155f93f4c5`.

## Repository validation

- Backend post-review consolidated focused/regression: 26 classes, 213 tests,
  zero failures/errors/skips; a further four unchanged authorization test classes
  passed 27 tests. Real PostgreSQL 16 clean V1→V28 and V26→V28 migration,
  constraints and repeat-migrate tests passed. Service transaction/rollback and
  concurrency tests use H2, not PostgreSQL; no PostgreSQL service concurrency or
  old-backend-binary rollback execution is claimed.
- Frontend post-review: 18 files / 143 tests plus successful TypeScript/Vite
  build, including 30 new semantic-rename regression cases. After
  integration alignment, menu cache tests passed 11/11 with the shared backend
  `menu-catalog-v4` fixture `fnv1a32:d0abe163`; legacy v2/v3 hash paths retained.
- Bounded acceptance script: 20 safety/fixture tests PASS. These are tool tests,
  not evidence of actual Staging business acceptance.
- Existing `test_staging_runtime_evidence.sh`: PASS with V27/V28 checksum manifest.
- Governance validation: PASS on an exact staged-file snapshot without ignored
  dependency directories. The installed node_modules tree is not repository
  authority and was excluded from this validation copy.
- Staged/unstaged `git diff --check`: PASS.

Initial independent Agent 6 found four P2s (no P0/P1): legacy pricing writer
bypass, label-based Combo misclassification, ambiguous duplicate relationships,
and Platform Combo writer bypass. These were repaired with focused regressions;
consolidated re-review on 2026-09-15 returned **AGENT_6=ACCEPT**, all four closed,
no remaining actionable P0/P1/P2. Independent review session:
`01a0a64c-ab7b-71e0-a62d-f4f7f5358f53`. Review covered actual staged code and
tests, stable semantic rename reproduction, migrations, isolation and acceptance
tool safety. Reviewer did not mutate code/runtime or repeat full tests.
PR/merge and actual Staging acceptance require completed runtime evidence;
the above local tests and review do not claim those outcomes.

## Production business decisions

The explicitly read-only conflict audit found five conflicting groups in each
of Production Stores 1, 2 and 3. Exact unresolved values are in
[`MENU_ADDON_PRODUCTION_DECISIONS.md`](MENU_ADDON_PRODUCTION_DECISIONS.md).
No value was selected and no current or historical data was changed.

## Product versus tooling scope

Product changes implement the approved Combo/catalog UX, additive V27/V28,
existing option materialization, immutable semantic code and current revision
refresh. Printing resolver, native app and historical order model are unchanged.

Necessary tooling changes are the focused synthetic acceptance script/tests,
read-only conflict report query, V27/V28 checksum entries and existing bounded
fixture cleanup dependency compatibility. No new deployment, credential,
printing or provisioning engine was introduced.
