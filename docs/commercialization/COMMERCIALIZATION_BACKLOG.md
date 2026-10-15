# Commercialization Backlog — 可执行 PR 与审计追踪

顺序/阶段入口只由 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md) 路由；动态 Phase/Package/Gate 在 [CURRENT_STATE](../governance/CURRENT_STATE.yml)。任务卡保留 Phase 1 的计划与验收义务；已执行 P2 的结果见下方日期化快照与两份报告。未执行任务仍为 `PLANNED`；本地提交/独立审查不冒充 main merge 或部署。每任务继续记录 commit、测试、review 与证据，不复制第二份当前阶段状态。

## 所有任务共同 DoD

每 PR 都须满足：有可审查 diff/commit；实现不超过目标；对应 focused、跨边界负向与受影响回归 PASS；API/契约/SYSTEM_DOCUMENTATION 与 HC/DG 记录同步；无泄露 Secrets/真实测试数据；新 migration序号经协调且旧 checksum不变；历史 snapshot/job/outbox 不被重写；rollback可执行；AGENTS规定的 review完成。未跑项写 NOT RUN，物理未知写 HARDWARE_QA_PENDING，阻断项写 BLOCKED，不以本地 commit冒充 merged。以下每任务 DoD 是在共同 DoD 上的额外条件。所有开发仅 LOCAL_SYNTHETIC，任何 Production/Staging mutation禁止。

风险 P0/P1 是问题严重度；Phase 2 必修的跨边界 P1 不因数据库独立而延期。以后新增发现应给独立受限 task，不静默扩写本 PR。

## Phase 2 执行快照 — 2026-10-15

最终代码观察点 `5ec8abeb7a67760000b29151e7f568a88a8f36ed`。本表是任务级日期化证据，唯一当前Phase/Gate仍在CURRENT_STATE。G2-01…09本地验收证据满足，未关闭P0=0/新P1=0；不等同main merge、remoteCI或发行。完整命令/counts/原始失败与攻击链见 [安全测试报告](PHASE2_SECURITY_TEST_REPORT.md)，逐Gate限制见 [验收报告](PHASE2_ACCEPTANCE_REPORT.md)。历史bit/实装APK/硬件未知与预存KDS完整角色流程保留相应后续任务。

| 任务 | 该观察点执行结果 | Commit / 可审查证据 / 余项 |
| --- | --- | --- |
| P2-BASE-01 | PASS_LOCAL | 92871df + c9f8d0f；Manifest/JSON/Runbook/source refs与checksum；[#275 Draft](https://github.com/Z1linXu/Restaurant_System/pull/275)。历史bit/实装APK未知仍限制发行 |
| P2-AUTH-02 | PASS_LOCAL | 3523b47 + 9c87944 + 739b708；有效scope、原始dormantgrants、锁后refresh/实际资源重验；独立review与最终1013全PG通过；完整supportgrant仍P6 |
| P2-WS-03 | PASS_LOCAL | 3523b47 + 9c87944；真实broker、SockJS、持续撤权与queuedframe；独立review与最终回归通过；物理Pad未认证 |
| P2-PROFILE-04 | PASS_LOCAL | 0f1b2a4 + 87d005b、739b708 HTTP负向；V38 conservativeownership/catalog/use/clone/replay；[#276 Draft](https://github.com/Z1linXu/Restaurant_System/pull/276)。独立review/最终全PG通过，unowned仍deny |
| P2-PRINT-05 | PASS_LOCAL；物理QA后续 | 0f1b2a4 + 30c1b52；Store/Org deviceauth、foreignassignment/七Pad操作及list/ack/reprint Bearer负向；独立review/最终回归通过；未改claim/lease/历史job |
| P2-OFFLINE-06 | PASS_LOCAL；A6-OFF-01/02 CLOSED | 初版c9de597源于129f66d；3985eac修复→1609f0d整合，c824d21独立复审→c2fa49a报告；no-redirect/manualbusy/generation与credential-associatedauth、DB5旧记录隔离通过；本地packaging/nativeguard通过，实机未证 |
| P2-INV-07 | PASS_LOCAL | 0f1b2a4；需求聚合、同店有序锁/ledger/订单事务；PG竞争/foreign/rollback与独立review/最终1013全PG通过；完整restock/prep NOT_IMPLEMENTED |
| P2-FIXTURE-08 | PASS_LOCAL；不声称remoteCI | 0f1b2a4 + 4621cb7 + 9c87944；startup/staticguard/2files扫描/3tests/合成fixture；6e9a370机械补menu测试slice依赖，无产品改动；最终全PG/frontend/native/guards通过；workflow定义/本地命令非remote run |
| P2-GATE-09 | PASS_LOCAL；G2-01…09证据满足 | 最终5ec8abe backend1013/0/0/0、frontend53files306/buildPASS；同产品树1609本地54assets/DB5/debugAPK与native6suites24PASS；全部独立reviewPASS、阶段P0=0/新P1=0。早期失败保留，主线程同步唯一CURRENT_STATE后续已授权P3 |

## Phase 2 — 基线与安全

### P2-BASE-01

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | Combined Baseline Manifest + 隔离 Commercial source/release流程；不重建正在运营的环境 |
| 依赖/模块/位置 | Phase 1 review；Git、backend/frontend Dockerfile/pom/lock、V1–V37、Android build/asset scripts、deployment现有 helper；新增阶段 Manifest/Runbook/只读校验脚本 |
| 步骤 | 核 B/F/M refs/ancestry/tree等价；保留来源；记录 image/OCI/evidence、schema checksum、build参数和config refs；在干净商业工作树分别构建并记录新产物hash；解释source与历史产物置信度；列APK/签名未知；定义hotfix backport路径 |
| 自动测试 | Git tree/ancestry校验、V1–V37 checksum guard、干净 backend/frontend build及相关unit；配置只校验结构/无敏感值；Android现有asset校验可运行则运行，不用SDK缺失冒充pass |
| 验收证据 | PHASE2_BASELINE_MANIFEST.md + PHASE2_SOURCE_CONTROL_RUNBOOK.md；source/tree/image/Flyway逐项证据、localbuild hash/工具版本、gap分级、工作树清洁与Owner旧main保护 |
| 风险/Rollback | 错认单一生产SHA、非确定构建、旧工作区污染；回滚仅撤销新文档/工具commit或停商业线，保留B/F refs与Owner原工作树，不触及线上 |
| DoD/并行 | source lineage可解释、本地安全构建可复现、blocking gap为零；历史bit一致/APK未知仍明确限制；必须先串行，基础fixture可在来源固定后并行 |

### P2-AUTH-02

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 撤掉无授权ADMIN业务全店通行；明确商家Owner、平台operator、Store role/membership与legacy边界；完整临时支持工作流延期P6-SUPPORT-03 |
| 依赖/模块/位置 | P2-BASE-01；common/auth StoreAccessService、AuthorizationService、RequestUserContextService、RoleCapabilityRegistry；platform/owner resource入口、auth配置/tests与API |
| 步骤 | 列actor→capability→resource owner调用链；平台metadata管理与business access分离；无有效商家授权的operator拒绝业务读写；PlatformAdmin path/body/parent/child归属一致；Staff all-store/跨店搬移、AuditLog无store全库读取与Analytics global rebuild须拒绝越权；active membership失效不得被legacy default回退；开发header仅显式安全本地profile；保留现店明确legacy兼容并测权限不提升 |
| 自动测试 | anonymous/Owner A/Owner B/Manager/Frontdesk/operator的401/403与正向；伪造store/org/resource IDs；inactive membership、mixed global/store role、header伪造；workspace/analytics/audit/staff不能列foreign资源；PlatformAdmin body指向foreignStore、人员跨店搬移与global rebuild负向；过期/撤销support访问默认deny |
| 验收证据 | security report每缺陷记录攻击场景、原链、修复点、负向与现店正向；API role/scope描述更新；无grant时不能靠ADMIN读业务 |
| 风险/Rollback | global role语义变化可能锁出现店；用合成legacy fixture验证；仅本地revert，不能为恢复测试开放跨组织；商业线停受影响业务保持deny |
| DoD/并行 | 所有业务授权统一有效scope、不给商家发平台ADMIN；共同DoD；Auth核心单负责人串行，接口稳定后交WS/Profile/Print/Offline复用 |

### P2-WS-03

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | STOMP CONNECT/SUBSCRIBE/消息授权及origin限制，保留事件名称/afterCommit语义 |
| 依赖/模块/位置 | P2-AUTH-02；common/config/WebSocketConfig、common/realtime、认证interceptor；frontend现有STOMP连接/hooks、Android runtime WS注入 |
| 步骤 | 跟踪浏览器/Pad凭据路径；选择现token可兼容的认证承载（不放URL/log）；握手origin白名单、CONNECT principal、SUBSCRIBE解析已知topic并requireStore；拒绝未知destination/wildcard/无许可SEND；token过期/撤权关闭旧session，重连重新验证 |
| 自动测试 | 真实本地WS/STOMP或服务器集成测试：匿名、bad/expiredtoken、错误origin、foreignStore、通配topic、伪造SEND均deny；允许本店订阅；A提交后B收不到事件；撤权后现有订阅不再收；reconnect可恢复本店 |
| 验收证据 | token/会话生命周期、origin与目的地授权表；负向记录不含token；本店POS→KDS/Ready refresh及REST一致性 |
| 风险/Rollback | SockJS/Android bearer兼容与旧连接存活；失败回滚客户端适配并禁不安全WS，不能恢复wildcard无scope；可暂用既有REST刷新 |
| DoD/并行 | CONNECT/持续订阅均可证明权限；只topic带StoreID不算通过；WS单写，可与不同文件Profile/Print并行 |

### P2-PROFILE-04

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | public preset与Org-private Profile授权，列举/读版本artifact/复制/使用/发布全路径；不建完整新Profile产品UI |
| 依赖/模块/位置 | P2-AUTH-02；owner/profile catalog/controller/entity/repositories、owner/master、provisioning resolve/materializer及clone入口；additive migration与tests |
| 步骤 | 逐一查既有API；增加visibility/owner Org契约（候选V38，协调后分配）；根Profile权限继承至version/artifact；public仅明确安全preset；现兰州种子private，优先用stores.provisioned_profile_code/version/fingerprint的一致引用确定唯一Org；V19跨Org同master_key不算owner证明，仅唯一可证明Master关系可后备，不硬写Org1，歧义PRIVATE-unowned deny；catalog/provision/clone都核scope，未知private不可读取 |
| 自动测试 | public可用；private A在B的list缺席、读/猜version/artifact/clone/publish/use均deny；非法owner/关系失败；同Org成功；V37→new迁移与重放验证；不改V1–37 checksum |
| 验收证据 | private可见性矩阵、授权资源链、合成迁移前后counts/ownership/权限、现店materialization及snapshot回归；不存在的publish API不得假造已验证 |
| 风险/Rollback | legacy owner歧义或seed被误公开；保持未知legacy不可授权；应用回退必须继续private deny，schema列保留做forwardfix；合成本地库可重建 |
| DoD/并行 | 私有内容全路径不可跨Org，同店旧Profile行为保留；schema串行唯一负责人，catalog与provision不得分Agent互改 |

### P2-PRINT-05

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | Store Device/Printer/Assignment/Print Job隔离，保留PAD_DIRECT claim/lease/attempt/防重复语义 |
| 依赖/模块/位置 | P2-AUTH-02；printing controllers/services/repositories、device认证/registration、PrinterConfig/assignments/job endpoints；必要frontend管理scope |
| 步骤 | 沿register→pending→claim→start→payload→complete/fail/reprint核actual owner；token绑定Store/设备/环境；跨Store printer/assignment及job-origin拒绝；区分配置管理能力与runtime readiness；MOCK/禁真实endpoint |
| 自动测试 | A的user/device不能列/claim/读payload/ack/reprint B job；A assignment不能引用B printer；pathStore与jobStore不一致deny；inactive/revokedtoken deny；并发claim只一成功、lease/ambiguous失败不盲重印；本店正向 |
| 验收证据 | print/device负向矩阵、mockpayload与历史job hash、现有printed/sourceKey/claim回归；明确未发送TCP/真实纸张 |
| 风险/Rollback | orphan job与旧token兼容；revert受影响API适配但保留scopeguard，禁dispatch而非重置状态；不得释放PRINTING后自动重印 |
| DoD/并行 | 跨Store无payload/endpoint/任务可读写；不破坏本店Pad流程；printing状态机单负责人，与Profile不同文件可并行 |

### P2-OFFLINE-06

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | deployment/account/Org/Store隔离，含Web与Android同bundled origin切API；保留pending和稳定retry；不实现新旧价政策 |
| 依赖/模块/位置 | Auth contract/接口既定后可从 P2-BASE-01 独立branch实现，最终集成与acceptance必须等P2-AUTH-02通过；不得改Auth公共函数签名；frontend offlineDatabase/menuCache/localDrafts/orderOutbox/workspace/auth/runtime config与processor；restaurant-pad-app MainActivity的preferences/bridge/Worker/APIURL配置 |
| 步骤 | 定义可信且稳定deployment identity（规范APIorigin或服务端契约，拒绝请求任意认领）；schema additive升级；所有cache/auth snapshot/outbox key与processor核namespace；DB4→DB5旧record来源不明原样隔离，不按当前环境认领/删除/回放；logout停会话；重配对/切商家/切服先停旧Worker/WS/请求，token按origin绑定，不发新服务器；可信origin才能调用nativebridge |
| 自动测试 | 两server使用相同user/org/store数字ID，A缓存/队列不在B可见/发送；logout/登录其他账户、切店、re-pair、离线冷启动；旧QUEUED/SUBMITTING/conflict升级后key/hash/payload不变、可同scope恢复；AndroidURL switch停止worker/不带旧token、untrustedbridge拒绝；401/403不cachedfallback |
| 验收证据 | JS与native各自测试命令/counts；版本迁移前后record/hash；Mock HTTP/假token观测；无SDK/实机则精确BLOCKED或HARDWARE_QA_PENDING，不能只测JS宣称Android完成 |
| 风险/Rollback | legacyqueue无法归属/IndexedDB downgrade/旧worker竞态；保留旧records与新namespace，禁旧未知自动发送；回退不得clear DB/改frozenpayload；DB5不能原地降级旧APK，需兼容DB5修复包或受控恢复；signedstorage加强放P7但跨origin凭据泄露立即修 |
| DoD/并行 | 同origin换server不泄露/误提交，账户/门店隔离，native安全部分有可重复验证；frontend/native同负责人协调，与Auth共享变更串行 |

### P2-INV-07

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 当前启用BOM扣减的Store关系与库存并发安全；未实现restock/prep不冒充完成 |
| 依赖/模块/位置 | P2-AUTH-02；OrderServiceImpl.deductInventory、inventory实体/repository、MenuItemBom/OptionBom与recipe refs；若必要新增约束migration，不抢Profile序号 |
| 步骤 | 枚举现可写入口；menu/option/BOM/inventory均验同Store；共享库存按稳定顺序加锁或原子条件扣减，流水和订单同事务，防死锁/不足；不能仅关库存UI而保留危险submit写；未完成路径拒绝/明确未提供，不臆造接口 |
| 自动测试 | 本地PostgreSQL两并发订单扣同库存总额/ledger守恒；不足只允许正确成功数；BOM跨Store/Org失败且零部分写；optionBOM、取消/rollback、相同idempotency仅扣一次；存在prep写路径同样验证，不存在记NOT_IMPLEMENTED |
| 验收证据 | SQL锁/事务策略、Postgres竞争结果与counts；跨Store负向和库存ledger检查；API/SYSTEM明确基础能力边界 |
| 风险/Rollback | 锁顺序、负库存政策、性能；回滚保留failclosed关系检查，禁不安全库存消耗能力或停止测试，不重放扣减/重建真实ledger；DBforwardfix |
| DoD/并行 | 启用路径跨店拒绝且并发正确，或严格不可执行并明确gate blocker；完整库存延期理由不掩盖危险写；OrderService与snapshot/printing同文件不可并行 |

### P2-FIXTURE-08

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 两Org多Store合成fixture、可重复负向矩阵与Commercial默认配置CI防线 |
| 依赖/模块/位置 | P2-BASE-01；tests/resources、frontend tests、backendPostgres test harness、Android unit、现有CI/scripts；运行断言依赖AUTH/WS/PROFILE/PRINT/OFFLINE/INV实现 |
| 步骤 | 建无真实业务数据的OrgA/B、A1/A2/B1、多role、private/publicprofiles、MOCKprinters；复用现harness并显式启用Postgres；扫描新commercialdefaults/fixtures禁止生产域名、真实IP、secret；历史证据/不可变migration只受精确allowlist，不批量改写 |
| 自动测试 | fixture重建/隔离；扫描positive/negative cases含错误默认；V37checksumguard与新migration唯一版本；现订单幂等/Add-on/Combo/打印snapshot/frontendoutbox回归；确认集成测试实际执行非skip |
| 验收证据 | fixture定义、CI命令与counts/skip理由、scanner覆盖边界、无externalnetwork/print；整合G2测试矩阵 |
| 风险/Rollback | scanner误报/fixture反成真实模板；回退仅fixture/CIcommit保留应用guard；不能靠放宽扫描隐藏secret |
| DoD/并行 | 单指令可重复建合成本地环境与相关检查，负向覆盖真实边界；BASE后可并行写tests/resources，避免共享API或migration修改 |

### P2-GATE-09

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 整合P2证据、独立review和Phase3 entry判定，不加相邻功能 |
| 依赖/模块/位置 | P2-BASE-01/AUTH-02/WS-03/PROFILE-04/PRINT-05/OFFLINE-06/INV-07/FIXTURE-08；阶段报告/Backlog/Planbook/验收矩阵/SYSTEM，CURRENT_STATE由主线程协调 |
| 步骤 | 在集成exactSHA重跑受影响跨模块回归；逐G2核证据与P0；检查secret及用户工作区保护；每baselinegap分级；记录真实review/PRcommit；G2不全过留BLOCKED并继续独立修复 |
| 自动测试 | G2-01…09；backendfocused+Postgres安全/库存+frontend关键suite/build+Android相关tests/assetchecks，实际命令与counts写报告 |
| 验收证据 | PHASE2_SECURITY_TEST_REPORT.md、PHASE2_ACCEPTANCE_REPORT.md；exactSHA、PASS/FAIL/BLOCKED/NOTRUN、剩余风险/物理未知、Phase3 entry决定 |
| 风险/Rollback | 只见编译或历史70PASS即误过gate；发现缺陷回所属PR最小修复，保留证据，禁止跳Phase；文档可revert但不能删失败证据 |
| DoD/并行 | G2全过、P0=0、blockingbaselinegap=0才DONE；本次Phase3已授权可继续；gate汇总串行 |

## Phase 3 执行快照 — 2026-10-15

实现观察点`e565be0`（含打印`57fe6ad`）。142focused已通过，Neutral/configconsumer与UI仍在实现；**六任务未最终关闭、G3-01…08均PENDING**。最终exactSHA、完整counts与独立review只在 [P3验收报告](PHASE3_ACCEPTANCE_REPORT.md)记录；当前Phase仅CURRENT_STATE负责。53HC/22DG原主任务不改：P3重点13项逐一列实现/待验/延期，不把其他Phase推定完成。

| 任务 | 观察点执行证据 | 最终关闭所缺 |
| --- | --- | --- |
| P3-CONFIG-01 | IMPLEMENTED_FOCUSED_ONLY：e565be0 typed九字段summary、strict四字段OwnerPUT、V39、revision/audit/menu invalidation；相关unit在142focused | 最终合同/API、Bearer正负向与真实consumer、PG/history、独立review |
| P3-PROFILE-02 | IN_PROGRESS：57fe6ad已隔离Neutral打印default/fullsnapshot；V40/显式空白Store创建由配置作者收敛 | Neutral/public-private/clone独立graph、fresh/V37upgrade、空白counts与原canonicalfingerprint、OwnerHTTP及独立review |
| P3-READ-03 | IN_PROGRESS：既有Canonical三层SoT/StoreownedCombo保留，metadata无经营数据复制；configrevision/menu invalidation已在scaffold | StoreContext/catalog/UI读取同summary、缓存revision/晚响应、旧outbox/synthetic兼容与historyhash；复杂算法P4/P5 |
| P3-UI-04 | IN_PROGRESS：专属UI分支执行；未以未提交实现作证据 | 同build双Storebrand/modules、disabled零API/WS、配置入口可达、切店/logout/768/1024与最终tests |
| P3-FIXTURE-05 | PARTIAL_FOCUSED_ONLY：phase3-core-focused.log 142/0failure/0error/0skip；de1032c freshUberPG57/0/0/0分别记录；Neutral打印与legacy受影响局部回归 | 双Profile创建/PG/history/完整兼容、受影响G2负向、frontend/native集成；不能与最终suite混计 |
| P3-GATE-06 | PENDING | G3-01…08、最终exactSHA/tests/review/remainingP0-newP1、技术合同/API/SYSTEM、HC/DG与[Phase4门槛](PHASE4_ENTRY_GATES.md)齐备；只汇总不实施P4 |

## Phase 3 — 配置与 Profile 基础（不实现完整菜单引擎）

### P3-CONFIG-01

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | Versioned Merchant Configuration contract、typed effective reader及Store-scoped最小读写/校验、revision/cache metadata；复用现表与artifact，不建重复source |
| 依赖/模块/位置 | P2-GATE-09；common配置边界、Store/Profile/Module DTO、现有Storecontext/provisioning contract；PHASE3_CONFIGURATION_CONTRACT.md；必要additivemigration串行 |
| 步骤 | 明确Platform/Org/Store/Device/User/Item逐字段ownership/override；schema_version、revision、稳定code/ref；metadata/brand/locale/CAD/timezone/taxownership/modules/profile/station/printrole/menu-option-comboref占位；unknownschema/ref/foreignscope拒绝；安全ceiling不可覆盖；只读现Store经营事实 |
| 自动测试 | 类型/版本/unknownfield策略、默认/继承/冲突、invalidminmax/同店ref、foreignOrg、缺配置failsafe；可选hardware不阻StoreLIVE；readonly不改orders/jobs |
| 验收证据 | 字段source与读取矩阵、schemaexamples合成、API兼容diff、配置readerunit/集成；未实现通用算法显式标占位 |
| 风险/Rollback | 契约过大/双写source；最小字段/现有命名，旧readers兼容fallback仅限明确legacyprofile；revert新reader不改经营rows，unknownconfig保持deny |
| DoD/并行 | 统一最小读取接口与schema验证可供P4依赖；合同/DTO/schema单负责人串行 |

### P3-PROFILE-02

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 保留private兰州Profile、显式Neutral初始化；隔离默认seed/clone/printingdictionary入口；复杂renderer/Combo仍兼容 |
| 依赖/模块/位置 | P3-CONFIG-01、P2-PROFILE-04；owner/profile/provisioning、OperationalStoreBaselineProvisioner、RuntimeDataSeeder、PrintingDisplayRuleDefaults入口与fixtures；newadditivemigration若必需 |
| 步骤 | 既有ST_DENIS_CANONICAL_PROFILEv2不改code/fingerprint；LANZHOU_NOODLE为业务/fixture别名；Neutral无菜单/硬件/真实默认ID；复用现通用Store API+最小配置初始化服务创建新空白Neutral Store，不造完整provisioning；Owner同品牌Master materialization保明确PRIVATE legacycompat；不调用Store1clone；V5/V15/V19不可变seed保持private不可见/不物化；中性printfallback完整名；现店字典显式选择 |
| 自动测试 | freshDB运行migration后新Neutral无兰州经营菜品/SKU/默认printer/table/staff；不能读取legacyprivateartifacts；双Store独立mutablegraph；legacycanonical创建与面型/Combo/displaygolden一致；seedflags错误启动failsafe |
| 验收证据 | PHASE3_PROFILE_DESIGN.md、空库/upgrade初始化counts、profile/artifactfingerprint、public/private矩阵；未提取算法标PROVISIONAL_COMPATIBILITY及P4/P5任务 |
| 风险/Rollback | 私有旧seed仍在库但误暴露、默认改变破旧店；只在显式商业Neutral入口启用；revert新选择不发布seed、不重写旧Profile；迁移forwardfix |
| DoD/并行 | Neutral可创建而无现店配置污染；兰州兼容未删；profile/provision/seed/打印默认重叠必须串行 |

### P3-READ-03

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | Menu Owner/Frontdesk、Order与Kitchen/Printing采用同稳定身份/配置读取边界；仅接口/适配，不改完整价格/renderer状态机 |
| 依赖/模块/位置 | P3-PROFILE-02；StoreAddonOptionResolver、menu catalog/Owner projection、Profile读取、menu/configrevision与缓存元数据；Order/printing调用适配处 |
| 步骤 | 复用Orgcode→Storeprice→itemeligibility；同店menu/option/combo/stationrefs统一；旧egg/side与syntheticoptionID映射放明确compat；配置事务更新menu/configrevision，前台/Android失效；snapshot只写新单，历史不recompute；复杂语义分配P4/P5 |
| 自动测试 | Owner与POS一致、Add-on编辑只一source且跨店无影响；revision变化/缓存刷新；旧outboxpayloadroundtrip；历史order/task/printjobhash不变；scope负向持续过 |
| 验收证据 | 规范化readprojection与各消费者来源图、revision/缓存测试、historyhash与兼容清单 |
| 风险/Rollback | 适配变成另一engine/隐式双source；revertreadadapter保留canonicalresolver，配置schema向后兼容，不rewriterecords |
| DoD/并行 | 初始统一读取入口真实使用且snapshot稳定；不宣称通用Modifier/Combo完成；共享Order/Menu/打印adapter单写串行 |

### P3-UI-04

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 中性品牌/双语入口与模块消费，保持iPad大按钮、低步骤；不做完整i18n/品牌模板编辑器 |
| 依赖/模块/位置 | P3-READ-03；frontend Storecontext/featureConfig/storeModuleAccess/App、Owner/Frontdesk品牌locale读取；不同时改共享API |
| 步骤 | 显示Storebrand而非默认餐厅；仅将已配置locale/CAD当明确capability；disabledKDS/Uber不启动request/subscribe/worker；配置页面仍可管理；profile-specific控件显式兼容标识，后端仍授权 |
| 自动测试 | 同build两Store不同modules/brand；切店清旧context不显示foreign；disabled无network/subscription，directAPI仍403；768/1024layout与现frontdesk关键交互 |
| 验收证据 | syntheticUI截图/测试、module请求计数、无新增内部stage按钮/重复配置；法语仍按上市gate识别缺口 |
| 风险/Rollback | hiddenpage误当backend安全/品牌空值；回退UI适配保留backendgate和scope；不改历史小票语言 |
| DoD/并行 | NeutralUI不带兰州品牌、模块前后端一致；可与fixture在不同文件并行，sharedAPI先冻结 |

### P3-FIXTURE-05

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 可重复LANZHOU_NOODLE与NEUTRAL_RESTAURANT合成fixture，证明兼容/中性与持续隔离 |
| 依赖/模块/位置 | P3-CONFIG-01（可准备）；最终验证依赖PROFILE-02/READ-03/UI-04；tests/resources/backend/frontend/Androidtestmock |
| 步骤 | 复用P2fixture；兰州合成面型/Combo/Add-on/桌台/GRAB/HOT/FRONTDESK/离线；Neutral无兰州初始菜单，另显式添加普通商品测试通用code；双Org禁止private共享 |
| 自动测试 | neutral空白与nonnoodlename/SKU反例；legacygolden/价格/默认值/纸张payload；menu/configupdate不改history；offline+scope+device负向；migrationempty/upgradecase |
| 验收证据 | tests精确counts/命令、goldens及hash、PROVISIONAL_COMPATIBILITY列表，不能用真实菜单/账号dump作fixture |
| 风险/Rollback | fixture过拟合或误共享privatepack；撤fixturecommit不动生产，保持测试失败不删断言 |
| DoD/并行 | 两Profile可重复且结果边界可解释，核心安全持续过；contract冻结后可并行只写fixture/tests |

### P3-GATE-06

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | G3集成验收、HC关闭/延期与Phase4可领取契约，不进入Phase4 |
| 依赖/模块/位置 | P3-CONFIG-01/PROFILE-02/READ-03/UI-04/FIXTURE-05；阶段报告、七规划doc/SYSTEM，CURRENT_STATE主线程同步 |
| 步骤 | exactSHA逐G3验；复核P2负向；列未隔离legacy算法/后续任务；检查API/schema/旧Profile兼容和无线上变更；review与证据回写 |
| 自动测试 | G3-01…08集成、backend/Frontend/Android受影响suite、empty/upgradeDB、historyhash与module/caching |
| 验收证据 | PHASE3_ACCEPTANCE_REPORT.md，任务commits/tests/counts、HCstatus、migration/contract、残余风险与P4entry |
| 风险/Rollback | 骨架误宣称已商业通用/已可PDF直接交付；缺项留PARTIAL/BLOCKED，回实现PR修复；不能通过改gate语言绕过 |
| DoD/并行 | G3全过、P4稳定契约已列、无Production/Staging变更；串行汇总；完成后停止等待后续授权 |

## Phase 4+ — 明确延期的执行单元

以下均 `PLANNED / NOT AUTHORIZED IN THIS RUN`，仍按共同DoD执行；真实菜单和客户细节由后续授权任务确认，不再问二十项产品方向。

### P4-MENU-01

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 通用Menu/Variant实体与引用；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P3-GATE-06；menu实体/DTO/controller/Owner+POS投影、必要additiveSQL |
| 实施步骤 | 复用现Store-owned结构→定义stable identity/variant/双语字段与sameStore refs→兼容现snapshot |
| 自动测试 | schema/ref负向、Owner/POS一致、empty/upgradeDB、history冻结 |
| 验收证据 | PHASE4_MENU_CONTRACT及合成往返证据 |
| 风险/Rollback | 关系迁移和旧客户端；以兼容reader/featuregate回退，不改旧ID/快照 |
| DoD/并行 | 模型contract可支撑Modifier/Combo；共享schema串行；另须共同DoD |

### P4-MODIFIER-02

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 可配置Modifier min/max/default/quantity与Add-on/Remove；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P4-MENU-01；menu option/group service、canonical resolver、POS customization/OwnerUI、版本化payload |
| 实施步骤 | 定义数量/免费额度明确计算规则→同语义后端校验→iPad控件→显式legacygroup适配 |
| 自动测试 | 必选/单多选/边界/default、tamper、同名不同code、canonicalprice唯一、离线roundtrip、旧打印 |
| 验收证据 | 规则矩阵及UI/后端计价一致证据 |
| 风险/Rollback | 新规则影响金额；默认关闭新schema，旧preset与outbox兼容，回退不重算历史 |
| DoD/并行 | 规则与价格无双source；共享DTO/菜单关系串行；另须共同DoD |

### P4-COMBO-03

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 通用选择组和linked real item，替代菜名/SKU关联；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P4-MODIFIER-02；menu/combo、useMenuCatalog/comboSelection、Order task/BOM与snapshotadapter |
| 实施步骤 | 建立durablecomponent identity→映射旧负ID不重编号队列→显式linkeditem/minmax/default→旧egg/side适配 |
| 自动测试 | hashcollision、payload回放、remove/BOM/tasks、默认选择、价格及GRAB/HOTgolden、跨店引用 |
| 验收证据 | 旧/新Combo行为对照与迁移兼容报告 |
| 风险/Rollback | 套餐任务/金额/纸张连锁；按schema/profile切回旧适配，保留历史/队列，不删legacybranch |
| DoD/并行 | 无需菜名触发通用逻辑；Order/printing/Flyway串行；另须共同DoD |

### P4-PRICE-04

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | Store默认+Item override、税/CAD/营业日与有限旧价政策；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P4-COMBO-03；menu/pricing、TaxCalculator、Order金额/旧价校验、frontendtax/reportformat、dailyclose |
| 实施步骤 | 先明确税/舍入/生效日/旧价期限与来源证据→版本化policy→server校验→冲突确认保持retry→Storetime/币种投影 |
| 自动测试 | 边界舍入、override、伪造/过期旧价、不可售、canonicalAdd-on、DST/跨夜、历史reprint不重算 |
| 验收证据 | 金额/税/时间casebook与商家确认记录；不伪称法律合规 |
| 风险/Rollback | 业务数值未定不得猜；保留旧Profilepolicy、版本化切换，旧outbox不静默改payload |
| DoD/并行 | 价格/税/日期source明确且历史稳定；金额核心串行；另须共同DoD |

### P5-KITCHEN-01

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | Store厨房模式/工位/输出role及中性task语义；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P4-PRICE-04；kitchen/task、Order kitchen snapshot、KDS、module/route配置 |
| 实施步骤 | station与输出角色显式关联→移开noodle中文/SKU推断→保留兰州adapter→printer/KDS/hybrid组合 |
| 自动测试 | 任意station、不出任务商品、ready/served、模块禁用无订阅、现noodle/hot/passgolden |
| 验收证据 | 组合矩阵、task快照与既有流程对照 |
| 风险/Rollback | 漏单/重复任务；profilegate回兼容adapter，不重建已提交任务 |
| DoD/并行 | 通用route不靠菜名；OrderSnapshot/KDS核心串行；另须共同DoD |

### P5-PRINT-02

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 有限receipt presets、品牌/语言/alias/sort及中性fallback；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P5-KITCHEN-01；printing renderer/semantic/displayrules、Owner打印配置与nativepayloadcontract |
| 实施步骤 | 配置WHAT/WHERE/HOW分离→profile字典/omit/排序→有限版式→job冻结revision→认证型号case |
| 自动测试 | 旧42GRAB及新增非面食/同名反例、HOT混单、frontdesk税/抬头、historyjobhash、claim防重 |
| 验收证据 | 版本化纸张golden/本地MOCK；物理QA在另授权环境完成 |
| 风险/Rollback | 漏打/格式错/ambiguous结果；新job切旧preset，旧job保留原payload，不盲重印 |
| DoD/并行 | 中性与兰州各自稳定；renderer/PAD_DIRECT串行；另须共同DoD |

### P5-TABLE-03

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 整桌/可选A-B与counter-only配置；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P4-PRICE-04；tables/dinein/Order tablecontext、provisioningservice-modepreset |
| 实施步骤 | 定义NONE/WHOLE/AB受支持模式→新店按preset建桌→保留旧suffix不改活跃订单 |
| 自动测试 | 无堂食、整桌/AB并发、restore草稿、含-A普通桌号反例、旧orderreceipt |
| 验收证据 | 触屏流程和兼容桌号证据 |
| 风险/Rollback | 活动订单归桌丢失；只回退新配置，不批量改table_no |
| DoD/并行 | 无任意分账/拼桌承诺；可与报表不同文件并行；另须共同DoD |

### P5-INTEGRATION-04

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 可选Uber商家授权/映射与财务隔离；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P5-KITCHEN-01、P4-PRICE-04；integration/ubereats、模块API/workers、channelanalytics |
| 实施步骤 | 每独立部署secret/merchantbinding→显式mapping→disabled全路径→保留KitchenMirror/财务source与备注冻结 |
| 自动测试 | synthetic签名/重复webhook/accepted/cancel、disabled不import/reprint、映射foreignStore、channel守恒 |
| 验收证据 | 无真实平台请求的合同/测试；外部许可另证 |
| 风险/Rollback | 双算/误接真实单；关Store/global能力、保留inbox不自动replay，不改生产off |
| DoD/并行 | 可选功能边界完整；与core金额/sharedsnapshot串行协调；另须共同DoD |

### P5-REPORT-05

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 基础报表category映射中性化；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P4-PRICE-04；analytics/SalesReporting与frontendreportpanels |
| 实施步骤 | 现noodle分组放profile→动态reportingcategory映射→store/currency/date读取→不重建真实summary |
| 自动测试 | 取消过滤、行分摊/总额守恒、无面食category、foreignOrg、KitchenMirror不双算 |
| 验收证据 | 合成report对账与historyhash |
| 风险/Rollback | 分类变化影响历史；版本化映射、保留原summary，重建需独立授权 |
| DoD/并行 | 首发基础报表真实一致；可与tables不同文件并行；另须共同DoD |

### P6-IMPORT-01

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | Merchant manifest/schema校验、diff/dryrun/幂等execute；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P5-PRINT-02、P5-TABLE-03、P5-REPORT-05；现provision/materialization/管理API之受控工具、schema和syntheticfixtures |
| 实施步骤 | schema→refs/去重→dryrun零写→expectedrevision/fingerprint→operationkey事务执行→失败补偿/恢复 |
| 自动测试 | 重复执行零重复、hash冲突、invalidrefs/crossOrg、TOCTOU、故障注入全回滚、无secret/真实defaults |
| 验收证据 | 工具commands与manifest/diff/report合成样本 |
| 风险/Rollback | 误指商家/部分导入；先仅独立测试DB，后按exacttarget执行，forwardfix/版本回切不抹营业单 |
| DoD/并行 | 复用现pipeline而非第二framework；schema/事务单写；另须共同DoD |

### P6-PLAYBOOK-02

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 外部Codex交付Playbook与可复用Prompt；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P6-IMPORT-01；MERCHANT_ONBOARDING_PLAYBOOK.md、NEW_MERCHANT_CODEX_PROMPT.md及工具文档 |
| 实施步骤 | PDF会话阅读→draft→只问业务未确认→Owner确认→validate/dryrun→独立测试→点餐/打印→发布候选；生产最终另批准 |
| 自动测试 | 用两份合成不同菜单执行完整工具路径、链接/schema校验，无POS PDF/LLMendpoint |
| 验收证据 | 从输入到配置/差异/验收完整演练 |
| 风险/Rollback | 误把草稿当确认/工具默认指生产；候选manifest撤回，保留audit不发布 |
| DoD/并行 | 可由未来Codex重复执行且无需复制业务源码；文档可并行但需已稳定toolcontract；另须共同DoD |

### P6-SUPPORT-03

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 限期/限范围/可撤销商家支持grant；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P2-AUTH-02、P3-CONFIG-01；auth授权/service、审计、最小运营工具，必要additivegrant表 |
| 实施步骤 | 明确商家授予→scope/expiry→每次验证→撤销/到期→审计；不新增普通staffADMIN |
| 自动测试 | 无grant/过期/撤销/foreignscope/操作超界deny、成功审计不含secret、WS现session撤权 |
| 验收证据 | grant威胁模型、权限矩阵与审计证据 |
| 风险/Rollback | 临时权限变永久；关闭grant路径不开放ADMIN，保留审计记录 |
| DoD/并行 | Owner已定业务不重问；与Auth/WS改动串行；另须共同DoD |

### P7-ANDROID-01

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 通用签名App、APK/assets/contract与有限硬件认证；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P5-PRINT-02、P2-OFFLINE-06；AndroidGradle/Keystore-storage/bridge/Worker、asset构建工具与分发runbook |
| 实施步骤 | 签名secret外置→版本manifest→受控商家绑定→token安全存储→DB5兼容升级/修复包→有限型号纸张验收 |
| 自动测试 | unit/native mockedHTTP/assetverify/build、同origin切服/升级保队列、deviceauth、实机网络/编码/切纸/ambiguous防重 |
| 验收证据 | APKhash/signingfingerprint（非私钥）/assetmanifest/contract，逐设备QA |
| 风险/Rollback | 丢队列/不兼容降级；DB5不直接降旧APK，使用兼容修复构建；不清appdata |
| DoD/并行 | 无未认证硬件承诺；实机权限另获，native与build单写；另须共同DoD |

### P7-DEPLOY-02

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 独立商家环境、发布/backup/restore/监控交付；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P6-IMPORT-01、P7-ANDROID-01；现cloudhelper/Compose/backup/restore/health工具与参数化runbook |
| 实施步骤 | 复用exactSHA流程→每商家DB/env/secrets→参数与目标guards→合成backup/restore→RPO/RTO/retention责任→独立release/rollback |
| 自动测试 | 无生产默认scanner、错target拒绝、合成restore/migrate/replay/readiness、secret未入git |
| 验收证据 | 独立商家演练与hash、恢复耗时/数据完整性、监控接收责任 |
| 风险/Rollback | oneoff指真实店/旧restore抹新单；只隔离演练，真实发布/恢复另批准，应用回退与DB前向修复分开 |
| DoD/并行 | 可重复交付且未知承诺已采集；部署/secret/迁移单写；另须共同DoD |

### P7-LEGACY-03

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 仅删除证明不可达的mock/menuImportSeed；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P3-FIXTURE-05；frontenddata与import/build图 |
| 实施步骤 | 查静态/动态import与bundle→确认无demo/profile依赖→移除验证不可达文件 |
| 自动测试 | typecheck/build/关联测试、bundle无变化及demo入口验证 |
| 验收证据 | reachability证据与小diff |
| 风险/Rollback | grep误判死代码；revert删除commit即可，不影响经营数据 |
| DoD/并行 | 非阻断维护项；可独立并行，不主动抢入P2/P3；另须共同DoD |

### P8-PILOT-01

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 首批跨业态真实Pilot与上市验收；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P5-INTEGRATION-04、P6-PLAYBOOK-02、P7-DEPLOY-02；商家独立测试/候选release、无业务代码fork |
| 实施步骤 | 采集客户税/语言/营业时间/设备事实→合成及商家授权测试→点餐/厨房/打印/离线恢复→确认支持/备份责任→exact生产候选另审批 |
| 自动测试 | 非面食/套餐/无堂食、跨scope、实机、税舍入/DST/语言、备份恢复与升级回归 |
| 验收证据 | 逐商家签收、剩余风险、release来源、人工生产批准 |
| 风险/Rollback | 把P3误称可售/法语税务缺口；不发布、候选回滚，已营业数据按恢复计划保护 |
| DoD/并行 | 真实Pilot过且发布gate单独批准；只能在未来授权环境执行；另须共同DoD |

### FUTURE-INVENTORY-01

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | 完整restock/prep/库存流水管理（非首发承诺）；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | P2-INV-07及新Owner范围授权；inventory/prepcontroller/service/DTO/ledger与UI |
| 实施步骤 | 先写真实API与事务合同→补restock/executePrep→库存流水/单位/权限→基本UI |
| 自动测试 | 并发、重复补货/备菜、事务回滚、BOM/prep同店、库存ledger守恒 |
| 验收证据 | 完整API与集成报告 |
| 风险/Rollback | 超Q19首发范围；模块关闭/拒绝入口，已启用deduction保持P2安全，不假设缺失API |
| DoD/并行 | 待需求再启动，非Phase3或首发硬依赖；库存共享schema/事务串行；另须共同DoD |

### FUTURE-CONNECTOR-02

| 字段 | 可执行内容 |
| --- | --- |
| 目标/范围 | Grab等新外卖connector（未获范围授权）；延期原因：依赖尚未稳定或不属本轮P2/P3 |
| 依赖/模块/位置 | 真实平台API/许可与新Owner授权；独立integration模块，不能复用GRAB厨房名冒充 |
| 实施步骤 | 取得正式contract→威胁模型/merchantauth→受控mapping→syntheticconnector→独立验收 |
| 自动测试 | 签名/去重/foreignscope/disabled不工作/财务守恒 |
| 验收证据 | 平台contract与独立connector证据 |
| 风险/Rollback | 缺API/许可；保持不可用，不虚构已集成；撤模块不改现GRAB |
| DoD/并行 | 仅授权后估实施；与既有Uber/printcore分离；另须共同DoD |

## HC 逐项处置（53/53，原规划与 P2 实施证据）

原始证据/风险与固定代码行见 [03](03_HARDCODE_AND_CUSTOMIZATION_INVENTORY.md)。每项保留一个主责任任务；以下P2实施注记对应最终5ec8abe观察点与G2本地证据，不等于发行/remoteCI/物理认证。其他Phase规划与延期理由保持。完整结果见两份P2报告。

| HC | 原规则族 | 判断/阶段 | 主任务 | 保留策略或延期理由 |
| --- | --- | --- | --- | --- |
| HC-001 | 事务、快照、幂等和持久 dispatch outbox | CORE / 保留 | [P2-GATE-09](#p2-gate-09) | 通用事务/快照/幂等/outbox保留；只做新fixture回归，不重写状态机。  **P2实施：** CORE保留；最终1013全PG/306frontend回归通过，不重写事务/状态机/历史snapshot。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-002 | ADMIN 全商家访问及 Owner 全 capability | Phase 2 / CORE安全 | [P2-AUTH-02](#p2-auth-02) | 关闭无授权ADMIN业务通行；完整支持grant交P6-SUPPORT-03，未授权先deny。  **P2实施：** PASS_LOCAL：ADMIN业务deny、有效scope与dormantgrant/锁后授权已独立关闭，最终集成通过；supportgrant留P6且无授权先deny。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-003 | WebSocket wildcard origin 与缺失订阅 scope 检查 | Phase 2 / CORE安全 | [P2-WS-03](#p2-ws-03) | CONNECT、持续SUBSCRIBE、origin/token撤权逐项负向，不靠topic名。  **P2实施：** PASS_LOCAL：真实broker/精确topic/Origin/持续及queued撤权、最终集成通过；真实Pad保留后续验收。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-004 | 固定角色矩阵和 FRONTDESK 管理权限 | Phase 2 / CONFIG | [P2-AUTH-02](#p2-auth-02) | 固定最小role preset及有效Store role；不建任意ACL编辑器，保留现店兼容。  **P2实施：** PASS_LOCAL：global/membership有效preset、不可比较冲突deny、aggregate逐店检查，独立review/最终回归通过；未建任意ACL。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-005 | legacy default Store fallback / 开发 header | Phase 2 / CORE安全 | [P2-AUTH-02](#p2-auth-02) | membership失效不能fallback，开发header与commercial启动隔离。  **P2实施：** PASS_LOCAL：任意状态membership存在即不legacyfallback，commercial/headerguard与最终回归通过；真实legacy账号依赖未知。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-006 | 离线客户端价格/名称/station 快照授权 | DEFER Phase 4 / CONFIG | [P4-PRICE-04](#p4-price-04) | Q09旧价政策缺期限/来源契约；本轮保留既有canonical校验、不接受任意新price信任，P2保护namespace/key。 |
| HC-007 | 库存 read-modify-save 无共享库存锁 | Phase 2 / CORE安全 | [P2-INV-07](#p2-inv-07) | 现启用写入必须锁/原子扣减并验证PostgreSQL竞争，不因完整库存延期忽略。  **P2实施：** PASS_LOCAL：同inventory需求聚合/有序同店锁/订单事务，PG竞争与ledger守恒，最终1013全PG通过。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-008 | BOM 引用库存未显式比较 Store | Phase 2 / CORE安全 | [P2-INV-07](#p2-inv-07) | item/option BOM同店及零部分写强制，坏引用failclosed。  **P2实施：** PASS_LOCAL：foreign库存引用零部分写、item+optionBOM聚合与PG回滚、最终回归通过；不自动修真实BOM。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-009 | 库存补货/备菜 API 文档超前 | DEFER Phase 4+ / MODULE | [FUTURE-INVENTORY-01](#future-inventory-01) | Q19不承诺完整库存；P2文档标缺失并严格关闭未实现写，不能假设restock/prep API。 |
| HC-010 | 固定 14.975% 税率及标签 | DEFER Phase 4 / CONFIG | [P4-PRICE-04](#p4-price-04) | 税effectivepolicy/快照须与金额engine同PR；P3只给归属/引用骨架，上市另确认税规则。 |
| HC-011 | 统一 Toronto 时区与固定日结调度 | DEFER Phase 4 / CONFIG | [P4-PRICE-04](#p4-price-04) | Store营业日/DST与历史时间解释需金额日结验收，P3只配置归属。 |
| HC-012 | CAD / en-CA 金额展示 | DEFER Phase 4 / CONFIG | [P4-PRICE-04](#p4-price-04) | 首发CAD仍明确限制；币种/order来源和报表格式随定价统一，P3UI仅读取已有值。 |
| HC-013 | Canonical Add-on 目录已可配置 | CORE / 保留CONFIG | [P3-READ-03](#p3-read-03) | 现canonical三层source复用，不复制15项现店默认，不新建逐菜价格source。 **P3实施：** PENDING_FINAL：沿StoreAddonOptionResolver既有Orgcode/Storeprice-name/Itemeligibility与Store-ownedcombo rows，不复制15项/五combo默认；最终reader/revision/cache待证，完整算法P4。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-014 | Option group 与 REMOVE/SOUP/NOODLE UI 类型固定 | DEFER Phase 4 / PROFILE兼容 | [P4-MODIFIER-02](#p4-modifier-02) | 完整group/minmax/quantity需要新契约；P3保留旧group显式兼容。 |
| HC-015 | 标准 Small/Regular/Large 与统一 combo 差价 | DEFER Phase 4 / CONFIG | [P4-PRICE-04](#p4-price-04) | 保留当前size/default政策，新增单菜override随价格engine验证。 |
| HC-016 | 五个内置套餐蛋/小菜定义 | Phase 3 / PROFILE | [P3-PROFILE-02](#p3-profile-02) | 五内置项只属显式兰州兼容，Neutral不注入；通用组engine交P4-COMBO-03。 **P3实施：** PENDING_FINAL：Neutral不注入枚举五套餐目标需初始化counts证明；既有Store-ownedgroups读取不改，synthetic/完整combo语义DEFER P4-COMBO-03。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-017 | Combo Side 根据 code/name 映射三种 SKU | DEFER Phase 4 / PROFILE | [P4-COMBO-03](#p4-combo-03) | legacy菜名/SKU关联未验收不删；P3限定私有兼容入口。 |
| HC-018 | Frontend legacy synthetic option IDs 与 hash fallback | DEFER Phase 4 / PROFILE | [P4-COMBO-03](#p4-combo-03) | durableID/旧负ID/hash碰撞与离线payload要一起改；P2先保原队列。 |
| HC-019 | POS 面型显示、noodle category 与 egg/side 兜底 UI | DEFER Phase 4 / PROFILE | [P4-MODIFIER-02](#p4-modifier-02) | POS面型/eggside控件要与新schema一致；P3不伪称任意modifierUI。 |
| HC-020 | OrderService 厨房名、面型、省略规则 | DEFER Phase 5 / PROFILE | [P5-KITCHEN-01](#p5-kitchen-01) | 任务冻结名称/omit影响业务，P3仅兼容边界不重写Order厨房语义。 |
| HC-021 | 全局默认 PrintingDisplayRuleDefaults 实为餐厅字典 | Phase 3 / PROFILE | [P3-PROFILE-02](#p3-profile-02) | 默认字典入口中性化与显式兰州选用；完整rendererfallback提取交P5-PRINT-02。 **P3实施：** IMPLEMENTED_FOCUSED_ONLY：57fe6ad空Neutral字典/完整snapshot/可访问missing-settings，NeutralProfilePrintingTest等在142focused；最终legacy/history待证；通用renderer/presets仍P5。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-022 | HOT 打印由固定 WOK/DEEPFRIED/category/煎蛋决定 | DEFER Phase 5 / PROFILE | [P5-KITCHEN-01](#p5-kitchen-01) | HOT eligibility与station/egg语义需全链纸张验证；保旧路由直到通过。 |
| HC-023 | GRAB station 白名单与菜类排序 | DEFER Phase 5 / PROFILE | [P5-PRINT-02](#p5-print-02) | GRAB filter/sort需新station和旧纸张基准；不误称外卖平台。 |
| HC-024 | Receipt 按名字/size 文本识别、隐藏 egg/side | DEFER Phase 5 / PROFILE | [P5-PRINT-02](#p5-print-02) | receipt文本识别与隐藏children逐case迁移；历史job原payload不动。 |
| HC-025 | 中英夹杂小票标题/税标签/固定排版 | DEFER Phase 5 / CONFIG | [P5-PRINT-02](#p5-print-02) | 有限presets/brand/locale/taxlabel依赖稳定P4语义，P3无自由模板。 |
| HC-026 | ESC/POS 80mm/9100/GBK 等默认 | CONFIG / 保留协议默认 | [P7-ANDROID-01](#p7-android-01) | 9100/GBK/width可配默认保留；认证型号/物理结果未证不宣称支持任意设备。 |
| HC-027 | KDS 固定 noodle/hot/pass/shelf 语义 | Phase 3 / MODULE | [P3-UI-04](#p3-ui-04) | 先模块关闭无请求/订阅且配置入口可达；专用KDS屏幕中性化交P5-KITCHEN-01。 **P3实施：** PENDING_UI：现modulegraph消费/disabled零API和WS待整合；固定noodle/hot/pass/shelf与完整厨房DTO留P5，未称全部KDS角色可运营。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-028 | Chinatown clone 硬定 source Store 1 | Phase 3 / PROFILE | [P3-PROFILE-02](#p3-profile-02) | Store1clone只保已授权legacy兼容，不作跨商家默认；Neutral不触发。 **P3实施：** PENDING_NEUTRAL：新空白入口不得调用Store1clone；保留授权PRIVATE legacycompat；创建/foreign/replay测试待提交。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-029 | 新店默认 ST_DENIS profile v2 | Phase 3 / PROFILE | [P3-PROFILE-02](#p3-profile-02) | 新空白商家通过明确Neutral入口，Owner同品牌Master materialization保持PRIVATE legacycompat；不重命名现code。 **P3实施：** PENDING_NEUTRAL：显式NEUTRAL_RESTAURANT@v1与V40/Owner原子创建待整合；ST_DENIS_CANONICAL_PROFILE@v2 code/fingerprint保留；完整导入/向导P6。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-030 | 默认两张桌与两个打印角色 | Phase 3 / CONFIG | [P3-PROFILE-02](#p3-profile-02) | 空白/无堂食不继承两桌/现打印角色；仍保持一步create与硬件可选。 **P3实施：** PENDING_NEUTRAL：空白Store零菜单/桌台/新staff/assignment/endpoint需counts证明；可选硬件不阻管理；legacy默认不批量删除。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-031 | 前台拆桌只认识 A/B 与左/右 | DEFER Phase 5 / CONFIG | [P5-TABLE-03](#p5-table-03) | 首发只整桌/可选AB，语义版本化后验收，现活跃桌号不改。 |
| HC-032 | 中文/英文双字段与固定 UI 文案 | Phase 3 / CONFIG | [P3-UI-04](#p3-ui-04) | 统一中性品牌/locale入口且保双字段；完整语言/receipt覆盖交P5/P8，法语缺口上市验收。 **P3实施：** PENDING_UI：safe summary提供brand/locale，双Store/切店/768/1024待验；未实现完整语言/法语/receipt翻译，留P5/P8。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-033 | 离线 key 包含 account/org/store 但无 deployment identity | Phase 2 / CORE安全 | [P2-OFFLINE-06](#p2-offline-06) | deployment namespace、DB4→5旧来源隔离、account/org/store guard与稳定payload。  **P2实施：** PASS_LOCAL：origin/完整scope与DB5旧记录原样隔离；A6-OFF-02以当前credentialassociation修复，独立复审与最终集成通过；同origin重分配限制保留。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-034 | Android SharedPreferences 保存 device token 与可编辑 URL | Phase 2 / CORE安全 | [P2-OFFLINE-06](#p2-offline-06) | Android切服停止worker/绑定tokenorigin/可信bridge；签名安全存储加强交P7但不得跨origin泄露。  **P2实施：** PASS_LOCAL：nativeorigin/可信bridge/新secret/no-redirect/manualbusy/generation已验证；A6-OFF-01独立关闭，本地debugpackaging与guard开启native24通过；实装/旧明文配对/physical仍未知。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-035 | Android package/版本分发与 native bridge | DEFER Phase 7 / MODULE | [P7-ANDROID-01](#p7-android-01) | Q15统一package签名与分发矩阵；P2修环境隔离，未读安装包保持UNKNOWN。 |
| HC-036 | WebSocket topic 名称含固定工作流 | CORE / 保留MODULE事件契约 | [P2-WS-03](#p2-ws-03) | 固定topic/aftercommit保持，先ACL；多实例broker无现依赖，延期需求证实后另立项。  **P2实施：** CORE保留 + PASS_LOCAL：七topic与aftercommit未改，持续ACL/queuedframe拒绝及最终回归通过；不建分布式broker。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-037 | Flyway V5 全库 SKU 修正 | DEFER Phase 7 / PROFILE迁移历史 | [P7-DEPLOY-02](#p7-deploy-02) | V5不可改；P3空库/Neutral隔离保证不物化旧seed；分发安装历史另设计，不批改生产。 |
| HC-038 | DB 租户约束保护不均匀、无 RLS | Phase 2 / CORE安全 | [P2-AUTH-02](#p2-auth-02) | Q02独立库保留；逐现启用API/resource关系检查与P2-INV/PRINT负向，RLS非首版硬依赖，不宣称全表已自动隔离。  **P2实施：** SCOPED_MITIGATION_PASS_LOCAL：现启用HTTP/WS/Profile/Device/Offline/BOM负向、独立review与最终G2本地通过；不宣称全表自动过滤或RLS，Q02独立库边界保留。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-039 | V15/V19 自动 seed 兰州 Profile/Master | Phase 3 / PROFILE | [P3-PROFILE-02](#p3-profile-02) | V15/V19历史不改；新install legacyseed private不可见/不可误选；Neutral无经营数据污染。 **P3实施：** PENDING_MIGRATION：V15/V19不改、V38 private保留；V39仅现存Store兼容snapshot，V40 Neutral与fresh/upgrade隔离待验；分发包净化P7。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-040 | Profile/Master 独立 materialization 可复用 | CORE / 保留 | [P3-PROFILE-02](#p3-profile-02) | 复用reservation/fingerprint/独立Store materialization；不造第二provisioning框架。 **P3实施：** PENDING_PROVISIONING：复用现Profile/artifact/reservation/fingerprint与原子事务，独立mutablegraph/retry/失败零写待验；不造第二framework。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-041 | RuntimeDataSeeder 含现餐厅菜单和 LAN 默认 | Phase 3 / PROFILE | [P3-PROFILE-02](#p3-profile-02) | demo/seed与commercial入口分离；P2 startup/CI guard先挡错误配置。 **P3实施：** PENDING_INITIALIZATION：commercial启动guard已保留，Neutral noRuntimeDataSeeder/noLAN/endpoint需新路径测试；旧demo/mock清理仍P7。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-042 | Analytics 固定 noodle SKU/category 分组 | DEFER Phase 5 / PROFILE | [P5-REPORT-05](#p5-report-05) | noodle报告映射保兰州profile，新category须总额守恒；不擅自重建历史汇总。 |
| HC-043 | Uber 全进程 credentials 与 Store mappings | DEFER Phase 5 / MODULE | [P5-INTEGRATION-04](#p5-integration-04) | 独立部署secret/商户许可与mapping受控，默认off；不带sandbox mapping进模板。 |
| HC-044 | Uber Kitchen Mirror、财务汇总与业务时区 | DEFER Phase 5 / MODULE | [P5-INTEGRATION-04](#p5-integration-04) | 保KitchenMirror零本地价/独立财务source，先P4时间金额契约再泛化。 |
| HC-045 | GRAB 不是 Grab 外卖集成 | DEFER Phase 4+ / MODULE | [FUTURE-CONNECTOR-02](#future-connector-02) | Q17已明确GRAB非Grab平台；无真实API/许可，不纳入本轮/首发承诺。 |
| HC-046 | Feature build 常量、全局 backend flags 与 Store modules | Phase 3 / MODULE | [P3-UI-04](#p3-ui-04) | 复用modulegraph，明确ceiling/Store/readiness/UI顺序，不第二feature系统。 **P3实施：** PENDING_UI：沿现FeaturePackage/modulegraph与后端ceiling，disabled零API/WS及管理/readiness分离待验；不建新feature系统。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-047 | 本地默认 schema update / seed / fallback 风险 | Phase 2 / CORE安全 | [P2-FIXTURE-08](#p2-fixture-08) | commercial默认拒错误profile/fallback/seed，安全scanner与启动矩阵；不改现env。  **P2实施：** PASS_LOCAL：explicitcommercial启动、合成loopback/Flyway/no-seed/no-real-I/O、新默认scanner及最终checks通过；不改现env，不声称remoteCI。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-048 | 品牌、Logo、地址/电话配置完整性未知 | Phase 3 / CONFIG | [P3-CONFIG-01](#p3-config-01) | 单一brand元数据/来源骨架，UI投影P3-UI；完整素材/preset和许可验收P5/P8。 **P3实施：** IMPLEMENTED_FOCUSED_ONLY：e565be0九字段typedsummary/strictOwnerbrand-locale写与revision；HTTP/consumer待验，Logo/地址/电话非已支持写字段，素材/完整preset留P5/P8。 [证据](PHASE3_ACCEPTANCE_REPORT.md#本阶段13项-hc-的具体处置)。 |
| HC-049 | 测试偏现有 noodle/打印 fixture，整体覆盖率未知 | Phase 2 / CORE回归 | [P2-FIXTURE-08](#p2-fixture-08) | 两Org负向与库存并发/离线/printing回归；P3扩双Profile；不报未知覆盖率。  **P2实施：** PASS_LOCAL：两Org/PG竞争/打印/幂等/HTTP/真实broker/Offline独立review及最终1013+306/native24通过；双Profile扩展留P3，不报未知覆盖率。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| HC-050 | 疑似无调用 mock/menuImportSeed | DEFER Phase 7 / DELETE候选 | [P7-LEGACY-03](#p7-legacy-03) | 仅疑似无引用，须bundle/import证据；不为本轮文档/安全工作删除。 |
| HC-051 | one-off 部署与验收脚本固定 Store/Printer/endpoint | DEFER Phase 7 / PROFILE工具 | [P7-DEPLOY-02](#p7-deploy-02) | 历史oneoff保留其targetguards，不能当商家工具；P2扫描新默认先防误用。 |
| HC-052 | 备份/恢复/监控未形成商家交付承诺 | DEFER Phase 7 / CONFIG运维 | [P7-DEPLOY-02](#p7-deploy-02) | 备份/恢复/监控/RPO/RTO缺真实证据；先合成演练，不运行线上restore或承诺ready。 |
| HC-053 | 全局 Store Profile catalog 对所有 Owner 返回内容 | Phase 2 / CORE安全 | [P2-PROFILE-04](#p2-profile-04) | private根/版本/artifact全路径scope；公开preset显式安全，歧义owner拒绝。  **P2实施：** PASS_LOCAL：root/version/artifact/catalog/use/clone/replay及Bearer负向、独立review/最终全PG通过；V38唯一明确owner推导，歧义private-unowneddeny，无publishAPI。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |

## DG 逐项关闭/延期追踪（22/22）

原始差异见 [05](05_DOCUMENTATION_GAP_REPORT.md)。这里保留原责任与延期；P2实施注记仅指当前契约/索引已核对的范围，不声称历史全文已修。历史文件保日期与scope，不批量重写。

| DG | 差异 | 主任务 | 关闭路径/延期理由 |
| --- | --- | --- | --- |
| DG-01 | 旧MVP认知与新AGENTS | [P2-BASE-01](#p2-base-01) | 读取fresh治理与实际模块，旧文档保历史，不将旧无auth规则套当前代码。  **P2实施：** CURRENT_REFERENCE_RECONCILED：fresh AGENTS/治理与SYSTEM优先，旧MVP不当现实能力清单。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-02 | 缺失docs/API_bilingual路径 | [P2-BASE-01](#p2-base-01) | Planbook已路由真实doc/API与DatabaseDesign；必要索引同步，不编造bilingual文件或接口。  **P2实施：** ROUTING_RECONCILED：真实doc/API.md与DatabaseDesign入口已链接，缺失bilingual路径不虚构。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-03 | MyBatis/JPA事实 | [P2-AUTH-02](#p2-auth-02) | scope覆盖JPA/JDBC，技术文档更正，不加无效MyBatis租户过滤当安全证据。  **P2实施：** CURRENT_REFERENCE_RECONCILED：DatabaseDesign头明确immutableFlyway/JPA/currentAPI，授权覆盖JPA/JDBC而非假定MyBatis自动隔离。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-04 | X-User-Id认证 | [P2-AUTH-02](#p2-auth-02) | API旧章节统一Bearer优先/开发fallback禁用于commercial。  **P2实施：** CURRENT_CONTRACT_RECONCILED：API/SYSTEM以Bearer为主、显式localfallback为兼容；相关旧API段已改，不批量改历史证据。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-05 | 生命周期非线性 | [P2-GATE-09](#p2-gate-09) | 实际cancelled/ready_for_pickup/served/beverage/dailyclose回归，旧图标历史，不重写状态机。  **P2实施：** FINAL_REGRESSION_PASS_LOCAL：保实际生命周期与既有快照，最终1013全PG/306frontend通过，不按旧线性图改实现。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-06 | 库存API文档超前 | [P2-INV-07](#p2-inv-07) | 将items/restock/prep列PLANNED/NOT_IMPLEMENTED；完整实现FUTURE-INVENTORY-01延期Q19之外。  **P2实施：** CLAIM_CORRECTED：API当前只声明health与已启用BOM；restock/prep/items/transactions明确PLANNED/NOT_IMPLEMENTED，未冒充已实现。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-07 | Android POC/Worker描述过期 | [P2-OFFLINE-06](#p2-offline-06) | 更新受影响Android安全/配置文档为实际claim/lease/Worker；安装认证仍待P7。  **P2实施：** CURRENT_REFERENCE_RECONCILED：SYSTEM/API/Offline报告写实际Worker、DB5/bridge与关闭两P1，旧POC非权威；本地debugpackaging/nativeguard通过，实装/物理认证留P7。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-08 | 旧worktree V7/V8 | [P2-BASE-01](#p2-base-01) | 只标历史与链接当前Manifest，不改写历史证据。  **P2实施：** SOURCE_REFERENCE_RECONCILED：新Manifest分别记B/F/V37，旧handoff保日期/历史身份。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-09 | HTTPS旧STOP | [P7-DEPLOY-02](#p7-deploy-02) | 当前证据按01与CURRENT_STATE引用；公网/TLS/旧IP清理未授权，后续发布前验证，不本轮改线上。 |
| DG-10 | 一个deployed_sha | [P2-BASE-01](#p2-base-01) | backend/frontend/APK/assets/Flyway/config分别记录，bitrepro未知如实标。  **P2实施：** MANIFEST_RECONCILED：source/image/frontend/APK/schema/config分别记录，bitrepro/实装仍未知。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-11 | 新店状态/activation认知 | [P3-PROFILE-02](#p3-profile-02) | 维持Create→LIVE自动内部流程，不新增Owner readiness步骤。 |
| DG-12 | 配置能力重复开发风险 | [P3-READ-03](#p3-read-03) | 复用现Profile/size/Addon，明确通用blank和深度engine尚缺。 |
| DG-13 | 逐菜Addon价格误导 | [P3-READ-03](#p3-read-03) | 契约统一Orgcode/Storeprice/Itemeligibility，history独立；API陈旧段随实现修。  **P2实施：** CURRENT_CONTRACT_RECONCILED：API已维持Org code/Store price-name/Item eligibility；P3统一reader仍是后续任务。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-14 | Org存在≠隔离 | [P2-GATE-09](#p2-gate-09) | 用API/WS/Profile/Print/Offline/BOM双Org负向证据关闭，不能只靠DB-per客户。  **P2实施：** SCOPED_EVIDENCE_RECONCILED：Auth/WS/Profile/Print/Offline/BOM负向与独立review、最终集成通过；不把Org实体或DB-per客户替代授权，也不称全表RLS/SaaS认证。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-15 | GRAB名称 | [P5-INTEGRATION-04](#p5-integration-04) | Q17已定为厨房模块；Grabconnector独立延期，任何文档不得称已集成。 |
| DG-16 | Staging不等Production | [P2-BASE-01](#p2-base-01) | Manifest分别记环境flags/DB/config来源，不激活productionUber。  **P2实施：** SCOPE_RECONCILED：生产与Staging flags/data/credentials分别声明，不以同image作同能力/启用证据。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-17 | 旧CURRENT_STATE授权 | [P2-BASE-01](#p2-base-01) | 主线程只同步当前治理；B历史状态不构成继续线上写入授权。  **P2实施：** AUTHORITY_RECONCILED：只有CURRENT_STATE保存当前授权，B历史reconciliation不授权线上续写。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-18 | dump存在≠可恢复 | [P7-DEPLOY-02](#p7-deploy-02) | 合成restore演练与服务承诺后关闭；本轮不读/恢复真实dump。 |
| DG-19 | 历史测试计数冒充新验收 | [P2-GATE-09](#p2-gate-09) | 本轮按命令/counts/skip记录，Postgres/native实际跑，硬件未知独立标。  **P2实施：** FINAL_EVIDENCE_RECONCILED：逐批保留951skip71/968全PG/1003含1fail/1013含3errors及1fail与修复原因，最终新合成DB1013零fail/error/skip、frontend306/native24及localdebugpackaging通过；硬件未知独立保留。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |
| DG-20 | 税/币/时区普适认知 | [P4-PRICE-04](#p4-price-04) | 首发QC/CAD且policy明确，旧常量保兼容，合规/多税未证不宣称。 |
| DG-21 | mock≠运行菜单 | [P7-LEGACY-03](#p7-legacy-03) | bundle/import证明后才删，P3候选不阻安全。 |
| DG-22 | Owner旧main当商业起点 | [P2-BASE-01](#p2-base-01) | freshrefs+隔离工作树+来源tree核验；原ba169ed与dirty工作保留。  **P2实施：** SOURCE_PROTECTION_RECONCILED：fresh main来源与独立worktree保留，旧Owner main/dirty work未覆盖。 [证据](PHASE2_SECURITY_TEST_REPORT.md)。 |

## 领取/关闭规则

任务卡的依赖是AND，失败依赖不得跳过。P2/P3所有任务以集成报告和验收矩阵为关闭证据；Phase4+延期项须保留原行为、明确业务限制与后来任务，不得标CLOSED。保留CORE也要持续通过其回归。新未知事实先记风险及受影响任务；不将二十项已定方向重新开放。
