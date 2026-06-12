package com.restaurant.system.common.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.auth.entity.UserCredential;
import com.restaurant.system.auth.repository.UserCredentialRepository;
import com.restaurant.system.auth.service.PasswordService;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.entity.RestaurantTemplate;
import com.restaurant.system.platform.entity.StoreKdsDisplayConfig;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.platform.repository.RestaurantTemplateRepository;
import com.restaurant.system.platform.repository.StoreKdsDisplayConfigRepository;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.transport.EscPosFontSizeMode;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.DiningTableRepository;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.StoreRepository;
import com.restaurant.system.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RuntimeDataSeeder implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RuntimeDataSeeder.class);

    private final StoreRepository storeRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final DiningTableRepository diningTableRepository;
    private final OrganizationRepository organizationRepository;
    private final RestaurantTemplateRepository restaurantTemplateRepository;
    private final StoreKdsDisplayConfigRepository storeKdsDisplayConfigRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final PrinterConfigRepository printerConfigRepository;
    private final PrinterAssignmentRepository printerAssignmentRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final PasswordService passwordService;
    private final ObjectMapper objectMapper;
    private final boolean runtimeSeedEnabled;
    private final boolean forceOverwrite;

    public RuntimeDataSeeder(
        StoreRepository storeRepository,
        RoleRepository roleRepository,
        UserRepository userRepository,
        StationRepository stationRepository,
        DiningTableRepository diningTableRepository,
        OrganizationRepository organizationRepository,
        RestaurantTemplateRepository restaurantTemplateRepository,
        StoreKdsDisplayConfigRepository storeKdsDisplayConfigRepository,
        MenuCategoryRepository menuCategoryRepository,
        MenuItemRepository menuItemRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        PrinterConfigRepository printerConfigRepository,
        PrinterAssignmentRepository printerAssignmentRepository,
        UserCredentialRepository userCredentialRepository,
        PasswordService passwordService,
        @Value("${app.seed.runtime-enabled:true}") boolean runtimeSeedEnabled,
        @Value("${app.seed.force-overwrite:false}") boolean forceOverwrite
    ) {
        this.storeRepository = storeRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.stationRepository = stationRepository;
        this.diningTableRepository = diningTableRepository;
        this.organizationRepository = organizationRepository;
        this.restaurantTemplateRepository = restaurantTemplateRepository;
        this.storeKdsDisplayConfigRepository = storeKdsDisplayConfigRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.printerConfigRepository = printerConfigRepository;
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.passwordService = passwordService;
        this.objectMapper = new ObjectMapper();
        this.runtimeSeedEnabled = runtimeSeedEnabled;
        this.forceOverwrite = forceOverwrite;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!runtimeSeedEnabled) {
            logger.info("RuntimeDataSeeder disabled: app.seed.runtime-enabled=false");
            return;
        }
        logger.info(
            "RuntimeDataSeeder running in {}",
            forceOverwrite ? "force overwrite mode" : "missing-data supplement mode"
        );
        seedStores();
        seedRoles();
        seedUsers();
        seedAuthCredentials();
        seedStations();
        seedMenuCategories();
        seedMenuItems();
        seedMenuItemOptions();
        if (forceOverwrite) {
            syncTargetOptionPrices();
        } else {
            logger.info("Skipping syncTargetOptionPrices because app.seed.force-overwrite=false");
        }
        seedOrganizations();
        attachStoresToOrganizations();
        seedDiningTables();
        seedStoreKdsDisplayConfigs();
        seedRamenTemplate();
        seedPrintingCenter();
    }

    private void seedStores() {
        if (storeRepository.count() > 0) {
            return;
        }

        Store store = new Store();
        store.organization_id = null;
        store.name = "Main Kitchen";
        store.code = "MAIN";
        store.status = "active";
        store.enable_bar_kitchen_tasks = false;
        store.printing_enabled = true;
        store.created_at = now();
        store.updated_at = now();
        storeRepository.save(store);
    }

    private void seedRoles() {
        if (roleRepository.count() > 0) {
            return;
        }

        roleRepository.saveAll(List.of(
            role("Frontdesk", "FRONTDESK"),
            role("Hot Kitchen", "HOT_KITCHEN"),
            role("Noodle View", "NOODLE_VIEW"),
            role("Pass", "PASS"),
            role("Admin", "ADMIN")
        ));
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }

        Store store = firstStore();
        Long frontdeskRoleId = findRoleId("FRONTDESK");
        Long adminRoleId = findRoleId("ADMIN");

        User frontdeskUser = new User();
        frontdeskUser.setStore_id(store.id);
        frontdeskUser.setRole_id(frontdeskRoleId);
        frontdeskUser.setUsername("frontdesk");
        frontdeskUser.setFull_name("Frontdesk User");
        frontdeskUser.setPhone("555-0001");
        frontdeskUser.setStatus("active");
        frontdeskUser.setCreated_at(now());
        frontdeskUser.setUpdated_at(now());

        User adminUser = new User();
        adminUser.setStore_id(store.id);
        adminUser.setRole_id(adminRoleId);
        adminUser.setUsername("admin");
        adminUser.setFull_name("Admin User");
        adminUser.setPhone("555-0002");
        adminUser.setStatus("active");
        adminUser.setCreated_at(now());
        adminUser.setUpdated_at(now());

        userRepository.saveAll(List.of(frontdeskUser, adminUser));
    }

    private void seedAuthCredentials() {
        Store store = firstStore();
        User owner = ensureAuthUser(store.id, "owner", "Owner User", "ADMIN", "555-0100");
        User frontdesk = ensureAuthUser(store.id, "frontdesk", "Frontdesk User", "FRONTDESK", "555-0001");
        User kitchen = ensureAuthUser(store.id, "kitchen", "Kitchen User", "HOT_KITCHEN", "555-0101");
        ensureCredential(owner, "owner", "ChangeMe123!");
        ensureCredential(frontdesk, "frontdesk", "ChangeMe123!");
        ensureCredential(kitchen, "kitchen", "ChangeMe123!");
    }

    private User ensureAuthUser(Long storeId, String username, String fullName, String roleCode, String phone) {
        User existing = userRepository.findAll().stream()
            .filter(user -> username.equalsIgnoreCase(user.getUsername()))
            .findFirst()
            .orElse(null);
        if (existing != null && !forceOverwrite) {
            logger.info("Seeder skip existing auth user {}", username);
            return existing;
        }

        User target = existing == null ? new User() : existing;
        logger.info("{} auth user {}", existing == null ? "Seeder inserting missing" : "Seeder force overwriting", username);
        target.setStore_id(storeId);
        target.setRole_id(findRoleId(roleCode));
        target.setUsername(username);
        target.setFull_name(fullName);
        target.setPhone(phone);
        target.setStatus("active");
        target.setUpdated_at(now());
        if (target.getCreated_at() == null) {
            target.setCreated_at(now());
        }
        return userRepository.save(target);
    }

    private void ensureCredential(User user, String loginIdentifier, String defaultPassword) {
        boolean credentialExists = userCredentialRepository.existsByLoginIdentifierIgnoreCase(loginIdentifier);
        if (credentialExists && !forceOverwrite) {
            logger.info("Seeder skip existing user credential {}", loginIdentifier);
            return;
        }

        UserCredential credential = credentialExists
            ? userCredentialRepository.findFirstByLoginIdentifierIgnoreCase(loginIdentifier)
                .orElseGet(UserCredential::new)
            : new UserCredential();
        logger.info("{} user credential {}", credential.id == null ? "Seeder inserting missing" : "Seeder force overwriting", loginIdentifier);
        credential.userId = user.getId();
        credential.loginIdentifier = loginIdentifier;
        credential.passwordHash = passwordService.hashPassword(defaultPassword);
        credential.passwordAlgorithm = "BCRYPT";
        credential.passwordUpdatedAt = now();
        credential.isActive = true;
        credential.updatedAt = now();
        if (credential.createdAt == null) {
            credential.createdAt = now();
        }
        userCredentialRepository.save(credential);
    }

    private void seedStations() {
        if (stationRepository.count() > 0) {
            return;
        }

        Long storeId = firstStore().id;
        stationRepository.saveAll(List.of(
            station(storeId, "NOODLE", "面档", 1),
            station(storeId, "WOK", "炒锅", 2),
            station(storeId, "COLD", "冷菜", 3),
            station(storeId, "DEEPFRIED", "炸物", 4),
            station(storeId, "BAR", "吧台", 5)
        ));
    }

    private void seedMenuCategories() {
        Long storeId = firstStore().id;
        ensureCategory(storeId, "SOUP_NOODLE", "汤面", "Soup Noodle", 1);
        ensureCategory(storeId, "FRIED_NOODLE", "炒面", "Stir-Fried Noodles", 2);
        ensureCategory(storeId, "DRY_NOODLE", "拌面", "Mixed / Dry / Cold Noodles", 3);
        ensureCategory(storeId, "SIDE", "小菜", "Side Dishes", 4);
        ensureCategory(storeId, "FRIED", "炸物", "Fried Items", 5);
        ensureCategory(storeId, "DRINK", "饮品", "Drinks", 6);
    }

    private void seedMenuItems() {
        Store store = firstStore();
        MenuCategory soupNoodle = findCategory("SOUP_NOODLE");
        MenuCategory friedNoodle = findCategory("FRIED_NOODLE");
        MenuCategory dryNoodle = findCategory("DRY_NOODLE");
        MenuCategory side = findCategory("SIDE");
        MenuCategory fried = findCategory("FRIED");
        MenuCategory drink = findCategory("DRINK");

        Long noodleStationId = findStationId("NOODLE");
        Long wokStationId = findStationId("WOK");
        Long coldStationId = findStationId("COLD");
        Long deepFriedStationId = findStationId("DEEPFRIED");
        Long barStationId = findStationId("BAR");

        ensureItem(store.id, soupNoodle.id, noodleStationId, "traditional_beef_noodle", "传统牛肉面", "Traditional Beef Noodle", "menu_item", "12.50");
        ensureItem(store.id, soupNoodle.id, noodleStationId, "braised_beef_tendon_noodle", "红烧牛筋面", "Braised Beef Tendon Noodle", "menu_item", "15.80");
        ensureItem(store.id, soupNoodle.id, noodleStationId, "pickled_vegetable_beef_noodle", "酸菜牛肉面", "Pickled Vegetable Beef Noodle", "menu_item", "14.80");
        ensureItem(store.id, soupNoodle.id, noodleStationId, "vegetable_noodle", "蔬菜面", "Vegetable Noodle", "menu_item", "13.80");

        ensureItem(store.id, friedNoodle.id, wokStationId, "beef_chow_mein", "牛肉炒面", "Beef Chow Mein", "menu_item", "14.50");
        ensureItem(store.id, friedNoodle.id, wokStationId, "chicken_chow_mein", "鸡肉炒面", "Chicken Chow Mein", "menu_item", "13.80");
        ensureItem(store.id, friedNoodle.id, wokStationId, "tomato_chow_mein", "番茄炒面", "Tomato Chow Mein", "menu_item", "13.20");
        ensureItem(store.id, friedNoodle.id, wokStationId, "vegetable_chow_mein", "素菜炒面", "Vegetable Chow Mein", "menu_item", "12.80");

        ensureItem(store.id, dryNoodle.id, noodleStationId, "cold_noodle_shredded_chicken", "鸡丝凉面", "Cold Noodle with Shredded Chicken", "menu_item", "13.80");
        ensureItem(store.id, dryNoodle.id, noodleStationId, "zha_jiang_noodle", "炸酱面", "Zha Jiang Noodle", "menu_item", "13.50");
        ensureItem(store.id, dryNoodle.id, noodleStationId, "dan_dan_noodle", "担担面", "Dan Dan Noodle", "menu_item", "13.50");

        ensureItem(store.id, side.id, coldStationId, "cucumber_salad", "拌黄瓜", "Cucumber Salad", "menu_item", "4.50");
        ensureItem(store.id, side.id, coldStationId, "edamame", "毛豆", "Edamame", "menu_item", "4.50");
        ensureItem(store.id, side.id, coldStationId, "shredded_potato", "土豆丝", "Shredded Potato", "menu_item", "4.80");
        ensureItem(store.id, side.id, coldStationId, "braised_beef_shank_salad", "拌牛展", "Braised Beef Shank Salad", "menu_item", "8.80");

        ensureItem(store.id, fried.id, deepFriedStationId, "fried_spring_rolls", "炸春卷", "Fried Spring Rolls", "menu_item", "5.99");
        ensureItem(store.id, fried.id, deepFriedStationId, "tempura_shrimp", "炸虾", "Tempura Shrimp", "menu_item", "8.99");
        ensureItem(store.id, fried.id, deepFriedStationId, "fried_steamed_buns", "炸馒头", "Fried Steamed Buns", "menu_item", "5.99");
        ensureItem(store.id, fried.id, deepFriedStationId, "fried_wontons", "炸馄饨", "Fried Wontons", "menu_item", "5.99");

        ensureItem(store.id, drink.id, barStationId, "coke", "可乐", "Coke", "drink", "2.50");
        ensureItem(store.id, drink.id, barStationId, "diet_coke", "健怡可乐", "Diet Coke", "drink", "2.50");
        ensureItem(store.id, drink.id, barStationId, "chinese_herbal_tea", "王老吉", "Chinese Herbal Tea", "drink", "3.50");
        ensureItem(store.id, drink.id, barStationId, "ice_tea", "冰红茶", "Ice Tea", "drink", "2.80");
        ensureItem(store.id, drink.id, barStationId, "shochu", "烧酒", "Shochu", "drink", "8.50");
        ensureItem(store.id, drink.id, barStationId, "sake", "清酒", "Sake", "drink", "7.80");
        ensureItem(store.id, drink.id, barStationId, "tsingtao_beer", "青岛啤酒", "Tsingtao Beer", "drink", "5.50");

        if (forceOverwrite) {
            deactivateLegacyMenuItems(store.id, Set.of(
                "traditional_beef_noodle",
                "braised_beef_tendon_noodle",
                "pickled_vegetable_beef_noodle",
                "vegetable_noodle",
                "beef_chow_mein",
                "chicken_chow_mein",
                "tomato_chow_mein",
                "vegetable_chow_mein",
                "cold_noodle_shredded_chicken",
                "zha_jiang_noodle",
                "dan_dan_noodle",
                "cucumber_salad",
                "edamame",
                "shredded_potato",
                "braised_beef_shank_salad",
                "fried_spring_rolls",
                "tempura_shrimp",
                "fried_steamed_buns",
                "fried_wontons",
                "coke",
                "diet_coke",
                "chinese_herbal_tea",
                "ice_tea",
                "shochu",
                "sake",
                "tsingtao_beer"
            ));
        } else {
            logger.info("Skipping legacy menu item deactivation because app.seed.force-overwrite=false");
        }
    }

    private void seedMenuItemOptions() {
        syncOptions("traditional_beef_noodle", buildSoupNoodleOptions(true));
        syncOptions("braised_beef_tendon_noodle", buildSoupNoodleOptions(true));
        syncOptions("pickled_vegetable_beef_noodle", buildSoupNoodleOptions(true));
        syncOptions("vegetable_noodle", buildVegetableNoodleOptions());
        syncOptions("beef_chow_mein", buildWokOptions(true));
        syncOptions("chicken_chow_mein", buildWokOptions(true));
        syncOptions("tomato_chow_mein", buildTomatoWokOptions());
        syncOptions("vegetable_chow_mein", buildWokOptions(false));
        syncOptions("dan_dan_noodle", buildDanDanOptions());
        syncOptions("zha_jiang_noodle", buildZhaJiangOptions());
        syncOptions("cold_noodle_shredded_chicken", buildColdChickenOptions());
        syncOptions("cucumber_salad", optionSeeds(
            optionSeed("remove", "走花生", "No Peanut", "0.00")
        ));
        syncOptions("edamame", List.of());
        syncOptions("shredded_potato", optionSeeds(
            optionSeed("remove", "走洋葱", "No Onion", "0.00"),
            optionSeed("remove", "走花生", "No Peanut", "0.00"),
            optionSeed("remove", "走香菜", "No Cilantro", "0.00")
        ));
        syncOptions("braised_beef_shank_salad", optionSeeds(
            optionSeed("remove", "走香菜", "No Cilantro", "0.00"),
            optionSeed("remove", "走花生碎", "No Crushed Peanut", "0.00"),
            optionSeed("remove", "走葱", "No Green Onion", "0.00")
        ));
        syncOptions("fried_spring_rolls", List.of());
        syncOptions("tempura_shrimp", List.of());
        syncOptions("fried_steamed_buns", List.of());
        syncOptions("fried_wontons", List.of());
    }

    private List<OptionSeed> buildSoupNoodleOptions(boolean includeExtraRadish) {
        List<OptionSeed> seeds = new ArrayList<>();
        seeds.addAll(buildComboOptions(false));
        seeds.addAll(buildCommonSoupSizes());
        seeds.addAll(buildNoodleTypeOptions("sanxi"));
        seeds.addAll(buildSpicyOptions());
        seeds.addAll(optionSeeds(
            optionSeed("addon", "加面", "Extra Noodle", "3.99"),
            optionSeed("addon", "加蛋", "Extra Tea Egg", "1.99"),
            optionSeed("addon", "加肉", "Extra Beef", "6.99"),
            optionSeed("addon", "加煎蛋", "Extra Fried Egg", "1.80"),
            optionSeed("addon", "加上海青", "Extra Bok Choy", "3.00"),
            optionSeed("addon", "加香菜", "Extra Cilantro", "0.00"),
            optionSeed("addon", "加葱", "Extra Green Onion", "0.00")
        ));
        if (includeExtraRadish) {
            seeds.add(optionSeed("addon", "加萝卜", "Extra Radish", "1.00"));
        }
        seeds.addAll(optionSeeds(
            optionSeed("remove", "走香菜", "No Cilantro", "0.00"),
            optionSeed("remove", "走葱", "No Green Onion", "0.00"),
            optionSeed("remove", "走牛肉", "No Beef", "0.00"),
            optionSeed("remove", "走面", "No Noodle", "0.00"),
            optionSeed("remove", "少面", "Less Noodle", "0.00")
        ));
        if (includeExtraRadish) {
            seeds.add(optionSeed("remove", "走萝卜", "No Radish", "0.00"));
        }
        return seeds;
    }

    private List<OptionSeed> buildVegetableNoodleOptions() {
        List<OptionSeed> seeds = new ArrayList<>();
        seeds.addAll(buildComboOptions(false));
        seeds.addAll(buildCommonSoupSizes());
        seeds.addAll(buildNoodleTypeOptions("sanxi"));
        seeds.addAll(buildSpicyOptions());
        seeds.addAll(optionSeeds(
            optionSeed("soup_base", "素汤", "Vegan Broth", "0.00"),
            optionSeed("soup_base", "肉汤", "Beef Broth", "0.00"),
            optionSeed("addon", "加面", "Extra Noodle", "3.99"),
            optionSeed("addon", "加蛋", "Extra Tea Egg", "1.99"),
            optionSeed("addon", "加肉", "Extra Beef", "6.99"),
            optionSeed("addon", "加煎蛋", "Extra Fried Egg", "1.80"),
            optionSeed("addon", "加上海青", "Extra Bok Choy", "3.00"),
            optionSeed("addon", "加西兰花", "Extra Broccoli", "1.20"),
            optionSeed("addon", "加玉米", "Extra Corn", "1.20"),
            optionSeed("addon", "加海菜", "Extra Seaweed", "1.20"),
            optionSeed("addon", "加蘑菇", "Extra Mushroom", "1.20"),
            optionSeed("addon", "加胡萝卜片", "Extra Carrot Slice", "1.20"),
            optionSeed("remove", "走面", "No Noodle", "0.00"),
            optionSeed("remove", "少面", "Less Noodle", "0.00"),
            optionSeed("remove", "走上海青", "No Bok Choy", "0.00"),
            optionSeed("remove", "走西兰花", "No Broccoli", "0.00"),
            optionSeed("remove", "走玉米", "No Corn", "0.00"),
            optionSeed("remove", "走蘑菇", "No Mushroom", "0.00"),
            optionSeed("remove", "走海菜", "No Seaweed", "0.00"),
            optionSeed("remove", "走胡萝卜片", "No Carrot Slice", "0.00")
        ));
        return seeds;
    }

    private List<OptionSeed> buildWokOptions(boolean includeMeatAdjustments) {
        List<OptionSeed> seeds = new ArrayList<>();
        seeds.addAll(buildComboOptions(true));
        seeds.addAll(buildSpicyOptions());
        seeds.addAll(optionSeeds(
            optionSeed("addon", "加西兰花", "Extra Broccoli", "1.20"),
            optionSeed("addon", "加包菜", "Extra Cabbage", "1.20")
        ));
        if (includeMeatAdjustments) {
            seeds.add(optionSeed("addon", "加肉", "Extra Meat", "6.99"));
            seeds.add(optionSeed("remove", "走肉", "No Meat", "0.00"));
        }
        seeds.addAll(optionSeeds(
            optionSeed("remove", "走西兰花", "No Broccoli", "0.00"),
            optionSeed("remove", "走包菜", "No Cabbage", "0.00")
        ));
        return seeds;
    }

    private List<OptionSeed> buildTomatoWokOptions() {
        List<OptionSeed> seeds = new ArrayList<>(buildWokOptions(true));
        seeds.add(optionSeed("remove", "走青椒", "No Green Pepper", "0.00"));
        return seeds;
    }

    private List<OptionSeed> buildDanDanOptions() {
        List<OptionSeed> seeds = new ArrayList<>();
        seeds.addAll(buildComboOptions(false));
        seeds.addAll(buildNoodleTypeOptions("sanxi"));
        seeds.addAll(buildSpicyOptions());
        seeds.addAll(optionSeeds(
            optionSeed("addon", "加面", "Extra Noodle", "3.99"),
            optionSeed("addon", "加蛋", "Extra Tea Egg", "1.99"),
            optionSeed("addon", "加肉", "Extra Meat", "6.99"),
            optionSeed("addon", "加煎蛋", "Extra Fried Egg", "1.80"),
            optionSeed("addon", "加上海青", "Extra Bok Choy", "3.00"),
            optionSeed("addon", "加酱", "Extra Sauce", "1.00"),
            optionSeed("addon", "加香菜", "Extra Cilantro", "0.00"),
            optionSeed("addon", "加葱", "Extra Green Onion", "0.00"),
            optionSeed("remove", "走香菜", "No Cilantro", "0.00"),
            optionSeed("remove", "走葱", "No Green Onion", "0.00"),
            optionSeed("remove", "走花生", "No Peanut", "0.00"),
            optionSeed("remove", "走上海青", "No Bok Choy", "0.00")
        ));
        return seeds;
    }

    private List<OptionSeed> buildZhaJiangOptions() {
        List<OptionSeed> seeds = new ArrayList<>();
        seeds.addAll(buildComboOptions(false));
        seeds.addAll(buildNoodleTypeOptions("leek_leaf"));
        seeds.addAll(buildSpicyOptions());
        seeds.addAll(optionSeeds(
            optionSeed("addon", "加面", "Extra Noodle", "3.99"),
            optionSeed("addon", "加蛋", "Extra Tea Egg", "1.99"),
            optionSeed("addon", "加肉", "Extra Meat", "6.99"),
            optionSeed("addon", "加煎蛋", "Extra Fried Egg", "1.80"),
            optionSeed("addon", "加上海青", "Extra Bok Choy", "3.00"),
            optionSeed("addon", "加酱", "Extra Sauce", "1.00"),
            optionSeed("addon", "加香菜", "Extra Cilantro", "0.50"),
            optionSeed("addon", "加葱", "Extra Green Onion", "0.50"),
            optionSeed("remove", "走香菜", "No Cilantro", "0.00"),
            optionSeed("remove", "走葱", "No Green Onion", "0.00"),
            optionSeed("remove", "走胡萝卜", "No Carrot", "0.00"),
            optionSeed("remove", "走黄瓜", "No Cucumber", "0.00"),
            optionSeed("remove", "走毛豆", "No Edamame", "0.00"),
            optionSeed("remove", "走上海青", "No Bok Choy", "0.00")
        ));
        return seeds;
    }

    private List<OptionSeed> buildColdChickenOptions() {
        List<OptionSeed> seeds = new ArrayList<>();
        seeds.addAll(buildComboOptions(false));
        seeds.addAll(buildNoodleTypeOptions("leek_leaf"));
        seeds.addAll(buildSpicyOptions());
        seeds.addAll(optionSeeds(
            optionSeed("addon", "加面", "Extra Noodle", "3.99"),
            optionSeed("addon", "加蛋", "Extra Tea Egg", "1.99"),
            optionSeed("addon", "加肉", "Extra Meat", "6.99"),
            optionSeed("addon", "加煎蛋", "Extra Fried Egg", "1.80"),
            optionSeed("addon", "加上海青", "Extra Bok Choy", "3.00"),
            optionSeed("addon", "加香菜", "Extra Cilantro", "0.00"),
            optionSeed("addon", "加葱", "Extra Green Onion", "0.00"),
            optionSeed("remove", "走香菜", "No Cilantro", "0.00"),
            optionSeed("remove", "走葱", "No Green Onion", "0.00"),
            optionSeed("remove", "走胡萝卜", "No Carrot", "0.00"),
            optionSeed("remove", "走花生", "No Peanut", "0.00")
        ));
        return seeds;
    }

    private List<OptionSeed> buildComboOptions(boolean friedEggDefault) {
        List<OptionSeed> seeds = new ArrayList<>();
        seeds.add(optionSeed("addon", "套餐", "Combo", "5.00"));
        if (friedEggDefault) {
            seeds.add(optionSeed("addon", "套餐煎蛋", "Combo Fried Egg", "0.00"));
            seeds.add(optionSeed("addon", "套餐卤蛋", "Combo Tea Egg", "0.00"));
        } else {
            seeds.add(optionSeed("addon", "套餐卤蛋", "Combo Tea Egg", "0.00"));
            seeds.add(optionSeed("addon", "套餐煎蛋", "Combo Fried Egg", "0.00"));
        }
        seeds.addAll(optionSeeds(
            optionSeed("addon", "套餐毛豆", "Combo Edamame", "0.00"),
            optionSeed("addon", "套餐土豆丝", "Combo Shredded Potato", "0.00"),
            optionSeed("addon", "套餐拌黄瓜", "Combo Cucumber Salad", "0.00")
        ));
        return seeds;
    }

    private List<OptionSeed> buildCommonSoupSizes() {
        return optionSeeds(
            optionSeed("size", "中碗", "Regular", "0.00"),
            optionSeed("size", "大碗", "Large", "2.00")
        );
    }

    private List<OptionSeed> buildNoodleTypeOptions(String defaultCode) {
        Map<String, OptionSeed> options = Map.of(
            "erxi", optionSeed("noodle_type", "二细", "Erxi", "0.00"),
            "sanxi", optionSeed("noodle_type", "三细", "Sanxi", "0.00"),
            "thin", optionSeed("noodle_type", "细", "Thin", "0.00"),
            "capillary", optionSeed("noodle_type", "毛细", "Capillary", "0.00"),
            "leek_leaf", optionSeed("noodle_type", "韭叶", "Leek Leaf", "0.00"),
            "wide", optionSeed("noodle_type", "宽", "Wide", "0.00"),
            "extra_wide", optionSeed("noodle_type", "大宽", "Extra Wide", "0.00")
        );
        List<String> order = new ArrayList<>(List.of("erxi", "sanxi", "thin", "capillary", "leek_leaf", "wide", "extra_wide"));
        order.remove(defaultCode);
        order.add(0, defaultCode);
        List<OptionSeed> seeds = new ArrayList<>();
        for (String key : order) {
            seeds.add(options.get(key));
        }
        return seeds;
    }

    private List<OptionSeed> buildSpicyOptions() {
        return optionSeeds(
            optionSeed("spicy_level", "不辣", "Non-Spicy", "0.00"),
            optionSeed("spicy_level", "少辣", "Mild", "0.00"),
            optionSeed("spicy_level", "正常辣", "Regular", "0.00"),
            optionSeed("spicy_level", "加辣", "Extra", "0.00")
        );
    }

    private OptionSeed optionSeed(String optionType, String nameZh, String nameEn, String priceDelta) {
        return new OptionSeed(optionType, nameZh, nameEn, priceDelta);
    }

    private List<OptionSeed> optionSeeds(OptionSeed... seeds) {
        return List.of(seeds);
    }

    private void syncOptions(String sku, List<OptionSeed> seeds) {
        Long menuItemId = findMenuItemId(sku);
        Set<String> allowedKeys = new LinkedHashSet<>();
        for (OptionSeed seed : seeds) {
            ensureOption(menuItemId, seed.optionType(), seed.nameZh(), seed.nameEn(), seed.priceDelta());
            allowedKeys.add(optionKey(menuItemId, seed.optionType(), seed.nameZh(), seed.nameEn()));
        }

        for (MenuItemOption option : menuItemOptionRepository.findAll()) {
            if (!menuItemId.equals(option.menu_item_id)) {
                continue;
            }
            String existingKey = optionKey(menuItemId, option.option_type, option.name_zh, option.name_en);
            if (allowedKeys.contains(existingKey)) {
                continue;
            }
            if (!forceOverwrite) {
                logger.info("Seeder skip existing extra menu option {} for sku {} because force overwrite is disabled", existingKey, sku);
                continue;
            }
            if (!Boolean.FALSE.equals(option.is_active)) {
                logger.info("Seeder force deactivating menu option {} for sku {}", existingKey, sku);
                option.is_active = false;
                option.updated_at = now();
                menuItemOptionRepository.save(option);
            }
        }
    }

    private String optionKey(Long menuItemId, String optionType, String nameZh, String nameEn) {
        return menuItemId + "|" + optionType + "|" + nameZh + "|" + nameEn;
    }

    private record OptionSeed(String optionType, String nameZh, String nameEn, String priceDelta) {}

    private record DiningTableSeed(
        String tableCode,
        String tableName,
        String areaName,
        String tableConfig,
        Integer capacity,
        boolean supportsSplit,
        int sortOrder
    ) {}

    private Role role(String name, String code) {
        Role role = new Role();
        role.setName(name);
        role.setCode(code);
        role.setCreated_at(now());
        role.setUpdated_at(now());
        return role;
    }

    private Station station(Long storeId, String code, String name, int sortOrder) {
        Station station = new Station();
        station.store_id = storeId;
        station.code = code;
        station.name = name;
        station.sort_order = sortOrder;
        station.is_active = true;
        station.created_at = now();
        station.updated_at = now();
        return station;
    }

    private MenuCategory category(Long storeId, String code, String nameZh, String nameEn, int sortOrder) {
        MenuCategory category = new MenuCategory();
        category.store_id = storeId;
        category.code = code;
        category.name_zh = nameZh;
        category.name_en = nameEn;
        category.sort_order = sortOrder;
        category.is_active = true;
        category.created_at = now();
        category.updated_at = now();
        return category;
    }

    private void ensureCategory(Long storeId, String code, String nameZh, String nameEn, int sortOrder) {
        MenuCategory existing = menuCategoryRepository.findAll().stream()
            .filter(category -> storeId.equals(category.store_id) && code.equals(category.code))
            .findFirst()
            .orElse(null);

        if (existing != null && !forceOverwrite) {
            logger.info("Seeder skip existing menu category {}", code);
            return;
        }

        MenuCategory target = existing == null ? new MenuCategory() : existing;
        logger.info("{} menu category {}", existing == null ? "Seeder inserting missing" : "Seeder force overwriting", code);
        target.store_id = storeId;
        target.code = code;
        target.name_zh = nameZh;
        target.name_en = nameEn;
        target.sort_order = sortOrder;
        target.is_active = true;
        target.updated_at = now();
        if (target.created_at == null) {
            target.created_at = now();
        }
        menuCategoryRepository.save(target);
    }

    private MenuItem item(
        Long storeId,
        Long categoryId,
        Long stationId,
        String sku,
        String nameZh,
        String nameEn,
        String itemType,
        String basePrice
    ) {
        MenuItem item = new MenuItem();
        item.store_id = storeId;
        item.category_id = categoryId;
        item.station_id = stationId;
        item.sku = sku;
        item.name_zh = nameZh;
        item.name_en = nameEn;
        item.item_type = itemType;
        item.base_price = new BigDecimal(basePrice);
        item.is_active = true;
        item.is_sold_out = false;
        item.created_at = now();
        item.updated_at = now();
        return item;
    }

    private void ensureItem(
        Long storeId,
        Long categoryId,
        Long stationId,
        String sku,
        String nameZh,
        String nameEn,
        String itemType,
        String basePrice
    ) {
        MenuItem existing = menuItemRepository.findAll().stream()
            .filter(item -> storeId.equals(item.store_id) && sku.equals(item.sku))
            .findFirst()
            .orElse(null);

        if (existing != null && !forceOverwrite) {
            logger.info("Seeder skip existing menu item {}", sku);
            return;
        }

        MenuItem target = existing == null ? new MenuItem() : existing;
        logger.info("{} menu item {}", existing == null ? "Seeder inserting missing" : "Seeder force overwriting", sku);
        target.store_id = storeId;
        target.category_id = categoryId;
        target.station_id = stationId;
        target.sku = sku;
        target.name_zh = nameZh;
        target.name_en = nameEn;
        target.item_type = itemType;
        target.base_price = new BigDecimal(basePrice);
        target.is_active = true;
        target.is_sold_out = false;
        target.updated_at = now();
        if (target.created_at == null) {
            target.created_at = now();
        }
        menuItemRepository.save(target);
    }

    private MenuItemOption option(Long menuItemId, String optionType, String nameZh, String nameEn, String priceDelta) {
        MenuItemOption option = new MenuItemOption();
        option.menu_item_id = menuItemId;
        option.option_type = optionType;
        option.name_zh = nameZh;
        option.name_en = nameEn;
        option.price_delta = new BigDecimal(priceDelta);
        option.is_active = true;
        option.created_at = now();
        option.updated_at = now();
        return option;
    }

    private void ensureOption(Long menuItemId, String optionType, String nameZh, String nameEn, String priceDelta) {
        MenuItemOption existing = menuItemOptionRepository.findAll().stream()
            .filter(option ->
                menuItemId.equals(option.menu_item_id)
                    && optionType.equals(option.option_type)
                    && nameZh.equals(option.name_zh)
                    && nameEn.equals(option.name_en)
            )
            .findFirst()
            .orElse(null);

        if (existing != null && !forceOverwrite) {
            logger.info("Seeder skip existing menu option {} for menu_item_id {}", nameZh, menuItemId);
            return;
        }

        MenuItemOption target = existing == null ? new MenuItemOption() : existing;
        logger.info("{} menu option {} for menu_item_id {}", existing == null ? "Seeder inserting missing" : "Seeder force overwriting", nameZh, menuItemId);
        target.menu_item_id = menuItemId;
        target.option_type = optionType;
        target.name_zh = nameZh;
        target.name_en = nameEn;
        target.price_delta = new BigDecimal(priceDelta);
        target.is_active = true;
        target.updated_at = now();
        if (target.created_at == null) {
            target.created_at = now();
        }
        menuItemOptionRepository.save(target);
    }

    private void syncTargetOptionPrices() {
        Map<String, BigDecimal> targetPrices = Map.of(
            "套餐", new BigDecimal("5.00"),
            "加面", new BigDecimal("3.99"),
            "加蛋", new BigDecimal("1.99"),
            "加肉", new BigDecimal("6.99"),
            "加上海青", new BigDecimal("3.00")
        );
        for (MenuItemOption option : menuItemOptionRepository.findAll()) {
            BigDecimal targetPrice = targetPrices.get(option.name_zh);
            if (targetPrice == null || targetPrice.compareTo(option.price_delta) == 0) {
                continue;
            }
            option.price_delta = targetPrice;
            option.updated_at = now();
            menuItemOptionRepository.save(option);
        }
    }

    private void deactivateLegacyMenuItems(Long storeId, Set<String> allowedSkus) {
        for (MenuItem item : menuItemRepository.findAll()) {
            if (!storeId.equals(item.store_id)) {
                continue;
            }
            if (allowedSkus.contains(item.sku)) {
                continue;
            }
            if (!Boolean.FALSE.equals(item.is_active)) {
                item.is_active = false;
                item.updated_at = now();
                menuItemRepository.save(item);
            }
        }
    }

    private Store firstStore() {
        return storeRepository.findAll().stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("Store seed prerequisite missing"));
    }

    private Long findRoleId(String code) {
        return roleRepository.findAll().stream()
            .filter(role -> code.equals(role.getCode()))
            .map(Role::getId)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Role not found for code " + code));
    }

    private Long findStationId(String code) {
        return stationRepository.findAll().stream()
            .filter(station -> code.equals(station.code))
            .map(station -> station.id)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Station not found for code " + code));
    }

    private MenuCategory findCategory(String code) {
        return menuCategoryRepository.findAll().stream()
            .filter(category -> code.equals(category.code))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Category not found for code " + code));
    }

    private Long findMenuItemId(String sku) {
        return menuItemRepository.findAll().stream()
            .filter(item -> sku.equals(item.sku))
            .map(item -> item.id)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Menu item not found for sku " + sku));
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private void seedOrganizations() {
        Organization existing = organizationRepository.findByCode("RAMEN_NOODLE_RESTAURANT");
        if (existing != null) {
            if (!forceOverwrite) {
                logger.info("Seeder skip existing organization {}", existing.code);
                return;
            }
            if (!"active".equals(existing.status)) {
                logger.info("Seeder force overwriting organization status {}", existing.code);
                existing.status = "active";
                existing.updated_at = now();
                organizationRepository.save(existing);
            }
            return;
        }

        Organization organization = new Organization();
        organization.name = "Ramen / Noodle Restaurant";
        organization.code = "RAMEN_NOODLE_RESTAURANT";
        organization.status = "active";
        organization.created_at = now();
        organization.updated_at = now();
        organizationRepository.save(organization);
    }

    private void attachStoresToOrganizations() {
        Organization organization = organizationRepository.findByCode("RAMEN_NOODLE_RESTAURANT");
        if (organization == null) {
            return;
        }

        for (Store store : storeRepository.findAll()) {
            if (store.organization_id != null) {
                continue;
            }
            store.organization_id = organization.id;
            store.updated_at = now();
            storeRepository.save(store);
        }
    }

    private void seedDiningTables() {
        Long storeId = firstStore().id;
        List<DiningTableSeed> seeds = List.of(
            new DiningTableSeed("T12", "10", "Main Hall", "split_supported", 4, true, 1),
            new DiningTableSeed("T12", "11", "Main Hall", "split_supported", 4, true, 2),
            new DiningTableSeed("T1", "1里", "Main Hall", "single_only", 4, false, 3),
            new DiningTableSeed("T9", "7", "Main Hall", "single_only", 4, false, 4),
            new DiningTableSeed("T10", "8", "Main Hall", "single_only", 4, false, 5),
            new DiningTableSeed("T11", "9", "Main Hall", "single_only", 4, false, 6),
            new DiningTableSeed("T2", "1外", "Main Hall", "split_supported", 4, true, 7),
            new DiningTableSeed("T3", "2里", "Main Hall", "single_only", 4, false, 8),
            new DiningTableSeed("T4", "2外", "Main Hall", "split_supported", 4, true, 9),
            new DiningTableSeed("T5", "3", "Patio", "split_supported", 4, true, 10),
            new DiningTableSeed("T6", "4", "Patio", "split_supported", 4, true, 11),
            new DiningTableSeed("T7", "5", "Window", "split_supported", 4, true, 12),
            new DiningTableSeed("T8", "6", "Window", "single_only", 4, false, 13)
        );
        syncDiningTables(storeId, seeds);
    }

    private void seedStoreKdsDisplayConfigs() {
        Long storeId = firstStore().id;
        ensureKdsDisplayConfig(storeId, "FRONTDESK_TABLE_BOARD", "top_nav", "compact", "standard", "{\"layout\":\"ipad_workstation\"}");
        ensureKdsDisplayConfig(storeId, "FRONTDESK_MENU", "top_nav", "compact", "standard", "{\"layout\":\"three_column\"}");
        ensureKdsDisplayConfig(storeId, "KDS_GRAB", "top_bar", "compact", "standard", "{\"stations\":[\"NOODLE\",\"WOK\",\"DEEPFRIED\",\"COLD\"]}");
        ensureKdsDisplayConfig(storeId, "KDS_HOT_KITCHEN", "top_bar", "compact", "standard", "{\"stations\":[\"WOK\",\"DEEPFRIED\"]}");
        ensureKdsDisplayConfig(storeId, "KDS_NOODLE_MONITOR", "top_bar", "compact", "standard", "{\"stations\":[\"NOODLE\",\"WOK\"]}");
        ensureKdsDisplayConfig(storeId, "PICKUP_BOARD", "top_bar", "standard", "standard", "{\"source\":\"READY_FOR_PICKUP\"}");
    }

    private void seedRamenTemplate() {
        Organization organization = organizationRepository.findByCode("RAMEN_NOODLE_RESTAURANT");
        Store store = firstStore();
        RestaurantTemplate existing = restaurantTemplateRepository.findByCode("RAMEN_NOODLE_SHOP_TEMPLATE");
        if (existing != null && !forceOverwrite) {
            logger.info("Seeder skip existing restaurant template {}", existing.code);
            return;
        }
        RestaurantTemplate template = existing == null ? new RestaurantTemplate() : existing;

        template.organization_id = organization == null ? null : organization.id;
        template.name = "Ramen / Noodle Shop Template";
        template.code = "RAMEN_NOODLE_SHOP_TEMPLATE";
        template.description = "Template created from the current ramen / noodle restaurant setup.";
        template.source_store_id = store.id;
        template.default_station_setup_json = toJson(
            stationRepository.findAll().stream()
                .filter(station -> store.id.equals(station.store_id))
                .sorted((left, right) -> Integer.compare(left.sort_order == null ? 0 : left.sort_order, right.sort_order == null ? 0 : right.sort_order))
                .map(station -> Map.of(
                    "code", station.code,
                    "name", station.name,
                    "sort_order", station.sort_order == null ? 0 : station.sort_order,
                    "is_active", Boolean.TRUE.equals(station.is_active)
                ))
                .toList()
        );
        template.default_kds_display_rules_json = toJson(
            storeKdsDisplayConfigRepository.findAllByStoreIdOrderByIdAsc(store.id).stream()
                .map(config -> Map.of(
                    "screen_code", config.screen_code,
                    "header_layout", nullToEmpty(config.header_layout),
                    "density_mode", nullToEmpty(config.density_mode),
                    "card_size_mode", nullToEmpty(config.card_size_mode),
                    "config_json", nullToEmpty(config.config_json)
                ))
                .toList()
        );
        template.default_menu_category_structure_json = toJson(
            menuCategoryRepository.findAll().stream()
                .filter(category -> store.id.equals(category.store_id))
                .sorted((left, right) -> Integer.compare(left.sort_order == null ? 0 : left.sort_order, right.sort_order == null ? 0 : right.sort_order))
                .map(category -> Map.of(
                    "code", category.code,
                    "name_zh", category.name_zh,
                    "name_en", category.name_en,
                    "sort_order", category.sort_order == null ? 0 : category.sort_order,
                    "is_active", Boolean.TRUE.equals(category.is_active)
                ))
                .toList()
        );
        template.default_dining_table_layout_rules_json = toJson(
            diningTableRepository.findAllByStoreIdOrderBySortOrderAscIdAsc(store.id).stream()
                .map(table -> Map.of(
                    "table_code", table.table_code,
                    "table_name", table.table_name,
                    "area_name", table.area_name,
                    "table_config", nullToEmpty(table.table_config),
                    "capacity", table.capacity == null ? 0 : table.capacity,
                    "supports_split", Boolean.TRUE.equals(table.supports_split),
                    "sort_order", table.sort_order == null ? 0 : table.sort_order
                ))
                .toList()
        );
        template.default_role_setup_json = toJson(
            roleRepository.findAll().stream()
                .map(role -> Map.of(
                    "name", role.getName(),
                    "code", role.getCode()
                ))
                .toList()
        );
        template.is_active = true;
        template.updated_at = now();
        if (template.created_at == null) {
            template.created_at = now();
        }
        restaurantTemplateRepository.save(template);
    }

    private void seedPrintingCenter() {
        Store store = firstStore();
        if (store.printing_enabled == null) {
            store.printing_enabled = true;
            store.updated_at = now();
            storeRepository.save(store);
        }

        PrinterConfig defaultPrinter = printerConfigRepository.findAllByStoreIdOrderByIdAsc(store.id).stream()
            .findFirst()
            .orElseGet(() -> {
                PrinterConfig printerConfig = new PrinterConfig();
                printerConfig.store_id = store.id;
                printerConfig.name = "Main Print Center Printer";
                printerConfig.ip_address = "192.168.2.200";
                printerConfig.port = 9100;
                printerConfig.printer_type = "ESC_POS_TCP";
                printerConfig.text_encoding = "GBK";
                printerConfig.escpos_code_page = null;
                printerConfig.font_size = EscPosFontSizeMode.DEFAULT_CODE;
                printerConfig.enabled = true;
                printerConfig.paper_width_mm = 80;
                printerConfig.timeout_ms = 3000;
                printerConfig.created_at = now();
                printerConfig.updated_at = now();
                return printerConfigRepository.save(printerConfig);
            });
        if (defaultPrinter.text_encoding == null || defaultPrinter.text_encoding.isBlank()) {
            defaultPrinter.text_encoding = "GBK";
            defaultPrinter.updated_at = now();
            printerConfigRepository.save(defaultPrinter);
        }
        if (defaultPrinter.font_size_mode == null || defaultPrinter.font_size_mode.isBlank()) {
            defaultPrinter.font_size_mode = EscPosFontSizeMode.DEFAULT_CODE;
            defaultPrinter.updated_at = now();
            printerConfigRepository.save(defaultPrinter);
        }
        for (PrinterConfig printerConfig : printerConfigRepository.findAllByStoreIdOrderByIdAsc(store.id)) {
            if (printerConfig.font_size == null || printerConfig.font_size.isBlank()) {
                printerConfig.font_size = EscPosFontSizeMode.DEFAULT_CODE;
                printerConfig.updated_at = now();
                printerConfigRepository.save(printerConfig);
            }
        }

        for (String moduleCode : PrintModuleCode.ALL) {
            PrinterAssignment assignment = printerAssignmentRepository.findByStoreIdAndModuleCode(store.id, moduleCode)
                .orElseGet(PrinterAssignment::new);
            boolean isNew = assignment.id == null;
            if (!isNew && !forceOverwrite) {
                logger.info("Seeder skip existing printer assignment {}", moduleCode);
                continue;
            }
            logger.info("{} printer assignment {}", isNew ? "Seeder inserting missing" : "Seeder force overwriting", moduleCode);
            assignment.store_id = store.id;
            assignment.module_code = moduleCode;
            assignment.printer_id = PrintModuleCode.PHASE_ONE_ENABLED.contains(moduleCode) ? defaultPrinter.id : null;
            assignment.enabled = PrintModuleCode.PHASE_ONE_ENABLED.contains(moduleCode);
            assignment.font_size = EscPosFontSizeMode.DEFAULT_CODE;
            if (isNew) {
                assignment.created_at = now();
            }
            assignment.updated_at = now();
            printerAssignmentRepository.save(assignment);
        }
    }

    private void syncDiningTables(Long storeId, List<DiningTableSeed> seeds) {
        List<DiningTable> existingTables = diningTableRepository.findAllByStoreIdOrderBySortOrderAscIdAsc(storeId);
        Set<Long> usedTableIds = new LinkedHashSet<>();
        for (DiningTableSeed seed : seeds) {
            if (!forceOverwrite && diningTableSeedExists(existingTables, seeds, seed)) {
                logger.info("Seeder skip existing dining table {} / {}", seed.tableCode(), seed.tableName());
                continue;
            }
            DiningTable target = findReusableDiningTable(existingTables, usedTableIds, seed);
            saveDiningTableSeed(storeId, target, seed);
            if (target.id != null) {
                usedTableIds.add(target.id);
            }
        }
        if (!forceOverwrite) {
            logger.info("Skipping dining table deactivation because app.seed.force-overwrite=false");
            return;
        }
        for (DiningTable table : existingTables) {
            if (table.id != null && !usedTableIds.contains(table.id) && Boolean.TRUE.equals(table.is_active)) {
                logger.info("Seeder force deactivating dining table {} / {}", table.table_code, table.table_name);
                table.is_active = false;
                table.updated_at = now();
                diningTableRepository.save(table);
            }
        }
    }

    private boolean diningTableSeedExists(List<DiningTable> existingTables, List<DiningTableSeed> seeds, DiningTableSeed seed) {
        long seedCodeCount = seeds.stream().filter(candidate -> seed.tableCode().equals(candidate.tableCode())).count();
        return existingTables.stream().anyMatch(table ->
            seed.tableCode().equals(table.table_code)
                && (seedCodeCount <= 1 || seed.tableName().equals(table.table_name))
        );
    }

    private DiningTable findReusableDiningTable(List<DiningTable> existingTables, Set<Long> usedTableIds, DiningTableSeed seed) {
        List<DiningTable> sameCodeTables = existingTables.stream()
            .filter(table -> table.id != null && !usedTableIds.contains(table.id))
            .filter(table -> seed.tableCode().equals(table.table_code))
            .toList();
        return sameCodeTables.stream()
            .filter(table -> seed.tableName().equals(table.table_name))
            .findFirst()
            .orElseGet(() -> sameCodeTables.stream().findFirst().orElseGet(DiningTable::new));
    }

    private void saveDiningTableSeed(Long storeId, DiningTable target, DiningTableSeed seed) {
        logger.info("{} dining table {} / {}", target.id == null ? "Seeder inserting missing" : "Seeder force overwriting", seed.tableCode(), seed.tableName());
        target.store_id = storeId;
        target.table_code = seed.tableCode();
        target.table_name = seed.tableName();
        target.area_name = seed.areaName();
        target.table_config = seed.tableConfig();
        target.capacity = seed.capacity();
        target.supports_split = seed.supportsSplit();
        target.sort_order = seed.sortOrder();
        target.is_active = true;
        target.updated_at = now();
        if (target.created_at == null) {
            target.created_at = now();
        }
        diningTableRepository.save(target);
    }

    private void ensureKdsDisplayConfig(
        Long storeId,
        String screenCode,
        String headerLayout,
        String densityMode,
        String cardSizeMode,
        String configJson
    ) {
        StoreKdsDisplayConfig existing = storeKdsDisplayConfigRepository.findByStoreIdAndScreenCode(storeId, screenCode);
        if (existing != null && !forceOverwrite) {
            logger.info("Seeder skip existing KDS display config {}", screenCode);
            return;
        }
        StoreKdsDisplayConfig target = existing == null ? new StoreKdsDisplayConfig() : existing;
        logger.info("{} KDS display config {}", existing == null ? "Seeder inserting missing" : "Seeder force overwriting", screenCode);
        target.store_id = storeId;
        target.screen_code = screenCode;
        target.header_layout = headerLayout;
        target.density_mode = densityMode;
        target.card_size_mode = cardSizeMode;
        target.config_json = configJson;
        target.updated_at = now();
        if (target.created_at == null) {
            target.created_at = now();
        }
        storeKdsDisplayConfigRepository.save(target);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize platform template seed data", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
