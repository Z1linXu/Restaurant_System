# Uber Kitchen Mapping 最终执行表 — 2026-10-02

状态：最新人工确认覆盖全部41个root；41/41已写入Staging并独立读库验证，Human Confirmed=YES、Persisted=YES。Modifier的已有确认范围保持不变。当前证据见[41-root读回表](UBER_ROOT_MAPPING_STATUS_20261002.md)。本文件后部旧时间戳段落仅保留历史证据，不代表当前root数量。

范围：sandbox Test Store `bd993244-5589-4b19-8f0d-dc2ba73d4273` → Store1/org1/STG005_SRC_20260809_R01。2026-10-03T03:50:59Z读回：262条规则=41 ITEM MAP+205 MODIFIER MAP+16 MODIFIER NO_OP。

## 最终人工确认摘要

- 所有原明确candidate正式获批；最后7项也已确认：Sprite→seven_up；Hakutsuru→lg_sake；Sayuri→sm_sake；Green Grape/Lychee/Melon/Peach Soju→shochu_fruit。
- 41个root全部MAP；8个Combo全部COMBO_ROOT→主菜+隐式combo；已有COMBO_EGG/COMBO_SIDE规则保留。
- 四种炒面及Combo去葱/香菜仍为原parent context限定的NO_OP / INGREDIENT_NOT_USED。
- 本轮只扩展root确认，不自动确认Add Fried Egg、Add Tea Boil Egg、其他未确认modifier。
- RAW_UBER_FALLBACK保留为未来root菜单漂移兜底；不改变历史冻结票据。本轮无订单/打印/E2E测试或Production修改。

## Parent context 索引

| Root ref | Uber root name | Stable ID | Local candidate / confirmed identity | Identity confirmed |
| --- | --- | --- | --- | --- |
| R01 | Beef Lanzhou Noodles Special Combo | eca7fbf3-a666-454b-b36e-7d380e80b49d | traditional_beef_noodle | YES |
| R02 | Braised Beef Tendon in Brown Sauce with Noodles | Braised_Beef_Tendon_ | braised_beef_tendon_noodle | YES |
| R03 | Chicken Chow Mein | d2a77967-74f3-4dfb-9e8e-a236369bb4f6 | chicken_chow_mein | YES |
| R04 | Chicken Chow Mein Combo | c169f422-a9cc-4758-82a9-0f60eef89b68 | chicken_chow_mein | YES |
| R05 | Chinese Herbal Tea | 745a90de-3e4b-444e-8de4-fa4cefc63eb0 | chinese_herbal_tea | YES |
| R06 | Coke | 4c7162f6-1a21-4545-8eb5-6c49b01af0fd | coke | YES |
| R07 | Crispy Tempura Shrimp (4pcs) | Crispy_Tempura_Shrim | tempura_shrimp | YES |
| R08 | Cucumber Mix With Home Made Spicy Sauce | 0cbf47bd-5d4c-456a-900e-7da7f6f8f54b | cucumber_salad | YES |
| R09 | Dandan Noodle Combo (With Peanuts) | 87bbecca-85fa-4681-a3dd-06c8bd97d70a | dan_dan_noodle | YES |
| R10 | Dandan Noodles (With Peanuts) | 2be2f7be-aa39-4742-bf91-edc9759f19c0 | dan_dan_noodle | YES |
| R11 | Diet Coke | 8c3c9093-c5fb-4d6d-ac0c-140451e9f29a | diet_coke | YES |
| R12 | Edamame With Preserved Vegetable | 2155b250-58f2-49d2-a971-d149d8a61386 | edamame | YES |
| R13 | Fried Chinese Steamed buns (3pcs) | Fried_Chinese_Steame | fried_steamed_buns | YES |
| R14 | Fried Wontons (6 pcs) | Fried_Wontons_(6_pcs | fried_wontons | YES |
| R15 | Ginger Ale | cbcd1dcd-87bb-4bd3-aa95-147817e134c8 | canada_dry | YES |
| R16 | Green Grape Soju | Green_Grape_Soju | shochu_fruit | YES |
| R17 | Hakutsuru Junmai Ginjo Sake | Hakutsuru_Junmai_Gin | lg_sake | YES |
| R18 | Homemade Lanzhou Beef (With Peanuts) | 71020dd7-1dbc-4711-9852-dcd1191d5eaa | braised_beef_shank_salad | YES |
| R19 | Ice Tea | 7aad8f91-c26d-4c1d-8609-341f83396317 | ice_tea | YES |
| R20 | Lanzhou Beef Chow Mein (Beef) | 9c8260ac-717a-4d20-80b5-aeef17613186 | beef_chow_mein | YES |
| R21 | Lanzhou Beef Chow Mein Combo | b3f90251-5066-42c4-bb4c-9a1581a38936 | beef_chow_mein | YES |
| R22 | Lychee Soju | Good_Day_Lychee_Soju | shochu_fruit | YES |
| R23 | Melon Soju | Melon_Soju | shochu_fruit | YES |
| R24 | Noodle with Vegetables | 3b8b2764-d492-47e0-9977-107ed4e67d1f | vegetable_noodle | YES |
| R25 | Noodles with Vegetables Combo | e72231fb-9029-4539-91d6-b6efbeeb3767 | vegetable_noodle | YES |
| R26 | Original Soju | Original_Soju | soju | YES |
| R27 | Peach Soju | Peach_Soju | shochu_fruit | YES |
| R28 | Rouleaux de printemps frits (3 pcs) | Rouleaux_de_printemp | fried_spring_rolls | YES |
| R29 | Sapporo Beer | Sapporo_Beer | sapporo | YES |
| R30 | Sayuri Nigori Sake | Nigori_Sake | sm_sake | YES |
| R31 | Sprite | 79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2 | seven_up | YES |
| R32 | Stir-fried Tomato Beef Noodle (Beef) | b11777c1-9d50-44fe-8397-040a5056152b | tomato_chow_mein | YES |
| R33 | Stir-fried Tomato Beef Noodle Combo | e45ddd3e-dbf5-449d-8ab6-badbccd4897b | tomato_chow_mein | YES |
| R34 | Sweet & Sour Mini Fries | 540c3a0b-3830-4bc6-b706-14638c7e6352 | shredded_potato | YES |
| R35 | Tea Corned Egg | e8e7be55-3139-4949-8a7e-69a1d52161c5 | tea_egg | YES |
| R36 | Traditional Lanzhou Hand-pull Beef Noodle | b940caa7-6e37-4b3b-b964-3e10151e7903 | traditional_beef_noodle | YES |
| R37 | Tsingtao Beer | Tsingtao_Beer | tsingtao_beer | YES |
| R38 | Vegetable Chow Mein | 842d5987-6945-42ef-a1d9-ed15cad18a0b | vegetable_chow_mein | YES |
| R39 | Vegetable Chow Mein Combo | 78b60d9c-29fc-4064-9b10-10dd11427df5 | vegetable_chow_mein | YES |
| R40 | Zha Jiang Noodle | 41989752-4e09-4fc5-8d22-96e9427173eb | zha_jiang_noodle | YES |
| R41 | Zhajiang Noodles Combo | e8188b13-132d-48af-a981-a3dd1476ee01 | zha_jiang_noodle | YES |

R编号仅是本报告的简写，持久化必须展开为完整root ID；禁止将R编号保存为Uber ID。相同modifier稳定ID合并展示，只有MAP/NO_OP范围不同才拆行。

## 完整最终表

| Uber Type | Uber Name | Uber Stable ID | Parent Uber Item / Group | Action | Local Item SKU | Local Option Code | Local Option Group | Combo Mapping Mode | Reason | Human Confirmed | Persisted | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ITEM | Beef Lanzhou Noodles Special Combo | eca7fbf3-a666-454b-b36e-7d380e80b49d | R01 | MAP | traditional_beef_noodle | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Braised Beef Tendon in Brown Sauce with Noodles | Braised_Beef_Tendon_ | R02 | MAP | braised_beef_tendon_noodle | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Chicken Chow Mein | d2a77967-74f3-4dfb-9e8e-a236369bb4f6 | R03 | MAP | chicken_chow_mein | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Chicken Chow Mein Combo | c169f422-a9cc-4758-82a9-0f60eef89b68 | R04 | MAP | chicken_chow_mein | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Chinese Herbal Tea | 745a90de-3e4b-444e-8de4-fa4cefc63eb0 | R05 | MAP | chinese_herbal_tea | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Coke | 4c7162f6-1a21-4545-8eb5-6c49b01af0fd | R06 | MAP | coke | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Crispy Tempura Shrimp (4pcs) | Crispy_Tempura_Shrim | R07 | MAP | tempura_shrimp | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Cucumber Mix With Home Made Spicy Sauce | 0cbf47bd-5d4c-456a-900e-7da7f6f8f54b | R08 | MAP | cucumber_salad | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Dandan Noodle Combo (With Peanuts) | 87bbecca-85fa-4681-a3dd-06c8bd97d70a | R09 | MAP | dan_dan_noodle | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Dandan Noodles (With Peanuts) | 2be2f7be-aa39-4742-bf91-edc9759f19c0 | R10 | MAP | dan_dan_noodle | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Diet Coke | 8c3c9093-c5fb-4d6d-ac0c-140451e9f29a | R11 | MAP | diet_coke | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Edamame With Preserved Vegetable | 2155b250-58f2-49d2-a971-d149d8a61386 | R12 | MAP | edamame | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Fried Chinese Steamed buns (3pcs) | Fried_Chinese_Steame | R13 | MAP | fried_steamed_buns | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Fried Wontons (6 pcs) | Fried_Wontons_(6_pcs | R14 | MAP | fried_wontons | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Ginger Ale | cbcd1dcd-87bb-4bd3-aa95-147817e134c8 | R15 | MAP | canada_dry | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Green Grape Soju | Green_Grape_Soju | R16 | MAP | shochu_fruit | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Hakutsuru Junmai Ginjo Sake | Hakutsuru_Junmai_Gin | R17 | MAP | lg_sake | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Homemade Lanzhou Beef (With Peanuts) | 71020dd7-1dbc-4711-9852-dcd1191d5eaa | R18 | MAP | braised_beef_shank_salad | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Ice Tea | 7aad8f91-c26d-4c1d-8609-341f83396317 | R19 | MAP | ice_tea | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Lanzhou Beef Chow Mein (Beef) | 9c8260ac-717a-4d20-80b5-aeef17613186 | R20 | MAP | beef_chow_mein | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Lanzhou Beef Chow Mein Combo | b3f90251-5066-42c4-bb4c-9a1581a38936 | R21 | MAP | beef_chow_mein | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Lychee Soju | Good_Day_Lychee_Soju | R22 | MAP | shochu_fruit | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Melon Soju | Melon_Soju | R23 | MAP | shochu_fruit | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Noodle with Vegetables | 3b8b2764-d492-47e0-9977-107ed4e67d1f | R24 | MAP | vegetable_noodle | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Noodles with Vegetables Combo | e72231fb-9029-4539-91d6-b6efbeeb3767 | R25 | MAP | vegetable_noodle | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Original Soju | Original_Soju | R26 | MAP | soju | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Peach Soju | Peach_Soju | R27 | MAP | shochu_fruit | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Rouleaux de printemps frits (3 pcs) | Rouleaux_de_printemp | R28 | MAP | fried_spring_rolls | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Sapporo Beer | Sapporo_Beer | R29 | MAP | sapporo | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Sayuri Nigori Sake | Nigori_Sake | R30 | MAP | sm_sake | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Sprite | 79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2 | R31 | MAP | seven_up | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Stir-fried Tomato Beef Noodle (Beef) | b11777c1-9d50-44fe-8397-040a5056152b | R32 | MAP | tomato_chow_mein | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Stir-fried Tomato Beef Noodle Combo | e45ddd3e-dbf5-449d-8ab6-badbccd4897b | R33 | MAP | tomato_chow_mein | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Sweet & Sour Mini Fries | 540c3a0b-3830-4bc6-b706-14638c7e6352 | R34 | MAP | shredded_potato | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Tea Corned Egg | e8e7be55-3139-4949-8a7e-69a1d52161c5 | R35 | MAP | tea_egg | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Traditional Lanzhou Hand-pull Beef Noodle | b940caa7-6e37-4b3b-b964-3e10151e7903 | R36 | MAP | traditional_beef_noodle | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Tsingtao Beer | Tsingtao_Beer | R37 | MAP | tsingtao_beer | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Vegetable Chow Mein | 842d5987-6945-42ef-a1d9-ed15cad18a0b | R38 | MAP | vegetable_chow_mein | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Vegetable Chow Mein Combo | 78b60d9c-29fc-4064-9b10-10dd11427df5 | R39 | MAP | vegetable_chow_mein | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Zha Jiang Noodle | 41989752-4e09-4fc5-8d22-96e9427173eb | R40 | MAP | zha_jiang_noodle | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| ITEM | Zhajiang Noodles Combo | e8188b13-132d-48af-a981-a3dd1476ee01 | R41 | MAP | zha_jiang_noodle | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | 2026-10-03T03:50:59Z DB read-back |
| MODIFIER | Add Fried Egg | 43731428-8827-403a-b401-246ff779bef0 | R03, R20, R32, R38 / Add Egg [f90e0f1c-5ddb-4f09-a637-7658c84571ac] | UNMAPPED | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Add Tea Boil Egg | 316bb70f-f2a4-4b44-9b7a-7ad73c5243d9 | R03, R20, R32, R38 / Add Egg [f90e0f1c-5ddb-4f09-a637-7658c84571ac] | UNMAPPED | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_extra_wide | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | R01, R04, R09, R21, R25, R33, R39, R41 / Side Dishes [1083b73c-aba1-4bbd-b48c-c3b15e757313] | MAP | beef_chow_mein, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | combo_cucumber_salad | COMBO_SIDE | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | R01, R04, R09, R21, R25, R33, R39, R41 / Side Dishes [1083b73c-aba1-4bbd-b48c-c3b15e757313] | MAP | beef_chow_mein, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | combo_edamame | COMBO_SIDE | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Fried Egg | 329049e9-f054-4f0a-ad82-ccbd189c262d | R09, R10, R40, R41 / Extra Topping [552ab8e7-81c9-4f99-b5ec-637c4c70aa77] | MAP | dan_dan_noodle, zha_jiang_noodle | fried_egg | ADD_ON | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Fried Egg | 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | MAP | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | fried_egg | ADD_ON | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Meat | 524842f3-7e5d-441f-813b-895f0491179f | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | UNMAPPED | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Noodle | 2ad33578-e7c6-4fa3-ac38-f4dff54a7eae | R09, R10, R40, R41 / Extra Topping [552ab8e7-81c9-4f99-b5ec-637c4c70aa77] | UNMAPPED | dan_dan_noodle, zha_jiang_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Noodle | dddc88d0-5f52-4266-af87-ea7477d63f30 | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | UNMAPPED | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | R01, R02, R03, R04, R09, R10, R20, R21, R24, R25, R32, R33, R36, R38, R39, R40, R41 / Spicy Level [1378d24d-9096-460c-868d-cc7c520178de] | MAP | beef_chow_mein, braised_beef_tendon_noodle, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | spicy_extra | SPICY_LEVEL | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Tea Boil Egg | 3e400404-e7da-4401-8f23-b91d74d7986e | R09, R10, R40, R41 / Extra Topping [552ab8e7-81c9-4f99-b5ec-637c4c70aa77] | MAP | dan_dan_noodle, zha_jiang_noodle | tea_egg | ADD_ON | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Tea Boil Egg | cd3b4c00-5065-47ac-a8e8-a2772067ca16 | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | MAP | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | tea_egg | ADD_ON | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Vegetable | 2ca784f1-b086-42bb-aee0-64840d75cacf | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | UNMAPPED | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Extra Vegetable | a79813fd-2d14-45bd-a57c-462a8ddbb70f | R09, R10, R40, R41 / Extra Topping [552ab8e7-81c9-4f99-b5ec-637c4c70aa77] | UNMAPPED | dan_dan_noodle, zha_jiang_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_capillary | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Fried Egg | 8d6efb88-7974-4973-a681-263b7f186959 | R04, R21, R33, R39 / Fried Egg [ecfa104f-042b-48d2-88a8-4955f11b381e] | MAP | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | combo_fried_egg | COMBO_EGG | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Large | 01c44198-eeb9-4452-bc2b-0939bf713a39 | R01, R02, R24, R25, R36 / Size [ddf85f09-ade3-406a-8d4a-5fdb25abae50] | MAP | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | size_large | SIZE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_leek_leaf | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Meat Broth | 133d15f2-124d-43c6-9b2c-15352e25926b | R24 / Soup Base [00c3e999-ba01-4cbb-91b3-bc4237bd5ed7] | UNMAPPED | vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | R01, R02, R03, R04, R09, R10, R20, R21, R24, R25, R32, R33, R36, R38, R39, R40, R41 / Spicy Level [1378d24d-9096-460c-868d-cc7c520178de] | MAP | beef_chow_mein, braised_beef_tendon_noodle, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | spicy_mild | SPICY_LEVEL | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | R01, R02, R09, R10, R36, R40, R41 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, zha_jiang_noodle | remove_cilantro | REMOVE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | R03, R04, R20, R21, R32, R33, R38, R39 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | NO_OP | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | — | — | — | INGREDIENT_NOT_USED | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。忽略该已确认去料要求；有子项/额外制作备注仍需审查 |
| MODIFIER | Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | R24, R25 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | UNMAPPED | vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | R01, R02, R09, R10, R36, R40, R41 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, zha_jiang_noodle | remove_green_onion | REMOVE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | R03, R04, R20, R21, R32, R33, R38, R39 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | NO_OP | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | — | — | — | INGREDIENT_NOT_USED | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。忽略该已确认去料要求；有子项/额外制作备注仍需审查 |
| MODIFIER | Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | R24, R25 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | UNMAPPED | vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | R01, R02, R03, R04, R09, R10, R20, R21, R24, R25, R32, R33, R36, R38, R39, R40, R41 / Spicy Level [1378d24d-9096-460c-868d-cc7c520178de] | MAP | beef_chow_mein, braised_beef_tendon_noodle, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | spicy_none | SPICY_LEVEL | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_thin | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Regular | 94b90aa8-a673-4793-b7bd-917c837716df | R01, R02, R24, R25, R36 / Size [ddf85f09-ade3-406a-8d4a-5fdb25abae50] | MAP | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | size_regular | SIZE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | R01, R02, R03, R04, R09, R10, R20, R21, R24, R25, R32, R33, R36, R38, R39, R40, R41 / Spicy Level [1378d24d-9096-460c-868d-cc7c520178de] | MAP | beef_chow_mein, braised_beef_tendon_noodle, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | spicy_regular | SPICY_LEVEL | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | R01, R04, R09, R21, R25, R33, R39, R41 / Side Dishes [1083b73c-aba1-4bbd-b48c-c3b15e757313] | MAP | beef_chow_mein, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | combo_shredded_potato | COMBO_SIDE | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Tea Boil Egg | 61ad7ec7-2a69-4781-9210-b71c75084bad | R01, R09, R25, R41 / Tea Boil Egg [70c64ebd-81ea-4c7c-9e57-b73885fe787e] | MAP | dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | combo_tea_egg | COMBO_EGG | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_sanxi | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_erxi | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Vegan Broth | 182b7ed4-d3c7-4aeb-9516-aa0c3959de97 | R24 / Soup Base [00c3e999-ba01-4cbb-91b3-bc4237bd5ed7] | UNMAPPED | vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；不跨parent context扩展。 |
| MODIFIER | Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_wide | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；不跨parent context扩展。 |

## 首阶段组合

Traditional Lanzhou Hand-pull Beef Noodle + Large + Three Fine + Non-spicy + Extra Fried Egg + Non Coriander：六项 identity 均已人工确认，正式规则已落库；实际部署 mapping service 的 resolution 结果另见本轮验收。

FIRST_STAGE_TEST_COMBINATION_FULLY_MAPPED = YES

## 当前外部条件

真实pos_data：integration_enabled=true；order_manager_client_id=当前TEST APP；is_order_manager_pending未返回；order_release_enabled=false；online_status=online。ORDER_RELEASE_TEST_BLOCKED=YES。未修改Uber配置或Production。

## 已批准规则范围

| Action / Kind | Confirmed context rules |
| --- | --- |
| MAP/ITEM | 41 |
| MAP/MODIFIER | 205 |
| NO_OP/MODIFIER | 16 |
| UNMAPPED/ITEM | 0 |
| UNMAPPED/MODIFIER | 37 |

用户批准已到达；Kitchen Mirror E2E 验收另行记录，不能由 mapping 持久化推断 Sandbox PASS。

## 历史：首次 Staging 数据库读回（2026-10-03T00:38:51Z）

写入前：MAP items=0，MAP modifier contexts=0，NO_OP contexts=0。
已备份 Staging DB，仅通过 Store-scoped 管理 API 写入批准规则。
写入后 API 完整键值集合与数据库计数一致；没有跨 Store/org 规则。

- MAPPED_ITEMS = 5
- MAPPED_MODIFIERS = 205 parent contexts
- NO_OP_MODIFIERS = 16 parent contexts
- UNMAPPED_ITEMS = 36
- UNMAPPED_MODIFIERS = 37 parent contexts
- Human Confirmed=NO / UNMAPPED 写入数 = 0
- Target = Store 1 / organization 1 / sandbox / bd993244-5589-4b19-8f0d-dc2ba73d4273

FIRST_STAGE_TEST_COMBINATION_FULLY_MAPPED = YES

## 历史：Runtime resolution after initial persistence

2026-10-03T00:58:45Z, Staging `703488638ec2bf2002cee113b037ef547aaa61fa`: the authenticated mapping-preview API used the persisted 226 rules and actual Store 1 menu catalog. First-stage item plus size_large/noodle_sanxi/spicy_none/fried_egg/remove_cilantro resolved without errors. COMBO_ROOT injected combo and all three side variants plus combo_tea_egg resolved without errors. Unknown modifier remained blocked. All 16 NO_OP contexts resolved without emitting options in an isolated exact-SKU diagnostic; their 8 actual Uber roots remain UNMAPPED and still block full orders. No order was inserted by these previews. A read-only preview of real TEST order `4ab95dd8-3a9d-49c8-9b13-9795adadf5c6` confirmed the same five options, with no fuzzy matching. This mapping proof is not real webhook/dispatch E2E.

## 历史：首次 Real TEST E2E and readback

2026-10-03T01:15:34Z: final authenticated API exact-set comparison plus DB counts
still match all 226 approved rules; no unapproved or cross-scope rows. Real TEST
order `05c16dcb-8ef8-4b75-9548-45581ef2a3a2` resolved the first combination into
local order 77 and both MOCK kitchen modules. See [real E2E evidence](UBER_KITCHEN_MIRROR_E2E_20261002.md).
UNMAPPED totals remain 36 items / 37 modifier contexts; Human Confirmed semantics
and all mapping actions are unchanged.
