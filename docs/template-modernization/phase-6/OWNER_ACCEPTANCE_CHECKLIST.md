# Owner 网页验收清单 — Phase 6 M0 + M1

状态：`OWNER_NOT_RUN`。本清单供 Owner 在 Root 确认 Test 部署 Ready 后手动执行；HTTP 通过不代表这些网页步骤已经通过。

仅访问 **https://test.lanzhounoodlesmtl.com**，仅使用指定 **SYNTHETIC Test 门店**及 **MOCK** 打印机。登录凭据由 Root 通过本地受保护文件另行提供，本文不记录账号、密码或 token。页面中的菜名、价格、门店和订单均为合成验收材料，不能据此推断任何真实客户菜单或 Production 状态。

记录：验收日期 ______；Owner ______；Root 提供的部署 Commit ______；绑定运行版本的 receipt ______。每步记录截图或订单号及结果；未执行填 `NOT_RUN`，不预填 PASS。

| 步骤 | 网页操作（采用当前 UI 标签） | 预期结果与记录 |
|---|---|---|
| 1 ☐ | 登录 Test，选择 Root 指定的 SYNTHETIC 门店；打开 **Menu Management**、**Printing Settings**。在打印中心核对当前为 **MOCK**。 | Owner 能进入管理页面；门店正确，无实体打印机连接。登录/门店截图：______；结果：______。不要配置地址、切换 REAL/PAD_DIRECT、配对 Pad 或点击实体设备测试。 |
| 2 ☐ | **Menu Management → New Menu Item**，建立带唯一后缀的 `SYNTHETIC SpringRoll`，填写名称、SKU、现有合成 **Category / Station / Base Price**，点 **Save Item**。点该行 **Options**，确认 **No option groups / 无组选项**；不添加组。回 POS 点菜卡的 **+**。 | 新商品保存后即可无组选项 QuickAdd，不弹出定制窗口；数量和金额正确。记录名称/单价：______；结果：______。这验证显式新模式无组，不能用旧商品的历史选项状态替代。 |
| 3 ☐ | 为 `SYNTHETIC Chicken` 打开 **Options → Item Options / 菜品选项**；从 **Choose a library group / 选择预设或自定义组** 分别选辣度、加料、去料，点 **Add independent copy / 添加独立副本**。用 **Required / 必选、Default / 默认、Enabled / 启用、Price / 差价** 设置可识别的测试值，点 **Save option groups / 保存组选项**并确认。 | POS 使用现有定制窗口显示 **Spicy / 辣度、Add-on / 加料、Remove / 去料**；默认正确、停用项不出现、缺少必选时不能 **Add to Order / 添加到订单**；加料差价参与普通计价，去料免费。记录选择/金额：______；结果：______。 |
| 4 ☐ | 在 Chicken 或另建合成面条商品的 **Options** 中选 **Custom / 自定义 → New group / 新建组选项**，命名“面条类型”；用 **Add option / 添加选项** 建三个选项，例如细面、宽面、米粉。设 **Single / 单选**、**Required / 必选**及一个默认，设置普通差价后保存。 | POS 显示该自定义组及默认，切换单选、差价和订单选项摘要正确；不依赖菜名/SKU 专属判断。记录值：______；结果：______。此步骤不测试通用条件互斥或完整历史菜单版本窗口。 |
| 5 ☐ | 在上述菜品选项页分别使用组标题旁的 **↑ / ↓**，以及组内选项旁的 **↑ / ↓**；保存，刷新管理页，再重开 POS 定制窗口。 | **组顺序与组内 Option 顺序两级**均按保存结果显示；默认、必选、启停状态不因排序改变。记录目标顺序：______；结果：______。 |
| 6 ☐ | **Menu Management → Option Group Library / 组选项库** 新建一个 **Custom / 自定义** 库组并保存。为合成商品 A、B 分别 **Add independent copy / 添加独立副本**并保存；只改 A 副本的某项 **Price / 差价**，刷新后分别重开 A、B 和库。 | A 新价生效，B 及库保持原价；默认和名称也各自独立。修改库不回写已有商品副本。记录 A/B/库价格：______ / ______ / ______；结果：______。 |
| 7 ☐ | **Printing Settings → Kitchen Tickets / 厨房分单**，勾选 **Use these kitchen ticket groups / 使用以下厨房分单组**；用 **New kitchen ticket / 新建厨房票** 配置 A/B/C，填写 **Ticket name / 票组名、Logical target / 逻辑目标**，选择现有 **MOCK printer / 模拟打印机**，故障保持 **None / 无**。用 **Categories / 分类**、必要的 **Additional items / 单独包含菜品**或 **Item overrides / 单品覆盖分类分配**，让待测三类商品分别有明确去向；点 **Save kitchen rules / 保存厨房票规则**并确认。 | 刷新后规则及各目标仍在；仅使用合成 MOCK 设备，A/B/C 可分别绑定或共享同一 MOCK 设备。不操作下方旧打印模块分配及设备配对。记录映射：______；结果：______。 |
| 8 ☐ | 在 **SYNTHETIC Preview / 合成订单预览** 勾选覆盖 A/B/C 的商品，必要时点 **Options / 选项**选择定制，点 **Preview saved rules / 预览已保存规则**，展开 **Item coverage / 商品分单去向**。 | 使用已保存规则展示三张预览、正确商品及选项去向；重复预览不出现新营业订单或 Job。记录预览截图及预览前后列表观察：______；结果：______。网页列表观察不能单独证明数据库零写，底层结论须关联同版本 HTTP/SQL 零写证据。 |
| 9 ☐ | 回 POS 选 A/B/C 对应商品和定制，点 **提交订单**，记录服务器确认的 Order #；在成功区 **Ticket Status / 票据状态**和 **Printing Settings**查看票据，展开内容与旧前台小票的 **预览小票**。再向同一订单仅增加一个待测商品，点 **更新订单**。 | 首次提交有三张持久 MOCK 厨房票，各含对应商品；旧前台票保留完整订单及金额。Update 厨房票及原前台加单票仅含新增批次，不重印旧行；首次提交的旧票内容不改。记录 Order #/Update/Job：______；结果：______。MOCK 的 PRINTED 表示模拟处理完成，不代表纸张输出；不要重复点提交来代替幂等证据。 |
| 10 ☐ | 只把 C 的 **MOCK printer / 模拟打印机**改成 **Unbound / 未绑定**并保存。提交另一张覆盖 A/B/C 的合法合成订单；记录 Order #。到 **Printing Settings → Kitchen Tickets / 厨房票与持久诊断**按该 Order #查找、展开，点 **Refresh tickets / 刷新票据**；整页刷新，再退出并重新登录同一门店，重查该 Order #。完成后恢复原 C 绑定并保存。 | 订单仍成功，A/B 继续模拟完成；成功区显示 **Order saved. Some tickets need attention. / 订单已保存，以下票据需要处理。**；C 显示 **Not created / 未创建**和原因、Intent/source key，不能伪装成已创建 Job。刷新/重新登录后诊断仍在；恢复 C 配置不应悄悄改写或重发旧票。记录 Order #/Intent/截图：______；结果：______。 |

## 当前边界与已知限制

- **Combo 复用现有配置**：组选项里的 **Combo / 套餐（沿用已有配置）**只绑定、排序现有 Combo；具体内容从 **Open Combo configuration / 打开套餐配置**管理。当前新路由仅支持已经验证的 side `LEGACY_COMBO_SIDE_TASK` + 有效、启用的 COLD linked item，以及 egg `NO_KITCHEN_TASK`。不承诺任意 Combo 行为；无 linked item 或其他未支持行为应拒绝，不能用文字备注伪造子项。
- **旧前台 Renderer 保留**：完整订单/金额/既有打印语义沿用；自由 Custom 的**零价 Option label 可能不显示于旧前台小票**，这是已记录限制。厨房票应保留该制作指令，不能把前台缺标签误写成已支持，也不能为了验收修改 Renderer。
- 本清单不验证实体打印、Pad/APK、Production 三旧店迁移、真实 SaaS 发布或备份恢复。`UNCERTAIN` 不允许用自动重发或人工反复点击来“变绿”。只在受控 Test 内保留合成订单及证据，不清库、不删除历史票据。

UI 标签核对来源：`frontend/src/features/owner-admin/{MenuManagementPage,TemplateOptionGroupsPanel,KitchenRoutingPanel,TemplatePrintDiagnosticsPanel,PrintingSettingsPage}.tsx`；POS 按钮来源：`frontend/src/features/ordering/components/{MenuItemCard,ItemCustomizationModal,OrderSummaryPanel}.tsx`。实际运行版本以 Root 提供的部署 Commit、receipt 和证据包为准。
