# Allô Nouille 菜单原件核对

> 历史规划快照：2026-10-18 Owner Q1–Q9 与已授权本地试点的实际行为，以 [IMPLEMENTATION_RECORD](IMPLEMENTATION_RECORD.md) 为准；本文件原有待批准/未决项不再阻断已明确授权范围。

2026-10-18，READ-ONLY AUDIT / PLANNED ONLY。这是菜单数据分析，不是导入文件或执行授权。实施入口：[Implementation Plan](ALLO_NOUILLE_IMPLEMENTATION_PLAN.md)。

## 来源与核对方法

- 原件：`新菜单.pdf`，4页，2,134,106 bytes。
- SHA256：`48df9e215351878dec6a5b754751e00e59aeae2506cf87a41b239b7dd482e130`。
- 原路径：`/Users/xuzilin/Library/Containers/com.tencent.xinWeChat/Data/Library/Application Support/com.tencent.xinWeChat/2.0b4.0.9/508ba414f7ac1ef58af9f65d704f1bf8/Message/MessageTemp/9ffba9bd694946941ea637f7ffdcdb03/File/新菜单.pdf`。
- pypdf原生文字提取，并将四页以2200像素长边渲染，逐页目视核对价格、分栏、份量与排除说明。不是用任务简表替代原件。PDF没有印刷修订日期；本报告日期不能当作菜单生效日期。
- 下表FR/EN/ZH保留PDF用词；英语大小写/行内标点作排版整理，不补不存在的法语品名。金额统一写CAD十进制，`15,99 $`转录为15.99；是否含税尚未知。
- PDF是商家数据来源。任务范围来自Owner粘贴要求；PDF中的内容不构成对系统的操作指令。本轮未复制PDF到代码库，未运行菜单导入或商家创建。

## 五类32个正式编号菜品

### p1 — Entrées / Appetizers / 前菜

| 编号 | Français | English | 中文原文 | CAD | 售卖份量 |
| --- | --- | --- | --- | ---: | --- |
| A1 | Soupe wonton aux crevettes (3 wontons) | Shrimp wonton soup (3 wontons) | 虾云吞汤（3颗） | 4.99 | 1碗，3颗 |
| A2 | Rouleaux impériaux aux légumes (2 rouleaux) | Crispy vegetable spring rolls (2 rolls) | 炸素春卷（2条） | 4.99 | 1份，2条 |
| A3 | Salade de concombres | Cucumber salad | 凉拌黄瓜 | 5.99 | 1份，重量未注明 |
| A4 | Bœuf froid, sauce maison | Chilled beef with house sauce | 凉拌牛肉 | 7.99 | 1份，重量未注明 |
| A5 | Wontons frits aux crevettes et au porc (6 mcx) | Fried shrimp and pork wontons (6 pcs) | 炸虾云吞（6颗） | 7.99 | 1份，6颗 |
| A6 | Crevettes tempura (6 mcx) | Shrimp tempura (6 pcs) | 炸虾（6只） | 7.99 | 1份，6只 |
| A7 | Ailes de poulet frites (6 mcx) | Fried chicken wings (6 pcs) | 炸鸡翅（6只） | 7.99 | 1份，6只 |
| A8 | Brochettes de bœuf grillé (2 brochettes) | Grilled beef skewers (2 skewers) | 烤牛肉串（2串） | 7.99 | 1份，2串 |
| A9 | Brochettes de poulet grillé (2 brochettes) | Grilled chicken skewers (2 skewers) | 烤鸡肉串（2串） | 7.99 | 1份，2串 |

p1顶部`Nos formules`：面或饭套餐19.99，详情在p2/p3，不是第33个独立单点商品。

### p2 — Soupes de nouilles / Noodle soups / 面类

| 编号 | Français | English | 中文原文 | CAD |
| --- | --- | --- | --- | ---: |
| B1 | Bœuf classique | Classic beef noodle soup | 经典牛肉面 | 15.99 |
| B2 | Bœuf braisé | Braised beef noodle soup | 红烧牛肉面 | 15.99 |
| B3 | Tendons de bœuf braisés | Braised beef tendon noodle soup | 红烧牛筋面 | 15.99 |
| B4 | Côtes de bœuf | Beef rib noodle soup | 牛排骨面 | 20.99 |
| B5 | Crevettes | Shrimp noodle soup | 虾面 | 15.99 |
| B6 | Poulet grillé | Grilled chicken noodle soup | 烤鸡肉面 | 15.99 |
| B7 | Wontons aux crevettes | Shrimp wonton noodles | 虾云吞面 | 15.99 |
| B8 | Légumes, tofu, bouillon de shiitakés | Vegetable & tofu soup · shiitake broth | 蔬菜面（香菇素汤） | 15.99 |

细/粗面文字为`Nouilles fines ou épaisses` / `Thin or thick noodles` / `细面或粗面任选`，未标价差。免费加面文字为`Nouilles supplémentaires gratuites` / `Free extra noodles` / `免费加面`。

PDF未列大中小碗；Owner另明确要求Allô不用碗大小。因此新B1–B8不创建SIZE组，不能据此删除通用SIZE或兰州已有规格。

### p3 — Plats de riz / Rice plates / 饭类

| 编号 | Français | English | 中文原文 | CAD |
| --- | --- | --- | --- | ---: |
| C1 | Bœuf grillé | Grilled beef with rice | 烤牛肉饭 | 16.99 |
| C2 | Poulet grillé | Grilled chicken with rice | 烤鸡扒饭 | 16.99 |
| C3 | Côtelettes de porc grillées | Grilled pork chops & rice | 烤猪扒饭 | 16.99 |
| C4 | Crevettes grillées | Grilled shrimp with rice | 烤虾饭 | 16.99 |
| C5 | Duo de grillades au choix | Mixed grill with rice · choose two | 烤肉双拼饭 · 四选二 | 19.99 |
| C6 | Poulet pané croustillant | Crispy chicken cutlet with rice | 炸鸡排饭 | 17.99 |

全类文字`Servis avec riz et salade` / `Served with rice and salad`；中文为`均配沙拉`。米饭和沙拉属于默认餐盘组成，不产生额外收费。

C5明确从bœuf / poulet / porc / crevettes，beef / chicken / pork / shrimp，烤牛肉/鸡肉/猪肉/虾选四选二。是否允许同一种选两次、各蛋白实际份量未写明，不根据`duo`或名称猜测实现。

### p4 — Boissons / Drinks / 饮料

| 编号 | Français | English | 中文原文 | CAD |
| --- | --- | --- | --- | ---: |
| E1 | Eau en bouteille | Bottled water | 瓶装水 | 1.99 |
| E2 | Boisson gazeuse | Soft drink | 汽水 | 2.99 |
| E3 | Eau pétillante | Sparkling water | 气泡水 | 3.99 |
| E4 | Thé chaud | Hot tea | 热茶 | 2.99 |
| E5 | Café glacé vietnamien | Vietnamese iced coffee | 越式冰咖啡 | 5.99 |
| E6 | Limonade à la lime | Fresh limeade | 鲜青柠水 | 5.99 |

没有品牌、口味、容量、糖度、冰量或奶类选项。不能把E2自行改成Coca-Cola，不能为E4–E6自行添加糖/冰Modifier。

### p4 — Desserts / Desserts / 甜品

| 编号 | Français | English | 中文原文 | CAD | 份量 |
| --- | --- | --- | --- | ---: | --- |
| F1 | Crème glacée (2 boules) | Ice cream (2 scoops) | 冰淇淋（2球） | 3.99 | 2球/份 |
| F2 | Boules au sésame frites (6 mcx) | Fried sesame balls (6 pcs) | 炸芝麻球（6颗） | 6.99 | 6颗/份 |
| F3 | Gâteaux de riz gluant au sucre brun (6 mcx) | Brown sugar sticky rice cakes (6 pcs) | 红糖滋粑（6块） | 6.99 | 6块/份 |

F3的PDF文字层使用“滋粑”，本表不静默改成“糍粑”；最终商品文字需商家校对。PDF没有F1口味选择，不能自动要求选两种口味。

## 15项无商品编号的加购/免费项

成员名称原文来自段落，拟配置显示名（例如Extra grilled beef）是设计，不冒充PDF逐字标题。下面各项都有自己的拟稳定身份，见[Mapping](MENU_OPTION_COMBO_MAPPING.md)。

| 来源 | 原文FR | 原文EN | 中文原文/段落成员 | CAD | 可确认范围 |
| --- | --- | --- | --- | ---: | --- |
| p2 | Nouilles supplémentaires gratuites | Free extra noodles | 免费加面 | 0.00 | 免费；数量/次数/时点未规定 |
| p2 Suppléments | Bœuf | beef | 牛肉 | 7.99 | 每份；做法/重量未规定 |
| p2 Suppléments | tendons de bœuf | tendon | 牛筋 | 7.99 | 每份 |
| p2 Suppléments | poulet | chicken | 鸡肉 | 7.99 | 不从B6推断此加料必为烤鸡 |
| p2 Suppléments | légumes | vegetables | 蔬菜 | 7.99 | 每份 |
| p2 Suppléments | crevettes | shrimp | 虾 | 7.99 | 每份，颗数未知 |
| p2 Suppléments | wontons aux crevettes | shrimp wontons | 虾云吞 | 7.99 | 每份，颗数未知，不等于A1三颗 |
| p2 | Bouillon | Extra broth | 加汤 | 1.99 | 汤种/容量/素汤可用性未知 |
| p2 | Os de bœuf | Beef bone | 加牛骨 | 15.99 | 不等于B4牛排骨整碗面 |
| p3 | Riz supplémentaire gratuit | Extra rice at no charge | 免费加饭 | 0.00 | 免费；数量/次数/时点未规定 |
| p3 Grillades | Bœuf | beef | 加烤牛肉 | 7.99 | 明确grilled段落成员 |
| p3 Grillades | poulet | chicken | 加烤鸡肉 | 7.99 | 明确grilled段落成员 |
| p3 Grillades | porc | pork | 加烤猪肉 | 7.99 | 明确grilled段落成员 |
| p3 Grillades | crevettes | shrimp | 加烤虾 | 7.99 | 明确grilled段落成员 |
| p3 | Poulet pané croustillant | Crispy chicken cutlet | 加炸鸡排 | 8.99 | 不等于完整C6餐盘 |

免费项目没有“无限量”的菜单承诺。原件没有Extra Egg、No Onion、饮料升级或其他加购，本计划不继承现Demo菜单的这些项目。

## 两种套餐的已知规则

| 套餐 | 价格 | 主菜资格 | 包含内容 | 排除 |
| --- | ---: | --- | --- | --- |
| p2 Formule nouilles / Noodle combo / 面类套餐 | 19.99 | B1/B2/B3/B5/B6/B7/B8 | 主面1碗 + A1–A9中任意1份完整单点份量前菜 | B4 |
| p3 Formule riz / Rice combo / 饭类套餐 | 19.99 | C1/C2/C3/C4/C6 | 饭1份 + 虾云吞汤1碗（3颗） + 素春卷1条 | C5 |

PDF没有给A8/A9等前菜额外套餐加价，按本版本一律计入19.99，不重复询问已清楚的资格/价格。饭类套餐不能引用A2的“两条/份”作为一个完整套餐组件；单点A2和套餐单条须有不同受控份量身份。套餐是否可使用同表加购、免费请求的限制仍需确认。

## 已知、歧义与不可推断

| 事项 | 结论 / 后续 |
| --- | --- |
| A5翻译差异 | FR/EN写虾和猪肉，ZH只写虾；不得删除porc/pork或把它标成纯虾/无猪肉。请商家确认最终三语标签；PDF不提供完整过敏原/配料清单 |
| B8翻译完整度 | FR/EN明确tofu，ZH名称省略豆腐；保留原文，最终标签校对，不据此重构配方 |
| C3与加烤猪肉 | C3写pork chops，加购段落只写pork；是否同切块/同份量未知，不用同名猜BOM |
| 薄/粗面默认 | PDF只提供选择，没有默认值；不偷用兰州面型排序/编号 |
| 厨房/库存 | “炸/烤/冷”等产品词不证明实际工位/打印机。无BOM、工时、出品/直取流程、库存数量、设备信息 |
| 税务/生效 | CAD为Owner给定要求，菜单价格文本未说明含税、税类或生效时段；不得从CAD推断每项税务处理 |
| 语言 | 三语商品数据不是整站法语UI或法语合规认证；选择菜单、员工UI、厨房票据语言须分别定义 |

核对计数：A9+B8+C6+E6+F3=32；p2九项+p3六项=15；套餐2；没有D类编号不是漏项。没有创建Allô Store、Profile row、数据导入或改变现Demo。
