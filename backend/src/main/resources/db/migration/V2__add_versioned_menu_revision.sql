ALTER TABLE stores
    ADD COLUMN menu_revision BIGINT NOT NULL DEFAULT 1;

ALTER TABLE stores
    ADD COLUMN menu_updated_at TIMESTAMP WITHOUT TIME ZONE;

UPDATE stores
SET menu_updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP)
WHERE menu_updated_at IS NULL;

ALTER TABLE stores
    ALTER COLUMN menu_updated_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE stores
    ALTER COLUMN menu_updated_at SET NOT NULL;

