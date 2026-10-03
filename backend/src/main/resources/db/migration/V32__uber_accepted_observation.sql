-- Additive observation metadata; accepted_observed_at is NOT an Uber acceptance timestamp.
ALTER TABLE uber_eats_orders ADD COLUMN accepted_observed_at TIMESTAMP;
ALTER TABLE uber_eats_orders ADD COLUMN current_state VARCHAR(80);
ALTER TABLE uber_eats_orders ADD COLUMN state_observed_at TIMESTAMP;
ALTER TABLE uber_eats_orders ADD COLUMN acceptance_poll_started_at TIMESTAMP;
ALTER TABLE uber_eats_orders ADD COLUMN acceptance_poll_expires_at TIMESTAMP;
ALTER TABLE uber_eats_orders ADD COLUMN acceptance_poll_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE uber_eats_orders ADD CONSTRAINT ck_uber_poll_count CHECK (acceptance_poll_count >= 0);

-- Null means no scheduled work; terminal/mapping-review rows must not keep polling.
ALTER TABLE uber_eats_orders ALTER COLUMN next_attempt_at DROP NOT NULL;
