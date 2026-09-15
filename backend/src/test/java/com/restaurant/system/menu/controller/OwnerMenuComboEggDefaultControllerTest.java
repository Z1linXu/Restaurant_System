package com.restaurant.system.menu.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.AuthenticatedUser;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.MenuItemComboEggDefaultRequest;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.menu.service.StorePricingPolicyService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OwnerMenuComboEggDefaultControllerTest {
    private final MenuItemRepository items = mock(MenuItemRepository.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final StoreComboConfigurationService combo = mock(StoreComboConfigurationService.class);
    private final StoreModuleAccessEvaluator modules = mock(StoreModuleAccessEvaluator.class);
    private final OwnerMenuPricingPolicyController controller = new OwnerMenuPricingPolicyController(
        mock(StorePricingPolicyService.class), combo, items, authorization, mock(AuditLogService.class), modules);

    @Test
    void authorizesUsingPersistedItemStoreAndReturnsStandardEnvelope() {
        MenuItem item = item();
        when(items.findById(22L)).thenReturn(Optional.of(item));
        when(authorization.requireForStore(9L, Capability.ADMIN_MENU_MANAGE))
            .thenReturn(new AuthenticatedUser(1L, 1L, 1L, "owner", "Owner", "OWNER"));
        MenuItemComboEggDefaultRequest request = new MenuItemComboEggDefaultRequest();
        request.default_combo_egg_component_code = "combo_fried_egg";

        assertTrue(controller.updateComboEggDefault(22L, request, null).isSuccess());

        verify(authorization).requireForStore(9L, Capability.ADMIN_MENU_MANAGE);
        verify(modules).requireCapability(9L, ModuleKeys.MENU_MANAGEMENT);
        verify(combo).updateItemEggDefault(22L, 9L, request);
    }

    @Test
    void deniedStoreAuthorizationCannotReachMutation() {
        when(items.findById(22L)).thenReturn(Optional.of(item()));
        doThrow(new BusinessException("FORBIDDEN"))
            .when(authorization).requireForStore(9L, Capability.ADMIN_MENU_MANAGE);

        assertThrows(BusinessException.class, () -> controller.updateComboEggDefault(22L,
            new MenuItemComboEggDefaultRequest(), null));

        verifyNoInteractions(combo);
    }

    @Test
    void adminItemResponseIncludesTheOverrideField() throws Exception {
        MenuItem item = item();
        item.default_combo_egg_component_code = "combo_fried_egg";
        assertTrue(new ObjectMapper().writeValueAsString(item)
            .contains("\"default_combo_egg_component_code\":\"combo_fried_egg\""));
    }

    private MenuItem item() {
        MenuItem item = new MenuItem();
        item.id = 22L;
        item.store_id = 9L;
        return item;
    }
}
