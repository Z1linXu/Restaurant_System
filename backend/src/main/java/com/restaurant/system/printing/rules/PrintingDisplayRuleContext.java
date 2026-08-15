package com.restaurant.system.printing.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.restaurant.system.owner.profile.StoreProfileCanonicalJson;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public record PrintingDisplayRuleContext(
    Long revisionId,
    Integer revisionNumber,
    String fingerprintSha256,
    JsonNode content
) {

    public static PrintingDisplayRuleContext defaultContext() {
        JsonNode content = StoreProfileCanonicalJson.parse(PrintingDisplayRuleDefaults.DEFAULT_CONTENT_JSON);
        return new PrintingDisplayRuleContext(null, 1, PrintingDisplayRuleDefaults.DEFAULT_FINGERPRINT, content);
    }

    public static PrintingDisplayRuleContext fromRevision(PrintingDisplayRuleRevision revision) {
        if (revision == null || revision.content_json == null || revision.content_json.isBlank()) {
            return defaultContext();
        }
        return new PrintingDisplayRuleContext(
            revision.id,
            revision.revision_number,
            revision.fingerprint_sha256,
            StoreProfileCanonicalJson.parse(revision.content_json)
        );
    }

    public String resolveItemAlias(String outputType, String itemSku, String fallback) {
        String normalizedOutput = normalizeOutput(outputType);
        String normalizedSku = stable(itemSku);
        if (normalizedOutput == null || normalizedSku == null) {
            return fallback;
        }
        for (JsonNode alias : content.path("item_aliases")) {
            if (normalizedSku.equals(stable(alias.path("item_sku").asText(null)))) {
                String value = text(alias.path("outputs").path(normalizedOutput));
                return value == null ? fallback : value;
            }
        }
        return fallback;
    }

    public String resolveDictionaryOutput(
        String dictionaryName,
        String outputType,
        String optionCode,
        String optionZh,
        String optionEn,
        String fallback
    ) {
        JsonNode entry = findDictionaryEntry(dictionaryName, optionCode, optionZh, optionEn);
        if (entry == null) {
            return fallback;
        }
        String normalizedOutput = normalizeOutput(outputType);
        String direct = text(entry.path("outputs").path(normalizedOutput));
        if (direct != null) {
            return direct;
        }
        String zh = text(entry.path("outputs").path(normalizedOutput + "_ZH"));
        if (zh != null) {
            return zh;
        }
        return fallback;
    }

    public String resolveDictionaryOutputKey(
        String dictionaryName,
        String outputKey,
        String optionCode,
        String optionZh,
        String optionEn,
        String fallback
    ) {
        JsonNode entry = findDictionaryEntry(dictionaryName, optionCode, optionZh, optionEn);
        if (entry == null) {
            return fallback;
        }
        String value = text(entry.path("outputs").path(outputKey));
        return value == null ? fallback : value;
    }

    public String resolveModifierToken(String dictionaryName, String semanticCode, String fallback) {
        String normalizedCode = stable(semanticCode);
        if (normalizedCode == null) {
            return fallback;
        }
        for (JsonNode entry : content.path("dictionaries").path(dictionaryName)) {
            if (entry.isArray() && entry.size() >= 2 && normalizedCode.equals(stable(entry.get(0).asText(null)))) {
                String value = text(entry.get(1));
                return value == null ? fallback : value;
            }
        }
        return fallback;
    }

    public String resolveSemanticCode(String dictionaryName, String optionCode, String optionZh, String optionEn) {
        JsonNode entry = findDictionaryEntry(dictionaryName, optionCode, optionZh, optionEn);
        return entry == null ? null : text(entry.path("semantic_code"));
    }

    public boolean shouldOmit(String itemSku, String dictionaryName, String semanticCode) {
        String normalizedDictionary = stable(dictionaryName);
        String normalizedSemantic = stable(semanticCode);
        if (normalizedDictionary == null || normalizedSemantic == null) {
            return false;
        }
        for (JsonNode override : content.path("conditional_overrides")) {
            if (!override.path("omit").asBoolean(false)) {
                continue;
            }
            JsonNode condition = override.path("condition");
            if (!normalizedDictionary.equals(stable(condition.path("dictionary").asText(null)))) {
                continue;
            }
            if (!normalizedSemantic.equals(stable(condition.path("semantic_code").asText(null)))) {
                continue;
            }
            if (matchesItemCondition(condition.path("item_sku"), itemSku)) {
                return true;
            }
        }
        return false;
    }

    public String formatting(String key, String fallback) {
        String value = text(content.path("formatting").path(key));
        return value == null ? fallback : value;
    }

    public String activeFingerprintOrDefault() {
        return fingerprintSha256 == null ? PrintingDisplayRuleDefaults.DEFAULT_FINGERPRINT : fingerprintSha256;
    }

    private JsonNode findDictionaryEntry(String dictionaryName, String optionCode, String optionZh, String optionEn) {
        JsonNode entries = content.path("dictionaries").path(dictionaryName);
        if (!entries.isArray()) {
            return null;
        }
        String normalizedCode = stable(stripRemovePrefix(optionCode));
        String normalizedZh = stable(optionZh);
        String normalizedEn = stable(optionEn);
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                continue;
            }
            if (matchesAny(entry.path("match_codes"), normalizedCode)
                || matchesAny(entry.path("match_zh"), normalizedZh)
                || matchesAny(entry.path("match_en"), normalizedEn)) {
                return entry;
            }
        }
        return null;
    }

    private boolean matchesAny(JsonNode array, String candidate) {
        if (candidate == null || !array.isArray()) {
            return false;
        }
        Iterator<JsonNode> iterator = array.elements();
        while (iterator.hasNext()) {
            if (candidate.equals(stable(iterator.next().asText(null)))) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesItemCondition(JsonNode itemCondition, String itemSku) {
        String normalizedSku = stable(itemSku);
        if (normalizedSku == null || itemCondition == null || itemCondition.isMissingNode() || itemCondition.isNull()) {
            return false;
        }
        if (itemCondition.isArray()) {
            for (JsonNode value : itemCondition) {
                if (normalizedSku.equals(stable(value.asText(null)))) {
                    return true;
                }
            }
            return false;
        }
        return normalizedSku.equals(stable(itemCondition.asText(null)));
    }

    private String normalizeOutput(String outputType) {
        String normalized = stable(outputType);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "GRAB", "FRONTDESK_RECEIPT", "HOT_KITCHEN" -> normalized;
            default -> null;
        };
    }

    private String stripRemovePrefix(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.toLowerCase(Locale.ROOT).startsWith("remove_")
            ? trimmed.substring("remove_".length())
            : trimmed;
    }

    private String text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            return null;
        }
        String value = node.asText();
        return value == null ? null : value.trim();
    }

    private String stable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public boolean sameContentAsDefault() {
        String canonical = StoreProfileCanonicalJson.canonicalize(content);
        return Objects.equals(StoreProfileCanonicalJson.sha256(canonical), PrintingDisplayRuleDefaults.DEFAULT_FINGERPRINT);
    }

    public Map<String, Object> revisionMetadata() {
        return Map.of(
            "revision_id", revisionId == null ? "LEGACY_DEFAULT" : revisionId,
            "revision_number", revisionNumber == null ? 1 : revisionNumber,
            "fingerprint_sha256", activeFingerprintOrDefault()
        );
    }
}
