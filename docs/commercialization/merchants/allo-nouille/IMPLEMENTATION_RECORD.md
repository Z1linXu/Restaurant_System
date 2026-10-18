# Allô Nouille 本地试点实施记录

2026-10-18。Owner在本轮明确授权：必要通用Core增量、集成测试、独立Mac本地Demo，连续交付至可亲自操作。本记录取代旧规划中的待回答/待批准假设，仅适用于本地商家试点；不批准正式P4/P5/P6、main合并、Staging/Production或实体打印。

## 范围与任务清单

- [x] Fresh origin/main `06bac51ddc0a829a9aa1106f07ef886572efbb65`；新worktree `allo-nouille-local-pilot/Restaurant_System`，branch `codex/allo-nouille-local-pilot`，base `cb6ff2e91409c3445d5a5e765e68f93599838638`。
- [x] 已有商业worktree及原本地主工作区脏更改保留，不切换/修改；旧Demo独立保留。
- [x] 独立治理审查：最新Owner已授权Package，无绝对禁止该本地pilot的条文；CURRENT_STATE同步精确本地范围，COMMERCIALIZATION_PHASE_3 / G3 PARTIAL / phase4_authorized=false不变。
- [x] Menu：通用配置、32编号商品、15加购、两面型、C5 distinct exact2、组件规格、有限三语。
- [x] Order：固定19.99套餐服务端校验、free0/1、准确组件任务/数量、历史与幂等兼容。
- [x] Frontend：现Make it Combo、中文默认、通用Modifier、后台默认面型编辑。
- [x] Printing：复用GRAB汇总BACK_KITCHEN、MOCK与前台票、不使用兰州缩写。
- [x] 独立DB/Backend/Frontend/origin、PRIVATE Profile、真实API初始化与受控HTTP/PG验收。
- [x] Backend/Frontend/Android/PG回归及独立Reviewer；失败/修复记录。
- [x] 真实运行地址、登录/密码安全获取、日志及精确停止/重启命令；完成后保留服务。

## Owner决定与旧计划收敛

细/粗单选默认细面，后台可改并可增选项；Allô无SIZE，通用/兰州Size保留。免费加面/饭在主菜内0/1，不建领取次数系统，可配置独立追加商品。C5四选二、禁止重复。面套餐A2完整2条；饭套餐单条受控component-only规格，不用0.5 A2。

按PDF价格与B/C各自加购资格，不造配方/库存。饮料DRINK不入后厨，其他用单一BACK_KITCHEN；每次提交一张汇总厨房票，稳定module复用GRAB；中文优先，FR/EN/ZH原文保留。全部价格税前，本地明确TEST税配置沿用现TaxCalculator；上线适用性未核实。不新增TTL/旧价/离线报价系统，复用DB5/Local Draft/Outbox/Idempotent Submit，发现真实安全/金额缺陷才做有界修复。

旧六PR是规划估算，本轮按明确本地范围合并必要增量，不为了完成所有Phase扩大架构。修改模块：menu模型/API/catalog/hash及唯一forward Migration由menu worker协调；frontend由UI worker；printing由printing worker；root拥有Order DTO/service、通用校验集成、PRIVATE Profile与Demo工具/初始化/验收。公共schema/DTO/金额统一契约，不并发改同一文件。每个worker不得revert他人工作。

## 保护边界

无真实凭据/订单/店铺、无真实设备/网络endpoint；只允许MOCK/DISABLED；独立随机本地凭据不入Git。不能清订单、IndexedDB或已有数据库，不能停止旧Demo，不运行Docker清理。保持PRIVATE exactOrg/Store权限、历史订单/PrintJob快照、打印claim/lease、稳定重试key及add-only Update语义。

## 实现与 Migration

应用代码提交：`eb07448c1d0959aad15e5872738b7fcb7ed723cd`。后续交付文档提交不改变应用源码。工作分支 `codex/allo-nouille-local-pilot`，草稿 PR 目标仅为 `codex/commercial-phase3-foundation`；不合并任何分支。

- Menu：`ItemCustomizationConfiguration` / `MenuItemCustomizationService` / Store-scoped GET/PUT customization；选项选择范围、distinct/default、free add-on 最大1、per-item fixed combo；现有 canonical Add-on 与 Combo group/component 唯一来源。无配置商品走原规则。
- Order：`ConfiguredOrderItemService` 服务端检查当前配置、真实价格/资格/工位、C5 exact2、组件0价和总数量；保持现 order-line total option quantity。组件冻结 ID/Station/name，既有 BOM 存在时只消费一次，不增加配方功能。原交易/幂等/add-only 更新边界不变。
- Frontend：中文默认、细/粗无Size、C5复选、0/1加购、固定价 Make it Combo、后台 Options 中的默认/规则编辑；新配置路径归一化数量，冻结 outbox 不改写。Java/TS legacy hash `d0abe163`，配置 fixture `25b96080` 一致。
- Printing：复用 GRAB 聚合已有 BACK_KITCHEN tasks，组件用自身名称/份量；显示规则新增受限 station CSV/title/receipt language，物理路由不变。MOCK banner 使用既有 Store summary，不轮询 Pad bridge。纯饮料初次非兰州 GRAB 空内容不建job，前台票正常；Lanzhou和历史jobs不改。
- PRIVATE Profile：`STORE_CONFIGURATION_PACKAGE` 引用 exact Neutral v1，九 artifact，拒绝 Master reference，旧 materializer 不能误应用。只提供配置归档，无 P6 importer。实际 Profile valid=true，fingerprint `e57a528dfff9f7fc79e2f7ae19728f330d6fb78d818662f64360a46fe057332f`，Owner Org1；外店 GET403。
- `V41__add_generic_menu_customization.sql` 仅新增列：French labels、sellable 默认true、typed customization TEXT、order component snapshots、kitchen component option reference。既有 V1–V40 字节不变。只应用新Demo/专用测试DB，不在旧Demo或远端执行。

菜单来源为 Owner 的4页 PDF（2,134,106 bytes，SHA256 `48df9e215351878dec6a5b754751e00e59aeae2506cf87a41b239b7dd482e130`）。`scripts/local-demo/allo_menu.json` 是逐项核对的数据文件，不是产品内的导入功能。32编号商品的三语原文和价格逐项 HTTP 比对；A5/B8 等源文本语言差异与 F3原字保留，不擅自重译。

## 自动验证

| 验证 | 实际结果与范围 |
| --- | --- |
| Backend full regression | 1098 collected：1027 PASS、71 conditional SKIP、0 failure/error；发生在最终饮料dispatcher有界修复前 |
| Final printing regression | 11 suites / 161 PASS、0 failure/error/skip，含最后7项空GRAB保护；独立Reviewer核Dispatcher31 PASS |
| Profile package | 最终7 PASS（含 Python完整配置包 → Java validator/aggregate fingerprint）；不与full重复相加 |
| Menu focused | 119 PASS；canonical add-on、组合配置、权限/模块拒绝、hash、默认/选择约束 |
| PostgreSQL real integration | full已实际运行 MerchantConfiguration、NeutralBusinessCreation、PRIVATE isolation、InventoryConsumption；追加 StoreAddonPostgresMigration 2 + PrintingDisplayRuleLifecycle 1 全PASS/零skip，均新簇专用测试DB |
| Frontend | full 63 files / 379 PASS；MOCK banner后追加3 files / 12 PASS；最终 tsc + Vite build PASS |
| Frontend lint | 改动新增组件/服务聚焦检查PASS；两个原有diagnostics与base逐行一致，未宣称全仓lint clean |
| Android | 原 bundled DB4 / source DB5 guard先FAIL；仅私有临时验证目录用已有copy/verify工具打包当前DB5后，24 PASS + asset guard PASS。不改tracked bundled assets、不声称APK/真机验收 |
| Local lifecycle guards | 7纯Mock PASS：无效/复用PID、stale PG状态、安全拒绝；实际start复用现有三个服务，无停止 |
| Governance / diff | validator PASS，diff --check PASS；正式G3 PARTIAL、phase4_authorized=false保留 |

71项 full 条件跳过不当作通过；其中2项Add-on和1项DisplayRule已在追加真实PG中执行。其他旧Uber/日终/clone/旧打印迁移环境门槛未在本轮全开。兰州兼容由既有Size/Combo/Add-on/renderer/dispatcher/neutral双路径回归覆盖；旧兰州Demo在线保持，不宣称对生产数据做验收。

## 真实 HTTP / DB / 浏览器

基础验收 `acceptance-cases.json` 119条case、`acceptance-http.json` + foreign文件59个HTTP检查通过，包括：Neutral空白、32商品三语/价格、15加购价格、B1细粗/加购、B1与Rice固定套餐、B4/C5禁套餐、C5漏选/重复拒绝、免费超量拒绝、伪造价格拒绝、q3组件不双乘、饮料无task、非饮料BACK_KITCHEN、每个含后厨内容订单一GRAB+一前台、金额一致、稳定幂等回放、foreign403、REAL/PAD_DIRECT400、无设备/Uber绑定。

初版基础历史断言误选Rice却改B1，Reviewer指出后保留其证据限制。已修正脚本，并独立复用含B1的既有订单54：thin→thick期间完整订单及两张已冻结小票SHA一致，finally恢复thin且GET确认。有效补充证据 `affected-b1-history-evidence.json`，不是以原Rice断言替代证明。

最后dispatcher修复后的真实提交：

- 订单66：E4 $2.99，测试税后 $3.44，厨房任务0、GRAB job0、前台job43 PRINTED。
- 订单67：B1 + A2套餐数量3，小计 $59.97、测试税 $8.98、合计 $68.95；GRAB45 + 前台44 PRINTED，各仅一张；两次稳定请求返回同订单。
- 两个旧失败job（饮料订单41/58）保留且不重写/重试。后端更新前后全部既有jobs完整JSON相同；新提交后再次按旧ID集合比较仍相同。

浏览器本轮真实操作PASS：登录、Owner Home两店、Allô Frontdesk、B1选择A2+免费加面、数量3、提交成功（堂食T1保留）、MOCK中文提示、Print Center打开订单54厨房票；error级console日志为空。保存 `allo-kitchen-preview.jpg`。本轮桌面预览不自动关闭正式G3的768/1024视觉矩阵或G3-08。

## 失败与有界修复

1. 原full出现 revision-lock slice 新依赖缺失与旧 `SELECT *` hash纳入新增nullable列的误差：仅修测试fixture与旧列比较，新增列另外断言；第二次full 0失败。
2. 首轮本地PG socket路径空格/长度、Node路径不符：使用本轮独有短 `/private/tmp/allo_pg_*` socket与现有Node20；保留同簇、不重建/重置，不碰旧Demo。
3. HTTP验收预期将源“虾”误写“烤虾”，改为source canonical；新Neutral无enabled assignment则无job，通过现打印API启用endpoint-free GRAB/front、关闭HOT；legacy job.execution_mode nullable不能冒充MOCK，通过Store mode+零endpoint+transport回归证明。原订单不重打/删除。
4. 生命周期Reviewer指出仅alive/PID文件判断过弱：添加pg_ctl状态/dataDir/port与应用命令身份核对，7 tests与真实复用启动PASS。
5. 浏览器发现纯饮料空厨房票：复用现有预渲染guard作最小dispatcher修复，161 printing tests及真实订单66/67 PASS；可见异常和历史job保护不放宽。
6. 追加PG测试首次命令误用不存在的mvnw，未启动任何测试/DB动作；使用现有 `/opt/homebrew/bin/mvn` 后3项PASS。Android测试只修验证产物来源，不改产品协议。

完整失败及修复日志仅在私有runtime目录；不把secret、tokens、user_credentials或整库dump提交Git。

## 独立审查与隔离

独立Agent6审查核心金额/选项/组件数量/权限/Profile/打印/离线兼容PASS；最终工具复核及饮料空票cluster复核PASS，无剩余P0/P1/P2阻塞。Reviewer未读取密码、修改数据库或停止服务。保留KDS分组组件未验收的限制。

运行目录：`/Users/xuzilin/Library/Application Support/RestaurantSystemCommercialDemo/allo-20261018-d42urcne`（0700）。PG16 `127.0.0.1:55441`、DB `commercial_allo_demo`、app role五项特权均false；Backend `127.0.0.1:8081`，Frontend `127.0.0.1:51026`。独立Origin；应用clean env只读取本轮生成配置。实际0 printers、0 devices、0 Uber mappings；Allô MOCK，Neutral DISABLED；无每日自动关店/外部Uber调用。无需真实凭据或打印网络。

Backend部署源 `eb07448c1d0959aad15e5872738b7fcb7ed723cd`，当前JAR SHA256 `a131c76c87b78564a752c9638af4d0114676bc55d2e1afa8ada76c54e42d24f5`。新后端PID58405、前端PID56817；PG51549。后续PID变化以runtime/status为准，不硬编码为启动条件。前端私有产物 `frontend-dist-ce05c14b` 已包含MOCK banner，后续文档提交不触发重建。

旧 PG55440 / Backend8080 / Frontend51025仍在线HTTP200，原两个worktree HEAD/脏更改不纳入本次提交。没有main merge、远端部署/环境变量/配置修改、真实订单/设备访问、数据库/Outbox清理或Docker清理。

本轮已真实启动并持续运行；访问/登录/主要页面/日志/停止重启见 [LOCAL_DEMO_GUIDE](LOCAL_DEMO_GUIDE.md)。最终本地状态 **ALLO NOUILLE LOCAL DEMO READY — AWAITING OWNER ACCEPTANCE**。正式G3与全局Phase授权不变；完整源码/产物及最终PR标识以交付manifest和最终报告补充。
