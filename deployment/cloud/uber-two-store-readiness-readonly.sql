-- Read-only two-store readiness inventory. No credentials, endpoint addresses,
-- customer fields, request snapshots, device identifiers or token hashes selected.
-- Local identity only: approved Sandbox root semantics do not prove Production Uber IDs.
-- Required psql variable is the independently confirmed backend JVM ZoneId.
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

WITH expected(store_id, store_code, organization_id) AS (
    VALUES (1::bigint, '4483_R_SAINT_DENIS', 1::bigint), (3::bigint, 'ST_CATHERINE', 1::bigint)
)
SELECT e.store_id, e.store_code AS expected_code,
       s.code AS actual_code, s.organization_id, s.printing_enabled, s.printing_mode,
       s.code=e.store_code AND s.organization_id=e.organization_id AS exact_store_identity
FROM expected e LEFT JOIN stores s ON s.id=e.store_id ORDER BY e.store_id;

-- Root numbers reference the approved 41-root semantic table; no external IDs copied.
WITH approved(root_no, sku, mapping_mode) AS (
VALUES
    (1, 'traditional_beef_noodle', 'COMBO_ROOT'),
    (2, 'braised_beef_tendon_noodle', 'STANDARD'),
    (3, 'chicken_chow_mein', 'STANDARD'),
    (4, 'chicken_chow_mein', 'COMBO_ROOT'),
    (5, 'chinese_herbal_tea', 'STANDARD'),
    (6, 'coke', 'STANDARD'),
    (7, 'tempura_shrimp', 'STANDARD'),
    (8, 'cucumber_salad', 'STANDARD'),
    (9, 'dan_dan_noodle', 'COMBO_ROOT'),
    (10, 'dan_dan_noodle', 'STANDARD'),
    (11, 'diet_coke', 'STANDARD'),
    (12, 'edamame', 'STANDARD'),
    (13, 'fried_steamed_buns', 'STANDARD'),
    (14, 'fried_wontons', 'STANDARD'),
    (15, 'canada_dry', 'STANDARD'),
    (16, 'shochu_fruit', 'STANDARD'),
    (17, 'lg_sake', 'STANDARD'),
    (18, 'braised_beef_shank_salad', 'STANDARD'),
    (19, 'ice_tea', 'STANDARD'),
    (20, 'beef_chow_mein', 'STANDARD'),
    (21, 'beef_chow_mein', 'COMBO_ROOT'),
    (22, 'shochu_fruit', 'STANDARD'),
    (23, 'shochu_fruit', 'STANDARD'),
    (24, 'vegetable_noodle', 'STANDARD'),
    (25, 'vegetable_noodle', 'COMBO_ROOT'),
    (26, 'soju', 'STANDARD'),
    (27, 'shochu_fruit', 'STANDARD'),
    (28, 'fried_spring_rolls', 'STANDARD'),
    (29, 'sapporo', 'STANDARD'),
    (30, 'sm_sake', 'STANDARD'),
    (31, 'seven_up', 'STANDARD'),
    (32, 'tomato_chow_mein', 'STANDARD'),
    (33, 'tomato_chow_mein', 'COMBO_ROOT'),
    (34, 'shredded_potato', 'STANDARD'),
    (35, 'tea_egg', 'STANDARD'),
    (36, 'traditional_beef_noodle', 'STANDARD'),
    (37, 'tsingtao_beer', 'STANDARD'),
    (38, 'vegetable_chow_mein', 'STANDARD'),
    (39, 'vegetable_chow_mein', 'COMBO_ROOT'),
    (40, 'zha_jiang_noodle', 'STANDARD'),
    (41, 'zha_jiang_noodle', 'COMBO_ROOT')
), targets(store_id) AS (VALUES (1::bigint), (3::bigint))
SELECT t.store_id, a.root_no, a.sku, a.mapping_mode,
       count(i.id) AS exact_sku_matches,
       count(i.id) FILTER (WHERE i.is_active AND NOT coalesce(i.is_sold_out,false))
           AS active_available_matches,
       count(i.id) FILTER (WHERE i.is_active AND station.id IS NOT NULL
           AND station.store_id=i.store_id AND station.is_active
           AND category.id IS NOT NULL AND category.store_id=i.store_id AND category.is_active)
           AS active_same_store_route_matches,
       CASE WHEN a.mapping_mode='COMBO_ROOT' THEN
           (SELECT count(*) FROM menu_items mi JOIN menu_item_options o ON o.menu_item_id=mi.id
            WHERE mi.store_id=t.store_id AND mi.sku=a.sku AND mi.is_active
              AND o.option_code='combo' AND o.option_group='COMBO' AND o.is_active)
       END AS active_combo_trigger_matches
FROM approved a CROSS JOIN targets t
LEFT JOIN menu_items i ON i.store_id=t.store_id AND i.sku=a.sku
LEFT JOIN stations station ON station.id=i.station_id
LEFT JOIN menu_categories category ON category.id=i.category_id
GROUP BY t.store_id,a.root_no,a.sku,a.mapping_mode ORDER BY t.store_id,a.root_no;

-- Historical duplicate SKU rows are visible; active duplicates are a separate gate.
SELECT store_id, sku, count(*) AS all_rows,
       count(*) FILTER (WHERE is_active) AS active_rows
FROM menu_items WHERE store_id IN (1,3) AND coalesce(sku,'')<>''
GROUP BY store_id,sku HAVING count(*)>1 ORDER BY store_id,sku;

SELECT i.store_id, station.code AS station_code, station.station_type,
       count(*) AS active_items,
       count(*) FILTER (WHERE station.id IS NULL OR station.store_id<>i.store_id
           OR NOT coalesce(station.is_active,false)) AS invalid_station_routes
FROM menu_items i LEFT JOIN stations station ON station.id=i.station_id
WHERE i.store_id IN (1,3) AND i.is_active
GROUP BY i.store_id,station.code,station.station_type ORDER BY i.store_id,station.code;

SELECT a.store_id, count(*) AS canonical_addons,
       count(*) FILTER (WHERE a.active) AS active_addons,
       count(*) FILTER (WHERE d.code='green_onion' AND a.active AND a.price=0)
           AS active_free_green_onion,
       count(*) FILTER (WHERE d.id IS NULL OR a.organization_id<>s.organization_id)
           AS invalid_canonical_scope
FROM store_addons a JOIN stores s ON s.id=a.store_id
LEFT JOIN organization_addon_definitions d
  ON d.id=a.organization_addon_definition_id AND d.organization_id=a.organization_id
WHERE a.store_id IN (1,3) GROUP BY a.store_id ORDER BY a.store_id;

SELECT i.store_id,
       count(*) FILTER (WHERE o.store_addon_id IS NOT NULL) AS linked_options,
       count(*) FILTER (WHERE o.store_addon_id IS NOT NULL AND
           (a.id IS NULL OR a.store_id<>i.store_id
            OR o.store_addon_store_id IS DISTINCT FROM i.store_id
            OR a.organization_id<>s.organization_id OR d.id IS NULL
            OR o.option_code IS DISTINCT FROM d.code
            OR o.option_group IS DISTINCT FROM 'ADD_ON'
            OR o.option_type IS DISTINCT FROM 'addon' OR o.addon_eligible IS NULL))
           AS invalid_link_or_code,
       count(*) FILTER (WHERE o.is_active AND o.option_group='ADD_ON'
           AND o.store_addon_id IS NULL) AS active_unlinked_addons
FROM menu_items i JOIN stores s ON s.id=i.store_id
JOIN menu_item_options o ON o.menu_item_id=i.id
LEFT JOIN store_addons a ON a.id=o.store_addon_id
LEFT JOIN organization_addon_definitions d
  ON d.id=a.organization_addon_definition_id AND d.organization_id=a.organization_id
WHERE i.store_id IN (1,3) GROUP BY i.store_id ORDER BY i.store_id;

SELECT g.store_id,g.group_code,g.enabled,g.required,g.selection_rule,g.default_component_code,
       count(c.id) FILTER (WHERE c.enabled AND c.archived_at IS NULL) AS enabled_components,
       count(c.id) FILTER (WHERE c.enabled AND c.archived_at IS NULL
           AND c.component_code=g.default_component_code) AS enabled_default_matches,
       count(c.id) FILTER (WHERE c.enabled AND c.archived_at IS NULL AND
           (c.store_id<>g.store_id OR c.component_group<>g.group_code
            OR c.linked_menu_item_id IS NOT NULL AND
                (i.id IS NULL OR i.store_id<>c.store_id OR NOT coalesce(i.is_active,false))))
           AS invalid_component_links
FROM store_combo_groups g LEFT JOIN store_combo_components c ON c.group_id=g.id
LEFT JOIN menu_items i ON i.id=c.linked_menu_item_id
WHERE g.store_id IN (1,3) AND g.archived_at IS NULL
GROUP BY g.store_id,g.group_code,g.enabled,g.required,g.selection_rule,g.default_component_code
ORDER BY g.store_id,g.group_code;

SELECT store_id,module_key,enabled FROM store_modules
WHERE store_id IN (1,2,3) AND module_key IN ('UBER_EATS','PRINTING','ORDERING_POS','MENU')
ORDER BY store_id,module_key;

SELECT environment,store_id,organization_id,processing_mode,enabled,
       (SELECT count(*) FROM uber_eats_menu_mappings mm WHERE mm.store_mapping_id=b.id) AS mapping_rules
FROM uber_eats_store_mappings b ORDER BY environment,store_id;

SELECT s.id AS store_id,a.module_code,a.enabled AS assignment_enabled,p.id AS printer_id,
       p.enabled AS printer_enabled,p.store_id=s.id AS same_store
FROM stores s LEFT JOIN printer_assignments a ON a.store_id=s.id
LEFT JOIN printer_configs p ON p.id=a.printer_id
WHERE s.id IN (1,3) ORDER BY s.id,a.module_code,p.id;

SELECT s.id AS store_id,count(d.id) AS devices,
       count(d.id) FILTER (WHERE d.is_active) AS active_devices,
       count(d.id) FILTER (WHERE d.is_active AND d.last_seen_at IS NOT NULL
           AND d.last_seen_at AT TIME ZONE :'uber_event_timezone' >= current_timestamp-interval '5 minutes')
           AS active_seen_within_5min,
       count(d.id) FILTER (WHERE d.is_active AND d.last_heartbeat_at IS NOT NULL
           AND d.last_heartbeat_at AT TIME ZONE :'uber_event_timezone' >= current_timestamp-interval '5 minutes')
           AS active_heartbeat_within_5min,
       age(current_timestamp,max(d.last_seen_at) AT TIME ZONE :'uber_event_timezone') AS latest_seen_age
FROM stores s LEFT JOIN store_devices d ON d.store_id=s.id
WHERE s.id IN (1,3) GROUP BY s.id ORDER BY s.id;

SELECT e.environment,m.store_id,e.status,e.event_type,count(*) AS event_count,
       age(current_timestamp,min(e.created_at) AT TIME ZONE :'uber_event_timezone') AS oldest_receipt_age
FROM uber_eats_events e LEFT JOIN uber_eats_store_mappings m
  ON m.environment=e.environment AND m.uber_store_id=e.uber_store_id
GROUP BY e.environment,m.store_id,e.status,e.event_type
ORDER BY e.environment,m.store_id,e.status,e.event_type;

SELECT environment,store_id,status,count(*) AS integration_orders,
       count(*) FILTER (WHERE local_order_id IS NOT NULL) AS already_imported,
       age(current_timestamp,min(placed_at) AT TIME ZONE 'UTC') AS oldest_placed_age
FROM uber_eats_orders GROUP BY environment,store_id,status ORDER BY environment,store_id,status;

SELECT 'all_database_uber_rows' AS proof,
       (SELECT count(*) FROM uber_eats_store_mappings) AS bindings,
       (SELECT count(*) FROM uber_eats_menu_mappings) AS mapping_rules,
       (SELECT count(*) FROM uber_eats_events) AS events,
       (SELECT count(*) FROM uber_eats_orders) AS integration_orders,
       (SELECT count(*) FROM orders WHERE external_source='UBER_EATS') AS local_orders;

COMMIT;
