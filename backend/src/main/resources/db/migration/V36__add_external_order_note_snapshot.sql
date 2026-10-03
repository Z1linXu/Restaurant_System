-- Frozen whole-order kitchen instructions, separate from item notes.
-- Existing order and print snapshots are intentionally not rewritten.
ALTER TABLE orders ADD COLUMN external_order_note_snapshot TEXT;
