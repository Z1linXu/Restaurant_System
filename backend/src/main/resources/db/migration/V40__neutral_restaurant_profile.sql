-- An empty public preset only. Never copy a private Profile or Master Menu graph.
INSERT INTO public.store_profiles (profile_code, display_name, description, status, provenance,
    visibility, owner_organization_id, created_at, updated_at)
VALUES ('NEUTRAL_RESTAURANT', 'Neutral restaurant', 'Empty restaurant configuration', 'PUBLISHED',
    'COMMERCIAL_NEUTRAL_PRESET_V1', 'PUBLIC_PRESET', NULL, current_timestamp, current_timestamp);
INSERT INTO public.store_profile_versions (profile_id, profile_version, status, schema_version,
    content_json, fingerprint_sha256, source_reference, created_at, updated_at, published_at)
SELECT id, 'v1', 'PUBLISHED', 'STORE_PROFILE_CONTRACT_V1', '{"menu_template":{"categories":[]},"module_defaults":{"modules":[{"enabled":true,"module_key":"ORDERING_POS"},{"enabled":true,"module_key":"MENU"},{"enabled":true,"module_key":"MENU_MANAGEMENT"},{"enabled":true,"module_key":"TABLE_MANAGEMENT"},{"enabled":true,"module_key":"PRINTING"},{"enabled":true,"module_key":"ORDER_HISTORY"},{"enabled":true,"module_key":"REPORTING_CORE"},{"enabled":true,"module_key":"STAFF_ACCESS"},{"enabled":true,"module_key":"STORE_ADMINISTRATION"},{"enabled":false,"module_key":"KDS"},{"enabled":false,"module_key":"ANALYTICS_ADVANCED"},{"enabled":false,"module_key":"UBER_EATS"}]},"profile_code":"NEUTRAL_RESTAURANT","profile_version":"v1","schema_version":"STORE_PROFILE_CONTRACT_V1"}', '3e696169e5609a9c8b7708bf9e5bb77a995a6539fc15155665f60f17095648ba',
    'COMMERCIAL_NEUTRAL_PRESET_V1', current_timestamp, current_timestamp, current_timestamp
FROM public.store_profiles WHERE profile_code = 'NEUTRAL_RESTAURANT';

-- Neutral creation has no Master Menu. Keep the existing ledger/idempotency protocol and
-- require all three absent only for this exact preset; legacy requests still require all.
ALTER TABLE public.owner_store_provisioning_requests
    ALTER COLUMN master_menu_key DROP NOT NULL,
    ALTER COLUMN master_menu_version DROP NOT NULL,
    ALTER COLUMN master_menu_fingerprint_sha256 DROP NOT NULL;
ALTER TABLE public.owner_store_provisioning_requests ADD CONSTRAINT chk_provisioning_optional_master
    CHECK ((profile_code = 'NEUTRAL_RESTAURANT' AND profile_version = 'v1'
        AND master_menu_key IS NULL AND master_menu_version IS NULL AND master_menu_fingerprint_sha256 IS NULL)
        OR (profile_code <> 'NEUTRAL_RESTAURANT' AND master_menu_key IS NOT NULL
            AND master_menu_version IS NOT NULL AND master_menu_fingerprint_sha256 IS NOT NULL));
