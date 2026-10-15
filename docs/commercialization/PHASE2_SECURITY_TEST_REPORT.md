# Phase 2 Security and Test Evidence

证据快照：2026-10-15，最终集成代码观察点 `5ec8abeb7a67760000b29151e7f568a88a8f36ed`。这是本地审查/测试结果汇总，不是第二份当前 Phase 或发布授权；动态状态只读 [CURRENT_STATE](../governance/CURRENT_STATE.yml)，执行入口为 [Planbook](COMMERCIALIZATION_MASTER_PLANBOOK.md)，gate 结论见 [验收报告](PHASE2_ACCEPTANCE_REPORT.md)。

范围为 P2-BASE-01、AUTH-02、WS-03、PROFILE-04、PRINT-05、OFFLINE-06、INV-07、FIXTURE-08 与 GATE-09。**G2-01…09 的隔离本地验收证据已满足；本阶段未关闭 P0=0、新 P1=0。** BASE、root boundaries、Auth/WS、Offline 及最后测试 slice 修复均经独立审查 PASS。本文不授予发布、main merge 或真实环境操作权限；主线程依唯一 CURRENT_STATE 路由后续已授权工作。全程未进行 Production/Staging mutation、真实账户/订单导出、真实设备绑定或物理打印。

## 源码与证据身份

| 单元 | 可追溯来源 / 结果 |
| --- | --- |
| 生产组合来源 | B=`ce35278b3569a4eacfa29efc01cadbf0c409bec6`、F=`8c3a9e356f59cd025beff25f7fb1d5a20594e028`、Flyway V37；见 [Baseline Manifest](PHASE2_BASELINE_MANIFEST.md) 与 [机器清单](phase2-baseline.json) |
| 基线工具/Runbook | `92871df`；`c9f8d0f` 将后端 archive 指令补为同 B 的 `backend deployment`，保留测试 fixture 相对路径；来源 refs、V1–V37 字节/checksum 不变 |
| Profile/Print/Inventory/commercial guard | `0f1b2a4`、`87d005b`；模块证据 `0720bb2`，见 [Store Boundaries](PHASE2_STORE_BOUNDARIES.md)；独立 Agent 6 复审通过 |
| Scanner | `4621cb7` 关闭 quoted JSON key、URL 大小写/非本地 IPv6 与实际 fixture 扫描遗漏；独立运行 2 个文件与 3 个测试方法 PASS |
| Auth/WS | 原分支 `78b59070613f8885e767ce2582a1d21ed27cf264`，本地集成 `3523b47`；审查修复 `9c87944`、`739b708`；[分支实施报告](PHASE2_AUTH_WS_SECURITY.md) 的早期测试数不替代后续集成结果 |
| Offline | 初版 `129f66d` → `c9de597`；修复 `3985eacb7d0ac1c2e0a55cc043a6ca48f3e7d582` → 集成 `1609f0d3f23c81676ca014be86ec5083afcea2c5`；独立复审报告提交 `c824d21` → `c2fa49a`，A6-OFF-01/02 CLOSED。见 [Offline 报告](PHASE2_OFFLINE_ANDROID_SECURITY.md#独立-agent-6-集中复审--3985eac2026-10-15) |
| 最终集成 / 测试修复 | `5ec8abe`；`30c1b52` 补 foreign print list/ack/reprint Bearer 负向；`6e9a370` 仅给 menu JPA slice 提供未使用的 user-only 授权依赖，独立审查 PASS，无产品逻辑变更。最终完整回归见下方账本 |
| 可审查 PR | [#275](https://github.com/Z1linXu/Restaurant_System/pull/275) 规划/基线、[#276](https://github.com/Z1linXu/Restaurant_System/pull/276) root boundaries 为 Draft；Auth/Offline 综合 PR 由主线程后续创建。本表不声称 main merge、CI 全量成功或部署 |

source lineage 与新本地构建可解释，不等于历史生产镜像逐字节复现。历史 Docker base/build 参数/SBOM、已装 APK/签名/asset/硬件仍为 `PRODUCTION_BIT_REPRO_UNVERIFIED`、`APK_UNKNOWN`、`HARDWARE_QA_PENDING`。新增 `LOCAL_DEBUG_PACKAGING_PASS` 只证明本次合成构建可打包，不消除历史或物理未知；详见验收报告中的缺口分级。

## 攻击链 → 修复 → 负向与正向证据

| 边界 / 误用链 | 已执行修复 | 测试与审查范围 |
| --- | --- | --- |
| ADMIN → StoreAccess 全店通行 / 被当 Owner | ADMIN 无隐式商家 capability/scope；平台身份与商家 Owner 分离；完整临时支持 grant 未实现时业务访问默认拒绝 | StoreAccess/HTTP/真实 broker ADMIN 负向；Owner/Manager/Frontdesk 合法本店正向；不声称真实生产账号已盘点 |
| 全局 OWNER → 被降权的 Store membership → 管理/报表越权 | 有效 Store preset 不超过 global 与 membership；不可比较冲突拒绝；Org 继承仅认 exact active Org OWNER；聚合读取再逐店检查管理 capability | mixed-role HTTP、Analytics、OwnerOverview 与 StoreAccess；不构造任意 ACL 引擎 |
| membership 停用 → users.store_id fallback 恢复权限 | 任意状态 membership 存在就不走 legacy fallback；只保留从未建 membership 的普通本店身份兼容；commercial 禁开发 header | inactive/revoked/default/legacy 正负向；Bearer filter 与 workspace 路径；开发 fallback 不作商业认证 |
| Platform path foreign ID + body own Store / POST 携既有 ID / foreign parent | 同时核原资源、body 与 category/station/item/parent-option 实际归属；Store/parent 重归属受限 | 参数化 HTTP 覆盖 station/table/category/item/KDS path/body 与 POST-id，option parent、user、合法菜单写；保留原 Add-on/Combo 保护 |
| Staff all-store / 改外店员工 / reset ADMIN / 全库 audit/rebuild | staff 列表按有效管理 scope；新旧 Store/Org 均授权；平台账号不进入商家 mutation；audit 必须显式 Store；analytics global rebuild 拒绝 | 真实 Bearer HTTP 对列表、foreign move、ADMIN reset/deactivate、null/foreign audit、global rebuild 负向，零业务写 |
| 降权目标隐藏 active foreign grant → Owner A 提升 global role → Org B OWNER 复活 | `requireManageIdentity` 枚举原始 active Store/Org grants；实际 Store/Org 关系与 actor staff capability 校验；Org OWNER grant 要求 exact Org OWNER，包含无 Store 的 Org | `9c87944` 参数化 foreign-org / foreign-store / empty-org，generic save、staff update、status、password 五入口拒绝；Manager 本店普通 membership 正向。初审用现 fixture/真实 guard 复现，复审关闭 |
| controller 已读 User A → 并发搬店/改变 grant/ADMIN role → 锁查询仍见 OSIV 旧实体 | generic service 事务内锁 → `EntityManager.refresh(PESSIMISTIC_WRITE)` → 重新核实际 Store/当前 role/raw grants → 写；Staff 也先 refresh 再授权 | `739b708` 的 `UserMutationRefreshTest` 三边界先令真实 guard 通过，再模拟 refresh 后 moved-store / dormant-org / platform-role，均拒绝且零 save；验证锁/refresh/re-read 顺序。确定性边界测试不是声称实测所有数据库并发调度 |
| WS anonymous / foreign topic / wildcard / client SEND | transport 不授予业务权限；STOMP CONNECT 唯一 Bearer；exact HTTP(S) origins；SUBSCRIBE 精确七 topic、有效 Store role/capability/module；禁止伪造 SEND | 真实本地 broker 的 native WS、SockJS websocket 与强制 XHR；匿名、bad/expired token、wrong origin、foreign/unknown/wildcard/topic-role mismatch 负向及本店接收/重连正向 |
| 订阅后撤 membership / 用户停用 / module 关闭 / token 到期；已入队消息仍发送 | outbound `preSend` 与 executor `beforeHandle` 均重新授权，拒绝时清 session/关闭 transport；frontend 每次连接读取最新 token，logout 停连接 | broker 11 项与 queued-frame 专项：先 preSend 通过，撤权后 beforeHandle 返回 null、identity 清除、transport POLICY_VIOLATION；无线上订阅或真实 Pad QA |
| 任意 Owner → global private Profile/version/artifact / 创建、复制、replay | root visibility/owner 授权继承至 children；catalog 过滤后才加载；create/use/clone 与 replay 前检查；事务执行再检查 | unit、Bearer Profile HTTP list/猜 version/operator 负向与零 child 查询；clone replay 负向、同 Org 正向；不存在 publish API，不假报已测试发布 |
| V19 全 Org 复制相同 master_key → 错猜兰州 owner | V38 只认 Store 的 exact profile/version/fingerprint 且一个 distinct Org；歧义/无引用保持 PRIVATE-unowned deny | PG16 V37 upgrade、fresh DB、ambiguous/unreferenced、FK/check、migration replay；version/artifact JSON/fingerprint 不变。没有写死 Org1，也不自动解锁真实旧数据 |
| device token 有效但 Store 已移 Org/失活；foreign printer/job lifecycle | 认证核当前 active Store/Org 关系后才 touch；existing assignment/payload/七 Pad lifecycle Store 校验保留；constant-time token hash 比较 | moved/inactive/revoked 与 foreign pending/claim/start/payload/complete/fail/release、foreign assignment 负向；无真实 endpoint/socket/打印 |
| 每 BOM read-modify-save → 同原料重复 / 并发 lost update / foreign stock | item+option demand 按 inventory identity 聚合，顺序同店悲观锁，全量检查后写余额/ledger，MANDATORY 加入订单事务；submit 用既有 locked-refresh Order reader | 本地 PG16 两消费者竞争 stock 5/demand 3，仅一成功，余额2/ledger-3；foreign second ingredient零部分写、后续失败回滚、事务要求；unit重复 submit只扣一次；原 add-only batch 幂等保留 |
| 默认 local seed/header/update 或真实 endpoint/secret 进入新模板 | explicit commercial-only startup guard：loopback `commercial_*`、Flyway validate、无 seed/header/REAL/PAD_DIRECT/endpoint/Uber；静态扫描实际 defaults/fixture | CommercialSafetyConfig、scanner 反例与 workflow 配置；只读本地等价命令 PASS，不声称远端 CI run 已验 |

## Offline 独立审查 P1 修复与关闭

| ID | 独立复现攻击/误用 | 已整合修复与独立关闭证据 |
| --- | --- | --- |
| A6-OFF-01 | `MainActivity.refreshPendingPrintJobs` 遗漏安全 factory，raw `HttpURLConnection` 自动跟随 A→B 307；双 loopback 复现 B 收到合成 A device token | 实际按钮入口在锁内捕获不可变 `ManualPendingJobsRequest` 的 origin/credential/Store/generation，仅经 `DeviceApiConnection` no-redirect；manual/worker/job busy 互斥，callback 再核 generation/origin/device。独立 loopback 验证 B 零连接、A token仅发A、foreign credential连接前deny、15种busy组合及迟到callback；`3985eac` 修复、`c824d21` 复审 PASS，集成 `1609f0d`/`c2fa49a`，**CLOSED** |
| A6-OFF-02 | 同 origin A snapshot 已落盘、B token已保存而B snapshot失败/崩溃；reader只检查 record 内账户一致性，离线恢复 A 身份。fake IndexedDB 复现 current token user8 / restored snapshot user7 | 以 environment+access/refresh pair SHA-256 association绑定已验证响应，不把 token 明文写 IDB；save/read/apply前检查当前会话，late auth/me/refresh与缺origin失败关闭。独立真实Provider+fakeIDB验证失败窗口不恢复A、B token不被改、A记录保留、正常refresh后同账户可恢复；同修复/复审/集成链，**CLOSED** |

原始独立失败证据、初版 BLOCKED 与后续关闭章节均保留在 [Offline工作项报告](PHASE2_OFFLINE_ANDROID_SECURITY.md)。这是修复后独立复审与完整集成通过的结论，不将作者局部测试等同审查。旧 v2 auth snapshot 缺 association 需正常在线验证，拒绝恢复时不删除 draft/outbox。WebCrypto 不可用时拒绝离线身份恢复。DB4→DB5 原样隔离未知来源记录的行为未改变。

## 测试账本：不要合并不同阶段的计数

| 来源 / 命令或证据 | 实际结果 | 能证明 / 不能证明 |
| --- | --- | --- |
| B 同源 `backend deployment` archive；Java17 `mvn -o clean verify` | 951 tests，0 failure/error，71 optional PG skipped；JAR hash见Baseline | 干净来源构建，不是P2新全量或历史镜像bit复现 |
| F archive `npm ci && npm test && npm run build` | 48 files / 267 tests PASS，TypeScript/Vite PASS | 前端源基线；不是后续Auth/Offline整合结果 |
| root 边界批次 `root-full-postgres-retry.log` | 968 tests，0 failure/error/skip，PG16所有optional显式开启 | 该批次Profile/Print/Inventory/guard完整回归；不能替代随后Auth/Offline最终集成 |
| root 后续 uniform403/negative focused | 16 tests PASS；早一次旧断言400失败，`87d005b`改为统一403后通过 | 聚焦变更通过，不能与968简单相加为新总数 |
| Auth 分支完整默认 suite | 984 total / 913 executed PASS / 71 optional PG skipped；真实broker11、HTTP19包含其中 | 该分支安全边界；PG skipped明确不是PASS |
| 初次集成 `phase2-integrated-backend.log` | **1003 tests，1 failure，0 error/skip，BUILD FAILURE** | 失败在Uber PG合成 membership漏`organization_id`；`9c87944`补同源fixture关系，未放宽授权。不得把该轮记为全量PASS |
| 随后 Uber PG focused | `UberEatsPostgresIntegrationTest` 57 tests PASS，0 failure/error/skip | 对应fixture修复通过；不是整个1003 suite已重跑成功 |
| `739b708` focused XML | UserMutationRefresh3 + HTTP24 + MenuOrdering39 + queued1 = **67 PASS，0 failure/error/skip** | 已核锁后授权与HTTP/Profile/queued-frame；此 focused 计数不与全量相加 |
| 初版集成 frontend `phase2-integrated-frontend.log` / build | 52 files / **294 tests PASS**，TypeScript/Vite PASS | 早期真实结果；最终结果另列，不与299/306相加 |
| Offline修复独立复审 `3985eac` / `c824d21` | Reviewer 独立执行 frontend5 files/33 tests PASS；Android SDK/JVM6 suites/24 PASS，0 failure/error/skip；diffcheck PASS | 修复边界独立 PASS；作者早期 frontend52/299 为分支结果，最终集成306另列 |
| 最终集成首次重跑，`1609f0d`；`phase2-integrated-final-backend.log` | **1013 tests，0 failure，3 errors，0 skipped，BUILD FAILURE** | 仅 MenuRevisionLockIntegrationTest 的 JPA slice 未提供新 user-only `PlatformResourceAuthorization` bean；`6e9a370` 加未使用的 mock，生产逻辑不变，独立只读复核 PASS |
| 完整重跑 `phase2-integrated-repaired-backend.log` | **1013 tests，1 failure，0 error/skip，BUILD FAILURE** | Uber recovery 合成库保留旧 pending 记录，先占 `due(PageRequest(0,10))` 批次，新测试记录仍 ACCEPTING；另开全新专用合成库重跑，无产品逻辑放宽或测试断言降级 |
| 最终 `5ec8abe`，`phase2-fresh-integrated-backend.log`；Java17 clean verify，全 optional PG 开启 | **1013 tests，0 failure，0 error，0 skipped，BUILD SUCCESS** | 全新隔离 PG16 合成目标上的最终后端结果；本报告复核日志总计及成功状态；不与早批次相加 |
| 最终 frontend，`phase2-integrated-final-frontend.log` + `phase2-integrated-final-front-build.log` | **53 files / 306 tests PASS**；TypeScript/Vite build PASS | source `1609f0d` 与 `5ec8abe` frontend tree 相同，tree=`54f5a93a97951e2cd63a06fb60fbf894e57b00cd`；为最终产品树证据 |
| 本地 Android packaging smoke；既有 `build-bundled-apk` 流程；`phase2-bundled-debug-build.log` | **54 asset files verified / DB5 / debug APK PASS**；SHA-256=`fad05caed96762ce60bb3cd8cab9b0449219430bfb9d97c831f154a8db801899` | source `1609f0d` 与 `5ec8abe` restaurant-pad-app tree相同，tree=`1446cdc81f035e78cfd3ba744d88335a1f822fe4`；只在临时打包工作树生成本次assets/APK，未把debug包等同实装或发行认证 |
| 已打包资产 guard 开启的 native tests，`phase2-packaged-native-tests.log` | **6 suites / 24 PASS，0 failure/error/skip**；Gradle BUILD SUCCESS | 不再仅依赖早期 `-x verifyBundledFrontend` 源码测试；仍无物理Android provider/安装/打印证明 |
| lint | 原始独立基线6 errors/6 warnings；集成后5/6，剩余errors均在未改文件；相关changed-core lint通过 | `NON_BLOCKING_EXISTING_DEBT`，不声称全库lint清洁，也不以此扩修无关文件 |
| source/static/doc checks | baseline guard、scanner2files/3methods、governance validator、diffcheck PASS | 新workflow定义/本地命令通过；不等于remote CI/部署已完成 |

工具版本与构建hash以 Baseline/各模块报告为准。PG16 是本机 `127.0.0.1:55439` 的专用合成 `commercial_*` 集群；随机 DB helper 只清理自己创建的库。最终持久型 Uber/clone/rules tests 使用新建的 `commercial_uber_p2_final`、`commercial_clone_p2_final`、`commercial_rules_p2_final`，避免旧合成恢复记录占据 due 批次；其余 optional PG 目标同样由主线程显式设置到该集群，未使用真实库或生产备份。原始日志位于 `/private/tmp/restaurant-commercial-baseline/`，该目录为本机复核辅助；本报告保留命令、count、失败原因、源码身份和审查结论，不能仅以临时目录作为长期唯一证据。

## 审查与残余边界

独立 Agent6 已关闭 BASE Runbook fixture遗漏、scanner格式/范围遗漏、dormant grant复活、OSIV User锁后授权；root boundaries与Auth/WS PASS。另一独立 Reviewer 已关闭 Offline A6-OFF-01/02；menu JPA slice 的 `6e9a370` 机械修复也经只读复核。Reviewer未实施所审业务修复。最终集成回归 PASS，未关闭本阶段 P0=0、新 P1=0；P2-GATE-09 的本地证据满足。

纯 `HOT_KITCHEN` 页面仍依赖无该角色许可的通用 `GET /orders/{id}` 补全 OrderResponse，完整厨房-only DTO与页面验收延期 P5-KITCHEN-01；本轮不增加厨房财务权限、不声称全部角色KDS已能运营。现店 Owner合成流程/legacy fixture不代替真实账号盘点。完整支持 grant在P6；旧价TTL/版本授权在P4，当前不猜数值。

历史 bit-repro、实装APK/签名/asset、WebView/Keystore持久化、全部Pad状态、真实纸张/硬件、税/语言、backup/restore服务承诺均未由本轮证明。本次新生成 DB5 assets/debug APK 的打包验证已通过，不能据此推断现装包或真实升级结果。Profile歧义owner保持拒绝；新native秘密采用Keystore，不声称原有明文旧配对已全量加密或已撤销。

## Rollback / 恢复限制

仅本地reviewed分支与合成库可回退/重建；不授权线上操作。安全回滚必须选择保留scope/私有Profile/WS/设备/库存隔离的兼容修复，必要时禁受影响能力，不能恢复ADMIN通行、匿名topic或旧native raw请求。

V38为additive：保留新增列/权限deny，使用forward fix，不改V1–V37、不删经营记录、不覆盖历史snapshot/job。库存回滚不重放扣减或重建真实ledger；print结果不确定不盲重打。DB4→DB5升级原样隔离旧来源未知records，不能按当前origin认领、删除或回放；直接降旧DB4前端/APK不受支持。保留DB5/namespace/credential-association/no-redirect的兼容修复包，真实legacy恢复先取得原服务器和账户/Org/Store来源证据，再另行授权。
