WITH ranked_options AS (
    SELECT
        option_row.id,
        ROW_NUMBER() OVER (
            PARTITION BY option_row.menu_item_id
            ORDER BY
                CASE
                    WHEN LOWER(option_row.option_code) IN (
                        'noodle_thin',
                        'cold_noodle_shredded_chicken_noodle_type_thin'
                    ) THEN 0
                    ELSE 1
                END,
                option_row.sort_order NULLS LAST,
                option_row.id
        ) * 10 AS next_sort_order
    FROM menu_item_options option_row
    JOIN menu_items item ON item.id = option_row.menu_item_id
    WHERE LOWER(item.sku) = 'cold_noodle_shredded_chicken'
      AND LOWER(option_row.option_type) = 'noodle_type'
      AND EXISTS (
          SELECT 1
          FROM menu_item_options thin_option
          WHERE thin_option.menu_item_id = item.id
            AND LOWER(thin_option.option_type) = 'noodle_type'
            AND LOWER(thin_option.option_code) IN (
                'noodle_thin',
                'cold_noodle_shredded_chicken_noodle_type_thin'
            )
      )
)
UPDATE menu_item_options option_row
SET sort_order = ranked_options.next_sort_order,
    updated_at = CURRENT_TIMESTAMP
FROM ranked_options
WHERE option_row.id = ranked_options.id
  AND option_row.sort_order IS DISTINCT FROM ranked_options.next_sort_order;

UPDATE stores store_row
SET menu_revision = COALESCE(store_row.menu_revision, 0) + 1,
    menu_updated_at = CURRENT_TIMESTAMP,
    updated_at = CURRENT_TIMESTAMP
WHERE EXISTS (
    SELECT 1
    FROM menu_items item
    JOIN menu_item_options thin_option ON thin_option.menu_item_id = item.id
    WHERE item.store_id = store_row.id
      AND LOWER(item.sku) = 'cold_noodle_shredded_chicken'
      AND LOWER(thin_option.option_type) = 'noodle_type'
      AND LOWER(thin_option.option_code) IN (
          'noodle_thin',
          'cold_noodle_shredded_chicken_noodle_type_thin'
      )
);
