# Uber Test Store mapping diff — 2026-10-01

Snapshot: 2026-10-01T17:15:56Z. TEST Store `bd993244-5589-4b19-8f0d-dc2ba73d4273` → local Store 1 / org 1.

Root count **41**; re-reading the earlier snapshot confirms its reported 31 was a counting omission, not a menu change. There are 74 API item objects, 11 groups, 33 unique modifiers. Local catalog revision 215, menu-catalog-v4, content hash `fnv1a32:562b32ff`. Uber returned no menu version, ETag or Last-Modified. All external_data values are blank/missing; external_id is absent.

**HIGH = 0. No mapping writes authorized by the supplied stable-identity criterion are possible.** Display-name candidates below remain REVIEW_REQUIRED even where names exactly match. NO_MATCH means no reliable candidate in the current catalog. No name-derived guess is saved. Combo-root suggestions require explicit combo semantics confirmation, not just an ITEM link.

## ITEM MAPPING

| Uber title | Uber stable ID | external_data | Local candidate ID / SKU / Chinese | Confidence |
|---|---|---|---|---|
| Beef Lanzhou Noodles Special Combo | eca7fbf3-a666-454b-b36e-7d380e80b49d | EMPTY / MISSING | 1 / traditional_beef_noodle / 传统牛肉面 (combo behavior unresolved) | REVIEW_REQUIRED |
| Braised Beef Tendon in Brown Sauce with Noodles | Braised_Beef_Tendon_ | EMPTY / MISSING | 2 / braised_beef_tendon_noodle / 红烧牛筋面 | REVIEW_REQUIRED |
| Chicken Chow Mein | d2a77967-74f3-4dfb-9e8e-a236369bb4f6 | EMPTY / MISSING | 15 / chicken_chow_mein / 鸡肉炒面 | REVIEW_REQUIRED |
| Chicken Chow Mein Combo | c169f422-a9cc-4758-82a9-0f60eef89b68 | EMPTY / MISSING | 15 / chicken_chow_mein / 鸡肉炒面 (combo behavior unresolved) | REVIEW_REQUIRED |
| Chinese Herbal Tea | 745a90de-3e4b-444e-8de4-fa4cefc63eb0 | EMPTY / MISSING | 13 / chinese_herbal_tea / 王老吉 | REVIEW_REQUIRED |
| Coke | 4c7162f6-1a21-4545-8eb5-6c49b01af0fd | EMPTY / MISSING | 10 / coke / 可乐 | REVIEW_REQUIRED |
| Crispy Tempura Shrimp (4pcs) | Crispy_Tempura_Shrim | EMPTY / MISSING | 20 / tempura_shrimp / 炸虾 | REVIEW_REQUIRED |
| Cucumber Mix With Home Made Spicy Sauce | 0cbf47bd-5d4c-456a-900e-7da7f6f8f54b | EMPTY / MISSING | 7 / cucumber_salad / 拌黄瓜 | REVIEW_REQUIRED |
| Dandan Noodle Combo (With Peanuts) | 87bbecca-85fa-4681-a3dd-06c8bd97d70a | EMPTY / MISSING | 4 / dan_dan_noodle / 担担面 (combo behavior unresolved) | REVIEW_REQUIRED |
| Dandan Noodles (With Peanuts) | 2be2f7be-aa39-4742-bf91-edc9759f19c0 | EMPTY / MISSING | 4 / dan_dan_noodle / 担担面 | REVIEW_REQUIRED |
| Diet Coke | 8c3c9093-c5fb-4d6d-ac0c-140451e9f29a | EMPTY / MISSING | 11 / diet_coke / 健怡可乐 | REVIEW_REQUIRED |
| Edamame With Preserved Vegetable | 2155b250-58f2-49d2-a971-d149d8a61386 | EMPTY / MISSING | 8 / edamame / 毛豆 | REVIEW_REQUIRED |
| Fried Chinese Steamed buns (3pcs) | Fried_Chinese_Steame | EMPTY / MISSING | 21 / fried_steamed_buns / 炸馒头 | REVIEW_REQUIRED |
| Fried Wontons (6 pcs) | Fried_Wontons_(6_pcs | EMPTY / MISSING | 22 / fried_wontons / 炸馄饨 | REVIEW_REQUIRED |
| Ginger Ale | cbcd1dcd-87bb-4bd3-aa95-147817e134c8 | EMPTY / MISSING | — | NO_MATCH |
| Green Grape Soju | Green_Grape_Soju | EMPTY / MISSING | — | NO_MATCH |
| Hakutsuru Junmai Ginjo Sake | Hakutsuru_Junmai_Gin | EMPTY / MISSING | — | NO_MATCH |
| Homemade Lanzhou Beef (With Peanuts) | 71020dd7-1dbc-4711-9852-dcd1191d5eaa | EMPTY / MISSING | 6 / braised_beef_shank_salad / 拌牛展 | REVIEW_REQUIRED |
| Ice Tea | 7aad8f91-c26d-4c1d-8609-341f83396317 | EMPTY / MISSING | 12 / ice_tea / 冰红茶 | REVIEW_REQUIRED |
| Lanzhou Beef Chow Mein (Beef) | 9c8260ac-717a-4d20-80b5-aeef17613186 | EMPTY / MISSING | 14 / beef_chow_mein / 牛肉炒面 | REVIEW_REQUIRED |
| Lanzhou Beef Chow Mein Combo | b3f90251-5066-42c4-bb4c-9a1581a38936 | EMPTY / MISSING | 14 / beef_chow_mein / 牛肉炒面 (combo behavior unresolved) | REVIEW_REQUIRED |
| Lychee Soju | Good_Day_Lychee_Soju | EMPTY / MISSING | — | NO_MATCH |
| Melon Soju | Melon_Soju | EMPTY / MISSING | — | NO_MATCH |
| Noodle with Vegetables | 3b8b2764-d492-47e0-9977-107ed4e67d1f | EMPTY / MISSING | 3 / vegetable_noodle / 蔬菜面 | REVIEW_REQUIRED |
| Noodles with Vegetables Combo | e72231fb-9029-4539-91d6-b6efbeeb3767 | EMPTY / MISSING | 3 / vegetable_noodle / 蔬菜面 (combo behavior unresolved) | REVIEW_REQUIRED |
| Original Soju | Original_Soju | EMPTY / MISSING | 37 / soju / 原味烧酒 | REVIEW_REQUIRED |
| Peach Soju | Peach_Soju | EMPTY / MISSING | — | NO_MATCH |
| Rouleaux de printemps frits (3 pcs) | Rouleaux_de_printemp | EMPTY / MISSING | 19 / fried_spring_rolls / 炸春卷 | REVIEW_REQUIRED |
| Sapporo Beer | Sapporo_Beer | EMPTY / MISSING | 35 / sapporo / Sapporo | REVIEW_REQUIRED |
| Sayuri Nigori Sake | Nigori_Sake | EMPTY / MISSING | — | NO_MATCH |
| Sprite | 79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2 | EMPTY / MISSING | — | NO_MATCH |
| Stir-fried Tomato Beef Noodle (Beef) | b11777c1-9d50-44fe-8397-040a5056152b | EMPTY / MISSING | 16 / tomato_chow_mein / 番茄炒面 | REVIEW_REQUIRED |
| Stir-fried Tomato Beef Noodle Combo | e45ddd3e-dbf5-449d-8ab6-badbccd4897b | EMPTY / MISSING | 16 / tomato_chow_mein / 番茄炒面 (combo behavior unresolved) | REVIEW_REQUIRED |
| Sweet & Sour Mini Fries | 540c3a0b-3830-4bc6-b706-14638c7e6352 | EMPTY / MISSING | — | NO_MATCH |
| Tea Corned Egg | e8e7be55-3139-4949-8a7e-69a1d52161c5 | EMPTY / MISSING | 27 / tea_egg / 茶叶蛋 | REVIEW_REQUIRED |
| Traditional Lanzhou Hand-pull Beef Noodle | b940caa7-6e37-4b3b-b964-3e10151e7903 | EMPTY / MISSING | 1 / traditional_beef_noodle / 传统牛肉面 | REVIEW_REQUIRED |
| Tsingtao Beer | Tsingtao_Beer | EMPTY / MISSING | 34 / tsingtao_beer / 青岛啤酒 | REVIEW_REQUIRED |
| Vegetable Chow Mein | 842d5987-6945-42ef-a1d9-ed15cad18a0b | EMPTY / MISSING | 17 / vegetable_chow_mein / 素菜炒面 | REVIEW_REQUIRED |
| Vegetable Chow Mein Combo | 78b60d9c-29fc-4064-9b10-10dd11427df5 | EMPTY / MISSING | 17 / vegetable_chow_mein / 素菜炒面 (combo behavior unresolved) | REVIEW_REQUIRED |
| Zha Jiang Noodle | 41989752-4e09-4fc5-8d22-96e9427173eb | EMPTY / MISSING | 5 / zha_jiang_noodle / 炸酱面 | REVIEW_REQUIRED |
| Zhajiang Noodles Combo | e8188b13-132d-48af-a981-a3dd1476ee01 | EMPTY / MISSING | 5 / zha_jiang_noodle / 炸酱面 (combo behavior unresolved) | REVIEW_REQUIRED |

## MODIFIER MAPPING

These are candidate codes only. Final rules must also name the root Uber item and local item; shared modifier ID alone is insufficient. Parent-dependent combo choices are deliberately not inferred.

| Uber title | Uber stable ID | Local candidate code / group / Chinese | Parent | Confidence |
|---|---|---|---|---|
| Add Fried Egg | 43731428-8827-403a-b401-246ff779bef0 | fried_egg / ADD_ON / 加煎蛋 | none for candidate | REVIEW_REQUIRED |
| Add Tea Boil Egg | 316bb70f-f2a4-4b44-9b7a-7ad73c5243d9 | tea_egg / ADD_ON / 加蛋 | none for candidate | REVIEW_REQUIRED |
| Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | noodle_extra_wide / NOODLE_TYPE / 大宽 | none for candidate | REVIEW_REQUIRED |
| Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | — | unresolved | NO_MATCH |
| Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | — | unresolved | NO_MATCH |
| Extra Fried Egg | 329049e9-f054-4f0a-ad82-ccbd189c262d | fried_egg / ADD_ON / 加煎蛋 | none for candidate | REVIEW_REQUIRED |
| Extra Fried Egg | 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | fried_egg / ADD_ON / 加煎蛋 | none for candidate | REVIEW_REQUIRED |
| Extra Meat | 524842f3-7e5d-441f-813b-895f0491179f | extra_meat / ADD_ON / 加肉 | none for candidate | REVIEW_REQUIRED |
| Extra Noodle | 2ad33578-e7c6-4fa3-ac38-f4dff54a7eae | extra_noodle / ADD_ON / 加面 | none for candidate | REVIEW_REQUIRED |
| Extra Noodle | dddc88d0-5f52-4266-af87-ea7477d63f30 | extra_noodle / ADD_ON / 加面 | none for candidate | REVIEW_REQUIRED |
| Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | spicy_extra / SPICY_LEVEL / 加辣 | none for candidate | REVIEW_REQUIRED |
| Extra Tea Boil Egg | 3e400404-e7da-4401-8f23-b91d74d7986e | tea_egg / ADD_ON / 加蛋 | none for candidate | REVIEW_REQUIRED |
| Extra Tea Boil Egg | cd3b4c00-5065-47ac-a8e8-a2772067ca16 | tea_egg / ADD_ON / 加蛋 | none for candidate | REVIEW_REQUIRED |
| Extra Vegetable | 2ca784f1-b086-42bb-aee0-64840d75cacf | — | unresolved | NO_MATCH |
| Extra Vegetable | a79813fd-2d14-45bd-a57c-462a8ddbb70f | — | unresolved | NO_MATCH |
| Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | noodle_thin / NOODLE_TYPE / 细 | none for candidate | REVIEW_REQUIRED |
| Fried Egg | 8d6efb88-7974-4973-a681-263b7f186959 | — | unresolved | NO_MATCH |
| Large | 01c44198-eeb9-4452-bc2b-0939bf713a39 | size_large / SIZE / 大碗 | none for candidate | REVIEW_REQUIRED |
| Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | noodle_leek_leaf / NOODLE_TYPE / 韭叶 | none for candidate | REVIEW_REQUIRED |
| Meat Broth | 133d15f2-124d-43c6-9b2c-15352e25926b | — | unresolved | NO_MATCH |
| Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | spicy_mild / SPICY_LEVEL / 少辣 | none for candidate | REVIEW_REQUIRED |
| Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | remove_cilantro / REMOVE / 走香菜 | none for candidate | REVIEW_REQUIRED |
| Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | remove_green_onion / REMOVE / 走葱 | none for candidate | REVIEW_REQUIRED |
| Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | spicy_none / SPICY_LEVEL / 不辣 | none for candidate | REVIEW_REQUIRED |
| One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | — | unresolved | NO_MATCH |
| Regular | 94b90aa8-a673-4793-b7bd-917c837716df | size_regular / SIZE / 中碗 | none for candidate | REVIEW_REQUIRED |
| Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | spicy_regular / SPICY_LEVEL / 正常辣 | none for candidate | REVIEW_REQUIRED |
| Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | — | unresolved | NO_MATCH |
| Tea Boil Egg | 61ad7ec7-2a69-4781-9210-b71c75084bad | — | unresolved | NO_MATCH |
| Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | noodle_sanxi / NOODLE_TYPE / 三细 | none for candidate | REVIEW_REQUIRED |
| Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | noodle_erxi / NOODLE_TYPE / 二细 | none for candidate | REVIEW_REQUIRED |
| Vegan Broth | 182b7ed4-d3c7-4aeb-9516-aa0c3959de97 | — | unresolved | NO_MATCH |
| Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | noodle_wide / NOODLE_TYPE / 宽 | none for candidate | REVIEW_REQUIRED |

## First-order candidate — NOT READY

Uber `b940caa7-6e37-4b3b-b964-3e10151e7903` Traditional Lanzhou Hand-pull Beef Noodle → local `1 / traditional_beef_noodle / 传统牛肉面` is REVIEW_REQUIRED, not a confirmed mapping.

| Required group | min / max | Options |
|---|---|---|
| Spicy Level | 1 / 1 | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy |
| Size | 1 / 1 | Regular, Large |
| Noodle Size Option | 1 / 1 | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide |
| Extra Topping | 0 / 999 | Extra Meat, Extra Fried Egg, Extra Tea Boil Egg, Extra Vegetable, Extra Noodle |
| Ingredients Option | 0 / 2 | Non Scallion/ Green Onion, Non Coriander |

Proposed review combination: main item above + Large + Extra Fried Egg + Non Coriander + Non-spicy + Three Fine. The last two selections satisfy required groups, but their identities still need confirmation. This is **not an instruction to place an order**.

| Uber stable ID | Proposed local identity | Confidence |
|---|---|---|
| b940caa7-6e37-4b3b-b964-3e10151e7903 | item 1 / traditional_beef_noodle / 传统牛肉面 | REVIEW_REQUIRED |
| 01c44198-eeb9-4452-bc2b-0939bf713a39 | item 1 option 286 / size_large / SIZE / 大碗 | REVIEW_REQUIRED |
| 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | item 1 option 281 / fried_egg / ADD_ON / 加煎蛋 | REVIEW_REQUIRED |
| 54c53703-db68-4044-85a3-5f4962715566 | item 1 option 275 / remove_cilantro / REMOVE / 走香菜 | REVIEW_REQUIRED |
| fa1544e9-4e8e-4a4f-81a1-8643e200028e | item 1 option 285 / spicy_none / SPICY_LEVEL / 不辣 | REVIEW_REQUIRED |
| d9750fe4-14fe-4efd-bcd6-8ea85774850d | item 1 option 3 / noodle_sanxi / NOODLE_TYPE / 三细 | REVIEW_REQUIRED |

Each modifier proposal is explicitly scoped to root `b940caa7-6e37-4b3b-b964-3e10151e7903` and local item 1, with no parent option. These are proposed associations requiring identity confirmation, not existing or automatically saved rules.

Items: {'REVIEW_REQUIRED': 32, 'NO_MATCH': 9}. Unique modifiers: {'REVIEW_REQUIRED': 23, 'NO_MATCH': 10}. Root/modifier contexts: 258; mapped 0.

No HIGH item exists, so TEST_COMBINATION_FULLY_MAPPED = NO. No menu upload, external_data mutation, order or decision was performed.
