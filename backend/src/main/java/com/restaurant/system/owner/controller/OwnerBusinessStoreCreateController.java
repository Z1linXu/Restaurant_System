package com.restaurant.system.owner.controller;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.dto.OwnerBusinessStoreCreateResponse;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningCatalogResponse;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningRequest;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.service.OwnerBusinessStoreCreateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/organizations/{organizationId}/stores")
public class OwnerBusinessStoreCreateController {

    private final AuthorizationService authorizationService;
    private final OwnerOrganizationAuthorizationService organizationAuthorizationService;
    private final OwnerBusinessStoreCreateService createService;

    public OwnerBusinessStoreCreateController(
        AuthorizationService authorizationService,
        OwnerOrganizationAuthorizationService organizationAuthorizationService,
        OwnerBusinessStoreCreateService createService
    ) {
        this.authorizationService = authorizationService;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.createService = createService;
    }

    @GetMapping("/create-catalog")
    public ApiResponse<OwnerStoreProvisioningCatalogResponse> catalog(@PathVariable Long organizationId) {
        requireOrganizationOwner(organizationId);
        return ApiResponse.success("Business Store creation catalog", OwnerStoreProvisioningCatalogResponse.initial(true));
    }

    @PostMapping
    public ApiResponse<OwnerBusinessStoreCreateResponse> create(
        @PathVariable Long organizationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody OwnerStoreProvisioningRequest request
    ) {
        AuthenticatedUser owner = requireOrganizationOwner(organizationId);
        return ApiResponse.success(
            "Business Store created",
            createService.create(owner, organizationId, idempotencyKey, request)
        );
    }

    private AuthenticatedUser requireOrganizationOwner(Long organizationId) {
        AuthenticatedUser owner;
        try {
            owner = authorizationService.requireOwner();
        } catch (ForbiddenException exception) {
            throw forbidden(
                "BUSINESS_STORE_CREATE_AUTHORIZATION_DENIED",
                "Organization Owner role is required to create a Store"
            );
        }
        try {
            organizationAuthorizationService.requireActiveOwnerMembership(owner, organizationId);
        } catch (ForbiddenException exception) {
            throw forbidden(
                "BUSINESS_STORE_CREATE_ORGANIZATION_DENIED",
                "Active Owner membership in this Organization is required to create a Store"
            );
        }
        return owner;
    }

    private OwnerStoreProvisioningException forbidden(String code, String message) {
        return new OwnerStoreProvisioningException(code, HttpStatus.FORBIDDEN, message);
    }
}
