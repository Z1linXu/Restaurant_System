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
    private final StoreAccessService storeAccessService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final KitchenTaskRepository kitchenTaskRepository;

    public AuthorizationService(
        RequestUserContextService requestUserContextService,
        RoleCapabilityRegistry roleCapabilityRegistry,
        StoreAccessService storeAccessService,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        KitchenTaskRepository kitchenTaskRepository
    ) {
        this.requestUserContextService = requestUserContextService;
        this.roleCapabilityRegistry = roleCapabilityRegistry;
        this.storeAccessService = storeAccessService;
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

    public AuthenticatedUser requireOwner(Capability... capabilities) {
        AuthenticatedUser authenticatedUser = require(capabilities);
        if (!isOwner(authenticatedUser)) {
            throw new ForbiddenException("Access denied. Owner role is required");
        }
        return authenticatedUser;
    }

    public AuthenticatedUser requireManagerOrOwnerForStore(Long storeId, Capability... capabilities) {
        AuthenticatedUser authenticatedUser = requireForStore(storeId, capabilities);
        if (!isOwner(authenticatedUser) && !isManager(authenticatedUser)) {
            throw new ForbiddenException("Access denied. Manager or owner role is required");
        }
        return authenticatedUser;
    }

    public AuthenticatedUser requireFrontdeskAccessForStore(Long storeId, Capability... capabilities) {
        AuthenticatedUser authenticatedUser = requireForStore(storeId, capabilities);
        if (!isOwner(authenticatedUser) && !isManager(authenticatedUser) && !isFrontdesk(authenticatedUser)) {
            throw new ForbiddenException("Access denied. Frontdesk access is required");
        }
        return authenticatedUser;
    }

    public AuthenticatedUser requireStaffManageForStore(Long storeId) {
        AuthenticatedUser authenticatedUser = requireForStore(storeId, Capability.ADMIN_USER_ROLE_MANAGE);
        if (!isOwner(authenticatedUser) && !isManager(authenticatedUser)) {
            throw new ForbiddenException("Access denied. Staff management requires owner or manager role");
        }
        return authenticatedUser;
    }

    public AuthenticatedUser requireOrder(Long orderId, Capability... capabilities) {
        AuthenticatedUser authenticatedUser = require(capabilities);
        Order order = orderRepository.findExistingById(orderId);
        if (order == null) {
            throw new ForbiddenException("Order not found for authorization");
        }
        ensureSameStore(authenticatedUser, order.store_id);
        return authenticatedUser;
    }

    public AuthenticatedUser requireOrderItem(Long orderItemId, Capability... capabilities) {
        AuthenticatedUser authenticatedUser = require(capabilities);
        OrderItem orderItem = orderItemRepository.findExistingById(orderItemId);
        if (orderItem == null) {
            throw new ForbiddenException("Order item not found for authorization");
        }
        Order order = orderRepository.findExistingById(orderItem.order_id);
        if (order == null) {
            throw new ForbiddenException("Order not found for authorization");
        }
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
        if (roleCapabilityRegistry.isOwner(authenticatedUser.roleCode())) {
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
        if (storeId == null) {
            return;
        }
        if (!storeAccessService.canAccessStore(authenticatedUser, storeId)) {
            throw new ForbiddenException("Access denied for store " + storeId);
        }
    }

    public boolean isOwner(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null && roleCapabilityRegistry.isOwner(authenticatedUser.roleCode());
    }

    public boolean isManager(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null && roleCapabilityRegistry.isManager(authenticatedUser.roleCode());
    }

    public boolean isFrontdesk(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null && roleCapabilityRegistry.isFrontdesk(authenticatedUser.roleCode());
    }

    public boolean canAccessStore(AuthenticatedUser authenticatedUser, Long storeId) {
        return storeAccessService.canAccessStore(authenticatedUser, storeId);
    }
}
