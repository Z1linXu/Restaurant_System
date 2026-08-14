package com.restaurant.system.printing.controller;

import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.printing.dto.PadPrintJobClaimRequest;
import com.restaurant.system.printing.dto.PadPrintJobCompleteRequest;
import com.restaurant.system.printing.dto.PadPrintJobFailRequest;
import com.restaurant.system.printing.dto.PadPrintJobPayloadResponse;
import com.restaurant.system.printing.dto.PadPrintJobReleaseRequest;
import com.restaurant.system.printing.dto.PadPrintJobStartPrintRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.service.PadPrintJobService;
import com.restaurant.system.printing.service.PrintJobService;
import com.restaurant.system.printing.service.StoreDeviceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PadPrintingController {

    private final StoreDeviceService storeDeviceService;
    private final PadPrintJobService padPrintJobService;
    private final StoreModuleAccessEvaluator moduleAccessEvaluator;
    private final PrintJobService printJobService;

    public PadPrintingController(
        StoreDeviceService storeDeviceService,
        PadPrintJobService padPrintJobService,
        StoreModuleAccessEvaluator moduleAccessEvaluator,
        PrintJobService printJobService
    ) {
        this.storeDeviceService = storeDeviceService;
        this.padPrintJobService = padPrintJobService;
        this.moduleAccessEvaluator = moduleAccessEvaluator;
        this.printJobService = printJobService;
    }

    @GetMapping("/api/v1/stores/{storeId}/printing/jobs/pending")
    public ApiResponse<List<PrintJobResponse>> listPendingJobs(
        @PathVariable Long storeId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestParam(required = false, defaultValue = "25") int limit
    ) {
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        ensureDeviceStore(device, storeId);
        requirePrinting(storeId);
        return ApiResponse.success(padPrintJobService.listPendingJobs(device, storeId, limit));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/claim")
    public ApiResponse<PrintJobResponse> claimJob(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody PadPrintJobClaimRequest request
    ) {
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        requirePrinting(resolveDeviceOwnedPrintJobStoreId(device, jobId));
        return ApiResponse.success("Print job claimed", padPrintJobService.claimJob(device, jobId, request));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/start-print")
    public ApiResponse<PrintJobResponse> startPrint(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody PadPrintJobStartPrintRequest request
    ) {
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        requirePrinting(resolveDeviceOwnedPrintJobStoreId(device, jobId));
        return ApiResponse.success("Print job marked printing", padPrintJobService.startPrint(device, jobId, request));
    }

    @GetMapping("/api/v1/printing/jobs/{jobId}/payload")
    public ApiResponse<PadPrintJobPayloadResponse> getPayload(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken
    ) {
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        requirePrinting(resolveDeviceOwnedPrintJobStoreId(device, jobId));
        return ApiResponse.success(padPrintJobService.getPayload(device, jobId));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/complete")
    public ApiResponse<PrintJobResponse> completeJob(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody PadPrintJobCompleteRequest request
    ) {
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        requirePrinting(resolveDeviceOwnedPrintJobStoreId(device, jobId));
        return ApiResponse.success("Print job completed", padPrintJobService.completeJob(device, jobId, request));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/fail")
    public ApiResponse<PrintJobResponse> failJob(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody PadPrintJobFailRequest request
    ) {
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        requirePrinting(resolveDeviceOwnedPrintJobStoreId(device, jobId));
        return ApiResponse.success("Print job failed", padPrintJobService.failJob(device, jobId, request));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/release")
    public ApiResponse<PrintJobResponse> releaseJob(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody(required = false) PadPrintJobReleaseRequest request
    ) {
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        requirePrinting(resolveDeviceOwnedPrintJobStoreId(device, jobId));
        return ApiResponse.success("Print job released", padPrintJobService.releaseJob(device, jobId, request));
    }

    private void requirePrinting(Long storeId) {
        moduleAccessEvaluator.requireCapability(storeId, ModuleKeys.PRINTING);
    }

    private Long resolveDeviceOwnedPrintJobStoreId(StoreDevice device, Long jobId) {
        PrintJob job = printJobService.requireJob(jobId);
        ensureDeviceStore(device, job.store_id);
        return job.store_id;
    }

    private void ensureDeviceStore(StoreDevice device, Long storeId) {
        if (device == null || storeId == null || !storeId.equals(device.storeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Device cannot access this store");
        }
    }
}
