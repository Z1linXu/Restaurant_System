package com.restaurant.system.analytics.service;

import com.restaurant.system.analytics.entity.SalesDailySummary;
import com.restaurant.system.analytics.entity.SalesHourlySummary;
import com.restaurant.system.analytics.entity.MenuItemSalesSummary;
import com.restaurant.system.analytics.repository.*;
import com.restaurant.system.analytics.service.impl.AnalyticsAggregationServiceImpl;
import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class AnalyticsSubmissionTimeTest {
    @Test void rebuiltReportsUseSubmissionDateHourAndSameActualSalesAllocation() {
        StoreRepository stores = mock(StoreRepository.class);
        OrderRepository orders = mock(OrderRepository.class);
        OrderItemRepository items = mock(OrderItemRepository.class);
        SalesDailySummaryRepository daily = mock(SalesDailySummaryRepository.class);
        SalesHourlySummaryRepository hourly = mock(SalesHourlySummaryRepository.class);
        MenuItemSalesSummaryRepository itemSales = mock(MenuItemSalesSummaryRepository.class);
        MenuItemRepository menu = mock(MenuItemRepository.class);
        var service = new AnalyticsAggregationServiceImpl(stores, orders, items, mock(InventoryItemRepository.class),
            menu, daily, hourly, itemSales, mock(StorePerformanceSummaryRepository.class), mock(AnalyticsAlertRepository.class), mock(ChannelSalesService.class), mock(AnalyticsReadScope.class));
        Store store = new Store(); store.id = 1L; store.organization_id = 1L;
        when(stores.findById(1L)).thenReturn(Optional.of(store));
        LocalDate date = LocalDate.of(2026, 10, 2);
        Order sale = new Order(); sale.id = 1L; sale.store_id = 1L; sale.status = "completed";
        sale.submitted_at = date.atTime(18, 15); sale.completed_at = date.plusDays(1).atTime(0, 15);
        sale.subtotal_amount = new BigDecimal("20.00"); sale.total_amount = new BigDecimal("22.99");
        Order external = new Order(); external.id = 2L; external.status = "completed"; external.submitted_at = sale.submitted_at;
        external.financial_mode = "EXTERNAL_PLATFORM"; external.total_amount = new BigDecimal("500.00");
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(sale, external));
        OrderItem item = new OrderItem(); item.id = 1L; item.order_id = 1L; item.menu_item_id = 1L;
        item.quantity = 2; item.line_amount = new BigDecimal("20.00"); item.item_name_snapshot_en = "Frozen";
        OrderItem cancelled = new OrderItem(); cancelled.id = 2L; cancelled.order_id = 1L; cancelled.menu_item_id = 2L;
        cancelled.status = "cancelled"; cancelled.quantity = 3; cancelled.line_amount = new BigDecimal("30.00");
        cancelled.item_name_snapshot_en = "Cancelled drink";
        when(items.findAllByOrderIds(anyList())).thenReturn(List.of(item, cancelled));
        MenuItem soup = new MenuItem(); soup.id = 1L; soup.cost_per_item = new BigDecimal("2.00");
        when(menu.findAllById(List.of(1L))).thenReturn(List.of(soup));
        service.rebuildForDate(date, 1L);
        ArgumentCaptor<SalesDailySummary> day = ArgumentCaptor.forClass(SalesDailySummary.class);
        verify(daily).save(day.capture());
        assertThat(day.getValue().net_sales).isEqualByComparingTo("22.99");
        assertThat(day.getValue().completed_order_count).isEqualTo(1);
        assertThat(day.getValue().total_cost).isEqualByComparingTo("4.00");
        verify(menu).findAllById(List.of(1L));
        ArgumentCaptor<SalesHourlySummary> hours = ArgumentCaptor.forClass(SalesHourlySummary.class);
        verify(hourly, times(24)).save(hours.capture());
        assertThat(hours.getAllValues().get(18).sales_amount).isEqualByComparingTo("22.99");
        assertThat(hours.getAllValues().get(21).sales_amount).isZero();
        ArgumentCaptor<MenuItemSalesSummary> line = ArgumentCaptor.forClass(MenuItemSalesSummary.class);
        verify(itemSales).save(line.capture());
        assertThat(line.getValue().quantity_sold).isEqualTo(2);
        assertThat(line.getValue().sales_amount).isEqualByComparingTo("22.99");
        verify(orders, never()).findCompletedByStoreIdAndCompletedDate(any(), any());
    }
}
