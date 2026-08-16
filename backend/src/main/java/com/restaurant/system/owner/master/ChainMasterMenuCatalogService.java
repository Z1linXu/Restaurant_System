package com.restaurant.system.owner.master;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import org.springframework.stereotype.Service;

@Service
public class ChainMasterMenuCatalogService {

    public static final String INITIAL_MASTER_MENU_KEY = "LANZHOU_CHAIN_MASTER_MENU";
    public static final String INITIAL_MASTER_MENU_VERSION = "v1";
    public static final String INITIAL_MASTER_MENU_FINGERPRINT =
        "ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c";

    private final ChainMasterMenuRepository masterMenuRepository;
    private final ChainMasterMenuVersionRepository masterMenuVersionRepository;

    public ChainMasterMenuCatalogService(
        ChainMasterMenuRepository masterMenuRepository,
        ChainMasterMenuVersionRepository masterMenuVersionRepository
    ) {
        this.masterMenuRepository = masterMenuRepository;
        this.masterMenuVersionRepository = masterMenuVersionRepository;
    }

    public ChainMasterMenuVersionEntity requirePublishedVersion(
        Long organizationId,
        String masterMenuKey,
        String versionKey,
        String expectedFingerprint
    ) {
        ChainMasterMenuEntity menu = masterMenuRepository.findByOrganizationAndKey(organizationId, masterMenuKey)
            .orElseThrow(() -> new BusinessException("Master Menu not found"));
        ChainMasterMenuVersionEntity version = masterMenuVersionRepository
            .findByMasterMenuAndVersionKey(menu.id, versionKey)
            .orElseThrow(() -> new BusinessException("Master Menu version not found"));
        if (!"PUBLISHED".equals(version.status)) {
            throw new BusinessException("Master Menu version is not published");
        }
        if (expectedFingerprint != null && !expectedFingerprint.equals(version.fingerprint_sha256)) {
            throw new BusinessException("Master Menu fingerprint mismatch");
        }
        assertStoredFingerprintMatches(version);
        return version;
    }

    public void assertStoredFingerprintMatches(ChainMasterMenuVersionEntity version) {
        String computed = computeFingerprint(version.content_json);
        if (!computed.equals(version.fingerprint_sha256)) {
            throw new BusinessException("Master Menu stored fingerprint mismatch");
        }
    }

    public String computeFingerprint(String contentJson) {
        return StoreProfileCanonicalJson.sha256Canonical(contentJson);
    }
}
