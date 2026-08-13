package com.restaurant.system.modules;

public record ModuleDefinition(
    String moduleKey,
    String displayName,
    String classification,
    String category,
    boolean core,
    boolean activeNormalStore,
    boolean activationBlocking,
    ModuleState defaultState
) {
}
