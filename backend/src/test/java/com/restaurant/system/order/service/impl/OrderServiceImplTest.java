package com.restaurant.system.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.restaurant.system.inventory.entity.InventoryTransaction;
import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.inventory.repository.InventoryTransactionRepository;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.kitchen.service.impl.KitchenServiceImpl;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemBomRepository;
import com.restaurant.system.menu.repository.MenuItemOptionBomRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.dto.CreateOrderUpdateRequest;
import com.restaurant.system.order.dto.FrontdeskBeverageItemResponse;
import com.restaurant.system.order.dto.FrontdeskOrderBoardResponse;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.dto.UpdateDraftOrderItemQuantityRequest;
import com.restaurant.system.order.entity.FrontdeskBeverageItem;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.order.entity.OrderUpdateBatch;
import com.restaurant.system.order.repository.FrontdeskBeverageItemRepository;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.repository.OrderUpdateBatchRepository;
import com.restaurant.system.production.repository.ProductionTaskRepository;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderUpdateBatchRepository orderUpdateBatchRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private OrderItemOptionRepository orderItemOptionRepository;
    @Mock
    private FrontdeskBeverageItemRepository frontdeskBeverageItemRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private MenuItemOptionRepository menuItemOptionRepository;
    @Mock
    private MenuCategoryRepository menuCategoryRepository;
    @Mock
    private MenuItemBomRepository menuItemBomRepository;
    @Mock
    private MenuItemOptionBomRepository menuItemOptionBomRepository;
    @Mock
    private KitchenTaskRepository kitchenTaskRepository;
    @Mock
    private ProductionTaskRepository productionTaskRepository;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;
    @Mock
    private PrintDispatcherService printDispatcherService;

    private OrderServiceImpl orderService;
    private KitchenServiceImpl kitchenService;
    private FrontdeskBeverageServiceImpl frontdeskBeverageService;

    private final Map<Long, Order> orders = new HashMap<>();
    private final Map<Long, OrderItem> orderItems = new HashMap<>();
    private final Map<Long, OrderItemOption> orderItemOptions = new HashMap<>();
    private final Map<Long, KitchenTask> kitchenTasks = new HashMap<>();
    private final Map<Long, FrontdeskBeverageItem> beverageItems = new HashMap<>();
    private final AtomicLong orderIdSeq = new AtomicLong(1);
    private final AtomicLong orderItemIdSeq = new AtomicLong(1);
    private final AtomicLong orderItemOptionIdSeq = new AtomicLong(1);
    private final AtomicLong kitchenTaskIdSeq = new AtomicLong(1);
    private final AtomicLong beverageItemIdSeq = new AtomicLong(1);
    private final AtomicLong orderUpdateBatchIdSeq = new AtomicLong(1);
    private final Map<Long, OrderUpdateBatch> orderUpdateBatches = new HashMap<>();

    private Store store;
    private MenuCategory menuCategory;
    private MenuItem menuItem;
    private Station station;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
            orderRepository,
            orderUpdateBatchRepository,
            orderItemRepository,
            orderItemOptionRepository,
            frontdeskBeverageItemRepository,
            menuItemRepository,
            menuItemOptionRepository,
            menuCategoryRepository,
            menuItemBomRepository,
            menuItemOptionBomRepository,
            kitchenTaskRepository,
            productionTaskRepository,
            inventoryItemRepository,
            inventoryTransactionRepository,
            stationRepository,
            storeRepository,
            realtimeEventPublisher,
            printDispatcherService
        );
        kitchenService = new KitchenServiceImpl(kitchenTaskRepository, orderRepository, realtimeEventPublisher);
        frontdeskBeverageService = new FrontdeskBeverageServiceImpl(
            frontdeskBeverageItemRepository,
            orderRepository,
            realtimeEventPublisher
        );

        store = new Store();
        store.id = 1L;
        store.enable_bar_kitchen_tasks = false;

        menuCategory = new MenuCategory();
        menuCategory.id = 10L;
        menuCategory.store_id = 1L;
        menuCategory.code = "SOUP_NOODLE";

        station = new Station();
        station.id = 30L;
        station.store_id = 1L;
        station.code = "NOODLE";
        station.is_active = true;

        menuItem = new MenuItem();
        menuItem.id = 20L;
        menuItem.store_id = 1L;
        menuItem.category_id = menuCategory.id;
        menuItem.station_id = station.id;
        menuItem.name_zh = "牛肉面";
        menuItem.name_en = "Beef Noodle";
        menuItem.sku = "traditional_beef_noodle";
        menuItem.base_price = new BigDecimal("12.50");

        when(storeRepository.findById(store.id)).thenAnswer(invocation -> Optional.of(store));
        when(menuCategoryRepository.findById(menuCategory.id)).thenAnswer(invocation -> Optional.of(menuCategory));
        when(menuItemRepository.findById(menuItem.id)).thenAnswer(invocation -> Optional.of(menuItem));
        when(menuItemOptionRepository.findById(anyLong())).thenAnswer(invocation -> Optional.<MenuItemOption>empty());
        when(stationRepository.findActiveStationByIdAndStoreId(station.id, store.id)).thenReturn(station);

        when(menuItemBomRepository.findAllByMenuItemId(anyLong())).thenReturn(List.of());
        when(menuItemOptionBomRepository.findAllByMenuItemOptionId(anyLong())).thenReturn(List.of());
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.id == null) {
                order.id = orderIdSeq.getAndIncrement();
            }
            orders.put(order.id, order);
            return order;
        });
        when(orderRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(orders.get(invocation.getArgument(0))));
        when(orderRepository.findExistingById(anyLong())).thenAnswer(invocation -> orders.get(invocation.getArgument(0)));
        when(orderRepository.findByIdForUpdate(anyLong())).thenAnswer(invocation -> orders.get(invocation.getArgument(0)));
        when(orderRepository.findActiveOperationalOrders(anyLong())).thenAnswer(invocation -> orders.values().stream()
            .filter(order -> store.id.equals(order.store_id))
            .toList());
        when(orderRepository.findAllByStoreId(anyLong())).thenAnswer(invocation -> orders.values().stream()
            .filter(order -> store.id.equals(order.store_id))
            .toList());

        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem orderItem = invocation.getArgument(0);
            if (orderItem.id == null) {
                orderItem.id = orderItemIdSeq.getAndIncrement();
            }
            orderItems.put(orderItem.id, orderItem);
            return orderItem;
        });
        when(orderItemRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(orderItems.get(invocation.getArgument(0))));
        when(orderItemRepository.findExistingById(anyLong())).thenAnswer(invocation -> orderItems.get(invocation.getArgument(0)));
        when(orderItemRepository.findAllByOrderId(anyLong())).thenAnswer(invocation -> orderItems.values().stream()
            .filter(item -> invocation.getArgument(0).equals(item.order_id))
            .sorted((left, right) -> left.id.compareTo(right.id))
            .toList());
        when(orderItemRepository.findAllByOrderIds(anyList())).thenAnswer(invocation -> {
            List<Long> orderIds = invocation.getArgument(0);
            return orderItems.values().stream().filter(item -> orderIds.contains(item.order_id)).toList();
        });

        when(orderItemOptionRepository.save(any(OrderItemOption.class))).thenAnswer(invocation -> {
            OrderItemOption option = invocation.getArgument(0);
            if (option.id == null) {
                option.id = orderItemOptionIdSeq.getAndIncrement();
            }
            orderItemOptions.put(option.id, option);
            return option;
        });
        when(orderItemOptionRepository.findAllByOrderItemIds(anyList())).thenAnswer(invocation -> {
            List<Long> orderItemIds = invocation.getArgument(0);
            return orderItemOptions.values().stream()
                .filter(option -> orderItemIds.contains(option.order_item_id))
                .sorted((left, right) -> left.id.compareTo(right.id))
                .toList();
        });
        when(kitchenTaskRepository.save(any(KitchenTask.class))).thenAnswer(invocation -> {
            KitchenTask task = invocation.getArgument(0);
            if (task.id == null) {
                task.id = kitchenTaskIdSeq.getAndIncrement();
            }
            kitchenTasks.put(task.id, task);
            return task;
        });
        when(kitchenTaskRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<KitchenTask> tasks = invocation.getArgument(0);
            List<KitchenTask> saved = new ArrayList<>();
            for (KitchenTask task : tasks) {
                if (task.id == null) {
                    task.id = kitchenTaskIdSeq.getAndIncrement();
                }
                kitchenTasks.put(task.id, task);
                saved.add(task);
            }
            return saved;
        });
        when(kitchenTaskRepository.findById(anyLong())).thenAnswer(invocation -> Optional.ofNullable(kitchenTasks.get(invocation.getArgument(0))));
        when(kitchenTaskRepository.findAllByOrderId(anyLong())).thenAnswer(invocation -> kitchenTasks.values().stream()
            .filter(task -> invocation.getArgument(0).equals(task.order_id))
            .sorted((left, right) -> left.id.compareTo(right.id))
            .toList());
        when(kitchenTaskRepository.findAllByOrderIds(anyList())).thenAnswer(invocation -> {
            List<Long> orderIds = invocation.getArgument(0);
            return kitchenTasks.values().stream()
                .filter(task -> orderIds.contains(task.order_id))
                .toList();
        });
        when(kitchenTaskRepository.countOpenTasksByOrderId(anyLong())).thenAnswer(invocation -> kitchenTasks.values().stream()
            .filter(task -> invocation.getArgument(0).equals(task.order_id))
            .filter(task -> "pending".equals(task.status) || "in_progress".equals(task.status))
            .count());
        when(frontdeskBeverageItemRepository.save(any(FrontdeskBeverageItem.class))).thenAnswer(invocation -> {
            FrontdeskBeverageItem item = invocation.getArgument(0);
            if (item.id == null) {
                item.id = beverageItemIdSeq.getAndIncrement();
            }
            beverageItems.put(item.id, item);
            return item;
        });
        when(frontdeskBeverageItemRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<FrontdeskBeverageItem> items = invocation.getArgument(0);
            List<FrontdeskBeverageItem> saved = new ArrayList<>();
            for (FrontdeskBeverageItem item : items) {
                if (item.id == null) {
                    item.id = beverageItemIdSeq.getAndIncrement();
                }
                beverageItems.put(item.id, item);
                saved.add(item);
            }
            return saved;
        });
        when(frontdeskBeverageItemRepository.findAllByOrderId(anyLong())).thenAnswer(invocation -> beverageItems.values().stream()
            .filter(item -> invocation.getArgument(0).equals(item.order_id))
            .sorted((left, right) -> left.id.compareTo(right.id))
            .toList());
        when(frontdeskBeverageItemRepository.findAllByOrderIds(anyList())).thenAnswer(invocation -> {
            List<Long> orderIds = invocation.getArgument(0);
            return beverageItems.values().stream().filter(item -> orderIds.contains(item.order_id)).toList();
        });
        when(orderUpdateBatchRepository.save(any(OrderUpdateBatch.class))).thenAnswer(invocation -> {
            OrderUpdateBatch batch = invocation.getArgument(0);
            if (batch.id == null) {
                batch.id = orderUpdateBatchIdSeq.getAndIncrement();
            }
            orderUpdateBatches.put(batch.id, batch);
            return batch;
        });
        when(orderUpdateBatchRepository.findByOrderIdAndIdempotencyKey(anyLong(), org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer(invocation -> orderUpdateBatches.values().stream()
                .filter(batch -> invocation.getArgument(0).equals(batch.order_id))
                .filter(batch -> invocation.getArgument(1).equals(batch.idempotency_key))
                .findFirst());
        when(frontdeskBeverageItemRepository.findByOrderItemId(anyLong())).thenAnswer(invocation -> beverageItems.values().stream()
            .filter(item -> invocation.getArgument(0).equals(item.order_item_id))
            .findFirst()
            .orElse(null));
        when(frontdeskBeverageItemRepository.findAllByStoreIdAndStatuses(anyLong(), anyList())).thenAnswer(invocation -> {
            Long storeId = invocation.getArgument(0);
            List<String> statuses = invocation.getArgument(1);
            return beverageItems.values().stream()
                .filter(item -> storeId.equals(item.store_id))
                .filter(item -> statuses.contains(item.status))
                .toList();
        });
    }

    @Test
    void draftSubmitReadyCompleteLifecycleWorks() {
        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.menu_item_id = menuItem.id;
        itemRequest.quantity = 1;
        itemRequest.notes = "less spicy";

        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.id;
        request.created_by = 1L;
        request.order_type = "dine_in";
        request.table_no = "T7";
        request.items = List.of(itemRequest);

        OrderResponse draftOrder = orderService.createOrder(request);
        assertEquals("draft", draftOrder.status);
        assertEquals(1, draftOrder.items.size());
        assertEquals(new BigDecimal("12.50"), draftOrder.subtotal_amount);
        assertEquals(new BigDecimal("14.37"), draftOrder.total_amount);

        OrderResponse submittedOrder = orderService.submitOrder(draftOrder.id);
        assertEquals("preparing", submittedOrder.status);

        List<KitchenTask> tasks = kitchenTaskRepository.findAllByOrderId(submittedOrder.id);
        assertEquals(1, tasks.size());
        assertEquals("NOODLE", tasks.get(0).station_code);

        kitchenService.markReadyForPickup(tasks.get(0).id);

        OrderResponse readyOrder = orderService.getOrderDetail(submittedOrder.id);
        assertEquals("ready", readyOrder.status);
        assertEquals("ready_for_pickup", readyOrder.kitchen_items.get(0).task_status);
        assertNotNull(readyOrder.kitchen_items.get(0).ready_for_pickup_at);
        assertTrue(readyOrder.beverage_items.isEmpty());
        assertFalse(readyOrder.kitchen_items.isEmpty());

        OrderResponse completedOrder = orderService.completeOrder(readyOrder.id);
        assertEquals("completed", completedOrder.status);
        assertNotNull(completedOrder.completed_at);
    }

    @Test
    void submitDispatchesHotKitchenWhenPrintableContentExists() {
        when(printDispatcherService.hasPrintableContent(
            org.mockito.ArgumentMatchers.eq(PrintModuleCode.HOT_KITCHEN),
            org.mockito.ArgumentMatchers.eq(store.id),
            anyLong()
        )).thenReturn(true);

        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.menu_item_id = menuItem.id;
        itemRequest.quantity = 1;

        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.id;
        request.created_by = 1L;
        request.order_type = "dine_in";
        request.table_no = "T7";
        request.items = List.of(itemRequest);

        OrderResponse submittedOrder = orderService.submitOrder(orderService.createOrder(request).id);

        org.mockito.Mockito.verify(printDispatcherService).dispatchAfterCommit(PrintModuleCode.GRAB, store.id, submittedOrder.id);
        org.mockito.Mockito.verify(printDispatcherService).dispatchAfterCommit(PrintModuleCode.FRONTDESK_RECEIPT, store.id, submittedOrder.id);
        org.mockito.Mockito.verify(printDispatcherService).dispatchAfterCommit(PrintModuleCode.HOT_KITCHEN, store.id, submittedOrder.id);
    }

    @Test
    void beverageItemsUseSeparateFrontdeskWorkflow() {
        MenuCategory drinkCategory = new MenuCategory();
        drinkCategory.id = 11L;
        drinkCategory.store_id = 1L;
        drinkCategory.code = "DRINK";

        MenuItem drinkItem = new MenuItem();
        drinkItem.id = 21L;
        drinkItem.store_id = 1L;
        drinkItem.category_id = drinkCategory.id;
        drinkItem.name_zh = "可乐";
        drinkItem.name_en = "Coke";
        drinkItem.base_price = new BigDecimal("3.00");

        when(menuCategoryRepository.findById(drinkCategory.id)).thenAnswer(invocation -> Optional.of(drinkCategory));
        when(menuItemRepository.findById(drinkItem.id)).thenAnswer(invocation -> Optional.of(drinkItem));

        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.menu_item_id = drinkItem.id;
        itemRequest.quantity = 2;
        itemRequest.notes = "less ice";

        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.id;
        request.created_by = 1L;
        request.order_type = "pickup";
        request.pickup_no = "P8";
        request.items = List.of(itemRequest);

        OrderResponse submittedOrder = orderService.submitOrder(orderService.createOrder(request).id);
        assertEquals("ready", submittedOrder.status);
        assertTrue(kitchenTaskRepository.findAllByOrderId(submittedOrder.id).isEmpty());

        OrderResponse detail = orderService.getOrderDetail(submittedOrder.id);
        assertEquals(1, detail.beverage_items.size());
        assertEquals("pending", detail.beverage_items.get(0).beverage_status);

        FrontdeskBeverageItemResponse preparing = frontdeskBeverageService.startBeverage(detail.beverage_items.get(0).id);
        assertEquals("preparing", preparing.beverage_status);

        FrontdeskBeverageItemResponse ready = frontdeskBeverageService.markBeverageReady(detail.beverage_items.get(0).id);
        assertEquals("ready", ready.beverage_status);

        FrontdeskBeverageItemResponse served = frontdeskBeverageService.markBeverageServed(detail.beverage_items.get(0).id);
        assertEquals("served", served.beverage_status);

        OrderResponse refreshed = orderService.getOrderDetail(submittedOrder.id);
        assertEquals("served", refreshed.beverage_items.get(0).beverage_status);
        assertNotNull(refreshed.beverage_items.get(0).beverage_served_at);
    }

    @Test
    void frontdeskBoardAndHistoryProvideOperationalCounts() {
        MenuCategory drinkCategory = new MenuCategory();
        drinkCategory.id = 11L;
        drinkCategory.store_id = 1L;
        drinkCategory.code = "DRINK";

        MenuItem drinkItem = new MenuItem();
        drinkItem.id = 21L;
        drinkItem.store_id = 1L;
        drinkItem.category_id = drinkCategory.id;
        drinkItem.name_zh = "可乐";
        drinkItem.name_en = "Coke";
        drinkItem.base_price = new BigDecimal("3.00");

        when(menuCategoryRepository.findById(drinkCategory.id)).thenAnswer(invocation -> Optional.of(drinkCategory));
        when(menuItemRepository.findById(drinkItem.id)).thenAnswer(invocation -> Optional.of(drinkItem));

        CreateOrderItemRequest noodleRequest = new CreateOrderItemRequest();
        noodleRequest.menu_item_id = menuItem.id;
        noodleRequest.quantity = 1;

        CreateOrderRequest dineIn = new CreateOrderRequest();
        dineIn.store_id = store.id;
        dineIn.created_by = 1L;
        dineIn.order_type = "dine_in";
        dineIn.table_no = "T9";
        dineIn.items = List.of(noodleRequest);

        OrderResponse kitchenOrder = orderService.submitOrder(orderService.createOrder(dineIn).id);
        kitchenService.markReadyForPickup(kitchenTaskRepository.findAllByOrderId(kitchenOrder.id).get(0).id);

        CreateOrderItemRequest drinkRequest = new CreateOrderItemRequest();
        drinkRequest.menu_item_id = drinkItem.id;
        drinkRequest.quantity = 1;

        CreateOrderRequest pickup = new CreateOrderRequest();
        pickup.store_id = store.id;
        pickup.created_by = 1L;
        pickup.order_type = "pickup";
        pickup.pickup_no = "P22";
        pickup.items = List.of(drinkRequest);

        OrderResponse beverageOrder = orderService.submitOrder(orderService.createOrder(pickup).id);
        frontdeskBeverageService.startBeverage(beverageOrder.items.get(0).id);

        List<FrontdeskOrderBoardResponse> activeBoard = orderService.getFrontdeskOrderBoard(
            store.id,
            null,
            null,
            null,
            null,
            null
        );
        assertEquals(2, activeBoard.size());

        FrontdeskOrderBoardResponse readyKitchenOrder = activeBoard.stream()
            .filter(row -> row.order_id.equals(kitchenOrder.id))
            .findFirst()
            .orElseThrow();
        assertEquals(1, readyKitchenOrder.total_item_count);
        assertEquals(1, readyKitchenOrder.ready_item_count);
        assertEquals(0, readyKitchenOrder.kitchen_pending_count);
        assertEquals(0, readyKitchenOrder.beverage_pending_count);

        FrontdeskOrderBoardResponse preparingDrinkOrder = activeBoard.stream()
            .filter(row -> row.order_id.equals(beverageOrder.id))
            .findFirst()
            .orElseThrow();
        assertEquals(1, preparingDrinkOrder.total_item_count);
        assertEquals(0, preparingDrinkOrder.ready_item_count);
        assertEquals(0, preparingDrinkOrder.kitchen_pending_count);
        assertEquals(1, preparingDrinkOrder.beverage_pending_count);

        orderService.completeOrder(kitchenOrder.id);
        orderService.cancelOrder(orderService.createOrder(dineIn).id);

        List<FrontdeskOrderBoardResponse> history = orderService.getFrontdeskOrderHistory(
            store.id,
            null,
            null,
            null,
            null,
            null,
            null
        );
        assertTrue(history.stream().anyMatch(row -> row.order_status.equals("completed")));
        assertTrue(history.stream().anyMatch(row -> row.order_status.equals("cancelled")));

        List<FrontdeskOrderBoardResponse> searchByPickup = orderService.getFrontdeskOrderBoard(
            store.id,
            List.of("all"),
            "pickup",
            null,
            "P22",
            "P22"
        );
        assertEquals(1, searchByPickup.size());
        assertEquals(beverageOrder.id, searchByPickup.get(0).order_id);
    }

    @Test
    void submittedOrderLocksOldItemsAndUsesIdempotentUpdateBatchForNewItems() {
        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.menu_item_id = menuItem.id;
        itemRequest.quantity = 1;

        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.id;
        request.created_by = 1L;
        request.order_type = "dine_in";
        request.table_no = "T5";
        request.items = List.of(itemRequest);

        OrderResponse submittedOrder = orderService.submitOrder(orderService.createOrder(request).id);
        UpdateDraftOrderItemQuantityRequest quantityRequest = new UpdateDraftOrderItemQuantityRequest();
        quantityRequest.quantity = 2;

        assertThrows(
            com.restaurant.system.common.exception.BusinessException.class,
            () -> orderService.updateDraftOrderItemQuantity(submittedOrder.id, submittedOrder.items.get(0).id, quantityRequest)
        );

        CreateOrderItemRequest newItemRequest = new CreateOrderItemRequest();
        newItemRequest.menu_item_id = menuItem.id;
        newItemRequest.quantity = 1;

        CreateOrderUpdateRequest updateRequest = new CreateOrderUpdateRequest();
        updateRequest.idempotency_key = "update-T5-1";
        updateRequest.items = List.of(newItemRequest);

        var firstResult = orderService.createOrderUpdate(submittedOrder.id, updateRequest, 1L);
        var repeatedResult = orderService.createOrderUpdate(submittedOrder.id, updateRequest, 1L);

        assertFalse(firstResult.already_processed);
        assertTrue(repeatedResult.already_processed);
        assertEquals(firstResult.update_batch_id, repeatedResult.update_batch_id);
        assertEquals(2, firstResult.order.items.size());
        assertEquals(firstResult.update_batch_id, firstResult.order.items.get(1).order_update_batch_id);
        assertEquals(2, kitchenTaskRepository.findAllByOrderId(submittedOrder.id).size());
        org.mockito.Mockito.verify(printDispatcherService, org.mockito.Mockito.times(1))
            .dispatchOrderUpdateAfterCommit("GRAB", store.id, submittedOrder.id, firstResult.update_batch_id);
        org.mockito.Mockito.verify(printDispatcherService, org.mockito.Mockito.times(1))
            .dispatchOrderUpdateAfterCommit("FRONTDESK_RECEIPT", store.id, submittedOrder.id, firstResult.update_batch_id);
    }

    @Test
    void updateDispatchesHotKitchenWhenBatchHasPrintableContent() {
        when(printDispatcherService.hasPrintableUpdateContent(
            org.mockito.ArgumentMatchers.eq(PrintModuleCode.HOT_KITCHEN),
            org.mockito.ArgumentMatchers.eq(store.id),
            anyLong(),
            anyLong()
        )).thenReturn(true);

        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.menu_item_id = menuItem.id;
        itemRequest.quantity = 1;

        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.id;
        request.created_by = 1L;
        request.order_type = "dine_in";
        request.table_no = "T10";
        request.items = List.of(itemRequest);

        OrderResponse submittedOrder = orderService.submitOrder(orderService.createOrder(request).id);
        CreateOrderItemRequest newItemRequest = new CreateOrderItemRequest();
        newItemRequest.menu_item_id = menuItem.id;
        newItemRequest.quantity = 1;

        CreateOrderUpdateRequest updateRequest = new CreateOrderUpdateRequest();
        updateRequest.idempotency_key = "update-T10-1";
        updateRequest.items = List.of(newItemRequest);

        var result = orderService.createOrderUpdate(submittedOrder.id, updateRequest, 1L);

        org.mockito.Mockito.verify(printDispatcherService).dispatchOrderUpdateAfterCommit(
            PrintModuleCode.HOT_KITCHEN,
            store.id,
            submittedOrder.id,
            result.update_batch_id
        );
    }

    @Test
    void comboEggAndExtraEggRemainVisibleInKitchenInstructions() {
        MenuItemOption comboEgg = menuOption(101L, "addon", "combo_tea_egg", "COMBO_EGG", "套餐卤蛋", "Combo Tea Egg", BigDecimal.ZERO);
        MenuItemOption extraEgg = menuOption(102L, "addon", "tea_egg", "ADD_ON", "加蛋", "Extra Egg", new BigDecimal("1.99"));
        Map<Long, MenuItemOption> optionsById = Map.of(comboEgg.id, comboEgg, extraEgg.id, extraEgg);
        when(menuItemOptionRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long optionId = invocation.getArgument(0);
            return Optional.ofNullable(optionsById.get(optionId));
        });

        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.menu_item_id = menuItem.id;
        itemRequest.quantity = 1;
        itemRequest.options = List.of(optionRequest(comboEgg.id, 1), optionRequest(extraEgg.id, 1));

        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.id;
        request.created_by = 1L;
        request.order_type = "dine_in";
        request.table_no = "T8";
        request.items = List.of(itemRequest);

        OrderResponse submittedOrder = orderService.submitOrder(orderService.createOrder(request).id);
        List<KitchenTask> tasks = kitchenTaskRepository.findAllByOrderId(submittedOrder.id);

        assertEquals(1, tasks.size());
        assertTrue(tasks.get(0).special_instructions_snapshot.contains("+蛋x2"));
        assertFalse(tasks.get(0).special_instructions_snapshot.contains("+蛋 +蛋"));
    }

    @Test
    void extraBokChoyKeepsFullKitchenInstructionName() {
        MenuItemOption bokChoy = menuOption(103L, "addon", "bok_choy", "ADD_ON", "加上海青", "Extra Bok Choy", new BigDecimal("3.00"));
        when(menuItemOptionRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long optionId = invocation.getArgument(0);
            return bokChoy.id.equals(optionId) ? Optional.of(bokChoy) : Optional.empty();
        });

        CreateOrderItemRequest itemRequest = new CreateOrderItemRequest();
        itemRequest.menu_item_id = menuItem.id;
        itemRequest.quantity = 1;
        itemRequest.options = List.of(optionRequest(bokChoy.id, 1));

        CreateOrderRequest request = new CreateOrderRequest();
        request.store_id = store.id;
        request.created_by = 1L;
        request.order_type = "dine_in";
        request.table_no = "T9";
        request.items = List.of(itemRequest);

        OrderResponse submittedOrder = orderService.submitOrder(orderService.createOrder(request).id);
        List<KitchenTask> tasks = kitchenTaskRepository.findAllByOrderId(submittedOrder.id);

        assertEquals(1, tasks.size());
        assertTrue(tasks.get(0).special_instructions_snapshot.contains("加上海青"));
        assertFalse(tasks.get(0).special_instructions_snapshot.contains("+青"));
        assertFalse(tasks.get(0).special_instructions_snapshot.contains("加青"));
    }

    private MenuItemOption menuOption(
        Long id,
        String optionType,
        String optionCode,
        String optionGroup,
        String nameZh,
        String nameEn,
        BigDecimal priceDelta
    ) {
        MenuItemOption option = new MenuItemOption();
        option.id = id;
        option.menu_item_id = menuItem.id;
        option.option_type = optionType;
        option.option_code = optionCode;
        option.option_group = optionGroup;
        option.name_zh = nameZh;
        option.name_en = nameEn;
        option.price_delta = priceDelta;
        option.is_active = true;
        return option;
    }

    private CreateOrderItemOptionRequest optionRequest(Long optionId, int quantity) {
        CreateOrderItemOptionRequest request = new CreateOrderItemOptionRequest();
        request.option_id = optionId;
        request.quantity = quantity;
        return request;
    }
}
