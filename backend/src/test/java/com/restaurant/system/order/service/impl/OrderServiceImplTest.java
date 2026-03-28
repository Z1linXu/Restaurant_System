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
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.dto.FrontdeskBeverageItemResponse;
import com.restaurant.system.order.dto.FrontdeskOrderBoardResponse;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.dto.UpdateDraftOrderItemQuantityRequest;
import com.restaurant.system.order.entity.FrontdeskBeverageItem;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.order.repository.FrontdeskBeverageItemRepository;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
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
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock
    private StationRepository stationRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private RealtimeEventPublisher realtimeEventPublisher;

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

    private Store store;
    private MenuCategory menuCategory;
    private MenuItem menuItem;
    private Station station;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(
            orderRepository,
            orderItemRepository,
            orderItemOptionRepository,
            frontdeskBeverageItemRepository,
            menuItemRepository,
            menuItemOptionRepository,
            menuCategoryRepository,
            menuItemBomRepository,
            menuItemOptionBomRepository,
            kitchenTaskRepository,
            inventoryItemRepository,
            inventoryTransactionRepository,
            stationRepository,
            storeRepository,
            realtimeEventPublisher
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
        assertEquals(new BigDecimal("12.50"), draftOrder.total_amount);

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
    void submittedOrderCanModifyPendingItemsButNotReadyItems() {
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

        OrderResponse modifiedOrder = orderService.updateDraftOrderItemQuantity(
            submittedOrder.id,
            submittedOrder.items.get(0).id,
            quantityRequest
        );
        assertEquals("preparing", modifiedOrder.status);
        assertTrue(Boolean.TRUE.equals(modifiedOrder.is_modified_after_submit));
        assertTrue(Boolean.TRUE.equals(modifiedOrder.items.get(0).is_modified_after_submit));
        assertEquals(2, modifiedOrder.items.get(0).quantity);
        assertEquals(2, kitchenTaskRepository.findAllByOrderId(modifiedOrder.id).get(0).quantity);

        kitchenService.markReadyForPickup(kitchenTaskRepository.findAllByOrderId(modifiedOrder.id).get(0).id);
        assertThrows(
            RuntimeException.class,
            () -> orderService.updateDraftOrderItemQuantity(modifiedOrder.id, modifiedOrder.items.get(0).id, quantityRequest)
        );

        CreateOrderItemRequest newItemRequest = new CreateOrderItemRequest();
        newItemRequest.menu_item_id = menuItem.id;
        newItemRequest.quantity = 1;

        OrderResponse reopenedOrder = orderService.addDraftOrderItem(modifiedOrder.id, newItemRequest);
        assertEquals("preparing", reopenedOrder.status);
        assertEquals(2, reopenedOrder.kitchen_items.size());
    }
}
