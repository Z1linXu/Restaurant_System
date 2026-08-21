package com.restaurant.system.printing.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluation;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.printing.CloudPrintingGuard;
import com.restaurant.system.printing.PrintJobStatus;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.entity.PrinterAssignment;
import com.restaurant.system.printing.entity.PrinterConfig;
import com.restaurant.system.printing.renderer.ReceiptRenderer;
import com.restaurant.system.printing.repository.PrintJobRepository;
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.rules.PrintingDisplayRuleContext;
import com.restaurant.system.printing.rules.PrintingDisplayRuleService;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.printing.service.PrintJobService;
import com.restaurant.system.printing.service.OrderDispatchOutboxService;
import com.restaurant.system.printing.service.PrinterConfigService;
import com.restaurant.system.printing.transport.PrinterTransport;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintDispatcherServiceImplTest {

    @Mock
    private PrinterConfigService printerConfigService;
    @Mock
    private PrinterConfigRepository printerConfigRepository;
    @Mock
    private PrinterAssignmentRepository printerAssignmentRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderItemOptionRepository orderItemOptionRepository;
    @Mock
    private KitchenTaskRepository kitchenTaskRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private PrinterTransport printerTransport;
    @Mock
    private StoreModuleAccessEvaluator moduleAccessEvaluator;
    @Mock
    private PrintJobService printJobService;
    @Mock
    private PrintJobRepository printJobRepository;
    @Mock
    private ReceiptRenderer grabRenderer;
    @Mock
    private ReceiptRenderer frontdeskRenderer;
    @Mock
    private ReceiptRenderer hotKitchenRenderer;
    @Mock
    private HotKitchenPrintEligibilityService hotKitchenPrintEligibilityService;
    @Mock
    private OrderDispatchOutboxService orderDispatchOutboxService;
    @Mock
    private PrintingDisplayRuleService printingDisplayRuleService;

    private PrintDispatcherServiceImpl service;

    @BeforeEach
    void setUp() {
        when(grabRenderer.getModuleCode()).thenReturn(PrintModuleCode.GRAB);
        when(frontdeskRenderer.getModuleCode()).thenReturn(PrintModuleCode.FRONTDESK_RECEIPT);
        when(hotKitchenRenderer.getModuleCode()).thenReturn(PrintModuleCode.HOT_KITCHEN);
        when(moduleAccessEvaluator.evaluateCapability(any(), eq(ModuleKeys.PRINTING))).thenReturn(printingAllowed());
        when(printingDisplayRuleService.activeContext(any())).thenReturn(PrintingDisplayRuleContext.defaultContext());
        when(printingDisplayRuleService.contextForJob(any())).thenReturn(PrintingDisplayRuleContext.defaultContext());
        when(printingDisplayRuleService.historicalContextForOrder(any(), any(), anyString())).thenReturn(PrintingDisplayRuleContext.defaultContext());
        when(printJobService.attachRenderedContent(any(PrintJob.class), any(), nullable(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(printJobService.attachRenderedContent(any(PrintJob.class), any(), nullable(String.class), any(), nullable(String.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        service = newService(new CloudPrintingGuard(new MockEnvironment()));
    }

    private PrintDispatcherServiceImpl newService(CloudPrintingGuard cloudPrintingGuard) {
        return new PrintDispatcherServiceImpl(
            printerConfigService,
            printerConfigRepository,
            printerAssignmentRepository,
            orderRepository,
            orderItemRepository,
            orderItemOptionRepository,
            kitchenTaskRepository,
            storeRepository,
            List.of(printerTransport),
            List.of(grabRenderer, frontdeskRenderer, hotKitchenRenderer),
            Runnable::run,
            printJobService,
            printJobRepository,
            cloudPrintingGuard,
            hotKitchenPrintEligibilityService,
            orderDispatchOutboxService,
            moduleAccessEvaluator,
            printingDisplayRuleService
        );
    }

    private StoreModuleAccessEvaluation printingAllowed() {
        return new StoreModuleAccessEvaluation(
            1L,
            ModuleKeys.PRINTING,
            true,
            true,
            true,
            true,
            true,
            true,
            null,
            "Module capability allowed",
            List.of(),
            List.of(),
            List.of()
        );
    }

    private StoreModuleAccessEvaluation printingDisabled() {
        return new StoreModuleAccessEvaluation(
            1L,
            ModuleKeys.PRINTING,
            true,
            true,
            false,
            true,
            true,
            false,
            StoreModuleAccessEvaluator.MODULE_DISABLED,
            "Module disabled for this Store: PRINTING",
            List.of(),
            List.of(),
            List.of()
        );
    }

    @Test
    void orderDispatchWritesPersistentOutboxInsteadOfStartingInMemoryPrintTask() {
        service.dispatchAfterCommit(PrintModuleCode.GRAB, 1L, 9L);

        verify(orderDispatchOutboxService).enqueue(PrintModuleCode.GRAB, 1L, 9L, null);
        verifyNoInteractions(printJobService);
    }

    @Test
    void persistedDispatchFailureBeforeJobCreationRemainsRetryable() {
        when(storeRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(
            BusinessException.class,
            () -> service.dispatchPersistedEvent(
                PrintModuleCode.GRAB,
                1L,
                9L,
                null,
                "submit:9:GRAB"
            )
        );

        verifyNoInteractions(printJobService);
    }

    @Test
    void persistedDispatchSkipsBeforeJobCreationWhenPrintingModuleUnavailable() {
        when(moduleAccessEvaluator.evaluateCapability(1L, ModuleKeys.PRINTING)).thenReturn(printingDisabled());

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, 1L, 9L, null, "submit:9:GRAB");

        verifyNoInteractions(printJobService);
        verify(storeRepository, never()).findById(1L);
    }

    @Test
    void updateDispatchRendersOnlyItemsFromRequestedBatch() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.GRAB, "dine_in", 1);
        OrderItem oldItem = item(1L, null);
        OrderItem batchItem = item(2L, 77L);
        when(orderItemRepository.findAllByOrderId(fixture.order.id)).thenReturn(List.of(oldItem, batchItem));
        when(orderItemOptionRepository.findAllByOrderItemIds(any())).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(fixture.order.id)).thenReturn(List.of());
        when(grabRenderer.render(org.mockito.ArgumentMatchers.argThat(request ->
            Boolean.TRUE.equals(request.is_update_ticket)
                && Long.valueOf(77L).equals(request.order_update_batch_id)
                && request.order_items.size() == 1
                && request.order_items.get(0).id.equals(batchItem.id)
        ))).thenReturn("UPDATED ITEM ONLY");

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, 1L, fixture.order.id, 77L, null);

        verify(grabRenderer).render(any());
        verify(frontdeskRenderer, never()).render(any());
    }

    @Test
    void hotKitchenDispatchSkipsBeforeJobCreationWhenOrderHasNoHotContent() {
        Store store = new Store();
        store.id = 1L;
        store.organization_id = 1L;
        Order order = new Order();
        order.id = 123L;
        order.store_id = store.id;
        OrderItem item = item(1L, null);
        KitchenTask task = new KitchenTask();
        task.id = 9L;
        task.order_id = order.id;
        task.order_item_id = item.id;
        task.station_code = "NOODLE";
        task.status = "pending";
        when(storeRepository.findById(store.id)).thenReturn(Optional.of(store));
        when(orderRepository.findById(order.id)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(order.id)).thenReturn(List.of(item));
        when(orderItemOptionRepository.findAllByOrderItemIds(any())).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(order.id)).thenReturn(List.of(task));
        when(hotKitchenPrintEligibilityService.hasHotKitchenContent(any(PrintRenderRequest.class))).thenReturn(false);

        service.dispatchPersistedEvent(PrintModuleCode.HOT_KITCHEN, store.id, order.id, null, null);

        verifyNoInteractions(printJobService);
        verify(printerAssignmentRepository, never()).findByStoreIdAndModuleCode(store.id, PrintModuleCode.HOT_KITCHEN);
        verify(hotKitchenRenderer, never()).render(any());
    }

    @Test
    void hotKitchenDispatchCreatesJobWhenOrderHasHotContent() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.HOT_KITCHEN, "dine_in", 1);
        when(hotKitchenPrintEligibilityService.hasHotKitchenContent(any(PrintRenderRequest.class))).thenReturn(true);
        when(hotKitchenRenderer.render(any())).thenReturn("HOT KITCHEN TICKET");

        service.dispatchPersistedEvent(PrintModuleCode.HOT_KITCHEN, 1L, fixture.order.id, null, null);

        verify(hotKitchenRenderer).render(any());
        verify(printJobService).createPendingJob(
            eq(1L),
            eq(1L),
            eq(fixture.order.id),
            any(),
            any(),
            eq(PrintModuleCode.HOT_KITCHEN),
            eq(PrintModuleCode.HOT_KITCHEN),
            any(),
            anyString()
        );
    }

    @Test
    void hotKitchenUpdateDispatchCreatesUpdateReceiptForRequestedBatch() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.HOT_KITCHEN, "dine_in", 1);
        OrderItem oldItem = item(1L, null);
        OrderItem batchItem = item(2L, 99L);
        when(orderItemRepository.findAllByOrderId(fixture.order.id)).thenReturn(List.of(oldItem, batchItem));
        when(orderItemOptionRepository.findAllByOrderItemIds(any())).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(fixture.order.id)).thenReturn(List.of());
        when(hotKitchenPrintEligibilityService.hasHotKitchenContent(any(PrintRenderRequest.class))).thenReturn(true);
        when(hotKitchenRenderer.render(org.mockito.ArgumentMatchers.argThat(request ->
            Boolean.TRUE.equals(request.is_update_ticket)
                && Long.valueOf(99L).equals(request.order_update_batch_id)
                && request.order_items.size() == 1
                && request.order_items.get(0).id.equals(batchItem.id)
        ))).thenReturn("HOT UPDATED ITEM ONLY");

        service.dispatchPersistedEvent(PrintModuleCode.HOT_KITCHEN, 1L, fixture.order.id, 99L, null);

        verify(hotKitchenRenderer).render(any());
        verify(printJobService).createPendingJob(
            eq(1L),
            eq(1L),
            eq(fixture.order.id),
            eq(99L),
            any(),
            eq(PrintModuleCode.HOT_KITCHEN),
            eq("HOT_KITCHEN_UPDATE"),
            any(),
            anyString()
        );
    }

    @Test
    void frontdeskUpdateDispatchRendersOnlyItemsFromRequestedBatch() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.FRONTDESK_RECEIPT, "dine_in", 1);
        OrderItem oldItem = item(1L, null);
        OrderItem batchItem = item(2L, 88L);
        when(orderItemRepository.findAllByOrderId(fixture.order.id)).thenReturn(List.of(oldItem, batchItem));
        when(orderItemOptionRepository.findAllByOrderItemIds(any())).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(fixture.order.id)).thenReturn(List.of());
        when(frontdeskRenderer.render(org.mockito.ArgumentMatchers.argThat(request ->
            Boolean.TRUE.equals(request.is_update_ticket)
                && Long.valueOf(88L).equals(request.order_update_batch_id)
                && request.order_items.size() == 1
                && request.order_items.get(0).id.equals(batchItem.id)
        ))).thenReturn("FRONTDESK UPDATED ITEM ONLY");

        service.dispatchPersistedEvent(PrintModuleCode.FRONTDESK_RECEIPT, 1L, fixture.order.id, 88L, null);

        verify(frontdeskRenderer).render(any());
        verify(grabRenderer, never()).render(any());
        verify(printJobService).createPendingJob(
            eq(1L),
            eq(1L),
            eq(fixture.order.id),
            eq(88L),
            any(),
            eq(PrintModuleCode.FRONTDESK_RECEIPT),
            eq("FRONTDESK_RECEIPT_UPDATE"),
            any(),
            anyString()
        );
    }

    @Test
    void grabUpdateDispatchSkipsBlankRendererBeforeCreatingFailedJob() {
        Store store = new Store();
        store.id = 1L;
        store.organization_id = 1L;
        Order order = new Order();
        order.id = 123L;
        order.store_id = store.id;
        OrderItem drinkItem = item(2L, 77L);
        when(storeRepository.findById(store.id)).thenReturn(Optional.of(store));
        when(orderRepository.findById(order.id)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(order.id)).thenReturn(List.of(drinkItem));
        when(orderItemOptionRepository.findAllByOrderItemIds(any())).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(grabRenderer.render(org.mockito.ArgumentMatchers.argThat(request ->
            Boolean.TRUE.equals(request.is_update_ticket)
                && Long.valueOf(77L).equals(request.order_update_batch_id)
                && request.order_items.size() == 1
                && request.order_items.get(0).id.equals(drinkItem.id)
        ))).thenReturn("");

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, store.id, order.id, 77L, null);

        verify(grabRenderer).render(any());
        verifyNoInteractions(printJobService);
        verify(printerAssignmentRepository, never()).findByStoreIdAndModuleCode(store.id, PrintModuleCode.GRAB);
        verify(printerTransport, never()).print(
            any(PrinterConfig.class),
            anyString(),
            any(),
            any(),
            anyString()
        );
    }

    @Test
    void takeoutFrontdeskReceiptUsesConfiguredTwoCopies() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.FRONTDESK_RECEIPT, "pickup", 2);
        when(frontdeskRenderer.render(any())).thenReturn("TAKEOUT RECEIPT");

        service.dispatchPersistedEvent(PrintModuleCode.FRONTDESK_RECEIPT, 1L, fixture.order.id, null, null);

        verify(printerTransport, times(2)).print(
            eq(fixture.printer),
            eq("TAKEOUT RECEIPT"),
            any(),
            any(),
            anyString()
        );
    }

    @Test
    void dineInFrontdeskReceiptAlwaysUsesOneCopy() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.FRONTDESK_RECEIPT, "dine_in", 2);
        when(frontdeskRenderer.render(any())).thenReturn("DINE IN RECEIPT");

        service.dispatchPersistedEvent(PrintModuleCode.FRONTDESK_RECEIPT, 1L, fixture.order.id, null, null);

        verify(printerTransport, times(1)).print(
            eq(fixture.printer),
            eq("DINE IN RECEIPT"),
            any(),
            any(),
            anyString()
        );
    }

    @Test
    void mockDispatchRunsRendererAndPersistsPrintedJobWithoutTransport() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.GRAB, "dine_in", 1, "MOCK");
        when(grabRenderer.render(any())).thenReturn("SANITIZED MOCK RECEIPT");
        when(printJobService.markPrinted(
            fixture.job,
            fixture.printer,
            "Mock print succeeded - no physical printer used"
        )).thenReturn(fixture.job);

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, 1L, fixture.order.id, null, null);

        verify(grabRenderer).render(any());
        verify(printJobService).attachRenderedContent(
            fixture.job,
            fixture.printer.id,
            "SANITIZED MOCK RECEIPT",
            null,
            PrintingDisplayRuleContext.defaultContext().activeFingerprintOrDefault()
        );
        verify(printJobService).markPrinted(
            fixture.job,
            fixture.printer,
            "Mock print succeeded - no physical printer used"
        );
        verifyNoInteractions(printerTransport);
    }

    @Test
    void mockDispatchUsesOneModeSnapshotWhenStoreModeChangesConcurrently() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.GRAB, "dine_in", 1, "MOCK");
        when(printerConfigService.getStorePrintingMode(1L)).thenReturn("MOCK", "DISABLED");
        when(grabRenderer.render(any())).thenReturn("SANITIZED MOCK RECEIPT");
        when(printJobService.markPrinted(
            fixture.job,
            fixture.printer,
            "Mock print succeeded - no physical printer used"
        )).thenReturn(fixture.job);

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, 1L, fixture.order.id, null, null);

        verify(printerConfigService, times(1)).getStorePrintingMode(1L);
        verify(printJobService).markPrinted(
            fixture.job,
            fixture.printer,
            "Mock print succeeded - no physical printer used"
        );
        verifyNoInteractions(printerTransport);
    }

    @Test
    void padDirectDispatchQueuesPayloadWithAssignmentFontSize() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.GRAB, "dine_in", 1, "PAD_DIRECT");
        fixture.assignment.font_size = "LARGE";
        fixture.printer.font_size = "SMALL";
        when(grabRenderer.render(any())).thenReturn("GRAB RECEIPT");
        when(printJobService.markPadDirectQueued(any(PrintJob.class), eq(fixture.printer), eq("LARGE")))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, 1L, fixture.order.id, null, null);

        verify(printJobService).markPadDirectQueued(any(PrintJob.class), eq(fixture.printer), eq("LARGE"));
        verify(printerTransport, never()).print(
            any(PrinterConfig.class),
            anyString(),
            any(),
            any(),
            anyString()
        );
    }

    @Test
    void existingPrintJobReprintUsesFrozenRenderedSnapshotWithoutRerendering() {
        PrintJob job = new PrintJob();
        job.id = 77L;
        job.store_id = 1L;
        job.order_id = 123L;
        job.printer_id = 10L;
        job.module_code = PrintModuleCode.GRAB;
        job.rendered_text_snapshot = "FROZEN HISTORICAL COMBO OUTPUT";
        job.status = PrintJobStatus.PRINTED;

        PrinterConfig printer = new PrinterConfig();
        printer.id = job.printer_id;
        printer.store_id = job.store_id;
        printer.enabled = true;
        when(printJobService.requireJob(job.id)).thenReturn(job);
        when(printerConfigRepository.findById(printer.id)).thenReturn(Optional.of(printer));
        when(printerConfigService.getStorePrintingMode(job.store_id)).thenReturn("MOCK");
        when(printJobService.markPrinting(job, printer)).thenReturn(job);
        when(printJobService.markPrinted(job, printer, "Mock print succeeded - no physical printer used")).thenReturn(job);

        service.reprintJob(job.id, 5L);

        verify(printJobService).attachRenderedContent(job, printer.id, "FROZEN HISTORICAL COMBO OUTPUT");
        verify(grabRenderer, never()).render(any());
        verifyNoInteractions(printerTransport);
    }

    @Test
    void cloudProfileBlocksPrivatePrinterBeforeTransport() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("cloud");
        service = newService(new CloudPrintingGuard(environment));
        DispatchFixture fixture = configureCloudBlockedDispatch();
        when(grabRenderer.render(any())).thenReturn("GRAB RECEIPT");

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, 1L, fixture.order.id, null, null);

        verify(printJobService).markFailed(
            any(PrintJob.class),
            eq(fixture.printer),
            eq(CloudPrintingGuard.ERROR_CODE),
            contains("Cloud server cannot directly connect")
        );
        verify(printerTransport, never()).print(
            any(PrinterConfig.class),
            anyString(),
            any(),
            any(),
            anyString()
        );
    }

    @Test
    void automaticDispatchRejectsAssignedPrinterFromAnotherStore() {
        Store store = new Store();
        store.id = 1L;
        store.organization_id = 1L;
        PrinterAssignment assignment = new PrinterAssignment();
        assignment.store_id = store.id;
        assignment.module_code = PrintModuleCode.GRAB;
        assignment.printer_id = 10L;
        assignment.enabled = true;
        PrinterConfig printer = new PrinterConfig();
        printer.id = assignment.printer_id;
        printer.store_id = 2L;
        printer.enabled = true;
        PrintJob job = new PrintJob();
        job.id = 99L;
        job.store_id = store.id;
        job.order_id = 123L;
        job.module_code = PrintModuleCode.GRAB;
        job.status = PrintJobStatus.PENDING;
        when(storeRepository.findById(store.id)).thenReturn(Optional.of(store));
        when(printJobService.createPendingJob(
            eq(store.organization_id),
            eq(store.id),
            eq(job.order_id),
            nullable(Long.class),
            nullable(Long.class),
            eq(PrintModuleCode.GRAB),
            anyString(),
            nullable(Long.class),
            anyString(),
            nullable(String.class)
        )).thenReturn(job);
        when(printJobService.createPendingJob(
            eq(store.organization_id),
            eq(store.id),
            eq(job.order_id),
            nullable(Long.class),
            nullable(Long.class),
            eq(PrintModuleCode.GRAB),
            anyString(),
            nullable(Long.class),
            anyString()
        )).thenReturn(job);
        when(printerConfigService.getStorePrintingMode(store.id)).thenReturn("REAL");
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(store.id, PrintModuleCode.GRAB))
            .thenReturn(Optional.of(assignment));
        when(printerConfigRepository.findById(printer.id)).thenReturn(Optional.of(printer));

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, store.id, job.order_id, null, null);

        verify(printJobService).markFailed(
            job,
            null,
            "DISPATCH_ERROR",
            "Printer does not belong to store"
        );
        verify(grabRenderer, never()).render(any());
        verifyNoInteractions(printerTransport);
    }

    private DispatchFixture configureCloudBlockedDispatch() {
        Store store = new Store();
        store.id = 1L;
        store.organization_id = 1L;
        Order order = new Order();
        order.id = 123L;
        order.store_id = store.id;
        order.order_type = "dine_in";
        PrinterAssignment assignment = new PrinterAssignment();
        assignment.store_id = store.id;
        assignment.module_code = PrintModuleCode.GRAB;
        assignment.printer_id = 10L;
        assignment.enabled = true;
        PrinterConfig printer = new PrinterConfig();
        printer.id = assignment.printer_id;
        printer.store_id = store.id;
        printer.enabled = true;
        printer.printer_type = "ESC_POS_TCP";
        printer.ip_address = "192.168.2.200";
        printer.font_size = "MEDIUM";
        PrintJob job = new PrintJob();
        job.id = 99L;
        job.store_id = store.id;
        job.order_id = order.id;
        job.module_code = PrintModuleCode.GRAB;
        job.status = PrintJobStatus.PENDING;
        when(printerConfigService.getStorePrintingMode(store.id)).thenReturn("REAL");
        when(storeRepository.findById(store.id)).thenReturn(Optional.of(store));
        when(orderRepository.findById(order.id)).thenReturn(Optional.of(order));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(store.id, PrintModuleCode.GRAB)).thenReturn(Optional.of(assignment));
        when(printerConfigRepository.findById(printer.id)).thenReturn(Optional.of(printer));
        when(orderItemRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(printJobService.createPendingJob(
            eq(store.organization_id), eq(store.id), eq(order.id), nullable(Long.class), nullable(Long.class), eq(PrintModuleCode.GRAB), anyString(), nullable(Long.class), anyString(), nullable(String.class)
        )).thenReturn(job);
        when(printJobService.createPendingJob(
            eq(store.organization_id), eq(store.id), eq(order.id), nullable(Long.class), nullable(Long.class), eq(PrintModuleCode.GRAB), anyString(), nullable(Long.class), anyString()
        )).thenReturn(job);
        return new DispatchFixture(order, printer, assignment, job);
    }

    private DispatchFixture configureSuccessfulDispatch(String moduleCode, String orderType, int copies) {
        return configureSuccessfulDispatch(moduleCode, orderType, copies, "REAL");
    }

    private DispatchFixture configureSuccessfulDispatch(String moduleCode, String orderType, int copies, String printingMode) {
        Store store = new Store();
        store.id = 1L;
        store.organization_id = 1L;
        Order order = new Order();
        order.id = 123L;
        order.store_id = store.id;
        order.order_type = orderType;
        PrinterAssignment assignment = new PrinterAssignment();
        assignment.store_id = store.id;
        assignment.module_code = moduleCode;
        assignment.printer_id = 10L;
        assignment.enabled = true;
        assignment.takeout_receipt_copies = copies;
        PrinterConfig printer = new PrinterConfig();
        printer.id = assignment.printer_id;
        printer.store_id = store.id;
        printer.enabled = true;
        printer.printer_type = "ESC_POS_TCP";
        printer.ip_address = "8.8.8.8";
        printer.font_size = "MEDIUM";
        PrintJob job = new PrintJob();
        job.id = 99L;
        job.store_id = store.id;
        job.order_id = order.id;
        job.module_code = moduleCode;
        job.status = PrintJobStatus.PENDING;
        when(printerConfigService.getStorePrintingMode(store.id)).thenReturn(printingMode);
        when(storeRepository.findById(store.id)).thenReturn(Optional.of(store));
        when(orderRepository.findById(order.id)).thenReturn(Optional.of(order));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(store.id, moduleCode)).thenReturn(Optional.of(assignment));
        when(printerConfigRepository.findById(printer.id)).thenReturn(Optional.of(printer));
        if (!"PAD_DIRECT".equalsIgnoreCase(printingMode) && !"MOCK".equalsIgnoreCase(printingMode)) {
            when(printerTransport.supports(printer.printer_type)).thenReturn(true);
        }
        when(orderItemRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(printJobService.createPendingJob(
            eq(store.organization_id), eq(store.id), eq(order.id), nullable(Long.class), nullable(Long.class), eq(moduleCode), anyString(), nullable(Long.class), anyString(), nullable(String.class)
        )).thenReturn(job);
        when(printJobService.createPendingJob(
            eq(store.organization_id), eq(store.id), eq(order.id), nullable(Long.class), nullable(Long.class), eq(moduleCode), anyString(), nullable(Long.class), anyString()
        )).thenReturn(job);
        if (!"PAD_DIRECT".equalsIgnoreCase(printingMode) && !"MOCK".equalsIgnoreCase(printingMode)) {
            when(printJobService.markPrinting(job, printer)).thenReturn(job);
            when(printJobService.markPrinted(job, printer)).thenReturn(job);
        } else if ("MOCK".equalsIgnoreCase(printingMode)) {
            when(printJobService.markPrinting(job, printer)).thenReturn(job);
        }
        return new DispatchFixture(order, printer, assignment, job);
    }

    private OrderItem item(Long id, Long batchId) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.order_id = 123L;
        item.order_update_batch_id = batchId;
        item.quantity = 1;
        return item;
    }

    private record DispatchFixture(Order order, PrinterConfig printer, PrinterAssignment assignment, PrintJob job) {
    }

    @Test
    void orderTriggeredDispatchCreatesCancelledJobWhenPrintingDisabled() {
        Store store = new Store();
        store.id = 1L;
        store.organization_id = 1L;
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(printerConfigService.getStorePrintingMode(1L)).thenReturn("DISABLED");

        PrintJob job = new PrintJob();
        job.id = 99L;
        job.store_id = 1L;
        job.order_id = 123L;
        job.module_code = PrintModuleCode.GRAB;
        job.status = PrintJobStatus.PENDING;
        when(printJobService.createPendingJob(
            eq(1L),
            eq(1L),
            eq(123L),
            nullable(Long.class),
            nullable(Long.class),
            eq(PrintModuleCode.GRAB),
            eq(PrintModuleCode.GRAB),
            nullable(Long.class),
            anyString(),
            nullable(String.class)
        )).thenReturn(job);
        when(printJobService.createPendingJob(
            eq(1L),
            eq(1L),
            eq(123L),
            nullable(Long.class),
            nullable(Long.class),
            eq(PrintModuleCode.GRAB),
            eq(PrintModuleCode.GRAB),
            nullable(Long.class),
            anyString()
        )).thenReturn(job);

        service.dispatchPersistedEvent(PrintModuleCode.GRAB, 1L, 123L, null, null);

        verify(printJobService).markCancelled(job, null, "PRINTING_DISABLED", "Store printing is disabled");
        verify(printerAssignmentRepository, never()).findByStoreIdAndModuleCode(1L, PrintModuleCode.GRAB);
        verify(printerTransport, never()).print(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
