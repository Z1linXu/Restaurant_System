package com.restaurant.system.printing.routing;
import com.restaurant.system.menu.template.TemplateMenuService;
import com.restaurant.system.printing.entity.*;
import com.restaurant.system.printing.repository.*;
import com.restaurant.system.printing.service.*;
import com.restaurant.system.printing.routing.TemplateRoutingModels.FrozenIntent;
import java.util.*;
import org.springframework.stereotype.Service;

/** Executes only inert MOCK via the existing JobService. All physical transports remain unreachable. */
@Service
public class TemplateKitchenDispatchHandler {
 private final TemplateMenuService menus;
 private final PrintJobService jobs;
 private final PrintJobRepository repository;
 private final PrinterConfigRepository printers;
 private final PrinterConfigService printing;
 private final com.restaurant.system.modules.StoreModuleAccessEvaluator modules;
 public TemplateKitchenDispatchHandler(TemplateMenuService menus,PrintJobService jobs,PrintJobRepository repository,PrinterConfigRepository printers,PrinterConfigService printing,com.restaurant.system.modules.StoreModuleAccessEvaluator modules){
  this.menus=menus;this.jobs=jobs;this.repository=repository;this.printers=printers;this.printing=printing;this.modules=modules;
 }
 public boolean handles(OrderDispatchOutbox event){return TemplateRoutingService.EVENT.equals(event.eventType);}
 public String serializationKey(OrderDispatchOutbox event){
  FrozenIntent f=menus.read(event.routingContext,FrozenIntent.class);
  return "store:"+event.storeId+"|template-printer:"+(f.printer_id==null?"none:"+f.group_code:f.printer_id);
 }
 public PrintDispatchOutcome dispatch(OrderDispatchOutbox event){
  menus.requireEnabled();FrozenIntent f=menus.read(event.routingContext,FrozenIntent.class);
  // An existing source-key result is immutable. Never reattach content or reopen active/terminal jobs.
  var existing=repository.findByDispatchSourceKey(event.sourceKey).orElse(null);
  if(existing!=null){
   event.lastError=existing.error_message;
   return "PRINTED".equals(existing.status)?PrintDispatchOutcome.MOCK_RENDERED:
    "FAILED".equals(existing.status)||"CANCELLED".equals(existing.status)?PrintDispatchOutcome.FAILED:PrintDispatchOutcome.DISPATCHED;
  }
  if(!"MOCK".equals(printing.getStorePrintingMode(event.storeId))){
   event.lastError="MOCK_ONLY: template kitchen transport is inert outside MOCK";return PrintDispatchOutcome.POLICY_BLOCKED;
  }
  try {modules.evaluateCapability(event.storeId,com.restaurant.system.modules.ModuleKeys.PRINTING).requireAllowed();}
  catch(com.restaurant.system.common.exception.BusinessException denied){
   event.lastError=denied.getMessage();return PrintDispatchOutcome.POLICY_BLOCKED;
  }
  if(f.reason!=null){event.lastError=f.reason;return PrintDispatchOutcome.FAILED;}
  var printer=f.printer_id==null?null:printers.findByIdAndStoreId(f.printer_id,event.storeId).filter(p->Boolean.TRUE.equals(p.enabled)).orElse(null);
  if(printer==null){event.lastError="MOCK printer is not configured or no longer available";return PrintDispatchOutcome.FAILED;}
  PrintJob job=jobs.createPendingJob(event.organizationId,event.storeId,event.orderId,event.orderUpdateBatchId,f.printer_id,
   TemplateRoutingService.EVENT,TemplateRoutingService.EVENT,null,event.routingContext,event.sourceKey);
  job.executionMode="MOCK";repository.save(job);
  job=jobs.attachRenderedContent(job,f.printer_id,f.rendered_text);
  job=jobs.markPrinting(job,printer);
  if("FAILED".equals(f.mock_failure)||"UNCERTAIN".equals(f.mock_failure)){
   String code="UNCERTAIN".equals(f.mock_failure)?"PRINT_RESULT_UNCERTAIN":"MOCK_EXECUTION_FAILED";
   String reason="UNCERTAIN".equals(f.mock_failure)?"MOCK: result uncertain; do not automatically reprint":"MOCK: simulated execution failure";
   jobs.markFailed(job,printer,code,reason);event.lastError=reason;return PrintDispatchOutcome.FAILED;
  }
  jobs.markPrinted(job,printer,"MOCK: inert transport, no printer bytes sent");event.lastError=null;return PrintDispatchOutcome.MOCK_RENDERED;
 }
}
