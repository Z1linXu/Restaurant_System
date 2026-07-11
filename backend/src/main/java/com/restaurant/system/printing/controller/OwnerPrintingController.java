package com.restaurant.system.printing.controller;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.printing.dto.PrintCenterOverviewResponse;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.dto.GrabFontTestRequest;
import com.restaurant.system.printing.dto.GrabFontTestResponse;
import com.restaurant.system.printing.dto.PrinterConnectionTestRequest;
import com.restaurant.system.printing.dto.PrinterConnectionTestResponse;
import com.restaurant.system.printing.dto.PrinterEncodingTestRequest;
import com.restaurant.system.printing.dto.PrinterEncodingTestResponse;
import com.restaurant.system.printing.dto.PrinterAssignmentUpdateRequest;
import com.restaurant.system.printing.dto.PrinterTestRequest;
import com.restaurant.system.printing.dto.PrinterTestResponse;
import com.restaurant.system.printing.dto.StorePrintingStatusRequest;
import com.restaurant.system.printing.dto.ModuleAssignmentTestRequest;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.PrintJobService;
import com.restaurant.system.printing.service.PrinterAssignmentService;
import com.restaurant.system.printing.service.PrinterConfigService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/printing")
public class OwnerPrintingController {

    private final PrinterConfigService printerConfigService;
    private final PrinterAssignmentService printerAssignmentService;
    private final PrintDispatcherService printDispatcherService;
    private final PrintJobService printJobService;
    private final AuthorizationService authorizationService;
    private final FeatureFlagService featureFlagService;
    private final AuditLogService auditLogService;

    @Autowired
    public OwnerPrintingController(
        PrinterConfigService printerConfigService,
        PrinterAssignmentService printerAssignmentService,
        PrintDispatcherService printDispatcherService,
        PrintJobService printJobService,
        AuthorizationService authorizationService,
        FeatureFlagService featureFlagService,
        AuditLogService auditLogService
    ) {
        this.printerConfigService = printerConfigService;
        this.printerAssignmentService = printerAssignmentService;
        this.printDispatcherService = printDispatcherService;
        this.printJobService = printJobService;
        this.authorizationService = authorizationService;
        this.featureFlagService = featureFlagService;
        this.auditLogService = auditLogService;
    }

    public OwnerPrintingController(
        PrinterConfigService printerConfigService,
        PrinterAssignmentService printerAssignmentService,
        PrintDispatcherService printDispatcherService,
        PrintJobService printJobService,
        AuthorizationService authorizationService,
        FeatureFlagService featureFlagService
    ) {
        this(
            printerConfigService,
            printerAssignmentService,
            printDispatcherService,
            printJobService,
            authorizationService,
            featureFlagService,
            null
        );
    }

    @GetMapping
    public ApiResponse<PrintCenterOverviewResponse> getOverview(@RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(store_id);
        return ApiResponse.success(printerConfigService.getOverview(store_id));
    }

    @GetMapping("/printers")
    public ApiResponse<List<PrinterConfig>> getPrinters(@RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(store_id);
        return ApiResponse.success(printerConfigService.getPrinters(store_id));
    }

    @PostMapping("/printers")
    public ApiResponse<PrinterConfig> createPrinter(@RequestBody PrinterConfig printerConfig) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(printerConfig.store_id);
        return ApiResponse.success("Printer saved", printerConfigService.savePrinter(printerConfig));
    }

    @PutMapping("/printers/{id}")
    public ApiResponse<PrinterConfig> updatePrinter(@PathVariable Long id, @RequestBody PrinterConfig printerConfig) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(printerConfig.store_id);
        printerConfig.id = id;
        return ApiResponse.success("Printer updated", printerConfigService.savePrinter(printerConfig));
    }

    @DeleteMapping("/printers/{id}")
    public ApiResponse<Boolean> deletePrinter(@PathVariable Long id, @RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(store_id);
        printerConfigService.deletePrinter(id, store_id);
        return ApiResponse.success("Printer deleted", true);
    }

    @PutMapping("/status")
    public ApiResponse<Boolean> updatePrintingStatus(@RequestBody StorePrintingStatusRequest request, HttpServletRequest servletRequest) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        var user = requirePrintingAccess(request.store_id);
        if (request.printing_mode != null && !request.printing_mode.isBlank()) {
            String mode = printerConfigService.updateStorePrintingMode(request.store_id, request.printing_mode);
            recordAudit(request.store_id, user, "PRINTING_MODE_CHANGED", "STORE", request.store_id, "Printing mode changed", Map.of("printing_mode", mode), servletRequest);
            return ApiResponse.success("Printing mode updated", !"DISABLED".equals(mode));
        }
        Boolean enabled = printerConfigService.updateStorePrintingEnabled(request.store_id, Boolean.TRUE.equals(request.printing_enabled));
        recordAudit(request.store_id, user, "PRINTING_STATUS_CHANGED", "STORE", request.store_id, "Printing status changed", Map.of("printing_enabled", enabled), servletRequest);
        return ApiResponse.success("Printing status updated", enabled);
    }

    @GetMapping("/assignments")
    public ApiResponse<List<PrinterAssignment>> getAssignments(@RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(store_id);
        return ApiResponse.success(printerAssignmentService.getAssignments(store_id));
    }

    @PutMapping("/assignments/{moduleCode}")
    public ApiResponse<PrinterAssignment> updateAssignment(@PathVariable String moduleCode, @RequestBody PrinterAssignmentUpdateRequest request, HttpServletRequest servletRequest) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        var user = requirePrintingAccess(request.store_id);
        request.module_code = moduleCode;
        PrinterAssignment assignment = printerAssignmentService.saveAssignment(request);
        recordAudit(request.store_id, user, "PRINTING_ASSIGNMENT_UPDATED", "PRINTER_ASSIGNMENT", assignment.id, "Printer assignment updated", Map.of("module_code", moduleCode), servletRequest);
        return ApiResponse.success("Printer assignment updated", assignment);
    }

    @PostMapping("/printers/test")
    public ApiResponse<PrinterTestResponse> testPrint(@RequestBody PrinterTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(request.store_id);
        PrinterTestResponse response = printDispatcherService.testPrint(request);
        return ApiResponse.success(response.success ? "Test print sent" : "Test print failed", response);
    }

    @PostMapping("/printers/connection-test")
    public ApiResponse<PrinterConnectionTestResponse> testConnection(@RequestBody PrinterConnectionTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(request.store_id);
        PrinterConnectionTestResponse response = printDispatcherService.testConnection(request);
        return ApiResponse.success(response.success ? "Printer connection successful" : "Printer connection failed", response);
    }

    @GetMapping("/jobs")
    public ApiResponse<List<PrintJobResponse>> getPrintJobs(
        @RequestParam Long store_id,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long orderId,
        @RequestParam(required = false) String moduleCode,
        @RequestParam(required = false) Long printerId,
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(store_id);
        return ApiResponse.success(printJobService.searchJobs(store_id, status, orderId, moduleCode, printerId, startDate, endDate));
    }

    @PostMapping("/jobs/{jobId}/reprint")
    public ApiResponse<PrintJobResponse> reprintJob(@PathVariable Long jobId, HttpServletRequest servletRequest) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        var job = printJobService.requireJob(jobId);
        var user = requirePrintingAccess(job.store_id);
        PrintJobResponse response = printDispatcherService.reprintJob(jobId, user.userId());
        recordAudit(job.store_id, user, "PRINT_JOB_REPRINTED", "PRINT_JOB", jobId, "Print job reprint requested", Map.of("module_code", job.module_code), servletRequest);
        return ApiResponse.success("Reprint requested", response);
    }

    private void recordAudit(
        Long storeId,
        com.restaurant.system.common.auth.AuthenticatedUser user,
        String action,
        String entityType,
        Long entityId,
        String summary,
        Map<String, ?> metadata,
        HttpServletRequest servletRequest
    ) {
        if (auditLogService != null) {
            auditLogService.record(storeId, user, action, entityType, entityId, summary, metadata, servletRequest);
        }
    }

    private com.restaurant.system.common.auth.AuthenticatedUser requirePrintingAccess(Long storeId) {
        return authorizationService.requireForStore(
            storeId,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
    }

    @PostMapping("/printers/font-size-test")
    public ApiResponse<PrinterTestResponse> testCurrentFontSize(@RequestBody PrinterTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.DEVELOPER_TOOLS);
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(request.store_id);
        PrinterTestResponse response = printDispatcherService.testCurrentFontSize(request);
        return ApiResponse.success(response.success ? "Current font size test sent" : "Current font size test failed", response);
    }

    @PostMapping("/modules/test")
    public ApiResponse<PrinterTestResponse> testAssignedModule(@RequestBody ModuleAssignmentTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(request.store_id);
        PrinterTestResponse response = printDispatcherService.testAssignedModulePrint(request);
        return ApiResponse.success(response.success ? "Module test print sent" : "Module test print failed", response);
    }

    @PostMapping("/printers/encoding-test")
    public ApiResponse<PrinterEncodingTestResponse> testEncodings(@RequestBody PrinterEncodingTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.DEVELOPER_TOOLS);
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(request.store_id);
        PrinterEncodingTestResponse response = printDispatcherService.testEncodings(request);
        return ApiResponse.success(response.success ? "Encoding test tickets sent" : "One or more encoding tests failed", response);
    }

    @PostMapping("/grab-font-test")
    public ApiResponse<GrabFontTestResponse> testGrabFontModes(@RequestBody GrabFontTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.DEVELOPER_TOOLS);
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        requirePrintingAccess(request.store_id);
        GrabFontTestResponse response = printDispatcherService.testGrabFontModes(request);
        return ApiResponse.success(response.success ? "GRAB font size test tickets sent" : "One or more GRAB font tests failed", response);
    }
}
