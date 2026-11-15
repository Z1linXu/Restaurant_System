-- Additive only: other merchants retain their established behavior.
ALTER TABLE orders ADD COLUMN tax_amount numeric(12,2);
ALTER TABLE orders ADD COLUMN discount_type varchar(16);
ALTER TABLE orders ADD COLUMN discount_value numeric(12,2);
ALTER TABLE orders ADD COLUMN discount_name varchar(120);
ALTER TABLE order_items ADD COLUMN voided_quantity integer NOT NULL DEFAULT 0;
ALTER TABLE order_items ADD CONSTRAINT order_items_voided_quantity_check CHECK (voided_quantity >= 0 AND voided_quantity <= quantity);
CREATE TABLE allo_order_adjustments (
 id bigserial PRIMARY KEY, store_id bigint NOT NULL REFERENCES stores(id),
 order_id bigint NOT NULL REFERENCES orders(id), business_date date NOT NULL,
 request_key varchar(100) NOT NULL, request_hash char(64) NOT NULL,
 actor_id bigint REFERENCES users(id), actor_name varchar(200) NOT NULL,
 action varchar(32) NOT NULL, reason varchar(500),
 before_snapshot text NOT NULL, after_snapshot text NOT NULL,
 item_changes text NOT NULL, collected_delta numeric(12,2) NOT NULL,
 created_at timestamp NOT NULL, UNIQUE(order_id,request_key)
);
CREATE INDEX allo_adjustments_day ON allo_order_adjustments(store_id,business_date,id);
CREATE TABLE allo_discount_presets (
 id bigserial PRIMARY KEY, store_id bigint NOT NULL REFERENCES stores(id),
 name varchar(120) NOT NULL, discount_type varchar(16) NOT NULL CHECK(discount_type IN ('PERCENT','AMOUNT')),
 value numeric(12,2) NOT NULL CHECK(value >= 0), enabled boolean NOT NULL DEFAULT true,
 revision integer NOT NULL DEFAULT 1, created_by bigint NOT NULL REFERENCES users(id),
 created_at timestamp NOT NULL, updated_at timestamp NOT NULL,
 CHECK(discount_type <> 'PERCENT' OR value <= 100)
);
CREATE TABLE allo_sales_reports (
 id uuid PRIMARY KEY, store_id bigint NOT NULL REFERENCES stores(id),
 business_date date NOT NULL, snapshot text NOT NULL, rendered_text text NOT NULL,
 created_by bigint NOT NULL REFERENCES users(id), created_at timestamp NOT NULL
);
