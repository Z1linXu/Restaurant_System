-- Optional absolute price on an existing canonical Size option. Other sizes
-- retain the Store pricing policy. Menu assignments are configured separately.
ALTER TABLE menu_item_options
    ADD COLUMN portion_price NUMERIC(12,2),
    ADD CONSTRAINT ck_menu_item_options_portion_price CHECK (
        portion_price IS NULL OR (
            portion_price >= 0
            AND option_type IS NOT DISTINCT FROM 'size'
            AND option_group IS NOT DISTINCT FROM 'SIZE'
            AND option_code IS NOT NULL
            AND option_code IN ('size_small', 'size_regular', 'size_large')
            AND parent_option_id IS NULL
            AND store_addon_id IS NULL
            AND store_remove_id IS NULL
        )
    );
