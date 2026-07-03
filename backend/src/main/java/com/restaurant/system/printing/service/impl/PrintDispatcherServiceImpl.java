package com.restaurant.system.printing.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.pricing.TaxCalculator;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.printing.CloudPrintingGuard;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.PrintJobStatus;
import com.restaurant.system.printing.PrintingMode;
import com.restaurant.system.printing.dto.OrderReprintRequest;
import com.restaurant.system.printing.dto.OrderPrintOptionResponse;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.dto.GrabFontTestRequest;
import com.restaurant.system.printing.dto.GrabFontTestResponse;
import com.restaurant.system.printing.dto.ModuleAssignmentTestRequest;
import com.restaurant.system.printing.dto.PrinterConnectionTestRequest;
import com.restaurant.system.printing.dto.PrinterConnectionTestResponse;
import com.restaurant.system.printing.dto.PrinterEncodingTestRequest;
import com.restaurant.system.printing.dto.PrinterEncodingTestResponse;
import com.restaurant.system.printing.dto.PrinterTestRequest;
import com.restaurant.system.printing.dto.PrinterTestResponse;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.renderer.PrintMarkup;
import com.restaurant.system.printing.renderer.ReceiptRenderer;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.PrintJobService;
import com.restaurant.system.printing.service.PrinterConfigService;
import com.restaurant.system.printing.transport.EscPosFontTestMode;
import com.restaurant.system.printing.transport.PrinterTransport;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.concurrent.Executor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PrintDispatcherServiceImpl implements PrintDispatcherService {

    private static final Logger logger = LoggerFactory.getLogger(PrintDispatcherServiceImpl.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PrinterConfigService printerConfigService;
    private final PrinterConfigRepository printerConfigRepository;
    private final PrinterAssignmentRepository printerAssignmentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemOptionRepository orderItemOptionRepository;
    private final KitchenTaskRepository kitchenTaskRepository;
    private final StoreRepository storeRepository;
    private final List<PrinterTransport> printerTransports;
    private final Map<String, ReceiptRenderer> renderersByModuleCode;
    private final Executor taskExecutor;
    private final FeatureFlagService featureFlagService;
    private final PrintJobService printJobService;
    private final CloudPrintingGuard cloudPrintingGuard;

    public PrintDispatcherServiceImpl(
        PrinterConfigService printerConfigService,
        PrinterConfigRepository printerConfigRepository,
        PrinterAssignmentRepository printerAssignmentRepository,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderItemOptionRepository orderItemOptionRepository,
        KitchenTaskRepository kitchenTaskRepository,
        StoreRepository storeRepository,
        List<PrinterTransport> printerTransports,
        List<ReceiptRenderer> renderers,
        @Qualifier("printTaskExecutor") Executor taskExecutor,
        FeatureFlagService featureFlagService,
        PrintJobService printJobService,
        CloudPrintingGuard cloudPrintingGuard
    ) {
        this.printerConfigService = printerConfigService;
        this.printerConfigRepository = printerConfigRepository;
        this.printerAssignmentRepository = printerAssignmentRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemOptionRepository = orderItemOptionRepository;
        this.kitchenTaskRepository = kitchenTaskRepository;
        this.storeRepository = storeRepository;
        this.printerTransports = printerTransports;
        this.renderersByModuleCode = renderers.stream().collect(Collectors.toMap(ReceiptRenderer::getModuleCode, renderer -> renderer));
        this.taskExecutor = taskExecutor;
        this.featureFlagService = featureFlagService;
        this.printJobService = printJobService;
        this.cloudPrintingGuard = cloudPrintingGuard;
    }

    @Override
    public void dispatchAfterCommit(String moduleCode, Long storeId, Long orderId) {
        if (!featureFlagService.isEnabled(FeaturePackage.PRINTING)) {
            logger.info("Skipping print for module {} store {} order {} because PRINTING feature is disabled", moduleCode, storeId, orderId);
            return;
        }
        Runnable dispatchTask = () -> taskExecutor.execute(() -> doDispatch(moduleCode, storeId, orderId, null));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchTask.run();
                }
            });
            return;
        }
        dispatchTask.run();
    }

    @Override
    public void dispatchOrderUpdateAfterCommit(
        String moduleCode,
        Long storeId,
        Long orderId,
        Long orderUpdateBatchId
    ) {
        if (!supportsAutomaticUpdateTicket(moduleCode)) {
            throw new BusinessException("Only GRAB and FRONTDESK_RECEIPT support automatic update tickets");
        }
        if (!featureFlagService.isEnabled(FeaturePackage.PRINTING)) {
            logger.info(
                "Skipping update print for module {} store {} order {} batch {} because PRINTING feature is disabled",
                moduleCode,
                storeId,
                orderId,
                orderUpdateBatchId
            );
            return;
        }
        Runnable dispatchTask = () -> taskExecutor.execute(
            () -> doDispatch(moduleCode, storeId, orderId, orderUpdateBatchId)
        );
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatchTask.run();
                }
            });
            return;
        }
        dispatchTask.run();
    }

    @Override
    public PrinterTestResponse testPrint(PrinterTestRequest request) {
        PrinterConfig printer = printerConfigRepository.findById(request.printer_id)
            .orElseThrow(() -> new BusinessException("Printer not found"));
        if (!request.store_id.equals(printer.store_id)) {
            throw new BusinessException("Printer does not belong to store");
        }
        Store store = storeRepository.findById(request.store_id).orElseThrow(() -> new BusinessException("Store not found"));
        if (!printerConfigService.isPrintingEnabled(request.store_id)) {
            PrinterTestResponse response = new PrinterTestResponse();
            response.success = false;
            response.message = "Printing is disabled for this store.";
            return response;
        }
        PrintJob job = printJobService.createPendingJob(
            store.organization_id,
            request.store_id,
            null,
            printer.id,
            request.module_code == null || request.module_code.isBlank() ? "TEST_PRINT" : request.module_code,
            "TEST_PRINT",
            null,
            buildPayloadSnapshot(request.module_code == null ? "TEST_PRINT" : request.module_code, request.store_id, null, "ADMIN_TEST_PRINT")
        );
        try {
            String content = buildTestPrintContent(store, printer, request.module_code);
            job = printJobService.attachRenderedContent(job, printer.id, content);
            if (isPadDirectMode(request.store_id)) {
                job = printJobService.markPadDirectQueued(job, printer);
                PrinterTestResponse response = new PrinterTestResponse();
                response.success = true;
                response.message = "Pad Direct test print job queued. Backend did not connect to the physical printer.";
                logger.info("Queued PAD_DIRECT test print job {} for printer {} store {}", job.id, printer.id, request.store_id);
                return response;
            }
            if (!isMockMode(request.store_id) && markCloudPrivatePrinterBlocked(job, printer)) {
                PrinterTestResponse response = new PrinterTestResponse();
                response.success = false;
                response.message = cloudPrintingGuard.blockedBackendTcpMessage(printer).orElse(CloudPrintingGuard.ERROR_MESSAGE);
                return response;
            }
            job = printJobService.markPrinting(job, printer);
            if (isMockMode(request.store_id)) {
                logMockPrint("TEST_PRINT", printer, job, content);
                printJobService.markPrinted(job, printer, "Mock print succeeded - no physical printer used");
            } else {
                sendToPrinter(printer, content);
                printJobService.markPrinted(job, printer);
            }
            PrinterTestResponse response = new PrinterTestResponse();
            response.success = true;
            response.message = isMockMode(request.store_id) ? "Mock test print succeeded - no physical printer used" : "Test print sent";
            return response;
        } catch (Exception exception) {
            logger.error("Test print failed for printer {} store {}", printer.id, request.store_id, exception);
            printJobService.markFailed(job, printer, "TEST_PRINT_FAILED", exception.getMessage());
            PrinterTestResponse response = new PrinterTestResponse();
            response.success = false;
            response.message = exception.getMessage();
            return response;
        }
    }

    @Override
    public PrinterEncodingTestResponse testEncodings(PrinterEncodingTestRequest request) {
        PrinterConfig printer = printerConfigRepository.findById(request.printer_id)
            .orElseThrow(() -> new BusinessException("Printer not found"));
        if (!request.store_id.equals(printer.store_id)) {
            throw new BusinessException("Printer does not belong to store");
        }
        Store store = storeRepository.findById(request.store_id).orElseThrow(() -> new BusinessException("Store not found"));
        boolean sendCodePage = Boolean.TRUE.equals(request.send_code_page_command);
        Integer codePage = sendCodePage ? request.escpos_code_page : null;

        PrinterEncodingTestResponse response = new PrinterEncodingTestResponse();
        response.code_page_command_sent = sendCodePage;
        response.escpos_code_page = codePage;
        response.recommendation = "RP820-class ESC/POS printers usually work best with GBK first. UTF-8 often prints garbled text. GB2312 can work on some firmware, but GBK is the safer default if Chinese needs to coexist with mixed content.";
        Optional<String> cloudBlockMessage = (!isMockMode(request.store_id) && !isPadDirectMode(request.store_id))
            ? cloudPrintingGuard.blockedBackendTcpMessage(printer)
            : Optional.empty();

        for (String encoding : Arrays.asList("UTF-8", "GBK", "GB2312")) {
            PrinterEncodingTestResponse.PrinterEncodingTestResult result = new PrinterEncodingTestResponse.PrinterEncodingTestResult();
            result.encoding = encoding;
            try {
                String content = buildEncodingTestContent(store, printer, encoding, sendCodePage, codePage);
                if (isPadDirectMode(request.store_id)) {
                    result.success = false;
                    result.message = "Pad Direct mode requires running encoding tests from the Android Pad on the store LAN.";
                } else if (isMockMode(request.store_id)) {
                    logMockPrint("ENCODING_TEST_" + encoding, printer, null, content);
                    result.success = true;
                    result.message = "Mock encoding test succeeded - no physical printer used";
                } else if (cloudBlockMessage.isPresent()) {
                    result.success = false;
                    result.message = cloudBlockMessage.get();
                } else {
                    sendToPrinter(
                        printer,
                        content,
                        encoding,
                        codePage
                    );
                    result.success = true;
                    result.message = "Test ticket sent";
                }
            } catch (Exception exception) {
                logger.error("Encoding test print failed for printer {} store {} encoding {}", printer.id, request.store_id, encoding, exception);
                result.success = false;
                result.message = exception.getMessage();
            }
            response.results.add(result);
        }
        response.success = response.results.stream().allMatch(result -> result.success);
        return response;
    }

    @Override
    public GrabFontTestResponse testGrabFontModes(GrabFontTestRequest request) {
        PrinterConfig printer = printerConfigRepository.findById(request.printer_id)
            .orElseThrow(() -> new BusinessException("Printer not found"));
        if (!request.store_id.equals(printer.store_id)) {
            throw new BusinessException("Printer does not belong to store");
        }

        GrabFontTestResponse response = new GrabFontTestResponse();
        Optional<String> cloudBlockMessage = (!isMockMode(request.store_id) && !isPadDirectMode(request.store_id))
            ? cloudPrintingGuard.blockedBackendTcpMessage(printer)
            : Optional.empty();
        for (EscPosFontTestMode mode : EscPosFontTestMode.values()) {
            GrabFontTestResponse.GrabFontTestResult result = new GrabFontTestResponse.GrabFontTestResult();
            result.test_mode = mode.label;
            result.command_bytes = mode.activateHex();
            try {
                logger.info(
                    "Running GRAB font size diagnostic: mode={} activate={} reset={} printer_ip={} printer_port={}",
                    mode.label,
                    mode.activateHex(),
                    mode.resetHex(),
                    printer.ip_address,
                    printer.port
                );
                if (isPadDirectMode(request.store_id)) {
                    result.success = false;
                    result.message = "Pad Direct mode requires running GRAB font diagnostics from the Android Pad on the store LAN.";
                } else if (isMockMode(request.store_id)) {
                    logMockPrint("GRAB_FONT_TEST_" + mode.label, printer, null, String.join("\n", List.of(
                        mode.label,
                        "GRAB TICKET",
                        "黄瓜 x1",
                        "炸春卷 x1",
                        "大二(S) | 走香 走牛 +蛋 +葱"
                    )));
                    result.success = true;
                    result.message = "Mock font test succeeded - no physical printer used";
                } else if (cloudBlockMessage.isPresent()) {
                    result.success = false;
                    result.message = cloudBlockMessage.get();
                } else {
                    sendDiagnosticGrabTicket(printer, mode);
                    result.success = true;
                    result.message = "Diagnostic ticket sent";
                }
            } catch (Exception exception) {
                logger.error(
                    "GRAB font size diagnostic failed: mode={} activate={} reset={} printer_ip={} printer_port={}",
                    mode.label,
                    mode.activateHex(),
                    mode.resetHex(),
                    printer.ip_address,
                    printer.port,
                    exception
                );
                result.success = false;
                result.message = exception.getMessage();
            }
            response.results.add(result);
        }
        response.success = response.results.stream().allMatch(result -> result.success);
        return response;
    }

    @Override
    public PrinterTestResponse testCurrentFontSize(PrinterTestRequest request) {
        PrinterConfig printer = printerConfigRepository.findById(request.printer_id)
            .orElseThrow(() -> new BusinessException("Printer not found"));
        if (!request.store_id.equals(printer.store_id)) {
            throw new BusinessException("Printer does not belong to store");
        }
        Store store = storeRepository.findById(request.store_id).orElseThrow(() -> new BusinessException("Store not found"));
        PrinterTestResponse response = new PrinterTestResponse();
        try {
            String content = buildCurrentFontSizeTestContent(store, printer);
            if (isPadDirectMode(request.store_id)) {
                response.success = false;
                response.message = "Pad Direct mode requires running font size tests from the Android Pad on the store LAN.";
                return response;
            }
            if (isMockMode(request.store_id)) {
                logMockPrint("FONT_SIZE_TEST", printer, null, content);
            } else if (cloudPrintingGuard.blockedBackendTcpMessage(printer).isPresent()) {
                response.success = false;
                response.message = cloudPrintingGuard.blockedBackendTcpMessage(printer).get();
                return response;
            } else {
                sendToPrinter(printer, content);
            }
            response.success = true;
            response.message = isMockMode(request.store_id) ? "Mock current font size test succeeded - no physical printer used" : "Current font size test sent";
            return response;
        } catch (Exception exception) {
            logger.error("Current font size test failed for printer {} store {}", printer.id, request.store_id, exception);
            response.success = false;
            response.message = exception.getMessage();
            return response;
        }
    }

    @Override
    public PrinterTestResponse testAssignedModulePrint(ModuleAssignmentTestRequest request) {
        PrinterTestResponse response = new PrinterTestResponse();
        try {
            if (!printerConfigService.isPrintingEnabled(request.store_id)) {
                response.success = false;
                response.message = "Store printing is disabled.";
                return response;
            }

            if (request.module_code == null || request.module_code.isBlank()) {
                response.success = false;
                response.message = "module_code is required.";
                return response;
            }

            Optional<PrinterAssignment> assignmentOptional = printerAssignmentRepository.findByStoreIdAndModuleCode(request.store_id, request.module_code);
            if (assignmentOptional.isEmpty()) {
                response.success = false;
                response.message = "No printer assignment exists for module " + request.module_code + ".";
                return response;
            }

            PrinterAssignment assignment = assignmentOptional.get();
            if (!Boolean.TRUE.equals(assignment.enabled)) {
                response.success = false;
                response.message = "Printer assignment is disabled for module " + request.module_code + ".";
                return response;
            }
            if (assignment.printer_id == null) {
                response.success = false;
                response.message = "No printer is assigned to module " + request.module_code + ".";
                return response;
            }

            PrinterConfig printer = printerConfigRepository.findById(assignment.printer_id)
                .orElseThrow(() -> new BusinessException("Assigned printer not found"));
            if (!Boolean.TRUE.equals(printer.enabled)) {
                response.success = false;
                response.message = "Assigned printer is disabled for module " + request.module_code + ".";
                return response;
            }

            String content = buildAssignedModuleTestContent(request.store_id, request.module_code, resolveEffectiveFontSize(assignment, printer));
            Store store = storeRepository.findById(request.store_id).orElseThrow(() -> new BusinessException("Store not found"));
            PrintJob job = printJobService.createPendingJob(
                store.organization_id,
                request.store_id,
                null,
                printer.id,
                request.module_code,
                request.module_code + "_TEST",
                null,
                buildPayloadSnapshot(request.module_code, request.store_id, null, "ADMIN_MODULE_TEST_PRINT")
            );
            job = printJobService.attachRenderedContent(job, printer.id, content);
            if (isPadDirectMode(request.store_id)) {
                job = printJobService.markPadDirectQueued(job, printer);
                response.success = true;
                response.message = "Pad Direct " + request.module_code + " test print job queued. Backend did not connect to the physical printer.";
                logger.info("Queued PAD_DIRECT module test print job {} module {} store {}", job.id, request.module_code, request.store_id);
                return response;
            }
            if (!isMockMode(request.store_id) && markCloudPrivatePrinterBlocked(job, printer)) {
                response.success = false;
                response.message = cloudPrintingGuard.blockedBackendTcpMessage(printer).orElse(CloudPrintingGuard.ERROR_MESSAGE);
                return response;
            }
            job = printJobService.markPrinting(job, printer);
            if (isMockMode(request.store_id)) {
                logMockPrint(request.module_code, printer, job, content);
                printJobService.markPrinted(job, printer, "Mock print succeeded - no physical printer used");
            } else {
                sendToPrinter(printer, content, resolveEffectiveFontSize(assignment, printer));
                printJobService.markPrinted(job, printer);
            }
            response.success = true;
            response.message = isMockMode(request.store_id)
                ? "Mock " + request.module_code + " test print succeeded - no physical printer used."
                : "Sent " + request.module_code + " test print to " + printer.name + " (" + printer.ip_address + ":" + printer.port + ").";
            return response;
        } catch (Exception exception) {
            logger.error("Assigned module test print failed for module {} store {}", request.module_code, request.store_id, exception);
            response.success = false;
            response.message = exception.getMessage();
            return response;
        }
    }

    private void doDispatch(String moduleCode, Long storeId, Long orderId, Long orderUpdateBatchId) {
        PrintJob job = null;
        PrinterConfig printer = null;
        try {
            Store store = storeRepository.findById(storeId).orElse(null);
            if (store == null) {
                logger.error("Print dispatch failed before job creation: store {} not found for module {} order {}", storeId, moduleCode, orderId);
                return;
            }
            job = printJobService.createPendingJob(
                store.organization_id,
                storeId,
                orderId,
                orderUpdateBatchId,
                null,
                moduleCode,
                orderUpdateBatchId == null ? moduleCode : moduleCode + "_UPDATE",
                null,
                buildPayloadSnapshot(
                    moduleCode,
                    storeId,
                    orderId,
                    orderUpdateBatchId == null ? "ORDER_SUBMIT" : "ORDER_UPDATE_BATCH"
                )
            );
            logger.info("Created print job {} for module {} store {} order {}", job.id, moduleCode, storeId, orderId);

            if (!printerConfigService.isPrintingEnabled(storeId)) {
                printJobService.markCancelled(job, null, "PRINTING_DISABLED", "Store printing is disabled");
                logger.info("Print job {} cancelled because printing is disabled", job.id);
                return;
            }

            Optional<PrinterAssignment> assignmentOptional = printerAssignmentRepository.findByStoreIdAndModuleCode(storeId, moduleCode);
            if (assignmentOptional.isEmpty()) {
                printJobService.markFailed(job, null, "ASSIGNMENT_MISSING", "No printer assignment exists for module " + moduleCode);
                logger.warn("Print job {} failed because assignment is missing for module {} store {}", job.id, moduleCode, storeId);
                return;
            }
            PrinterAssignment assignment = assignmentOptional.get();
            if (!Boolean.TRUE.equals(assignment.enabled) || assignment.printer_id == null) {
                printJobService.markFailed(job, null, "ASSIGNMENT_DISABLED", "Printer assignment is disabled or missing printer for module " + moduleCode);
                logger.warn("Print job {} failed because assignment is disabled or missing printer", job.id);
                return;
            }

            printer = printerConfigRepository.findById(assignment.printer_id)
                .orElseThrow(() -> new BusinessException("Assigned printer not found"));
            if (!Boolean.TRUE.equals(printer.enabled)) {
                job = printJobService.attachRenderedContent(job, printer.id, null);
                printJobService.markFailed(job, printer, "PRINTER_DISABLED", "Assigned printer is disabled");
                logger.warn("Print job {} failed because printer {} is disabled", job.id, printer.id);
                return;
            }

            ReceiptRenderer renderer = renderersByModuleCode.get(moduleCode);
            if (renderer == null) {
                job = printJobService.attachRenderedContent(job, printer.id, null);
                printJobService.markFailed(job, printer, "RENDERER_MISSING", "No renderer is registered for module " + moduleCode);
                logger.warn("Print job {} failed because renderer is missing for module {}", job.id, moduleCode);
                return;
            }

            PrintRenderRequest renderRequest = buildRenderRequest(moduleCode, storeId, orderId, orderUpdateBatchId);
            if (renderRequest == null) {
                job = printJobService.attachRenderedContent(job, printer.id, null);
                printJobService.markFailed(job, printer, "RENDER_DATA_MISSING", "No render data was available");
                logger.warn("Print job {} failed because no render data was available", job.id);
                return;
            }
            String content = renderer.render(renderRequest);
            if (content == null || content.isBlank()) {
                job = printJobService.attachRenderedContent(job, printer.id, content);
                printJobService.markFailed(job, printer, "RENDERED_CONTENT_BLANK", "Rendered content was blank");
                logger.warn("Print job {} failed because rendered content was blank", job.id);
                return;
            }

            job = printJobService.attachRenderedContent(job, printer.id, content);
            if (isPadDirectMode(storeId)) {
                job = printJobService.markPadDirectQueued(job, printer);
                int copies = resolveCopyCount(moduleCode, assignment, renderRequest.order);
                logger.info(
                    "PAD_DIRECT queued print job {} module {} store {} order {} printer {} copies {}. Backend did not connect to printer.",
                    job.id,
                    moduleCode,
                    storeId,
                    orderId,
                    printer.id,
                    copies
                );
                return;
            }
            if (!isMockMode(storeId) && markCloudPrivatePrinterBlocked(job, printer)) {
                logger.warn(
                    "Blocked cloud private printer connection for print job {} module {} store {} order {} printer {}",
                    job.id,
                    moduleCode,
                    storeId,
                    orderId,
                    printer.id
                );
                return;
            }
            job = printJobService.markPrinting(job, printer);
            logger.info("Dispatching print job {} module {} store {} order {} to printer {}", job.id, moduleCode, storeId, orderId, printer.id);
            int copies = resolveCopyCount(moduleCode, assignment, renderRequest.order);
            for (int copyIndex = 0; copyIndex < copies; copyIndex++) {
                if (isMockMode(storeId)) {
                    logMockPrint(moduleCode, printer, job, content);
                } else {
                    sendToPrinter(printer, content, resolveEffectiveFontSize(assignment, printer));
                }
            }
            if (isMockMode(storeId)) {
                printJobService.markPrinted(job, printer, "Mock print succeeded - no physical printer used");
                logger.info("Mock printed module {} for store {} order {} using printer {} copies {}", moduleCode, storeId, orderId, printer.id, copies);
            } else {
                printJobService.markPrinted(job, printer);
                logger.info("Printed module {} for store {} order {} using printer {} copies {}", moduleCode, storeId, orderId, printer.id, copies);
            }
        } catch (Exception exception) {
            if (job != null) {
                printJobService.markFailed(job, printer, "DISPATCH_ERROR", exception.getMessage());
            }
            logger.error("Print dispatch failed for module {} store {} order {}", moduleCode, storeId, orderId, exception);
        }
    }

    @Override
    public PrintJobResponse reprintJob(Long jobId, Long requestedByUserId) {
        PrintJob job = printJobService.requireJob(jobId);
        PrinterConfig printer = requirePrinterForJob(job);
        try {
            String content = job.rendered_text_snapshot;
            PrinterAssignment assignment = printerAssignmentRepository.findByStoreIdAndModuleCode(job.store_id, job.module_code).orElse(null);
            if (content == null || content.isBlank()) {
                content = renderOrderContent(job.module_code, job.store_id, job.order_id);
                job = printJobService.attachRenderedContent(job, printer.id, content);
            } else {
                job = printJobService.attachRenderedContent(job, printer.id, content);
            }
            if (isPadDirectMode(job.store_id)) {
                job = printJobService.markPadDirectQueued(job, printer);
                logger.info("PAD_DIRECT queued existing print job {} for client-side reprint", job.id);
                return printJobService.toResponse(job);
            }
            job.requested_by_user_id = requestedByUserId;
            if (!isMockMode(job.store_id) && markCloudPrivatePrinterBlocked(job, printer)) {
                return printJobService.toResponse(job);
            }
            job = printJobService.markPrinting(job, printer);
            logger.info("Manual reprint requested for print job {} by user {}", job.id, requestedByUserId);
            if (isMockMode(job.store_id)) {
                logMockPrint(job.module_code, printer, job, content);
                return printJobService.toResponse(printJobService.markPrinted(job, printer, "Mock print succeeded - no physical printer used"));
            }
            sendToPrinter(printer, content, resolveEffectiveFontSize(assignment, printer));
            return printJobService.toResponse(printJobService.markPrinted(job, printer));
        } catch (Exception exception) {
            logger.error("Manual reprint failed for print job {}", jobId, exception);
            return printJobService.toResponse(printJobService.markFailed(job, printer, "REPRINT_FAILED", exception.getMessage()));
        }
    }

    @Override
    public PrintJobResponse reprintOrder(Long orderId, OrderReprintRequest request, Long requestedByUserId) {
        Order order = orderRepository.findExistingById(orderId);
        if (order == null) {
            throw new BusinessException("Order not found");
        }
        Store store = storeRepository.findById(order.store_id).orElseThrow(() -> new BusinessException("Store not found"));
        String moduleCode = normalizeReceiptType(request == null ? null : request.receipt_type);
        PrinterAssignment assignment = printerAssignmentRepository.findByStoreIdAndModuleCode(order.store_id, moduleCode).orElse(null);
        PrinterConfig printer = request != null && request.printer_id != null
            ? requirePrinterForStore(request.printer_id, order.store_id)
            : requireAssignedPrinter(order.store_id, moduleCode, assignment);

        PrintJob job = printJobService.createPendingJob(
            store.organization_id,
            order.store_id,
            order.id,
            printer.id,
            moduleCode,
            moduleCode,
            requestedByUserId,
            buildPayloadSnapshot(moduleCode, order.store_id, order.id, "ORDER_CENTER_REPRINT")
        );
        try {
            if (!Boolean.TRUE.equals(printer.enabled)) {
                return printJobService.toResponse(printJobService.markFailed(job, printer, "PRINTER_DISABLED", "Selected printer is disabled"));
            }
            String content = renderOrderContent(moduleCode, order.store_id, order.id);
            job = printJobService.attachRenderedContent(job, printer.id, content);
            if (isPadDirectMode(order.store_id)) {
                job = printJobService.markPadDirectQueued(job, printer);
                logger.info("PAD_DIRECT queued order reprint job {} order {} module {}", job.id, orderId, moduleCode);
                return printJobService.toResponse(job);
            }
            if (!isMockMode(order.store_id) && markCloudPrivatePrinterBlocked(job, printer)) {
                return printJobService.toResponse(job);
            }
            job = printJobService.markPrinting(job, printer);
            logger.info("Order reprint requested for order {} module {} print job {} by user {}", orderId, moduleCode, job.id, requestedByUserId);
            if (isMockMode(order.store_id)) {
                logMockPrint(moduleCode, printer, job, content);
                return printJobService.toResponse(printJobService.markPrinted(job, printer, "Mock print succeeded - no physical printer used"));
            }
            sendToPrinter(printer, content, resolveEffectiveFontSize(assignment, printer));
            return printJobService.toResponse(printJobService.markPrinted(job, printer));
        } catch (Exception exception) {
            logger.error("Order reprint failed for order {} module {} print job {}", orderId, moduleCode, job.id, exception);
            return printJobService.toResponse(printJobService.markFailed(job, printer, "ORDER_REPRINT_FAILED", exception.getMessage()));
        }
    }

    @Override
    public List<OrderPrintOptionResponse> getOrderPrintOptions(Long orderId) {
        Order order = orderRepository.findExistingById(orderId);
        if (order == null) {
            throw new BusinessException("Order not found");
        }
        boolean featureEnabled = featureFlagService.isEnabled(FeaturePackage.PRINTING);
        boolean storeEnabled = printerConfigService.isPrintingEnabled(order.store_id);
        return renderersByModuleCode.keySet().stream()
            .sorted()
            .map(moduleCode -> buildOrderPrintOption(order.store_id, moduleCode, featureEnabled, storeEnabled))
            .toList();
    }

    private OrderPrintOptionResponse buildOrderPrintOption(
        Long storeId,
        String moduleCode,
        boolean featureEnabled,
        boolean storeEnabled
    ) {
        OrderPrintOptionResponse response = new OrderPrintOptionResponse();
        response.module_code = moduleCode;
        response.label = "Reprint " + moduleCode;
        response.available = false;
        if (!featureEnabled) {
            response.unavailable_reason = "Printing feature is disabled";
            return response;
        }
        if (!storeEnabled) {
            response.unavailable_reason = "Store printing mode is disabled";
            return response;
        }
        PrinterAssignment assignment = printerAssignmentRepository.findByStoreIdAndModuleCode(storeId, moduleCode).orElse(null);
        if (assignment == null || assignment.printer_id == null) {
            response.unavailable_reason = "No printer assigned";
            return response;
        }
        if (!Boolean.TRUE.equals(assignment.enabled)) {
            response.unavailable_reason = "Assignment is disabled";
            return response;
        }
        PrinterConfig printer = printerConfigRepository.findById(assignment.printer_id).orElse(null);
        if (printer == null || !Boolean.TRUE.equals(printer.enabled)) {
            response.unavailable_reason = "Assigned printer is unavailable";
            return response;
        }
        response.available = true;
        response.unavailable_reason = null;
        return response;
    }

    @Override
    public PrinterConnectionTestResponse testConnection(PrinterConnectionTestRequest request) {
        PrinterConfig printer = requirePrinterForStore(request.printer_id, request.store_id);
        PrinterConnectionTestResponse response = new PrinterConnectionTestResponse();
        response.checked_at = LocalDateTime.now();
        if (isMockMode(request.store_id)) {
            printer.last_connection_success_at = response.checked_at;
            printer.last_connection_error = "Mock connection test - no physical printer used";
            printer.updated_at = response.checked_at;
            printerConfigRepository.save(printer);
            response.success = true;
            response.message = "Mock connection successful - no physical printer used";
            logger.info("Mock printer connection test succeeded for printer {} store {}", printer.id, printer.store_id);
            return response;
        }
        if (isPadDirectMode(request.store_id)) {
            response.success = false;
            response.message = "Pad Direct mode requires running connection tests from the Android Pad on the store LAN.";
            logger.info("Skipped backend printer connection test for printer {} store {} because mode is PAD_DIRECT", printer.id, printer.store_id);
            return response;
        }
        Optional<String> cloudBlockMessage = cloudPrintingGuard.blockedBackendTcpMessage(printer);
        if (cloudBlockMessage.isPresent()) {
            printer.last_connection_failed_at = response.checked_at;
            printer.last_connection_error = cloudBlockMessage.get();
            printer.updated_at = response.checked_at;
            printerConfigRepository.save(printer);
            response.success = false;
            response.message = cloudBlockMessage.get();
            logger.warn("Blocked cloud printer connection test for printer {} store {}", printer.id, printer.store_id);
            return response;
        }
        try (Socket socket = new Socket()) {
            int timeout = printer.timeout_ms == null ? 3000 : printer.timeout_ms;
            socket.connect(new InetSocketAddress(printer.ip_address, printer.port == null ? 9100 : printer.port), timeout);
            printer.last_connection_success_at = response.checked_at;
            printer.last_connection_error = null;
            printer.updated_at = response.checked_at;
            printerConfigRepository.save(printer);
            response.success = true;
            response.message = "Connection successful";
            logger.info("Printer connection test succeeded for printer {} store {}", printer.id, printer.store_id);
            return response;
        } catch (Exception exception) {
            printer.last_connection_failed_at = response.checked_at;
            printer.last_connection_error = exception.getMessage();
            printer.updated_at = response.checked_at;
            printerConfigRepository.save(printer);
            response.success = false;
            response.message = exception.getMessage();
            logger.warn("Printer connection test failed for printer {} store {}: {}", printer.id, printer.store_id, exception.getMessage());
            return response;
        }
    }

    private PrintRenderRequest buildRenderRequest(String moduleCode, Long storeId, Long orderId) {
        return buildRenderRequest(moduleCode, storeId, orderId, null);
    }

    private PrintRenderRequest buildRenderRequest(
        String moduleCode,
        Long storeId,
        Long orderId,
        Long orderUpdateBatchId
    ) {
        Store store = storeRepository.findById(storeId).orElse(null);
        Order order = orderRepository.findById(orderId).orElse(null);
        if (store == null || order == null) {
            return null;
        }
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.id);
        List<OrderItemOption> orderItemOptions = orderItems.isEmpty() ? List.of() : orderItemOptionRepository.findAllByOrderItemIds(
            orderItems.stream().map(item -> item.id).toList()
        );
        List<KitchenTask> kitchenTasks = kitchenTaskRepository.findAllByOrderId(order.id);
        boolean updateTicket = orderUpdateBatchId != null && supportsAutomaticUpdateTicket(moduleCode);
        if (updateTicket) {
            Set<Long> modifiedOrderItemIds = orderItems.stream()
                .filter(item -> orderUpdateBatchId.equals(item.order_update_batch_id))
                .map(item -> item.id)
                .collect(java.util.stream.Collectors.toSet());
            orderItems = orderItems.stream()
                .filter(item -> modifiedOrderItemIds.contains(item.id))
                .toList();
            orderItemOptions = orderItemOptions.stream()
                .filter(option -> modifiedOrderItemIds.contains(option.order_item_id))
                .toList();
            kitchenTasks = kitchenTasks.stream()
                .filter(task -> modifiedOrderItemIds.contains(task.order_item_id))
                .toList();
        }
        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = moduleCode;
        request.store = store;
        request.order = order;
        request.order_items = orderItems;
        request.order_item_options = orderItemOptions;
        request.kitchen_tasks = kitchenTasks;
        request.happened_at = LocalDateTime.now();
        request.is_update_ticket = updateTicket;
        request.order_update_batch_id = orderUpdateBatchId;
        return request;
    }

    private String renderOrderContent(String moduleCode, Long storeId, Long orderId) {
        return renderOrderContent(moduleCode, storeId, orderId, null);
    }

    private String renderOrderContent(String moduleCode, Long storeId, Long orderId, Long orderUpdateBatchId) {
        ReceiptRenderer renderer = renderersByModuleCode.get(moduleCode);
        if (renderer == null) {
            throw new BusinessException("No renderer is registered for module " + moduleCode);
        }
        PrintRenderRequest renderRequest = buildRenderRequest(moduleCode, storeId, orderId, orderUpdateBatchId);
        if (renderRequest == null) {
            throw new BusinessException("No render data was available");
        }
        String content = renderer.render(renderRequest);
        if (content == null || content.isBlank()) {
            throw new BusinessException("Rendered content was blank");
        }
        return content;
    }

    private int resolveCopyCount(String moduleCode, PrinterAssignment assignment, Order order) {
        if (!PrintModuleCode.FRONTDESK_RECEIPT.equals(moduleCode) || order == null || !isTakeout(order)) {
            return 1;
        }
        Integer configuredCopies = assignment == null ? null : assignment.takeout_receipt_copies;
        return configuredCopies != null && configuredCopies >= 2 ? 2 : 1;
    }

    private boolean isTakeout(Order order) {
        return "pickup".equalsIgnoreCase(order.order_type) || "takeout".equalsIgnoreCase(order.order_type);
    }

    private boolean supportsAutomaticUpdateTicket(String moduleCode) {
        return PrintModuleCode.GRAB.equals(moduleCode) || PrintModuleCode.FRONTDESK_RECEIPT.equals(moduleCode);
    }

    private PrinterConfig requirePrinterForJob(PrintJob job) {
        if (job.printer_id != null) {
            return requirePrinterForStore(job.printer_id, job.store_id);
        }
        PrinterAssignment assignment = printerAssignmentRepository.findByStoreIdAndModuleCode(job.store_id, job.module_code)
            .orElseThrow(() -> new BusinessException("No printer assignment exists for module " + job.module_code));
        return requireAssignedPrinter(job.store_id, job.module_code, assignment);
    }

    private PrinterConfig requireAssignedPrinter(Long storeId, String moduleCode, PrinterAssignment assignment) {
        if (assignment == null) {
            throw new BusinessException("No printer assignment exists for module " + moduleCode);
        }
        if (!Boolean.TRUE.equals(assignment.enabled) || assignment.printer_id == null) {
            throw new BusinessException("Printer assignment is disabled or missing printer for module " + moduleCode);
        }
        return requirePrinterForStore(assignment.printer_id, storeId);
    }

    private PrinterConfig requirePrinterForStore(Long printerId, Long storeId) {
        PrinterConfig printer = printerConfigRepository.findById(printerId)
            .orElseThrow(() -> new BusinessException("Printer not found"));
        if (!storeId.equals(printer.store_id)) {
            throw new BusinessException("Printer does not belong to store");
        }
        return printer;
    }

    private String normalizeReceiptType(String receiptType) {
        if (PrintModuleCode.FRONTDESK_RECEIPT.equals(receiptType)) {
            return PrintModuleCode.FRONTDESK_RECEIPT;
        }
        if (PrintModuleCode.GRAB.equals(receiptType)) {
            return PrintModuleCode.GRAB;
        }
        throw new BusinessException("receiptType must be GRAB or FRONTDESK_RECEIPT");
    }

    private String buildPayloadSnapshot(String moduleCode, Long storeId, Long orderId, String source) {
        return "{\"source\":\"" + source + "\",\"module_code\":\"" + moduleCode + "\",\"store_id\":" + storeId + ",\"order_id\":" + orderId + "}";
    }

    private boolean isMockMode(Long storeId) {
        return PrintingMode.MOCK.equals(printerConfigService.getStorePrintingMode(storeId));
    }

    private boolean isPadDirectMode(Long storeId) {
        return PrintingMode.PAD_DIRECT.equals(printerConfigService.getStorePrintingMode(storeId));
    }

    private boolean markCloudPrivatePrinterBlocked(PrintJob job, PrinterConfig printer) {
        Optional<String> blockedMessage = cloudPrintingGuard.blockedBackendTcpMessage(printer);
        if (blockedMessage.isEmpty()) {
            return false;
        }
        printJobService.markFailed(job, printer, CloudPrintingGuard.ERROR_CODE, blockedMessage.get());
        return true;
    }

    private void logMockPrint(String moduleCode, PrinterConfig printer, PrintJob job, String content) {
        logger.info(
            "\n===== MOCK PRINT START =====\nModule: {}\nPrinter: {}\nPrint Job ID: {}\nOrder ID: {}\n\n{}\n===== MOCK PRINT END =====",
            moduleCode,
            printer == null || printer.name == null ? "Mock Printer" : printer.name,
            job == null ? "N/A" : job.id,
            job == null ? "N/A" : job.order_id,
            content == null ? "" : content
        );
    }

    private void sendToPrinter(PrinterConfig printer, String content) {
        sendToPrinter(printer, content, printer.text_encoding, printer.escpos_code_page);
    }

    private void sendToPrinter(PrinterConfig printer, String content, String overrideEncoding, Integer overrideCodePage) {
        sendToPrinter(printer, content, overrideEncoding, overrideCodePage, printer.font_size);
    }

    private void sendToPrinter(PrinterConfig printer, String content, String overrideFontSize) {
        sendToPrinter(printer, content, printer.text_encoding, printer.escpos_code_page, overrideFontSize);
    }

    private void sendToPrinter(
        PrinterConfig printer,
        String content,
        String overrideEncoding,
        Integer overrideCodePage,
        String overrideFontSize
    ) {
        PrinterTransport transport = printerTransports.stream()
            .filter(candidate -> candidate.supports(printer.printer_type))
            .findFirst()
            .orElseThrow(() -> new BusinessException("No printer transport available for type " + printer.printer_type));
        transport.print(printer, content, overrideEncoding, overrideCodePage, overrideFontSize);
    }

    private void sendDiagnosticGrabTicket(PrinterConfig printer, EscPosFontTestMode mode) {
        PrinterTransport transport = printerTransports.stream()
            .filter(candidate -> candidate.supports(printer.printer_type))
            .findFirst()
            .orElseThrow(() -> new BusinessException("No printer transport available for type " + printer.printer_type));

        transport.printDiagnosticTicket(
            printer,
            List.of(
                "",
                mode.label,
                "GRAB TICKET",
                "--------------------------------",
                "Table/Pickup: T2",
                "Order Type: Dine-in",
                "",
                "Sample Content",
                "--------------------------------"
            ),
            List.of(
                "黄瓜 x1",
                "炸春卷 x1",
                "炸馒头 x1",
                "",
                "大二(S) | 走香 走牛 +蛋 +葱",
                "中素",
                "中酸大宽"
            ),
            List.of(
                "--------------------------------",
                "No order number printed.",
                ""
            ),
            printer.text_encoding,
            printer.escpos_code_page,
            mode
        );
    }

    private String buildTestPrintContent(Store store, PrinterConfig printer, String moduleCode) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("PRINT CENTER TEST\n");
        builder.append("--------------------------------\n");
        builder.append("Store: ").append(store.name == null ? "Store" : store.name).append("\n");
        builder.append("Printer: ").append(printer.name).append("\n");
        if (moduleCode != null && !moduleCode.isBlank()) {
            builder.append("Module: ").append(moduleCode).append("\n");
        }
        builder.append("Time: ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n");
        builder.append("Status: Test print successful\n\n");
        return builder.toString();
    }

    private String buildCurrentFontSizeTestContent(Store store, PrinterConfig printer) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("CURRENT FONT SIZE TEST\n");
        builder.append("--------------------------------\n");
        builder.append("Store: ").append(store.name == null ? "Store" : store.name).append("\n");
        builder.append("Printer: ").append(printer.name).append("\n");
        builder.append("Font Size: ").append(printer.font_size == null || printer.font_size.isBlank() ? "MEDIUM" : printer.font_size).append("\n");
        builder.append("Time: ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n");
        builder.append("--------------------------------\n");
        builder.append(PrintMarkup.doubleHeight("黄瓜 x1")).append("\n");
        builder.append(PrintMarkup.doubleHeight("炸春卷 x1")).append("\n");
        builder.append(PrintMarkup.doubleHeight("大二(S) | 走香 走牛 +蛋 +葱")).append("\n");
        builder.append("--------------------------------\n\n");
        return builder.toString();
    }

    private String buildGrabModuleTestContent(Store store, String effectiveFontSize) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("GRAB TICKET\n");
        builder.append("--------------------------------\n");
        builder.append("Store: ").append(store.name == null ? "Store" : store.name).append("\n");
        builder.append("Table/Pickup: T2\n");
        builder.append("Order Type: Dine-in\n");
        builder.append("Font Size: ").append(effectiveFontSize).append("\n");
        builder.append("Submitted: ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n");
        builder.append("--------------------------------\n");
        builder.append(PrintMarkup.doubleHeight("黄瓜 x1")).append("\n");
        builder.append(PrintMarkup.doubleHeight("炸春卷 x1")).append("\n");
        builder.append(PrintMarkup.doubleHeight("大二(S) | 走香 走牛 +蛋 +葱")).append("\n");
        builder.append(PrintMarkup.doubleHeight("备注：只要一根面")).append("\n");
        builder.append("--------------------------------\n\n");
        return builder.toString();
    }

    private String buildAssignedModuleTestContent(Long storeId, String moduleCode, String effectiveFontSize) {
        Store store = storeRepository.findById(storeId).orElseThrow(() -> new BusinessException("Store not found"));
        if (PrintModuleCode.FRONTDESK_RECEIPT.equals(moduleCode)) {
            ReceiptRenderer renderer = renderersByModuleCode.get(PrintModuleCode.FRONTDESK_RECEIPT);
            if (renderer == null) {
                throw new BusinessException("No renderer is registered for module " + moduleCode);
            }
            return renderer.render(buildFrontdeskReceiptTestRequest(store));
        }
        if (PrintModuleCode.GRAB.equals(moduleCode)) {
            ReceiptRenderer renderer = renderersByModuleCode.get(PrintModuleCode.GRAB);
            if (renderer == null) {
                throw new BusinessException("No renderer is registered for module " + moduleCode);
            }
            return renderer.render(buildGrabReceiptTestRequest(store));
        }
        throw new BusinessException("Module test printing is not supported for " + moduleCode + " yet.");
    }

    private String resolveEffectiveFontSize(PrinterAssignment assignment, PrinterConfig printer) {
        if (assignment != null && assignment.font_size != null && !assignment.font_size.isBlank()) {
            return assignment.font_size;
        }
        if (printer.font_size != null && !printer.font_size.isBlank()) {
            return printer.font_size;
        }
        return "MEDIUM";
    }

    private PrintRenderRequest buildFrontdeskReceiptTestRequest(Store store) {
        Order order = new Order();
        order.id = -1L;
        order.store_id = store.id;
        order.order_type = "dine_in";
        order.table_no = "T2";
        order.pickup_no = null;
        order.subtotal_amount = new java.math.BigDecimal("26.99");
        order.total_amount = TaxCalculator.calculateTotal(order.subtotal_amount);
        order.submitted_at = LocalDateTime.now();

        OrderItem mainItem = new OrderItem();
        mainItem.id = -101L;
        mainItem.order_id = order.id;
        mainItem.item_name_snapshot_zh = "传统牛肉面";
        mainItem.item_name_snapshot_en = "Traditional Beef Noodle";
        mainItem.quantity = 1;
        mainItem.unit_price = new java.math.BigDecimal("21.00");
        mainItem.line_amount = new java.math.BigDecimal("21.00");
        mainItem.combo_role = "main";
        mainItem.status = "submitted";

        OrderItem friedItem = new OrderItem();
        friedItem.id = -102L;
        friedItem.order_id = order.id;
        friedItem.item_name_snapshot_zh = "炸春卷";
        friedItem.item_name_snapshot_en = "Spring Roll";
        friedItem.quantity = 1;
        friedItem.unit_price = new java.math.BigDecimal("5.99");
        friedItem.line_amount = new java.math.BigDecimal("5.99");
        friedItem.combo_role = "standalone";
        friedItem.status = "submitted";

        OrderItemOption sizeOption = new OrderItemOption();
        sizeOption.order_item_id = mainItem.id;
        sizeOption.option_type_snapshot = "size";
        sizeOption.option_name_snapshot_zh = "大碗";
        sizeOption.option_name_snapshot_en = "Large";
        sizeOption.price_delta = java.math.BigDecimal.ZERO;
        sizeOption.quantity = 1;

        OrderItemOption noodleOption = new OrderItemOption();
        noodleOption.order_item_id = mainItem.id;
        noodleOption.option_type_snapshot = "noodle_type";
        noodleOption.option_name_snapshot_zh = "二细";
        noodleOption.option_name_snapshot_en = "Erxi";
        noodleOption.price_delta = java.math.BigDecimal.ZERO;
        noodleOption.quantity = 1;

        OrderItemOption comboOption = new OrderItemOption();
        comboOption.order_item_id = mainItem.id;
        comboOption.option_type_snapshot = "addon";
        comboOption.option_name_snapshot_zh = "套餐";
        comboOption.option_name_snapshot_en = "Combo";
        comboOption.price_delta = new java.math.BigDecimal("5.00");
        comboOption.quantity = 1;

        OrderItemOption paidAddon = new OrderItemOption();
        paidAddon.order_item_id = mainItem.id;
        paidAddon.option_type_snapshot = "addon";
        paidAddon.option_name_snapshot_zh = "加面";
        paidAddon.option_name_snapshot_en = "Extra Noodles";
        paidAddon.price_delta = new java.math.BigDecimal("3.99");
        paidAddon.quantity = 1;

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.FRONTDESK_RECEIPT;
        request.store = store;
        request.order = order;
        request.order_items = List.of(mainItem, friedItem);
        request.order_item_options = List.of(sizeOption, noodleOption, comboOption, paidAddon);
        request.kitchen_tasks = List.of();
        request.happened_at = LocalDateTime.now();
        return request;
    }

    private PrintRenderRequest buildGrabReceiptTestRequest(Store store) {
        Order order = new Order();
        order.id = -2L;
        order.store_id = store.id;
        order.order_type = "dine_in";
        order.table_no = "T2";
        order.submitted_at = LocalDateTime.now();

        OrderItem coldItem = new OrderItem();
        coldItem.id = -201L;
        coldItem.order_id = order.id;
        coldItem.item_name_snapshot_zh = "拌黄瓜";
        coldItem.item_name_snapshot_en = "Cucumber Salad";
        coldItem.category_code_snapshot = "SIDE";
        coldItem.quantity = 1;
        coldItem.status = "submitted";

        OrderItem coldItemTwo = new OrderItem();
        coldItemTwo.id = -203L;
        coldItemTwo.order_id = order.id;
        coldItemTwo.item_name_snapshot_zh = "毛豆";
        coldItemTwo.item_name_snapshot_en = "Edamame";
        coldItemTwo.category_code_snapshot = "SIDE";
        coldItemTwo.quantity = 1;
        coldItemTwo.status = "submitted";

        OrderItem noodleItem = new OrderItem();
        noodleItem.id = -202L;
        noodleItem.order_id = order.id;
        noodleItem.item_name_snapshot_zh = "传统牛肉面";
        noodleItem.item_name_snapshot_en = "Traditional Beef Noodle";
        noodleItem.category_code_snapshot = "SOUP_NOODLE";
        noodleItem.quantity = 1;
        noodleItem.status = "submitted";
        noodleItem.notes = "只要一根面";

        KitchenTask coldTask = new KitchenTask();
        coldTask.id = -301L;
        coldTask.order_id = order.id;
        coldTask.order_item_id = coldItem.id;
        coldTask.store_id = store.id;
        coldTask.station_code = "COLD";
        coldTask.item_name_snapshot_zh = "拌黄瓜";
        coldTask.item_name_snapshot_en = "Cucumber Salad";
        coldTask.special_instructions_snapshot = "黄瓜";
        coldTask.status = "pending";
        coldTask.quantity = 1;
        coldTask.created_at = LocalDateTime.now();

        KitchenTask coldTaskTwo = new KitchenTask();
        coldTaskTwo.id = -303L;
        coldTaskTwo.order_id = order.id;
        coldTaskTwo.order_item_id = coldItemTwo.id;
        coldTaskTwo.store_id = store.id;
        coldTaskTwo.station_code = "COLD";
        coldTaskTwo.item_name_snapshot_zh = "毛豆";
        coldTaskTwo.item_name_snapshot_en = "Edamame";
        coldTaskTwo.special_instructions_snapshot = "毛豆";
        coldTaskTwo.status = "pending";
        coldTaskTwo.quantity = 1;
        coldTaskTwo.created_at = LocalDateTime.now().plusNanos(1);

        KitchenTask noodleTask = new KitchenTask();
        noodleTask.id = -302L;
        noodleTask.order_id = order.id;
        noodleTask.order_item_id = noodleItem.id;
        noodleTask.store_id = store.id;
        noodleTask.station_code = "NOODLE";
        noodleTask.item_name_snapshot_zh = "传统牛肉面";
        noodleTask.item_name_snapshot_en = "Traditional Beef Noodle";
        noodleTask.special_instructions_snapshot = "大二(S) | 走牛 +蛋 +葱 +香菜";
        noodleTask.status = "pending";
        noodleTask.quantity = 1;
        noodleTask.created_at = LocalDateTime.now().plusSeconds(1);

        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = PrintModuleCode.GRAB;
        request.store = store;
        request.order = order;
        request.order_items = List.of(coldItem, coldItemTwo, noodleItem);
        request.order_item_options = List.of();
        request.kitchen_tasks = List.of(coldTask, coldTaskTwo, noodleTask);
        request.happened_at = LocalDateTime.now();
        return request;
    }

    private String buildEncodingTestContent(Store store, PrinterConfig printer, String encoding, boolean sendCodePage, Integer codePage) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n");
        builder.append("ENCODING TEST\n");
        builder.append("--------------------------------\n");
        builder.append("Store: ").append(store.name == null ? "Store" : store.name).append("\n");
        builder.append("Printer: ").append(printer.name).append("\n");
        builder.append("Encoding: ").append(encoding).append("\n");
        builder.append("ESC/POS Code Page: ")
            .append(sendCodePage ? String.valueOf(codePage) : "none")
            .append("\n");
        builder.append("--------------------------------\n");
        builder.append("Chinese Test\n\n");
        builder.append("牛肉面\n");
        builder.append("炸酱面\n");
        builder.append("凉拌黄瓜\n\n");
        builder.append("English Test\n\n");
        builder.append("## ABC123\n\n");
        builder.append("Time: ").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n\n");
        return builder.toString();
    }
}
