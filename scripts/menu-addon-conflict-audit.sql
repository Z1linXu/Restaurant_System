-- Read-only current ADD_ON business-value conflicts. No order/customer/auth data.
-- Run using psql -X -v ON_ERROR_STOP=1 on the explicitly selected environment.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '15s';
SELECT s.id AS store_id, s.code AS store_code, o.option_code,
       COUNT(*) AS option_rows,
       ARRAY_AGG(DISTINCT o.name_zh ORDER BY o.name_zh) AS chinese_names,
       ARRAY_AGG(DISTINCT o.name_en ORDER BY o.name_en) AS english_names,
       ARRAY_AGG(DISTINCT o.price_delta ORDER BY o.price_delta) AS prices,
       BOOL_OR(o.is_active) AS any_eligible
FROM menu_item_options o
JOIN menu_items i ON i.id = o.menu_item_id
JOIN stores s ON s.id = i.store_id
WHERE UPPER(o.option_group) = 'ADD_ON'
GROUP BY s.id, s.code, o.option_code
HAVING COUNT(DISTINCT COALESCE(o.name_zh, '<NULL>')) > 1
    OR COUNT(DISTINCT COALESCE(o.name_en, '<NULL>')) > 1
    OR COUNT(DISTINCT COALESCE(o.price_delta::text, '<NULL>')) > 1
ORDER BY s.id, o.option_code;

-- Same-item relationships are not the same as different items' eligibility.
-- Report only contradictory duplicates; never choose a parent or active value.
SELECT s.id AS store_id, s.code AS store_code, o.menu_item_id, o.option_code,
       ARRAY_AGG(o.id ORDER BY o.id) AS option_ids,
       ARRAY_AGG(DISTINCT o.is_active ORDER BY o.is_active) AS eligibility_values,
       ARRAY_AGG(DISTINCT o.parent_option_id ORDER BY o.parent_option_id) AS parent_option_ids
FROM menu_item_options o
JOIN menu_items i ON i.id = o.menu_item_id
JOIN stores s ON s.id = i.store_id
WHERE UPPER(o.option_group) = 'ADD_ON'
GROUP BY s.id, s.code, o.menu_item_id, o.option_code
HAVING COUNT(*) > 1
   AND (COUNT(DISTINCT COALESCE(o.is_active::text, '<NULL>')) > 1
     OR COUNT(DISTINCT COALESCE(o.parent_option_id::text, '<NULL>')) > 1)
ORDER BY s.id, o.menu_item_id, o.option_code;
COMMIT;
