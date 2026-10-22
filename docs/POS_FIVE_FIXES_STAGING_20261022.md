# POS 五项修复：Staging 部署与验收记录

五项修改已部署至 <https://staging-pos.lanzhounoodlesmtl.com>，真实 API 与浏览器主要业务验收通过。
应用共同 SHA：`fbcdb4f8152183b62b6ecc3859e2d5a689d01d49`。
最终部署时间：**2026-10-22 03:15:18 UTC / 2026-10-21 23:15:18 EDT（Toronto）**。
数据库为 **V38**。专用分支 `codex/pos-five-fixes-staging`，
[Draft PR #282](https://github.com/Z1linXu/Restaurant_System/pull/282)，未合并 main。
原有脏工作区未被本任务修改；实施使用独立 managed worktree，保留并行 #280/#281 的合入内容。

## 实施任务清单

- [x] PR1：每份 Size / Combo / Add-on 差价先求和再乘商品数量；初次提交只重算 DRAFT 的冻结价格；Update 保留已提交旧行。Frontdesk 逐份金额之和等于持久化 line_amount，兼容历史不整分金额。
- [x] PR2：Frontdesk 依据 SOUP_BASE snapshot 显示汤底；历史缺失保持未知。GRAB / HOT / Preview 共用 rule-first NO_SPICY 解析，区分显式空串与未配置；不改全局 Default/Profile 和历史任务/打印快照。
- [x] PR3：独立免费 Store Remove Catalog、V38、分类继承与单品 Allow/Deny、稳定不可变 code、同店 FK、当前菜单 revision、明确的 legacy reconciliation；校验真实动态 Combo Side、Parent 和同食材 Add/Remove 冲突。Clone/provisioning 创建目标店独立关联。
- [x] PR4：Add-ons 内的 Add-ons / Removes 子区、共享名称/启用/顺序/适用商品维护、关联数、Legacy 与冲突报告；前端后选覆盖相反项，服务端拒绝冲突。
- [x] PR5：复用原规则编辑器，置于 Pricing Rules 后；URL/刷新/Store/权限/module 检查有效。旧 Printing Settings 保留入口及设备、mode、assignments、jobs。
- [x] 独立 Agent 6 审查、自动测试、构建、文档、专用分支 push、Staging 备份/部署/迁移、真实新单/重打/浏览器验收。
- [x] 验收后再次验证历史及非目标 Store 配置；保留带 FIVE 前缀的测试记录供 Owner 人工复核。

## 自动化测试与构建

最终完整 Maven：**1,046 tests / 0 failures / 0 errors / 83 conditional skips（963 executed）**。
七项 Remove PostgreSQL 测试真实运行在本任务专用 PostgreSQL 16 集群：全新迁移、V37→V38、重复迁移、同店/免费/不可变约束、分类与单品适用性、reconciliation dry/apply/rollback、动态 Side 改绑及分类/商品删除。独立数据库中的打印规则生命周期测试也通过。
测试集群已停止，不影响开发者原 PostgreSQL 或任何服务器数据库。

Frontend **318 tests / 55 files 全部通过**，修复实际权限格式后再次全量通过；TypeScript/Vite production build、Backend package、目标 ESLint 与 tracked-snapshot governance validator 通过。
部署/验收保护测试 **16/16** 通过，Agent 6 独立接受。

固定价格回归使用 base 16.99、Size ±2、Combo 5、paid egg 1.99，覆盖 DTO→Service→flush/clear Database→Renderer，以及 offline draft / Outbox / frozen old lines。服务器实际使用原 Dockerfiles 构建双镜像，Frontend 显式注入应用 SHA；未修改门店售价凑结果。

常规复跑：`backend/` 内 `mvn test`，`frontend/` 内 `npm test` 和 `npm run build`；PostgreSQL 条件测试需按测试类提供独立测试数据库环境变量。
保护测试：`python3 -B -m unittest discover -s deployment/cloud/pos-five-fixes -p 'test_*.py'`。

## 部署、版本与回滚

| 检查 | 实际结果 |
| --- | --- |
| 公网前端 | HTTP 200；`/assets/index-BwODRDGt.js` 内嵌完整 fbcdb4f SHA |
| 公网 Backend | `/api/v1/system/health` HTTP 200，`UP` |
| Frontend image | `sha256:48bea3d30070c66299ee16b24ac915914004a764303097991be4850b126e734b` |
| Backend image | `sha256:4bb38242b262de79f626453f87435c71891549d5b24c7e91f441403b9e9c444a` |
| 镜像与运行模型 | 镜像 OCI revision、容器 revision、固定 release Git SHA 一致；环境、网络/IP、mount、HTTPS 转发、limits 与原模型一致 |
| Flyway | 首次 ece7724 发布正常增加 V38；最终 fbcdb4f 修复发布整个 ledger 保持不变 |
| 最新备份 SHA-256 | `a186d4f919968dde823044c5893c1183799a4371eb8c676872e4c117d29d652f` |
| 备份位置 | 私有 `/srv/restaurant-pos/staging/pos-five-release-fbcdb4f8152183b62b6ecc3859e2d5a689d01d49/staging-before.dump`，archive TOC 已检查 |
| Builder / 磁盘 | 仅移除本任务两个临时 builder；无全局 prune。最终可用 16 GiB；已有共享 Build Cache 16.82 GB 已只读审查，未清理 |

实际 Staging 使用独立 `restaurant-pos-staging` project/network、数据库 `restaurant_pos_staging` 和 `/srv/restaurant-pos/staging/state/postgres`；Production 使用另一网络、数据库容器和目录。仅更新 Staging backend/nginx，未修改 edge ingress、证书、Production checkout 或 CI/CD。

初次 19ba04b 尝试在镜像 guard 处停止，未创建部署证据或执行任何更新：并行 Uber 流程把 Staging Backend 更新成 image `6512955…`，其 **image OCI revision 是 7d573be**。当时遗留的 container revision label 是 8eea8d；该旧标签不能用来推断真实源码，也不代表发生了回滚。完成新基线核对后，ece7724 于 03:00:15 UTC 部署并迁移 V38。

浏览器发现新 Tab 曾按 capability code 读取权限，而真实 `/auth/me.permissions` 返回 enum names。fbcdb4f 已改用 `ADMIN_PRINTING_MANAGE` / `ADMIN_STORE_CONFIG`，未放宽后端授权；完整前端测试及独立审查通过后重新部署。Frontend 依赖安装曾等待 npm 网络请求，最终原构建流程正常完成，没有绕过构建。

[有界部署/回滚说明](../deployment/cloud/pos-five-fixes/README.md) 复用现有 ingress Compose model helper。服务器保存私有 rollback model、备份、日志和结果。回滚仅恢复对应应用模型，保留 additive V38；数据库恢复是另一个需要明确授权的破坏性动作。该 one-shot 已执行完毕，不得把历史 pin 改成宽泛 allowlist 后重跑。

## 真实 Staging 业务验收

目标是 **Store 18 / CHINATOWN / VALIDATION_FIXTURE / PHASE_B_OWNER_PROVISIONING**，未假定 Production Store 2。
通过原 Printing Settings UI 将本测试门店 PAD_DIRECT→MOCK，并启用原本关闭的 HOT assignment，保持其 printer 未分配；已有一个打印机地址保留，没有发起连接或实体测试。
其余 Store 的 mode、规则、Remove 配置未被覆盖。

在新 Menu Management Tab 实际 Save Draft、Publish v2（revision **82**）、Generate Preview：NO_SPICY 的 GRAB/HOT=`（走s）`，FRONTDESK=`不辣`。
实际新单验收不是仅检查 Preview：

| 场景 | Order | Frontdesk Job | Subtotal | Tax | Total |
| --- | --- | --- | ---: | ---: | ---: |
| 大碗牛肉面 ×2（初始快照） | 91 | 228 | 37.98 | 5.69 | 43.67 |
| Small ×2 | 92 | 230 | 29.98 | 4.49 | 34.47 |
| 大碗 Combo ×2 | 93 | 232 | 47.98 | 7.19 | 55.17 |
| 大碗 Combo + paid egg ×2 | 94 | 234 | 51.96 | 7.78 | 59.74 |
| 大碗蔬菜面·素汤 ×2 | 95 | 236 | 37.98 | 5.69 | 43.67 |
| 大碗蔬菜面·肉汤 ×2 | 96 | 238 | 37.98 | 5.69 | 43.67 |
| 既有牛肉炒面 NO_SPICY ×2 | 97 | 241 | 37.98 | 5.69 | 43.67 |

- Job 236/238 实际文本分别含 `汤底: 素汤` / `汤底: 肉汤`。
- GRAB **227** 实际文本 `(大（走s）) ×2`；HOT **240** 实际文本 `(牛炒（走s）) ×2`。
- Order 91 增加 Combo 的 Update batch **8**，旧行未重计；Update Job **243**、完整重打 **244**、原 Job 冻结补打 **245** 通过。更新后 Order 91 是两组商品，subtotal 85.96 / total 98.83；原 228 及其冻结补打 245 仍为 37.98 / 43.67。
- API 创建 Remove **22**，关联测试商品 **919 / 920**；随后通过真实管理页面改名为 **页面共享减料验收 / UI Shared Remove Accepted**，两个菜单 option **8768 / 8769** 均同步且 price=0。最终 menu revision **14**。
- Legacy 无歧义组 **21** 已显式关联。`remove_carrot`、`remove_cucumber` 和缺失/关系不明确的组保留原值并显示冲突，没有强行合并。
- 其他 **8 Store** 的 printing / display-rules / removes digest 验收前后相同；跨店 Remove scope 请求正确返回 400。
- 原 Printing Settings 的模式、assignment、设备/打印机/job 区域与新 Tab 入口均在实际页面核对；未点击实体测试按钮。
- 浏览器实际登录、Chinatown context、七 Tab 顺序、URL 刷新保持、Store18 v2→Store1 v12→Store18 v2 切换通过。1024×768、768×1024 viewport 均无页面横向溢出；这是浏览器布局证据，不是实体 Android/iPad 测试。
- 前台真实订单页面显示 Order 93 的 ×2 line 47.98 / total 55.17，与数据库和 receipt 相同。

## 数据保护与并行 Production 发布边界

本任务没有对 Production 数据库执行写入，没有重启/部署 Production，也没有发送任何外部平台订单或实体打印。
**整段会话期间 Production 并非完全无变化**：独立 Uber 发布于 **02:54:24 UTC** 更新了 Production Backend 至 **7d573be**（image `6512955…`）。该变化发生在本轮 Staging 发布取得运维锁之前，不能归为本任务的操作，也不能隐去。
Production Frontend 仍是 8c3a9e，数据库 V37。

本轮两次 Staging 部署分别保存自己的 Production/DB baseline；部署及验收后的容器 ID、start/restart、image、env hash、network、port 和 Production ledger 连续性检查均通过。
旧 Staging **orders ≤90 / print_jobs ≤226** 的五组 hash 在最终验收后仍一致：订单财务、item 财务/名称/备注、全部 option snapshot、厨房任务文字、Job rendered/payload/ESC-POS/rule snapshot。未重新计价旧单、改写旧 Job 或清理旧队列。既有 200/205/207 PRINTING job 保留。
新测试订单与两项测试商品保留供人工复核，Chinatown 保持 MOCK。
另对七个新订单的**全部 19 个 Job（227–245）**逐一回读：均为 PRINTED、
无 printer/endpoint、无 claim/printed device，raw execution mode 为 null，
结合当前 Store MOCK 及 dispatcher 的既有合同确认有效执行模式为 MOCK。

## 可验证证据

- [完整 API 验收 JSON](evidence/pos-five-fixes-20261022/acceptance.json)：SHA、订单/Job、金额、原始渲染、scope、reconciliation、12 个由场景验收器逐项检查的 MOCK Job。
- [发布与最终数据保护回读](evidence/pos-five-fixes-20261022/safety.json)：备份 digest、镜像、五组历史 hash、两个商品 UI 改名后的 readback、其他店隔离。
- [全部 19 个新 Job 的 MOCK 安全回读](evidence/pos-five-fixes-20261022/all-print-jobs.json)。
- [最终公网/镜像/数据库/历史连续性复核](evidence/pos-five-fixes-20261022/final-health.json)。
- [可读小票文本](evidence/pos-five-fixes-20261022/rendered-receipts.txt)：仅移除格式标记；原始文本保留在 JSON。
- [规则导航 1024](evidence/pos-five-fixes-20261022/rules-1024.png)、[规则 768](evidence/pos-five-fixes-20261022/rules-768.png)、[共享 Remove / 冲突保留](evidence/pos-five-fixes-20261022/remove-shared-768.png)、[真实 Combo 金额](evidence/pos-five-fixes-20261022/order-combo-768.png)。

凭证、私有 Compose/env、数据库 dump、服务器构建日志没有进入仓库证据。测试登录使用既有私有凭证，浏览器临时凭证文件已删除。

## 更新文档与剩余验证

已更新 [SYSTEM_DOCUMENTATION](../SYSTEM_DOCUMENTATION.md)、[API](../doc/API.md)、
[Database Design](../doc/DatabaseDesign.md)、[CURRENT_PILOT_SCOPE](CURRENT_PILOT_SCOPE.md)、
[Frontdesk/GRAB](operations/FRONTDESK_GRAB_ITEM_NAME_RULES.md)、
[Remove Catalog](operations/REMOVE_CATALOG.md)、[Add-on Contract](governance/contracts/MENU_ADDON_CATALOG_CONTRACT.md)、
[Store Override/Clone Contract](governance/contracts/STORE_MENU_LOCAL_OVERRIDE_CONTRACT.md)、
[Staging](../deployment/cloud/README_STAGING.md) 与 [Rollback](../deployment/cloud/README_ROLLBACK.md)。

剩余人工项是 Owner 在 Staging 复核，以及另行授权后的实体 Pad/打印机、纸张字体/编码/传输验证。
本轮没有进行真实纸张测试；Legacy 食材命名冲突仍需 Owner 判断实际含义。

## Docker / Disk Hygiene

| 字段 | 记录 |
| --- | --- |
| Disk Before / After | 最初预检 70% used / 18G free（临近首次构建 71% / 17G）；最终 73% / 16G |
| Build Cache Before / After | 16.25 GB / 10.37 GB reclaimable → 16.82 GB / 10.45 GB reclaimable；包含独立并行构建影响 |
| Cleanup Performed / Reclaimed Space | 没有清理共享 cache/image/volume/release；仅删除本任务两个临时 builder。共享回收量 0 GB；临时 builder 释放量未单独量测 |
| Protected Runtime Artifacts | 当前 Production/Staging images、运行容器、DB/data目录、两个应用 rollback model、备份、私有配置和验收证据均保留 |
| Staging Health | 公网前端 200、Backend UP、PostgreSQL 可查询；最终健康复核见交付记录 |
| Production Mutation | 本任务 NONE；独立并行 Uber 发布的 Backend 变化已在上文单独记录 |
| Hygiene review | 超过 12 GB 已进行前后两次只读复核；12 个 active cache record 和并行发布/回滚来源需要保护。磁盘低于 75% warning，未在本任务里确认共享旧缓存的年龄/全部保护引用，因此没有升级为清理 |

## Execution Time Breakdown

本轮从 **2026-10-22 02:17:00 UTC** 开始；阶段耗时为 Estimated，包含并行工作而不能相加为 Total。
最终整体耗时以交付回复中从该开始时间至最终 Stop 的实际区间为准。

| Stage | Estimated Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | 5 min | 基线、脏工作区保护、隔离及备份核查 |
| Implementation | 23 min | 五项并行实施，以及实际权限合同修复 |
| Tests | 12 min | 完整回归、真实 PostgreSQL、production builds、修复复跑 |
| Agent 6 / PR / Merge | 8 min | 多次独立复审、Draft PR #282；本分支未合并 main |
| Staging Deploy | 23 min | 两次实际应用发布；另两次初始 image/lock guard 停止；含 npm 网络等待与一次修复重发 |
| Staging Acceptance | 12 min | 首次 API 在写入前停止，最终 API/UI/全部 Job 回读通过 |
| Evidence / Governance | 8 min | hash、跨店回读、截图、记录、state 与最终证据提交 |
| Total | 见最终交付时间区间 | 从 02:17 UTC 到最终 Stop；阶段存在重叠 |
