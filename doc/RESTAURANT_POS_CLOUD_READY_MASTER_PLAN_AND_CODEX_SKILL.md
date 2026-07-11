# 餐饮点单系统 Cloud-Ready Master Plan + Codex Skill

> 这是一份可以直接喂给 Codex 的总控文档。它包含上云前最终版整改计划、上云后 Android/多店路线图，以及 Codex 执行守则。

> 当前阶段明确不改 `complete / Finish` 语义，不做 payment/refund，不把 split bill 变成后端正式账单。

---



---

# Part 1：上云前最终版本架构整改计划


> 目标：把当前餐饮点单系统整理成可以稳定部署到云端服务器的“最终上云前版本”。  
> 当前阶段不重做订单生命周期，不做 payment/refund，不做正式支付闭环。`complete / Finish` 目前继续作为现有餐厅运营结束动作使用。财务侧当前目标是让老板看到销售、成本、利润趋势，不要求先接入支付、退款、正式拆账或会计级结算。

---

## 0. 当前决策边界

### 0.1 本阶段必须做

1. 数据库迁移和 schema 治理。
2. 生产安全配置，尤其是关闭开发身份绕过。
3. 多店 store scope 和前端硬编码清理。
4. 云端部署模式下的打印安全边界。
5. 打印可靠性和 print job 可观测性补强。
6. Platform / Owner / Developer Tools 边界整理。
7. Feature Package 与权限判断收敛。
8. 前端服务端状态、轮询、WebSocket 策略收敛。
9. 同桌并发编辑防护。
10. 关键集成测试、部署 runbook、备份恢复方案。

### 0.2 本阶段明确不做

1. 不重构 `completeOrder` 的业务含义。
2. 不引入 payment provider。
3. 不做 refund flow。
4. 不把前端 split bill 变成后端正式账单。
5. 不做完整 SaaS 订阅、计费、套餐售卖。
6. 不把 Android App 壳和 Pad Direct 本地打印作为本阶段必须完成项。
7. 不在上云前大改 KDS 生产任务模型的 runtime source of truth。

这些内容可以进入后续路线图，但不能阻塞上云前最终版。

---

## 1. P0：数据库迁移和数据完整性

### 1.1 问题

当前系统使用 JPA `ddl-auto=update` 自动演进 schema，且没有正式 Flyway/Liquibase migration。系统已经有订单、菜单、库存、打印、用户、权限、审计、多店、报表、Pad Direct 等多个关键表，继续依赖自动建表/改表会导致：

- 本地、试点、云端数据库结构漂移。
- 上线时 Hibernate 自动改表不可控。
- 没有可审计的迁移历史。
- 回滚困难。
- 缺少明确索引、唯一约束、外键约束。
- 生产数据修复无法版本化。

### 1.2 目标

上云前必须建立正式 migration 机制，并把云端/试点环境从 `ddl-auto=update` 切到 `validate` 或 `none`。

### 1.3 Codex 任务

#### 任务 DB-001：引入 Flyway

建议使用 Flyway，原因是 Spring Boot 集成简单，适合当前 Java/Spring Boot 项目。

需要完成：

1. backend 加入 Flyway 依赖。
2. 新建目录：
   - `backend/src/main/resources/db/migration`
3. 生成 baseline migration：
   - `V1__baseline_current_schema.sql`
4. 生成后续补丁 migration：
   - `V2__add_missing_indexes_and_constraints.sql`
   - `V3__production_auth_and_store_scope_hardening.sql`
   - 实际编号按项目当前状态调整。
5. local profile 可保留 `ddl-auto=update`，但 pilot/cloud/prod 必须改为：
   - `spring.jpa.hibernate.ddl-auto=validate`
   - 或 `none`
6. 在 README / deployment docs 里写清楚：
   - 新环境如何初始化 DB。
   - 老环境如何 baseline。
   - 如何 rollback 或 restore backup。

#### 任务 DB-002：补齐关键约束

优先补以下约束，避免餐饮现场数据断链：

1. 订单主线：
   - `order_items.order_id -> orders.id`
   - `order_item_options.order_item_id -> order_items.id`
   - `orders.store_id -> stores.id`
   - `orders.created_by -> users.id`
2. 菜单主线：
   - `menu_categories.store_id -> stores.id`
   - `menu_items.store_id -> stores.id`
   - `menu_items.category_id -> menu_categories.id`
   - `menu_items.station_id -> stations.id`
   - `menu_item_options.menu_item_id -> menu_items.id`
3. 打印主线：
   - `printer_configs.store_id -> stores.id`
   - `printer_assignments.store_id -> stores.id`
   - `printer_assignments.printer_id -> printer_configs.id`，如果允许未分配则 nullable。
   - `print_jobs.store_id -> stores.id`
   - `print_jobs.order_id -> orders.id`
   - `print_job_attempts.print_job_id -> print_jobs.id`
4. 多店/权限：
   - `organization_memberships.user_id -> users.id`
   - `store_memberships.user_id -> users.id`
   - `store_memberships.store_id -> stores.id`
5. 审计日志：
   - `audit_logs.store_id -> stores.id` 可 nullable，但需要索引。
   - `audit_logs.actor_user_id -> users.id` 可 nullable。

注意：如果当前数据库已有脏数据，不要直接加约束导致启动失败。先写数据检查 SQL，再写数据修复 migration，再加约束。

#### 任务 DB-003：补齐关键唯一约束和索引

建议优先：

```sql
-- 订单查询
CREATE INDEX IF NOT EXISTS idx_orders_store_status_updated ON orders(store_id, status, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_store_completed ON orders(store_id, completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_table_open ON orders(store_id, table_no, status);
CREATE INDEX IF NOT EXISTS idx_orders_pickup_open ON orders(store_id, pickup_no, status);

-- 订单明细
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_order_item_options_item ON order_item_options(order_item_id);

-- KDS / 生产任务
CREATE INDEX IF NOT EXISTS idx_kitchen_tasks_store_status ON kitchen_tasks(store_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_kitchen_tasks_order ON kitchen_tasks(order_id);
CREATE INDEX IF NOT EXISTS idx_production_tasks_store_status ON production_tasks(store_id, status, created_at);

-- 打印
CREATE INDEX IF NOT EXISTS idx_print_jobs_store_status_created ON print_jobs(store_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_print_jobs_order_module ON print_jobs(order_id, module_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_print_job_attempts_job ON print_job_attempts(print_job_id, attempt_no);

-- 审计
CREATE INDEX IF NOT EXISTS idx_audit_logs_store_created ON audit_logs(store_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_created ON audit_logs(actor_user_id, created_at DESC);
```

唯一约束要谨慎：

- `dining_tables.table_code` 当前不能简单设唯一，因为现有数据允许重复 `T12`。
- 更合理的目标模型是订单保存 `table_id + slot_code + display_snapshot`。
- 上云前不要强行重构 table model，但需要新增数据质量检查和 admin UI 提醒。

#### 任务 DB-004：seed 策略冻结

当前 `RuntimeDataSeeder` 有补数据能力，也有 force overwrite 能力。上云前必须明确：

1. cloud/prod 默认：
   - `app.seed.runtime-enabled=true` 可以保留用于补缺失基础数据。
   - `app.seed.force-overwrite=false` 必须固定。
2. 禁止生产自动覆盖 owner 管理过的菜单价格、选项、桌台、打印设置。
3. 如果需要初始化新店，走 template onboarding 或显式 admin API，不靠启动时覆盖。
4. 给 seeder 加日志：启动时补了哪些数据、跳过了哪些数据。

### 1.4 验收标准

- 云端 profile 启动时不再自动改 schema。
- migration 可以在空库完整建表。
- migration 可以在现有试点库 baseline 后继续升级。
- 关键业务表有查询索引。
- 重启服务不会覆盖老板维护过的菜单、价格、打印、桌台配置。
- `ddl-auto=update` 不出现在 pilot/cloud/prod 配置里。

---

## 2. P0：生产安全和认证授权收敛

### 2.1 问题

系统已经有 JWT、refresh token、BCrypt、role guard，但仍保留 `X-User-Id` fallback 和 dev role switcher。开发阶段可接受，上云阶段必须关闭。

### 2.2 目标

云端部署后，所有业务请求必须基于 Bearer token 身份，不能靠请求头伪造用户。

### 2.3 Codex 任务

#### 任务 SEC-001：生产 profile 禁用开发身份入口

cloud/prod 配置必须：

```yaml
app:
  auth:
    x-user-id-fallback-enabled: false
  dev-tools:
    role-switcher-enabled: false
  features:
    developer-tools: false
    platform: false # 除非明确是平台管理环境
```

并增加启动期保护：

- 如果 active profile 包含 `prod` 或 `cloud`，但 `x-user-id-fallback-enabled=true`，启动失败。
- 如果 active profile 包含 `prod` 或 `cloud`，但 role switcher 开启，启动失败。
- 如果 JWT secret 仍是 dev 默认值，启动失败。

#### 任务 SEC-002：默认账号和密码治理

当前 local/dev 默认账号可以保留，但 cloud/prod 必须：

1. 禁止使用默认密码自动重置。
2. 首次部署创建 owner 账号应走一次性初始化脚本或管理命令。
3. 不在生产日志打印密码、token、refresh token、device token。
4. 文档中生产部署部分不能写真实默认密码，只写“通过环境变量或初始化脚本设置”。

#### 任务 SEC-003：统一前端 API 认证

所有 frontend service 必须通过共享 `apiClient`：

- 自动加 `Authorization: Bearer ...`
- 不再手写 `X-User-Id`
- 401 refresh 只重试一次
- refresh 失败统一清 session 并跳 login
- API error 显示可理解中文错误

需要扫描并删除：

- `X-User-Id: 1`
- `X-User-Id: 2`
- `store_id=1` 硬编码业务调用

#### 任务 SEC-004：CORS 和 WebSocket origin 收敛

上云后 REST 和 WebSocket 都要允许正确 origin，不能只配 WebSocket。

需要配置：

- cloud 前端域名。
- Android Pad WebView origin，后续可能是 `https://restaurant-pad.local`。
- 本地开发 origin。
- 严格限制 wildcard。

### 2.4 验收标准

- cloud/prod 不能用 `X-User-Id` 访问业务 API。
- dev role switcher 在 cloud/prod 不可用。
- 默认 JWT secret 不能启动。
- 前端业务请求统一走 token。
- CORS 覆盖 REST 和 WebSocket。

---

## 3. P0：多店 Store Scope 和硬编码清理

### 3.1 问题

系统已经进入 `/stores/{storeId}/...` workspace 模式，但历史代码里仍有 `store_id=1`、legacy routes、默认 store、旧 API 参数路径。上云后要给多家店使用，必须先把 store scope 做成后端强约束。

### 3.2 目标

所有业务 API 都满足：

```text
CanAccessStore = authenticated_user + requested_store_id + StoreAccessService
```

URL 中的 storeId 只能作为 workspace 参数，不能作为安全边界。

### 3.3 Codex 任务

#### 任务 STORE-001：后端 API store scope 审计

扫描所有 controller/service：

- order
- menu
- frontdesk
- kds
- printing
- analytics
- admin
- staff
- audit
- owner overview
- platform

每个带 `store_id`、`storeId`、`orderId`、`printerId`、`menuItemId`、`tableId` 的 API 必须在 service 层校验：

1. 当前用户是谁。
2. 目标实体属于哪个 store。
3. 当前用户是否能访问该 store。
4. 当前角色是否能执行该 action。

不要只在前端 route guard 做判断。

#### 任务 STORE-002：前端 workspace 参数统一

所有 store-scoped 页面从 `useCurrentStore()` 取当前 store：

- `/stores/{storeId}/frontdesk`
- `/stores/{storeId}/frontdesk/menu`
- `/stores/{storeId}/frontdesk/order`
- `/stores/{storeId}/admin/...`
- `/stores/{storeId}/kds/...`
- `/stores/{storeId}/pickup`

禁止页面里直接写 `store_id=1`。

Legacy routes 只能 redirect，不应承载完整业务逻辑。

#### 任务 STORE-003：店铺访问测试

至少添加这些集成测试：

1. OWNER 可访问自己组织下多个 store。
2. MANAGER 只能访问 own store。
3. FRONTDESK 只能访问 own store 的 frontdesk/order。
4. 一个 store 的 orderId 不能被另一个 store 的用户读取。
5. 一个 store 的 printerId/menuItemId/tableId 不能被另一个 store 的用户修改。
6. URL 改 storeId 不能越权。

### 3.4 验收标准

- 前端不再有生产路径 `store_id=1`。
- 后端所有关键 API 都走 StoreAccessService。
- Legacy route redirect 到默认可访问 store。
- 多店权限测试通过。

---

## 4. P0：云端打印安全边界

### 4.1 问题

当前 Print Center 支持 `REAL / MOCK / PAD_DIRECT / DISABLED`。如果后端放到云端服务器，云服务器不能直接连接店内 `192.168.x.x` 打印机。继续让云端以 `REAL` 模式连 LAN 打印机会失败，甚至造成大量 socket timeout。

### 4.2 目标

上云前必须把打印模式和部署模式绑定清楚：

- 本地 Windows/Mac 单店试点：可以使用 `REAL` 直连 LAN printer。
- 云端服务器：不能直连店内私网 printer。
- 云端 + Android Pad：目标模式是 `PAD_DIRECT`。
- 云端但 Android App 未完成前：只能 `MOCK` 或 `DISABLED`，或者继续使用本地 Windows pilot 作为营业主系统。

### 4.3 Codex 任务

#### 任务 PRINT-001：增加 Cloud Profile 打印保护

当 profile 是 cloud/prod 时：

1. 如果 `stores.printing_mode=REAL` 且 printer IP 是私网地址：
   - 不要让系统不断 socket timeout。
   - 建议启动时 warning，运行时自动将 job 标记为 FAILED，error_code=`CLOUD_CANNOT_REACH_PRIVATE_PRINTER`。
   - 更严格的方案是配置开关：`app.printing.allow-real-private-lan=false`，cloud 默认 false。
2. Print Center 页面显示明显提示：
   - “云端服务器无法直接连接店内 192.168.x.x 打印机，请使用 PAD_DIRECT 或本地打印桥。”
3. `MOCK` 模式必须保留，用于云端功能验证。
4. `DISABLED` 模式必须保留，用于不自动打印。
5. `PAD_DIRECT` job 生成必须不打开 TCP socket。

#### 任务 PRINT-002：补打印失败闭环

当前可以手动 reprint，但没有自动 retry。上云前至少要补：

1. Print Center 首页明显显示：
   - 今日 failed jobs 数。
   - 最近失败原因。
   - 哪个 store / printer / module 失败。
2. 订单提交后，如果 GRAB ticket failed/cancelled：
   - 前台立即提示“厨房票未打印，请立即重打”。
3. failed job 列表支持一键 reprint。
4. reprint 不改变订单业务状态。
5. `PRINTED` 文案要清楚：代表 dispatch 成功，不保证物理出纸。

自动 retry scheduler 可以放到后续阶段，但失败可见性必须先做。

#### 任务 PRINT-003：Pad Direct 上云前准备，不实现完整 Android 壳

上云前可以先保证后端 Pad Direct API 是稳定的：

- device registration
- device token hash
- pending jobs
- claim lease
- payload
- complete/fail/release
- attempts
- Print Center device status

但 Android native shell 不作为本阶段完成项。不要让 Codex 在本阶段大规模写 Android UI，除非明确切到后续阶段。

### 4.4 验收标准

- 云端不会尝试直接连接店内私网打印机。
- MOCK/DISABLED/PAD_DIRECT 模式行为明确。
- 失败 print jobs 在 UI 可见、可重打。
- order submit 不因打印失败而失败。
- 打印失败不会静默丢失。

---

## 5. P0：同桌并发编辑保护

### 5.1 问题

当前两台 iPad 打开同一桌，会编辑同一个 backend order。submit 有防重复，但编辑仍是 last-write-wins，没有 optimistic locking，也没有 editor presence。

### 5.2 目标

上云前至少要避免静默覆盖。

### 5.3 Codex 任务

#### 任务 CONC-001：基于 revision 的乐观锁

当前文档已经有 `orders.current_revision` 和 update batch 概念。可以扩展为：

1. `GET /api/v1/orders/{id}` 返回：
   - `current_revision`
   - `updated_at`
2. 所有会修改 submitted/preparing/ready order 的 API 带：
   - `expected_revision`
   - 或 `If-Match` 风格 header。
3. 后端修改前检查 revision。
4. 不匹配时返回 409：
   - `ORDER_REVISION_CONFLICT`
   - message: `订单已被其他设备修改，请刷新后继续。`
5. 前端收到 409：
   - 不覆盖本地 UI。
   - 提示刷新。
   - 可提供“查看最新订单”。

#### 任务 CONC-002：draft 阶段最小保护

如果 draft order 也要保护，可以先做较轻量版本：

- item create/update/delete 返回最新 order revision。
- 前端每次 mutation 用最新 revision。
- 同一页面连续操作必须串行，不并发发多个 mutation。

#### 任务 CONC-003：不要在本阶段做复杂多人协作

本阶段不做：

- 实时光标。
- 编辑者在线头像。
- 分叉 cart。
- CRDT。

### 5.4 验收标准

- 两台设备同时编辑同一订单，不会静默覆盖。
- 冲突返回 409。
- 前端显示可理解中文提示。
- submit 仍保持已有幂等和防重复打印规则。

---

## 6. P1：Feature Package 与权限合并

### 6.1 问题

当前 feature flags 和 role permissions 是两套机制。前端也有静态 feature config。长期会造成前后端不一致。

### 6.2 目标

统一成：

```text
CanAccess = feature_enabled && role_has_permission && store_scope_allowed
```

### 6.3 Codex 任务

1. 后端提供 `/api/v1/auth/me` 或 `/api/v1/me/capabilities`，返回：
   - user
   - accessible stores
   - features
   - permissions/capabilities
2. 前端 route guard 从后端能力信息渲染，不自行决定权限。
3. 后端仍然是最终授权边界。
4. feature disabled 返回 403，错误码稳定。
5. owner/admin/manager/frontdesk/kitchen/pass/noodle roles 有明确权限矩阵。

### 6.4 验收标准

- 前端导航和后端权限一致。
- 禁用 KDS 时不挂载 KDS polling/WebSocket。
- 禁用 developer-tools 时诊断按钮和 dev API 都不可用。

---

## 7. P1：Platform / Owner / Developer Tools 边界整理

### 7.1 问题

Platform Admin、Owner Admin、Developer Tools 当前有边界混淆。某些 owner 页面使用 platform controller 中的接口，developer diagnostics 和正式 print center 也靠 feature flag 隔离。

### 7.2 目标

后端路径和权限分成三类：

1. Owner Admin：餐厅老板/经理日常维护。
2. Platform Admin：软件提供者/平台方低层配置。
3. Developer Tools：本地诊断、JSON editor、打印编码/font 测试。

### 7.3 Codex 任务

1. 新增或整理 controller package：
   - `owneradmin`
   - `platform`
   - `devtools`
2. Owner Admin API 不再依赖 platform-only JSON editor 风格接口。
3. Platform-only API 必须 require PLATFORM feature + platform/admin permission。
4. Developer API 必须要求 local/dev profile + developer-tools flag。
5. Print Center 中生产功能和诊断功能分开：
   - test connection、module test 可以保留。
   - encoding/font diagnostics 只在 developer-tools 开启时显示。

### 7.4 验收标准

- Owner 用户看不到 platform JSON editor。
- 生产环境看不到 developer diagnostics。
- API 层也不能访问 developer diagnostics。
- Owner Admin 页面仍能维护菜单、桌台、打印、员工、审计。

---

## 8. P1：前端 server-state 和实时同步治理

### 8.1 问题

当前很多页面自己维护 loading、saving、retry、polling、WebSocket、local optimistic update。随着多店、Pad、云端后，状态复杂度会快速上升。

### 8.2 目标

先不强行大重构，但要建立统一规则。

### 8.3 Codex 任务

#### 任务 FE-STATE-001：统一 API cache key

如果引入 TanStack Query，优先迁移：

1. menu catalog
2. order detail
3. frontdesk table board
4. print jobs
5. owner overview
6. reports summaries

不建议一次性迁移所有页面。先从只读/低风险 API 开始。

#### 任务 FE-STATE-002：统一实时刷新规则

建立统一策略：

1. WebSocket event 只表示资源变更，不携带复杂 UI 状态。
2. 收到 event 后 invalidate/refetch 对应 query。
3. refetch debounce。
4. tab hidden 暂停轮询。
5. 页面 unmount 清理 WebSocket subscription。
6. fallback polling 间隔不能随便写 4 秒，必须有理由。

#### 任务 FE-STATE-003：移除临时 CPU 诊断

上云前删除：

- `console.count("DineInPage render")`
- `syncFromBackend called` 之类临时诊断
- 高频 console log

保留正式错误日志和必要 debug 开关。

### 8.4 验收标准

- 多个 iPad 打开 frontdesk 不会持续 4 秒 hammer backend。
- 背景 tab 不轮询。
- KDS/pickup 后续有统一同步方案。
- 生产构建没有临时 console spam。

---

## 9. P1：生产任务模型迁移控制

### 9.1 问题

当前系统同时存在：

- `kitchen_tasks`
- `frontdesk_beverage_items`
- `production_tasks`

`production_tasks` 是 additive migration，但旧表仍是 runtime source of truth。双写阶段容易出现一致性问题。

### 9.2 目标

上云前不要半切换。必须明确当前事实：

```text
Cloud-ready v1 runtime source of truth remains kitchen_tasks + frontdesk_beverage_items.
production_tasks is dual-written for future migration and consistency validation.
```

### 9.3 Codex 任务

1. 增加 consistency checker：
   - 每个 submitted order 是否有对应 production_tasks。
   - production_tasks status 是否与旧表基本一致。
   - 只报警/记录，不改变业务状态。
2. 文档写清楚：当前 KDS、pickup、printing 读哪个表。
3. 不要在上云前把 KDS 大规模切到 production_tasks，除非另开专门 migration epic。
4. 为后续切换准备 ADR：
   - 目标表。
   - 回填策略。
   - 双读校验。
   - 切换开关。
   - 回滚策略。

### 9.4 验收标准

- 上云前 runtime source of truth 明确。
- 双写不静默失败。
- 不出现一部分页面读旧表、一部分页面读新表且无说明的情况。

---

## 10. P1：测试体系补强

### 10.1 问题

当前系统功能复杂，但测试覆盖不足。上云前必须补关键链路测试。

### 10.2 后端集成测试优先级

1. Auth / Store Scope：
   - OWNER / MANAGER / FRONTDESK / KDS roles。
   - 跨店访问 403。
2. Order submit：
   - draft -> submitted/preparing。
   - kitchen tasks 创建。
   - beverage items 创建。
   - inventory transaction 创建。
   - print jobs 创建。
3. Submitted order update：
   - update batch。
   - idempotency key。
   - update print jobs 只包含新增项。
4. Printing modes：
   - REAL config missing -> FAILED。
   - MOCK -> PRINTED without socket。
   - DISABLED -> CANCELLED。
   - PAD_DIRECT -> PENDING no socket。
5. Menu snapshot：
   - 改菜单名/价格不影响历史 order snapshot。
6. Audit：
   - 审计写失败不阻断主操作。
7. Migration smoke：
   - 空库 migrate 成功。
   - baseline 后 migrate 成功。

### 10.3 前端 E2E smoke 优先级

使用 Playwright 或同类工具，至少覆盖：

1. 登录 owner。
2. 进入 store workspace。
3. frontdesk 开桌。
4. 加菜。
5. 提交订单。
6. Print Center 能看到 GRAB / RECEIPT job。
7. Finish 后 table board 释放。
8. manager 不能访问其他 store。
9. frontdesk 不能访问 owner admin。
10. cloud profile 下 developer tools 不显示。

### 10.4 验收标准

- 上云前主分支必须跑过后端测试。
- 至少有一条完整 POS smoke E2E。
- migration 测试通过。
- 打印四种模式测试通过。

---

## 11. P0/P1：云端部署 Runbook

### 11.1 目标

Codex 需要生成一份可操作的部署文档，而不是只改代码。

### 11.2 内容要求

部署文档建议命名：

```text
doc/CLOUD_DEPLOYMENT_RUNBOOK.md
```

必须包含：

1. 环境变量：
   - `DB_HOST`
   - `DB_PORT`
   - `DB_NAME`
   - `DB_USER`
   - `DB_PASSWORD`
   - `JWT_SECRET`
   - frontend API base / origin
   - allowed CORS origins
2. profile：
   - local
   - pilot
   - cloud
   - prod
3. 数据库：
   - 创建库。
   - 跑 migration。
   - baseline 老库。
   - 备份。
   - 恢复。
4. 启动：
   - backend jar / systemd / docker compose，按项目实际选择。
   - frontend static build。
   - reverse proxy。
   - HTTPS。
5. 健康检查：
   - `/actuator/health` 或现有 health。
   - API smoke。
   - WebSocket smoke。
6. 打印模式选择：
   - local REAL。
   - cloud MOCK/DISABLED/PAD_DIRECT。
7. 上线前 checklist。
8. 回滚步骤。
9. 日志位置。
10. 常见故障：
    - 登录失败。
    - workspace 加载失败。
    - CORS 失败。
    - WebSocket 失败。
    - migration 失败。
    - 打印失败。

### 11.3 验收标准

- 一个新机器能按 runbook 部署成功。
- 一次数据库恢复流程被实际演练。
- 上线前 checklist 能被店内试点执行。

---

## 12. 上云前最终版推荐 PR 拆分

### PR-1：Migration Baseline

- 引入 Flyway。
- baseline schema。
- cloud/pilot ddl-auto 改 validate。
- migration runbook。

### PR-2：Production Security Guard

- 禁用 X-User-Id fallback。
- 禁用 dev role switcher。
- 禁用 dev default secret。
- 前端 API client 清理 legacy headers。

### PR-3：Store Scope Audit

- 所有 store-scoped API 后端校验。
- 前端移除 `store_id=1`。
- 多店权限测试。

### PR-4：Cloud Printing Guard

- 云端不直连 LAN printer。
- Print Center 云端提示。
- MOCK/DISABLED/PAD_DIRECT 行为回归测试。

### PR-5：Print Visibility and Reprint UX

- failed jobs 高亮。
- order submit 后 GRAB failed warning。
- recent/failed job reprint。

### PR-6：Concurrent Edit Guard

- order revision exposed。
- update mutation expected revision。
- 409 conflict。
- 前端冲突提示。

### PR-7：Owner/Platform/Dev Boundary

- API/package 边界整理。
- developer diagnostics production hidden and forbidden。
- owner admin 不依赖 platform-only API。

### PR-8：Frontend Sync Cleanup

- 移除临时 console diagnostics。
- 统一 polling visibility pause。
- 开始引入 query/cache 或至少统一 hook 策略。

### PR-9：Critical Integration Tests

- auth/store scope。
- order submit。
- print modes。
- update batch printing。
- migration smoke。

### PR-10：Cloud Deployment Runbook

- 部署文档。
- 备份恢复。
- smoke test checklist。
- rollback。

---

## 13. 不要让 Codex 做的事

为了避免跑偏，明确禁止：

1. 不要在本阶段重写订单状态机。
2. 不要引入 Stripe/Square/Moneris 等 payment provider。
3. 不要实现 refund。
4. 不要把 split bill 变成正式后端账单。
5. 不要把 cloud backend 配成直连 `192.168.x.x` 打印机。
6. 不要在生产 profile 打开 `X-User-Id` fallback。
7. 不要继续新增 `store_id=1` 硬编码。
8. 不要让 owner admin 调 platform-only JSON editor。
9. 不要大规模切换 `production_tasks` runtime source of truth。
10. 不要在上云前开始写完整 Android App 壳，除非上云前任务已经完成。

---

## 14. 上云前 Definition of Done

上云前最终版必须满足：

- [ ] 数据库 migration 可控，cloud/prod 不用 `ddl-auto=update`。
- [ ] JWT secret 和默认密码不使用 dev 值。
- [ ] cloud/prod 禁用 `X-User-Id` fallback。
- [ ] cloud/prod 禁用 dev role switcher。
- [ ] 前端无生产路径 `store_id=1` 和 `X-User-Id`。
- [ ] 所有关键 API 做 store access 校验。
- [ ] 云端不直连私网打印机。
- [ ] Print Center 可见 failed jobs，并支持 reprint。
- [ ] 同桌并发编辑不会静默覆盖。
- [ ] 临时 console CPU 诊断已移除。
- [ ] migration smoke 测试通过。
- [ ] 订单提交 + 打印 job + table occupancy smoke 通过。
- [ ] 多角色多店权限测试通过。
- [ ] 部署 runbook、备份、恢复、回滚文档完成。



---

# Part 2：上云后路线图与 Android App 开发规划


> 前提：先完成《上云前最终版本架构整改计划》。  
> 当前阶段暂不重做 complete/order lifecycle，不做 payment/refund。老板看利润的需求继续通过订单、成本、analytics summary、profit report 解决。

---

## 总体路线

推荐顺序：

```text
阶段 0：上云前最终版 hardening
阶段 1：云端部署 MVP
阶段 2：Android Pad App 壳基础
阶段 3：Pad Direct 本地打印闭环
阶段 4：多店规模化使用
阶段 5：架构债清理和性能升级
阶段 6：后续商业化能力
```

不要跳过阶段 0 直接做 Android 壳。否则 Android App 会把现有硬编码、权限漏洞、数据库迁移风险一起带到更多门店。

---

## 阶段 0：上云前最终版 Hardening

详见：`FINAL_PRE_CLOUD_SERVER_HARDENING_PLAN.md`

核心目标：

1. 数据库 migration。
2. 生产安全。
3. store scope。
4. 云端打印边界。
5. 打印失败可见和 reprint。
6. 同桌并发防护。
7. 测试和部署 runbook。

完成标准：

- 可以稳定部署到一台云端服务器。
- 可以支持 owner/frontdesk/manager 多角色登录。
- 可以支持多 store workspace，但先不要求复杂 SaaS onboarding。
- 云端不直接连店内私网打印机。
- 打印失败不会静默。

---

## 阶段 1：云端部署 MVP

### 1.1 目标

把系统从本地 Windows/Mac 试点，迁移到云端服务器运行：

- backend 在云端。
- frontend 静态资源在云端或同服务器。
- PostgreSQL 在云端或托管数据库。
- 店内 iPad/电脑通过 HTTPS 访问。
- 打印暂时使用 MOCK/DISABLED，或等待 Android Pad Direct。

### 1.2 推荐交付项

#### CLOUD-001：部署结构

可选方案：

1. 单 VPS：
   - Nginx/Caddy 反代。
   - Spring Boot backend。
   - frontend dist。
   - PostgreSQL。
2. 更稳方案：
   - backend VPS。
   - managed PostgreSQL。
   - object storage / backup。

MVP 可先单 VPS，但必须有备份和恢复演练。

#### CLOUD-002：HTTPS 和域名

必须：

- HTTPS。
- 固定域名。
- CORS 只允许正式域名和明确开发域名。
- WebSocket `/ws` 通过反代可用。

#### CLOUD-003：环境变量和 secret

必须通过环境变量或 secret manager 注入：

- DB password。
- JWT secret。
- allowed origins。
- profile。
- seed overwrite 禁用。

#### CLOUD-004：监控和日志

最低要求：

- backend 日志可查看。
- migration 日志可查看。
- print job failure 可在 UI 看到。
- auth failure 不打印密码/token。
- 每日数据库备份。

#### CLOUD-005：上云 smoke test

上线后立即测试：

1. 登录 owner。
2. 进入 owner dashboard。
3. 进入 store frontdesk。
4. 开桌。
5. 加菜。
6. 提交订单。
7. 产生 print jobs。
8. Print Center 可看到 job。
9. Finish 后桌台释放。
10. 退出登录。

### 1.3 本阶段不要做

- 不要强行接真实 LAN 打印机。
- 不要做完整 Android App。
- 不要做 payment/refund。
- 不要做复杂 tenant billing。

---

## 阶段 2：Android Pad App 壳基础

### 2.1 目标

创建可安装的 Android Pad App，让餐厅员工不再依赖浏览器地址栏访问系统。

### 2.2 架构方向

推荐沿用已有规划：

- `Restaurant_System` 继续作为 Web + Backend source of truth。
- 独立 `restaurant-pad-app` 承载 Android WebView / Capacitor shell。
- Pad App 复用当前 React frontend build，不重写 POS UI。
- Android 原生只负责设备能力：本地打印、网络状态、设备注册、可能的 kiosk/全屏。

### 2.3 Pad App 基础能力

#### PAD-001：Android Shell

需要：

- WebView/Capacitor shell。
- 加载 bundled frontend 或远程 cloud URL。
- 支持配置 API base URL。
- 支持路由 fallback 到 `index.html`。
- 支持 HTTPS。
- 支持 app version 显示。

#### PAD-002：设备注册

流程：

1. 店长/经理登录 cloud web。
2. 在 Print Center 或 Device Center 注册 Pad。
3. Pad 获得一次性 device token。
4. Android 安全存储 device token。
5. 后端只保存 hash。

#### PAD-003：登录和 workspace

Pad App 可以复用现有账号登录：

- access token / refresh token。
- store workspace。
- role redirect。
- frontdesk/kds/pickup 根据权限进入。

#### PAD-004：Pad Runtime 设置页

最少包括：

- API base URL。
- 当前店铺。
- 当前登录用户。
- 当前设备 ID。
- 当前打印模式。
- App version。
- 网络状态。
- 清缓存/重新登录。

### 2.4 本阶段不要做

- 不要重写 POS UI 成原生 Android。
- 不要绕过后端业务规则。
- 不要让 Android 直接改数据库。
- 不要在 Android 里 hardcode store_id。

---

## 阶段 3：Pad Direct 本地打印闭环

### 3.1 目标

云端后端创建 print jobs，Android Pad 在店内 LAN 上 claim job，本地连接 ESC/POS printer，打印后回报结果。

### 3.2 核心原因

云端服务器不能直接连接店内 `192.168.x.x` 打印机。Pad Direct 是正确方向：

```text
Cloud Backend -> durable print_jobs -> Android Pad claim -> LAN printer -> complete/fail report
```

### 3.3 后端能力

必须稳定：

- `store_devices`
- device token hash
- `print_jobs` status
- `CLAIMED` lease
- pending jobs API
- claim API atomic update
- payload API only for claimed device
- complete/fail/release API
- attempts history
- device last seen
- Print Center device status

### 3.4 Android 能力

#### PRINT-PAD-001：Worker

Pad 需要后台/前台 worker：

1. 定时请求 pending jobs。
2. claim 一个 job。
3. 拉 payload。
4. 调用本地 ESC/POS TCP printer。
5. 成功后 complete。
6. 失败后 fail，带错误信息。
7. 可释放 claim。

#### PRINT-PAD-002：打印机配置

两种策略：

1. 后端 Print Center 保存 printer IP/port，Pad 使用 job 里的 printer config。
2. Pad 本地保存 printer IP/port，后端只负责 job routing。

建议短期采用方案 1：配置仍在 Print Center，Pad 只执行。

#### PRINT-PAD-003：错误可见

Android App 必须显示：

- printer unreachable。
- paper/connection unknown。
- claim expired。
- token invalid。
- backend unreachable。
- last print success/failure。

#### PRINT-PAD-004：避免重复打印

必须依赖后端 atomic claim：

- 一个 job 同一时间只能一个 device claim。
- lease 过期后可重新 claim。
- complete 后不能再 claim。
- reprint 创建新 job 或明确 reprint attempt。

### 3.5 验收标准

- 云端 backend 不开 TCP printer socket。
- Pad 在店内网络能打印 GRAB 和 FRONTDESK_RECEIPT。
- 断网/失败后 job 状态可见。
- 同一 job 不会被两个 Pad 重复打印。
- Print Center 能看到 device 和 job 状态。

---

## 阶段 4：多店规模化使用

### 4.1 目标

支持一个老板管理多家店，每家店有自己的：

- 菜单。
- 桌台。
- 打印机。
- 员工。
- 报表。
- KDS 配置。
- Feature Package。

### 4.2 多店核心能力

#### MULTI-001：Owner Home

已有 `/owner/dashboard` 方向，后续加强：

- 每家店今日销售。
- 今日订单数。
- active orders。
- occupied tables。
- failed print jobs。
- printing mode。
- 快速进入店铺 workspace。

#### MULTI-002：Store Switcher

在单店 workspace 内切店：

- 保留当前模块路径。
- 切换 storeId。
- 重新加载 StoreContext。
- 没权限则跳 owner home 或 access denied。

#### MULTI-003：新店 onboarding

从 template 创建：

- stations。
- dining tables。
- menu categories。
- menu items/options。
- roles。
- KDS configs。
- default printing assignments。

注意：不要复制历史订单、支付、打印 job。

#### MULTI-004：权限矩阵

最少：

- OWNER：组织下全部店。
- MANAGER：指定店。
- FRONTDESK：指定店 frontdesk。
- HOT_KITCHEN/NOODLE/PASS：指定店 KDS/pickup。
- PLATFORM ADMIN：平台管理，不等于餐厅 owner。

### 4.3 数据隔离测试

每次新增多店功能必须测：

- A 店员工不能读 B 店订单。
- A 店员工不能改 B 店菜单。
- A 店打印配置不能影响 B 店。
- Owner all-store dashboard 只汇总可访问门店。
- Reports query 必须 store/organization scoped。

---

## 阶段 5：架构债清理和性能升级

### 5.1 production_tasks 统一

当前保持旧表为 runtime source of truth。后续可以启动专门 epic：

1. 双写一致性 checker。
2. 历史数据回填。
3. KDS 读 production_tasks shadow endpoint。
4. 双读对比。
5. feature flag 切换。
6. 旧表只作为 compatibility。
7. 最后删除旧写路径。

### 5.2 前端 server-state

建议逐步引入 TanStack Query：

1. reports。
2. print jobs。
3. menu catalog。
4. order detail。
5. table board。
6. KDS feeds。

目标：

- 统一 cache。
- 统一 refetch。
- WebSocket invalidate。
- 减少 N+1。
- 减少重复 loading/saving 逻辑。

### 5.3 KDS 性能和实时性

后续优化：

- KDS 使用 WebSocket-led + fallback polling。
- 去掉每个 order detail N+1。
- 后端返回 KDS screen-specific read model。
- 每个屏幕只拿自己需要的数据。
- KDS polling interval 按 feature/visibility 控制。

### 5.4 菜单语义 metadata 化

彻底减少中文名称 fallback：

- `option_code`
- `option_group`
- `print_token`
- `kitchen_token`
- `station_rule`
- combo parent/child metadata

UI 名称只用于展示，不参与业务判断。

### 5.5 报表近实时

当前老板看利润足够，但后续如果多店使用，需要：

- 当前日 summary 增量更新。
- summary freshness 标记。
- dashboard 和 reports 数据来源一致。
- rebuild job 可重跑。
- 报表页面显示“最后更新时间”。

---

## 阶段 6：后续商业化能力

这些可以以后做，不要影响当前上云：

1. 正式 payment provider。
2. refund。
3. 小费、服务费、折扣券。
4. 正式 split bill persistence。
5. SaaS subscription/billing。
6. 店铺自助 onboarding。
7. 插件市场。
8. 多语言后台。
9. 离线点单。
10. 复杂库存采购。

---

## 推荐时间顺序

### 第 1 轮：上云前

- Migration。
- Security。
- Store scope。
- Printing cloud guard。
- Concurrency guard。
- Tests。
- Runbook。

### 第 2 轮：云端 MVP

- Deploy cloud。
- HTTPS/domain。
- Backup/restore。
- Smoke test。
- 店内试用 cloud web。

### 第 3 轮：Android App 壳

- WebView shell。
- API base config。
- Login/session。
- Store workspace。
- App full screen/kiosk。

### 第 4 轮：Pad Direct

- Native TCP print。
- Device register。
- Claim/payload/complete/fail。
- Print Center device status。
- 店内打印试点。

### 第 5 轮：多店推广

- Owner Home 加强。
- Store onboarding。
- Staff/store membership 管理。
- Reports 多店一致性。

### 第 6 轮：长期架构优化

- production_tasks 统一。
- server-state。
- KDS read model。
- 菜单 metadata。
- 报表近实时。

---

## 阶段切换门槛

不要只按功能完成切阶段，要按稳定性切。

### 可以开始云端部署的门槛

- migration 完成。
- production security 完成。
- store scope 完成。
- cloud printing guard 完成。
- smoke test 通过。

### 可以开始 Android 壳的门槛

- cloud web 稳定登录。
- store workspace 稳定。
- API CORS/WebSocket 稳定。
- print job 后端稳定。
- 没有生产路径硬编码 store/user。

### 可以开始多店推广的门槛

- 一个老板多店 dashboard 可用。
- manager/frontdesk store scope 测试通过。
- 新店 template onboarding 可重复。
- 每店打印配置隔离。



---

# Part 3：Codex Skill / Guardrails


> 用途：把本文件作为 Codex / coding agent 的工程执行守则。  
> 目标：让 Codex 沿着当前餐饮点单系统的上云前大纲开发，不要跑偏，不要提前做 payment/refund，不要破坏现有 POS/打印/多店基础。

---

## 1. Mission

You are working on a restaurant POS / ordering system that is being prepared for cloud deployment first, and Android Pad shell development later.

Your mission is to harden the current system into a cloud-ready version while preserving the current restaurant workflow:

- Frontdesk opens table or takeout order.
- Staff adds items.
- Staff submits order.
- Print jobs are created.
- Kitchen/frontdesk uses current operational flow.
- Staff clicks Finish when the current restaurant workflow is done.
- Owner uses dashboard/reports to see sales, cost, and profit.

Do not redesign the business into a full payment/refund accounting POS during this phase.

---

## 2. Non-Negotiable Scope Rules

### 2.1 Do not change in this phase

Do NOT implement or refactor these unless the human explicitly asks in a new task:

1. Do not redesign the order lifecycle.
2. Do not change the current meaning of `completeOrder` / `Finish`.
3. Do not implement online payment provider integration.
4. Do not implement refund flow.
5. Do not persist split bill as formal backend bills/payments.
6. Do not rewrite the POS UI as native Android.
7. Do not switch KDS runtime source of truth from current tables to `production_tasks` without a dedicated migration epic.
8. Do not remove existing `MOCK`, `REAL`, `PAD_DIRECT`, or `DISABLED` printing modes.

### 2.2 Must protect

Always protect these existing behaviors:

1. Existing frontdesk ordering flow.
2. Existing menu customization flow.
3. Existing submit order behavior.
4. Existing GRAB and FRONTDESK_RECEIPT job creation.
5. Existing owner dashboard and profit reporting intent.
6. Existing store workspace routing.
7. Existing role-based access behavior.
8. Existing manual reprint behavior.
9. Historical order snapshots.
10. Soft-delete behavior for menu items/options.

---

## 3. Production Safety Rules

### 3.1 Authentication

For cloud/prod code paths:

- `X-User-Id` fallback must be disabled.
- Dev role switcher must be disabled.
- Developer tools must be disabled unless active profile is local/dev and config explicitly enables them.
- JWT secret must come from environment/secret manager.
- Default dev JWT secret must fail startup in cloud/prod.
- Do not log raw passwords, access tokens, refresh tokens, or device tokens.

### 3.2 Authorization

Every store-scoped backend operation must verify:

```text
authenticated_user + requested_resource + resource_store_id + StoreAccessService + permission/capability
```

Never trust frontend route guards as security.
Never trust URL `storeId` as authorization.
Never assume `store_id=1`.

### 3.3 Frontend API usage

All frontend services must use the shared API client.

Do not add:

```ts
headers: { "X-User-Id": "1" }
```

Do not add hardcoded:

```ts
store_id=1
```

Use store context / current workspace instead.

---

## 4. Database Rules

### 4.1 Migration

Cloud/prod must not rely on Hibernate `ddl-auto=update`.

Required direction:

- Use Flyway or Liquibase.
- Keep migrations versioned.
- Use `ddl-auto=validate` or `none` outside local development.
- Add indexes/constraints through migrations.
- Write data repair migrations before adding constraints if existing data may be dirty.

### 4.2 Seed safety

Runtime seeding must not overwrite owner-managed production data unless an explicit force flag is enabled in local/dev.

Cloud/prod default:

```yaml
app.seed.force-overwrite: false
```

Never reset production menu prices, menu option names, printer assignments, dining tables, or staff data on restart.

### 4.3 Historical snapshots

Orders and receipts rely on snapshot fields.

Do not rewrite old orders when menu names/options/prices change.
Menu item and option deletion should remain soft delete.

---

## 5. Printing Rules

### 5.1 Cloud printing boundary

A cloud server cannot directly connect to store-private printers such as:

```text
192.168.x.x
10.x.x.x
172.16.x.x - 172.31.x.x
```

Therefore:

- Local Windows/Mac pilot may use `REAL` LAN printing.
- Cloud deployment must use `MOCK`, `DISABLED`, or `PAD_DIRECT` until a local bridge/Android Pad printer worker exists.
- In cloud/prod, do not create code that repeatedly socket-connects to private LAN printers.
- Add clear error messages and Print Center warnings for cloud/private-printer mismatch.

### 5.2 Print job reliability

Order submission must not fail because printing fails.

Instead:

- Create print jobs.
- Record failures.
- Show failures in Print Center.
- Allow manual reprint.
- Warn frontdesk if GRAB kitchen ticket failed/cancelled shortly after submit.

### 5.3 Print modes

Preserve semantics:

- `REAL`: renderer + TCP transport.
- `MOCK`: renderer runs, no socket, job marked printed for local/cloud testing.
- `PAD_DIRECT`: backend creates jobs/payload, leaves job pending for Pad claim; no backend socket.
- `DISABLED`: automatic printing off; jobs cancelled with clear reason.

### 5.4 Pad Direct

When working on Pad Direct:

- Device registration returns raw token once.
- Backend stores only token hash.
- Runtime device auth uses device id + token.
- Claim must be atomic.
- Payload only visible to claiming device.
- Complete/fail/release must record attempts.
- Avoid duplicate printing.

---

## 6. Store Workspace Rules

Use `/stores/{storeId}/...` routes for active workspaces.

Legacy routes may redirect, but should not become new business logic entry points.

Every module must receive active store from workspace context:

- frontdesk
- menu
- order
- printing
- reports
- staff
- audit
- KDS
- pickup

Never introduce new hidden defaults to store 1.

---

## 7. Concurrency Rules

Same-table concurrent editing currently attaches to the same backend order. Do not silently overwrite.

Preferred approach:

- Expose `current_revision` on order reads.
- Mutations include expected revision.
- Backend returns `409 ORDER_REVISION_CONFLICT` if stale.
- Frontend shows: `订单已被其他设备修改，请刷新后继续。`

Do not implement complex CRDT/editor-presence unless explicitly requested.

---

## 8. Feature / Permission Rules

The target rule is:

```text
CanAccess = feature_enabled && role_has_permission && store_scope_allowed
```

Frontend hiding is convenience only.
Backend must enforce.

Developer tools require both:

- local/dev profile
- developer-tools enabled

Platform admin is not the same as restaurant owner admin.
Do not expose platform JSON editor to restaurant owner users.

---

## 9. Frontend State and Realtime Rules

Avoid uncontrolled polling and duplicated requests.

Rules:

1. Prefer WebSocket-led updates with debounced refetch.
2. Polling is fallback, not the primary high-frequency strategy.
3. Pause polling when `document.visibilityState !== 'visible'`.
4. Clean up subscriptions on unmount.
5. Do not add 4-second polling unless justified.
6. Remove temporary console CPU diagnostics before production.
7. Consider TanStack Query or an equivalent server-state pattern for shared reads.

---

## 10. Testing Rules

Any change touching backend business behavior must include or update tests.

Minimum critical flows:

1. Auth and store scope.
2. Order submit creates tasks and print jobs.
3. Submitted order update creates update print jobs.
4. Printing modes: REAL/MOCK/PAD_DIRECT/DISABLED.
5. Cross-store access is forbidden.
6. Menu snapshot history remains stable.
7. Audit failures do not block main operations.
8. Migration smoke test.

Frontend smoke tests should cover:

1. Login.
2. Store workspace routing.
3. Open table.
4. Add item.
5. Submit order.
6. Print Center job visible.
7. Finish releases table.
8. Role access denied.

---

## 11. Implementation Workflow for Codex

For every PR/task:

1. Read relevant existing code first.
2. Determine whether feature is runtime-active, POC, planned, or deprecated.
3. Write a short impact summary.
4. Make the smallest safe change.
5. Add migration if schema changes.
6. Add or update tests.
7. Update documentation if behavior/config changes.
8. Run backend tests.
9. Run frontend build/tests if frontend changed.
10. Report risks and rollback steps.

Do not bundle unrelated architecture changes into one large PR.

---

## 12. Recommended PR Sequence

Follow this order unless the human says otherwise:

1. Migration baseline.
2. Production security guard.
3. Store scope audit and hardcoded store cleanup.
4. Cloud printing guard.
5. Print failure visibility and reprint UX.
6. Concurrent edit guard.
7. Owner/Platform/Developer boundary cleanup.
8. Frontend sync cleanup.
9. Critical integration tests.
10. Cloud deployment runbook.
11. Android Pad shell.
12. Pad Direct local printing.
13. Multi-store onboarding.

---

## 13. Output Format Expected from Codex

When proposing or implementing changes, respond with:

```md
## Summary
What changed and why.

## Scope
What is included and explicitly excluded.

## Files Changed
List important files.

## Database Migration
Migration files and rollback notes.

## Security Impact
Auth/store/permission impact.

## Testing
Commands run and results.

## Manual QA
Step-by-step restaurant workflow to verify.

## Risks
Known risks and mitigations.
```

---

## 14. Red Flags

Stop and ask for confirmation if a task would:

1. Change order lifecycle semantics.
2. Introduce payment/refund.
3. Remove printing modes.
4. Allow cloud server to direct-connect to private LAN printers.
5. Enable dev auth bypass in cloud/prod.
6. Add new `store_id=1` production code.
7. Hard delete menu/options/orders.
8. Switch KDS runtime source of truth to `production_tasks`.
9. Expose developer diagnostics to production users.
10. Skip migration for schema change.
