package com.restaurant.system.analytics.service;

import java.time.LocalDateTime;
import java.util.List;

/** Read-only adapter over immutable Uber financial and root-item identity snapshots. */
public interface UberSalesSource {
    // Bounds and returned placedAt use each Store's local wall time, not UTC.
    List<Sale> load(List<Long> storeIds, LocalDateTime start, LocalDateTime end);

    record Sale(Long storeId, String orderId, LocalDateTime placedAt, String currency,
                Long revenueMinor, List<Item> items) {}
    record Item(String stableId, String sku, String categoryCode, String name,
                int quantity, Long revenueMinor) {}
}
