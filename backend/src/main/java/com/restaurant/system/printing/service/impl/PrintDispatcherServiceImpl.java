package com.restaurant.system.printing.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.dto.GrabFontTestRequest;
import com.restaurant.system.printing.dto.GrabFontTestResponse;
import com.restaurant.system.printing.dto.ModuleAssignmentTestRequest;
import com.restaurant.system.printing.dto.PrinterEncodingTestRequest;
import com.restaurant.system.printing.dto.PrinterEncodingTestResponse;
import com.restaurant.system.printing.dto.PrinterTestRequest;
import com.restaurant.system.printing.dto.PrinterTestResponse;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.renderer.PrintMarkup;
import com.restaurant.system.printing.renderer.ReceiptRenderer;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.PrinterConfigService;
import com.restaurant.system.printing.transport.EscPosFontTestMode;
import com.restaurant.system.printing.transport.PrinterTransport;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        FeatureFlagService featureFlagService
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
    }

    @Override
    public void dispatchAfterCommit(String moduleCode, Long storeId, Long orderId) {
        if (!featureFlagService.isEnabled(FeaturePackage.PRINTING)) {
            logger.info("Skipping print for module {} store {} order {} because PRINTING feature is disabled", moduleCode, storeId, orderId);
            return;
        }
        Runnable dispatchTask = () -> taskExecutor.execute(() -> doDispatch(moduleCode, storeId, orderId));
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
        try {
            sendToPrinter(printer, buildTestPrintContent(store, printer, request.module_code));
            PrinterTestResponse response = new PrinterTestResponse();
            response.success = true;
            response.message = "Test print sent";
            return response;
        } catch (Exception exception) {
            logger.error("Test print failed for printer {} store {}", printer.id, request.store_id, exception);
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

        for (String encoding : Arrays.asList("UTF-8", "GBK", "GB2312")) {
            PrinterEncodingTestResponse.PrinterEncodingTestResult result = new PrinterEncodingTestResponse.PrinterEncodingTestResult();
            result.encoding = encoding;
            try {
                sendToPrinter(
                    printer,
                    buildEncodingTestContent(store, printer, encoding, sendCodePage, codePage),
                    encoding,
                    codePage
                );
                result.success = true;
                result.message = "Test ticket sent";
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
                sendDiagnosticGrabTicket(printer, mode);
                result.success = true;
                result.message = "Diagnostic ticket sent";
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
            sendToPrinter(printer, buildCurrentFontSizeTestContent(store, printer));
            response.success = true;
            response.message = "Current font size test sent";
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
            sendToPrinter(printer, content, resolveEffectiveFontSize(assignment, printer));
            response.success = true;
            response.message = "Sent " + request.module_code + " test print to " + printer.name + " (" + printer.ip_address + ":" + printer.port + ").";
            return response;
        } catch (Exception exception) {
            logger.error("Assigned module test print failed for module {} store {}", request.module_code, request.store_id, exception);
            response.success = false;
            response.message = exception.getMessage();
            return response;
        }
    }

    private void doDispatch(String moduleCode, Long storeId, Long orderId) {
        try {
            if (!printerConfigService.isPrintingEnabled(storeId)) {
                logger.info("Skipping print for module {} store {} because printing is disabled", moduleCode, storeId);
                return;
            }

            Optional<PrinterAssignment> assignmentOptional = printerAssignmentRepository.findByStoreIdAndModuleCode(storeId, moduleCode);
            if (assignmentOptional.isEmpty()) {
                logger.info("Skipping print for module {} store {} because no assignment exists", moduleCode, storeId);
                return;
            }
            PrinterAssignment assignment = assignmentOptional.get();
            if (!Boolean.TRUE.equals(assignment.enabled) || assignment.printer_id == null) {
                logger.info("Skipping print for module {} store {} because assignment is disabled or missing printer", moduleCode, storeId);
                return;
            }

            PrinterConfig printer = printerConfigRepository.findById(assignment.printer_id)
                .orElseThrow(() -> new BusinessException("Assigned printer not found"));
            if (!Boolean.TRUE.equals(printer.enabled)) {
                logger.info("Skipping print for module {} store {} because printer {} is disabled", moduleCode, storeId, printer.id);
                return;
            }

            ReceiptRenderer renderer = renderersByModuleCode.get(moduleCode);
            if (renderer == null) {
                logger.info("Skipping print for module {} store {} because no renderer is registered", moduleCode, storeId);
                return;
            }

            PrintRenderRequest renderRequest = buildRenderRequest(moduleCode, storeId, orderId);
            if (renderRequest == null) {
                logger.info("Skipping print for module {} store {} order {} because no render data was available", moduleCode, storeId, orderId);
                return;
            }
            String content = renderer.render(renderRequest);
            if (content == null || content.isBlank()) {
                logger.info("Skipping print for module {} store {} order {} because rendered content was blank", moduleCode, storeId, orderId);
                return;
            }

            sendToPrinter(printer, content, resolveEffectiveFontSize(assignment, printer));
            logger.info("Printed module {} for store {} order {} using printer {}", moduleCode, storeId, orderId, printer.id);
        } catch (Exception exception) {
            logger.error("Print dispatch failed for module {} store {} order {}", moduleCode, storeId, orderId, exception);
        }
    }

    private PrintRenderRequest buildRenderRequest(String moduleCode, Long storeId, Long orderId) {
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
        PrintRenderRequest request = new PrintRenderRequest();
        request.module_code = moduleCode;
        request.store = store;
        request.order = order;
        request.order_items = orderItems;
        request.order_item_options = orderItemOptions;
        request.kitchen_tasks = kitchenTasks;
        request.happened_at = LocalDateTime.now();
        return request;
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
            return buildGrabModuleTestContent(store, effectiveFontSize);
        }
        throw new BusinessException("Module test printing is not supported for " + moduleCode + " yet.");
    }

    private String resolveEffectiveFontSize(PrinterAssignment assignment, PrinterConfig printer) {
        if (assignment.font_size != null && !assignment.font_size.isBlank()) {
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
        order.total_amount = new java.math.BigDecimal("26.99");
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
