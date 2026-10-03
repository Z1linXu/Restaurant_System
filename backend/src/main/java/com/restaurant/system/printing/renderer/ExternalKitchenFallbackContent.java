package com.restaurant.system.printing.renderer;

import com.restaurant.system.order.dto.ExternalKitchenSnapshot;
import com.restaurant.system.order.entity.OrderItem;
import java.util.List;
import java.util.Set;

/** Append frozen display-only lines to the existing kitchen tickets, without routing by names. */
final class ExternalKitchenFallbackContent {
    private ExternalKitchenFallbackContent() {}

    static void append(StringBuilder out, List<OrderItem> items, Set<Long> eligibleItemIds) {
        if (items == null) return;
        for (OrderItem item : items) {
            ExternalKitchenSnapshot raw = item.externalKitchenSnapshot;
            if (raw == null || !eligibleItemIds.contains(item.id)) continue;
            String title = raw.rawRoot() ? display(raw.itemName(), raw.itemId()) : item.item_name_snapshot_zh;
            out.append(plain(title)).append(" x").append(item.quantity).append('\n');
            modifiers(out, raw.modifiers(), 1);
            if (raw.notes() != null && !raw.notes().isBlank()) out.append(plain(raw.notes())).append('\n');
            out.append(raw.rawRoot() ? "【未映射 Uber 菜】\n" : "【未映射 Uber 选项/备注】\n");
        }
    }

    private static void modifiers(StringBuilder out, List<ExternalKitchenSnapshot.Modifier> modifiers, int multiplier) {
        if (modifiers == null) return;
        for (var modifier : modifiers) {
            int quantity = Math.multiplyExact(multiplier, modifier.quantity());
            if (modifier.removed()) out.append("去除 ");
            out.append(plain(display(modifier.name(), modifier.id()))).append(" x").append(quantity).append('\n');
            if (modifier.notes() != null && !modifier.notes().isBlank()) out.append(plain(modifier.notes())).append('\n');
            modifiers(out, modifier.modifiers(), quantity);
        }
    }

    private static String display(String name, String id) {
        return name == null || name.isBlank() ? "Uber item " + id : name;
    }

    private static String plain(String text) {
        // External names cannot inject ESC/POS controls or the renderer's markup directives.
        return text == null ? "" : text.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replace('[', '［').replace(']', '］');
    }
}
