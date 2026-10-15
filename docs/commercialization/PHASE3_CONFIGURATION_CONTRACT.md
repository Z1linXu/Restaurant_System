# Phase 3 Merchant Configuration Contract

范围：隔离 Commercial 开发源码，schema v1；不授权部署、真实商家初始化或 Phase 4。
实现入口为 `com.restaurant.system.merchant.MerchantConfigurationService`。Root 负责最终整合验收。

## 来源与归属

| 层级 | Source of Truth | 本轮可覆盖范围 |
| --- | --- | --- |
| Platform | 现有 feature/runtime policies、Role capability presets | Merchant API 不覆盖；ADMIN 无 merchant grant |
| Organization | `organizations`、active OWNER memberships、Profile root visibility/owner | 沿用现有管理与授权；没有隐式 Organization 配置继承 |
| Store | `stores` 身份/组织/时区/生命周期；`store_merchant_configurations` brand/locale/runtime Profile association | Owner 仅写 brand/locale；其余只读 |
| Modules | `store_modules` 和现有 Module contract | 沿用 modules API；新配置不复制 flags |
| Device/Printer/Table/Station | 现有 Store-local entities、设备凭据和授权 API | 只读资源指针；不接受第二份 mutable graph、endpoint 或 secret |
| User | 现有 active User/Role/memberships、Auth Provider | 无 User-level merchant override；不能从 roleId 再扩权 |
| Menu Item/Option/Combo | 现有 menu/options/combo/pricing/addon entities 与 API | 只读资源指针；不存价格、菜品 graph、recipe 或 Profile 私有 artifact |

优先级不是通用 cascading engine。每个字段只有一个写来源；冲突由所属 API 拒绝。
Brand 是显示元数据，不改 `Store.name`、历史订单商家快照或小票 payload。Locale 是语言标签，
不声明已有全部语言翻译。Timezone 读 `Store.timezone`；缺值沿用当前 daily-close 默认
`app.daily-close.default-timezone`（默认 `America/Toronto`），不另存 timezone 真值。

## 安全读取

`MerchantConfigurationService.isLanzhouCompatible(Long)` 只有在持久化 schema v1 行有效且精确为
`ST_DENIS_CANONICAL_PROFILE@v2` / `PROVISIONAL_COMPATIBILITY` 时返回 true。
缺行、未知 schema、损坏 Profile/version/mode 组合均 false；Store 名称、SKU、Org 数字 ID、
旧 master key 和 runtime marker 都不触发运行时自动认领。

`safeSummary(Long)` 为内部 typed reader，调用者先授权当前 Store。它不读 Profile/artifact 表，
只投影以下 9 项，接入现有 StoreContext 和 MenuCatalog 的 `safe_summary`：

```json
{
  "schema_version": 1,
  "config_revision": 1,
  "brand_name": "Synthetic Restaurant",
  "locale": "en-CA",
  "currency": "CAD",
  "timezone": "America/Toronto",
  "tax_policy_reference": "ca-qc-tax-2026-01",
  "profile_code": "NEUTRAL_RESTAURANT",
  "compatibility_mode": "NEUTRAL"
}
```

`compatibility_mode` 仅 `NEUTRAL` / `PROVISIONAL_COMPATIBILITY`。缺行或坏配置读取为
Neutral、revision 0、Store 名称及安全元数据默认值；不会写库或恢复兰州配置。修复无效的持久化配置需
受控修复，PUT 不将损坏行静默改成另一 Profile。现有 Store 的明确兼容迁移见 Profile design。

Currency/tax reference 是当前计算政策的只读标识：CAD 与 `ca-qc-tax-2026-01`。
本轮无多币种、任意税率、税务管辖、舍入、优惠或价格 TTL 引擎，不改变当前金额或时间计算。

## 管理 API

`GET /api/v1/stores/{storeId}/merchant-configuration`

要求 Bearer active user、effective Store OWNER 和 `ADMIN_STORE_CONFIG`。返回标准 `ApiResponse`，
data 为 `store_id`、`organization_id`、`safe_summary`、`configuration_source`、`resource_references`。
Summary 供普通已授权业务读取；完整管理对象/资源指针只在这个 Owner API 返回。

References 为 `{schema_version:1, resource_type, api_path}`，只指向已存在的同店 APIs：
modules、MenuCatalog、platform stations/dining-tables/item-options、printing overview、combo-configuration。
其中 `/api/v1/admin/printing?store_id=...` 是现有 `OwnerPrintingController.getOverview`。
指针不加载目标业务数据，也不授予访问权；目标 API 继续验证 Store/capability/module。
不存在新导入 executor、任意 URL pointer 或从外店实体重绑定的 API。

`PUT /api/v1/stores/{storeId}/merchant-configuration`

是完整替换两个可写元数据的操作，完整 body 必须恰好为：

```json
{"schema_version":1,"expected_config_revision":1,"brand_name":"Synthetic Brand","locale":"fr-CA"}
```

JSON object、字段集合、整数类型/范围、schema 精确值、非空最多 255 字符 brand、有效最多 64 字符
BCP47 locale 都由服务校验。未知字段、缺字段、null、字符串冒充版本、浮点 revision 均 400。
`store_id`、`organization_id`、Profile、timezone、currency、tax policy、station/table/printer/device/menu/
option/combo refs 都不是可写字段；即使它们声称本店 ID 也拒绝，foreign IDs 不会触发目标 lookup。

先授权，再锁 `Store`；对可能由 OSIV 缓存的实体 `refresh(PESSIMISTIC_WRITE)`，重新核实际 Store scope。
revision CAS 不匹配返回 409；成功在同一事务增加 config revision，写现有 Audit action
`MERCHANT_CONFIGURATION_UPDATED` 并增加现有 menu revision。并发同 revision 只能一次成功。
菜单 content hash 纳入安全摘要；读取不写 Profile、菜单、订单或打印 job。

拒绝语义：未登录 401；foreign Store、ADMIN、Manager/Staff、scope 降权 403；schema/type/refs 无效 400；
stale revision 或持久化配置损坏需修复 409。当前不支持 PATCH、任意 schema 扩展或配置发布工作流。

## 迁移、缓存和回滚

V39 新表有 Store FK、schema/revision、Profile/mode、CAD/tax 约束；不复制 Organization ID。
V1–V38 不改。V39 已由整合线执行，后续必须保持字节不可变；必要修复只追加 forward migration。
V40 添加空 public preset，并仅为精确 Neutral request 放开 ledger 中三项 Master Menu 为空；
旧 request 仍要求三项齐全。没有删除旧 Profile、artifact、master 或业务历史。

Config revision 与 menu revision 是缓存失效依据，不创建第二套缓存协议。现有离线 DB5/key/账号边界不改；
旧客户端可忽略新增 `safe_summary`，新客户端必须允许 legacy API 缺该字段。离线历史 order/print frozen
payload/hash 不根据新配置回填。配置更新不是旧订单重新定价或重新渲染许可。

回滚使用兼容 V39/V40 和明确 Neutral 语义的 forward fix；不删表/清 queue/降级历史 schema。
旧后端若不能理解 Neutral，必须停止新建/配置该类店，不能通过 missing→Lanzhou fallback 绕过。
Deployment、真实迁移与硬件验证仍需各自授权与 exact-artifact 证据。

## 本地证明

`MerchantConfigurationServiceTest`：严格 schema/refs、missing Neutral、精确兼容标识、revision/audit/menu invalidation。
`MerchantConfigurationHttpTest`：真实 Bearer/StoreAccess，foreign Owner、ADMIN、Manager/Staff、membership
提权、unknown refs/schema、refresh 后 scope 变化拒绝。
`MerchantConfigurationPostgresTest`：fresh/V38 upgrade、显式 backfill、旧业务和 Profile 内容 hash 不变。
`NeutralBusinessCreationPostgresTest`：真实 PostgreSQL/JPA + Owner HTTP 创建/重放/并发 CAS/实际 stale entity
refresh/失败回滚。最终整合数量与独立 Agent 6 结论由 Root 记录；本文件不是阶段 PASS 声明。
