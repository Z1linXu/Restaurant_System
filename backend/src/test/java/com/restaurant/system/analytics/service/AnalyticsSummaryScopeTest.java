package com.restaurant.system.analytics.service;

import com.restaurant.system.analytics.repository.*;
import com.restaurant.system.analytics.service.impl.AnalyticsAggregationServiceImpl;
import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnalyticsSummaryScopeTest {
    @Test void organizationReadQueriesOnlyMembershipAuthorizedStoresForOldAndNewFinancialData() {
        StoreRepository stores = mock(StoreRepository.class);
        AnalyticsReadScope scope = mock(AnalyticsReadScope.class);
        ChannelSalesService channels = mock(ChannelSalesService.class);
        SalesDailySummaryRepository daily = mock(SalesDailySummaryRepository.class);
        SalesHourlySummaryRepository hourly = mock(SalesHourlySummaryRepository.class);
        MenuItemSalesSummaryRepository items = mock(MenuItemSalesSummaryRepository.class);
        StorePerformanceSummaryRepository performance = mock(StorePerformanceSummaryRepository.class);
        AnalyticsAlertRepository alerts = mock(AnalyticsAlertRepository.class);
        var service = new AnalyticsAggregationServiceImpl(stores, mock(OrderRepository.class), mock(OrderItemRepository.class),
            mock(InventoryItemRepository.class), mock(MenuItemRepository.class), daily, hourly, items, performance, alerts, channels, scope);
        Store allowed = new Store(); allowed.id = 7L; allowed.organization_id = 1L;
        when(scope.stores(1L, null)).thenReturn(List.of(allowed));
        LocalDate date = LocalDate.of(2026, 10, 3);
        service.getSummaries(1L, null, "today", date, null, null);
        verify(channels).build(List.of(7L), date, date, true);
        verify(daily).findAllByStore_idAndSummary_dateBetweenOrderBySummary_dateAsc(7L, date, date);
        verify(hourly).findAllByStore_idAndSummary_dateOrderByHour_of_dayAsc(7L, date);
        verify(items).findAllByStore_idAndSummary_dateBetween(7L, date, date);
        verify(performance).findAllByStore_idAndSummary_dateBetweenOrderBySummary_dateAsc(7L, date, date);
        verify(alerts).findAllByStore_idAndCreated_atBetweenAndIs_resolvedFalseOrderByCreated_atDesc(7L, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        verifyNoInteractions(stores);
        verify(daily, never()).findAllByOrganization_idAndSummary_dateBetweenOrderBySummary_dateAsc(any(), any(), any());
    }
}
