package com.restaurant.system.modules;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.RequestUserContextService;
import com.restaurant.system.common.auth.StoreAccessService;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.dto.StoreModuleConfigurationResponse;
import com.restaurant.system.modules.dto.StoreModuleUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StoreModuleController {

    private final RequestUserContextService requestUserContextService;
    private final StoreAccessService storeAccessService;
    private final AuthorizationService authorizationService;
    private final StoreModuleService storeModuleService;

    public StoreModuleController(
        RequestUserContextService requestUserContextService,
        StoreAccessService storeAccessService,
        AuthorizationService authorizationService,
        StoreModuleService storeModuleService
    ) {
        this.requestUserContextService = requestUserContextService;
        this.storeAccessService = storeAccessService;
        this.authorizationService = authorizationService;
        this.storeModuleService = storeModuleService;
    }

    @GetMapping("/stores/{storeId}/modules")
    public ApiResponse<StoreModuleConfigurationResponse> getStoreModules(@PathVariable Long storeId) {
        AuthenticatedUser user = requestUserContextService.getRequiredUser();
        storeAccessService.requireStoreAccess(user, storeId);
        return ApiResponse.success(storeModuleService.getConfiguration(storeId));
    }

    @PutMapping("/admin/stores/{storeId}/modules")
    public ApiResponse<StoreModuleConfigurationResponse> updateStoreModules(
        @PathVariable Long storeId,
        @RequestBody StoreModuleUpdateRequest request
    ) {
        authorizationService.requireManagerOrOwnerForStore(storeId, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(storeModuleService.updateConfiguration(storeId, request));
    }
}
