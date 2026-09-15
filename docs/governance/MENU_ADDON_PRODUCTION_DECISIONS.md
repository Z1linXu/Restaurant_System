# Production Add-on Business Decisions

Read-only observation: 2026-09-15, explicitly selected `cloud-db-1`, using
[`menu-addon-conflict-audit.sql`](../../scripts/menu-addon-conflict-audit.sql)
with `BEGIN READ ONLY` and a 15-second statement timeout. No Production writes.
Re-run before a later release because operating menu values may change.

The query found the following 15 Store/code conflict groups. No automatic
choice is recommended; each Store may choose a different business value.
Unambiguous codes are deliberately excluded from these decision tables.

## Store 1 — St-Denis (`4483_R_SAINT_DENIS`)

| Add-on field | Current values | Recommended | Owner decision |
| --- | --- | --- | --- |
| fried_egg price | $1.80 / $1.99 | Do not guess | PENDING |
| cilantro price | $0.00 / $0.50 | Do not guess | PENDING |
| green_onion price | $0.00 / $0.50 | Do not guess | PENDING |
| tea_egg Chinese name | 加卤蛋 / 加蛋 | Do not guess | PENDING |
| extra_meat English name | Extra Beef / Extra Meat | Do not guess | PENDING |

## Store 2 — Chinatown (`CHINATOWN`)

| Add-on field | Current values | Recommended | Owner decision |
| --- | --- | --- | --- |
| fried_egg price | $1.80 / $1.99 | Do not guess | PENDING |
| cilantro price | $0.00 / $0.50 | Do not guess | PENDING |
| green_onion price | $0.00 / $0.50 | Do not guess | PENDING |
| tea_egg Chinese name | 加卤蛋 / 加蛋 | Do not guess | PENDING |
| extra_meat English name | Extra Beef / Extra Meat | Do not guess | PENDING |

## Store 3 — St-Catherine (`ST_CATHERINE`)

| Add-on field | Current values | Recommended | Owner decision |
| --- | --- | --- | --- |
| fried_egg price | $1.80 / $1.99 | Do not guess | PENDING |
| cilantro price | $0.00 / $0.50 | Do not guess | PENDING |
| green_onion price | $0.00 / $0.50 | Do not guess | PENDING |
| tea_egg Chinese name | 加卤蛋 / 加蛋 | Do not guess | PENDING |
| extra_meat English name | Extra Beef / Extra Meat | Do not guess | PENDING |

## Reconciliation boundary

Rows with identical code, Chinese name, English name and price can be linked
deterministically. Active differences preserve item eligibility. The five
groups above per Store require explicit Owner values before Production
reconciliation; schema changes do not silently resolve them. Historical order
codes, labels and prices are never changed. These decisions are required input
to a separately authorized Production release, not authorization to deploy.
