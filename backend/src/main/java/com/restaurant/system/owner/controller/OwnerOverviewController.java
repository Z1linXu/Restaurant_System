package com.restaurant.system.owner.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.dto.OwnerOverviewResponse;
import com.restaurant.system.owner.service.OwnerOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner")
public class OwnerOverviewController {

    private final OwnerOverviewService ownerOverviewService;
    private final AuthorizationService authorizationService;

    public OwnerOverviewController(OwnerOverviewService ownerOverviewService, AuthorizationService authorizationService) {
        this.ownerOverviewService = ownerOverviewService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/overview")
    public ApiResponse<OwnerOverviewResponse> getOverview() {
        authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(ownerOverviewService.getOverview());
    }
}
