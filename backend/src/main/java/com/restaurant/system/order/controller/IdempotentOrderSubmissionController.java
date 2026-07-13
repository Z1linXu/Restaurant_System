package com.restaurant.system.order.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.order.dto.IdempotentOrderSubmitRequest;
import com.restaurant.system.order.dto.IdempotentOrderSubmitResponse;
import com.restaurant.system.order.service.IdempotentOrderSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/orders")
public class IdempotentOrderSubmissionController {

    private final AuthorizationService authorizationService;
    private final IdempotentOrderSubmissionService submissionService;

    public IdempotentOrderSubmissionController(
        AuthorizationService authorizationService,
        IdempotentOrderSubmissionService submissionService
    ) {
        this.authorizationService = authorizationService;
        this.submissionService = submissionService;
    }

    @PostMapping("/idempotent-submit")
    public ApiResponse<IdempotentOrderSubmitResponse> submit(
        @PathVariable Long storeId,
        @Valid @RequestBody IdempotentOrderSubmitRequest request
    ) {
        var user = authorizationService.requireForStore(
            storeId,
            Capability.ORDER_CREATE,
            Capability.ORDER_SUBMIT
        );
        return ApiResponse.success(
            "Order accepted idempotently",
            submissionService.submit(storeId, request, user.userId())
        );
    }
}
