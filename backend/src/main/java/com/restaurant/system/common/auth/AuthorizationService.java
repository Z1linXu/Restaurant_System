package com.restaurant.system.common.auth;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {

    private final RequestUserContextService requestUserContextService;
    private final RoleCapabilityRegistry roleCapabilityRegistry;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final KitchenTaskRepository kitchenTaskRepository;

    public AuthorizationService(
        RequestUserContextService requestUserContextService,
        RoleCapabilityRegistry roleCapabilityRegistry,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        KitchenTaskRepository kitchenTaskRepository
    ) {
        this.requestUserContextService = requestUserContextService;
        this.roleCapabilityRegistry = roleCapabilityRegistry;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.kitchenTaskRepository = kitchenTaskRepository;
    }

    public AuthenticatedUser require(Capability... capabilities) {
        AuthenticatedUser authenticatedUser = requestUserContextService.getRequiredUser();
        ensureHasAnyCapability(authenticatedUser, capabilities);
        return authenticatedUser;
    }

    public AuthenticatedUser requireForStore(Long storeId, Capability... capabilities) {
        AuthenticatedUser authenticatedUser = require(capabilities);
        ensureSameStore(authenticatedUser, storeId);
        return authenticatedUser;
    }

    public AuthenticatedUser requireOrder(Long orderId, Capability... capabilities) {
        AuthenticatedUser authenticatedUser = require(capabilities);
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ForbiddenException("Order not found for authorization"));
        ensureSameStore(authenticatedUser, order.store_id);
        return authenticatedUser;
    }

    public AuthenticatedUser requireOrderItem(Long orderItemId, Capability... capabilities) {
        AuthenticatedUser authenticatedUser = require(capabilities);
        OrderItem orderItem = orderItemRepository.findById(orderItemId)
            .orElseThrow(() -> new ForbiddenException("Order item not found for authorization"));
        Order order = orderRepository.findById(orderItem.order_id)
            .orElseThrow(() -> new ForbiddenException("Order not found for authorization"));
        ensureSameStore(authenticatedUser, order.store_id);
        return authenticatedUser;
    }

    public AuthenticatedUser requireKitchenTask(Long kitchenTaskId, Capability... capabilities) {
        AuthenticatedUser authenticatedUser = require(capabilities);
        KitchenTask kitchenTask = kitchenTaskRepository.findById(kitchenTaskId)
            .orElseThrow(() -> new ForbiddenException("Kitchen task not found for authorization"));
        ensureSameStore(authenticatedUser, kitchenTask.store_id);
        return authenticatedUser;
    }

    private void ensureHasAnyCapability(AuthenticatedUser authenticatedUser, Capability... capabilities) {
        if (roleCapabilityRegistry.isAdmin(authenticatedUser.roleCode())) {
            return;
        }

        for (Capability capability : capabilities) {
            if (roleCapabilityRegistry.hasCapability(authenticatedUser.roleCode(), capability)) {
                return;
            }
        }

        String requestedCapabilities = java.util.Arrays.stream(capabilities)
            .map(Capability::getCode)
            .reduce((left, right) -> left + ", " + right)
            .orElse("unknown");
        throw new ForbiddenException("Access denied. Required capability: " + requestedCapabilities);
    }

    private void ensureSameStore(AuthenticatedUser authenticatedUser, Long storeId) {
        if (storeId == null || roleCapabilityRegistry.isAdmin(authenticatedUser.roleCode())) {
            return;
        }
        if (authenticatedUser.storeId() == null || !authenticatedUser.storeId().equals(storeId)) {
            throw new ForbiddenException("Access denied for store " + storeId);
        }
    }
}
