# Final Acceptance Report — Uber Eats v1

日期：2026-09-16。范围：独立 worktree `Restaurant_System_uber_eats`、本机 PostgreSQL 16 / synthetic fixtures、真实应用代码；远端 Uber 请求在自动化测试中使用 HTTP mock。**不把 fixture 成功当作 Sandbox 成功。** 原工作区有未完成 merge，本轮未修改其文件/索引；没有 merge main，没有部署或修改 Staging/Production。

## A. Uber Dashboard Audit — PARTIAL

Application / Organization 名称均为 `Restaruant Pos`；Client ID `t86ofdunSsVjL-0AvK6MA6LCTyF2eYLf`；Client Secret：**PRESENT**。Dashboard 为 **TEST APP / Eats Marketplace**，Sandbox Access Granted 已勾选；Integration Verification Requested/Result 尚未完成，Create Prod App 不可用。当前尚不具备本系统端到端测试配置。

| Scope | Dashboard 证据状态 | Runtime 用途 |
|---|---|---|
| eats.order | AVAILABLE；实际 token grant UNKNOWN | Accept/Deny；Get 兼容 scope |
| eats.store | AVAILABLE；本版 NOT REQUIRED | 未调用 store 管理 API |
| eats.store.orders.read | AVAILABLE；实际 token grant UNKNOWN | Get Order v2 |
| eats.pos_provisioning | AVAILABLE 于 Authorization Code 列表；grant UNKNOWN | 本版不自动 provision，runtime NOT REQUIRED |
| eats.store.orders.cancel | AVAILABLE；NOT REQUIRED | 本版只收 cancel event，不调用 cancel |
| eats.store.orders.restaurantdelivery.status | AVAILABLE；NOT REQUIRED | 不更新配送状态 |
| eats.store.status.write / eats.report | AVAILABLE；NOT REQUIRED | 未使用 |

Dashboard 页显示 Scopes Grant Completed，但 checkbox 当时不可编辑，且未实际发 token；不据此宣称每项 APPROVED。文档 API/scope/host 明细见 [官方 endpoint 表](UBER_EATS_INTEGRATION.md#已核实的官方-api)。OAuth POST `/oauth/v2/token`；Get GET `/v2/eats/order/{order_id}`；Accept/Deny POST `/v1/eats/orders/{order_id}/accept_pos_order|deny_pos_order`。Sandbox token host `sandbox-login.uber.com`、API `test-api.uber.com`；Production token `auth.uber.com`、API `api.uber.com`。只调用上述四个端点。

Webhook URL：**未配置**（Dashboard 显示 No Webhooks）。环境 Testing；签名 HMAC SHA256/client secret；应用路径 `/api/v1/integrations/uber-eats/webhook`。notification/cancel/scheduled/edit 均有本地处理，Dashboard 均未配置；signature 本地 PASS；Uber Sandbox 真正回调：**BLOCKED**。

Uber Store ID/name/provisioning/test-order eligibility/local Store binding：**UNKNOWN / 未提供**。没有把 Organization ID 当作 Store ID，也没有把 fixture UUID 当真实门店。需要用户确认测试门店与本地目标 Store、order-manager provisioning。

## B. Architecture Result — PASS（本地）

Webhook controller → `UberEatsWebhookService` raw signature/event 去重 → `UberEatsWorker` durable queue → `UberEatsOrderClient` Get → `UberEatsOrderNormalizer` whitelist → `UberEatsMenuMappingService` stable Store/item/modifier mapping → `UberInboxPage` → staff Accept → `UberEatsOrderTransactions.prepare` → real-path Uber Accept client → accepted checkpoint → `submitLocal` → **现有 `OrderService.createOrReplaceDraftAndSubmit`** → kitchen/production/inventory/realtime/outbox → **现有 `PrintDispatcherService` / renderers / execution modes**。

NO duplicated order pipeline；NO Uber-specific GRAB renderer。新增 `ExternalOrderReceiptHeader` 仅统一显示外部来源，且外部来源单不再显示虚构的 Walk-in 桌号。普通 Pad source=null 的既有 renderer 路径保持原样。

## C. Database / Migration — PASS（隔离本机）

新增 Flyway：`V29__add_uber_eats_integration.sql`。新建空 PostgreSQL 实例执行 V1–V29；后续启动 Flyway validate + Hibernate validate 通过，无历史 migration 改写。

| 表 | 新增字段（完整） |
|---|---|
| uber_eats_store_mappings | id, environment, uber_store_id, store_id, organization_id, enabled, created_at |
| uber_eats_menu_mappings | id, store_mapping_id, kind, identifier_type, uber_identifier, uber_item_id, local_menu_item_id, local_option_code, local_option_group, parent_option_code, updated_at |
| uber_eats_events | id, environment, event_id, event_type, uber_store_id, uber_order_id, body_hash, status, error_code, attempt_count, next_attempt_at, created_at, updated_at |
| uber_eats_orders | id, environment, store_mapping_id, store_id, uber_store_id, uber_order_id, uber_event_id, status, display_id, fulfillment_type, mapping_status, mapping_error, raw_order_snapshot_json, local_request_json, last_error, deny_reason, accepted_by, local_order_id, attempt_count, cancelled, edit_required, scheduled, placed_at, scheduled_at, accepted_at, cancelled_at, next_attempt_at, created_at, updated_at |
| orders（只增加 nullable 字段） | external_source, external_order_id, external_display_id |

| Unique | 防止的重复 |
|---|---|
| uq_uber_store (environment,uber_store_id) | 同一 Uber Store 绑定多餐厅 |
| uq_uber_local_store (environment,store_id) | 一个本地 Store 在同环境拥有冲突绑定 |
| uq_uber_menu_key | 同 Store/种类/标识类型/根菜品/标识的冲突规则 |
| uq_uber_event (environment,event_id) | webhook replay 重复事件 |
| uq_uber_order (environment,uber_order_id) | notification replay 重复 integration order |
| uq_uber_local_order (local_order_id) | 多个 integration record 指向同张本地单 |
| uq_order_external_source (store_id,external_source,external_order_id) | 同来源同外部单再次创建本地单 |

重复 webhook、不同 event ID 的重复 notification、并发双 Accept 均通过真实 PostgreSQL 验证；只一次 remote accept、一个 local order、一次对应库存扣减及既有 outbox entries。

## D. OAuth — PARTIAL

本地 HTTP mock：client_credentials form、scope配置、正确 sandbox host、cache reuse、expires_in、提前刷新、异常脱敏均 PASS；token仅 backend 内存，前端/Android无 token。禁用 redirect；POST决定必须 204，302/307/意外200 均不能生成本地订单。**真实 token grant NOT TESTED**：backend 环境未配置实际凭据。非代码 blocker。

## E. Webhook — PASS（本地 HTTP + PostgreSQL）

| 场景 | 结果 / response |
|---|---|
| Valid signature | PASS / 200 empty |
| Invalid signature | PASS / 401 |
| Missing signature | PASS / 401 |
| Duplicate event | PASS / 200，一条 event，无重复 local |
| Malformed signed payload | PASS / 400 |
| Unknown event type | PASS / 200，IGNORED |
| Environment mismatch | PASS / 400 |

MockMvc valid ACK 断言 Uber client 尚无任何调用，证明 ACK 不等待 OAuth/Get/import/printing。取消/改单 guard 在 ACK 事务内持久化，普通导入异步。

## F. Store Mapping — PASS（fixture）

例：fixture Uber Store `efd3f78e-062c-47a2-b871-89adaf4e4a11` → local Store **164**；来自测试专用 DB binding，无真实餐厅关联。错 store response 无导入；未配置 Store 的 signed event 保留重试但不创建本地单；员工跨 Store 403、无登录401；integration row scope mismatch403。StoreAccessService 与 organization binding 校验继续有效。绑定只允许平台 ADMIN，Owner仅配置所属店菜单。

## G. Item Mapping — PASS（三个实际 fixture）

| Uber title / stable identity | Local SKU / menu ID | 中文 snapshot |
|---|---|---|
| Traditional Beef Noodle / external_data=traditional_beef_noodle，id=uber-noodle | traditional_beef_noodle / 180 | 牛肉面 |
| Cucumber / external_data=cucumber_salad，id=uber-side | cucumber_salad / 181 | 拍黄瓜 |
| Rice / external_data=fried_rice，id=uber-rice | fried_rice / 182 | 炒饭 |

三者在 Store164 同单映射并提交成功。另测 external_data 空时显式 Uber ID → local menu mapping，保存后自动 remap pending order。优先级：显式 external_data rule → 显式 ID rule → external_data 精确本地 SKU/option_code。无 display-name fallback。

## H. Modifier Mapping — PASS（fixture）

| Uber / stable identity | local code → group | 中文 snapshot / 既有厨房语义 |
|---|---|---|
| Large / size_large | size_large → SIZE | 大碗 / 大 |
| Thin / noodle_type_2 | noodle_type_2 → NOODLE_TYPE | 二细 / 二 |
| Mild / spicy_mild | spicy_mild → SPICY_LEVEL | 微辣；按原 formatter规则 |
| Fried Egg / fried_egg | fried_egg → ADD_ON | 加煎蛋 / +煎 |
| No Cilantro / remove_cilantro | remove_cilantro → REMOVE | 走香菜 / 走香 |
| Combo / combo | combo → COMBO | 套餐 / （s） |
| Combo egg / combo_fried_egg | combo_fried_egg → COMBO_EGG | 煎蛋 / 与加蛋按现有数量合并 |
| Combo cucumber / combo_cucumber_salad | combo_cucumber_salad → COMBO_SIDE | 拍黄瓜 / 黄瓜 |
| No garlic / explicit id=no-garlic | remove_garlic → COMBO_SIDE_REMOVE，parent=combo_cucumber_salad | 走蒜 / 走蒜 |

组合测试覆盖九类且提交后检查 options，GRAB 内容含套餐、两份煎蛋合并、走香/走蒜。`removed_items` 未配置时阻断，显式映射后保留去除语义。structured/标量 fulfillment_action 阻断测试通过。没有 silently dropped modifier。

## I. Mapping Failure Safety — PASS

Unknown item → MAPPING_REQUIRED，不能调 Uber Accept，无 local order/print。Known item + unknown modifier → error 包含具体 `Fried Egg [id]` + MODIFIER_MAPPING_MISSING，整体阻断，不生成 local/print；前端禁用 Accept，并显示错误详情。Deny仍可用。

## J. Frontdesk Inbox — PASS（本地）；真实来单 BLOCKED

Route `/stores/{storeId}/frontdesk/uber-eats`，组件 `UberInboxPage` / `UberInboxBadge` / `useUberInbox`。自动化7项覆盖 notes/modifiers、disabled mapping、Deny reason、loading、timeout error刷新、double tap、取消/修改不可Accept、refresh/remount与跨店状态隔离。

本机真实 jar + Vite + 正常登录，应用内浏览器观察 Store125：Uber Eats badge=1、FIX01、时间、数量、Large/Fried Egg/No Cilantro、MAPPED、Accept/Deny；1024×768 iPad 横屏卡片/大按钮可见；点击 Deny 显示原因选择与确认/返回。浏览器QA禁用远端 integration，未通过此页面向真实 Uber 发单。管理页源码/接口测试已验证，浏览器管理页读取遇连接超时，**管理页视觉 QA PARTIAL**。

## K. Accept E2E — PASS（mock Uber + 真实本地 pipeline）

| 阶段 | 证据 |
|---|---|
| 待接单、staff动作、最新 mapping validation | PASS，API/service集成测试 |
| Uber Accept成功 | PASS mock204；真实Sandbox BLOCKED |
| exactly one local/source/external link | PASS，并发去重；external_source=UBER_EATS |
| item/option中文 snapshots | PASS，同catalog字段与parent关系 |
| kitchen tasks | PASS，常规1；套餐fixture4 |
| production tasks | PASS，套餐fixture4 |
| inventory | PASS，BOM扣减一次，txn一条；失败事务回滚 |
| realtime | PARTIAL：实现 after-commit 既有领域事件+Uber inbox hint；UI刷新/订阅测试通过，未捕获真实Uber到浏览器WebSocket trace |
| outbox/jobs | PASS，每单按既有路由3条，重复dispatch不重复jobs |

Fixture证据：integration **410**，local order **118**，Uber order `4f711ac5-c3d4-4a70-b935-6d2db2ea9961`，Store164，MOCK；GRAB job274、FRONTDESK275、HOT_KITCHEN276，PRINTED（mock，无出纸）。这些是 fixture，不属于 S 的 Sandbox IDs。

## L. Chinese Kitchen Semantic Parity — PASS

独立构造 Pad `CreateOrderRequest`（未复用 Uber mapping 构造 Pad请求）与同配置 Uber fixture：牛肉面 + Large + Fried Egg + No Cilantro。真实数据库比对，Store159 Uber local116、Pad pickup117；两边 kitchen snapshot 均为 **`大 | +煎 走香`**。规范化来源/桌号/时间之后，现有 GRAB renderer 输出完全相同；测试明确包含 `+煎`、`走香`，没有另一套英文厨房翻译。

KITCHEN SEMANTIC PARITY: **PASS**。

## M. Printing — PARTIAL（软件PASS，实体未测）

订单提交 → dispatch outbox → PrintDispatcher → PrintJob → 既有 renderer/execution；integration 无直接printer/socket调用。MOCK PASS，GRAB/HOT_KITCHEN/FRONTDESK job生成/渲染/幂等；DISABLED PASS，订单保持ACCEPTED；PAD_DIRECT软件PASS。REAL **NOT TESTED**，没有真实打印机授权/端点。原 PrintJob/Outbox/Pad service 回归测试覆盖失败与重试、模糊物理结果不盲目重打；Uber已提交订单不因打印执行失败回滚。新 default scheduler 与 Uber scheduler 隔离，阻塞测试通过。

## N. PAD_DIRECT — PARTIAL

真实 PostgreSQL fixture：Uber accept → PENDING → device pending query → claim → start-print → payload（assigned printer `127.0.0.1:9`，ESC/POS非空，中文/source完整）→ **SIMULATED_ACK_NO_PHYSICAL_PRINT** → PRINTED；重复complete与dispatch不新增job。三种module均测。Android设备和实际纸张 **NOT TESTED**，模拟ACK不宣称physical print PASS。

## O. Cancellation — PASS（fixture）

Before accept：CANCELLED，local/kitchen/GRAB为0，Accept受guard拒绝。After accept：CANCELLED_REVIEW_REQUIRED，保留local、items、tasks与jobs；不hard delete、不自动取消已进厨房内容、不退款。

## P. Customer Edit — PASS（fixture）

识别并保存 `orders.customer_order_edit`；EDIT_REVIEW_REQUIRED；原 submitted items 数量不变、历史不变。前台明确人工核对。**AUTO APPLY CUSTOMER EDIT: DISABLED**。正式 notification 对 scheduled 的解除是持久单调信号，乱序/重复预约通知不会重新锁住正式订单。

## Q. Security — PARTIAL（代码检查PASS，凭据需轮换）

- PASS：本轮 Git无真实client secret/webhook secret/token；env example仅空值/默认值；应用异常不携带原Uber响应/cause；frontend/Android不含Uber token；普通integration API保留authorization；Webhook独立signature trust；Store绑定与菜单同店验证。
- PASS：raw webhook不写日志/数据库；只存metadata/hash。GET order按白名单最小化，eater/contact/payment/courier排除。自由备注可能含用户输入PII，不能宣称内容完全不含PII。
- **PARTIAL：Dashboard 初次浏览的 accessibility 输出意外包含被掩码字段的真实 secret。未复制到源码、Git或报告，已即时告知用户。必须由用户在 Dashboard rotate client secret，再将新值写入 backend私密环境；不要发聊天。没有证据显示该secret进入Git history。**
- 扫描：本轮56个 changed/new 文件做credential赋值与私钥/token模式检查；0条真实凭据发现，1条原文档random...占位符；允许明确fixture字符串与变量引用，报告不输出匹配值。原仓库全历史credential取证不在本次范围，不能声称全历史完全清洁。

## R. Automated Tests

| Command | tests run | passed | failed | skipped |
|---|---:|---:|---:|---:|
| `UBER_TEST_POSTGRES_URL=jdbc:postgresql://127.0.0.1:55439/uber_integration_test mvn -q -f backend/pom.xml verify` | 799 | 793 | 0 | 6 |
| 其中 `-Dtest='UberEats*Test' test` | 23 | 23 | 0 | 0 |
| `cd frontend && npm test` | 225 | 225 | 0 | 0 |
| `cd frontend && npm run build` | build | PASS | 0 | 0 |
| `npx eslint src/features/uber-eats src/services/uberEatsService.ts` | lint | PASS | 0 | 0 |
| PostgreSQL16 Flyway V1–V29 + Hibernate validate | migration | PASS | 0 | 0 |

6项既有PG测试未启用其专属 env：StoreAddonPostgresMigrationTest(2)、OwnerStoreMenuClonePostgresIntegrationTest(3)、PrintingDisplayRuleLifecyclePostgresIntegrationTest(1)。不是失败，也不是已通过。Uber PG测试实际执行，未因Docker不可用而跳过。

覆盖矩阵：valid/invalid/missing signature、duplicate webhook/order、OAuth cache/expiry/error脱敏、Store/item/modifier/全部combo、unknown item/modifier、Accept success/doubletap、API401/timeout/302/307、local事务失败恢复、cancel before/after、edit、scheduled乱序、GRAB/HOT/FRONTDESK、MOCK/PAD_DIRECT/DISABLED、跨店/no credential response、调度隔离均 PASS（fixture）。真实scope/token/Sandbox/REAL出纸 BLOCKED/NOT TESTED。

Agent6 初审P1一项（调度共享）、P2两项（scheduled乱序、3xx误ACK）全部修复；复核PASS，无未关闭P0/P1/P2代码blocker。独立审查读取代码与23项测试报告，未独立运行远端或硬件测试。

## S. Sandbox E2E — BLOCKED

Sandbox Order / Integration / Local / Uber Store / Local Store / GRAB / HOT IDs：**N/A，未产生真实Sandbox订单**。不填写fixture冒充。阻塞：`WEBHOOK_NOT_CONFIGURED`、`BACKEND_CREDENTIAL_NOT_CONFIGURED`、`TEST_STORE_PROVISIONING_UNKNOWN`、`ORDER_MANAGER_AND_TOKEN_SCOPE_NOT_VERIFIED`。Dashboard Sandbox Access Granted 与无production app均已观察；**不能确定是SCOPE_NOT_APPROVED或TEST_STORE_NOT_PROVISIONED，不作无证据断言。**

用户最小步骤：1) rotate已暴露的旧secret并安全写入backend环境；2) 提供/确认测试Uber Store UUID和对应local Store ID，确认本app为order manager；3) 提供部署目标的公开HTTPS callback并在Dashboard配置Testing webhook。其后执行真实token/Get/Accept/Deny与完整订单/打印job验收。上述均为账号/配置blocker，不是本地代码测试失败。

## T. Restart / Idempotency / Reliability — PARTIAL

| 场景 | 结果 |
|---|---|
| duplicate webhook / notification / Accept double tap | PASS，真实DB唯一性+并发 |
| received event 后重新实例化消费者 | PASS，durable PENDING恢复 |
| Uber Accept timeout / accepted before local transaction | PASS，用correlated GET恢复；不再POST Accept |
| local transaction failure | PASS，完整rollback→再次local恢复 |
| dispatch retry / job complete retry | PASS，同source key/job无重复 |
| frontend refresh/remount pending | PASS，hook测试及实际页面读取持久pending |
| 真正进程kill在每个中间指令位置的故障注入 | NOT TESTED |
| Android进程崩溃/真实纸张模糊结果 | NOT TESTED |

若远端ACCEPTED但缺少本次stable reference，进入人工review，不猜测已接单；若已有accepted checkpoint，自动重试local最多10次，之后明确LOCAL_REVIEW_REQUIRED。此窗口保留可恢复，不掩盖失败。

## U. Existing System Regression — PASS（自动化范围）

既有 `OrderServiceImplTest`、IdempotentOrderSubmission/Hash、GRAB/FRONTDESK/HOT renderers、PrintJob/PadPrintJob/Outbox、前端ordering/offline测试继续通过。覆盖dine-in/takeout、add-only更新、Finish/complete既有行为、离线幂等、MOCK/PAD_DIRECT保护。没有重写普通Pad shorthand。实体餐厅Pad/厨房现场回归 NOT TESTED。

## V. Changed Files

完整按模块清单由同目录 [UBER_EATS_CHANGED_FILES.md](UBER_EATS_CHANGED_FILES.md) 列出，每个文件标明作用。

## W. Documentation — PASS

已更新 `SYSTEM_DOCUMENTATION.md`、`doc/API.md`，新增 `docs/UBER_EATS_INTEGRATION.md` 与本报告/清单；涵盖 flow、OAuth/scopes、webhook/HMAC、Store/item/modifier、Accept/Deny、cancel/edit、local domain/printing/PAD_DIRECT、Sandbox/Production配置、env、troubleshooting、安全、限制、reconciliation。CURRENT_STATE仅更新本轮package/gate/stop，保留部署SHA事实；BACKLOG保留未完成菜单验收并登记Uber外部验收。

## X. Git / PR

Branch：`codex/uber-eats-order-integration`；base：`main`；fresh origin/main base SHA：`78d20faeb8ba144aae74a0c1c5a787d3641e932d`。原工作区保留其既有merge中状态；clean要求仅针对本次独立worktree。

实现Commit / PR：见本报告最终Git证据更新与交付消息。**不merge main，不部署Production；Staging未修改。**

## Y. Final Acceptance Matrix

| 项目 | 状态 | Evidence |
|---|---|---|
| Uber Dashboard Access | PASS | 已审计Test App/scopes/webhook页面 |
| OAuth | PARTIAL | HTTP cache/form测试PASS，真实grant未做 |
| Required Scopes | PARTIAL | 列表AVAILABLE，token grants未知 |
| Webhook | PARTIAL | 本地HTTP/持久化PASS，Dashboard未配置 |
| Signature Verification | PASS | valid/invalid/missing/raw bytes |
| Store Mapping | PASS | fixture隔离/StoreAccess测试 |
| Item Mapping | PASS | 3 SKU fixture+explicit ID |
| Modifier Mapping | PASS | 九类combo/modifier、removed items |
| Mapping Failure Safety | PASS | 未知项阻断、零local/print |
| Frontdesk Inbox | PASS | 7交互测试+实际1024×768浏览 |
| Accept | PARTIAL | 本地mock/并发PASS，真实Uber未接单 |
| Deny | PARTIAL | 本地mock/幂等PASS，真实Uber未拒单 |
| Local Order Creation | PASS | 统一OrderService+source/link |
| Kitchen Task Creation | PASS | fixture常规/套餐tasks |
| Chinese Kitchen Semantic Parity | PASS | Pad/Uber中文任务与GRAB相等 |
| GRAB | PASS | 现有renderer+mockjob |
| HOT_KITCHEN | PASS | 现有routing+mockjob |
| MOCK | PASS | 3模块PRINTED，无物理打印 |
| PAD_DIRECT | PARTIAL | DB job至payload/模拟completePASS，实体未测 |
| Cancellation | PASS | before/after保留历史 |
| Customer Edit Safety | PASS | 只review，不autoapply |
| Idempotency | PASS | DB unique/lock/concurrent/replay |
| Restart Recovery | PARTIAL | durable状态/实例重建测试，未全窗口kill |
| Security | PARTIAL | 代码/变更扫描PASS；Dashboard旧secret需轮换 |
| Regression Tests | PASS | 793 backend+225 frontend；6既有skip明确 |
| Sandbox E2E | BLOCKED | credential/store/callback缺失 |
| Documentation | PASS | contract/API/system/acceptance |

## Z. 最终结论

| 结论 | 状态 |
|---|---|
| IMPLEMENTATION | PASS（本地验收范围） |
| UBER CONNECTION | BLOCKED |
| SANDBOX E2E | BLOCKED |
| LOCAL ORDER PIPELINE | PASS |
| KITCHEN SEMANTIC PARITY | PASS |
| PRINTING | PARTIAL |
| PRODUCTION READY | NO |

Remaining blockers：

| 优先级 | 类别 | 未完成与最小动作 |
|---|---|---|
| P0 | CONFIGURATION / SECURITY | 用户轮换Dashboard曾进入工具输出的旧secret；新secret仅backend私密配置 |
| P1 | UBER ACCOUNT / SCOPE | 确认test Store、provisioning/order manager、真实token scopes；Production app尚未获验证 |
| P1 | CONFIGURATION | 独立部署目标、HTTPS callback、Store binding与完整menu mapping，运行真正Sandbox E2E |
| P1 | HARDWARE / FIELD QA | 真实Android和REAL printer出纸、业务现场取消/改单流程验收 |
| P2 | FIELD QA | 管理页浏览器视觉复验、实际WebSocket trace、进程kill窗口测试；检查v1 inbox100条容量限制 |

CODE BLOCKER：当前已审查范围内无未关闭P0/P1/P2代码问题；不代表未测试的远端合同与设备环境已经合格。Gate/Stop以 CURRENT_STATE 为准：本地通过、外部Sandbox配置阻塞、禁止自动merge/Production。

## Execution Time Breakdown

| Stage | Time | Notes |
|---|---|---|
| Investigation / Planning | Estimated 15–20min | Dashboard、官方API、既有领域与隔离worktree |
| Implementation | Estimated 20–30min | 后端、前端、V29、通用打印来源 |
| Tests | Estimated 20–30min | 夹具修正、并发锁和3项review修复、全回归，阶段并行 |
| Agent 6 / PR / Merge | Estimated 5–10min | 初审及复核通过；PR准备；不merge |
| Staging Deploy | N/A | 未执行 |
| Staging Acceptance | N/A | 未执行 |
| Evidence / Governance | Estimated 10–15min | A–Z、配置与运行限制 |
| Total | Estimated active 60–90min；墙钟至少9h47min | 已知实现文件创建13:17、本次收尾23:04（America/Toronto）；包含中断空档。任务初始时间无法精确回溯，不能把墙钟空档当作持续执行时间。 |

PRODUCT IMPLEMENTATION：backend/frontend/Flyway/tests及少量generic printer header。TOOLING/GOVERNANCE：隔离本机PG/测试进程、文档/env示例/state/backlog；用于保护原脏工作区、验证数据库约束与说明外部阻塞。无额外平台重构、无真实Store/硬件绑定。
