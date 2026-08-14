package com.restaurant.system.modules;

public record StoreHardwareCapabilityReadiness(
    String capabilityKey,
    HardwareReadinessState readinessState,
    boolean requiredByCurrentRuntime,
    boolean dependencySatisfied,
    String layer,
    String source,
    String note
) {
    public StoreHardwareCapabilityReadiness(
        String capabilityKey,
        HardwareReadinessState readinessState,
        boolean requiredByCurrentRuntime,
        String layer,
        String source,
        String note
    ) {
        this(
            capabilityKey,
            readinessState,
            requiredByCurrentRuntime,
            readinessState != null && readinessState.dependencySatisfied(),
            layer,
            source,
            note
        );
    }
}
