package com.restaurant.system.owner.provisioning;

import com.restaurant.system.menu.addon.StoreAddonService;
import java.util.List;

public record OwnerStoreProvisioningResult(
    Long requestId,
    Long storeId,
    String status,
    boolean replayed,
    String validationStatus,
    String resultCode,
    String errorCode,
    OwnerStoreProvisioningCounts counts,
    List<StoreAddonService.Conflict> addonConflicts
) {
    public OwnerStoreProvisioningResult(
        Long requestId,
        Long storeId,
        String status,
        boolean replayed,
        String validationStatus,
        String resultCode,
        String errorCode,
        OwnerStoreProvisioningCounts counts
    ) {
        this(requestId, storeId, status, replayed, validationStatus, resultCode, errorCode, counts, List.of());
    }

    public OwnerStoreProvisioningResult {
        addonConflicts = List.copyOf(addonConflicts);
    }
}
