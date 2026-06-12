package com.restaurant.system.platform.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.platform.dto.CreateStoreFromTemplateRequest;
import com.restaurant.system.platform.dto.PlatformAdminOverviewResponse;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.entity.RestaurantTemplate;
import com.restaurant.system.platform.entity.StoreKdsDisplayConfig;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.platform.repository.RestaurantTemplateRepository;
import com.restaurant.system.platform.repository.StoreKdsDisplayConfigRepository;
import com.restaurant.system.platform.service.PlatformAdminService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminServiceImpl implements PlatformAdminService {

    private static final Pattern NATURAL_CODE_PATTERN = Pattern.compile("^([A-Za-z]+)?\\s*(\\d+)?(.*)$");

    private final OrganizationRepository organizationRepository;
    private final RestaurantTemplateRepository restaurantTemplateRepository;
    private final StoreRepository storeRepository;
    private final StationRepository stationRepository;
    private final DiningTableRepository diningTableRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final StoreKdsDisplayConfigRepository storeKdsDisplayConfigRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ObjectMapper objectMapper;

    public PlatformAdminServiceImpl(
        OrganizationRepository organizationRepository,
        RestaurantTemplateRepository restaurantTemplateRepository,
        StoreRepository storeRepository,
        StationRepository stationRepository,
        DiningTableRepository diningTableRepository,
        MenuCategoryRepository menuCategoryRepository,
        MenuItemRepository menuItemRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        StoreKdsDisplayConfigRepository storeKdsDisplayConfigRepository,
        UserRepository userRepository,
        RoleRepository roleRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.restaurantTemplateRepository = restaurantTemplateRepository;
        this.storeRepository = storeRepository;
        this.stationRepository = stationRepository;
        this.diningTableRepository = diningTableRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.storeKdsDisplayConfigRepository = storeKdsDisplayConfigRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public PlatformAdminOverviewResponse getOverview(Long storeId) {
        PlatformAdminOverviewResponse response = new PlatformAdminOverviewResponse();
        response.organizations = getOrganizations();
        response.templates = getTemplates();
        response.stores = getStores();
        response.roles = getRoles();
        response.users = getUsers(storeId);
        response.stations = getStations(storeId);
        response.dining_tables = getDiningTables(storeId);
        response.menu_categories = getMenuCategories(storeId);
        response.menu_items = getMenuItems(storeId);
        response.menu_item_options = getMenuItemOptions(storeId);
        response.kds_display_configs = getKdsDisplayConfigs(storeId);
        return response;
    }

    @Override
    public List<Organization> getOrganizations() {
        return organizationRepository.findAllByOrderByIdAsc();
    }

    @Override
    @Transactional
    public Organization saveOrganization(Organization organization) {
        Organization target = organization.id == null
            ? new Organization()
            : organizationRepository.findById(organization.id).orElseThrow(() -> new BusinessException("Organization not found"));
        target.name = organization.name;
        target.code = organization.code;
        target.status = organization.status == null ? "active" : organization.status;
        stamp(target, organization.id == null);
        return organizationRepository.save(target);
    }

    @Override
    public List<RestaurantTemplate> getTemplates() {
        return restaurantTemplateRepository.findAllByOrderByIdAsc();
    }

    @Override
    @Transactional
    public RestaurantTemplate saveTemplate(RestaurantTemplate template) {
        RestaurantTemplate target = template.id == null
            ? new RestaurantTemplate()
            : restaurantTemplateRepository.findById(template.id).orElseThrow(() -> new BusinessException("Template not found"));
        target.organization_id = template.organization_id;
        target.name = template.name;
        target.code = template.code;
        target.description = template.description;
        target.source_store_id = template.source_store_id;
        target.default_station_setup_json = template.default_station_setup_json;
        target.default_kds_display_rules_json = template.default_kds_display_rules_json;
        target.default_menu_category_structure_json = template.default_menu_category_structure_json;
        target.default_dining_table_layout_rules_json = template.default_dining_table_layout_rules_json;
        target.default_role_setup_json = template.default_role_setup_json;
        target.is_active = template.is_active == null ? true : template.is_active;
        stamp(target, template.id == null);
        return restaurantTemplateRepository.save(target);
    }

    @Override
    public List<Store> getStores() {
        return storeRepository.findAll().stream().sorted(Comparator.comparing(store -> store.id)).toList();
    }

    @Override
    @Transactional
    public Store saveStore(Store store) {
        Store target = store.id == null
            ? new Store()
            : storeRepository.findById(store.id).orElseThrow(() -> new BusinessException("Store not found"));
        target.organization_id = store.organization_id;
        target.name = store.name;
        target.code = store.code;
        target.status = store.status == null ? "active" : store.status;
        target.enable_bar_kitchen_tasks = store.enable_bar_kitchen_tasks;
        target.printing_enabled = store.printing_enabled == null ? true : store.printing_enabled;
        stamp(target, store.id == null);
        return storeRepository.save(target);
    }

    @Override
    @Transactional
    public Store createStoreFromTemplate(CreateStoreFromTemplateRequest request) {
        Store store = new Store();
        store.organization_id = request.organization_id;
        store.name = request.name;
        store.code = request.code;
        store.status = request.status == null ? "active" : request.status;
        store.enable_bar_kitchen_tasks = request.enable_bar_kitchen_tasks;
        store.printing_enabled = true;
        stamp(store, true);
        Store savedStore = storeRepository.save(store);

        if (request.template_id == null) {
            return savedStore;
        }

        RestaurantTemplate template = restaurantTemplateRepository.findById(request.template_id)
            .orElseThrow(() -> new BusinessException("Template not found"));

        copyStationsFromTemplate(savedStore.id, template.default_station_setup_json);
        copyDiningTablesFromTemplate(savedStore.id, template.default_dining_table_layout_rules_json);
        copyMenuCategoriesFromTemplate(savedStore.id, template.default_menu_category_structure_json);
        copyKdsConfigsFromTemplate(savedStore.id, template.default_kds_display_rules_json);

        return savedStore;
    }

    @Override
    public List<Station> getStations(Long storeId) {
        return stationRepository.findAll().stream()
            .filter(station -> storeId.equals(station.store_id))
            .sorted(Comparator.comparing((Station station) -> station.sort_order == null ? 0 : station.sort_order).thenComparing(station -> station.id))
            .toList();
    }

    @Override
    @Transactional
    public Station saveStation(Station station) {
        Station target = station.id == null
            ? new Station()
            : stationRepository.findById(station.id).orElseThrow(() -> new BusinessException("Station not found"));
        target.store_id = station.store_id;
        target.name = station.name;
        target.code = station.code;
        target.sort_order = station.sort_order;
        target.is_active = station.is_active == null ? true : station.is_active;
        stamp(target, station.id == null);
        return stationRepository.save(target);
    }

    @Override
    public List<DiningTable> getDiningTables(Long storeId) {
        List<DiningTable> tables = new ArrayList<>(diningTableRepository.findAllByStoreIdOrderBySortOrderAscIdAsc(storeId));
        tables.sort(Comparator
            .comparing((DiningTable table) -> extractNaturalPrefix(table.table_code))
            .thenComparingInt(table -> extractNaturalNumber(table.table_code))
            .thenComparing(table -> extractNaturalSuffix(table.table_code))
            .thenComparing(table -> table.table_code == null ? "" : table.table_code)
            .thenComparing(table -> table.id == null ? Long.MAX_VALUE : table.id)
        );
        return tables;
    }

    @Override
    @Transactional
    public DiningTable saveDiningTable(DiningTable diningTable) {
        DiningTable target = diningTable.id == null
            ? new DiningTable()
            : diningTableRepository.findById(diningTable.id).orElseThrow(() -> new BusinessException("Dining table not found"));
        target.store_id = diningTable.store_id;
        target.table_code = diningTable.table_code;
        target.table_name = diningTable.table_name;
        target.area_name = diningTable.area_name;
        target.table_config = diningTable.table_config;
        target.capacity = diningTable.capacity;
        target.supports_split = diningTable.supports_split;
        target.sort_order = diningTable.sort_order;
        target.is_active = diningTable.is_active == null ? true : diningTable.is_active;
        stamp(target, diningTable.id == null);
        return diningTableRepository.save(target);
    }

    @Override
    public List<MenuCategory> getMenuCategories(Long storeId) {
        return menuCategoryRepository.findAll().stream()
            .filter(category -> storeId.equals(category.store_id))
            .sorted(Comparator.comparing((MenuCategory category) -> category.sort_order == null ? 0 : category.sort_order).thenComparing(category -> category.id))
            .toList();
    }

    @Override
    @Transactional
    public MenuCategory saveMenuCategory(MenuCategory menuCategory) {
        MenuCategory target = menuCategory.id == null
            ? new MenuCategory()
            : menuCategoryRepository.findById(menuCategory.id).orElseThrow(() -> new BusinessException("Menu category not found"));
        target.store_id = menuCategory.store_id;
        target.code = menuCategory.code;
        target.name_zh = menuCategory.name_zh;
        target.name_en = menuCategory.name_en;
        target.sort_order = menuCategory.sort_order;
        target.is_active = menuCategory.is_active == null ? true : menuCategory.is_active;
        stamp(target, menuCategory.id == null);
        return menuCategoryRepository.save(target);
    }

    @Override
    public List<MenuItem> getMenuItems(Long storeId) {
        return menuItemRepository.findAll().stream()
            .filter(item -> storeId.equals(item.store_id))
            .sorted(Comparator.comparing(item -> item.id))
            .toList();
    }

    @Override
    @Transactional
    public MenuItem saveMenuItem(MenuItem menuItem) {
        MenuItem target = menuItem.id == null
            ? new MenuItem()
            : menuItemRepository.findById(menuItem.id).orElseThrow(() -> new BusinessException("Menu item not found"));
        target.store_id = menuItem.store_id;
        target.category_id = menuItem.category_id;
        target.station_id = menuItem.station_id;
        target.sku = menuItem.sku;
        target.name_zh = menuItem.name_zh;
        target.name_en = menuItem.name_en;
        target.item_type = menuItem.item_type;
        target.base_price = menuItem.base_price;
        target.cost_per_item = menuItem.cost_per_item;
        target.is_active = menuItem.is_active == null ? true : menuItem.is_active;
        target.is_sold_out = menuItem.is_sold_out == null ? false : menuItem.is_sold_out;
        stamp(target, menuItem.id == null);
        return menuItemRepository.save(target);
    }

    @Override
    public List<MenuItemOption> getMenuItemOptions(Long storeId) {
        List<Long> menuItemIds = getMenuItems(storeId).stream().map(item -> item.id).toList();
        return menuItemOptionRepository.findAll().stream()
            .filter(option -> menuItemIds.contains(option.menu_item_id))
            .sorted(Comparator.comparing(option -> option.id))
            .toList();
    }

    @Override
    @Transactional
    public MenuItemOption saveMenuItemOption(MenuItemOption menuItemOption) {
        MenuItemOption target = menuItemOption.id == null
            ? new MenuItemOption()
            : menuItemOptionRepository.findById(menuItemOption.id).orElseThrow(() -> new BusinessException("Menu item option not found"));
        target.menu_item_id = menuItemOption.menu_item_id;
        target.option_type = menuItemOption.option_type;
        target.name_zh = menuItemOption.name_zh;
        target.name_en = menuItemOption.name_en;
        target.price_delta = menuItemOption.price_delta;
        target.is_active = menuItemOption.is_active == null ? true : menuItemOption.is_active;
        stamp(target, menuItemOption.id == null);
        return menuItemOptionRepository.save(target);
    }

    @Override
    public List<StoreKdsDisplayConfig> getKdsDisplayConfigs(Long storeId) {
        return storeKdsDisplayConfigRepository.findAllByStoreIdOrderByIdAsc(storeId);
    }

    @Override
    @Transactional
    public StoreKdsDisplayConfig saveKdsDisplayConfig(StoreKdsDisplayConfig config) {
        StoreKdsDisplayConfig target = config.id == null
            ? new StoreKdsDisplayConfig()
            : storeKdsDisplayConfigRepository.findById(config.id).orElseThrow(() -> new BusinessException("KDS config not found"));
        target.store_id = config.store_id;
        target.screen_code = config.screen_code;
        target.header_layout = config.header_layout;
        target.density_mode = config.density_mode;
        target.card_size_mode = config.card_size_mode;
        target.config_json = config.config_json;
        stamp(target, config.id == null);
        return storeKdsDisplayConfigRepository.save(target);
    }

    @Override
    public List<User> getUsers(Long storeId) {
        return userRepository.findAll().stream()
            .filter(user -> storeId.equals(user.getStore_id()))
            .sorted(Comparator.comparing(User::getId))
            .toList();
    }

    @Override
    @Transactional
    public User saveUser(User user) {
        User target = user.getId() == null
            ? new User()
            : userRepository.findById(user.getId()).orElseThrow(() -> new BusinessException("User not found"));
        target.setStore_id(user.getStore_id());
        target.setRole_id(user.getRole_id());
        target.setUsername(user.getUsername());
        target.setFull_name(user.getFull_name());
        target.setPhone(user.getPhone());
        target.setStatus(user.getStatus() == null ? "active" : user.getStatus());
        stamp(target, user.getId() == null);
        return userRepository.save(target);
    }

    @Override
    public List<Role> getRoles() {
        return roleRepository.findAll().stream().sorted(Comparator.comparing(Role::getId)).toList();
    }

    @Override
    @Transactional
    public Role saveRole(Role role) {
        Role target = role.getId() == null
            ? new Role()
            : roleRepository.findById(role.getId()).orElseThrow(() -> new BusinessException("Role not found"));
        target.setName(role.getName());
        target.setCode(role.getCode());
        stamp(target, role.getId() == null);
        return roleRepository.save(target);
    }

    private void copyStationsFromTemplate(Long storeId, String stationJson) {
        if (stationJson == null || stationJson.isBlank()) {
            return;
        }
        try {
            List<TemplateStationSeed> seeds = objectMapper.readValue(stationJson, new TypeReference<>() {});
            for (TemplateStationSeed seed : seeds) {
                Station station = new Station();
                station.store_id = storeId;
                station.code = seed.code();
                station.name = seed.name();
                station.sort_order = seed.sort_order();
                station.is_active = seed.is_active();
                stamp(station, true);
                stationRepository.save(station);
            }
        } catch (Exception exception) {
            throw new BusinessException("Failed to copy station template: " + exception.getMessage());
        }
    }

    private void copyDiningTablesFromTemplate(Long storeId, String tableJson) {
        if (tableJson == null || tableJson.isBlank()) {
            return;
        }
        try {
            List<TemplateDiningTableSeed> seeds = objectMapper.readValue(tableJson, new TypeReference<>() {});
            for (TemplateDiningTableSeed seed : seeds) {
                DiningTable table = new DiningTable();
                table.store_id = storeId;
                table.table_code = seed.table_code();
                table.table_name = seed.table_name();
                table.area_name = seed.area_name();
                table.table_config = seed.table_config();
                table.capacity = seed.capacity();
                table.supports_split = seed.supports_split();
                table.sort_order = seed.sort_order();
                table.is_active = true;
                stamp(table, true);
                diningTableRepository.save(table);
            }
        } catch (Exception exception) {
            throw new BusinessException("Failed to copy dining table template: " + exception.getMessage());
        }
    }

    private void copyMenuCategoriesFromTemplate(Long storeId, String categoryJson) {
        if (categoryJson == null || categoryJson.isBlank()) {
            return;
        }
        try {
            List<TemplateCategorySeed> seeds = objectMapper.readValue(categoryJson, new TypeReference<>() {});
            for (TemplateCategorySeed seed : seeds) {
                MenuCategory category = new MenuCategory();
                category.store_id = storeId;
                category.code = seed.code();
                category.name_zh = seed.name_zh();
                category.name_en = seed.name_en();
                category.sort_order = seed.sort_order();
                category.is_active = seed.is_active();
                stamp(category, true);
                menuCategoryRepository.save(category);
            }
        } catch (Exception exception) {
            throw new BusinessException("Failed to copy menu category template: " + exception.getMessage());
        }
    }

    private void copyKdsConfigsFromTemplate(Long storeId, String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return;
        }
        try {
            List<TemplateKdsConfigSeed> seeds = objectMapper.readValue(configJson, new TypeReference<>() {});
            for (TemplateKdsConfigSeed seed : seeds) {
                StoreKdsDisplayConfig config = new StoreKdsDisplayConfig();
                config.store_id = storeId;
                config.screen_code = seed.screen_code();
                config.header_layout = seed.header_layout();
                config.density_mode = seed.density_mode();
                config.card_size_mode = seed.card_size_mode();
                config.config_json = seed.config_json();
                stamp(config, true);
                storeKdsDisplayConfigRepository.save(config);
            }
        } catch (Exception exception) {
            throw new BusinessException("Failed to copy KDS config template: " + exception.getMessage());
        }
    }

    private void stamp(Organization entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(RestaurantTemplate entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(Store entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(Station entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(DiningTable entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(MenuCategory entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(MenuItem entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(MenuItemOption entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(StoreKdsDisplayConfig entity, boolean create) {
        if (create) {
            entity.created_at = LocalDateTime.now();
        }
        entity.updated_at = LocalDateTime.now();
    }

    private void stamp(User entity, boolean create) {
        if (create) {
            entity.setCreated_at(LocalDateTime.now());
        }
        entity.setUpdated_at(LocalDateTime.now());
    }

    private void stamp(Role entity, boolean create) {
        if (create) {
            entity.setCreated_at(LocalDateTime.now());
        }
        entity.setUpdated_at(LocalDateTime.now());
    }

    private String extractNaturalPrefix(String value) {
        Matcher matcher = NATURAL_CODE_PATTERN.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            return value == null ? "" : value;
        }
        return matcher.group(1) == null ? "" : matcher.group(1);
    }

    private int extractNaturalNumber(String value) {
        Matcher matcher = NATURAL_CODE_PATTERN.matcher(value == null ? "" : value.trim());
        if (!matcher.matches() || matcher.group(2) == null || matcher.group(2).isBlank()) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private String extractNaturalSuffix(String value) {
        Matcher matcher = NATURAL_CODE_PATTERN.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            return "";
        }
        return matcher.group(3) == null ? "" : matcher.group(3);
    }

    private record TemplateStationSeed(String code, String name, Integer sort_order, Boolean is_active) {}
    private record TemplateDiningTableSeed(
        String table_code,
        String table_name,
        String area_name,
        String table_config,
        Integer capacity,
        Boolean supports_split,
        Integer sort_order
    ) {}
    private record TemplateCategorySeed(String code, String name_zh, String name_en, Integer sort_order, Boolean is_active) {}
    private record TemplateKdsConfigSeed(
        String screen_code,
        String header_layout,
        String density_mode,
        String card_size_mode,
        String config_json
    ) {}
}
