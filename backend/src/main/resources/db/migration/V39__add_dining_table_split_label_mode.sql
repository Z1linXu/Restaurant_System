-- Additive default preserves all existing table identities, configuration and orders.
SET LOCAL lock_timeout = '5s';
ALTER TABLE public.dining_tables
    ADD COLUMN split_label_mode varchar(16) NOT NULL DEFAULT 'LEFT_RIGHT';
ALTER TABLE public.dining_tables
    ADD CONSTRAINT chk_dining_tables_split_label_mode
    CHECK (split_label_mode IN ('LEFT_RIGHT', 'INSIDE_OUTSIDE'));
