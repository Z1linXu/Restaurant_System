package com.restaurant.system.owner.controller;

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
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.common.feature.FeatureDisabledException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneRequest;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneResponse;
import com.restaurant.system.owner.dto.OwnerStoreMenuCloneValidationResponse;
import com.restaurant.system.owner.service.OwnerStoreMenuCloneService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OwnerStoreMenuCloneControllerTest {

    private static final long ORGANIZATION_ID = 100L;
    private static final long TARGET_STORE_ID = 200L;
    private static final String KEY = "menu-clone-controller-key";

    @Mock private AuthorizationService authorizationService;
    @Mock private OwnerStoreMenuCloneService menuCloneService;
    @Mock private FeatureFlagService featureFlagService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthenticatedUser owner;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OwnerStoreMenuCloneController(
                authorizationService,
                menuCloneService,
                featureFlagService
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        objectMapper = new ObjectMapper();
        owner = new AuthenticatedUser(1L, 10L, 1L, "owner", "Owner", "OWNER");
    }

    @Test
    void validateUsesOwnerAuthorizationAndDoesNotRequireAnIdempotencyHeader() throws Exception {
        when(authorizationService.requireOwner()).thenReturn(owner);
        when(menuCloneService.validateMenuClone(eq(ORGANIZATION_ID), eq(TARGET_STORE_ID), any(), eq(owner)))
            .thenReturn(validation());

        mockMvc.perform(post(route() + "/validate")
                .contentType("application/json").content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.missing_codes").isArray())
            .andExpect(jsonPath("$.data.category_ids_by_code").doesNotExist());

        verify(authorizationService).requireOwner();
        verify(menuCloneService).validateMenuClone(eq(ORGANIZATION_ID), eq(TARGET_STORE_ID), any(), eq(owner));
    }

    @Test
    void executeForwardsTheKeyAndReturnsOnlySanitizedResponseFields() throws Exception {
        when(authorizationService.requireOwner()).thenReturn(owner);
        when(menuCloneService.cloneMenu(eq(ORGANIZATION_ID), eq(TARGET_STORE_ID), eq(KEY), any(), eq(owner)))
            .thenReturn(response());

        mockMvc.perform(post(route()).header("Idempotency-Key", KEY)
                .contentType("application/json").content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.clone_request_id").value(300))
            .andExpect(jsonPath("$.data.replayed").value(false))
            .andExpect(jsonPath("$.data.item_ids_by_sku").doesNotExist())
            .andExpect(jsonPath("$.data.option_ids").doesNotExist());

        verify(authorizationService).requireOwner();
        verify(menuCloneService).cloneMenu(eq(ORGANIZATION_ID), eq(TARGET_STORE_ID), eq(KEY), any(), eq(owner));
    }

    @Test
    void malformedBodyDoesNotReachAuthorizationOrService() throws Exception {
        mockMvc.perform(post(route() + "/validate").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("MENU_CLONE_REQUEST_INVALID"));

        verifyNoInteractions(authorizationService, menuCloneService);
    }

    @Test
    void platformFeatureGateBlocksCloneRuntimeBeforeOwnerAuthorization() throws Exception {
        doThrow(new FeatureDisabledException(FeaturePackage.PLATFORM))
            .when(featureFlagService).requireEnabled(FeaturePackage.PLATFORM);

        mockMvc.perform(post(route() + "/validate")
                .contentType("application/json").content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("FEATURE_DISABLED"));

        verifyNoInteractions(authorizationService, menuCloneService);
    }

    @Test
    void nonOwnerIsMappedToTheCloneSpecificForbiddenCode() throws Exception {
        when(authorizationService.requireOwner()).thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(post(route() + "/validate")
                .contentType("application/json").content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("MENU_CLONE_FORBIDDEN"));

        verifyNoInteractions(menuCloneService);
    }

    private String route() {
        return "/api/v1/owner/organizations/" + ORGANIZATION_ID + "/stores/" + TARGET_STORE_ID + "/menu-clone";
    }

    private OwnerStoreMenuCloneRequest request() {
        OwnerStoreMenuCloneRequest request = new OwnerStoreMenuCloneRequest();
        request.source_store_id = 10L;
        request.profile_code = "CHINATOWN_MENU_2026_02_02";
        return request;
    }

    private OwnerStoreMenuCloneValidationResponse validation() {
        OwnerStoreMenuCloneValidationResponse response = new OwnerStoreMenuCloneValidationResponse();
        response.valid = true;
        response.profile_code = "CHINATOWN_MENU_2026_02_02";
        response.missing_codes = List.of();
        response.duplicate_codes = List.of();
        response.warnings = List.of();
        return response;
    }

    private OwnerStoreMenuCloneResponse response() {
        OwnerStoreMenuCloneResponse response = new OwnerStoreMenuCloneResponse();
        response.clone_request_id = 300L;
        response.status = "COMPLETED";
        response.result_code = "MENU_CLONE_COMPLETED";
        response.warnings = List.of();
        return response;
    }
}
