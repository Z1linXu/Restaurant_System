package com.restaurant.system.owner.menu;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record StoreMenuCloneSnapshot(
    Long storeId,
    Long organizationId,
    Long menuRevision,
    LocalDateTime menuUpdatedAt,
    List<SourceCategory> categories,
    List<SourceStation> stations,
    List<SourceItem> items,
    List<SourceOption> options
) {

    public StoreMenuCloneSnapshot {
        Objects.requireNonNull(storeId, "storeId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(menuRevision, "menuRevision");
        categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        stations = List.copyOf(Objects.requireNonNull(stations, "stations"));
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        options = List.copyOf(Objects.requireNonNull(options, "options"));
    }

    public record SourceCategory(
        Long id,
        String code,
        String nameZh,
        String nameEn,
        Integer sortOrder,
        Boolean active
    ) {
    }

    public record SourceStation(
        Long id,
        String code,
        String name,
        Integer sortOrder,
        Boolean active
    ) {
    }

    public record SourceItem(
        Long id,
        Long storeId,
        Long categoryId,
        Long stationId,
        String sku,
        String nameZh,
        String nameEn,
        String itemType,
        BigDecimal basePrice,
        BigDecimal costPerItem,
        Boolean active,
        Boolean soldOut,
        Integer sortOrder
    ) {
    }

    public record SourceOption(
        Long id,
        Long ownerMenuItemId,
        Long ownerStoreId,
        String optionType,
        String optionCode,
        String optionGroup,
        Long parentOptionId,
        Integer sortOrder,
        String nameZh,
        String nameEn,
        BigDecimal priceDelta,
        Boolean active
    ) {
    }
}
