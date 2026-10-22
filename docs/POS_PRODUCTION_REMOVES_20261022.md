# POS 五项修复与三店共享 Remove 发布证据

PRIMARY_REPAIR_GOAL：发布五项修复与三店指定 18 个共享 Remove，原商品有效选项、
价格、启停、Parent、历史、设备和 Uber 行为保持。EXPECTED_REPAIR_CLASS：HIGH。
本文件只记录本次证据，不授予未来生产操作权限。

## 已验证基线与实施

- 开始：2026-10-22 04:32 UTC。fresh main `e6bd1f3ec13df3519f83881ef3fea29209f37fc6`。
- 以独立 `codex/pos-production-removes` worktree 整合 PR #282；合并基线
  `9a6fa04` 同时包含 main Uber 与原 PR 全部代码。保留原 Owner 脏工作区。
- Production 实测 Backend `7d573be03d66a9505e0c68f92fa17829247f56e3`，Frontend
  `8c3a9e356f59cd025beff25f7fb1d5a20594e028`，Flyway V37。
- 实际营业 Store：1 / 4483 R. Saint-Denis / `4483_R_SAINT_DENIS`；2 / CHINATOWN；
  3 / St. Catherine / `ST_CATHERINE`，Organization 1 / Lanzhou Noodles。
- 每店共享主数据 0、旧 Remove 110。三店已发布 MODIFIER_REMOVE 恰好 18 canonical
  对且相同，无草稿；无需新建规则版本。
- Backend 完整 `mvn verify`：1061 total、975 executed、86 conditional skip，0 failure/error。
  其中 real PostgreSQL StoreRemove 7、Baseline 4、printing lifecycle 13 均执行。
- Frontend：321 tests / 55 files、TypeScript/Vite build、受影响 ESLint PASS。
- 独立 Agent6 整批 ACCEPT，无剩余 P0/P1/P2。

## 数据演练与备份

使用只读 Production 330 条 Remove 输入，在独立本地 PG16 同 service dry-run/apply/replay：
每店 0→18 Master、53 links、原 raw/effective/价格/Parent 变化 0，fresh dry-run replay 0/0。
Store 2 原 inactive 406/420 保持 false。报告 `/tmp/pos-prod-remove-dryrun.json` 私密保存。

每店 57 retained legacy rows，对应 54 条诊断：19 missing code、22 outside baseline、
11 special parent、2 ambiguous business-value groups（胡萝卜/黄瓜）；不猜测、不删除/停用。

Production preflight backup：
`/srv/restaurant-pos/production/pos-removes-preflight-20261022T044150Z`，custom dump
11,020,499 bytes，SHA256 `1d6daf9c114746060aa7945bcb729064715ea8a5d75423c40cc47194f818f557`。
`pg_restore --list` 与完整 `--file=/dev/null` 读取成功；另保存三店 menu/options/pricing/rules
及当前私密 Compose。实际发布前部署 helper 还会生成新鲜备份。

保护历史 PRINTING IDs：Production 1176/3169/9226/20391；Staging 200/205/207。
只保留，不 release/requeue/ack/重打。Production 无实际测试营业订单或实体打印授权。

## Docker / Disk Hygiene（发布前）

磁盘 59G、已用 41G、可用 16G、73%；Build Cache 16.82GB / reclaimable 10.45GB。
168h 以上 291 records，严格 immutable/unshared/zero-use eligible 0；279 reclaimable
未满足保守资格，未清理。当前/回滚 app images、DB volume、配置、备份及证据全部保护。

以下记录随后完成的实际 Staging / Production 部署与只读验收。

## 最终集成与并行 Uber 配置

发布前 fresh fetch 发现并整合 `5d2c563` / PR #284（receiver 运维 helper、6 个无网络
unit checks、文档；Backend/Frontend 源码与已测试版本完全相同）。保留其显式授权及配置。
PR #282 于 2026-10-22 04:51:20 UTC merge，最终 Release SHA：
`4b3abd1349e66a146c36ac310b6a8b48bb2ad572`。

04:51 UTC 独立只读复核：Production Backend 仍为 7d573be 同镜像，但 receiver 配置已
于 04:50:13 重建：receiver=true、signing key present、worker=false、KITCHEN_MIRROR。
仅 1 条 HELD_RECEIVE_ONLY 事件，binding、Uber/localUber order、Uber print job 全 0。
新发布复制这份实际 current.compose，不使用先前 receiver=false 的旧备份作为恢复模型。

最终小簇修复另跑 14 PostgreSQL/semantic tests，全部执行通过：generic reconcile 对
缺失 type/group 或 mixed-prefix group 保留 conflict，不触发 V38 CHECK 回滚整店。
Agent6 增量 ACCEPT；干净 exact-SHA server source 的治理校验 PASS。

发布前三店 Printing revision：Store1 v2 / id5；Store2 v6 / id10；Store3 v2 / id6。
三个已发布 18 项字典均原样保留；Store2 NO_SPICY 自定义内容保留。


## Staging 实际部署与验收脚本修正

Staging 于 04:56:59 UTC 部署 APP `4b3abd1` / Flyway V38；Backend image
`sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5`，
Frontend image `sha256:2b0749d39545a4eae784b29315be78aac0b5866dc069fb6c60372ad82ee4387b`。
全库备份 SHA256 `717dccc62f3b11f2280e95dfd7de49a46a34bba3d6c8ec4b4d1eeba2618db624`，
路径 `/srv/restaurant-pos/staging/pos-removes-4b3abd1349e66a146c36ac310b6a8b48bb2ad572`。
原 Production 及两 DB container/fingerprints 保持；历史快照 hash 不变。

Store18 首次补 3 个缺失 master（22→25）、0 legacy links、原 raw/effective 变化 0；
fresh dry-run/apply replay 0/0。原 apply/dry-run/replay 已另存 `.attempt1.json`。
首轮验收脚本错误比较整个 `combo_configuration`，其 `menu_revision` 随基线整理合法
递增，因此误报 AssertionError。DTO/服务确认该字段为共享菜单版本；原子事务报告
及其后 dry-run/无变更 replay 全 PASS。修正只忽略这一个 metadata 字段，仍完整比较
Store、groups/components/item overrides，新增 2 个针对检查 PASS；不修改应用代码，
不重建镜像。修正脚本通过独立 source SHA/clean tree/零 app-source-diff 校验后复用镜像。


## Production 正式结果

2026-10-22 **05:04:20 UTC（Toronto 01:04:20 EDT）** 完成双镜像提升。
应用 SHA（Backend/Frontend 同一源码）：`4b3abd1349e66a146c36ac310b6a8b48bb2ad572`。
PR #282 已合并；验收脚本修复 PR #285 merge 为
`d32fa709f2e895ebc4ebf729e8b546d6fd043df8`，仅脚本/文档，无 app 源码差异，无二次构建。
Backend `sha256:c79b6bd78a2f82a4b95eb15472b5a82820a2c48248d94e334fb983969d3a80c5`、
Frontend `sha256:2b0749d39545a4eae784b29315be78aac0b5866dc069fb6c60372ad82ee4387b` 与 Staging 完全相同。
Flyway V37→V38，旧 ledger 原样，Health UP、公开 Frontend 200。

Fresh Production backup（全 sections 读取成功）：
`/srv/restaurant-pos/production/pos-removes-4b3abd1349e66a146c36ac310b6a8b48bb2ad572/database-before.dump`，
11,021,166 bytes，SHA256 `0c832c7b79ea294c42c25b0fbd89b22d6b96656d9943c14e08570146bd422202`。
同目录保留原/目标/rollback model、三店 scoped menu/rules backup、history cutoff hashes、
逐店 dry-run/apply/replay 完整报告及 acceptance。原私密 model 保留最新 Uber receiver=true。

| Store（Organization 1） | 共享前→后 | 指定18检查 | Legacy关联 | 保留Legacy行 | 适用性变化 | 打印行数/原版本 |
|---|---:|---|---:|---:|---:|---|
| 1 / 4483 R. Saint-Denis | 0→18 | 18/18，Store内唯一 | 53 | 57 | 0 | 18 / v2（id5） |
| 2 / CHINATOWN | 0→18 | 18/18，Store内唯一 | 53 | 57 | 0 | 18 / v6（id10） |
| 3 / St. Catherine | 0→18 | 18/18，Store内唯一 | 53 | 57 | 0 | 18 / v2（id6） |

以下为部署后实际 GET 回查，单元格是本店该共享语义的有效适用商品数。
每个 code 在三店均各 1 个独立 Master；0 表示保持未分配，不扩大原商品选择。

| Semantic Code | 共享中文/打印默认 | Store1 | Store2 | Store3 |
|---|---|---:|---:|---:|
| `cilantro` | 走香 | 8 | 7 | 8 |
| `green_onion` | 走葱 | 7 | 7 | 7 |
| `beef` | 走牛 | 3 | 3 | 3 |
| `radish` | 走萝 | 3 | 3 | 3 |
| `noodle` | 走面 | 4 | 4 | 4 |
| `less_noodle` | 少面 | 4 | 4 | 4 |
| `bok_choy` | 走上海青 | 3 | 3 | 3 |
| `broccoli` | 走西兰 | 5 | 5 | 5 |
| `corn` | 走玉米 | 1 | 1 | 1 |
| `mushroom` | 走菇 | 1 | 1 | 1 |
| `seaweed` | 走海 | 1 | 1 | 1 |
| `carrot` | 走胡 | 0 | 0 | 0 |
| `cucumber` | 走黄瓜 | 0 | 0 | 0 |
| `edamame` | 走毛豆 | 1 | 1 | 1 |
| `peanut` | 走花生 | 4 | 3 | 4 |
| `cabbage` | 走包 | 4 | 4 | 4 |
| `meat` | 走肉 | 0 | 0 | 0 |
| `green_pepper` | 走青椒 | 4 | 4 | 4 |

所有有链接的原 Option 价格为 0；共享 Master 不含收费字段。三店分别独立事务，
`expected_before_fingerprint` 校验、原始字段/当前有效集合精确对照及原子 audit 均通过；
fresh dry-run/apply 幂等重放全部 0 create / 0 link / 0 eligibility change。
18 行 Modifier Remove 自动来自本店 catalog；canonical/prefix alias Preview 多选走香/走葱/
走牛/走肉通过。三店 Validate PASS、published revision 全内容/hash 不变，不写无意义新版本。
Save Draft / Publish 生命周期及冻结 reprint 使用隔离自动化通过证据，不在营业店制造测试订单。

### 安全核对

[Production API evidence](evidence/pos-production-removes-20261022/production-api.json) 记录完整
before/after hashes：菜单 option 除合法新增 FK 及 updated_at 元数据外，商品、定价策略、
Combo groups/components、Printer configs/assignments、Pad pairing、printing modes、
Uber binding/menu/event/order 与 published rules 均相等。历史订单金额/option、Kitchen
名称及 Print Job 冻结文本/payload/revision 的 cutoff hashes 不变。两个 DB container 和
非本环境容器不重建；4 个遗留 Production PRINTING 完整记录不改。
本轮新营业测试订单 0、实体打印 0、设备/Pad 配置修改 0。

并行 Uber 任务的 receiver/key 和后续 Store1 本地 binding 独立于本轮；本轮按实际最新
配置/数据保护，worker 始终 false，不清空新 binding、不导入/回放订单、不回退接收器。

### 页面验收与限制

Staging 真实 Owner 会话：Shared Remove 25 行、Legacy 默认折叠（35 unlinked / 3 conflicts /
11 items）、Printing alias 25 个唯一入口、Generate Preview 实际返回 GRAB/FD/HOT 文本。
桌面 1280px 无横向溢出，原触控按钮保留。截图：
[Shared Removes](evidence/pos-production-removes-20261022/staging-shared-removes.png)、
[Remove print aliases](evidence/pos-production-removes-20261022/staging-remove-print-aliases.png)。
Production 浏览器公开登录页可见、页面正常；没有既有登录会话或私密 Owner 密码文件，
密码登录及受保护页面浏览器验收未完成。已请求 Owner 可选手动登录；没有重置账号、
没有把 Staging 凭证用于 Production。Production 三店 authenticated API 使用既有审阅的
短时 numeric Owner identity 方法，/auth/me、Store Context、Menu、Remove、规则及
Print Center 正常；这不冒充 password-login 验收。

保留的真实 Legacy 问题为胡萝卜/黄瓜的名称歧义、missing code、特殊 Parent 以及本轮
清单外旧项目。原来商品上的选择继续有效；本轮不推断/补齐商品缺少的 Remove。


## 独立上线后回查与诊断口径

[脱敏独立回查](evidence/pos-production-removes-20261022/independent-postrelease.json) ACCEPT。
三店各 18 shared / 53 links / 57 legacy raw / 13 affected items / 15 legacy display groups。
正常 Catalog 的 conflicts 为 **3 组**：missing code（19 条）、carrot（3 条）、cucumber
（2 条）。迁移报告的 54 是 retention entries（包括正常清单外及特殊 Parent），不等于
54 组业务歧义。每个指定 semantic 均 1 Master，DB 实查无 normalized 重复、跨 Store 或收费链接。

独立比较原 330 条 Remove 全字段、全部 1154 条 option 除新增 2 FK 外的全部原字段，
均完全相同（包括原 updated_at）。原 Store2 options406/420=false 且 DENY；三店各一条
atomic audit 记录 18 create / 53 link / 0 eligibility delta。4 旧 PRINTING 全 row 和
历史五组 cutoff hashes 均不变。ERROR/exception mention 0，公开 root/login/health=200/UP。

本轮验收时保护字段 hashes 全部相同。之后并行 receive-only 流量新增第二条 held 事件，
DB时间 2026-10-22 01:05:06.403770（Toronto；UTC 05:05:06），原第一条时间 00:50:38；
两条均 HELD_RECEIVE_ONLY、attempt0。Store1 enabled binding hash 与验收时一致，
Uber/local business order/print仍 0。不声称整个事件表永不变化，也不清除合法后到事件。
本轮没有调用任何 Uber write/probe API。

完整独立 preflight/postrelease、真实330条隔离演练及逐店详细报告保持私密，并已归档到
Production release 0700目录；仓库仅保存已检查无secret/token/payload/IP的脱敏摘要。
本地 run-owned PG16:55438 已停止，其他数据库未触及。

## Docker / Disk Hygiene（完成后）

Disk Before/After：73% / 73%，16G / 16G可用（df取整；已用41–42G）。
Build Cache Before/After：16.82GB / 16.82GB，reclaimable均10.45GB。
Cleanup Performed：无；Reclaimed：0。旧168h记录没有满足保守资格的条目；临时builder已移除，
未 prune共享镜像/cache/volume。保护 current/previous Staging、旧Production双镜像、
rollback Compose、DB containers/volumes、所有备份/配置与证据。两环境 Health PASS。
Production Mutation：本轮授权的 B/F替换、V38及三Store受控18补齐/关联/审计/menu revision；
订单、实体打印、设备、价格、printing revision、Uber worker/映射均不由本轮修改。

补充完整 Uber 回归：独立 run-owned PG16 数据库、Mock remote client，`Uber*Test` **119 tests / 0 skip / 0 failure/error** PASS（11.096s），含73项 PostgreSQL集成及V37 module migration。该临时数据库已drop，专用PG55438再次停止；没有真实Uber请求、订单或Production mutation。

## Execution Time Breakdown

估计值按实际工作窗口归类，存在并行/交叉，不能将各行相加作为总耗时。

| Stage | Time | Notes |
|---|---:|---|
| Investigation / Planning | Estimated 10 min | fresh Git/实际三店、schema、菜单/别名、镜像与备份检查 |
| Implementation | Estimated 11 min | menu/printing并行，复用已有发布与验收能力 |
| Tests | Estimated 3 min | Maven完整、最终Remove14、前端321、Uber119含PG73、脚本单测；与实现交叉 |
| Agent 6 / PR / Merge | Estimated 8 min | 整批/增量接受；整合并行PR284；PR282/285及证据收口 |
| Staging Deploy | Estimated 5 min | 只构建一次B/F，等待Maven/npm和readiness；无应用重建重试 |
| Staging Acceptance | Estimated 6 min | 1次metadata误报；保留初始报告，dryrun/no-op replay及修正脚本复验通过 |
| Production Deploy / Acceptance | Estimated 4 min | 同镜像提升、V38、3个独立Store事务/replay、独立只读回查 |
| Evidence / Governance | Estimated 10 min | 私密归档、脱敏摘要/截图、actual state、文档；与验收交叉 |
| Total | Estimated 43 min | 04:32 UTC开始，约05:15 UTC完成证据收口；最终Owner回复报告实际Stop时间 |

收口 fresh main 另整合 PR #286 / `79f75cf` 的独立 Uber 配置证据（仅文档）。其上线后签名探针已在APP4b/V38验证200/401/400/duplicate/held；Dashboard webhook注册已由该任务只读核实。保留其receiver、Store1binding、candidate-only映射及外部授权阻塞事实，不重测/修改任何Uber业务状态。本轮日志中的第二条held事件对应并行验证。参见[独立Uber证据](governance/UBER_PRODUCTION_RECEIVER_20261022.md)。
