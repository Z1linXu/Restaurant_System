package com.restaurant.system.owner.profile;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StoreProfileMaterializationDryRunValidator {

    public StoreProfileMaterializationDryRunResult validate(
        String profileContentJson,
        List<StoreProfileArtifactInput> artifacts
    ) {
        List<String> issues = new ArrayList<>();
        Map<String, JsonNode> artifactsByCode = new LinkedHashMap<>();
        for (StoreProfileArtifactInput artifact : artifacts == null ? List.<StoreProfileArtifactInput>of() : artifacts) {
            try {
                JsonNode content = StoreProfileCanonicalJson.parse(artifact.contentJson());
                scanNoRuntimeIdentity(content, artifact.artifactCode(), issues);
                artifactsByCode.put(artifact.artifactCode(), content);
            } catch (IllegalArgumentException exception) {
                issues.add("INVALID_ARTIFACT_JSON:" + artifact.artifactCode());
            }
        }

        try {
            JsonNode profile = StoreProfileCanonicalJson.parse(profileContentJson);
            scanNoRuntimeIdentity(profile, "PROFILE_CONTENT", issues);
            validateProfileIndependence(profile, issues);
        } catch (IllegalArgumentException exception) {
            issues.add("INVALID_PROFILE_JSON");
        }

        MaterializationCounts counts = validateArtifacts(artifactsByCode, issues);
        return new StoreProfileMaterializationDryRunResult(issues.isEmpty(), counts, List.copyOf(issues));
    }

    private MaterializationCounts validateArtifacts(Map<String, JsonNode> artifactsByCode, List<String> issues) {
        JsonNode stations = requiredArtifact(artifactsByCode, "STATION_TEMPLATE", issues);
        JsonNode menu = requiredArtifact(artifactsByCode, "MENU_TEMPLATE", issues);
        JsonNode pricing = requiredArtifact(artifactsByCode, "PRICING_POLICY", issues);
        JsonNode combo = requiredArtifact(artifactsByCode, "COMBO_CONFIGURATION", issues);
        JsonNode tables = requiredArtifact(artifactsByCode, "TABLE_TEMPLATE", issues);
        JsonNode printing = requiredArtifact(artifactsByCode, "PRINTING_TOPOLOGY", issues);
        JsonNode roles = requiredArtifact(artifactsByCode, "ROLE_ACCESS_DEFAULTS", issues);
        JsonNode hardware = requiredArtifact(artifactsByCode, "HARDWARE_REQUIREMENTS", issues);

        Set<String> stationRefs = collectRefs(array(stations, "stations", issues), "station_ref", "STATION", issues);
        MenuGraphCounts menuCounts = validateMenu(menu, stationRefs, issues);
        int tableCount = collectRefs(array(tables, "tables", issues), "table_ref", "TABLE", issues).size();
        validatePricing(pricing, issues);
        int comboComponentCount = validateCombo(combo, menuCounts.itemRefs(), issues);
        PrintingCounts printingCounts = validatePrinting(printing, issues);
        int roleTemplateCount = collectRefs(array(roles, "staff_templates", issues), "staff_ref", "STAFF", issues).size();
        validateHardware(hardware, issues);

        return new MaterializationCounts(
            menuCounts.categoryCount(),
            menuCounts.itemCount(),
            menuCounts.optionCount(),
            menuCounts.parentOptionRelationshipCount(),
            tableCount,
            stationRefs.size(),
            printingCounts.printerCount(),
            printingCounts.assignmentCount(),
            comboComponentCount,
            roleTemplateCount
        );
    }

    private MenuGraphCounts validateMenu(JsonNode menu, Set<String> stationRefs, List<String> issues) {
        Set<String> categoryRefs = collectRefs(array(menu, "categories", issues), "category_ref", "CATEGORY", issues);
        Set<String> itemRefs = new LinkedHashSet<>();
        for (JsonNode item : array(menu, "items", issues)) {
            String itemRef = text(item, "item_ref");
            if (!addExact(itemRefs, itemRef)) {
                issues.add("MENU_ITEM_REF_INVALID_OR_DUPLICATE:" + itemRef);
            }
            if (!categoryRefs.contains(text(item, "category_ref"))) {
                issues.add("MENU_ITEM_CATEGORY_REF_MISSING:" + itemRef);
            }
            if (!stationRefs.contains(text(item, "station_ref"))) {
                issues.add("MENU_ITEM_STATION_REF_MISSING:" + itemRef);
            }
        }

        Set<String> optionRefs = new LinkedHashSet<>();
        Map<String, String> optionItemRefs = new LinkedHashMap<>();
        int parentRelationships = 0;
        for (JsonNode option : array(menu, "options", issues)) {
            String optionRef = text(option, "option_ref");
            String itemRef = text(option, "item_ref");
            if (!addExact(optionRefs, optionRef)) {
                issues.add("MENU_OPTION_REF_INVALID_OR_DUPLICATE:" + optionRef);
            }
            if (!itemRefs.contains(itemRef)) {
                issues.add("MENU_OPTION_ITEM_REF_MISSING:" + optionRef);
            }
            optionItemRefs.put(optionRef, itemRef);
        }
        for (JsonNode option : array(menu, "options", issues)) {
            String parentRef = text(option, "parent_option_ref");
            if (parentRef == null || parentRef.isBlank()) {
                continue;
            }
            parentRelationships++;
            String optionRef = text(option, "option_ref");
            if (!optionRefs.contains(parentRef)) {
                issues.add("MENU_PARENT_OPTION_REF_MISSING:" + optionRef);
            } else if (!safe(optionItemRefs.get(parentRef)).equals(optionItemRefs.get(optionRef))) {
                issues.add("MENU_PARENT_OPTION_CROSSES_ITEM:" + optionRef);
            }
        }
        return new MenuGraphCounts(categoryRefs.size(), itemRefs.size(), optionRefs.size(), parentRelationships, itemRefs);
    }

    private void validatePricing(JsonNode pricing, List<String> issues) {
        JsonNode policy = pricing == null ? null : pricing.path("store_pricing_policy");
        if (policy == null || !policy.isObject()) {
            issues.add("PRICING_POLICY_MISSING");
            return;
        }
        for (String field : List.of("size_small_delta", "size_regular_delta", "size_large_delta", "combo_delta")) {
            if (!isDecimal(policy.path(field).asText(null))) {
                issues.add("PRICING_POLICY_DECIMAL_INVALID:" + field);
            }
        }
    }

    private int validateCombo(JsonNode combo, Set<String> itemRefs, List<String> issues) {
        Set<String> componentRefs = collectRefs(array(combo, "components", issues), "component_ref", "COMBO_COMPONENT", issues);
        for (JsonNode component : array(combo, "components", issues)) {
            String group = text(component, "component_group");
            if (!Set.of("COMBO_EGG", "COMBO_SIDE").contains(group)) {
                issues.add("COMBO_COMPONENT_GROUP_INVALID:" + group);
            }
        }
        for (JsonNode itemRef : array(combo, "combo_allowed_item_refs", issues)) {
            String value = itemRef.asText(null);
            if (!itemRefs.contains(value)) {
                issues.add("COMBO_ALLOWED_ITEM_REF_MISSING:" + value);
            }
        }
        return componentRefs.size();
    }

    private PrintingCounts validatePrinting(JsonNode printing, List<String> issues) {
        Set<String> printerRefs = collectRefs(array(printing, "logical_printers", issues), "printer_ref", "PRINTER", issues);
        int assignments = 0;
        for (JsonNode assignment : array(printing, "assignments", issues)) {
            assignments++;
            String printerRef = text(assignment, "printer_ref");
            if (!printerRefs.contains(printerRef)) {
                issues.add("PRINT_ASSIGNMENT_PRINTER_REF_MISSING:" + printerRef);
            }
        }
        return new PrintingCounts(printerRefs.size(), assignments);
    }

    private void validateHardware(JsonNode hardware, List<String> issues) {
        if (hardware == null || !hardware.path("requirements").isArray()) {
            issues.add("HARDWARE_REQUIREMENTS_MISSING");
        }
    }

    private void validateProfileIndependence(JsonNode profile, List<String> issues) {
        JsonNode materialization = profile.path("materialization_contract");
        if (!materialization.path("uses_profile_local_refs").asBoolean(false)
            || !materialization.path("new_surrogate_ids_required").asBoolean(false)
            || materialization.path("source_store_db_ids_allowed").asBoolean(true)
            || !materialization.path("profile_store_independence").asBoolean(false)
            || materialization.path("materialized_store_updates_profile").asBoolean(true)) {
            issues.add("PROFILE_STORE_INDEPENDENCE_MISSING");
        }
    }

    private JsonNode requiredArtifact(Map<String, JsonNode> artifactsByCode, String artifactCode, List<String> issues) {
        JsonNode artifact = artifactsByCode.get(artifactCode);
        if (artifact == null) {
            issues.add("ARTIFACT_MISSING:" + artifactCode);
        }
        return artifact;
    }

    private List<JsonNode> array(JsonNode root, String field, List<String> issues) {
        if (root == null || !root.path(field).isArray()) {
            issues.add("ARRAY_MISSING:" + field);
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        root.path(field).forEach(values::add);
        return values;
    }

    private Set<String> collectRefs(List<JsonNode> nodes, String field, String label, List<String> issues) {
        Set<String> refs = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            String ref = text(node, field);
            if (!addExact(refs, ref)) {
                issues.add(label + "_REF_INVALID_OR_DUPLICATE:" + ref);
            }
        }
        return refs;
    }

    private boolean addExact(Set<String> refs, String value) {
        return StoreProfileIdentity.isExact(value) && refs.add(value);
    }

    private void scanNoRuntimeIdentity(JsonNode node, String path, List<String> issues) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                scanNoRuntimeIdentity(node.get(index), path + "[" + index + "]", issues);
            }
            return;
        }
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String normalized = key.toLowerCase();
            if ("id".equals(normalized)
                || normalized.endsWith("_id")
                || normalized.endsWith("_ids")
                || normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("credential")
                || normalized.contains("secret")
                || normalized.contains("endpoint")
                || normalized.equals("ip")
                || normalized.equals("ip_address")
                || normalized.equals("host")
                || normalized.equals("port")) {
                issues.add("RUNTIME_ID_OR_SECRET_FIELD_FORBIDDEN:" + path + "." + key);
            }
            scanNoRuntimeIdentity(entry.getValue(), path + "." + key, issues);
        });
    }

    private boolean isDecimal(String value) {
        if (value == null) {
            return false;
        }
        try {
            new BigDecimal(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).asText(null);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record MenuGraphCounts(
        int categoryCount,
        int itemCount,
        int optionCount,
        int parentOptionRelationshipCount,
        Set<String> itemRefs
    ) {
    }

    private record PrintingCounts(int printerCount, int assignmentCount) {
    }

    public record MaterializationCounts(
        int categoryCount,
        int itemCount,
        int optionCount,
        int parentOptionRelationshipCount,
        int tableCount,
        int stationCount,
        int logicalPrinterCount,
        int printerAssignmentCount,
        int comboComponentCount,
        int staffTemplateCount
    ) {
    }

    public record StoreProfileMaterializationDryRunResult(
        boolean valid,
        MaterializationCounts counts,
        List<String> issues
    ) {
    }
}
