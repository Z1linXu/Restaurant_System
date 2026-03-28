package com.restaurant.system.kitchen.service.impl;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.realtime.RealtimeEventPublisher;
import com.restaurant.system.common.realtime.RealtimeTopics;
import com.restaurant.system.common.realtime.RealtimeUpdateMessage;
import com.restaurant.system.kitchen.dto.KitchenTaskResponse;
import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.enums.KitchenTaskStatus;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.kitchen.service.KitchenService;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.repository.OrderRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KitchenServiceImpl implements KitchenService {

    private static final String ORDER_STATUS_READY = "ready";

    private final KitchenTaskRepository kitchenTaskRepository;
    private final OrderRepository orderRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    public KitchenServiceImpl(
        KitchenTaskRepository kitchenTaskRepository,
        OrderRepository orderRepository,
        RealtimeEventPublisher realtimeEventPublisher
    ) {
        this.kitchenTaskRepository = kitchenTaskRepository;
        this.orderRepository = orderRepository;
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    @Override
    public List<KitchenTaskResponse> getTasks(Long storeId, String stationCode) {
        List<KitchenTask> tasks = (stationCode == null || stationCode.isBlank())
            ? kitchenTaskRepository.findAllByStoreId(storeId)
            : kitchenTaskRepository.findAllByStoreIdAndStationCode(storeId, stationCode);
        return tasks.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public KitchenTaskResponse startTask(Long id) {
        KitchenTask kitchenTask = kitchenTaskRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Kitchen task not found: " + id));

        if (KitchenTaskStatus.ready_for_pickup.name().equals(kitchenTask.status)
            || KitchenTaskStatus.served.name().equals(kitchenTask.status)) {
            throw new BusinessException("Completed kitchen task cannot be started");
        }
        if (KitchenTaskStatus.cancelled.name().equals(kitchenTask.status)) {
            throw new BusinessException("Cancelled kitchen task cannot be started");
        }

        kitchenTask.status = KitchenTaskStatus.in_progress.name();
        if (kitchenTask.started_at == null) {
            kitchenTask.started_at = LocalDateTime.now();
        }
        kitchenTaskRepository.save(kitchenTask);
        publishKitchenTaskEvent("kitchen_task.started", kitchenTask, null);
        return toResponse(kitchenTask);
    }

    @Override
    @Transactional
    public KitchenTaskResponse markReadyForPickup(Long id) {
        KitchenTask kitchenTask = kitchenTaskRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Kitchen task not found: " + id));

        if (KitchenTaskStatus.ready_for_pickup.name().equals(kitchenTask.status)) {
            throw new BusinessException("Kitchen task is already ready for pickup");
        }
        if (KitchenTaskStatus.served.name().equals(kitchenTask.status)) {
            throw new BusinessException("Served kitchen task cannot return to shelf");
        }
        if (KitchenTaskStatus.cancelled.name().equals(kitchenTask.status)) {
            throw new BusinessException("Cancelled kitchen task cannot be completed");
        }

        kitchenTask.status = KitchenTaskStatus.ready_for_pickup.name();
        kitchenTask.completed_at = LocalDateTime.now();
        kitchenTaskRepository.save(kitchenTask);

        if (kitchenTaskRepository.countOpenTasksByOrderId(kitchenTask.order_id) == 0) {
            Order order = orderRepository.findById(kitchenTask.order_id)
                .orElseThrow(() -> new BusinessException("Order not found for task: " + kitchenTask.order_id));
            order.status = ORDER_STATUS_READY;
            order.ready_at = LocalDateTime.now();
            order.updated_at = LocalDateTime.now();
            orderRepository.save(order);
            publishKitchenTaskEvent("order.ready", kitchenTask, order.status);
        }

        publishKitchenTaskEvent("kitchen_task.ready_for_pickup", kitchenTask, null);
        return toResponse(kitchenTask);
    }

    @Override
    @Transactional
    public KitchenTaskResponse markServed(Long id) {
        KitchenTask kitchenTask = kitchenTaskRepository.findById(id)
            .orElseThrow(() -> new BusinessException("Kitchen task not found: " + id));

        if (!KitchenTaskStatus.ready_for_pickup.name().equals(kitchenTask.status)) {
            throw new BusinessException("Only ready_for_pickup items can be marked served");
        }

        kitchenTask.status = KitchenTaskStatus.served.name();
        kitchenTask.served_at = LocalDateTime.now();
        kitchenTaskRepository.save(kitchenTask);
        publishKitchenTaskEvent("kitchen_task.served", kitchenTask, null);
        return toResponse(kitchenTask);
    }

    @Override
    @Transactional
    public KitchenTaskResponse completeTask(Long id) {
        return markReadyForPickup(id);
    }

    private KitchenTaskResponse toResponse(KitchenTask kitchenTask) {
        KitchenTaskResponse response = new KitchenTaskResponse();
        response.id = kitchenTask.id;
        response.order_id = kitchenTask.order_id;
        response.order_item_id = kitchenTask.order_item_id;
        response.store_id = kitchenTask.store_id;
        response.station_code = kitchenTask.station_code;
        response.item_name_snapshot_zh = kitchenTask.item_name_snapshot_zh;
        response.item_name_snapshot_en = kitchenTask.item_name_snapshot_en;
        response.quantity = kitchenTask.quantity;
        response.special_instructions_snapshot = kitchenTask.special_instructions_snapshot;
        response.status = kitchenTask.status;
        response.priority = kitchenTask.priority;
        response.created_at = kitchenTask.created_at;
        response.started_at = kitchenTask.started_at;
        response.completed_at = kitchenTask.completed_at;
        response.served_at = kitchenTask.served_at;
        response.cancelled_at = kitchenTask.cancelled_at;
        return response;
    }

    private void publishKitchenTaskEvent(String eventType, KitchenTask kitchenTask, String orderStatusOverride) {
        Order order = orderRepository.findById(kitchenTask.order_id)
            .orElseThrow(() -> new BusinessException("Order not found: " + kitchenTask.order_id));
        RealtimeUpdateMessage message = new RealtimeUpdateMessage();
        message.event_type = eventType;
        message.store_id = kitchenTask.store_id;
        message.order_id = kitchenTask.order_id;
        message.order_item_id = kitchenTask.order_item_id;
        message.order_status = orderStatusOverride == null ? order.status : orderStatusOverride;
        message.task_status = kitchenTask.status;
        message.is_modified_after_submit = Boolean.TRUE.equals(order.is_modified_after_submit);
        message.happened_at = LocalDateTime.now();
        realtimeEventPublisher.publish(message, List.of(
            RealtimeTopics.FRONTDESK_ORDERS,
            RealtimeTopics.KDS_NOODLE,
            RealtimeTopics.KDS_HOT_KITCHEN,
            RealtimeTopics.KDS_PASS,
            RealtimeTopics.KDS_SERVING_SHELF,
            RealtimeTopics.HISTORY
        ));
    }
}
