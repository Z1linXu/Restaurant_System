package com.restaurant.system.menu.addon;

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

/** Current configuration only. Never resolve an already-frozen order or print snapshot. */
@Service
public class StoreAddonOptionResolver {
    private final NamedParameterJdbcTemplate jdbc;
    public StoreAddonOptionResolver(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private record Current(Long id, Long store, String code, String zh, String en, BigDecimal price, boolean active) {}

    public List<MenuItemOption> resolve(List<MenuItemOption> options) {
        List<Long> ids = options.stream().map(o -> o.store_addon_id).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return options;
        Map<Long, Current> catalog = jdbc.query("""
            select a.id,a.store_id,d.code,a.name_zh,a.name_en,a.price,a.active
            from store_addons a join organization_addon_definitions d
              on d.id=a.organization_addon_definition_id and d.organization_id=a.organization_id
            where a.id in (:ids)
            """, new MapSqlParameterSource("ids", ids), (rs, n) -> new Current(rs.getLong("id"),
                rs.getLong("store_id"), rs.getString("code"), rs.getString("name_zh"),
                rs.getString("name_en"), rs.getBigDecimal("price"), rs.getBoolean("active")))
            .stream().collect(Collectors.toMap(Current::id, c -> c));
        return options.stream().map(option -> {
            if (option.store_addon_id == null) return option;
            Current addon = catalog.get(option.store_addon_id);
            if (addon == null || !addon.store().equals(option.store_addon_store_id) || option.addon_eligible == null) {
                throw new BusinessException("ADDON_REFERENCE_INVALID");
            }
            MenuItemOption current = new MenuItemOption();
            // Detached projection: never dirty a managed entity during a catalog read.
            current.id = option.id;
            current.menu_item_id = option.menu_item_id;
            current.store_addon_id = option.store_addon_id;
            current.store_addon_store_id = option.store_addon_store_id;
            current.addon_eligible = option.addon_eligible;
            current.parent_option_id = option.parent_option_id;
            current.sort_order = option.sort_order;
            current.created_at = option.created_at;
            current.updated_at = option.updated_at;
            current.option_code = addon.code();
            current.option_type = "addon";
            current.option_group = "ADD_ON";
            current.name_zh = addon.zh();
            current.name_en = addon.en();
            current.price_delta = addon.price();
            current.is_active = addon.active() && Boolean.TRUE.equals(option.addon_eligible);
            return current;
        }).toList();
    }

    public boolean requiresAssignment(Long storeId, String group, String code) {
        String normalized = group == null ? "" : group.trim().toUpperCase(java.util.Locale.ROOT);
        // Combo components and removals remain separate existing business semantics.
        if (java.util.Set.of("COMBO", "COMBO_EGG", "COMBO_SIDE", "COMBO_SIDE_REMOVE", "REMOVE").contains(normalized)) return false;
        return hasCanonicalCode(storeId, code);
    }

    public boolean hasCanonicalCode(Long storeId, String code) {
        if (code == null || code.isBlank()) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from store_addons a join organization_addon_definitions d
              on d.id=a.organization_addon_definition_id
              where a.store_id=:store and d.code=:code)
            """, new MapSqlParameterSource("store", storeId).addValue("code", code.trim().toLowerCase(java.util.Locale.ROOT)), Boolean.class));
    }
}
