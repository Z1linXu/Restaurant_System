package com.restaurant.system.printing.renderer;

import com.restaurant.system.order.entity.Order;

/** Whole-order instructions from the trusted, frozen Uber snapshot, once per kitchen ticket. */
final class ExternalOrderNoteContent {
    private ExternalOrderNoteContent() {}

    static void append(StringBuilder out, Order order) {
        if (order == null || !"UBER_EATS".equals(order.external_source)
                || order.external_order_note_snapshot == null || order.external_order_note_snapshot.isBlank()) return;
        // External text cannot inject ESC/POS controls or renderer markup directives.
        String note = order.external_order_note_snapshot.replaceAll("[\\p{Cc}\\p{Cf}]", " ")
                .replace('[', '［').replace(']', '］').trim();
        if (!note.isBlank()) out.append("订单备注：\n").append(note).append("\n");
    }
}
