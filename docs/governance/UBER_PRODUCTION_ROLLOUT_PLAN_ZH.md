# Uber Eats Production 上线说明与操作计划

**DRAFT / SUPPORTING / NOT AUTHORIZED — 本文是设计和只读审计，不是上线授权。**

核对日期：2026-10-03。代码基线：`9c8d8887ec4ad12ac384e3e8a3ee80aec80d1e14`。
Production 现场只读审计首次时间戳：17:44:42 UTC，后续同轮补充 Store/module/账号角色与镜像标签读取。本文不设置第二份 Phase/Gate；执行授权仍以
[CURRENT_STATE.yml](CURRENT_STATE.yml) 和 Owner 对具体 Production 发布批次的确认决定。
本轮没有调用 Production Uber API、提交 Support、修改 Production DB/config、部署或重启。

## 1. 现在 Test 是怎样连上的

Uber 的 Store UUID 是 Uber 给门店的编号；Restaurant_System 的 `store_id` 是我们自己数据库里的编号。
它们不需要相同。系统用 `uber_eats_store_mappings` 这张对应表，把两边明确连起来：

```text
Uber TEST App
  → Uber Test Store bd993244-5589-4b19-8f0d-dc2ba73d4273
  → Staging uber_eats_store_mappings，binding id=1
     environment=sandbox，organization_id=1，store_id=1，enabled=true
  → Restaurant_System Staging Store 1：STG005_SRC_20260809_R01
  → processing_mode=KITCHEN_MIRROR
  → 员工在 Uber Pad 接单 → 系统读到 ACCEPTED → 现有厨房、Outbox、打印流程
```

上面的绑定和 `KITCHEN_MIRROR` 已于本轮从 **Staging DB** 重新读回；Flyway 为 V33。
真实 Test 订单、人工 Uber Pad Accept、accepted-state 检测及厨房镜像的历史证据见
[Mirror E2E](UBER_KITCHEN_MIRROR_E2E_20261002.md)。最新字段、菜单确认范围分别见
[系统说明](../../SYSTEM_DOCUMENTATION.md)、[41 个 root 确认结果](UBER_ROOT_MAPPING_STATUS_20261002.md)
和[厨房映射表](UBER_KITCHEN_MAPPING_FINAL_20261002.md)。后续 Part A 的变更验收另行记录，不由本文预先宣称通过。

Webhook 到达后，backend 先检查原始 body 的 HMAC 和环境，取事件里的 Uber Store UUID，
查找**同环境**的 binding，再核对 local Store 的 organization、binding enabled 和处理模式。
因此订单归属来自经过校验的数据库关系，不依赖当前哪台 Pad 打开页面，也不根据餐厅名称猜。
重复事件、重复订单和通知/轮询同时观察到 ACCEPTED，仍经过现有唯一约束与单次厨房提交关口。
代码依据：[Webhook service](../../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsWebhookService.java)、
[Order transactions](../../backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsOrderTransactions.java)、
[V29](../../backend/src/main/resources/db/migration/V29__add_uber_eats_integration.sql)。

**Staging Store 1 不是 Production Store 1。** 两个独立数据库都可能有数字 `1`；
只有完整的环境、数据库、organization、Store ID 和 Uber UUID 一起才能确定目标。

## 2. Production 三家店的真实状态

本轮对 `cloud-db-1` / `restaurant_pos` 使用 `BEGIN READ ONLY` 读取 `stores`、
`store_modules`、Flyway ledger 和 schema metadata。没有读顾客订单或修改数据。

| Restaurant Store | Restaurant Store ID | Store code | Organization | Status / lifecycle | UBER_EATS Desired | Current Uber Mapping |
| --- | ---: | --- | ---: | --- | --- | --- |
| St-Denis（DB 名称 `4483 R. Saint-Denis`） | 1 | `4483_R_SAINT_DENIS` | 1 | active / ACTIVE，BUSINESS | ENABLED | `MISSING_CAPABILITY`：Uber tables 尚不存在 |
| St-Catherine（DB 名称 `St. Catherine`） | 3 | `ST_CATHERINE` | 1 | active / ACTIVE，BUSINESS | ENABLED | `MISSING_CAPABILITY`：Uber tables 尚不存在 |
| Chinatown（DB 名称 `CHINATOWN`） | 2 | `CHINATOWN` | 1 | active / ACTIVE，BUSINESS | DISABLED | `MISSING_CAPABILITY`；未来也不创建 Uber binding |

`Desired` 是本轮 Owner 明确确定的最终业务规则，**不表示已经启用**。此前仅 St-Denis 的候选计划
不再代表这份两店设计，但不能据此自动扩大 Production 执行权限。

当前三店的 `store_modules` 配置相同：`MENU`、`MENU_MANAGEMENT`、`ORDERING_POS`、
`ORDER_HISTORY`、`PRINTING`、`REPORTING_CORE`、`STAFF_ACCESS`、`STORE_ADMINISTRATION`、
`TABLE_MANAGEMENT` 为 enabled；`KDS`、`ANALYTICS_ADVANCED` 为 disabled；均 CONFIGURED。
这些是持久配置，不代替 runtime/hardware readiness。

| Store | printing_enabled | printing_mode | 意义 |
| --- | --- | --- | --- |
| St-Denis / 1 | true | PAD_DIRECT | 已有本地点餐打印配置；不等于 Uber Production 出纸已验收 |
| St-Catherine / 3 | false | DISABLED | 即使 PRINTING module 配置为 true，当前门店打印仍未启用 |
| Chinatown / 2 | true | MOCK | 这是现状记录，不是本轮要改变的目标 |

Production Flyway 最新成功版本是 **V28**；schema 中没有任何 `uber_eats_*` 表。
当前 backend/frontend 镜像 RepoTag 指向 `11996ef919d9b28ee5366c7e40a58a78c074b675`，
backend image ID 为 `sha256:603f79e0272a3fe83fdd7ac9bb58b72dc2b1facb3ceef720bc636bf5b9c5469d`，
frontend 为 `sha256:d82650726a7faec62f1270a98bc7e878f0060392d5b6d62fa75ee03bf6e0728f`。
RepoTag 名称带 `staging-`，但实际容器为 `cloud-*`、`APP_ENVIRONMENT=production`、DB 为
`restaurant_pos`；不能用镜像标签里的单词判断环境。

Production backend 注入环境中没有 Uber client secret、webhook signing key 或 TEST compatibility pair。
这只证明容器注入状态；没有据此推断 Uber Dashboard 是否已经创建/批准某个 Production App。
Production Uber 账号、App、scope 和真实店 provisioning 本轮均**未连接、未核实**。

## 3. Uber 当前官方流程：哪些是事实，哪些还需确认

以下官方网页已于 2026-10-03 重新读取。它们说明可用机制，不证明本账号已经获得批准。

| Owner 问题 | 当前结论与官方依据 |
| --- | --- |
| TEST App 能直接用于 Production 吗？ | **不能。** Sandbox 访问与 Production 授权分开；官方要求完成集成后建立 Production application。[Sandbox](https://developer.uber.com/docs/eats/guides/sandbox) |
| 是否需要新的 Production App？ | **需要。** Going Live 要求独立 Production App/Production Uber account，不能使用 Uber 发放的测试账号上线。[Going Live](https://developer.uber.com/docs/eats/guides/going-live) |
| 是否需要 integration verification？ | **需要。** 内部测试后通过 Tech Support 安排 Uber 联合验证；本项目必须按实际 Mirror 工作流申请，不能将 Sandbox PASS 写成 Uber approved。[Going Live](https://developer.uber.com/docs/eats/guides/going-live) |
| Production scopes 怎样开？ | App 先获 Uber approval/whitelist；随后在 Dashboard 启用相应 scopes。实际 token granted scopes 和接口结果仍要验证。[Authentication](https://developer.uber.com/docs/eats/guides/authentication)、[Going Live](https://developer.uber.com/docs/eats/guides/going-live) |
| 真实 Uber 店怎样关联 App？ | 官方列出 Uber onboarding 预配置、live merchant 联系 Tech Support、merchant OAuth 自助 activation 三种途径。[Integration Configuration Flows](https://developer.uber.com/docs/eats/guides/integration-activation-flows) |
| 是否需要 Uber 支持/partner manager？ | 推荐路线需要 Tech Support 处理验证、权限和两店配置，并与 partner manager 排上线时间；上线前至少提前一周告知首店及日期。[Going Live](https://developer.uber.com/docs/eats/guides/going-live) |
| 必须开发通用 merchant OAuth 吗？ | **不必。** 它是自助 activation 路线的要求，不是三种路线共同的前提。固定自有两店优先走 Support/onboarding，由 Uber 确认可用流程；如果 Uber 要求 merchant consent，完成必要授权即可，不扩成第三方 SaaS 入驻平台。[Activation Flows](https://developer.uber.com/docs/eats/guides/integration-activation-flows) |

推荐路线是**工程建议**：准备两店身份、Production App 和 Mirror 说明，请 Uber 按被动观察角色关联两店；
获批后正常 API 使用 backend client-credentials token。只有选择自助 activation 时，才需要商户
authorization-code token 和 `eats.pos_provisioning`；这类用户 token 不能代替普通 Menu/Order API token。
参考：[Authentication](https://developer.uber.com/docs/eats/guides/authentication)、
[Activate Integration](https://developer.uber.com/docs/eats/references/api/v1/post-eats-stores-storeid-posdata)。

### 非 Order Manager 的 Mirror 是否有官方依据

有**角色机制**依据：Activate Integration 明确说被动观察 store/order activity 的应用不要请求
`is_order_manager`；同店只有一个 Order Manager，申请成为 manager 可能使原 manager 被降级。
Update Integration Details 说明退出 manager 后保留 API access，但不再允许 Order API 写操作。
参考：[Activate Integration](https://developer.uber.com/docs/eats/references/api/v1/post-eats-stores-storeid-posdata)、
[Update Integration Details](https://developer.uber.com/docs/eats/references/api/v1/patch-eats-stores-storeid-posdata)。

这些说明**不等于本项目 Production Mirror 已获批**。上线前需要 Uber 针对两店确认：

- Production App 作为 observer 获得该店订单通知、取消/改单信息及 GET Order 权限；
- Uber Eats Orders / Uber Pad 继续让员工人工 Accept，现有 manager/收据流程不被替换；
- 手工接受后 `current_state=ACCEPTED` 能被本应用读取；没有依赖尚未启用的 `orders.release`；
- Mirror 的验证范围、菜单只读边界及质量指标被接受，支持人员不会套用整套菜单托管/POS manager 切换流程。

`integration_enabled` 与 `is_order_manager` 是不同概念。不能为“不要当 Order Manager”而关闭整个
integration；官方说明前者关闭会失去多数 API 和 webhook。
参考：[Update Integration Details](https://developer.uber.com/docs/eats/references/api/v1/patch-eats-stores-storeid-posdata)。
本轮不发送上述请求，也不执行任何 Production POST/PATCH/DELETE。

### 最小 scope 方案

| 用途 | Scope / token | 当前设计判断 |
| --- | --- | --- |
| Mirror GET Order v2 | `eats.store.orders.read`；官方也接受 `eats.order` | 使用 client_credentials；只读订单是业务目标。[Get Order](https://developer.uber.com/docs/eats/references/api/v2/get-eats-order-orderid) |
| 已关联 Store discovery、读取每店真实菜单 | `eats.store` / client_credentials | 上线准备确有用途；此 scope 也含写能力，代码和操作范围仍禁止 live menu 写入。[List Stores](https://developer.uber.com/docs/eats/references/api/v1/get-eats-stores)、[Get Menu](https://developer.uber.com/docs/eats/references/api/v2/get-eats-stores-storeid-menu) |
| 自助授权/activation | `eats.pos_provisioning` / authorization_code | 仅 Uber 要求/选择自助路线时申请；不是日常 Mirror token。[Activate Integration](https://developer.uber.com/docs/eats/references/api/v1/post-eats-stores-storeid-posdata) |
| 报表、online/offline、配送写接口 | 未使用 | 不为本次 pilot 申请。[Authentication](https://developer.uber.com/docs/eats/guides/authentication) |

当前代码仍要求 `UBER_EATS_SCOPES` 包含 `eats.order`，默认请求
`eats.order eats.store.orders.read`，见 [UberEatsProperties](../../backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java)。
不能把默认列表称为已经收敛到最小只读权限。提交 scope 申请前，应向 Uber确认 observer 所需权限，
并确定是保留经批准的兼容 scope，还是用一个受审阅的小修复解除 Mirror-only 的不必要 scope 要求；
不能直接改配置导致启动失败。无论 scope 如何获批，Mirror backend 都不允许 remote Accept/Deny。

## 4. TEST 和 Production 配置完全隔离

| 项目 | STAGING / TEST（实际） | PRODUCTION（设计，尚未配置） |
| --- | --- | --- |
| `APP_ENVIRONMENT` | staging | production |
| `UBER_EATS_ENVIRONMENT` | sandbox | production |
| Uber App / credentials | TEST 独立私密 backend 配置 | 新 Production App 独立私密 backend 配置 |
| API | `https://test-api.uber.com` | `https://api.uber.com` |
| OAuth | `https://sandbox-login.uber.com/oauth/v2/token` | `https://auth.uber.com/oauth/v2/token` |
| Store UUID | `bd993244-5589-4b19-8f0d-dc2ba73d4273` | 每家真实店单独核验 |
| Webhook signing material | TEST 专用 key | Production 自己的签名配置；不复制 TEST key |
| TEST webhook exception | 已授权 exact TEST app/store pair | **必须为空/关闭；不能绕过环境隔离** |

域名依据：[Sandbox](https://developer.uber.com/docs/eats/guides/sandbox)。现有 backend 通过环境枚举选择
集中、固定的官方 host allowlist，不在 frontend、Android、每店配置或调用处复制 URL/credentials。
不接受 webhook payload 提供任意 host。Production secrets 不放 Git、`.env.example`、APK、浏览器或日志。
官方同样要求 token 留在服务器：[Authentication](https://developer.uber.com/docs/eats/guides/authentication)。

## 5. 未来两家店绑定与同一 APK 的开关设计

| Production 目标 | Uber UUID | Local Store / org | Processing mode | Desired enabled |
| --- | --- | --- | --- | --- |
| St-Denis | `5a7dca5c-b7ec-57c8-a684-87d23e66e8d9`（Owner 提供，本轮未向 Uber 验证） | 1 / 1 | KITCHEN_MIRROR | true |
| St-Catherine | **尚未提供** | 3 / 1 | KITCHEN_MIRROR | true |
| Chinatown | 不申请、不虚构 | 2 / 1 | 不建立 binding | false |

保留现有 `(environment, uber_store_id)` 和 `(environment, store_id)` 唯一约束；每次处理核对
organization。不能让同一 Uber Store 绑定两家 local Store，也不能把 Staging binding 复制到 Production。

**代码支持（V37，Production 尚未部署/配置）：** 正式 `ModuleKeys.UBER_EATS`
复用 `store_modules`，迁移和新 Store 默认 false；同一 Frontend/APK 读取已认证 Store
Context，enabled 才渲染 Uber 按钮，切店立即清除旧 capability。前台和管理路由都由
`RequireStoreModule` 保护。Backend StoreAccess/角色校验后要求相同 capability；
disabled 返回 `403 MODULE_DISABLED`。重打、Webhook 导入和恢复也受约束。

配置管理入口对 module enabled 的授权 Owner/Admin 开放，不要求已经绑定或 OAuth
配置完成。binding.enabled 仍决定外部 UUID 路由，module 不自动创建 binding。
不按店名/ID 硬编码、不为 Chinatown 创建 binding、不为每店编译 APK。
未来 Production 批次需经授权配置 St-Denis/St-Catherine enabled、Chinatown disabled；
本轮仅在现有 Staging TEST Store 启用，不能把代码实现当成 Production 配置已生效。

本轮 Production 有 OWNER/MANAGER/FRONTDESK 账号，**没有 ADMIN 账号**；现有 binding API
只允许平台 ADMIN。未来发布批次还要确定经过审阅的绑定执行主体/受控后台操作。
不能临时提升 Owner 角色，也不能直接把历史 Staging SQL 操作当作 Production runbook。

## 6. Production 菜单映射：搬语义，不搬 Test UUID

每家店获授权后，才只读 GET 它自己的 Production menu 和 local catalog。
官方提供的 Get Menu 按指定 Store 返回 items、modifier groups 及其引用关系：
[Get Menu](https://developer.uber.com/docs/eats/references/api/v2/get-eats-stores-storeid-menu)。
本轮没有执行 Production GET Menu。获授权后的 Store 列表和身份读取依据
[Store Integration](https://developer.uber.com/docs/eats/guides/store-integration)，不能只凭 UUID 格式认定已 provision。

分别准备 `ST_DENIS_PRODUCTION_MAPPING_DIFF` 与 `ST_CATHERINE_PRODUCTION_MAPPING_DIFF`：

1. 记录 source environment、Uber Store UUID、menu 获取时间/hash、目标 local Store/org。
2. 复用已经确认的语义，例如 Traditional Beef Noodle → `traditional_beef_noodle`、Extra Meat →
   `extra_meat`、Extra Vegetable → `bok_choy`；重新核实各店真实 stable item/modifier IDs。
3. 保留每个根菜品 parent-context、modifier ID、option_code/group、combo/removed 语义；
   `NO_OP` 必须保留已确认理由，不能因为 display name 相似自动忽略。
4. 高置信候选可自动生成供审核，持久化仍使用该店 Production ID 和对应 local identity。
   即使 Test 的 41 个 root 语义完全相同，也不能假定 UUID 相同或同店同菜单。
5. 用既有 mapping preview 检查厨房中文、routing、组合、notes；确认后在获准发布窗口写入并读回。

`RAW_UBER_FALLBACK` 继续保留真实未知内容，避免新 modifier 被丢掉；它不是映射已完成的证明。
RAW root 不虚构 local SKU/BOM/HOT routing。Production menu drift 要进入待审阅清单，
不能为消灭警告猜映射。Uber 金额保留外部 financial snapshot，不能用本地菜单价替代；Mirror
`financial_mode=EXTERNAL_PLATFORM`，不得计入 IN_STORE revenue。
实现依据：[系统 V33 说明](../../SYSTEM_DOCUMENTATION.md#kitchen-mirror-field-repair-v33)。

## 7. Production webhook 的真实缺口与安全设计

当前唯一实测配置的 HTTPS hostname 是 **staging-pos.lanzhounoodlesmtl.com**，其 443
server block 转发 Staging。Production `cloud-nginx-1` 的 DOMAIN 为空，Production 页面/API
处于 HTTP default server；443 default server 设置 `ssl_reject_handshake on`。
因此现在没有经过核实的 Production HTTPS callback，不能把 Staging URL 当成 Production URL。

**拟议、未配置的候选：**
`https://pos.lanzhounoodlesmtl.com/api/v1/integrations/uber-eats/webhook`。
它使用项目已使用的父域；子域控制权、DNS、TLS 和 Production 路由本轮尚未验证或建立。
只有 Owner 选定 hostname、发布批次获准、TLS/路由验收完成后，才可填 Production App Dashboard。
同一 Production App 的一个 primary webhook 可以由 backend binding 分流两店；不必为两店创建两套 APK。
Primary URL 的官方配置入口见 [Webhooks](https://developer.uber.com/docs/eats/guides/webhooks)。

安全验收使用 Production 实际签名配置：原始 bytes、HMAC SHA256、小写 hex、常量时间比较、
无 bearer/login 页面拦截、proxy 不改 body/header、无密钥日志。官方指南说明使用 client secret
计算 `X-Uber-Signature`；本项目现有实现优先 `UBER_EATS_WEBHOOK_SIGNING_KEY`，未配置才兼容
client secret。Production Dashboard 若提供独立 signing key，要与该配置对应并用真实签名验收，
不能把 TEST 的 BASIC_HMAC key 行为未经确认推广到 Production。
参考：[Webhooks](https://developer.uber.com/docs/eats/guides/webhooks)、
[现有签名 contract](../../SYSTEM_DOCUMENTATION.md#uber-webhook-signing-key-contract)。

需要证明：错误签名拒绝、合法签名持久化后快速 200 empty ACK、不同环境拒绝、重复 event/order
不重复厨房提交、相同 event ID 不同 body 拒绝、跨店/org 不导入、取消/改单保留 review guard。
HMAC 本身不能防合法旧事件重放，现有 durable event hash/unique order gate 承担去重。
官方事件/环境字段和重试机制见 [Notification](https://developer.uber.com/docs/eats/references/api/webhooks.orders-notification)
与 [Webhooks](https://developer.uber.com/docs/eats/guides/webhooks)。

TEST compatibility exception 不是全局 `ignore environment`：代码仅在 `APP_ENVIRONMENT=staging`、
Uber=sandbox、精确 app/store pair、enabled Mirror binding、HMAC 正确且 Sandbox GET 验证同一
order/store 后允许实际 TEST 的 production header。默认 pair 为空，Production 配置 pair 会在
启动时失败。未来 Production 配置必须保持两变量为空，并重新验证官方真实 Production header。
依据：[UberEatsProperties](../../backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java)、
[API exception contract](../../doc/API.md#test-webhook-environment-compatibility-owner-authorized-2026-10-02)。

## 8. 两店分批上线，保持 Uber Pad 人工接单

最终业务流：Uber Customer → Uber Order → Uber Eats Orders/Pad 人工 Accept →
Restaurant_System 读到 ACCEPTED → mapping → GRAB/HOT_KITCHEN → PAD_DIRECT。
Restaurant_System 不成为 Order Manager、不 remote Accept/Deny、不打印 Uber customer/courier receipt；
Uber 自己的 Pad/Printer 继续负责 Uber receipt。Today、snapshot-safe Reprint、financial snapshot
属于厨房镜像，不替代 Uber 的订单管理和结算。

**第一批 St-Denis；稳定后第二批 St-Catherine；Chinatown 不开。** 首店优先是本项目建议，
因为已有 Test/Staging 验证主要围绕 St-Denis 菜单。未来获准后按以下顺序执行：

1. Uber 确认 observer/Mirror 验证范围 → verification → Production App/scopes approved。
2. 先准备 exact-SHA Production 发布批次：备份、V28 到目标版本的逐项 additive migration 审核、
   artifact/rollback、两店 capability 默认关闭、正式 HTTPS 和 backend secret 隔离；Owner 明确批准后才部署。
3. Uber 按确认的 observer 路线关联 St-Denis；不申请 manager，不 PUT live menu；GET stores/pos_data
   验证真实 UUID、访问和角色。获准批次建立 local binding、menu mapping、webhook 后检查 Pad 入口。
4. 在预定运营窗口验收人工 Uber Pad Accept、ACCEPTED 检测、exactly-one local mirror、厨房内容、
   GRAB/HOT 和现场 PAD_DIRECT；每步关联真实 IDs。没有实际出纸证据不能标 PAD_DIRECT PASS。
5. 观察首店并与 Uber 对齐指标；通过后另行批准 St-Catherine 批次，使用其独立 UUID/menu/config。

官方 Going Live 的标准 POS 流程提及上传菜单和启用 order integration；**不要把这段照抄成 Mirror
执行命令**。新版 Activate Integration 参数表还说明 `pos_integration_enabled` 已 deprecated/ignored，
而其旧示例仍含该字段。应遵循当前 endpoint contract 并让 Uber 确认 observer 配置，不能据此切换
真实 manager 或覆盖 live menu。[Going Live](https://developer.uber.com/docs/eats/guides/going-live)、
[Activate Integration](https://developer.uber.com/docs/eats/references/api/v1/post-eats-stores-storeid-posdata)。

### 观察时间与成功率

官方 Going Live 写的是首店至少 **3 天**且 injection success rate **≥98%**后扩店。
[Going Live](https://developer.uber.com/docs/eats/guides/going-live)。
另一份质量标准写目标 **99.9%**，低于 **99%**可能被限制，并说明按集成工作流评估。
[Quality & Performance](https://developer.uber.com/docs/eats/quality-and-performance)。
不能把 98% 当成长期 SLA，也不能把本地 Mirror 成功率冒充官方 Accepted/Submitted 比率。

**项目建议，需 Uber 确认：** 首店至少覆盖上述观察期及一个繁忙时段，单独记录
“人工已接 Uber 订单 → 一张正确厨房镜像”的成功率、延迟、缺单、重复厨房任务/自动打印、RAW 警告、
取消/改单处置和现场出纸。以零重复/零跨店为硬门槛，以 ≥99.9% 作为内部镜像目标；低订单量时
同时列 numerator/denominator，不能仅给百分比。标准不足或 sample 太小就延长观察，不自动开第二店。

异常时先使用既有 Uber Pad 工作流保障接单，按获准应急方案暂停本系统镜像消费，保留事件和审计；
不自动切 Uber manager、不回放可能已出纸的任务、不清表、不修改付款事实。Production 暂停、回滚、
恢复仍须遵循已批准批次和 [Rollback runbook](../../deployment/cloud/README_ROLLBACK.md)。

## 9. Owner 简单 Checklist

状态只使用：`NOT_STARTED` 未执行；`READY` 资料/设计可供下一步；`PASS` 有该环境实际证据；
`BLOCKED` 缺明确前提。未知 Uber 账号审批状态不得写成已批准。

| 项目 | 当前状态 | 放行所需事实 |
| --- | --- | --- |
| UBER_VERIFICATION | BLOCKED | Uber 确认 Mirror 流程并完成正式 verification；本轮未查询/提交审批 |
| PRODUCTION_APP | NOT_STARTED | 独立 Production App 身份和批准记录经核实 |
| PRODUCTION_SCOPES | BLOCKED | observer scope 方案确认、whitelist、实际 granted scopes/API 权限 |
| PRODUCTION_WEBHOOK | BLOCKED | Production hostname/TLS/backend capability 和真实签名验收；当前只有 Staging HTTPS |
| ST_DENIS_PROVISIONED | NOT_STARTED | Uber 正式 observer 关联与只读验证；本轮没有连接 Production Uber |
| ST_DENIS_STORE_MAPPING | BLOCKED | 目标发布、capability/安全绑定方式准备；UUID 指向 Production local Store 1/org1 |
| ST_DENIS_MENU_MAPPING | BLOCKED | 该店真实 Production menu stable IDs 的独立 diff 和审核 |
| ST_DENIS_PAD_DIRECT | NOT_STARTED | 已有本地 PAD_DIRECT 不代替真实 Uber 厨房纸单验收 |
| ST_DENIS_PILOT | BLOCKED | 前置项 PASS、Owner 发布/运营窗口授权、Uber 确认指标 |
| ST_CATHERINE_PROVISIONED | BLOCKED | 真实 Uber UUID、首店观察通过、Uber observer 关联 |
| ST_CATHERINE_STORE_MAPPING | BLOCKED | Production local Store 3/org1 的独立 binding |
| ST_CATHERINE_MENU_MAPPING | BLOCKED | 独立 Production IDs 和语义审核；不复制 Test/St-Denis UUID |
| ST_CATHERINE_PAD_DIRECT | BLOCKED | 当前 printing DISABLED；需获准硬件配置及 Uber 现场打印验收 |
| ST_CATHERINE_LAUNCH | BLOCKED | 首店 gate 通过和第二批 Owner 明确批准 |
| CHINATOWN_UBER_DISABLED | PASS | 本轮 V28 缺 Uber capability，确未启用；未来 capability 默认 false/no binding 必须重新验收 |

**PRODUCTION_MUTATION_THIS_ROUND = NONE。PRODUCTION_CONNECTED = NO（本系统）。**
本文 READY 的是上线设计，不是 Production readiness。真正待补的业务资料是 St-Catherine Uber UUID；
随后需要 Uber observer 流程确认、正式域名选择和 Owner 对具体 Production 发布批次的授权。
