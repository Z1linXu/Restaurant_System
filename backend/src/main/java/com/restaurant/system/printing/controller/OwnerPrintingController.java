package com.restaurant.system.printing.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.printing.dto.PrintCenterOverviewResponse;
import com.restaurant.system.printing.dto.GrabFontTestRequest;
import com.restaurant.system.printing.dto.GrabFontTestResponse;
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
import com.restaurant.system.printing.service.PrinterAssignmentService;
import com.restaurant.system.printing.service.PrinterConfigService;
import java.util.List;
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
    private final AuthorizationService authorizationService;
    private final FeatureFlagService featureFlagService;

    public OwnerPrintingController(
        PrinterConfigService printerConfigService,
        PrinterAssignmentService printerAssignmentService,
        PrintDispatcherService printDispatcherService,
        AuthorizationService authorizationService,
        FeatureFlagService featureFlagService
    ) {
        this.printerConfigService = printerConfigService;
        this.printerAssignmentService = printerAssignmentService;
        this.printDispatcherService = printDispatcherService;
        this.authorizationService = authorizationService;
        this.featureFlagService = featureFlagService;
    }

    @GetMapping
    public ApiResponse<PrintCenterOverviewResponse> getOverview(@RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(printerConfigService.getOverview(store_id));
    }

    @GetMapping("/printers")
    public ApiResponse<List<PrinterConfig>> getPrinters(@RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(printerConfigService.getPrinters(store_id));
    }

    @PostMapping("/printers")
    public ApiResponse<PrinterConfig> createPrinter(@RequestBody PrinterConfig printerConfig) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(printerConfig.store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Printer saved", printerConfigService.savePrinter(printerConfig));
    }

    @PutMapping("/printers/{id}")
    public ApiResponse<PrinterConfig> updatePrinter(@PathVariable Long id, @RequestBody PrinterConfig printerConfig) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(printerConfig.store_id, Capability.ADMIN_STORE_CONFIG);
        printerConfig.id = id;
        return ApiResponse.success("Printer updated", printerConfigService.savePrinter(printerConfig));
    }

    @DeleteMapping("/printers/{id}")
    public ApiResponse<PrinterConfig> disablePrinter(@PathVariable Long id, @RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success("Printer disabled", printerConfigService.disablePrinter(id, store_id));
    }

    @PutMapping("/status")
    public ApiResponse<Boolean> updatePrintingStatus(@RequestBody StorePrintingStatusRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(request.store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(
            "Printing status updated",
            printerConfigService.updateStorePrintingEnabled(request.store_id, Boolean.TRUE.equals(request.printing_enabled))
        );
    }

    @GetMapping("/assignments")
    public ApiResponse<List<PrinterAssignment>> getAssignments(@RequestParam Long store_id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        return ApiResponse.success(printerAssignmentService.getAssignments(store_id));
    }

    @PutMapping("/assignments/{moduleCode}")
    public ApiResponse<PrinterAssignment> updateAssignment(@PathVariable String moduleCode, @RequestBody PrinterAssignmentUpdateRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(request.store_id, Capability.ADMIN_STORE_CONFIG);
        request.module_code = moduleCode;
        return ApiResponse.success("Printer assignment updated", printerAssignmentService.saveAssignment(request));
    }

    @PostMapping("/printers/test")
    public ApiResponse<PrinterTestResponse> testPrint(@RequestBody PrinterTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(request.store_id, Capability.ADMIN_STORE_CONFIG);
        PrinterTestResponse response = printDispatcherService.testPrint(request);
        return ApiResponse.success(response.success ? "Test print sent" : "Test print failed", response);
    }

    @PostMapping("/printers/font-size-test")
    public ApiResponse<PrinterTestResponse> testCurrentFontSize(@RequestBody PrinterTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.DEVELOPER_TOOLS);
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(request.store_id, Capability.ADMIN_STORE_CONFIG);
        PrinterTestResponse response = printDispatcherService.testCurrentFontSize(request);
        return ApiResponse.success(response.success ? "Current font size test sent" : "Current font size test failed", response);
    }

    @PostMapping("/modules/test")
    public ApiResponse<PrinterTestResponse> testAssignedModule(@RequestBody ModuleAssignmentTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(request.store_id, Capability.ADMIN_STORE_CONFIG);
        PrinterTestResponse response = printDispatcherService.testAssignedModulePrint(request);
        return ApiResponse.success(response.success ? "Module test print sent" : "Module test print failed", response);
    }

    @PostMapping("/printers/encoding-test")
    public ApiResponse<PrinterEncodingTestResponse> testEncodings(@RequestBody PrinterEncodingTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.DEVELOPER_TOOLS);
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(request.store_id, Capability.ADMIN_STORE_CONFIG);
        PrinterEncodingTestResponse response = printDispatcherService.testEncodings(request);
        return ApiResponse.success(response.success ? "Encoding test tickets sent" : "One or more encoding tests failed", response);
    }

    @PostMapping("/grab-font-test")
    public ApiResponse<GrabFontTestResponse> testGrabFontModes(@RequestBody GrabFontTestRequest request) {
        featureFlagService.requireEnabled(FeaturePackage.DEVELOPER_TOOLS);
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        authorizationService.requireForStore(request.store_id, Capability.ADMIN_STORE_CONFIG);
        GrabFontTestResponse response = printDispatcherService.testGrabFontModes(request);
        return ApiResponse.success(response.success ? "GRAB font size test tickets sent" : "One or more GRAB font tests failed", response);
    }
}
