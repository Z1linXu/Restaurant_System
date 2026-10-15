# 06 — Owner Decision Questions

审计日期：2026-10-15（America/Toronto）。范围：READ-ONLY AUDIT + DOCUMENTATION + QUESTIONS。

源码基准：后端 **B = `ce35278b3569a4eacfa29efc01cadbf0c409bec6`**；线上前端 **F = `8c3a9e356f59cd025beff25f7fb1d5a20594e028`**；远端 main **M = `06bac51ddc0a829a9aa1106f07ef886572efbb65`**（本轮 `git ls-remote` 确认，未 fetch 或更新引用）。本轮证明 B/F 的 `frontend/`、`restaurant-pad-app/` 无 Git 内容差异，因此 B 中这两部分也可用于语义审查。源码链接固定到提交，不能用当前旧工作区的同名文件替代。

证据标签：`VERIFIED_IN_CODE` 表示读到固定提交中的实现；`VERIFIED_IN_PRODUCTION` 仅表示本轮明确读取的运行事实；`DOCUMENTED_ONLY` 表示仅有文档陈述；`PLANNED_ONLY` 表示计划/契约尚未证明完成；`UNKNOWN` 表示证据不足。代码被部署不等于所有行为均经过线上验证。风险优先级 P0=独立商家共享部署阻断，P1=上线前关键，P2=按首批范围处理，P3=维护改善；不代表要求立即修改现有 Production。


01–05审计完成后形成以下**恰好20问**。推荐是基于当前实现的候选判断，不代表Owner已选择，也不授权代码、数据库、分支或Production变更。可按 `Q01=A` 等格式作答，每题均允许自定义或组合说明；不回答的项目保持UNKNOWN，不能由推荐自动填充。

## Q01 — 首批付费客户要覆盖哪类餐厅，而不是最终想覆盖哪些餐厅？

为什么需要回答：现代码的厨房、套餐、默认选项与报表偏向拉面/炒面；首批跨度直接决定首发需要替换多少行为。

- A. 先服务与现流程接近的面馆/中式快餐，再扩菜系 **（推荐）**
- B. 首发覆盖咖啡店、简餐和普通快餐
- C. 首发覆盖多菜系全服务餐厅

推荐：**A**。理由：先用现成熟流程获得可验证的产品边界，同时用中性菜单测试防止继续绑定单店；不限制未来市场。

审计依据：HC-016：[B:backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java:10](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java#L10)；HC-020：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2057](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2057)；[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2182](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2182)；HC-022：[B:backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java:106](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java#L106)；HC-042：[B:backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java:21](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java#L21)；[F:frontend/src/features/reports/components/ChannelSalesPanels.tsx:6](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.tsx#L6)。

## Q02 — 首批独立商家采用哪种部署与数据恢复责任模式？

为什么需要回答：共享服务会放大ADMIN/WS/数据库scope缺口；独立部署增加每客户运维；恢复和升级窗口也受此决定。

- A. 每独立商家独立应用+数据库，同商家多门店共用 **（推荐）**
- B. 多独立商家共享应用和数据库
- C. 共享平台控制面，商家数据/运行单元独立
- D. 客户自托管或餐厅现场主机

推荐：**A**。理由：可复用现部署方式并缩小首批故障与恢复边界；仍须修应用权限，成本/运维承诺需另估。

审计依据：HC-002：[B:backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java:44](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java#L44)；HC-003：[B:backend/src/main/java/com/restaurant/system/common/config/WebSocketConfig.java:23](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/WebSocketConfig.java#L23)；[B:backend/src/main/java/com/restaurant/system/common/realtime/StompRealtimeEventPublisher.java:25](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/realtime/StompRealtimeEventPublisher.java#L25)；HC-038：[B:backend/src/main/resources/db/migration/V1__baseline_current_schema.sql:181](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V1__baseline_current_schema.sql#L181)；[B:backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql:37](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql#L37)；HC-051：[B:deployment/cloud/final-channels-20261003/deploy-once.py:33](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/deploy-once.py#L33)；HC-052：[B:deployment/cloud/backup-db.sh:41](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/backup-db.sh#L41)；[B:deployment/cloud/restore-db.sh:54](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/restore-db.sh#L54)。

## Q03 — Organization 是否确定代表一个独立客户/数据拥有者，加盟商怎样划界？

为什么需要回答：当前Org连接Owner、Store、Master Menu和Add-on定义；把无关联加盟商放同Org会改变菜单/报表/权限可见性。

- A. 独立经营客户各自一个Org，旗下自营门店属于该Org **（推荐）**
- B. 按品牌一个Org，加盟商也作为其Store
- C. 每个Store单独Org，跨店共享另授予

推荐：**A**。理由：与已有模型最吻合；加盟商若需要独立数据边界，应作为独立Org处理，品牌共享另设计。

审计依据：HC-002：[B:backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java:44](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java#L44)；HC-013：[B:backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java:14](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java#L14)；[B:backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql:3](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql#L3)；HC-038：[B:backend/src/main/resources/db/migration/V1__baseline_current_schema.sql:181](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V1__baseline_current_schema.sql#L181)；[B:backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql:37](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql#L37)；HC-053：[B:backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileController.java:30](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileController.java#L30)；[B:backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileCatalogService.java:64](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileCatalogService.java#L64)。

## Q04 — 平台支持人员默认能读取商家的业务数据吗，普通商家能否获得ADMIN？

为什么需要回答：当前ADMIN可全店访问，FRONTDESK还有菜单/打印管理能力；商家角色与平台支持必须有明确商业边界。

- A. 商家不获平台ADMIN；支持访问经商家授权、限范围/期限并审计 **（推荐）**
- B. 平台支持长期全商家读取，所有操作审计
- C. 平台默认无业务数据访问，由客户本地导出诊断

推荐：**A**。理由：支持效率与最小权限较平衡，也为Owner/Manager/Frontdesk预置职责留下清晰边界。

审计依据：HC-002：[B:backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java:44](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java#L44)；HC-004：[B:backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java:20](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/RoleCapabilityRegistry.java#L20)；HC-005：[B:backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java:58](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/StoreAccessService.java#L58)；[B:backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java:27](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/auth/RequestUserContextService.java#L27)。

## Q05 — 通用modifier首发需要怎样的选择规则，是否超出现有面型/忌口分组？

为什么需要回答：当前group、UI和套餐选择规则仍有限；多选数量、必选范围、免费额度会改变schema、价格和离线payload。

- A. 保留现有固定组，只允许改名称/选项
- B. 配置分组及最小/最大选择数，按需支持数量/免费额度 **（推荐）**
- C. 任意条件、嵌套和脚本规则引擎

推荐：**B**。理由：覆盖较常见的商家差异并能保留现group为preset；具体数量/免费规则可随首批需求限缩。

审计依据：HC-014：[F:frontend/src/features/owner-admin/MenuOptionsPanel.tsx:19](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/owner-admin/MenuOptionsPanel.tsx#L19)；[B:backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java:34](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/service/impl/OwnerMenuOptionServiceImpl.java#L34)；HC-016：[B:backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java:10](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java#L10)；HC-019：[F:frontend/src/features/ordering/components/ItemCustomizationModal.tsx:16](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.tsx#L16)；[F:frontend/src/utils/comboSelection.ts:8](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/comboSelection.ts#L8)。

## Q06 — 同组织多店的Add-on名称、价格与单菜可用性，应由哪一层拥有？

为什么需要回答：生产刚建立canonical Add-on唯一来源；重新允许每菜维护一份名称/价格会恢复刚消除的漂移。

- A. Org统一identity code，Store定名称/价格/active，每菜仅定eligibility **（推荐）**
- B. 每菜完全独立设置名称/价格
- C. Org强制统一名称/价格，Store不能覆盖

推荐：**A**。理由：最大复用已部署resolver和scope约束，同时允许各店经营差异；历史订单继续使用冻结快照。

审计依据：HC-006：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:994](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L994)；[B:backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java:183](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java#L183)；HC-013：[B:backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java:14](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java#L14)；[B:backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql:3](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql#L3)。

## Q07 — 套餐首发沿用“蛋+小菜”，还是支持由真实菜品组成的任意选择组？

为什么需要回答：既有动态group/linked item可以复用，但legacy SKU映射、负ID、child remove和打印仍认识固定蛋/小菜。

- A. 通用选择组+显式关联真实菜品，保留原蛋/小菜profile兼容 **（推荐）**
- B. 只支持现有蛋+小菜模型并改显示名称
- C. 同时支持嵌套套餐、任选多份和复杂促销

推荐：**A**。理由：利用已有dynamic组件逐步脱离菜名判断；复杂嵌套促销会显著扩大价格/库存/纸张回归。

审计依据：HC-016：[B:backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java:10](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/combo/StoreComboComponentDefinition.java#L10)；HC-017：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2429](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2429)；HC-018：[F:frontend/src/hooks/useMenuCatalog.ts:20](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.ts#L20)；HC-019：[F:frontend/src/features/ordering/components/ItemCustomizationModal.tsx:16](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/ordering/components/ItemCustomizationModal.tsx#L16)；[F:frontend/src/utils/comboSelection.ts:8](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/comboSelection.ts#L8)。

## Q08 — 新商家价格是否允许每个菜品的规格独立定价？

为什么需要回答：当前Store policy为Small/Regular/Large和统一combo差价；咖啡容量、肉类规格等可能无法只用全店差价表示。

- A. 首发只用全店三规格差价和统一套餐差价
- B. 门店默认政策+显式单菜规格/价格覆盖 **（推荐）**
- C. 首发再加入按时段、渠道和促销的动态价格

推荐：**B**。理由：保留现默认能力，同时给真实菜品差异明确出口；渠道促销待首批需求证明后另估。

审计依据：HC-006：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:994](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L994)；[B:backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java:183](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java#L183)；HC-015：[B:backend/src/main/java/com/restaurant/system/menu/pricing/StandardSize.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/pricing/StandardSize.java#L8)；[B:backend/src/main/java/com/restaurant/system/menu/pricing/StorePricingPolicy.java:26](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/pricing/StorePricingPolicy.java#L26)。

## Q09 — 离线草稿恢复联网后，价格变化和已下架选项应怎样处理？

为什么需要回答：当前主菜旧快照多处记录drift后仍接受，canonical Add-on却可能要求刷新；不同规则会影响成交价和重试。

- A. 有限期/version的旧价按服务端政策接受，越界/不可售项提示确认，重试身份保持 **（推荐）**
- B. 所有草稿必须刷新当前菜单并重新确认后提交
- C. 永远接受客户端保存的全部价格/选项

推荐：**A**。理由：可兼顾弱网体验与价格授权；具体有效期另定，并保留当前Add-on必要校验和pending outbox。

审计依据：HC-006：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:994](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L994)；[B:backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java:183](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java#L183)；HC-013：[B:backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java:14](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/addon/StoreAddonOptionResolver.java#L14)；[B:backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql:3](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V28__add_store_addon_catalog.sql#L3)；HC-033：[F:frontend/src/offline/offlineDatabase.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/offlineDatabase.ts#L1)；[F:frontend/src/offline/orderOutbox.ts:58](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/orderOutbox.ts#L58)。

## Q10 — 每家店是否需要独立选择打印厨房、KDS或混合流程？

为什么需要回答：现有模块开关存在，但HOT/GRAB路由和KDS屏幕带固定station/蛋/面语义。

- A. 每Store选择printer/KDS/hybrid，并配置工位与输出角色 **（推荐）**
- B. 首发只提供现餐厅打印工作流
- C. 首发统一改成KDS主导流程

推荐：**A**。理由：能复用现模块基础并保留现餐厅工作方式；不同流程先限定受支持组合。

审计依据：HC-022：[B:backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java:106](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/semantic/HotKitchenPrintEligibilityService.java#L106)；HC-023：[B:backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java:26](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java#L26)；HC-027：[B:backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java:7](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java#L7)；[F:frontend/src/App.tsx:43](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/App.tsx#L43)；HC-036：[B:backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/realtime/RealtimeTopics.java#L8)；HC-046：[F:frontend/src/features/feature-flags/featureConfig.ts:10](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/feature-flags/featureConfig.ts#L10)；[B:backend/src/main/java/com/restaurant/system/common/feature/FeatureFlagService.java:14](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/feature/FeatureFlagService.java#L14)。

## Q11 — 商家可以改变小票的哪些部分，是否需要自由模板编辑器？

为什么需要回答：已经有display dictionaries/revision，但默认缩写、排序、面型省略、版式和文字fallback仍属兰州语义。

- A. 中性版式+有限打印presets，商家配置抬头/语言/别名/排序/份数 **（推荐）**
- B. 提供任意脚本或拖拽式完整模板设计器
- C. 所有商家统一使用现有固定小票

推荐：**A**。理由：覆盖常见个性化并复用已有规则版本；自由设计器会扩大支持和打印正确性成本。

审计依据：HC-020：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2057](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2057)；[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2182](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2182)；HC-021：[B:backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/rules/PrintingDisplayRuleDefaults.java#L8)；HC-023：[B:backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java:26](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java#L26)；HC-024：[B:backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java:215](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L215)；[B:backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java:47](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java#L47)；HC-025：[B:backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java:118](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L118)；[B:backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java:31](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java#L31)。

## Q12 — 首发桌台能力要到哪一步，A/B拆桌是否必须保留为可选模式？

为什么需要回答：桌台已数据驱动，但slot、suffix、显示和恢复逻辑固定A/B；这不等于任意座位或分账。

- A. 整桌+可选原A/B拆桌；暂不承诺任意分账/拼桌 **（推荐）**
- B. 首发支持任意座位、合并/拆分及分账
- C. 首批产品只服务无桌台的外带/柜台店

推荐：**A**。理由：保留已验证的现餐厅桌台行为并限制首发复杂度；B需要新的订单/桌台语义验收。

审计依据：HC-030：[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java:45](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java#L45)；HC-031：[F:frontend/src/types/dinein.ts:5](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/types/dinein.ts#L5)；[F:frontend/src/utils/tableDisplay.ts:7](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/tableDisplay.ts#L7)。

## Q13 — 首发地域、税制、币种与营业日时区要覆盖多广？

为什么需要回答：代码固定14.975%、CAD和Toronto调度；这只证明现实现，不能直接保证其他税区或税包含价正确。

- A. 先限定现有Quebec/CAD营业范围，但税/时区作为明确配置并验证 **（推荐）**
- B. 首发覆盖加拿大多个省和时区
- C. 首发支持多个国家/币种/税制

推荐：**A**。理由：减少首发校验矩阵；并不把现常量认作所有交易的合法税率，税务政策需由商家确认。

审计依据：HC-010：[B:backend/src/main/java/com/restaurant/system/common/pricing/TaxCalculator.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/pricing/TaxCalculator.java#L8)；[F:frontend/src/utils/tax.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/utils/tax.ts#L1)；HC-011：[B:backend/src/main/java/com/restaurant/system/analytics/service/AnalyticsAggregationScheduler.java:17](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/service/AnalyticsAggregationScheduler.java#L17)；[B:deployment/cloud/docker-compose.yml:9](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/docker-compose.yml#L9)；HC-012：[F:frontend/src/features/reports/reportUtils.ts:13](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/reportUtils.ts#L13)。

## Q14 — 餐厅品牌与界面语言首发如何组合？

为什么需要回答：菜单/快照使用中英双字段，UI和小票夹杂固定文字；第三语言和全面白标都需要额外发布/测试。

- A. 统一产品品牌+每店品牌资料，中英基础语言包并保留扩展入口 **（推荐）**
- B. 首发所有界面/小票完整中英法三语
- C. 首发每客户完整白标与自定义多语言

推荐：**A**。理由：与现双语模型最接近；若Q1明确要求法语或其他语言，再将其作为首发验收而非假设已支持。

审计依据：HC-025：[B:backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java:118](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/FrontdeskReceiptRenderer.java#L118)；[B:backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java:31](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/renderer/HotKitchenReceiptRenderer.java#L31)；HC-032：[F:frontend/src/types/ordering.ts:181](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/types/ordering.ts#L181)；[F:frontend/src/features/reports/components/ChannelSalesPanels.tsx:6](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.tsx#L6)；HC-048：[B:restaurant-pad-app/android/app/src/main/AndroidManifest.xml:1](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/AndroidManifest.xml#L1)；[F:frontend/src/App.tsx:2](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/App.tsx#L2)。

## Q15 — Android Pad采用一个通用App，还是每商家独立APK？

为什么需要回答：当前只有com.restaurant.pad包，URL可运行时配置；安装签名、bundled assets与后台兼容需要独立追踪。

- A. 一个签名通用App，通过安全绑定选择商家/环境 **（推荐）**
- B. 每品牌独立package/signing/flavor
- C. 只交付浏览器界面，不承担Android App分发

推荐：**A**。理由：复用最多、更新矩阵较小；必须先补切服隔离及APK/asset/contract版本管理。

审计依据：HC-033：[F:frontend/src/offline/offlineDatabase.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/offlineDatabase.ts#L1)；[F:frontend/src/offline/orderOutbox.ts:58](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/offline/orderOutbox.ts#L58)；HC-034：[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:3676](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L3676)；[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:416](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L416)；HC-035：[B:restaurant-pad-app/android/app/build.gradle:10](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/build.gradle#L10)；[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:200](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L200)。

## Q16 — 首发承诺支持哪些Pad与打印硬件组合？

为什么需要回答：成熟路径为Android原生LAN ESC/POS TCP；默认9100/GBK不意味着任意打印机或iPad浏览器能直接TCP打印。

- A. 认证有限ESC/POS TCP型号，Android打印，iPad可作为浏览器点餐端 **（推荐）**
- B. 首发同时支持USB/Bluetooth及广泛打印机型号
- C. iPad独立完成打印为首要目标，另做native/gateway方案

推荐：**A**。理由：最贴近现实现；仍需逐型号物理纸张/编码/网络和不确定重试验收。

审计依据：HC-026：[B:backend/src/main/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImpl.java:89](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/service/impl/PrinterConfigServiceImpl.java#L89)；[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:90](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L90)；HC-034：[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:3676](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L3676)；[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:416](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L416)；HC-035：[B:restaurant-pad-app/android/app/build.gradle:10](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/build.gradle#L10)；[B:restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java:200](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/restaurant-pad-app/android/app/src/main/java/com/restaurant/pad/MainActivity.java#L200)。

## Q17 — 第三方平台首发范围是什么，需求中的Grab是否真的指外卖平台？

为什么需要回答：代码有Uber connector，但Production flag关闭；GRAB名称在当前代码代表厨房打印模块，未找到Grab外卖实现。

- A. Uber作为可选模块；Grab平台独立需求另立项 **（推荐）**
- B. 首发同时交付Uber与Grab等新平台
- C. 首发不激活外卖集成，保留既有模块能力

推荐：**A**。理由：复用已有Uber契约/映射而不混淆厨房GRAB；各商家授权和Production激活仍单独控制。

审计依据：HC-043：[B:backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java:19](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java#L19)；[B:backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsStoreMapping.java:9](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/entity/UberEatsStoreMapping.java#L9)；HC-044：[B:backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java:31](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/integration/ubereats/config/UberEatsProperties.java#L31)；[B:backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java:33](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java#L33)；HC-045：[B:backend/src/main/java/com/restaurant/system/printing/PrintModuleCode.java:8](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/printing/PrintModuleCode.java#L8)。

## Q18 — 新独立商家的初始化应是人工协助、完全自助，还是只做现品牌扩店？

为什么需要回答：当前创建店默认St-Denis profile，global profile catalog无组织私有边界；空白商家/首个Owner引导未完整证明。

- A. 运营协助建独立Org/Owner，一次创建空白店或选择授权preset **（推荐）**
- B. 首发完全自助注册、邀请、菜单导入和设备向导
- C. 首发只复制当前同品牌Master Menu开分店

推荐：**A**。理由：复用现provisioning并减少首发注册/支持复杂度；禁止自动带入现店菜单、员工、硬件、credentials或真实订单。

审计依据：HC-028：[B:backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java:31](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java#L31)；HC-029：[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java:33](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningServiceImpl.java#L33)；HC-030：[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java:45](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OperationalStoreBaselineProvisioner.java#L45)；HC-039：[B:backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql:7](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L7)；[B:backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql:1](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql#L1)；HC-040：[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java:53](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java#L53)；HC-041：[B:backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java:178](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L178)；[B:backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java:1543](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java#L1543)；HC-053：[B:backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileController.java:30](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileController.java#L30)；[B:backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileCatalogService.java:64](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/profile/StoreProfileCatalogService.java#L64)。

## Q19 — 首个可售版本对库存、报表与支付承诺到什么程度？

为什么需要回答：BOM扣减已有，但restock/prep API仍是空壳；报表已有但分类专用；不能将未来功能当现能力销售。

- A. POS/厨房/打印+基础报表；库存仅已验证基础，支付暂不集成 **（推荐）**
- B. 在A基础上，补齐库存补货/备菜/流水闭环再上市
- C. 首发还包含支付集成与高级分析

推荐：**A**。理由：先交付成熟运营核心并明确限制；若库存完整正确性是销售承诺，须选B并优先解决并发/BOM隔离。

审计依据：HC-007：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:1634](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L1634)；[B:backend/src/main/java/com/restaurant/system/inventory/entity/InventoryItem.java:42](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/inventory/entity/InventoryItem.java#L42)；HC-008：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:1634](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L1634)；[B:backend/src/main/java/com/restaurant/system/menu/repository/MenuItemBomRepository.java:12](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/menu/repository/MenuItemBomRepository.java#L12)；HC-009：[B:backend/src/main/java/com/restaurant/system/inventory/controller/InventoryController.java:12](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/inventory/controller/InventoryController.java#L12)；[B:backend/src/main/java/com/restaurant/system/inventory/service/InventoryService.java:3](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/inventory/service/InventoryService.java#L3)；HC-042：[B:backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java:21](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/analytics/support/SalesReporting.java#L21)；[F:frontend/src/features/reports/components/ChannelSalesPanels.tsx:6](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/features/reports/components/ChannelSalesPanels.tsx#L6)；HC-049：[B:backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java:1](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java#L1)；[F:frontend/src/hooks/useMenuCatalog.test.ts:1](https://github.com/Z1linXu/Restaurant_System/blob/8c3a9e356f59cd025beff25f7fb1d5a20594e028/frontend/src/hooks/useMenuCatalog.test.ts#L1)；HC-052：[B:deployment/cloud/backup-db.sh:41](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/backup-db.sh#L41)；[B:deployment/cloud/restore-db.sh:54](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/restore-db.sh#L54)。

## Q20 — 现兰州餐厅与商业产品未来怎样共享源码、发布和修复？

为什么需要回答：Production为B/F组合，本地旧HEAD远落后；长期fork会增加双向补丁，直接跟main则会扩大生产变更面。

- A. 一个Core/Modules/Profile代码体系，现餐厅和商业客户各自受控发布 **（推荐）**
- B. 独立commercial repository，保留来源和明确双向backport机制
- C. 长期维护两套独立fork和数据库演进

推荐：**A**。理由：利用已有Profile/Master降低重复修复成本，同时现餐厅保留独立release/验收/回滚；本轮只建议，不建分支。

审计依据：HC-001：[B:backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java:71](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/IdempotentOrderSubmissionServiceImpl.java#L71)；HC-020：[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2057](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2057)；[B:backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java:2182](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java#L2182)；HC-028：[B:backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java:31](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/menu/ChinatownMenuCloneProfile.java#L31)；HC-039：[B:backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql:7](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V15__seed_st_denis_canonical_profile.sql#L7)；[B:backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql:1](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/resources/db/migration/V19__seed_phase_b_master_menu_profile_v2.sql#L1)；HC-040：[B:backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java:53](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/backend/src/main/java/com/restaurant/system/owner/provisioning/OwnerStoreProvisioningMaterializer.java#L53)；HC-051：[B:deployment/cloud/final-channels-20261003/deploy-once.py:33](https://github.com/Z1linXu/Restaurant_System/blob/ce35278b3569a4eacfa29efc01cadbf0c409bec6/deployment/cloud/final-channels-20261003/deploy-once.py#L33)。


## 20问之外仍然UNKNOWN的事实

- 实际首批客户数量/签约时限/支持预算、承诺的可用性和维护窗口。
- 全部已装APK签名/文件hash/asset manifest、每台设备目标、长期Worker及认证打印机型号矩阵。
- 完整secret inventory/rotation/云账户最小权限；证书续期任务和跨公网地点可达性。
- 备份自动频率、offsite/加密、retention、客户数据导出/删除范围、RPO/RTO和已完成restore演练。
- 完整负向tenant/WS/profile访问矩阵、库存并发、跨环境outbox切换和覆盖率数字。
- Uber各新商家的外部授权/生产许可、Grab等未提供平台的实际API合同。
- 品牌素材授权、模板可共享权及目标地区的具体税务/营业政策。

这些是事实缺口，未额外转换为第21问，也未擅自假设。后续Planbook只能在Owner回答后把相关约束纳入验收与估算。

**AUDIT COMPLETED — AWAITING OWNER DECISIONS**

STOP：本轮结束于20问；没有创建商业分支/Tag/repo，没有重构、部署、自动开发循环或生产写入。完整Product Commercialization Planbook等待Owner答复后另行制定。
