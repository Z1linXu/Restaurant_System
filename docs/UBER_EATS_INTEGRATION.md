# Uber Eats Order Integration v1

本版本接入外卖订单收件箱，员工明确 Accept/Deny。默认关闭。真实账号连接、测试门店与公开 HTTPS callback 必须另行配置；代码与本机 fixture 通过不代表 Uber Sandbox 或 Production 验收通过。验收证据见 [Final Acceptance Report](UBER_EATS_ACCEPTANCE.md)。

2026-10-01 最新证据见 [下单前检查](governance/UBER_PREORDER_CHECK_20261001.md)：Dashboard 已恢复访问，显示固定 Staging URL / PRIMARY / BASIC_HMAC。Test Store 已绑定 Staging `STG005_SRC_20260809_R01`；独立 signing-key 部署结果以新检查为准。真实订单/Webhook/Accept/打印仍未测试，菜单稳定 ID 映射尚未建立。[此前 binding audit](governance/UBER_TEST_STORE_BINDING_20261001.md) 的 Dashboard 阻塞与旧验收 `0 stores` 都是历史观察。

## 数据流与现有领域复用

Uber → `UberEatsWebhookController` → raw-body HMAC → `UberEatsWebhookService` durable event → `UberEatsWorker` → `UberEatsOrderClient.getOrder` → `UberEatsOrderNormalizer` → `UberEatsMenuMappingService` → Frontdesk inbox → 员工 Accept → `UberEatsOrderTransactions.prepare` → Uber Accept → accepted durable checkpoint → `submitLocal` → `OrderService.createOrReplaceDraftAndSubmit` → 现有 order items/options、kitchen/production tasks、BOM 库存流水、realtime、dispatch outbox → `PrintDispatcherService` → 现有 GRAB/HOT_KITCHEN/FRONTDESK renderer 与执行模式。

没有复制订单提交、厨房格式、库存或打印流程。唯一通用打印增强是 `ExternalOrderReceiptHeader`：本地订单携带外部来源时显示来源和 display ID。无外部来源的 Pad 输出不变。`completeOrder`、Finish、支付、退款、价格结算不改动。

## 已核实的官方 API

核实日期 2026-09-16。以下是官方文档地址，不能把本地 mock 当作远端调用成功。

| 用途 | 方法与路径 | Scope | Sandbox host | Production host |
|---|---|---|---|---|
| OAuth | POST `/oauth/v2/token` | client_credentials 请求下列 scopes | `https://sandbox-login.uber.com` | `https://auth.uber.com` |
| Get Order | GET `/v2/eats/order/{order_id}` | `eats.store.orders.read` 或 `eats.order`，本版请求两者 | `https://test-api.uber.com` | `https://api.uber.com` |
| Accept | POST `/v1/eats/orders/{order_id}/accept_pos_order` | `eats.order`，且 app 为 nominated order manager | 同上 | 同上 |
| Deny | POST `/v1/eats/orders/{order_id}/deny_pos_order` | `eats.order` | 同上 | 同上 |

来源：[Sandbox](https://developer.uber.com/docs/eats/guides/sandbox)、[Get Order](https://developer.uber.com/docs/eats/references/api/v2/get-eats-order-orderid)、[Accept](https://developer.uber.com/docs/eats/references/api/v1/post-eats-order-orderid-acceptposorder)、[Deny](https://developer.uber.com/docs/eats/references/api/v1/post-eats-order-orderid-denyposorder)、[Webhook](https://developer.uber.com/docs/eats/references/api/webhooks.orders-notification)、[事件指南](https://developer.uber.com/docs/eats/guides/webhooks)、[Customer edit changelog](https://developer.uber.com/docs/eats/api-change-log)。

本版不调用 menu push、store provisioning、Uber cancel、courier 状态或付款 API。`eats.store` 与 `eats.pos_provisioning` 不用于当前 runtime endpoints。已有门店必须在 Uber 侧 provision 并指定该 app 为 order manager；不能仅根据 Dashboard 显示 scope 名称认定 token 已授权。

## OAuth 与配置

`UberEatsOAuthClient` 使用 backend form client_credentials；同步内存缓存 token，根据 expires_in 提前最多 60 秒（短 token 提前约 10%）刷新。401 清除缓存；后续请求重新取 token。网络超时 connect 5s/read 15s，不跟随 HTTP redirect。OAuth/API 异常只保留安全错误码，不记录请求、响应 body 或 Authorization。token 不入数据库、不返回前端、不进入 Android payload。

| 环境变量 | 默认 / 用途 |
|---|---|
| `UBER_EATS_ENABLED` | `false`；true 时必须有 client ID/secret |
| `UBER_EATS_ENVIRONMENT` | `sandbox`；仅允许 sandbox/production，host 固定 |
| `UBER_EATS_CLIENT_ID` | 空；backend only |
| `UBER_EATS_CLIENT_SECRET` | 空；backend secret manager/private env，用于 OAuth |
| `UBER_EATS_WEBHOOK_SIGNING_KEY` | 空；独立 webhook HMAC key，配置后优先使用；未配置时兼容原 client secret |
| `UBER_EATS_SCOPES` | `eats.order eats.store.orders.read` |
| `UBER_EATS_WORKER_ENABLED` | `true`；维护时暂停异步消费/恢复 |

示例 `backend/.env.uber-eats.example`；Spring 不自动加载 .env，必须由进程/容器注入。Staging compose 强制 sandbox，默认关闭。没有修改任何 Production compose/Flyway target。不得把前端 `VITE_*` 作为凭据入口。Store mapping 使用数据库/API，不另建第二份 env Store source of truth。

## Webhook

`POST /api/v1/integrations/uber-eats/webhook`，部署时组合可公开访问的 HTTPS origin。`X-Uber-Signature` 为 `UBER_EATS_WEBHOOK_SIGNING_KEY` 对**原始请求字节**计算的 HMAC SHA256、小写 hex；常量时间比较。独立 key 未配置时才兼容原 client secret；配置独立 key 后不再额外接受 client secret 签名。此版本不实现 Secondary Key 双验签。要求 `X-Environment` 与配置一致。仅此精确 POST 路径绕过浏览器 bearer 解析，完全依赖 signature trust；其他 integration endpoints 保留正常用户认证。

body 上限 256 KiB；仅存 event ID/type、environment、store/order UUID、body SHA256、重试状态。缺少/错误签名 401；已签名的错误 JSON/元数据或环境不符 400；超限 413；disabled 503。已保存相同事件重放 200；相同 event ID 不同 body 409；未知事件类型 IGNORED 并 200。有效事件持久化后 200 empty ACK，不等待 GET、OAuth、本地订单或打印。

支持 `orders.notification`、`orders.cancel`、`orders.scheduled.notification`、`orders.customer_order_edit`。scheduled 提前通知仅展示需检查，不提前制作；regular notification 到达才解除 scheduled guard。取消/改单在 webhook 数据库事务中立即置标，即使 Accept 网络请求正在返回，也不能越过 guard 提交厨房。事件消费独立 scheduler 每 2 秒，失败保留 durable retry。Webhook DB 不可用时不能假 ACK；Uber 必须重投。Uber 接单窗口需按官方当前约 11.5 分钟运营要求及时处理。

## Store 与菜单映射

`uber_eats_store_mappings` 绑定 environment + Uber Store UUID → local store ID + organization ID。共享 app 的外部门店归属只允许平台 ADMIN 建立；Owner 无权抢占其他 tenant 的 Uber Store。绑定不可通过 UI 重绑。每次导入/恢复再次核对本地 organization、启用状态和环境。员工 API 明确经过 `AuthorizationService` + `StoreAccessService`。

菜单优先级：该 Store 的显式 EXTERNAL_DATA rule → 显式 ID rule → **精确** external_data=local SKU（item）或 parentless option_code（modifier）。不以 title/name 匹配，不翻译英文菜名，不模糊搜索。modifier rule 限定 Uber 根菜品 ID 与 local menu item；组合去除选项还限定 parent_option_code。正常 item ID 本身不是自动 SKU fallback。

`UberEatsMenuMappingService` 从现有 `MenuService.getCatalog` 获取同一份 effective prices、Chinese names、option type/code/group、station/category/SKU 和 combo configuration。组合组件 ID 与 Pad 固定兼容 ID/FNV 规则一致；组合小菜通过 linked local item ID/SKU 找到 REMOVE 选项。`removed_items` 必须配置 REMOVED_MODIFIER → REMOVE/COMBO_SIDE_REMOVE，不能当成 Add-on。

完整支持 SIZE、NOODLE_TYPE、SPICY_LEVEL、SOUP_BASE、ADD_ON、REMOVE、COMBO、COMBO_EGG、COMBO_SIDE、COMBO_SIDE_REMOVE 以及 catalog 声明的组合组件。未知/多义/不可售/缺失必选/非法数量/失配 parent 使整个订单 MAPPING_REQUIRED。结构化 special_requests、fulfillment_action 与 cart fulfillment_issues 暂不支持，显式阻塞，绝不丢弃后继续 Accept。notes 进入同一 local notes/special instructions 流；超过现有 255 字段容量保守阻塞，不截断制作指令。

Owner/Admin 管理页：`/stores/{storeId}/admin/integrations/uber-eats`。选择现有本地菜品/选项、保存稳定标识。展示 enabled/environment、last observed webhook、Uber Store、未映射订单数。保存后重算 inbox 待处理映射；Accept 前再 GET 最新 Uber 订单和最新 catalog。配置 catalog 接口不依赖点餐 runtime 已启用。

## Inbox / Accept / Deny

Frontdesk：`/stores/{storeId}/frontdesk/uber-eats`。顶栏 badge、5 秒可见页轮询与现有门店 WebSocket 提示；卡片显示来源、display ID、时间、数量、所有嵌套 modifiers、备注、mapping errors、后端状态。列表优先未完成记录，最多 100 条；大量积压须运维查询，v1 无分页。双击 UI ref guard + backend row lock/状态机/DB unique 共同防重复。

Accept 分短事务：锁行并 refresh（避免 JPA first-level stale state）→ 验证 store、CREATED、mapping、Ordering/Menu capability → 冻结本地 request 与 actor、写 ACCEPTING → 事务外 POST Uber（reference `rs-uber-{integrationId}`）→ UBER_ACCEPTED checkpoint → 本地统一领域事务与 localOrderId 原子提交。网络 I/O 不占业务 DB 事务。GET Order 的 `order_manager_client_id` 可被Uber脱敏，因此不与明文client ID比较；nominated-manager权限由真实Accept/Deny API判定，只有远端204才允许本地提交，403不得产生本地订单或厨房/打印任务。

Deny 必须有官方 reason_code；写 DENYING → 真实 POST → DENIED。重复点击终态不重复调用 Uber，不创建本地订单。官方 Deny 不保证立即取消消费者订单，仍以 Uber 状态/事件为准。

## Reliability / Reconciliation

| 持久状态 / 窗口 | 恢复策略 |
|---|---|
| 已 ACK 但未 GET | 重启后消费 PENDING event；90s lease 到期继续 |
| ACCEPTING/DENYING 进程中断或 timeout/5xx | GET 对账；不盲重发 POST |
| ACCEPTING + remote ACCEPTED + 相同 external_reference_id | 记录 accepted，再执行冻结的 local request |
| DENYING + remote DENIED | 结束本地 deny |
| 远端无法证明本次 decision 成功 | DECISION_REVIEW_REQUIRED，人工在 Uber 核实，禁止再 Accept |
| Uber accepted 已持久化，本地事务失败 | 全部本地写入回滚；LOCAL_FAILED 自动重试，最多 10 次后 LOCAL_REVIEW_REQUIRED；按钮只重试 local，不再调 Uber Accept |
| local 事务已提交但响应丢失 | durable localOrderId 与 unique 保证 replay，无第二张单 |
| 打印失败 | committed local order 保留；现有 outbox/job 重试与 PAD_DIRECT 模糊打印保护 |

恢复仍核对 Store binding 与取消/改单 guard。没有自动“强制成功”工具。需要数据库修复或人工解决歧义时先核对 Uber 状态、stable reference、localOrderId、existing order/outbox/jobs；经既有运营审批执行，禁止直接清 event 或重复插入订单。暂停 worker 可保留 webhook durable receipt。Undo migration 不属于回滚方案：先禁用 integration、保留事件和订单审计，再按 reviewed release 回滚；不可 drop 已关联的表。

## Cancellation / Customer edit

Accept 前取消：CANCELLED，无本地 kitchen/print。Accept 后或网络决策中取消：CANCELLED_REVIEW_REQUIRED，保留 local order、snapshots、tasks、jobs；员工按既有业务处理，不硬删、不自动退款。Customer edit 永久记录事件并置 EDIT_REVIEW_REQUIRED。**AUTO APPLY CUSTOMER EDIT = DISABLED**。未知事件不会触发 local production。

## Printing / PAD_DIRECT

提交只产出现有 dispatch outbox；没有 socket/printer 调用。现有路由决定 GRAB/HOT_KITCHEN/FRONTDESK。MOCK 可以完成 job 并保存渲染；PAD_DIRECT 保持 PENDING → device pending/claim → start-print → payload（含 assigned printer、ESC/POS）→ complete；重复保护、claim token/lease、模糊物理失败处理均由现有 service 实现。DISABLED 不影响 order commit。REAL 必须另做真实打印机验收；模拟 complete 不代表出纸。

## Sandbox setup / Production setup

1. Uber Dashboard 确认可用 Sandbox app、获批 scopes、test store 与本 app order-manager provisioning。把新的 client secret 写入 backend 私密环境，勿发聊天。
2. 独立测试 DB 运行 V29，启用 sandbox integration；平台 ADMIN 绑定**明确归属**的 Uber Store → local Store。
3. Owner 建立稳定 item/modifier mappings（可用 external_data 精确协议）；MOCK 或已验证的测试 PAD_DIRECT。
4. 提供公开 HTTPS callback，Dashboard 添加 primary webhook，选择目标 testing environment。执行真实 signed delivery，再通过官方测试订单生成步骤验证本报告 S 的完整 IDs 链。
5. Production 必须另外具备 Production app/scopes/provisioning、独立 secret、域名和 store mapping；经 exact-SHA reviewed migration、备份、Staging、硬件现场验收与 Owner 发布授权。当前不部署、不启用 Production。

排障：401 webhook 核对 raw bytes/key；400 environment 核对 sandbox/prod；STORE_MAPPING_MISSING 先绑定正确 Store；MAPPING_REQUIRED 根据原始稳定 ID 补配置；UBER_API_401 核对 app/token host；403 核对 scopes/order-manager；LOCAL_SUBMISSION_FAILED 检查 station、menu/store capability、库存/BOM 领域条件；CANCELLED/EDIT/DECISION_REVIEW_REQUIRED 必须运营复核。日志仅有安全代码；不要通过开 HTTP wire debug 排查 OAuth。

## 隐私、安全与已知限制

Normalized snapshot 是字段白名单，排除 eater phone/email/address、payment、courier；raw 字段名为历史命名但内容并非原始 Uber 全包。自由备注仍可能包含顾客主动输入的个人信息，应按订单访问/保留政策处理，不能宣称彻底去 PII。HTTP response 不暴露 local frozen request 或凭据。V29 不预置真实 Store/菜单/secret。Store ID 为公开标识，不是认证。

本地用 catalog price/tax 保留现有订单语义，不做 Uber 总额、优惠、税费、佣金和付款 reconciliation，也不向顾客再收费。Uber pickup 在本系统作为来源订单 delivery context 进入厨房，保留外部 fulfillment_type，不占 Pad pickup/table 编号。v1 不支持自动 scheduled release、自动改单/取消厨房、自动 provision/menu sync、映射删除/重绑；订单超多时 inbox 100 条限制需关注。实际 Sandbox 远端证据、真实 Android、REAL 出纸尚需独立完成。

## Production Pilot 门店配置计划

Owner 最新确认的最终 Production capability 目标为 St-Denis 和 St-Catherine 启用、Chinatown 禁用。St-Denis 已提供 UUID `5a7dca5c-b7ec-57c8-a684-87d23e66e8d9`；St-Catherine UUID 待提供。本 UUID 仅记录，不用于 Sandbox binding，不据此 provision、切 order manager、改菜单或启用接单。真实 TEST Store 是 `bd993244-5589-4b19-8f0d-dc2ba73d4273`，不能混用这两个 UUID。

先满足真正 Sandbox E2E PASS、Integration Verification approved、Production scopes approved，再取得明确 Production Pilot 授权，才执行各门店 Enable → Bind UUID → Map Menu → Test → Activate。V37 已复用正式 `store_modules` 增加默认关闭的 `UBER_EATS` capability，统一控制按钮、前台/管理路由、所有 Store-scoped Uber APIs、重打与后台导入恢复；不重构现有订单/打印。启用 module 不等于建立 binding 或授权 Production activation。

2026-09-17 继续工作授权已扩大到 merge-gate review、修复/测试/合并、独立 Staging备份部署与HTTPS webhook、真实Testing E2E及符合Uber流程的verification/scope申请；不包含Production接单激活或任何Production不可逆操作。Secret仍只允许配置于backend私密环境，例如经核实部署目标后的 `/srv/restaurant-pos/staging/config/.env.staging` 的 `UBER_EATS_CLIENT_SECRET`，不可写入仓库或聊天。当前技术文档路径不代表服务器已核实或配置已生效。


## Kitchen Mirror extension (2026-10-02, V31)

See `doc/API.md` for the additive mode/mapping/inbox contract. In KITCHEN_MIRROR,
notification schedules the accepted-state checks described in V32 below. ACCEPTED/release shares the kitchen gate after fresh GET and Store validation. No remote Accept/Deny or local receipts.
Financial snapshots remain separate from zero-valued kitchen records and in-store
analytics. Existing OrderService, task/inventory logic, GRAB/HOT renderers, outbox,
manual reprint audit/idempotency/Pad protections are reused.

Test Store `bd993244-5589-4b19-8f0d-dc2ba73d4273` currently reports
`order_release_enabled=false`. Official release documentation describes kitchen
release (including courier proximity behavior), so Tablet acceptance alone must
not be represented as a guaranteed immediate release event. The documented PATCH
pos_data does not expose a writable order_release_enabled field. Do not invent a
PATCH or substitute production Store IDs. Uber configuration/support is required;
no real Sandbox test order is authorized in this batch.

## Kitchen Mirror ACCEPTED observation — V32

Kitchen Mirror no longer requires orders.release as its only trigger. The existing
backend worker durably checks CREATED orders until it observes ACCEPTED, with
2/5/10/15/30/60/120-second backoff, maximum 40 attempts or 30 minutes, and a 90-second
claim lease. Terminal states and unknown/FINISHED states stop; ambiguous states
require review. API failures back off at least 60 seconds. Structural/scope failures
wait for corrective review; missing mappings use V33 frozen fallback. accepted_observed_at is a
local UTC observation timestamp; accepted_at remains reserved for legacy decisions.
Both triggers reuse the same transactional kitchen checkpoint/local-order/outbox path.
No Accept/Deny or receipt is enabled by this change. See the [API contract](../doc/API.md).

The Owner approved the final-table MAP/NO_OP rules; 226 context rules are persisted
for Test Store -> Store 1 only. The exact table retains all unconfirmed identities.
The authorized TEST-only Order Manager resignation uses optional is_order_manager=false
with eats.store scope and preserves integration_enabled. Its runtime result and real
consumer/Tablet E2E are separate gates; code and mapping preview cannot prove them.

### TEST webhook environment compatibility (Owner authorized 2026-10-02)

The real TEST Store notification used `X-Environment: production` despite a valid TEST HMAC and a readable matching order on `test-api.uber.com`. Default strict environment isolation remains. Only `APP_ENVIRONMENT=staging`, `UBER_EATS_ENVIRONMENT=sandbox`, a matching nonempty `UBER_EATS_TEST_WEBHOOK_CLIENT_ID` and exact `UBER_EATS_TEST_WEBHOOK_STORE_ID` opt in. A recognized order event must target an enabled KITCHEN_MIRROR binding; raw HMAC is verified first. Before any durable event/order/cancel mutation, Sandbox GET Order must return the same order and store UUID. Failed lookup returns retryable 503; identity mismatch returns 400. Missing/unknown headers, other stores/apps and Production runtimes cannot use this exception. Credentials and raw traffic are never logged. The TEST-only pre-ack read uses the existing bounded API timeouts; ordinary matching-environment webhooks retain fast durable receipt.

## Mirror mapping fallback — V33

The Owner changed only Kitchen Mirror's missing-identity behavior: accepted orders must reach kitchen. Known roots/options retain local semantics; unknown modifiers print raw. Unknown roots freeze raw IDs/names/quantity/nested modifiers/notes in the additive order-item snapshot column, create GRAB-only holding tasks, and show `UNMAPPED_ROUTE_REVIEW`. No guessed HOT station, menu identity, BOM or auto-persisted mapping. Mixed orders preserve every line. `KITCHEN_SENT_WITH_MAPPING_WARNINGS` / `PARTIALLY_MAPPED` and Today `raw_items` expose the issue without disabling kitchen reprint. Existing frozen tickets do not change when future mapping rules change. Unsupported source structures, invalid quantities, tenant/environment mismatch and cancellation/edit guards remain blocking. ORDER_MANAGER behavior above is unchanged. Both existing kitchen renderers use their TAKEOUT footer for Mirror.

[All 41 roots and current persistence](governance/UBER_ROOT_MAPPING_STATUS_20261002.md). The current field repair is TEST/Staging Store 1 only, including the Owner's already-paired Pad and printer endpoint; it authorizes no Production action.
