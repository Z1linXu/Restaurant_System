-- A Store-local daily Finish uses the normal order completion domain; no payment facts.
ALTER TABLE stores ADD COLUMN timezone varchar(64);

CREATE TABLE store_daily_close_runs (
    store_id bigint NOT NULL REFERENCES stores(id),
    business_date date NOT NULL,
    timezone varchar(64) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    finished_order_count integer NOT NULL DEFAULT 0 CHECK (finished_order_count >= 0),
    PRIMARY KEY (store_id, business_date)
);
