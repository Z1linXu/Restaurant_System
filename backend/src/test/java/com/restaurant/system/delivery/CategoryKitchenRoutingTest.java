package com.restaurant.system.delivery;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CategoryKitchenRoutingTest {
    @Test void toggleIsScopedVersionedAndDoesNotTouchMenuPrices() {
        var stores=mock(StoreRepository.class);var revisions=mock(MenuRevisionService.class);var jdbc=mock(JdbcTemplate.class);
        var store=new Store();store.id=1L;store.presentation_json="{\"receipts\":{\"enabled\":true,\"revision\":3}}";
        when(stores.findById(1L)).thenReturn(Optional.of(store));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L),eq("DESSERT"))).thenReturn(1);
        var service=new StorePresentationService(stores,revisions,new ObjectMapper(),jdbc);
        var settings=service.receipts(store);settings.expected_revision=3L;settings.kitchen_category_enabled=Map.of("DESSERT",false);
        var saved=service.saveReceipts(1L,settings);
        assertEquals(4,saved.revision);assertFalse(saved.kitchenEnabled("DESSERT"));assertTrue(saved.kitchenEnabled("RICE"));
        verify(revisions).incrementRevision(1L);verify(stores).flush();
        var stale=service.receipts(store);stale.expected_revision=3L;
        assertThrows(StorePresentationService.ReceiptSettingsConflict.class,()->service.saveReceipts(1L,stale));
    }
    @Test void foreignCategoryAndDirectServeCannotBeRoutedToKitchen() {
        var stores=mock(StoreRepository.class);var revisions=mock(MenuRevisionService.class);var jdbc=mock(JdbcTemplate.class);
        var store=new Store();store.id=1L;when(stores.findById(1L)).thenReturn(Optional.of(store));
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L),eq("FOREIGN"))).thenReturn(0);
        var service=new StorePresentationService(stores,revisions,new ObjectMapper(),jdbc);
        var request=new StorePresentationService.ReceiptSettings();request.kitchen_category_enabled=Map.of("FOREIGN",true);
        assertThrows(BusinessException.class,()->service.saveReceipts(1L,request));verify(stores,never()).save(any());
        when(jdbc.queryForObject(anyString(),eq(Integer.class),eq(1L),eq("DRINK"))).thenReturn(1);request.kitchen_category_enabled=Map.of("DRINK",true);
        assertThrows(BusinessException.class,()->service.saveReceipts(1L,request));
    }
    @Test void disabledPrintingModuleCannotBeBypassedViaReceiptSettings() {
        var service=mock(StorePresentationService.class);var auth=mock(AuthorizationService.class);var modules=mock(StoreModuleAccessEvaluator.class);
        doThrow(new BusinessException("MODULE_DISABLED")).when(modules).requireOperationalModuleEnabled(1L,ModuleKeys.PRINTING);
        var controller=new StorePresentationController(service,auth,modules);
        assertThrows(BusinessException.class,()->controller.receipts(1L));
        assertThrows(BusinessException.class,()->controller.saveReceipts(1L,new StorePresentationService.ReceiptSettings()));verifyNoInteractions(service);
    }
}
