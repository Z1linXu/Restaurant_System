package com.restaurant.system.modules;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public interface StoreModuleCapabilityProvider {

    Set<String> environmentCapabilities(Long storeId);

    Set<String> hardwareCapabilities(Long storeId);

    default List<StoreHardwareCapabilityReadiness> hardwareReadiness(Long storeId) {
        return hardwareCapabilities(storeId).stream()
            .sorted(Comparator.naturalOrder())
            .map(capability -> new StoreHardwareCapabilityReadiness(
                capability,
                HardwareReadinessState.CONFIGURED,
                true,
                "HARDWARE_CAPABILITY",
                "LEGACY_HARDWARE_CAPABILITY_SET",
                "Compatibility readiness synthesized from hardware_capabilities."
            ))
            .toList();
    }
}
