package com.restaurant.system.printing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.printing.dto.DeviceRegisterRequest;
import com.restaurant.system.printing.dto.DeviceRegisterResponse;
import com.restaurant.system.printing.dto.StoreDeviceResponse;
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
    private FeatureFlagService featureFlagService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        StoreDeviceController controller = new StoreDeviceController(
            storeDeviceService,
            authorizationService,
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

        verify(featureFlagService).requireEnabled(FeaturePackage.PRINTING);
        verify(authorizationService).requireForStore(
            1L,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
    }
}
