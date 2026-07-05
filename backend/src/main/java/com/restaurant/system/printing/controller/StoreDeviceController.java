package com.restaurant.system.printing.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.printing.dto.DeviceHeartbeatRequest;
import com.restaurant.system.printing.dto.DeviceRegisterRequest;
import com.restaurant.system.printing.dto.DeviceRegisterResponse;
import com.restaurant.system.printing.dto.StoreDeviceResponse;
import com.restaurant.system.printing.service.StoreDeviceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreDeviceController {

    private final StoreDeviceService storeDeviceService;
    private final AuthorizationService authorizationService;
    private final FeatureFlagService featureFlagService;

    public StoreDeviceController(
        StoreDeviceService storeDeviceService,
        AuthorizationService authorizationService,
        FeatureFlagService featureFlagService
    ) {
        this.storeDeviceService = storeDeviceService;
        this.authorizationService = authorizationService;
        this.featureFlagService = featureFlagService;
    }

    @PostMapping("/api/v1/devices/register")
    public ApiResponse<DeviceRegisterResponse> registerDevice(@RequestBody DeviceRegisterRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(
            request.store_id,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
        return ApiResponse.success("Device registered", storeDeviceService.registerDevice(request));
    }

    @PostMapping("/api/v1/devices/heartbeat")
    public ApiResponse<StoreDeviceResponse> heartbeat(
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody(required = false) DeviceHeartbeatRequest request
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        return ApiResponse.success(storeDeviceService.heartbeat(deviceId, deviceToken, request));
    }

    @GetMapping("/api/v1/admin/printing/devices")
    public ApiResponse<List<StoreDeviceResponse>> listStoreDevices(@RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(
            store_id,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
        return ApiResponse.success(storeDeviceService.listStoreDevices(store_id));
    }
}
