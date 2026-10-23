# 堂食小票额外顶部进纸

PRIMARY_REPAIR_GOAL: 所有门店六种堂食小票，在原空白基础上、桌号之前额外进纸至少15mm，
每张只应用一次，同时保持桌号及其他打印行为。
EXPECTED_REPAIR_CLASS: MEDIUM

Owner 于 2026-10-23 明确授权实现、测试、审查、合并并直接部署 Production，无需重复确认。
基线为 fresh main `06b8e57a627fc22dd23bd8e35232b93ceea67e7c`；开始记录时间
`2026-10-23T15:09:27Z`。原脏工作区受到保护，使用独立修复分支。

产品文件：`PrintMarkup` / `PrintTableDisplayFormatter`、两处编码器及共享
`EscPosPaperFeed`；MOCK 预览的 `ReceiptPreview` / `PrintingSettingsPage`；对应回归测试。
既有快照不原地改写，历史补打若增加布局也只作用于新的补打任务。
订单服务、菜单、金额、路由、printer 配置、Android APK 和 schema 不变。

## ESC/POS 实现

堂食渲染保存一次 `[[TOP_FEED_16MM]]` 布局标记。MOCK 显示额外16mm空白，保留现有
预览 padding 和原文本空行。PAD_DIRECT 与 backend TCP 使用同一个命令编码函数：
在 ESC @ 初始化之后、正文之前输出 `GS P 0 254`、`ESC J 160`、`GS P 0 0`。
垂直单位为25.4/254=0.1mm，目标进纸16mm，留下超过15mm的机械步进余量；之后恢复
默认运动单位，正文行距和末尾裁纸保持原值。不在进纸后再次初始化，也不使用反向进纸。
连续重复的前缀只编码一次，标记本身不产生额外 LF。

依据 [Epson GS P](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_cp.html)
和 [Epson ESC J](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/esc_cj.html)。
目标环境实际出纸长度仍需现场尺量；没有进行实体打印时不得把命令证明写成纸面实测。

## 发布边界

复用上一批已审部署/验收工具，更新本批次 image baseline、release 目录和实际 JAR 验收。
SCOPE_EXPANSION_CHECK：MOCK 的16mm预览是用户明确要求，因此本次须构建并提升 backend
和 frontend；复用已有双镜像有界 builder，不建立第二套部署生命周期。
无 migration；保留 Production Uber worker=true、cutover、全部环境配置、旧打印任务及历史
cutoff hash。先 Staging 验收，再提升同一镜像对；发布前 fresh 可读 dump 和原模型回滚。

## 发布前验证

打印测试397项：395 PASS、2项PostgreSQL条件测试跳过、0失败；前端328项测试及构建
PASS；受影响3个前端文件eslint PASS。全量eslint存在6项既有错误，已在同一工具链的
干净基线复现，相关文件均未修改，归类NON_BLOCKING_EXISTING_DEBT。
首次打印回归的3项旧纯文本断言需剥离新增非打印标记，修正测试助手后全套通过。
发布工具2项测试、Python语法、clean tracked export治理校验及diff检查PASS。
本地实际编码器验收24种堂食头部及6种外卖：16mm、一次进纸、完整PAD/TCP字节一致PASS。

Agent 6已合并审查全部产品、测试和发布工具，ACCEPT，无剩余P0/P1/P2；确认新补打仅修改
新任务、原快照保留、双镜像部署和原模型回滚边界。实体进纸长度NOT TESTED。
测试/基线/只读preflight证据见`docs/evidence/dine-in-top-feed-20261023/`。
## 不可变发布批次

应用PR #297，reviewed commit `3752e4cf5455150c3af2325a200fb99c94712d8a`；合并SHA
`e44d7a5fe534a50c675574e589751cce41615cc5`。合并后fresh fetch，默认authority未改变，
backend/frontend/deployment与审查树一致。仅从该SHA的服务器clean checkout构建。
Stage于15:30:36 UTC开始既有1GiB/1CPU有界builder构建backend/frontend，随后只重建
这两个应用容器；实际验收后的同一镜像对用于Production，后者不重新构建。
两环境各自发布前执行fresh custom-format dump且pg_restore --list可读；回滚使用
各自原双应用模型/镜像，不恢复或重建DB。无schema/Flyway迁移。验收包括实际JAR
24个堂食用例、6个外卖用例、PAD/TCP完整字节一致、公开MOCK预览asset与容器字节
一致，以及全量env、V38、历史cutoff及旧PRINTING保护；不产生营业订单/PrintJob。

Stage于15:35:41 UTC部署完成，15:35:55 UTC实际验收PASS（一次完成，无发布返工）。
不可变backend为`sha256:a16b8a2feacd7eed97cc2e3d971c9bd993455d262c85fe955cfdf340807b58a5`；
frontend为`sha256:abe9a86ac6c138143bfcfa6b37523f9213ed5d16692f4c35c904bbb2b7ae505b`。
24个实际JAR堂食用例、6个外卖用例、PAD/TCP完整字节一致及公网预览asset均PASS。
Production批次严格使用此SHA及镜像对，按Owner预先授权直接提升；保留原双镜像回滚。

## Production结果

Production于`2026-10-23T15:37:07Z`（Toronto 11:37）部署完成，15:37:21 UTC验收PASS。
应用SHA为`e44d7a5fe534a50c675574e589751cce41615cc5`，与Stage共享以上双镜像，未重建。
实际JAR SHA256为`202a70d9f9be340cf4fe20f23f0d4978d13340d7e440e6caf8378a25e2479b49`。

| 类型 | 普通 / UPDATE桌号 | 额外单次16mm、原字号、左对齐、PAD/TCP字节 |
| --- | --- | --- |
| GRAB / GRAB_UPDATE | `1` / `1 (UPDATE)` | PASS |
| FRONTDESK_RECEIPT / FRONTDESK_RECEIPT_UPDATE | `1` / `1 (UPDATE)` | PASS |
| HOT_KITCHEN / HOT_KITCHEN_UPDATE | `1` / `1 (UPDATE)` | PASS |

每行均覆盖`1`、`1里`、`1-左`、`1-右`，共24例/环境；另有6例外卖不加进纸的证明。
重复标记不叠加，原空行/padding不删除。后端实际JAR在network-none/read-only/受限
operator容器中验收，未调用数据库、未dispatch打印；前端公网asset与容器文件完全一致。
**实体出纸和尺量：NOT TESTED**。16mm为ESC/POS命令目标，不能冒充实测至少15mm。

部署后两站health PASS；完整环境配置、菜单/设备/打印配置、旧PRINTING、历史五类
cutoff hash、V38均通过发布工具的原值保护。Production Uber worker=true、cutover
保留；Stage worker=false。两环境DB容器不重建，无schema或数据迁移。
各自fresh dump经pg_restore --list可读，原双应用镜像与rollback model保留。

证据：
[Stage部署](evidence/dine-in-top-feed-20261023/staging-deployment.json)、
[Stage验收](evidence/dine-in-top-feed-20261023/staging-acceptance.json)、
[Production部署](evidence/dine-in-top-feed-20261023/production-deployment.json)、
[Production验收](evidence/dine-in-top-feed-20261023/production-acceptance.json)。

## 产品修复与必要工具

PRODUCT REPAIR（约8分钟，Estimated、与测试/审查交叠）：
`PrintMarkup.java`、`PrintTableDisplayFormatter.java`表达单次堂食布局；
`EscPosPaperFeed.java`、`PrintJobServiceImpl.java`、`EscPosTcpPrinterTransport.java`
保证实体payload；`PrintDispatcherServiceImpl.java`为新补打任务幂等增加进纸；
`ReceiptPreview.tsx`、`PrintingSettingsPage.tsx`显示同一留白。对应测试属于直接验证。

TOOLING / GOVERNANCE REPAIR（约6分钟，Estimated、与构建/审查交叠）：复用
`deployment/cloud/dine-in-headers/{deploy.py,verify.py,RenderHeaderAcceptance.java}`
和现有builder/backup/rollback，仅适配本次双镜像及新增进纸验收，属于BLOCKING；
对应2项guard测试/README及CURRENT_STATE、API、SYSTEM_DOCUMENTATION、操作说明/
本记录用于明确批次及证据，无新平台或生命周期。全量lint既有6错误留待其他任务，
不修复无关代码，不改APK，不触发实体订单/打印。

独立发布后只读审计于15:39:05 UTC观察：实际双镜像source、全量环境hash与预检
匹配，两个DB容器ID/启动时间/restart不变；V38 ledger、旧PRINTING全行及五类历史
hash均不变，canonical model/actual runtime一致，fresh dump可读，两站200/UP。
Stage protected中两个Production应用因本轮授权提升改变属预期；Production自身
protected集合零变化。验收没有创建营业订单或PrintJob，release baseline后新job=0。
预检到审计期间Production自然连续Uber events/orders/local各增加2（49/39/39→51/41/41），
已按正常营业流量单独记录，不归因于验收。Agent 6发布后独立ACCEPT/PASS。详见
[独立只读证据](evidence/dine-in-top-feed-20261023/postrelease.json)。

## Docker / Disk Hygiene

Disk Before/After：75%（约15GiB可用）→76%（约14GiB可用）。Build Cache Before/After：
均为16.82GB，reclaimable10.45GB，291 records。独立只读二次审查发现279个旧
reclaimable记录均有非零使用；按现有immutable/unshared/zero-use/>7days规则eligible=0。
Cleanup Performed：无；Reclaimed Space：0；临时builder已移除。
Protected Runtime Artifacts：Stage/Prod当前双镜像、原双镜像回滚、全部DB容器/卷、
private模型、fresh dump及证据保留。Staging Health：200/UP。Production Mutation：
仅Owner授权的backend/frontend镜像替换，DB和配置不变。76%为既有磁盘观察项，
不扩大本次任务为缓存/部署架构调整。

## Execution Time Breakdown

任务开始15:09:27 UTC。以下前半阶段为Estimated且相互交叠，不相加；部署/验收取
实际日志时间。最终Owner报告的Total包含证据PR合并后的实际结束时间。

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 约3分钟 Estimated | 基线、既有打印链及ESC/POS规范 |
| Implementation | 约8分钟 Estimated | 产品代码，保持原正文与外卖 |
| Tests | 约9分钟 Estimated | 397打印/328前端及构建；旧测试助手修正1轮 |
| Agent 6 / PR / Merge | 约10分钟 Estimated | 一次合并审查ACCEPT，PR297 |
| Staging Deploy | 5分05秒 | 15:30:36–15:35:41，有界双镜像构建，无重建返工 |
| Staging Acceptance | 14秒 | 15:35:41–15:35:55，一次PASS |
| Production Deploy / Acceptance | 1分17秒 | 15:36:04–15:37:21，复用同组镜像，一次PASS |
| Evidence / Governance | 约5分钟 Estimated | 独立审计、治理校验、纯证据PR；与审计交叠 |
| Total | 见最终Owner报告 | 从15:09:27 UTC至最终Stop，包含文档PR |

完成后停止当前Package；不开放新Phase/下一Package，不追加运行时变更。
