package com.restaurant.system.common.auth;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.auth.filter.AuthTokenFilter;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.kitchen.repository.KitchenTaskRepository;
import com.restaurant.system.merchant.*;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.order.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.RequestContextFilter;

class MerchantConfigurationHttpTest {
    CommercialSecurityFixture f;
    MockMvc mvc;
    StoreMerchantConfigurationRepository configs;
    AuditLogService audit;
    jakarta.persistence.EntityManager entityManager;
    final String body = "{\"schema_version\":1,\"expected_config_revision\":0,\"brand_name\":\"Synthetic Brand\",\"locale\":\"en-CA\"}";
    @BeforeEach void setup() {
        f = new CommercialSecurityFixture(); configs = mock(StoreMerchantConfigurationRepository.class);
        audit = mock(AuditLogService.class); entityManager = mock(jakarta.persistence.EntityManager.class);
        when(f.stores.findByIdForUpdate(anyLong())).thenAnswer(i -> Optional.ofNullable(f.storeRows.get(i.getArgument(0))));
        var auth = new AuthorizationService(f.context, f.capabilities, f.access, mock(OrderRepository.class),
            mock(OrderItemRepository.class), mock(KitchenTaskRepository.class));
        var service = new MerchantConfigurationService(configs, f.stores, auth, mock(MenuRevisionService.class), audit,
            "America/Toronto", entityManager);
        mvc = MockMvcBuilders.standaloneSetup(new MerchantConfigurationController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestContextFilter(), new AuthTokenFilter(f.identities, new ObjectMapper())).build();
    }
    @Test void authenticatedOwnerSeesOwnSafeSummaryAndPointersOnly() throws Exception {
        mvc.perform(get("/api/v1/stores/10/merchant-configuration").header("Authorization", f.bearer(1)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.safe_summary.profile_code").value("NEUTRAL_RESTAURANT"))
            .andExpect(jsonPath("$.data.organization_id").value(100))
            .andExpect(jsonPath("$.data.resource_references[0].api_path").value("/api/v1/stores/10/modules"));
        verifyNoInteractions(audit);
    }
    @Test void anonymousAndForeignOwnerDeniedBeforeConfigurationReadOrWrite() throws Exception {
        mvc.perform(get("/api/v1/stores/10/merchant-configuration")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/stores/20/merchant-configuration").header("Authorization", f.bearer(1))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/stores/20/merchant-configuration").header("Authorization", f.bearer(1)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(configs, audit);
    }
    @ParameterizedTest @ValueSource(longs = {3,4,5,7,8,9})
    void managerStaffAdminOrRoleMembershipEscalationCannotConfigure(long user) throws Exception {
        long store = user == 7 ? 11 : 10;
        mvc.perform(put("/api/v1/stores/" + store + "/merchant-configuration").header("Authorization", f.bearer(user))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(configs, audit);
    }
    @ParameterizedTest @ValueSource(strings = {"store_id","organization_id","station_id","table_id","printer_id","device_id","menu_item_id","option_id","combo_id","resource_references"})
    void foreignReferenceFieldsCannotEnterWriteContract(String field) throws Exception {
        String extra = body.substring(0, body.length()-1) + ",\"" + field + "\":20}";
        mvc.perform(put("/api/v1/stores/10/merchant-configuration").header("Authorization", f.bearer(1))
            .contentType(MediaType.APPLICATION_JSON).content(extra)).andExpect(status().isBadRequest());
        verifyNoInteractions(configs, audit);
    }
    @ParameterizedTest @ValueSource(strings = {"", "{", "{invalid}"})
    void malformedJsonReturnsLocalBadRequestWithoutAnyConfigurationWrite(String malformed) throws Exception {
        mvc.perform(put("/api/v1/stores/10/merchant-configuration").header("Authorization", f.bearer(1))
            .contentType(MediaType.APPLICATION_JSON).content(malformed))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error_code").value("MERCHANT_CONFIGURATION_INVALID_JSON"))
            .andExpect(jsonPath("$.message").value("Invalid merchant configuration JSON"));
        verifyNoInteractions(configs, audit);
    }

    @Test void unsupportedVersionAndStaleLockScopeFailClosed() throws Exception {
        mvc.perform(put("/api/v1/stores/10/merchant-configuration").header("Authorization", f.bearer(1))
            .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"schema_version\":1", "\"schema_version\":2")))
            .andExpect(status().isBadRequest());
        // Deterministic OSIV stale-entity window: refresh observes the committed foreign move.
        doAnswer(i -> { f.storeRows.get(10L).organization_id = 200L; return null; })
            .when(entityManager).refresh(any(), eq(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        mvc.perform(put("/api/v1/stores/10/merchant-configuration").header("Authorization", f.bearer(1))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(configs, audit);
    }
}
