package com.restaurant.system.owner.menu;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Internal target graph contract supplied by a reviewed, versioned Store profile.
 */
public interface StoreMenuCloneBaseGraphProfile extends StoreMenuCloneProfileDescriptor {

    List<CategorySelection> categories();

    List<StationSelection> stations();

    List<ItemSelection> items();

    record CategorySelection(
        CategorySourcePolicy sourcePolicy,
        String sourceCode,
        String targetCode,
        String targetNameZh,
        String targetNameEn,
        boolean targetActive,
        int targetSortOrder
    ) {
    }

    record StationSelection(
        StationSourcePolicy sourcePolicy,
        String sourceCode,
        String targetCode,
        String targetName,
        boolean targetActive,
        int targetSortOrder
    ) {
    }

    record ItemSelection(
        String sourceSku,
        SourcePolicy sourcePolicy,
        String targetSku,
        String targetCategoryCode,
        String targetStationCode,
        String profileCreatedItemType,
        String targetNameZh,
        String targetNameEn,
        BigDecimal targetBasePrice,
        boolean targetActive,
        boolean targetSoldOut,
        int targetSortOrder,
        Set<ItemRole> roles
    ) {

        public ItemSelection {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }

    enum StationSourcePolicy {
        REQUIRED_SOURCE_CODE,
        UNIQUE_ACTIVE_STATION_FROM_SELECTED_ITEMS
    }

    enum CategorySourcePolicy {
        REQUIRED_SOURCE_CODE,
        CREATE_ONLY
    }

    enum SourcePolicy {
        REQUIRED_SOURCE_CODE,
        CLONE_IF_ACTIVE_OR_CREATE,
        CREATE_ONLY
    }

    enum ItemRole {
        NOODLE,
        SIDE_DISH,
        DRINK
    }
}
