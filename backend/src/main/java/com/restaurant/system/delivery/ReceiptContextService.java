package com.restaurant.system.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.pricing.TaxCalculator;
import com.restaurant.system.order.entity.*;
import com.restaurant.system.order.repository.*;
import com.restaurant.system.printing.dto.ReceiptPresentation;
import com.restaurant.system.menu.display.MenuCodeFormatter;
import com.restaurant.system.printing.entity.PrintJob;
import com.restaurant.system.printing.AlloProductionPrintingPolicy;
import com.restaurant.system.printing.repository.OrderDispatchOutboxRepository;
import com.restaurant.system.user.entity.Store;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;

/** Closed Allô receipt seam. Existing events without a context keep their legacy interpretation. */
@Service
public class ReceiptContextService {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OrderUpdateBatchRepository noteBatches;
    private final StorePresentationService presentation;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderItemOptionRepository options;
    private final OrderDispatchOutboxRepository events;
    private final ObjectMapper mapper;
    private final AlloProductionPrintingPolicy printingPolicy;
    public ReceiptContextService(StorePresentationService presentation, OrderRepository orders,
            OrderItemRepository items, OrderItemOptionRepository options,
            OrderDispatchOutboxRepository events, ObjectMapper mapper, AlloProductionPrintingPolicy printingPolicy) {
        this.presentation=presentation;this.orders=orders;this.items=items;this.options=options;this.events=events;this.mapper=mapper;
        this.printingPolicy=printingPolicy;
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
        requireAllowedMode(store);
        Order order=orders.findById(orderId).orElseThrow(()->fail("ORDER_NOT_FOUND"));
        if(!store.id.equals(order.store_id))throw fail("RECEIPT_STORE_MISMATCH");
        var settings=presentation.receipts(store);
        ReceiptPresentation context=new ReceiptPresentation();
        context.execution_mode=store.printing_mode;
        context.language="GRAB".equals(module)?settings.kitchen_language:settings.frontdesk_language;
        if(context.language==null)context.language=settings.default_language;
        if(!StorePresentationService.LOCALES.contains(context.language))throw fail("RECEIPT_LANGUAGE_INVALID");
        context.settings_revision=settings.revision;
        context.kitchen_station_codes=List.copyOf(settings.kitchen_station_codes);
        context.restaurant_name=String.valueOf(presentation.publicView(store).getOrDefault("display_name",store.name));
        // The installed GBK receipt printers cannot encode ô. Freeze the physical
        // merchant label once; existing intents and historical artifacts stay unchanged.
        if ("PAD_DIRECT".equals(context.execution_mode) && "Allô Nouilles".equals(context.restaurant_name)) {
            context.restaurant_name = "Allo Nouilles";
        }
        var allLines=items.findAllByOrderId(orderId);
        var lines=allLines.stream().filter(i->batch==null||batch.equals(i.order_update_batch_id)).toList();
        context.order_item_ids=lines.stream().map(i->i.id).toList();
        if("GRAB".equals(module))context.kitchen_excluded_item_ids=lines.stream()
            .filter(i->!settings.kitchenEnabled(i.category_code_snapshot)).map(i->i.id).toList();
        if ("GRAB".equals(module)) {
            context.order_notes_captured = true;
            context.order_notes = order.order_notes;
            context.order_notes_changed = batch != null && noteBatches != null && noteBatches.findById(batch)
                .map(b -> Boolean.TRUE.equals(b.order_notes_changed)).orElse(false);
            context.order_notes_only = Boolean.TRUE.equals(context.order_notes_changed)
                && lines.stream().noneMatch(i -> settings.kitchenEnabled(i.category_code_snapshot));
        }
        context.item_names=new LinkedHashMap<>();context.option_names=new LinkedHashMap<>();
        for(var item:lines)context.item_names.put(String.valueOf(item.id),MenuCodeFormatter.display(item.item_sku_snapshot,resolve(context.language,item.item_name_snapshot_zh,item.item_name_snapshot_en,item.item_name_snapshot_fr)));
        if(!lines.isEmpty())for(var option:options.findAllByOrderItemIds(context.order_item_ids))
            context.option_names.put(String.valueOf(option.id),resolve(context.language,option.option_name_snapshot_zh,option.option_name_snapshot_en,option.option_name_snapshot_fr));
        context.subtotal=order.subtotal_amount;context.total=order.total_amount;
        if (batch != null && "FRONTDESK_RECEIPT".equals(module)) {
            freezeUpdateAmounts(context, order, allLines, batch);
        }
        return context;
    }

    private void freezeUpdateAmounts(ReceiptPresentation context, Order order, List<OrderItem> allLines, Long batch) {
        // Called by the existing outbox enqueue inside the locked add-only order transaction.
        // This bounded contract supports the current zero-discount, one-rate in-store model.
        BigDecimal previousSubtotal = BigDecimal.ZERO.setScale(2);
        BigDecimal addedSubtotal = BigDecimal.ZERO.setScale(2);
        for (OrderItem line : allLines) {
            if ("cancelled".equals(line.status)) continue;
            BigDecimal amount = receiptMoney(line.line_amount);
            if (amount.signum() < 0) throw fail("RECEIPT_UPDATE_AMOUNTS_UNSUPPORTED");
            if (batch.equals(line.order_update_batch_id)) addedSubtotal = addedSubtotal.add(amount);
            else previousSubtotal = previousSubtotal.add(amount);
        }
        BigDecimal wholeSubtotal = receiptMoney(order.subtotal_amount);
        BigDecimal wholeTotal = receiptMoney(order.total_amount);
        if ((order.discount_amount != null && order.discount_amount.signum() != 0)
                || wholeSubtotal.compareTo(previousSubtotal.add(addedSubtotal)) != 0
                || wholeTotal.compareTo(TaxCalculator.calculateTotal(wholeSubtotal)) != 0) {
            throw fail("RECEIPT_UPDATE_AMOUNTS_UNSUPPORTED");
        }
        context.update_subtotal = addedSubtotal;
        context.update_total = wholeTotal.subtract(TaxCalculator.calculateTotal(previousSubtotal));
    }

    private BigDecimal receiptMoney(BigDecimal value) {
        if (value == null) throw fail("RECEIPT_UPDATE_AMOUNTS_UNSUPPORTED");
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException error) {
            throw fail("RECEIPT_UPDATE_AMOUNTS_UNSUPPORTED");
        }
    }
    public ReceiptPresentation forEvent(Store store,String module,Long orderId,Long batch) {
        var event=events.findBySourceKey(sourceKey(module,orderId,batch)).orElse(null);
        if(event!=null) {
            if(!store.id.equals(event.storeId)||!orderId.equals(event.orderId)||!module.equals(event.moduleCode))throw fail("RECEIPT_EVENT_SCOPE_MISMATCH");
            var context = deserialize(event.routingContext);
            if(context==null && event.routingContext!=null && !event.routingContext.isBlank()
                    && "ALLO_NOUILLES_TEST".equals(store.code))throw fail("RECEIPT_CONTEXT_INVALID");
            if(context!=null)requireAllowedMode(store);
            requireFrozenPadIntent(store,module,context);
            return context;
        }
        ReceiptPresentation context=freeze(store,module,orderId,batch);
        requireFrozenPadIntent(store,module,context);
        return context;
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
    private void requireAllowedMode(Store store) {
        if(Set.of("MOCK","DISABLED").contains(store.printing_mode))return;
        if("PAD_DIRECT".equals(store.printing_mode)&&printingPolicy.allowsPadDirect(store))return;
        throw fail("ALLO_RECEIPT_TEST_MOCK_ONLY");
    }
    private void requireFrozenPadIntent(Store store,String module,ReceiptPresentation context) {
        if("PAD_DIRECT".equals(store.printing_mode)&&"ALLO_NOUILLES_TEST".equals(store.code)
                &&Set.of("GRAB","FRONTDESK_RECEIPT").contains(module)
                &&(context==null||!"PAD_DIRECT".equals(context.execution_mode))) {
            throw fail("RECEIPT_PAD_INTENT_REQUIRED");
        }
    }
    private static BusinessException fail(String message){return new BusinessException(message);}
}
