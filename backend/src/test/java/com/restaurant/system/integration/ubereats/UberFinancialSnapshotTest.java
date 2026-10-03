package com.restaurant.system.integration.ubereats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.restaurant.system.integration.ubereats.dto.UberFinancialSnapshot;
import com.restaurant.system.integration.ubereats.service.UberEatsOrderNormalizer;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UberFinancialSnapshotTest {
    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode order() throws Exception {
        return (ObjectNode) json.readTree("""
            {"id":"order","store":{"id":"store"},"type":"DELIVERY_BY_UBER","placed_at":"2026-10-03T22:15:00Z",
            "cart":{"items":[
              {"id":"noodle","instance_id":"one","quantity":2,"price":{"total_price":{"amount":2599,"currency_code":"CAD"}},
               "selected_modifier_groups":[{"selected_items":[{"id":"egg","quantity":1,"price":{"total_price":{"amount":399,"currency_code":"CAD"}}}]}]},
              {"id":"drink","instance_id":"two","quantity":1,"price":{"total_price":{"amount":301,"currency_code":"CAD"}}}]},
            "payment":{"charges":{"sub_total":{"amount":2900,"currency_code":"CAD"},"tax":{"amount":435,"currency_code":"CAD"},"total":{"amount":3335,"currency_code":"CAD"}}},
            "eater":{"first_name":"Private","phone":"never-store"}}
            """);
    }

    @Test void freezesActualRootTotalsIncludingQuantityAndModifiersAndAllocatesActualTax() throws Exception {
        var snapshot = UberFinancialSnapshot.capture(order());
        assertThat(snapshot.revenue_minor()).isEqualTo(3335L);
        assertThat(snapshot.items()).extracting(UberFinancialSnapshot.Line::quantity).containsExactly(2, 1);
        assertThat(snapshot.items()).extracting(UberFinancialSnapshot.Line::revenue_minor).containsExactly(2989L, 346L);
        assertThat(snapshot.items().get(0).modifiers().get(0).price().path("total_price").path("amount").asLong()).isEqualTo(399);
        assertThat(json.writeValueAsString(snapshot)).doesNotContain("Private", "never-store", "eater");
        assertThat(new UberEatsOrderNormalizer().normalize(order()).item_financial_snapshot()).isEqualTo(snapshot);
    }

    @Test void absentPriceCurrencyOrFractionalAmountNeverBecomesLocalPriceOrZeroRevenue() throws Exception {
        for (String replacement : new String[]{"null", "{\"amount\":2599.5,\"currency_code\":\"CAD\"}", "{\"amount\":2599,\"currency_code\":\"USD\"}", "{\"amount\":-2599,\"currency_code\":\"CAD\"}"}) {
            var order = order();
            ((ObjectNode) order.path("cart").path("items").get(0).path("price")).set("total_price", json.readTree(replacement));
            var snapshot = UberFinancialSnapshot.capture(order);
            assertThat(snapshot.revenue_minor()).isNull();
            assertThat(snapshot.items()).allSatisfy(line -> assertThat(line.revenue_minor()).isNull());
            assertThat(snapshot.items().get(0).quantity()).isEqualTo(2);
        }
    }

    @Test void subtotalMismatchFeesPromotionsAndAccountingFailClosed() throws Exception {
        for (String field : new String[]{"sub_total", "total_fee", "promotions", "accounting", "tax_reporting"}) {
            var order = order();
            if (field.equals("sub_total")) ((ObjectNode) order.path("payment").path("charges").path(field)).put("amount", 2800);
            else if (field.equals("total_fee")) ((ObjectNode) order.path("payment").path("charges")).set(field, json.readTree("{\"amount\":100,\"currency_code\":\"CAD\"}"));
            else ((ObjectNode) order.path("payment")).set(field, json.readTree("{\"amount\":100}"));
            assertThat(UberFinancialSnapshot.capture(order).revenue_minor()).as(field).isNull();
        }
    }

    @Test void repeatedStableItemIdsAreDistinctLinesButDuplicateInstanceIsInvalid() throws Exception {
        var order = order();
        ((ObjectNode) order.path("cart").path("items").get(1)).put("id", "noodle");
        assertThat(UberFinancialSnapshot.capture(order).revenue_minor()).isEqualTo(3335L);
        ((ObjectNode) order.path("cart").path("items").get(1)).put("instance_id", "one");
        assertThat(UberFinancialSnapshot.capture(order).blocked_reason()).isEqualTo("DUPLICATE_ITEM_INSTANCE");
    }
}
