package com.restaurant.system.printing.renderer;

import com.restaurant.system.order.entity.Order;

/** Optional external reference, independent of kitchen formatting and table/pickup identities. */
public final class ExternalOrderReceiptHeader {
    private ExternalOrderReceiptHeader() {}

    public static boolean isPresent(Order order) {
        return order != null && order.external_source != null && (order.kitchenMirror() || order.external_display_id != null);
    }

    public static String kitchenLabel(String customerName, String displayId) {
        String name = customerName == null ? "" : customerName.replaceAll("[^\\p{L}\\p{N} .'-]", "").trim();
        String id = displayId == null ? "" : displayId.replaceAll("[^A-Za-z0-9-]", "");
        return "UBER - " + (!name.isBlank() ? name : !id.isBlank() ? id : "ORDER");
    }

    public static void append(StringBuilder builder, Order order) {
        if (!isPresent(order)) return;
        if (order.kitchenMirror()) {
            builder.append(PrintMarkup.large(kitchenLabel(order.external_customer_display_name, order.external_display_id))).append("\n");
            return;
        }
        String source = order.external_source.replace('_', ' ');
        String display = order.external_display_id.replaceAll("[^A-Za-z0-9-]", "");
        builder.append(PrintMarkup.large(source + " #" + display)).append("\n");
    }
}
