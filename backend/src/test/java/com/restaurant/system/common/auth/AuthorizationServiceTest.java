package com.restaurant.system.common.auth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.restaurant.system.kitchen.entity.KitchenTask;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Role;
import com.restaurant.system.user.entity.User;
import com.restaurant.system.user.repository.RoleRepository;
import com.restaurant.system.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private StoreAccessService storeAccessService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private KitchenTaskRepository kitchenTaskRepository;

    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        RequestUserContextService requestUserContextService = new RequestUserContextService(
            request,
            userRepository,
            roleRepository,
            true
        );
        authorizationService = new AuthorizationService(
            requestUserContextService,
            new RoleCapabilityRegistry(),
            storeAccessService,
            orderRepository,
            orderItemRepository,
            kitchenTaskRepository
        );
    }

    @Test
    void frontdeskCanAccessOrderCreateForOwnStore() {
        stubUser(1L, 100L, "FRONTDESK");

        AuthenticatedUser authenticatedUser = authorizationService.requireForStore(100L, Capability.ORDER_CREATE);

        assertEquals("FRONTDESK", authenticatedUser.roleCode());
    }

    @Test
    void frontdeskCanUseStoreScopedMenuAndPrintingToolsOnlyForOwnStore() {
        stubUser(8L, 100L, "FRONTDESK");

        assertDoesNotThrow(() -> authorizationService.requireForStore(100L, Capability.ADMIN_MENU_MANAGE));
        assertDoesNotThrow(() -> authorizationService.requireForStore(100L, Capability.ADMIN_PRINTING_MANAGE));
        assertThrows(ForbiddenException.class, () -> authorizationService.requireForStore(100L, Capability.ADMIN_STORE_CONFIG));
        assertThrows(ForbiddenException.class, () -> authorizationService.requireForStore(100L, Capability.ADMIN_USER_ROLE_MANAGE));
        assertThrows(ForbiddenException.class, () -> authorizationService.requireForStore(100L, Capability.ADMIN_HISTORY_LIMIT));
        assertThrows(ForbiddenException.class, () -> authorizationService.requireForStore(200L, Capability.ADMIN_PRINTING_MANAGE));
    }

    @Test
    void hotKitchenCannotCreateOrders() {
        stubUser(2L, 100L, "HOT_KITCHEN");

        assertThrows(ForbiddenException.class, () -> authorizationService.requireForStore(100L, Capability.ORDER_CREATE));
    }

    @Test
    void noodleViewCanOnlyReadNoodleDisplay() {
        stubUser(3L, 100L, "NOODLE_VIEW");

        assertDoesNotThrow(() -> authorizationService.requireForStore(100L, Capability.KDS_NOODLE_VIEW));
        assertThrows(ForbiddenException.class, () -> authorizationService.requireForStore(100L, Capability.KDS_HOT_VIEW));
    }

    @Test
    void passCanMarkReadyForPickupButNotOperateBeverage() {
        stubUser(4L, 100L, "PASS");

        KitchenTask kitchenTask = new KitchenTask();
        kitchenTask.id = 11L;
        kitchenTask.store_id = 100L;
        when(kitchenTaskRepository.findById(11L)).thenReturn(Optional.of(kitchenTask));

        assertDoesNotThrow(() -> authorizationService.requireKitchenTask(11L, Capability.KDS_PASS_READY_FOR_PICKUP));
        assertThrows(ForbiddenException.class, () -> authorizationService.requireForStore(100L, Capability.BEVERAGE_READY));
    }

    @Test
    void frontdeskCannotAccessAnotherStore() {
        stubUser(5L, 100L, "FRONTDESK");

        assertThrows(ForbiddenException.class, () -> authorizationService.requireForStore(200L, Capability.ORDER_VIEW_ACTIVE));
    }

    @Test
    void adminBypassesStoreScopeChecks() {
        stubUser(6L, 100L, "ADMIN");
        when(storeAccessService.canAccessStore(any(AuthenticatedUser.class), eq(999L))).thenReturn(true);

        Order order = new Order();
        order.id = 21L;
        order.store_id = 999L;
        when(orderRepository.findExistingById(21L)).thenReturn(order);

        assertDoesNotThrow(() -> authorizationService.requireOrder(21L, Capability.ORDER_CANCEL));
    }

    @Test
    void missingUserHeaderReturnsUnauthorized() {
        when(request.getAttribute(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> authorizationService.require(Capability.ORDER_VIEW_DETAIL));
    }

    @Test
    void orderItemScopeResolvesThroughOrderStore() {
        stubUser(7L, 100L, "FRONTDESK");

        OrderItem orderItem = new OrderItem();
        orderItem.id = 31L;
        orderItem.order_id = 41L;
        when(orderItemRepository.findExistingById(31L)).thenReturn(orderItem);

        Order order = new Order();
        order.id = 41L;
        order.store_id = 100L;
        when(orderRepository.findExistingById(41L)).thenReturn(order);

        assertDoesNotThrow(() -> authorizationService.requireOrderItem(31L, Capability.BEVERAGE_START));
    }

    private void stubUser(Long userId, Long storeId, String roleCode) {
        User user = new User();
        user.setId(userId);
        user.setStore_id(storeId);
        user.setRole_id(userId);
        user.setUsername("user" + userId);
        user.setFull_name("User " + userId);
        user.setStatus("active");

        Role role = new Role();
        role.setId(userId);
        role.setCode(roleCode);
        role.setName(roleCode);

        when(request.getAttribute(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn(String.valueOf(userId));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findById(userId)).thenReturn(Optional.of(role));
        lenient().when(storeAccessService.canAccessStore(any(AuthenticatedUser.class), eq(storeId))).thenReturn(true);
    }
}
