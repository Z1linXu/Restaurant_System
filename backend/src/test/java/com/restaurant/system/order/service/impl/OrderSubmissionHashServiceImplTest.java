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

    @Test
    void menuRevisionIsDiagnosticButSnapshotPriceRemainsPartOfIdempotentContent() {
        IdempotentOrderSubmitRequest original = request(null);
        IdempotentOrderSubmitRequest newerRevision = request(null);
        newerRevision.menu_revision = 99L;
        IdempotentOrderSubmitRequest differentPrice = request(null);
        differentPrice.items.get(0).unit_price_snapshot = new BigDecimal("13.00");

        assertEquals(service.hash(original), service.hash(newerRevision));
        assertNotEquals(service.hash(original), service.hash(differentPrice));
        assertNotEquals(service.legacyHash(original), service.legacyHash(newerRevision));
    }

    private IdempotentOrderSubmitRequest request(String notes) {
        CreateOrderItemRequest item = new CreateOrderItemRequest();
        item.menu_item_id = 20L;
        item.quantity = 1;
        item.combo_role = "standalone";
        item.item_name_snapshot_zh = "牛肉面";
        item.item_name_snapshot_en = "Beef Noodle";
        item.unit_price_snapshot = new BigDecimal("12.50");
        item.category_code_snapshot = "SOUP_NOODLE";
        item.station_id_snapshot = 3L;
        item.item_sku_snapshot = "traditional_beef_noodle";
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
