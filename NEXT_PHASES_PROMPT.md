# Restaurant POS Phase0-Phase4 Roadmap Prompt

请先阅读当前代码库和 `SYSTEM_DOCUMENTATION.md`。本任务分为 Phase0 到 Phase4。请严格按阶段执行，不要跨阶段乱改。

重要原则：

* 每个 Phase 必须独立完成、独立测试、独立汇报。
* 不要一次性实现 Phase0-Phase4 全部内容。
* Phase0 可以直接实现。
* Phase1-Phase4 先输出详细实施方案，除非我明确说“开始实现 Phase X”。
* 不要破坏现有功能：

  * Mock Printing Mode
  * REAL printer dispatch
  * Order Update Batch / Revision
  * Edit Order 只能新增菜
  * Print Center / Reprint
  * KDS
  * Current auth / JWT / permission system
* 所有涉及权限、store scope、multi-store 的改动，后端必须校验，不能只靠前端隐藏。
* 不要使用 X-User-Id 作为新架构基础。
* 不要把 dev-only 功能带进 pilot/production。

---

## Phase0：马上修复 GRAB 走葱 / 走青 bug

### 背景

当前 bug：

* 点一碗面，只选择“走葱”
* GRAB ticket 错误显示成“走青”

正确规则：

* 只走葱 -> 显示 `走葱`
* 只走香菜 -> 显示 `走香菜`
* 同时走葱 + 走香菜 -> 才显示 `走青`

同理加料：

* 只加葱 -> 显示 `加葱`
* 只加香菜 -> 显示 `加香菜`
* 同时加葱 + 加香菜 -> 才显示 `加青`

### 要求

1. 只修改 GRAB ticket renderer 相关逻辑。
2. 不影响 FRONTDESK_RECEIPT。
3. 不影响 order submit / update batch / reprint。
4. Mock Printing Mode Preview Receipt 能验证。
5. Update GRAB ticket 也必须使用相同规则。
6. 不要引入复杂 Display Rules 系统，这个 Phase 只修 bug。
7. 补充或更新测试：

   * only 走葱 -> 走葱
   * only 走香菜 -> 走香菜
   * 走葱 + 走香菜 -> 走青
   * only 加葱 -> 加葱
   * only 加香菜 -> 加香菜
   * 加葱 + 加香菜 -> 加青
   * 走葱 + 加葱 不应互相误合并
   * FRONTDESK_RECEIPT 不受影响

### 完成后运行

* `cd backend && mvn -q test`
* `cd backend && mvn -q -DskipTests compile`
* `cd frontend && npm run build`，如果 Phase0 没改前端，也仍然跑一次 build 确认没破坏。

### Phase0 输出

完成后输出：

* 修改文件列表
* 根因说明
* 新逻辑说明
* 测试结果
* Mock Preview 手动验证步骤
* 是否有未完成点

---

## Phase1：Store Access 后端安全基础，先出方案，不要实现

### 背景

当前系统已有：

* organizations
* stores.organization_id
* users.store_id
* roles
* JWT
* permission capability registry

但现在用户基本上还是天然属于一个 `users.store_id`。JWT 里的 `store_id` 和 `organization_id` 更像默认门店，不是完整 workspace scope。

当前风险：

* `AuthorizationService.ensureSameStore()` 对 OWNER 放行过宽。
* 未来多个 owner 使用同一套系统时，Owner A 不能访问 Owner B 的 store。
* 前端很多 service 仍默认 `store_id=1`。

### 目标

设计 store/organization membership 权限基础。

建议新增：

* `organization_memberships`
* `store_memberships`

权限目标：

* Staff 只能访问自己 store_memberships 里的 stores。
* Manager 只能管理自己 store 的员工、菜单、桌台、打印。
* Owner 只能访问自己 organization_memberships 下 organization 的 stores。
* Platform Admin 才能跨 owner / organization / store。
* Dev Role Switcher 也不能绕过 membership。

### Phase1 方案必须包含

1. 当前 entity 状态分析。
2. 新增表设计：

   * organization_memberships
   * store_memberships
3. 是否保留 `users.store_id` 作为 legacy default store。
4. 新增 `StoreAccessService` 设计。
5. 如何修改 `AuthorizationService.ensureSameStore()`。
6. 如何 seed 当前用户 membership，保证现有开发数据不坏。
7. 新增 API：

   * `GET /api/v1/me/workspaces`
   * `GET /api/v1/stores/{storeId}/context`
8. 非授权访问如何返回 403。
9. Dev Role Switcher dev users 如何补 membership。
10. 数据迁移/兼容策略。
11. 测试方案。

### 注意

Phase1 先只输出方案，不要实现，除非我明确确认。

---

## Phase2：前端 Store Context 和 Store Workspace 路由，先出方案，不要实现

### 背景

当前前端路由是全局静态路由：

* `/frontdesk`
* `/frontdesk/menu`
* `/frontdesk/order`
* `/admin/settings/printing`
* `/admin/menu/items`
* `/kds/...`

未来应该变成 store workspace：

* `/stores/:storeId/frontdesk`
* `/stores/:storeId/frontdesk/menu`
* `/stores/:storeId/frontdesk/order`
* `/stores/:storeId/admin/settings/printing`
* `/stores/:storeId/admin/menu/items`
* `/stores/:storeId/kds/grab`
* `/stores/:storeId/kds/hot-kitchen`
* `/stores/:storeId/kds/noodle`

### 目标

用户登录后进入自己的 store workspace。

* 如果用户只有一个 store，自动进入该 store。
* 如果用户有多个 stores，显示 Store Switcher。
* Owner 可以切换自己名下多家餐厅。
* Staff 一般只显示当前门店名称，不需要切换。
* 手动修改 URL 到无权限 store，前端显示 Access Denied，后端必须 403。

### Phase2 方案必须包含

1. `StoreContextProvider` 设计。
2. `useCurrentStore()` 设计。
3. `StoreSwitcher` UI 设计。
4. `RequireStoreAccess` 设计。
5. 登录后 redirect 规则。
6. 旧路由 redirect 策略：

   * `/frontdesk`
   * `/frontdesk/menu`
   * `/admin/settings/printing`
7. 哪些 service 存在 `DEFAULT_STORE_ID=1` 或硬编码 store。
8. 如何逐步让这些 service 从 route/context 取 storeId：

   * orderService.ts
   * menuService.ts
   * kdsService.ts
   * printingAdminService.ts
   * frontdesk services
9. WebSocket topic 如何 store-scoped。
10. 测试方案：

    * Staff A 只能进 Store A
    * Owner 能切 Store A / Store B
    * Store A 的订单不会出现在 Store B
    * 打印配置不串店
    * KDS 不串店

### 注意

Phase2 先只输出方案，不要实现，除非我明确确认。

---

## Phase3：Owner Multi-Store 体验，先出方案，不要实现

### 背景

老板和餐厅关系应该是：

* Owner 1:N Stores
* 一个老板可以管理多家餐厅
* 每家店有自己的菜单、税率、打印机、桌台、订单、员工、display rules
* 未来一个 manager 也可能管理多家店

### 目标

Owner 登录后不是直接进单店页面，而是可以看到自己 organization 下的多家餐厅。

### Phase3 方案必须包含

1. Owner Dashboard / Store Selector 设计。
2. Store cards 显示内容：

   * store name
   * today orders
   * printer status
   * open tables
   * active KDS orders
3. Owner 切店后进入对应 store workspace。
4. Manager 多店权限设计。
5. Store-level settings 范围：

   * menu
   * tax rate
   * printing
   * dining tables
   * staff
   * display rules
6. Organization-level settings 范围：

   * owner users
   * billing future placeholder
   * global templates future placeholder
7. API 设计。
8. 前端页面结构。
9. 测试方案。

### 注意

Phase3 先只输出方案，不要实现，除非我明确确认。

---

## Phase4：Admin Menu Display Rules / Print Rules 页面，先出方案，不要实现

### 背景

老板或 manager 需要在后台调试每个菜、addon、remove option、小菜在不同地方怎么展示。

展示目标包括：

* Frontdesk Menu
* GRAB Ticket
* FRONTDESK_RECEIPT
* KDS

典型规则：

* 走葱 + 走香菜 -> GRAB 显示 `走青`
* 但单独走葱不能显示成 `走青`
* 加葱 + 加香菜 -> GRAB 显示 `加青`
* 不同地方可有不同 display label

### 推荐页面

短期：

* `/admin/settings/menu-display-rules`

未来 store-scoped：

* `/stores/:storeId/admin/settings/menu-display-rules`

### 设计原则

1. 不要一开始做复杂规则引擎。
2. 不要直接让老板编辑 JSON。
3. 第一版用受控表单 + preview。
4. 必须支持 reset-to-default。
5. 必须支持 Mock Preview 或 renderer preview。
6. 不要影响 Menu Management 的价格/option 主数据。

### Phase4 方案必须包含

1. 页面应该放 Admin Settings 还是 Menu Management，并说明原因。
2. 数据库设计，例如：

   * menu_display_rules
   * scope: FRONTDESK_MENU / GRAB_TICKET / FRONTDESK_RECEIPT / KDS
   * target_type: MENU_ITEM / MENU_ITEM_OPTION / OPTION_GROUP / COMBINATION_RULE
   * target_id
   * rule_code
   * rule_json
   * enabled
   * sort_order
3. MVP 受控规则设计：

   * combine remove green
   * combine add green
   * alias / rename
   * hide on receipt
4. Preview API：

   * `POST /api/v1/admin/stores/{storeId}/display-rules/preview`
5. UI 设计：

   * Menu item selector
   * Option selector
   * Preview tabs
   * Rules list
   * Reset to default
6. 权限：

   * Owner / Manager 可管理
   * Frontdesk/Kitchen 只读或无权
7. 风险：

   * 规则太自由导致打印混乱
   * 历史订单显示变化
   * 中英文 label 不一致
8. 测试方案：

   * 只走葱 preview GRAB = 走葱
   * 只走香菜 = 走香菜
   * 两个都选 = 走青
   * FRONTDESK_RECEIPT 不被 GRAB rule 影响
   * KDS preview 正确
   * reset-to-default 生效

### 注意

Phase4 先只输出方案，不要实现，除非我明确确认。

---

## 最终要求

请按顺序执行：

1. 先实现 Phase0。
2. Phase0 完成并测试后，输出结果。
3. 然后只输出 Phase1-Phase4 的详细方案。
4. 不要实现 Phase1-Phase4。
5. 不要在我不在电脑前时大规模改 multi-store 架构。
6. 所有修改必须保持可编译。
7. 完成后输出：

   * 当前 git diff 摘要
   * 修改文件
   * 测试命令和结果
   * 未完成事项
   * 下一步建议
