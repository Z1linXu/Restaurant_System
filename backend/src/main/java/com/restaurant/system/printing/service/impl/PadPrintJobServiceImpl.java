package com.restaurant.system.printing.service.impl;

import com.restaurant.system.printing.PrintJobStatus;
import com.restaurant.system.printing.dto.PadPrintJobClaimRequest;
import com.restaurant.system.printing.dto.PadPrintJobCompleteRequest;
import com.restaurant.system.printing.dto.PadPrintJobFailRequest;
import com.restaurant.system.printing.dto.PadPrintJobPayloadResponse;
import com.restaurant.system.printing.dto.PadPrintJobReleaseRequest;
import com.restaurant.system.printing.dto.PadPrintJobStartPrintRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrintJobAttempt;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.repository.PrintJobAttemptRepository;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.service.PadPrintJobService;
import com.restaurant.system.printing.service.PrintJobService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PadPrintJobServiceImpl implements PadPrintJobService {

    private static final Logger logger = LoggerFactory.getLogger(PadPrintJobServiceImpl.class);
    private static final int DEFAULT_PENDING_LIMIT = 25;
    private static final int DEFAULT_LEASE_SECONDS = 90;
    private static final int MIN_LEASE_SECONDS = 15;
    private static final int MAX_LEASE_SECONDS = 300;
    private static final int DEFAULT_PRINTING_LEASE_SECONDS = 300;
    private static final int MIN_PRINTING_LEASE_SECONDS = 60;
    private static final int MAX_PRINTING_LEASE_SECONDS = 600;

    private final PrintJobRepository printJobRepository;
    private final PrintJobAttemptRepository printJobAttemptRepository;
    private final PrinterConfigRepository printerConfigRepository;
    private final PrintJobService printJobService;

    public PadPrintJobServiceImpl(
        PrintJobRepository printJobRepository,
        PrintJobAttemptRepository printJobAttemptRepository,
        PrinterConfigRepository printerConfigRepository,
        PrintJobService printJobService
    ) {
        this.printJobRepository = printJobRepository;
        this.printJobAttemptRepository = printJobAttemptRepository;
        this.printerConfigRepository = printerConfigRepository;
        this.printJobService = printJobService;
    }

    @Override
    public List<PrintJobResponse> listPendingJobs(StoreDevice device, Long storeId, int limit) {
        ensureDeviceStore(device, storeId);
        int effectiveLimit = limit <= 0 ? DEFAULT_PENDING_LIMIT : Math.min(limit, 100);
        LocalDateTime now = LocalDateTime.now();
        List<PrintJobResponse> jobs = printJobRepository.findPendingPadDirectJobs(storeId, now, PageRequest.of(0, effectiveLimit))
            .stream()
            .map(printJobService::toResponse)
            .toList();
        if (jobs.isEmpty()) {
            logger.debug("PAD_DIRECT Worker Poll Queue device {} store {} returned 0 jobs", device.id, storeId);
        } else {
            long oldestJobAgeMs = jobs.get(0).created_at == null ? -1L : Duration.between(jobs.get(0).created_at, now).toMillis();
            logger.info("PAD_DIRECT Worker Poll Queue device {} store {} returned {} jobs oldestJobAgeMs={}",
                device.id,
                storeId,
                jobs.size(),
                Math.max(-1L, oldestJobAgeMs)
            );
        }
        return jobs;
    }

    @Override
    @Transactional
    public PrintJobResponse claimJob(StoreDevice device, Long jobId, PadPrintJobClaimRequest request) {
        PrintJob existing = requirePadJobForDevice(device, jobId);
        String attemptToken = normalizeAttemptToken(request == null ? null : request.client_attempt_token);
        if (isSameActiveClaim(existing, device, attemptToken)) {
            return printJobService.toResponse(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        int leaseSeconds = clampLeaseSeconds(request == null ? null : request.lease_seconds);
        int updated = printJobRepository.claimPadDirectJob(
            jobId,
            device.storeId,
            device.id,
            attemptToken,
            now,
            now.plusSeconds(leaseSeconds)
        );
        if (updated != 1) {
            logger.info("PAD_DIRECT Job Picked conflict job {} device {} store {}", jobId, device.id, device.storeId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Print job is not available to claim");
        }

        PrintJob claimed = printJobService.requireJob(jobId);
        createAttemptIfMissing(claimed, device, attemptToken, now);
        long queueDelayMs = claimed.created_at == null ? -1L : Duration.between(claimed.created_at, now).toMillis();
        logger.info("PAD_DIRECT Job Picked job {} module {} device {} store {} queueDelayMs={}",
            claimed.id,
            claimed.module_code,
            device.id,
            device.storeId,
            Math.max(-1L, queueDelayMs)
        );
        return printJobService.toResponse(claimed);
    }

    @Override
    @Transactional
    public PrintJobResponse startPrint(StoreDevice device, Long jobId, PadPrintJobStartPrintRequest request) {
        PrintJob job = requirePadJobForDevice(device, jobId);
        String attemptToken = normalizeAttemptToken(request == null ? null : request.client_attempt_token);
        ensureClaimedOrPrintingByDevice(job, device, attemptToken);

        LocalDateTime now = LocalDateTime.now();
        int leaseSeconds = clampPrintingLeaseSeconds(request == null ? null : request.lease_seconds);
        job.status = PrintJobStatus.PRINTING;
        job.claimExpiresAt = now.plusSeconds(leaseSeconds);
        job.last_attempt_at = now;
        job.updated_at = now;
        PrintJob saved = printJobRepository.save(job);
        markAttemptPrinting(saved, device, attemptToken, now);
        logger.info("PAD_DIRECT Job Processing job {} module {} device {} store {}", saved.id, saved.module_code, device.id, device.storeId);
        return printJobService.toResponse(saved);
    }

    @Override
    public PadPrintJobPayloadResponse getPayload(StoreDevice device, Long jobId) {
        PrintJob job = requirePadJobForDevice(device, jobId);
        ensureClaimedOrPrintingByDevice(job, device, job.clientAttemptToken);
        PrinterConfig printer = requireAssignedPayloadPrinter(job);
        logger.info("PAD_DIRECT Job Processing payload job {} module {} device {} printer {}", job.id, job.module_code, device.id, printer.id);
        return PadPrintJobPayloadResponse.from(job, printer);
    }

    @Override
    @Transactional
    public PrintJobResponse completeJob(StoreDevice device, Long jobId, PadPrintJobCompleteRequest request) {
        PrintJob job = requirePadJobForDevice(device, jobId);
        String attemptToken = normalizeAttemptToken(request == null ? null : request.client_attempt_token);
        if (PrintJobStatus.PRINTED.equals(job.status) && sameToken(job.clientAttemptToken, attemptToken)) {
            return printJobService.toResponse(job);
        }
        ensureClaimedOrPrintingByDevice(job, device, attemptToken);

        LocalDateTime now = LocalDateTime.now();
        job.status = PrintJobStatus.PRINTED;
        job.printedByDeviceId = device.id;
        job.printed_at = now;
        job.failed_at = null;
        job.error_code = null;
        job.error_message = null;
        job.last_attempt_at = now;
        job.updated_at = now;
        PrintJob saved = printJobRepository.save(job);
        completeAttempt(saved, attemptToken, PrintJobStatus.PRINTED, null, null, request == null ? null : request.raw_result, now);
        updatePrinterSuccess(saved.printer_id, now);
        logger.info("PAD_DIRECT Job Finished job {} module {} device {} store {}", saved.id, saved.module_code, device.id, device.storeId);
        return printJobService.toResponse(saved);
    }

    @Override
    @Transactional
    public PrintJobResponse failJob(StoreDevice device, Long jobId, PadPrintJobFailRequest request) {
        PrintJob job = requirePadJobForDevice(device, jobId);
        String attemptToken = normalizeAttemptToken(request == null ? null : request.client_attempt_token);
        ensureClaimedOrPrintingByDevice(job, device, attemptToken);

        LocalDateTime now = LocalDateTime.now();
        String errorCode = truncate(request == null ? null : request.error_code, 80);
        String errorMessage = truncate(request == null ? "Pad Direct print failed" : request.error_message, 2000);
        job.status = PrintJobStatus.FAILED;
        job.error_code = errorCode == null ? "PAD_DIRECT_FAILED" : errorCode;
        job.error_message = errorMessage == null ? "Pad Direct print failed" : errorMessage;
        job.failed_at = now;
        job.last_attempt_at = now;
        job.retry_count = (job.retry_count == null ? 0 : job.retry_count) + 1;
        job.updated_at = now;
        PrintJob saved = printJobRepository.save(job);
        completeAttempt(saved, attemptToken, PrintJobStatus.FAILED, saved.error_code, saved.error_message, request == null ? null : request.raw_result, now);
        updatePrinterFailure(saved.printer_id, saved.error_message, now);
        logger.warn("PAD_DIRECT Job Failed job {} module {} device {} store {} code {} message {}",
            saved.id,
            saved.module_code,
            device.id,
            device.storeId,
            saved.error_code,
            saved.error_message
        );
        return printJobService.toResponse(saved);
    }

    @Override
    @Transactional
    public PrintJobResponse releaseJob(StoreDevice device, Long jobId, PadPrintJobReleaseRequest request) {
        PrintJob job = requirePadJobForDevice(device, jobId);
        String attemptToken = normalizeAttemptToken(request == null ? null : request.client_attempt_token);
        ensureClaimedByDevice(job, device, attemptToken);

        LocalDateTime now = LocalDateTime.now();
        String reason = truncate(request == null ? null : request.reason, 1000);
        job.status = PrintJobStatus.PENDING;
        job.claimedByDeviceId = null;
        job.claimedAt = null;
        job.claimExpiresAt = null;
        job.clientAttemptToken = null;
        job.error_code = reason == null ? null : "PAD_DIRECT_RELEASED";
        job.error_message = reason;
        job.updated_at = now;
        PrintJob saved = printJobRepository.save(job);
        completeAttempt(saved, attemptToken, PrintJobStatus.CANCELLED, "PAD_DIRECT_RELEASED", reason, reason, now);
        logger.info("PAD_DIRECT Job Released job {} module {} device {} store {}", saved.id, saved.module_code, device.id, device.storeId);
        return printJobService.toResponse(saved);
    }

    private PrintJob requirePadJobForDevice(StoreDevice device, Long jobId) {
        PrintJob job = printJobService.requireJob(jobId);
        ensureDeviceStore(device, job.store_id);
        if (!"PAD_DIRECT".equals(job.executionMode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Print job is not a Pad Direct job");
        }
        return job;
    }

    private void ensureDeviceStore(StoreDevice device, Long storeId) {
        if (device == null || storeId == null || !storeId.equals(device.storeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Device cannot access this store");
        }
    }

    private boolean isSameActiveClaim(PrintJob job, StoreDevice device, String attemptToken) {
        return PrintJobStatus.CLAIMED.equals(job.status)
            && device.id.equals(job.claimedByDeviceId)
            && sameToken(job.clientAttemptToken, attemptToken);
    }

    private void ensureClaimedByDevice(PrintJob job, StoreDevice device, String attemptToken) {
        if (!PrintJobStatus.CLAIMED.equals(job.status)
            || !device.id.equals(job.claimedByDeviceId)
            || !sameToken(job.clientAttemptToken, attemptToken)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Print job is not claimed by this device");
        }
    }

    private void ensureClaimedOrPrintingByDevice(PrintJob job, StoreDevice device, String attemptToken) {
        boolean ownedActiveState = PrintJobStatus.CLAIMED.equals(job.status) || PrintJobStatus.PRINTING.equals(job.status);
        if (!ownedActiveState
            || !device.id.equals(job.claimedByDeviceId)
            || !sameToken(job.clientAttemptToken, attemptToken)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Print job is not claimed by this device");
        }
    }

    private PrinterConfig requireAssignedPayloadPrinter(PrintJob job) {
        if (job.printer_id == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pad Direct print job is missing assigned printer");
        }
        PrinterConfig printer = printerConfigRepository.findById(job.printer_id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Assigned printer was not found"));
        if (!job.store_id.equals(printer.store_id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assigned printer does not belong to this print job store");
        }
        if (!Boolean.TRUE.equals(printer.enabled)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assigned printer is disabled");
        }
        if (printer.ip_address == null || printer.ip_address.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assigned printer is missing host");
        }
        return printer;
    }

    private void createAttemptIfMissing(PrintJob job, StoreDevice device, String attemptToken, LocalDateTime startedAt) {
        List<PrintJobAttempt> existing = printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, attemptToken);
        if (!existing.isEmpty()) {
            return;
        }
        PrintJobAttempt attempt = new PrintJobAttempt();
        attempt.print_job_id = job.id;
        attempt.printer_id = job.printer_id;
        attempt.device_id = device.id;
        attempt.transportType = "PAD_DIRECT";
        attempt.clientAttemptToken = attemptToken;
        attempt.attempt_number = (int) printJobAttemptRepository.countByPrintJobId(job.id) + 1;
        attempt.status = PrintJobStatus.CLAIMED;
        attempt.started_at = startedAt;
        printJobAttemptRepository.save(attempt);
    }

    private void markAttemptPrinting(PrintJob job, StoreDevice device, String attemptToken, LocalDateTime startedAt) {
        List<PrintJobAttempt> attempts = printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, attemptToken);
        if (attempts.isEmpty()) {
            createAttemptIfMissing(job, device, attemptToken, startedAt);
            attempts = printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, attemptToken);
        }
        if (attempts.isEmpty()) {
            return;
        }
        PrintJobAttempt attempt = attempts.get(0);
        attempt.status = PrintJobStatus.PRINTING;
        if (attempt.started_at == null) {
            attempt.started_at = startedAt;
        }
        attempt.finished_at = null;
        attempt.errorCode = null;
        attempt.error_message = null;
        printJobAttemptRepository.save(attempt);
    }

    private void completeAttempt(
        PrintJob job,
        String attemptToken,
        String status,
        String errorCode,
        String errorMessage,
        String rawResult,
        LocalDateTime finishedAt
    ) {
        List<PrintJobAttempt> attempts = printJobAttemptRepository.findAllByPrintJobIdAndClientAttemptToken(job.id, attemptToken);
        if (attempts.isEmpty()) {
            return;
        }
        PrintJobAttempt attempt = attempts.get(0);
        attempt.status = status;
        attempt.errorCode = truncate(errorCode, 80);
        attempt.error_message = truncate(errorMessage, 2000);
        attempt.rawResult = truncate(rawResult, 4000);
        attempt.finished_at = finishedAt;
        printJobAttemptRepository.save(attempt);
    }

    private void updatePrinterSuccess(Long printerId, LocalDateTime now) {
        if (printerId == null) {
            return;
        }
        PrinterConfig printer = printerConfigRepository.findById(printerId).orElse(null);
        if (printer == null) {
            return;
        }
        printer.last_successful_print_at = now;
        printer.last_error_message = null;
        printer.updated_at = now;
        printerConfigRepository.save(printer);
    }

    private void updatePrinterFailure(Long printerId, String errorMessage, LocalDateTime now) {
        if (printerId == null) {
            return;
        }
        PrinterConfig printer = printerConfigRepository.findById(printerId).orElse(null);
        if (printer == null) {
            return;
        }
        printer.last_failed_print_at = now;
        printer.last_error_message = errorMessage;
        printer.updated_at = now;
        printerConfigRepository.save(printer);
    }

    private String normalizeAttemptToken(String attemptToken) {
        if (attemptToken == null || attemptToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "client_attempt_token is required");
        }
        return attemptToken.trim();
    }

    private boolean sameToken(String left, String right) {
        return left != null && left.equals(right);
    }

    private int clampLeaseSeconds(Integer value) {
        if (value == null) {
            return DEFAULT_LEASE_SECONDS;
        }
        return Math.max(MIN_LEASE_SECONDS, Math.min(MAX_LEASE_SECONDS, value));
    }

    private int clampPrintingLeaseSeconds(Integer value) {
        if (value == null) {
            return DEFAULT_PRINTING_LEASE_SECONDS;
        }
        return Math.max(MIN_PRINTING_LEASE_SECONDS, Math.min(MAX_PRINTING_LEASE_SECONDS, value));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
