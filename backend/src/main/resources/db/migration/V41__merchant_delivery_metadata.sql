-- Independent merchant Test delivery; never apply to existing stores without release authorization.
ALTER TABLE stores ADD COLUMN presentation_json text;
ALTER TABLE menu_categories ADD COLUMN name_fr varchar(255);
ALTER TABLE menu_items ADD COLUMN name_fr varchar(255);
ALTER TABLE menu_items ADD COLUMN refill_category_code varchar(64);
ALTER TABLE menu_item_options ADD COLUMN name_fr varchar(255);
ALTER TABLE order_items ADD COLUMN item_name_snapshot_fr varchar(255);
ALTER TABLE order_item_options ADD COLUMN option_name_snapshot_fr varchar(255);
ALTER TABLE template_item_option_groups ADD COLUMN name_translations_json text;
ALTER TABLE template_item_option_groups ADD COLUMN min_select integer;
ALTER TABLE template_item_option_groups ADD COLUMN max_select integer;
CREATE TABLE merchant_menu_import_entries (
 store_id bigint NOT NULL REFERENCES stores(id),
 source_key varchar(160) NOT NULL,
 entity_kind varchar(32) NOT NULL,
 entity_id bigint NOT NULL,
 source_hash varchar(64) NOT NULL,
 applied_hash varchar(64) NOT NULL,
 imported_at timestamp NOT NULL DEFAULT current_timestamp,
 PRIMARY KEY(store_id,source_key)
);
