# 逐桌拆桌命名方式

PRIMARY_REPAIR_GOAL: 全部门店逐桌配置LEFT_RIGHT/INSIDE_OUTSIDE，统一前台、订单和打印显示，
保留A/B身份、活跃订单隔离和既有16mm顶部额外进纸。
EXPECTED_REPAIR_CLASS: HIGH

Owner于2026-10-24明确授权实现、安全Flyway、测试、独立审查、合并及直接Production部署。
开始时间2026-10-24T04:32:51Z（Toronto 00:32）；fresh main为
`6e9fba0a1e04592d0bcf188f941edceb4af43a2a`，使用干净已附着worktree的独立分支，
原Owner脏工作区不动。当前Package由此请求限定，不开放其他Phase。

## 最小方案与边界

- `dining_tables.split_label_mode`：安全新增、NOT NULL、默认LEFT_RIGHT及值约束。
- 现有桌台其他字段、内部A/B和orders.table_no不改，无自动拆分/合并。
- 现有API增加可选模式及显示字段；统一显示逻辑处理所有页面和六种堂食打印。
- 有任何未完成堂食订单时拒绝模式更改；同一桌台行锁协调订单进入与模式更改。
- 新打印保留左侧完整桌号、原LARGE字号和单次额外16mm；外卖和既有冻结任务不改。
- 仅新增用户要求的Split Label选择，不增加用户操作流程或重复配置。
- 历史订单原始身份不改，冻结小票原文不重写；后续显示/补打兼容性在实现和测试中明确。
- 不修改金额、菜单/Combo/Add-on/Remove、路由、设备配置、订单/打印状态机和Uber连续配置。

计划修改DiningTable实体/repository、PlatformAdmin保存、Order显示/入桌协调、统一formatter、
三renderer的显示来源、前端桌台编辑/共享显示及其调用者、直接相关测试和文档。
复用既有受审部署/备份/回滚/验收工具，只有V39及新增显示契约所需适配进入本批次。


## 明确的兼容性选择

LEFT_RIGHT当前Production为`1-左/1-右`，与请求示例省略连字符有差别。已发可选澄清，
无回答时按“不改变当前显示/完全向后兼容”保留旧格式；INSIDE_OUTSIDE为`1里/1外`。
没有在不同页面分别写后缀，调整该决策只需两端统一formatter，不改身份。
已完成订单DTO显示为当前配置投影；不承诺没有存储快照时历史显示标题永远冻结。
原始orders.table_no、id/关联及已冻结PrintJob原文/bytes保持，不挪用其他快照字段。

Fresh只读预检：Production三店共39桌，Store1 13、Chinatown(Store2)24、Store3 2；
现有T12重复代码和Chinatown的独立Single里/外桌均保留。legacy draft rawT3与当前
name2里不一致属既有数据，不在本批次自动修复；模式保护保守覆盖其旧code alias。
两环境V38、双镜像e44、完整env保持，Production worker=true、Stage=false；队列quiet。
磁盘76%/14GiB，缓存16.82GB，严格eligible0，不清理。

SCOPE_EXPANSION_CHECK：已有发布工具可复用，唯一BLOCKING适配为V39 ledger精确追加、
非Flyway配置与旧桌列/原订单身份保护，以及本批次48个split标签实际JAR验收。
旧Production实际JAR已在本机隔离PG先V38、再V39真实启动验证兼容，支持只回滚应用
而保留新增schema；不引入新部署/缓存生命周期，不修复无关lint或历史数据。


## 测试与发布前证据

相关后端561 tests：551 PASS、0failure/error、10既有条件跳过（printing2、order8）；
station15项全部运行通过，其中7项真实本地PostgreSQL迁移/工作流/并发，8项formatter/
context服务验证。打印404项中402 PASS；order78项中70 PASS；platform64 PASS。
初次testCompile缺一个新测试的LocalDateTime import；接着新PG fixture缺menu_items.sort_order，
修正后流程6项PASS；一次重试因命令cwd未应用修正重复了fixture失败，不涉及产品逻辑。
本地实际renderer/PAD/TCP 24旧头部+48双mode拆桌+6外卖PASS，16mm/once/字体/左侧保持。

旧Production实际JAR SHA256 `202a70d9f9be340cf4fe20f23f0d4978d13340d7e440e6caf8378a25e2479b49`
在单独本机DB先V38启动、Flyway追加V39后再次启动，Flyway future migration接受、Hibernate
validate及healthUP，两个测试Java进程已停止。V39 CRC32 `1431202837`，SQL SHA256
`bae5cbe1b38e06be34ab39ec8c69d0ac1ea01910549706b01bb61c3bb3927893`。
这是启动/schema兼容证明，不是已执行Production回滚。

只读证明Production三店TABLE_MANAGEMENT均enabled=true/configuration_status=CONFIGURED，
active/ACTIVE，包括Chinatown。部署只增加默认字段，不代Owner更改任何真实桌的mode。
证据目录：`docs/evidence/split-label-mode-20261024/`。

前端最终354 tests / 61 files及build PASS；23个其他新增/改动文件lint PASS。
OrderDetailPanel原setState-in-effect错误在精确HEAD6e9fba0复现（原140行/当前141行），
属于NON_BLOCKING_EXISTING_DEBT，无新增disable或无关修复。显示配置缓存复用现有
workspaceSnapshots objectStore、24h freshness和account/org/store键，无新schema版本。
用户仅多一个二选一设置，rawcontext/draft/outbox不增加显示身份。

部署保护测试5 PASS；本地actual-class harness24+48+6 PASS。Agent 6合并审查ACCEPT，无剩余P0/P1/P2；独立21前端/5部署用例PASS。

必要边界修正：按完整原始桌名取A/B尾部，含连字符桌名1-左-A/B的Edit/Finish隔离回归通过；不从显示中文反推身份。
