-- Additive, initially empty: existing order/financial/kitchen/print snapshots are unchanged.
ALTER TABLE uber_eats_orders ADD COLUMN item_financial_snapshot_json TEXT;
