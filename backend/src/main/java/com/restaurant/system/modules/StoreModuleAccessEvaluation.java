package com.restaurant.system.modules;

import java.util.List;

public record StoreModuleAccessEvaluation(
    Long storeId,
    String moduleKey,
    boolean moduleKnown,
    boolean persisted,
    boolean storeModuleEnabled,
    boolean environmentAvailable,
    boolean hardwareAvailable,
    boolean allowed,
    String errorCode,
    String message,
    List<String> missingEnvironmentCapabilities,
    List<String> missingHardwareCapabilities,
    List<String> issueCodes
) {

    public void requireAllowed() {
        if (!allowed) {
            throw new ModuleAccessException(errorCode, moduleKey, message);
        }
    }

    public void requireModuleEnabled() {
        if (!moduleKnown || !persisted || !storeModuleEnabled) {
            throw new ModuleAccessException(errorCode, moduleKey, message);
        }
    }
}
