package com.restaurant.system.analytics.dto;

import java.math.BigDecimal;
import java.util.List;

public class ChannelSalesSummary {
    public String revenue_basis = "MERCHANDISE_INCLUDING_TAX_EXCLUDING_PLATFORM_FEES_TIPS";
    public String currency = "CAD";
    public String revenue_note = "Merchandise sales including allocated tax; not platform payout or profit.";
    public Split totals;
    public List<Category> categories;
    public List<Category> noodle_sales;
    public List<Item> items;
    public List<Point> trend;
    public List<Point> daily;
    public List<StoreSales> stores;

    public static class Metric {
        /** Null means one or more real amounts are unknown. Never coerce to zero. */
        public BigDecimal revenue;
        public BigDecimal known_revenue;
        public int unknown_amount_count;
        public int order_count;
        public int quantity;
        public BigDecimal average_order_value;
        public BigDecimal percentage;
    }
    public static class Split {
        public Metric in_store;
        public Metric uber_eats;
        public Metric total;
    }
    public static class Category extends Split { public String reporting_group; }
    public static class Item extends Split { public String item_key; public String item_name; public String reporting_group; }
    public static class Point extends Split { public String label; public String date; public Integer hour; }
    public static class StoreSales extends Split { public Long store_id; }
}
