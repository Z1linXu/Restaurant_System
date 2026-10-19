package com.restaurant.system.order.service;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.addon.StoreAddonOptionResolver;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuItemCustomizationService;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Canonical validation for opt-in configured menus; legacy payloads retain their existing adapter. */
@Service
public class ConfiguredOrderItemService {
    public record Component(Long componentId, Long menuItemId, Long stationId) {}
    private final MenuItemCustomizationService customization;
    private final MenuItemOptionRepository options;
    private final MenuItemRepository items;
    private final StoreComboConfigurationService combos;
    private final StoreAddonOptionResolver addons;

    public ConfiguredOrderItemService(MenuItemCustomizationService customization,
        MenuItemOptionRepository options, MenuItemRepository items,
        StoreComboConfigurationService combos, StoreAddonOptionResolver addons) {
        this.customization = customization; this.options = options; this.items = items;
        this.combos = combos; this.addons = addons;
    }

    public void validateItem(MenuItem item, BigDecimal submittedPrice) {
        if (Boolean.FALSE.equals(item.sellable)) throw failure("MENU_ITEM_COMPONENT_ONLY");
        if (item.customization_config == null) return;
        if (!Boolean.TRUE.equals(item.is_active) || Boolean.TRUE.equals(item.is_sold_out)) {
            throw failure("MENU_ITEM_UNAVAILABLE");
        }
        requirePrice(submittedPrice, item.base_price);
    }

    /** Option quantities are totals for this order line, matching the existing amount/BOM contract. */
    public Map<Long, Component> canonicalize(MenuItem item, Integer parentQuantity,
        List<CreateOrderItemOptionRequest> requested) {
        if (item.customization_config == null) return Map.of();
        if (parentQuantity == null || parentQuantity < 1) throw failure("ITEM_QUANTITY_INVALID");
        List<MenuItemOption> active = addons.resolve(options.findAllByMenuItemIdOrdered(item.id));
        Map<Long, MenuItemOption> byId = new HashMap<>();
        active.stream().filter(o -> Boolean.TRUE.equals(o.is_active)).forEach(o -> byId.put(o.id, o));
        List<StoreComboConfigurationResponse.GroupResponse> groups = customization.applicableComboGroups(
            item, combos.getConfiguration(item.store_id).groups);
        Map<Long, StoreComboConfigurationResponse.ComponentResponse> componentById = new HashMap<>();
        Map<Long, String> groupById = new HashMap<>();
        for (var group : groups) for (var component : group.components) {
            if (!Boolean.TRUE.equals(component.enabled)) continue;
            long id = MenuItemCustomizationService.componentOptionId(component.id);
            componentById.put(id, component); groupById.put(id, group.group_code);
        }
        Map<Long, Component> components = new LinkedHashMap<>();
        List<MenuItemCustomizationService.Selection> selections = new ArrayList<>();
        Map<String, Integer> groupCounts = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        boolean comboSelected = false;
        for (var request : requested) {
            if (request == null || request.option_id == null || !seen.add(request.option_id)
                || request.quantity == null || request.quantity < 1 || request.quantity % parentQuantity != 0) {
                throw failure("MENU_SELECTION_QUANTITY_INVALID");
            }
            int perServing = request.quantity / parentQuantity;
            MenuItemOption option = byId.get(request.option_id);
            if (option != null) {
                String group = option.option_group == null ? "" : option.option_group;
                BigDecimal price = option.price_delta == null ? BigDecimal.ZERO : option.price_delta;
                if ("COMBO".equals(group)) {
                    if (item.customization_config.combo == null || perServing != 1 || comboSelected) {
                        throw failure("COMBO_MAIN_INELIGIBLE");
                    }
                    comboSelected = true;
                    price = customization.fixedComboDelta(item);
                }
                requirePrice(request.option_price_snapshot, price);
                request.option_type_snapshot = option.option_type;
                request.option_code_snapshot = option.option_code;
                request.option_group_snapshot = option.option_group;
                request.parent_option_id_snapshot = option.parent_option_id;
                request.option_name_snapshot_zh = option.name_zh;
                request.option_name_snapshot_en = option.name_en;
                request.option_name_snapshot_fr = option.name_fr;
                request.option_price_snapshot = price;
                selections.add(new MenuItemCustomizationService.Selection(group, option.option_code, perServing));
                continue;
            }
            var component = componentById.get(request.option_id);
            if (component == null || perServing != 1) throw failure("MENU_OPTION_UNAVAILABLE");
            requirePrice(request.option_price_snapshot, BigDecimal.ZERO);
            String group = groupById.get(request.option_id);
            request.option_type_snapshot = "modifier";
            request.option_group_snapshot = group;
            request.option_code_snapshot = component.component_code;
            request.option_name_snapshot_zh = component.name_zh;
            request.option_name_snapshot_en = component.name_en;
            request.option_name_snapshot_fr = component.name_fr;
            request.option_price_snapshot = BigDecimal.ZERO;
            request.parent_option_id_snapshot = null;
            groupCounts.merge(group, 1, Integer::sum);
            if ("LINKED_MENU_ITEM".equals(component.business_behavior)) {
                MenuItem linked = items.findById(component.linked_menu_item_id)
                    .orElseThrow(() -> failure("COMBO_COMPONENT_UNAVAILABLE"));
                if (!Objects.equals(item.store_id, linked.store_id) || !Boolean.TRUE.equals(linked.is_active)
                    || Boolean.TRUE.equals(linked.is_sold_out) || linked.station_id == null) {
                    throw failure("COMBO_COMPONENT_UNAVAILABLE");
                }
                components.put(request.option_id, new Component(component.id, linked.id, linked.station_id));
            }
        }
        customization.validateSelection(item, selections);
        if (!comboSelected && !groupCounts.isEmpty()) throw failure("COMBO_SELECTION_REQUIRES_MAIN");
        if (comboSelected) for (var group : groups) {
            int count = groupCounts.getOrDefault(group.group_code, 0);
            if (count > 1 || ((Boolean.TRUE.equals(group.required) || "EXACTLY_ONE".equals(group.selection_rule)) && count != 1)) {
                throw failure("COMBO_SELECTION_REQUIRED: " + group.group_code);
            }
        }
        return components;
    }

    private static void requirePrice(BigDecimal supplied, BigDecimal canonical) {
        if (canonical == null || canonical.signum() < 0 || (supplied != null && supplied.compareTo(canonical) != 0)) {
            throw failure("MENU_CATALOG_CHANGED: refresh menu and confirm the current price");
        }
    }
    private static BusinessException failure(String message) { return new BusinessException(message); }
}
