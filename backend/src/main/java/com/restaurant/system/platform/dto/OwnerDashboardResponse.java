package com.restaurant.system.platform.dto;

import java.math.BigDecimal;
import java.util.List;

public class OwnerDashboardResponse {
    public Long organization_id;
    public String organization_name;
    public String range;
    public boolean compare_enabled;
    public List<StoreSummary> stores;
    public KpiSummary kpis;
    public List<InsightCard> insights;
    public SalesTrend trend;
    public List<ItemPerformance> top_items;
    public List<ItemPerformance> worst_items;
    public OrderStatusPanel order_status;
    public List<StoreComparisonRow> store_comparison;
    public List<RecentOrderRow> recent_orders;

    public static class StoreSummary {
        public Long id;
        public String name;
        public String code;
    }

    public static class MetricWithChange {
        public BigDecimal value;
        public BigDecimal previous_value;
        public BigDecimal change_pct;
    }

    public static class KpiSummary {
        public MetricWithChange sales;
        public MetricWithChange orders;
        public MetricWithChange average_order_value;
        public MetricWithChange active_orders;
    }

    public static class InsightCard {
        public String type;
        public String title;
        public String message;
        public String severity;
    }

    public static class TrendPoint {
        public String label;
        public BigDecimal value;
    }

    public static class SalesTrend {
        public String granularity;
        public List<TrendPoint> points;
    }

    public static class ItemPerformance {
        public String item_name;
        public Integer quantity;
        public BigDecimal revenue;
        public Integer previous_quantity;
        public BigDecimal quantity_change;
    }

    public static class OrderStatusPanel {
        public int pending;
        public int preparing;
        public int ready;
    }

    public static class StoreComparisonRow {
        public Long store_id;
        public String store_name;
        public BigDecimal sales;
        public BigDecimal previous_sales;
        public BigDecimal change_pct;
        public Integer active_orders;
    }

    public static class RecentOrderRow {
        public Long order_id;
        public String order_no;
        public String label;
        public String order_type;
        public String status;
        public BigDecimal total_amount;
        public String occurred_at_label;
    }
}
