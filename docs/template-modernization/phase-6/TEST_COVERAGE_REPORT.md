# Phase 6 M0 + M1 测试覆盖与证据映射

本报告整理已经执行的证据；编写本报告时未重新运行测试、连接数据库、访问网络或部署。覆盖对象是 SYNTHETIC / MOCK 研究实施功能，不能外推为三家 Production 门店、真实设备或 SaaS 安全认证。公共域名端到端结果留待 Root 实际完成后补充。

固定研究基线：`028d4971740889a0ac0d784a43497bc3e5b54b3d`。功能状态采用 `IMPLEMENTED_AND_TESTED / PARTIAL / BLOCKED`；具体证据另标 `PASS / KNOWN_LIMITATION / NOT_RUN / PENDING`。`IMPLEMENTED_AND_TESTED` 只适用于所列测试范围，不表示所有环境都已验收。

## 1. 版本、环境与结果总表

| 证据 ID | 实际执行结果 | 环境与版本绑定 | 可复核来源及边界 |
|---|---|---|---|
| E-BE | Backend **1304 total，1187 executed PASS，117 conditional skipped，0 failures/errors**；verify exit0 | 本地 JDK21 Maven；已验证 Jar SHA256 `2e2716739259da8b04f8693dffe166adedad43128ad31c9ad12b4f3bdd4b6624`。Jar 编译版本 `25c690d`；Root 对 b64、96 候选确认运行 Java/resources/pom 相同，复用同一 Jar | [candidate-unit-proof-96a8b56.json](../../../.template-test-private/candidate-unit-proof-96a8b56.json)、`backend/target/surefire-reports/TEST-*.xml`。117 个跳过项不计 PASS；参数化 invocation 计入1304。 |
| E-FE | Frontend **71 files / 401 tests PASS**；TypeScript + Vite build PASS | 在实施工作树执行；最终产品 Frontend 冻结后，b64 / 96 精确候选构建通过。401 的执行回报保留在本会话工具结果，未私存 Vitest JSON | [实施记录](IMPLEMENTATION_TRACKER.md)及本会话实际执行结果。没有结构化 Vitest SHA receipt，不能声称每次 ops-only Commit 都重新执行401项。未升级依赖或锁文件。 |
| E-HTTP | **41 PASS / 0 FAIL**，2026-10-31 05:09:17–05:09:34 UTC | 真实 Spring Boot `http://127.0.0.1:28081`；独立 PostgreSQL18 `127.0.0.1:25432/restaurant_template_test`；SYNTHETIC Store3、跨 Org Store4；Commit `96a8b56cb9809b2ddf21fabbdd718f420d7e8e71`；上述真实 Jar | [http-local-96a8b56.json](../../../.template-test-private/http-local-96a8b56.json)，SHA256 `0934c947f62d064f6f01e48c404e07896cc7baede353af603b7a93fbc5e61315`。前后工作树 clean，source diff digest 为空 SHA256；runtime policy actual build_sha=96。HTTP 不是网页验收。 |
| E-SQL | **13 PASS / 0 FAIL**，2026-10-31 05:05:33 UTC | 独立本地 Test PG、只读事务；Commit `b64cc7ed002b9974ce70673212e1d388e48d4cb6`，同一 Jar2e2716；核对订单80、81，linked item8及指定 Job | [sql-local-final-candidate-proof.json](../../../.template-test-private/sql-local-final-candidate-proof.json)，SHA256 `398ef26979834edcd92ba0f9e2a7a71296da2de75abe405edbdbb7b37253e0d4`。这是 b64 真实行证据；不能改称96订单101、102的 SQL 重跑。Jar 身份由独立运行证明关联，SQL 自身只观察数据库行。 |
| E-GUARD | **59 / 59 deployment topology/safety guards PASS**及独立复审 ACCEPT | 本地离线 Compose config/资源归属/监听及隔离守卫；修复后独立审阅 tree `f39ba7cfc5f492ce6f3e86a1f1d77c58c5113239ac447ef801163768baee6efd` | [deployment/test/test_safety.py](../../../deployment/test/test_safety.py)、[实施记录](IMPLEMENTATION_TRACKER.md)、独立 Agent 实际执行回报。其后 ops-only stdin 修复扩展到62项，当前执行/复审结论须由 Root 另补，不能用59覆盖新增3项。 |
| E-UI-LOCAL | Root 实际网页：Owner 登录、无组 QuickAdd、草稿整页刷新、Chicken 选项、提交 Order110、A/B MOCK PRINTED **PASS** | 本地真实浏览器 `127.0.0.1:28080` → `28081`；Root 本会话 CUA 观察；产品源与已接受候选一致，未另造 UI 数据模型 | [LOCAL_BROWSER_MOCK.png](../../../.template-test-private/LOCAL_BROWSER_MOCK.png)及 Root 本会话 CUA 记录。Chicken 微辣、芝士差价1.50、去料；小计17.50、合计20.12可见。尚未提供独立完整网页 SHA receipt，不将该片段扩写为所有10步已完成。 |
| E-DOMAIN | **PENDING / NOT_RUN：本报告尚无公共域名完整业务验收 receipt** | `https://test.lanzhounoodlesmtl.com` | Root 最终补充实际部署 SHA、HTTPS/健康/登录/业务用例、运行镜像及证据路径。local HTTP 带该 Origin 的成功不能证明这个域名已可用。 |

私有证据均在 ignored、受保护的 `.template-test-private/` 下。本文只读取指定无秘密 receipt/proof；未读取账户凭据文件，不复制密码、token 或生产数据到报告。

## 2. 测试源码索引

以下路径可直接追溯到实际测试文件；表中的函数/用例名保留源码原名，避免只有模糊模块名。

| 索引 | 文件与本次相关测试 |
|---|---|
| B-MENU | [TemplateMenuServiceTest.java](../../../backend/src/test/java/com/restaurant/system/menu/template/TemplateMenuServiceTest.java)，22项；例如 `libraryCopiesHaveIndependentOptionIdsDefaultsAndPricesPerItem`、`groupAndOptionOrderingAreReturnedFromPersistedMetadata`、`requiredSingleSelectionValidatesAbsenceCardinalityAndQuantity`、`staleOrForgedOptionAndBasePriceAreRejectedByServerLeaves`、`emptyGroupsMaterializeQuickAddMetadataAndRetainInactiveHistoricalLeaves`。 |
| B-COMBO | [TemplateComboAdapterTest.java](../../../backend/src/test/java/com/restaurant/system/menu/template/TemplateComboAdapterTest.java)，9项；`legacyColdMappingRetainsStableIdentity`、`unlinkedLegacySideCannotDisappearFromKitchenTickets`、`unsupportedGeneralLinkedTaskIsRejected`、缺 code/group、伪造 code、非 COLD/不可用 linked item 拒绝。 |
| B-PLAN | [KitchenRoutingPlannerTest.java](../../../backend/src/test/java/com/restaurant/system/printing/routing/KitchenRoutingPlannerTest.java)，18项；一票/三票/共享设备、单品覆盖、多目标、空覆盖、未知覆盖、停用/缺设备/缺目标/未匹配、Combo child、Update、不可变输入输出、非法数量和控制字符。 |
| B-BOUND | [TemplatePrintingBoundaryTest.java](../../../backend/src/test/java/com/restaurant/system/printing/routing/TemplatePrintingBoundaryTest.java)，8项；`knownPreJobFailureDoesNotInventPrintedJob`、`terminalUncertainResultIsNeverReopenedOrReattached`、`successfulMockUsesFrozenTextAndExistingStateMethods`、`genericProcessorBranchDoesNotCallLegacyDispatcherOrAssignmentQuery`。 |
| B-IDEM | [IdempotentOrderSubmissionServiceImplTest.java](../../../backend/src/test/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImplTest.java)，13项；`sameKeySubmittedTwentyTimesCreatesOneOrderAndReplaysResult`、`serverSuccessIsReturnedWhenClientRetriesAfterLosingTheFirstResponse`、`sameKeySubmittedConcurrentlyCreatesOneOrder`、`sameKeyWithDifferentPayloadReturnsIdempotencyConflict`、`sameKeyIsIsolatedByStore`。 |
| B-PRICE | [OrderPricingSnapshotIntegrationTest.java](../../../backend/src/test/java/com/restaurant/system/order/service/impl/OrderPricingSnapshotIntegrationTest.java)，14 invocation；`dtoToDatabaseToReceiptKeepsPerUnitModifiersAndTax`、`updatePricesOnlyNewFrozenLinesAndKeepsHistoricalSubmittedAmounts`、`configuredSideBindingPersistsRemoveParentWithoutClientParentSnapshot`。 |
| B-PRINT | [OrderDispatchOutboxProcessorTest.java](../../../backend/src/test/java/com/restaurant/system/printing/service/impl/OrderDispatchOutboxProcessorTest.java)，8项；[PrintJobServiceImplTest.java](../../../backend/src/test/java/com/restaurant/system/printing/service/impl/PrintJobServiceImplTest.java)，27 invocation；[PrintDispatcherServiceImplTest.java](../../../backend/src/test/java/com/restaurant/system/printing/service/impl/PrintDispatcherServiceImplTest.java)，35 invocation；[PadPrintJobServiceImplTest.java](../../../backend/src/test/java/com/restaurant/system/printing/service/impl/PadPrintJobServiceImplTest.java)，21项。 |
| B-LEGACY | [GrabReceiptRendererTest.java](../../../backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java)42、[HotKitchenReceiptRendererTest.java](../../../backend/src/test/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRendererTest.java)20、[DineInTicketHeaderTest.java](../../../backend/src/test/java/com/restaurant/system/printing/renderer/DineInTicketHeaderTest.java)66、[FrontdeskReceiptPricingTest.java](../../../backend/src/test/java/com/restaurant/system/printing/renderer/FrontdeskReceiptPricingTest.java)6、[FrontdeskSoupBaseReceiptTest.java](../../../backend/src/test/java/com/restaurant/system/printing/renderer/FrontdeskSoupBaseReceiptTest.java)9、[NoSpicyPrintingRuleTest.java](../../../backend/src/test/java/com/restaurant/system/printing/renderer/NoSpicyPrintingRuleTest.java)4。这些是 E-BE 的子集，不能再次相加当新增测试总数。 |
| B-SAFETY | [TemplateTestRuntimeSafetyTest.java](../../../backend/src/test/java/com/restaurant/system/template/bootstrap/TemplateTestRuntimeSafetyTest.java)，7项；Test profile、数据库/user、Flyway 备用源、REAL/endpoint、外部 worker 的启动前保护；B-MENU 另有 Bootstrap 不写 foreign/错 profile/缺秘密/已有业务行的负例。 |
| F-MENU | [TemplateOptionGroupsPanel.test.tsx](../../../frontend/src/features/owner-admin/TemplateOptionGroupsPanel.test.tsx)：独立 copy、价格、两级排序、默认/启停、显式无组、失败不报成功/读失败不清配置；[templateCustomization.test.ts](../../../frontend/src/utils/templateCustomization.test.ts)：默认/整数分计价、验证、多选无新业务上限、稳定 code 冲突、QuickAdd；[templateMenuCatalog.test.ts](../../../frontend/src/hooks/templateMenuCatalog.test.ts)：null legacy、显式组、Combo绑定。 |
| F-POS | [TemplateItemCustomizationModal.test.tsx](../../../frontend/src/features/ordering/components/TemplateItemCustomizationModal.test.tsx)：`sorts choices, requires selection, and replaces a single selection with the correct ordinary price`、空必选组阻止、既有 Combo 按配置顺序；[orderPricing.test.ts](../../../frontend/src/utils/orderPricing.test.ts)、[useDraftOrder.menuSnapshot.test.ts](../../../frontend/src/hooks/useDraftOrder.menuSnapshot.test.ts)保留旧价格/快照。 |
| F-ROUTE | [KitchenRoutingPanel.test.tsx](../../../frontend/src/features/owner-admin/KitchenRoutingPanel.test.tsx)：保存/共享设备/排序、真实合同预览、后端失败不伪造成功、`passes the existing full Combo child Remove snapshot through preview, including its virtual parent identity`。 |
| F-DIAG | [TemplatePrintDiagnosticsPanel.test.tsx](../../../frontend/src/features/owner-admin/TemplatePrintDiagnosticsPanel.test.tsx)：pre-Job、remount 重查、异步故障刷新、Update batch 隔离、读取失败不改变订单成功、旧 backend404不影响原 coordinator。 |
| F-OFFLINE | [localDrafts.test.ts](../../../frontend/src/offline/localDrafts.test.ts)、[orderOutbox.test.ts](../../../frontend/src/offline/orderOutbox.test.ts)、[orderOutboxProcessor.test.ts](../../../frontend/src/services/orderOutboxProcessor.test.ts)：scope、本地状态/身份、冷启动可恢复、超时/网络/5xx退避、冲突停止、单飞、仅服务端确认后成功。 |
| H | [test_template_platform_http.py](../../../backend/test_template_platform_http.py)：E-HTTP 41项真实 API 验收；凭据仅进程内读取，精确 base URL allowlist/禁止跳转，合成数据独立命名，不清库/删除订单。 |

## 3. M0 菜单需求覆盖

本节自动化 PASS 对应 E-BE / E-FE；真实 API PASS 对应 E-HTTP@96。未单独网页验证的子操作不能因组件测试通过而记作 Owner 网页已完成。

| Owner 需求 | 自动化与真实证据映射 | 当前结论及边界 |
|---|---|---|
| Owner/Manager 创建、编辑本店菜单 | B-MENU；H `owner_own_store_management`、`manager_own_store_management`、`owner_manager_create_no_group_items_atomic_catalog` | `IMPLEMENTED_AND_TESTED / PASS`：真实认证、本店持久化和 catalog[]；完整网页创建/编辑清单待 Owner。 |
| 系统预设 Spicy/Add-on/Remove/Size/Combo | B-MENU `presetLibraryIsEmptyOfCustomerOptionsAndLibraryWritesDropRuntimeIdentities`；F-MENU/F-POS；H `preset_custom_per_item_copy_price_default_two_level_order` | `IMPLEMENTED_AND_TESTED / PASS`：复用当前类型/叶子/控件。预设不带真实客户选项；SYNTHETIC 示例不是餐厅默认菜单承诺。 |
| 自由命名 Custom 组和普通 Option | F-MENU `creates a freely named custom group with its own option/default and enabled state`；B-MENU；同上 H case | `IMPLEMENTED_AND_TESTED / PASS`：名称、默认、差价无需新增专属 Enum/Modal；不含条件 DSL、重量/促销。 |
| 从库选预设/Custom、复制到 A/B 后独立改价/默认 | B-MENU `libraryCopiesHaveIndependentOptionIdsDefaultsAndPricesPerItem`；F-MENU clears IDs/copy；H `preset_custom_per_item_copy_price_default_two_level_order` | `IMPLEMENTED_AND_TESTED / PASS`：item IDs分开，A编辑不改B/库；使用原叶子作为金额 authority，不伪造只配 metadata 就能绕开价格。 |
| 直接建组、移除组、显式无组、历史叶子保留 | B-MENU `emptyGroupsMaterializeQuickAddMetadataAndRetainInactiveHistoricalLeaves`；F-MENU explicit no-group、失败保护 | `IMPLEMENTED_AND_TESTED / PASS`：新显式[]与旧null/缺字段分开；不删除历史订单快照。 |
| 两级排序、默认、必选、启停 | B-MENU ordering/default/cardinality/disabled；F-MENU/F-POS；H copy/order case与 `server_rejects_required`、`server_rejects_single`、`server_rejects_single_quantity` | `IMPLEMENTED_AND_TESTED / PASS`。多选没有新增业务上限，但技术数量、重复身份和金额精度仍校验。 |
| 服务端金额、scope、metadata 验真 | B-MENU；H `server_rejects_base_price`、`option_price`、`item_scope`、`unknown_item`、`negative_item`、`category_forgery`、`sku_forgery` | `IMPLEMENTED_AND_TESTED / PASS`：负例要求拒绝且无新增 Order/Job/intent；不是信任客户端价格/分类/SKU。 |
| 无交互组选项 QuickAdd、必选不得绕过 | B-MENU empty/required；F-MENU `adds an arbitrary category group-free spring roll directly, preserving legacy category behavior`及 `never bypasses an interactive or empty required group`；H atomic catalog[]；E-UI-LOCAL | `IMPLEMENTED_AND_TESTED / PASS`：本地真实网页 SpringRoll无空Modal可直接入车。公共域名尚未验证。 |
| Combo 复用默认/虚拟 ID/父子/价格/linked side | B-COMBO、B-PRICE、F-POS；H `existing_combo_virtual_defaults_linked_child_preview_and_real_jobs`、`combo_child_remove_full_snapshot_preview_and_real_child_ticket`；E-SQL真实 WOK/COLD tasks | `PARTIAL / PASS for bounded existing behavior`：side仅 `LEGACY_COMBO_SIDE_TASK` + active COLD linked item；egg仅 `NO_KITCHEN_TASK`。新通用LINKED等未实现，不能泛称任意 Combo 都支持。 |
| Combo 非法/缺metadata/无linked fail closed | B-COMBO；H `known_combo_virtual_id_missing_code_group_rejected`、`unsupported_combo_behavior_fails_closed_no_business_artifacts`、`legacy_combo_side_without_linked_item_rejected_no_business_artifacts` | `IMPLEMENTED_AND_TESTED / PASS`：第41项先 NO_TASK 清链，再LEGACY+null，GET证实持久null，preview/Submit均400 `TEMPLATE_COMBO_LINKED_SIDE_REQUIRED`，无业务产物，finally恢复合成配置。 |

## 4. M0 厨房分单与无副作用预览覆盖

| Owner 需求 | 测试/实际证据 | 结论 |
|---|---|---|
| 任意票组名、数量、排序、启停、逻辑目标 | B-PLAN deterministic/sort/disabled/identity；F-ROUTE保存；H一组/三组preview及真实提交 | `IMPLEMENTED_AND_TESTED / PASS`。一组与三组实际验证；更大组数仅通用 planner规则覆盖，无大规模性能容量证明。 |
| 分类分配、单品覆盖、同item多组、明确空覆盖 | B-PLAN `itemOverrideReplacesBothCategoryAndItemMembership`、`explicitEmptyOverrideDoesNotFallBackOrSilentlyLoseCoverage`、`explicitMultipleGroupsDoNotDuplicateAnEntryOrMergeTickets`；H `three_group_shared_mock_printer_and_item_override_preview` | `IMPLEMENTED_AND_TESTED / PASS`；不猜测未分配去向，不静默fallback。 |
| 多逻辑组共享设备仍独立票据 | B-PLAN `threeGroupsSharingOnePrinterStaySeparateAndSortByOrderThenStableCode`；F-ROUTE；H三组preview及真实多票 | `IMPLEMENTED_AND_TESTED / PASS`：共享MOCK设备不按打印机自动合并。 |
| Combo linked child归属、子去料不串父票 | B-PLAN Combo source identity；F-ROUTE full existing snapshot传递；H Combo/child Remove；E-SQL `expected_child_remove_only_on_actual_cold_task`、`expected_child_remove_only_on_cold_intent_and_job` | `IMPLEMENTED_AND_TESTED / PASS`，限第3节Combo边界；parent/code/group原快照被保留并服务端验证。 |
| 最终票内容、目标、coverage、menu/routing revision可见 | B-PLAN frozen names/free instruction/control chars；F-ROUTE后端内容展示；H previews与最终Job内容核对 | `IMPLEMENTED_AND_TESTED / PASS`：未用客户端HTML卡片替代后端渲染；当前菜单/路由revision不代表完整不可变catalog history。 |
| 未匹配、停用、无设备/目标Warning | B-PLAN `noMatchReportsUnassignedWithoutInventingTicketDestination`、disabled/missing printer/target；F-ROUTE unbound提示；H missing printer | `IMPLEMENTED_AND_TESTED / PASS`：已配置但缺设备有真实HTTP覆盖；各异常组合以所列unit为范围，无硬件故障实测。 |
| 预览无Order/Job/intent、TCP/Uber副作用 | H `pure_preview_has_no_order_job_intent_writes_one_group`及三组/Combo preview前后scope API集合比较；B-PLAN pure function；B-SAFETY/隔离守卫 | `IMPLEMENTED_AND_TESTED / PASS`：真实API集合没有新增；无真实endpoint、Test外部worker禁用。不能只凭可见列表不变推断所有部署都无外联。 |

## 5. M1 持久后端闭环与可靠性覆盖

| Owner 需求 | 测试/实际证据 | 结论 |
|---|---|---|
| 真Order→事务Outbox→多真实PrintJob | H `real_order_one_kitchen_frontdesk_and_idempotency`、`real_order_three_kitchens_frontdesk_and_idempotency`；B-BOUND；E-SQL frozen intent/oneJob | `IMPLEMENTED_AND_TESTED / PASS`：真实独立PG/Backend；非测试打印endpoint、非静态demo。新厨房MOCK分支复用原JobService与状态操作。 |
| 一组一稳定业务身份，重复提交不重复票/订单 | B-IDEM/B-PRINT；H正常订单顺序重放、`fresh_concurrent_submit_single_order_three_intents_four_jobs`（4并发同key→1Order/3intent/4Job）、`same_idempotency_key_different_payload_conflict` | `IMPLEMENTED_AND_TESTED / PASS`：并发/冲突是实际HTTP；不重写旧去重算法。 |
| Update只本次新增批次 | B-PLAN `updateRendersOnlySuppliedBatchSnapshotsWithoutReintroducingOldItems`；B-PRICE；B-PRINT `frontdeskUpdateDispatchRendersOnlyItemsFromRequestedBatch`；H `update_only_new_batch_and_idempotency` | `IMPLEMENTED_AND_TESTED / PASS`：一个新增B→一厨房票+一原前台加单票；重放batch不新增Job，不重印原A/C。 |
| C未绑定，订单成功、A/B继续，C不假PRINTED | B-BOUND preJob；F-DIAG；H `legal_order_success_other_groups_continue_durable_missing` | `IMPLEMENTED_AND_TESTED / PASS`：C为UNRESOLVED/null Job，A/B PRINTED；无全局旧assignment挡住新MOCK组处理。真实generic Pad路径未验证。 |
| 失败前Job意图、内容与原因可重查 | F-DIAG remount/query/状态；H missing/failed/uncertain刷新诊断；E-SQL真实Outbox与Job | `IMPLEMENTED_AND_TESTED / PASS`：复用原Outbox保存intent及冻结文本，不建第二队列。浏览器退出重登录完整业务链仍待Owner清单，不把组件remount当重登录实测。 |
| 异步故障刷新、成功与票失败分开 | F-DIAG `refreshes a later asynchronous failure after successful server confirmation`、`keeps order success distinct from a status-read failure and never writes a job`；H FAILED/UNCERTAIN | `IMPLEMENTED_AND_TESTED / PASS`：成功响应后才发现的异步故障通过持久状态刷新；不声称初始响应已知道未来故障。 |
| UNCERTAIN不自动重发、其他组继续 | B-BOUND `terminalUncertainResultIsNeverReopenedOrReattached`；B-PRINT Pad `expiredPrintingIsNotReclaimable`；H `legal_order_success_other_groups_continue_durable_uncertain`对照观察窗口内Job数量/attempt不增 | `IMPLEMENTED_AND_TESTED / PASS`：MOCK注入及自动化状态协议范围；不是纸张输出结果不确定的真实设备实验，也不是无限时间证明。 |
| 前台完整初次票、增量Update、原金额/税/快照 | B-PRICE/B-LEGACY/B-PRINT；H首次前台三商品完整、Update无旧行、`historical_snapshots_immutable_zero_price_custom_kitchen_instruction` | `PARTIAL`：完整商品/金额及旧语义 PASS；自由CUSTOM零价label遗漏见第7节，不能报任意自定义明细完整支持。 |
| 当前菜单变更不改历史订单/票据 | B-PRICE；H同上snapshot case，保存当前option改名/价后核对旧OrderLine与旧Job文本不变 | `IMPLEMENTED_AND_TESTED / PASS`：已接受订单快照稳定；完整catalog历史窗口/TrustedSelection仍首版范围外。 |

## 6. 旧功能回归、弱网与环境边界

| 范围 | 证据及实际层级 | 状态/未覆盖 |
|---|---|---|
| null/缺option_groups旧菜单路径 | F-MENU `keeps null and missing metadata in the exact legacy customization path`；B-MENU `legacyAbsentGroupMetadataKeepsLegacyValidationResponsibility`及 feature disabled；原ItemCustomizationModal/Combo/价格测试仍在401内 | 自动化 `PASS`；三家门店真实菜单/终端全量回归 `NOT_RUN / OUT_OF_SCOPE`，未把合成fixture说成真实门店完全等价。 |
| 原GRAB/HOT/FRONTDESK文本、表号、快照及字节 | B-LEGACY；B-PRINT Job `frozenWholeLineSizeMarkupKeepsItsExistingPadDirectBytes`、Dispatcher frozen reprint/Table header、[PROTECTED_BASELINE.sha256](PROTECTED_BASELINE.sha256) | 自动化 `PASS`；实体纸张/编码/机型/Pad实机 `NOT_RUN`。受保护源码未改的保全证明与行为测试互补，不能互相替代。 |
| 旧Outbox/调度/去重/PAD协议 | B-PRINT processor8、Job27、Dispatcher35、Pad21；如 `samePrinterEventsKeepFifoOrder`、`differentPrinterEventsCanDispatchConcurrentlyWithinExecutorBound`、`persistedDispatchSourceReturnsExistingJobWithoutCreatingDuplicate`、claim/start token/expiry校验 | 自动化 `PASS`；本次未改既有协议/状态机，未真实Pad领单，不宣称所有硬件问题解决。 |
| 草稿、IndexedDB Outbox、弱网/重放 | F-OFFLINE有状态/存储边界及网络失败模拟；B-IDEM丢首响应后重试；E-HTTP真实顺序/并发重放；E-UI-LOCAL实际草稿刷新后仍在 | `PARTIAL`：这些既有自动化及真实HTTP重放已PASS；**未做真实无线断连、网络限速/高丢包、断电、浏览器杀进程或长时间离线**。组件/存储adapter mock不能证明真实IndexedDB所有故障恢复；不以MOCK失败注入冒称无线实验。 |
| Org/Store/角色隔离 | H anonymous拒绝；Owner/Manager own-store与foreign-Org读/写拒；foreign-order诊断拒绝；B-MENU跨店item/option/源copy及invalidstation；E-SQL两独立Org及无scope错行 | 指定HTTP/SQL负例 `PASS`；不是完整攻击面渗透测试。Phase0/1未解决的STOMP/设备凭据/SaaS安全问题不因本次通过而关闭。 |
| Test空库迁移/Bootstrap | Root实际独立PG18 FlywayV1–V40及Hibernate validate成功；B-SAFETY/B-MENU启动前拒绝；E-SQL合成Org/Store | 本地Test `PASS`；未对Production/Staging迁移、导入真实交易或恢复生产备份。完整新客户onboarding流程不由本次bootstrap自动宣告完成。 |
| Test部署网络/存储/资源与受保护共享入口 | E-GUARD59项：真实Compose规范化、tmpfs逗号修复、独立内部网络/前端入口、无backend/db公开port、volume/network归属、readonly/资源界限、relay/监听归属、nginx漂移与锁等 | 59项已执行 `PASS`；后续62项ops-only扩展/实际远端部署最终结果 `PENDING_ROOT_UPDATE`。本报告不执行部署，不把静态守卫通过当HTTPS业务已可用。 |
| CORS local/Test精确Origin、evil拒绝 | H `exact_browser_origin_login_local`、`exact_browser_origin_login_test_https`、`evil_browser_origin_login_rejected`，含真实preflight/login | 本地服务收到指定Origin `PASS`；HTTPS域名DNS、TLS、反代、远端登录/业务仍 E-DOMAIN `PENDING`。 |
| 实体打印/Android/Uber/支付/旧店迁移/生产恢复 | 本次无对应真实执行授权；Test MOCK ceiling和endpoint禁用是安全限制证据 | `NOT_RUN / OUT_OF_SCOPE`。不触真实打印、Uber、支付，不接三家旧店，不做备份恢复；没有“真实全流程PASS”。 |

### 117 个条件跳过项

E-BE 中下列套件依赖未配置的外部Postgres等条件，本次未执行；实际独立Test迁移/HTTP/SQL只补充所列功能证据，不能把这些套件自动改成PASS。

| 套件 | skipped |
|---|---:|
| `UberEatsPostgresIntegrationTest` | 72 |
| `StoreAddonPostgresMigrationTest` | 2 |
| `StoreRemoveBaselinePostgresTest` | 4 |
| `StoreRemovePostgresTest` | 8 |
| `UberStoreModuleMigrationTest` | 1 |
| `StoreDailyClosePostgresIntegrationTest` | 8 |
| `OwnerStoreMenuClonePostgresIntegrationTest` | 3 |
| `PrintingReliabilityPostgresMigrationTest` | 1 |
| `PrintingDisplayRuleLifecyclePostgresIntegrationTest` | 1 |
| `DiningTableSoftDeletePostgresTest` | 12 |
| `SplitLabelMigrationPostgresTest` | 1 |
| `SplitTableLabelPostgresIntegrationTest` | 4 |
| **合计** | **117** |

## 7. 已知限制与证据解释

1. **旧前台零价CUSTOM label缺失**：E-HTTP `historical_snapshots_immutable_zero_price_custom_kitchen_instruction`明确记录 `OMITTED_BY_PROTECTED_LEGACY_RENDERER_KNOWN_LIMITATION`，新厨房票包含该制作指令。原前台 Renderer只对普通收费modifier走正差价分支；既有特定语义分支另有规则。此处为 `PARTIAL / KNOWN_LIMITATION`。后续候选 [CR-RECEIPT-CUSTOM-001](CR_RECEIPT_CUSTOM_DETAIL.md) 为 `PROPOSED / NOT_AUTHORIZED / NOT_IMPLEMENTED`，不得偷偷改Renderer、伪造收费或塞notes后宣告支持。
2. **Combo支持范围受旧Task语义约束**：COLD linked side及其Remove父子通过真实票据/Task证据；不支持的新behavior明确拒绝。真实KDS模块未开启，不曾通过启用KDS绕过验收边界；E-SQL直接只读核对实际Task。
3. **旧Mock nullable mode 的测试oracle修正**：历史错误receipt `http-local-complete-guarded.json`保留；“所有Job.execution_mode必须MOCK”的断言对未改旧前台路径错误。最终严格规则是新TEMPLATE_KITCHEN必须MOCK，实际printer元数据无endpoint；旧FRONTDESK仅在当前StoreMOCK、运行ceiling MOCK/DISABLED、无Job printer/endpoint且相应来源/SQL补证下允许null。E-SQL证明旧路径；这不是修改产品来适配错误expected，也不放过未知module/REAL/PAD_DIRECT。
4. **SQL与HTTP版本及订单区分**：b64 SQL订单80/81是真实COLD task证据；96 HTTP订单101/102已真实通过票据及子去料，但该receipt中的SQL followup仍是待补，本文不篡改它。二者运行同一已验证Jar，但不同实际行的后验查询不能混写。
5. **无覆盖率百分比推断**：测试用例数量不是行覆盖率、分支覆盖率或所有业务组合覆盖率。本次没有生成可信的全仓coverage百分比；本表列出已测和未测边界，不填100%。

## 8. 待 Root 最终补充的公共域名证据

当前 `PENDING`，以下位置只留证据入口，不预填结果：

| 项目 | 最终填写 |
|---|---|
| 最终部署Commit / 镜像digest / live runtime build_sha / Jar SHA | PENDING_ROOT_UPDATE |
| 62项ops-only新增守卫实际执行与独立复审 | PENDING_ROOT_UPDATE；59项已执行结果保留 |
| Test DNS、TLS证书、反向代理、健康与隔离证明 | PENDING_ROOT_UPDATE |
| 公共域名Owner/Manager登录及真实41HTTP receipt | PENDING_ROOT_UPDATE |
| 公共域名网页菜单、QuickAdd、提交/多MOCK票、Update、C未绑定Warning | PENDING_ROOT_UPDATE |
| 网页刷新/退出重登录后诊断持久性及截图 | PENDING_ROOT_UPDATE |
| Owner业务验收结论 | 参考 [OWNER_ACCEPTANCE_CHECKLIST.md](OWNER_ACCEPTANCE_CHECKLIST.md)，`OWNER_NOT_RUN`，等待Owner执行 |

仅在这些动作实际完成且证据绑定最终版本后更新相应行。生产三旧店、Staging和Android当前外部状态不能从本次Test证据推定；本报告不声称这些环境未被任何其他主体改变。

## Subsequent confirmed security repair

After the public41-case run, IAB and direct HTTPS found P6-SEC-OVERVIEW-001 in the old platform overview metadata arrays. Root added6 Owner/Manager/authorized-multi-store/ADMIN/denial/template-source serialization tests without weakening leakage assertions. New full JDK21 minimal-environment `mvn -q verify`:1310 total,1193 executed successful,117 conditional skips,0 failures/errors; original fixture failure is retained. HTTP runner now has45 cases including actual own overview200 with scoped metadata and foreign overview403 for both roles. The45-case new public run remains PENDING until deployment; prior41 and19SQL evidence remain historical, not silently relabeled. See SECURITY_OVERVIEW_SCOPE_FIX.md.
