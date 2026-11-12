# Allô 打印路由与协议验收报告

结果：**PASS — 本机合成 Renderer / HTTP 协议范围**。真实 Android Worker、
Pad 到打印机连接和实体出纸均为 **NOT TESTED**。

**Order 8 金额/收据不变量：UNRESOLVED。** 初单与加单收据相加比当前整单总计
多 CAD 0.01。前述原六单和协议 PASS 不包含对此差异的接受；是否阻断及是否需修复，
由主线程与独立 reviewer 评估。现有实现本身不构成豁免依据。

本报告记录实际已执行的测试，以及主线程浏览器改价之后的只读复核。
完整预期金额、实际订单明细与十二张保存的小票原文见
[五组实际 Renderer 验收](PRINTING_ACCEPTANCE_5_ORDERS.md)。本文件不重新生成、
替换或手写任何小票。

## 环境和证据

| 项目 | 实际值 |
| --- | --- |
| 应用构建标识 | `c2425481ec25257660fa1a73d9ed0ec284eb5fe2` |
| JAR SHA-256 | `ec2627dbeeeb8cd11e8cd41ad60cf61d0ec97214dcc355d1c738c828d08a05a5` |
| Backend | `127.0.0.1:18512`，PID `50669`，保留运行供浏览器验收 |
| PostgreSQL | `127.0.0.1:26512/restaurant_allo_nouilles_test`，本轮新建独立空库 |
| Store | `1 / ALLO_NOUILLES_TEST / SYNTHETIC Allo QA` |
| 负向 Store | `2 / ALLO_SCOPE_NEGATIVE`，另一 Organization |
| Profiles | 精确 `allo-test,allo-qa` |
| Bootstrap | 首次 `app.environment=test`，仅空合成库启用 Allo bootstrap；随后关闭 bootstrap |
| 验收 runtime | `app.environment=production`，显式 Allo production printing policy；普通 seed、其他 bootstrap、Uber/worker 均关闭 |
| 身份认证 | 真实新建 QA Bearer 登录；`APP_REALTIME_AUTHENTICATED_STOMP_ENABLED=true`；未使用认证绕过 |
| Flyway | V1–V42，42 成功、0 失败 |
| 批准菜单 SHA-256 | `9a855f6565f426c38957d3004efcf8e944a5e4c3ac472ae27fa061bc9713ab79` |
| HTTP 协议原始证据 | 私有 `qa-api-safety-1794470671354687000.json`，开始于 `2026-11-12T08:04:31Z` |
| 六单 Renderer 原始证据 | 私有 `qa-five-orders-1794470701977644000.json`，开始于 `2026-11-12T08:05:01Z` |
| 历史不变复核 | `2026-11-12T08:22:54Z`，PostgreSQL `transaction_read_only=on`，仅 SELECT |

私有证据位于 `/Users/xuzilin/.codex/private/allo-release-20261112/`。
只导入批准的公开菜单配置；未读取或写入远程/生产数据库，未使用生产 dump、
真实订单、真实凭据或实体设备。与其他任务的 `25442` / `28180` 进程无关。

本次复核未提交订单、修改价格/路由、领取队列、停止进程或处理当前浏览器
`TEST-1` 订单。以下历史结论针对原 Order 2–7，不宣称浏览器工作已结束。

## 五组完整 Renderer 路由结果

六单均真实执行 Create → signed Submit → Outbox → 持久化 PrintJob → Finish。
每单恰好一张 `GRAB` 和一张 `FRONTDESK_RECEIPT`，十二张均为 MOCK `PRINTED`。
厨房采用同一 `BACK_KITCHEN` 工位；没有按分类重复派发多张 GRAB。

| 场景 | Order | GRAB / FRONTDESK Job | 实际路由和内容 | 小计 / 税 / 总计 CAD |
| --- | --- | --- | --- | --- |
| 1. B1 细面，无 Combo | 2 | 4 / 3 | 两张均保留 B1 与细面各一次；无虚构 Combo | 16.99 / 2.54 / 19.53 |
| 2. B1 + paid Add-on + A2 | 3 | 5 / 6 | B1、牛肉加料一次、A2 两条整份各一次 | 29.97 / 4.49 / 34.46 |
| 3. C1 + A1 汤 + A2 | 4 | 8 / 7 | 一份饭、一份三颗云吞汤、一份两条春卷均保留 | 27.97 / 4.19 / 32.16 |
| 4. C6 双蛋白 | 5 | 9 / 10 | 一份 C6，牛肉和鸡肉各选一次、选择价均 0；未生成两份全价饭 | 19.99 / 2.99 / 22.98 |
| 5a. A2 + B1 + D1 + E1，Dessert 厨房关闭 | 6 | 12 / 11 | GRAB 仅 A2/B1；收据保留 A2/B1/D1/E1 | 27.96 / 4.19 / 32.15 |
| 5b. 同样四项，Dessert 厨房开启 | 7 | 13 / 14 | GRAB 增加 E1 一次；D1 仍不进厨房；收据四项完整 | 27.96 / 4.19 / 32.15 |

预期金额由批准菜单单价与选项单价独立以 Decimal 计算，税为
`round_half_up(subtotal × 0.14975, 2)`，按整单取分。
六单实际金额均与独立预期相同，最终 `completed`、`current_revision=1`。
Dessert 开关只改变 GRAB 内容，5a/5b 金额相同；饮品两单均留在收据。

场景 2/3 是单品等价组合，**未配置固定 Combo / 单条 portion，非固定套餐证明**。
当前合成 Store 的 `store_combo_groups=0`、`store_combo_components=0`；
批准菜单没有固定 Offer 价格。A2 始终为两条整份 `$4.99`，没有推算或创建单条价格。

MOCK 兼容 Job 的 `execution_mode` 字段为 `null`；逐张已核对冻结
`receipt_context.execution_mode=MOCK`、
`receipt_artifact.transport=MOCK_ONLY_NOT_PHYSICAL_PROOF`，以及保存文本的 SHA-256。
它们不是可用于物理打印的 PAD_DIRECT 出纸证明。

## HTTP 设备认证、lease 和状态协议

这些步骤实际调用新 QA 后端。两个设备均为 Owner 认证后注册的合成设备
`1`、`2`；没有启动 Android Worker。临时 printer endpoint 仅为
`127.0.0.1:29512` sentinel。

| 已执行检查 | 实际结果 |
| --- | --- |
| 新合成 FRONTDESK 登录与 POS 目录 | 登录 200，`/menu/catalog?store_id=1` 200 |
| FRONTDESK 收据配置角色边界 | `/stores/1/receipt-presentation` 403 |
| FRONTDESK 新 Bulk 三入口 | capabilities GET、preview POST、apply POST 全为 403 |
| 既有 Add-on 权限 | FRONTDESK Add-on GET 200；未修改既有 capability 模型 |
| Owner 跨 Store 2 目录 | 403 |
| 设备坏 token / 跨 Store 2 队列 | 401 / 403 |
| PAD_DIRECT 未签名 Submit | 403，订单仍为 draft |
| 有效同店 HMAC Submit | 成功；合成协议 Order 1 生成 Job 1/2，两张均 PENDING / PAD_DIRECT，origin 设备为 1 |
| 签名契约 | 按现有 `PrintRequestAttestation` 的 `PRINT_ORIGIN_V1`、时间戳、POST 路径、body/Bearer SHA-256 与 HMAC-SHA256 契约发送 |
| 正确 origin pending | 可读取两张实际排队 Job；backend 未自行打印 |
| claim 同 token replay | 返回原 claim，未延长或重建 lease |
| 未到期另一设备 claim / payload、错误 attempt start | 均为 409 |
| 实际 CLAIMED lease 到期 | 请求 15 秒 lease，真实等待 16 秒；旧设备 start 409，设备 2 可重新 claim |
| 旧 claim 在转交后继续 start | 409 |
| start-print | 正确设备/attempt 转为 PRINTING |
| PRINTING 再 claim | 原设备、另一设备、另一 attempt 均 409，不重复领取 |
| 正确 payload | 读取实际保存文本与 ESC/POS bytes；与冻结 artifact hash 一致，只在内存校验，不发送至 socket |
| 错误设备 / attempt complete | 均为 409 |
| 当前 Store 切 DISABLED | pending、claim、start-print、payload 全为 409 |
| DISABLED 下已在途的 complete | 允许记录原设备的模拟回报；相同 complete replay 保留原 printed_at |
| DISABLED 下已在途的 fail | 允许记录明确模拟失败；恢复 PAD_DIRECT 后 FAILED Job 再 claim 409 |
| Sentinel 最终计数 | 退出 listener、join 后再次检查：**0 连接** |
| 收尾 | 恢复 Store MOCK、原 assignments、两台 MOCK printer、空 endpoint，撤销设备 1/2 |

协议 Job 1 的 `PRINTED` 只代表客户端发送了明确的合成 HTTP 完成回报，
`raw_result=SYNTHETIC_HTTP_PROTOCOL_SIMULATION_ONLY_NO_HARDWARE_NO_PRINT_ATTEMPT`。
Job 2 的预期结果为 `FAILED / SYNTHETIC_HTTP_NO_PRINT_ATTEMPT`。
它们不表示真实打印成功或实体打印故障，不应据此触发补打。

sentinel 只 accept 后立即 close，不 recv、send 或转发数据；测试客户端没有
执行任何 socket 打印。后端对该打印端点的连接数为零。该协议证明不覆盖
真实 Pad 配对、Android 生命周期、打印机字库/编码、网络、裁切、纸张或出纸效果。

## 浏览器改价后的历史订单不变

主线程执行 UI 分类 `+1/-1` 后，本次只读核对到两条持久化调整记录：

| Adjustment | 方向 / 金额 | Categories | 生效商品 / 排除续份 | Revision |
| --- | --- | --- | --- | --- |
| 1 | INCREASE / CAD 1.00 | 2 NOODLE、3 RICE | 14 / 2 | 81 → 82 |
| 2 | DECREASE / CAD 1.00 | 2 NOODLE、3 RICE | 14 / 2 | 82 → 83 |

当前 14 个 B/C 主菜 base_price 逐项等于批准菜单；两个免费续份均被排除。
在 `2026-11-12T08:22:54Z`，canonical `n_extra_beef` 的 QA 测试价为 `$8.49`，
其八个关联 noodle option 的当前 `price_delta` 均为 `$8.49`。
主线程计划随后恢复该 QA 测试价；本报告只记录观察值，未执行或宣称恢复。

原 Order 3 已冻结的 `n_extra_beef`（option ID 13）仍为 `$7.99 × 1`。
Order 2–7 的小计、税和总计仍为上表值；十二张 `rendered_text_snapshot`
逐字等于首次真实 Renderer 证据，冻结 artifact 的 text hash 也与当前文本相同。
这证明当前 canonical Add-on 价格和分类改价没有重写这批历史单据。

| Order | GRAB Job / 当前 SHA-256 | FRONTDESK Job / 当前 SHA-256 |
| --- | --- | --- |
| 2 | 4 / `63eecf0e0654c38fe619b86b401e82eda78c304c65930156932fe3f0490b34e2` | 3 / `a78f9ee78187ba6e1637a0728798cc40c8d7cb1b227c5d3d0160dcf21a274cec` |
| 3 | 5 / `bfcc07465e4ecdb7f7fc6ca4387713f551f4bc446e787de60067aa7619c3e004` | 6 / `eb48696d2b34bf32091ce240d85a871df8cbd569960bcc53d693bbf86783de59` |
| 4 | 8 / `4e27814330d616e32c18bfb206238a2e414a241559e50b6dd72d40112fa7b180` | 7 / `54bb3d0c384ccac3fd6c366771aa42caceda5cf42a32eab6dc18a7a231bba02d` |
| 5 | 9 / `376ea106e0446d8574e5b11c7ab76ec58c9a68f82346bbf73d257a2c1bccb618` | 10 / `759acaef5b2360b6c991fb0c297db6baa90faa79062d0e8f777dd0a05fb29375` |
| 6 | 12 / `1c63efb6fb03e853618584d216b1f39f8f21eff7a752e8560cd29722f7266c0b` | 11 / `ac09d6960f9e33d4ae52fa51c658e91b976ce420f4ebbfa224fa4e369fdd8d9d` |
| 7 | 13 / `218db51ffb8406b208413fa7cd682ccb1138c971d35db1c0d73b72d4966ed7d9` | 14 / `ac09d6960f9e33d4ae52fa51c658e91b976ce420f4ebbfa224fa4e369fdd8d9d` |

每一项当前 hash 均与首次证据相同。Job 11/14 的文本 hash 相同不等于重复派单：
它们属于不同订单，source key 分别为 `submit:6:FRONTDESK_RECEIPT` 和
`submit:7:FRONTDESK_RECEIPT`。

保存 Job 唯一性也已核对：Order 2–7 共 12 Job、12 个不同
`submit:{orderId}:{module}` source key，重复 source key 为 0；每单每个 module
恰好一条。对应十二条 Outbox 均为 `MOCK_RENDERED`、`attempt_count=0`；
十二个 Job 均为 `PRINTED`、`retry_count=0`。未通过删除、补打或重建 Job 得到该结果。

只读观察时 Store 1 为 MOCK，两台 printer 均为 MOCK 且 endpoint 数为 0，
三台原验收合成设备全部已撤销。后续浏览器订单和测试配置由主线程继续管理。

## 追加：单项 QA 加料的 API 清理

`2026-11-12T08:39:13Z` 完成 **AUTHENTICATED API QA CLEANUP — PASS**。
主线程报告浏览器被 `window.confirm` 阻塞，无法通过 UI 恢复加料；经明确授权，
只通过既有本机认证 API 恢复这一项。**此结果不记作 UI 恢复 PASS**，
未操作浏览器确认框、当前订单或 pending UI。

先核对精确本机数据库、`Store 1 / ALLO_NOUILLES_TEST / SYNTHETIC Allo QA / MOCK`，
并在数据库和实际 API GET 双重确认目标仍是以下合成测试值，随后才执行一次
`PUT /api/v1/admin/menu/addons/1`。认证使用新 QA Owner 的正常 Bearer 登录，
未绕过角色、Store 或模块校验。

| canonical Add-on | 清理前 | 清理后 |
| --- | --- | --- |
| ID / code | `1 / n_extra_beef` | `1 / n_extra_beef` |
| name_zh | 合成牛肉 QA | 牛肉 |
| name_en | Synthetic beef QA | Beef |
| price / active | CAD 8.49 / true | CAD 7.99 / true |

业务写请求共 **1 次**，恢复 canonical 行 **1 条**；既有 API 同步其 **8 个**关联
menu option 的名称和价格到 `$7.99`，关联 ID 和 eligibility 保持原值。
没有修改其他 Add-on、任何菜单 base_price、订单、订单选项快照、PrintJob 或 Outbox。

清理前后用 `default_transaction_read_only=on` 的 SELECT 逐字段比较以下数据，
全部相同；私有证据保存了每组前后 SHA-256 与计数：

| 未变化对象 | 记录数 |
| --- | --- |
| orders | 8 |
| order_items | 19 |
| order_item_options | 10 |
| print_jobs | 18 |
| order_dispatch_outbox | 18 |
| menu_items 的 ID / Store / base_price | 34 |
| 其他 canonical Add-on | 12 |
| 非目标 Add-on 的 menu option | 148 |

最新浏览器 **Order 8** 保持 `preparing`、`table_no=TEST 1`、`current_revision=2`，
小计 `$31.46`、总计 `$36.17`；它的冻结 option ID 13 仍为
`n_extra_beef / 合成牛肉 QA / Synthetic beef QA / $8.49 × 1`。
该订单与四张已保存小票均未改动，当前 canonical `$7.99` 没有倒改其 `$8.49` 历史快照。

原 **Order 2–7** 的金额与 `completed` 状态也再次通过；原十二张保存小票
逐字及 SHA-256 仍等于首次证据和本报告上表，未删除、补打或重建任何 Job。

本次清理的私有记录：`qa-addon-cleanup-1794472752532852000.json`。
其中只记录无秘密的 API 请求/响应、对象 ID、计数、快照和比较 hash；
不包含密码、JWT 或设备 token。数据库读取仅连 `127.0.0.1:26512`，
业务写请求仅发往 `127.0.0.1:18512`；未接触远程/生产环境。

## 追加：浏览器 Order 8 的后端快照二次证据

只读复核时间：`2026-11-12T08:42:25Z`。本节读取实际保存的数据库 snapshot，
属于 **backend snapshot secondary evidence**；它不证明浏览器 Print Center
列表、预览或硬件打印已被观察通过。未再发任何写 API，未操作浏览器或 Finish。

Order 8 保持 `preparing / TEST 1 / revision 2`，作为未 Finish 的真实 UI 操作证据保留。
对应四张 Job 的身份、内容和唯一 source key 如下：

| Job | Module / receipt_type | Batch | source key | 实际保存内容 |
| --- | --- | --- | --- | --- |
| 15 | GRAB / GRAB | initial | `submit:8:GRAB` | B1 一份、粗面一次、冻结合成牛肉加料一次 |
| 16 | FRONTDESK_RECEIPT / FRONTDESK_RECEIPT | initial | `submit:8:FRONTDESK_RECEIPT` | 初单 B1 与选项，25.48 + 3.82 = 29.30 |
| 17 | GRAB / GRAB_UPDATE | 1 | `update:8:1:GRAB` | 仅 E1 一份；排除 D1，未重复 B1 |
| 18 | FRONTDESK_RECEIPT / FRONTDESK_RECEIPT_UPDATE | 1 | `update:8:1:FRONTDESK_RECEIPT` | 仅新增 D1/E1，5.98 + 0.90 = 6.88 |

四张均为 MOCK `PRINTED / retry_count=0`；四条不同 Outbox/source key 均为
`MOCK_RENDERED / attempt_count=0`。文本 SHA-256 全部等于单项 API 清理前后记录，
也与各自保存的 `receipt_artifact.text_sha256` 一致：

| Job | 保存文本 SHA-256 |
| --- | --- |
| 15 | `30b37598d727504c2f9cbdd86efebfb0f8ca67a9a6dfd009aa6a1ca762f257c3` |
| 16 | `4a219e13bbfbdc17d0fbcbc742d4f386bbeda87b31989ac3cd4cdcbdfc7d32a2` |
| 17 | `6ebc4b54342950ee827b852b55a3bf9916e94437ed42a25b6bb37ddb3dcf1f9f` |
| 18 | `b34cc776010d540cbc665f57b56e2128e0f73429979e7314f30e4fc0284cf475` |

初单 context 的 item IDs 为 `[17]`；加单 context 仅 `[18,19]`，分别是 D1/E1。
Job 17 的 `kitchen_excluded_item_ids=[18]`，所以饮品不会进入加单厨房票，
而 E1 只打印一次。旧 B1 的 item ID 17 不属于这一加单 context。

收据金额遵循现有 Renderer 的两种范围：初单使用冻结初单金额，加单仅合计新增行并
独立计算本批税；当前整单则为小计 `$31.46`、税 `$4.71`、总计 `$36.17`。
初单 `$29.30` 加加单 `$6.88` 为 `$36.18`，与整单总计相差 `$0.01`，来自独立
batch 税取分与整单税取分的既有差异。`FrontdeskReceiptRenderer.render` 对
`updateTicket` 使用 `calculateItemsSubtotal(allItems)` 与 `TaxCalculator`；
即使加单 context 保存了当前整单 totals，Job 18 也不会把整单金额重复打印。
本次未修改该金额语义、历史订单或保存快照。

此处代码路径是已观察到的来源事实，**金额差异分类仍为 UNRESOLVED**，
尚未认定符合金额/收据不变量、可接受或非阻断。Order 8 与四张 Job 原样保留，
等待主线程与独立 reviewer 评估；未继续执行 QA 写操作或测试。

下列原文直接从 `rendered_text_snapshot` 读出，保留全部 markup：

### Order 8 / Job 15 / GRAB

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]B1 经典牛肉面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]粗面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合成牛肉 QA x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_BROWSER_QA_NO_PRINT[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:18[[/SMALL]]
--------------------------------


```

### Order 8 / Job 16 / FRONTDESK_RECEIPT

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 1[[/LARGE]]
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x B1 经典牛肉面[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]粗面 x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合成牛肉 QA x1[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]备注: SYNTHETIC_BROWSER_QA_NO_PRINT[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$25.48[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]小计: $25.48[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]税 (14.975%): $3.82[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合计: $29.30[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]提交时间: 2026-11-12 03:18[[/DOUBLE_HEIGHT]]


```

### Order 8 / Job 17 / GRAB_UPDATE

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 1[[/LARGE]] (加单)
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
--------------------------------
[[DOUBLE_HEIGHT]]E1 冰淇淋（2球） x1[[/DOUBLE_HEIGHT]]

--------------------------------
[[SMALL]]03:18[[/SMALL]]
--------------------------------


```

### Order 8 / Job 18 / FRONTDESK_RECEIPT_UPDATE

```text
[[TOP_FEED_16MM]]
[[LARGE]]TEST 1[[/LARGE]] (加单)
[[DOUBLE_HEIGHT]]SYNTHETIC Allo QA[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]1 x D1 瓶装水[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$1.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]1 x E1 冰淇淋（2球）[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]$3.99[[/DOUBLE_HEIGHT]]

[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]小计: $5.98[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]税 (14.975%): $0.90[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]合计: $6.88[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]--------------------------------[[/DOUBLE_HEIGHT]]
[[DOUBLE_HEIGHT]]提交时间: 2026-11-12 03:18[[/DOUBLE_HEIGHT]]


```

## Coordinator disposition of the discovered monetary discrepancy

The historical Order8/Job18 evidence above remains unchanged and is not
retrospectively corrected. The unresolved classification recorded at discovery
was subsequently investigated and repaired for **new** Allô update contexts in
e7dde0a6e27adf3be00baa9972fee06cdcc5482c. The authoritative order amount was
already correct. Actual new-artifact tests now pass both negative/positive
one-cent allocation and zero-price sequential update, with10 new saved
snapshots and unchanged hashes for every old Order/Job/outbox. See
[amount regression evidence](UPDATE_RECEIPT_AMOUNT_REGRESSION.md).
Independent amount review PASS closes that backend monetary defect for new
receipts; the Print Center/Finish browser gate remains incomplete and Production
is NOT DEPLOYED. Old-binary rollback must pause writes and drain new monetary
intents with the new binary, never reinterpret/replay them with the old renderer.
