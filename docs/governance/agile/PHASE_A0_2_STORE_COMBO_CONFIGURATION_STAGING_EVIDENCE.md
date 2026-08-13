# Phase A0.2 Store Combo Configuration Staging Evidence

## Authority and boundary

- Date: 2026-08-13
- Loop: `PHASE_A0_2_STORE_COMBO_CONFIGURATION`
- Owner approval: Staging-only exact-SHA deployment and automated A0.2 smoke
- Production boundary: `NO MUTATION`
- Staging target before deploy: `ed3e4cdbf38c4d8812620baf64cd42ce3a229431`, Flyway V11
- Implementation PR: #134
- Implementation merge SHA: `90ac0cb0496161b12c47cff00573b56b4abc961c`
- Deployed Staging SHA: `90ac0cb0496161b12c47cff00573b56b4abc961c`

## Deployment evidence

Server-retained evidence paths:

```text
/srv/restaurant-pos/staging/evidence/phase-a0-2-store-combo-repo-import-90ac0cb0496161b12c47cff00573b56b4abc961c.txt
/srv/restaurant-pos/staging/evidence/phase-a0-2-store-combo-release-env-90ac0cb0496161b12c47cff00573b56b4abc961c.txt
/srv/restaurant-pos/staging/evidence/phase-a0-2-store-combo-preflight-90ac0cb0496161b12c47cff00573b56b4abc961c.txt
/srv/restaurant-pos/staging/evidence/phase-a0-2-store-combo-deploy-90ac0cb0496161b12c47cff00573b56b4abc961c.txt
/srv/restaurant-pos/staging/evidence/phase-a0-2-store-combo-health-90ac0cb0496161b12c47cff00573b56b4abc961c.txt
/srv/restaurant-pos/staging/evidence/phase-a0-2-store-combo-validation-90ac0cb0496161b12c47cff00573b56b4abc961c.txt
```

Evidence fingerprints:

```text
repo-import sha256 = a6105de3ec0659b602972036941b561611f8ca4c3ffa4eb524d81752bf28339c
release-env sha256 = df0ea0de8f40a1702bb014955571a9981a5f987013f17ccf5a70aa4bce1d622e
preflight sha256 = 480f4620bd89627197313361837ff598506e837da8a8934166f52e48f01a43f6
deploy sha256 = d1a7aaf17639c3bbc65fbc3d63c803e056aae66b556eca992f6357c1f23cca3f
health sha256 = 36f9d4a542e4a22e1ed391285903eac8934bc65d1741e1a5d6c1e8a8c8f80657
validation sha256 = 8f1f85172db0b869c6a89de6eee628e72d4a60e9958cf94a429b2ed203ecc512
```

Deployment result:

```text
STAGING_COMMIT_SHA = 90ac0cb0496161b12c47cff00573b56b4abc961c
Flyway = V12
Flyway count/max installed rank/max version/failed = 12|12|12|0
Flyway latest row = 12|12|V12__add_store_combo_components.sql|true
failed migrations = 0
frontend root = 200
backend health = 200
menu health = 200
WebSocket SockJS info = 200
Printing feature = enabled
print mode = MOCK
logical printers = 4
logical assignments = 3
```

Production continuity was checked read-only after Staging validation:

```text
Production system health = 200
Production menu health = 200
Production deploy/restart/Flyway/config = NOT PERFORMED
```

## Automated Staging smoke result

The retained validation evidence ends in:

```text
SUMMARY|PASS
```

Validated behavior:

- Combo Configuration API read passed.
- Unauthenticated Combo Configuration access returned 401.
- Owner Store authorization is enforced; wrong-Store mutation returned 403 and
  did not alter the current Store.
- Staging retained MOCK printing with four enabled logical printers and three
  enabled assignments.
- `store_pricing_policies.combo_delta` remained the Combo price source at
  `5.00`; legacy component option rows did not become a second price source.
- Initial catalog exposed Tea Egg, Fried Egg, Edamame, Shredded Potato and
  Cucumber Salad.
- Existing draft snapshot stayed unchanged when Fried Egg was disabled.
- Disabling Fried Egg incremented `stores.menu_revision`, changed the catalog
  hash and removed Fried Egg from new order choices.
- New order submission with disabled Fried Egg was rejected with
  `COMBO_COMPONENT_DISABLED`.
- Re-enabling Fried Egg incremented the revision and restored it in the catalog.
- Combo Side enablement changed the revision and catalog.
- Submitted synthetic order retained its snapshot and was not repriced.
- Printing created and completed three MOCK-rendered jobs:
  `FRONTDESK_RECEIPT`, `GRAB`, and `HOT_KITCHEN`.
- Kitchen routing produced a combo-side task and retained the remove-side
  instruction.
- Final restoration returned the reviewed St-Denis Store Combo configuration:

```text
COMBO_EGG | combo_tea_egg | enabled | display_order 10
COMBO_EGG | combo_fried_egg | enabled | display_order 20
COMBO_SIDE | combo_edamame | enabled | display_order 10
COMBO_SIDE | combo_shredded_potato | enabled | display_order 20
COMBO_SIDE | combo_cucumber_salad | enabled | display_order 30
```

## Classification

```text
SCHEMA = PASS
V12 = APPLIED
COMBO_CONFIGURATION_API = PASS
COMBO_EGG = PASS
COMBO_SIDE = PASS
COMBO_PRICE_SOURCE = PASS
MENU_REVISION = PASS
INDEXEDDB_CACHE_CONTRACT = PASS
DRAFT_SNAPSHOT = PASS
SUBMITTED_SNAPSHOT = PASS
PRINTING = PASS_MOCK
KITCHEN_ROUTING = PASS
STORE_ISOLATION = PASS
AUTHORIZATION = PASS
PRODUCTION_CONTINUITY = PASS_READ_ONLY
PROHIBITED_DATA = NOT_READ_OR_COPIED
```

## Owner retest gate

Automated validation is complete. The Owner completed manual Staging retest:

```text
OWNER_A0_2_MANUAL_STAGING_RETEST = PASS
```

Accepted manual coverage included:

- Store Combo Configuration UI.
- Tea Egg and Fried Egg enable/disable behavior.
- Side enable/disable behavior.
- Pad catalog revision refresh / IndexedDB cache update.
- Actual ordering price from Pricing Rules.
- GRAB, Frontdesk Receipt and Hot Kitchen ticket readability.
- Store isolation expectations from the Owner workflow.

Historical A0.2 closure:

```text
PHASE_A0_2_STORE_COMBO_CONFIGURATION_DEPLOYED_TO_STAGING_WAITING_FOR_OWNER_RETEST
```
