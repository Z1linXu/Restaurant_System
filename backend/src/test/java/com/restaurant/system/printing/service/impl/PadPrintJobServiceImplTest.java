package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.printing.PrintJobStatus;
import com.restaurant.system.printing.dto.PadPrintJobClaimRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.PrintJobAttemptRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.service.PrintJobService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PadPrintJobServiceImplTest {

    @Mock
    private PrintJobRepository printJobRepository;
    @Mock
    private PrintJobAttemptRepository printJobAttemptRepository;
    @Mock
    private PrinterConfigRepository printerConfigRepository;
    @Mock
    private PrintJobService printJobService;

    private PadPrintJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PadPrintJobServiceImpl(
            printJobRepository,
            printJobAttemptRepository,
            printerConfigRepository,
            printJobService
        );
    }

    @Test
    void listPendingJobsOnlyUsesDeviceStore() {
        StoreDevice device = device();
        PrintJob job = padJob(PrintJobStatus.PENDING);
        PrintJobResponse response = PrintJobResponse.from(job, "Kitchen", "10.0.0.5:9100");
        when(printJobRepository.findPendingPadDirectJobs(eq(1L), any(LocalDateTime.class), any())).thenReturn(List.of(job));
        when(printJobService.toResponse(job)).thenReturn(response);

        List<PrintJobResponse> jobs = service.listPendingJobs(device, 1L, 25);

        assertEquals(1, jobs.size());
        assertEquals(job.id, jobs.get(0).id);
    }

    @Test
    void claimJobUsesAtomicRepositoryUpdate() {
        StoreDevice device = device();
        PrintJob job = padJob(PrintJobStatus.PENDING);
        PadPrintJobClaimRequest request = new PadPrintJobClaimRequest();
        request.client_attempt_token = "attempt-1";

        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printJobRepository.claimPadDirectJob(eq(job.id), eq(device.storeId), eq(device.id), eq("attempt-1"), any(), any()))
            .thenReturn(1);
        PrintJob claimed = padJob(PrintJobStatus.CLAIMED);
        claimed.claimedByDeviceId = device.id;
        claimed.clientAttemptToken = "attempt-1";
        when(printJobService.requireJob(job.id)).thenReturn(job, claimed);
        when(printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, "attempt-1")).thenReturn(List.of());
        when(printJobAttemptRepository.countByPrintJobId(job.id)).thenReturn(0L);
        when(printJobService.toResponse(claimed)).thenReturn(PrintJobResponse.from(claimed, null, null));

        PrintJobResponse response = service.claimJob(device, job.id, request);

        assertEquals(PrintJobStatus.CLAIMED, response.status);
        verify(printJobAttemptRepository).save(any());
    }

    @Test
    void claimJobReturnsConflictWhenAlreadyClaimedByAnotherDevice() {
        StoreDevice device = device();
        PrintJob job = padJob(PrintJobStatus.PENDING);
        PadPrintJobClaimRequest request = new PadPrintJobClaimRequest();
        request.client_attempt_token = "attempt-1";
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printJobRepository.claimPadDirectJob(eq(job.id), eq(device.storeId), eq(device.id), eq("attempt-1"), any(), any()))
            .thenReturn(0);

        assertThrows(ResponseStatusException.class, () -> service.claimJob(device, job.id, request));
    }

    private StoreDevice device() {
        StoreDevice device = new StoreDevice();
        device.id = 10L;
        device.storeId = 1L;
        device.isActive = true;
        device.status = "ACTIVE";
        return device;
    }

    private PrintJob padJob(String status) {
        PrinterConfig printer = new PrinterConfig();
        printer.id = 7L;
        PrintJob job = new PrintJob();
        job.id = 99L;
        job.store_id = 1L;
        job.printer_id = printer.id;
        job.executionMode = "PAD_DIRECT";
        job.status = status;
        return job;
    }
}
