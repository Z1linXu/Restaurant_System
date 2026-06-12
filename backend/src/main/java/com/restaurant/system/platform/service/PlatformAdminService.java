package com.restaurant.system.platform.service;

import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.platform.dto.CreateStoreFromTemplateRequest;
import com.restaurant.system.platform.dto.PlatformAdminOverviewResponse;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.entity.RestaurantTemplate;
import com.restaurant.system.platform.entity.StoreKdsDisplayConfig;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import java.util.List;

public interface PlatformAdminService {
    PlatformAdminOverviewResponse getOverview(Long storeId);

    List<Organization> getOrganizations();
    Organization saveOrganization(Organization organization);

    List<RestaurantTemplate> getTemplates();
    RestaurantTemplate saveTemplate(RestaurantTemplate template);

    List<Store> getStores();
    Store saveStore(Store store);
    Store createStoreFromTemplate(CreateStoreFromTemplateRequest request);

    List<Station> getStations(Long storeId);
    Station saveStation(Station station);

    List<DiningTable> getDiningTables(Long storeId);
    DiningTable saveDiningTable(DiningTable diningTable);

    List<MenuCategory> getMenuCategories(Long storeId);
    MenuCategory saveMenuCategory(MenuCategory menuCategory);

    List<MenuItem> getMenuItems(Long storeId);
    MenuItem saveMenuItem(MenuItem menuItem);

    List<MenuItemOption> getMenuItemOptions(Long storeId);
    MenuItemOption saveMenuItemOption(MenuItemOption menuItemOption);

    List<StoreKdsDisplayConfig> getKdsDisplayConfigs(Long storeId);
    StoreKdsDisplayConfig saveKdsDisplayConfig(StoreKdsDisplayConfig config);

    List<User> getUsers(Long storeId);
    User saveUser(User user);

    List<Role> getRoles();
    Role saveRole(Role role);
}
