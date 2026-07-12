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
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PadPrintingController {

    private static final Logger logger = LoggerFactory.getLogger(PadPrintingController.class);

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
        @RequestHeader(value = "X-Device-Id", required = false) Long deviceId,
        @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
        @RequestParam(required = false, defaultValue = "25") int limit
    ) {
        return withDeviceFailureLogging(storeId, deviceId, deviceToken, "pending", () -> {
            featureFlagService.requireEnabled(FeaturePackage.PRINTING);
            StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
            return ApiResponse.success(padPrintJobService.listPendingJobs(device, storeId, limit));
        });
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/claim")
    public ApiResponse<PrintJobResponse> claimJob(
        @PathVariable Long jobId,
        @RequestHeader(value = "X-Device-Id", required = false) Long deviceId,
        @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
        @RequestBody PadPrintJobClaimRequest request
    ) {
        return withDeviceFailureLogging(null, deviceId, deviceToken, "claim", () -> {
            featureFlagService.requireEnabled(FeaturePackage.PRINTING);
            StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
            return ApiResponse.success("Print job claimed", padPrintJobService.claimJob(device, jobId, request));
        });
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/start-print")
    public ApiResponse<PrintJobResponse> startPrint(
        @PathVariable Long jobId,
        @RequestHeader(value = "X-Device-Id", required = false) Long deviceId,
        @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
        @RequestBody PadPrintJobStartPrintRequest request
    ) {
        return withDeviceFailureLogging(null, deviceId, deviceToken, "start-print", () -> {
            featureFlagService.requireEnabled(FeaturePackage.PRINTING);
            StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
            return ApiResponse.success("Print job marked printing", padPrintJobService.startPrint(device, jobId, request));
        });
    }

    @GetMapping("/api/v1/printing/jobs/{jobId}/payload")
    public ApiResponse<PadPrintJobPayloadResponse> getPayload(
        @PathVariable Long jobId,
        @RequestHeader(value = "X-Device-Id", required = false) Long deviceId,
        @RequestHeader(value = "X-Device-Token", required = false) String deviceToken
    ) {
        return withDeviceFailureLogging(null, deviceId, deviceToken, "payload", () -> {
            featureFlagService.requireEnabled(FeaturePackage.PRINTING);
            StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
            return ApiResponse.success(padPrintJobService.getPayload(device, jobId));
        });
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/complete")
    public ApiResponse<PrintJobResponse> completeJob(
        @PathVariable Long jobId,
        @RequestHeader(value = "X-Device-Id", required = false) Long deviceId,
        @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
        @RequestBody PadPrintJobCompleteRequest request
    ) {
        return withDeviceFailureLogging(null, deviceId, deviceToken, "complete", () -> {
            featureFlagService.requireEnabled(FeaturePackage.PRINTING);
            StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
            return ApiResponse.success("Print job completed", padPrintJobService.completeJob(device, jobId, request));
        });
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/fail")
    public ApiResponse<PrintJobResponse> failJob(
        @PathVariable Long jobId,
        @RequestHeader(value = "X-Device-Id", required = false) Long deviceId,
        @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
        @RequestBody PadPrintJobFailRequest request
    ) {
        return withDeviceFailureLogging(null, deviceId, deviceToken, "fail", () -> {
            featureFlagService.requireEnabled(FeaturePackage.PRINTING);
            StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
            return ApiResponse.success("Print job failed", padPrintJobService.failJob(device, jobId, request));
        });
    }

    @PostMapping("/api/v1/printing/jobs/{jobId}/release")
    public ApiResponse<PrintJobResponse> releaseJob(
        @PathVariable Long jobId,
        @RequestHeader(value = "X-Device-Id", required = false) Long deviceId,
        @RequestHeader(value = "X-Device-Token", required = false) String deviceToken,
        @RequestBody(required = false) PadPrintJobReleaseRequest request
    ) {
        return withDeviceFailureLogging(null, deviceId, deviceToken, "release", () -> {
            featureFlagService.requireEnabled(FeaturePackage.PRINTING);
            StoreDevice device = storeDeviceService.authenticateDevice(deviceId, deviceToken);
            return ApiResponse.success("Print job released", padPrintJobService.releaseJob(device, jobId, request));
        });
    }

    private <T> T withDeviceFailureLogging(
        Long requestStoreId,
        Long deviceId,
        String deviceToken,
        String operation,
        Supplier<T> action
    ) {
        try {
            return action.get();
        } catch (ResponseStatusException exception) {
            logger.warn(
                "PAD_DIRECT device request rejected operation={} requestStoreId={} deviceId={} httpStatus={} failureCategory={} tokenPresent={} tokenLast4={}",
                operation,
                requestStoreId,
                deviceId,
                exception.getStatusCode().value(),
                failureCategory(exception),
                deviceToken != null && !deviceToken.isBlank(),
                tokenLast4(deviceToken)
            );
            throw exception;
        }
    }

    private String failureCategory(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        String reason = exception.getReason() == null ? "" : exception.getReason().trim().toUpperCase();
        if (status == 401 && reason.contains("REQUIRED")) {
            return "DEVICE_CREDENTIALS_MISSING";
        }
        if (status == 401 && reason.contains("TOKEN")) {
            return "TOKEN_INVALID";
        }
        if (status == 404 && reason.contains("DEVICE")) {
            return "DEVICE_NOT_FOUND";
        }
        if (status == 403 && reason.contains("STORE")) {
            return "STORE_MISMATCH";
        }
        if (status == 403 && reason.contains("ACTIVE")) {
            return "DEVICE_INACTIVE";
        }
        if (status == 409) {
            return "JOB_CONFLICT";
        }
        return "HTTP_" + status;
    }

    private String tokenLast4(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        String trimmed = token.trim();
        return trimmed.substring(Math.max(0, trimmed.length() - 4));
    }
}
