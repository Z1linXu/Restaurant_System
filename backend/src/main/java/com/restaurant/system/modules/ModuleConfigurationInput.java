package com.restaurant.system.modules;

import java.util.Map;
import java.util.Set;

public record ModuleConfigurationInput(
    Map<String, ModuleState> moduleStates,
    Set<String> environmentCapabilities,
    Set<String> hardwareCapabilities
) {
    public ModuleConfigurationInput {
        moduleStates = moduleStates == null ? Map.of() : Map.copyOf(moduleStates);
        environmentCapabilities = environmentCapabilities == null ? Set.of() : Set.copyOf(environmentCapabilities);
        hardwareCapabilities = hardwareCapabilities == null ? Set.of() : Set.copyOf(hardwareCapabilities);
    }

    public static ModuleConfigurationInput defaultsWith(
        Set<String> environmentCapabilities,
        Set<String> hardwareCapabilities
    ) {
        return new ModuleConfigurationInput(Map.of(), environmentCapabilities, hardwareCapabilities);
    }
}
