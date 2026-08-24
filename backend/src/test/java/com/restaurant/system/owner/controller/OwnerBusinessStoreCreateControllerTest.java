package com.restaurant.system.owner.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.owner.dto.OwnerBusinessStoreCreateResponse;
import com.restaurant.system.owner.dto.OwnerStoreProvisioningRequest;
import com.restaurant.system.owner.service.OwnerBusinessStoreCreateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class OwnerBusinessStoreCreateControllerTest {

    private static final long ORGANIZATION_ID = 100L;
    private static final String ROUTE = "/api/v1/owner/organizations/100/stores";

    @Mock private AuthorizationService authorizationService;
    @Mock private OwnerOrganizationAuthorizationService organizationAuthorizationService;
    @Mock private OwnerBusinessStoreCreateService createService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AuthenticatedUser owner;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OwnerBusinessStoreCreateController(
                authorizationService,
                organizationAuthorizationService,
                createService
            ))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        objectMapper = new ObjectMapper();
        owner = new AuthenticatedUser(1L, null, 10L, "owner", "Owner", "OWNER");
    }

    @Test
    void organizationOwnerCanReadCatalogAndCreateWithoutExistingStoreMembership() throws Exception {
        when(authorizationService.requireOwner()).thenReturn(owner);
        when(createService.create(any(), any(), any(), any())).thenReturn(created());

        mockMvc.perform(get(ROUTE + "/create-catalog"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(post(ROUTE)
                .header("Idempotency-Key", "business-create-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.store_kind").value("BUSINESS"))
            .andExpect(jsonPath("$.data.lifecycle_status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.is_live").value(true));

        verify(organizationAuthorizationService, org.mockito.Mockito.times(2))
            .requireActiveOwnerMembership(owner, ORGANIZATION_ID);
        verify(createService).create(eq(owner), eq(ORGANIZATION_ID), eq("business-create-key"), any());
    }

    @Test
    void managerOrFrontdeskIsDeniedBeforeOrganizationLookup() throws Exception {
        when(authorizationService.requireOwner()).thenThrow(new ForbiddenException("owner required"));

        mockMvc.perform(post(ROUTE)
                .header("Idempotency-Key", "denied-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("BUSINESS_STORE_CREATE_AUTHORIZATION_DENIED"));

        verifyNoInteractions(organizationAuthorizationService, createService);
    }

    @Test
    void ownerFromWrongOrganizationOrWithoutMembershipIsDenied() throws Exception {
        when(authorizationService.requireOwner()).thenReturn(owner);
        doThrow(new ForbiddenException("membership required"))
            .when(organizationAuthorizationService)
            .requireActiveOwnerMembership(owner, ORGANIZATION_ID);

        mockMvc.perform(post(ROUTE)
                .header("Idempotency-Key", "wrong-org-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error_code").value("BUSINESS_STORE_CREATE_ORGANIZATION_DENIED"));

        verifyNoInteractions(createService);
    }

    private OwnerStoreProvisioningRequest request() {
        OwnerStoreProvisioningRequest request = new OwnerStoreProvisioningRequest();
        request.store_name = "Business Store";
        request.store_code = "BUSINESS_STORE";
        return request;
    }

    private OwnerBusinessStoreCreateResponse created() {
        OwnerBusinessStoreCreateResponse response = new OwnerBusinessStoreCreateResponse();
        response.store_id = 200L;
        response.store_kind = "BUSINESS";
        response.lifecycle_status = "ACTIVE";
        response.operational_state = "LIVE";
        response.is_live = true;
        return response;
    }
}
