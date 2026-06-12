package com.restaurant.system.analytics.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsAggregationScheduler {

    private final AnalyticsAggregationService analyticsAggregationService;

    public AnalyticsAggregationScheduler(AnalyticsAggregationService analyticsAggregationService) {
        this.analyticsAggregationService = analyticsAggregationService;
    }

    @Scheduled(cron = "0 20 0 * * *", zone = "America/Toronto")
    public void rebuildYesterday() {
        analyticsAggregationService.rebuildYesterday();
    }
}
