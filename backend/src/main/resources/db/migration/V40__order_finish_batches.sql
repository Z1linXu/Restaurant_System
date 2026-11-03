-- Durable results for confirmed table Finish. Existing order/printing tables are unchanged.
CREATE TABLE order_finish_batches (
    id VARCHAR(36) PRIMARY KEY,
    store_id BIGINT NOT NULL REFERENCES stores(id),
    actor_user_id BIGINT NOT NULL REFERENCES users(id),
    plan_json TEXT NOT NULL,
    results_json TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);
