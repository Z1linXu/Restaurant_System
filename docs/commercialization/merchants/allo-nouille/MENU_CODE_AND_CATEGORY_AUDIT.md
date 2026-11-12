# Menu code and category audit — 2026-11-12

Read-only source: Allô Production `43.166.242.250`, actual `2b44aa9-allo-ws-915e4ff4d8a6`, menu revision96, Flyway41. Owner’s two latest menu images are the numbering/price reference; the old PDF was not used.

All 32 numbered items match their current production category and pictured base price. Two additional zero-price refills stay unnumbered. No SKU, current base price, historical snapshot, category identity or sort order requires a production rewrite. Display prefixes derive from stable `AN_` SKU / immutable item_sku_snapshot; names remain in Chinese, English and French.

|Code|Current SKU|Name (English)|Category|Screenshot CAD|Production CAD|Result|
|---|---|---|---|---:|---:|---|
|A1|AN_A1|Shrimp wonton soup (3 wontons)|APPETIZER|4.99|4.99|MATCH|
|A2|AN_A2|Vegetable spring rolls (2 rolls)|APPETIZER|4.99|4.99|MATCH|
|A3|AN_A3|Cucumber salad|APPETIZER|5.99|5.99|MATCH|
|A4|AN_A4|Chilled beef with house sauce|APPETIZER|7.99|7.99|MATCH|
|A5|AN_A5|Fried shrimp & pork wontons (6 pcs)|APPETIZER|7.99|7.99|MATCH|
|A6|AN_A6|Shrimp tempura (6 pcs)|APPETIZER|7.99|7.99|MATCH|
|A7|AN_A7|Fried chicken wings (6 pcs)|APPETIZER|7.99|7.99|MATCH|
|A8|AN_A8|Grilled beef skewers (2 skewers)|APPETIZER|7.99|7.99|MATCH|
|A9|AN_A9|Grilled chicken skewers (2 skewers)|APPETIZER|7.99|7.99|MATCH|
|B1|AN_B1|Classic beef|NOODLE|16.99|16.99|MATCH|
|B2|AN_B2|Braised beef|NOODLE|16.99|16.99|MATCH|
|B3|AN_B3|Braised beef tendon|NOODLE|16.99|16.99|MATCH|
|B4|AN_B4|Beef ribs|NOODLE|20.99|20.99|MATCH|
|B5|AN_B5|Shrimp (6 pcs)|NOODLE|16.99|16.99|MATCH|
|B6|AN_B6|Grilled chicken|NOODLE|16.99|16.99|MATCH|
|B7|AN_B7|Shrimp wontons (10 pcs)|NOODLE|16.99|16.99|MATCH|
|B8|AN_B8|Vegetables & tofu · Shiitake broth|NOODLE|16.99|16.99|MATCH|
|C1|AN_C1|Crispy chicken cutlet|RICE|17.99|17.99|MATCH|
|C2|AN_C2|Grilled beef|RICE|16.99|16.99|MATCH|
|C3|AN_C3|Grilled chicken|RICE|16.99|16.99|MATCH|
|C4|AN_C4|Grilled pork chops|RICE|16.99|16.99|MATCH|
|C5|AN_C5|Grilled shrimp (6 pcs)|RICE|16.99|16.99|MATCH|
|C6|AN_C6|Mixed grill · Choose two|RICE|19.99|19.99|MATCH|
|D1|AN_D1|Bottled water|DRINK|1.99|1.99|MATCH|
|D2|AN_D2|Soft drink|DRINK|2.99|2.99|MATCH|
|D3|AN_D3|Sparkling water|DRINK|3.99|3.99|MATCH|
|D4|AN_D4|Hot tea|DRINK|2.99|2.99|MATCH|
|D5|AN_D5|Vietnamese iced coffee|DRINK|5.99|5.99|MATCH|
|D6|AN_D6|Fresh limeade|DRINK|5.99|5.99|MATCH|
|E1|AN_E1|Ice cream (2 scoops)|DESSERT|3.99|3.99|MATCH|
|E2|AN_E2|Fried sesame balls (6 pcs)|DESSERT|6.99|6.99|MATCH|
|E3|AN_E3|Brown sugar sticky rice cakes (6 pcs)|DESSERT|6.99|6.99|MATCH|

C1 is Crispy chicken cutlet; C6 is Mixed grill. B1 remains16.99. D contains drinks and E contains desserts. No name-prefix mutation or historical rewrite is performed.

Approved category order: APPETIZER, NOODLE, RICE, DRINK, DESSERT. All original items retain BACK_KITCHEN station; the existing direct-serve category rule excludes DRINK from kitchen tasks/GRAB. Dessert printing uses a separate configurable, frozen receipt setting and defaults to included.

Implementation validation: menuCode unit coverage includes all32 codes, repeated-prefix avoidance, language/name changes, non-Allô SKUs, unknown codes and both free refills. Runtime/browser results and final commit are populated in the final release report.
