# AL-003A 最终菜单审计与三方比较

## 文档状态

| 字段 | 值 |
| --- | --- |
| Loop | `AL-003A_CHINATOWN_MENU_COMPARISON` |
| 仓库基线 | `2613344d403365d61283ae440de16edffaaad788`（PR #40 已合并后的 `origin/main`） |
| Migration 基线 | STG-005A 已占用 V9：`V9__add_staging_synthetic_bootstrap_requests.sql`；AL-003 计划使用 V10 |
| Chinatown 菜单来源 | `Lanzhou Montreal Chinatown Menu 2026-02-02.pdf` |
| 最终状态 | `AL-003A_FINAL_COMPARISON_READY_FOR_AL003_IMPLEMENTATION` |
| Live St-Denis 访问 | 未执行 |
| Production / Staging 访问 | 未执行 |
| 菜单克隆或数据变更 | 未执行 |

本报告严格区分三套数据：

1. **St-Denis LIVE Source Menu**：只有实际 Live 数据库的受限只读证据可以证明。
2. **Repository Seed Menu**：`RuntimeDataSeeder` 和 `menuImportSeed.ts` 中的历史开发 seed，只标记为 `REPOSITORY_SEED_ONLY`。
3. **Chinatown PDF Menu**：Chinatown target 菜单与价格的业务权威来源。

任何 Repository Seed 内容均不得写成 St-Denis Live 事实。

### 固定 Source Store

| 字段 | 固定值 |
| --- | --- |
| Source Store | St-Denis |
| Store ID | `1` |
| AL-003 唯一 clone source | Store 1 当前 Live 菜单 |
| 禁止作为 clone source | `RuntimeDataSeeder`、`menuImportSeed.ts`、任何 Repository Seed |

Repository Seed 只保留为历史参考。AL-003 不得由 seed 重建、补齐或替代 Store 1 Live 菜单。

## 1. Current Repository Seed

本节只描述仓库代码中当前存在的历史 seed。所有内容统一分类为：

`REPOSITORY_SEED_ONLY`

### 1.1 Category 清单

后端 `RuntimeDataSeeder` 与前端 `menuImportSeed.ts` 均定义以下六个 category：

| 顺序 | Code | 中文 | 英文 | 分类 |
| ---: | --- | --- | --- | --- |
| 1 | `SOUP_NOODLE` | 汤面 | Soup Noodle | `REPOSITORY_SEED_ONLY` |
| 2 | `FRIED_NOODLE` | 炒面 | Stir-Fried Noodles | `REPOSITORY_SEED_ONLY` |
| 3 | `DRY_NOODLE` | 拌面 | Mixed / Dry / Cold Noodles | `REPOSITORY_SEED_ONLY` |
| 4 | `SIDE` | 小菜 | Side Dishes | `REPOSITORY_SEED_ONLY` |
| 5 | `FRIED` | 炸物 | Fried Items | `REPOSITORY_SEED_ONLY` |
| 6 | `DRINK` | 饮品 | Drinks | `REPOSITORY_SEED_ONLY` |

### 1.2 Station 清单

| 来源 | 顺序 | Code | 名称 | 分类 |
| --- | ---: | --- | --- | --- |
| RuntimeDataSeeder | 1 | `NOODLE` | 面档 | `REPOSITORY_SEED_ONLY` |
| RuntimeDataSeeder | 2 | `WOK` | 炒锅 | `REPOSITORY_SEED_ONLY` |
| RuntimeDataSeeder | 3 | `COLD` | 冷菜 | `REPOSITORY_SEED_ONLY` |
| RuntimeDataSeeder | 4 | `DEEPFRIED` | 炸物 | `REPOSITORY_SEED_ONLY` |
| RuntimeDataSeeder | 5 | `BAR` | 吧台 | `REPOSITORY_SEED_ONLY` |
| menuImportSeed.ts | - | `NOODLE` | 由 item 引用 | `REPOSITORY_SEED_ONLY` |
| menuImportSeed.ts | - | `WOK` | 由 item 引用 | `REPOSITORY_SEED_ONLY` |
| menuImportSeed.ts | - | `COLD` | 由 item 引用 | `REPOSITORY_SEED_ONLY` |
| menuImportSeed.ts | - | `DEEPFRIED` | 由 item 引用 | `REPOSITORY_SEED_ONLY` |
| menuImportSeed.ts | - | `BEVERAGE` | 由 drink item 引用 | `REPOSITORY_SEED_ONLY` |

`BAR` 与 `BEVERAGE` 的差异证明两份历史 seed 自身也不是同一份可执行 Live 快照。

### 1.3 Menu item 与 SKU 清单

两份 seed 均列出 26 个 item；以下价格、名称和 station 只代表 seed：

| Category | Station（后端 / 前端） | SKU | 中文 | 英文 | Seed 价格 | 分类 |
| --- | --- | --- | --- | --- | ---: | --- |
| SOUP_NOODLE | NOODLE | `traditional_beef_noodle` | 传统牛肉面 | Traditional Beef Noodle | 12.50 | `REPOSITORY_SEED_ONLY` |
| SOUP_NOODLE | NOODLE | `braised_beef_tendon_noodle` | 红烧牛筋面 | Braised Beef Tendon Noodle | 15.80 | `REPOSITORY_SEED_ONLY` |
| SOUP_NOODLE | NOODLE | `pickled_vegetable_beef_noodle` | 酸菜牛肉面 | Pickled Vegetable Beef Noodle | 14.80 | `REPOSITORY_SEED_ONLY` |
| SOUP_NOODLE | NOODLE | `vegetable_noodle` | 蔬菜面 | Vegetable Noodle | 13.80 | `REPOSITORY_SEED_ONLY` |
| FRIED_NOODLE | WOK | `beef_chow_mein` | 牛肉炒面 | Beef Chow Mein | 14.50 | `REPOSITORY_SEED_ONLY` |
| FRIED_NOODLE | WOK | `chicken_chow_mein` | 鸡肉炒面 | Chicken Chow Mein | 13.80 | `REPOSITORY_SEED_ONLY` |
| FRIED_NOODLE | WOK | `tomato_chow_mein` | 番茄炒面 | Tomato Chow Mein | 13.20 | `REPOSITORY_SEED_ONLY` |
| FRIED_NOODLE | WOK | `vegetable_chow_mein` | 素菜炒面 | Vegetable Chow Mein | 12.80 | `REPOSITORY_SEED_ONLY` |
| DRY_NOODLE | NOODLE | `cold_noodle_shredded_chicken` | 鸡丝凉面 | Cold Noodle with Shredded Chicken | 13.80 | `REPOSITORY_SEED_ONLY` |
| DRY_NOODLE | NOODLE | `zha_jiang_noodle` | 炸酱面 | Zha Jiang Noodle | 13.50 | `REPOSITORY_SEED_ONLY` |
| DRY_NOODLE | NOODLE | `dan_dan_noodle` | 担担面 | Dan Dan Noodle | 13.50 | `REPOSITORY_SEED_ONLY` |
| SIDE | COLD | `cucumber_salad` | 拌黄瓜 | Cucumber Salad | 4.50 | `REPOSITORY_SEED_ONLY` |
| SIDE | COLD | `edamame` | 毛豆 | Edamame | 4.50 | `REPOSITORY_SEED_ONLY` |
| SIDE | COLD | `shredded_potato` | 土豆丝 | Shredded Potato | 4.80 | `REPOSITORY_SEED_ONLY` |
| SIDE | COLD | `braised_beef_shank_salad` | 拌牛展 | Braised Beef Shank Salad | 8.80 | `REPOSITORY_SEED_ONLY` |
| FRIED | DEEPFRIED | `fried_spring_rolls` | 炸春卷 | Fried Spring Rolls | 5.99 | `REPOSITORY_SEED_ONLY` |
| FRIED | DEEPFRIED | `tempura_shrimp` | 炸虾 | Tempura Shrimp | 8.99 | `REPOSITORY_SEED_ONLY` |
| FRIED | DEEPFRIED | `fried_steamed_buns` | 炸馒头 | Fried Steamed Buns | 5.99 | `REPOSITORY_SEED_ONLY` |
| FRIED | DEEPFRIED | `fried_wontons` | 炸馄饨 | Fried Wontons | 5.99 | `REPOSITORY_SEED_ONLY` |
| DRINK | BAR / BEVERAGE | `coke` | 可乐 | Coke | 2.50 | `REPOSITORY_SEED_ONLY` |
| DRINK | BAR / BEVERAGE | `diet_coke` | 健怡可乐 | Diet Coke | 2.50 | `REPOSITORY_SEED_ONLY` |
| DRINK | BAR / BEVERAGE | `chinese_herbal_tea` | 王老吉 | Chinese Herbal Tea | 3.50 | `REPOSITORY_SEED_ONLY` |
| DRINK | BAR / BEVERAGE | `ice_tea` | 冰红茶 | Ice Tea | 2.80 | `REPOSITORY_SEED_ONLY` |
| DRINK | BAR / BEVERAGE | `shochu` | 烧酒 | Shochu | 8.50 | `REPOSITORY_SEED_ONLY` |
| DRINK | BAR / BEVERAGE | `sake` | 清酒 | Sake | 7.80 | `REPOSITORY_SEED_ONLY` |
| DRINK | BAR / BEVERAGE | `tsingtao_beer` | 青岛啤酒 | Tsingtao Beer | 5.50 | `REPOSITORY_SEED_ONLY` |

### 1.4 Option 完整清单

静态解析结果：

- `menuImportSeed.ts`：313 个显式 option。
- `RuntimeDataSeeder`：按当前 builder 静态展开为 324 个 option。
- 差值 11：后端为 11 个可套餐面类各增加一个 `COMBO_SIDE_REMOVE` 的 `combo_cucumber_no_peanut`；前端历史 seed 没有这些 parent-linked option。
- 前端 option code 通常是 `<item_sku>_<historical_suffix>`；后端使用 item 内可复用的通用 code，例如 `noodle_erxi`、`extra_meat`。二者不可混为 Live code。

#### 1.4.1 共享 option 定义

| Set | Code / 历史 suffix | 中文 / 英文 | Price delta | Group |
| --- | --- | --- | ---: | --- |
| COMBO | `combo` / `addon_combo` | 套餐 / Combo | 5.00 | COMBO |
| COMBO | `combo_tea_egg` / `addon_combo_tea_egg` | 套餐卤蛋 / Combo Tea Egg | 0 | COMBO_EGG |
| COMBO | `combo_fried_egg` / `addon_combo_fried_egg` | 套餐煎蛋 / Combo Fried Egg | 0 | COMBO_EGG |
| COMBO | `combo_edamame` / `addon_combo_edamame` | 套餐毛豆 / Combo Edamame | 0 | COMBO_SIDE |
| COMBO | `combo_shredded_potato` / `addon_combo_shredded_potato` | 套餐土豆丝 / Combo Shredded Potato | 0 | COMBO_SIDE |
| COMBO | `combo_cucumber_salad` / `addon_combo_cucumber_salad` | 套餐拌黄瓜 / Combo Cucumber Salad | 0 | COMBO_SIDE |
| COMBO | `combo_cucumber_no_peanut` | 走花生 / No Peanut | 0 | COMBO_SIDE_REMOVE；仅后端 seed |
| SIZE | `size_regular` | 中碗 / Regular | 0 | SIZE |
| SIZE | `size_large` | 大碗 / Large | 2.00 | SIZE |
| NOODLE_TYPE | `noodle_erxi` / `noodle_type_erxi` | 二细 / Erxi | 0 | NOODLE_TYPE |
| NOODLE_TYPE | `noodle_sanxi` / `noodle_type_sanxi` | 三细 / Sanxi | 0 | NOODLE_TYPE |
| NOODLE_TYPE | `noodle_thin` / `noodle_type_thin` | 细 / Thin | 0 | NOODLE_TYPE |
| NOODLE_TYPE | `noodle_capillary` / `noodle_type_capillary` | 毛细 / Capillary | 0 | NOODLE_TYPE |
| NOODLE_TYPE | `noodle_leek_leaf` / `noodle_type_leek_leaf` | 韭叶 / Leek Leaf | 0 | NOODLE_TYPE |
| NOODLE_TYPE | `noodle_wide` / `noodle_type_wide` | 宽 / Wide | 0 | NOODLE_TYPE |
| NOODLE_TYPE | `noodle_extra_wide` / `noodle_type_extra_wide` | 大宽 / Extra Wide | 0 | NOODLE_TYPE |
| SPICY | `spicy_none` / `spicy_level_non_spicy` | 不辣 / Non-Spicy | 0 | SPICY_LEVEL |
| SPICY | `spicy_mild` / `spicy_level_mild` | 少辣 / Mild | 0 | SPICY_LEVEL |
| SPICY | `spicy_regular` / `spicy_level_regular` | 正常辣 / Regular | 0 | SPICY_LEVEL |
| SPICY | `spicy_extra` / `spicy_level_extra` | 加辣 / Extra | 0 | SPICY_LEVEL |
| SOUP_BASE | `soup_vegan` / `soup_base_vegan_broth` | 素汤 / Vegan Broth | 0 | SOUP_BASE |
| SOUP_BASE | `soup_beef` / `soup_base_beef_broth` | 肉汤 / Beef Broth | 0 | SOUP_BASE |

#### 1.4.2 全部加料/减料定义

以下为两份 seed 联合出现的完整 option 语义清单。前端显式 code 使用 item SKU 前缀；后端 code 为表中通用 code。

| Type | 后端通用 code | 中文 / 英文 | Price delta |
| --- | --- | --- | ---: |
| addon | `extra_noodle` | 加面 / Extra Noodle | 3.99 |
| addon | `tea_egg` | 加蛋或加卤蛋 / Extra Tea Egg | 1.99 |
| addon | `extra_meat` | 加肉 / Extra Meat或Beef | 6.99 |
| addon | `fried_egg` | 加煎蛋 / Extra Fried Egg | 1.80；WOK 为 1.99 |
| addon | `bok_choy` | 加上海青 / Extra Bok Choy | 3.00 |
| addon | `cilantro` | 加香菜 / Extra Cilantro | 0；炸酱面为 0.50 |
| addon | `green_onion` | 加葱 / Extra Green Onion | 0；炸酱面为 0.50 |
| addon | `extra_radish` | 加萝卜 / Extra Radish | 1.00 |
| addon | `broccoli` | 加西兰花 / Extra Broccoli | 1.20 |
| addon | `corn` | 加玉米 / Extra Corn | 1.20 |
| addon | `seaweed` | 加海菜 / Extra Seaweed | 1.20 |
| addon | `mushroom` | 加蘑菇 / Extra Mushroom | 1.20 |
| addon | `carrot_slice` | 加胡萝卜片 / Extra Carrot Slice | 1.20 |
| addon | `extra_sauce` | 加酱 / Extra Sauce | 1.00 |
| remove | `remove_cilantro` | 走香菜 / No Cilantro | 0 |
| remove | `remove_green_onion` | 走葱 / No Green Onion | 0 |
| remove | `remove_onion` | 走洋葱 / No Onion | 0 |
| remove | `remove_beef` | 走牛肉 / No Beef | 0 |
| remove | `remove_radish` | 走萝卜 / No Radish | 0 |
| remove | `remove_noodle` | 走面 / No Noodle | 0 |
| remove | `less_noodle` | 少面 / Less Noodle | 0 |
| remove | `remove_bok_choy` | 走上海青 / No Bok Choy | 0 |
| remove | `remove_broccoli` | 走西兰花 / No Broccoli | 0 |
| remove | `remove_corn` | 走玉米 / No Corn | 0 |
| remove | `remove_mushroom` | 走蘑菇 / No Mushroom | 0 |
| remove | `remove_seaweed` | 走海菜 / No Seaweed | 0 |
| remove | `remove_carrot` | 走胡萝卜或胡萝卜片 / No Carrot | 0 |
| remove | `remove_cucumber` | 走黄瓜 / No Cucumber | 0 |
| remove | `remove_edamame` | 走毛豆 / No Edamame | 0 |
| remove | `remove_peanut` | 走花生 / No Peanut | 0 |
| remove | `remove_crushed_peanut` | 走花生碎 / No Crushed Peanut | 0 |
| remove | `remove_bean_sprouts` | 走豆芽 / No Bean Sprouts | 0 |
| remove | `remove_green_pepper` | 走青椒 / No Green Pepper | 0 |
| remove | `remove_cabbage` | 走大头菜 / No Cabbage | 0 |
| remove | `remove_zucchini` | 走西葫芦 / No Zucchini | 0 |
| remove | `remove_all_vegetables` | 走所有菜 / No Vegetables | 0 |
| remove | `remove_tomato` | 走番茄 / No Tomato | 0 |

#### 1.4.3 menuImportSeed.ts 精确 code suffix 清单

`menuImportSeed.ts` 的每个完整 option code 均可由
`<menuItemSku>_<下列 suffix>` 唯一还原；具体 SKU 与 suffix 的适用关系见下一节分配矩阵。
下列 60 个 suffix 是该文件中出现的完整去重集合：

- `noodle_type`：`noodle_type_capillary`, `noodle_type_erxi`,
  `noodle_type_extra_wide`, `noodle_type_leek_leaf`, `noodle_type_sanxi`,
  `noodle_type_thin`, `noodle_type_wide`。
- `spicy_level`：`spicy_level_extra`, `spicy_level_mild`,
  `spicy_level_non_spicy`, `spicy_level_regular`。
- `size`：`size_large`, `size_regular`。
- `soup_base`：`soup_base_beef_broth`, `soup_base_vegan_broth`。
- `addon`：`addon_combo`, `addon_combo_cucumber_salad`,
  `addon_combo_edamame`, `addon_combo_fried_egg`,
  `addon_combo_shredded_potato`, `addon_combo_tea_egg`,
  `addon_extra_beef`, `addon_extra_bok_choy`, `addon_extra_broccoli`,
  `addon_extra_carrot_slice`, `addon_extra_cilantro`, `addon_extra_corn`,
  `addon_extra_fried_egg`, `addon_extra_green_onion`, `addon_extra_meat`,
  `addon_extra_mushroom`, `addon_extra_noodle`, `addon_extra_radish`,
  `addon_extra_sauce`, `addon_extra_seaweed`, `addon_extra_tea_egg`。
- `remove`：`remove_less_noodle`, `remove_no_bean_sprouts`,
  `remove_no_beef`, `remove_no_bok_choy`, `remove_no_broccoli`,
  `remove_no_cabbage`, `remove_no_carrot`, `remove_no_carrot_slice`,
  `remove_no_cilantro`, `remove_no_corn`, `remove_no_crushed_peanut`,
  `remove_no_cucumber`, `remove_no_edamame`, `remove_no_green_onion`,
  `remove_no_green_pepper`, `remove_no_mushroom`, `remove_no_noodle`,
  `remove_no_onion`, `remove_no_peanut`, `remove_no_radish`,
  `remove_no_seaweed`, `remove_no_tomato`, `remove_no_vegetables`,
  `remove_no_zucchini`。

#### 1.4.4 Option 到 SKU 的完整分配矩阵

此矩阵配合前两张定义表，完整覆盖两个 seed 中的每个 option。`Frontend count` 是 `menuImportSeed.ts` 的显式行数；`Backend count` 是当前 builder 静态展开数。

| SKU | 共享 sets | 专属 addon/remove | Frontend count | Backend count | 分类 |
| --- | --- | --- | ---: | ---: | --- |
| `traditional_beef_noodle` | COMBO, SIZE, NOODLE_TYPE, SPICY | 加面/蛋/肉/煎蛋/上海青/香菜/葱/萝卜；走香菜/葱/牛肉/面/萝卜；少面 | 33 | 34 | `REPOSITORY_SEED_ONLY` |
| `braised_beef_tendon_noodle` | 同上 | 同上 | 33 | 34 | `REPOSITORY_SEED_ONLY` |
| `pickled_vegetable_beef_noodle` | 同上 | 同上 | 33 | 34 | `REPOSITORY_SEED_ONLY` |
| `vegetable_noodle` | COMBO, SIZE, NOODLE_TYPE, SPICY, SOUP_BASE | 加面/蛋/肉/煎蛋/上海青/西兰花/玉米/海菜/蘑菇/胡萝卜片；走面/上海青/西兰花/玉米/蘑菇/海菜/胡萝卜片；少面 | 39 | 40 | `REPOSITORY_SEED_ONLY` |
| `beef_chow_mein` | COMBO, SPICY | 加煎蛋/卤蛋；走豆芽/洋葱/青椒/西兰花/大头菜/西葫芦/所有菜/番茄 | 20 | 21 | `REPOSITORY_SEED_ONLY` |
| `chicken_chow_mein` | 同上 | 同上 | 20 | 21 | `REPOSITORY_SEED_ONLY` |
| `tomato_chow_mein` | 同上 | 同上 | 20 | 21 | `REPOSITORY_SEED_ONLY` |
| `vegetable_chow_mein` | 同上 | 同上 | 20 | 21 | `REPOSITORY_SEED_ONLY` |
| `cold_noodle_shredded_chicken` | COMBO, NOODLE_TYPE, SPICY | 加面/蛋/肉/煎蛋/上海青/香菜/葱；走香菜/葱/胡萝卜/花生 | 28 | 29 | `REPOSITORY_SEED_ONLY` |
| `zha_jiang_noodle` | COMBO, NOODLE_TYPE, SPICY | 加面/蛋/肉/煎蛋/上海青/酱/香菜/葱；走香菜/葱/胡萝卜/黄瓜/毛豆/上海青 | 31 | 32 | `REPOSITORY_SEED_ONLY` |
| `dan_dan_noodle` | COMBO, NOODLE_TYPE, SPICY | 加面/蛋/肉/煎蛋/上海青/酱/香菜/葱；走香菜/葱/花生/上海青 | 29 | 30 | `REPOSITORY_SEED_ONLY` |
| `cucumber_salad` | - | 走花生 | 1 | 1 | `REPOSITORY_SEED_ONLY` |
| `edamame` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `shredded_potato` | - | 走洋葱/花生/香菜 | 3 | 3 | `REPOSITORY_SEED_ONLY` |
| `braised_beef_shank_salad` | - | 走香菜/花生碎/葱 | 3 | 3 | `REPOSITORY_SEED_ONLY` |
| `fried_spring_rolls` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `tempura_shrimp` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `fried_steamed_buns` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `fried_wontons` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `coke` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `diet_coke` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `chinese_herbal_tea` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `ice_tea` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `shochu` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `sake` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |
| `tsingtao_beer` | - | 无 | 0 | 0 | `REPOSITORY_SEED_ONLY` |

## 2. Expected St-Denis Live Menu

### 2.1 可从仓库证明的模型与架构

- Live menu 应由 Store-scoped `menu_categories`、`stations`、`menu_items`、`menu_item_options` 与 Store `menu_revision` 表示。
- `menu_items.sku` 和 `menu_item_options.option_code` 是候选稳定标识。
- `/api/v1/menu/catalog` 只返回 active catalog，不足以证明 inactive 行或完整 clone source。
- `/api/v1/admin/menu/management-context` 只返回 Store/category/station context，不是完整 item/option 导出。
- 现有 clone 计划要求在执行时读取实际 source Store，不能由 seed 重建。

### 2.2 Seed 条目的 Live 判定

仓库代码不能证明 26 个 seed item 中任何一个当前存在于 St-Denis Live，也不能证明其当前名称、价格、active/sold-out、排序或 option。故不得把任一条目宣布为“历史 placeholder”或“仍然有效”。

| Seed 类型 | 仓库能证明的事实 | Live 判定 |
| --- | --- | --- |
| 被当前 renderer/service/test 使用的 SKU | 代码仍认识该稳定标识或语义 | `LIVE_SOURCE_EVIDENCE_REQUIRED` |
| WOK、炸物、酒类 seed | 历史 seed 存在，且 Chinatown PDF 不包含 | St-Denis 是否仍有该项为 `LIVE_SOURCE_EVIDENCE_REQUIRED`；Chinatown target 可按 Owner 规则排除 |
| 菜单名称、价格、active、sold-out、sort | seed 中存在历史值 | `LIVE_SOURCE_EVIDENCE_REQUIRED` |
| option code/group/parent/price | 两份 seed 甚至存在 code 与数量漂移 | `LIVE_SOURCE_EVIDENCE_REQUIRED` |
| `BAR` / `BEVERAGE` | 历史 seed 不一致 | Live station code 为 `LIVE_SOURCE_EVIDENCE_REQUIRED` |

结论：Repository Seed 只能提供候选 SKU 与历史 option 语义，不能提供 Live source 行。

## 3. Chinatown PDF

### 3.1 最终 Category 与顺序

Owner 已决定仅使用中英文，不建法文本地化：

| 顺序 | Category | 中文 |
| ---: | --- | --- |
| 1 | `SOUP_NOODLE` | Soup Noodles / 汤面 |
| 2 | `DRY_NOODLE` | Dry Noodles / 干拌面 |
| 3 | `SIDE_DISHES` | Side Dishes / 小菜 |
| 4 | `DRINK` | Drinks / 饮料 |

### 3.2 Item、价格、尺寸与供应规则

PDF 价格是 target 权威。忽略旧 Backlog 价格。

| 顺序 | Category | 中文 | 英文 | PDF 价格/尺寸 | 最终规则 |
| ---: | --- | --- | --- | --- | --- |
| 1 | SOUP_NOODLE | 兰州牛肉面 | Traditional LanZhou Hand-pull Beef Noodle | S 14.99 / M 16.99 / L 18.99 | 汤面第一项 |
| 2 | SOUP_NOODLE | 红烧牛筋面 | Braised Beef Tendon Noodle | 17.99 | 不实现 schedule；周末供应由运营控制 |
| 3 | SOUP_NOODLE | 蔬菜面 | Vegetable Noodle | S 14.99 / M 16.99 / L 18.99 | 汤面第三项 |
| 1 | DRY_NOODLE | 担担面 | Dan Dan Noodle | S 15.99 / M 17.99 | 干面第一项 |
| 2 | DRY_NOODLE | 老兰州炸酱面 | Zha Jiang Noodle | 17.99 | 干面第二项 |
| 1 | SIDE_DISHES | 兰州辣拌牛展 | Beef Shank Mix With Home Made Spicy Sauce | 9.99 | `COLD`；小菜第一项 |
| 2 | SIDE_DISHES | 香辣黄瓜 | Cucumber Mix With Home Made Spicy Sauce | 4.99 | 小菜第二项 |
| 3 | SIDE_DISHES | 雪菜毛豆 | Edamame With Preserved Vegetable | 4.99 | 小菜第三项 |
| 4 | SIDE_DISHES | 海菜土豆丝 | Seaweed Potato Salad | 4.99 | 小菜第四项 |
| 5 | SIDE_DISHES | 椒麻鸡 | Sichuan Pepper Chicken | 9.99 | `SIDE_DISHES` / `COLD`；小菜第五项 |
| 6 | SIDE_DISHES | 茶叶卤蛋 | Tea Boil Egg | 1.99 | standalone item + add-on；小菜第六项 |
| 1 | DRINK | 可乐 | Coke | 3.00 | 按 PDF 顺序 |
| 2 | DRINK | 健怡可乐 | Diet Coke | 3.00 | 按 PDF 顺序 |
| 3 | DRINK | 七喜 | 7 Up | 3.00 | 新 target item；按 PDF 顺序 |
| 4 | DRINK | 姜汁汽水 | Ginger Ale | 3.00 | 新 target item；按 PDF 顺序 |
| 5 | DRINK | 冰茶 | Ice Tea | 3.00 | 按 PDF 顺序 |
| 6 | DRINK | 中式凉茶 | Chinese Herb Tea | 3.00 | 按 PDF 顺序；与 Live SKU 的对应需 DB 证据 |

### 3.3 Combo

| Combo | 主菜 | 包含 | PDF 价格 | Owner 最终决定 |
| --- | --- | --- | --- | --- |
| 1 | 兰州牛肉面 | 小菜任选 + 茶叶卤蛋 | S 19.99 / M 21.99 / L 23.99 | 只适用于此主菜 |
| 2 | 老兰州炸酱面 | 小菜任选 + 茶叶卤蛋 | 22.99 | 只适用于此主菜 |
| 3 | 蔬菜面 | 小菜任选 + 茶叶卤蛋 | S 19.99 / M 21.99 / L 23.99 | Owner 明确包括一份茶叶卤蛋 |
| 4 | 担担面 | 小菜任选 + 茶叶卤蛋 | S 20.99 / M 22.99 | 只适用于此主菜 |

小菜 choice：香辣黄瓜、雪菜毛豆、海菜土豆丝。

### 3.4 面型、加料与减料

- 所有五种 target 面均适用七种面型：毛细、细、三细、二细、韭叶、宽、大宽。
- PDF 明确加肉 6.99、加蛋 1.99；已有 St-Denis Live 加/减料即使未印在 PDF 也全部保留。
- “已有 St-Denis Live option”必须来自 Live 数据库，不得用 Repository Seed 代替。
- 茶叶卤蛋同时为 standalone item 和 add-on option。
- 仅中文 + 英文；不创建法文 localization。

## 4. 三方比较

`St-Denis LIVE` 列未查询时只能写 `LIVE_SOURCE_REQUIRED`。分类可组合使用，以同时表达 target 动作和证据状态。

| St-Denis LIVE | Repository Seed | Chinatown PDF | 分类 |
| --- | --- | --- | --- |
| `LIVE_SOURCE_REQUIRED` | `traditional_beef_noodle`，传统牛肉面，12.50 | 兰州牛肉面，S/M/L 14.99/16.99/18.99 | `RENAMED`, `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `braised_beef_tendon_noodle`，15.80 | 红烧牛筋面，17.99 | `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `vegetable_noodle`，13.80 | 蔬菜面，S/M/L 14.99/16.99/18.99 | `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `zha_jiang_noodle`，炸酱面，13.50 | 老兰州炸酱面，17.99 | `RENAMED`, `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `dan_dan_noodle`，13.50 | 担担面，S/M 15.99/17.99 | `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | 无 standalone item；有历史 egg option | 茶叶卤蛋 1.99，standalone + add-on | `NEW_IN_CHINATOWN`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `braised_beef_shank_salad`，拌牛展，8.80 | 兰州辣拌牛展 9.99 | `RENAMED`, `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | Seed 无椒麻鸡 | 椒麻鸡 9.99，SIDE_DISHES/COLD | `NEW_IN_CHINATOWN`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `edamame`，毛豆，4.50 | 雪菜毛豆 4.99 | `RENAMED`, `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `shredded_potato`，土豆丝，4.80 | 海菜土豆丝 4.99 | `RENAMED`, `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `cucumber_salad`，拌黄瓜，4.50 | 香辣黄瓜 4.99 | `RENAMED`, `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `coke`，2.50 | 可乐 3.00 | `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `diet_coke`，2.50 | 健怡可乐 3.00 | `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | Seed 无 7 Up | 七喜 3.00 | `NEW_IN_CHINATOWN` |
| `LIVE_SOURCE_REQUIRED` | Seed 无 Ginger Ale | 姜汁汽水 3.00 | `NEW_IN_CHINATOWN` |
| `LIVE_SOURCE_REQUIRED` | `ice_tea`，冰红茶，2.80 | 冰茶 3.00 | `RENAMED`, `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `chinese_herbal_tea`，王老吉，3.50 | 中式凉茶 3.00 | `RENAMED`, `PRICE_CHANGED`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `pickled_vegetable_beef_noodle` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `beef_chow_mein` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `chicken_chow_mein` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `tomato_chow_mein` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `vegetable_chow_mein` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `cold_noodle_shredded_chicken` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `fried_spring_rolls` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `tempura_shrimp` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `fried_steamed_buns` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `fried_wontons` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `shochu` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `sake` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |
| `LIVE_SOURCE_REQUIRED` | `tsingtao_beer` | PDF 无 | `REMOVE_FROM_CHINATOWN`, `SEED_ONLY`, `LIVE_SOURCE_REQUIRED` |

没有任何行可仅凭仓库写成 `UNCHANGED`。只有 Live 导出完成后，才能确认 source identity 是否 unchanged；target 价格仍以 PDF 为准。

## 5. Owner Decisions

以下决定为本轮权威输入，不再列为待确认问题：

| 主题 | Owner 决定 |
| --- | --- |
| Pricing | PDF 是价格 source of truth；忽略旧 Backlog 价格 |
| Combo | Combo 1-4 仅适用于 PDF 指定主菜 |
| Combo 3 | 包含一份茶叶卤蛋 |
| Tea Egg | 同时存在 standalone menu item 和 add-on option |
| Sichuan Pepper Chicken | `SIDE_DISHES` / `COLD`，与 St-Denis 保持一致 |
| Noodle Types | 七种面型适用于每一道 target 面 |
| Hidden Add-ons | 保留全部现有 St-Denis Live add/remove options，即使 PDF 未印 |
| Braised Beef Tendon | 不实现 schedule；周末供应由运营处理 |
| French | 不做法文本地化，仅中文 + 英文 |
| Categories | Soup Noodles, Dry Noodles, Side Dishes, Drinks |
| Category Order | Soup Noodles -> Dry Noodles -> Side Dishes -> Drinks |
| Soup item order | Traditional Beef -> Braised Beef Tendon -> Vegetable |
| Dry item order | Dan Dan -> Zha Jiang |
| Side item order | Braised Beef Shank -> Spicy Cucumber -> Edamame -> Seaweed Potato -> Sichuan Pepper Chicken -> Tea Egg |
| Drink item order | Coke -> Diet Coke -> 7 Up -> Ginger Ale -> Ice Tea -> Chinese Herb Tea（按 PDF） |
| Source Store | St-Denis，Store ID `1`；唯一 Live clone source |
| Seed policy | Repository Seed 仅作历史参考，不得作为 clone source |

## 6. Store 1 Live 数据库仍缺少的证据

产品决策已经完整，不再等待新的 Owner 产品答案。以下只保留 AL-003 执行时必须从 Store 1 读取的数据库字段证据：

1. Store 1 当前 `menu_revision`。
2. Live category 的 `id`、`code`、中英文名、`sort_order`、`is_active`。
3. Live station 的 `id`、`code`、名称、`sort_order`、`is_active`，尤其确认 `BAR`、`COLD`、`NOODLE`。
4. 所有 Live menu item 的 `id`、`sku`、中英文名、category/station ID、`base_price`、`is_active`、`is_sold_out`、`sort_order`。
5. 本报告每个 reuse 候选 SKU 是否实际存在，以及是否有重复/legacy inactive 行。
6. Live 是否已有椒麻鸡及其准确 SKU；若存在，确认其 item ID 与 `SIDE_DISHES`/`COLD` 归属。
7. Live 是否已有 standalone 茶叶卤蛋及准确 SKU；无则 target 创建新 SKU。
8. `chinese_herbal_tea` 是否为 PDF 中式凉茶所对应产品。
9. 每个 target 面当前 Live option 的 `id`、`option_code`、`option_type`、`option_group`、`parent_option_id`、中英文名、`price_delta`、`is_active`、`sort_order`。
10. 当前所有 Live add/remove option，以便按 Owner 决策完整保留，而不是从 seed 推断。
11. Live 七种面型 option 的准确 code/ID 与当前 active 状态。
12. Live Combo option 的准确 code/group/parent 关系，以便 clone 后按 Owner 决策限制到 Combo 1-4 指定主菜。

允许的后续只读导出范围严格限定为 `store_id = 1`，且只包含：

- Store `menu_revision`；
- Category；
- Station；
- Menu Item；
- Menu Item Option。

不得读取 Order、Customer、Staff、Payment、Inventory、Printer、Device、Credential、Token 或 Analytics。未访问数据库；本报告不授权 SSH、Production 查询或 runtime mutation。数据库证据是 AL-003 clone 执行输入，不再是产品设计阻塞项。

## 7. AL-003 克隆实现最终映射输入

### 7.1 Category 与 station 输入

| Target category | Order | Target station | Clone action |
| --- | ---: | --- | --- |
| `SOUP_NOODLE` | 1 | `NOODLE` | Reuse category/station semantics；新 target IDs |
| `DRY_NOODLE` | 2 | `NOODLE` | Reuse category/station semantics；新 target IDs |
| `SIDE_DISHES` | 3 | `COLD` | 使用 Owner 指定 target code；新 target IDs |
| `DRINK` | 4 | Live `BAR` code 待证据确认 | Reuse semantics；新 target IDs |

### 7.2 Item 映射

Source SKU 在 Store 1 数据库证据完成前均是候选稳定标识；AL-003 必须在实现流程中按第 6 节验证，再执行 clone。产品映射无需再次分析。

| Target 顺序 | Chinatown 菜品 | Source SKU 候选 | Target price | Clone Action |
| ---: | --- | --- | --- | --- |
| 1 | 兰州牛肉面 | `traditional_beef_noodle` | S 14.99 / M 16.99 / L 18.99 | Reuse + Rename + Price/Size Override |
| 2 | 红烧牛筋面 | `braised_beef_tendon_noodle` | 17.99 | Reuse + Price Override；不建 schedule |
| 3 | 蔬菜面 | `vegetable_noodle` | S 14.99 / M 16.99 / L 18.99 | Reuse + Price/Size Override |
| 1 | 担担面 | `dan_dan_noodle` | S 15.99 / M 17.99 | Reuse + Size/Price Override |
| 2 | 老兰州炸酱面 | `zha_jiang_noodle` | 17.99 | Reuse + Rename + Price Override |
| 1 | 兰州辣拌牛展 | `braised_beef_shank_salad` | 9.99 | Reuse + Rename + Price Override |
| 2 | 香辣黄瓜 | `cucumber_salad` | 4.99 | Reuse + Rename + Price Override |
| 3 | 雪菜毛豆 | `edamame` | 4.99 | Reuse + Rename + Price Override |
| 4 | 海菜土豆丝 | `shredded_potato` | 4.99 | Reuse + Rename + Price Override |
| 5 | 椒麻鸡 | Store 1 Live SKU required；seed 无 | 9.99 | Reuse if Live exists, otherwise Create；SIDE_DISHES/COLD |
| 6 | 茶叶卤蛋 | Store 1 Live SKU required；seed 无 standalone | 1.99 | Reuse if Live exists, otherwise Create；另保留 add-on |
| 1 | 可乐 | `coke` | 3.00 | Reuse + Price Override |
| 2 | 健怡可乐 | `diet_coke` | 3.00 | Reuse + Price Override |
| 3 | 七喜 | `NEW` | 3.00 | Create |
| 4 | 姜汁汽水 | `NEW` | 3.00 | Create |
| 5 | 冰茶 | `ice_tea` | 3.00 | Reuse + Rename + Price Override |
| 6 | 中式凉茶 | `chinese_herbal_tea` 候选，需 Store 1 产品证据 | 3.00 | Reuse + Rename + Price Override after evidence |

### 7.3 Option 与 Combo 输入

| 范围 | Target 规则 |
| --- | --- |
| Noodle type | 五种面全部创建/克隆毛细、细、三细、二细、韭叶、宽、大宽 |
| Existing add/remove | 从 Live source 按 item 克隆全部 active add/remove；不从 seed 重建 |
| Tea egg | standalone item + add-on option |
| Combo 1 | 仅 `traditional_beef_noodle`；小菜任选 + 茶叶卤蛋；按 S/M/L PDF 总价 |
| Combo 2 | 仅 `zha_jiang_noodle`；小菜任选 + 茶叶卤蛋；总价 22.99 |
| Combo 3 | 仅 `vegetable_noodle`；小菜任选 + 茶叶卤蛋；按 S/M/L PDF 总价 |
| Combo 4 | 仅 `dan_dan_noodle`；小菜任选 + 茶叶卤蛋；按 S/M PDF 总价 |
| Combo side | 香辣黄瓜、雪菜毛豆、海菜土豆丝 |
| French | 不创建法文 localization；保留中文名称与英文 `name_en` |
| Tendon availability | 不新增 schedule；运营控制 active/sold-out |

### 7.4 AL-003 固定实现锚点表

此表是后续 AL-003 菜单克隆实现的固定输入，不需要再次分析其业务动作。Source SKU 仍必须由第 6 节的 Store 1 Live 数据库证据确认。

| Chinatown 菜品 | Source SKU | Clone Action |
| --- | --- | --- |
| 牛肉面 | `traditional_beef_noodle` | Reuse |
| 牛筋面 | `braised_beef_tendon_noodle` | Reuse + Price Override |
| 担担面 | `dan_dan_noodle` | Reuse + Size Change |
| 椒麻鸡 | `NEW` | Create |
| 7 Up | `NEW` | Create |
| Ginger Ale | `NEW` | Create |

## 最终停止状态

`AL-003A_FINAL_COMPARISON_READY_FOR_AL003_IMPLEMENTATION`

本轮未实现菜单克隆，未修改业务代码，未 SSH，未访问 Production/Staging，也未查询 runtime 数据库。
