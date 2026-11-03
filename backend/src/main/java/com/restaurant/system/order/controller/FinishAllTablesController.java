package com.restaurant.system.order.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.order.finish.FinishAllTablesService;
import com.restaurant.system.order.finish.FinishAllTablesResponse.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/orders/finish-all-tables")
public class FinishAllTablesController {
    private final AuthorizationService authorization;
    private final StoreModuleAccessEvaluator modules;
    private final FinishAllTablesService service;
    public FinishAllTablesController(AuthorizationService authorization,StoreModuleAccessEvaluator modules,FinishAllTablesService service) {
        this.authorization=authorization;this.modules=modules;this.service=service;
    }
    @GetMapping("/preview")
    public ApiResponse<Preview> preview(@PathVariable Long storeId) {
        var actor=authorization.requireForStore(storeId,Capability.ORDER_COMPLETE);
        modules.requireOperationalCapability(storeId,ModuleKeys.ORDERING_POS);
        return ApiResponse.success(service.preview(storeId,actor));
    }
    @PostMapping
    public ApiResponse<Result> execute(@PathVariable Long storeId,@RequestBody Request request) {
        var actor=authorization.requireForStore(storeId,Capability.ORDER_COMPLETE);
        modules.requireOperationalCapability(storeId,ModuleKeys.ORDERING_POS);
        return ApiResponse.success(service.execute(storeId,actor,request.preview_token()));
    }
}
