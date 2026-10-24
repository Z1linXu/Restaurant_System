package com.restaurant.system.printing.renderer;

import com.restaurant.system.order.entity.Order;
import com.restaurant.system.station.service.DiningTableDisplayService;

public final class PrintTableDisplayFormatter {

    private PrintTableDisplayFormatter() {
    }

    public static boolean isDineIn(Order order) {
        return order != null && "dine_in".equalsIgnoreCase(order.order_type)
            && !order.kitchenMirror() && !ExternalOrderReceiptHeader.isPresent(order);
    }

    static String formatDineInHeader(Order order, boolean updateTicket) {
        String label = order.table_display_name;
        if (label == null || label.isBlank()) label = formatSplitTableLabel(order.table_no);
        if (label == null || label.isBlank()) {
            label = "Walk-in";
        }
        return PrintMarkup.withTopFeed(PrintMarkup.large(label) + (updateTicket ? " (UPDATE)" : ""));
    }

    static String formatSplitTableLabel(String value) {
        return DiningTableDisplayService.legacyDisplayName(value);
    }
}
