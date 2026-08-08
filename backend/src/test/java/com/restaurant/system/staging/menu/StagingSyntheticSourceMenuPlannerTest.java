package com.restaurant.system.staging.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class StagingSyntheticSourceMenuPlannerTest {

    private final StagingSyntheticSourceMenuManifestFactory factory =
        new StagingSyntheticSourceMenuManifestFactory();
    private final StagingSyntheticSourceMenuPlanner planner = new StagingSyntheticSourceMenuPlanner();

    @Test
    void returnsCanonicalCountsAndStableFingerprintAcrossInputOrder() {
        StagingSyntheticSourceMenuManifest original = factory.create();
        StagingSyntheticSourceMenuPlan first = planner.plan(original);
        StagingSyntheticSourceMenuPlan reordered = planner.plan(reversed(original));

        assertThat(first.categoryCount()).isEqualTo(4);
        assertThat(first.stationCount()).isEqualTo(3);
        assertThat(first.itemCount()).isEqualTo(13);
        assertThat(first.optionCount()).isEqualTo(38);
        assertThat(first.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(reordered.fingerprint()).isEqualTo(first.fingerprint());
        assertThat(reordered.categories()).isEqualTo(first.categories());
    }

    @Test
    void changingManifestContentChangesFingerprint() {
        StagingSyntheticSourceMenuManifest original = factory.create();
        List<StagingSyntheticSourceMenuManifest.Item> items = original.items().stream()
            .map(item -> item.sku().equals("traditional_beef_noodle")
                ? new StagingSyntheticSourceMenuManifest.Item(
                    item.sku(), item.categoryCode(), item.stationCode(), item.itemType(),
                    item.nameZh(), item.nameEn(), new BigDecimal("11.00"), item.costPerItem(), item.active(),
                    item.soldOut(), item.sortOrder()
                )
                : item)
            .toList();
        StagingSyntheticSourceMenuManifest changed = copy(original, original.categories(), original.stations(),
            items, original.options());

        assertThat(planner.plan(changed).fingerprint()).isNotEqualTo(planner.plan(original).fingerprint());
    }

    @Test
    void changingCloneRelevantCostChangesFingerprint() {
        StagingSyntheticSourceMenuManifest original = factory.create();
        List<StagingSyntheticSourceMenuManifest.Item> items = original.items().stream()
            .map(item -> item.sku().equals("traditional_beef_noodle")
                ? new StagingSyntheticSourceMenuManifest.Item(
                    item.sku(), item.categoryCode(), item.stationCode(), item.itemType(),
                    item.nameZh(), item.nameEn(), item.basePrice(), new BigDecimal("2.00"), item.active(),
                    item.soldOut(), item.sortOrder()
                )
                : item)
            .toList();

        StagingSyntheticSourceMenuManifest changed = copy(original, original.categories(), original.stations(),
            items, original.options());

        assertThat(planner.plan(changed).fingerprint()).isNotEqualTo(planner.plan(original).fingerprint());
    }

    @Test
    void createsParentBeforeChildHierarchyDeterministically() {
        StagingSyntheticSourceMenuManifest original = factory.create();
        List<StagingSyntheticSourceMenuManifest.Option> options = new ArrayList<>(original.options());
        options.add(option("parent_test", null, 200));
        options.add(option("child_b", "parent_test", 202));
        options.add(option("child_a", "parent_test", 201));
        StagingSyntheticSourceMenuPlan plan = planner.plan(copy(
            original, original.categories(), original.stations(), original.items(), options
        ));

        StagingSyntheticSourceMenuPlan.ItemPlan item = plan.categories().stream()
            .flatMap(category -> category.stations().stream())
            .flatMap(station -> station.items().stream())
            .filter(candidate -> candidate.item().sku().equals("traditional_beef_noodle"))
            .findFirst()
            .orElseThrow();
        StagingSyntheticSourceMenuPlan.OptionPlan parent = item.options().stream()
            .filter(candidate -> candidate.option().optionCode().equals("parent_test"))
            .findFirst()
            .orElseThrow();

        assertThat(parent.children())
            .extracting(child -> child.option().optionCode())
            .containsExactly("child_a", "child_b");
    }

    @Test
    void planningIsPureAndPlanCollectionsAreImmutable() {
        StagingSyntheticSourceMenuManifest manifest = factory.create();
        StagingSyntheticSourceMenuManifest before = copy(
            manifest, manifest.categories(), manifest.stations(), manifest.items(), manifest.options()
        );

        StagingSyntheticSourceMenuPlan plan = planner.plan(manifest);

        assertThat(manifest).isEqualTo(before);
        assertThatThrownBy(() -> plan.categories().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plan.categories().get(0).stations().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private StagingSyntheticSourceMenuManifest reversed(StagingSyntheticSourceMenuManifest original) {
        return copy(
            original,
            reversed(original.categories()),
            reversed(original.stations()),
            reversed(original.items()),
            reversed(original.options())
        );
    }

    private <T> List<T> reversed(List<T> values) {
        List<T> copy = new ArrayList<>(values);
        Collections.reverse(copy);
        return copy;
    }

    private StagingSyntheticSourceMenuManifest.Option option(
        String optionCode,
        String parentOptionCode,
        int sortOrder
    ) {
        return new StagingSyntheticSourceMenuManifest.Option(
            "traditional_beef_noodle",
            "addon",
            "ADD_ON",
            optionCode,
            parentOptionCode,
            "STG005_" + optionCode,
            "STG005_" + optionCode,
            BigDecimal.ZERO.setScale(2),
            true,
            sortOrder
        );
    }

    private StagingSyntheticSourceMenuManifest copy(
        StagingSyntheticSourceMenuManifest original,
        List<StagingSyntheticSourceMenuManifest.Category> categories,
        List<StagingSyntheticSourceMenuManifest.Station> stations,
        List<StagingSyntheticSourceMenuManifest.Item> items,
        List<StagingSyntheticSourceMenuManifest.Option> options
    ) {
        return new StagingSyntheticSourceMenuManifest(
            original.manifestCode(),
            original.manifestVersion(),
            original.topologyNamespace(),
            categories,
            stations,
            items,
            options
        );
    }
}
