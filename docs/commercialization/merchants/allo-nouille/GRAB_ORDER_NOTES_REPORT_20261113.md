# Allô GRAB排序、菜品与整单备注交付 — 2026-11-13

已部署到 https://pos.allonouilles.com（43.166.242.250 / allo-nouilles）。
技术验收关闭；当前下一动作与Stop只见 `docs/governance/CURRENT_STATE.yml`。

| 验收标识 | 结果 |
| --- | --- |
| GRAB_SORT | PASS |
| GRAB_HEADER_REMOVED | PASS |
| C_ITEM_NOTES | PASS |
| B_ITEM_NOTES_PRESERVED | PASS |
| A_D_E_UNCHANGED | PASS |
| ORDER_NOTES | PASS |
| REGRESSION | PASS |
| DEPLOYMENT | PASS |
| PRODUCTION_ISOLATION | VERIFIED |

**版本与授权**

PRIMARY_REPAIR_GOAL：完成三项要求，保留金额、历史快照、草稿/重试身份和门店隔离。
EXPECTED_REPAIR_CLASS：HIGH；UI、Order持久化、幂等更新与冻结打印上下文的必要连接。

- Fresh origin/main基线：80e888fcdad74e98326fcbeaa5df14bc0d9baed6。
- 工作区起点49dbfba2a53cbeb181728938b1ad89472ac497e7，初始clean；2个main-only提交为文档。
  实际Allô运行23f3e804，因此复用干净Allô交付线；未碰dirty主工作区。
- 分支：codex/allo-grab-notes-20261113；[PR #311](https://github.com/Z1linXu/Restaurant_System/pull/311)
  基于codex/allo-production-improvements-20261112；未合并main。
- 本轮Owner明确授权测试通过后直接发布到Allô；该批次已执行完，无后续Production常驻授权。
- 不访问或改变Lanzhou Production、其数据库/容器/Nginx/Pad/打印端点。

**PRODUCT REPAIR**

GRAB/GRAB_UPDATE真正renderer删除餐厅名，稳定按snapshot SKU的A–E与数字排序，
A2在A10前；缺失/非法code稳定置后，不丢行、不合并不同选项/备注、不改变数据库顺序。
Update仍只列本次batch。单菜备注附于对应菜，整单备注在底部一次。
初始空备注不出区域；更新标明当前/已更新/已清除；仅备注票明确标为备注更新。
FRONTDESK保留原标题、顺序与金额规则；旧original-copy内容/字节不改写。

Allô C1–C6复用B备注控件与order_items.notes。A/D/E维持原规则。
Current Order卡片为只读备注摘要，编辑仍进商品弹窗；面板只有一个整单备注框，位于合计前。
Product Simplicity Check：复用Submit/Update按钮，不新增用户步骤或暴露revision/幂等机制。

V43只新增orders.order_notes和batch的order_notes_changed/notes_request_hash。
缺失/null保留，显式空串清除；初始空/缺失字段兼容旧提交hash。
已提交备注更新锁Order并校验expected_revision；冻结完整请求到既有LocalDraftRecord，
原key/body/revision确认响应丢失后的结果，确认前锁编辑，确认后可继续下一次更新。
旧namespace/schema/client identity和既有冻结Outbox不清理。不同context/client的延迟结果不串单。
仅备注更新不重算历史金额/折扣/税费，不新增items/tasks/inventory变化，且不出零金额前台票。
现有API返回的update_batch_id用于打印协调器与Pad kick，备注票失败不会被初始票掩盖。

全部产品路径为primary goal的BLOCKING连接。源改动文件（含定向测试）：

- `backend/src/main/java/com/restaurant/system/delivery/ReceiptContextService.java`
- `backend/src/main/java/com/restaurant/system/order/dto/CreateOrderRequest.java`
- `backend/src/main/java/com/restaurant/system/order/dto/CreateOrderUpdateRequest.java`
- `backend/src/main/java/com/restaurant/system/order/dto/IdempotentOrderSubmitRequest.java`
- `backend/src/main/java/com/restaurant/system/order/dto/OrderResponse.java`
- `backend/src/main/java/com/restaurant/system/order/dto/UpdateDraftOrderHeaderRequest.java`
- `backend/src/main/java/com/restaurant/system/order/entity/Order.java`
- `backend/src/main/java/com/restaurant/system/order/entity/OrderUpdateBatch.java`
- `backend/src/main/java/com/restaurant/system/order/service/OrderSubmissionHashService.java`
- `backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/order/service/impl/OrderSubmissionHashServiceImpl.java`
- `backend/src/main/java/com/restaurant/system/printing/dto/ReceiptPresentation.java`
- `backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java`
- `backend/src/main/resources/db/migration/V43__add_in_store_order_notes.sql`
- `backend/src/test/java/com/restaurant/system/order/service/impl/OrderServiceImplTest.java`
- `backend/src/test/java/com/restaurant/system/order/service/impl/OrderSubmissionHashServiceImplTest.java`
- `backend/src/test/java/com/restaurant/system/printing/renderer/AlloGrabNotesTest.java`
- `frontend/src/features/ordering/OrderingPage.tsx`
- `frontend/src/features/ordering/components/AlloOrderNotes.test.tsx`
- `frontend/src/features/ordering/components/ItemCustomizationModal.tsx`
- `frontend/src/features/ordering/components/OrderLineItemRow.tsx`
- `frontend/src/features/ordering/components/OrderSummaryPanel.tsx`
- `frontend/src/features/ordering/orderingCustomizationRules.ts`
- `frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts`
- `frontend/src/hooks/useDraftOrder.ts`
- `frontend/src/hooks/useDraftOrder.updatePersistence.test.tsx`
- `frontend/src/i18n/messages.ts`
- `frontend/src/offline/localDrafts.test.ts`
- `frontend/src/offline/localDrafts.ts`
- `frontend/src/services/orderService.test.ts`
- `frontend/src/services/orderService.ts`
- `frontend/src/types/ordering.ts`

**自动化与实际验收**

- Maven全套：1529 total，1412执行PASS、117环境条件SKIPPED，0failure/0error。
- Frontend：85文件、503项PASS；tsc PASS；git diff --check PASS。
- Agent6产品与最终不可变发布批次均PASS。四项直接相关阻断已合并关闭：历史金额重算、
  响应丢失后恢复、notes-only打印batch关联、延迟确认的context隔离。
- 真实ESC/POS编码器在内存中消费同一renderer文本，验证无标题、自然顺序与中文备注；
  不将编码器/MOCK证据称为实体纸张证明。
- 复用localhost PG26512/backend18512/frontend28182；同一exact-SHA JAR，独立合成数据与凭据，
  MOCK、无真实端点，seed/bootstrap/Uber关闭；未把生产恢复数据导入QA。
- V42→V43后及完整验收结束时，原11orders、28items、14options、4batches、31PrintJobs、
  30outbox以及菜单/加料/价格配置，逐行旧字段hash均不变。
- API Order12：E2,C4,B1,A7,E1,A3存储顺序保留；GRAB为A3,A7,B1,C4,E1,E2。
  初始提交重放、notes-only/clear/replay/hash conflict/stale revision、added-only update、
  legacy字段省略、Finish及完成后原意图确认全部PASS。
- 浏览器Order13：相同六菜输入顺序，B1少辣+米粉+牛筋x2、C4不要酱汁，中英文换行；
  单一Order Notes、保存/刷新/商品重编辑/服务器Edit恢复均PASS。74.92+11.22=86.14。
  仅备注更新产生Job42/batch9一张GRAB；旧items/金额不变。
  新增C6（CUSTOM双拼两项）和D2后，GRAB Job43只含C6，FRONTDESK Job44包含两者，
  subtotal97.90/tax14.66/total112.56；旧Job40/41完整artifact不变。5个Job全PRINTED。
- Finish用新SHA实际API完成12、13，浏览器确认TEST1释放为4空闲/0使用中。
  未重复已知CUA会卡住的原生确认框：复用Nov12 Owner亲自确认的未变UI证据；未声称本轮点击了原生Finish确认。
- Combo/Remove与离线Outbox/重试/幂等由相关自动化回归覆盖；未为测试更改生产主数据。
- 实际域名只读：Owner页、桌台、空闲TEST4菜单与C4弹窗，确认新备注区和唯一Order Notes。
  未输入/添加/提交生产菜品；原TEST1草稿标签未重载或改动。只有普通空本机草稿初始化，无服务端测试销售。

**不可变批次、备份与上线证明**

- Application/runtime SHA：`b78ab21d9d4b97bf2b6526ccc489503f486b866e`。
- Backend JAR SHA256：`3ef5ff6af2cf11ad882bafca07be7584d96dd9c48343118395c23cbe1f044a0d`。
- Archive SHA256：`3ec50f9a6b205dc76a3b38ccbeca6d8a3ce9869d2c4b650ceb352436135f0307`；60文件hash验证，source.tar等于git archive。
- Backend image：`sha256:780ec824897cf363fd3a62d94bb52f7e609ebe3988d3d01c9d833487a3a31c4e`。
- Frontend image：`sha256:1e8c3dbe54e03b3567c76c5fcccc055cb52328fed6800d77d360dfad4a5b78a2`；生产namespace维持allo-nouilles。
- Compose SHA256：`51bc6d759a01382c9c29db7b09a90a5a65ac3055348540320c984ee01209aa83`；唯一必要配置变化为Flyway target42→43，
  运行APP_BUILD_SHA与两个镜像引用随版本更新，DB及其他环境配置比较一致。
- Fresh备份：2026-11-13T07:42:29Z，`a2e108c051cf19099c28a0d0ed3147012f6ac812d0379d56dbe9c8efe41f6197`。
  当前V42实测87表；在socket-only恢复集群逐表count/hash与约束/索引验证，随后停止移除。
- 激活完成：`2026-11-13T08:04:12.831945+00:00`。只替换backend/frontend；三容器healthy，V43_ALL_43_SUCCESS。
  DB容器仍为`cbd0c6cf504d3b746f880879db3f0beb9da3c3ce212e35c8d2b0a21061820d37`，启动时间Nov8不变。
- 只读post-smoke：13个API检查、HTTPS index/entryJS/logo三制品hash、WSS认证订阅及匿名/错误token/
  跨店/wildcard/client-publish拒绝PASS；7组业务旧字段hash、11组配置及34价格不变。
  排除的仅是V43新增三个字段；原有1笔preparing订单保留，0pendingJobs/Outbox，0devices/endpoints。
  Nginx配置hash仍为`2a25aac4adfa8e3fc60a8453d2dd978e61d7ba5aaef7ec52f53db891a4a14fa7`。
- 固定发布假设routing.enabled=false已在预检/激活守卫确认；没有扩大到通用TemplateRouting notes项目。

**TOOLING / GOVERNANCE REPAIR**

下列变动均为本轮安全闭合所需；末次仅事实/回滚约束文档与证据同步，采用纯文档review例外，不重建或重启已验证应用：

- `SYSTEM_DOCUMENTATION.md`
- `deployment/allo-nouilles/compose.yml`
- `doc/API.md`
- `docs/commercialization/merchants/allo-nouille/GRAB_ORDER_NOTES_REPORT_20261113.md`
- `docs/governance/CURRENT_STATE.yml`

Compose target43为BLOCKING；私有backup/recovery/stage/activation/只读smoke复用既有发布路径，
只绑定本批次SHA/hash/87表与V43投影，没有第二套部署/凭据/队列生命周期。
无capability SQL、生产价格批次、生产测试订单写入、生产reprint或硬件绑定。
新UI公开后默认forward fix；任何frontend/backend降级前，必须证明所有客户端没有新备注草稿/
冻结更新意图，服务器outbox不足以证明；保留V43和现有订单，禁止用旧dump覆盖新数据。

全量governance checker遇到ignored node_modules/jiti README链接；tracked隔离副本另遇历史
TEST_COVERAGE_REPORT引用ignored .template-test-private/LOCAL_BROWSER_MOCK.png。
记NON_BLOCKING_EXISTING_DEBT，Agent6同意不扩展治理工具；当前authority结构与本轮变更链接单独验证。

**Docker / Disk Hygiene**

初次检查15% used /49GB free，候选构建后及发布后16%/48GB；Build Cache1.162→1.278GB。
未清理（reclaimed0）；当前runtime、23f3回滚镜像、数据库持久化目录、备份/配置均保留。
Shared Staging、旧共享Test和Lanzhou Production未变更；Allô Production本批次已变更。

**证据位置与限制**

私有目录：`/Users/xuzilin/.codex/private/allo-notes-20261113`。
关键文件：qa-acceptance-summary.json、qa-api-notes.json、qa-browser-final.json、
qa-draft-notes.jpg、qa-grab-preview.jpg、qa-notes-only-preview.jpg、qa-finish-board.jpg、
production-c4-notes.jpg、release-artifact.json、production-recovery-notes-verification.json、
staged-images.json、activation-prepared.json、production-activation-result.json、production-smoke-result.json、
production-notes-after.json。备份、凭据、完整业务数据未提交Git。
Native Android worker/真实打印机/codepage/纸张：NOT_RUN，无尝试，不影响本轮MOCK交付结论。
没有未解决的本轮产品阻断；上述未变硬件及治理链接债务不作额外确定性声明。

**Execution Time Breakdown**

本轮开始2026-11-13 01:33:12 America/Toronto；阶段为Estimated分摊，包含并行审查、构建及重试。

| Stage | Time | Notes |
| --- | ---: | --- |
| Investigation / Planning | Estimated 8 min | fresh Git/authority与实际Allô只读核对 |
| Implementation | Estimated 34 min | renderer、UI、V43与安全备注更新；含Agent6阻断修复 |
| Tests | Estimated 6 min | 定向与完整回归、tsc；含并发保护与测试fixture断言修复 |
| Agent 6 / PR / Merge | Estimated 13 min | 产品及批次复审、PR311；未合并main |
| Staging Deploy | N/A | 未授权/未修改shared Staging |
| Staging Acceptance | N/A | 以独立loopback synthetic QA验收 |
| Synthetic QA / release preparation | Estimated 28 min | 同SHA构建/API/浏览器、fresh备份恢复；含一次收据前缀断言修正与约4分钟上传等待 |
| Production Deploy / Acceptance | Estimated 5 min | 激活约70秒，后续HTTPS/API/WSS/实际域名检查 |
| Evidence / Governance | Estimated 12 min | 当前事实、证据、PR元数据与Stop同步 |
| Total | Estimated 106 min | 本轮01:33:12 EST开始，含最终文档/PR同步；阶段为并行工作分摊 |
