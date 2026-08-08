package com.restaurant.system.staging.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class StagingSyntheticSourceMenuManifestValidatorTest {

    private final StagingSyntheticSourceMenuManifestFactory factory =
        new StagingSyntheticSourceMenuManifestFactory();
    private final StagingSyntheticSourceMenuManifestValidator validator =
        new StagingSyntheticSourceMenuManifestValidator();

    @Test
    void acceptsCanonicalManifestAndProfileClassification() {
        assertThatCode(() -> validator.validate(factory.create())).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonExactIdentityAndVersion() {
        StagingSyntheticSourceMenuManifest original = factory.create();

        assertRejected(copy(original, " " + original.manifestCode(), null, null, null, null),
            "STG005_MENU_MANIFEST_CODE_INVALID");
        assertRejected(copy(original, null, original.manifestVersion() + " ", null, null, null),
            "STG005_MENU_MANIFEST_VERSION_INVALID");
    }

    @Test
    void rejectsDuplicateCategoryStationItemAndOptionIdentifiers() {
        StagingSyntheticSourceMenuManifest original = factory.create();

        List<StagingSyntheticSourceMenuManifest.Category> categories = new ArrayList<>(original.categories());
        categories.add(original.categories().get(0));
        assertRejected(copy(original, null, null, categories, null, null), "STG005_MENU_CATEGORY_DUPLICATE");

        List<StagingSyntheticSourceMenuManifest.Station> stations = new ArrayList<>(original.stations());
        stations.add(original.stations().get(0));
        assertRejected(copy(original, null, null, null, stations, null), "STG005_MENU_STATION_DUPLICATE");

        List<StagingSyntheticSourceMenuManifest.Item> items = new ArrayList<>(original.items());
        items.add(original.items().get(0));
        assertRejected(copy(original, null, null, null, null, items), "STG005_MENU_ITEM_DUPLICATE");

        List<StagingSyntheticSourceMenuManifest.Option> options = new ArrayList<>(original.options());
        options.add(original.options().get(0));
        assertRejected(copyOptions(original, options), "STG005_MENU_OPTION_DUPLICATE");
    }

    @Test
    void rejectsMissingRequiredSourceSku() {
        StagingSyntheticSourceMenuManifest original = factory.create();
        List<StagingSyntheticSourceMenuManifest.Item> items = original.items().stream()
            .filter(item -> !item.sku().equals("traditional_beef_noodle"))
            .toList();
        List<StagingSyntheticSourceMenuManifest.Option> options = original.options().stream()
            .filter(option -> !option.itemSku().equals("traditional_beef_noodle"))
            .toList();

        assertRejected(copy(original, null, null, null, null, items, options),
            "STG005_MENU_PROFILE_SOURCE_SKU_MISSING");
    }

    @Test
    void rejectsMissingCategoryAndStationReferences() {
        StagingSyntheticSourceMenuManifest original = factory.create();
        assertRejected(replaceItem(original, "traditional_beef_noodle", item -> item(
            item, "MISSING_CATEGORY", item.stationCode()
        )), "STG005_MENU_ITEM_CATEGORY_MISSING");
        assertRejected(replaceItem(original, "traditional_beef_noodle", item -> item(
            item, item.categoryCode(), "MISSING_STATION"
        )), "STG005_MENU_ITEM_STATION_MISSING");
    }

    @Test
    void rejectsSelfMissingCrossItemAndCyclicParents() {
        StagingSyntheticSourceMenuManifest original = factory.create();
        StagingSyntheticSourceMenuManifest.Option self = option(
            "traditional_beef_noodle", "self_parent", "self_parent"
        );
        assertRejected(withOptions(original, self), "STG005_MENU_OPTION_PARENT_SELF");

        StagingSyntheticSourceMenuManifest.Option missing = option(
            "traditional_beef_noodle", "missing_parent_child", "not_present"
        );
        assertRejected(withOptions(original, missing), "STG005_MENU_OPTION_PARENT_MISSING");

        StagingSyntheticSourceMenuManifest.Option foreign = option("cucumber_salad", "foreign_parent", null);
        StagingSyntheticSourceMenuManifest.Option cross = option(
            "traditional_beef_noodle", "cross_child", "foreign_parent"
        );
        assertRejected(withOptions(original, foreign, cross), "STG005_MENU_OPTION_PARENT_CROSS_ITEM");

        StagingSyntheticSourceMenuManifest.Option first = option("traditional_beef_noodle", "cycle_a", "cycle_b");
        StagingSyntheticSourceMenuManifest.Option second = option("traditional_beef_noodle", "cycle_b", "cycle_a");
        assertRejected(withOptions(original, first, second), "STG005_MENU_OPTION_PARENT_CYCLE");
    }

    @Test
    void rejectsUnclassifiedActiveSourceOption() {
        StagingSyntheticSourceMenuManifest original = factory.create();
        StagingSyntheticSourceMenuManifest.Option unsupported = new StagingSyntheticSourceMenuManifest.Option(
            "traditional_beef_noodle",
            "unknown",
            "UNKNOWN",
            "unknown_option",
            null,
            "STG005_unknown_option",
            "STG005_unknown_option",
            java.math.BigDecimal.ZERO.setScale(2),
            true,
            999
        );

        assertRejected(withOptions(original, unsupported), "STG005_MENU_PROFILE_OPTION_UNCLASSIFIED");
    }

    private StagingSyntheticSourceMenuManifest replaceItem(
        StagingSyntheticSourceMenuManifest original,
        String sku,
        UnaryOperator<StagingSyntheticSourceMenuManifest.Item> replacement
    ) {
        List<StagingSyntheticSourceMenuManifest.Item> items = original.items().stream()
            .map(item -> item.sku().equals(sku) ? replacement.apply(item) : item)
            .toList();
        return copy(original, null, null, null, null, items);
    }

    private StagingSyntheticSourceMenuManifest.Item item(
        StagingSyntheticSourceMenuManifest.Item source,
        String categoryCode,
        String stationCode
    ) {
        return new StagingSyntheticSourceMenuManifest.Item(
            source.sku(),
            categoryCode,
            stationCode,
            source.itemType(),
            source.nameZh(),
            source.nameEn(),
            source.basePrice(),
            source.costPerItem(),
            source.active(),
            source.soldOut(),
            source.sortOrder()
        );
    }

    private StagingSyntheticSourceMenuManifest.Option option(
        String itemSku,
        String optionCode,
        String parentOptionCode
    ) {
        return new StagingSyntheticSourceMenuManifest.Option(
            itemSku,
            "addon",
            "ADD_ON",
            optionCode,
            parentOptionCode,
            "STG005_" + optionCode,
            "STG005_" + optionCode,
            java.math.BigDecimal.ZERO.setScale(2),
            true,
            999
        );
    }

    private StagingSyntheticSourceMenuManifest withOptions(
        StagingSyntheticSourceMenuManifest original,
        StagingSyntheticSourceMenuManifest.Option... additions
    ) {
        List<StagingSyntheticSourceMenuManifest.Option> options = new ArrayList<>(original.options());
        options.addAll(List.of(additions));
        return copyOptions(original, options);
    }

    private StagingSyntheticSourceMenuManifest copyOptions(
        StagingSyntheticSourceMenuManifest original,
        List<StagingSyntheticSourceMenuManifest.Option> options
    ) {
        return copy(original, null, null, null, null, null, options);
    }

    private StagingSyntheticSourceMenuManifest copy(
        StagingSyntheticSourceMenuManifest original,
        String manifestCode,
        String manifestVersion,
        List<StagingSyntheticSourceMenuManifest.Category> categories,
        List<StagingSyntheticSourceMenuManifest.Station> stations,
        List<StagingSyntheticSourceMenuManifest.Item> items
    ) {
        return copy(original, manifestCode, manifestVersion, categories, stations, items, null);
    }

    private StagingSyntheticSourceMenuManifest copy(
        StagingSyntheticSourceMenuManifest original,
        String manifestCode,
        String manifestVersion,
        List<StagingSyntheticSourceMenuManifest.Category> categories,
        List<StagingSyntheticSourceMenuManifest.Station> stations,
        List<StagingSyntheticSourceMenuManifest.Item> items,
        List<StagingSyntheticSourceMenuManifest.Option> options
    ) {
        return new StagingSyntheticSourceMenuManifest(
            manifestCode == null ? original.manifestCode() : manifestCode,
            manifestVersion == null ? original.manifestVersion() : manifestVersion,
            original.topologyNamespace(),
            categories == null ? original.categories() : categories,
            stations == null ? original.stations() : stations,
            items == null ? original.items() : items,
            options == null ? original.options() : options
        );
    }

    private void assertRejected(StagingSyntheticSourceMenuManifest manifest, String errorCode) {
        assertThatThrownBy(() -> validator.validate(manifest))
            .isInstanceOfSatisfying(
                StagingSyntheticSourceMenuManifestValidator.ValidationException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(errorCode)
            );
    }
}
