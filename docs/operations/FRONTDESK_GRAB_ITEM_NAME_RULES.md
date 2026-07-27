# 前台菜单与 GRAB 菜品名称映射及显示规则

> 文档状态：`OPERATIONAL_DISPLAY_RULE_SOURCE`
>
> 规则基线日期：2026-07-27
>
> 适用范围：当前仓库中 Store 1 的菜单种子目录、GRAB Renderer、厨房任务快照和相关回归测试。
> 生产启用状态：必须以目标门店的 Menu API/数据库为准；本文件不因仓库种子而断言生产菜单当前全部启用。

## 1. 目的和权威边界

本文件定义前台点餐使用的顾客/服务员可读名称，如何映射为 GRAB
厨房生产名称。两者允许不同，**不得**为了统一显示而修改菜单名称、
厨房简称、KDS 或 Renderer。

本文件是前台菜单、GRAB Renderer、KDS 厨房简称和现场打印回归测试的
共同参考。它不替代运行时菜单数据，也不替代订单或厨房任务快照。

证据优先级如下：

1. `GrabReceiptRenderer` 与 `KitchenNoodlePrintFormatter` 的运行代码；
2. `OrderServiceImpl` 创建 `KitchenTask` 时写入的快照；
3. `GrabReceiptRendererTest` 和订单服务测试；
4. 稳定标识（`menu_item_id`、`item_sku_snapshot` / SKU、`option_code_snapshot`）；
5. `RuntimeDataSeeder` 与菜单 API 的目录定义；
6. 已确认的现场示例和 `SYSTEM_DOCUMENTATION.md` 摘要。

发生冲突时，已提交订单的 `KitchenTask` / `OrderItem` 快照优先于当前
菜单；当前生产菜单的启用/停售状态优先于种子目录。

## 2. 名称与稳定标识的定义

- **前台菜单名称**：`menu_items.name_zh` / `name_en`，或订单创建时写入
  `order_items.item_name_snapshot_zh` / `_en` 的顾客可读名称。
- **GRAB 主显示名称**：由 `kitchen_tasks.item_name_snapshot_zh` / `_en`
  与 `special_instructions_snapshot` 经过 `GrabReceiptRenderer` 格式化后
  的厨房生产内容。
- **稳定菜品标识**：优先 SKU 与订单快照中的 `menu_item_id`；不要仅按
  中文名称判断。运行时 `menu_item_id` 由数据库分配，不能由此文档硬编码。
- **稳定选项标识**：优先 `option_code_snapshot`、`option_group_snapshot`、
  `option_type_snapshot` 和 `option_id`。仅为兼容旧订单，代码才回退到
  `option_name_snapshot_zh`。
- **厨房快照**：创建任务时，订单服务写入
  `item_name_snapshot_zh`、`item_name_snapshot_en`、
  `special_instructions_snapshot`。GRAB 以这些不可变订单快照为输入，不
  回查或猜测当前菜单名。

## 3. 当前仓库菜单目录映射表

下表覆盖 `RuntimeDataSeeder.seedMenuItems()` 中定义的当前菜单目录。
这些条目的**映射规则**为 `CODE_VERIFIED`；它们在某个生产门店是否仍为
active / sold-out，必须由 Menu API 或该门店数据库确认，因而不在本文件
中推断。饮品由 BAR 直接服务，不形成 GRAB 厨房任务，仍保留在表中以避免
把“无 GRAB 行”误解为缺失映射。

| stable_item_identifier | menu_item_id | SKU / item_code | category | station | 前台菜单中文名称 | 前台英文名称 | GRAB 基础名称 | 选项动态生成 | 尺寸 / 汤底 / 面型影响 | 加减料 / 套餐影响 | 数量格式 | GRAB 示例 | 证据来源 | 验证状态 | 最后确认日期 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `sku:traditional_beef_noodle` | runtime assigned | `traditional_beef_noodle` | `SOUP_NOODLE` | `NOODLE` | 传统牛肉面 | Traditional Beef Noodle | 厨房快照 / 完整 shorthand | 是 | `中`/`大` + 非默认面型 + 辣度 | 加减料作为 modifier；套餐小菜另建 COLD task | `…×1`；同配置多碗为 `(…) ×N` | `中二（s）×1 \| +蛋` | `OrderServiceImpl:1813-1966`; formatter | CODE_VERIFIED | 2026-07-27 |
| `sku:braised_beef_tendon_noodle` | runtime assigned | `braised_beef_tendon_noodle` | `SOUP_NOODLE` | `NOODLE` | 红烧牛筋面 | Braised Beef Tendon Noodle | `红` + 尺寸/选项 shorthand | 是 | 尺寸、非默认面型、辣度 | 同上 | 同上 | `中红×1 \| +煎` | `OrderServiceImpl:1813-1966` | CODE_VERIFIED | 2026-07-27 |
| `sku:pickled_vegetable_beef_noodle` | runtime assigned | `pickled_vegetable_beef_noodle` | `SOUP_NOODLE` | `NOODLE` | 酸菜牛肉面 | Pickled Vegetable Beef Noodle | `酸` + 尺寸/选项 shorthand | 是 | 尺寸、非默认面型、辣度 | 同上 | 同上 | `大酸（少s）×1 \| 走牛` | `OrderServiceImpl:1813-1966` | CODE_VERIFIED | 2026-07-27 |
| `sku:vegetable_noodle` | runtime assigned | `vegetable_noodle` | `SOUP_NOODLE` | `NOODLE` | 蔬菜面 | Vegetable Noodle | `素` / `素（肉汤）` + shorthand | 是 | 尺寸、汤底、非默认面型、辣度 | 加减料；套餐小菜另建 COLD task | 同上 | `中素×1 \| +西兰` | `OrderServiceImpl:1969-1982` | CODE_VERIFIED | 2026-07-27 |
| `sku:beef_chow_mein` | runtime assigned | `beef_chow_mein` | `FRIED_NOODLE` | `WOK` | 牛肉炒面 | Beef Chow Mein | `牛炒` | 是 | 辣度；无汤底/面型前缀 | 加蛋、减料、套餐按快照 | 同上 | `牛炒（s）×1 \| 走洋葱` | `OrderServiceImpl:1813-1869` | CODE_VERIFIED | 2026-07-27 |
| `sku:chicken_chow_mein` | runtime assigned | `chicken_chow_mein` | `FRIED_NOODLE` | `WOK` | 鸡肉炒面 | Chicken Chow Mein | `鸡炒` | 是 | 同上 | 同上 | 同上 | `鸡炒×1 \| +煎` | `OrderServiceImpl:1813-1869` | CODE_VERIFIED | 2026-07-27 |
| `sku:tomato_chow_mein` | runtime assigned | `tomato_chow_mein` | `FRIED_NOODLE` | `WOK` | 番茄炒面 | Tomato Chow Mein | `番炒` | 是 | 同上 | 同上 | 同上 | `番炒×1 \| 走番茄` | `OrderServiceImpl:1813-1869` | CODE_VERIFIED | 2026-07-27 |
| `sku:vegetable_chow_mein` | runtime assigned | `vegetable_chow_mein` | `FRIED_NOODLE` | `WOK` | 素菜炒面 | Vegetable Chow Mein | `素炒` | 是 | 同上 | 同上 | 同上 | `素炒×1 \| 走所有菜` | `OrderServiceImpl:1813-1869` | CODE_VERIFIED | 2026-07-27 |
| `sku:cold_noodle_shredded_chicken` | runtime assigned | `cold_noodle_shredded_chicken` | `DRY_NOODLE` | `NOODLE` | 鸡丝凉面 | Cold Noodle with Shredded Chicken | `鸡凉` | 是 | 非默认面型、辣度 | 加减料；套餐小菜另建 COLD task | 同上 | `鸡凉×1 \| 加青` | `OrderServiceImpl:1813-1966` | CODE_VERIFIED | 2026-07-27 |
| `sku:zha_jiang_noodle` | runtime assigned | `zha_jiang_noodle` | `DRY_NOODLE` | `NOODLE` | 炸酱面 | Zha Jiang Noodle | `炸` | 是 | 非默认面型、辣度 | 加减料；套餐小菜另建 COLD task | 同上 | `炸韭×1 \| 走黄瓜` | `OrderServiceImpl:1813-1966` | CODE_VERIFIED | 2026-07-27 |
| `sku:dan_dan_noodle` | runtime assigned | `dan_dan_noodle` | `DRY_NOODLE` | `NOODLE` | 担担面 | Dan Dan Noodle | `担` | 是 | 非默认面型、辣度 | 加减料；套餐小菜另建 COLD task | 同上 | `担细（s）×1 \| +蛋` | `OrderServiceImpl:1813-1966` | CODE_VERIFIED | 2026-07-27 |
| `sku:cucumber_salad` | runtime assigned | `cucumber_salad` | `SIDE` | `COLD` | 拌黄瓜 | Cucumber Salad | `黄瓜` 或任务完整快照 | 是 | 无 | `走花生` 等需求参与分组键 | `{名称} xN` | `黄瓜 x2` / `黄瓜 x1` + `走花生` | `GrabReceiptRenderer:268-432` | TEST_VERIFIED | 2026-07-27 |
| `sku:edamame` | runtime assigned | `edamame` | `SIDE` | `COLD` | 毛豆 | Edamame | `毛豆` 或任务完整快照 | 是 | 无 | 需求和备注参与分组键 | `{名称} xN` | `毛豆 x1` | Seeder; `GrabReceiptRenderer:268-432` | CODE_VERIFIED | 2026-07-27 |
| `sku:shredded_potato` | runtime assigned | `shredded_potato` | `SIDE` | `COLD` | 土豆丝 | Shredded Potato | `土豆` 或任务完整快照 | 是 | 无 | `走洋葱`/`走花生`/`走香菜` 参与分组键 | `{名称} xN` | `土豆 x1` + `走花生` | Seeder; `GrabReceiptRenderer:268-432` | CODE_VERIFIED | 2026-07-27 |
| `sku:braised_beef_shank_salad` | runtime assigned | `braised_beef_shank_salad` | `SIDE` | `COLD` | 拌牛展 | Braised Beef Shank Salad | `牛展` 或任务完整快照 | 是 | 无 | `走香`、`走花生碎`、`走葱` 与备注参与分组键 | `{名称} xN` | `牛展 x2` | Seeder; `GrabReceiptRendererTest` | TEST_VERIFIED | 2026-07-27 |
| `sku:fried_spring_rolls` | runtime assigned | `fried_spring_rolls` | `FRIED` | `DEEPFRIED` | 炸春卷 | Fried Spring Rolls | `炸春卷` | 否，除订单快照 | 无 | options、notes、combo 等完全相同才聚合 | `{数量}*{名称}` | `2*炸春卷` | `GrabReceiptRenderer:311-380` | CODE_VERIFIED | 2026-07-27 |
| `sku:tempura_shrimp` | runtime assigned | `tempura_shrimp` | `FRIED` | `DEEPFRIED` | 炸虾 | Tempura Shrimp | `炸虾` | 否，除订单快照 | 同上 | 同上 | `2*炸虾` | `GrabReceiptRendererTest:68-105` | TEST_VERIFIED | 2026-07-27 |
| `sku:fried_steamed_buns` | runtime assigned | `fried_steamed_buns` | `FRIED` | `DEEPFRIED` | 炸馒头 | Fried Steamed Buns | `炸馒头` | 否，除订单快照 | 同上 | 同上 | `1*炸馒头` | Seeder; renderer grouping key | CODE_VERIFIED | 2026-07-27 |
| `sku:fried_wontons` | runtime assigned | `fried_wontons` | `FRIED` | `DEEPFRIED` | 炸馄饨 | Fried Wontons | `炸馄饨` | 否，除订单快照 | 同上 | 同上 | `1*炸馄饨` | Seeder; renderer grouping key | CODE_VERIFIED | 2026-07-27 |
| `sku:coke` | runtime assigned | `coke` | `DRINK` | `BAR` | 可乐 | Coke | 不创建 GRAB 厨房任务 | 不适用 | 不适用 | 不适用 | 无 GRAB 行 | `RuntimeDataSeeder:479`; direct-serve path | CODE_VERIFIED | 2026-07-27 |
| `sku:diet_coke` | runtime assigned | `diet_coke` | `DRINK` | `BAR` | 健怡可乐 | Diet Coke | 不创建 GRAB 厨房任务 | 不适用 | 不适用 | 不适用 | 无 GRAB 行 | `RuntimeDataSeeder:480`; direct-serve path | CODE_VERIFIED | 2026-07-27 |
| `sku:chinese_herbal_tea` | runtime assigned | `chinese_herbal_tea` | `DRINK` | `BAR` | 王老吉 | Chinese Herbal Tea | 不创建 GRAB 厨房任务 | 不适用 | 不适用 | 不适用 | 无 GRAB 行 | `RuntimeDataSeeder:481`; direct-serve path | CODE_VERIFIED | 2026-07-27 |
| `sku:ice_tea` | runtime assigned | `ice_tea` | `DRINK` | `BAR` | 冰红茶 | Ice Tea | 不创建 GRAB 厨房任务 | 不适用 | 不适用 | 不适用 | 无 GRAB 行 | `RuntimeDataSeeder:482`; direct-serve path | CODE_VERIFIED | 2026-07-27 |
| `sku:shochu` | runtime assigned | `shochu` | `DRINK` | `BAR` | 烧酒 | Shochu | 不创建 GRAB 厨房任务 | 不适用 | 不适用 | 不适用 | 无 GRAB 行 | `RuntimeDataSeeder:483`; direct-serve path | CODE_VERIFIED | 2026-07-27 |
| `sku:sake` | runtime assigned | `sake` | `DRINK` | `BAR` | 清酒 | Sake | 不创建 GRAB 厨房任务 | 不适用 | 不适用 | 不适用 | 无 GRAB 行 | `RuntimeDataSeeder:484`; direct-serve path | CODE_VERIFIED | 2026-07-27 |
| `sku:tsingtao_beer` | runtime assigned | `tsingtao_beer` | `DRINK` | `BAR` | 青岛啤酒 | Tsingtao Beer | 不创建 GRAB 厨房任务 | 不适用 | 不适用 | 不适用 | 无 GRAB 行 | `RuntimeDataSeeder:485`; direct-serve path | CODE_VERIFIED | 2026-07-27 |

### 3.1 当前 inactive 菜品历史附录

本仓库的 `RuntimeDataSeeder` 只列出允许目录；在
`app.seed.force-overwrite=true` 时才把不在该目录内的旧 SKU 设为 inactive。
仓库不保留一份可证明“当前生产 inactive 菜品”的完整名单。因此本附录不
猜测或混入任何 inactive 项；生产门店的 inactive 菜品必须从 Menu API 或
数据库导出后，作为带日期的历史补充加入此处。

验证状态：`UNKNOWN_REQUIRES_OPERATOR_CONFIRMATION`。

## 4. 组合与简称生成规则

### 汤面、干面和凉面

1. `OrderServiceImpl.buildKitchenDisplayNameZh(...)` 仅为稳定 SKU 提供炒面
   和部分干/凉面的短名；其余菜保持订单中文快照。
2. `buildKitchenPrimaryLine(...)` 按以下顺序组合主 shorthand：尺寸、SKU
   基础码、蔬菜面汤底、非默认面型、辣度。
3. 代码已知的基础码包括 `红`、`酸`、`牛炒`、`鸡炒`、`番炒`、`素炒`、
   `炸`、`担`、`鸡凉`。这些短名均绑定上表 SKU，不可由相似中文菜名推断。
4. 默认面型不添加简称；非默认值按 `二`、`三`、`细`、`毛`、`韭`、`宽`、
   `大宽` 转换。无法识别的值保留原快照，不猜测转换。
5. `KitchenNoodlePrintFormatter` 可将完整
   `special_instructions_snapshot` 作为主行，或把它接在菜单/厨房快照后面。
   因此完整厨房 shorthand 优先于重新从名称猜测。
6. 单碗面始终在第一段后显示 `×1`；同一稳定分组键的多碗显示
   `({完整配置}) ×N`。

### 炒面固定简称

- `beef_chow_mein` -> `牛炒`
- `chicken_chow_mein` -> `鸡炒`
- `tomato_chow_mein` -> `番炒`
- `vegetable_chow_mein` -> `素炒`

这些是代码绑定 SKU 的规则，不是通用中文词形规则。

### 小菜、炸物和套餐小菜

- COLD 小菜必须显示准确菜名或已写入的完整厨房快照，绝不打印通用
  `小菜 xN`。
- 只有菜名/厨房显示、按字典排序后的需求集合和备注完全相同的小菜才合并。
- 套餐小菜是独立的 COLD `KitchenTask`，不是面条主行中的 `小菜` 总称。
- 炸物的聚合键包括 `menu_item_id`、分类、station、菜名、special、备注、
  `combo_role` 以及完整稳定 option 签名。任一项不同都不得合并。
- 炸物格式为 ASCII 星号前缀：`{quantity}*{displayName}`；例如
  `2*炸虾`。这与 HOT_KITCHEN 的 `炸虾 ×3` 格式不同，不能混用。

## 5. 选项、加减料和备注

### 选项与套餐

- Combo、蛋和小菜由稳定 option group/code 表示；组合主项和套餐小菜按
  订单快照生成，不能只看显示名。
- `combo_edamame`、`combo_shredded_potato`、`combo_cucumber_salad` 创建
  单独小菜任务。套餐蛋保留在面条 shorthand 中。
- 同一菜品的不同辣度、蛋、套餐小菜、加料、减料、option quantity 或备注
  都会改变分组键，必须分开打印。

### 加料、减料、辣度和备注

- 加料使用稳定 `option_code_snapshot`；代码为旧数据保留名称回退。
- 常见转换包括 `+蛋`、`+煎`、`+面`、`+肉`、`+萝`、`+西兰` 等；多份
  加料保留数量，例如 `+蛋×2`。
- 减料使用 `走…` / `少…` shorthand；未知稳定码回退到选项中文快照。
- 不辣不写辣度；少辣、正常辣、加辣分别映射为 `（少s）`、`（s）`、
  `（大s）`，未知辣度使用当前代码的保守 `（s）` 回退。
- `order_items.notes` 以独立 `备注：{notes}` 行打印，不应并入厨房任务
  special snapshot；空备注隐藏。

### 葱和香菜压缩

- 同一显示块同时存在加葱和加香菜时，压缩为 `加青`。
- 同一显示块同时存在走葱和走香菜时，压缩为 `走青`。
- 只有其中之一时必须保留原名称，例如 `加葱`、`走香菜`。
- 同时出现加与走的混合方向时保留原指令，不做跨方向压缩。
- `加上海青` 是独立食材，必须保持完整名称，不能压缩为 `加青`。

## 6. GRAB 排序、数量与 fallback

1. GRAB 先排序 COLD 小菜，再 DEEPFRIED，随后 NOODLE/WOK，最后其他
   fallback 任务；同优先级按任务创建时间、任务 ID 排序。
2. options 与 notes 永远跟随其来源菜品/任务块。
3. 面类单碗：`{第一段}×1`，如有后续段则 `\|` 后继续显示；多碗：
   `({完整配置}) ×N`。
4. 小菜和普通未分组任务使用 `{名称} xN`；炸物使用 `{数量}*{名称}`。
5. 不能解析稳定短名或 option code 时，回退到
   `item_name_snapshot_zh`，再回退英文快照，最后才使用 `Item`。不得
   猜测新的厨房简称。

## 7. 当前未知和现场确认事项

- 当前生产 `menu_item_id` 值、每个 SKU 的 active/sold-out 状态和现场新增
  菜品：`UNKNOWN_REQUIRES_OPERATOR_CONFIRMATION`。
- 未列入仓库种子的历史 inactive 菜品：
  `UNKNOWN_REQUIRES_OPERATOR_CONFIRMATION`。
- 任何由管理员在运行时添加且没有对应稳定 SKU/option code 的菜品简称：
  `UNKNOWN_REQUIRES_OPERATOR_CONFIRMATION`，应先使用中文快照 fallback。

## 8. 菜单变更同步流程

修改菜单、SKU、station、option code、combo side 或 GRAB renderer 时：

1. 先确认稳定 SKU 和 option code 是否保持兼容；不得只改中文显示名后
   假定厨房简称仍正确。
2. 更新本文件的对应行、示例、证据路径和最后确认日期。
3. 若菜品 inactive，移至本文件的历史附录，不与 active/source 目录混合。
4. 为变更的 SKU 添加或更新 GRAB Renderer / Order Service 回归测试。
5. 用不含真实顾客资料的批准测试订单完成现场确认后，标记
   `OPERATOR_CONFIRMED`；没有该确认时保留 `CODE_VERIFIED` 或
   `UNKNOWN_REQUIRES_OPERATOR_CONFIRMATION`。

## 9. 回归测试清单

- `GrabReceiptRendererTest`: COLD 小菜相同/不同需求的合并与分离。
- `GrabReceiptRendererTest`: 炸虾等炸物的 `2*`、`3*` 合并，及 notes、
  options、辣度、combo 差异不合并。
- `GrabReceiptRendererTest`: 单碗 `×1`、多碗 `×N`、面类稳定分组和
  green shorthand。
- `OrderServiceImplTest`: SKU -> 厨房短名、尺寸/汤底/面型/辣度、加减料、
  combo side 独立任务和快照生成。
- `HotKitchenReceiptRendererTest`: HOT_KITCHEN 自身的数量格式与 GRAB
  不混淆。
- 经批准的现场测试：菜单改动后验证 GRAB 出纸内容与本规则一致，同时不
  记录顾客信息、token 或 raw payload。

## 10. 关键代码证据

- `backend/src/main/java/com/restaurant/system/order/service/impl/OrderServiceImpl.java`
  - `createKitchenTasks(...)`：写入厨房快照（约 1368-1405）
  - `buildKitchenDisplayNameZh(...)`：SKU 短名（1813-1825）
  - `buildKitchenPrimaryLine(...)`：尺寸/基础码/汤底/面型/辣度（1827-1855）
  - 加减料和稳定 option-code 回退（1985-2090）
- `backend/src/main/java/com/restaurant/system/printing/renderer/GrabReceiptRenderer.java`
  - station 排序与快照输出（约 64-145、163-265）
  - 小菜和炸物聚合键（268-380）
  - green 压缩（444-640）
- `backend/src/main/java/com/restaurant/system/printing/renderer/KitchenNoodlePrintFormatter.java`
  - 面类识别、稳定分组、单碗和多碗数量格式（约 22-126）
- `backend/src/test/java/com/restaurant/system/printing/renderer/GrabReceiptRendererTest.java`
  - 小菜、炸物、面类和 green 规则回归覆盖。
- `backend/src/main/java/com/restaurant/system/common/config/RuntimeDataSeeder.java`
  - 菜单目录和选项种子（430-540）。
