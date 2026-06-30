package com.restaurant.system.menu.service;

import com.restaurant.system.menu.dto.MenuItemOptionAdminResponse;
import com.restaurant.system.menu.dto.MenuItemOptionReorderRequest;
import com.restaurant.system.menu.dto.MenuItemOptionUpsertRequest;
import java.util.List;

public interface OwnerMenuOptionService {

    List<MenuItemOptionAdminResponse> getOptions(Long itemId);

    MenuItemOptionAdminResponse createOption(Long itemId, MenuItemOptionUpsertRequest request);

    MenuItemOptionAdminResponse updateOption(Long itemId, Long optionId, MenuItemOptionUpsertRequest request);

    MenuItemOptionAdminResponse deactivateOption(Long itemId, Long optionId);

    List<MenuItemOptionAdminResponse> reorderOptions(Long itemId, MenuItemOptionReorderRequest request);
}
