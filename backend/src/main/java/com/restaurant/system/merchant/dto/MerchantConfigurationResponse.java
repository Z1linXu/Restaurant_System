package com.restaurant.system.merchant.dto;

import java.util.List;

public record MerchantConfigurationResponse(
    Long store_id,
    Long organization_id,
    MerchantConfigurationSummary safe_summary,
    String configuration_source,
    List<ResourceReference> resource_references
) {
    /** Pointers only; the destination API remains authoritative and authorizes each request. */
    public record ResourceReference(int schema_version, String resource_type, String api_path) { }
}
