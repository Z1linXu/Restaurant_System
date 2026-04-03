package com.restaurant.system.kitchen.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.kitchen.dto.FrontdeskBeverageOrderResponse;
import com.restaurant.system.kitchen.dto.KdsOrderGroupResponse;
import com.restaurant.system.kitchen.dto.KdsOrderItemStatusResponse;
import com.restaurant.system.kitchen.dto.KdsTaskDisplayResponse;
import com.restaurant.system.kitchen.dto.ServingShelfItemResponse;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.kitchen.service.KdsService;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.order.repository.OrderItemOptionRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class KdsServiceImpl implements KdsService {

    private static final String STATION_NOODLE = "NOODLE";
    private static final String STATION_WOK = "WOK";
    private static final String STATION_COLD = "COLD";
    private static final String STATION_DEEPFRIED = "DEEPFRIED";
    private static final String STATION_GROUP_ASSEMBLING = "ASSEMBLING";
    private static final int KITCHEN_TASK_PRIORITY_COMBO_SIDE = 100;
    private static final List<String> ASSEMBLING_STATIONS = List.of(STATION_NOODLE, STATION_WOK, STATION_COLD, STATION_DEEPFRIED);
    private static final List<String> HOT_STATIONS = List.of("WOK", "DEEPFRIED");
    private static final Set<String> FRONTDESK_CODES = Set.of("DRINK", "ALCOHOL", "MILK_TEA");
    private static final Set<String> EXTRA_FLAG_OPTION_TYPES = Set.of("addon", "remove");

    private final KitchenTaskRepository kitchenTaskRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemOptionRepository orderItemOptionRepository;

    public KdsServiceImpl(
        KitchenTaskRepository kitchenTaskRepository,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        OrderItemOptionRepository orderItemOptionRepository
    ) {
        this.kitchenTaskRepository = kitchenTaskRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemOptionRepository = orderItemOptionRepository;
    }

    private boolean isComboSideTask(KitchenTask task) {
        return task != null && Integer.valueOf(KITCHEN_TASK_PRIORITY_COMBO_SIDE).equals(task.priority);
    }

    @Override
    public List<KdsTaskDisplayResponse> getNoodleDisplay(Long storeId, Integer limit) {
        int maxOrders = limit == null || limit < 1 ? 10 : limit;
        List<KitchenTask> activeTasks = kitchenTaskRepository.findActiveTasksByStoreIdAndStationCodes(
            storeId,
            ASSEMBLING_STATIONS
        );

        Set<Long> visibleOrderIds = new HashSet<>();
        List<KitchenTask> visibleTasks = new ArrayList<>();
        for (KitchenTask task : activeTasks) {
            if (visibleOrderIds.size() >= maxOrders && !visibleOrderIds.contains(task.order_id)) {
                continue;
            }
            visibleOrderIds.add(task.order_id);
        }
        if (visibleOrderIds.isEmpty()) {
            return List.of();
        }
        visibleTasks = kitchenTaskRepository.findVisibleTasksByStoreIdAndStationCodesAndOrderIds(
            storeId,
            ASSEMBLING_STATIONS,
            new ArrayList<>(visibleOrderIds)
        );
        return buildTaskDisplayResponses(visibleTasks);
    }

    @Override
    public List<KdsTaskDisplayResponse> getHotKitchenDisplay(Long storeId) {
        return buildTaskDisplayResponses(
            kitchenTaskRepository.findActiveTasksByStoreIdAndStationCodes(storeId, HOT_STATIONS)
        );
    }

    @Override
    public List<KdsOrderGroupResponse> getPassView(Long storeId) {
        return buildOrderGroups(orderRepository.findActiveOperationalOrders(storeId));
    }

    @Override
    public List<FrontdeskBeverageOrderResponse> getFrontdeskBeverageView(Long storeId) {
        List<Order> activeOrders = new ArrayList<>(orderRepository.findActiveOperationalOrders(storeId));
        List<Order> recentOrders = orderRepository.findRecentFinishedOrders(storeId, PageRequest.of(0, 20));
        for (Order order : recentOrders) {
            if (activeOrders.stream().noneMatch(existing -> existing.id.equals(order.id))) {
                activeOrders.add(order);
            }
        }

        List<FrontdeskBeverageOrderResponse> responses = new ArrayList<>();
        for (KdsOrderGroupResponse orderGroup : buildOrderGroups(activeOrders)) {
            List<KdsOrderItemStatusResponse> beverageItems = orderGroup.items.stream()
                .filter(item -> FRONTDESK_CODES.contains(item.category_code_snapshot))
                .toList();
            if (beverageItems.isEmpty()) {
                continue;
            }
            FrontdeskBeverageOrderResponse response = new FrontdeskBeverageOrderResponse();
            response.order_id = orderGroup.order_id;
            response.order_no = orderGroup.order_no;
            response.table_no = orderGroup.table_no;
            response.pickup_no = orderGroup.pickup_no;
            response.order_status = orderGroup.order_status;
            response.created_at = orderGroup.created_at;
            response.items = beverageItems;
            responses.add(response);
        }
        return responses;
    }

    @Override
    public List<ServingShelfItemResponse> getServingShelfView(Long storeId) {
        List<KitchenTask> tasks = kitchenTaskRepository.findShelfTasksByStoreId(storeId);
        Map<Long, Order> ordersById = fetchOrdersMap(tasks.stream().map(task -> task.order_id).collect(java.util.stream.Collectors.toSet()));
        Map<Long, OrderItem> orderItemsById = fetchOrderItemsMap(tasks.stream().map(task -> task.order_item_id).collect(java.util.stream.Collectors.toSet()));
        Map<Long, List<OrderItemOption>> optionsByOrderItemId = fetchOptionsByOrderItemId(orderItemsById.keySet());
        List<ServingShelfItemResponse> responses = new ArrayList<>();
        for (KitchenTask task : tasks) {
            Order order = ordersById.get(task.order_id);
            OrderItem orderItem = orderItemsById.get(task.order_item_id);
            if (order == null || orderItem == null) {
                continue;
            }
            if ("delivery".equalsIgnoreCase(order.order_type)) {
                continue;
            }
            ServingShelfItemResponse response = new ServingShelfItemResponse();
            response.task_id = task.id;
            response.order_id = task.order_id;
            response.order_item_id = task.order_item_id;
            response.order_no = order.order_no;
            response.order_type = order.order_type;
            response.table_no = order.table_no;
            response.pickup_no = order.pickup_no;
            response.category_code_snapshot = isComboSideTask(task) ? "SIDE" : orderItem.category_code_snapshot;
            response.item_name_snapshot_zh = task.item_name_snapshot_zh;
            response.item_name_snapshot_en = task.item_name_snapshot_en;
            response.quantity = task.quantity;
            response.special_instructions_snapshot = task.special_instructions_snapshot;
            response.size_label = extractOptionLabel(optionsByOrderItemId.getOrDefault(orderItem.id, List.of()), "size");
            response.created_at = task.created_at;
            response.ready_for_pickup_at = task.completed_at;
            responses.add(response);
        }
        return responses;
    }

    @Override
    public List<KdsOrderGroupResponse> getHistoryView(Long storeId, Integer limit, String stationCode) {
        int historyLimit = limit == null || limit < 1 ? 20 : limit;
        if (stationCode != null && !stationCode.isBlank()) {
            List<String> stationCodes = resolveStationCodes(stationCode);
            List<KitchenTask> completedTasks = stationCodes.size() == 1
                ? kitchenTaskRepository.findCompletedTasksByStoreIdAndStationCode(storeId, stationCodes.get(0))
                : kitchenTaskRepository.findCompletedTasksByStoreIdAndStationCodes(storeId, stationCodes);
            Set<Long> activeOrderIds = kitchenTaskRepository.findActiveTasksByStoreIdAndStationCodes(storeId, stationCodes).stream()
                .map(task -> task.order_id)
                .collect(java.util.stream.Collectors.toSet());
            List<Long> orderedIds = completedTasks.stream()
                .map(task -> task.order_id)
                .filter(orderId -> !activeOrderIds.contains(orderId))
                .distinct()
                .limit(historyLimit)
                .toList();
            List<KdsOrderGroupResponse> groups = buildOrderGroups(fetchOrders(new LinkedHashSet<>(orderedIds)));
            groups.sort(Comparator.comparing(
                (KdsOrderGroupResponse group) -> resolveLatestCompletionTimestamp(group, stationCodes),
                Comparator.nullsLast(Comparator.naturalOrder())
            ).reversed().thenComparing(group -> group.order_id, Comparator.reverseOrder()));
            return groups;
        }
        List<Order> orders = orderRepository.findRecentFinishedOrders(storeId, PageRequest.of(0, historyLimit));
        return buildOrderGroups(orders);
    }

    private List<KdsTaskDisplayResponse> buildTaskDisplayResponses(List<KitchenTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Map<Long, Order> ordersById = fetchOrdersMap(tasks.stream().map(task -> task.order_id).collect(java.util.stream.Collectors.toSet()));
        Map<Long, OrderItem> orderItemsById = fetchOrderItemsMap(tasks.stream().map(task -> task.order_item_id).collect(java.util.stream.Collectors.toSet()));
        Map<Long, List<OrderItemOption>> optionsByOrderItemId = fetchOptionsByOrderItemId(orderItemsById.keySet());

        List<KdsTaskDisplayResponse> responses = new ArrayList<>();
        for (KitchenTask task : tasks) {
            Order order = ordersById.get(task.order_id);
            OrderItem orderItem = orderItemsById.get(task.order_item_id);
            if (order == null || orderItem == null) {
                continue;
            }
            KdsTaskDisplayResponse response = new KdsTaskDisplayResponse();
            response.task_id = task.id;
            response.order_id = order.id;
            response.order_no = order.order_no;
            response.table_no = order.table_no;
            response.pickup_no = order.pickup_no;
            response.station_code = task.station_code;
            response.item_name_snapshot_zh = task.item_name_snapshot_zh;
            response.item_name_snapshot_en = task.item_name_snapshot_en;
            response.quantity = task.quantity;
            response.order_modified_after_submit = Boolean.TRUE.equals(order.is_modified_after_submit);
            response.order_modified_after_submit_at = order.modified_after_submit_at;
            response.item_modified_after_submit = Boolean.TRUE.equals(orderItem.is_modified_after_submit);
            response.item_modified_after_submit_at = orderItem.modified_after_submit_at;
            response.status = task.status;
            response.special_instructions_snapshot = task.special_instructions_snapshot;
            response.size_label = extractOptionLabel(optionsByOrderItemId.getOrDefault(orderItem.id, List.of()), "size");
            response.noodle_type_label = extractOptionLabel(optionsByOrderItemId.getOrDefault(orderItem.id, List.of()), "noodle_type");
            response.extra_flags = extractExtraFlags(optionsByOrderItemId.getOrDefault(orderItem.id, List.of()));
            response.created_at = task.created_at;
            response.started_at = task.started_at;
            response.completed_at = task.completed_at;
            response.served_at = task.served_at;
            responses.add(response);
        }
        return responses;
    }

    private List<KdsOrderGroupResponse> buildOrderGroups(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(order -> order.id).toList();
        List<OrderItem> orderItems = orderItemRepository.findAllByOrderIds(orderIds).stream()
            .filter(orderItem -> !"cancelled".equals(orderItem.status))
            .toList();
        Map<Long, List<OrderItem>> itemsByOrderId = new HashMap<>();
        for (OrderItem orderItem : orderItems) {
            itemsByOrderId.computeIfAbsent(orderItem.order_id, key -> new ArrayList<>()).add(orderItem);
        }
        Map<Long, List<OrderItemOption>> optionsByOrderItemId = fetchOptionsByOrderItemId(
            orderItems.stream().map(item -> item.id).collect(java.util.stream.Collectors.toSet())
        );
        Map<Long, List<KitchenTask>> tasksByOrderItemId = new HashMap<>();
        for (KitchenTask task : kitchenTaskRepository.findAllByOrderIds(orderIds)) {
            tasksByOrderItemId.computeIfAbsent(task.order_item_id, key -> new ArrayList<>()).add(task);
        }

        List<KdsOrderGroupResponse> responses = new ArrayList<>();
        for (Order order : orders) {
            KdsOrderGroupResponse response = new KdsOrderGroupResponse();
            response.order_id = order.id;
            response.order_no = order.order_no;
            response.table_no = order.table_no;
            response.pickup_no = order.pickup_no;
            response.order_status = order.status;
            response.is_modified_after_submit = Boolean.TRUE.equals(order.is_modified_after_submit);
            response.modified_after_submit_at = order.modified_after_submit_at;
            response.created_at = order.created_at;
            response.ready_at = order.ready_at;
            response.completed_at = order.completed_at;
            response.items = new ArrayList<>();

            for (OrderItem orderItem : itemsByOrderId.getOrDefault(order.id, List.of())) {
                List<KitchenTask> itemTasks = tasksByOrderItemId.getOrDefault(orderItem.id, List.of());
                KitchenTask task = itemTasks.stream()
                    .filter(candidate -> !isComboSideTask(candidate))
                    .findFirst()
                    .orElseGet(() -> itemTasks.stream().findFirst().orElse(null));
                KdsOrderItemStatusResponse itemResponse = new KdsOrderItemStatusResponse();
                itemResponse.order_item_id = orderItem.id;
                itemResponse.category_code_snapshot = orderItem.category_code_snapshot;
                itemResponse.item_name_snapshot_zh = orderItem.item_name_snapshot_zh;
                itemResponse.item_name_snapshot_en = orderItem.item_name_snapshot_en;
                itemResponse.quantity = orderItem.quantity;
                itemResponse.is_modified_after_submit = Boolean.TRUE.equals(orderItem.is_modified_after_submit);
                itemResponse.modified_after_submit_at = orderItem.modified_after_submit_at;
                itemResponse.requires_kitchen_task = task != null;
                itemResponse.station_code = task == null ? null : task.station_code;
                itemResponse.task_status = task == null ? null : task.status;
                itemResponse.special_instructions_snapshot = task == null
                    ? buildOptionSummary(optionsByOrderItemId.getOrDefault(orderItem.id, List.of()), orderItem.notes)
                    : task.special_instructions_snapshot;
                itemResponse.started_at = task == null ? null : task.started_at;
                itemResponse.completed_at = task == null ? null : task.completed_at;
                itemResponse.served_at = task == null ? null : task.served_at;
                response.items.add(itemResponse);

                for (KitchenTask comboTask : itemTasks) {
                    if (!isComboSideTask(comboTask)) {
                        continue;
                    }
                    KdsOrderItemStatusResponse comboResponse = new KdsOrderItemStatusResponse();
                    comboResponse.order_item_id = orderItem.id;
                    comboResponse.category_code_snapshot = "SIDE";
                    comboResponse.item_name_snapshot_zh = comboTask.item_name_snapshot_zh;
                    comboResponse.item_name_snapshot_en = comboTask.item_name_snapshot_en;
                    comboResponse.quantity = comboTask.quantity;
                    comboResponse.is_modified_after_submit = Boolean.TRUE.equals(orderItem.is_modified_after_submit);
                    comboResponse.modified_after_submit_at = orderItem.modified_after_submit_at;
                    comboResponse.requires_kitchen_task = true;
                    comboResponse.station_code = comboTask.station_code;
                    comboResponse.task_status = comboTask.status;
                    comboResponse.special_instructions_snapshot = comboTask.special_instructions_snapshot;
                    comboResponse.started_at = comboTask.started_at;
                    comboResponse.completed_at = comboTask.completed_at;
                    comboResponse.served_at = comboTask.served_at;
                    response.items.add(comboResponse);
                }
            }

            responses.add(response);
        }
        return responses;
    }

    private Map<Long, Order> fetchOrdersMap(Set<Long> orderIds) {
        return fetchOrders(orderIds).stream().collect(java.util.stream.Collectors.toMap(order -> order.id, order -> order));
    }

    private List<String> resolveStationCodes(String stationCode) {
        if (STATION_GROUP_ASSEMBLING.equalsIgnoreCase(stationCode)) {
            return ASSEMBLING_STATIONS;
        }
        return List.of(stationCode);
    }

    private LocalDateTime resolveLatestCompletionTimestamp(KdsOrderGroupResponse group, List<String> stationCodes) {
        LocalDateTime latest = null;
        for (KdsOrderItemStatusResponse item : group.items) {
            if (item.station_code == null || !stationCodes.contains(item.station_code)) {
                continue;
            }
            LocalDateTime candidate = item.served_at != null ? item.served_at : item.completed_at;
            if (candidate == null) {
                continue;
            }
            if (latest == null || candidate.isAfter(latest)) {
                latest = candidate;
            }
        }
        return latest;
    }

    private List<Order> fetchOrders(Set<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return List.of();
        }
        return orderIds.stream()
            .map(orderId -> orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException("Order not found: " + orderId)))
            .toList();
    }

    private Map<Long, OrderItem> fetchOrderItemsMap(Set<Long> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        return orderItemRepository.findAllById(orderItemIds).stream()
            .collect(java.util.stream.Collectors.toMap(orderItem -> orderItem.id, orderItem -> orderItem));
    }

    private Map<Long, List<OrderItemOption>> fetchOptionsByOrderItemId(Set<Long> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<OrderItemOption>> optionsByOrderItemId = new HashMap<>();
        for (OrderItemOption option : orderItemOptionRepository.findAllByOrderItemIds(orderItemIds.stream().toList())) {
            optionsByOrderItemId.computeIfAbsent(option.order_item_id, key -> new ArrayList<>()).add(option);
        }
        return optionsByOrderItemId;
    }

    private String extractOptionLabel(List<OrderItemOption> options, String optionType) {
        for (OrderItemOption option : options) {
            if (optionType.equals(option.option_type_snapshot)) {
                return bilingualLabel(option.option_name_snapshot_zh, option.option_name_snapshot_en);
            }
        }
        return null;
    }

    private List<String> extractExtraFlags(List<OrderItemOption> options) {
        List<String> flags = new ArrayList<>();
        for (OrderItemOption option : options) {
            if (!EXTRA_FLAG_OPTION_TYPES.contains(option.option_type_snapshot)) {
                continue;
            }
            flags.add(bilingualLabel(option.option_name_snapshot_zh, option.option_name_snapshot_en));
        }
        return flags;
    }

    private String buildOptionSummary(List<OrderItemOption> options, String notes) {
        List<String> parts = new ArrayList<>();
        if (notes != null && !notes.isBlank()) {
            parts.add(notes.trim());
        }
        for (OrderItemOption option : options) {
            parts.add(bilingualLabel(option.option_name_snapshot_zh, option.option_name_snapshot_en));
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
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
}
