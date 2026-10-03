package com.restaurant.system.analytics.support;

import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SalesReportingTest {
    @Test void classificationUsesStableSkuForMixedLegacyCategoriesAndNeverNames() {
        assertThat(SalesReporting.group("DRINK", "shochu_fruit")).isEqualTo(SalesReporting.Group.ALCOHOL);
        assertThat(SalesReporting.group("DRINK", "coke")).isEqualTo(SalesReporting.Group.DRINK);
        assertThat(SalesReporting.group("SIDE", "cold_noodle_shredded_chicken")).isEqualTo(SalesReporting.Group.DRY_NOODLE);
        assertThat(SalesReporting.group("SOUP_NOODLE", "future_soup_sku")).isEqualTo(SalesReporting.Group.SOUP_NOODLE);
        assertThat(SalesReporting.group("FUTURE", "future_unknown_sku")).isEqualTo(SalesReporting.Group.OTHER);
    }

    @Test void onlySubmittedCompletedInStoreSalesAreEligible() {
        Order order = order("9.99");
        assertThat(SalesReporting.eligible(order)).isTrue();
        order.status = "draft";
        assertThat(SalesReporting.eligible(order)).isFalse();
        order.status = "completed"; order.submitted_at = null;
        assertThat(SalesReporting.eligible(order)).isFalse();
        order.submitted_at = LocalDateTime.now(); order.financial_mode = "EXTERNAL_PLATFORM";
        assertThat(SalesReporting.eligible(order)).isFalse();
        order.financial_mode = "IN_STORE"; order.external_source = "UBER_EATS";
        assertThat(SalesReporting.eligible(order)).isFalse();
    }

    @Test void allocatesActualOrderTotalIncludingDiscountAndOptionsWithExactCents() {
        Order order = order("10.00"); // actual charged total; no menu price dependency
        OrderItem first = item(1L, "4.00"); // frozen line includes option charges
        OrderItem second = item(2L, "4.00");
        OrderItem third = item(3L, "4.00");
        var lines = SalesReporting.allocate(List.of(order), List.of(third, first, second));
        assertThat(lines).extracting(SalesReporting.AllocatedLine::revenue)
            .containsExactly(new BigDecimal("3.33"), new BigDecimal("3.34"), new BigDecimal("3.33"));
        assertThat(lines.stream().map(SalesReporting.AllocatedLine::revenue).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("10.00");
    }

    @Test void unallocatableRevenueIsRetainedAsOtherRatherThanDropped() {
        var lines = SalesReporting.allocate(List.of(order("10.00")), List.of(item(1L, "0")));
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1).item()).isNull();
        assertThat(lines.get(1).revenue()).isEqualByComparingTo("10.00");
        assertThat(SalesReporting.allocate(List.of(order("3.00")), List.of()).get(0).revenue()).isEqualByComparingTo("3.00");
    }

    @Test void cancelledFrozenLineDoesNotParticipateInAllocation() {
        OrderItem soup = item(1L, "10.00");
        OrderItem drink = item(2L, "10.00");
        drink.status = "cancelled";
        var lines = SalesReporting.allocate(List.of(order("10.00")), List.of(soup, drink));
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).item()).isSameAs(soup);
        assertThat(lines.get(0).revenue()).isEqualByComparingTo("10.00");
        drink.status = "CANCELLED";
        assertThat(SalesReporting.eligibleItem(drink)).isFalse();
    }

    private Order order(String total) {
        Order order = new Order(); order.id = 1L; order.status = "completed"; order.submitted_at = LocalDateTime.now(); order.total_amount = new BigDecimal(total); return order;
    }
    private OrderItem item(long id, String amount) {
        OrderItem item = new OrderItem(); item.id = id; item.order_id = 1L; item.line_amount = new BigDecimal(amount); return item;
    }
}
