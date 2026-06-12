package com.restaurant.system.platform.dto;

import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.entity.RestaurantTemplate;
import com.restaurant.system.platform.entity.StoreKdsDisplayConfig;
import com.restaurant.system.station.entity.DiningTable;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.entity.User;
import java.util.List;

public class PlatformAdminOverviewResponse {
    public List<Organization> organizations;
    public List<RestaurantTemplate> templates;
    public List<Store> stores;
    public List<Role> roles;
    public List<User> users;
    public List<Station> stations;
    public List<DiningTable> dining_tables;
    public List<MenuCategory> menu_categories;
    public List<MenuItem> menu_items;
    public List<MenuItemOption> menu_item_options;
    public List<StoreKdsDisplayConfig> kds_display_configs;
}
