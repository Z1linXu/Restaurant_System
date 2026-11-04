package com.restaurant.system.integration.ubereats;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.restaurant.system.integration.ubereats.dto.UberFinancialSnapshot;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UberPromotionFinancialTest {
    final ObjectMapper json=new ObjectMapper();
    ObjectNode order() throws Exception {
        return (ObjectNode)json.readTree("""
            {"id":"synthetic","store":{"id":"synthetic-store"},"placed_at":"2026-11-03T12:00:00Z",
            "cart":{"items":[{"id":"one","quantity":1,"price":{"total_price":{"amount":2000,"currency_code":"CAD"}}},
            {"id":"two","quantity":1,"price":{"total_price":{"amount":1000,"currency_code":"CAD"}}}]},
            "payment":{"charges":{"sub_total":{"amount":3000,"currency_code":"CAD"},"tax":{"amount":450,"currency_code":"CAD"},
            "total":{"amount":3450,"currency_code":"CAD"}}}}
            """);
    }
    void promo(ObjectNode order,long subtotal,long tax,long total) {
        ObjectNode charges=(ObjectNode)order.path("payment").path("charges");
        charges.set("sub_total_promo_applied",money(subtotal));charges.set("tax_promo_applied",money(tax));charges.set("total_promo_applied",money(total));
    }
    ObjectNode money(long value) {return json.createObjectNode().put("amount",value).put("currency_code","CAD");}
    ObjectNode promotion(String source,long value) {
        var p=json.createObjectNode().put("promo_type","FLATOFF").put("promo_discount_value",value);
        p.putArray("promo_funding_splits").addObject().put("funding_source",source).set("amount_paid",money(value));
        return p;
    }
    void promotions(ObjectNode order,ObjectNode... entries) {
        var list=((ObjectNode)order.path("payment")).putObject("promotions").putArray("promotions");
        for(var entry:entries)list.add(entry);
    }
    @Test void merchantPromoUsesAuthoritativeTotalWithoutInventingItemNetAmounts() throws Exception {
        var o=order();promo(o,2500,375,2875);promotions(o,promotion("organization",500));
        var s=UberFinancialSnapshot.capture(o);
        assertThat(s.version()).isEqualTo(3);assertThat(s.blocked_reason()).isNull();assertThat(s.revenue_minor()).isEqualTo(2875);
        assertThat(s.items()).allSatisfy(x->assertThat(x.revenue_minor()).isNull());
        assertThat(s.charges().path("_merchant_funded_minor").asLong()).isEqualTo(500);
    }
    @Test void platformFundingIsNotSubtractedFromMerchantRevenueTwice() throws Exception {
        var o=order();promotions(o,promotion("uber",500));
        var s=UberFinancialSnapshot.capture(o);
        assertThat(s.revenue_minor()).isEqualTo(3450);assertThat(s.charges().path("_uber_funded_minor").asLong()).isEqualTo(500);
    }
    @Test void mixedPromotionsValidateMerchantAndPlatformFundingSeparately() throws Exception {
        var o=order();promo(o,2500,375,2875);promotions(o,promotion("organization",500),promotion("uber",200));
        assertThat(UberFinancialSnapshot.capture(o).revenue_minor()).isEqualTo(2875);
        promo(o,2400,360,2760);
        assertThat(UberFinancialSnapshot.capture(o).blocked_reason()).isEqualTo("PROMO_FUNDING_MISMATCH");
    }
    @Test void zeroIsSupportedButUnknownFeesRefundsAndUnexpectedDetailsRemainBlocked() throws Exception {
        var free=order();promo(free,0,0,0);
        assertThat(UberFinancialSnapshot.capture(free).revenue_minor()).isZero();
        for(String field:List.of("refunds","accounting","unrecognized_money")) {
            var o=order();((ObjectNode)o.path("payment")).putObject(field).put("amount",123);
            assertThat(UberFinancialSnapshot.capture(o).revenue_minor()).isNull();
        }
        var fees=order();((ObjectNode)fees.path("payment").path("charges")).set("total_fee",money(100));
        assertThat(UberFinancialSnapshot.capture(fees).blocked_reason()).isEqualTo("FEES_REQUIRE_REVIEW");
        var currency=order();promo(currency,2500,375,2875);
        ((ObjectNode)currency.path("payment").path("charges").path("tax_promo_applied")).put("currency_code","USD");
        assertThat(UberFinancialSnapshot.capture(currency).revenue_minor()).isNull();
    }
    @Test void authoritativeTotalSurvivesComponentRoundingWithoutAdjustingSourceMoney() throws Exception {
        var o=order();promo(o,2500,375,2874);
        var result=UberFinancialSnapshot.capture(o);
        assertThat(result.blocked_reason()).isNull();assertThat(result.revenue_minor()).isEqualTo(2874);
        assertThat(result.charges().path("_component_delta_minor").asLong()).isEqualTo(-1);
        assertThat(result.charges().path("_component_reconciliation_status").asText()).isEqualTo("ROUNDING_REVIEW");
        assertThat(result.items()).allSatisfy(x->assertThat(x.revenue_minor()).isNull());
        ((ObjectNode)o.path("payment").path("charges")).remove("tax_promo_applied");
        assertThat(UberFinancialSnapshot.capture(o).revenue_minor()).isEqualTo(2874);
    }
    @Test void authoritativeTotalsDoNotDependOnItemAllocationButMaterialConflictsStillBlock() throws Exception {
        var o=order();promo(o,2500,375,2876);
        var s=UberFinancialSnapshot.capture(o);
        assertThat(s.revenue_minor()).isEqualTo(2876);assertThat(s.charges().path("_component_delta_minor").asLong()).isEqualTo(1);
        promo(o,2500,375,2877);
        assertThat(UberFinancialSnapshot.capture(o).blocked_reason()).isEqualTo("PROMO_TOTAL_RECONCILIATION_REQUIRED");
        promo(o,2500,375,2875);
        ((ObjectNode)o.path("cart").path("items").get(0)).remove("price");
        assertThat(UberFinancialSnapshot.capture(o).revenue_minor()).isEqualTo(2875);
        ((ObjectNode)o.path("cart")).remove("items");
        assertThat(UberFinancialSnapshot.capture(o).revenue_minor()).isEqualTo(2875);
        var huge=order();promo(huge,Long.MAX_VALUE,Long.MAX_VALUE,0);
        assertThatCode(()->UberFinancialSnapshot.capture(huge)).doesNotThrowAnyException();
        assertThat(UberFinancialSnapshot.capture(huge).revenue_minor()).isNull();
    }

    @Test void sixRetainedOrderTotalsPreserveEverySourceCentAndLeaveItemsUnconfirmed() throws Exception {
        var rows=json.readTree(getClass().getResourceAsStream("/uber/pending-20261103-sanitized.json"));long sum=0;int count=0;
        for(var row:rows)if(Set.of("U-e081b098","U-d40b43e1","U-284238ab","U-31281cd2","U-6f6dfe76","U-ae3be476").contains(row.path("audit_id").asText())) {
            var old=json.readValue(row.path("original_snapshot_json").asText(),UberFinancialSnapshot.class);
            var charges=json.readTree(row.path("source_charges_json").asText());
            var s=UberFinancialSnapshot.reconcileRetainedOrderTotal(old,charges);
            assertThat(s.version()).isEqualTo(3);assertThat(s.blocked_reason()).isNull();
            assertThat(s.revenue_minor()).isEqualTo(charges.path("total_promo_applied").path("amount").asLong());
            assertThat(s.charges().path("_component_delta_minor").asLong()).isIn(-1L,1L);
            assertThat(s.items()).allSatisfy(x->assertThat(x.revenue_minor()).isNull());
            assertThat(old.revenue_minor()).isNull();assertThat(old.version()).isEqualTo(1);
            sum+=s.revenue_minor();count++;
        }
        assertThat(count).isEqualTo(6);assertThat(sum).isEqualTo(31541);
    }

    @Test void fourteenRetainedSnapshotsYieldEightStrictCandidatesAndSixBlockedWithoutChangingOriginals() throws Exception {
        JsonNode rows=json.readTree(getClass().getResourceAsStream("/uber/pending-20261103-sanitized.json"));
        int allowed=0,blocked=0;
        for(JsonNode row:rows) {
            String original=row.path("original_snapshot_json").asText();
            var old=json.readValue(original,UberFinancialSnapshot.class);
            var candidate=UberFinancialSnapshot.reconcileRetained(old,json.readTree(row.path("source_charges_json").asText()));
            assertThat(old.revenue_minor()).isNull();assertThat(old.version()).isEqualTo(1);
            if(candidate.revenue_minor()!=null) {
                allowed++;assertThat(candidate.items().stream().mapToLong(x->x.revenue_minor()).sum()).isEqualTo(candidate.revenue_minor());
            } else {blocked++;assertThat(candidate.blocked_reason()).isEqualTo("PROMO_TOTAL_RECONCILIATION_REQUIRED");}
            assertThat(row.path("original_snapshot_json").asText()).isEqualTo(original);
        }
        assertThat(allowed).isEqualTo(8);assertThat(blocked).isEqualTo(6);
    }
}
