package com.restaurant.system.printing.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.restaurant.system.menu.remove.RemoveSemantics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Pure read/preview helpers; never changes published content or chooses a conflicting alias. */
public final class PrintingRemoveAliases {
    public record Alias(String code, String value) {}
    public record Conflict(String code, List<Alias> aliases) {}
    public record Analysis(ArrayNode proposed_aliases, List<Conflict> conflicts, List<Alias> aliases) {}

    private PrintingRemoveAliases() {}

    public static Analysis analyze(JsonNode content) {
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        List<JsonNode> unrecognized = new ArrayList<>();
        for (JsonNode pair : content.path("dictionaries").path("MODIFIER_REMOVE")) {
            if (!pair.isArray() || pair.size() != 2 || !pair.get(0).isTextual()
                || (!pair.get(1).isTextual() && !pair.get(1).isNull())) {
                unrecognized.add(pair);
                continue;
            }
            String semantic = RemoveSemantics.canonicalSemantic(pair.get(0).asText());
            if (semantic != null) groups.computeIfAbsent(semantic, ignored -> new ArrayList<>()).add(pair);
            else unrecognized.add(pair);
        }
        ArrayNode proposed = JsonNodeFactory.instance.arrayNode();
        List<Conflict> conflicts = new ArrayList<>();
        List<Alias> aliases = new ArrayList<>();
        groups.forEach((semantic, pairs) -> {
            List<Alias> values = pairs.stream().map(pair -> new Alias(pair.get(0).asText(),
                pair.get(1).isNull() ? null : pair.get(1).asText())).toList();
            aliases.addAll(values);
            if (new LinkedHashSet<>(values.stream().map(Alias::value).toList()).size() > 1) {
                conflicts.add(new Conflict(semantic, values));
                pairs.forEach(pair -> proposed.add(pair.deepCopy()));
            } else {
                ArrayNode pair = proposed.addArray().add(semantic);
                String value = values.get(0).value();
                if (value == null) pair.addNull(); else pair.add(value);
            }
        });
        // A dry run must not silently remove malformed legacy data; validation handles it.
        unrecognized.forEach(pair -> proposed.add(pair.deepCopy()));
        return new Analysis(proposed, List.copyOf(conflicts), List.copyOf(aliases));
    }

    public static String resolve(JsonNode content, String code, String fallback) {
        String semantic = RemoveSemantics.canonicalSemantic(code);
        if (semantic == null) return fallback;
        List<Alias> matches = analyze(content).aliases().stream()
            .filter(alias -> semantic.equals(RemoveSemantics.canonicalSemantic(alias.code()))).toList();
        // Preserve the existing canonical key's precedence in historical revisions.
        for (Alias alias : matches) {
            if (semantic.equals(alias.code().trim().toLowerCase(java.util.Locale.ROOT))) {
                return alias.value() == null || alias.value().isBlank() ? fallback : alias.value().trim();
            }
        }
        List<String> values = matches.stream().map(Alias::value).distinct().toList();
        if (values.size() != 1 || values.get(0) == null || values.get(0).isBlank()) return fallback;
        return values.get(0).trim();
    }
}
