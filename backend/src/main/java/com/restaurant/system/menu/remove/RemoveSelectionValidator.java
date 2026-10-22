package com.restaurant.system.menu.remove;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuItemOption;
import com.restaurant.system.menu.repository.MenuItemOptionRepository;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.entity.OrderItemOption;
import com.restaurant.system.menu.service.StoreComboConfigurationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One backend boundary for Remove scope, current applicability and per-dish ingredient conflicts. */
@Service
public class RemoveSelectionValidator {
    private final JdbcTemplate jdbc;
    private final MenuItemOptionRepository options;
    private final StoreRemoveOptionResolver resolver;
    private final StoreComboConfigurationService combos;
    private final StoreComboSideItemResolver sideItems;
    public RemoveSelectionValidator(JdbcTemplate jdbc,MenuItemOptionRepository options,
        StoreRemoveOptionResolver resolver,StoreComboConfigurationService combos) {
        this.jdbc=jdbc;this.options=options;this.resolver=resolver;this.combos=combos;
        this.sideItems=new StoreComboSideItemResolver(jdbc);
    }
    private record Selection(Long id,String type,String group,String code,Long parent,MenuItemOption current) {}

    @Transactional(readOnly=true)
    public void validateRequest(Long storeId,Long menuItemId,List<CreateOrderItemOptionRequest> requests) {
        if(requests==null || requests.isEmpty()) return;
        List<Selection> selected=new ArrayList<>();
        for(CreateOrderItemOptionRequest request:requests) {
            if(request==null || request.option_id==null) throw failure("REMOVE_OPTION_INVALID");
            MenuItemOption current=options.findById(request.option_id).orElse(null);
            if(current!=null && current.store_remove_id!=null) current=resolver.resolve(List.of(current)).get(0);
            selected.add(new Selection(request.option_id,current==null?request.option_type_snapshot:current.option_type,
                current==null?request.option_group_snapshot:current.option_group,current==null?request.option_code_snapshot:current.option_code,
                request.parent_option_id_snapshot!=null?request.parent_option_id_snapshot:current==null?null:current.parent_option_id,current));
            if(isRemove(current==null?request.option_group_snapshot:current.option_group,current==null?request.option_type_snapshot:current.option_type)
                && request.option_price_snapshot!=null && request.option_price_snapshot.signum()!=0) throw failure("REMOVE_MUST_BE_FREE");
        }
        if (selected.stream().noneMatch(s -> isRemove(s.group(), s.type()))) return;
        requireItemStore(menuItemId, storeId);
        validate(storeId,menuItemId,selected);
    }

    @Transactional(readOnly=true)
    public void validateSnapshots(Order order,List<OrderItem> lines,List<OrderItemOption> snapshots) {
        if(order==null || snapshots==null) return;
        for(OrderItem line:lines) {
            if ("cancelled".equalsIgnoreCase(line.status)) continue;
            List<CreateOrderItemOptionRequest> requests=snapshots.stream().filter(s->Objects.equals(s.order_item_id,line.id)).map(s->{
                CreateOrderItemOptionRequest request=new CreateOrderItemOptionRequest();
                request.option_id=s.option_id;request.option_type_snapshot=s.option_type_snapshot;request.option_group_snapshot=s.option_group_snapshot;
                request.option_code_snapshot=s.option_code_snapshot;request.parent_option_id_snapshot=s.parent_option_id_snapshot;
                request.option_price_snapshot=s.price_delta;request.quantity=s.quantity;return request;
            }).toList();
            validateRequest(order.store_id,line.menu_item_id,requests);
        }
    }

    private void validate(Long storeId,Long itemId,List<Selection> selected) {
        Map<Long,Selection> byId=new HashMap<>();
        selected.forEach(s->byId.put(s.id(),s));
        Map<String,HashSet<String>> adds=new HashMap<>();
        Map<String,HashSet<String>> removes=new HashMap<>();
        for(Selection selection:selected) {
            boolean removal=isRemove(selection.group(),selection.type());
            if(!removal) {
                if("ADD_ON".equals(upper(selection.group())) || (blank(selection.group()) && "addon".equals(lower(selection.type())))) {
                    String ingredient=ingredient(selection.code());
                    if(ingredient!=null) adds.computeIfAbsent("MAIN",ignored->new HashSet<>()).add(ingredient);
                }
                continue;
            }
            MenuItemOption current=selection.current();
            // A cached label cannot prove that a foreign or missing Remove belongs to this Store.
            if(current==null) throw failure("REMOVE_ASSIGNMENT_UNAVAILABLE");
            requireItemStore(current.menu_item_id,storeId);
            if (!Boolean.TRUE.equals(current.is_active)) throw failure("REMOVE_ASSIGNMENT_UNAVAILABLE");
            if(current.price_delta==null || current.price_delta.signum()!=0) throw failure("REMOVE_MUST_BE_FREE");
            if(current.store_remove_id!=null && (!Objects.equals(storeId,current.store_remove_store_id) || !Boolean.TRUE.equals(current.is_active))) throw failure("REMOVE_ASSIGNMENT_UNAVAILABLE");
            String scope="MAIN";
            if(selection.parent()!=null || !Objects.equals(current.menu_item_id,itemId) || "COMBO_SIDE_REMOVE".equals(upper(selection.group()))) {
                Selection parent=selection.parent()==null?findSelectedSideForItem(storeId,current.menu_item_id,selected):byId.get(selection.parent());
                if (selected.stream().noneMatch(s -> "COMBO".equals(upper(s.group())) || "combo".equals(lower(s.code())))) throw failure("REMOVE_COMBO_REQUIRED");
                if(parent==null || !"COMBO_SIDE".equals(upper(parent.group()))) throw failure("REMOVE_COMBO_SIDE_REQUIRED");
                if(parent.current()!=null && !Objects.equals(parent.current().menu_item_id,itemId)) throw failure("REMOVE_COMBO_SIDE_INVALID");
                combos.requireSnapshotEnabledForNewSelection(storeId,"COMBO_SIDE",parent.code());
                if("COMBO_SIDE_REMOVE".equals(upper(current.option_group))) {
                    if(!Objects.equals(current.menu_item_id,itemId) || !Objects.equals(current.parent_option_id,parent.id())) throw failure("REMOVE_COMBO_SIDE_INVALID");
                } else {
                    if (!Objects.equals(sideItems.resolve(storeId,parent.code()),current.menu_item_id)) throw failure("REMOVE_COMBO_SIDE_INVALID");
                }
                scope="SIDE:"+parent.id();
            }
            String ingredient=ingredient(selection.code());
            if(ingredient!=null) removes.computeIfAbsent(scope,ignored->new HashSet<>()).add(ingredient);
        }
        for(var entry:removes.entrySet()) for(String ingredient:entry.getValue()) {
            if(adds.getOrDefault(entry.getKey(),new HashSet<>()).contains(ingredient)) throw failure("ADD_REMOVE_CONFLICT: "+ingredient);
        }
    }
    private Selection findSelectedSideForItem(Long storeId,Long itemId,List<Selection> selected) {
        for(Selection s:selected) {
            if("COMBO_SIDE".equals(upper(s.group())) && Objects.equals(sideItems.resolve(storeId,s.code()),itemId)) return s;
        }
        return null;
    }
    private void requireItemStore(Long itemId,Long storeId) {
        if(itemId==null || !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from menu_items where id=? and store_id=?)",Boolean.class,itemId,storeId))) throw failure("REMOVE_STORE_MISMATCH");
    }
    public Long resolveSideItem(Long storeId, String componentCode) {
        return sideItems.resolve(storeId, componentCode);
    }

    public static String ingredient(String code) {
        String value=lower(code);
        for(String prefix:List.of("remove_","extra_","add_","addon_")) if(value.startsWith(prefix)) {value=value.substring(prefix.length());break;}
        return switch(value) {
            case "","all_vegetables"->null;
            case "carrot_slice"->"carrot";
            case "crushed_peanut"->"peanut";
            case "beef"->"meat";
            default->value;
        };
    }
    private static boolean isRemove(String group,String type) {return "REMOVE".equals(upper(group)) || "COMBO_SIDE_REMOVE".equals(upper(group)) || "remove".equals(lower(type));}
    private static String upper(String value) {return value==null?"":value.trim().toUpperCase(Locale.ROOT);}
    private static String lower(String value) {return value==null?"":value.trim().toLowerCase(Locale.ROOT);}
    private static boolean blank(String value) {return value==null || value.isBlank();}
    private static BusinessException failure(String message) {return new BusinessException(message);}
}
