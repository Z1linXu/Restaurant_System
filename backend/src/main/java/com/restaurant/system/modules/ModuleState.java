package com.restaurant.system.modules;

public enum ModuleState {
    ENABLED,
    DISABLED;

    public static ModuleState fromDefaultState(String value) {
        if ("ENABLED".equalsIgnoreCase(value)) {
            return ENABLED;
        }
        return DISABLED;
    }
}
