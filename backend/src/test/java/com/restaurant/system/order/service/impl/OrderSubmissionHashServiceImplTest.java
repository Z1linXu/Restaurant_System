package com.restaurant.system.order.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.order.dto.CreateOrderItemRequest;
import com.restaurant.system.order.dto.IdempotentOrderSubmitRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderSubmissionHashServiceImplTest {

    private final OrderSubmissionHashServiceImpl service = new OrderSubmissionHashServiceImpl(new ObjectMapper());

    @Test
    void producesStableHashAndIncludesBusinessPayload() {
        IdempotentOrderSubmitRequest request = request("less soup");

        assertEquals(service.hash(request), service.hash(request));
        assertNotEquals(service.hash(request), service.hash(request("extra soup")));
    }

    private IdempotentOrderSubmitRequest request(String notes) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.menu_item_id = 20L;
        item.quantity = 1;
        item.combo_role = "standalone";
        item.notes = notes;
        item.options = List.of();

        IdempotentOrderSubmitRequest request = new IdempotentOrderSubmitRequest();
        request.client_order_id = "client-order-1";
        request.organization_id = 7L;
        request.store_id = 1L;
        request.order_type = "dine_in";
        request.table_no = "T1";
        request.menu_revision = 4L;
        request.expected_subtotal_amount = new BigDecimal("12.50");
        request.items = List.of(item);
        return request;
    }
}
