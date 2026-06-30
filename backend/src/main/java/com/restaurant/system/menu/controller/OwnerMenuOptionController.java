package com.restaurant.system.menu.controller;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.menu.dto.MenuItemOptionAdminResponse;
import com.restaurant.system.menu.dto.MenuItemOptionReorderRequest;
import com.restaurant.system.menu.dto.MenuItemOptionUpsertRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.OwnerMenuOptionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/menu/items/{itemId}/options")
public class OwnerMenuOptionController {

    private final OwnerMenuOptionService ownerMenuOptionService;
    private final MenuItemRepository menuItemRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    public OwnerMenuOptionController(
        OwnerMenuOptionService ownerMenuOptionService,
        MenuItemRepository menuItemRepository,
        AuthorizationService authorizationService,
        AuditLogService auditLogService
    ) {
        this.ownerMenuOptionService = ownerMenuOptionService;
        this.menuItemRepository = menuItemRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<List<MenuItemOptionAdminResponse>> getOptions(@PathVariable Long itemId) {
        requireItemStore(itemId);
        return ApiResponse.success(ownerMenuOptionService.getOptions(itemId));
    }

    @PostMapping
    public ApiResponse<MenuItemOptionAdminResponse> createOption(
        @PathVariable Long itemId,
        @RequestBody MenuItemOptionUpsertRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = requireItemStore(itemId);
        MenuItemOptionAdminResponse response = ownerMenuOptionService.createOption(itemId, request);
        auditLogService.record(user.storeId(), user, "MENU_OPTION_CREATED", "MENU_ITEM_OPTION", response.id, "Created menu option", Map.of("menu_item_id", itemId), servletRequest);
        return ApiResponse.success("Menu item option saved", response);
    }

    @PutMapping("/{optionId}")
    public ApiResponse<MenuItemOptionAdminResponse> updateOption(
        @PathVariable Long itemId,
        @PathVariable Long optionId,
        @RequestBody MenuItemOptionUpsertRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = requireItemStore(itemId);
        MenuItemOptionAdminResponse response = ownerMenuOptionService.updateOption(itemId, optionId, request);
        auditLogService.record(user.storeId(), user, "MENU_OPTION_UPDATED", "MENU_ITEM_OPTION", optionId, "Updated menu option", Map.of("menu_item_id", itemId), servletRequest);
        return ApiResponse.success("Menu item option updated", response);
    }

    @DeleteMapping("/{optionId}")
    public ApiResponse<MenuItemOptionAdminResponse> deleteOption(
        @PathVariable Long itemId,
        @PathVariable Long optionId,
        HttpServletRequest servletRequest
    ) {
        var user = requireItemStore(itemId);
        MenuItemOptionAdminResponse response = ownerMenuOptionService.deactivateOption(itemId, optionId);
        auditLogService.record(user.storeId(), user, "MENU_OPTION_DEACTIVATED", "MENU_ITEM_OPTION", optionId, "Deactivated menu option", Map.of("menu_item_id", itemId), servletRequest);
        return ApiResponse.success("Menu item option deactivated", response);
    }

    @PutMapping("/reorder")
    public ApiResponse<List<MenuItemOptionAdminResponse>> reorderOptions(
        @PathVariable Long itemId,
        @RequestBody MenuItemOptionReorderRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = requireItemStore(itemId);
        List<MenuItemOptionAdminResponse> response = ownerMenuOptionService.reorderOptions(itemId, request);
        auditLogService.record(user.storeId(), user, "MENU_OPTION_REORDERED", "MENU_ITEM", itemId, "Reordered menu options", Map.of("menu_item_id", itemId), servletRequest);
        return ApiResponse.success("Menu item options reordered", response);
    }

    private com.restaurant.system.common.auth.AuthenticatedUser requireItemStore(Long itemId) {
        MenuItem menuItem = menuItemRepository.findById(itemId)
            .orElseThrow(() -> new com.restaurant.system.common.exception.BusinessException("Menu item not found: " + itemId));
        return authorizationService.requireForStore(menuItem.store_id, Capability.ADMIN_MENU_MANAGE);
    }
}
