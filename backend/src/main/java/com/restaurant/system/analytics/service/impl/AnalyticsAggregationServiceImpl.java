package com.restaurant.system.analytics.service.impl;

import com.restaurant.system.analytics.dto.AnalyticsSummaryResponse;
import com.restaurant.system.analytics.entity.AnalyticsAlert;
import com.restaurant.system.analytics.entity.MenuItemSalesSummary;
import com.restaurant.system.analytics.entity.SalesDailySummary;
import com.restaurant.system.analytics.entity.SalesHourlySummary;
import com.restaurant.system.analytics.entity.StorePerformanceSummary;
import com.restaurant.system.analytics.repository.AnalyticsAlertRepository;
import com.restaurant.system.analytics.repository.MenuItemSalesSummaryRepository;
import com.restaurant.system.analytics.repository.SalesDailySummaryRepository;
import com.restaurant.system.analytics.repository.SalesHourlySummaryRepository;
import com.restaurant.system.analytics.repository.StorePerformanceSummaryRepository;
import com.restaurant.system.analytics.service.AnalyticsAggregationService;
import com.restaurant.system.inventory.entity.InventoryItem;
import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.menu.entity.MenuItem;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsAggregationServiceImpl implements AnalyticsAggregationService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAggregationServiceImpl.class);

    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of("submitted", "preparing", "ready");
    private static final Set<String> COMPLETED_ORDER_STATUSES = Set.of("completed");
    private static final Set<String> CANCELLED_ORDER_STATUSES = Set.of("cancelled");

    private final StoreRepository storeRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final SalesDailySummaryRepository salesDailySummaryRepository;
    private final SalesHourlySummaryRepository salesHourlySummaryRepository;
    private final MenuItemSalesSummaryRepository menuItemSalesSummaryRepository;
    private final StorePerformanceSummaryRepository storePerformanceSummaryRepository;
    private final AnalyticsAlertRepository analyticsAlertRepository;

    public AnalyticsAggregationServiceImpl(
        StoreRepository storeRepository,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        InventoryItemRepository inventoryItemRepository,
        MenuItemRepository menuItemRepository,
        SalesDailySummaryRepository salesDailySummaryRepository,
        SalesHourlySummaryRepository salesHourlySummaryRepository,
        MenuItemSalesSummaryRepository menuItemSalesSummaryRepository,
        StorePerformanceSummaryRepository storePerformanceSummaryRepository,
        AnalyticsAlertRepository analyticsAlertRepository
    ) {
        this.storeRepository = storeRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.salesDailySummaryRepository = salesDailySummaryRepository;
        this.salesHourlySummaryRepository = salesHourlySummaryRepository;
        this.menuItemSalesSummaryRepository = menuItemSalesSummaryRepository;
        this.storePerformanceSummaryRepository = storePerformanceSummaryRepository;
        this.analyticsAlertRepository = analyticsAlertRepository;
    }

    @Override
    @Transactional
    public void rebuildForDate(LocalDate summaryDate, Long storeId) {
        List<Store> stores = resolveStores(storeId);
        for (Store store : stores) {
            rebuildStoreForDate(summaryDate, store);
        }
    }

    @Override
    @Transactional
    public void rebuildYesterday() {
        rebuildForDate(LocalDate.now().minusDays(1), null);
    }

    @Override
    public AnalyticsSummaryResponse getSummaries(Long organizationId, Long storeId, String range) {
        return getSummaries(organizationId, storeId, range, null, null, null);
    }

    @Override
    public AnalyticsSummaryResponse getSummaries(
        Long organizationId,
        Long storeId,
        String range,
        LocalDate anchorDate,
        LocalDate startDate,
        LocalDate endDate
    ) {
        String normalizedRange = normalizeRange(range, startDate, endDate);
        DateRangeWindow window = buildDateWindow(normalizedRange, anchorDate == null ? LocalDate.now() : anchorDate, startDate, endDate);
        AnalyticsSummaryResponse response = new AnalyticsSummaryResponse();
        response.organization_id = organizationId;
        response.store_id = storeId;
        response.range = normalizedRange;
        response.start_date = window.startDate.toString();
        response.end_date = window.endDate.toString();
        response.sales_daily_summaries = getDailySummaries(organizationId, storeId, window);
        response.sales_hourly_summaries =
            "today".equals(normalizedRange) && window.startDate.equals(window.endDate)
                ? getHourlySummaries(organizationId, storeId, window.startDate)
                : List.of();
        response.menu_item_sales_summaries = getMenuItemSummaries(organizationId, storeId, window);
        response.store_performance_summaries = getStorePerformanceSummaries(organizationId, storeId, window);
        response.analytics_alerts = getAlerts(organizationId, storeId, window);
        return response;
    }

    private void rebuildStoreForDate(LocalDate summaryDate, Store store) {
        Date sqlSummaryDate = Date.valueOf(summaryDate);
        List<Order> storeOrders = orderRepository.findAllByStoreId(store.id);
        List<Order> completedOrders = orderRepository.findCompletedByStoreIdAndCompletedDate(store.id, sqlSummaryDate);
        List<Order> cancelledOrders = orderRepository.findCancelledByStoreIdAndUpdatedDate(store.id, sqlSummaryDate);
        List<OrderItem> completedOrderItems = fetchOrderItems(completedOrders);
        Map<Long, BigDecimal> costByMenuItemId = loadItemCosts(completedOrderItems);

        BigDecimal grossSales = scale(sumSubtotals(completedOrders));
        BigDecimal netSales = scale(sumTotals(completedOrders));
        BigDecimal totalCost = scale(sumItemCosts(completedOrderItems, costByMenuItemId));
        BigDecimal totalProfit = scale(netSales.subtract(totalCost));
        BigDecimal profitMargin = scale(percentOf(totalProfit, netSales));

        log.info(
            "Analytics rebuild started: targetDate={}, storeId={}, completedOrdersFound={}, totalGrossSales={}, totalNetSales={}, totalCost={}, totalProfit={}",
            summaryDate,
            store.id,
            completedOrders.size(),
            grossSales,
            netSales,
            totalCost,
            totalProfit
        );

        salesDailySummaryRepository.deleteAllBySummary_dateAndStore_id(summaryDate, store.id);
        salesHourlySummaryRepository.deleteAllBySummary_dateAndStore_id(summaryDate, store.id);
        menuItemSalesSummaryRepository.deleteAllBySummary_dateAndStore_id(summaryDate, store.id);
        storePerformanceSummaryRepository.deleteAllBySummary_dateAndStore_id(summaryDate, store.id);
        analyticsAlertRepository.deleteAllByStore_idAndCreated_atBetween(store.id, summaryDate.atStartOfDay(), summaryDate.plusDays(1).atStartOfDay());

        SalesDailySummary dailySummary = new SalesDailySummary();
        dailySummary.summary_date = summaryDate;
        dailySummary.organization_id = store.organization_id;
        dailySummary.store_id = store.id;
        dailySummary.gross_sales = grossSales;
        dailySummary.net_sales = netSales;
        dailySummary.completed_order_count = completedOrders.size();
        dailySummary.cancelled_order_count = cancelledOrders.size();
        dailySummary.order_count = completedOrders.size();
        dailySummary.average_order_value = scale(safeDivide(dailySummary.net_sales, BigDecimal.valueOf(Math.max(completedOrders.size(), 1))));
        dailySummary.total_cost = totalCost;
        dailySummary.total_profit = totalProfit;
        dailySummary.profit_margin = profitMargin;
        dailySummary.created_at = summaryDate.atStartOfDay();
        dailySummary.updated_at = LocalDateTime.now();
        salesDailySummaryRepository.save(dailySummary);

        Map<Integer, List<Order>> ordersByHour = completedOrders.stream()
            .filter(order -> order.completed_at != null)
            .collect(Collectors.groupingBy(order -> order.completed_at.getHour()));
        for (int hour = 0; hour < 24; hour += 1) {
            List<Order> hourOrders = ordersByHour.getOrDefault(hour, List.of());
            SalesHourlySummary hourlySummary = new SalesHourlySummary();
            hourlySummary.summary_date = summaryDate;
            hourlySummary.hour_of_day = hour;
            hourlySummary.organization_id = store.organization_id;
            hourlySummary.store_id = store.id;
            hourlySummary.sales_amount = scale(sumTotals(hourOrders));
            hourlySummary.order_count = hourOrders.size();
            hourlySummary.created_at = summaryDate.atStartOfDay();
            hourlySummary.updated_at = LocalDateTime.now();
            salesHourlySummaryRepository.save(hourlySummary);
        }

        Map<Long, AggregatedItem> aggregatedItems = new LinkedHashMap<>();
        for (OrderItem item : completedOrderItems) {
            AggregatedItem current = aggregatedItems.computeIfAbsent(
                item.menu_item_id,
                ignored -> new AggregatedItem(item.menu_item_id, item.item_name_snapshot_zh, item.item_name_snapshot_en)
            );
            current.quantitySold += optionalInt(item.quantity);
            current.salesAmount = current.salesAmount.add(optional(item.line_amount));
            current.totalCost = current.totalCost.add(scale(optional(costByMenuItemId.get(item.menu_item_id)).multiply(BigDecimal.valueOf(Math.max(optionalInt(item.quantity), 0)))));
            current.orderIds.add(item.order_id);
        }

        aggregatedItems.values().forEach(item -> {
            MenuItemSalesSummary summary = new MenuItemSalesSummary();
            summary.summary_date = summaryDate;
            summary.organization_id = store.organization_id;
            summary.store_id = store.id;
            summary.menu_item_id = item.menuItemId;
            summary.item_name_snapshot_zh = item.itemNameZh;
            summary.item_name_snapshot_en = item.itemNameEn;
            summary.quantity_sold = item.quantitySold;
            summary.sales_amount = scale(item.salesAmount);
            summary.total_cost = scale(item.totalCost);
            summary.total_profit = scale(item.salesAmount.subtract(item.totalCost));
            summary.order_count = item.orderIds.size();
            summary.created_at = summaryDate.atStartOfDay();
            summary.updated_at = LocalDateTime.now();
            menuItemSalesSummaryRepository.save(summary);
        });

        StorePerformanceSummary performanceSummary = new StorePerformanceSummary();
        performanceSummary.summary_date = summaryDate;
        performanceSummary.organization_id = store.organization_id;
        performanceSummary.store_id = store.id;
        performanceSummary.sales_amount = netSales;
        performanceSummary.order_count = completedOrders.size();
        performanceSummary.average_order_value = scale(safeDivide(performanceSummary.sales_amount, BigDecimal.valueOf(Math.max(completedOrders.size(), 1))));
        performanceSummary.active_order_count = summaryDate.equals(LocalDate.now())
            ? (int) storeOrders.stream().filter(order -> ACTIVE_ORDER_STATUSES.contains(normalize(order.status))).count()
            : 0;
        performanceSummary.created_at = summaryDate.atStartOfDay();
        performanceSummary.updated_at = LocalDateTime.now();
        storePerformanceSummaryRepository.save(performanceSummary);

        createAlertsForDate(summaryDate, store, dailySummary, completedOrderItems);

        int summaryRowsInserted = 1 + 24 + aggregatedItems.size() + 1;
        log.info(
            "Analytics rebuild completed: targetDate={}, storeId={}, summaryRowsInserted={}, dailyRows=1, hourlyRows=24, menuItemRows={}, storePerformanceRows=1",
            summaryDate,
            store.id,
            summaryRowsInserted,
            aggregatedItems.size()
        );
    }

    private void createAlertsForDate(LocalDate summaryDate, Store store, SalesDailySummary dailySummary, List<OrderItem> completedOrderItems) {
        SalesDailySummary previousSummary = salesDailySummaryRepository.findBySummary_dateAndStore_id(summaryDate.minusDays(1), store.id).orElse(null);
        if (previousSummary != null && optional(previousSummary.net_sales).compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dropPct = percentChange(optional(dailySummary.net_sales), optional(previousSummary.net_sales));
            if (dropPct.compareTo(BigDecimal.valueOf(-15)) <= 0) {
                analyticsAlertRepository.save(alert(
                    store.organization_id,
                    store.id,
                    "sales_drop",
                    "warning",
                    "Sales dropped more than 15%",
                    "Net sales are down " + scale(dropPct).abs() + "% compared with the previous day.",
                    optional(dailySummary.net_sales),
                    optional(previousSummary.net_sales),
                    summaryDate
                ));
            }
        }

        Map<Long, Integer> quantityByMenuItem = completedOrderItems.stream()
            .collect(Collectors.groupingBy(item -> item.menu_item_id, Collectors.summingInt(item -> optionalInt(item.quantity))));
        quantityByMenuItem.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .ifPresent(entry -> {
                OrderItem sample = completedOrderItems.stream().filter(item -> Objects.equals(item.menu_item_id, entry.getKey())).findFirst().orElse(null);
                if (sample != null && entry.getValue() > 0) {
                    analyticsAlertRepository.save(alert(
                        store.organization_id,
                        store.id,
                        "trending_item",
                        "info",
                        "Trending item: " + sample.item_name_snapshot_zh,
                        sample.item_name_snapshot_zh + " sold " + entry.getValue() + " units on " + summaryDate + ".",
                        BigDecimal.valueOf(entry.getValue()),
                        BigDecimal.ZERO,
                        summaryDate
                    ));
                }
            });

        inventoryItemRepository.findAll().stream()
            .filter(item -> Objects.equals(item.store_id, store.id))
            .filter(item -> Boolean.TRUE.equals(item.is_active))
            .filter(item -> item.current_stock != null && item.safety_stock != null)
            .filter(item -> item.current_stock.compareTo(item.safety_stock) <= 0)
            .limit(3)
            .forEach(item -> analyticsAlertRepository.save(alert(
                store.organization_id,
                store.id,
                "low_inventory",
                "critical",
                "Low inventory: " + item.name,
                "Current stock " + scale(item.current_stock) + " is at or below safety stock " + scale(item.safety_stock) + ".",
                optional(item.current_stock),
                optional(item.safety_stock),
                summaryDate
            )));
    }

    private AnalyticsAlert alert(
        Long organizationId,
        Long storeId,
        String alertType,
        String severity,
        String title,
        String message,
        BigDecimal metricValue,
        BigDecimal comparisonValue,
        LocalDate summaryDate
    ) {
        AnalyticsAlert alert = new AnalyticsAlert();
        alert.organization_id = organizationId;
        alert.store_id = storeId;
        alert.alert_type = alertType;
        alert.severity = severity;
        alert.title = title;
        alert.message = message;
        alert.metric_value = scale(metricValue);
        alert.comparison_value = scale(comparisonValue);
        alert.is_resolved = false;
        alert.created_at = summaryDate.atStartOfDay();
        alert.resolved_at = null;
        return alert;
    }

    private List<Store> resolveStores(Long storeId) {
        if (storeId == null) {
            return storeRepository.findAll();
        }
        return List.of(storeRepository.findById(storeId).orElseThrow(() -> new IllegalArgumentException("Store not found")));
    }

    private List<SalesDailySummary> getDailySummaries(Long organizationId, Long storeId, DateRangeWindow window) {
        if (storeId != null) {
            return salesDailySummaryRepository.findAllByStore_idAndSummary_dateBetweenOrderBySummary_dateAsc(storeId, window.startDate, window.endDate);
        }
        if (organizationId != null) {
            return salesDailySummaryRepository.findAllByOrganization_idAndSummary_dateBetweenOrderBySummary_dateAsc(organizationId, window.startDate, window.endDate);
        }
        return List.of();
    }

    private List<SalesHourlySummary> getHourlySummaries(Long organizationId, Long storeId, LocalDate summaryDate) {
        if (storeId != null) {
            return salesHourlySummaryRepository.findAllByStore_idAndSummary_dateOrderByHour_of_dayAsc(storeId, summaryDate);
        }
        if (organizationId != null) {
            return salesHourlySummaryRepository.findAllByOrganization_idAndSummary_dateOrderByHour_of_dayAsc(organizationId, summaryDate);
        }
        return List.of();
    }

    private List<MenuItemSalesSummary> getMenuItemSummaries(Long organizationId, Long storeId, DateRangeWindow window) {
        if (storeId != null) {
            return menuItemSalesSummaryRepository.findAllByStore_idAndSummary_dateBetween(storeId, window.startDate, window.endDate);
        }
        if (organizationId != null) {
            return menuItemSalesSummaryRepository.findAllByOrganization_idAndSummary_dateBetween(organizationId, window.startDate, window.endDate);
        }
        return List.of();
    }

    private List<StorePerformanceSummary> getStorePerformanceSummaries(Long organizationId, Long storeId, DateRangeWindow window) {
        if (storeId != null) {
            return storePerformanceSummaryRepository.findAllByStore_idAndSummary_dateBetweenOrderBySummary_dateAsc(storeId, window.startDate, window.endDate);
        }
        if (organizationId != null) {
            return storePerformanceSummaryRepository.findAllByOrganization_idAndSummary_dateBetweenOrderBySummary_dateAsc(organizationId, window.startDate, window.endDate);
        }
        return List.of();
    }

    private List<AnalyticsAlert> getAlerts(Long organizationId, Long storeId, DateRangeWindow window) {
        LocalDateTime start = window.startDate.atStartOfDay();
        LocalDateTime end = window.endDate.plusDays(1).atStartOfDay();
        if (storeId != null) {
            return analyticsAlertRepository.findAllByStore_idAndCreated_atBetweenAndIs_resolvedFalseOrderByCreated_atDesc(storeId, start, end);
        }
        if (organizationId != null) {
            return analyticsAlertRepository.findAllByOrganization_idAndCreated_atBetweenAndIs_resolvedFalseOrderByCreated_atDesc(organizationId, start, end);
        }
        return List.of();
    }

    private List<OrderItem> fetchOrderItems(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orders.stream().map(order -> order.id).toList();
        return orderItemRepository.findAllByOrderIds(orderIds);
    }

    private boolean matchesDate(LocalDateTime value, LocalDate targetDate) {
        return value != null && value.toLocalDate().equals(targetDate);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private BigDecimal optional(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int optionalInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal scale(BigDecimal value) {
        return optional(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return optional(numerator).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current == null || current.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return optional(current)
            .subtract(previous)
            .multiply(BigDecimal.valueOf(100))
            .divide(previous, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumTotals(List<Order> orders) {
        return orders.stream().map(order -> optional(order.total_amount)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumSubtotals(List<Order> orders) {
        return orders.stream().map(order -> optional(order.subtotal_amount)).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumItemCosts(List<OrderItem> items, Map<Long, BigDecimal> costByMenuItemId) {
        return items.stream()
            .map(item -> optional(costByMenuItemId.get(item.menu_item_id)).multiply(BigDecimal.valueOf(Math.max(optionalInt(item.quantity), 0))))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, BigDecimal> loadItemCosts(List<OrderItem> items) {
        List<Long> menuItemIds = items.stream()
            .map(item -> item.menu_item_id)
            .filter(Objects::nonNull)
            .distinct()
            .toList();

        if (menuItemIds.isEmpty()) {
            return Map.of();
        }

        return menuItemRepository.findAllById(menuItemIds).stream()
            .collect(Collectors.toMap(menuItem -> menuItem.id, menuItem -> optional(menuItem.cost_per_item)));
    }

    private BigDecimal percentOf(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return optional(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private String normalizeRange(String range, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            return "custom";
        }
        if (range == null) {
            return "today";
        }
        return switch (range.trim().toLowerCase()) {
            case "week" -> "week";
            case "month" -> "month";
            case "custom" -> "custom";
            default -> "today";
        };
    }

    private DateRangeWindow buildDateWindow(String range, LocalDate referenceDate, LocalDate startDate, LocalDate endDate) {
        if ("custom".equals(range) && startDate != null && endDate != null) {
            return startDate.isAfter(endDate)
                ? new DateRangeWindow(endDate, startDate)
                : new DateRangeWindow(startDate, endDate);
        }
        return switch (range) {
            case "week" -> new DateRangeWindow(
                referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                referenceDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            );
            case "month" -> new DateRangeWindow(referenceDate.withDayOfMonth(1), referenceDate.withDayOfMonth(referenceDate.lengthOfMonth()));
            default -> new DateRangeWindow(referenceDate, referenceDate);
        };
    }

    private static class AggregatedItem {
        private final Long menuItemId;
        private final String itemNameZh;
        private final String itemNameEn;
        private int quantitySold = 0;
        private BigDecimal salesAmount = BigDecimal.ZERO;
        private BigDecimal totalCost = BigDecimal.ZERO;
        private final Set<Long> orderIds = new java.util.LinkedHashSet<>();

        private AggregatedItem(Long menuItemId, String itemNameZh, String itemNameEn) {
            this.menuItemId = menuItemId;
            this.itemNameZh = itemNameZh;
            this.itemNameEn = itemNameEn;
        }
    }

    private static class DateRangeWindow {
        private final LocalDate startDate;
        private final LocalDate endDate;

        private DateRangeWindow(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}
