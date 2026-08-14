package com.restaurant.system.kitchen.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.kitchen.dto.KitchenTaskResponse;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.kitchen.service.KitchenService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
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
    private final StoreModuleAccessEvaluator moduleAccessEvaluator;
    private final KitchenTaskRepository kitchenTaskRepository;

    public KitchenController(
        KitchenService kitchenService,
        AuthorizationService authorizationService,
        StoreModuleAccessEvaluator moduleAccessEvaluator,
        KitchenTaskRepository kitchenTaskRepository
    ) {
        this.kitchenService = kitchenService;
        this.authorizationService = authorizationService;
        this.moduleAccessEvaluator = moduleAccessEvaluator;
        this.kitchenTaskRepository = kitchenTaskRepository;
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("kitchen module ready");
    }

    @GetMapping
    public ApiResponse<List<KitchenTaskResponse>> getTasks(
        @RequestParam Long store_id,
        @RequestParam(required = false) String station_code
    ) {
        authorizationService.requireForStore(store_id, Capability.KDS_HOT_VIEW);
        requireKds(store_id);
        return ApiResponse.success(kitchenService.getTasks(store_id, station_code));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<KitchenTaskResponse> startTask(@PathVariable Long id) {
        authorizationService.requireKitchenTask(id, Capability.KDS_HOT_START);
        requireKds(resolveKitchenTaskStoreId(id));
        return ApiResponse.success("Kitchen task started", kitchenService.startTask(id));
    }

    @PostMapping("/{id}/ready-for-pickup")
    public ApiResponse<KitchenTaskResponse> markReadyForPickup(@PathVariable Long id) {
        authorizationService.requireKitchenTask(id, Capability.KDS_HOT_READY_FOR_PICKUP, Capability.KDS_PASS_READY_FOR_PICKUP);
        requireKds(resolveKitchenTaskStoreId(id));
        return ApiResponse.success("Kitchen task is ready for pickup", kitchenService.markReadyForPickup(id));
    }

    @PostMapping("/{id}/served")
    public ApiResponse<KitchenTaskResponse> markServed(@PathVariable Long id) {
        authorizationService.requireKitchenTask(id, Capability.SHELF_SERVED);
        requireKds(resolveKitchenTaskStoreId(id));
        return ApiResponse.success("Kitchen task served", kitchenService.markServed(id));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<KitchenTaskResponse> completeTask(@PathVariable Long id) {
        authorizationService.requireKitchenTask(id, Capability.KDS_HOT_READY_FOR_PICKUP, Capability.KDS_PASS_READY_FOR_PICKUP);
        requireKds(resolveKitchenTaskStoreId(id));
        return ApiResponse.success("Kitchen task is ready for pickup", kitchenService.completeTask(id));
    }

    private void requireKds(Long storeId) {
        moduleAccessEvaluator.requireCapability(storeId, ModuleKeys.KDS);
    }

    private Long resolveKitchenTaskStoreId(Long taskId) {
        KitchenTask task = kitchenTaskRepository.findById(taskId)
            .orElseThrow(() -> new BusinessException("Kitchen task not found"));
        return task.store_id;
    }
}
