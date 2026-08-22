package com.restaurant.system.printing.controller;

import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.provisioning.part2.DeviceReadinessProofRequest;
import com.restaurant.system.owner.provisioning.part2.DeviceReadinessProofResponse;
import com.restaurant.system.owner.provisioning.part2.StoreDeviceReadinessProofService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreDeviceReadinessController {

    private final StoreDeviceReadinessProofService readinessProofService;

    public StoreDeviceReadinessController(StoreDeviceReadinessProofService readinessProofService) {
        this.readinessProofService = readinessProofService;
    }

    @PostMapping("/api/v1/devices/readiness-proof")
    public ApiResponse<DeviceReadinessProofResponse> readinessProof(
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody(required = false) DeviceReadinessProofRequest request
    ) {
        return ApiResponse.success(
            "Device readiness proof recorded",
            readinessProofService.record(deviceId, deviceToken, request)
        );
    }
}
