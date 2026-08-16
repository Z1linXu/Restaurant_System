package com.restaurant.system.owner.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningCatalogResponse;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningRequest;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningResponse;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCommand;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningService;
import com.restaurant.system.owner.provisioning.PhaseBProvisioningRuntimeGate;
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
@RequestMapping("/api/v1/owner/organizations/{organizationId}/phase-b/store-provisioning")
public class OwnerStoreProvisioningController {

    private final AuthorizationService authorizationService;
    private final OwnerOrganizationAuthorizationService organizationAuthorizationService;
    private final FeatureFlagService featureFlagService;
    private final PhaseBProvisioningRuntimeGate runtimeGate;
    private final OwnerStoreProvisioningService provisioningService;

    public OwnerStoreProvisioningController(
        AuthorizationService authorizationService,
        OwnerOrganizationAuthorizationService organizationAuthorizationService,
        FeatureFlagService featureFlagService,
        PhaseBProvisioningRuntimeGate runtimeGate,
        OwnerStoreProvisioningService provisioningService
    ) {
        this.authorizationService = authorizationService;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.featureFlagService = featureFlagService;
        this.runtimeGate = runtimeGate;
        this.provisioningService = provisioningService;
    }

    @GetMapping("/catalog")
    public ApiResponse<OwnerStoreProvisioningCatalogResponse> catalog(@PathVariable Long organizationId) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        runtimeGate.requireEnabled();
        requireOrganizationOwner(organizationId);
        return ApiResponse.success(
            "Phase B provisioning catalog",
            OwnerStoreProvisioningCatalogResponse.initial(true)
        );
    }

    @PostMapping
    public ApiResponse<OwnerStoreProvisioningResponse> provision(
        @PathVariable Long organizationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody OwnerStoreProvisioningRequest request
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        runtimeGate.requireEnabled();
        AuthenticatedUser owner = requireOrganizationOwner(organizationId);
        return ApiResponse.success(
            "Phase B Store provisioning accepted",
            OwnerStoreProvisioningResponse.from(provisioningService.provision(new OwnerStoreProvisioningCommand(
                owner,
                organizationId,
                idempotencyKey,
                request.store_name,
                request.store_code,
                request.profile_code,
                request.profile_version,
                request.profile_fingerprint_sha256,
                request.master_menu_key,
                request.master_menu_version,
                request.master_menu_fingerprint_sha256
            )))
        );
    }

    private AuthenticatedUser requireOrganizationOwner(Long organizationId) {
        try {
            AuthenticatedUser owner = authorizationService.requireOwner();
            organizationAuthorizationService.requireActiveOwnerMembership(owner, organizationId);
            return owner;
        } catch (ForbiddenException exception) {
            throw new OwnerStoreProvisioningException(
                "PHASE_B_PROVISIONING_FORBIDDEN",
                HttpStatus.FORBIDDEN,
                "Owner access is required for Phase B Store provisioning"
            );
        }
    }
}
