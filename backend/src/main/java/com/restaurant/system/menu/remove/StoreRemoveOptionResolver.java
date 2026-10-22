package com.restaurant.system.menu.remove;

import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.entity.MenuItemOption;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only current projection. Historical and frozen draft snapshots never pass through it. */
@Service
public class StoreRemoveOptionResolver {
    private final NamedParameterJdbcTemplate jdbc;
    public StoreRemoveOptionResolver(NamedParameterJdbcTemplate jdbc) {this.jdbc=jdbc;}
    private record Current(Long id,Long store,String code,String zh,String en,Integer sort,boolean active,boolean eligible,String type,String group,Long parent) {}
    public boolean hasCanonicalCode(Long storeId, String code) {
        if (RemoveSemantics.canonicalSemantic(code) == null) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from store_removes where store_id=:store and (code=:code or code=:prefixed))",
            new MapSqlParameterSource("store", storeId).addValue("code", RemoveSemantics.canonicalSemantic(code)).addValue("prefixed", "remove_" + RemoveSemantics.canonicalSemantic(code)), Boolean.class));
    }

    public List<MenuItemOption> resolve(List<MenuItemOption> options) {
        List<Long> ids=options.stream().filter(o->o.store_remove_id!=null).map(o->o.id).distinct().toList();
        if(ids.isEmpty()) return options;
        Map<Long,Current> rows=jdbc.query("""
            select o.id,r.store_id,o.option_code,o.name_zh,o.name_en,o.sort_order,(r.active and o.is_active) active,o.option_type,o.option_group,o.parent_option_id,
            (coalesce(rule.mode='ALLOW',false) or (rule.mode is null and exists (
            select 1 from store_remove_categories c where c.remove_id=r.id and c.store_id=i.store_id and c.category_id=i.category_id))) eligible
            from menu_item_options o join menu_items i on i.id=o.menu_item_id
            join store_removes r on r.id=o.store_remove_id and r.store_id=i.store_id and r.store_id=o.store_remove_store_id
            left join store_remove_item_rules rule on rule.remove_id=r.id and rule.menu_item_id=i.id
            where o.id in (:ids)
            """,new MapSqlParameterSource("ids",ids),(rs,n)->new Current(rs.getLong("id"),rs.getLong("store_id"),rs.getString("option_code"),
            rs.getString("name_zh"),rs.getString("name_en"),rs.getObject("sort_order",Integer.class),rs.getBoolean("active"),rs.getBoolean("eligible"),rs.getString("option_type"),rs.getString("option_group"),rs.getObject("parent_option_id",Long.class)))
            .stream().collect(Collectors.toMap(Current::id,r->r));
        return options.stream().map(option->{
            if(option.store_remove_id==null) return option;
            Current row=rows.get(option.id);
            if(row==null || !Objects.equals(row.store(),option.store_remove_store_id)) throw new BusinessException("REMOVE_REFERENCE_INVALID");
            MenuItemOption projection=new MenuItemOption();
            projection.id=option.id;projection.menu_item_id=option.menu_item_id;
            projection.store_remove_id=option.store_remove_id;projection.store_remove_store_id=option.store_remove_store_id;
            projection.option_type=row.type();projection.option_group=row.group();projection.option_code=row.code();
            projection.name_zh=row.zh();projection.name_en=row.en();projection.sort_order=row.sort();
            projection.parent_option_id=row.parent();projection.price_delta=BigDecimal.ZERO;projection.is_active=row.active()&&row.eligible();
            projection.created_at=option.created_at;projection.updated_at=option.updated_at;
            return projection;
        }).toList();
    }
}
