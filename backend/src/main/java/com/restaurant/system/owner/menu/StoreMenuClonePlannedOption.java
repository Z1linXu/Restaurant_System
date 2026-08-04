package com.restaurant.system.owner.menu;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Transaction-local option plan keyed by target item and stable option code.
 */
public record StoreMenuClonePlannedOption(
    Long targetItemId,
    Long sourceOptionId,
    String optionType,
    String optionCode,
    String optionGroup,
    String parentOptionCode,
    Integer sortOrder,
    String nameZh,
    String nameEn,
    BigDecimal priceDelta,
    Boolean active
) {

    public StoreMenuClonePlannedOption {
        Objects.requireNonNull(targetItemId, "targetItemId");
        Objects.requireNonNull(optionType, "optionType");
        Objects.requireNonNull(optionCode, "optionCode");
        Objects.requireNonNull(optionGroup, "optionGroup");
        Objects.requireNonNull(sortOrder, "sortOrder");
        Objects.requireNonNull(priceDelta, "priceDelta");
        Objects.requireNonNull(active, "active");
    }
}
