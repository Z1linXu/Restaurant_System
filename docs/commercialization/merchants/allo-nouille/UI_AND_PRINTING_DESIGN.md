# Allô Nouille 点餐、厨房与打印设计

> 历史规划快照：2026-10-18 Owner Q1–Q9 与已授权本地试点的实际行为，以 [IMPLEMENTATION_RECORD](IMPLEMENTATION_RECORD.md) 为准；本文件原有待批准/未决项不再阻断已明确授权范围。

DESIGN ONLY / NOT IMPLEMENTED。依据[菜单原件](MENU_PDF_ANALYSIS.md)、[能力证据E01–E18](CURRENT_CAPABILITY_GAP.md)及[菜单映射](MENU_OPTION_COMBO_MAPPING.md)。下列交互、小票与逻辑履约角色是拟议设计，不是本轮浏览器、订单或真实打印验收结果。

## POS交互建议与替代方案

建议复用现ItemCustomizationModal，在符合资格的主菜中显示默认关闭的 `Make it Combo`。理由是先选想吃的主菜，再完成必要选择，适合iPad，不增加独立配置步骤。此建议等待Owner审核，未作为既定产品决定写入代码。

| 场景 | 建议交互 | 价格和服务端约束 |
| --- | --- | --- |
| B1–B3/B5–B8 | 名称、单点价、细/粗面、加面/加料、套餐开关、Notes | 打开套餐后显示“套餐价 CAD 19.99”，不能显示统一“+4”作为所有饭面的规则 |
| 面类套餐 | 选择A1–A9之一，显示该前菜完整份量；未完成必选不能加入购物车 | offer独立绑定7种主面；前菜包含在19.99内，不叠加其单点价 |
| B4 | 普通面自定义；不显示套餐开关 | 20.99单点，直接伪造套餐请求也拒绝 |
| C1–C4/C6 | 单点或Rice Combo，加饭、饭类加购、Notes | 套餐19.99；C1–C4与C6不能共用一个delta算价 |
| 饭类套餐 | 清晰列“虾云吞汤1碗（3颗）+素春卷1条”，固定包含项不要求重复点击 | 父行19.99，included子项零增量；不可替成A2两条 |
| C5 | 四种蛋白的选择区，顶部显示“选2份”；达到约束后才能加入 | min=max=2；重复政策待确认；不显示Rice Combo |
| E/F | 无必要选项时尽可能一次加入；有经确认的必选项才打开Modal | Quick Add与是否制作/打印分别配置，不能为快捷添加而跳过厨房 |

替代方案是单独的“套餐”入口：先选offer，再选主品/前菜。它必须引用同一offer、资格、规则和报价，不能创建另一组重复Combo商品、单价或SKU分支。代价是增加导航和主菜选择步骤。先由Owner比较两种流程；本轮不强行采用任一种。

## 面类Modal与无Size全链

拟布局依次为：名称/价格 → Thin / Thick → Extra Noodles → 收费Add-on → 合资格套餐 → Notes → 大号加入按钮。细/粗各0增量；首次默认值尚未确认，不能偷偷采用兰州默认，也不能因排序第一就推定商家选择。

Allô新面品不创建SIZE options，隐藏整个空组而不是显示禁用的大中小。现新商品无SIZE路径已存在（E02/E09），不需要删通用SIZE功能；“清空已配Size”能力与通用必选约束由AN-PR1补齐。

| 路径 | 后续必须验证的契约 |
| --- | --- |
| Catalog / Backend validation | 无SIZE就是无该组；只对实际分配的active组校验，不按面类名称强制碗型 |
| Pricing | 使用base price或固定offer价；未选SIZE不得触发默认Regular价格 |
| Cart edit | 保存、重新编辑、复制数量后仍无Size，不从legacy默认值补回 |
| Order / component snapshots | Size可缺省；保存面型ID/标签/规则版本，不能只保存显示字符串 |
| Offline draft / idempotent submit | 同scope、原冻结payload、稳定key；菜单变化按可信旧价规则处理，不改已提交内容 |
| Receipt / task / reprint | 不输出空括号、Regular或兰州编号；旧兰州订单仍按原snapshot打印 |

加面/加饭继续复用Canonical Add-on的零价身份，不新增“免费商品”价目库。0价不等于无限份；在Owner确认上限前，不把现无上限stepper作为最终设计。建议的0/1开关仅为待确认选项，厨房必须能看见实际选择。付费加购单价及B/C资格按[映射](MENU_OPTION_COMBO_MAPPING.md)维护。

## 饭类、C5与默认配菜

每盘饭的米饭及沙拉是单品组成说明，不成为额外收费Add-on。“Extra Rice”是另一次明确选择，不能从默认米饭自动生成。默认组成是票上固定说明还是只打异常，由Owner决定；无论显示模式如何，履约/BOM只能计一次。

C5四种蛋白是modifier/component选项，不链接四份完整C1–C4饭盘。相同蛋白可否出现两次必须有显式规则；计数按quantity或distinct identity使用统一服务端契约，前端计数方式不能自行决定。保存两个选择的身份、数量、标签、份量与规则版本，厨房逐项输出，不能解析“牛+鸡”字符串恢复结构。

## Quick Add、饮料和甜品

当前Quick Add只覆盖特定DRINK/item type或FRIED条件（E17），普通甜品无选项也不保证走快捷路径。当前direct-serve按固定DRINK等category跳过厨房（E10）；把E4热茶、E5咖啡、E6青柠水随意设为DRINK，可能使真实制作任务消失。

后续在既有Item配置扩展最小的typed `quick_add` 与履约模式/实际station映射，概念由AN-PR5评审，并以AN-PR1的required规则为前提：有必选项就不能跳过。无须给Owner增加一套“内部验证页面”。E1/E2/E3也不能从名称推定无制作；F1口味、E2品牌、冰量/糖量、库存配方均UNKNOWN，未确认前不生成假选项或BOM。

## 菜单语言与界面语言

| 方案 | 优点 | 代价 / 建议 |
| --- | --- | --- |
| 在当前有名称的必要实体加nullable `name_fr`，保留zh/en | 三语菜单投影、编辑和冻结路径直观；现DTO可additive兼容 | 建议首选。覆盖本菜单涉及的category/item/option/Add-on/group/component及快照；不是只给MenuItem加一列 |
| 通用版本化Translation表 | 适合未来多语言种类动态扩展 | 增加实体引用、版本、查询、权限和导入关联复杂度；当前仅三语需求不足以要求新翻译平台 |

两种方案均为待评审设计，不同时建设两套标签SoT。语言字段/引用进入menu revision、content hash、Order与PrintJob冻结。历史打印不实时查翻译，更新法语不能改变旧票。

Store menu locale、员工UI语言、前台票语言、厨房票语言是不同偏好：商家locale不能切换所有员工页面，员工切中文也不能篡改已成交快照。最小范围是菜单及小票三语标签，不要求完整法语管理后台作为菜单导入前提。

拟回退：法语请求缺译时 `fr → en → zh → stable code`；英语为 `en → zh → stable code`，中文为 `zh → en → stable code`。可配置厨房双语展示使用同一标签来源。回退与实际输出标签在新订单/任务/打印时冻结，不跨Store/Profile借兰州名称。缺译在管理端提示，不输出工程revision/hash给前台。A5猪肉、B8豆腐、F3“滋粑”先保留原文并审校，见Owner问题Q7。

## 真实工位与初始MOCK方案

PDF不能证明工位数量、设备、KDS、库存或连接方式。“烤/炸/冷”不是实际station code或Printer assignment，不能映射成兰州WOK/COLD/DEEPFRIED后宣称已通用。

未来独立Demo可以先以“面类制作、饭类制作、前菜制作、饮料/甜品处理”作为**逻辑MOCK角色占位**，在Owner确认实际制作流程后合并/拆分。占位不是必须新建四个工位，也不是当前已存在接口或真实Allô配置。每个需要制作的单品/组件引用同Store实际station；无需制作的项目明确direct serve。无真实LAN IP、TCP/PAD_DIRECT绑定或外部商户接口。

| 现职责 | 复用内容 | 缺口与拟调整 |
| --- | --- | --- |
| Kitchen Tasks / routing | 当前事务、状态推进、单品station关联 | 通用遍历套餐组件；父qty×组件qty；每个制作身份只计一次；route缺失需可解释拒绝/配置提示，不静默漏单 |
| GRAB | 现厨房总览/出餐协调小票与冻结job | 不是Grab外卖平台。当前station白名单需配置化，按父子关系显示完整组成和数量 |
| HOT_KITCHEN | 现厨房制作打印基础设施 | 不能把Allô固定归WOK/DEEPFRIED；按确认的角色/assignment分发相应组件 |
| FRONTDESK_RECEIPT | 成交价、税与总额冻结、历史重印 | 父行承担套餐价、included组成清楚；加购另列、含餐组件不双计单点价 |
| Printing Assignment | WHERE：哪一角色/工位到哪个MOCK目标 | 不维护菜单内容、套餐价格或另一份语言字典 |
| Printing Display Rules | HOW：名称、顺序、简写、双语与固定配菜显示 | 商家私有presets；不能全局硬编码Allô品牌或覆盖兰州缩写 |
| Print Job snapshots / claim / lease | 现冻结载荷、重试/去重与所有权边界 | 新结构版本化；未知实体打印结果不自动盲重印；打印失败不回滚订单 |

套餐子项保留`parent line/component identity`；不同工位各收到自己负责的制作量，协调票可以看到整套结构。协调显示不能再生成第二份KitchenTask或重复扣BOM。米饭/沙拉若已在主菜recipe消费，不因票上显示又计两份；C5蛋白同样按真实recipe确认，不能臆造库存。

## 未来MOCK票据示意（设计，非实际渲染）

以下均为数量1的例子；税务处理尚未确认，所以只展示菜单套餐价，**不是有效结算票，不给出税后总额**。实际工位、缩写、语言和纸张宽度仍需确认。

```text
ALLÔ NOUILLE — MOCK / DESIGN
Kitchen / Coordination
1 × B1 Classic beef noodle soup / 经典牛肉面
    Thin noodles / 细面
    Combo: 1 × A2 Vegetable spring rolls / 素春卷（2条）
    Included component; do not make a second main

Frontdesk / Design
1 × Noodle Combo                           CAD 19.99
    B1 + A2 (2 rolls included)
Tax / final total: policy pending confirmation
```

```text
ALLÔ NOUILLE — MOCK / DESIGN
Kitchen / Coordination
1 × C1 Bœuf grillé / 烤牛肉饭
    Rice + salad included / 含米饭及沙拉
    1 × Shrimp wonton soup bowl (3 wontons) / 虾云吞汤1碗（3颗）
    1 × Vegetable spring roll / 素春卷1条

Frontdesk / Design
1 × Rice Combo                             CAD 19.99
    C1 + soup (3 wontons) + spring roll (1 piece)
Tax / final total: policy pending confirmation
```

Rice数量3时应为3盘、3碗共9颗云吞、3条春卷；不能显示6条。Noodle选A2数量3时则是3份、共6条春卷。实现验收须分别比较Order/component snapshot、KitchenTask、BOM需求及各角色MOCK payload，不能仅凭屏幕文案判定PASS。

## 兼容与实施边界

复用现Modal、Catalog、Cart、Order事务、DB5 outbox、task/job/lease/assignment；新增规则与语言由通用schema驱动，不在React用AN_B1之类SKU分支。原兰州Size、Combo、Add-on、缩写、GRAB/HOT/frontdesk黄金样例和Neutral零默认字典是不可回归项。

本设计依赖AN-PR1/2/3/4/5，按[实施计划](ALLO_NOUILLE_IMPLEMENTATION_PLAN.md)受P4/P5 Gate约束。本轮无UI修改、无订单提交、无MOCK执行、无实体打印，也不把示意票当真实测试证据。
