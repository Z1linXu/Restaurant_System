package com.restaurant.system.modules;

import java.util.Arrays;
import java.util.Optional;

public enum ModuleDependencyType {
    REQUIRES,
    CONFLICTS_WITH,
    REQUIRES_ENVIRONMENT_CAPABILITY,
    REQUIRES_HARDWARE_CAPABILITY;

    public static Optional<ModuleDependencyType> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(type -> type.name().equals(value))
            .findFirst();
    }
}
