package com.restaurant.system.platform.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.platform.dto.OwnerDashboardResponse;
import com.restaurant.system.platform.service.OwnerDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class OwnerDashboardController {

    private final OwnerDashboardService ownerDashboardService;
    private final AuthorizationService authorizationService;

    public OwnerDashboardController(OwnerDashboardService ownerDashboardService, AuthorizationService authorizationService) {
        this.ownerDashboardService = ownerDashboardService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResponse<OwnerDashboardResponse> getDashboard(
        @RequestParam(required = false) Long organization_id,
        @RequestParam(required = false) Long store_id,
        @RequestParam(defaultValue = "today") String range,
        @RequestParam(defaultValue = "false") boolean compare
    ) {
        if (store_id != null) {
            authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        } else {
            authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        }
        return ApiResponse.success(ownerDashboardService.getDashboard(organization_id, store_id, range, compare));
    }
}
