package com.restaurant.system.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.order.entity.*;
import com.restaurant.system.order.repository.*;
import com.restaurant.system.printing.dto.ReceiptPresentation;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.user.entity.Store;
import java.util.*;
import org.springframework.stereotype.Service;

/** Closed Allô MOCK seam. Existing events without a context keep their legacy interpretation. */
@Service
public class ReceiptContextService {
    private final StorePresentationService presentation;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderItemOptionRepository options;
    private final OrderDispatchOutboxRepository events;
    private final ObjectMapper mapper;
    public ReceiptContextService(StorePresentationService presentation, OrderRepository orders,
            OrderItemRepository items, OrderItemOptionRepository options,
            OrderDispatchOutboxRepository events, ObjectMapper mapper) {
        this.presentation=presentation;this.orders=orders;this.items=items;this.options=options;this.events=events;this.mapper=mapper;
    }
    public static String sourceKey(String module,Long order,Long batch) {
        return (batch==null?"submit:"+order:"update:"+order+":"+batch)+":"+module;
    }
    public boolean enabled(Store store,String module) {
        return "ALLO_NOUILLES_TEST".equals(store.code) && Set.of("GRAB","FRONTDESK_RECEIPT").contains(module)
            && presentation.receipts(store).enabled;
    }
    public ReceiptPresentation freeze(Store store,String module,Long orderId,Long batch) {
        if(!enabled(store,module))return null;
        if(!Set.of("MOCK","DISABLED").contains(store.printing_mode))throw fail("ALLO_RECEIPT_TEST_MOCK_ONLY");
        Order order=orders.findById(orderId).orElseThrow(()->fail("ORDER_NOT_FOUND"));
        if(!store.id.equals(order.store_id))throw fail("RECEIPT_STORE_MISMATCH");
        var settings=presentation.receipts(store);
        ReceiptPresentation context=new ReceiptPresentation();
        context.language="GRAB".equals(module)?settings.kitchen_language:settings.frontdesk_language;
        if(context.language==null)context.language=settings.default_language;
        if(!StorePresentationService.LOCALES.contains(context.language))throw fail("RECEIPT_LANGUAGE_INVALID");
        context.settings_revision=settings.revision;
        context.kitchen_station_codes=List.copyOf(settings.kitchen_station_codes);
        context.restaurant_name=String.valueOf(presentation.publicView(store).getOrDefault("display_name",store.name));
        var lines=items.findAllByOrderId(orderId).stream().filter(i->batch==null||batch.equals(i.order_update_batch_id)).toList();
        context.order_item_ids=lines.stream().map(i->i.id).toList();
        context.item_names=new LinkedHashMap<>();context.option_names=new LinkedHashMap<>();
        for(var item:lines)context.item_names.put(String.valueOf(item.id),resolve(context.language,item.item_name_snapshot_zh,item.item_name_snapshot_en,item.item_name_snapshot_fr));
        if(!lines.isEmpty())for(var option:options.findAllByOrderItemIds(context.order_item_ids))
            context.option_names.put(String.valueOf(option.id),resolve(context.language,option.option_name_snapshot_zh,option.option_name_snapshot_en,option.option_name_snapshot_fr));
        context.subtotal=order.subtotal_amount;context.total=order.total_amount;
        return context;
    }
    public ReceiptPresentation forEvent(Store store,String module,Long orderId,Long batch) {
        var event=events.findBySourceKey(sourceKey(module,orderId,batch)).orElse(null);
        if(event!=null) {
            if(!store.id.equals(event.storeId)||!orderId.equals(event.orderId)||!module.equals(event.moduleCode))throw fail("RECEIPT_EVENT_SCOPE_MISMATCH");
            var context = deserialize(event.routingContext);
            if(context==null && event.routingContext!=null && !event.routingContext.isBlank()
                    && "ALLO_NOUILLES_TEST".equals(store.code))throw fail("RECEIPT_CONTEXT_INVALID");
            if(context!=null&&!Set.of("MOCK","DISABLED").contains(store.printing_mode))throw fail("ALLO_RECEIPT_TEST_MOCK_ONLY");
            return context;
        }
        return freeze(store,module,orderId,batch);
    }
    public String serialize(ReceiptPresentation context) {
        if(context==null)return null;
        try{return mapper.writeValueAsString(context);}catch(Exception e){throw fail("RECEIPT_CONTEXT_INVALID");}
    }
    public ReceiptPresentation deserialize(String json) {
        if(json==null||json.isBlank())return null;
        try{
            var node=mapper.readTree(json);
            if(!"ALLO_RECEIPT_V1".equals(node.path("version").asText()))return null;
            return mapper.treeToValue(node,ReceiptPresentation.class);
        }catch(Exception e){throw fail("RECEIPT_CONTEXT_INVALID");}
    }
    public String withContext(String payload,ReceiptPresentation context) {
        if(context==null)return payload;
        try{ObjectNode node=(ObjectNode)mapper.readTree(payload);node.set("receipt_context",mapper.valueToTree(context));return mapper.writeValueAsString(node);}catch(Exception e){throw fail("RECEIPT_CONTEXT_INVALID");}
    }
    public static boolean hasContext(PrintJob job) {
        if(job==null||job.payload_snapshot==null)return false;
        try{
            var payload=new ObjectMapper().readTree(job.payload_snapshot);
            if(!payload.has("receipt_context"))return false;
            if(!"ALLO_RECEIPT_V1".equals(payload.path("receipt_context").path("version").asText()))throw fail("RECEIPT_CONTEXT_INVALID");
            return true;
        }
        catch(Exception e){throw fail("RECEIPT_CONTEXT_INVALID");}
    }
    static String resolve(String locale,String zh,String en,String fr) {
        String preferred="zh-CN".equals(locale)?zh:"fr-CA".equals(locale)?fr:en;
        for(String name:new String[]{preferred,en,zh,fr})if(name!=null&&!name.isBlank())return name;
        return "[missing name]";
    }
    private static BusinessException fail(String message){return new BusinessException(message);}
}
