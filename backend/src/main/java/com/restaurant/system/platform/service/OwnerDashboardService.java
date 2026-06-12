package com.restaurant.system.platform.service;

import com.restaurant.system.platform.dto.OwnerDashboardResponse;

public interface OwnerDashboardService {
    OwnerDashboardResponse getDashboard(Long organizationId, Long storeId, String range, boolean compareEnabled);
}
