package com.restaurant.system.order.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.order.dto.FrontdeskBeverageItemResponse;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.order.service.FrontdeskBeverageService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/frontdesk/beverages")
public class FrontdeskBeverageController {

    private final FrontdeskBeverageService frontdeskBeverageService;
    private final AuthorizationService authorizationService;
    private final StoreModuleAccessEvaluator moduleAccessEvaluator;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;

    public FrontdeskBeverageController(
        FrontdeskBeverageService frontdeskBeverageService,
        AuthorizationService authorizationService,
        StoreModuleAccessEvaluator moduleAccessEvaluator,
        OrderItemRepository orderItemRepository,
        OrderRepository orderRepository
    ) {
        this.frontdeskBeverageService = frontdeskBeverageService;
        this.authorizationService = authorizationService;
        this.moduleAccessEvaluator = moduleAccessEvaluator;
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping
    public ApiResponse<List<FrontdeskBeverageItemResponse>> getBeverageBoard(
        @RequestParam Long store_id,
        @RequestParam(required = false) List<String> status
    ) {
        authorizationService.requireForStore(store_id, Capability.BEVERAGE_VIEW_BOARD);
        requireOrdering(store_id);
        return ApiResponse.success(frontdeskBeverageService.getBeverageBoard(store_id, status));
    }

    @PostMapping("/{orderItemId}/start")
    public ApiResponse<FrontdeskBeverageItemResponse> startBeverage(@PathVariable Long orderItemId) {
        authorizationService.requireOrderItem(orderItemId, Capability.BEVERAGE_START);
        requireOrdering(resolveOrderItemStoreId(orderItemId));
        return ApiResponse.success("Beverage preparation started", frontdeskBeverageService.startBeverage(orderItemId));
    }

    @PostMapping("/{orderItemId}/ready")
    public ApiResponse<FrontdeskBeverageItemResponse> markBeverageReady(@PathVariable Long orderItemId) {
        authorizationService.requireOrderItem(orderItemId, Capability.BEVERAGE_READY);
        requireOrdering(resolveOrderItemStoreId(orderItemId));
        return ApiResponse.success("Beverage item ready", frontdeskBeverageService.markBeverageReady(orderItemId));
    }

    @PostMapping("/{orderItemId}/served")
    public ApiResponse<FrontdeskBeverageItemResponse> markBeverageServed(@PathVariable Long orderItemId) {
        authorizationService.requireOrderItem(orderItemId, Capability.BEVERAGE_SERVED);
        requireOrdering(resolveOrderItemStoreId(orderItemId));
        return ApiResponse.success("Beverage item served", frontdeskBeverageService.markBeverageServed(orderItemId));
    }

    @PostMapping("/{orderItemId}/cancel")
    public ApiResponse<FrontdeskBeverageItemResponse> cancelBeverage(@PathVariable Long orderItemId) {
        authorizationService.requireOrderItem(orderItemId, Capability.BEVERAGE_CANCEL);
        requireOrdering(resolveOrderItemStoreId(orderItemId));
        return ApiResponse.success("Beverage item cancelled", frontdeskBeverageService.cancelBeverage(orderItemId));
    }

    private void requireOrdering(Long storeId) {
        moduleAccessEvaluator.requireCapability(storeId, ModuleKeys.ORDERING_POS);
    }

    private Long resolveOrderItemStoreId(Long orderItemId) {
        OrderItem orderItem = orderItemRepository.findExistingById(orderItemId);
        if (orderItem == null) {
            throw new BusinessException("Order item not found");
        }
        Order order = orderRepository.findExistingById(orderItem.order_id);
        if (order == null) {
            throw new BusinessException("Order not found");
        }
        return order.store_id;
    }
}
