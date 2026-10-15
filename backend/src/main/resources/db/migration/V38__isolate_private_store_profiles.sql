ALTER TABLE public.store_profiles
    ADD COLUMN visibility varchar(32) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN owner_organization_id bigint REFERENCES public.organizations(id),
    ADD CONSTRAINT chk_store_profiles_visibility CHECK (
        visibility = 'PRIVATE' OR (visibility = 'PUBLIC_PRESET' AND owner_organization_id IS NULL)
    );

-- Only a verified exact Profile/version/fingerprint reference proves provenance.
-- V19 copied the same master key into every Organization: that key proves no owner.
-- Ambiguous and unreferenced legacy profiles remain PRIVATE and unowned (deny).
WITH verified_owners AS (
    SELECT p.id, min(s.organization_id) AS organization_id
    FROM public.store_profiles p
    JOIN public.store_profile_versions v ON v.profile_id = p.id
    JOIN public.stores s ON s.provisioned_profile_code = p.profile_code
        AND s.provisioned_profile_version = v.profile_version
        AND s.provisioned_profile_fingerprint_sha256 = v.fingerprint_sha256
    JOIN public.organizations o ON o.id = s.organization_id
    GROUP BY p.id
    HAVING count(DISTINCT s.organization_id) = 1
)
UPDATE public.store_profiles p SET owner_organization_id = v.organization_id
FROM verified_owners v WHERE v.id = p.id;

CREATE INDEX idx_store_profiles_owner_visibility
    ON public.store_profiles(owner_organization_id, visibility);
