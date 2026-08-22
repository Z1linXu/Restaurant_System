-- V25__widen_phase_b_device_contract_version
-- Additive compatibility repair: preserve existing readiness values while
-- allowing the current explicit Part 2 contract identifier to be persisted.

ALTER TABLE store_device_readiness
    ALTER COLUMN contract_version TYPE VARCHAR(64);
