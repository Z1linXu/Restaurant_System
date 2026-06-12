package com.restaurant.system.analytics.service;

import com.restaurant.system.analytics.dto.AnalyticsSummaryResponse;
import java.time.LocalDate;

public interface AnalyticsAggregationService {
    void rebuildForDate(LocalDate summaryDate, Long storeId);

    void rebuildYesterday();

    AnalyticsSummaryResponse getSummaries(Long organizationId, Long storeId, String range);

    AnalyticsSummaryResponse getSummaries(
        Long organizationId,
        Long storeId,
        String range,
        LocalDate anchorDate,
        LocalDate startDate,
        LocalDate endDate
    );
}
