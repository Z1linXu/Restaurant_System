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
        if (catalog.safe_summary != null) {
            var summary = catalog.safe_summary;
            append(canonical, summary.schema_version()); append(canonical, summary.config_revision());
            append(canonical, summary.brand_name()); append(canonical, summary.locale()); append(canonical, summary.currency());
            append(canonical, summary.timezone()); append(canonical, summary.tax_policy_reference());
            append(canonical, summary.profile_code()); append(canonical, summary.compatibility_mode());
        }
        append(canonical, catalog.tax_policy == null ? null : catalog.tax_policy.rate);
        append(canonical, catalog.tax_policy == null ? null : catalog.tax_policy.label);
        append(canonical, catalog.tax_policy == null ? null : catalog.tax_policy.version);
        appendPricingPolicy(canonical, catalog.pricing_policy);
        appendComboConfiguration(canonical, catalog.combo_configuration);
        appendCategories(canonical, catalog.categories);
        appendCustomizationExtension(canonical, catalog);
        return "fnv1a32:" + fnv1a32(canonical.toString());
    }

    // Keep legacy canonical bytes stable. This suffix is shared with frontend menuCache.ts.
    private void appendCustomizationExtension(StringBuilder target, MenuCatalogResponse catalog) {
        var categories = catalog.categories == null ? List.<MenuCatalogResponse.CategoryResponse>of() : catalog.categories;
        var groups = catalog.combo_configuration == null || catalog.combo_configuration.groups == null
            ? List.<com.restaurant.system.menu.dto.StoreComboConfigurationResponse.GroupResponse>of() : catalog.combo_configuration.groups;
        boolean extended = categories.stream().anyMatch(category -> category.name_fr != null
            || (category.items != null && category.items.stream().anyMatch(item -> item.name_fr != null
                || item.customization_config != null || Boolean.FALSE.equals(item.sellable) || hasFrenchOptions(item.options))))
            || groups.stream().anyMatch(group -> group.name_fr != null || (group.components != null
                && group.components.stream().anyMatch(component -> component.name_fr != null || component.linked_menu_item_name_fr != null)));
        if (!extended) return;
        append(target, "menu-customization-v1");
        append(target, categories.size());
        for (var category : categories) {
            append(target, category.id); append(target, category.name_fr);
            var items = category.items == null ? List.<MenuCatalogResponse.ItemResponse>of() : category.items;
            append(target, items.size());
            for (var item : items) {
                append(target, item.id); append(target, item.name_fr); append(target, !Boolean.FALSE.equals(item.sellable));
                var config = item.customization_config;
                append(target, config != null);
                if (config != null) {
                    append(target, config.schema_version);
                    append(target, config.option_groups.size());
                    for (var group : config.option_groups) {
                        append(target, group.option_group); append(target, group.min_select); append(target, group.max_select);
                        append(target, group.distinct); append(target, group.default_option_codes.size());
                        for (var code : group.default_option_codes) append(target, code);
                    }
                    var addonCodes = config.addon_max_quantities.keySet().stream().sorted().toList();
                    append(target, addonCodes.size());
                    for (var code : addonCodes) { append(target, code); append(target, config.addon_max_quantities.get(code)); }
                    append(target, config.combo != null);
                    if (config.combo != null) {
                        append(target, config.combo.fixed_price); append(target, config.combo.group_codes.size());
                        for (var code : config.combo.group_codes) append(target, code);
                    }
                }
                appendFrenchOptions(target, item.options);
            }
        }
        append(target, groups.size());
        for (var group : groups) {
            append(target, group.group_id); append(target, group.name_fr);
            var components = group.components == null ? List.<com.restaurant.system.menu.dto.StoreComboConfigurationResponse.ComponentResponse>of() : group.components;
            append(target, components.size());
            for (var component : components) {
                append(target, component.id); append(target, component.name_fr);
                append(target, component.linked_menu_item_name_fr); append(target, component.synthetic_option_id);
            }
        }
    }

    private boolean hasFrenchOptions(List<MenuCatalogResponse.OptionResponse> options) {
        return options != null && options.stream().anyMatch(option -> option.name_fr != null || hasFrenchOptions(option.side_item_remove_options));
    }

    private void appendFrenchOptions(StringBuilder target, List<MenuCatalogResponse.OptionResponse> options) {
        var safe = options == null ? List.<MenuCatalogResponse.OptionResponse>of() : options;
        append(target, safe.size());
        for (var option : safe) {
            append(target, option.id); append(target, option.name_fr); appendFrenchOptions(target, option.side_item_remove_options);
        }
    }

    private void appendPricingPolicy(StringBuilder target, com.restaurant.system.menu.dto.StorePricingPolicyResponse policy) {
        append(target, policy == null ? null : policy.store_id);
        append(target, policy == null ? null : policy.policy_revision);
        append(target, policy == null ? null : policy.size_small_delta);
        append(target, policy == null ? null : policy.size_regular_delta);
        append(target, policy == null ? null : policy.size_large_delta);
        append(target, policy == null ? null : policy.combo_delta);
    }

    private void appendComboConfiguration(StringBuilder target, com.restaurant.system.menu.dto.StoreComboConfigurationResponse configuration) {
        append(target, configuration == null ? null : configuration.store_id);
        append(target, configuration == null ? null : configuration.menu_revision);
        List<com.restaurant.system.menu.dto.StoreComboConfigurationResponse.GroupResponse> groups =
            configuration == null || configuration.groups == null ? List.of() : configuration.groups;
        append(target, groups.size());
        for (com.restaurant.system.menu.dto.StoreComboConfigurationResponse.GroupResponse group : groups) {
            append(target, group.group_id);
            append(target, group.group_code);
            append(target, group.component_group);
            append(target, group.name_zh);
            append(target, group.name_en);
            append(target, group.selection_rule);
            append(target, group.required);
            append(target, group.enabled);
            append(target, group.display_order);
            append(target, group.default_component_code);
            List<com.restaurant.system.menu.dto.StoreComboConfigurationResponse.ComponentResponse> components =
                group.components == null ? List.of() : group.components;
            append(target, components.size());
            for (com.restaurant.system.menu.dto.StoreComboConfigurationResponse.ComponentResponse component : components) {
                append(target, component.id);
                append(target, component.group_id);
                append(target, component.component_group);
                append(target, component.component_code);
                append(target, component.name_zh);
                append(target, component.name_en);
                append(target, component.enabled);
                append(target, component.display_order);
                append(target, component.is_default);
                append(target, component.linked_menu_item_id);
                append(target, component.linked_menu_item_sku);
                append(target, component.linked_menu_item_name_zh);
                append(target, component.linked_menu_item_name_en);
                append(target, component.business_behavior);
            }
        }
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
                append(target, item.default_combo_egg_component_code);
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
