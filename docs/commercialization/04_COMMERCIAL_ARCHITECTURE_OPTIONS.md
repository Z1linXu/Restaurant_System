# 04 — Commercial Architecture Options

审计日期：2026-10-15（America/Toronto）。范围：READ-ONLY AUDIT + DOCUMENTATION + QUESTIONS。

源码基准：后端 **B = `ce35278b3569a4eacfa29efc01cadbf0c409bec6`**；线上前端 **F = `8c3a9e356f59cd025beff25f7fb1d5a20594e028`**；远端 main **M = `06bac51ddc0a829a9aa1106f07ef886572efbb65`**（本轮 `git ls-remote` 确认，未 fetch 或更新引用）。本轮证明 B/F 的 `frontend/`、`restaurant-pad-app/` 无 Git 内容差异，因此 B 中这两部分也可用于语义审查。源码链接固定到提交，不能用当前旧工作区的同名文件替代。

证据标签：`VERIFIED_IN_CODE` 表示读到固定提交中的实现；`VERIFIED_IN_PRODUCTION` 仅表示本轮明确读取的运行事实；`DOCUMENTED_ONLY` 表示仅有文档陈述；`PLANNED_ONLY` 表示计划/契约尚未证明完成；`UNKNOWN` 表示证据不足。代码被部署不等于所有行为均经过线上验证。风险优先级 P0=独立商家共享部署阻断，P1=上线前关键，P2=按首批范围处理，P3=维护改善；不代表要求立即修改现有 Production。


## 状态与建议方向

这是选项分析，**不是获批的目标架构、实施 Planbook 或下阶段授权**。现有 Phase B 代码已经实现相当部分产品化基础，M 的 CURRENT_STATE 明确 next_phase_authorized=false。当前用户请求允许重新审视商业方向，但只允许只读审计/文档；旧计划中出现的自动 Agile Loop 不在本轮执行。

建议候选方向：复用现有可靠订单、Store-owned 菜单、Profile/Master、printing outbox/Pad；将 Lan Zhou 的行为固定成显式版本化 Restaurant Profile；通用 Core 使用中性默认。首批独立商家是否采用每 Organization 独立部署、共享服务或混合模式由 Q2 决定。**不能仅因两环境运行相同镜像，就认定共享多租户已安全。**

## 多商家模型的可行性

| 已有能力 | 可复用程度 | 尚缺证明/改造条件 |
| --- | --- | --- |
| Organization → Stores + Owner/Store memberships | 可表达连锁组织和多个门店 | 无关联商家的法律/经营主体边界由 Q3 定；global ADMIN bypass、legacy fallback、每店role生效语义需明确 |
| Store-owned menu/pricing/combo/Addon/printing/device | 很适合作商家内多店配置基础 | BOM/旧实体 scope FK 不均匀；任意 ID 访问/后台任务/export/WS 都需独立检查 |
| Chain Master Menu | 带 organization_id，可作为组织私有母版 | 要证明所有版本/查询/materialization 都保持该 scope；禁止从真实 Store1默认跨组织克隆 |
| Store Profile global catalog | 可作为平台公开、无敏感数据的 preset 库 | controller 只 requireOwner、版本返回 artifacts；不能未经扩展用来存客户私有模板（HC-053） |
| StoreAccess REST guards | 多数主流程有显式检查 | WS CONNECT/SUBSCRIBE、global后台、备份恢复、异步 worker 与导出不能只依赖 URL Store ID |
| PostgreSQL shared schema | 现有运行方式成熟 | 本轮 catalog 79 public tables、0 RLS、0 policies；shared 场景不能声称数据库提供 tenant 边界 |

证据：[B:backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java:40](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java#L40)；[B:backend/src/main/java/com/restaurant/system/owner/master/ChainMasterMenuEntity.java:20](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/master/ChainMasterMenuEntity.java#L20)；[B:backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileController.java:30](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileController.java#L30)；[B:backend/src/main/java/com/restaurant/system/common/config/WebSocketConfig.java:23](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/WebSocketConfig.java#L23)。

Platform Operator 与 Restaurant Owner 建议明确分离：平台账户管理 deployment/支持授权/public presets；组织 Owner 只能管理自身组织；Store 管理员只按该店能力。支持跨商家读取应具有明确目的、有效期和审计，不以普通 Owner 或商家 staff 默认获得。是否提供按请求临时支持访问由 Q4 决定。本轮不修改任何角色、账号或 membership。

## 部署模式比较

| 选项 | 现架构适配 | 优点 | 成本/风险 | 适用条件 |
| --- | --- | --- | --- | --- |
| A. 每独立 Organization 单独 app+DB（同组织多 Store） | 复用当前 Compose、云后端与 Android，改动相对小 | 数据恢复、升级窗口、故障与商家边界容易控制 | 每客户成本/运维数量上升，仍需应用隔离与部署自动化；不是所有安全问题自动消失 | 首批 assisted onboarding，客户数量有限；建议作为候选起点 |
| B. 全商家共享 app+schema | 可复用 Store/Organization，但隔离审计尚未闭合 | 统一更新、资源复用、平台控制台方便 | ADMIN/WS/旧查询/任务/备份/噪声邻居影响全体，schema migration同批；投入较高 | 完成 tenant threat model、全路径负向测试、强约束、支持权限后才可评估承诺 |
| C. 共享 control plane + 每组织独立 DB/运行单元 | 业务服务可复用，控制面尚未证明现成 | 逐客户维护与隔离兼顾，可服务高要求客户 | 连接路由、secret、migration orchestrator、backup、版本矩阵复杂，首版成本较高 | 客户量/隔离要求证明有价值后分期建设 |
| D. 现场/LAN 或客户自托管 | Windows pilot/Android LAN 经验可参考 | 现场局域网依赖更小，客户可掌控运行环境 | 远程支持、升级、备份、安全补丁与第三方 webhook ingress更难；当前 outbox不是完整离线服务器 | 市场明确要求且有人维护现场主机，不自动作为默认 |

多租户不只是一列 tenant_id。若选 shared，需要同时覆盖 REST/WS、scheduled jobs、连接池与事务、缓存/outbox、global profile visibility、打印设备、第三方 webhook、日志、导出和恢复。是否 RLS、schema-per-org 或 DB-per-org 是后续 threat model 决策，不能在本轮拍板。simple STOMP broker 与进程内 dispatch chains 也需在多实例部署前验证。

## 配置层级与唯一来源（候选责任划分）

| 层级 | 应负责的事实 | 已有基础 / 避免重复 |
| --- | --- | --- |
| Platform | deployment identity、受控公开 Profile/Module catalog、协议版本、全局安全禁用、secret引用、产品分发策略 | FeatureConfigProperties、module catalog、版本化 artifacts。不是全店 Add-on 名称/价格数据库 |
| Organization | membership/Owner、组织私有 Master Menu/品牌默认、canonical Add-on definition code、可用 preset | ChainMasterMenu.organization_id / organization_addon_definitions。客户私有 profile 与全局公共 preset 要区分 |
| Store | 实际可售菜单/options/eligible Add-ons和店价、size/combo政策、税/币种/时区/营业日、桌台/工位、打印配置/assignment/display rule、Store modules | Menu 是 WHAT；assignment 是 WHERE；display rule 是 HOW。税/币种/时区完整 per-Store 模型尚未实现 |
| Device | 已验证 Store/device identity、token、API deployment绑定、设备能力、worker/本机偏好 | 真实任务目标 printer 以 Store assignment/payload 为准；Pad 的 test IP 不应变成另一套路由权威 |
| User | 语言/无障碍/显示偏好、当前授权 workspace | 有效权限由服务端 membership/capability 得出，localStorage/URL不授权 |
| Item / Option | SKU/semantic identity、显式默认/选择规则/variant、linked menu item/工位/BOM/eligibility | 不用中文名判定厨房动作；Add-on price/name由 canonical Store catalog提供，历史快照独立 |

优先级建议必须**逐字段定义**：平台安全禁用最高；组织默认仅用于创建/允许继承的字段；Store own value 决定运行；item override 仅在明确允许的价格/默认规则内；device/user 只影响本机/个人偏好，不改成交价/权限/路由。API deployment identity 必须进入离线命名空间。内部 effective-config diagnostics 可服务支持人员，避免向前台暴露 fingerprint/provisioning工程状态。

## 新商家初始化：当前能力与缺口

| 业务步骤 | 当前证据 | 当前仍需开发/运营处理的部分 |
| --- | --- | --- |
| 建独立组织/首个 Owner | PlatformAdminController 有组织 create/update；OwnerOrganizationAuthorizationService 校验组织 Owner | 未证实完整的一键外部商家注册、邀请/验证/credential lifecycle。不能把手写 DB/bootstrap secret 当可售 wizard |
| 建第一 Store | Owner onboarding / business provisioning 已有，保留 scope和幂等 | 默认 ST_DENIS profile；blank/cafe/快餐等通用 preset不存在已验收证明 |
| 菜单/Add-on/价格/套餐 | Menu管理、canonical Add-ons、Master materialization | 新组织 master/profile来源、导入验证、无专属 seed 安装链和更通用 modifier能力待 Q5–Q8/Q18 |
| 桌台/工位/员工 | 新店默认T01/T02/Owner membership，已有管理模块/Part2能力 | 首批 staff invitation/角色模板、counter-only、任意 split 等是否承诺待定 |
| 打印/设备 | 新店无硬件时 DISABLED；逻辑角色/assignment、device配对和native Worker已有 | 现场 LAN/printer型号/encoding、Pad正确目标和纸张验收仍需人处理；不复制现店 endpoint/token |
| 上线/备份/支持 | exact-SHA tools、dump/restore、health和历史evidence可复用 | 商家级安装包、恢复/导出、监控责任、维护窗口、签名APK更新策略没有完整交付证明 |

证据：[B:backend/src/main/java/com/restaurant/system/platform/controller/PlatformAdminController.java:75](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/platform/controller/PlatformAdminController.java#L75)；[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java:33](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java#L33)；[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java:53](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java#L53)。

Onboarding Wizard 是产品呈现选项，不是必须新建的技术平台。建议候选为 assisted 一次业务创建 + 菜单导入/选择授权 preset + 必需的现场打印设置；内部 reservation/validation/defaults继续自动完成。只有确认自助获客需求后才估算注册、邀请、引导、计费与支持系统。创建新组织时应验证订单/客户/员工/credentials/配对/真实printer endpoints为空，只保留授权配置，不从生产 dump克隆。

## Lan Zhou 兼容与 Core / Module / Profile 边界

| 候选层 | 保留内容 | 不应混入 |
| --- | --- | --- |
| Core | 订单快照/金额原语、scope与授权接口、事务/幂等、任务/dispatch契约、审计/版本 | `traditional_beef_noodle`、三细省略、Store1 ID、固定税/报表SKU |
| Optional modules | KDS各屏、库存完整管理、打印transport、Uber connector、进阶analytics | 打开模块不自动注入一家店的菜单/权限/硬件 |
| Lan Zhou Profile（版本化） | 现菜单语义、大小/Combo defaults、egg/side兼容、厨房工位/GRAB缩写、税/语言/桌台/报告presets | 历史订单、真实用户/密码/token、真实设备和打印IP、真实第三方商户binding |
| Environment/Device state | 域名、TLS、网络、secret引用、现场endpoint/device enrollment | 不进可共享 Restaurant Profile，不作为产品源码身份 |

可行性：已有 Profile/Master 合约允许独立 materialization，适合复用；但当前 global default dictionaries、Java fallback、SQL data seed、前台compat字段尚未全部变成显式 profile。迁移必须先建立现有行为对照，再由调用链逐条改造；不能仅移动一份 JSON或删除switch后声称通用化。

## Android、硬件、数据和维护选项

- APK：统一 signed app + 可配置商家绑定（候选推荐）成本较低；白标 flavor提高 branding能力但增加签名、发布渠道与兼容矩阵；浏览器方案可覆盖点餐，但不自动拥有native TCP打印。当前源码version不证明所有已装设备。
- 打印：以认证 ESC/POS TCP型号先行可以复用最多；iPad 需要明确是否仅browser点餐、Android协作打印、或未来local gateway。USB/Bluetooth/任意型号均未在本轮验证。现场设置必须连接指定客户自己的硬件。
- 备份：单组织独立DB便于整体恢复，但仍不能用旧dump覆盖营业新订单；sharedDB需tenant-aware export/recovery。自动频率/加密/offsite/retention/删除政策和RPO/RTO另列UNKNOWN，不能用存在dump代替验收。
- 维护：同 core + profile + 独立受控 release可减少双向补丁；另商业repo可隔离交付资料但增加backport成本；长期fork需要明确补丁责任。所有选择都保留 Lan Zhou独立验收与回滚窗口，不让商业试验自动更新Production。

关键决策依赖：Q1市场 → Q2/Q3隔离与运维 → Q4权限、Q5–Q9菜单/价格/离线 → Q10–Q16厨房/打印/本地化/设备 → Q17平台 → Q18/Q19初始化与上市承诺 → Q20维护。仅此依赖分析；完整分阶段 PR/预算/回滚 Planbook 等20问回答后再制定。
