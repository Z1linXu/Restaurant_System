-- Additive journal only. No existing order, snapshot, task, outbox or print row is updated.
CREATE TABLE uber_eats_financial_corrections (
    id BIGSERIAL PRIMARY KEY,
    operation_key VARCHAR(180) NOT NULL UNIQUE,
    environment VARCHAR(32) NOT NULL,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    uber_eats_order_id BIGINT NOT NULL REFERENCES uber_eats_orders(id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('APPLY', 'REVOKE')),
    snapshot_version INTEGER NOT NULL CHECK (snapshot_version = 2),
    original_snapshot_sha256 CHAR(64) NOT NULL,
    source_charges_sha256 CHAR(64) NOT NULL,
    source_kind VARCHAR(64) NOT NULL,
    batch_preview_sha256 CHAR(64) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    rule_version VARCHAR(64) NOT NULL,
    snapshot_json TEXT,
    reverses_id BIGINT REFERENCES uber_eats_financial_corrections(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((action = 'APPLY' AND snapshot_json IS NOT NULL AND reverses_id IS NULL)
        OR (action = 'REVOKE' AND snapshot_json IS NULL AND reverses_id IS NOT NULL))
);
CREATE INDEX idx_uber_financial_corrections_scope
    ON uber_eats_financial_corrections(environment, store_id, uber_eats_order_id, id DESC);
