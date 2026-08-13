# Phase A0.1 Standard Size Pricing Policy Staging Evidence

Status: `DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST`

Date: 2026-08-13

Owner approval:

```text
PHASE_A0_1_PRICING_POLICY_SCHEMA_CHANGE_APPROVAL
```

Runtime boundary:

- Production: `NO MUTATION`
- Staging: exact-SHA deploy only
- Deployed Staging application SHA:
  `ed3e4cdbf38c4d8812620baf64cd42ce3a229431`
- Staging Flyway: V10 -> V11
- Stop state:

```text
PHASE_A0_1_STANDARD_SIZE_PRICING_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```

## Repository and review lineage

- PR #131 implemented additive Flyway V11, Store-level Pricing Rules,
  system-controlled Size Configuration, item Combo policy, catalog/hash/cache
  pricing policy semantics, rollback compatibility mirror writes, frontend UI
  and repository evidence.
- PR #132 closed the Agent 6 post-merge bounded repair: generic Owner option
  write APIs now reject Combo upcharge rows, so `store_pricing_policies` remains
  the only new-application canonical Size/Combo pricing source.
- Agent 6 result for the repair: `ACCEPT`.

## Runtime evidence

| Evidence | Result |
|---|---|
| Staging dedicated repository import | `STAGING_REPO_IMPORT PASS`; prior main `c83933f16f4eb1c1be33bd13772ac489d79a7176`; approved main `ed3e4cdbf38c4d8812620baf64cd42ce3a229431` |
| release/env rotation | `OPS001_RELEASE_ENV PASS`; approval SHA-256 `75dba7712d305a7a2c30cbf4369cfea3e78e7a85e8b423470186c91a01f5431f`; recovery record SHA-256 `35a3a948631d853532c4db767dd84c88d5b1caa68b3b24894b58c5cebe456642` |
| preflight | `/srv/restaurant-pos/staging/evidence/phase-a0-1-pricing-policy-preflight-ed3e4cdbf38c4d8812620baf64cd42ce3a229431.txt`; SHA-256 `7ffee4d7d65e209a2ca85b65f6166aa1a6019419278d3167da95ffe6b408ec1d`; `SUMMARY PASS` |
| deploy | `/srv/restaurant-pos/staging/evidence/phase-a0-1-pricing-policy-deploy-ed3e4cdbf38c4d8812620baf64cd42ce3a229431.txt`; SHA-256 `625b0de6a32ac37fdc53d172e5f5088929e3e28689dee847889dfd1239cc088e`; `DEPLOY PASS` |
| health | `/srv/restaurant-pos/staging/evidence/phase-a0-1-pricing-policy-health-ed3e4cdbf38c4d8812620baf64cd42ce3a229431.txt`; SHA-256 `e44460e11dc89de865dfcccf1e67a74d66f286786bb422c6ef8e84af6d683a14`; loopback frontend/backend health passed |
| automated validation | `/srv/restaurant-pos/staging/evidence/phase-a0-1-pricing-policy-validation-ed3e4cdbf38c4d8812620baf64cd42ce3a229431.txt`; SHA-256 `37529c24805df1d3c89959541e527c10e657b92218fc5f8bc123fe12b89398f0`; `SUMMARY PASS` |

## Staging runtime result

- Deployed Staging SHA:
  `ed3e4cdbf38c4d8812620baf64cd42ce3a229431`
- Services: backend/db/nginx all running.
- Flyway: 11 successful rows, numeric max version 11, failed rows 0.
- Latest Flyway row:
  `11 | 11 | V11__add_store_pricing_policies.sql | true`
- `store_pricing_policies`: one Store-scoped row in the current single-Store
  Staging database; required canonical columns present.
- Printing configuration retained: `printing_enabled=true`, `printing_mode=MOCK`.
- Logical printer topology retained: 4 enabled printers, 3 enabled assignments.

## Automated validation result

The automated Staging validation used the private Staging Owner test credential
file without printing credentials or tokens.

Validated:

- Owner login and authorization.
- Store Pricing Policy read, preview and update.
- Size Configuration API with canonical Small/Regular/Large only.
- Item Combo Policy API.
- Menu revision increment and `stores.menu_updated_at` update path through the
  same application transaction boundary.
- Catalog `pricing_policy` and content-hash change.
- IndexedDB cache invalidation contract through catalog hash/revision change.
- Rollback compatibility mirror for Size/Combo `menu_item_options.price_delta`.
- Generic Combo upcharge write rejection through the old option writer.
- Draft order snapshot pricing.
- Submitted order snapshot no-reprice after policy restore.
- MOCK printing job creation/rendering for the synthetic submitted order:
  2 jobs, `GRAB` and `FRONTDESK_RECEIPT`, both `PRINTED`, both rendered.
- Store isolation schema: one Store, one policy row, zero duplicate policy
  Stores.
- Production continuity: system/menu health `200/200`.

Validation summary:

```text
LOGIN|PASS
SIZE_CONFIGURATION_API|PASS
COMBO_POLICY_API|PASS
IMPACT_PREVIEW|PASS
PRICING_POLICY_UPDATE|PASS
MENU_REVISION_INCREMENT|PASS|before=111|after=114
CATALOG_HASH_CHANGE|PASS
INDEXEDDB_CACHE_CONTRACT|PASS|catalog_hash_changed
ROLLBACK_COMPATIBILITY_MIRROR|PASS
GENERIC_COMBO_WRITE_REJECTED|PASS|http=400
DRAFT_SNAPSHOT|PASS
SUBMITTED_ORDER|PASS|status=preparing
PRINTING_JOBS|PASS|count=2|types=FRONTDESK_RECEIPT,GRAB|statuses=PRINTED|rendered=2
RESTORE|PASS
RESTORED_POLICY_MATCH|PASS
SUBMITTED_SNAPSHOT_NO_REPRICE|PASS
STORE_ISOLATION_SCHEMA|PASS|stores=1|policies=1|duplicate_policy_stores=0
PRODUCTION_CONTINUITY|PASS|system=200|menu=200
SUMMARY|PASS
```

The validation temporarily changed one Staging item's Size/Combo configuration
and Store policy, submitted one synthetic Staging order, verified snapshots and
printing, then restored the item and policy values. Staging menu revision
advanced by design. Historical submitted-order, receipt, print and reporting
snapshot semantics remained snapshot-based and were not repriced by the later
policy restore.

## Boundaries retained

- No Production deploy, restart, Flyway action, configuration change, menu
  change, credential change, printer/Pad action, restore or business-data
  mutation occurred.
- No Staging downgrade, Flyway history edit, destructive reset, Flyway clean,
  Production clone, raw DB clone, Production secret copy, printer endpoint copy
  or device credential copy occurred.
- No Phase A1, Phase B, Phase C, Chinatown, Sainte-Catherine or Production
  promotion was started.

## Owner retest remains pending

The next Owner action is manual Staging retest for:

- canonical Size selection;
- Small/Regular/Large combinations;
- Pricing Rules;
- Large delta modification;
- Combo delta modification;
- impact preview;
- Pad revision refresh;
- actual ordering price.

Do not mark A0.1 Owner acceptance PASS until the Owner completes that retest.
