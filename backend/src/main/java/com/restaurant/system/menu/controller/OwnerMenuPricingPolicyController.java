package com.restaurant.system.menu.controller;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.menu.dto.MenuItemComboPolicyRequest;
import com.restaurant.system.menu.dto.MenuItemOptionAdminResponse;
import com.restaurant.system.menu.dto.MenuItemSizeConfigurationRequest;
import com.restaurant.system.menu.dto.StorePricingPolicyPreviewRequest;
import com.restaurant.system.menu.dto.StorePricingPolicyPreviewResponse;
import com.restaurant.system.menu.dto.StorePricingPolicyResponse;
import com.restaurant.system.menu.dto.StorePricingPolicyUpdateRequest;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.dto.StoreComboConfigurationUpdateRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.menu.service.StorePricingPolicyService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
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
public class OwnerMenuPricingPolicyController {

    private final StorePricingPolicyService storePricingPolicyService;
    private final StoreComboConfigurationService storeComboConfigurationService;
    private final MenuItemRepository menuItemRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final StoreModuleAccessEvaluator moduleAccessEvaluator;

    public OwnerMenuPricingPolicyController(
        StorePricingPolicyService storePricingPolicyService,
        StoreComboConfigurationService storeComboConfigurationService,
        MenuItemRepository menuItemRepository,
        AuthorizationService authorizationService,
        AuditLogService auditLogService,
        StoreModuleAccessEvaluator moduleAccessEvaluator
    ) {
        this.storePricingPolicyService = storePricingPolicyService;
        this.storeComboConfigurationService = storeComboConfigurationService;
        this.menuItemRepository = menuItemRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.moduleAccessEvaluator = moduleAccessEvaluator;
    }

    @GetMapping("/pricing-policy")
    public ApiResponse<StorePricingPolicyResponse> getPolicy(@RequestParam("store_id") Long storeId) {
        authorizationService.requireForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        requireMenuManagement(storeId);
        return ApiResponse.success(storePricingPolicyService.getPolicyResponse(storeId));
    }

    @GetMapping("/combo-configuration")
    public ApiResponse<StoreComboConfigurationResponse> getComboConfiguration(@RequestParam("store_id") Long storeId) {
        authorizationService.requireForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        requireMenuManagement(storeId);
        return ApiResponse.success(storeComboConfigurationService.getConfiguration(storeId));
    }

    @PostMapping("/pricing-policy/preview")
    public ApiResponse<StorePricingPolicyPreviewResponse> preview(
        @RequestBody StorePricingPolicyPreviewRequest request
    ) {
        Long storeId = request == null ? null : request.store_id;
        authorizationService.requireForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        requireMenuManagement(storeId);
        return ApiResponse.success(storePricingPolicyService.preview(storeId, request));
    }

    @PutMapping("/pricing-policy")
    public ApiResponse<StorePricingPolicyResponse> updatePolicy(
        @RequestBody StorePricingPolicyUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        Long storeId = request == null ? null : request.store_id;
        var user = authorizationService.requireForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        requireMenuManagement(storeId);
        StorePricingPolicyResponse response = storePricingPolicyService.updatePolicy(storeId, request);
        auditLogService.record(user.storeId(), user, "STORE_PRICING_POLICY_UPDATED", "STORE", storeId, "Updated Store pricing policy", Map.of("store_id", storeId), servletRequest);
        return ApiResponse.success("Pricing policy updated", response);
    }

    @PutMapping("/combo-configuration")
    public ApiResponse<StoreComboConfigurationResponse> updateComboConfiguration(
        @RequestBody StoreComboConfigurationUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        Long storeId = request == null ? null : request.store_id;
        var user = authorizationService.requireForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        requireMenuManagement(storeId);
        StoreComboConfigurationResponse response = storeComboConfigurationService.updateConfiguration(storeId, request);
        auditLogService.record(
            user.storeId(),
            user,
            "COMBO_CONFIGURATION_UPDATED",
            "STORE",
            storeId,
            "Updated Store combo configuration",
            Map.of("store_id", storeId),
            servletRequest
        );
        return ApiResponse.success("Combo configuration updated", response);
    }

    @PutMapping("/items/{itemId}/size-configuration")
    public ApiResponse<List<MenuItemOptionAdminResponse>> updateSizeConfiguration(
        @PathVariable Long itemId,
        @RequestBody MenuItemSizeConfigurationRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = requireItemStore(itemId);
        List<MenuItemOptionAdminResponse> response = storePricingPolicyService.updateSizeConfiguration(itemId, request);
        auditLogService.record(user.storeId(), user, "MENU_ITEM_SIZE_CONFIGURATION_UPDATED", "MENU_ITEM", itemId, "Updated canonical Size configuration", Map.of("menu_item_id", itemId), servletRequest);
        return ApiResponse.success("Size configuration updated", response);
    }

    @PutMapping("/items/{itemId}/combo-policy")
    public ApiResponse<List<MenuItemOptionAdminResponse>> updateComboPolicy(
        @PathVariable Long itemId,
        @RequestBody MenuItemComboPolicyRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = requireItemStore(itemId);
        List<MenuItemOptionAdminResponse> response = storePricingPolicyService.updateComboPolicy(itemId, request);
        auditLogService.record(user.storeId(), user, "MENU_ITEM_COMBO_POLICY_UPDATED", "MENU_ITEM", itemId, "Updated item combo policy", Map.of("menu_item_id", itemId), servletRequest);
        return ApiResponse.success("Combo policy updated", response);
    }

    private com.restaurant.system.common.auth.AuthenticatedUser requireItemStore(Long itemId) {
        MenuItem menuItem = menuItemRepository.findById(itemId)
            .orElseThrow(() -> new com.restaurant.system.common.exception.BusinessException("Menu item not found: " + itemId));
        var user = authorizationService.requireForStore(menuItem.store_id, Capability.ADMIN_MENU_MANAGE);
        requireMenuManagement(menuItem.store_id);
        return user;
    }

    private void requireMenuManagement(Long storeId) {
        moduleAccessEvaluator.requireCapability(storeId, ModuleKeys.MENU_MANAGEMENT);
    }
}
