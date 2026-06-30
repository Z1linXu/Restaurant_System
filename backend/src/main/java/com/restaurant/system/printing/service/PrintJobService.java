package com.restaurant.system.printing.service;

import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrinterConfig;
import java.time.LocalDate;
import java.util.List;

public interface PrintJobService {

    PrintJob createPendingJob(
        Long organizationId,
        Long storeId,
        Long orderId,
        Long printerId,
        String moduleCode,
        String receiptType,
        Long requestedByUserId,
        String payloadSnapshot
    );

    PrintJob createPendingJob(
        Long organizationId,
        Long storeId,
        Long orderId,
        Long orderUpdateBatchId,
        Long printerId,
        String moduleCode,
        String receiptType,
        Long requestedByUserId,
        String payloadSnapshot
    );

    PrintJob attachRenderedContent(PrintJob job, Long printerId, String renderedTextSnapshot);

    PrintJob markPrinting(PrintJob job, PrinterConfig printer);

    PrintJob markPrinted(PrintJob job, PrinterConfig printer);

    PrintJob markPrinted(PrintJob job, PrinterConfig printer, String attemptMessage);

    PrintJob markFailed(PrintJob job, PrinterConfig printer, String errorCode, String errorMessage);

    PrintJob markCancelled(PrintJob job, PrinterConfig printer, String errorCode, String errorMessage);

    List<PrintJobResponse> searchJobs(
        Long storeId,
        String status,
        Long orderId,
        String moduleCode,
        Long printerId,
        LocalDate startDate,
        LocalDate endDate
    );

    PrintJob requireJob(Long jobId);

    PrintJobResponse toResponse(PrintJob job);

    List<PrintJobResponse> listOrderJobs(Long storeId, Long orderId);
}
