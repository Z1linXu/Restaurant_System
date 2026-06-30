package com.restaurant.system.common.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TaxCalculator {

    public static final BigDecimal TAX_RATE = new BigDecimal("0.14975");
    public static final String TAX_RATE_LABEL = "14.975%";

    private TaxCalculator() {}

    public static BigDecimal calculateTax(BigDecimal subtotal) {
        BigDecimal safeSubtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
        return safeSubtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateTotal(BigDecimal subtotal) {
        BigDecimal safeSubtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
        return safeSubtotal.add(calculateTax(safeSubtotal)).setScale(2, RoundingMode.HALF_UP);
    }
}
