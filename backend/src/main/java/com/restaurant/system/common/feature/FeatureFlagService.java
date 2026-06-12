package com.restaurant.system.common.feature;

import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService {

    private final FeatureConfigProperties properties;

    public FeatureFlagService(FeatureConfigProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled(FeaturePackage featurePackage) {
        return switch (featurePackage) {
            case CORE_POS -> properties.isCorePos();
            case PRINTING -> properties.isPrinting();
            case KDS -> properties.isKds();
            case ADMIN -> properties.isAdmin();
            case ANALYTICS -> properties.isAnalytics();
            case PLATFORM -> properties.isPlatform();
            case DEVELOPER_TOOLS -> properties.isDeveloperTools();
        };
    }

    public void requireEnabled(FeaturePackage featurePackage) {
        if (!isEnabled(featurePackage)) {
            throw new FeatureDisabledException(featurePackage);
        }
    }
}
