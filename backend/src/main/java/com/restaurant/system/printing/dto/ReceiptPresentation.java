package com.restaurant.system.printing.dto;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Frozen, server-owned presentation data for the explicitly enabled merchant receipt. */
public class ReceiptPresentation {
    public String language;
    public String restaurant_name;
    public Long settings_revision;
    public List<String> kitchen_station_codes = new ArrayList<>();
    public List<Long> order_item_ids = new ArrayList<>();
    public Map<String, String> item_names = new LinkedHashMap<>();
    public Map<String, String> option_names = new LinkedHashMap<>();
    public BigDecimal subtotal;
    public BigDecimal total;
    public String version = "ALLO_RECEIPT_V1";

    public void requireValid() {
        if (!"ALLO_RECEIPT_V1".equals(version) || language == null
                || !Set.of("zh-CN", "en", "fr-CA").contains(language)
                || order_item_ids == null || order_item_ids.isEmpty()
                || order_item_ids.stream().anyMatch(id -> id == null)
                || kitchen_station_codes == null || item_names == null || option_names == null) {
            throw new BusinessException("RECEIPT_PRESENTATION_INVALID");
        }
    }

    public boolean includesItem(Long id) {
        return id != null && order_item_ids.contains(id);
    }

    public String itemName(OrderItem item) {
        return frozenName(item_names, item.id, item.item_name_snapshot_zh,
            item.item_name_snapshot_en, item.item_name_snapshot_fr, item.item_sku_snapshot);
    }

    public String optionName(OrderItemOption option) {
        return frozenName(option_names, option.id, option.option_name_snapshot_zh,
            option.option_name_snapshot_en, option.option_name_snapshot_fr, option.option_code_snapshot);
    }

    private String frozenName(Map<String, String> names, Long id, String zh, String en, String fr, String code) {
        String captured = id == null ? null : names.get(String.valueOf(id));
        if (captured != null && !captured.isBlank()) return captured;
        // Only immutable order snapshots are fallback sources; never consult a live menu.
        List<String> candidates = "fr-CA".equals(language)
            ? java.util.Arrays.asList(fr, en, zh, code)
            : "en".equals(language) ? java.util.Arrays.asList(en, zh, fr, code)
                : java.util.Arrays.asList(zh, en, fr, code);
        return candidates.stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse("?");
    }

    public String label(String key) {
        return switch (key) {
            case "updated" -> words("加单", "UPDATED", "AJOUT");
            case "added_only" -> words("仅新增菜品", "Added items only", "Articles ajoutés seulement");
            case "table" -> words("桌号", "Table", "Table");
            case "pickup" -> words("取餐号", "Pickup", "Numéro de retrait");
            case "walk_in" -> words("散客", "Walk-in", "Sans réservation");
            case "takeout" -> words("外带", "Takeout", "À emporter");
            case "order_type" -> words("订单类型", "Order Type", "Type de commande");
            case "notes" -> words("备注", "Notes", "Notes");
            case "subtotal" -> words("小计", "Subtotal", "Sous-total");
            case "tax" -> words("税", "Tax", "Taxes");
            case "total" -> words("合计", "Total", "Total");
            case "submitted" -> words("提交时间", "Submitted", "Envoyée");
            case "printed" -> words("打印时间", "Printed", "Imprimée");
            case "created" -> words("创建时间", "Created", "Créée");
            default -> throw new BusinessException("RECEIPT_LABEL_UNKNOWN");
        };
    }

    private String words(String zh, String en, String fr) {
        return "fr-CA".equals(language) ? fr : "en".equals(language) ? en : zh;
    }
}
