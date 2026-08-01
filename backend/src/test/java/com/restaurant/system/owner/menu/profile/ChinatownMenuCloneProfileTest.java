package com.restaurant.system.owner.menu.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemSelection;
import com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.SourcePolicy;
import com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionDisposition;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ChinatownMenuCloneProfileTest {

    private final ChinatownMenuCloneProfile profile = new ChinatownMenuCloneProfile();

    @Test
    void identityCategoriesAndStationsArePinned() {
        assertThat(profile.profileCode()).isEqualTo("CHINATOWN_MENU_2026_02_02");
        assertThat(profile.sourceStoreId()).isEqualTo(1L);
        assertThat(profile.profileFingerprint()).matches("[0-9a-f]{64}");

        assertThat(profile.categories())
            .extracting(CategorySelection::targetCode, CategorySelection::targetNameZh,
                CategorySelection::targetNameEn, CategorySelection::targetSortOrder)
            .containsExactly(
                tuple("SOUP_NOODLE", "汤面", "Soup Noodles", 1),
                tuple("DRY_NOODLE", "干拌面", "Dry Noodles", 2),
                tuple("SIDE_DISHES", "小菜", "Side Dishes", 3),
                tuple("DRINK", "饮料", "Drinks", 4)
            );
        assertThat(profile.stations())
            .extracting(station -> station.targetCode(), station -> station.targetSortOrder())
            .containsExactly(tuple("NOODLE", 1), tuple("COLD", 2), tuple("BAR", 3));
    }

    @Test
    void allSeventeenItemsMatchTheFinalReviewedOrderAndPrices() {
        assertThat(profile.items()).hasSize(17);
        assertThat(profile.items())
            .extracting(ItemSelection::targetSku, ItemSelection::targetCategoryCode,
                ItemSelection::targetBasePrice, ItemSelection::targetSortOrder)
            .containsExactly(
                item("traditional_beef_noodle", "SOUP_NOODLE", "14.99", 1),
                item("braised_beef_tendon_noodle", "SOUP_NOODLE", "17.99", 2),
                item("vegetable_noodle", "SOUP_NOODLE", "14.99", 3),
                item("dan_dan_noodle", "DRY_NOODLE", "15.99", 1),
                item("zha_jiang_noodle", "DRY_NOODLE", "17.99", 2),
                item("braised_beef_shank_salad", "SIDE_DISHES", "9.99", 1),
                item("cucumber_salad", "SIDE_DISHES", "4.99", 2),
                item("edamame", "SIDE_DISHES", "4.99", 3),
                item("shredded_potato", "SIDE_DISHES", "4.99", 4),
                item("sichuan_pepper_chicken", "SIDE_DISHES", "9.99", 5),
                item("tea_egg", "SIDE_DISHES", "1.99", 6),
                item("coke", "DRINK", "3.00", 1),
                item("diet_coke", "DRINK", "3.00", 2),
                item("seven_up", "DRINK", "3.00", 3),
                item("ginger_ale", "DRINK", "3.00", 4),
                item("ice_tea", "DRINK", "3.00", 5),
                item("chinese_herbal_tea", "DRINK", "3.00", 6)
            );
        assertThat(profile.items()).allSatisfy(item -> {
            assertThat(item.targetActive()).isTrue();
            assertThat(item.targetSoldOut()).isFalse();
        });
        assertThat(find("sichuan_pepper_chicken").sourcePolicy()).isEqualTo(SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE);
        assertThat(find("tea_egg").sourcePolicy()).isEqualTo(SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE);
        assertThat(find("seven_up").sourcePolicy()).isEqualTo(SourcePolicy.CREATE_ONLY);
        assertThat(find("ginger_ale").sourcePolicy()).isEqualTo(SourcePolicy.CREATE_ONLY);
        assertThat(find("tea_egg").targetNameEn()).isEqualTo("Tea Boil Egg");
    }

    @Test
    void sourceApplicationsClassifyEverySourceBackedItemAndReserveOverrides() {
        assertThat(profile.sourceOptionApplications()).hasSize(15);
        assertThat(profile.sourceOptionApplications())
            .extracting(application -> application.targetItemSku())
            .doesNotContain("seven_up", "ginger_ale")
            .contains("sichuan_pepper_chicken", "tea_egg", "chinese_herbal_tea");

        var noodle = profile.sourceOptionApplications().stream()
            .filter(application -> application.targetItemSku().equals("traditional_beef_noodle"))
            .findFirst().orElseThrow();
        assertThat(noodle.rules())
            .extracting(rule -> rule.optionType(), rule -> rule.optionGroup(), rule -> rule.disposition())
            .containsExactly(
                tuple("addon", "ADD_ON", SourceOptionDisposition.COPY),
                tuple("remove", "REMOVE", SourceOptionDisposition.COPY),
                tuple("spicy_level", "SPICY_LEVEL", SourceOptionDisposition.COPY),
                tuple("soup_base", "SOUP_BASE", SourceOptionDisposition.COPY),
                tuple("noodle_type", "NOODLE_TYPE", SourceOptionDisposition.PROFILE_OVERRIDE),
                tuple("size", "SIZE", SourceOptionDisposition.PROFILE_OVERRIDE),
                tuple("addon", "COMBO", SourceOptionDisposition.PROFILE_OVERRIDE),
                tuple("addon", "COMBO_EGG", SourceOptionDisposition.PROFILE_OVERRIDE),
                tuple("addon", "COMBO_SIDE", SourceOptionDisposition.PROFILE_OVERRIDE),
                tuple("remove", "COMBO_SIDE_REMOVE", SourceOptionDisposition.PROFILE_OVERRIDE)
            );
    }

    @Test
    void sizeNoodleComboAndTeaEggRulesAreExact() {
        assertThat(profile.noodleTypeCodes()).containsExactly(
            "noodle_capillary", "noodle_thin", "noodle_sanxi", "noodle_erxi",
            "noodle_leek_leaf", "noodle_wide", "noodle_extra_wide"
        );
        assertThat(profile.sizeRulesFor("traditional_beef_noodle"))
            .extracting(rule -> rule.optionCode(), rule -> rule.priceDelta())
            .containsExactly(
                tuple("size_small", money("0.00")),
                tuple("size_medium", money("2.00")),
                tuple("size_large", money("4.00"))
            );
        assertThat(profile.sizeRulesFor("dan_dan_noodle"))
            .extracting(rule -> rule.optionCode(), rule -> rule.priceDelta())
            .containsExactly(tuple("size_small", money("0.00")), tuple("size_medium", money("2.00")));
        assertThat(profile.sizeRulesFor("zha_jiang_noodle")).isEmpty();
        assertThat(profile.sizeRulesFor("braised_beef_tendon_noodle")).isEmpty();

        assertThat(profile.combos())
            .extracting(combo -> combo.comboNumber(), combo -> combo.mainItemSku())
            .containsExactly(
                tuple(1, "traditional_beef_noodle"),
                tuple(2, "zha_jiang_noodle"),
                tuple(3, "vegetable_noodle"),
                tuple(4, "dan_dan_noodle")
            );
        assertThat(profile.combos()).allSatisfy(combo -> assertThat(combo.sides()).hasSize(3));
        assertThat(profile.comboFor("braised_beef_tendon_noodle")).isEmpty();
        assertThat(profile.teaEggAddOn().priceDelta()).isEqualByComparingTo("1.99");
        assertThat(profile.extraMeatAddOn().optionCode()).isEqualTo("extra_meat");
        assertThat(profile.extraMeatAddOn().priceDelta()).isEqualByComparingTo("6.99");
        assertThat(profile.comboOption().priceDelta()).isEqualByComparingTo("5.00");
        assertThat(profile.comboTeaEggOption().optionCode()).isEqualTo("combo_tea_egg");
        assertThat(profile.comboSideOption(profile.combos().get(0).sides().get(0)))
            .extracting(rule -> rule.optionType(), rule -> rule.optionGroup(), rule -> rule.priceDelta(), rule -> rule.sortOrder())
            .containsExactly("addon", "COMBO_SIDE", money("0.00"), 1021);
        assertThat(ChinatownMenuCloneProfile.CREATE_LEGACY_COMBO_SIDE_REMOVE).isFalse();
    }

    @Test
    void profileCollectionsAreImmutableAndFingerprintIsStable() {
        assertThat(profile.profileFingerprint()).isEqualTo(new ChinatownMenuCloneProfile().profileFingerprint());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> profile.items().clear())
            .isInstanceOf(UnsupportedOperationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> profile.combos().get(0).sides().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private ItemSelection find(String sku) {
        return profile.items().stream().filter(item -> item.targetSku().equals(sku)).findFirst().orElseThrow();
    }

    private org.assertj.core.groups.Tuple item(String sku, String category, String price, int order) {
        return tuple(sku, category, money(price), order);
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
