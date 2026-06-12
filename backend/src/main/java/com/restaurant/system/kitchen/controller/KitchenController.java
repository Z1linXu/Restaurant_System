package com.restaurant.system.kitchen.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.kitchen.dto.KitchenTaskResponse;
import com.restaurant.system.kitchen.service.KitchenService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kitchen-tasks")
public class KitchenController {

    private final KitchenService kitchenService;
    private final AuthorizationService authorizationService;
    private final FeatureFlagService featureFlagService;

    public KitchenController(KitchenService kitchenService, AuthorizationService authorizationService, FeatureFlagService featureFlagService) {
        this.kitchenService = kitchenService;
        this.authorizationService = authorizationService;
        this.featureFlagService = featureFlagService;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        featureFlagService.requireEnabled(FeaturePackage.KDS);
        return ApiResponse.success("kitchen module ready");
    }

    @GetMapping
    public ApiResponse<List<KitchenTaskResponse>> getTasks(
        @RequestParam Long store_id,
        @RequestParam(required = false) String station_code
    ) {
        featureFlagService.requireEnabled(FeaturePackage.KDS);
        authorizationService.requireForStore(store_id, Capability.KDS_HOT_VIEW);
        return ApiResponse.success(kitchenService.getTasks(store_id, station_code));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<KitchenTaskResponse> startTask(@PathVariable Long id) {
        featureFlagService.requireEnabled(FeaturePackage.KDS);
        authorizationService.requireKitchenTask(id, Capability.KDS_HOT_START);
        return ApiResponse.success("Kitchen task started", kitchenService.startTask(id));
    }

    @PostMapping("/{id}/ready-for-pickup")
    public ApiResponse<KitchenTaskResponse> markReadyForPickup(@PathVariable Long id) {
        featureFlagService.requireEnabled(FeaturePackage.KDS);
        authorizationService.requireKitchenTask(id, Capability.KDS_HOT_READY_FOR_PICKUP, Capability.KDS_PASS_READY_FOR_PICKUP);
        return ApiResponse.success("Kitchen task is ready for pickup", kitchenService.markReadyForPickup(id));
    }

    @PostMapping("/{id}/served")
    public ApiResponse<KitchenTaskResponse> markServed(@PathVariable Long id) {
        featureFlagService.requireEnabled(FeaturePackage.KDS);
        authorizationService.requireKitchenTask(id, Capability.SHELF_SERVED);
        return ApiResponse.success("Kitchen task served", kitchenService.markServed(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<KitchenTaskResponse> completeTask(@PathVariable Long id) {
        featureFlagService.requireEnabled(FeaturePackage.KDS);
        authorizationService.requireKitchenTask(id, Capability.KDS_HOT_READY_FOR_PICKUP, Capability.KDS_PASS_READY_FOR_PICKUP);
        return ApiResponse.success("Kitchen task is ready for pickup", kitchenService.completeTask(id));
    }
}
