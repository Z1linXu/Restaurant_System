package com.restaurant.system.printing.routing;

import com.restaurant.system.menu.template.*;
import com.restaurant.system.menu.repository.*;
import com.restaurant.system.menu.entity.*;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.printing.entity.*;
import com.restaurant.system.printing.repository.*;
import com.restaurant.system.printing.service.PrinterConfigService;
import com.restaurant.system.printing.AlloProductionPrintingPolicy;
import com.restaurant.system.printing.security.PrintOriginContext;
import com.restaurant.system.printing.routing.TemplateRoutingModels.*;
import com.restaurant.system.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Freezes plans into the existing transaction/outbox. No transport and no second queue. */
@Service
public class TemplateRoutingService {
 private final TemplateMenuService menus;
 private final TemplateStoreConfigurationRepository configs;
 private final MenuItemRepository items;
 private final MenuItemOptionRepository options;
 private final MenuCategoryRepository categories;
 private final PrinterConfigRepository printers;
 private final MenuRevisionService revisions;
 private final OrderDispatchOutboxRepository outbox;
 private final PrinterConfigService printing;
 private final AlloProductionPrintingPolicy printingPolicy;
 private com.restaurant.system.menu.template.TemplateComboAdapter combos;
 @org.springframework.beans.factory.annotation.Autowired
 public void setCombos(com.restaurant.system.menu.template.TemplateComboAdapter value){this.combos=value;}
 private final KitchenRoutingPlanner planner=new KitchenRoutingPlanner();
 public static final String EVENT="TEMPLATE_KITCHEN";
 public TemplateRoutingService(TemplateMenuService menus,TemplateStoreConfigurationRepository configs,
  MenuItemRepository items,MenuItemOptionRepository options,MenuCategoryRepository categories,
  PrinterConfigRepository printers,MenuRevisionService revisions,OrderDispatchOutboxRepository outbox,PrinterConfigService printing,AlloProductionPrintingPolicy printingPolicy){
  this.menus=menus;this.configs=configs;this.items=items;this.options=options;this.categories=categories;
  this.printers=printers;this.revisions=revisions;this.outbox=outbox;this.printing=printing;
  this.printingPolicy=printingPolicy;
 }
 @Transactional(readOnly=true)
 public Configuration configuration(Long storeId){
  var c=configs.findById(storeId).orElse(null);Configuration result=c==null?new Configuration():menus.read(c.routing_json,Configuration.class);
  result.revision=c==null?0:c.routing_revision;return result;
 }
 public boolean enabled(Long storeId){
  try {menus.requireEnabled();}catch(BusinessException ex){return false;}
  return configuration(storeId).enabled;
 }
 @Transactional
 public Configuration save(Long storeId,Configuration request){
  menus.requireEnabled();revisions.lockStoresInOrder(List.of(storeId));
  String mode=printing.getStorePrintingMode(storeId);
  if(!"MOCK".equals(mode)&&!("PAD_DIRECT".equals(mode)&&printingPolicy.allowsPadDirect(storeId)))throw new BusinessException("TEMPLATE_ROUTING_REQUIRES_MOCK");
  if(request==null||request.groups==null||request.groups.size()>100||request.item_overrides==null)throw new BusinessException("ROUTING_CONFIG_INVALID");
  Set<Long> validCategories=new HashSet<>();categories.findAllByStoreIdOrderByIdAsc(storeId).forEach(c->validCategories.add(c.id));
  Set<String> codes=new HashSet<>();
  for(Group g:request.groups){
   if(g.group_code==null||!g.group_code.matches("[A-Za-z0-9_-]{1,64}")||!codes.add(g.group_code)||g.name==null||g.name.isBlank()||g.name.length()>160
      ||g.category_ids==null||g.item_ids==null||g.combo_component_codes==null)throw new BusinessException("ROUTING_GROUP_INVALID");
   if(!validCategories.containsAll(g.category_ids))throw new com.restaurant.system.common.auth.ForbiddenException("Category belongs to another store");
   g.item_ids.forEach(id->menus.requireItem(storeId,id));
   if(g.printer_id!=null && printers.findByIdAndStoreId(g.printer_id,storeId).isEmpty())throw new com.restaurant.system.common.auth.ForbiddenException("Printer belongs to another store");
   if(g.mock_failure!=null&&!Set.of("FAILED","UNCERTAIN").contains(g.mock_failure))throw new BusinessException("MOCK_FAILURE_INVALID");
   if("PAD_DIRECT".equals(mode)&&g.mock_failure!=null)throw new BusinessException("MOCK_FAILURE_REQUIRES_MOCK");
   if(g.logical_target!=null&&g.logical_target.length()>64)throw new BusinessException("ROUTING_TARGET_INVALID");
  }
  Set<Long> overridden=new HashSet<>();
  for(var o:request.item_overrides){
   if(o==null||o.menu_item_id==null||!overridden.add(o.menu_item_id)||o.group_codes==null||!codes.containsAll(o.group_codes))throw new BusinessException("ROUTING_OVERRIDE_INVALID");
   menus.requireItem(storeId,o.menu_item_id);
  }
  var c=configs.findById(storeId).orElseGet(TemplateStoreConfiguration::new);c.store_id=storeId;c.routing_revision++;
  request.revision=c.routing_revision;c.routing_json=menus.write(request);configs.save(c);return configuration(storeId);
 }
 @Transactional(readOnly=true)
 public Map<String,Object> preview(Long storeId,PreviewRequest request){
  menus.requireEnabled();if(request==null||request.items==null||request.items.size()>200)throw new BusinessException("PREVIEW_INPUT_INVALID");
  List<KitchenRoutingPlanner.SourceLine> lines=new ArrayList<>();int index=0;
  for(var input:request.items){
   menus.validateSelection(storeId,input);
   var item=menus.requireItem(storeId,input.menu_item_id);if(input.quantity==null||input.quantity<1)throw new BusinessException("ITEM_QUANTITY_INVALID");
   List<KitchenRoutingPlanner.Instruction> instructions=new ArrayList<>();
   List<com.restaurant.system.order.dto.CreateOrderItemOptionRequest> selected=input.options==null?List.of():input.options;
   String parent="preview:"+(++index);
   for(var o:selected){
    if(o.option_id==null||o.quantity==null||o.quantity<1)throw new BusinessException("OPTION_QUANTITY_INVALID");
    if(o.option_id<0){var component=combos.resolve(storeId,o);if(component.linkedItemId()==null || "NO_KITCHEN_TASK".equals(component.behavior()))
      instructions.add(new KitchenRoutingPlanner.Instruction(component.code(),component.group(),component.name(),o.quantity));continue;}
    var leaf=options.findById(o.option_id).orElseThrow(()->new BusinessException("PREVIEW_OPTION_NOT_FOUND"));
    if(o.parent_option_id_snapshot!=null)continue;
    if(!Objects.equals(leaf.menu_item_id,item.id))throw new BusinessException("PREVIEW_OPTION_SCOPE_MISMATCH");
    instructions.add(new KitchenRoutingPlanner.Instruction(leaf.option_code,leaf.option_group,leaf.name_zh,o.quantity));
   }
   lines.add(new KitchenRoutingPlanner.SourceLine(parent,null,item.id,item.category_id,item.name_zh,input.quantity,instructions,null));
   lines.addAll(componentLines(storeId,parent,input.quantity,selected));
  }
  Configuration c=configuration(storeId);long rev=revisions.getRevision(storeId).menu_revision;
  var result=plan(storeId,null,null,"SYNTHETIC PREVIEW",null,null,rev,c,lines);
  List<Map<String,Object>> tickets=new ArrayList<>();
  for(var t:result.tickets()){
   Map<String,Object> row=new LinkedHashMap<>();row.put("group_code",t.groupCode());row.put("group_name",t.groupName());row.put("logical_target",t.logicalTarget());row.put("printer_id",t.printerId());
   row.put("rendered_text",t.renderedText());row.put("source_keys",t.sourceKeys());row.put("diagnostics",t.diagnostics().stream().map(this::diagnostic).toList());tickets.add(row);
  }
  return Map.of("tickets",tickets,"diagnostics",result.diagnostics().stream().map(this::diagnostic).toList(),
   "coverage",result.coverage().stream().map(v->{
    var source=lines.stream().filter(l->l.sourceKey().equals(v.sourceKey())).findFirst().orElseThrow();
    Map<String,Object> view=new LinkedHashMap<>();view.put("source_key",v.sourceKey());view.put("group_codes",v.groupCodes());view.put("disposition",v.disposition());
    view.put("name",source.name());view.put("menu_item_id",source.menuItemId());view.put("parent_source_key",source.parentSourceKey());return view;
   }).toList(),
   "menu_revision",rev,"routing_revision",c.revision);
 }
 @Transactional
 public void enqueue(Order order,Long batchId,List<OrderItem> orderItems,List<OrderItemOption> selected){
  Configuration c=configuration(order.store_id);long rev=revisions.getRevision(order.store_id).menu_revision;
  String executionMode=printing.getStorePrintingMode(order.store_id);
  List<KitchenRoutingPlanner.SourceLine> lines=new ArrayList<>();
  for(var line:orderItems){
   if("cancelled".equalsIgnoreCase(line.status))continue;
   var item=items.findById(line.menu_item_id).orElse(null);List<KitchenRoutingPlanner.Instruction> instructions=new ArrayList<>();
   List<com.restaurant.system.order.dto.CreateOrderItemOptionRequest> choices=new ArrayList<>();
   for(var o:selected)if(Objects.equals(line.id,o.order_item_id)){
    var choice=new com.restaurant.system.order.dto.CreateOrderItemOptionRequest();choice.option_id=o.option_id;choice.quantity=o.quantity;
    choice.option_group_snapshot=o.option_group_snapshot;choice.option_code_snapshot=o.option_code_snapshot;
    choice.option_name_snapshot_zh=o.option_name_snapshot_zh;choice.parent_option_id_snapshot=o.parent_option_id_snapshot;choice.option_price_snapshot=o.price_delta;
    choices.add(choice);
    if(o.parent_option_id_snapshot!=null)continue;
    if(o.option_id!=null&&o.option_id<0){var component=combos.resolve(order.store_id,choice);
      if(component.linkedItemId()!=null&&!"NO_KITCHEN_TASK".equals(component.behavior()))continue;}
    instructions.add(new KitchenRoutingPlanner.Instruction(o.option_code_snapshot,o.option_group_snapshot,
      o.option_name_snapshot_zh==null?o.option_name_snapshot_en:o.option_name_snapshot_zh,o.quantity==null?1:o.quantity));
   }
   String source="item:"+line.id;
   lines.add(new KitchenRoutingPlanner.SourceLine(source,null,line.menu_item_id,item==null?null:item.category_id,
    line.item_name_snapshot_zh==null?line.item_name_snapshot_en:line.item_name_snapshot_zh,line.quantity,instructions,null));
   lines.addAll(componentLines(order.store_id,source,line.quantity,choices));
  }
  try {
   var result=plan(order.store_id,order.id,batchId,order.order_no,order.order_type,order.table_no,rev,c,lines);
   for(var ticket:result.tickets()){
    FrozenIntent f=new FrozenIntent();f.group_code=ticket.groupCode();f.group_name=ticket.groupName();f.logical_target=ticket.logicalTarget();
    f.printer_id=ticket.printerId();f.rendered_text=ticket.renderedText();f.source_keys=new ArrayList<>(ticket.sourceKeys());f.menu_revision=rev;f.routing_revision=c.revision;
    if(!ticket.diagnostics().isEmpty()){f.error_code=ticket.diagnostics().get(0).code();f.reason=ticket.diagnostics().get(0).message();}
    f.mock_failure=c.groups.stream().filter(g->g.group_code.equals(f.group_code)).map(g->g.mock_failure).filter(Objects::nonNull).findFirst().orElse(null);
    persist(order,batchId,f,executionMode);
   }
   var unassigned=result.coverage().stream().filter(v->"UNASSIGNED".equals(v.disposition())).toList();
   if(!unassigned.isEmpty()){
    FrozenIntent f=new FrozenIntent();f.diagnostic_only=true;f.group_code="UNASSIGNED";f.group_name="Unassigned kitchen items";f.error_code="UNASSIGNED";f.reason="Required items have no kitchen ticket group";
    f.source_keys=unassigned.stream().map(v->v.sourceKey()).toList();f.menu_revision=rev;f.routing_revision=c.revision;f.rendered_text="";persist(order,batchId,f,executionMode);
   }
  }catch(IllegalArgumentException ex){
   FrozenIntent f=new FrozenIntent();f.diagnostic_only=true;f.group_code="PLAN_INVALID";f.group_name="Kitchen routing";f.error_code="PLAN_INVALID";f.reason="Kitchen routing configuration could not be resolved";
   f.menu_revision=rev;f.routing_revision=c.revision;f.rendered_text="";persist(order,batchId,f,executionMode);
  }
 }
 private List<KitchenRoutingPlanner.SourceLine> componentLines(Long store,String parent,int quantity,List<com.restaurant.system.order.dto.CreateOrderItemOptionRequest> choices){
  List<KitchenRoutingPlanner.SourceLine> sources=new ArrayList<>();
  for(var choice:choices){
   if(choice.option_id==null||choice.option_id>=0)continue;
   var c=combos.resolve(store,choice);
   if(c.linkedItemId()==null||"NO_KITCHEN_TASK".equals(c.behavior()))continue;
   var child=menus.requireItem(store,c.linkedItemId());List<KitchenRoutingPlanner.Instruction> instructions=new ArrayList<>();
   for(var remove:choices)if(Objects.equals(remove.parent_option_id_snapshot,choice.option_id)){
    var leaf=options.findById(remove.option_id).orElseThrow(()->new BusinessException("COMBO_REMOVE_NOT_FOUND"));
    if(!Objects.equals(leaf.menu_item_id,child.id)||!"remove".equalsIgnoreCase(leaf.option_type)||!Boolean.TRUE.equals(leaf.is_active)||leaf.price_delta.signum()!=0)throw new BusinessException("COMBO_REMOVE_SCOPE_MISMATCH");
    instructions.add(new KitchenRoutingPlanner.Instruction(leaf.option_code,leaf.option_group,leaf.name_zh,remove.quantity));
   }
   sources.add(new KitchenRoutingPlanner.SourceLine(parent+":component:"+c.code(),parent,child.id,child.category_id,c.name(),Math.multiplyExact(quantity,choice.quantity),instructions,c.code()));
  }return sources;
 }
 private void persist(Order order,Long batch,FrozenIntent f,String executionMode){
  f.execution_mode=executionMode;
  String key="template:"+(batch==null?"submit:"+order.id:"update:"+order.id+":"+batch)+":"+(f.diagnostic_only?"diagnostic":"group")+":"+f.group_code;
  Long organizationId=revisions.getRevision(order.store_id).organization_id;
  int inserted=outbox.insertIfAbsent(organizationId,order.store_id,order.id,batch,EVENT,EVENT,key,LocalDateTime.now(),PrintOriginContext.deviceFor(order.store_id,organizationId));
  if(inserted==1){var e=outbox.findBySourceKey(key).orElseThrow();e.routingContext=menus.write(f);e.lastError=f.reason;outbox.save(e);}
 }
 private KitchenRoutingPlanner.Result plan(Long store,Long order,Long batch,String number,String type,String table,long rev,Configuration c,List<KitchenRoutingPlanner.SourceLine> lines){
  List<KitchenRoutingPlanner.Group> gs=c.groups.stream().map(g->new KitchenRoutingPlanner.Group(g.group_code,g.name,g.sort_order,g.enabled,g.category_ids,g.item_ids,g.combo_component_codes,g.logical_target,g.printer_id,
   g.printer_id!=null&&printers.findByIdAndStoreId(g.printer_id,store).filter(p->Boolean.TRUE.equals(p.enabled)).isPresent())).toList();
  List<KitchenRoutingPlanner.ItemOverride> os=c.item_overrides.stream().map(o->new KitchenRoutingPlanner.ItemOverride(o.menu_item_id,o.group_codes)).toList();
  return planner.plan(new KitchenRoutingPlanner.Input(new KitchenRoutingPlanner.Context(store,order,batch,number,type,table,Long.toString(rev),Long.toString(c.revision)),lines,gs,os));
 }
 private Map<String,Object> diagnostic(KitchenRoutingPlanner.Diagnostic d){
  Map<String,Object> v=new LinkedHashMap<>();v.put("code",d.code());v.put("message",d.message());v.put("source_key",d.sourceKey());return v;
 }
 public List<Map<String,Object>> printerChoices(Long storeId){
  return printers.findAllByStoreIdOrderByIdAsc(storeId).stream().map(p->Map.<String,Object>of("id",p.id,"name",p.name,"is_active",Boolean.TRUE.equals(p.enabled))).toList();
 }
}
