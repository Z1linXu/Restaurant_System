package com.restaurant.system.analytics.dto;

import com.restaurant.system.analytics.entity.AnalyticsAlert;
import com.restaurant.system.analytics.entity.MenuItemSalesSummary;
import com.restaurant.system.analytics.entity.SalesDailySummary;
import com.restaurant.system.analytics.entity.SalesHourlySummary;
import com.restaurant.system.analytics.entity.StorePerformanceSummary;
import java.util.List;

public class AnalyticsSummaryResponse {
    public Long organization_id;
    public Long store_id;
    public String range;
    public String start_date;
    public String end_date;
    public List<SalesDailySummary> sales_daily_summaries;
    public List<SalesHourlySummary> sales_hourly_summaries;
    public List<MenuItemSalesSummary> menu_item_sales_summaries;
    public List<StorePerformanceSummary> store_performance_summaries;
    public List<AnalyticsAlert> analytics_alerts;
}
