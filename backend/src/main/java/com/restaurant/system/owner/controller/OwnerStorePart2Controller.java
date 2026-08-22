package com.restaurant.system.owner.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.provisioning.PhaseBProvisioningRuntimeGate;
import com.restaurant.system.owner.provisioning.part2.StoreActivationRequest;
import com.restaurant.system.owner.provisioning.part2.StoreActivationResponse;
import com.restaurant.system.owner.provisioning.part2.StorePart2ProvisioningRequest;
import com.restaurant.system.owner.provisioning.part2.StorePart2ProvisioningResponse;
import com.restaurant.system.owner.provisioning.part2.StorePart2ProvisioningService;
import com.restaurant.system.owner.provisioning.part2.StoreReadinessResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/organizations/{organizationId}/stores/{storeId}/phase-b/part2")
public class OwnerStorePart2Controller {

    private final AuthorizationService authorizationService;
    private final OwnerOrganizationAuthorizationService organizationAuthorizationService;
    private final FeatureFlagService featureFlagService;
    private final PhaseBProvisioningRuntimeGate runtimeGate;
    private final StorePart2ProvisioningService part2Service;

    public OwnerStorePart2Controller(
        AuthorizationService authorizationService,
        OwnerOrganizationAuthorizationService organizationAuthorizationService,
        FeatureFlagService featureFlagService,
        PhaseBProvisioningRuntimeGate runtimeGate,
        StorePart2ProvisioningService part2Service
    ) {
        this.authorizationService = authorizationService;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.featureFlagService = featureFlagService;
        this.runtimeGate = runtimeGate;
        this.part2Service = part2Service;
    }

    @GetMapping("/readiness")
    public ApiResponse<StoreReadinessResponse> readiness(@PathVariable Long organizationId, @PathVariable Long storeId) {
        requireOwner(organizationId);
        return ApiResponse.success("Phase B Part 2 readiness", part2Service.readiness(organizationId, storeId));
    }

    @PostMapping("/provision")
    public ApiResponse<StorePart2ProvisioningResponse> provision(
        @PathVariable Long organizationId,
        @PathVariable Long storeId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody(required = false) StorePart2ProvisioningRequest request
    ) {
        AuthenticatedUser owner = requireOwner(organizationId);
        return ApiResponse.success(
            "Phase B Part 2 provisioning accepted",
            part2Service.provision(owner, organizationId, storeId, idempotencyKey, request)
        );
    }

    @PostMapping("/activate")
    public ApiResponse<StoreActivationResponse> activate(
        @PathVariable Long organizationId,
        @PathVariable Long storeId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @RequestBody(required = false) StoreActivationRequest request
    ) {
        AuthenticatedUser owner = requireOwner(organizationId);
        return ApiResponse.success(
            "Phase B Part 2 activation accepted",
            part2Service.activate(owner, organizationId, storeId, idempotencyKey, request)
        );
    }

    private AuthenticatedUser requireOwner(Long organizationId) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        runtimeGate.requireEnabled();
        try {
            AuthenticatedUser owner = authorizationService.requireOwner();
            organizationAuthorizationService.requireActiveOwnerMembership(owner, organizationId);
            return owner;
        } catch (ForbiddenException exception) {
            throw new OwnerStoreProvisioningException(
                "PHASE_B_PART2_FORBIDDEN",
                HttpStatus.FORBIDDEN,
                "Owner access for the exact Organization is required"
            );
        }
    }
}
