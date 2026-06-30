package com.restaurant.system.order.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.order.dto.FrontdeskOrderBoardResponse;
import com.restaurant.system.order.service.OrderService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/frontdesk/orders")
public class FrontdeskOrderController {

    private final OrderService orderService;
    private final AuthorizationService authorizationService;

    public FrontdeskOrderController(OrderService orderService, AuthorizationService authorizationService) {
        this.orderService = orderService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResponse<List<FrontdeskOrderBoardResponse>> getOrderBoard(
        @RequestParam Long store_id,
        @RequestParam(required = false) List<String> status,
        @RequestParam(required = false) String order_type,
        @RequestParam(required = false) String table_no,
        @RequestParam(required = false) String pickup_no,
        @RequestParam(required = false) String keyword
    ) {
        authorizationService.requireForStore(store_id, Capability.ORDER_VIEW_ACTIVE);
        return ApiResponse.success(
            orderService.getFrontdeskOrderBoard(store_id, status, order_type, table_no, pickup_no, keyword)
        );
    }

    @GetMapping("/history")
    public ApiResponse<List<FrontdeskOrderBoardResponse>> getOrderHistory(
        @RequestParam Long store_id,
        @RequestParam(required = false) List<String> status,
        @RequestParam(required = false) String order_type,
        @RequestParam(required = false) String table_no,
        @RequestParam(required = false) String pickup_no,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer limit
    ) {
        authorizationService.requireForStore(store_id, Capability.ORDER_VIEW_HISTORY);
        return ApiResponse.success(
            orderService.getFrontdeskOrderHistory(store_id, status, order_type, table_no, pickup_no, keyword, limit)
        );
    }

    @GetMapping("/today")
    public ApiResponse<List<FrontdeskOrderBoardResponse>> getTodayOrderHistory(
        @RequestParam Long store_id,
        @RequestParam(required = false) Integer limit
    ) {
        authorizationService.requireForStore(store_id, Capability.ORDER_VIEW_HISTORY);
        return ApiResponse.success(orderService.getFrontdeskTodayOrderHistory(store_id, limit));
    }
}
