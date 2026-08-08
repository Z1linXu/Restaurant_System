package com.restaurant.system.owner.profile;

import java.util.List;
import java.util.Set;

public record StoreProfileComposition(
    List<StoreProfileModuleReference> modules,
    Set<StoreProvisioningModuleCode> activationRequirements
) {

    public StoreProfileComposition {
        modules = modules == null ? List.of() : List.copyOf(modules);
        activationRequirements = activationRequirements == null
            ? Set.of()
            : Set.copyOf(activationRequirements);
    }
}
