package com.restaurant.system.order.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.order.dto.CreateOrderItemOptionRequest;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.IdempotentOrderSubmitRequest;
import com.restaurant.system.order.service.OrderSubmissionHashService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderSubmissionHashServiceImpl implements OrderSubmissionHashService {

    private final ObjectMapper objectMapper;

    public OrderSubmissionHashServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String hash(IdempotentOrderSubmitRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("organization_id", request.organization_id);
        payload.put("store_id", request.store_id);
        payload.put("server_order_id", request.server_order_id);
        payload.put("order_type", normalize(request.order_type));
        payload.put("table_no", normalize(request.table_no));
        payload.put("pickup_no", normalize(request.pickup_no));
        payload.put("menu_revision", request.menu_revision);
        payload.put("expected_subtotal_amount", request.expected_subtotal_amount == null
            ? null
            : request.expected_subtotal_amount.stripTrailingZeros().toPlainString());
        payload.put("items", request.items.stream().map(this::itemPayload).toList());
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(payload);
            return hex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash order submission payload", exception);
        }
    }

    private Map<String, Object> itemPayload(CreateOrderItemRequest item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("menu_item_id", item.menu_item_id);
        payload.put("quantity", item.quantity);
        payload.put("combo_group_no", item.combo_group_no);
        payload.put("combo_role", normalize(item.combo_role));
        payload.put("notes", normalize(item.notes));
        List<CreateOrderItemOptionRequest> options = item.options == null ? List.of() : item.options;
        payload.put("options", options.stream().map(option -> Map.of(
            "option_id", option.option_id,
            "quantity", option.quantity
        )).toList());
        return payload;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
