# Allô Nouilles — DELIVERY_TRACKER

PRIMARY_REPAIR_GOAL：按最新两页菜单交付这一家餐厅；复用成熟 POS/Station，完成独立 Test 与 Owner 验收入口。
EXPECTED_REPAIR_CLASS：HIGH。只限首店，不开发第二客户、完整模板或 Control Plane。
首轮开始：2026-11-03 22:17:44 UTC；桌面中断后按最新 Owner 附件恢复：2026-11-04 04:54:11 UTC。

## 当前授权与边界

最新 Owner 指令：`9faee6af-1c26-4c30-a7f6-1f493fdbfd2e/已粘贴的文本.txt`（Allô 最终执行／旧 Test 退休）。它取代此前“保留旧 Template Test”的运行安排。

- 明确批准永久删除归属已核实的旧 Template Test 四个停止容器、专属数据库/Volume、无共享引用的网络及旧运行配置。旧 Test 数据库备份已明确豁免。
- Allô 使用独立 Backend/PostgreSQL/Frontend/Relay、凭据、网络和数据；复用原 Test 域名及 `127.0.0.1:28080` / Relay `28480`，保持共享 Nginx/证书不变。
- 精确最小打印 CR 已明确批准，见 `REQUIRED_CHANGE_REQUEST.md`。仅 Allô 显式 Test/MOCK；没有 HOT、Pad 协议、状态/去重算法、Android、实体打印或旧店发布授权。
- Production 绝对不部署/重启/改数据配置。Staging 不启动或变更。另一线程正在发布 `74dfc1f…`，本任务必须等其完成并稳定，再取得现有运维锁处理 Test；本地工作继续。
- 旧 Test 镜像若无法完整证明无回滚依赖则保留，不执行全局 prune、递归目录删除或恢复旧 Test。

## 来源与工作现场

- 分支：`codex/allo-nouilles-delivery-20261103`。
- 工作区：`/Users/xuzilin/.codex/worktrees/allo-nouilles-delivery-20261103/Restaurant_System`。
- 初始 HEAD：`b6cab1b29c330c3020a009942973e083793d228c`；实际模板应用来源 `dbe207a1cc62256c7173108ccc375499f1e16caf`。新鲜 `origin/main=80e888fcdad74e98326fcbeaa5df14bc0d9baed6`；分支为 behind2/ahead10，main 两个新增提交仅旧店发布证据。未 merge/rebase/reset/clean。
- 原开发目录、Research028 和前轮 planning 工作区均未改动。Allô 未提交改动完整恢复；仅本任务拥有的路径可 stage/commit。
- 客户 PDF 两页，SHA256 `2ebcf2ef38fb58a8080a2078e4f957af590767b48e886a72ae16339904057db8`。
- 私有规范化来源 `menu.source.json`（加入来源联系/品牌元数据后）SHA256 `9a855f6565f426c38957d3004efcf8e944a5e4c3ac472ae27fa061bc9713ab79`，版本 `20261103-v1`；配置/客户原文件/凭据不入 Git。
- Logo 从原 PDF 无损提取，SHA256 `8a91f839aa9e614876fee5d312b06b48e55f00ee7ef36be62490fba52dd0cf94`；仅私有目录及 ignored `frontend/public/merchant/logo.png`，最终 dist 逐文件哈希。主题红色为依据 Logo 的呈现选择，待 Owner 视觉验收。

## 实现与复用

| 范围 | 状态／事实 |
|---|---|
| 菜单 | 32 编号商品＋2 受资格约束续加；5 分类、13 收费 Add-on、3 面型；C6 固定主食四选二，不伪造 Combo 子菜；旧促销套餐不导入 |
| 日常管理 | 沿原 Owner/Manager CRUD、组选项、排序/默认/必选和规范价格；新增可空 FR 名称、明确 distinct min/max；显式 null 清除边界，缺字段兼容旧客户端 |
| 导入 | Store 菜单锁及 source/applied 指纹；校验引用/重复/空定义；重复不复制图；同来源保留 Owner 编辑，双方改动冲突整笔拒绝；分类变动推进 revision |
| 续加 | Owner 明确“面只续面，饭只续饭”；堂食、同类别付费主食资格；零价行不能自证；次数不限、员工核实每人一主食；Submit/Update 服务端验证 |
| Station | 原实际 Station→KitchenTask→ProductionTask；非饮品 BACK_KITCHEN 单张 GRAB；饮品沿 DRINK 直接供应、不创建厨房任务；前台全单 FRONTDESK_RECEIPT；不新增路由引擎 |
| 三语 App | 同一 React 树，zh-CN/en/fr-CA 静态资源；真实业务名称独立字段及回退提示；本店点单/桌台/外带/历史/管理主路径/基础报表/打印主控；无外部翻译调用 |
| 打印语言 | Store 默认/厨房/前台独立于 App；既有 Outbox 同事务冻结 locale、实际快照名、行 ID 和初单金额；必要零价选项可见；保存只影响新事件/批次 |
| 原件重印 | 复制原 Job 文本/上下文/严格 UTF-8 MOCK 证明字节，核对 hash；缺失/损坏失败；有 Update 的 OrderCenter“完整单”明确引导原 PrintCenter 分批原件，不能把初单冒充整单 |
| 保护项 | Legacy null-context 分支保留；金额 helper、Order 状态、source key、Pad claim/lease/token 未替换；新证明字节不允许进入实体/PAD 队列 |
| 配置故障 | 新厨房工位未分配时厨房 Job 明确失败；合法订单仍保存、前台独立成功；纯饮品不会虚报空厨房失败；DISABLED 不阻塞 Submit/Update |
| 浏览器隔离 | 稳定 `allo-nouilles-test` namespace 隔离 Token、IndexedDB 和 manual-reprint intent；空 namespace 保持原键，不删旧 Draft/Outbox，不改重试身份 |

## 实际验证（本候选源码，非旧报告计数）

- Backend：`mvn -q test`，182 suites / 1377 total / 1260 PASS / 117 conditional SKIP / 0 FAIL / 0 ERROR。条件跳过不视为 PG 或设备验收。
- Frontend：78 files / 433 PASS；`tsc -b` PASS。最后收费 Add-on distinct 上限仍可数量2、C6 每项数量1；三语切换保留 draft；重印 namespace 保护旧未知结果请求。
- 原打印相关 focused：178/178 PASS，包括旧 GRAB/HOT/前台 cents/Job/Dispatcher/Pad/manual 行为和 Allô 新渲染。
- 全新隔离本地 PG18：实际成功应用 V1–V41，ddl validate；未使用 Production dump。PG18 有上游 Flyway16支持范围提示；远端部署沿 reviewed PostgreSQL16 固定镜像，远端迁移须独立验证。
- 本地 HTTP 主流程：27/27 PASS。32 价格/FR 名核对，C6 错误语义验证，同幂等身份同订单，87.92 原计价断言、初单/Update、两模块票据、旧 Job 不变、新批次独立语言、同类续加正反例、DISABLED Submit/Update、跨 Store 403、Owner/Manager 登录。
- 本地补充：8/8 PASS。纯饮品一张前台票/零厨房任务；有 Update 的全单重印明确拒绝；原件副本文本/字节/context 相同且同重印 key 不重复；厨房错误不回滚订单、前台成功、警告持久化。
- 导入闭环：8/8 PASS。重复来源保留 Owner 改价/FR 名，冲突来源失败且事务原值保留，恢复原来源再次启动。已恢复本任务测试修改；最终 34 商品/39 provenance rows/测试标记0/MOCK/默认中文。
- 独立业务 Agent6：ACCEPT WITH LIMITATIONS，无剩余所审 P0/P1/P2。覆盖前轮普通4项、打印3项、后续空饮品/font/引用3项及 FE cluster；不宣称全 SaaS 安全。
- 同端口替换部署守卫：99/99 PASS。精确退休 ownership/共享引用/完整 ID/partial failure：15/15 PASS（合计114）；独立运维 Agent6 ACCEPT WITH LIMITATIONS，无剩余所审P0/P1/P2。
- 本地浏览器工具曾多次超时，未据此伪报完整浏览器点击验收；实际 DOM/截图、弱网断线/恢复和公共 HTTPS 仍待对应环境验收。
- 私有证据：`.merchant-private/allo-nouilles/local-http-evidence.json`、`local-extra-evidence.json`、`local-import-evidence.json`、`local-import-final-state.json` 及相关日志。真实票据在 `local-test-receipts.json`，不入公开 Git。

## Test 替换执行记录

待 Production 发布完成和稳定后：取得现有 `.production-ops.lock` 与 Staging hygiene lock；只读生成实际完整 ID/Compose labels/mounts/networks/ports/protected snapshot/shared ingress hash；封存精确删除清单并自动复核后执行。任何归属不明的资源保留。仅旧 Test 已获豁免备份；Production/Staging 数据仍完全受保护。

Allô 固定预算沿原路径：Backend512 MiB、DB128 MiB、Frontend32 MiB、Relay16 MiB，总 cap688 MiB；CPU caps 合计1.25，非预期同时满载保证。启动前门槛仍为 MemAvailable≥1100 MiB / 可用磁盘≥6 GiB，不能降低；启动中/后复检 Production 健康和资源。失败只停止 Allô 专属组件，保留数据/日志，不恢复旧 Test。

前轮容量采样838 MiB只代表当时12容器并存；不得作为现在已停止 Staging/Test 后的真实资源。下一次以实时测量为准。部署镜像只 COPY 本地最终 JAR/dist，不在服务器跑 Maven/npm 编译。

当前尚无已部署 Allô SHA或实际退休删除结果。候选以本分支commit及私有artifact receipt的精确SHA/哈希为准；不把源码提交当已部署。

Governance validator 已按HEAD原脚本只读执行：FAIL，原因是 sparse checkout 未物化的历史文档 `docs/governance/UBER_TEST_MENU_FULL_COMPARE_20261002.md` 链接；未为无关历史链接改动源码或扩治理工具。当前修改 diff-check PASS。

## 已知限制与验收边界

- 本店核心用户路径三语已完成；部分高级定价/Display Rule/高级报表/Android诊断仍有历史文案，不宣称全仓翻译。
- C6 是本菜单定价主食，未启用旧促销 Combo；通用 Combo 仍为原已验证子集，不扩未知套餐。
- 实体机型/codepage/纸宽/出纸、真实 Android、Uber/支付、Production 上线均 NOT_RUN；MOCK PRINTED 只代表软件模拟成功。
- 历史有多批次的整单不重新组合排版；PrintCenter 以具体原件重印。F31/STOMP 安全缺口保留，公网 `/ws` 必须继续拒绝。
- 本阶段完成后 Stop：`FIRST_RESTAURANT_ALLO_TEST_DELIVERY_ONLY_NO_REAL_PRINT_OR_PRODUCTION`，等 Owner 在 Test 反馈，不自动第二店/下一阶段。
