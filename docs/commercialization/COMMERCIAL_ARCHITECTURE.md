# Commercial Architecture — Core / Modules / Profiles

本文件定义目标边界；未在日期化实现表中确认的接口/字段仍为 `PLANNED_ONLY`，须由实现、测试与 API 更新证明。执行顺序见 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，决策见 [Owner Decisions](OWNER_DECISIONS.md)，动态状态只读 [CURRENT_STATE](../governance/CURRENT_STATE.yml)。

## 证据基线与架构选择

已读 [01 生产审计](01_PRODUCTION_BASELINE_AUDIT.md)、[02 架构](02_SYSTEM_ARCHITECTURE_AUDIT.md)、[03 HC](03_HARDCODE_AND_CUSTOMIZATION_INVENTORY.md)、[04 选项](04_COMMERCIAL_ARCHITECTURE_OPTIONS.md)、[05 文档差异](05_DOCUMENTATION_GAP_REPORT.md)、[06 二十问](06_OWNER_DECISION_QUESTIONS.md)；本地核对 StoreAccessService、WebSocketConfig、StoreProfileCatalogService、OwnerStoreProvisioningServiceImpl、StoreAddonOptionResolver、OrderServiceImpl、offlineDatabase、MainActivity、Gradle 与现有 Profile/Add-on contracts。实际运行模块以 JPA/JDBC 为主，不用 MyBatis interceptor 假设租户隔离。前端 React/TypeScript/Tailwind，保持现命名和最少用户步骤，不为旧 MVP 文档引入 Ant Design 或六模块重写。

采用每独立商家 app+DB、同 Org 多 Store、同一代码体系分别发布。它保留现 Compose/订单/打印路径，也缩小恢复与升级范围；初期不建共享控制面、计费或动态数据源路由。应用始终执行 Org/Store 权限，测试库故意包含两个独立 Org，防止部署隔离掩盖服务缺陷。

审计确认的组合为 backend `ce35278b3569a4eacfa29efc01cadbf0c409bec6`、frontend `8c3a9e356f59cd025beff25f7fb1d5a20594e028`、Flyway V37；fresh origin/main `06bac51ddc0a829a9aa1106f07ef886572efbb65`。Git 证实 B/F 的 frontend/Android trees 相同且 main 应用树未改，不等于两个历史镜像或全部已装 APK 可逐字节重建。精确对象、checksum、image ID 和未核验部分保存在 Phase 2 Manifest，不拼贴源码制造“统一生产 commit”。

## Phase 3 有界实现观察 — 2026-10-15

源码观察点为backend `711638e`（config/Neutral `9fdf5e7`，打印 `57fe6ad`/`de1032c`）；后端实现与独立审查已完成，不是整体G3结论。[P3验收报告](PHASE3_ACCEPTANCE_REPORT.md)记录待验项，CURRENT_STATE仍独占动态阶段。配置/Profile技术合同由对应实现负责人维护，本表只说明实际边界，不建立第二份schema。

| 责任 | 当前代码边界 / 后续证据 |
| --- | --- |
| Typed Store配置 | [MerchantConfigurationService](../../backend/src/main/java/com/restaurant/system/merchant/MerchantConfigurationService.java)与[DTO](../../backend/src/main/java/com/restaurant/system/merchant/dto/MerchantConfigurationSummary.java)提供九字段safe summary：schema_version、config_revision、brand_name、locale、currency、timezone、tax_policy_reference、profile_code、compatibility_mode；不返回私有artifact/Secret。StoreContext/menu catalog已接入同一summary，真实loopback HTTP摘要一致；UI55198bc最终61files363/build通过，刷新delta已由另一独立Reviewer通过 |
| 受限写入 | [Controller](../../backend/src/main/java/com/restaurant/system/merchant/MerchantConfigurationController.java)仅GET/PUT现Store配置；PUT严格只接受schema_version、expected_config_revision、brand_name、locale，Owner capability/实际Store、锁后再次授权、revision冲突与audit/menu revision均在服务内处理。CAD/tax reference保持现兼容值、timezone读取Store；不因此宣称税引擎或任意override已实现 |
| 唯一经营来源 | response只指向已有MODULES/MENU/STATIONS/TABLES/PRINTING/MENU_OPTIONS/COMBOS API，目标API继续独立授权；不把指针变成写入跨资源ID的后门。Canonical Add-on继续Org code→Store price/name→Item eligibility；Store-owned Combo现存rows读取不注入五项默认，完整组算法/旧synthetic ID仍P4 |
| 兼容判定 | [V39](../../backend/src/main/resources/db/migration/V39__merchant_configuration.sql)只回填迁移当时现存Store：可核Profile引用或明确LEGACY_EXISTING_STORE标记为PROVISIONAL_COMPATIBILITY，其余Neutral。运行时仅有效persisted配置可启用ST_DENIS_CANONICAL_PROFILE@v2；missing/invalid配置走Neutral，不靠名字/SKU/Org1重新推断 |
| 中性打印 | [打印实现记录](PHASE3_PRINTING_COMPATIBILITY.md)界定空字典、完整冻结商品/option标签、无兰州name/SKU/egg推断、零价children保留与缺settings不进入硬件readiness循环。原claim/lease/dispatch、金额及历史payload路径保留；现station/output-role约束仍PROVISIONAL_COMPATIBILITY，任意路由/renderer提取P5 |
| Neutral初始化 | 显式NEUTRAL_RESTAURANT@v1原子创建新空Store/config/现Owner授权membership；不创建菜单/桌台/新staff/设备/assignments，不调用Store1clone。V40与现Owner provisioning原子扩展已实现，private catalog/HTTP/PG fresh+upgrade、replay/CAS/回滚/history已本地证明；不恢复无授权generic Store POST |
| UI与缓存 | 同一build按safe summary显示Store品牌/locale，沿现menu revision/cache和module graph消费；关闭模块不发请求/订阅、管理入口不依赖硬件readiness。UI定时/focus context刷新与same-scope保留、lateforeign缓存拒绝及默认DOM取消均经独立review；768/1024实际截图/布局因用户权限策略拒绝而NOT RUN；不承诺完整i18n或所有角色KDS运行 |

九字段安全摘要是最小读取契约；它不把菜单/金额/打印renderer或可售规则重新建模。字段完整合同、Profile设计与最终exactSHA测试由对应文档/报告路由。backend最终Java17/all六类PG `1050/0failure/0error/0skip`、独立审查与非空history/升级已通过，实际loopback API14/14；UI55198bc最终61files363/build通过，另一Reviewer14files101/build/diff PASS，Provider stale/defaultdialog P1 CLOSED；P3本地54assets/DB5/debugAPK与native24通过，未安装/实体打印。原G3-04的768/1024布局/视觉证据NOT RUN，G3-08 BLOCKED，整体只能PARTIAL/BLOCKED，不因基础代码与自动测试完成改写原gate。Profile fingerprint使用既有code/version/schema/content/artifacts的aggregate定义，不等raw content摘要；原误判与撤回完整保留在独立审查报告。

## 模块边界

| 层 | 责任与已有扩展点 | 不变量 |
| --- | --- | --- |
| Core | Auth/RBAC、Org/Store、StoreAccessService；菜单身份/价格原语/订单生命周期；offline draft、outbox、idempotency、menu revision；快照、设备身份与打印抽象、配置读取 | 后端权威授权、价格政策与事务；稳定 retry key；本地 queued 不冒充服务器订单；提交后 add-only；print failure 不回滚订单 |
| Modules | 复用 FeaturePackage、Store modules/依赖图；KDS、printing 功能层级、analytics、Uber；基础库存只限验证路径 | 平台安全禁用优先；backend API、worker/dispatch、STOMP 与 frontend 一致；管理入口与 runtime readiness 分离 |
| Profiles | 既有 StoreProfile/version/artifact/fingerprint + Org-owned ChainMaster；兰州私有兼容、Neutral 空白模板 | 无任意代码、权限覆盖、Secrets、真实设备/订单；materialization 生成独立 Store mutable graph |
| Environment | 独立 DB、secret references、域名/TLS、API deployment identity、版本与备份配置 | 不进入可共享 Profile；商家间环境和 credentials 不复用 |
| Device/User | 同店安全配对、可信 bridge origin、设备能力；语言/显示偏好 | 不决定成交价、授权或实际打印路由；token 不暴露 JS/manifest |

Inventory 完整补货/备菜、支付、高级报表不自动进入商业首发。KDS 关闭时不能启动其订阅/查询；Uber global off 是平台 ceiling，Store enabled 不绕过 ceiling、外部许可或独立生产批准。保留 `GRAB`/`FRONTDESK`/`HOT_KITCHEN` 的稳定协议 code，不把它们重命名为外卖平台。

## 配置层级与唯一来源

不是通用的后层覆盖前层。仅允许下表逐字段继承；安全 ceiling 不能被低层覆盖。Phase 3 实现 typed Merchant configuration、受 Store scope 约束的最小读写/校验入口、revision/cache metadata 与 schema contract，复用现有表/API，不建立第二份经营配置数据库。

| 配置域 | Source of Truth / 允许覆盖 | 缺失、冲突与版本 |
| --- | --- | --- |
| 安全/部署 | Platform 环境配置拥有 deployment identity、协议版本、安全禁用、secret refs | 缺 identity/secret/错误环境拒绝启动或拒绝受影响能力；不能回退到生产域名/Store1 |
| Org | Organization/membership；Org-owned Master/Profile；canonical Add-on immutable code；品牌默认 | 私有资源只限所属 Org；默认在创建时 materialize，不能共享可变对象 |
| Store | Store-owned 菜单/价格/tax/currency/timezone/营业日、modules、工位/桌台、品牌、printing assignment/display revision | Store 实际值决定经营；未配置税等商业必需值不能静默套兰州常量。可选打印缺失只禁打印，不禁 Store LIVE |
| Menu Item | 显式 variant price override、options/min/max/default、eligibility、linked item、station/BOM refs | 只覆盖明确允许的 Store default；同店引用；名称/显示文本不能授权逻辑；min≤max、default 满足范围 |
| Device | 本机安全环境绑定/能力/显示偏好，打印目标来自服务端 assignment/payload | 切服先停止旧 Worker/连接，旧 token 不发新 origin；队列保留在原 namespace |
| User | 语言、辅助显示、已授权 workspace 选择 | 不能覆盖 Org/Store/Device 安全、税、价格、路由；401/403 不走离线降级 |

发布的 Profile/artifact immutable；Store runtime 配置在事务中更新既有 menu/config revision。Reader 返回 effective values、schema/revision、来源用于内部诊断；POS 只接收业务需要的展示/能力，不暴露工程 stage。缓存键至少含 deployment、account、Org、Store、schema/menu/config revision，用户退出取消可见会话但保留不泄露的原 scope pending records；重登录同 scope 可恢复。旧 DB4 升级 DB5 时未证明来源的 records 原样隔离，不能按当前 API origin 自动认领、删除或回放。原地降级旧 APK 无法读 DB5，回滚只能用兼容 DB5 的修复构建或受控导出恢复，不能宣称旧 APK 可直接降级。

## 通用菜单语义与分期

Phase 3 只固定实体引用/版本/读取入口和兼容适配。Phase 4 扩展现有模型，先审当前 controller/service/DTO/tests，不假定下列新字段/API 已存在。

| 语义 | 目标 | 所有消费者 |
| --- | --- | --- |
| 分类/商品/规格 | Store-owned stable codes/IDs；双语名称；variant 与 item override；currency/tax policy refs | Owner 与 Frontdesk 读取同一规范化投影；Order 最终校验 |
| Modifier group | required、single/multiple、min/max、数量、default；经定义的免费额度与超额计价 | UI 选择与 backend validation/price 相同；Add-on/Remove 语义分离 |
| Canonical Add-on | Org code → Store 名称/价格/active → Item eligibility | 复用 [Add-on contract](../governance/contracts/MENU_ADDON_CATALOG_CONTRACT.md)；printing 只读身份、编辑 alias |
| Combo | group/component stable identity，显式 linked real item、min/max/default；同店引用 | 前台 payload、Order、BOM、Kitchen、Receipt 同语义；旧 synthetic IDs 只经兼容适配解释 |
| Kitchen/Printing | station 与 output-role 引用；task/order/job 的冻结名称、语义及 route/display revision | 不用中文或任意 SKU 推断通用厨房动作；显示规则不能定义可售菜单 |
| Historical snapshots | 下单时 identity/name/quantity/price/tax/currency/selected options；任务与 rendered print payload | 新配置不追溯改写历史；重印读取原 job，旧数据 fallback 明确版本 |

门店默认规格政策及单菜覆盖、旧价有效期/版本校验在 P4 一起实现，避免两个 price engines。旧价期限仍 UNKNOWN；不能仅相信客户端 timestamp/price。服务端需验证来源与有效 revision，过期/不可售冲突显式确认且不改已提交重试的 key/hash；非 Add-on 与 canonical Add-on 的兼容差异需测试后统一。

## Profile 与新安装边界

实际既有 canonical code 是 `ST_DENIS_CANONICAL_PROFILE`、版本 `v2`。`LANZHOU_NOODLE` 是测试/业务别名，不无故重命名持久 code。P2-PROFILE-04 给 Profile 增加 PUBLIC/PRIVATE 和 Org owner，版本/artifact 继承根权限；优先从 stores.provisioned_profile_code/version/fingerprint 的一致引用推导唯一 Org；V19 给多个 Org seed 的同 master_key 不能证明 owner，只有唯一且可证明的 Master 关系才作后备，不写死 Org1。多归属或无可证明归属一律不可授权，不把含兰州内容的种子自动公开。V38已在P2以additive迁移实现并验证；P3使用V39配置metadata与待整合V40 Neutral扩展，迁移由集成负责人串行协调；不得改V1–V38。

P3-PROFILE-02 建立 `NEUTRAL_RESTAURANT` 或同语义的明确版本化配置：可复用现通用 Store API 加最小配置初始化服务，针对新空白 Store 验证，不造另一套完整 provisioning；Owner 同品牌 Master materialization 保留为明确 PRIVATE legacy compatibility。Neutral 配置：无现店菜单/SKU/面型默认、无两桌隐式需求、无真实用户、无 LAN/配对、无兰州打印字典。中性打印可用完整快照名，硬件缺失 printing DISABLED。现店 Profile 保留面型、蛋/小菜、厨房缩写、GRAB/HOT 与原默认行为；尚未隔离的 renderer/算法标 `PROVISIONAL_COMPATIBILITY` 并交 P4/P5，不谎称已通用。

空库重放 V1–V37 会含 V5/V15/V19 餐厅数据。这不能靠编辑旧 SQL 消除：需新 additive 安装/访问边界，使旧种子保留为无授权 private legacy，Neutral 创建不选择它、不物化它、不向商家列出内容；fresh-install 测试证明无兰州经营 rows 或可见 artifact。若仍需把静态 legacy seed 从分发包移除，另走 P6/P7 安装包方案，不删除已应用历史。

## 安全与故障边界

P0：ADMIN 默认全店通行、WS CONNECT/SUBSCRIBE 未授权属于进入商家隔离前的 blockers。P1 但同样纳入 P2 的跨边界安全包括 Profile、Device/Print Job、offline origin、BOM scope/并发、错误 seed/profile 环境。

授权链必须以 principal → active Org/Store membership/capability → path 与实际 resource owner 一致 → module/capability 检查为准。WS CONNECT 验证身份/允许 origin，SUBSCRIBE 对已知 destination + Store 权限检查，拒绝通配/未知/SEND 冒用；过期、撤权、重连要重验且断开旧会话。支持 grant 未实现前拒绝支持业务读取，不让 ADMIN 兜底。特别复核 PlatformAdmin path/body 中资源实际归属、Staff all-store 与跨店搬移、AuditLog 无 Store 参数的全库读取、Analytics global rebuild；平台 metadata 权限不能被借用为经营数据权限。路径/日志/错误不返回 foreign 内容。

库存当前启用 deduction 路径必须锁相同库存的并发扣减、验证所有 item/option BOM 的同店引用并保留 ledger/事务；只关闭 UI 无法关闭 submit 中写入。完整 restock/prep 未实现不得造 API。迁移只在合成本地库验证 upgrade/rollback；失败回滚业务事务，不能借全库 restore 覆盖新订单。打印保留 `PAD_DIRECT` claim/lease/sourceKey、防重与不确定结果人工确认。

发布、onboarding 与恢复设计见 [Merchant Onboarding](MERCHANT_ONBOARDING_DESIGN.md)。本轮任何 Production/Staging 部署、重启、Flyway、配对、出纸或数据变更均禁止。当前兰州独立发布/回滚，商业代码只留在隔离开发线。
