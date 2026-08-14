package com.restaurant.system.owner.controller;

import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingRequest;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingResponse;
import com.restaurant.system.owner.service.OwnerStoreOnboardingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/organizations/{organizationId}/stores")
public class OwnerStoreOnboardingController {

    private final AuthorizationService authorizationService;
    private final OwnerStoreOnboardingService onboardingService;
    private final FeatureFlagService featureFlagService;

    public OwnerStoreOnboardingController(
        AuthorizationService authorizationService,
        OwnerStoreOnboardingService onboardingService,
        FeatureFlagService featureFlagService
    ) {
        this.authorizationService = authorizationService;
        this.onboardingService = onboardingService;
        this.featureFlagService = featureFlagService;
    }

    @PostMapping("/onboard")
    public ApiResponse<OwnerStoreOnboardingResponse> onboard(
        @PathVariable Long organizationId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody OwnerStoreOnboardingRequest request
    ) {
        featureFlagService.requireEnabled(FeaturePackage.PLATFORM);
        return ApiResponse.success(
            "Store onboarding accepted",
            onboardingService.onboard(
                organizationId,
                idempotencyKey,
                request,
                authorizationService.requireOwner()
            )
        );
    }
}
