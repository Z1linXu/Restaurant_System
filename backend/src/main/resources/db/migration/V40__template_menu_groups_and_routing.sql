-- Additive template configuration. Deploy only to the independently authorized Test database.
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS template_option_groups_enabled boolean NOT NULL DEFAULT false;
ALTER TABLE menu_item_options ADD COLUMN IF NOT EXISTS template_group_code varchar(64);
ALTER TABLE menu_item_options ADD COLUMN IF NOT EXISTS template_default boolean NOT NULL DEFAULT false;
CREATE TABLE template_item_option_groups (
 id bigserial PRIMARY KEY,
 store_id bigint NOT NULL REFERENCES stores(id),
 menu_item_id bigint NOT NULL REFERENCES menu_items(id),
 group_code varchar(64) NOT NULL,
 name varchar(160) NOT NULL,
 kind varchar(32) NOT NULL,
 selection_mode varchar(16) NOT NULL,
 required boolean NOT NULL DEFAULT false,
 is_active boolean NOT NULL DEFAULT true,
 sort_order integer NOT NULL DEFAULT 0,
 source_library_code varchar(64),
 UNIQUE(menu_item_id,group_code)
);
CREATE INDEX ix_template_option_group ON menu_item_options(menu_item_id,template_group_code);
CREATE TABLE template_store_configuration (
 store_id bigint PRIMARY KEY REFERENCES stores(id),
 library_json text NOT NULL DEFAULT '{"groups":[]}',
 routing_json text NOT NULL DEFAULT '{"enabled":false,"groups":[],"item_overrides":[]}',
 routing_revision bigint NOT NULL DEFAULT 0
);
ALTER TABLE order_dispatch_outbox ADD COLUMN IF NOT EXISTS routing_context text;
