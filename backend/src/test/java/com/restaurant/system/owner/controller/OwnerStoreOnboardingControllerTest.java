package com.restaurant.system.owner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.common.feature.FeatureDisabledException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingRequest;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingResponse;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingStaffRequest;
import com.restaurant.system.owner.dto.OwnerStoreOnboardingStaffResponse;
import com.restaurant.system.owner.service.OwnerStoreOnboardingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OwnerStoreOnboardingControllerTest {

    private static final long ORGANIZATION_ID = 100L;
    private static final String IDEMPOTENCY_KEY = "controller-onboarding-key";

    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private OwnerStoreOnboardingService onboardingService;
    @Mock
    private FeatureFlagService featureFlagService;
    @Captor
    private ArgumentCaptor<OwnerStoreOnboardingRequest> requestCaptor;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthenticatedUser owner;
    private String syntheticPassword;

    @BeforeEach
    void setUp() {
        OwnerStoreOnboardingController controller = new OwnerStoreOnboardingController(
            authorizationService,
            onboardingService,
            featureFlagService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        objectMapper = new ObjectMapper();
        owner = new AuthenticatedUser(1L, 10L, 1L, "owner-test", "Owner Test", "OWNER");
        syntheticPassword = "test-" + UUID.randomUUID();
    }

    @Test
    void ownerOnboardingResponseIsRedactedAndForwardsTheIdempotencyKey() throws Exception {
        when(authorizationService.requireOwner()).thenReturn(owner);
        when(onboardingService.onboard(eq(ORGANIZATION_ID), eq(IDEMPOTENCY_KEY), any(), eq(owner)))
            .thenReturn(safeResponse());

        MvcResult result = mockMvc.perform(post("/api/v1/owner/organizations/{organizationId}/stores/onboard", ORGANIZATION_ID)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.store_id").value(102))
            .andExpect(jsonPath("$.data.staff[0].login_identifier").value("controller-test-user"))
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(syntheticPassword);
        verify(authorizationService).requireOwner();
        verify(onboardingService).onboard(
            eq(ORGANIZATION_ID),
            eq(IDEMPOTENCY_KEY),
            requestCaptor.capture(),
            eq(owner)
        );
        assertThat(requestCaptor.getValue().toString()).doesNotContain(syntheticPassword);
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequestWithoutCallingOnboardingService() throws Exception {
        mockMvc.perform(post("/api/v1/owner/organizations/{organizationId}/stores/onboard", ORGANIZATION_ID)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error_code").value("REQUEST_HEADER_REQUIRED"));

        verifyNoInteractions(authorizationService, onboardingService);
    }

    @Test
    void platformFeatureGateBlocksOnboardingRuntimeBeforeOwnerAuthorization() throws Exception {
        doThrow(new FeatureDisabledException(FeaturePackage.PLATFORM))
            .when(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);

        mockMvc.perform(post("/api/v1/owner/organizations/{organizationId}/stores/onboard", ORGANIZATION_ID)
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("FEATURE_DISABLED"));

        verifyNoInteractions(authorizationService, onboardingService);
    }

    private OwnerStoreOnboardingRequest request() {
        OwnerStoreOnboardingStaffRequest staff = new OwnerStoreOnboardingStaffRequest();
        staff.login_identifier = "controller-test-user";
        staff.full_name = "Synthetic Staff";
        staff.role_code = "FRONTDESK";
        staff.initial_password = syntheticPassword;

        OwnerStoreOnboardingRequest request = new OwnerStoreOnboardingRequest();
        request.source_store_id = 10L;
        request.store_name = "Controller Test Store";
        request.store_code = "controller-test";
        request.staff = List.of(staff);
        return request;
    }

    private OwnerStoreOnboardingResponse safeResponse() {
        OwnerStoreOnboardingStaffResponse staff = new OwnerStoreOnboardingStaffResponse();
        staff.user_id = 103L;
        staff.login_identifier = "controller-test-user";
        staff.role_code = "FRONTDESK";

        OwnerStoreOnboardingResponse response = new OwnerStoreOnboardingResponse();
        response.onboarding_request_id = 104L;
        response.organization_id = ORGANIZATION_ID;
        response.source_store_id = 10L;
        response.store_id = 102L;
        response.store_name = "Controller Test Store";
        response.store_code = "CONTROLLER-TEST";
        response.store_status = "inactive";
        response.onboarding_status = "COMPLETED";
        response.result_code = "PENDING_MENU_AND_PRINT_CONFIGURATION";
        response.staff = List.of(staff);
        return response;
    }
}
