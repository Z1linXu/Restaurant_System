# Phase 3 Profile / Neutral Design

本文件描述实际后端边界。兰州运行时兼容仍为 Phase 4/5 前的兼容层，不能声称通用 Modifier/Combo、
厨房路由或打印语义已完成。当前商家生产行为与本开发线隔离，不自动部署。

## 两种明确身份

| 业务名称 | 实际持久化标识 | 来源与模式 |
| --- | --- | --- |
| Lanzhou compatibility | `ST_DENIS_CANONICAL_PROFILE@v2` | 已授权原 provisioner，或 V39 明确旧安装 backfill；`PROVISIONAL_COMPATIBILITY` |
| Neutral restaurant | `NEUTRAL_RESTAURANT@v1` | V40 受审空 public preset；`NEUTRAL` |

`LANZHOU_NOODLES` 只是业务称呼；未 rename 旧 canonical code/version、artifact、fingerprint。
`stores.provisioned_profile_*` 记录创建 provenance；新表 `profile_code/profile_version` 是明确 runtime
compatibility assignment。普通 merchant PUT 不切换 Profile。缺 runtime 配置不会追溯推断 provenance。

## 显式旧店回填

V39 在执行时对**当时已存在的 Store rows**创建配置行，来源按顺序：

1. 精确 `ST_DENIS_CANONICAL_PROFILE@v2` 且存储 Store fingerprint 匹配版本表：
   `VERIFIED_PROFILE_BACKFILL`，保留 compatibility。
2. 旧生命周期迁移留下的明确 `stores.provisioning_source='LEGACY_EXISTING_STORE'`：
   `LEGACY_INSTALLATION_BACKFILL`，保留该旧安装原行为，不把它说成已核 Profile artifact provenance。
3. 其余：`NEUTRAL_EXISTING_BACKFILL`，不猜兰州。

这是一次迁移快照；新 Store 后来即使填写同样 marker 也不在运行时自动认领兼容。
代码只读取实际配置行。Owner/Org 归属和 private Profile 使用权仍独立验证：
legacy compatibility marker 不授予私有 Profile artifact 的可见性或新店复制授权。

V38 private 规则不变：active global OWNER + exact active owning Organization OWNER；
unknown/ambiguous owner 拒绝，ADMIN 无 bypass。Neutral root 是唯一新建 ownerless `PUBLIC_PRESET`，
内容没有 private source ID、硬件 endpoint、人员或菜单数据。未将历史 V15/V19 seeds 改成公开模板。

## Neutral 初始化

使用已有业务入口：

`POST /api/v1/owner/organizations/{organizationId}/stores`，仍要求 `Idempotency-Key`。

```json
{"store_name":"Synthetic Restaurant","store_code":"SYNTHETIC_RESTAURANT"}
```

只对 BUSINESS 创建，blank/omitted profile 默认 Neutral；显式 `NEUTRAL_RESTAURANT` 也可用。
version/fingerprint 必须匹配受审 preset；任何 Master Menu reference 非空都拒绝。
`GET .../stores/create-catalog` 返回 Neutral，不附兰州 master reference。

旧 `phase-b/store-provisioning/catalog` 保留原 feature/runtime gate 和私有兰州 catalog，
显式私有 `ST_DENIS_CANONICAL_PROFILE@v2` 的业务创建继续原 Profile + Master Menu 校验/materializer。
两个 catalog 由同一现有 service 的明确模式区分，未创建第二套 provisioning framework。

Neutral 复用原 command fingerprint、request coordinator、organization lock、Store code uniqueness、
事务 materializer、成功/失败 ledger。事务内再次核 active exact Organization Owner。
实际创建只有：

- 一个 BUSINESS / active / ACTIVE Store，打印 runtime 为 DISABLED；
- 一行 merchant config：schema 1 / revision 1 / Neutral；
- 一份 Store-local module rows，沿用现有 core module contract，optional KDS/Uber/advanced analytics off；
- 当前 Owner 的一份 active scoped Store membership。

创建 counts 的 station/category/item/option/pricing/combo/printing-rule 全为 0。
没有复制 Master Menu、菜单/BOM/Add-on、station、table、logical printer role、printer/device/endpoint 或其他员工。
Core PRINTING/TABLE 管理模块被既有 contract 视为 core，不等同于已创建硬件/桌台或启用物理打印。

空白工作区无需满足原兰州 readiness 中“已有菜单/工位/桌台/GRAB 打印角色”的复制前提。
Neutral 分支检查 preset/Owner/Org/code/config，并在同一事务完成 empty workspace；
不调用 legacy menu readiness 或 activation preset，不给 Owner 添加 fingerprint/readiness 产品步骤。
后续资源仍由现有 Store APIs 正常配置；这不是完整商家导入/发布工具。

相同 key/相同规范内容返回同 Store 和 replayed=true；冲突/处理中/失败重试继续原 ledger 语义。
后段创建失败回滚 Store、config、modules、membership 等业务 graph，保留已独立预留的 FAILED request。
显式兰州配置也在原 materializer 的同一事务初始化 runtime config，不留下先 LIVE 后补配置的窗口。

## 模板安全与兼容

`NeutralRestaurantProfile` 使用固定 canonical empty content/fingerprint；现有 Profile validator 对精确
Neutral 验证该受审内容、schema/version、无 artifact。其余 Profile 验证不变。
V40 不需要空 Master Menu graph；ledger 允许 null Master reference 仅限精确 Neutral v1，防止假造共享 master。

Neutral 读配置不会加载兰州 artifact。打印/Order/StoreCombo 的运行时兼容选择由 Root 对统一 reader 接线，
具体覆盖与 P4/P5 延期见 `PHASE3_PRINTING_COMPATIBILITY.md`。现有历史订单、option/task、print payload/hash
不因 metadata 更新重写。Profile 资源私有化和 DB5 legacy isolation 均继续生效。

## 验证与限制

已执行本地 fresh/V38 upgrade，验证旧 Profile JSON/fingerprint、菜单/订单/task/print 表 hash 不变；
新增 public root 仅 Neutral。真实 PG/JPA + Owner HTTP 验证空店创建/重放、foreign Org 与私有 Profile 拒绝、
并发 revision CAS、跨连接 Store move 后 stale-entity refresh 拒绝、后段 membership 异常完整回滚。
原合法兰州 service/transaction regression 保留。最终整合数量与 Agent 6 由 Root 汇总。

未授权运行真实商家 migration/provision、生产/预发布部署、物理设备或打印。本轮不含 P4 价格/Modifier/Combo
引擎、P5 站点路由重构、P6 配置导入 executor、商家 PDF 操作手册或真实税务/语言认证。
Rollback 要保留 V39/V40 与 Neutral 语义；forward repair，不删既有数据或把 missing config 改回兰州。
