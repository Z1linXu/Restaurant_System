package com.restaurant.system.printing.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.printing.dto.PadPrintJobClaimRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.service.PadPrintJobService;
import com.restaurant.system.printing.service.PrintJobService;
import com.restaurant.system.printing.service.StoreDeviceService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PadPrintingControllerTest {

    @Mock
    private StoreDeviceService storeDeviceService;
    @Mock
    private PadPrintJobService padPrintJobService;
    @Mock
    private StoreModuleAccessEvaluator moduleAccessEvaluator;
    @Mock
    private PrintJobService printJobService;

    private PadPrintingController controller;

    @BeforeEach
    void setUp() {
        controller = new PadPrintingController(
            storeDeviceService,
            padPrintJobService,
            moduleAccessEvaluator,
            printJobService
        );
    }

    @Test
    void pendingJobsChecksDeviceStoreBeforeModuleCapability() {
        StoreDevice device = device(1L, 10L);
        when(storeDeviceService.authenticateDevice(10L, "token")).thenReturn(device);

        assertThrows(
            ResponseStatusException.class,
            () -> controller.listPendingJobs(2L, 10L, "token", 25)
        );

        verify(moduleAccessEvaluator, never()).requireCapability(anyLong(), anyString());
        verifyNoInteractions(padPrintJobService);
    }

    @Test
    void jobActionChecksDeviceStoreBeforeModuleCapability() {
        StoreDevice device = device(1L, 10L);
        PrintJob job = new PrintJob();
        job.id = 77L;
        job.store_id = 2L;
        when(storeDeviceService.authenticateDevice(10L, "token")).thenReturn(device);
        when(printJobService.requireJob(77L)).thenReturn(job);

        assertThrows(
            ResponseStatusException.class,
            () -> controller.claimJob(77L, 10L, "token", new PadPrintJobClaimRequest())
        );

        verify(moduleAccessEvaluator, never()).requireCapability(anyLong(), anyString());
        verifyNoInteractions(padPrintJobService);
    }

    @Test
    void jobActionGatesModuleAfterDeviceOwnsJobStore() {
        StoreDevice device = device(1L, 10L);
        PrintJob job = new PrintJob();
        job.id = 77L;
        job.store_id = 1L;
        PrintJobResponse response = new PrintJobResponse();
        response.id = 77L;
        when(storeDeviceService.authenticateDevice(10L, "token")).thenReturn(device);
        when(printJobService.requireJob(77L)).thenReturn(job);
        when(padPrintJobService.claimJob(any(StoreDevice.class), anyLong(), any())).thenReturn(response);
        PadPrintJobClaimRequest request = new PadPrintJobClaimRequest();

        controller.claimJob(77L, 10L, "token", request);

        verify(moduleAccessEvaluator).requireCapability(1L, ModuleKeys.PRINTING);
        verify(padPrintJobService).claimJob(device, 77L, request);
    }

    @Test
    void pendingJobsGatesModuleAfterDeviceOwnsPathStore() {
        StoreDevice device = device(1L, 10L);
        when(storeDeviceService.authenticateDevice(10L, "token")).thenReturn(device);
        when(padPrintJobService.listPendingJobs(device, 1L, 25)).thenReturn(List.of());

        controller.listPendingJobs(1L, 10L, "token", 25);

        verify(moduleAccessEvaluator).requireCapability(1L, ModuleKeys.PRINTING);
        verify(padPrintJobService).listPendingJobs(device, 1L, 25);
    }

    private StoreDevice device(Long storeId, Long deviceId) {
        StoreDevice device = new StoreDevice();
        device.id = deviceId;
        device.storeId = storeId;
        return device;
    }
}
