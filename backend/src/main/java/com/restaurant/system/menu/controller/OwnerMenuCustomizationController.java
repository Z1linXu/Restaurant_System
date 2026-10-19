package com.restaurant.system.menu.controller;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.menu.dto.ItemCustomizationConfiguration;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuItemCustomizationService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/menu/items/{itemId}/customization")
public class OwnerMenuCustomizationController {
    private final MenuItemRepository items;
    private final MenuItemCustomizationService customizations;
    private final AuthorizationService authorization;
    private final StoreModuleAccessEvaluator modules;
    private final AuditLogService audit;

    public OwnerMenuCustomizationController(MenuItemRepository items, MenuItemCustomizationService customizations,
        AuthorizationService authorization, StoreModuleAccessEvaluator modules, AuditLogService audit) {
        this.items = items; this.customizations = customizations; this.authorization = authorization;
        this.modules = modules; this.audit = audit;
    }

    @GetMapping
    public ApiResponse<ItemCustomizationConfiguration> get(@PathVariable Long itemId) {
        return ApiResponse.success(customizations.configuration(requireItem(itemId)));
    }

    @PutMapping
    public ApiResponse<ItemCustomizationConfiguration> update(@PathVariable Long itemId,
        @RequestBody ItemCustomizationConfiguration configuration, HttpServletRequest request) {
        MenuItem item = requireItem(itemId);
        var actor = authorization.requireForStore(item.store_id, Capability.ADMIN_MENU_MANAGE);
        var saved = customizations.updateConfiguration(itemId, item.store_id, configuration);
        audit.record(item.store_id, actor, "MENU_CUSTOMIZATION_UPDATED", "MENU_ITEM", itemId,
            "Updated menu customization", Map.of("menu_item_id", itemId), request);
        return ApiResponse.success("Menu customization updated", saved);
    }

    private MenuItem requireItem(Long itemId) {
        MenuItem item = items.findById(itemId).orElseThrow(() -> new BusinessException("Menu item not found"));
        authorization.requireForStore(item.store_id, Capability.ADMIN_MENU_MANAGE);
        modules.requireCapability(item.store_id, ModuleKeys.MENU_MANAGEMENT);
        return item;
    }
}
