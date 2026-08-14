package com.restaurant.system.menu.service;

import com.restaurant.system.menu.dto.MenuCategoryUpsertRequest;
import com.restaurant.system.menu.dto.StationUpsertRequest;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.station.entity.Station;
import java.util.List;

public interface OwnerMenuStructureService {

    MenuCategory createCategory(Long storeId, MenuCategoryUpsertRequest request);

    MenuCategory updateCategory(Long storeId, Long categoryId, MenuCategoryUpsertRequest request);

    List<MenuCategory> deleteCategory(Long storeId, Long categoryId);

    Station createStation(Long storeId, StationUpsertRequest request);

    Station updateStation(Long storeId, Long stationId, StationUpsertRequest request);

    List<Station> deleteStation(Long storeId, Long stationId);
}
