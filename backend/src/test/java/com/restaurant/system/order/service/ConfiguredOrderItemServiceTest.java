package com.restaurant.system.order.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.addon.StoreAddonOptionResolver;
import com.restaurant.system.menu.dto.ItemCustomizationConfiguration;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.MenuItemCustomizationService;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.station.repository.StationRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfiguredOrderItemServiceTest {
    final MenuItemRepository items = mock(MenuItemRepository.class);
    final MenuItemOptionRepository options = mock(MenuItemOptionRepository.class);
    final StoreComboConfigurationService combos = mock(StoreComboConfigurationService.class);
    final StoreAddonOptionResolver addons = mock(StoreAddonOptionResolver.class);
    final MenuItem item = new MenuItem();
    ConfiguredOrderItemService service;
    @BeforeEach void setup() {
        item.id=1L; item.store_id=10L; item.base_price=new BigDecimal("15.99"); item.is_active=true; item.sellable=true;
        item.customization_config=new ItemCustomizationConfiguration();
        when(addons.resolve(anyList())).thenAnswer(i -> i.getArgument(0));
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of());
        var config=new StoreComboConfigurationResponse(); when(combos.getConfiguration(item.store_id)).thenReturn(config);
        var rules=new MenuItemCustomizationService(items,options,combos,mock(MenuRevisionService.class),addons,mock(StationRepository.class));
        service=new ConfiguredOrderItemService(rules,options,items,combos,addons);
    }
    MenuItemOption option(long id,String group,String code,String price) {
        var o=new MenuItemOption();o.id=id;o.menu_item_id=item.id;o.is_active=true;o.option_group=group;o.option_type="modifier";
        o.option_code=code;o.price_delta=new BigDecimal(price);o.name_zh=code;o.name_en=code;o.name_fr="fr-"+code;return o;
    }
    CreateOrderItemOptionRequest request(long id,int quantity,String price) {
        var r=new CreateOrderItemOptionRequest();r.option_id=id;r.quantity=quantity;r.option_price_snapshot=new BigDecimal(price);return r;
    }
    void group(String code,int min,int max) {
        var g=new ItemCustomizationConfiguration.OptionGroup();g.option_group=code;g.min_select=min;g.max_select=max;
        item.customization_config.option_groups.add(g);
    }
    @Test void c5RequiresExactlyTwoDistinctChoicesForEveryServing() {
        group("PROTEIN",2,2);
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of(option(1,"PROTEIN","beef","0"),option(2,"PROTEIN","chicken","0")));
        assertDoesNotThrow(() -> service.canonicalize(item,3,List.of(request(1,3,"0"),request(2,3,"0"))));
        assertThrows(BusinessException.class,() -> service.canonicalize(item,3,List.of(request(1,3,"0"))));
        assertThrows(BusinessException.class,() -> service.canonicalize(item,3,List.of(request(1,6,"0"))));
    }
    @Test void quantitiesCannotMaskMissingSelectionOnOneOfThreeMeals() {
        group("PROTEIN",2,2);
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of(option(1,"PROTEIN","beef","0"),option(2,"PROTEIN","chicken","0")));
        assertThrows(BusinessException.class,() -> service.canonicalize(item,3,List.of(request(1,2,"0"),request(2,3,"0"))));
    }
    @Test void freeAddonZeroPriceIsValidButTwoPerServingIsRejected() {
        item.customization_config.addon_max_quantities.put("extra_noodles",1);
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of(option(1,"ADD_ON","extra_noodles","0")));
        assertDoesNotThrow(() -> service.canonicalize(item,3,List.of(request(1,3,"0"))));
        assertThrows(BusinessException.class,() -> service.canonicalize(item,3,List.of(request(1,6,"0"))));
    }
    @Test void callerCannotInjectUnknownOptionOrForeignComponent() {
        assertThrows(BusinessException.class,() -> service.canonicalize(item,1,List.of(request(999,1,"0"))));
        assertThrows(BusinessException.class,() -> service.canonicalize(item,1,List.of(request(-1000000123,1,"0"))));
    }
    @Test void callerCannotReplacePaidAddonPriceOrCanonicalLabels() {
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of(option(1,"ADD_ON","extra_beef","7.99")));
        assertThrows(BusinessException.class,() -> service.canonicalize(item,1,List.of(request(1,1,"0"))));
        var selected=request(1,1,"7.99");selected.option_name_snapshot_fr="forged";
        service.canonicalize(item,1,List.of(selected));assertEquals("fr-extra_beef",selected.option_name_snapshot_fr);
    }
    @Test void componentOnlyCannotBePurchasedAndBasePriceCannotBeForged() {
        assertThrows(BusinessException.class,() -> service.validateItem(item,new BigDecimal("0")));
        item.sellable=false;assertThrows(BusinessException.class,() -> service.validateItem(item,new BigDecimal("15.99")));
    }
    @Test void excludedMainCannotUseAnyComboMarker() {
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of(option(1,"COMBO","combo","4.00")));
        assertThrows(BusinessException.class,() -> service.canonicalize(item,1,List.of(request(1,1,"4.00"))));
    }
    @Test void fixedComboUsesServerDeltaAndRequiresItsRealComponent() {
        var combo=new ItemCustomizationConfiguration.Combo();combo.fixed_price=new BigDecimal("19.99");combo.group_codes=List.of("APPETIZER");item.customization_config.combo=combo;
        var configuration=new StoreComboConfigurationResponse();var g=new StoreComboConfigurationResponse.GroupResponse();
        g.group_code="APPETIZER";g.enabled=true;g.required=true;g.selection_rule="EXACTLY_ONE";
        var c=new StoreComboConfigurationResponse.ComponentResponse();c.id=12L;c.component_code="spring_rolls";c.enabled=true;
        c.business_behavior="LINKED_MENU_ITEM";c.linked_menu_item_id=2L;c.name_zh="春卷（2条）";g.components=List.of(c);configuration.groups=List.of(g);
        when(combos.getConfiguration(item.store_id)).thenReturn(configuration);
        when(options.findAllByMenuItemIdOrdered(item.id)).thenReturn(List.of(option(1,"COMBO","combo","5.00")));
        var linked=new MenuItem();linked.id=2L;linked.store_id=item.store_id;linked.station_id=9L;linked.is_active=true;linked.sellable=false;
        when(items.findById(2L)).thenReturn(Optional.of(linked));
        assertThrows(BusinessException.class,() -> service.canonicalize(item,3,List.of(request(1,3,"4"))));
        long transport=MenuItemCustomizationService.componentOptionId(12);
        var result=service.canonicalize(item,3,List.of(request(1,3,"4"),request(transport,3,"0")));
        assertEquals(2L,result.get(transport).menuItemId());assertEquals(9L,result.get(transport).stationId());
        linked.store_id=999L;
        assertThrows(BusinessException.class,() -> service.canonicalize(item,3,List.of(request(1,3,"4"),request(transport,3,"0"))));
    }
}
