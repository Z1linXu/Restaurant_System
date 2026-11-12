# Allô 独立安全审查

协调者补充（18:25UTC后）：Owner已接受此前QA确认；主Agent实际浏览器核验
TEST1completed、4idle/0occupied和CAD36.17历史不变。Finish现为PASS_OWNER_ASSISTED，
余下2项工具覆盖限制保留。此为后续验收事实，未重写下方独立审查当时的判断；未改变
应用源码或Production运行，文档按风险比例例外无需新增Agent6审查。

最新审查结论：23f3代码、并发证明、最终固定批次/激活与Canonical/Smoke脚本PASS；
剩余Finish/角色UI登录/Library完整Save明确NOT_RUN_TOOL_LIMIT，依原始§8.6/§11.1和
未变依赖图判为非阻断覆盖限制。以下保留各轮审查过程；早期仅工具受限的NO-GO解释
已由末尾重分类段取代。实际Production运行证据由主线程[发布报告](ALLO_PRODUCTION_DEPLOYMENT_REPORT.md)记录，reviewer未SSH或部署。

审查日期：2026-11-12，最终源码与测试报告核对于 08:03 UTC。
Reviewer：`/root/independent_safety_review`，未参与实现。

| 结论 | 状态 |
| --- | --- |
| 本轮候选代码审查 | **PASS** |
| 未关闭 P0 / P1 | **0 / 0** |
| 实际 Pad、Android Worker、物理出纸 | **NOT TESTED** |
| 候选版本完整构建 | **主 Agent 报告 PASS；Reviewer 未执行构建** |
| 合成浏览器 QA、Production 发布与发布后验收 | **待主 Agent 完成；本报告不作运行证明** |

此 PASS 只适用于下述精确代码与已核对测试，不替代 Release 验收，不更改任何
Gate 或授权状态。完整回归曾发现 P1-04，修复后已复核原断言及最终完整回归结果。
实现若再发生实质变化，应复核受影响切片。

## 审查范围与独立性

- Worktree：`/Users/xuzilin/.codex/worktrees/allo-production-improvements/Restaurant_System`。
- Branch：`codex/allo-production-improvements-20261112`。
- 原始基线：`138d0291ad4e72a191c19328bcef10efb9b43677`。
- 最终核心源码 commit：`c2425481ec25257660fa1a73d9ed0ec284eb5fe2`；代码 PASS 绑定此 commit。
  另只读审查本轮未提交的 `scripts/allo-qa/runtime.py`、`five_orders.py`、
  `api_safety.py` 与 `README.md`，只证明其静态执行边界，未执行脚本。
- 已知 Allô 运行基线为 `2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9` 加
  `915e4ff4d8a6a0698c54ae70d2567e0919c50a49a59e48bdd03fbf902321c6e0` 兼容补丁。
  本 Reviewer 未访问目标服务器，未重新认证该运行基线。
- 已读 `AGENTS.md`、治理路由、当前状态、API/数据库相关契约及本任务实现说明。
- 实现、测试、部署配置和 Gate 文件均只读；仅经主 Agent 明确授权新增本报告。
  未启动 Maven/npm，未修改现有测试产物，未连接或修改任何运行环境、订单、价格、
  设备、历史 PrintJob 或 Outbox；未触碰 Lanzhou。

## 初审阻断问题与关闭证据

| 编号 | 初审问题 | 最终核对与结论 |
| --- | --- | --- |
| P1-01 | Bulk apply 收到 500/502/504 时丢弃原 `pendingApply` 和幂等 key；后端已提交但代理响应失败时，再次操作可重复调价。 | `CategoryBulkPriceAdjustmentPanel` 仅对明确请求/权限/冲突拒绝清空请求；未知结果保留原完整请求/key并冻结编辑。新增三项“先 commit、再 500/502/504、重试同 key”测试。**CLOSED**。 |
| P1-02 | 普通模板保存可以创建新 unlinked `ADD_ON`，或把旧 `CUSTOM` 改成独立收费 `ADD_ON`，绕开 catalog 单源。 | 普通 `saveItem` / `saveLibrary` 对新增或重分类 `ADD_ON` 强制同店 canonical assignment。`saveImportedItem` 为内部导入接缝，仅 `AlloMenuImporter` 调用，无 HTTP route。JPA 负向测试验证 leaf/group/item/revision 全部回滚。**CLOSED**。 |
| P1-03 | 旧 MOCK/未知 intent 在首次处理时可按当前 PAD mode 入物理队列；历史模板 MOCK Job 的手动副本未受 receipt artifact 保护。 | `ReceiptPresentation` / `FrozenIntent` 冻结 `execution_mode`，缺失或非 PAD 的旧 intent 在 PAD 队列入口拒绝；模板 Job 纳入 artifact、payload 与原件副本校验，源必须为 PAD Job 且有有效 PAD artifact。新增旧 pending/历史重印负向与新 PAD 原件副本正向测试。**CLOSED**。 |
| P1-04 | `StoreAddonOptionResolver.requiresAssignment` 曾把所有非 `ADD_ON` group 视为例外；该 helper 也接收客户端 snapshot，因此 missing/unlinked option 可伪造 `CUSTOM` / `COMBO_FAKE` 与已有 canonical code，以零价绕过 assignment。 | 已恢复 generic helper 原严格边界。Create/draft-submit 的局部例外要求 snapshot code/group 与真实 leaf 一致，服务再次读取并确认同店同菜、active managed item/leaf、真实 active group kind/binding；只允许 CUSTOM/SPICY/SIZE。原负向断言未改，新增合法 collision 与 foreign/missing binding 拒绝测试。OrderService 31 项与最终完整回归通过。**CLOSED**。 |

非阻断 P2：Bulk 分类选项原先包含 inactive category，虽然后端会安全拒绝，但 UI
容易产生无法保存的选择。最终 `MenuManagementPage` 仅在传入 Pricing 面板时过滤
inactive category，保留其他页面原有分类行为。**CLOSED**。

## 安全边界核对

- **PAD runtime**：必须显式 opt-in、精确 Allô Store code、独立数据库身份、
  `production` environment、关闭 seeders/Test bootstrap；允许集合精确为
  `DISABLED/MOCK/PAD_DIRECT`，不能包含 `REAL`。普通 `allo-test` 缺省仍为
  endpoint-free MOCK。新增 QA 身份仅接受精确 `allo-test+allo-qa` profiles 与
  `127.0.0.1:26512`，不能替代 Production 的 `db` 身份。
- **设备与状态**：Store/Organization、device token、原有 HMAC 来源证明保持；
  pending/claim/start/payload 复核当前 mode；已拥有的 complete/fail 可在关闭后回报。
  `PRINTING` 不在可重领查询内；未增加盲目重印路径。
- **原件保护**：text/bytes/hash、encoding/codepage/paper/font 保留来源；MOCK 或未知
  物理来源不能升级为 PAD；不兼容设备拒绝重新编码。已存在 source-key Job 不重开、
  不重新附加内容；历史 context 不按当前语言、分类规则或 mode 重解释为物理意图。
- **Add-on linked 路径**：当前中英文名称/价格/Store availability 取 canonical catalog，item
  eligibility 独立保留，French 保留 item-owned 值。旧草稿价格失配拒绝，提交历史快照
  不更新。受控 reconcile 采用 Store 锁、revision/fingerprint 和冲突整批拒绝；无变更
  replay 不增加 revision，也不覆盖 Owner 值。无变更 import 不执行重新物化。
  generic unlinked/snapshot 路径保持严格 canonical assignment 检查；独立模板语义只采用
  上表 P1-04 修复后的 server-owned 局部例外，不信任客户端 group 取得豁免。
- **分类调价**：显式 Store capability；完整分类/菜品 Store 校验；同一菜单锁下核对
  revision、request hash 和 fingerprint；幂等已提交 replay 先于过期检查。BigDecimal
  精度、负价、免费 refill、明确固定 offer/Combo item type 排除均由后端执行。
  价格、一次 revision、strict audit 与 ledger 同事务，失败全部回滚，不改 option delta、
  历史订单、receipt 或 PrintJob。
- **新 Bulk 角色边界**：capabilities/preview/apply 复用
  `requireManagerOrOwnerForStore(ADMIN_MENU_MANAGE)` 加现有 module gate，允许既有
  OWNER/ADMIN/MANAGER 管理身份，不因 FRONTDESK 原有菜单 capability 而开放新的整批
  金额操作。原 Registry、Addon 和单品操作权限保持。既有 FRONTDESK capability 是
  原产品规则，本报告未将它归类为漏洞；真实 HTTP 角色验收仍待 QA。
- **前端隔离**：Pricing 能力按 Store 从后端读取；模板面型 `SIZE` 不冒充标准
  Small/Regular/Large Size。未更换 Lanzhou Size Engine 或增加全局 bulk 默认。
- **分类厨房打印与编号**：复用 ReceiptSettings JSON，事件冻结
  `kitchen_excluded_item_ids`，仅 GRAB 过滤，Frontdesk 保留全部售卖行；旧事件缺字段保持
  兼容。菜单编号只从批准的 32 个 `AN_A1..AN_E3` SKU 范围派生，不改历史存储。
- **V42**：纯新增 column/tables/index，capability 默认 false；无旧 migration 修改，
  不自动启用 Store 或调整任何价格。旧应用实际回滚启动仍属于 Release 验证。
- **本机 QA harness**：固定私有 marker、独立随机凭据、精确新 empty DB/loopback
  端口与 backend 环境白名单；不连接远程。`api_safety.py` 临时使用 loopback sentinel，
  只 accept/close，绝不 recv/send/forward；HTTP completion 明确为 `NO_PRINT` 模拟，
  finally 恢复 MOCK/原逻辑 assignments/空 endpoint 并撤销合成设备。sentinel 线程
  join 后复核最终计数 0，未把模拟 PRINTED 当实体输出。脚本尚未执行，不能登记运行 PASS。

## 已核对的验证

Reviewer 直接读取了现有 `backend/target/surefire-reports/TEST-*.xml`，未重跑或改写它们。

| 证据 | 结果 | 证明范围 |
| --- | --- | --- |
| Printing/runtime/receipt/分类路由/编号/CORS 等 16 类最新 Surefire XML | **227 tests，0 failures / 0 errors / 0 skips** | 包括旧 receipt/template pending 切 PAD 拒绝、历史 MOCK 模板重印拒绝、新 PAD artifact/原件副本/source-key 正向、精确 QA identity。 |
| Add-on/模板/Allô 导入、selection 与 OrderService 7 类 Surefire XML | **123 tests，0 failures / 0 errors / 0 skips** | 包括 canonical 单源、旧草稿、Owner 值、Eligibility、内部 importer、新增普通保存负向回滚与 P1-04 的原负向及合法例外回归。 |
| Bulk service/integration/controller 与原 Pricing Policy 4 类 Surefire XML | **42 tests，0 failures / 0 errors / 0 skips** | 锁与并发、原子 audit/ledger 回滚、Store 隔离、精度、replay、refill/fixed offer/history invariants、controller 管理权限调用和 malformed amount；H2 执行 V42。 |
| Bulk 前端 focused 结果 | **18 tests PASS（执行 Agent 报告）** | Reviewer 已直接读源码与 500/502/504 的测试断言；未独立重跑前端。 |
| `git diff --check` | **PASS** | 最终审查时工作区 whitespace 检查。 |
| 修复后完整 Backend：190 个 Surefire XML | **1507 tests，0 failures / 0 errors / 117 skipped** | Reviewer 直接汇总。117 项全部因对应 `*_POSTGRES_URL` 环境变量缺失而显式跳过，不能算作运行 PASS；受影响 PostgreSQL V42 由新本机 QA 另行证明。 |
| 完整 Frontend / 构建 | **481 tests PASS；build PASS（主 Agent 报告）** | Reviewer 未自行运行。兼容 lock 更新后的 production dependency audit 为 0，完整依赖剩 7 项 moderate ESLint 开发链提示，无 high/critical；该 scanner 结果来自主 Agent，不作为硬件或 Production 运行证明。 |

完整 Backend/Frontend 构建、实际 PostgreSQL V42、合成 QA 浏览器及发布后证据，
以主 Agent 后续精确 Release 记录为准；不能由上述局部结果推定。

## Release 尚待完成的动作

1. 按精确核心 commit 固定不可变产物并完成隔离合成 QA，包含真实 HTTP 的新 Bulk
   FRONTDESK 拒绝和 Manager/Owner 管理身份、PAD 协议与六单实际 Renderer；不得用真实营业
   订单或真实价格涨跌充当测试。
2. 仅对授权 Allô 独立目标执行 reviewed backup/preflight。现仓库 Compose 仍有
   `SPRING_FLYWAY_TARGET=41`；Release 必须核对新目标为 V42、V1–V41 checksum
   不变及迁移成功，不得在 Lanzhou 环境执行迁移。
3. 发布前只读确认旧 `PENDING/PROCESSING` Outbox 及待处理打印状态，按精确范围记录
   结果；本次不得以改写历史 Job/Outbox 来制造通过。Store 维持 `MOCK`，设备数 0
   是限制证据，不能写为设备验收 PASS。
4. 回滚必须配对旧 artifact 与已保存的旧 env/config：旧应用不能在新
   `production/PAD_DIRECT` policy 环境下直接启动。保留 V42 和现有业务数据，验证旧
   应用按旧配置启动；不得通过删 ledger、降 migration 或旧 dump 覆盖营业数据回退。
5. 部署后再核对精确运行身份、登录/API/WSS、Store 隔离与无实体 transport；本报告
   不声称任何 Staging 或 Production 变更已完成。

实际 Pad pairing、Android Worker、打印机归属/机型、codepage、纸宽和实体出纸
均为 **NOT TESTED**，须在另行授权的真实设备验收中证明。

## Execution Time Breakdown

本子任务未记录完整单调计时，以下为 Estimated，仅含独立审查与报告，不含实现
Agent 的工作或主 Agent 后续构建/部署。

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | Estimated 28–38 min | 只读契约、源码、四项 P1、管理权限与 QA harness 复核；包含等待实现收敛。 |
| Implementation | Not required | 未修改实现；唯一新增文件为本报告。 |
| Tests | Estimated 4 min | 只读测试断言、完整 Surefire 汇总与 skip 原因，未自行执行测试。 |
| Agent 6 / PR / Merge | Not required | 本子任务为独立 review，无 PR/merge。 |
| Staging Deploy | N/A | 未执行。 |
| Staging Acceptance | N/A | 未执行。 |
| Evidence / Governance | Estimated 3 min | 写本报告；未更改 Gate/授权文件。 |
| Total | Estimated 35–45 min | 到本报告最终 Stop；真实硬件及 Release 仍待后续证据。 |

## 补充独立审查：Allô 加单收据金额修复

复核时间：2026-11-12 08:53–08:59 UTC。最终源码为
`e7dde0a6e27adf3be00baa9972fee06cdcc5482c`，是此前 `c242548` 核心版本的有界补充。
Reviewer 只读复核实现、测试、API/System 文档及保存的本机 HTTP 证据；仅按授权新增本节，
未运行共享构建/测试、操作 QA/Production 或修改其他文件。

**代码审查 PASS；未发现新增 P0/P1。新金额的 Backend HTTP 验收 PASS。**
这不关闭尚未完成的 Browser Final Finish / Print Center UI / Release / Hardware 门槛。

### 修复与边界

- 原问题为 batch 独立计税取分与整单应收取分不一致，不是整单存储金额多算。新 Allô
  FRONTDESK update context 在既有 order 行锁/outbox 事务内冻结
  `update_subtotal/update_total`，从未属于本批的 active immutable lines 重建 previous
  subtotal，并分配 rounded whole total 的差额。本例 `5.98 + 0.89 = 6.87`；税标签明确
  标记整单取分。没有修改 OrderService 金额计算、税率、单行分摊、任务、队列或 schema。
- freeze 检查零折扣、精确分、非负 active line amounts、active line 合计与 whole subtotal
  相等，以及 whole total 符合当前固定税规则。取消行过滤与订单/Renderer 一致。该路径
  只支持本次已声明的零折扣、固定税率、add-only 模型，不声称支持历史折扣或旧税率。
- 新 fields 要么同时有效，要么都不存在；部分/非法值或 rendered batch 小计不匹配
  fail closed。两项均缺失严格保留旧计税及标签。事件重放采用冻结 context，不读取 live
  金额重算；已有 Job 原件/bytes 不重新生成或覆盖。其他 Store 与 GRAB 不采用新金额。
- 生产更新入口仍为 `createOrderUpdate` 的锁内事务，金额一致性失败属于提交前业务
  校验；实际渲染和 transport 仍在提交后的既有 dispatch 路径，普通打印失败不会回滚
  已保存订单。未新增硬件依赖或“打印成功才能保存”的门槛。

### 实际核对结果

| 证据 | 结果 |
| --- | --- |
| 新金额 12、OrderService 31、ReceiptContext 14、旧 Allô Renderer 15、Dispatcher 43 项最新 XML | **115 tests，0 failures / 0 errors / 0 skips**；原 legacy batch 断言保留并通过 |
| 修复后完整 191 个 Surefire XML | **1519 tests，0 failures / 0 errors / 117 环境条件 skips**；未将 skip 计为通过 |
| 本机实际 JAR SHA-256 | `9430ee811525bd22bf525d479c864ac86c22d26c45bb93facee8e3f14c02d380` |
| 实际 HTTP Order 9：A1 → E1 | 初单 `5.74`，追加 `3.99 + 0.59 = 4.58`，累计/整单均 `10.32`；旧独立取分原会打印 `4.59` |
| 实际 HTTP Order 10：B1 → B1 → 合格零价续面 | 初单 `19.53`，追加 `16.99 + 2.55 = 19.54`，续加 `0.00`，累计/整单均 `39.07`；覆盖相反取分方向与连续追加 |

Reviewer 直接读取 `qa-update-amounts-1794473877111365000.json`，确认目标仅为
`127.0.0.1:18512` / 新合成 QA 数据库，build/JAR 绑定上述 e7 版本；独立复算两订单
初单加各追加总计，均等于最终整单；逐张核对记录文本 SHA、artifact SHA、冻结 fields
与实际 update 值一致。重复相同 idempotency key 返回原 batch，未新增 Job。
保存的 orders 1–8、items/options、Jobs 1–18 与 Outbox 保护范围的 before/after 全行
hash 均相等，菜单保护项均 unchanged。Order 9/10 由正常 API Finish；旧 Order 8 和
Job 18 的 `6.88` 原文仍保留，没有伪造历史修复。

详细保存证据见 [加单收据金额回归](UPDATE_RECEIPT_AMOUNT_REGRESSION.md)。上述仅为
Backend HTTP / persisted Renderer 证明，不是浏览器 Finish、Print Center 或实体出纸观察。

### P2 / 回滚操作限制

旧 binary 没有新 `update_*` 金额分支，不能让它消费尚未物化 Job 的新金额 intent，
否则可能退回 legacy 独立取分。回滚切换前须停止新写入，并证明新版已经处理完这些
pending/processing intent 且没有 active Job，再恢复旧应用与对应旧 Compose/env。
若条件不能证明，保持安全停止或在新版修复，不能让旧版重新解释新金融 intent；
不得改写或删除历史 Job/Outbox 来制造队列为空。已经保存的 Job 原件/bytes 副本保持。

本次约 6 分钟用于只读复核和补充记录；未执行实现、测试、部署或真实回滚。真实 Pad、
Android Worker、打印机/纸宽/实体出纸仍为 **NOT TESTED**；Production Release GO 仍须
由主 Agent 依据剩余实际验收与发布证据单独给出。

## 最终增量独立审查：菜单选项读取事务

复核时间：2026-11-12 15:40–15:43 UTC。本轮精确应用源码为
`23f3e804713ad633f1d95f10d1b0cfebfcf31edf`；较已审 e7 应用版本，生产代码唯一增量是
`OwnerMenuOptionServiceImpl.getOptions` 的 `@Transactional(readOnly = true)`。
Reviewer 未参与实现，只读核对 diff、调用路径、测试 XML、日志和既有合成 HTTP 证据，
只追加本审查段；未执行共享测试、SSH、运行环境修改或部署。

**本轮 consolidated Agent 6 代码与受影响 Backend 验收审查 PASS，未关闭 P0/P1 为 0/0。**
这不是 Production 激活或剩余浏览器用例 PASS。主 Agent 提供的 PR310 为指向 Allô
delivery base `1f9f112` 的 draft PR，非 main；本审查不证明其已合并。

### 事务增量与实际证明

- Controller 的 Store/module 授权仍在原入口，随后经 Spring service proxy 进入只读
  事务。原 JPA repository 与 canonical JDBC 投影因而复用事务连接，不需要扩大 pool
  或关闭全局 OSIV。Add-on/Remove resolver 的 catalog 结果使用 detached projection，
  `toResponse` 只复制字段；此次未增加读取写入、菜单 revision 或价格变化。
- 原 `reorderOptions` 内部调用 `getOptions` 仍处于既有写事务；该 self-call 不另开
  read-only 事务，不改变 reorder 的保存语义。其他写入口、授权、schema、订单、打印、
  财务冻结及离线/幂等路径均无本轮代码改动，复用此前对应证据，不复跑无关历史矩阵。
- 新 `OwnerMenuOptionReadTransactionTest` 在两连接 pool 下验证两并发只读事务、每次
  单连接借用、canonical 值/法文投影及连接释放。该轻量测试使用 mock repository 与
  `DataSourceTransactionManager`，不单独冒充真实 JPA/OSIV 证明。保存的去注解负向
  日志在 `assertReadOnlyTransaction` 因事务未激活而失败；恢复注解后的 focused 日志为
  **44 tests / 0 failures / 0 errors / 0 skips**。
- Reviewer 直接汇总当前 **192 个 Surefire XML：1520 tests，0 failures / 0 errors /
  117 skips，即 1403 passed**；未将环境条件 skip 计作通过。实际本机 JAR 重新计算的
  SHA-256 为 `d4b9473fce4d7bfaf35c6a5f1ace4e83bc5dc0ab24fe9c04a616c9187154f522`，
  与本次实际 QA 证据一致。
- 直接解析 `qa-menu-option-read-concurrency-23f3e804-1794497982962726000.json`：
  精确 source/JAR 的原合成 QA、pool=4，两轮各 34 个 options GET 全部 200，逐 Item
  完整响应 hash 均等于串行基线；最慢分别为 84.765/48.574 ms，穿插与前后 health
  全部 200，采样最多 4 条 JDBC 连接，无新增连接超时。20 张受保护业务表的完整
  before/after 结构、行数和 hash 相等，原 Order8 保持 preparing。旧复现 thread dump
  SHA 匹配，并独立数得 4 个 resolver/Hikari 等待线程。短时连接采样不是容量认证。

实际 HTTP 证明明确为 **CLI/API_NOT_BROWSER**；正常合成 Owner 登录的 session/audit
为已声明副作用，不混入业务零写断言。详见
[菜单选项并发验收](MENU_OPTION_READ_CONCURRENCY.md)。真实浏览器 Combo 加载/交互及
其他剩余用例由主 Agent 单独登记，不由本 HTTP 结果代替。

### 新批次发布与回滚边界

本次事务修复改变了应用源码和 JAR；新的发布身份必须绑定上述 23f3 源码和 d4b9473
JAR，最终 bundle、镜像 ID、resolved Compose/build SHA 的封存与远端核验仍待主 Agent
完成。旧 e7 的 archive/image ID 不能作为新批次制品证明。部署拓扑、V42 及迁移范围
未变，可复用已审流程和稳定证据，无需扩建第二套发布工具或重做无关验收。

在已获 Owner 授权的有界修复/部署范围内，完成剩余浏览器门槛后还须：

1. 刷新精确 Allô 主机/project、当前源备份及回滚组合；仅更新 backend/frontend，保留
   DB 容器/卷、JWT/TLS 和既有数据。Store1/Org1 始终 MOCK，活动硬件/设备及 printer
   endpoint 为 0；迁移只到 V42，V1–V41 保持。当前订单/outbox/Job 状态须即时核对，
   不能用早晨快照冒充激活前事实。
2. `category_bulk_pricing_enabled` 只对已授权 Store1/Org1/code 身份定向开启，SQL
   必须精确限定并核对唯一目标行及其他 policy 字段；不运行真实价格 batch apply。
3. 新后端 fresh canonical dry-run 须实际返回 13 groups / 94 eligible paid leaves、
   conflicts 为空，再以同次 revision/fingerprint checked apply，不传 confirmed_prices。
   94 是叶子数，不是 menu revision。零写 replay 使用 apply 返回的最新 guards；
   stale 或未知 HTTP 结果先读取核对，不盲目强制重做。94 目标叶允许规范化 FK/语义
   缓存等既定字段变化，名称/法文/code/价格/ID 等保护字段及另外 94 叶须保持；34 个
   base prices、历史订单/明细/Job/outbox 保持。
4. 回滚到原生产 `2b44+915e` 前暂停新订单写入，由新版排空新 `update_*` 财务 intent，
   确认无 active Job。不能把尚未形成 artifact、仍需消费/重试的新金额 intent 交给旧
   renderer。必须配套恢复旧 Compose/env/双镜像并保留 WSS 修复、V42 和当前数据库，
   不还原 dump、不改写/删除/重排历史来制造空队列。无法安全排空则停止或 forward fix；
   暂时旧版期间暂停 Add-on/template 编辑。原回滚组合只是 prepared，非已执行演练。

以上条件未被本报告提前标为完成。Production Release 结论仍由实际浏览器、精确新制品、
即时 preflight、激活与只读 post-deploy smoke 证据决定；真实 Pad/Android/实体出纸继续
**NOT TESTED**。本轮约 4 分钟只读审查与报告更新，没有实现、重测、部署或真实回滚。

## 最终固定批次与一次性激活脚本复核

2026-11-12 15:53–15:57 UTC，Reviewer 仅只读核对主 Agent 保存的最终封存包、staged
manifest、prepare、回滚及恢复证据和 `activate-production-23f3e804.py`，未连接服务器
或执行脚本。**固定批次与修复后激活脚本审查 PASS；未关闭 P0/P1 为 0/0。**
Browser Finish 原生确认框仍未完成，**Release 继续 BLOCKED_BROWSER，不得执行 activate**；
本审查不将 prepare 结果改写为运行切换或浏览器通过。

| 已独立核对的批次标识 | 结果 |
| --- | --- |
| Source | `23f3e804713ad633f1d95f10d1b0cfebfcf31edf` |
| Archive 实算 SHA-256 | `f12e133270db93bb913071c7e60bab4c3c04ad58014a5e37d961c4f93ff04fb1`；manifest 的 59 个 payload 逐项匹配 |
| JAR / Compose | JAR 为上述 `d4b9473f…4f522`；Compose 实算 `6672b3277ab87b39da37d6313d8694b238599de29fb41f3c6635754a7bc94177`，等于当前已审 Compose |
| Backend staged image | `sha256:edd2f53a0c2c64e9218c682015763f958dc87db8fa246447db6987cbcd5f5f9f` |
| Frontend staged image | `sha256:1e5094013596f889b498f9438a17d3ba3dd103fb10815240051f5e8afedbeaf2` |
| 15:48:32 UTC 新备份实算 SHA-256 | `ad7dee0694447c566ecef239c19e453dad605d2b3d1918d0ce6e31a6bcd53eff`；保存的独立 socket-only 恢复验证为85表哈希匹配、约束/索引有效，临时恢复库已移除且未用作 QA |
| 修复后激活脚本 SHA-256 | `3450cb4efc582e5e43cb7d091f1e88e41521b10dad42439ebca34044793adc3f` |
| 定向 capability SQL SHA-256 | `e9b5b41b301bc71991a4942ce2e168f4eec792392b69bedb0ac8f4e8f6f279c8`；与15:55:35 UTC 最新 prepare 结果一致 |

保存的15:47:43 UTC production preflight 仍为原三容器/旧镜像/V41、Store1/Org1
MOCK、0 devices；10 completed orders、无活动 Job/outbox。Reviewer 直接比较其34项
菜单价格和7张受保护业务表的计数/哈希，与此前基线完全相等。该结果只证明其时间点，
脚本 activate 前仍会重新核验，不将准备阶段证据充当未来事实。

### 脚本边界及已关闭发现

- 无参数仅生成 release 目录下 candidate env/prepare 证据；不会替换 live Compose/env、
  recreate 服务或执行 capability UPDATE。唯一激活参数为 `activate`，浏览器门槛由
  主 Agent 在调用前核对，脚本本身没有可替代浏览器证据的自动判定。
- env 从已核对旧 env 仅替换两个 image refs 和 build SHA；resolved backend 环境
  差异限制在明确 allowset 并检查新值，其余配置相等，DB service 完全相等。JWT、
  datasource credentials、TLS/source mounts、WSS 认证及 seeder/Uber 禁用边界保留。
  实际 image ID 在执行前与 staged manifest 对照，manifest 绑定23f3/Allô目标/V42。
- 仅对 backend/frontend 使用 `up --no-deps --force-recreate`，逐个等待健康并检查实际
  image ID；DB container ID 与 StartedAt 必须保持。随后确认 V42 success。没有
  dump restore、DB 容器重建、历史 Job/outbox 重排或自动 old-binary rollback。
- 初审发现 CAP_SQL 的精确身份 SELECT 结果未检查，UPDATE 只有 store_id 限定，
  早期 preflight 后身份/mode 漂移时可能仍继续。该 **P2 release guard 已关闭**：
  最新 DO 在同一事务中对 id1/Org1/code/MOCK 执行 SELECT INTO FOR UPDATE，
  NOT FOUND 即异常；UPDATE 使用锁定的 target_store，并以 ROW_COUNT=1 强制断言。
  激活前最后队列检查还重新核对 Store 身份/MOCK、0 devices/0 endpoint。
- 备份摘要/文件哈希/85表及一小时 freshness 限制均在调用时校验。若浏览器等待导致
  freshness 失败，沿既有备份/prepare 路径刷新后再评估，不能绕过断言。activate
  中途异常可能已经切换部分应用配置/服务，应检查实际状态后有界处理，不能把旧
  prepare 的 runtime_changed=false 当作异常后事实，也不能盲目重复执行。

canonical apply 不在此激活脚本内：后续仍须 fresh API 的13组/94叶/0冲突及同次
revision/fingerprint guards，checked replay 使用最新 guards，不执行价格决策或真实
batch调价。已准备旧 Compose/env/双镜像组合保持不变，真实回滚尚未执行；任何回退到
原生产旧 binary 仍须先停止新写入，由新版排空新财务 intent 且无 active Job，并保留
V42/当前数据库。实际 Pad/Android/物理出纸继续 **NOT TESTED**。

## 最终验收条款复核：工具限制与发布阻断的区别

2026-11-12 16:01–16:07 UTC，主 Agent 重新提供 Owner 原始验收条款：§8.6 明确
“如果浏览器访问受到权限策略限制，不允许绕过；记录明确的 NOT RUN，并继续执行其他
合法测试”；§11.1 要求“Human-Like E2E 无未解决的关键故障”，同时允许普通非阻断
问题记录后继续，禁止伪造测试通过。本节按这些原始条款及 `AGENTS.md` 的
Risk-proportionate proof，重新评估上节将所有剩余浏览器项均视为 Release blocker
的解释。**本节取代前文仅因浏览器工具限制而作出的 BLOCKED_BROWSER 结论；不改写
实际测试记录，不豁免任何已知产品故障。**

**独立结论：剩余 Finish/table-release、Frontdesk 正常浏览器登录和 Library full Save
可登记为 `NOT_RUN_TOOL_LIMIT / NON_BLOCKING_COVERAGE_LIMIT`。在其余已批准的固定
批次、即时 preflight、迁移/数据保护及 post-deploy smoke 要求保持的条件下，这三项
覆盖限制本身不阻止本轮已验证变化部署。不将 Human-Like E2E 全项登记为 PASS。**

### Finish 的 changed dependency graph

Reviewer 对原部署基线 `2b44aa95d6b9f5ead343858b3a4a18c4eda3d0a9` 至本次23f3源码
直接比较，确认以下事实，而不是仅依据“旧功能”推测安全：

- `DineInPage.tsx` 和 `useTableBoard.ts` 整文件未变；`handleFinish` 的确认、调用
  complete API、offline terminal 清理、两次 table refresh 均未变。
- 前端 `completeOrder`、后端 Controller 授权/调用、`OrderServiceImpl.completeOrder`、
  `requireOrderForUpdate` 的锁内刷新、`loadOrderResponse` 及 `publishOrderEvent` 均
  与基线相同。完成路径保留订单事务、任务/饮品终态处理、completed 保存和事件。
  table refresh 仍读取 active board，确认无该桌剩余订单后清除 occupancy。
- 同步 `window.confirm` 在 complete API 调用之前；本次工具停在该确认框，没有
  观察到完成请求失败、状态写错或清桌失败。新菜单读取事务、canonical 保存、加单
  金额冻结和 Renderer/PAD transport 不在该确认步骤或完成调用的新增依赖中。
- 保存的五组/六单实际记录含 Orders2–7 六次 `/complete` HTTP200，逐单读回
  completed；e7 财务回归 Orders9/10 也各有 `/complete` HTTP200、completed 和正确
  金额读回。此前已核对的保护证明保留旧 Orders1–8/Jobs1–18；23f3 并发证据仍保留
  Order8 preparing 和完整 orders 表 hash。Reviewer 未新建请求、Finish Order8 或
  修改任何合成/真实历史。这些是 Backend 完成证明，不能冒充 browser table-release。

因此，此处未决的是受控浏览器无法接受/关闭既有原生确认框、`getJsDialog` 不返回可
操作对象及 native CUA 替代路径被安全机制拒绝的工具覆盖限制。它不构成已证明的
关键产品故障；不得通过未批准机制绕过，也无需为缺失覆盖修改已稳定的 Finish 产品
代码。本判断不承诺任意浏览器/设备下 Finish 已完成端到端认证。

### 另外两项未执行用例的具体风险

- Frontdesk 正常浏览器登录保留 NOT RUN。Login/AuthProvider/authService/apiClient、
  后端 auth 及本次完成入口无基线 diff；正常 Owner 浏览器登录已执行。保存的真实
  HTTP 证据使用正常 Frontdesk 密码登录，POS catalog200、receipt配置及新增 Bulk
  三入口403，既有 Add-on GET200；Owner cross-Store403 也已验证。角色 browser
  navigation/视觉行为没有因此变成 PASS，但没有发现未解决的授权或登录故障。
- Library full Save **确为受影响保存路径的覆盖缺口，不能称为未改功能**。真实浏览器
  已加载 canonical catalog、观察名称/价格 readonly、修改 translation draft并丢弃。
  已审 JPA 集成测试证明 canonical item 保存/陈旧值不能复写当前权威值，library 无
  canonical link 保存失败且回滚；TemplateMenuService 的 library 独立副本/default
  测试及前端 scoped item-save payload 测试提供补充。完整 Library 浏览器 Save 和
  reload 持久化仍未执行，不能以这些测试伪造其 PASS；现有证据也未呈现已知保存
  故障。原始条款允许在本工具限制下保留该非阻断覆盖差距。

实际已发现的一分钱收据错误及连接 pool 饥饿均已修复并分别取得金额与实际并发证明，
本次重分类不撤销任何产品 bug 的修复标准。Print Center、长预览滚动、MOCK原件副本、
C6 takeout、history、assignment、availability及修复后 Combo 加载的浏览器记录仍各按
其实际范围保留，不由本节扩成全功能/全设备 PASS。

本节不新增 Owner 确认门槛，部署授权沿已明确范围执行；也不提前证明 Production 已
部署。精确23f3镜像/包、当前源备份、Store MOCK/零活动硬件、定向 capability、canonical
guards/replay、历史保护，以及旧 binary 回退前新版财务 intent 排空等要求全部保留。
后续若出现真实状态/授权/金额/持久化关键故障，仍须阻断并修复。本轮只读检查及此
报告追加未运行新测试、HTTP、SSH或部署，未修改其他报告/治理文件。

随后只读检查最小私有 `production-api-23f3.py` 与 `production-config-snapshot.py`：
**静态边界 PASS，尚未以此登记实际 Production API 运行结果。** 复用的 Nov8 源码在
`def enc` 前只有 imports、私有 session 读取及 req/WS/denied 定义，没有执行旧订单/
配置业务。新请求明确使用正常新登录 token；唯一允许的业务 POST 为 canonical
reconcile，顺序为 fresh13/94/0conflicts dry-run、该 revision/fingerprint apply、
apply 返回的新 guards replay。snapshot 仅查询，核对历史哈希、94目标叶保护字段/
另外94叶全行、34items、groups/printers，并检查 apply 单 audit、replay 零 audit/配置
变化。smoke 仅正常登录、GET及认证/隔离 WS 握手/订阅/无业务 payload 的拒绝验证；
没有创建订单、batch调价、设备绑定或打印调用。Reviewer 未运行这些 helper，也未新增
部署工具。调用中若发生失败或未知结果，应先核对实际状态，不强制重放 mutation。
