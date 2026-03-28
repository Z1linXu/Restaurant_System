package com.restaurant.system.order.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.dto.UpdateDraftOrderHeaderRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemQuantityRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemRequest;
import com.restaurant.system.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final AuthorizationService authorizationService;

    public OrderController(OrderService orderService, AuthorizationService authorizationService) {
        this.orderService = orderService;
        this.authorizationService = authorizationService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        authorizationService.requireForStore(request.store_id, Capability.ORDER_CREATE);
        return ApiResponse.success("Order created in draft status", orderService.createOrder(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getOrderDetail(@PathVariable Long id) {
        authorizationService.requireOrder(id, Capability.ORDER_VIEW_DETAIL);
        return ApiResponse.success(orderService.getOrderDetail(id));
    }

    @PutMapping("/{id}/draft-header")
    public ApiResponse<OrderResponse> updateDraftOrderHeader(
        @PathVariable Long id,
        @RequestBody UpdateDraftOrderHeaderRequest request
    ) {
        authorizationService.requireOrder(id, Capability.ORDER_EDIT_DRAFT, Capability.ORDER_MODIFY_SUBMITTED);
        return ApiResponse.success("Draft order header updated", orderService.updateDraftOrderHeader(id, request));
    }

    @PostMapping("/{id}/items")
    public ApiResponse<OrderResponse> addDraftOrderItem(
        @PathVariable Long id,
        @Valid @RequestBody CreateOrderItemRequest request
    ) {
        authorizationService.requireOrder(id, Capability.ORDER_EDIT_DRAFT, Capability.ORDER_MODIFY_SUBMITTED);
        return ApiResponse.success("Draft order item added", orderService.addDraftOrderItem(id, request));
    }

    @PutMapping("/{id}/items/{itemId}/quantity")
    public ApiResponse<OrderResponse> updateDraftOrderItemQuantity(
        @PathVariable Long id,
        @PathVariable Long itemId,
        @Valid @RequestBody UpdateDraftOrderItemQuantityRequest request
    ) {
        authorizationService.requireOrder(id, Capability.ORDER_EDIT_DRAFT, Capability.ORDER_MODIFY_SUBMITTED);
        return ApiResponse.success("Draft order item quantity updated", orderService.updateDraftOrderItemQuantity(id, itemId, request));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ApiResponse<OrderResponse> updateDraftOrderItem(
        @PathVariable Long id,
        @PathVariable Long itemId,
        @Valid @RequestBody UpdateDraftOrderItemRequest request
    ) {
        authorizationService.requireOrder(id, Capability.ORDER_EDIT_DRAFT, Capability.ORDER_MODIFY_SUBMITTED);
        return ApiResponse.success("Draft order item updated", orderService.updateDraftOrderItem(id, itemId, request));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ApiResponse<OrderResponse> removeDraftOrderItem(@PathVariable Long id, @PathVariable Long itemId) {
        authorizationService.requireOrder(id, Capability.ORDER_EDIT_DRAFT, Capability.ORDER_MODIFY_SUBMITTED);
        return ApiResponse.success("Draft order item removed", orderService.removeDraftOrderItem(id, itemId));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<OrderResponse> submitOrder(@PathVariable Long id) {
        authorizationService.requireOrder(id, Capability.ORDER_SUBMIT);
        return ApiResponse.success(
            "Order submitted and moved to preparing after kitchen task and inventory processing",
            orderService.submitOrder(id)
        );
    }

    @GetMapping("/active")
    public ApiResponse<List<OrderResponse>> getActiveOrders(
        @RequestParam Long store_id,
        @RequestParam(required = false) List<String> status,
        @RequestParam(required = false) String order_type,
        @RequestParam(required = false) String sort_by
    ) {
        authorizationService.requireForStore(store_id, Capability.ORDER_VIEW_ACTIVE);
        return ApiResponse.success(orderService.getActiveOrders(store_id, status, order_type, sort_by));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<OrderResponse> completeOrder(@PathVariable Long id) {
        authorizationService.requireOrder(id, Capability.ORDER_COMPLETE);
        return ApiResponse.success("Order completed", orderService.completeOrder(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long id) {
        authorizationService.requireOrder(id, Capability.ORDER_CANCEL);
        return ApiResponse.success("Order cancelled", orderService.cancelOrder(id));
    }
}
