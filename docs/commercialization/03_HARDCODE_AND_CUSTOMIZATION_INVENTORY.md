# 03 — Hardcode and Customization Inventory

审计日期：2026-10-15（America/Toronto）。范围：READ-ONLY AUDIT + DOCUMENTATION + QUESTIONS。

源码基准：后端 **B = `ce35278b3569a4eacfa29efc01cadbf0c409bec6`**；线上前端 **F = `8c3a9e356f59cd025beff25f7fb1d5a20594e028`**；远端 main **M = `06bac51ddc0a829a9aa1106f07ef886572efbb65`**（本轮 `git ls-remote` 确认，未 fetch 或更新引用）。本轮证明 B/F 的 `frontend/`、`restaurant-pad-app/` 无 Git 内容差异，因此 B 中这两部分也可用于语义审查。源码链接固定到提交，不能用当前旧工作区的同名文件替代。

证据标签：`VERIFIED_IN_CODE` 表示读到固定提交中的实现；`VERIFIED_IN_PRODUCTION` 仅表示本轮明确读取的运行事实；`DOCUMENTED_ONLY` 表示仅有文档陈述；`PLANNED_ONLY` 表示计划/契约尚未证明完成；`UNKNOWN` 表示证据不足。代码被部署不等于所有行为均经过线上验证。风险优先级 P0=独立商家共享部署阻断，P1=上线前关键，P2=按首批范围处理，P3=维护改善；不代表要求立即修改现有 Production。


## 方法与范围

全局扫描 B/F 中 Backend、Frontend、Android、migration/seed、test、deployment/config 共 **1266 个文本文件**：backend 830、frontend 247、restaurant-pad-app 41、deployment 148。包括 SQL、YAML、Java/TS、JSON、shell/Python、Gradle、Dockerfile、Nginx templates、`.env.example`；排除 binary、generated bundled/minified assets、依赖/target/build、private 文件和实际 `.env`/密钥。生成 assets 不是独立业务源码，artifact/version 另见 01；没有将秘密内容放入结果。

除搜索外，逐段跟踪 order snapshot → option/canonical resolver → Combo task → BOM → display rule/eligibility → renderer → dispatch/Pad，以及 onboarding profile/master 和离线环境切换。条目按**同一业务规则族**合并多个命中，53 项不是字面常量总数。配置已存在的能力明确写出，普通协议默认和通用幂等不被当成删除目标。附录仅为全部词法候选索引，不能据匹配直接判定安全漏洞/死代码。

所有迁移建议均为分析，未实施。尤其不能改已经应用的 Flyway checksum、删除业务分支、重写历史订单/print payload、清 outbox，或把真实库/用户/打印设备导入商业模板。

## 分类统计

| 分类 | 规则族数 |
| --- | --- |
| A. GENERIC_CORE | 2 |
| B. CONFIGURABLE | 12 |
| C. OPTIONAL_MODULE | 5 |
| D. RESTAURANT_PROFILE | 18 |
| E. LEGACY_OR_DEAD_CODE | 1 |
| F. SECURITY_OR_ISOLATION_RISK | 11 |
| G. NEEDS_OWNER_DECISION | 4 |

## 模块与优先级统计

| 主模块 | P0 | P1 | P2 | P3 | 合计 |
| --- | --- | --- | --- | --- | --- |
| Analytics | 0 | 1 | 1 | 0 | 2 |
| Android | 0 | 2 | 0 | 0 | 2 |
| Auth | 0 | 2 | 0 | 0 | 2 |
| Brand | 0 | 0 | 1 | 0 | 1 |
| Core | 0 | 0 | 1 | 0 | 1 |
| Database | 0 | 3 | 0 | 0 | 3 |
| Deployment | 0 | 2 | 0 | 0 | 2 |
| Frontend | 0 | 2 | 3 | 1 | 6 |
| Integration | 0 | 2 | 0 | 0 | 2 |
| Inventory | 0 | 2 | 1 | 0 | 3 |
| Kitchen | 0 | 0 | 1 | 0 | 1 |
| Menu | 0 | 1 | 3 | 0 | 4 |
| Modules | 0 | 1 | 0 | 0 | 1 |
| Offline | 0 | 1 | 0 | 0 | 1 |
| Operations | 0 | 1 | 0 | 0 | 1 |
| Order | 0 | 3 | 0 | 0 | 3 |
| Owner | 0 | 3 | 2 | 0 | 5 |
| Pricing | 0 | 1 | 0 | 0 | 1 |
| Printing | 0 | 4 | 2 | 0 | 6 |
| Product | 0 | 0 | 1 | 0 | 1 |
| Realtime | 1 | 0 | 1 | 0 | 2 |
| Security | 1 | 0 | 0 | 0 | 1 |
| Seed | 0 | 1 | 0 | 0 | 1 |
| Tests | 0 | 0 | 1 | 0 | 1 |

P0 为独立商家共享服务的前置安全问题；不代表已证实当前餐厅遭受攻击。Q1–Q20 对应 [06](06_OWNER_DECISION_QUESTIONS.md)。

## 已语义审查的清单

### HC-001 — 事务、快照、幂等和持久 dispatch outbox

- 分类 / 风险 / 模块：**A. GENERIC_CORE · P2 · Core / Order**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java:71](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java#L71)。
- 触发条件：POST Store-scoped idempotent submission；重试相同 key。
- API / UI / 打印 / 任务调用链：Controller → submission/hash/lock → OrderService → outbox。
- 其他餐厅影响：所有餐厅都依赖的可靠性机制；本身不是应删除硬编码。
- 现有配置能力：请求 ledger、Store scope、sourceKey 已实现。
- 依赖：HC-006/007/033。
- 建议处理：保留通用 Core，不以通用化为由绕过事务/快照。
- 推荐迁移：先固定旧 profile 行为基准，再以非拉面 fixtures 验证相同不变量。

### HC-002 — ADMIN 全商家访问及 Owner 全 capability

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P0 · Security**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java:44](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java#L44)。
- 触发条件：全局角色为 ADMIN 时直接允许任意 storeId。
- API / UI / 打印 / 任务调用链：REST 授权、workspace、平台后台、analytics。
- 其他餐厅影响：无关联商家共享进程时支持人员拥有全数据路径；未做恶意请求复现。
- 现有配置能力：Organization/Store memberships 已有，但 ADMIN bypass 明确存在。
- 依赖：Q3/Q4；HC-004/005。
- 建议处理：设计 Platform Operator 与 Restaurant Owner 分离、最小支持访问及审计。
- 推荐迁移：独立授权新角色与负向矩阵；不要直接撤销现有餐厅账号权限。

### HC-003 — WebSocket wildcard origin 与缺失订阅 scope 检查

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P0 · Realtime / Security**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/common/config/WebSocketConfig.java:23](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/WebSocketConfig.java#L23)；[B:backend/src/main/java/com/restaurant/system/common/realtime/StompRealtimeEventPublisher.java:25](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/realtime/StompRealtimeEventPublisher.java#L25)。
- 触发条件：客户端连接 /ws 后订阅 /topic/stores/<id>/…。
- API / UI / 打印 / 任务调用链：AuthTokenFilter 无 Bearer 会继续 filter chain；STOMP simple broker。
- 其他餐厅影响：可预测 topic 不是授权；代码搜索无 inbound ChannelInterceptor/Handshake membership guard；未连接真实频道。
- 现有配置能力：REST StoreAccess 已存在但没有接到 SUBSCRIBE/CONNECT。
- 依赖：HC-002/033。
- 建议处理：共享多商家前验证并封闭 CONNECT/SUBSCRIBE 授权、来源及 token 生命周期。
- 推荐迁移：先在 synthetic foreign-org 测试读不到事件，再兼容既有 Pad/Web reconnect；本轮不订阅线上订单。

### HC-004 — 固定角色矩阵和 FRONTDESK 管理权限

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P1 · Auth / Staff**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java:20](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java#L20)。
- 触发条件：FRONTDESK/OWNER/MANAGER/NOODLE_VIEW 等角色解析。
- API / UI / 打印 / 任务调用链：AuthorizationService、Staff UI、App routes。
- 其他餐厅影响：其他商家可能不希望前台管理菜单或打印；全局 role 与每店 role 的组合需验证。
- 现有配置能力：有 membership.roleCode、capability registry；没有通用商家编辑矩阵。
- 依赖：HC-002；Q4。
- 建议处理：先明确最小预置角色及每店生效规则，避免第一版任意 ACL 引擎。
- 推荐迁移：版本化权限 preset；现餐厅沿用原矩阵并增加跨角色负向测试。

### HC-005 — legacy default Store fallback / 开发 header

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Auth / Configuration**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java:58](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java#L58)；[B:backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java:27](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java#L27)。
- 触发条件：没有 active membership 时回退 users.store_id；fallback profile 开启时接受 X-User-Id。
- API / UI / 打印 / 任务调用链：认证上下文、workspace 和所有 requireForStore。
- 其他餐厅影响：成员关系失效/导入遗漏可能恢复旧默认店访问；错误 profile 可变成身份冒用入口。
- 现有配置能力：cloud 源码默认 false，有 ProductionSafetyConfig；本轮未伪造 header。
- 依赖：HC-002/004/047。
- 建议处理：客户初始化必须显式 membership，错误环境 fail closed。
- 推荐迁移：先盘点 legacy 账号依赖，再经授权迁移；不能直接删除 fallback 破坏现店。

### HC-006 — 离线客户端价格/名称/station 快照授权

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Order / Pricing**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:994](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L994)；[B:backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java:183](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java#L183)。
- 触发条件：提交非 Add-on unit_price_snapshot、过期菜单或已缺失菜单行。
- API / UI / 打印 / 任务调用链：Idempotent submit → drift logging → draft snapshot → totals。
- 其他餐厅影响：兼容弱网的设计也允许提交者携带历史/自定义价格；新商家必须选择价格有效期与服务端验证；未证明利用。
- 现有配置能力：linked canonical Add-on 当前价有强制校验，其余 drift 多处仅记录。
- 依赖：HC-010/013/033；Q9。
- 建议处理：明确价格信任契约，区分合法缓存与任意篡改，不简单一律拒绝旧单。
- 推荐迁移：新增价格版本/有效期与冲突 UX 时必须保留旧 outbox key 和 payload，不自动改重试内容。

### HC-007 — 库存 read-modify-save 无共享库存锁

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Inventory**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:1634](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L1634)；[B:backend/src/main/java/com/restaurant/system/inventory/entity/InventoryItem.java:42](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/inventory/entity/InventoryItem.java#L42)。
- 触发条件：不同订单同时扣同一 inventory_item_id。
- API / UI / 打印 / 任务调用链：submit/update → deductInventory → findById → stockBefore/After → save + ledger。
- 其他餐厅影响：订单事务和 order lock 不锁共享库存；源码未见 InventoryItem @Version/悲观锁，可能 lost update；未做线上并发测试。
- 现有配置能力：当前有负库存检查和流水，无已验证库存并发策略。
- 依赖：HC-001/008；Q19。
- 建议处理：若商业版承诺库存正确，先补并发设计/真实 PostgreSQL 验证。
- 推荐迁移：synthetic 两订单竞争同库存与回滚测试，通过后逐步迁移；保留库存历史流水。

### HC-008 — BOM 引用库存未显式比较 Store

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Inventory / DB**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:1634](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L1634)；[B:backend/src/main/java/com/restaurant/system/menu/repository/MenuItemBomRepository.java:12](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/repository/MenuItemBomRepository.java#L12)。
- 触发条件：BOM 指向另一 Store 的 inventory ID。
- API / UI / 打印 / 任务调用链：订单 BOM/Option BOM 扣减。
- 其他餐厅影响：坏导入/旧数据可能跨店写库存；源码路径只按 inventory ID 查；未查询真实 BOM 内容。
- 现有配置能力：menu item 自身校验 Store；V28 Add-on composite FK 较完整，旧 BOM 无等价统一保证。
- 依赖：HC-007/038。
- 建议处理：沿 BOM、prep recipe 所有资源关系建立 Store invariant。
- 推荐迁移：先只读检测 synthetic/授权数据一致性，再 additive scoped FK/服务校验；禁止自动修真实 BOM。

### HC-009 — 库存补货/备菜 API 文档超前

- 分类 / 风险 / 模块：**G. NEEDS_OWNER_DECISION · P2 · Inventory / Prep**；状态 `UNKNOWN`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/inventory/controller/InventoryController.java:12](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/inventory/controller/InventoryController.java#L12)；[B:backend/src/main/java/com/restaurant/system/inventory/service/InventoryService.java:3](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/inventory/service/InventoryService.java#L3)。
- 触发条件：文档中的 items/restock/transactions/executePrep 需求。
- API / UI / 打印 / 任务调用链：API 文档、库存管理期待。
- 其他餐厅影响：只找到 health controller 和空 service，BOM deduction 不能代替完整补货/备菜产品。
- 现有配置能力：实体/repository 基础存在；没有假设未实现 API 可调用。
- 依赖：HC-007/008；Q19。
- 建议处理：Owner 决定首发是否承诺库存完整闭环。
- 推荐迁移：先写明确 API/验收再实施；本轮状态为缺失能力，不添加接口。

### HC-010 — 固定 14.975% 税率及标签

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P1 · Pricing / Receipt**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/common/pricing/TaxCalculator.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/pricing/TaxCalculator.java#L8)；[F:frontend/src/utils/tax.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/tax.ts#L1)。
- 触发条件：每次 total/Tax label 计算；部分 update receipt。
- API / UI / 打印 / 任务调用链：OrderService、前台 totals、FrontdeskReceiptRenderer。
- 其他餐厅影响：其他税区/免税商品/税包含价不适配；这里仅记录实现，不评估税法合规。
- 现有配置能力：Store pricing policies 只覆盖 size/combo，未见通用 tax policy。
- 依赖：HC-006/011/012；Q13。
- 建议处理：设计 Store tax policy、有效期/税组件快照与显示。
- 推荐迁移：Lan Zhou 保留原 policy/rounding；旧订单重印不可使用当前税率重算。

### HC-011 — 统一 Toronto 时区与固定日结调度

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P1 · Analytics / Time**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/analytics/service/AnalyticsAggregationScheduler.java:17](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/service/AnalyticsAggregationScheduler.java#L17)；[B:deployment/cloud/docker-compose.yml:9](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/docker-compose.yml#L9)。
- 触发条件：日汇总 cron、LocalDateTime.now、日结/营业日窗口。
- API / UI / 打印 / 任务调用链：Analytics aggregation、daily close、Uber inbox business day。
- 其他餐厅影响：跨时区、跨午夜营业会被错误归日；未验证各地 DST 与营业日。
- 现有配置能力：部署 TZ 可配；Uber businessTimeZone 是进程配置；无完整 per-Store 时区来源。
- 依赖：HC-010/044；Q13。
- 建议处理：Store business timezone/营业日边界与系统 UTC 存储分开。
- 推荐迁移：先保留现店日期解释；对 DST/过夜与旧 timestamp 制定转换策略，不能批量重写历史。

### HC-012 — CAD / en-CA 金额展示

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P1 · Frontend / Reports**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/features/reports/reportUtils.ts:13](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/reportUtils.ts#L13)。
- 触发条件：报表/Owner Dashboard 金额格式化。
- API / UI / 打印 / 任务调用链：Intl.NumberFormat 多处、frontdesk money labels。
- 其他餐厅影响：多币种/不同小数位不适配；同一符号可能混淆汇总。
- 现有配置能力：部分外部 snapshot 带 currency，但 UI 仍固定。
- 依赖：HC-010/011；Q13。
- 建议处理：首发明确单币种边界；币种取 Store/Order snapshot。
- 推荐迁移：不同币种不能直接相加；现店 CAD 保持，新增币种前补端到端金额测试。

### HC-013 — Canonical Add-on 目录已可配置

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P2 · Menu / Add-on**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java:14](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java#L14)；[B:backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql:3](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql#L3)。
- 触发条件：菜单读取、item eligibility、新订单选项和菜单复制。
- API / UI / 打印 / 任务调用链：Organization definition → StoreAddon → per-item assignment → frozen order。
- 其他餐厅影响：这是已完成的配置基础；不能把所有 Add-on 价格误报成硬编码。
- 现有配置能力：已支持 Store 名称/价格/active 和每菜 eligibility，resolver 拒绝坏引用。
- 依赖：HC-006/014；Q6。
- 建议处理：复用唯一当前权威，历史订单/print snapshot 保持独立。
- 推荐迁移：新商家只初始化自己的定义/目录；不导入现餐厅 15 项当前值；沿用 scope composite FK。

### HC-014 — Option group 与 REMOVE/SOUP/NOODLE UI 类型固定

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P2 · Menu / Frontend**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/features/owner-admin/MenuOptionsPanel.tsx:19](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuOptionsPanel.tsx#L19)；[B:backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java:34](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java#L34)。
- 触发条件：Owner 添加选项或 POS 判断 option group。
- API / UI / 打印 / 任务调用链：MenuOptionsPanel → menu metadata → customization modal → submit/print。
- 其他餐厅影响：一般 modifier min/max、多选、quantity/free allowance 不由现有有限组完整表示。
- 现有配置能力：部分动态 combo group 已有，传统 noodle/remove groups 仍固定。
- 依赖：HC-016/019；Q5。
- 建议处理：先确认首批 modifier 能力，保留这些组为 Lan Zhou preset。
- 推荐迁移：建立稳定 group/option identity 后转换菜单；保留旧快照字段和客户端兼容。

### HC-015 — 标准 Small/Regular/Large 与统一 combo 差价

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P2 · Menu / Pricing**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/menu/pricing/StandardSize.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/pricing/StandardSize.java#L8)；[B:backend/src/main/java/com/restaurant/system/menu/pricing/StorePricingPolicy.java:26](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/pricing/StorePricingPolicy.java#L26)。
- 触发条件：Store pricing policy apply/preview/catalog projection。
- API / UI / 打印 / 任务调用链：PricingRulesPanel → policy service → size/combo options。
- 其他餐厅影响：可改金额但价格模型仍三种标准 size + Store combo delta，不能等同任意 variant pricing。
- 现有配置能力：Store policies、preview/impact 和 per-item supports flags 已实现。
- 依赖：HC-006/016；Q8。
- 建议处理：复用现 policy，按需求评估 item variants/override。
- 推荐迁移：原门店 profile 保留三 size 和现 delta；新模式需单独版本且不重估旧单。

### HC-016 — 五个内置套餐蛋/小菜定义

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Menu / Combo**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java:10](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java#L10)。
- 触发条件：初始化默认 components；兼容 COMBO_EGG/COMBO_SIDE。
- API / UI / 打印 / 任务调用链：Combo config、menu catalog、POS 和打印。
- 其他餐厅影响：默认包含茶叶蛋/煎蛋/毛豆/土豆丝/黄瓜；不适合任意餐厅默认。
- 现有配置能力：动态 groups/components、LINKED_MENU_ITEM 等已存在，不应删除。
- 依赖：HC-017/018/028；Q7。
- 建议处理：将预置项明确归入 Lan Zhou profile，泛化 core 读取 schema。
- 推荐迁移：先证明动态任意组与旧五项行为相同；空白商家不得自动收到这五项。

### HC-017 — Combo Side 根据 code/name 映射三种 SKU

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Order / Kitchen**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2429](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2429)。
- 触发条件：选中 legacy side、parent remove 或生成 combo side tasks。
- API / UI / 打印 / 任务调用链：buildComboSideRemoveParentMap → menu lookup → task generation。
- 其他餐厅影响：中文名称和 code fragments 间接控制任务/关联菜品；相同 SKU 可能触发错路由。
- 现有配置能力：新 LINKED_MENU_ITEM 有配置，legacy branch 仍在执行。
- 依赖：HC-016/022/028。
- 建议处理：迁到显式 linked menu item / behavior，旧 profile 暂保兼容。
- 推荐迁移：每个 legacy side 建映射、验证 remove/BOM/纸张；未验收前不能删名字分支。

### HC-018 — Frontend legacy synthetic option IDs 与 hash fallback

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Frontend / Combo**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/hooks/useMenuCatalog.ts:20](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.ts#L20)。
- 触发条件：将 Store combo component 投影为 option ID。
- API / UI / 打印 / 任务调用链：useMenuCatalog → selection → frozen payload → backend component matching。
- 其他餐厅影响：五项固定负 ID；其他组使用有限 hash 范围，存在碰撞可能；未证明发生碰撞。
- 现有配置能力：动态 group、component_code 与 generic hash 已支持，但 identity 未统一为 durable ID。
- 依赖：HC-016/017；Q7。
- 建议处理：稳定 component identity 与 offline payload contract 协同设计。
- 推荐迁移：兼容已排队负 ID payload；先 collision/roundtrip 测试，不重新编号旧 outbox。

### HC-019 — POS 面型显示、noodle category 与 egg/side 兜底 UI

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P2 · Frontend / Ordering**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/features/ordering/components/ItemCustomizationModal.tsx:16](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.tsx#L16)；[F:frontend/src/utils/comboSelection.ts:8](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/comboSelection.ts#L8)。
- 触发条件：打开 customization、恢复 draft 或 fallback combo schema。
- API / UI / 打印 / 任务调用链：OrderingPage / modal / useDraftOrder / order sessions。
- 其他餐厅影响：前台仍有面类/鸡蛋语义；不能声称任意 option schema 自动生成 UI。
- 现有配置能力：已优先消费 dynamic combo groups，兼容字段保留。
- 依赖：HC-014/018/032；Q5/Q7。
- 建议处理：用 item capabilities 决定控件，面型作为可选 preset。
- 推荐迁移：双跑新旧 display model 并比较 payload/价格/默认值，保留 iPad 大按钮。

### HC-020 — OrderService 厨房名、面型、省略规则

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Order / Kitchen**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2057](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2057)；[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2182](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2182)。
- 触发条件：订单提交冻结 kitchen task name/instructions。
- API / UI / 打印 / 任务调用链：buildKitchenPrimaryLine → size/item/noodle/spicy/soup code。
- 其他餐厅影响：SKU→红/酸/牛炒、三细/韭叶默认省略等绑定兰州拉面；并非纯展示常量。
- 现有配置能力：PrintingDisplayRuleContext 可覆盖部分，但 fallback/omit 有代码 OR 条件。
- 依赖：HC-021/024/028。
- 建议处理：完整提取成版本化 profile display behavior，Core neutral fallback。
- 推荐迁移：逐 SKU + option 比较冻结 task snapshot/GRAB/HOT；不能只搬 JSON 就断言无 hardcode。

### HC-021 — 全局默认 PrintingDisplayRuleDefaults 实为餐厅字典

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Printing / Rules**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java#L8)。
- 触发条件：Store 没有有效 display-rule revision / 使用默认规则。
- API / UI / 打印 / 任务调用链：Rule service/context → order snapshot & renderers。
- 其他餐厅影响：没有选择 profile 的新商家也可能收到 noodle alias/omit；默认 JSON含店铺菜单知识。
- 现有配置能力：draft/publish/revision/fingerprint、Store rule set 已有。
- 依赖：HC-020/024/040；Q11。
- 建议处理：通用默认只输出完整快照，Lan Zhou 字典用显式 profile。
- 推荐迁移：保存现 published revision 与 fingerprint，对新/旧无规则 Store 分别迁移验证。

### HC-022 — HOT 打印由固定 WOK/DEEPFRIED/category/煎蛋决定

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Printing / Kitchen**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java:106](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java#L106)。
- 触发条件：HOT_KITCHEN printable content/route check。
- API / UI / 打印 / 任务调用链：OrderService → dispatcher → eligibility → HotKitchenReceiptRenderer。
- 其他餐厅影响：其他厨房自定义 station code 不自动获得正确 HOT 路由；煎蛋触发为餐厅规则。
- 现有配置能力：menu station、printer assignment 可配；eligibility 仍专用。
- 依赖：HC-017/020/023；Q10。
- 建议处理：将 kitchen role/route predicate 归 Store routing profile。
- 推荐迁移：对同单 WOK/deepfried/egg/side 混合建立纸张基准，保留现店出纸范围。

### HC-023 — GRAB station 白名单与菜类排序

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Printing / GRAB**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java:26](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java#L26)。
- 触发条件：生成 GRAB ticket 时 filter task 和分组排序。
- API / UI / 打印 / 任务调用链：task snapshots → GRAB renderer → Pad。
- 其他餐厅影响：任意新工位可能被过滤；GRAB 是厨房组装单名称，并非第三方 Grab integration。
- 现有配置能力：已配置输出规则/字体/份数，但 station filter/sort 不完全可配置。
- 依赖：HC-020/022；Q10/Q11。
- 建议处理：Store kitchen print roles + routing/sort preset。
- 推荐迁移：新工位先 synthetic 正向测试，现店 cold/noodle/wok/raw fallback 不得漏打。

### HC-024 — Receipt 按名字/size 文本识别、隐藏 egg/side

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Printing / Receipt**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java:215](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L215)；[B:backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java:47](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java#L47)。
- 触发条件：frontdesk item line / combo children / noodle quantity formatting。
- API / UI / 打印 / 任务调用链：manual reprint、frontdesk auto print、GRAB/HOT shared formatter。
- 其他餐厅影响：传统牛肉面替名、大小中文解析、面/noodle 名称判断会误识其他菜系。
- 现有配置能力：部分 aliases/dictionaries 已可配，文本 fallback 仍在。
- 依赖：HC-020/021/025；Q11。
- 建议处理：改为稳定 semantic metadata + neutral renderer；版式保持 profile。
- 推荐迁移：历史 snapshots + existing 42 GRAB fixtures 基准；再加非面食/同名菜反例。

### HC-025 — 中英夹杂小票标题/税标签/固定排版

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P2 · Printing / Locale**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java:118](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L118)；[B:backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java:31](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java#L31)。
- 触发条件：render subtotal/tax/notes/size/更新时间。
- API / UI / 打印 / 任务调用链：三种 renderer 与 printer transport。
- 其他餐厅影响：目前是固定业务版式，不能满足任意商家抬头、语言、税组件和信息密度。
- 现有配置能力：已有 paper width/font/copies/display dictionary，未见完整通用品牌抬头配置。
- 依赖：HC-010/021/026；Q11/Q14。
- 建议处理：先有限 receipt presets + Store brand fields，避免自由脚本模板。
- 推荐迁移：新模板版本冻结到 job；旧 print job 按原 payload 补打，不随新语言重排。

### HC-026 — ESC/POS 80mm/9100/GBK 等默认

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P2 · Printing / Hardware**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImpl.java:89](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImpl.java#L89)；[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:90](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L90)。
- 触发条件：新 printer 配置缺省值或 native connection test。
- API / UI / 打印 / 任务调用链：PrinterConfig → payload → ESC/POS TCP、test bridge。
- 其他餐厅影响：默认协议不等于所有硬件通用；USB/Bluetooth/iPad 直连能力未证实。
- 现有配置能力：IP/port/encoding/timeout/font/width 可 Store-config，native test GBK default。
- 依赖：HC-034/035；Q16。
- 建议处理：认证有限型号矩阵，保留协议默认作为配置默认而非店铺专属逻辑。
- 推荐迁移：每型号检查字库/切纸/宽度/重试不确定性；禁止复制真实 LAN endpoint。

### HC-027 — KDS 固定 noodle/hot/pass/shelf 语义

- 分类 / 风险 / 模块：**C. OPTIONAL_MODULE · P2 · Kitchen / KDS**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java:7](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java#L7)；[F:frontend/src/App.tsx:43](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/App.tsx#L43)。
- 触发条件：启用 KDS feature/module 访问专用页面。
- API / UI / 打印 / 任务调用链：KdsServiceImpl、kitchen controllers、App routes、realtime topic。
- 其他餐厅影响：这是一套可复用可选模块，但屏幕/role 与兰州流程紧耦合。
- 现有配置能力：全局 feature + Store modules + display config 已有。
- 依赖：HC-022/023/031；Q10。
- 建议处理：打印与 KDS 为可组合能力，专门屏幕作为 profile preset。
- 推荐迁移：保留现店 printer mode，先证明新店屏幕订阅与 Store scope；不强制启用 KDS。

### HC-028 — Chinatown clone 硬定 source Store 1

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Owner / Clone**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java:31](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java#L31)。
- 触发条件：选择 CHINATOWN clone profile。
- API / UI / 打印 / 任务调用链：profile registry → source clone validation/transaction/composer。
- 其他餐厅影响：适用于既有同品牌第二店，不能跨无关联商家作为 universal menu source。
- 现有配置能力：有 exact profile code/validation/fingerprint 和新 Profile/Master materializer。
- 依赖：HC-016/037/039；Q18/Q20。
- 建议处理：保留既有专属 clone，商业默认与真实 Store ID 脱钩。
- 推荐迁移：先建立合法 versioned profile artifact；迁移时不读取或复制线上订单/用户/设备。

### HC-029 — 新店默认 ST_DENIS profile v2

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Owner / Onboarding**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java:33](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java#L33)。
- 触发条件：Owner 创建 Store 未显式提供 profile/version。
- API / UI / 打印 / 任务调用链：business create → resolve → master/profile → materializer。
- 其他餐厅影响：其他商家会继承兰州默认菜单/打印规则，当前流程是连锁扩店基础。
- 现有配置能力：可显式选择已发布 profile/master；尚无已证明通用 blank org/store wizard。
- 依赖：HC-028/037/039；Q18。
- 建议处理：区分同组织扩店与无关联商家 onboarding。
- 推荐迁移：新增中性 blank/preset 前做 no-Lan-Zhou-data 验收；现店默认不在本轮修改。

### HC-030 — 默认两张桌与两个打印角色

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P2 · Owner / Provisioning**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java:45](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java#L45)。
- 触发条件：新 business Store materialization。
- API / UI / 打印 / 任务调用链：Owner membership → T01/T02 → GRAB/FRONTDESK logical roles → DISABLED printing。
- 其他餐厅影响：两桌是 product default 而非真实餐厅数据；快餐/无堂食店可能不需要。
- 现有配置能力：创建后可管理 tables/printing，optional hardware 不阻塞 Store。
- 依赖：HC-029/031；Q12/Q18。
- 建议处理：允许 Store service-mode preset 选择合理默认，保留一步创建。
- 推荐迁移：先测试 blank/no-table/counter preset；不要把内部 readiness 按钮逐个暴露给用户。

### HC-031 — 前台拆桌只认识 A/B 与左/右

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P2 · Frontend / Tables**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/types/dinein.ts:5](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/types/dinein.ts#L5)；[F:frontend/src/utils/tableDisplay.ts:7](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/tableDisplay.ts#L7)。
- 触发条件：split_supported table 或 table_no 以 -A/-B 区分。
- API / UI / 打印 / 任务调用链：useTableBoard → seat entries → Order table_no → receipt labels。
- 其他餐厅影响：不是通用任意座位/拼桌/分账；其他店桌号可能含同类后缀。
- 现有配置能力：桌台数据驱动；capacity/supports_split 可配，split 语义固定。
- 依赖：HC-030；Q12。
- 建议处理：明确首发只支持哪些拆桌模式，避免误承诺 split bill。
- 推荐迁移：保留旧后缀并为新模型版本化 table context，不能重命名当前活跃订单桌号。

### HC-032 — 中文/英文双字段与固定 UI 文案

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P2 · Frontend / Locale**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/types/ordering.ts:181](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/types/ordering.ts#L181)；[F:frontend/src/features/reports/components/ChannelSalesPanels.tsx:6](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.tsx#L6)。
- 触发条件：菜单输入、locale 切换、厨房和报表显示。
- API / UI / 打印 / 任务调用链：name_zh/name_en snapshots、App UI labels、native panel。
- 其他餐厅影响：第三语言不是简单开关；未找到所有 UI 统一 locale catalog。
- 现有配置能力：菜单/订单双语 snapshot 已有；部分业务标签内置。
- 依赖：HC-024/025/048；Q14。
- 建议处理：先约定语言矩阵与 fallback，品牌和语言分开配置。
- 推荐迁移：保持双字段 API 兼容；新增语言资源/快照不能按当前菜单回填历史显示。

### HC-033 — 离线 key 包含 account/org/store 但无 deployment identity

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Offline / Environment**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/offline/offlineDatabase.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/offlineDatabase.ts#L1)；[F:frontend/src/offline/orderOutbox.ts:58](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/orderOutbox.ts#L58)。
- 触发条件：同一 bundled WebView origin 切换后端，两个部署重用相同数字 IDs。
- API / UI / 打印 / 任务调用链：menu cache/localDraft/outbox/workspace → retry → configured API URL。
- 其他餐厅影响：缓存/凭据/outbox 可能跨环境混用；未做实机碰撞复现；浏览器不同 origin 自身隔离不能覆盖 bundled URL switch。
- 现有配置能力：已有 account/org/store guards、frozenPayload/hash/retry，缺 backend namespace。
- 依赖：HC-005/006/034；Q9/Q15。
- 建议处理：加入稳定 environment identity 与切服安全流程，保存待提交队列。
- 推荐迁移：版本化 DB key migration；明确 pending queue 不自动丢弃或发送至新服务器。

### HC-034 — Android SharedPreferences 保存 device token 与可编辑 URL

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Android / Security**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:3676](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L3676)；[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:416](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L416)。
- 触发条件：设备配对、保存 target、native Worker 发 X-Device-Token。
- API / UI / 打印 / 任务调用链：PadDeviceBridge → preferences → openDeviceConnection。
- 其他餐厅影响：代码注释仍称 local pilot storage；切服与 token/Store 生命周期需明确；未读取真实 token。
- 现有配置能力：runtime URL 已可配，不能误报 hardcoded production URL；未验证设备加密/MDM策略。
- 依赖：HC-033/035；Q15/Q16。
- 建议处理：评估 Keystore-backed storage/受控切服和 trusted origin bridge。
- 推荐迁移：不自动清配对；先做假凭据测试和恢复验证，再经批准迁移已装 Pad。

### HC-035 — Android package/版本分发与 native bridge

- 分类 / 风险 / 模块：**C. OPTIONAL_MODULE · P1 · Android / Release**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:restaurant-pad-app/android/app/build.gradle:10](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/build.gradle#L10)；[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:200](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L200)。
- 触发条件：安装 APK、加载 bundled 或 remote WebView、升级。
- API / UI / 打印 / 任务调用链：Gradle → asset sync → WebView → device/printing bridges。
- 其他餐厅影响：一个应用包是可复用模块；每商家白标会增加签名/更新/混装成本；bridge 来源信任需专门检查。
- 现有配置能力：runtime API/Web URL 可改；有 assets manifest/sync/verify 工具。
- 依赖：HC-033/034/049；Q15。
- 建议处理：优先统一可配置 App 候选，分别记录 APK+frontend+backend contract。
- 推荐迁移：定义签名/版本/rollback/asset兼容矩阵；本轮不重签/安装/改设备目标。

### HC-036 — WebSocket topic 名称含固定工作流

- 分类 / 风险 / 模块：**C. OPTIONAL_MODULE · P2 · Realtime / Screens**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java#L8)。
- 触发条件：订单/厨房/饮料事件广播。
- API / UI / 打印 / 任务调用链：Stomp publisher → 固定 topic suffix → hooks refresh。
- 其他餐厅影响：事件框架通用，topic 粒度是当前工作流；多 worker/实例 broker 仍为进程 simple broker。
- 现有配置能力：Store ID 分 topic、有 afterCommit；未见分布式 broker。
- 依赖：HC-003/027；Q2/Q10。
- 建议处理：保留事件契约，按模块扩展而非按菜系复制事件总线。
- 推荐迁移：先确保 tenant ACL；多实例方案由部署决策触发，不能提前重写 broker。

### HC-037 — Flyway V5 全库 SKU 修正

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Database / Data migration**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/resources/db/migration/V5__set_cold_chicken_noodle_default_type.sql:19](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V5__set_cold_chicken_noodle_default_type.sql#L19)。
- 触发条件：在有相同 SKU 的数据库应用 V5。
- API / UI / 打印 / 任务调用链：Flyway → option sort/default → stores.menu_revision。
- 其他餐厅影响：没有 Organization/profile predicate，同名 SKU 的其他客户可能被改默认；历史已应用迁移不可改 checksum。
- 现有配置能力：无开关；是既有 migration，不是运行时可编辑配置。
- 依赖：HC-020/039；Q18。
- 建议处理：区分 schema 与餐厅数据导入，未来 customer bootstrap 不无意套用专属修正。
- 推荐迁移：保留已应用 V5；设计新安装 baseline/条件化后续数据包须单独计划，不改现库 history。

### HC-038 — DB 租户约束保护不均匀、无 RLS

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Database / Isolation**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/resources/db/migration/V1__baseline_current_schema.sql:181](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V1__baseline_current_schema.sql#L181)；[B:backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql:37](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql#L37)。
- 触发条件：任意 repository/JDBC 查询或错误关系写入。
- API / UI / 打印 / 任务调用链：JPA/JDBC/MyBatis → public tables；服务依赖显式 scope。
- 其他餐厅影响：本轮 production catalog 79 tables / 0 RLS / 0 policies；不等于所有 API 已漏洞，但缺 DB fallback。
- 现有配置能力：V28 等新域有 composite FKs；旧域仅少量 FK；没有统一 ORM tenant enforcement。
- 依赖：HC-002/003/008；Q2/Q3。
- 建议处理：租户 threat model 决定 DB-per-org、schema 或 shared+强约束/RLS选项。
- 推荐迁移：逐表 scope/关系审计与 foreign-org负向测试；不能直接在营业库批量加 policy。

### HC-039 — V15/V19 自动 seed 兰州 Profile/Master

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Database / Profile**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql:7](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L7)；[B:backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql:1](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql#L1)。
- 触发条件：新空库运行完整 migration chain。
- API / UI / 打印 / 任务调用链：Profile catalog / Master Menu / provisioning defaults。
- 其他餐厅影响：不含真实订单凭据的模板仍有餐厅菜单、规则/结构；不是通用空白初始化。
- 现有配置能力：profile/version/artifact/fingerprint 已实现；数据 seed 与 schema 发布绑定。
- 依赖：HC-028/029/037；Q18/Q20。
- 建议处理：后续安装包区分 neutral baseline 与明确 opt-in restaurant profile。
- 推荐迁移：不编辑已应用 SQL；新库基线/pack 可复现测试；现 profile artifact 保留历史版本。

### HC-040 — Profile/Master 独立 materialization 可复用

- 分类 / 风险 / 模块：**A. GENERIC_CORE · P2 · Owner / Profile**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java:53](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java#L53)。
- 触发条件：合法 Owner+组织 scope、Profile/Master/fingerprint provision。
- API / UI / 打印 / 任务调用链：reservation → independent Store menu/options/pricing/modules/printing。
- 其他餐厅影响：这是目标架构的重要既有基础，不能误报仍是完整 live DB clone。
- 现有配置能力：支持 version/fingerprint/idempotent ledger，business minimal baseline。
- 依赖：HC-029/039；Q18/Q20。
- 建议处理：复用现 artifact contract，补 blank/通用 preset 和负向验证。
- 推荐迁移：校验新店无共享 mutable graph、无真实 endpoints/credentials/order history；不要建第二套 provisioning framework。

### HC-041 — RuntimeDataSeeder 含现餐厅菜单和 LAN 默认

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Seed / Configuration**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java:178](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L178)；[B:backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java:1543](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L1543)。
- 触发条件：runtime seed + demo-data/default-users/membership flags 打开。
- API / UI / 打印 / 任务调用链：startup → stores/menu/options/template/tables/printers/auth seed。
- 其他餐厅影响：误用本地 profile 会导入餐厅假设或默认用户/打印 endpoint。
- 现有配置能力：cloud 默认 demo/default-user/membership false，guard 存在；不能误报 production 正在执行。
- 依赖：HC-005/039/047；Q18。
- 建议处理：中性 schema metadata 与 demo/profile pack 分离并 fail closed。
- 推荐迁移：为客户配置做 isolated empty DB bootstrap 测试；绝不复制线上账号或现场打印IP。

### HC-042 — Analytics 固定 noodle SKU/category 分组

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P2 · Analytics / Reports**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java:21](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java#L21)；[F:frontend/src/features/reports/components/ChannelSalesPanels.tsx:6](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.tsx#L6)。
- 触发条件：汇总菜类收入、noodle panel/filter、Owner Dashboard。
- API / UI / 打印 / 任务调用链：SalesReporting.group → ChannelSalesService → report panels。
- 其他餐厅影响：其他餐厅被归 OTHER 或错误匹配同 SKU；固定图表标签不通用。
- 现有配置能力：动态菜单 category 已有；reporting group 另有静态 map。
- 依赖：HC-011/012/044；Q19。
- 建议处理：采用配置的 reporting category mapping，Lan Zhou mapping 保留为 profile。
- 推荐迁移：固定已完成订单 frozen line 分摊与取消过滤；重建汇总须独立授权、比较总额守恒。

### HC-043 — Uber 全进程 credentials 与 Store mappings

- 分类 / 风险 / 模块：**C. OPTIONAL_MODULE · P1 · Integration / Uber**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java:19](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java#L19)；[B:backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsStoreMapping.java:9](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsStoreMapping.java#L9)。
- 触发条件：Webhook/OAuth/worker 开启及 Store module/mapping enabled。
- API / UI / 打印 / 任务调用链：HMAC inbox → external Store mapping → import/mirror/financial snapshot。
- 其他餐厅影响：多 Store mapping 不证明多个无关联商家可分别授权/轮换 credentials；current app secret 为进程级。
- 现有配置能力：Store/menu mapping、HMAC、dedup、module gate、raw fallback 已有。
- 依赖：HC-002/038/044；Q17。
- 建议处理：将第三方 connector 设可选，明确平台 app 与 merchant授权模型。
- 推荐迁移：新客户独立映射与secret边界；不把现 sandbox IDs/批准 mapping JSON 打进通用包；Prod Uber当前 false保持。

### HC-044 — Uber Kitchen Mirror、财务汇总与业务时区

- 分类 / 风险 / 模块：**C. OPTIONAL_MODULE · P1 · Integration / Analytics**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java:31](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java#L31)；[B:backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java:33](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java#L33)。
- 触发条件：外部 accepted/order import、channel sales 与 day-close。
- API / UI / 打印 / 任务调用链：Uber snapshot → kitchen-only local order → separate financial sales source。
- 其他餐厅影响：mirror 本地价格为零且独立财务 source，不能将外卖单当普通 POS 收入叠加；日界线仍进程级。
- 现有配置能力：normalizer/mapping/frozen financial snapshots、channel separation 已有。
- 依赖：HC-011/042/043；Q17。
- 建议处理：保留防双算语义，明确首发接受订单/厨房镜像/平台结算职责。
- 推荐迁移：synthetic 同单重复 webhook/accepted/mapping/取消与channel total守恒；不触发真实 Uber 接单。

### HC-045 — GRAB 不是 Grab 外卖集成

- 分类 / 风险 / 模块：**G. NEEDS_OWNER_DECISION · P2 · Product / Integration**；状态 `UNKNOWN`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/printing/PrintModuleCode.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/PrintModuleCode.java#L8)。
- 触发条件：需求中提及 Grab 或看到 GRAB 模块名。
- API / UI / 打印 / 任务调用链：厨房 dispatcher/renderer/module assignment。
- 其他餐厅影响：当前固定提交仅找到 Uber connector；误认为已具备 Grab 平台能力会低估开发成本。
- 现有配置能力：无已证实 Grab API、认证或 merchant mapping。
- 依赖：HC-023/043；Q17。
- 建议处理：Owner 明确 Grab 是否指第三方平台，按独立 connector 估算。
- 推荐迁移：若需要，先获得实际平台契约/许可再做设计；不假设缺失 API。

### HC-046 — Feature build 常量、全局 backend flags 与 Store modules

- 分类 / 风险 / 模块：**B. CONFIGURABLE · P1 · Modules / Frontend**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/features/feature-flags/featureConfig.ts:10](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/feature-flags/featureConfig.ts#L10)；[B:backend/src/main/java/com/restaurant/system/common/feature/FeatureFlagService.java:14](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/feature/FeatureFlagService.java#L14)。
- 触发条件：UI route/render 与 backend capability checks。
- API / UI / 打印 / 任务调用链：build featureConfig → StoreModuleAccessEvaluator → runtime dependency。
- 其他餐厅影响：同一 frontend build 固定 KDS false/PLATFORM false；多层开关可能出现可管理性与运行能力不一致。
- 现有配置能力：Store module catalog/enable/readiness 已实现，不能另起第二套 feature system。
- 依赖：HC-027/040；Q10/Q18。
- 建议处理：写清平台最高禁用→Store授权→硬件 readiness→UI可见性的优先级。
- 推荐迁移：用两个不同套餐商家同一 build 测试配置入口始终可达；optional hardware不变全局 blocker。

### HC-047 — 本地默认 schema update / seed / fallback 风险

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Deployment / Security**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/resources/application.yml:11](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/application.yml#L11)；[B:backend/src/main/resources/application-cloud.yml:9](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/application-cloud.yml#L9)。
- 触发条件：新客户配置遗漏 cloud profile/guard-required参数。
- API / UI / 打印 / 任务调用链：Spring profile merge → Hibernate/Flyway/seeder/auth。
- 其他餐厅影响：错误启动参数会执行本地开发行为；本轮 production cloud已确认，不声称正在错误运行。
- 现有配置能力：ProductionSafetyConfig、cloud default safeguards 已有。
- 依赖：HC-005/041/052；Q2/Q18。
- 建议处理：通用安装 fail closed，受检验的环境模板并与 secret 注入分离。
- 推荐迁移：isolated startup matrix 验证缺配置不启业务；禁改现 env/网络/凭据。

### HC-048 — 品牌、Logo、地址/电话配置完整性未知

- 分类 / 风险 / 模块：**G. NEEDS_OWNER_DECISION · P2 · Brand / UI**；状态 `UNKNOWN`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:restaurant-pad-app/android/app/src/main/AndroidManifest.xml:1](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/AndroidManifest.xml#L1)；[F:frontend/src/App.tsx:2](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/App.tsx#L2)。
- 触发条件：客户品牌化或白标交付。
- API / UI / 打印 / 任务调用链：App label/icons、Store names、receipt/preset、public assets。
- 其他餐厅影响：主代码搜索未建立统一 Store brand/address/contact/receipt header contract；不能据未找到声称不存在任何品牌文件。
- 现有配置能力：Store name 可动态，package固定；公开资产/所有品牌输入点尚无统一产品模型。
- 依赖：HC-025/032/035；Q14/Q15。
- 建议处理：明确平台品牌+餐厅品牌覆盖范围，单一 brand source。
- 推荐迁移：仅用人工提供/授权公开品牌素材；无真实客户/员工/凭据导入模板。

### HC-049 — 测试偏现有 noodle/打印 fixture，整体覆盖率未知

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P2 · Tests / Regression**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java:1](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java#L1)；[F:frontend/src/hooks/useMenuCatalog.test.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.test.ts#L1)。
- 触发条件：改变 defaults、SKU、combo IDs、printing/菜单结构。
- API / UI / 打印 / 任务调用链：Maven/Vitest/Android unit tests、conditional Postgres tests。
- 其他餐厅影响：保留现店回归很好，但 70 unit PASS 不证明 arbitrary restaurant、tenant隔离/物理打印。
- 现有配置能力：148 backend test/resource files、47 frontend test files、3 Android unit源；Postgres opt-in。
- 依赖：HC-001/003/007/018/033。
- 建议处理：补业务矩阵而不是为文档变化写实现镜像测试。
- 推荐迁移：未来 generic cafe/grill/无堂食/外卖 fixtures + adversarial cross-org/并发；保留 Lan Zhou golden snapshots。

### HC-050 — 疑似无调用 mock/menuImportSeed

- 分类 / 风险 / 模块：**E. LEGACY_OR_DEAD_CODE · P3 · Frontend / Legacy**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[F:frontend/src/data/menuImportSeed.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/data/menuImportSeed.ts#L1)；[F:frontend/src/data/mockOrdering.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/data/mockOrdering.ts#L1)。
- 触发条件：被手工 import 或未来开发 demo 使用时。
- API / UI / 打印 / 任务调用链：静态搜索未见其他 src 文件引用这两个 module path/export；未运行 bundle reachability proof。
- 其他餐厅影响：内含餐厅样例但不等于当前 POS runtime 在用；不得据 grep 直接删除。
- 现有配置能力：真实菜单 catalog hook 已是主路径；完整 dynamic/build引用 UNKNOWN。
- 依赖：HC-041/049。
- 建议处理：仅列 dead-code candidate，Owner确认/依赖验证后再清理。
- 推荐迁移：先做 import/bundle验证并保存 demo/profile 需要的数据；本轮零删除。

### HC-051 — one-off 部署与验收脚本固定 Store/Printer/endpoint

- 分类 / 风险 / 模块：**D. RESTAURANT_PROFILE · P1 · Deployment / Operations**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:deployment/cloud/final-channels-20261003/deploy-once.py:33](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/deploy-once.py#L33)。
- 触发条件：重用旧 one-off promotion/acceptance 脚本给新客户。
- API / UI / 打印 / 任务调用链：固定 SQL read guards / image/source paths / approved Uber mapping JSON。
- 其他餐厅影响：这些 guard 对原任务有安全价值，但不是可重复商家初始化器；新客户复用会误指目标。
- 现有配置能力：已有 exact-SHA/Compose/operation locks；脚本是环境/事件专用。
- 依赖：HC-028/043/047；Q2/Q18。
- 建议处理：保留历史证据，提取受约束参数化 runbook，代码与private state分开。
- 推荐迁移：新环境 dry-run 校验所有target；禁止照抄 Store1/真实LAN/外部merchant mapping与生产备份。

### HC-052 — 备份/恢复/监控未形成商家交付承诺

- 分类 / 风险 / 模块：**G. NEEDS_OWNER_DECISION · P1 · Operations / Data lifecycle**；状态 `UNKNOWN`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:deployment/cloud/backup-db.sh:41](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/backup-db.sh#L41)；[B:deployment/cloud/restore-db.sh:54](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/restore-db.sh#L54)。
- 触发条件：新商家故障、升级、客户请求数据导出/注销。
- API / UI / 打印 / 任务调用链：custom pg_dump、restore --clean、health-check、deployment evidence。
- 其他餐厅影响：有备份文件不等于可恢复；共享库单租户恢复/导出、RPO/RTO、retention、告警未完整证实。
- 现有配置能力：脚本和多个dump存在；本轮只看metadata，未restore/读取dump。
- 依赖：HC-038/051；Q2/Q19。
- 建议处理：明确服务责任与tenant-aware导出/恢复；恢复演练先用隔离合成数据。
- 推荐迁移：保留各版本migration和兼容旧image；不把全库restore作为线上补丁回滚；所有真实恢复另授权。

### HC-053 — 全局 Store Profile catalog 对所有 Owner 返回内容

- 分类 / 风险 / 模块：**F. SECURITY_OR_ISOLATION_RISK · P1 · Owner / Profile isolation**；状态 `VERIFIED_IN_CODE`，Production 行为验证未执行（除条目明确引用的 runtime 事实）。
- 文件与行号：[B:backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileController.java:30](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileController.java#L30)；[B:backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileCatalogService.java:64](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileCatalogService.java#L64)。
- 触发条件：任意 OWNER 读取 /api/v1/store-profiles 或指定版本。
- API / UI / 打印 / 任务调用链：Owner controller → global profile/version/artifact repository → content_json response。
- 其他餐厅影响：当前公共预置模板可以共享；若未来把客户私有菜单/配方/品牌放这里，则无组织 ownership 的读取模型不足。
- 现有配置能力：ChainMasterMenu 有 organization_id；StoreProfileEntity 没有，controller 仅检查 Owner。
- 依赖：HC-029/038/040；Q3/Q18。
- 建议处理：区分 public product preset 与 organization-private profile，明确可见性/所有权。
- 推荐迁移：先测试不同组织看不到私有配置；保留安全公共模板，不将整个 global catalog 简单封死。


## 全局搜索命中统计（可重叠）

| 主题 | 命中文件 | 命中行 |
| --- | --- | --- |
| brand | 80 | 375 |
| tenant | 28 | 80 |
| endpoint | 100 | 327 |
| menu_identity | 88 | 997 |
| combo | 95 | 600 |
| station | 212 | 2900 |
| tax_time | 36 | 80 |
| permission_flags | 216 | 1057 |
| names_logic | 16 | 186 |

检索主题覆盖品牌/地址域名、Store/Organization 固定 ID、endpoint/协议、SKU/Option/菜名、Combo、工位/GRAB、税/时区/币种、权限/feature、以名称控制行为。另以类/方法/调用方审查配置间接语义；不是只凭 grep 下结论。

## 附录：完整词法候选位置索引

以下是扫描命中的 file+line 清单，不额外宣称逐行都是 hardcode。测试/历史部署 guard 和已配置路径也会命中；正式处理应回到上面的规则族和业务触发条件。超长单行 JSON 的多个词只计一行。

| 搜索主题 | 固定提交文件 | 行号 |
| --- | --- | --- |
| brand | [backend/src/main/java/com/restaurant/system/owner/dto/OwnerStoreProvisioningCatalogResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/dto/OwnerStoreProvisioningCatalogResponse.java#L15) | 15, 17 |
| brand | [backend/src/main/java/com/restaurant/system/owner/master/ChainMasterMenuCatalogService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/master/ChainMasterMenuCatalogService.java#L10) | 10 |
| brand | [backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java#L23) | 23, 26, 28, 29, 30, 90, 91, 96, 97 |
| brand | [backend/src/main/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposer.java#L4) | 4, 5, 6, 7, 8, 30, 32, 34, 54, 59, 104, 105, 106, 108, 109, 114, 116, 123, 128, 149, 251, 256, 269, 270, 274, 275, 280, 289, 296 |
| brand | [backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileContractValidator.java#L253) | 253, 290 |
| brand | [backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java#L33) | 33 |
| brand | [backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuGuard.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuGuard.java#L3) | 3, 43 |
| brand | [backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactory.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactory.java#L3) | 3, 10, 15, 31, 32, 34, 37, 38, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 73 |
| brand | [backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestValidator.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestValidator.java#L3) | 3, 27, 30, 33, 265, 269, 290 |
| brand | [backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L1) | 1, 2, 7, 8, 9, 11, 27, 28, 123, 126, 202, 207, 6364, 7093 |
| brand | [backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql#L132) | 132 |
| brand | [backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql#L3) | 3, 17, 45, 46, 47, 49, 66, 149, 158, 249, 256, 288, 335, 378, 387, 412, 416, 442, 454, 462, 495, 527 |
| brand | [backend/src/main/resources/db/migration/V21__repair_phase_b_master_menu_fingerprint.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V21__repair_phase_b_master_menu_fingerprint.sql#L15) | 15, 25, 42, 64, 84, 97 |
| brand | [backend/src/main/resources/module/module-catalog.v1.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/module/module-catalog.v1.json#L102) | 102, 276, 317, 359, 503 |
| brand | [backend/src/test/java/com/restaurant/system/common/config/StagingForwardedOriginTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/config/StagingForwardedOriginTest.java#L39) | 39 |
| brand | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java#L1375) | 1375, 1390 |
| brand | [backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreMenuCloneControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreMenuCloneControllerTest.java#L138) | 138, 145 |
| brand | [backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreProvisioningControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreProvisioningControllerTest.java#L84) | 84, 86, 161, 163, 277, 279 |
| brand | [backend/src/test/java/com/restaurant/system/owner/master/PhaseBMasterMenuSeedMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/master/PhaseBMasterMenuSeedMigrationTest.java#L19) | 19, 27, 64 |
| brand | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneProfileTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneProfileTest.java#L6) | 6, 14, 16, 20, 138, 143 |
| brand | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneTransactionIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneTransactionIntegrationTest.java#L22) | 22, 64, 67, 112, 114, 122, 142, 144, 146, 215, 242, 246, 251, 276, 289, 290, 317 |
| brand | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposerTest.java#L8) | 8, 26, 30, 32, 33, 38, 126, 127, 129, 130, 131, 134, 135, 151, 170, 193, 231 |
| brand | [backend/src/test/java/com/restaurant/system/owner/profile/StDenisCanonicalProfileContractTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/profile/StDenisCanonicalProfileContractTest.java#L16) | 16, 18, 27, 33, 50, 171 |
| brand | [backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileContractValidatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileContractValidatorTest.java#L65) | 65, 202 |
| brand | [backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileControllerTest.java#L47) | 47, 48, 57, 65, 66, 67, 77, 80, 87 |
| brand | [backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningFingerprintTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningFingerprintTest.java#L25) | 25, 28 |
| brand | [backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializerContractTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializerContractTest.java#L35) | 35 |
| brand | [backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningRequestCoordinatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningRequestCoordinatorTest.java#L39) | 39, 42, 71, 74, 100, 103, 153, 156, 179, 182, 197, 200 |
| brand | [backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImplTest.java#L195) | 195, 198 |
| brand | [backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerOverviewServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerOverviewServiceImplTest.java#L61) | 61, 63, 84, 101 |
| brand | [backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreMenuClonePostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreMenuClonePostgresIntegrationTest.java#L7) | 7, 54, 143, 145, 215, 218 |
| brand | [backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreMenuCloneRequestCoordinatorImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreMenuCloneRequestCoordinatorImplTest.java#L13) | 13, 49, 67, 94, 116, 138, 161, 185, 206, 208, 234, 236, 292, 310, 313, 370, 371, 407, 410 |
| brand | [backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreMenuCloneServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreMenuCloneServiceImplTest.java#L136) | 136, 148, 154, 161, 169 |
| brand | [backend/src/test/java/com/restaurant/system/platform/service/impl/PlatformAdminServiceImplMenuOrderingTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/platform/service/impl/PlatformAdminServiceImplMenuOrderingTest.java#L120) | 120, 139 |
| brand | [backend/src/test/java/com/restaurant/system/staging/cleanup/StoreFixtureCleanupPolicyTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/cleanup/StoreFixtureCleanupPolicyTest.java#L45) | 45, 53 |
| brand | [backend/src/test/java/com/restaurant/system/staging/cleanup/StoreFixtureCleanupServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/cleanup/StoreFixtureCleanupServiceImplTest.java#L43) | 43, 77, 118 |
| brand | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuCommandTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuCommandTest.java#L93) | 93 |
| brand | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuEvidenceWriterTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuEvidenceWriterTest.java#L22) | 22 |
| brand | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuGuardTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuGuardTest.java#L35) | 35 |
| brand | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuServiceIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuServiceIntegrationTest.java#L18) | 18, 23, 320, 324, 346 |
| brand | [deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md#L316) | 316 |
| brand | [deployment/cloud/README_PHASE_B_PART2_STAGING_ACCEPTANCE.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_PHASE_B_PART2_STAGING_ACCEPTANCE.md#L9) | 9 |
| brand | [deployment/cloud/README_PRODUCTION_V37_RELEASE.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_PRODUCTION_V37_RELEASE.md#L5) | 5 |
| brand | [deployment/cloud/README_STG005_SYNTHETIC_SOURCE_MENU.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_STG005_SYNTHETIC_SOURCE_MENU.md#L1) | 1, 9, 19, 35 |
| brand | [deployment/cloud/README_TWIN001_STAGING_RECONSTRUCTION.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_TWIN001_STAGING_RECONSTRUCTION.md#L7) | 7 |
| brand | [deployment/cloud/final-channels-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/apply-menu-once.py#L14) | 14 |
| brand | [deployment/cloud/final-channels-20261003/approved-modifiers.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/approved-modifiers.json#L32) | 32, 45 |
| brand | [deployment/cloud/final-closeout-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/apply-menu-once.py#L14) | 14 |
| brand | [deployment/cloud/final-closeout-20261003/approved-modifiers.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/approved-modifiers.json#L6) | 6, 46, 96, 136, 146, 186 |
| brand | [deployment/cloud/ops001-flyway-checksums.txt](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-flyway-checksums.txt#L17) | 17 |
| brand | [deployment/cloud/ops001-flyway-v26-checksums.txt](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-flyway-v26-checksums.txt#L18) | 18 |
| brand | [deployment/cloud/ops001-jq-compat.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-jq-compat.py#L484) | 484, 486 |
| brand | [deployment/cloud/production-v26-app-patch-evidence.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-app-patch-evidence.py#L79) | 79 |
| brand | [deployment/cloud/promote-addon-backend-20261006.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/promote-addon-backend-20261006.py#L23) | 23, 54, 94, 95 |
| brand | [deployment/cloud/reconcile-production-addons-20261006.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/reconcile-production-addons-20261006.sql#L225) | 225 |
| brand | [deployment/cloud/staging-ingress/README.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/README.md#L90) | 90 |
| brand | [deployment/cloud/staging-ingress/edge-http.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/edge-http.conf#L4) | 4, 10 |
| brand | [deployment/cloud/staging-ingress/edge-https.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/edge-https.conf#L11) | 11, 12, 13, 17, 18, 22, 23 |
| brand | [deployment/cloud/staging-ingress/host-relay.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/host-relay.conf#L16) | 16, 19, 23, 24 |
| brand | [deployment/cloud/staging-ingress/renew-staging.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/renew-staging.sh#L8) | 8 |
| brand | [deployment/cloud/staging-ingress/staging.conf.template](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/staging.conf.template#L5) | 5, 8, 18, 28 |
| brand | [deployment/cloud/staging-ingress/verify.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/verify.py#L13) | 13, 39 |
| brand | [deployment/cloud/staging-owner-acceptance-client.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-owner-acceptance-client.sh#L35) | 35, 70, 341, 481 |
| brand | [deployment/cloud/staging-pad-direct/README.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-pad-direct/README.md#L39) | 39, 67 |
| brand | [deployment/cloud/staging-pad-direct/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-pad-direct/apply-once.py#L59) | 59, 64 |
| brand | [deployment/cloud/staging-phase-b-part1-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-phase-b-part1-acceptance.sh#L315) | 315, 370, 372, 401, 428 |
| brand | [deployment/cloud/tests/test_production_v26_app_patch_evidence.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_production_v26_app_patch_evidence.py#L27) | 27 |
| brand | [deployment/cloud/tests/test_staging_owner_acceptance_client.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_owner_acceptance_client.sh#L74) | 74, 118 |
| brand | [deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh#L70) | 70, 107, 108, 122, 167 |
| brand | [deployment/cloud/tests/test_staging_phase_b_part2_acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_phase_b_part2_acceptance.sh#L54) | 54 |
| brand | [deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py#L12) | 12 |
| brand | [deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/approved-roots.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/approved-roots.json#L3) | 3, 35, 155, 171, 179 |
| brand | [frontend/src/data/mockOrdering.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/data/mockOrdering.ts#L123) | 123, 124, 137, 139, 140, 142 |
| brand | [frontend/src/features/store/storeModuleAccess.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/store/storeModuleAccess.test.ts#L40) | 40 |
| brand | [frontend/src/features/store/storeRoutes.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/store/storeRoutes.test.ts#L19) | 19, 20, 31, 32, 44, 45 |
| brand | [frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts#L105) | 105, 106, 144, 157 |
| brand | [frontend/src/hooks/useMenuCatalog.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.test.ts#L237) | 237, 243, 244, 388, 394, 395 |
| brand | [frontend/src/hooks/useTableBoard.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useTableBoard.test.ts#L23) | 23, 32 |
| brand | [frontend/src/services/ownerStoreProvisioningService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/ownerStoreProvisioningService.test.ts#L25) | 25, 27, 62, 64, 79, 82 |
| brand | [frontend/src/services/ownerWorkspaceService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/ownerWorkspaceService.test.ts#L7) | 7, 8 |
| combo | [backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L717) | 717, 793, 794 |
| combo | [backend/src/main/java/com/restaurant/system/integration/ubereats/mapping/UberEatsMenuMappingService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/mapping/UberEatsMenuMappingService.java#L270) | 270, 390, 413, 448, 477, 479, 481, 483, 485 |
| combo | [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsConfigurationService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsConfigurationService.java#L211) | 211 |
| combo | [backend/src/main/java/com/restaurant/system/kitchen/service/impl/KdsServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/service/impl/KdsServiceImpl.java#L38) | 38, 62 |
| combo | [backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java#L64) | 64 |
| combo | [backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java#L10) | 10, 11, 12, 13, 14, 57, 65 |
| combo | [backend/src/main/java/com/restaurant/system/menu/controller/OwnerMenuPricingPolicyController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/controller/OwnerMenuPricingPolicyController.java#L157) | 157 |
| combo | [backend/src/main/java/com/restaurant/system/menu/dto/MenuCatalogResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/dto/MenuCatalogResponse.java#L98) | 98 |
| combo | [backend/src/main/java/com/restaurant/system/menu/dto/MenuItemComboEggDefaultRequest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/dto/MenuItemComboEggDefaultRequest.java#L4) | 4 |
| combo | [backend/src/main/java/com/restaurant/system/menu/dto/StoreComboConfigurationResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/dto/StoreComboConfigurationResponse.java#L17) | 17 |
| combo | [backend/src/main/java/com/restaurant/system/menu/dto/StorePricingPolicyResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/dto/StorePricingPolicyResponse.java#L12) | 12 |
| combo | [backend/src/main/java/com/restaurant/system/menu/dto/StorePricingPolicyUpdateRequest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/dto/StorePricingPolicyUpdateRequest.java#L11) | 11 |
| combo | [backend/src/main/java/com/restaurant/system/menu/entity/MenuItem.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/entity/MenuItem.java#L61) | 61, 62 |
| combo | [backend/src/main/java/com/restaurant/system/menu/pricing/StorePricingPolicy.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/pricing/StorePricingPolicy.java#L35) | 35, 36 |
| combo | [backend/src/main/java/com/restaurant/system/menu/pricing/StorePricingPolicyRepository.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/pricing/StorePricingPolicyRepository.java#L19) | 19, 44 |
| combo | [backend/src/main/java/com/restaurant/system/menu/repository/MenuItemRepository.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/repository/MenuItemRepository.java#L20) | 20 |
| combo | [backend/src/main/java/com/restaurant/system/menu/service/StorePricingPolicyService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/StorePricingPolicyService.java#L17) | 17 |
| combo | [backend/src/main/java/com/restaurant/system/menu/service/impl/MenuCatalogHashService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/MenuCatalogHashService.java#L34) | 34, 100 |
| combo | [backend/src/main/java/com/restaurant/system/menu/service/impl/MenuServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/MenuServiceImpl.java#L140) | 140 |
| combo | [backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java#L34) | 34, 35, 175, 176, 185, 186, 233 |
| combo | [backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuStructureServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuStructureServiceImpl.java#L28) | 28, 203, 209, 211 |
| combo | [backend/src/main/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImpl.java#L43) | 43, 48, 115, 124, 126, 129, 136, 139, 145, 152, 153, 154, 161, 166, 538, 539, 546, 575, 582 |
| combo | [backend/src/main/java/com/restaurant/system/menu/service/impl/StorePricingPolicyServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/StorePricingPolicyServiceImpl.java#L114) | 114, 115, 121, 232, 280, 309, 332, 517 |
| combo | [backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L100) | 100, 101, 1080, 2099, 2413 |
| combo | [backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java#L46) | 46, 50, 127, 128, 129, 136, 137, 138, 171, 179, 180, 181, 182, 255, 260, 273, 440, 451, 454, 456 |
| combo | [backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileMaterializationDryRunValidator.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileMaterializationDryRunValidator.java#L129) | 129, 140 |
| combo | [backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java#L483) | 483, 515, 569, 570, 574 |
| combo | [backend/src/main/java/com/restaurant/system/platform/service/impl/PlatformAdminServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/platform/service/impl/PlatformAdminServiceImpl.java#L316) | 316, 317, 320 |
| combo | [backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L27) | 27, 28, 34, 35, 36, 345, 346, 347, 367, 383, 460 |
| combo | [backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java#L463) | 463 |
| combo | [backend/src/main/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolver.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolver.java#L12) | 12, 13, 19, 35 |
| combo | [backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java#L20) | 20, 21, 133 |
| combo | [backend/src/main/java/com/restaurant/system/printing/semantic/OptionSemanticResolver.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/OptionSemanticResolver.java#L12) | 12, 37, 47 |
| combo | [backend/src/main/resources/db/migration/V11__add_store_pricing_policies.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V11__add_store_pricing_policies.sql#L65) | 65, 122, 132, 142 |
| combo | [backend/src/main/resources/db/migration/V12__add_store_combo_components.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V12__add_store_combo_components.sql#L17) | 17, 20, 23, 41, 42, 43, 44, 45, 52, 55, 56, 63 |
| combo | [backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L1033) | 1033, 1047, 1061, 1075, 1089, 1103, 1691, 1705, 1719, 1733, 1747, 1761, 2251, 2265, 2279, 2293, 2307, 2321, 2811, 2825, 2839, 2853, 2867, 2881, 3175, 3189, 3203, 3217, 3231, 3245, 3539, 3553, 3567, 3581, 3595, 3609, 3903, 3917, 3931, 3945, 3959, 3973, 4239, 4267, 4309, 4337, 4351, 4393, 4715, 4729, 4743, 4757, 4771, 4785, 5219, 5233, 5247, 5261, 5275, 5289, 5751, 5765, 5779, 5793, 5807, 5821, 6361, 6367, 6403, 6404, 6412, 6413, 6421, 6422, 6430, 6431, 6439, 6440 |
| combo | [backend/src/main/resources/db/migration/V16__add_dynamic_menu_management_configuration.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V16__add_dynamic_menu_management_configuration.sql#L29) | 29, 30, 91, 102, 106 |
| combo | [backend/src/main/resources/db/migration/V27__add_item_combo_egg_default.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V27__add_item_combo_egg_default.sql#L2) | 2, 4, 5 |
| combo | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java#L861) | 861, 869, 875, 883, 901, 945, 946, 947, 958, 1738, 1746, 1752, 1760, 1775, 1794 |
| combo | [backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonJpaIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonJpaIntegrationTest.java#L296) | 296 |
| combo | [backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonServiceIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonServiceIntegrationTest.java#L425) | 425, 439 |
| combo | [backend/src/test/java/com/restaurant/system/menu/combo/MenuItemComboEggDefaultMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/combo/MenuItemComboEggDefaultMigrationTest.java#L21) | 21, 23, 39, 41 |
| combo | [backend/src/test/java/com/restaurant/system/menu/combo/StoreComboComponentMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/combo/StoreComboComponentMigrationTest.java#L54) | 54 |
| combo | [backend/src/test/java/com/restaurant/system/menu/controller/OwnerMenuComboEggDefaultControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/controller/OwnerMenuComboEggDefaultControllerTest.java#L42) | 42, 66, 68 |
| combo | [backend/src/test/java/com/restaurant/system/menu/service/impl/MenuCatalogHashServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/MenuCatalogHashServiceTest.java#L35) | 35, 38, 40, 135, 136, 145, 147, 152, 153, 162, 164, 165, 166, 167, 214 |
| combo | [backend/src/test/java/com/restaurant/system/menu/service/impl/MenuRevisionLockIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/MenuRevisionLockIntegrationTest.java#L173) | 173 |
| combo | [backend/src/test/java/com/restaurant/system/menu/service/impl/MenuServiceImplComboDefaultsTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/MenuServiceImplComboDefaultsTest.java#L46) | 46, 55, 58, 60 |
| combo | [backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImplTest.java#L219) | 219, 222, 414, 429, 438, 456, 464, 480 |
| combo | [backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuStructureServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuStructureServiceImplTest.java#L113) | 113, 170, 173 |
| combo | [backend/src/test/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImplTest.java#L74) | 74, 75, 76, 77, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 192, 206, 222, 228, 245, 257, 281, 292, 302, 309, 313, 315, 324, 327, 328, 332, 352, 353, 354, 358, 359, 363, 364, 372, 379, 382, 383, 386, 389, 394, 402, 410, 422, 426, 432, 446, 447, 448, 450, 451, 482, 486, 487, 488, 494, 507, 511, 512, 513, 541 |
| combo | [backend/src/test/java/com/restaurant/system/menu/service/impl/StorePricingPolicyServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/StorePricingPolicyServiceImplTest.java#L90) | 90, 168, 200, 205, 308 |
| combo | [backend/src/test/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImplTest.java#L299) | 299, 309 |
| combo | [backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java#L527) | 527, 563, 584, 930, 971, 992, 1080 |
| combo | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneProfileTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneProfileTest.java#L95) | 95, 96, 97, 137, 138 |
| combo | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneTransactionIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneTransactionIntegrationTest.java#L176) | 176, 177, 178 |
| combo | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposerTest.java#L52) | 52, 53, 56 |
| combo | [backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningTransactionIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningTransactionIntegrationTest.java#L340) | 340 |
| combo | [backend/src/test/java/com/restaurant/system/owner/service/impl/StoreMenuCloneTransactionServiceImplIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/StoreMenuCloneTransactionServiceImplIntegrationTest.java#L636) | 636 |
| combo | [backend/src/test/java/com/restaurant/system/platform/service/impl/PlatformAdminServiceImplMenuOrderingTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/platform/service/impl/PlatformAdminServiceImplMenuOrderingTest.java#L423) | 423, 435, 441, 444, 449, 459, 468, 599, 613 |
| combo | [backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java#L100) | 100, 101, 507, 695, 707, 718 |
| combo | [backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java#L56) | 56, 102, 135 |
| combo | [backend/src/test/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolverTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolverTest.java#L15) | 15, 33 |
| combo | [backend/src/test/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityServiceTest.java#L75) | 75, 82, 91, 122, 162, 163 |
| combo | [backend/src/test/java/com/restaurant/system/printing/semantic/OptionSemanticResolverTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/semantic/OptionSemanticResolverTest.java#L40) | 40, 48 |
| combo | [deployment/cloud/ops001-flyway-checksums.txt](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-flyway-checksums.txt#L29) | 29 |
| combo | [deployment/cloud/ops001-jq-compat.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-jq-compat.py#L598) | 598, 605 |
| combo | [deployment/cloud/production-v26-smoke.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-smoke.py#L462) | 462, 463, 464, 465, 466, 489, 555, 784 |
| combo | [deployment/cloud/staging-phase-b-part1-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-phase-b-part1-acceptance.sh#L283) | 283, 584 |
| combo | [deployment/cloud/tests/test_production_v26_smoke.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_production_v26_smoke.py#L239) | 239, 240, 241 |
| combo | [deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh#L143) | 143 |
| combo | [deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py#L76) | 76 |
| combo | [frontend/src/data/mockOrdering.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/data/mockOrdering.ts#L50) | 50, 59, 263, 264 |
| combo | [frontend/src/features/ordering/OrderingPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/OrderingPage.tsx#L79) | 79, 81 |
| combo | [frontend/src/features/ordering/components/ItemCustomizationModal.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.test.tsx#L24) | 24, 25, 30, 43 |
| combo | [frontend/src/features/ordering/components/ItemCustomizationModal.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.tsx#L42) | 42, 43, 48, 198, 199, 203, 204, 251 |
| combo | [frontend/src/features/owner-admin/ComboConfigurationPanel.interaction.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/ComboConfigurationPanel.interaction.test.tsx#L14) | 14, 17, 36 |
| combo | [frontend/src/features/owner-admin/ComboConfigurationPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/ComboConfigurationPanel.tsx#L100) | 100, 507, 542 |
| combo | [frontend/src/features/owner-admin/ComboEggOverridesPanel.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/ComboEggOverridesPanel.test.tsx#L41) | 41, 43, 44, 45, 51, 120, 129, 165 |
| combo | [frontend/src/features/owner-admin/ComboEggOverridesPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/ComboEggOverridesPanel.tsx#L67) | 67, 68, 133, 139, 147 |
| combo | [frontend/src/features/owner-admin/MenuOptionsPanel.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuOptionsPanel.test.tsx#L30) | 30, 61, 78, 102, 150, 155 |
| combo | [frontend/src/features/owner-admin/MenuOptionsPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuOptionsPanel.tsx#L19) | 19, 22, 30, 31, 32, 60 |
| combo | [frontend/src/features/owner-admin/PricingRulesPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PricingRulesPanel.tsx#L19) | 19, 29, 92, 173 |
| combo | [frontend/src/hooks/useDraftOrder.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useDraftOrder.ts#L166) | 166, 172, 222, 224, 227, 229 |
| combo | [frontend/src/hooks/useMenuCatalog.refresh.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.refresh.test.tsx#L21) | 21 |
| combo | [frontend/src/hooks/useMenuCatalog.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.test.ts#L66) | 66, 67, 92, 125, 136, 139, 146, 154, 158, 160, 164, 171, 174, 182, 461, 468, 474, 483, 494, 500, 536, 548, 570, 571, 583, 590, 591, 600, 610, 611, 620, 685, 700, 707, 708, 717, 727, 728, 738, 747, 834, 835, 839, 842 |
| combo | [frontend/src/hooks/useMenuCatalog.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.ts#L21) | 21, 22, 23, 24, 25, 28, 69, 78, 88, 176, 215, 254, 255, 298, 300 |
| combo | [frontend/src/hooks/useOrderSessions.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useOrderSessions.ts#L59) | 59, 60 |
| combo | [frontend/src/offline/menuCache.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/menuCache.test.ts#L37) | 37, 45, 46, 58, 74, 91, 92, 104, 120, 131, 136, 147, 212, 214, 218, 257 |
| combo | [frontend/src/offline/menuCache.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/menuCache.ts#L110) | 110, 178 |
| combo | [frontend/src/services/ownerAddonService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/ownerAddonService.test.ts#L31) | 31 |
| combo | [frontend/src/services/ownerMenuOptionService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/ownerMenuOptionService.ts#L11) | 11, 12, 13, 52, 60, 90, 107, 126, 145, 157, 233 |
| combo | [frontend/src/services/platformAdminService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/platformAdminService.ts#L85) | 85 |
| combo | [frontend/src/types/ordering.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/types/ordering.ts#L165) | 165, 180, 194, 230 |
| combo | [frontend/src/utils/comboSelection.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/comboSelection.test.ts#L73) | 73, 77, 78, 82, 86, 93, 97 |
| combo | [frontend/src/utils/comboSelection.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/comboSelection.ts#L8) | 8, 11, 56, 57, 61, 62 |
| endpoint | [backend/pom.xml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/pom.xml#L1) | 1, 2, 3 |
| endpoint | [backend/src/main/java/com/restaurant/system/common/config/RestCorsConfig.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RestCorsConfig.java#L14) | 14, 15, 16, 17 |
| endpoint | [backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L1543) | 1543, 1544, 1546, 1557 |
| endpoint | [backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java#L72) | 72, 73, 78, 79 |
| endpoint | [backend/src/main/java/com/restaurant/system/printing/CloudPrintingGuard.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/CloudPrintingGuard.java#L108) | 108 |
| endpoint | [backend/src/main/java/com/restaurant/system/printing/dto/PadPrintJobPayloadResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/dto/PadPrintJobPayloadResponse.java#L51) | 51 |
| endpoint | [backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java#L308) | 308, 313, 1087 |
| endpoint | [backend/src/main/java/com/restaurant/system/printing/service/impl/PrintJobServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/impl/PrintJobServiceImpl.java#L359) | 359, 462 |
| endpoint | [backend/src/main/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImpl.java#L75) | 75, 80 |
| endpoint | [backend/src/main/java/com/restaurant/system/printing/transport/EscPosTcpPrinterTransport.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/transport/EscPosTcpPrinterTransport.java#L51) | 51, 82, 107 |
| endpoint | [backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L6727) | 6727, 6740, 6753, 6766 |
| endpoint | [backend/src/test/java/com/restaurant/system/common/auth/PrinterTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/auth/PrinterTest.java#L8) | 8, 9, 45 |
| endpoint | [backend/src/test/java/com/restaurant/system/common/config/StagingForwardedOriginTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/config/StagingForwardedOriginTest.java#L42) | 42, 47, 50, 53, 58, 61, 70 |
| endpoint | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsOAuthClientTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsOAuthClientTest.java#L43) | 43 |
| endpoint | [backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileContractValidatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileContractValidatorTest.java#L277) | 277 |
| endpoint | [backend/src/test/java/com/restaurant/system/printing/CloudPrintingGuardTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/CloudPrintingGuardTest.java#L16) | 16, 39, 61 |
| endpoint | [backend/src/test/java/com/restaurant/system/printing/controller/OwnerPrintingControllerDisabledStateTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/controller/OwnerPrintingControllerDisabledStateTest.java#L281) | 281, 282 |
| endpoint | [backend/src/test/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImplTest.java#L76) | 76, 211, 216, 217, 218, 219, 226, 230, 235, 239, 256, 268, 321, 346, 480 |
| endpoint | [backend/src/test/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImplTest.java#L766) | 766 |
| endpoint | [backend/src/test/java/com/restaurant/system/printing/service/impl/PrintJobServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PrintJobServiceImplTest.java#L50) | 50, 70, 94 |
| endpoint | [backend/src/test/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImplTest.java#L163) | 163, 166, 169, 245 |
| endpoint | [backend/src/test/java/com/restaurant/system/printing/transport/EscPosTcpPrinterTransportTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/transport/EscPosTcpPrinterTransportTest.java#L22) | 22, 31 |
| endpoint | [deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_OPS001_STAGING_SECRET_SAFE_TOOLING.md#L74) | 74, 287 |
| endpoint | [deployment/cloud/README_PRODUCTION_V37_RELEASE.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_PRODUCTION_V37_RELEASE.md#L5) | 5 |
| endpoint | [deployment/cloud/README_STAGING_SERVER_PREFLIGHT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_STAGING_SERVER_PREFLIGHT.md#L10) | 10, 154 |
| endpoint | [deployment/cloud/final-channels-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/apply-menu-once.py#L14) | 14 |
| endpoint | [deployment/cloud/final-channels-20261003/backfill-financial.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/backfill-financial.py#L221) | 221, 232 |
| endpoint | [deployment/cloud/final-channels-20261003/deploy-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/deploy-once.py#L33) | 33, 106 |
| endpoint | [deployment/cloud/final-closeout-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/apply-menu-once.py#L14) | 14 |
| endpoint | [deployment/cloud/final-closeout-20261003/deploy-layout-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/deploy-layout-once.py#L33) | 33, 106 |
| endpoint | [deployment/cloud/final-closeout-20261003/deploy-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/deploy-once.py#L33) | 33, 106 |
| endpoint | [deployment/cloud/health-check.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/health-check.sh#L17) | 17, 19 |
| endpoint | [deployment/cloud/nginx.conf.example](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/nginx.conf.example#L13) | 13, 22, 43, 57, 66 |
| endpoint | [deployment/cloud/nginx.http.conf.template](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/nginx.http.conf.template#L18) | 18, 30 |
| endpoint | [deployment/cloud/nginx.https.conf.template](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/nginx.https.conf.template#L11) | 11, 29, 40 |
| endpoint | [deployment/cloud/production-exact-artifact-promote.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-exact-artifact-promote.sh#L149) | 149, 156, 157 |
| endpoint | [deployment/cloud/production-v10-v26-rehearsal.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v10-v26-rehearsal.sh#L387) | 387, 403 |
| endpoint | [deployment/cloud/production-v26-app-patch-promote.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-app-patch-promote.sh#L182) | 182, 188, 277 |
| endpoint | [deployment/cloud/production-v26-exact-artifact-promote.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-exact-artifact-promote.sh#L277) | 277, 285, 286, 289, 332, 337 |
| endpoint | [deployment/cloud/production-v26-recover.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-recover.sh#L411) | 411, 419, 420 |
| endpoint | [deployment/cloud/promote-addon-backend-20261006.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/promote-addon-backend-20261006.py#L23) | 23, 54, 94, 95 |
| endpoint | [deployment/cloud/staging-health-check.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-health-check.sh#L126) | 126 |
| endpoint | [deployment/cloud/staging-ingress/README.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/README.md#L90) | 90 |
| endpoint | [deployment/cloud/staging-ingress/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/apply-once.py#L25) | 25, 37, 164 |
| endpoint | [deployment/cloud/staging-ingress/edge-http.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/edge-http.conf#L10) | 10 |
| endpoint | [deployment/cloud/staging-ingress/edge-https.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/edge-https.conf#L20) | 20 |
| endpoint | [deployment/cloud/staging-ingress/host-relay.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/host-relay.conf#L21) | 21 |
| endpoint | [deployment/cloud/staging-ingress/staging.conf.template](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/staging.conf.template#L18) | 18, 19, 20, 21, 22, 32, 47 |
| endpoint | [deployment/cloud/staging-ingress/verify.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/verify.py#L14) | 14, 36, 37, 38, 39, 53, 78 |
| endpoint | [deployment/cloud/staging-local-rehearsal.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-local-rehearsal.sh#L414) | 414, 415, 420, 423, 424 |
| endpoint | [deployment/cloud/staging-owner-acceptance-client.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-owner-acceptance-client.sh#L36) | 36 |
| endpoint | [deployment/cloud/staging-pad-direct/README.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-pad-direct/README.md#L19) | 19 |
| endpoint | [deployment/cloud/staging-pad-direct/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-pad-direct/apply-once.py#L50) | 50 |
| endpoint | [deployment/cloud/staging-phase-b-part1-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-phase-b-part1-acceptance.sh#L15) | 15 |
| endpoint | [deployment/cloud/staging-phase-b-part2-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-phase-b-part2-acceptance.sh#L8) | 8, 562, 563, 564 |
| endpoint | [deployment/cloud/staging-release-control-bootstrap.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-release-control-bootstrap.sh#L11) | 11 |
| endpoint | [deployment/cloud/staging-runtime-evidence.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-runtime-evidence.sh#L199) | 199, 200, 201 |
| endpoint | [deployment/cloud/staging-synthetic-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-synthetic-acceptance.sh#L680) | 680 |
| endpoint | [deployment/cloud/tests/test_production_v26_smoke.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_production_v26_smoke.py#L115) | 115, 117, 118, 119, 121, 126, 163, 198, 213, 219, 247 |
| endpoint | [deployment/cloud/tests/test_staging_owner_acceptance_client.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_owner_acceptance_client.sh#L87) | 87 |
| endpoint | [deployment/cloud/tests/test_staging_release_control_bootstrap.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_release_control_bootstrap.sh#L33) | 33 |
| endpoint | [deployment/cloud/tests/test_staging_runtime_evidence.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_runtime_evidence.sh#L109) | 109, 110, 111, 119, 127, 134, 141 |
| endpoint | [deployment/cloud/uber-capability-20261003/deploy-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-capability-20261003/deploy-once.py#L33) | 33, 106 |
| endpoint | [deployment/cloud/uber-kitchen-mirror/apply-field-fixes-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/apply-field-fixes-once.py#L33) | 33, 106 |
| endpoint | [deployment/cloud/uber-kitchen-mirror/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/apply-once.py#L108) | 108 |
| endpoint | [deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py#L12) | 12 |
| endpoint | [deployment/cloud/uber-note-20261003/deploy-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-note-20261003/deploy-once.py#L33) | 33, 106 |
| endpoint | [deployment/cloud/uber-signing-key/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-signing-key/apply-once.py#L94) | 94 |
| endpoint | [deployment/windows-pilot/README_WINDOWS_PILOT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/README_WINDOWS_PILOT.md#L14) | 14, 123, 226 |
| endpoint | [deployment/windows-pilot/package/README_WINDOWS_PILOT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/package/README_WINDOWS_PILOT.md#L14) | 14, 123, 226 |
| endpoint | [deployment/windows-pilot/package/check-pos-windows.bat](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/package/check-pos-windows.bat#L16) | 16, 19, 22, 26 |
| endpoint | [deployment/windows-pilot/package/scripts/frontend-server.js](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/package/scripts/frontend-server.js#L118) | 118, 119 |
| endpoint | [deployment/windows-pilot/package/start-pos-windows.bat](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/package/start-pos-windows.bat#L20) | 20, 21 |
| endpoint | [deployment/windows-pilot/scripts/check-pos-windows.bat](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/scripts/check-pos-windows.bat#L16) | 16, 19, 22, 26 |
| endpoint | [deployment/windows-pilot/scripts/frontend-server.js](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/scripts/frontend-server.js#L118) | 118, 119 |
| endpoint | [deployment/windows-pilot/scripts/start-pos-windows.bat](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/scripts/start-pos-windows.bat#L20) | 20, 21 |
| endpoint | [frontend/README.md](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/README.md#L7) | 7, 8, 12, 46 |
| endpoint | [frontend/public/favicon.svg](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/public/favicon.svg#L1) | 1 |
| endpoint | [frontend/public/icons.svg](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/public/icons.svg#L1) | 1 |
| endpoint | [frontend/src/features/owner-admin/PrintingSettingsPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PrintingSettingsPage.tsx#L88) | 88, 89, 91, 716, 1154, 1216, 1220, 1292, 1433 |
| endpoint | [frontend/src/index.css](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/index.css#L1) | 1 |
| endpoint | [frontend/src/services/manualReprintService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/manualReprintService.test.ts#L14) | 14 |
| endpoint | [frontend/src/services/networkStatus.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/networkStatus.ts#L71) | 71 |
| endpoint | [frontend/src/services/orderService.realtime.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderService.realtime.test.ts#L47) | 47, 61 |
| endpoint | [frontend/src/utils/displayLabels.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/displayLabels.ts#L65) | 65 |
| endpoint | [frontend/vite.config.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/vite.config.ts#L7) | 7, 24, 28, 40, 44 |
| endpoint | [restaurant-pad-app/README_PAD_APP.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/README_PAD_APP.md#L31) | 31, 106, 118, 133, 134, 141, 142, 146, 148, 151, 152, 153 |
| endpoint | [restaurant-pad-app/android/app/src/debug/AndroidManifest.xml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/debug/AndroidManifest.xml#L2) | 2, 3 |
| endpoint | [restaurant-pad-app/android/app/src/main/AndroidManifest.xml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/AndroidManifest.xml#L1) | 1 |
| endpoint | [restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L65) | 65, 90, 350, 358, 381, 3178, 3194 |
| endpoint | [restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/PrinterPluginBridge.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/PrinterPluginBridge.java#L41) | 41, 56 |
| endpoint | [restaurant-pad-app/android/app/src/test/java/com/restaurant/pad/DeadlineTcpPrinterTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/test/java/com/restaurant/pad/DeadlineTcpPrinterTest.java#L33) | 33, 39, 44, 53, 57, 59, 64, 71, 92, 95 |
| endpoint | [restaurant-pad-app/android/app/src/test/java/com/restaurant/pad/PrintRequestProofTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/test/java/com/restaurant/pad/PrintRequestProofTest.java#L10) | 10 |
| endpoint | [restaurant-pad-app/docs/API_BASE_CONFIG.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/API_BASE_CONFIG.md#L10) | 10, 28, 41, 57, 115, 217, 269, 279 |
| endpoint | [restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md#L12) | 12, 59 |
| endpoint | [restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md#L20) | 20, 27, 60, 77, 83, 84, 231, 274, 280, 317, 326, 344, 353, 354, 355, 358, 364, 365, 373 |
| endpoint | [restaurant-pad-app/docs/PAD_DIRECT_PENDING_JOBS.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PAD_DIRECT_PENDING_JOBS.md#L49) | 49, 50, 51, 59, 60, 61 |
| endpoint | [restaurant-pad-app/docs/PRINTER_PLUGIN_POC.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PRINTER_PLUGIN_POC.md#L22) | 22, 23, 32, 33, 41, 48, 76, 78, 85, 86, 87, 90, 100, 111, 128 |
| endpoint | [restaurant-pad-app/docs/PRINTER_TESTING.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PRINTER_TESTING.md#L26) | 26, 36, 62 |
| endpoint | [restaurant-pad-app/docs/WEBVIEW_ROUTING.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/WEBVIEW_ROUTING.md#L18) | 18 |
| menu_identity | [backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java#L22) | 22, 24, 27 |
| menu_identity | [backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L63) | 63, 64, 65, 66, 455, 460, 461, 462, 463, 469, 470, 489, 493, 494, 495, 496, 500, 501, 522, 527, 528, 529, 530, 535, 538, 676, 714, 717, 731, 732, 807, 809, 811, 816, 817, 828, 856 |
| menu_identity | [backend/src/main/java/com/restaurant/system/integration/ubereats/mapping/UberEatsMenuMappingService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/mapping/UberEatsMenuMappingService.java#L420) | 420, 422, 477, 481, 485 |
| menu_identity | [backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonService.java#L404) | 404 |
| menu_identity | [backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java#L10) | 10, 12, 14 |
| menu_identity | [backend/src/main/java/com/restaurant/system/menu/service/impl/MenuServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/MenuServiceImpl.java#L206) | 206, 207, 209, 210, 213, 215, 221, 223 |
| menu_identity | [backend/src/main/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImpl.java#L551) | 551, 553 |
| menu_identity | [backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2007) | 2007, 2008, 2009, 2010, 2066, 2067, 2068, 2069, 2073, 2074, 2171, 2172, 2185, 2189, 2435, 2436, 2438, 2439, 2442, 2444, 2450, 2452 |
| menu_identity | [backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java#L41) | 41, 42, 45, 48, 90, 99, 101, 102, 107, 172, 173, 179, 228, 244, 260, 445, 454, 464 |
| menu_identity | [backend/src/main/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposer.java#L269) | 269, 275 |
| menu_identity | [backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java#L582) | 582, 584 |
| menu_identity | [backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L467) | 467, 469 |
| menu_identity | [backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java#L290) | 290, 291, 292, 293, 297, 298, 331, 332, 347, 351 |
| menu_identity | [backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java#L9) | 9 |
| menu_identity | [backend/src/main/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolver.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolver.java#L23) | 23, 25 |
| menu_identity | [backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java#L25) | 25, 26, 27, 28 |
| menu_identity | [backend/src/main/java/com/restaurant/system/printing/semantic/KitchenModifierTokenResolver.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/KitchenModifierTokenResolver.java#L105) | 105, 119, 121, 145 |
| menu_identity | [backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java#L1515) | 1515, 1568, 1603 |
| menu_identity | [backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactory.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactory.java#L22) | 22, 42, 53, 54, 82, 85, 88 |
| menu_identity | [backend/src/main/resources/db/migration/V12__add_store_combo_components.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V12__add_store_combo_components.sql#L21) | 21, 25, 27, 41, 43, 45, 53, 58, 60, 67, 70, 75, 77, 81, 83 |
| menu_identity | [backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L383) | 383, 472, 488, 501, 504, 824, 840, 856, 872, 1032, 1058, 1060, 1088, 1143, 1157, 1312, 1619, 1633, 1690, 1716, 1718, 1746, 1801, 1815, 1970, 2250, 2276, 2278, 2306, 2361, 2375, 2530, 2824, 2836, 2838, 2866, 2964, 3188, 3200, 3202, 3230, 3328, 3552, 3564, 3566, 3594, 3692, 3916, 3928, 3930, 3958, 4056, 4238, 4293, 4306, 4308, 4321, 4350, 4490, 4714, 4740, 4742, 4770, 4811, 4825, 4966, 5118, 5120, 5218, 5244, 5246, 5274, 5301, 5315, 5470, 5750, 5776, 5778, 5806, 5861, 5875, 6058, 6402, 6404, 6420, 6422, 6425, 6438, 6440 |
| menu_identity | [backend/src/main/resources/db/migration/V16__add_dynamic_menu_management_configuration.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V16__add_dynamic_menu_management_configuration.sql#L108) | 108, 110 |
| menu_identity | [backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql#L129) | 129 |
| menu_identity | [backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql#L513) | 513 |
| menu_identity | [backend/src/test/java/com/restaurant/system/analytics/service/ChannelSalesServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/analytics/service/ChannelSalesServiceTest.java#L34) | 34, 35, 52, 53, 55, 56, 81, 92, 96 |
| menu_identity | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java#L196) | 196, 204, 749, 840, 883, 891, 902, 913, 923, 1418, 1671, 1687, 1717, 1760, 1768, 1775, 1779, 1808, 1810, 1859, 1860, 2385 |
| menu_identity | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSalesSourceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSalesSourceTest.java#L37) | 37, 49 |
| menu_identity | [backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonServiceIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonServiceIntegrationTest.java#L226) | 226, 232, 240, 259, 265 |
| menu_identity | [backend/src/test/java/com/restaurant/system/menu/combo/StoreComboComponentMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/combo/StoreComboComponentMigrationTest.java#L25) | 25, 27, 29 |
| menu_identity | [backend/src/test/java/com/restaurant/system/menu/service/impl/MenuCatalogHashServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/MenuCatalogHashServiceTest.java#L38) | 38, 70, 94, 143, 145, 160, 162, 163, 166, 167 |
| menu_identity | [backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuStructureServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuStructureServiceImplTest.java#L171) | 171 |
| menu_identity | [backend/src/test/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImplTest.java#L80) | 80, 82, 84, 85, 87, 89, 268, 270, 273, 328, 343, 345, 352, 354, 365, 382, 383, 386, 402, 422, 447, 451, 482, 488, 494, 507, 513 |
| menu_identity | [backend/src/test/java/com/restaurant/system/modules/UberStoreModuleMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/UberStoreModuleMigrationTest.java#L36) | 36, 46 |
| menu_identity | [backend/src/test/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImplTest.java#L104) | 104, 400 |
| menu_identity | [backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java#L207) | 207, 930, 931, 972, 982, 984, 1032, 1034, 1081 |
| menu_identity | [backend/src/test/java/com/restaurant/system/order/service/impl/OrderSubmissionHashServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/OrderSubmissionHashServiceImplTest.java#L48) | 48 |
| menu_identity | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneProfileTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneProfileTest.java#L45) | 45, 51, 52, 55, 68, 71, 80, 83, 107, 123, 134 |
| menu_identity | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneTransactionIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneTransactionIntegrationTest.java#L179) | 179, 186, 302, 309, 310, 324 |
| menu_identity | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposerTest.java#L57) | 57, 69, 70, 80, 85, 113, 183, 196 |
| menu_identity | [backend/src/test/java/com/restaurant/system/owner/provisioning/ProvisionedStandardSizePlanTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/ProvisionedStandardSizePlanTest.java#L45) | 45, 46, 54, 55 |
| menu_identity | [backend/src/test/java/com/restaurant/system/platform/service/OwnerDashboardReportingTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/platform/service/OwnerDashboardReportingTest.java#L68) | 68, 94, 107, 116 |
| menu_identity | [backend/src/test/java/com/restaurant/system/platform/service/impl/PlatformAdminServiceImplMenuOrderingTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/platform/service/impl/PlatformAdminServiceImplMenuOrderingTest.java#L441) | 441 |
| menu_identity | [backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java#L100) | 100, 318, 319, 328, 477, 507, 708 |
| menu_identity | [backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java#L58) | 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 81, 87, 103, 111, 136, 147, 394, 429 |
| menu_identity | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContextTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContextTest.java#L14) | 14, 16, 18, 20, 27, 37, 40, 41 |
| menu_identity | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceValidationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceValidationTest.java#L103) | 103, 131, 134, 154, 161, 172, 175 |
| menu_identity | [backend/src/test/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolverTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolverTest.java#L15) | 15, 25, 32 |
| menu_identity | [backend/src/test/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityServiceTest.java#L55) | 55, 82, 88, 100, 101, 102, 103, 104, 105, 110, 161 |
| menu_identity | [backend/src/test/java/com/restaurant/system/printing/semantic/KitchenModifierTokenResolverTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/semantic/KitchenModifierTokenResolverTest.java#L42) | 42, 96, 119 |
| menu_identity | [backend/src/test/java/com/restaurant/system/printing/semantic/OptionSemanticResolverTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/semantic/OptionSemanticResolverTest.java#L48) | 48 |
| menu_identity | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactoryTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactoryTest.java#L31) | 31, 34 |
| menu_identity | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestValidatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestValidatorTest.java#L59) | 59, 62, 72, 75, 84, 89, 93, 95, 99, 100, 108 |
| menu_identity | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuPlannerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuPlannerTest.java#L37) | 37, 55, 84, 135 |
| menu_identity | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuServiceIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuServiceIntegrationTest.java#L219) | 219, 239, 243, 260 |
| menu_identity | [deployment/cloud/README_STG005_SYNTHETIC_SOURCE_MENU.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_STG005_SYNTHETIC_SOURCE_MENU.md#L31) | 31 |
| menu_identity | [deployment/cloud/README_TWIN001_STAGING_RECONSTRUCTION.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_TWIN001_STAGING_RECONSTRUCTION.md#L51) | 51 |
| menu_identity | [deployment/cloud/final-channels-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/apply-menu-once.py#L53) | 53, 54, 55, 56, 58, 134, 135, 138, 141, 149, 150, 193, 219, 237, 242 |
| menu_identity | [deployment/cloud/final-channels-20261003/approved-modifiers.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/approved-modifiers.json#L7) | 7, 20, 22, 33, 46, 48, 59, 72, 74, 85, 98, 100 |
| menu_identity | [deployment/cloud/final-channels-20261003/reconcile-tea-egg.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/reconcile-tea-egg.sql#L1) | 1, 4, 5, 6, 7, 8, 9, 10, 18, 20, 24, 26, 28, 33, 34, 37, 40, 41, 44, 46, 49, 50 |
| menu_identity | [deployment/cloud/final-closeout-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/apply-menu-once.py#L63) | 63, 115 |
| menu_identity | [deployment/cloud/final-closeout-20261003/approved-modifiers.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/approved-modifiers.json#L7) | 7, 47, 97, 137, 147, 187 |
| menu_identity | [deployment/cloud/production-v26-app-patch-evidence.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-app-patch-evidence.py#L106) | 106, 107 |
| menu_identity | [deployment/cloud/production-v26-smoke.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-smoke.py#L462) | 462, 464, 466 |
| menu_identity | [deployment/cloud/reconcile-production-addons-20261006.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/reconcile-production-addons-20261006.sql#L25) | 25, 27, 28, 29, 30, 31, 32, 33, 34, 35, 38, 46, 53, 54, 55, 56, 57, 58, 59, 60, 62, 68, 76, 84, 93, 94, 95, 96, 97, 98, 99, 100, 101, 103, 108, 110, 111, 113, 120, 127, 128, 130, 138, 145, 146, 148, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 167, 172, 174, 175, 177, 185, 192, 193, 195, 203, 210, 211, 213 |
| menu_identity | [deployment/cloud/tests/test_production_v26_app_patch_evidence.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_production_v26_app_patch_evidence.py#L99) | 99, 100 |
| menu_identity | [deployment/cloud/tests/test_production_v26_smoke.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_production_v26_smoke.py#L239) | 239 |
| menu_identity | [deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh#L143) | 143 |
| menu_identity | [deployment/cloud/uber-capability-20261003/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-capability-20261003/apply-once.py#L59) | 59 |
| menu_identity | [deployment/cloud/uber-capability-20261003/reconcile-option370.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-capability-20261003/reconcile-option370.sql#L12) | 12, 15, 17, 20, 25, 38, 39 |
| menu_identity | [deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/approved-roots.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/approved-roots.json#L5) | 5, 13, 19, 21, 37, 53, 61, 173, 181, 269, 277, 285, 301, 309 |
| menu_identity | [deployment/cloud/uber-note-20261003/RenderNoteAcceptance.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-note-20261003/RenderNoteAcceptance.java#L34) | 34, 55, 114 |
| menu_identity | [frontend/src/data/menuImportSeed.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/data/menuImportSeed.ts#L42) | 42, 86, 97, 108, 119, 163, 174, 175, 330, 331, 334, 339, 340, 343, 348, 349, 357, 358, 366, 367, 375, 376, 384, 385, 393, 394, 396, 402, 403, 411, 412, 420, 421, 429, 430, 438, 439, 447, 448, 456, 457, 465, 466, 474, 475, 483, 484, 492, 493, 501, 502, 510, 511, 519, 520, 528, 529, 537, 538, 546, 547, 555, 556, 564, 565, 573, 574, 582, 583, 591, 592, 600, 601, 609, 610, 618, 619, 631, 640, 672, 690, 693, 708, 807, 928, 937, 969, 987, 990, 1005, 1104, 1230, 1248, 1251, 1266, 1297, 1306, 1419, 1572, 1573, 1581, 1582, 1590, 1591, 1599, 1600, 1602, 1608, 1609, 1617, 1618, 1626, 1627, 1635, 1636, 1644, 1645, 1653, 1654, 1662, 1663, 1671, 1672, 1680, 1681, 1689, 1690, 1698, 1699, 1707, 1708, 1716, 1717, 1725, 1726, 1734, 1735, 1743, 1744, 1752, 1753, 1761, 1762, 1770, 1771, 1779, 1780, 1782, 1788, 1789, 1797, 1798, 1806, 1807, 1815, 1816, 1824, 1825, 1833, 1834, 1842, 1843, 1851, 1852, 1860, 1861, 1869, 1870, 1878, 1879, 1887, 1888, 1896, 1897, 1905, 1906, 1914, 1915, 1923, 1924, 1932, 1933, 1941, 1942, 1950, 1951, 1959, 1960, 1962, 1968, 1969, 1977, 1978, 1986, 1987, 1995, 1996, 2004, 2005, 2013, 2014, 2022, 2023, 2031, 2032, 2040, 2041, 2049, 2050, 2058, 2059, 2067, 2068, 2076, 2077, 2085, 2086, 2094, 2095, 2103, 2104, 2112, 2113, 2121, 2122, 2130, 2131, 2139, 2140, 2142, 2148, 2149, 2157, 2158, 2166, 2167, 2175, 2176, 2184, 2185, 2193, 2194, 2202, 2203, 2211, 2212, 2220, 2221, 2229, 2230, 2238, 2239, 2247, 2248, 2256, 2257, 2265, 2266, 2274, 2275, 2283, 2284, 2296, 2305, 2328, 2346, 2349, 2364, 2454, 2548, 2557, 2580, 2598, 2601, 2616, 2706, 2805, 2808, 2827, 2836, 2859, 2877, 2880, 2895, 2985, 3084, 3085 |
| menu_identity | [frontend/src/data/mockOrdering.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/data/mockOrdering.ts#L23) | 23, 24, 225, 276 |
| menu_identity | [frontend/src/features/ordering/components/ItemCustomizationModal.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.test.tsx#L14) | 14, 15, 24 |
| menu_identity | [frontend/src/features/owner-admin/AddonCatalogPanel.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/AddonCatalogPanel.test.tsx#L20) | 20, 117, 128, 134, 146, 201, 211 |
| menu_identity | [frontend/src/features/owner-admin/ComboConfigurationPanel.interaction.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/ComboConfigurationPanel.interaction.test.tsx#L15) | 15, 16, 28 |
| menu_identity | [frontend/src/features/owner-admin/ComboConfigurationPanel.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/ComboConfigurationPanel.test.ts#L38) | 38, 40, 44, 45, 53 |
| menu_identity | [frontend/src/features/owner-admin/ComboEggOverridesPanel.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/ComboEggOverridesPanel.test.tsx#L42) | 42, 43, 108, 128, 129, 131, 155, 159 |
| menu_identity | [frontend/src/features/owner-admin/PrintingDisplayRulesPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PrintingDisplayRulesPanel.tsx#L182) | 182, 186, 188 |
| menu_identity | [frontend/src/features/reports/components/ChannelSalesPanels.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.test.tsx#L66) | 66 |
| menu_identity | [frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts#L8) | 8, 78, 163 |
| menu_identity | [frontend/src/hooks/useMenuCatalog.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.test.ts#L96) | 96, 97, 137, 138, 181, 242, 393, 471, 475, 497, 501, 503, 516, 535, 547, 552, 563, 564, 570, 571, 598, 601, 618, 621, 623, 666, 715, 718, 735, 739, 741, 748, 814, 836, 842, 846 |
| menu_identity | [frontend/src/hooks/useMenuCatalog.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.ts#L21) | 21, 23, 25, 29, 31, 71, 80 |
| menu_identity | [frontend/src/offline/menuCache.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/menuCache.test.ts#L53) | 53, 59, 99, 105, 107, 112, 114, 137, 144, 166, 218, 282 |
| menu_identity | [frontend/src/offline/orderOutbox.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/orderOutbox.test.ts#L65) | 65 |
| menu_identity | [frontend/src/services/orderOutboxProcessor.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderOutboxProcessor.test.ts#L82) | 82 |
| menu_identity | [frontend/src/services/orderService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderService.test.ts#L25) | 25, 121 |
| menu_identity | [frontend/src/utils/comboSelection.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/comboSelection.test.ts#L77) | 77 |
| names_logic | [backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2136) | 2136, 2139 |
| names_logic | [backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L215) | 215, 290, 293, 296, 504 |
| names_logic | [backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java#L270) | 270, 273 |
| names_logic | [backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java#L47) | 47 |
| names_logic | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java#L783) | 783, 784, 1053, 1254, 1290, 1445, 1446 |
| names_logic | [backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java#L954) | 954, 955, 1022, 1053, 1138, 1139, 1166, 1167, 1168, 1200, 1240, 1267, 1268, 1293, 1294, 1295 |
| names_logic | [backend/src/test/java/com/restaurant/system/printing/renderer/ExternalOrderNoteRenderingTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/ExternalOrderNoteRenderingTest.java#L51) | 51, 64 |
| names_logic | [backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java#L30) | 30, 31, 42, 43, 53, 63, 64, 75, 76, 78, 89, 105, 111, 112, 119, 120, 127, 128, 135, 136, 137, 144, 145, 152, 153, 160, 161, 162, 169, 170, 177, 178, 188, 189, 196, 197, 198, 199, 204, 205, 206, 207, 208, 215, 216, 223, 230, 231, 241, 242, 253, 260, 261, 262, 269, 270, 280, 290, 291, 292, 293, 303, 304, 336, 367, 372, 373, 418, 423, 424, 492, 521, 523, 524, 525, 526, 548, 575, 599, 600, 624, 647, 648, 649, 738, 740, 741, 742, 743 |
| names_logic | [backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java#L36) | 36, 37, 38, 85, 86, 88, 119, 122, 156, 157, 160, 171, 185, 198, 199, 213, 225, 226, 236, 237, 250, 263, 264, 265, 266, 279, 280, 298, 340, 372, 373, 443, 444, 445, 446, 447, 461, 462, 493 |
| names_logic | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceLifecycleIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceLifecycleIntegrationTest.java#L204) | 204, 307, 308 |
| names_logic | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceValidationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceValidationTest.java#L140) | 140, 141, 142, 180, 181, 209, 210 |
| names_logic | [frontend/src/features/ordering/components/ItemCustomizationModal.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.test.tsx#L38) | 38 |
| names_logic | [frontend/src/features/owner-admin/MenuManagementPage.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuManagementPage.test.tsx#L124) | 124, 125 |
| names_logic | [frontend/src/features/owner-admin/MenuOptionsPanel.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuOptionsPanel.test.tsx#L68) | 68, 72, 124 |
| names_logic | [frontend/src/features/pickup/components/PickupOrderCard.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/pickup/components/PickupOrderCard.tsx#L48) | 48, 51, 54, 57 |
| names_logic | [frontend/src/hooks/useMenuCatalog.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.ts#L73) | 73, 82, 83 |
| permission_flags | [backend/src/main/java/com/restaurant/system/analytics/controller/AnalyticsAdminController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/controller/AnalyticsAdminController.java#L7) | 7, 8 |
| permission_flags | [backend/src/main/java/com/restaurant/system/auth/entity/RolePermission.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/auth/entity/RolePermission.java#L22) | 22 |
| permission_flags | [backend/src/main/java/com/restaurant/system/auth/service/impl/AuthServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/auth/service/impl/AuthServiceImpl.java#L19) | 19, 20, 281, 282, 283, 284, 285, 286, 287 |
| permission_flags | [backend/src/main/java/com/restaurant/system/common/auth/AuthorizationService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/AuthorizationService.java#L65) | 65, 67, 68, 148, 149 |
| permission_flags | [backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java#L27) | 27 |
| permission_flags | [backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java#L12) | 12, 13, 18, 21, 47, 82, 103, 107, 114, 115 |
| permission_flags | [backend/src/main/java/com/restaurant/system/common/config/ProductionSafetyConfig.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/ProductionSafetyConfig.java#L118) | 118, 119, 179, 180 |
| permission_flags | [backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L239) | 239, 252, 255, 256, 257, 258, 259, 260, 261, 262, 263, 275, 282, 283, 288, 1436, 1437 |
| permission_flags | [backend/src/main/java/com/restaurant/system/common/exception/GlobalExceptionHandler.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/exception/GlobalExceptionHandler.java#L5) | 5, 83 |
| permission_flags | [backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java#L5) | 5, 6 |
| permission_flags | [backend/src/main/java/com/restaurant/system/dev/DevTestUser.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/dev/DevTestUser.java#L14) | 14 |
| permission_flags | [backend/src/main/java/com/restaurant/system/dev/service/impl/DevAuthServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/dev/service/impl/DevAuthServiceImpl.java#L13) | 13, 14, 177, 178, 179, 180, 181, 182, 183 |
| permission_flags | [backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsInboxController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsInboxController.java#L83) | 83 |
| permission_flags | [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsInboxEvents.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsInboxEvents.java#L43) | 43 |
| permission_flags | [backend/src/main/java/com/restaurant/system/kitchen/controller/KdsController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/controller/KdsController.java#L6) | 6, 61, 62, 65 |
| permission_flags | [backend/src/main/java/com/restaurant/system/kitchen/dto/FrontdeskBeverageOrderResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/dto/FrontdeskBeverageOrderResponse.java#L6) | 6 |
| permission_flags | [backend/src/main/java/com/restaurant/system/kitchen/service/KdsService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/service/KdsService.java#L3) | 3, 17 |
| permission_flags | [backend/src/main/java/com/restaurant/system/kitchen/service/impl/KdsServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/service/impl/KdsServiceImpl.java#L4) | 4, 41, 105, 114, 117, 122 |
| permission_flags | [backend/src/main/java/com/restaurant/system/kitchen/service/impl/KitchenServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/service/impl/KitchenServiceImpl.java#L160) | 160 |
| permission_flags | [backend/src/main/java/com/restaurant/system/modules/HardwareCapabilityKeys.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/modules/HardwareCapabilityKeys.java#L7) | 7 |
| permission_flags | [backend/src/main/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImpl.java#L3) | 3, 4, 68, 74, 77, 111, 112 |
| permission_flags | [backend/src/main/java/com/restaurant/system/modules/StoreModuleServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/modules/StoreModuleServiceImpl.java#L162) | 162 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/controller/FrontdeskBeverageController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/controller/FrontdeskBeverageController.java#L9) | 9, 14, 24, 25, 27, 33, 34, 40, 48, 54, 58, 61, 65, 68, 72, 75, 79, 82 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/controller/FrontdeskOrderController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/controller/FrontdeskOrderController.java#L8) | 8, 17, 18, 24, 35, 46, 51, 63, 68, 74 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/dto/FrontdeskBeverageItemResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/dto/FrontdeskBeverageItemResponse.java#L5) | 5 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/dto/FrontdeskOrderBoardResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/dto/FrontdeskOrderBoardResponse.java#L6) | 6 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/entity/FrontdeskBeverageItem.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/entity/FrontdeskBeverageItem.java#L14) | 14, 15 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/repository/FrontdeskBeverageItemRepository.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/repository/FrontdeskBeverageItemRepository.java#L3) | 3, 9, 12, 16, 19, 23, 29, 33, 36, 40, 43, 46 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/service/FrontdeskBeverageService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/FrontdeskBeverageService.java#L3) | 3, 6, 8, 10, 12, 14, 16 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/service/OrderService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/OrderService.java#L8) | 8, 45, 54, 64 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/service/impl/FrontdeskBeverageServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/FrontdeskBeverageServiceImpl.java#L7) | 7, 8, 10, 12, 22, 31, 35, 36, 40, 46, 50, 51, 52, 61, 62, 73, 80, 81, 93, 100, 101, 107, 114, 115, 128, 133, 134, 136, 146, 147, 172, 184, 185 |
| permission_flags | [backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L33) | 33, 38, 42, 118, 120, 121, 163, 186, 210, 472, 489, 600, 618, 648, 664, 666, 670, 688, 691, 695, 704, 738, 739, 750, 775, 794, 802, 819, 823, 842, 843, 847, 851, 871, 884, 1209, 1217, 1220, 1223, 1240, 1248, 1276, 1281, 1283, 1308, 1309, 1311, 1325, 1387, 1394, 1445, 1688, 1713, 1714, 1723, 1724, 1853, 2526, 2579, 2589, 2590, 2595, 2626, 2627 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/controller/OwnerStoreMenuCloneController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/controller/OwnerStoreMenuCloneController.java#L6) | 6, 7 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/controller/OwnerStoreOnboardingController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/controller/OwnerStoreOnboardingController.java#L4) | 4, 5 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/controller/OwnerStorePart2Controller.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/controller/OwnerStorePart2Controller.java#L7) | 7, 8 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/controller/OwnerStoreProvisioningController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/controller/OwnerStoreProvisioningController.java#L7) | 7, 8 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java#L48) | 48 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java#L5) | 5, 6 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/provisioning/part2/Part2PlanNormalizer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/part2/Part2PlanNormalizer.java#L65) | 65 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/provisioning/part2/StorePart2ProvisioningWriter.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/part2/StorePart2ProvisioningWriter.java#L396) | 396, 411, 418 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/provisioning/part2/StoreReadinessServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/part2/StoreReadinessServiceImpl.java#L352) | 352, 385, 394 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/service/impl/OwnerOverviewServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/service/impl/OwnerOverviewServiceImpl.java#L6) | 6, 7, 202, 203, 205, 206, 207 |
| permission_flags | [backend/src/main/java/com/restaurant/system/owner/service/impl/OwnerStoreOnboardingServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/service/impl/OwnerStoreOnboardingServiceImpl.java#L43) | 43, 157 |
| permission_flags | [backend/src/main/java/com/restaurant/system/platform/controller/FrontdeskConfigController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/platform/controller/FrontdeskConfigController.java#L17) | 17, 18, 24 |
| permission_flags | [backend/src/main/java/com/restaurant/system/platform/controller/PlatformAdminController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/platform/controller/PlatformAdminController.java#L6) | 6, 7 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/PrintModuleCode.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/PrintModuleCode.java#L9) | 9, 17, 26 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/controller/OwnerPrintingController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/controller/OwnerPrintingController.java#L6) | 6, 7 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/controller/StoreDeviceController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/controller/StoreDeviceController.java#L5) | 5, 6 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/dto/PrintJobResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/dto/PrintJobResponse.java#L165) | 165, 166, 171, 172 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L24) | 24, 42, 207, 238, 256, 318, 410 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContext.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContext.java#L197) | 197 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java#L9) | 9 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceImpl.java#L34) | 34, 79, 81, 82, 94, 370, 382, 383, 385 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/rules/dto/PrintingDisplayRulePreviewResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/dto/PrintingDisplayRulePreviewResponse.java#L5) | 5 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/service/ManualReprintService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/ManualReprintService.java#L76) | 76, 77 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/service/StorePrintingRoleRequirementService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/StorePrintingRoleRequirementService.java#L28) | 28 |
| permission_flags | [backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java#L159) | 159, 1199, 1212, 1256, 1257, 1265, 1305, 1437, 1438, 1442, 1471, 1537 |
| permission_flags | [backend/src/main/java/com/restaurant/system/staff/service/impl/StaffAdminServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staff/service/impl/StaffAdminServiceImpl.java#L35) | 35, 37, 88, 227, 246, 247, 259, 267 |
| permission_flags | [backend/src/main/java/com/restaurant/system/staging/bootstrap/StagingSyntheticBootstrapCommand.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/bootstrap/StagingSyntheticBootstrapCommand.java#L92) | 92 |
| permission_flags | [backend/src/main/java/com/restaurant/system/staging/cleanup/StagingStoreFixtureCleanupController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/cleanup/StagingStoreFixtureCleanupController.java#L7) | 7, 8 |
| permission_flags | [backend/src/main/java/com/restaurant/system/staging/cleanup/StoreFixtureCleanupServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/cleanup/StoreFixtureCleanupServiceImpl.java#L40) | 40, 481 |
| permission_flags | [backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuCommand.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuCommand.java#L70) | 70 |
| permission_flags | [backend/src/main/resources/application-cloud.yml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/application-cloud.yml#L30) | 30, 48 |
| permission_flags | [backend/src/main/resources/application-example.yml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/application-example.yml#L29) | 29 |
| permission_flags | [backend/src/main/resources/application-pilot.yml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/application-pilot.yml#L42) | 42 |
| permission_flags | [backend/src/main/resources/application.yml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/application.yml#L48) | 48 |
| permission_flags | [backend/src/main/resources/db/migration/V13__add_store_modules.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V13__add_store_modules.sql#L61) | 61 |
| permission_flags | [backend/src/main/resources/db/migration/V14__add_store_profiles.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V14__add_store_profiles.sql#L74) | 74 |
| permission_flags | [backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L67) | 67, 69, 148, 149, 227, 229, 6697, 6826, 6827, 6833, 6834, 6997, 7004, 7055, 7056, 7058, 7059, 7064, 7069, 7073, 7080 |
| permission_flags | [backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql#L129) | 129 |
| permission_flags | [backend/src/main/resources/db/migration/V18__add_phase_b_master_menu_provisioning.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V18__add_phase_b_master_menu_provisioning.sql#L319) | 319 |
| permission_flags | [backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql#L513) | 513 |
| permission_flags | [backend/src/main/resources/db/migration/V1__baseline_current_schema.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V1__baseline_current_schema.sql#L142) | 142, 145, 164, 167, 168, 1045, 1469, 1472, 1473 |
| permission_flags | [backend/src/main/resources/hardware/hardware-capability-catalog.v1.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/hardware/hardware-capability-catalog.v1.json#L54) | 54, 55, 58, 61 |
| permission_flags | [backend/src/main/resources/module/module-catalog.v1.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/module/module-catalog.v1.json#L13) | 13, 47, 71, 72, 73, 74, 81, 82, 131, 211, 233, 247, 253, 289, 304, 308, 309, 341, 471, 524, 565, 586, 587, 589, 613, 615, 623, 631, 639, 647, 655, 663, 732, 749, 753, 754 |
| permission_flags | [backend/src/main/resources/module/module-dependency-graph.v1.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/module/module-dependency-graph.v1.json#L43) | 43, 48, 56, 59, 64 |
| permission_flags | [backend/src/test/java/com/restaurant/system/analytics/controller/AnalyticsAdminControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/analytics/controller/AnalyticsAdminControllerTest.java#L9) | 9, 10 |
| permission_flags | [backend/src/test/java/com/restaurant/system/analytics/controller/AnalyticsScopeAuthorizationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/analytics/controller/AnalyticsScopeAuthorizationTest.java#L8) | 8 |
| permission_flags | [backend/src/test/java/com/restaurant/system/common/auth/AuthorizationServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/auth/AuthorizationServiceTest.java#L71) | 71, 72, 76, 80, 81, 120, 121, 178 |
| permission_flags | [backend/src/test/java/com/restaurant/system/common/auth/OwnerOrganizationAuthorizationServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/auth/OwnerOrganizationAuthorizationServiceTest.java#L95) | 95, 96, 100 |
| permission_flags | [backend/src/test/java/com/restaurant/system/common/auth/StoreAccessServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/auth/StoreAccessServiceTest.java#L49) | 49, 60, 91, 101 |
| permission_flags | [backend/src/test/java/com/restaurant/system/common/config/ProductionSafetyConfigTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/config/ProductionSafetyConfigTest.java#L18) | 18, 42, 49, 263, 265, 291, 295, 392, 410 |
| permission_flags | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java#L67) | 67, 73, 74, 75, 163, 175, 180, 226, 624, 1509, 1577, 1583, 1593, 1597, 1849, 1991 |
| permission_flags | [backend/src/test/java/com/restaurant/system/modules/HardwareCapabilityCatalogContractTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/HardwareCapabilityCatalogContractTest.java#L20) | 20, 36 |
| permission_flags | [backend/src/test/java/com/restaurant/system/modules/ModuleCatalogContractTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/ModuleCatalogContractTest.java#L100) | 100, 119, 120, 136, 137, 138, 139, 140 |
| permission_flags | [backend/src/test/java/com/restaurant/system/modules/ModuleDependencyValidatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/ModuleDependencyValidatorTest.java#L128) | 128, 248, 250, 258 |
| permission_flags | [backend/src/test/java/com/restaurant/system/modules/StoreModuleAccessEvaluatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/StoreModuleAccessEvaluatorTest.java#L123) | 123, 212, 214, 225, 227, 228, 236, 246 |
| permission_flags | [backend/src/test/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImplTest.java#L8) | 8, 63, 64, 69, 84, 91, 109, 110, 117, 133, 134, 139, 151, 152, 157 |
| permission_flags | [backend/src/test/java/com/restaurant/system/modules/StoreModuleServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/StoreModuleServiceImplTest.java#L251) | 251, 253, 264, 266, 267, 275, 285 |
| permission_flags | [backend/src/test/java/com/restaurant/system/order/close/StoreDailyClosePostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/close/StoreDailyClosePostgresIntegrationTest.java#L168) | 168, 190 |
| permission_flags | [backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java#L41) | 41, 42, 45, 51, 96, 130, 136, 159, 178, 179, 307, 308, 315, 316, 317, 318, 327, 331, 348, 352, 652, 657, 694, 697, 700, 709, 752, 754, 764, 773, 785, 796, 808, 886 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/controller/OwnerBusinessStoreCreateControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/controller/OwnerBusinessStoreCreateControllerTest.java#L83) | 83 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreMenuCloneControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreMenuCloneControllerTest.java#L18) | 18, 19, 20, 114 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreOnboardingControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreOnboardingControllerTest.java#L18) | 18, 19, 20, 124, 133, 148 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreProvisioningControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/controller/OwnerStoreProvisioningControllerTest.java#L22) | 22, 23, 24, 214 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/profile/StDenisCanonicalProfileContractTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/profile/StDenisCanonicalProfileContractTest.java#L120) | 120 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileContractValidatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileContractValidatorTest.java#L30) | 30, 32 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImplTest.java#L13) | 13, 14, 15 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/provisioning/part2/Part2PlanNormalizerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/part2/Part2PlanNormalizerTest.java#L23) | 23, 24 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/service/impl/OnboardingStaffProvisioningServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/OnboardingStaffProvisioningServiceImplTest.java#L92) | 92, 107, 113, 119, 145, 160, 171 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerOverviewServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerOverviewServiceImplTest.java#L9) | 9 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreOnboardingServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreOnboardingServiceImplTest.java#L265) | 265, 313 |
| permission_flags | [backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreOnboardingTransactionIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/OwnerStoreOnboardingTransactionIntegrationTest.java#L101) | 101, 102, 103, 104, 105, 106, 161 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/controller/OwnerPrintingControllerDisabledStateTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/controller/OwnerPrintingControllerDisabledStateTest.java#L25) | 25, 26, 27 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/controller/StoreDeviceControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/controller/StoreDeviceControllerTest.java#L18) | 18, 19, 20 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/dto/PrintJobResponseTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/dto/PrintJobResponseTest.java#L25) | 25, 45, 48 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java#L312) | 312, 377, 378, 406, 412, 413, 428, 429, 445, 451, 457, 458, 484, 490, 496, 497, 502, 512, 518, 530, 531, 535, 539, 545, 553, 554, 558, 566, 572, 580, 581, 586, 591, 597, 605, 606, 611, 616, 622, 630, 631, 635, 639, 645, 653, 654, 726, 734, 750 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java#L420) | 420 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContextTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContextTest.java#L18) | 18, 20, 25 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleMigrationTest.java#L33) | 33 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceConflictTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceConflictTest.java#L104) | 104 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceLifecycleIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceLifecycleIntegrationTest.java#L332) | 332 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceValidationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceValidationTest.java#L62) | 62, 88, 141, 152, 154, 158, 164, 192 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/service/StorePrintingRoleRequirementServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/StorePrintingRoleRequirementServiceTest.java#L53) | 53, 54, 58 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/service/impl/OrderDispatchOutboxProcessorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/OrderDispatchOutboxProcessorTest.java#L142) | 142, 149, 150, 173, 183, 184, 198 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImplTest.java#L225) | 225, 226, 228 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImplTest.java#L102) | 102, 117, 143, 265, 352, 353, 359, 364, 366, 368, 376, 377, 419, 420, 421, 423, 435, 436, 437, 439, 914, 915, 920, 927 |
| permission_flags | [backend/src/test/java/com/restaurant/system/printing/service/impl/PrintJobServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PrintJobServiceImplTest.java#L89) | 89 |
| permission_flags | [backend/src/test/java/com/restaurant/system/staging/bootstrap/StagingSyntheticBootstrapCommandTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/bootstrap/StagingSyntheticBootstrapCommandTest.java#L38) | 38 |
| permission_flags | [backend/src/test/java/com/restaurant/system/staging/cleanup/StagingStoreFixtureCleanupControllerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/cleanup/StagingStoreFixtureCleanupControllerTest.java#L15) | 15 |
| permission_flags | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuCommandTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuCommandTest.java#L34) | 34 |
| permission_flags | [deployment/cloud/.env.staging.example](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/.env.staging.example#L42) | 42, 50 |
| permission_flags | [deployment/cloud/FINAL_SMOKE_TEST_CHECKLIST.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/FINAL_SMOKE_TEST_CHECKLIST.md#L64) | 64, 83, 103, 104, 127 |
| permission_flags | [deployment/cloud/README_CLOUD_DEPLOYMENT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_CLOUD_DEPLOYMENT.md#L217) | 217 |
| permission_flags | [deployment/cloud/README_PHASE_B_PART2_STAGING_ACCEPTANCE.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_PHASE_B_PART2_STAGING_ACCEPTANCE.md#L20) | 20 |
| permission_flags | [deployment/cloud/README_PRODUCTION_V26_RELEASE.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_PRODUCTION_V26_RELEASE.md#L57) | 57 |
| permission_flags | [deployment/cloud/README_ROLLBACK.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_ROLLBACK.md#L37) | 37 |
| permission_flags | [deployment/cloud/README_STAGING.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_STAGING.md#L74) | 74, 81, 83, 91, 93 |
| permission_flags | [deployment/cloud/README_TWIN001_STAGING_RECONSTRUCTION.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_TWIN001_STAGING_RECONSTRUCTION.md#L94) | 94 |
| permission_flags | [deployment/cloud/STAGING_SMOKE_TEST_CHECKLIST.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/STAGING_SMOKE_TEST_CHECKLIST.md#L61) | 61 |
| permission_flags | [deployment/cloud/application-cloud.yml.example](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/application-cloud.yml.example#L22) | 22, 24, 27 |
| permission_flags | [deployment/cloud/docker-compose.staging.yml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/docker-compose.staging.yml#L50) | 50, 51 |
| permission_flags | [deployment/cloud/final-channels-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/apply-menu-once.py#L20) | 20 |
| permission_flags | [deployment/cloud/final-channels-20261003/backfill-financial.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/backfill-financial.py#L24) | 24 |
| permission_flags | [deployment/cloud/ops001-jq-compat.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-jq-compat.py#L147) | 147, 194, 313 |
| permission_flags | [deployment/cloud/production-v26-app-patch-evidence.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-app-patch-evidence.py#L13) | 13 |
| permission_flags | [deployment/cloud/production-v26-smoke.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-smoke.py#L351) | 351, 354, 420, 422, 631, 740, 771 |
| permission_flags | [deployment/cloud/staging-deploy.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-deploy.sh#L39) | 39, 41, 46, 561, 566, 574, 577, 580, 653, 654 |
| permission_flags | [deployment/cloud/staging-local-rehearsal.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-local-rehearsal.sh#L240) | 240, 241, 270, 271, 373, 374 |
| permission_flags | [deployment/cloud/staging-ops-common.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ops-common.sh#L113) | 113 |
| permission_flags | [deployment/cloud/staging-owner-acceptance-client.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-owner-acceptance-client.sh#L91) | 91, 192, 386, 387, 428 |
| permission_flags | [deployment/cloud/staging-pad-direct/README.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-pad-direct/README.md#L70) | 70 |
| permission_flags | [deployment/cloud/staging-pad-direct/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-pad-direct/apply-once.py#L27) | 27 |
| permission_flags | [deployment/cloud/staging-phase-b-part1-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-phase-b-part1-acceptance.sh#L477) | 477, 479, 481, 487 |
| permission_flags | [deployment/cloud/staging-phase-b-part2-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-phase-b-part2-acceptance.sh#L388) | 388 |
| permission_flags | [deployment/cloud/staging-synthetic-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-synthetic-acceptance.sh#L191) | 191, 751, 778 |
| permission_flags | [deployment/cloud/tests/test_staging_guard.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_guard.sh#L191) | 191, 192, 270, 273, 294, 296, 299, 300, 474, 478, 570, 571, 579, 596 |
| permission_flags | [deployment/cloud/tests/test_staging_local_rehearsal.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_local_rehearsal.sh#L141) | 141, 142 |
| permission_flags | [deployment/cloud/tests/test_staging_owner_acceptance_client.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_owner_acceptance_client.sh#L131) | 131 |
| permission_flags | [deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_phase_b_part1_acceptance.sh#L65) | 65, 128, 129, 130, 131, 134 |
| permission_flags | [deployment/cloud/tests/test_staging_release_rotation.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_release_rotation.sh#L71) | 71 |
| permission_flags | [deployment/cloud/tests/test_staging_server_preflight.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_server_preflight.sh#L98) | 98, 99, 202, 203 |
| permission_flags | [deployment/windows-pilot/README_WINDOWS_PILOT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/README_WINDOWS_PILOT.md#L159) | 159, 178, 183 |
| permission_flags | [deployment/windows-pilot/package/README_WINDOWS_PILOT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/package/README_WINDOWS_PILOT.md#L159) | 159, 178, 183 |
| permission_flags | [deployment/windows-pilot/package/config/application-pilot.yml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/package/config/application-pilot.yml#L31) | 31 |
| permission_flags | [frontend/src/App.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/App.tsx#L3) | 3, 4, 11, 39, 41, 45, 113, 146, 147, 148, 220, 232, 233, 255, 256, 266, 315, 316, 319, 320 |
| permission_flags | [frontend/src/features/admin/PlatformAdminPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/admin/PlatformAdminPage.tsx#L7) | 7, 8, 42, 233, 247, 250 |
| permission_flags | [frontend/src/features/auth/AccessDeniedPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/auth/AccessDeniedPage.tsx#L1) | 1, 21, 24 |
| permission_flags | [frontend/src/features/auth/AuthProvider.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/auth/AuthProvider.tsx#L164) | 164 |
| permission_flags | [frontend/src/features/auth/RequireAuth.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/auth/RequireAuth.tsx#L2) | 2, 6, 10, 11 |
| permission_flags | [frontend/src/features/auth/useAuth.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/auth/useAuth.ts#L19) | 19 |
| permission_flags | [frontend/src/features/dev/DevRoleSwitcher.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/dev/DevRoleSwitcher.tsx#L6) | 6 |
| permission_flags | [frontend/src/features/dinein/DineInPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/dinein/DineInPage.tsx#L8) | 8, 10, 13, 14, 15, 54, 55, 60, 61, 162, 219, 575 |
| permission_flags | [frontend/src/features/dinein/components/DineInSidebar.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/dinein/components/DineInSidebar.tsx#L1) | 1, 33, 54, 78, 87, 91, 95 |
| permission_flags | [frontend/src/features/feature-flags/featureConfig.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/feature-flags/featureConfig.ts#L30) | 30, 31, 32 |
| permission_flags | [frontend/src/features/frontdesk/components/FrontdeskTopNav.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/frontdesk/components/FrontdeskTopNav.tsx#L18) | 18, 34, 219, 241, 249, 357 |
| permission_flags | [frontend/src/features/frontdesk/components/PrintWorkerHealthBanner.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/frontdesk/components/PrintWorkerHealthBanner.tsx#L122) | 122 |
| permission_flags | [frontend/src/features/frontdesk/navigation.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/frontdesk/navigation.ts#L14) | 14, 16, 34, 37, 38, 43, 61 |
| permission_flags | [frontend/src/features/ordering/OrderingPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/OrderingPage.tsx#L8) | 8, 9, 496 |
| permission_flags | [frontend/src/features/orders/OrdersPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/orders/OrdersPage.tsx#L12) | 12, 14, 24, 114 |
| permission_flags | [frontend/src/features/orders/components/OrderDetailPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/orders/components/OrderDetailPanel.tsx#L29) | 29, 567, 570 |
| permission_flags | [frontend/src/features/orders/components/OrderMiniCard.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/orders/components/OrderMiniCard.tsx#L2) | 2, 5, 11 |
| permission_flags | [frontend/src/features/owner-admin/DiningTablesManagementPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/DiningTablesManagementPage.tsx#L203) | 203, 453 |
| permission_flags | [frontend/src/features/owner-admin/MenuManagementPage.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuManagementPage.test.tsx#L16) | 16 |
| permission_flags | [frontend/src/features/owner-admin/MenuManagementPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuManagementPage.tsx#L130) | 130, 405 |
| permission_flags | [frontend/src/features/owner-admin/OwnerAdminShell.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/OwnerAdminShell.tsx#L3) | 3, 20, 26, 28, 36, 58, 60, 63, 64, 65, 67, 74, 87, 96, 119, 205 |
| permission_flags | [frontend/src/features/owner-admin/PrintingDisplayRulesPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PrintingDisplayRulesPanel.tsx#L63) | 63, 68, 81, 666, 667, 670, 820 |
| permission_flags | [frontend/src/features/owner-admin/PrintingSettingsPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PrintingSettingsPage.tsx#L3) | 3, 54, 1318, 1427, 1908, 1943 |
| permission_flags | [frontend/src/features/owner-admin/StaffManagementPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/StaffManagementPage.tsx#L19) | 19, 39, 139, 212 |
| permission_flags | [frontend/src/features/owner-home/OwnerDashboardPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-home/OwnerDashboardPage.tsx#L3) | 3, 195, 196, 197, 198, 199, 210, 214, 215, 216, 217, 218, 219, 418 |
| permission_flags | [frontend/src/features/pickup/PickupBoardPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/pickup/PickupBoardPage.tsx#L4) | 4, 16 |
| permission_flags | [frontend/src/features/reports/components/OwnerAdminReportsShell.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/OwnerAdminReportsShell.tsx#L2) | 2 |
| permission_flags | [frontend/src/features/store/StoreSwitcher.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/store/StoreSwitcher.tsx#L2) | 2 |
| permission_flags | [frontend/src/features/store/storeModuleAccess.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/store/storeModuleAccess.test.ts#L58) | 58, 68, 104, 105, 134, 135 |
| permission_flags | [frontend/src/features/store/storeModuleAccess.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/store/storeModuleAccess.ts#L189) | 189, 219, 223, 224, 225 |
| permission_flags | [frontend/src/features/store/storeRoutes.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/store/storeRoutes.ts#L46) | 46, 47, 80, 83 |
| permission_flags | [frontend/src/features/uber-eats/UberInboxBadge.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/uber-eats/UberInboxBadge.tsx#L3) | 3, 7 |
| permission_flags | [frontend/src/features/uber-eats/UberInboxPage.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/uber-eats/UberInboxPage.test.tsx#L7) | 7 |
| permission_flags | [frontend/src/features/uber-eats/UberInboxPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/uber-eats/UberInboxPage.tsx#L3) | 3, 52 |
| permission_flags | [frontend/src/features/uber-eats/UberStoreCapability.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/uber-eats/UberStoreCapability.test.tsx#L5) | 5, 32, 38, 62, 81, 82 |
| permission_flags | [frontend/src/features/uber-eats/useUberInbox.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/uber-eats/useUberInbox.test.tsx#L6) | 6, 8, 23 |
| permission_flags | [frontend/src/features/uber-eats/useUberInbox.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/uber-eats/useUberInbox.ts#L3) | 3, 20 |
| permission_flags | [frontend/src/hooks/useTableBoard.hook.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useTableBoard.hook.test.tsx#L4) | 4, 5, 8, 14, 19 |
| permission_flags | [frontend/src/hooks/useTableBoard.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useTableBoard.test.ts#L17) | 17 |
| permission_flags | [frontend/src/hooks/useTableBoard.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useTableBoard.ts#L2) | 2, 5, 6, 11, 12, 13, 121, 351, 362, 363, 374, 385 |
| permission_flags | [frontend/src/offline/workspaceSnapshot.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/workspaceSnapshot.test.ts#L14) | 14, 15, 18, 19 |
| permission_flags | [frontend/src/pages/Home.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/pages/Home.tsx#L1) | 1, 10, 12, 13, 14, 15, 64 |
| permission_flags | [frontend/src/services/apiClient.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/apiClient.test.ts#L27) | 27 |
| permission_flags | [frontend/src/services/apiClient.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/apiClient.ts#L102) | 102 |
| permission_flags | [frontend/src/services/frontdeskConfigService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/frontdeskConfigService.ts#L9) | 9 |
| permission_flags | [frontend/src/services/orderService.realtime.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderService.realtime.test.ts#L2) | 2, 42, 47, 53, 63, 84, 105 |
| permission_flags | [frontend/src/services/orderService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderService.test.ts#L209) | 209, 218, 221, 250 |
| permission_flags | [frontend/src/services/orderService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderService.ts#L2) | 2, 36, 42, 54, 56, 61, 62, 271, 277, 285, 288, 296, 299, 314, 318, 325, 328, 333, 335, 343, 357, 739 |
| permission_flags | [frontend/src/services/printingAdminService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/printingAdminService.ts#L218) | 218 |
| permission_flags | [frontend/src/services/staffAdminService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/staffAdminService.ts#L15) | 15 |
| permission_flags | [frontend/src/types/ordering.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/types/ordering.ts#L268) | 268 |
| permission_flags | [frontend/src/utils/appCacheVersion.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/appCacheVersion.ts#L9) | 9 |
| permission_flags | [frontend/src/utils/displayLabels.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/displayLabels.ts#L12) | 12, 22, 23 |
| permission_flags | [restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L541) | 541, 542 |
| permission_flags | [restaurant-pad-app/docs/API_BASE_CONFIG.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/API_BASE_CONFIG.md#L82) | 82, 95, 96, 102, 130 |
| permission_flags | [restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/BUNDLED_ASSETS_PRODUCTION.md#L83) | 83 |
| permission_flags | [restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/LOCAL_DEVELOPMENT.md#L104) | 104, 124 |
| permission_flags | [restaurant-pad-app/docs/PAD_DIRECT_MULTI_PRINTER_ROUTING.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PAD_DIRECT_MULTI_PRINTER_ROUTING.md#L12) | 12 |
| permission_flags | [restaurant-pad-app/docs/PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md#L14) | 14 |
| permission_flags | [restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md#L108) | 108, 116, 144 |
| permission_flags | [restaurant-pad-app/docs/WEBVIEW_ROUTING.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/WEBVIEW_ROUTING.md#L6) | 6, 7 |
| station | [backend/src/main/java/com/restaurant/system/analytics/dto/ChannelSalesSummary.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/dto/ChannelSalesSummary.java#L12) | 12 |
| station | [backend/src/main/java/com/restaurant/system/analytics/service/ChannelSalesService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/service/ChannelSalesService.java#L80) | 80, 81, 82, 141, 144 |
| station | [backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java#L19) | 19, 22, 23, 24, 44, 45, 46 |
| station | [backend/src/main/java/com/restaurant/system/common/auth/Capability.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/Capability.java#L23) | 23 |
| station | [backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java#L15) | 15, 16, 68, 74, 75 |
| station | [backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L62) | 62, 240, 241, 284, 422, 425, 432, 433, 434, 442, 443, 444, 449, 452, 455, 456, 457, 458, 460, 461, 462, 463, 465, 466, 467, 474, 475, 476, 477, 489, 490, 491, 492, 497, 498, 499, 522, 523, 524, 525, 526, 527, 528, 529, 530, 531, 532, 533, 534, 555, 559, 562, 577, 578, 586, 590, 595, 605, 606, 617, 639, 642, 661, 664, 685, 688, 729, 731, 732, 733, 734, 735, 736, 737, 781, 782, 816, 817, 818, 819, 820, 821, 822, 827, 847, 848, 905, 912, 925, 930, 1305, 1321, 1322, 1330, 1438, 1439, 1440, 1445, 1447, 1455, 1456, 1457 |
| station | [backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java#L7) | 7, 8 |
| station | [backend/src/main/java/com/restaurant/system/dev/DevTestUser.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/dev/DevTestUser.java#L15) | 15 |
| station | [backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsInboxController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/controller/UberEatsInboxController.java#L78) | 78, 79, 260 |
| station | [backend/src/main/java/com/restaurant/system/integration/ubereats/mapping/UberEatsMenuMappingService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/mapping/UberEatsMenuMappingService.java#L147) | 147, 352, 453 |
| station | [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsKitchenView.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberEatsKitchenView.java#L17) | 17, 35, 36, 40, 55, 63 |
| station | [backend/src/main/java/com/restaurant/system/kitchen/controller/KdsController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/controller/KdsController.java#L37) | 37, 38, 42, 44 |
| station | [backend/src/main/java/com/restaurant/system/kitchen/dto/KdsTaskDisplayResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/dto/KdsTaskDisplayResponse.java#L24) | 24 |
| station | [backend/src/main/java/com/restaurant/system/kitchen/service/KdsService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/service/KdsService.java#L11) | 11 |
| station | [backend/src/main/java/com/restaurant/system/kitchen/service/impl/KdsServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/service/impl/KdsServiceImpl.java#L33) | 33, 36, 39, 40, 66, 232 |
| station | [backend/src/main/java/com/restaurant/system/kitchen/service/impl/KitchenServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/kitchen/service/impl/KitchenServiceImpl.java#L161) | 161, 162 |
| station | [backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java#L231) | 231 |
| station | [backend/src/main/java/com/restaurant/system/modules/HardwareCapabilityKeys.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/modules/HardwareCapabilityKeys.java#L6) | 6, 8 |
| station | [backend/src/main/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImpl.java#L109) | 109, 118, 119 |
| station | [backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L107) | 107, 129, 487, 490, 491, 612, 623, 625, 1550, 2002, 2011, 2012, 2013, 2024, 2029, 2043, 2044, 2059, 2064, 2065, 2070, 2071, 2072, 2127, 2145, 2146, 2147, 2151, 2156, 2160, 2161, 2170, 2178, 2182, 2185, 2186, 2187, 2188, 2189, 2190, 2191, 2203, 2221, 2230, 2628, 2629 |
| station | [backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java#L5) | 5, 33, 34, 37, 54, 56, 65, 66, 67, 90, 91, 92, 93, 94, 95, 96, 119, 124, 146, 150, 151, 152, 153, 154, 155, 156, 157, 179, 180, 181, 182, 222, 223, 228, 229, 313, 321, 426, 464, 465 |
| station | [backend/src/main/java/com/restaurant/system/owner/menu/StoreMenuCloneBaseGraphProfile.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/StoreMenuCloneBaseGraphProfile.java#L77) | 77 |
| station | [backend/src/main/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposer.java#L67) | 67, 68, 70, 71, 79, 148, 155, 157, 158, 161, 162, 168, 169, 172, 173, 176, 178, 179, 180, 182, 189, 192, 194, 265, 266, 267, 268, 280, 282, 346 |
| station | [backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java#L47) | 47 |
| station | [backend/src/main/java/com/restaurant/system/owner/provisioning/part2/StorePart2ProvisioningWriter.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/part2/StorePart2ProvisioningWriter.java#L417) | 417 |
| station | [backend/src/main/java/com/restaurant/system/owner/provisioning/part2/StoreReadinessServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/part2/StoreReadinessServiceImpl.java#L391) | 391, 411, 413 |
| station | [backend/src/main/java/com/restaurant/system/platform/dto/OwnerDashboardResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/platform/dto/OwnerDashboardResponse.java#L22) | 22 |
| station | [backend/src/main/java/com/restaurant/system/platform/service/impl/OwnerDashboardServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/platform/service/impl/OwnerDashboardServiceImpl.java#L119) | 119, 156, 159, 163, 165, 166, 167 |
| station | [backend/src/main/java/com/restaurant/system/printing/PrintModuleCode.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/PrintModuleCode.java#L8) | 8, 10, 16, 18, 25 |
| station | [backend/src/main/java/com/restaurant/system/printing/controller/OwnerPrintingController.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/controller/OwnerPrintingController.java#L14) | 14, 15, 344, 345, 348, 349 |
| station | [backend/src/main/java/com/restaurant/system/printing/dto/GrabFontTestRequest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/dto/GrabFontTestRequest.java#L5) | 5 |
| station | [backend/src/main/java/com/restaurant/system/printing/dto/GrabFontTestResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/dto/GrabFontTestResponse.java#L6) | 6, 9, 11 |
| station | [backend/src/main/java/com/restaurant/system/printing/dto/PrintJobResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/dto/PrintJobResponse.java#L162) | 162, 168 |
| station | [backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L30) | 30, 37, 87, 88, 89, 161, 163, 166, 168, 177, 198, 200, 302, 304 |
| station | [backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java#L24) | 24, 26, 32, 70, 82, 83, 113, 114, 115, 121, 124, 125, 178, 181, 187, 188, 269, 272, 296, 301, 304, 307, 308, 317, 549, 550, 551, 719, 720, 721 |
| station | [backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java#L31) | 31, 45, 105, 106, 140, 141, 143, 147, 164, 169, 179, 202, 209, 222, 223, 261, 283, 288, 289, 294, 295, 296, 305, 306, 307, 311, 316, 320, 321, 330, 338, 342, 347, 348, 349, 350, 351, 352, 353, 365, 383, 392, 457, 472, 473, 477, 480, 481, 483, 484, 485, 486, 623, 626 |
| station | [backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java#L15) | 15, 17, 18, 19, 20, 21, 22, 26, 29, 31, 35, 48, 51, 75, 78, 94, 98, 104, 105, 107, 261, 264 |
| station | [backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContext.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContext.java#L197) | 197 |
| station | [backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java#L9) | 9 |
| station | [backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceImpl.java#L33) | 33, 35, 39, 52, 78, 80, 90, 91, 359, 361, 363, 368, 369, 371, 372, 373, 374, 375, 376, 380, 381 |
| station | [backend/src/main/java/com/restaurant/system/printing/rules/dto/PrintingDisplayRulePreviewRequest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/dto/PrintingDisplayRulePreviewRequest.java#L13) | 13 |
| station | [backend/src/main/java/com/restaurant/system/printing/rules/dto/PrintingDisplayRulePreviewResponse.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/dto/PrintingDisplayRulePreviewResponse.java#L4) | 4, 6 |
| station | [backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java#L22) | 22, 23, 94, 99, 109, 110, 113 |
| station | [backend/src/main/java/com/restaurant/system/printing/semantic/KitchenModifierTokenResolver.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/KitchenModifierTokenResolver.java#L104) | 104, 136, 137, 193 |
| station | [backend/src/main/java/com/restaurant/system/printing/service/ManualReprintService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/ManualReprintService.java#L76) | 76, 77, 79, 80 |
| station | [backend/src/main/java/com/restaurant/system/printing/service/PrintDispatcherService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/PrintDispatcherService.java#L7) | 7, 8, 39 |
| station | [backend/src/main/java/com/restaurant/system/printing/service/StorePrintingRoleRequirementService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/StorePrintingRoleRequirementService.java#L27) | 27, 29 |
| station | [backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java#L27) | 27, 28, 159, 209, 218, 353, 361, 367, 372, 384, 386, 388, 399, 405, 607, 611, 620, 826, 827, 909, 910, 976, 1014, 1211, 1213, 1259, 1260, 1262, 1263, 1265, 1304, 1306, 1345, 1356, 1416, 1419, 1444, 1445, 1449, 1451, 1452, 1486, 1512, 1513, 1514, 1515, 1516, 1517, 1518, 1532, 1541, 1547, 1573, 1574, 1575, 1576, 1577, 1578, 1579, 1580, 1581, 1609, 1610, 1611, 1612, 1613, 1614, 1615, 1616, 1617, 1618, 1619, 1620, 1623, 1626, 1628, 1647, 1651, 1652, 1653, 1654, 1655, 1656, 1657, 1658, 1659, 1662, 1683, 1684, 1685, 1686, 1687, 1688, 1689, 1690, 1691, 1692, 1693, 1694, 1697, 1700, 1702 |
| station | [backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactory.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactory.java#L21) | 21, 22, 23, 24, 25, 26, 31, 32, 37, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 75, 77, 78, 82, 85 |
| station | [backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestValidator.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestValidator.java#L264) | 264, 265, 266 |
| station | [backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L316) | 316, 318, 324, 326, 332, 334, 377, 383, 396, 399, 412, 418, 431, 437, 450, 456, 901, 904, 917, 920, 933, 936, 1144, 1145, 1147, 1158, 1159, 1161, 1172, 1173, 1175, 1186, 1187, 1189, 1200, 1201, 1203, 1214, 1215, 1217, 1228, 1229, 1231, 1324, 1326, 1450, 1452, 1464, 1466, 1623, 1637, 1802, 1803, 1805, 1816, 1817, 1819, 1830, 1831, 1833, 1844, 1845, 1847, 1858, 1859, 1861, 1872, 1873, 1875, 1886, 1887, 1889, 1954, 1956, 2108, 2110, 2122, 2124, 2362, 2363, 2365, 2376, 2377, 2379, 2390, 2391, 2393, 2404, 2405, 2407, 2418, 2419, 2421, 2432, 2433, 2435, 2446, 2447, 2449, 2514, 2516, 2668, 2670, 2682, 2684, 4252, 4253, 4255, 4280, 4281, 4283, 4294, 4295, 4297, 4322, 4323, 4325, 4364, 4365, 4367, 4378, 4379, 4381, 4406, 4407, 4409, 4474, 4476, 4798, 4799, 4801, 4812, 4813, 4815, 4826, 4827, 4829, 4840, 4841, 4843, 4854, 4855, 4857, 4868, 4869, 4871, 4882, 4883, 4885, 4950, 4952, 5302, 5303, 5305, 5316, 5317, 5319, 5330, 5331, 5333, 5344, 5345, 5347, 5358, 5359, 5361, 5372, 5373, 5375, 5386, 5387, 5389, 5454, 5456, 5862, 5863, 5865, 5876, 5877, 5879, 5890, 5891, 5893, 5904, 5905, 5907, 5918, 5919, 5921, 5932, 5933, 5935, 5946, 5947, 5949, 6042, 6044, 6182, 6184, 6196, 6198, 6644, 6665, 6704, 6711, 6735, 7008, 7011, 7015, 7018, 7022, 7025 |
| station | [backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V17__add_printing_display_rules.sql#L129) | 129 |
| station | [backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql#L513) | 513 |
| station | [backend/src/main/resources/db/migration/V5__set_cold_chicken_noodle_default_type.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V5__set_cold_chicken_noodle_default_type.sql#L9) | 9, 10, 19, 20, 25, 27, 28, 48, 49, 51, 52 |
| station | [backend/src/main/resources/hardware/hardware-capability-catalog.v1.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/hardware/hardware-capability-catalog.v1.json#L42) | 42, 43, 46, 49, 67, 71, 74 |
| station | [backend/src/main/resources/module/module-catalog.v1.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/module/module-catalog.v1.json#L12) | 12, 232, 234, 252, 254, 459, 460, 479, 481, 496, 588, 731, 733 |
| station | [backend/src/main/resources/module/module-dependency-graph.v1.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/module/module-dependency-graph.v1.json#L63) | 63, 65 |
| station | [backend/src/test/java/com/restaurant/system/analytics/service/ChannelSalesServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/analytics/service/ChannelSalesServiceTest.java#L34) | 34, 35, 42, 43, 44, 45, 50, 52, 53, 55, 58, 62, 81, 86, 87, 88, 92, 95, 96, 97 |
| station | [backend/src/test/java/com/restaurant/system/analytics/support/SalesReportingTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/analytics/support/SalesReportingTest.java#L15) | 15, 16 |
| station | [backend/src/test/java/com/restaurant/system/common/auth/AuthorizationServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/auth/AuthorizationServiceTest.java#L93) | 93, 99, 100, 102 |
| station | [backend/src/test/java/com/restaurant/system/common/config/StagingForwardedOriginTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/common/config/StagingForwardedOriginTest.java#L39) | 39 |
| station | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java#L24) | 24, 110, 184, 190, 196, 197, 204, 226, 535, 738, 748, 749, 750, 797, 897, 909, 940, 1040, 1289, 1354, 1372, 1384, 1389, 1392, 1396, 1418, 1420, 1421, 1422, 1444, 1461, 1501, 1556, 1560, 1565, 1570, 1579, 1588, 1606, 1610, 1617, 1680, 1804, 1855, 1856, 1857, 1858, 1859, 1860, 1861, 1862, 1863, 1865, 1866, 1870, 1871, 1873, 1874, 1879, 1885, 1886, 2076, 2347, 2384, 2385, 2386 |
| station | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSalesSourceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSalesSourceTest.java#L36) | 36, 37, 38, 49 |
| station | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSnapshotTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSnapshotTest.java#L17) | 17, 58 |
| station | [backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonServiceIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonServiceIntegrationTest.java#L227) | 227, 425 |
| station | [backend/src/test/java/com/restaurant/system/menu/service/impl/MenuCatalogHashServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/MenuCatalogHashServiceTest.java#L93) | 93, 94, 95, 104, 106 |
| station | [backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuStructureServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuStructureServiceImplTest.java#L89) | 89, 120, 122, 134 |
| station | [backend/src/test/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImplTest.java#L291) | 291, 301 |
| station | [backend/src/test/java/com/restaurant/system/modules/HardwareCapabilityCatalogContractTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/HardwareCapabilityCatalogContractTest.java#L19) | 19, 21, 35, 37 |
| station | [backend/src/test/java/com/restaurant/system/modules/ModuleCatalogContractTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/ModuleCatalogContractTest.java#L99) | 99 |
| station | [backend/src/test/java/com/restaurant/system/modules/ModuleDependencyValidatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/ModuleDependencyValidatorTest.java#L140) | 140, 257, 259 |
| station | [backend/src/test/java/com/restaurant/system/modules/StoreModuleAccessEvaluatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/StoreModuleAccessEvaluatorTest.java#L122) | 122, 132, 235, 237, 245, 247 |
| station | [backend/src/test/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/StoreModuleCapabilityProviderImplTest.java#L61) | 61, 62, 65, 66, 68, 70, 83, 85, 90, 92, 105, 106, 107, 108, 112, 113, 114, 128, 131, 132, 135, 136, 138, 140, 149, 150, 153, 154, 156, 158, 159, 160, 168 |
| station | [backend/src/test/java/com/restaurant/system/modules/StoreModuleServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/StoreModuleServiceImplTest.java#L94) | 94, 96, 274, 276, 284, 286 |
| station | [backend/src/test/java/com/restaurant/system/modules/UberStoreModuleMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/modules/UberStoreModuleMigrationTest.java#L28) | 28 |
| station | [backend/src/test/java/com/restaurant/system/order/close/StoreDailyClosePostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/close/StoreDailyClosePostgresIntegrationTest.java#L65) | 65 |
| station | [backend/src/test/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImplTest.java#L102) | 102, 104, 396, 398, 400, 401 |
| station | [backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java#L192) | 192, 197, 206, 207, 495, 606, 608, 627, 633, 651, 653, 726, 727, 728, 735, 884, 892, 921, 1272, 1273, 1275, 1276 |
| station | [backend/src/test/java/com/restaurant/system/order/service/impl/OrderSubmissionHashServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/service/impl/OrderSubmissionHashServiceImplTest.java#L44) | 44, 46, 48 |
| station | [backend/src/test/java/com/restaurant/system/owner/menu/StoreMenuCloneSourceOptionsComposerIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/StoreMenuCloneSourceOptionsComposerIntegrationTest.java#L71) | 71, 96, 142, 169, 170, 240, 242, 252, 259, 260 |
| station | [backend/src/test/java/com/restaurant/system/owner/menu/StoreMenuCloneSourceOptionsComposerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/StoreMenuCloneSourceOptionsComposerTest.java#L36) | 36, 42, 43, 53, 74, 78, 79, 105, 122, 123, 131, 139, 144, 145, 154, 164, 167, 229, 230, 408, 460 |
| station | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneProfileTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneProfileTest.java#L28) | 28, 29, 35, 45, 46, 47, 48, 49, 82, 83, 85, 92, 102, 103, 104, 105, 107, 114, 117, 118, 123, 124, 125, 126, 129 |
| station | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneTransactionIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuCloneTransactionIntegrationTest.java#L173) | 173, 293, 294, 297, 301, 302, 303, 304, 305, 306, 318, 320, 321, 324, 325 |
| station | [backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/menu/profile/ChinatownMenuProfileOverridesComposerTest.java#L37) | 37, 40, 49, 62, 69, 76, 95, 96, 97, 103, 104, 113, 118, 146, 181, 183, 184, 185, 186, 187, 196, 210, 212, 215, 232, 234 |
| station | [backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningTransactionIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningTransactionIntegrationTest.java#L247) | 247 |
| station | [backend/src/test/java/com/restaurant/system/owner/provisioning/ProvisionedStandardSizePlanTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/ProvisionedStandardSizePlanTest.java#L15) | 15, 16, 24, 25, 45, 46, 54, 55, 98, 99 |
| station | [backend/src/test/java/com/restaurant/system/owner/provisioning/part2/Part2PlanNormalizerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/provisioning/part2/Part2PlanNormalizerTest.java#L40) | 40, 41 |
| station | [backend/src/test/java/com/restaurant/system/owner/service/impl/StoreMenuCloneTransactionServiceImplIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/owner/service/impl/StoreMenuCloneTransactionServiceImplIntegrationTest.java#L734) | 734, 746 |
| station | [backend/src/test/java/com/restaurant/system/platform/service/OwnerDashboardReportingTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/platform/service/OwnerDashboardReportingTest.java#L68) | 68, 69, 75, 76, 92, 93, 97, 98, 99, 111, 116, 123, 124 |
| station | [backend/src/test/java/com/restaurant/system/printing/ManualReprintServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/ManualReprintServiceTest.java#L29) | 29, 54, 57, 78, 79 |
| station | [backend/src/test/java/com/restaurant/system/printing/PrintingReliabilityPostgresMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/PrintingReliabilityPostgresMigrationTest.java#L25) | 25 |
| station | [backend/src/test/java/com/restaurant/system/printing/PrintingReliabilityRepositoryTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/PrintingReliabilityRepositoryTest.java#L40) | 40, 50, 75 |
| station | [backend/src/test/java/com/restaurant/system/printing/controller/OwnerPrintingControllerDisabledStateTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/controller/OwnerPrintingControllerDisabledStateTest.java#L206) | 206, 242, 246, 293 |
| station | [backend/src/test/java/com/restaurant/system/printing/dto/PrintJobResponseTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/dto/PrintJobResponseTest.java#L15) | 15, 35, 55, 69 |
| station | [backend/src/test/java/com/restaurant/system/printing/renderer/ExternalOrderNoteRenderingTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/ExternalOrderNoteRenderingTest.java#L28) | 28, 46, 57, 73, 83, 88, 96, 108, 135 |
| station | [backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java#L19) | 19, 21, 109, 110, 117, 125, 133, 142, 150, 158, 167, 175, 182, 194, 203, 204, 205, 206, 207, 208, 213, 221, 228, 235, 236, 237, 238, 246, 247, 248, 249, 250, 257, 258, 266, 267, 274, 275, 276, 277, 284, 285, 286, 287, 297, 298, 299, 300, 312, 314, 317, 324, 358, 371, 372, 373, 377, 386, 457, 477, 496, 502, 530, 535, 558, 586, 605, 611, 612, 624, 635, 662, 750, 754, 787, 809, 848, 856, 857, 860, 861, 864, 872, 881, 889, 897, 902, 904, 906, 908, 909, 910, 915, 919, 921, 923, 924, 929, 967, 968, 971, 972, 1033 |
| station | [backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java#L27) | 27, 145, 189, 191, 192, 193, 203, 205, 206, 207, 208, 217, 219, 220, 230, 232, 241, 243, 244, 245, 254, 256, 257, 258, 270, 272, 273, 274, 292, 294, 310, 318, 335, 350, 358, 365, 366, 381, 382, 391, 392, 393, 394, 416, 420, 422, 426, 428, 429, 432, 518, 519, 534, 536, 550, 560, 562, 564, 566, 567, 568, 569, 573, 580, 584, 587, 589, 590, 592, 605, 606, 609, 610, 613, 614, 641 |
| station | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContextTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleContextTest.java#L14) | 14, 16, 18, 23, 37, 40, 41 |
| station | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleMigrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleMigrationTest.java#L34) | 34 |
| station | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceConflictTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceConflictTest.java#L104) | 104, 107 |
| station | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceLifecycleIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceLifecycleIntegrationTest.java#L307) | 307, 308, 332, 336 |
| station | [backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceValidationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/rules/PrintingDisplayRuleServiceValidationTest.java#L62) | 62, 64, 88, 91, 99, 131, 134, 140, 142, 152, 154, 158, 160, 161, 164, 172, 175, 180, 181, 182, 192, 196, 209, 210 |
| station | [backend/src/test/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityServiceTest.java#L42) | 42, 43, 48, 58, 62, 63, 67, 70, 77, 84, 89, 94, 97, 120, 142, 145 |
| station | [backend/src/test/java/com/restaurant/system/printing/service/StorePrintingRoleRequirementServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/StorePrintingRoleRequirementServiceTest.java#L48) | 48, 49, 50, 51, 52, 56, 57, 65, 67, 69, 70, 72, 75, 76, 77, 78, 83, 85, 88, 89, 96, 98, 100, 103 |
| station | [backend/src/test/java/com/restaurant/system/printing/service/impl/OrderDispatchOutboxProcessorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/OrderDispatchOutboxProcessorTest.java#L69) | 69, 76, 122, 123, 129, 135, 136, 141, 147, 148, 154, 172, 181, 182, 192, 229, 235, 236, 242, 247, 253, 254, 255, 261 |
| station | [backend/src/test/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImplTest.java#L210) | 210, 211, 213, 215, 235, 237 |
| station | [backend/src/test/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImplTest.java#L100) | 100, 116, 118, 143, 206, 213, 215, 226, 230, 241, 249, 255, 262, 264, 281, 290, 293, 299, 303, 312, 313, 321, 335, 344, 345, 369, 384, 397, 404, 406, 408, 452, 453, 460, 462, 491, 499, 501, 504, 505, 509, 513, 517, 534, 535, 538, 542, 551, 553, 560, 573, 576, 580, 587, 622, 634, 658, 668, 670, 694, 705, 714, 726, 732, 736, 744, 758, 772, 777, 782, 785, 870, 878, 879, 890, 891, 896, 899, 912, 913, 916, 917, 919, 921, 926, 928 |
| station | [backend/src/test/java/com/restaurant/system/printing/service/impl/PrintJobServiceImplTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/service/impl/PrintJobServiceImplTest.java#L89) | 89, 109, 118, 119 |
| station | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactoryTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestFactoryTest.java#L26) | 26, 29, 34 |
| station | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestValidatorTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuManifestValidatorTest.java#L59) | 59, 62, 72, 75, 84, 89, 95, 99, 100, 108 |
| station | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuPlannerTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuPlannerTest.java#L37) | 37, 55, 84, 135 |
| station | [backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuServiceIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/staging/menu/StagingSyntheticSourceMenuServiceIntegrationTest.java#L219) | 219, 239 |
| station | [deployment/cloud/FINAL_SMOKE_TEST_CHECKLIST.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/FINAL_SMOKE_TEST_CHECKLIST.md#L67) | 67, 82, 84, 85, 86, 87, 105 |
| station | [deployment/cloud/README_CLOUD_DEPLOYMENT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_CLOUD_DEPLOYMENT.md#L153) | 153 |
| station | [deployment/cloud/README_PRODUCTION_V37_RELEASE.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_PRODUCTION_V37_RELEASE.md#L5) | 5 |
| station | [deployment/cloud/README_STG005_SYNTHETIC_SOURCE_MENU.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/README_STG005_SYNTHETIC_SOURCE_MENU.md#L31) | 31 |
| station | [deployment/cloud/final-channels-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/apply-menu-once.py#L14) | 14, 72 |
| station | [deployment/cloud/final-channels-20261003/approved-modifiers.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/approved-modifiers.json#L58) | 58, 71, 110, 111, 123, 124, 136, 137, 149, 150 |
| station | [deployment/cloud/final-channels-20261003/reconcile-tea-egg.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/reconcile-tea-egg.sql#L6) | 6, 11, 12, 13, 14, 15, 16 |
| station | [deployment/cloud/final-closeout-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/apply-menu-once.py#L14) | 14, 112, 113 |
| station | [deployment/cloud/final-closeout-20261003/approved-modifiers.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/approved-modifiers.json#L6) | 6, 7, 16, 17, 26, 27, 36, 37, 46, 47, 54, 56, 57, 58, 64, 66, 67, 68, 74, 76, 77, 78, 84, 86, 87, 88, 94, 96, 97, 98, 104, 106, 107, 108, 114, 116, 117, 118, 124, 126, 127, 128, 134, 136, 137, 138, 146, 147, 156, 157, 166, 167, 176, 177, 186, 187, 196, 197, 206, 207, 216, 217, 226, 227, 236, 237, 246, 247 |
| station | [deployment/cloud/ops001-flyway-checksums.txt](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-flyway-checksums.txt#L7) | 7 |
| station | [deployment/cloud/ops001-flyway-production-checksums.txt](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-flyway-production-checksums.txt#L9) | 9 |
| station | [deployment/cloud/ops001-flyway-v26-checksums.txt](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-flyway-v26-checksums.txt#L8) | 8 |
| station | [deployment/cloud/ops001-jq-compat.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/ops001-jq-compat.py#L228) | 228, 229, 645 |
| station | [deployment/cloud/production-v26-smoke.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-smoke.py#L631) | 631, 647, 655, 771, 786 |
| station | [deployment/cloud/promote-addon-backend-20261006.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/promote-addon-backend-20261006.py#L23) | 23, 54, 94, 95 |
| station | [deployment/cloud/reconcile-production-addons-20261006.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/reconcile-production-addons-20261006.sql#L18) | 18, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98, 99, 102, 103, 104, 105, 106, 107, 108, 109, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 147, 148, 149, 150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 166, 167, 168, 169, 170, 171, 172, 173, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 194, 195, 196, 197, 198, 199, 200, 201, 202, 203, 204, 205, 206, 207, 208, 209, 212, 213, 214, 215, 216, 217, 218, 219, 220, 221 |
| station | [deployment/cloud/staging-ingress/README.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/README.md#L90) | 90 |
| station | [deployment/cloud/staging-ingress/edge-http.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/edge-http.conf#L4) | 4, 10 |
| station | [deployment/cloud/staging-ingress/edge-https.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/edge-https.conf#L11) | 11, 12, 13, 17, 18, 22, 23 |
| station | [deployment/cloud/staging-ingress/host-relay.conf](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/host-relay.conf#L16) | 16, 19, 23, 24 |
| station | [deployment/cloud/staging-ingress/renew-staging.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/renew-staging.sh#L8) | 8 |
| station | [deployment/cloud/staging-ingress/staging.conf.template](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/staging.conf.template#L5) | 5, 8, 18, 28 |
| station | [deployment/cloud/staging-ingress/verify.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-ingress/verify.py#L13) | 13, 39 |
| station | [deployment/cloud/staging-pad-direct/README.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-pad-direct/README.md#L69) | 69, 70 |
| station | [deployment/cloud/staging-phase-b-part1-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-phase-b-part1-acceptance.sh#L617) | 617 |
| station | [deployment/cloud/staging-phase-b-part2-acceptance.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-phase-b-part2-acceptance.sh#L312) | 312, 313 |
| station | [deployment/cloud/uber-capability-20261003/reconcile-option370.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-capability-20261003/reconcile-option370.sql#L7) | 7 |
| station | [deployment/cloud/uber-kitchen-mirror/README.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/README.md#L42) | 42 |
| station | [deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py#L12) | 12 |
| station | [deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/approved-roots.json](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/approved-roots.json#L3) | 3, 5, 35, 37, 43, 45, 91, 93, 99, 101, 203, 205, 211, 213, 267, 275, 315, 317, 323, 325 |
| station | [deployment/cloud/uber-note-20261003/RenderNoteAcceptance.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-note-20261003/RenderNoteAcceptance.java#L10) | 10, 44, 45, 48, 63, 64, 170, 173, 176, 179 |
| station | [deployment/windows-pilot/README_WINDOWS_PILOT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/README_WINDOWS_PILOT.md#L177) | 177 |
| station | [deployment/windows-pilot/package/README_WINDOWS_PILOT.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/windows-pilot/package/README_WINDOWS_PILOT.md#L177) | 177 |
| station | [frontend/src/App.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/App.tsx#L31) | 31, 42, 43, 45, 239, 240, 244, 247, 248 |
| station | [frontend/src/data/menuImportSeed.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/data/menuImportSeed.ts#L4) | 4, 5, 10, 11, 16, 17, 42, 43, 45, 46, 53, 54, 56, 57, 64, 65, 67, 68, 75, 76, 78, 79, 89, 100, 111, 122, 130, 131, 133, 134, 141, 142, 144, 145, 152, 153, 155, 156, 211, 222, 233, 244, 330, 331, 332, 339, 340, 341, 348, 349, 357, 358, 360, 366, 367, 375, 376, 384, 385, 393, 394, 402, 403, 411, 412, 420, 421, 429, 430, 438, 439, 440, 447, 448, 449, 456, 457, 458, 465, 466, 467, 474, 475, 476, 483, 484, 492, 493, 501, 502, 510, 511, 519, 520, 528, 529, 537, 538, 546, 547, 555, 556, 564, 565, 573, 574, 582, 583, 591, 592, 600, 601, 603, 609, 610, 612, 618, 619, 627, 628, 629, 636, 637, 638, 645, 646, 654, 655, 657, 663, 664, 672, 673, 681, 682, 690, 691, 699, 700, 708, 709, 717, 718, 726, 727, 735, 736, 737, 744, 745, 746, 753, 754, 755, 762, 763, 764, 771, 772, 773, 780, 781, 789, 790, 798, 799, 807, 808, 816, 817, 825, 826, 834, 835, 843, 844, 852, 853, 861, 862, 870, 871, 879, 880, 888, 889, 897, 898, 900, 906, 907, 909, 915, 916, 924, 925, 926, 933, 934, 935, 942, 943, 951, 952, 954, 960, 961, 969, 970, 978, 979, 987, 988, 996, 997, 1005, 1006, 1014, 1015, 1023, 1024, 1032, 1033, 1034, 1041, 1042, 1043, 1050, 1051, 1052, 1059, 1060, 1061, 1068, 1069, 1070, 1077, 1078, 1086, 1087, 1095, 1096, 1104, 1105, 1113, 1114, 1122, 1123, 1131, 1132, 1140, 1141, 1149, 1150, 1158, 1159, 1167, 1168, 1176, 1177, 1185, 1186, 1194, 1195, 1197, 1203, 1204, 1206, 1212, 1213, 1221, 1222, 1230, 1231, 1239, 1240, 1248, 1249, 1257, 1258, 1266, 1267, 1275, 1276, 1284, 1285, 1293, 1294, 1295, 1302, 1303, 1304, 1311, 1312, 1313, 1320, 1321, 1322, 1329, 1330, 1331, 1338, 1339, 1340, 1347, 1348, 1349, 1356, 1357, 1365, 1366, 1374, 1375, 1383, 1384, 1392, 1393, 1401, 1402, 1410, 1411, 1413, 1419, 1420, 1428, 1429, 1437, 1438, 1446, 1447, 1455, 1456, 1464, 1465, 1473, 1474, 1482, 1483, 1491, 1492, 1500, 1501, 1503, 1509, 1510, 1512, 1518, 1519, 1527, 1528, 1536, 1537, 1545, 1546, 1554, 1555, 1563, 1564, 2292, 2293, 2294, 2301, 2302, 2303, 2310, 2311, 2313, 2319, 2320, 2328, 2329, 2337, 2338, 2346, 2347, 2355, 2356, 2364, 2365, 2373, 2374, 2375, 2382, 2383, 2384, 2391, 2392, 2393, 2400, 2401, 2402, 2409, 2410, 2411, 2418, 2419, 2427, 2428, 2436, 2437, 2445, 2446, 2454, 2455, 2463, 2464, 2472, 2473, 2481, 2482, 2490, 2491, 2499, 2500, 2508, 2509, 2517, 2518, 2526, 2527, 2535, 2536, 2544, 2545, 2546, 2553, 2554, 2555, 2562, 2563, 2565, 2571, 2572, 2580, 2581, 2589, 2590, 2598, 2599, 2607, 2608, 2616, 2617, 2625, 2626, 2627, 2634, 2635, 2636, 2643, 2644, 2645, 2652, 2653, 2654, 2661, 2662, 2663, 2670, 2671, 2679, 2680, 2688, 2689, 2697, 2698, 2706, 2707, 2715, 2716, 2724, 2725, 2733, 2734, 2742, 2743, 2751, 2752, 2760, 2761, 2769, 2770, 2778, 2779, 2787, 2788, 2796, 2797, 2805, 2806, 2814, 2815, 2823, 2824, 2825, 2832, 2833, 2834, 2841, 2842, 2844, 2850, 2851, 2859, 2860, 2868, 2869, 2877, 2878, 2886, 2887, 2895, 2896, 2904, 2905, 2906, 2913, 2914, 2915, 2922, 2923, 2924, 2931, 2932, 2933, 2940, 2941, 2942, 2949, 2950, 2958, 2959, 2967, 2968, 2976, 2977, 2985, 2986, 2994, 2995, 3003, 3004, 3012, 3013, 3021, 3022, 3030, 3031, 3039, 3040, 3048, 3049, 3057, 3058, 3066, 3067, 3075, 3076 |
| station | [frontend/src/data/mockOrdering.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/data/mockOrdering.ts#L11) | 11, 17, 22, 69, 73, 76, 83, 84, 85, 87, 92, 93, 96, 97, 101, 102, 103, 110, 111, 114, 115, 213, 257, 267 |
| station | [frontend/src/features/auth/RequireAuth.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/auth/RequireAuth.tsx#L6) | 6 |
| station | [frontend/src/features/dinein/DineInPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/dinein/DineInPage.tsx#L51) | 51, 57 |
| station | [frontend/src/features/kds/history/KdsHistoryPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/kds/history/KdsHistoryPage.tsx#L1) | 1, 2, 8, 11, 12, 20, 49, 62, 104 |
| station | [frontend/src/features/kds/hot-kitchen/HotKitchenPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/kds/hot-kitchen/HotKitchenPage.tsx#L4) | 4, 58, 107 |
| station | [frontend/src/features/kds/hot-kitchen/components/HotKitchenItemRow.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/kds/hot-kitchen/components/HotKitchenItemRow.tsx#L3) | 3 |
| station | [frontend/src/features/kds/hot-kitchen/components/HotKitchenOrderCard.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/kds/hot-kitchen/components/HotKitchenOrderCard.tsx#L4) | 4, 5, 9 |
| station | [frontend/src/features/kds/noodle/NoodleStationPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/kds/noodle/NoodleStationPage.tsx#L3) | 3, 22, 24, 64, 127, 130 |
| station | [frontend/src/features/kds/noodle/components/KdsOrderCard.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/kds/noodle/components/KdsOrderCard.tsx#L4) | 4, 16, 17, 22, 29, 60, 233 |
| station | [frontend/src/features/kds/ramen/components/RamenOrderCard.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/kds/ramen/components/RamenOrderCard.tsx#L1) | 1, 4, 16, 24, 34, 52, 67, 72 |
| station | [frontend/src/features/ordering/components/ItemCustomizationModal.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.test.tsx#L5) | 5, 11, 12, 13, 14, 15, 16, 17, 18, 21, 28, 49, 50, 53, 54, 61, 62, 68, 73, 74, 79, 106, 107, 108, 116 |
| station | [frontend/src/features/ordering/components/ItemCustomizationModal.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.tsx#L10) | 10, 16, 30, 32, 36, 190, 191, 335, 413, 415, 417, 420, 422, 424, 539 |
| station | [frontend/src/features/ordering/components/noodleTypePresentation.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/noodleTypePresentation.ts#L1) | 1, 2, 3, 4, 5, 6, 7, 8, 11, 12 |
| station | [frontend/src/features/orders/components/OrderDetailPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/orders/components/OrderDetailPanel.tsx#L29) | 29, 559, 562 |
| station | [frontend/src/features/owner-admin/ComboEggOverridesPanel.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/ComboEggOverridesPanel.test.tsx#L25) | 25 |
| station | [frontend/src/features/owner-admin/MenuManagementPage.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuManagementPage.test.tsx#L44) | 44, 45, 57 |
| station | [frontend/src/features/owner-admin/MenuOptionsPanel.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuOptionsPanel.test.tsx#L29) | 29, 39 |
| station | [frontend/src/features/owner-admin/MenuOptionsPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuOptionsPanel.tsx#L14) | 14, 15, 20, 23, 26, 106, 107, 219, 222, 338, 339, 347, 593, 600, 610, 621, 624, 633 |
| station | [frontend/src/features/owner-admin/OwnerAdminDashboardPage.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/OwnerAdminDashboardPage.test.tsx#L15) | 15, 16, 22, 31 |
| station | [frontend/src/features/owner-admin/OwnerAdminDashboardPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/OwnerAdminDashboardPage.tsx#L13) | 13, 14, 429 |
| station | [frontend/src/features/owner-admin/PrintingDisplayRulesPanel.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PrintingDisplayRulesPanel.test.tsx#L16) | 16, 20, 29, 35 |
| station | [frontend/src/features/owner-admin/PrintingDisplayRulesPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PrintingDisplayRulesPanel.tsx#L55) | 55, 63, 64, 68, 81, 182, 184, 186, 276, 351, 535, 662, 663, 759, 770, 788, 803, 819, 821 |
| station | [frontend/src/features/owner-admin/PrintingSettingsPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PrintingSettingsPage.tsx#L22) | 22, 53, 55, 723, 726, 731, 732, 735, 1348, 1351, 1427, 1943 |
| station | [frontend/src/features/owner-admin/RevenueCategoryPanels.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/RevenueCategoryPanels.tsx#L4) | 4, 5, 6, 18, 20, 22 |
| station | [frontend/src/features/owner-admin/menuOptionDefaults.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/menuOptionDefaults.test.ts#L4) | 4, 6, 10, 11, 19, 20, 24, 31 |
| station | [frontend/src/features/owner-admin/menuOptionDefaults.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/menuOptionDefaults.ts#L13) | 13, 14, 15, 49, 50, 53, 54 |
| station | [frontend/src/features/pickup/components/PickupOrderCard.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/pickup/components/PickupOrderCard.tsx#L28) | 28, 29, 30, 31, 32, 45, 63, 64, 115 |
| station | [frontend/src/features/reports/SalesReportPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/SalesReportPage.tsx#L5) | 5, 27 |
| station | [frontend/src/features/reports/components/ChannelSalesPanels.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.test.tsx#L4) | 4, 15, 34, 36, 66 |
| station | [frontend/src/features/reports/components/ChannelSalesPanels.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.tsx#L6) | 6, 7, 35, 36, 38 |
| station | [frontend/src/features/store/storeModuleAccess.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/store/storeModuleAccess.test.ts#L120) | 120, 121 |
| station | [frontend/src/features/store/storeRoutes.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/store/storeRoutes.ts#L49) | 49, 52, 53 |
| station | [frontend/src/features/uber-eats/UberInboxPage.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/uber-eats/UberInboxPage.test.tsx#L10) | 10, 26, 30, 33, 42, 66, 70, 108, 112, 113, 117, 124, 125 |
| station | [frontend/src/features/uber-eats/UberInboxPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/uber-eats/UberInboxPage.tsx#L45) | 45, 66 |
| station | [frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts#L8) | 8, 10, 12, 13, 76, 78, 163 |
| station | [frontend/src/hooks/useDraftOrder.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useDraftOrder.ts#L106) | 106, 143, 192, 193, 215, 216, 301, 318 |
| station | [frontend/src/hooks/useHotKitchenOrders.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useHotKitchenOrders.ts#L2) | 2, 3, 7, 10, 16, 30, 65 |
| station | [frontend/src/hooks/useMenuCatalog.refresh.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.refresh.test.tsx#L19) | 19, 20 |
| station | [frontend/src/hooks/useMenuCatalog.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.test.ts#L195) | 195, 200, 202, 206, 207, 208, 218, 219, 220, 232, 233, 234, 242, 244, 298, 300, 354, 356, 393, 395, 512, 516, 518, 662, 666, 810, 814 |
| station | [frontend/src/hooks/useMenuCatalog.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.ts#L321) | 321 |
| station | [frontend/src/hooks/useNoodleStationOrders.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useNoodleStationOrders.ts#L2) | 2, 3, 7, 8, 22 |
| station | [frontend/src/hooks/useOrderSessions.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useOrderSessions.ts#L56) | 56, 117, 128, 129 |
| station | [frontend/src/hooks/useRamenStationOrders.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useRamenStationOrders.ts#L2) | 2, 3, 8, 12, 20, 56 |
| station | [frontend/src/offline/localDrafts.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/localDrafts.test.ts#L49) | 49 |
| station | [frontend/src/offline/menuCache.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/menuCache.test.ts#L155) | 155, 157, 165, 166, 167 |
| station | [frontend/src/offline/orderOutbox.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/orderOutbox.test.ts#L61) | 61, 63, 65, 66 |
| station | [frontend/src/pages/Home.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/pages/Home.tsx#L22) | 22, 24 |
| station | [frontend/src/pages/KdsNoodle.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/pages/KdsNoodle.tsx#L1) | 1, 3, 4 |
| station | [frontend/src/services/channelSales.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/channelSales.ts#L24) | 24 |
| station | [frontend/src/services/kdsService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/kdsService.ts#L7) | 7, 11, 34, 60 |
| station | [frontend/src/services/manualReprintService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/manualReprintService.test.ts#L32) | 32, 33, 38, 43, 47 |
| station | [frontend/src/services/orderOutboxProcessor.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderOutboxProcessor.test.ts#L78) | 78, 80, 82, 83 |
| station | [frontend/src/services/orderService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderService.test.ts#L25) | 25, 27, 29, 30, 76, 79, 83, 84, 85, 90, 92, 114, 119, 121, 122, 190, 218, 220, 245, 249, 251, 253 |
| station | [frontend/src/services/orderService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderService.ts#L201) | 201, 233 |
| station | [frontend/src/services/ownerDashboardService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/ownerDashboardService.ts#L75) | 75, 100 |
| station | [frontend/src/services/ownerMenuOptionService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/ownerMenuOptionService.ts#L6) | 6 |
| station | [frontend/src/services/ownerWorkspaceService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/ownerWorkspaceService.test.ts#L7) | 7 |
| station | [frontend/src/services/printingAdminService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/printingAdminService.ts#L152) | 152, 159, 161, 209, 217, 219, 435, 436 |
| station | [frontend/src/services/uberEatsService.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/uberEatsService.ts#L4) | 4, 18 |
| station | [frontend/src/types/kds.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/types/kds.ts#L20) | 20, 59 |
| station | [frontend/src/types/ordering.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/types/ordering.ts#L46) | 46, 95, 236 |
| station | [frontend/src/utils/displayLabels.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/displayLabels.ts#L11) | 11, 13, 20, 21, 24, 25 |
| station | [restaurant-pad-app/docs/PAD_DIRECT_MULTI_PRINTER_ROUTING.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PAD_DIRECT_MULTI_PRINTER_ROUTING.md#L11) | 11, 13 |
| station | [restaurant-pad-app/docs/PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PAD_DIRECT_RESTAURANT_PILOT_CHECKLIST.md#L12) | 12, 13 |
| station | [restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/docs/PAD_DIRECT_SEMI_AUTO_RUNBOOK.md#L116) | 116 |
| tax_time | [backend/src/main/java/com/restaurant/system/analytics/dto/ChannelSalesSummary.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/dto/ChannelSalesSummary.java#L8) | 8 |
| tax_time | [backend/src/main/java/com/restaurant/system/analytics/service/AnalyticsAggregationScheduler.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/service/AnalyticsAggregationScheduler.java#L17) | 17 |
| tax_time | [backend/src/main/java/com/restaurant/system/analytics/service/ChannelSalesService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/service/ChannelSalesService.java#L59) | 59 |
| tax_time | [backend/src/main/java/com/restaurant/system/common/pricing/TaxCalculator.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/pricing/TaxCalculator.java#L8) | 8, 9 |
| tax_time | [backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java#L31) | 31, 32 |
| tax_time | [backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberFinancialSalesSource.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/service/UberFinancialSalesSource.java#L38) | 38, 68 |
| tax_time | [backend/src/main/java/com/restaurant/system/order/close/StoreDailyCloseService.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/close/StoreDailyCloseService.java#L40) | 40 |
| tax_time | [backend/src/test/java/com/restaurant/system/analytics/service/ChannelSalesServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/analytics/service/ChannelSalesServiceTest.java#L109) | 109, 119, 120, 129 |
| tax_time | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberEatsPostgresIntegrationTest.java#L1181) | 1181, 1187, 1300, 1301, 1302, 1304, 1900, 1957, 1962, 1971, 1976, 2001, 2002, 2014, 2018, 2197 |
| tax_time | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSalesSourceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSalesSourceTest.java#L27) | 27, 38, 54 |
| tax_time | [backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSnapshotTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/integration/ubereats/UberFinancialSnapshotTest.java#L17) | 17, 18, 19, 20, 36, 50 |
| tax_time | [backend/src/test/java/com/restaurant/system/menu/service/impl/MenuCatalogHashServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/menu/service/impl/MenuCatalogHashServiceTest.java#L119) | 119, 120 |
| tax_time | [backend/src/test/java/com/restaurant/system/order/close/StoreDailyClosePostgresIntegrationTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/close/StoreDailyClosePostgresIntegrationTest.java#L57) | 57, 100 |
| tax_time | [backend/src/test/java/com/restaurant/system/order/close/StoreDailyCloseServiceTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/order/close/StoreDailyCloseServiceTest.java#L27) | 27, 47, 62, 70, 72, 86, 99, 101 |
| tax_time | [backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java#L745) | 745 |
| tax_time | [deployment/cloud/.env.example](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/.env.example#L8) | 8 |
| tax_time | [deployment/cloud/.env.staging.example](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/.env.staging.example#L14) | 14 |
| tax_time | [deployment/cloud/docker-compose.staging.yml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/docker-compose.staging.yml#L9) | 9, 43, 89 |
| tax_time | [deployment/cloud/docker-compose.yml](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/docker-compose.yml#L9) | 9, 36, 58 |
| tax_time | [deployment/cloud/production-v26-exact-artifact-promote.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/production-v26-exact-artifact-promote.sh#L320) | 320, 366 |
| tax_time | [deployment/cloud/staging-local-rehearsal.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-local-rehearsal.sh#L228) | 228 |
| tax_time | [deployment/cloud/tests/test_staging_guard.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_guard.sh#L258) | 258 |
| tax_time | [deployment/cloud/tests/test_staging_server_preflight.sh](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_staging_server_preflight.sh#L190) | 190 |
| tax_time | [frontend/src/features/owner-admin/AddonCatalogPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/AddonCatalogPanel.tsx#L28) | 28 |
| tax_time | [frontend/src/features/owner-admin/MenuManagementPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuManagementPage.tsx#L57) | 57 |
| tax_time | [frontend/src/features/owner-admin/OwnerAdminDashboardPage.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/OwnerAdminDashboardPage.tsx#L25) | 25 |
| tax_time | [frontend/src/features/owner-admin/PricingRulesPanel.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/PricingRulesPanel.tsx#L43) | 43 |
| tax_time | [frontend/src/features/owner-admin/RevenueCategoryPanels.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/RevenueCategoryPanels.tsx#L15) | 15 |
| tax_time | [frontend/src/features/reports/components/ChannelSalesPanels.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.test.tsx#L14) | 14 |
| tax_time | [frontend/src/features/reports/components/ReportTrendChart.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ReportTrendChart.tsx#L10) | 10 |
| tax_time | [frontend/src/features/reports/reportUtils.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/reportUtils.ts#L13) | 13 |
| tax_time | [frontend/src/hooks/useMenuCatalog.refresh.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.refresh.test.tsx#L18) | 18 |
| tax_time | [frontend/src/hooks/useMenuCatalog.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.test.ts#L18) | 18 |
| tax_time | [frontend/src/offline/menuCache.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/menuCache.test.ts#L27) | 27, 28 |
| tax_time | [frontend/src/services/channelSales.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/channelSales.ts#L32) | 32 |
| tax_time | [frontend/src/utils/tax.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/tax.ts#L1) | 1, 2 |
| tenant | [deployment/cloud/final-channels-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/apply-menu-once.py#L100) | 100, 102, 107, 206, 218, 235 |
| tenant | [deployment/cloud/final-channels-20261003/backfill-financial.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/backfill-financial.py#L148) | 148, 150, 152, 203, 205, 210, 212, 214 |
| tenant | [deployment/cloud/final-channels-20261003/deploy-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/deploy-once.py#L30) | 30, 31, 33 |
| tenant | [deployment/cloud/final-channels-20261003/reconcile-tea-egg.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/reconcile-tea-egg.sql#L19) | 19, 26, 28, 34, 37, 41, 48 |
| tenant | [deployment/cloud/final-closeout-20261003/apply-menu-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/apply-menu-once.py#L51) | 51, 62, 75, 77, 86, 114 |
| tenant | [deployment/cloud/final-closeout-20261003/deploy-layout-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/deploy-layout-once.py#L30) | 30, 31, 33 |
| tenant | [deployment/cloud/final-closeout-20261003/deploy-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/deploy-once.py#L30) | 30, 31, 33 |
| tenant | [deployment/cloud/final-closeout-20261003/reconcile-extra-meat.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-closeout-20261003/reconcile-extra-meat.sql#L13) | 13, 22, 28, 31, 35, 41 |
| tenant | [deployment/cloud/reconcile-production-addons-20261006.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/reconcile-production-addons-20261006.sql#L224) | 224, 265 |
| tenant | [deployment/cloud/staging-pad-direct/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/staging-pad-direct/apply-once.py#L59) | 59 |
| tenant | [deployment/cloud/tests/test_production_v26_evidence.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/tests/test_production_v26_evidence.py#L30) | 30, 34 |
| tenant | [deployment/cloud/uber-capability-20261003/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-capability-20261003/apply-once.py#L35) | 35, 38, 53 |
| tenant | [deployment/cloud/uber-capability-20261003/deploy-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-capability-20261003/deploy-once.py#L30) | 30, 31, 33 |
| tenant | [deployment/cloud/uber-capability-20261003/reconcile-option370.sql](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-capability-20261003/reconcile-option370.sql#L4) | 4, 7, 15 |
| tenant | [deployment/cloud/uber-kitchen-mirror/apply-field-fixes-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/apply-field-fixes-once.py#L30) | 30, 31, 33 |
| tenant | [deployment/cloud/uber-kitchen-mirror/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/apply-once.py#L32) | 32 |
| tenant | [deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-kitchen-mirror/root-mappings-20261002/apply-once.py#L86) | 86, 88, 90 |
| tenant | [deployment/cloud/uber-note-20261003/deploy-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-note-20261003/deploy-once.py#L30) | 30, 31, 33 |
| tenant | [deployment/cloud/uber-signing-key/apply-once.py](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/uber-signing-key/apply-once.py#L30) | 30 |
| tenant | [frontend/src/features/owner-admin/MenuManagementPage.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuManagementPage.test.tsx#L13) | 13, 80 |
| tenant | [frontend/src/features/owner-admin/OwnerAdminDashboardPage.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/OwnerAdminDashboardPage.test.tsx#L10) | 10, 55 |
| tenant | [frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts#L58) | 58, 88, 146 |
| tenant | [frontend/src/hooks/useMenuCatalog.refresh.test.tsx](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.refresh.test.tsx#L13) | 13 |
| tenant | [frontend/src/hooks/useMenuCatalog.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.test.ts#L428) | 428 |
| tenant | [frontend/src/offline/menuCache.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/menuCache.test.ts#L14) | 14 |
| tenant | [frontend/src/offline/menuCacheNotice.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/menuCacheNotice.test.ts#L6) | 6 |
| tenant | [frontend/src/services/orderService.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/services/orderService.test.ts#L154) | 154 |
| tenant | [frontend/src/utils/appCacheVersion.test.ts](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/appCacheVersion.test.ts#L11) | 11 |
