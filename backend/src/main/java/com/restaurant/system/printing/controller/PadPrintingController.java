package com.restaurant.system.printing.controller;

import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.printing.dto.PadPrintJobClaimRequest;
import com.restaurant.system.printing.dto.PadPrintJobCompleteRequest;
import com.restaurant.system.printing.dto.PadPrintJobFailRequest;
import com.restaurant.system.printing.dto.PadPrintJobPayloadResponse;
import com.restaurant.system.printing.dto.PadPrintJobReleaseRequest;
import com.restaurant.system.printing.dto.PadPrintJobStartPrintRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.entity.StoreDevice;
import com.restaurant.system.printing.service.PadPrintJobService;
import com.restaurant.system.printing.service.StoreDeviceService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PadPrintingController {

    private final StoreDeviceService storeDeviceService;
    private final PadPrintJobService padPrintJobService;
    private final FeatureFlagService featureFlagService;

    public PadPrintingController(
        StoreDeviceService storeDeviceService,
        PadPrintJobService padPrintJobService,
        FeatureFlagService featureFlagService
    ) {
        this.storeDeviceService = storeDeviceService;
        this.padPrintJobService = padPrintJobService;
        this.featureFlagService = featureFlagService;
    }

    @GetMapping("/api/v1/stores/{storeId}/printing/jobs/pending")
    public ApiResponse<List<PrintJobResponse>> listPendingJobs(
        @PathVariable Long storeId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestParam(required = false, defaultValue = "25") int limit
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        return ApiResponse.success(padPrintJobService.listPendingJobs(device, storeId, limit));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/claim")
    public ApiResponse<PrintJobResponse> claimJob(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody PadPrintJobClaimRequest request
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        return ApiResponse.success("Print job claimed", padPrintJobService.claimJob(device, jobId, request));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/start-print")
    public ApiResponse<PrintJobResponse> startPrint(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody PadPrintJobStartPrintRequest request
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        return ApiResponse.success("Print job marked printing", padPrintJobService.startPrint(device, jobId, request));
    }

    @GetMapping("/api/v1/printing/jobs/{jobId}/payload")
    public ApiResponse<PadPrintJobPayloadResponse> getPayload(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        return ApiResponse.success(padPrintJobService.getPayload(device, jobId));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/complete")
    public ApiResponse<PrintJobResponse> completeJob(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody PadPrintJobCompleteRequest request
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        return ApiResponse.success("Print job completed", padPrintJobService.completeJob(device, jobId, request));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/fail")
    public ApiResponse<PrintJobResponse> failJob(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody PadPrintJobFailRequest request
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        return ApiResponse.success("Print job failed", padPrintJobService.failJob(device, jobId, request));
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/release")
    public ApiResponse<PrintJobResponse> releaseJob(
        @PathVariable Long jobId,
        @RequestHeader("X-Device-Id") Long deviceId,
        @RequestHeader("X-Device-Token") String deviceToken,
        @RequestBody(required = false) PadPrintJobReleaseRequest request
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
        return ApiResponse.success("Print job released", padPrintJobService.releaseJob(device, jobId, request));
    }
}
