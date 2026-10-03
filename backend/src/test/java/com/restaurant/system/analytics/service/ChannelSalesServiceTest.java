package com.restaurant.system.analytics.service;

import com.restaurant.system.analytics.dto.ChannelSalesSummary;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChannelSalesServiceTest {
    final LocalDate date = LocalDate.of(2026, 10, 3);
    final OrderRepository orders = mock(OrderRepository.class);
    final OrderItemRepository items = mock(OrderItemRepository.class);
    final MenuItemRepository menu = mock(MenuItemRepository.class);
    final UberSalesSource uber = mock(UberSalesSource.class);
    final ChannelSalesService service = new ChannelSalesService(orders, items, menu, mock(MenuCategoryRepository.class), uber);

    @BeforeEach void setup() { when(uber.load(anyList(), any(), any())).thenReturn(List.of()); }

    @Test void combinesActualChannelAmountsWithoutCountingMirrorLocalPrices() {
        Order pos = pos(1L, "10.00");
        Order mirror = pos(2L, "999.00"); mirror.external_source = "UBER_EATS"; mirror.financial_mode = "EXTERNAL_PLATFORM";
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(pos, mirror));
        when(items.findAllByOrderIds(List.of(1L))).thenReturn(List.of(item(1L, "traditional_beef_noodle", "SOUP_NOODLE", 1, "10.00")));
        var external = sale("one", 1999L, List.of(uberItem("traditional_beef_noodle", "SOUP_NOODLE", 2, 1999L)));
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(external, external));
        var result = build();
        assertThat(result.totals.in_store.revenue).isEqualByComparingTo("10.00");
        assertThat(result.totals.uber_eats.revenue).isEqualByComparingTo("19.99");
        assertThat(result.totals.total.revenue).isEqualByComparingTo("29.99");
        assertThat(result.totals.total.order_count).isEqualTo(2);
        assertThat(group(result, "SOUP_NOODLE").total.quantity).isEqualTo(3);
        assertThat(group(result, "SOUP_NOODLE").in_store.quantity).isEqualTo(1);
        assertThat(group(result, "SOUP_NOODLE").uber_eats.quantity).isEqualTo(2);
        assertThat(group(result, "SOUP_NOODLE").total.revenue).isEqualByComparingTo("29.99");
        assertThat(result.items).hasSize(1);
        verify(items).findAllByOrderIds(List.of(1L));
    }

    @Test void allThreeNoodleCategoriesReceiveBothChannelsAndExactDonutPercentages() {
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(pos(1L, "30.00")));
        when(items.findAllByOrderIds(List.of(1L))).thenReturn(List.of(item(1L, "traditional_beef_noodle", "SOUP_NOODLE", 1, "10"),
            item(2L, "dan_dan_noodle", "DRY_NOODLE", 1, "10"), item(3L, "beef_chow_mein", "FRIED_NOODLE", 1, "10")));
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(sale("mixed", 6000L, List.of(
            uberItem("traditional_beef_noodle", "SOUP_NOODLE", 2, 2000L), uberItem("dan_dan_noodle", "DRY_NOODLE", 2, 2000L), uberItem("beef_chow_mein", "FRIED_NOODLE", 2, 2000L)))));
        MenuItem chow = new MenuItem(); chow.sku = "beef_chow_mein"; when(menu.findActiveByStoreId(1L)).thenReturn(List.of(chow));
        var result = build();
        for (String group : List.of("SOUP_NOODLE", "DRY_NOODLE", "FRIED_NOODLE")) {
            assertThat(group(result, group).total.quantity).isEqualTo(3);
            assertThat(group(result, group).total.revenue).isEqualByComparingTo("30.00");
        }
        assertThat(result.noodle_sales).hasSize(3);
        assertThat(result.categories.stream().map(row -> row.total.percentage).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        assertThat(result.categories.stream().map(row -> row.in_store.percentage).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        assertThat(result.categories.stream().map(row -> row.uber_eats.percentage).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }

    @Test void trendUsesPosSubmissionAndUberPlacementOnly() {
        Order pos = pos(1L, "10.00"); pos.completed_at = date.atTime(22, 0);
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(pos));
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(sale("placed-1815", 1999L, List.of(uberItem("coke", "DRINK", 1, 1999L)))));
        var result = build();
        assertThat(result.trend).hasSize(13);
        assertThat(result.trend.get(0).label).isEqualTo("10:00");
        assertThat(result.trend.get(12).label).isEqualTo("22:00");
        assertThat(result.trend.get(8).total.revenue).isEqualByComparingTo("29.99");
        assertThat(result.trend.get(12).total.revenue).isZero();
    }

    @Test void unknownFinancialAmountsRetainQuantityButDoNotBecomeZeroOrMenuPrice() {
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(sale("unknown", null, List.of(uberItem("traditional_beef_noodle", "SOUP_NOODLE", 3, null)))));
        var result = build();
        assertThat(result.totals.uber_eats.revenue).isNull();
        assertThat(result.totals.total.revenue).isNull();
        assertThat(result.totals.total.unknown_amount_count).isEqualTo(1);
        assertThat(group(result, "SOUP_NOODLE").total.quantity).isEqualTo(3);
        assertThat(group(result, "SOUP_NOODLE").uber_eats.revenue).isNull();
        assertThat(group(result, "SOUP_NOODLE").total.percentage).isNull();
    }

    @Test void knownOrderWithMissingOrUnreconciledLinesDoesNotInventCategoryAmounts() {
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(sale("missing-line", 1999L, List.of(uberItem("traditional_beef_noodle", "SOUP_NOODLE", 1, null)))));
        var result = build();
        assertThat(result.totals.total.revenue).isEqualByComparingTo("19.99");
        assertThat(group(result, "SOUP_NOODLE").total.revenue).isNull();
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(sale("bad-sum", 1999L, List.of(uberItem("traditional_beef_noodle", "SOUP_NOODLE", 1, 1000L)))));
        assertThat(group(build(), "SOUP_NOODLE").total.revenue).isNull();
    }

    @Test void rawFallbackRetainsActualAmountInOtherAndCurrencyMismatchIsUnknown() {
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(sale("raw", 500L, List.of(uberItem(null, "RAW_UBER_FALLBACK", 2, 500L)))));
        assertThat(group(build(), "OTHER").total.revenue).isEqualByComparingTo("5.00");
        assertThat(group(build(), "OTHER").total.quantity).isEqualTo(2);
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(new UberSalesSource.Sale(1L, "usd", date.atTime(18, 15), "USD", 500L, List.of(uberItem("coke", "DRINK", 2, 500L)))));
        assertThat(build().totals.total.revenue).isNull();
    }

    @Test void outsideHoursStillCountInTotalsAndDailyWeekMonth() {
        var early = new UberSalesSource.Sale(1L, "early", date.atTime(9, 0), "CAD", 500L, List.of(uberItem("coke", "DRINK", 1, 500L)));
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(early));
        assertThat(build().totals.total.revenue).isEqualByComparingTo("5.00");
        assertThat(build().trend.stream().map(row -> row.total.revenue).reduce(BigDecimal.ZERO, BigDecimal::add)).isZero();
        var daily = service.build(List.of(1L), date.withDayOfMonth(1), date.withDayOfMonth(31), false);
        assertThat(daily.trend).hasSize(31);
        assertThat(daily.trend.get(2).total.revenue).isEqualByComparingTo("5.00");
    }

    @Test void rejectsForeignStoreRowsAndOutOfWindowRowsFromAdapter() {
        var foreign = new UberSalesSource.Sale(2L, "foreign", date.atTime(18, 15), "CAD", 9000L, List.of());
        var old = new UberSalesSource.Sale(1L, "old", date.minusDays(1).atTime(18, 15), "CAD", 9000L, List.of());
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(foreign, old));
        assertThat(build().totals.total.order_count).isZero();
    }

    private ChannelSalesSummary build() { return service.build(List.of(1L), date, date, true); }
    private ChannelSalesSummary.Category group(ChannelSalesSummary summary, String group) { return summary.categories.stream().filter(row -> row.reporting_group.equals(group)).findFirst().orElseThrow(); }
    private Order pos(Long id, String total) { Order order = new Order(); order.id = id; order.store_id = 1L; order.status = "completed"; order.submitted_at = date.atTime(18, 10); order.total_amount = new BigDecimal(total); return order; }
    private OrderItem item(Long id, String sku, String group, int qty, String amount) { OrderItem item = new OrderItem(); item.id = id; item.order_id = 1L; item.menu_item_id = id; item.item_sku_snapshot = sku; item.category_code_snapshot = group; item.quantity = qty; item.line_amount = new BigDecimal(amount); return item; }
    private UberSalesSource.Sale sale(String id, Long minor, List<UberSalesSource.Item> lines) { return new UberSalesSource.Sale(1L, id, date.atTime(18, 15), "CAD", minor, lines); }
    private UberSalesSource.Item uberItem(String sku, String group, int qty, Long minor) { return new UberSalesSource.Item("stable-" + sku, sku, group, "Frozen name", qty, minor); }
}
