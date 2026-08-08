package com.restaurant.system.owner.profile;

import java.util.List;

public record StoreProfileSummary(
    String profileCode,
    String profileVersion,
    String profileFingerprint,
    List<StoreProvisioningModuleCode> modules
) {

    public StoreProfileSummary {
        modules = List.copyOf(modules);
    }
}
