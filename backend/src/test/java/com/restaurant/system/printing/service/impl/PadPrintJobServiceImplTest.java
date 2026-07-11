package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurant.system.printing.PrintJobStatus;
import com.restaurant.system.printing.dto.PadPrintJobClaimRequest;
import com.restaurant.system.printing.dto.PadPrintJobCompleteRequest;
import com.restaurant.system.printing.dto.PadPrintJobFailRequest;
import com.restaurant.system.printing.dto.PadPrintJobPayloadResponse;
import com.restaurant.system.printing.dto.PadPrintJobStartPrintRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrintJobAttempt;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.PrintJobAttemptRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.service.PrintJobService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
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

    @Test
    void startPrintRequiresClaimedDevice() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.CLAIMED, 999L, "attempt-1");
        PadPrintJobStartPrintRequest request = startPrintRequest("attempt-1", 300);
        when(printJobService.requireJob(job.id)).thenReturn(job);

        assertThrows(ResponseStatusException.class, () -> service.startPrint(device, job.id, request));
    }

    @Test
    void startPrintRequiresMatchingAttemptToken() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.CLAIMED, device.id, "attempt-1");
        PadPrintJobStartPrintRequest request = startPrintRequest("wrong-token", 300);
        when(printJobService.requireJob(job.id)).thenReturn(job);

        assertThrows(ResponseStatusException.class, () -> service.startPrint(device, job.id, request));
    }

    @Test
    void startPrintSetsPrintingExtendsLeaseAndUpdatesAttempt() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.CLAIMED, device.id, "attempt-1");
        job.claimExpiresAt = LocalDateTime.now().plusSeconds(30);
        PrintJobAttempt attempt = attempt("attempt-1", PrintJobStatus.CLAIMED);
        PadPrintJobStartPrintRequest request = startPrintRequest("attempt-1", 600);
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, "attempt-1")).thenReturn(List.of(attempt));
        when(printJobService.toResponse(any(PrintJob.class))).thenAnswer(invocation -> PrintJobResponse.from(invocation.getArgument(0), null, null));

        PrintJobResponse response = service.startPrint(device, job.id, request);

        assertEquals(PrintJobStatus.PRINTING, response.status);
        assertTrue(job.claimExpiresAt.isAfter(LocalDateTime.now().plusSeconds(500)));
        ArgumentCaptor<PrintJobAttempt> attemptCaptor = ArgumentCaptor.forClass(PrintJobAttempt.class);
        verify(printJobAttemptRepository).save(attemptCaptor.capture());
        assertEquals(PrintJobStatus.PRINTING, attemptCaptor.getValue().status);
    }

    @Test
    void startPrintIsIdempotentForSameDeviceAndTokenWhilePrinting() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.PRINTING, device.id, "attempt-1");
        PadPrintJobStartPrintRequest request = startPrintRequest("attempt-1", 300);
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, "attempt-1")).thenReturn(List.of(attempt("attempt-1", PrintJobStatus.PRINTING)));
        when(printJobService.toResponse(any(PrintJob.class))).thenAnswer(invocation -> PrintJobResponse.from(invocation.getArgument(0), null, null));

        PrintJobResponse response = service.startPrint(device, job.id, request);

        assertEquals(PrintJobStatus.PRINTING, response.status);
    }

    @Test
    void payloadForGrabJobReturnsAssignedPrinterEndpoint() {
        PadPrintJobPayloadResponse response = payloadForModule("GRAB", printer(7L, "GRAB Printer", "192.168.1.101", 9100));

        assertEquals("GRAB", response.module_code);
        assertEquals(7L, response.printer_id);
        assertEquals("GRAB Printer", response.printer_name);
        assertEquals("192.168.1.101", response.printer_host);
        assertEquals(9100, response.printer_port);
        assertEquals("192.168.1.101:9100", response.printer_endpoint);
        assertEquals("GBK", response.text_encoding);
    }

    @Test
    void payloadForFrontdeskReceiptJobReturnsAssignedPrinterEndpoint() {
        PadPrintJobPayloadResponse response = payloadForModule("FRONTDESK_RECEIPT", printer(8L, "Receipt Printer", "192.168.1.102", 9100));

        assertEquals("FRONTDESK_RECEIPT", response.module_code);
        assertEquals(8L, response.printer_id);
        assertEquals("192.168.1.102:9100", response.printer_endpoint);
    }

    @Test
    void payloadForHotKitchenJobReturnsAssignedPrinterEndpoint() {
        PadPrintJobPayloadResponse response = payloadForModule("HOT_KITCHEN", printer(9L, "Hot Kitchen Printer", "192.168.1.103", 9100));

        assertEquals("HOT_KITCHEN", response.module_code);
        assertEquals(9L, response.printer_id);
        assertEquals("192.168.1.103:9100", response.printer_endpoint);
    }

    @Test
    void payloadRejectsMissingPrinter() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.PRINTING, device.id, "attempt-1");
        job.printer_id = null;
        when(printJobService.requireJob(job.id)).thenReturn(job);

        assertThrows(ResponseStatusException.class, () -> service.getPayload(device, job.id));
    }

    @Test
    void payloadRejectsDisabledPrinter() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.PRINTING, device.id, "attempt-1");
        PrinterConfig printer = printer(job.printer_id, "Disabled Printer", "192.168.1.104", 9100);
        printer.enabled = false;
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printerConfigRepository.findById(job.printer_id)).thenReturn(Optional.of(printer));

        assertThrows(ResponseStatusException.class, () -> service.getPayload(device, job.id));
    }

    @Test
    void payloadRejectsCrossStorePrinter() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.PRINTING, device.id, "attempt-1");
        PrinterConfig printer = printer(job.printer_id, "Other Store Printer", "192.168.1.105", 9100);
        printer.store_id = 999L;
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printerConfigRepository.findById(job.printer_id)).thenReturn(Optional.of(printer));

        assertThrows(ResponseStatusException.class, () -> service.getPayload(device, job.id));
    }

    @Test
    void completeWorksFromPrinting() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.PRINTING, device.id, "attempt-1");
        job.printer_id = null;
        PadPrintJobCompleteRequest request = new PadPrintJobCompleteRequest();
        request.client_attempt_token = "attempt-1";
        request.raw_result = "ok";
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, "attempt-1")).thenReturn(List.of(attempt("attempt-1", PrintJobStatus.PRINTING)));
        when(printJobService.toResponse(any(PrintJob.class))).thenAnswer(invocation -> PrintJobResponse.from(invocation.getArgument(0), null, null));

        PrintJobResponse response = service.completeJob(device, job.id, request);

        assertEquals(PrintJobStatus.PRINTED, response.status);
        assertEquals(device.id, job.printedByDeviceId);
    }

    @Test
    void failWorksFromPrinting() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.PRINTING, device.id, "attempt-1");
        PadPrintJobFailRequest request = new PadPrintJobFailRequest();
        request.client_attempt_token = "attempt-1";
        request.error_code = "ANDROID_NATIVE_PRINT_FAILED";
        request.error_message = "printer offline";
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, "attempt-1")).thenReturn(List.of(attempt("attempt-1", PrintJobStatus.PRINTING)));
        when(printerConfigRepository.findById(7L)).thenReturn(Optional.empty());
        when(printJobService.toResponse(any(PrintJob.class))).thenAnswer(invocation -> PrintJobResponse.from(invocation.getArgument(0), null, null));

        PrintJobResponse response = service.failJob(device, job.id, request);

        assertEquals(PrintJobStatus.FAILED, response.status);
        assertEquals("ANDROID_NATIVE_PRINT_FAILED", job.error_code);
    }

    @Test
    void completeStillWorksFromClaimedForLegacyManualClient() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.CLAIMED, device.id, "attempt-1");
        job.printer_id = null;
        PadPrintJobCompleteRequest request = new PadPrintJobCompleteRequest();
        request.client_attempt_token = "attempt-1";
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printJobRepository.save(any(PrintJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, "attempt-1")).thenReturn(List.of(attempt("attempt-1", PrintJobStatus.CLAIMED)));
        when(printJobService.toResponse(any(PrintJob.class))).thenAnswer(invocation -> PrintJobResponse.from(invocation.getArgument(0), null, null));

        PrintJobResponse response = service.completeJob(device, job.id, request);

        assertEquals(PrintJobStatus.PRINTED, response.status);
    }

    @Test
    void expiredPrintingIsNotReclaimable() {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.PRINTING, 99L, "other-attempt");
        job.claimExpiresAt = LocalDateTime.now().minusMinutes(5);
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

    private PadPrintJobPayloadResponse payloadForModule(String moduleCode, PrinterConfig printer) {
        StoreDevice device = device();
        PrintJob job = claimedPadJob(PrintJobStatus.PRINTING, device.id, "attempt-1");
        job.module_code = moduleCode;
        job.receipt_type = moduleCode;
        job.printer_id = printer.id;
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printerConfigRepository.findById(printer.id)).thenReturn(Optional.of(printer));
        return service.getPayload(device, job.id);
    }

    private PrintJob claimedPadJob(String status, Long deviceId, String attemptToken) {
        PrintJob job = padJob(status);
        job.claimedByDeviceId = deviceId;
        job.clientAttemptToken = attemptToken;
        job.claimedAt = LocalDateTime.now().minusSeconds(30);
        job.claimExpiresAt = LocalDateTime.now().plusSeconds(300);
        return job;
    }

    private PadPrintJobStartPrintRequest startPrintRequest(String attemptToken, Integer leaseSeconds) {
        PadPrintJobStartPrintRequest request = new PadPrintJobStartPrintRequest();
        request.client_attempt_token = attemptToken;
        request.lease_seconds = leaseSeconds;
        return request;
    }

    private PrinterConfig printer(Long id, String name, String host, Integer port) {
        PrinterConfig printer = new PrinterConfig();
        printer.id = id;
        printer.store_id = 1L;
        printer.name = name;
        printer.ip_address = host;
        printer.port = port;
        printer.enabled = true;
        printer.paper_width_mm = 80;
        printer.text_encoding = "GBK";
        printer.escpos_code_page = 17;
        printer.timeout_ms = 3000;
        return printer;
    }

    private PrintJobAttempt attempt(String attemptToken, String status) {
        PrintJobAttempt attempt = new PrintJobAttempt();
        attempt.print_job_id = 99L;
        attempt.clientAttemptToken = attemptToken;
        attempt.status = status;
        attempt.started_at = LocalDateTime.now().minusSeconds(20);
        return attempt;
    }
}
