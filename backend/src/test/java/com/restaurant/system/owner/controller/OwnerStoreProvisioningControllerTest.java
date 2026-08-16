package com.restaurant.system.owner.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.OwnerOrganizationAuthorizationService;
import com.restaurant.system.common.auth.UnauthorizedException;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.common.feature.FeatureDisabledException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningRequest;
import com.restaurant.system.owner.exception.OwnerStoreProvisioningException;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCommand;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningCounts;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningResult;
import com.restaurant.system.owner.provisioning.OwnerStoreProvisioningService;
import com.restaurant.system.owner.provisioning.PhaseBProvisioningRuntimeGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OwnerStoreProvisioningControllerTest {

    private static final long ORGANIZATION_ID = 100L;
    private static final String IDEMPOTENCY_KEY = "phase-b-provisioning-key";

    @Mock private AuthorizationService authorizationService;
    @Mock private OwnerOrganizationAuthorizationService organizationAuthorizationService;
    @Mock private FeatureFlagService featureFlagService;
    @Mock private PhaseBProvisioningRuntimeGate runtimeGate;
    @Mock private OwnerStoreProvisioningService provisioningService;
    @Captor private ArgumentCaptor<OwnerStoreProvisioningCommand> commandCaptor;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthenticatedUser owner;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OwnerStoreProvisioningController(
                authorizationService,
                organizationAuthorizationService,
                featureFlagService,
                runtimeGate,
                provisioningService
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        objectMapper = new ObjectMapper();
        owner = new AuthenticatedUser(1L, 10L, 1L, "owner-test", "Owner Test", "OWNER");
    }

    @Test
    void catalogRequiresProvisioningRuntimeGateAndOwnerAuthorization() throws Exception {
        when(authorizationService.requireOwner()).thenReturn(owner);

        mockMvc.perform(get(route() + "/catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.profile_code").value("ST_DENIS_CANONICAL_PROFILE"))
            .andExpect(jsonPath("$.data.profile_version").value("v2"))
            .andExpect(jsonPath("$.data.master_menu_key").value("LANZHOU_CHAIN_MASTER_MENU"))
            .andExpect(jsonPath("$.data.master_menu_version").value("v1"))
            .andExpect(jsonPath("$.data.master_menu_fingerprint_sha256")
                .value("ef28a4d160373f0f08b810a6b82d1f3c84f2c7d4aa076cceac00836a13d4f38c"));

        verify(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);
        verify(runtimeGate).requireEnabled();
        verify(authorizationService).requireOwner();
        verify(organizationAuthorizationService).requireActiveOwnerMembership(owner, ORGANIZATION_ID);
        verifyNoInteractions(provisioningService);
    }

    @Test
    void catalogRequiresActiveOwnerMembershipInRequestedOrganization() throws Exception {
        when(authorizationService.requireOwner()).thenReturn(owner);
        doThrow(new ForbiddenException("wrong organization"))
            .when(organizationAuthorizationService)
            .requireActiveOwnerMembership(owner, ORGANIZATION_ID);

        mockMvc.perform(get(route() + "/catalog"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("PHASE_B_PROVISIONING_FORBIDDEN"));

        verifyNoInteractions(provisioningService);
    }

    @Test
    void catalogRuntimeGateBlocksBeforeOwnerAuthorization() throws Exception {
        doThrow(new OwnerStoreProvisioningException(
                "PHASE_B_PROVISIONING_DISABLED",
                HttpStatus.FORBIDDEN,
                "Phase B Store provisioning is not enabled in this runtime"
            ))
            .when(runtimeGate).requireEnabled();

        mockMvc.perform(get(route() + "/catalog"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("PHASE_B_PROVISIONING_DISABLED"));

        verify(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);
        verify(runtimeGate).requireEnabled();
        verifyNoInteractions(authorizationService, provisioningService);
    }

    @Test
    void provisionRequiresRuntimeGateAndForwardsOwnerCommand() throws Exception {
        when(authorizationService.requireOwner()).thenReturn(owner);
        when(provisioningService.provision(any())).thenReturn(successfulResult());

        mockMvc.perform(post(route())
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.request_id").value(700))
            .andExpect(jsonPath("$.data.store_id").value(701))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.validation_status").value("PASS"))
            .andExpect(jsonPath("$.data.result_code").value("PHASE_B_STORE_PROVISIONED"))
            .andExpect(jsonPath("$.data.counts.item_count").value(39))
            .andExpect(jsonPath("$.data.counts.printing_rule_count").value(1));

        verify(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);
        verify(runtimeGate).requireEnabled();
        verify(authorizationService).requireOwner();
        verify(organizationAuthorizationService).requireActiveOwnerMembership(owner, ORGANIZATION_ID);
        verify(provisioningService).provision(commandCaptor.capture());

        OwnerStoreProvisioningCommand command = commandCaptor.getValue();
        assertThat(command.actor()).isEqualTo(owner);
        assertThat(command.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(command.idempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(command.storeName()).isEqualTo("Phase B Controller Store");
        assertThat(command.storeCode()).isEqualTo("PHASE_B_CONTROLLER_STORE");
        assertThat(command.profileCode()).isEqualTo("ST_DENIS_CANONICAL_PROFILE");
        assertThat(command.profileVersion()).isEqualTo("v2");
        assertThat(command.masterMenuKey()).isEqualTo("LANZHOU_CHAIN_MASTER_MENU");
        assertThat(command.masterMenuVersion()).isEqualTo("v1");
    }

    @Test
    void provisionAllowsArbitraryOwnerUsernameWhenOrganizationMembershipIsActive() throws Exception {
        AuthenticatedUser arbitraryOwner = new AuthenticatedUser(
            2L,
            10L,
            1L,
            "regional_owner_42",
            "Regional Owner",
            "OWNER"
        );
        when(authorizationService.requireOwner()).thenReturn(arbitraryOwner);
        when(provisioningService.provision(any())).thenReturn(successfulResult());

        mockMvc.perform(post(route())
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(organizationAuthorizationService).requireActiveOwnerMembership(arbitraryOwner, ORGANIZATION_ID);
        verify(provisioningService).provision(commandCaptor.capture());
        assertThat(commandCaptor.getValue().actor()).isEqualTo(arbitraryOwner);
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequestBeforeRuntimeOrAuthorization() throws Exception {
        mockMvc.perform(post(route())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("REQUEST_HEADER_REQUIRED"));

        verifyNoInteractions(featureFlagService, runtimeGate, authorizationService, provisioningService);
    }

    @Test
    void platformFeatureGateBlocksBeforeRuntimeGateAndOwnerAuthorization() throws Exception {
        doThrow(new FeatureDisabledException(FeaturePackage.PLATFORM))
            .when(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);

        mockMvc.perform(post(route())
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("FEATURE_DISABLED"));

        verifyNoInteractions(runtimeGate, authorizationService, provisioningService);
    }

    @Test
    void runtimeGateBlocksBeforeOwnerAuthorizationAndProvisioning() throws Exception {
        doThrow(new OwnerStoreProvisioningException(
                "PHASE_B_PROVISIONING_DISABLED",
                HttpStatus.FORBIDDEN,
                "Phase B Store provisioning is not enabled in this runtime"
            ))
            .when(runtimeGate).requireEnabled();

        mockMvc.perform(post(route())
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("PHASE_B_PROVISIONING_DISABLED"));

        verify(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);
        verify(runtimeGate).requireEnabled();
        verifyNoInteractions(authorizationService, provisioningService);
    }

    @Test
    void nonOwnerIsMappedToProvisioningSpecificForbiddenCode() throws Exception {
        when(authorizationService.requireOwner()).thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(post(route())
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("PHASE_B_PROVISIONING_FORBIDDEN"));

        verify(runtimeGate).requireEnabled();
        verify(provisioningService, never()).provision(any());
    }

    @Test
    void unauthenticatedProvisionRequestReturnsUnauthorizedBeforeProvisioning() throws Exception {
        when(authorizationService.requireOwner()).thenThrow(new UnauthorizedException("Authentication required"));

        mockMvc.perform(post(route())
                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isUnauthorized());

        verify(runtimeGate).requireEnabled();
        verify(provisioningService, never()).provision(any());
    }

    private String route() {
        return "/api/v1/owner/organizations/" + ORGANIZATION_ID + "/phase-b/store-provisioning";
    }

    private OwnerStoreProvisioningRequest request() {
        OwnerStoreProvisioningRequest request = new OwnerStoreProvisioningRequest();
        request.store_name = "Phase B Controller Store";
        request.store_code = "PHASE_B_CONTROLLER_STORE";
        request.profile_code = "ST_DENIS_CANONICAL_PROFILE";
        request.profile_version = "v2";
        request.master_menu_key = "LANZHOU_CHAIN_MASTER_MENU";
        request.master_menu_version = "v1";
        return request;
    }

    private OwnerStoreProvisioningResult successfulResult() {
        return new OwnerStoreProvisioningResult(
            700L,
            701L,
            "COMPLETED",
            false,
            "PASS",
            "PHASE_B_STORE_PROVISIONED",
            null,
            new OwnerStoreProvisioningCounts(5, 6, 39, 380, 1, 5, 1)
        );
    }
}
