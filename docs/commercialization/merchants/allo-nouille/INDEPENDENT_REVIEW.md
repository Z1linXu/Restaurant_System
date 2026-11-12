# Allô 独立安全审查

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
