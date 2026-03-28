package com.restaurant.system.order.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.order.dto.FrontdeskBeverageItemResponse;
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

    public FrontdeskBeverageController(
        FrontdeskBeverageService frontdeskBeverageService,
        AuthorizationService authorizationService
    ) {
        this.frontdeskBeverageService = frontdeskBeverageService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    public ApiResponse<List<FrontdeskBeverageItemResponse>> getBeverageBoard(
        @RequestParam Long store_id,
        @RequestParam(required = false) List<String> status
    ) {
        authorizationService.requireForStore(store_id, Capability.BEVERAGE_VIEW_BOARD);
        return ApiResponse.success(frontdeskBeverageService.getBeverageBoard(store_id, status));
    }

    @PostMapping("/{orderItemId}/start")
    public ApiResponse<FrontdeskBeverageItemResponse> startBeverage(@PathVariable Long orderItemId) {
        authorizationService.requireOrderItem(orderItemId, Capability.BEVERAGE_START);
        return ApiResponse.success("Beverage preparation started", frontdeskBeverageService.startBeverage(orderItemId));
    }

    @PostMapping("/{orderItemId}/ready")
    public ApiResponse<FrontdeskBeverageItemResponse> markBeverageReady(@PathVariable Long orderItemId) {
        authorizationService.requireOrderItem(orderItemId, Capability.BEVERAGE_READY);
        return ApiResponse.success("Beverage item ready", frontdeskBeverageService.markBeverageReady(orderItemId));
    }

    @PostMapping("/{orderItemId}/served")
    public ApiResponse<FrontdeskBeverageItemResponse> markBeverageServed(@PathVariable Long orderItemId) {
        authorizationService.requireOrderItem(orderItemId, Capability.BEVERAGE_SERVED);
        return ApiResponse.success("Beverage item served", frontdeskBeverageService.markBeverageServed(orderItemId));
    }

    @PostMapping("/{orderItemId}/cancel")
    public ApiResponse<FrontdeskBeverageItemResponse> cancelBeverage(@PathVariable Long orderItemId) {
        authorizationService.requireOrderItem(orderItemId, Capability.BEVERAGE_CANCEL);
        return ApiResponse.success("Beverage item cancelled", frontdeskBeverageService.cancelBeverage(orderItemId));
    }
}
