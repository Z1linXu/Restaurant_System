package com.restaurant.system.platform.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.platform.service.PlatformAdminService;
import com.restaurant.system.station.entity.DiningTable;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/frontdesk")
public class FrontdeskConfigController {

    private final PlatformAdminService platformAdminService;
    private final AuthorizationService authorizationService;

    public FrontdeskConfigController(PlatformAdminService platformAdminService, AuthorizationService authorizationService) {
        this.platformAdminService = platformAdminService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/dining-tables")
    public ApiResponse<List<DiningTable>> getDiningTables(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.ORDER_VIEW_ACTIVE, Capability.ORDER_CREATE);
        return ApiResponse.success(platformAdminService.getDiningTables(store_id));
    }
}
