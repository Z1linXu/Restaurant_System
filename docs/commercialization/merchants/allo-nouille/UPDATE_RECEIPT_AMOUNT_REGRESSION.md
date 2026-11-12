# Allô 加单收据金额回归

**PASS — 新建合成本机订单的实际 HTTP / Renderer 验收。**

本报告为 API 验收，不代表浏览器 Finish、Print Center UI 或实体打印通过。没有重写原 Order 8 或旧 Job 18；旧加单收据 `$6.88` 原样保留。

时间：`2026-11-12T08:57:57Z`；build：`e7dde0a6e27adf3be00baa9972fee06cdcc5482c`；JAR SHA-256：`9430ee811525bd22bf525d479c864ac86c22d26c45bb93facee8e3f14c02d380`。

目标仅 `127.0.0.1:18512` / `127.0.0.1:26512/restaurant_allo_nouilles_test`，Store `ALLO_NOUILLES_TEST / SYNTHETIC Allo QA`，MOCK、无 endpoint。使用正常 QA Owner Bearer 登录；无生产、菜单改价/改名、路由写入、Pad Worker 或物理打印。

预期金额从批准菜单独立用 Decimal 计算。每次加单应收额为`round_order_total(new_subtotal) - round_order_total(previous_subtotal)`；该批税额为应收额减新增小计。新 context 的 `update_subtotal/update_total` 与实际收据逐项核对。

| 场景 | 新订单 | 初单总计 | 新增加小计 / 税 / 应收 | 当前整单总计 | 旧独立计税应收 | Finish |
| --- | --- | --- | --- | --- | --- | --- |
| A1 takeout → E1 | 9 | 5.74 | 3.99 / 0.59 / 4.58 | 10.32 | 4.59 | completed |
| B1 dine-in → B1 → 合格零价续面 | 10 | 19.53 | 16.99 / 2.55 / 19.54 | 39.07 | 19.53 | completed |

所有 update 使用稳定 idempotency_key，重复同一请求返回原 batch/revision、`already_processed=true`，未新增订单行、batch、Outbox 或重复 Job。每批实际保留一张 GRAB 与一张 FRONTDESK_RECEIPT。

## A1 takeout → E1

```json
{
  "name": "A1 takeout → E1",
  "order_id": 9,
  "initial_subtotal": "4.99",
  "initial_total": "5.74",
  "updates": [
    {
      "batch_id": 2,
      "idempotency_key": "amount-1794473877111365000-1-1",
      "replay_already_processed": true,
      "sku": "AN_E1",
      "subtotal": "3.99",
      "tax": "0.59",
      "total": "4.58",
      "old_independent_total": "4.59",
      "whole_subtotal": "8.98",
      "whole_total": "10.32",
      "cumulative_receipts": "10.32",
      "frontdesk_job_id": 22
    }
  ],
  "finished_status": "completed",
  "final_total": "10.32"
}
```

### Job 19 / GRAB

`source_key=submit:9:GRAB`，SHA-256 `e51aba881de2429ed2e80478bc1c15107c19f4a15d0313dfb353c48df5f511eb`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    20
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "20": "A1 虾云吞汤（3颗）"
  },
  "option_names": {},
  "subtotal": "4.99",
  "total": "5.74",
  "update_subtotal": null,
  "update_total": null,
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text




[[LARGE]]AMT-877111365000[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]A1 虾云吞汤（3颗） x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]

--------------------------------
[[LARGE]]外带[[/LARGE]]
[[SMALL]]03:57[[/SMALL]]
--------------------------------


```

### Job 20 / FRONTDESK_RECEIPT

`source_key=submit:9:FRONTDESK_RECEIPT`，SHA-256 `ece9ff28dec1bb2e26577d6a740d59b1fa077ee612ef1b1dcc016a5fb8c08913`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    20
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "20": "A1 虾云吞汤（3颗）"
  },
  "option_names": {},
  "subtotal": "4.99",
  "total": "5.74",
  "update_subtotal": null,
  "update_total": null,
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text
[[LARGE]]取餐号: AMT-877111365000[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]订单类型: 外带[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x A1 虾云吞汤（3颗）[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$4.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]小计: $4.99[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]税 (14.975%): $0.75[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合计: $5.74[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]提交时间: 2026-11-12 03:57[[/DOUBLE_HEIGHT]]


```

### Job 21 / GRAB_UPDATE

`source_key=update:9:2:GRAB`，SHA-256 `b05677fae281ff97d3f8427984b6b3eab2350fcfcea044689c444af0ed1eaec9`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    21
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "21": "E1 冰淇淋（2球）"
  },
  "option_names": {},
  "subtotal": "8.98",
  "total": "10.32",
  "update_subtotal": null,
  "update_total": null,
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text




[[LARGE]]加单[[/LARGE]]
--------------------------------
[[LARGE]]AMT-877111365000[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]E1 冰淇淋（2球） x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]

--------------------------------
[[LARGE]]外带[[/LARGE]]
[[SMALL]]03:57[[/SMALL]]
--------------------------------


```

### Job 22 / FRONTDESK_RECEIPT_UPDATE

`source_key=update:9:2:FRONTDESK_RECEIPT`，SHA-256 `7dd408f8175fbbfdee4eb7e26fd556fb7e775c49793c4b751f2426a5ee5b72b1`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    21
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "21": "E1 冰淇淋（2球）"
  },
  "option_names": {},
  "subtotal": "8.98",
  "total": "10.32",
  "update_subtotal": "3.99",
  "update_total": "4.58",
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text
[[LARGE]]取餐号: AMT-877111365000[[/LARGE]]
[[LARGE]]加单[[/LARGE]]
[[DOUBLE_HEIGHT]]仅新增菜品[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]订单类型: 外带[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x E1 冰淇淋（2球）[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$3.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]小计: $3.99[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]税（整单舍入） (14.975%): $0.59[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合计: $4.58[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]提交时间: 2026-11-12 03:57[[/DOUBLE_HEIGHT]]


```
## B1 dine-in → B1 → 合格零价续面

```json
{
  "name": "B1 dine-in → B1 → 合格零价续面",
  "order_id": 10,
  "initial_subtotal": "16.99",
  "initial_total": "19.53",
  "updates": [
    {
      "batch_id": 3,
      "idempotency_key": "amount-1794473877111365000-2-1",
      "replay_already_processed": true,
      "sku": "AN_B1",
      "subtotal": "16.99",
      "tax": "2.55",
      "total": "19.54",
      "old_independent_total": "19.53",
      "whole_subtotal": "33.98",
      "whole_total": "39.07",
      "cumulative_receipts": "39.07",
      "frontdesk_job_id": 26
    },
    {
      "batch_id": 4,
      "idempotency_key": "amount-1794473877111365000-2-2",
      "replay_already_processed": true,
      "sku": "AN_REFILL_NOODLES",
      "subtotal": "0.00",
      "tax": "0.00",
      "total": "0.00",
      "old_independent_total": "0.00",
      "whole_subtotal": "33.98",
      "whole_total": "39.07",
      "cumulative_receipts": "39.07",
      "frontdesk_job_id": 28
    }
  ],
  "finished_status": "completed",
  "final_total": "39.07"
}
```

### Job 23 / FRONTDESK_RECEIPT

`source_key=submit:10:FRONTDESK_RECEIPT`，SHA-256 `3dbf021a01c25321daa352e92fd22972fe936706c7d687da0a15510eec4196e4`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    22
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "22": "B1 经典牛肉面"
  },
  "option_names": {
    "11": "细面"
  },
  "subtotal": "16.99",
  "total": "19.53",
  "update_subtotal": null,
  "update_total": null,
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 3[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x B1 经典牛肉面[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]细面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$16.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]小计: $16.99[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]税 (14.975%): $2.54[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合计: $19.53[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]提交时间: 2026-11-12 03:57[[/DOUBLE_HEIGHT]]


```

### Job 24 / GRAB

`source_key=submit:10:GRAB`，SHA-256 `c39a5782ff611978fa61fb05c0725ea9c616c3de44f2df89401bf9562461582d`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    22
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "22": "B1 经典牛肉面"
  },
  "option_names": {
    "11": "细面"
  },
  "subtotal": "16.99",
  "total": "19.53",
  "update_subtotal": null,
  "update_total": null,
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 3[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]B1 经典牛肉面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]细面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:57[[/SMALL]]
--------------------------------


```

### Job 25 / GRAB_UPDATE

`source_key=update:10:3:GRAB`，SHA-256 `f4f27bb6e6dd10f065288f7c48a2ebb1062480ef28d27e187c75d953c34d8a1c`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    23
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "23": "B1 经典牛肉面"
  },
  "option_names": {
    "12": "粗面"
  },
  "subtotal": "33.98",
  "total": "39.07",
  "update_subtotal": null,
  "update_total": null,
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 3[[/LARGE]] (加单)
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]B1 经典牛肉面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]粗面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:57[[/SMALL]]
--------------------------------


```

### Job 26 / FRONTDESK_RECEIPT_UPDATE

`source_key=update:10:3:FRONTDESK_RECEIPT`，SHA-256 `a270ca15229c615330005259510ba20859da3b71bc9e992c9e82526ff776a267`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    23
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "23": "B1 经典牛肉面"
  },
  "option_names": {
    "12": "粗面"
  },
  "subtotal": "33.98",
  "total": "39.07",
  "update_subtotal": "16.99",
  "update_total": "19.54",
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 3[[/LARGE]] (加单)
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x B1 经典牛肉面[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]粗面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$16.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]小计: $16.99[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]税（整单舍入） (14.975%): $2.55[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合计: $19.54[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]提交时间: 2026-11-12 03:57[[/DOUBLE_HEIGHT]]


```

### Job 27 / GRAB_UPDATE

`source_key=update:10:4:GRAB`，SHA-256 `1cc56acb39183fa8a21391b9d4c03a7b80f8c8129be255b76259bf639d80e5dd`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    24
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "24": "续面"
  },
  "option_names": {},
  "subtotal": "33.98",
  "total": "39.07",
  "update_subtotal": null,
  "update_total": null,
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 3[[/LARGE]] (加单)
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]续面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:57[[/SMALL]]
--------------------------------


```

### Job 28 / FRONTDESK_RECEIPT_UPDATE

`source_key=update:10:4:FRONTDESK_RECEIPT`，SHA-256 `46e9fb8b00f1800c60b711d12432d7825e4f50dd076a887aa9a9c54ea9836b2f`。

冻结 context（实际保存值）：

```json
{
  "execution_mode": "MOCK",
  "language": "zh-CN",
  "restaurant_name": "SYNTHETIC Allo QA",
  "settings_revision": 10,
  "kitchen_station_codes": [
    "BACK_KITCHEN"
  ],
  "order_item_ids": [
    24
  ],
  "kitchen_excluded_item_ids": [],
  "item_names": {
    "24": "续面"
  },
  "option_names": {},
  "subtotal": "33.98",
  "total": "39.07",
  "update_subtotal": 0,
  "update_total": 0,
  "version": "ALLO_RECEIPT_V1"
}
```

实际 `rendered_text_snapshot` 原文：

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 3[[/LARGE]] (加单)
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x 续面[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_UPDATE_AMOUNTS_1794473877111365000[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$0.00[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]小计: $0.00[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]税（整单舍入） (14.975%): $0.00[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合计: $0.00[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]提交时间: 2026-11-12 03:57[[/DOUBLE_HEIGHT]]


```

## 原历史和菜单保护

启动前将旧数据全行 hash 与既有 `qa-addon-cleanup-1794472752532852000.json` 比较；测试后再次比较。涵盖 Orders 1–8（包含要求的 2–8）、其订单行/选项、PrintJobs 1–18 和对应 Outbox。全部保持相同；原 Job 18 仍保存 `$6.88`，其文本、payload、bytes 与状态都没有被新实现重写。

```json
{
  "orders": {
    "rows": 8,
    "before_sha256": "0195254c63a1b02391a2bb5bcb8c67cfc6bf6ed7a91febf425b9b4bf67e6f010",
    "after_sha256": "0195254c63a1b02391a2bb5bcb8c67cfc6bf6ed7a91febf425b9b4bf67e6f010",
    "unchanged": true
  },
  "order_items": {
    "rows": 19,
    "before_sha256": "0b6471876ab8ea0273a8f4063dbf76df4df5f6db1b34519cf0cc493520f1a0c9",
    "after_sha256": "0b6471876ab8ea0273a8f4063dbf76df4df5f6db1b34519cf0cc493520f1a0c9",
    "unchanged": true
  },
  "order_item_options": {
    "rows": 10,
    "before_sha256": "707ad19124ce8a62739fe5f1e58f55df4ab11fef006ac01637931098d42e85b5",
    "after_sha256": "707ad19124ce8a62739fe5f1e58f55df4ab11fef006ac01637931098d42e85b5",
    "unchanged": true
  },
  "print_jobs": {
    "rows": 18,
    "before_sha256": "1dee0287bb7f5cdafc0d8f1c04c63ae326b83dbfc3459826626690d6d78a18bf",
    "after_sha256": "1dee0287bb7f5cdafc0d8f1c04c63ae326b83dbfc3459826626690d6d78a18bf",
    "unchanged": true
  },
  "outbox": {
    "rows": 18,
    "before_sha256": "427afe48eeff0fcbfd7b057efa24fce47055fe36db6fa07113b7f9f49ffa674a",
    "after_sha256": "427afe48eeff0fcbfd7b057efa24fce47055fe36db6fa07113b7f9f49ffa674a",
    "unchanged": true
  }
}
```

全部 menu_items、menu_item_options、store_addons、store_pricing_policies 前后全行 hash 相同。只对本脚本新建的订单执行 Finish；Order 8 保持原状态。硬件打印 **NOT TESTED**。
