package com.restaurant.system.order.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.pricing.TaxCalculator;
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
import com.restaurant.system.order.dto.CreateOrderUpdateRequest;
import com.restaurant.system.order.dto.FrontdeskOrderBoardResponse;
import com.restaurant.system.order.dto.OrderItemOptionResponse;
import com.restaurant.system.order.dto.OrderItemResponse;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.dto.OrderUpdateResponse;
import com.restaurant.system.order.entity.FrontdeskBeverageItem;
import com.restaurant.system.order.dto.UpdateDraftOrderHeaderRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemQuantityRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemRequest;
import com.restaurant.system.order.repository.FrontdeskBeverageItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.order.entity.OrderUpdateBatch;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.repository.OrderUpdateBatchRepository;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.production.entity.ProductionTask;
import com.restaurant.system.production.repository.ProductionTaskRepository;
import com.restaurant.system.printing.PrintModuleCode;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

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
    private static final String CATEGORY_CODE_SIDE = "SIDE";
    private static final String OPTION_TYPE_NOODLE_TYPE = "noodle_type";
    private static final String OPTION_TYPE_SIZE = "size";
    private static final String OPTION_TYPE_ADDON = "addon";
    private static final String OPTION_TYPE_REMOVE = "remove";
    private static final String OPTION_TYPE_SOUP_BASE = "soup_base";
    private static final String OPTION_TYPE_SPICY_LEVEL = "spicy_level";
    private static final String BEVERAGE_STATUS_PENDING = "pending";
    private static final String BEVERAGE_STATUS_PREPARING = "preparing";
    private static final String BEVERAGE_STATUS_READY = "ready";
    private static final String BEVERAGE_STATUS_SERVED = "served";
    private static final String BEVERAGE_STATUS_CANCELLED = "cancelled";
    private static final String PRODUCTION_STATION_FRONTDESK_BEVERAGE = "FRONTDESK_BEVERAGE";
    private static final String PRODUCTION_SOURCE_KITCHEN_TASK = "kitchen_task";
    private static final String PRODUCTION_SOURCE_FRONTDESK_BEVERAGE = "frontdesk_beverage_item";
    private static final int DEFAULT_FRONTDESK_HISTORY_LIMIT = 20;
    private static final int KITCHEN_TASK_PRIORITY_COMBO_SIDE = 100;
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
        OPTION_TYPE_SOUP_BASE,
        OPTION_TYPE_SPICY_LEVEL
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
    private static final Set<String> EDITABLE_TABLE_ORDER_STATUSES = Set.of(
        ORDER_STATUS_DRAFT,
        ORDER_STATUS_SUBMITTED,
        ORDER_STATUS_PREPARING,
        ORDER_STATUS_READY
    );

    private final OrderRepository orderRepository;
    private final OrderUpdateBatchRepository orderUpdateBatchRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemOptionRepository orderItemOptionRepository;
    private final FrontdeskBeverageItemRepository frontdeskBeverageItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionRepository menuItemOptionRepository;
    private final MenuCategoryRepository menuCategoryRepository;
    private final MenuItemBomRepository menuItemBomRepository;
    private final MenuItemOptionBomRepository menuItemOptionBomRepository;
    private final KitchenTaskRepository kitchenTaskRepository;
    private final ProductionTaskRepository productionTaskRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final StationRepository stationRepository;
    private final StoreRepository storeRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final PrintDispatcherService printDispatcherService;

    public OrderServiceImpl(
        OrderRepository orderRepository,
        OrderUpdateBatchRepository orderUpdateBatchRepository,
        OrderItemRepository orderItemRepository,
        OrderItemOptionRepository orderItemOptionRepository,
        FrontdeskBeverageItemRepository frontdeskBeverageItemRepository,
        MenuItemRepository menuItemRepository,
        MenuItemOptionRepository menuItemOptionRepository,
        MenuCategoryRepository menuCategoryRepository,
        MenuItemBomRepository menuItemBomRepository,
        MenuItemOptionBomRepository menuItemOptionBomRepository,
        KitchenTaskRepository kitchenTaskRepository,
        ProductionTaskRepository productionTaskRepository,
        InventoryItemRepository inventoryItemRepository,
        InventoryTransactionRepository inventoryTransactionRepository,
        StationRepository stationRepository,
        StoreRepository storeRepository,
        RealtimeEventPublisher realtimeEventPublisher,
        PrintDispatcherService printDispatcherService
    ) {
        this.orderRepository = orderRepository;
        this.orderUpdateBatchRepository = orderUpdateBatchRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemOptionRepository = orderItemOptionRepository;
        this.frontdeskBeverageItemRepository = frontdeskBeverageItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.menuItemOptionRepository = menuItemOptionRepository;
        this.menuCategoryRepository = menuCategoryRepository;
        this.menuItemBomRepository = menuItemBomRepository;
        this.menuItemOptionBomRepository = menuItemOptionBomRepository;
        this.kitchenTaskRepository = kitchenTaskRepository;
        this.productionTaskRepository = productionTaskRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
        this.stationRepository = stationRepository;
        this.storeRepository = storeRepository;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.printDispatcherService = printDispatcherService;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Order existingEditableOrder = findExistingEditableOrder(request.store_id, request.table_no, request.pickup_no);
        if (existingEditableOrder != null) {
            return loadOrderResponse(existingEditableOrder.id);
        }

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
        order.current_revision = 1;
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
    public OrderResponse findOpenDraftOrder(Long storeId, String tableNo, String pickupNo) {
        Order order = null;
        if (tableNo != null && !tableNo.isBlank()) {
            order = orderRepository.findLatestDraftByStoreIdAndTableNo(storeId, tableNo);
        } else if (pickupNo != null && !pickupNo.isBlank()) {
            order = orderRepository.findLatestDraftByStoreIdAndPickupNo(storeId, pickupNo);
        }

        return order == null ? null : loadOrderResponse(order.id);
    }

    @Override
    public OrderResponse findOpenEditableOrder(Long storeId, String tableNo, String pickupNo) {
        Order order = findExistingEditableOrder(storeId, tableNo, pickupNo);

        return order == null ? null : loadOrderResponse(order.id);
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
        requireDraftForLegacyItemMutation(order);
        LocalDateTime now = LocalDateTime.now();
        OrderItem orderItem = addDraftOrderItemInternal(order, request, now);
        recalculateOrderAmounts(order, now);
        return loadOrderResponse(order.id);
    }

    @Override
    @Transactional
    public OrderResponse updateDraftOrderItemQuantity(Long id, Long itemId, UpdateDraftOrderItemQuantityRequest request) {
        Order order = requireItemEditableOrder(id);
        requireDraftForLegacyItemMutation(order);
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
        requireDraftForLegacyItemMutation(order);
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
        requireDraftForLegacyItemMutation(order);
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
        Order order = requireOrder(id);

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
        List<FrontdeskBeverageItem> beverageItems = createFrontdeskBeverageItems(order, orderItems, orderItemOptions, now);
        createProductionTasks(order, kitchenTasks, beverageItems, now);
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
        printDispatcherService.dispatchAfterCommit(PrintModuleCode.GRAB, order.store_id, order.id);
        printDispatcherService.dispatchAfterCommit(PrintModuleCode.FRONTDESK_RECEIPT, order.store_id, order.id);
        return loadOrderResponse(order.id);
    }

    @Override
    @Transactional
    public OrderUpdateResponse createOrderUpdate(Long id, CreateOrderUpdateRequest request, Long userId) {
        Order order = orderRepository.findByIdForUpdate(id);
        if (order == null) {
            throw new BusinessException("Order not found: " + id);
        }
        if (!MODIFIABLE_AFTER_SUBMIT_ORDER_STATUSES.contains(order.status)) {
            throw new BusinessException("Only submitted, preparing, or ready orders can receive an update batch");
        }

        String idempotencyKey = request.idempotency_key == null ? "" : request.idempotency_key.trim();
        if (idempotencyKey.isEmpty()) {
            throw new BusinessException("idempotency_key is required");
        }
        OrderUpdateBatch existingBatch = orderUpdateBatchRepository
            .findByOrderIdAndIdempotencyKey(order.id, idempotencyKey)
            .orElse(null);
        if (existingBatch != null) {
            return buildOrderUpdateResponse(order.id, existingBatch, true);
        }
        if (request.items == null || request.items.isEmpty()) {
            throw new BusinessException("At least one new item is required for Update Order");
        }

        LocalDateTime now = LocalDateTime.now();
        int currentRevision = order.current_revision == null ? 1 : order.current_revision;
        int nextRevision = currentRevision + 1;

        OrderUpdateBatch batch = new OrderUpdateBatch();
        batch.order_id = order.id;
        batch.revision = nextRevision;
        batch.idempotency_key = idempotencyKey;
        batch.created_by = userId;
        batch.created_at = now;
        batch = orderUpdateBatchRepository.save(batch);

        List<OrderItem> addedItems = new ArrayList<>();
        for (CreateOrderItemRequest itemRequest : request.items) {
            OrderItem addedItem = addDraftOrderItemInternal(order, itemRequest, now);
            addedItem.added_revision = nextRevision;
            addedItem.order_update_batch_id = batch.id;
            markSubmittedModification(addedItem, now);
            addedItems.add(orderItemRepository.save(addedItem));
        }

        List<OrderItemOption> addedOptions = loadOptionsForOrderItems(addedItems);
        List<KitchenTask> kitchenTasks = createKitchenTasks(order, addedItems, addedOptions, now);
        List<FrontdeskBeverageItem> beverageItems = createFrontdeskBeverageItems(order, addedItems, addedOptions, now);
        createProductionTasks(order, kitchenTasks, beverageItems, now);
        deductInventory(order, addedItems, addedOptions, now);

        order.current_revision = nextRevision;
        order.modified_after_submit_by = userId;
        markSubmittedModification(order, now);
        recalculateOrderAmounts(order, now);
        recalculateOrderStatusAfterSubmittedModification(order, now);
        publishOrderEvent("order.updated", order, null, null, null);

        printDispatcherService.dispatchOrderUpdateAfterCommit(
            PrintModuleCode.GRAB,
            order.store_id,
            order.id,
            batch.id
        );
        return buildOrderUpdateResponse(order.id, batch, false);
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
    public List<FrontdeskOrderBoardResponse> getFrontdeskTodayOrderHistory(Long storeId, Integer limit) {
        int resolvedLimit = limit == null ? 100 : Math.max(1, Math.min(limit, 200));
        LocalDate today = LocalDate.now();
        List<Order> orders = orderRepository.findTodayByStoreId(
            storeId,
            today.atStartOfDay(),
            today.plusDays(1).atStartOfDay(),
            PageRequest.of(0, resolvedLimit)
        );
        return buildFrontdeskOrderBoardResponses(orders);
    }

    @Override
    @Transactional
    public OrderResponse completeOrder(Long id) {
        Order order = requireOrder(id);

        if (ORDER_STATUS_COMPLETED.equals(order.status)) {
            throw new BusinessException("Order is already completed");
        }
        if (!ORDER_STATUS_READY.equals(order.status)
            && !ORDER_STATUS_PREPARING.equals(order.status)
            && !ORDER_STATUS_SUBMITTED.equals(order.status)) {
            throw new BusinessException("Only submitted, preparing, or ready orders can be completed");
        }

        LocalDateTime now = LocalDateTime.now();
        List<KitchenTask> tasks = kitchenTaskRepository.findAllByOrderId(order.id);
        for (KitchenTask task : tasks) {
            if (KitchenTaskStatus.cancelled.name().equals(task.status) || KitchenTaskStatus.served.name().equals(task.status)) {
                continue;
            }
            if (KitchenTaskStatus.ready_for_pickup.name().equals(task.status)) {
                task.status = KitchenTaskStatus.served.name();
                task.served_at = now;
            } else {
                task.status = KitchenTaskStatus.cancelled.name();
                task.cancelled_at = now;
            }
            kitchenTaskRepository.save(task);
        }

        List<FrontdeskBeverageItem> beverageItems = frontdeskBeverageItemRepository.findAllByOrderId(order.id);
        for (FrontdeskBeverageItem beverageItem : beverageItems) {
            if (BEVERAGE_STATUS_CANCELLED.equals(beverageItem.status) || BEVERAGE_STATUS_SERVED.equals(beverageItem.status)) {
                continue;
            }
            if (BEVERAGE_STATUS_READY.equals(beverageItem.status)) {
                beverageItem.status = BEVERAGE_STATUS_SERVED;
                beverageItem.served_at = now;
            } else {
                beverageItem.status = BEVERAGE_STATUS_CANCELLED;
                beverageItem.cancelled_at = now;
            }
            frontdeskBeverageItemRepository.save(beverageItem);
        }

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
        Order order = requireOrder(id);

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
        Order order = requireOrder(orderId);
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
        for (FrontdeskBeverageItem beverageItem : frontdeskBeverageItemRepository.findAllByOrderIds(orderIds)) {
            beverageItemsByOrderId.computeIfAbsent(beverageItem.order_id, key -> new ArrayList<>()).add(beverageItem);
        }

        List<FrontdeskOrderBoardResponse> responses = new ArrayList<>();
        for (Order order : orders) {
            List<OrderItem> orderLevelItems = orderItemsByOrderId.getOrDefault(order.id, List.of());
            List<KitchenTask> kitchenTasks = kitchenTasksByOrderId.getOrDefault(order.id, List.of());
            List<FrontdeskBeverageItem> beverageItems = beverageItemsByOrderId.getOrDefault(order.id, List.of());

            if (ORDER_STATUS_DRAFT.equals(order.status) && orderLevelItems.isEmpty()) {
                continue;
            }

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
            response.completed_at = order.completed_at;
            response.updated_at = order.updated_at;
            response.total_item_count = totalItemCount;
            response.ready_item_count = readyItemCount;
            response.beverage_pending_count = beveragePendingCount;
            response.kitchen_pending_count = kitchenPendingCount;
            response.total_amount = defaultIfNull(order.total_amount);
            responses.add(response);
        }
        return responses;
    }

    private Order requireDraftOrder(Long id) {
        Order order = requireOrder(id);
        if (!ORDER_STATUS_DRAFT.equals(order.status)) {
            throw new BusinessException("Only draft orders can be edited");
        }
        return order;
    }

    private Order requireItemEditableOrder(Long id) {
        Order order = requireOrder(id);
        if (ORDER_STATUS_COMPLETED.equals(order.status) || ORDER_STATUS_CANCELLED.equals(order.status)) {
            throw new BusinessException("Completed or cancelled orders cannot be modified");
        }
        if (!ORDER_STATUS_DRAFT.equals(order.status) && !MODIFIABLE_AFTER_SUBMIT_ORDER_STATUSES.contains(order.status)) {
            throw new BusinessException("Order status does not allow item modification: " + order.status);
        }
        return order;
    }

    private void requireDraftForLegacyItemMutation(Order order) {
        if (!ORDER_STATUS_DRAFT.equals(order.status)) {
            throw new BusinessException(
                "Submitted order items are locked. Add new items through POST /api/v1/orders/{orderId}/updates"
            );
        }
    }

    private OrderUpdateResponse buildOrderUpdateResponse(
        Long orderId,
        OrderUpdateBatch batch,
        boolean alreadyProcessed
    ) {
        OrderUpdateResponse response = new OrderUpdateResponse();
        response.order = loadOrderResponse(orderId);
        response.update_batch_id = batch.id;
        response.revision = batch.revision;
        response.already_processed = alreadyProcessed;
        return response;
    }

    private OrderItem requireEditableOrderItem(Order order, Long itemId) {
        OrderItem orderItem = requireOrderItem(itemId);
        if (!order.id.equals(orderItem.order_id)) {
            throw new BusinessException("Order item does not belong to order: " + itemId);
        }
        if (!isActiveOrderItem(orderItem)) {
            throw new BusinessException("Cancelled order item cannot be modified");
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
        orderItem.added_revision = order.current_revision == null ? 1 : order.current_revision;
        orderItem.order_update_batch_id = null;
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
        MenuItem mainMenuItem = menuItemRepository.findById(menuItemId)
            .orElseThrow(() -> new BusinessException("Menu item not found: " + menuItemId));
        Map<Long, MenuItemOption> requestedOptionsById = loadRequestedMenuItemOptions(optionRequests);
        Map<Long, Long> comboSideRemoveParentByOptionId = buildComboSideRemoveParentMap(mainMenuItem, requestedOptionsById);

        List<OrderItemOption> savedOptions = new ArrayList<>();
        for (CreateOrderItemOptionRequest optionRequest : optionRequests) {
            MenuItemOption menuItemOption = requestedOptionsById.get(optionRequest.option_id);
            if (menuItemOption == null) {
                throw new BusinessException("Menu item option not found: " + optionRequest.option_id);
            }

            Long comboSideParentOptionId = comboSideRemoveParentByOptionId.get(menuItemOption.id);
            validateOptionBelongsToMenuItem(menuItemOption, menuItemId, comboSideParentOptionId != null);

            OrderItemOption orderItemOption = new OrderItemOption();
            orderItemOption.order_item_id = orderItem.id;
            orderItemOption.option_id = menuItemOption.id;
            orderItemOption.option_type_snapshot = menuItemOption.option_type;
            orderItemOption.option_code_snapshot = menuItemOption.option_code;
            orderItemOption.option_group_snapshot = comboSideParentOptionId == null ? menuItemOption.option_group : "COMBO_SIDE_REMOVE";
            orderItemOption.parent_option_id_snapshot = comboSideParentOptionId == null ? menuItemOption.parent_option_id : comboSideParentOptionId;
            orderItemOption.option_name_snapshot_zh = menuItemOption.name_zh;
            orderItemOption.option_name_snapshot_en = menuItemOption.name_en;
            orderItemOption.price_delta = defaultIfNull(menuItemOption.price_delta);
            orderItemOption.quantity = optionRequest.quantity;
            orderItemOption.created_at = now;
            savedOptions.add(orderItemOptionRepository.save(orderItemOption));
        }
        return savedOptions;
    }

    private Map<Long, MenuItemOption> loadRequestedMenuItemOptions(List<CreateOrderItemOptionRequest> optionRequests) {
        Map<Long, MenuItemOption> optionsById = new HashMap<>();
        for (CreateOrderItemOptionRequest optionRequest : optionRequests) {
            if (optionRequest == null || optionRequest.option_id == null) {
                throw new BusinessException("Option id is required");
            }
            if (optionsById.containsKey(optionRequest.option_id)) {
                continue;
            }
            MenuItemOption option = menuItemOptionRepository.findById(optionRequest.option_id)
                .orElseThrow(() -> new BusinessException("Menu item option not found: " + optionRequest.option_id));
            optionsById.put(option.id, option);
        }
        return optionsById;
    }

    private Map<Long, Long> buildComboSideRemoveParentMap(MenuItem mainMenuItem, Map<Long, MenuItemOption> requestedOptionsById) {
        Map<Long, Long> allowedParentByRemoveOptionId = new HashMap<>();
        for (MenuItemOption selectedOption : requestedOptionsById.values()) {
            if (!mainMenuItem.id.equals(selectedOption.menu_item_id) || !isComboSideOption(selectedOption)) {
                continue;
            }
            String sideSku = resolveComboSideSku(selectedOption.option_code, selectedOption.name_zh);
            if (sideSku == null) {
                continue;
            }
            MenuItem sideItem = menuItemRepository.findAll().stream()
                .filter(item -> mainMenuItem.store_id.equals(item.store_id))
                .filter(item -> sideSku.equals(item.sku))
                .findFirst()
                .orElse(null);
            if (sideItem == null) {
                continue;
            }
            menuItemOptionRepository.findAllByMenuItemIdOrdered(sideItem.id).stream()
                .filter(this::isRemoveMenuOption)
                .forEach(removeOption -> allowedParentByRemoveOptionId.put(removeOption.id, selectedOption.id));
        }
        return allowedParentByRemoveOptionId;
    }

    private void recalculateOrderAmounts(Order order, LocalDateTime now) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem orderItem : orderItemRepository.findAllByOrderId(order.id).stream().filter(this::isActiveOrderItem).toList()) {
            subtotal = subtotal.add(defaultIfNull(orderItem.line_amount));
        }
        order.subtotal_amount = subtotal;
        order.discount_amount = BigDecimal.ZERO;
        order.total_amount = TaxCalculator.calculateTotal(subtotal);
        order.updated_at = now;
        orderRepository.save(order);
    }

    private List<FrontdeskBeverageItem> createFrontdeskBeverageItems(
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
            return frontdeskBeverageItemRepository.saveAll(beverageItems);
        }
        return List.of();
    }

    private void createProductionTasks(
        Order order,
        List<KitchenTask> kitchenTasks,
        List<FrontdeskBeverageItem> beverageItems,
        LocalDateTime now
    ) {
        List<ProductionTask> productionTasks = new ArrayList<>();

        for (KitchenTask kitchenTask : kitchenTasks) {
            ProductionTask productionTask = new ProductionTask();
            productionTask.order_id = order.id;
            productionTask.order_item_id = kitchenTask.order_item_id;
            productionTask.store_id = order.store_id;
            productionTask.source_type = PRODUCTION_SOURCE_KITCHEN_TASK;
            productionTask.source_id = kitchenTask.id;
            productionTask.station_code = kitchenTask.station_code;
            productionTask.item_name_snapshot_zh = kitchenTask.item_name_snapshot_zh;
            productionTask.item_name_snapshot_en = kitchenTask.item_name_snapshot_en;
            productionTask.special_instructions_snapshot = kitchenTask.special_instructions_snapshot;
            productionTask.status = kitchenTask.status;
            productionTask.quantity = kitchenTask.quantity;
            productionTask.priority = kitchenTask.priority;
            productionTask.started_at = kitchenTask.started_at;
            productionTask.completed_at = kitchenTask.completed_at;
            productionTask.served_at = kitchenTask.served_at;
            productionTask.cancelled_at = kitchenTask.cancelled_at;
            productionTask.created_at = kitchenTask.created_at;
            productionTask.updated_at = now;
            productionTasks.add(productionTask);
        }

        for (FrontdeskBeverageItem beverageItem : beverageItems) {
            ProductionTask productionTask = new ProductionTask();
            productionTask.order_id = order.id;
            productionTask.order_item_id = beverageItem.order_item_id;
            productionTask.store_id = order.store_id;
            productionTask.source_type = PRODUCTION_SOURCE_FRONTDESK_BEVERAGE;
            productionTask.source_id = beverageItem.id;
            productionTask.station_code = PRODUCTION_STATION_FRONTDESK_BEVERAGE;
            productionTask.item_name_snapshot_zh = beverageItem.item_name_snapshot_zh;
            productionTask.item_name_snapshot_en = beverageItem.item_name_snapshot_en;
            productionTask.special_instructions_snapshot = beverageItem.special_instructions_snapshot;
            productionTask.status = beverageItem.status;
            productionTask.quantity = beverageItem.quantity;
            productionTask.priority = null;
            productionTask.started_at = beverageItem.started_at;
            productionTask.ready_at = beverageItem.ready_at;
            productionTask.served_at = beverageItem.served_at;
            productionTask.cancelled_at = beverageItem.cancelled_at;
            productionTask.created_at = beverageItem.created_at;
            productionTask.updated_at = now;
            productionTasks.add(productionTask);
        }

        if (!productionTasks.isEmpty()) {
            productionTaskRepository.saveAll(productionTasks);
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
        MenuItem menuItem = menuItemRepository.findById(orderItem.menu_item_id)
            .orElseThrow(() -> new BusinessException("Menu item not found for kitchen task: " + orderItem.menu_item_id));
        if (kitchenTask == null) {
            kitchenTask = createKitchenTaskForOrderItem(order, orderItem, orderItemOptions, menuItem, now);
        } else {
            if (KitchenTaskStatus.cancelled.name().equals(kitchenTask.status)
                || KitchenTaskStatus.ready_for_pickup.name().equals(kitchenTask.status)
                || KitchenTaskStatus.served.name().equals(kitchenTask.status)) {
                kitchenTask.status = KitchenTaskStatus.pending.name();
                kitchenTask.cancelled_at = null;
                kitchenTask.started_at = null;
                kitchenTask.completed_at = null;
                kitchenTask.served_at = null;
            }
            kitchenTask.item_name_snapshot_zh = buildKitchenDisplayNameZh(menuItem, orderItem);
            kitchenTask.item_name_snapshot_en = orderItem.item_name_snapshot_en;
            kitchenTask.quantity = orderItem.quantity;
            kitchenTask.special_instructions_snapshot = buildSpecialInstructionsSnapshot(menuItem, orderItem, orderItemOptions);
            kitchenTaskRepository.save(kitchenTask);
        }

        synchronizeComboSideKitchenTasks(order, orderItem, orderItemOptions, now);
    }

    private KitchenTask createKitchenTaskForOrderItem(
        Order order,
        OrderItem orderItem,
        List<OrderItemOption> orderItemOptions,
        MenuItem menuItem,
        LocalDateTime now
    ) {
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
        kitchenTask.item_name_snapshot_zh = buildKitchenDisplayNameZh(menuItem, orderItem);
        kitchenTask.item_name_snapshot_en = orderItem.item_name_snapshot_en;
        kitchenTask.special_instructions_snapshot = buildSpecialInstructionsSnapshot(menuItem, orderItem, orderItemOptions);
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

        for (KitchenTask kitchenTask : findKitchenTasksByOrderItemId(order.id, orderItem.id)) {
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
    }

    private KitchenTask findKitchenTaskByOrderItemId(Long orderId, Long orderItemId) {
        List<KitchenTask> tasks = findKitchenTasksByOrderItemId(orderId, orderItemId);
        return tasks.stream()
            .filter(task -> !isComboSideKitchenTask(task))
            .findFirst()
            .orElseGet(() -> tasks.stream().findFirst().orElse(null));
    }

    private List<KitchenTask> findKitchenTasksByOrderItemId(Long orderId, Long orderItemId) {
        Long resolvedOrderId = orderId == null ? orderItemsOrderId(orderItemId) : orderId;
        return kitchenTaskRepository.findAllByOrderId(resolvedOrderId).stream()
            .filter(task -> orderItemId.equals(task.order_item_id))
            .toList();
    }

    private Long orderItemsOrderId(Long orderItemId) {
        OrderItem orderItem = requireOrderItem(orderItemId);
        return orderItem.order_id;
    }

    private Order requireOrder(Long id) {
        Order order = orderRepository.findExistingById(id);
        if (order == null) {
            throw new BusinessException("Order not found: " + id);
        }
        return order;
    }

    private Order findExistingEditableOrder(Long storeId, String tableNo, String pickupNo) {
        if (tableNo != null && !tableNo.isBlank()) {
            return orderRepository.findLatestEditableByStoreIdAndTableNo(storeId, tableNo);
        }
        if (pickupNo != null && !pickupNo.isBlank()) {
            return orderRepository.findLatestEditableByStoreIdAndPickupNo(storeId, pickupNo);
        }
        return null;
    }

    private OrderItem requireOrderItem(Long itemId) {
        OrderItem orderItem = orderItemRepository.findExistingById(itemId);
        if (orderItem == null) {
            throw new BusinessException("Order item not found: " + itemId);
        }
        return orderItem;
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
            kitchenTask.item_name_snapshot_zh = buildKitchenDisplayNameZh(menuItem, orderItem);
            kitchenTask.item_name_snapshot_en = orderItem.item_name_snapshot_en;
            kitchenTask.special_instructions_snapshot = buildSpecialInstructionsSnapshot(
                menuItem,
                orderItem,
                optionsByOrderItemId.getOrDefault(orderItem.id, List.of())
            );
            kitchenTask.status = KitchenTaskStatus.pending.name();
            kitchenTask.quantity = orderItem.quantity;
            kitchenTask.priority = null;
            kitchenTask.created_at = now;
            kitchenTasks.add(kitchenTask);

            kitchenTasks.addAll(createComboSideKitchenTasks(
                order,
                orderItem,
                optionsByOrderItemId.getOrDefault(orderItem.id, List.of()),
                now
            ));
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
        validateOptionBelongsToMenuItem(menuItemOption, menuItemId, false);
    }

    private void validateOptionBelongsToMenuItem(MenuItemOption menuItemOption, Long menuItemId, boolean allowComboSideRemove) {
        if (menuItemOption.menu_item_id == null || !menuItemOption.menu_item_id.equals(menuItemId)) {
            if (!allowComboSideRemove) {
                throw new BusinessException("Menu item option does not belong to menu item: " + menuItemOption.id);
            }
            if (!isRemoveMenuOption(menuItemOption)) {
                throw new BusinessException("Only combo side remove options can reference another menu item: " + menuItemOption.id);
            }
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
            response.option_code_snapshot = orderItemOption.option_code_snapshot;
            response.option_group_snapshot = orderItemOption.option_group_snapshot;
            response.parent_option_id_snapshot = orderItemOption.parent_option_id_snapshot;
            response.option_name_snapshot_zh = orderItemOption.option_name_snapshot_zh;
            response.option_name_snapshot_en = orderItemOption.option_name_snapshot_en;
            response.price_delta = defaultIfNull(orderItemOption.price_delta);
            response.quantity = orderItemOption.quantity;
            optionsByOrderItemId.computeIfAbsent(orderItemOption.order_item_id, key -> new ArrayList<>()).add(response);
        }

        Map<Long, List<KitchenTask>> tasksByOrderItemId = new HashMap<>();
        for (KitchenTask kitchenTask : kitchenTasks) {
            tasksByOrderItemId.computeIfAbsent(kitchenTask.order_item_id, key -> new ArrayList<>()).add(kitchenTask);
        }
        Map<Long, FrontdeskBeverageItem> beverageItemByOrderItemId = new HashMap<>();
        for (FrontdeskBeverageItem beverageItem : beverageItems) {
            beverageItemByOrderItemId.put(beverageItem.order_item_id, beverageItem);
        }

        List<OrderItemResponse> itemResponses = orderItems.stream().map(orderItem -> {
            KitchenTask kitchenTask = tasksByOrderItemId.getOrDefault(orderItem.id, List.of()).stream()
                .filter(task -> !isComboSideKitchenTask(task))
                .findFirst()
                .orElseGet(() -> tasksByOrderItemId.getOrDefault(orderItem.id, List.of()).stream().findFirst().orElse(null));
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
            response.added_revision = orderItem.added_revision;
            response.order_update_batch_id = orderItem.order_update_batch_id;
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
        response.current_revision = order.current_revision == null ? 1 : order.current_revision;
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

    private String buildSpecialInstructionsSnapshot(MenuItem menuItem, OrderItem orderItem, List<OrderItemOption> orderItemOptions) {
        String primaryLine = buildKitchenPrimaryLine(menuItem, orderItemOptions);
        List<String> secondaryParts = buildKitchenSecondaryParts(orderItem, orderItemOptions);

        List<String> lines = new ArrayList<>();
        if (primaryLine != null && !primaryLine.isBlank()) {
            lines.add(primaryLine);
        }
        if (!secondaryParts.isEmpty()) {
            lines.add(String.join(" ", aggregateKitchenSecondaryParts(secondaryParts)));
        }
        return lines.isEmpty() ? null : String.join(" | ", lines);
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

    private List<String> aggregateKitchenSecondaryParts(List<String> values) {
        Map<String, Integer> addonQuantities = new LinkedHashMap<>();
        List<String> orderedKeys = new ArrayList<>();
        Set<String> seenOther = new HashSet<>();

        for (String value : values) {
            KitchenAddonToken addonToken = parseKitchenAddonToken(value);
            if (addonToken != null) {
                String key = "ADDON:" + addonToken.label();
                if (!addonQuantities.containsKey(key)) {
                    orderedKeys.add(key);
                    addonQuantities.put(key, 0);
                }
                addonQuantities.put(key, addonQuantities.get(key) + addonToken.quantity());
                continue;
            }

            if (value == null || value.isBlank() || !seenOther.add(value)) {
                continue;
            }
            orderedKeys.add("RAW:" + value);
        }

        List<String> result = new ArrayList<>();
        for (String key : orderedKeys) {
            if (key.startsWith("ADDON:")) {
                String label = key.substring("ADDON:".length());
                int quantity = addonQuantities.getOrDefault(key, 1);
                result.add(quantity > 1 ? label + "x" + quantity : label);
            } else if (key.startsWith("RAW:")) {
                result.add(key.substring("RAW:".length()));
            }
        }
        return result;
    }

    private KitchenAddonToken parseKitchenAddonToken(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || !trimmed.startsWith("+")) {
            return null;
        }
        int quantity = 1;
        String label = trimmed;
        int markerIndex = Math.max(trimmed.lastIndexOf('x'), trimmed.lastIndexOf('*'));
        if (markerIndex > 0 && markerIndex < trimmed.length() - 1) {
            String quantityText = trimmed.substring(markerIndex + 1);
            if (quantityText.chars().allMatch(Character::isDigit)) {
                quantity = Integer.parseInt(quantityText);
                label = trimmed.substring(0, markerIndex);
            }
        }
        return new KitchenAddonToken(label, quantity);
    }

    private String buildKitchenDisplayNameZh(MenuItem menuItem, OrderItem orderItem) {
        return switch (menuItem.sku) {
            case "beef_chow_mein" -> "牛炒";
            case "chicken_chow_mein" -> "鸡炒";
            case "tomato_chow_mein" -> "番炒";
            case "vegetable_chow_mein" -> "素炒";
            case "zha_jiang_noodle" -> "炸";
            case "dan_dan_noodle" -> "担";
            case "cold_noodle_shredded_chicken" -> "鸡凉";
            default -> orderItem.item_name_snapshot_zh;
        };
    }

    private String buildKitchenPrimaryLine(MenuItem menuItem, List<OrderItemOption> options) {
        String sizeCode = mapSizeCode(findOptionZh(options, OPTION_TYPE_SIZE));
        String baseCode = mapItemBaseCode(menuItem.sku);
        String noodleCode = mapNoodleCode(menuItem.sku, findOptionZh(options, OPTION_TYPE_NOODLE_TYPE));
        String spicyCode = mapSpicyCode(findOptionZh(options, OPTION_TYPE_SPICY_LEVEL));
        String soupBaseCode = mapSoupBaseCode(menuItem.sku, findOptionZh(options, OPTION_TYPE_SOUP_BASE));

        List<String> inline = new ArrayList<>();
        if (sizeCode != null) {
            inline.add(sizeCode);
        }
        if (baseCode != null) {
            inline.add(baseCode);
        }
        if (soupBaseCode != null) {
            inline.add(soupBaseCode);
        }
        if (noodleCode != null) {
            inline.add(noodleCode);
        }

        String primary = String.join("", inline);
        if (primary.isBlank()) {
            primary = null;
        }
        if (spicyCode != null) {
            primary = (primary == null ? "" : primary) + spicyCode;
        }
        return primary;
    }

    private String mapItemBaseCode(String sku) {
        return switch (sku) {
            case "braised_beef_tendon_noodle" -> "红";
            case "pickled_vegetable_beef_noodle" -> "酸";
            case "beef_chow_mein" -> "牛炒";
            case "chicken_chow_mein" -> "鸡炒";
            case "tomato_chow_mein" -> "番炒";
            case "vegetable_chow_mein" -> "素炒";
            case "zha_jiang_noodle" -> "炸";
            case "dan_dan_noodle" -> "担";
            case "cold_noodle_shredded_chicken" -> "鸡凉";
            case "cucumber_salad" -> "黄瓜";
            case "edamame" -> "毛豆";
            case "shredded_potato" -> "土豆";
            case "braised_beef_shank_salad" -> "牛展";
            default -> null;
        };
    }

    private List<String> buildKitchenSecondaryParts(OrderItem orderItem, List<OrderItemOption> options) {
        List<String> parts = new ArrayList<>();
        for (OrderItemOption option : options) {
            if (OPTION_TYPE_ADDON.equals(option.option_type_snapshot)) {
                String addonCode = canonicalAddonCode(option.option_name_snapshot_zh);
                if (isComboSideCode(addonCode)) {
                    continue;
                }
                String token = mapAddonToken(option);
                if (token != null) {
                    parts.add(token);
                }
                continue;
            }
            if (OPTION_TYPE_REMOVE.equals(option.option_type_snapshot)) {
                if (isOptionGroup(option, "COMBO_SIDE_REMOVE")) {
                    continue;
                }
                String token = mapRemoveToken(option);
                if (token != null) {
                    parts.add(token);
                }
            }
        }
        return parts;
    }

    private String findOptionZh(List<OrderItemOption> options, String optionType) {
        for (OrderItemOption option : options) {
            if (optionType.equals(option.option_type_snapshot)) {
                return option.option_name_snapshot_zh;
            }
        }
        return null;
    }

    private String mapSizeCode(String sizeZh) {
        if (sizeZh == null || sizeZh.isBlank()) {
            return null;
        }
        if (sizeZh.contains("大")) {
            return "大";
        }
        return "中";
    }

    private String mapNoodleCode(String sku, String noodleZh) {
        if (noodleZh == null || noodleZh.isBlank()) {
            return null;
        }
        if (isDefaultNoodleType(sku, noodleZh)) {
            return null;
        }
        return switch (noodleZh) {
            case "二细" -> "二";
            case "三细" -> "三";
            case "细" -> "细";
            case "毛细" -> "毛";
            case "韭叶" -> "韭";
            case "宽" -> "宽";
            case "大宽" -> "大宽";
            default -> noodleZh;
        };
    }

    private boolean isDefaultNoodleType(String sku, String noodleZh) {
        return switch (sku) {
            case "traditional_beef_noodle",
                 "braised_beef_tendon_noodle",
                 "pickled_vegetable_beef_noodle",
                 "vegetable_noodle",
                 "dan_dan_noodle" -> "三细".equals(noodleZh);
            case "zha_jiang_noodle",
                 "cold_noodle_shredded_chicken" -> "韭叶".equals(noodleZh);
            default -> false;
        };
    }

    private String mapSpicyCode(String spicyZh) {
        if (spicyZh == null || spicyZh.isBlank() || "不辣".equals(spicyZh)) {
            return null;
        }
        return switch (spicyZh) {
            case "少辣" -> "（少s）";
            case "正常辣" -> "（s）";
            case "加辣" -> "（大s）";
            default -> "（s）";
        };
    }

    private String mapSoupBaseCode(String sku, String soupBaseZh) {
        if (!"vegetable_noodle".equals(sku)) {
            return null;
        }
        if (soupBaseZh == null || soupBaseZh.isBlank()) {
            return "素";
        }
        if ("素汤".equals(soupBaseZh)) {
            return "素";
        }
        if ("肉汤".equals(soupBaseZh) || "牛汤".equals(soupBaseZh)) {
            return "素（肉汤）";
        }
        return null;
    }

    private String mapAddonToken(OrderItemOption option) {
        String label = option.option_name_snapshot_zh;
        String code = resolveAddonCode(option);
        if (code == null || "combo".equals(code)) {
            return null;
        }
        String mapped = switch (code) {
            case "extra_noodle" -> "+面";
            case "tea_egg", "combo_tea_egg" -> "+蛋";
            case "fried_egg", "combo_fried_egg" -> "+煎";
            case "extra_meat" -> "+肉";
            case "extra_radish" -> "+萝";
            case "bok_choy" -> "+青";
            case "cilantro" -> "+香";
            case "green_onion" -> "+葱";
            case "extra_sauce" -> "+酱";
            case "broccoli" -> "+西兰";
            case "cabbage" -> "+包";
            case "corn" -> "+玉";
            case "seaweed" -> "+海";
            case "mushroom" -> "+菇";
            case "carrot_slice" -> "+胡";
            case "combo_edamame" -> "+毛豆";
            case "combo_shredded_potato" -> "+土豆";
            case "combo_cucumber_salad" -> "+黄瓜";
            default -> null;
        };
        if (mapped == null) {
            return null;
        }
        int quantity = option.quantity == null ? 1 : option.quantity;
        return quantity > 1 ? mapped + "x" + quantity : mapped;
    }

    private String mapRemoveToken(OrderItemOption option) {
        String label = option.option_name_snapshot_zh;
        String code = resolveRemoveCode(option);
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "cilantro" -> "走香";
            case "green_onion" -> "走葱";
            case "beef" -> "走牛";
            case "radish" -> "走萝";
            case "noodle" -> "走面";
            case "less_noodle" -> "少面";
            case "bok_choy" -> "走青";
            case "broccoli" -> "走西兰";
            case "corn" -> "走玉米";
            case "mushroom" -> "走菇";
            case "seaweed" -> "走海";
            case "carrot" -> "走胡";
            case "cucumber" -> "走黄瓜";
            case "edamame" -> "走毛豆";
            case "peanut" -> "走花生";
            case "cabbage" -> "走包";
            case "meat" -> "走肉";
            case "green_pepper" -> "走青椒";
            default -> label;
        };
    }

    private String resolveAddonCode(OrderItemOption option) {
        if (option.option_code_snapshot != null && !option.option_code_snapshot.isBlank()) {
            return option.option_code_snapshot;
        }
        // Legacy fallback for orders/options created before stable option_code metadata existed.
        return canonicalAddonCode(option.option_name_snapshot_zh);
    }

    private String resolveRemoveCode(OrderItemOption option) {
        if (option.option_code_snapshot != null && !option.option_code_snapshot.isBlank()) {
            String code = option.option_code_snapshot;
            return code.startsWith("remove_") ? code.substring("remove_".length()) : code;
        }
        // Legacy fallback for orders/options created before stable option_code metadata existed.
        return canonicalRemoveCode(option.option_name_snapshot_zh);
    }

    private String canonicalAddonCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return switch (label) {
            case "套餐" -> "combo";
            case "加面" -> "extra_noodle";
            case "加蛋", "套餐卤蛋" -> "tea_egg";
            case "加煎蛋", "套餐煎蛋" -> "fried_egg";
            case "加肉" -> "extra_meat";
            case "加萝卜" -> "extra_radish";
            case "加上海青" -> "bok_choy";
            case "加香菜" -> "cilantro";
            case "加葱" -> "green_onion";
            case "加酱" -> "extra_sauce";
            case "加西兰花" -> "broccoli";
            case "加包菜" -> "cabbage";
            case "加玉米" -> "corn";
            case "加海菜" -> "seaweed";
            case "加蘑菇" -> "mushroom";
            case "加胡萝卜片" -> "carrot_slice";
            case "套餐毛豆" -> "combo_edamame";
            case "套餐土豆丝" -> "combo_shredded_potato";
            case "套餐拌黄瓜" -> "combo_cucumber_salad";
            default -> null;
        };
    }

    private boolean isComboSideKitchenTask(KitchenTask task) {
        return task != null && Integer.valueOf(KITCHEN_TASK_PRIORITY_COMBO_SIDE).equals(task.priority);
    }

    private record ComboSideSelection(String code, String labelZh, String labelEn, int quantity, List<String> instructions) {
    }

    private record KitchenAddonToken(String label, int quantity) {
    }

    private List<ComboSideSelection> extractComboSideSelections(OrderItem orderItem, List<OrderItemOption> options) {
        Map<String, ComboSideSelection> selections = new HashMap<>();
        for (OrderItemOption option : options) {
            if (!OPTION_TYPE_ADDON.equals(option.option_type_snapshot) && !isOptionGroup(option, "COMBO_SIDE")) {
                continue;
            }
            String code = resolveAddonCode(option);
            if (!isComboSideCode(code)) {
                continue;
            }
            int optionQuantity = option.quantity == null ? 1 : option.quantity;
            int totalQuantity = optionQuantity * (orderItem.quantity == null ? 1 : orderItem.quantity);
            List<String> childInstructions = extractComboSideChildInstructions(option, options);
            ComboSideSelection selection = switch (code) {
                case "combo_edamame" -> new ComboSideSelection(code, "毛豆", "Edamame", totalQuantity, childInstructions);
                case "combo_shredded_potato" -> new ComboSideSelection(code, "土豆", "Shredded Potato", totalQuantity, childInstructions);
                case "combo_cucumber_salad" -> new ComboSideSelection(code, "黄瓜", "Cucumber Salad", totalQuantity, childInstructions);
                default -> null;
            };
            if (selection == null) {
                continue;
            }
            selections.put(selection.code(), selection);
        }
        return new ArrayList<>(selections.values());
    }

    private List<String> extractComboSideChildInstructions(OrderItemOption sideOption, List<OrderItemOption> options) {
        List<String> instructions = new ArrayList<>();
        for (OrderItemOption option : options) {
            if (!isOptionGroup(option, "COMBO_SIDE_REMOVE")) {
                continue;
            }
            if (sideOption.option_id == null || !sideOption.option_id.equals(option.parent_option_id_snapshot)) {
                continue;
            }
            String token = mapRemoveToken(option);
            if (token != null) {
                instructions.add(token);
            }
        }
        return instructions;
    }

    private List<KitchenTask> createComboSideKitchenTasks(
        Order order,
        OrderItem orderItem,
        List<OrderItemOption> options,
        LocalDateTime now
    ) {
        List<ComboSideSelection> selections = extractComboSideSelections(orderItem, options);
        if (selections.isEmpty()) {
            return List.of();
        }
        Station coldStation = stationRepository.findActiveStationByCodeAndStoreId("COLD", order.store_id);
        if (coldStation == null) {
            throw new BusinessException("Assigned station is not enabled for combo side items. station_code=COLD");
        }

        List<KitchenTask> tasks = new ArrayList<>();
        for (ComboSideSelection selection : selections) {
            KitchenTask kitchenTask = new KitchenTask();
            kitchenTask.order_id = order.id;
            kitchenTask.order_item_id = orderItem.id;
            kitchenTask.store_id = order.store_id;
            kitchenTask.station_code = coldStation.code;
            kitchenTask.item_name_snapshot_zh = selection.labelZh();
            kitchenTask.item_name_snapshot_en = selection.labelEn();
            kitchenTask.special_instructions_snapshot = selection.instructions().isEmpty()
                ? null
                : String.join(" ", selection.instructions());
            kitchenTask.status = KitchenTaskStatus.pending.name();
            kitchenTask.quantity = selection.quantity();
            kitchenTask.priority = KITCHEN_TASK_PRIORITY_COMBO_SIDE;
            kitchenTask.created_at = now;
            tasks.add(kitchenTask);
        }
        return tasks;
    }

    private void synchronizeComboSideKitchenTasks(
        Order order,
        OrderItem orderItem,
        List<OrderItemOption> options,
        LocalDateTime now
    ) {
        List<KitchenTask> existingTasks = findKitchenTasksByOrderItemId(order.id, orderItem.id).stream()
            .filter(this::isComboSideKitchenTask)
            .toList();
        Map<String, KitchenTask> existingByLabel = new HashMap<>();
        for (KitchenTask task : existingTasks) {
            existingByLabel.put(task.item_name_snapshot_zh, task);
        }

        Map<String, ComboSideSelection> desiredByLabel = new HashMap<>();
        for (ComboSideSelection selection : extractComboSideSelections(orderItem, options)) {
            desiredByLabel.put(selection.labelZh(), selection);
        }

        Station coldStation = null;
        if (!desiredByLabel.isEmpty()) {
            coldStation = stationRepository.findActiveStationByCodeAndStoreId("COLD", order.store_id);
            if (coldStation == null) {
                throw new BusinessException("Assigned station is not enabled for combo side items. station_code=COLD");
            }
        }

        for (ComboSideSelection selection : desiredByLabel.values()) {
            KitchenTask task = existingByLabel.get(selection.labelZh());
            if (task == null) {
                task = new KitchenTask();
                task.order_id = order.id;
                task.order_item_id = orderItem.id;
                task.store_id = order.store_id;
                task.created_at = now;
                task.priority = KITCHEN_TASK_PRIORITY_COMBO_SIDE;
            }
            if (KitchenTaskStatus.cancelled.name().equals(task.status)
                || KitchenTaskStatus.ready_for_pickup.name().equals(task.status)
                || KitchenTaskStatus.served.name().equals(task.status)) {
                task.status = KitchenTaskStatus.pending.name();
                task.cancelled_at = null;
                task.started_at = null;
                task.completed_at = null;
                task.served_at = null;
            }
            task.station_code = coldStation.code;
            task.item_name_snapshot_zh = selection.labelZh();
            task.item_name_snapshot_en = selection.labelEn();
            task.special_instructions_snapshot = selection.instructions().isEmpty()
                ? null
                : String.join(" ", selection.instructions());
            task.quantity = selection.quantity();
            kitchenTaskRepository.save(task);
        }

        for (KitchenTask task : existingTasks) {
            if (desiredByLabel.containsKey(task.item_name_snapshot_zh)) {
                continue;
            }
            if (KitchenTaskStatus.served.name().equals(task.status)) {
                continue;
            }
            task.status = KitchenTaskStatus.cancelled.name();
            task.cancelled_at = now;
            kitchenTaskRepository.save(task);
        }
    }

    private boolean isComboSideCode(String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.toLowerCase();
        return normalized.contains("combo_edamame")
            || normalized.contains("combo_shredded_potato")
            || normalized.contains("combo_cucumber_salad");
    }

    private boolean isComboSideOption(MenuItemOption option) {
        if (option.option_group != null && "COMBO_SIDE".equalsIgnoreCase(option.option_group)) {
            return true;
        }
        String code = resolveComboSideSku(option.option_code, option.name_zh);
        return code != null;
    }

    private boolean isRemoveMenuOption(MenuItemOption option) {
        if (option.option_group != null && "REMOVE".equalsIgnoreCase(option.option_group)) {
            return true;
        }
        return option.option_group == null
            && option.option_type != null
            && OPTION_TYPE_REMOVE.equalsIgnoreCase(option.option_type);
    }

    private String resolveComboSideSku(String optionCode, String nameZh) {
        if (optionCode != null && !optionCode.isBlank()) {
            String code = optionCode.trim().toLowerCase();
            if (code.contains("combo_shredded_potato")) {
                return "shredded_potato";
            }
            if (code.contains("combo_cucumber_salad")) {
                return "cucumber_salad";
            }
            if (code.contains("combo_edamame")) {
                return "edamame";
            }
            return switch (code) {
                case "edamame" -> "edamame";
                case "shredded_potato" -> "shredded_potato";
                case "cucumber_salad" -> "cucumber_salad";
                default -> null;
            };
        }
        // Legacy fallback for older menu option metadata.
        return switch (nameZh == null ? "" : nameZh.trim()) {
            case "套餐毛豆" -> "edamame";
            case "套餐土豆丝" -> "shredded_potato";
            case "套餐拌黄瓜" -> "cucumber_salad";
            default -> null;
        };
    }

    private boolean isOptionGroup(OrderItemOption option, String group) {
        return option.option_group_snapshot != null && group.equalsIgnoreCase(option.option_group_snapshot);
    }

    private String canonicalRemoveCode(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        return switch (label) {
            case "走香菜", "不要香菜" -> "cilantro";
            case "走葱", "不要葱", "走洋葱" -> "green_onion";
            case "走牛肉" -> "beef";
            case "走萝卜" -> "radish";
            case "走面", "No Noodle" -> "noodle";
            case "少面" -> "less_noodle";
            case "走上海青" -> "bok_choy";
            case "走西兰花" -> "broccoli";
            case "走玉米" -> "corn";
            case "走蘑菇" -> "mushroom";
            case "走海菜" -> "seaweed";
            case "走胡萝卜片", "走胡萝卜" -> "carrot";
            case "走黄瓜" -> "cucumber";
            case "走毛豆" -> "edamame";
            case "走花生", "走花生碎" -> "peanut";
            case "走包菜" -> "cabbage";
            case "走肉" -> "meat";
            case "走青椒" -> "green_pepper";
            default -> null;
        };
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
