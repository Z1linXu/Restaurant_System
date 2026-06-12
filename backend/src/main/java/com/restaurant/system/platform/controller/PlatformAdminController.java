package com.restaurant.system.platform.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.platform.dto.CreateStoreFromTemplateRequest;
import com.restaurant.system.platform.dto.PlatformAdminOverviewResponse;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.entity.RestaurantTemplate;
import com.restaurant.system.platform.entity.StoreKdsDisplayConfig;
import com.restaurant.system.platform.service.PlatformAdminService;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/platform")
public class PlatformAdminController {

    private final PlatformAdminService platformAdminService;
    private final AuthorizationService authorizationService;
    private final FeatureFlagService featureFlagService;

    public PlatformAdminController(
        PlatformAdminService platformAdminService,
        AuthorizationService authorizationService,
        FeatureFlagService featureFlagService
    ) {
        this.platformAdminService = platformAdminService;
        this.authorizationService = authorizationService;
        this.featureFlagService = featureFlagService;
    }

    @GetMapping("/overview")
    public ApiResponse<PlatformAdminOverviewResponse> getOverview(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG, Capability.ADMIN_USER_ROLE_MANAGE);
        return ApiResponse.success(platformAdminService.getOverview(store_id));
    }

    @GetMapping("/organizations")
    public ApiResponse<List<Organization>> getOrganizations() {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getOrganizations());
    }

    @PostMapping("/organizations")
    public ApiResponse<Organization> createOrganization(@RequestBody Organization organization) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Organization saved", platformAdminService.saveOrganization(organization));
    }

    @PutMapping("/organizations/{id}")
    public ApiResponse<Organization> updateOrganization(@PathVariable Long id, @RequestBody Organization organization) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        organization.id = id;
        return ApiResponse.success("Organization updated", platformAdminService.saveOrganization(organization));
    }

    @GetMapping("/templates")
    public ApiResponse<List<RestaurantTemplate>> getTemplates() {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getTemplates());
    }

    @PostMapping("/templates")
    public ApiResponse<RestaurantTemplate> createTemplate(@RequestBody RestaurantTemplate template) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Template saved", platformAdminService.saveTemplate(template));
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<RestaurantTemplate> updateTemplate(@PathVariable Long id, @RequestBody RestaurantTemplate template) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        template.id = id;
        return ApiResponse.success("Template updated", platformAdminService.saveTemplate(template));
    }

    @GetMapping("/stores")
    public ApiResponse<List<Store>> getStores() {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getStores());
    }

    @PostMapping("/stores")
    public ApiResponse<Store> createStore(@RequestBody Store store) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Store saved", platformAdminService.saveStore(store));
    }

    @PutMapping("/stores/{id}")
    public ApiResponse<Store> updateStore(@PathVariable Long id, @RequestBody Store store) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        store.id = id;
        return ApiResponse.success("Store updated", platformAdminService.saveStore(store));
    }

    @PostMapping("/stores/from-template")
    public ApiResponse<Store> createStoreFromTemplate(@RequestBody CreateStoreFromTemplateRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Store created", platformAdminService.createStoreFromTemplate(request));
    }

    @GetMapping("/stations")
    public ApiResponse<List<Station>> getStations(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getStations(store_id));
    }

    @PostMapping("/stations")
    public ApiResponse<Station> createStation(@RequestBody Station station) {
        authorizationService.requireForStore(station.store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Station saved", platformAdminService.saveStation(station));
    }

    @PutMapping("/stations/{id}")
    public ApiResponse<Station> updateStation(@PathVariable Long id, @RequestBody Station station) {
        authorizationService.requireForStore(station.store_id, Capability.ADMIN_STORE_CONFIG);
        station.id = id;
        return ApiResponse.success("Station updated", platformAdminService.saveStation(station));
    }

    @GetMapping("/dining-tables")
    public ApiResponse<List<DiningTable>> getDiningTables(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getDiningTables(store_id));
    }

    @PostMapping("/dining-tables")
    public ApiResponse<DiningTable> createDiningTable(@RequestBody DiningTable diningTable) {
        authorizationService.requireForStore(diningTable.store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Dining table saved", platformAdminService.saveDiningTable(diningTable));
    }

    @PutMapping("/dining-tables/{id}")
    public ApiResponse<DiningTable> updateDiningTable(@PathVariable Long id, @RequestBody DiningTable diningTable) {
        authorizationService.requireForStore(diningTable.store_id, Capability.ADMIN_STORE_CONFIG);
        diningTable.id = id;
        return ApiResponse.success("Dining table updated", platformAdminService.saveDiningTable(diningTable));
    }

    @GetMapping("/menu/categories")
    public ApiResponse<List<MenuCategory>> getMenuCategories(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getMenuCategories(store_id));
    }

    @PostMapping("/menu/categories")
    public ApiResponse<MenuCategory> createMenuCategory(@RequestBody MenuCategory menuCategory) {
        authorizationService.requireForStore(menuCategory.store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Menu category saved", platformAdminService.saveMenuCategory(menuCategory));
    }

    @PutMapping("/menu/categories/{id}")
    public ApiResponse<MenuCategory> updateMenuCategory(@PathVariable Long id, @RequestBody MenuCategory menuCategory) {
        authorizationService.requireForStore(menuCategory.store_id, Capability.ADMIN_STORE_CONFIG);
        menuCategory.id = id;
        return ApiResponse.success("Menu category updated", platformAdminService.saveMenuCategory(menuCategory));
    }

    @GetMapping("/menu/items")
    public ApiResponse<List<MenuItem>> getMenuItems(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getMenuItems(store_id));
    }

    @PostMapping("/menu/items")
    public ApiResponse<MenuItem> createMenuItem(@RequestBody MenuItem menuItem) {
        authorizationService.requireForStore(menuItem.store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Menu item saved", platformAdminService.saveMenuItem(menuItem));
    }

    @PutMapping("/menu/items/{id}")
    public ApiResponse<MenuItem> updateMenuItem(@PathVariable Long id, @RequestBody MenuItem menuItem) {
        authorizationService.requireForStore(menuItem.store_id, Capability.ADMIN_STORE_CONFIG);
        menuItem.id = id;
        return ApiResponse.success("Menu item updated", platformAdminService.saveMenuItem(menuItem));
    }

    @GetMapping("/menu/item-options")
    public ApiResponse<List<MenuItemOption>> getMenuItemOptions(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getMenuItemOptions(store_id));
    }

    @PostMapping("/menu/item-options")
    public ApiResponse<MenuItemOption> createMenuItemOption(@RequestBody MenuItemOption menuItemOption) {
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Menu item option saved", platformAdminService.saveMenuItemOption(menuItemOption));
    }

    @PutMapping("/menu/item-options/{id}")
    public ApiResponse<MenuItemOption> updateMenuItemOption(@PathVariable Long id, @RequestBody MenuItemOption menuItemOption) {
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        menuItemOption.id = id;
        return ApiResponse.success("Menu item option updated", platformAdminService.saveMenuItemOption(menuItemOption));
    }

    @GetMapping("/kds-configs")
    public ApiResponse<List<StoreKdsDisplayConfig>> getKdsDisplayConfigs(@RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(platformAdminService.getKdsDisplayConfigs(store_id));
    }

    @PostMapping("/kds-configs")
    public ApiResponse<StoreKdsDisplayConfig> createKdsDisplayConfig(@RequestBody StoreKdsDisplayConfig config) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.requireForStore(config.store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("KDS display config saved", platformAdminService.saveKdsDisplayConfig(config));
    }

    @PutMapping("/kds-configs/{id}")
    public ApiResponse<StoreKdsDisplayConfig> updateKdsDisplayConfig(@PathVariable Long id, @RequestBody StoreKdsDisplayConfig config) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        authorizationService.requireForStore(config.store_id, Capability.ADMIN_STORE_CONFIG);
        config.id = id;
        return ApiResponse.success("KDS display config updated", platformAdminService.saveKdsDisplayConfig(config));
    }

    @GetMapping("/users")
    public ApiResponse<List<User>> getUsers(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_USER_ROLE_MANAGE);
        return ApiResponse.success(platformAdminService.getUsers(store_id));
    }

    @PostMapping("/users")
    public ApiResponse<User> createUser(@RequestBody User user) {
        authorizationService.requireForStore(user.getStore_id(), Capability.ADMIN_USER_ROLE_MANAGE);
        return ApiResponse.success("User saved", platformAdminService.saveUser(user));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        authorizationService.requireForStore(user.getStore_id(), Capability.ADMIN_USER_ROLE_MANAGE);
        user.setId(id);
        return ApiResponse.success("User updated", platformAdminService.saveUser(user));
    }

    @GetMapping("/roles")
    public ApiResponse<List<Role>> getRoles() {
        authorizationService.require(Capability.ADMIN_USER_ROLE_MANAGE);
        return ApiResponse.success(platformAdminService.getRoles());
    }

    @PostMapping("/roles")
    public ApiResponse<Role> createRole(@RequestBody Role role) {
        authorizationService.require(Capability.ADMIN_USER_ROLE_MANAGE);
        return ApiResponse.success("Role saved", platformAdminService.saveRole(role));
    }

    @PutMapping("/roles/{id}")
    public ApiResponse<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        authorizationService.require(Capability.ADMIN_USER_ROLE_MANAGE);
        role.setId(id);
        return ApiResponse.success("Role updated", platformAdminService.saveRole(role));
    }
}
