package com.restaurant.system.menu.pricing;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum StandardSize {
    SMALL("SMALL", "size_small", "小碗", "Small", new BigDecimal("-2.00")),
    REGULAR("REGULAR", "size_regular", "中碗", "Regular", BigDecimal.ZERO.setScale(2)),
    LARGE("LARGE", "size_large", "大碗", "Large", new BigDecimal("2.00"));

    public final String semantic;
    public final String code;
    public final String labelZh;
    public final String labelEn;
    public final BigDecimal defaultDelta;

    StandardSize(String semantic, String code, String labelZh, String labelEn, BigDecimal defaultDelta) {
        this.semantic = semantic;
        this.code = code;
        this.labelZh = labelZh;
        this.labelEn = labelEn;
        this.defaultDelta = defaultDelta;
    }

    public static Optional<StandardSize> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(size -> size.code.equals(normalized))
            .findFirst();
    }

    public static Optional<StandardSize> fromOption(String optionCode, String nameZh, String nameEn) {
        Optional<StandardSize> byCode = fromCode(optionCode);
        if (byCode.isPresent()) {
            return byCode;
        }
        String zh = nameZh == null ? "" : nameZh.trim();
        String en = nameEn == null ? "" : nameEn.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(size -> size.labelZh.equals(zh) || size.labelEn.toLowerCase(Locale.ROOT).equals(en))
            .findFirst();
    }
}
