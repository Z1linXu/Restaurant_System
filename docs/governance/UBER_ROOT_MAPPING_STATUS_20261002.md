# Uber Root Mapping — Staging Store 1 / 2026-10-02

Owner 最终确认全部 41 个 root identity，包括原 Candidate=NONE 的7项。已于 2026-10-03T03:50:52Z 写入 Staging，并于03:50:59Z完成独立数据库读回。Scope：sandbox Test Store `bd993244-5589-4b19-8f0d-dc2ba73d4273` → org1 / Store1 `STG005_SRC_20260809_R01`。

- UBER_ROOT_ITEMS_TOTAL = 41
- MAPPED_ROOTS_BEFORE = 5
- NEWLY_MAPPED_ROOTS = 36
- MAPPED_ROOTS_AFTER = 41
- UNMAPPED_ROOTS_AFTER = 0
- ALL_ROOT_MAPPINGS_PERSISTED = YES
- HUMAN_CONFIRMED = YES（全部41项）
- PRODUCTION_CHANGED = NO

8个Combo均为 `COMBO_ROOT`，自动注入本地 `combo`；对应catalog确认唯一combo触发项并包含COMBO_EGG/COMBO_SIDE。已有205条modifier MAP和16条NO_OP原样保留；总规则262条。本轮只确认并写入root，不扩大modifier确认范围。

当前41个root均走正式local mapping。RAW_UBER_FALLBACK只保留为未来新增/漂移root的安全兜底；已生成历史票据仍使用冻结快照。未创建测试订单、重放旧单或执行打印验收。

## MAPPED_ROOTS_AFTER

| Uber Name | Uber Stable ID | Current State | Local Target | Mapping Mode | Human Confirmed | Persisted |
| --- | --- | --- | --- | --- | --- | --- |
| Beef Lanzhou Noodles Special Combo | `eca7fbf3-a666-454b-b36e-7d380e80b49d` | MAP | `traditional_beef_noodle` | COMBO_ROOT | YES | YES |
| Braised Beef Tendon in Brown Sauce with Noodles | `Braised_Beef_Tendon_` | MAP | `braised_beef_tendon_noodle` | STANDARD | YES | YES |
| Chicken Chow Mein | `d2a77967-74f3-4dfb-9e8e-a236369bb4f6` | MAP | `chicken_chow_mein` | STANDARD | YES | YES |
| Chicken Chow Mein Combo | `c169f422-a9cc-4758-82a9-0f60eef89b68` | MAP | `chicken_chow_mein` | COMBO_ROOT | YES | YES |
| Chinese Herbal Tea | `745a90de-3e4b-444e-8de4-fa4cefc63eb0` | MAP | `chinese_herbal_tea` | STANDARD | YES | YES |
| Coke | `4c7162f6-1a21-4545-8eb5-6c49b01af0fd` | MAP | `coke` | STANDARD | YES | YES |
| Crispy Tempura Shrimp (4pcs) | `Crispy_Tempura_Shrim` | MAP | `tempura_shrimp` | STANDARD | YES | YES |
| Cucumber Mix With Home Made Spicy Sauce | `0cbf47bd-5d4c-456a-900e-7da7f6f8f54b` | MAP | `cucumber_salad` | STANDARD | YES | YES |
| Dandan Noodle Combo (With Peanuts) | `87bbecca-85fa-4681-a3dd-06c8bd97d70a` | MAP | `dan_dan_noodle` | COMBO_ROOT | YES | YES |
| Dandan Noodles (With Peanuts) | `2be2f7be-aa39-4742-bf91-edc9759f19c0` | MAP | `dan_dan_noodle` | STANDARD | YES | YES |
| Diet Coke | `8c3c9093-c5fb-4d6d-ac0c-140451e9f29a` | MAP | `diet_coke` | STANDARD | YES | YES |
| Edamame With Preserved Vegetable | `2155b250-58f2-49d2-a971-d149d8a61386` | MAP | `edamame` | STANDARD | YES | YES |
| Fried Chinese Steamed buns (3pcs) | `Fried_Chinese_Steame` | MAP | `fried_steamed_buns` | STANDARD | YES | YES |
| Fried Wontons (6 pcs) | `Fried_Wontons_(6_pcs` | MAP | `fried_wontons` | STANDARD | YES | YES |
| Ginger Ale | `cbcd1dcd-87bb-4bd3-aa95-147817e134c8` | MAP | `canada_dry` | STANDARD | YES | YES |
| Green Grape Soju | `Green_Grape_Soju` | MAP | `shochu_fruit` | STANDARD | YES | YES |
| Hakutsuru Junmai Ginjo Sake | `Hakutsuru_Junmai_Gin` | MAP | `lg_sake` | STANDARD | YES | YES |
| Homemade Lanzhou Beef (With Peanuts) | `71020dd7-1dbc-4711-9852-dcd1191d5eaa` | MAP | `braised_beef_shank_salad` | STANDARD | YES | YES |
| Ice Tea | `7aad8f91-c26d-4c1d-8609-341f83396317` | MAP | `ice_tea` | STANDARD | YES | YES |
| Lanzhou Beef Chow Mein (Beef) | `9c8260ac-717a-4d20-80b5-aeef17613186` | MAP | `beef_chow_mein` | STANDARD | YES | YES |
| Lanzhou Beef Chow Mein Combo | `b3f90251-5066-42c4-bb4c-9a1581a38936` | MAP | `beef_chow_mein` | COMBO_ROOT | YES | YES |
| Lychee Soju | `Good_Day_Lychee_Soju` | MAP | `shochu_fruit` | STANDARD | YES | YES |
| Melon Soju | `Melon_Soju` | MAP | `shochu_fruit` | STANDARD | YES | YES |
| Noodle with Vegetables | `3b8b2764-d492-47e0-9977-107ed4e67d1f` | MAP | `vegetable_noodle` | STANDARD | YES | YES |
| Noodles with Vegetables Combo | `e72231fb-9029-4539-91d6-b6efbeeb3767` | MAP | `vegetable_noodle` | COMBO_ROOT | YES | YES |
| Original Soju | `Original_Soju` | MAP | `soju` | STANDARD | YES | YES |
| Peach Soju | `Peach_Soju` | MAP | `shochu_fruit` | STANDARD | YES | YES |
| Rouleaux de printemps frits (3 pcs) | `Rouleaux_de_printemp` | MAP | `fried_spring_rolls` | STANDARD | YES | YES |
| Sapporo Beer | `Sapporo_Beer` | MAP | `sapporo` | STANDARD | YES | YES |
| Sayuri Nigori Sake | `Nigori_Sake` | MAP | `sm_sake` | STANDARD | YES | YES |
| Sprite | `79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2` | MAP | `seven_up` | STANDARD | YES | YES |
| Stir-fried Tomato Beef Noodle (Beef) | `b11777c1-9d50-44fe-8397-040a5056152b` | MAP | `tomato_chow_mein` | STANDARD | YES | YES |
| Stir-fried Tomato Beef Noodle Combo | `e45ddd3e-dbf5-449d-8ab6-badbccd4897b` | MAP | `tomato_chow_mein` | COMBO_ROOT | YES | YES |
| Sweet & Sour Mini Fries | `540c3a0b-3830-4bc6-b706-14638c7e6352` | MAP | `shredded_potato` | STANDARD | YES | YES |
| Tea Corned Egg | `e8e7be55-3139-4949-8a7e-69a1d52161c5` | MAP | `tea_egg` | STANDARD | YES | YES |
| Traditional Lanzhou Hand-pull Beef Noodle | `b940caa7-6e37-4b3b-b964-3e10151e7903` | MAP | `traditional_beef_noodle` | STANDARD | YES | YES |
| Tsingtao Beer | `Tsingtao_Beer` | MAP | `tsingtao_beer` | STANDARD | YES | YES |
| Vegetable Chow Mein | `842d5987-6945-42ef-a1d9-ed15cad18a0b` | MAP | `vegetable_chow_mein` | STANDARD | YES | YES |
| Vegetable Chow Mein Combo | `78b60d9c-29fc-4064-9b10-10dd11427df5` | MAP | `vegetable_chow_mein` | COMBO_ROOT | YES | YES |
| Zha Jiang Noodle | `41989752-4e09-4fc5-8d22-96e9427173eb` | MAP | `zha_jiang_noodle` | STANDARD | YES | YES |
| Zhajiang Noodles Combo | `e8188b13-132d-48af-a981-a3dd1476ee01` | MAP | `zha_jiang_noodle` | COMBO_ROOT | YES | YES |

## UNMAPPED_ROOTS_AFTER

无（0）。

## Write / read-back evidence

现有PUT mapping接口会调用remapStore并唤醒旧held订单，因此本轮采用经过Agent6审查的[固定单次事务](../../deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py)。只INSERT缺失36个ITEM及1条审计记录；5个既有ITEM不变；不修改订单/打印任务/历史快照/绑定。失败事务回滚。Agent6 ACCEPT，P0/P1/P2=0。

- SQL SHA256：`5ca6fee8032a1f734f3e7e85f1ba7c61815e1dc931987a26c61a9545e6e9ce4a`。
- 写前私密DB备份：`/srv/restaurant-pos/staging/state/uber-all-roots-20261002.dump`，1,140,126 bytes，pg_restore listing PASS。
- 精确读回集合：41个稳定ID/local SKU/mode与人工批准manifest完全一致，8个COMBO_ROOT；不是只检查总数。
- 其他binding及全部modifier行hash不变；旧held单5/9、历史order_items、binding行hash不变；orders总数82和print_jobs总数207不变。
- Staging/Production backend、nginx、DB容器身份与配置指纹不变；无重启、Flyway或应用部署。Staging仍为500aacd991fddbc8f224174fc65db833e60d7848/V33。
