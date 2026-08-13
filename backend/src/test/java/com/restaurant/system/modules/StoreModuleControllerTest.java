package com.restaurant.system.modules;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.common.auth.RequestUserContextService;
import com.restaurant.system.common.auth.StoreAccessService;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.modules.dto.StoreModuleConfigurationResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class StoreModuleControllerTest {

    @Mock
    private RequestUserContextService requestUserContextService;
    @Mock
    private StoreAccessService storeAccessService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private StoreModuleService storeModuleService;

    private MockMvc mockMvc;
    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        StoreModuleController controller = new StoreModuleController(
            requestUserContextService,
            storeAccessService,
            authorizationService,
            storeModuleService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        user = new AuthenticatedUser(1L, 10L, 1L, "owner", "Owner User", "OWNER");
    }

    @Test
    void readStoreModulesRequiresStoreAccess() throws Exception {
        when(requestUserContextService.getRequiredUser()).thenReturn(user);
        when(storeModuleService.getConfiguration(10L)).thenReturn(validResponse());

        mockMvc.perform(get("/api/v1/stores/10/modules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.store_id").value(10))
            .andExpect(jsonPath("$.data.validation_status").value("VALID"));

        verify(storeAccessService).requireStoreAccess(user, 10L);
    }

    @Test
    void updateStoreModulesRequiresConfigurationAuthority() throws Exception {
        when(storeModuleService.updateConfiguration(any(), any())).thenReturn(validResponse());

        mockMvc.perform(put("/api/v1/admin/stores/10/modules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"store_id\":10,\"modules\":[{\"module_key\":\"KDS\",\"enabled\":false}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.store_id").value(10));

        verify(authorizationService).requireManagerOrOwnerForStore(10L, Capability.ADMIN_STORE_CONFIG);
    }

    @Test
    void updateStoreModulesRejectsUnauthorizedStaff() throws Exception {
        doThrow(new ForbiddenException("Access denied"))
            .when(authorizationService).requireManagerOrOwnerForStore(10L, Capability.ADMIN_STORE_CONFIG);

        mockMvc.perform(put("/api/v1/admin/stores/10/modules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"store_id\":10,\"modules\":[{\"module_key\":\"KDS\",\"enabled\":false}]}"))
            .andExpect(status().isForbidden());
    }

    private StoreModuleConfigurationResponse validResponse() {
        StoreModuleConfigurationResponse response = new StoreModuleConfigurationResponse();
        response.store_id = 10L;
        response.valid = true;
        response.validation_status = "VALID";
        response.modules = List.of();
        response.validation_issues = List.of();
        return response;
    }
}
