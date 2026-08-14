package com.restaurant.system.kitchen.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.kitchen.dto.FrontdeskBeverageOrderResponse;
import com.restaurant.system.kitchen.dto.KdsOrderGroupResponse;
import com.restaurant.system.kitchen.dto.KdsTaskDisplayResponse;
import com.restaurant.system.kitchen.dto.ServingShelfItemResponse;
import com.restaurant.system.kitchen.service.KdsService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kds")
public class KdsController {

    private final KdsService kdsService;
    private final AuthorizationService authorizationService;
    private final StoreModuleAccessEvaluator moduleAccessEvaluator;

    public KdsController(
        KdsService kdsService,
        AuthorizationService authorizationService,
        StoreModuleAccessEvaluator moduleAccessEvaluator
    ) {
        this.kdsService = kdsService;
        this.authorizationService = authorizationService;
        this.moduleAccessEvaluator = moduleAccessEvaluator;
    }

    @GetMapping("/noodle-display")
    public ApiResponse<List<KdsTaskDisplayResponse>> getNoodleDisplay(
        @RequestParam Long store_id,
        @RequestParam(required = false) Integer limit
    ) {
        authorizationService.requireForStore(store_id, Capability.KDS_NOODLE_VIEW);
        requireKds(store_id);
        return ApiResponse.success(kdsService.getNoodleDisplay(store_id, limit));
    }

    @GetMapping("/hot-kitchen")
    public ApiResponse<List<KdsTaskDisplayResponse>> getHotKitchenDisplay(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.KDS_HOT_VIEW);
        requireKds(store_id);
        return ApiResponse.success(kdsService.getHotKitchenDisplay(store_id));
    }

    @GetMapping("/pass")
    public ApiResponse<List<KdsOrderGroupResponse>> getPassView(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.KDS_PASS_VIEW);
        requireKds(store_id);
        return ApiResponse.success(kdsService.getPassView(store_id));
    }

    @GetMapping("/frontdesk-beverages")
    public ApiResponse<List<FrontdeskBeverageOrderResponse>> getFrontdeskBeverageView(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.BEVERAGE_VIEW_BOARD);
        requireKds(store_id);
        return ApiResponse.success(kdsService.getFrontdeskBeverageView(store_id));
    }

    @GetMapping("/serving-shelf")
    public ApiResponse<List<ServingShelfItemResponse>> getServingShelfView(@RequestParam Long store_id) {
        authorizationService.requireForStore(store_id, Capability.SHELF_VIEW);
        requireKds(store_id);
        return ApiResponse.success(kdsService.getServingShelfView(store_id));
    }

    @GetMapping("/history")
    public ApiResponse<List<KdsOrderGroupResponse>> getHistoryView(
        @RequestParam Long store_id,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String station_code
    ) {
        authorizationService.requireForStore(store_id, Capability.KDS_HOT_VIEW, Capability.KDS_PASS_VIEW);
        requireKds(store_id);
        return ApiResponse.success(kdsService.getHistoryView(store_id, limit, station_code));
    }

    private void requireKds(Long storeId) {
        moduleAccessEvaluator.requireCapability(storeId, ModuleKeys.KDS);
    }
}
