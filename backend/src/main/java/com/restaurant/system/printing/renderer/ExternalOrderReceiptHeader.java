package com.restaurant.system.printing.renderer;

import com.restaurant.system.order.entity.Order;

/** Optional external reference, independent of kitchen formatting and table/pickup identities. */
public final class ExternalOrderReceiptHeader {
    private ExternalOrderReceiptHeader() {}

    public static boolean isPresent(Order order) {
        return order != null && order.external_source != null && order.external_display_id != null;
    }

    public static void append(StringBuilder builder, Order order) {
        if (order == null || order.external_source == null || order.external_display_id == null)
            return;
        String source = order.external_source.replace('_', ' ');
        String display = order.external_display_id.replaceAll("[^A-Za-z0-9-]", "");
        builder.append(PrintMarkup.large(source + " #" + display)).append("\n");
    }
}
