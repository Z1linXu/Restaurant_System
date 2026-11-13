-- Additive only: old clients, order lines and stored print artifacts remain intact.
ALTER TABLE orders ADD COLUMN order_notes TEXT;
ALTER TABLE order_update_batches ADD COLUMN order_notes_changed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE order_update_batches ADD COLUMN notes_request_hash VARCHAR(64);
