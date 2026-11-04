package com.restaurant.system.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Live, reproducible reporting projection. It is not a replacement for transaction evidence. */
public record SalesPeriodReport(
        Long store_id,
        String business_timezone,
        String period,
        LocalDate start_date,
        LocalDate end_date,
        String projection_mode,
        boolean original_transactions_required,
        ChannelSalesSummary channel_sales,
        Pending pending,
        Adjustments adjustments) {

    /** An unknown amount cannot be measured by subtracting a known subtotal from an unknown total. */
    public record Pending(int order_count, int item_line_count, BigDecimal amount, String amount_status) {}

    /** These values are informational evidence, never added to channel_sales a second time. */
    public record Adjustments(
            BigDecimal in_store_discount,
            String in_store_discount_status,
            BigDecimal tax,
            String tax_status,
            BigDecimal uber_discount,
            String uber_discount_status,
            BigDecimal refunds,
            String refunds_status,
            String note) {}
}
