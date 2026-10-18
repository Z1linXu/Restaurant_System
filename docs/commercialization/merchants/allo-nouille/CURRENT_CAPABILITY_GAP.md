# 当前商业版能力与Allô差距

> 历史规划快照：2026-10-18 Owner Q1–Q9 与已授权本地试点的实际行为，以 [IMPLEMENTATION_RECORD](IMPLEMENTATION_RECORD.md) 为准；本文件原有待批准/未决项不再阻断已明确授权范围。

2026-10-18，STATIC AUDIT / PLANNED ONLY。结论来自当前代码，不将架构目标、既有测试文件或上轮Demo成功当作Allô已验收。菜单依据：[原件分析](MENU_PDF_ANALYSIS.md)。

## 版本与保护范围

worktree为 `/Users/xuzilin/.codex/worktrees/commercialization-phase1-3/Restaurant_System`，branch `codex/commercial-phase3-foundation`，HEAD及origin同名分支均为 `cb6ff2e91409c3445d5a5e765e68f93599838638`。fresh origin/main为 `06bac51ddc0a829a9aa1106f07ef886572efbb65`，没有把本商业化分支合入main。

GitHub只读核实PR275→276→277→278全部OPEN/Draft、依赖base相连；278 head为上述cb6ff2e。本轮不创建/修改/合并PR。开始时SYSTEM_DOCUMENTATION、CURRENT_STATE及LOCAL_INTERACTIVE_DEMO说明存在上一轮未提交工作，保留；不切换脏主工作区、不提交未知内容。

[P3验收](../../PHASE3_ACCEPTANCE_REPORT.md)保留backend1050/frontend363等历史自动测试证据；G3-04视觉NOT RUN、G3-08未闭合。本轮没有运行应用测试、SQL或业务HTTP请求；[CURRENT_STATE](../../../governance/CURRENT_STATE.yml)里的正式验收暂停和P4未授权边界不变。旧 `docs/API_bilingual.md` 不存在，实际API合同为 [doc/API.md](../../../../doc/API.md)，schema为 [DatabaseDesign](../../../../doc/DatabaseDesign.md)。

## 标记含义

READY=相应原语已有实现；CONFIG_REQUIRED=原语能用但尚未创建Allô配置；CORE_GAP=端到端语义缺失；PROFILE_REQUIRED=私有商家配置资产待建立，不等于要加Core分支；UNKNOWN=PDF/代码不能证明业务决定或实际环境结果。同一需求可分别标基础原语和缺失约束，不能把局部READY写成整项完成。

## 代码与既有测试证据索引

路径均为真实文件，行号按cb6ff2e观察，方法名用于定位。测试文件仅静态阅读，未在本轮执行。

| ID | 实际源码 / 证据 | 本次核实事实 |
| --- | --- | --- |
| E01 | [OwnerMenuManagementController](../../../../backend/src/main/java/com/restaurant/system/menu/controller/OwnerMenuManagementController.java)、[PlatformAdminController](../../../../backend/src/main/java/com/restaurant/system/platform/controller/PlatformAdminController.java)、[MenuItem](../../../../backend/src/main/java/com/restaurant/system/menu/entity/MenuItem.java) | 分类/工位CRUD、Store商品CRUD；商品含Store/category/station/base_price，名称只有zh/en |
| E02 | [MenuServiceImpl](../../../../backend/src/main/java/com/restaurant/system/menu/service/impl/MenuServiceImpl.java)、[useMenuCatalog](../../../../frontend/src/hooks/useMenuCatalog.ts) L257–350 | catalog真实Store投影；只有存在active size才建required sizes，无size则undefined；无option不自动造碗型 |
| E03 | [StorePricingPolicyServiceImpl](../../../../backend/src/main/java/com/restaurant/system/menu/service/impl/StorePricingPolicyServiceImpl.java) L150–240/L336–360、[既有测试](../../../../backend/src/test/java/com/restaurant/system/menu/service/impl/StorePricingPolicyServiceImplTest.java) | 专用Size接口只支持SMALL/REGULAR/LARGE，enabled列表不能空；同Store的Combo option统一镜像一个combo_delta |
| E04 | [OwnerMenuOptionServiceImpl](../../../../backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java) L140–235、[既有测试](../../../../backend/src/test/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImplTest.java) | 可配置noodle_type/普通Remove等；无通用min/max/default规则实体；SIZE/COMBO/ADD_ON价格写入受专用入口限制 |
| E05 | [StoreAddonService](../../../../backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonService.java) L95–145/L440–451、[Resolver](../../../../backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java)、[既有测试](../../../../backend/src/test/java/com/restaurant/system/menu/addon/StoreAddonServiceIntegrationTest.java) | Org canonical code→Store名称/非负两位价格→Item eligibility；零价允许，当前值从catalog解析、历史值不回写 |
| E06 | [StoreComboGroup](../../../../backend/src/main/java/com/restaurant/system/menu/combo/StoreComboGroup.java)、[StoreComboComponent](../../../../backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponent.java)、[ServiceImpl](../../../../backend/src/main/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImpl.java) L38–48/L463–540 | 有Store组、组件、linked_menu_item_id和默认；只EXACTLY_ONE/OPTIONAL_ONE，组件无份量/数量字段；同Store active引用验证 |
| E07 | [Combo既有测试](../../../../backend/src/test/java/com/restaurant/system/menu/service/impl/StoreComboConfigurationServiceImplTest.java) `updateCanCreateDynamicDrinkGroupAndValidateSnapshotSelections` / `rejectsCrossStoreLinkedMenuItemMapping`、[useMenuCatalog测试](../../../../frontend/src/hooks/useMenuCatalog.test.ts) `maps dynamic combo groups...` | 动态组/关联身份能保存和投影，不等于已生成关联菜品的独立任务；前端仍有legacy固定负ID与FNV派生ID |
| E08 | [ItemCustomizationModal](../../../../frontend/src/features/ordering/components/ItemCustomizationModal.tsx) L14–38/L185–210/L417/L481–505、[useOrderSessions](../../../../frontend/src/hooks/useOrderSessions.ts) L51–108、[noodleTypePresentation](../../../../frontend/src/features/ordering/components/noodleTypePresentation.ts) | 面型单选与第一个默认、free toggle只识别“加香菜/加葱”，其他加购用无上限stepper；旧面型code带编号后缀；价格使用JS Number |
| E09 | [OrderController](../../../../backend/src/main/java/com/restaurant/system/order/controller/OrderController.java)、[IdempotentOrderSubmissionController](../../../../backend/src/main/java/com/restaurant/system/order/controller/IdempotentOrderSubmissionController.java)、[Idempotent实现](../../../../backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java) L152–242 | 普通draft→submit与幂等提交；当前revision仅要求存在active size/soup类型时选齐，不全局要求所有面有SIZE；非Add-on旧快照漂移多为log，不是完整可信旧价政策 |
| E10 | [OrderServiceImpl](../../../../backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java) L1527–1596/L1808–1822/L2230–2367、[ComboComponentSemanticResolver](../../../../backend/src/main/java/com/restaurant/system/printing/semantic/ComboComponentSemanticResolver.java) L12–35 | 主商品按station创建任务；direct serve取决于DRINK等固定category；套餐副菜任务只解读三项legacy语义并固定COLD，未遍历所有linked component |
| E11 | [GrabReceiptRenderer](../../../../backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java) L25–42、[NeutralKitchenReceiptContent](../../../../backend/src/main/java/com/restaurant/system/printing/renderer/NeutralKitchenReceiptContent.java)、[HotKitchenPrintEligibilityService](../../../../backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java)、[Neutral打印测试](../../../../backend/src/test/java/com/restaurant/system/printing/renderer/NeutralProfilePrintingTest.java) | Neutral完整冻结标签/空默认字典已实现；GRAB/HOT仍限制既定station codes，不能任意工位；不是按Allô名称硬编码renderer |
| E12 | [MerchantConfigurationService](../../../../backend/src/main/java/com/restaurant/system/merchant/MerchantConfigurationService.java) L34/L54–69/L152–161、[合同](../../PHASE3_CONFIGURATION_CONTRACT.md) | PUT严格四字段schema/revision/brand/locale；CAD/tax/Profile不可写；locale是元数据，不是三语labels支持 |
| E13 | [StoreProfileAccessService](../../../../backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileAccessService.java)、[NeutralBusinessCreationPostgresTest](../../../../backend/src/test/java/com/restaurant/system/merchant/NeutralBusinessCreationPostgresTest.java)、[ProfileIsolationTest](../../../../backend/src/test/java/com/restaurant/system/owner/profile/StoreProfileIsolationTest.java) | 私有Profile精确Org Owner；Neutral空白原子创建已有；新建Allô私有Profile/导入不是已存在通用executor |
| E14 | [StoreAccessService](../../../../backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java)、[PlatformResourceAuthorization](../../../../backend/src/main/java/com/restaurant/system/platform/controller/PlatformResourceAuthorization.java)、[MerchantConfigurationHttpTest](../../../../backend/src/test/java/com/restaurant/system/common/auth/MerchantConfigurationHttpTest.java) | 服务端实际Store/Org scope，ADMIN无租户绕过；metadata strict/CAS/foreign拒绝 |
| E15 | [MenuRevisionServiceImpl](../../../../backend/src/main/java/com/restaurant/system/menu/service/impl/MenuRevisionServiceImpl.java)、[menuCache](../../../../frontend/src/offline/menuCache.ts)、[useDraftOrder](../../../../frontend/src/hooks/useDraftOrder.ts)、[environmentIsolation测试](../../../../frontend/src/offline/environmentIsolation.test.ts) | 复用menu/config revision/hash、DB5/account/Org/Store namespace和冻结payload；新增字段必须参与hash与版本兼容，不新建平行缓存 |
| E16 | [TaxCalculator](../../../../backend/src/main/java/com/restaurant/system/common/pricing/TaxCalculator.java) L8–22、[前端tax](../../../../frontend/src/utils/tax.ts)、[useDraftOrder](../../../../frontend/src/hooks/useDraftOrder.ts) L62–86 | 后端BigDecimal、0.14975、subtotal税额HALF_UP两位；前端Number运算，不能宣称全链十进制确定性；不是每商品税分类引擎 |
| E17 | [orderingCustomizationRules](../../../../frontend/src/features/ordering/orderingCustomizationRules.ts)、[OrderingPage](../../../../frontend/src/features/ordering/OrderingPage.tsx) L338–380 | Quick Add仅特定drink/item type或FRIED条件；有普通商品不等于全品类Quick Add配置化 |
| E18 | [OwnerStoreProvisioningServiceImpl](../../../../backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java) L112–205、[Profile design](../../PHASE3_PROFILE_DESIGN.md)、[Onboarding design](../../MERCHANT_ONBOARDING_DESIGN.md) | Neutral可建空Store；非Neutral路径仍依赖既有Profile+Master契约。通用manifest/diff/dryrun/execute在P6设计中，不能直接声称可安装任意新Profile |

## 所有要求的当前能力

| 能力 | 状态 | Allô适用结论 | 证据 |
| --- | --- | --- | --- |
| Menu Categories / Items / 固定单品价 | CONFIG_REQUIRED | 可建五类32品、stable SKU、Store-owned base_price；本轮未创建 | E01/E02 |
| Menu Catalog | READY | 同Store分类/商品/option/summary/revision；新规则和fr字段尚不在schema | E02/E15 |
| Canonical Add-on / Store Overrides | CONFIG_REQUIRED | 可表达15项零价/收费项与单品资格；组织code不能因为同名误用 | E05 |
| Modifier Groups / Selection Rules | CORE_GAP | 当前option_group标签不是通用规则实体；没有全链min/max/重复/额度校验 | E04/E06/E08/E09 |
| Default Options | CONFIG_REQUIRED / CORE_GAP | 旧模型有排序默认和Combo组默认；没有通用显式no-default及同契约backend required验证 | E06/E08/E09 |
| Combo Groups / Components / Store Configuration | CONFIG_REQUIRED / CORE_GAP | 能配置组、关联、单选/默认；不能把它当完整套餐价格/份量/履约 | E06/E07/E10 |
| Product Pricing | READY / CORE_GAP | 单品/普通Add-on可用；per-offer固定总价、税分类、前端精确数值仍缺 | E03/E05/E16 |
| Customize Modal | CONFIG_REQUIRED / CORE_GAP | 条件显示size、面型、加购/Combo；C5计数/free limits/商家中性展示需补 | E02/E08 |
| Order Creation / Submission | READY / CORE_GAP | 原事务/幂等继续用；新组约束/固定套餐权威校验尚缺 | E09 |
| Order Snapshots | READY / CORE_GAP | 现zh/en/价/option快照可冻结；fr、套餐结构、份量、规则版本快照待扩展 | E09/E15 |
| Kitchen Task Generation | CORE_GAP | 主商品路径已有；所有套餐组件按自身station/portion生成任务未实现 | E10 |
| Printing Renderers | READY / CORE_GAP | Neutral完整标签适配可复用；任意station、组件数量及语言不完整 | E11 |
| Restaurant Profiles | PROFILE_REQUIRED | ALLO_NOUILLE_PROFILE私有配置资产设计；不能改Neutral/兰州或公开给其他Org | E13/E18 |
| Store Configuration | CONFIG_REQUIRED | 仅品牌/locale可配置，不能向现四字段PUT塞menu/Profile/min-max/tax | E12 |
| Store / Organization Isolation | READY | 复用实际scope、同店引用、Private根权限；新路径仍须补负向测试 | E13/E14 |
| Menu Revision / Offline Cache | READY / CORE_GAP | 现版本/恢复底座可复用；新规则/套餐/语言加入hash及旧价政策尚缺 | E09/E15/E16 |
| 真实Allô税/工位/语言/免费额度 | UNKNOWN | 菜单不是部署/设备/BOM/税务事实，需Owner确认 | E10/E12/E16 + PDF |

## 16项差距与建议

| Requirement | Current Support | Gap | Recommended Change | Risk | Phase |
| --- | --- | --- | --- | --- | --- |
| Optional Size Group | CONFIG_REQUIRED：新商品不建size即可无碗型 | 已配size不能用空列表关闭；缺Allô全链无size用例 | 新Allô保持无SIZE；仅为通用“清空已有Size”补显式optional语义，不删兰州 | 中，老菜单默认/缓存 | P4-MODIFIER-02；AN-PR1 |
| Thin / Thick Noodle Selection | 可建noodle_type两项/0价，默认第一项 | 无generic required/min-max/no-default；旧code附兰州编号 | 用商家局部stable code，Owner确认默认，再通用组校验；不靠SKU | 中 | P4-MODIFIER-02；AN-PR1 |
| Free Add-on | 0价catalog已支持 | 次数/数量上限和toggle语义不通用 | 复用Add-on SoT，增加显式selection/quantity/free-allowance规则；未知限制不执行 | 中 | P4-MODIFIER-02；AN-PR1 |
| Category-specific Add-on Eligibility | 单item eligibility已存在 | 无声明式category继承规则，但不阻断有限菜单配置 | 将菜单类别资格展开为现item assignments；不再造Add-on库或先开发category引擎 | 低，更新需diff | 配置工作AN-CFG，无新feature PR |
| Fixed-price Combo | 全店combo_delta | 面类需+4，C1–4需+3，C6需+2，不能一个delta | 每offer固定1999 cents，服务端权威报价；禁伪装负价Add-on/重复计组件价 | 高，金额 | P4-PRICE-04；AN-PR3 |
| Combo Main Item Eligibility | 每item combo_allowed开关已有 | 同店两种offer没有per-offer主品集合 | 复用开关，补offer↔main明确引用；B4排除不影响其单点 | 高，绕UI直接API | P4-COMBO-03；AN-PR2 |
| A1–A9 Appetizer Selection | dynamic单选组/linked item已有 | 当前所有主菜读同一组集，未真正拆全部关联任务 | Noodle offer独享appetizer组，显式A1–A9；真实child/task/BOM | 高，厨房漏单 | P4-COMBO-03 / P5-KITCHEN-01；AN-PR2/5 |
| C5 Exclusion | peritem combo_allowed可关 | Rice offer资格需服务端绑定，不能只隐藏控件 | C5 excluded IDs；C6 included；拒伪造主品/offer组合 | 中 | AN-PR2 |
| Fixed Combo Component Quantity | linked item有身份 | 无份量/qty；A2整份是2条 | Rice combo引用受控单条组件规格，整数量1，不用0.5 A2 | 高，实际出品数量 | AN-PR2/5 |
| C5 Exactly Two Selections | 无通用恰好二选 | EXACTLY_ONE/OPTIONAL_ONE不够；重复政策未知 | generic min=max=2，计数/重复开关由明确配置决定；蛋白不是整盘C1–C4 | 高 | AN-PR1 |
| Trilingual Menu Display | name_zh/name_en与locale元数据 | 无name_fr/翻译/法语历史快照 | 最小nullable法语字段+同投影/冻结；保留zh/en，独立员工UI语言 | 中 | P4菜单兼容 / P5语言；AN-PR4 |
| Configurable Kitchen Routing | 单商品station引用已有 | direct-serve/category与GRAB/HOT依赖固定code，组件路由缺失 | 扩展同Store fulfillment/role映射，先MOCK；真实工位待确认 | 高 | P5-KITCHEN-01；AN-PR5 |
| Neutral Printing | 已有完整快照/空字典 | 不等任意工位、三语、份量显示 | 复用print job/claim/lease/renderer，补明确通用上下文和语言 | 高，重印兼容 | P5-PRINT-02；AN-PR5 |
| Merchant Configuration Import | 逐资源API+既有Profile/ledger存在 | 无通用Allô importer/dryrun/补偿或apply到现Neutral接口 | P6复用原profile/授权/事务，新增受控apply/diff/idempotency；不加系统内PDF AI | 高，半图/越权 | P6-IMPORT-01；AN-PR6 |
| Store Isolation | READY现Core/Private边界 | 新offer/portion/translation/import refs须同scope验证 | 复用现guard，拒foreign真实实体、缺membership、stale-owner；不信URL | 高 | 横跨全部PR，非新隔离框架 |
| Pricing and Offline Compatibility | BigDecimal后端/DB5冻结/稳定key已有 | frontend Number、任意旧价、rule/offer/portion revision缺口 | minor-unit或定点十进制、受信有效版本；旧TTL待Owner决策；已提交payload不改 | 高，重复/错价 | P4-PRICE-04；AN-PR3 |

因此“只关闭Size+配置Add-on+配置Combo即可上线”结论为 **否**。可以准备不执行的菜单映射和标签；完整Allô依赖P4/P5/P6，且这些阶段尚未获得本轮实施授权。
