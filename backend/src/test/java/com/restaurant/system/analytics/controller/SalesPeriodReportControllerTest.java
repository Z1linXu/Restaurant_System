package com.restaurant.system.analytics.controller;

import com.restaurant.system.analytics.service.AnalyticsReadScope;
import com.restaurant.system.analytics.service.SalesPeriodReportService;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.exception.GlobalExceptionHandler;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.user.entity.Store;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SalesPeriodReportControllerTest {
    AuthorizationService auth = mock(AuthorizationService.class);
    StoreAccessService access = mock(StoreAccessService.class);
    StoreModuleAccessEvaluator modules = mock(StoreModuleAccessEvaluator.class);
    SalesPeriodReportService reports = mock(SalesPeriodReportService.class);
    AuthenticatedUser owner = new AuthenticatedUser(10L, 1L, 1L, "owner", "Owner", "OWNER");
    Store own = new Store();
    MockMvc mvc;

    @BeforeEach void setup() {
        own.id = 1L; own.organization_id = 1L;
        when(auth.require(Capability.ADMIN_STORE_CONFIG)).thenReturn(owner);
        when(access.accessibleStores(owner)).thenReturn(List.of(own));
        var scope = new AnalyticsReadScope(auth, access, new RoleCapabilityRegistry());
        mvc = MockMvcBuilders.standaloneSetup(new SalesPeriodReportController(auth, modules, scope, reports))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void foreignStoreCannotReachFinancialReadEvenIfCapabilityMockAllowsIt() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/period-report").param("store_id", "2")
                .param("period", "month").param("date", "2026-11-03")).andExpect(status().isForbidden());
        verifyNoInteractions(reports);
    }

    @Test void ownStoreUsesExistingStoreAuthorizationAndReportingCapability() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/period-report").param("store_id", "1")
                .param("period", "month").param("date", "2026-11-03")).andExpect(status().isOk());
        verify(auth).requireForStore(1L, Capability.ADMIN_STORE_CONFIG);
        verify(modules).requireCapability(1L, ModuleKeys.REPORTING_CORE);
        verify(reports).build(own, "month", LocalDate.of(2026, 11, 3));
    }

    @Test void malformedDateIsBadRequestWithoutInvokingReport() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/period-report").param("store_id", "1")
                .param("date", "2026-99-01")).andExpect(status().isBadRequest());
        verifyNoInteractions(reports);
    }

    @Test void missingStoreOrDateIsBadRequestAndNeverBecomesGlobalRead() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/period-report").param("date", "2026-11-03"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/analytics/period-report").param("store_id", "1"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reports, modules, access);
    }
}
