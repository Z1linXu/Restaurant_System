package com.restaurant.system.printing.rules;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read-only projection: Menu owns identity; published legacy aliases are retained. */
@Component
public class PrintingAddonVocabulary {
    public record Entry(String code, String name_zh, String default_print_text, String source) {}
    private final JdbcTemplate jdbc;

    public PrintingAddonVocabulary(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Entry> entries(Long storeId, JsonNode legacyContent) {
        Map<String, Entry> result = new LinkedHashMap<>();
        jdbc.query("""
            select d.code,a.name_zh from store_addons a
            join organization_addon_definitions d on d.id=a.organization_addon_definition_id
              and d.organization_id=a.organization_id
            join stores s on s.id=a.store_id and s.organization_id=a.organization_id
            where a.store_id=? order by d.code
            """, rs -> {
                String code = rs.getString("code");
                result.put(code, new Entry(code, rs.getString("name_zh"), rs.getString("name_zh"), "MENU"));
            }, storeId);
        jdbc.query("""
            select o.option_code, min(o.name_zh) as name_zh, count(distinct o.name_zh) as names
            from menu_item_options o join menu_items i on i.id=o.menu_item_id
            where i.store_id=? and (o.option_group='ADD_ON' or
              ((o.option_group is null or o.option_group='') and o.option_type='addon'))
              and o.option_code is not null and o.option_code<>'' and o.option_code<>'combo'
            group by o.option_code order by o.option_code
            """, rs -> {
                String code = rs.getString("option_code");
                String name = rs.getInt("names") == 1 ? rs.getString("name_zh") : null;
                result.putIfAbsent(code, new Entry(code, name, name, "LEGACY_MENU"));
            }, storeId);
        jdbc.query("select component_code,name_zh from store_combo_components where store_id=? order by component_code",
            rs -> {
                String code = rs.getString("component_code");
                result.put(code, new Entry(code, rs.getString("name_zh"), rs.getString("name_zh"), "COMBO"));
            }, storeId);
        for (JsonNode pair : legacyContent.path("dictionaries").path("MODIFIER_ADD")) {
            if (pair.isArray() && pair.size() == 2 && pair.get(0).isTextual()) {
                String code = pair.get(0).asText();
                result.putIfAbsent(code, new Entry(code, null, null, "LEGACY_ALIAS"));
            }
        }
        return new ArrayList<>(result.values());
    }
}
