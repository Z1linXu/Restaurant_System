package com.restaurant.system.owner.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class StoreProfileCanonicalJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private StoreProfileCanonicalJson() {
    }

    public static JsonNode parse(String contentJson) {
        try {
            return OBJECT_MAPPER.readTree(contentJson);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Profile JSON must be valid", exception);
        }
    }

    public static String canonicalize(String contentJson) {
        return canonicalize(parse(contentJson));
    }

    public static String canonicalize(JsonNode node) {
        try {
            return OBJECT_MAPPER.writeValueAsString(sortNode(node));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Profile JSON cannot be canonicalized", exception);
        }
    }

    public static String sha256Canonical(String contentJson) {
        return sha256(canonicalize(contentJson));
    }

    public static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                hex.append(String.format("%02x", valueByte));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static JsonNode sortNode(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode sortedArray = OBJECT_MAPPER.createArrayNode();
            for (JsonNode child : node) {
                sortedArray.add(sortNode(child));
            }
            return sortedArray;
        }
        ObjectNode sortedObject = OBJECT_MAPPER.createObjectNode();
        List<Map.Entry<String, JsonNode>> fields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
        while (iterator.hasNext()) {
            fields.add(iterator.next());
        }
        fields.stream()
            .sorted(Comparator.comparing(Map.Entry::getKey))
            .forEach(entry -> sortedObject.set(entry.getKey(), sortNode(entry.getValue())));
        return sortedObject;
    }
}
