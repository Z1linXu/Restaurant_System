package com.restaurant.system.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.printing.dto.OrderReprintRequest;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.PrintJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class OrderControllerReprintTest {

    @Mock
    private OrderService orderService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private PrintDispatcherService printDispatcherService;
    @Mock
    private PrintJobService printJobService;
    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private AuditLogService auditLogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        OrderController controller = new OrderController(
            orderService,
            authorizationService,
            printDispatcherService,
            printJobService,
            featureFlagService,
            auditLogService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        when(authorizationService.requireOrder(5L, Capability.ORDER_VIEW_DETAIL)).thenReturn(
            new AuthenticatedUser(7L, 1L, 1L, "owner", "Owner", "OWNER")
        );
    }

    @Test
    void noReprintableGrabSnapshotReturnsConflictInsteadOfFailedJobResponse() throws Exception {
        when(printDispatcherService.reprintOrder(eq(5L), any(OrderReprintRequest.class), eq(7L)))
            .thenThrow(new ResponseStatusException(
                HttpStatus.CONFLICT,
                "NO_REPRINTABLE_SNAPSHOT: No successful full GRAB ticket is available for reprint"
            ));

        mockMvc.perform(post("/api/v1/orders/5/reprint")
                .contentType("application/json")
                .content("{\"receipt_type\":\"GRAB\",\"update_ticket\":false}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(
                "NO_REPRINTABLE_SNAPSHOT: No successful full GRAB ticket is available for reprint"
            ));
    }
}
