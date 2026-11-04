package com.restaurant.system.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Store;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SalesPeriodReportServiceTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final UberSalesSource uber = mock(UberSalesSource.class);
    private final ChannelSalesService channels = new ChannelSalesService(orders, items,
            mock(MenuItemRepository.class), mock(MenuCategoryRepository.class), uber);
    private final SalesPeriodReportService reports = new SalesPeriodReportService(channels, orders);
    private final LocalDate date = LocalDate.of(2026, 11, 3);

    @Test void dailyAndMonthlyReuseChannelAmountsIdentitiesAndPendingWithoutCountingOptionsAgain() {
        Order current = pos(1, date, "12.00", "1.00");
        Order otherDay = pos(2, date.plusDays(1), "5.00", "0.00");
        Order oldMonth = pos(3, date.minusMonths(1), "99.00", "0.00");
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(current, otherDay, oldMonth));
        OrderItem noodle = item(11, 1, "traditional_beef_noodle", "Frozen noodle", 2, "12.00");
        // line_amount includes any selected option charge. No option repository is read or re-added.
        when(items.findAllByOrderIds(List.of(1L))).thenReturn(List.of(noodle));
        when(items.findAllByOrderIds(List.of(1L, 2L))).thenReturn(List.of(noodle, item(12, 2, "coke", "Frozen drink", 1, "5.00")));
        var unknown = new UberSalesSource.Sale(1L, "synthetic-pending", date.atTime(12, 0), "CAD", null,
                List.of(new UberSalesSource.Item("stable-uber-root", "coke", "DRINK", "Frozen Uber drink", 3, null)), "FINANCIAL_EVIDENCE_MISSING");
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(unknown));

        var day = reports.build(store(), "day", date);
        assertThat(day.start_date()).isEqualTo(date);
        assertThat(day.end_date()).isEqualTo(date);
        assertThat(day.channel_sales().totals.total.order_count).isEqualTo(2);
        assertThat(day.channel_sales().totals.in_store.revenue).isEqualByComparingTo("12.00");
        assertThat(day.channel_sales().totals.total.revenue).isNull();
        assertThat(day.pending().order_count()).isEqualTo(1);
        assertThat(day.pending().item_line_count()).isEqualTo(1);
        assertThat(day.pending().amount()).isNull();
        assertThat(day.pending().amount_status()).isEqualTo("UNKNOWN");
        assertThat(day.adjustments().in_store_discount()).isEqualByComparingTo("1.00");
        assertThat(day.adjustments().tax()).isNull();
        assertThat(day.adjustments().refunds()).isNull();
        assertThat(day.channel_sales().items).anySatisfy(row -> {
            assertThat(row.item_key).isEqualTo("SKU_traditional_beef_noodle");
            assertThat(row.item_name).isEqualTo("Frozen noodle");
            assertThat(row.in_store.quantity).isEqualTo(2);
            assertThat(row.in_store.revenue).isEqualByComparingTo("12.00");
        });

        var month = reports.build(store(), "month", date);
        assertThat(month.start_date()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(month.end_date()).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(month.channel_sales().daily).hasSize(30);
        assertThat(month.channel_sales().totals.in_store.revenue).isEqualByComparingTo("17.00");
        assertThat(month.channel_sales().totals.total.order_count).isEqualTo(3);
        assertThat(month.original_transactions_required()).isTrue();
        assertThat(month.projection_mode()).isEqualTo("LIVE_RECOMPUTED_NOT_ARCHIVE");
    }

    @Test void recomputationIsIdempotentAndReflectsLateEventsCorrectionsAndRevocation() throws Exception {
        when(orders.findAllByStoreId(1L)).thenReturn(List.of());
        var json = new ObjectMapper().findAndRegisterModules();
        var pending = external(null);
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(pending));
        String before = json.writeValueAsString(reports.build(store(), "month", date));
        assertThat(json.writeValueAsString(reports.build(store(), "month", date))).isEqualTo(before);

        // The existing adapter represents the latest valid APPLY; no journal or snapshot is modified here.
        when(uber.load(anyList(), any(), any())).thenReturn(List.of(external(1000L)));
        var corrected = reports.build(store(), "month", date);
        assertThat(corrected.channel_sales().totals.total.revenue).isEqualByComparingTo("10.00");
        assertThat(corrected.pending().order_count()).isZero();
        assertThat(corrected.pending().amount()).isEqualByComparingTo("0.00");
        assertThat(json.writeValueAsString(reports.build(store(), "month", date)))
                .isEqualTo(json.writeValueAsString(corrected));

        Order late = pos(4, date, "7.00", null);
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(late));
        when(items.findAllByOrderIds(List.of(4L))).thenReturn(List.of(item(14, 4, "coke", "Frozen drink", 1, "7.00")));
        var afterLate = reports.build(store(), "month", date);
        assertThat(afterLate.channel_sales().totals.total.revenue).isEqualByComparingTo("17.00");
        assertThat(afterLate.adjustments().in_store_discount()).isNull();
        assertThat(afterLate.adjustments().in_store_discount_status()).isEqualTo("UNKNOWN");

        when(uber.load(anyList(), any(), any())).thenReturn(List.of(pending));
        var revoked = reports.build(store(), "month", date);
        assertThat(revoked.channel_sales().totals.total.revenue).isNull();
        assertThat(revoked.channel_sales().totals.total.known_revenue).isEqualByComparingTo("7.00");
        assertThat(revoked.pending().order_count()).isEqualTo(1);
        assertThat(late.total_amount).isEqualByComparingTo("7.00");
        verify(orders, never()).save(any());
    }

    @Test void monthBoundariesFollowCalendarIncludingLeapYearAndKeepStoreTimezone() {
        when(orders.findAllByStoreId(1L)).thenReturn(List.of());
        when(uber.load(anyList(), any(), any())).thenReturn(List.of());
        var result = reports.build(store(), "month", LocalDate.of(2024, 2, 29));
        assertThat(result.start_date()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(result.end_date()).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(result.channel_sales().daily).hasSize(29);
        assertThat(result.business_timezone()).isEqualTo("America/Toronto");
        verify(uber).load(List.of(1L), LocalDate.of(2024, 2, 1).atStartOfDay(), LocalDate.of(2024, 3, 1).atStartOfDay());
    }

    @Test void invalidPeriodAndTimezoneFailBeforeAnyFinancialRead() {
        assertThatThrownBy(() -> reports.build(store(), "year", date)).isInstanceOf(BusinessException.class);
        Store bad = store(); bad.timezone = "not/a-zone";
        assertThatThrownBy(() -> reports.build(bad, "day", date)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(orders, items, uber);
    }

    private Store store() { Store s = new Store(); s.id = 1L; s.timezone = "America/Toronto"; return s; }
    private Order pos(long id, LocalDate day, String total, String discount) {
        Order o = new Order(); o.id = id; o.store_id = 1L; o.status = "completed";
        o.submitted_at = day.atTime(12, 0); o.total_amount = new BigDecimal(total);
        o.discount_amount = discount == null ? null : new BigDecimal(discount); return o;
    }
    private OrderItem item(long id, long order, String sku, String name, int quantity, String amount) {
        OrderItem i = new OrderItem(); i.id = id; i.order_id = order; i.menu_item_id = id;
        i.item_sku_snapshot = sku; i.item_name_snapshot_en = name; i.quantity = quantity;
        i.line_amount = new BigDecimal(amount); return i;
    }
    private UberSalesSource.Sale external(Long minor) {
        return new UberSalesSource.Sale(1L, "synthetic-uber", date.atTime(12, 0), "CAD", minor,
                List.of(new UberSalesSource.Item("root", "coke", "DRINK", "Frozen drink", 1, minor)),
                minor == null ? "FINANCIAL_EVIDENCE_MISSING" : null);
    }
}
