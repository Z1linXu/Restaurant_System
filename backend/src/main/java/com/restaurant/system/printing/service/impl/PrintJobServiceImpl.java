package com.restaurant.system.printing.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.printing.PrintJobStatus;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrintJobAttempt;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.repository.PrintJobAttemptRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.service.PrintJobService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrintJobServiceImpl implements PrintJobService {

    private static final int DEFAULT_MAX_RETRY_COUNT = 3;

    private final PrintJobRepository printJobRepository;
    private final PrintJobAttemptRepository printJobAttemptRepository;
    private final PrinterConfigRepository printerConfigRepository;

    public PrintJobServiceImpl(
        PrintJobRepository printJobRepository,
        PrintJobAttemptRepository printJobAttemptRepository,
        PrinterConfigRepository printerConfigRepository
    ) {
        this.printJobRepository = printJobRepository;
        this.printJobAttemptRepository = printJobAttemptRepository;
        this.printerConfigRepository = printerConfigRepository;
    }

    @Override
    @Transactional
    public PrintJob createPendingJob(
        Long organizationId,
        Long storeId,
        Long orderId,
        Long printerId,
        String moduleCode,
        String receiptType,
        Long requestedByUserId,
        String payloadSnapshot
    ) {
        return createPendingJob(
            organizationId,
            storeId,
            orderId,
            null,
            printerId,
            moduleCode,
            receiptType,
            requestedByUserId,
            payloadSnapshot
        );
    }

    @Override
    @Transactional
    public PrintJob createPendingJob(
        Long organizationId,
        Long storeId,
        Long orderId,
        Long orderUpdateBatchId,
        Long printerId,
        String moduleCode,
        String receiptType,
        Long requestedByUserId,
        String payloadSnapshot
    ) {
        LocalDateTime now = LocalDateTime.now();
        PrintJob job = new PrintJob();
        job.organization_id = organizationId;
        job.store_id = storeId;
        job.order_id = orderId;
        job.order_update_batch_id = orderUpdateBatchId;
        job.printer_id = printerId;
        job.module_code = moduleCode;
        job.receipt_type = receiptType;
        job.status = PrintJobStatus.PENDING;
        job.payload_snapshot = payloadSnapshot;
        job.retry_count = 0;
        job.max_retry_count = DEFAULT_MAX_RETRY_COUNT;
        job.requested_by_user_id = requestedByUserId;
        job.created_at = now;
        job.updated_at = now;
        return printJobRepository.save(job);
    }

    @Override
    @Transactional
    public PrintJob attachRenderedContent(PrintJob job, Long printerId, String renderedTextSnapshot) {
        PrintJob target = requireJob(job.id);
        target.printer_id = printerId;
        target.rendered_text_snapshot = renderedTextSnapshot;
        target.updated_at = LocalDateTime.now();
        return printJobRepository.save(target);
    }

    @Override
    @Transactional
    public PrintJob markPrinting(PrintJob job, PrinterConfig printer) {
        LocalDateTime now = LocalDateTime.now();
        PrintJob target = requireJob(job.id);
        target.status = PrintJobStatus.PRINTING;
        target.printer_id = printer == null ? target.printer_id : printer.id;
        if (job.requested_by_user_id != null) {
            target.requested_by_user_id = job.requested_by_user_id;
        }
        target.last_attempt_at = now;
        target.updated_at = now;
        printJobRepository.save(target);

        PrintJobAttempt attempt = new PrintJobAttempt();
        attempt.print_job_id = target.id;
        attempt.printer_id = target.printer_id;
        attempt.attempt_number = (int) printJobAttemptRepository.countByPrintJobId(target.id) + 1;
        attempt.status = PrintJobStatus.PRINTING;
        attempt.started_at = now;
        printJobAttemptRepository.save(attempt);
        return target;
    }

    @Override
    @Transactional
    public PrintJob markPrinted(PrintJob job, PrinterConfig printer) {
        return markPrinted(job, printer, null);
    }

    @Override
    @Transactional
    public PrintJob markPrinted(PrintJob job, PrinterConfig printer, String attemptMessage) {
        LocalDateTime now = LocalDateTime.now();
        PrintJob target = requireJob(job.id);
        target.status = PrintJobStatus.PRINTED;
        target.error_message = null;
        target.error_code = null;
        target.printed_at = now;
        target.failed_at = null;
        target.last_attempt_at = now;
        target.updated_at = now;
        completeLatestAttempt(target.id, PrintJobStatus.PRINTED, attemptMessage, now);
        if (printer != null) {
            printer.last_successful_print_at = now;
            printer.last_error_message = null;
            printer.updated_at = now;
            printerConfigRepository.save(printer);
        }
        return printJobRepository.save(target);
    }

    @Override
    @Transactional
    public PrintJob markFailed(PrintJob job, PrinterConfig printer, String errorCode, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        PrintJob target = requireJob(job.id);
        boolean wasPrinting = PrintJobStatus.PRINTING.equals(target.status);
        target.status = PrintJobStatus.FAILED;
        target.error_code = truncate(errorCode, 80);
        target.error_message = truncate(errorMessage, 2000);
        target.failed_at = now;
        target.last_attempt_at = now;
        target.updated_at = now;
        if (wasPrinting) {
            target.retry_count = (target.retry_count == null ? 0 : target.retry_count) + 1;
        }
        completeLatestAttempt(target.id, PrintJobStatus.FAILED, target.error_message, now);
        if (printer != null) {
            printer.last_failed_print_at = now;
            printer.last_error_message = target.error_message;
            printer.updated_at = now;
            printerConfigRepository.save(printer);
        }
        return printJobRepository.save(target);
    }

    @Override
    @Transactional
    public PrintJob markCancelled(PrintJob job, PrinterConfig printer, String errorCode, String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        PrintJob target = requireJob(job.id);
        target.status = PrintJobStatus.CANCELLED;
        target.error_code = truncate(errorCode, 80);
        target.error_message = truncate(errorMessage, 2000);
        target.failed_at = null;
        target.last_attempt_at = now;
        target.updated_at = now;
        if (printer != null) {
            printer.updated_at = now;
            printerConfigRepository.save(printer);
        }
        return printJobRepository.save(target);
    }

    @Override
    public List<PrintJobResponse> searchJobs(
        Long storeId,
        String status,
        Long orderId,
        String moduleCode,
        Long printerId,
        LocalDate startDate,
        LocalDate endDate
    ) {
        LocalDate effectiveStart = startDate == null ? LocalDate.now() : startDate;
        LocalDate effectiveEnd = endDate == null ? effectiveStart : endDate;
        String effectiveStatus = blankToNull(status);
        String effectiveModuleCode = blankToNull(moduleCode);
        return printJobRepository.findAllInCreatedRange(
                storeId,
                effectiveStart.atStartOfDay(),
                effectiveEnd.plusDays(1).atStartOfDay()
            )
            .stream()
            .filter(job -> effectiveStatus == null || effectiveStatus.equals(job.status))
            .filter(job -> orderId == null || orderId.equals(job.order_id))
            .filter(job -> effectiveModuleCode == null || effectiveModuleCode.equals(job.module_code))
            .filter(job -> printerId == null || printerId.equals(job.printer_id))
            .map(this::toResponse)
            .toList();
    }

    @Override
    public PrintJob requireJob(Long jobId) {
        return printJobRepository.findById(jobId).orElseThrow(() -> new BusinessException("Print job not found"));
    }

    @Override
    public PrintJobResponse toResponse(PrintJob job) {
        String printerName = null;
        String printerEndpoint = null;
        if (job.printer_id != null) {
            PrinterConfig printer = printerConfigRepository.findById(job.printer_id).orElse(null);
            if (printer != null) {
                printerName = printer.name;
                printerEndpoint = printer.ip_address + ":" + (printer.port == null ? 9100 : printer.port);
            }
        }
        return PrintJobResponse.from(job, printerName, printerEndpoint);
    }

    @Override
    public List<PrintJobResponse> listOrderJobs(Long storeId, Long orderId) {
        return printJobRepository.findAllByStoreIdAndOrderId(storeId, orderId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public List<PrintJobResponse> recentJobs(Long storeId, int limit) {
        return printJobRepository.findRecentByStoreId(storeId, PageRequest.of(0, Math.max(1, limit)))
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private void completeLatestAttempt(Long printJobId, String status, String errorMessage, LocalDateTime finishedAt) {
        List<PrintJobAttempt> attempts = printJobAttemptRepository.findAllByPrintJobId(printJobId);
        if (attempts.isEmpty()) {
            return;
        }
        PrintJobAttempt latest = attempts.get(attempts.size() - 1);
        latest.status = status;
        latest.error_message = errorMessage;
        latest.finished_at = finishedAt;
        printJobAttemptRepository.save(latest);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
