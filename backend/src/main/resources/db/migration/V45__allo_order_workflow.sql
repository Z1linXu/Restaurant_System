-- Retain all original order and option snapshots.
ALTER TABLE order_item_options ADD COLUMN voided_quantity integer NOT NULL DEFAULT 0;
ALTER TABLE order_item_options ADD CONSTRAINT order_item_options_voided_quantity_check CHECK (voided_quantity >= 0);
ALTER TABLE allo_discount_presets ADD COLUMN kind varchar(16) NOT NULL DEFAULT 'DISCOUNT'
    CHECK (kind IN ('DISCOUNT', 'COUPON'));
ALTER TABLE orders ADD COLUMN discount_coupon_id bigint REFERENCES allo_discount_presets(id);
CREATE INDEX orders_store_submitted_history ON orders(store_id, submitted_at DESC, id DESC)
    WHERE submitted_at IS NOT NULL;
