package com.restaurant.system.order.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import com.restaurant.system.common.realtime.RealtimeTopics;
import com.restaurant.system.common.realtime.RealtimeUpdateMessage;
import com.restaurant.system.inventory.entity.InventoryItem;
import com.restaurant.system.inventory.entity.InventoryTransaction;
import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.inventory.repository.InventoryTransactionRepository;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.enums.KitchenTaskStatus;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.menu.entity.MenuCategory;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemBom;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.entity.MenuItemOptionBom;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemBomRepository;
import com.restaurant.system.menu.repository.MenuItemOptionBomRepository;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.dto.FrontdeskOrderBoardResponse;
import com.restaurant.system.order.dto.OrderItemOptionResponse;
import com.restaurant.system.order.dto.OrderItemResponse;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.entity.FrontdeskBeverageItem;
import com.restaurant.system.order.dto.UpdateDraftOrderHeaderRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemQuantityRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemRequest;
import com.restaurant.system.order.repository.FrontdeskBeverageItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    private static final String ORDER_STATUS_DRAFT = "draft";
    private static final String ORDER_STATUS_SUBMITTED = "submitted";
    private static final String ORDER_STATUS_PREPARING = "preparing";
    private static final String ORDER_STATUS_READY = "ready";
    private static final String ORDER_STATUS_COMPLETED = "completed";
    private static final String ORDER_STATUS_CANCELLED = "cancelled";
    private static final String ORDER_STATUS_PICKED_UP = "picked_up";
    private static final String ORDER_ITEM_STATUS_CANCELLED = "cancelled";
    private static final String INVENTORY_TXN_TYPE_CONSUME = "consume";
    private static final String INVENTORY_SOURCE_TYPE_ORDER = "order";
    private static final String COMBO_ROLE_MAIN = "main";
    private static final String COMBO_ROLE_SIDE = "combo_side";
    private static final String COMBO_ROLE_EGG = "combo_egg";
    private static final String COMBO_ROLE_STANDALONE = "standalone";
    private static final String CATEGORY_CODE_DRINK = "DRINK";
    private static final String CATEGORY_CODE_ALCOHOL = "ALCOHOL";
    private static final String CATEGORY_CODE_MILK_TEA = "MILK_TEA";
    private static final String OPTION_TYPE_NOODLE_TYPE = "noodle_type";
    private static final String OPTION_TYPE_SIZE = "size";
    private static final String OPTION_TYPE_ADDON = "addon";
    private static final String OPTION_TYPE_REMOVE = "remove";
    private static final String OPTION_TYPE_SOUP_BASE = "soup_base";
    private static final String BEVERAGE_STATUS_PENDING = "pending";
    private static final String BEVERAGE_STATUS_PREPARING = "preparing";
    private static final String BEVERAGE_STATUS_READY = "ready";
    private static final String BEVERAGE_STATUS_SERVED = "served";
    private static final String BEVERAGE_STATUS_CANCELLED = "cancelled";
    private static final int DEFAULT_FRONTDESK_HISTORY_LIMIT = 20;
    private static final Set<String> ALLOWED_COMBO_ROLES = Set.of(
        COMBO_ROLE_MAIN,
        COMBO_ROLE_SIDE,
        COMBO_ROLE_EGG,
        COMBO_ROLE_STANDALONE
    );
    private static final Set<String> KITCHEN_RELEVANT_OPTION_TYPES = Set.of(
        OPTION_TYPE_NOODLE_TYPE,
        OPTION_TYPE_SIZE,
        OPTION_TYPE_ADDON,
        OPTION_TYPE_REMOVE,
        OPTION_TYPE_SOUP_BASE
    );
    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of(
        ORDER_STATUS_SUBMITTED,
        ORDER_STATUS_PREPARING,
        ORDER_STATUS_READY
    );
    private static final Set<String> MODIFIABLE_AFTER_SUBMIT_ORDER_STATUSES = Set.of(
        ORDER_STATUS_SUBMITTED,
        ORDER_STATUS_PREPARING,
        ORDER_STATUS_READY
    );
    private static final Set<String> DEFAULT_HISTORY_ORDER_STATUSES = Set.of(
        ORDER_STATUS_COMPLETED,
        ORDER_STATUS_CANCELLED
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemOptionRepository orderItemOptionRepository;
    private final FrontdeskBeverageItemRepository frontdeskBeverageItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemBomRepository menuItemBomRepository;
    private final MenuItemOptionBomRepository menuItemOptionBomRepository;
    private final KitchenTaskRepository kitchenTaskRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final StationRepository stationRepository;
    private final StoreRepository storeRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public OrderServiceImpl(
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderItemOptionRepository orderItemOptionRepository,
        FrontdeskBeverageItemRepository frontdeskBeverageItemRepository,
        MenuItemRepository menuItemRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        MenuCategoryRepository menuCategoryRepository,
        MenuItemBomRepository menuItemBomRepository,
        MenuItemOptionBomRepository menuItemOptionBomRepository,
        KitchenTaskRepository kitchenTaskRepository,
        InventoryItemRepository inventoryItemRepository,
        InventoryTransactionRepository inventoryTransactionRepository,
        StationRepository stationRepository,
        StoreRepository storeRepository,
        RealtimeEventPublisher realtimeEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemOptionRepository = orderItemOptionRepository;
        this.frontdeskBeverageItemRepository = frontdeskBeverageItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemBomRepository = menuItemBomRepository;
        this.menuItemOptionBomRepository = menuItemOptionBomRepository;
        this.kitchenTaskRepository = kitchenTaskRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.stationRepository = stationRepository;
        this.storeRepository = storeRepository;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.store_id = request.store_id;
        order.created_by = request.created_by;
        order.order_type = request.order_type;
        order.table_no = request.table_no;
        order.pickup_no = request.pickup_no;
        order.order_no = generateOrderNo();
        order.status = ORDER_STATUS_DRAFT;
        order.subtotal_amount = BigDecimal.ZERO;
        order.discount_amount = BigDecimal.ZERO;
        order.total_amount = BigDecimal.ZERO;
        order.is_modified_after_submit = false;
        order.created_at = now;
        order.updated_at = now;
        Order savedOrder = orderRepository.save(order);

        for (CreateOrderItemRequest itemRequest : normalizeItemRequests(request.items)) {
            addDraftOrderItemInternal(savedOrder, itemRequest, now);
        }

        recalculateOrderAmounts(savedOrder, now);
        publishOrderEvent("order.created", savedOrder, null, null, null);
        return loadOrderResponse(savedOrder.id);
    }

    @Override
    public OrderResponse getOrderDetail(Long id) {
        return loadOrderResponse(id);
    }

    @Override
    @Transactional
    public OrderResponse updateDraftOrderHeader(Long id, UpdateDraftOrderHeaderRequest request) {
        Order order = requireDraftOrder(id);
        if (request.order_type != null) {
            if (request.order_type.isBlank()) {
                throw new BusinessException("order_type cannot be blank");
            }
            order.order_type = request.order_type;
        }
        if (request.table_no != null) {
            order.table_no = request.table_no;
        }
        if (request.pickup_no != null) {
            order.pickup_no = request.pickup_no;
        }
        order.updated_at = LocalDateTime.now();
        orderRepository.save(order);
        return loadOrderResponse(order.id);
    }

    @Override
    @Transactional
    public OrderResponse addDraftOrderItem(Long id, CreateOrderItemRequest request) {
        Order order = requireItemEditableOrder(id);
        LocalDateTime now = LocalDateTime.now();
        OrderItem orderItem = addDraftOrderItemInternal(order, request, now);
        if (isSubmittedOrder(order)) {
            markSubmittedModification(order, now);
            markSubmittedModification(orderItem, now);
            synchronizeOperationalRecordForOrderItem(order, orderItem, now);
            recalculateOrderStatusAfterSubmittedModification(order, now);
            publishOrderEvent("order.modified_after_submit", order, orderItem.id, null, null);
        }
        recalculateOrderAmounts(order, now);
        return loadOrderResponse(order.id);
    }

    @Override
    @Transactional
    public OrderResponse updateDraftOrderItemQuantity(Long id, Long itemId, UpdateDraftOrderItemQuantityRequest request) {
        Order order = requireItemEditableOrder(id);
        OrderItem orderItem = requireEditableOrderItem(order, itemId);
        LocalDateTime now = LocalDateTime.now();
        orderItem.quantity = request.quantity;
        orderItem.line_amount = calculateLineAmount(
            orderItem.unit_price,
            orderItem.quantity,
            loadOrderItemOptions(orderItem.id)
        );
        orderItem.updated_at = now;
        if (isSubmittedOrder(order)) {
            markSubmittedModification(order, now);
            markSubmittedModification(orderItem, now);
            synchronizeOperationalRecordForOrderItem(order, orderItem, now);
            recalculateOrderStatusAfterSubmittedModification(order, now);
            publishOrderEvent("order.modified_after_submit", order, orderItem.id, null, null);
        }
        orderItemRepository.save(orderItem);
        recalculateOrderAmounts(order, now);
        return loadOrderResponse(order.id);
    }

    @Override
    @Transactional
    public OrderResponse updateDraftOrderItem(Long id, Long itemId, UpdateDraftOrderItemRequest request) {
        Order order = requireItemEditableOrder(id);
        OrderItem orderItem = requireEditableOrderItem(order, itemId);
        LocalDateTime now = LocalDateTime.now();

        if (request.quantity != null) {
            if (request.quantity < 1) {
                throw new BusinessException("quantity must be at least 1");
            }
            orderItem.quantity = request.quantity;
        }
        orderItem.notes = request.notes;

        String comboRole = request.combo_role == null ? normalizeComboRole(orderItem.combo_role) : normalizeComboRole(request.combo_role);
        Integer comboGroupNo = request.combo_group_no == null ? orderItem.combo_group_no : request.combo_group_no;
        orderItem.combo_role = comboRole;
        orderItem.combo_group_no = normalizeComboGroupNo(comboRole, comboGroupNo);
        orderItem.updated_at = now;
        orderItemRepository.save(orderItem);

        replaceOrderItemOptions(orderItem, normalizeOptionRequests(request.options), now);
        orderItem.line_amount = calculateLineAmount(
            orderItem.unit_price,
            orderItem.quantity,
            loadOrderItemOptions(orderItem.id)
        );
        orderItem.updated_at = now;
        if (isSubmittedOrder(order)) {
            markSubmittedModification(order, now);
            markSubmittedModification(orderItem, now);
            synchronizeOperationalRecordForOrderItem(order, orderItem, now);
            recalculateOrderStatusAfterSubmittedModification(order, now);
            publishOrderEvent("order.modified_after_submit", order, orderItem.id, null, null);
        }
        orderItemRepository.save(orderItem);

        recalculateOrderAmounts(order, now);
        return loadOrderResponse(order.id);
    }

    @Override
    @Transactional
    public OrderResponse removeDraftOrderItem(Long id, Long itemId) {
        Order order = requireItemEditableOrder(id);
        OrderItem orderItem = requireEditableOrderItem(order, itemId);
        LocalDateTime now = LocalDateTime.now();
        if (isSubmittedOrder(order)) {
            markSubmittedModification(order, now);
            markSubmittedModification(orderItem, now);
            cancelOperationalRecordsForRemovedOrderItem(order, orderItem, now);
            orderItem.status = ORDER_ITEM_STATUS_CANCELLED;
            orderItem.updated_at = now;
            orderItemRepository.save(orderItem);
            recalculateOrderStatusAfterSubmittedModification(order, now);
            publishOrderEvent("order.modified_after_submit", order, orderItem.id, null, null);
        } else {
            orderItemOptionRepository.deleteByOrderItemId(orderItem.id);
            orderItemRepository.delete(orderItem);
        }
        recalculateOrderAmounts(order, now);
        return loadOrderResponse(order.id);
    }

    @Override
    @Transactional
    public OrderResponse submitOrder(Long id) {
        LocalDateTime now = LocalDateTime.now();
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Order not found: " + id));

        if (!ORDER_STATUS_DRAFT.equals(order.status)) {
            throw new BusinessException("Only draft orders can be submitted");
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.id);
        if (orderItems.isEmpty()) {
            throw new BusinessException("Draft order must contain at least one item before submission");
        }

        List<OrderItemOption> orderItemOptions = loadOptionsForOrderItems(orderItems);

        order.status = ORDER_STATUS_SUBMITTED;
        order.submitted_at = now;
        order.updated_at = now;
        orderRepository.save(order);

        List<KitchenTask> kitchenTasks = createKitchenTasks(order, orderItems, orderItemOptions, now);
        createFrontdeskBeverageItems(order, orderItems, orderItemOptions, now);
        deductInventory(order, orderItems, orderItemOptions, now);

        order.status = kitchenTasks.isEmpty() ? ORDER_STATUS_READY : ORDER_STATUS_PREPARING;
        if (kitchenTasks.isEmpty()) {
            order.ready_at = now;
        }
        order.updated_at = now;
        orderRepository.save(order);

        publishOrderEvent("order.submitted", order, null, null, null);
        if (ORDER_STATUS_READY.equals(order.status)) {
            publishOrderEvent("order.ready", order, null, null, null);
        }
        return loadOrderResponse(order.id);
    }

    @Override
    public List<OrderResponse> getActiveOrders(Long storeId, List<String> statuses, String orderType, String sortBy) {
        Set<String> statusFilter = normalizeStatuses(statuses);
        List<Order> orders = orderRepository.findActiveOperationalOrders(storeId).stream()
            .filter(order -> statusFilter.contains(order.status))
            .filter(order -> orderType == null || orderType.isBlank() || orderType.equals(order.order_type))
            .sorted(resolveOrderComparator(sortBy))
            .toList();

        return orders.stream().map(order -> loadOrderResponse(order.id)).toList();
    }

    @Override
    public List<FrontdeskOrderBoardResponse> getFrontdeskOrderBoard(
        Long storeId,
        List<String> statuses,
        String orderType,
        String tableNo,
        String pickupNo,
        String keyword
    ) {
        Set<String> statusFilter = normalizeStatuses(statuses);
        List<Order> orders = orderRepository.findAllByStoreId(storeId).stream()
            .filter(order -> statusFilter.contains(order.status))
            .filter(order -> matchesOrderType(order, orderType))
            .filter(order -> matchesExact(order.table_no, tableNo))
            .filter(order -> matchesExact(order.pickup_no, pickupNo))
            .filter(order -> matchesKeyword(order, keyword))
            .sorted(resolveFrontdeskBoardComparator())
            .toList();
        return buildFrontdeskOrderBoardResponses(orders);
    }

    @Override
    public List<FrontdeskOrderBoardResponse> getFrontdeskOrderHistory(
        Long storeId,
        List<String> statuses,
        String orderType,
        String tableNo,
        String pickupNo,
        String keyword,
        Integer limit
    ) {
        Set<String> statusFilter = normalizeHistoryStatuses(statuses);
        int historyLimit = normalizeHistoryLimit(limit);
        List<Order> orders = orderRepository.findAllByStoreId(storeId).stream()
            .filter(order -> statusFilter.contains(order.status))
            .filter(order -> matchesOrderType(order, orderType))
            .filter(order -> matchesExact(order.table_no, tableNo))
            .filter(order -> matchesExact(order.pickup_no, pickupNo))
            .filter(order -> matchesKeyword(order, keyword))
            .sorted(resolveFrontdeskHistoryComparator())
            .limit(historyLimit)
            .toList();
        return buildFrontdeskOrderBoardResponses(orders);
    }

    @Override
    @Transactional
    public OrderResponse completeOrder(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Order not found: " + id));

        if (ORDER_STATUS_COMPLETED.equals(order.status)) {
            throw new BusinessException("Order is already completed");
        }
        if (!ORDER_STATUS_READY.equals(order.status)) {
            throw new BusinessException("Only ready orders can be completed");
        }

        LocalDateTime now = LocalDateTime.now();
        order.status = ORDER_STATUS_COMPLETED;
        order.completed_at = now;
        order.updated_at = now;
        orderRepository.save(order);
        publishOrderEvent("order.completed", order, null, null, null);
        return loadOrderResponse(order.id);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Order not found: " + id));

        if (ORDER_STATUS_COMPLETED.equals(order.status)) {
            throw new BusinessException("Completed orders cannot be cancelled");
        }
        if (ORDER_STATUS_CANCELLED.equals(order.status)) {
            throw new BusinessException("Order is already cancelled");
        }

        List<KitchenTask> tasks = kitchenTaskRepository.findAllByOrderId(order.id);
        List<FrontdeskBeverageItem> beverageItems = frontdeskBeverageItemRepository.findAllByOrderId(order.id);
        if (!ORDER_STATUS_DRAFT.equals(order.status)) {
            if (!ORDER_STATUS_SUBMITTED.equals(order.status) && !ORDER_STATUS_PREPARING.equals(order.status)) {
                throw new BusinessException("Only draft or not-yet-processed active orders can be cancelled");
            }
            boolean kitchenStarted = tasks.stream().anyMatch(task -> !KitchenTaskStatus.pending.name().equals(task.status));
            if (kitchenStarted) {
                throw new BusinessException("Order cannot be cancelled after kitchen processing has started");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (KitchenTask task : tasks) {
            task.status = KitchenTaskStatus.cancelled.name();
            task.cancelled_at = now;
        }
        if (!tasks.isEmpty()) {
            kitchenTaskRepository.saveAll(tasks);
        }
        for (FrontdeskBeverageItem beverageItem : beverageItems) {
            if (BEVERAGE_STATUS_SERVED.equals(beverageItem.status) || BEVERAGE_STATUS_CANCELLED.equals(beverageItem.status)) {
                continue;
            }
            beverageItem.status = BEVERAGE_STATUS_CANCELLED;
            beverageItem.cancelled_at = now;
        }
        if (!beverageItems.isEmpty()) {
            frontdeskBeverageItemRepository.saveAll(beverageItems);
        }

        order.status = ORDER_STATUS_CANCELLED;
        order.updated_at = now;
        orderRepository.save(order);
        publishOrderEvent("order.cancelled", order, null, null, null);
        return loadOrderResponse(order.id);
    }

    private OrderResponse loadOrderResponse(Long orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found: " + orderId));
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.id).stream()
            .filter(this::isActiveOrderItem)
            .toList();
        List<OrderItemOption> orderItemOptions = loadOptionsForOrderItems(orderItems);
        List<KitchenTask> kitchenTasks = kitchenTaskRepository.findAllByOrderId(order.id);
        List<FrontdeskBeverageItem> beverageItems = frontdeskBeverageItemRepository.findAllByOrderId(order.id);
        return toOrderResponse(order, orderItems, orderItemOptions, kitchenTasks, beverageItems);
    }

    private List<FrontdeskOrderBoardResponse> buildFrontdeskOrderBoardResponses(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> orderIds = orders.stream().map(order -> order.id).toList();
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderIds(orderIds).stream()
            .filter(this::isActiveOrderItem)
            .toList();
        Map<Long, List<OrderItem>> orderItemsByOrderId = new HashMap<>();
        for (OrderItem orderItem : orderItems) {
            orderItemsByOrderId.computeIfAbsent(orderItem.order_id, key -> new ArrayList<>()).add(orderItem);
        }

        Map<Long, List<KitchenTask>> kitchenTasksByOrderId = new HashMap<>();
        for (KitchenTask kitchenTask : kitchenTaskRepository.findAllByOrderIds(orderIds)) {
            kitchenTasksByOrderId.computeIfAbsent(kitchenTask.order_id, key -> new ArrayList<>()).add(kitchenTask);
        }

        Map<Long, List<FrontdeskBeverageItem>> beverageItemsByOrderId = new HashMap<>();
        for (Long orderId : orderIds) {
            List<FrontdeskBeverageItem> beverageItems = frontdeskBeverageItemRepository.findAllByOrderId(orderId);
            if (!beverageItems.isEmpty()) {
                beverageItemsByOrderId.put(orderId, beverageItems);
            }
        }

        List<FrontdeskOrderBoardResponse> responses = new ArrayList<>();
        for (Order order : orders) {
            List<OrderItem> orderLevelItems = orderItemsByOrderId.getOrDefault(order.id, List.of());
            List<KitchenTask> kitchenTasks = kitchenTasksByOrderId.getOrDefault(order.id, List.of());
            List<FrontdeskBeverageItem> beverageItems = beverageItemsByOrderId.getOrDefault(order.id, List.of());

            int totalItemCount = orderLevelItems.size();
            int kitchenPendingCount = 0;
            int beveragePendingCount = 0;
            int readyItemCount = 0;

            Set<Long> readyKitchenOrderItemIds = new HashSet<>();
            Set<Long> readyBeverageOrderItemIds = new HashSet<>();
            for (KitchenTask kitchenTask : kitchenTasks) {
                if (isKitchenReadyOrHigher(kitchenTask.status)) {
                    readyKitchenOrderItemIds.add(kitchenTask.order_item_id);
                } else if (!KitchenTaskStatus.cancelled.name().equals(kitchenTask.status)) {
                    kitchenPendingCount++;
                }
            }
            for (FrontdeskBeverageItem beverageItem : beverageItems) {
                if (isBeverageReadyOrHigher(beverageItem.status)) {
                    readyBeverageOrderItemIds.add(beverageItem.order_item_id);
                } else if (isBeveragePending(beverageItem.status)) {
                    beveragePendingCount++;
                }
            }
            for (OrderItem orderItem : orderLevelItems) {
                if (readyKitchenOrderItemIds.contains(orderItem.id) || readyBeverageOrderItemIds.contains(orderItem.id)) {
                    readyItemCount++;
                }
            }

            FrontdeskOrderBoardResponse response = new FrontdeskOrderBoardResponse();
            response.order_id = order.id;
            response.order_no = order.order_no;
            response.order_type = order.order_type;
            response.table_no = order.table_no;
            response.pickup_no = order.pickup_no;
            response.order_status = order.status;
            response.is_modified_after_submit = Boolean.TRUE.equals(order.is_modified_after_submit);
            response.modified_after_submit_at = order.modified_after_submit_at;
            response.submitted_at = order.submitted_at;
            response.updated_at = order.updated_at;
            response.total_item_count = totalItemCount;
            response.ready_item_count = readyItemCount;
            response.beverage_pending_count = beveragePendingCount;
            response.kitchen_pending_count = kitchenPendingCount;
            responses.add(response);
        }
        return responses;
    }

    private Order requireDraftOrder(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Order not found: " + id));
        if (!ORDER_STATUS_DRAFT.equals(order.status)) {
            throw new BusinessException("Only draft orders can be edited");
        }
        return order;
    }

    private Order requireItemEditableOrder(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Order not found: " + id));
        if (ORDER_STATUS_COMPLETED.equals(order.status) || ORDER_STATUS_CANCELLED.equals(order.status)) {
            throw new BusinessException("Completed or cancelled orders cannot be modified");
        }
        if (!ORDER_STATUS_DRAFT.equals(order.status) && !MODIFIABLE_AFTER_SUBMIT_ORDER_STATUSES.contains(order.status)) {
            throw new BusinessException("Order status does not allow item modification: " + order.status);
        }
        return order;
    }

    private OrderItem requireEditableOrderItem(Order order, Long itemId) {
        OrderItem orderItem = orderItemRepository.findById(itemId)
            .orElseThrow(() -> new BusinessException("Order item not found: " + itemId));
        if (!order.id.equals(orderItem.order_id)) {
            throw new BusinessException("Order item does not belong to order: " + itemId);
        }
        if (!isActiveOrderItem(orderItem)) {
            throw new BusinessException("Cancelled order item cannot be modified");
        }
        if (isSubmittedOrder(order)) {
            validateSubmittedOrderItemCanBeModified(orderItem.id);
        }
        return orderItem;
    }

    private OrderItem addDraftOrderItemInternal(Order order, CreateOrderItemRequest itemRequest, LocalDateTime now) {
        MenuItem menuItem = menuItemRepository.findById(itemRequest.menu_item_id)
            .orElseThrow(() -> new BusinessException("Menu item not found: " + itemRequest.menu_item_id));

        validateMenuItemBelongsToStore(menuItem, order.store_id);
        MenuCategory menuCategory = findMenuCategory(menuItem);

        OrderItem orderItem = new OrderItem();
        orderItem.order_id = order.id;
        orderItem.menu_item_id = menuItem.id;
        orderItem.category_code_snapshot = menuCategory.code;
        orderItem.item_name_snapshot_zh = menuItem.name_zh;
        orderItem.item_name_snapshot_en = menuItem.name_en;
        orderItem.quantity = itemRequest.quantity;
        orderItem.unit_price = defaultIfNull(menuItem.base_price);
        orderItem.combo_role = normalizeComboRole(itemRequest.combo_role);
        orderItem.combo_group_no = normalizeComboGroupNo(orderItem.combo_role, itemRequest.combo_group_no);
        orderItem.status = null;
        orderItem.notes = itemRequest.notes;
        orderItem.is_modified_after_submit = false;
        orderItem.created_at = now;
        orderItem.updated_at = now;

        OrderItem savedOrderItem = orderItemRepository.save(orderItem);
        List<OrderItemOption> savedOptions = createOrderItemOptions(
            savedOrderItem,
            menuItem.id,
            normalizeOptionRequests(itemRequest.options),
            now
        );
        savedOrderItem.line_amount = calculateLineAmount(savedOrderItem.unit_price, savedOrderItem.quantity, savedOptions);
        savedOrderItem.updated_at = now;
        return orderItemRepository.save(savedOrderItem);
    }

    private void replaceOrderItemOptions(OrderItem orderItem, List<CreateOrderItemOptionRequest> optionRequests, LocalDateTime now) {
        orderItemOptionRepository.deleteByOrderItemId(orderItem.id);
        createOrderItemOptions(orderItem, orderItem.menu_item_id, optionRequests, now);
    }

    private List<OrderItemOption> createOrderItemOptions(
        OrderItem orderItem,
        Long menuItemId,
        List<CreateOrderItemOptionRequest> optionRequests,
        LocalDateTime now
    ) {
        List<OrderItemOption> savedOptions = new ArrayList<>();
        for (CreateOrderItemOptionRequest optionRequest : optionRequests) {
            MenuItemOption menuItemOption = menuItemOptionRepository.findById(optionRequest.option_id)
                .orElseThrow(() -> new BusinessException("Menu item option not found: " + optionRequest.option_id));

            validateOptionBelongsToMenuItem(menuItemOption, menuItemId);

            OrderItemOption orderItemOption = new OrderItemOption();
            orderItemOption.order_item_id = orderItem.id;
            orderItemOption.option_id = menuItemOption.id;
            orderItemOption.option_type_snapshot = menuItemOption.option_type;
            orderItemOption.option_name_snapshot_zh = menuItemOption.name_zh;
            orderItemOption.option_name_snapshot_en = menuItemOption.name_en;
            orderItemOption.price_delta = defaultIfNull(menuItemOption.price_delta);
            orderItemOption.quantity = optionRequest.quantity;
            orderItemOption.created_at = now;
            savedOptions.add(orderItemOptionRepository.save(orderItemOption));
        }
        return savedOptions;
    }

    private void recalculateOrderAmounts(Order order, LocalDateTime now) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem orderItem : orderItemRepository.findAllByOrderId(order.id).stream().filter(this::isActiveOrderItem).toList()) {
            subtotal = subtotal.add(defaultIfNull(orderItem.line_amount));
        }
        order.subtotal_amount = subtotal;
        order.discount_amount = BigDecimal.ZERO;
        order.total_amount = subtotal;
        order.updated_at = now;
        orderRepository.save(order);
    }

    private void createFrontdeskBeverageItems(
        Order order,
        List<OrderItem> orderItems,
        List<OrderItemOption> orderItemOptions,
        LocalDateTime now
    ) {
        Store store = loadStore(order.store_id);
        Map<Long, List<OrderItemOption>> optionsByOrderItemId = groupOptionsByOrderItemId(orderItemOptions);
        List<FrontdeskBeverageItem> beverageItems = new ArrayList<>();

        for (OrderItem orderItem : orderItems) {
            if (!isFrontdeskBeverageItem(orderItem.category_code_snapshot, store)) {
                continue;
            }
            FrontdeskBeverageItem beverageItem = new FrontdeskBeverageItem();
            beverageItem.order_id = order.id;
            beverageItem.order_item_id = orderItem.id;
            beverageItem.store_id = order.store_id;
            beverageItem.item_name_snapshot_zh = orderItem.item_name_snapshot_zh;
            beverageItem.item_name_snapshot_en = orderItem.item_name_snapshot_en;
            beverageItem.special_instructions_snapshot = buildBeverageInstructionsSnapshot(
                orderItem,
                optionsByOrderItemId.getOrDefault(orderItem.id, List.of())
            );
            beverageItem.status = BEVERAGE_STATUS_PENDING;
            beverageItem.quantity = orderItem.quantity;
            beverageItem.created_at = now;
            beverageItems.add(beverageItem);
        }

        if (!beverageItems.isEmpty()) {
            frontdeskBeverageItemRepository.saveAll(beverageItems);
        }
    }

    private void synchronizeOperationalRecordForOrderItem(Order order, OrderItem orderItem, LocalDateTime now) {
        List<OrderItemOption> orderItemOptions = loadOrderItemOptions(orderItem.id);
        Store store = loadStore(order.store_id);

        if (isFrontdeskBeverageItem(orderItem.category_code_snapshot, store)) {
            FrontdeskBeverageItem beverageItem = frontdeskBeverageItemRepository.findByOrderItemId(orderItem.id);
            if (beverageItem == null) {
                beverageItem = new FrontdeskBeverageItem();
                beverageItem.order_id = order.id;
                beverageItem.order_item_id = orderItem.id;
                beverageItem.store_id = order.store_id;
                beverageItem.status = BEVERAGE_STATUS_PENDING;
                beverageItem.created_at = now;
            }
            if (BEVERAGE_STATUS_READY.equals(beverageItem.status) || BEVERAGE_STATUS_SERVED.equals(beverageItem.status)) {
                throw new BusinessException("Ready or served beverage item cannot be modified");
            }
            beverageItem.item_name_snapshot_zh = orderItem.item_name_snapshot_zh;
            beverageItem.item_name_snapshot_en = orderItem.item_name_snapshot_en;
            beverageItem.quantity = orderItem.quantity;
            beverageItem.special_instructions_snapshot = buildBeverageInstructionsSnapshot(orderItem, orderItemOptions);
            frontdeskBeverageItemRepository.save(beverageItem);
            return;
        }

        KitchenTask kitchenTask = findKitchenTaskByOrderItemId(order.id, orderItem.id);
        if (kitchenTask == null) {
            kitchenTask = createKitchenTaskForOrderItem(order, orderItem, orderItemOptions, now);
        } else {
            if (KitchenTaskStatus.ready_for_pickup.name().equals(kitchenTask.status)
                || KitchenTaskStatus.served.name().equals(kitchenTask.status)) {
                throw new BusinessException("ready_for_pickup or served kitchen item cannot be modified");
            }
            if (KitchenTaskStatus.cancelled.name().equals(kitchenTask.status)) {
                kitchenTask.status = KitchenTaskStatus.pending.name();
                kitchenTask.cancelled_at = null;
                kitchenTask.started_at = null;
                kitchenTask.completed_at = null;
                kitchenTask.served_at = null;
            }
            kitchenTask.item_name_snapshot_zh = orderItem.item_name_snapshot_zh;
            kitchenTask.item_name_snapshot_en = orderItem.item_name_snapshot_en;
            kitchenTask.quantity = orderItem.quantity;
            kitchenTask.special_instructions_snapshot = buildSpecialInstructionsSnapshot(orderItem, orderItemOptions);
            kitchenTaskRepository.save(kitchenTask);
        }
    }

    private KitchenTask createKitchenTaskForOrderItem(
        Order order,
        OrderItem orderItem,
        List<OrderItemOption> orderItemOptions,
        LocalDateTime now
    ) {
        MenuItem menuItem = menuItemRepository.findById(orderItem.menu_item_id)
            .orElseThrow(() -> new BusinessException("Menu item not found for kitchen task: " + orderItem.menu_item_id));
        if (menuItem.station_id == null) {
            throw new BusinessException("menu_items.station_id is required for kitchen task assignment: " + menuItem.id);
        }
        Station station = stationRepository.findActiveStationByIdAndStoreId(menuItem.station_id, order.store_id);
        if (station == null) {
            throw new BusinessException(
                "Assigned station is not enabled for store. menu_item_id=" + menuItem.id + ", station_id=" + menuItem.station_id
            );
        }

        KitchenTask kitchenTask = new KitchenTask();
        kitchenTask.order_id = order.id;
        kitchenTask.order_item_id = orderItem.id;
        kitchenTask.store_id = order.store_id;
        kitchenTask.station_code = station.code;
        kitchenTask.item_name_snapshot_zh = orderItem.item_name_snapshot_zh;
        kitchenTask.item_name_snapshot_en = orderItem.item_name_snapshot_en;
        kitchenTask.special_instructions_snapshot = buildSpecialInstructionsSnapshot(orderItem, orderItemOptions);
        kitchenTask.status = KitchenTaskStatus.pending.name();
        kitchenTask.quantity = orderItem.quantity;
        kitchenTask.created_at = now;
        return kitchenTaskRepository.save(kitchenTask);
    }

    private void cancelOperationalRecordsForRemovedOrderItem(Order order, OrderItem orderItem, LocalDateTime now) {
        FrontdeskBeverageItem beverageItem = frontdeskBeverageItemRepository.findByOrderItemId(orderItem.id);
        if (beverageItem != null) {
            if (BEVERAGE_STATUS_READY.equals(beverageItem.status) || BEVERAGE_STATUS_SERVED.equals(beverageItem.status)) {
                throw new BusinessException("Ready or served beverage item cannot be modified");
            }
            beverageItem.status = BEVERAGE_STATUS_CANCELLED;
            beverageItem.cancelled_at = now;
            frontdeskBeverageItemRepository.save(beverageItem);
            return;
        }

        KitchenTask kitchenTask = findKitchenTaskByOrderItemId(order.id, orderItem.id);
        if (kitchenTask != null) {
            if (KitchenTaskStatus.ready_for_pickup.name().equals(kitchenTask.status)
                || KitchenTaskStatus.served.name().equals(kitchenTask.status)) {
                throw new BusinessException("ready_for_pickup or served kitchen item cannot be modified");
            }
            kitchenTask.status = KitchenTaskStatus.cancelled.name();
            kitchenTask.cancelled_at = now;
            kitchenTaskRepository.save(kitchenTask);
        }
    }

    private void recalculateOrderStatusAfterSubmittedModification(Order order, LocalDateTime now) {
        List<KitchenTask> kitchenTasks = kitchenTaskRepository.findAllByOrderId(order.id);
        boolean hasOpenKitchenTasks = kitchenTasks.stream()
            .filter(task -> !KitchenTaskStatus.cancelled.name().equals(task.status))
            .anyMatch(task -> KitchenTaskStatus.pending.name().equals(task.status)
                || KitchenTaskStatus.in_progress.name().equals(task.status));
        boolean shouldPublishReadyEvent = false;

        if (hasOpenKitchenTasks) {
            order.status = ORDER_STATUS_PREPARING;
            order.ready_at = null;
        } else {
            boolean wasReady = ORDER_STATUS_READY.equals(order.status);
            order.status = ORDER_STATUS_READY;
            if (order.ready_at == null) {
                order.ready_at = now;
            }
            shouldPublishReadyEvent = !wasReady;
        }
        order.updated_at = now;
        orderRepository.save(order);
        if (shouldPublishReadyEvent) {
            publishOrderEvent("order.ready", order, null, null, null);
        }
    }

    private void markSubmittedModification(Order order, LocalDateTime now) {
        order.is_modified_after_submit = true;
        order.modified_after_submit_at = now;
        order.updated_at = now;
        orderRepository.save(order);
    }

    private void markSubmittedModification(OrderItem orderItem, LocalDateTime now) {
        orderItem.is_modified_after_submit = true;
        orderItem.modified_after_submit_at = now;
        orderItem.updated_at = now;
    }

    private void validateSubmittedOrderItemCanBeModified(Long orderItemId) {
        FrontdeskBeverageItem beverageItem = frontdeskBeverageItemRepository.findByOrderItemId(orderItemId);
        if (beverageItem != null) {
            if (BEVERAGE_STATUS_READY.equals(beverageItem.status) || BEVERAGE_STATUS_SERVED.equals(beverageItem.status)) {
                throw new BusinessException("Ready or served beverage item cannot be modified");
            }
            return;
        }
        KitchenTask kitchenTask = findKitchenTaskByOrderItemId(null, orderItemId);
        if (kitchenTask != null && (KitchenTaskStatus.ready_for_pickup.name().equals(kitchenTask.status)
            || KitchenTaskStatus.served.name().equals(kitchenTask.status))) {
            throw new BusinessException("ready_for_pickup or served kitchen item cannot be modified");
        }
    }

    private KitchenTask findKitchenTaskByOrderItemId(Long orderId, Long orderItemId) {
        Long resolvedOrderId = orderId == null ? orderItemsOrderId(orderItemId) : orderId;
        List<KitchenTask> tasks = kitchenTaskRepository.findAllByOrderId(resolvedOrderId);
        for (KitchenTask task : tasks) {
            if (orderItemId.equals(task.order_item_id)) {
                return task;
            }
        }
        return null;
    }

    private Long orderItemsOrderId(Long orderItemId) {
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
            .orElseThrow(() -> new BusinessException("Order item not found: " + orderItemId));
        return orderItem.order_id;
    }

    private BigDecimal calculateLineAmount(BigDecimal unitPrice, Integer quantity, List<OrderItemOption> options) {
        BigDecimal lineAmount = defaultIfNull(unitPrice).multiply(BigDecimal.valueOf(quantity));
        for (OrderItemOption option : options) {
            lineAmount = lineAmount.add(defaultIfNull(option.price_delta).multiply(BigDecimal.valueOf(option.quantity)));
        }
        return lineAmount;
    }

    private List<OrderItemOption> loadOptionsForOrderItems(List<OrderItem> orderItems) {
        List<Long> orderItemIds = orderItems.stream().map(orderItem -> orderItem.id).toList();
        if (orderItemIds.isEmpty()) {
            return List.of();
        }
        return orderItemOptionRepository.findAllByOrderItemIds(orderItemIds);
    }

    private List<OrderItemOption> loadOrderItemOptions(Long orderItemId) {
        return orderItemOptionRepository.findAllByOrderItemIds(List.of(orderItemId));
    }

    private List<KitchenTask> createKitchenTasks(
        Order order,
        List<OrderItem> orderItems,
        List<OrderItemOption> orderItemOptions,
        LocalDateTime now
    ) {
        Map<Long, List<OrderItemOption>> optionsByOrderItemId = groupOptionsByOrderItemId(orderItemOptions);
        List<KitchenTask> kitchenTasks = new ArrayList<>();

        for (OrderItem orderItem : orderItems) {
            MenuItem menuItem = menuItemRepository.findById(orderItem.menu_item_id)
                .orElseThrow(() -> new BusinessException("Menu item not found for kitchen task: " + orderItem.menu_item_id));
            if (isDirectServe(menuItem, order.store_id)) {
                continue;
            }
            if (menuItem.station_id == null) {
                throw new BusinessException("menu_items.station_id is required for kitchen task assignment: " + menuItem.id);
            }

            Station station = stationRepository.findActiveStationByIdAndStoreId(menuItem.station_id, order.store_id);
            if (station == null) {
                throw new BusinessException(
                    "Assigned station is not enabled for store. menu_item_id=" + menuItem.id + ", station_id=" + menuItem.station_id
                );
            }

            KitchenTask kitchenTask = new KitchenTask();
            kitchenTask.order_id = order.id;
            kitchenTask.order_item_id = orderItem.id;
            kitchenTask.store_id = order.store_id;
            kitchenTask.station_code = station.code;
            kitchenTask.item_name_snapshot_zh = orderItem.item_name_snapshot_zh;
            kitchenTask.item_name_snapshot_en = orderItem.item_name_snapshot_en;
            kitchenTask.special_instructions_snapshot = buildSpecialInstructionsSnapshot(
                orderItem,
                optionsByOrderItemId.getOrDefault(orderItem.id, List.of())
            );
            kitchenTask.status = KitchenTaskStatus.pending.name();
            kitchenTask.quantity = orderItem.quantity;
            kitchenTask.priority = null;
            kitchenTask.created_at = now;
            kitchenTasks.add(kitchenTask);
        }
        return kitchenTaskRepository.saveAll(kitchenTasks);
    }

    private void deductInventory(
        Order order,
        List<OrderItem> orderItems,
        List<OrderItemOption> orderItemOptions,
        LocalDateTime now
    ) {
        Map<Long, List<OrderItemOption>> optionsByOrderItemId = groupOptionsByOrderItemId(orderItemOptions);

        for (OrderItem orderItem : orderItems) {
            List<MenuItemBom> itemBoms = menuItemBomRepository.findAllByMenuItemId(orderItem.menu_item_id);
            for (MenuItemBom itemBom : itemBoms) {
                BigDecimal qtyChange = itemBom.qty_per_unit
                    .multiply(BigDecimal.valueOf(orderItem.quantity))
                    .negate();
                consumeInventory(order, itemBom.inventory_item_id, qtyChange, now);
            }

            for (OrderItemOption orderItemOption : optionsByOrderItemId.getOrDefault(orderItem.id, List.of())) {
                List<MenuItemOptionBom> optionBoms =
                    menuItemOptionBomRepository.findAllByMenuItemOptionId(orderItemOption.option_id);
                for (MenuItemOptionBom optionBom : optionBoms) {
                    BigDecimal qtyChange = optionBom.qty_per_unit
                        .multiply(BigDecimal.valueOf(orderItemOption.quantity))
                        .negate();
                    consumeInventory(order, optionBom.inventory_item_id, qtyChange, now);
                }
            }
        }
    }

    private void consumeInventory(Order order, Long inventoryItemId, BigDecimal qtyChange, LocalDateTime now) {
        InventoryItem inventoryItem = inventoryItemRepository.findById(inventoryItemId)
            .orElseThrow(() -> new BusinessException("Inventory item not found: " + inventoryItemId));

        BigDecimal stockBefore = defaultIfNull(inventoryItem.current_stock);
        BigDecimal stockAfter = stockBefore.add(qtyChange);
        if (stockAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Insufficient inventory for item: " + inventoryItemId);
        }

        inventoryItem.current_stock = stockAfter;
        inventoryItem.updated_at = now;
        inventoryItemRepository.save(inventoryItem);

        InventoryTransaction inventoryTransaction = new InventoryTransaction();
        inventoryTransaction.inventory_item_id = inventoryItemId;
        inventoryTransaction.operated_by = order.created_by;
        inventoryTransaction.txn_type = INVENTORY_TXN_TYPE_CONSUME;
        inventoryTransaction.source_type = INVENTORY_SOURCE_TYPE_ORDER;
        inventoryTransaction.source_id = order.id;
        inventoryTransaction.qty_change = qtyChange;
        inventoryTransaction.stock_before = stockBefore;
        inventoryTransaction.stock_after = stockAfter;
        inventoryTransaction.remarks = "Order submission inventory deduction";
        inventoryTransaction.created_at = now;
        inventoryTransactionRepository.save(inventoryTransaction);
    }

    private void validateMenuItemBelongsToStore(MenuItem menuItem, Long storeId) {
        if (menuItem.store_id == null || !menuItem.store_id.equals(storeId)) {
            throw new BusinessException("Menu item does not belong to the order store: " + menuItem.id);
        }
    }

    private void validateOptionBelongsToMenuItem(MenuItemOption menuItemOption, Long menuItemId) {
        if (menuItemOption.menu_item_id == null || !menuItemOption.menu_item_id.equals(menuItemId)) {
            throw new BusinessException("Menu item option does not belong to menu item: " + menuItemOption.id);
        }
    }

    private OrderResponse toOrderResponse(
        Order order,
        List<OrderItem> orderItems,
        List<OrderItemOption> orderItemOptions,
        List<KitchenTask> kitchenTasks,
        List<FrontdeskBeverageItem> beverageItems
    ) {
        Store store = storeRepository.findById(order.store_id)
            .orElseThrow(() -> new BusinessException("Store not found: " + order.store_id));

        Map<Long, List<OrderItemOptionResponse>> optionsByOrderItemId = new HashMap<>();
        for (OrderItemOption orderItemOption : orderItemOptions) {
            OrderItemOptionResponse response = new OrderItemOptionResponse();
            response.id = orderItemOption.id;
            response.option_id = orderItemOption.option_id;
            response.option_type_snapshot = orderItemOption.option_type_snapshot;
            response.option_name_snapshot_zh = orderItemOption.option_name_snapshot_zh;
            response.option_name_snapshot_en = orderItemOption.option_name_snapshot_en;
            response.price_delta = defaultIfNull(orderItemOption.price_delta);
            response.quantity = orderItemOption.quantity;
            optionsByOrderItemId.computeIfAbsent(orderItemOption.order_item_id, key -> new ArrayList<>()).add(response);
        }

        Map<Long, KitchenTask> taskByOrderItemId = new HashMap<>();
        for (KitchenTask kitchenTask : kitchenTasks) {
            taskByOrderItemId.put(kitchenTask.order_item_id, kitchenTask);
        }
        Map<Long, FrontdeskBeverageItem> beverageItemByOrderItemId = new HashMap<>();
        for (FrontdeskBeverageItem beverageItem : beverageItems) {
            beverageItemByOrderItemId.put(beverageItem.order_item_id, beverageItem);
        }

        List<OrderItemResponse> itemResponses = orderItems.stream().map(orderItem -> {
            KitchenTask kitchenTask = taskByOrderItemId.get(orderItem.id);
            FrontdeskBeverageItem beverageTracker = beverageItemByOrderItemId.get(orderItem.id);
            boolean beverageRow = isFrontdeskBeverageItem(orderItem.category_code_snapshot, store);

            OrderItemResponse response = new OrderItemResponse();
            response.id = orderItem.id;
            response.menu_item_id = orderItem.menu_item_id;
            response.category_code_snapshot = orderItem.category_code_snapshot;
            response.item_name_snapshot_zh = orderItem.item_name_snapshot_zh;
            response.item_name_snapshot_en = orderItem.item_name_snapshot_en;
            response.quantity = orderItem.quantity;
            response.unit_price = defaultIfNull(orderItem.unit_price);
            response.line_amount = defaultIfNull(orderItem.line_amount);
            response.combo_group_no = orderItem.combo_group_no;
            response.combo_role = orderItem.combo_role;
            response.notes = orderItem.notes;
            response.is_modified_after_submit = Boolean.TRUE.equals(orderItem.is_modified_after_submit);
            response.modified_after_submit_at = orderItem.modified_after_submit_at;
            response.requires_kitchen_task = kitchenTask != null;
            response.is_beverage_item = beverageRow;
            response.is_kitchen_related_item = !beverageRow;
            response.station_code = kitchenTask == null ? null : kitchenTask.station_code;
            response.task_status = kitchenTask == null ? null : kitchenTask.status;
            response.started_at = kitchenTask == null ? null : kitchenTask.started_at;
            response.ready_for_pickup_at = kitchenTask == null ? null : kitchenTask.completed_at;
            response.served_at = kitchenTask == null ? null : kitchenTask.served_at;
            response.beverage_status = beverageTracker == null ? null : beverageTracker.status;
            response.beverage_special_instructions_snapshot = beverageTracker == null
                ? null
                : beverageTracker.special_instructions_snapshot;
            response.beverage_started_at = beverageTracker == null ? null : beverageTracker.started_at;
            response.beverage_ready_at = beverageTracker == null ? null : beverageTracker.ready_at;
            response.beverage_served_at = beverageTracker == null ? null : beverageTracker.served_at;
            response.beverage_cancelled_at = beverageTracker == null ? null : beverageTracker.cancelled_at;
            response.options = optionsByOrderItemId.getOrDefault(orderItem.id, List.of());
            return response;
        }).toList();

        OrderResponse response = new OrderResponse();
        response.id = order.id;
        response.order_no = order.order_no;
        response.status = order.status;
        response.store_id = order.store_id;
        response.created_by = order.created_by;
        response.order_type = order.order_type;
        response.table_no = order.table_no;
        response.pickup_no = order.pickup_no;
        response.subtotal_amount = defaultIfNull(order.subtotal_amount);
        response.discount_amount = defaultIfNull(order.discount_amount);
        response.total_amount = defaultIfNull(order.total_amount);
        response.submitted_at = order.submitted_at;
        response.ready_at = order.ready_at;
        response.completed_at = order.completed_at;
        response.is_modified_after_submit = Boolean.TRUE.equals(order.is_modified_after_submit);
        response.modified_after_submit_at = order.modified_after_submit_at;
        response.modified_after_submit_by = order.modified_after_submit_by;
        response.created_at = order.created_at;
        response.updated_at = order.updated_at;
        response.items = itemResponses;
        response.beverage_items = itemResponses.stream().filter(item -> Boolean.TRUE.equals(item.is_beverage_item)).toList();
        response.kitchen_items = itemResponses.stream().filter(item -> Boolean.TRUE.equals(item.is_kitchen_related_item)).toList();
        return response;
    }

    private BigDecimal defaultIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeComboRole(String comboRole) {
        if (comboRole == null || comboRole.isBlank()) {
            return COMBO_ROLE_STANDALONE;
        }
        if (!ALLOWED_COMBO_ROLES.contains(comboRole)) {
            throw new BusinessException("Invalid combo_role: " + comboRole);
        }
        return comboRole;
    }

    private Integer normalizeComboGroupNo(String comboRole, Integer comboGroupNo) {
        if (COMBO_ROLE_STANDALONE.equals(comboRole)) {
            return null;
        }
        if (comboGroupNo == null) {
            throw new BusinessException("combo_group_no is required when combo_role is not standalone");
        }
        return comboGroupNo;
    }

    private boolean isDirectServe(MenuItem menuItem, Long storeId) {
        return isDirectServe(findMenuCategory(menuItem).code, loadStore(storeId));
    }

    private boolean isDirectServe(String categoryCode, Store store) {
        if (CATEGORY_CODE_DRINK.equals(categoryCode) || CATEGORY_CODE_ALCOHOL.equals(categoryCode)) {
            return true;
        }
        if (CATEGORY_CODE_MILK_TEA.equals(categoryCode)) {
            return !Boolean.TRUE.equals(store.enable_bar_kitchen_tasks);
        }
        return false;
    }

    private boolean isFrontdeskBeverageItem(String categoryCode, Store store) {
        return CATEGORY_CODE_DRINK.equals(categoryCode)
            || CATEGORY_CODE_ALCOHOL.equals(categoryCode)
            || (CATEGORY_CODE_MILK_TEA.equals(categoryCode) && !Boolean.TRUE.equals(store.enable_bar_kitchen_tasks));
    }

    private boolean isSubmittedOrder(Order order) {
        return MODIFIABLE_AFTER_SUBMIT_ORDER_STATUSES.contains(order.status);
    }

    private boolean isActiveOrderItem(OrderItem orderItem) {
        return !ORDER_ITEM_STATUS_CANCELLED.equals(orderItem.status);
    }

    private Store loadStore(Long storeId) {
        return storeRepository.findById(storeId)
            .orElseThrow(() -> new BusinessException("Store not found: " + storeId));
    }

    private MenuCategory findMenuCategory(MenuItem menuItem) {
        if (menuItem.category_id == null) {
            throw new BusinessException("menu_items.category_id is required for routing: " + menuItem.id);
        }
        return menuCategoryRepository.findById(menuItem.category_id)
            .orElseThrow(() -> new BusinessException("Menu category not found for menu item: " + menuItem.id));
    }

    private String buildSpecialInstructionsSnapshot(OrderItem orderItem, List<OrderItemOption> orderItemOptions) {
        List<String> parts = new ArrayList<>();
        if (orderItem.notes != null && !orderItem.notes.isBlank()) {
            parts.add(orderItem.notes.trim());
        }

        for (OrderItemOption orderItemOption : orderItemOptions) {
            if (!KITCHEN_RELEVANT_OPTION_TYPES.contains(orderItemOption.option_type_snapshot)) {
                continue;
            }
            String label = bilingualLabel(orderItemOption.option_name_snapshot_zh, orderItemOption.option_name_snapshot_en);
            if (orderItemOption.quantity != null && orderItemOption.quantity > 1) {
                label = label + " x" + orderItemOption.quantity;
            }
            parts.add(label);
        }

        List<String> normalized = deduplicate(parts);
        return normalized.isEmpty() ? null : String.join(" | ", normalized);
    }

    private String buildBeverageInstructionsSnapshot(OrderItem orderItem, List<OrderItemOption> orderItemOptions) {
        List<String> parts = new ArrayList<>();
        if (orderItem.notes != null && !orderItem.notes.isBlank()) {
            parts.add(orderItem.notes.trim());
        }
        for (OrderItemOption orderItemOption : orderItemOptions) {
            String label = bilingualLabel(orderItemOption.option_name_snapshot_zh, orderItemOption.option_name_snapshot_en);
            if (label.isBlank()) {
                continue;
            }
            if (orderItemOption.quantity != null && orderItemOption.quantity > 1) {
                label = label + " x" + orderItemOption.quantity;
            }
            parts.add(label);
        }
        List<String> normalized = deduplicate(parts);
        return normalized.isEmpty() ? null : String.join(" | ", normalized);
    }

    private String bilingualLabel(String zh, String en) {
        if ((zh == null || zh.isBlank()) && (en == null || en.isBlank())) {
            return "";
        }
        if (en == null || en.isBlank()) {
            return zh;
        }
        if (zh == null || zh.isBlank() || zh.equals(en)) {
            return en;
        }
        return zh + " / " + en;
    }

    private List<String> deduplicate(List<String> values) {
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !seen.add(value)) {
                continue;
            }
            result.add(value);
        }
        return result;
    }

    private List<CreateOrderItemRequest> normalizeItemRequests(List<CreateOrderItemRequest> requests) {
        return requests == null ? List.of() : requests;
    }

    private List<CreateOrderItemOptionRequest> normalizeOptionRequests(List<CreateOrderItemOptionRequest> requests) {
        return requests == null ? List.of() : requests;
    }

    private Map<Long, List<OrderItemOption>> groupOptionsByOrderItemId(List<OrderItemOption> orderItemOptions) {
        Map<Long, List<OrderItemOption>> optionsByOrderItemId = new HashMap<>();
        for (OrderItemOption orderItemOption : orderItemOptions) {
            optionsByOrderItemId.computeIfAbsent(orderItemOption.order_item_id, key -> new ArrayList<>())
                .add(orderItemOption);
        }
        return optionsByOrderItemId;
    }

    private Set<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return ACTIVE_ORDER_STATUSES;
        }
        Set<String> normalized = new HashSet<>();
        for (String status : statuses) {
            if (status == null || status.isBlank()) {
                continue;
            }
            normalized.add(status);
        }
        if (normalized.contains("all")) {
            return Set.of(
                ORDER_STATUS_SUBMITTED,
                ORDER_STATUS_PREPARING,
                ORDER_STATUS_READY,
                ORDER_STATUS_COMPLETED,
                ORDER_STATUS_CANCELLED
            );
        }
        return normalized.isEmpty() ? ACTIVE_ORDER_STATUSES : normalized;
    }

    private Set<String> normalizeHistoryStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return DEFAULT_HISTORY_ORDER_STATUSES;
        }
        Set<String> normalized = new HashSet<>();
        for (String status : statuses) {
            if (status == null || status.isBlank()) {
                continue;
            }
            normalized.add(status);
        }
        if (normalized.contains("all")) {
            return Set.of(
                ORDER_STATUS_SUBMITTED,
                ORDER_STATUS_PREPARING,
                ORDER_STATUS_READY,
                ORDER_STATUS_COMPLETED,
                ORDER_STATUS_CANCELLED
            );
        }
        return normalized.isEmpty() ? DEFAULT_HISTORY_ORDER_STATUSES : normalized;
    }

    private int normalizeHistoryLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_FRONTDESK_HISTORY_LIMIT;
        }
        return limit;
    }

    private boolean matchesOrderType(Order order, String orderType) {
        return orderType == null || orderType.isBlank() || orderType.equals(order.order_type);
    }

    private boolean matchesExact(String actual, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return actual != null && actual.equalsIgnoreCase(filter.trim());
    }

    private boolean matchesKeyword(Order order, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase();
        return containsIgnoreCase(order.order_no, normalizedKeyword)
            || containsIgnoreCase(order.table_no, normalizedKeyword)
            || containsIgnoreCase(order.pickup_no, normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedNeedle) {
        return value != null && value.toLowerCase().contains(normalizedNeedle);
    }

    private boolean isKitchenReadyOrHigher(String status) {
        return KitchenTaskStatus.ready_for_pickup.name().equals(status)
            || KitchenTaskStatus.served.name().equals(status);
    }

    private boolean isBeverageReadyOrHigher(String status) {
        return BEVERAGE_STATUS_READY.equals(status) || BEVERAGE_STATUS_SERVED.equals(status);
    }

    private boolean isBeveragePending(String status) {
        return BEVERAGE_STATUS_PENDING.equals(status) || BEVERAGE_STATUS_PREPARING.equals(status);
    }

    private Comparator<Order> resolveOrderComparator(String sortBy) {
        Comparator<Order> comparator;
        if ("updated_at".equals(sortBy)) {
            comparator = Comparator.comparing(order -> order.updated_at, Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            comparator = Comparator.comparing(order -> order.submitted_at, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return comparator.reversed().thenComparing(order -> order.id, Comparator.reverseOrder());
    }

    private Comparator<Order> resolveFrontdeskBoardComparator() {
        return Comparator.comparing(
                (Order order) -> order.submitted_at == null ? order.updated_at : order.submitted_at,
                Comparator.nullsLast(Comparator.naturalOrder())
            )
            .reversed()
            .thenComparing(order -> order.updated_at, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(order -> order.id, Comparator.reverseOrder());
    }

    private Comparator<Order> resolveFrontdeskHistoryComparator() {
        return Comparator.comparing(this::resolveFrontdeskHistoryTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(order -> order.id, Comparator.reverseOrder());
    }

    private LocalDateTime resolveFrontdeskHistoryTimestamp(Order order) {
        if (ORDER_STATUS_COMPLETED.equals(order.status)) {
            return order.completed_at == null ? order.updated_at : order.completed_at;
        }
        if (ORDER_STATUS_CANCELLED.equals(order.status)) {
            return order.updated_at;
        }
        if (ORDER_STATUS_PICKED_UP.equals(order.status)) {
            return order.updated_at;
        }
        return order.updated_at;
    }

    private void publishOrderEvent(
        String eventType,
        Order order,
        Long orderItemId,
        String taskStatus,
        String beverageStatus
    ) {
        RealtimeUpdateMessage message = new RealtimeUpdateMessage();
        message.event_type = eventType;
        message.store_id = order.store_id;
        message.order_id = order.id;
        message.order_item_id = orderItemId;
        message.order_status = order.status;
        message.task_status = taskStatus;
        message.beverage_status = beverageStatus;
        message.is_modified_after_submit = Boolean.TRUE.equals(order.is_modified_after_submit);
        message.happened_at = LocalDateTime.now();
        realtimeEventPublisher.publish(message, List.of(
            RealtimeTopics.FRONTDESK_ORDERS,
            RealtimeTopics.FRONTDESK_BEVERAGES,
            RealtimeTopics.KDS_NOODLE,
            RealtimeTopics.KDS_HOT_KITCHEN,
            RealtimeTopics.KDS_PASS,
            RealtimeTopics.KDS_SERVING_SHELF,
            RealtimeTopics.HISTORY
        ));
    }

    private String generateOrderNo() {
        return "ORD-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-"
            + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
