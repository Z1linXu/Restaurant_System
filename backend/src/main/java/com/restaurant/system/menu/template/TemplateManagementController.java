package com.restaurant.system.menu.template;
import com.restaurant.system.audit.service.AuditLogService;
import com.restaurant.system.common.auth.*;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.*;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.printing.routing.*;
import com.restaurant.system.printing.routing.TemplateRoutingModels.*;
import com.restaurant.system.menu.template.TemplateMenuModels.Groups;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/stores/{storeId}/template")
@ConditionalOnProperty(name="app.template.enabled",havingValue="true")
public class TemplateManagementController {
 private final TemplateMenuService menus;
 private final TemplateRoutingService routing;
 private final TemplatePrintingDiagnostics diagnostics;
 private final AuthorizationService auth;
 private final StoreModuleAccessEvaluator modules;
 private final AuditLogService audit;
 private final OrderRepository orders;
 public TemplateManagementController(TemplateMenuService menus,TemplateRoutingService routing,TemplatePrintingDiagnostics diagnostics,
  AuthorizationService auth,StoreModuleAccessEvaluator modules,AuditLogService audit,OrderRepository orders){
  this.menus=menus;this.routing=routing;this.diagnostics=diagnostics;this.auth=auth;this.modules=modules;this.audit=audit;this.orders=orders;
 }
 private AuthenticatedUser manage(Long store){var user=auth.requireManagerOrOwnerForStore(store,Capability.ADMIN_MENU_MANAGE,Capability.ADMIN_PRINTING_MANAGE);
  modules.requireModuleEnabled(store,ModuleKeys.MENU_MANAGEMENT);return user;}
 private void log(Long store,AuthenticatedUser user,String action,Long entity,HttpServletRequest request){
  audit.record(store,user,action,"TEMPLATE_CONFIGURATION",entity,"Updated store template configuration",Map.of("store_id",store),request);
 }
 @GetMapping("/option-library") public ApiResponse<Groups> library(@PathVariable Long storeId){manage(storeId);return ApiResponse.success(menus.library(storeId));}
 @PutMapping("/option-library") public ApiResponse<Groups> library(@PathVariable Long storeId,@RequestBody Groups body,HttpServletRequest request){
  var u=manage(storeId);var result=menus.saveLibrary(storeId,body);log(storeId,u,"TEMPLATE_LIBRARY_SAVED",storeId,request);return ApiResponse.success(result);
 }
 @GetMapping("/items/{itemId}/option-groups") public ApiResponse<Groups> item(@PathVariable Long storeId,@PathVariable Long itemId){manage(storeId);return ApiResponse.success(menus.getItem(storeId,itemId));}
 @PutMapping("/items/{itemId}/option-groups") public ApiResponse<Groups> item(@PathVariable Long storeId,@PathVariable Long itemId,@RequestBody Groups body,HttpServletRequest request){
  var u=manage(storeId);var result=menus.saveItem(storeId,itemId,body);log(storeId,u,"TEMPLATE_ITEM_GROUPS_SAVED",itemId,request);return ApiResponse.success(result);
 }
 @GetMapping("/kitchen-routing") public ApiResponse<Configuration> config(@PathVariable Long storeId){manage(storeId);return ApiResponse.success(routing.configuration(storeId));}
 @PutMapping("/kitchen-routing") public ApiResponse<Configuration> config(@PathVariable Long storeId,@RequestBody Configuration body,HttpServletRequest request){
  var u=manage(storeId);var result=routing.save(storeId,body);log(storeId,u,"TEMPLATE_ROUTING_SAVED",storeId,request);return ApiResponse.success(result);
 }
 @GetMapping("/printers") public ApiResponse<List<Map<String,Object>>> printers(@PathVariable Long storeId){manage(storeId);return ApiResponse.success(routing.printerChoices(storeId));}
 @PostMapping("/kitchen-routing/preview") public ApiResponse<Map<String,Object>> preview(@PathVariable Long storeId,@RequestBody PreviewRequest body){manage(storeId);return ApiResponse.success(routing.preview(storeId,body));}
 @GetMapping("/printing-diagnostics") public ApiResponse<Map<String,Object>> diagnostics(@PathVariable Long storeId,@RequestParam(required=false) Long order_id,@RequestParam(required=false) Long batch_id){
  auth.requireFrontdeskAccessForStore(storeId,Capability.ORDER_VIEW_DETAIL,Capability.ADMIN_PRINTING_MANAGE);
  modules.requireModuleEnabled(storeId,ModuleKeys.PRINTING);
  if(order_id!=null){var order=orders.findExistingById(order_id);if(order==null||!Objects.equals(order.store_id,storeId))throw new ForbiddenException("Order belongs to another store");}
  return ApiResponse.success(diagnostics.list(storeId,order_id,batch_id));
 }
}
