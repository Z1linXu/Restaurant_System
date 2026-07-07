package com.restaurant.system.printing.service.impl;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
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
import com.restaurant.system.printing.repository.PrinterAssignmentRepository;
import com.restaurant.system.printing.repository.PrinterConfigRepository;
import com.restaurant.system.printing.semantic.HotKitchenPrintEligibilityService;
import com.restaurant.system.printing.service.PrintJobService;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
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
    private FeatureFlagService featureFlagService;
    @Mock
    private PrintJobService printJobService;
    @Mock
    private ReceiptRenderer grabRenderer;
    @Mock
    private ReceiptRenderer frontdeskRenderer;
    @Mock
    private ReceiptRenderer hotKitchenRenderer;
    @Mock
    private HotKitchenPrintEligibilityService hotKitchenPrintEligibilityService;

    private PrintDispatcherServiceImpl service;

    @BeforeEach
    void setUp() {
        when(grabRenderer.getModuleCode()).thenReturn(PrintModuleCode.GRAB);
        when(frontdeskRenderer.getModuleCode()).thenReturn(PrintModuleCode.FRONTDESK_RECEIPT);
        when(hotKitchenRenderer.getModuleCode()).thenReturn(PrintModuleCode.HOT_KITCHEN);
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
            featureFlagService,
            printJobService,
            cloudPrintingGuard,
            hotKitchenPrintEligibilityService
        );
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

        service.dispatchOrderUpdateAfterCommit(PrintModuleCode.GRAB, 1L, fixture.order.id, 77L);

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

        when(featureFlagService.isEnabled(FeaturePackage.PRINTING)).thenReturn(true);
        when(storeRepository.findById(store.id)).thenReturn(Optional.of(store));
        when(orderRepository.findById(order.id)).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(order.id)).thenReturn(List.of(item));
        when(orderItemOptionRepository.findAllByOrderItemIds(any())).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(order.id)).thenReturn(List.of(task));
        when(hotKitchenPrintEligibilityService.hasHotKitchenContent(any(PrintRenderRequest.class))).thenReturn(false);

        service.dispatchAfterCommit(PrintModuleCode.HOT_KITCHEN, store.id, order.id);

        verifyNoInteractions(printJobService);
        verify(printerAssignmentRepository, never()).findByStoreIdAndModuleCode(store.id, PrintModuleCode.HOT_KITCHEN);
        verify(hotKitchenRenderer, never()).render(any());
    }

    @Test
    void hotKitchenDispatchCreatesJobWhenOrderHasHotContent() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.HOT_KITCHEN, "dine_in", 1);
        when(hotKitchenPrintEligibilityService.hasHotKitchenContent(any(PrintRenderRequest.class))).thenReturn(true);
        when(hotKitchenRenderer.render(any())).thenReturn("HOT KITCHEN TICKET");

        service.dispatchAfterCommit(PrintModuleCode.HOT_KITCHEN, 1L, fixture.order.id);

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

        service.dispatchOrderUpdateAfterCommit(PrintModuleCode.HOT_KITCHEN, 1L, fixture.order.id, 99L);

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

        service.dispatchOrderUpdateAfterCommit(PrintModuleCode.FRONTDESK_RECEIPT, 1L, fixture.order.id, 88L);

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
    void takeoutFrontdeskReceiptUsesConfiguredTwoCopies() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.FRONTDESK_RECEIPT, "pickup", 2);
        when(frontdeskRenderer.render(any())).thenReturn("TAKEOUT RECEIPT");

        service.dispatchAfterCommit(PrintModuleCode.FRONTDESK_RECEIPT, 1L, fixture.order.id);

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

        service.dispatchAfterCommit(PrintModuleCode.FRONTDESK_RECEIPT, 1L, fixture.order.id);

        verify(printerTransport, times(1)).print(
            eq(fixture.printer),
            eq("DINE IN RECEIPT"),
            any(),
            any(),
            anyString()
        );
    }

    @Test
    void padDirectDispatchQueuesPayloadWithAssignmentFontSize() {
        DispatchFixture fixture = configureSuccessfulDispatch(PrintModuleCode.GRAB, "dine_in", 1, "PAD_DIRECT");
        fixture.assignment.font_size = "LARGE";
        fixture.printer.font_size = "SMALL";
        when(grabRenderer.render(any())).thenReturn("GRAB RECEIPT");
        when(printJobService.markPadDirectQueued(any(PrintJob.class), eq(fixture.printer), eq("LARGE")))
            .thenAnswer(invocation -> invocation.getArgument(0));

        service.dispatchAfterCommit(PrintModuleCode.GRAB, 1L, fixture.order.id);

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
    void cloudProfileBlocksPrivatePrinterBeforeTransport() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("cloud");
        service = newService(new CloudPrintingGuard(environment));
        DispatchFixture fixture = configureCloudBlockedDispatch();
        when(grabRenderer.render(any())).thenReturn("GRAB RECEIPT");

        service.dispatchAfterCommit(PrintModuleCode.GRAB, 1L, fixture.order.id);

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

        when(featureFlagService.isEnabled(FeaturePackage.PRINTING)).thenReturn(true);
        when(printerConfigService.isPrintingEnabled(store.id)).thenReturn(true);
        when(printerConfigService.getStorePrintingMode(store.id)).thenReturn("REAL");
        when(storeRepository.findById(store.id)).thenReturn(Optional.of(store));
        when(orderRepository.findById(order.id)).thenReturn(Optional.of(order));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(store.id, PrintModuleCode.GRAB)).thenReturn(Optional.of(assignment));
        when(printerConfigRepository.findById(printer.id)).thenReturn(Optional.of(printer));
        when(orderItemRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(printJobService.createPendingJob(
            eq(store.organization_id), eq(store.id), eq(order.id), any(), any(), eq(PrintModuleCode.GRAB), anyString(), any(), anyString()
        )).thenReturn(job);
        when(printJobService.attachRenderedContent(eq(job), eq(printer.id), anyString())).thenReturn(job);
        return new DispatchFixture(order, printer, assignment);
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

        when(featureFlagService.isEnabled(FeaturePackage.PRINTING)).thenReturn(true);
        when(printerConfigService.isPrintingEnabled(store.id)).thenReturn(true);
        when(printerConfigService.getStorePrintingMode(store.id)).thenReturn(printingMode);
        when(storeRepository.findById(store.id)).thenReturn(Optional.of(store));
        when(orderRepository.findById(order.id)).thenReturn(Optional.of(order));
        when(printerAssignmentRepository.findByStoreIdAndModuleCode(store.id, moduleCode)).thenReturn(Optional.of(assignment));
        when(printerConfigRepository.findById(printer.id)).thenReturn(Optional.of(printer));
        if (!"PAD_DIRECT".equalsIgnoreCase(printingMode)) {
            when(printerTransport.supports(printer.printer_type)).thenReturn(true);
        }
        when(orderItemRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(kitchenTaskRepository.findAllByOrderId(order.id)).thenReturn(List.of());
        when(printJobService.createPendingJob(
            eq(store.organization_id), eq(store.id), eq(order.id), any(), any(), eq(moduleCode), anyString(), any(), anyString()
        )).thenReturn(job);
        when(printJobService.attachRenderedContent(eq(job), eq(printer.id), anyString())).thenReturn(job);
        if (!"PAD_DIRECT".equalsIgnoreCase(printingMode)) {
            when(printJobService.markPrinting(job, printer)).thenReturn(job);
            when(printJobService.markPrinted(job, printer)).thenReturn(job);
        }
        return new DispatchFixture(order, printer, assignment);
    }

    private OrderItem item(Long id, Long batchId) {
        OrderItem item = new OrderItem();
        item.id = id;
        item.order_id = 123L;
        item.order_update_batch_id = batchId;
        item.quantity = 1;
        return item;
    }

    private record DispatchFixture(Order order, PrinterConfig printer, PrinterAssignment assignment) {
    }

    @Test
    void orderTriggeredDispatchCreatesCancelledJobWhenPrintingDisabled() {
        Store store = new Store();
        store.id = 1L;
        store.organization_id = 1L;
        when(featureFlagService.isEnabled(FeaturePackage.PRINTING)).thenReturn(true);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(printerConfigService.isPrintingEnabled(1L)).thenReturn(false);

        PrintJob job = new PrintJob();
        job.id = 99L;
        job.store_id = 1L;
        job.order_id = 123L;
        job.module_code = PrintModuleCode.GRAB;
        job.status = PrintJobStatus.PENDING;
        when(printJobService.createPendingJob(1L, 1L, 123L, null, null, PrintModuleCode.GRAB, PrintModuleCode.GRAB, null,
            "{\"source\":\"ORDER_SUBMIT\",\"module_code\":\"GRAB\",\"store_id\":1,\"order_id\":123}")).thenReturn(job);

        service.dispatchAfterCommit(PrintModuleCode.GRAB, 1L, 123L);

        verify(printJobService).markCancelled(job, null, "PRINTING_DISABLED", "Store printing is disabled");
        verify(printerAssignmentRepository, never()).findByStoreIdAndModuleCode(1L, PrintModuleCode.GRAB);
        verify(printerTransport, never()).print(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }
}
