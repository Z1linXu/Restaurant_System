# Allô Nouilles 销售与打印交付记录 — 2026-11-15

PRIMARY_REPAIR_GOAL: 完成 Owner 本轮 Allô 打印、作废/折扣、销售汇总、Takeout 修复、辣度移除及兼容 APK 上线。
EXPECTED_REPAIR_CLASS: HIGH

## 授权、版本与执行结果

Owner 附件 `461d7ddd-09bc-48b2-a52d-7de6eb4bde38` 明确授权本轮实现、备份、隔离验证、指定数据修复及兼容部署。正式目标核对为 `43.166.242.250` / `allo-nouilles` / Store1、Organization1 / `ALLO_NOUILLES_TEST`、Allô Nouilles，数据库 `restaurant_allo_nouilles_test`。名称和配置由真实环境确认，并非仅凭 URL。

Ground Truth：fresh `origin/main=80e888fcdad74e98326fcbeaa5df14bc0d9baed6`；Allô 基线 `7490694e80316156ffbdd5f62a2eb4e66c708bd5`，main-only2 / Allô-only40，初始工作区干净。本轮依 Owner 要求沿用已上线 Allô 独立线，不以 main 覆盖客户功能。

- 分支 `codex/allo-sales-printing-20261115`；[PR315](https://github.com/Z1linXu/Restaurant_System/pull/315)，Draft，base `codex/allo-printer-test-20261113`。未合并 main。
- 主功能/前端/APK资源 SHA：`079b1759f8bfd7be9177c6b6b7b134d16e090167`。
- 构建引用修复 SHA：`9d9954080501fc635210f04400e6e3b132792e97`。
- 最终后端 SHA：`8b4f5bc61ed182d200e1755f5397f5277152b38e`。
- 部署前后端 `4c75f960d1ad00a799c1d5054f22286149429447` / 前端 `b78ab21d9d4b97bf2b6526ccc489503f486b866e`，V43。
- 主批次上线 `2026-11-15 02:06:43 America/Toronto`；原票 XS 兼容补丁 `02:22:15` 上线。
- Allô Production 已修改并通过检查；旧共享 Test/Staging 未启动。最终本地 QA 后端/前端进程已停止，数据保留。

## PRODUCT REPAIR

`backend/.../allo/` 新增独立门店金额、审计、预设、汇总、报表、旧客户端兼容和打印分组能力；现有 Order/Analytics/Receipt/Print 服务接入；前端 `features/allo/`、点单、订单与 Dashboard/Sales 页面接入。`V44__allo_financial_adjustments.sql` 为 additive migration：订单税/折扣规则、行累计作废量、3 张审计/预设/报表表。已应用 migration 未改写。

业务行为：

- 相同菜单身份、价格、备注、选项数量、combo归属和批次均一致才合并打印；不跨不等价行合并。GRAB A–E自然排序及既有备注保留。当前订单重印用有效现状；Print Center 原票副本保留原文/字节/上下文。
- Allô 新票逻辑 XS 对应受支持 `SMALL`，ESC/POS `GS ! 0x11`。原票副本按冻结字号兼容新 SMALL/历史 XS；编码、纸宽、code page 不匹配仍拒绝。没有重建原票字节或自动重试物理失败。
- 当前 Toronto 原提交营业日可部分作废、整单作废、百分比/固定额税前整单折扣、取消折扣；必须记录操作者、原因、原日期、前后金额、折扣规则和打印处理策略。订单行原价格/快照不删改；并发 revision200/409、稳定请求重放和审计写失败全事务回滚已验证。
- 自定义折扣预设 Store共享，可编辑/停用，历史调整保留当时规则；FRONTDESK可操作，无 PIN、支付方式或新的审批步骤。
- Sales Overview 与 Dashboard/订单/小票共享金额。预览冻结 report_id/日期/生成时间/金额/文本；使用现有 FRONTDESK_RECEIPT assignment及Pad协议创建单份无订单报表任务，不制造销售订单。
- Allô Takeout 服务器提交成功即 Completed；库存、厨房任务及派发按原提交事务完成，不调用会取消任务的 Finish。堂食保留 Finish。原 Outbox 请求身份/快照不改写。
- B1–B8 精确结构辣度身份软停用8组/32选项；menu_revision97→98，旧快照、已冻结 Outbox辣度仍可提交/重放。无其他菜单价格或打印配置变更。

金额定义：Gross 为有效行金额；Discount 为单个整单税前折扣并限制不超过 Gross；Net=Gross−Discount；税按既有14.975%税率在订单级 HALF_UP至分，Total=Net+Tax。报表逐单累加已确定税额，不按日总额重算税。有效 Completed 才记 Collected=Total；整单作废不进入有效销售，单独披露。Amount Collected 明示“按门店规则记账的已收金额，未按现金/银行卡核对”。

隔离例证：2瓶水 Gross3.98，50%折扣1.99，Net1.99，Tax0.30，Total2.29；添加1瓶并提交后 Gross5.97，Discount2.99，Net2.98，Tax0.45，Total3.43。浏览器已核对。部分作废、最后有效行作废→整单作废、取消折扣、ledger失败回滚、相同key重放均通过真实 API。Production 未创建测试折扣、作废或新订单。

## 历史 Takeout 与销售对账

仅修复8张符合条件的滞留 Takeout，保留原 submitted_at/营业日期，不重派打印、不重扣库存、不取消厨房任务；生成 SYSTEM / OWNER_AUTHORIZED_20261115 审计。幂等重跑0订单、0配置写入。

| 原营业日 | 修复订单 | Collected 增量 |
| --- | ---: | ---: |
| 2026-11-13 | 4 | $71.10 |
| 2026-11-14 | 4 | $180.38 |
| 合计 | 8 | $251.48 |

真实环境 raw orders、汇总API、已重建日期聚合逐项对账：

| 日期 | 单数 | Gross | Discount | Net | Tax | Total / Collected |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 2026-11-04 | 10 | 1946.02 | 0.00 | 1946.02 | 291.42 | 2237.44 |
| 2026-11-13 | 25 | 1067.79 | 0.00 | 1067.79 | 159.89 | 1227.68 |
| 2026-11-14 | 15 | 727.44 | 0.00 | 727.44 | 108.93 | 836.37 |
| 2026-11-15 | 0 | 0.00 | 0.00 | 0.00 | 0.00 | 0.00 |

最终线上浏览器已选11月14日：15单、总额836.37、Takeout4单180.38、4条修复审计，前后台一致；新日期未承接历史销售。真实当前日预览 `8da493e8-79da-4370-b7a0-0009f1e5aff1` 已核对，未在 Production 点打印。纸张未验收。

## 验证与风险边界

- 主批次后端19类325测试，前端87文件522测试及 TypeScript，Native18单测 PASS。
- 四套 exact-SHA localhost合成 API acceptance PASS：财务/并发/预设与隔离、旧客户端/ledger回滚/Combo、历史修复重放/辣度兼容、报表Pad claim/start/payload/fail。
- 报表协议证明：无 order_id、1份、GBK、正确 frozen hash、`1D 21 11`、相同意图不重新排队 FAILED；sentinel0 TCP连接，不等于纸张成功。
- 最终后端补丁3类63测试 PASS（与325有重叠，不累计计数）；新增6用例覆盖历史XS/新SMALL原票副本及4种设置拒绝。真实签名 HTTP 原票105→副本113 PASS：text/bytes/provenance完全相同、原票未改、0 TCP；副本通过已有Pad协议显式结束为合成 FAILED，未留下活动测试任务。
- Agent6：主功能、构建引用小修复、最终原票补丁及复用backend-only发布路径全部 PASS，无未关闭P0–P2。
- 线上最终 SHA/健康/V44、5组CORS、匿名401/跨店403、WebSocket info、Web与APK资源hash、3日期金额对账 PASS。最终补丁后只读检查，未重跑修复SQL。
- 原 item/options/jobs/outbox/厨房/饮品/库存与静态配置12域指纹及device版本清单保持；补丁前后完全相同。主批次仅有明确的8单/辣度/修订号/聚合/derived report变更。
- APK实际覆盖安装及纸张打印未执行：adb无设备。源码/协议/签名与资产证明不能替代真机验收。
- 兰州 Production 未改的验证依据：所有远程执行严格固定SSH alias `allo-nouilles`、VM hostname、项目labels、DB挂载和Store/org/code守卫；Lanzhou网络/API调用0、无其部署/SQL/容器操作。此结论是本批次操作隔离证明，不重新认证兰州当前运行版本。
- 文档/状态定向校验 PASS；全库 `validate-governance.py` FAIL于既有 `frontend/node_modules/jiti/README.md`→`./src/babel.ts`缺失链接，与本轮改动无关，归类 NON_BLOCKING_EXISTING_DEBT，未修改依赖包。没有把该全库检查报告为PASS。

## Android 影响与交付

| 改动 | Backend | Web / APK内置资源 | IndexedDB / Outbox | Native Bridge / Worker |
| --- | --- | --- | --- | --- |
| 同品合并、XS | 是 | 原票预览随服务器 | 不变 | 不变，传输冻结字节 |
| 作废、折扣、预设 | 是 | 是，需新APK界面 | 不增加财务离线队列 | 不变 |
| Dashboard / Sales Overview | 是 | 是，需新APK界面 | schema不变 | 现有非订单job协议 |
| Takeout完成/修复 | 是 | 兼容旧响应终态处理 | stable身份/冻结请求保持 | 不变 |
| B类结构辣度移除 | 精确配置+兼容处理 | 新revision隐藏选项 | 旧Outbox仍可重放 | 不变 |

旧1.0.3兼容 PASS：普通点单/旧冻结请求提交、已提交update重放、Takeout终态、旧Pad报表字节传输协议保持。已发生财务调整的订单给旧读取接口净额兼容视图；旧版新加菜在该类订单上明确要求更新，避免旧界面/提交规则产生错误金额。1.1.0使用 `financial_version=2`。因此需要更新APK使用完整新功能，但无需先更换全部Pad再发布服务器。

APK：`dist/android/allo-nouilles-pos-v1.1.0-debug.apk`，4,376,156字节，applicationId `com.allonouilles.pos`，versionName1.1.0 / code5，minSdk26 / target35；Bundled Assets，93文件，同一Web构建079b。Native Java/Bridge/Worker无修改，IndexedDB schema4 / namespace `allo-nouilles` / origin `https://restaurant-pad.local`不变；固定API/WSS `pos.allonouilles.com`。

SHA256 `1e213b39659dc654f3d8c846e7027db07dd0d243f4e7308c97b4c09671ce21d7`。
签名证书SHA256 `eefc3470dfeb1d118a69c2d0ba011c2f8a158e79bc23395caf0ea3ef8d35e106`，与旧版相同；覆盖安装使用 `adb -s DEVICE_SERIAL install -r ABSOLUTE_APK_PATH`，勿卸载/清数据。实际设备升级保留待验证，不将此前版本的真机证据当作本版验收。

[Android官方更新规则](https://developer.android.com/build/configure-app-module)；[Epson GS ! 字号指令](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/gs_exclamation.html)。

## TOOLING / GOVERNANCE REPAIR

复用既有本地合成QA、socket-only恢复验证、Compose镜像复制构建和受控替换路径。`deployment/allo/promote-sales-20261115.py`及明确一次性SQL是本轮V44/授权修复必须的范围；新增4个QA脚本覆盖新的金额/报表/修复边界。`CURRENT_STATE.yml`、`SYSTEM_DOCUMENTATION.md`、`doc/API.md`及本文只同步改变的事实。

首次stage在运行时变更前失败：Docker FROM不能直接使用裸sha256镜像ID。TOOLING_REASSESSMENT：11行修复回到已审查tag路径，同时校验tag→imageID、保存stderr、复用不变baseline；无额外应用构建。最终XS补丁复用Nov13已审查私有backend-only脚本，仅替换当前SHA/V44/90表并删除旧测试清理。无新增部署架构、凭据流程或常驻测试栈。

SCOPE_EXPANSION_CHECK：原票产品补丁5行，新增6回归；复用脚本只为保留前端/DB不重启、exact-SHA、备份/回滚、双租约守卫及配置证明等必需安全步骤。通用化/清理/P3优化全部延期。未修改APK/前端源，因此不重建这两件制品。

## 备份、恢复和 Docker

详见[不可变发布批次](SALES_RELEASE_BATCH_20261115.md)。主批次前87表备份及最终V44前90表备份均已 socket-only restore、逐表row hash、约束/index验证，恢复副本删除，不作为QA数据。配置/旧镜像基线均保留。

最终补丁可只恢复其baseline runtime.env后受控替换到079b后端，保留V44与所有当前数据；079b已支持全部财务调整，仅原票XS比较问题回退。更早的忽略作废/折扣后端不可用于已有调整订单；优先forward repair。主批次修复前精确行快照保留，需补偿时以revision守卫+新ledger记录，不覆盖数据库、不回放票据。每次停后端前后都需无CLAIMED/PRINTING，不盲目重印。

Docker / Disk Hygiene：主批次前磁盘18% /47GB空闲、Build Cache1.78GB；最终19% /46GB空闲、Build Cache2.066GB（reclaimable249.8MB）。Cleanup NONE，回收0；当前与回滚镜像、配置、DB和恢复制品保留；共享Staging未启动；Production仅本批次Allô授权变更。

## 最终状态

| 项目 | 状态 |
| --- | --- |
| PRINT_MERGE | PASS |
| XS_ENCODING | PASS |
| XS_PHYSICAL_PRINT | HARDWARE_NOT_TESTED |
| ORDER_VOID | PASS |
| DISCOUNTS | PASS |
| CUSTOM_DISCOUNT_PRESETS | PASS |
| FINANCIAL_RECONCILIATION | PASS |
| SALES_OVERVIEW_PREVIEW | PASS |
| SALES_OVERVIEW_PAD_PRINT | HARDWARE_NOT_TESTED |
| TAKEOUT_AUTO_COMPLETE | PASS |
| EXISTING_TAKEOUT_REPAIR | PASS |
| SPICY_OPTIONS_REMOVED | PASS |
| APK_UPDATE_REQUIRED | YES |
| NATIVE_CODE_CHANGE_REQUIRED | NO |
| OLD_APK_COMPATIBILITY | PASS |
| APK_BUILD | PASS |
| PAD_INSTALLATION | PENDING |
| ALLO_DEPLOYMENT | PASS |
| LANZHOU_PRODUCTION_UNCHANGED | VERIFIED |

## Execution Time Breakdown

分项为 Estimated wall-clock 工作时间，含并发重叠和重试。Total涵盖初始调查到最终Stop，约85分钟；不以分项相加推算并发墙钟。

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | ~8 min | 实际客户版本、数据、APK、协议及权限范围 |
| Implementation | ~28 min | 财务/打印/界面/向后兼容，含最终XS5行修复 |
| Tests | ~12 min | Maven/npm/Native，主测试与最终63聚焦 |
| Agent 6 / PR / Merge | ~8 min | consolidated修复+最终窄审；PR315，未合并main |
| Staging Deploy | N/A | 旧共享栈按Owner要求不启动 |
| Staging Acceptance | ~12 min | 由独立localhost合成环境API+真实浏览器替代 |
| Production Deploy / Smoke | ~14 min | 两次备份恢复，传输/构建，主上线+backend-only补丁；首次stage在变更前修复1次 |
| Evidence / Governance | ~7 min | runtime指纹、对账、安装交付与当前状态同步 |
| Total | ~85 min Estimated | 含本轮初始读取、构建与重试；最终Stop前无新增工作包 |

Stop：服务器与APK交付完成，真机安装/纸张验收待硬件。下一Phase、额外Production操作、硬件绑定均未授权。当前Gate/Stop唯一权威在 `CURRENT_STATE.yml`。

## 修改文件索引

```text
SYSTEM_DOCUMENTATION.md
backend/src/main/java/com/restaurant/system/allo/AlloCopies.java
backend/src/main/java/com/restaurant/system/allo/AlloLegacyOrderView.java
backend/src/main/java/com/restaurant/system/allo/AlloLegacySpice.java
backend/src/main/java/com/restaurant/system/allo/AlloMoney.java
backend/src/main/java/com/restaurant/system/allo/AlloReceiptSupport.java
backend/src/main/java/com/restaurant/system/allo/AlloSalesController.java
backend/src/main/java/com/restaurant/system/allo/AlloSalesReportService.java
backend/src/main/java/com/restaurant/system/allo/AlloSalesService.java
backend/src/main/java/com/restaurant/system/analytics/service/ChannelSalesService.java
backend/src/main/java/com/restaurant/system/analytics/service/impl/AnalyticsAggregationServiceImpl.java
backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java
backend/src/main/java/com/restaurant/system/delivery/ReceiptArtifact.java
backend/src/main/java/com/restaurant/system/delivery/ReceiptContextService.java
backend/src/main/java/com/restaurant/system/menu/template/TemplateMenuService.java
backend/src/main/java/com/restaurant/system/order/controller/OrderController.java
backend/src/main/java/com/restaurant/system/order/dto/CreateOrderUpdateRequest.java
backend/src/main/java/com/restaurant/system/order/dto/OrderResponse.java
backend/src/main/java/com/restaurant/system/order/entity/Order.java
backend/src/main/java/com/restaurant/system/order/entity/OrderItem.java
backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java
backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java
backend/src/main/java/com/restaurant/system/printing/dto/PrintRenderRequest.java
backend/src/main/java/com/restaurant/system/printing/dto/ReceiptPresentation.java
backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java
backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java
backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java
backend/src/main/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImpl.java
backend/src/main/java/com/restaurant/system/printing/service/impl/PrintJobServiceImpl.java
backend/src/main/resources/db/migration/V44__allo_financial_adjustments.sql
backend/src/test/java/com/restaurant/system/allo/AlloMoneyTest.java
backend/src/test/java/com/restaurant/system/allo/AlloReceiptSupportTest.java
backend/src/test/java/com/restaurant/system/delivery/ReceiptArtifactTest.java
deployment/allo/promote-sales-20261115.py
deployment/allo/repair-sales-20261115.sql
doc/API.md
docs/commercialization/merchants/allo-nouille/SALES_PRINTING_20261115.md
docs/commercialization/merchants/allo-nouille/SALES_RELEASE_BATCH_20261115.md
docs/governance/CURRENT_STATE.yml
frontend/src/features/allo/AlloOrderActions.tsx
frontend/src/features/allo/AlloSalesOverview.tsx
frontend/src/features/allo/alloSalesService.ts
frontend/src/features/ordering/OrderingPage.tsx
frontend/src/features/ordering/components/OrderSummaryPanel.tsx
frontend/src/features/orders/OrdersPage.tsx
frontend/src/features/orders/components/OrderMiniCard.tsx
frontend/src/pages/AdminDashboard.tsx
frontend/src/pages/AdminReportsSales.tsx
frontend/src/services/manualReprintService.ts
frontend/src/services/orderService.ts
frontend/src/types/ordering.ts
restaurant-pad-app/android/app/build.gradle
scripts/allo-qa/sales_adjustment_edges.py
scripts/allo-qa/sales_pad_report.py
scripts/allo-qa/sales_printing.py
scripts/allo-qa/sales_repair.py
```
