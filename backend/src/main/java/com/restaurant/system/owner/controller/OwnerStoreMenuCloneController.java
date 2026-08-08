package com.restaurant.system.owner.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneRequest;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneResponse;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneValidationResponse;
import com.restaurant.system.owner.exception.OwnerStoreMenuCloneException;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Owner-only API facade; planning and the clone write transaction remain separate services. */
@RestController
@RequestMapping("/api/v1/owner/organizations/{organizationId}/stores/{targetStoreId}/menu-clone")
public class OwnerStoreMenuCloneController {

    private final AuthorizationService authorizationService;
    private final OwnerStoreMenuCloneService menuCloneService;

    public OwnerStoreMenuCloneController(
        AuthorizationService authorizationService,
        OwnerStoreMenuCloneService menuCloneService
    ) {
        this.authorizationService = authorizationService;
        this.menuCloneService = menuCloneService;
    }

    @PostMapping("/validate")
    public ApiResponse<OwnerStoreMenuCloneValidationResponse> validate(
        @PathVariable Long organizationId,
        @PathVariable Long targetStoreId,
        @RequestBody OwnerStoreMenuCloneRequest request
    ) {
        requireFixedRequest(request);
        return ApiResponse.success(
            "Menu clone validation completed",
            menuCloneService.validateMenuClone(
                organizationId, targetStoreId, request, requireOwner()
            )
        );
    }

    @PostMapping
    public ApiResponse<OwnerStoreMenuCloneResponse> execute(
        @PathVariable Long organizationId,
        @PathVariable Long targetStoreId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody OwnerStoreMenuCloneRequest request
    ) {
        requireFixedRequest(request);
        return ApiResponse.success(
            "Menu clone accepted",
            menuCloneService.cloneMenu(
                organizationId, targetStoreId, idempotencyKey, request, requireOwner()
            )
        );
    }

    private void requireFixedRequest(OwnerStoreMenuCloneRequest request) {
        if (request == null || request.source_store_id == null || request.profile_code == null
            || request.profile_code.isBlank() || !request.profile_code.equals(request.profile_code.trim())) {
            throw new OwnerStoreMenuCloneException(
                "MENU_CLONE_REQUEST_INVALID", HttpStatus.BAD_REQUEST,
                "Source Store and exact profile code are required"
            );
        }
    }

    private AuthenticatedUser requireOwner() {
        try {
            return authorizationService.requireOwner();
        } catch (ForbiddenException exception) {
            throw new OwnerStoreMenuCloneException(
                "MENU_CLONE_FORBIDDEN", HttpStatus.FORBIDDEN, "Owner menu clone access is required"
            );
        }
    }
}
