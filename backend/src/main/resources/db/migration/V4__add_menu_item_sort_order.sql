ALTER TABLE menu_items
    ADD COLUMN sort_order INTEGER;

WITH ranked_items AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY store_id, category_id
            ORDER BY id
        ) * 10 AS initial_sort_order
    FROM menu_items
)
UPDATE menu_items AS item
SET sort_order = ranked.initial_sort_order
FROM ranked_items AS ranked
WHERE item.id = ranked.id;

ALTER TABLE menu_items
    ALTER COLUMN sort_order SET NOT NULL;

CREATE INDEX idx_menu_items_store_category_sort
    ON menu_items (store_id, category_id, sort_order, id);
