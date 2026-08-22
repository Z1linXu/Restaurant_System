package com.restaurant.system.printing.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.printing.dto.DeviceHeartbeatRequest;
import com.restaurant.system.printing.dto.DeviceRegisterRequest;
import com.restaurant.system.printing.dto.DeviceRegisterResponse;
import com.restaurant.system.printing.dto.StoreDeviceRenameRequest;
import com.restaurant.system.printing.dto.StoreDeviceResponse;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.service.StoreDeviceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreDeviceController {

    private final StoreDeviceService storeDeviceService;
    private final AuthorizationService authorizationService;
    private final StoreModuleAccessEvaluator moduleAccessEvaluator;

    public StoreDeviceController(
        StoreDeviceService storeDeviceService,
        AuthorizationService authorizationService,
        StoreModuleAccessEvaluator moduleAccessEvaluator
    ) {
        this.storeDeviceService = storeDeviceService;
        this.authorizationService = authorizationService;
        this.moduleAccessEvaluator = moduleAccessEvaluator;
    }

    @PostMapping("/api/v1/devices/register")
    public ApiResponse<DeviceRegisterResponse> registerDevice(@RequestBody DeviceRegisterRequest request) {
        authorizationService.requireForStore(
            request.store_id,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
        requirePrinting(request.store_id);
        return ApiResponse.success("Device registered", storeDeviceService.registerDevice(request));
    }

    @PostMapping("/api/v1/devices/heartbeat")
    public ApiResponse<StoreDeviceResponse> heartbeat(
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody(required = false) DeviceHeartbeatRequest request
    ) {
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        // Heartbeat is a device-readiness signal, not a physical print action.
        // Keep the Store-local printing module boundary while allowing the
        // synthetic Part 2 device proof to run with DISABLED/MOCK transport.
        moduleAccessEvaluator.requireModuleEnabled(device.storeId, ModuleKeys.PRINTING);
        return ApiResponse.success(storeDeviceService.heartbeat(deviceId, deviceToken, request));
    }

    @GetMapping("/api/v1/admin/printing/devices")
    public ApiResponse<List<StoreDeviceResponse>> listStoreDevices(@RequestParam Long store_id) {
        authorizationService.requireForStore(
            store_id,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
        requirePrinting(store_id);
        return ApiResponse.success(storeDeviceService.listStoreDevices(store_id));
    }

    @PatchMapping("/api/v1/admin/printing/devices/{deviceId}/rename")
    public ApiResponse<StoreDeviceResponse> renameDevice(
        @PathVariable Long deviceId,
        @RequestParam Long store_id,
        @RequestBody StoreDeviceRenameRequest request
    ) {
        requirePrintingDeviceManagement(store_id);
        String deviceName = request == null ? null : request.device_name;
        return ApiResponse.success("Device renamed", storeDeviceService.renameDevice(store_id, deviceId, deviceName));
    }

    @PostMapping("/api/v1/admin/printing/devices/{deviceId}/disable")
    public ApiResponse<StoreDeviceResponse> disableDevice(
        @PathVariable Long deviceId,
        @RequestParam Long store_id
    ) {
        requirePrintingDeviceManagement(store_id);
        return ApiResponse.success("Device disabled", storeDeviceService.disableDevice(store_id, deviceId));
    }

    @PostMapping("/api/v1/admin/printing/devices/{deviceId}/revoke")
    public ApiResponse<StoreDeviceResponse> revokeDevice(
        @PathVariable Long deviceId,
        @RequestParam Long store_id
    ) {
        requirePrintingDeviceManagement(store_id);
        return ApiResponse.success("Device revoked", storeDeviceService.revokeDevice(store_id, deviceId));
    }

    private void requirePrintingDeviceManagement(Long storeId) {
        authorizationService.requireForStore(
            storeId,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
        requirePrinting(storeId);
    }

    private void requirePrinting(Long storeId) {
        moduleAccessEvaluator.requireCapability(storeId, ModuleKeys.PRINTING);
    }
}
