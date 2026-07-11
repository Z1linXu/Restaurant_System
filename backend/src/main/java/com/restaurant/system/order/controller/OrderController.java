package com.restaurant.system.order.controller;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.order.dto.CreateOrderRequest;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.CreateOrderUpdateRequest;
import com.restaurant.system.order.dto.OrderResponse;
import com.restaurant.system.order.dto.OrderUpdateResponse;
import com.restaurant.system.order.dto.UpdateDraftOrderHeaderRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemQuantityRequest;
import com.restaurant.system.order.dto.UpdateDraftOrderItemRequest;
import com.restaurant.system.order.service.OrderService;
import com.restaurant.system.printing.dto.OrderReprintRequest;
import com.restaurant.system.printing.dto.OrderPrintOptionResponse;
import com.restaurant.system.printing.dto.PrintJobResponse;
import com.restaurant.system.printing.service.PrintDispatcherService;
import com.restaurant.system.printing.service.PrintJobService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
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
    private final PrintDispatcherService printDispatcherService;
    private final PrintJobService printJobService;
    private final FeatureFlagService featureFlagService;
    private final AuditLogService auditLogService;

    public OrderController(
        OrderService orderService,
        AuthorizationService authorizationService,
        PrintDispatcherService printDispatcherService,
        PrintJobService printJobService,
        FeatureFlagService featureFlagService,
        AuditLogService auditLogService
    ) {
        this.orderService = orderService;
        this.authorizationService = authorizationService;
        this.printDispatcherService = printDispatcherService;
        this.printJobService = printJobService;
        this.featureFlagService = featureFlagService;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public ApiResponse<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        var user = authorizationService.requireForStore(request.store_id, Capability.ORDER_CREATE);
        request.created_by = user.userId();
        return ApiResponse.success("Order created in draft status", orderService.createOrder(request));
    }

    @GetMapping("/draft-open")
    public ApiResponse<OrderResponse> findOpenDraftOrder(
        @RequestParam Long store_id,
        @RequestParam(required = false) String table_no,
        @RequestParam(required = false) String pickup_no
    ) {
        authorizationService.requireForStore(store_id, Capability.ORDER_CREATE);
        return ApiResponse.success(orderService.findOpenDraftOrder(store_id, table_no, pickup_no));
    }

    @GetMapping("/open-editable")
    public ApiResponse<OrderResponse> findOpenEditableOrder(
        @RequestParam Long store_id,
        @RequestParam(required = false) String table_no,
        @RequestParam(required = false) String pickup_no
    ) {
        authorizationService.requireForStore(store_id, Capability.ORDER_VIEW_DETAIL);
        return ApiResponse.success(orderService.findOpenEditableOrder(store_id, table_no, pickup_no));
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

    @PostMapping("/{id}/updates")
    public ApiResponse<OrderUpdateResponse> createOrderUpdate(
        @PathVariable Long id,
        @Valid @RequestBody CreateOrderUpdateRequest request,
        HttpServletRequest servletRequest
    ) {
        var user = authorizationService.requireOrder(id, Capability.ORDER_MODIFY_SUBMITTED);
        OrderUpdateResponse response = orderService.createOrderUpdate(id, request, user.userId());
        auditLogService.record(user.storeId(), user, "ORDER_UPDATED", "ORDER", id, "Update Order processed", Map.of("idempotency_key", request.idempotency_key), servletRequest);
        return ApiResponse.success(
            "Order update processed",
            response
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
    public ApiResponse<OrderResponse> completeOrder(@PathVariable Long id, HttpServletRequest servletRequest) {
        var user = authorizationService.requireOrder(id, Capability.ORDER_COMPLETE);
        OrderResponse response = orderService.completeOrder(id);
        auditLogService.record(user.storeId(), user, "ORDER_FINISHED", "ORDER", id, "Finished table/order", Map.of(), servletRequest);
        return ApiResponse.success("Order completed", response);
    }

    @PostMapping("/{id}/reprint")
    public ApiResponse<PrintJobResponse> reprintOrderReceipt(
        @PathVariable Long id,
        @RequestBody OrderReprintRequest request,
        HttpServletRequest servletRequest
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        var user = authorizationService.requireOrder(id, Capability.ORDER_VIEW_DETAIL);
        PrintJobResponse response = printDispatcherService.reprintOrder(id, request, user.userId());
        auditLogService.record(user.storeId(), user, "ORDER_REPRINTED", "ORDER", id, "Reprint requested", Map.of("receipt_type", request.receipt_type == null ? "" : request.receipt_type), servletRequest);
        return ApiResponse.success("Reprint requested", response);
    }

    @GetMapping("/{id}/print-jobs")
    public ApiResponse<List<PrintJobResponse>> getOrderPrintJobs(@PathVariable Long id) {
        featureFlagService.requireEnabled(FeaturePackage.PRINTING);
        var user = authorizationService.requireOrder(id, Capability.ORDER_VIEW_DETAIL, Capability.ORDER_SUBMIT);
        return ApiResponse.success(printJobService.listOrderJobs(user.storeId(), id));
    }

    @GetMapping("/{id}/print-options")
    public ApiResponse<List<OrderPrintOptionResponse>> getOrderPrintOptions(@PathVariable Long id) {
        authorizationService.requireOrder(id, Capability.ORDER_VIEW_DETAIL);
        return ApiResponse.success(printDispatcherService.getOrderPrintOptions(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(@PathVariable Long id) {
        authorizationService.requireOrder(id, Capability.ORDER_CANCEL);
        return ApiResponse.success("Order cancelled", orderService.cancelOrder(id));
    }
}
