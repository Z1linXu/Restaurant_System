package com.restaurant.system.printing.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.feature.FeatureDisabledException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.printing.dto.PrintCenterOverviewResponse;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.dto.PrinterAssignmentUpdateRequest;
import com.restaurant.system.printing.dto.PrinterConnectionTestResponse;
import com.restaurant.system.printing.dto.PrinterTestResponse;
import com.restaurant.system.printing.dto.StorePrintingStatusRequest;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.rules.PrintingDisplayRuleService;
import com.restaurant.system.printing.rules.dto.PrintingDisplayRuleSettingsResponse;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.PrintJobService;
import com.restaurant.system.printing.service.PrinterAssignmentService;
import com.restaurant.system.printing.service.PrinterConfigService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OwnerPrintingControllerDisabledStateTest {

    @Mock
    private PrinterConfigService printerConfigService;
    @Mock
    private PrinterAssignmentService printerAssignmentService;
    @Mock
    private PrintDispatcherService printDispatcherService;
    @Mock
    private PrintJobService printJobService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private StoreModuleAccessEvaluator moduleAccessEvaluator;
    @Mock
    private PrintingDisplayRuleService printingDisplayRuleService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private OwnerPrintingController controller;

    @BeforeEach
    void setUp() {
        controller = new OwnerPrintingController(
            printerConfigService,
            printerAssignmentService,
            printDispatcherService,
            printJobService,
            authorizationService,
            featureFlagService,
            moduleAccessEvaluator,
            printingDisplayRuleService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        lenient().when(authorizationService.requireForStore(eq(1L), any(Capability[].class))).thenReturn(
            new AuthenticatedUser(2L, 1L, 1L, "owner", "Owner", "ADMIN")
        );
    }

    @Test
    void displayRuleConfigurationDoesNotRequireOperationalHardwareReadiness() throws Exception {
        PrintingDisplayRuleSettingsResponse settings = new PrintingDisplayRuleSettingsResponse();
        settings.store_id = 1L;
        when(printingDisplayRuleService.getSettings(1L)).thenReturn(settings);
        mockMvc.perform(get("/api/v1/admin/printing/display-rules").param("store_id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.store_id").value(1));

        verify(moduleAccessEvaluator).requireModuleEnabled(1L, "PRINTING");
        verify(featureFlagService).requireEnabled(FeaturePackage.PRINTING);
        verify(moduleAccessEvaluator, never()).requireCapability(1L, "PRINTING");
    }

    @Test
    void displayRuleConfigurationStillFailsClosedWhenPrintingModuleIsDisabled() {
        doThrow(new BusinessException("MODULE_DISABLED"))
            .when(moduleAccessEvaluator).requireModuleEnabled(1L, "PRINTING");

        assertThrows(BusinessException.class, () -> controller.getDisplayRules(1L));

        verify(printingDisplayRuleService, never()).getSettings(1L);
        verify(featureFlagService, never()).requireEnabled(FeaturePackage.PRINTING);
    }

    @Test
    void displayRuleConfigurationStillFailsClosedWhenPrintingFeatureIsDisabled() {
        doThrow(new FeatureDisabledException(FeaturePackage.PRINTING))
            .when(featureFlagService).requireEnabled(FeaturePackage.PRINTING);

        assertThrows(FeatureDisabledException.class, () -> controller.getDisplayRules(1L));

        verify(printingDisplayRuleService, never()).getSettings(1L);
    }

    @Test
    void displayRuleConfigurationStillRequiresStoreAuthorization() {
        doThrow(new ForbiddenException("Access denied for store 1"))
            .when(authorizationService).requireForStore(eq(1L), any(Capability[].class));

        assertThrows(ForbiddenException.class, () -> controller.getDisplayRules(1L));

        verify(moduleAccessEvaluator, never()).requireModuleEnabled(1L, "PRINTING");
        verify(printingDisplayRuleService, never()).getSettings(1L);
    }

    @Test
    void overviewStillReturnsPrintersAndAssignmentsWhenPrintingDisabled() throws Exception {
        PrintCenterOverviewResponse overview = new PrintCenterOverviewResponse();
        overview.store_id = 1L;
        overview.printing_enabled = false;
        overview.printers = List.of(printer());
        overview.assignments = List.of(assignment());
        when(printerConfigService.getOverview(1L)).thenReturn(overview);

        mockMvc.perform(get("/api/v1/admin/printing").param("store_id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.printing_enabled").value(false))
            .andExpect(jsonPath("$.data.printers", hasSize(1)))
            .andExpect(jsonPath("$.data.assignments", hasSize(1)));
    }

    @Test
    void printerListEndpointWorksWhenPrintingDisabled() throws Exception {
        when(printerConfigService.getPrinters(1L)).thenReturn(List.of(printer()));

        mockMvc.perform(get("/api/v1/admin/printing/printers").param("store_id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));

        InOrder orderedAccess = inOrder(authorizationService, moduleAccessEvaluator);
        orderedAccess.verify(authorizationService).requireForStore(
            1L,
            Capability.ADMIN_PRINTING_MANAGE,
            Capability.ADMIN_STORE_CONFIG
        );
        orderedAccess.verify(moduleAccessEvaluator).requireCapability(1L, "PRINTING");
    }

    @Test
    void assignmentEndpointWorksWhenPrintingDisabled() throws Exception {
        when(printerAssignmentService.getAssignments(1L)).thenReturn(List.of(assignment()));

        mockMvc.perform(get("/api/v1/admin/printing/assignments").param("store_id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void printJobsEndpointWorksWhenPrintingDisabled() throws Exception {
        when(printJobService.searchJobs(eq(1L), any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/printing/jobs").param("store_id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void acknowledgePrintJobKeepsTheJobStateAndReturnsSnapshot() throws Exception {
        PrintJob job = new PrintJob();
        job.id = 91L;
        job.store_id = 1L;
        job.status = "FAILED";
        job.module_code = "GRAB";
        when(printJobService.requireJob(91L)).thenReturn(job);
        when(printJobService.acknowledgeAttention(91L, 2L, "已人工通知厨房")).thenReturn(job);
        PrintJobResponse response = new PrintJobResponse();
        response.id = 91L;
        response.status = "FAILED";
        when(printJobService.toResponse(job)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/printing/jobs/91/acknowledge")
                .contentType("application/json")
                .content("{\"note\":\"已人工通知厨房\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(91))
            .andExpect(jsonPath("$.data.status").value("FAILED"));
    }

    @Test
    void savePrinterAndAssignmentWorkWhenPrintingDisabled() throws Exception {
        PrinterConfig printer = printer();
        when(printerConfigService.savePrinter(any(PrinterConfig.class))).thenReturn(printer);

        mockMvc.perform(post("/api/v1/admin/printing/printers")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(printer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(10));

        PrinterAssignment assignment = assignment();
        when(printerAssignmentService.saveAssignment(any(PrinterAssignmentUpdateRequest.class))).thenReturn(assignment);

        PrinterAssignmentUpdateRequest request = new PrinterAssignmentUpdateRequest();
        request.store_id = 1L;
        request.printer_id = 10L;
        request.enabled = true;
        request.font_size = "MEDIUM";

        mockMvc.perform(put("/api/v1/admin/printing/assignments/GRAB")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.module_code").value("GRAB"));
    }

    @Test
    void testPrintAndConnectionReturnClearResponsesWhenPrintingDisabled() throws Exception {
        PrinterTestResponse testResponse = new PrinterTestResponse();
        testResponse.success = false;
        testResponse.message = "Store printing is disabled.";
        when(printDispatcherService.testPrint(any())).thenReturn(testResponse);

        mockMvc.perform(post("/api/v1/admin/printing/printers/test")
                .contentType("application/json")
                .content("{\"store_id\":1,\"printer_id\":10}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(false));

        PrinterConnectionTestResponse connectionResponse = new PrinterConnectionTestResponse();
        connectionResponse.success = false;
        connectionResponse.message = "Connection refused";
        connectionResponse.checked_at = LocalDateTime.now();
        when(printDispatcherService.testConnection(any())).thenReturn(connectionResponse);

        mockMvc.perform(post("/api/v1/admin/printing/printers/connection-test")
                .contentType("application/json")
                .content("{\"store_id\":1,\"printer_id\":10}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.success").value(false))
            .andExpect(jsonPath("$.data.message").value("Connection refused"));
    }

    private PrinterConfig printer() {
        PrinterConfig printer = new PrinterConfig();
        printer.id = 10L;
        printer.store_id = 1L;
        printer.name = "Front Printer";
        printer.ip_address = "192.168.2.200";
        printer.port = 9100;
        printer.printer_type = "ESC_POS_TCP";
        printer.enabled = true;
        return printer;
    }

    private PrinterAssignment assignment() {
        PrinterAssignment assignment = new PrinterAssignment();
        assignment.id = 20L;
        assignment.store_id = 1L;
        assignment.printer_id = 10L;
        assignment.module_code = "GRAB";
        assignment.enabled = true;
        assignment.font_size = "MEDIUM";
        return assignment;
    }
}
