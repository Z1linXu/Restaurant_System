# Final Product Closeout — 2026-10-03

## Scope and evidence

PRIMARY_REPAIR_GOAL: finish the explicitly authorized Staging modifier/Add-on, daily Finish and sales presentation batch; provide a read-only Production rollout plan. EXPECTED_REPAIR_CLASS: HIGH. Production mutation is not authorized. Current execution status is owned by CURRENT_STATE.yml.

Baseline: origin/main `9c8d8887ec4ad12ac384e3e8a3ee80aec80d1e14`; Staging backend/frontend `500aacd991fddbc8f224174fc65db833e60d7848`, Flyway V33, APP_ENVIRONMENT=staging, UBER_EATS_ENVIRONMENT=sandbox, TZ=America/Toronto. Original user workspace remains untouched.

## B8E83 note provenance

Real Testing Get Order returned HTTP 200 at 2026-10-03T17:47:27Z for `5d1a2c47-7c2d-4965-9335-3e0f895b8e83` (inbox21/local88). Exact text `Please check order contents are correct` is in `cart.special_instructions` (39 characters), normalized `$.notes`. Its SHA-256 is `df34fc8f392aea0247efabbc335a5e8746f68c52d23ab80bb8c934a63d4c3c05`. The first item has an independent 29-character instruction, retained without publishing its contents. Modifier notes are empty. Eater keys contain no note/source author marker.

The database column named raw_order_snapshot_json actually stores the normalized UberOrderSnapshot, not the complete raw Get Order response. This audit read the current raw response in memory; no raw customer payload or token is published. The order-level note is joined into local item notes; item119 contains the combined 71-character note, items120–137 the order note, and raw fallback items138–139 retain it in external_kitchen_snapshot_json. Frozen jobs222/223 also contain it. Local order-level notes have no separate matching text.

B8E83_NOTE_SOURCE = cart.special_instructions → normalized.notes → local item notes / frozen raw kitchen notes → frozen print snapshots. AUTHOR_CLASSIFICATION = UNKNOWN. UBER_PLATFORM_BOILERPLATE_FILTER = NOT_APPLIED. No note/snapshot mutation.

Official [Get Order v2](https://developer.uber.com/docs/eats/references/api/v2/get-eats-order-orderid) describes cart.special_instructions as Eater preparation instructions. No official evidence marks this exact phrase as platform-generated; filtering based on wording alone could erase a customer instruction. Existing preservation behavior therefore remains, including mixed and local POS notes.

## Add-on conflicts: current-data audit

这个黄色区域保护旧 Add-on catalog 的身份与业务值：当前 option 缺 code，或同 code 名称/价格不一致时，暂不允许普通编辑覆盖。数据来自 StoreAddonService 对 Store1 当前 menu_item_options 与 store_addons 的比较，独立于 Uber stable-ID mapping。

Before: 3 conflict groups, 38 option rows. All extra_meat rows have price6.99 and Chinese 加肉; English Extra Beef/Extra Meat differs. Owner explicitly confirms Extra Meat identity. The bounded reconciliation normalizes the seven existing coded rows and repairs four noncompeting missing-code rows65/91/117/369, preserving IDs, prices, active state and all historical snapshots.

Remaining decisions: tea_egg (11 rows) 加蛋 vs 加卤蛋; missing-code16 rows: cabbage4 (no established canonical identity), broccoli4 (legacy1.20 vs canonical3.00), generic egg7 (tea vs fried unresolved), and inactive Extra Meat265 competing with active37 on the same item. No silent price change, deletion, reactivation or duplicate-eligibility decision.

| Code | Option ID | Local item ID / SKU | Chinese / English | Price | Active | Canonical candidate / decision |
| --- | ---: | --- | --- | ---: | --- | --- |
| Missing code | 40 | 17 / vegetable_chow_mein (素菜炒面) | 加包菜 / Extra Cabbage | 1.20 | false | NONE; no canonical cabbage add-on |
| Missing code | 41 | 17 / vegetable_chow_mein (素菜炒面) | 加西兰花 / Extra Broccoli | 1.20 | false | broccoli; price1.20 vs3.00 unresolved |
| Missing code | 65 | 16 / tomato_chow_mein (番茄炒面) | 加肉 / Extra Meat | 6.99 | false | extra_meat / 加肉 / Extra Meat — approved |
| Missing code | 66 | 16 / tomato_chow_mein (番茄炒面) | 加包菜 / Extra Cabbage | 1.20 | false | NONE; no canonical cabbage add-on |
| Missing code | 67 | 16 / tomato_chow_mein (番茄炒面) | 加西兰花 / Extra Broccoli | 1.20 | false | broccoli; price1.20 vs3.00 unresolved |
| Missing code | 91 | 15 / chicken_chow_mein (鸡肉炒面) | 加肉 / Extra Meat | 6.99 | false | extra_meat / 加肉 / Extra Meat — approved |
| Missing code | 92 | 15 / chicken_chow_mein (鸡肉炒面) | 加包菜 / Extra Cabbage | 1.20 | false | NONE; no canonical cabbage add-on |
| Missing code | 93 | 15 / chicken_chow_mein (鸡肉炒面) | 加西兰花 / Extra Broccoli | 1.20 | false | broccoli; price1.20 vs3.00 unresolved |
| Missing code | 117 | 14 / beef_chow_mein (牛肉炒面) | 加肉 / Extra Meat | 6.99 | false | extra_meat / 加肉 / Extra Meat — approved |
| Missing code | 118 | 14 / beef_chow_mein (牛肉炒面) | 加包菜 / Extra Cabbage | 1.20 | false | NONE; no canonical cabbage add-on |
| Missing code | 119 | 14 / beef_chow_mein (牛肉炒面) | 加西兰花 / Extra Broccoli | 1.20 | false | broccoli; price1.20 vs3.00 unresolved |
| Missing code | 143 | 4 / dan_dan_noodle (担担面) | 加蛋 / Extra Egg | 1.99 | false | tea_egg or fried_egg; unresolved |
| Missing code | 170 | 5 / zha_jiang_noodle (炸酱面) | 加蛋 / Extra Egg | 1.99 | false | tea_egg or fried_egg; unresolved |
| Missing code | 232 | 2 / braised_beef_tendon_noodle (红烧牛筋面) | 加蛋 / Extra Egg | 1.99 | false | tea_egg or fried_egg; unresolved |
| Missing code | 265 | 1 / traditional_beef_noodle (传统牛肉面) | 加肉 / Extra Meat | 6.99 | false | extra_meat; inactive duplicate relationship needs explicit decision |
| Missing code | 266 | 1 / traditional_beef_noodle (传统牛肉面) | 加蛋 / Extra Egg | 1.99 | false | tea_egg or fried_egg; unresolved |
| Missing code | 297 | 24 / cold_noodle_shredded_chicken (鸡丝凉面) | 加蛋 / Extra Egg | 1.99 | false | tea_egg or fried_egg; unresolved |
| Missing code | 331 | 26 / pickled_vegetable_beef_noodle (酸菜牛肉面) | 加蛋 / Extra Egg | 1.99 | false | tea_egg or fried_egg; unresolved |
| Missing code | 369 | 25 / braised_beef_noodle (红烧牛肉面) | 加肉 / Extra Meat | 6.99 | true | extra_meat / 加肉 / Extra Meat — approved |
| Missing code | 370 | 25 / braised_beef_noodle (红烧牛肉面) | 加蛋 / Extra Egg | 1.99 | true | tea_egg or fried_egg; unresolved |
| extra_meat | 37 | 1 / traditional_beef_noodle (传统牛肉面) | 加肉 / Extra Beef | 6.99 | true | extra_meat / 加肉 / Extra Meat — approved |
| extra_meat | 154 | 4 / dan_dan_noodle (担担面) | 加肉 / Extra Meat | 6.99 | true | extra_meat / 加肉 / Extra Meat — approved |
| extra_meat | 183 | 5 / zha_jiang_noodle (炸酱面) | 加肉 / Extra Meat | 6.99 | true | extra_meat / 加肉 / Extra Meat — approved |
| extra_meat | 212 | 3 / vegetable_noodle (蔬菜面) | 加肉 / Extra Beef | 6.99 | true | extra_meat / 加肉 / Extra Meat — approved |
| extra_meat | 247 | 2 / braised_beef_tendon_noodle (红烧牛筋面) | 加肉 / Extra Beef | 6.99 | true | extra_meat / 加肉 / Extra Meat — approved |
| extra_meat | 308 | 24 / cold_noodle_shredded_chicken (鸡丝凉面) | 加肉 / Extra Meat | 6.99 | true | extra_meat / 加肉 / Extra Meat — approved |
| extra_meat | 346 | 26 / pickled_vegetable_beef_noodle (酸菜牛肉面) | 加肉 / Extra Beef | 6.99 | true | extra_meat / 加肉 / Extra Meat — approved |
| tea_egg | 36 | 1 / traditional_beef_noodle (传统牛肉面) | 加蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 50 | 17 / vegetable_chow_mein (素菜炒面) | 加卤蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 76 | 16 / tomato_chow_mein (番茄炒面) | 加卤蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 102 | 15 / chicken_chow_mein (鸡肉炒面) | 加卤蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 128 | 14 / beef_chow_mein (牛肉炒面) | 加卤蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 155 | 4 / dan_dan_noodle (担担面) | 加蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 184 | 5 / zha_jiang_noodle (炸酱面) | 加蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 213 | 3 / vegetable_noodle (蔬菜面) | 加蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 248 | 2 / braised_beef_tendon_noodle (红烧牛筋面) | 加蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 309 | 24 / cold_noodle_shredded_chicken (鸡丝凉面) | 加蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |
| tea_egg | 347 | 26 / pickled_vegetable_beef_noodle (酸菜牛肉面) | 加蛋 / Extra Tea Egg | 1.99 | true | tea_egg; confirm canonical Chinese label/semantics |

## Authorized modifier batch

Exact manifest: [approved-modifiers.json](../../deployment/cloud/final-closeout-20261003/approved-modifiers.json). 25 explicit contexts: extra_meat5, bok_choy9 across both source IDs, extra_noodle9 across both IDs, soup_beef1 and soup_vegan1 on Noodle with Vegetables only. Mapping is semantic, never a financial price-equality gate. Existing41 roots, 16NO_OP and205modifier MAP preserved. Bounded configuration transaction avoids the normal PUT mapping remap side effect; no historical held-order replay or physical print.

Remaining unmapped12 contexts: Add Fried Egg43731428-8827-403a-b401-246ff779bef0 and Add Tea Boil Egg316bb70f-f2a4-4b44-9b7a-7ad73c5243d9, each on four noncombo Chow Mein roots; Non Coriander54c53703-db68-4044-85a3-5f4962715566 and Non Scallion/Green Oniond1f4a19b-fd7c-4cdf-863a-581391172f23, each on Vegetable Noodle and its Combo. RAW fallback remains.

## Implementation acceptance

- Full backend verify: 891 tests, 0 failures/errors, 7 skipped optional existing environment suites; real PostgreSQL Uber48/48 and DailyClose8/8 passed, Flyway V1–V34 on fresh isolated database.
- Frontend: 46 files / 242 tests passed, production build and targeted eslint passed.
- Note filtering is intentionally not implemented; source audit establishes preservation as the safe outcome.
- Independent Agent6 final ACCEPT: P0/P1/P2=0. Review repaired cancelled-line revenue/quantity/cost inclusion, stale OSIV entities after locking Finish/Cancel, and transactional root identity drift validation. One test compilation retry fixed a package name shadowed by the existing org fixture variable.
- At 2026-10-03T17:53:20Z Staging data batch committed and independently read back: 41 ITEM MAP +230 MODIFIER MAP +16 MODIFIER NO_OP =287. All25 target contexts resolved against actual runtime API; order+item notes preserved. Add-on conflicts3→2 (27 remaining rows). Backup archive1,153,614bytes, private server state directory; archive validation PASS.
- Orders/print-job counts, held-order hashes, historical-item hashes and binding hash unchanged. All application/DB containers unchanged. Docker inspect Mounts order varies for Production nginx; enumerating unchanged mount orderings reproduced the initial fingerprint, with same container ID/started-at/image and zero restarts.
- PR259 merged/deployed exact `fa51366625e770e5c331e30115c9371b6abac61f`; public HTTPS health/login/Store workspace/Frontdesk/printing routes/Uber Today passed. Dashboard Today13/Week7/Month31 buckets, classification totals and percentage sums passed. FlywayV34 successful; Production fingerprint/ledger continuity PASS.
- Existing report rebuild API refreshed289 exact Store/date pairs across Staging Stores1/18/21/22/23/24/25/26/27. SQL read-back compared every net_sales against eligible submitted-date total: zero mismatches. Derived summaries only; no order mutation.
- Browser Desktop and1024px Pad show new panels and removed Recent/Active/status panels. Pad inspection caught an existing long Store-name min-content overflow (page1085px at viewport1024); a three-class wrap/shrink follow-up is under final acceptance. Backend source remains identical to the891-test artifact.
- Docker before64%/21GBfree, buildcache14.75GB; after first deployment64%/21GBfree,14.8GB cache. Cache growth is small; no prune or release/volume/database cleanup. Active/rollback images and runtime artifacts preserved.
