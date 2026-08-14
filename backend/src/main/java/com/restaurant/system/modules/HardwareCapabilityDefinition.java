package com.restaurant.system.modules;

import java.util.List;

public record HardwareCapabilityDefinition(
    String capabilityKey,
    String displayName,
    String layer,
    String category,
    String readinessContract,
    boolean physicalBinding,
    List<String> aliases
) {
    public HardwareCapabilityDefinition {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
