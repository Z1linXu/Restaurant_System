package com.restaurant.system.menu.service;

import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationUpdateRequest;
import com.restaurant.system.menu.dto.MenuItemComboEggDefaultRequest;
import com.restaurant.system.menu.entity.MenuItemOption;
import java.util.List;

public interface StoreComboConfigurationService {

    StoreComboConfigurationResponse getConfiguration(Long storeId);

    StoreComboConfigurationResponse updateConfiguration(Long storeId, StoreComboConfigurationUpdateRequest request);

    void updateItemEggDefault(Long itemId, Long authorizedStoreId, MenuItemComboEggDefaultRequest request);

    boolean isCatalogOptionEnabled(Long storeId, MenuItemOption option);

    void requireOptionEnabledForNewSelection(Long storeId, MenuItemOption option);

    void requireSnapshotEnabledForNewSelection(Long storeId, String optionGroup, String optionCode);

    void validateRequiredComponentsForCatalog(Long storeId, List<MenuItemOption> activeOptions);
}
