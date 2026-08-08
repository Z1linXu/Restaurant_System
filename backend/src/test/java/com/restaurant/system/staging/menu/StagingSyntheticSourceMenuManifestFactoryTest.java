package com.restaurant.system.staging.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StagingSyntheticSourceMenuManifestFactoryTest {

    private final StagingSyntheticSourceMenuManifestFactory factory =
        new StagingSyntheticSourceMenuManifestFactory();

    @Test
    void createsReviewedSyntheticGraphWithCanonicalTechnicalIdentifiers() {
        StagingSyntheticSourceMenuManifest manifest = factory.create();

        assertThat(manifest.manifestCode())
            .isEqualTo(StagingSyntheticSourceMenuManifestFactory.MANIFEST_CODE);
        assertThat(manifest.manifestVersion())
            .isEqualTo(StagingSyntheticSourceMenuManifestFactory.MANIFEST_VERSION);
        assertThat(manifest.categories()).hasSize(4);
        assertThat(manifest.stations()).hasSize(3);
        assertThat(manifest.items()).hasSize(13);
        assertThat(manifest.options()).hasSize(38);
        assertThat(manifest.options())
            .filteredOn(option -> option.optionGroup().equals("NOODLE_TYPE"))
            .hasSize(35);
        assertThat(manifest.options())
            .filteredOn(option -> !option.optionGroup().equals("NOODLE_TYPE"))
            .extracting(StagingSyntheticSourceMenuManifest.Option::optionCode)
            .containsExactlyInAnyOrder("tea_egg", "extra_meat", "remove_garlic");
        assertThat(manifest.items())
            .extracting(StagingSyntheticSourceMenuManifest.Item::sku)
            .contains("traditional_beef_noodle", "dan_dan_noodle", "chinese_herbal_tea")
            .allSatisfy(sku -> assertThat(sku).doesNotStartWith("STG005_"));
        assertThat(manifest.items())
            .allSatisfy(item -> {
                assertThat(item.nameZh()).startsWith("STG005_");
                assertThat(item.nameEn()).startsWith("STG005_");
            });
    }

    @Test
    void exposesDeeplyImmutableCollections() {
        StagingSyntheticSourceMenuManifest manifest = factory.create();

        assertThatThrownBy(() -> manifest.categories().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> manifest.options().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
