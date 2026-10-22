package com.restaurant.system.menu.remove;

import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Current Store Combo binding is authoritative; SKU mapping is legacy compatibility only. */
@Service
public class StoreComboSideItemResolver {
    private final JdbcTemplate jdbc;

    public StoreComboSideItemResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Binding(Long itemId, boolean enabled, boolean groupEnabled, boolean archived) {}

    public Long resolve(Long storeId, String componentCode) {
        String code = componentCode == null ? "" : componentCode.trim().toLowerCase(Locale.ROOT);
        List<Binding> configured = jdbc.query("""
            select c.linked_menu_item_id,c.enabled,g.enabled group_enabled,
                (c.archived_at is not null or g.archived_at is not null) archived
            from store_combo_components c join store_combo_groups g
              on g.id=c.group_id and g.store_id=c.store_id
            where c.store_id=? and c.component_group='COMBO_SIDE' and g.group_code='COMBO_SIDE'
              and c.component_code=?
            """, (rs, n) -> new Binding(rs.getObject("linked_menu_item_id", Long.class),
                rs.getBoolean("enabled"), rs.getBoolean("group_enabled"), rs.getBoolean("archived")), storeId, code);
        if (!configured.isEmpty()) {
            Binding binding = configured.get(0);
            if (!binding.enabled() || !binding.groupEnabled() || binding.archived()) return null;
            if (binding.itemId() != null) {
                return availableItem(storeId, binding.itemId()) ? binding.itemId() : null;
            }
        } else if (Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from store_combo_groups where store_id=? and group_code='COMBO_SIDE')
            """, Boolean.class, storeId))) {
            // A configured group with no matching component cannot be revived by a legacy SKU.
            return null;
        }
        String sku = switch (code) {
            case "combo_cucumber_salad", "cucumber_salad" -> "cucumber_salad";
            case "combo_shredded_potato", "shredded_potato" -> "shredded_potato";
            case "combo_edamame", "edamame" -> "edamame";
            default -> null;
        };
        if (sku == null) return null;
        List<Long> ids = jdbc.query("""
            select id from menu_items where store_id=? and sku=? and is_active=true order by id
            """, (rs, n) -> rs.getLong(1), storeId, sku);
        return ids.size() == 1 ? ids.get(0) : null;
    }

    private boolean availableItem(Long storeId, Long itemId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from menu_items where id=? and store_id=? and is_active=true)
            """, Boolean.class, itemId, storeId));
    }
}
