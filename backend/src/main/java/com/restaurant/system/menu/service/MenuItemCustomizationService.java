package com.restaurant.system.menu.service;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.ItemCustomizationConfiguration;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.addon.StoreAddonOptionResolver;
import com.restaurant.system.station.repository.StationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Validates optional menu rules. Order callers retain their existing identity and snapshot checks. */
@Service
public class MenuItemCustomizationService {
    public static final long COMPONENT_OPTION_ID_OFFSET = 1_000_000_000L;
    public record Selection(String option_group, String option_code, Integer quantity) {}

    private final MenuItemRepository items;
    private final MenuItemOptionRepository options;
    private final StoreComboConfigurationService combos;
    private final MenuRevisionService revisions;
    private final StoreAddonOptionResolver addons;
    private final StationRepository stations;

    public MenuItemCustomizationService(MenuItemRepository items, MenuItemOptionRepository options,
        StoreComboConfigurationService combos, MenuRevisionService revisions,
        StoreAddonOptionResolver addons, StationRepository stations) {
        this.items = items;
        this.options = options;
        this.combos = combos;
        this.revisions = revisions;
        this.addons = addons;
        this.stations = stations;
    }

    public ItemCustomizationConfiguration configuration(MenuItem item) { return item.customization_config; }

    @Transactional
    public ItemCustomizationConfiguration updateConfiguration(Long itemId, Long authorizedStoreId,
        ItemCustomizationConfiguration configuration) {
        revisions.lockStoresInOrder(List.of(authorizedStoreId));
        MenuItem item = items.findById(itemId).orElseThrow(() -> failure("MENU_ITEM_NOT_FOUND"));
        if (!Objects.equals(authorizedStoreId, item.store_id)) throw failure("MENU_CUSTOMIZATION_STORE_MISMATCH");
        item.customization_config = configuration;
        validateConfiguration(item);
        item.updated_at = LocalDateTime.now();
        items.save(item);
        revisions.incrementRevision(authorizedStoreId);
        return item.customization_config;
    }

    public void validateConfiguration(MenuItem item) {
        ItemCustomizationConfiguration config = configuration(item);
        if (config == null) return;
        if (!Integer.valueOf(1).equals(config.schema_version) || config.option_groups == null
            || config.addon_max_quantities == null) throw failure("MENU_CUSTOMIZATION_SCHEMA_INVALID");
        if (item.station_id != null && stations.findActiveStationByIdAndStoreId(item.station_id, item.store_id) == null) {
            throw failure("MENU_CUSTOMIZATION_STATION_INVALID");
        }
        List<MenuItemOption> currentOptions = item.id == null ? List.of() : addons.resolve(options.findAllByMenuItemIdOrdered(item.id));
        Set<String> groupCodes = new HashSet<>();
        for (var group : config.option_groups) {
            if (group == null || !validGroup(group.option_group) || !groupCodes.add(group.option_group)
                || "COMBO".equals(group.option_group) || group.option_group.startsWith("COMBO_")
                || "ADD_ON".equals(group.option_group) || group.min_select == null || group.max_select == null
                || group.min_select < 0 || group.max_select < group.min_select || group.distinct == null
                || group.default_option_codes == null) throw failure("MENU_CUSTOMIZATION_GROUP_INVALID");
            List<String> enabledCodes = currentOptions.stream()
                .filter(o -> Boolean.TRUE.equals(o.is_active) && group.option_group.equals(o.option_group))
                .map(o -> o.option_code).filter(Objects::nonNull).distinct().toList();
            if (group.distinct && enabledCodes.size() < group.min_select) throw failure("MENU_CUSTOMIZATION_GROUP_UNSATISFIABLE");
            if (new HashSet<>(group.default_option_codes).size() != group.default_option_codes.size()
                || !enabledCodes.containsAll(group.default_option_codes)
                || group.default_option_codes.size() > group.max_select
                || (!group.default_option_codes.isEmpty() && group.default_option_codes.size() < group.min_select)) {
                throw failure("MENU_CUSTOMIZATION_DEFAULT_INVALID");
            }
        }
        for (var limit : config.addon_max_quantities.entrySet()) {
            if (!validCode(limit.getKey()) || limit.getValue() == null || limit.getValue() < 0) {
                throw failure("MENU_CUSTOMIZATION_ADDON_LIMIT_INVALID");
            }
            boolean assigned = currentOptions.stream().anyMatch(o -> limit.getKey().equals(o.option_code)
                && o.store_addon_id != null && Objects.equals(item.store_id, o.store_addon_store_id)
                && Boolean.TRUE.equals(o.addon_eligible));
            if (!assigned) throw failure("MENU_CUSTOMIZATION_ADDON_NOT_ASSIGNED");
        }
        if (config.combo != null) {
            money(config.combo.fixed_price);
            if (item.base_price == null || config.combo.fixed_price.compareTo(item.base_price) < 0
                || config.combo.group_codes == null || config.combo.group_codes.isEmpty()
                || config.combo.group_codes.stream().anyMatch(code -> !validGroup(code))
                || new HashSet<>(config.combo.group_codes).size() != config.combo.group_codes.size()) {
                throw failure("MENU_CUSTOMIZATION_COMBO_INVALID");
            }
            List<StoreComboConfigurationResponse.GroupResponse> active = combos.getConfiguration(item.store_id).groups;
            for (var group : applicableComboGroups(item, active)) for (var component : group.components) {
                if (!Boolean.TRUE.equals(component.enabled) || component.linked_menu_item_id == null) continue;
                MenuItem linked = items.findById(component.linked_menu_item_id)
                    .orElseThrow(() -> failure("MENU_CUSTOMIZATION_COMPONENT_INVALID"));
                if (!Objects.equals(item.store_id, linked.store_id) || !Boolean.TRUE.equals(linked.is_active)
                    || (linked.station_id != null && stations.findActiveStationByIdAndStoreId(linked.station_id, item.store_id) == null)
                    || ("LINKED_MENU_ITEM".equals(component.business_behavior) && linked.station_id == null)) {
                    throw failure("MENU_CUSTOMIZATION_COMPONENT_INVALID");
                }
            }
        }
    }

    /** Validates cardinality, defaults and quantity caps; never supplies missing user selections. */
    public void validateSelection(MenuItem item, List<Selection> requested) {
        ItemCustomizationConfiguration config = configuration(item);
        if (config == null) return;
        List<Selection> selections = requested == null ? List.of() : requested;
        for (Selection selection : selections) {
            if (selection == null || selection.quantity == null || selection.quantity < 1) throw failure("MENU_SELECTION_QUANTITY_INVALID");
        }
        for (var group : config.option_groups) {
            List<Selection> selected = selections.stream().filter(s -> group.option_group.equals(s.option_group)).toList();
            long total = selected.stream().mapToLong(s -> s.quantity).sum();
            Set<String> codes = new HashSet<>();
            boolean duplicate = selected.stream().anyMatch(s -> s.quantity != 1 || s.option_code == null || !codes.add(s.option_code));
            if (total < group.min_select || total > group.max_select || (group.distinct && duplicate)) {
                throw failure("MENU_SELECTION_CARDINALITY_INVALID: " + group.option_group);
            }
        }
        Map<String, Long> addonQuantities = new LinkedHashMap<>();
        selections.stream().filter(s -> "ADD_ON".equals(s.option_group))
            .forEach(s -> addonQuantities.merge(s.option_code, s.quantity.longValue(), Long::sum));
        config.addon_max_quantities.forEach((code, max) -> {
            if (addonQuantities.getOrDefault(code, 0L) > max) throw failure("MENU_SELECTION_ADDON_LIMIT_EXCEEDED: " + code);
        });
    }

    public BigDecimal fixedComboDelta(MenuItem item) { return fixedComboDeltaFor(item); }

    /** A fixed total is represented using the existing base + COMBO option snapshot contract. */
    public static BigDecimal fixedComboDeltaFor(MenuItem item) {
        if (item.customization_config == null || item.customization_config.combo == null) return null;
        BigDecimal total = money(item.customization_config.combo.fixed_price);
        if (item.base_price == null || total.compareTo(item.base_price) < 0) throw failure("MENU_CUSTOMIZATION_COMBO_INVALID");
        return total.subtract(item.base_price).setScale(2, RoundingMode.UNNECESSARY);
    }

    public List<StoreComboConfigurationResponse.GroupResponse> applicableComboGroups(MenuItem item,
        List<StoreComboConfigurationResponse.GroupResponse> available) {
        ItemCustomizationConfiguration config = configuration(item);
        if (config == null) return available;
        if (config.combo == null) return List.of();
        Map<String, StoreComboConfigurationResponse.GroupResponse> byCode = new LinkedHashMap<>();
        for (var group : available) byCode.put(group.group_code, group);
        return config.combo.group_codes.stream().map(code -> {
            var group = byCode.get(code);
            if (group == null || !Boolean.TRUE.equals(group.enabled)
                || group.components == null || group.components.stream().noneMatch(c -> Boolean.TRUE.equals(c.enabled))) {
                throw failure("MENU_CUSTOMIZATION_COMBO_GROUP_UNAVAILABLE: " + code);
            }
            return group;
        }).toList();
    }

    public static long componentOptionId(long componentId) {
        if (componentId <= 0 || componentId > 9_007_199_254_740_991L - COMPONENT_OPTION_ID_OFFSET) {
            throw failure("COMBO_COMPONENT_ID_INVALID");
        }
        return -(COMPONENT_OPTION_ID_OFFSET + componentId);
    }

    private static BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() < 0 || amount.precision() > 38) throw failure("MENU_CUSTOMIZATION_PRICE_INVALID");
        try { return amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw failure("MENU_CUSTOMIZATION_PRICE_INVALID"); }
    }
    private static boolean validGroup(String code) { return code != null && code.length() <= 50 && code.matches("[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)*"); }
    private static boolean validCode(String code) { return code != null && code.length() <= 255 && code.matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"); }
    private static BusinessException failure(String message) { return new BusinessException(message); }
}
