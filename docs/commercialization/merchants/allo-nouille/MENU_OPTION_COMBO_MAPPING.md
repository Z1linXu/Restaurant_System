# 商品、选项、套餐与份量映射

> 历史规划快照：2026-10-18 Owner Q1–Q9 与已授权本地试点的实际行为，以 [IMPLEMENTATION_RECORD](IMPLEMENTATION_RECORD.md) 为准；本文件原有待批准/未决项不再阻断已明确授权范围。

PLANNED ONLY。源价格/三语文案唯一转录在[PDF Analysis](MENU_PDF_ANALYSIS.md)，当前接口边界在[Capability Gap](CURRENT_CAPABILITY_GAP.md)。下列code为拟议配置身份，不是已存在的数据库ID或新API。

## 32个可售商品

| PDF集合 | 拟Store SKU / 逻辑引用 | 数量 | 单点和规格 | 套餐资格 | 所属配置 |
| --- | --- | ---: | --- | --- | --- |
| A1–A9 | AN_A1…AN_A9；包内引用A1…A9 | 9 | 保持p1完整份量与4.99/5.99/7.99各价 | Noodle appetizer group候选全九项 | Appetizers |
| B1–B8 | AN_B1…AN_B8 | 8 | 无SIZE；B4=20.99，其余15.99；细/粗面0差价 | B1/2/3/5/6/7/8参与NOODLE_1999；B4仅单点 | Noodle soups |
| C1–C6 | AN_C1…AN_C6 | 6 | 固定价；C5双拼选择，不是Size；米饭沙拉含在原价 | C1/2/3/4/6参与RICE_1999；C5不参与 | Rice plates |
| E1–E6 | AN_E1…AN_E6 | 6 | 1.99/2.99/3.99/2.99/5.99/5.99，无未证实口味/容量 | 本PDF没有饮料套餐组件 | Drinks |
| F1–F3 | AN_F1…AN_F3 | 3 | 3.99/6.99/6.99；2球/6颗/6块为每售卖份量 | 本PDF没有甜品套餐 | Desserts |

SKU只是商家配置中的稳定标识，不能在Frontend、定价或全局renderer写 `if sku.startsWith(AN_)`。导入解析为同Store真实ID，授权始终验证实际Store/Org。分类中文/英文名不触发Core业务。

## 普通Modifier与免费请求

| 拟组/选择身份 | 适用项 | 价 | 建议通用规则 | 未决内容 |
| --- | --- | ---: | --- | --- |
| AN_NOODLE_TYPE / an_noodle_thin | B1–B8 | 0 | 单选组候选；稳定语义THIN，显示Thin noodles / 细面，FR取p2原文 | 是否默认细面、是否要求员工显式确认 |
| AN_NOODLE_TYPE / an_noodle_thick | B1–B8 | 0 | 同组候选THICK，不新增兰州7种面 | 同上 |
| n_extra_noodles | B1–B8，套餐适用待确认 | 0 | 复用Store Add-on+eligibility；未勾选时不产额外请求快照 | boolean/数量、上限、是否限每碗/每次就餐、续加时点 |
| r_extra_rice | C1–C6，套餐适用待确认 | 0 | 与餐盘自带米饭分开，是额外请求；打印时保留数量 | 同上 |
| AN_C5_PROTEIN / beef/chicken/pork/shrimp | 只C5 | 0 | generic min_selected_units=2、max_selected_units=2；无名字包含判断 | allow_duplicate_choice未知；每种蛋白份量未知 |

面型在当前option API可用 `option_type=noodle_type` / `option_group=NOODLE_TYPE` 建立两条，且不创建SIZE。选用局部code `an_noodle_thin` / `an_noodle_thick`，避免撞到当前兰州 `noodle_thin` 等编号后缀；新通用路径最终应由语义/受审legacy适配控制，不能把“换code避开旧显示”当完整Core修复。

当前零价Add-on可保存，但UI只有“加香菜/加葱”被当成toggle，其余是stepper；不能仅把Extra Noodles设0价就声称实现了免费额度。C5不能拆成两组不受约束的单选或塞Notes来绕过恰好二选校验。

## 15项Canonical Add-on建议身份

下表EN为拟显示名，FR/ZH原文值参照PDF分析，补全词组须商家审校。配置资格先按B/C组展开为现item eligibility；是否对个别商品/套餐例外由Owner确认，不用字符串判断。

| Canonical code（Org scoped） | 拟EN显示名 | CAD | 初始资格设计 | 不得错误复用为 |
| --- | --- | ---: | --- | --- |
| n_extra_noodles | Extra noodles | 0.00 | B1–B8，数量政策未决 | 兰州收费加面/默认面份量 |
| n_extra_beef | Extra beef | 7.99 | B1–B8 | C1整盘饭/烤牛肉加购 |
| n_extra_tendon | Extra beef tendon | 7.99 | B1–B8 | B3整碗面 |
| n_extra_chicken | Extra chicken | 7.99 | B1–B8 | 未经确认的烤鸡加购 |
| n_extra_vegetables | Extra vegetables | 7.99 | B1–B8 | 饭类自带沙拉 |
| n_extra_shrimp | Extra shrimp | 7.99 | B1–B8 | 饭类烤虾/A6 tempura |
| n_extra_shrimp_wontons | Extra shrimp wontons | 7.99 | B1–B8 | A1三颗一碗汤/A5含猪肉六颗炸云吞 |
| n_extra_broth | Extra broth | 1.99 | B1–B8，汤种/容量/素汤边界待确认 | 默认汤底选择/完整A1汤 |
| n_extra_beef_bone | Extra beef bone | 15.99 | B1–B8，份量待确认 | B4牛排骨面 |
| r_extra_rice | Extra rice | 0.00 | C1–C6，数量政策未决 | 原餐盘已经包含的米饭 |
| r_extra_grilled_beef | Extra grilled beef | 7.99 | C1–C6 | n_extra_beef / C1完整餐盘 |
| r_extra_grilled_chicken | Extra grilled chicken | 7.99 | C1–C6 | n_extra_chicken / C2完整餐盘 |
| r_extra_grilled_pork | Extra grilled pork | 7.99 | C1–C6 | C3完整猪扒饭；加购切块未明 |
| r_extra_grilled_shrimp | Extra grilled shrimp | 7.99 | C1–C6 | n_extra_shrimp / C4完整餐盘 |
| r_extra_crispy_chicken | Extra crispy chicken cutlet | 8.99 | C1–C6 | C6完整炸鸡排饭 |

先分开code是因为工艺/份量尚未证实相同，不是再造另一Add-on体系。若Owner确认某两加购确为同一制作单位与语义，可在同Org canonical定义层复用，再保留Store名称/价override；不能只因都叫chicken自动合并。没有菜谱/BOM，不编造库存扣量；零售价不代表零食材消耗。

## 两种Offer与Main资格

| 拟offer | Main集合 | 总基础价 | 组/组件 | 明确不能做 |
| --- | --- | ---: | --- | --- |
| AN_NOODLE_1999 | B1,B2,B3,B5,B6,B7,B8 | 1999 cents CAD | AN_NOODLE_APPETIZER，恰好选1，候选A1–A9，各为完整原单点份量，component qty=1 | 不含B4；不强加鸡蛋/兰州三小菜；不叠加A项单点价 |
| AN_RICE_1999 | C1,C2,C3,C4,C6 | 1999 cents CAD | 固定A1-size汤1碗（3颗）+ SINGLE_SPRING_ROLL规格1份（1条） | 不含C5；不要求再选前菜；不引用A2两条整份 |

当前 `combo_allowed` 可配置B4/C5=false，但不足以表达两个offer的独立组、主品集合与规则版本。当前Store-level groups会整体投影到所有Combo主菜；若把面类自选前菜和饭类固定汤/春卷全放一个组集，会错误地让两类主菜都携带这些组。

19.99是**套餐基础总价**，不是套餐附加费。现统一delta无法表示：面15.99→19.99为4.00；C1–4的16.99→19.99为3.00；C6的17.99→19.99为2.00。不能把单点base_price改成19.99，也不能写三个价后被现Store镜像重置、用负价Add-on或Notes伪装折扣。

## A1 / A2份量及厨房关系

| 使用场景 | 明确生产/售卖规格 | 计数关系 | 订单金额 |
| --- | --- | --- | --- |
| A1单点 | SOUP_WONTON_3PC_BOWL | 售1份=1碗=3颗云吞 | 4.99 |
| Noodle combo选A1 | 同一A1完整规格 | 每套餐1碗=3颗 | 已包含，不另加4.99 |
| Rice combo含汤 | 复用已证实同份量的A1规格，保留included角色 | 每套餐1碗=3颗 | 已包含 |
| A2单点 | SPRING_ROLL_2PC_SERVING | 售1份=2条 | 4.99 |
| Noodle combo选A2 | 同一A2完整规格 | 每套餐1份=2条 | 已包含 |
| Rice combo含春卷 | 拟新增SPRING_ROLL_1PC_COMPONENT | 每套餐1份=1条 | 已包含，不能从4.99/2推一个未公布单卖价 |

推荐最小模型：扩展现Store Menu Item/variant表达**受控组件规格**，新增一个active但COMPONENT_ONLY、不可单点的“一条素春卷”记录，供combo component显式引用；同食材制作来源可共享BOM/recipe引用，份量与可售状态各自明确。当前MenuItem没有sellable/visibility字段，这部分是AN-PR2新能力，不能把is_active=false项塞进现要求active的linked引用。

不推荐当前整数quantity写0.5 A2，也不把A2全局从2条改为1条。另建通用Portion数据库的方案只在将来出现多个真实份量族需求时评估；本例先做最小受控规格，避免新建第二产品库。商家须确认实际单条春卷是否与A2同制作规格，确认前不绑定配方。

### 拟订单/履约快照，不是当前DTO

- parent：主菜真实Store item ID、offer ID/version、套餐基础价1999、订单数量q、独立Add-on selections。
- child/component：真实Store item/portion ID、group/component身份、父子关系、component_units_per_bundle=1、portion unit/count、included=true、实际标签与route revision。
- 例如3份Rice combo：饭3份；汤3碗、共9颗云吞；春卷3条。小票可写“3 × 虾云吞汤（3颗/碗）”，不能写成只有3颗总量；春卷明确“3条”。生产task数量/份量和BOM扣量都按同一乘法，不从商品名称数字推断。
- 一份Noodle combo选A2：厨房前菜2条春卷；一份Rice combo：1条。两者独立验收，不能仅测价一样。
- 组件included-charge=0，父套餐收1999；将来报表若需组件收入分摊，须明确独立的守恒分摊策略，不能重复收费，也不能默认按A项单点价叠加。
- C5蛋白选择不是C1–C4完整餐盘，避免生成两份米饭/沙拉。父C5价1999，两个已确认蛋白选择0差价，数量/重复与份量都冻结。

当前linked_menu_item_id保存成功、POS中能选、负ID快照提交成功，不代表这些通用child/task已产生。E10的legacy副菜解析不认识任意A1–A9；必须在AN-PR2/5补齐，不能将配置成功当作厨房成功。

## 价格与资格的最终校验边界

未来服务端按offer/main/targetStore/有效rule revision校验，并重新计算确定性金额；UI只是同投影预览。套餐外加购按已确认的适用规则收费，免费项0价仍保留厨房请求；原价显示不能暗示组件另收费。

穷举面类7×9=63种组合、Rice5种主菜组合，测试B4/C5直接API篡改拒绝。另测q=2/3、Add-on数量、切换单点/套餐、Cart Edit、旧菜单离线恢复、add-only Update、取消/重印原快照。不创建虚构Allô套餐菜名分支，不把本文件当可执行导入。
