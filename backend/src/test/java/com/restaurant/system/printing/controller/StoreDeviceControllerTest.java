package com.restaurant.system.printing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureDisabledException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.printing.dto.DeviceRegisterRequest;
import com.restaurant.system.printing.dto.DeviceRegisterResponse;
import com.restaurant.system.printing.dto.StoreDeviceRenameRequest;
import com.restaurant.system.printing.dto.StoreDeviceResponse;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.service.StoreDeviceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreDeviceControllerTest {

    @Mock
    private StoreDeviceService storeDeviceService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private StoreModuleAccessEvaluator moduleAccessEvaluator;
    @Mock
    private FeatureFlagService featureFlagService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private StoreDeviceController controller;

    @BeforeEach
    void setUp() {
        controller = new StoreDeviceController(
            storeDeviceService,
            authorizationService,
            moduleAccessEvaluator,
            featureFlagService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void registerDeviceUsesPrintingManageCapability() throws Exception {
        DeviceRegisterResponse response = new DeviceRegisterResponse();
        response.device_id = 90L;
        response.store_id = 1L;
        response.device_name = "Restaurant Pad";
        response.device_type = "ANDROID_PAD";
        response.device_token = "raw-token-returned-once";
        when(storeDeviceService.registerDevice(any(DeviceRegisterRequest.class))).thenReturn(response);

        DeviceRegisterRequest request = new DeviceRegisterRequest();
        request.store_id = 1L;
        request.device_name = "Restaurant Pad";
        request.device_type = "ANDROID_PAD";
        request.platform = "ANDROID";
        request.app_version = "unknown";

        mockMvc.perform(post("/api/v1/devices/register")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.device_id").value(90))
            .andExpect(jsonPath("$.data.device_token").value("raw-token-returned-once"));

        verify(moduleAccessEvaluator).requireModuleEnabled(1L, ModuleKeys.PRINTING);
        verify(featureFlagService).requireEnabled(FeaturePackage.PRINTING);
        verify(authorizationService).requireForStore(
            1L,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
    }

    @Test
    void listStoreDevicesUsesPrintingManageCapability() throws Exception {
        StoreDeviceResponse response = new StoreDeviceResponse();
        response.id = 90L;
        response.store_id = 1L;
        response.device_name = "Restaurant Pad";
        response.device_type = "ANDROID_PAD";
        when(storeDeviceService.listStoreDevices(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/admin/printing/devices").param("store_id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(90));

        verify(moduleAccessEvaluator).requireModuleEnabled(1L, ModuleKeys.PRINTING);
        verify(featureFlagService).requireEnabled(FeaturePackage.PRINTING);
        verify(authorizationService).requireForStore(
            1L,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
    }

    @Test
    void heartbeatRequiresOnlyStorePrintingModuleForDeviceReadiness() throws Exception {
        StoreDevice device = new StoreDevice();
        device.id = 90L;
        device.storeId = 1L;
        when(storeDeviceService.authenticateDevice(90L, "raw-device-token")).thenReturn(device);
        StoreDeviceResponse response = new StoreDeviceResponse();
        response.id = 90L;
        response.store_id = 1L;
        response.status = "ACTIVE";
        when(storeDeviceService.heartbeat(eq(90L), eq("raw-device-token"), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/devices/heartbeat")
                .header("X-Device-Id", "90")
                .header("X-Device-Token", "raw-device-token")
                .contentType("application/json")
                .content("{\"app_version\":\"synthetic-build\",\"platform\":\"STAGING\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(90))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(moduleAccessEvaluator).requireModuleEnabled(1L, ModuleKeys.PRINTING);
        verify(moduleAccessEvaluator, org.mockito.Mockito.never()).requireCapability(1L, ModuleKeys.PRINTING);
        verify(storeDeviceService).authenticateDevice(90L, "raw-device-token");
    }

    @Test
    void renameDeviceUsesPrintingManageCapability() throws Exception {
        StoreDeviceResponse response = new StoreDeviceResponse();
        response.id = 90L;
        response.store_id = 1L;
        response.device_name = "Expo Pad";
        when(storeDeviceService.renameDevice(1L, 90L, "Expo Pad")).thenReturn(response);

        StoreDeviceRenameRequest request = new StoreDeviceRenameRequest();
        request.device_name = "Expo Pad";

        mockMvc.perform(patch("/api/v1/admin/printing/devices/90/rename")
                .param("store_id", "1")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.device_name").value("Expo Pad"));

        verify(moduleAccessEvaluator).requireModuleEnabled(1L, ModuleKeys.PRINTING);
        verify(authorizationService).requireForStore(
            1L,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
    }

    @Test
    void disableDeviceUsesPrintingManageCapability() throws Exception {
        StoreDeviceResponse response = new StoreDeviceResponse();
        response.id = 90L;
        response.store_id = 1L;
        response.status = "DISABLED";
        response.is_active = false;
        when(storeDeviceService.disableDevice(1L, 90L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/printing/devices/90/disable").param("store_id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DISABLED"))
            .andExpect(jsonPath("$.data.is_active").value(false));

        verify(moduleAccessEvaluator).requireModuleEnabled(1L, ModuleKeys.PRINTING);
        verify(authorizationService).requireForStore(
            1L,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
    }

    @Test
    void revokeDeviceUsesPrintingManageCapability() throws Exception {
        StoreDeviceResponse response = new StoreDeviceResponse();
        response.id = 90L;
        response.store_id = 1L;
        response.status = "REVOKED";
        response.is_active = false;
        when(storeDeviceService.revokeDevice(1L, 90L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/printing/devices/90/revoke").param("store_id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REVOKED"))
            .andExpect(jsonPath("$.data.is_active").value(false));

        verify(moduleAccessEvaluator).requireModuleEnabled(1L, ModuleKeys.PRINTING);
        verify(authorizationService).requireForStore(
            1L,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
    }

    @Test
    void registerDeviceFailsClosedWhenPrintingFeatureIsDisabled() throws Exception {
        doThrow(new FeatureDisabledException(FeaturePackage.PRINTING))
            .when(featureFlagService).requireEnabled(FeaturePackage.PRINTING);
        DeviceRegisterRequest request = new DeviceRegisterRequest();
        request.store_id = 1L;
        request.device_name = "Restaurant Pad";
        request.device_type = "ANDROID_PAD";
        request.platform = "ANDROID";

        assertThrows(FeatureDisabledException.class, () -> controller.registerDevice(request));

        verify(featureFlagService).requireEnabled(FeaturePackage.PRINTING);
        verify(moduleAccessEvaluator, org.mockito.Mockito.never()).requireModuleEnabled(1L, ModuleKeys.PRINTING);
        verify(storeDeviceService, org.mockito.Mockito.never()).registerDevice(any(DeviceRegisterRequest.class));
    }
}
