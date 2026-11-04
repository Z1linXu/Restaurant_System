# Allô Nouilles — DELIVERY_TRACKER

PRIMARY_REPAIR_GOAL：按最新两页菜单交付这一家餐厅；复用成熟 POS/Station，完成独立 Test 与 Owner 验收入口。
EXPECTED_REPAIR_CLASS：HIGH。只限首店，不开发第二客户、完整模板或 Control Plane。
首轮开始：2026-11-03 22:17:44 UTC；桌面中断后按最新 Owner 附件恢复：2026-11-04 04:54:11 UTC。

## 当前授权与边界

最新 Owner 指令：`9faee6af-1c26-4c30-a7f6-1f493fdbfd2e/已粘贴的文本.txt`（Allô 最终执行／旧 Test 退休）。它取代此前“保留旧 Template Test”的运行安排。

- 明确批准永久删除归属已核实的旧 Template Test 四个停止容器、专属数据库/Volume、无共享引用的网络及旧运行配置。旧 Test 数据库备份已明确豁免。
- Allô 使用独立 Backend/PostgreSQL/Frontend/Relay、凭据、网络和数据；复用原 Test 域名及 `127.0.0.1:28080` / Relay `28480`，保持共享 Nginx/证书不变。
- 精确最小打印 CR 已明确批准，见 `REQUIRED_CHANGE_REQUEST.md`。仅 Allô 显式 Test/MOCK；没有 HOT、Pad 协议、状态/去重算法、Android、实体打印或旧店发布授权。
- Production 绝对不部署/重启/改数据配置。Staging 不启动或变更。另一线程的 Production 发布已于 2026-11-04 05:23:59 UTC 完成并停止；本任务随后核对实际运行并取得现有运维锁处理 Test。
- 旧 Test 镜像若无法完整证明无回滚依赖则保留，不执行全局 prune、递归目录删除或恢复旧 Test。

## 来源与工作现场

- 分支：`codex/allo-nouilles-delivery-20261103`。
- 工作区：`/Users/xuzilin/.codex/worktrees/allo-nouilles-delivery-20261103/Restaurant_System`。
- 初始 HEAD：`b6cab1b29c330c3020a009942973e083793d228c`；实际模板应用来源 `dbe207a1cc62256c7173108ccc375499f1e16caf`。新鲜 `origin/main=80e888fcdad74e98326fcbeaa5df14bc0d9baed6`；恢复时分支为 behind2/ahead10，main 两个新增提交仅旧店发布证据。未 merge/rebase/reset/clean。
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
- 全新隔离本地 PG18：实际成功应用 V1–V41，ddl validate；未使用 Production dump。PG18 有上游 Flyway16支持范围提示；远端部署沿 reviewed PostgreSQL16 固定镜像，远端 PostgreSQL16 实际 V1–V41 全部成功，见公网主流程 `41_successful_migrations`（仅 Allô 新数据库 SELECT）。
- 本地 HTTP 主流程：27/27 PASS。32 价格/FR 名核对，C6 错误语义验证，同幂等身份同订单，87.92 原计价断言、初单/Update、两模块票据、旧 Job 不变、新批次独立语言、同类续加正反例、DISABLED Submit/Update、跨 Store 403、Owner/Manager 登录。
- 本地补充：8/8 PASS。纯饮品一张前台票/零厨房任务；有 Update 的全单重印明确拒绝；原件副本文本/字节/context 相同且同重印 key 不重复；厨房错误不回滚订单、前台成功、警告持久化。
- 导入闭环：8/8 PASS。重复来源保留 Owner 改价/FR 名，冲突来源失败且事务原值保留，恢复原来源再次启动。已恢复本任务测试修改；最终 34 商品/39 provenance rows/测试标记0/MOCK/默认中文。
- 独立业务 Agent6：ACCEPT WITH LIMITATIONS，无剩余所审 P0/P1/P2。覆盖前轮普通4项、打印3项、后续空饮品/font/引用3项及 FE cluster；不宣称全 SaaS 安全。
- 同端口替换部署守卫：99/99 PASS。精确退休 ownership/共享引用/完整 ID/partial failure：15/15 PASS（合计114）；独立运维 Agent6 ACCEPT WITH LIMITATIONS，无剩余所审P0/P1/P2。
- 公网浏览器实际打开登录页，中/英/法切换及保留账号输入已确认；authenticated POS 全流程点击、真实弱网断线/恢复 NOT_RUN。单元测试与 HTTP 验收不冒充这两项浏览器验收。
- 私有证据：`.merchant-private/allo-nouilles/local-http-evidence.json`、`local-extra-evidence.json`、`local-import-evidence.json`、`local-import-final-state.json` 及相关日志。真实票据在 `local-test-receipts.json`，不入公开 Git。

## 首次交付身份与入口（后续登录修复见文末）

- Test：<https://test.lanzhounoodlesmtl.com/>，部署及公网验收 PASS；Owner 业务验收待进行。
- 应用源码／运行 SHA：`a19b5d64eab36bfc770927b1b1bb4bfe8876d15d`。
- 运维工具 SHA：`9803ada1184c7d9a310a507cdd2c0f242b82da6d`。仅修 Docker 可选 Health 字段读取；应用 Backend/Frontend 与 a19 相同，复用已验收 JAR/dist，没有重新构建应用。
- Draft PR：<https://github.com/Z1linXu/Restaurant_System/pull/308>；未合并、未授权自动合并。分支尚未集成 main 的两项文档提交；PR 当前不可直接合并，留待 Owner 后续决定，不为交付 Test 擅自 rebase/merge。
- JAR SHA256：`09bed11a656558b2bc88837c3db3cb1ab2b66a36ad34ae281357c0054784770e`；dist tree SHA256：`9faf320476b61e81e879969cd0f7968e4f62290bfd6401a2e25ffa72b5275f44`。
- 封存包 SHA256：`705baf41eab32c0c16349cca554950eb4998696ecf20150da5c947ff317bc210`。应用 release 与工具-only operations 目录分别保留，未覆盖已封存 manifest。
- 后续本轮文档提交仅记录证据，不是新应用 release，不触发 rebuild/redeploy。

| 服务／容器 | 完整容器 ID | 实际镜像 ID |
|---|---|---|
| `allo-nouilles-test-backend-1` | `9ea3a9da222af41186f14fbb060f66bee4aa4d57b1a16139acf26882d4f32a39` | `sha256:0d5dc0fd5d37d92e62e986703471a6437108110731da89ae69677a8829d30622` |
| `allo-nouilles-test-frontend-1` | `fea8ac2d6db1eb123c24912d9acfbd1328b6847e33e1b9f2f830a7f3253c68af` | `sha256:79e4db1941d15bcccc1a6f9d8a48f96c12f49a5cd6b87494637826e006d33ea2` |
| `allo-nouilles-test-db-1` | `57f1e8a3152cb08d88bd2f0a26abe1495ca9dfbde8444047aeb598ff157d2d09` | `sha256:cbf42f0edc946c0bc1b8eeed62403734e1f25dc67a3491455d07a92fdb558b28` |
| `allo-nouilles-test-relay-1` | `d2c024d5e8108fe3d21e9e1690e9c61bc2c846c1339124b296fabcb4a6e518d0` | `sha256:84ed8c0dd296c188bfc24ed506f46589e9285fb0b11cf3a04d91d524d3067e11` |

Backend/Frontend 镜像 tag 均为各自 `allo-nouilles-test-{backend,frontend}:a19b5d64eab36bfc770927b1b1bb4bfe8876d15d`。最终四容器均运行、无 restarting，DB healthy；Backend 无 Docker Health 字段，健康另由实际 HTTPS API 证明。

## Test 替换执行结果

另一线程 Production 发布结束后，本任务重新采集实际状态，在 `.production-ops.lock` 与 Staging hygiene lock 内逐项复核完整 ID、Compose labels、mounts、共享引用、端口、保护集合与 ingress hash。精确退休计划 SHA256：`582a7227d3af3807a44de16572c3a0927e8830c6a6483cd55520e5573f2c6fa5`；执行前自动重建比对，结果 PASS。

2026-11-04 05:25:38 UTC 永久删除旧 `restaurant-template-test`：

- 四个已停止容器 relay/frontend/backend/db，精确 ID 保存在私有 `retire-result.json`。
- 专属 `restaurant-template-test_postgres` volume（包含旧测试数据库/订单）。Owner 已明确豁免备份；无法从本次操作恢复旧 Test 数据。
- 专属 `restaurant-template-test_internal`、`restaurant-template-test_ingress` 两个网络。
- 精确私有配置 `/srv/restaurant-pos/test/private/runtime.env`。
- 所有镜像及历史 release/evidence 保留，保护共享及回滚引用。无全局 prune、递归删除或共享 Nginx reload。

实测释放文件系统 **72,146,944 bytes（68.8 MiB）**。停止容器本来不占运行内存，未把退休描述为释放在线 RAM。旧四容器最终剩余0；没有恢复旧 Test。

新 Compose project `allo-nouilles-test`，根目录 `/srv/restaurant-pos/allo-nouilles-test`。独立数据库 `restaurant_allo_nouilles_test`、用户 `allo_test`、volume `allo-nouilles-test_postgres`、网络 `allo-nouilles-test_internal` 与 `allo-nouilles-test_ingress`。Backend/DB 仅 internal、无 host port；Frontend 发布 `127.0.0.1:28080`；专用 host-network Relay 复用 `172.18.0.1:28480` 与原 edge allowlist。凭据全新独立，未读取/复制 Production 数据库或凭据。

共享 Nginx 宿主模板与有效配置 SHA256 始终为 `c70c419d83f6d541228a27bdf66bec59d62aca43832f108a0de6cdea07a42db8`；证书/DNS/配置/进程未变，无 reload。

## 部署后验收与证据索引

以下证据均在 ignored 私有 `.merchant-private/allo-nouilles/`，没有公开上传客户菜单、票据或凭据：

| 证据文件 | 实际结果 |
|---|---|
| `remote-deploy-result.json` | PASS，精确 app/tool SHA、资源 admission、保护集合及 ingress 不变、Production HTTP200/UP |
| `public-http-evidence.json` | 27 PASS：全部来源价格/名称、V1–V41、C6、续加、初单/Update、幂等、跨店拒绝、打印语言/旧 Job 不变、DISABLED 不阻塞保存 |
| `public-extra-evidence.json` | 8 PASS：饮品零厨房任务、原件副本/重印去重、多批次整单拒绝、厨房失败仍保存且前台成功 |
| `public-acceptance-summary.json` | 公网根页/health/logo/精确前端 SHA 正常；`/ws` 与 `/ws/info`403、`/actuator/env`404、未登录 catalog401，共9项入口检查 |
| `public-menu-ticket-matrix.json` | 19 PASS：每种打印语言覆盖全部32商品＋2续加、全部13加料各两份；金额565.42；34行、28 BACK_KITCHEN tasks；仅 GRAB＋FRONTDESK 两张 MOCK，完整必要零价选项 |
| `remote-repeat-bootstrap.json` | 仅 Allô Backend 同完整 ID 重启一次：0 created / 0 updated / 34 unchanged，39 provenance、菜单 revision 不变；14个启动采样中 Production 始终 UP |
| `public-final-state.json` | PASS：中文默认、目标覆盖 null、BACK_KITCHEN；品牌/Logo hash、refresh 后授权上下文；34商品/39provenance、真实打印 endpoint0、pending Job0 |
| `remote-runtime-before.json` / `remote-runtime-after.json` | 8个保护容器的 ID/image/start/restarts/running/restarting/health 全部不变；4个 Staging 主服务继续停止 |
| `retire-plan.json` / `retire-result.json` | 精确删除对象、计划 hash、实际结果与释放空间 |

最后公网测试已恢复 MOCK、默认中文、厨房/前台 override=null。故障验收产生的失败厨房任务保留用于证明，前台成功且未盲目重发；不清理测试历史掩盖结果。4张桌台为明确 TEST 占位，不冒充真实厅面桌号。

当前观测 Production Backend/Nginx 镜像标签均为另一线程完成的 `74dfc1fd235af31fc7345c43f770d9991960da70`，实际 HTTPS HTTP200/UP、DB healthy。本任务没有 Production restart/deploy/config/data/Flyway 动作；未查询其数据库验证 schema。Staging nginx/backend/ingress/db 继续停止（Backend/Frontend 标签 a14cc4ce87ecaea17b31d86aa55aae76f1a79294）；既有 uber-sandbox-webhook 仍运行且未变，不把它说成已停止。

## Docker / Disk Hygiene

| 指标 | 替换前／部署 admission | 最后快照 2026-11-04 05:51:41 UTC |
|---|---|---|
| MemAvailable | 初始1929 MiB；部署 admission1912 MiB | 1550 MiB |
| Load 1/5/15min | 0.22 / 0.23 / 0.22 | 0.68 / 0.37 / 0.27（2 vCPU） |
| Disk Used / Free | 65%；部署 admission21,421,649,920 bytes | 66%；21,169,999,872 bytes（19.72 GiB） |
| Build Cache / Reclaimable | 8.562 GB / 1.572 GB | 8.678 GB / 1.575 GB |
| Cleanup | 仅已授权旧 Test 专属资源 | 释放68.8 MiB；无 cache/image/volume 全局清理 |

Allô 固定上限 BE512/DB128/FE32/Relay16 MiB，总688 MiB；CPU caps合计1.25。部署沿原 MemAvailable≥1100 MiB、可用磁盘≥6 GiB 门槛，无阈值下调。重复启动采样 MemAvailable 最低1574 MiB、load最高1.83；最终1550 MiB仍通过门槛。Cache8–12GB 区间已复核：增长0.116GB来自本次两应用 COPY 镜像层；disk<70%、cache<12GB，不扩大为额外清理。保护 Production/Staging 当前镜像、数据库、回滚 release、共享入口与证据，均未删改。

## 安全登录与 Owner 验收

账号 `allo_owner` / `allo_manager`。密码仅在本地 ignored、0600 文件 `.merchant-private/allo-nouilles/TEST_ACCESS.md`，不在报告/PR/终端输出明文。文件含准确 Test URL 和使用说明；无 Production 凭据。

建议 Owner 先在 Test 核对菜单/品牌，分别切换三语，选择 C6 两项、Add-on 数量2，堂食提交/Update/同类续加，在 PrintCenter 检查 MOCK 原件与重印。切换 UI 语言与打印默认/厨房/前台语言应互不替代。完整 authenticated browser POS 点击及真实弱网恢复仍需 Owner 验收；已有433前端测试不是这两项实机证明。

### 后续实体打印验收清单（尚未授权执行）

1. 明确目标 Test 设备/打印机型号、纸宽、连接方式、目标 Station 及授权范围；保持三旧店设备/配置完全不动。
2. 中英法各测一张含长菜名/重音字符/必要零价选项/数量2加料的厨房与前台票，核对实际字形、换行、纸宽、字号、切纸。
3. 初单、Update 只打印对应新增批次；纯饮品无厨房纸；对照前台金额/税；同幂等重试不多打一张。
4. 改语言不改变历史原件；显式选原 Job 重印一次；断电/断网等不明确出纸结果人工核对，不盲目自动重发。
5. 单独确认设备协议与字体/codepage；MOCK UTF-8 证明字节不得直接当成实体/PAD 可用载荷。未获得明确授权前保持 MOCK/无真实 endpoint。

## 已知限制与停止边界

- 本店核心用户路径三语已完成；部分高级定价/Display Rule/高级报表/Android诊断仍有历史文案，不宣称全仓翻译。
- C6 是本菜单定价主食，未启用旧促销 Combo；通用 Combo 仍为原已验证子集，不扩未知套餐。
- 实体机型/codepage/纸宽/出纸、真实 Android、Uber/支付、Production 上线均 NOT_RUN；MOCK PRINTED 只代表软件模拟成功。
- 历史多批次整单不重新组合排版；PrintCenter 以具体原件重印。F31/STOMP 缺口保留，公网 `/ws` 与 `/ws/info`403，只是入口收口，不宣称修复 Broker/SaaS 隔离。
- Governance validator FAIL：sparse checkout 未物化的历史链接 `docs/governance/UBER_TEST_MENU_FULL_COMPARE_20261002.md`；不为无关历史链接扩治理工具。当前文档 diff-check 及本轮具体链接核对独立记录。
- Agent6 对业务代码、受保护打印切片、退休/部署守卫、执行 caller 及 Health 修复 ACCEPT WITH LIMITATIONS，无剩余所审 P0/P1/P2；纯证据文档收尾采用文档风险例外，不重跑应用构建/部署。
- STOP：`FIRST_RESTAURANT_ALLO_TEST_DELIVERY_ONLY_NO_REAL_PRINT_OR_PRODUCTION`。技术交付已完成，等待 Owner 在 Test 验收，不自动第二店/下一阶段/正式上线。

## 2026-11-04 浏览器登录修复

PRIMARY_REPAIR_GOAL：修复 Allô Test 浏览器携带 Origin 登录被403拒绝。EXPECTED_REPAIR_CLASS：SMALL。Owner实际反馈重新授权此单店 Test 局部修复；不改账号权限、数据、Production/Staging。

已复现：无 Origin auth/login200；带 `Origin: https://test.lanzhounoodlesmtl.com` 返回403 `Invalid CORS request`。旧验收遗漏浏览器 Origin。`RestCorsConfig` 的 `!template-test` 仍在 allo-test 注册，覆盖同路径专属 CORS 配置。最小修复仅将 profile 排除扩展到 allo-test；沿用现有显式 Test origin allowlist，不增加通配符或信任未知来源。新增 profile/来源隔离回归，原 Staging 代理回归保留。

修复实际运行 SHA：`a1b94a216a294de4e6f0b462aca414eef5266c5c`，取代上文首次交付 a19。Agent6 ACCEPT WITH LIMITATIONS；新增2项＋既有转发7项全部PASS，JDK21打包及前端build/typecheck PASS。未受影响的全量业务测试复用前轮证据，没有冒称重新全跑。

公网 `cors-public-proof.json`：Owner/Manager 带真实Origin登录、workspace、Store1 context、catalog均200；Store2访问403，恶意Origin和错误端口403，浏览器预检OPTIONS200。`cors-deploy.log`证明精确SHA、V1–V41、共享入口hash不变、Production UP、8保护容器不变。数据库volume与凭据原样复用，没有清除菜单/订单/草稿或提升权限。

Docker / Disk Hygiene：MemAvailable替换前1517MiB、后1479MiB；disk66%→66%；Build Cache8.678→8.794GB，未清理cache/镜像。只Allô应用/relay更新，Production/Staging无变更。实体打印仍NOT_RUN。

实际浏览器 Owner 登录已进入 `/stores/1/admin/dashboard`，Allô品牌和管理导航可见；私有截图 `cors-login-success.png`。未创建订单、未清理浏览器草稿/Outbox。修复后停回 Owner 验收 Gate。

## 2026-11-04 完整浏览器验收

Owner授权仅Allô Test真实浏览器菜单/点单/Update/同类续加/MOCK/中英法验收；不操作Production/Staging。起始分支HEAD3007bcc，fresh origin/main80e888fcdad74e98326fcbeaa5df14bc0d9baed6，运行a1b94a2。

PRIMARY_REPAIR_GOAL：解决实际POS菜单hash校验阻塞后完成原定浏览器闭环。EXPECTED_REPAIR_CLASS：SMALL。真实浏览器TEST1提示MENU_CACHE_HASH_MISMATCH，无商品可点；不清缓存/草稿/Outbox、不关闭校验。代码证据：Backend MenuCatalogHashService已将非null法文和组选项翻译/基数纳入canonical，Frontend menuCache尚未同步。最小修复仅同步canonical字段，保留legacy向后兼容哈希；普通局部缺陷按现有有界修复授权处理。

菜单修复 d1a05891928fe123506e2c447fdaf26989ec39bf 已部署。FE78文件443PASS、21项hash focused、typecheck/build及Agent6通过；Backend JAR byte-identical。首次调用因历史保护基线中Nginx旧ID被守卫拒绝，未修改服务；只读核对另一线程已在本轮开始前完成54d526e Production前端发布后，按本轮实时8容器基线复核再部署，保护集合和共享入口hash不变。

真实UI已完成初单#14：54.95/8.23/63.18，B1牛肉x2、C6两种必要零价蛋白、饮品；法语UI提交但中文打印。MOCK Job19/20均PRINTED，厨房不含饮品。Update追加当前可售A3（A1/A2现为停用，未改Owner配置）后60.94/9.13/70.07，新Job22英文厨房/21法文前台仅含A3；初单原件文本不变，指定原件重印一次，副本文本逐字相同。纯饮品#15只有Frontdesk Job23（法文），1.99/0.30/2.29，未出现GRAB；无主食时续面/续饭均隐藏。#14/#15已用正常Finish结束，历史保留。

随后重入#14时发现Update确认后的新增行未持久化为locked，刷新仍显示temp-A3，存在新key重复追加风险。PRIMARY_REPAIR_GOAL更新为同一验收中的本机Update确认落盘修复；仍SMALL有界cluster。已暂停再次Update、保留截图/草稿证据；不靠同名同价推断，不清IDB/Outbox。最小方向为成功返回前await规范server snapshot持久化、串行防旧autosave覆盖、历史不明确状态fail-closed保留并提示核对，不设计新的恢复协议。
