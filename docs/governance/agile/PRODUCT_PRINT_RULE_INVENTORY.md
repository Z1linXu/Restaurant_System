# Product Print Rule Inventory

Status:

```text
PHASE_A11_PRODUCT_PRINT_RULE_INVENTORY = AUDIT_COMPLETE
A11_IMPLEMENTATION = REPOSITORY_CANDIDATE_READY
```

This inventory lists product- or wording-specific printing rules found in
fresh code and docs. It intentionally does not propose moving every rule into
Owner configuration. Operational routing, state-machine and anti-duplicate
logic remain system logic.

Implementation overlay:

The A11 candidate externalizes the Owner-approved safe display subset as
structured Store configuration:

- item aliases per output;
- Store-global Size/noodle/spicy/modifier display dictionaries;
- constrained display conditions;
- menu/order snapshot fallback;
- historical rule revision capture for rendered PrintJob output.

It intentionally keeps routing, HOT_KITCHEN eligibility, PrintJob state,
queue/retry semantics, assignment, hardware binding, pricing, payment, tax and
reports in protected application logic.

Classification vocabulary:

- `HARD_CODED`: current behavior is embedded in Java/TypeScript code.
- `CONFIGURABLE_ALREADY`: current behavior is driven by Store/menu data.
- `SAFE_TO_EXTERNALIZE`: suitable for future structured Owner configuration.
- `SHOULD_REMAIN_CODE`: should stay protected system logic.
- `LEGACY`: fallback for old snapshots or backward compatibility.
- `AMBIGUOUS`: requires Owner/product decision before externalization.

## Inventory

| inventory_id | rule surface | current location | examples / values | current classification | future disposition |
| --- | --- | --- | --- | --- | --- |
| `A11-P001` | Chow-mein kitchen aliases by SKU. | `OrderServiceImpl.buildKitchenDisplayNameZh`, `mapItemBaseCode`. | `beef_chow_mein -> 牛炒`, `chicken_chow_mein -> 鸡炒`, `tomato_chow_mein -> 番炒`, `vegetable_chow_mein -> 素炒`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as item output alias defaults. |
| `A11-P002` | Dry/cold noodle kitchen aliases by SKU. | `OrderServiceImpl.buildKitchenDisplayNameZh`, `mapItemBaseCode`. | `zha_jiang_noodle -> 炸`, `dan_dan_noodle -> 担`, `cold_noodle_shredded_chicken -> 鸡凉`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as item output alias defaults. |
| `A11-P003` | Soup noodle base aliases by SKU. | `OrderServiceImpl.mapItemBaseCode`. | `braised_beef_tendon_noodle -> 红`, `pickled_vegetable_beef_noodle -> 酸`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE`; should be item/alias config, not Store-name logic. |
| `A11-P004` | Side dish base aliases. | `OrderServiceImpl.mapItemBaseCode` and kitchen task snapshots. | `cucumber_salad -> 黄瓜`, `edamame -> 毛豆`, `shredded_potato -> 土豆`, `braised_beef_shank_salad -> 牛展`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as item alias defaults; existing Store Combo behavior still requires separate side-task logic. |
| `A11-P005` | Traditional beef noodle Frontdesk rename. | `FrontdeskReceiptRenderer.resolveReceiptDisplayName`. | `传统牛肉面` prints as `牛肉面`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as FRONTDESK alias. |
| `A11-P006` | Size zh shorthand for kitchen. | `OrderServiceImpl.mapSizeCode`. | contains `大 -> 大`, contains `小 -> 小`, else `中`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as Store-global Size dictionary with canonical size codes. |
| `A11-P007` | Size Frontdesk wording. | `FrontdeskReceiptRenderer.mapSizeZh/mapSizeEn`. | `大/大碗/large -> 大碗/Large`, `小/小碗/small -> 小碗/Small`, `中/标准/regular/standard -> 中碗/Regular`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as output-specific Size dictionary. |
| `A11-P008` | Noodle type abbreviations. | `OrderServiceImpl.mapNoodleCode`. | `二细 -> 二`, `三细 -> 三`, `毛细 -> 毛`, `韭叶 -> 韭`, `宽 -> 宽`, `大宽 -> 大宽`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as Store-global noodle dictionary with optional item overrides. |
| `A11-P009` | SKU-specific default noodle omission. | `OrderServiceImpl.isDefaultNoodleType`. | `三细` hidden for several soup noodles; `韭叶` hidden for zha jiang; `细/细面` hidden for chicken cold noodle. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as item-specific default option display behavior. |
| `A11-P010` | Spiciness kitchen shorthand. | `OrderServiceImpl.mapSpicyCode`. | `少辣 -> （少s）`, `正常辣 -> （s）`, `加辣 -> （大s）`, `不辣` hidden. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as Store-global spiciness dictionary. |
| `A11-P011` | Unknown spiciness fallback. | `OrderServiceImpl.mapSpicyCode`. | Unknown nonblank spicy label becomes `（s）`. | `HARD_CODED` | `AMBIGUOUS`; fail-closed validation may be better than silent fallback for new rules. |
| `A11-P012` | Vegetable noodle soup-base wording. | `OrderServiceImpl.mapSoupBaseCode`. | `素汤 -> 素`, `肉汤/牛汤 -> 素（肉汤）`, absent -> `素`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as item-specific option-display rule. |
| `A11-P013` | Add-on display tokens. | `OrderServiceImpl.mapAddonToken`. | `extra_noodle -> +面`, `tea_egg -> +蛋`, `fried_egg -> +煎`, `bok_choy -> 加上海青`, `green_onion -> +葱`, etc. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as Store-global modifier dictionary with item/output overrides. |
| `A11-P014` | Remove display tokens. | `OrderServiceImpl.mapRemoveToken`. | `cilantro -> 走香`, `green_onion -> 走葱`, `bok_choy -> 走上海青`, `peanut -> 走花生`, etc. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as modifier dictionary. |
| `A11-P015` | Legacy add/remove label fallback. | `canonicalAddonCode`, `canonicalRemoveCode`. | Chinese names such as `套餐毛豆`, `加煎蛋`, `走花生碎` map to stable codes. | `LEGACY` | `SHOULD_REMAIN_CODE` as backward compatibility parser, but new runtime should require stable codes. |
| `A11-P016` | Add-on quantity aggregation. | `OrderServiceImpl.aggregateKitchenSecondaryParts`, `GrabReceiptRenderer`, `KitchenNoodlePrintFormatter`. | `+蛋 +蛋x2 -> +蛋×3`; supports `x` and `*`. | `HARD_CODED` | `SHOULD_REMAIN_CODE`; Owner can choose symbol, but aggregation/anti-duplication should be system logic. |
| `A11-P017` | Green compression. | `GrabReceiptRenderer.simplifyGreenOptions`. | `加葱 + 加香菜 -> 加青`; `走葱 + 走香菜 -> 走青`; `加上海青/走上海青` preserved. | `HARD_CODED` | `AMBIGUOUS`; likely configurable as a safe structured rule, not arbitrary expression. |
| `A11-P018` | Combo side component codes. | `OrderServiceImpl`, `FrontdeskReceiptRenderer`, A0.2/A5.5 Store Combo docs. | `combo_edamame`, `combo_shredded_potato`, `combo_cucumber_salad`. | `CONFIGURABLE_ALREADY` + `HARD_CODED` bridge | `SAFE_TO_EXTERNALIZE`; A11 should consume Store Combo config and output aliases without preserving hardcoded St-Denis-only component codes forever. |
| `A11-P019` | Combo egg component codes. | `OrderServiceImpl`, `OptionSemanticResolver`, A0.2/A5.5 docs. | `combo_tea_egg`, `combo_fried_egg`. | `CONFIGURABLE_ALREADY` + `HARD_CODED` bridge | `SAFE_TO_EXTERNALIZE`; display aliases should be Store-owned. |
| `A11-P020` | Combo side label cleaning. | `FrontdeskReceiptRenderer.cleanComboSideLabel/cleanComboEggLabel`. | Strip leading `套餐` or `combo `. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` through explicit aliases; avoid magic stripping for future Stores. |
| `A11-P021` | Frontdesk combo/no-combo wording. | `FrontdeskReceiptRenderer.buildItemLine`. | Soup noodle combo uses `1* combo {name}`; non-soup combo uses `Combo {name}`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as output-specific quantity/combo template, but only with structured templates. |
| `A11-P022` | Chargeable option display. | `FrontdeskReceiptRenderer.resolveChargeableOptionLines`. | Positive-price non-size/non-combo options print `{label} xN`. | `HARD_CODED` + order snapshot | `SAFE_TO_EXTERNALIZE` for label and symbol; positive-price filter should remain code. |
| `A11-P023` | Hot-kitchen wok SKU fallback. | `HotKitchenPrintEligibilityService.WOK_SKUS`. | Four chow-mein SKUs are hot-kitchen eligible. | `HARD_CODED` | `SHOULD_REMAIN_CODE` until routing is modeled by station/category/business behavior; eligibility is operational logic, not Owner wording. |
| `A11-P024` | Fried egg eligibility. | `OptionSemanticResolver`. | `fried_egg`, suffixes, `combo_fried_egg`, `加煎蛋`, `套餐煎蛋`, English fallback. | `HARD_CODED` + `LEGACY` | `SHOULD_REMAIN_CODE` for eligibility; display aliases can be configurable separately. |
| `A11-P025` | Station/category print routing. | `GrabReceiptRenderer`, `HotKitchenPrintEligibilityService`, printer assignments. | `COLD`, `DEEPFRIED`, `NOODLE`, `WOK`, category fallbacks. | `HARD_CODED` + topology config | `SHOULD_REMAIN_CODE`; owner display rules must not alter routing safety. |
| `A11-P026` | Table-side A/B wording. | `PrintTableDisplayFormatter`. | `A/-A -> 左`, `B/-B -> 右`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` as Store display dictionary if desired. |
| `A11-P027` | Receipt labels. | `FrontdeskReceiptRenderer`. | `Subtotal`, `Tax`, `Total`, `Submitted`, `Printed`, `Created`, `Order Type: Takeout`. | `HARD_CODED` | `SAFE_TO_EXTERNALIZE` for labels/localization; money math remains code. |
| `A11-P028` | Ticket structural markup. | `PrintMarkup`, renderers, `PrintJobServiceImpl`. | `[[DOUBLE_HEIGHT]]`, `[[LARGE]]`, `[[SMALL]]`, ESC/POS conversion. | `HARD_CODED` | `SHOULD_REMAIN_CODE`; Owner may choose predefined style, not raw control codes. |
| `A11-P029` | Print job state, queue and retries. | `PrintJobServiceImpl`, `PadPrintJobServiceImpl`, dispatch processor. | `PENDING/CLAIMED/PRINTING/PRINTED/FAILED/CANCELLED`, leases. | `SHOULD_REMAIN_CODE` | Not part of A11 display configuration. |
| `A11-P030` | Printer assignment and hardware readiness. | Print Center and A8 hardware contract. | Logical printers/assignments/runtime mode/device readiness. | `CONFIGURABLE_ALREADY` | Keep separate from display rules; Phase B validates alongside A11 but does not merge them. |

## Owner-configurable vs system-safe split

Recommended Owner-configurable surfaces:

- item aliases per output: `GRAB`, `FRONTDESK_RECEIPT`, `HOT_KITCHEN`;
- Store-global dictionaries for canonical Size, noodle type, spiciness,
  add/remove modifiers and table side labels;
- item-specific overrides for default noodle omission and unusual wording;
- output-specific combo, quantity and label wording selected from structured
  fields/templates.

Recommended protected system logic:

- PrintJob state machine, retry, lease, queue and duplicate prevention;
- printer assignment, routing safety and hardware readiness;
- order totals, tax, payment and reports;
- Store/Organization authorization and Store isolation;
- legacy snapshot parsers needed to preserve historical orders.

## Counts

```text
product_rule_inventory_rows = 30
hardcoded_or_legacy_rows = 26
already_configurable_rows = 2
should_remain_code_rows = 7
safe_to_externalize_rows = 20
ambiguous_rows = 2
```
