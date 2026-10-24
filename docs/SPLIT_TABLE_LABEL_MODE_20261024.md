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


## 不可变发布批次

PR #299 reviewed commit `509d341a35235649cc2dd560d139875706b294da`；应用merge SHA
`f838e48de1f20689253716bd1910b952fcbc00d3`。fresh fetch/ancestry/authority refresh完成，
backend/frontend/deployment树与已审commit相同。服务器clean exact-SHA release已建立。
Stage于04:50:40 UTC开始有界双镜像构建；验收成功后按Owner预先授权将同一镜像对直接
提升Production，不重建。两环境各自fresh pg_dump/可读TOC，原双镜像及完整model回滚；
V39为唯一迁移，应用回滚保留V39新增列。acceptance与postrelease必须记录实际artifact。
本机隔离PostgreSQL与旧JAR测试进程均已停止，不遗留业务worker或监听进程。

Stage于04:55:35 UTC部署完成，04:56:01 UTC验收一次PASS，无发布/工具返工。
B=`sha256:ced502b10fc6d3a61eb57b55733ee24d45665ce16350c15a7caf1de2339f0b2e`；
F=`sha256:e2fee1acee5a4b9dfb137090b0be5aa8e4a361cfc6e51b6527dcd91c85593bf0`。
实际JAR SHA256 `aca424461dff0bec7185965373b36bee5d68576b89780e4a018990ee062209da`。
30旧桌均LEFT_RIGHT，旧桌列/原订单身份及其它配置全等，准确追加V39。
24+48+6用例、单次16mm、完整PAD/TCP字节、公网MOCK/Admin设置asset全部PASS。
Production于04:56:19 UTC开始同镜像对提升，独立fresh备份、当前e44pair回滚，保留V39。


## Production 首次提升与有界工具修复

04:56:19 UTC首次提升，V39正确追加；新双容器启动后保护比较失败，04:57:23 UTC自动
恢复原e44应用对，V39保留。Production health UP；原V38备份、失败目录、rollback/target
均保留，不冒称功能已发布。独立只读复核：旧表字段、39桌默认、原订单context、5类
历史快照及4个受保护容器全等，原PRINTING四项保持。

部署脚本原先逐项比较HostConfig.Binds数组，Docker重建的数组顺序不稳定。独立
network-none、仅create未start的5轮dummy挂载实验复现2种顺序，完整字符串排序相同；
自建容器/目录已清理。首次异常被旧catch隐藏，故精确异常未留存；该已复现缺陷与
前端启动后立即回滚的时序一致，不能将推断写成已捕获异常。

BLOCKING工具修复：仅将Binds按完整字符串排序，重复目标拒绝；失败记录仅阶段/类型/
scriptSHA，不记录配置值；严格production retry-1子目录，保留原失败证据，fresh V39
备份，原V38→V39逐行验证且本次账本不得变化，旧数据/配置/受保护容器仍全等。
工具SHA可独立，但backend/frontend diff必须为空，复用已验收f838镜像，绝不重建。
8项工具保护测试PASS；无需另建部署生命周期，其他改进延期。

Agent 6定向工具修复审查ACCEPT，无P0/P1/P2；独立8 tests/diffcheck PASS，
backend/frontend相对f838为空。原应用Agent6审查不重跑，使用原Stage工件。


## Production 成功提升与验收

工具PR #300 reviewed commit `06c7d876eddf19c408d54de6b1a70335b8a4c5a3`，merge/tool SHA
`31466c59e8330127ba707ac5d5ccb67cbf47499b`。fresh fetch/ancestry及三份authority
未改blob复核完成，backend/frontend相对f838空。服务器clean exact工具release，
05:06:19 UTC无重建/无重启重新Stage验收PASS，JAR与原Stage相同。

Production retry-1于05:06:22 UTC开始、05:07:09 UTC提升成功；独立fresh V39 pg_dump
SHA256 `cd9cbad34bce20f40d3e0383b12fb60b0b1238ba67825139eb0de024415a43bb`。
原V38备份和失败目录不覆写；当前V39 ledger逐行不变且仍精确原38+39。canonical与
release metadata指向同一f838镜像对、retry-1目录，无再次build。

验收第一次只读class时被0600检查阻断（scp默认0644），只chmod600后重试，
不涉及应用重启/数据库/打印动作。05:07:39 UTC actual-deployed-JAR验收PASS：
24旧头部、48双模式/双槽/完整桌名、6外卖；三模块和对应UPDATE全部覆盖；原字号、
最左侧完整桌号、inline UPDATE、初始化之后且裁纸之前的单次16mm额外feed、
PAD_DIRECT/TCP完整字节、公共MOCK预览与DiningTables SplitLabel资源一致。

三店TABLE_MANAGEMENT已只读确认可用，39张原桌全部LEFT_RIGHT（13/24/2），
不修改真实mode，不创建真实测试订单，不触发打印/补打/队列重放。真实隔离PG已测
三店INSIDE_OUTSIDE保存/重载/提交/更新/Finish、A/B隔离、活跃订单保护与双向并发锁。
未进行实体纸张输出/测量、真PAD操作、已登录Production UI点击或真实业务写入测试。

## 复测入口

- backend：`mvn -q -Dtest='com.restaurant.system.printing.**.*Test,com.restaurant.system.order.**.*Test,com.restaurant.system.platform.**.*Test,com.restaurant.system.station.**.*Test' test`。
  PG集成使用各测试显式环境变量连接独立本机测试DB；此次均实际执行后已停库。
- frontend：`npm test`、`npm run build`；changed-file lint例外见前述既有错误证据。
- 工具：`python3 -B -m unittest discover -s deployment/cloud/dine-in-headers -p 'test_*.py' -v`。
- 远端exact SHA/class hash及不发job的render证明见两环境acceptance JSON，禁止据此盲目重印。

## Final independent audit and closure

Independent read-only audit at 05:10:02 UTC: PASS. Canonical compose exactly
matches retry target; release.json points to retry-1. Same Stage pair/JAR,
complete environment and Uber worker=true, four protected containers, exact
V39 append and unchanged retry ledger, five history hashes, raw order contexts,
old table fields and business configuration all match. All 39 tables default
LEFT_RIGHT (13/24/2); four old PRINTING jobs remain; pending/due queues are zero;
both public sites are UP. Initial helper never wrote failure.json: the audit
retains actual original baseline/backup/rollback/target evidence. No audit
business writes, credential changes or lifecycle mutations occurred.

### Docker / Disk Hygiene

- Disk Before: 76%, 14GiB free. After: 77%, 14GiB free (14,226,792,448 bytes).
- Build Cache Before/After: 16.82GB, reclaimable 10.45GB; 291 records, 12 active.
- Strict second review: 266 age>168h reclaimable records all excluded for
  UsageCount>0; eligible=0, unparseable=0. Initial 279 difference reflects
  lastUsed/age calculation, not a relaxed predicate.
- Cleanup Performed: no; Reclaimed Space: 0. WARNING / NON_BLOCKING_EXISTING_DEBT.
  Healthy disk<70% and cache<=8GB targets remain unmet, not a new product gate.
- Protected Runtime Artifacts: current Stage/Production pair, e44 rollback pair,
  DB containers/volumes, original V38 and retry V39 backups, private models,
  configuration and evidence retained.
- Staging Health: HTTP200/UP. Production Mutation: only Owner-authorized app
  pair restarts and V39; continuous Uber and existing Store data preserved.
- Own bounded builder, dummy bind containers/directories and render containers
  removed. Local isolated PostgreSQL/Java stopped. Other worktrees untouched.

### PRODUCT REPAIR / TOOLING AND GOVERNANCE REPAIR

Product: backend station/platform/order/printing, frontend shared tableDisplay
and consuming pages, V39, relevant tests/API/SYSTEM_DOCUMENTATION. Per-table
mode, unified display, active-order guard and A/B isolation. Implementation
about 9 minutes, tests about 3 minutes (Estimated; parallel workers).
Unrelated lint, legacy empty drafts and Store3 real printer setup stay out of scope.

BLOCKING tooling reuses deployment/cloud/dine-in-headers deploy.py, verify.py,
test_deploy.py and actual-JAR harness. Initial adaptation covers V39/48 cases;
after rollback, the bind-order guard and strict same-artifact retry required
about 9 minutes of diagnosis/repair/review (Estimated). No new deployment
framework or app rebuild. Final CURRENT_STATE/evidence sync is docs-only with
the risk-based review exception; governance validation/diff check required.

Stop: SPLIT_TABLE_LABEL_MODE_PRODUCTION_COMPLETE. No next Package/Phase opens.
Global Production defaults remain mutation_authorized=false and
authorization.production=NONE. Physical paper/Pad and measurement NOT_TESTED;
protocol/render proof is not an on-site measurement.
