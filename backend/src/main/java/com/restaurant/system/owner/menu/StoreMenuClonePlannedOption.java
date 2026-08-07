package com.restaurant.system.owner.menu;

import java.math.BigDecimal;
/**
 * Transaction-local option plan keyed by target item and stable option code.
 *
 * <p>This record is intentionally internal. It is neither persisted as clone evidence nor exposed by
 * a public API.</p>
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
}
