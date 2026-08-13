package com.restaurant.system.modules;

import java.util.Map;
import java.util.Set;

public record ModuleCatalogDefinition(
    Set<String> moduleKeys,
    Set<String> coreModuleKeys,
    Map<String, ModuleState> defaultStates,
    Set<String> environmentCapabilities,
    Set<String> hardwareCapabilities
) {
    public boolean hasModule(String moduleKey) {
        return moduleKeys.contains(moduleKey);
    }

    public ModuleState defaultState(String moduleKey) {
        return defaultStates.getOrDefault(moduleKey, ModuleState.DISABLED);
    }
}
