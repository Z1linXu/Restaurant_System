# Printing Rule Reconciliation Matrix

Status:

```text
PHASE_A11_PRINTING_RULE_TRANSPARENCY_AND_CONFIGURATION_DESIGN = AUDIT_COMPLETE
A11_IMPLEMENTATION = REPOSITORY_CANDIDATE_READY
```

Authority: fresh `origin/main@78ac87b633ba6d4e113d52ed65eddb8fcc06eacd`.

Status vocabulary is limited to:
`MATCH`, `DOCUMENT_STALE`, `CODE_STALE`, `PARTIAL_MATCH`,
`UNDOCUMENTED_CODE_RULE`, `DOCUMENT_ONLY_RULE`, `AMBIGUOUS`.

Implementation overlay:

This matrix preserves the pre-A11 audit findings. The A11 implementation
candidate now addresses the former `DOCUMENT_ONLY_RULE` / `CODE_STALE` gaps
for Owner UI, Store-scoped independent display configuration, post-A11 profile
contract support and frozen rule revision capture through:

- additive V17 `printing_display_rule_sets` /
  `printing_display_rule_revisions`;
- item aliases in Menu Management;
- Store-global dictionaries, constrained conditions, validation, preview and
  revision history in Printing Settings;
- `print_jobs.printing_rule_revision_id` and
  `print_jobs.printing_rule_fingerprint`;
- post-A11 Store Profile contract validation requiring a
  `PRINTING_DISPLAY_RULES` artifact for non-v1 profile versions.

The detailed table below remains useful as the compatibility inventory for
legacy snapshots, fallback behavior and remaining system-owned routing logic.

## Matrix

| rule_id | business meaning | documented rule | actual code behavior | GRAB behavior | FRONTDESK_RECEIPT behavior | HOT_KITCHEN behavior | source-of-truth today | status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `A11-R001` | Print output modules exist as independent renderer modules. | Printing docs list `GRAB`, `FRONTDESK_RECEIPT`, `HOT_KITCHEN`. | `ReceiptRenderer` implementations are registered by `PrintModuleCode`. | `GrabReceiptRenderer`. | `FrontdeskReceiptRenderer`. | `HotKitchenReceiptRenderer`. | RENDERER_HARDCODE + module assignment config. | `MATCH` |
| `A11-R002` | GRAB task eligibility. | GRAB uses kitchen task snapshots and station/category semantics. | GRAB accepts station codes `NOODLE`, `WOK`, `COLD`, `DEEPFRIED`; cancelled tasks are filtered out. | Included only for those hardcoded stations. | Not applicable. | Independent eligibility service. | RENDERER_HARDCODE. | `PARTIAL_MATCH` |
| `A11-R003` | GRAB top label. | Table/pickup labels are described operationally. | Dine-in prints `桌号：{table}` or `桌号：Walk-in`; takeout prints pickup number when present. | Chinese colon `：`; uses split-table formatter. | Separate label grammar. | Same grammar as GRAB for hot-kitchen. | RENDERER_HARDCODE + order snapshot. | `MATCH` |
| `A11-R004` | Split table A/B display. | Docs mention split table labels. | `PrintTableDisplayFormatter` maps `A`/`-A` to `左`, `B`/`-B` to `右`. | Applies. | Applies with `桌号: `. | Applies. | RENDERER_HARDCODE. | `MATCH` |
| `A11-R005` | Update ticket marker. | API/System docs mention update-ticket semantics. | Renderers print `UPDATED`; Frontdesk also prints `Added items only`. | `UPDATED` only. | `UPDATED` + `Added items only`. | `UPDATED` only. | RENDERER_HARDCODE. | `MATCH` |
| `A11-R006` | GRAB task sort order. | Operations doc says COLD, DEEPFRIED, NOODLE/WOK, fallback. | Code first uses station code, then category fallback. | COLD priority 1, DEEPFRIED 2, NOODLE/WOK 3, fallback 4. | Not applicable. | Hot-kitchen has independent aggregation order. | RENDERER_HARDCODE. | `MATCH` |
| `A11-R007` | COLD/side grouping. | Same side dish with same demands merges. | Group key is display name plus sorted demands; quantity printed as `{name} xN`. | Applies to COLD/SIDE/COLD_APPETIZER. | Combo side is shown under main item, not grouped the same way. | Cold combo side excluded unless task itself is hot. | RENDERER_HARDCODE + kitchen snapshots. | `MATCH` |
| `A11-R008` | Fried item grouping. | Operations doc describes grouping by stable item/task/option dimensions. | Code groups by menu item, category, station, item name, special, note, combo role and option signatures. | Applies. | Receipt does not group fried kitchen tasks; item quantity expands. | Hot-kitchen groups similar hot items by stable option/note key. | RENDERER_HARDCODE + order snapshots. | `MATCH` |
| `A11-R009` | GRAB fried quantity symbol. | Operations doc still says `{quantity}*{name}` and examples `2*炸虾`. | Current code and tests use multiplication sign: `2×炸虾`. | `×` without space. | Receipt item quantity uses `x` or `* combo` depending case. | ` ×` with spaces for non-noodle hot items. | CURRENT_CODE. | `DOCUMENT_STALE` |
| `A11-R010` | Noodle item recognition. | Docs say noodle/category/SKU semantics. | Formatter treats known noodle categories, `NOODLE` station, Chinese name containing `面`, or English containing `noodle` as noodle. | Applies to grouping/quantity format. | Receipt only has special soup-noodle branch for `SOUP_NOODLE`. | Applies for eligible hot-kitchen tasks. | STRING_MATCH + CATEGORY/STATION. | `PARTIAL_MATCH` |
| `A11-R011` | Kitchen SKU aliases. | Operations doc lists aliases such as `牛炒`, `鸡炒`, `番炒`, `素炒`, `炸`, `担`, `鸡凉`, `红`, `酸`. | `OrderServiceImpl` hardcodes SKU-to-short-name/base-code switches. | Consumes kitchen task snapshots built from those aliases. | Does not use SKU aliases except through item snapshots. | Reuses kitchen task snapshots for eligible tasks. | ORDER_SERVICE_HARDCODE. | `MATCH` |
| `A11-R012` | Product-specific receipt alias for traditional beef noodle. | Not documented in the operations GRAB rule as a Frontdesk display rule. | Frontdesk replaces `传统牛肉面` with `牛肉面`. | GRAB keeps kitchen snapshot/shorthand. | `传统牛肉面` becomes `牛肉面`; size may prefix it. | Hot-kitchen uses kitchen snapshot/special. | RENDERER_HARDCODE. | `UNDOCUMENTED_CODE_RULE` |
| `A11-R013` | Size display in kitchen shorthand. | Docs describe `中`/`大`, non-default size behavior. | `mapSizeCode` maps any containing `大` to `大`, any containing `小` to `小`, otherwise `中`. | Encoded in kitchen snapshot. | Separate zh/en size mapping. | Reuses kitchen snapshot. | ORDER_SERVICE_HARDCODE + option snapshot. | `PARTIAL_MATCH` |
| `A11-R014` | Size display on Frontdesk receipt. | A0.1 docs define system-controlled Small/Regular/Large, not receipt wording details. | Frontdesk maps zh/en to `小碗/中碗/大碗` for soup noodles and `Small/Regular/Large` for non-soup items. | Not applicable. | Applies. | Not applicable. | RENDERER_HARDCODE + option snapshot. | `UNDOCUMENTED_CODE_RULE` |
| `A11-R015` | Noodle type abbreviations. | Operations doc maps `二细/三细/细/毛细/韭叶/宽/大宽`. | `OrderServiceImpl.mapNoodleCode` hardcodes that mapping. | Encoded in kitchen snapshot. | Frontdesk prints raw noodle option label, not abbreviation. | Reuses kitchen snapshot. | ORDER_SERVICE_HARDCODE. | `MATCH` |
| `A11-R016` | Default noodle omission by SKU. | Docs say defaults are SKU-specific. | Hardcoded defaults: beef/veg/dandan use `三细`, zha jiang uses `韭叶`, chicken cold noodle uses `细` or `细面`. | Encoded in kitchen snapshot. | Raw noodle label may still show if receipt chooses to print it. | Reuses kitchen snapshot. | ORDER_SERVICE_HARDCODE. | `MATCH` |
| `A11-R017` | Spiciness display in kitchen shorthand. | Docs say `不辣` hidden, `少辣/正常辣/加辣` map to `（少s）/（s）/（大s）`; unknown defaults conservatively. | `mapSpicyCode` implements that mapping with unknown `（s）`. | Encoded in kitchen snapshot. | Frontdesk prints `辣度: {raw label}`. | Reuses kitchen snapshot. | ORDER_SERVICE_HARDCODE + option snapshot. | `MATCH` |
| `A11-R018` | Spiciness detection on Frontdesk receipt. | Not fully documented as separate receipt rule. | Frontdesk treats option type `spicy_level`, group `SPICY_LEVEL`, or code patterns as spiciness. | Not applicable. | Prints `辣度: {zh/en label}`. | Not applicable. | RENDERER_HARDCODE + OPTION CODE. | `UNDOCUMENTED_CODE_RULE` |
| `A11-R019` | Vegetable noodle soup-base wording. | Operations doc describes `素` and `素（肉汤）`. | Only SKU `vegetable_noodle` maps soup base `素汤` to `素`, `肉汤/牛汤` to `素（肉汤）`, absent to `素`. | Encoded in kitchen snapshot. | Not a special receipt rule. | Reuses kitchen snapshot. | ORDER_SERVICE_HARDCODE. | `MATCH` |
| `A11-R020` | Add-on kitchen tokens. | Operations doc lists examples. | `mapAddonToken` maps option codes to tokens: `+面`, `+蛋`, `+煎`, `+肉`, `+萝`, `加上海青`, `+香`, `+葱`, `+酱`, `+西兰`, `+包`, `+玉`, `+海`, `+菇`, `+胡`, and combo side tokens. | Consumed by GRAB. | Chargeable add-ons print full option label and quantity. | Consumed through kitchen snapshot. | ORDER_SERVICE_HARDCODE + OPTION CODE. | `MATCH` |
| `A11-R021` | Remove kitchen tokens. | Operations doc lists examples. | `mapRemoveToken` maps stable remove codes to `走...`/`少面`; unknown falls back to option label. | Consumed by GRAB. | Zero-price remove options are not generally printed except combo side removes. | Consumed through kitchen snapshot. | ORDER_SERVICE_HARDCODE + OPTION CODE. | `PARTIAL_MATCH` |
| `A11-R022` | Legacy name fallback for add/remove semantics. | Docs say legacy label fallback exists. | `canonicalAddonCode`, `canonicalRemoveCode`, and `OptionSemanticResolver` contain label string switches. | Indirect. | Indirect. | Fried-egg eligibility uses legacy labels. | LEGACY_FALLBACK STRING_MATCH. | `MATCH` |
| `A11-R023` | Add-on quantity aggregation. | Docs mention multi-add-on quantities. | Order service aggregates `+` tokens with `x`/`*`; renderer normalizes output to `×` for noodle config. | `+蛋 +蛋x2` becomes `+蛋×3`. | Paid add-ons print `{label} xN`. | Reuses noodle formatter. | ORDER_SERVICE_HARDCODE + RENDERER_HARDCODE. | `MATCH` |
| `A11-R024` | Green onion/cilantro compression. | Docs say add onion + cilantro -> `加青`; remove onion + cilantro -> `走青`; bok choy preserved. | GRAB simplifies only when both same-direction tokens exist; `加上海青`/`走上海青` are preserved. | Applies. | Explicitly not applied in Frontdesk tests. | Noodle formatter path can apply to hot-kitchen through GRAB normalizer only where shared. | RENDERER_HARDCODE. | `MATCH` |
| `A11-R025` | Notes wording. | Docs say notes print as `备注：...` under relevant item. | GRAB/HotKitchen add `备注：`; Frontdesk adds `备注：` for item notes in current code. | Applies. | Applies in code; older System doc still says Frontdesk intentionally does not print item notes. | Applies. | CURRENT_CODE. | `DOCUMENT_STALE` |
| `A11-R026` | Combo detection. | A0/A0.2 docs say combo source is Store pricing/config plus item COMBO_ALLOWED. | Frontdesk also treats combo role, option group `COMBO`, or label containing `套餐`/`combo` as combo. | Kitchen snapshot handles combo side tasks. | Applies. | Eligibility separates combo side tasks. | RENDERER_HARDCODE + LEGACY STRING_MATCH. | `PARTIAL_MATCH` |
| `A11-R027` | Combo egg display. | A0.2 docs define combo egg components; display wording is not fully captured as print rule. | Frontdesk prints `走蛋` when no combo egg option, otherwise `鸡蛋: {cleaned label}`. | Combo egg remains in noodle shorthand as `+蛋` or `+煎`. | Applies. | Fried egg drives hot-kitchen eligibility and task content. | RENDERER_HARDCODE + OPTION CODE. | `UNDOCUMENTED_CODE_RULE` |
| `A11-R028` | Combo side display. | A0/A5.5 docs define Store combo groups/components and legacy side task behavior. | Frontdesk prints `小菜: {cleaned label}` and child remove instructions. Order service creates COLD synthetic tasks for three reviewed legacy side components. | Separate COLD task lines. | Main receipt child lines. | Excluded unless side task itself is hot. | MIXED: STORE CONFIG + HARDCODED LEGACY COMPONENTS. | `PARTIAL_MATCH` |
| `A11-R029` | Combo side label cleaning. | Not documented as a reusable rule. | Frontdesk strips leading `套餐` or `combo ` from egg/side labels. | Not applicable. | Applies. | Not applicable. | RENDERER_HARDCODE. | `UNDOCUMENTED_CODE_RULE` |
| `A11-R030` | Chargeable option receipt lines. | Not fully documented as a display rule. | Frontdesk prints positive `price_delta` options except Size/Combo groups and labels containing combo. | Not applicable. | `{label} xN`. | Not applicable. | RENDERER_HARDCODE + ORDER SNAPSHOT. | `UNDOCUMENTED_CODE_RULE` |
| `A11-R031` | Receipt subtotal/tax/total labels. | API/System docs mention receipt rendering and tax rate. | Frontdesk prints `Subtotal`, `Tax ({rate})`, `Total`, `Submitted/Printed/Created`. | Not applicable. | Applies. | Not applicable. | RENDERER_HARDCODE + TaxCalculator. | `MATCH` |
| `A11-R032` | HOT_KITCHEN routing for fried/wok/fried-egg. | System docs document stable semantics. | `HotKitchenPrintEligibilityService` implements station/category/SKU/fried-egg checks. | Not applicable. | Not applicable. | Applies. | CODE + SYSTEM_DOCUMENTATION. | `MATCH` |
| `A11-R033` | HOT_KITCHEN hardcoded wok SKU fallback. | System docs mention known chow-mein SKUs. | `WOK_SKUS` hardcodes four chow-mein SKUs. | Not applicable. | Not applicable. | Eligible by SKU fallback. | SEMANTIC SERVICE HARDCODE. | `MATCH` |
| `A11-R034` | HOT_KITCHEN fried egg semantics. | System docs document code/current/legacy label fallback. | `OptionSemanticResolver` checks `fried_egg`, `combo_fried_egg`, suffixes and legacy labels `加煎蛋`/`套餐煎蛋`/English text. | Not applicable. | Not applicable. | Applies. | OPTION CODE + LEGACY STRING_MATCH. | `MATCH` |
| `A11-R035` | HOT_KITCHEN header and takeout label. | Partially documented. | Renderer prints `HOT KITCHEN`; takeout bottom line is `外卖 / TAKEOUT`. | Not applicable. | Different receipt header. | Applies. | RENDERER_HARDCODE. | `PARTIAL_MATCH` |
| `A11-R036` | HOT_KITCHEN quantity and aggregation style. | Tests document examples. | Noodle quantity uses shared noodle formatter; non-noodle uses `{name} ×N` with spaces. | GRAB fried uses `N×name`; noodle shared. | Frontdesk expands per copy. | Applies. | RENDERER_HARDCODE + TESTS. | `MATCH` |
| `A11-R037` | PrintJob render snapshot. | API/System docs say MOCK/PAD_DIRECT render/store preview text. | Dispatch attaches `rendered_text_snapshot` before MOCK/PAD_DIRECT/REAL dispatch. | Snapshot stored. | Snapshot stored. | Snapshot stored. | PRINT_JOB SNAPSHOT. | `MATCH` |
| `A11-R038` | Single job reprint historical behavior. | System docs say Print Center job reprint reprints from job snapshot. | `reprintJob` uses `job.rendered_text_snapshot` if present; only re-renders when missing. | Frozen by job. | Frozen by job. | Frozen by job. | PRINT_JOB SNAPSHOT. | `MATCH` |
| `A11-R039` | Order-level reprint historical behavior. | API/System docs say manual order reprint renders the full current order. | `reprintOrder` creates a new job and calls the current renderer against order snapshots. | Current renderer, old order snapshots. | Current renderer, old order snapshots. | Current renderer, old order snapshots. | CURRENT_CODE. | `MATCH` |
| `A11-R040` | Test print content. | Printing Settings exposes module tests. | Module tests use synthetic hardcoded orders/options/tasks in `PrintDispatcherServiceImpl`. | Synthetic GRAB content. | Synthetic receipt content. | Synthetic hot-kitchen content. | TEST HARDCODE. | `UNDOCUMENTED_CODE_RULE` |
| `A11-R041` | Owner UI for display/naming rules. | Owner now requires transparency; existing docs do not define a UI. | No Menu Management or Printing Settings UI/API for print aliases/dictionaries exists. | Not configurable. | Not configurable. | Not configurable. | ABSENT. | `DOCUMENT_ONLY_RULE` |
| `A11-R042` | Store Profile support for display rules. | A4/A5 profiles support safe templates, logical printing, menu, combo, etc.; no display-rule artifact. | `ST_DENIS_CANONICAL_PROFILE/v1` cannot include a printing display rules snapshot today. | Not materializable. | Not materializable. | Not materializable. | ABSENT PROFILE CONTRACT. | `DOCUMENT_ONLY_RULE` |
| `A11-R043` | Store-scoped independent rule configuration. | Product rule requires `BUILD ONCE, CONFIGURE MANY`. | Current display rules are shared code; Store data provides menu/option names but not per-output aliases. | Shared globally. | Shared globally. | Shared globally. | SHARED_CODE. | `CODE_STALE` |
| `A11-R044` | Operational routing separated from display. | A8/A9 docs separate logical topology, runtime mode and physical binding. | Current routing/dispatch are separate from display renderers, but some display semantics also affect HOT_KITCHEN eligibility through code. | Mostly display. | Display. | Eligibility + display. | MIXED. | `PARTIAL_MATCH` |

## Summary

```text
total_rules_found = 44
hardcoded_or_string_match_rules = 31
database_or_snapshot_backed_rules = 13
document_code_status:
  MATCH = 24
  PARTIAL_MATCH = 8
  DOCUMENT_STALE = 2
  CODE_STALE = 1
  UNDOCUMENTED_CODE_RULE = 7
  DOCUMENT_ONLY_RULE = 2
  AMBIGUOUS = 0
```
