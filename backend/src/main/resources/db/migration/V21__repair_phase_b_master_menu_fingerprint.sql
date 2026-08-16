-- V21__repair_phase_b_master_menu_fingerprint
-- Phase B Part 1 runtime repair: V19 stored the initial Chain Master Menu
-- fingerprint as a reviewed design constant, while runtime validation
-- recomputes it from canonical content_json. This migration aligns the stored
-- Master fingerprint and the Phase B-ready Store Profile v2 reference without
-- rewriting Flyway history.

DO $$
DECLARE
    old_master_fingerprint CONSTANT text := 'e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7';
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.stores
        WHERE provisioned_master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
          AND provisioned_master_menu_version = 'v1'
          AND provisioned_master_menu_fingerprint_sha256 = old_master_fingerprint
    ) THEN
        RAISE EXCEPTION 'PHASE_B_MASTER_FINGERPRINT_REPAIR_BLOCKED_BY_PROVISIONED_STORE';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.owner_store_provisioning_requests
        WHERE master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
          AND master_menu_version = 'v1'
          AND master_menu_fingerprint_sha256 = old_master_fingerprint
          AND status = 'COMPLETED'
    ) THEN
        RAISE EXCEPTION 'PHASE_B_MASTER_FINGERPRINT_REPAIR_BLOCKED_BY_COMPLETED_REQUEST';
    END IF;
END $$;

ALTER TABLE public.chain_master_menu_versions
    DISABLE TRIGGER trg_chain_master_menu_versions_immutable;

UPDATE public.chain_master_menu_versions version
SET fingerprint_sha256 = 'ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c',
    updated_at = CURRENT_TIMESTAMP
FROM public.chain_master_menus master_menu
WHERE version.master_menu_id = master_menu.id
  AND master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
  AND version.version_key = 'v1'
  AND version.status = 'PUBLISHED'
  AND version.fingerprint_sha256 = 'e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7';

ALTER TABLE public.chain_master_menu_versions
    ENABLE TRIGGER trg_chain_master_menu_versions_immutable;

ALTER TABLE public.store_profile_versions
    DISABLE TRIGGER trg_store_profile_versions_immutable;

UPDATE public.store_profile_versions version
SET content_json = jsonb_set(
        version.content_json::jsonb,
        '{master_menu_reference,fingerprint_sha256}',
        to_jsonb('ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c'::text),
        false
    )::text,
    fingerprint_sha256 = '51ddf408755ef476ac99abd9ab7498f48995431c5d5a52d98a77704ab71b23ae',
    updated_at = CURRENT_TIMESTAMP
FROM public.store_profiles profile
WHERE version.profile_id = profile.id
  AND profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
  AND version.profile_version = 'v2'
  AND version.status = 'READY'
  AND version.fingerprint_sha256 = '2083269d602cf068b78551ee5d53916442dc262a77c4fa5eaef8eae5dc1267c2'
  AND version.content_json::jsonb #>> '{master_menu_reference,fingerprint_sha256}' =
      'e55ad23773753ade22e3e090622c361b58268ae5b43ee354ec7eef5b78f233f7';

ALTER TABLE public.store_profile_versions
    ENABLE TRIGGER trg_store_profile_versions_immutable;

DO $$
DECLARE
    new_master_fingerprint CONSTANT text := 'ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c';
    new_profile_fingerprint CONSTANT text := '51ddf408755ef476ac99abd9ab7498f48995431c5d5a52d98a77704ab71b23ae';
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.chain_master_menu_versions version
        JOIN public.chain_master_menus master_menu
            ON master_menu.id = version.master_menu_id
        WHERE master_menu.master_menu_key = 'LANZHOU_CHAIN_MASTER_MENU'
          AND version.version_key = 'v1'
          AND version.status = 'PUBLISHED'
          AND version.fingerprint_sha256 <> new_master_fingerprint
    ) THEN
        RAISE EXCEPTION 'PHASE_B_MASTER_FINGERPRINT_REPAIR_INCOMPLETE';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.store_profile_versions version
        JOIN public.store_profiles profile
            ON profile.id = version.profile_id
        WHERE profile.profile_code = 'ST_DENIS_CANONICAL_PROFILE'
          AND version.profile_version = 'v2'
          AND (
              version.fingerprint_sha256 <> new_profile_fingerprint
              OR version.content_json::jsonb #>> '{master_menu_reference,fingerprint_sha256}'
                  <> new_master_fingerprint
          )
    ) THEN
        RAISE EXCEPTION 'PHASE_B_PROFILE_V2_FINGERPRINT_REPAIR_INCOMPLETE';
    END IF;
END $$;
