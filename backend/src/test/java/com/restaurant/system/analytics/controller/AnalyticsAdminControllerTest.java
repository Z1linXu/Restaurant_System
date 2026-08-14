package com.restaurant.system.analytics.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.restaurant.system.analytics.service.AnalyticsAggregationService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsAdminControllerTest {

    @Mock
    private AnalyticsAggregationService analyticsAggregationService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private FeatureFlagService featureFlagService;
    @Mock
    private StoreModuleAccessEvaluator moduleAccessEvaluator;

    private AnalyticsAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new AnalyticsAdminController(
            analyticsAggregationService,
            authorizationService,
            featureFlagService,
            moduleAccessEvaluator
        );
    }

    @Test
    void storelessRebuildRetainsLegacyAnalyticsEnvironmentGate() {
        controller.rebuild("2026-08-14", null);

        verify(authorizationService).require(Capability.ADMIN_STORE_CONFIG);
        verify(featureFlagService).requireEnabled(FeaturePackage.ANALYTICS);
        verifyNoInteractions(moduleAccessEvaluator);
    }
}
