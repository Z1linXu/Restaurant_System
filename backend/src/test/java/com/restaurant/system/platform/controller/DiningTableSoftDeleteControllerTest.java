package com.restaurant.system.platform.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.platform.service.PlatformAdminService;
import com.restaurant.system.station.entity.DiningTable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DiningTableSoftDeleteControllerTest {
    private final PlatformAdminService service = mock(PlatformAdminService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final StoreModuleAccessEvaluator modules = mock(StoreModuleAccessEvaluator.class);
    private final PlatformAdminController admin = new PlatformAdminController(service, authorization,
        mock(FeatureFlagService.class), mock(AuditLogService.class), modules);

    @Test
    void deleteRouteRequiresStoreAndReturnsTheTombstoneWithExistingCapabilityAndModuleGuards() throws Exception {
        DiningTable row = new DiningTable(); row.id = 22L; row.store_id = 2L; row.is_active = false;
        when(service.deleteDiningTable(22L, 2L)).thenReturn(row);
        var mvc = MockMvcBuilders.standaloneSetup(admin).build();
        mvc.perform(delete("/api/v1/admin/platform/dining-tables/22").param("store_id", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(22)).andExpect(jsonPath("$.data.is_active").value(false));
        var sequence = inOrder(authorization, modules, service);
        sequence.verify(authorization).requireForStore(2L, Capability.ADMIN_STORE_CONFIG);
        sequence.verify(modules).requireCapability(2L, ModuleKeys.TABLE_MANAGEMENT);
        sequence.verify(service).deleteDiningTable(22L, 2L);
        mvc.perform(delete("/api/v1/admin/platform/dining-tables/22")).andExpect(status().isBadRequest());
    }

    @Test
    void deniedStoreOrModuleCannotReachDelete() {
        doThrow(new BusinessException("FORBIDDEN")).when(authorization).requireForStore(2L, Capability.ADMIN_STORE_CONFIG);
        assertThatThrownBy(() -> admin.deleteDiningTable(22L, 2L)).hasMessageContaining("FORBIDDEN");
        verifyNoInteractions(service, modules);
        reset(authorization);
        doThrow(new BusinessException("MODULE_DISABLED")).when(modules).requireCapability(2L, ModuleKeys.TABLE_MANAGEMENT);
        assertThatThrownBy(() -> admin.deleteDiningTable(22L, 2L)).hasMessageContaining("MODULE_DISABLED");
        verifyNoInteractions(service);
    }

    @Test
    void frontdeskAndAdminBothUseTheFilteredListWithoutChangingTheirReadPermissions() {
        DiningTable row = new DiningTable(); row.id = 22L; row.store_id = 2L; row.is_active = true;
        when(service.getDiningTables(2L)).thenReturn(List.of(row));
        FrontdeskConfigController frontdesk = new FrontdeskConfigController(service, authorization, modules);
        assertThat(admin.getDiningTables(2L).getData()).containsExactly(row);
        assertThat(frontdesk.getDiningTables(2L).getData()).containsExactly(row);
        verify(authorization).requireForStore(2L, Capability.ADMIN_STORE_CONFIG);
        verify(authorization).requireForStore(2L, Capability.ORDER_VIEW_ACTIVE, Capability.ORDER_CREATE);
        verify(modules).requireCapability(2L, ModuleKeys.TABLE_MANAGEMENT);
        verify(modules).requireOperationalCapability(2L, ModuleKeys.TABLE_MANAGEMENT);
    }
}
