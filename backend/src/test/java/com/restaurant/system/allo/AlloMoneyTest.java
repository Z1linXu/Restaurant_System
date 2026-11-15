package com.restaurant.system.allo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import com.restaurant.system.order.entity.*;
import static org.junit.jupiter.api.Assertions.*;
class AlloMoneyTest {
 @ParameterizedTest @CsvSource({"10,PERCENT,50,5.00,5.00,0.75,5.75","10,PERCENT,0,0.00,10.00,1.50,11.50","10,PERCENT,100,10.00,0.00,0.00,0.00","10,AMOUNT,20,10.00,0.00,0.00,0.00","0.03,PERCENT,50,0.02,0.01,0.00,0.01","20,AMOUNT,5,5.00,15.00,2.25,17.25"})
 void exactMoney(String g,String type,String v,String d,String n,String t,String total){
  var m=AlloMoney.calculate(new BigDecimal(g),type,new BigDecimal(v),true);
  assertEquals(new BigDecimal(d),m.discount());assertEquals(new BigDecimal(n),m.net());assertEquals(new BigDecimal(t),m.tax());assertEquals(new BigDecimal(total),m.total());assertEquals(m.total(),m.collected());
 }
 @Test void invalidAndIncomplete(){assertThrows(RuntimeException.class,()->AlloMoney.calculate(BigDecimal.TEN,"PERCENT",new BigDecimal("100.01"),true));assertThrows(RuntimeException.class,()->AlloMoney.calculate(BigDecimal.TEN,"AMOUNT",new BigDecimal("-1"),true));assertThrows(RuntimeException.class,()->AlloMoney.calculate(BigDecimal.TEN,"AMOUNT",new BigDecimal("1.001"),true));assertEquals(new BigDecimal("0.00"),AlloMoney.calculate(BigDecimal.TEN,null,null,false).collected());}
 @Test void voidThenCancelDiscountNeverRestoresItem(){var i=new OrderItem();i.quantity=2;i.line_amount=new BigDecimal("20");i.voided_quantity=1;var discounted=AlloMoney.calculate(AlloMoney.effectiveLine(i),"PERCENT",new BigDecimal("50"),true);assertEquals(new BigDecimal("5.00"),discounted.discount());assertEquals(new BigDecimal("11.50"),AlloMoney.calculate(AlloMoney.effectiveLine(i),null,null,true).total());assertEquals(2,i.quantity);assertEquals(new BigDecimal("20"),i.line_amount);}
 @Test void legacyDiscountTaxUsesNet(){var o=new Order();o.subtotal_amount=new BigDecimal("10");o.discount_amount=new BigDecimal("5");o.total_amount=new BigDecimal("5.75");o.status="completed";assertEquals(new BigDecimal("0.75"),AlloMoney.determined(o).tax());}
 @Test void effectiveProjectionCannotApplyVoidsTwice(){var i=new OrderItem();i.id=1L;i.order_id=1L;i.quantity=2;i.voided_quantity=1;i.line_amount=new BigDecimal("20.00");var projected=AlloMoney.effectiveItems(java.util.List.of(i));assertEquals(0,projected.get(0).voided_quantity);var o=new Order();o.id=1L;o.total_amount=new BigDecimal("11.50");var allocated=com.restaurant.system.analytics.support.SalesReporting.allocate(java.util.List.of(o),projected);assertEquals(1,allocated.size());assertEquals(1,allocated.get(0).item().quantity);assertEquals(new BigDecimal("11.50"),allocated.get(0).revenue());assertEquals(1,i.voided_quantity);}
 @Test void oldUiViewReconcilesNetWithoutChangingGrossSnapshots(){var o=new com.restaurant.system.order.dto.OrderResponse();o.financial_version=2;o.financial_adjusted=true;o.subtotal_amount=new BigDecimal("10.00");o.discount_amount=new BigDecimal("5.00");o.net_amount=new BigDecimal("5.00");o.tax_amount=new BigDecimal("0.75");o.total_amount=new BigDecimal("5.75");var i=new com.restaurant.system.order.dto.OrderItemResponse();i.id=1L;i.line_amount=new BigDecimal("10.00");o.items=java.util.List.of(i);var old=AlloLegacyOrderView.project(o,null);assertEquals(new BigDecimal("5.00"),old.subtotal_amount);assertEquals(new BigDecimal("5.00"),old.items.get(0).line_amount);assertEquals(new BigDecimal("10.00"),o.items.get(0).line_amount);assertSame(o,AlloLegacyOrderView.project(o,2));}
}
