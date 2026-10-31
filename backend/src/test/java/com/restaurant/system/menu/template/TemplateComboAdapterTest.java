package com.restaurant.system.menu.template;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.dto.StoreComboConfigurationResponse;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.station.entity.Station;
import com.restaurant.system.station.repository.StationRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateComboAdapterTest {
 private final StoreComboConfigurationService configuration=mock(StoreComboConfigurationService.class);
 private final MenuItemRepository items=mock(MenuItemRepository.class);
 private final StationRepository stations=mock(StationRepository.class);
 private final StoreComboConfigurationResponse.ComponentResponse component=new StoreComboConfigurationResponse.ComponentResponse();
 private final Station station=new Station();
 private TemplateComboAdapter adapter;
 private CreateOrderItemOptionRequest selected;
 @BeforeEach void setup(){
  var group=new StoreComboConfigurationResponse.GroupResponse();group.enabled=true;group.component_group="COMBO_SIDE";
  component.enabled=true;component.component_code="combo_cucumber_salad";component.business_behavior="LEGACY_COMBO_SIDE_TASK";component.linked_menu_item_id=8L;group.components.add(component);
  var config=new StoreComboConfigurationResponse();config.groups.add(group);when(configuration.getConfiguration(1L)).thenReturn(config);
  var item=new MenuItem();item.id=8L;item.store_id=1L;item.station_id=3L;item.is_active=true;item.is_sold_out=false;when(items.findById(8L)).thenReturn(Optional.of(item));
  station.id=3L;station.code="COLD";when(stations.findActiveStationByIdAndStoreId(3L,1L)).thenReturn(station);
  adapter=new TemplateComboAdapter(configuration,items,stations);
  selected=new CreateOrderItemOptionRequest();selected.option_id=-20203L;selected.option_code_snapshot=component.component_code;
  selected.option_group_snapshot="COMBO_SIDE";selected.option_price_snapshot=BigDecimal.ZERO;selected.quantity=1;
 }
 @Test void legacyColdMappingRetainsStableIdentity(){var resolved=adapter.resolve(1L,selected);assertEquals(8L,resolved.linkedItemId());assertEquals("COMBO_SIDE",resolved.group());}
 @Test void missingCodeCannotProduceTicketWithoutTaskIdentity(){selected.option_code_snapshot=null;assertThrows(BusinessException.class,()->adapter.resolve(1L,selected));}
 @Test void missingGroupCannotProduceTicketWithoutTaskIdentity(){selected.option_group_snapshot=null;assertThrows(BusinessException.class,()->adapter.resolve(1L,selected));}
 @Test void forgedCodeCannotBorrowStableVirtualId(){selected.option_code_snapshot="combo_edamame";assertThrows(BusinessException.class,()->adapter.resolve(1L,selected));}
 @Test void unsupportedNoTaskSideIsRejected(){component.business_behavior="NO_KITCHEN_TASK";assertThrows(BusinessException.class,()->adapter.resolve(1L,selected));}
 @Test void unsupportedGeneralLinkedTaskIsRejected(){component.business_behavior="LINKED_MENU_ITEM";assertThrows(BusinessException.class,()->adapter.resolve(1L,selected));}
 @Test void hotLinkedItemCannotDisagreeWithLegacyColdTask(){station.code="WOK";assertThrows(BusinessException.class,()->adapter.resolve(1L,selected));}
 @Test void unavailableLinkedItemCannotBeSelected(){when(stations.findActiveStationByIdAndStoreId(3L,1L)).thenReturn(null);assertThrows(BusinessException.class,()->adapter.resolve(1L,selected));}
}
