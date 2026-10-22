-- Read-only inventory, no snapshots/customer fields, credentials or order details.
-- Required psql variable: uber_event_timezone = actual backend JVM ZoneId.
-- Existing receipt/retry timestamps are JVM-local TIMESTAMP WITHOUT TIME ZONE;
-- order placed_at is UTC. Do not infer the JVM ZoneId from the DB session zone.
-- Example invocation appears in README_UBER_PRODUCTION_WEBHOOK.md.
\set ON_ERROR_STOP on
\if :{?uber_event_timezone}
\else
\echo 'Provide -v uber_event_timezone=<confirmed backend JVM ZoneId>'
\quit 3
\endif

BEGIN READ ONLY;

SELECT current_timestamp AS observed_at,
       current_setting('TimeZone') AS database_session_time_zone,
       :'uber_event_timezone'::text AS confirmed_backend_jvm_time_zone;

SELECT e.environment,
       m.store_id,
       e.uber_store_id,
       e.event_type,
       e.status,
       count(*) AS event_count,
       min(e.created_at) AS oldest_received_backend_local,
       max(e.created_at) AS newest_received_backend_local,
       age(current_timestamp, min(e.created_at) AT TIME ZONE :'uber_event_timezone')
           AS oldest_receipt_age,
       max(e.attempt_count) AS greatest_attempt_count
FROM uber_eats_events e
LEFT JOIN uber_eats_store_mappings m
  ON m.environment = e.environment AND m.uber_store_id = e.uber_store_id
GROUP BY e.environment, m.store_id, e.uber_store_id, e.event_type, e.status
ORDER BY e.environment, m.store_id NULLS LAST, e.uber_store_id, e.event_type, e.status;

SELECT environment,
       store_id,
       status,
       count(*) AS integration_order_count,
       count(*) FILTER (WHERE local_order_id IS NOT NULL) AS already_imported_count,
       count(*) FILTER (WHERE local_order_id IS NULL) AS not_imported_count,
       min(placed_at) AT TIME ZONE 'UTC' AS oldest_order_placed_at,
       age(current_timestamp, min(placed_at) AT TIME ZONE 'UTC') AS oldest_order_age,
       count(*) FILTER (WHERE local_order_id IS NULL AND placed_at IS NULL)
           AS not_imported_missing_order_time_count,
       count(*) FILTER (WHERE cancelled) AS cancelled_count,
       count(*) FILTER (WHERE edit_required) AS edit_review_count
FROM uber_eats_orders
GROUP BY environment, store_id, status
ORDER BY environment, store_id, status;

-- Safety signals remain relevant even when the original notification is held.
SELECT environment, uber_store_id, event_type, status, count(*) AS retained_safety_events
FROM uber_eats_events
WHERE event_type IN ('orders.cancel', 'orders.customer_order_edit')
  AND status <> 'COMPLETED'
GROUP BY environment, uber_store_id, event_type, status
ORDER BY environment, uber_store_id, event_type, status;

COMMIT;
