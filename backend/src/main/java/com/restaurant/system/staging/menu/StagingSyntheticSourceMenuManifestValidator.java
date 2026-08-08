package com.restaurant.system.staging.menu;

import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.SourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.StationSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.StationSourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionApplication;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionRule;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fail-closed structural and AL-003 source-contract validation without database identities. */
public final class StagingSyntheticSourceMenuManifestValidator {

    private static final String CODE_PATTERN = "[A-Za-z0-9_]+";

    private final ChinatownMenuCloneProfile profile;

    public StagingSyntheticSourceMenuManifestValidator() {
        this(new ChinatownMenuCloneProfile());
    }

    StagingSyntheticSourceMenuManifestValidator(ChinatownMenuCloneProfile profile) {
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public void validate(StagingSyntheticSourceMenuManifest manifest) {
        if (manifest == null) {
            throw invalid("STG005_MENU_MANIFEST_REQUIRED", "Synthetic source menu manifest is required");
        }
        requireExactIdentity(
            manifest.manifestCode(),
            StagingSyntheticSourceMenuManifestFactory.MANIFEST_CODE,
            "STG005_MENU_MANIFEST_CODE_INVALID"
        );
        requireExactIdentity(
            manifest.manifestVersion(),
            StagingSyntheticSourceMenuManifestFactory.MANIFEST_VERSION,
            "STG005_MENU_MANIFEST_VERSION_INVALID"
        );
        requireExactIdentity(
            manifest.topologyNamespace(),
            StagingSyntheticSourceMenuManifestFactory.TOPOLOGY_NAMESPACE,
            "STG005_MENU_NAMESPACE_INVALID"
        );

        Map<String, StagingSyntheticSourceMenuManifest.Category> categories = categories(manifest.categories());
        Map<String, StagingSyntheticSourceMenuManifest.Station> stations = stations(manifest.stations());
        Map<String, StagingSyntheticSourceMenuManifest.Item> items = items(manifest.items(), categories, stations);
        options(manifest.options(), items);
        validateProfileCompatibility(categories, stations, items, manifest.options());
    }

    private Map<String, StagingSyntheticSourceMenuManifest.Category> categories(
        List<StagingSyntheticSourceMenuManifest.Category> values
    ) {
        Map<String, StagingSyntheticSourceMenuManifest.Category> result = new LinkedHashMap<>();
        for (StagingSyntheticSourceMenuManifest.Category category : values) {
            if (category == null) {
                throw invalid("STG005_MENU_CATEGORY_INVALID", "Category cannot be null");
            }
            requireSemanticCode(category.code(), "STG005_MENU_CATEGORY_CODE_INVALID");
            requireSyntheticName(category.nameZh(), "STG005_MENU_CATEGORY_NAME_INVALID");
            requireSyntheticName(category.nameEn(), "STG005_MENU_CATEGORY_NAME_INVALID");
            requireActive(category.active(), "STG005_MENU_CATEGORY_INACTIVE");
            requirePositiveOrder(category.sortOrder(), "STG005_MENU_CATEGORY_ORDER_INVALID");
            if (result.putIfAbsent(category.code(), category) != null) {
                throw invalid("STG005_MENU_CATEGORY_DUPLICATE", "Category codes must be unique");
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, StagingSyntheticSourceMenuManifest.Station> stations(
        List<StagingSyntheticSourceMenuManifest.Station> values
    ) {
        Map<String, StagingSyntheticSourceMenuManifest.Station> result = new LinkedHashMap<>();
        for (StagingSyntheticSourceMenuManifest.Station station : values) {
            if (station == null) {
                throw invalid("STG005_MENU_STATION_INVALID", "Station cannot be null");
            }
            requireSemanticCode(station.code(), "STG005_MENU_STATION_CODE_INVALID");
            requireSyntheticName(station.name(), "STG005_MENU_STATION_NAME_INVALID");
            requireActive(station.active(), "STG005_MENU_STATION_INACTIVE");
            requirePositiveOrder(station.sortOrder(), "STG005_MENU_STATION_ORDER_INVALID");
            if (result.putIfAbsent(station.code(), station) != null) {
                throw invalid("STG005_MENU_STATION_DUPLICATE", "Station codes must be unique");
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, StagingSyntheticSourceMenuManifest.Item> items(
        List<StagingSyntheticSourceMenuManifest.Item> values,
        Map<String, StagingSyntheticSourceMenuManifest.Category> categories,
        Map<String, StagingSyntheticSourceMenuManifest.Station> stations
    ) {
        Map<String, StagingSyntheticSourceMenuManifest.Item> result = new LinkedHashMap<>();
        for (StagingSyntheticSourceMenuManifest.Item item : values) {
            if (item == null) {
                throw invalid("STG005_MENU_ITEM_INVALID", "Item cannot be null");
            }
            requireSemanticCode(item.sku(), "STG005_MENU_ITEM_SKU_INVALID");
            requireSemanticCode(item.categoryCode(), "STG005_MENU_ITEM_CATEGORY_INVALID");
            requireSemanticCode(item.stationCode(), "STG005_MENU_ITEM_STATION_INVALID");
            requireSemanticCode(item.itemType(), "STG005_MENU_ITEM_TYPE_INVALID");
            requireSyntheticName(item.nameZh(), "STG005_MENU_ITEM_NAME_INVALID");
            requireSyntheticName(item.nameEn(), "STG005_MENU_ITEM_NAME_INVALID");
            requireMoney(item.basePrice(), "STG005_MENU_ITEM_PRICE_INVALID");
            requireMoney(item.costPerItem(), "STG005_MENU_ITEM_COST_INVALID");
            requireActive(item.active(), "STG005_MENU_ITEM_INACTIVE");
            if (item.soldOut()) {
                throw invalid("STG005_MENU_ITEM_SOLD_OUT", "Synthetic source items cannot be sold out");
            }
            requirePositiveOrder(item.sortOrder(), "STG005_MENU_ITEM_ORDER_INVALID");
            if (!categories.containsKey(item.categoryCode())) {
                throw invalid("STG005_MENU_ITEM_CATEGORY_MISSING", "Item category reference is missing");
            }
            if (!stations.containsKey(item.stationCode())) {
                throw invalid("STG005_MENU_ITEM_STATION_MISSING", "Item station reference is missing");
            }
            if (result.putIfAbsent(item.sku(), item) != null) {
                throw invalid("STG005_MENU_ITEM_DUPLICATE", "Item SKUs must be unique");
            }
        }
        return Map.copyOf(result);
    }

    private void options(
        List<StagingSyntheticSourceMenuManifest.Option> values,
        Map<String, StagingSyntheticSourceMenuManifest.Item> items
    ) {
        Map<String, Map<String, StagingSyntheticSourceMenuManifest.Option>> byItem = new LinkedHashMap<>();
        Map<String, Set<String>> ownersByCode = new HashMap<>();
        for (StagingSyntheticSourceMenuManifest.Option option : values) {
            if (option == null) {
                throw invalid("STG005_MENU_OPTION_INVALID", "Option cannot be null");
            }
            requireSemanticCode(option.itemSku(), "STG005_MENU_OPTION_ITEM_INVALID");
            requireSemanticCode(option.optionType(), "STG005_MENU_OPTION_TYPE_INVALID");
            requireSemanticCode(option.optionGroup(), "STG005_MENU_OPTION_GROUP_INVALID");
            requireSemanticCode(option.optionCode(), "STG005_MENU_OPTION_CODE_INVALID");
            if (option.parentOptionCode() != null) {
                requireSemanticCode(option.parentOptionCode(), "STG005_MENU_OPTION_PARENT_INVALID");
            }
            requireSyntheticName(option.nameZh(), "STG005_MENU_OPTION_NAME_INVALID");
            requireSyntheticName(option.nameEn(), "STG005_MENU_OPTION_NAME_INVALID");
            requireMoney(option.priceDelta(), "STG005_MENU_OPTION_PRICE_INVALID");
            requireActive(option.active(), "STG005_MENU_OPTION_INACTIVE");
            requirePositiveOrder(option.sortOrder(), "STG005_MENU_OPTION_ORDER_INVALID");
            if (!items.containsKey(option.itemSku())) {
                throw invalid("STG005_MENU_OPTION_ITEM_MISSING", "Option item reference is missing");
            }
            Map<String, StagingSyntheticSourceMenuManifest.Option> itemOptions = byItem.computeIfAbsent(
                option.itemSku(),
                ignored -> new LinkedHashMap<>()
            );
            if (itemOptions.putIfAbsent(option.optionCode(), option) != null) {
                throw invalid("STG005_MENU_OPTION_DUPLICATE", "Option codes must be unique per item");
            }
            ownersByCode.computeIfAbsent(option.optionCode(), ignored -> new LinkedHashSet<>())
                .add(option.itemSku());
        }

        for (Map.Entry<String, Map<String, StagingSyntheticSourceMenuManifest.Option>> entry : byItem.entrySet()) {
            validateParentReferences(entry.getKey(), entry.getValue(), ownersByCode);
            validateNoParentCycles(entry.getValue());
        }
    }

    private void validateParentReferences(
        String itemSku,
        Map<String, StagingSyntheticSourceMenuManifest.Option> itemOptions,
        Map<String, Set<String>> ownersByCode
    ) {
        for (StagingSyntheticSourceMenuManifest.Option option : itemOptions.values()) {
            String parentCode = option.parentOptionCode();
            if (parentCode == null) {
                continue;
            }
            if (parentCode.equals(option.optionCode())) {
                throw invalid("STG005_MENU_OPTION_PARENT_SELF", "Option cannot reference itself as parent");
            }
            if (!itemOptions.containsKey(parentCode)) {
                Set<String> owners = ownersByCode.getOrDefault(parentCode, Set.of());
                if (!owners.isEmpty() && !owners.contains(itemSku)) {
                    throw invalid(
                        "STG005_MENU_OPTION_PARENT_CROSS_ITEM",
                        "Option parent cannot belong to another item"
                    );
                }
                throw invalid("STG005_MENU_OPTION_PARENT_MISSING", "Option parent reference is missing");
            }
        }
    }

    private void validateNoParentCycles(Map<String, StagingSyntheticSourceMenuManifest.Option> options) {
        Map<String, VisitState> states = new HashMap<>();
        for (String optionCode : options.keySet()) {
            visit(optionCode, options, states);
        }
    }

    private void visit(
        String optionCode,
        Map<String, StagingSyntheticSourceMenuManifest.Option> options,
        Map<String, VisitState> states
    ) {
        VisitState state = states.get(optionCode);
        if (state == VisitState.VISITING) {
            throw invalid("STG005_MENU_OPTION_PARENT_CYCLE", "Option parent graph contains a cycle");
        }
        if (state == VisitState.VISITED) {
            return;
        }
        states.put(optionCode, VisitState.VISITING);
        String parent = options.get(optionCode).parentOptionCode();
        if (parent != null) {
            visit(parent, options, states);
        }
        states.put(optionCode, VisitState.VISITED);
    }

    private void validateProfileCompatibility(
        Map<String, StagingSyntheticSourceMenuManifest.Category> categories,
        Map<String, StagingSyntheticSourceMenuManifest.Station> stations,
        Map<String, StagingSyntheticSourceMenuManifest.Item> items,
        List<StagingSyntheticSourceMenuManifest.Option> options
    ) {
        Set<String> requiredCategories = profile.categories().stream()
            .filter(selection -> selection.sourcePolicy() == CategorySourcePolicy.REQUIRED_SOURCE_CODE)
            .map(CategorySelection::sourceCode)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!categories.keySet().containsAll(requiredCategories)) {
            throw invalid("STG005_MENU_PROFILE_CATEGORY_MISSING", "Required source category is missing");
        }

        Set<String> requiredStations = profile.stations().stream()
            .filter(selection -> selection.sourcePolicy() == StationSourcePolicy.REQUIRED_SOURCE_CODE)
            .map(StationSelection::sourceCode)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!stations.keySet().containsAll(requiredStations)) {
            throw invalid("STG005_MENU_PROFILE_STATION_MISSING", "Required source station is missing");
        }

        List<ItemSelection> requiredSelections = profile.items().stream()
            .filter(selection -> selection.sourcePolicy() == SourcePolicy.REQUIRED_SOURCE_CODE)
            .toList();
        for (ItemSelection selection : requiredSelections) {
            StagingSyntheticSourceMenuManifest.Item source = items.get(selection.sourceSku());
            if (source == null) {
                throw invalid("STG005_MENU_PROFILE_SOURCE_SKU_MISSING", "Required source SKU is missing");
            }
            if (selection.roles().contains(ItemRole.NOODLE)
                && !source.stationCode().equals(ChinatownMenuCloneProfile.NOODLE_STATION)) {
                throw invalid("STG005_MENU_PROFILE_NOODLE_STATION_INVALID", "Noodle source station is invalid");
            }
            if (selection.roles().contains(ItemRole.SIDE_DISH)
                && !source.stationCode().equals(ChinatownMenuCloneProfile.COLD_STATION)) {
                throw invalid("STG005_MENU_PROFILE_SIDE_STATION_INVALID", "Side source station is invalid");
            }
        }

        Set<String> drinkStations = requiredSelections.stream()
            .filter(selection -> selection.roles().contains(ItemRole.DRINK))
            .map(ItemSelection::sourceSku)
            .map(items::get)
            .map(StagingSyntheticSourceMenuManifest.Item::stationCode)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (drinkStations.size() != 1) {
            throw invalid("STG005_MENU_PROFILE_DRINK_STATION_INVALID", "Source drinks require one active station");
        }

        Map<String, Set<OptionSelector>> selectorsBySourceSku = sourceOptionSelectors();
        for (StagingSyntheticSourceMenuManifest.Option option : options) {
            Set<OptionSelector> selectors = selectorsBySourceSku.get(option.itemSku());
            if (selectors == null || !selectors.contains(new OptionSelector(option.optionType(), option.optionGroup()))) {
                throw invalid(
                    "STG005_MENU_PROFILE_OPTION_UNCLASSIFIED",
                    "Active source option is not classified by the Chinatown profile"
                );
            }
        }
    }

    private Map<String, Set<OptionSelector>> sourceOptionSelectors() {
        Map<String, Set<OptionSelector>> result = new LinkedHashMap<>();
        for (SourceOptionApplication application : profile.sourceOptionApplications()) {
            Set<OptionSelector> selectors = application.rules().stream()
                .map(rule -> new OptionSelector(rule.optionType(), rule.optionGroup()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            result.put(application.sourceItemSku(), selectors);
        }
        return Map.copyOf(result);
    }

    private void requireExactIdentity(String actual, String expected, String errorCode) {
        if (!Objects.equals(actual, expected)) {
            throw invalid(errorCode, "Manifest identity must match the reviewed exact value");
        }
    }

    private void requireSemanticCode(String value, String errorCode) {
        if (value == null || !value.matches(CODE_PATTERN) || !value.equals(value.trim())) {
            throw invalid(errorCode, "Technical codes must be exact nonblank semantic identifiers");
        }
    }

    private void requireSyntheticName(String value, String errorCode) {
        if (value == null
            || !value.equals(value.trim())
            || !value.startsWith(StagingSyntheticSourceMenuManifestFactory.TOPOLOGY_NAMESPACE)) {
            throw invalid(errorCode, "Synthetic display names must use the STG005_ marker");
        }
    }

    private void requireMoney(BigDecimal value, String errorCode) {
        if (value == null || value.signum() < 0 || value.scale() > 2) {
            throw invalid(errorCode, "Synthetic monetary values must be nonnegative with at most two decimals");
        }
    }

    private void requireActive(boolean active, String errorCode) {
        if (!active) {
            throw invalid(errorCode, "Synthetic source graph records must be active");
        }
    }

    private void requirePositiveOrder(int sortOrder, String errorCode) {
        if (sortOrder < 1) {
            throw invalid(errorCode, "Synthetic source graph sort order must be positive");
        }
    }

    private ValidationException invalid(String errorCode, String message) {
        return new ValidationException(errorCode, message);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }

    private record OptionSelector(String optionType, String optionGroup) {
    }

    public static final class ValidationException extends IllegalArgumentException {

        private final String errorCode;

        private ValidationException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public String errorCode() {
            return errorCode;
        }
    }
}
