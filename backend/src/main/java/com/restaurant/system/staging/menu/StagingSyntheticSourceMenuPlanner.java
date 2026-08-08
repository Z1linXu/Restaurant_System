package com.restaurant.system.staging.menu;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Pure canonical planner and fingerprint generator for the reviewed synthetic manifest. */
@Component
@Profile("staging-synthetic-bootstrap")
public final class StagingSyntheticSourceMenuPlanner {

    private static final String FINGERPRINT_VERSION = "STG005_SOURCE_MENU_SHA256_V1";

    private static final Comparator<StagingSyntheticSourceMenuManifest.Category> CATEGORY_ORDER = Comparator
        .comparingInt(StagingSyntheticSourceMenuManifest.Category::sortOrder)
        .thenComparing(StagingSyntheticSourceMenuManifest.Category::code);
    private static final Comparator<StagingSyntheticSourceMenuManifest.Station> STATION_ORDER = Comparator
        .comparingInt(StagingSyntheticSourceMenuManifest.Station::sortOrder)
        .thenComparing(StagingSyntheticSourceMenuManifest.Station::code);
    private static final Comparator<StagingSyntheticSourceMenuManifest.Item> ITEM_ORDER = Comparator
        .comparingInt(StagingSyntheticSourceMenuManifest.Item::sortOrder)
        .thenComparing(StagingSyntheticSourceMenuManifest.Item::sku);
    private static final Comparator<StagingSyntheticSourceMenuManifest.Option> OPTION_ORDER = Comparator
        .comparingInt(StagingSyntheticSourceMenuManifest.Option::sortOrder)
        .thenComparing(StagingSyntheticSourceMenuManifest.Option::optionCode);

    private final StagingSyntheticSourceMenuManifestValidator validator;

    public StagingSyntheticSourceMenuPlanner() {
        this(new StagingSyntheticSourceMenuManifestValidator());
    }

    StagingSyntheticSourceMenuPlanner(StagingSyntheticSourceMenuManifestValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public StagingSyntheticSourceMenuPlan plan(StagingSyntheticSourceMenuManifest manifest) {
        validator.validate(manifest);

        Map<String, StagingSyntheticSourceMenuManifest.Station> stationsByCode = manifest.stations().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                StagingSyntheticSourceMenuManifest.Station::code,
                station -> station
            ));
        Map<String, List<StagingSyntheticSourceMenuManifest.Option>> optionsByItem = manifest.options().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                StagingSyntheticSourceMenuManifest.Option::itemSku,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));

        List<StagingSyntheticSourceMenuPlan.CategoryPlan> categoryPlans = manifest.categories().stream()
            .sorted(CATEGORY_ORDER)
            .map(category -> categoryPlan(category, manifest.items(), stationsByCode, optionsByItem))
            .toList();

        return new StagingSyntheticSourceMenuPlan(
            manifest.manifestCode(),
            manifest.manifestVersion(),
            fingerprint(manifest),
            manifest.categories().size(),
            manifest.stations().size(),
            manifest.items().size(),
            manifest.options().size(),
            categoryPlans
        );
    }

    private StagingSyntheticSourceMenuPlan.CategoryPlan categoryPlan(
        StagingSyntheticSourceMenuManifest.Category category,
        List<StagingSyntheticSourceMenuManifest.Item> allItems,
        Map<String, StagingSyntheticSourceMenuManifest.Station> stationsByCode,
        Map<String, List<StagingSyntheticSourceMenuManifest.Option>> optionsByItem
    ) {
        List<StagingSyntheticSourceMenuManifest.Item> categoryItems = allItems.stream()
            .filter(item -> item.categoryCode().equals(category.code()))
            .toList();
        List<StagingSyntheticSourceMenuPlan.StationPlan> stationPlans = categoryItems.stream()
            .map(StagingSyntheticSourceMenuManifest.Item::stationCode)
            .distinct()
            .map(stationsByCode::get)
            .sorted(STATION_ORDER)
            .map(station -> stationPlan(station, categoryItems, optionsByItem))
            .toList();
        return new StagingSyntheticSourceMenuPlan.CategoryPlan(category, stationPlans);
    }

    private StagingSyntheticSourceMenuPlan.StationPlan stationPlan(
        StagingSyntheticSourceMenuManifest.Station station,
        List<StagingSyntheticSourceMenuManifest.Item> categoryItems,
        Map<String, List<StagingSyntheticSourceMenuManifest.Option>> optionsByItem
    ) {
        List<StagingSyntheticSourceMenuPlan.ItemPlan> itemPlans = categoryItems.stream()
            .filter(item -> item.stationCode().equals(station.code()))
            .sorted(ITEM_ORDER)
            .map(item -> new StagingSyntheticSourceMenuPlan.ItemPlan(
                item,
                optionTree(optionsByItem.getOrDefault(item.sku(), List.of()))
            ))
            .toList();
        return new StagingSyntheticSourceMenuPlan.StationPlan(station, itemPlans);
    }

    private List<StagingSyntheticSourceMenuPlan.OptionPlan> optionTree(
        List<StagingSyntheticSourceMenuManifest.Option> options
    ) {
        Map<String, List<StagingSyntheticSourceMenuManifest.Option>> childrenByParent = options.stream()
            .filter(option -> option.parentOptionCode() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                StagingSyntheticSourceMenuManifest.Option::parentOptionCode,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
            ));
        return options.stream()
            .filter(option -> option.parentOptionCode() == null)
            .sorted(OPTION_ORDER)
            .map(option -> optionPlan(option, childrenByParent))
            .toList();
    }

    private StagingSyntheticSourceMenuPlan.OptionPlan optionPlan(
        StagingSyntheticSourceMenuManifest.Option option,
        Map<String, List<StagingSyntheticSourceMenuManifest.Option>> childrenByParent
    ) {
        List<StagingSyntheticSourceMenuPlan.OptionPlan> children = childrenByParent
            .getOrDefault(option.optionCode(), List.of()).stream()
            .sorted(OPTION_ORDER)
            .map(child -> optionPlan(child, childrenByParent))
            .toList();
        return new StagingSyntheticSourceMenuPlan.OptionPlan(option, children);
    }

    private String fingerprint(StagingSyntheticSourceMenuManifest manifest) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "fingerprintVersion", FINGERPRINT_VERSION);
        append(canonical, "manifestCode", manifest.manifestCode());
        append(canonical, "manifestVersion", manifest.manifestVersion());
        append(canonical, "topologyNamespace", manifest.topologyNamespace());
        manifest.categories().stream().sorted(CATEGORY_ORDER).forEach(category -> {
            append(canonical, "category.code", category.code());
            append(canonical, "category.nameZh", category.nameZh());
            append(canonical, "category.nameEn", category.nameEn());
            append(canonical, "category.active", category.active());
            append(canonical, "category.sortOrder", category.sortOrder());
        });
        manifest.stations().stream().sorted(STATION_ORDER).forEach(station -> {
            append(canonical, "station.code", station.code());
            append(canonical, "station.name", station.name());
            append(canonical, "station.active", station.active());
            append(canonical, "station.sortOrder", station.sortOrder());
        });
        manifest.items().stream().sorted(Comparator
            .comparing(StagingSyntheticSourceMenuManifest.Item::sku))
            .forEach(item -> {
                append(canonical, "item.sku", item.sku());
                append(canonical, "item.categoryCode", item.categoryCode());
                append(canonical, "item.stationCode", item.stationCode());
                append(canonical, "item.itemType", item.itemType());
                append(canonical, "item.nameZh", item.nameZh());
                append(canonical, "item.nameEn", item.nameEn());
                append(canonical, "item.basePrice", money(item.basePrice()));
                append(canonical, "item.costPerItem", money(item.costPerItem()));
                append(canonical, "item.active", item.active());
                append(canonical, "item.soldOut", item.soldOut());
                append(canonical, "item.sortOrder", item.sortOrder());
            });
        manifest.options().stream().sorted(Comparator
            .comparing(StagingSyntheticSourceMenuManifest.Option::itemSku)
            .thenComparing(StagingSyntheticSourceMenuManifest.Option::optionCode))
            .forEach(option -> {
                append(canonical, "option.itemSku", option.itemSku());
                append(canonical, "option.type", option.optionType());
                append(canonical, "option.group", option.optionGroup());
                append(canonical, "option.code", option.optionCode());
                append(canonical, "option.parent", option.parentOptionCode());
                append(canonical, "option.nameZh", option.nameZh());
                append(canonical, "option.nameEn", option.nameEn());
                append(canonical, "option.price", money(option.priceDelta()));
                append(canonical, "option.active", option.active());
                append(canonical, "option.sortOrder", option.sortOrder());
            });
        return sha256(canonical.toString());
    }

    private void append(StringBuilder target, String key, Object rawValue) {
        String value = rawValue == null ? "<null>" : String.valueOf(rawValue);
        target.append(key.length()).append(':').append(key)
            .append(value.length()).append(':').append(value);
    }

    private String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
