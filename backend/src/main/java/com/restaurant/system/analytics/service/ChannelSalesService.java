package com.restaurant.system.analytics.service;

import com.restaurant.system.analytics.dto.ChannelSalesSummary;
import com.restaurant.system.analytics.dto.ChannelSalesSummary.*;
import com.restaurant.system.analytics.support.SalesReporting;
import com.restaurant.system.menu.repository.MenuCategoryRepository;
import com.restaurant.system.menu.repository.MenuItemRepository;
import com.restaurant.system.order.entity.Order;
import com.restaurant.system.order.entity.OrderItem;
import com.restaurant.system.order.repository.OrderItemRepository;
import com.restaurant.system.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Same channel projection feeds Dashboard and all sales reports. No operational writes. */
@Service
public class ChannelSalesService {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final MenuItemRepository menu;
    private final MenuCategoryRepository categories;
    private final UberSalesSource uber;

    public ChannelSalesService(OrderRepository orders, OrderItemRepository items, MenuItemRepository menu,
                               MenuCategoryRepository categories, UberSalesSource uber) {
        this.orders = orders; this.items = items; this.menu = menu; this.categories = categories; this.uber = uber;
    }

    public ChannelSalesSummary build(List<Long> storeIds, LocalDate start, LocalDate end, boolean hourly) {
        List<Sale> sales = new ArrayList<>();
        LocalDateTime startAt = start.atStartOfDay(), endAt = end.plusDays(1).atStartOfDay();
        for (Long storeId : storeIds) {
            List<Order> pos = orders.findAllByStoreId(storeId).stream().filter(SalesReporting::eligible)
                .filter(order -> inside(order.submitted_at, startAt, endAt)).toList();
            List<OrderItem> posItems = pos.isEmpty() ? List.of() : items.findAllByOrderIds(pos.stream().map(order -> order.id).toList());
            Map<Long, List<OrderItem>> byOrder = posItems.stream().filter(SalesReporting::eligibleItem).collect(Collectors.groupingBy(item -> item.order_id));
            for (Order order : pos) {
                List<Line> lines = SalesReporting.allocate(List.of(order), byOrder.getOrDefault(order.id, List.of())).stream().map(allocated -> {
                    OrderItem item = allocated.item();
                    return item == null ? new Line("UNALLOCATED", "Unallocated sales", SalesReporting.Group.OTHER, 0, allocated.revenue())
                        : new Line(itemKey(item.item_sku_snapshot, "LOCAL_" + item.menu_item_id),
                            item.item_name_snapshot_zh == null ? item.item_name_snapshot_en : item.item_name_snapshot_zh,
                            SalesReporting.group(item.category_code_snapshot, item.item_sku_snapshot),
                            item.quantity == null ? 0 : item.quantity, allocated.revenue());
                }).toList();
                sales.add(new Sale("POS:" + order.id, storeId, order.submitted_at, false, SalesReporting.money(order.total_amount), lines));
            }
        }
        Set<String> seenUber = new HashSet<>();
        for (var order : uber.load(storeIds, startAt, endAt)) {
            if (!storeIds.contains(order.storeId()) || !inside(order.placedAt(), startAt, endAt)
                || order.orderId() == null || !seenUber.add(order.orderId())) continue;
            boolean currencyMatches = "CAD".equals(order.currency());
            BigDecimal revenue = currencyMatches ? fromMinor(order.revenueMinor()) : null;
            List<UberSalesSource.Item> sourceLines = order.items() == null ? List.of() : order.items();
            boolean reconciles = revenue != null && sourceLines.stream().allMatch(item -> item.revenueMinor() != null)
                && sourceLines.stream().map(item -> fromMinor(item.revenueMinor())).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(revenue) == 0;
            List<Line> lines = sourceLines.stream().map(item -> new Line(itemKey(item.sku(), "UBER_" + item.stableId()),
                item.name(), SalesReporting.group(item.categoryCode(), item.sku()), item.quantity(),
                currencyMatches && (revenue == null || reconciles) ? fromMinor(item.revenueMinor()) : null)).toList();
            if (lines.isEmpty()) lines = List.of(new Line("UNMAPPED_UBER", "Unmapped Uber item", SalesReporting.Group.OTHER, 0, revenue));
            sales.add(new Sale("UBER:" + order.orderId(), order.storeId(), order.placedAt(), true, revenue, lines));
        }
        ChannelSalesSummary result = new ChannelSalesSummary();
        result.totals = summarize(sales, null);
        result.categories = new ArrayList<>();
        for (var group : SalesReporting.Group.values()) {
            Category row = copy(summarize(sales, line -> line.group == group), new Category());
            row.reporting_group = group.name(); result.categories.add(row);
        }
        assignPercentages(result.categories, result.totals, split -> split.in_store);
        assignPercentages(result.categories, result.totals, split -> split.uber_eats);
        assignPercentages(result.categories, result.totals, split -> split.total);
        boolean hasFried = storeIds.stream().anyMatch(this::sellsFriedNoodles);
        result.noodle_sales = result.categories.stream().filter(row -> "SOUP_NOODLE".equals(row.reporting_group)
            || "DRY_NOODLE".equals(row.reporting_group) || (hasFried && "FRIED_NOODLE".equals(row.reporting_group))).toList();
        result.items = new ArrayList<>();
        Map<String, Line> identities = new LinkedHashMap<>();
        sales.forEach(sale -> sale.lines.forEach(line -> identities.putIfAbsent(line.key, line)));
        identities.forEach((key, line) -> {
            Item row = copy(summarize(sales, candidate -> candidate.key.equals(key)), new Item());
            row.item_key = key; row.item_name = line.name; row.reporting_group = line.group.name(); result.items.add(row);
        });
        result.daily = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            LocalDate day = date;
            Point point = copy(summarize(sales.stream().filter(sale -> sale.placedAt.toLocalDate().equals(day)).toList(), null), new Point());
            point.date = day.toString(); point.label = day.toString().substring(5); result.daily.add(point);
        }
        result.trend = result.daily;
        if (hourly) {
            result.trend = new ArrayList<>();
            for (int hour = 10; hour <= 22; hour++) {
                int currentHour = hour;
                Point point = copy(summarize(sales.stream().filter(sale -> sale.placedAt.getHour() == currentHour).toList(), null), new Point());
                point.hour = hour; point.date = start.toString(); point.label = String.format("%02d:00", hour); result.trend.add(point);
            }
        }
        result.stores = storeIds.stream().map(storeId -> {
            StoreSales row = copy(summarize(sales.stream().filter(sale -> sale.storeId.equals(storeId)).toList(), null), new StoreSales());
            row.store_id = storeId; return row;
        }).toList();
        return result;
    }

    private Split summarize(List<Sale> sales, java.util.function.Predicate<Line> filter) {
        Accumulator pos = new Accumulator(), platform = new Accumulator();
        for (Sale sale : sales) {
            Accumulator target = sale.uber ? platform : pos;
            if (filter == null) {
                target.add(sale.key, sale.lines.stream().mapToInt(line -> line.quantity).sum(), sale.revenue);
            } else {
                sale.lines.stream().filter(filter).forEach(line -> target.add(sale.key, line.quantity, line.revenue));
            }
        }
        Split split = new Split(); split.in_store = pos.metric(); split.uber_eats = platform.metric();
        pos.known = pos.known.add(platform.known); pos.unknown += platform.unknown; pos.quantity += platform.quantity; pos.orderIds.addAll(platform.orderIds);
        split.total = pos.metric(); return split;
    }

    private void assignPercentages(List<Category> rows, Split totals, Function<Split, Metric> channel) {
        Metric total = channel.apply(totals);
        boolean complete = total.revenue != null && rows.stream().allMatch(row -> channel.apply(row).revenue != null);
        BigDecimal cumulative = BigDecimal.ZERO, assigned = BigDecimal.ZERO;
        for (Category row : rows) {
            Metric metric = channel.apply(row);
            if (!complete) { metric.percentage = null; continue; }
            cumulative = cumulative.add(metric.revenue);
            BigDecimal target = total.revenue.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                : cumulative.multiply(BigDecimal.valueOf(100)).divide(total.revenue, 2, RoundingMode.HALF_UP);
            metric.percentage = target.subtract(assigned); assigned = target;
        }
    }

    private boolean sellsFriedNoodles(Long storeId) {
        Map<Long, String> codes = categories.findAllByStoreIdOrderByIdAsc(storeId).stream()
            .filter(category -> category.code != null).collect(Collectors.toMap(category -> category.id, category -> category.code));
        return menu.findActiveByStoreId(storeId).stream().anyMatch(item -> SalesReporting.group(codes.get(item.category_id), item.sku) == SalesReporting.Group.FRIED_NOODLE);
    }
    private static <T extends Split> T copy(Split source, T target) { target.in_store = source.in_store; target.uber_eats = source.uber_eats; target.total = source.total; return target; }
    private static String itemKey(String sku, String fallback) { return sku == null || sku.isBlank() ? fallback : "SKU_" + sku; }
    private static BigDecimal fromMinor(Long value) { return value == null ? null : BigDecimal.valueOf(value, 2); }
    private static boolean inside(LocalDateTime date, LocalDateTime start, LocalDateTime end) { return date != null && !date.isBefore(start) && date.isBefore(end); }
    private record Line(String key, String name, SalesReporting.Group group, int quantity, BigDecimal revenue) {}
    private record Sale(String key, Long storeId, LocalDateTime placedAt, boolean uber, BigDecimal revenue, List<Line> lines) {}
    private static class Accumulator {
        BigDecimal known = BigDecimal.ZERO.setScale(2); int unknown; int quantity; Set<String> orderIds = new HashSet<>();
        void add(String orderId, int qty, BigDecimal revenue) { orderIds.add(orderId); quantity += qty; if (revenue == null) unknown++; else known = known.add(revenue); }
        Metric metric() {
            Metric result = new Metric(); result.known_revenue = known; result.revenue = unknown == 0 ? known : null;
            result.unknown_amount_count = unknown; result.quantity = quantity; result.order_count = orderIds.size();
            result.average_order_value = result.revenue == null ? null : orderIds.isEmpty() ? BigDecimal.ZERO.setScale(2) : known.divide(BigDecimal.valueOf(orderIds.size()), 2, RoundingMode.HALF_UP);
            return result;
        }
    }
}
