-- Execution context only: no historical order, instruction or receipt rewrite.
ALTER TABLE order_dispatch_outbox ADD COLUMN originating_device_id bigint REFERENCES store_devices(id);
ALTER TABLE print_jobs ADD COLUMN preferred_device_id bigint REFERENCES store_devices(id);
ALTER TABLE print_jobs ADD COLUMN preferred_device_until timestamp(6) without time zone;
ALTER TABLE print_jobs ADD COLUMN reprint_source_job_id bigint REFERENCES print_jobs(id);
ALTER TABLE print_jobs ADD COLUMN manual_request_hash varchar(64);
ALTER TABLE print_jobs ADD COLUMN printing_started_at timestamp(6) without time zone;
CREATE INDEX idx_print_jobs_active_order_module ON print_jobs(store_id, order_id, module_code, id)
    WHERE status IN ('PENDING', 'CLAIMED', 'PRINTING');
