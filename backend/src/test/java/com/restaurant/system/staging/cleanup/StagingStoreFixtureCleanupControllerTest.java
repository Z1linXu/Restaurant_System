package com.restaurant.system.staging.cleanup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.owner.provisioning.PhaseBProvisioningRuntimeGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StagingStoreFixtureCleanupControllerTest {

    private MockMvc mockMvc;
    private StoreFixtureCleanupService cleanupService;
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = mock(AuthorizationService.class);
        OwnerOrganizationAuthorizationService organizationAuthorizationService = mock(OwnerOrganizationAuthorizationService.class);
        FeatureFlagService featureFlagService = mock(FeatureFlagService.class);
        PhaseBProvisioningRuntimeGate runtimeGate = mock(PhaseBProvisioningRuntimeGate.class);
        cleanupService = mock(StoreFixtureCleanupService.class);
        AuthenticatedUser owner = new AuthenticatedUser(7L, 1L, 1L, "owner", "Owner", "OWNER");
        when(authorizationService.requireOwner()).thenReturn(owner);
        StoreFixtureCleanupResponse response = new StoreFixtureCleanupResponse();
        response.status = "DRY_RUN_PASS";
        when(cleanupService.cleanup(any(), eq(1L), eq("cleanup-dry-run"), any())).thenReturn(response);
        mockMvc = MockMvcBuilders.standaloneSetup(new StagingStoreFixtureCleanupController(
            authorizationService,
            organizationAuthorizationService,
            featureFlagService,
            runtimeGate,
            cleanupService
        )).build();
    }

    @Test
    void usesSeparateRestrictedCleanupEndpointAndForwardsExplicitRequest() throws Exception {
        String body = new ObjectMapper().writeValueAsString(new StoreFixtureCleanupRequest());

        mockMvc.perform(post("/api/v1/owner/organizations/1/staging/fixture-cleanup")
                .header("Idempotency-Key", "cleanup-dry-run")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk());

        verify(cleanupService).cleanup(any(), eq(1L), eq("cleanup-dry-run"), any());
    }
}
