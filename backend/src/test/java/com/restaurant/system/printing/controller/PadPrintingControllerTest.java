package com.restaurant.system.printing.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.printing.PrintJobStatus;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.service.PadPrintJobService;
import com.restaurant.system.printing.service.StoreDeviceService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PadPrintingControllerTest {

    @Mock
    private StoreDeviceService storeDeviceService;
    @Mock
    private PadPrintJobService padPrintJobService;
    @Mock
    private FeatureFlagService featureFlagService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PadPrintingController controller = new PadPrintingController(
            storeDeviceService,
            padPrintJobService,
            featureFlagService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void pendingJobsInvalidDeviceTokenReturns401() throws Exception {
        when(storeDeviceService.authenticateDevice(3L, "bad-token"))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device token"));

        mockMvc.perform(get("/api/v1/stores/1/printing/jobs/pending")
                .header("X-Device-Id", "3")
                .header("X-Device-Token", "bad-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Invalid device token"));

        verify(featureFlagService).requireEnabled(FeaturePackage.PRINTING);
    }

    @Test
    void pendingJobsMissingDeviceTokenReturns401() throws Exception {
        when(storeDeviceService.authenticateDevice(3L, null))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Device credentials are required"));

        mockMvc.perform(get("/api/v1/stores/1/printing/jobs/pending")
                .header("X-Device-Id", "3"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Device credentials are required"));
    }

    @Test
    void pendingJobsMissingDeviceReturns404() throws Exception {
        when(storeDeviceService.authenticateDevice(3L, "saved-token"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));

        mockMvc.perform(get("/api/v1/stores/1/printing/jobs/pending")
                .header("X-Device-Id", "3")
                .header("X-Device-Token", "saved-token"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Device not found"));
    }

    @Test
    void pendingJobsStoreMismatchReturns403() throws Exception {
        StoreDevice device = activeDevice(3L, 2L);
        when(storeDeviceService.authenticateDevice(3L, "saved-token")).thenReturn(device);
        when(padPrintJobService.listPendingJobs(device, 1L, 25))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Device cannot access this store"));

        mockMvc.perform(get("/api/v1/stores/1/printing/jobs/pending")
                .header("X-Device-Id", "3")
                .header("X-Device-Token", "saved-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Device cannot access this store"));
    }

    @Test
    void pendingJobsDisabledDeviceReturns403() throws Exception {
        when(storeDeviceService.authenticateDevice(3L, "saved-token"))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Device is not active"));

        mockMvc.perform(get("/api/v1/stores/1/printing/jobs/pending")
                .header("X-Device-Id", "3")
                .header("X-Device-Token", "saved-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Device is not active"));
    }

    @Test
    void pendingJobsRevokedDeviceReturns403() throws Exception {
        when(storeDeviceService.authenticateDevice(3L, "saved-token"))
            .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Device is not active"));

        mockMvc.perform(get("/api/v1/stores/1/printing/jobs/pending")
                .header("X-Device-Id", "3")
                .header("X-Device-Token", "saved-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Device is not active"));
    }

    @Test
    void pendingJobsValidDeviceWithoutJobsReturns200EmptyArray() throws Exception {
        StoreDevice device = activeDevice(3L, 1L);
        when(storeDeviceService.authenticateDevice(3L, "saved-token")).thenReturn(device);
        when(padPrintJobService.listPendingJobs(device, 1L, 25)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/stores/1/printing/jobs/pending")
                .header("X-Device-Id", "3")
                .header("X-Device-Token", "saved-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void pendingJobsValidDeviceCanReadPadDirectJob() throws Exception {
        StoreDevice device = activeDevice(3L, 1L);
        PrintJobResponse job = new PrintJobResponse();
        job.id = 9L;
        job.store_id = 1L;
        job.module_code = "GRAB";
        job.execution_mode = "PAD_DIRECT";
        job.status = PrintJobStatus.PENDING;
        job.created_at = LocalDateTime.now();
        when(storeDeviceService.authenticateDevice(3L, "saved-token")).thenReturn(device);
        when(padPrintJobService.listPendingJobs(eq(device), eq(1L), eq(10))).thenReturn(List.of(job));

        mockMvc.perform(get("/api/v1/stores/1/printing/jobs/pending")
                .param("limit", "10")
                .header("X-Device-Id", "3")
                .header("X-Device-Token", "saved-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(9))
            .andExpect(jsonPath("$.data[0].execution_mode").value("PAD_DIRECT"));
    }

    private StoreDevice activeDevice(Long deviceId, Long storeId) {
        StoreDevice device = new StoreDevice();
        device.id = deviceId;
        device.storeId = storeId;
        device.status = "ACTIVE";
        device.isActive = true;
        return device;
    }
}
