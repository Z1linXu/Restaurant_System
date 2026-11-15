package com.restaurant.system.allo;
import org.junit.jupiter.api.Test;
import com.restaurant.system.order.entity.*;
import com.restaurant.system.printing.dto.*;
import com.restaurant.system.printing.renderer.*;
import com.restaurant.system.delivery.ReceiptArtifact;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.kitchen.entity.KitchenTask;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AlloReceiptSupportTest {
 OrderItem item(long id){var i=new OrderItem();i.id=id;i.menu_item_id=5L;i.item_sku_snapshot="AN_A5";i.item_name_snapshot_zh="炸馄饨（6颗）";i.quantity=1;i.line_amount=new BigDecimal("7.99");i.unit_price=i.line_amount;return i;}
 PrintRenderRequest request(){var r=new PrintRenderRequest();r.allo_current=true;r.order=new Order();r.order.status="completed";r.order.order_type="pickup";r.order.pickup_no="QA";r.order.subtotal_amount=new BigDecimal("15.98");r.order.total_amount=new BigDecimal("18.37");r.order_items=List.of(item(1),item(2));r.order_item_options=List.of();r.kitchen_tasks=List.of();return r;}
 @Test void mergesCopiesOnlyAndKeepsCountsAndMoney(){var r=request();AlloReceiptSupport.merge(r);assertEquals(1,r.order_items.size());assertEquals(2,r.order_items.get(0).quantity);assertEquals(new BigDecimal("15.98"),r.order_items.get(0).line_amount);assertEquals(1,item(1).quantity);AlloReceiptSupport.merge(r);assertEquals(2,r.order_items.get(0).quantity);}
 @Test void differencesNeverMerge(){for(int n=0;n<5;n++){var r=request();var i=r.order_items.get(1);switch(n){case 0:i.notes="no sauce";break;case 1:i.menu_item_id=6L;break;case 2:i.unit_price=BigDecimal.TEN;break;case 3:i.combo_group_no=1;break;case 4:i.line_amount=BigDecimal.TEN;break;}AlloReceiptSupport.merge(r);assertEquals(2,r.order_items.size());}}
 @Test void optionQuantitiesArePerPortion(){var r=request();var a=new OrderItemOption();a.id=1L;a.order_item_id=1L;a.option_id=8L;a.quantity=2;var b=new OrderItemOption();com.restaurant.system.allo.AlloCopies.copy(a,b);b.id=2L;b.order_item_id=2L;r.order_item_options=List.of(a,b);AlloReceiptSupport.merge(r);assertEquals(1,r.order_item_options.size());assertEquals(2,r.order_item_options.get(0).quantity);assertEquals(2,r.order_items.get(0).quantity);}
 @Test void frontdeskRendersQuantityOnceAndTotalOnce(){var r=request();String text=new FrontdeskReceiptRenderer().render(r);assertTrue(text.contains("2 x 炸馄饨"));assertFalse(text.contains("1 x 炸馄饨"));assertTrue(text.contains("$15.98"));}
 @Test void legacyRequestUnchanged(){var r=request();r.allo_current=false;AlloReceiptSupport.merge(r);assertEquals(2,r.order_items.size());}
 @Test void reportIntentCannotUseMockBytesAsPad(){var j=new PrintJob();j.store_id=1L;j.module_code="FRONTDESK_RECEIPT";j.receipt_type="SALES_OVERVIEW";j.payload_snapshot="{\"store_id\":1,\"report_id\":\"id\",\"report_intent\":{\"version\":\"ALLO_SALES_REPORT_V1\",\"execution_mode\":\"MOCK\"}}";assertTrue(ReceiptArtifact.protects(j));assertThrows(RuntimeException.class,()->ReceiptArtifact.requirePadIntent(j));j.order_id=1L;assertThrows(RuntimeException.class,()->ReceiptArtifact.isSalesReport(j));}
 @Test void xsUsesNextSupportedIntegerModeOnly(){assertEquals("SMALL",AlloReceiptSupport.font("XS"));for(String mode:List.of("SMALL","MEDIUM","LARGE","XL"))assertEquals(mode,AlloReceiptSupport.font(mode));}
 @Test void mergedFrontOptionsShowTotalQuantities(){var r=request();var p=new ReceiptPresentation();p.language="en";p.order_item_ids=List.of(1L,2L);p.subtotal=r.order.subtotal_amount;p.total=r.order.total_amount;p.option_names=Map.of("1","Extra beef","2","Extra beef");r.receipt_context=p;var a=new OrderItemOption();a.id=1L;a.order_item_id=1L;a.option_id=5L;a.quantity=2;var b=AlloCopies.copy(a,new OrderItemOption());b.id=2L;b.order_item_id=2L;r.order_item_options=List.of(a,b);String text=new FrontdeskReceiptRenderer().render(r);assertTrue(text.contains("Extra beef x4"),text);assertFalse(text.contains("Extra beef x2"));}
}
