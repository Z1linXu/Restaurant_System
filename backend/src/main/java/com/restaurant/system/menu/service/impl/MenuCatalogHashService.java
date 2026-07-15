package com.restaurant.system.menu.service.impl;

import com.restaurant.system.menu.dto.MenuCatalogResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MenuCatalogHashService {

    public String calculate(MenuCatalogResponse catalog) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, catalog.store_id);
        append(canonical, catalog.organization_id);
        append(canonical, catalog.menu_revision);
        append(canonical, catalog.catalog_version);
        append(canonical, catalog.combo_metadata_version);
        append(canonical, catalog.tax_policy == null ? null : catalog.tax_policy.rate);
        append(canonical, catalog.tax_policy == null ? null : catalog.tax_policy.label);
        append(canonical, catalog.tax_policy == null ? null : catalog.tax_policy.version);
        appendCategories(canonical, catalog.categories);
        return "fnv1a32:" + fnv1a32(canonical.toString());
    }

    private void appendCategories(StringBuilder target, List<MenuCatalogResponse.CategoryResponse> categories) {
        List<MenuCatalogResponse.CategoryResponse> safeCategories = categories == null ? List.of() : categories;
        append(target, safeCategories.size());
        for (MenuCatalogResponse.CategoryResponse category : safeCategories) {
            append(target, category.id);
            append(target, category.code);
            append(target, category.name_zh);
            append(target, category.name_en);
            append(target, category.sort_order);
            append(target, category.is_active);
            List<MenuCatalogResponse.ItemResponse> items = category.items == null ? List.of() : category.items;
            append(target, items.size());
            for (MenuCatalogResponse.ItemResponse item : items) {
                append(target, item.id);
                append(target, item.category_id);
                append(target, item.station_id);
                append(target, item.name_zh);
                append(target, item.name_en);
                append(target, item.sku);
                append(target, item.item_type);
                append(target, item.base_price);
                append(target, item.is_active);
                append(target, item.is_sold_out);
                append(target, item.sort_order);
                appendOptions(target, item.options);
            }
        }
    }

    private void appendOptions(StringBuilder target, List<MenuCatalogResponse.OptionResponse> options) {
        List<MenuCatalogResponse.OptionResponse> safeOptions = options == null ? List.of() : options;
        append(target, safeOptions.size());
        for (MenuCatalogResponse.OptionResponse option : safeOptions) {
            append(target, option.id);
            append(target, option.option_type);
            append(target, option.option_code);
            append(target, option.option_group);
            append(target, option.parent_option_id);
            append(target, option.sort_order);
            append(target, option.name_zh);
            append(target, option.name_en);
            append(target, option.price_delta);
            append(target, option.is_active);
            appendOptions(target, option.side_item_remove_options);
        }
    }

    private void append(StringBuilder target, Object value) {
        String normalized;
        if (value == null) {
            normalized = "<null>";
        } else if (value instanceof BigDecimal decimal) {
            normalized = decimal.stripTrailingZeros().toPlainString();
        } else {
            normalized = String.valueOf(value);
        }
        target.append(normalized.length()).append(':').append(normalized).append('|');
    }

    private String fnv1a32(String value) {
        int hash = 0x811c9dc5;
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= current & 0xff;
            hash *= 0x01000193;
        }
        return String.format("%08x", hash);
    }
}
