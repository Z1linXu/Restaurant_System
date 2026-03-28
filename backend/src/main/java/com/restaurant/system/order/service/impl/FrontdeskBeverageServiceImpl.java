package com.restaurant.system.order.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import com.restaurant.system.common.realtime.RealtimeTopics;
import com.restaurant.system.common.realtime.RealtimeUpdateMessage;
import com.restaurant.system.order.dto.FrontdeskBeverageItemResponse;
import com.restaurant.system.order.entity.FrontdeskBeverageItem;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.repository.FrontdeskBeverageItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.service.FrontdeskBeverageService;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class FrontdeskBeverageServiceImpl implements FrontdeskBeverageService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PREPARING = "preparing";
    private static final String STATUS_READY = "ready";
    private static final String STATUS_SERVED = "served";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final Set<String> ACTIVE_STATUSES = Set.of(STATUS_PENDING, STATUS_PREPARING, STATUS_READY);

    private final FrontdeskBeverageItemRepository frontdeskBeverageItemRepository;
    private final OrderRepository orderRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public FrontdeskBeverageServiceImpl(
        FrontdeskBeverageItemRepository frontdeskBeverageItemRepository,
        OrderRepository orderRepository,
        RealtimeEventPublisher realtimeEventPublisher
    ) {
        this.frontdeskBeverageItemRepository = frontdeskBeverageItemRepository;
        this.orderRepository = orderRepository;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Override
    public List<FrontdeskBeverageItemResponse> getBeverageBoard(Long storeId, List<String> statuses) {
        List<String> statusFilter = (statuses == null || statuses.isEmpty())
            ? List.copyOf(ACTIVE_STATUSES)
            : statuses.stream().filter(status -> status != null && !status.isBlank()).distinct().toList();
        List<FrontdeskBeverageItem> beverageItems = statusFilter.isEmpty()
            ? frontdeskBeverageItemRepository.findAllByStoreId(storeId)
            : frontdeskBeverageItemRepository.findAllByStoreIdAndStatuses(storeId, statusFilter);
        Map<Long, Order> ordersById = orderRepository.findAllById(
            beverageItems.stream().map(item -> item.order_id).distinct().toList()
        ).stream().collect(Collectors.toMap(order -> order.id, order -> order));
        return beverageItems.stream().map(item -> toResponse(item, ordersById.get(item.order_id))).toList();
    }

    @Override
    @Transactional
    public FrontdeskBeverageItemResponse startBeverage(Long orderItemId) {
        FrontdeskBeverageItem beverageItem = requireBeverageItemByOrderItemId(orderItemId);
        if (STATUS_READY.equals(beverageItem.status) || STATUS_SERVED.equals(beverageItem.status)) {
            throw new BusinessException("Ready or served beverage item cannot be started");
        }
        if (STATUS_CANCELLED.equals(beverageItem.status)) {
            throw new BusinessException("Cancelled beverage item cannot be started");
        }
        beverageItem.status = STATUS_PREPARING;
        if (beverageItem.started_at == null) {
            beverageItem.started_at = LocalDateTime.now();
        }
        frontdeskBeverageItemRepository.save(beverageItem);
        publishBeverageEvent("beverage_item.started", beverageItem);
        return toResponse(beverageItem, loadOrder(beverageItem.order_id));
    }

    @Override
    @Transactional
    public FrontdeskBeverageItemResponse markBeverageReady(Long orderItemId) {
        FrontdeskBeverageItem beverageItem = requireBeverageItemByOrderItemId(orderItemId);
        if (STATUS_READY.equals(beverageItem.status)) {
            throw new BusinessException("Beverage item is already ready");
        }
        if (STATUS_SERVED.equals(beverageItem.status)) {
            throw new BusinessException("Served beverage item cannot return to ready");
        }
        if (STATUS_CANCELLED.equals(beverageItem.status)) {
            throw new BusinessException("Cancelled beverage item cannot be marked ready");
        }
        beverageItem.status = STATUS_READY;
        beverageItem.ready_at = LocalDateTime.now();
        frontdeskBeverageItemRepository.save(beverageItem);
        publishBeverageEvent("beverage_item.ready", beverageItem);
        return toResponse(beverageItem, loadOrder(beverageItem.order_id));
    }

    @Override
    @Transactional
    public FrontdeskBeverageItemResponse markBeverageServed(Long orderItemId) {
        FrontdeskBeverageItem beverageItem = requireBeverageItemByOrderItemId(orderItemId);
        if (!STATUS_READY.equals(beverageItem.status)) {
            throw new BusinessException("Only ready beverage items can be marked served");
        }
        beverageItem.status = STATUS_SERVED;
        beverageItem.served_at = LocalDateTime.now();
        frontdeskBeverageItemRepository.save(beverageItem);
        publishBeverageEvent("beverage_item.served", beverageItem);
        return toResponse(beverageItem, loadOrder(beverageItem.order_id));
    }

    @Override
    @Transactional
    public FrontdeskBeverageItemResponse cancelBeverage(Long orderItemId) {
        FrontdeskBeverageItem beverageItem = requireBeverageItemByOrderItemId(orderItemId);
        Order order = loadOrder(beverageItem.order_id);
        if ("completed".equals(order.status) || "cancelled".equals(order.status)) {
            throw new BusinessException("Beverage item cannot be cancelled after order completion/cancellation");
        }
        if (STATUS_SERVED.equals(beverageItem.status)) {
            throw new BusinessException("Served beverage item cannot be cancelled");
        }
        if (STATUS_CANCELLED.equals(beverageItem.status)) {
            throw new BusinessException("Beverage item is already cancelled");
        }
        beverageItem.status = STATUS_CANCELLED;
        beverageItem.cancelled_at = LocalDateTime.now();
        frontdeskBeverageItemRepository.save(beverageItem);
        publishBeverageEvent("beverage_item.cancelled", beverageItem);
        return toResponse(beverageItem, order);
    }

    private FrontdeskBeverageItem requireBeverageItemByOrderItemId(Long orderItemId) {
        FrontdeskBeverageItem beverageItem = frontdeskBeverageItemRepository.findByOrderItemId(orderItemId);
        if (beverageItem == null) {
            throw new BusinessException("Frontdesk beverage item not found for order_item_id: " + orderItemId);
        }
        return beverageItem;
    }

    private Order loadOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("Order not found: " + orderId));
    }

    private FrontdeskBeverageItemResponse toResponse(FrontdeskBeverageItem beverageItem, Order order) {
        FrontdeskBeverageItemResponse response = new FrontdeskBeverageItemResponse();
        response.beverage_item_id = beverageItem.id;
        response.order_id = beverageItem.order_id;
        response.order_item_id = beverageItem.order_item_id;
        response.item_name_snapshot_zh = beverageItem.item_name_snapshot_zh;
        response.item_name_snapshot_en = beverageItem.item_name_snapshot_en;
        response.quantity = beverageItem.quantity;
        response.special_instructions_snapshot = beverageItem.special_instructions_snapshot;
        response.beverage_status = beverageItem.status;
        response.created_at = beverageItem.created_at;
        response.started_at = beverageItem.started_at;
        response.ready_at = beverageItem.ready_at;
        response.served_at = beverageItem.served_at;
        response.cancelled_at = beverageItem.cancelled_at;
        if (order != null) {
            response.order_no = order.order_no;
            response.table_no = order.table_no;
            response.pickup_no = order.pickup_no;
            response.order_type = order.order_type;
            response.submitted_at = order.submitted_at;
            response.updated_at = order.updated_at;
        }
        return response;
    }

    private void publishBeverageEvent(String eventType, FrontdeskBeverageItem beverageItem) {
        Order order = loadOrder(beverageItem.order_id);
        RealtimeUpdateMessage message = new RealtimeUpdateMessage();
        message.event_type = eventType;
        message.store_id = beverageItem.store_id;
        message.order_id = beverageItem.order_id;
        message.order_item_id = beverageItem.order_item_id;
        message.order_status = order.status;
        message.beverage_status = beverageItem.status;
        message.is_modified_after_submit = Boolean.TRUE.equals(order.is_modified_after_submit);
        message.happened_at = LocalDateTime.now();
        realtimeEventPublisher.publish(message, List.of(
            RealtimeTopics.FRONTDESK_ORDERS,
            RealtimeTopics.FRONTDESK_BEVERAGES,
            RealtimeTopics.HISTORY
        ));
    }
}
