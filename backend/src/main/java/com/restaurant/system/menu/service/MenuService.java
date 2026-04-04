package com.restaurant.system.menu.service;

import com.restaurant.system.menu.dto.MenuCatalogResponse;

public interface MenuService {

    MenuCatalogResponse getCatalog(Long storeId);
}
