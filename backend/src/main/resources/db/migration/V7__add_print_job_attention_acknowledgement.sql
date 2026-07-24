ALTER TABLE print_jobs
    ADD COLUMN IF NOT EXISTS attention_acknowledged_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS attention_acknowledged_by BIGINT,
    ADD COLUMN IF NOT EXISTS attention_acknowledgement_note VARCHAR(500),
    ADD COLUMN IF NOT EXISTS attention_acknowledged_status VARCHAR(32),
    ADD COLUMN IF NOT EXISTS attention_acknowledged_retry_count INTEGER,
    ADD COLUMN IF NOT EXISTS attention_acknowledged_error_code VARCHAR(128);
