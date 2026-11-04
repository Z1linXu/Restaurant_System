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
    @Test void merchantPromoUsesPostPromotionTotalAndExactDerivedLineAllocation() throws Exception {
        var o=order();promo(o,2500,375,2875);promotions(o,promotion("organization",500));
        var s=UberFinancialSnapshot.capture(o);
        assertThat(s.version()).isEqualTo(2);assertThat(s.blocked_reason()).isNull();assertThat(s.revenue_minor()).isEqualTo(2875);
        assertThat(s.items().stream().mapToLong(x->x.revenue_minor()).sum()).isEqualTo(2875);
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
    @Test void missingPromoFieldsAndOneCentDifferencesNeverFallBackToGrossOrAdjustMoney() throws Exception {
        var o=order();promo(o,2500,375,2874);
        assertThat(UberFinancialSnapshot.capture(o).blocked_reason()).isEqualTo("PROMO_TOTAL_RECONCILIATION_REQUIRED");
        ((ObjectNode)o.path("payment").path("charges")).remove("tax_promo_applied");
        assertThat(UberFinancialSnapshot.capture(o).revenue_minor()).isNull();
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
