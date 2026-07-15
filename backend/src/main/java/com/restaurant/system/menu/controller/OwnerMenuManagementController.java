package com.restaurant.system.menu.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.menu.dto.MenuItemReorderRequest;
import com.restaurant.system.menu.dto.MenuManagementContextResponse;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.service.OwnerMenuItemOrderingService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final OwnerMenuItemOrderingService ownerMenuItemOrderingService;
    private final AuditLogService auditLogService;

    public OwnerMenuManagementController(
        AuthorizationService authorizationService,
        StoreRepository storeRepository,
        MenuCategoryRepository menuCategoryRepository,
        StationRepository stationRepository,
        OwnerMenuItemOrderingService ownerMenuItemOrderingService,
        AuditLogService auditLogService
    ) {
        this.authorizationService = authorizationService;
        this.storeRepository = storeRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.stationRepository = stationRepository;
        this.ownerMenuItemOrderingService = ownerMenuItemOrderingService;
        this.auditLogService = auditLogService;
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

    @PutMapping("/categories/{categoryId}/items/reorder")
    public ApiResponse<List<MenuItem>> reorderItems(
        @PathVariable Long categoryId,
        @RequestBody MenuItemReorderRequest request,
        HttpServletRequest servletRequest
    ) {
        if (request == null || request.store_id == null) {
            throw new BusinessException("Store id is required for menu item reorder");
        }
        var user = authorizationService.requireForStore(
            request.store_id,
            Capability.ADMIN_MENU_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
        List<MenuItem> response = ownerMenuItemOrderingService.reorder(
            request.store_id,
            categoryId,
            request.item_ids
        );
        auditLogService.record(
            request.store_id,
            user,
            "MENU_ITEMS_REORDERED",
            "MENU_CATEGORY",
            categoryId,
            "Reordered menu items",
            Map.of("category_id", categoryId, "item_count", response.size()),
            servletRequest
        );
        return ApiResponse.success("Menu item order updated", response);
    }
}
