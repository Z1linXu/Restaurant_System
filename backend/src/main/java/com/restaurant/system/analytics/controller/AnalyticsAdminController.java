package com.restaurant.system.analytics.controller;

import com.restaurant.system.analytics.dto.AnalyticsSummaryResponse;
import com.restaurant.system.analytics.service.AnalyticsAggregationService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.feature.FeatureFlagService;
import com.restaurant.system.common.feature.FeaturePackage;
import com.restaurant.system.common.response.ApiResponse;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AnalyticsAdminController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsAdminController.class);

    private final AnalyticsAggregationService analyticsAggregationService;
    private final AuthorizationService authorizationService;
    private final FeatureFlagService featureFlagService;

    public AnalyticsAdminController(
        AnalyticsAggregationService analyticsAggregationService,
        AuthorizationService authorizationService,
        FeatureFlagService featureFlagService
    ) {
        this.analyticsAggregationService = analyticsAggregationService;
        this.authorizationService = authorizationService;
        this.featureFlagService = featureFlagService;
    }

    @PostMapping("/rebuild")
    public ApiResponse<String> rebuild(
        @RequestParam String date,
        @RequestParam(required = false) Long store_id
    ) {
        featureFlagService.requireEnabled(FeaturePackage.ANALYTICS);
        if (store_id != null) {
            authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        } else {
            authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        }

        analyticsAggregationService.rebuildForDate(LocalDate.parse(date), store_id);
        return ApiResponse.success("Analytics rebuild completed", "OK");
    }

    @GetMapping("/summaries")
    public ApiResponse<AnalyticsSummaryResponse> getSummaries(
        @RequestParam(required = false) Long organization_id,
        @RequestParam(required = false) Long store_id,
        @RequestParam(defaultValue = "today") String range,
        @RequestParam(required = false) String anchor_date,
        @RequestParam(required = false) String start_date,
        @RequestParam(required = false) String end_date
    ) {
        featureFlagService.requireEnabled(FeaturePackage.ANALYTICS);
        if (store_id != null) {
            authorizationService.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        } else {
            authorizationService.require(Capability.ADMIN_STORE_CONFIG);
        }

        AnalyticsSummaryResponse response = analyticsAggregationService.getSummaries(
                organization_id,
                store_id,
                range,
                anchor_date == null || anchor_date.isBlank() ? null : LocalDate.parse(anchor_date),
                start_date == null || start_date.isBlank() ? null : LocalDate.parse(start_date),
                end_date == null || end_date.isBlank() ? null : LocalDate.parse(end_date)
        );
        log.info(
            "Analytics summaries served: range={}, startDate={}, endDate={}, dailySummariesReturned={}",
            response.range,
            response.start_date,
            response.end_date,
            response.sales_daily_summaries == null ? 0 : response.sales_daily_summaries.size()
        );
        return ApiResponse.success(response);
    }
}
