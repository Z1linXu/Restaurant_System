package com.restaurant.system.integration.ubereats.mapping;

import com.restaurant.system.integration.ubereats.dto.UberOrderSnapshot;
import com.restaurant.system.integration.ubereats.entity.*;
import com.restaurant.system.integration.ubereats.repository.UberEatsMenuMappingRepository;
import com.restaurant.system.menu.dto.MenuCatalogResponse;
import com.restaurant.system.menu.service.MenuService;
import com.restaurant.system.order.dto.*;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** Maps stable identities into the SAME catalog snapshots consumed by Pad. No name translation. */
@Service
public class UberEatsMenuMappingService {
    private final MenuService menus;
    private final UberEatsMenuMappingRepository mappings;

    public UberEatsMenuMappingService(MenuService menus, UberEatsMenuMappingRepository mappings) {
        this.menus = menus;
        this.mappings = mappings;
    }

    public record Result(CreateOrderRequest request, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public record Choice(
            Long id,
            String code,
            String group,
            String type,
            String zh,
            String en,
            BigDecimal price,
            Long parentId,
            String parentCode) {}

    public MenuCatalogResponse catalog(Long storeId) {
        return menus.getCatalog(storeId);
    }

    public Result map(UberEatsStoreMapping store, UberOrderSnapshot snapshot) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.storeId;
        request.order_type = "delivery";
        List<String> errors = new ArrayList<>(snapshot.issues());
        if (!store.uberStoreId.equals(snapshot.store_id())) errors.add("STORE_MISMATCH");
        MenuCatalogResponse catalog = menus.getCatalog(store.storeId);
        if (!store.storeId.equals(catalog.store_id)
                || !store.organizationId.equals(catalog.organization_id))
            errors.add("CATALOG_STORE_MISMATCH");
        List<UberEatsMenuMapping> rules = mappings.findAllByStoreMappingIdOrderByIdAsc(store.id);
        for (UberOrderSnapshot.Item source : snapshot.items()) {
            addIssues(errors, source);
            var rule = resolve(rules, "ITEM", "", source);
            List<MenuCatalogResponse.ItemResponse> candidates =
                    allItems(catalog).stream()
                            .filter(
                                    i ->
                                            rule != null
                                                    ? Objects.equals(i.id, rule.localMenuItemId)
                                                    : stableItemMatch(i, source))
                            .toList();
            if (candidates.size() != 1) {
                errors.add(
                        label(source)
                                + ": ITEM_MAPPING_"
                                + (candidates.isEmpty() ? "MISSING" : "AMBIGUOUS"));
                continue;
            }
            var item = candidates.get(0);
            if (!Boolean.TRUE.equals(item.is_active) || Boolean.TRUE.equals(item.is_sold_out))
                errors.add(label(source) + ": ITEM_UNAVAILABLE");
            if (item.station_id == null || item.name_zh == null || item.name_zh.isBlank())
                errors.add(label(source) + ": LOCAL_SNAPSHOT_INCOMPLETE");
            var category =
                    catalog.categories.stream()
                            .filter(c -> Objects.equals(c.id, item.category_id))
                            .findFirst()
                            .orElseThrow();
            CreateOrderItemRequest line = new CreateOrderItemRequest();
            line.menu_item_id = item.id;
            line.item_name_snapshot_zh = item.name_zh;
            line.item_name_snapshot_en = item.name_en;
            line.category_code_snapshot = category.code;
            line.station_id_snapshot = item.station_id;
            line.item_sku_snapshot = item.sku;
            line.item_type_snapshot = item.item_type;
            line.unit_price_snapshot = item.base_price;
            line.quantity = source.quantity();
            line.combo_role = "standalone";
            line.notes = joinNotes(snapshot.notes(), source.notes());
            List<Choice> choices = choices(catalog, item);
            Set<Long> selected = new HashSet<>();
            for (UberOrderSnapshot.Item modifier : source.modifiers())
                mapModifier(modifier, source.id(), null, 1, rules, choices, line, errors, selected);
            validateSelections(catalog, item, line, choices, errors, source);
            if (line.notes != null && line.notes.length() > 255)
                errors.add(label(source) + ": NOTES_EXCEED_LOCAL_LIMIT");
            int instructionBudget =
                    (line.notes == null ? 0 : line.notes.length())
                            + line.options.stream()
                                    .mapToInt(
                                            o ->
                                                    (o.option_name_snapshot_zh == null
                                                                    ? 0
                                                                    : o.option_name_snapshot_zh
                                                                            .length())
                                                            + 8)
                                    .sum();
            if (instructionBudget > 255)
                errors.add(label(source) + ": INSTRUCTIONS_EXCEED_LOCAL_LIMIT");
            request.items.add(line);
        }
        return new Result(request, List.copyOf(errors));
    }

    private void mapModifier(
            UberOrderSnapshot.Item source,
            String root,
            String parent,
            int multiplier,
            List<UberEatsMenuMapping> rules,
            List<Choice> choices,
            CreateOrderItemRequest line,
            List<String> errors,
            Set<Long> selected) {
        addIssues(errors, source);
        var rule = resolve(rules, source.removed() ? "REMOVED_MODIFIER" : "MODIFIER", root, source);
        if (rule != null && !Objects.equals(rule.localMenuItemId, line.menu_item_id)) {
            errors.add(label(source) + ": MODIFIER_PARENT_ITEM_MISMATCH");
            return;
        }
        // Removed ingredients require explicit REMOVE mapping; never interpret an ingredient as an
        // Add-on.
        List<Choice> found =
                choices.stream()
                        .filter(
                                c ->
                                        rule != null
                                                ? Objects.equals(c.code(), rule.localOptionCode)
                                                        && Objects.equals(
                                                                c.group(), rule.localOptionGroup)
                                                        && Objects.equals(
                                                                empty(c.parentCode()),
                                                                empty(rule.parentOptionCode))
                                                : !source.removed()
                                                        && c.parentCode() == null
                                                        && !source.external_data().isBlank()
                                                        && source.external_data().equals(c.code()))
                        .toList();
        if (found.size() != 1) {
            errors.add(
                    label(source)
                            + ": MODIFIER_MAPPING_"
                            + (found.isEmpty() ? "MISSING" : "AMBIGUOUS"));
            return;
        }
        Choice choice = found.get(0);
        if (source.removed() && !Set.of("REMOVE", "COMBO_SIDE_REMOVE").contains(choice.group()))
            errors.add(label(source) + ": REMOVED_MODIFIER_MUST_MAP_TO_REMOVE");
        if (choice.parentCode() != null && !Objects.equals(parent, choice.parentCode()))
            errors.add(label(source) + ": COMBO_PARENT_MISMATCH");
        if (!selected.add(choice.id())) errors.add(label(source) + ": DUPLICATE_LOCAL_OPTION");
        long quantity = (long) multiplier * source.quantity();
        if (quantity < 1 || quantity > 100)
            errors.add(label(source) + ": MODIFIER_QUANTITY_INVALID");
        CreateOrderItemOptionRequest option = new CreateOrderItemOptionRequest();
        option.option_id = choice.id();
        option.option_code_snapshot = choice.code();
        option.option_group_snapshot = choice.group();
        option.option_type_snapshot = choice.type();
        option.parent_option_id_snapshot = choice.parentId();
        option.option_name_snapshot_zh = choice.zh();
        option.option_name_snapshot_en = choice.en();
        option.option_price_snapshot = choice.price();
        option.quantity = (int) quantity;
        line.options.add(option);
        line.notes =
                joinNotes(
                        line.notes,
                        source.notes().isBlank() ? "" : source.title() + ": " + source.notes());
        for (var child : source.modifiers())
            mapModifier(
                    child,
                    root,
                    choice.code(),
                    (int) quantity,
                    rules,
                    choices,
                    line,
                    errors,
                    selected);
    }

    private void validateSelections(
            MenuCatalogResponse catalog,
            MenuCatalogResponse.ItemResponse item,
            CreateOrderItemRequest line,
            List<Choice> choices,
            List<String> errors,
            UberOrderSnapshot.Item source) {
        Set<String> groups = new HashSet<>();
        line.options.forEach(o -> groups.add(o.option_group_snapshot));
        for (String required : List.of("SIZE", "SOUP_BASE"))
            if (choices.stream().anyMatch(c -> required.equals(c.group()))
                    && !groups.contains(required))
                errors.add(label(source) + ": REQUIRED_" + required + "_MISSING");
        for (String single : List.of("SIZE", "SOUP_BASE", "NOODLE_TYPE", "SPICY_LEVEL", "COMBO")) {
            long n =
                    line.options.stream()
                            .filter(o -> single.equals(o.option_group_snapshot))
                            .mapToLong(o -> o.quantity)
                            .sum();
            if (n > 1) errors.add(label(source) + ": MULTIPLE_" + single);
        }
        boolean combo = groups.contains("COMBO");
        if (catalog.combo_configuration != null)
            for (var group : catalog.combo_configuration.groups) {
                long count =
                        line.options.stream()
                                .filter(o -> group.component_group.equals(o.option_group_snapshot))
                                .mapToLong(o -> o.quantity)
                                .sum();
                if (count > 0 && !combo)
                    errors.add(label(source) + ": COMBO_COMPONENT_WITHOUT_COMBO");
                if (combo
                        && Boolean.TRUE.equals(group.enabled)
                        && Boolean.TRUE.equals(group.required)
                        && count == 0)
                    errors.add(label(source) + ": REQUIRED_" + group.group_code + "_MISSING");
                if (count > 1 && !"MULTIPLE".equals(group.selection_rule))
                    errors.add(label(source) + ": COMBO_SELECTION_INVALID");
            }
        for (var option : line.options)
            if (option.parent_option_id_snapshot != null
                    && line.options.stream()
                            .noneMatch(o -> o.option_id.equals(option.parent_option_id_snapshot)))
                errors.add(label(source) + ": CHILD_WITHOUT_PARENT");
    }

    public List<Choice> choices(
            MenuCatalogResponse catalog, MenuCatalogResponse.ItemResponse item) {
        List<Choice> result = new ArrayList<>();
        for (var option : item.options) {
            if (!Boolean.TRUE.equals(option.is_active)
                    || Set.of("COMBO_EGG", "COMBO_SIDE").contains(empty(option.option_group)))
                continue;
            result.add(choice(option, null, null));
        }
        boolean allowCombo = result.stream().anyMatch(c -> "COMBO".equals(c.group()));
        if (allowCombo && catalog.combo_configuration != null)
            for (var group : catalog.combo_configuration.groups) {
                if (!Boolean.TRUE.equals(group.enabled)) continue;
                for (var component : group.components) {
                    if (!Boolean.TRUE.equals(component.enabled)) continue;
                    Long id =
                            componentOptionId(component.component_group, component.component_code);
                    result.add(
                            new Choice(
                                    id,
                                    component.component_code,
                                    component.component_group,
                                    "addon",
                                    component.name_zh,
                                    component.name_en,
                                    BigDecimal.ZERO,
                                    null,
                                    null));
                    if ("COMBO_SIDE".equals(component.component_group)) {
                        // Stable Store-linked identity first; legacy SKU compatibility comes from
                        // the existing catalog contract.
                        String sku = component.linked_menu_item_sku;
                        if (sku == null)
                            sku =
                                    switch (component.component_code) {
                                        case "combo_edamame" -> "edamame";
                                        case "combo_shredded_potato" -> "shredded_potato";
                                        case "combo_cucumber_salad" -> "cucumber_salad";
                                        default -> "";
                                    };
                        final String sideSku = sku;
                        var sides =
                                allItems(catalog).stream()
                                        .filter(
                                                i ->
                                                        component.linked_menu_item_id != null
                                                                ? component.linked_menu_item_id
                                                                        .equals(i.id)
                                                                : sideSku.equals(i.sku))
                                        .toList();
                        if (sides.size() == 1)
                            for (var remove : sides.get(0).options)
                                if (Boolean.TRUE.equals(remove.is_active)
                                        && ("REMOVE".equals(remove.option_group)
                                                || "remove".equals(remove.option_type)))
                                    result.add(choice(remove, id, component.component_code));
                    }
                }
            }
        return result;
    }

    private Choice choice(MenuCatalogResponse.OptionResponse o, Long parent, String parentCode) {
        String group = parent != null ? "COMBO_SIDE_REMOVE" : o.option_group;
        if (group == null)
            group =
                    switch (empty(o.option_type)) {
                        case "size" -> "SIZE";
                        case "noodle_type" -> "NOODLE_TYPE";
                        case "soup_base" -> "SOUP_BASE";
                        case "spicy_level" -> "SPICY_LEVEL";
                        case "remove" -> "REMOVE";
                        case "addon" -> "ADD_ON";
                        default -> "";
                    };
        return new Choice(
                o.id,
                o.option_code,
                group,
                o.option_type,
                o.name_zh,
                o.name_en,
                o.price_delta,
                parent != null ? parent : o.parent_option_id,
                parentCode);
    }

    public static long componentOptionId(String group, String code) {
        String key =
                group.trim().toUpperCase(Locale.ROOT) + ":" + code.trim().toLowerCase(Locale.ROOT);
        Long legacy =
                Map.of(
                                "COMBO_EGG:combo_tea_egg",
                                -20101L,
                                "COMBO_EGG:combo_fried_egg",
                                -20102L,
                                "COMBO_SIDE:combo_edamame",
                                -20201L,
                                "COMBO_SIDE:combo_shredded_potato",
                                -20202L,
                                "COMBO_SIDE:combo_cucumber_salad",
                                -20203L)
                        .get(key);
        if (legacy != null) return legacy;
        int hash = 0x811c9dc5;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x01000193;
        }
        return -300000L - (Integer.toUnsignedLong(hash) % 600000);
    }

    public List<MenuCatalogResponse.ItemResponse> allItems(MenuCatalogResponse catalog) {
        return catalog.categories.stream().flatMap(c -> c.items.stream()).toList();
    }

    private UberEatsMenuMapping resolve(
            List<UberEatsMenuMapping> rules,
            String kind,
            String root,
            UberOrderSnapshot.Item source) {
        for (String type : List.of("EXTERNAL_DATA", "ID")) {
            String key = "ID".equals(type) ? source.id() : source.external_data();
            if (key.isBlank()) continue;
            var matches =
                    rules.stream()
                            .filter(
                                    r ->
                                            kind.equals(r.kind)
                                                    && root.equals(r.uberItemId)
                                                    && type.equals(r.identifierType)
                                                    && key.equals(r.uberIdentifier))
                            .toList();
            if (matches.size() == 1) return matches.get(0);
        }
        return null;
    }

    private boolean stableItemMatch(
            MenuCatalogResponse.ItemResponse i, UberOrderSnapshot.Item source) {
        return !source.external_data().isBlank() && source.external_data().equals(i.sku);
    }

    private void addIssues(List<String> errors, UberOrderSnapshot.Item item) {
        item.issues().forEach(issue -> errors.add(label(item) + ": " + issue));
    }

    private String label(UberOrderSnapshot.Item item) {
        return item.title() + " [" + item.id() + "]";
    }

    private static String empty(String s) {
        return s == null ? "" : s;
    }

    private String joinNotes(String a, String b) {
        return a == null || a.isBlank() ? empty(b) : b == null || b.isBlank() ? a : a + " | " + b;
    }
}
