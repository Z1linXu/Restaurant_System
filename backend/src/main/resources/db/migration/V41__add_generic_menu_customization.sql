-- Additive menu data only; existing Profiles, orders and print payloads are untouched.
ALTER TABLE public.menu_categories ADD COLUMN name_fr varchar(255);
ALTER TABLE public.menu_items ADD COLUMN name_fr varchar(255);
ALTER TABLE public.menu_items ADD COLUMN sellable boolean NOT NULL DEFAULT true;
ALTER TABLE public.menu_items ADD COLUMN customization_config text;
ALTER TABLE public.menu_item_options ADD COLUMN name_fr varchar(255);
ALTER TABLE public.store_addons ADD COLUMN name_fr varchar(255);
ALTER TABLE public.store_combo_groups ADD COLUMN name_fr varchar(255);
ALTER TABLE public.store_combo_components ADD COLUMN name_fr varchar(255);
ALTER TABLE public.order_items ADD COLUMN item_name_snapshot_fr varchar(255);
ALTER TABLE public.order_item_options ADD COLUMN option_name_snapshot_fr varchar(255);
ALTER TABLE public.order_item_options ADD COLUMN component_id_snapshot bigint;
ALTER TABLE public.order_item_options ADD COLUMN linked_menu_item_id_snapshot bigint;
ALTER TABLE public.order_item_options ADD COLUMN linked_station_id_snapshot bigint;
ALTER TABLE public.kitchen_tasks ADD COLUMN component_option_id bigint;
