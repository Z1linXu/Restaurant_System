-- Uber Eats inbox and durable event/decision recovery; no production credentials or Store binding.
CREATE TABLE uber_eats_store_mappings (
    id BIGSERIAL PRIMARY KEY,
    environment VARCHAR(255) NOT NULL,
    uber_store_id VARCHAR(255) NOT NULL,
    store_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_uber_store UNIQUE(environment, uber_store_id),
    CONSTRAINT uq_uber_local_store UNIQUE(environment, store_id),
    FOREIGN KEY (store_id) REFERENCES stores(id),
    FOREIGN KEY (organization_id) REFERENCES organizations(id)
);

CREATE TABLE uber_eats_menu_mappings (
    id BIGSERIAL PRIMARY KEY,
    store_mapping_id BIGINT NOT NULL,
    kind VARCHAR(255) NOT NULL,
    identifier_type VARCHAR(255) NOT NULL,
    uber_identifier VARCHAR(255) NOT NULL,
    uber_item_id VARCHAR(255) NOT NULL,
    local_menu_item_id BIGINT,
    local_option_code VARCHAR(255),
    local_option_group VARCHAR(255),
    parent_option_code VARCHAR(255),
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_uber_menu_key UNIQUE(store_mapping_id,kind,identifier_type,uber_item_id,uber_identifier),
    FOREIGN KEY (store_mapping_id) REFERENCES uber_eats_store_mappings(id),
    FOREIGN KEY (local_menu_item_id) REFERENCES menu_items(id)
);

CREATE TABLE uber_eats_events (
    id BIGSERIAL PRIMARY KEY,
    environment VARCHAR(255) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    uber_store_id VARCHAR(255) NOT NULL,
    uber_order_id VARCHAR(255) NOT NULL,
    body_hash VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    error_code VARCHAR(255),
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_uber_event UNIQUE(environment,event_id)
);

CREATE TABLE uber_eats_orders (
    id BIGSERIAL PRIMARY KEY,
    environment VARCHAR(255) NOT NULL,
    store_mapping_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    uber_store_id VARCHAR(255) NOT NULL,
    uber_order_id VARCHAR(255) NOT NULL,
    uber_event_id VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    display_id VARCHAR(255),
    fulfillment_type VARCHAR(255),
    mapping_status VARCHAR(255),
    mapping_error TEXT,
    raw_order_snapshot_json TEXT,
    local_request_json TEXT,
    last_error VARCHAR(255),
    deny_reason VARCHAR(255),
    accepted_by BIGINT,
    local_order_id BIGINT,
    attempt_count INTEGER NOT NULL,
    cancelled BOOLEAN NOT NULL,
    edit_required BOOLEAN NOT NULL,
    scheduled BOOLEAN NOT NULL,
    placed_at TIMESTAMP,
    scheduled_at TIMESTAMP,
    accepted_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    next_attempt_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_uber_order UNIQUE(environment,uber_order_id),
    CONSTRAINT uq_uber_local_order UNIQUE(local_order_id),
    FOREIGN KEY (store_mapping_id) REFERENCES uber_eats_store_mappings(id),
    FOREIGN KEY (store_id) REFERENCES stores(id),
    FOREIGN KEY (local_order_id) REFERENCES orders(id)
);

CREATE INDEX idx_uber_event_due ON uber_eats_events(environment,status,next_attempt_at);
CREATE INDEX idx_uber_order_due ON uber_eats_orders(environment,status,next_attempt_at);
CREATE INDEX idx_uber_inbox ON uber_eats_orders(environment,store_id,id);
ALTER TABLE orders ADD COLUMN external_source VARCHAR(40);
ALTER TABLE orders ADD COLUMN external_order_id VARCHAR(255);
ALTER TABLE orders ADD COLUMN external_display_id VARCHAR(255);
CREATE UNIQUE INDEX uq_order_external_source ON orders(store_id,external_source,external_order_id);
