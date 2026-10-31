package com.restaurant.system.menu.template;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.auth.ForbiddenException;
import com.restaurant.system.menu.entity.*;
import com.restaurant.system.menu.repository.*;
import com.restaurant.system.menu.service.MenuRevisionService;
import com.restaurant.system.menu.template.TemplateMenuModels.*;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** New group metadata; existing item Option leaves remain the single runtime price authority. */
@Service
public class TemplateMenuService {
 private final MenuItemRepository items;
 private final MenuItemOptionRepository options;
 private final TemplateItemOptionGroupRepository groups;
 private final TemplateStoreConfigurationRepository configs;
 private final MenuRevisionService revisions;
 private final ObjectMapper mapper;
 private final boolean enabled;
 private TemplateComboAdapter comboAdapter;
 @org.springframework.beans.factory.annotation.Autowired
 public void setComboAdapter(TemplateComboAdapter value){this.comboAdapter=value;}
 private com.restaurant.system.menu.service.StorePricingPolicyService pricing;
 @org.springframework.beans.factory.annotation.Autowired
 public void setPricing(com.restaurant.system.menu.service.StorePricingPolicyService value){this.pricing=value;}
 public TemplateMenuService(MenuItemRepository items,MenuItemOptionRepository options,
  TemplateItemOptionGroupRepository groups,TemplateStoreConfigurationRepository configs,
  MenuRevisionService revisions,ObjectMapper mapper,Environment env) {
  this.items=items;this.options=options;this.groups=groups;this.configs=configs;
  this.revisions=revisions;this.mapper=mapper;this.enabled=env.getProperty("app.template.enabled",Boolean.class,false);
 }
 public boolean enabled(){return enabled;}
 public void lockMenuIfEnabled(Long storeId){if(enabled)revisions.lockStoresInOrder(List.of(storeId));}
 public boolean manages(MenuItem item){return enabled && item!=null && item.template_option_groups_enabled;}
 @Transactional(readOnly=true)
 public Groups getItem(Long storeId,Long itemId) {
  requireItem(storeId,itemId);Groups out=new Groups();out.menu_revision=revisions.getRevision(storeId).menu_revision;
  List<MenuItemOption> leaves=options.findAllByMenuItemIdOrdered(itemId);
  for(var row:groups.forItem(itemId)) {
   Group g=new Group();g.group_instance_code=row.group_code;g.name=row.name;g.kind=row.kind;
   g.selection_mode=row.selection_mode;g.required=row.required;g.is_active=row.is_active;
   g.sort_order=row.sort_order;g.source_library_code=row.source_library_code;
   for(var leaf:leaves)if(Objects.equals(row.group_code,leaf.template_group_code)){
    Option o=new Option();o.id=leaf.id;o.option_type=leaf.option_type;o.option_code=leaf.option_code;
    o.option_group=leaf.option_group;o.parent_option_id=leaf.parent_option_id;o.sort_order=leaf.sort_order==null?0:leaf.sort_order;
    o.name_zh=leaf.name_zh;o.name_en=leaf.name_en;o.price_delta=leaf.price_delta;
    o.is_active=Boolean.TRUE.equals(leaf.is_active);o.is_default=leaf.template_default;
    g.options.add(o);if(o.is_default)g.default_option_ids.add(o.id);
   }out.groups.add(g);
  }return out;
 }
 @Transactional
 public Groups saveItem(Long storeId,Long itemId,Groups request) {
  requireEnabled();revisions.lockStoresInOrder(List.of(storeId));MenuItem item=requireItem(storeId,itemId);validate(request);
  Map<String,TemplateItemOptionGroup> before=new HashMap<>();groups.forItem(itemId).forEach(g->before.put(g.group_code,g));
  Map<Long,MenuItemOption> leaves=new HashMap<>();options.findAllByMenuItemIdOrdered(itemId).forEach(o->leaves.put(o.id,o));
  Set<Long> retained=new HashSet<>();Set<String> retainedGroups=new HashSet<>();
  for(Group g:request.groups) {
   String code=code(g.group_instance_code);g.group_instance_code=code;retainedGroups.add(code);
   var row=before.getOrDefault(code,new TemplateItemOptionGroup());
   row.store_id=storeId;row.menu_item_id=itemId;row.group_code=code;row.name=g.name.trim();row.kind=g.kind;
   row.selection_mode=g.selection_mode;row.required=g.required;row.is_active=g.is_active;row.sort_order=g.sort_order;
   row.source_library_code=g.source_library_code;groups.save(row);
   for(Option o:g.options) {
    MenuItemOption leaf=o.id==null?new MenuItemOption():leaves.get(o.id);
    if(leaf==null || (o.id!=null && !Objects.equals(code,leaf.template_group_code)))throw failure("TEMPLATE_OPTION_SCOPE_MISMATCH");
    if(o.id!=null && !retained.add(o.id))throw failure("TEMPLATE_OPTION_DUPLICATE");
    if(o.id==null && o.source_option_id!=null) {
     var source=options.findById(o.source_option_id).orElseThrow(()->failure("SOURCE_OPTION_NOT_FOUND"));
     requireItem(storeId,source.menu_item_id);
     leaf.store_addon_id=source.store_addon_id;leaf.store_addon_store_id=source.store_addon_store_id;leaf.addon_eligible=source.addon_eligible;
     leaf.store_remove_id=source.store_remove_id;leaf.store_remove_store_id=source.store_remove_store_id;leaf.option_code=source.option_code;
    }
    leaf.menu_item_id=itemId;leaf.template_group_code=code;leaf.option_type=type(g.kind);leaf.option_group=g.kind;
    if(leaf.option_code==null)leaf.option_code=o.option_code==null || o.option_code.isBlank()?"tpl_"+UUID.randomUUID().toString().replace("-",""):o.option_code;
    leaf.name_zh=o.name_zh.trim();leaf.name_en=o.name_en;leaf.price_delta=o.price_delta;
    leaf.sort_order=o.sort_order;leaf.is_active=o.is_active;leaf.template_default=o.is_default || (o.id!=null && g.default_option_ids.contains(o.id));
    leaf.updated_at=LocalDateTime.now();if(leaf.created_at==null)leaf.created_at=leaf.updated_at;
    leaf=options.save(leaf);retained.add(leaf.id);
   }
  }
  boolean combo=request.groups.stream().anyMatch(g->g.is_active && "COMBO".equals(g.kind));
  for(var old:leaves.values())if(!retained.contains(old.id) && (!isCombo(old)||!combo)){old.is_active=false;old.template_default=false;options.save(old);}
  for(var old:before.values())if(!retainedGroups.contains(old.group_code))groups.delete(old);
  item.template_option_groups_enabled=true;items.save(item);
  if(pricing!=null){var policy=new com.restaurant.system.menu.dto.MenuItemComboPolicyRequest();policy.combo_allowed=combo;pricing.updateComboPolicy(itemId,policy);}
  revisions.incrementRevision(storeId);return getItem(storeId,itemId);
 }
 @Transactional(readOnly=true)
 public Groups library(Long storeId) {
  requireEnabled();var c=configs.findById(storeId).orElse(null);if(c!=null)return read(c.library_json,Groups.class);
  Groups initial=new Groups();
  for(String kind:List.of("SPICY_LEVEL","ADD_ON","REMOVE","SIZE","COMBO")){
   Group g=new Group();g.group_instance_code="preset_"+kind.toLowerCase(Locale.ROOT);g.name=switch(kind){case "SPICY_LEVEL"->"Spicy Level";case "ADD_ON"->"Add-on";case "REMOVE"->"Remove";case "SIZE"->"Size";default->"Combo";};
   g.kind=kind;g.selection_mode=Set.of("ADD_ON","REMOVE").contains(kind)?"MULTIPLE":"SINGLE";g.sort_order=initial.groups.size();initial.groups.add(g);
  }return initial;
 }
 @Transactional
 public Groups saveLibrary(Long storeId,Groups request){
  requireEnabled();revisions.lockStoresInOrder(List.of(storeId));validate(request);
  for(Group g:request.groups){g.group_instance_code=code(g.group_instance_code);for(Option o:g.options){o.id=null;o.source_option_id=null;}}
  var c=configs.findById(storeId).orElseGet(TemplateStoreConfiguration::new);c.store_id=storeId;c.library_json=write(request);configs.save(c);return library(storeId);
 }
 public void validateSelection(Long storeId,CreateOrderItemRequest request){
  MenuItem item=items.findById(request.menu_item_id).orElse(null);if(!manages(item))return;requireItem(storeId,item.id);
  if(!Boolean.TRUE.equals(item.is_active)||Boolean.TRUE.equals(item.is_sold_out))throw failure("TEMPLATE_ITEM_UNAVAILABLE");
  if(request.quantity==null||request.quantity<1)throw failure("ITEM_QUANTITY_INVALID");
  if(request.station_id_snapshot!=null && !Objects.equals(request.station_id_snapshot,item.station_id))throw failure("TEMPLATE_STATION_MISMATCH");
  if(request.unit_price_snapshot!=null && request.unit_price_snapshot.compareTo(item.base_price)!=0)throw failure("MENU_PRICE_CHANGED: refresh menu and retain draft");
  var selected=request.options==null?List.<com.restaurant.system.order.dto.CreateOrderItemOptionRequest>of():request.options;
  Set<Long> ids=new HashSet<>();Map<Long,MenuItemOption> all=new HashMap<>();options.findAllByMenuItemIdOrdered(item.id).forEach(o->all.put(o.id,o));
  boolean hasCombo=groups.forItem(item.id).stream().anyMatch(g->g.is_active && "COMBO".equals(g.kind));
  for(var o:selected){
   if(o==null||o.option_id==null||o.quantity==null||o.quantity<1||o.quantity>999||!ids.add(o.option_id))throw failure("TEMPLATE_OPTION_INVALID");
   var leaf=all.get(o.option_id);
   if(leaf==null){
    // The mature Combo/Remove validators remain authoritative for virtual IDs and child removals.
    if(hasCombo && o.option_id<0 && comboAdapter!=null){comboAdapter.resolve(storeId,o);continue;}
    if(hasCombo && "COMBO_SIDE_REMOVE".equals(o.option_group_snapshot))continue;
    throw failure("TEMPLATE_OPTION_SCOPE_MISMATCH");
   }
   if(!Boolean.TRUE.equals(leaf.is_active))throw failure("TEMPLATE_OPTION_UNAVAILABLE");
   if(o.option_price_snapshot!=null && o.option_price_snapshot.compareTo(leaf.price_delta)!=0)throw failure("TEMPLATE_OPTION_PRICE_CHANGED");
   if(leaf.template_group_code!=null && o.option_code_snapshot!=null && !o.option_code_snapshot.equals(leaf.option_code))throw failure("TEMPLATE_OPTION_CODE_CHANGED");
  }
  boolean comboSelected=selected.stream().anyMatch(o->all.containsKey(o.option_id) && "COMBO".equals(all.get(o.option_id).option_group));
  if(selected.stream().anyMatch(o->o.option_id<0) && !comboSelected)throw failure("COMBO_PARENT_REQUIRED");
  if(comboSelected && comboAdapter!=null)comboAdapter.validateSelected(storeId,selected);
  for(var g:groups.forItem(item.id)){
   long n=all.values().stream().filter(o->ids.contains(o.id)&&Objects.equals(g.group_code,o.template_group_code)).count();
   if(!g.is_active && n>0)throw failure("TEMPLATE_GROUP_DISABLED");
   if("SINGLE".equals(g.selection_mode) && selected.stream().anyMatch(o->all.containsKey(o.option_id) && Objects.equals(g.group_code,all.get(o.option_id).template_group_code) && o.quantity!=1))
       throw failure("TEMPLATE_GROUP_SELECTION_INVALID: single choice quantity must be one");
   if(g.is_active && !"COMBO".equals(g.kind) && ((g.required&&n==0)||("SINGLE".equals(g.selection_mode)&&n>1)))throw failure("TEMPLATE_GROUP_SELECTION_INVALID: "+g.name);
  }
 }
 public void validateSnapshots(com.restaurant.system.order.entity.Order order,List<com.restaurant.system.order.entity.OrderItem> lines,List<com.restaurant.system.order.entity.OrderItemOption> snapshots){
  for(var line:lines){
   var request=new CreateOrderItemRequest();request.menu_item_id=line.menu_item_id;request.quantity=line.quantity;
   request.unit_price_snapshot=line.unit_price;request.station_id_snapshot=line.station_id_snapshot;
   request.options=new ArrayList<>();
   for(var o:snapshots)if(Objects.equals(line.id,o.order_item_id)){
    var selected=new com.restaurant.system.order.dto.CreateOrderItemOptionRequest();selected.option_id=o.option_id;selected.quantity=o.quantity;
    selected.option_code_snapshot=o.option_code_snapshot;selected.option_group_snapshot=o.option_group_snapshot;
    selected.option_price_snapshot=o.price_delta;selected.parent_option_id_snapshot=o.parent_option_id_snapshot;request.options.add(selected);
   }
   validateSelection(order.store_id,request);
  }
 }
 public MenuItem requireItem(Long storeId,Long itemId){
  var i=items.findById(itemId).orElseThrow(()->failure("MENU_ITEM_NOT_FOUND"));if(!Objects.equals(storeId,i.store_id))throw new ForbiddenException("Menu item belongs to another store");return i;
 }
 public void requireEnabled(){if(!enabled)throw failure("TEMPLATE_FEATURE_DISABLED");}
 public <T>T read(String json,Class<T> type){try{return mapper.readValue(json,type);}catch(JsonProcessingException ex){throw failure("TEMPLATE_CONFIGURATION_INVALID");}}
 public String write(Object v){try{return mapper.writeValueAsString(v);}catch(JsonProcessingException ex){throw failure("TEMPLATE_CONFIGURATION_INVALID");}}
 private void validate(Groups request){
  if(request==null||request.groups==null||request.groups.size()>100)throw failure("TEMPLATE_GROUPS_INVALID");
  Set<String> codes=new HashSet<>();
  for(Group g:request.groups){
   if(g==null||g.name==null||g.name.isBlank()||g.name.length()>160||g.kind==null||!Set.of("SPICY_LEVEL","ADD_ON","REMOVE","SIZE","CUSTOM","COMBO").contains(g.kind)||g.selection_mode==null||!Set.of("SINGLE","MULTIPLE").contains(g.selection_mode)||g.options==null||g.options.size()>1000)throw failure("TEMPLATE_GROUP_INVALID");
   if(g.group_instance_code!=null&&!g.group_instance_code.isBlank()&&!codes.add(g.group_instance_code))throw failure("TEMPLATE_GROUP_DUPLICATE");
   if("COMBO".equals(g.kind) && !g.options.isEmpty())throw failure("COMBO_USE_EXISTING_CONFIGURATION");
   if(g.default_option_ids==null)g.default_option_ids=new ArrayList<>();
   long defaults=0;
   for(Option o:g.options){
    if(o==null||o.name_zh==null||o.name_zh.isBlank()||o.name_zh.length()>160||o.price_delta==null||o.price_delta.signum()<0||o.price_delta.scale()>2)throw failure("TEMPLATE_OPTION_INVALID");
    if("REMOVE".equals(g.kind)&&o.price_delta.signum()!=0)throw failure("REMOVE_MUST_BE_FREE");
    boolean selected=o.is_default||(o.id!=null&&g.default_option_ids.contains(o.id));
    if(selected){defaults++;if(!o.is_active)throw failure("TEMPLATE_DEFAULT_INVALID");}
   }
   if("SINGLE".equals(g.selection_mode)&&defaults>1)throw failure("TEMPLATE_DEFAULT_INVALID");
  }
 }
 private String code(String v){if(v==null||v.isBlank())return UUID.randomUUID().toString();if(!v.matches("[A-Za-z0-9_-]{1,64}"))throw failure("TEMPLATE_CODE_INVALID");return v;}
 private String type(String k){return switch(k){case "SPICY_LEVEL"->"spicy_level";case "REMOVE"->"remove";case "SIZE"->"size";default->"addon";};}
 private boolean isCombo(MenuItemOption o){return o.option_group!=null&&o.option_group.startsWith("COMBO");}
 private BusinessException failure(String code){return new BusinessException(code);}
}
