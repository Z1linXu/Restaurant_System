package com.restaurant.system.allo;

import com.restaurant.system.order.entity.*;
import com.restaurant.system.printing.dto.PrintRenderRequest;
import com.restaurant.system.kitchen.entity.KitchenTask;
import java.util.*;
import java.math.*;

/** Rendering copies only: no managed order/task/option is merged or mutated. */
public final class AlloReceiptSupport {
    private AlloReceiptSupport() {}
    public static String font(String configured) {return "XS".equalsIgnoreCase(configured)?"SMALL":configured;}
    public static String identity(OrderItem i,List<OrderItemOption> opts) {
        var key=new ArrayList<Object>(Arrays.asList(i.menu_item_id,i.item_sku_snapshot,i.category_code_snapshot,i.station_id_snapshot,
            i.item_name_snapshot_zh,i.item_name_snapshot_en,i.item_name_snapshot_fr,i.combo_group_no,i.combo_role,
            i.notes,i.unit_price == null?null:i.unit_price.stripTrailingZeros().toPlainString(),
            i.line_amount == null?null:i.line_amount.divide(BigDecimal.valueOf(i.quantity),12,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),i.order_update_batch_id));
        key.add(opts.stream().map(o->Arrays.asList(o.option_id,o.option_code_snapshot,o.option_type_snapshot,o.option_group_snapshot,
            o.parent_option_id_snapshot,o.option_name_snapshot_zh,o.option_name_snapshot_en,o.option_name_snapshot_fr,
            o.price_delta==null?null:o.price_delta.stripTrailingZeros().toPlainString(),o.quantity)).map(AlloReceiptSupport::key).sorted().toList());
        return key(key);
    }
    private static String key(Object value){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    public static void merge(PrintRenderRequest r) {
        if(!r.allo_current || r.allo_merged || r.order_items==null)return;
        r.allo_merged=true;
        var options=new LinkedHashMap<Long,List<OrderItemOption>>();
        for(var o:r.order_item_options==null?List.<OrderItemOption>of():r.order_item_options)options.computeIfAbsent(o.order_item_id,k->new ArrayList<>()).add(o);
        var groups=new LinkedHashMap<String,OrderItem>();var representative=new HashMap<Long,Long>();
        for(var i:r.order_items) {
            String key=identity(i,options.getOrDefault(i.id,List.of()));
            // Unknown identities never merge with another unknown snapshot.
            if(i.menu_item_id==null)key+="|id="+i.id;
            var current=groups.get(key);
            if(current==null){var copy=new OrderItem();com.restaurant.system.allo.AlloCopies.copy(i,copy);groups.put(key,copy);representative.put(i.id,i.id);}
            else {current.quantity=Math.addExact(current.quantity,i.quantity);current.line_amount=current.line_amount.add(i.line_amount);representative.put(i.id,current.id);}
        }
        r.order_items=new ArrayList<>(groups.values());
        var reps=new HashSet<>(r.order_items.stream().map(i->i.id).toList());
        r.order_item_options=(r.order_item_options==null?List.<OrderItemOption>of():r.order_item_options).stream().filter(o->reps.contains(o.order_item_id)).toList();
        var tasks=new LinkedHashMap<String,KitchenTask>();
        for(var t:r.kitchen_tasks==null?List.<KitchenTask>of():r.kitchen_tasks) {
            Long rep=representative.getOrDefault(t.order_item_id,t.order_item_id);
            String key=key(Arrays.asList(rep,t.station_code,t.item_name_snapshot_zh,t.item_name_snapshot_en,t.special_instructions_snapshot,t.status));
            var current=tasks.get(key);
            if(current==null){var copy=new KitchenTask();com.restaurant.system.allo.AlloCopies.copy(t,copy);copy.order_item_id=rep;tasks.put(key,copy);}
            else current.quantity=Math.addExact(current.quantity==null?1:current.quantity,t.quantity==null?1:t.quantity);
        }
        r.kitchen_tasks=new ArrayList<>(tasks.values());
    }
}
