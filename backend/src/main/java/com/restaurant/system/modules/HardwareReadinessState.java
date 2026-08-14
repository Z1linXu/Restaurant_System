package com.restaurant.system.modules;

public enum HardwareReadinessState {
    NOT_REQUIRED(true),
    UNCONFIGURED(false),
    CONFIGURED(true),
    VERIFIED(true);

    private final boolean dependencySatisfied;

    HardwareReadinessState(boolean dependencySatisfied) {
        this.dependencySatisfied = dependencySatisfied;
    }

    public boolean dependencySatisfied() {
        return dependencySatisfied;
    }
}
