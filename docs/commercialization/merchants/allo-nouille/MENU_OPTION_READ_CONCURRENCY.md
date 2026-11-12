# Allô 菜单选项并发读取验收

**CLI/API concurrency PASS；不是浏览器验收，也不是 Production 验收。**

2026-11-12 `15:39:42Z`–`15:39:44Z`，复用原合成 QA 数据库，通过正常合成
Owner 登录后执行顺序基线、两轮 34 路菜单选项 GET 和穿插的 health GET。
本次没有创建、修改、提交或 Finish 订单，没有菜单/配置/Job 写入；正常登录
产生的认证 session/audit 属于已授权副作用，不在业务零变更断言内。

## 精确运行身份

| 项目 | 本次证据 |
| --- | --- |
| Source | `23f3e804713ad633f1d95f10d1b0cfebfcf31edf` |
| JAR SHA-256 | `d4b9473fce4d7bfaf35c6a5f1ace4e83bc5dc0ab24fe9c04a616c9187154f522` |
| QA backend | PID `74928`，`http://127.0.0.1:18512` |
| QA PostgreSQL | `127.0.0.1:26512/restaurant_allo_nouilles_test` |
| 安全边界 | 原合成 QA、Store 均为 MOCK、printer endpoint 为空；无 SSH/Production/物理打印 |
| 连接配置 | 原 Allô profile `maximum-pool-size=4`，未扩大 pool，OSIV 仍启用 |

验收同时核对了受保护 runtime process metadata、PID 启动身份、实际 JAR
digest 和 `/stores/1/template/runtime-safety` 返回的 build SHA。数据库读取
复用既有 `ReadOnlyDatabase`，使用 `PGOPTIONS=default_transaction_read_only=on`。
没有重启、构建、重新导入或重建 QA。

## 原因及最小修复

旧 `e7dde0a` 复现的私有线程 dump
`qa-options-pool-repro-thread-dump.txt` 中有 4 个请求停在
`OwnerMenuOptionServiceImpl.getOptions → StoreAddonOptionResolver.resolve →
JdbcTemplate → HikariPool.getConnection`。其 SHA-256 为
`039ca56672bee099a5ddd9bed3a9d2f42aa6cde16879e7cec580f8b3834ceefe`。
协调者同时只读确认 PostgreSQL 的 4 条连接均为 idle/ClientRead，并非数据库锁。
旧 QA 日志保存了 `total=4, active=4, idle=0` 的 Hikari 30 秒等待超时。

旧选项读取没有覆盖完整 JPA/JDBC 调用的事务；OSIV 并发请求占用 JPA 连接后，
canonical JDBC lookup 再等第二条连接，导致小 pool 饥饿。修复仅在现有
`getOptions` 增加 `@Transactional(readOnly=true)`，让实际 JpaTransactionManager
中的 repository/JDBC 读取共享事务连接。没有改价格、权限、schema、pool 或
全局 OSIV 设置。

轻量 Spring 行为回归已证明两并发只读事务复用连接；移除注解时该测试因实际
事务未激活而失败，恢复后通过。本节下面的真实 HTTP 验收进一步覆盖了该测试
使用 mock repository 时无法证明的实际 JPA/OSIV 组合。

## 实际 HTTP 结果

从 `GET /api/v1/admin/platform/menu/items?store_id=1` 获取当前 34 个 active
same-Store Item IDs，再按原 UI 使用的
`GET /api/v1/admin/menu/items/{itemId}/options` 建立完整业务响应 `data` 的顺序基线，
使用规范 JSON 计算 hash；HTTP 状态和成功 envelope 另行校验。
并发阶段各有 34 个 options GET 同时释放，并穿插两次 health GET。

| 阶段 | Options HTTP 200 | 响应与逐 Item 基线相同 | 整轮耗时 | 最慢 options 请求 | 穿插 health |
| --- | --- | --- | --- | --- | --- |
| 顺序基线 | 34/34 | 基线 | 合计 229.211 ms | 14.316 ms | 前置 200 / 1.455 ms |
| 并发第 1 轮 | 34/34 | 34/34，完整 data SHA-256 一致 | 114.732 ms | 84.765 ms | 200 / 36.981 ms；200 / 26.577 ms |
| 并发第 2 轮 | 34/34 | 34/34，完整 data SHA-256 一致 | 74.724 ms | 48.574 ms | 200 / 31.828 ms；200 / 2.360 ms |
| 后置 health | — | — | — | — | 200 / 1.861 ms |

共 102 次 options GET、6 次阶段 health GET 全部成功。并发期间 4 次 PostgreSQL
采样观察到 JDBC 连接最多 4 条；排除采样 psql 自身后，全部其他 client backend
连接最多也是 4 条。此为短时采样及固定 pool 配置证明，不是持续负载容量认证。
从执行前 log byte offset 到执行结束，没有新增 `Connection is not available`
或 `request timed out after` 日志。

## 业务数据保护

执行前后读取并比较完整行的逐行 SHA-256、整表 SHA-256 和行数，20 张表共
378 行完全一致。不是只比较价格或几个业务字段。

- 菜单/配置：`stores`、`stations`、`menu_categories`、`menu_items`、
  `menu_item_options`、`template_item_option_groups`、`template_store_configuration`、
  `organization_addon_definitions`、`store_addons`、`store_pricing_policies`、
  `store_combo_groups`、`store_combo_components`。
- 订单/执行/快照：`orders`、`order_items`、`order_item_options`、
  `order_update_batches`、`order_dispatch_outbox`、`print_jobs`、`kitchen_tasks`、
  `frontdesk_beverage_items`。

包含全部当前 10 张合成订单、28 个 PrintJobs、28 个 Outbox 行，均无字段变化。
原 active Order `8` 的完整行另行比较保持一致，状态仍为 `preparing`，没有 Finish。
菜单仍为 34 items / 156 physical option rows / 13 canonical Add-ons；这些是现有
合成 QA 的计数，不能与 Production 的 188 physical options 混为一组证据。

## 证据与剩余范围

私有完整请求时延、响应 hash、连接采样和前后逐行 hash：
`qa-menu-option-read-concurrency-23f3e804-1794497982962726000.json`
（权限 `0600`），SHA-256：
`f24454935cacb52815038e9a11ff0a923a043d59f6ab4b41d20f53b1b6a733bf`。
执行脚本仅保存在私有 `qa_options_read_concurrency_23f3e804.py`，复用现有
`runtime`、`five_orders.Api` 和只读数据库 helper；没有新增部署工具或源代码改动。
证据不保存密码、access token 或 refresh token。

**实际 Combo UI 页面刷新、候选加载与交互的浏览器证明仍由协调者继续。**
本次 PASS 不替代该浏览器门槛，不授权 Production 切换，也不证明真实 Pad 或实体打印。
