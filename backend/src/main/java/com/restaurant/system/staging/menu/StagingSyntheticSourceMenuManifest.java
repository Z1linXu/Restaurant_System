package com.restaurant.system.staging.menu;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Immutable, ID-free synthetic source menu contract used by STG-005B planning. */
public record StagingSyntheticSourceMenuManifest(
    String manifestCode,
    String manifestVersion,
    String topologyNamespace,
    List<Category> categories,
    List<Station> stations,
    List<Item> items,
    List<Option> options
) {

    public StagingSyntheticSourceMenuManifest {
        categories = immutable(categories, "categories");
        stations = immutable(stations, "stations");
        items = immutable(items, "items");
        options = immutable(options, "options");
    }

    private static <T> List<T> immutable(List<T> values, String field) {
        return List.copyOf(Objects.requireNonNull(values, field));
    }

    public record Category(
        String code,
        String nameZh,
        String nameEn,
        boolean active,
        int sortOrder
    ) {
    }

    public record Station(
        String code,
        String name,
        boolean active,
        int sortOrder
    ) {
    }

    public record Item(
        String sku,
        String categoryCode,
        String stationCode,
        String itemType,
        String nameZh,
        String nameEn,
        BigDecimal basePrice,
        BigDecimal costPerItem,
        boolean active,
        boolean soldOut,
        int sortOrder
    ) {
    }

    public record Option(
        String itemSku,
        String optionType,
        String optionGroup,
        String optionCode,
        String parentOptionCode,
        String nameZh,
        String nameEn,
        BigDecimal priceDelta,
        boolean active,
        int sortOrder
    ) {
    }
}
