ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS station_id_snapshot BIGINT,
    ADD COLUMN IF NOT EXISTS item_sku_snapshot VARCHAR(255);

UPDATE order_items oi
SET station_id_snapshot = mi.station_id,
    item_sku_snapshot = mi.sku
FROM menu_items mi
WHERE oi.menu_item_id = mi.id
  AND (oi.station_id_snapshot IS NULL OR oi.item_sku_snapshot IS NULL);
