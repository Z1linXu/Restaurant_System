package com.restaurant.system.staging.menu;

import com.restaurant.system.owner.menu.ChinatownMenuCloneProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Builds the reviewed synthetic source graph; this is not live St-Denis or Production data. */
@Component
@Profile("staging-synthetic-bootstrap")
public final class StagingSyntheticSourceMenuManifestFactory {

    public static final String MANIFEST_CODE = "STG005_ST_DENIS_SOURCE_MENU";
    public static final String MANIFEST_VERSION = "STG005_SOURCE_MENU_V1";
    public static final String TOPOLOGY_NAMESPACE = "STG005_";

    private static final String SOURCE_SIDE = "SOURCE_SIDE";
    private static final String BAR_SOURCE = "BAR_SOURCE";
    private static final List<String> NOODLE_SKUS = List.of(
        "traditional_beef_noodle",
        "braised_beef_tendon_noodle",
        "vegetable_noodle",
        "dan_dan_noodle",
        "zha_jiang_noodle"
    );

    public StagingSyntheticSourceMenuManifest create() {
        List<StagingSyntheticSourceMenuManifest.Category> categories = List.of(
            category(ChinatownMenuCloneProfile.SOUP_NOODLE, 1),
            category(ChinatownMenuCloneProfile.DRY_NOODLE, 2),
            category(SOURCE_SIDE, 3),
            category(ChinatownMenuCloneProfile.DRINK_CATEGORY, 4)
        );
        List<StagingSyntheticSourceMenuManifest.Station> stations = List.of(
            station(ChinatownMenuCloneProfile.NOODLE_STATION, 1),
            station(ChinatownMenuCloneProfile.COLD_STATION, 2),
            station(BAR_SOURCE, 3)
        );
        List<StagingSyntheticSourceMenuManifest.Item> items = List.of(
            item("traditional_beef_noodle", ChinatownMenuCloneProfile.SOUP_NOODLE,
                ChinatownMenuCloneProfile.NOODLE_STATION, 1),
            item("braised_beef_tendon_noodle", ChinatownMenuCloneProfile.SOUP_NOODLE,
                ChinatownMenuCloneProfile.NOODLE_STATION, 2),
            item("vegetable_noodle", ChinatownMenuCloneProfile.SOUP_NOODLE,
                ChinatownMenuCloneProfile.NOODLE_STATION, 3),
            item("dan_dan_noodle", ChinatownMenuCloneProfile.DRY_NOODLE,
                ChinatownMenuCloneProfile.NOODLE_STATION, 1),
            item("zha_jiang_noodle", ChinatownMenuCloneProfile.DRY_NOODLE,
                ChinatownMenuCloneProfile.NOODLE_STATION, 2),
            item("braised_beef_shank_salad", SOURCE_SIDE, ChinatownMenuCloneProfile.COLD_STATION, 1),
            item("cucumber_salad", SOURCE_SIDE, ChinatownMenuCloneProfile.COLD_STATION, 2),
            item("edamame", SOURCE_SIDE, ChinatownMenuCloneProfile.COLD_STATION, 3),
            item("shredded_potato", SOURCE_SIDE, ChinatownMenuCloneProfile.COLD_STATION, 4),
            item("coke", ChinatownMenuCloneProfile.DRINK_CATEGORY, BAR_SOURCE, 1),
            item("diet_coke", ChinatownMenuCloneProfile.DRINK_CATEGORY, BAR_SOURCE, 2),
            item("ice_tea", ChinatownMenuCloneProfile.DRINK_CATEGORY, BAR_SOURCE, 3),
            item("chinese_herbal_tea", ChinatownMenuCloneProfile.DRINK_CATEGORY, BAR_SOURCE, 4)
        );
        return new StagingSyntheticSourceMenuManifest(
            MANIFEST_CODE,
            MANIFEST_VERSION,
            TOPOLOGY_NAMESPACE,
            categories,
            stations,
            items,
            options()
        );
    }

    private List<StagingSyntheticSourceMenuManifest.Option> options() {
        ChinatownMenuCloneProfile profile = new ChinatownMenuCloneProfile();
        List<StagingSyntheticSourceMenuManifest.Option> options = new ArrayList<>();
        for (String sku : NOODLE_SKUS) {
            int sortOrder = 1;
            for (String code : profile.noodleTypeCodes()) {
                options.add(option(sku, "noodle_type", "NOODLE_TYPE", code, null, sortOrder++, "0.00"));
            }
        }
        options.add(option(
            "traditional_beef_noodle", "addon", "ADD_ON", "tea_egg", null, 100, "0.50"
        ));
        options.add(option(
            "traditional_beef_noodle", "addon", "ADD_ON", "extra_meat", null, 101, "4.25"
        ));
        options.add(option(
            "cucumber_salad", "remove", "REMOVE", "remove_garlic", null, 1, "0.00"
        ));
        return List.copyOf(options);
    }

    private StagingSyntheticSourceMenuManifest.Category category(String code, int sortOrder) {
        return new StagingSyntheticSourceMenuManifest.Category(
            code,
            syntheticName(code),
            syntheticName(code),
            true,
            sortOrder
        );
    }

    private StagingSyntheticSourceMenuManifest.Station station(String code, int sortOrder) {
        return new StagingSyntheticSourceMenuManifest.Station(code, syntheticName(code), true, sortOrder);
    }

    private StagingSyntheticSourceMenuManifest.Item item(
        String sku,
        String categoryCode,
        String stationCode,
        int sortOrder
    ) {
        return new StagingSyntheticSourceMenuManifest.Item(
            sku,
            categoryCode,
            stationCode,
            "menu_item",
            syntheticName(sku),
            syntheticName(sku),
            money("10.00"),
            money("1.00"),
            true,
            false,
            sortOrder
        );
    }

    private StagingSyntheticSourceMenuManifest.Option option(
        String itemSku,
        String optionType,
        String optionGroup,
        String optionCode,
        String parentOptionCode,
        int sortOrder,
        String priceDelta
    ) {
        return new StagingSyntheticSourceMenuManifest.Option(
            itemSku,
            optionType,
            optionGroup,
            optionCode,
            parentOptionCode,
            syntheticName(optionCode),
            syntheticName(optionCode),
            money(priceDelta),
            true,
            sortOrder
        );
    }

    private String syntheticName(String semanticCode) {
        return TOPOLOGY_NAMESPACE + semanticCode;
    }

    private BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
