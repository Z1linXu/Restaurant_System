package com.restaurant.system.menu.pricing.bulk;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CategoryBulkPriceModels {
    private CategoryBulkPriceModels() {}

    public static class PreviewRequest {
        public Long store_id;
        public List<Long> category_ids;
        public String direction;
        public BigDecimal amount;
        public String currency;
        // Required explicitly: inactive items are never silently included.
        public Boolean include_inactive;
    }

    public static class ApplyRequest extends PreviewRequest {
        public String preview_id;
        public Long expected_revision;
        public String fingerprint;
        public String idempotency_key;
    }

    public record PricingCapabilities(Long store_id, boolean category_bulk_enabled, boolean standard_size_enabled) {}

    public record PriceChange(Long item_id, Long category_id, String sku, String name_zh, String name_en,
        String name_fr, BigDecimal old_base_price, BigDecimal new_base_price) {}

    public record ExcludedItem(Long item_id, Long category_id, String sku, String name_zh, String name_en,
        String name_fr, String reason) {}

    public record Preview(String preview_id, Long store_id, List<Long> category_ids, String direction,
        BigDecimal amount, String currency, boolean include_inactive, long menu_revision, String fingerprint,
        Instant expires_at, int affected_item_count, int excluded_item_count,
        List<PriceChange> items, List<ExcludedItem> excluded_items) {}

    public record ApplyResult(Long store_id, String preview_id, String idempotency_key, String direction,
        BigDecimal amount, String currency, boolean include_inactive, List<Long> category_ids,
        long before_revision, long menu_revision, String before_fingerprint, int affected_item_count,
        int excluded_item_count, List<PriceChange> items, List<ExcludedItem> excluded_items) {}
}
