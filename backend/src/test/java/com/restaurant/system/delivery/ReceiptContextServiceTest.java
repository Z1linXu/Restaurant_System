package com.restaurant.system.delivery;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.order.entity.*;
import com.restaurant.system.order.repository.*;
import com.restaurant.system.printing.entity.OrderDispatchOutbox;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.user.entity.Store;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
class ReceiptContextServiceTest {
 StorePresentationService presentation=mock(StorePresentationService.class);
 OrderRepository orders=mock(OrderRepository.class);OrderItemRepository items=mock(OrderItemRepository.class);
 OrderItemOptionRepository options=mock(OrderItemOptionRepository.class);OrderDispatchOutboxRepository events=mock(OrderDispatchOutboxRepository.class);
 com.restaurant.system.printing.AlloProductionPrintingPolicy printingPolicy=mock(com.restaurant.system.printing.AlloProductionPrintingPolicy.class);
 ReceiptContextService service=new ReceiptContextService(presentation,orders,items,options,events,new ObjectMapper(),printingPolicy);
 Store store=new Store();Order order=new Order();OrderItem item=new OrderItem();OrderItemOption option=new OrderItemOption();
 @BeforeEach void setup(){store.id=1L;store.code="ALLO_NOUILLES_TEST";store.printing_mode="MOCK";store.name="Synthetic";
  order.id=10L;order.store_id=1L;order.subtotal_amount=new BigDecimal("19.99");order.total_amount=new BigDecimal("22.98");
  item.id=20L;item.order_id=10L;item.item_name_snapshot_zh="合成菜";item.item_name_snapshot_en="Synthetic dish";item.item_name_snapshot_fr="Plat synthétique";
  option.id=30L;option.order_item_id=20L;option.option_name_snapshot_en="Synthetic choice";
 }
 void enabled(){var settings=new StorePresentationService.ReceiptSettings();settings.enabled=true;settings.default_language="fr-CA";settings.revision=4;settings.kitchen_station_codes=List.of("BACK_KITCHEN");
  when(presentation.receipts(store)).thenReturn(settings);when(presentation.publicView(store)).thenReturn(Map.of("display_name","Synthetic"));
  when(orders.findById(10L)).thenReturn(Optional.of(order));when(items.findAllByOrderId(10L)).thenReturn(List.of(item));when(options.findAllByOrderItemIds(List.of(20L))).thenReturn(List.of(option));}
 @Test void freezeResolvesTrustedSnapshotsAndExplicitFallbackAndMoney(){enabled();var context=service.freeze(store,"GRAB",10L,null);
  assertEquals("MOCK",context.execution_mode);
  assertEquals("fr-CA",context.language);assertEquals("Plat synthétique",context.item_names.get("20"));assertEquals("Synthetic choice",context.option_names.get("30"));
  assertEquals(new BigDecimal("19.99"),context.subtotal);assertEquals(4,context.settings_revision);assertEquals(List.of(20L),context.order_item_ids);
 }
 @Test void eventKeepsOriginalLanguageNamesIdsAndTotalsAfterLiveChanges(){enabled();var frozen=service.freeze(store,"GRAB",10L,null);
  var event=new OrderDispatchOutbox();event.storeId=1L;event.orderId=10L;event.moduleCode="GRAB";event.routingContext=service.serialize(frozen);
  when(events.findBySourceKey("submit:10:GRAB")).thenReturn(Optional.of(event));
  item.item_name_snapshot_fr="Changed";order.total_amount=new BigDecimal("99.00");clearInvocations(items,orders,presentation);
  var context=service.forEvent(store,"GRAB",10L,null);assertEquals("Plat synthétique",context.item_names.get("20"));assertEquals(new BigDecimal("22.98"),context.total);
  verifyNoInteractions(items,orders,presentation);
 }
 @Test void legacyEventDoesNotRetroactivelyEnableCurrentSettings(){var event=new OrderDispatchOutbox();event.storeId=1L;event.orderId=10L;event.moduleCode="GRAB";
  when(events.findBySourceKey("submit:10:GRAB")).thenReturn(Optional.of(event));assertNull(service.forEvent(store,"GRAB",10L,null));verifyNoInteractions(presentation,orders,items);}
 @Test void foreignEventFailsClosed(){var event=new OrderDispatchOutbox();event.storeId=2L;event.orderId=10L;event.moduleCode="GRAB";when(events.findBySourceKey("submit:10:GRAB")).thenReturn(Optional.of(event));assertThrows(RuntimeException.class,()->service.forEvent(store,"GRAB",10L,null));}
 @Test void oldStoreAndHotKeepLegacyWithoutReadingConfiguration(){store.code="LEGACY";assertNull(service.freeze(store,"GRAB",10L,null));store.code="ALLO_NOUILLES_TEST";assertNull(service.freeze(store,"HOT_KITCHEN",10L,null));verifyNoInteractions(presentation,orders,items);}
 @Test void realModeCannotUseMockProof(){enabled();store.printing_mode="REAL";assertThrows(RuntimeException.class,()->service.freeze(store,"GRAB",10L,null));}
 @Test void disabledPrintingStillFreezesWithoutBlockingOrder(){enabled();store.printing_mode="DISABLED";assertNotNull(service.freeze(store,"GRAB",10L,null));}
 @Test void padRequiresTheExplicitControlledRuntime(){enabled();store.printing_mode="PAD_DIRECT";assertThrows(RuntimeException.class,()->service.freeze(store,"GRAB",10L,null));when(printingPolicy.allowsPadDirect(store)).thenReturn(true);assertNotNull(service.freeze(store,"GRAB",10L,null));}
 @Test void savedContextCannotBypassCurrentPadRuntime(){var event=new OrderDispatchOutbox();event.storeId=1L;event.orderId=10L;event.moduleCode="GRAB";event.routingContext="{\"version\":\"ALLO_RECEIPT_V1\"}";when(events.findBySourceKey("submit:10:GRAB")).thenReturn(Optional.of(event));store.printing_mode="PAD_DIRECT";assertThrows(RuntimeException.class,()->service.forEvent(store,"GRAB",10L,null));}
 @Test void historicalReceiptIntentCannotBecomePhysicalAfterStoreSwitchesToPad(){
  enabled();var frozen=service.freeze(store,"GRAB",10L,null);assertEquals("MOCK",frozen.execution_mode);
  var event=new OrderDispatchOutbox();event.storeId=1L;event.orderId=10L;event.moduleCode="GRAB";
  when(events.findBySourceKey("submit:10:GRAB")).thenReturn(Optional.of(event));
  store.printing_mode="PAD_DIRECT";when(printingPolicy.allowsPadDirect(store)).thenReturn(true);
  for(String capturedMode:Arrays.asList(null,"MOCK","DISABLED")){
   frozen.execution_mode=capturedMode;event.routingContext=service.serialize(frozen);String original=event.routingContext;
   assertThrows(RuntimeException.class,()->service.forEvent(store,"GRAB",10L,null));assertEquals(original,event.routingContext);
  }
  event.routingContext=null;assertThrows(RuntimeException.class,()->service.forEvent(store,"GRAB",10L,null));
 }
 @Test void explicitPadReceiptIntentReplaysItsFrozenContextWithoutReinterpretingLiveData(){
  enabled();store.printing_mode="PAD_DIRECT";when(printingPolicy.allowsPadDirect(store)).thenReturn(true);
  var frozen=service.freeze(store,"GRAB",10L,null);assertEquals("PAD_DIRECT",frozen.execution_mode);
  var event=new OrderDispatchOutbox();event.storeId=1L;event.orderId=10L;event.moduleCode="GRAB";event.routingContext=service.serialize(frozen);
  when(events.findBySourceKey("submit:10:GRAB")).thenReturn(Optional.of(event));clearInvocations(items,orders,presentation);
  for(int replay=0;replay<2;replay++)assertEquals(event.routingContext,service.serialize(service.forEvent(store,"GRAB",10L,null)));
  verifyNoInteractions(items,orders,presentation);
 }
 @Test void corruptNewJobMarkerCannotFallBackToLegacy(){var job=new com.restaurant.system.printing.entity.PrintJob();job.payload_snapshot="{\"receipt_context\":{}}";assertThrows(RuntimeException.class,()->ReceiptContextService.hasContext(job));}
 @Test void grabCategoryRulesFreezeExclusionsButReceiptKeepsAllSoldItemsAndCodes(){
  enabled();item.item_sku_snapshot="AN_E1";item.category_code_snapshot="DESSERT";
  var settings=presentation.receipts(store);settings.kitchen_category_enabled=Map.of("DESSERT",false);
  var grab=service.freeze(store,"GRAB",10L,null);var receipt=service.freeze(store,"FRONTDESK_RECEIPT",10L,null);
  assertEquals(List.of(20L),grab.order_item_ids);assertEquals(List.of(20L),grab.kitchen_excluded_item_ids);
  assertFalse(grab.includesKitchenItem(20L));assertTrue(receipt.includesKitchenItem(20L));
  assertEquals("E1 Plat synthétique",grab.item_names.get("20"));
  settings.kitchen_category_enabled=Map.of("DESSERT",true);
  assertFalse(grab.includesKitchenItem(20L));assertTrue(service.freeze(store,"GRAB",10L,null).includesKitchenItem(20L));
 }
 @Test void drinkIsNeverKitchenEvenWithLegacyStationAndMissingCategoryIsConservative(){
  enabled();item.category_code_snapshot="DRINK";
  assertFalse(service.freeze(store,"GRAB",10L,null).includesKitchenItem(item.id));
  item.category_code_snapshot=null;presentation.receipts(store).kitchen_category_enabled=Map.of("DESSERT",false);
  assertTrue(service.freeze(store,"GRAB",10L,null).includesKitchenItem(item.id));
 }
}
