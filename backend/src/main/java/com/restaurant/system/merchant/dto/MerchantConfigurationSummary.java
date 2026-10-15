package com.restaurant.system.merchant.dto;

/** Public, non-secret Store configuration; never contains a private Profile artifact. */
public record MerchantConfigurationSummary(
    int schema_version,
    long config_revision,
    String brand_name,
    String locale,
    String currency,
    String timezone,
    String tax_policy_reference,
    String profile_code,
    String compatibility_mode
) { }
