package com.restaurant.system.staging.cleanup;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.provisioning.PhaseBProvisioningRuntimeGate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately separate from the general Store CRUD surface. This endpoint is
 * only for the audited, Owner-approved Staging synthetic/test cleanup window.
 */
@RestController
@RequestMapping("/api/v1/owner/organizations/{organizationId}/staging/fixture-cleanup")
public class StagingStoreFixtureCleanupController {

    private final AuthorizationService authorizationService;
    private final OwnerOrganizationAuthorizationService organizationAuthorizationService;
    private final FeatureFlagService featureFlagService;
    private final PhaseBProvisioningRuntimeGate runtimeGate;
    private final StoreFixtureCleanupService cleanupService;

    public StagingStoreFixtureCleanupController(
        AuthorizationService authorizationService,
        OwnerOrganizationAuthorizationService organizationAuthorizationService,
        FeatureFlagService featureFlagService,
        PhaseBProvisioningRuntimeGate runtimeGate,
        StoreFixtureCleanupService cleanupService
    ) {
        this.authorizationService = authorizationService;
        this.organizationAuthorizationService = organizationAuthorizationService;
        this.featureFlagService = featureFlagService;
        this.runtimeGate = runtimeGate;
        this.cleanupService = cleanupService;
    }

    @PostMapping
    public ApiResponse<StoreFixtureCleanupResponse> cleanup(
        @PathVariable Long organizationId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody StoreFixtureCleanupRequest request
    ) {
        AuthenticatedUser owner = requireOwner(organizationId);
        return ApiResponse.success(
            "Staging synthetic/test fixture cleanup evaluated",
            cleanupService.cleanup(owner, organizationId, idempotencyKey, request)
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
                "STAGING_FIXTURE_CLEANUP_FORBIDDEN",
                HttpStatus.FORBIDDEN,
                "Owner access for the exact Organization is required"
            );
        }
    }
}
