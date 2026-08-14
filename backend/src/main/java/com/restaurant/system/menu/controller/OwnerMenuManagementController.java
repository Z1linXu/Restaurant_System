package com.restaurant.system.menu.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.menu.dto.MenuCategoryUpsertRequest;
import com.restaurant.system.menu.dto.MenuItemReorderRequest;
import com.restaurant.system.menu.dto.MenuManagementContextResponse;
import com.restaurant.system.menu.dto.StationUpsertRequest;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.service.OwnerMenuStructureService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.menu.service.OwnerMenuItemOrderingService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final OwnerMenuStructureService ownerMenuStructureService;
    private final AuditLogService auditLogService;
    private final StoreModuleAccessEvaluator moduleAccessEvaluator;

    public OwnerMenuManagementController(
        AuthorizationService authorizationService,
        StoreRepository storeRepository,
        MenuCategoryRepository menuCategoryRepository,
        StationRepository stationRepository,
        OwnerMenuItemOrderingService ownerMenuItemOrderingService,
        OwnerMenuStructureService ownerMenuStructureService,
        AuditLogService auditLogService,
        StoreModuleAccessEvaluator moduleAccessEvaluator
    ) {
        this.authorizationService = authorizationService;
        this.storeRepository = storeRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.stationRepository = stationRepository;
        this.ownerMenuItemOrderingService = ownerMenuItemOrderingService;
        this.ownerMenuStructureService = ownerMenuStructureService;
        this.auditLogService = auditLogService;
        this.moduleAccessEvaluator = moduleAccessEvaluator;
    }

    @GetMapping("/management-context")
    public ApiResponse<MenuManagementContextResponse> getManagementContext(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);
        requireMenuManagement(store_id);

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

    @PostMapping("/categories")
    public ApiResponse<MenuCategory> createCategory(
        @RequestParam Long store_id,
        @RequestBody MenuCategoryUpsertRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = authorizationService.requireForStore(store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);
        requireMenuManagement(store_id);
        MenuCategory response = ownerMenuStructureService.createCategory(store_id, request);
        auditLogService.record(
            store_id,
            user,
            "MENU_CATEGORY_CREATED",
            "MENU_CATEGORY",
            response.id,
            "Created menu category",
            Map.of("category_id", response.id, "category_code", response.code),
            servletRequest
        );
        return ApiResponse.success("Menu category created", response);
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<MenuCategory> updateCategory(
        @PathVariable Long categoryId,
        @RequestParam Long store_id,
        @RequestBody MenuCategoryUpsertRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = authorizationService.requireForStore(store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);
        requireMenuManagement(store_id);
        MenuCategory response = ownerMenuStructureService.updateCategory(store_id, categoryId, request);
        auditLogService.record(
            store_id,
            user,
            "MENU_CATEGORY_UPDATED",
            "MENU_CATEGORY",
            response.id,
            "Updated menu category",
            Map.of("category_id", response.id, "category_code", response.code),
            servletRequest
        );
        return ApiResponse.success("Menu category updated", response);
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<List<MenuCategory>> deleteCategory(
        @PathVariable Long categoryId,
        @RequestParam Long store_id,
        HttpServletRequest servletRequest
    ) {
        var user = authorizationService.requireForStore(store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);
        requireMenuManagement(store_id);
        List<MenuCategory> response = ownerMenuStructureService.deleteCategory(store_id, categoryId);
        auditLogService.record(
            store_id,
            user,
            "MENU_CATEGORY_DELETED",
            "MENU_CATEGORY",
            categoryId,
            "Deleted empty menu category",
            Map.of("category_id", categoryId),
            servletRequest
        );
        return ApiResponse.success("Menu category deleted", response);
    }

    @PostMapping("/stations")
    public ApiResponse<Station> createStation(
        @RequestParam Long store_id,
        @RequestBody StationUpsertRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = authorizationService.requireForStore(store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);
        requireMenuManagement(store_id);
        Station response = ownerMenuStructureService.createStation(store_id, request);
        auditLogService.record(
            store_id,
            user,
            "MENU_STATION_CREATED",
            "STATION",
            response.id,
            "Created station",
            Map.of("station_id", response.id, "station_code", response.code),
            servletRequest
        );
        return ApiResponse.success("Station created", response);
    }

    @PutMapping("/stations/{stationId}")
    public ApiResponse<Station> updateStation(
        @PathVariable Long stationId,
        @RequestParam Long store_id,
        @RequestBody StationUpsertRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = authorizationService.requireForStore(store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);
        requireMenuManagement(store_id);
        Station response = ownerMenuStructureService.updateStation(store_id, stationId, request);
        auditLogService.record(
            store_id,
            user,
            "MENU_STATION_UPDATED",
            "STATION",
            response.id,
            "Updated station",
            Map.of("station_id", response.id, "station_code", response.code),
            servletRequest
        );
        return ApiResponse.success("Station updated", response);
    }

    @DeleteMapping("/stations/{stationId}")
    public ApiResponse<List<Station>> deleteStation(
        @PathVariable Long stationId,
        @RequestParam Long store_id,
        HttpServletRequest servletRequest
    ) {
        var user = authorizationService.requireForStore(store_id, Capability.ADMIN_MENU_MANAGE, Capability.ADMIN_STORE_CONFIG);
        requireMenuManagement(store_id);
        List<Station> response = ownerMenuStructureService.deleteStation(store_id, stationId);
        auditLogService.record(
            store_id,
            user,
            "MENU_STATION_DELETED",
            "STATION",
            stationId,
            "Deleted unused station",
            Map.of("station_id", stationId),
            servletRequest
        );
        return ApiResponse.success("Station deleted", response);
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
        requireMenuManagement(request.store_id);
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

    private void requireMenuManagement(Long storeId) {
        moduleAccessEvaluator.requireCapability(storeId, ModuleKeys.MENU_MANAGEMENT);
    }
}
