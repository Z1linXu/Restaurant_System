package com.restaurant.system.platform.service.impl;

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
import com.restaurant.system.inventory.entity.InventoryItem;
import com.restaurant.system.inventory.repository.InventoryItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import com.restaurant.system.platform.dto.OwnerDashboardResponse;
import com.restaurant.system.platform.repository.OrganizationRepository;
import com.restaurant.system.platform.entity.Organization;
import com.restaurant.system.platform.service.OwnerDashboardService;
import com.restaurant.system.user.entity.Store;
import com.restaurant.system.user.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OwnerDashboardServiceImpl implements OwnerDashboardService {

    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("h:mm a");
    private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of("submitted", "preparing", "ready");
    private static final Set<String> COMPLETED_ORDER_STATUSES = Set.of("completed");

    private final StoreRepository storeRepository;
    private final OrganizationRepository organizationRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final SalesDailySummaryRepository salesDailySummaryRepository;
    private final SalesHourlySummaryRepository salesHourlySummaryRepository;
    private final MenuItemSalesSummaryRepository menuItemSalesSummaryRepository;
    private final StorePerformanceSummaryRepository storePerformanceSummaryRepository;
    private final AnalyticsAlertRepository analyticsAlertRepository;

    public OwnerDashboardServiceImpl(
        StoreRepository storeRepository,
        OrganizationRepository organizationRepository,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        InventoryItemRepository inventoryItemRepository,
        SalesDailySummaryRepository salesDailySummaryRepository,
        SalesHourlySummaryRepository salesHourlySummaryRepository,
        MenuItemSalesSummaryRepository menuItemSalesSummaryRepository,
        StorePerformanceSummaryRepository storePerformanceSummaryRepository,
        AnalyticsAlertRepository analyticsAlertRepository
    ) {
        this.storeRepository = storeRepository;
        this.organizationRepository = organizationRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.salesDailySummaryRepository = salesDailySummaryRepository;
        this.salesHourlySummaryRepository = salesHourlySummaryRepository;
        this.menuItemSalesSummaryRepository = menuItemSalesSummaryRepository;
        this.storePerformanceSummaryRepository = storePerformanceSummaryRepository;
        this.analyticsAlertRepository = analyticsAlertRepository;
    }

    @Override
    public OwnerDashboardResponse getDashboard(Long organizationId, Long storeId, String range, boolean compareEnabled) {
        String normalizedRange = normalizeRange(range);
        TimeWindow currentWindow = buildWindow(normalizedRange, LocalDate.now());
        TimeWindow previousWindow = currentWindow.previousWindow();

        List<Store> organizationStores = resolveStores(organizationId, storeId);
        List<Long> scopedStoreIds = resolveScopedStoreIds(organizationStores, storeId);
        Map<Long, List<Order>> ordersByStore = organizationStores.stream()
            .collect(Collectors.toMap(store -> store.id, store -> orderRepository.findAllByStoreId(store.id)));

        List<Order> scopedOrders = scopedStoreIds.stream()
            .flatMap(id -> ordersByStore.getOrDefault(id, List.of()).stream())
            .toList();

        List<Order> currentCompletedOrders = scopedOrders.stream()
            .filter(order -> COMPLETED_ORDER_STATUSES.contains(normalize(order.status)))
            .filter(order -> inWindow(order.completed_at, currentWindow))
            .toList();
        List<Order> previousCompletedOrders = scopedOrders.stream()
            .filter(order -> COMPLETED_ORDER_STATUSES.contains(normalize(order.status)))
            .filter(order -> inWindow(order.completed_at, previousWindow))
            .toList();
        List<Order> activeOrders = scopedOrders.stream()
            .filter(order -> ACTIVE_ORDER_STATUSES.contains(normalize(order.status)))
            .toList();

        List<SalesDailySummary> currentDailySummaries = fetchDailySummaries(organizationId, storeId, currentWindow);
        List<SalesDailySummary> previousDailySummaries = fetchDailySummaries(organizationId, storeId, previousWindow);
        boolean useSummaryData = hasCurrentSummaryCoverage(currentDailySummaries, currentWindow, scopedStoreIds);
        List<MenuItemSalesSummary> currentItemSummaries = useSummaryData ? fetchItemSummaries(organizationId, storeId, currentWindow) : List.of();
        List<MenuItemSalesSummary> previousItemSummaries = useSummaryData ? fetchItemSummaries(organizationId, storeId, previousWindow) : List.of();
        List<AnalyticsAlert> currentAlerts = useSummaryData ? fetchAlerts(organizationId, storeId, currentWindow) : List.of();

        List<OrderItem> currentOrderItems = fetchOrderItems(currentCompletedOrders);
        List<OrderItem> previousOrderItems = fetchOrderItems(previousCompletedOrders);

        OwnerDashboardResponse response = new OwnerDashboardResponse();
        response.organization_id = organizationStores.stream().findFirst().map(store -> store.organization_id).orElse(organizationId);
        response.organization_name = resolveOrganizationName(organizationStores, organizationId);
        response.range = normalizedRange;
        response.compare_enabled = compareEnabled;
        response.stores = organizationStores.stream().map(this::toStoreSummary).toList();
        response.kpis = useSummaryData
            ? buildKpisFromSummaries(currentDailySummaries, previousDailySummaries, activeOrders)
            : buildKpis(currentCompletedOrders, previousCompletedOrders, activeOrders);
        response.insights = useSummaryData
            ? buildInsightsFromSummaries(currentAlerts, currentDailySummaries, previousDailySummaries, currentItemSummaries, previousItemSummaries, scopedStoreIds)
            : buildInsights(currentCompletedOrders, previousCompletedOrders, currentOrderItems, previousOrderItems, scopedStoreIds);
        response.trend = useSummaryData
            ? buildTrendFromSummaries(organizationId, storeId, normalizedRange, currentDailySummaries, currentWindow)
            : buildTrend(normalizedRange, currentCompletedOrders, currentWindow);
        response.top_items = useSummaryData
            ? buildItemPerformanceFromSummaries(currentItemSummaries, previousItemSummaries, true)
            : buildItemPerformance(currentOrderItems, previousOrderItems, true);
        response.worst_items = useSummaryData
            ? buildItemPerformanceFromSummaries(currentItemSummaries, previousItemSummaries, false)
            : buildItemPerformance(currentOrderItems, previousOrderItems, false);
        response.order_status = buildOrderStatus(activeOrders);
        response.store_comparison = useSummaryData
            ? buildStoreComparisonFromSummaries(organizationStores, currentWindow, previousWindow, activeOrders)
            : buildStoreComparison(organizationStores, ordersByStore, currentWindow, previousWindow);
        response.recent_orders = buildRecentOrders(scopedOrders);
        return response;
    }

    private boolean hasCurrentSummaryCoverage(List<SalesDailySummary> summaries, TimeWindow currentWindow, List<Long> scopedStoreIds) {
        LocalDate currentDate = currentWindow.start.toLocalDate();
        long coveredStores = summaries.stream()
            .filter(summary -> currentDate.equals(summary.summary_date))
            .map(summary -> summary.store_id)
            .distinct()
            .count();
        return coveredStores >= scopedStoreIds.size() && coveredStores > 0;
    }

    private List<SalesDailySummary> fetchDailySummaries(Long organizationId, Long storeId, TimeWindow window) {
        if (storeId != null) {
            return salesDailySummaryRepository.findAllByStore_idAndSummary_dateBetweenOrderBySummary_dateAsc(
                storeId,
                window.start.toLocalDate(),
                window.end.minusNanos(1).toLocalDate()
            );
        }
        if (organizationId != null) {
            return salesDailySummaryRepository.findAllByOrganization_idAndSummary_dateBetweenOrderBySummary_dateAsc(
                organizationId,
                window.start.toLocalDate(),
                window.end.minusNanos(1).toLocalDate()
            );
        }
        return List.of();
    }

    private List<MenuItemSalesSummary> fetchItemSummaries(Long organizationId, Long storeId, TimeWindow window) {
        if (storeId != null) {
            return menuItemSalesSummaryRepository.findAllByStore_idAndSummary_dateBetween(
                storeId,
                window.start.toLocalDate(),
                window.end.minusNanos(1).toLocalDate()
            );
        }
        if (organizationId != null) {
            return menuItemSalesSummaryRepository.findAllByOrganization_idAndSummary_dateBetween(
                organizationId,
                window.start.toLocalDate(),
                window.end.minusNanos(1).toLocalDate()
            );
        }
        return List.of();
    }

    private List<AnalyticsAlert> fetchAlerts(Long organizationId, Long storeId, TimeWindow window) {
        if (storeId != null) {
            return analyticsAlertRepository.findAllByStore_idAndCreated_atBetweenAndIs_resolvedFalseOrderByCreated_atDesc(
                storeId,
                window.start,
                window.end
            );
        }
        if (organizationId != null) {
            return analyticsAlertRepository.findAllByOrganization_idAndCreated_atBetweenAndIs_resolvedFalseOrderByCreated_atDesc(
                organizationId,
                window.start,
                window.end
            );
        }
        return List.of();
    }

    private List<StorePerformanceSummary> fetchStorePerformanceSummaries(Long organizationId, Long storeId, TimeWindow window) {
        if (storeId != null) {
            return storePerformanceSummaryRepository.findAllByStore_idAndSummary_dateBetweenOrderBySummary_dateAsc(
                storeId,
                window.start.toLocalDate(),
                window.end.minusNanos(1).toLocalDate()
            );
        }
        if (organizationId != null) {
            return storePerformanceSummaryRepository.findAllByOrganization_idAndSummary_dateBetweenOrderBySummary_dateAsc(
                organizationId,
                window.start.toLocalDate(),
                window.end.minusNanos(1).toLocalDate()
            );
        }
        return List.of();
    }

    private List<Store> resolveStores(Long organizationId, Long storeId) {
        List<Store> allStores = storeRepository.findAll().stream()
            .filter(store -> store.organization_id != null)
            .toList();

        if (organizationId != null) {
            return allStores.stream().filter(store -> Objects.equals(store.organization_id, organizationId)).toList();
        }

        if (storeId != null) {
            Store store = storeRepository.findById(storeId).orElseThrow(() -> new IllegalArgumentException("Store not found"));
            return allStores.stream().filter(candidate -> Objects.equals(candidate.organization_id, store.organization_id)).toList();
        }

        return allStores;
    }

    private List<Long> resolveScopedStoreIds(List<Store> organizationStores, Long storeId) {
        if (storeId == null) {
            return organizationStores.stream().map(store -> store.id).toList();
        }
        return List.of(storeId);
    }

    private OwnerDashboardResponse.StoreSummary toStoreSummary(Store store) {
        OwnerDashboardResponse.StoreSummary summary = new OwnerDashboardResponse.StoreSummary();
        summary.id = store.id;
        summary.name = store.name;
        summary.code = store.code;
        return summary;
    }

    private String resolveOrganizationName(List<Store> organizationStores, Long organizationId) {
        Long resolvedOrganizationId =
            organizationId != null
                ? organizationId
                : organizationStores.stream().findFirst().map(store -> store.organization_id).orElse(null);
        if (resolvedOrganizationId != null) {
            Organization organization = organizationRepository.findById(resolvedOrganizationId).orElse(null);
            if (organization != null && organization.name != null && !organization.name.isBlank()) {
                return organization.name;
            }
        }
        return resolvedOrganizationId == null ? "All Organizations" : "Organization " + resolvedOrganizationId;
    }

    private OwnerDashboardResponse.KpiSummary buildKpis(List<Order> currentCompletedOrders, List<Order> previousCompletedOrders, List<Order> activeOrders) {
        OwnerDashboardResponse.KpiSummary summary = new OwnerDashboardResponse.KpiSummary();
        BigDecimal currentSales = sumTotals(currentCompletedOrders);
        BigDecimal previousSales = sumTotals(previousCompletedOrders);
        BigDecimal currentOrders = BigDecimal.valueOf(currentCompletedOrders.size());
        BigDecimal previousOrders = BigDecimal.valueOf(previousCompletedOrders.size());
        BigDecimal currentAov = safeDivide(currentSales, currentOrders);
        BigDecimal previousAov = safeDivide(previousSales, previousOrders);
        BigDecimal currentActive = BigDecimal.valueOf(activeOrders.size());

        summary.sales = metric(currentSales, previousSales);
        summary.orders = metric(currentOrders, previousOrders);
        summary.average_order_value = metric(currentAov, previousAov);
        summary.active_orders = metric(currentActive, currentActive);
        return summary;
    }

    private OwnerDashboardResponse.KpiSummary buildKpisFromSummaries(
        List<SalesDailySummary> currentDailySummaries,
        List<SalesDailySummary> previousDailySummaries,
        List<Order> activeOrders
    ) {
        OwnerDashboardResponse.KpiSummary summary = new OwnerDashboardResponse.KpiSummary();
        BigDecimal currentSales = currentDailySummaries.stream().map(row -> optional(row.net_sales)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousSales = previousDailySummaries.stream().map(row -> optional(row.net_sales)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal currentOrders = BigDecimal.valueOf(currentDailySummaries.stream().mapToInt(row -> row.completed_order_count == null ? 0 : row.completed_order_count).sum());
        BigDecimal previousOrders = BigDecimal.valueOf(previousDailySummaries.stream().mapToInt(row -> row.completed_order_count == null ? 0 : row.completed_order_count).sum());
        BigDecimal currentAov = safeDivide(currentSales, currentOrders);
        BigDecimal previousAov = safeDivide(previousSales, previousOrders);
        BigDecimal currentActive = BigDecimal.valueOf(activeOrders.size());

        summary.sales = metric(currentSales, previousSales);
        summary.orders = metric(currentOrders, previousOrders);
        summary.average_order_value = metric(currentAov, previousAov);
        summary.active_orders = metric(currentActive, currentActive);
        return summary;
    }

    private OwnerDashboardResponse.MetricWithChange metric(BigDecimal current, BigDecimal previous) {
        OwnerDashboardResponse.MetricWithChange metric = new OwnerDashboardResponse.MetricWithChange();
        metric.value = scale(current);
        metric.previous_value = scale(previous);
        metric.change_pct = scale(percentChange(current, previous));
        return metric;
    }

    private List<OwnerDashboardResponse.InsightCard> buildInsights(
        List<Order> currentCompletedOrders,
        List<Order> previousCompletedOrders,
        List<OrderItem> currentOrderItems,
        List<OrderItem> previousOrderItems,
        List<Long> scopedStoreIds
    ) {
        List<OwnerDashboardResponse.InsightCard> cards = new ArrayList<>();

        BigDecimal currentSales = sumTotals(currentCompletedOrders);
        BigDecimal previousSales = sumTotals(previousCompletedOrders);
        BigDecimal salesDrop = percentChange(currentSales, previousSales);
        if (previousSales.compareTo(BigDecimal.ZERO) > 0 && salesDrop.compareTo(BigDecimal.valueOf(-15)) <= 0) {
            cards.add(insight(
                "sales_drop",
                "Sales dropped more than 15%",
                "Sales are down " + scale(salesDrop).abs() + "% versus the previous period.",
                "warning"
            ));
        }

        buildTrendingItems(currentOrderItems, previousOrderItems).stream()
            .limit(2)
            .forEach(item -> cards.add(insight(
                "trending_item",
                "Trending item: " + item.item_name,
                item.item_name + " is up by " + item.quantity_change.intValue() + " units versus the previous period.",
                "info"
            )));

        inventoryItemRepository.findAll().stream()
            .filter(item -> scopedStoreIds.contains(item.store_id))
            .filter(item -> Boolean.TRUE.equals(item.is_active))
            .filter(item -> item.safety_stock != null && item.current_stock != null)
            .filter(item -> item.current_stock.compareTo(item.safety_stock) <= 0)
            .sorted(Comparator.comparing(item -> optional(item.current_stock)))
            .limit(3)
            .forEach(item -> cards.add(insight(
                "low_inventory",
                "Low inventory: " + item.name,
                "Current stock " + scale(item.current_stock) + " is at or below safety stock " + scale(item.safety_stock) + ".",
                "critical"
            )));

        if (cards.isEmpty()) {
            cards.add(insight(
                "stable",
                "Operations look stable",
                "No significant sales drop or low inventory risk was detected in the current dashboard scope.",
                "success"
            ));
        }

        return cards;
    }

    private List<OwnerDashboardResponse.InsightCard> buildInsightsFromSummaries(
        List<AnalyticsAlert> currentAlerts,
        List<SalesDailySummary> currentDailySummaries,
        List<SalesDailySummary> previousDailySummaries,
        List<MenuItemSalesSummary> currentItemSummaries,
        List<MenuItemSalesSummary> previousItemSummaries,
        List<Long> scopedStoreIds
    ) {
        List<OwnerDashboardResponse.InsightCard> cards = currentAlerts.stream()
            .sorted(Comparator.comparing((AnalyticsAlert alert) -> alert.created_at).reversed())
            .limit(4)
            .map(alert -> insight(alert.alert_type, alert.title, alert.message, alert.severity))
            .collect(Collectors.toCollection(ArrayList::new));

        if (!cards.isEmpty()) {
            return cards;
        }

        BigDecimal currentSales = currentDailySummaries.stream().map(row -> optional(row.net_sales)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal previousSales = previousDailySummaries.stream().map(row -> optional(row.net_sales)).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salesDrop = percentChange(currentSales, previousSales);
        if (previousSales.compareTo(BigDecimal.ZERO) > 0 && salesDrop.compareTo(BigDecimal.valueOf(-15)) <= 0) {
            cards.add(insight(
                "sales_drop",
                "Sales dropped more than 15%",
                "Sales are down " + scale(salesDrop).abs() + "% versus the previous period.",
                "warning"
            ));
        }

        buildTrendingItemsFromSummaries(currentItemSummaries, previousItemSummaries).stream()
            .limit(2)
            .forEach(item -> cards.add(insight(
                "trending_item",
                "Trending item: " + item.item_name,
                item.item_name + " is up by " + item.quantity_change.intValue() + " units versus the previous period.",
                "info"
            )));

        inventoryItemRepository.findAll().stream()
            .filter(item -> scopedStoreIds.contains(item.store_id))
            .filter(item -> Boolean.TRUE.equals(item.is_active))
            .filter(item -> item.safety_stock != null && item.current_stock != null)
            .filter(item -> item.current_stock.compareTo(item.safety_stock) <= 0)
            .sorted(Comparator.comparing(item -> optional(item.current_stock)))
            .limit(3)
            .forEach(item -> cards.add(insight(
                "low_inventory",
                "Low inventory: " + item.name,
                "Current stock " + scale(item.current_stock) + " is at or below safety stock " + scale(item.safety_stock) + ".",
                "critical"
            )));

        if (cards.isEmpty()) {
            cards.add(insight(
                "stable",
                "Operations look stable",
                "No significant sales drop or low inventory risk was detected in the current dashboard scope.",
                "success"
            ));
        }

        return cards;
    }

    private OwnerDashboardResponse.InsightCard insight(String type, String title, String message, String severity) {
        OwnerDashboardResponse.InsightCard card = new OwnerDashboardResponse.InsightCard();
        card.type = type;
        card.title = title;
        card.message = message;
        card.severity = severity;
        return card;
    }

    private OwnerDashboardResponse.SalesTrend buildTrend(String range, List<Order> currentCompletedOrders, TimeWindow currentWindow) {
        OwnerDashboardResponse.SalesTrend trend = new OwnerDashboardResponse.SalesTrend();
        trend.granularity = switch (range) {
            case "today" -> "hourly";
            case "week" -> "daily";
            default -> "weekly";
        };
        trend.points = switch (trend.granularity) {
            case "hourly" -> buildHourlyPoints(currentCompletedOrders, currentWindow);
            case "daily" -> buildDailyPoints(currentCompletedOrders, currentWindow);
            default -> buildWeeklyPoints(currentCompletedOrders, currentWindow);
        };
        return trend;
    }

    private OwnerDashboardResponse.SalesTrend buildTrendFromSummaries(
        Long organizationId,
        Long storeId,
        String range,
        List<SalesDailySummary> currentDailySummaries,
        TimeWindow currentWindow
    ) {
        OwnerDashboardResponse.SalesTrend trend = new OwnerDashboardResponse.SalesTrend();
        trend.granularity = switch (range) {
            case "today" -> "hourly";
            case "week" -> "daily";
            default -> "weekly";
        };
        if ("hourly".equals(trend.granularity)) {
            List<SalesHourlySummary> hourlySummaries = storeId != null
                ? salesHourlySummaryRepository.findAllByStore_idAndSummary_dateOrderByHour_of_dayAsc(storeId, currentWindow.start.toLocalDate())
                : salesHourlySummaryRepository.findAllByOrganization_idAndSummary_dateOrderByHour_of_dayAsc(organizationId, currentWindow.start.toLocalDate());
            trend.points = buildHourlyPointsFromSummaries(hourlySummaries);
        } else if ("daily".equals(trend.granularity)) {
            trend.points = buildDailyPointsFromSummaries(currentDailySummaries, currentWindow);
        } else {
            trend.points = buildWeeklyPointsFromSummaries(currentDailySummaries, currentWindow);
        }
        return trend;
    }

    private List<OwnerDashboardResponse.TrendPoint> buildHourlyPoints(List<Order> orders, TimeWindow currentWindow) {
        Map<Integer, BigDecimal> totals = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour += 1) {
            totals.put(hour, BigDecimal.ZERO);
        }
        orders.forEach(order -> {
            if (order.completed_at != null && inWindow(order.completed_at, currentWindow)) {
                totals.computeIfPresent(order.completed_at.getHour(), (key, value) -> value.add(optional(order.total_amount)));
            }
        });
        return totals.entrySet().stream().map(entry -> point(String.format("%02d:00", entry.getKey()), entry.getValue())).toList();
    }

    private List<OwnerDashboardResponse.TrendPoint> buildHourlyPointsFromSummaries(List<SalesHourlySummary> hourlySummaries) {
        Map<Integer, BigDecimal> totals = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour += 1) {
            totals.put(hour, BigDecimal.ZERO);
        }
        hourlySummaries.forEach(summary ->
            totals.computeIfPresent(summary.hour_of_day, (key, value) -> value.add(optional(summary.sales_amount)))
        );
        return totals.entrySet().stream().map(entry -> point(String.format("%02d:00", entry.getKey()), entry.getValue())).toList();
    }

    private List<OwnerDashboardResponse.TrendPoint> buildDailyPoints(List<Order> orders, TimeWindow currentWindow) {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        LocalDate startDate = currentWindow.start.toLocalDate();
        for (int day = 0; day < 7; day += 1) {
            totals.put(startDate.plusDays(day), BigDecimal.ZERO);
        }
        orders.forEach(order -> {
            if (order.completed_at != null && inWindow(order.completed_at, currentWindow)) {
                LocalDate date = order.completed_at.toLocalDate();
                totals.computeIfPresent(date, (key, value) -> value.add(optional(order.total_amount)));
            }
        });
        return totals.entrySet().stream()
            .map(entry -> point(entry.getKey().getMonthValue() + "/" + entry.getKey().getDayOfMonth(), entry.getValue()))
            .toList();
    }

    private List<OwnerDashboardResponse.TrendPoint> buildDailyPointsFromSummaries(
        List<SalesDailySummary> summaries,
        TimeWindow currentWindow
    ) {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        LocalDate startDate = currentWindow.start.toLocalDate();
        for (int day = 0; day < 7; day += 1) {
            totals.put(startDate.plusDays(day), BigDecimal.ZERO);
        }
        summaries.forEach(summary -> totals.computeIfPresent(summary.summary_date, (key, value) -> value.add(optional(summary.net_sales))));
        return totals.entrySet().stream()
            .map(entry -> point(entry.getKey().getMonthValue() + "/" + entry.getKey().getDayOfMonth(), entry.getValue()))
            .toList();
    }

    private List<OwnerDashboardResponse.TrendPoint> buildWeeklyPoints(List<Order> orders, TimeWindow currentWindow) {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        LocalDate startDate = currentWindow.start.toLocalDate();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(currentWindow.end.toLocalDate())) {
            totals.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusWeeks(1);
        }
        orders.forEach(order -> {
            if (order.completed_at != null && inWindow(order.completed_at, currentWindow)) {
                LocalDate date = order.completed_at.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                if (date.isBefore(startDate)) {
                    date = startDate;
                }
                LocalDate finalDate = date;
                totals.computeIfPresent(finalDate, (key, value) -> value.add(optional(order.total_amount)));
            }
        });
        return totals.entrySet().stream()
            .map(entry -> point("Week of " + entry.getKey().getMonthValue() + "/" + entry.getKey().getDayOfMonth(), entry.getValue()))
            .toList();
    }

    private List<OwnerDashboardResponse.TrendPoint> buildWeeklyPointsFromSummaries(
        List<SalesDailySummary> summaries,
        TimeWindow currentWindow
    ) {
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        LocalDate startDate = currentWindow.start.toLocalDate();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(currentWindow.end.toLocalDate())) {
            totals.put(cursor, BigDecimal.ZERO);
            cursor = cursor.plusWeeks(1);
        }
        summaries.forEach(summary -> {
            LocalDate date = summary.summary_date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            if (date.isBefore(startDate)) {
                date = startDate;
            }
            LocalDate bucket = date;
            totals.computeIfPresent(bucket, (key, value) -> value.add(optional(summary.net_sales)));
        });
        return totals.entrySet().stream()
            .map(entry -> point("Week of " + entry.getKey().getMonthValue() + "/" + entry.getKey().getDayOfMonth(), entry.getValue()))
            .toList();
    }

    private OwnerDashboardResponse.TrendPoint point(String label, BigDecimal value) {
        OwnerDashboardResponse.TrendPoint point = new OwnerDashboardResponse.TrendPoint();
        point.label = label;
        point.value = scale(value);
        return point;
    }

    private List<OwnerDashboardResponse.ItemPerformance> buildItemPerformance(
        List<OrderItem> currentOrderItems,
        List<OrderItem> previousOrderItems,
        boolean descending
    ) {
        Map<String, ItemAccumulator> current = aggregateItems(currentOrderItems);
        Map<String, ItemAccumulator> previous = aggregateItems(previousOrderItems);

        Comparator<OwnerDashboardResponse.ItemPerformance> comparator = Comparator
            .comparing((OwnerDashboardResponse.ItemPerformance row) -> optional(row.revenue))
            .thenComparing(row -> optional(row.quantity_change));
        if (descending) {
            comparator = comparator.reversed();
        }

        return current.entrySet().stream()
            .map(entry -> toPerformance(entry.getKey(), entry.getValue(), previous.get(entry.getKey())))
            .filter(row -> optional(row.revenue).compareTo(BigDecimal.ZERO) > 0)
            .sorted(comparator)
            .limit(5)
            .toList();
    }

    private List<OwnerDashboardResponse.ItemPerformance> buildItemPerformanceFromSummaries(
        List<MenuItemSalesSummary> currentItemSummaries,
        List<MenuItemSalesSummary> previousItemSummaries,
        boolean descending
    ) {
        Map<String, SummaryItemAccumulator> current = aggregateItemSummaries(currentItemSummaries);
        Map<String, SummaryItemAccumulator> previous = aggregateItemSummaries(previousItemSummaries);

        Comparator<OwnerDashboardResponse.ItemPerformance> comparator = Comparator
            .comparing((OwnerDashboardResponse.ItemPerformance row) -> optional(row.revenue))
            .thenComparing(row -> optional(row.quantity_change));
        if (descending) {
            comparator = comparator.reversed();
        }

        return current.entrySet().stream()
            .map(entry -> toPerformance(entry.getKey(), entry.getValue(), previous.get(entry.getKey())))
            .filter(row -> optional(row.revenue).compareTo(BigDecimal.ZERO) > 0)
            .sorted(comparator)
            .limit(5)
            .toList();
    }

    private List<OwnerDashboardResponse.ItemPerformance> buildTrendingItems(List<OrderItem> currentOrderItems, List<OrderItem> previousOrderItems) {
        Map<String, ItemAccumulator> current = aggregateItems(currentOrderItems);
        Map<String, ItemAccumulator> previous = aggregateItems(previousOrderItems);
        return current.entrySet().stream()
            .map(entry -> toPerformance(entry.getKey(), entry.getValue(), previous.get(entry.getKey())))
            .filter(row -> optional(row.quantity_change).compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing((OwnerDashboardResponse.ItemPerformance row) -> optional(row.quantity_change)).reversed())
            .toList();
    }

    private List<OwnerDashboardResponse.ItemPerformance> buildTrendingItemsFromSummaries(
        List<MenuItemSalesSummary> currentItemSummaries,
        List<MenuItemSalesSummary> previousItemSummaries
    ) {
        Map<String, SummaryItemAccumulator> current = aggregateItemSummaries(currentItemSummaries);
        Map<String, SummaryItemAccumulator> previous = aggregateItemSummaries(previousItemSummaries);
        return current.entrySet().stream()
            .map(entry -> toPerformance(entry.getKey(), entry.getValue(), previous.get(entry.getKey())))
            .filter(row -> optional(row.quantity_change).compareTo(BigDecimal.ZERO) > 0)
            .sorted(Comparator.comparing((OwnerDashboardResponse.ItemPerformance row) -> optional(row.quantity_change)).reversed())
            .toList();
    }

    private OwnerDashboardResponse.ItemPerformance toPerformance(String itemName, ItemAccumulator current, ItemAccumulator previous) {
        OwnerDashboardResponse.ItemPerformance row = new OwnerDashboardResponse.ItemPerformance();
        row.item_name = itemName;
        row.quantity = current.quantity;
        row.revenue = scale(current.revenue);
        row.previous_quantity = previous == null ? 0 : previous.quantity;
        row.quantity_change = scale(BigDecimal.valueOf(current.quantity - (previous == null ? 0 : previous.quantity)));
        return row;
    }

    private OwnerDashboardResponse.ItemPerformance toPerformance(
        String itemName,
        SummaryItemAccumulator current,
        SummaryItemAccumulator previous
    ) {
        OwnerDashboardResponse.ItemPerformance row = new OwnerDashboardResponse.ItemPerformance();
        row.item_name = itemName;
        row.quantity = current.quantity;
        row.revenue = scale(current.revenue);
        row.previous_quantity = previous == null ? 0 : previous.quantity;
        row.quantity_change = scale(BigDecimal.valueOf(current.quantity - (previous == null ? 0 : previous.quantity)));
        return row;
    }

    private Map<String, ItemAccumulator> aggregateItems(List<OrderItem> orderItems) {
        Map<String, ItemAccumulator> rows = new LinkedHashMap<>();
        orderItems.forEach(item -> {
            String key = item.item_name_snapshot_zh != null && !item.item_name_snapshot_zh.isBlank()
                ? item.item_name_snapshot_zh
                : item.item_name_snapshot_en;
            ItemAccumulator current = rows.computeIfAbsent(key, ignored -> new ItemAccumulator());
            current.quantity += item.quantity == null ? 0 : item.quantity;
            current.revenue = current.revenue.add(optional(item.line_amount));
        });
        return rows;
    }

    private Map<String, SummaryItemAccumulator> aggregateItemSummaries(List<MenuItemSalesSummary> summaries) {
        Map<String, SummaryItemAccumulator> rows = new LinkedHashMap<>();
        summaries.forEach(item -> {
            String key = item.item_name_snapshot_zh != null && !item.item_name_snapshot_zh.isBlank()
                ? item.item_name_snapshot_zh
                : item.item_name_snapshot_en;
            SummaryItemAccumulator current = rows.computeIfAbsent(key, ignored -> new SummaryItemAccumulator());
            current.quantity += item.quantity_sold == null ? 0 : item.quantity_sold;
            current.revenue = current.revenue.add(optional(item.sales_amount));
        });
        return rows;
    }

    private OwnerDashboardResponse.OrderStatusPanel buildOrderStatus(List<Order> activeOrders) {
        OwnerDashboardResponse.OrderStatusPanel panel = new OwnerDashboardResponse.OrderStatusPanel();
        activeOrders.forEach(order -> {
            switch (normalize(order.status)) {
                case "submitted" -> panel.pending += 1;
                case "preparing" -> panel.preparing += 1;
                case "ready" -> panel.ready += 1;
                default -> {
                }
            }
        });
        return panel;
    }

    private List<OwnerDashboardResponse.StoreComparisonRow> buildStoreComparison(
        List<Store> stores,
        Map<Long, List<Order>> ordersByStore,
        TimeWindow currentWindow,
        TimeWindow previousWindow
    ) {
        return stores.stream().map(store -> {
            List<Order> storeOrders = ordersByStore.getOrDefault(store.id, List.of());
            BigDecimal currentSales = sumTotals(storeOrders.stream()
                .filter(order -> COMPLETED_ORDER_STATUSES.contains(normalize(order.status)))
                .filter(order -> inWindow(order.completed_at, currentWindow))
                .toList());
            BigDecimal previousSales = sumTotals(storeOrders.stream()
                .filter(order -> COMPLETED_ORDER_STATUSES.contains(normalize(order.status)))
                .filter(order -> inWindow(order.completed_at, previousWindow))
                .toList());
            int activeCount = (int) storeOrders.stream()
                .filter(order -> ACTIVE_ORDER_STATUSES.contains(normalize(order.status)))
                .count();

            OwnerDashboardResponse.StoreComparisonRow row = new OwnerDashboardResponse.StoreComparisonRow();
            row.store_id = store.id;
            row.store_name = store.name;
            row.sales = scale(currentSales);
            row.previous_sales = scale(previousSales);
            row.change_pct = scale(percentChange(currentSales, previousSales));
            row.active_orders = activeCount;
            return row;
        }).sorted(Comparator.comparing((OwnerDashboardResponse.StoreComparisonRow row) -> optional(row.sales)).reversed()).toList();
    }

    private List<OwnerDashboardResponse.StoreComparisonRow> buildStoreComparisonFromSummaries(
        List<Store> stores,
        TimeWindow currentWindow,
        TimeWindow previousWindow,
        List<Order> activeOrders
    ) {
        Map<Long, List<StorePerformanceSummary>> currentByStore = fetchStorePerformanceSummaries(
            stores.stream().findFirst().map(store -> store.organization_id).orElse(null),
            null,
            currentWindow
        ).stream().collect(Collectors.groupingBy(summary -> summary.store_id));
        Map<Long, List<StorePerformanceSummary>> previousByStore = fetchStorePerformanceSummaries(
            stores.stream().findFirst().map(store -> store.organization_id).orElse(null),
            null,
            previousWindow
        ).stream().collect(Collectors.groupingBy(summary -> summary.store_id));
        Map<Long, Long> activeCounts = activeOrders.stream()
            .collect(Collectors.groupingBy(order -> order.store_id, Collectors.counting()));

        return stores.stream().map(store -> {
            BigDecimal currentSales = currentByStore.getOrDefault(store.id, List.of()).stream()
                .map(summary -> optional(summary.sales_amount))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal previousSales = previousByStore.getOrDefault(store.id, List.of()).stream()
                .map(summary -> optional(summary.sales_amount))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            OwnerDashboardResponse.StoreComparisonRow row = new OwnerDashboardResponse.StoreComparisonRow();
            row.store_id = store.id;
            row.store_name = store.name;
            row.sales = scale(currentSales);
            row.previous_sales = scale(previousSales);
            row.change_pct = scale(percentChange(currentSales, previousSales));
            row.active_orders = activeCounts.getOrDefault(store.id, 0L).intValue();
            return row;
        }).sorted(Comparator.comparing((OwnerDashboardResponse.StoreComparisonRow row) -> optional(row.sales)).reversed()).toList();
    }

    private List<OwnerDashboardResponse.RecentOrderRow> buildRecentOrders(List<Order> orders) {
        return orders.stream()
            .sorted(Comparator.comparing((Order order) -> latestTimestamp(order)).reversed())
            .limit(8)
            .map(order -> {
                OwnerDashboardResponse.RecentOrderRow row = new OwnerDashboardResponse.RecentOrderRow();
                row.order_id = order.id;
                row.order_no = order.order_no;
                row.label = order.table_no != null && !order.table_no.isBlank() ? order.table_no : order.pickup_no;
                row.order_type = order.order_type;
                row.status = order.status;
                row.total_amount = scale(optional(order.total_amount));
                row.occurred_at_label = latestTimestamp(order).format(TIME_LABEL);
                return row;
            })
            .toList();
    }

    private List<OrderItem> fetchOrderItems(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        return orderItemRepository.findAllByOrderIds(orders.stream().map(order -> order.id).toList());
    }

    private BigDecimal sumTotals(List<Order> orders) {
        return orders.stream()
            .map(order -> optional(order.total_amount))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            if (current == null || current.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(100);
        }
        return current.subtract(previous)
            .divide(previous, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal safeDivide(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return optional(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal optional(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDateTime latestTimestamp(Order order) {
        if (order.updated_at != null) {
            return order.updated_at;
        }
        if (order.completed_at != null) {
            return order.completed_at;
        }
        if (order.ready_at != null) {
            return order.ready_at;
        }
        if (order.submitted_at != null) {
            return order.submitted_at;
        }
        return order.created_at == null ? LocalDateTime.now() : order.created_at;
    }

    private boolean inWindow(LocalDateTime timestamp, TimeWindow window) {
        return timestamp != null && !timestamp.isBefore(window.start) && timestamp.isBefore(window.end);
    }

    private String normalizeRange(String range) {
        if ("week".equalsIgnoreCase(range)) {
            return "week";
        }
        if ("month".equalsIgnoreCase(range)) {
            return "month";
        }
        return "today";
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private TimeWindow buildWindow(String range, LocalDate today) {
        return switch (range) {
            case "week" -> {
                LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new TimeWindow(start.atStartOfDay(), start.plusDays(7).atStartOfDay());
            }
            case "month" -> {
                LocalDate start = today.withDayOfMonth(1);
                yield new TimeWindow(start.atStartOfDay(), start.plusMonths(1).atStartOfDay());
            }
            default -> new TimeWindow(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        };
    }

    private static class TimeWindow {
        private final LocalDateTime start;
        private final LocalDateTime end;

        private TimeWindow(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        private TimeWindow previousWindow() {
            long seconds = java.time.Duration.between(start, end).getSeconds();
            return new TimeWindow(start.minusSeconds(seconds), start);
        }
    }

    private static class ItemAccumulator {
        private int quantity;
        private BigDecimal revenue = BigDecimal.ZERO;
    }

    private static class SummaryItemAccumulator {
        private int quantity;
        private BigDecimal revenue = BigDecimal.ZERO;
    }
}
