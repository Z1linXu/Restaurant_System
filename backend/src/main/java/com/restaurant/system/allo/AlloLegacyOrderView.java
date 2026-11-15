package com.restaurant.system.allo;
import com.restaurant.system.order.dto.*;
import java.math.*;
import java.util.*;
/** Old bundled UIs calculate tax from line sums. Give them a truthful net display
 * projection while preserving original gross snapshots in persistence and v2 APIs. */
public final class AlloLegacyOrderView {
 private AlloLegacyOrderView(){}
 public static OrderResponse project(OrderResponse order,Integer view) {
  if(order==null||!Integer.valueOf(2).equals(order.financial_version)||!Boolean.TRUE.equals(order.financial_adjusted)||Integer.valueOf(2).equals(view))return order;
  var copy=AlloCopies.copy(order,new OrderResponse());
  BigDecimal gross=AlloMoney.cents(order.subtotal_amount),net=AlloMoney.cents(order.net_amount),cumulative=BigDecimal.ZERO,assigned=BigDecimal.ZERO;
  var projected=new ArrayList<OrderItemResponse>();var byId=new HashMap<Long,OrderItemResponse>();
  for(var item:order.items) {
   var line=AlloCopies.copy(item,new OrderItemResponse());cumulative=cumulative.add(AlloMoney.cents(item.line_amount));
   BigDecimal target=gross.signum()==0?BigDecimal.ZERO:net.multiply(cumulative).divide(gross,2,RoundingMode.HALF_UP);
   line.line_amount=target.subtract(assigned);assigned=target;projected.add(line);byId.put(line.id,line);
  }
  copy.items=projected;copy.beverage_items=order.beverage_items==null?List.of():order.beverage_items.stream().map(i->byId.get(i.id)).filter(Objects::nonNull).toList();
  copy.kitchen_items=order.kitchen_items==null?List.of():order.kitchen_items.stream().map(i->byId.get(i.id)).filter(Objects::nonNull).toList();
  copy.subtotal_amount=net;copy.discount_amount=BigDecimal.ZERO.setScale(2);return copy;
 }
}
