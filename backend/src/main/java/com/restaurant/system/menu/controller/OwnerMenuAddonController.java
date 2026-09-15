package com.restaurant.system.menu.controller;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.menu.addon.StoreAddonService;
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
public class OwnerMenuAddonController {
    private final StoreAddonService addons;
    private final AuthorizationService authorization;
    private final StoreModuleAccessEvaluator modules;
    private final AuditLogService audit;

    public OwnerMenuAddonController(StoreAddonService addons, AuthorizationService authorization,
                                    StoreModuleAccessEvaluator modules, AuditLogService audit) {
        this.addons = addons;
        this.authorization = authorization;
        this.modules = modules;
        this.audit = audit;
    }

    @GetMapping("/addons")
    public ApiResponse<StoreAddonService.AddonList> list(@RequestParam("store_id") Long storeId) {
        requireStore(storeId);
        return ApiResponse.success(addons.getAddons(storeId));
    }

    @PostMapping("/addons")
    public ApiResponse<StoreAddonService.Addon> create(@RequestBody StoreAddonService.WriteRequest request,
                                                     HttpServletRequest servletRequest) {
        if (request == null) throw new BusinessException("ADDON_PAYLOAD_REQUIRED");
        var user = requireStore(request.store_id);
        var result = addons.create(request);
        audit.record(result.store_id(), user, "MENU_ADDON_CREATED", "STORE_ADDON", result.id(),
            "Created Store Add-on", Map.of("code", result.code()), servletRequest);
        return ApiResponse.success(result);
    }

    @PutMapping("/addons/{id}")
    public ApiResponse<StoreAddonService.Addon> update(@PathVariable Long id,
        @RequestBody StoreAddonService.WriteRequest request, HttpServletRequest servletRequest) {
        Long storeId = addons.storeIdForAddon(id);
        var user = requireStore(storeId);
        var result = addons.update(id, request);
        audit.record(storeId, user, "MENU_ADDON_UPDATED", "STORE_ADDON", id,
            "Updated Store Add-on", Map.of("code", result.code()), servletRequest);
        return ApiResponse.success(result);
    }

    @GetMapping("/items/{itemId}/addons")
    public ApiResponse<List<StoreAddonService.ItemAddon>> itemAddons(@PathVariable Long itemId) {
        Long storeId = addons.storeIdForItem(itemId);
        requireStore(storeId);
        return ApiResponse.success(addons.getItemAddons(itemId, storeId));
    }

    @PutMapping("/items/{itemId}/addons/{addonId}")
    public ApiResponse<Void> eligibility(@PathVariable Long itemId, @PathVariable Long addonId,
        @RequestBody StoreAddonService.EligibilityRequest request, HttpServletRequest servletRequest) {
        Long storeId = addons.storeIdForItem(itemId);
        var user = requireStore(storeId);
        if (request == null || request.enabled == null) throw new BusinessException("ADDON_ENABLED_REQUIRED");
        addons.setEligibility(itemId, addonId, request.enabled, storeId);
        audit.record(storeId, user, "MENU_ITEM_ADDON_UPDATED", "MENU_ITEM", itemId,
            "Updated item Add-on eligibility", Map.of("addon_id", addonId, "enabled", request.enabled), servletRequest);
        return ApiResponse.success(null);
    }

    @PostMapping("/addons/reconcile")
    public ApiResponse<StoreAddonService.ReconciliationReport> reconcile(
        @RequestBody StoreAddonService.ReconcileRequest request, HttpServletRequest servletRequest) {
        if (request == null || request.dry_run == null) throw new BusinessException("ADDON_RECONCILE_DRY_RUN_REQUIRED");
        var user = requireStore(request.store_id);
        var result = addons.reconcile(request.store_id, request.dry_run);
        if (!request.dry_run) audit.record(request.store_id, user, "MENU_ADDONS_RECONCILED", "STORE", request.store_id,
            "Reconciled Store Add-ons", Map.of("linked_groups", result.linked_groups(),
                "linked_options", result.linked_options(), "conflict_count", result.conflicts().size()), servletRequest);
        return ApiResponse.success(result);
    }

    private AuthenticatedUser requireStore(Long storeId) {
        if (storeId == null) throw new BusinessException("ADDON_STORE_REQUIRED");
        var user = authorization.requireForStore(storeId, Capability.ADMIN_MENU_MANAGE);
        modules.requireCapability(storeId, ModuleKeys.MENU_MANAGEMENT);
        return user;
    }
}
