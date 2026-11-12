# Allô 五组实际 Renderer 验收

结果：**PASS**。时间：`2026-11-12T08:05:01Z`。

全部订单、凭据和设备均为本轮新建本机合成数据。目标仅 `127.0.0.1:18512` / `127.0.0.1:26512/restaurant_allo_nouilles_test`；Store `ALLO_NOUILLES_TEST`，显示名 `SYNTHETIC Allo QA`，`printing_mode=MOCK`。无远程/生产数据库连接、真实订单、LAN 连接、Android Worker 或物理打印。这六单不领取 Pad 队列；另行 PAD_DIRECT HTTP 模拟证据单独保存。此证明不代表纸张或打印机硬件验收。

Artifact SHA-256：`ec2627dbeeeb8cd11e8cd41ad60cf61d0ec97214dcc355d1c738c828d08a05a5`；build 标识：`c2425481ec25257660fa1a73d9ed0ec284eb5fe2`。

本机 Flyway：`V42`，成功 `42`、失败 `0`。

批准菜单 SHA-256：`9a855f6565f426c38957d3004efcf8e944a5e4c3ac472ae27fa061bc9713ab79`。预期金额从该菜单单价与实际选项独立用 Decimal 计算：`tax = round_half_up(subtotal × 0.14975, 2)`，`total = subtotal + tax`。

五组产生六个实际订单，第五组为 Dessert 开/关对照。全部通过实际 Create → signed Submit → 持久化 PrintJob → Finish API。下文小票均直接取保存的 `rendered_text_snapshot`，保留原始 markup；未手写或替换 Renderer 输出。

旧兼容 MOCK Job 的 `execution_mode` 字段可为 `null`；本轮同时逐张验证冻结 `receipt_context.execution_mode=MOCK` 与 `receipt_artifact.transport=MOCK_ONLY_NOT_PHYSICAL_PROOF`。

固定套餐限制：未配置固定Combo/单条portion，非固定套餐证明；仅验证批准菜单的单品等价组合，A2始终为2条整份。

| 场景 | Order ID | GRAB Job | FRONTDESK Job | 预期/实际小计 | 预期/实际税 | 预期/实际总计 | Finish |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1. B1 面型、无 Combo | 2 | 4 | 3 | 16.99/16.99 | 2.54/2.54 | 19.53/19.53 | completed |
| 2. B1 + A2 整份 + paid Add-on | 3 | 5 | 6 | 29.97/29.97 | 4.49/4.49 | 34.46/34.46 | completed |
| 3. C1 + A1 汤 + A2 整份 | 4 | 8 | 7 | 27.97/27.97 | 4.19/4.19 | 32.16/32.16 | completed |
| 4. C6 双拼两蛋白 | 5 | 9 | 10 | 19.99/19.99 | 2.99/2.99 | 22.98/22.98 | completed |
| 5a. 前菜 + 主菜 + 饮品 + Dessert（关闭） | 6 | 12 | 11 | 27.96/27.96 | 4.19/4.19 | 32.15/32.15 | completed |
| 5b. 前菜 + 主菜 + 饮品 + Dessert（开启） | 7 | 13 | 14 | 27.96/27.96 | 4.19/4.19 | 32.15/32.15 | completed |

## 1. B1 面型、无 Combo

B1 经典牛肉面，细面；没有 Combo 选项或额外子菜。

独立预期与 API 实际读回：

```json
{
  "expected": {
    "lines": [
      {
        "code": "B1",
        "quantity": 1,
        "name_zh": "经典牛肉面",
        "name_en": "Classic beef",
        "base_price": "16.99",
        "category": "NOODLE",
        "options": [
          {
            "code": "choice_thin",
            "quantity": 1,
            "name_zh": "细面",
            "name_en": "thin noodles",
            "price": "0.00"
          }
        ],
        "line_amount": "16.99"
      }
    ],
    "subtotal": "16.99",
    "tax_rate": "14.975%",
    "tax": "2.54",
    "total": "19.53",
    "calculation": "sum((approved base price + selected approved option prices) * quantity); tax=round_half_up(subtotal*0.14975,2)"
  },
  "actual": {
    "id": 2,
    "order_no": "ORD-20261112030502-0EA7F9",
    "status": "completed",
    "subtotal_amount": "16.99",
    "total_amount": "19.53",
    "items": [
      {
        "id": 2,
        "menu_item_id": 10,
        "category_code_snapshot": "NOODLE",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_B1",
        "item_name_snapshot_zh": "经典牛肉面",
        "item_name_snapshot_en": "Classic beef",
        "item_name_snapshot_fr": "Bœuf classique",
        "quantity": 1,
        "unit_price": "16.99",
        "line_amount": "16.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": [
          {
            "id": 2,
            "option_id": 10,
            "option_type_snapshot": "addon",
            "option_code_snapshot": "choice_thin",
            "option_group_snapshot": "CUSTOM",
            "parent_option_id_snapshot": null,
            "option_name_snapshot_zh": "细面",
            "option_name_snapshot_en": "thin noodles",
            "option_name_snapshot_fr": "nouilles fines",
            "price_delta": "0.00",
            "quantity": 1
          }
        ]
      }
    ]
  }
}
```

### GRAB — Job 4

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`63eecf0e0654c38fe619b86b401e82eda78c304c65930156932fe3f0490b34e2`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]B1 经典牛肉面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]细面 x1[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:05[[/SMALL]]
--------------------------------


```

### FRONTDESK_RECEIPT — Job 3

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`a78f9ee78187ba6e1637a0728798cc40c8d7cb1b227c5d3d0160dcf21a274cec`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x B1 Classic beef[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]thin noodles x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$16.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Subtotal: $16.99[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Tax (14.975%): $2.54[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Total: $19.53[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Submitted: 2026-11-12 03:05[[/DOUBLE_HEIGHT]]


```

## 2. B1 + A2 整份 + paid Add-on

B1 细面加一份牛肉（$7.99），A2 炸素春卷始终为2条整份（$4.99）。

独立预期与 API 实际读回：

```json
{
  "expected": {
    "lines": [
      {
        "code": "B1",
        "quantity": 1,
        "name_zh": "经典牛肉面",
        "name_en": "Classic beef",
        "base_price": "16.99",
        "category": "NOODLE",
        "options": [
          {
            "code": "choice_thin",
            "quantity": 1,
            "name_zh": "细面",
            "name_en": "thin noodles",
            "price": "0.00"
          },
          {
            "code": "n_extra_beef",
            "quantity": 1,
            "name_zh": "牛肉",
            "name_en": "Beef",
            "price": "7.99"
          }
        ],
        "line_amount": "24.98"
      },
      {
        "code": "A2",
        "quantity": 1,
        "name_zh": "炸素春卷（2条）",
        "name_en": "Vegetable spring rolls (2 rolls)",
        "base_price": "4.99",
        "category": "APPETIZER",
        "options": [],
        "line_amount": "4.99"
      }
    ],
    "subtotal": "29.97",
    "tax_rate": "14.975%",
    "tax": "4.49",
    "total": "34.46",
    "calculation": "sum((approved base price + selected approved option prices) * quantity); tax=round_half_up(subtotal*0.14975,2)"
  },
  "actual": {
    "id": 3,
    "order_no": "ORD-20261112030502-21C430",
    "status": "completed",
    "subtotal_amount": "29.97",
    "total_amount": "34.46",
    "items": [
      {
        "id": 3,
        "menu_item_id": 10,
        "category_code_snapshot": "NOODLE",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_B1",
        "item_name_snapshot_zh": "经典牛肉面",
        "item_name_snapshot_en": "Classic beef",
        "item_name_snapshot_fr": "Bœuf classique",
        "quantity": 1,
        "unit_price": "16.99",
        "line_amount": "24.98",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": [
          {
            "id": 3,
            "option_id": 10,
            "option_type_snapshot": "addon",
            "option_code_snapshot": "choice_thin",
            "option_group_snapshot": "CUSTOM",
            "parent_option_id_snapshot": null,
            "option_name_snapshot_zh": "细面",
            "option_name_snapshot_en": "thin noodles",
            "option_name_snapshot_fr": "nouilles fines",
            "price_delta": "0.00",
            "quantity": 1
          },
          {
            "id": 4,
            "option_id": 13,
            "option_type_snapshot": "addon",
            "option_code_snapshot": "n_extra_beef",
            "option_group_snapshot": "ADD_ON",
            "parent_option_id_snapshot": null,
            "option_name_snapshot_zh": "牛肉",
            "option_name_snapshot_en": "Beef",
            "option_name_snapshot_fr": "bœuf",
            "price_delta": "7.99",
            "quantity": 1
          }
        ]
      },
      {
        "id": 4,
        "menu_item_id": 2,
        "category_code_snapshot": "APPETIZER",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_A2",
        "item_name_snapshot_zh": "炸素春卷（2条）",
        "item_name_snapshot_en": "Vegetable spring rolls (2 rolls)",
        "item_name_snapshot_fr": "Rouleaux impériaux aux légumes (2)",
        "quantity": 1,
        "unit_price": "4.99",
        "line_amount": "4.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": []
      }
    ]
  }
}
```

未配置固定Combo/单条portion，非固定套餐证明；仅验证批准菜单的单品等价组合，A2始终为2条整份。

### FRONTDESK_RECEIPT — Job 6

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`eb48696d2b34bf32091ce240d85a871df8cbd569960bcc53d693bbf86783de59`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x B1 Classic beef[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]thin noodles x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Beef x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$24.98[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x A2 Vegetable spring rolls (2 rolls)[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$4.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Subtotal: $29.97[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Tax (14.975%): $4.49[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Total: $34.46[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Submitted: 2026-11-12 03:05[[/DOUBLE_HEIGHT]]


```

### GRAB — Job 5

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`bfcc07465e4ecdb7f7fc6ca4387713f551f4bc446e787de60067aa7619c3e004`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]B1 经典牛肉面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]细面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]牛肉 x1[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]A2 炸素春卷（2条） x1[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:05[[/SMALL]]
--------------------------------


```

## 3. C1 + A1 汤 + A2 整份

C1 炸鸡排饭、A1 虾云吞汤3颗、A2 炸素春卷2条；所有金额采用单品价。

独立预期与 API 实际读回：

```json
{
  "expected": {
    "lines": [
      {
        "code": "C1",
        "quantity": 1,
        "name_zh": "炸鸡排饭",
        "name_en": "Crispy chicken cutlet",
        "base_price": "17.99",
        "category": "RICE",
        "options": [],
        "line_amount": "17.99"
      },
      {
        "code": "A1",
        "quantity": 1,
        "name_zh": "虾云吞汤（3颗）",
        "name_en": "Shrimp wonton soup (3 wontons)",
        "base_price": "4.99",
        "category": "APPETIZER",
        "options": [],
        "line_amount": "4.99"
      },
      {
        "code": "A2",
        "quantity": 1,
        "name_zh": "炸素春卷（2条）",
        "name_en": "Vegetable spring rolls (2 rolls)",
        "base_price": "4.99",
        "category": "APPETIZER",
        "options": [],
        "line_amount": "4.99"
      }
    ],
    "subtotal": "27.97",
    "tax_rate": "14.975%",
    "tax": "4.19",
    "total": "32.16",
    "calculation": "sum((approved base price + selected approved option prices) * quantity); tax=round_half_up(subtotal*0.14975,2)"
  },
  "actual": {
    "id": 4,
    "order_no": "ORD-20261112030503-2EEC52",
    "status": "completed",
    "subtotal_amount": "27.97",
    "total_amount": "32.16",
    "items": [
      {
        "id": 5,
        "menu_item_id": 18,
        "category_code_snapshot": "RICE",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_C1",
        "item_name_snapshot_zh": "炸鸡排饭",
        "item_name_snapshot_en": "Crispy chicken cutlet",
        "item_name_snapshot_fr": "Poulet pané croustillant",
        "quantity": 1,
        "unit_price": "17.99",
        "line_amount": "17.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": []
      },
      {
        "id": 6,
        "menu_item_id": 1,
        "category_code_snapshot": "APPETIZER",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_A1",
        "item_name_snapshot_zh": "虾云吞汤（3颗）",
        "item_name_snapshot_en": "Shrimp wonton soup (3 wontons)",
        "item_name_snapshot_fr": "Soupe wonton aux crevettes (3)",
        "quantity": 1,
        "unit_price": "4.99",
        "line_amount": "4.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": []
      },
      {
        "id": 7,
        "menu_item_id": 2,
        "category_code_snapshot": "APPETIZER",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_A2",
        "item_name_snapshot_zh": "炸素春卷（2条）",
        "item_name_snapshot_en": "Vegetable spring rolls (2 rolls)",
        "item_name_snapshot_fr": "Rouleaux impériaux aux légumes (2)",
        "quantity": 1,
        "unit_price": "4.99",
        "line_amount": "4.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": []
      }
    ]
  }
}
```

未配置固定Combo/单条portion，非固定套餐证明；仅验证批准菜单的单品等价组合，A2始终为2条整份。

### GRAB — Job 8

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`4e27814330d616e32c18bfb206238a2e414a241559e50b6dd72d40112fa7b180`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]C1 炸鸡排饭 x1[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]A1 虾云吞汤（3颗） x1[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]A2 炸素春卷（2条） x1[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:05[[/SMALL]]
--------------------------------


```

### FRONTDESK_RECEIPT — Job 7

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`54bb3d0c384ccac3fd6c366771aa42caceda5cf42a32eab6dc18a7a231bba02d`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x C1 Crispy chicken cutlet[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$17.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x A1 Shrimp wonton soup (3 wontons)[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$4.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x A2 Vegetable spring rolls (2 rolls)[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$4.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Subtotal: $27.97[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Tax (14.975%): $4.19[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Total: $32.16[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Submitted: 2026-11-12 03:05[[/DOUBLE_HEIGHT]]


```

## 4. C6 双拼两蛋白

C6 四选二选择牛肉与鸡肉，两项均为$0.00选择；保留一份饭，不生成两份全价饭。

独立预期与 API 实际读回：

```json
{
  "expected": {
    "lines": [
      {
        "code": "C6",
        "quantity": 1,
        "name_zh": "烤肉双拼饭（四选二）",
        "name_en": "Mixed grill · Choose two",
        "base_price": "19.99",
        "category": "RICE",
        "options": [
          {
            "code": "choice_beef",
            "quantity": 1,
            "name_zh": "牛肉",
            "name_en": "Beef",
            "price": "0.00"
          },
          {
            "code": "choice_chicken",
            "quantity": 1,
            "name_zh": "鸡肉",
            "name_en": "chicken",
            "price": "0.00"
          }
        ],
        "line_amount": "19.99"
      }
    ],
    "subtotal": "19.99",
    "tax_rate": "14.975%",
    "tax": "2.99",
    "total": "22.98",
    "calculation": "sum((approved base price + selected approved option prices) * quantity); tax=round_half_up(subtotal*0.14975,2)"
  },
  "actual": {
    "id": 5,
    "order_no": "ORD-20261112030504-4F1943",
    "status": "completed",
    "subtotal_amount": "19.99",
    "total_amount": "22.98",
    "items": [
      {
        "id": 8,
        "menu_item_id": 23,
        "category_code_snapshot": "RICE",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_C6",
        "item_name_snapshot_zh": "烤肉双拼饭（四选二）",
        "item_name_snapshot_en": "Mixed grill · Choose two",
        "item_name_snapshot_fr": "Duo de grillades au choix",
        "quantity": 1,
        "unit_price": "19.99",
        "line_amount": "19.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": [
          {
            "id": 5,
            "option_id": 136,
            "option_type_snapshot": "addon",
            "option_code_snapshot": "choice_beef",
            "option_group_snapshot": "CUSTOM",
            "parent_option_id_snapshot": null,
            "option_name_snapshot_zh": "牛肉",
            "option_name_snapshot_en": "Beef",
            "option_name_snapshot_fr": "Bœuf",
            "price_delta": "0.00",
            "quantity": 1
          },
          {
            "id": 6,
            "option_id": 137,
            "option_type_snapshot": "addon",
            "option_code_snapshot": "choice_chicken",
            "option_group_snapshot": "CUSTOM",
            "parent_option_id_snapshot": null,
            "option_name_snapshot_zh": "鸡肉",
            "option_name_snapshot_en": "chicken",
            "option_name_snapshot_fr": "poulet",
            "price_delta": "0.00",
            "quantity": 1
          }
        ]
      }
    ]
  }
}
```

### FRONTDESK_RECEIPT — Job 10

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`759acaef5b2360b6c991fb0c297db6baa90faa79062d0e8f777dd0a05fb29375`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x C6 Mixed grill · Choose two[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Beef x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]chicken x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$19.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Subtotal: $19.99[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Tax (14.975%): $2.99[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Total: $22.98[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Submitted: 2026-11-12 03:05[[/DOUBLE_HEIGHT]]


```

### GRAB — Job 9

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`376ea106e0446d8574e5b11c7ab76ec58c9a68f82346bbf73d257a2c1bccb618`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]C6 烤肉双拼饭（四选二） x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]牛肉 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]鸡肉 x1[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:05[[/SMALL]]
--------------------------------


```

## 5a. 前菜 + 主菜 + 饮品 + Dessert（关闭）

Dessert 厨房开关关闭；GRAB 只打印前菜和主菜；饮品与甜品仍完整记账并留在收据。

独立预期与 API 实际读回：

```json
{
  "expected": {
    "lines": [
      {
        "code": "A2",
        "quantity": 1,
        "name_zh": "炸素春卷（2条）",
        "name_en": "Vegetable spring rolls (2 rolls)",
        "base_price": "4.99",
        "category": "APPETIZER",
        "options": [],
        "line_amount": "4.99"
      },
      {
        "code": "B1",
        "quantity": 1,
        "name_zh": "经典牛肉面",
        "name_en": "Classic beef",
        "base_price": "16.99",
        "category": "NOODLE",
        "options": [
          {
            "code": "choice_thin",
            "quantity": 1,
            "name_zh": "细面",
            "name_en": "thin noodles",
            "price": "0.00"
          }
        ],
        "line_amount": "16.99"
      },
      {
        "code": "D1",
        "quantity": 1,
        "name_zh": "瓶装水",
        "name_en": "Bottled water",
        "base_price": "1.99",
        "category": "DRINK",
        "options": [],
        "line_amount": "1.99"
      },
      {
        "code": "E1",
        "quantity": 1,
        "name_zh": "冰淇淋（2球）",
        "name_en": "Ice cream (2 scoops)",
        "base_price": "3.99",
        "category": "DESSERT",
        "options": [],
        "line_amount": "3.99"
      }
    ],
    "subtotal": "27.96",
    "tax_rate": "14.975%",
    "tax": "4.19",
    "total": "32.15",
    "calculation": "sum((approved base price + selected approved option prices) * quantity); tax=round_half_up(subtotal*0.14975,2)"
  },
  "actual": {
    "id": 6,
    "order_no": "ORD-20261112030505-AC25D0",
    "status": "completed",
    "subtotal_amount": "27.96",
    "total_amount": "32.15",
    "items": [
      {
        "id": 9,
        "menu_item_id": 2,
        "category_code_snapshot": "APPETIZER",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_A2",
        "item_name_snapshot_zh": "炸素春卷（2条）",
        "item_name_snapshot_en": "Vegetable spring rolls (2 rolls)",
        "item_name_snapshot_fr": "Rouleaux impériaux aux légumes (2)",
        "quantity": 1,
        "unit_price": "4.99",
        "line_amount": "4.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": []
      },
      {
        "id": 10,
        "menu_item_id": 10,
        "category_code_snapshot": "NOODLE",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_B1",
        "item_name_snapshot_zh": "经典牛肉面",
        "item_name_snapshot_en": "Classic beef",
        "item_name_snapshot_fr": "Bœuf classique",
        "quantity": 1,
        "unit_price": "16.99",
        "line_amount": "16.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": [
          {
            "id": 7,
            "option_id": 10,
            "option_type_snapshot": "addon",
            "option_code_snapshot": "choice_thin",
            "option_group_snapshot": "CUSTOM",
            "parent_option_id_snapshot": null,
            "option_name_snapshot_zh": "细面",
            "option_name_snapshot_en": "thin noodles",
            "option_name_snapshot_fr": "nouilles fines",
            "price_delta": "0.00",
            "quantity": 1
          }
        ]
      },
      {
        "id": 11,
        "menu_item_id": 24,
        "category_code_snapshot": "DRINK",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_D1",
        "item_name_snapshot_zh": "瓶装水",
        "item_name_snapshot_en": "Bottled water",
        "item_name_snapshot_fr": "Eau en bouteille",
        "quantity": 1,
        "unit_price": "1.99",
        "line_amount": "1.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": false,
        "is_beverage_item": true,
        "is_kitchen_related_item": false,
        "station_code": null,
        "task_status": null,
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": "cancelled",
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": "2026-11-12T03:05:06.720905",
        "options": []
      },
      {
        "id": 12,
        "menu_item_id": 30,
        "category_code_snapshot": "DESSERT",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_E1",
        "item_name_snapshot_zh": "冰淇淋（2球）",
        "item_name_snapshot_en": "Ice cream (2 scoops)",
        "item_name_snapshot_fr": "Crème glacée (2 boules)",
        "quantity": 1,
        "unit_price": "3.99",
        "line_amount": "3.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": []
      }
    ]
  }
}
```

### GRAB — Job 12

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`1c63efb6fb03e853618584d216b1f39f8f21eff7a752e8560cd29722f7266c0b`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]A2 炸素春卷（2条） x1[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]B1 经典牛肉面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]细面 x1[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:05[[/SMALL]]
--------------------------------


```

### FRONTDESK_RECEIPT — Job 11

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`ac09d6960f9e33d4ae52fa51c658e91b976ce420f4ebbfa224fa4e369fdd8d9d`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x A2 Vegetable spring rolls (2 rolls)[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$4.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x B1 Classic beef[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]thin noodles x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$16.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x D1 Bottled water[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$1.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x E1 Ice cream (2 scoops)[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$3.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Subtotal: $27.96[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Tax (14.975%): $4.19[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Total: $32.15[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Submitted: 2026-11-12 03:05[[/DOUBLE_HEIGHT]]


```

## 5b. 前菜 + 主菜 + 饮品 + Dessert（开启）

与5a同样的单品及金额；Dessert 厨房开关开启，甜品仅增加一次，饮品仍不进厨房。

独立预期与 API 实际读回：

```json
{
  "expected": {
    "lines": [
      {
        "code": "A2",
        "quantity": 1,
        "name_zh": "炸素春卷（2条）",
        "name_en": "Vegetable spring rolls (2 rolls)",
        "base_price": "4.99",
        "category": "APPETIZER",
        "options": [],
        "line_amount": "4.99"
      },
      {
        "code": "B1",
        "quantity": 1,
        "name_zh": "经典牛肉面",
        "name_en": "Classic beef",
        "base_price": "16.99",
        "category": "NOODLE",
        "options": [
          {
            "code": "choice_thin",
            "quantity": 1,
            "name_zh": "细面",
            "name_en": "thin noodles",
            "price": "0.00"
          }
        ],
        "line_amount": "16.99"
      },
      {
        "code": "D1",
        "quantity": 1,
        "name_zh": "瓶装水",
        "name_en": "Bottled water",
        "base_price": "1.99",
        "category": "DRINK",
        "options": [],
        "line_amount": "1.99"
      },
      {
        "code": "E1",
        "quantity": 1,
        "name_zh": "冰淇淋（2球）",
        "name_en": "Ice cream (2 scoops)",
        "base_price": "3.99",
        "category": "DESSERT",
        "options": [],
        "line_amount": "3.99"
      }
    ],
    "subtotal": "27.96",
    "tax_rate": "14.975%",
    "tax": "4.19",
    "total": "32.15",
    "calculation": "sum((approved base price + selected approved option prices) * quantity); tax=round_half_up(subtotal*0.14975,2)"
  },
  "actual": {
    "id": 7,
    "order_no": "ORD-20261112030506-BEAE68",
    "status": "completed",
    "subtotal_amount": "27.96",
    "total_amount": "32.15",
    "items": [
      {
        "id": 13,
        "menu_item_id": 2,
        "category_code_snapshot": "APPETIZER",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_A2",
        "item_name_snapshot_zh": "炸素春卷（2条）",
        "item_name_snapshot_en": "Vegetable spring rolls (2 rolls)",
        "item_name_snapshot_fr": "Rouleaux impériaux aux légumes (2)",
        "quantity": 1,
        "unit_price": "4.99",
        "line_amount": "4.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": []
      },
      {
        "id": 14,
        "menu_item_id": 10,
        "category_code_snapshot": "NOODLE",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_B1",
        "item_name_snapshot_zh": "经典牛肉面",
        "item_name_snapshot_en": "Classic beef",
        "item_name_snapshot_fr": "Bœuf classique",
        "quantity": 1,
        "unit_price": "16.99",
        "line_amount": "16.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": [
          {
            "id": 8,
            "option_id": 10,
            "option_type_snapshot": "addon",
            "option_code_snapshot": "choice_thin",
            "option_group_snapshot": "CUSTOM",
            "parent_option_id_snapshot": null,
            "option_name_snapshot_zh": "细面",
            "option_name_snapshot_en": "thin noodles",
            "option_name_snapshot_fr": "nouilles fines",
            "price_delta": "0.00",
            "quantity": 1
          }
        ]
      },
      {
        "id": 15,
        "menu_item_id": 24,
        "category_code_snapshot": "DRINK",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_D1",
        "item_name_snapshot_zh": "瓶装水",
        "item_name_snapshot_en": "Bottled water",
        "item_name_snapshot_fr": "Eau en bouteille",
        "quantity": 1,
        "unit_price": "1.99",
        "line_amount": "1.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": false,
        "is_beverage_item": true,
        "is_kitchen_related_item": false,
        "station_code": null,
        "task_status": null,
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": "cancelled",
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": "2026-11-12T03:05:07.685498",
        "options": []
      },
      {
        "id": 16,
        "menu_item_id": 30,
        "category_code_snapshot": "DESSERT",
        "station_id_snapshot": 1,
        "item_sku_snapshot": "AN_E1",
        "item_name_snapshot_zh": "冰淇淋（2球）",
        "item_name_snapshot_en": "Ice cream (2 scoops)",
        "item_name_snapshot_fr": "Crème glacée (2 boules)",
        "quantity": 1,
        "unit_price": "3.99",
        "line_amount": "3.99",
        "combo_group_no": null,
        "combo_role": "standalone",
        "notes": null,
        "is_modified_after_submit": false,
        "modified_after_submit_at": null,
        "added_revision": 1,
        "order_update_batch_id": null,
        "requires_kitchen_task": true,
        "is_beverage_item": false,
        "is_kitchen_related_item": true,
        "station_code": "BACK_KITCHEN",
        "task_status": "cancelled",
        "started_at": null,
        "ready_for_pickup_at": null,
        "served_at": null,
        "beverage_status": null,
        "beverage_special_instructions_snapshot": null,
        "beverage_started_at": null,
        "beverage_ready_at": null,
        "beverage_served_at": null,
        "beverage_cancelled_at": null,
        "options": []
      }
    ]
  }
}
```

### GRAB — Job 13

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`218db51ffb8406b208413fa7cd682ccb1138c971d35db1c0d73b72d4966ed7d9`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]A2 炸素春卷（2条） x1[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]B1 经典牛肉面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]细面 x1[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]E1 冰淇淋（2球） x1[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:05[[/SMALL]]
--------------------------------


```

### FRONTDESK_RECEIPT — Job 14

`status=PRINTED`，`execution_mode=null`，`preferred_device_id=3`。保存文本 SHA-256：`ac09d6960f9e33d4ae52fa51c658e91b976ce420f4ebbfa224fa4e369fdd8d9d`。

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST-1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x A2 Vegetable spring rolls (2 rolls)[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$4.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x B1 Classic beef[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]thin noodles x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$16.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x D1 Bottled water[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$1.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x E1 Ice cream (2 scoops)[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$3.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Subtotal: $27.96[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Tax (14.975%): $4.19[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Total: $32.15[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]Submitted: 2026-11-12 03:05[[/DOUBLE_HEIGHT]]


```

Dessert 对照：关闭时 GRAB 仅包含前菜与主菜；开启时 Dessert 增加一次。饮品两单均保留在 FRONTDESK、两单均不进 GRAB；金额一致；每单恰好一张 GRAB，无重复派单。

数据库读回已逐张核对保存文本与 API 快照完全相同；所有订单最终状态 `completed`。合成设备验收后已撤销。固定 Combo 定价、单条春卷 portion、真实 Pad/打印机输出未在本报告中宣称通过。
