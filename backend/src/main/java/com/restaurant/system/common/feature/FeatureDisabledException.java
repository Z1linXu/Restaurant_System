package com.restaurant.system.common.feature;

public class FeatureDisabledException extends RuntimeException {

    private final FeaturePackage featurePackage;

    public FeatureDisabledException(FeaturePackage featurePackage) {
        super("Feature Disabled: " + featurePackage.name());
        this.featurePackage = featurePackage;
    }

    public FeaturePackage getFeaturePackage() {
        return featurePackage;
    }
}
