package com.restaurant.system.menu.dto;

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

public class MenuManagementContextResponse {
    public List<Organization> organizations = List.of();
    public List<RestaurantTemplate> templates = List.of();
    public List<Store> stores = List.of();
    public List<Role> roles = List.of();
    public List<User> users = List.of();
    public List<Station> stations = List.of();
    public List<DiningTable> dining_tables = List.of();
    public List<MenuCategory> menu_categories = List.of();
    public List<MenuItem> menu_items = List.of();
    public List<MenuItemOption> menu_item_options = List.of();
    public List<StoreKdsDisplayConfig> kds_display_configs = List.of();
}
