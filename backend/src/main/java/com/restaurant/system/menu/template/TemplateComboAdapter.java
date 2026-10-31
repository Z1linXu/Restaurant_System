package com.restaurant.system.menu.template;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import java.util.*;
import org.springframework.stereotype.Service;

/** Boundary adapter for the existing five stable virtual Combo IDs, not a new Combo engine. */
@Service
public class TemplateComboAdapter {
 public record Component(Long virtualId,String group,String code,String name,Long linkedItemId,String behavior) {}
 private static final Map<Long,String> CODES=Map.of(-20101L,"combo_tea_egg",-20102L,"combo_fried_egg",-20201L,"combo_edamame",-20202L,"combo_shredded_potato",-20203L,"combo_cucumber_salad");
 private final StoreComboConfigurationService combos;
 public TemplateComboAdapter(StoreComboConfigurationService combos){this.combos=combos;}
 public Component resolve(Long store,CreateOrderItemOptionRequest option){
  String code=CODES.get(option.option_id);
  if(code==null)throw new BusinessException("COMBO_VIRTUAL_OPTION_INVALID");
  for(var group:combos.getConfiguration(store).groups) for(var c:group.components) if(code.equals(c.component_code)){
   if(!Boolean.TRUE.equals(group.enabled)||!Boolean.TRUE.equals(c.enabled))throw new BusinessException("COMBO_COMPONENT_DISABLED");
   if(option.option_code_snapshot!=null&&!code.equals(option.option_code_snapshot))throw new BusinessException("COMBO_VIRTUAL_CODE_MISMATCH");
   if(option.option_group_snapshot!=null&&!group.component_group.equals(option.option_group_snapshot))throw new BusinessException("COMBO_VIRTUAL_GROUP_MISMATCH");
   if(option.option_price_snapshot!=null&&option.option_price_snapshot.signum()!=0)throw new BusinessException("COMBO_COMPONENT_PRICE_INVALID");
   if(option.quantity==null||option.quantity!=1)throw new BusinessException("COMBO_COMPONENT_QUANTITY_INVALID");
   return new Component(option.option_id,group.component_group,code,c.name_zh==null?c.name_en:c.name_zh,c.linked_menu_item_id,c.business_behavior);
  }
  throw new BusinessException("COMBO_COMPONENT_UNAVAILABLE");
 }
 public void validateSelected(Long store,List<CreateOrderItemOptionRequest> selected){
  for(var g:combos.getConfiguration(store).groups){
   if(!Boolean.TRUE.equals(g.enabled))continue;
   long count=selected.stream().filter(o->o.option_id!=null&&o.option_id<0)
    .map(o->resolve(store,o)).filter(c->Objects.equals(c.group(),g.component_group)).count();
   if(count>1 || (Boolean.TRUE.equals(g.required)&&count!=1))throw new BusinessException("COMBO_SELECTION_INVALID: "+g.component_group);
  }
 }
}
