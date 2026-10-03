package com.restaurant.system.platform.service;

import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.platform.dto.OwnerDashboardResponse;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.platform.service.impl.OwnerDashboardServiceImpl;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class OwnerDashboardReportingTest {
    StoreRepository stores = mock(StoreRepository.class);
    OrderRepository orders = mock(OrderRepository.class);
    OrderItemRepository items = mock(OrderItemRepository.class);
    MenuItemRepository menu = mock(MenuItemRepository.class);
    com.restaurant.system.analytics.service.AnalyticsReadScope readScope = mock(com.restaurant.system.analytics.service.AnalyticsReadScope.class);
    OwnerDashboardService service = new OwnerDashboardServiceImpl(menu, mock(MenuCategoryRepository.class), stores,
        mock(OrganizationRepository.class), orders, items, mock(InventoryItemRepository.class), mock(com.restaurant.system.analytics.service.ChannelSalesService.class), readScope);

    @BeforeEach void setup() {
        Store store = new Store(); store.id = 1L; store.organization_id = 1L; store.name = "Test";
        when(readScope.comparisonStores(1L, 1L)).thenReturn(List.of(store));
        when(stores.findById(1L)).thenReturn(java.util.Optional.of(store));
    }

    @Test void completedAtTwentyOneIsBucketedAtSubmissionEighteenAndDraftAndUberAreExcluded() {
        Order regular = order(1, 18, "30.00"); regular.completed_at = LocalDate.now().atTime(21, 0);
        Order uber = order(2, 18, "900.00"); uber.financial_mode = "EXTERNAL_PLATFORM";
        Order draft = order(3, 18, "100.00"); draft.status = "draft";
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(regular, uber, draft));
        var result = service.getDashboard(1L, 1L, "today", false);
        assertThat(result.kpis.sales.value).isEqualByComparingTo("30.00");
        assertThat(result.trend.points).extracting(row -> row.label).containsExactly("10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00", "21:00", "22:00");
        assertThat(result.trend.points.get(8).value).isEqualByComparingTo("30.00");
        assertThat(result.trend.points.get(11).value).isZero();
        assertThat(result.sales_timestamp).isEqualTo("POS_SUBMITTED_AT_UBER_PLACED_AT");
    }

    @Test void outsideTradingHoursRemainInSalesAndDailyWeekMonthButNotHourlyChart() {
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(order(1, 9, "20"), order(2, 23, "30")));
        var today = service.getDashboard(1L, 1L, "today", false);
        assertThat(today.kpis.sales.value).isEqualByComparingTo("50.00");
        assertThat(trendTotal(today)).isZero();
        for (String range : List.of("week", "month")) {
            var result = service.getDashboard(1L, 1L, range, false);
            assertThat(result.trend.granularity).isEqualTo("daily");
            assertThat(trendTotal(result)).isEqualByComparingTo("50.00");
        }
    }

    @Test void revenueMixIncludesOptionsDiscountAndOtherAndSeparatesAlcohol() {
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(order(1, 18, "100.00")));
        when(items.findAllByOrderIds(anyList())).thenReturn(List.of(
            item(1, "traditional_beef_noodle", "SOUP_NOODLE", 2, "40.00"),
            item(2, "dan_dan_noodle", "DRY_NOODLE", 1, "20.00"),
            item(3, "coke", "DRINK", 1, "10.00"),
            item(4, "soju", "DRINK", 1, "10.00"),
            item(5, "future_unknown", "FUTURE", 1, "20.00")
        ));
        var result = service.getDashboard(1L, 1L, "today", false);
        assertThat(group(result, "SOUP_NOODLE").quantity_sold).isEqualTo(2);
        assertThat(group(result, "SOUP_NOODLE").revenue).isEqualByComparingTo("40.00");
        assertThat(group(result, "DRINK").revenue).isEqualByComparingTo("10.00");
        assertThat(group(result, "ALCOHOL").revenue).isEqualByComparingTo("10.00");
        assertThat(group(result, "OTHER").revenue).isEqualByComparingTo("20.00");
        assertThat(result.revenue_mix.stream().map(row -> row.revenue).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(result.kpis.sales.value);
        assertThat(result.revenue_mix.stream().map(row -> row.percentage).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }

    @Test void thirdCentPercentagesStillSumToOneHundred() {
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(order(1, 18, "1.00")));
        when(items.findAllByOrderIds(anyList())).thenReturn(List.of(item(1, "coke", "DRINK", 1, "1"), item(2, "soju", "DRINK", 1, "1"), item(3, "future", "FUTURE", 1, "1")));
        var result = service.getDashboard(1L, 1L, "today", false);
        assertThat(result.revenue_mix.stream().map(row -> row.percentage).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        assertThat(result.revenue_mix.stream().map(row -> row.revenue).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("1.00");
    }

    @Test void friedNoodleRowRequiresActiveCatalogAndShowsZeroSalesWhenSold() {
        assertThat(service.getDashboard(1L, 1L, "today", false).noodle_sales).extracting(row -> row.reporting_group).containsExactly("SOUP_NOODLE", "DRY_NOODLE");
        MenuItem chow = new MenuItem(); chow.sku = "chicken_chow_mein"; chow.is_active = true;
        when(menu.findActiveByStoreId(1L)).thenReturn(List.of(chow));
        var result = service.getDashboard(1L, 1L, "today", false);
        assertThat(result.noodle_sales).extracting(row -> row.reporting_group).containsExactly("SOUP_NOODLE", "DRY_NOODLE", "FRIED_NOODLE");
        assertThat(group(result, "FRIED_NOODLE").revenue).isZero();
        assertThat(group(result, "FRIED_NOODLE").quantity_sold).isZero();
    }

    @Test void selectedStoreCannotIncludeAnotherStoresSalesOrFriedCatalog() {
        Store second = new Store(); second.id = 2L; second.organization_id = 1L;
        var first = stores.findById(1L).orElseThrow(); when(readScope.comparisonStores(1L, 1L)).thenReturn(List.of(first, second));
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(order(1, 18, "10")));
        when(orders.findAllByStoreId(2L)).thenReturn(List.of(order(2, 18, "90")));
        MenuItem chow = new MenuItem(); chow.sku = "chicken_chow_mein";
        when(menu.findActiveByStoreId(2L)).thenReturn(List.of(chow));
        var result = service.getDashboard(1L, 1L, "today", false);
        assertThat(result.kpis.sales.value).isEqualByComparingTo("10.00");
        assertThat(result.noodle_sales).hasSize(2);
    }

    @Test void cancelledDrinkRetainsHistoryButNotRevenueQuantityOrItemRankings() {
        when(orders.findAllByStoreId(1L)).thenReturn(List.of(order(1, 18, "10.00")));
        OrderItem soup = item(1, "traditional_beef_noodle", "SOUP_NOODLE", 1, "10.00");
        soup.item_name_snapshot_en = "Soup";
        OrderItem drink = item(2, "coke", "DRINK", 1, "10.00");
        drink.item_name_snapshot_en = "Cancelled drink";
        drink.status = "cancelled";
        when(items.findAllByOrderIds(anyList())).thenReturn(List.of(soup, drink));
        var result = service.getDashboard(1L, 1L, "today", false);
        assertThat(group(result, "SOUP_NOODLE").revenue).isEqualByComparingTo("10.00");
        assertThat(group(result, "SOUP_NOODLE").quantity_sold).isEqualTo(1);
        assertThat(group(result, "DRINK").revenue).isZero();
        assertThat(group(result, "DRINK").quantity_sold).isZero();
        assertThat(result.top_items).extracting(row -> row.item_name).containsExactly("Soup");
    }

    private Order order(long id, int hour, String amount) {
        Order order = new Order(); order.id = id; order.store_id = 1L; order.status = "completed";
        order.submitted_at = LocalDate.now().atTime(hour, 0); order.completed_at = order.submitted_at.plusHours(1); order.total_amount = new BigDecimal(amount); return order;
    }
    private OrderItem item(long id, String sku, String category, int quantity, String amount) {
        OrderItem item = new OrderItem(); item.id = id; item.order_id = 1L; item.menu_item_id = id;
        item.item_sku_snapshot = sku; item.category_code_snapshot = category; item.item_name_snapshot_en = "Display is not a classifier";
        item.quantity = quantity; item.line_amount = new BigDecimal(amount); return item;
    }
    private OwnerDashboardResponse.CategorySales group(OwnerDashboardResponse response, String group) {
        return response.revenue_mix.stream().filter(row -> row.reporting_group.equals(group)).findFirst().orElseThrow();
    }
    private BigDecimal trendTotal(OwnerDashboardResponse response) { return response.trend.points.stream().map(row -> row.value).reduce(BigDecimal.ZERO, BigDecimal::add); }
}
