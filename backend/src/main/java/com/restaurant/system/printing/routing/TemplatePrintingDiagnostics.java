package com.restaurant.system.printing.routing;
import com.restaurant.system.menu.template.TemplateMenuService;
import com.restaurant.system.printing.repository.*;
import com.restaurant.system.printing.routing.TemplateRoutingModels.FrozenIntent;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class TemplatePrintingDiagnostics {
 private final OrderDispatchOutboxRepository outbox;
 private final PrintJobRepository jobs;
 private final TemplateMenuService menus;
 public TemplatePrintingDiagnostics(OrderDispatchOutboxRepository outbox,PrintJobRepository jobs,TemplateMenuService menus){this.outbox=outbox;this.jobs=jobs;this.menus=menus;}
 @Transactional(readOnly=true)
 public Map<String,Object> list(Long storeId,Long orderId,Long batchId){
  menus.requireEnabled();List<Map<String,Object>> rows=new ArrayList<>();
  for(var e:outbox.findTemplateIntents(storeId,orderId,batchId,PageRequest.of(0,500))){
   FrozenIntent f=menus.read(e.routingContext,FrozenIntent.class);var j=jobs.findByDispatchSourceKey(e.sourceKey).orElse(null);
   Map<String,Object> v=new LinkedHashMap<>();
   v.put("intent_id",e.id);v.put("source_key",e.sourceKey);v.put("order_id",e.orderId);v.put("order_update_batch_id",e.orderUpdateBatchId);
   v.put("group_code",f.group_code);v.put("group_name",f.group_name);v.put("logical_target",f.logical_target);v.put("job_id",j==null?null:j.id);
   String reason=j==null?(e.lastError==null?f.reason:e.lastError):j.error_message;
   v.put("state",j!=null?j.status:reason==null?"PENDING":"UNRESOLVED");v.put("reason",reason);
   v.put("error_code",j==null?(f.error_code==null&&reason!=null?"TICKET_UNRESOLVED":f.error_code):j.error_code);
   v.put("rendered_text",j==null?f.rendered_text:j.rendered_text_snapshot);
   v.put("menu_revision",f.menu_revision);v.put("routing_revision",f.routing_revision);v.put("execution_mode","MOCK");rows.add(v);
  }return Map.of("tickets",rows);
 }
}
