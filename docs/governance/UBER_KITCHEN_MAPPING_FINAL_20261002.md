# Uber Kitchen Mapping 最终执行表 — 2026-10-02

状态：用户于 2026-10-02 正式批准“确认按表执行”；已按批准范围写入 Staging。Human Confirmed=YES 表示本次附件/消息已明确确认身份或NO_OP；Persisted=YES 表示本轮已从数据库重新读取并核对。MAP/NO_OP 是已执行规则；UNMAPPED 不授予本地身份；Owner 后续明确修改 Mirror 规则：新接单使用冻结 raw GRAB fallback，详见 [V33 合约](../../doc/API.md)。本表身份确认和持久化结果不变。本轮不会将旧review稿或占位文字“[这里粘贴...]”当成确认。

范围：sandbox Test Store `bd993244-5589-4b19-8f0d-dc2ba73d4273` → Store 1 / org 1 / STG005_SRC_20260809_R01 / MOCK。新鲜读取 2026-10-02T16:41:08Z；正式规则已写入 226 条，见文末读回。

## 最终人工确认摘要

- 已确认并已写入：传统牛肉面、土豆丝、黄瓜、毛豆、牛肉面套餐根；7种面型、大小碗、4种辣度、Extra煎蛋/茶蛋、明确去葱香菜、套餐蛋和3种小菜。全部按表中稳定ID执行。
- 四种炒面及其Combo根下两个去葱/香菜 modifier：NO_OP / INGREDIENT_NOT_USED；其他菜按已有REMOVE语义MAP，不能全局NO_OP。
- Combo模式采用COMBO_ROOT，自动注入本地combo，再映射egg/side。模式已确认，但除牛肉面Combo示例外，其余主菜身份没有明确确认，仍UNMAPPED。
- Add Fried Egg / Add Tea Boil Egg 与 Extra版本是不同Uber IDs，未收到这两个ID语义确认，不自动扩展；Extra Noodle、Extra Meat、Extra Vegetable、Soup Base等也保持UNMAPPED。
- 本表已获批准并执行 MAP/NO_OP，其余保留 UNMAPPED；若要补充，按下方R编号确认具体主菜，不必阅读258条上下文。

## Parent context 索引

| Root ref | Uber root name | Stable ID | Local candidate / confirmed identity | Identity confirmed |
| --- | --- | --- | --- | --- |
| R01 | Beef Lanzhou Noodles Special Combo | eca7fbf3-a666-454b-b36e-7d380e80b49d | traditional_beef_noodle | YES |
| R02 | Braised Beef Tendon in Brown Sauce with Noodles | Braised_Beef_Tendon_ | braised_beef_tendon_noodle | NO |
| R03 | Chicken Chow Mein | d2a77967-74f3-4dfb-9e8e-a236369bb4f6 | chicken_chow_mein | NO |
| R04 | Chicken Chow Mein Combo | c169f422-a9cc-4758-82a9-0f60eef89b68 | chicken_chow_mein | NO |
| R05 | Chinese Herbal Tea | 745a90de-3e4b-444e-8de4-fa4cefc63eb0 | chinese_herbal_tea | NO |
| R06 | Coke | 4c7162f6-1a21-4545-8eb5-6c49b01af0fd | coke | NO |
| R07 | Crispy Tempura Shrimp (4pcs) | Crispy_Tempura_Shrim | tempura_shrimp | NO |
| R08 | Cucumber Mix With Home Made Spicy Sauce | 0cbf47bd-5d4c-456a-900e-7da7f6f8f54b | cucumber_salad | YES |
| R09 | Dandan Noodle Combo (With Peanuts) | 87bbecca-85fa-4681-a3dd-06c8bd97d70a | dan_dan_noodle | NO |
| R10 | Dandan Noodles (With Peanuts) | 2be2f7be-aa39-4742-bf91-edc9759f19c0 | dan_dan_noodle | NO |
| R11 | Diet Coke | 8c3c9093-c5fb-4d6d-ac0c-140451e9f29a | diet_coke | NO |
| R12 | Edamame With Preserved Vegetable | 2155b250-58f2-49d2-a971-d149d8a61386 | edamame | YES |
| R13 | Fried Chinese Steamed buns (3pcs) | Fried_Chinese_Steame | fried_steamed_buns | NO |
| R14 | Fried Wontons (6 pcs) | Fried_Wontons_(6_pcs | fried_wontons | NO |
| R15 | Ginger Ale | cbcd1dcd-87bb-4bd3-aa95-147817e134c8 | canada_dry | NO |
| R16 | Green Grape Soju | Green_Grape_Soju | NONE | NO |
| R17 | Hakutsuru Junmai Ginjo Sake | Hakutsuru_Junmai_Gin | NONE | NO |
| R18 | Homemade Lanzhou Beef (With Peanuts) | 71020dd7-1dbc-4711-9852-dcd1191d5eaa | braised_beef_shank_salad | NO |
| R19 | Ice Tea | 7aad8f91-c26d-4c1d-8609-341f83396317 | ice_tea | NO |
| R20 | Lanzhou Beef Chow Mein (Beef) | 9c8260ac-717a-4d20-80b5-aeef17613186 | beef_chow_mein | NO |
| R21 | Lanzhou Beef Chow Mein Combo | b3f90251-5066-42c4-bb4c-9a1581a38936 | beef_chow_mein | NO |
| R22 | Lychee Soju | Good_Day_Lychee_Soju | NONE | NO |
| R23 | Melon Soju | Melon_Soju | NONE | NO |
| R24 | Noodle with Vegetables | 3b8b2764-d492-47e0-9977-107ed4e67d1f | vegetable_noodle | NO |
| R25 | Noodles with Vegetables Combo | e72231fb-9029-4539-91d6-b6efbeeb3767 | vegetable_noodle | NO |
| R26 | Original Soju | Original_Soju | soju | NO |
| R27 | Peach Soju | Peach_Soju | NONE | NO |
| R28 | Rouleaux de printemps frits (3 pcs) | Rouleaux_de_printemp | fried_spring_rolls | NO |
| R29 | Sapporo Beer | Sapporo_Beer | sapporo | NO |
| R30 | Sayuri Nigori Sake | Nigori_Sake | NONE | NO |
| R31 | Sprite | 79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2 | NONE | NO |
| R32 | Stir-fried Tomato Beef Noodle (Beef) | b11777c1-9d50-44fe-8397-040a5056152b | tomato_chow_mein | NO |
| R33 | Stir-fried Tomato Beef Noodle Combo | e45ddd3e-dbf5-449d-8ab6-badbccd4897b | tomato_chow_mein | NO |
| R34 | Sweet & Sour Mini Fries | 540c3a0b-3830-4bc6-b706-14638c7e6352 | shredded_potato | YES |
| R35 | Tea Corned Egg | e8e7be55-3139-4949-8a7e-69a1d52161c5 | tea_egg | NO |
| R36 | Traditional Lanzhou Hand-pull Beef Noodle | b940caa7-6e37-4b3b-b964-3e10151e7903 | traditional_beef_noodle | YES |
| R37 | Tsingtao Beer | Tsingtao_Beer | tsingtao_beer | NO |
| R38 | Vegetable Chow Mein | 842d5987-6945-42ef-a1d9-ed15cad18a0b | vegetable_chow_mein | NO |
| R39 | Vegetable Chow Mein Combo | 78b60d9c-29fc-4064-9b10-10dd11427df5 | vegetable_chow_mein | NO |
| R40 | Zha Jiang Noodle | 41989752-4e09-4fc5-8d22-96e9427173eb | zha_jiang_noodle | NO |
| R41 | Zhajiang Noodles Combo | e8188b13-132d-48af-a981-a3dd1476ee01 | zha_jiang_noodle | NO |

R编号仅是本报告的简写，持久化必须展开为完整root ID；禁止将R编号保存为Uber ID。相同modifier稳定ID合并展示，只有MAP/NO_OP范围不同才拆行。

## 完整最终表

| Uber Type | Uber Name | Uber Stable ID | Parent Uber Item / Group | Action | Local Item SKU | Local Option Code | Local Option Group | Combo Mapping Mode | Reason | Human Confirmed | Persisted | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| ITEM | Beef Lanzhou Noodles Special Combo | eca7fbf3-a666-454b-b36e-7d380e80b49d | R01 | MAP | traditional_beef_noodle | combo (implicit) | COMBO | COMBO_ROOT | HUMAN_CONFIRMED_IDENTITY | YES | YES | — |
| ITEM | Braised Beef Tendon in Brown Sauce with Noodles | Braised_Beef_Tendon_ | R02 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: braised_beef_tendon_noodle；需另外确认，不自动扩展 |
| ITEM | Chicken Chow Mein | d2a77967-74f3-4dfb-9e8e-a236369bb4f6 | R03 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: chicken_chow_mein；需另外确认，不自动扩展 |
| ITEM | Chicken Chow Mein Combo | c169f422-a9cc-4758-82a9-0f60eef89b68 | R04 | UNMAPPED | — | — | — | COMBO_ROOT | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: chicken_chow_mein；需另外确认，不自动扩展 |
| ITEM | Chinese Herbal Tea | 745a90de-3e4b-444e-8de4-fa4cefc63eb0 | R05 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: chinese_herbal_tea；需另外确认，不自动扩展 |
| ITEM | Coke | 4c7162f6-1a21-4545-8eb5-6c49b01af0fd | R06 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: coke；需另外确认，不自动扩展 |
| ITEM | Crispy Tempura Shrimp (4pcs) | Crispy_Tempura_Shrim | R07 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: tempura_shrimp；需另外确认，不自动扩展 |
| ITEM | Cucumber Mix With Home Made Spicy Sauce | 0cbf47bd-5d4c-456a-900e-7da7f6f8f54b | R08 | MAP | cucumber_salad | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | — |
| ITEM | Dandan Noodle Combo (With Peanuts) | 87bbecca-85fa-4681-a3dd-06c8bd97d70a | R09 | UNMAPPED | — | — | — | COMBO_ROOT | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: dan_dan_noodle；需另外确认，不自动扩展 |
| ITEM | Dandan Noodles (With Peanuts) | 2be2f7be-aa39-4742-bf91-edc9759f19c0 | R10 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: dan_dan_noodle；需另外确认，不自动扩展 |
| ITEM | Diet Coke | 8c3c9093-c5fb-4d6d-ac0c-140451e9f29a | R11 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: diet_coke；需另外确认，不自动扩展 |
| ITEM | Edamame With Preserved Vegetable | 2155b250-58f2-49d2-a971-d149d8a61386 | R12 | MAP | edamame | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | — |
| ITEM | Fried Chinese Steamed buns (3pcs) | Fried_Chinese_Steame | R13 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: fried_steamed_buns；需另外确认，不自动扩展 |
| ITEM | Fried Wontons (6 pcs) | Fried_Wontons_(6_pcs | R14 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: fried_wontons；需另外确认，不自动扩展 |
| ITEM | Ginger Ale | cbcd1dcd-87bb-4bd3-aa95-147817e134c8 | R15 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: canada_dry；需另外确认，不自动扩展 |
| ITEM | Green Grape Soju | Green_Grape_Soju | R16 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | — |
| ITEM | Hakutsuru Junmai Ginjo Sake | Hakutsuru_Junmai_Gin | R17 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | — |
| ITEM | Homemade Lanzhou Beef (With Peanuts) | 71020dd7-1dbc-4711-9852-dcd1191d5eaa | R18 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: braised_beef_shank_salad；需另外确认，不自动扩展 |
| ITEM | Ice Tea | 7aad8f91-c26d-4c1d-8609-341f83396317 | R19 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: ice_tea；需另外确认，不自动扩展 |
| ITEM | Lanzhou Beef Chow Mein (Beef) | 9c8260ac-717a-4d20-80b5-aeef17613186 | R20 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: beef_chow_mein；需另外确认，不自动扩展 |
| ITEM | Lanzhou Beef Chow Mein Combo | b3f90251-5066-42c4-bb4c-9a1581a38936 | R21 | UNMAPPED | — | — | — | COMBO_ROOT | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: beef_chow_mein；需另外确认，不自动扩展 |
| ITEM | Lychee Soju | Good_Day_Lychee_Soju | R22 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | — |
| ITEM | Melon Soju | Melon_Soju | R23 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | — |
| ITEM | Noodle with Vegetables | 3b8b2764-d492-47e0-9977-107ed4e67d1f | R24 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: vegetable_noodle；需另外确认，不自动扩展 |
| ITEM | Noodles with Vegetables Combo | e72231fb-9029-4539-91d6-b6efbeeb3767 | R25 | UNMAPPED | — | — | — | COMBO_ROOT | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: vegetable_noodle；需另外确认，不自动扩展 |
| ITEM | Original Soju | Original_Soju | R26 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: soju；需另外确认，不自动扩展 |
| ITEM | Peach Soju | Peach_Soju | R27 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | — |
| ITEM | Rouleaux de printemps frits (3 pcs) | Rouleaux_de_printemp | R28 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: fried_spring_rolls；需另外确认，不自动扩展 |
| ITEM | Sapporo Beer | Sapporo_Beer | R29 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: sapporo；需另外确认，不自动扩展 |
| ITEM | Sayuri Nigori Sake | Nigori_Sake | R30 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | — |
| ITEM | Sprite | 79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2 | R31 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | — |
| ITEM | Stir-fried Tomato Beef Noodle (Beef) | b11777c1-9d50-44fe-8397-040a5056152b | R32 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: tomato_chow_mein；需另外确认，不自动扩展 |
| ITEM | Stir-fried Tomato Beef Noodle Combo | e45ddd3e-dbf5-449d-8ab6-badbccd4897b | R33 | UNMAPPED | — | — | — | COMBO_ROOT | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: tomato_chow_mein；需另外确认，不自动扩展 |
| ITEM | Sweet & Sour Mini Fries | 540c3a0b-3830-4bc6-b706-14638c7e6352 | R34 | MAP | shredded_potato | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | — |
| ITEM | Tea Corned Egg | e8e7be55-3139-4949-8a7e-69a1d52161c5 | R35 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: tea_egg；需另外确认，不自动扩展 |
| ITEM | Traditional Lanzhou Hand-pull Beef Noodle | b940caa7-6e37-4b3b-b964-3e10151e7903 | R36 | MAP | traditional_beef_noodle | — | — | STANDARD | HUMAN_CONFIRMED_IDENTITY | YES | YES | — |
| ITEM | Tsingtao Beer | Tsingtao_Beer | R37 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: tsingtao_beer；需另外确认，不自动扩展 |
| ITEM | Vegetable Chow Mein | 842d5987-6945-42ef-a1d9-ed15cad18a0b | R38 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: vegetable_chow_mein；需另外确认，不自动扩展 |
| ITEM | Vegetable Chow Mein Combo | 78b60d9c-29fc-4064-9b10-10dd11427df5 | R39 | UNMAPPED | — | — | — | COMBO_ROOT | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: vegetable_chow_mein；需另外确认，不自动扩展 |
| ITEM | Zha Jiang Noodle | 41989752-4e09-4fc5-8d22-96e9427173eb | R40 | UNMAPPED | — | — | — | STANDARD | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: zha_jiang_noodle；需另外确认，不自动扩展 |
| ITEM | Zhajiang Noodles Combo | e8188b13-132d-48af-a981-a3dd1476ee01 | R41 | UNMAPPED | — | — | — | COMBO_ROOT | ITEM_IDENTITY_NOT_CONFIRMED | NO | NO | 既有review候选: zha_jiang_noodle；需另外确认，不自动扩展 |
| MODIFIER | Add Fried Egg | 43731428-8827-403a-b401-246ff779bef0 | R03, R20, R32, R38 / Add Egg [f90e0f1c-5ddb-4f09-a637-7658c84571ac] | UNMAPPED | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Add Tea Boil Egg | 316bb70f-f2a4-4b44-9b7a-7ad73c5243d9 | R03, R20, R32, R38 / Add Egg [f90e0f1c-5ddb-4f09-a637-7658c84571ac] | UNMAPPED | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_extra_wide | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | R01, R04, R09, R21, R25, R33, R39, R41 / Side Dishes [1083b73c-aba1-4bbd-b48c-c3b15e757313] | MAP | beef_chow_mein, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | combo_cucumber_salad | COMBO_SIDE | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | R01, R04, R09, R21, R25, R33, R39, R41 / Side Dishes [1083b73c-aba1-4bbd-b48c-c3b15e757313] | MAP | beef_chow_mein, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | combo_edamame | COMBO_SIDE | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Fried Egg | 329049e9-f054-4f0a-ad82-ccbd189c262d | R09, R10, R40, R41 / Extra Topping [552ab8e7-81c9-4f99-b5ec-637c4c70aa77] | MAP | dan_dan_noodle, zha_jiang_noodle | fried_egg | ADD_ON | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Fried Egg | 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | MAP | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | fried_egg | ADD_ON | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Meat | 524842f3-7e5d-441f-813b-895f0491179f | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | UNMAPPED | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Noodle | 2ad33578-e7c6-4fa3-ac38-f4dff54a7eae | R09, R10, R40, R41 / Extra Topping [552ab8e7-81c9-4f99-b5ec-637c4c70aa77] | UNMAPPED | dan_dan_noodle, zha_jiang_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Noodle | dddc88d0-5f52-4266-af87-ea7477d63f30 | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | UNMAPPED | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | R01, R02, R03, R04, R09, R10, R20, R21, R24, R25, R32, R33, R36, R38, R39, R40, R41 / Spicy Level [1378d24d-9096-460c-868d-cc7c520178de] | MAP | beef_chow_mein, braised_beef_tendon_noodle, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | spicy_extra | SPICY_LEVEL | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Tea Boil Egg | 3e400404-e7da-4401-8f23-b91d74d7986e | R09, R10, R40, R41 / Extra Topping [552ab8e7-81c9-4f99-b5ec-637c4c70aa77] | MAP | dan_dan_noodle, zha_jiang_noodle | tea_egg | ADD_ON | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Tea Boil Egg | cd3b4c00-5065-47ac-a8e8-a2772067ca16 | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | MAP | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | tea_egg | ADD_ON | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Vegetable | 2ca784f1-b086-42bb-aee0-64840d75cacf | R01, R02, R24, R25, R36 / Extra Topping [266df6ed-5d9d-4b05-94b2-7c86884866a3] | UNMAPPED | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Extra Vegetable | a79813fd-2d14-45bd-a57c-462a8ddbb70f | R09, R10, R40, R41 / Extra Topping [552ab8e7-81c9-4f99-b5ec-637c4c70aa77] | UNMAPPED | dan_dan_noodle, zha_jiang_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_capillary | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Fried Egg | 8d6efb88-7974-4973-a681-263b7f186959 | R04, R21, R33, R39 / Fried Egg [ecfa104f-042b-48d2-88a8-4955f11b381e] | MAP | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | combo_fried_egg | COMBO_EGG | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Large | 01c44198-eeb9-4452-bc2b-0939bf713a39 | R01, R02, R24, R25, R36 / Size [ddf85f09-ade3-406a-8d4a-5fdb25abae50] | MAP | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | size_large | SIZE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_leek_leaf | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Meat Broth | 133d15f2-124d-43c6-9b2c-15352e25926b | R24 / Soup Base [00c3e999-ba01-4cbb-91b3-bc4237bd5ed7] | UNMAPPED | vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | R01, R02, R03, R04, R09, R10, R20, R21, R24, R25, R32, R33, R36, R38, R39, R40, R41 / Spicy Level [1378d24d-9096-460c-868d-cc7c520178de] | MAP | beef_chow_mein, braised_beef_tendon_noodle, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | spicy_mild | SPICY_LEVEL | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | R01, R02, R09, R10, R36, R40, R41 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, zha_jiang_noodle | remove_cilantro | REMOVE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | R03, R04, R20, R21, R32, R33, R38, R39 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | NO_OP | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | — | — | — | INGREDIENT_NOT_USED | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。忽略该已确认去料要求；有子项/额外制作备注仍需审查 |
| MODIFIER | Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | R24, R25 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | UNMAPPED | vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | R01, R02, R09, R10, R36, R40, R41 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, zha_jiang_noodle | remove_green_onion | REMOVE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | R03, R04, R20, R21, R32, R33, R38, R39 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | NO_OP | beef_chow_mein, chicken_chow_mein, tomato_chow_mein, vegetable_chow_mein | — | — | — | INGREDIENT_NOT_USED | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。忽略该已确认去料要求；有子项/额外制作备注仍需审查 |
| MODIFIER | Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | R24, R25 / Ingredients Option [00301352-8fe2-4c03-91c4-229a32a5acfc] | UNMAPPED | vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | R01, R02, R03, R04, R09, R10, R20, R21, R24, R25, R32, R33, R36, R38, R39, R40, R41 / Spicy Level [1378d24d-9096-460c-868d-cc7c520178de] | MAP | beef_chow_mein, braised_beef_tendon_noodle, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | spicy_none | SPICY_LEVEL | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_thin | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Regular | 94b90aa8-a673-4793-b7bd-917c837716df | R01, R02, R24, R25, R36 / Size [ddf85f09-ade3-406a-8d4a-5fdb25abae50] | MAP | braised_beef_tendon_noodle, traditional_beef_noodle, vegetable_noodle | size_regular | SIZE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | R01, R02, R03, R04, R09, R10, R20, R21, R24, R25, R32, R33, R36, R38, R39, R40, R41 / Spicy Level [1378d24d-9096-460c-868d-cc7c520178de] | MAP | beef_chow_mein, braised_beef_tendon_noodle, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | spicy_regular | SPICY_LEVEL | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | R01, R04, R09, R21, R25, R33, R39, R41 / Side Dishes [1083b73c-aba1-4bbd-b48c-c3b15e757313] | MAP | beef_chow_mein, chicken_chow_mein, dan_dan_noodle, tomato_chow_mein, traditional_beef_noodle, vegetable_chow_mein, vegetable_noodle, zha_jiang_noodle | combo_shredded_potato | COMBO_SIDE | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Tea Boil Egg | 61ad7ec7-2a69-4781-9210-b71c75084bad | R01, R09, R25, R41 / Tea Boil Egg [70c64ebd-81ea-4c7c-9e57-b73885fe787e] | MAP | dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | combo_tea_egg | COMBO_EGG | COMPONENT_REQUIRES_COMBO | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_sanxi | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_erxi | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Vegan Broth | 182b7ed4-d3c7-4aeb-9516-aa0c3959de97 | R24 / Soup Base [00c3e999-ba01-4cbb-91b3-bc4237bd5ed7] | UNMAPPED | vegetable_noodle | — | — | — | IDENTITY_NOT_CONFIRMED_OR_OPTION_UNAVAILABLE | NO | NO | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |
| MODIFIER | Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | R01, R02, R09, R10, R24, R25, R36, R40, R41 / Noodle Size Option [04eda3d3-627c-405b-9e7c-8bde8061d5c6] | MAP | braised_beef_tendon_noodle, dan_dan_noodle, traditional_beef_noodle, vegetable_noodle, zha_jiang_noodle | noodle_wide | NOODLE_TYPE | — | HUMAN_CONFIRMED_IDENTITY | YES | YES | 适用范围仅所列root IDs；root自身UNMAPPED时仍禁止release。 |

## 首阶段组合

Traditional Lanzhou Hand-pull Beef Noodle + Large + Three Fine + Non-spicy + Extra Fried Egg + Non Coriander：六项 identity 均已人工确认，正式规则已落库；实际部署 mapping service 的 resolution 结果另见本轮验收。

FIRST_STAGE_TEST_COMBINATION_FULLY_MAPPED = YES

## 当前外部条件

真实pos_data：integration_enabled=true；order_manager_client_id=当前TEST APP；is_order_manager_pending未返回；order_release_enabled=false；online_status=online。ORDER_RELEASE_TEST_BLOCKED=YES。未修改Uber配置或Production。

## 已批准规则范围

| Action / Kind | Confirmed context rules |
| --- | --- |
| MAP/ITEM | 5 |
| MAP/MODIFIER | 205 |
| NO_OP/MODIFIER | 16 |
| UNMAPPED/ITEM | 36 |
| UNMAPPED/MODIFIER | 37 |

用户批准已到达；Kitchen Mirror E2E 验收另行记录，不能由 mapping 持久化推断 Sandbox PASS。

## Staging 数据库最终读回（2026-10-03T00:38:51Z）

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

## Runtime resolution after persistence

2026-10-03T00:58:45Z, Staging `703488638ec2bf2002cee113b037ef547aaa61fa`: the authenticated mapping-preview API used the persisted 226 rules and actual Store 1 menu catalog. First-stage item plus size_large/noodle_sanxi/spicy_none/fried_egg/remove_cilantro resolved without errors. COMBO_ROOT injected combo and all three side variants plus combo_tea_egg resolved without errors. Unknown modifier remained blocked. All 16 NO_OP contexts resolved without emitting options in an isolated exact-SKU diagnostic; their 8 actual Uber roots remain UNMAPPED and still block full orders. No order was inserted by these previews. A read-only preview of real TEST order `4ab95dd8-3a9d-49c8-9b13-9795adadf5c6` confirmed the same five options, with no fuzzy matching. This mapping proof is not real webhook/dispatch E2E.

## Real TEST E2E and final readback

2026-10-03T01:15:34Z: final authenticated API exact-set comparison plus DB counts
still match all 226 approved rules; no unapproved or cross-scope rows. Real TEST
order `05c16dcb-8ef8-4b75-9548-45581ef2a3a2` resolved the first combination into
local order 77 and both MOCK kitchen modules. See [real E2E evidence](UBER_KITCHEN_MIRROR_E2E_20261002.md).
UNMAPPED totals remain 36 items / 37 modifier contexts; Human Confirmed semantics
and all mapping actions are unchanged.
