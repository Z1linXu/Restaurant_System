# Uber Test Store 与 Staging Store 1 完整菜单对照 — 2026-10-02

这是人工确认稿。Confirmed 全部留空；本轮没有保存 mapping、改变菜单/价格、创建订单或操作 Production。

## 数据来源与口径

新鲜采集：**2026-10-02T15:43:24.271505+00:00**。TEST OAuth `eats.store` HTTP 200；`GET https://test-api.uber.com/v2/eats/stores/bd993244-5589-4b19-8f0d-dc2ba73d4273/menus` HTTP 200。本地直接读取 Staging DB Store 1，事务 `REPEATABLE READ READ ONLY`。运行 backend `21826ea5b2dc9297dd98bc8be47f5223446d11c6`。正式 mapping 共 0 条。
Uber 未返回菜单版本、ETag、Last-Modified；不推断更新时间。菜单价格整数按 /100 展示，保留两位小数；响应未单列 currency，按 Montreal 店铺语境解释为 CAD，不据此执行金额对账。[Uber Menu API 字段定义](https://developer.uber.com/docs/eats/references/api/v2/get-eats-stores-storeid-menu)。价格表是未确认候选之间的数值比较，不代表身份已经确认。

本地 39 items（35 active、4 inactive），385 option 行（332 active、53 inactive）；332 中 7 条属于 inactive item，active item 下实际 325。此前 catalog 270 不是 DB 全量：排除了 55 条 COMBO_EGG/COMBO_SIDE 历史行；运行组件来自 store_combo_components。此报告同时展示 DB 原始行和虚拟组件身份。

## A. Uber Menu Inventory

### Menu Menu — `167b2993-6aa7-454d-ab6e-afdfd666dbe8`

invisible=False; Category IDs: `["6748f5a2-a808-43c3-b882-9840007093db","78422a1e-1d6b-4c80-a1aa-cc50b782e306","acd22f7f-d3d2-46e0-a1b1-701c8ec0b5cc","4495449f-1cd0-4fd0-a9e6-00c5e08de644","Alcohol"]`。营业时段按原始 API（响应未单列时区）：

| Day | Periods |
| --- | --- |
| monday | [{"start_time":"11:30","end_time":"21:45"}] |
| tuesday | [{"start_time":"11:30","end_time":"21:45"}] |
| wednesday | [{"start_time":"11:30","end_time":"21:45"}] |
| thursday | [{"start_time":"11:30","end_time":"21:45"}] |
| friday | [{"start_time":"11:30","end_time":"22:15"}] |
| saturday | [{"start_time":"11:30","end_time":"22:15"}] |
| sunday | [{"start_time":"11:30","end_time":"21:45"}] |

### Category: Alcohol — `Alcohol`

- **Sayuri Nigori Sake** — 23.00；Uber ID `Nigori_Sake`
  - Description: EMPTY
  - Stable fields: `{"id":"Nigori_Sake"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `{"selling_options":[{"sold_by_unit":{"measurement_type":"MEASUREMENT_TYPE_COUNT"}}]}`。
- **Hakutsuru Junmai Ginjo Sake** — 23.00；Uber ID `Hakutsuru_Junmai_Gin`
  - Description: EMPTY
  - Stable fields: `{"id":"Hakutsuru_Junmai_Gin"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Lychee Soju** — 29.00；Uber ID `Good_Day_Lychee_Soju`
  - Description: EMPTY
  - Stable fields: `{"id":"Good_Day_Lychee_Soju"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Peach Soju** — 29.00；Uber ID `Peach_Soju`
  - Description: EMPTY
  - Stable fields: `{"id":"Peach_Soju"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Melon Soju** — 29.00；Uber ID `Melon_Soju`
  - Description: EMPTY
  - Stable fields: `{"id":"Melon_Soju"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Green Grape Soju** — 29.00；Uber ID `Green_Grape_Soju`
  - Description: EMPTY
  - Stable fields: `{"id":"Green_Grape_Soju"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Original Soju** — 25.00；Uber ID `Original_Soju`
  - Description: EMPTY
  - Stable fields: `{"id":"Original_Soju"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Sapporo Beer** — 14.50；Uber ID `Sapporo_Beer`
  - Description: EMPTY
  - Stable fields: `{"id":"Sapporo_Beer"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Tsingtao Beer** — 13.00；Uber ID `Tsingtao_Beer`
  - Description: EMPTY
  - Stable fields: `{"id":"Tsingtao_Beer"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。

### Category: Ramen Set Meal — `6748f5a2-a808-43c3-b882-9840007093db`

- **Beef Lanzhou Noodles Special Combo** — 24.99；Uber ID `eca7fbf3-a666-454b-b36e-7d380e80b49d`
  - Description: Savoury beef noodles served with a rich broth and various toppings.
  - Stable fields: `{"id":"eca7fbf3-a666-454b-b36e-7d380e80b49d","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Size `ddf85f09-ade3-406a-8d4a-5fdb25abae50`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Side Dishes `1083b73c-aba1-4bbd-b48c-c3b15e757313`; Tea Boil Egg `70c64ebd-81ea-4c7c-9e57-b73885fe787e`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`; Extra Topping `266df6ed-5d9d-4b05-94b2-7c86884866a3`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Zhajiang Noodles Combo** — 25.99；Uber ID `e8188b13-132d-48af-a981-a3dd1476ee01`
  - Description: Savoury noodles served with a rich Zhajiang sauce.
  - Stable fields: `{"id":"e8188b13-132d-48af-a981-a3dd1476ee01","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Side Dishes `1083b73c-aba1-4bbd-b48c-c3b15e757313`; Tea Boil Egg `70c64ebd-81ea-4c7c-9e57-b73885fe787e`; Extra Topping `552ab8e7-81c9-4f99-b5ec-637c4c70aa77`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Noodles with Vegetables Combo** — 24.99；Uber ID `e72231fb-9029-4539-91d6-b6efbeeb3767`
  - Description: Noodles served with a variety of vegetables.
  - Stable fields: `{"id":"e72231fb-9029-4539-91d6-b6efbeeb3767","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Size `ddf85f09-ade3-406a-8d4a-5fdb25abae50`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Side Dishes `1083b73c-aba1-4bbd-b48c-c3b15e757313`; Tea Boil Egg `70c64ebd-81ea-4c7c-9e57-b73885fe787e`; Extra Topping `266df6ed-5d9d-4b05-94b2-7c86884866a3`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Dandan Noodle Combo (With Peanuts)** — 25.99；Uber ID `87bbecca-85fa-4681-a3dd-06c8bd97d70a`
  - Description: If you have an allergy to peanuts, please be sure to make a note when placing an order.
  - Stable fields: `{"id":"87bbecca-85fa-4681-a3dd-06c8bd97d70a","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Side Dishes `1083b73c-aba1-4bbd-b48c-c3b15e757313`; Tea Boil Egg `70c64ebd-81ea-4c7c-9e57-b73885fe787e`; Extra Topping `552ab8e7-81c9-4f99-b5ec-637c4c70aa77`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Lanzhou Beef Chow Mein Combo** — 26.99；Uber ID `b3f90251-5066-42c4-bb4c-9a1581a38936`
  - Description: Tender beef and noodles in a savory broth.
  - Stable fields: `{"id":"b3f90251-5066-42c4-bb4c-9a1581a38936","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Side Dishes `1083b73c-aba1-4bbd-b48c-c3b15e757313`; Fried Egg `ecfa104f-042b-48d2-88a8-4955f11b381e`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Chicken Chow Mein Combo** — 26.99；Uber ID `c169f422-a9cc-4758-82a9-0f60eef89b68`
  - Description: Stir-fried noodles with chicken and vegetables.
  - Stable fields: `{"id":"c169f422-a9cc-4758-82a9-0f60eef89b68","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Side Dishes `1083b73c-aba1-4bbd-b48c-c3b15e757313`; Fried Egg `ecfa104f-042b-48d2-88a8-4955f11b381e`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Vegetable Chow Mein Combo** — 26.99；Uber ID `78b60d9c-29fc-4064-9b10-10dd11427df5`
  - Description: Stir-fried noodles with mixed vegetables.
  - Stable fields: `{"id":"78b60d9c-29fc-4064-9b10-10dd11427df5","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Side Dishes `1083b73c-aba1-4bbd-b48c-c3b15e757313`; Fried Egg `ecfa104f-042b-48d2-88a8-4955f11b381e`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Stir-fried Tomato Beef Noodle Combo** — 26.99；Uber ID `e45ddd3e-dbf5-449d-8ab6-badbccd4897b`
  - Description: Tender beef and tomatoes served with noodles.
  - Stable fields: `{"id":"e45ddd3e-dbf5-449d-8ab6-badbccd4897b","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Side Dishes `1083b73c-aba1-4bbd-b48c-c3b15e757313`; Fried Egg `ecfa104f-042b-48d2-88a8-4955f11b381e`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。

### Category: Salades — `78422a1e-1d6b-4c80-a1aa-cc50b782e306`

- **Tea Corned Egg** — 2.50；Uber ID `e8e7be55-3139-4949-8a7e-69a1d52161c5`
  - Description: EMPTY
  - Stable fields: `{"id":"e8e7be55-3139-4949-8a7e-69a1d52161c5","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Homemade Lanzhou Beef (With Peanuts)** — 11.99；Uber ID `71020dd7-1dbc-4711-9852-dcd1191d5eaa`
  - Description: If you have an allergy to peanuts, please be sure to make a note when placing an order.
  - Stable fields: `{"id":"71020dd7-1dbc-4711-9852-dcd1191d5eaa","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Edamame With Preserved Vegetable** — 6.99；Uber ID `2155b250-58f2-49d2-a971-d149d8a61386`
  - Description: Edamame paired with preserved vegetables.
  - Stable fields: `{"id":"2155b250-58f2-49d2-a971-d149d8a61386","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Sweet & Sour Mini Fries** — 6.99；Uber ID `540c3a0b-3830-4bc6-b706-14638c7e6352`
  - Description: Crispy fries tossed in a sweet and sour sauce.
  - Stable fields: `{"id":"540c3a0b-3830-4bc6-b706-14638c7e6352","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Cucumber Mix With Home Made Spicy Sauce** — 6.99；Uber ID `0cbf47bd-5d4c-456a-900e-7da7f6f8f54b`
  - Description: Fresh cucumber mixed with a zesty homemade spicy sauce.
  - Stable fields: `{"id":"0cbf47bd-5d4c-456a-900e-7da7f6f8f54b","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Rouleaux de printemps frits (3 pcs)** — 7.99；Uber ID `Rouleaux_de_printemp`
  - Description: 3 pieces
  - Stable fields: `{"id":"Rouleaux_de_printemp","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Crispy Tempura Shrimp (4pcs)** — 10.99；Uber ID `Crispy_Tempura_Shrim`
  - Description: EMPTY
  - Stable fields: `{"id":"Crispy_Tempura_Shrim","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Fried Chinese Steamed buns (3pcs)** — 7.99；Uber ID `Fried_Chinese_Steame`
  - Description: EMPTY
  - Stable fields: `{"id":"Fried_Chinese_Steame","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Fried Wontons (6 pcs)** — 7.99；Uber ID `Fried_Wontons_(6_pcs`
  - Description: EMPTY
  - Stable fields: `{"id":"Fried_Wontons_(6_pcs","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。

### Category: Ramen — `acd22f7f-d3d2-46e0-a1b1-701c8ec0b5cc`

- **Traditional Lanzhou Hand-pull Beef Noodle** — 19.99；Uber ID `b940caa7-6e37-4b3b-b964-3e10151e7903`
  - Description: Tender beef and noodles in a rich, savory broth.
  - Stable fields: `{"id":"b940caa7-6e37-4b3b-b964-3e10151e7903","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Size `ddf85f09-ade3-406a-8d4a-5fdb25abae50`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Extra Topping `266df6ed-5d9d-4b05-94b2-7c86884866a3`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Zha Jiang Noodle** — 20.99；Uber ID `41989752-4e09-4fc5-8d22-96e9427173eb`
  - Description: Traditional Chinese noodles served in a savory sauce.
  - Stable fields: `{"id":"41989752-4e09-4fc5-8d22-96e9427173eb","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Extra Topping `552ab8e7-81c9-4f99-b5ec-637c4c70aa77`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Dandan Noodles (With Peanuts)** — 20.99；Uber ID `2be2f7be-aa39-4742-bf91-edc9759f19c0`
  - Description: Spicy noodles tossed with peanuts.
  - Stable fields: `{"id":"2be2f7be-aa39-4742-bf91-edc9759f19c0","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Extra Topping `552ab8e7-81c9-4f99-b5ec-637c4c70aa77`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Noodle with Vegetables** — 19.99；Uber ID `3b8b2764-d492-47e0-9977-107ed4e67d1f`
  - Description: EMPTY
  - Stable fields: `{"id":"3b8b2764-d492-47e0-9977-107ed4e67d1f","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Size `ddf85f09-ade3-406a-8d4a-5fdb25abae50`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Soup Base `00c3e999-ba01-4cbb-91b3-bc4237bd5ed7`; Extra Topping `266df6ed-5d9d-4b05-94b2-7c86884866a3`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Braised Beef Tendon in Brown Sauce with Noodles** — 19.99；Uber ID `Braised_Beef_Tendon_`
  - Description: EMPTY
  - Stable fields: `{"id":"Braised_Beef_Tendon_"}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Size `ddf85f09-ade3-406a-8d4a-5fdb25abae50`; Noodle Size Option `04eda3d3-627c-405b-9e7c-8bde8061d5c6`; Extra Topping `266df6ed-5d9d-4b05-94b2-7c86884866a3`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: SUSPENDED until 8640000000；raw suspension `{"suspension":{"suspend_until":8640000000},"overrides":[]}`；selling `null`。
- **Lanzhou Beef Chow Mein (Beef)** — 21.99；Uber ID `9c8260ac-717a-4d20-80b5-aeef17613186`
  - Description: EMPTY
  - Stable fields: `{"id":"9c8260ac-717a-4d20-80b5-aeef17613186","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Add Egg `f90e0f1c-5ddb-4f09-a637-7658c84571ac`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Chicken Chow Mein** — 21.99；Uber ID `d2a77967-74f3-4dfb-9e8e-a236369bb4f6`
  - Description: Stir-fried noodles with chicken and vegetables.
  - Stable fields: `{"id":"d2a77967-74f3-4dfb-9e8e-a236369bb4f6","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Add Egg `f90e0f1c-5ddb-4f09-a637-7658c84571ac`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Stir-fried Tomato Beef Noodle (Beef)** — 21.99；Uber ID `b11777c1-9d50-44fe-8397-040a5056152b`
  - Description: Tender beef and tomatoes served over noodles.
  - Stable fields: `{"id":"b11777c1-9d50-44fe-8397-040a5056152b","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Add Egg `f90e0f1c-5ddb-4f09-a637-7658c84571ac`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Vegetable Chow Mein** — 21.99；Uber ID `842d5987-6945-42ef-a1d9-ed15cad18a0b`
  - Description: Stir-fried noodles with mixed vegetables.
  - Stable fields: `{"id":"842d5987-6945-42ef-a1d9-ed15cad18a0b","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: Spicy Level `1378d24d-9096-460c-868d-cc7c520178de`; Add Egg `f90e0f1c-5ddb-4f09-a637-7658c84571ac`; Ingredients Option `00301352-8fe2-4c03-91c4-229a32a5acfc`
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。

### Category: Soda — `4495449f-1cd0-4fd0-a9e6-00c5e08de644`

- **Coke** — 3.00；Uber ID `4c7162f6-1a21-4545-8eb5-6c49b01af0fd`
  - Description: EMPTY
  - Stable fields: `{"id":"4c7162f6-1a21-4545-8eb5-6c49b01af0fd","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Diet Coke** — 3.00；Uber ID `8c3c9093-c5fb-4d6d-ac0c-140451e9f29a`
  - Description: EMPTY
  - Stable fields: `{"id":"8c3c9093-c5fb-4d6d-ac0c-140451e9f29a","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Sprite** — 3.00；Uber ID `79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2`
  - Description: EMPTY
  - Stable fields: `{"id":"79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Ginger Ale** — 3.00；Uber ID `cbcd1dcd-87bb-4bd3-aa95-147817e134c8`
  - Description: A refreshing, crisp carbonated drink with a hint of ginger.
  - Stable fields: `{"id":"cbcd1dcd-87bb-4bd3-aa95-147817e134c8","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Ice Tea** — 3.00；Uber ID `7aad8f91-c26d-4c1d-8609-341f83396317`
  - Description: Refreshing beverage made with brewed tea.
  - Stable fields: `{"id":"7aad8f91-c26d-4c1d-8609-341f83396317","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。
- **Chinese Herbal Tea** — 3.00；Uber ID `745a90de-3e4b-444e-8de4-fa4cefc63eb0`
  - Description: EMPTY
  - Stable fields: `{"id":"745a90de-3e4b-444e-8de4-fa4cefc63eb0","external_data":""}`；merchant supplied/external_id 字段未返回。
  - Modifier groups: NONE
  - Availability: 无显式暂停；受 menu 营业时间限制，非实时可下单证明；raw suspension `{"overrides":[]}`；selling `null`。

### Uber Modifier Groups 与全部 modifier

#### Soup Base — `00c3e999-ba01-4cbb-91b3-bc4237bd5ed7`

external_data=''; min/max/required 原始规则 `{"quantity":{"min_permitted":1,"max_permitted":1},"overrides":[]}`。父菜：Noodle with Vegetables

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Meat Broth | {"id":"133d15f2-124d-43c6-9b2c-15352e25926b","external_data":""} | 0.00 | [] | {"quantity":{},"overrides":[]} | [] |
| Vegan Broth | {"id":"182b7ed4-d3c7-4aeb-9516-aa0c3959de97","external_data":""} | 0.00 | [] | {"quantity":{},"overrides":[]} | [] |

#### Size — `ddf85f09-ade3-406a-8d4a-5fdb25abae50`

external_data=''; min/max/required 原始规则 `{"quantity":{"min_permitted":1,"max_permitted":1},"overrides":[]}`。父菜：Braised Beef Tendon in Brown Sauce with Noodles, Noodle with Vegetables, Traditional Lanzhou Hand-pull Beef Noodle, Beef Lanzhou Noodles Special Combo, Noodles with Vegetables Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Regular | {"id":"94b90aa8-a673-4793-b7bd-917c837716df","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"ddf85f09-ade3-406a-8d4a-5fdb25abae50","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"ddf85f09-ade3-406a-8d4a-5fdb25abae50","quantity":{}}]} | [] |
| Large | {"id":"01c44198-eeb9-4452-bc2b-0939bf713a39","external_data":""} | 2.00 | [{"context_type":"MODIFIER_GROUP","context_value":"ddf85f09-ade3-406a-8d4a-5fdb25abae50","price":200}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"ddf85f09-ade3-406a-8d4a-5fdb25abae50","quantity":{}}]} | [] |

#### Spicy Level — `1378d24d-9096-460c-868d-cc7c520178de`

external_data=''; min/max/required 原始规则 `{"quantity":{"min_permitted":1,"max_permitted":1},"overrides":[]}`。父菜：Braised Beef Tendon in Brown Sauce with Noodles, Chicken Chow Mein, Dandan Noodles (With Peanuts), Lanzhou Beef Chow Mein (Beef), Noodle with Vegetables, Stir-fried Tomato Beef Noodle (Beef), Traditional Lanzhou Hand-pull Beef Noodle, Vegetable Chow Mein, Zha Jiang Noodle, Beef Lanzhou Noodles Special Combo, Chicken Chow Mein Combo, Dandan Noodle Combo (With Peanuts), Lanzhou Beef Chow Mein Combo, Noodles with Vegetables Combo, Stir-fried Tomato Beef Noodle Combo, Vegetable Chow Mein Combo, Zhajiang Noodles Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Non-spicy | {"id":"fa1544e9-4e8e-4a4f-81a1-8643e200028e","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"1378d24d-9096-460c-868d-cc7c520178de","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"1378d24d-9096-460c-868d-cc7c520178de","quantity":{}}]} | [] |
| Mild Spicy | {"id":"b4203d8e-6d06-47b9-ae76-ea74ef8ce015","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"1378d24d-9096-460c-868d-cc7c520178de","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"1378d24d-9096-460c-868d-cc7c520178de","quantity":{}}]} | [] |
| Regular Spicy | {"id":"a32414ff-bae3-4354-84a9-9099e6185509","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"1378d24d-9096-460c-868d-cc7c520178de","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"1378d24d-9096-460c-868d-cc7c520178de","quantity":{}}]} | [] |
| Extra Spicy | {"id":"ff8e0441-064e-411c-a877-59d8b9035c82","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"1378d24d-9096-460c-868d-cc7c520178de","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"1378d24d-9096-460c-868d-cc7c520178de","quantity":{}}]} | [] |

#### Extra Topping — `552ab8e7-81c9-4f99-b5ec-637c4c70aa77`

external_data=''; min/max/required 原始规则 `{"quantity":{"max_permitted":999},"overrides":[]}`。父菜：Dandan Noodles (With Peanuts), Zha Jiang Noodle, Dandan Noodle Combo (With Peanuts), Zhajiang Noodles Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Extra Noodle | {"id":"2ad33578-e7c6-4fa3-ac38-f4dff54a7eae","external_data":""} | 3.99 | [{"context_type":"MODIFIER_GROUP","context_value":"552ab8e7-81c9-4f99-b5ec-637c4c70aa77","price":399}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"552ab8e7-81c9-4f99-b5ec-637c4c70aa77","quantity":{"max_permitted":1}}]} | [] |
| Extra Vegetable | {"id":"a79813fd-2d14-45bd-a57c-462a8ddbb70f","external_data":""} | 3.99 | [{"context_type":"MODIFIER_GROUP","context_value":"552ab8e7-81c9-4f99-b5ec-637c4c70aa77","price":399}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"552ab8e7-81c9-4f99-b5ec-637c4c70aa77","quantity":{"max_permitted":1}}]} | [] |
| Extra Fried Egg | {"id":"329049e9-f054-4f0a-ad82-ccbd189c262d","external_data":""} | 2.50 | [{"context_type":"MODIFIER_GROUP","context_value":"552ab8e7-81c9-4f99-b5ec-637c4c70aa77","price":250}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"552ab8e7-81c9-4f99-b5ec-637c4c70aa77","quantity":{"max_permitted":1}}]} | [] |
| Extra Tea Boil Egg | {"id":"3e400404-e7da-4401-8f23-b91d74d7986e","external_data":""} | 2.50 | [{"context_type":"MODIFIER_GROUP","context_value":"552ab8e7-81c9-4f99-b5ec-637c4c70aa77","price":250}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"552ab8e7-81c9-4f99-b5ec-637c4c70aa77","quantity":{"max_permitted":1}}]} | [] |

#### Tea Boil Egg — `70c64ebd-81ea-4c7c-9e57-b73885fe787e`

external_data=''; min/max/required 原始规则 `{"quantity":{"min_permitted":1,"max_permitted":1},"overrides":[]}`。父菜：Beef Lanzhou Noodles Special Combo, Dandan Noodle Combo (With Peanuts), Noodles with Vegetables Combo, Zhajiang Noodles Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Tea Boil Egg | {"id":"61ad7ec7-2a69-4781-9210-b71c75084bad","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"70c64ebd-81ea-4c7c-9e57-b73885fe787e","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"70c64ebd-81ea-4c7c-9e57-b73885fe787e","quantity":{}}]} | [] |

#### Side Dishes — `1083b73c-aba1-4bbd-b48c-c3b15e757313`

external_data=''; min/max/required 原始规则 `{"quantity":{"min_permitted":1,"max_permitted":1},"overrides":[]}`。父菜：Beef Lanzhou Noodles Special Combo, Chicken Chow Mein Combo, Dandan Noodle Combo (With Peanuts), Lanzhou Beef Chow Mein Combo, Noodles with Vegetables Combo, Stir-fried Tomato Beef Noodle Combo, Vegetable Chow Mein Combo, Zhajiang Noodles Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Cucumber Mix With Home Made Spicy Sauce | {"id":"5209b4ae-9f45-44f2-9600-fb46c14e10ad","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"1083b73c-aba1-4bbd-b48c-c3b15e757313","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"1083b73c-aba1-4bbd-b48c-c3b15e757313","quantity":{}}]} | [] |
| Edamame With Preserved Vegetable | {"id":"b6c655e0-c49d-441b-aa9e-94e470d35404","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"1083b73c-aba1-4bbd-b48c-c3b15e757313","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"1083b73c-aba1-4bbd-b48c-c3b15e757313","quantity":{}}]} | [] |
| Sweet & Sour Mini Fries | {"id":"5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"1083b73c-aba1-4bbd-b48c-c3b15e757313","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"1083b73c-aba1-4bbd-b48c-c3b15e757313","quantity":{}}]} | [] |

#### Noodle Size Option — `04eda3d3-627c-405b-9e7c-8bde8061d5c6`

external_data=''; min/max/required 原始规则 `{"quantity":{"min_permitted":1,"max_permitted":1},"overrides":[]}`。父菜：Braised Beef Tendon in Brown Sauce with Noodles, Dandan Noodles (With Peanuts), Noodle with Vegetables, Traditional Lanzhou Hand-pull Beef Noodle, Zha Jiang Noodle, Beef Lanzhou Noodles Special Combo, Dandan Noodle Combo (With Peanuts), Noodles with Vegetables Combo, Zhajiang Noodles Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Fine | {"id":"c6a744c9-2d5f-4d1e-8e79-9deb3d917d03","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","quantity":{}}]} | [] |
| One Fine | {"id":"4c32e520-aca2-4a31-b7e5-ba3f05a83dde","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","quantity":{}}]} | [] |
| Two Fine | {"id":"9405ea5a-c77a-4596-a185-614ea81b7702","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","quantity":{}}]} | [] |
| Three Fine | {"id":"d9750fe4-14fe-4efd-bcd6-8ea85774850d","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","quantity":{}}]} | [] |
| Leek Leaves | {"id":"8d7a40e5-7f2e-40bc-896d-7759db7984ce","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","quantity":{}}]} | [] |
| Wide | {"id":"f5026fbe-eb02-4d84-bfe3-e336827cfce7","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","quantity":{}}]} | [] |
| Big Wide | {"id":"513c53d8-5464-47aa-9948-bda923fecdbd","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"04eda3d3-627c-405b-9e7c-8bde8061d5c6","quantity":{}}]} | [] |

#### Add Egg — `f90e0f1c-5ddb-4f09-a637-7658c84571ac`

external_data=''; min/max/required 原始规则 `{"quantity":{"max_permitted":999},"overrides":[]}`。父菜：Chicken Chow Mein, Lanzhou Beef Chow Mein (Beef), Stir-fried Tomato Beef Noodle (Beef), Vegetable Chow Mein

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Add Fried Egg | {"id":"43731428-8827-403a-b401-246ff779bef0","external_data":""} | 1.99 | [{"context_type":"MODIFIER_GROUP","context_value":"f90e0f1c-5ddb-4f09-a637-7658c84571ac","price":199}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"f90e0f1c-5ddb-4f09-a637-7658c84571ac","quantity":{}}]} | [] |
| Add Tea Boil Egg | {"id":"316bb70f-f2a4-4b44-9b7a-7ad73c5243d9","external_data":""} | 1.99 | [{"context_type":"MODIFIER_GROUP","context_value":"f90e0f1c-5ddb-4f09-a637-7658c84571ac","price":199}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"f90e0f1c-5ddb-4f09-a637-7658c84571ac","quantity":{}}]} | [] |

#### Ingredients Option — `00301352-8fe2-4c03-91c4-229a32a5acfc`

external_data=''; min/max/required 原始规则 `{"quantity":{"max_permitted":2},"overrides":[]}`。父菜：Braised Beef Tendon in Brown Sauce with Noodles, Chicken Chow Mein, Dandan Noodles (With Peanuts), Lanzhou Beef Chow Mein (Beef), Noodle with Vegetables, Stir-fried Tomato Beef Noodle (Beef), Traditional Lanzhou Hand-pull Beef Noodle, Vegetable Chow Mein, Zha Jiang Noodle, Beef Lanzhou Noodles Special Combo, Chicken Chow Mein Combo, Dandan Noodle Combo (With Peanuts), Lanzhou Beef Chow Mein Combo, Noodles with Vegetables Combo, Stir-fried Tomato Beef Noodle Combo, Vegetable Chow Mein Combo, Zhajiang Noodles Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Non Scallion/ Green Onion | {"id":"d1f4a19b-fd7c-4cdf-863a-581391172f23","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"00301352-8fe2-4c03-91c4-229a32a5acfc","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"00301352-8fe2-4c03-91c4-229a32a5acfc","quantity":{}}]} | [] |
| Non Coriander | {"id":"54c53703-db68-4044-85a3-5f4962715566","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"00301352-8fe2-4c03-91c4-229a32a5acfc","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"00301352-8fe2-4c03-91c4-229a32a5acfc","quantity":{}}]} | [] |

#### Fried Egg — `ecfa104f-042b-48d2-88a8-4955f11b381e`

external_data=''; min/max/required 原始规则 `{"quantity":{"min_permitted":1,"max_permitted":1},"overrides":[]}`。父菜：Chicken Chow Mein Combo, Lanzhou Beef Chow Mein Combo, Stir-fried Tomato Beef Noodle Combo, Vegetable Chow Mein Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Fried Egg | {"id":"8d6efb88-7974-4973-a681-263b7f186959","external_data":""} | 0.00 | [{"context_type":"MODIFIER_GROUP","context_value":"ecfa104f-042b-48d2-88a8-4955f11b381e","price":0}] | {"quantity":{},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"ecfa104f-042b-48d2-88a8-4955f11b381e","quantity":{}}]} | [] |

#### Extra Topping — `266df6ed-5d9d-4b05-94b2-7c86884866a3`

external_data=''; min/max/required 原始规则 `{"quantity":{"max_permitted":999},"overrides":[]}`。父菜：Braised Beef Tendon in Brown Sauce with Noodles, Noodle with Vegetables, Traditional Lanzhou Hand-pull Beef Noodle, Beef Lanzhou Noodles Special Combo, Noodles with Vegetables Combo

| Modifier | Stable fields | Price delta | Price overrides | Item quantity rules | Child groups |
| --- | --- | --- | --- | --- | --- |
| Extra Meat | {"id":"524842f3-7e5d-441f-813b-895f0491179f","external_data":""} | 6.99 | [{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","price":699}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","quantity":{"max_permitted":1}}]} | [] |
| Extra Fried Egg | {"id":"62c5d2f7-0f32-4cbe-8514-47060f55ea7d","external_data":""} | 2.50 | [{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","price":250}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","quantity":{"max_permitted":1}}]} | [] |
| Extra Tea Boil Egg | {"id":"cd3b4c00-5065-47ac-a8e8-a2772067ca16","external_data":""} | 2.50 | [{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","price":250}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","quantity":{"max_permitted":1}}]} | [] |
| Extra Vegetable | {"id":"2ca784f1-b086-42bb-aee0-64840d75cacf","external_data":""} | 3.99 | [{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","price":399}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","quantity":{"max_permitted":1}}]} | [] |
| Extra Noodle | {"id":"dddc88d0-5f52-4266-af87-ea7477d63f30","external_data":""} | 3.99 | [{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","price":399}] | {"quantity":{"max_permitted":1},"overrides":[{"context_type":"MODIFIER_GROUP","context_value":"266df6ed-5d9d-4b05-94b2-7c86884866a3","quantity":{"max_permitted":1}}]} | [] |

## B. Restaurant_System Menu Inventory

Store 1 / STG005_SRC_20260809_R01 / organization 1 / MOCK。以下包括停用记录，避免把历史记录误当可售菜。

### SOUP_NOODLE — 汤面 / Soup Noodle（id=1, active=True）

#### 传统牛肉面 / Traditional Beef Noodle — item 1

SKU `traditional_beef_noodle`；base_price 16.99；station `NOODLE` (id 1)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | noodle_capillary | NOODLE_TYPE / noodle_type | 毛细 / Capillary | 0.00 | — | 120 | True |
| 2 | noodle_thin | NOODLE_TYPE / noodle_type | 细 / Thin | 0.00 | — | 110 | True |
| 3 | noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 | — | 90 | True |
| 4 | noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 | — | 100 | True |
| 5 | noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 | — | 130 | True |
| 6 | noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 | — | 140 | True |
| 7 | noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 | — | 150 | True |
| 36 | tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 | — | 210 | True |
| 37 | extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 | — | 230 | True |
| 263 | — | None / remove | 不要葱 / No Onion | 0.00 | — | — | False |
| 264 | — | None / remove | 不要香菜 / No Cilantro | 0.00 | — | — | False |
| 265 | — | None / addon | 加肉 / Extra Meat | 6.99 | — | — | False |
| 266 | — | None / addon | 加蛋 / Extra Egg | 1.99 | — | — | False |
| 267 | — | None / spicy_level | 正常 / Regular | 0.00 | — | — | False |
| 268 | legacy_large_disabled_268 | SIZE / size | 大份 / Large | 2.00 | — | 1168 | False |
| 269 | legacy_regular_disabled_269 | SIZE / size | 标准份 / Regular | 0.00 | — | 1169 | False |
| 270 | remove_radish | REMOVE / remove | 走萝卜 / No Radish | 0.00 | — | 330 | True |
| 271 | less_noodle | REMOVE / remove | 少面 / Less Noodle | 0.00 | — | 320 | True |
| 272 | remove_noodle | REMOVE / remove | 走面 / No Noodle | 0.00 | — | 310 | True |
| 273 | remove_beef | REMOVE / remove | 走牛肉 / No Beef | 0.00 | — | 300 | True |
| 274 | remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 | — | 290 | True |
| 275 | remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 | — | 280 | True |
| 276 | extra_radish | ADD_ON / addon | 加萝卜 / Extra Radish | 3.00 | — | 270 | True |
| 277 | green_onion | ADD_ON / addon | 加葱 / Extra Green Onion | 0.00 | — | 260 | True |
| 278 | cilantro | ADD_ON / addon | 加香菜 / Extra Cilantro | 0.00 | — | 250 | True |
| 279 | bok_choy | ADD_ON / addon | 加上海青 / Extra Bok Choy | 3.00 | — | 240 | True |
| 280 | extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 | — | 220 | True |
| 281 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 200 | True |
| 282 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 190 | True |
| 283 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 180 | True |
| 284 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 170 | True |
| 285 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 160 | True |
| 286 | size_large | SIZE / size | 大碗 / Large | 2.00 | — | 30 | True |
| 287 | size_regular | SIZE / size | 中碗 / Regular | 0.00 | — | 10 | True |
| 288 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 289 | 60 | True |
| 289 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 290 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 291 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 292 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 20 | True |
| 293 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 10 | True |
| 294 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |
| 381 | size_small | SIZE / size | 小碗 / Small | -2.00 | — | 20 | False |

#### 红烧牛筋面 / Braised Beef Tendon Noodle — item 2

SKU `braised_beef_tendon_noodle`；base_price 17.99；station `NOODLE` (id 1)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 8 | noodle_capillary | NOODLE_TYPE / noodle_type | 毛细 / Capillary | 0.00 | — | 120 | True |
| 9 | noodle_thin | NOODLE_TYPE / noodle_type | 细 / Thin | 0.00 | — | 110 | True |
| 10 | noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 | — | 90 | True |
| 11 | noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 | — | 100 | True |
| 12 | noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 | — | 130 | True |
| 13 | noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 | — | 140 | True |
| 14 | noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 | — | 150 | True |
| 230 | — | None / remove | 不要葱 / No Onion | 0.00 | — | — | False |
| 231 | — | None / remove | 不要香菜 / No Cilantro | 0.00 | — | — | False |
| 232 | — | None / addon | 加蛋 / Extra Egg | 1.99 | — | — | False |
| 233 | — | None / spicy_level | 正常 / Regular | 0.00 | — | — | False |
| 234 | — | None / size | 大份 / Large | 2.00 | — | — | False |
| 235 | — | None / size | 标准份 / Regular | 0.00 | — | — | False |
| 236 | remove_radish | REMOVE / remove | 走萝卜 / No Radish | 0.00 | — | 330 | True |
| 237 | less_noodle | REMOVE / remove | 少面 / Less Noodle | 0.00 | — | 320 | True |
| 238 | remove_noodle | REMOVE / remove | 走面 / No Noodle | 0.00 | — | 310 | True |
| 239 | remove_beef | REMOVE / remove | 走牛筋 / No Beef | 0.00 | — | 300 | True |
| 240 | remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 | — | 290 | True |
| 241 | remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 | — | 280 | True |
| 242 | extra_radish | ADD_ON / addon | 加萝卜 / Extra Radish | 3.00 | — | 280 | True |
| 243 | green_onion | ADD_ON / addon | 加葱 / Extra Green Onion | 0.00 | — | 250 | True |
| 244 | cilantro | ADD_ON / addon | 加香菜 / Extra Cilantro | 0.00 | — | 240 | True |
| 245 | bok_choy | ADD_ON / addon | 加上海青 / Extra Bok Choy | 3.00 | — | 230 | True |
| 246 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 220 | True |
| 247 | extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 | — | 270 | True |
| 248 | tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 | — | 210 | True |
| 249 | extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 | — | 200 | True |
| 250 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 190 | True |
| 251 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 180 | True |
| 252 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 170 | True |
| 253 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 160 | True |
| 254 | size_large | SIZE / size | 大碗 / Large | 2.00 | — | 30 | True |
| 255 | size_regular | SIZE / size | 中碗 / Regular | 0.00 | — | 10 | True |
| 256 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 257 | 60 | False |
| 257 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 258 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 259 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 260 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 20 | True |
| 261 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 10 | True |
| 262 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | False |
| 382 | size_small | SIZE / size | 小碗 / Small | -2.00 | — | 20 | True |
| 3437 | addon_beef_tendons | ADD_ON / addon | 加牛筋 / addon_beef_tendons | 6.99 | — | 260 | True |
| 3438 | removebaicai | REMOVE / remove | 走上海青 / zoushanghaiqing | 0.00 | — | 340 | True |

#### 蔬菜面 / Vegetable Noodle — item 3

SKU `vegetable_noodle`；base_price 16.99；station `NOODLE` (id 1)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 15 | noodle_capillary | NOODLE_TYPE / noodle_type | 毛细 / Capillary | 0.00 | — | 120 | True |
| 16 | noodle_thin | NOODLE_TYPE / noodle_type | 细 / Thin | 0.00 | — | 110 | True |
| 17 | noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 | — | 90 | True |
| 18 | noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 | — | 100 | True |
| 19 | noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 | — | 130 | True |
| 20 | noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 | — | 140 | True |
| 21 | noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 | — | 150 | True |
| 197 | remove_carrot | REMOVE / remove | 走胡萝卜片 / No Carrot Slice | 0.00 | — | 390 | True |
| 198 | remove_seaweed | REMOVE / remove | 走海菜 / No Seaweed | 0.00 | — | 380 | True |
| 199 | remove_mushroom | REMOVE / remove | 走蘑菇 / No Mushroom | 0.00 | — | 370 | True |
| 200 | remove_corn | REMOVE / remove | 走玉米 / No Corn | 0.00 | — | 360 | True |
| 201 | remove_broccoli | REMOVE / remove | 走西兰花 / No Broccoli | 0.00 | — | 350 | True |
| 202 | remove_bok_choy | REMOVE / remove | 走上海青 / No Bok Choy | 0.00 | — | 340 | True |
| 203 | less_noodle | REMOVE / remove | 少面 / Less Noodle | 0.00 | — | 330 | True |
| 204 | remove_noodle | REMOVE / remove | 走面 / No Noodle | 0.00 | — | 320 | True |
| 205 | carrot_slice | ADD_ON / addon | 加胡萝卜片 / Extra Carrot Slice | 3.00 | — | 310 | True |
| 206 | mushroom | ADD_ON / addon | 加蘑菇 / Extra Mushroom | 3.00 | — | 300 | True |
| 207 | seaweed | ADD_ON / addon | 加海菜 / Extra Seaweed | 3.00 | — | 290 | True |
| 208 | corn | ADD_ON / addon | 加玉米 / Extra Corn | 3.00 | — | 280 | True |
| 209 | broccoli | ADD_ON / addon | 加西兰花 / Extra Broccoli | 3.00 | — | 270 | True |
| 210 | bok_choy | ADD_ON / addon | 加上海青 / Extra Bok Choy | 3.00 | — | 260 | True |
| 211 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 250 | True |
| 212 | extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 | — | 240 | True |
| 213 | tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 | — | 230 | True |
| 214 | extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 | — | 220 | True |
| 215 | soup_beef | SOUP_BASE / soup_base | 肉汤 / Beef Broth | 0.00 | — | 210 | True |
| 216 | soup_vegan | SOUP_BASE / soup_base | 素汤 / Vegan Broth | 0.00 | — | 200 | True |
| 217 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 190 | True |
| 218 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 180 | True |
| 219 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 170 | True |
| 220 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 160 | True |
| 221 | size_large | SIZE / size | 大碗 / Large | 2.00 | — | 80 | True |
| 222 | size_regular | SIZE / size | 中碗 / Regular | 0.00 | — | 70 | True |
| 223 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 224 | 60 | True |
| 224 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 225 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 226 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 227 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 20 | True |
| 228 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 10 | True |
| 229 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

#### 红烧牛肉面 / Braised Beef Noodle — item 25

SKU `braised_beef_noodle`；base_price 16.99；station `NOODLE` (id 1)；active=False；sold_out=True；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 369 | — | None / addon | 加肉 / Extra Meat | 6.99 | — | — | True |
| 370 | — | None / addon | 加蛋 / Extra Egg | 1.99 | — | — | True |
| 371 | — | None / noodle_type | 三细 / Sanxi | 0.00 | — | — | True |
| 372 | — | None / noodle_type | 二细 / Erxi | 0.00 | — | — | True |
| 373 | size_large | SIZE / size | 大碗 / Large | 2.00 | — | 30 | True |
| 374 | size_regular | SIZE / size | 中碗 / Regular | 0.00 | — | 10 | True |
| 383 | size_small | SIZE / size | 小碗 / Small | -2.00 | — | 20 | True |

#### 酸菜牛肉面 / Pickled Vegetable Beef Noodle — item 26

SKU `pickled_vegetable_beef_noodle`；base_price 17.99；station `NOODLE` (id 1)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 329 | — | None / remove | 不要葱 / No Onion | 0.00 | — | — | False |
| 330 | — | None / remove | 不要香菜 / No Cilantro | 0.00 | — | — | False |
| 331 | — | None / addon | 加蛋 / Extra Egg | 1.99 | — | — | False |
| 332 | — | None / spicy_level | 正常 / Regular | 0.00 | — | — | False |
| 333 | — | None / size | 大份 / Large | 2.00 | — | — | False |
| 334 | — | None / size | 标准份 / Regular | 0.00 | — | — | False |
| 335 | remove_radish | REMOVE / remove | 走萝卜 / No Radish | 0.00 | — | 330 | True |
| 336 | less_noodle | REMOVE / remove | 少面 / Less Noodle | 0.00 | — | 320 | True |
| 337 | remove_noodle | REMOVE / remove | 走面 / No Noodle | 0.00 | — | 310 | True |
| 338 | remove_beef | REMOVE / remove | 走牛肉 / No Beef | 0.00 | — | 300 | True |
| 339 | remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 | — | 290 | True |
| 340 | remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 | — | 280 | True |
| 341 | extra_radish | ADD_ON / addon | 加萝卜 / Extra Radish | 3.00 | — | 270 | True |
| 342 | green_onion | ADD_ON / addon | 加葱 / Extra Green Onion | 0.00 | — | 260 | True |
| 343 | cilantro | ADD_ON / addon | 加香菜 / Extra Cilantro | 0.00 | — | 250 | True |
| 344 | bok_choy | ADD_ON / addon | 加上海青 / Extra Bok Choy | 3.00 | — | 240 | True |
| 345 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 230 | True |
| 346 | extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 | — | 220 | True |
| 347 | tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 | — | 210 | True |
| 348 | extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 | — | 200 | True |
| 349 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 190 | True |
| 350 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 180 | True |
| 351 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 170 | True |
| 352 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 160 | True |
| 353 | noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 | — | 150 | True |
| 354 | noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 | — | 140 | True |
| 355 | noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 | — | 130 | True |
| 356 | noodle_capillary | NOODLE_TYPE / noodle_type | 毛细 / Capillary | 0.00 | — | 120 | True |
| 357 | noodle_thin | NOODLE_TYPE / noodle_type | 细 / Thin | 0.00 | — | 110 | True |
| 358 | noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 | — | 100 | True |
| 359 | noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 | — | 90 | True |
| 360 | size_large | SIZE / size | 大碗 / Large | 2.00 | — | 80 | True |
| 361 | size_regular | SIZE / size | 中碗 / Regular | 0.00 | — | 70 | True |
| 362 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 363 | 60 | True |
| 363 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 364 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 365 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 366 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 20 | True |
| 367 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 10 | True |
| 368 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

### FRIED_NOODLE — 炒面 / Stir-Fried Noodles（id=5, active=True）

#### 牛肉炒面 / Beef Chow Mein — item 14

SKU `beef_chow_mein`；base_price 18.99；station `WOK` (id 4)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=combo_fried_egg。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 115 | — | None / remove | 走包菜 / No Cabbage | 0.00 | — | — | False |
| 116 | — | None / remove | 走肉 / No Meat | 0.00 | — | — | False |
| 117 | — | None / addon | 加肉 / Extra Meat | 6.99 | — | — | False |
| 118 | — | None / addon | 加包菜 / Extra Cabbage | 1.20 | — | — | False |
| 119 | — | None / addon | 加西兰花 / Extra Broccoli | 1.20 | — | — | False |
| 120 | remove_tomato | REMOVE / remove | 走番茄 / No Tomato | 0.00 | — | 200 | True |
| 121 | remove_all_vegetables | REMOVE / remove | 走所有菜 / No Vegetables | 0.00 | — | 190 | True |
| 122 | remove_zucchini | REMOVE / remove | 走西葫芦 / No Zucchini | 0.00 | — | 180 | True |
| 123 | remove_cabbage | REMOVE / remove | 走大头菜 / No Cabbage | 0.00 | — | 170 | True |
| 124 | remove_broccoli | REMOVE / remove | 走西兰花 / No Broccoli | 0.00 | — | 160 | True |
| 125 | remove_green_pepper | REMOVE / remove | 走青椒 / No Green Pepper | 0.00 | — | 150 | True |
| 126 | remove_onion | REMOVE / remove | 走洋葱 / No Onion | 0.00 | — | 140 | True |
| 127 | remove_bean_sprouts | REMOVE / remove | 走豆芽 / No Bean Sprouts | 0.00 | — | 130 | True |
| 128 | tea_egg | ADD_ON / addon | 加卤蛋 / Extra Tea Egg | 1.99 | — | 120 | True |
| 129 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 110 | True |
| 130 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 100 | True |
| 131 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 90 | True |
| 132 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 80 | True |
| 133 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 70 | True |
| 134 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 135 | 60 | True |
| 135 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 136 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 137 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 138 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 20 | True |
| 139 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 10 | True |
| 140 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

#### 鸡肉炒面 / Chicken Chow Mein — item 15

SKU `chicken_chow_mein`；base_price 18.99；station `WOK` (id 4)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=combo_fried_egg。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 89 | — | None / remove | 走包菜 / No Cabbage | 0.00 | — | — | False |
| 90 | — | None / remove | 走肉 / No Meat | 0.00 | — | — | False |
| 91 | — | None / addon | 加肉 / Extra Meat | 6.99 | — | — | False |
| 92 | — | None / addon | 加包菜 / Extra Cabbage | 1.20 | — | — | False |
| 93 | — | None / addon | 加西兰花 / Extra Broccoli | 1.20 | — | — | False |
| 94 | remove_tomato | REMOVE / remove | 走番茄 / No Tomato | 0.00 | — | 200 | True |
| 95 | remove_all_vegetables | REMOVE / remove | 走所有菜 / No Vegetables | 0.00 | — | 190 | True |
| 96 | remove_zucchini | REMOVE / remove | 走西葫芦 / No Zucchini | 0.00 | — | 180 | True |
| 97 | remove_cabbage | REMOVE / remove | 走大头菜 / No Cabbage | 0.00 | — | 170 | True |
| 98 | remove_broccoli | REMOVE / remove | 走西兰花 / No Broccoli | 0.00 | — | 160 | True |
| 99 | remove_green_pepper | REMOVE / remove | 走青椒 / No Green Pepper | 0.00 | — | 150 | True |
| 100 | remove_onion | REMOVE / remove | 走洋葱 / No Onion | 0.00 | — | 140 | True |
| 101 | remove_bean_sprouts | REMOVE / remove | 走豆芽 / No Bean Sprouts | 0.00 | — | 130 | True |
| 102 | tea_egg | ADD_ON / addon | 加卤蛋 / Extra Tea Egg | 1.99 | — | 120 | True |
| 103 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 110 | True |
| 104 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 100 | True |
| 105 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 90 | True |
| 106 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 80 | True |
| 107 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 70 | True |
| 108 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 109 | 60 | True |
| 109 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 110 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 111 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 112 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 20 | True |
| 113 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 10 | True |
| 114 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

#### 番茄炒面 / Tomato Chow Mein — item 16

SKU `tomato_chow_mein`；base_price 18.99；station `WOK` (id 4)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=combo_fried_egg。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 63 | — | None / remove | 走包菜 / No Cabbage | 0.00 | — | — | False |
| 64 | — | None / remove | 走肉 / No Meat | 0.00 | — | — | False |
| 65 | — | None / addon | 加肉 / Extra Meat | 6.99 | — | — | False |
| 66 | — | None / addon | 加包菜 / Extra Cabbage | 1.20 | — | — | False |
| 67 | — | None / addon | 加西兰花 / Extra Broccoli | 1.20 | — | — | False |
| 68 | remove_tomato | REMOVE / remove | 走番茄 / No Tomato | 0.00 | — | 200 | True |
| 69 | remove_all_vegetables | REMOVE / remove | 走所有菜 / No Vegetables | 0.00 | — | 190 | True |
| 70 | remove_zucchini | REMOVE / remove | 走西葫芦 / No Zucchini | 0.00 | — | 180 | True |
| 71 | remove_cabbage | REMOVE / remove | 走大头菜 / No Cabbage | 0.00 | — | 170 | True |
| 72 | remove_broccoli | REMOVE / remove | 走西兰花 / No Broccoli | 0.00 | — | 160 | True |
| 73 | remove_green_pepper | REMOVE / remove | 走青椒 / No Green Pepper | 0.00 | — | 150 | True |
| 74 | remove_onion | REMOVE / remove | 走洋葱 / No Onion | 0.00 | — | 140 | True |
| 75 | remove_bean_sprouts | REMOVE / remove | 走豆芽 / No Bean Sprouts | 0.00 | — | 130 | True |
| 76 | tea_egg | ADD_ON / addon | 加卤蛋 / Extra Tea Egg | 1.99 | — | 120 | True |
| 77 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 110 | True |
| 78 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 100 | True |
| 79 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 90 | True |
| 80 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 80 | True |
| 81 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 70 | True |
| 82 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 83 | 60 | True |
| 83 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 84 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 85 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 86 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 20 | True |
| 87 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 10 | True |
| 88 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

#### 素菜炒面 / Vegetable Chow Mein — item 17

SKU `vegetable_chow_mein`；base_price 18.99；station `WOK` (id 4)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=combo_fried_egg。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 39 | — | None / remove | 走包菜 / No Cabbage | 0.00 | — | — | False |
| 40 | — | None / addon | 加包菜 / Extra Cabbage | 1.20 | — | — | False |
| 41 | — | None / addon | 加西兰花 / Extra Broccoli | 1.20 | — | — | False |
| 42 | remove_tomato | REMOVE / remove | 走番茄 / No Tomato | 0.00 | — | 200 | True |
| 43 | remove_all_vegetables | REMOVE / remove | 走所有菜 / No Vegetables | 0.00 | — | 190 | True |
| 44 | remove_zucchini | REMOVE / remove | 走西葫芦 / No Zucchini | 0.00 | — | 180 | True |
| 45 | remove_cabbage | REMOVE / remove | 走大头菜 / No Cabbage | 0.00 | — | 170 | True |
| 46 | remove_broccoli | REMOVE / remove | 走西兰花 / No Broccoli | 0.00 | — | 160 | True |
| 47 | remove_green_pepper | REMOVE / remove | 走青椒 / No Green Pepper | 0.00 | — | 150 | True |
| 48 | remove_onion | REMOVE / remove | 走洋葱 / No Onion | 0.00 | — | 140 | True |
| 49 | remove_bean_sprouts | REMOVE / remove | 走豆芽 / No Bean Sprouts | 0.00 | — | 130 | True |
| 50 | tea_egg | ADD_ON / addon | 加卤蛋 / Extra Tea Egg | 1.99 | — | 120 | True |
| 51 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 110 | True |
| 52 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 100 | True |
| 53 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 90 | True |
| 54 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 80 | True |
| 55 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 70 | True |
| 56 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 57 | 60 | True |
| 57 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 58 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 59 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 60 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 20 | True |
| 61 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 10 | True |
| 62 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

#### 煎蛋 / Fried Egg — item 23

SKU `fried_egg`；base_price 0.00；station `DEEPFRIED` (id 5)；active=False；sold_out=True；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

### DRY_NOODLE — 拌面 / Mixed / Dry / Cold Noodles（id=2, active=True）

#### 担担面 / Dan Dan Noodle — item 4

SKU `dan_dan_noodle`；base_price 17.99；station `NOODLE` (id 1)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 22 | noodle_capillary | NOODLE_TYPE / noodle_type | 毛细 / Capillary | 0.00 | — | 100 | True |
| 23 | noodle_thin | NOODLE_TYPE / noodle_type | 细 / Thin | 0.00 | — | 90 | True |
| 24 | noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 | — | 70 | True |
| 25 | noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 | — | 80 | True |
| 26 | noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 | — | 110 | True |
| 27 | noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 | — | 120 | True |
| 28 | noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 | — | 130 | True |
| 141 | — | None / remove | 不要葱 / No Onion | 0.00 | — | — | False |
| 142 | — | None / remove | 不要香菜 / No Cilantro | 0.00 | — | — | False |
| 143 | — | None / addon | 加蛋 / Extra Egg | 1.99 | — | — | False |
| 144 | — | None / size | 标准份 / Regular | 0.00 | — | — | False |
| 145 | remove_bok_choy | REMOVE / remove | 走上海青 / No Bok Choy | 0.00 | — | 290 | True |
| 146 | remove_peanut | REMOVE / remove | 走花生 / No Peanut | 0.00 | — | 280 | True |
| 147 | remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 | — | 270 | True |
| 148 | remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 | — | 260 | True |
| 149 | green_onion | ADD_ON / addon | 加葱 / Extra Green Onion | 0.00 | — | 250 | True |
| 150 | cilantro | ADD_ON / addon | 加香菜 / Extra Cilantro | 0.00 | — | 240 | True |
| 151 | extra_sauce | ADD_ON / addon | 加酱 / Extra Sauce | 3.00 | — | 230 | True |
| 152 | bok_choy | ADD_ON / addon | 加上海青 / Extra Bok Choy | 3.00 | — | 220 | True |
| 153 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 210 | True |
| 154 | extra_meat | ADD_ON / addon | 加肉 / Extra Meat | 6.99 | — | 200 | True |
| 155 | tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 | — | 190 | True |
| 156 | extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 | — | 180 | True |
| 157 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 170 | True |
| 158 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 160 | True |
| 159 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 150 | True |
| 160 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 140 | True |
| 161 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 162 | 60 | True |
| 162 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 163 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 164 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 165 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 20 | True |
| 166 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 10 | True |
| 167 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

#### 炸酱面 / Zha Jiang Noodle — item 5

SKU `zha_jiang_noodle`；base_price 17.99；station `NOODLE` (id 1)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 29 | noodle_capillary | NOODLE_TYPE / noodle_type | 毛细 / Capillary | 0.00 | — | 110 | True |
| 30 | noodle_thin | NOODLE_TYPE / noodle_type | 细 / Thin | 0.00 | — | 100 | True |
| 31 | noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 | — | 90 | True |
| 32 | noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 | — | 80 | True |
| 33 | noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 | — | 70 | True |
| 34 | noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 | — | 120 | True |
| 35 | noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 | — | 130 | True |
| 168 | — | None / remove | 不要葱 / No Onion | 0.00 | — | — | False |
| 169 | — | None / remove | 不要香菜 / No Cilantro | 0.00 | — | — | False |
| 170 | — | None / addon | 加蛋 / Extra Egg | 1.99 | — | — | False |
| 171 | — | None / size | 标准份 / Regular | 0.00 | — | — | False |
| 172 | remove_bok_choy | REMOVE / remove | 走上海青 / No Bok Choy | 0.00 | — | 310 | True |
| 173 | remove_edamame | REMOVE / remove | 走毛豆 / No Edamame | 0.00 | — | 300 | True |
| 174 | remove_cucumber | REMOVE / remove | 走黄瓜 / No Cucumber | 0.00 | — | 290 | True |
| 175 | remove_carrot | REMOVE / remove | 走胡萝卜 / No Carrot | 0.00 | — | 280 | True |
| 176 | remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 | — | 270 | True |
| 177 | remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 | — | 260 | True |
| 178 | green_onion | ADD_ON / addon | 加葱 / Extra Green Onion | 0.00 | — | 250 | True |
| 179 | cilantro | ADD_ON / addon | 加香菜 / Extra Cilantro | 0.00 | — | 240 | True |
| 180 | extra_sauce | ADD_ON / addon | 加酱 / Extra Sauce | 3.00 | — | 230 | True |
| 181 | bok_choy | ADD_ON / addon | 加上海青 / Extra Bok Choy | 3.00 | — | 220 | True |
| 182 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 210 | True |
| 183 | extra_meat | ADD_ON / addon | 加肉 / Extra Meat | 6.99 | — | 200 | True |
| 184 | tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 | — | 190 | True |
| 185 | extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 | — | 180 | True |
| 186 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 170 | True |
| 187 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 160 | True |
| 188 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 150 | True |
| 189 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 140 | True |
| 190 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 191 | 60 | True |
| 191 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 192 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 193 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 194 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 20 | True |
| 195 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 10 | True |
| 196 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

#### 鸡丝凉面 / Cold Noodle with Shredded Chicken — item 24

SKU `cold_noodle_shredded_chicken`；base_price 17.99；station `NOODLE` (id 1)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 295 | — | None / remove | 不要葱 / No Onion | 0.00 | — | — | False |
| 296 | — | None / remove | 不要香菜 / No Cilantro | 0.00 | — | — | False |
| 297 | — | None / addon | 加蛋 / Extra Egg | 1.99 | — | — | False |
| 298 | — | None / size | 标准份 / Regular | 0.00 | — | — | False |
| 299 | remove_cucumber | REMOVE / remove | 走黄瓜丝 / remove cucumber | 0.00 | — | 290 | True |
| 300 | remove_carrot | REMOVE / remove | 走胡萝卜 / No Carrot | 0.00 | — | 280 | True |
| 301 | remove_peanut | REMOVE / remove | 走花生 / No Peanut | 0.00 | — | 270 | True |
| 302 | remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 | — | 260 | True |
| 303 | remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 | — | 250 | True |
| 304 | green_onion | ADD_ON / addon | 加葱 / Extra Green Onion | 0.00 | — | 240 | True |
| 305 | cilantro | ADD_ON / addon | 加香菜 / Extra Cilantro | 0.00 | — | 230 | True |
| 306 | bok_choy | ADD_ON / addon | 加上海青 / Extra Bok Choy | 3.00 | — | 220 | False |
| 307 | fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 | — | 210 | True |
| 308 | extra_meat | ADD_ON / addon | 加肉 / Extra Meat | 6.99 | — | 200 | True |
| 309 | tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 | — | 190 | True |
| 310 | extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 | — | 180 | True |
| 311 | spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 | — | 170 | True |
| 312 | spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 | — | 160 | True |
| 313 | spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 | — | 150 | True |
| 314 | spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 | — | 140 | True |
| 315 | noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 | — | 70 | True |
| 316 | combo_cucumber_no_peanut | COMBO_SIDE_REMOVE / remove | 走花生 / No Peanut | 0.00 | 319 | 60 | True |
| 317 | noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 | — | 60 | True |
| 318 | noodle_capillary | NOODLE_TYPE / noodle_type | 毛细 / Capillary | 0.00 | — | 50 | True |
| 319 | combo_cucumber_salad | COMBO_SIDE / addon | 套餐拌黄瓜 / Combo Cucumber Salad | 0.00 | — | 50 | True |
| 320 | combo_shredded_potato | COMBO_SIDE / addon | 套餐土豆丝 / Combo Shredded Potato | 0.00 | — | 40 | True |
| 321 | noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 | — | 40 | True |
| 322 | combo_edamame | COMBO_SIDE / addon | 套餐毛豆 / Combo Edamame | 0.00 | — | 30 | True |
| 323 | noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 | — | 30 | True |
| 324 | noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 | — | 20 | True |
| 325 | combo_fried_egg | COMBO_EGG / addon | 套餐煎蛋 / Combo Fried Egg | 0.00 | — | 20 | True |
| 326 | noodle_thin | NOODLE_TYPE / noodle_type | 细 / Thin | 0.00 | — | 10 | True |
| 327 | combo_tea_egg | COMBO_EGG / addon | 套餐卤蛋 / Combo Tea Egg | 0.00 | — | 10 | True |
| 328 | combo | COMBO / addon | 套餐 / Combo | 5.00 | — | 0 | True |

### SIDE — 小菜 / Side Dishes（id=3, active=True）

#### 拌牛展 / Braised Beef Shank Salad — item 6

SKU `braised_beef_shank_salad`；base_price 9.99；station `COLD` (id 2)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 375 | remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 | — | 20 | True |
| 376 | remove_crushed_peanut | REMOVE / remove | 走花生碎 / No Crushed Peanut | 0.00 | — | 10 | True |
| 377 | remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 | — | 0 | True |

#### 拌黄瓜 / Cucumber Salad — item 7

SKU `cucumber_salad`；base_price 4.99；station `COLD` (id 2)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 38 | remove_peanut | REMOVE / remove | 走花生 / No Peanut | 0.00 | — | 0 | True |

#### 毛豆 / Edamame — item 8

SKU `edamame`；base_price 4.99；station `COLD` (id 2)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 土豆丝 / Shredded Potato — item 9

SKU `shredded_potato`；base_price 4.99；station `COLD` (id 2)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

| Option ID | Code | Group / Type | 中文 / English | Delta | Parent ID | Sort | Active |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 378 | remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 | — | 20 | True |
| 379 | remove_peanut | REMOVE / remove | 走花生 / No Peanut | 0.00 | — | 10 | True |
| 380 | remove_onion | REMOVE / remove | 走洋葱 / No Onion | 0.00 | — | 0 | True |

#### 煎蛋 / Fried Egg — item 18

SKU `fried_egg`；base_price 1.99；station `DEEPFRIED` (id 5)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 茶叶蛋 / Tea Egg — item 27

SKU `tea_egg`；base_price 1.99；station `COLD` (id 2)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 纯牛肉 / pure_beef — item 28

SKU `beef`；base_price 6.99；station `COLD` (id 2)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 纯牛筋 / pure_tendon — item 29

SKU `tendon`；base_price 6.99；station `COLD` (id 2)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

### FRIED — 炸物 / Fried Items（id=6, active=True）

#### 炸春卷 / Fried Spring Rolls — item 19

SKU `fried_spring_rolls`；base_price 5.99；station `DEEPFRIED` (id 5)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 炸虾 / Tempura Shrimp — item 20

SKU `tempura_shrimp`；base_price 8.99；station `DEEPFRIED` (id 5)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 炸馒头 / Fried Steamed Buns — item 21

SKU `fried_steamed_buns`；base_price 5.99；station `DEEPFRIED` (id 5)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 炸馄饨 / Fried Wontons — item 22

SKU `fried_wontons`；base_price 5.99；station `DEEPFRIED` (id 5)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

### DRINK — 饮品 / Drinks（id=4, active=True）

#### 可乐 / Coke — item 10

SKU `coke`；base_price 3.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### 健怡可乐 / Diet Coke — item 11

SKU `diet_coke`；base_price 3.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### 冰红茶 / Ice Tea — item 12

SKU `ice_tea`；base_price 3.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### 王老吉 / Chinese Herbal Tea — item 13

SKU `chinese_herbal_tea`；base_price 3.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### 七喜 / Seven Up — item 30

SKU `seven_up`；base_price 3.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### 姜汁汽水 / canada dry — item 31

SKU `canada_dry`；base_price 3.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 果味烧酒 / Shochu Fruit — item 32

SKU `shochu_fruit`；base_price 24.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### 大清酒 / Large Sake — item 33

SKU `lg_sake`；base_price 18.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### 青岛啤酒 / Tsingtao Beer — item 34

SKU `tsingtao_beer`；base_price 8.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### Sapporo / sapporo — item 35

SKU `sapporo`；base_price 9.50；station `BAR` (id 3)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 小清酒 / Small Sake — item 36

SKU `sm_sake`；base_price 15.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 原味烧酒 / Soju — item 37

SKU `soju`；base_price 20.00；station `BAR` (id 3)；active=True；sold_out=False；item_type=menu_item；default_combo_egg=NONE。

Options: NONE。

#### 烧酒 / Shochu — item 38

SKU `shochu`；base_price 18.00；station `BAR` (id 3)；active=False；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

#### 清酒 / Sake — item 39

SKU `sake`；base_price 18.00；station `BAR` (id 3)；active=False；sold_out=False；item_type=drink；default_combo_egg=NONE。

Options: NONE。

### 本地 Store Combo 配置（包括 archived）

| Group ID | Code | Rule | Required | Enabled | Default | Archived |
| --- | --- | --- | --- | --- | --- | --- |
| 3 | COMBO_ACCEPTANCE_DRINK_A5520260814125219 | OPTIONAL_ONE | False | False | combo_coke_a5520260814125219 | 2026-08-14T00:52:21.968214 |
| 1 | COMBO_EGG | EXACTLY_ONE | True | True | combo_tea_egg | — |
| 2 | COMBO_SIDE | EXACTLY_ONE | True | True | combo_cucumber_salad | — |
| 4 | COMBO_DRINKS | EXACTLY_ONE | True | False | combo_cola | 2026-08-21T18:28:13.008274 |
| 5 | COMBO_A55_SMOKE_DRINK_728288 | EXACTLY_ONE | True | False | combo_a55_sprite_728288 | 2026-08-14T13:24:52.264314 |

| Component ID | Code / Group | Name | Linked item | Behavior | Enabled | Archived |
| --- | --- | --- | --- | --- | --- | --- |
| 6 | combo_coke_a5520260814125219 / COMBO_ACCEPTANCE_DRINK_A5520260814125219 | 可乐A5520260814125219 / Coke A5520260814125219 | — | NO_KITCHEN_TASK | False | 2026-08-14T00:52:21.968214 |
| 7 | combo_sprite_a5520260814125219 / COMBO_ACCEPTANCE_DRINK_A5520260814125219 | 雪碧A5520260814125219 / Sprite A5520260814125219 | — | NO_KITCHEN_TASK | False | 2026-08-14T00:52:21.968214 |
| 9 | combo_a55_coke_728288 / COMBO_A55_SMOKE_DRINK_728288 | A5.5可乐728288 / A55 Coke 728288 | — | NO_KITCHEN_TASK | False | 2026-08-14T13:24:52.264314 |
| 10 | combo_a55_sprite_728288 / COMBO_A55_SMOKE_DRINK_728288 | A5.5雪碧728288 / A55 Sprite 728288 | — | NO_KITCHEN_TASK | False | 2026-08-14T13:24:52.264314 |
| 3 | combo_tea_egg / COMBO_EGG | 卤蛋 / Tea Egg | 27 | LINKED_MENU_ITEM | True | — |
| 1 | combo_fried_egg / COMBO_EGG | 煎蛋 / Fried Egg | 18 | LINKED_MENU_ITEM | True | — |
| 4 | combo_cucumber_salad / COMBO_SIDE | 拌黄瓜 / Cucumber Salad | 7 | LEGACY_COMBO_SIDE_TASK | True | — |
| 5 | combo_edamame / COMBO_SIDE | 毛豆 / Edamame | 8 | LEGACY_COMBO_SIDE_TASK | True | — |
| 2 | combo_shredded_potato / COMBO_SIDE | 土豆丝 / Shredded Potato | 9 | LEGACY_COMBO_SIDE_TASK | True | — |
| 8 | combo_cola / COMBO_DRINKS | 可乐 / cola | 10 | LINKED_MENU_ITEM | False | 2026-08-21T18:28:13.008274 |

## C. Item 对照与 ITEM PRICE DIFF

HIGH 仅接受唯一稳定 identity 或明确人工证据。所有语义建议均为 REVIEW_REQUIRED；NO_MATCH 的潜在歧义写在 Notes，不强行指定。

| Review | Uber Category / Item | Uber ID / external_data | Uber price | Local ID / SKU | Local 中文 / English | Local price | Price class / Δ | Confidence | Basis / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| I001 | Alcohol / Green Grape Soju | Green_Grape_Soju / EMPTY | 29.00 |  /  |  /  | — | UBER_ONLY /  | NO_MATCH | 没有可唯一确认的本地候选；本地 shochu_fruit 只有通用果味烧酒，无法保留独立口味，规格亦未确认 |
| I002 | Alcohol / Hakutsuru Junmai Ginjo Sake | Hakutsuru_Junmai_Gin / EMPTY | 23.00 |  /  |  /  | — | UBER_ONLY /  | NO_MATCH | 没有可唯一确认的本地候选；本地 lg_sake/sm_sake 区分大小，Uber区分品牌/品种；无容量证据，候选冲突 |
| I003 | Alcohol / Lychee Soju | Good_Day_Lychee_Soju / EMPTY | 29.00 |  /  |  /  | — | UBER_ONLY /  | NO_MATCH | 没有可唯一确认的本地候选；本地 shochu_fruit 只有通用果味烧酒，无法保留独立口味，规格亦未确认 |
| I004 | Alcohol / Melon Soju | Melon_Soju / EMPTY | 29.00 |  /  |  /  | — | UBER_ONLY /  | NO_MATCH | 没有可唯一确认的本地候选；本地 shochu_fruit 只有通用果味烧酒，无法保留独立口味，规格亦未确认 |
| I005 | Alcohol / Original Soju | Original_Soju / EMPTY | 25.00 | 37 / soju | 原味烧酒 / Soju | 20.00 | DIFF / 5.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I006 | Alcohol / Peach Soju | Peach_Soju / EMPTY | 29.00 |  /  |  /  | — | UBER_ONLY /  | NO_MATCH | 没有可唯一确认的本地候选；本地 shochu_fruit 只有通用果味烧酒，无法保留独立口味，规格亦未确认 |
| I007 | Alcohol / Sapporo Beer | Sapporo_Beer / EMPTY | 14.50 | 35 / sapporo | Sapporo / sapporo | 9.50 | DIFF / 5.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I008 | Alcohol / Sayuri Nigori Sake | Nigori_Sake / EMPTY | 23.00 |  /  |  /  | — | UBER_ONLY /  | NO_MATCH | 没有可唯一确认的本地候选；本地 lg_sake/sm_sake 区分大小，Uber区分品牌/品种；无容量证据，候选冲突 |
| I009 | Alcohol / Tsingtao Beer | Tsingtao_Beer / EMPTY | 13.00 | 34 / tsingtao_beer | 青岛啤酒 / Tsingtao Beer | 8.00 | DIFF / 5.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I010 | Ramen / Braised Beef Tendon in Brown Sauce with Noodles | Braised_Beef_Tendon_ / EMPTY | 19.99 | 2 / braised_beef_tendon_noodle | 红烧牛筋面 / Braised Beef Tendon Noodle | 17.99 | DIFF / 2.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I011 | Ramen / Chicken Chow Mein | d2a77967-74f3-4dfb-9e8e-a236369bb4f6 / EMPTY | 21.99 | 15 / chicken_chow_mein | 鸡肉炒面 / Chicken Chow Mein | 18.99 | DIFF / 3.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I012 | Ramen / Dandan Noodles (With Peanuts) | 2be2f7be-aa39-4742-bf91-edc9759f19c0 / EMPTY | 20.99 | 4 / dan_dan_noodle | 担担面 / Dan Dan Noodle | 17.99 | DIFF / 3.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I013 | Ramen / Lanzhou Beef Chow Mein (Beef) | 9c8260ac-717a-4d20-80b5-aeef17613186 / EMPTY | 21.99 | 14 / beef_chow_mein | 牛肉炒面 / Beef Chow Mein | 18.99 | DIFF / 3.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I014 | Ramen / Noodle with Vegetables | 3b8b2764-d492-47e0-9977-107ed4e67d1f / EMPTY | 19.99 | 3 / vegetable_noodle | 蔬菜面 / Vegetable Noodle | 16.99 | DIFF / 3.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I015 | Ramen / Stir-fried Tomato Beef Noodle (Beef) | b11777c1-9d50-44fe-8397-040a5056152b / EMPTY | 21.99 | 16 / tomato_chow_mein | 番茄炒面 / Tomato Chow Mein | 18.99 | DIFF / 3.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I016 | Ramen / Traditional Lanzhou Hand-pull Beef Noodle | b940caa7-6e37-4b3b-b964-3e10151e7903 / EMPTY | 19.99 | 1 / traditional_beef_noodle | 传统牛肉面 / Traditional Beef Noodle | 16.99 | DIFF / 3.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I017 | Ramen / Vegetable Chow Mein | 842d5987-6945-42ef-a1d9-ed15cad18a0b / EMPTY | 21.99 | 17 / vegetable_chow_mein | 素菜炒面 / Vegetable Chow Mein | 18.99 | DIFF / 3.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I018 | Ramen / Zha Jiang Noodle | 41989752-4e09-4fc5-8d22-96e9427173eb / EMPTY | 20.99 | 5 / zha_jiang_noodle | 炸酱面 / Zha Jiang Noodle | 17.99 | DIFF / 3.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I019 | Ramen Set Meal / Beef Lanzhou Noodles Special Combo | eca7fbf3-a666-454b-b36e-7d380e80b49d / EMPTY | 24.99 | 1 / traditional_beef_noodle | 传统牛肉面 / Traditional Beef Noodle | 16.99 | DIFF / 8.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；Uber 独立套餐根菜；本地须主菜+COMBO触发+蛋+小菜，单独 ITEM rule 不足以表达 |
| I020 | Ramen Set Meal / Chicken Chow Mein Combo | c169f422-a9cc-4758-82a9-0f60eef89b68 / EMPTY | 26.99 | 15 / chicken_chow_mein | 鸡肉炒面 / Chicken Chow Mein | 18.99 | DIFF / 8.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；Uber 独立套餐根菜；本地须主菜+COMBO触发+蛋+小菜，单独 ITEM rule 不足以表达 |
| I021 | Ramen Set Meal / Dandan Noodle Combo (With Peanuts) | 87bbecca-85fa-4681-a3dd-06c8bd97d70a / EMPTY | 25.99 | 4 / dan_dan_noodle | 担担面 / Dan Dan Noodle | 17.99 | DIFF / 8.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；Uber 独立套餐根菜；本地须主菜+COMBO触发+蛋+小菜，单独 ITEM rule 不足以表达 |
| I022 | Ramen Set Meal / Lanzhou Beef Chow Mein Combo | b3f90251-5066-42c4-bb4c-9a1581a38936 / EMPTY | 26.99 | 14 / beef_chow_mein | 牛肉炒面 / Beef Chow Mein | 18.99 | DIFF / 8.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；Uber 独立套餐根菜；本地须主菜+COMBO触发+蛋+小菜，单独 ITEM rule 不足以表达 |
| I023 | Ramen Set Meal / Noodles with Vegetables Combo | e72231fb-9029-4539-91d6-b6efbeeb3767 / EMPTY | 24.99 | 3 / vegetable_noodle | 蔬菜面 / Vegetable Noodle | 16.99 | DIFF / 8.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；Uber 独立套餐根菜；本地须主菜+COMBO触发+蛋+小菜，单独 ITEM rule 不足以表达 |
| I024 | Ramen Set Meal / Stir-fried Tomato Beef Noodle Combo | e45ddd3e-dbf5-449d-8ab6-badbccd4897b / EMPTY | 26.99 | 16 / tomato_chow_mein | 番茄炒面 / Tomato Chow Mein | 18.99 | DIFF / 8.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；Uber 独立套餐根菜；本地须主菜+COMBO触发+蛋+小菜，单独 ITEM rule 不足以表达 |
| I025 | Ramen Set Meal / Vegetable Chow Mein Combo | 78b60d9c-29fc-4064-9b10-10dd11427df5 / EMPTY | 26.99 | 17 / vegetable_chow_mein | 素菜炒面 / Vegetable Chow Mein | 18.99 | DIFF / 8.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；Uber 独立套餐根菜；本地须主菜+COMBO触发+蛋+小菜，单独 ITEM rule 不足以表达 |
| I026 | Ramen Set Meal / Zhajiang Noodles Combo | e8188b13-132d-48af-a981-a3dd1476ee01 / EMPTY | 25.99 | 5 / zha_jiang_noodle | 炸酱面 / Zha Jiang Noodle | 17.99 | DIFF / 8.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；Uber 独立套餐根菜；本地须主菜+COMBO触发+蛋+小菜，单独 ITEM rule 不足以表达 |
| I027 | Salades / Crispy Tempura Shrimp (4pcs) | Crispy_Tempura_Shrim / EMPTY | 10.99 | 20 / tempura_shrimp | 炸虾 / Tempura Shrimp | 8.99 | DIFF / 2.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I028 | Salades / Cucumber Mix With Home Made Spicy Sauce | 0cbf47bd-5d4c-456a-900e-7da7f6f8f54b / EMPTY | 6.99 | 7 / cucumber_salad | 拌黄瓜 / Cucumber Salad | 4.99 | DIFF / 2.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I029 | Salades / Edamame With Preserved Vegetable | 2155b250-58f2-49d2-a971-d149d8a61386 / EMPTY | 6.99 | 8 / edamame | 毛豆 / Edamame | 4.99 | DIFF / 2.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I030 | Salades / Fried Chinese Steamed buns (3pcs) | Fried_Chinese_Steame / EMPTY | 7.99 | 21 / fried_steamed_buns | 炸馒头 / Fried Steamed Buns | 5.99 | DIFF / 2.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I031 | Salades / Fried Wontons (6 pcs) | Fried_Wontons_(6_pcs / EMPTY | 7.99 | 22 / fried_wontons | 炸馄饨 / Fried Wontons | 5.99 | DIFF / 2.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I032 | Salades / Homemade Lanzhou Beef (With Peanuts) | 71020dd7-1dbc-4711-9852-dcd1191d5eaa / EMPTY | 11.99 | 6 / braised_beef_shank_salad | 拌牛展 / Braised Beef Shank Salad | 9.99 | DIFF / 2.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I033 | Salades / Rouleaux de printemps frits (3 pcs) | Rouleaux_de_printemp / EMPTY | 7.99 | 19 / fried_spring_rolls | 炸春卷 / Fried Spring Rolls | 5.99 | DIFF / 2.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I034 | Salades / Sweet & Sour Mini Fries | 540c3a0b-3830-4bc6-b706-14638c7e6352 / EMPTY | 6.99 |  /  |  /  | — | UBER_ONLY /  | NO_MATCH | 没有可唯一确认的本地候选；可能有人认为是土豆丝，但英文名称/描述指薯条；不能据此确认 shredded_potato |
| I035 | Salades / Tea Corned Egg | e8e7be55-3139-4949-8a7e-69a1d52161c5 / EMPTY | 2.50 | 27 / tea_egg | 茶叶蛋 / Tea Egg | 1.99 | DIFF / 0.51 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I036 | Soda / Chinese Herbal Tea | 745a90de-3e4b-444e-8de4-fa4cefc63eb0 / EMPTY | 3.00 | 13 / chinese_herbal_tea | 王老吉 / Chinese Herbal Tea | 3.00 | MATCH / 0.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I037 | Soda / Coke | 4c7162f6-1a21-4545-8eb5-6c49b01af0fd / EMPTY | 3.00 | 10 / coke | 可乐 / Coke | 3.00 | MATCH / 0.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I038 | Soda / Diet Coke | 8c3c9093-c5fb-4d6d-ac0c-140451e9f29a / EMPTY | 3.00 | 11 / diet_coke | 健怡可乐 / Diet Coke | 3.00 | MATCH / 0.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I039 | Soda / Ginger Ale | cbcd1dcd-87bb-4bd3-aa95-147817e134c8 / EMPTY | 3.00 | 31 / canada_dry | 姜汁汽水 / canada dry | 3.00 | MATCH / 0.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认；同饮料种类和价格；Uber未声明Canada Dry品牌，需确认品牌/规格 |
| I040 | Soda / Ice Tea | 7aad8f91-c26d-4c1d-8609-341f83396317 / EMPTY | 3.00 | 12 / ice_tea | 冰红茶 / Ice Tea | 3.00 | MATCH / 0.00 | REVIEW_REQUIRED | 名称/菜品语义对应；价格差异另列，无稳定身份或人工确认； |
| I041 | Soda / Sprite | 79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2 / EMPTY | 3.00 |  /  |  /  | — | UBER_ONLY /  | NO_MATCH | 没有可唯一确认的本地候选；本地 seven_up 是七喜，品牌不同，不当成同一SKU |

## D. Modifier / Option 对照与 MODIFIER PRICE DIFF

global unique modifier count=33；item-context modifier count=258（root + immediate parent + group + modifier）；本次没有 nested child group。CSV 保留每个上下文，不按名称去重。unique confidence 使用『至少一个父菜可建议则 REVIEW_REQUIRED』口径，不能替代每一行判断。

### Braised Beef Tendon in Brown Sauce with Noodles — `Braised_Beef_Tendon_`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M001 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 253 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M002 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 252 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M003 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 251 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M004 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 250 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M005 | Size / Regular | 94b90aa8-a673-4793-b7bd-917c837716df | 1–1 / True | 0.00 | 255 / size_regular | SIZE / size | 中碗 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M006 | Size / Large | 01c44198-eeb9-4452-bc2b-0939bf713a39 | 1–1 / True | 2.00 | 254 / size_large | SIZE / size | 大碗 / Large | 2.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M007 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M008 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M009 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 11 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M010 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 10 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M011 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 12 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M012 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 13 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M013 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 14 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M014 | Extra Topping / Extra Meat | 524842f3-7e5d-441f-813b-895f0491179f | 0–999 / False | 6.99 | 247 / extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M015 | Extra Topping / Extra Fried Egg | 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | 0–999 / False | 2.50 | 246 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M016 | Extra Topping / Extra Tea Boil Egg | cd3b4c00-5065-47ac-a8e8-a2772067ca16 | 0–999 / False | 2.50 | 248 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M017 | Extra Topping / Extra Vegetable | 2ca784f1-b086-42bb-aee0-64840d75cacf | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M018 | Extra Topping / Extra Noodle | dddc88d0-5f52-4266-af87-ea7477d63f30 | 0–999 / False | 3.99 | 249 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M019 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 | 240 / remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M020 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 | 241 / remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |

### Chicken Chow Mein — `d2a77967-74f3-4dfb-9e8e-a236369bb4f6`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M021 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 107 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M022 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 106 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M023 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 105 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M024 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 104 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M025 | Add Egg / Add Fried Egg | 43731428-8827-403a-b401-246ff779bef0 | 0–999 / False | 1.99 | 103 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M026 | Add Egg / Add Tea Boil Egg | 316bb70f-f2a4-4b44-9b7a-7ad73c5243d9 | 0–999 / False | 1.99 | 102 / tea_egg | ADD_ON / addon | 加卤蛋 / Extra Tea Egg | 1.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M027 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M028 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Dandan Noodles (With Peanuts) — `2be2f7be-aa39-4742-bf91-edc9759f19c0`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M029 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 160 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M030 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 159 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M031 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 158 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M032 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 157 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M033 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M034 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M035 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 25 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M036 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 24 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M037 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 26 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M038 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 27 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M039 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 28 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M040 | Extra Topping / Extra Noodle | 2ad33578-e7c6-4fa3-ac38-f4dff54a7eae | 0–999 / False | 3.99 | 156 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M041 | Extra Topping / Extra Vegetable | a79813fd-2d14-45bd-a57c-462a8ddbb70f | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M042 | Extra Topping / Extra Fried Egg | 329049e9-f054-4f0a-ad82-ccbd189c262d | 0–999 / False | 2.50 | 153 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M043 | Extra Topping / Extra Tea Boil Egg | 3e400404-e7da-4401-8f23-b91d74d7986e | 0–999 / False | 2.50 | 155 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M044 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 | 147 / remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M045 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 | 148 / remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |

### Lanzhou Beef Chow Mein (Beef) — `9c8260ac-717a-4d20-80b5-aeef17613186`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M046 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 133 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M047 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 132 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M048 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 131 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M049 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 130 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M050 | Add Egg / Add Fried Egg | 43731428-8827-403a-b401-246ff779bef0 | 0–999 / False | 1.99 | 129 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M051 | Add Egg / Add Tea Boil Egg | 316bb70f-f2a4-4b44-9b7a-7ad73c5243d9 | 0–999 / False | 1.99 | 128 / tea_egg | ADD_ON / addon | 加卤蛋 / Extra Tea Egg | 1.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M052 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M053 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Noodle with Vegetables — `3b8b2764-d492-47e0-9977-107ed4e67d1f`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M054 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 220 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M055 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 219 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M056 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 218 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M057 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 217 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M058 | Size / Regular | 94b90aa8-a673-4793-b7bd-917c837716df | 1–1 / True | 0.00 | 222 / size_regular | SIZE / size | 中碗 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M059 | Size / Large | 01c44198-eeb9-4452-bc2b-0939bf713a39 | 1–1 / True | 2.00 | 221 / size_large | SIZE / size | 大碗 / Large | 2.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M060 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M061 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M062 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 18 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M063 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 17 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M064 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 19 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M065 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 20 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M066 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 21 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M067 | Soup Base / Meat Broth | 133d15f2-124d-43c6-9b2c-15352e25926b | 1–1 / True | 0.00 | 215 / soup_beef | SOUP_BASE / soup_base | 肉汤 / Beef Broth | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M068 | Soup Base / Vegan Broth | 182b7ed4-d3c7-4aeb-9516-aa0c3959de97 | 1–1 / True | 0.00 | 216 / soup_vegan | SOUP_BASE / soup_base | 素汤 / Vegan Broth | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M069 | Extra Topping / Extra Meat | 524842f3-7e5d-441f-813b-895f0491179f | 0–999 / False | 6.99 | 212 / extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M070 | Extra Topping / Extra Fried Egg | 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | 0–999 / False | 2.50 | 211 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M071 | Extra Topping / Extra Tea Boil Egg | cd3b4c00-5065-47ac-a8e8-a2772067ca16 | 0–999 / False | 2.50 | 213 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M072 | Extra Topping / Extra Vegetable | 2ca784f1-b086-42bb-aee0-64840d75cacf | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M073 | Extra Topping / Extra Noodle | dddc88d0-5f52-4266-af87-ea7477d63f30 | 0–999 / False | 3.99 | 214 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M074 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M075 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Stir-fried Tomato Beef Noodle (Beef) — `b11777c1-9d50-44fe-8397-040a5056152b`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M076 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 81 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M077 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 80 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M078 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 79 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M079 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 78 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M080 | Add Egg / Add Fried Egg | 43731428-8827-403a-b401-246ff779bef0 | 0–999 / False | 1.99 | 77 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M081 | Add Egg / Add Tea Boil Egg | 316bb70f-f2a4-4b44-9b7a-7ad73c5243d9 | 0–999 / False | 1.99 | 76 / tea_egg | ADD_ON / addon | 加卤蛋 / Extra Tea Egg | 1.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M082 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M083 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Traditional Lanzhou Hand-pull Beef Noodle — `b940caa7-6e37-4b3b-b964-3e10151e7903`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M084 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 285 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M085 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 284 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M086 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 283 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M087 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 282 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M088 | Size / Regular | 94b90aa8-a673-4793-b7bd-917c837716df | 1–1 / True | 0.00 | 287 / size_regular | SIZE / size | 中碗 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M089 | Size / Large | 01c44198-eeb9-4452-bc2b-0939bf713a39 | 1–1 / True | 2.00 | 286 / size_large | SIZE / size | 大碗 / Large | 2.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M090 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M091 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M092 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 4 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M093 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 3 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M094 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 5 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M095 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 6 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M096 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 7 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M097 | Extra Topping / Extra Meat | 524842f3-7e5d-441f-813b-895f0491179f | 0–999 / False | 6.99 | 37 / extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M098 | Extra Topping / Extra Fried Egg | 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | 0–999 / False | 2.50 | 281 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M099 | Extra Topping / Extra Tea Boil Egg | cd3b4c00-5065-47ac-a8e8-a2772067ca16 | 0–999 / False | 2.50 | 36 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M100 | Extra Topping / Extra Vegetable | 2ca784f1-b086-42bb-aee0-64840d75cacf | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M101 | Extra Topping / Extra Noodle | dddc88d0-5f52-4266-af87-ea7477d63f30 | 0–999 / False | 3.99 | 280 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M102 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 | 274 / remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M103 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 | 275 / remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |

### Vegetable Chow Mein — `842d5987-6945-42ef-a1d9-ed15cad18a0b`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M104 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 55 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M105 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 54 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M106 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 53 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M107 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 52 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M108 | Add Egg / Add Fried Egg | 43731428-8827-403a-b401-246ff779bef0 | 0–999 / False | 1.99 | 51 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M109 | Add Egg / Add Tea Boil Egg | 316bb70f-f2a4-4b44-9b7a-7ad73c5243d9 | 0–999 / False | 1.99 | 50 / tea_egg | ADD_ON / addon | 加卤蛋 / Extra Tea Egg | 1.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M110 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M111 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Zha Jiang Noodle — `41989752-4e09-4fc5-8d22-96e9427173eb`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M112 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 189 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M113 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 188 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M114 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 187 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M115 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 186 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M116 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M117 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M118 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 32 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M119 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 31 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M120 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 33 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M121 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 34 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M122 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 35 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M123 | Extra Topping / Extra Noodle | 2ad33578-e7c6-4fa3-ac38-f4dff54a7eae | 0–999 / False | 3.99 | 185 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M124 | Extra Topping / Extra Vegetable | a79813fd-2d14-45bd-a57c-462a8ddbb70f | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M125 | Extra Topping / Extra Fried Egg | 329049e9-f054-4f0a-ad82-ccbd189c262d | 0–999 / False | 2.50 | 182 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M126 | Extra Topping / Extra Tea Boil Egg | 3e400404-e7da-4401-8f23-b91d74d7986e | 0–999 / False | 2.50 | 184 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M127 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 | 176 / remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M128 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 | 177 / remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |

### Beef Lanzhou Noodles Special Combo — `eca7fbf3-a666-454b-b36e-7d380e80b49d`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M129 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 285 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M130 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 284 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M131 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 283 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M132 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 282 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M133 | Size / Regular | 94b90aa8-a673-4793-b7bd-917c837716df | 1–1 / True | 0.00 | 287 / size_regular | SIZE / size | 中碗 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M134 | Size / Large | 01c44198-eeb9-4452-bc2b-0939bf713a39 | 1–1 / True | 2.00 | 286 / size_large | SIZE / size | 大碗 / Large | 2.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M135 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M136 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M137 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 4 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M138 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 3 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M139 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 5 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M140 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 6 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M141 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 7 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M142 | Side Dishes / Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | 1–1 / True | 0.00 | -20203 / combo_cucumber_salad | COMBO_SIDE / addon | 拌黄瓜 / Cucumber Salad | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M143 | Side Dishes / Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | 1–1 / True | 0.00 | -20201 / combo_edamame | COMBO_SIDE / addon | 毛豆 / Edamame | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M144 | Side Dishes / Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| M145 | Tea Boil Egg / Tea Boil Egg | 61ad7ec7-2a69-4781-9210-b71c75084bad | 1–1 / True | 0.00 | -20101 / combo_tea_egg | COMBO_EGG / addon | 卤蛋 / Tea Egg | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M146 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 | 274 / remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M147 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 | 275 / remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M148 | Extra Topping / Extra Meat | 524842f3-7e5d-441f-813b-895f0491179f | 0–999 / False | 6.99 | 37 / extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M149 | Extra Topping / Extra Fried Egg | 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | 0–999 / False | 2.50 | 281 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M150 | Extra Topping / Extra Tea Boil Egg | cd3b4c00-5065-47ac-a8e8-a2772067ca16 | 0–999 / False | 2.50 | 36 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M151 | Extra Topping / Extra Vegetable | 2ca784f1-b086-42bb-aee0-64840d75cacf | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M152 | Extra Topping / Extra Noodle | dddc88d0-5f52-4266-af87-ea7477d63f30 | 0–999 / False | 3.99 | 280 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |

### Chicken Chow Mein Combo — `c169f422-a9cc-4758-82a9-0f60eef89b68`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M153 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 107 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M154 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 106 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M155 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 105 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M156 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 104 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M157 | Side Dishes / Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | 1–1 / True | 0.00 | -20203 / combo_cucumber_salad | COMBO_SIDE / addon | 拌黄瓜 / Cucumber Salad | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M158 | Side Dishes / Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | 1–1 / True | 0.00 | -20201 / combo_edamame | COMBO_SIDE / addon | 毛豆 / Edamame | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M159 | Side Dishes / Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| M160 | Fried Egg / Fried Egg | 8d6efb88-7974-4973-a681-263b7f186959 | 1–1 / True | 0.00 | -20102 / combo_fried_egg | COMBO_EGG / addon | 煎蛋 / Fried Egg | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M161 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M162 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Dandan Noodle Combo (With Peanuts) — `87bbecca-85fa-4681-a3dd-06c8bd97d70a`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M163 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 160 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M164 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 159 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M165 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 158 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M166 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 157 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M167 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M168 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M169 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 25 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M170 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 24 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M171 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 26 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M172 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 27 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M173 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 28 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M174 | Side Dishes / Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | 1–1 / True | 0.00 | -20203 / combo_cucumber_salad | COMBO_SIDE / addon | 拌黄瓜 / Cucumber Salad | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M175 | Side Dishes / Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | 1–1 / True | 0.00 | -20201 / combo_edamame | COMBO_SIDE / addon | 毛豆 / Edamame | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M176 | Side Dishes / Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| M177 | Tea Boil Egg / Tea Boil Egg | 61ad7ec7-2a69-4781-9210-b71c75084bad | 1–1 / True | 0.00 | -20101 / combo_tea_egg | COMBO_EGG / addon | 卤蛋 / Tea Egg | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M178 | Extra Topping / Extra Noodle | 2ad33578-e7c6-4fa3-ac38-f4dff54a7eae | 0–999 / False | 3.99 | 156 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M179 | Extra Topping / Extra Vegetable | a79813fd-2d14-45bd-a57c-462a8ddbb70f | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M180 | Extra Topping / Extra Fried Egg | 329049e9-f054-4f0a-ad82-ccbd189c262d | 0–999 / False | 2.50 | 153 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M181 | Extra Topping / Extra Tea Boil Egg | 3e400404-e7da-4401-8f23-b91d74d7986e | 0–999 / False | 2.50 | 155 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M182 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 | 147 / remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M183 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 | 148 / remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |

### Lanzhou Beef Chow Mein Combo — `b3f90251-5066-42c4-bb4c-9a1581a38936`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M184 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 133 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M185 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 132 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M186 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 131 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M187 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 130 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M188 | Side Dishes / Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | 1–1 / True | 0.00 | -20203 / combo_cucumber_salad | COMBO_SIDE / addon | 拌黄瓜 / Cucumber Salad | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M189 | Side Dishes / Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | 1–1 / True | 0.00 | -20201 / combo_edamame | COMBO_SIDE / addon | 毛豆 / Edamame | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M190 | Side Dishes / Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| M191 | Fried Egg / Fried Egg | 8d6efb88-7974-4973-a681-263b7f186959 | 1–1 / True | 0.00 | -20102 / combo_fried_egg | COMBO_EGG / addon | 煎蛋 / Fried Egg | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M192 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M193 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Noodles with Vegetables Combo — `e72231fb-9029-4539-91d6-b6efbeeb3767`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M194 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 220 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M195 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 219 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M196 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 218 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M197 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 217 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M198 | Size / Regular | 94b90aa8-a673-4793-b7bd-917c837716df | 1–1 / True | 0.00 | 222 / size_regular | SIZE / size | 中碗 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M199 | Size / Large | 01c44198-eeb9-4452-bc2b-0939bf713a39 | 1–1 / True | 2.00 | 221 / size_large | SIZE / size | 大碗 / Large | 2.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M200 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M201 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M202 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 18 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M203 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 17 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M204 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 19 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M205 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 20 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M206 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 21 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M207 | Side Dishes / Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | 1–1 / True | 0.00 | -20203 / combo_cucumber_salad | COMBO_SIDE / addon | 拌黄瓜 / Cucumber Salad | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M208 | Side Dishes / Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | 1–1 / True | 0.00 | -20201 / combo_edamame | COMBO_SIDE / addon | 毛豆 / Edamame | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M209 | Side Dishes / Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| M210 | Tea Boil Egg / Tea Boil Egg | 61ad7ec7-2a69-4781-9210-b71c75084bad | 1–1 / True | 0.00 | -20101 / combo_tea_egg | COMBO_EGG / addon | 卤蛋 / Tea Egg | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M211 | Extra Topping / Extra Meat | 524842f3-7e5d-441f-813b-895f0491179f | 0–999 / False | 6.99 | 212 / extra_meat | ADD_ON / addon | 加肉 / Extra Beef | 6.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M212 | Extra Topping / Extra Fried Egg | 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | 0–999 / False | 2.50 | 211 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M213 | Extra Topping / Extra Tea Boil Egg | cd3b4c00-5065-47ac-a8e8-a2772067ca16 | 0–999 / False | 2.50 | 213 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M214 | Extra Topping / Extra Vegetable | 2ca784f1-b086-42bb-aee0-64840d75cacf | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M215 | Extra Topping / Extra Noodle | dddc88d0-5f52-4266-af87-ea7477d63f30 | 0–999 / False | 3.99 | 214 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M216 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M217 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Stir-fried Tomato Beef Noodle Combo — `e45ddd3e-dbf5-449d-8ab6-badbccd4897b`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M218 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 81 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M219 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 80 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M220 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 79 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M221 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 78 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M222 | Side Dishes / Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | 1–1 / True | 0.00 | -20203 / combo_cucumber_salad | COMBO_SIDE / addon | 拌黄瓜 / Cucumber Salad | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M223 | Side Dishes / Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | 1–1 / True | 0.00 | -20201 / combo_edamame | COMBO_SIDE / addon | 毛豆 / Edamame | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M224 | Side Dishes / Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| M225 | Fried Egg / Fried Egg | 8d6efb88-7974-4973-a681-263b7f186959 | 1–1 / True | 0.00 | -20102 / combo_fried_egg | COMBO_EGG / addon | 煎蛋 / Fried Egg | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M226 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M227 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Vegetable Chow Mein Combo — `78b60d9c-29fc-4064-9b10-10dd11427df5`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M228 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 55 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M229 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 54 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M230 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 53 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M231 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 52 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M232 | Side Dishes / Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | 1–1 / True | 0.00 | -20203 / combo_cucumber_salad | COMBO_SIDE / addon | 拌黄瓜 / Cucumber Salad | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M233 | Side Dishes / Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | 1–1 / True | 0.00 | -20201 / combo_edamame | COMBO_SIDE / addon | 毛豆 / Edamame | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M234 | Side Dishes / Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| M235 | Fried Egg / Fried Egg | 8d6efb88-7974-4973-a681-263b7f186959 | 1–1 / True | 0.00 | -20102 / combo_fried_egg | COMBO_EGG / addon | 煎蛋 / Fried Egg | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M236 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |
| M237 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 |  /  | REMOVE /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；该父菜无此 active option，其他菜同名选项不能跨菜复用 |

### Zhajiang Noodles Combo — `e8188b13-132d-48af-a981-a3dd1476ee01`

| Review | Uber group / Modifier | Uber modifier ID | Min–Max / required | Uber Δ | Local ID / Code | Group / Type | 中文 / English | Local Δ / Parent | Price class | Confidence / Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| M238 | Spicy Level / Non-spicy | fa1544e9-4e8e-4a4f-81a1-8643e200028e | 1–1 / True | 0.00 | 189 / spicy_none | SPICY_LEVEL / spicy_level | 不辣 / Non-Spicy | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M239 | Spicy Level / Mild Spicy | b4203d8e-6d06-47b9-ae76-ea74ef8ce015 | 1–1 / True | 0.00 | 188 / spicy_mild | SPICY_LEVEL / spicy_level | 少辣 / Mild | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M240 | Spicy Level / Regular Spicy | a32414ff-bae3-4354-84a9-9099e6185509 | 1–1 / True | 0.00 | 187 / spicy_regular | SPICY_LEVEL / spicy_level | 正常辣 / Regular | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M241 | Spicy Level / Extra Spicy | ff8e0441-064e-411c-a877-59d8b9035c82 | 1–1 / True | 0.00 | 186 / spicy_extra | SPICY_LEVEL / spicy_level | 加辣 / Extra | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M242 | Noodle Size Option / Fine | c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |
| M243 | Noodle Size Option / One Fine | 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| M244 | Noodle Size Option / Two Fine | 9405ea5a-c77a-4596-a185-614ea81b7702 | 1–1 / True | 0.00 | 32 / noodle_erxi | NOODLE_TYPE / noodle_type | 二细 / Erxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M245 | Noodle Size Option / Three Fine | d9750fe4-14fe-4efd-bcd6-8ea85774850d | 1–1 / True | 0.00 | 31 / noodle_sanxi | NOODLE_TYPE / noodle_type | 三细 / Sanxi | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M246 | Noodle Size Option / Leek Leaves | 8d7a40e5-7f2e-40bc-896d-7759db7984ce | 1–1 / True | 0.00 | 33 / noodle_leek_leaf | NOODLE_TYPE / noodle_type | 韭叶 / Leek Leaf | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M247 | Noodle Size Option / Wide | f5026fbe-eb02-4d84-bfe3-e336827cfce7 | 1–1 / True | 0.00 | 34 / noodle_wide | NOODLE_TYPE / noodle_type | 宽 / Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M248 | Noodle Size Option / Big Wide | 513c53d8-5464-47aa-9948-bda923fecdbd | 1–1 / True | 0.00 | 35 / noodle_extra_wide | NOODLE_TYPE / noodle_type | 大宽 / Extra Wide | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M249 | Side Dishes / Cucumber Mix With Home Made Spicy Sauce | 5209b4ae-9f45-44f2-9600-fb46c14e10ad | 1–1 / True | 0.00 | -20203 / combo_cucumber_salad | COMBO_SIDE / addon | 拌黄瓜 / Cucumber Salad | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M250 | Side Dishes / Edamame With Preserved Vegetable | b6c655e0-c49d-441b-aa9e-94e470d35404 | 1–1 / True | 0.00 | -20201 / combo_edamame | COMBO_SIDE / addon | 毛豆 / Edamame | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M251 | Side Dishes / Sweet & Sour Mini Fries | 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | 1–1 / True | 0.00 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| M252 | Tea Boil Egg / Tea Boil Egg | 61ad7ec7-2a69-4781-9210-b71c75084bad | 1–1 / True | 0.00 | -20101 / combo_tea_egg | COMBO_EGG / addon | 卤蛋 / Tea Egg | 0.00 / NONE | MATCH | REVIEW_REQUIRED；虚拟ID为当前 mapping service 组件ID；关联DB option仅供对照，不可把旧行ID当runtime组件ID；依赖COMBO触发 |
| M253 | Extra Topping / Extra Noodle | 2ad33578-e7c6-4fa3-ac38-f4dff54a7eae | 0–999 / False | 3.99 | 185 / extra_noodle | ADD_ON / addon | 加面 / Extra Noodle | 3.99 / NONE | MATCH | REVIEW_REQUIRED； |
| M254 | Extra Topping / Extra Vegetable | a79813fd-2d14-45bd-a57c-462a8ddbb70f | 0–999 / False | 3.99 |  /  |  /  |  /  |  / NONE | UBER_ONLY | NO_MATCH；本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| M255 | Extra Topping / Extra Fried Egg | 329049e9-f054-4f0a-ad82-ccbd189c262d | 0–999 / False | 2.50 | 182 / fried_egg | ADD_ON / addon | 加煎蛋 / Extra Fried Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M256 | Extra Topping / Extra Tea Boil Egg | 3e400404-e7da-4401-8f23-b91d74d7986e | 0–999 / False | 2.50 | 184 / tea_egg | ADD_ON / addon | 加蛋 / Extra Tea Egg | 1.99 / NONE | DIFF | REVIEW_REQUIRED； |
| M257 | Ingredients Option / Non Scallion/ Green Onion | d1f4a19b-fd7c-4cdf-863a-581391172f23 | 0–2 / False | 0.00 | 176 / remove_green_onion | REMOVE / remove | 走葱 / No Green Onion | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |
| M258 | Ingredients Option / Non Coriander | 54c53703-db68-4044-85a3-5f4962715566 | 0–2 / False | 0.00 | 177 / remove_cilantro | REMOVE / remove | 走香菜 / No Cilantro | 0.00 / NONE | MATCH | REVIEW_REQUIRED； |

## E. Combo 专项

Uber 有 8 个独立套餐 root，售价相对对应普通主菜高 5.00；本地是相应主菜 + COMBO `combo` option（DB delta 5.00）。Uber没有独立的 COMBO 开关 modifier；因此不能仅将套餐根映射为主菜然后忽略触发器。当前 service 校验会拒绝 `COMBO_COMPONENT_WITHOUT_COMBO`，不应把已有候选当成可运行方案。

| Uber Combo | Base local | Uber combo price | Local base + COMBO | Uber required egg | Local effective default egg | Sides |
| --- | --- | --- | --- | --- | --- | --- |
| Beef Lanzhou Noodles Special Combo | traditional_beef_noodle | 24.99 | 21.99 | Tea Boil Egg | combo_tea_egg (Store default) | Uber 3选1；本地3选1，第三项菜义待确认 |
| Chicken Chow Mein Combo | chicken_chow_mein | 26.99 | 23.99 | Fried Egg | combo_fried_egg | Uber 3选1；本地3选1，第三项菜义待确认 |
| Dandan Noodle Combo (With Peanuts) | dan_dan_noodle | 25.99 | 22.99 | Tea Boil Egg | combo_tea_egg (Store default) | Uber 3选1；本地3选1，第三项菜义待确认 |
| Lanzhou Beef Chow Mein Combo | beef_chow_mein | 26.99 | 23.99 | Fried Egg | combo_fried_egg | Uber 3选1；本地3选1，第三项菜义待确认 |
| Noodles with Vegetables Combo | vegetable_noodle | 24.99 | 21.99 | Tea Boil Egg | combo_tea_egg (Store default) | Uber 3选1；本地3选1，第三项菜义待确认 |
| Stir-fried Tomato Beef Noodle Combo | tomato_chow_mein | 26.99 | 23.99 | Fried Egg | combo_fried_egg | Uber 3选1；本地3选1，第三项菜义待确认 |
| Vegetable Chow Mein Combo | vegetable_chow_mein | 26.99 | 23.99 | Fried Egg | combo_fried_egg | Uber 3选1；本地3选1，第三项菜义待确认 |
| Zhajiang Noodles Combo | zha_jiang_noodle | 25.99 | 22.99 | Tea Boil Egg | combo_tea_egg (Store default) | Uber 3选1；本地3选1，第三项菜义待确认 |

Uber Tea Boil Egg / Fried Egg group 均 min=max=1，组内各仅一项，价格 0；这证明必选单一蛋，不证明消费者端预选状态。本地 COMBO_EGG 支持茶蛋/煎蛋两项；Store 默认茶蛋，炒面 item 14–17 覆盖煎蛋。

Uber Side Dishes 有黄瓜、毛豆、Sweet & Sour Mini Fries；本地为黄瓜、毛豆、土豆丝。前两项仅语义候选，第三项 NO_MATCH。Uber三项均 0，本地组件也为 0。Uber side modifier 没有子组，未发现 side child remove；本地存在 COMBO_SIDE_REMOVE 及运行时 linked side REMOVE 派生路径。

当前 UberEatsMenuMappingService.choices 跳过 DB COMBO_EGG/COMBO_SIDE 历史行，改用 store_combo_components 虚拟ID（茶蛋 -20101、煎蛋 -20102、毛豆 -20201、土豆丝 -20202、黄瓜 -20203）。CSV Local ID 对这些行使用运行ID，Related DB Option IDs 保留本地历史行；这不是已确认映射。

| Item | Child option | Parent option | Delta | Active |
| --- | --- | --- | --- | --- |
| vegetable_chow_mein | 56 / combo_cucumber_no_peanut | 57 / combo_cucumber_salad | 0.00 | True |
| tomato_chow_mein | 82 / combo_cucumber_no_peanut | 83 / combo_cucumber_salad | 0.00 | True |
| chicken_chow_mein | 108 / combo_cucumber_no_peanut | 109 / combo_cucumber_salad | 0.00 | True |
| beef_chow_mein | 134 / combo_cucumber_no_peanut | 135 / combo_cucumber_salad | 0.00 | True |
| dan_dan_noodle | 161 / combo_cucumber_no_peanut | 162 / combo_cucumber_salad | 0.00 | True |
| zha_jiang_noodle | 190 / combo_cucumber_no_peanut | 191 / combo_cucumber_salad | 0.00 | True |
| vegetable_noodle | 223 / combo_cucumber_no_peanut | 224 / combo_cucumber_salad | 0.00 | True |
| braised_beef_tendon_noodle | 256 / combo_cucumber_no_peanut | 257 / combo_cucumber_salad | 0.00 | False |
| traditional_beef_noodle | 288 / combo_cucumber_no_peanut | 289 / combo_cucumber_salad | 0.00 | True |
| cold_noodle_shredded_chicken | 316 / combo_cucumber_no_peanut | 319 / combo_cucumber_salad | 0.00 | True |
| pickled_vegetable_beef_noodle | 362 / combo_cucumber_no_peanut | 363 / combo_cucumber_salad | 0.00 | True |

## F. Noodle item-level matrix

详细候选ID和价格见 D 的逐菜表；下表逐项比较六种面类语义。LOCAL_ONLY 仅表示当前该 Uber 菜没有对应组，不等于本地选项错误。

### Braised Beef Tendon in Brown Sauce with Noodles → braised_beef_tendon_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | Regular, Large | 254:size_large, 255:size_regular, 382:size_small | REVIEW_REQUIRED |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 8:noodle_capillary, 9:noodle_thin, 10:noodle_sanxi, 11:noodle_erxi, 12:noodle_leek_leaf, 13:noodle_wide, 14:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 250:spicy_extra, 251:spicy_regular, 252:spicy_mild, 253:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Extra Meat, Extra Fried Egg, Extra Tea Boil Egg, Extra Vegetable, Extra Noodle | 242:extra_radish, 243:green_onion, 244:cilantro, 245:bok_choy, 246:fried_egg, 247:extra_meat, 248:tea_egg, 249:extra_noodle, 3437:addon_beef_tendons | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 236:remove_radish, 237:less_noodle, 238:remove_noodle, 239:remove_beef, 240:remove_green_onion, 241:remove_cilantro, 3438:removebaicai | REVIEW_REQUIRED |

### Chicken Chow Mein → chicken_chow_mein

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | NONE | NONE | NONE |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 104:spicy_extra, 105:spicy_regular, 106:spicy_mild, 107:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Add Fried Egg, Add Tea Boil Egg | 102:tea_egg, 103:fried_egg | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 94:remove_tomato, 95:remove_all_vegetables, 96:remove_zucchini, 97:remove_cabbage, 98:remove_broccoli, 99:remove_green_pepper, 100:remove_onion, 101:remove_bean_sprouts | REVIEW_REQUIRED |

### Dandan Noodles (With Peanuts) → dan_dan_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 22:noodle_capillary, 23:noodle_thin, 24:noodle_sanxi, 25:noodle_erxi, 26:noodle_leek_leaf, 27:noodle_wide, 28:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 157:spicy_extra, 158:spicy_regular, 159:spicy_mild, 160:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Extra Noodle, Extra Vegetable, Extra Fried Egg, Extra Tea Boil Egg | 149:green_onion, 150:cilantro, 151:extra_sauce, 152:bok_choy, 153:fried_egg, 154:extra_meat, 155:tea_egg, 156:extra_noodle | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 145:remove_bok_choy, 146:remove_peanut, 147:remove_green_onion, 148:remove_cilantro | REVIEW_REQUIRED |

### Lanzhou Beef Chow Mein (Beef) → beef_chow_mein

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | NONE | NONE | NONE |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 130:spicy_extra, 131:spicy_regular, 132:spicy_mild, 133:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Add Fried Egg, Add Tea Boil Egg | 128:tea_egg, 129:fried_egg | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 120:remove_tomato, 121:remove_all_vegetables, 122:remove_zucchini, 123:remove_cabbage, 124:remove_broccoli, 125:remove_green_pepper, 126:remove_onion, 127:remove_bean_sprouts | REVIEW_REQUIRED |

### Noodle with Vegetables → vegetable_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | Regular, Large | 221:size_large, 222:size_regular | REVIEW_REQUIRED |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 15:noodle_capillary, 16:noodle_thin, 17:noodle_sanxi, 18:noodle_erxi, 19:noodle_leek_leaf, 20:noodle_wide, 21:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 217:spicy_extra, 218:spicy_regular, 219:spicy_mild, 220:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | Meat Broth, Vegan Broth | 215:soup_beef, 216:soup_vegan | REVIEW_REQUIRED |
| ADD_ON | Extra Meat, Extra Fried Egg, Extra Tea Boil Egg, Extra Vegetable, Extra Noodle | 205:carrot_slice, 206:mushroom, 207:seaweed, 208:corn, 209:broccoli, 210:bok_choy, 211:fried_egg, 212:extra_meat, 213:tea_egg, 214:extra_noodle | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 197:remove_carrot, 198:remove_seaweed, 199:remove_mushroom, 200:remove_corn, 201:remove_broccoli, 202:remove_bok_choy, 203:less_noodle, 204:remove_noodle | REVIEW_REQUIRED |

### Stir-fried Tomato Beef Noodle (Beef) → tomato_chow_mein

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | NONE | NONE | NONE |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 78:spicy_extra, 79:spicy_regular, 80:spicy_mild, 81:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Add Fried Egg, Add Tea Boil Egg | 76:tea_egg, 77:fried_egg | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 68:remove_tomato, 69:remove_all_vegetables, 70:remove_zucchini, 71:remove_cabbage, 72:remove_broccoli, 73:remove_green_pepper, 74:remove_onion, 75:remove_bean_sprouts | REVIEW_REQUIRED |

### Traditional Lanzhou Hand-pull Beef Noodle → traditional_beef_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | Regular, Large | 286:size_large, 287:size_regular | REVIEW_REQUIRED |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 1:noodle_capillary, 2:noodle_thin, 3:noodle_sanxi, 4:noodle_erxi, 5:noodle_leek_leaf, 6:noodle_wide, 7:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 282:spicy_extra, 283:spicy_regular, 284:spicy_mild, 285:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Extra Meat, Extra Fried Egg, Extra Tea Boil Egg, Extra Vegetable, Extra Noodle | 36:tea_egg, 37:extra_meat, 276:extra_radish, 277:green_onion, 278:cilantro, 279:bok_choy, 280:extra_noodle, 281:fried_egg | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 270:remove_radish, 271:less_noodle, 272:remove_noodle, 273:remove_beef, 274:remove_green_onion, 275:remove_cilantro | REVIEW_REQUIRED |

### Vegetable Chow Mein → vegetable_chow_mein

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | NONE | NONE | NONE |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 52:spicy_extra, 53:spicy_regular, 54:spicy_mild, 55:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Add Fried Egg, Add Tea Boil Egg | 50:tea_egg, 51:fried_egg | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 42:remove_tomato, 43:remove_all_vegetables, 44:remove_zucchini, 45:remove_cabbage, 46:remove_broccoli, 47:remove_green_pepper, 48:remove_onion, 49:remove_bean_sprouts | REVIEW_REQUIRED |

### Zha Jiang Noodle → zha_jiang_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 29:noodle_capillary, 30:noodle_thin, 31:noodle_sanxi, 32:noodle_erxi, 33:noodle_leek_leaf, 34:noodle_wide, 35:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 186:spicy_extra, 187:spicy_regular, 188:spicy_mild, 189:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Extra Noodle, Extra Vegetable, Extra Fried Egg, Extra Tea Boil Egg | 178:green_onion, 179:cilantro, 180:extra_sauce, 181:bok_choy, 182:fried_egg, 183:extra_meat, 184:tea_egg, 185:extra_noodle | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 172:remove_bok_choy, 173:remove_edamame, 174:remove_cucumber, 175:remove_carrot, 176:remove_green_onion, 177:remove_cilantro | REVIEW_REQUIRED |

### Beef Lanzhou Noodles Special Combo → traditional_beef_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | Regular, Large | 286:size_large, 287:size_regular | REVIEW_REQUIRED |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 1:noodle_capillary, 2:noodle_thin, 3:noodle_sanxi, 4:noodle_erxi, 5:noodle_leek_leaf, 6:noodle_wide, 7:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 282:spicy_extra, 283:spicy_regular, 284:spicy_mild, 285:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Extra Meat, Extra Fried Egg, Extra Tea Boil Egg, Extra Vegetable, Extra Noodle | 36:tea_egg, 37:extra_meat, 276:extra_radish, 277:green_onion, 278:cilantro, 279:bok_choy, 280:extra_noodle, 281:fried_egg | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 270:remove_radish, 271:less_noodle, 272:remove_noodle, 273:remove_beef, 274:remove_green_onion, 275:remove_cilantro | REVIEW_REQUIRED |

### Chicken Chow Mein Combo → chicken_chow_mein

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | NONE | NONE | NONE |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 104:spicy_extra, 105:spicy_regular, 106:spicy_mild, 107:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | NONE | 102:tea_egg, 103:fried_egg | LOCAL_ONLY |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 94:remove_tomato, 95:remove_all_vegetables, 96:remove_zucchini, 97:remove_cabbage, 98:remove_broccoli, 99:remove_green_pepper, 100:remove_onion, 101:remove_bean_sprouts | REVIEW_REQUIRED |

### Dandan Noodle Combo (With Peanuts) → dan_dan_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 22:noodle_capillary, 23:noodle_thin, 24:noodle_sanxi, 25:noodle_erxi, 26:noodle_leek_leaf, 27:noodle_wide, 28:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 157:spicy_extra, 158:spicy_regular, 159:spicy_mild, 160:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Extra Noodle, Extra Vegetable, Extra Fried Egg, Extra Tea Boil Egg | 149:green_onion, 150:cilantro, 151:extra_sauce, 152:bok_choy, 153:fried_egg, 154:extra_meat, 155:tea_egg, 156:extra_noodle | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 145:remove_bok_choy, 146:remove_peanut, 147:remove_green_onion, 148:remove_cilantro | REVIEW_REQUIRED |

### Lanzhou Beef Chow Mein Combo → beef_chow_mein

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | NONE | NONE | NONE |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 130:spicy_extra, 131:spicy_regular, 132:spicy_mild, 133:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | NONE | 128:tea_egg, 129:fried_egg | LOCAL_ONLY |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 120:remove_tomato, 121:remove_all_vegetables, 122:remove_zucchini, 123:remove_cabbage, 124:remove_broccoli, 125:remove_green_pepper, 126:remove_onion, 127:remove_bean_sprouts | REVIEW_REQUIRED |

### Noodles with Vegetables Combo → vegetable_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | Regular, Large | 221:size_large, 222:size_regular | REVIEW_REQUIRED |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 15:noodle_capillary, 16:noodle_thin, 17:noodle_sanxi, 18:noodle_erxi, 19:noodle_leek_leaf, 20:noodle_wide, 21:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 217:spicy_extra, 218:spicy_regular, 219:spicy_mild, 220:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | 215:soup_beef, 216:soup_vegan | LOCAL_ONLY |
| ADD_ON | Extra Meat, Extra Fried Egg, Extra Tea Boil Egg, Extra Vegetable, Extra Noodle | 205:carrot_slice, 206:mushroom, 207:seaweed, 208:corn, 209:broccoli, 210:bok_choy, 211:fried_egg, 212:extra_meat, 213:tea_egg, 214:extra_noodle | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 197:remove_carrot, 198:remove_seaweed, 199:remove_mushroom, 200:remove_corn, 201:remove_broccoli, 202:remove_bok_choy, 203:less_noodle, 204:remove_noodle | REVIEW_REQUIRED |

### Stir-fried Tomato Beef Noodle Combo → tomato_chow_mein

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | NONE | NONE | NONE |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 78:spicy_extra, 79:spicy_regular, 80:spicy_mild, 81:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | NONE | 76:tea_egg, 77:fried_egg | LOCAL_ONLY |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 68:remove_tomato, 69:remove_all_vegetables, 70:remove_zucchini, 71:remove_cabbage, 72:remove_broccoli, 73:remove_green_pepper, 74:remove_onion, 75:remove_bean_sprouts | REVIEW_REQUIRED |

### Vegetable Chow Mein Combo → vegetable_chow_mein

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | NONE | NONE | NONE |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 52:spicy_extra, 53:spicy_regular, 54:spicy_mild, 55:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | NONE | 50:tea_egg, 51:fried_egg | LOCAL_ONLY |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 42:remove_tomato, 43:remove_all_vegetables, 44:remove_zucchini, 45:remove_cabbage, 46:remove_broccoli, 47:remove_green_pepper, 48:remove_onion, 49:remove_bean_sprouts | REVIEW_REQUIRED |

### Zhajiang Noodles Combo → zha_jiang_noodle

| Semantic | Uber | Local active DB options | State |
| --- | --- | --- | --- |
| SIZE | NONE | NONE | NONE |
| NOODLE_TYPE | Fine, One Fine, Two Fine, Three Fine, Leek Leaves, Wide, Big Wide | 29:noodle_capillary, 30:noodle_thin, 31:noodle_sanxi, 32:noodle_erxi, 33:noodle_leek_leaf, 34:noodle_wide, 35:noodle_extra_wide | REVIEW_REQUIRED |
| SPICY_LEVEL | Non-spicy, Mild Spicy, Regular Spicy, Extra Spicy | 186:spicy_extra, 187:spicy_regular, 188:spicy_mild, 189:spicy_none | REVIEW_REQUIRED |
| SOUP_BASE | NONE | NONE | NONE |
| ADD_ON | Extra Noodle, Extra Vegetable, Extra Fried Egg, Extra Tea Boil Egg | 178:green_onion, 179:cilantro, 180:extra_sauce, 181:bok_choy, 182:fried_egg, 183:extra_meat, 184:tea_egg, 185:extra_noodle | REVIEW_REQUIRED |
| REMOVE | Non Scallion/ Green Onion, Non Coriander | 172:remove_bok_choy, 173:remove_edamame, 174:remove_cucumber, 175:remove_carrot, 176:remove_green_onion, 177:remove_cilantro | REVIEW_REQUIRED |

## G. Missing / Extra 与价格汇总

ONLY 是本次未确认候选图的差集，不声称这些物品现实中绝不存在；包括未能唯一分辨的品牌、口味和规格。LOCAL_ONLY_OPTIONS 以 active item 下 active DB 行为全集，legacy combo 行单列说明。

| Metric | Count |
| --- | --- |
| UBER_ROOT_ITEMS | 41 |
| LOCAL_ACTIVE_ITEMS | 35 |
| ITEM_CONFIDENCE | {'NO_MATCH': 8, 'REVIEW_REQUIRED': 33} |
| UBER_UNIQUE_MODIFIERS | 33 |
| ITEM_CONTEXT_MODIFIERS | 258 |
| LOCAL_ACTIVE_OPTIONS | 332 |
| LOCAL_ACTIVE_OPTIONS_ON_ACTIVE_ITEMS | 325 |
| MODIFIER_UNIQUE_CONFIDENCE | {'REVIEW_REQUIRED': 28, 'NO_MATCH': 5} |
| MODIFIER_CONTEXT_CONFIDENCE | {'REVIEW_REQUIRED': 203, 'NO_MATCH': 55} |
| UBER_ONLY_ITEMS | 8 |
| LOCAL_ONLY_ITEMS | 10 |
| UBER_ONLY_MODIFIERS | 5 |
| LOCAL_ONLY_OPTIONS | 198 |

### UBER_ONLY_ITEMS

| ID | Name | Reason |
| --- | --- | --- |
| Green_Grape_Soju | Green Grape Soju | 本地 shochu_fruit 只有通用果味烧酒，无法保留独立口味，规格亦未确认 |
| Hakutsuru_Junmai_Gin | Hakutsuru Junmai Ginjo Sake | 本地 lg_sake/sm_sake 区分大小，Uber区分品牌/品种；无容量证据，候选冲突 |
| Good_Day_Lychee_Soju | Lychee Soju | 本地 shochu_fruit 只有通用果味烧酒，无法保留独立口味，规格亦未确认 |
| Melon_Soju | Melon Soju | 本地 shochu_fruit 只有通用果味烧酒，无法保留独立口味，规格亦未确认 |
| Peach_Soju | Peach Soju | 本地 shochu_fruit 只有通用果味烧酒，无法保留独立口味，规格亦未确认 |
| Nigori_Sake | Sayuri Nigori Sake | 本地 lg_sake/sm_sake 区分大小，Uber区分品牌/品种；无容量证据，候选冲突 |
| 540c3a0b-3830-4bc6-b706-14638c7e6352 | Sweet & Sour Mini Fries | 可能有人认为是土豆丝，但英文名称/描述指薯条；不能据此确认 shredded_potato |
| 79f85c57-ddb3-4f3f-87c6-9a07ad4f6af2 | Sprite | 本地 seven_up 是七喜，品牌不同，不当成同一SKU |

### LOCAL_ONLY_ITEMS / LOCAL_ONLY price

| ID | SKU | Name | Price class | Price |
| --- | --- | --- | --- | --- |
| 9 | shredded_potato | 土豆丝 / Shredded Potato | LOCAL_ONLY | 4.99 |
| 18 | fried_egg | 煎蛋 / Fried Egg | LOCAL_ONLY | 1.99 |
| 24 | cold_noodle_shredded_chicken | 鸡丝凉面 / Cold Noodle with Shredded Chicken | LOCAL_ONLY | 17.99 |
| 26 | pickled_vegetable_beef_noodle | 酸菜牛肉面 / Pickled Vegetable Beef Noodle | LOCAL_ONLY | 17.99 |
| 28 | beef | 纯牛肉 / pure_beef | LOCAL_ONLY | 6.99 |
| 29 | tendon | 纯牛筋 / pure_tendon | LOCAL_ONLY | 6.99 |
| 30 | seven_up | 七喜 / Seven Up | LOCAL_ONLY | 3.00 |
| 32 | shochu_fruit | 果味烧酒 / Shochu Fruit | LOCAL_ONLY | 24.00 |
| 33 | lg_sake | 大清酒 / Large Sake | LOCAL_ONLY | 18.00 |
| 36 | sm_sake | 小清酒 / Small Sake | LOCAL_ONLY | 15.00 |

### UBER_ONLY_MODIFIERS

| ID | Name | Reason |
| --- | --- | --- |
| 2ca784f1-b086-42bb-aee0-64840d75cacf | Extra Vegetable | 本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| 4c32e520-aca2-4a31-b7e5-ba3f05a83dde | One Fine | 与 Fine 同时存在；本地毛细/细两候选，需确认具体面型 |
| 5c7603d6-37a5-4948-ae62-0fbdfb3f5ec1 | Sweet & Sour Mini Fries | 本地 combo_shredded_potato 土豆丝并非可确认的薯条；需确认真实菜义 |
| a79813fd-2d14-45bd-a57c-462a8ddbb70f | Extra Vegetable | 本地 bok_choy/extra_radish 等具体加菜多个候选；Uber未说明蔬菜组成 |
| c6a744c9-2d5f-4d1e-8e79-9deb3d917d03 | Fine | 与 One Fine 同时存在；本地毛细/细两候选，不能只凭翻译唯一分配 |

### LOCAL_ONLY_OPTIONS / LOCAL_ONLY price

| Item | Option ID | Code / Group | Name | Price class / Delta | Parent |
| --- | --- | --- | --- | --- | --- |
| traditional_beef_noodle | 1 | noodle_capillary / NOODLE_TYPE | 毛细 / Capillary | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 2 | noodle_thin / NOODLE_TYPE | 细 / Thin | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 8 | noodle_capillary / NOODLE_TYPE | 毛细 / Capillary | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 9 | noodle_thin / NOODLE_TYPE | 细 / Thin | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 15 | noodle_capillary / NOODLE_TYPE | 毛细 / Capillary | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 16 | noodle_thin / NOODLE_TYPE | 细 / Thin | LOCAL_ONLY / 0.00 | — |
| dan_dan_noodle | 22 | noodle_capillary / NOODLE_TYPE | 毛细 / Capillary | LOCAL_ONLY / 0.00 | — |
| dan_dan_noodle | 23 | noodle_thin / NOODLE_TYPE | 细 / Thin | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 29 | noodle_capillary / NOODLE_TYPE | 毛细 / Capillary | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 30 | noodle_thin / NOODLE_TYPE | 细 / Thin | LOCAL_ONLY / 0.00 | — |
| cucumber_salad | 38 | remove_peanut / REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 42 | remove_tomato / REMOVE | 走番茄 / No Tomato | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 43 | remove_all_vegetables / REMOVE | 走所有菜 / No Vegetables | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 44 | remove_zucchini / REMOVE | 走西葫芦 / No Zucchini | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 45 | remove_cabbage / REMOVE | 走大头菜 / No Cabbage | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 46 | remove_broccoli / REMOVE | 走西兰花 / No Broccoli | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 47 | remove_green_pepper / REMOVE | 走青椒 / No Green Pepper | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 48 | remove_onion / REMOVE | 走洋葱 / No Onion | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 49 | remove_bean_sprouts / REMOVE | 走豆芽 / No Bean Sprouts | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 56 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 57 |
| vegetable_chow_mein | 58 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 60 | combo_tea_egg / COMBO_EGG | 套餐卤蛋 / Combo Tea Egg | LOCAL_ONLY / 0.00 | — |
| vegetable_chow_mein | 62 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| tomato_chow_mein | 68 | remove_tomato / REMOVE | 走番茄 / No Tomato | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 69 | remove_all_vegetables / REMOVE | 走所有菜 / No Vegetables | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 70 | remove_zucchini / REMOVE | 走西葫芦 / No Zucchini | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 71 | remove_cabbage / REMOVE | 走大头菜 / No Cabbage | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 72 | remove_broccoli / REMOVE | 走西兰花 / No Broccoli | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 73 | remove_green_pepper / REMOVE | 走青椒 / No Green Pepper | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 74 | remove_onion / REMOVE | 走洋葱 / No Onion | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 75 | remove_bean_sprouts / REMOVE | 走豆芽 / No Bean Sprouts | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 82 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 83 |
| tomato_chow_mein | 84 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 86 | combo_tea_egg / COMBO_EGG | 套餐卤蛋 / Combo Tea Egg | LOCAL_ONLY / 0.00 | — |
| tomato_chow_mein | 88 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| chicken_chow_mein | 94 | remove_tomato / REMOVE | 走番茄 / No Tomato | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 95 | remove_all_vegetables / REMOVE | 走所有菜 / No Vegetables | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 96 | remove_zucchini / REMOVE | 走西葫芦 / No Zucchini | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 97 | remove_cabbage / REMOVE | 走大头菜 / No Cabbage | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 98 | remove_broccoli / REMOVE | 走西兰花 / No Broccoli | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 99 | remove_green_pepper / REMOVE | 走青椒 / No Green Pepper | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 100 | remove_onion / REMOVE | 走洋葱 / No Onion | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 101 | remove_bean_sprouts / REMOVE | 走豆芽 / No Bean Sprouts | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 108 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 109 |
| chicken_chow_mein | 110 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 112 | combo_tea_egg / COMBO_EGG | 套餐卤蛋 / Combo Tea Egg | LOCAL_ONLY / 0.00 | — |
| chicken_chow_mein | 114 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| beef_chow_mein | 120 | remove_tomato / REMOVE | 走番茄 / No Tomato | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 121 | remove_all_vegetables / REMOVE | 走所有菜 / No Vegetables | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 122 | remove_zucchini / REMOVE | 走西葫芦 / No Zucchini | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 123 | remove_cabbage / REMOVE | 走大头菜 / No Cabbage | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 124 | remove_broccoli / REMOVE | 走西兰花 / No Broccoli | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 125 | remove_green_pepper / REMOVE | 走青椒 / No Green Pepper | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 126 | remove_onion / REMOVE | 走洋葱 / No Onion | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 127 | remove_bean_sprouts / REMOVE | 走豆芽 / No Bean Sprouts | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 134 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 135 |
| beef_chow_mein | 136 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 138 | combo_tea_egg / COMBO_EGG | 套餐卤蛋 / Combo Tea Egg | LOCAL_ONLY / 0.00 | — |
| beef_chow_mein | 140 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| dan_dan_noodle | 145 | remove_bok_choy / REMOVE | 走上海青 / No Bok Choy | LOCAL_ONLY / 0.00 | — |
| dan_dan_noodle | 146 | remove_peanut / REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | — |
| dan_dan_noodle | 149 | green_onion / ADD_ON | 加葱 / Extra Green Onion | LOCAL_ONLY / 0.00 | — |
| dan_dan_noodle | 150 | cilantro / ADD_ON | 加香菜 / Extra Cilantro | LOCAL_ONLY / 0.00 | — |
| dan_dan_noodle | 151 | extra_sauce / ADD_ON | 加酱 / Extra Sauce | LOCAL_ONLY / 3.00 | — |
| dan_dan_noodle | 152 | bok_choy / ADD_ON | 加上海青 / Extra Bok Choy | LOCAL_ONLY / 3.00 | — |
| dan_dan_noodle | 154 | extra_meat / ADD_ON | 加肉 / Extra Meat | LOCAL_ONLY / 6.99 | — |
| dan_dan_noodle | 161 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 162 |
| dan_dan_noodle | 163 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| dan_dan_noodle | 165 | combo_fried_egg / COMBO_EGG | 套餐煎蛋 / Combo Fried Egg | LOCAL_ONLY / 0.00 | — |
| dan_dan_noodle | 167 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| zha_jiang_noodle | 172 | remove_bok_choy / REMOVE | 走上海青 / No Bok Choy | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 173 | remove_edamame / REMOVE | 走毛豆 / No Edamame | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 174 | remove_cucumber / REMOVE | 走黄瓜 / No Cucumber | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 175 | remove_carrot / REMOVE | 走胡萝卜 / No Carrot | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 178 | green_onion / ADD_ON | 加葱 / Extra Green Onion | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 179 | cilantro / ADD_ON | 加香菜 / Extra Cilantro | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 180 | extra_sauce / ADD_ON | 加酱 / Extra Sauce | LOCAL_ONLY / 3.00 | — |
| zha_jiang_noodle | 181 | bok_choy / ADD_ON | 加上海青 / Extra Bok Choy | LOCAL_ONLY / 3.00 | — |
| zha_jiang_noodle | 183 | extra_meat / ADD_ON | 加肉 / Extra Meat | LOCAL_ONLY / 6.99 | — |
| zha_jiang_noodle | 190 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 191 |
| zha_jiang_noodle | 192 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 194 | combo_fried_egg / COMBO_EGG | 套餐煎蛋 / Combo Fried Egg | LOCAL_ONLY / 0.00 | — |
| zha_jiang_noodle | 196 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| vegetable_noodle | 197 | remove_carrot / REMOVE | 走胡萝卜片 / No Carrot Slice | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 198 | remove_seaweed / REMOVE | 走海菜 / No Seaweed | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 199 | remove_mushroom / REMOVE | 走蘑菇 / No Mushroom | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 200 | remove_corn / REMOVE | 走玉米 / No Corn | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 201 | remove_broccoli / REMOVE | 走西兰花 / No Broccoli | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 202 | remove_bok_choy / REMOVE | 走上海青 / No Bok Choy | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 203 | less_noodle / REMOVE | 少面 / Less Noodle | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 204 | remove_noodle / REMOVE | 走面 / No Noodle | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 205 | carrot_slice / ADD_ON | 加胡萝卜片 / Extra Carrot Slice | LOCAL_ONLY / 3.00 | — |
| vegetable_noodle | 206 | mushroom / ADD_ON | 加蘑菇 / Extra Mushroom | LOCAL_ONLY / 3.00 | — |
| vegetable_noodle | 207 | seaweed / ADD_ON | 加海菜 / Extra Seaweed | LOCAL_ONLY / 3.00 | — |
| vegetable_noodle | 208 | corn / ADD_ON | 加玉米 / Extra Corn | LOCAL_ONLY / 3.00 | — |
| vegetable_noodle | 209 | broccoli / ADD_ON | 加西兰花 / Extra Broccoli | LOCAL_ONLY / 3.00 | — |
| vegetable_noodle | 210 | bok_choy / ADD_ON | 加上海青 / Extra Bok Choy | LOCAL_ONLY / 3.00 | — |
| vegetable_noodle | 223 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 224 |
| vegetable_noodle | 225 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 227 | combo_fried_egg / COMBO_EGG | 套餐煎蛋 / Combo Fried Egg | LOCAL_ONLY / 0.00 | — |
| vegetable_noodle | 229 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| braised_beef_tendon_noodle | 236 | remove_radish / REMOVE | 走萝卜 / No Radish | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 237 | less_noodle / REMOVE | 少面 / Less Noodle | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 238 | remove_noodle / REMOVE | 走面 / No Noodle | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 239 | remove_beef / REMOVE | 走牛筋 / No Beef | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 242 | extra_radish / ADD_ON | 加萝卜 / Extra Radish | LOCAL_ONLY / 3.00 | — |
| braised_beef_tendon_noodle | 243 | green_onion / ADD_ON | 加葱 / Extra Green Onion | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 244 | cilantro / ADD_ON | 加香菜 / Extra Cilantro | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 245 | bok_choy / ADD_ON | 加上海青 / Extra Bok Choy | LOCAL_ONLY / 3.00 | — |
| braised_beef_tendon_noodle | 257 | combo_cucumber_salad / COMBO_SIDE | 套餐拌黄瓜 / Combo Cucumber Salad | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 258 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 259 | combo_edamame / COMBO_SIDE | 套餐毛豆 / Combo Edamame | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 260 | combo_fried_egg / COMBO_EGG | 套餐煎蛋 / Combo Fried Egg | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 261 | combo_tea_egg / COMBO_EGG | 套餐卤蛋 / Combo Tea Egg | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 270 | remove_radish / REMOVE | 走萝卜 / No Radish | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 271 | less_noodle / REMOVE | 少面 / Less Noodle | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 272 | remove_noodle / REMOVE | 走面 / No Noodle | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 273 | remove_beef / REMOVE | 走牛肉 / No Beef | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 276 | extra_radish / ADD_ON | 加萝卜 / Extra Radish | LOCAL_ONLY / 3.00 | — |
| traditional_beef_noodle | 277 | green_onion / ADD_ON | 加葱 / Extra Green Onion | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 278 | cilantro / ADD_ON | 加香菜 / Extra Cilantro | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 279 | bok_choy / ADD_ON | 加上海青 / Extra Bok Choy | LOCAL_ONLY / 3.00 | — |
| traditional_beef_noodle | 288 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 289 |
| traditional_beef_noodle | 290 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 292 | combo_fried_egg / COMBO_EGG | 套餐煎蛋 / Combo Fried Egg | LOCAL_ONLY / 0.00 | — |
| traditional_beef_noodle | 294 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| cold_noodle_shredded_chicken | 299 | remove_cucumber / REMOVE | 走黄瓜丝 / remove cucumber | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 300 | remove_carrot / REMOVE | 走胡萝卜 / No Carrot | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 301 | remove_peanut / REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 302 | remove_green_onion / REMOVE | 走葱 / No Green Onion | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 303 | remove_cilantro / REMOVE | 走香菜 / No Cilantro | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 304 | green_onion / ADD_ON | 加葱 / Extra Green Onion | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 305 | cilantro / ADD_ON | 加香菜 / Extra Cilantro | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 307 | fried_egg / ADD_ON | 加煎蛋 / Extra Fried Egg | LOCAL_ONLY / 1.99 | — |
| cold_noodle_shredded_chicken | 308 | extra_meat / ADD_ON | 加肉 / Extra Meat | LOCAL_ONLY / 6.99 | — |
| cold_noodle_shredded_chicken | 309 | tea_egg / ADD_ON | 加蛋 / Extra Tea Egg | LOCAL_ONLY / 1.99 | — |
| cold_noodle_shredded_chicken | 310 | extra_noodle / ADD_ON | 加面 / Extra Noodle | LOCAL_ONLY / 3.99 | — |
| cold_noodle_shredded_chicken | 311 | spicy_extra / SPICY_LEVEL | 加辣 / Extra | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 312 | spicy_regular / SPICY_LEVEL | 正常辣 / Regular | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 313 | spicy_mild / SPICY_LEVEL | 少辣 / Mild | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 314 | spicy_none / SPICY_LEVEL | 不辣 / Non-Spicy | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 315 | noodle_extra_wide / NOODLE_TYPE | 大宽 / Extra Wide | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 316 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 319 |
| cold_noodle_shredded_chicken | 317 | noodle_wide / NOODLE_TYPE | 宽 / Wide | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 318 | noodle_capillary / NOODLE_TYPE | 毛细 / Capillary | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 319 | combo_cucumber_salad / COMBO_SIDE | 套餐拌黄瓜 / Combo Cucumber Salad | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 320 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 321 | noodle_sanxi / NOODLE_TYPE | 三细 / Sanxi | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 322 | combo_edamame / COMBO_SIDE | 套餐毛豆 / Combo Edamame | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 323 | noodle_erxi / NOODLE_TYPE | 二细 / Erxi | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 324 | noodle_leek_leaf / NOODLE_TYPE | 韭叶 / Leek Leaf | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 325 | combo_fried_egg / COMBO_EGG | 套餐煎蛋 / Combo Fried Egg | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 326 | noodle_thin / NOODLE_TYPE | 细 / Thin | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 327 | combo_tea_egg / COMBO_EGG | 套餐卤蛋 / Combo Tea Egg | LOCAL_ONLY / 0.00 | — |
| cold_noodle_shredded_chicken | 328 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| pickled_vegetable_beef_noodle | 335 | remove_radish / REMOVE | 走萝卜 / No Radish | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 336 | less_noodle / REMOVE | 少面 / Less Noodle | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 337 | remove_noodle / REMOVE | 走面 / No Noodle | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 338 | remove_beef / REMOVE | 走牛肉 / No Beef | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 339 | remove_green_onion / REMOVE | 走葱 / No Green Onion | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 340 | remove_cilantro / REMOVE | 走香菜 / No Cilantro | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 341 | extra_radish / ADD_ON | 加萝卜 / Extra Radish | LOCAL_ONLY / 3.00 | — |
| pickled_vegetable_beef_noodle | 342 | green_onion / ADD_ON | 加葱 / Extra Green Onion | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 343 | cilantro / ADD_ON | 加香菜 / Extra Cilantro | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 344 | bok_choy / ADD_ON | 加上海青 / Extra Bok Choy | LOCAL_ONLY / 3.00 | — |
| pickled_vegetable_beef_noodle | 345 | fried_egg / ADD_ON | 加煎蛋 / Extra Fried Egg | LOCAL_ONLY / 1.99 | — |
| pickled_vegetable_beef_noodle | 346 | extra_meat / ADD_ON | 加肉 / Extra Beef | LOCAL_ONLY / 6.99 | — |
| pickled_vegetable_beef_noodle | 347 | tea_egg / ADD_ON | 加蛋 / Extra Tea Egg | LOCAL_ONLY / 1.99 | — |
| pickled_vegetable_beef_noodle | 348 | extra_noodle / ADD_ON | 加面 / Extra Noodle | LOCAL_ONLY / 3.99 | — |
| pickled_vegetable_beef_noodle | 349 | spicy_extra / SPICY_LEVEL | 加辣 / Extra | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 350 | spicy_regular / SPICY_LEVEL | 正常辣 / Regular | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 351 | spicy_mild / SPICY_LEVEL | 少辣 / Mild | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 352 | spicy_none / SPICY_LEVEL | 不辣 / Non-Spicy | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 353 | noodle_extra_wide / NOODLE_TYPE | 大宽 / Extra Wide | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 354 | noodle_wide / NOODLE_TYPE | 宽 / Wide | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 355 | noodle_leek_leaf / NOODLE_TYPE | 韭叶 / Leek Leaf | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 356 | noodle_capillary / NOODLE_TYPE | 毛细 / Capillary | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 357 | noodle_thin / NOODLE_TYPE | 细 / Thin | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 358 | noodle_erxi / NOODLE_TYPE | 二细 / Erxi | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 359 | noodle_sanxi / NOODLE_TYPE | 三细 / Sanxi | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 360 | size_large / SIZE | 大碗 / Large | LOCAL_ONLY / 2.00 | — |
| pickled_vegetable_beef_noodle | 361 | size_regular / SIZE | 中碗 / Regular | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 362 | combo_cucumber_no_peanut / COMBO_SIDE_REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | 363 |
| pickled_vegetable_beef_noodle | 363 | combo_cucumber_salad / COMBO_SIDE | 套餐拌黄瓜 / Combo Cucumber Salad | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 364 | combo_shredded_potato / COMBO_SIDE | 套餐土豆丝 / Combo Shredded Potato | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 365 | combo_edamame / COMBO_SIDE | 套餐毛豆 / Combo Edamame | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 366 | combo_fried_egg / COMBO_EGG | 套餐煎蛋 / Combo Fried Egg | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 367 | combo_tea_egg / COMBO_EGG | 套餐卤蛋 / Combo Tea Egg | LOCAL_ONLY / 0.00 | — |
| pickled_vegetable_beef_noodle | 368 | combo / COMBO | 套餐 / Combo | LOCAL_ONLY / 5.00 | — |
| braised_beef_shank_salad | 375 | remove_green_onion / REMOVE | 走葱 / No Green Onion | LOCAL_ONLY / 0.00 | — |
| braised_beef_shank_salad | 376 | remove_crushed_peanut / REMOVE | 走花生碎 / No Crushed Peanut | LOCAL_ONLY / 0.00 | — |
| braised_beef_shank_salad | 377 | remove_cilantro / REMOVE | 走香菜 / No Cilantro | LOCAL_ONLY / 0.00 | — |
| shredded_potato | 378 | remove_cilantro / REMOVE | 走香菜 / No Cilantro | LOCAL_ONLY / 0.00 | — |
| shredded_potato | 379 | remove_peanut / REMOVE | 走花生 / No Peanut | LOCAL_ONLY / 0.00 | — |
| shredded_potato | 380 | remove_onion / REMOVE | 走洋葱 / No Onion | LOCAL_ONLY / 0.00 | — |
| braised_beef_tendon_noodle | 382 | size_small / SIZE | 小碗 / Small | LOCAL_ONLY / -2.00 | — |
| braised_beef_tendon_noodle | 3437 | addon_beef_tendons / ADD_ON | 加牛筋 / addon_beef_tendons | LOCAL_ONLY / 6.99 | — |
| braised_beef_tendon_noodle | 3438 | removebaicai / REMOVE | 走上海青 / zoushanghaiqing | LOCAL_ONLY / 0.00 | — |

### 逐行价格分类总数

| Level | MATCH | DIFF | UBER_ONLY | LOCAL_ONLY |
| --- | --- | --- | --- | --- |
| items | 5 | 28 | 8 | 10 |
| modifier-contexts | 185 | 18 | 55 | 198 |

所有价格保留原值，没有同步价格。套餐列中的 Uber root vs local base 差额包含套餐本身的构成差异，E 另列 base+COMBO 供比较。

## H. 第一张测试单候选与 YES/NO 清单

FIRST_TEST_COMBINATION_AVAILABLE = **YES（菜单存在、可供人工确认的候选）**。全部仍 REVIEW_REQUIRED；TEST_COMBINATION_FULLY_MAPPED=NO，READY_FOR_REAL_SANDBOX_ORDER_TEST=NO。本轮不下单。

| Reply key | Uber name / ID | Suggested local ID / code / 中文 | Uber / Local price | Status | Confirm YES/NO |
| --- | --- | --- | --- | --- | --- |
| I016 | Traditional Lanzhou Hand-pull Beef Noodle / b940caa7-6e37-4b3b-b964-3e10151e7903 | 1 / traditional_beef_noodle / 传统牛肉面 | 19.99 / 16.99 | REVIEW_REQUIRED | — |
| M089 | Large / 01c44198-eeb9-4452-bc2b-0939bf713a39 | 286 / size_large / 大碗 | 2.00 / 2.00 | REVIEW_REQUIRED | — |
| M093 | Three Fine / d9750fe4-14fe-4efd-bcd6-8ea85774850d | 3 / noodle_sanxi / 三细 | 0.00 / 0.00 | REVIEW_REQUIRED | — |
| M084 | Non-spicy / fa1544e9-4e8e-4a4f-81a1-8643e200028e | 285 / spicy_none / 不辣 | 0.00 / 0.00 | REVIEW_REQUIRED | — |
| M098 | Extra Fried Egg / 62c5d2f7-0f32-4cbe-8514-47060f55ea7d | 281 / fried_egg / 加煎蛋 | 2.50 / 1.99 | REVIEW_REQUIRED | — |
| M103 | Non Coriander / 54c53703-db68-4044-85a3-5f4962715566 | 275 / remove_cilantro / 走香菜 | 0.00 / 0.00 | REVIEW_REQUIRED | — |

可以回复上表六个 Reply key 的 YES/NO；YES 仅确认该 Uber ID → 指定本地 identity 和父菜上下文，不自动确认其他门店、同名 modifier 或价格。价格差异独立保留。

### 全量人工确认索引

两个 CSV 的 Confirmed 初始全部空白。逐行填 YES 或 NO；NO_MATCH 行应保留空白或填 NO，并在 Notes 给出正确目标。不要将全表笼统 YES 当成结构差异已解决。

[Item CSV](UBER_TEST_ITEM_MAPPING_REVIEW_20261002.csv) / [Modifier CSV](UBER_TEST_MODIFIER_MAPPING_REVIEW_20261002.csv)。每行 Review ID 可直接作为回复编号；modifier 含 root/group/parent，不丢上下文。

## I. 验证与安全

只读 OAuth/menu fetch 与只读 DB snapshot；读取的是 Store 1 菜单与配置，不读取客户订单/PII。没有应用代码修改或部署。报告逐项覆盖 41 roots、33 unique modifiers、258 contexts、39 local items、385 local options；CSV全部Confirmed空白。价格原值保留、ID引用和父菜归属核对。

本次为文档/数据审计，Agent 6 采用文档例外，不进行应用构建。origin/main `43d86d11a0354868df209441cf8a4abc1a1bfa91`；独立工作区 branch `codex/uber-test-store-binding-audit-20261001`，保护原工作区。

| Snapshot | SHA256 |
| --- | --- |
| menu | 5f3faf50ae60aefcf2ad76af4fc86d693da355c8709aec768c19f333fede56a9 |
| local | 54f0d76ff74a0ef2b8d5b1261597636fc4e12f47858229f771963692de0f3034 |
| meta | ab63b88ae518ab51ecab52aa6476692079c9240eded44a5e8d4881c921a65160 |

CSV artifact-tool 回读逐格一致；覆盖率、ID引用、父菜归属、空白 Confirmed、凭据扫描及 governance validation 均 PASS。

Execution Time Breakdown（Estimated）：调查/新鲜数据读取 2 分钟；报告与CSV整理 5 分钟；校验/证据/PR同步 2 分钟；合计约 9 分钟。部署、订单测试、Agent 6 runtime review：Not required。

READY_FOR_HUMAN_MAPPING_CONFIRMATION = YES
