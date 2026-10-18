package com.restaurant.system.menu.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.ItemCustomizationConfiguration;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuItemCustomizationService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OwnerMenuCustomizationControllerTest {
    private final MenuItemRepository items = mock(MenuItemRepository.class);
    private final MenuItemCustomizationService customizations = mock(MenuItemCustomizationService.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final StoreModuleAccessEvaluator modules = mock(StoreModuleAccessEvaluator.class);
    private final OwnerMenuCustomizationController controller = new OwnerMenuCustomizationController(items, customizations,
        authorization, modules, mock(AuditLogService.class));

    @Test void ownerCannotReadOrWriteAnotherExistingStoresRules() {
        var item = item(); when(items.findById(11L)).thenReturn(Optional.of(item));
        doThrow(new BusinessException("FORBIDDEN")).when(authorization).requireForStore(22L, Capability.ADMIN_MENU_MANAGE);
        assertThrows(BusinessException.class, () -> controller.get(11L));
        assertThrows(BusinessException.class, () -> controller.update(11L, new ItemCustomizationConfiguration(), null));
        verifyNoInteractions(customizations);
    }

    @Test void disabledMenuModuleCannotWriteConfiguration() {
        when(items.findById(11L)).thenReturn(Optional.of(item()));
        doThrow(new BusinessException("MODULE_DISABLED")).when(modules).requireCapability(22L, ModuleKeys.MENU_MANAGEMENT);
        assertThrows(BusinessException.class, () -> controller.update(11L, new ItemCustomizationConfiguration(), null));
        verifyNoInteractions(customizations);
    }

    @Test void configurationIsAJsonObjectAndUsesThePersistedStore() throws Exception {
        var item = item(); item.customization_config = new ItemCustomizationConfiguration();
        when(items.findById(11L)).thenReturn(Optional.of(item));
        when(authorization.requireForStore(22L, Capability.ADMIN_MENU_MANAGE)).thenReturn(new AuthenticatedUser(1L, 22L, 1L, "owner", "Owner", "OWNER"));
        when(customizations.configuration(item)).thenReturn(item.customization_config);
        when(customizations.updateConfiguration(11L, 22L, item.customization_config)).thenReturn(item.customization_config);
        assertTrue(controller.update(11L, item.customization_config, null).isSuccess());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(controller.get(11L)));
        assertTrue(json.get("data").isObject());
        assertEquals(1, json.get("data").get("schema_version").asInt());
        verify(customizations).updateConfiguration(11L, 22L, item.customization_config);
    }

    private MenuItem item() { var item = new MenuItem(); item.id = 11L; item.store_id = 22L; return item; }
}
