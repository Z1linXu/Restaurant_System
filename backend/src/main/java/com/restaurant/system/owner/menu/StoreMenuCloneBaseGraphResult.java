package com.restaurant.system.owner.menu;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Transaction-local source-to-target mapping that is never persisted or returned by the API.
 */
public record StoreMenuCloneBaseGraphResult(
    StoreMenuCloneSnapshot sourceSnapshot,
    Map<Long, Long> targetCategoryIdBySourceId,
    Map<Long, Long> targetStationIdBySourceId,
    Map<Long, Long> targetItemIdBySourceId,
    Map<String, Long> targetItemIdByTargetSku,
    Map<Long, Set<StoreMenuCloneBaseGraphProfile.ItemRole>> rolesByTargetItemId
) {

    public StoreMenuCloneBaseGraphResult {
        Objects.requireNonNull(sourceSnapshot, "sourceSnapshot");
        targetCategoryIdBySourceId = Map.copyOf(Objects.requireNonNull(
            targetCategoryIdBySourceId,
            "targetCategoryIdBySourceId"
        ));
        targetStationIdBySourceId = Map.copyOf(Objects.requireNonNull(
            targetStationIdBySourceId,
            "targetStationIdBySourceId"
        ));
        targetItemIdBySourceId = Map.copyOf(Objects.requireNonNull(
            targetItemIdBySourceId,
            "targetItemIdBySourceId"
        ));
        targetItemIdByTargetSku = Map.copyOf(Objects.requireNonNull(
            targetItemIdByTargetSku,
            "targetItemIdByTargetSku"
        ));
        Objects.requireNonNull(rolesByTargetItemId, "rolesByTargetItemId");
        rolesByTargetItemId = rolesByTargetItemId.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey,
            entry -> Set.copyOf(entry.getValue())
        ));
    }
}
