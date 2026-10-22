package com.restaurant.system.integration.ubereats.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.integration.ubereats.entity.UberEatsStoreMapping;
import jakarta.annotation.PostConstruct;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Explicit Store/root-scoped Kitchen Mirror defaults; never infer a selection from a name. */
@Component
public class UberEatsMirrorItemDefaults {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);

    @Value("${UBER_EATS_MIRROR_ITEM_DEFAULTS:[]}")
    public String configuration = "[]";

    public record Rule(String environment, Long localStoreId, Long organizationId,
                       String uberStoreId, String uberItemId, String localSku,
                       String optionGroup, String optionCode) {}

    @PostConstruct
    public void validate() {
        rules();
    }

    public Optional<Rule> find(UberEatsStoreMapping store, String uberItemId, String localSku) {
        return forRoot(store, uberItemId).filter(rule -> Objects.equals(rule.localSku(), localSku));
    }

    public Optional<Rule> forRoot(UberEatsStoreMapping store, String uberItemId) {
        return rules().stream().filter(rule -> Objects.equals(rule.environment(), store.environment)
                && Objects.equals(rule.localStoreId(), store.storeId)
                && Objects.equals(rule.organizationId(), store.organizationId)
                && Objects.equals(rule.uberStoreId(), store.uberStoreId)
                && Objects.equals(rule.uberItemId(), uberItemId)).findFirst();
    }

    private List<Rule> rules() {
        try {
            var node = JSON.readTree(configuration);
            if (node == null || !node.isArray()) throw new IllegalArgumentException();
            List<Rule> result = new ArrayList<>();
            Set<List<Object>> scopes = new HashSet<>();
            for (var entry : node) {
                if (!entry.isObject() || !entry.path("localStoreId").isIntegralNumber()
                        || !entry.path("localStoreId").canConvertToLong()
                        || !entry.path("organizationId").isIntegralNumber()
                        || !entry.path("organizationId").canConvertToLong()) throw new IllegalArgumentException();
                for (String field : List.of("environment", "uberStoreId", "uberItemId", "localSku", "optionGroup", "optionCode"))
                    if (!entry.path(field).isTextual()) throw new IllegalArgumentException();
                Rule rule = JSON.treeToValue(entry, Rule.class);
                if (rule == null || !Set.of("sandbox", "production").contains(rule.environment())
                        || rule.localStoreId() == null || rule.localStoreId() <= 0
                        || rule.organizationId() == null || rule.organizationId() <= 0
                        || !text(rule.uberStoreId()) || !UUID.fromString(rule.uberStoreId()).toString().equals(rule.uberStoreId())
                        || !text(rule.uberItemId()) || !text(rule.localSku())
                        || !"SOUP_BASE".equals(rule.optionGroup()) || !text(rule.optionCode()))
                    throw new IllegalArgumentException();
                if (!scopes.add(List.of(rule.environment(), rule.localStoreId(), rule.organizationId(),
                        rule.uberStoreId(), rule.uberItemId())))
                    throw new IllegalArgumentException();
                result.add(rule);
            }
            return List.copyOf(result);
        } catch (Exception error) {
            // Configuration can contain private deployment metadata; never echo parser input.
            throw new IllegalStateException("UBER_EATS_MIRROR_ITEM_DEFAULTS must be a strict array of unique exact Store/root SOUP_BASE defaults");
        }
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank() && value.equals(value.strip()) && value.length() <= 255;
    }
}
