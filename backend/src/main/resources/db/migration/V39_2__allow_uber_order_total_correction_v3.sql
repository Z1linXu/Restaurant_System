SET LOCAL lock_timeout = '1s';
SET LOCAL statement_timeout = '3s';

-- Preserve existing v2 events. Only permit the new append-only snapshot version.
ALTER TABLE uber_eats_financial_corrections
    DROP CONSTRAINT uber_eats_financial_corrections_snapshot_version_check;
ALTER TABLE uber_eats_financial_corrections
    ADD CONSTRAINT uber_eats_financial_corrections_snapshot_version_check CHECK (snapshot_version IN (2,3));
