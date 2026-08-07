package com.restaurant.system.owner.menu;

import static com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.CategorySourcePolicy.CREATE_ONLY;
import static com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole.DRINK;
import static com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole.NOODLE;
import static com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.ItemRole.SIDE_DISH;
import static com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.SourcePolicy.CLONE_IF_ACTIVE_OR_CREATE;
import static com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.SourcePolicy.REQUIRED_SOURCE_CODE;
import static com.restaurant.system.owner.menu.StoreMenuCloneBaseGraphProfile.StationSourcePolicy.UNIQUE_ACTIVE_STATION_FROM_SELECTED_ITEMS;
import static com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionDisposition.COPY;
import static com.restaurant.system.owner.menu.StoreMenuCloneSourceOptionsProfile.SourceOptionDisposition.PROFILE_OVERRIDE;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Reviewed Chinatown menu policy. Store-specific rules remain isolated from the shared clone engine.
 */
@Component
public final class ChinatownMenuCloneProfile implements StoreMenuCloneSourceOptionsProfile {

    public static final String PROFILE_CODE = "CHINATOWN_MENU_2026_02_02";
    public static final String CONTRACT_VERSION = "AL003_CHINATOWN_PROFILE_V1";
    public static final String FINGERPRINT_VERSION = "CHINATOWN_PROFILE_SHA256_V2";
    public static final Long SOURCE_STORE_ID = 1L;

    public static final String SOUP_NOODLE = "SOUP_NOODLE";
    public static final String DRY_NOODLE = "DRY_NOODLE";
    public static final String SIDE_DISHES = "SIDE_DISHES";
    public static final String DRINK_CATEGORY = "DRINK";
    public static final String NOODLE_STATION = "NOODLE";
    public static final String COLD_STATION = "COLD";
    public static final String DRINK_STATION = "BAR";

    public static final String TEA_EGG_SKU = "tea_egg";
    public static final String TEA_EGG_ADD_ON_CODE = "tea_egg";
    public static final String EXTRA_MEAT_ADD_ON_CODE = "extra_meat";
    public static final String COMBO_CODE = "combo";
    public static final String COMBO_TEA_EGG_CODE = "combo_tea_egg";
    public static final boolean CREATE_LEGACY_COMBO_SIDE_REMOVE = false;

    private static final BigDecimal TEA_EGG_PRICE = money("1.99");
    private static final BigDecimal EXTRA_MEAT_PRICE = money("6.99");
    private static final BigDecimal COMBO_DELTA = money("5.00");

    private static final List<CategorySelection> CATEGORIES = List.of(
        new CategorySelection(CategorySourcePolicy.REQUIRED_SOURCE_CODE,
            SOUP_NOODLE, SOUP_NOODLE, "汤面", "Soup Noodles", true, 1),
        new CategorySelection(CategorySourcePolicy.REQUIRED_SOURCE_CODE,
            DRY_NOODLE, DRY_NOODLE, "干拌面", "Dry Noodles", true, 2),
        new CategorySelection(CREATE_ONLY, null, SIDE_DISHES, "小菜", "Side Dishes", true, 3),
        new CategorySelection(CategorySourcePolicy.REQUIRED_SOURCE_CODE,
            DRINK_CATEGORY, DRINK_CATEGORY, "饮料", "Drinks", true, 4)
    );

    private static final List<StationSelection> STATIONS = List.of(
        new StationSelection(
            StationSourcePolicy.REQUIRED_SOURCE_CODE,
            NOODLE_STATION,
            NOODLE_STATION,
            "Noodle",
            true,
            1
        ),
        new StationSelection(
            StationSourcePolicy.REQUIRED_SOURCE_CODE,
            COLD_STATION,
            COLD_STATION,
            "Cold",
            true,
            2
        ),
        new StationSelection(
            UNIQUE_ACTIVE_STATION_FROM_SELECTED_ITEMS,
            null,
            DRINK_STATION,
            "Bar",
            true,
            3
        )
    );

    private static final List<ItemSelection> ITEMS = List.of(
        reusedNoodle("traditional_beef_noodle", SOUP_NOODLE, "兰州牛肉面",
            "Traditional LanZhou Hand-pull Beef Noodle", "14.99", 1),
        reusedNoodle("braised_beef_tendon_noodle", SOUP_NOODLE, "红烧牛筋面",
            "Braised Beef Tendon Noodle", "17.99", 2),
        reusedNoodle("vegetable_noodle", SOUP_NOODLE, "蔬菜面", "Vegetable Noodle", "14.99", 3),
        reusedNoodle("dan_dan_noodle", DRY_NOODLE, "担担面", "Dan Dan Noodle", "15.99", 1),
        reusedNoodle("zha_jiang_noodle", DRY_NOODLE, "老兰州炸酱面", "Zha Jiang Noodle", "17.99", 2),
        reusedItem("braised_beef_shank_salad", SIDE_DISHES, COLD_STATION, "兰州辣拌牛展",
            "Beef Shank Mix With Home Made Spicy Sauce", "9.99", 1, SIDE_DISH),
        reusedItem("cucumber_salad", SIDE_DISHES, COLD_STATION, "香辣黄瓜",
            "Cucumber Mix With Home Made Spicy Sauce", "4.99", 2, SIDE_DISH),
        reusedItem("edamame", SIDE_DISHES, COLD_STATION, "雪菜毛豆",
            "Edamame With Preserved Vegetable", "4.99", 3, SIDE_DISH),
        reusedItem("shredded_potato", SIDE_DISHES, COLD_STATION, "海菜土豆丝",
            "Seaweed Potato Salad", "4.99", 4, SIDE_DISH),
        cloneOrCreateItem("sichuan_pepper_chicken", SIDE_DISHES, COLD_STATION, "menu_item", "椒麻鸡",
            "Sichuan Pepper Chicken", "9.99", 5, SIDE_DISH),
        cloneOrCreateItem(TEA_EGG_SKU, SIDE_DISHES, COLD_STATION, "menu_item", "茶叶卤蛋",
            "Tea Boil Egg", "1.99", 6, SIDE_DISH),
        reusedItem("coke", DRINK_CATEGORY, DRINK_STATION, "可乐", "Coke", "3.00", 1, DRINK),
        reusedItem("diet_coke", DRINK_CATEGORY, DRINK_STATION, "健怡可乐", "Diet Coke", "3.00", 2, DRINK),
        createdItem("seven_up", DRINK_CATEGORY, DRINK_STATION, "drink", "七喜", "7 Up", "3.00", 3, DRINK),
        createdItem("ginger_ale", DRINK_CATEGORY, DRINK_STATION, "drink", "姜汁汽水",
            "Ginger Ale", "3.00", 4, DRINK),
        reusedItem("ice_tea", DRINK_CATEGORY, DRINK_STATION, "冰茶", "Ice Tea", "3.00", 5, DRINK),
        reusedItem("chinese_herbal_tea", DRINK_CATEGORY, DRINK_STATION, "中式凉茶",
            "Chinese Herb Tea", "3.00", 6, DRINK)
    );

    private static final List<SourceOptionRule> NOODLE_SOURCE_RULES = List.of(
        copy("addon", "ADD_ON"),
        copy("remove", "REMOVE"),
        copy("spicy_level", "SPICY_LEVEL"),
        copy("soup_base", "SOUP_BASE"),
        override("noodle_type", "NOODLE_TYPE"),
        override("size", "SIZE"),
        override("addon", "COMBO"),
        override("addon", "COMBO_EGG"),
        override("addon", "COMBO_SIDE"),
        override("remove", "COMBO_SIDE_REMOVE")
    );

    private static final List<SourceOptionRule> DIRECT_ITEM_SOURCE_RULES = List.of(
        copy("addon", "ADD_ON"),
        copy("remove", "REMOVE"),
        override("addon", "COMBO"),
        override("addon", "COMBO_EGG"),
        override("addon", "COMBO_SIDE"),
        override("remove", "COMBO_SIDE_REMOVE")
    );

    private static final List<SourceOptionApplication> SOURCE_OPTION_APPLICATIONS = ITEMS.stream()
        .filter(item -> item.sourcePolicy() != SourcePolicy.CREATE_ONLY)
        .map(item -> new SourceOptionApplication(
            item.sourceSku(),
            item.targetSku(),
            item.roles().contains(NOODLE) ? NOODLE_SOURCE_RULES : DIRECT_ITEM_SOURCE_RULES
        ))
        .toList();

    private static final List<String> NOODLE_TYPE_CODES = List.of(
        "noodle_capillary",
        "noodle_thin",
        "noodle_sanxi",
        "noodle_erxi",
        "noodle_leek_leaf",
        "noodle_wide",
        "noodle_extra_wide"
    );

    private static final List<SizeRule> SMALL_MEDIUM_LARGE = List.of(
        new SizeRule("size_small", "小碗", "Small", 1, money("0.00")),
        new SizeRule("size_medium", "中碗", "Medium", 2, money("2.00")),
        new SizeRule("size_large", "大碗", "Large", 3, money("4.00"))
    );

    private static final List<SizeRule> SMALL_MEDIUM = List.of(
        new SizeRule("size_small", "小碗", "Small", 1, money("0.00")),
        new SizeRule("size_medium", "中碗", "Medium", 2, money("2.00"))
    );

    private static final List<ComboSideRule> COMBO_SIDES = List.of(
        new ComboSideRule("combo_cucumber_salad", "cucumber_salad", "套餐拌黄瓜", "Combo Cucumber Salad", 1),
        new ComboSideRule("combo_edamame", "edamame", "套餐毛豆", "Combo Edamame", 2),
        new ComboSideRule("combo_shredded_potato", "shredded_potato", "套餐土豆丝",
            "Combo Shredded Potato", 3)
    );

    private static final List<ComboRule> COMBOS = List.of(
        new ComboRule(1, "traditional_beef_noodle", COMBO_SIDES),
        new ComboRule(2, "zha_jiang_noodle", COMBO_SIDES),
        new ComboRule(3, "vegetable_noodle", COMBO_SIDES),
        new ComboRule(4, "dan_dan_noodle", COMBO_SIDES)
    );

    private static final String PROFILE_FINGERPRINT = sha256(canonicalDefinition());

    @Override
    public String profileCode() {
        return PROFILE_CODE;
    }

    @Override
    public Long sourceStoreId() {
        return SOURCE_STORE_ID;
    }

    @Override
    public String profileFingerprint() {
        return PROFILE_FINGERPRINT;
    }

    @Override
    public List<CategorySelection> categories() {
        return CATEGORIES;
    }

    @Override
    public List<StationSelection> stations() {
        return STATIONS;
    }

    @Override
    public List<ItemSelection> items() {
        return ITEMS;
    }

    @Override
    public List<SourceOptionApplication> sourceOptionApplications() {
        return SOURCE_OPTION_APPLICATIONS;
    }

    public List<String> noodleTypeCodes() {
        return NOODLE_TYPE_CODES;
    }

    public List<SizeRule> sizeRulesFor(String targetSku) {
        return switch (targetSku) {
            case "traditional_beef_noodle", "vegetable_noodle" -> SMALL_MEDIUM_LARGE;
            case "dan_dan_noodle" -> SMALL_MEDIUM;
            default -> List.of();
        };
    }

    public List<ComboRule> combos() {
        return COMBOS;
    }

    public Optional<ComboRule> comboFor(String targetSku) {
        return COMBOS.stream().filter(combo -> combo.mainItemSku().equals(targetSku)).findFirst();
    }

    public GeneratedOptionRule teaEggAddOn() {
        return new GeneratedOptionRule(
            "addon", "ADD_ON", TEA_EGG_ADD_ON_CODE, "加卤蛋", "Extra Tea Egg", TEA_EGG_PRICE, 900
        );
    }

    public GeneratedOptionRule extraMeatAddOn() {
        return new GeneratedOptionRule(
            "addon", "ADD_ON", EXTRA_MEAT_ADD_ON_CODE, "加肉", "Extra Meat", EXTRA_MEAT_PRICE, 910
        );
    }

    public GeneratedOptionRule comboOption() {
        return new GeneratedOptionRule("addon", "COMBO", COMBO_CODE, "套餐", "Combo", COMBO_DELTA, 1000);
    }

    public GeneratedOptionRule comboTeaEggOption() {
        return new GeneratedOptionRule(
            "addon", "COMBO_EGG", COMBO_TEA_EGG_CODE, "套餐卤蛋", "Combo Tea Egg", money("0.00"), 1010
        );
    }

    public GeneratedOptionRule sizeOption(SizeRule size) {
        return new GeneratedOptionRule(
            "size", "SIZE", size.optionCode(), size.nameZh(), size.nameEn(),
            size.priceDelta(), size.sortOrder()
        );
    }

    public GeneratedOptionRule comboSideOption(ComboSideRule side) {
        return new GeneratedOptionRule(
            "addon", "COMBO_SIDE", side.optionCode(), side.nameZh(), side.nameEn(),
            money("0.00"), 1020 + side.sortOrder()
        );
    }

    public record SizeRule(
        String optionCode,
        String nameZh,
        String nameEn,
        int sortOrder,
        BigDecimal priceDelta
    ) {
    }

    public record GeneratedOptionRule(
        String optionType,
        String optionGroup,
        String optionCode,
        String nameZh,
        String nameEn,
        BigDecimal priceDelta,
        int sortOrder
    ) {
    }

    public record ComboSideRule(
        String optionCode,
        String targetSideSku,
        String nameZh,
        String nameEn,
        int sortOrder
    ) {
    }

    public record ComboRule(int comboNumber, String mainItemSku, List<ComboSideRule> sides) {
        public ComboRule {
            sides = List.copyOf(sides);
        }
    }

    private static ItemSelection reusedNoodle(
        String sku,
        String category,
        String nameZh,
        String nameEn,
        String price,
        int order
    ) {
        return reusedItem(sku, category, NOODLE_STATION, nameZh, nameEn, price, order, NOODLE);
    }

    private static ItemSelection reusedItem(
        String sku,
        String category,
        String station,
        String nameZh,
        String nameEn,
        String price,
        int order,
        ItemRole role
    ) {
        return item(sku, SourcePolicy.REQUIRED_SOURCE_CODE, sku, category, station, null,
            nameZh, nameEn, price, order, role);
    }

    private static ItemSelection cloneOrCreateItem(
        String sku,
        String category,
        String station,
        String itemType,
        String nameZh,
        String nameEn,
        String price,
        int order,
        ItemRole role
    ) {
        return item(sku, CLONE_IF_ACTIVE_OR_CREATE, sku, category, station, itemType,
            nameZh, nameEn, price, order, role);
    }

    private static ItemSelection createdItem(
        String sku,
        String category,
        String station,
        String itemType,
        String nameZh,
        String nameEn,
        String price,
        int order,
        ItemRole role
    ) {
        return item(null, SourcePolicy.CREATE_ONLY, sku, category, station, itemType,
            nameZh, nameEn, price, order, role);
    }

    private static ItemSelection item(
        String sourceSku,
        SourcePolicy sourcePolicy,
        String targetSku,
        String category,
        String station,
        String itemType,
        String nameZh,
        String nameEn,
        String price,
        int order,
        ItemRole role
    ) {
        return new ItemSelection(
            sourceSku,
            sourcePolicy,
            targetSku,
            category,
            station,
            itemType,
            nameZh,
            nameEn,
            money(price),
            true,
            false,
            order,
            Set.of(role)
        );
    }

    private static SourceOptionRule copy(String type, String group) {
        return new SourceOptionRule(type, group, COPY);
    }

    private static SourceOptionRule override(String type, String group) {
        return new SourceOptionRule(type, group, PROFILE_OVERRIDE);
    }

    private static String canonicalDefinition() {
        StringBuilder value = new StringBuilder();
        append(value, "profileCode", PROFILE_CODE);
        append(value, "contractVersion", CONTRACT_VERSION);
        append(value, "fingerprintVersion", FINGERPRINT_VERSION);
        append(value, "sourceStoreId", SOURCE_STORE_ID);
        for (CategorySelection category : CATEGORIES) {
            append(value, "category", category);
        }
        for (StationSelection station : STATIONS) {
            append(value, "station", station);
        }
        for (ItemSelection item : ITEMS) {
            append(value, "item", item);
        }
        for (SourceOptionApplication application : SOURCE_OPTION_APPLICATIONS) {
            append(value, "sourceApplication.sourceSku", application.sourceItemSku());
            append(value, "sourceApplication.targetSku", application.targetItemSku());
            application.rules().forEach(rule -> append(value, "sourceApplication.rule", rule));
        }
        NOODLE_TYPE_CODES.forEach(code -> append(value, "noodleType", code));
        for (ItemSelection item : ITEMS) {
            sizeRules(item.targetSku()).forEach(size -> {
                append(value, "size." + item.targetSku(), size);
                append(value, "sizeOption." + item.targetSku(), new GeneratedOptionRule(
                    "size", "SIZE", size.optionCode(), size.nameZh(), size.nameEn(),
                    size.priceDelta(), size.sortOrder()
                ));
            });
        }
        COMBOS.forEach(combo -> {
            append(value, "combo", combo);
            combo.sides().forEach(side -> append(value, "comboSideOption." + combo.mainItemSku(),
                new GeneratedOptionRule(
                    "addon", "COMBO_SIDE", side.optionCode(), side.nameZh(), side.nameEn(),
                    money("0.00"), 1020 + side.sortOrder()
                )));
        });
        append(value, "teaEggAddOn", new GeneratedOptionRule(
            "addon", "ADD_ON", TEA_EGG_ADD_ON_CODE, "加卤蛋", "Extra Tea Egg", TEA_EGG_PRICE, 900
        ));
        append(value, "extraMeatAddOn", new GeneratedOptionRule(
            "addon", "ADD_ON", EXTRA_MEAT_ADD_ON_CODE, "加肉", "Extra Meat", EXTRA_MEAT_PRICE, 910
        ));
        append(value, "comboOption", new GeneratedOptionRule(
            "addon", "COMBO", COMBO_CODE, "套餐", "Combo", COMBO_DELTA, 1000
        ));
        append(value, "comboTeaEgg", new GeneratedOptionRule(
            "addon", "COMBO_EGG", COMBO_TEA_EGG_CODE, "套餐卤蛋", "Combo Tea Egg", money("0.00"), 1010
        ));
        append(value, "createLegacyComboSideRemove", CREATE_LEGACY_COMBO_SIDE_REMOVE);
        append(value, "frenchLocalization", false);
        append(value, "tendonSchedule", false);
        return value.toString();
    }

    private static List<SizeRule> sizeRules(String targetSku) {
        return switch (targetSku) {
            case "traditional_beef_noodle", "vegetable_noodle" -> SMALL_MEDIUM_LARGE;
            case "dan_dan_noodle" -> SMALL_MEDIUM;
            default -> List.of();
        };
    }

    private static void append(StringBuilder target, String name, Object rawValue) {
        String value = canonicalValue(rawValue);
        target.append(name.length()).append(':').append(name)
            .append(value.length()).append(':').append(value);
    }

    private static String canonicalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.setScale(2).toPlainString();
        }
        return String.valueOf(value);
    }

    private static String sha256(String value) {
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

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }
}
