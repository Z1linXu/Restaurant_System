package com.restaurant.system.printing.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.menu.remove.RemoveSemantics;
import com.restaurant.system.printing.semantic.KitchenModifierTokenResolver;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** One normal editor row per Store-owned Remove semantic, independently of item assignments. */
@Component
public class PrintingRemoveVocabulary {
    public record Entry(String code, String name_zh, String default_print_text, String source, List<String> alias_codes) {}
    private final JdbcTemplate jdbc;

    public PrintingRemoveVocabulary(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Entry> entries(Long storeId, JsonNode retainedContent) {
        Map<String, LinkedHashSet<String>> names = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> codes = new LinkedHashMap<>();
        jdbc.query("select code,name_zh from store_removes where store_id=? order by sort_order,id", rs -> {
            String raw = rs.getString("code");
            String semantic = RemoveSemantics.canonicalSemantic(raw);
            if (semantic == null) return;
            names.computeIfAbsent(semantic, ignored -> new LinkedHashSet<>()).add(rs.getString("name_zh"));
            codes.computeIfAbsent(semantic, ignored -> new LinkedHashSet<>()).add(raw);
        }, storeId);
        List<PrintingRemoveAliases.Alias> retained = PrintingRemoveAliases.analyze(retainedContent).aliases();
        List<Entry> result = new ArrayList<>();
        names.forEach((semantic, labels) -> {
            LinkedHashSet<String> aliases = codes.get(semantic);
            aliases.add(semantic);
            aliases.add("remove_" + semantic);
            retained.stream().filter(alias -> semantic.equals(RemoveSemantics.canonicalSemantic(alias.code())))
                .forEach(alias -> aliases.add(alias.code()));
            String name = String.join(" / ", labels);
            String defaultText = RemoveSemantics.baselineDefaults().get(semantic);
            if (defaultText == null && labels.size() == 1) {
                defaultText = KitchenModifierTokenResolver.resolveRemove(codes.get(semantic).iterator().next(), name, null, null);
            }
            result.add(new Entry(semantic, name, defaultText, "MENU", List.copyOf(aliases)));
        });
        return result;
    }
}
