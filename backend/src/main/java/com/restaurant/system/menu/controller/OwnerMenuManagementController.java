package com.restaurant.system.menu.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.menu.dto.MenuManagementContextResponse;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.Comparator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/menu")
public class OwnerMenuManagementController {

    private final AuthorizationService authorizationService;
    private final StoreRepository storeRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final StationRepository stationRepository;

    public OwnerMenuManagementController(
        AuthorizationService authorizationService,
        StoreRepository storeRepository,
        MenuCategoryRepository menuCategoryRepository,
        StationRepository stationRepository
    ) {
        this.authorizationService = authorizationService;
        this.storeRepository = storeRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.stationRepository = stationRepository;
    }

    @GetMapping("/management-context")
    public ApiResponse<MenuManagementContextResponse> getManagementContext(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);

        MenuManagementContextResponse response = new MenuManagementContextResponse();
        response.stores = java.util.List.of(
            storeRepository.findById(store_id).orElseThrow(() -> new BusinessException("Store not found"))
        );
        response.menu_categories = menuCategoryRepository.findAll().stream()
            .filter(category -> store_id.equals(category.store_id))
            .sorted(Comparator
                .comparing((MenuCategory category) -> category.sort_order == null ? 0 : category.sort_order)
                .thenComparing(category -> category.id == null ? Long.MAX_VALUE : category.id)
            )
            .toList();
        response.stations = stationRepository.findAll().stream()
            .filter(station -> store_id.equals(station.store_id))
            .sorted(Comparator
                .comparing((Station station) -> station.sort_order == null ? 0 : station.sort_order)
                .thenComparing(station -> station.id == null ? Long.MAX_VALUE : station.id)
            )
            .toList();
        return ApiResponse.success(response);
    }
}
