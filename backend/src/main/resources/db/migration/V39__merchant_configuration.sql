CREATE TABLE public.store_merchant_configurations (
    store_id bigint PRIMARY KEY REFERENCES public.stores(id),
    schema_version integer NOT NULL CHECK (schema_version = 1),
    config_revision bigint NOT NULL CHECK (config_revision >= 1),
    brand_name varchar(255) NOT NULL CHECK (length(trim(brand_name)) > 0),
    locale varchar(64) NOT NULL,
    currency varchar(3) NOT NULL CHECK (currency = 'CAD'),
    tax_policy_reference varchar(96) NOT NULL CHECK (tax_policy_reference = 'ca-qc-tax-2026-01'),
    profile_code varchar(96) NOT NULL,
    profile_version varchar(64) NOT NULL,
    compatibility_mode varchar(32) NOT NULL,
    configuration_source varchar(64) NOT NULL,
    updated_by bigint REFERENCES public.users(id),
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT chk_merchant_runtime_profile CHECK (
        (profile_code = 'NEUTRAL_RESTAURANT' AND profile_version = 'v1' AND compatibility_mode = 'NEUTRAL') OR
        (profile_code = 'ST_DENIS_CANONICAL_PROFILE' AND profile_version = 'v2' AND compatibility_mode = 'PROVISIONAL_COMPATIBILITY')
    )
);

-- Snapshot only the rows present during this migration. Never infer a new Store's runtime
-- Profile from a missing configuration, name, organization ID or copied master-menu key.
-- LEGACY_EXISTING_STORE is the explicit pre-provisioner installation marker introduced
-- by the Store lifecycle migration; it preserves the old installation's compatibility.
WITH sources AS (
    SELECT s.*, CASE
        WHEN s.provisioned_profile_code = 'ST_DENIS_CANONICAL_PROFILE' AND s.provisioned_profile_version = 'v2'
            AND EXISTS (SELECT 1 FROM public.store_profiles p JOIN public.store_profile_versions v ON v.profile_id = p.id
                WHERE p.profile_code = s.provisioned_profile_code AND v.profile_version = s.provisioned_profile_version
                  AND v.fingerprint_sha256 = s.provisioned_profile_fingerprint_sha256) THEN 'VERIFIED_PROFILE_BACKFILL'
        WHEN s.provisioning_source = 'LEGACY_EXISTING_STORE' THEN 'LEGACY_INSTALLATION_BACKFILL'
        ELSE 'NEUTRAL_EXISTING_BACKFILL' END AS config_source
    FROM public.stores s
)
INSERT INTO public.store_merchant_configurations
    (store_id, schema_version, config_revision, brand_name, locale, currency, tax_policy_reference,
     profile_code, profile_version, compatibility_mode, configuration_source, created_at, updated_at)
SELECT id, 1, 1, coalesce(nullif(trim(name), ''), nullif(trim(code), ''), 'Restaurant'), 'en-CA', 'CAD', 'ca-qc-tax-2026-01',
    CASE WHEN config_source = 'NEUTRAL_EXISTING_BACKFILL' THEN 'NEUTRAL_RESTAURANT' ELSE 'ST_DENIS_CANONICAL_PROFILE' END,
    CASE WHEN config_source = 'NEUTRAL_EXISTING_BACKFILL' THEN 'v1' ELSE 'v2' END,
    CASE WHEN config_source = 'NEUTRAL_EXISTING_BACKFILL' THEN 'NEUTRAL' ELSE 'PROVISIONAL_COMPATIBILITY' END,
    config_source, current_timestamp, current_timestamp
FROM sources;
