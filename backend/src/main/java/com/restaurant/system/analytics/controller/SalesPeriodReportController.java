package com.restaurant.system.analytics.controller;

import com.restaurant.system.analytics.dto.SalesPeriodReport;
import com.restaurant.system.analytics.service.AnalyticsReadScope;
import com.restaurant.system.analytics.service.SalesPeriodReportService;
import com.restaurant.system.common.auth.AuthorizationService;
import com.restaurant.system.common.auth.Capability;
import com.restaurant.system.common.exception.BusinessException;
import com.restaurant.system.common.response.ApiResponse;
import com.restaurant.system.modules.ModuleKeys;
import com.restaurant.system.modules.StoreModuleAccessEvaluator;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Explicitly Store-scoped; no implicit global or cross-organization financial reads. */
@RestController
@RequestMapping("/api/v1/admin/analytics")
public class SalesPeriodReportController {
    private final AuthorizationService authorization;
    private final StoreModuleAccessEvaluator modules;
    private final AnalyticsReadScope scope;
    private final SalesPeriodReportService reports;

    public SalesPeriodReportController(AuthorizationService authorization, StoreModuleAccessEvaluator modules,
            AnalyticsReadScope scope, SalesPeriodReportService reports) {
        this.authorization = authorization;
        this.modules = modules;
        this.scope = scope;
        this.reports = reports;
    }

    @GetMapping("/period-report")
    public ApiResponse<SalesPeriodReport> report(@RequestParam(required = false) Long store_id,
            @RequestParam(defaultValue = "day") String period, @RequestParam(required = false) String date) {
        if (store_id == null || store_id <= 0) throw new BusinessException("An explicit store_id is required");
        if (date == null || date.isBlank()) throw new BusinessException("date must be YYYY-MM-DD");
        authorization.requireForStore(store_id, Capability.ADMIN_STORE_CONFIG);
        modules.requireCapability(store_id, ModuleKeys.REPORTING_CORE);
        var selected = scope.stores(null, store_id);
        LocalDate anchor;
        try { anchor = LocalDate.parse(date); }
        catch (DateTimeParseException invalidDate) { throw new BusinessException("date must be YYYY-MM-DD"); }
        return ApiResponse.success(reports.build(selected.get(0), period, anchor));
    }
}
